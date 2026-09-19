// 本文件多处用到列表项位移动画（LazyItemScope.animateItemPlacement），
// 该 API 在 Compose 1.6 仍标为实验性，这里一次性对整个文件放开。
@file:OptIn(ExperimentalFoundationApi::class)

package com.fumi.voice.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fumi.voice.library.PlayRecord
import com.fumi.voice.library.Playlist
import com.fumi.voice.library.LibraryPrefs
import com.fumi.voice.library.TrackMetadataParser
import com.fumi.voice.library.TrackSort
import com.fumi.voice.model.MidiTrack
import com.fumi.voice.ui.components.EmptyState
import com.fumi.voice.ui.components.SegmentedTabs
import com.fumi.voice.ui.theme.AccentGreen
import com.fumi.voice.ui.theme.AccentOrange
import com.fumi.voice.ui.theme.CharcoalRaised
import com.fumi.voice.ui.theme.Divider
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.Motion
import com.fumi.voice.ui.theme.TextPrimary
import com.fumi.voice.ui.theme.TextSecondary
import com.fumi.voice.ui.theme.TimecodeStyle
import com.fumi.voice.util.formatDuration
import com.fumi.voice.util.formatRelativeTime
import com.fumi.voice.util.formatSize
import androidx.compose.ui.res.stringResource
import com.fumi.voice.R
import com.fumi.voice.util.localizedText

/**
 * 曲库页。
 *
 * 分四个分区：
 * - 曲目：全部导入的 MIDI
 * - 歌单：用户自建的集合
 * - 艺术家：按「艺术家」聚合（来自文件名推断 + 手动修正）
 * - 最近：按最后播放时间倒序
 *
 * 顶部搜索框对四个分区同时生效（歌单按名称过滤）。
 */
