package com.fumi.voice.audio

import android.content.Context
import android.util.Log
import com.un4seen.bass.BASS
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 10 段均衡器的频段定义。
 *
 * 31Hz 到 16kHz 按约一个八度等程分布，Q=1 时相邻频段刚好首尾相接。
 */
object EqBands {
    val frequencies = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val labels = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    /** 增益范围（dB）。 */
    const val MIN_DB = -12f
    const val MAX_DB = 12f
}

/** 均衡器预设。 */
data class EqPreset(val name: String, val gains: FloatArray) {
    override fun equals(other: Any?): Boolean =
        other is EqPreset && name == other.name && gains.contentEquals(other.gains)

    override fun hashCode(): Int = 31 * name.hashCode() + gains.contentHashCode()
}

/**
 * 10 段均衡器。
 *
 * 音频处理走 BASS 的 DSP 回调（[BASS.DSPPROC]），每条频段是一个 peaking（bell）
 * 双二阶滤波器，10 段级联后作用于 32 位浮点采样。
 *
 * 为什么不用 android.media.audiofx.Equalizer：BASS 在 Android 上走 OpenSL ES /
 * AAudio，不暴露 AudioTrack 的 session id，那个 API 根本没有作用对象。
 * 为什么不用 BASS_FX 的 BFX_PEAKEQ：libbass_fx.so 的参数容器类字段名需要与
 * native 端严格一致，本项目没有取到该清单；而且 BASS 对单通道可挂的 FX 数量有
 * 限制，10 段不一定放得下。自写 DSP 则完全可控。
 *
 * 算法与之前 C++ 版本逐行对应（同样的 Q=1、同样的 A=10^(dB/40) 系数、
 * 同样的「每缓冲区向目标增益靠拢 1/4」平滑），因此听感与行为保持不变。
 */
class Equalizer(context: Context) : BASS.DSPPROC {

