package com.fumi.voice.library

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 一首曲目的手动修正信息。
 * 任一字段为 null 表示「沿用自动推断的结果」。
 */
data class TrackOverride(
    val artist: String? = null,
    val title: String? = null,
)

/**
 * 曲目元数据修正表。
 *
 * 文件名启发式必然会猜错（`经过 - 张杰` 是「曲名 - 歌手」而不是「歌手 - 曲名」），
 * 所以必须允许用户手动改，并且改完要能持久保存。
 * 和歌单一样按键存**文件名**，因为曲库目录的绝对路径会随安装/迁移变化。
 */
class TrackMetaStore(private val context: Context) {

    companion object {
        private const val TAG = "TrackMetaStore"
        private const val FILE_NAME = "track_meta.json"
        private const val KEY_ARTIST = "artist"
        private const val KEY_TITLE = "title"
    }

    private val file: File = File(context.filesDir, FILE_NAME)

    /** 文件名 -> 修正值。 */
    private val entries: MutableMap<String, TrackOverride> = read().toMutableMap()

    /** 取某首曲目的手动修正；没有则返回 null。 */
    fun get(fileName: String): TrackOverride? = entries[fileName]

    /** 覆盖名与曲名。传 null 的字段回退到自动推断。 */
    fun put(fileName: String, artist: String?, title: String?) {
        if (artist.isNullOrBlank() && title.isNullOrBlank()) {
            if (entries.remove(fileName) != null) persist()
            return
        }
        entries[fileName] = TrackOverride(artist?.trim()?.ifBlank { null }, title?.trim()?.ifBlank { null })
        persist()
    }

    fun remove(fileName: String) {
        if (entries.remove(fileName) != null) persist()
    }

    /** 曲库文件被删除后，顺手清掉悬空的修正记录。 */
    fun pruneTo(validFileNames: Set<String>) {
        val removed = entries.keys.filterNot { it in validFileNames }
        if (removed.isEmpty()) return
        removed.forEach { entries.remove(it) }
        persist()
    }

    private fun read(): Map<String, TrackOverride> {
        if (!file.exists()) return emptyMap()
        return try {
            val root = JSONObject(file.readText())
            val result = mutableMapOf<String, TrackOverride>()
            val names = root.keys()
            while (names.hasNext()) {
                val name = names.next()
                val obj = root.optJSONObject(name) ?: continue
                result[name] = TrackOverride(
                    artist = obj.optString(KEY_ARTIST).ifBlank { null },
                    title = obj.optString(KEY_TITLE).ifBlank { null },
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "读取曲目信息失败", e)
            emptyMap()
        }
    }

    @Synchronized
    private fun persist() {
        try {
            val root = JSONObject()
            entries.forEach { (name, override) ->
                root.put(
                    name,
                    JSONObject().apply {
                        if (override.artist != null) put(KEY_ARTIST, override.artist)
                        if (override.title != null) put(KEY_TITLE, override.title)
                    }
                )
            }
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "保存曲目信息失败", e)
        }
    }
}