@Composable
fun LibraryScreen(
    tracks: List<MidiTrack>,
    playlists: List<Playlist>,
    resolvePlaylist: (Playlist) -> List<MidiTrack>,
    recentTracks: List<Pair<MidiTrack, PlayRecord>>,
    playCounts: Map<String, Int>,
    currentPath: String?,
    loading: Boolean,
    onPlayQueue: (List<MidiTrack>, Int) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onOpenFile: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onCreatePlaylistAndAdd: (String, List<MidiTrack>) -> Unit,
    onRenamePlaylist: (Playlist, String) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onRemoveFromPlaylist: (Playlist, MidiTrack) -> Unit,
    onAddToPlaylists: (List<MidiTrack>, List<Playlist>) -> Unit,
    onDeleteTrack: (MidiTrack) -> Unit,
    onDeleteTracks: (List<MidiTrack>) -> Unit,
    onEditTrack: (MidiTrack, String?, String?) -> Unit,
    onExportTrack: (MidiTrack) -> Unit,
    onClearHistory: () -> Unit,
    onImportM3u: () -> Unit,
    /** 把选中的文件导入曲库后，直接加进目标歌单。 */
    onImportFilesIntoPlaylist: (Playlist) -> Unit,
    onExportPlaylistM3u: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 排序方式与升降序存在 SharedPreferences 里，下次进应用保持上次的选择
    val sortPrefs = remember { LibraryPrefs(context) }
    var sortMode by remember { mutableStateOf(sortPrefs.sortMode()) }
    var sortAscending by remember { mutableStateOf(sortPrefs.ascending(sortMode)) }

    var section by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var trackPendingDelete by remember { mutableStateOf<MidiTrack?>(null) }
    var trackPendingAdd by remember { mutableStateOf<MidiTrack?>(null) }
    var trackPendingEdit by remember { mutableStateOf<MidiTrack?>(null) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var openedPlaylistId by remember { mutableStateOf<String?>(null) }
    var openedArtist by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var confirmingClearHistory by remember { mutableStateOf(false) }

    // ---- 曲目批量选择 ----
    var selecting by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
    var batchAddTracks by remember { mutableStateOf<List<MidiTrack>?>(null) }
    var confirmingBatchDelete by remember { mutableStateOf(false) }
    // 歌单详情里点「从曲库添加」时的目标歌单
    var pickingLibraryFor by remember { mutableStateOf<Playlist?>(null) }

    fun clearSelection() {
        selecting = false
        selectedPaths = emptySet()
    }

    val openedPlaylist = playlists.firstOrNull { it.id == openedPlaylistId }

    // ---- 搜索过滤 ----
    val keyword = query.trim()
    val visibleTracks = remember(tracks, keyword) {
        if (keyword.isEmpty()) tracks else tracks.filter { it.matches(keyword) }
    }
    val visiblePlaylists = remember(playlists, keyword) {
        if (keyword.isEmpty()) playlists else playlists.filter { it.name.contains(keyword, ignoreCase = true) }
    }
    val visibleRecent = remember(recentTracks, keyword) {
        if (keyword.isEmpty()) recentTracks else recentTracks.filter { (track, _) -> track.matches(keyword) }
    }
    // 「未知艺术家」也要跟着语言走。在这里就把它取出来当分组键，
    // 后面所有显示分组名的地方（分区标题、详情页标题）就都不用各自再翻译一次。
    val unknownArtist = stringResource(R.string.unknown_artist)
    val artistGroups = remember(visibleTracks, unknownArtist) {
        visibleTracks
            .groupBy { it.artist?.takeIf { name -> name.isNotBlank() } ?: unknownArtist }
            .toList()
            .sortedBy { it.first.lowercase() }
    }

    // 排序只作用于「曲目」分区：歌单/艺术家/最近各自有既定顺序，
    // 套上排序会把分组打散、把「最近」的语义破坏掉。
    val sortedTracks = remember(visibleTracks, sortMode, sortAscending, playCounts) {
        sortMode.apply(visibleTracks, sortAscending, playCounts)
    }

    // 进入歌单详情
    if (openedPlaylist != null) {
        PlaylistDetailScreen(
            playlist = openedPlaylist,
            tracks = resolvePlaylist(openedPlaylist),
            currentPath = currentPath,
            onBack = { openedPlaylistId = null },
            onPlayQueue = onPlayQueue,
            onRemove = { track -> onRemoveFromPlaylist(openedPlaylist, track) },
            onAddFromLibrary = { pickingLibraryFor = openedPlaylist },
            onImportFiles = { onImportFilesIntoPlaylist(openedPlaylist) },
            onRename = { editingPlaylist = openedPlaylist },
            onExportM3u = { onExportPlaylistM3u(openedPlaylist) },
            onDelete = {
                onDeletePlaylist(openedPlaylist)
                openedPlaylistId = null
            },
            modifier = modifier,
        )
        // 从曲库多选添加。候选会先排掉歌单已有的曲目，避免「勾了却没加进去」。
        pickingLibraryFor?.let { target ->
            val existing = resolvePlaylist(target).map { it.fileName }.toSet()
            PickLibraryTracksSheet(
                tracks = tracks,
                existingNames = existing,
                onDismiss = { pickingLibraryFor = null },
                onConfirm = { chosen ->
                    onAddToPlaylists(chosen, listOf(target))
                    pickingLibraryFor = null
                },
            )
        }
        // 详情打开时，重命名弹窗仍可叠加显示。
        // 注意必须判空：RenamePlaylistDialog 把 null 当作「新建」，不判空会立刻弹出新建歌单弹窗。
        editingPlaylist?.let { target ->
            RenamePlaylistDialog(
                playlist = target,
                onDismiss = { editingPlaylist = null },
                onConfirm = { name ->
                    onRenamePlaylist(target, name)
                    editingPlaylist = null
                },
            )
        }
        return
    }

    // 进入艺术家详情
    val artistTracks = artistGroups.firstOrNull { it.first == openedArtist }?.second
    if (openedArtist != null && artistTracks != null) {
        ArtistDetailScreen(
            artist = openedArtist!!,
            tracks = artistTracks,
            currentPath = currentPath,
            playCounts = playCounts,
            onBack = { openedArtist = null },
            onPlayQueue = onPlayQueue,
            onRequestAddToPlaylist = { trackPendingAdd = it },
            onRequestEdit = { trackPendingEdit = it },
            modifier = modifier,
        )
        trackPendingAdd?.let { track ->
            AddToPlaylistDialog(
                tracks = listOf(track),
                playlists = playlists,
                onDismiss = { trackPendingAdd = null },
                onCreateAndAdd = { name ->
                    onCreatePlaylistAndAdd(name, listOf(track))
                    trackPendingAdd = null
                },
                onConfirm = { chosen ->
                    onAddToPlaylists(listOf(track), chosen)
                    trackPendingAdd = null
                },
            )
        }
        trackPendingEdit?.let { track ->
            EditTrackDialog(
                track = track,
                onDismiss = { trackPendingEdit = null },
                onConfirm = { artist, title ->
                    onEditTrack(track, artist, title)
                    trackPendingEdit = null
                },
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {

        SearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        SegmentedTabs(
            options = listOf(stringResource(R.string.lib_tab_tracks), stringResource(R.string.lib_tab_playlists), stringResource(R.string.lib_tab_artists), stringResource(R.string.lib_tab_recent)),
            selected = section,
            onSelect = { section = it },
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(8.dp))

        // 分区切换带一点横向位移 + 淡入淡出。
        //
        // 「曲目 / 歌单 / 艺术家 / 最近」是并列关系，切换时给个方向感
        // 比硬切更容易看懂"我换了一区"。方向按 index 大小决定，
        // 和分段控件上从左到右的顺序一致。
        //
        // 注意 AnimatedContent 的内容 lambda 不再是 ColumnScope，
        // weight 用不了，所以占满高度挪到外层，各分区内部统一 fillMaxSize。
        AnimatedContent(
            targetState = section,
            transitionSpec = { Motion.horizontalSwitch(targetState > initialState) },
            modifier = Modifier.weight(1f),
            label = "librarySection",
        ) { current ->
            when (current) {
                0 -> TracksSection(
                    tracks = sortedTracks,
                    searchActive = keyword.isNotEmpty(),
                    loading = loading,
                    currentPath = currentPath,
                    playCounts = playCounts,
                    onPlayQueue = onPlayQueue,
                    onImportFiles = onImportFiles,
                    onImportFolder = onImportFolder,
                    onOpenFile = onOpenFile,
                    onRequestDelete = { trackPendingDelete = it },
                    onRequestAddToPlaylist = { trackPendingAdd = it },
                    onRequestEdit = { trackPendingEdit = it },
                    onRequestExport = onExportTrack,
                    selectable = selecting,
                    selectedPaths = selectedPaths,
                    onToggleSelect = { track ->
                        selectedPaths = if (track.path in selectedPaths) {
                            selectedPaths - track.path
                        } else {
                            selectedPaths + track.path
                        }
                    },
                    onLongPressTrack = { track ->
                        if (!selecting) {
                            selecting = true
                            selectedPaths = setOf(track.path)
                        } else {
                            selectedPaths = if (track.path in selectedPaths) {
                                selectedPaths - track.path
                            } else {
                                selectedPaths + track.path
                            }
                        }
                    },
                    onEnterSelection = {
                        selecting = true
                        selectedPaths = emptySet()
                    },
                    onExitSelection = { clearSelection() },
                    onToggleSelectAll = {
                        selectedPaths = if (selectedPaths.size == sortedTracks.size) {
                            emptySet()
                        } else {
                            sortedTracks.map { it.path }.toSet()
                        }
                    },
                    onBatchAddToPlaylist = {
                        batchAddTracks = sortedTracks.filter { it.path in selectedPaths }
                    },
                    onBatchDelete = { confirmingBatchDelete = true },
                    sortMode = sortMode,
                    sortAscending = sortAscending,
                    onPickSort = { mode ->
                        val ascending = sortPrefs.ascending(mode)
                        sortMode = mode
                        sortAscending = ascending
                        sortPrefs.save(mode, ascending)
                    },
                    onToggleSortDirection = {
                        val next = !sortAscending
                        sortAscending = next
                        sortPrefs.save(sortMode, next)
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                1 -> PlaylistsSection(
                    playlists = visiblePlaylists,
                    searchActive = keyword.isNotEmpty(),
                    resolvePlaylist = resolvePlaylist,
                    onCreate = { showCreateDialog = true },
                    onImportM3u = onImportM3u,
                    onOpen = { openedPlaylistId = it.id },
                    onRename = { editingPlaylist = it },
                    onDelete = onDeletePlaylist,
                    modifier = Modifier.fillMaxSize(),
                )

                2 -> ArtistsSection(
                    groups = artistGroups,
                    searchActive = keyword.isNotEmpty(),
                    onOpen = { openedArtist = it },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> RecentSection(
                    recent = visibleRecent,
                    searchActive = keyword.isNotEmpty(),
                    currentPath = currentPath,
                    onPlayQueue = onPlayQueue,
                    onClear = { confirmingClearHistory = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // ---- 弹窗 ----

    trackPendingDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackPendingDelete = null },
            title = { Text(stringResource(R.string.lib_remove_dialog_title), color = TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.lib_remove_dialog_text, track.title),
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTrack(track)
                    trackPendingDelete = null
                }) { Text(stringResource(R.string.lib_remove), color = AccentOrange) }
            },
            dismissButton = {
                TextButton(onClick = { trackPendingDelete = null }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) }
            },
            containerColor = CharcoalRaised,
        )
    }

    trackPendingAdd?.let { track ->
        AddToPlaylistDialog(
            tracks = listOf(track),
            playlists = playlists,
            onDismiss = { trackPendingAdd = null },
            onCreateAndAdd = { name ->
                onCreatePlaylistAndAdd(name, listOf(track))
                trackPendingAdd = null
            },
            onConfirm = { chosen ->
                onAddToPlaylists(listOf(track), chosen)
                trackPendingAdd = null
            },
        )
    }

    // 批量：加入歌单。复用同一个弹窗，tracks 传多首即可。
    batchAddTracks?.let { chosenTracks ->
        AddToPlaylistDialog(
            tracks = chosenTracks,
            playlists = playlists,
            onDismiss = { batchAddTracks = null },
            onCreateAndAdd = { name ->
                onCreatePlaylistAndAdd(name, chosenTracks)
                batchAddTracks = null
                clearSelection()
            },
            onConfirm = { chosenPlaylists ->
                onAddToPlaylists(chosenTracks, chosenPlaylists)
                batchAddTracks = null
                clearSelection()
            },
        )
    }

    // 批量：从曲库移除。删除会同时移出所有歌单，所以必须二次确认。
    if (confirmingBatchDelete) {
        val targets = sortedTracks.filter { it.path in selectedPaths }
        AlertDialog(
            onDismissRequest = { confirmingBatchDelete = false },
            title = { Text(stringResource(R.string.lib_batch_remove_title, targets.size), color = TextPrimary) },
            text = { Text(stringResource(R.string.lib_batch_remove_text), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingBatchDelete = false
                    onDeleteTracks(targets)
                    clearSelection()
                }) { Text(stringResource(R.string.lib_remove), color = AccentOrange) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingBatchDelete = false }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) }
            },
            containerColor = CharcoalRaised,
        )
    }

    trackPendingEdit?.let { track ->
        EditTrackDialog(
            track = track,
            onDismiss = { trackPendingEdit = null },
            onConfirm = { artist, title ->
                onEditTrack(track, artist, title)
                trackPendingEdit = null
            },
        )
    }

    if (showCreateDialog) {
        RenamePlaylistDialog(
            playlist = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            },
        )
    }

    if (confirmingClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmingClearHistory = false },
            title = { Text(stringResource(R.string.lib_clear_history_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.lib_clear_history_text), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingClearHistory = false
                    onClearHistory()
                }) { Text(stringResource(R.string.action_clear), color = AccentOrange) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearHistory = false }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) }
            },
            containerColor = CharcoalRaised,
        )
    }
}

