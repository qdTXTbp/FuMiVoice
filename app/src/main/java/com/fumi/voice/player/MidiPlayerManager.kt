package com.fumi.voice.player

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fumi.voice.audio.Equalizer
import com.fumi.voice.midi.MidiParser
import com.fumi.voice.model.MidiTrack
import com.fumi.voice.ui.NoteData
import com.un4seen.bass.BASS
import com.un4seen.bass.BASSMIDI
import com.un4seen.bass.BASSFLAC
import com.un4seen.bass.BASSWV
import com.un4seen.bass.BASS_FX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** 正在发声的音符，供钢琴瀑布高亮琴键。 */
data class ActiveNote(
    val key: Int,
    val channel: Int,
    val velocity: Int,
    val startTime: Long,
)

enum class PlayState { STOPPED, PLAYING, PAUSED }

/** 循环模式：单曲循环 / 列表循环 / 随机播放。 */
enum class RepeatMode {
    SINGLE, LIST, SHUFFLE;

    fun next(): RepeatMode = when (this) {
        SINGLE -> LIST
        LIST -> SHUFFLE
        SHUFFLE -> SINGLE
    }
}

/** 界面与通知栏共用的播放状态快照。 */
data class PlayerUiState(
    val playState: PlayState = PlayState.STOPPED,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val track: MidiTrack? = null,
    val repeatMode: RepeatMode = RepeatMode.LIST,
    val tempo: Float = 1f,
    val pitchSemitones: Int = 0,
    val metronomeEnabled: Boolean = false,
    val metronomeVolume: Float = 0.6f,
    /** 当前曲目是否有可用的节拍网格（只有 MIDI 有）。 */
    val metronomeAvailable: Boolean = false,
    val volume: Float = 1f,
    val soundFontName: String? = null,
    val hasSoundFont: Boolean = false,
    /** 正在播放乐器试听片段：此时不应拉起通知栏前台服务。 */
    val isPreview: Boolean = false,
)

/** 曲目容器类型，决定用哪个 BASS 接口建流。 */
private enum class StreamKind { MIDI, FLAC, WAVPACK, OTHER }

/**
 * 单个 MIDI 通道的混音状态。
 *
 * [volume] / [pan] 都是标准 MIDI 量程 0-127（声像 64 居中），
 * [program] 为 -1 表示文件里没写、暂时未知。
 */
data class ChannelState(
    val index: Int,
    val program: Int = -1,
    val volume: Int = 100,
    val pan: Int = 64,
    val muted: Boolean = false,
    val soloed: Boolean = false,
) {
    /** 是否属于通道 10（GM 鼓组）。 */
    val isDrum: Boolean get() = index == 9
}

/**
 * 基于 BASS / BASSMIDI 的 MIDI 播放器。
 *
 * 这里改用 un4seen **官方 Android Java 绑定**（com.un4seen.bass.*），
 * 取代了早期自写的 bass_jni.cpp —— 官方绑定就编译在 libbass.so 里，
 * 换来的是整条 BASS API（FX、DSP、插件、标记、事件查询）都能直接用，
 * 不必每加一个功能就补一遍 JNI。
 *
 * 对外 API 与重构前完全一致，界面层无需改动。
 */
class MidiPlayerManager(val context: Context) {

    companion object {
        private const val TAG = "MidiPlayer"
        private const val SAMPLE_RATE = 44100
        private const val POSITION_INTERVAL_MS = 30L

        private val MIDI_EXT = setOf("mid", "midi", "rmi", "kar", "smf")
        private val FLAC_EXT = setOf("flac")
        private val WV_EXT = setOf("wv")

        // 注：Tracker 模块音乐（mod/xm/s3m/it/mtm）曾接过一轮，但实测本机
        // libbass.so 加载 .mod 时解码与直接播放都返回 BASS_ERROR_FILEFORM(41)，
        // 故扫描器与这里一并撤掉，详见 MidiLibraryManager 里的说明。

        /** 节拍器重音与非重音的音高，差一个纯五度，听感上容易区分。 */
        private const val CLICK_ACCENT_HZ = 1500.0
        private const val CLICK_NORMAL_HZ = 1000.0
        private const val CLICK_DURATION_MS = 50
    }

    private var streamHandle: Int = 0
    private var fontHandle: Int = 0
    private var streamKind: StreamKind = StreamKind.OTHER

    /** 当前播放通道是否套了 BASS_FX 的 tempo 流（决定倍速能否实时生效）。 */
    private var tempoStreamActive = false

    /** 上一次广播出去的高亮键集合，只有真正变化时才通知界面。 */
    private var lastActiveSignature: String = ""

    @Volatile var playState: PlayState = PlayState.STOPPED
        private set
    @Volatile var positionMs: Long = 0
        private set
    @Volatile var durationMs: Long = 0
        private set
    @Volatile var volume: Float = 1.0f
        private set

    /** 倍速，1.0 为原速。 */
    @Volatile var tempo: Float = 1.0f
        private set

    /** 音调偏移，单位是半音，0 为原调（±12 一个八度）。 */
    @Volatile var pitchSemitones: Int = 0
        private set

    /** 节拍器：开关与独立音量。 */
    @Volatile var metronomeEnabled: Boolean = false
        private set
    @Volatile var metronomeVolume: Float = 0.6f
        private set

    /** 每一拍的毫秒位置（源时间轴），由 MIDI 的速度表推出；音频/模块为空。 */
    private var beatTimesMs: List<Long> = emptyList()

    /** 节拍器的两个 click 流：重音 / 非重音。 */
    private var clickAccentHandle = 0
    private var clickNormalHandle = 0