    companion object {
        private const val TAG = "Equalizer"
        private const val PREFS = "fumi_equalizer"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_GAINS = "gains"
        private const val KEY_PRESET = "preset"
        private const val SEPARATOR = ","

        private const val BAND_COUNT = 10
        private const val MAX_CHANNELS = 8

        val presets: List<EqPreset> = listOf(
            EqPreset("平坦", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
            EqPreset("流行", floatArrayOf(-1f, 0f, 1f, 2f, 3f, 2f, 1f, 0f, -1f, -1f)),
            EqPreset("摇滚", floatArrayOf(4f, 3f, 2f, 0f, -1f, -1f, 0f, 2f, 3f, 4f)),
            EqPreset("爵士", floatArrayOf(3f, 2f, 1f, 1f, -1f, -1f, 0f, 1f, 2f, 2f)),
            EqPreset("古典", floatArrayOf(3f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 3f)),
            EqPreset("低音增强", floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
            EqPreset("高音增强", floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 4f, 5f, 6f)),
            EqPreset("人声", floatArrayOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f)),
        )
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val currentGains: FloatArray = loadGains()

    private var presetIndex: Int = prefs.getInt(KEY_PRESET, 0)

    var enabled: Boolean = prefs.getBoolean(KEY_ENABLED, false)
        private set

    // ---- DSP 状态（只在音频线程与 attach 时访问） ----

    /** 用户设定的目标增益（dB）。 */
    private val targetDb = FloatArray(BAND_COUNT)

    /** 实际生效的增益，按缓冲区向目标平滑靠拢，避免拖动推子时爆音。 */
    private val currentDb = FloatArray(BAND_COUNT)

    private val b0 = FloatArray(BAND_COUNT)
    private val b1 = FloatArray(BAND_COUNT)
    private val b2 = FloatArray(BAND_COUNT)
    private val a1 = FloatArray(BAND_COUNT)
    private val a2 = FloatArray(BAND_COUNT)

    private val x1 = Array(BAND_COUNT) { FloatArray(MAX_CHANNELS) }
    private val x2 = Array(BAND_COUNT) { FloatArray(MAX_CHANNELS) }
    private val y1 = Array(BAND_COUNT) { FloatArray(MAX_CHANNELS) }
    private val y2 = Array(BAND_COUNT) { FloatArray(MAX_CHANNELS) }

    private var channels = 2
    private var sampleRate = 44100

    /** DSP 回调里复用，避免每个缓冲区都分配数组。 */
    private var scratch = FloatArray(0)

    private var dspHandle = 0
    private var dspChannel = 0

    init {
        currentGains.copyInto(targetDb)
        currentGains.copyInto(currentDb)
        resetFilterState()
        recomputeAll()
    }

    // ---------------- 对外 API（与界面约定保持不变） ----------------

    /** 当前 10 段增益的副本。 */
    fun gains(): FloatArray = currentGains.copyOf()

    /** 手动拖动推子后，当前就不再对应任何预设。 */
    fun setGain(index: Int, db: Float) {
        if (index !in currentGains.indices) return
        val clamped = db.coerceIn(EqBands.MIN_DB, EqBands.MAX_DB)
        if (currentGains[index] == clamped) return
        currentGains[index] = clamped
        presetIndex = findPresetIndex(currentGains)
        persist()
        pushGains()
    }

    fun applyPreset(index: Int) {
        val preset = presets.getOrNull(index) ?: return
        preset.gains.copyInto(currentGains)
        presetIndex = index
        persist()
        pushGains()
    }

    fun reset() {
        applyPreset(0)
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        if (value) pushGains()
    }

    /** 当前对应哪个预设，手动改过则为 -1。 */
    fun presetIndex(): Int = presetIndex

    /**
     * 把均衡器挂到新创建的音频流上。
     * 换曲后必须重新挂载，否则 DSP 会指向已经释放的通道。
     */
    fun attach(streamHandle: Int) {
        if (streamHandle == 0) return
        detach()
        val info = BASS.BASS_CHANNELINFO()
        if (BASS.BASS_ChannelGetInfo(streamHandle, info)) {
            sampleRate = if (info.freq > 0) info.freq else 44100
            channels = if (info.chans > 0) info.chans else 2
            if (channels > MAX_CHANNELS) channels = MAX_CHANNELS
        }
        resetFilterState()
        recomputeAll()
        val handle = try {
            BASS.BASS_ChannelSetDSP(streamHandle, this, null, 0)
        } catch (e: Throwable) {
            Log.e(TAG, "BASS_ChannelSetDSP 失败", e)
            0
        }
        if (handle != 0) {
            dspHandle = handle
            dspChannel = streamHandle
            pushGains()
            Log.i(TAG, "EQ 已挂载: stream=$streamHandle dsp=$handle freq=$sampleRate chans=$channels")
        }
    }

    fun detach() {
        if (dspHandle != 0) {
            runCatching { BASS.BASS_ChannelRemoveDSP(dspChannel, dspHandle) }
            dspHandle = 0
            dspChannel = 0
        }
    }

    // ---------------- DSP 回调 ----------------

    override fun DSPPROC(handle: Int, channel: Int, buffer: ByteBuffer, length: Int, user: Any?) {
        if (!enabled || length <= 0) return
        val sampleCount = length / 4
        if (sampleCount <= 0) return

        if (scratch.size < sampleCount) scratch = FloatArray(sampleCount)
        val samples = scratch

        // 增益向目标平滑过渡：每个缓冲区走 1/4 的差值
        var coefficientsChanged = false
        for (i in 0 until BAND_COUNT) {
            val diff = targetDb[i] - currentDb[i]
            if (diff > -0.01f && diff < 0.01f) {
                if (currentDb[i] != targetDb[i]) {
                    currentDb[i] = targetDb[i]
                    coefficientsChanged = true
                }
            } else {
                currentDb[i] += diff * 0.25f
                coefficientsChanged = true
            }
        }
        if (coefficientsChanged) recomputeAll()

        val position = buffer.position()
        buffer.position(0)
        val floats = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
        floats.get(samples, 0, sampleCount)

        val ch = channels
        val frames = sampleCount / ch
        var index = 0
        for (frame in 0 until frames) {
            for (c in 0 until ch) {
                var sample = samples[index]
                for (band in 0 until BAND_COUNT) {
                    val out = b0[band] * sample +
                        b1[band] * x1[band][c] + b2[band] * x2[band][c] -
                        a1[band] * y1[band][c] - a2[band] * y2[band][c]
                    x2[band][c] = x1[band][c]
                    x1[band][c] = sample
                    y2[band][c] = y1[band][c]
                    y1[band][c] = out
                    sample = out
                }
                samples[index] = sample
                index++
            }
        }

        floats.position(0)
        floats.put(samples, 0, sampleCount)
        buffer.position(position)
    }

    // ---------------- 内部 ----------------

    private fun pushGains() {
        currentGains.copyInto(targetDb)
    }

    /** 重算某一段的 peaking 系数。 */
    private fun computeBand(index: Int) {
        var freq = EqBands.frequencies[index].toDouble()
        val nyquist = sampleRate * 0.5
        if (freq >= nyquist) freq = nyquist * 0.9

        // Q = 1.0 大约对应一个八度的带宽，10 段等程分布刚好首尾相接
        val q = 1.0
        val a = 10.0.pow(currentDb[index] / 40.0)
        val w0 = 2.0 * Math.PI * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)
        val a0 = 1.0 + alpha / a

        b0[index] = ((1.0 + alpha * a) / a0).toFloat()
        b1[index] = ((-2.0 * cosW0) / a0).toFloat()
        b2[index] = ((1.0 - alpha * a) / a0).toFloat()
        a1[index] = ((-2.0 * cosW0) / a0).toFloat()
        a2[index] = ((1.0 - alpha / a) / a0).toFloat()
    }

    private fun recomputeAll() {
        for (i in 0 until BAND_COUNT) computeBand(i)
    }

    /** 清空滤波器历史，换曲/换声道数时必须调用，否则会残留上一首的尾音。 */
    private fun resetFilterState() {
        for (band in 0 until BAND_COUNT) {
            java.util.Arrays.fill(x1[band], 0f)
            java.util.Arrays.fill(x2[band], 0f)
            java.util.Arrays.fill(y1[band], 0f)
            java.util.Arrays.fill(y2[band], 0f)
        }
    }

    private fun persist() {
        prefs.edit()
            .putInt(KEY_PRESET, presetIndex)
            .putString(KEY_GAINS, currentGains.joinToString(SEPARATOR))
            .apply()
    }

    private fun loadGains(): FloatArray {
        val stored = prefs.getString(KEY_GAINS, null)
            ?: return presets.first().gains.copyOf()
        val parsed = stored.split(SEPARATOR)
            .mapNotNull { it.toFloatOrNull() }
            .toFloatArray()
        if (parsed.size != EqBands.frequencies.size) return presets.first().gains.copyOf()
        return parsed
    }

    private fun findPresetIndex(gains: FloatArray): Int =
        presets.indexOfFirst { it.gains.contentEquals(gains) }
}
