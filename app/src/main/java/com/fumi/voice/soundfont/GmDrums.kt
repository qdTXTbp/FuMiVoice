package com.fumi.voice.soundfont

/** GM 标准打击乐器（对应 MIDI 通道 10 上的音符号）。 */
data class GmDrum(
    val note: Int,
    val name: String,
    val englishName: String,
)

/**
 * General MIDI 标准鼓组映射（音符 35-81）。
 * 全部落在 MIDI 通道 10 上，音色库会自动使用鼓组 bank。
 */
object GmDrums {

    val all: List<GmDrum> = listOf(
        GmDrum(35, "低音大鼓", "Acoustic Bass Drum"),
        GmDrum(36, "大鼓", "Bass Drum 1"),
        GmDrum(37, "边击", "Side Stick"),
        GmDrum(38, "军鼓", "Acoustic Snare"),
        GmDrum(39, "拍手", "Hand Clap"),
        GmDrum(40, "电子军鼓", "Electric Snare"),
        GmDrum(41, "低音落地鼓", "Low Floor Tom"),
        GmDrum(42, "闭合踩镲", "Closed Hi-Hat"),
        GmDrum(43, "高音落地鼓", "High Floor Tom"),
        GmDrum(44, "踩踏踩镲", "Pedal Hi-Hat"),
        GmDrum(45, "低音通鼓", "Low Tom"),
        GmDrum(46, "开放踩镲", "Open Hi-Hat"),
        GmDrum(47, "中低通鼓", "Low-Mid Tom"),
        GmDrum(48, "中高通鼓", "Hi-Mid Tom"),
        GmDrum(49, "强音镲 1", "Crash Cymbal 1"),
        GmDrum(50, "高音通鼓", "High Tom"),
        GmDrum(51, "叮叮镲 1", "Ride Cymbal 1"),
        GmDrum(52, "中国镲", "Chinese Cymbal"),
        GmDrum(53, "镲铃", "Ride Bell"),
        GmDrum(54, "铃鼓", "Tambourine"),
        GmDrum(55, "水镲", "Splash Cymbal"),
        GmDrum(56, "牛铃", "Cowbell"),
        GmDrum(57, "强音镲 2", "Crash Cymbal 2"),
        GmDrum(58, "振动器", "Vibraslap"),
        GmDrum(59, "叮叮镲 2", "Ride Cymbal 2"),
        GmDrum(60, "高音邦戈", "Hi Bongo"),
        GmDrum(61, "低音邦戈", "Low Bongo"),
        GmDrum(62, "闷音高康加", "Mute Hi Conga"),
        GmDrum(63, "开放高康加", "Open Hi Conga"),
        GmDrum(64, "低音康加", "Low Conga"),
        GmDrum(65, "高音天巴鼓", "High Timbale"),
        GmDrum(66, "低音天巴鼓", "Low Timbale"),
        GmDrum(67, "高音阿哥哥", "High Agogo"),
        GmDrum(68, "低音阿哥哥", "Low Agogo"),
        GmDrum(69, "沙铃", "Cabasa"),
        GmDrum(70, "沙锤", "Maracas"),
        GmDrum(71, "短哨", "Short Whistle"),
        GmDrum(72, "长哨", "Long Whistle"),
        GmDrum(73, "短刮胡", "Short Guiro"),
        GmDrum(74, "长刮胡", "Long Guiro"),
        GmDrum(75, "响棒", "Claves"),
        GmDrum(76, "高音木鱼", "Hi Wood Block"),
        GmDrum(77, "低音木鱼", "Low Wood Block"),
        GmDrum(78, "闷音库依卡", "Mute Cuica"),
        GmDrum(79, "开放库依卡", "Open Cuica"),
        GmDrum(80, "闷音三角铁", "Mute Triangle"),
        GmDrum(81, "开放三角铁", "Open Triangle"),
    )

    /** 试听常用的核心部件。 */
    val core: List<GmDrum> = listOf(36, 38, 42, 46, 45, 48, 49, 51, 39, 54, 56)
        .mapNotNull { n -> all.firstOrNull { it.note == n } }
}
