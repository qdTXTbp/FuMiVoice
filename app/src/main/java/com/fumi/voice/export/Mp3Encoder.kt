package com.fumi.voice.export

import android.util.Log
import com.un4seen.bass.BASS
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * MP3 编码。
 *
 * Android 平台**没有** MP3 编码器（MediaCodec 只提供 AAC / FLAC / Opus / AMR 等），
 * 所以这里用项目自带的 LAME，通过 libmp3lame_jni.so 暴露极简接口。
 *
 * [isAvailable] 以"能不能加载到那个 .so"为准：没把 LAME 编进包时，
 * 界面就不会列出 MP3，而不是用户点了才报错。
 */
object Mp3Encoder {

    private const val TAG = "Mp3Encoder"
    private const val LIB = "mp3lame_jni"

    /** 一次从 BASS 拉取的 PCM 字节数。 */
    private const val PULL_BYTES = 64 * 1024

    /** MP3 输出缓冲上限，LAME 一帧最多约 1.25 KB，留足余量。 */
    private const val OUT_CHUNK = 16 * 1024

    private const val BITRATE_KBPS = 192
    private const val QUALITY = 2

    private var availability: Boolean? = null

    fun isAvailable(): Boolean {
        availability?.let { return it }
        val ok = try {
            System.loadLibrary(LIB)
            Log.i(TAG, "LAME 编码器已加载")
            true
        } catch (e: Throwable) {
            Log.i(TAG, "未集成本地 MP3 编码器：${e.message}")
            false
        }
        availability = ok
        return ok
    }

    /**
     * 把 [decoder] 解码流里的 PCM 编码成 MP3 写入 [outFile]。
     *
     * LAME 内部自带输入缓冲，因此一次把整块 PCM 交给它即可，
     * 不需要在外面按帧切分。
     *
     * @return 写出的字节数
     */
    fun encode(
        outFile: File,
        decoder: Int,
        sampleRate: Int,
        channels: Int,
        onProgress: (Long) -> Unit,
    ): Long {
        if (!isAvailable()) throw IllegalStateException("未集成 MP3 编码器")

        val handle = nativeInit(sampleRate, channels, BITRATE_KBPS, QUALITY)
        if (handle == 0L) throw IllegalStateException("MP3 编码器初始化失败")

        val pcm = ByteBuffer.allocateDirect(PULL_BYTES)
        val out = ByteBuffer.allocateDirect(OUT_CHUNK)
        val scratch = ByteArray(OUT_CHUNK)
        var totalPcm = 0L
        var written = 0L

        try {
            BufferedOutputStream(FileOutputStream(outFile), 64 * 1024).use { sink ->
                while (true) {
                    pcm.clear()
                    val read = BASS.BASS_ChannelGetData(decoder, pcm, PULL_BYTES)
                    if (read <= 0) break
                    pcm.position(0)
                    pcm.limit(read)
                    totalPcm += read
                    onProgress(totalPcm)

                    out.clear()
                    val produced = nativeEncode(handle, pcm, read, out)
                    if (produced > 0) {
                        out.position(0)
                        out.limit(produced)
                        out.get(scratch, 0, produced)
                        sink.write(scratch, 0, produced)
                        written += produced
                    }
                }

                // 冲掉编码器内部残留的最后一帧
                while (true) {
                    out.clear()
                    val produced = nativeFlush(handle, out)
                    if (produced <= 0) break
                    out.position(0)
                    out.limit(produced)
                    out.get(scratch, 0, produced)
                    sink.write(scratch, 0, produced)
                    written += produced
                }
            }
        } finally {
            nativeClose(handle)
        }
        return written
    }

    // ---- 由 libmp3lame_jni.so 提供，接口刻意做到最小 ----

    /** 初始化编码器，返回句柄；0 表示失败。 */
    private external fun nativeInit(
        sampleRate: Int,
        channels: Int,
        bitRateKbps: Int,
        quality: Int,
    ): Long

    /**
     * 编码一块 16 位交错 PCM（[input] 前 [length] 字节会被全部消费），
     * 返回写进 [out] 的 MP3 字节数。
     */
    private external fun nativeEncode(
        handle: Long,
        input: ByteBuffer,
        length: Int,
        out: ByteBuffer,
    ): Int

    /** 冲刷编码器，返回写进 [out] 的字节数。 */
    private external fun nativeFlush(handle: Long, out: ByteBuffer): Int

    /** 释放编码器。 */
    private external fun nativeClose(handle: Long)
}
