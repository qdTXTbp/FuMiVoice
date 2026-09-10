package com.fumi.voice.library

/**
 * 从文件名推断「艺术家 - 曲名」。
 *
 * 为什么走文件名而不是读元数据：标准 MIDI 文件（SMF）规范里**没有艺术家字段**。
 * 实测用户的 8 个 MIDI 里，版权/文本事件命中 0 个，
 * 唯一存在的 TrackName(0x03) 是**轨道名**（Piano、Melody 之类），不是曲名。
 * 所以曲名和艺术家只能从文件名推。
 *
 * 这个推断必然是启发式的：`铃木爱理 _ HOYO-MiX - 尘间星旅` 是「艺术家 - 曲名」，
 * 而 `经过 - 张杰 _ HOYO-MiX` 是「曲名 - 艺术家」，两种情况无法靠规则区分。
 * 因此这里只做默认猜测，界面上必须提供手动修正。
 */
object TrackMetadataParser {

    /** 没有可用艺术家信息时的归类名。 */
    const val UNKNOWN_ARTIST = "未知艺术家"

    private const val SEPARATOR = " - "

    /**
     * @return artist 为 null 表示没能从文件名推断出艺术家
     */
    fun parse(fileName: String): Pair<String?, String> {
        val base = fileName.substringBeforeLast('.').trim()
        if (base.isEmpty()) return null to fileName

        // 取第一个 " - " 作为分隔：中文音乐文件名普遍是「歌手 - 歌名」
        val idx = base.indexOf(SEPARATOR)
        if (idx > 0) {
            val artist = base.substring(0, idx).trim().trimQuotes()
            val title = base.substring(idx + SEPARATOR.length).trim().trimQuotes()
            if (artist.isNotBlank() && title.isNotBlank()) {
                return artist to title
            }
        }
        return null to base
    }

    private fun String.trimQuotes(): String =
        trim().trim('"', '\'', '“', '”', '‘', '’', '「', '」', '《', '》')
}
