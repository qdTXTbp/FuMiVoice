package com.fumi.voice.soundfont

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
                if (source.parts.isEmpty()) {
                    DownloadEngine.downloadTo(source.url, tempFile) { read, total ->
                        reportProgress(source, read, total)
                    }
                } else {
                    fetchParts(source, tempFile)
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

    /** 把进度写回任务表；下载线程只在这里改状态，避免多处竞态。 */
    private fun reportProgress(source: SoundFontSource, read: Long, total: Long) {
        _tasks.value = _tasks.value + (
            source.id to DownloadState(
                sourceId = source.id,
                displayName = source.displayName,
                bytesRead = read,
                totalBytes = total,
            )
            )
    }

    /**
     * 分卷音色库：逐卷下载到各自的临时文件，再按声明顺序首尾相接拼成完整文件。
     *
     * 分卷是上游为了绕过单文件体积上限切开的，缺任何一卷都拼不出可用的音色库，
     * 所以中途失败就直接抛错，由调用方走统一的失败处理（不会留下半截成品）。
     */
    private suspend fun fetchParts(source: SoundFontSource, dest: File) {
        val total = source.approxBytes
        val partFiles = mutableListOf<File>()
        var done = 0L

        try {
            source.parts.forEachIndexed { index, partUrl ->
                val partFile = File(context.cacheDir, "sf_${source.id}_$index.part")
                partFiles += partFile
                val offset = done
                // 单卷的总长是它自己的，进度条按整包体积算，所以统一用 approxBytes 当分母
                DownloadEngine.downloadTo(partUrl, partFile) { read, _ ->
                    reportProgress(source, offset + read, total)
                }
                done += partFile.length()
                reportProgress(source, done, total)
            }

            FileOutputStream(dest).use { out ->
                partFiles.forEach { part ->
                    part.inputStream().use { it.copyTo(out) }
                }
                out.flush()
            }
        } finally {
            partFiles.forEach { it.delete() }
        }
    }
}