    /** 节拍器排点协程。 */
    private var metronomeJob: Job? = null

    @Volatile var repeatMode: RepeatMode = RepeatMode.LIST
        private set

    /** 当前曲目队列。 */
    var playlist: List<MidiTrack> = emptyList()
        private set

    @Volatile var currentIndex: Int = -1
        private set

    /** 当前正在播放（或已装载）的曲目。 */
    @Volatile var currentTrack: MidiTrack? = null
        private set

    /** 当前生效的音色库文件名。 */
    @Volatile var soundFontName: String? = null
        private set

    /** 10 段均衡器，换曲后会自动重新挂载。 */
    val equalizer: Equalizer = Equalizer(context)

    /** 16 个通道的混音状态，混音台界面直接订阅。 */
    private val _channels = MutableStateFlow(List(16) { ChannelState(it) })
    val channels: StateFlow<List<ChannelState>> = _channels.asStateFlow()

    /** 当前曲目实际用到的通道号（升序）；混音台默认只列出这些通道。 */
    var usedChannels: List<Int> = emptyList()
        private set

    /** 试听模式：播放的是乐器试听片段，结束时不应推进队列。 */
    @Volatile private var previewing = false

    /** 上一次已上报的曲目路径，避免同一首重复计数。 */
    private var lastReportedPath: String? = null

    // 钢琴瀑布数据
    private val activeNotes = mutableMapOf<String, ActiveNote>()
    val activeNoteList: List<ActiveNote>
        get() = synchronized(activeNotes) { activeNotes.values.toList() }

    var waterfallNotes: List<NoteData> = emptyList()
        private set

    /** 速度标记，供瀑布换算"一小节多少毫秒"，使滚动速度跟随曲速。 */
    var waterfallTempoMarks: List<MidiParser.TempoMark> = emptyList()
        private set

    /** 每小节拍数，来自拍号事件。 */
    var waterfallBeatsPerBar: Int = 4
        private set

    private var stateListener: ((PlayState, Long, Long) -> Unit)? = null
    private var noteListener: ((List<ActiveNote>) -> Unit)? = null

    /**
     * 曲目装载成功时回调。
     * 播放历史记录、桌面小部件刷新都挂在这里——只在换曲时触发一次，
     * 不像状态流那样会因为暂停/拖动而重复回调。
     */
    var onTrackLoaded: ((MidiTrack) -> Unit)? = null

    /** 播放状态流：界面与前台服务都从这里订阅，避免互相覆盖监听。 */
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var positionJob: Job? = null

    fun setOnStateChanged(l: (PlayState, Long, Long) -> Unit) { stateListener = l }
    fun setOnNoteListener(l: (List<ActiveNote>) -> Unit) { noteListener = l }

    private fun notifyState() {
        stateListener?.invoke(playState, positionMs, durationMs)
        _state.value = PlayerUiState(
            playState = playState,
            positionMs = positionMs,
            durationMs = durationMs,
            track = currentTrack,
            repeatMode = repeatMode,
            tempo = tempo,
            pitchSemitones = pitchSemitones,
            metronomeEnabled = metronomeEnabled,
            metronomeVolume = metronomeVolume,
            metronomeAvailable = beatTimesMs.isNotEmpty(),
            volume = volume,
            soundFontName = soundFontName,
            hasSoundFont = fontHandle != 0,
            isPreview = previewing,
        )
    }

