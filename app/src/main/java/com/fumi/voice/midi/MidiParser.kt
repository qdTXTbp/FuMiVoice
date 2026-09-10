package com.fumi.voice.midi

import java.io.InputStream

/**
 * Lightweight MIDI parser for extracting note timing data for the piano waterfall.
 * Does not handle playback (that's BASS's job), just extracts note on/off times.
 */
object MidiParser {

    data class NoteData(
        val tick: Long,
        val key: Int,
        val channel: Int,
        val velocity: Int,
        val durationTicks: Long,
    )

    fun parse(stream: InputStream): ParsedMidi {
        val data = stream.readBytes()
        var pos = 0

        // Header
        val header = String(data, pos, 4)
        pos += 4
        val headerLen = readInt32(data, pos); pos += 4
        val format = readInt16(data, pos); pos += 2
        val trackCount = readInt16(data, pos); pos += 2
        val ticksPerQuarter = readInt16(data, pos); pos += 2
        pos += headerLen - 6

        var tempo = 500000 // default 120 BPM (microseconds per quarter)
        val tempoChanges = mutableListOf<Pair<Long, Int>>()
        val sigChanges = mutableListOf<Pair<Long, Int>>()

        val allEvents = mutableListOf<MidiEvent>()

        // 每个通道的初始状态：只取文件中第一次出现的值，供混音台显示
        val channelPrograms = IntArray(16) { -1 }
        val channelVolumes = IntArray(16) { 100 }
        val channelPans = IntArray(16) { 64 }
        val firstCc7 = IntArray(16) { -1 }
        val firstCc10 = IntArray(16) { -1 }

        for (t in 0 until trackCount) {
            val trackHeader = String(data, pos, 4)
            pos += 4
            val trackLen = readInt32(data, pos); pos += 4
            val trackEnd = pos + trackLen

            var tick = 0L
            var lastStatus = 0

            while (pos < trackEnd) {
                // Delta time (variable-length)
                var delta = 0
                var b: Int
                do {
                    b = data[pos++].toInt() and 0xFF
                    delta = (delta shl 7) or (b and 0x7F)
                } while (b and 0x80 != 0)
                tick += delta

                var status = data[pos].toInt() and 0xFF
                if (status and 0x80 == 0) {
                    status = lastStatus
                } else {
                    lastStatus = status
                    pos++
                }

                val channel = status and 0x0F
                val eventType = status and 0xF0

                when (eventType) {
                    0x80 -> { // Note Off
                        val note = data[pos++].toInt() and 0xFF
                        val vel = data[pos++].toInt() and 0xFF
                        allEvents.add(MidiEvent(tick, channel, 0x80, note, vel))
                    }
                    0x90 -> { // Note On
                        val note = data[pos++].toInt() and 0xFF
                        val vel = data[pos++].toInt() and 0xFF
                        allEvents.add(MidiEvent(tick, channel, 0x90, note, vel))
                    }
                    0xA0 -> { // Poly Aftertouch
                        pos += 2
                    }
                    0xB0 -> { // CC
                        val cc = data[pos++].toInt() and 0xFF
                        val v = data[pos++].toInt() and 0xFF
                        // 只记首次出现的值，等价于"文件开始时这个通道设成了什么"
                        when (cc) {
                            7 -> if (firstCc7[channel] == -1) {
                                firstCc7[channel] = v
                                channelVolumes[channel] = v
                            }

                            10 -> if (firstCc10[channel] == -1) {
                                firstCc10[channel] = v
                                channelPans[channel] = v
                            }
                        }
                    }
                    0xC0 -> { // Program Change
                        val program = data[pos++].toInt() and 0xFF
                        if (channelPrograms[channel] < 0) channelPrograms[channel] = program
                    }
                    0xD0 -> { // Channel Aftertouch
                        pos += 1
                    }
                    0xE0 -> { // Pitch Bend
                        pos += 2
                    }
                    0xF0 -> { // Meta / SysEx
                        if (status == 0xFF) {
                            val metaType = data[pos++].toInt() and 0xFF
                            val len = readVarLen(data, pos); pos += lenSize(len, data, pos)
                            when (metaType) {
                                0x51 -> { // Tempo
                                    val t = (data[pos].toInt() and 0xFF shl 16) or
                                            (data[pos + 1].toInt() and 0xFF shl 8) or
                                            (data[pos + 2].toInt() and 0xFF)
                                    tempo = t
                                    tempoChanges.add(tick to t)
                                }
                                0x58 -> { // 拍号：分子决定每小节几拍，瀑布用它算小节长度
                                    val numerator = data[pos].toInt() and 0xFF
                                    if (numerator > 0) sigChanges.add(tick to numerator)
                                }
                            }
                            pos += len.toInt()
                        } else {
                            // SysEx
                            val len = readVarLen(data, pos); pos += lenSize(len, data, pos)
                            pos += len.toInt()
                        }
                    }
                }
            }
        }

        allEvents.sortBy { it.tick }

        // 速度事件可能分散在多个轨道里，先按 tick 去重，
        // 并保证 tick 0 一定有一条（否则 tickToMs 取不到起始速度）。
        val merged = linkedMapOf<Long, Int>()
        if (tempoChanges.none { it.first == 0L }) merged[0L] = tempo
        for ((tick, us) in tempoChanges) {
            if (!merged.containsKey(tick)) merged[tick] = us
        }
        val tempoMap = merged.entries
            .map { it.key to it.value }
            .sortedBy { it.first }

        // Extract notes
        val notes = extractNotes(allEvents, ticksPerQuarter)
        val durationMs = if (notes.isNotEmpty()) {
            tickToMs(notes.last().tick + notes.last().durationTicks, tempoMap, ticksPerQuarter)
        } else 0L

        // 速度标记换算成毫秒时间点，供瀑布自适应滚动速度使用
        val tempoMarks = tempoMap
            .map { (tick, us) -> TempoMark(tickToMs(tick, tempoMap, ticksPerQuarter).toDouble(), us) }
            .sortedBy { it.timeMs }

        return ParsedMidi(
            notes = notes,
            durationMs = durationMs,
            ticksPerQuarter = ticksPerQuarter,
            tempoChanges = tempoMap,
            tempoMarks = tempoMarks,
            beatsPerBar = sigChanges.firstOrNull()?.second ?: 4,
            channelPrograms = channelPrograms,
            channelVolumes = channelVolumes,
            channelPans = channelPans,
        )
    }

