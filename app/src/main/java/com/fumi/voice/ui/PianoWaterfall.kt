package com.fumi.voice.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fumi.voice.midi.MidiParser
import com.fumi.voice.player.ActiveNote
import kotlin.math.abs
import kotlin.math.floor

/** 瀑布中的单个音符。[startTimeMs] / [durationMs] 均已按速度表换算成毫秒。 */
data class NoteData(
    val startTimeMs: Double,
    val durationMs: Double,
    val key: Int,
    val channel: Int,
    val velocity: Int,
)

/**
 * 钢琴瀑布（Synthesia 式）。
 *
 * 布局与 FuFumidi 的可视化一致：**横轴是音高，纵轴是时间**，
 * 琴键横向铺在底部，音符块自上而下落向对应琴键。
 *
 * 三条关键规则（这是与"所有音符挤在顶部"的旧实现最本质的区别）：
 *
 * 1. **音符高度 = 时长 × 每秒像素**，与窗口大小无关。
 *    旧实现把高度写成 `时长 / 固定窗口 × 屏幕高度`，导致长音符撑满整屏。
 * 2. **滚动速度按小节换算**：一小节固定占 [BAR_HEIGHT_DP]，
 *    因此快歌滚得快、慢歌滚得慢，视觉节奏与听觉一致。
 * 3. **画面由帧时钟驱动**：在音频位置更新之间做插值，得到 60fps 平滑滚动，
 *    而不是每 30ms 跳一格。
 */
@Composable
fun PianoWaterfall(
    notes: List<NoteData>,
    currentTimeMs: Long,
    activeNotes: List<ActiveNote>,
    isPlaying: Boolean,
    tempoMarks: List<MidiParser.TempoMark>,
    beatsPerBar: Int,
    modifier: Modifier = Modifier,
    onKeyPress: ((Int) -> Unit)? = null,
    /**
     * 是否驱动逐帧重绘。
     *
     * 分页会预加载相邻页，所以播放页在别的标签页下也可能被组合着；
     * 关掉这个开关就能让它停画，免得在后台白跑 60fps。
     * 重新打开时帧时钟会自己对齐到真实播放位置，不会从冻结处续着走。
     */
    animate: Boolean = true,
) {
    val textMeasurer = rememberTextMeasurer()

    // 音符准备：按开始时间排序 + 同音高重叠合并（避免长音上叠着一堆短音）
    val prepared = remember(notes) { prepareNotes(notes) }
    val pitchLayout = remember(prepared) { buildPitchLayout(prepared.notes) }

    // 只用到一个通道的曲子（多数钢琴独奏 MIDI 就是），按通道着色等于整屏一个颜色，
    // 看不出旋律走向。这种情况退回"按音区着色"：把全曲音域三等分，低/中/高各一色，
    // 至少能一眼看出线条在往上还是往下走。多通道曲目仍用通道色，那才分得出声部。
    val registerRange = remember(prepared) {
        if (prepared.notes.isEmpty() || prepared.notes.map { it.channel }.distinct().size > 1) {
            null
        } else {
            val keys = prepared.notes.map { it.key }
            keys.min() to keys.max()
        }
    }

    // 帧时钟：在音频位置更新之间插值，保证滚动平滑
    val displayMs = rememberFrameClock(currentTimeMs, isPlaying && animate)

    // 发声中的琴键（用于高亮）
    val activeKeys = remember(activeNotes) { activeNotes.map { it.key }.toHashSet() }

    // 粒子：音符击键时迸发，增强"落点"的反馈
    val particles = remember { mutableListOf<Particle>() }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onKeyPress, pitchLayout) {
                    val layout = pitchLayout ?: return@pointerInput
                    if (onKeyPress == null) return@pointerInput
                    detectTapGestures { offset ->
                        val key = layout.keyAt(offset.x, size.width.toFloat())
                        if (key >= 0) onKeyPress(key)
                    }
                }
        ) {
            val layout = pitchLayout
            if (layout == null || prepared.notes.isEmpty()) {
                drawEmptyHint()
                return@Canvas
            }

            val nowMs = displayMs
            drawWaterfall(
                prepared = prepared,
                layout = layout,
                nowMs = nowMs,
                activeKeys = activeKeys,
                tempoMarks = tempoMarks,
                beatsPerBar = beatsPerBar,
                textMeasurer = textMeasurer,
                particles = particles,
                registerRange = registerRange,
            )
        }
    }
}

