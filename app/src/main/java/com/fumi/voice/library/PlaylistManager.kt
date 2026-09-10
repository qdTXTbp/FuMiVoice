package com.fumi.voice.library

import android.content.Context
import android.util.Log
import com.fumi.voice.model.MidiTrack
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 一个自定义歌单。[trackFileNames] 按用户排定的顺序保存曲目文件名。 */
data class Playlist(
    val id: String,
    val name: String,
    val trackFileNames: List<String>,
    val createdAt: Long,
)

/**
 * 歌单管理。
 *
 * 为什么存文件名而不是绝对路径：
 * 曲库文件统一放在应用私有目录，路径会随安装/迁移变化，
 * 而文件名稳定，且能自然地把「文件已被移除」的歌单项识别成失效项。
 */
class PlaylistManager(private val context: Context) {

    companion object {
        private const val TAG = "PlaylistManager"
        private const val FILE_NAME = "playlists.json"
        private const val KEY_PLAYLISTS = "playlists"
    }

    private val file: File = File(context.filesDir, FILE_NAME)

    /** 全部歌单，按创建时间倒序（新建的排在前面）。 */
    fun list(): List<Playlist> = read().sortedByDescending { it.createdAt }

    fun find(id: String): Playlist? = read().firstOrNull { it.id == id }

    fun create(name: String): Playlist {
        val cleaned = name.trim().ifBlank { "新建歌单" }
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = uniqueName(cleaned),
            trackFileNames = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
        write(read() + playlist)
        return playlist
    }

    fun rename(id: String, newName: String): Boolean {
        val cleaned = newName.trim()
        if (cleaned.isBlank()) return false
        val all = read()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return false
        all[index] = all[index].copy(name = uniqueName(cleaned, excludeId = id))
        write(all)
        return true
    }

    fun delete(id: String): Boolean {
        val all = read()
        val filtered = all.filterNot { it.id == id }
        if (filtered.size == all.size) return false
        write(filtered)
        return true
    }

    /**
     * 往歌单里追加曲目，已存在的会被跳过。
     * @return 实际新增的数量
     */
    fun addTracks(playlistId: String, tracks: List<MidiTrack>): Int {
        val all = read()
        val index = all.indexOfFirst { it.id == playlistId }
        if (index < 0) return 0

        val existing = all[index].trackFileNames.toMutableSet()
        var added = 0
        for (track in tracks) {
            if (existing.add(track.fileName)) added++
        }
        if (added == 0) return 0

        all[index] = all[index].copy(trackFileNames = existing.toList())
        write(all)
        return added
    }

    fun removeTrack(playlistId: String, fileName: String): Boolean {
        val all = read()
        val index = all.indexOfFirst { it.id == playlistId }
        if (index < 0) return false
        val updated = all[index].trackFileNames.filterNot { it == fileName }
        if (updated.size == all[index].trackFileNames.size) return false
        all[index] = all[index].copy(trackFileNames = updated)
        write(all)
        return true
    }

    /** 把歌单成员解析成曲目对象；文件已不存在的项会被自动剔除并落盘。 */
    fun resolve(playlist: Playlist, library: List<MidiTrack>): List<MidiTrack> {
        val byName = library.associateBy { it.fileName }
        val resolved = playlist.trackFileNames.mapNotNull { byName[it] }
        if (resolved.size != playlist.trackFileNames.size) {
            // 曲库里的文件被删了，顺手清理掉失效引用
            val all = read()
            val index = all.indexOfFirst { it.id == playlist.id }
            if (index >= 0) {
                all[index] = all[index].copy(trackFileNames = resolved.map { it.fileName })
                write(all)
            }
        }
        return resolved
    }

    // ---------------- 持久化 ----------------

    private fun read(): MutableList<Playlist> {
        if (!file.exists()) return mutableListOf()
        return try {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray(KEY_PLAYLISTS) ?: return mutableListOf()
            val result = mutableListOf<Playlist>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val names = obj.optJSONArray("tracks") ?: JSONArray()
                val trackNames = ArrayList<String>(names.length())
                for (j in 0 until names.length()) {
                    names.optString(j).takeIf { it.isNotBlank() }?.let { trackNames.add(it) }
                }
                result.add(
                    Playlist(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = obj.optString("name").ifBlank { "歌单" },
                        trackFileNames = trackNames,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "读取歌单失败", e)
            mutableListOf()
        }
    }

    private fun write(playlists: List<Playlist>) {
        try {
            val array = JSONArray()
            for (playlist in playlists) {
                val names = JSONArray()
                playlist.trackFileNames.forEach { names.put(it) }
                array.put(
                    JSONObject().apply {
                        put("id", playlist.id)
                        put("name", playlist.name)
                        put("tracks", names)
                        put("createdAt", playlist.createdAt)
                    }
                )
            }
            val root = JSONObject().put(KEY_PLAYLISTS, array)
            file.writeText(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "保存歌单失败", e)
        }
    }

    /** 同名时自动加序号，避免列表里出现两个「我的最爱」。 */
    private fun uniqueName(name: String, excludeId: String? = null): String {
        val taken = read()
            .filter { it.id != excludeId }
            .map { it.name }
            .toSet()
        if (name !in taken) return name
        var i = 2
        while ("$name $i" in taken) i++
        return "$name $i"
    }
}
