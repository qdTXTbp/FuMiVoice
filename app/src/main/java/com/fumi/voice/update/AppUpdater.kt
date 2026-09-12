package com.fumi.voice.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用更新：检查 → 下载 → 拉起系统安装器。
 *
 * **发布约定**：每次发版除了 APK/AAB，还要上传两个固定名资产，应用才读得到新版——
 * - `version.json`：形如 `{"versionCode":6,"versionName":"1.0.5","apk":"FuMiVoice-latest.apk","notes":"…"}`
 * - `FuMiVoice-latest.apk`：始终是当次发布的 APK
 *
 * 用 `releases/latest/download/<资产名>` 这个固定地址，所以 URL 里不必带版本号，
 * 每次覆盖上传即可（与电脑端 `FuFumidi.Install.exe` 是同一套做法）。
 *
 * 检查与下载都按「国内镜像 → GitHub 直连」的顺序尝试：某条链路被墙、超时或
 * 返回错误页时自动换下一条，不会因为单个镜像不可用就让更新失败。
 */
object AppUpdater {

    private const val TAG = "AppUpdater"
    private const val REPO = "qdTXTbp/FuMiVoice"
    private const val VERSION_ASSET = "version.json"
    private const val DEFAULT_APK_ASSET = "FuMiVoice-latest.apk"
    private const val TIMEOUT_MS = 20_000
    private const val BUFFER_SIZE = 64 * 1024

    /** 国内镜像在前、GitHub 直连兜底（与电脑端更新器同一批镜像）。 */
    private val MIRRORS = listOf(
        "https://ghfast.top/https://github.com",
        "https://gh-proxy.com/https://github.com",
        "https://ghproxy.net/https://github.com",
        "https://github.com",
    )

    /** 一份版本描述；取自 Release 里的 version.json。 */
    data class UpdateInfo(
        val versionCode: Long,
        val versionName: String,
        val notes: String,
        val apkAsset: String,
    )

    // ---------------- 当前版本 ----------------

    fun currentVersionCode(context: Context): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)

    fun currentVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    // ---------------- 检查 ----------------

    /**
     * 检查是否有新版本。
     *
     * @return 有新版本返回它；已是最新返回 null。
     * @throws RuntimeException 所有镜像都失败（把每个镜像的失败原因一并抛出，便于排查）。
     */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val text = fetchText("$VERSION_ASSET")
        val json = try {
            JSONObject(text.trim())
        } catch (e: Exception) {
            throw RuntimeException("版本信息不是合法 JSON：" + text.trim().take(80))
        }
        val code = json.optLong("versionCode", 0L)
        val name = json.optString("versionName").ifBlank { code.toString() }
        if (code <= currentVersionCode(context)) return@withContext null
        UpdateInfo(
            versionCode = code,
            versionName = name,
            notes = json.optString("notes").trim(),
            apkAsset = json.optString("apk").ifBlank { DEFAULT_APK_ASSET },
        )
    }

    // ---------------- 下载 ----------------

    /**
     * 下载新版 APK 到 cacheDir。
     *
     * 下载完会先校验「是同一个包名、且版本号不低于 version.json 里声明的」，
     * 避免镜像返回错误页或被缓存的旧包被当成新版装上去。
     *
     * @param onProgress 已下载字节 / 总字节（总长未知时为 0）
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (read: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
        // 清掉上一次留下的包，避免多个版本堆在 cache 里
        dir.listFiles()?.forEach { if (it.isFile) it.delete() }
        val dest = File(dir, "FuMiVoice-${info.versionName}.apk")
        val failures = mutableListOf<String>()

        for (mirror in MIRRORS) {
            val url = "$mirror/$REPO/releases/latest/download/${info.apkAsset}"
            try {
                fetchTo(url, dest, onProgress)
                verifyApk(context, dest, info)
                return@withContext dest
            } catch (e: Exception) {
                Log.w(TAG, "从 $mirror 更新失败：${e.message}")
                dest.delete()
                failures.add(hostOf(mirror) + "（" + (e.message ?: "下载失败") + "）")
            }
        }
        throw RuntimeException("更新包下载失败，已尝试：" + failures.joinToString("；"))
    }

    /** 取 version.json 文本；逐个镜像试，全部失败时把原因汇总抛出。 */
    private fun fetchText(asset: String): String {
        val failures = mutableListOf<String>()
        for (mirror in MIRRORS) {
            try {
                val conn = open("$mirror/$REPO/releases/latest/download/$asset")
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) throw IllegalStateException("HTTP $code")
                    val text = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    if (text.isBlank()) throw IllegalStateException("响应为空")
                    return text
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                failures.add(hostOf(mirror) + "（" + (e.message ?: "连接失败") + "）")
            }
        }
        throw RuntimeException("无法获取版本信息，已尝试：" + failures.joinToString("；"))
    }

    private suspend fun fetchTo(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        val conn = open(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: 0L
            var read = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                    output.flush()
                }
            }
            if (read <= 0) throw IllegalStateException("下载内容为空")
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        instanceFollowRedirects = true
        requestMethod = "GET"
        useCaches = false
        setRequestProperty("User-Agent", "FuMiVoice")
    }

    /**
     * 校验下下来的确实是我们自己、且版本更高的包。
     *
     * 镜像偶尔会把错误页/旧的缓存当成新包返回；这里用系统解析 APK 头，
     * 包名不对或版本号没达到 version.json 声明的一律拒掉，绝不拉起安装器。
     */
    private fun verifyApk(context: Context, file: File, info: UpdateInfo) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IllegalStateException("下载到的文件不是有效的 APK")
        if (archive.packageName != context.packageName) {
            throw IllegalStateException("安装包包名不符（${archive.packageName}）")
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode
        else @Suppress("DEPRECATION") archive.versionCode.toLong()
        if (code < info.versionCode) {
            throw IllegalStateException("安装包版本($code)低于声明的 ${info.versionCode}，可能镜像返回了旧包")
        }
    }

    // ---------------- 安装 ----------------

    /**
     * 拉起系统安装器。
     *
     * Android 8 起需要用户为「本应用」单独打开「安装未知应用」开关；
     * 未打开时跳去系统设置页并返回 false，界面据此提示用户允许后再点一次。
     *
     * @return true 已拉起安装器；false 已跳去授权设置页。
     */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    private fun hostOf(base: String): String =
        base.removePrefix("https://").removePrefix("http://")
}
