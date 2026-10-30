package tv.music.player.core.lyrics

import android.content.Context

data class LyricCandidate(
    val songMid: String,
    val songId: Long,
    val title: String,
    val artist: String,
)

class LyricsMatchStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lyric_matches", Context.MODE_PRIVATE)

    fun get(originalSongmid: String): LyricCandidate? = runCatching {
        val raw = prefs.getString(originalSongmid, null) ?: return null
        val songMid = raw.substringBefore('|')
        val songId = raw.substringAfter('|', "0").toLongOrNull() ?: return null
        if (songMid.isBlank()) return null
        LyricCandidate(songMid, songId, "", "")
    }.getOrNull()

    fun put(originalSongmid: String, candidate: LyricCandidate) {
        prefs.edit().putString(originalSongmid, "${candidate.songMid}|${candidate.songId}").apply()
    }

    fun clear(originalSongmid: String) {
        prefs.edit().remove(originalSongmid).apply()
    }
}