// =====================================================================
// 数据准备
// =====================================================================

/**
 * 帧时钟。
 *
 * 音频位置每 ~30ms 才更新一次，直接用它绘制会看到明显的阶梯感。
 * 这里以每次位置更新为锚点，用帧时间戳线性外插；
 * 位置更新到来时重新锚定，因此误差不会累积超过一个更新周期。
 */
@Composable
private fun rememberFrameClock(currentTimeMs: Long, isPlaying: Boolean): Double {
    var displayMs by remember { mutableDoubleStateOf(currentTimeMs.toDouble()) }

    LaunchedEffect(currentTimeMs, isPlaying) {
        val base = currentTimeMs.toDouble()

        // 暂停、或位置被拖动/跳转时，直接硬对齐
        if (!isPlaying || abs(displayMs - base) > 150.0) {
            displayMs = base
        }
        if (!isPlaying) return@LaunchedEffect

        val baseFrame = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                displayMs = base + (now - baseFrame) / 1_000_000.0
            }
        }
    }

    return displayMs
}

/** 已排序并合并过的音符集合。 */
private class PreparedNotes(
    val notes: List<NoteData>,
    /** 最长音符时长，用于决定回溯扫描的起点。 */
    val maxDurationMs: Double,
)

private fun prepareNotes(source: List<NoteData>): PreparedNotes {
    if (source.isEmpty()) return PreparedNotes(emptyList(), 0.0)

    // 先按音高分组做重叠合并：同音高上时间相接/重叠的音符合并成一条长条，
    // 否则瀑布上会出现多个矩形互相覆盖、边缘闪烁。
    val byPitch = HashMap<Int, MutableList<NoteData>>()
    for (note in source) {
        byPitch.getOrPut(note.key) { mutableListOf() }.add(note)
    }

    val merged = ArrayList<NoteData>(source.size)
    for ((_, list) in byPitch) {
        list.sortBy { it.startTimeMs }
        var cursor = list[0]
        for (i in 1 until list.size) {
            val next = list[i]
            val cursorEnd = cursor.startTimeMs + cursor.durationMs
            // 允许 10ms 容差，把几乎相接的音符合并
            if (next.startTimeMs <= cursorEnd + 10.0) {
                val newEnd = maxOf(cursorEnd, next.startTimeMs + next.durationMs)
                cursor = cursor.copy(
                    startTimeMs = minOf(cursor.startTimeMs, next.startTimeMs),
                    durationMs = newEnd - minOf(cursor.startTimeMs, next.startTimeMs),
                    velocity = maxOf(cursor.velocity, next.velocity),
                )
            } else {
                merged.add(cursor)
                cursor = next
            }
        }
        merged.add(cursor)
    }

    merged.sortBy { it.startTimeMs }
    val maxDuration = merged.maxOfOrNull { it.durationMs } ?: 0.0
    return PreparedNotes(merged, maxDuration)
}

/**
 * 音高 → 屏幕横向布局。
 *
 * 采用 FuFumidi 的做法：按**白键**均分宽度，
 * 黑键居中压在相邻白键的边界上，而不是把 12 个半音等宽排列。
 */
