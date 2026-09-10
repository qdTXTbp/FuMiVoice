package com.fumi.voice.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fumi.voice.R

/**
 * 字体策略：
 * - 正文/标题一律用系统字体，保证中文字形完整、字重正确。
 * - KodeMono 只用于时间码等纯数字读数：等宽可避免数字跳动导致布局抖动。
 * - Boogaloo 只用于拉丁文品牌字样，不参与中文排版。
 */
val Boogaloo = FontFamily(Font(R.font.boogaloo_regular, FontWeight.Normal))
val KodeMono = FontFamily(Font(R.font.kode_mono_medium, FontWeight.Medium))

/** 时间码 / 数字读数：等宽，避免逐帧跳动 */
val TimecodeStyle = TextStyle(
    fontFamily = KodeMono,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.5.sp,
)

val AppTypography = Typography(
    // 品牌字样（拉丁文）
    displaySmall = TextStyle(
        fontFamily = Boogaloo,
        fontSize = 26.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp,
    ),
    // 页面 / 区块标题（中文）
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    // 正文
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    // 标签 / 辅助
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)
