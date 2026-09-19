package com.fumi.voice.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fumi.voice.ui.theme.CharcoalCard
import com.fumi.voice.ui.theme.CharcoalRaised
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.Motion
import com.fumi.voice.ui.theme.TextOnPrimarySoft
import com.fumi.voice.ui.theme.TextPrimary
import com.fumi.voice.ui.theme.TextSecondary
import androidx.compose.ui.res.stringResource
import com.fumi.voice.R

/** 区块小标题：全大写感的细体，用来分隔列表分组。 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
    }
}

/**
 * 通用列表行。
 *
 * 左侧留出 56dp 的方形图标位（沿用 MyPlayer 列表的图文结构），
 * 主副标题两行，右侧可选尾部内容。
 */
@Composable
fun AppListRow(
    title: String,
    subtitle: String?,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    leadingTint: Color = MaterialTheme.colorScheme.primary,
    leadingIconText: String? = null,
    highlighted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (highlighted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (leadingIconText != null) {
                Text(
                    leadingIconText,
                    color = if (highlighted) Color.White else leadingTint,
                    style = MaterialTheme.typography.labelSmall,
                )
            } else {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = if (highlighted) Color.White else leadingTint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 空状态：图标 + 说明 + 可选主操作按钮。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** 圆角卡片容器。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CharcoalCard),
    ) { content() }
}

/**
 * 分段切换控件。
 *
 * 只有 2-4 个互斥分区时，它比 Material 的 Tab 更紧凑，
 * 也不会额外占用一整条 48dp 的纵向空间。
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CharcoalRaised)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            // 选中态的底色和文字色一起渐变。
            // 只渐变底色的话文字会"闪"过去，两个一起动才像一个整体在切换。
            val background by animateColorAsState(
                targetValue = if (active) Color(0xFF6C8CFF) else Color.Transparent,
                animationSpec = Motion.spec(Motion.Quick),
                label = "segmentBackground",
            )
            val contentColor by animateColorAsState(
                targetValue = if (active) Color.White else TextSecondary,
                animationSpec = Motion.spec(Motion.Quick),
                label = "segmentContent",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}

/** 小块标签，用于「内置」「已下载」等状态标记。 */
@Composable
fun PillTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** 一行细分割线。 */
@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(com.fumi.voice.ui.theme.Divider),
    )
}

/**
 * 页面顶部的提示条。
 *
 * 用 AnimatedVisibility 控制展开/收起，而不是简单地 `if` 挂载：
 * 之前提示条是"啪"地占住一行、又"啪"地抽走，底下的内容跟着跳一下，很硬。
 * 现在占位高度平滑变化，文字同步淡入淡出。
 *
 * 退出动画期间 [message] 已经是 null，如果直接渲染它会出现
 * "容器还在收、字已经没了"的空壳，所以要留住最后一次的文字。
 */
@Composable
fun MessageBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastMessage by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (message != null) lastMessage = message
    }

    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically(animationSpec = Motion.spec(Motion.Standard)) +
            fadeIn(animationSpec = Motion.spec(Motion.Quick)),
        exit = shrinkVertically(animationSpec = Motion.spec(Motion.Quick)) +
            fadeOut(animationSpec = Motion.spec(Motion.Instant)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(Indigo.copy(alpha = 0.18f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                lastMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.common_got_it),
                style = MaterialTheme.typography.labelMedium,
                color = TextOnPrimarySoft,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(onClick = onDismiss),
            )
        }
    }
}
