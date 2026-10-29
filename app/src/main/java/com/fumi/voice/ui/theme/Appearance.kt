package com.fumi.voice.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.fumi.voice.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * ============================================================================
 * 外观（材质 / 壁纸 / 背景压暗）
 * ============================================================================
 *
 * 为什么单独一层：电脑端 FuFumidi 把「外观」做成一个抽屉（主题 / 壁纸 / 显示），
 * 手机端原来只有一套写死的深靛，两端放在一起就不是同一个产品的两个端。
 * 这里把"表面材质"和"背景"抽成可保存的偏好，界面只读 [LocalAppearance]，
 * 不再各自写死底色 —— 与电脑端 frontend/src/styles/tokens.css 的思路一致：
 * **字面色值只允许出现在 theme 包里**，其余一律引用 token。
 *
 * 材质说明（Compose 没有 CSS 的 backdrop-filter，无法做真正的背景模糊）：
 *  - 玻璃 GLASS   ：低不透明度 + 顶部斜向高光 + 1px 亮边，模拟"隔着玻璃看壁纸"
 *  - 亚克力 ACRYLIC：更高的不透明度 + 细颗粒噪点，模拟磨砂亚克力
 *  - 云母 MICA    ：低对比、几乎不透明、只有一道发丝线，安静、适合长时间阅读
 *  - 天鹅绒 VELVET：哑光深色 + 极轻的顶部内发光，像布料吸收光线
 * 这些差异全部落在 [SurfacePalette] 里，卡片/面板只调用 Modifier.appSurface()。
 */

/** 界面材质。stageAlpha 是"舞台"（整屏底色）压在壁纸上的不透明度。 */
enum class SurfaceMaterial(@StringRes val labelRes: Int, @StringRes val descRes: Int, val stageAlpha: Float) {
    SOLID(R.string.material_solid, R.string.material_solid_desc, 1.0f),
    GLASS(R.string.material_glass, R.string.material_glass_desc, 0.34f),
    ACRYLIC(R.string.material_acrylic, R.string.material_acrylic_desc, 0.48f),
    MICA(R.string.material_mica, R.string.material_mica_desc, 0.62f),
    FROSTED(R.string.material_frosted, R.string.material_frosted_desc, 0.70f),
    VELVET(R.string.material_velvet, R.string.material_velvet_desc, 0.74f),
}

/** 一种材质在卡片/面板上的具体取值。 */
data class SurfacePalette(
    /** 卡片底色（带 alpha，透出壁纸与舞台）。 */
    val surface: Color,
    /** 1px 发丝边。 */
    val border: Color,
    /** 是否画顶部斜向高光（玻璃）。 */
    val sheen: Boolean,
    /** 细颗粒强度，0 表示不画（亚克力 / 天鹅绒）。 */
    val grain: Float,
    /** 顶部内发光，给面板一点"受光"的方向感。 */
    val innerGlow: Color,
)

/** 材质 → 取值。这是手机端允许出现字面色值的两处之一（另一处是 Theme.kt）。 */
fun SurfaceMaterial.palette(): SurfacePalette = when (this) {
    SurfaceMaterial.SOLID -> SurfacePalette(
        surface = Charcoal,
        border = Divider,
        sheen = false,
        grain = 0f,
        innerGlow = Color.Transparent,
    )
    SurfaceMaterial.GLASS -> SurfacePalette(
        surface = Color(0x75171C2E),      // 46% 抬起面
        border = Color(0x26FFFFFF),       // 15% 白边，玻璃靠边线立住
        sheen = true,
        grain = 0f,
        innerGlow = Color(0x1A6C8CFF),    // 顶部一层极淡品牌色
    )
    SurfaceMaterial.ACRYLIC -> SurfacePalette(
        surface = Color(0xA8171C2E),      // 66%
        border = Color(0x1FFFFFFF),
        sheen = false,
        grain = 0.055f,
        innerGlow = Color(0x146C8CFF),
    )
    SurfaceMaterial.MICA -> SurfacePalette(
        surface = Color(0xD9111524),      // 85%
        border = Color(0x1AFFFFFF),
        sheen = false,
        grain = 0f,
        innerGlow = Color(0x0FFFFFFF),
    )
    SurfaceMaterial.FROSTED -> SurfacePalette(
        surface = Color(0xC81A2135),
        border = Color(0x1AFFFFFF),
        sheen = false,
        grain = 0.018f,
        innerGlow = Color(0x10DDE7FF),
    )
    SurfaceMaterial.VELVET -> SurfacePalette(
        surface = Color(0xE6121018),      // 90%，偏暖的深色
        border = Color(0x14FFFFFF),
        sheen = false,
        grain = 0.04f,
        innerGlow = Color(0x14FF9F6B),    // 暖色内发光，布料感
    )
}