/** 搜索框：标题/艺术家/文件名都能匹配。 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.lib_search_placeholder), color = TextSecondary) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = TextSecondary,
                    modifier = Modifier.clickable { onValueChange("") },
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
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
}

/** 曲名 / 艺术家 / 原始文件名任一命中即算匹配。 */
private fun MidiTrack.matches(keyword: String): Boolean =
    title.contains(keyword, ignoreCase = true) ||
        fileName.contains(keyword, ignoreCase = true) ||
        artist?.contains(keyword, ignoreCase = true) == true

/** 搜索无结果的统一提示。 */
@Composable
private fun NoMatchHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.lib_no_match),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

// =====================================================================
// 曲目分区
// =====================================================================

@Composable
private fun TracksSection(
    tracks: List<MidiTrack>,
    searchActive: Boolean,
    loading: Boolean,
    currentPath: String?,
    playCounts: Map<String, Int>,
    onPlayQueue: (List<MidiTrack>, Int) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onOpenFile: () -> Unit,
    onRequestDelete: (MidiTrack) -> Unit,
    onRequestAddToPlaylist: (MidiTrack) -> Unit,
    onRequestEdit: (MidiTrack) -> Unit,
    onRequestExport: (MidiTrack) -> Unit,
    // ---- 批量选择 ----
    selectable: Boolean,
    selectedPaths: Set<String>,
    onToggleSelect: (MidiTrack) -> Unit,
    /** 长按某行：没进选择模式就先进入并勾上它，已在选择模式就当作切换。 */
    onLongPressTrack: (MidiTrack) -> Unit,
    onEnterSelection: () -> Unit,
    onExitSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onBatchAddToPlaylist: () -> Unit,
    onBatchDelete: () -> Unit,
    // ---- 排序 ----
    sortMode: TrackSort,
    sortAscending: Boolean,
    onPickSort: (TrackSort) -> Unit,
    onToggleSortDirection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {

        if (tracks.isNotEmpty()) {
            // 沿用 MyPlayer 的「随机播放全部」作为列表主操作
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { onPlayQueue(tracks.shuffled(), 0) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, tint = Indigo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.lib_shuffle_all),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.lib_track_count_short, tracks.size), style = TimecodeStyle, color = TextSecondary)
                if (!selectable) {
                    // 排序：图标 + 菜单，不占横向空间。
                    // 点当前那项 = 翻转升降序，不用再加第二个按钮。
                    Spacer(Modifier.width(6.dp))
                    Box {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).clickable { sortMenu = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.SwapVert,
                                stringResource(R.string.lib_sort),
                                tint = if (sortMode != TrackSort.FILE_NAME || sortAscending != sortMode.defaultAscending) {
                                    Indigo
                                } else {
                                    TextSecondary
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            TrackSort.entries.forEach { mode ->
                                val active = mode == sortMode
                                // 排序名来自「中英并列」的数据表，按当前语言取那一侧
                                val modeLabel = localizedText(mode.label, mode.labelEn)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (active) {
                                                "$modeLabel  ${if (sortAscending) "↑" else "↓"}"
                                            } else {
                                                modeLabel
                                            },
                                            color = if (active) Indigo else TextPrimary,
                                        )
                                    },
                                    onClick = {
                                        sortMenu = false
                                        if (active) onToggleSortDirection() else onPickSort(mode)
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.lib_select),
                        style = MaterialTheme.typography.labelMedium,
                        color = Indigo,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onEnterSelection)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && tracks.isEmpty() -> CircularProgressIndicator(
                    color = Indigo,
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                )

                tracks.isEmpty() && searchActive -> NoMatchHint()

                tracks.isEmpty() -> EmptyState(
                    icon = Icons.Default.MusicNote,
                    title = stringResource(R.string.lib_empty_title),
                    message = stringResource(R.string.lib_empty_message),
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onImportFiles, shape = RoundedCornerShape(20.dp)) {
                                Icon(Icons.Default.UploadFile, null, tint = Indigo, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_import_files))
                            }
                            OutlinedButton(onClick = onOpenFile, shape = RoundedCornerShape(20.dp)) {
                                Icon(Icons.Default.MusicNote, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.lib_open_single))
                            }
                        }
                    },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    itemsIndexed(tracks, key = { _, t -> t.path }) { index, track ->
                        TrackRow(
                            index = index,
                            track = track,
                            isCurrent = track.path == currentPath,
                            playCount = playCounts[track.fileName] ?: 0,
                            onClick = { onPlayQueue(tracks, index) },
                            onAddToPlaylist = { onRequestAddToPlaylist(track) },
                            onEdit = { onRequestEdit(track) },
                            onExport = { onRequestExport(track) },
                            onDelete = { onRequestDelete(track) },
                            selectable = selectable,
                            selected = track.path in selectedPaths,
                            onToggleSelect = { onToggleSelect(track) },
                            onLongPress = { onLongPressTrack(track) },
                            // 搜索过滤、删除曲目后，剩下的行滑到新位置而不是瞬移。
                            // 有 key 才能对上"是同一行换了位置"，所以 itemsIndexed
                            // 那里的 key 不能省。
                            modifier = Modifier.animateItemPlacement(Motion.itemPlacement()),
                        )
                    }
                }
            }
        }

        if (tracks.isNotEmpty()) {
            if (selectable) {
                // 选择模式：底部这一行换成批量操作条。占的是同一块位置，
                // 视线不用在"顶部按钮"和"底部列表"之间来回跳。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.lib_selected_count, selectedPaths.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedPaths.isEmpty()) TextSecondary else Indigo,
                            modifier = Modifier.weight(1f),
                        )
                        val allSelected = selectedPaths.size == tracks.size
                        Text(
                            if (allSelected) stringResource(R.string.pl_deselect_all) else stringResource(R.string.pl_select_all),
                            style = MaterialTheme.typography.labelLarge,
                            color = Indigo,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onToggleSelectAll)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Text(
                            stringResource(R.string.lib_done),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onExitSelection)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onBatchAddToPlaylist,
                            enabled = selectedPaths.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.PlaylistAdd, null, tint = Indigo, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.pl_dialog_title), style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = onBatchDelete,
                            enabled = selectedPaths.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Default.Delete, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.lib_remove), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onImportFiles,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.UploadFile, null, tint = Indigo, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_import_files), style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = onImportFolder,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Folder, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_import_folder), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * 曲目行。
 * 副标题第一行显示艺术家（可能来自推断或手动修正），第二行是时长/音符/大小。
 */
