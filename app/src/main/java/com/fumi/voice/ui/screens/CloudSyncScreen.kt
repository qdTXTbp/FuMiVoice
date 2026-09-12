package com.fumi.voice.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fumi.voice.cloud.CloudSyncManager
import com.fumi.voice.cloud.ArchiveCounts
import com.fumi.voice.cloud.AuthResult
import com.fumi.voice.cloud.CloudSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 云同步页（第 4 个标签页）：账号登录/注册 + 把曲库/歌单与云端双向同步。
 */
@Composable
fun CloudSyncScreen(
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { CloudSyncManager(context) }

    var email by remember { mutableStateOf(manager.email) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(manager.isLoggedIn) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<CloudSyncResult?>(null) }
    var conflict by remember { mutableStateOf<AuthResult?>(null) }
    // 手动同步：先展示两侧存档规模，让用户选择以哪一份为准
    var counts by remember { mutableStateOf<ArchiveCounts?>(null) }
    var showSyncChoice by remember { mutableStateOf(false) }
    // 同步进度与已用时：分批传输上百首可能持续数分钟，没有反馈时看起来就像卡死
    var progressText by remember { mutableStateOf<String?>(null) }
    var elapsed by remember { mutableStateOf(0) }
    // 人机验证：Turnstile 无官方原生 SDK，用 WebView 加载托管页取 token
    var tsToken by remember { mutableStateOf("") }
    var tsErr by remember { mutableStateOf<String?>(null) }
    var showTs by remember { mutableStateOf(false) }

    fun doAuth(register: Boolean) {
        if (email.isBlank() || password.isBlank()) { error = "请输入邮箱与密码"; return }
        busy = true; error = null; lastResult = null; conflict = null
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                if (register) manager.register(email, password, tsToken)
                else manager.login(email, password, tsToken)
            }
            busy = false
            when {
                !res.ok -> {
                    error = res.error ?: "操作失败"
                    // 服务端要求人机验证时自动弹出验证框，否则用户不知道要先完成哪一步
                    if (res.error?.contains("人机验证") == true) showTs = true
                }
                res.conflict -> conflict = res // 本机与云端都有存档：让用户选择保留哪一侧
                else -> { loggedIn = true; password = ""; message = if (register) "注册成功，已登录" else "登录成功"; onRefresh() }
            }
        }
    }

    fun resolveConflict(choose: String) {
        busy = true; error = null
        scope.launch {
            val res = withContext(Dispatchers.IO) { manager.resolveConflict(choose) }
            busy = false
            if (res.ok) { conflict = null; loggedIn = true; password = ""; message = "已保留${if (choose == "cloud") "云端" else "本机"}存档"; onRefresh() }
            else error = res.error ?: "操作失败"
        }
    }

    /** 点「立即同步」：先拉取两侧规模并弹出选择框 */
    fun askSync() {
        busy = true; error = null; lastResult = null
        scope.launch {
            val c = withContext(Dispatchers.IO) { manager.counts() }
            busy = false
            counts = c
            if (!c.ok) error = c.error ?: "读取存档信息失败"
            showSyncChoice = true
        }
    }

    /** mode = push（本机覆盖云端）| pull（云端覆盖本机） */
    fun doSync(mode: String) {
        busy = true; error = null; lastResult = null; progressText = null
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                // 进度回调发生在 IO 线程，切回主线程再更新界面状态
                manager.sync(mode) { text -> scope.launch { progressText = text } }
            }
            lastResult = res; busy = false
            if (res.ok) {
                message = "同步完成：上传 ${res.uploaded} 曲，下载 ${res.downloaded} 曲"
                showSyncChoice = false
                onRefresh()
            } else error = res.error ?: "同步失败"
        }
    }

    fun doLogout() {
        scope.launch {
            withContext(Dispatchers.IO) { manager.logout() }
            loggedIn = false; password = ""; message = "已退出登录"
        }
    }

    // 同步期间每 0.5 秒刷新「已用时」：即使某一批很慢，秒数也在走，便于区分「在传」与「卡死」
    LaunchedEffect(busy) {
        if (!busy) { elapsed = 0; return@LaunchedEffect }
        val t0 = System.currentTimeMillis()
        elapsed = 0
        while (true) {
            elapsed = ((System.currentTimeMillis() - t0) / 1000).toInt()
            delay(500)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("云同步", style = MaterialTheme.typography.titleMedium)
        Text(
            "把手机曲库与歌单上传到云端，并接收电脑端的新增与修改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }

        if (conflict != null) {
            // 冲突：本机与云端都有存档，需选择保留哪一侧（另一侧将被覆盖）
            Text("检测到歌单存档冲突", style = MaterialTheme.typography.titleSmall)
            Text(
                "本机存档：${conflict!!.localSongs} 曲 / ${conflict!!.localN} 个歌单\n" +
                    "云端存档：${conflict!!.cloudSongs} 曲 / ${conflict!!.cloudN} 个歌单\n" +
                    "请选择保留哪一份；选择后另一份将被覆盖。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { resolveConflict("cloud") }, enabled = !busy) { Text("保留云端") }
                Button(onClick = { resolveConflict("local") }, enabled = !busy) { Text(if (busy) "处理中…" else "保留本机") }
            }
        } else if (loggedIn) {
            OutlinedTextField(
                value = email,
                onValueChange = {},
                readOnly = true,
                label = { Text("已登录账号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { askSync() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "同步中…" else "立即同步")
            }
            // 同步进度：阶段文案 + 已用时，避免长时间没有反馈时看起来像卡死
            if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        progressText ?: "正在同步…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${elapsed}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            lastResult?.let {
                Text(
                    "上传 ${it.uploaded} 曲 / 下载 ${it.downloaded} 曲",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (it.missing > 0) {
                    Text(
                        "注意：有 ${it.missing} 首曲目在本机读不到文件内容，已跳过备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = { doLogout() }, enabled = !busy) { Text("退出登录") }
        } else {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("邮箱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码（注册需 8 位以上）") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            // 人机验证（可选触发：未通过时服务端会拒绝，这里提前让用户拿到 token）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { showTs = true }, enabled = !busy) {
                    Text(if (tsToken.isNotBlank()) "人机验证已完成" else "进行人机验证")
                }
                val hint = tsErr
                if (hint != null) {
                    Text(hint, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { doAuth(false) }, enabled = !busy) { Text("登录") }
                OutlinedButton(onClick = { doAuth(true) }, enabled = !busy) { Text("注册") }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showSyncChoice) {
        // 立即同步前先选存档：本机=push（覆盖云端），云端=pull（覆盖本机）
        // 云端为空而本机有存档时禁用「使用云端存档」：pull 会以云端为基准覆盖本机，等于清空本机存档
        val c0 = counts
        val cloudEmpty = c0 != null && c0.ok &&
            (c0.cloudSongs + c0.cloudPlaylists) == 0 && (c0.localSongs + c0.localPlaylists) > 0
        AlertDialog(
            onDismissRequest = { if (!busy) showSyncChoice = false },
            title = { Text("选择要使用的存档") },
            text = {
                val c = counts
                if (c != null && c.ok) {
                    Column {
                        Text("本机存档：${c.localSongs} 曲 / ${c.localPlaylists} 个歌单", style = MaterialTheme.typography.bodySmall)
                        Text("云端存档：${c.cloudSongs} 曲 / ${c.cloudPlaylists} 个歌单", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "请选择以哪一份为准；选择后另一份将被覆盖。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (cloudEmpty) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "云端存档为空，使用云端会清空本机存档，已禁用该选项。请选择「使用本机存档」上传。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    Text(if (busy) "正在读取存档…" else "未能读取存档信息")
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { doSync("pull") }, enabled = !busy && !cloudEmpty) { Text("使用云端存档") }
                    Button(onClick = { doSync("push") }, enabled = !busy) { Text(if (busy) "同步中…" else "使用本机存档") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncChoice = false }, enabled = !busy) { Text("取消") }
            },
        )
    }

    if (showTs) {
        TurnstileDialog(
            onToken = { t -> tsToken = t; tsErr = null; showTs = false },
            onError = { m -> tsErr = m },
            onDismiss = { showTs = false },
        )
    }
}

/**
 * 人机验证对话框。
 *
 * Turnstile 没有官方原生 SDK，且会校验来源域名，因此用 WebView 加载
 * 我们域名下托管的验证页（https://fusync.de5.net/turnstile），
 * 页面通过注入的 JS 桥 AndroidBridge 回传 token。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TurnstileDialog(
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var web by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose { runCatching { web?.destroy() } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("人机验证") },
        text = {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onMessage(json: String) {
                                post {
                                    runCatching {
                                        val o = JSONObject(json)
                                        when (o.optString("type")) {
                                            "turnstile-token" -> onToken(o.optString("token"))
                                            "turnstile-error" -> onError(o.optString("error"))
                                        }
                                    }
                                }
                            }
                        }, "AndroidBridge")
                        loadUrl("https://fusync.de5.net/turnstile")
                        web = this
                    }
                },
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}