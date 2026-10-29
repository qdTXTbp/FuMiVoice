package com.fumi.voice.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * 带材质的表面。
 *
 * 所有卡片 / 面板都走这一个入口：底色、发丝边、高光、颗粒全部来自
 * [SurfaceMaterial.palette]，换材质只改一处。默认从 [LocalAppearance] 取当前材质，
 * 所以调用点不用逐层传参。
 *
 * 绘制顺序很关键：
 *   1. 底色（半透明，透出背后的壁纸）
 *   2. drawWithCache 里在内容之下画内发光 / 斜向高光 / 颗粒（onDrawBehind）
 *   3. 1px 描边
 * 高光必须画在内容**下面**，否则文字上会盖一层白雾。
 */
@Composable
fun Modifier.appSurface(
    material: SurfaceMaterial = LocalAppearance.current.material,
    shape: Shape = RoundedCornerShape(Radius.Lg),
): Modifier {
    val palette = material.palette()
    return this
        .clip(shape)
        .background(palette.surface)
        .drawWithCache {
            // 颗粒的坐标与半径按当前尺寸算好缓存下来，避免每帧重新随机
            val dots = if (palette.grain > 0f) {
                val rnd = Random(material.ordinal * 7919 + 31)
                val count = (size.width * size.height / 6000f).toInt().coerceIn(20, 200)
                List(count) {
                    val x = rnd.nextFloat() * size.width
                    val y = rnd.nextFloat() * size.height
                    val r = 0.6f + rnd.nextFloat() * 1.2f
                    val a = palette.grain * (0.35f + rnd.nextFloat() * 0.9f)
                    Triple(Offset(x, y), r, a)
                }
            } else {
                emptyList()
            }
            val sheen = if (palette.sheen) {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.02f),
                        Color.Transparent,
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width * 0.85f, size.height * 1.15f),
                )
            } else {
                null
            }
            val glow = Brush.verticalGradient(
                colors = listOf(palette.innerGlow, Color.Transparent),
                startY = 0f,
                endY = size.height.coerceAtLeast(1f),
            )
            onDrawBehind {
                drawRect(brush = glow)
                sheen?.let { drawRect(brush = it) }
                dots.forEach { (center, radius, alpha) ->
                    drawCircle(Color.White.copy(alpha = alpha), radius = radius, center = center)
                }
            }
        }
        .border(1.dp, palette.border, shape)
}

/** 顶栏 / 底栏这类"整条"面板的底色：与卡片同一个材质，只是不叠高光边。 */
@Composable
fun appBarColor(material: SurfaceMaterial = LocalAppearance.current.material): Color =
    material.palette().surface.copy(alpha = (material.palette().surface.alpha + 0.22f).coerceAtMost(1f))
