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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fumi.voice.R
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
    onOpenSettings: () -> Unit = {},
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
        if (email.isBlank() || password.isBlank()) { error = context.getString(R.string.cloud_enter_credentials); return }
        busy = true; error = null; lastResult = null; conflict = null
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                if (register) manager.register(email, password, tsToken)
                else manager.login(email, password, tsToken)
            }
            busy = false
            when {
                !res.ok -> {
                    error = res.error ?: context.getString(R.string.cloud_op_failed)
                    // 服务端要求人机验证时自动弹出验证框，否则用户不知道要先完成哪一步
                    if (res.error?.contains("人机验证") == true) showTs = true
                }
                res.conflict -> conflict = res // 本机与云端都有存档：让用户选择保留哪一侧
                else -> { loggedIn = true; password = ""; message = if (register) context.getString(R.string.cloud_registered) else context.getString(R.string.cloud_logged_in); onRefresh() }
            }
        }
    }

    fun resolveConflict(choose: String) {
        busy = true; error = null
        scope.launch {
            val res = withContext(Dispatchers.IO) { manager.resolveConflict(choose) }
            busy = false
            if (res.ok) { conflict = null; loggedIn = true; password = ""; message = context.getString(R.string.cloud_kept_side, context.getString(if (choose == "cloud") R.string.cloud_side_cloud else R.string.cloud_side_local)); onRefresh() }
            else error = res.error ?: context.getString(R.string.cloud_op_failed)
        }
    }

    /** 点「立即同步」：先拉取两侧规模并弹出选择框 */
    fun askSync() {
        busy = true; error = null; lastResult = null
        scope.launch {
            val c = withContext(Dispatchers.IO) { manager.counts() }
            busy = false
            counts = c
            if (!c.ok) error = c.error ?: context.getString(R.string.cloud_read_archive_failed)
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
                message = context.getString(R.string.cloud_sync_done, res.uploaded, res.downloaded)
                showSyncChoice = false
                onRefresh()
            } else error = res.error ?: context.getString(R.string.cloud_sync_failed)
        }
    }

    fun doLogout() {
        scope.launch {
            withContext(Dispatchers.IO) { manager.logout() }
            loggedIn = false; password = ""; message = context.getString(R.string.cloud_logged_out)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.tab_cloud),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // 设置入口挂在云同步页：这一页是账号与云服务，设置同属「应用级」操作。
            // 顶栏那一排图标已经给了「检查更新」，再挤一个会看不清，所以放在标题行右侧。
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_open),
                )
            }
        }
        Text(
            stringResource(R.string.cloud_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }

        if (conflict != null) {
            // 冲突：本机与云端都有存档，需选择保留哪一侧（另一侧将被覆盖）
            Text(stringResource(R.string.cloud_conflict_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.cloud_local_archive, conflict!!.localSongs, conflict!!.localN) +
                    stringResource(R.string.cloud_cloud_archive, conflict!!.cloudSongs, conflict!!.cloudN) +
                    stringResource(R.string.cloud_conflict_choice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { resolveConflict("cloud") }, enabled = !busy) { Text(stringResource(R.string.cloud_keep_cloud)) }
                Button(onClick = { resolveConflict("local") }, enabled = !busy) { Text(if (busy) stringResource(R.string.common_processing) else stringResource(R.string.cloud_keep_local)) }
            }
        } else if (loggedIn) {
            OutlinedTextField(
                value = email,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.cloud_signed_in_as)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { askSync() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) stringResource(R.string.cloud_syncing) else stringResource(R.string.cloud_sync_now))
            }
            // 同步进度：阶段文案 + 已用时，避免长时间没有反馈时看起来像卡死
            if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        progressText ?: stringResource(R.string.cloud_syncing_progress),
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
                    stringResource(R.string.cloud_result_summary, it.uploaded, it.downloaded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (it.missing > 0) {
                    Text(
                        stringResource(R.string.cloud_missing_warning, it.missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = { doLogout() }, enabled = !busy) { Text(stringResource(R.string.cloud_sign_out)) }
        } else {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.cloud_email)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.cloud_password)) },
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
                    Text(if (tsToken.isNotBlank()) stringResource(R.string.cloud_captcha_done) else stringResource(R.string.cloud_captcha_go))
                }
                val hint = tsErr
                if (hint != null) {
                    Text(hint, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { doAuth(false) }, enabled = !busy) { Text(stringResource(R.string.cloud_sign_in)) }
                OutlinedButton(onClick = { doAuth(true) }, enabled = !busy) { Text(stringResource(R.string.cloud_sign_up)) }
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
            title = { Text(stringResource(R.string.cloud_pick_archive)) },
            text = {
                val c = counts
                if (c != null && c.ok) {
                    Column {
                        Text(stringResource(R.string.cloud_local_archive_one, c.localSongs, c.localPlaylists), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.cloud_cloud_archive_one, c.cloudSongs, c.cloudPlaylists), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.cloud_pick_archive_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (cloudEmpty) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.cloud_cloud_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    Text(if (busy) stringResource(R.string.cloud_reading_archive) else stringResource(R.string.cloud_read_failed))
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { doSync("pull") }, enabled = !busy && !cloudEmpty) { Text(stringResource(R.string.cloud_use_cloud)) }
                    Button(onClick = { doSync("push") }, enabled = !busy) { Text(if (busy) stringResource(R.string.cloud_syncing) else stringResource(R.string.cloud_use_local)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncChoice = false }, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
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
        title = { Text(stringResource(R.string.cloud_captcha_title)) },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
