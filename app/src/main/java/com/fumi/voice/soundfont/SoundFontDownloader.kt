package com.fumi.voice.soundfont

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** 单个下载任务的状态。 */
data class DownloadState(
    val sourceId: String,
    val displayName: String,
    val bytesRead: Long = 0,
    val totalBytes: Long = 0,
    val failed: Boolean = false,
    val error: String? = null,
) {
    /** 0..1，总长未知时返回 0（界面改用不确定进度）。 */
    val progress: Float
        get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/**
 * 音色库下载器。
 *
 * 设计要点：
 * 1. 下载到 cache 里的临时文件，**成功且校验通过后**才由
 *    [SoundFontManager.adoptDownloaded] 收编进正式目录 —— 中途失败不会留下半截文件。
 * 2. 支持多个任务并行，用 [tasks] 暴露进度给界面。
 * 3. 支持取消（协程取消后清理临时文件）。
 */
class SoundFontDownloader(
    private val context: Context,
    private val manager: SoundFontManager,
) {

    companion object {
        private const val TAG = "SoundFontDownloader"
        private const val TIMEOUT_MS = 20_000
        private const val BUFFER_SIZE = 64 * 1024
    }

    private val _tasks = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val tasks: StateFlow<Map<String, DownloadState>> = _tasks.asStateFlow()

    /** 已下载成功的音色库（用于在下载列表里标"已下载"）。 */
    private val _completed = MutableStateFlow<Set<String>>(emptySet())
    val completed: StateFlow<Set<String>> = _completed.asStateFlow()

    fun isDownloading(sourceId: String): Boolean = _tasks.value.containsKey(sourceId)

    /**
     * 下载并安装一个音色库。
     *
     * @return 成功时返回安装好的条目，失败或取消返回 null。
     */
    suspend fun download(source: SoundFontSource): SoundFontInfo? {
        // 上一次失败的话，先清掉失败态，让这次重试可以直接开始
        _tasks.value[source.id]?.let { existing ->
            if (!existing.failed) return null
            _tasks.value = _tasks.value - source.id
        }

        // 已存在同名文件时，先标记为已完成，避免重复下载
        manager.find(source.fileName)?.let { existing ->
            _completed.value = _completed.value + source.id
            return existing
        }

        _tasks.value = _tasks.value + (source.id to DownloadState(source.id, source.displayName))
        val tempFile = File(context.cacheDir, "sf_download_${source.id}.part")

        return try {
            val info = withContext(Dispatchers.IO) {
                fetchTo(source, tempFile) { read, total ->
                    _tasks.value = _tasks.value + (
                        source.id to DownloadState(
                            sourceId = source.id,
                            displayName = source.displayName,
                            bytesRead = read,
                            totalBytes = total,
                        )
                        )
                }
                // 下载完成，校验并收编
                manager.adoptDownloaded(tempFile, source.fileName)
            }

            if (info != null) {
                _completed.value = _completed.value + source.id
            } else {
                _tasks.value = _tasks.value + (
                    source.id to DownloadState(
                        sourceId = source.id,
                        displayName = source.displayName,
                        failed = true,
                        error = "文件校验未通过，可能不是有效的音色库",
                    )
                    )
            }
            info
        } catch (e: Exception) {
            Log.e(TAG, "下载失败: ${source.url}", e)
            tempFile.delete()
            _tasks.value = _tasks.value + (
                source.id to DownloadState(
                    sourceId = source.id,
                    displayName = source.displayName,
                    failed = true,
                    error = e.message ?: "网络错误",
                )
                )
            null
        } finally {
            // 无论成败，任务表里最终只保留失败态或干脆移除
            val current = _tasks.value[source.id]
            if (current != null && !current.failed) {
                _tasks.value = _tasks.value - source.id
            }
        }
    }

    /** 从 CDN 拉取到本地临时文件，边下边回报进度。 */
    private suspend fun fetchTo(
        source: SoundFontSource,
        dest: File,
        onProgress: (read: Long, total: Long) -> Unit,
    ) {
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "FuMiVoice/1.0")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("服务器返回 HTTP $code")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: source.approxBytes

            var read = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            connection.inputStream.use { input ->
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
            if (read == 0L) throw IllegalStateException("下载内容为空")
        } finally {
            connection.disconnect()
        }
    }
}
