package com.fumi.voice.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.un4seen.bass.BASS
import com.un4seen.bass.BASSFLAC
import com.un4seen.bass.BASSMIDI
import com.un4seen.bass.BASSWV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** 可导出的音频格式。 */
enum class ExportFormat(
    val label: String,
    val extension: String,
    val description: String,
) {
    /** 纯 Kotlin 写 PCM 头，不依赖任何编码器，任何设备都能用。 */
    WAV("WAV", "wav", "无损未压缩，体积最大，兼容性最好"),

    /** MediaCodec 的 FLAC 编码器 + 自写原生 FLAC 容器。 */
    FLAC("FLAC", "flac", "无损压缩，体积约为 WAV 的一半"),

    /** MediaCodec 的 AAC 编码器，几乎所有 Android 设备都有。 */
    AAC("AAC (.m4a)", "m4a", "有损压缩，体积小，兼容性最好"),

    /** Android 平台没有 MP3 编码器，由项目自带的 LAME 提供。 */
    MP3("MP3", "mp3", "有损压缩，兼容性最广"),
}

/** 一次导出的结果。 */
data class ExportResult(
    val file: File,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
)

/**
 * 把曲目渲染成音频文件。
 *
 * 数据来源是 BASS 的**解码流**：用 BASS_STREAM_DECODE 建流后，
 * BASS 不会推到音频设备，而是由我们用 BASS_ChannelGetData 一帧帧取 PCM，
 * 这样能达到比实时播放快得多的导出速度（一首 1 分钟的曲子几秒内完成）。
 *
 * 不设 BASS_SAMPLE_FLOAT，因此拿到的是 16 位交错 PCM，
 * 正好是 WAV / AAC / MP3 / FLAC 编码器都接受的输入格式。
 *
 * FLAC 走 MediaCodec 的编码器，但 MediaMuxer 没有 FLAC 容器，
 * 所以这里自己拼原生 FLAC：写 "fLaC" + STREAMINFO 元数据块，
 * 再把编码器吐出的每一帧原样追加——FLAC 的帧本身就是自包含的，
 * 可以安全拼接。
 */
object AudioExporter {

    private const val TAG = "AudioExporter"

    /** 每次向 BASS 拉取的字节数。 */
    private const val PULL_BYTES = 64 * 1024

    private const val MIME_AAC = "audio/mp4a-latm"
    private const val AAC_BITRATE = 192_000

    private const val MIME_FLAC = "audio/flac"

    private val MIDI_EXT = setOf("mid", "midi", "rmi", "kar", "smf")

    /** 该设备能不能导出这种格式。 */
    fun isSupported(format: ExportFormat): Boolean = when (format) {
        ExportFormat.WAV -> true
        ExportFormat.AAC -> findEncoder(MIME_AAC) != null
        ExportFormat.FLAC -> findEncoder(MIME_FLAC) != null
        ExportFormat.MP3 -> Mp3Encoder.isAvailable()
    }

    private fun findEncoder(mime: String): String? {
        return try {
            MediaCodecListCompat.encodersFor(mime).firstOrNull()
        } catch (e: Throwable) {
            Log.w(TAG, "查询编码器失败 $mime", e)
            null
        }
    }