@Composable
private fun TrackRow(
    index: Int,
    track: MidiTrack,
    isCurrent: Boolean,
    playCount: Int,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEdit: () -> Unit,
    onExport: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    /** 批量选择模式：行首序号换成勾选框、整行点击=切换选中、右侧四个图标隐藏。 */
    selectable: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    /** 长按进入批量选择并勾上当前这首（由调用方决定"还没进入就顺便进入"）。 */
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectable) onToggleSelect() else onClick() },
                onLongClick = onLongPress,
            )
            .background(
                when {
                    selectable && selected -> Indigo.copy(alpha = 0.18f)
                    isCurrent -> Indigo.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
            )
            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectable) {
            // 勾选框占的宽度和序号一致（28dp），切换模式时整行内容不左右跳。
            // 不能直接给 Icon 同时写 width + size：size 会把宽度也覆盖成 20dp。
            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = if (selected) stringResource(R.string.lib_deselect) else stringResource(R.string.lib_select),
                    tint = if (selected) Indigo else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            // 序号固定两位宽，避免两位数时整行抖动
            Text(
                "%02d".format(index + 1),
                style = TimecodeStyle.copy(fontSize = 12.sp),
                color = if (isCurrent) Indigo else TextSecondary,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Start,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) Indigo else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            // buildString 的 lambda 不是可组合上下文，带占位的文案要先取出来
            val noteCountSuffix = if (track.noteCount > 0) {
                stringResource(R.string.lib_note_count_suffix, track.noteCount)
            } else {
                ""
            }
            val playCountSuffix = if (playCount > 0) {
                stringResource(R.string.lib_play_count_suffix, playCount)
            } else {
                ""
            }
            Text(
                buildString {
                    val artist = track.artist
                    if (!artist.isNullOrBlank()) {
                        append(artist)
                        append(" · ")
                    }
                    if (track.durationMs > 0) append(formatDuration(track.durationMs)) else append("--:--")
                    append(noteCountSuffix)
                    append(playCountSuffix)
                    append(" · ${formatSize(track.sizeBytes)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (track.artist != null) Indigo.copy(alpha = 0.85f) else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 选择模式下把四个操作图标收起来：整行的语义已经变成"勾选"，
        // 再留一排可点的单曲操作容易误触，也让选中态的视觉变得嘈杂。
        if (!selectable) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onAddToPlaylist),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlaylistAdd, stringResource(R.string.pl_dialog_title), tint = Indigo, modifier = Modifier.size(19.dp))
            }
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Edit, stringResource(R.string.lib_edit_track), tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            if (onExport != null) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onExport),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.FileDownload, stringResource(R.string.export_title), tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            if (onDelete != null) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Delete, stringResource(R.string.lib_remove), tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// =====================================================================
// 艺术家分区
// =====================================================================

