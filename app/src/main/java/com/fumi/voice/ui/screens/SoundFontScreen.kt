// 音色库列表用到列表项位移动画（LazyItemScope.animateItemPlacement），
// 该 API 在 Compose 1.6 仍标为实验性，这里对整个文件放开。
@file:OptIn(ExperimentalFoundationApi::class)

package com.fumi.voice.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fumi.voice.midi.PreviewMidiFactory
import com.fumi.voice.player.MidiPlayerManager
import com.fumi.voice.soundfont.DownloadState
import com.fumi.voice.soundfont.GmDrums
import com.fumi.voice.soundfont.GmInstrument
import com.fumi.voice.soundfont.GmInstruments
import com.fumi.voice.soundfont.SoundFontCatalog
import com.fumi.voice.soundfont.SoundFontInfo
import com.fumi.voice.soundfont.SoundFontManager
import com.fumi.voice.soundfont.SoundFontSource
import com.fumi.voice.ui.components.PillTag
import com.fumi.voice.ui.components.SectionLabel
import com.fumi.voice.ui.components.SegmentedTabs
import com.fumi.voice.ui.theme.AccentGreen
import com.fumi.voice.ui.theme.AccentOrange
import com.fumi.voice.ui.theme.AccentRed
import com.fumi.voice.ui.theme.CharcoalCard
import com.fumi.voice.ui.theme.CharcoalRaised
import com.fumi.voice.ui.theme.Divider
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.Motion
import com.fumi.voice.ui.theme.TextPrimary
import com.fumi.voice.ui.theme.TextSecondary
import com.fumi.voice.ui.theme.TimecodeStyle
import com.fumi.voice.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 音色页：SF2 音色库管理 + GM 乐器试听 + 在线下载。 */
@Composable
fun SoundFontScreen(
    player: MidiPlayerManager,
    manager: SoundFontManager,
    sounds: List<SoundFontInfo>,
    currentFontName: String?,
    downloadStates: Map<String, DownloadState>,
    downloadedIds: Set<String>,
    onDownload: (SoundFontSource) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var section by remember { mutableIntStateOf(0) }
    var loadingText by remember { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf<String?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** 把生成的试听片段落到缓存目录，再交给播放器播放。 */
    fun playPreview(bytes: ByteArray, label: String) {
        previewing = label
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(context.cacheDir, "preview.mid")
                    file.writeBytes(bytes)
                    player.playPreview(file.absolutePath)
                }.getOrDefault(false)
            }
            if (!ok) {
                previewing = null
                toast("试听失败，请先确认已装载音色库")
            }
        }
    }

    fun applySoundFont(info: SoundFontInfo) {
        if (loadingText != null) return
        loadingText = "正在加载 ${info.displayName}…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) { player.loadSoundFont(info.path) }
            loadingText = null
            if (ok) {
                manager.select(info)
                onRefresh()
                toast("已切换到 ${info.displayName}")
            } else {
                toast("${info.displayName} 不是有效的音色库文件")
            }
        }
    }

    fun importUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        loadingText = "正在导入 ${uris.size} 个音色库…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { manager.importAll(uris) }
            loadingText = null
            onRefresh()
            when {
                result.imported.isEmpty() -> toast("导入失败，请确认文件是 .sf2 格式")
                result.failed > 0 -> toast("成功 ${result.imported.size} 个，失败 ${result.failed} 个")
                else -> toast("成功导入 ${result.imported.size} 个音色库")
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> importUris(uris) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        loadingText = "正在扫描文件夹…"
        scope.launch {
            val uris = withContext(Dispatchers.IO) { manager.scanTree(treeUri) }
            loadingText = null
            if (uris.isEmpty()) toast("该文件夹里没有找到音色库文件") else importUris(uris)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            SegmentedTabs(
                options = listOf("音色库", "乐器试听", "在线下载"),
                selected = section,
                onSelect = { section = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            // 三个分区之间做横向位移 + 淡入淡出，方向与分段控件顺序一致。
            // 这里用 fillMaxSize 而不是 weight：原本就是靠 fillMaxSize 占满
            // 剩余高度的，换成 weight 会改变高度计算方式。
            AnimatedContent(
                targetState = section,
                transitionSpec = { Motion.horizontalSwitch(targetState > initialState) },
                modifier = Modifier.fillMaxSize(),
                label = "soundFontSection",
            ) { current ->
            when (current) {
                0 -> SoundFontList(
                    sounds = sounds,
                    currentFontName = currentFontName,
                    onApply = { applySoundFont(it) },
                    onDelete = { info ->
                        if (manager.delete(info)) {
                            onRefresh()
                            toast("已移除 ${info.displayName}")
                        } else {
                            toast("内置音色不可移除")
                        }
                    },
                    onImportFiles = { filePicker.launch(arrayOf("*/*")) },
                    onImportFolder = { folderPicker.launch(null) },
                )

                1 -> InstrumentBrowser(
                    previewing = previewing,
                    onPreviewInstrument = { inst ->
                        playPreview(PreviewMidiFactory.instrumentPreview(inst.program), inst.name)
                    },
                    onPreviewDrums = {
                        playPreview(PreviewMidiFactory.drumPattern(), "鼓组")
                    },
                    onPreviewDrumHit = { drum ->
                        playPreview(PreviewMidiFactory.drumHit(drum.note), drum.name)
                    },
                )

                else -> DownloadBrowser(
                    downloadStates = downloadStates,
                    downloadedIds = downloadedIds,
                    installedNames = sounds.map { it.fileName }.toSet(),
                    onDownload = onDownload,
                )
            }
            }
        }

        // 遮罩淡入淡出。退出动画期间 loadingText 已经是 null，
        // 留住最后一次的文字，否则会出现"遮罩还在暗下去、字先没了"。
        var lastLoadingText by remember { mutableStateOf("") }
        LaunchedEffect(loadingText) {
            loadingText?.let { lastLoadingText = it }
        }

        AnimatedVisibility(
            visible = loadingText != null,
            enter = fadeIn(animationSpec = Motion.spec(Motion.Quick)),
            exit = fadeOut(animationSpec = Motion.spec(Motion.Instant)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Indigo)
                    Spacer(Modifier.height(14.dp))
                    Text(lastLoadingText, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ---------------- 在线下载 ----------------

/**
 * 在线下载列表。
 *
 * 每条显示来源、体积、许可证与当前状态（未下载 / 下载中 / 已下载 / 失败）。
 * 下载完成后由调用方刷新音色库列表，用户可切到「音色库」分区启用。
 */
@Composable
private fun DownloadBrowser(
    downloadStates: Map<String, DownloadState>,
    downloadedIds: Set<String>,
    installedNames: Set<String>,
    onDownload: (SoundFontSource) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item {
            Text(
                "全部为第三方公开发布的自由音色库，统一走 jsDelivr CDN 直连（实测比 GitHub Pages 快约 100 倍）。下载完成后会自动校验文件格式，再收入「音色库」分区。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        SoundFontCatalog.grouped.forEach { (category, sources) ->
            item(key = "cat_$category") { SectionLabel(category) }
            items(sources, key = { it.id }) { source ->
                val state = downloadStates[source.id]
                val installed = downloadedIds.contains(source.id) ||
                    installedNames.contains(source.fileName)

                DownloadRow(
                    source = source,
                    state = state,
                    installed = installed,
                    onDownload = { onDownload(source) },
                )
            }
        }

        item {
            Text(
                "音色库版权归各自作者所有，点条目上的许可证标签可确认授权范围。若某个源不可访问，通常是 CDN 临时波动，稍后重试即可。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun DownloadRow(
    source: SoundFontSource,
    state: DownloadState?,
    installed: Boolean,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CharcoalCard)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        source.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    PillTag(source.license, Indigo)
                    if (source.slow) {
                        Spacer(Modifier.width(4.dp))
                        PillTag("较慢", AccentOrange)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append(formatSize(source.approxBytes))
                        append(" · ")
                        append(source.fileName)
                        if (source.slow) append(" · 此源下载慢，请耐心等待")
                    },
                    style = TimecodeStyle.copy(fontSize = 11.sp),
                    color = if (source.slow) AccentOrange else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(12.dp))

            when {
                installed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("已下载", style = MaterialTheme.typography.labelMedium, color = AccentGreen)
                }

                state != null && !state.failed -> Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        color = Indigo,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(26.dp),
                    )
                }

                state != null && state.failed -> OutlinedButton(
                    onClick = onDownload,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Refresh, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重试", style = MaterialTheme.typography.labelLarge)
                }

                else -> OutlinedButton(
                    onClick = onDownload,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Download, null, tint = Indigo, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("下载", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // 下载中显示具体进度，让用户对 50MB 级别的等待有预期
        if (state != null && !state.failed) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Indigo,
                trackColor = Divider,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatSize(state.bytesRead)} / ${formatSize(state.totalBytes)}",
                style = TimecodeStyle.copy(fontSize = 11.sp),
                color = TextSecondary,
            )
        }

        if (state?.failed == true) {
            Spacer(Modifier.height(8.dp))
            Text(
                "下载失败：${state.error ?: "未知错误"}",
                style = MaterialTheme.typography.bodySmall,
                color = AccentRed,
            )
        }
    }
}

// ---------------- 音色库 ----------------

@Composable
private fun SoundFontList(
    sounds: List<SoundFontInfo>,
    currentFontName: String?,
    onApply: (SoundFontInfo) -> Unit,
    onDelete: (SoundFontInfo) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            item { SectionLabel("已装载的 SoundFont", trailing = "${sounds.size} 个") }

            items(sounds, key = { it.fileName }) { info ->
                val active = info.fileName == currentFontName
                Row(
                    modifier = Modifier
                        .animateItemPlacement(Motion.itemPlacement())
                        .fillMaxWidth()
                        .clickable { onApply(info) }
                        .background(if (active) Indigo.copy(alpha = 0.14f) else Color.Transparent)
                        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) Indigo else Indigo.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = if (active) Color.White else Indigo,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                SoundFontCatalog.displayNameFor(info.fileName) ?: info.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (active) Indigo else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (info.isBundled) {
                                Spacer(Modifier.width(6.dp))
                                PillTag("内置", AccentOrange)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatSize(info.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    if (active) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "使用中",
                            tint = AccentOrange,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (!info.isBundled) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onDelete(info) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Delete, "移除", tint = TextSecondary, modifier = Modifier.size(19.dp))
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }

            item {
                Text(
                    "支持 SoundFont2（.sf2）、SoundFont3（.sf3）与 DLS。导入时会校验文件头，改名的其它文件会被拒绝。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onImportFiles,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.UploadFile, null, tint = Indigo, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导入文件", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onImportFolder,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Folder, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导入文件夹", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ---------------- 乐器试听 ----------------

@Composable
private fun InstrumentBrowser(
    previewing: String?,
    onPreviewInstrument: (GmInstrument) -> Unit,
    onPreviewDrums: () -> Unit,
    onPreviewDrumHit: (com.fumi.voice.soundfont.GmDrum) -> Unit,
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
    val grouped = remember(filtered) { filtered.groupBy { it.family } }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("搜索音色，如「钢琴」「Violin」", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
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

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { SectionLabel("鼓组", trailing = "MIDI 通道 10") }
            item {
                DrumKitCard(
                    previewing = previewing,
                    onPreviewAll = onPreviewDrums,
                    onPreviewHit = onPreviewDrumHit,
                )
            }

            if (grouped.isEmpty()) {
                item {
                    Text(
                        "没有匹配的音色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }

            grouped.forEach { (family, list) ->
                item(key = "header_$family") { SectionLabel(family) }
                items(list, key = { it.program }) { instrument ->
                    InstrumentRow(
                        instrument = instrument,
                        playing = previewing == instrument.name,
                        onClick = { onPreviewInstrument(instrument) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrumKitCard(
    previewing: String?,
    onPreviewAll: () -> Unit,
    onPreviewHit: (com.fumi.voice.soundfont.GmDrum) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CharcoalCard)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("标准鼓组", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text("两小节节奏 + 桶鼓过门", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            OutlinedButton(onClick = onPreviewAll, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.PlayArrow, null, tint = Indigo, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("试听", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 常用部件逐一点击试听，方便确认音色库里到底有哪些鼓
        GmDrums.core.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { drum ->
                    val active = previewing == drum.name
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) Indigo else CharcoalRaised)
                            .clickable { onPreviewHit(drum) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            drum.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Color.White else TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun InstrumentRow(
    instrument: GmInstrument,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (playing) Indigo.copy(alpha = 0.14f) else Color.Transparent)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // GM 程序号固定三位，等宽显示避免对齐抖动
        Text(
            "%03d".format(instrument.program + 1),
            style = TimecodeStyle.copy(fontSize = 12.sp),
            color = if (playing) Indigo else TextSecondary,
            modifier = Modifier.width(34.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                instrument.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) Indigo else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                instrument.englishName,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "试听 ${instrument.name}",
                tint = if (playing) Indigo else TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