    /**
     * 执行导出。
     *
     * @param source        要导出的曲目
     * @param soundFontPath 当前选中的音色库（MIDI 才有意义）
     * @param outFile       输出文件
     * @param onProgress    已处理毫秒 / 总毫秒
     */
    suspend fun export(
        source: com.fumi.voice.model.MidiTrack,
        soundFontPath: String?,
        format: ExportFormat,
        outFile: File,
        onProgress: (processedMs: Long, totalMs: Long) -> Unit,
    ): ExportResult = withContext(Dispatchers.IO) {
        val decoder = createDecoder(source.path, soundFontPath)
        if (decoder == 0) throw IllegalStateException("无法解码该文件")
        try {
            val info = BASS.BASS_CHANNELINFO()
            if (!BASS.BASS_ChannelGetInfo(decoder, info)) {
                throw IllegalStateException("读取音频参数失败")
            }
            val sampleRate = if (info.freq > 0) info.freq else 44100
            val channels = if (info.chans > 0) info.chans else 2
            val totalBytes = BASS.BASS_ChannelGetLength(decoder, BASS.BASS_POS_BYTE)
            val totalMs = if (totalBytes > 0) {
                (BASS.BASS_ChannelBytes2Seconds(decoder, totalBytes) * 1000).toLong()
            } else {
                0L
            }

            Log.i(
                TAG,
                "开始导出: ${source.title} -> ${format.label} ($sampleRate Hz, $channels ch, 共 ${totalMs}ms)",
            )

            val progress: (Long) -> Unit = { pcmBytes ->
                if (totalMs > 0) {
                    val ms = pcmBytes / (sampleRate.toLong() * channels * 2) * 1000
                    onProgress(ms.coerceAtMost(totalMs), totalMs)
                }
            }

            val written = when (format) {
                ExportFormat.WAV -> writeWav(outFile, decoder, sampleRate, channels, progress)

                ExportFormat.AAC -> encodeWithCodec(
                    decoder, sampleRate, channels, progress,
                    mime = MIME_AAC,
                    configure = {
                        it.setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
                        it.setInteger(
                            MediaFormat.KEY_AAC_PROFILE,
                            MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                        )
                    },
                    sink = mp4Sink(outFile),
                )

                ExportFormat.FLAC -> encodeWithCodec(
                    decoder, sampleRate, channels, progress,
                    mime = MIME_FLAC,
                    configure = {},
                    sink = flacSink(outFile, sampleRate, channels, totalSamples(totalBytes, channels)),
                )

                ExportFormat.MP3 -> Mp3Encoder.encode(
                    outFile, decoder, sampleRate, channels, progress,
                )
            }

            Log.i(TAG, "导出完成: ${outFile.name}, ${outFile.length()} 字节 (编码数据 $written 字节)")
            onProgress(totalMs, totalMs)
            ExportResult(outFile, totalMs, sampleRate, channels)
        } finally {
            BASS.BASS_StreamFree(decoder)
            if (fontHandle != 0) {
                BASSMIDI.BASS_MIDI_FontFree(fontHandle)
                fontHandle = 0
            }
        }
    }

    private var fontHandle = 0

    /**
     * 按扩展名建解码流。
     * MIDI 走 BASSMIDI 并挂上音色库；音频交给对应的 BASS 解码插件。
     */
    private fun createDecoder(path: String, soundFontPath: String?): Int {
        val ext = File(path).extension.lowercase()
        return if (ext in MIDI_EXT) {
            val decoder = BASSMIDI.BASS_MIDI_StreamCreateFile(
                path, 0L, 0L,
                BASSMIDI.BASS_MIDI_NOSYSRESET or BASSMIDI.BASS_MIDI_SINCINTER or BASS.BASS_STREAM_DECODE,
                44100,
            )
            if (decoder == 0) {
                Log.e(TAG, "MIDI 解码流创建失败: ${BASS.BASS_ErrorGetCode()}")
                return 0
            }
            if (!soundFontPath.isNullOrBlank()) {
                fontHandle = BASSMIDI.BASS_MIDI_FontInit(soundFontPath, BASSMIDI.BASS_MIDI_NOSYSRESET)
                if (fontHandle != 0) {
                    BASSMIDI.BASS_MIDI_FontLoad(fontHandle, -1, -1)
                    val font = BASSMIDI.BASS_MIDI_FONT().apply {
                        font = fontHandle
                        preset = -1
                        bank = 0
                    }
                    BASSMIDI.BASS_MIDI_StreamSetFonts(decoder, arrayOf(font), 1)
                } else {
                    Log.w(TAG, "音色库装载失败，导出将使用内置音色: ${BASS.BASS_ErrorGetCode()}")
                }
            }
            decoder
        } else {
            when (ext) {
                "flac" -> BASSFLAC.BASS_FLAC_StreamCreateFile(path, 0L, 0L, BASS.BASS_STREAM_DECODE)
                "wv" -> BASSWV.BASS_WV_StreamCreateFile(path, 0L, 0L, BASS.BASS_STREAM_DECODE)
                else -> BASS.BASS_StreamCreateFile(path, 0L, 0L, BASS.BASS_STREAM_DECODE)
            }
        }
    }