@Composable
private fun ArtistsSection(
    groups: List<Pair<String, List<MidiTrack>>>,
    searchActive: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        when {
            groups.isEmpty() && searchActive -> NoMatchHint()
            groups.isEmpty() -> EmptyState(
                icon = Icons.Default.Person,
                title = stringResource(R.string.lib_artists_empty_title),
                message = stringResource(R.string.lib_artists_empty_message),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(groups, key = { it.first }) { (artist, list) ->
                    Row(
                        modifier = Modifier
                            .animateItemPlacement(Motion.itemPlacement())
                            .fillMaxWidth()
                            .clickable { onOpen(artist) }
                            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Indigo.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Person, null, tint = Indigo, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.lib_track_count, list.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单个艺术家的曲目列表。 */
@Composable
private fun ArtistDetailScreen(
    artist: String,
    tracks: List<MidiTrack>,
    currentPath: String?,
    playCounts: Map<String, Int>,
    onBack: () -> Unit,
    onPlayQueue: (List<MidiTrack>, Int) -> Unit,
    onRequestAddToPlaylist: (MidiTrack) -> Unit,
    onRequestEdit: (MidiTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {

        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MusicNote, stringResource(R.string.action_back), tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.lib_track_count, tracks.size), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

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
                Icon(Icons.Default.MusicNote, null, tint = Indigo, modifier = Modifier.size(20.dp))
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
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            itemsIndexed(tracks, key = { _, t -> t.path }) { index, track ->
                TrackRow(
                    index = index,
                    track = track,
                    isCurrent = track.path == currentPath,
                    playCount = playCounts[track.fileName] ?: 0,
                    onClick = { onPlayQueue(tracks, index) },
                    onAddToPlaylist = { onRequestAddToPlaylist(track) },
                    onEdit = { onRequestEdit(track) },
                    onExport = null,
                    onDelete = null,
                    modifier = Modifier.animateItemPlacement(Motion.itemPlacement()),
                )
            }
        }
    }
}

// =====================================================================
// 最近播放分区
// =====================================================================

@Composable
private fun RecentSection(
    recent: List<Pair<MidiTrack, PlayRecord>>,
    searchActive: Boolean,
    currentPath: String?,
    onPlayQueue: (List<MidiTrack>, Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        when {
            recent.isEmpty() && searchActive -> NoMatchHint()
            recent.isEmpty() -> EmptyState(
                icon = Icons.Default.History,
                title = stringResource(R.string.lib_recent_empty_title),
                message = stringResource(R.string.lib_recent_empty_message),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            else -> {
                val queue = remember(recent) { recent.map { it.first } }
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.History, null, tint = Indigo, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.lib_recent_count, recent.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.action_clear),
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentOrange,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClear)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    itemsIndexed(recent, key = { _, item -> item.first.path }) { index, (track, record) ->
                        val isCurrent = track.path == currentPath
                        Row(
                            modifier = Modifier
                                .animateItemPlacement(Motion.itemPlacement())
                                .fillMaxWidth()
                                .clickable { onPlayQueue(queue, index) }
                                .background(if (isCurrent) Indigo.copy(alpha = 0.14f) else Color.Transparent)
                                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isCurrent) Indigo else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                // buildString 内不能调用 stringResource，先取出来
                                val playedTimes = stringResource(R.string.lib_played_times, record.count)
                                Text(
                                    buildString {
                                        if (!track.artist.isNullOrBlank()) {
                                            append(track.artist)
                                            append(" · ")
                                        }
                                        append(playedTimes)
                                        append(" · ")
                                        append(formatRelativeTime(record.lastPlayed))
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentGreen.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    "${record.count}x",
                                    style = TimecodeStyle.copy(fontSize = 11.sp),
                                    color = AccentGreen,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// 歌单分区
// =====================================================================

@Composable
private fun PlaylistsSection(
    playlists: List<Playlist>,
    searchActive: Boolean,
    resolvePlaylist: (Playlist) -> List<MidiTrack>,
    onCreate: () -> Unit,
    onImportM3u: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onCreate)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = Indigo, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.pl_new_playlist),
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            // 整行是"新建歌单"的点击区，这个内层 Text 自带 clickable，
            // 事件会被内层先消费掉，所以点"导入 M3U"不会误触发新建
            Text(
                stringResource(R.string.lib_import_m3u),
                style = MaterialTheme.typography.labelMedium,
                color = Indigo,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onImportM3u)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.lib_playlists_count, playlists.size), style = TimecodeStyle, color = TextSecondary)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))

        if (playlists.isEmpty()) {
            if (searchActive) {
                NoMatchHint(Modifier.weight(1f).fillMaxWidth())
            } else {
                EmptyState(
                    icon = Icons.Default.PlaylistAdd,
                    title = stringResource(R.string.lib_playlists_empty_title),
                    message = stringResource(R.string.lib_playlists_empty_message),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onCreate, shape = RoundedCornerShape(20.dp)) {
                                Text(stringResource(R.string.pl_new_playlist))
                            }
                            OutlinedButton(onClick = onImportM3u, shape = RoundedCornerShape(20.dp)) {
                                Text(stringResource(R.string.lib_import_m3u))
                            }
                        }
                    },
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                val count = resolvePlaylist(playlist).size
                PlaylistRow(
                    playlist = playlist,
                    trackCount = count,
                    onOpen = { onOpen(playlist) },
                    onRename = { onRename(playlist) },
                    onDelete = { onDelete(playlist) },
                    modifier = Modifier.animateItemPlacement(Motion.itemPlacement()),
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    trackCount: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Indigo.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Indigo, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (trackCount > 0) stringResource(R.string.lib_track_count, trackCount) else stringResource(R.string.lib_empty_playlist),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
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
            modifier = Modifier.size(42.dp).clip(CircleShape).clickable { confirmingDelete = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Delete, stringResource(R.string.pl_delete_playlist), tint = TextSecondary, modifier = Modifier.size(19.dp))
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.pl_delete_confirm_title), color = TextPrimary) },
            text = { Text(stringResource(R.string.pl_delete_confirm_text, playlist.name), color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.pl_delete), color = AccentOrange) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.action_cancel), color = TextSecondary) }
            },
            containerColor = CharcoalRaised,
        )
    }
}

// =====================================================================
// 曲目信息修正
// =====================================================================

/**
 * 手动修正艺术家与曲名。
 *
 * 两个输入框都留空即表示「恢复自动推断」。
 */
@Composable
private fun EditTrackDialog(
    track: MidiTrack,
    onDismiss: () -> Unit,
    onConfirm: (artist: String?, title: String?) -> Unit,
) {
    var artist by remember(track.path) { mutableStateOf(track.artist.orEmpty()) }
    var title by remember(track.path) { mutableStateOf(track.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lib_edit_title), color = TextPrimary) },
        text = {
            Column {
                Text(
                    track.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                DialogTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    placeholder = stringResource(R.string.lib_artist_placeholder),
                )
                Spacer(Modifier.height(10.dp))
                DialogTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = stringResource(R.string.lib_title_placeholder),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(artist, title) }) { Text(stringResource(R.string.pl_save), color = Indigo) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = TextSecondary) }
        },
        containerColor = CharcoalRaised,
    )
}

@Composable
private fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = TextSecondary) },
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
