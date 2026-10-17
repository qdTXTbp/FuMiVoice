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

/**
 * 时间码的三档语义字号。
 *
 * 原来各页面靠 `TimecodeStyle.copy(fontSize = 11.sp / 12.sp / 13.sp)` 就地改字号，
 * 一共 11 处，同一类读数在列表里是 11、在弹层里是 13，来回漂移。
 * 定成三档后调用点只表达「这是小号读数还是大号读数」。
 */
val TimecodeSmall = TimecodeStyle.copy(fontSize = 11.sp, letterSpacing = 0.3.sp)
val TimecodeLarge = TimecodeStyle.copy(fontSize = 13.sp)

/**
 * 字号阶与电脑端一致：6 档（11 / 12 / 13 / 15 / 20 / 28），
 * 行高 标题 1.25、正文 1.55；字重正文封顶 600（700 只许标题与大数字）。
 * 两端用同一套阶，跨端看同一屏内容时字号不会「跳一档」。
 */
val AppTypography = Typography(
    // 品牌字样（拉丁文）：电脑端 --fs-2xl 28px
    displaySmall = TextStyle(
        fontFamily = Boogaloo,
        fontSize = 28.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp,
    ),
    // 区块标题
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    // 卡片标题
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    // 正文：15 强调段 / 13 界面正文 / 12 次要说明
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    // 标签 / 辅助
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)
