package com.fumi.voice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fumi.voice.player.ChannelState
import com.fumi.voice.player.MidiPlayerManager
import com.fumi.voice.soundfont.GmInstrument
import com.fumi.voice.soundfont.GmInstruments
import com.fumi.voice.ui.components.SegmentedTabs
import com.fumi.voice.ui.theme.AccentGreen
import com.fumi.voice.ui.theme.AccentOrange
import com.fumi.voice.ui.theme.CharcoalRaised
import com.fumi.voice.ui.theme.Divider
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.TextPrimary
import com.fumi.voice.ui.theme.TextSecondary
import com.fumi.voice.ui.theme.TimecodeStyle

/**
 * 通道混音台底部弹层。
 *
 * 与均衡器放在同一层：调通道时要能一边听一边看播放进度。
 * 默认只列出当前曲目实际用到的通道（多数 MIDI 只用了 3-6 个通道），
 * 需要时再切到全部 16 通道。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerSheet(
    player: MidiPlayerManager,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val channels by player.channels.collectAsState()
    val usedChannels = player.usedChannels

    var showAll by remember { mutableStateOf(false) }
    var editingChannel by remember { mutableIntStateOf(-1) }

    val visible = remember(channels, usedChannels, showAll) {
        if (showAll || usedChannels.isEmpty()) channels
        else channels.filter { it.index in usedChannels }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CharcoalRaised,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {

            // ---- 标题 ----
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
                    Icon(Icons.Default.GraphicEq, null, tint = Indigo, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("混音台", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        if (usedChannels.isEmpty()) "当前曲目不使用 MIDI 通道"
                        else "本次曲目用到 ${usedChannels.size} 个通道",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    "重置",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentOrange,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { player.resetChannelMix() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            SegmentedTabs(
                options = listOf("用到的通道", "全部 16 通道"),
                selected = if (showAll) 1 else 0,
                onSelect = { showAll = it == 1 },
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(visible, key = { it.index }) { state ->
                    ChannelRow(
                        state = state,
                        onVolume = { player.setChannelVolume(state.index, it) },
                        onPan = { player.setChannelPan(state.index, it) },
                        onMute = { player.toggleChannelMute(state.index) },
                        onSolo = { player.toggleChannelSolo(state.index) },
                        onPickInstrument = { editingChannel = state.index },
                    )
                }
            }

            Text(
                "静音与独奏通过把该通道音量置 0 实现；乐器选择会直接作用于当前播放的曲目。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }

    if (editingChannel >= 0) {
        val current = channels.getOrNull(editingChannel)
        InstrumentPickerDialog(
            channelIndex = editingChannel,
            currentProgram = current?.program ?: -1,
            onDismiss = { editingChannel = -1 },
            onPick = { program ->
                player.setChannelProgram(editingChannel, program)
                editingChannel = -1
            },
        )
    }
}

/** 单个通道：一行头部（通道号/乐器/静音/独奏）+ 一行两个推子（音量、声像）。 */
@Composable
private fun ChannelRow(
    state: ChannelState,
    onVolume: (Int) -> Unit,
    onPan: (Int) -> Unit,
    onMute: () -> Unit,
    onSolo: () -> Unit,
    onPickInstrument: () -> Unit,
) {
    val instrumentName = remember(state.program) {
        if (state.isDrum && state.program < 0) "标准鼓组"
        else GmInstruments.all.firstOrNull { it.program == state.program }?.name ?: "未指定音色"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (state.muted) Divider.copy(alpha = 0.35f) else Color.Transparent)
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (state.isDrum) AccentOrange.copy(alpha = 0.18f) else Indigo.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.isDrum) "鼓" else "${state.index + 1}",
                    style = TimecodeStyle.copy(fontSize = 12.sp),
                    color = if (state.isDrum) AccentOrange else Indigo,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onPickInstrument)
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    instrumentName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.program >= 0) "音色 ${state.program + 1} · 点击更换" else "点击选择音色",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }

            ToggleChip("静音", state.muted, AccentOrange, onMute)
            Spacer(Modifier.width(6.dp))
            ToggleChip("独奏", state.soloed, AccentGreen, onSolo)
        }

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("音量", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(28.dp))
            Slider(
                value = state.volume.toFloat(),
                onValueChange = { onVolume(it.toInt()) },
                valueRange = 0f..127f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Indigo,
                    activeTrackColor = Indigo,
                    inactiveTrackColor = Divider,
                ),
            )
            Text(
                "${state.volume * 100 / 127}%",
                style = TimecodeStyle.copy(fontSize = 11.sp),
                color = TextSecondary,
                modifier = Modifier.width(40.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("声像", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.width(28.dp))
            Slider(
                value = state.pan.toFloat(),
                onValueChange = { onPan(it.toInt()) },
                valueRange = 0f..127f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = AccentOrange,
                    activeTrackColor = AccentOrange,
                    inactiveTrackColor = Divider,
                ),
            )
            Text(
                panLabel(state.pan),
                style = TimecodeStyle.copy(fontSize = 11.sp),
                color = TextSecondary,
                modifier = Modifier.width(40.dp),
            )
        }
    }
}

/** 小开关块，样式与 PillTag 一致但可点击。 */
@Composable
private fun ToggleChip(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) color else Divider.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.White else TextSecondary,
        )
    }
}

private fun panLabel(pan: Int): String = when {
    pan == 64 -> "居中"
    pan < 64 -> "左${(64 - pan) * 100 / 64}%"
    else -> "右${(pan - 64) * 100 / 63}%"
}

/** GM 音色选择弹窗，复用音色页那份 128 个音色表。 */
@Composable
private fun InstrumentPickerDialog(
    channelIndex: Int,
    currentProgram: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) GmInstruments.all
        else GmInstruments.all.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.englishName.contains(query, ignoreCase = true) ||
                it.family.contains(query, ignoreCase = true)
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "通道 ${channelIndex + 1} 的音色",
                color = TextPrimary,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索音色，如「钢琴」", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "清空",
                                tint = TextSecondary,
                                modifier = Modifier.clickable { query = "" },
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CharcoalRaised,
                        unfocusedContainerColor = CharcoalRaised,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Indigo,
                        focusedIndicatorColor = Indigo,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.program }) { instrument: GmInstrument ->
                        val selected = instrument.program == currentProgram
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Indigo.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { onPick(instrument.program) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "%03d".format(instrument.program + 1),
                                style = TimecodeStyle.copy(fontSize = 11.sp),
                                color = if (selected) Indigo else TextSecondary,
                                modifier = Modifier.width(32.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    instrument.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) Indigo else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    instrument.family,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = TextSecondary) }
        },
        containerColor = CharcoalRaised,
    )
}