private class PitchLayout(
    val lowKey: Int,
    val highKey: Int,
    val whiteCount: Int,
    /** key → 左侧白键数量（白键自身为序号，黑键为所贴边界序号）。下标与 MIDI 音号一致。 */
    private val whiteIndexBefore: IntArray,
) {
    /** 判断某音是否落在本布局范围内。 */
    fun contains(key: Int): Boolean = key in lowKey..highKey

    /** 白键宽度（px）。 */
    fun whiteWidth(canvasWidth: Float): Float = canvasWidth / whiteCount

    /** 某个键在画布上的左边界。 */
    fun xOf(key: Int, canvasWidth: Float): Float {
        val g = whiteWidth(canvasWidth)
        val index = whiteIndexBefore[key]
        return if (isBlackKey(key)) {
            // 黑键宽度 0.6g，居中于边界
            index * g - BLACK_WIDTH_RATIO * g / 2f
        } else {
            // 白键宽度 0.9g，留出细缝
            index * g + WHITE_GAP_RATIO * g
        }
    }

    fun widthOf(key: Int, canvasWidth: Float): Float {
        val g = whiteWidth(canvasWidth)
        return if (isBlackKey(key)) BLACK_WIDTH_RATIO * g else WHITE_WIDTH_RATIO * g
    }

    /** 画布 x 坐标落在哪个键上（用于点按试听）。 */
    fun keyAt(x: Float, canvasWidth: Float): Int {
        // 黑键画在白键上层，优先命中
        for (key in lowKey..highKey) {
            if (!isBlackKey(key)) continue
            val kx = xOf(key, canvasWidth)
            val kw = widthOf(key, canvasWidth)
            if (x >= kx && x <= kx + kw) return key
        }

        // 再落到白键：把白键序号换算回 MIDI 音
        val white = floor(x / whiteWidth(canvasWidth)).toInt()
        var count = 0
        for (key in lowKey..highKey) {
            if (isBlackKey(key)) continue
            if (count == white) return key
            count++
        }
        return -1
    }
}

private fun buildPitchLayout(notes: List<NoteData>): PitchLayout? {
    if (notes.isEmpty()) return null

    var lo = Int.MAX_VALUE
    var hi = Int.MIN_VALUE
    for (n in notes) {
        if (n.key < lo) lo = n.key
        if (n.key > hi) hi = n.key
    }

    // 左右各留 2 个半音余量
    lo -= 2
    hi += 2

    // 至少覆盖 3 个八度，避免只弹几个音时琴键宽得离谱
    val minSpan = 36
    if (hi - lo + 1 < minSpan) {
        val need = minSpan - (hi - lo + 1)
        lo -= need / 2
        hi += need - need / 2
    }

    lo = lo.coerceAtLeast(21)   // A0
    hi = hi.coerceAtMost(108)   // C8
    if (hi - lo + 1 < 12) return null

    // 起点对齐到白键：从黑键起画会让第一个黑键落到画布外
    while (lo > 21 && isBlackKey(lo)) lo--

    val whiteIndexBefore = IntArray(128)
    var whiteCursor = 0
    for (key in lo..hi) {
        whiteIndexBefore[key] = whiteCursor
        if (!isBlackKey(key)) whiteCursor++
    }
    if (whiteCursor == 0) return null

    return PitchLayout(lo, hi, whiteCursor, whiteIndexBefore)
}

// =====================================================================
// 绘制
// =====================================================================

private const val BLACK_WIDTH_RATIO = 0.6f
private const val WHITE_WIDTH_RATIO = 0.9f
private const val WHITE_GAP_RATIO = 0.05f
private const val BAR_HEIGHT_DP = 96f
private const val KEYBOARD_FRACTION = 0.22f
private const val MIN_KEYBOARD_DP = 54f
private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)

private val COLORS = listOf(
    Color(0xFF6C8CFF), Color(0xFF39C6B4), Color(0xFFEF6F6C), Color(0xFFF2C14E),
    Color(0xFF7FB2FF), Color(0xFFFF9F45), Color(0xFF56D9A0), Color(0xFFE86FB8),
    Color(0xFF9B8CFF), Color(0xFFFFE08A), Color(0xFF69D9E8), Color(0xFFFFB3A0),
    Color(0xFFB8C4D9), Color(0xFFA8E06A), Color(0xFFD79BE8), Color(0xFF8FA6FF),
)

private fun isBlackKey(key: Int): Boolean = BLACK_PITCH_CLASSES.contains(key % 12)
private fun colorForChannel(channel: Int): Color = COLORS[channel.mod(COLORS.size)]

/**
 * 单通道曲目的音区配色：把全曲音域三等分，低 / 中 / 高各一色。
 *
 * 取的是 [COLORS] 里对比最强的三个（蓝紫 / 青绿 / 琥珀），
 * 相邻音区不会糊在一起。
 */
private val REGISTER_COLORS = listOf(
    Color(0xFF6C8CFF),
    Color(0xFF39C6B4),
    Color(0xFFF2C14E),
)