    /** 初始化 BASS 输出设备，只需调用一次。 */
    fun init(): Boolean {
        // 让 DSP 回调拿到 32 位浮点数据，自写 EQ 依赖这一点
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_FLOATDSP, 1)
        val ok = BASS.BASS_Init(-1, SAMPLE_RATE, 0)
        Log.i(TAG, "BASS init: $ok, version=${Integer.toHexString(BASS.BASS_GetVersion())}")
        if (!ok) {
            Log.e(TAG, "BASS_Init failed: ${BASS.BASS_ErrorGetCode()}")
        }
        return ok
    }

    /** 装载音色库（.sf2/.sf3/.sfz），成功后自动应用到当前流。 */
    fun loadSoundFont(path: String): Boolean {
        if (fontHandle != 0) {
            BASSMIDI.BASS_MIDI_FontFree(fontHandle)
            fontHandle = 0
        }
        fontHandle = BASSMIDI.BASS_MIDI_FontInit(path, BASSMIDI.BASS_MIDI_NOSYSRESET)
        if (fontHandle == 0) {
            Log.e(TAG, "音色库装载失败: $path, error=${BASS.BASS_ErrorGetCode()}")
            return false
        }
        BASSMIDI.BASS_MIDI_FontLoad(fontHandle, -1, -1)
        soundFontName = File(path).name
        if (streamHandle != 0 && streamKind == StreamKind.MIDI) {
            applyFontsTo(midiSource())
        }
        Log.i(TAG, "音色库已装载: $path (handle=$fontHandle)")
        notifyState()
        return true
    }

    /**
     * 真正的 MIDI 通道。
     * 套了 tempo 流时，MIDI 流是它的源；音色库与通道事件都必须作用在源上，
     * 设在 tempo 流上不会生效。
     */
    private fun midiSource(): Int {
        if (streamHandle == 0) return 0
        return if (tempoStreamActive) {
            runCatching { BASS_FX.BASS_FX_TempoGetSource(streamHandle) }.getOrDefault(streamHandle)
        } else {
            streamHandle
        }
    }

    // ---------------- 通道混音 ----------------

    /**
     * 按新曲目初始化通道状态。
     *
     * 取值来自文件里各通道首次出现的 Program / CC7 / CC10，
     * 因此界面一开始就能显示"这首曲子各通道在用什么音色、音量多少"。
     * 初始化后立刻回推一次，好处是静音/独奏在单曲循环时不会被文件自己的
     * 音量事件覆盖掉。
     */
    private fun initChannels(parsed: MidiParser.ParsedMidi?) {
        usedChannels = waterfallNotes.map { it.channel }.distinct().sorted()
        _channels.value = List(16) { i ->
            ChannelState(
                index = i,
                program = parsed?.channelPrograms?.getOrNull(i) ?: -1,
                volume = parsed?.channelVolumes?.getOrNull(i) ?: 100,
                pan = parsed?.channelPans?.getOrNull(i) ?: 64,
            )
        }
        pushAllChannels()
    }

    private fun resetChannelState() {
        usedChannels = emptyList()
        _channels.value = List(16) { ChannelState(it) }
    }

    fun setChannelVolume(index: Int, volume: Int) =
        updateChannel(index) { it.copy(volume = volume.coerceIn(0, 127)) }

    fun setChannelPan(index: Int, pan: Int) =
        updateChannel(index) { it.copy(pan = pan.coerceIn(0, 127)) }

    fun setChannelProgram(index: Int, program: Int) {
        val state = updateChannel(index) { it.copy(program = program.coerceIn(0, 127)) } ?: return
        pushProgram(state)
    }

    fun toggleChannelMute(index: Int) =
        updateChannel(index) { it.copy(muted = !it.muted) }

    fun toggleChannelSolo(index: Int) =
        updateChannel(index) { it.copy(soloed = !it.soloed) }

    /** 清掉全部静音/独奏，并把音量声像恢复成文件里的初始值。 */
    fun resetChannelMix() {
        _channels.value = _channels.value.map {
            it.copy(muted = false, soloed = false)
        }
        pushAllChannels()
    }

    private fun updateChannel(index: Int, transform: (ChannelState) -> ChannelState): ChannelState? {
        val list = _channels.value.toMutableList()
        if (index !in list.indices) return null
        list[index] = transform(list[index])
        _channels.value = list
        pushAllChannels()
        return list[index]
    }

    /**
     * 把音量与声像推给 BASS。
     *
     * 静音和独奏都是通过"音量置 0"实现的：只要有任一通道被独奏，
     * 未独奏的通道一律静音。这样不需要额外的通道开关状态。
     */
    private fun pushAllChannels() {
        val source = midiSource()
        if (source == 0 || streamKind != StreamKind.MIDI) return
        val states = _channels.value
        val anySolo = states.any { it.soloed }
        for (s in states) {
            val audible = !s.muted && (!anySolo || s.soloed)
            val vol = if (audible) s.volume else 0
            BASSMIDI.BASS_MIDI_StreamEvent(source, s.index, BASSMIDI.MIDI_EVENT_VOLUME, vol)
            BASSMIDI.BASS_MIDI_StreamEvent(source, s.index, BASSMIDI.MIDI_EVENT_PAN, s.pan)
        }
    }

    private fun pushProgram(state: ChannelState) {
        val source = midiSource()
        if (source == 0 || streamKind != StreamKind.MIDI || state.program < 0) return
        BASSMIDI.BASS_MIDI_StreamEvent(source, state.index, BASSMIDI.MIDI_EVENT_BANK, 0)
        BASSMIDI.BASS_MIDI_StreamEvent(source, state.index, BASSMIDI.MIDI_EVENT_PROGRAM, state.program)
    }

    // ---------------- 队列 ----------------

    /** 设置播放队列并立即播放第 [startIndex] 首。 */
    fun setPlaylist(tracks: List<MidiTrack>, startIndex: Int, autoPlay: Boolean = true): Boolean {
        playlist = tracks
        if (tracks.isEmpty()) {
            currentIndex = -1
            stop()
            currentTrack = null
            notifyState()
            return false
        }
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        currentIndex = index
        val ok = loadInternal(tracks[index])
        if (ok && autoPlay) play()
        return ok
    }

    fun playAt(index: Int): Boolean {
        if (index !in playlist.indices) return false
        currentIndex = index
        val ok = loadInternal(playlist[index])
        if (ok) play()
        return ok
    }

    /** 下一首（受循环模式影响）。 */
    fun next() {
        if (playlist.isEmpty()) return
        val index = when {
            repeatMode == RepeatMode.SHUFFLE && playlist.size > 1 -> randomIndexExcept(currentIndex)
            currentIndex + 1 <= playlist.lastIndex -> currentIndex + 1
            repeatMode == RepeatMode.LIST -> 0
            else -> return
        }
        playAt(index)
    }

    /** 上一首。 */
    fun previous() {
        if (playlist.isEmpty()) return
        val index = when {
            repeatMode == RepeatMode.SHUFFLE && playlist.size > 1 -> randomIndexExcept(currentIndex)
            currentIndex - 1 >= 0 -> currentIndex - 1
            repeatMode == RepeatMode.LIST -> playlist.lastIndex
            else -> return
        }
        playAt(index)
    }

    private fun randomIndexExcept(current: Int): Int {
        if (playlist.size <= 1) return 0
        var r = Random.nextInt(playlist.size)
        while (r == current) r = Random.nextInt(playlist.size)
        return r
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
        notifyState()
    }

    fun cycleRepeatMode() {
        setRepeatMode(repeatMode.next())
    }

    /** 播放曲库中的某一首（不改变队列）。 */
    fun playTrack(track: MidiTrack, autoPlay: Boolean = true): Boolean {
        currentIndex = playlist.indexOfFirst { it.path == track.path }
        val ok = loadInternal(track)
        if (ok && autoPlay) play()
        return ok
    }

    // ---------------- 装载 ----------------

    private fun loadInternal(track: MidiTrack): Boolean {
        val ok = loadPath(track.path)
        if (ok) {
            currentTrack = track
            // 同一首曲目重复装载（停止后重新播放）不重复上报，
            // 只有真的换曲才通知播放历史/小部件
            if (track.path != lastReportedPath) {
                lastReportedPath = track.path
                onTrackLoaded?.invoke(track)
            }
            notifyState()
        }
        return ok
    }

    /** 从 content:// URI 装载曲目（文件管理器"打开方式"）。 */
    fun loadMidi(uri: Uri): Boolean {
        return try {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "opened.mid"
            val tempFile = File(context.cacheDir, "opened_${name.substringAfterLast('.', "mid")}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: return false
            val parsed = runCatching { MidiParser.parse(tempFile.inputStream()) }.getOrNull()
            val track = MidiTrack(
                fileName = name,
                title = name.substringBeforeLast('.'),
                path = tempFile.absolutePath,
                sizeBytes = tempFile.length(),
                noteCount = parsed?.notes?.size ?: 0,
                durationMs = parsed?.durationMs ?: 0,
            )
            previewing = false
            loadInternal(track)
        } catch (e: Exception) {
            Log.e(TAG, "装载 MIDI 失败", e)
            false
        }
    }

    /**
     * 创建 MIDI 流。
     *
     * 走「解码流 + BASS_FX tempo 流」这条官方路径，而不是给 MIDI 流设
     * BASS_ATTRIB_MIDI_SPEED —— 实测后者既非倍数也非百分比语义，
     * 写入后位置直接冻结（写 2.0 近乎停住，写 200 完全停住），无法用于实时变速。
     *
     * BASS_FX_TempoCreate 要求源必须是解码通道（BASS_STREAM_DECODE），
     * 返回的才是可播放的通道，变速/变调都作用在它上面。
     * 带上 BASS_FX_FREESOURCE 后，释放播放通道会一并释放解码流。
     *
     * 若 tempo 流创建失败，退回「直接播放 MIDI 流」，功能少一项但能出声。
     */
    private fun createMidiStream(path: String, baseFlags: Int): Int {
        val decoder = BASSMIDI.BASS_MIDI_StreamCreateFile(
            path, 0L, 0L, baseFlags or BASS.BASS_STREAM_DECODE, SAMPLE_RATE,
        )
        if (decoder != 0) {
            applyFontsTo(decoder)
            val tempoStream = BASS_FX.BASS_FX_TempoCreate(decoder, BASS_FX.BASS_FX_FREESOURCE)
            if (tempoStream != 0) {
                tempoStreamActive = true
                return tempoStream
            }
            Log.w(TAG, "BASS_FX_TempoCreate 失败，退回直接播放: ${BASS.BASS_ErrorGetCode()}")
            BASS.BASS_StreamFree(decoder)
        } else {
            Log.w(TAG, "MIDI 解码流创建失败: ${BASS.BASS_ErrorGetCode()}")
        }

        val direct = BASSMIDI.BASS_MIDI_StreamCreateFile(path, 0L, 0L, baseFlags, SAMPLE_RATE)
        if (direct != 0) applyFontsTo(direct)
        tempoStreamActive = false
        return direct
    }

    /**
     * 音频 / 模块类也套一层 BASS_FX tempo 流。
     *
     * 改动前只有 MIDI 走 tempo 流，结果是倍速对 FLAC、WavPack、MOD 都是无效的——
     * 界面上能拖但没反应。既然这条 tempo 流同样能承载音调，就统一都套上，
     * 倍速与音调因此对全库生效。
     *
     * tempo 流建不出来就退回原来那条"直接播放"的路：少两项能力但能出声，
     * 行为与改动前一致，不会因为这一步把能放的曲子变成放不了。
     */
    private fun createAudioStream(decode: () -> Int, play: () -> Int): Int {
        val decoder = decode()
        if (decoder != 0) {
            val tempoStream = BASS_FX.BASS_FX_TempoCreate(decoder, BASS_FX.BASS_FX_FREESOURCE)
            if (tempoStream != 0) {
                tempoStreamActive = true
                return tempoStream
            }
            Log.w(TAG, "音频 BASS_FX_TempoCreate 失败，退回直接播放: ${BASS.BASS_ErrorGetCode()}")
            BASS.BASS_StreamFree(decoder)
        } else {
            Log.w(TAG, "解码流创建失败: ${BASS.BASS_ErrorGetCode()}")
        }
        tempoStreamActive = false
        return play()
    }

    /** 把当前音色库挂到指定 MIDI 流上。 */
    private fun applyFontsTo(handle: Int) {
        if (handle == 0 || fontHandle == 0) return
        val font = BASSMIDI.BASS_MIDI_FONT().apply {
            this.font = fontHandle
            preset = -1
            bank = 0
        }
        val ok = BASSMIDI.BASS_MIDI_StreamSetFonts(handle, arrayOf(font), 1)
        Log.i(TAG, "BASS_MIDI_StreamSetFonts: stream=$handle font=$fontHandle ok=$ok")
    }

    /**
     * 把当前倍速应用到播放通道。
     *
     * BASS_ATTRIB_TEMPO 是**百分比变化量**：0 = 原速，+100 = 2 倍，-50 = 0.5 倍。
     * 开了抗锯齿滤波，变速后高频不会出现明显的金属感。
     */
    private fun applyTempo() {
        if (streamHandle == 0 || !tempoStreamActive) return
        BASS.BASS_ChannelSetAttribute(streamHandle, BASS.BASS_ATTRIB_TEMPO, (tempo - 1f) * 100f)
    }

    /**
     * 把当前音调应用到播放通道。
     *
     * BASS_ATTRIB_TEMPO_PITCH 的单位是**半音**：0 = 原调，+12 = 高一个八度。
     * 它和 BASS_ATTRIB_TEMPO 相互独立，所以能做到只变速不变调，
     * 或者只变调不变速（练唱时降调但不拖慢）。
     */
    private fun applyPitch() {
        if (streamHandle == 0 || !tempoStreamActive) return
        BASS.BASS_ChannelSetAttribute(
            streamHandle,
            BASS.BASS_ATTRIB_TEMPO_PITCH,
            pitchSemitones.toFloat(),
        )
    }

    private fun kindOf(path: String): StreamKind {
        val ext = File(path).extension.lowercase()
        return when (ext) {
            in MIDI_EXT -> StreamKind.MIDI
            in FLAC_EXT -> StreamKind.FLAC
            in WV_EXT -> StreamKind.WAVPACK
            else -> StreamKind.OTHER
        }
    }

    private fun loadPath(path: String): Boolean {
        releaseStream()
        previewing = false

        val kind = kindOf(path)
        streamHandle = when (kind) {
            StreamKind.MIDI -> createMidiStream(
                path,
                BASSMIDI.BASS_MIDI_NOSYSRESET or BASSMIDI.BASS_MIDI_SINCINTER,
            )

            StreamKind.FLAC -> createAudioStream(
                decode = { BASSFLAC.BASS_FLAC_StreamCreateFile(path, 0L, 0L, BASS.BASS_STREAM_DECODE) },
                play = { BASSFLAC.BASS_FLAC_StreamCreateFile(path, 0L, 0L, BASS.BASS_SAMPLE_FLOAT) },
            )

            StreamKind.WAVPACK -> createAudioStream(
                decode = { BASSWV.BASS_WV_StreamCreateFile(path, 0L, 0L, BASS.BASS_STREAM_DECODE) },
                play = { BASSWV.BASS_WV_StreamCreateFile(path, 0L, 0L, BASS.BASS_SAMPLE_FLOAT) },
            )

            // 其余可解码容器（Ogg / AIFF / WAVE / MP3 等）都走 libbass 主库
            StreamKind.OTHER -> createAudioStream(
                decode = { BASS.BASS_StreamCreateFile(path, 0L, 0L, BASS.BASS_STREAM_DECODE) },
                play = { BASS.BASS_StreamCreateFile(path, 0L, 0L, BASS.BASS_SAMPLE_FLOAT) },
            )
        }
        if (streamHandle == 0) {
            Log.e(TAG, "创建流失败: $path, error=${BASS.BASS_ErrorGetCode()}")
            return false
        }
        streamKind = kind

        BASS.BASS_ChannelSetAttribute(streamHandle, BASS.BASS_ATTRIB_VOL, volume)
        applyTempo()
        applyPitch()
        // DSP 是挂在通道上的，新流必须重新挂一次均衡器
        equalizer.attach(streamHandle)

        durationMs = readDurationMs()

        if (kind == StreamKind.MIDI) {
            var parsed: MidiParser.ParsedMidi? = null
            waterfallNotes = runCatching {
                val p = MidiParser.parse(File(path).inputStream())
                parsed = p
                waterfallTempoMarks = p.tempoMarks
                waterfallBeatsPerBar = p.beatsPerBar
                p.notes.map { n ->
                    NoteData(
                        startTimeMs = MidiParser.tickToMs(n.tick, p.tempoChanges, p.ticksPerQuarter).toDouble(),
                        durationMs = MidiParser.tickToMs(n.durationTicks, p.tempoChanges, p.ticksPerQuarter).toDouble(),
                        key = n.key,
                        channel = n.channel,
                        velocity = n.velocity,
                    )
                }
            }.getOrElse {
                Log.e(TAG, "解析瀑布音符失败", it)
                waterfallTempoMarks = emptyList()
                waterfallBeatsPerBar = 4
                emptyList()
            }
            initChannels(parsed)
            beatTimesMs = buildBeatGrid(parsed, durationMs)
        } else {
            // 音频文件没有音符可解析，直接给空，别把几 MB 的音频丢进 SMF 解析器
            waterfallTempoMarks = emptyList()
            waterfallBeatsPerBar = 4
            waterfallNotes = emptyList()
            // 没有速度表就没有节拍网格，节拍器对这类曲目不可用
            beatTimesMs = emptyList()
            resetChannelState()
        }

        // 换曲后清掉上一首残留的高亮键
        positionMs = 0
        clearActiveNotes()
        // 节拍器按新曲目的速度表重新排点
        restartMetronome()
        Log.i(TAG, "曲目已装载: $path, 类型=$kind, 时长=${durationMs}ms, 音符=${waterfallNotes.size}")
        notifyState()
        return true
    }

    /**
     * 由速度表推出每一拍的毫秒位置，供节拍器排点。
     *
     * 只对 MIDI 有意义：音频与模块没有速度表，节拍器无从对齐，网格为空，
     * 界面上节拍器会置灰。
     *
     * 这里逐拍用 tickToMs 换算，而不是"按首个 BPM 均分"——
     * 曲子中途变速时前者才跟得上。
     */
    private fun buildBeatGrid(parsed: MidiParser.ParsedMidi?, totalMs: Long): List<Long> {
        if (parsed == null || parsed.ticksPerQuarter <= 0 || totalMs <= 0) return emptyList()
        val step = parsed.ticksPerQuarter.toLong()
        // 先按"120BPM 下每拍 500ms"估个容量，省掉几次扩容
        val out = ArrayList<Long>((totalMs / 500).toInt() + 8)
        var tick = 0L
        while (true) {
            val ms = MidiParser.tickToMs(tick, parsed.tempoChanges, parsed.ticksPerQuarter)
            if (ms > totalMs) break
            out.add(ms)
            tick += step
        }
        return out
    }

    /** 播放一段乐器试听片段（不影响队列与瀑布）。 */
    fun playPreview(path: String): Boolean {
        releaseStream()
        streamHandle = createMidiStream(
            path,
            BASSMIDI.BASS_MIDI_NOSYSRESET or BASSMIDI.BASS_MIDI_SINCINTER,
        )
        if (streamHandle == 0) return false
        streamKind = StreamKind.MIDI
        BASS.BASS_ChannelSetAttribute(streamHandle, BASS.BASS_ATTRIB_VOL, volume)
        applyTempo()
        applyPitch()
        equalizer.attach(streamHandle)
        previewing = true
        waterfallNotes = emptyList()
        durationMs = readDurationMs()
        positionMs = 0
        BASS.BASS_ChannelPlay(streamHandle, false)
        playState = PlayState.PLAYING
        startPositionUpdater()
        notifyState()
        return true
    }

    // ---------------- 传输控制 ----------------

    fun play() {
        if (streamHandle == 0) {
            // 流已释放但仍有曲目：重新装载后再播放
            val track = currentTrack ?: return
            if (!loadInternal(track)) return
        }
        BASS.BASS_ChannelPlay(streamHandle, false)
        playState = PlayState.PLAYING
        startPositionUpdater()
        // 恢复播放后节拍点可能已经跑偏（暂停期间位置没动，但循环里的锚点要重来）
        restartMetronome()
        notifyState()
    }

    fun pause() {
        if (streamHandle == 0) return
        BASS.BASS_ChannelPause(streamHandle)
        playState = PlayState.PAUSED
        stopPositionUpdater()
        stopMetronome()
        silenceClicks()
        notifyState()
    }

    fun togglePlayPause() {
        when (playState) {
            PlayState.PLAYING -> pause()
            PlayState.PAUSED -> play()
            PlayState.STOPPED -> if (currentTrack != null) play()
        }
    }

    /** 停止播放并释放音频流，保留当前曲目信息。 */
    fun stop() {
        releaseStream()
        clearActiveNotes()
        playState = PlayState.STOPPED
        positionMs = 0
        stopPositionUpdater()
        stopMetronome()
        silenceClicks()
        notifyState()
    }

    private fun releaseStream() {
        if (streamHandle != 0) {
            BASS.BASS_ChannelStop(streamHandle)
            equalizer.detach()
            // 带 BASS_FX_FREESOURCE 时，这里会连 tempo 流的源一起释放
            BASS.BASS_StreamFree(streamHandle)
            streamHandle = 0
        }
        tempoStreamActive = false
        streamKind = StreamKind.OTHER
    }

    fun seekTo(ms: Long) {
        if (streamHandle == 0) return
        val clamped = ms.coerceIn(0, if (durationMs > 0) durationMs else ms)
        val pos = BASS.BASS_ChannelSeconds2Bytes(streamHandle, clamped / 1000.0)
        BASS.BASS_ChannelSetPosition(streamHandle, pos, BASS.BASS_POS_BYTE)
        positionMs = clamped
        clearActiveNotes()
        // 拖动后节拍点要按新位置重新对齐
        restartMetronome()
        notifyState()
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        if (streamHandle != 0) {
            BASS.BASS_ChannelSetAttribute(streamHandle, BASS.BASS_ATTRIB_VOL, volume)
        }
        notifyState()
    }

    /** 设置倍速（0.25x - 4x），实时生效。 */
    fun setTempo(t: Float) {
        tempo = t.coerceIn(0.25f, 4f)
        applyTempo()
        notifyState()
    }

    /** 设置音调（±12 半音），实时生效，与倍速互不影响。 */
    fun setPitch(semitones: Int) {
        pitchSemitones = semitones.coerceIn(-12, 12)
        applyPitch()
        notifyState()
    }

    // ---------------- 节拍器 ----------------

    /** 开关节拍器。曲目没有节拍网格（音频 / 模块）时不可用。 */
    fun setMetronomeEnabled(on: Boolean) {
        if (on && beatTimesMs.isEmpty()) {
            Log.w(TAG, "当前曲目没有节拍网格，节拍器不可用")
            return
        }
        metronomeEnabled = on
        restartMetronome()
        notifyState()
    }

    /** 节拍器音量（0-1），独立于曲目音量。 */
    fun setMetronomeVolume(v: Float) {
        metronomeVolume = v.coerceIn(0f, 1f)
        applyClickVolume()
        notifyState()
    }

    /**
     * 按当前节拍网格与播放状态重新排点。
     *
     * 换曲、播放、拖动进度之后都要调一次：节拍点必须挂在新曲目的速度表上。
     */
    private fun restartMetronome() {
        stopMetronome()
        silenceClicks()
        if (!metronomeEnabled || beatTimesMs.isEmpty()) return
        if (!ensureClickStreams()) {
            // 音源建不出来就干脆关掉，免得界面显示"已开启"却一声不响
            metronomeEnabled = false
            return
        }
        applyClickVolume()
        metronomeJob = scope.launch { runMetronome() }
    }

    private fun stopMetronome() {
        metronomeJob?.cancel()
        metronomeJob = null
    }

    /**
     * 节拍器主循环。
     *
     * 每一拍都重新向 BASS 取一次真实播放位置，再算出"离下一拍还有多久"去 sleep，
     * 所以每拍都会重新锚定：系统时钟与音频时钟之间的微小漂移不会累积，
     * 拖动进度、暂停恢复之后也会自动重新对齐，不需要额外的回调去同步状态。
     *
     * 这里刻意没用 BASS_SYNC_POS：那需要在原生回调线程里播放 click 并续注册下一个
     * 同步点，跨线程状态一多就容易出隐蔽问题；而本方案的误差来源只有 delay() 的
     * 唤醒精度（毫秒级），对节拍器已经够用。
     *
     * 循环条件用 metronomeEnabled 而不是 isActive——任务被 cancel 时 delay() 会抛
     * CancellationException，循环自然结束，不必再额外判一次。
     */
    private suspend fun runMetronome() {
        var index = 0
        while (metronomeEnabled) {
            if (playState != PlayState.PLAYING) {
                // 暂停时不空转；index 置 -1，恢复播放后重新找拍
                index = -1
                delay(20)
                continue
            }
            val pos = readPositionMs()
            if (index < 0 || index >= beatTimesMs.size || beatTimesMs[index] < pos - 1) {
                index = firstBeatAtOrAfter(pos)
            }
            if (index < 0) {
                // 后面的拍都过去了（比如拖到了曲尾），等状态变化再重新对齐
                delay(20)
                index = -1
                continue
            }
            val wait = beatTimesMs[index] - pos
            if (wait > 1) delay(wait - 1)
            if (!metronomeEnabled || playState != PlayState.PLAYING) {
                index = -1
                continue
            }
            // 睡醒后偏差过大，说明这期间被拖动过，丢掉这一拍重新对齐
            if (readPositionMs() - beatTimesMs[index] > 120) {
                index = -1
                continue
            }
            fireClick(index)
            index++
        }
    }

    /** 二分找出第一个不早于 [ms] 的拍；都过去了返回 -1。 */
    private fun firstBeatAtOrAfter(ms: Long): Int {
        val beats = beatTimesMs
        var lo = 0
        var hi = beats.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (beats[mid] >= ms) {
                ans = mid
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }
        return ans
    }

    /** 敲一下；每小节第一拍用重音，听感上才知道小节线在哪。 */
    private fun fireClick(index: Int) {
        val handle = if (index % waterfallBeatsPerBar == 0) clickAccentHandle else clickNormalHandle
        if (handle == 0) return
        // restart = true：从头重放，所以同一个通道可以反复触发，不用每次新建通道
        BASS.BASS_ChannelSetPosition(handle, 0, BASS.BASS_POS_BYTE)
        BASS.BASS_ChannelPlay(handle, true)
    }

    private fun applyClickVolume() {
        val v = if (metronomeEnabled) metronomeVolume else 0f
        if (clickAccentHandle != 0) {
            BASS.BASS_ChannelSetAttribute(clickAccentHandle, BASS.BASS_ATTRIB_VOL, v)
        }
        if (clickNormalHandle != 0) {
            BASS.BASS_ChannelSetAttribute(clickNormalHandle, BASS.BASS_ATTRIB_VOL, v)
        }
    }

    private fun silenceClicks() {
        if (clickAccentHandle != 0) BASS.BASS_ChannelStop(clickAccentHandle)
        if (clickNormalHandle != 0) BASS.BASS_ChannelStop(clickNormalHandle)
    }

    /**
     * 懒加载两个 click 流（重音 / 非重音）。
     *
     * 做法是先合成一小段 WAV 落到缓存目录，再让 BASS 建流。
     * 之所以不直接用内存采样（BASS_SampleCreate 那一套）：当前绑定里没有这几个
     * 函数，手写声明必须和原生侧编译时的签名完全对齐，一旦对不上就是崩溃或脏数据，
     * 为了一声"哒"不值得冒这个险。
     */
    private fun ensureClickStreams(): Boolean {
        if (clickAccentHandle != 0 && clickNormalHandle != 0) return true
        return runCatching {
            val dir = File(context.cacheDir, "metronome").apply { if (!exists()) mkdirs() }
            val accent = File(dir, "accent.wav")
            val normal = File(dir, "normal.wav")
            // 同一个音源只写一次，之后每次启动直接复用
            if (accent.length() == 0L) accent.writeBytes(buildClickWav(CLICK_ACCENT_HZ))
            if (normal.length() == 0L) normal.writeBytes(buildClickWav(CLICK_NORMAL_HZ))
            clickAccentHandle = BASS.BASS_StreamCreateFile(accent.absolutePath, 0L, 0L, 0)
            clickNormalHandle = BASS.BASS_StreamCreateFile(normal.absolutePath, 0L, 0L, 0)
            Log.i(TAG, "节拍器音源: 重音=$clickAccentHandle 普通=$clickNormalHandle")
        }.fold(
            onSuccess = { clickAccentHandle != 0 && clickNormalHandle != 0 },
            onFailure = {
                Log.e(TAG, "节拍器音源创建失败", it)
                false
            },
        )
    }

    /**
     * 合成一个"哒"声：单声道 16 位 WAV。
     *
     * 正弦波乘指数衰减包络，前 1ms 叠一段起音斜坡——没有斜坡的话波形会从 0
     * 直接跳到峰值，听起来会多一声爆音。
     */
    private fun buildClickWav(freqHz: Double): ByteArray {
        val rate = SAMPLE_RATE
        val frames = rate * CLICK_DURATION_MS / 1000
        val dataBytes = frames * 2
        val out = ByteArray(44 + dataBytes)
        val head = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        head.put("RIFF".toByteArray(Charsets.US_ASCII))
        head.putInt(36 + dataBytes)
        head.put("WAVE".toByteArray(Charsets.US_ASCII))
        head.put("fmt ".toByteArray(Charsets.US_ASCII))
        head.putInt(16)                 // fmt 块长度
        head.putShort(1)                // PCM
        head.putShort(1)                // 单声道
        head.putInt(rate)
        head.putInt(rate * 2)           // 字节率
        head.putShort(2)                // 块对齐
        head.putShort(16)               // 位深
        head.put("data".toByteArray(Charsets.US_ASCII))
        head.putInt(dataBytes)

        var offset = 44
        for (i in 0 until frames) {
            val t = i.toDouble() / rate
            val attack = (t / 0.001).coerceAtMost(1.0)
            val envelope = attack * exp(-t * 60.0)
            val sample = (sin(2.0 * PI * freqHz * t) * envelope * 0.9 * 32767.0)
                .toInt()
                .coerceIn(-32768, 32767)
            out[offset++] = (sample and 0xFF).toByte()
            out[offset++] = ((sample shr 8) and 0xFF).toByte()
        }
        return out
    }

    // ---------------- 位置推进 ----------------

    private fun readDurationMs(): Long {
        if (streamHandle == 0) return 0
        val bytes = BASS.BASS_ChannelGetLength(streamHandle, BASS.BASS_POS_BYTE)
        if (bytes <= 0) return 0
        return (BASS.BASS_ChannelBytes2Seconds(streamHandle, bytes) * 1000.0).toLong()
    }

    private fun readPositionMs(): Long {
        if (streamHandle == 0) return 0
        val bytes = BASS.BASS_ChannelGetPosition(streamHandle, BASS.BASS_POS_BYTE)
        if (bytes < 0) return 0
        return (BASS.BASS_ChannelBytes2Seconds(streamHandle, bytes) * 1000.0).toLong()
    }

    private fun startPositionUpdater() {
        stopPositionUpdater()
        positionJob = scope.launch {
            while (isActive && playState == PlayState.PLAYING) {
                if (streamHandle != 0) {
                    positionMs = readPositionMs()
                    // 高亮的键由位置推导，和瀑布用同一份音符数据
                    updateActiveNotes(positionMs)
                }
                notifyState()
                if (durationMs > 0 && positionMs >= durationMs) {
                    onTrackFinished()
                    break
                }
                delay(POSITION_INTERVAL_MS)
            }
        }
    }

    private fun onTrackFinished() {
        positionMs = durationMs
        notifyState()
        if (previewing) {
            stop()
            return
        }
        when (repeatMode) {
            RepeatMode.SINGLE -> {
                seekTo(0)
                play()
            }
            RepeatMode.LIST, RepeatMode.SHUFFLE -> {
                if (playlist.isEmpty()) stop() else next()
            }
        }
    }

    private fun stopPositionUpdater() {
        positionJob?.cancel()
        positionJob = null
    }

    // ---------------- 发声音符（瀑布高亮用） ----------------

    /**
     * 由当前播放位置推导正在发声的音符。
     *
     * 为什么不用 BASS_SYNC_MIDI_EVENT 回调：该回调里 data 的事件打包格式
     * 官方未公开成文档，实测取到的值与按状态字节在高位/低位两种猜测都不吻合；
     * 而瀑布下落的音符本来就来自同一份解析结果，用位置推导能保证
     * 「高亮的键」与「落下的键」天然一致，也少一条跨线程回调链。
     *
     * notes 数量级在 10^3~10^4，每 30ms 扫一遍完全可接受。
     */
    private fun updateActiveNotes(nowMs: Long) {
        val notes = waterfallNotes
        if (notes.isEmpty()) {
            clearActiveNotes()
            return
        }
        val active = LinkedHashMap<String, ActiveNote>()
        for (note in notes) {
            val start = note.startTimeMs
            if (start > nowMs) break
            if (note.durationMs > 0 && nowMs < start + note.durationMs) {
                val key = note.key
                active["${note.channel}:$key"] =
                    ActiveNote(key, note.channel, note.velocity, start.toLong())
            }
        }
        val signature = active.keys.sorted().joinToString(",")
        if (signature == lastActiveSignature) return
        lastActiveSignature = signature
        synchronized(activeNotes) {
            activeNotes.clear()
            activeNotes.putAll(active)
        }
        noteListener?.invoke(active.values.toList())
    }

    private fun clearActiveNotes() {
        if (lastActiveSignature.isEmpty() && activeNotes.isEmpty()) return
        lastActiveSignature = ""
        synchronized(activeNotes) { activeNotes.clear() }
        noteListener?.invoke(emptyList())
    }

    // ---------------- 释放 ----------------

    fun release() {
        stop()
        // 节拍器的两个流要显式释放：它们不挂在曲目流上，
        // BASS_Free 之后这两个句柄就悬空了
        if (clickAccentHandle != 0) {
            BASS.BASS_StreamFree(clickAccentHandle)
            clickAccentHandle = 0
        }
        if (clickNormalHandle != 0) {
            BASS.BASS_StreamFree(clickNormalHandle)
            clickNormalHandle = 0
        }
        if (fontHandle != 0) {
            BASSMIDI.BASS_MIDI_FontFree(fontHandle)
            fontHandle = 0
        }
        BASS.BASS_Free()
    }
}
