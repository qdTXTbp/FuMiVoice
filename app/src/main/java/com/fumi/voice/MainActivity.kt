package com.fumi.voice

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fumi.voice.player.PlayState
import com.fumi.voice.ui.FuMiVoiceApp
import com.fumi.voice.ui.theme.Charcoal
import com.fumi.voice.ui.theme.FuMiVoiceTheme
import com.fumi.voice.util.AppLanguage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val midiUri = mutableStateOf<Uri?>(null)

    /** 是否处于小窗（画中画）模式；界面据此只保留音符瀑布。 */
    private val inPip = mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝也不影响播放，只是通知栏不显示 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotificationPermission()

        // 语言偏好必须在 setContent 之前落到 Resources 上，
        // 否则首帧仍按系统语言渲染，切换过语言的用户每次启动都会看到一次闪烁。
        AppLanguage.apply(this, AppLanguage(this).current(), force = true)

        val app = application as App
        midiUri.value = extractMidiUri(intent)

        // 播放中才允许系统在按 Home 时自动把画面缩成小窗
        lifecycleScope.launch {
            app.player.state.collect { state ->
                syncAutoEnterPip(state.playState == PlayState.PLAYING && !state.isPreview)
            }
        }

        setContent {
            FuMiVoiceTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Charcoal) {
                    FuMiVoiceApp(
                        app = app,
                        initialMidiUri = midiUri.value,
                        isInPip = inPip.value,
                        onEnterPip = { enterWaterfallPip() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractMidiUri(intent)?.let { midiUri.value = it }
    }

    /**
     * 系统配置变更（旋转、深浅色、系统语言）会把 Activity 的 Resources 重置回系统语言，
     * 把应用内选择的语言冲掉。这里强制重写一次，否则用户转个屏，界面就从英文变回中文。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.apply(this, AppLanguage(this).current(), force = true)
    }

    /** 从桌面按 Home 时自动进小窗，这样播放不会被打断。 */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && isPlaying()) {
            enterWaterfallPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    /**
     * 进入小窗，画面只保留音符瀑布。
     *
     * 比例取 16:9 —— 瀑布本来就是横向铺开的钢琴卷帘，
     * 小窗里保持横屏观感最合适。
     */
    private fun enterWaterfallPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(true)
        }
        runCatching { enterPictureInPictureMode(builder.build()) }
    }

    /**
     * Android 12 起可以声明"自动进入小窗"，
     * 这样用户按 Home 时系统会直接把画面缩成小窗，不需要我们自己拦截。
     */
    private fun syncAutoEnterPip(playing: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setAutoEnterEnabled(playing)
            .setSeamlessResizeEnabled(true)
            .build()
        runCatching { setPictureInPictureParams(params) }
    }

    private fun isPlaying(): Boolean {
        return (application as App).player.playState == PlayState.PLAYING
    }

    /** 通知栏控制需要 Android 13+ 的通知权限，拒绝也能正常播放。 */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** 从 ACTION_VIEW 意图里取出 MIDI 的 URI，支持 content:// 与 file://。 */
    private fun extractMidiUri(intent: Intent?): Uri? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val type = intent.type
        val lastName = uri.lastPathSegment?.lowercase().orEmpty()
        val looksLikeMidi = type?.contains("midi", ignoreCase = true) == true ||
            lastName.endsWith(".mid") ||
            lastName.endsWith(".midi")
        return if (looksLikeMidi) uri else null
    }
}
