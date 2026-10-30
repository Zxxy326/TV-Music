package tv.music.player.core.search

import org.json.JSONArray
import org.json.JSONObject
import tv.music.player.core.playback.DemoTrack

fun List<DemoTrack>.encodeTracks(): String {
    val array = JSONArray()
    forEach { track ->
        array.put(
            JSONObject().apply {
                put("id", track.id)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("durationMs", track.durationMs)
                put("colorSeed", track.colorSeed)
                put("coverUrl", track.coverUrl)
                put("onlineSource", track.onlineSource)
                put("onlineSongId", track.onlineSongId)
                put("onlineHash", track.onlineHash)
                put("onlineAlbumMid", track.onlineAlbumMid)
                put("onlineStrMediaMid", track.onlineStrMediaMid)
            }
        )
    }
    return array.toString()
}

fun decodeTracks(json: String?): List<DemoTrack>? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    DemoTrack(
                        id = item.getInt("id"),
                        title = item.getString("title"),
                        artist = item.optString("artist"),
                        album = item.optString("album"),
                        durationMs = item.optLong("durationMs"),
                        colorSeed = item.optInt("colorSeed"),
                        coverUrl = item.optString("coverUrl"),
                        onlineSource = item.optString("onlineSource"),
                        onlineSongId = item.optString("onlineSongId"),
                        onlineHash = item.optString("onlineHash"),
                        onlineAlbumMid = item.optString("onlineAlbumMid"),
                        onlineStrMediaMid = item.optString("onlineStrMediaMid"),
                    )
                )
            }
        }
    }.getOrNull()
}