    /** 从解码流里拉一段 16 位交错 PCM，返回实际字节数；<=0 表示结束。 */
    private fun pull(decoder: Int, sink: ByteBuffer, maxBytes: Int): Int {
        sink.clear()
        val read = BASS.BASS_ChannelGetData(decoder, sink, maxBytes)
        return if (read <= 0) 0 else read
    }

    /** 字节数换算成采样帧数（每个采样点 channels 个 16 位样本）。 */
    private fun totalSamples(totalBytes: Long, channels: Int): Long =
        if (channels > 0 && totalBytes > 0) totalBytes / (channels * 2L) else 0L

    // ---------------- WAV ----------------

    /**
     * 直接写 16 位 PCM 的 WAV。
     * 先占位 44 字节头，数据写完后回填长度——避免为了知道大小而把整首歌缓存在内存里。
     */
    private suspend fun writeWav(
        outFile: File,
        decoder: Int,
        sampleRate: Int,
        channels: Int,
        onProgress: (Long) -> Unit,
    ): Long {
        val buffer = ByteBuffer.allocateDirect(PULL_BYTES)
        var dataBytes = 0L
        RandomAccessFile(outFile, "rw").use { raf ->
            raf.setLength(0)
            raf.write(ByteArray(44))

            while (true) {
                coroutineContext.ensureActive()
                val read = pull(decoder, buffer, PULL_BYTES)
                if (read <= 0) break
                val bytes = ByteArray(read)
                buffer.position(0)
                buffer.limit(read)
                buffer.get(bytes)
                raf.write(bytes)
                dataBytes += read
                onProgress(dataBytes)
            }

            raf.seek(0)
            raf.write(wavHeader(dataBytes, sampleRate, channels))
        }
        return dataBytes + 44
    }

