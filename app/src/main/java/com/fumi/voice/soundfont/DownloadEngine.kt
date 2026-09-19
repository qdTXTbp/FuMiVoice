package com.fumi.voice.soundfont

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/**
 * 音色库下载引擎。
 *
 * 相比之前的「单连接 + 单一地址」，这里做两件事：
 *
 * 1. **镜像回退**：GitHub 直连在部分网络下慢到几乎不可用，所以对
 *    `github.com` 的资源按「国内镜像 → 直连」的顺序逐个尝试，
 *    某条链路超时/返回错误页就自动换下一条（与 [com.fumi.voice.update.AppUpdater] 同一批镜像）。
 *
 * 2. **分段并发下载**：多数 CDN/Pages 会限制单条连接的吞吐，但支持 `Range`。
 *    对大文件按字节区间切成若干段并发拉取，再各自写进同一个文件的对应偏移，
 *    实测能把单连接的瓶颈摊开。不支持 `Range` 的源自动退回单连接。
 *
 * 只依赖 [RandomAccessFile] 的偏移写入，不需要额外的临时分片文件。
 */
object DownloadEngine {

    private const val TAG = "DownloadEngine"
    private const val TIMEOUT_MS = 20_000
    private const val BUFFER_SIZE = 128 * 1024

    /** 低于这个体积不值得分段（握手开销反而更大）。 */
    private const val SEGMENT_MIN_BYTES = 4L * 1024 * 1024

    /** 分段数。太多会被部分服务端判为异常请求，4 段是速度与稳妥的平衡点。 */
    private const val SEGMENTS = 4

    /** 进度回报间隔：太密会让上层 StateFlow 频繁重组，太疏进度条会一跳一跳。 */
    private const val PROGRESS_INTERVAL_MS = 180L

    private const val GITHUB_PREFIX = "https://github.com/"

