package com.fumi.voice.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * 全应用统一的动效规范。
 *
 * 动手之前各个页面的动画参数都是就地写死的，有的 300ms、有的 500ms，
 * 缓动曲线也各不相同，观感上「不像同一个应用」。这里把时长和曲线集中到一处，
 * 任何地方要加动效都从这里取，改也只改一处。
 *
 * 取向是「偏短、偏干脆」：音乐类应用里动画只是配角，
 * 慢吞吞的过渡会让人等得烦躁，所以最快 120ms、最慢不超过 380ms。
 */
object Motion {

    /** 点按反馈、图标切换这类「按下就要看到回应」的动作。 */
    const val Instant = 120

    /** 默认时长：绝大多数状态切换用它。 */
    const val Quick = 180

    /** 需要让人看清「从哪来、到哪去」的动作，例如分区切换。 */
    const val Standard = 280

    /** 展开 / 收起整块内容。 */
    const val Emphasized = 380

    /**
     * 标准缓动：起步快、收尾慢。
     *
     * 对应「手指甩出去之后自然减速停下」的直觉，
     * 用在跟随手指方向上比较贴合。
     */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * 强调缓动：进出都更利落。
     *
     * 用在有明确方向的分区切换上，比标准曲线更「跟得上手」。
     */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0.1f, 1f)

    /**
     * 补间动画。
     *
     * 名字没叫 tween 是为了不遮蔽 `androidx.compose.animation.core.tween`，
     * 否则对象内部再调用它会变成无限递归。
     */
    fun <T> spec(
        durationMillis: Int = Quick,
        easing: Easing = StandardEasing,
        delayMillis: Int = 0,
    ): TweenSpec<T> = tween(
        durationMillis = durationMillis,
        delayMillis = delayMillis,
        easing = easing,
    )

    /**
     * 无回弹弹簧：落点干脆，不会「弹过头」。
     *
     * 用于位移、尺寸这类需要精确对齐的属性。
     */
    fun <T> spring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * 给列表项位移用的弹簧。
     *
     * 刚度比 [spring] 高一点，插入/移除时重新排布更快，
     * 不会让用户觉得列表「黏」在原位。
     */
    fun itemPlacement(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /**
     * 分区切换的横向滑动 + 淡入淡出。
     *
     * [forward] 表示「切到右边那一区」，进出场方向据此反过来。
     * 只滑 `fullWidth / 6`：位移太大反而像两页在赛跑，
     * 小位移配合淡入更接近「内容在原位换了」的感觉。
     */
    fun horizontalSwitch(forward: Boolean): ContentTransform {
        val enter = slideInHorizontally(
            animationSpec = spec(Standard, EmphasizedEasing),
            initialOffsetX = { full -> if (forward) full / 6 else -full / 6 },
        ) + fadeIn(animationSpec = spec(Quick))

        val exit = slideOutHorizontally(
            animationSpec = spec(Standard, EmphasizedEasing),
            targetOffsetX = { full -> if (forward) -full / 6 else full / 6 },
        ) + fadeOut(animationSpec = spec(Instant))

        return enter togetherWith exit
    }
}
