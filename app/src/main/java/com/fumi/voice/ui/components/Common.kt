package com.fumi.voice.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fumi.voice.R
import com.fumi.voice.ui.theme.CharcoalCard
import com.fumi.voice.ui.theme.CharcoalRaised
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.Motion
import com.fumi.voice.ui.theme.Radius
import com.fumi.voice.ui.theme.Spacing
import com.fumi.voice.ui.theme.TextOnPrimarySoft
import com.fumi.voice.ui.theme.TextPrimary
import com.fumi.voice.ui.theme.TextSecondary
import kotlinx.coroutines.delay

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
                .clip(RoundedCornerShape(Radius.Md))
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
            .clip(RoundedCornerShape(Radius.Lg))
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
            .clip(RoundedCornerShape(Radius.Md))
            .background(CharcoalRaised)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            // 选中态的底色和文字色一起渐变。
            // 只渐变底色的话文字会"闪"过去，两个一起动才像一个整体在切换。
            //
            // 底色用主题 primary：原先这里单独写死了一个更亮的蓝（#6C8CFF），
            // 导致「选中态」在分段控件里是一套蓝、在其余地方是另一套蓝，肉眼可见地不统一。
            val background by animateColorAsState(
                targetValue = if (active) Indigo else Color.Transparent,
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
                    .clip(RoundedCornerShape(Radius.Sm))
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
            .clip(RoundedCornerShape(Radius.Xs))
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

/*
 * ============================ 交互反馈 ============================
 */

/**
 * 会「按下去」的可点区域。
 *
 * 全应用的列表行、按钮原本只有 Material 的涟漪，按下去看不出「被按住」；
 * 唯一的缩放反馈在底部导航栏（FuMiVoiceApp.kt 里那处 scale），属于个案。
 * 这里把缩放收成公共能力：按下缩到 [pressedScale]，松开弹回。
 *
 * 时长取 [Motion.Instant]（120ms）：按压反馈必须比手指的预期更快，
 * 慢了会觉得界面「软」。返回值是 Modifier，用法 `.appPressable { ... }`。
 * 注意它接管了 indication，不要再叠加 clickable。
 */
@Composable
fun Modifier.appPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.spec(Motion.Instant),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}

/**
 * 列表项入场：淡入 + 轻微上移，按 [index] 依次错开。
 *
 * 原先列表只有「重排」动画（animateItemPlacement），首次加载或切分区时
 * 整屏内容是一帧内「啪」地出现的。补上入场后，切分区的手感从「换了一屏」
 * 变成「内容长出来」。
 *
 * 两条约束：
 * 1. 错开封顶 8 档 —— 长列表里第 40 项要等 1 秒才出现会很怪；
 * 2. 每个 item 只播一次 —— 状态记在 remember 里，滚动复用时不重播。
 */
@Composable
fun Modifier.listEntrance(index: Int = 0, animated: Boolean = true): Modifier {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(animated) {
        if (animated) delay((index.coerceIn(0, 8) * 22).toLong())
        appeared = true
    }
    val revealed = appeared || !animated
    val itemAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = Motion.spec(Motion.Standard),
        label = "entranceAlpha",
    )
    val itemOffset by animateFloatAsState(
        targetValue = if (revealed) 0f else 14f,
        animationSpec = Motion.spec(Motion.Standard),
        label = "entranceOffset",
    )
    return graphicsLayer {
        alpha = itemAlpha
        translationY = itemOffset
    }
}

/*
 * ============================ 页面转场 ============================
 */

/**
 * 整块页面的入场：淡入 + 水平位移。
 *
 * 为什么需要它：本应用没有 NavHost，子页（歌单详情、艺术家详情、设置）都是
 * 「条件渲染 + 提前 return」。这类写法的代价是进出**完全没有过渡**——
 * 返回时上一页会「啪」地跳回来，观感很硬。
 *
 * 但正因为是条件渲染，目标页每次进入都是全新建组合，所以只要给页面根节点
 * 挂一个入场动画，就能同时拿到「进入」和「返回」两个方向的过渡：
 * 子页用 [fromRight] = true（从右推入），被它盖住的列表页用 false（从左侧微微回位），
 * 合起来就是一次「推入 / 退回」的页面切换。
 *
 * 位移只给 28dp：整屏宽度的滑动会和加载中内容的出现叠在一起，显得拖沓。
 */
@Composable
fun Modifier.pageEntrance(fromRight: Boolean = true, animated: Boolean = true): Modifier {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(animated) {
        if (animated) delay(16L)
        appeared = true
    }
    val revealed = appeared || !animated
    val pageAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = Motion.spec(Motion.Standard),
        label = "pageAlpha",
    )
    val pageOffset by animateFloatAsState(
        targetValue = if (revealed) 0f else if (fromRight) 28f else -18f,
        animationSpec = Motion.spec(Motion.Standard, Motion.EmphasizedEasing),
        label = "pageOffset",
    )
    return graphicsLayer {
        alpha = pageAlpha
        translationX = pageOffset
    }
}

/*
 * ============================ 页面状态 ============================
 */

/**
 * 统一的加载态。
 *
 * 之前「正在加载」各页各写：曲库是居中转圈、云同步只给一行文字、
 * 音色库列表干脆没有加载态（点进去一片空白，像没反应）。
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.Screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(28.dp),
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.Md))
            Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

/**
 * 统一的错误态：图标 + 说明 + 可选重试。
 *
 * 与 [EmptyState] 分开是有意的：空态是「本来就没有东西」，
 * 错误态是「本该有东西但没拿到」。后者必须给动作，否则用户只能来回切页面试探。
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retry: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.Xxl, vertical = Spacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(Spacing.Md))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        if (retry != null) {
            Spacer(Modifier.height(Spacing.Lg))
            retry()
        }
    }
}
