package com.fumi.voice.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.fumi.voice.MainActivity
import com.fumi.voice.R
import com.fumi.voice.widget.FumiWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 播放前台服务 + MediaSession。
 *
 * 使用平台自带的 android.media.session.MediaSession 与 Notification.MediaStyle，
 * 不引入 media3 —— 音频由 BASS 输出，MediaSession 只承担"对外播报状态 + 接收传输控制"。
 * 这样通知栏、蓝牙耳机、锁屏、车机的播放控制都能联动。
 */
class PlaybackService : Service() {

    companion object {
        private const val CHANNEL_ID = "fumi_playback"
        private const val CHANNEL_NAME = "播放控制"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_PLAY_PAUSE = "com.fumi.voice.action.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.fumi.voice.action.NEXT"
        private const val ACTION_PREV = "com.fumi.voice.action.PREV"
        private const val ACTION_STOP = "com.fumi.voice.action.STOP"

        /**
         * 播放时小部件里瀑布的刷新间隔，见 [maybeRefreshWidget]。
         *
         * RemoteViews 做不了真正的逐帧动画，只能定时重推一张位图。250ms（4fps）是
         * 折中：再快就是每秒几 MB 的跨进程推图，桌面会被拖累；再慢就一眼看出在跳帧。
         */
        private const val WIDGET_INTERVAL_MS = 250L

        /** 播放开始时调用，服务会立即转为前台并把通知挂上去。 */
        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }

    private lateinit var session: MediaSession
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var collectJob: Job? = null

    private var lastNotificationAt = 0L
    private var lastWidgetAt = 0L
    private var lastSnapshot: PlayerUiState? = null

    private val player get() = (application as com.fumi.voice.App).player

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        setUpMediaSession()

        collectJob = scope.launch {
            player.state.collectLatest { state ->
                updateSessionPlaybackState(state)
                // 先算"是否有实质变化"，再交给下面两个方法——它们内部会更新 lastSnapshot
                val significant = isSignificantChange(state)
                maybeNotify(state, significant)
                maybeRefreshWidget(state, significant)
                if (state.playState == PlayState.STOPPED) {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> player.togglePlayPause()
            ACTION_NEXT -> player.next()
            ACTION_PREV -> player.previous()
            ACTION_STOP -> {
                player.stop()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // startForegroundService 要求 5 秒内进入前台，这里立即执行
        startForeground(NOTIFICATION_ID, buildNotification(player.state.value))
        lastNotificationAt = System.currentTimeMillis()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        session.isActive = false
        session.release()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------- MediaSession ----------------

    private fun setUpMediaSession() {
        session = MediaSession(this, "FuMiVoice").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = player.play()
                override fun onPause() = player.pause()
                override fun onStop() = player.stop()
                override fun onSkipToNext() = player.next()
                override fun onSkipToPrevious() = player.previous()
                override fun onSeekTo(pos: Long) = player.seekTo(pos)
            })
            setSessionActivity(openAppIntent())
            isActive = true
        }
    }

    private fun updateSessionPlaybackState(state: PlayerUiState) {
        val playbackState = when (state.playState) {
            PlayState.PLAYING -> PlaybackState.STATE_PLAYING
            PlayState.PAUSED -> PlaybackState.STATE_PAUSED
            PlayState.STOPPED -> PlaybackState.STATE_STOPPED
        }
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS

        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(playbackState, state.positionMs, if (state.playState == PlayState.PLAYING) 1f else 0f)
                .build()
        )

        val track = state.track
        session.setMetadata(
            android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, track?.title ?: "未在播放")
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, state.soundFontName ?: "FuMiVoice")
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
                .build()
        )
    }

    // ---------------- 通知 ----------------

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                    description = "显示当前 MIDI 曲目与播放控制"
                    setShowBadge(false)
                    enableVibration(false)
                }
            )
        }
    }

    /** 播放状态 / 曲目 / 循环模式的切换，与 30ms 一次的位置刷新区分开。 */
    private fun isSignificantChange(state: PlayerUiState): Boolean {
        val previous = lastSnapshot
        return previous == null ||
            previous.playState != state.playState ||
            previous.track?.path != state.track?.path ||
            previous.repeatMode != state.repeatMode
    }

    /** 位置每 30ms 变化一次，这里做节流，避免疯狂重建通知。 */
    private fun maybeNotify(state: PlayerUiState, significant: Boolean) {
        val now = System.currentTimeMillis()
        if (!significant && now - lastNotificationAt < 1000) return

        lastSnapshot = state
        lastNotificationAt = now
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(state))
    }

    /**
     * 小部件里的瀑布每秒推进一次。
     *
     * RemoteViews 没法逐帧动画，只能隔一段时间重画一张位图推给桌面，间隔取 1s 是折中：
     * 再快就变成频繁跨进程推图；再慢就看不出音符在往下走。
     *
     * 暂停 / 停止时不做定时刷新，只在真有变化时刷一次——静止的画面不必反复推。
     */
    private fun maybeRefreshWidget(state: PlayerUiState, significant: Boolean) {
        val now = System.currentTimeMillis()
        val playing = state.playState == PlayState.PLAYING
        val due = if (playing) now - lastWidgetAt >= WIDGET_INTERVAL_MS else significant
        if (!due) return
        lastWidgetAt = now
        FumiWidgetProvider.refresh(this)
    }

    private fun buildNotification(state: PlayerUiState): Notification {
        val playing = state.playState == PlayState.PLAYING
        val track = state.track

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(track?.title ?: "未在播放")
            .setContentText(state.soundFontName?.let { "音色：${it.substringBeforeLast('.')}" } ?: "FuMiVoice")
            .setContentIntent(openAppIntent())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                    "上一首",
                    serviceIntent(ACTION_PREV, 1),
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(
                        this,
                        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    ),
                    if (playing) "暂停" else "播放",
                    serviceIntent(ACTION_PLAY_PAUSE, 2),
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_next),
                    "下一首",
                    serviceIntent(ACTION_NEXT, 3),
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        if (state.durationMs > 0) {
            builder.setProgress(1000, ((state.positionMs * 1000) / state.durationMs).toInt(), false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }
}