    /**
     * 国内镜像前缀，按实测速度排序，最后一条是 GitHub 直连兜底。
     * 顺序与电脑端 FuFumidi 的更新器保持一致，便于排查。
     */
    private val MIRRORS = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
    )

    /**
     * 把原始地址展开成「按优先级排序的候选地址列表」。
     *
     * 只有 github.com 的资源才走镜像；jsDelivr 本身就是国内较快的 CDN，
     * 再套一层镜像只会更慢，所以原样返回。
     */
    fun candidates(url: String): List<String> {
        if (!url.startsWith(GITHUB_PREFIX)) return listOf(url)
        return MIRRORS.map { it + url } + url
    }

    /**
     * 下载 [url] 到 [dest]，逐个候选地址尝试。
     *
     * @param onProgress 已下载字节 / 总字节（总长未知时为 0）
     * @throws RuntimeException 所有候选地址都失败，异常信息里汇总了每条链路的原因
     */
    suspend fun downloadTo(
        url: String,
        dest: File,
        onProgress: (read: Long, total: Long) -> Unit,
    ) = coroutineScope {
        val failures = mutableListOf<String>()
        for (candidate in candidates(url)) {
            try {
                fetchOne(candidate, dest, onProgress)
                return@coroutineScope
            } catch (e: CancellationException) {
                dest.delete()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "从 ${hostOf(candidate)} 下载失败：${e.message}")
                dest.delete()
                failures.add(hostOf(candidate) + "（" + (e.message ?: "下载失败") + "）")
            }
        }
        throw RuntimeException("下载失败，已尝试：" + failures.joinToString("；"))
    }

    // ---------------- 单条链路 ----------------

    private suspend fun fetchOne(
        url: String,
        dest: File,
        onProgress: (Long, Long) -> Unit,
    ) = coroutineScope {
        val probe = probe(url)
        val total = probe.total
        val loaded = AtomicLong(0L)

        // 由单独的协程统一回报进度：并发分段各自回报会因为竞态丢更新，
        // 集中成一个 ticker 反而更简单也更稳。
        val ticker = launch {
            while (isActive) {
                onProgress(loaded.get(), total)
                delay(PROGRESS_INTERVAL_MS)
            }
        }

        try {
            var done = false
            if (probe.ranges && total >= SEGMENT_MIN_BYTES) {
                try {
                    segments(url, dest, total) { n -> loaded.addAndGet(n) }
                    done = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 分段失败（服务端谎报 Range、中途断流等）不能就此放弃，
                    // 退回单连接再试一次，代价只是重下。
                    Log.w(TAG, "分段下载失败，回退单连接：${e.message}")
                    loaded.set(0L)
                    dest.delete()
                }
            }
            if (!done) {
                singleStream(url, dest) { n -> loaded.addAndGet(n) }
            }
        } finally {
            ticker.cancel()
        }

        onProgress(loaded.get(), if (total > 0) total else loaded.get())
    }

    private class Probe(val total: Long, val ranges: Boolean)

    /** 只取 1 个字节探一下：既拿到总长度，也确认服务端是否支持 Range。 */
    private fun probe(url: String): Probe {
        val conn = open(url, range = "bytes=0-0")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val ranges = code == HttpURLConnection.HTTP_PARTIAL
            val total = if (ranges) {
                conn.getHeaderField("Content-Range")
                    ?.substringAfterLast('/')
                    ?.trim()
                    ?.toLongOrNull() ?: -1L
            } else {
                conn.contentLengthLong
            }
            runCatching { conn.inputStream?.read() }
            return Probe(total, ranges && total > 0)
        } finally {
            conn.disconnect()
        }
    }

    /** 单连接顺序下载。 */
    private suspend fun singleStream(
        url: String,
        dest: File,
        onBytes: (Long) -> Unit,
    ) {
        val conn = open(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val buffer = ByteArray(BUFFER_SIZE)
            var any = false
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        onBytes(n.toLong())
                        any = true
                    }
                    output.flush()
                }
            }
            if (!any) throw IllegalStateException("下载内容为空")
        } finally {
            conn.disconnect()
        }
    }

    /** 分段并发下载：每段写进文件里自己那段偏移，互不重叠。 */
    private suspend fun segments(
        url: String,
        dest: File,
        total: Long,
        onBytes: (Long) -> Unit,
    ) {
        val chunk = (total + SEGMENTS - 1) / SEGMENTS
        RandomAccessFile(dest, "rw").use { it.setLength(total) }

        coroutineScope {
            (0 until SEGMENTS)
                .mapNotNull { index ->
                    val start = index * chunk
                    val end = minOf(total - 1, start + chunk - 1)
                    // 体积不足以切满这么多段时，末尾几段直接不参与
                    if (start > end) null
                    else async(Dispatchers.IO) { fetchRange(url, dest, start, end, onBytes) }
                }
                .awaitAll()
        }
    }

    private suspend fun fetchRange(
        url: String,
        dest: File,
        start: Long,
        end: Long,
        onBytes: (Long) -> Unit,
    ) {
        val conn = open(url, range = "bytes=$start-$end")
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_PARTIAL) {
                throw IllegalStateException("服务端未按 Range 返回（HTTP $code）")
            }
            val buffer = ByteArray(BUFFER_SIZE)
            var offset = start
            conn.inputStream.use { input ->
                RandomAccessFile(dest, "rw").use { raf ->
                    raf.seek(start)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        raf.seek(offset)
                        raf.write(buffer, 0, n)
                        offset += n
                        onBytes(n.toLong())
                    }
                }
            }
            if (offset - 1 < end) {
                throw IllegalStateException("分片未取满（期望到 $end，实际到 ${offset - 1}）")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, range: String? = null) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("User-Agent", "FuMiVoice")
            // 明确不要压缩：音色库本身不可压，带上反而可能拿不到 Range
            setRequestProperty("Accept-Encoding", "identity")
            range?.let { setRequestProperty("Range", it) }
        }

    private fun hostOf(u: String) =
        u.removePrefix("https://").removePrefix("http://").substringBefore('/')
}
