package com.fumi.voice.model

/** 曲库中的一首 MIDI 曲目。 */
data class MidiTrack(
    val fileName: String,
    val title: String,
    val path: String,
    val sizeBytes: Long,
    /** 音符总数，导入时解析得到；解析失败为 0。 */
    val noteCount: Int = 0,
    /** 时长（毫秒），导入时解析得到；解析失败为 0。 */
    val durationMs: Long = 0,
    /**
     * 艺术家。来自文件名启发式推断或用户手动修正；推断不出时为 null。
     *
     * 标准 MIDI 文件没有艺术家字段，只能走文件名 + 手动修正。
     */
    val artist: String? = null,
)