private fun colorForRegister(key: Int, range: Pair<Int, Int>): Color {
    val span = (range.second - range.first + 1).coerceAtLeast(1)
    val band = ((key - range.first) * REGISTER_COLORS.size / span)
        .coerceIn(0, REGISTER_COLORS.lastIndex)
    return REGISTER_COLORS[band]
}

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val color: Color,
)

private fun DrawScope.drawEmptyHint() {
    drawRect(Color(0xFF141419))
}

private fun DrawScope.drawWaterfall(
    prepared: PreparedNotes,
    layout: PitchLayout,
    nowMs: Double,
    activeKeys: Set<Int>,
    tempoMarks: List<MidiParser.TempoMark>,
    beatsPerBar: Int,
    textMeasurer: TextMeasurer,
    particles: MutableList<Particle>,
    /** 非空表示这首曲子只用了一个通道，此时按这个音高范围分段着色。 */
    registerRange: Pair<Int, Int>?,
) {
    val w = size.width
    val h = size.height

    val keyboardHeight = (h * KEYBOARD_FRACTION).coerceAtLeast(MIN_KEYBOARD_DP.dp.toPx())
    val hitY = h - keyboardHeight              // 键盘顶边 = 击键线
    if (hitY <= 0f) return

    val barPx = BAR_HEIGHT_DP.dp.toPx()
    val pxPerMs = (barPx / barDurationMsAt(nowMs, tempoMarks, beatsPerBar)).toFloat()
    if (pxPerMs <= 0f) return

    val whiteWidth = layout.whiteWidth(w)

    // ---------- 背景 ----------
    drawRect(Color(0xFF141419))

    // ---------- C 音竖线（八度参考线） ----------
    for (key in layout.lowKey..layout.highKey) {
        if (key % 12 != 0) continue
        val x = layout.xOf(key, w)
        drawLine(
            color = Color(0xFF26263A),
            start = Offset(x, 0f),
            end = Offset(x, hitY),
            strokeWidth = 1f,
        )
    }

    // ---------- 滚动小节线 + 小节号 ----------
    drawBarLines(nowMs, pxPerMs, barPx, w, hitY, textMeasurer)

    // ---------- 音符 ----------
    // 只遍历可能可见的一段：从 (now - 最长音符) 起扫到 (now + 屏幕可显示时长)
    val visibleSpanMs = (hitY / pxPerMs).toDouble()
    val scanFromMs = nowMs - prepared.maxDurationMs - 200.0
    val scanToMs = nowMs + visibleSpanMs + 200.0

    val notes = prepared.notes
    var index = lowerBoundByStart(notes, scanFromMs)
    while (index < notes.size && notes[index].startTimeMs <= scanToMs) {
        val note = notes[index]
        index++

        if (!layout.contains(note.key)) continue

        // 纵轴换算（与 FuFumidi 一致）：
        // 音符的**底边**在"开始时刻"正好落到击键线上，之后继续向下扫过键盘；
        // 因此结束时间对应上边、开始时间对应下边。
        // 高度 = 时长 × pxPerMs，与屏幕尺寸无关 —— 这是瀑布比例正确的前提。
        val topY = hitY - ((note.startTimeMs + note.durationMs - nowMs) * pxPerMs).toFloat()
        val bottomY = hitY - ((note.startTimeMs - nowMs) * pxPerMs).toFloat()
        if (bottomY < 0f || topY > hitY) continue

        val x = layout.xOf(note.key, w)
        val noteWidth = layout.widthOf(note.key, w)
        val clippedTop = topY.coerceAtLeast(0f)
        val clippedBottom = bottomY.coerceAtMost(hitY)
        val height = (clippedBottom - clippedTop).coerceAtLeast(2f)

        val color = registerRange?.let { colorForRegister(note.key, it) }
            ?: colorForChannel(note.channel)
        val sounding = note.startTimeMs <= nowMs && nowMs <= note.startTimeMs + note.durationMs

        // 纵深：离击键线越远越淡。
        // 瀑布上方那些还在等落下来的音符如果全都满不透明，整片会糊成一块色墙；
        // 按距离平方衰减后近处清楚、远处退成背景，纵向层次立刻出来了。
        // 远处保留 45% 而不是淡到看不见——太淡会让人以为音符丢了。
        val depth = ((hitY - clippedTop) / hitY).coerceIn(0f, 1f)
        val fade = 1f - depth * depth * 0.55f

        // 正在发声的音符加一层柔光，与 FuFumidi 的处理一致
        if (sounding) {
            drawRoundRect(
                color = color.copy(alpha = 0.35f * fade),
                topLeft = Offset(x - 3f, clippedTop - 3f),
                size = Size(noteWidth + 6f, height + 6f),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = fade), color.copy(alpha = 0.82f * fade)),
                startY = clippedTop,
                endY = clippedTop + height,
            ),
            topLeft = Offset(x, clippedTop),
            size = Size(noteWidth, height),
            cornerRadius = CornerRadius(4f, 4f),
        )

        // 右上角一条高光，让方块有体积感
        drawRoundRect(
            color = Color.White.copy(alpha = (if (sounding) 0.5f else 0.22f) * fade),
            topLeft = Offset(x + 1.5f, clippedTop + 1.5f),
            size = Size(noteWidth - 3f, (height * 0.22f).coerceAtMost(6f)),
            cornerRadius = CornerRadius(2f, 2f),
        )
    }

    // ---------- 粒子 ----------
    updateAndDrawParticles(particles, activeKeys, layout, w, hitY, whiteWidth)

    // ---------- 击键线上的光带 ----------
    // 在击键线上方铺一层向上渐隐的柔光：音符落进这条带子里就是"正在发声"，
    // 比单独一根 2px 的线更容易看出节奏落点。
    run {
        val glowHeight = 48f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x006C8CFF), Color(0x386C8CFF)),
                startY = hitY - glowHeight,
                endY = hitY,
            ),
            topLeft = Offset(0f, hitY - glowHeight),
            size = Size(w, glowHeight),
        )
    }

    // ---------- 击键线 ----------
    drawLine(
        color = Color(0xFF6C8CFF).copy(alpha = 0.9f),
        start = Offset(0f, hitY),
        end = Offset(w, hitY),
        strokeWidth = 2f,
    )

    // ---------- 键盘 ----------
    drawKeyboard(layout, w, hitY, keyboardHeight, activeKeys, textMeasurer)
}

