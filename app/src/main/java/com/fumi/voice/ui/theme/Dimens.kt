package com.fumi.voice.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 间距阶梯。
 *
 * 之前每个页面都就地写数字：同样是「列表左右留白」，
 * 曲库写 20、音色写 16、云同步写 20，同一屏里出现 2/3/4/6/8/10/12/14/16/20 十种间距，
 * 单看每一处都合理，拼在一起就「不像一个应用」。
 *
 * 这里把它收敛成 6 档。取值依据是原来的多数派：
 * 20 出现最多（列表行、区块留白），所以 [Screen] 就是 20；
 * 行内图文之间的 14 归到 [Md] 的 12，视觉上几乎无差，但少一个 magic number。
 *
 * 用法：能对上就取 token，实在对不上（比如自绘瀑布里的像素级坐标）再写裸值。
 */
object Spacing {

    /** 4dp：图标与其紧邻的小字（桌面 4/8 网格）。 */
    val Xs = 4.dp

    /** 8dp：同一组内的行间距。 */
    val Sm = 8.dp

    /** 12dp：行内图文间距、卡片内边距。 */
    val Md = 12.dp

    /** 16dp：卡片之间、页面左右留白（与电脑端页面内边距同值）。 */
    val Lg = 16.dp
    val Screen = 16.dp

    /** 24dp：大区块之间的呼吸（桌面 --sp-5）。 */
    val Xxl = 24.dp
}

/**
 * 圆角阶梯。
 *
 * 原来同一种元素在不同页面圆角不同：列表行的图标块有 10dp 也有 12dp，
 * 次要按钮这一页 14dp、那一页 20dp。这里定 5 档，并接到 [MaterialTheme.shapes]，
 * 顺带让 Material 组件（弹窗、弹层、菜单）也走同一套圆角。
 */
object Radius {

    /** 6dp：小块标签、徽标（电脑端 --r-sm）。 */
    val Xs = 6.dp

    /** 10dp：分段控件内块、输入框、普通按钮（电脑端 --r-md）。 */
    val Sm = 10.dp
    val Md = 10.dp

    /** 14dp：卡片、弹层顶部（电脑端 --r-lg）。 */
    val Lg = 14.dp
    val Xl = 14.dp
}

/** 接入 Material 主题，让 `MaterialTheme.shapes.*` 与上面这套圆角一致。 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.Xs),
    small = RoundedCornerShape(Radius.Sm),
    medium = RoundedCornerShape(Radius.Md),
    large = RoundedCornerShape(Radius.Lg),
    extraLarge = RoundedCornerShape(Radius.Xl),
)
