package com.fumi.voice.library

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/** 一首曲目的播放统计。 */
data class PlayRecord(
    val fileName: String,
    val count: Int,
    val lastPlayed: Long,
)

/**
 * 播放历史。
 *
 * 记录每首曲目的播放次数与最后一次播放时间，
 * 「最近播放」按时间倒序，同时用播放次数给列表加一个热度标记。
 * 同样按键存文件名，与歌单/修正表保持一致。
 */
class PlayHistoryManager(private val context: Context) {

    companion object {
        private const val TAG = "PlayHistory"
        private const val FILE_NAME = "play_history.json"
        private const val KEY_COUNT = "count"
        private const val KEY_LAST = "last"
    }

    private val file: File = File(context.filesDir, FILE_NAME)

    private val entries: MutableMap<String, PlayRecord> = read().toMutableMap()

    /** 记录一次播放（曲目装载成功时调用）。 */
    @Synchronized
    fun record(fileName: String) {
        if (fileName.isBlank()) return
        val previous = entries[fileName]
        entries[fileName] = PlayRecord(
            fileName = fileName,
            count = (previous?.count ?: 0) + 1,
            lastPlayed = System.currentTimeMillis(),
        )
        persist()
    }

    /** 全部记录，按最后播放时间倒序。 */
    fun all(): List<PlayRecord> = entries.values.sortedByDescending { it.lastPlayed }

    /** 最近播放，按时间倒序取前 [limit] 条。 */
    fun recent(limit: Int = 50): List<PlayRecord> = all().take(limit)

    fun recordFor(fileName: String): PlayRecord? = entries[fileName]

    @Synchronized
    fun clear() {
        if (entries.isEmpty()) return
        entries.clear()
        persist()
    }

    /** 曲库文件被删除后清理悬空记录。 */
    @Synchronized
    fun pruneTo(validFileNames: Set<String>) {
        val removed = entries.keys.filterNot { it in validFileNames }
        if (removed.isEmpty()) return
        removed.forEach { entries.remove(it) }
        persist()
    }

    private fun read(): Map<String, PlayRecord> {
        if (!file.exists()) return emptyMap()
        return try {
            val root = JSONObject(file.readText())
            val result = mutableMapOf<String, PlayRecord>()
            val names = root.keys()
            while (names.hasNext()) {
                val name = names.next()
                val obj = root.optJSONObject(name) ?: continue
                result[name] = PlayRecord(
                    fileName = name,
                    count = obj.optInt(KEY_COUNT, 0),
                    lastPlayed = obj.optLong(KEY_LAST, 0L),
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "读取播放历史失败", e)
            emptyMap()
        }
    }

    private fun persist() {
        try {
            val root = JSONObject()
            entries.forEach { (name, record) ->
                root.put(
                    name,
                    JSONObject().apply {
                        put(KEY_COUNT, record.count)
                        put(KEY_LAST, record.lastPlayed)
                    }
                )
            }
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "保存播放历史失败", e)
        }
    }
}
