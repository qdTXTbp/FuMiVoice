package com.fumi.voice.cloud

import android.content.Context
import com.fumi.voice.library.MidiLibraryManager
import com.fumi.voice.library.PlaylistManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 登录/注册结果；conflict=true 表示本机与云端都有存档，需用户选择保留哪一侧。 */
data class AuthResult(
    val ok: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
    val localN: Int = 0,
    val cloudN: Int = 0,
    /** 冲突对比用：本机/云端的曲目数 */
    val localSongs: Int = 0,
    val cloudSongs: Int = 0,
)

/** 一次同步的结果。 */
data class CloudSyncResult(
    val ok: Boolean = false,
    val error: String? = null,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    /** 本地文件读不到、因而无法备份的曲目数 */
    val missing: Int = 0,
)

/** 手动同步前的存档规模：本机与云端各有多少曲目 / 歌单。 */
data class ArchiveCounts(
    val ok: Boolean = false,
    val error: String? = null,
    val localSongs: Int = 0,
    val localPlaylists: Int = 0,
    val cloudSongs: Int = 0,
    val cloudPlaylists: Int = 0,
)

/**
 * 云同步管理器：
 * - 账号（邮箱+密码）：登录/注册/登出/状态。token 存 SharedPreferences。
 * - 登录时对本机与云端存档做对齐：冲突则让上层选择；否则自动 push/pull 建立或合并存档。
 * - 曲目云 id 采用「文件名」，playlist 的 songIds 即文件名列表，与本地曲库引用方式一致。
 */
class CloudSyncManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("fumi_cloud", Context.MODE_PRIVATE)

    private companion object {
        /** 每批搬运的曲目数，与后端 MAX_BATCH 对应 */
        const val BATCH = 6
    }

    var deviceId: String = ""
        private set
    var token: String = ""
        private set
    var email: String = ""
        private set

    init {
        token = prefs.getString("token", "") ?: ""
        email = prefs.getString("email", "") ?: ""
        deviceId = prefs.getString("deviceId", "") ?: ""
        if (deviceId.isBlank()) {
            deviceId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("deviceId", deviceId).apply()
        }
    }

    val isLoggedIn: Boolean get() = token.isNotBlank()

    fun register(email: String, password: String, turnstile: String? = null): AuthResult =
        doAuth("/auth/register", email, password, turnstile)

    fun login(email: String, password: String, turnstile: String? = null): AuthResult =
        doAuth("/auth/login", email, password, turnstile)

    private fun midiCount(): Int {
        val re = Regex("\\.(mid|midi|kar|rmi|smf)$", RegexOption.IGNORE_CASE)
        return MidiLibraryManager(context).list().count { re.containsMatchIn(it.fileName) }
    }

    /** 记录本设备已关联（同步过）的账号：同一账号再次登录不再误报冲突 */
    private fun markLinked(userId: String) {
        if (userId.isBlank()) return
        val linked = prefs.getStringSet("linkedUsers", emptySet()) ?: emptySet()
        if (userId !in linked) prefs.edit().putStringSet("linkedUsers", linked + userId).apply()
    }

    private fun doAuth(path: String, email: String, password: String, turnstile: String?): AuthResult {
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("turnstile", turnstile?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("deviceId", deviceId)
        }
        val res = try {
            CloudApi.post(path, null, body)
        } catch (e: Exception) {
            return AuthResult(error = "网络错误：${e.message}")
        }
        if (!CloudApi.isOk(res)) return AuthResult(error = CloudApi.errorOf(res) ?: "请求失败")
        val t = res.optString("token")
        if (t.isBlank()) return AuthResult(error = "服务器未发放会话")

        val myId = res.optString("userId")
        save(t, res.optString("email", email), myId)

        // 登录后对齐存档
        val cloudPls = res.optInt("cloudPlaylists", 0)
        val cloudSongs = res.optInt("cloudSongs", 0)
        val localPls = PlaylistManager(context).list().count { it.trackFileNames.isNotEmpty() }
        val localSongs = midiCount()
        // 有曲目或有歌单都算"有存档"（只有曲目没有歌单也要能建档）
        val hasLocal = localSongs > 0 || localPls > 0
        val hasCloud = cloudSongs > 0 || cloudPls > 0
        val linked = prefs.getStringSet("linkedUsers", emptySet()) ?: emptySet()

        if (myId.isNotBlank() && myId in linked) {
            // 本设备同步过该账号：正常合并（云空则本机当首次存档推上去）
            val mode = if (!hasCloud && hasLocal) "push" else "merge"
            sync(mode)
            return AuthResult(ok = true)
        }

        // 首次在本设备登录该账号：两边都有存档 -> 让用户选择保留哪一侧
        if (hasLocal && hasCloud) {
            return AuthResult(
                ok = true, conflict = true,
                localN = localPls, cloudN = cloudPls,
                localSongs = localSongs, cloudSongs = cloudSongs,
            )
        }

        // 自动建立 / 拉取
        when {
            !hasCloud && hasLocal -> sync("push")
            hasCloud && !hasLocal -> sync("pull")
        }
        markLinked(myId)
        return AuthResult(ok = true)
    }

    /** choose = 'local'（保留本机，覆盖云端）| 'cloud'（保留云端，覆盖本机） */
    fun resolveConflict(choose: String): AuthResult {
        val mode = if (choose == "cloud") "pull" else "push"
        val r = sync(mode)
        markLinked(prefs.getString("userId", "") ?: "")
        return AuthResult(ok = r.ok, error = r.error)
    }

    fun logout() {
        if (token.isNotBlank()) {
            runCatching { CloudApi.post("/auth/logout", token, JSONObject()) }
        }
        clear()
    }

    /** 读取本机与云端存档规模，供「立即同步」前让用户选择以哪一侧为准。请在 IO 线程调用。 */
    fun counts(): ArchiveCounts {
        val localSongs = midiCount()
        val localPls = PlaylistManager(context).list().count { it.trackFileNames.isNotEmpty() }
        if (!isLoggedIn) {
            return ArchiveCounts(error = "未登录", localSongs = localSongs, localPlaylists = localPls)
        }
        val res = try {
            CloudApi.get("/counts", token)
        } catch (e: Exception) {
            return ArchiveCounts(
                error = "网络错误：${e.message}",
                localSongs = localSongs, localPlaylists = localPls,
            )
        }
        if (!CloudApi.isOk(res)) {
            return ArchiveCounts(
                error = CloudApi.errorOf(res) ?: "读取存档信息失败",
                localSongs = localSongs, localPlaylists = localPls,
            )
        }
        return ArchiveCounts(
            ok = true,
            localSongs = localSongs, localPlaylists = localPls,
            cloudSongs = res.optInt("cloudSongs", 0),
            cloudPlaylists = res.optInt("cloudPlaylists", 0),
        )
    }

    /** 双向同步。mode = merge | push | pull。请在 IO 线程调用。
     *
     *  /sync 只搬元数据；MIDI 字节走 /songs/put、/songs/get 分批传输，
     *  否则 500+ 首曲库会因单次请求/响应过大而在中途失败（此前 526 首只成功 121 首）。
     *
     *  onProgress 会按阶段回调中文文案（如「已上传 12/128 首…」），供界面显示同步进度；
     *  分批传输上百首可能持续数分钟，没有反馈时看起来就像卡死。
     */
    fun sync(mode: String = "merge", onProgress: ((String) -> Unit)? = null): CloudSyncResult {
        if (!isLoggedIn) return CloudSyncResult(error = "未登录")
        val library = MidiLibraryManager(context)
        val playlistManager = PlaylistManager(context)

        val midiRe = Regex("\\.(mid|midi|kar|rmi|smf)$", RegexOption.IGNORE_CASE)

        // ---- 曲目元数据：不读字节（读盘慢且无必要，只有确定要上传时才读）----
        onProgress?.invoke("正在读取本机曲库…")
        val tracks = library.list().filter { midiRe.containsMatchIn(it.fileName) }
        val trackByKey = tracks.associateBy { it.fileName }
        val localNames = mutableSetOf<String>()
        val metaByKey = HashMap<String, JSONObject>()
        val updatedByKey = HashMap<String, Long>()
        val songsArray = JSONArray()
        for (track in tracks) {
            val f = File(track.path)
            localNames.add(track.fileName)
            updatedByKey[track.fileName] = f.lastModified()
            val meta = JSONObject().apply {
                put("title", track.title)
                put("size", f.length())
            }
            metaByKey[track.fileName] = meta
            songsArray.put(JSONObject().apply {
                put("id", track.fileName)
                put("name", track.fileName)
                put("meta", meta)
                put("updatedAt", f.lastModified())
                put("deleted", false)
            })
        }

        val playlistsArray = JSONArray()
        val localPlIds = mutableSetOf<String>()
        playlistManager.list().forEach { p ->
            localPlIds.add(p.id)
            playlistsArray.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("songIds", JSONArray(p.trackFileNames))
                put("updatedAt", p.createdAt)
                put("deleted", false)
            })
        }

        // 本地已删除、但此前同步过的项：上报墓碑，让删除传播到其他设备
        val knownSongs = prefs.getStringSet("knownSongs", emptySet()) ?: emptySet()
        val knownPls = prefs.getStringSet("knownPls", emptySet()) ?: emptySet()
        if (mode != "pull") {
            val ts = System.currentTimeMillis()
            for (k in knownSongs) {
                if (k !in localNames) songsArray.put(JSONObject().apply {
                    put("id", k); put("name", k); put("meta", JSONObject.NULL)
                    put("updatedAt", ts); put("deleted", true)
                })
            }
            for (k in knownPls) {
                if (k !in localPlIds) playlistsArray.put(JSONObject().apply {
                    put("id", k); put("name", ""); put("songIds", JSONArray())
                    put("updatedAt", ts); put("deleted", true)
                })
            }
        }

        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("mode", mode)
            put("songs", songsArray)
            put("playlists", playlistsArray)
        }

        onProgress?.invoke("本机 ${tracks.size} 首，正在与云端比对…")
        val res = try {
            CloudApi.post("/sync", token, payload)
        } catch (e: Exception) {
            return CloudSyncResult(error = "网络错误：${e.message}")
        }
        if (!CloudApi.isOk(res)) return CloudSyncResult(error = CloudApi.errorOf(res) ?: "同步失败")

        // ---- 云端元数据 ----
        val cloudSongs = res.optJSONArray("songs") ?: JSONArray()
        val hasData = HashMap<String, Boolean>()
        val cloudUpdated = HashMap<String, Long>()
        for (i in 0 until cloudSongs.length()) {
            val s = cloudSongs.optJSONObject(i) ?: continue
            val id = s.optString("id")
            if (id.isBlank()) continue
            hasData[id] = s.optBoolean("hasData", false)
            cloudUpdated[id] = s.optLong("updatedAt", 0)
        }

        // ---- 云端全量清单（含未变更项）----
        // 上面的 songs 只含"有变化"的曲目；未变更曲目的 hasData 无从得知，
        // 会被当成"云端没有"从而每次同步都重传整库。服务端因此额外下发 cloudMeta；
        // 旧服务端不下发时退回原判定。
        val cloudMetaArr = res.optJSONArray("cloudMeta")
        val haveCloudAll = cloudMetaArr != null
        val cloudAllData = HashMap<String, Boolean>()
        val cloudAllUpdated = HashMap<String, Long>()
        for (i in 0 until (cloudMetaArr?.length() ?: 0)) {
            val s = cloudMetaArr?.optJSONObject(i) ?: continue
            val id = s.optString("id")
            if (id.isBlank()) continue
            cloudAllData[id] = s.optBoolean("hasData", false)
            cloudAllUpdated[id] = s.optLong("updatedAt", 0)
        }

        // ---- 分批上传本地新增 / 云端只有空壳 / 本地更新的曲目 ----
        val toUpload = mutableListOf<String>()
        if (mode != "pull") {
            for (t in tracks) {
                val id = t.fileName
                val sv = if (haveCloudAll) cloudAllUpdated[id] else cloudUpdated[id]
                val hd = if (haveCloudAll) cloudAllData[id] else hasData[id]
                if (sv == null || hd != true || updatedByKey.getOrDefault(id, 0L) > sv) toUpload.add(id)
            }
        }
        var uploaded = 0
        var missing = 0
        if (toUpload.isNotEmpty()) onProgress?.invoke("准备上传 ${toUpload.size} 首…")
        else if (mode != "pull") onProgress?.invoke("本机曲目均无改动，无需上传")
        for ((idx, chunk) in toUpload.chunked(BATCH).withIndex()) {
            val arr = JSONArray()
            for (id in chunk) {
                val t = trackByKey[id] ?: continue
                val bytes = runCatching { File(t.path).readBytes() }.getOrNull()
                if (bytes == null || bytes.isEmpty()) { missing++; continue }
                arr.put(JSONObject().apply {
                    put("id", id); put("name", id)
                    put("meta", metaByKey[id] ?: JSONObject())
                    put("updatedAt", updatedByKey.getOrDefault(id, 0L))
                    put("data", CloudApi.b64(bytes))
                })
            }
            if (arr.length() == 0) continue
            val r = try {
                CloudApi.post("/songs/put", token, JSONObject().put("songs", arr))
            } catch (e: Exception) {
                return CloudSyncResult(error = "网络错误：${e.message}", uploaded = uploaded, missing = missing)
            }
            if (!CloudApi.isOk(r)) return CloudSyncResult(error = CloudApi.errorOf(r) ?: "曲目上传失败", uploaded = uploaded, missing = missing)
            uploaded += r.optInt("saved", 0)
            onProgress?.invoke("已上传 ${minOf((idx + 1) * BATCH, toUpload.size)}/${toUpload.size} 首…")
        }

        // ---- 应用云端曲目：墓碑删除，并挑出需要下载的 ----
        val keepSongIds = mutableSetOf<String>()
        val toDownload = mutableListOf<String>()
        val localNow = library.list().associateBy { it.fileName }
        for (i in 0 until cloudSongs.length()) {
            val s = cloudSongs.optJSONObject(i) ?: continue
            val id = s.optString("id")
            if (id.isBlank()) continue
            if (s.optBoolean("deleted", false)) {
                localNow[id]?.let { runCatching { File(it.path).delete() } }
                continue
            }
            keepSongIds.add(id)
            if (hasData[id] == true) {
                val local = localNow[id]
                if (local == null || cloudUpdated.getOrDefault(id, 0L) > runCatching { File(local.path).lastModified() }.getOrDefault(0L)) {
                    toDownload.add(id)
                }
            }
        }

        // ---- 分批下载字节 ----
        var downloaded = 0
        if (toDownload.isNotEmpty()) onProgress?.invoke("准备下载 ${toDownload.size} 首…")
        for ((idx, chunk) in toDownload.chunked(BATCH).withIndex()) {
            val r = try {
                CloudApi.post("/songs/get", token, JSONObject().put("ids", JSONArray(chunk)))
            } catch (e: Exception) {
                return CloudSyncResult(error = "网络错误：${e.message}", uploaded = uploaded, downloaded = downloaded, missing = missing)
            }
            if (!CloudApi.isOk(r)) {
                return CloudSyncResult(error = CloudApi.errorOf(r) ?: "曲目下载失败", uploaded = uploaded, downloaded = downloaded, missing = missing)
            }
            val got = r.optJSONArray("songs") ?: JSONArray()
            for (j in 0 until got.length()) {
                val item = got.optJSONObject(j) ?: continue
                val id = item.optString("id")
                val data = item.optString("data")
                if (id.isBlank() || data.isBlank()) continue
                val dest = File(library.cloudDir, id)
                dest.parentFile?.mkdirs()
                dest.writeBytes(CloudApi.safeB64ToBytes(data))
                downloaded++
                onProgress?.invoke("已下载 ${minOf((idx + 1) * BATCH, toDownload.size)}/${toDownload.size} 首…")
            }
        }

        // ---- 应用下发歌单 ----
        onProgress?.invoke("正在写入歌单…")
        val keepPlayIds = mutableSetOf<String>()
        val retPlaylists = res.optJSONArray("playlists") ?: JSONArray()
        for (i in 0 until retPlaylists.length()) {
            val p = retPlaylists.optJSONObject(i) ?: continue
            if (p.optBoolean("deleted", false)) continue
            keepPlayIds.add(p.optString("id"))
            val songIdsJson = p.optJSONArray("songIds") ?: JSONArray()
            val trackNames = (0 until songIdsJson.length()).map { songIdsJson.optString(it) }.filter { it.isNotBlank() }
            playlistManager.upsertCloud(p.optString("id"), p.optString("name"), trackNames)
        }

        // pull 模式：本机被整体替换为云端
        if (mode == "pull") {
            library.list().forEach { t -> if (t.fileName !in keepSongIds) runCatching { File(t.path).delete() } }
            playlistManager.list().forEach { p -> if (p.id !in keepPlayIds) playlistManager.delete(p.id) }
        }

        // 记录本次已知集合（供下次生成墓碑）
        val knownSongsNext = if (mode == "pull") localNames.toMutableSet() else (knownSongs + localNames + keepSongIds).toMutableSet()
        val knownPlsNext = if (mode == "pull") localPlIds.toMutableSet() else (knownPls + localPlIds + keepPlayIds).toMutableSet()
        prefs.edit().putStringSet("knownSongs", knownSongsNext).putStringSet("knownPls", knownPlsNext).apply()

        onProgress?.invoke("同步完成")
        return CloudSyncResult(ok = true, uploaded = uploaded, downloaded = downloaded, missing = missing)
    }

    private fun save(t: String, e: String, userId: String) {
        token = t; email = e
        prefs.edit().putString("token", t).putString("email", e).putString("userId", userId).apply()
    }

    private fun clear() {
        token = ""; email = ""
        prefs.edit().remove("token").remove("email").apply()
    }
}