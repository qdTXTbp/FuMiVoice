package com.fumi.voice.library

import com.fumi.voice.model.MidiTrack

/**
 * M3U 播放列表的读写。
 *
 * 只做纯文本转换，不碰文件系统，格式对不对可以单独核对。
 *
 * 写出的路径用**文件名**而不是绝对路径：曲库文件都在应用私有目录下，
 * 绝对路径换台设备就失效；文件名放在同目录的 m3u 里正好是一条可用的相对路径，
 * 而且别的播放器也能认出同一批文件。
 */
object M3uCodec {

    private const val HEADER = "#EXTM3U"

    /**
     * 生成 m3u 文本。
     *
     * 带上 #EXTINF 是为了让别的播放器能显示时长与曲名；
     * 不带也能播，但列表里会是一串光秃秃的文件名。
     */
    fun build(tracks: List<MidiTrack>): String = buildString {
        appendLine(HEADER)
        for (track in tracks) {
            val seconds = track.durationMs / 1000
            // 显示名里的换行/回车会把一行撑成两行，破坏格式，先压掉
            val label = displayName(track).replace('\r', ' ').replace('\n', ' ')
            appendLine("#EXTINF:$seconds,$label")
            appendLine(track.fileName)
        }
    }

    /**
     * 解析 m3u，按顺序返回其中的条目（可能是文件名，也可能是路径）。
     *
     * 跳过空行与 `#` 开头的注释行。trim() 会一并去掉行尾的 `\r`——
     * Windows 导出的 m3u 基本都是 CRLF，不裁掉的话最后那个文件名永远对不上曲库。
     */
    fun parse(text: String): List<String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

    /** 从条目里取出文件名，用来和曲库比对（兼容 Windows 的反斜杠路径）。 */
    fun fileNameOf(entry: String): String =
        entry.replace('\\', '/').substringAfterLast('/').trim()

    /** `艺术家 - 曲名`，没有艺术家时只给曲名。 */
    private fun displayName(track: MidiTrack): String =
        if (track.artist.isNullOrBlank()) track.title else "${track.artist} - ${track.title}"
}
