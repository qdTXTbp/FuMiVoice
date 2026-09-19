package com.fumi.voice.soundfont

import com.fumi.voice.util.isChineseUi

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
    /**
     * 分卷资源：按顺序下载后拼成一个文件。
     *
     * 上游把超大音色库切成了几个 `.sf2.partN` 分卷（单文件超过平台上限），
     * 这里按声明顺序逐卷拉取并首尾相接；[url] 留空即可。
     */
    val parts: List<String> = emptyList(),
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

        // ---------- 旗舰音色库 ----------
        //
        // 这一组来自电脑端 FuFumidi 的官方音色库镜像，体积明显更大、
        // 采样更完整，适合对音质有要求的场景。全部走 GitHub Releases，
        // 因此下载时会由 DownloadEngine 自动套用国内镜像并按分段并发加速。
        //
        // 许可证说明：GeneralUser GS / FluidR3 的许可是公开且明确的；
        // SGM、Arachno 在上游没有随包附许可文件，这里只如实标注出处，
        // 不替作者下结论——使用者应以发布页为准。
        SoundFontSource(
            id = "generaluser_gs",
            fileName = "GeneralUser.GS.v1.471.sf2",
            displayName = "GeneralUser GS 1.471",
            description = "完整 GM/GS，自带 GS 变体与鼓组，音色自然、体积适中",
            category = "旗舰音色库",
            url = "https://github.com/monologue82/FuFumidiSoundFonts/releases/download/v1/GeneralUser.GS.v1.471.sf2",
            approxBytes = 31_281_186,
            license = "GeneralUser GS License v2.0",
        ),
        SoundFontSource(
            id = "fluidr3_gm",
            fileName = "FluidR3_GM.sf2",
            displayName = "FluidR3 GM（完整版）",
            description = "128 音色 + 鼓组的经典完整采样库，钢琴与弦乐表现力强",
            category = "旗舰音色库",
            url = "https://github.com/monologue82/FuFumidiSoundFonts/releases/download/v1/FluidR3_GM.sf2",
            approxBytes = 148_398_306,
            license = "MIT（Frank Wen）",
        ),
        SoundFontSource(
            id = "sgm_v2",
            fileName = "SGM_V2_01.sf2",
            displayName = "SGM-V2.01（分卷）",
            description = "GS/GM 兼容的大体量音色库，分 3 卷下载后自动合并",
            category = "旗舰音色库",
            url = "",
            approxBytes = 305_414_752,
            license = "出处：FuFumidiSoundFonts 镜像，许可以发布页为准",
            parts = listOf(
                "https://github.com/monologue82/FuFumidiSoundFonts/releases/download/v1/SGM_V2_01_part1.sf2",
                "https://github.com/monologue82/FuFumidiSoundFonts/releases/download/v1/SGM_V2_01_part2.sf2",
                "https://github.com/monologue82/FuFumidiSoundFonts/releases/download/v1/SGM_V2_01_part3.sf2",
            ),
        ),
        SoundFontSource(
            id = "arachno",
            fileName = "Arachno_SoundFont_Version_1.0.sf2",
            displayName = "Arachno SoundFont 1.0",
            description = "影视配乐取向的大体量音色库，动态与层次感突出",
            category = "旗舰音色库",
            url = "https://github.com/qdTXTbp/FuFumidi/releases/download/soundfonts-v1/Arachno_SoundFont_Version_1.0.sf2",
            approxBytes = 155_405_818,
            license = "出处：FuFumidi 音色库发布页，许可以发布页为准",
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

    /** 按文件名反查友好名称。
     * 下载进来的文件在磁盘上叫 `FluidR3Mono_GM.sf3`，但界面应该显示「FluidR3 Mono GM」。
     */
    fun displayNameFor(fileName: String): String? =
        all.firstOrNull { it.fileName == fileName }?.displayName

    /**
     * 英文界面下的文案替换表：中文原文 → 英文。
     *
     * 用「查表」而不是给每条都加 `*En` 平行字段：这张表本质是数据，
     * 每条塞两份文案会让它长一倍且容易漏填；查表没命中时原样返回，
     * 也不会因为漏了一条就显示出空字符串。
     */
    private val EN: Map<String, String> = mapOf(
        // 分类
        "通用音色库" to "General MIDI banks",
        "旗舰音色库" to "Flagship banks",
        "单音色强化" to "Instrument-focused",
        "合成与电子" to "Synth & electronic",
        "轻量音色库" to "Lightweight",

        // 中文显示名
        "FM/GM 紧凑版" to "FM/GM Compact",
        "雅马哈 C5 三角钢琴" to "Yamaha C5 Grand Piano",
        "Galaxy 电钢琴" to "Galaxy Electric Pianos",
        "Supersaw 合成音色" to "Supersaw Collection",
        "Florestan 轻量 GM" to "Florestan Light GM",
        "FluidR3 GM（完整版）" to "FluidR3 GM (complete)",
        "SGM-V2.01（分卷）" to "SGM-V2.01 (split parts)",

        // 许可/出处中的中文部分
        "公共素材" to "Public domain",
        "出处：FuFumidiSoundFonts 镜像，许可以发布页为准" to
            "Source: FuFumidiSoundFonts mirror; see its release page for the licence",
        "出处：FuFumidi 音色库发布页，许可以发布页为准" to
            "Source: FuFumidi soundfont release page; see it for the licence",

        // 描述
        "完整 128 音色 + 鼓组，音色均衡自然，日常播放首选" to
            "Full 128 GM instruments plus drums; balanced and natural — a solid everyday choice",
        "完整 GM，FM 合成味道，复古游戏听感，兼容 GS/XG/GM2 别名" to
            "Full GM with FM character and a retro game feel; GS/XG/GM2 aliases supported",
        "完整 GM，FM 合成味道更重，复古游戏机听感" to
            "Full GM with a heavier FM character — classic console sound",
        "单独强化的钢琴音色，弹钢琴曲首选" to
            "An individually sampled grand piano — best for piano pieces",
        "电钢琴合集，适合流行与爵士" to
            "An electric piano collection suited to pop and jazz",
        "60 个锯齿波音色，电子舞曲风格" to
            "60 sawtooth-based patches in an EDM style",
        "约 0.5MB 的极简 GM 子集，秒下秒加载，适合快速试听" to
            "A ~0.5 MB minimal GM subset — instant to download and load, great for a quick listen",
        "约 0.3MB 的复古合成波形，怀旧电子音色" to
            "A ~0.3 MB retro synth waveform bank with a nostalgic electronic flavour",
        "完整 GM/GS，自带 GS 变体与鼓组，音色自然、体积适中" to
            "Full GM/GS with GS variations and drums; natural sound at a moderate size",
        "128 音色 + 鼓组的经典完整采样库，钢琴与弦乐表现力强" to
            "The classic complete 128-instrument sampling set; expressive piano and strings",
        "GS/GM 兼容的大体量音色库，分 3 卷下载后自动合并" to
            "A large GS/GM-compatible bank, split into 3 parts that are merged automatically",
        "影视配乐取向的大体量音色库，动态与层次感突出" to
            "A large film-scoring oriented bank with strong dynamics and depth",
    )

    /** 按当前语言取文案；英文界面且表里有对应条目时返回英文。 */
    fun localized(text: String): String = if (isChineseUi) text else EN[text] ?: text
}
