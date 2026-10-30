package tv.music.player.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNamingTest {

    @Test
    fun `sanitize strips path separators and reserved characters`() {
        assertEquals("AC_DC - Back In Black", sanitizeFileName("AC/DC - Back In Black"))
        assertEquals("a_b_c_d_e_f_g_h", sanitizeFileName("a\\b:c*d?e\"f<g>h"))
    }

    @Test
    fun `sanitize keeps chinese and trims trailing dots`() {
        assertEquals("晴天 - 周杰伦", sanitizeFileName("  晴天 - 周杰伦...  "))
    }

    @Test
    fun `sanitize limits length and falls back to empty`() {
        assertEquals(80, sanitizeFileName("x".repeat(120)).length)
        assertEquals("", sanitizeFileName("   "))
    }

    @Test
    fun `extension prefers content type`() {
        assertEquals("flac", audioExtension("https://cdn.example/song.mp3?sign=1", "audio/flac"))
        assertEquals("m4a", audioExtension("https://cdn.example/song", "audio/mp4; charset=utf-8"))
    }

    @Test
    fun `extension falls back to url path`() {
        assertEquals("flac", audioExtension("https://cdn.example/a/b.flac?token=x", "application/octet-stream"))
        assertEquals("ogg", audioExtension("https://cdn.example/a/b.ogg", ""))
    }

    @Test
    fun `extension defaults to mp3 for opaque urls`() {
        assertEquals("mp3", audioExtension("https://cdn.example/stream?id=42", "application/octet-stream"))
    }

    @Test
    fun `only m3u8 urls are treated as hls`() {
        assertTrue(looksLikeHls("https://cdn.example/list.m3u8?sign=1"))
        assertFalse(looksLikeHls("https://cdn.example/song.flac"))
    }
}
