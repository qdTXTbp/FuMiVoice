package com.fumi.voice.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fumi.voice.audio.EqBands
import com.fumi.voice.audio.Equalizer
import com.fumi.voice.ui.theme.CharcoalRaised
import com.fumi.voice.ui.theme.Divider
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.TextPrimary
import com.fumi.voice.ui.theme.TextSecondary
import com.fumi.voice.ui.theme.TimecodeStyle
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.fumi.voice.R

/**
 * 均衡器底部弹层。
 *
 * 放在弹层而不是独立页面：调音时要能一边听一边看播放进度，
 * 跳走一个 Activity 会打断播放上下文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    equalizer: Equalizer,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 弹层内部自己维护一份状态，改完立即回写并重绘
    var gains by remember { mutableStateOf(equalizer.gains()) }
    var enabled by remember { mutableStateOf(equalizer.enabled) }
    var presetIndex by remember { mutableStateOf(equalizer.presetIndex()) }

    fun applyGain(index: Int, db: Float) {
        equalizer.setGain(index, db)
        gains = equalizer.gains()
        presetIndex = equalizer.presetIndex()
    }

    fun applyPreset(index: Int) {
        equalizer.applyPreset(index)
        gains = equalizer.gains()
        presetIndex = index
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CharcoalRaised,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {

            // ---- 标题 + 开关 ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Indigo.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Equalizer, null, tint = Indigo, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.eq_title), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        stringResource(R.string.eq_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        equalizer.setEnabled(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Indigo,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Divider,
                    ),
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---- 预设 ----
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                itemsIndexed(Equalizer.presets) { index, preset ->
                    val selected = presetIndex == index
                    FilterChip(
                        selected = selected,
                        onClick = { applyPreset(index) },
                        label = {
                            Text(
                                preset.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else TextSecondary,
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CharcoalRaised,
                            selectedContainerColor = Indigo,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = Divider,
                            selectedBorderColor = Indigo,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- 10 段推子 ----
            Row(
                modifier = Modifier.fillMaxWidth().height(230.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EqBands.labels.forEachIndexed { index, label ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            formatGain(gains.getOrElse(index) { 0f }),
                            style = TimecodeStyle.copy(fontSize = 11.sp),
                            color = if (enabled) TextPrimary else TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        VerticalFader(
                            gainDb = gains.getOrElse(index) { 0f },
                            enabled = enabled,
                            onGainChange = { applyGain(index, it) },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (presetIndex >= 0) stringResource(R.string.eq_current_preset, Equalizer.presets[presetIndex].name) else stringResource(R.string.eq_current_custom),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { applyPreset(0) }) {
                    Text(stringResource(R.string.eq_reset_flat), color = Indigo)
                }
            }
        }
    }
}

/** 单条竖直推子：自绘轨道与滑块，避免旋转 Slider 带来的触摸区错位。 */
@Composable
private fun VerticalFader(
    gainDb: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val span = EqBands.MAX_DB - EqBands.MIN_DB
    val fraction = ((gainDb - EqBands.MIN_DB) / span).coerceIn(0f, 1f)
    val centerFraction = ((0f - EqBands.MIN_DB) / span).coerceIn(0f, 1f)

    fun dbAt(y: Float, height: Float): Float {
        if (height <= 0f) return 0f
        val f = (1f - y / height).coerceIn(0f, 1f)
        return EqBands.MIN_DB + f * span
    }

    Box(
        modifier = modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onGainChange(dbAt(down.position.y, size.height.toFloat()))
                        down.consume()
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) break
                            onGainChange(dbAt(change.position.y, size.height.toFloat()))
                            change.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = 4.dp.toPx()
            val thumbRadius = 6.dp.toPx()
            val centerX = size.width / 2f
            val top = thumbRadius
            val bottom = size.height - thumbRadius
            val usable = bottom - top
            if (usable <= 0f) return@Canvas

            drawLine(
                color = Divider,
                start = Offset(centerX, top),
                end = Offset(centerX, bottom),
                strokeWidth = trackWidth,
                cap = StrokeCap.Round,
            )

            // 0 dB 中位参考刻线
            val zeroY = bottom - usable * centerFraction
            drawLine(
                color = Divider,
                start = Offset(centerX - 6.dp.toPx(), zeroY),
                end = Offset(centerX + 6.dp.toPx(), zeroY),
                strokeWidth = 1.dp.toPx(),
            )

            val thumbY = bottom - usable * fraction
            val accent = if (enabled) Indigo else TextSecondary
            drawLine(
                color = accent,
                start = Offset(centerX, zeroY),
                end = Offset(centerX, thumbY),
                strokeWidth = trackWidth,
                cap = StrokeCap.Round,
            )
            drawCircle(color = accent, radius = thumbRadius, center = Offset(centerX, thumbY))
            drawCircle(color = CharcoalRaised, radius = thumbRadius * 0.42f, center = Offset(centerX, thumbY))
        }
    }
}

private fun formatGain(db: Float): String = String.format(Locale.US, "%+.1f", db)
