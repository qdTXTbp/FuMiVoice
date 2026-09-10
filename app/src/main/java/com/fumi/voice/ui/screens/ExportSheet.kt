package com.fumi.voice.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.core.content.FileProvider
import com.fumi.voice.export.AudioExporter
import com.fumi.voice.export.ExportFormat
import com.fumi.voice.export.ExportResult
import com.fumi.voice.model.MidiTrack
import com.fumi.voice.ui.components.PillTag
import com.fumi.voice.ui.components.SectionLabel
import com.fumi.voice.ui.theme.AccentGreen
import com.fumi.voice.ui.theme.AccentOrange
import com.fumi.voice.ui.theme.AccentRed
import com.fumi.voice.ui.theme.CharcoalCard
import com.fumi.voice.ui.theme.CharcoalRaised
import com.fumi.voice.ui.theme.Divider
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.TextPrimary
import com.fumi.voice.ui.theme.TextSecondary
import com.fumi.voice.ui.theme.TimecodeStyle
import com.fumi.voice.util.formatDuration
import com.fumi.voice.util.formatSize
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** 导出流程的粗粒度阶段（只在主线程改）。 */
private sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Running(val format: ExportFormat) : ExportUiState
    data class Done(val result: ExportResult) : ExportUiState
    data class Failed(val message: String) : ExportUiState
}

/**
 * 导出为音频的底部弹层。
 *
 * 只在设备真正具备某种编码能力时才把该格式点得动：WAV 永远可用，
 * FLAC / AAC 取决于设备有没有对应 MediaCodec 编码器，
 * MP3 取决于有没有把 LAME 打进包。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    track: MidiTrack,
    soundFontPath: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var state by remember(track.path) { mutableStateOf<ExportUiState>(ExportUiState.Idle) }

    // 进度来自 IO 线程，用 StateFlow 传递，不去跨线程写 Compose 状态
    val progressFlow = remember(track.path) { MutableStateFlow(0L to 0L) }
    val progress by progressFlow.collectAsState()

    val support = remember { ExportFormat.entries.associateWith { AudioExporter.isSupported(it) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CharcoalRaised,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)) {

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
                    Icon(Icons.Default.FileDownload, null, tint = Indigo, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("导出为音频", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            when (val current = state) {
                is ExportUiState.Idle -> {
                    SectionLabel("选择格式")
                    ExportFormat.entries.forEach { format ->
                        FormatRow(
                            format = format,
                            supported = support[format] == true,
                            onClick = {
                                progressFlow.value = 0L to track.durationMs
                                state = ExportUiState.Running(format)
                                scope.launch {
                                    val result = runCatching {
                                        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                                            ?: context.filesDir
                                        val out = uniqueFile(dir, sanitize(track.title), format.extension)
                                        AudioExporter.export(
                                            source = track,
                                            soundFontPath = soundFontPath,
                                            format = format,
                                            outFile = out,
                                        ) { processed, total ->
                                            progressFlow.value = processed to total
                                        }
                                    }
                                    state = result.fold(
                                        onSuccess = { ExportUiState.Done(it) },
                                        onFailure = { ExportUiState.Failed(it.message ?: "导出失败") },
                                    )
                                }
                            },
                        )
                    }
                    Text(
                        "导出文件保存在应用专属目录，卸载应用会一并删除；导出后可直接分享到其它应用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }

                is ExportUiState.Running -> {
                    val (processed, total) = progress
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Indigo,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "正在导出 ${current.format.label}…",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (total > 0) (processed.toFloat() / total).coerceIn(0f, 1f) else 0f
                            },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = Indigo,
                            trackColor = Divider,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${formatDuration(processed)} / ${formatDuration(total)}",
                            style = TimecodeStyle,
                            color = TextSecondary,
                        )
                    }
                }

                is ExportUiState.Done -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = AccentGreen,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("导出完成", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            current.result.file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatSize(current.result.file.length())} · " +
                                "${current.result.sampleRate} Hz · " +
                                "${formatDuration(current.result.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            current.result.file.parent ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { shareAudio(context, current.result.file) },
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Icon(Icons.Default.Share, null, tint = Indigo, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("分享")
                            }
                            OutlinedButton(
                                onClick = { state = ExportUiState.Idle },
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Text("再导一份")
                            }
                        }
                    }
                }

                is ExportUiState.Failed -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Text("导出失败", style = MaterialTheme.typography.titleSmall, color = AccentRed)
                        Spacer(Modifier.height(6.dp))
                        Text(current.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { state = ExportUiState.Idle },
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text("重新选择格式")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatRow(
    format: ExportFormat,
    supported: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CharcoalCard)
            .clickable(enabled = supported, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    format.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (supported) TextPrimary else TextSecondary,
                )
                if (!supported) {
                    Spacer(Modifier.width(8.dp))
                    PillTag(
                        if (format == ExportFormat.MP3) "未集成" else "设备不支持",
                        AccentOrange,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                format.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

/** 文件名的非法字符替换掉，避免建文件失败。 */
private fun sanitize(title: String): String {
    val cleaned = title.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
    return cleaned.ifBlank { "export" }.take(64)
}

private fun uniqueFile(dir: File, base: String, ext: String): File {
    if (!dir.exists()) dir.mkdirs()
    var candidate = File(dir, "$base.$ext")
    var i = 2
    while (candidate.exists()) {
        candidate = File(dir, "$base $i.$ext")
        i++
    }
    return candidate
}

private fun shareAudio(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享音频"))
}
