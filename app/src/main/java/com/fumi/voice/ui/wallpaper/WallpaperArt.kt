package com.fumi.voice.ui.wallpaper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.fumi.voice.ui.theme.Charcoal
import com.fumi.voice.ui.theme.LocalAppearance
import com.fumi.voice.ui.theme.WallpaperKind
import kotlin.math.sin
import kotlin.random.Random

/*
 * ============================================================================
 * 内置壁纸：全部由 Canvas 代码绘制
 * ============================================================================
 *
 * 一张位图壁纸在 1080p 上约 300KB～1.5MB，六张就是几 MB 包体；
 * 而且位图在不同宽高比上要么被裁、要么被拉。这里改成程序化绘制：
 *  - 包体零增长（只有代码）；
 *  - 任意宽高比都铺满，不会露出黑边；
 *  - 颜色全部取自现有深靛 + 数据色，和电脑端的舞台底是同一套色。
 *
 * 性能约定：
 *  - 星点/颗粒的坐标在 remember 里按固定 seed 生成一次，不逐帧随机；
 *  - 漂移动画限到 20fps（见 [wallpaperTime]），静止时完全不驱动帧；
 *  - 只画渐变、圆、线、路径，不用 Blur（Compose 的 Modifier.blur 在 API<31 上是空实现，
 *    指望它做柔化会在低版本设备上直接变成硬边）。
 */

/** 一颗星：坐标与半径都归一化到 0..1，画的时候再乘画布尺寸。 */
private class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val phase: Float,
)

private fun buildStars(kind: WallpaperKind, count: Int): List<Star> {
    val rnd = Random(kind.ordinal * 6151 + 97)
    return List(count) {
        Star(
            x = rnd.nextFloat(),
            y = rnd.nextFloat(),
            radius = 0.0008f + rnd.nextFloat() * 0.0022f,
            alpha = 0.25f + rnd.nextFloat() * 0.75f,
            phase = rnd.nextFloat(),
        )
    }
}

/**
 * 壁纸画布。[animated] 打开时以 20fps 缓慢漂移。
 *
 * 20fps 是刻意的：壁纸只是氛围，60fps 重绘一张全屏渐变在低端机上会明显抢走
 * 瀑布/列表的帧预算，而 20fps 的极光和海流肉眼已经连续。
 */
@Composable
fun WallpaperArt(
    kind: WallpaperKind,
    modifier: Modifier = Modifier,
    animated: Boolean = false,
) {
    val stars = remember(kind) {
        when (kind) {
            WallpaperKind.STARFIELD -> buildStars(kind, 220)
            WallpaperKind.NEBULA -> buildStars(kind, 150)
            WallpaperKind.AURORA -> buildStars(kind, 90)
            WallpaperKind.DEEP_SEA -> buildStars(kind, 110)
            WallpaperKind.DUSK -> buildStars(kind, 40)
            else -> emptyList()
        }
    }
    val time = wallpaperTime(animated)
    Canvas(modifier) { drawWallpaper(kind, time, stars) }
}

/** 壁纸时间轴（秒）。关闭动效时恒为 0，不产生任何帧回调。 */
@Composable
private fun wallpaperTime(animated: Boolean): Float {
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        val start = System.nanoTime()
        var lastFrame = 0L
        while (true) {
            withFrameNanos { now ->
                // 限帧到 20fps：两次刷新之间不足 50ms 就不更新状态
                if (now - lastFrame >= 50_000_000L) {
                    lastFrame = now
                    seconds = (now - start) / 1_000_000_000f
                }
            }
        }
    }
    return seconds
}

/**
 * 整屏背景：壁纸 + 舞台 + 压暗 + 底部渐隐。
 *
 * 这一层是**唯一**知道"壁纸长什么样"的地方。外壳只需要把自己的根节点改成透明，
 * 材质与壁纸就同时作用于所有页面，不必逐页改。
 */
