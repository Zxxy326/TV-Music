package tv.music.player.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import tv.music.player.core.playback.DemoTrack

class LibraryStoreTest {

    private fun track(
        id: Int = 1,
        source: String = "",
        songId: String = "",
    ) = DemoTrack(
        id = id,
        title = "晴天",
        artist = "周杰伦",
        album = "叶惠美",
        durationMs = 269_000L,
        colorSeed = 23,
        onlineSource = source,
        onlineSongId = songId,
    )

    @Test
    fun `online tracks use source and song id`() {
        assertEquals("online:tx:003", track(source = "tx", songId = "003").libraryKey())
    }

    @Test
    fun `demo tracks use local id`() {
        assertEquals("demo:2", track(id = 2).libraryKey())
    }

    @Test
    fun `same song from different sources is not deduped`() {
        assertNotEquals(
            track(source = "tx", songId = "003").libraryKey(),
            track(source = "kw", songId = "003").libraryKey(),
        )
    }
}
