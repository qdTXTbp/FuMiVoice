package com.fumi.voice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fumi.voice.library.Playlist
import com.fumi.voice.model.MidiTrack
import com.fumi.voice.ui.components.EmptyState
import com.fumi.voice.ui.theme.AccentOrange
import com.fumi.voice.ui.theme.CharcoalRaised
import com.fumi.voice.ui.theme.Divider
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.TextPrimary
import com.fumi.voice.ui.theme.TextSecondary
import com.fumi.voice.ui.theme.TimecodeStyle
import com.fumi.voice.util.formatDuration
import com.fumi.voice.util.formatSize

/** 歌单详情：曲目列表 + 播放全部 / 随机 / 重命名 / 删除。 */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    tracks: List<MidiTrack>,
    currentPath: String?,
    onBack: () -> Unit,
    onPlayQueue: (List<MidiTrack>, Int) -> Unit,
    onRemove: (MidiTrack) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExportM3u: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDeletePlaylist by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {

        // 子页头：返回 + 歌单名 + 操作
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowBack, "返回", tint = TextPrimary)
            }
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(
                "重命名",
                style = MaterialTheme.typography.labelMedium,
                color = Indigo,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRename)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { confirmingDeletePlaylist = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Delete, "删除歌单", tint = TextSecondary, modifier = Modifier.size(19.dp))
            }
        }

        if (tracks.isEmpty()) {
            EmptyState(
                icon = Icons.Default.MusicNote,
                title = "歌单还是空的",
                message = "回到「曲目」列表，点每行右侧的 ⊕ 把曲目加进来",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPlayQueue(tracks, 0) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Indigo, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("播放全部", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPlayQueue(tracks.shuffled(), 0) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Shuffle, null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("随机", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onExportM3u)
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Share, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导出", style = MaterialTheme.typography.titleSmall, color = TextSecondary)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(tracks, key = { _, t -> t.path }) { index, track ->
                    val isCurrent = track.path == currentPath
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayQueue(tracks, index) }
                            .background(if (isCurrent) Indigo.copy(alpha = 0.14f) else Color.Transparent)
                            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "%02d".format(index + 1),
                            style = TimecodeStyle.copy(fontSize = 12.sp),
                            color = if (isCurrent) Indigo else TextSecondary,
                            modifier = Modifier.width(28.dp),
                            textAlign = TextAlign.Start,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCurrent) Indigo else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                buildString {
                                    if (track.durationMs > 0) append(formatDuration(track.durationMs))
                                    else append("--:--")
                                    append(" · ${formatSize(track.sizeBytes)}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Box(
                            modifier = Modifier.size(42.dp).clip(CircleShape).clickable { onRemove(track) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Delete, "移出歌单", tint = TextSecondary, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }

    if (confirmingDeletePlaylist) {
        AlertDialog(
            onDismissRequest = { confirmingDeletePlaylist = false },
            title = { Text("删除歌单？", color = TextPrimary) },
            text = { Text("「${playlist.name}」将被删除，曲库中的文件不受影响。", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeletePlaylist = false
                    onDelete()
                }) { Text("删除", color = AccentOrange) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDeletePlaylist = false }) { Text("取消", color = TextSecondary) }
            },
            containerColor = CharcoalRaised,
        )
    }
}

/**
 * 新建 / 重命名歌单的输入弹窗。
 * [playlist] 为 null 时是新建。
 */
@Composable
fun RenamePlaylistDialog(
    playlist: Playlist?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(playlist?.id) { mutableStateOf(playlist?.name ?: "") }
    val isCreate = playlist == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreate) "新建歌单" else "重命名歌单", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("歌单名称", color = TextSecondary) },
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2A2A33),
                    unfocusedContainerColor = Color(0xFF2A2A33),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Indigo,
                    focusedIndicatorColor = Indigo,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(if (isCreate) "创建" else "保存", color = Indigo) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        },
        containerColor = CharcoalRaised,
    )
}

/** 把一首曲目加入一个或多个歌单。 */
@Composable
fun AddToPlaylistDialog(
    track: MidiTrack,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onConfirm: (List<Playlist>) -> Unit,
) {
    val chosen = remember { mutableStateOf(setOf<String>()) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入歌单", color = TextPrimary) },
        text = {
            Column {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Text(
                        "还没有歌单，先建一个：",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                } else {
                    Column(modifier = Modifier.height(200.dp)) {
                        LazyColumn {
                            itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                                val selected = chosen.value.contains(playlist.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            chosen.value = if (selected) {
                                                chosen.value - playlist.id
                                            } else {
                                                chosen.value + playlist.id
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (selected) Indigo else Divider,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        playlist.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("或新建一个：", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }

                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("新歌单名称", color = TextSecondary) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2A2A33),
                        unfocusedContainerColor = Color(0xFF2A2A33),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Indigo,
                        focusedIndicatorColor = Indigo,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newName.isNotBlank()) {
                        onCreateAndAdd(newName)
                    } else {
                        val picked = playlists.filter { chosen.value.contains(it.id) }
                        if (picked.isNotEmpty()) onConfirm(picked)
                    }
                },
                enabled = newName.isNotBlank() || chosen.value.isNotEmpty(),
            ) { Text(if (newName.isNotBlank()) "新建并加入" else "加入", color = Indigo) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        },
        containerColor = CharcoalRaised,
    )
}