/** 当前时刻一小节的时长（毫秒），由速度标记换算。 */
private fun barDurationMsAt(
    nowMs: Double,
    tempoMarks: List<MidiParser.TempoMark>,
    beatsPerBar: Int,
): Double {
    val beats = if (beatsPerBar in 1..16) beatsPerBar else 4
    var usPerQuarter = 500_000
    for (mark in tempoMarks) {
        if (mark.timeMs <= nowMs + 1e-6) usPerQuarter = mark.usPerQuarter else break
    }
    return (usPerQuarter / 1000.0) * beats
}

/** 按开始时间二分查找第一个 >= target 的位置。 */
private fun lowerBoundByStart(notes: List<NoteData>, target: Double): Int {
    var lo = 0
    var hi = notes.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (notes[mid].startTimeMs < target) lo = mid + 1 else hi = mid
    }
    return lo
}

private fun DrawScope.drawBarLines(
    nowMs: Double,
    pxPerMs: Float,
    barPx: Float,
    w: Float,
    hitY: Float,
    textMeasurer: TextMeasurer,
) {
    // 小节线在小节边界上，随音符一起向下滚动
    val scrolledPx = (nowMs * pxPerMs).toFloat()
    val firstBar = floor(scrolledPx / barPx).toInt()
    val lastBar = ((scrolledPx + hitY) / barPx).toInt() + 1

    val labelStyle = TextStyle(color = Color(0xFF6A6A85), fontSize = 10.sp)

    for (bar in firstBar..lastBar) {
        val y = hitY - (bar * barPx - scrolledPx)
        if (y < -20f || y > hitY + 20f) continue

        val isMajor = bar % 4 == 0
        drawLine(
            color = Color(0xFF2E2E44).copy(alpha = if (isMajor) 0.95f else 0.55f),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = if (isMajor) 1.4f else 1f,
        )

        if (bar > 0 && y in 0f..hitY) {
            val label = textMeasurer.measure(bar.toString(), labelStyle)
            drawText(
                textLayoutResult = label,
                topLeft = Offset(6f, y - label.size.height - 2f),
            )
        }
    }
}

