package tv.music.player.core.lyrics

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.music.player.core.playback.DemoCatalog
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.search.OnlineSearchApi

class LyricsRepository(
    private val api: QqLyricsApi = QqLyricsApi(),
    private val diskCache: LyricsDiskCache? = null,
    private val matchStore: LyricsMatchStore? = null,
    private val searchApi: OnlineSearchApi? = null,
) {
    private val cache = object : LruCache<String, Lyrics>(24) {}

    suspend fun load(track: DemoTrack, forceRefresh: Boolean = false): LyricsState = withContext(Dispatchers.IO) {
        if (!track.isOnline) {
            val demo = Lyrics(
                lines = DemoCatalog.lyrics.map { LyricLine(it.timeMs, it.text) },
                sourceLabel = "演示歌词",
            )
            return@withContext LyricsState.Ready(demo)
        }

        val songmid = track.onlineSongId
        if (songmid.isBlank()) return@withContext LyricsState.Unavailable("缺少歌曲 ID")
        val matched = matchStore?.get(songmid)
        val fetchMid = matched?.songMid ?: songmid
        val fetchSongId = matched?.songId ?: track.onlineSongNumericId

        if (!forceRefresh) {
            cache.get(fetchMid)?.let { return@withContext LyricsState.Ready(it) }
            diskCache?.read(fetchMid)?.let { cached ->
                cache.put(fetchMid, cached)
                return@withContext LyricsState.Ready(cached)
            }
        }
        try {
            val lyrics = api.fetch(fetchMid, fetchSongId)
            when {
                lyrics == null -> LyricsState.Unavailable("暂无歌词")
                lyrics.instrumental -> LyricsState.Unavailable("纯音乐，请欣赏")
                lyrics.lines.isEmpty() -> LyricsState.Unavailable("暂无歌词")
                else -> {
                    cache.put(fetchMid, lyrics)
                    diskCache?.write(fetchMid, lyrics)
                    LyricsState.Ready(lyrics)
                }
            }
        } catch (t: Throwable) {
            LyricsState.Failed(t.message?.takeIf { it.isNotBlank() } ?: "歌词获取失败")
        }
    }

    suspend fun searchCandidates(track: DemoTrack): List<LyricCandidate> = withContext(Dispatchers.IO) {
        val search = searchApi ?: return@withContext emptyList()
        val keyword = listOf(track.title, track.artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (keyword.isBlank()) return@withContext emptyList()
        runCatching { search.search(keyword, 1, 8) }
            .getOrNull()
            ?.list
            .orEmpty()
            .map { LyricCandidate(it.songmid, it.songId, it.title, it.artist) }
            .distinctBy { it.songMid }
            .take(6)
    }

    fun setMatch(originalSongmid: String, candidate: LyricCandidate) {
        matchStore?.put(originalSongmid, candidate)
    }

    fun clearMatch(originalSongmid: String) {
        matchStore?.clear(originalSongmid)
    }
}
