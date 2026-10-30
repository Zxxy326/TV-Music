package tv.music.player.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class PinyinT9Test {

    @Test
    fun `same key cycles letters`() {
        val t9 = PinyinT9()
        var q = t9.type('2', "", 1_000L)
        assertEquals("a", q)
        q = t9.type('2', q, 1_100L)
        assertEquals("b", q)
        q = t9.type('2', q, 1_200L)
        assertEquals("c", q)
        q = t9.type('2', q, 1_300L)
        assertEquals("a", q)
    }

    @Test
    fun `multi-tap spells pinyin`() {
        val t9 = PinyinT9()
        var q = t9.type('7', "", 1_000L)
        q = t9.type('4', q, 1_100L)
        q = t9.type('4', q, 1_200L)
        q = t9.type('4', q, 1_300L)
        q = t9.type('6', q, 1_400L)
        q = t9.type('6', q, 1_500L)
        assertEquals("pin", q)
    }

    @Test
    fun `timeout starts a new letter`() {
        val t9 = PinyinT9(timeoutMs = 500L)
        var q = t9.type('2', "", 1_000L)
        q = t9.type('2', q, 1_600L)
        assertEquals("aa", q)
    }

    @Test
    fun `seven and nine have four letters`() {
        val t9 = PinyinT9()
        var q = t9.type('7', "", 0L)
        q = t9.type('7', q, 100L)
        q = t9.type('7', q, 200L)
        q = t9.type('7', q, 300L)
        assertEquals("s", q)
        q = t9.type('9', q, 400L)
        q = t9.type('9', q, 500L)
        q = t9.type('9', q, 600L)
        q = t9.type('9', q, 700L)
        assertEquals("sz", q)
    }

    @Test
    fun `reset breaks the cycle`() {
        val t9 = PinyinT9()
        var q = t9.type('2', "", 1_000L)
        t9.reset()
        q = t9.type('2', q, 1_100L)
        assertEquals("aa", q)
    }
}
