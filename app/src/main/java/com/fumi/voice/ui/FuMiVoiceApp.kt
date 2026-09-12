package com.fumi.voice.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.fumi.voice.App
import com.fumi.voice.library.M3uCodec
import com.fumi.voice.library.MidiLibraryManager
import com.fumi.voice.library.PlayHistoryManager
import com.fumi.voice.library.PlayRecord
import com.fumi.voice.library.Playlist
import com.fumi.voice.library.PlaylistManager
import com.fumi.voice.library.TrackMetaStore
import com.fumi.voice.model.MidiTrack
import com.fumi.voice.player.ActiveNote
import com.fumi.voice.player.PlayState
import com.fumi.voice.player.PlaybackService
import com.fumi.voice.player.RepeatMode
import com.fumi.voice.soundfont.SoundFontInfo
import com.fumi.voice.ui.components.MessageBanner
import com.fumi.voice.ui.screens.ExportSheet
import com.fumi.voice.ui.screens.LibraryScreen
import com.fumi.voice.ui.screens.NowPlayingScreen
import com.fumi.voice.ui.screens.SoundFontScreen
import com.fumi.voice.ui.screens.CloudSyncScreen
import com.fumi.voice.ui.theme.Charcoal
import com.fumi.voice.ui.theme.CharcoalRaised
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.Motion
import com.fumi.voice.ui.theme.TextOnPrimarySoft
import com.fumi.voice.ui.theme.TextSecondary
import com.fumi.voice.util.DocumentTreeScanner
import com.fumi.voice.widget.FumiWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Tab(val label: String, val icon: ImageVector) {
    PLAY("播放", Icons.Default.Piano),
    LIBRARY("曲库", Icons.Default.LibraryMusic),
    SOUNDFONT("音色", Icons.Default.Tune),
    CLOUD("云同步", Icons.Default.Cloud),
}

