package com.fumi.voice.soundfont

/** 可下载的音色库条目。 */
data class SoundFontSource(
    val id: String,
    /** 下载后保存的文件名（含扩展名）。 */
    val fileName: String,
    val displayName: String,
    /** 一句话说明音色取向，帮助用户选择。 */
    val description: String,
    /** 分类标签，用于分组。 */
    val category: String,
    val url: String,
    /** 预期体积（字节），仅用于列表提前显示；下载后以实际为准。 */
    val approxBytes: Long,
    val license: String,
    /**
     * 下载速度偏慢的源。
     *
     * 实测同一网络下 jsDelivr 约 2.6 MB/s，GitHub Pages 只有约 24 KB/s。
     * 标慢的条目依然可用（体积小的很快就能下完），但界面要提前告知用户。
     */
    val slow: Boolean = false,
)

/**
 * 应用内可下载的音色库目录。
 *
 * 全部为**第三方公开发布**的自由音色库，条目里标注了许可证。
 *
 * 关于速度：优先选 jsDelivr（实测约 2.6 MB/s），
 * 但 jsDelivr 对单文件限 20MB、对单仓库限 50MB，
 * 雅马哈三角钢琴、Galaxy 电钢、Supersaw 这类较大的资源只能走 GitHub Pages，
 * 实测仅约 24 KB/s —— 因此这些条目标记 [SoundFontSource.slow]，
 * 由界面显示「较慢」提示，让用户自己权衡。
 */
object SoundFontCatalog {

    val all: List<SoundFontSource> = listOf(
        // ---------- 通用音色库 ----------
        SoundFontSource(
            id = "fluidr3mono",
            fileName = "FluidR3Mono_GM.sf3",
            displayName = "FluidR3 Mono GM",
            description = "完整 128 音色 + 鼓组，音色均衡自然，日常播放首选",
            category = "通用音色库",
            url = "https://cdn.jsdelivr.net/gh/musescore/MuseScore@2.1/share/sound/FluidR3Mono_GM.sf3",
            approxBytes = 14_563_174,
            license = "CC BY 3.0",
        ),
        SoundFontSource(
            id = "fm_gm_mini",
            fileName = "FM_GM_SoundFont_mini.sf2",
            displayName = "FM/GM 紧凑版",
            description = "完整 GM，FM 合成味道，复古游戏听感，兼容 GS/XG/GM2 别名",
            category = "通用音色库",
            url = "https://cdn.jsdelivr.net/gh/zeittresor/opensoundfont@main/FM_GM_SoundFont_v0_2_1_mini.sf2",
            approxBytes = 14_337_918,
            license = "GPL-3.0",
        ),
        SoundFontSource(
            id = "giga_fm_gm",
            fileName = "giga-hq-fm-gm.sf2",
            displayName = "GIGA FM GM",
            description = "完整 GM，FM 合成味道更重，复古游戏机听感",
            category = "通用音色库",
            url = "https://smpldsnds.github.io/soundfonts/soundfonts/giga-hq-fm-gm.sf2",
            approxBytes = 20_573_000,
            license = "CC BY 4.0",
            slow = true,
        ),

        // ---------- 单音色强化 ----------
        SoundFontSource(
            id = "yamaha_grand",
            fileName = "yamaha-grand-lite.sf2",
            displayName = "雅马哈 C5 三角钢琴",
            description = "单独强化的钢琴音色，弹钢琴曲首选",
            category = "单音色强化",
            url = "https://smpldsnds.github.io/soundfonts/soundfonts/yamaha-grand-lite.sf2",
            approxBytes = 21_780_000,
            license = "GPL-3.0",
            slow = true,
        ),
        SoundFontSource(
            id = "galaxy_ep",
            fileName = "galaxy-electric-pianos.sf2",
            displayName = "Galaxy 电钢琴",
            description = "电钢琴合集，适合流行与爵士",
            category = "单音色强化",
            url = "https://smpldsnds.github.io/soundfonts/soundfonts/galaxy-electric-pianos.sf2",
            approxBytes = 30_300_000,
            license = "GPL-3.0",
            slow = true,
        ),

        // ---------- 合成与电子 ----------
        SoundFontSource(
            id = "supersaw",
            fileName = "supersaw-collection.sf2",
            displayName = "Supersaw 合成音色",
            description = "60 个锯齿波音色，电子舞曲风格",
            category = "合成与电子",
            url = "https://smpldsnds.github.io/soundfonts/soundfonts/supersaw-collection.sf2",
            approxBytes = 56_360_000,
            license = "GPL-3.0",
            slow = true,
        ),

        // ---------- 轻量音色库 ----------
        SoundFontSource(
            id = "florestan",
            fileName = "florestan-subset.sf2",
            displayName = "Florestan 轻量 GM",
            description = "约 0.5MB 的极简 GM 子集，秒下秒加载，适合快速试听",
            category = "轻量音色库",
            url = "https://cdn.jsdelivr.net/gh/schellingb/TinySoundFont@master/examples/florestan-subset.sf2",
            approxBytes = 531_786,
            license = "公共素材",
        ),
        SoundFontSource(
            id = "vintage_dreams",
            fileName = "VintageDreamsWaves-v2.sf2",
            displayName = "Vintage Dreams Waves",
            description = "约 0.3MB 的复古合成波形，怀旧电子音色",
            category = "轻量音色库",
            url = "https://cdn.jsdelivr.net/gh/FluidSynth/fluidsynth@master/sf2/VintageDreamsWaves-v2.sf2",
            approxBytes = 314_640,
            license = "公共素材",
        ),
    )

    /** 按分类分组，保持 [all] 里的声明顺序。 */
    val grouped: List<Pair<String, List<SoundFontSource>>>
        get() = all.groupBy { it.category }.toList()

    fun byId(id: String): SoundFontSource? = all.firstOrNull { it.id == id }

    /**
     * 按文件名反查友好名称。
     * 下载进来的文件在磁盘上叫 `FluidR3Mono_GM.sf3`，但界面应该显示「FluidR3 Mono GM」。
     */
    fun displayNameFor(fileName: String): String? =
        all.firstOrNull { it.fileName == fileName }?.displayName
}
