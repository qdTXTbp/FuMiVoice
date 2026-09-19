package com.fumi.voice.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * 应用语言偏好。
 *
 * 这里没有引入 AppCompat 的 `setApplicationLocales`（本项目是纯 `ComponentActivity`，
 * 拉一个 appcompat 只为了切语言不划算），改用「把 Locale 写进 Resources 后重建 Activity」
 * 这套老办法：主线程代价只有一个 Activity 重建，切换后立即生效，
 * 且不会替换 `LocalContext`——沉浸模式、Toast 这些需要真实 Activity 的地方不受影响。
 */
class AppLanguage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("fumi_settings", Context.MODE_PRIVATE)

    fun current(): String = prefs.getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM

    fun save(tag: String) {
        prefs.edit().putString(KEY_LANGUAGE, tag).apply()
    }

    companion object {
        /** 跟随系统。 */
        const val SYSTEM = "system"
        const val CHINESE = "zh"
        const val ENGLISH = "en"

        private const val KEY_LANGUAGE = "app_language"

        /** 界面上可选的语言（顺序即展示顺序）。 */
        val options: List<Pair<String, String>> = listOf(
            SYSTEM to "跟随系统 / System",
            CHINESE to "简体中文",
            ENGLISH to "English",
        )

        /**
         * 把语言应用到 Resources。
         *
         * 必须在 `setContent` 之前调用，否则首帧仍是旧语言，会闪一下。
         *
         * @param force 为 true 时即使 `Locale.getDefault()` 已经是目标语言也重写一遍资源。
         *        旋转、深浅色切换这类系统配置变更会把 Activity 的 Resources 重置回系统语言，
         *        而 `Locale.getDefault()` 仍是进程级、不会跟着回退——只看 Locale 判断
         *        「已经一致就不用改」会把这一情形漏掉，转个屏界面就变回系统语言。
         */
        fun apply(context: Context, tag: String, force: Boolean = false): Boolean {
            val target = if (tag == SYSTEM) deviceLocale() else Locale.forLanguageTag(tag)
            val now = Locale.getDefault()
            if (!force && now.language == target.language && now.country == target.country) return false

            Locale.setDefault(target)
            val config = Configuration(context.resources.configuration).apply {
                setLocale(target)
                setLayoutDirection(target)
            }
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return true
        }

        /**
         * 设备自身语言。
         *
         * 取系统资源而不是应用资源的配置：应用资源的配置可能已经被我们改写过了，
         * 用它去还原「跟随系统」会还原到自己，切回去等于没切。
         */
        private fun deviceLocale(): Locale =
            Configuration(Resources.getSystem().configuration).locales[0] ?: Locale.getDefault()
    }
}

/** 从 Compose 的 Context 里找出宿主 Activity；找不到时返回 null（例如预览环境）。 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
