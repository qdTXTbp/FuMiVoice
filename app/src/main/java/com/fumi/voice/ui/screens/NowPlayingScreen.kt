package com.fumi.voice.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fumi.voice.R
import com.fumi.voice.player.ActiveNote
import com.fumi.voice.player.MidiPlayerManager
import com.fumi.voice.player.PlayState
import com.fumi.voice.player.PlayerUiState
import com.fumi.voice.player.RepeatMode
import com.fumi.voice.ui.PianoWaterfall
import com.fumi.voice.ui.components.AppCard
import com.fumi.voice.ui.components.EmptyState
import com.fumi.voice.ui.components.PillTag
import com.fumi.voice.ui.theme.CharcoalRaised
import com.fumi.voice.ui.theme.Divider
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.Motion
import com.fumi.voice.ui.theme.TextPrimary
import com.fumi.voice.ui.theme.TextSecondary
import com.fumi.voice.ui.theme.TimecodeStyle
import com.fumi.voice.util.formatDuration
import com.fumi.voice.util.formatTempo
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.ScreenRotation

/** 倍速档位，覆盖需求要求的 0.25x - 4x。 */
private val TEMPO_STEPS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)

/** 播放页：钢琴瀑布 + 传输控制 + 倍速 / 音量。 */
@Composable
fun NowPlayingScreen(
    player: MidiPlayerManager,
    state: PlayerUiState,
    activeNotes: List<ActiveNote>,
    onPickMidi: () -> Unit,
    onOpenSoundFonts: () -> Unit,
    isInPip: Boolean = false,
    onEnterPip: () -> Unit = {},
    /** 沉浸模式：由外壳负责收掉顶栏与底部导航，这里只留一个开关按钮。 */
    immersive: Boolean = false,
    onToggleImmersive: (() -> Unit)? = null,
    onToggleLandscape: (() -> Unit)? = null,
    /** 是否驱动瀑布逐帧重绘；非当前标签页时关掉，省下后台重绘。 */
    animateWaterfall: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val track = state.track
    // 有曲目就算已选中；但只有乐谱才有音符可以画成瀑布
    val hasTrack = track != null
    val hasNotes = hasTrack && player.waterfallNotes.isNotEmpty()

    /** 瀑布区的形态编号，见下面 AnimatedContent 处的说明。 */
    val stage = when {
        hasNotes -> 0
        hasTrack -> 1
        else -> 2
    }

    var showEqualizer by remember { mutableStateOf(false) }
    var showMixer by remember { mutableStateOf(false) }

    // 小窗模式：不给任何控件留位置，整窗只画音符瀑布
    if (isInPip) {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF141419))) {
            if (hasNotes) {
                PianoWaterfall(
                    notes = player.waterfallNotes,
                    currentTimeMs = state.positionMs,
                    activeNotes = activeNotes,
                    isPlaying = state.playState == PlayState.PLAYING,
                    tempoMarks = player.waterfallTempoMarks,
                    beatsPerBar = player.waterfallBeatsPerBar,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return
    }

    // 横竖屏判定提前到这里：瀑布区里的横屏开关要用到它
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 瀑布区抽成 lambda：竖屏时在上、横屏时在左。
    // 两种形态共用同一份渲染逻辑，避免「横屏瀑布」变成另一套要各自维护的实现。
    val waterfallArea: @Composable (Modifier) -> Unit = { stageModifier ->
        Box(
            modifier = stageModifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141419)),
        ) {
            // 沉浸 / 横屏两个开关统一放在瀑布之后绘制，原因见下面那段说明。
            // 瀑布区有三种形态：有音符就画瀑布、纯音频文件给占位说明、
            // 没选曲目给空状态。用一个序号描述它，切换形态时淡入淡出，
            // 这样刚载入曲目时空状态不会"啪"地一下直接变成瀑布。
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    fadeIn(animationSpec = Motion.spec(Motion.Standard)) togetherWith
                        fadeOut(animationSpec = Motion.spec(Motion.Instant))
                },
                modifier = Modifier.fillMaxSize(),
                label = "waterfallStage",
            ) { current ->
            when (current) {
                0 -> PianoWaterfall(
                    notes = player.waterfallNotes,
                    currentTimeMs = state.positionMs,
                    activeNotes = activeNotes,
                    isPlaying = state.playState == PlayState.PLAYING,
                    tempoMarks = player.waterfallTempoMarks,
                    beatsPerBar = player.waterfallBeatsPerBar,
                    modifier = Modifier.fillMaxSize(),
                    animate = animateWaterfall,
                )

                // 音频文件（FLAC / WavPack）没有音符，给一个同风格的占位说明
                1 -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.np_audio_file), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.np_no_note_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }

                else -> EmptyState(
                    icon = Icons.Default.MusicNote,
                    title = stringResource(R.string.np_no_track_title),
                    message = stringResource(R.string.np_no_track_message),
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        Button(
                            onClick = onPickMidi,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                        ) {
                            Text(stringResource(R.string.np_open_midi))
                        }
                    },
                )
            }
            }

            // 沉浸 / 横屏两个开关必须画在瀑布**之后**。
            //
            // 瀑布第一件事就是 drawRect(Color(0xFF141419)) 把整块画布铺满，
            // 而 Compose 按组合顺序绘制；早先把开关写在 AnimatedContent 之前，
            // 结果只要有音符、瀑布一画出来，开关就被整块盖住——
            // 「播放曲目时找不到沉浸按钮」就是这么来的。
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 手动横屏：系统关掉「自动旋转」时，用户没有任何别的办法把界面转过来
                if (onToggleLandscape != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable(onClick = onToggleLandscape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.ScreenRotation,
                            contentDescription = stringResource(
                                if (landscape) R.string.action_exit_landscape
                                else R.string.action_enter_landscape
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                if (onToggleImmersive != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable(onClick = onToggleImmersive),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (immersive) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = stringResource(
                                if (immersive) R.string.action_exit_immersive
                                else R.string.action_enter_immersive
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
    }

    // 控制区同理：竖屏在下、横屏在右，两块共用同一份实现。
    val panel: @Composable () -> Unit = {
        // ---------- 曲目信息 ----------
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 曲名做交叉淡入淡出。key 用"曲名字符串"而不是曲目对象：
                // 退场的那一份还能显示旧名字，直接读外层 track 的话
                // 新旧两行会在切换的瞬间同时显示新曲名。
                AnimatedContent(
                    targetState = track?.title ?: stringResource(R.string.np_no_track_short),
                    transitionSpec = {
                        fadeIn(animationSpec = Motion.spec(Motion.Standard)) togetherWith
                            fadeOut(animationSpec = Motion.spec(Motion.Instant))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "trackTitle",
                ) { title ->
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                // buildString 的 lambda 不是可组合上下文，stringResource 不能写在里面，
                // 所以先把两段文案取出来，再拼进去。
                val fallbackFont = stringResource(R.string.header_no_soundfont)
                val noteSuffix = if (track != null && track.noteCount > 0) {
                    stringResource(R.string.np_note_count_suffix, track.noteCount)
                } else {
                    ""
                }
                Text(
                    buildString {
                        append(state.soundFontName?.substringBeforeLast('.') ?: fallbackFont)
                        append(noteSuffix)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEnterPip) {
                Icon(
                    Icons.Default.PictureInPictureAlt,
                    contentDescription = stringResource(R.string.np_pip),
                    tint = Indigo,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = { showMixer = true }) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = stringResource(R.string.mixer_title),
                    tint = Indigo,
                    modifier = Modifier.size(22.dp),
                )
            }
            // 均衡器图标的颜色跟着开关渐变，比"瞬间变色"更能表达这是个状态
            val eqIconTint by animateColorAsState(
                targetValue = if (player.equalizer.enabled) Indigo else TextSecondary,
                animationSpec = Motion.spec(Motion.Standard),
                label = "eqIconTint",
            )
            IconButton(onClick = { showEqualizer = true }) {
                Icon(
                    Icons.Default.Equalizer,
                    contentDescription = stringResource(R.string.eq_title),
                    tint = eqIconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onOpenSoundFonts) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = stringResource(R.string.np_switch_soundfont),
                    tint = Indigo,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- 进度 ----------
        SeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            enabled = hasTrack,
            onSeek = { player.seekTo(it) },
        )

        // ---------- 传输控制 ----------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { player.cycleRepeatMode() }) {
                // 循环模式的三个图标之间淡入淡出，避免硬切
                AnimatedContent(
                    targetState = state.repeatMode,
                    transitionSpec = {
                        fadeIn(animationSpec = Motion.spec(Motion.Instant)) togetherWith
                            fadeOut(animationSpec = Motion.spec(Motion.Instant))
                    },
                    label = "repeatIcon",
                ) { mode ->
                    Icon(
                        imageVector = when (mode) {
                            RepeatMode.SINGLE -> Icons.Default.RepeatOne
                            RepeatMode.LIST -> Icons.Default.Repeat
                            RepeatMode.SHUFFLE -> Icons.Default.Shuffle
                        },
                        contentDescription = when (mode) {
                            RepeatMode.SINGLE -> stringResource(R.string.np_repeat_single)
                            RepeatMode.LIST -> stringResource(R.string.np_repeat_list)
                            RepeatMode.SHUFFLE -> stringResource(R.string.np_repeat_shuffle)
                        },
                        tint = Indigo,
                    )
                }
            }

            IconButton(onClick = { player.previous() }, enabled = hasTrack) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.np_prev),
                    tint = if (hasTrack) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Indigo),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = {
                        if (hasTrack) player.togglePlayPause() else onPickMidi()
                    },
                    modifier = Modifier.size(64.dp),
                ) {
                    // 播放 / 暂停不是简单换个图标：新图标带一点缩放"弹"进来，
                    // 看起来才像真的按下去了，而不是闪了一下
                    AnimatedContent(
                        targetState = state.playState == PlayState.PLAYING,
                        transitionSpec = {
                            (
                                fadeIn(animationSpec = Motion.spec(Motion.Instant)) +
                                    scaleIn(initialScale = 0.7f, animationSpec = Motion.spring())
                                ) togetherWith fadeOut(animationSpec = Motion.spec(Motion.Instant))
                        },
                        label = "playPauseIcon",
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) stringResource(R.string.np_pause) else stringResource(R.string.tab_play),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }

            IconButton(onClick = { player.next() }, enabled = hasTrack) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.np_next),
                    tint = if (hasTrack) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp),
                )
            }

            IconButton(onClick = { player.stop() }, enabled = hasTrack) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = stringResource(R.string.np_stop),
                    tint = if (hasTrack) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // ---------- 播放调节：倍速 / 音调 / 节拍器 ----------
        PlaybackAdjustCard(
            tempo = state.tempo,
            pitch = state.pitchSemitones,
            metronomeOn = state.metronomeEnabled,
            metronomeVolume = state.metronomeVolume,
            metronomeAvailable = state.metronomeAvailable,
            onTempoChange = { player.setTempo(it) },
            onPitchChange = { player.setPitch(it) },
            onMetronomeToggle = { player.setMetronomeEnabled(it) },
            onMetronomeVolume = { player.setMetronomeVolume(it) },
        )

        // ---------- 音量 ----------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.VolumeDown, contentDescription = stringResource(R.string.mixer_volume), tint = TextSecondary, modifier = Modifier.size(20.dp))
            Slider(
                value = state.volume,
                onValueChange = { player.setVolume(it) },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Indigo,
                    activeTrackColor = Indigo,
                    inactiveTrackColor = Divider,
                ),
            )
            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(12.dp))
    }

    when {
        // 沉浸：整屏只留瀑布，顶栏/底部导航由外壳收掉
        immersive -> waterfallArea(modifier.fillMaxSize())

        // 横屏：瀑布占满左侧，控制栏收进右侧固定宽度里自己滚动。
        // 竖屏那套「从上往下堆」搬到横屏会变成"瀑布很扁、控件很长一根"，两头都不好用。
        landscape -> Row(modifier = modifier.fillMaxSize()) {
            waterfallArea(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            )
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 16.dp),
            ) {
                panel()
            }
        }

        else -> Column(modifier = modifier.fillMaxSize()) {
            waterfallArea(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            panel()
        }
    }

    if (showMixer) {
        MixerSheet(
            player = player,
            onDismiss = { showMixer = false },
        )
    }

    if (showEqualizer) {
        EqualizerSheet(
            equalizer = player.equalizer,
            onDismiss = { showEqualizer = false },
        )
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    val progress = when {
        dragging -> dragValue
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Slider(
            value = progress,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                if (durationMs > 0) onSeek((dragValue * durationMs).toLong())
                dragging = false
            },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Indigo,
                activeTrackColor = Indigo,
                inactiveTrackColor = Divider,
                disabledThumbColor = TextSecondary.copy(alpha = 0.4f),
                disabledActiveTrackColor = Divider,
                disabledInactiveTrackColor = Divider,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                formatDuration(if (dragging) (dragValue * durationMs).toLong() else positionMs),
                style = TimecodeStyle,
                color = TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatDuration(durationMs),
                style = TimecodeStyle,
                color = TextSecondary,
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * 播放调节卡片：倍速 / 音调 / 节拍器。
 *
 * 三者都属于"对当前这次播放做调整"，放同一张卡里比散成三个入口更好找，
 * 也延续了原来「倍速」那张卡的位置和样式。
 *
 * 节拍器音量只在开启后才出现：平时不白占高度，同时暗示它只在开的时候才有意义。
 */
@Composable
private fun PlaybackAdjustCard(
    tempo: Float,
    pitch: Int,
    metronomeOn: Boolean,
    metronomeVolume: Float,
    metronomeAvailable: Boolean,
    onTempoChange: (Float) -> Unit,
    onPitchChange: (Int) -> Unit,
    onMetronomeToggle: (Boolean) -> Unit,
    onMetronomeVolume: (Float) -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {

            // ---- 倍速 ----
            AdjustRow(label = stringResource(R.string.np_speed), value = formatTempo(tempo)) {
                Slider(
                    // 找不到匹配档位时回落到 1x（索引 3），避免拖到一半文件被换掉时跳档
                    value = TEMPO_STEPS.indexOfFirst { it == tempo }.let { if (it < 0) 3 else it }.toFloat(),
                    onValueChange = { onTempoChange(TEMPO_STEPS[it.toInt()]) },
                    valueRange = 0f..(TEMPO_STEPS.size - 1).toFloat(),
                    steps = TEMPO_STEPS.size - 2,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    colors = adjustSliderColors(),
                )
            }

            // ---- 音调：±12 半音（一个八度），与倍速互不影响 ----
            AdjustRow(label = stringResource(R.string.np_pitch), value = formatPitch(pitch)) {
                Slider(
                    value = pitch.toFloat(),
                    onValueChange = { onPitchChange(it.roundToInt()) },
                    valueRange = -12f..12f,
                    steps = 23,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    colors = adjustSliderColors(),
                )
            }

            // ---- 节拍器 ----
            Row(
                modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.np_metronome), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                if (!metronomeAvailable) {
                    // 节拍要靠曲子的速度表对齐，音频 / 模块没有，这里说清楚为什么点不动
                    PillTag(stringResource(R.string.np_metronome_midi_only), TextSecondary)
                    Spacer(Modifier.width(8.dp))
                }
                Switch(
                    checked = metronomeOn,
                    onCheckedChange = onMetronomeToggle,
                    enabled = metronomeAvailable,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Indigo,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = CharcoalRaised,
                        uncheckedBorderColor = TextSecondary,
                    ),
                )
            }

            AnimatedVisibility(visible = metronomeOn) {
                AdjustRow(label = stringResource(R.string.mixer_volume), value = "${(metronomeVolume * 100).roundToInt()}%") {
                    Slider(
                        value = metronomeVolume,
                        onValueChange = onMetronomeVolume,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        colors = adjustSliderColors(),
                    )
                }
            }
        }
    }
}

/** 调节卡片里的一行：左侧固定标签，中间留给控件，右侧固定宽度的读数。 */
@Composable
private fun AdjustRow(
    label: String,
    value: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        content()
        Text(
            value,
            style = TimecodeStyle.copy(fontSize = 13.sp),
            color = TextPrimary,
            modifier = Modifier.width(46.dp),
            textAlign = TextAlign.End,
        )
    }
}

/** 音调读数：带符号的半音数。 */
private fun formatPitch(semitones: Int): String =
    if (semitones > 0) "+$semitones" else "$semitones"

@Composable
private fun adjustSliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = Indigo,
    activeTrackColor = Indigo,
    inactiveTrackColor = Divider,
)
