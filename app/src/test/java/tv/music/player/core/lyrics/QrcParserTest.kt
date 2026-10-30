package tv.music.player.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrcParserTest {

    @Test
    fun parsesWordsAndLineDuration() {
        val content = """
            [ti:测试]
            [offset:0]
            [1000,2000]晴(1000,500)天(1500,500) (2000,500)故(2500,500)事(3000,500)
            [4000,1500]Hello(4000,400) (4400,100)world(4500,1000)
        """.trimIndent()

        val lyrics = QrcParser.parseContent(content)
        assertEquals(2, lyrics.lines.size)
        val first = lyrics.lines[0]
        assertEquals(1_000L, first.timeMs)
        assertEquals(2_000L, first.durationMs)
        assertEquals("晴天 故事", first.text)
        assertEquals(5, first.words.size)
        assertEquals("晴", first.words.first().text)
        assertEquals(1_500L, first.words[1].timeMs)
        assertEquals("Hello world", lyrics.lines[1].text)
    }

    @Test
    fun mergesTranslationByNearestLine() {
        val lineLyrics = LrcParser.parse(
            "[00:01.00]晴天\n[00:04.00]Hello",
            "[00:01.00]晴天翻译\n[00:04.00]你好",
        )
        val lyrics = QrcParser.parseContent(
            "[1000,2000]晴(1000,500)天(1500,500)\n[4000,1500]Hello(4000,1500)",
            lineLyrics,
        )
        assertEquals("晴天翻译", lyrics.lines[0].translation)
        assertEquals("你好", lyrics.lines[1].translation)
    }

    @Test
    fun parsesXmlContentAndEntities() {
        val xml = """<QrcInfos><Lyric_1 LyricContent="[0,1000]A(0,500)&quot;B&quot;(500,500)"/>"""
        val lyrics = QrcParser.parseXml(xml)
        assertEquals(1, lyrics.lines.size)
        assertEquals("A\"B\"", lyrics.lines.single().text)
        assertEquals("QQ QRC", lyrics.sourceLabel)
    }

    @Test
    fun appliesFileOffsetToLineAndWords() {
        val lyrics = QrcParser.parseContent("[offset:-500]\n[1000,500]晴(1000,500)")
        assertEquals(500L, lyrics.lines.single().timeMs)
        assertEquals(500L, lyrics.lines.single().words.single().timeMs)
    }

    @Test
    fun instrumentalMarkerIsFlagged() {
        val lyrics = QrcParser.parseContent("[0,1000]纯(0,100)音(100,100)乐(200,100)")
        assertTrue(lyrics.instrumental)
    }

    @Test
    fun extractsQrcCipherFromDownloadResponse() {
        val xml = """<lyric><content><![CDATA[0011223344556677]]></content></lyric>"""
        val bytes = QrcParser.extractCdataHex(xml)
        assertEquals(8, bytes?.size)
        assertEquals(0x00, bytes?.first()?.toInt())
    }
}