@Composable
fun AppWallpaperBackdrop(modifier: Modifier = Modifier) {
    val appearance = LocalAppearance.current
    Box(modifier = modifier.fillMaxSize().background(Charcoal)) {
        if (appearance.wallpaper != WallpaperKind.NONE) {
            WallpaperArt(
                kind = appearance.wallpaper,
                modifier = Modifier.fillMaxSize(),
                animated = appearance.animateWallpaper,
            )
            // 舞台：深靛压在壁纸上，材质越"实"透得越少
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Charcoal.copy(alpha = appearance.material.stageAlpha)),
            )
            // 用户可调的压暗
            val dim = appearance.dim.coerceIn(0f, 1f) * 0.6f
            if (dim > 0.001f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            }
            // 底部渐隐：底部导航与播放控制压在最亮的那一段上时，文字才不会飘
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Charcoal.copy(alpha = 0.42f)),
                                startY = size.height * 0.45f,
                                endY = size.height,
                            ),
                        )
                    },
            )
        }
    }
}

// =====================================================================
// 绘制
// =====================================================================

private fun DrawScope.drawWallpaper(kind: WallpaperKind, t: Float, stars: List<Star>) {
    when (kind) {
        WallpaperKind.NONE -> drawRect(Charcoal)
        WallpaperKind.AURORA -> drawAurora(t, stars)
        WallpaperKind.STARFIELD -> drawStarfield(t, stars)
        WallpaperKind.DEEP_SEA -> drawDeepSea(t, stars)
        WallpaperKind.NEBULA -> drawNebula(t, stars)
        WallpaperKind.GRID -> drawGrid(t, stars)
        WallpaperKind.DUSK -> drawDusk(t, stars)
    }
}

private fun DrawScope.drawStars(stars: List<Star>, t: Float, maxAlpha: Float) {
    val minSide = minOf(size.width, size.height)
    stars.forEach { s ->
        // 每颗星有自己的相位与频率，整体不会"一起呼吸"
        val twinkle = 0.7f + 0.3f * sin(t * (0.5f + s.phase) + s.phase * 6.283f)
        val alpha = (s.alpha * twinkle * maxAlpha).coerceIn(0f, 1f)
        if (alpha <= 0.01f) return@forEach
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = s.radius * minSide,
            center = Offset(s.x * size.width, s.y * size.height),
        )
    }
}

/** 极光：冷色竖直帘幕 + 地平线辉光 + 稀疏星点。 */
private fun DrawScope.drawAurora(t: Float, stars: List<Star>) {
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color(0xFF05060E), Color(0xFF0A1230), Color(0xFF04050C)),
            startY = 0f,
            endY = size.height,
        ),
    )
    drawStars(stars, t, maxAlpha = 0.5f)

    // 极光帘：每条自带 sway，相位不同才不会同起同落
    val curtains = listOf(
        Triple(0.16f, 0.11f, listOf(Color(0x0000E5A0), Color(0x8023D9A5), Color(0x0013A08C))),
        Triple(0.40f, 0.14f, listOf(Color(0x0000D0FF), Color(0x702F9BFF), Color(0x000066CC))),
        Triple(0.63f, 0.09f, listOf(Color(0x0000E5A0), Color(0x665AE0C8), Color(0x0012A08C))),
        Triple(0.84f, 0.13f, listOf(Color(0x008A5BFF), Color(0x5C7C6CFF), Color(0x002A2A7A))),
    )
    curtains.forEachIndexed { i, (cx, wRatio, colors) ->
        val sway = sin(t * 0.16f + i * 1.7f) * size.width * 0.04f +
            sin(t * 0.06f + i * 0.9f) * size.width * 0.018f
        val width = size.width * wRatio * (1f + 0.06f * sin(t * 0.09f + i))
        val left = size.width * cx + sway - width / 2f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = colors,
                startY = -size.height * 0.06f,
                endY = size.height * 0.74f,
            ),
            topLeft = Offset(left, -size.height * 0.08f),
            size = Size(width, size.height * 0.8f),
            cornerRadius = CornerRadius(width * 0.5f, width * 0.38f),
        )
    }

    // 地平线冷光：让画面"有地面"，而不是纯黑的上半截
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x5539C6B4), Color(0x1139C6B4), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.98f),
            radius = size.width * 0.75f,
        ),
        radius = size.width * 0.75f,
        center = Offset(size.width * 0.5f, size.height * 0.98f),
    )
    // 顶部幕光
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color(0x221E3A6E), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.42f,
        ),
    )
}

