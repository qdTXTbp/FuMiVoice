package com.fumi.voice.soundfont

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fumi.voice.util.DocumentTreeScanner
import java.io.File
import java.io.FileOutputStream

/** 一个可用的音色库（.sf2）。 */
data class SoundFontInfo(
    val fileName: String,
    val displayName: String,
    val path: String,
    val sizeBytes: Long,
    val isBundled: Boolean,
)

/** 导入结果，用于给界面反馈。 */
data class ImportResult(
    val imported: List<SoundFontInfo>,
    val failed: Int,
)

/**
 * 音色库管理。
 *
 * - 内置音色：随 APK 打包在 assets/soundfonts 下，首次使用时解压到应用私有目录。
 * - 导入音色：通过系统文件选择器（单/多文件或整个文件夹）导入，统一存放在 filesDir/soundfonts。
 * - 当前选中的音色库记录在 SharedPreferences 中，重启后仍然生效。
 */
class SoundFontManager(private val context: Context) {

    companion object {
        private const val TAG = "SoundFontManager"
        private const val PREFS = "soundfont_prefs"
        private const val KEY_SELECTED = "selected_file_name"
        private const val BUNDLED_ASSET_DIR = "soundfonts"
        const val BUNDLED_NAME = "GeneralUser.sf2"

        /** 支持的音色库扩展名：SF2、SF3（Vorbis 压缩）与 DLS。 */
        val EXTENSIONS = listOf("sf2", "sf3", "dls")
    }

    /** 所有音色库的存放目录。 */
    val dir: File = File(context.filesDir, "soundfonts")

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        if (!dir.exists()) dir.mkdirs()
    }

    /** 当前选中的音色库文件名。 */
    val selectedFileName: String?
        get() = prefs.getString(KEY_SELECTED, null)

    fun select(info: SoundFontInfo) {
        prefs.edit().putString(KEY_SELECTED, info.fileName).apply()
    }

    /** 列出内置 + 已导入的全部音色库。 */
    fun list(): List<SoundFontInfo> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.lowercase() in EXTENSIONS && it.length() > 0 }
            .map { file ->
                SoundFontInfo(
                    fileName = file.name,
                    displayName = prettyName(file.name),
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    isBundled = file.name == BUNDLED_NAME,
                )
            }
            .sortedWith(compareByDescending<SoundFontInfo> { it.isBundled }.thenBy { it.displayName })

    fun find(fileName: String): SoundFontInfo? = list().firstOrNull { it.fileName == fileName }

    /**
     * 确保内置音色已解压到应用目录（幂等）。
     * @return 解压后的文件，失败返回 null。
     */
    fun ensureBundledExtracted(): File? {
        val dest = File(dir, BUNDLED_NAME)
        if (dest.exists() && dest.length() > 0) return dest
        return try {
            context.assets.open("$BUNDLED_ASSET_DIR/$BUNDLED_NAME").use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            dest
        } catch (e: Exception) {
            Log.e(TAG, "解压内置音色失败", e)
            null
        }
    }

    /** 导入单个文件（系统选择器返回的 URI）。 */
    fun importFromUri(uri: Uri): SoundFontInfo? {
        val rawName = queryDisplayName(uri)
            ?: "soundfont_${System.currentTimeMillis()}.sf2"
        val fileName = uniqueName(sanitize(rawName))
        val dest = File(dir, fileName)
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use { input -> FileOutputStream(dest).use { output -> input.copyTo(output) } }
            if (!looksLikeSoundFont(dest)) {
                Log.w(TAG, "不是有效的音色库文件: ${dest.name}")
                dest.delete()
                return null
            }
            SoundFontInfo(fileName, prettyName(fileName), dest.absolutePath, dest.length(), false)
        } catch (e: Exception) {
            Log.e(TAG, "导入音色失败: $uri", e)
            dest.delete()
            null
        }
    }

    /** 批量导入，返回成功与失败数量。 */
    fun importAll(uris: List<Uri>): ImportResult {
        val ok = mutableListOf<SoundFontInfo>()
        var failed = 0
        for (uri in uris) {
            val info = importFromUri(uri)
            if (info != null) ok.add(info) else failed++
        }
        return ImportResult(ok, failed)
    }

    /**
     * 把下载完成的临时文件收编进音色库目录。
     *
     * 先校验文件头再落位，避免把 CDN 的错误页当成音色库存下来。
     * 校验通过后使用 [desiredName] 作为最终文件名（重名自动加后缀）。
     */
    fun adoptDownloaded(tempFile: File, desiredName: String): SoundFontInfo? {
        if (!tempFile.exists() || !looksLikeSoundFont(tempFile)) {
            Log.w(TAG, "下载内容不是有效音色库: ${tempFile.name}")
            tempFile.delete()
            return null
        }
        val fileName = uniqueName(sanitize(desiredName))
        val dest = File(dir, fileName)
        return try {
            if (dest.exists()) dest.delete()
            if (!tempFile.renameTo(dest)) {
                tempFile.inputStream().use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                tempFile.delete()
            }
            SoundFontInfo(fileName, prettyName(fileName), dest.absolutePath, dest.length(), false)
        } catch (e: Exception) {
            Log.e(TAG, "收编下载文件失败", e)
            dest.delete()
            null
        }
    }

    /** 删除已导入的音色库；内置音色不可删除。 */
    fun delete(info: SoundFontInfo): Boolean {
        if (info.isBundled) return false
        val ok = File(info.path).delete()
        if (ok && selectedFileName == info.fileName) {
            prefs.edit().remove(KEY_SELECTED).apply()
        }
        return ok
    }

    /**
     * 递归扫描用户通过 SAF 选中的文件夹，找出所有 .sf2 文件。
     * 使用 SAF 是为了避开 Android 11+ 的存储权限限制。
     */
    fun scanTree(treeUri: Uri, maxDepth: Int = 3): List<Uri> =
        DocumentTreeScanner.scan(context, treeUri, listOf("sf2", "sf3", "dls"), maxDepth)

    private fun queryDisplayName(uri: Uri): String? = DocumentTreeScanner.displayName(context, uri)

    /**
     * 校验文件头，确认是 BASSMIDI 能识别的音色库。
     * 合法的 RIFF 容器：SoundFont2/3 为 "sfbk"，DLS 为 "DLS "。
     * 这样可以挡掉改名成 .sf2 的其它文件（例如网页、压缩包）。
     */
    /**
     * 按文件头判断是不是音色库（RIFF 容器 + sfbk/DLS 类型）。
     *
     * 公开给下载链路当校验器用：下载"成功"不代表拿到了音色库，
     * 镜像可能返回自己的 HTML 错误页，必须在收编之前验一次。
     */
    fun looksLikeSoundFont(file: File): Boolean {
        if (file.length() < 1024) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(12)
                if (input.read(header) != 12) return false
                val riff = String(header, 0, 4, Charsets.US_ASCII)
                val form = String(header, 8, 4, Charsets.US_ASCII)
                riff == "RIFF" && (form == "sfbk" || form == "DLS ")
            }
        }.getOrDefault(false)
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        // 已经是受支持的扩展名就原样保留，否则补上 .sf2
        return if (cleaned.substringAfterLast('.', "").lowercase() in EXTENSIONS) cleaned
        else "$cleaned.sf2"
    }

    private fun uniqueName(name: String): String {
        if (!File(dir, name).exists()) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "sf2")
        var i = 2
        while (File(dir, "${base}_$i.$ext").exists()) i++
        return "${base}_$i.$ext"
    }

    private fun prettyName(fileName: String): String = fileName.substringBeforeLast('.')
}
