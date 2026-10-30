package tv.music.player.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryStoreTest {

    @Test
    fun `encode and decode keep order`() {
        val items = listOf("晴天", "qingtian", "夜航星")
        assertEquals(items, SearchHistoryStore.decode(SearchHistoryStore.encode(items)))
    }

    @Test
    fun `merge moves term to front and dedupes`() {
        val merged = SearchHistoryStore.merge(listOf("晴天", "夜航星", "晴天"), "夜航星", 8)
        assertEquals(listOf("夜航星", "晴天"), merged)
    }

    @Test
    fun `merge caps length`() {
        val existing = (1..10).map { "term$it" }
        val merged = SearchHistoryStore.merge(existing, "new", 8)
        assertEquals(8, merged.size)
        assertEquals("new", merged.first())
    }

    @Test
    fun `merge trims and limits term`() {
        val merged = SearchHistoryStore.merge(emptyList(), "  " + "a".repeat(40) + "  ")
        assertEquals("a".repeat(24), merged.single())
    }

    @Test
    fun `decode drops empty segments`() {
        assertEquals(listOf("晴天", "qt"), SearchHistoryStore.decode("晴天\u001F\u001F  \u001Fqt"))
    }
}