/** 星空：银河带 + 三团星云 + 两百多颗会眨的星。 */
private fun DrawScope.drawStarfield(t: Float, stars: List<Star>) {
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color(0xFF02030A), Color(0xFF070C1E), Color(0xFF02030A)),
            startY = 0f,
            endY = size.height,
        ),
    )
    // 银河：一条斜向的软带，用宽渐变近似
    drawRect(
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color(0x22A9C4FF), Color(0x1A7C6CFF), Color.Transparent),
            start = Offset(0f, size.height * 0.15f),
            end = Offset(size.width, size.height * 0.85f),
        ),
        alpha = 0.9f,
    )
    val clouds = listOf(
        Triple(0.24f, 0.34f, Color(0x333D74E8)),
        Triple(0.68f, 0.26f, Color(0x2AEA5EC1)),
        Triple(0.52f, 0.68f, Color(0x2239C6B4)),
    )
    clouds.forEachIndexed { i, (cx, cy, color) ->
        val center = Offset(size.width * cx, size.height * cy)
        val radius = size.width * (0.4f + 0.06f * i)
        drawCircle(
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
            radius = radius,
            center = center,
        )
    }
    drawStars(stars, t, maxAlpha = 0.95f)
    // 四周压暗，中心留出一点亮
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0x99000000)),
            center = Offset(size.width * 0.5f, size.height * 0.45f),
            radius = maxOf(size.width, size.height) * 0.85f,
        ),
    )
}

/** 深海：光柱 + 焦散细线 + 浮游微粒。 */
private fun DrawScope.drawDeepSea(t: Float, stars: List<Star>) {
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color(0xFF05192B), Color(0xFF03101E), Color(0xFF01060C)),
            startY = 0f,
            endY = size.height,
        ),
    )
    // 斜射进来的光柱：旋转坐标系后画竖直渐变，比多边形简单且边缘自然
    for (i in 0 until 4) {
        val x = size.width * (0.14f + 0.23f * i)
        val width = size.width * (0.07f + 0.03f * (i % 2))
        rotate(degrees = 10f + i * 3.5f, pivot = Offset(x, 0f)) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x2A7FD8FF), Color(0x0A2F9BFF), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.9f,
                ),
                topLeft = Offset(x - width / 2f, 0f),
                size = Size(width, size.height * 0.9f),
            )
        }
    }
    // 焦散：三条随时间缓慢平移的正弦细线
    for (i in 0 until 3) {
        val path = Path()
        val baseY = size.height * (0.58f + i * 0.1f)
        var x = 0f
        path.moveTo(0f, baseY)
        while (x <= size.width) {
            val phase = (x / size.width) * 6.283f * 1.8f + t * 0.35f + i * 1.1f
            path.lineTo(x, baseY + sin(phase) * size.height * 0.018f)
            x += size.width / 28f
        }
        drawPath(path, color = Color(0x2E7FD8FF), style = Stroke(width = 1.2f))
    }
    drawStars(stars, t, maxAlpha = 0.32f)
    // 越往下越暗
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color(0xAA000000)),
            startY = size.height * 0.4f,
            endY = size.height,
        ),
    )
}