private fun DrawScope.updateAndDrawParticles(
    particles: MutableList<Particle>,
    activeKeys: Set<Int>,
    layout: PitchLayout,
    w: Float,
    hitY: Float,
    whiteWidth: Float,
) {
    val maxParticles = 160

    // 给"刚开始发声"的键补一次迸发。
    // 这里没有事件流，所以以「当前发声集合中、粒子还没覆盖的键」为准。
    for (key in activeKeys) {
        if (!layout.contains(key)) continue
        if (particles.size >= maxParticles) break
        val x = layout.xOf(key, w) + layout.widthOf(key, w) / 2f
        // 同一键已有近期粒子则跳过，避免每帧重复生成
        if (particles.any { abs(it.x - x) < 2f && it.life > 0.75f }) continue
        repeat(3) {
            particles.add(
                Particle(
                    x = x,
                    y = hitY,
                    vx = (Math.random().toFloat() - 0.5f) * whiteWidth * 0.12f,
                    vy = -(1.5f + Math.random().toFloat() * 2.5f),
                    life = 1f,
                    color = Color(0xFFBFC9FF),
                )
            )
        }
    }

    // 更新 + 绘制 + 回收
    var i = particles.size - 1
    while (i >= 0) {
        val p = particles[i]
        p.x += p.vx
        p.y += p.vy
        p.vy += 0.28f
        p.life -= 0.035f
        if (p.life <= 0f) {
            particles.removeAt(i)
            i--
            continue
        }
        drawCircle(
            color = p.color.copy(alpha = p.life.coerceIn(0f, 1f) * 0.85f),
            radius = 2.2f,
            center = Offset(p.x, p.y),
        )
        i--
    }
}

private fun DrawScope.drawKeyboard(
    layout: PitchLayout,
    w: Float,
    hitY: Float,
    keyboardHeight: Float,
    activeKeys: Set<Int>,
    textMeasurer: TextMeasurer,
) {
    val g = layout.whiteWidth(w)
    val whiteWidthPx = WHITE_WIDTH_RATIO * g

    // 白键
    for (key in layout.lowKey..layout.highKey) {
        if (isBlackKey(key)) continue
        val x = layout.xOf(key, w)
        val lit = activeKeys.contains(key)

        if (lit) {
            drawRoundRect(
                color = Color(0xFF9FB4FF),
                topLeft = Offset(x, hitY),
                size = Size(whiteWidthPx, keyboardHeight),
                cornerRadius = CornerRadius(3f, 3f),
            )
        } else {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE6ECF7), Color(0xFFC6D0E2)),
                    startY = hitY,
                    endY = hitY + keyboardHeight,
                ),
                topLeft = Offset(x, hitY),
                size = Size(whiteWidthPx, keyboardHeight),
                cornerRadius = CornerRadius(3f, 3f),
            )
        }

        // 白键之间的分隔细线
        drawLine(
            color = Color(0x33000000),
            start = Offset(x + whiteWidthPx, hitY),
            end = Offset(x + whiteWidthPx, hitY + keyboardHeight - 3f),
            strokeWidth = 1f,
        )

        // 每个 C 标注音名，便于定位
        if (key % 12 == 0 && g > 18f) {
            val label = textMeasurer.measure(
                "C${key / 12 - 1}",
                TextStyle(color = Color(0xFF5A6478), fontSize = 9.sp),
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(x + (whiteWidthPx - label.size.width) / 2f, hitY + keyboardHeight - label.size.height - 4f),
            )
        }
    }

    // 黑键画在白键之上
    val blackHeight = keyboardHeight * 0.62f
    for (key in layout.lowKey..layout.highKey) {
        if (!isBlackKey(key)) continue
        val x = layout.xOf(key, w)
        val width = layout.widthOf(key, w)
        val lit = activeKeys.contains(key)

        drawRoundRect(
            brush = if (lit) {
                Brush.verticalGradient(listOf(Color(0xFF8FA5FF), Color(0xFF6C8CFF)))
            } else {
                Brush.verticalGradient(listOf(Color(0xFF2A3142), Color(0xFF151A26)))
            },
            topLeft = Offset(x, hitY),
            size = Size(width, blackHeight),
            cornerRadius = CornerRadius(2f, 2f),
        )
    }
}
