package com.fumi.voice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * 配色与电脑端（FuFumidi + HeroUI）**同一套设计语言**：
 * 深靛舞台底（stage → panel → raised 三级抬升）+ 柔白文字 + 10% 白细线 + 七元素数据色。
 * 取值直接对齐电脑端 frontend/src/styles/tokens.css 的 --t-* / --data-* 语义色，
 * 这样两端截图放在一起是同一个产品；本文件是手机端唯一允许写字面色值的地方。
 */

/** 主色：电脑端「专业工作站」主题的 accent #4F8EF7 */
val Indigo = Color(0xFF4F8EF7)
val IndigoDeep = Color(0xFF3A6FD8)
val IndigoSoft = Color(0xFFA9C4FF)

/** 三级抬升：舞台 → 面板 → 抬起的卡片（与电脑端 --t-stage/--t-panel/--t-raised 一致） */
val Charcoal = Color(0xFF0A0D16)
val CharcoalRaised = Color(0xFF111524)
val CharcoalCard = Color(0xFF171C2E)

/** 细线：电脑端用 10% 白而不是实色灰 —— 深浅底上都成立，也更有「玻璃面板」感 */
val Divider = Color(0x1AFFFFFF)

/** 文字：柔白压深靛（纯白压纯黑会眩光，这是电脑端定下的规矩） */
val TextPrimary = Color(0xFFE8ECF4)
val TextSecondary = Color(0xFFA7B0C0)
val TextOnPrimary = Color(0xFFFFFFFF)
val TextOnPrimarySoft = Color(0xFFD7E3FF)

/** 功能色：与电脑端 --t-ok/--t-warn/--t-err/--t-info 同值 */
val AccentOrange = Color(0xFFFFB340)
val AccentGreen = Color(0xFF3ECF8E)
val AccentRed = Color(0xFFFF6B6B)
val AccentInfo = Color(0xFF5AC8FA)

/** 七元素数据色（电脑端 --data-1..7）—— 曲目色、图表、可视化共用 */
val Data1 = Color(0xFFFF5530)
val Data2 = Color(0xFFFFB340)
val Data3 = Color(0xFFFFE066)
val Data4 = Color(0xFF3ECF8E)
val Data5 = Color(0xFF5AC8FA)
val Data6 = Color(0xFF7C6CFF)
val Data7 = Color(0xFFEA5EC1)

/*
 * 下面三个颜色原先散在页面里写裸值（输入框底色重复 6 处、瀑布底色重复 5 处），
 * 收进主题后改一次即可全局生效。它们不属于 M3 的语义角色，所以保持顶层常量。
 */

/** 输入框、次级可选区的底：比 surfaceVariant 再暗一档（电脑端 --t-sunken #070911），
 *  「可输入」的凹陷感与「只读」的抬起面区分开。 */
val FieldSurface = Color(0xFF070911)

/** 钢琴瀑布的画布底：舞台darkest，音符才能「跳」出来。 */
val WaterfallBackground = Color(0xFF070911)

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
    surfaceContainerHighest = Color(0xFF1D2438),
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