/** 内置壁纸。全部由 Canvas 代码绘制，不打包任何位图（见 ui/wallpaper/WallpaperArt.kt）。 */
enum class WallpaperKind(@StringRes val labelRes: Int) {
    NONE(R.string.wallpaper_none),
    AURORA(R.string.wallpaper_aurora),
    STARFIELD(R.string.wallpaper_starfield),
    DEEP_SEA(R.string.wallpaper_deep_sea),
    NEBULA(R.string.wallpaper_nebula),
    GRID(R.string.wallpaper_grid),
    DUSK(R.string.wallpaper_dusk),
}

/**
 * 一份外观偏好。
 *
 * 默认值取"玻璃 + 极光 + 中等压暗"：既保留原有深靛舞台的观感
 * （压暗后壁纸只是背景里的一层氛围），又让两端第一眼是同一个产品。
 * 不喜欢的话在设置里选「无」即可回到 1.0.10 的纯色底。
 */
data class Appearance(
    val material: SurfaceMaterial = SurfaceMaterial.GLASS,
    val wallpaper: WallpaperKind = WallpaperKind.AURORA,
    /** 0..1，壁纸之上再压一层黑，越大越暗、文字越稳。 */
    val dim: Float = 0.30f,
    /** 壁纸是否缓慢漂移（默认关：静止画面已经够用，省电）。 */
    val animateWallpaper: Boolean = false,
) {
    /** 整屏舞台色：壁纸之上的一层深靛。没有壁纸时就是原来那个实色 Charcoal。 */
    val stageColor: Color
        get() = if (wallpaper == WallpaperKind.NONE) Charcoal
        else Charcoal.copy(alpha = material.stageAlpha)
}

/** 全局外观状态；默认值让未初始化时也能安全渲染。 */
val LocalAppearance = compositionLocalOf { Appearance() }

/**
 * 外观偏好的落盘。
 *
 * 用 SharedPreferences 而不是 DataStore：本应用其它偏好（音色库、排序、更新线路）
 * 都是 SharedPreferences，多引一个 DataStore 只会多一套异步初始化的时序问题；
 * 这里的数据量是三四个枚举 + 两个数值，SharedPreferences 完全够。
 */
class AppearanceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Appearance {
        val material = SurfaceMaterial.entries
            .firstOrNull { it.name == prefs.getString(KEY_MATERIAL, "") }
            ?: Appearance().material
        val wallpaper = WallpaperKind.entries
            .firstOrNull { it.name == prefs.getString(KEY_WALLPAPER, "") }
            ?: Appearance().wallpaper
        return Appearance(
            material = material,
            wallpaper = wallpaper,
            dim = prefs.getFloat(KEY_DIM, Appearance().dim).coerceIn(0f, 1f),
            animateWallpaper = prefs.getBoolean(KEY_MOTION, Appearance().animateWallpaper),
        )
    }

    fun save(value: Appearance) {
        prefs.edit()
            .putString(KEY_MATERIAL, value.material.name)
            .putString(KEY_WALLPAPER, value.wallpaper.name)
            .putFloat(KEY_DIM, value.dim.coerceIn(0f, 1f))
            .putBoolean(KEY_MOTION, value.animateWallpaper)
            .apply()
    }

    private companion object {
        const val PREFS = "fumi_appearance"
        const val KEY_MATERIAL = "material"
        const val KEY_WALLPAPER = "wallpaper"
        const val KEY_DIM = "dim"
        const val KEY_MOTION = "animate_wallpaper"
    }
}

/**
 * 进程内的外观总线。
 *
 * 设置页改完必须让外壳立刻跟着变，而外壳与设置页是两棵各自 remember 的子树；
 * 用一个进程级 StateFlow 做单一事实来源，比"返回时重新读一遍 prefs"可靠
 * ——后者在 Activity 重建、分屏、画中画这些路径上很容易读到旧值。
 */
object AppearanceHub {

    private val _state = MutableStateFlow(Appearance())
    val state: StateFlow<Appearance> = _state.asStateFlow()

    private var loaded = false

    /** 只在主线程调用；幂等。 */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        _state.value = AppearanceStore(context).load()
    }

    /** 立即生效并落盘。 */
    fun update(context: Context, next: Appearance) {
        ensureLoaded(context)
        _state.value = next
        AppearanceStore(context).save(next)
    }
}
