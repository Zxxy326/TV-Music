package tv.music.player.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parsesTimestampsAndSorts() {
        val lyrics = LrcParser.parse(
            """
            [ti:测试]
            [00:10.50]第二句
            [00:02.25]第一句
            """.trimIndent(),
        )
        assertEquals(2, lyrics.lines.size)
        assertEquals(2_250L, lyrics.lines[0].timeMs)
        assertEquals("第一句", lyrics.lines[0].text)
        assertEquals(10_500L, lyrics.lines[1].timeMs)
    }

    @Test
    fun expandsMultipleTimestampsPerLine() {
        val lyrics = LrcParser.parse("[00:01.00][00:05.00]副歌")
        assertEquals(listOf(1_000L, 5_000L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun mergesTranslationByTimestamp() {
        val lyrics = LrcParser.parse(
            "[00:01.00]Hello\n[00:03.00]World",
            "[00:01.00]你好\n[00:03.00]世界",
        )
        assertEquals("你好", lyrics.lines[0].translation)
        assertEquals("世界", lyrics.lines[1].translation)
    }

    @Test
    fun readsInlineTranslationAndFileOffset() {
        val lyrics = LrcParser.parse(
            "[offset:-500]\n[00:10.00]Hello//你好",
        )
        assertEquals(9_500L, lyrics.lines.single().timeMs)
        assertEquals("你好", lyrics.lines.single().translation)
    }

    @Test
    fun marksInstrumentalWithoutExposingMarkerLine() {
        val lyrics = LrcParser.parse("[00:00.00]纯音乐，请欣赏")
        assertTrue(lyrics.instrumental)
        assertTrue(lyrics.lines.isEmpty())
    }

    @Test
    fun emptyLyricIsNotInstrumental() {
        val lyrics = LrcParser.parse("")
        assertTrue(lyrics.lines.isEmpty())
        assertTrue(!lyrics.instrumental)
    }

    @Test
    fun indexAtUsesOffsetAndStopsAtFirstLine() {
        val lyrics = LrcParser.parse("[00:10.00]A\n[00:20.00]B")
        assertEquals(-1, lyrics.indexAt(9_000L))
        assertEquals(0, lyrics.indexAt(10_000L))
        assertEquals(1, lyrics.indexAt(21_000L))
        assertEquals(0, lyrics.indexAt(21_000L, offsetMs = -1_500L))
        assertNull(lyrics.lines.firstOrNull()?.translation)
    }
}
