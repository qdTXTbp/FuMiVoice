package com.fumi.voice.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.fumi.voice.ui.NoteData

/**
 * 桌面小部件里的音符瀑布。
 *
 * 这是给全屏那张 Composable 瀑布画的"缩略图"。RemoteViews 做不了逐帧动画，
 * 只能按固定间隔重画一张位图推给桌面，所以这里的目标是"一眼看出音符在往下落"，
 * 而不是像素级复刻。
 *
 * 与全屏版的两处有意差异：
 * - 时间窗固定 4 秒，不按小节换算。小部件的高度只有几十 dp，
 *   按小节换算会让快歌整屏都在飞速刷新，反而看不清落点。
 * - 琴键画成一条横带，不画黑白键——这个尺寸下黑白键只会糊成一团噪点。
 */
object WidgetWaterfall {

    /** 画面上方保留的时间跨度（毫秒）；越小滚动越快。 */
    private const val WINDOW_MS = 4000.0

    /** 琴键横带占的高度比例。留够高度才画得下黑键和 C 音名。 */
    private const val KEYBOARD_RATIO = 0.72f

    private const val BG_COLOR = 0xFF141419.toInt()
    private const val KEYBOARD_COLOR = 0xFFE8E8F0.toInt()
    private const val BLACK_KEY_COLOR = 0xFF1B1B24.toInt()
    private const val KEY_LABEL_COLOR = 0xFF5A6478.toInt()
    /** 与全屏瀑布的第一个通道色一致，保证两边看着是同一个东西。 */
    private const val NOTE_COLOR = 0xFF6C8CFF.toInt()
    private const val PLAYHEAD_COLOR = 0xFF6C8CFF.toInt()

    /**
     * 整张图最后压暗一层的透明度（约 25%）。
     *
     * 压暗直接烤进位图里，而不是在布局里再叠一个半透明 View：
     * RemoteViews 少一个视图就少一处可能 inflate 失败的地方。
     * 只压 25%——太重会把琴键也压灰，而琴键是这图上最认得出的一块。
     */
    private const val SCRIM_ALPHA = 0x40

    /** 一个八度里哪些是黑键（音级 1/3/6/8/10）。 */
    private fun isBlackKey(key: Int): Boolean = when (key.mod(12)) {
        1, 3, 6, 8, 10 -> true
        else -> false
    }

    /** 画一张瀑布快照；没有音符或尺寸非法时返回 null。 */
    fun render(notes: List<NoteData>, nowMs: Long, widthPx: Int, heightPx: Int): Bitmap? {
        if (notes.isEmpty() || widthPx <= 0 || heightPx <= 0) return null

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG_COLOR)

        val keyboardTop = heightPx * KEYBOARD_RATIO
        val trackHeight = keyboardTop
        if (trackHeight <= 0f) return bitmap

        // 音高 → 横坐标。用整首曲子的音域，而不是"当前窗口里出现的音"：
        // 否则音符进出画面时列宽跟着变，看起来像整排在左右抖。
        var minKey = Int.MAX_VALUE
        var maxKey = Int.MIN_VALUE
        for (note in notes) {
            if (note.key < minKey) minKey = note.key
            if (note.key > maxKey) maxKey = note.key
        }
        if (maxKey < minKey) return bitmap

        val columns = (maxKey - minKey + 1).coerceAtLeast(1)
        val columnWidth = widthPx.toDouble() / columns
        // 留出约 15% 间隙，相邻音符才分得开
        val barWidth = (columnWidth * 0.85).coerceAtLeast(1.0)

        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NOTE_COLOR }
        val rect = RectF()

        for (note in notes) {
            // 两端各自距"落到琴键"还有多久；负数表示这一端已经越过琴键
            val startOffset = note.startTimeMs - nowMs
            val endOffset = startOffset + note.durationMs
            // 整段还在窗口上方，或已经整段划过琴键 → 跳过
            if (startOffset > WINDOW_MS || endOffset < 0.0) continue

            // 时间越靠后越靠上：音符"先响的那端"在下（靠近琴键），
            // "后响的那端"在上。所以 top 要用 endOffset、bottom 要用 startOffset——
            // 反过来写会让 bottom < top，矩形恒为空，画面上一个音符都不会出现。
            val top = keyboardTop - (endOffset / WINDOW_MS) * trackHeight
            val bottom = keyboardTop - (startOffset / WINDOW_MS) * trackHeight
            val left = (note.key - minKey) * columnWidth

            rect.set(
                left.toFloat(),
                top.coerceAtLeast(0.0).toFloat(),
                (left + barWidth).toFloat(),
                bottom.coerceAtMost(keyboardTop.toDouble()).toFloat(),
            )
            if (rect.bottom <= rect.top) continue

            // 力度映射成不透明度，弱音不会和强音一样抢眼
            notePaint.alpha = (110 + note.velocity * 115 / 127).coerceIn(110, 225)
            val radius = (barWidth * 0.3).toFloat()
            canvas.drawRoundRect(rect, radius, radius, notePaint)
        }

        // 琴键：白键铺底 + 黑键覆盖。用和音符同一套"音高→横坐标"映射，
        // 音符才能正好落在自己该落的那一键上——这是"还原"最关键的一点。
        val keyHeight = heightPx - keyboardTop
        canvas.drawRect(
            0f, keyboardTop, widthPx.toFloat(), heightPx.toFloat(),
            Paint().apply { color = KEYBOARD_COLOR },
        )

        val blackKeyPaint = Paint().apply { color = BLACK_KEY_COLOR }
        for (key in minKey..maxKey) {
            if (!isBlackKey(key)) continue
            val left = (key - minKey) * columnWidth
            canvas.drawRect(
                left.toFloat(),
                keyboardTop,
                (left + columnWidth).toFloat(),
                // 黑键只占白键长度的一多半，键盘的纵深才出得来
                keyboardTop + keyHeight * 0.62f,
                blackKeyPaint,
            )
        }

        // C 音名：和全屏那张一致，方便一眼定位音区
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = KEY_LABEL_COLOR
            textSize = (keyHeight * 0.32f).coerceAtLeast(6f)
            textAlign = Paint.Align.CENTER
        }
        for (key in minKey..maxKey) {
            if (key % 12 != 0) continue
            val center = ((key - minKey) + 0.5) * columnWidth
            canvas.drawText("C${key / 12 - 1}", center.toFloat(), heightPx - 2f, labelPaint)
        }

        // 落点线：音符在它上面归零，视觉上有个"砸到这里"的参照
        canvas.drawRect(
            0f, keyboardTop - 1.5f, widthPx.toFloat(), keyboardTop + 1.5f,
            Paint().apply { color = PLAYHEAD_COLOR },
        )
        // 最后整体压暗，让叠在上面的曲名与按钮读得清
        canvas.drawColor(Color.argb(SCRIM_ALPHA, 0, 0, 0))
        return bitmap
    }
}
