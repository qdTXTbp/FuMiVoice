package com.fumi.voice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * 配色取自 remix.myplayer 的实测取色（截图逐像素采样），
 * 但把副文字提亮以满足无障碍对比度要求。
 */

/** 主色：MyPlayer 顶栏/Tab/状态栏实测色 #708BEF */
val Indigo = Color(0xFF708BEF)
val IndigoDeep = Color(0xFF5A73D8)
val IndigoSoft = Color(0xFFC6D1F7)

/** 背景：MyPlayer 列表底实测色 #242428 */
val Charcoal = Color(0xFF242428)
val CharcoalRaised = Color(0xFF2D2D32)
val CharcoalCard = Color(0xFF32323A)
val Divider = Color(0xFF3C3C44)

/** 文字 */
val TextPrimary = Color(0xFFFFFFFF)
/** 原版 #6C6A6C 对比度仅 2.9:1，提亮到 5.5:1 */
val TextSecondary = Color(0xFF9A98A0)
val TextOnPrimary = Color(0xFFFFFFFF)
val TextOnPrimarySoft = Color(0xFFE4E9FF)

/** 功能色 */
val AccentOrange = Color(0xFFFF8A4C)
val AccentGreen = Color(0xFF4CC38A)
val AccentRed = Color(0xFFE5484D)

/*
 * 下面三个颜色原先散在页面里写裸值（输入框底色重复 6 处、瀑布底色重复 5 处），
 * 收进主题后改一次即可全局生效。它们不属于 M3 的语义角色，所以保持顶层常量。
 */

/** 输入框、次级可选区的底：比 surfaceVariant 再亮一档，让「可输入」与「只读」区分开。 */
val FieldSurface = Color(0xFF2A2A33)

/** 钢琴瀑布的画布底：比页面背景更暗，音符才能「跳」出来。 */
val WaterfallBackground = Color(0xFF141419)

/** 弹层、相机/文件选择这类需要压暗底层的遮罩。 */
val Scrim = Color(0xCC000000)

/** 瀑布画布上的浮层底（播放控制条、角标），半透明黑让音符仍隐约可见。 */
val GlassOverlay = Color(0x59000000)

private val AppColorScheme = darkColorScheme(
    primary = Indigo,
    onPrimary = TextOnPrimary,
    primaryContainer = IndigoDeep,
    onPrimaryContainer = TextOnPrimary,
    secondary = AccentOrange,
    onSecondary = Color(0xFF2A1708),
    background = Charcoal,
    onBackground = TextPrimary,
    surface = Charcoal,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalRaised,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    // M3 的容器层级默认值偏紫灰，和本应用的暖灰底不是一路。
    // 补齐这几档后，底部弹层（ModalBottomSheet 用 surfaceContainerLow）、
    // 菜单、弹窗这些「我们没显式指定底色」的组件也不会突然冒出一块异色。
    surfaceContainerLowest = Charcoal,
    surfaceContainerLow = CharcoalRaised,
    surfaceContainer = CharcoalCard,
    surfaceContainerHigh = CharcoalCard,
    surfaceContainerHighest = Color(0xFF3A3A42),
    outlineVariant = Divider,
    error = AccentRed,
    onError = Color.White,
)

@Composable
fun FuMiVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
