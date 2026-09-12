package com.fumi.voice.cloud

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 访问云端后端的极简 HTTP 客户端（标准库实现）。
 *
 * 主用自定义域名（国内可直连），失败自动回退 workers.dev 兜底，并把当前可用域名记住。
 */
object CloudApi {

    // 自定义域名在前（国内优先），workers.dev 作为兜底
    private val BASES = listOf(
        "https://fusync.de5.net",
        "https://fufumidi-cloud-sync.fumivoice.workers.dev",
    )

    @Volatile
    private var activeBase: String = BASES[0]

    private fun candidates(): List<String> =
        listOf(activeBase) + BASES.filter { it != activeBase }

    /** 用于报错的短主机名，去掉协议前缀 */
    private fun hostOf(base: String): String =
        base.removePrefix("https://").removePrefix("http://")

    /** POST 一个 JSON 到指定路径，失败抛异常。成功返回响应 JSON。 */
    fun post(path: String, token: String?, body: JSONObject): JSONObject =
        send("POST", path, token, body)

    /** GET 一个 JSON，失败抛异常。成功返回响应 JSON。 */
    fun get(path: String, token: String?): JSONObject =
        send("GET", path, token, null)

    private fun send(method: String, path: String, token: String?, body: JSONObject?): JSONObject {
        // 逐个候选域名尝试；全部失败时把「每个域名 + 各自原因」一并报出，
        // 否则只抛最后一个域名的错误，会让人误以为只用过那一个（且无法判断主域名为何失败）。
        val failures = mutableListOf<String>()
        for (base in candidates()) {
            try {
                val out = request(base, method, path, token, body)
                if (base != activeBase) activeBase = base
                return out
            } catch (e: Exception) {
                failures.add(hostOf(base) + "（" + (e.message ?: "连接失败") + "）")
            }
        }
        throw RuntimeException("无法连接云服务，已尝试：" + failures.joinToString("；"))
    }

    private fun request(base: String, method: String, path: String, token: String?, body: JSONObject?): JSONObject {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 60_000
            useCaches = false
            // 仅在有请求体时开 doOutput：GET 置 true 会被 HttpURLConnection 改写成 POST
            if (body != null) doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(StandardCharsets.UTF_8) } ?: ""
            parseJsonOrThrow(code, text)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 解析响应；非 JSON 时抛异常。
     *
     * 非 JSON 多为网关错误页 / WAF 挑战页 / 运营商劫持页，或 Worker 超限（Error 1102）——
     * 这类失败源于链路而非业务，必须抛出去让上层换备用域名重试；
     * 若当成"业务错误"直接返回，就再也不会尝试第二个域名。
     */
    private fun parseJsonOrThrow(code: Int, text: String): JSONObject {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw RuntimeException("HTTP $code 空响应")
        return try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            throw RuntimeException("HTTP $code 非 JSON 响应：" + snippet(trimmed))
        }
    }

    /** 错误提示里只保留一小段响应内容，避免把整页 HTML 塞进界面 */
    private fun snippet(text: String): String {
        val s = text.replace(Regex("\\s+"), " ").trim()
        return if (s.length <= 120) s else s.take(120) + "…"
    }

    fun isOk(o: JSONObject): Boolean = o.optBoolean("ok", false)

    fun errorOf(o: JSONObject): String? = o.optString("error").takeIf { it.isNotBlank() }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun b64ToBytes(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    /** 把一段不完整的 base64 安全转换成字节（容错应答中带软回车等）。 */
    fun safeB64ToBytes(s: String): ByteArray =
        try { b64ToBytes(s) } catch (e: Exception) { Base64.decode(s.filterNot { it.isWhitespace() }, Base64.NO_WRAP) }
}