package tv.music.player.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LyricsDiskCacheTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "tvmusic-lyrics-${System.nanoTime()}")

    @Test
    fun roundTripsWordsTranslationsAndInstrumentalFlag() {
        val cache = LyricsDiskCache(dir)
        val lyrics = Lyrics(
            lines = listOf(
                LyricLine(
                    timeMs = 1_000,
                    text = "晴天",
                    translation = "Sunny day",
                    durationMs = 2_000,
                    words = listOf(
                        LyricWord(1_000, 500, "晴"),
                        LyricWord(1_500, 500, "天"),
                    ),
                ),
            ),
            sourceLabel = "QQ QRC",
        )
        cache.write("0039MnYb0qxYhV", lyrics)

        val restored = cache.read("0039MnYb0qxYhV")
        assertEquals("晴天", restored?.lines?.single()?.text)
        assertEquals("Sunny day", restored?.lines?.single()?.translation)
        assertEquals(2, restored?.lines?.single()?.words?.size)
        assertEquals("QQ QRC", restored?.sourceLabel)

        cache.write("instrumental", Lyrics(instrumental = true))
        assertTrue(cache.read("instrumental")?.instrumental == true)
    }

    @Test
    fun expiresAndDropsCorruptFiles() {
        var now = 1_000_000L
        val cache = LyricsDiskCache(dir, ttlMs = 10_000, clock = { now })
        cache.write("mid1", Lyrics(lines = listOf(LyricLine(0, "hi"))))
        assertEquals(1, cache.read("mid1")?.lines?.size)

        now += 10_001
        assertNull(cache.read("mid1"))

        File(dir, "mid2.lrc").writeText("broken")
        assertNull(cache.read("mid2"))
    }
}
