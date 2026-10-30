package tv.music.player.core.search

import org.json.JSONArray
import org.json.JSONObject
import tv.music.player.core.playback.DemoTrack

fun DemoTrack.toJsonObject(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("artist", artist)
    put("album", album)
    put("durationMs", durationMs)
    put("colorSeed", colorSeed)
    put("coverUrl", coverUrl)
    put("onlineSource", onlineSource)
    put("onlineSongId", onlineSongId)
    put("onlineHash", onlineHash)
    put("onlineAlbumMid", onlineAlbumMid)
    put("onlineStrMediaMid", onlineStrMediaMid)
    put("onlineSongNumericId", onlineSongNumericId)
    put("localUri", localUri)
    put("localSource", localSource)
}

fun JSONObject.toDemoTrack(): DemoTrack = DemoTrack(
    id = getInt("id"),
    title = getString("title"),
    artist = optString("artist"),
    album = optString("album"),
    durationMs = optLong("durationMs"),
    colorSeed = optInt("colorSeed"),
    coverUrl = optString("coverUrl"),
    onlineSource = optString("onlineSource"),
    onlineSongId = optString("onlineSongId"),
    onlineHash = optString("onlineHash"),
    onlineAlbumMid = optString("onlineAlbumMid"),
    onlineStrMediaMid = optString("onlineStrMediaMid"),
    onlineSongNumericId = optLong("onlineSongNumericId"),
    localUri = optString("localUri"),
    localSource = optString("localSource"),
)

fun List<DemoTrack>.encodeTracks(): String {
    val array = JSONArray()
    forEach { array.put(it.toJsonObject()) }
    return array.toString()
}

fun decodeTracks(json: String?): List<DemoTrack>? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                add(array.getJSONObject(i).toDemoTrack())
            }
        }
    }.getOrNull()
}
