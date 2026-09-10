package com.fumi.voice.library

import android.content.Context
import com.fumi.voice.model.MidiTrack

/**
 * 曲目排序方式。
 *
 * 名称类默认升序（A→Z），数量类默认降序（长 / 大 / 多的排前面）。
 * 想看"哪首最长"时还要再点一次才反过来，体验是反的。
 */
enum class TrackSort(val label: String, val defaultAscending: Boolean) {
    FILE_NAME("文件名", true),
    TITLE("曲名", true),
    DURATION("时长", false),
    SIZE("文件大小", false),
    NOTES("音符数", false),
    PLAY_COUNT("播放次数", false);

    /** 按本方式排序。[playCounts] 只有 PLAY_COUNT 用得上。 */
    fun apply(
        tracks: List<MidiTrack>,
        ascending: Boolean,
        playCounts: Map<String, Int>,
    ): List<MidiTrack> {
        val comparator: Comparator<MidiTrack> = when (this) {
            FILE_NAME -> compareBy { it.fileName.lowercase() }
            TITLE -> compareBy { it.title.lowercase() }
            DURATION -> compareBy { it.durationMs }
            SIZE -> compareBy { it.sizeBytes }
            NOTES -> compareBy { it.noteCount }
            PLAY_COUNT -> compareBy { playCounts[it.fileName] ?: 0 }
        }
        // 同值时用文件名兜底，保证顺序稳定——否则时长一样的曲目每次
        // 重扫都可能换位，列表看起来在"自己动"
        val stable = comparator.thenBy { it.fileName.lowercase() }
        return tracks.sortedWith(if (ascending) stable else stable.reversed())
    }
}

/**
 * 曲库显示偏好。
 *
 * 排序属于"看一眼就希望被记住"的设置，所以落盘而不是只放内存——
 * 每次启动都回到默认排序，用户会以为排序没生效。
 */
class LibraryPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("fumi_library", Context.MODE_PRIVATE)

    fun sortMode(): TrackSort =
        TrackSort.entries.getOrNull(prefs.getInt(KEY_SORT, 0)) ?: TrackSort.FILE_NAME

    /** 每种排序方式各自记住升降序，切换回来时保持上次的方向。 */
    fun ascending(mode: TrackSort): Boolean =
        prefs.getBoolean(KEY_ASC_PREFIX + mode.name, mode.defaultAscending)

    fun save(mode: TrackSort, ascending: Boolean) {
        prefs.edit()
            .putInt(KEY_SORT, mode.ordinal)
            .putBoolean(KEY_ASC_PREFIX + mode.name, ascending)
            .apply()
    }

    private companion object {
        const val KEY_SORT = "sort_mode"
        const val KEY_ASC_PREFIX = "sort_asc_"
    }
}
