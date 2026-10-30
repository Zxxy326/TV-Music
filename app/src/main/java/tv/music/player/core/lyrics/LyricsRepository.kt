package tv.music.player.core.lyrics

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.music.player.core.playback.DemoCatalog
import tv.music.player.core.playback.DemoTrack

class LyricsRepository(
    private val api: QqLyricsApi = QqLyricsApi(),
) {
    private val cache = object : LruCache<String, Lyrics>(24) {}

    suspend fun load(track: DemoTrack): LyricsState = withContext(Dispatchers.IO) {
        if (!track.isOnline) {
            val demo = Lyrics(
                lines = DemoCatalog.lyrics.map { LyricLine(it.timeMs, it.text) },
                sourceLabel = "演示歌词",
            )
            return@withContext LyricsState.Ready(demo)
        }

        val songmid = track.onlineSongId
        if (songmid.isBlank()) return@withContext LyricsState.Unavailable("缺少歌曲 ID")

        cache.get(songmid)?.let { return@withContext LyricsState.Ready(it) }
        try {
            val lyrics = api.fetch(songmid, track.onlineSongNumericId)
            when {
                lyrics == null -> LyricsState.Unavailable("暂无歌词")
                lyrics.instrumental -> LyricsState.Unavailable("纯音乐，请欣赏")
                lyrics.lines.isEmpty() -> LyricsState.Unavailable("暂无歌词")
                else -> {
                    cache.put(songmid, lyrics)
                    LyricsState.Ready(lyrics)
                }
            }
        } catch (t: Throwable) {
            LyricsState.Failed(t.message?.takeIf { it.isNotBlank() } ?: "歌词获取失败")
        }
    }
}
