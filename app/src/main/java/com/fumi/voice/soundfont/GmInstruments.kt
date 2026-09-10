package com.fumi.voice.soundfont

/** 一个 GM 音色（旋律乐器）。[program] 为 0-127 的 MIDI 程序号。 */
data class GmInstrument(
    val program: Int,
    val name: String,
    val englishName: String,
    val family: String,
)

/**
 * General MIDI 标准音色表：128 个旋律音色，按 16 个族分组，每组 8 个。
 *
 * 顺序严格遵循 GM 规范，因此 program 号可直接用于 MIDI Program Change。
 */
object GmInstruments {

    private val FAMILIES = listOf(
        "钢琴", "色彩打击", "风琴", "吉他", "贝斯", "弦乐", "合奏", "铜管",
        "簧管", "吹管", "合成主音", "合成铺底", "合成效果", "民族乐器", "打击乐", "音效",
    )

    private val NAMES = listOf(
        // 1-8 钢琴
        "大钢琴" to "Acoustic Grand Piano",
        "明亮钢琴" to "Bright Acoustic Piano",
        "电钢琴" to "Electric Grand Piano",
        "酒吧钢琴" to "Honky-tonk Piano",
        "电钢琴 1" to "Electric Piano 1",
        "电钢琴 2" to "Electric Piano 2",
        "大键琴" to "Harpsichord",
        "击弦古钢琴" to "Clavinet",
        // 9-16 色彩打击
        "钢片琴" to "Celesta",
        "钟琴" to "Glockenspiel",
        "八音盒" to "Music Box",
        "颤音琴" to "Vibraphone",
        "马林巴" to "Marimba",
        "木琴" to "Xylophone",
        "管钟" to "Tubular Bells",
        "扬琴" to "Dulcimer",
        // 17-24 风琴
        "拉杆风琴" to "Drawbar Organ",
        "打击风琴" to "Percussive Organ",
        "摇滚风琴" to "Rock Organ",
        "教堂管风琴" to "Church Organ",
        "簧风琴" to "Reed Organ",
        "手风琴" to "Accordion",
        "口琴" to "Harmonica",
        "探戈手风琴" to "Tango Accordion",
        // 25-32 吉他
        "尼龙弦吉他" to "Acoustic Guitar (nylon)",
        "钢弦吉他" to "Acoustic Guitar (steel)",
        "爵士电吉他" to "Electric Guitar (jazz)",
        "清音电吉他" to "Electric Guitar (clean)",
        "闷音电吉他" to "Electric Guitar (muted)",
        "过载吉他" to "Overdriven Guitar",
        "失真吉他" to "Distortion Guitar",
        "吉他泛音" to "Guitar Harmonics",
        // 33-40 贝斯
        "原声贝斯" to "Acoustic Bass",
        "指弹电贝斯" to "Electric Bass (finger)",
        "拨片电贝斯" to "Electric Bass (pick)",
        "无品贝斯" to "Fretless Bass",
        "击弦贝斯 1" to "Slap Bass 1",
        "击弦贝斯 2" to "Slap Bass 2",
        "合成贝斯 1" to "Synth Bass 1",
        "合成贝斯 2" to "Synth Bass 2",
        // 41-48 弦乐
        "小提琴" to "Violin",
        "中提琴" to "Viola",
        "大提琴" to "Cello",
        "低音提琴" to "Contrabass",
        "颤弓弦乐" to "Tremolo Strings",
        "拨弦弦乐" to "Pizzicato Strings",
        "竖琴" to "Orchestral Harp",
        "定音鼓" to "Timpani",
        // 49-56 合奏
        "弦乐合奏 1" to "String Ensemble 1",
        "弦乐合奏 2" to "String Ensemble 2",
        "合成弦乐 1" to "Synth Strings 1",
        "合成弦乐 2" to "Synth Strings 2",
        "人声合唱" to "Choir Aahs",
        "人声呓语" to "Voice Oohs",
        "合成人声" to "Synth Voice",
        "管弦乐齐奏" to "Orchestra Hit",
        // 57-64 铜管
        "小号" to "Trumpet",
        "长号" to "Trombone",
        "大号" to "Tuba",
        "弱音小号" to "Muted Trumpet",
        "圆号" to "French Horn",
        "铜管合奏" to "Brass Section",
        "合成铜管 1" to "Synth Brass 1",
        "合成铜管 2" to "Synth Brass 2",
        // 65-72 簧管
        "高音萨克斯" to "Soprano Sax",
        "中音萨克斯" to "Alto Sax",
        "次中音萨克斯" to "Tenor Sax",
        "上低音萨克斯" to "Baritone Sax",
        "双簧管" to "Oboe",
        "英国管" to "English Horn",
        "巴松管" to "Bassoon",
        "单簧管" to "Clarinet",
        // 73-80 吹管
        "短笛" to "Piccolo",
        "长笛" to "Flute",
        "竖笛" to "Recorder",
        "排箫" to "Pan Flute",
        "瓶笛" to "Blown Bottle",
        "尺八" to "Shakuhachi",
        "口哨" to "Whistle",
        "陶笛" to "Ocarina",
        // 81-88 合成主音
        "方波主音" to "Lead 1 (square)",
        "锯齿主音" to "Lead 2 (sawtooth)",
        "汽笛风琴主音" to "Lead 3 (calliope)",
        "吹管主音" to "Lead 4 (chiff)",
        "人声主音" to "Lead 5 (charang)",
        "合唱主音" to "Lead 6 (voice)",
        "五度主音" to "Lead 7 (fifths)",
        "贝斯主音" to "Lead 8 (bass + lead)",
        // 89-96 合成铺底
        "新世纪铺底" to "Pad 1 (new age)",
        "温暖铺底" to "Pad 2 (warm)",
        "复音铺底" to "Pad 3 (polysynth)",
        "合唱铺底" to "Pad 4 (choir)",
        "弓弦铺底" to "Pad 5 (bowed)",
        "金属铺底" to "Pad 6 (metallic)",
        "光晕铺底" to "Pad 7 (halo)",
        "扫掠铺底" to "Pad 8 (sweep)",
        // 97-104 合成效果
        "雨声" to "FX 1 (rain)",
        "配乐" to "FX 2 (soundtrack)",
        "水晶" to "FX 3 (crystal)",
        "氛围" to "FX 4 (atmosphere)",
        "明亮" to "FX 5 (brightness)",
        "妖精" to "FX 6 (goblins)",
        "回声" to "FX 7 (echoes)",
        "科幻" to "FX 8 (sci-fi)",
        // 105-112 民族乐器
        "西塔琴" to "Sitar",
        "班卓琴" to "Banjo",
        "三味线" to "Shamisen",
        "古筝" to "Koto",
        "卡林巴" to "Kalimba",
        "风笛" to "Bagpipe",
        "乡村提琴" to "Fiddle",
        "唢呐" to "Shanai",
        // 113-120 打击乐
        "铃铛" to "Tinkle Bell",
        "阿哥哥铃" to "Agogo",
        "钢鼓" to "Steel Drums",
        "木鱼" to "Woodblock",
        "太鼓" to "Taiko Drum",
        "旋律鼓" to "Melodic Tom",
        "合成鼓" to "Synth Drum",
        "反镲" to "Reverse Cymbal",
        // 121-128 音效
        "吉他品丝噪音" to "Guitar Fret Noise",
        "呼吸声" to "Breath Noise",
        "海浪" to "Seashore",
        "鸟鸣" to "Bird Tweet",
        "电话铃" to "Telephone Ring",
        "直升机" to "Helicopter",
        "掌声" to "Applause",
        "枪声" to "Gunshot",
    )

    /** 128 个 GM 旋律音色。 */
    val all: List<GmInstrument> = NAMES.mapIndexed { index, (cn, en) ->
        GmInstrument(
            program = index,
            name = cn,
            englishName = en,
            family = FAMILIES[index / 8],
        )
    }

    fun byProgram(program: Int): GmInstrument? = all.getOrNull(program)

    /** 当 program 落在某个族时的族名。 */
    fun familyOf(program: Int): String? = all.getOrNull(program)?.family
}
