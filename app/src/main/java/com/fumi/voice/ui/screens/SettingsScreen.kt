package com.fumi.voice.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fumi.voice.AppInfo
import com.fumi.voice.R
import com.fumi.voice.ui.theme.Charcoal
import com.fumi.voice.ui.theme.CharcoalCard
import com.fumi.voice.ui.theme.Divider
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.TextPrimary
import com.fumi.voice.ui.theme.TextSecondary
import com.fumi.voice.update.AppUpdater
import com.fumi.voice.util.AppLanguage
import com.fumi.voice.util.findActivity

/**
 * 设置页。
 *
 * 目前承载三类内容：
 * - **语言**：应用内切换中/英，立即生效（写 Resources 后重建 Activity）；
 * - **通用**：检查更新；
 * - **关于**：开发者信息、下载地址、第三方组件与音色库许可提示。
 *
 * 之所以自成一层而不是塞进某个标签页：这些是「应用级」配置，
 * 挂在任何一个具体页面下都会让用户先记住「设置藏在云同步页里」这件事。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    updateChecking: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val language = remember { AppLanguage(context) }
    var selected by remember { mutableStateOf(language.current()) }
    var versionText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val name = AppUpdater.currentVersionName(context)
        val code = AppUpdater.currentVersionCode(context)
        versionText = "$name ($code)"
    }

    fun openUrl(url: String) {
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!opened) {
            Toast.makeText(context, context.getString(R.string.settings_open_failed), Toast.LENGTH_SHORT).show()
        }
    }

    /** 切语言：存偏好 → 写进 Resources → 重建 Activity 让全应用立即换过来。 */
    fun pickLanguage(tag: String) {
        if (tag == selected) return
        selected = tag
        language.save(tag)
        if (AppLanguage.apply(context, tag)) {
            context.findActivity()?.recreate()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Charcoal)) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = TextPrimary,
                )
            }
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {

            // ---------------- 语言 ----------------
            SectionTitle(stringResource(R.string.settings_section_language))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard),
            ) {
                AppLanguage.options.forEach { (tag, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pickLanguage(tag) }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == tag,
                            onClick = { pickLanguage(tag) },
                            colors = RadioButtonDefaults.colors(selectedColor = Indigo),
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }
            }

            // ---------------- 通用 ----------------
            SectionTitle(stringResource(R.string.settings_section_general))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.SystemUpdateAlt,
                        contentDescription = null,
                        tint = Indigo,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_check_update),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                        Text(
                            stringResource(R.string.settings_version) + " " + versionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    TextButton(onClick = onCheckUpdate, enabled = !updateChecking) {
                        Text(
                            if (updateChecking) stringResource(R.string.state_checking)
                            else stringResource(R.string.action_check),
                            color = Indigo,
                        )
                    }
                }
            }

            // ---------------- 关于 ----------------
            SectionTitle(stringResource(R.string.settings_section_about))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard),
            ) {
                InfoRow(label = AppInfo.DISPLAY_NAME, value = versionText)
                HairLine()
                InfoRow(
                    label = stringResource(R.string.settings_developer),
                    value = AppInfo.DEVELOPER,
                    link = true,
                    onClick = { openUrl(AppInfo.DEVELOPER_URL) },
                )
                HairLine()
                InfoRow(
                    label = stringResource(R.string.settings_project),
                    value = AppInfo.REPO_URL.removePrefix("https://"),
                    link = true,
                    onClick = { openUrl(AppInfo.REPO_URL) },
                )
                HairLine()
                InfoRow(
                    label = stringResource(R.string.settings_download),
                    value = AppInfo.RELEASES_URL.removePrefix("https://"),
                    link = true,
                    onClick = { openUrl(AppInfo.RELEASES_URL) },
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.settings_license_title),
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_license_body),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = Indigo,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun HairLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(Divider)
    )
}

/** 左标签右取值；[link] 为真时右侧带一个「在外部打开」的小图标。 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    link: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (onClick != null) Indigo else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (link) {
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                tint = Indigo,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
