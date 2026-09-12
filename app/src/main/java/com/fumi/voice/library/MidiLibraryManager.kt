package com.fumi.voice.library

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fumi.voice.midi.MidiParser
import com.fumi.voice.model.MidiTrack
import com.fumi.voice.util.DocumentTreeScanner
import java.io.File
import java.io.FileOutputStream

/** 一次导入的结果，用于给界面反馈。 */
data class MidiImportResult(
    val imported: List<MidiTrack>,
    val skipped: Int,
)

/**
 * MIDI 曲库：把用户导入的 .mid/.midi 统一存放在 filesDir/midi 下。
 *
 * 导入时顺带解析一次音符数与时长并写进文件清单，
 * 这样列表渲染不需要每次重新解析。
 */
class MidiLibraryManager(
    private val context: Context,
    private val metaStore: TrackMetaStore? = null,
) {

    companion object {
        private const val TAG = "MidiLibrary"

        /** 乐谱类：需要 BASSMIDI 建流，并能解析出音符供瀑布使用。 */
        private val MIDI_EXTENSIONS = setOf("mid", "midi", "smf", "kar", "rmi")

        /** 音频类：交给 BASS 的解码插件（FLAC / WavPack）。 */
        private val AUDIO_EXTENSIONS = setOf("flac", "wv")

        // Tracker 模块音乐（mod/xm/s3m/it/mtm）**没有**放进来。
        //
        // 实测这台设备的 libbass.so 加载 .mod 时，解码与直接播放两条路径
        // 都返回 BASS_ERROR_FILEFORM(41)，即该构建里没有可用的模块加载器。
        // （符号表里残留着 "Extended Module"/"MTM" 字样，但确定被支持的
        //   "OggS" 同样不在里面，靠字符串判断不了，只能以实际加载结果为准。）
        //
        // 收进来只会让用户导入一首永远播不了的曲子，所以在扫描器这层就不收。
        // 若日后确认某个真实 .mod 能播，这里加回扩展名，
        // 并恢复按文件头嗅探（MOD 看偏移 1080 的 "M.K."、XM 看 "Extended Module: "、
        // S3M 看偏移 44 的 "SCRM"、IT 看 "IMPM"）即可。
        private val EXTENSIONS = (MIDI_EXTENSIONS + AUDIO_EXTENSIONS).toList()

        /** BASS / 插件能解码的通用容器（各格式的魔数都在文件开头）。 */
        private val CONTAINER_TAGS = setOf("fLaC", "wvpk", "RIFF", "OggS", "FORM")
    }

    val dir: File = File(context.filesDir, "midi").apply { if (!exists()) mkdirs() }

    /** 云同步下发的曲目单独存放，与本地导入的曲库分开，便于查看与清理 */
    val cloudDir: File = File(dir, "cloud").apply { if (!exists()) mkdirs() }

    /** 列出曲库中的全部曲目（含云同步子目录）。会解析 MIDI，请在 IO 线程调用。 */
    fun list(): List<MidiTrack> {
        val tracks = ((dir.listFiles() ?: emptyArray()) + (cloudDir.listFiles() ?: emptyArray()))
            .filter { it.isFile && it.extension.lowercase() in EXTENSIONS && it.length() > 0 }
            .map { toTrack(it) }
            .sortedBy { it.title.lowercase() }
        // 清理已被删除文件的悬空修正记录
        metaStore?.pruneTo(tracks.map { it.fileName }.toSet())
        return tracks
    }

    private fun toTrack(file: File): MidiTrack {
        val isScore = file.extension.lowercase() in MIDI_EXTENSIONS
        // 只有乐谱才解析音符与时长；音频交给 BASS，列表里先显示 --
        val parsed = if (isScore) {
            runCatching { MidiParser.parse(file.inputStream()) }.getOrNull()
        } else {
            null
        }
        // 文件名启发式推断「艺术家 - 曲名」，再让手动修正覆盖
        val (guessedArtist, guessedTitle) = TrackMetadataParser.parse(file.name)
        val override = metaStore?.get(file.name)
        return MidiTrack(
            fileName = file.name,
            title = override?.title ?: guessedTitle,
            path = file.absolutePath,
            sizeBytes = file.length(),
            noteCount = parsed?.notes?.size ?: 0,
            durationMs = parsed?.durationMs ?: 0,
            artist = override?.artist ?: guessedArtist,
        )
    }

    fun find(fileName: String): MidiTrack? =
        (dir.listFiles() ?: emptyArray()).firstOrNull { it.isFile && it.name == fileName }?.let { toTrack(it) }

    /** 从 SAF URI 导入单个 MIDI 文件。 */
    fun importFromUri(uri: Uri): MidiTrack? {
        val rawName = DocumentTreeScanner.displayName(context, uri) ?: "track_${System.currentTimeMillis()}.mid"
        val fileName = uniqueName(sanitize(rawName))
        val dest = File(dir, fileName)
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use { input -> FileOutputStream(dest).use { output -> input.copyTo(output) } }
            if (!looksLikeSupported(dest)) {
                Log.w(TAG, "文件头不是受支持的格式: ${dest.name}")
                dest.delete()
                return null
            }
            toTrack(dest)
        } catch (e: Exception) {
            Log.e(TAG, "导入 MIDI 失败: $uri", e)
            dest.delete()
            null
        }
    }

    /** 批量导入，返回成功与跳过数量。 */
    fun importAll(uris: List<Uri>): MidiImportResult {
        val ok = mutableListOf<MidiTrack>()
        var skipped = 0
        for (uri in uris) {
            val track = importFromUri(uri)
            if (track != null) ok.add(track) else skipped++
        }
        return MidiImportResult(ok, skipped)
    }

    /** 扫描用户选中的目录，导入其中所有 MIDI 文件。 */
    fun importFromTree(treeUri: Uri): MidiImportResult {
        val uris = DocumentTreeScanner.scan(context, treeUri, EXTENSIONS)
        return importAll(uris)
    }

    fun delete(track: MidiTrack): Boolean = File(track.path).delete()

    fun rename(track: MidiTrack, newTitle: String): MidiTrack? {
        val cleaned = newTitle.trim().ifBlank { return null }
        val source = File(track.path)
        val target = File(dir, uniqueName(sanitize("$cleaned.${source.extension}")))
        return if (source.renameTo(target)) toTrack(target) else null
    }

    /** 解析出曲目时长，用于列表二次校正。 */
    fun durationOf(track: MidiTrack): Long =
        runCatching { MidiParser.parse(File(track.path).inputStream()).durationMs }.getOrDefault(track.durationMs)

    /**
     * 按文件头判断是否是可播放的曲目。
     *
     * 乐谱必须是 MThd；音频只放行 BASS / 插件能解码的几种容器
     * （FLAC、WavPack、RIFF/WAVE、Ogg、AIFF、MP3）。
     * 只改扩展名的其它文件依然会被拒绝。
     */
    private fun looksLikeSupported(file: File): Boolean {
        if (file.length() < 16) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(12)
                if (input.read(header) < 12) return@use false
                if (header[0] == 'M'.code.toByte() && header[1] == 'T'.code.toByte() &&
                    header[2] == 'h'.code.toByte() && header[3] == 'd'.code.toByte()
                ) {
                    return@use true
                }
                val tag = String(header, 0, 4, Charsets.US_ASCII)
                when {
                    tag in CONTAINER_TAGS -> true
                    // MP3：带 ID3 标签，或开头就是 MPEG 帧同步
                    tag.startsWith("ID3") -> true
                    (header[0].toInt() and 0xFF) == 0xFF &&
                        (header[1].toInt() and 0xE0) == 0xE0 -> true
                    else -> false
                }
            }
        }.getOrDefault(false)
    }

    private fun sanitize(name: String): String {
        val base = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        return if (base.contains('.')) base else "$base.mid"
    }

    private fun uniqueName(name: String): String {
        if (!File(dir, name).exists()) return name
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "mid")
        var i = 2
        while (File(dir, "${base}_$i.$ext").exists()) i++
        return "${base}_$i.$ext"
    }
}
