package com.fumi.voice

import com.fumi.voice.export.ExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatTest {
    @Test fun wav_is_always_first_lossless_option() {
        assertEquals("WAV", ExportFormat.WAV.label)
        assertEquals("wav", ExportFormat.WAV.extension)
    }

    @Test fun every_format_has_distinct_extension() {
        val exts = ExportFormat.entries.map { it.extension }
        assertEquals(exts.size, exts.toSet().size)
        assertTrue(ExportFormat.entries.all { it.label.isNotBlank() && it.description.isNotBlank() })
    }

    @Test fun mp3_uses_lame_path() {
        assertEquals("mp3", ExportFormat.MP3.extension)
        assertEquals("MP3", ExportFormat.MP3.label)
    }
}
