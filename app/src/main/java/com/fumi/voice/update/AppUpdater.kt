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

    // 线路顺序由 UpdateSource 按用户在设置里选的「自动 / 国内源 / 境外源」给出
    // （CNB 直连、GitHub 加速镜像、GitHub 直连的先后与兜底都在那里统一维护）。

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
        // 顺手清掉「上个版本已装好」的旧安装包（不碰尚未安装的新包）
        pruneInstalled(context)
        val text = fetchText(context, "$VERSION_ASSET")
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
     * 下载新版 APK。
     *
     * ★ 放 `filesDir/updates` 而不是 `cacheDir`：cacheDir 会被系统在存储紧张时清掉，
     *   而这里最典型的场景恰恰是「下完没装成」——安装器被中途关掉、用户没确认、
     *   或者装到一半切走了应用。那份 40MB 的包必须留到真正装好为止，
     *   所以不能放在随时会被回收的目录里。
     * ★ 目标版本已经下好并校验过时**直接复用**，不再走网络（见 [cachedApk]）。
     * ★ 下载先写 `.part`，校验通过才改名为最终文件：半途失败不会留下
     *   「看起来是完整包」的文件，也就不会出现装了半截包的情况。
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
        // 上次已经下好且校验通过：直接复用，用户不必再下一次
        cachedApk(context, info)?.let { return@withContext it }

        val dir = updatesDir(context)
        val dest = File(dir, "FuMiVoice-${info.versionName}.apk")
        // 只清别的版本（含残留的 .part），本版本的目标文件留到最后一步覆盖
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name != dest.name && !f.name.startsWith("FuMiVoice-${info.versionName}.apk.")) f.delete()
        }
        val part = File(dir, dest.name + ".part")
        val failures = mutableListOf<String>()

        for (url in UpdateSource.releaseUrls(context, info.apkAsset)) {
            try {
                part.delete()
                fetchTo(url, part, onProgress)
                verifyApk(context, part, info)
                // 校验通过才转正；renameTo 同盘是原子操作
                if (!part.renameTo(dest)) {
                    part.delete()
                    throw IllegalStateException("无法写入安装包目录")
                }
                return@withContext dest
            } catch (e: Exception) {
                Log.w(TAG, "从 $url 更新失败：${e.message}")
                part.delete()
                failures.add(hostOf(url) + "（" + (e.message ?: "下载失败") + "）")
            }
        }
        throw RuntimeException("更新包下载失败，已尝试：" + failures.joinToString("；"))
    }

    /** 安装包的持久目录（应用私有，不会被系统当缓存清掉）。 */
    private fun updatesDir(context: Context): File =
        File(context.filesDir, "updates").apply { if (!exists()) mkdirs() }

    /**
     * 该版本是否已经下好且校验通过。
     *
     * 命中就返回文件，可直接交给 [install]；文件损坏/不符则删掉并返回 null。
     * 供两处用：download 的复用判断、界面上「已下载，点此安装」的分支。
     */
    fun cachedApk(context: Context, info: UpdateInfo): File? {
        val f = File(updatesDir(context), "FuMiVoice-${info.versionName}.apk")
        if (!f.isFile || f.length() <= 0L) return null
        return runCatching {
            verifyApk(context, f, info)
            f
        }.getOrElse {
            Log.w(TAG, "已下载的安装包不可用（${it.message}），删除后重下")
            f.delete()
            null
        }
    }

    /**
     * 清掉「已经装好」的旧安装包：check 时调用，避免升级完成后那 40MB 一直占着。
     * 只删版本号等于当前已安装版本的，不碰尚未安装的新包。
     */
    private fun pruneInstalled(context: Context) {
        runCatching {
            val current = currentVersionName(context)
            updatesDir(context).listFiles()?.forEach { f ->
                if (f.isFile && f.name == "FuMiVoice-$current.apk") f.delete()
            }
        }
    }

    /** 取 version.json 文本；按当前线路设置逐个尝试，全部失败时把原因汇总抛出。 */
    private fun fetchText(context: Context, asset: String): String {
        val failures = mutableListOf<String>()
        for (url in UpdateSource.releaseUrls(context, asset)) {
            try {
                val conn = open(url)
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
                failures.add(hostOf(url) + "（" + (e.message ?: "连接失败") + "）")
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
