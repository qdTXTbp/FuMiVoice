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
    error = AccentRed,
    onError = Color.White,
)

@Composable
fun FuMiVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content,
    )
}