/**
 * 应用外壳：MyPlayer 风格的实色顶栏 + 三标签页底部导航。
 *
 * 跨页共享的状态（曲库、歌单、音色库、下载进度、播放状态）集中在这里，
 * 各标签页保持无状态，只通过回调触发动作。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FuMiVoiceApp(
    app: App,
    initialMidiUri: Uri?,
    isInPip: Boolean = false,
    onEnterPip: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = app.player

    val playlistManager = remember { PlaylistManager(context) }
    val downloader = remember { app.soundFontDownloader }

    val metaStore = remember { TrackMetaStore(context) }
    val history = remember { PlayHistoryManager(context) }
    // 带元数据修正的曲库实例：列表里才能反映用户手改的艺术家/曲名
    val library = remember(metaStore) { MidiLibraryManager(context, metaStore) }

    // 三个标签页做成整屏可左右滑动的分页。
    //
    // 这里用 pagerState.currentPage 作为唯一事实来源，而不是自己再维护一个
    // index：拖动过半时 currentPage 就会翻页，顶栏标题和底部导航因此能跟着
    // 手指一起走，不需要另写一套"拖动进度 → 高亮"的同步逻辑，也就不会出现
    // 「页面滑过去了、导航栏还停在上一个」这类不同步。
    val pagerState = rememberPagerState(pageCount = { Tab.entries.size })

    /** 当前标签页；手指拖到一半就会更新，顶栏与导航栏都读它。 */
    val tab = pagerState.currentPage

    /** 切页统一入口：点导航栏、代码里跳转都走这里，避免和手势状态打架。 */
    fun goToTab(index: Int) {
        scope.launch {
            // 必须显式给 animationSpec。animateScrollToPage 默认用 spring，
            // 而弹簧的时长取决于距离：从「音色」跳回「播放」要跨两页，
            // 实测要一秒多才停稳，点个导航栏等一秒会以为没点上。
            // 固定成 280ms 的补间后，无论跨几页都是同一个手感。
            pagerState.animateScrollToPage(
                page = index,
                animationSpec = Motion.spec(Motion.Standard, Motion.EmphasizedEasing),
            )
        }
    }

    val playerState by player.state.collectAsState()
    var activeNotes by remember { mutableStateOf<List<ActiveNote>>(emptyList()) }

    var tracks by remember { mutableStateOf<List<MidiTrack>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var historyRecords by remember { mutableStateOf<List<PlayRecord>>(emptyList()) }
    var libraryLoading by remember { mutableStateOf(true) }
    var sounds by remember { mutableStateOf<List<SoundFontInfo>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportTrack by remember { mutableStateOf<MidiTrack?>(null) }
    // 歌单详情点「导入文件」时的目标歌单：launcher 回调拿不到参数，只能先存起来
    var pendingPlaylistImport by remember { mutableStateOf<Playlist?>(null) }

    val downloadStates by downloader.tasks.collectAsState()
    val downloadedIds by downloader.completed.collectAsState()

    /** 最近播放：把历史记录映射回曲库对象，曲库中已删除的自然消失。 */
    val recentTracks = remember(historyRecords, tracks) {
        val byName = tracks.associateBy { it.fileName }
        historyRecords.mapNotNull { record -> byName[record.fileName]?.let { it to record } }
    }
    val playCounts = remember(historyRecords) {
        historyRecords.associate { it.fileName to it.count }
    }

    fun reloadHistory() {
        scope.launch {
            historyRecords = withContext(Dispatchers.IO) { history.recent(200) }
        }
    }

    fun reloadLibrary() {
        scope.launch {
            libraryLoading = true
            // 走带元数据修正的实例，列表里才能反映用户手改的艺术家/曲名
            val loaded = withContext(Dispatchers.IO) { library.list() }
            tracks = loaded
            libraryLoading = false
            // 曲库文件被删后清理悬空的历史/修正记录
            history.pruneTo(loaded.map { it.fileName }.toSet())
            reloadHistory()
        }
    }

    fun reloadPlaylists() {
        scope.launch {
            playlists = withContext(Dispatchers.IO) { playlistManager.list() }
        }
    }

    fun reloadSounds() {
        sounds = app.soundFonts.list()
    }

    /**
     * 把歌单导出成 .m3u 并交给系统分享。
     *
     * 条目写的是文件名而不是绝对路径：曲库都在应用私有目录下，
     * 绝对路径离开这台设备就失效，文件名反倒是可用的相对路径，
     * 别的播放器把 m3u 和音频放一起也能认出来。
     */
    fun exportPlaylistM3u(playlist: Playlist) {
        val list = runCatching { playlistManager.resolve(playlist, tracks) }.getOrDefault(emptyList())
        if (list.isEmpty()) {
            message = "「${playlist.name}」里还没有曲目"
            return
        }
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                    if (!dir.exists()) dir.mkdirs()
                    File(dir, "${sanitizeFileName(playlist.name)}.m3u").apply {
                        writeText(M3uCodec.build(list), Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (file == null) {
                message = "导出失败，无法写入文件"
                return@launch
            }
            val shared = runCatching { shareFile(context, file, "audio/x-mpegurl") }
            message = if (shared.isSuccess) {
                "已导出「${playlist.name}」，共 ${list.size} 首"
            } else {
                "已生成 ${file.name}，但分享失败"
            }
        }
    }

    LaunchedEffect(Unit) {
        player.setOnNoteListener { activeNotes = it }
        // 换曲时记一次播放历史
        player.onTrackLoaded = { loaded ->
            scope.launch {
                withContext(Dispatchers.IO) { history.record(loaded.fileName) }
                reloadHistory()
            }
        }
        reloadLibrary()
        reloadPlaylists()
        reloadSounds()
        // 内置音色库由 App 在后台线程解压装载，稍后再刷新一次
        kotlinx.coroutines.delay(600)
        reloadSounds()
    }

    // 播放开始后拉起前台服务，通知栏/耳机/锁屏即可控制（乐器试听不打扰通知栏）
    LaunchedEffect(playerState.playState, playerState.isPreview) {
        if (playerState.playState != PlayState.STOPPED && !playerState.isPreview) {
            PlaybackService.start(context)
        }
    }

    // 曲目/播放状态变化时刷新桌面小部件；没有放置部件时内部直接返回
    LaunchedEffect(playerState.track?.path, playerState.playState, playerState.soundFontName) {
        FumiWidgetProvider.refresh(context)
    }

    // 切回列表页时重新扫描，保证导入/删除后的结果立刻可见。
    //
    // 用 settledPage 而不是拖动中的 currentPage：后者在手指滑过一半时就翻页，
    // 于是磁盘扫描 + 重建整个列表正好压在滑动动画上，滑动会顿一下。
    // 等停稳再扫晚不了几百毫秒，用户感觉不到，但滑动顺了。
    val settledPage = pagerState.settledPage
    LaunchedEffect(settledPage) {
        when (settledPage) {
            1 -> {
                reloadLibrary()
                reloadPlaylists()
            }
            2 -> reloadSounds()
        }
    }

    /**
     * 导入 M3U 播放列表。
     *
     * 两条入口共用：歌单页的文件选择器，以及外部应用「用 FuMiVoice 打开」一个 .m3u。
     *
     * m3u 里记的是文件名，按文件名和曲库比对；对不上的直接跳过并在提示里报数量，
     * 而不是整个导入失败——从别的播放器导出的 m3u 常常夹着几本机没有的曲子。
     */
    fun importM3uFromUri(uri: Uri) {
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: return@runCatching null

                    val entries = M3uCodec.parse(text)
                    val byName = library.list().associateBy { it.fileName.lowercase() }
                    val matched = LinkedHashSet<MidiTrack>()
                    for (entry in entries) {
                        byName[M3uCodec.fileNameOf(entry).lowercase()]?.let { matched.add(it) }
                    }
                    val baseName = DocumentTreeScanner.displayName(context, uri)
                        ?.substringBeforeLast('.')
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "导入的播放列表"
                    Triple(baseName, matched.toList(), entries.size)
                }.getOrNull()
            }

            if (parsed == null) {
                message = "无法读取该 M3U 文件"
                return@launch
            }
            val (baseName, matched, total) = parsed
            if (matched.isEmpty()) {
                message = "M3U 里 $total 个条目都不在曲库中"
                return@launch
            }
            withContext(Dispatchers.IO) {
                val created = playlistManager.create(baseName)
                playlistManager.addTracks(created.id, matched)
            }
            reloadPlaylists()
            val skipped = total - matched.size
            message = if (skipped > 0) {
                "已导入歌单「$baseName」共 ${matched.size} 首，跳过 $skipped 首（不在曲库中）"
            } else {
                "已导入歌单「$baseName」共 ${matched.size} 首"
            }
        }
    }

    // 文件管理器"打开方式"进入：可能是 MIDI，也可能是 M3U
    LaunchedEffect(initialMidiUri) {
        val uri = initialMidiUri ?: return@LaunchedEffect
        // 先认 M3U：它进来是要"导入成歌单"，不是直接当曲目播
        if (isM3uUri(context, uri)) {
            importM3uFromUri(uri)
        } else if (player.loadMidi(uri)) {
            player.play()
        }
    }

    /** 统一入口：设置队列并播放指定位置。 */
    fun playQueue(queue: List<MidiTrack>, index: Int) {
        if (queue.isEmpty()) return
        player.setRepeatMode(
            if (playerState.repeatMode == RepeatMode.SHUFFLE) RepeatMode.SHUFFLE else RepeatMode.LIST
        )
        if (player.setPlaylist(queue, index)) {
            goToTab(0)
        } else {
            // 装载失败必须说出来。之前这里没有 else，点了曲目没反应也没提示，
            // 用户只会以为应用卡了——碰到 BASS 解不了的文件尤其容易踩。
            message = "无法播放「${queue.getOrNull(index)?.title ?: queue[0].title}」，格式可能不受支持"
        }
    }

    // ---------- 文件选择 ----------

    val openOnePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            if (player.loadMidi(it)) {
                player.play()
                goToTab(0)
            } else {
                message = "无法打开该文件，可能不是有效的 MIDI"
            }
        }
    }

    val importFilesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            libraryLoading = true
            val result = withContext(Dispatchers.IO) { library.importAll(uris) }
            tracks = withContext(Dispatchers.IO) { library.list() }
            libraryLoading = false
            reloadPlaylists()
            message = when {
                result.imported.isEmpty() -> "导入失败，请确认是 .mid / .midi 文件"
                result.skipped > 0 -> "导入 ${result.imported.size} 首，跳过 ${result.skipped} 个无效文件"
                else -> "已导入 ${result.imported.size} 首曲目"
            }
        }
    }

    val importFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            libraryLoading = true
            val result = withContext(Dispatchers.IO) { library.importFromTree(treeUri) }
            tracks = withContext(Dispatchers.IO) { library.list() }
            libraryLoading = false
            reloadPlaylists()
            message = if (result.imported.isEmpty()) {
                "该文件夹里没有找到 MIDI 文件"
            } else {
                "已导入 ${result.imported.size} 首曲目"
            }
        }
    }

    /** 歌单页的文件选择器：选完交给 [importM3uFromUri] 统一处理。 */
    val importM3uPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importM3uFromUri(uri)
    }

    /**
     * 歌单详情里的「导入文件」：先把选中的文件导入曲库，
     * 再把导入成功的曲目直接加进目标歌单——用户在这一个动作里既导了文件又进了歌单，
     * 不用"先回曲目页导入、再切回歌单逐首添加"。
     */
    val importIntoPlaylistPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val target = pendingPlaylistImport
        pendingPlaylistImport = null
        if (uris.isEmpty() || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            libraryLoading = true
            val result = withContext(Dispatchers.IO) { library.importAll(uris) }
            tracks = withContext(Dispatchers.IO) { library.list() }
            libraryLoading = false
            if (result.imported.isNotEmpty()) {
                withContext(Dispatchers.IO) { playlistManager.addTracks(target.id, result.imported) }
            }
            reloadPlaylists()
            message = when {
                result.imported.isEmpty() -> "导入失败，请确认是 .mid / .midi 文件"
                else -> "已导入 ${result.imported.size} 首并加入「${target.name}」"
            }
        }
    }

    // ---------- 布局 ----------

    // 小窗模式：不带顶栏、不带底部导航，整窗只留音符瀑布
    if (isInPip) {
        NowPlayingScreen(
            player = player,
            state = playerState,
            activeNotes = activeNotes,
            onPickMidi = {},
            onOpenSoundFonts = {},
            isInPip = true,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Charcoal)) {

        AppHeader(
            tab = tab,
            playerState = playerState,
            trackCount = tracks.size,
            onHeaderAction = {
                openOnePicker.launch(arrayOf("audio/midi", "audio/x-midi", "application/x-midi", "*/*"))
            },
        )

        // 展开/收起由 MessageBanner 内部做动画，这里只负责给值
        MessageBanner(message = message, onDismiss = { message = null })

        // 整屏可滑动的三页。
        //
        // 预加载相邻一页（beyondBoundsPageCount = 1）：不预加载的话，滑到一半
        // 才开始首次组合目标页，那一帧的合成开销正好压在滑动动画上，手感发顿。
        //
        // 代价是播放页在别的标签页下也被组合着，而瀑布是 withFrameNanos 驱动的
        // 逐帧重绘——所以下面用 animateWaterfall 把非当前页的动画关掉：
        // 既不白耗电，又拿回了滑动的顺滑。
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondBoundsPageCount = 1,
            key = { Tab.entries[it].name },
        ) { page ->
            when (Tab.entries[page]) {
                Tab.PLAY -> NowPlayingScreen(
                    player = player,
                    state = playerState,
                    activeNotes = activeNotes,
                    onPickMidi = {
                        openOnePicker.launch(
                            arrayOf("audio/midi", "audio/x-midi", "application/x-midi", "*/*")
                        )
                    },
                    onOpenSoundFonts = { goToTab(2) },
                    onEnterPip = onEnterPip,
                    // 滑动过程中放行（画面正在过渡，冻住会看出来），
                    // 停稳后只有停在播放页才继续动画
                    animateWaterfall = pagerState.isScrollInProgress ||
                        pagerState.settledPage == Tab.PLAY.ordinal,
                )

                Tab.LIBRARY -> LibraryScreen(
                    tracks = tracks,
                    playlists = playlists,
                    resolvePlaylist = { playlist ->
                        runCatching { playlistManager.resolve(playlist, tracks) }
                            .getOrDefault(emptyList())
                    },
                    recentTracks = recentTracks,
                    playCounts = playCounts,
                    currentPath = playerState.track?.path,
                    loading = libraryLoading,
                    onPlayQueue = { queue, index -> playQueue(queue, index) },
                    onImportFiles = { importFilesPicker.launch(arrayOf("*/*")) },
                    onImportFolder = { importFolderPicker.launch(null) },
                    onOpenFile = {
                        openOnePicker.launch(
                            arrayOf("audio/midi", "audio/x-midi", "application/x-midi", "*/*")
                        )
                    },
                    onCreatePlaylist = { name ->
                        scope.launch {
                            withContext(Dispatchers.IO) { playlistManager.create(name) }
                            reloadPlaylists()
                            message = "已创建歌单「$name」"
                        }
                    },
                    // 弹窗上写的是「新建并加入」，就必须真的把歌单加进去。
                    // 之前这里只调了 onCreatePlaylist，结果歌单建出来是空的：
                    // 导出时说"里还没有曲目"、进详情页说"歌单还是空的"，
                    // 用户只能看到"新建成功了但什么都没发生"。
                    // 现在接受一个列表，批量加入时也走这一条路径。
                    onCreatePlaylistAndAdd = { name, toAdd ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val created = playlistManager.create(name)
                                playlistManager.addTracks(created.id, toAdd)
                            }
                            reloadPlaylists()
                            message = if (toAdd.size == 1) {
                                "已新建歌单「$name」并加入「${toAdd.first().title}」"
                            } else {
                                "已新建歌单「$name」并加入 ${toAdd.size} 首曲目"
                            }
                        }
                    },
                    onRenamePlaylist = { playlist, name ->
                        scope.launch {
                            withContext(Dispatchers.IO) { playlistManager.rename(playlist.id, name) }
                            reloadPlaylists()
                        }
                    },
                    onDeletePlaylist = { playlist ->
                        scope.launch {
                            withContext(Dispatchers.IO) { playlistManager.delete(playlist.id) }
                            reloadPlaylists()
                            message = "已删除歌单「${playlist.name}」"
                        }
                    },
                    onRemoveFromPlaylist = { playlist, track ->
                        scope.launch {
                            withContext(Dispatchers.IO) { playlistManager.removeTrack(playlist.id, track.fileName) }
                            reloadPlaylists()
                        }
                    },
                    onAddToPlaylists = { chosenTracks, chosenPlaylists ->
                        scope.launch {
                            val added = withContext(Dispatchers.IO) {
                                chosenPlaylists.sumOf { playlistManager.addTracks(it.id, chosenTracks) }
                            }
                            reloadPlaylists()
                            message = if (added > 0) "已加入 ${chosenPlaylists.size} 个歌单" else "这些曲目已在所选歌单中"
                        }
                    },
                    onDeleteTrack = { track ->
                        scope.launch {
                            withContext(Dispatchers.IO) { library.delete(track) }
                            reloadLibrary()
                            reloadPlaylists()
                            message = "已从曲库移除「${track.title}」"
                        }
                    },
                    onDeleteTracks = { targets ->
                        if (targets.isNotEmpty()) {
                            scope.launch {
                                withContext(Dispatchers.IO) { targets.forEach { library.delete(it) } }
                                reloadLibrary()
                                reloadPlaylists()
                                message = "已从曲库移除 ${targets.size} 首曲目"
                            }
                        }
                    },
                    onEditTrack = { track, artist, title ->
                        scope.launch {
                            withContext(Dispatchers.IO) { metaStore.put(track.fileName, artist, title) }
                            reloadLibrary()
                            message = "已更新「${track.fileName}」的信息"
                        }
                    },
                    onExportTrack = { exportTrack = it },
                    onClearHistory = {
                        scope.launch {
                            withContext(Dispatchers.IO) { history.clear() }
                            reloadHistory()
                            message = "播放历史已清空"
                        }
                    },
                    onImportM3u = {
                        // m3u 的 MIME 各家给得不一致，挂上 */* 兜底，
                        // 选到什么文件由解析阶段再判
                        importM3uPicker.launch(
                            arrayOf("audio/x-mpegurl", "audio/mpegurl", "*/*")
                        )
                    },
                    onExportPlaylistM3u = { exportPlaylistM3u(it) },
                    onImportFilesIntoPlaylist = { playlist ->
                        pendingPlaylistImport = playlist
                        importIntoPlaylistPicker.launch(arrayOf("*/*"))
                    },
                )

                Tab.SOUNDFONT -> SoundFontScreen(
                    player = player,
                    manager = app.soundFonts,
                    sounds = sounds,
                    currentFontName = playerState.soundFontName,
                    downloadStates = downloadStates,
                    downloadedIds = downloadedIds,
                    onDownload = { source ->
                        scope.launch {
                            val info = downloader.download(source)
                            if (info != null) {
                                reloadSounds()
                                message = "「${source.displayName}」下载完成，已加入音色库"
                            }
                        }
                    },
                    onRefresh = { reloadSounds() },
                )

                Tab.CLOUD -> CloudSyncScreen(
                    onRefresh = { reloadLibrary(); reloadPlaylists() },
                )
            }
        }

        NavigationBar(containerColor = CharcoalRaised) {
            Tab.entries.forEachIndexed { index, item ->
                val selected = tab == index
                // 选中图标轻微放大。M3 的 NavigationBarItem 只动指示器底色，
                // 图标本身没有任何反馈，加上缩放后"现在在哪一页"更一眼可辨。
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.86f,
                    animationSpec = Motion.spring(),
                    label = "navIconScale",
                )
                NavigationBarItem(
                    selected = selected,
                    onClick = { goToTab(index) },
                    icon = {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(22.dp).scale(iconScale),
                        )
                    },
                    label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = Color.White,
                        indicatorColor = Indigo,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                    ),
                )
            }
        }
    }

    // 导出弹层放在外层：它需要跟着当前选中的音色库走，而不依赖曲库页是否可见
    exportTrack?.let { track ->
        ExportSheet(
            track = track,
            soundFontPath = playerState.soundFontName?.let { name -> app.soundFonts.find(name)?.path },
            onDismiss = { exportTrack = null },
        )
    }
}

