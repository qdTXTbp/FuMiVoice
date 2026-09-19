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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.stringResource
import com.fumi.voice.R

/** 歌单详情：曲目列表 + 播放全部 / 随机 / 重命名 / 删除。 */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    tracks: List<MidiTrack>,
    currentPath: String?,
    onBack: () -> Unit,
    onPlayQueue: (List<MidiTrack>, Int) -> Unit,
    onRemove: (MidiTrack) -> Unit,
    onAddFromLibrary: () -> Unit,
    onImportFiles: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExportM3u: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDeletePlaylist by remember { mutableStateOf(false) }
    var addingMenu by remember { mutableStateOf(false) }

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
                Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back), tint = TextPrimary)
            }
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            // 往歌单里批量添加入口。放菜单里而不是并排两个按钮——
            // 这一行已经有重命名和删除，再塞两个文字按钮会挤成一条。
            Box {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable { addingMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.pl_add_tracks), tint = Indigo)
                }
                DropdownMenu(expanded = addingMenu, onDismissRequest = { addingMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pl_add_from_library), color = TextPrimary) },
                        onClick = { addingMenu = false; onAddFromLibrary() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_import_files), color = TextPrimary) },
                        onClick = { addingMenu = false; onImportFiles() },
                    )
                }
            }
            Text(
                stringResource(R.string.pl_rename),
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
                Icon(Icons.Default.Delete, stringResource(R.string.pl_delete_playlist), tint = TextSecondary, modifier = Modifier.size(19.dp))
            }
        }

        if (tracks.isEmpty()) {
            EmptyState(
                icon = Icons.Default.MusicNote,
                title = stringResource(R.string.pl_empty_title),
                message = stringResource(R.string.pl_empty_message),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            // 空歌单时把两个导入入口直接摆出来：原来只有一句"回到曲目列表点 ⊕"，
            // 用户得先退出去、再切到曲目、再逐首点，批量导入的意义就没了。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onAddFromLibrary, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pl_add_from_library), maxLines = 1)
                }
                Button(onClick = onImportFiles, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_import_files), maxLines = 1)
                }
            }
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
                    Text(stringResource(R.string.pl_play_all), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
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
                    Text(stringResource(R.string.pl_shuffle), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
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
                    Text(stringResource(R.string.pl_export), style = MaterialTheme.typography.titleSmall, color = TextSecondary)
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
                            Icon(Icons.Default.Delete, stringResource(R.string.pl_remove_from_playlist), tint = TextSecondary, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }

    if (confirmingDeletePlaylist) {
        AlertDialog(
            onDismissRequest = { confirmingDeletePlaylist = false },
            title = { Text(stringResource(R.string.pl_delete_confirm_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.pl_delete_confirm_text, playlist.name), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeletePlaylist = false
                    onDelete()
                }) { Text(stringResource(R.string.pl_delete), color = AccentOrange) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDeletePlaylist = false }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) }
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
        title = { Text(if (isCreate) stringResource(R.string.pl_new_playlist) else stringResource(R.string.pl_rename_playlist), color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.pl_name_placeholder), color = TextSecondary) },
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
            ) { Text(if (isCreate) stringResource(R.string.pl_create) else stringResource(R.string.pl_save), color = Indigo) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = TextSecondary) }
        },
        containerColor = CharcoalRaised,
    )
}

/**
 * 从曲库多选曲目，用于往歌单里批量添加。
 *
 * 已经在歌单里的曲目会被直接排除出候选：addTracks 本身会静默去重，
 * 若还让用户勾选，点了确认却什么也没加，只会被当成 bug。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickLibraryTracksSheet(
    tracks: List<MidiTrack>,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<MidiTrack>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var keyword by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(setOf<String>()) }

    val candidates = remember(tracks, existingNames) {
        tracks.filterNot { it.fileName in existingNames }
    }
    val visible = remember(candidates, keyword) {
        val k = keyword.trim()
        if (k.isEmpty()) {
            candidates
        } else {
            candidates.filter {
                it.title.contains(k, ignoreCase = true) ||
                    it.fileName.contains(k, ignoreCase = true) ||
                    it.artist?.contains(k, ignoreCase = true) == true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CharcoalRaised,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pl_add_from_library), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        if (candidates.isEmpty()) stringResource(R.string.pl_pick_none)
                        else stringResource(R.string.pl_pick_count, candidates.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                // 全选只作用于"当前可见"，搜索过滤后不会把看不见的也选上
                if (visible.isNotEmpty()) {
                    val allChosen = visible.all { it.path in chosen }
                    Text(
                        if (allChosen) stringResource(R.string.pl_deselect_all) else stringResource(R.string.pl_select_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = Indigo,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val paths = visible.map { it.path }.toSet()
                                chosen = if (allChosen) chosen - paths else chosen + paths
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.pl_search_tracks), color = TextSecondary) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2A2A33),
                    unfocusedContainerColor = Color(0xFF2A2A33),
                    cursorColor = Indigo,
                ),
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.height(320.dp)) {
                itemsIndexed(visible, key = { _, t -> t.path }) { _, track ->
                    val picked = track.path in chosen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                chosen = if (picked) chosen - track.path else chosen + track.path
                            }
                            .padding(vertical = 9.dp, horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (picked) Indigo else Divider,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (picked) Indigo else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val artist = track.artist
                            if (!artist.isNullOrBlank()) {
                                Text(
                                    artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel), color = TextSecondary)
                }
                Button(
                    onClick = { onConfirm(candidates.filter { it.path in chosen }) },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (chosen.isEmpty()) stringResource(R.string.pl_add) else stringResource(R.string.pl_add_count, chosen.size))
                }
            }
        }
    }
}

/** 把若干曲目加入一个或多个歌单。单首和批量共用这一个弹窗。 */
@Composable
fun AddToPlaylistDialog(
    tracks: List<MidiTrack>,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onConfirm: (List<Playlist>) -> Unit,
) {
    val chosen = remember { mutableStateOf(setOf<String>()) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pl_dialog_title), color = TextPrimary) },
        text = {
            Column {
                Text(
                    // 单首显示曲名，多首显示数量
                    if (tracks.size == 1) tracks.first().title else stringResource(R.string.pl_selected_count, tracks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Text(
                        stringResource(R.string.pl_no_playlists),
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
                    Text(stringResource(R.string.pl_or_create), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }

                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.pl_new_name_placeholder), color = TextSecondary) },
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
            ) { Text(if (newName.isNotBlank()) stringResource(R.string.pl_create_and_add) else stringResource(R.string.pl_join), color = Indigo) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = TextSecondary) }
        },
        containerColor = CharcoalRaised,
    )
}