    private fun wavHeader(dataBytes: Long, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        val out = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt((36 + dataBytes).toInt())
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII))
        out.putInt(16)                       // fmt 块长度
        out.putShort(1)                      // PCM
        out.putShort(channels.toShort())
        out.putInt(sampleRate)
        out.putInt(byteRate)
        out.putShort((channels * 2).toShort()) // 块对齐
        out.putShort(16)                     // 位深
        out.put("data".toByteArray(Charsets.US_ASCII))
        out.putInt(dataBytes.toInt())
        return out.array()
    }

    // ---------------- MediaCodec 通用编码 ----------------

    /**
     * 编码结果的落盘策略。
     *
     * AAC 交给系统 MediaMuxer 封进 MP4，FLAC 由我们自己拼原生容器，
     * 两者只有"怎么写"不同，"怎么编码"是共用的。
     */
    private interface EncodedSink {
        /** 编码器给出输出格式时回调；返回 true 表示可以开始接收数据。 */
        fun onFormat(format: MediaFormat): Boolean

        fun write(buffer: ByteBuffer, info: MediaCodec.BufferInfo)

        fun close()
    }

    /**
     * 用 MediaCodec 编码。
     *
     * 采用同步 API：拉一块 PCM → 塞进输入缓冲 → 排空所有可用输出缓冲，
     * 直到编码器报出 END_OF_STREAM。
     */
    private suspend fun encodeWithCodec(
        decoder: Int,
        sampleRate: Int,
        channels: Int,
        onProgress: (Long) -> Unit,
        mime: String,
        configure: (MediaFormat) -> Unit,
        sink: EncodedSink,
    ): Long {
        val codecName = findEncoder(mime) ?: throw IllegalStateException("设备不支持 $mime 编码")
        val format = MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, PULL_BYTES)
            configure(this)
        }

        val codec = MediaCodec.createByCodecName(codecName)
        val bufferInfo = MediaCodec.BufferInfo()
        val pcm = ByteBuffer.allocateDirect(PULL_BYTES)
        var ready = false
        var presentationUs = 0L
        var inputDone = false
        var outputDone = false
        var totalPcm = 0L
        var written = 0L

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            while (!outputDone) {
                coroutineContext.ensureActive()

                // 先把编码器已经产出的数据全部取走。
                // 这里必须排空：每次只取一个输出缓冲的话，缓冲池很快被占满，
                // 编码器随即停止接收输入，整个导出会被拖到接近实时。
                var producedAny = false
                while (true) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        ready = sink.onFormat(codec.outputFormat)
                        continue
                    }
                    if (outIndex < 0) continue

                    val outBuf = codec.getOutputBuffer(outIndex)
                    val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (outBuf != null && bufferInfo.size > 0 && !isConfig && ready) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        sink.write(outBuf, bufferInfo)
                        written += bufferInfo.size
                        producedAny = true
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                        break
                    }
                }
                if (outputDone) break

                if (inputDone) {
                    // 输入已结束，等编码器把最后几帧吐出来
                    if (!producedAny) Thread.sleep(5)
                    continue
                }

                val inIndex = codec.dequeueInputBuffer(20_000)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)
                    val read = pull(decoder, pcm, PULL_BYTES)
                    if (read > 0 && inBuf != null) {
                        pcm.position(0)
                        pcm.limit(read)
                        inBuf.clear()
                        // 输入缓冲可能比一次拉取量小，分片喂给它
                        var fed = 0
                        while (fed < read) {
                            val piece = minOf(inBuf.remaining(), read - fed)
                            if (piece <= 0) break
                            pcm.position(fed)
                            pcm.limit(fed + piece)
                            inBuf.put(pcm)
                            fed += piece
                        }
                        codec.queueInputBuffer(inIndex, 0, read, presentationUs, 0)
                        presentationUs += read.toLong() * 1_000_000L / (sampleRate * channels * 2)
                        totalPcm += read
                        onProgress(totalPcm)
                    } else {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { sink.close() }
        }
        return written
    }

    /** AAC → MP4（.m4a），交给系统 MediaMuxer 封装。 */
    private fun mp4Sink(outFile: File): EncodedSink = object : EncodedSink {
        private val muxer =
            MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private var trackIndex = -1
        private var started = false

        override fun onFormat(format: MediaFormat): Boolean {
            trackIndex = muxer.addTrack(format)
            muxer.start()
            started = true
            return true
        }

        override fun write(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (started) muxer.writeSampleData(trackIndex, buffer, info)
        }

        override fun close() {
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    // ---------------- FLAC：自写原生容器 ----------------

    /**
     * FLAC → 原生 .flac 文件。
     *
     * MediaMuxer 没有 FLAC 容器，所以文件头自己写：
     *   "fLaC" + 元数据块头(4 字节) + STREAMINFO(34 字节)
     * 之后编码器每吐出一个输出缓冲就直接追加——FLAC 的帧是自包含的，
     * 从头解到尾就能拼出完整文件，不需要任何长度回填。
     */
    private fun flacSink(
        outFile: File,
        sampleRate: Int,
        channels: Int,
        totalSamples: Long,
    ): EncodedSink = object : EncodedSink {
        private val raf = RandomAccessFile(outFile, "rw").apply { setLength(0) }

        override fun onFormat(format: MediaFormat): Boolean {
            val streamInfo = extractStreamInfo(format.getByteBuffer("csd-0"))
                ?: throw IllegalStateException("FLAC 编码器没有给出 STREAMINFO")
            val header = flacHeader(streamInfo, sampleRate, channels, totalSamples)
            raf.write(header)
            Log.i(TAG, "FLAC 容器头: ${header.size} 字节, 总采样数=$totalSamples")
            return true
        }

        override fun write(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            val dup = buffer.duplicate()
            dup.position(info.offset)
            dup.limit(info.offset + info.size)
            val bytes = ByteArray(info.size)
            dup.get(bytes)
            raf.write(bytes)
        }

        override fun close() {
            runCatching { raf.close() }
        }
    }

    /**
     * 从 csd-0 里取出 34 字节的 STREAMINFO。
     *
     * 各家实现给的形态不一样：可能是一整块元数据（"fLaC" + 块头 + STREAMINFO），
     * 也可能只有裸的 STREAMINFO。这里按魔数和长度判断，并把实际排布打进日志，
     * 万一以后遇到没见过的形态可以直接对照。
     */
    private fun extractStreamInfo(csd: ByteBuffer?): ByteArray? {
        if (csd == null || csd.remaining() < 34) return null
        val bytes = ByteArray(csd.remaining())
        csd.duplicate().get(bytes)
        Log.i(TAG, "FLAC csd-0: ${bytes.size} 字节, 开头=${hex(bytes, 12)}")

        val magic = findMagic(bytes, "fLaC")
        return when {
            // 完整元数据块：跳过 "fLaC" 和 4 字节块头
            magic >= 0 && bytes.size >= magic + 42 ->
                bytes.copyOfRange(magic + 8, magic + 42)

            // 裸 STREAMINFO
            bytes.size == 34 -> bytes

            // 少了 "fLaC" 但有块头
            bytes.size >= 38 -> bytes.copyOfRange(bytes.size - 34, bytes.size)

            else -> null
        }
    }

    /**
     * 拼 FLAC 文件头。
     *
     * 块头 4 字节 = 1 字节标志（最高位 1 表示"这是最后一个元数据块"，
     * 低 7 位是块类型，0 = STREAMINFO）+ 3 字节大端长度（固定 34）。
     */
    private fun flacHeader(
        streamInfo: ByteArray,
        sampleRate: Int,
        channels: Int,
        totalSamples: Long,
    ): ByteArray {
        val si = streamInfo.copyOf(34)
        // STREAMINFO 是紧凑的位域，这里按位覆写我们自己确知的字段，
        // 免得依赖编码器给的（流式编码时采样率/声道数偶尔会是占位值）。
        writeBits(si, 80, 20, sampleRate.toLong())
        writeBits(si, 100, 3, (channels - 1).toLong())
        writeBits(si, 103, 5, 15L)
        if (totalSamples > 0) writeBits(si, 108, 36, totalSamples)

        val out = ByteArray(42)
        "fLaC".toByteArray(Charsets.US_ASCII).copyInto(out, 0)
        out[4] = 0x80.toByte()
        out[7] = 34
        si.copyInto(out, 8)
        return out
    }

    /** 按位（MSB 在前）往字节数组里写一个整数。 */
    private fun writeBits(buf: ByteArray, bitOffset: Int, bitLength: Int, value: Long) {
        for (i in 0 until bitLength) {
            val bit = ((value shr (bitLength - 1 - i)) and 1L).toInt()
            val pos = bitOffset + i
            val byteIndex = pos ushr 3
            val mask = 1 shl (7 - (pos and 7))
            buf[byteIndex] = if (bit == 1) {
                (buf[byteIndex].toInt() or mask).toByte()
            } else {
                (buf[byteIndex].toInt() and mask.inv()).toByte()
            }
        }
    }

    private fun findMagic(bytes: ByteArray, magic: String): Int {
        val m = magic.toByteArray(Charsets.US_ASCII)
        if (bytes.size < m.size) return -1
        outer@ for (i in 0..bytes.size - m.size) {
            for (j in m.indices) if (bytes[i + j] != m[j]) continue@outer
            return i
        }
        return -1
    }

    private fun hex(bytes: ByteArray, count: Int): String =
        bytes.take(count).joinToString(" ") { "%02x".format(it) }
}