/** 顶栏：实色主色背景 + 当前页标题 + 一行上下文副标题。 */
@Composable
private fun AppHeader(
    tab: Int,
    playerState: com.fumi.voice.player.PlayerUiState,
    trackCount: Int,
    onHeaderAction: () -> Unit,
) {
    val current = Tab.entries[tab]

    Column(modifier = Modifier.fillMaxWidth().background(Indigo)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 标题只做淡入淡出，不跟着横滑。
            // 页面本身已经在整屏滑动，标题再叠一层方向位移的话，
            // 过渡那 200 多毫秒里新旧两行标题会同时在 56dp 的高度里错位交叠，
            // 看起来像渲染错乱。这里让标题安静地换掉，把"位移"留给页面。
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    fadeIn(animationSpec = Motion.spec(Motion.Standard)) togetherWith
                        fadeOut(animationSpec = Motion.spec(Motion.Instant))
                },
                modifier = Modifier.weight(1f),
                label = "headerTitle",
            ) { item ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                    )
                    Text(
                        headerSubtitle(item, playerState, trackCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextOnPrimarySoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 「打开文件」只属于曲库页，进出也带一点过渡，免得按钮突兀地闪现
            AnimatedVisibility(
                visible = current == Tab.LIBRARY,
                enter = fadeIn(animationSpec = Motion.spec(Motion.Quick)) +
                    scaleIn(initialScale = 0.8f, animationSpec = Motion.spring()),
                exit = fadeOut(animationSpec = Motion.spec(Motion.Instant)),
            ) {
                IconButton(onClick = onHeaderAction) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "打开单个 MIDI 文件", tint = Color.White)
                }
            }
        }
    }
}