    private fun extractNotes(events: List<MidiEvent>, ticksPerQuarter: Int): List<NoteData> {
        val active = HashMap<String, Long>() // "ch:key" -> startTick
        val activeVel = HashMap<String, Int>()
        val notes = mutableListOf<NoteData>()

        for (e in events) {
            val k = "${e.channel}:${e.data1}"
            when (e.type) {
                0x90 -> {
                    if (e.data2 > 0) {
                        active[k] = e.tick
                        activeVel[k] = e.data2
                    } else {
                        active.remove(k)?.let { start ->
                            notes.add(NoteData(start, e.data1, e.channel, activeVel[k] ?: 64, e.tick - start))
                        }
                        activeVel.remove(k)
                    }
                }
                0x80 -> {
                    active.remove(k)?.let { start ->
                        notes.add(NoteData(start, e.data1, e.channel, activeVel[k] ?: 64, (e.tick - start).coerceAtLeast(1)))
                    }
                    activeVel.remove(k)
                }
            }
        }
        // 未闭合的音符（缺 Note Off）按半拍收尾，避免留下贯通全曲的长条
        for ((k, start) in active) {
            val parts = k.split(":")
            notes.add(
                NoteData(
                    tick = start,
                    key = parts[1].toInt(),
                    channel = parts[0].toInt(),
                    velocity = activeVel[k] ?: 64,
                    durationTicks = (ticksPerQuarter / 2).toLong().coerceAtLeast(1),
                )
            )
        }
        notes.sortBy { it.tick }
        return notes
    }

    fun tickToMs(tick: Long, tempoChanges: List<Pair<Long, Int>>, tpq: Int): Long {
        if (tick <= 0) return 0
        var ms = 0.0
        var lastTick = 0L
        var currentTempo = tempoChanges[0].second.toDouble()
        for (i in tempoChanges.indices) {
            val (tTick, tempo) = tempoChanges[i]
            if (tTick >= tick) break
            val segTicks = (tTick - lastTick).toDouble()
            ms += segTicks * currentTempo / tpq / 1000.0
            lastTick = tTick
            currentTempo = tempo.toDouble()
        }
        ms += (tick - lastTick) * currentTempo / tpq / 1000.0
        return ms.toLong()
    }

    private fun readInt32(data: ByteArray, pos: Int): Int =
        (data[pos].toInt() and 0xFF shl 24) or (data[pos + 1].toInt() and 0xFF shl 16) or
        (data[pos + 2].toInt() and 0xFF shl 8) or (data[pos + 3].toInt() and 0xFF)

    private fun readInt16(data: ByteArray, pos: Int): Int =
        (data[pos].toInt() and 0xFF shl 8) or (data[pos + 1].toInt() and 0xFF)

    private fun readVarLen(data: ByteArray, pos: Int): Long {
        var value = 0L
        var p = pos
        var b: Int
        do {
            b = data[p++].toInt() and 0xFF
            value = (value shl 7) or (b and 0x7F).toLong()
        } while (b and 0x80 != 0)
        return value
    }

    private fun lenSize(value: Long, data: ByteArray, pos: Int): Int {
        var p = pos
        var b: Int
        var size = 0
        do {
            b = data[p++].toInt() and 0xFF
            size++
        } while (b and 0x80 != 0)
        return size
    }

    data class MidiEvent(val tick: Long, val channel: Int, val type: Int, val data1: Int, val data2: Int)

    /**
     * 速度标记：在 [timeMs] 这一时刻起，四分音符时长为 [usPerQuarter] 微秒。
     * 瀑布用它换算"一小节多少毫秒"，从而让滚动速度跟随曲速。
     */
    data class TempoMark(val timeMs: Double, val usPerQuarter: Int)

    data class ParsedMidi(
        val notes: List<NoteData>,
        val durationMs: Long,
        val ticksPerQuarter: Int,
        val tempoChanges: List<Pair<Long, Int>>,
        val tempoMarks: List<TempoMark>,
        /** 每小节拍数，来自拍号事件（缺省 4/4）。 */
        val beatsPerBar: Int,
        /** 各通道首个 Program Change，-1 表示文件里没写。 */
        val channelPrograms: IntArray,
        /** 各通道首个 CC7 音量（0-127），缺省 100。 */
        val channelVolumes: IntArray,
        /** 各通道首个 CC10 声像（0-127，64 居中）。 */
        val channelPans: IntArray,
    )
}
