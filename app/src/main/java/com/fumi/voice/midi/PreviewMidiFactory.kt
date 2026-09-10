package com.fumi.voice.midi

import java.io.ByteArrayOutputStream

/**
 * 生成乐器试听用的标准 MIDI 文件（SMF Format 0）。
 *
 * 为什么不新增 JNI 去实时触发 BASS_MIDI_StreamCreate + StreamEvent？
 * 因为生成 SMF 可以完整复用现有的 midiStreamCreateFile 通路：
 * 不需要新增、调试原生代码，试听与播放真实曲目走同一条路径，行为一致。
 */
object PreviewMidiFactory {

    private const val DIVISION = 480
    private const val MELODY_CHANNEL = 0
    /** MIDI 通道 10 为鼓组通道（此处是 0 基索引）。 */
    private const val DRUM_CHANNEL = 9

    /** 一个待写入事件。 */
    private data class N(val start: Int, val dur: Int, val key: Int, val vel: Int = 100)

    /** 旋律乐器试听：C 大调琶音 + 和弦，约 3 秒。 */
    fun instrumentPreview(program: Int): ByteArray {
        val notes = listOf(
            N(0, 420, 60, 96),      // C4
            N(480, 420, 64, 96),    // E4
            N(960, 420, 67, 96),    // G4
            N(1440, 420, 72, 104),  // C5
            N(1920, 900, 60, 88),   // C 大三和弦
            N(1920, 900, 64, 88),
            N(1920, 900, 67, 88),
            N(1920, 900, 72, 92),
        )
        return build(program, notes)
    }

    /** 鼓组试听：两小节基础节奏 + 桶鼓过门。 */
    fun drumPattern(): ByteArray {
        val notes = mutableListOf<N>()
        for (bar in 0 until 2) {
            val o = bar * 1920
            notes += N(o, 120, 49, 110)         // 强音镲
            notes += N(o, 120, 36, 115)         // 大鼓
            notes += N(o + 480, 120, 38, 106)   // 军鼓
            notes += N(o + 960, 120, 36, 112)   // 大鼓
            notes += N(o + 1440, 120, 38, 106)  // 军鼓
            notes += N(o + 1680, 160, 46, 96)   // 开放踩镲
            var t = o
            while (t < o + 1680) {              // 闭合踩镲走八分
                notes += N(t, 60, 42, 78)
                t += 240
            }
            if (bar == 1) {                      // 第二小节加过门
                notes += N(o + 1440, 110, 45, 102)
                notes += N(o + 1560, 110, 47, 102)
                notes += N(o + 1680, 160, 50, 104)
            }
        }
        return build(null, notes)
    }

    /** 单个打击乐部件的试听。 */
    fun drumHit(note: Int): ByteArray = build(null, listOf(N(0, 180, note, 112)))

    /** 单个旋律音，用于点琴键试听。 */
    fun singleNote(program: Int, key: Int): ByteArray =
        build(program, listOf(N(0, 900, key, 104)))

    // ---------------- SMF 写入 ----------------

    private fun build(program: Int?, notes: List<N>): ByteArray {
        val channel = if (program == null) DRUM_CHANNEL else MELODY_CHANNEL
        val noteOn = 0x90 or channel
        val noteOff = 0x80 or channel

        val events = mutableListOf<Pair<Int, ByteArray>>()
        // 速度：500000 微秒/四分音符 = 120 BPM
        events += 0 to byteArrayOf(0xFF.toByte(), 0x51, 0x03, 0x07, 0xA1.toByte(), 0x20)
        if (program != null) {
            events += 0 to byteArrayOf((0xC0 or channel).toByte(), program.toByte())
        }
        for (n in notes) {
            events += n.start to byteArrayOf(noteOn.toByte(), n.key.toByte(), n.vel.toByte())
            events += (n.start + n.dur) to byteArrayOf(noteOff.toByte(), n.key.toByte(), 0x40)
        }
        events.sortBy { it.first }

        val endTick = (notes.maxOfOrNull { it.start + it.dur } ?: 0) + DIVISION / 2

        val track = ByteArrayOutputStream()
        var cursor = 0
        for ((tick, payload) in events) {
            track.writeVarLen(tick - cursor)
            track.write(payload)
            cursor = tick
        }
        track.writeVarLen(endTick - cursor)
        track.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00)) // End of Track

        val trackBytes = track.toByteArray()
        val out = ByteArrayOutputStream()
        // MThd
        out.write("MThd".toByteArray(Charsets.US_ASCII))
        out.writeInt32(6)
        out.writeInt16(0)          // format 0
        out.writeInt16(1)          // 1 track
        out.writeInt16(DIVISION)
        // MTrk
        out.write("MTrk".toByteArray(Charsets.US_ASCII))
        out.writeInt32(trackBytes.size)
        out.write(trackBytes)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeInt32(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeInt16(value: Int) {
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    /** SMF 可变长度数值。 */
    private fun ByteArrayOutputStream.writeVarLen(value: Int) {
        var v = value
        var buffer = v and 0x7F
        v = v ushr 7
        while (v > 0) {
            buffer = (buffer shl 8) or ((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        while (true) {
            write(buffer and 0xFF)
            if (buffer and 0x80 != 0) buffer = buffer ushr 8 else break
        }
    }
}
