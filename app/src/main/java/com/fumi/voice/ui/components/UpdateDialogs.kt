package com.fumi.voice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fumi.voice.ui.theme.TextSecondary
import com.fumi.voice.update.AppUpdater

/** 「发现新版本」弹窗：展示版本号与更新说明，由用户决定是否更新。 */
@Composable
fun UpdateAvailableDialog(
    info: AppUpdater.UpdateInfo,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    // 更新说明可能较长，限高可滚动，避免撑破弹窗
                    .verticalScroll(rememberScrollState()),
            ) {
                if (info.notes.isBlank()) {
                    Text("建议更新到最新版本。", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(info.notes, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "下载完成后会拉起系统安装界面，覆盖安装即可，曲库与歌单不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text("立即更新") } },
        dismissButton = { TextButton(onClick = onLater) { Text("稍后") } },
    )
}

/** 下载进度弹窗：下载中不可取消（中途退出会留下半截包）。 */
@Composable
fun UpdateDownloadDialog(read: Long, total: Long) {
    val known = total > 0
    val progress = if (known) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
    AlertDialog(
        // 下载期间不允许点外部/返回键关掉：界面收起后进度就看不到了
        onDismissRequest = {},
        title = { Text("正在下载更新") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (known) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${formatMb(read)} / ${formatMb(total)}（${(progress * 100).toInt()}%）",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "已下载 ${formatMb(read)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    "走国内镜像下载，稍候片刻即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        },
        confirmButton = {},
    )
}

private fun formatMb(bytes: Long): String {
    val mb = bytes / 1048576.0
    return if (mb >= 10) String.format("%.0f MB", mb) else String.format("%.1f MB", mb)
}