/** 星云：五团彩色气体 + 尘埃 + 深空压暗。 */
private fun DrawScope.drawNebula(t: Float, stars: List<Star>) {
    drawRect(Color(0xFF04030C))
    val blobs = listOf(
        Triple(0.26f, 0.30f, 0x66EA5EC1),
        Triple(0.74f, 0.24f, 0x553D74E8),
        Triple(0.56f, 0.60f, 0x4D7C6CFF),
        Triple(0.20f, 0.74f, 0x4439C6B4),
        Triple(0.84f, 0.72f, 0x3DEA5EC1),
    )
    blobs.forEachIndexed { i, blob ->
        val cx = blob.first
        val cy = blob.second
        val color = blob.third
        val drift = sin(t * 0.045f + i * 1.3f) * size.width * 0.02f
        val center = Offset(size.width * cx + drift, size.height * cy)
        val radius = size.width * (0.32f + 0.05f * i)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(color), Color.Transparent), center = center, radius = radius),
            radius = radius,
            center = center,
        )
    }
    drawStars(stars, t, maxAlpha = 0.7f)
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0xCC000000)),
            center = Offset(size.width * 0.5f, size.height * 0.46f),
            radius = maxOf(size.width, size.height) * 0.82f,
        ),
    )
}

/** 渐变网格：透视网格 + 地平线辉光，最接近电脑端"技术界面"的气质。 */
private fun DrawScope.drawGrid(t: Float, stars: List<Star>) {
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color(0xFF05070F), Color(0xFF0B1330), Color(0xFF05070F)),
            startY = 0f,
            endY = size.height,
        ),
    )
    drawStars(stars, t, maxAlpha = 0.22f)

    val horizon = size.height * 0.60f
    val vanishX = size.width * 0.5f

    // 竖线：从灭点向下散开（近大远小）
    for (i in -10..10) {
        val x = vanishX + i * size.width * 0.105f
        drawLine(
            color = Color(0x265AC8FA),
            start = Offset(x, horizon),
            end = Offset(x + (x - vanishX) * 1.55f, size.height),
            strokeWidth = 1f,
        )
    }
    // 横线：间距按 1.28 递增，越近越疏
    var y = horizon
    var step = size.height * 0.012f
    while (y < size.height) {
        drawLine(Color(0x2E5AC8FA), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
        step *= 1.28f
    }
    // 地平线辉光
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0x003D74E8), Color(0x553D74E8), Color(0x0005070F)),
            startY = horizon - size.height * 0.14f,
            endY = horizon + size.height * 0.05f,
        ),
        topLeft = Offset(0f, horizon - size.height * 0.14f),
        size = Size(size.width, size.height * 0.19f),
    )
    // 网格屏下方的雾
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color(0x222F63D4), Color.Transparent),
            startY = horizon,
            endY = size.height,
        ),
    )
}

/** 暮色：紫到琥珀的竖向渐变 + 落日 + 云带，给"暖色"留一个选项。 */
private fun DrawScope.drawDusk(t: Float, stars: List<Star>) {
    drawRect(
        Brush.verticalGradient(
            colors = listOf(Color(0xFF0A0B1C), Color(0xFF2A1B4A), Color(0xFF6C2A4A), Color(0xFFB4553A)),
            startY = 0f,
            endY = size.height,
        ),
    )
    drawStars(stars, t, maxAlpha = 0.14f)

    val sunCenter = Offset(size.width * 0.62f, size.height * 0.84f)
    val sunRadius = size.width * 0.55f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xBBFFB36B), Color(0x44FF7A45), Color.Transparent),
            center = sunCenter,
            radius = sunRadius,
        ),
        radius = sunRadius,
        center = sunCenter,
    )
    drawCircle(
        color = Color(0xFFFFD9A0),
        radius = size.height * 0.042f,
        center = sunCenter,
    )
    // 云带：几条横向软条，越靠下越亮
    for (i in 0 until 4) {
        val y = size.height * (0.56f + 0.07f * i)
        val height = size.height * (0.018f + 0.005f * i)
        drawRoundRect(
            color = Color(0x22FFD9C0),
            topLeft = Offset(size.width * (0.06f + 0.05f * i), y),
            size = Size(size.width * (0.62f + 0.08f * i), height),
            cornerRadius = CornerRadius(height / 2f, height / 2f),
        )
    }
}
