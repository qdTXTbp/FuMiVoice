package com.fumi.voice.update

import android.content.Context

/**
 * 下载 / 更新走的线路，用户可在设置里切换（自动 / 国内源 / 境外源）。
 *
 * 背景：更新器与音色库下载原先各自写死一份「国内镜像在前、GitHub 兜底」的列表，
 * 用户没法干预。国内时 CNB 直连通常最快且最稳；境外或代理环境反而 GitHub 直连更顺。
 * 现在两处统一走这里，按模式给出**有序**候选，任一链路失败自动换下一条。
 */
enum class SourceMode(val key: String) {
    AUTO("auto"),
    DOMESTIC("domestic"),
    FOREIGN("foreign"),
}

object UpdateSource {

    private const val PREFS = "update_source"
    private const val KEY_MODE = "mode"

    /** CNB 上的镜像仓库（与 GitHub 发布同名同标签），资产地址形如：
     *  `https://cnb.cool/FuFuCloud-mirror/FuMiVoice/-/releases/latest/download/<asset>` */
    private const val CNB_BASE = "https://cnb.cool/FuFuCloud-mirror/FuMiVoice"

    private const val GH = "https://github.com"
    private const val REPO = "qdTXTbp/FuMiVoice"

    /** GitHub 加速镜像（前缀式，直接拼完整 GitHub URL）。顺序按实测速度。 */
    private val GH_MIRRORS = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
    )

    fun get(context: Context): SourceMode {
        val k = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        return SourceMode.values().firstOrNull { it.key == k } ?: SourceMode.AUTO
    }

    fun set(context: Context, mode: SourceMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.key).apply()
    }

    /**
     * 更新器用：某个 release 资产的**有序**候选地址（全部是可直接 GET 的完整 URL）。
     *
     * - 国内源：CNB → GitHub 加速镜像 → GitHub 直连
     * - 境外源：GitHub 直连 → 加速镜像 → CNB（反着排，让直连先试）
     * - 自动：与国内源一致（大陆用户占多数；任何一条不通都会自动换下一条）
     */
    fun releaseUrls(context: Context, asset: String): List<String> {
        val cnb = "$CNB_BASE/-/releases/latest/download/$asset"
        val gh = "$GH/$REPO/releases/latest/download/$asset"
        val mirrors = GH_MIRRORS.map { it + gh }
        return when (get(context)) {
            SourceMode.DOMESTIC -> listOf(cnb) + mirrors + gh
            SourceMode.FOREIGN -> listOf(gh) + mirrors + cnb
            SourceMode.AUTO -> listOf(cnb) + mirrors + gh
        }
    }

    /**
     * 音色库等**任意 GitHub 直链**的候选顺序（跟随同一份设置）。
     * 非 GitHub 地址原样返回（jsDelivr 之类本身就是快 CDN，套镜像只会更慢）。
     */
    fun mirrorOrder(context: Context, url: String): List<String> {
        if (!url.startsWith("$GH/")) return listOf(url)
        val mirrored = GH_MIRRORS.map { it + url }
        return when (get(context)) {
            SourceMode.DOMESTIC, SourceMode.AUTO -> mirrored + url
            SourceMode.FOREIGN -> listOf(url) + mirrored
        }
    }
}
