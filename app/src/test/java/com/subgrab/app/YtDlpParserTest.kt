package com.subgrab.app

import com.subgrab.app.data.YtDlpOutputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpParserTest {
    @Test fun parsesJsonLinesAndLimitsToFifty() {
        val lines = (1..55).asSequence().map { "{\"id\":\"v$it\",\"title\":\"Video $it\",\"duration\":60}" }
        val result = YtDlpOutputParser().parseFlatPlaylist(lines, "https://youtu.be/list")
        assertEquals(50, result.second.size)
        assertEquals("v1", result.second.first().videoId)
        assertEquals("Video 50", result.second.last().title)
    }
    @Test fun parsesManualAndAutoSubtitleLanguages() {
        val result = YtDlpOutputParser().parseAvailableSubs("  vi                    vtt\n  en-auto               vtt\n")
        assertTrue(result.any { it.code == "vi" && !it.isAuto })
        assertTrue(result.any { it.code == "en" && it.isAuto })
    }
}