/**
 * 顶栏副标题。
 *
 * 抽成普通函数是为了让 AnimatedContent 能按"目标页"取值——
 * 在内容 lambda 里直接用外层的 `tab` 会读到已经变过去的值，
 * 结果新旧两页显示同一个副标题。
 */
private fun headerSubtitle(
    tab: Tab,
    playerState: com.fumi.voice.player.PlayerUiState,
    trackCount: Int,
): String = when (tab) {
    // 曲目名已经显示在瀑布下方，这里改为播报播放状态，避免重复
    Tab.PLAY -> when (playerState.playState) {
        PlayState.PLAYING -> "正在播放"
        PlayState.PAUSED -> "已暂停"
        PlayState.STOPPED -> "未在播放"
    }
    Tab.LIBRARY -> if (trackCount > 0) "$trackCount 首曲目" else "曲库为空"
    Tab.SOUNDFONT -> playerState.soundFontName?.substringBeforeLast('.') ?: "未装载音色库"
    Tab.CLOUD -> "云端同步"
}

/** 文件名里的非法字符换掉，避免建文件失败。 */
private fun sanitizeFileName(name: String): String {
    val cleaned = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
    return cleaned.ifBlank { "playlist" }.take(64)
}

/**
 * 外部传进来的 URI 是不是 M3U。
 *
 * 先看文件名后缀（最可靠），再看 MIME 兜底：各家文件管理器给 .m3u 报的类型
 * 五花八门，有报 audio/x-mpegurl 的，也有报 application/octet-stream 的。
 */
private fun isM3uUri(context: Context, uri: Uri): Boolean {
    val name = DocumentTreeScanner.displayName(context, uri)?.lowercase()
    if (name != null && (name.endsWith(".m3u") || name.endsWith(".m3u8"))) return true
    val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()?.lowercase()
        ?: return false
    return type.contains("mpegurl") || type.contains("m3u")
}

/** 用 FileProvider 把导出文件交给系统分享面板。 */
private fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享播放列表"))
}
