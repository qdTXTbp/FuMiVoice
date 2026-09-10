package com.fumi.voice.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.fumi.voice.App
import com.fumi.voice.MainActivity
import com.fumi.voice.R
import com.fumi.voice.player.PlayState
import com.fumi.voice.player.PlaybackService
import com.fumi.voice.ui.NoteData

private const val TAG = "FumiWidget"

private const val ACTION_TOGGLE = "com.fumi.voice.widget.TOGGLE"
private const val ACTION_NEXT = "com.fumi.voice.widget.NEXT"
private const val ACTION_PREV = "com.fumi.voice.widget.PREV"

private const val REQUEST_OPEN = 10
private const val REQUEST_PREV = 11
private const val REQUEST_TOGGLE = 12
private const val REQUEST_NEXT = 13

/**
 * 瀑布位图的尺寸上限。
 *
 * RemoteViews 要把位图跨 Binder 交给桌面，而 Binder 事务上限约 1MB；
 * 位图是 W×H×4 字节，按部件实际像素尺寸画很容易就冲到 1MB 以上
 * （250dp×110dp 在这台设备上约 750×330px ≈ 990KB），再乘上"可纵向拉伸"
 * 的 MAX_HEIGHT 就更夸张——结果就是桌面报「载入小部件时出现问题」。
 *
 * 这里把位图钳到 256×160（约 160KB，留足余量），好把刷新率提上去：
 * 帧率与体积是乘在一起的，体积减半才能在不加重 IPC 的前提下把帧率翻几倍。
 */
private const val MAX_BITMAP_WIDTH = 256
private const val MAX_BITMAP_HEIGHT = 160

/**
 * 桌面播放控制小部件。
 *
 * 用传统的 [AppWidgetProvider] + RemoteViews 而不是 Glance：
 * 这个部件只有一行文字加三个按钮，引入 glance-appwidget 会多带一套注解处理器与资源生成，
 * 收益却很小。音频本身由 BASS 在进程内输出，[PlaybackService] 只负责通知栏，
 * 所以即使前台服务被系统拒绝启动（后台启动限制），播放依然正常，只是少了通知。
 */
class FumiWidgetProvider : AppWidgetProvider() {

    companion object {
        /**
         * 刷新所有已放置的小部件。
         *
         * 由界面在播放状态变化时调用；没有放置任何小部件时直接返回，不做无谓工作。
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, FumiWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            ids.forEach { id -> render(context, manager, id) }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val player = player(context)
        if (player == null) {
            Log.w(TAG, "播放器尚未就绪，忽略 ${intent.action}")
            return
        }

        when (intent.action) {
            ACTION_TOGGLE -> {
                // 冷启动时没有队列可播，此时按钮等效于「打开应用」
                if (player.currentTrack == null) {
                    launchApp(context)
                } else {
                    player.togglePlayPause()
                    if (player.playState == PlayState.PLAYING) startServiceQuietly(context)
                }
            }

            ACTION_NEXT -> player.next()
            ACTION_PREV -> player.previous()
            else -> return
        }
        refresh(context)
    }

    /** 后台启动前台服务会被系统限制，失败不影响播放，只少了通知。 */
    private fun startServiceQuietly(context: Context) {
        try {
            PlaybackService.start(context)
        } catch (e: Exception) {
            Log.w(TAG, "前台服务启动被拒绝：${e.message}")
        }
    }
}

private fun player(context: Context) = (context.applicationContext as? App)?.player

private fun launchApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    context.startActivity(intent)
}

private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
    val views = RemoteViews(context.packageName, R.layout.widget_player)
    val playerRef = player(context)
    val state = playerRef?.state?.value
    val track = state?.track
    val playing = state?.playState == PlayState.PLAYING

    views.setTextViewText(
        R.id.widget_title,
        track?.title ?: context.getString(R.string.widget_no_track),
    )
    views.setTextViewText(
        R.id.widget_subtitle,
        when {
            track == null -> context.getString(R.string.widget_subtitle_placeholder)
            !track.artist.isNullOrBlank() -> track.artist
            else -> state?.soundFontName?.substringBeforeLast('.') ?: "FuMiVoice"
        },
    )
    views.setImageViewResource(
        R.id.widget_toggle,
        if (playing) R.drawable.widget_pause else R.drawable.widget_play,
    )

    renderWaterfall(
        context = context,
        manager = manager,
        widgetId = widgetId,
        views = views,
        notes = playerRef?.waterfallNotes.orEmpty(),
        positionMs = state?.positionMs ?: 0L,
    )

    views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
    views.setOnClickPendingIntent(R.id.widget_prev, broadcastIntent(context, ACTION_PREV, REQUEST_PREV))
    views.setOnClickPendingIntent(R.id.widget_toggle, broadcastIntent(context, ACTION_TOGGLE, REQUEST_TOGGLE))
    views.setOnClickPendingIntent(R.id.widget_next, broadcastIntent(context, ACTION_NEXT, REQUEST_NEXT))

    manager.updateAppWidget(widgetId, views)
}

/**
 * 把当前这一瞬的瀑布画成位图贴进小部件。
 *
 * 只有 MIDI 才有音符可画；音频与模块曲目把瀑布层和压暗层一起设成 GONE，
 * 布局就退回「曲名 + 三个按钮」的原样，不会留一块空白。
 */
private fun renderWaterfall(
    context: Context,
    manager: AppWidgetManager,
    widgetId: Int,
    views: RemoteViews,
    notes: List<NoteData>,
    positionMs: Long,
) {
    val bitmap = if (notes.isEmpty()) {
        null
    } else {
        runCatching {
            val (width, height) = widgetSize(context, manager, widgetId)
            Log.i(TAG, "小部件瀑布位图 ${width}x${height}（${width * height * 4 / 1024} KB）")
            WidgetWaterfall.render(notes, positionMs, width, height)
        }.getOrNull()
    }

    views.setViewVisibility(R.id.widget_waterfall, if (bitmap != null) View.VISIBLE else View.GONE)
    if (bitmap != null) views.setImageViewBitmap(R.id.widget_waterfall, bitmap)
}

/**
 * 瀑布位图的像素尺寸。
 *
 * 系统只给 dp 且只保证一个方向的 MIN / MAX，所以宽取 MIN、高取 MAX——
 * 这是各家启动器通用的近似做法。拿不到（首次添加时可能为 0）就退回默认值。
 *
 * 关键是把结果钳在 [MAX_BITMAP_WIDTH] × [MAX_BITMAP_HEIGHT] 以内：ImageView 是
 * fitXY，位图会被拉伸填满，所以画小一点完全没问题，而跨 Binder 的体积可控。
 */
private fun widgetSize(context: Context, manager: AppWidgetManager, widgetId: Int): Pair<Int, Int> {
    val density = context.resources.displayMetrics.density
    val options = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull()
    val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
    val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
    val width = if (widthDp > 0) (widthDp * density).toInt() else MAX_BITMAP_WIDTH
    val height = if (heightDp > 0) (heightDp * density).toInt() else MAX_BITMAP_HEIGHT
    return width.coerceIn(160, MAX_BITMAP_WIDTH) to height.coerceIn(96, MAX_BITMAP_HEIGHT)
}

private fun openAppIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun broadcastIntent(context: Context, action: String, requestCode: Int): PendingIntent {
    val intent = Intent(context, FumiWidgetProvider::class.java).setAction(action)
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
