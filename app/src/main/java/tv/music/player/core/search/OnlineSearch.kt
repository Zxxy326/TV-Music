package tv.music.player.core.search

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.InflaterInputStream
import tv.music.player.core.playback.DemoTrack

data class OnlineTrack(
    val source: String,
    val songmid: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String,
    val strMediaMid: String = "",
    val albumMid: String = "",
    val albumId: String = "",
    val artistIds: String = "",
    val songId: Long = 0L,
)

data class OnlineSearchResult(
    val list: List<OnlineTrack> = emptyList(),
    val total: Int = 0,
    val isEnd: Boolean = true,
)

fun OnlineTrack.toDemoTrack(): DemoTrack {
    val key = "$source:$songmid"
    val rawAbs = kotlin.math.abs(key.hashCode()).coerceAtLeast(1)
    return DemoTrack(
        id = -rawAbs,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        colorSeed = (songmid.hashCode() and 0x7fffffff) % 98,
        coverUrl = coverUrl,
        onlineSource = source,
        onlineSongId = songmid,
        onlineHash = strMediaMid,
        onlineAlbumMid = albumMid,
        onlineStrMediaMid = strMediaMid,
        onlineSongNumericId = songId,
    )
}

/**
 * 原生 QQ 音乐搜索。LX 自定义音源脚本只开放 musicUrl，搜索必须由宿主提供元数据。
 */
class OnlineSearchApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(keyword: String, page: Int = 1, pageSize: Int = 30): OnlineSearchResult =
        withContext(Dispatchers.IO) {
            require(keyword.isNotBlank()) { "关键词为空" }
            var lastError: Throwable? = null
            repeat(3) { attempt ->
                try {
                    searchOnce(keyword.trim(), page, pageSize)?.let { return@withContext it }
                } catch (t: Throwable) {
                    lastError = t
                }
                if (attempt < 2) delay(300L * (attempt + 1))
            }
            throw lastError ?: IllegalStateException("QQ 搜索失败")
        }

    private fun searchOnce(keyword: String, page: Int, pageSize: Int): OnlineSearchResult? {
        val json = buildSearchJson(keyword, page, pageSize)
        val sign = zzcSign(json)
        val request = Request.Builder()
            .url("$TX_SEARCH_URL?sign=$sign")
            .header("Accept", "application/json")
            .header("User-Agent", "QQMusic 14090508(android 12)")
            .post(json.toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .build()

        val text = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            decodeBody(response.body.bytes())
        } ?: return null

        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (root.optInt("code", -1) != 0) return null
        val req = root.optJSONObject("req") ?: return null
        if (req.optInt("code", -1) != 0) return null
        val data = req.optJSONObject("data") ?: return null
        val body = data.optJSONObject("body") ?: return null
        val items = body.optJSONArray("item_song") ?: return OnlineSearchResult()

        val list = mutableListOf<OnlineTrack>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val file = item.optJSONObject("file") ?: continue
            val mediaMid = file.optString("media_mid")
            if (mediaMid.isBlank()) continue
            val mid = item.optString("mid")
            val title = item.optString("title")
            if (mid.isBlank() || title.isBlank()) continue

            val singerNames = mutableListOf<String>()
            val singerIds = mutableListOf<String>()
            item.optJSONArray("singer")?.let { singers ->
                for (j in 0 until singers.length()) {
                    val singer = singers.optJSONObject(j) ?: continue
                    singer.optString("name").takeIf { it.isNotBlank() }?.let { singerNames.add(it) }
                    singer.optString("mid").takeIf { it.isNotBlank() }?.let { singerIds.add(it) }
                }
            }
            val album = item.optJSONObject("album")
            val albumMid = album?.optString("mid", "").orEmpty()
            val albumName = album?.optString("name", "").orEmpty()
            val albumId = album?.optString("id", "").orEmpty()
            val pic = when {
                albumMid.isNotBlank() && albumMid != "空" ->
                    "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg"
                singerIds.isNotEmpty() ->
                    "https://y.gtimg.cn/music/photo_new/T001R300x300M000${singerIds.first()}.jpg"
                else -> ""
            }

            list += OnlineTrack(
                source = "tx",
                songmid = mid,
                title = title,
                artist = singerNames.joinToString(" / "),
                album = albumName,
                durationMs = item.optLong("interval", 0L) * 1000L,
                coverUrl = pic,
                strMediaMid = mediaMid,
                albumMid = albumMid,
                albumId = albumId,
                artistIds = singerIds.joinToString(","),
                songId = item.optLong("id", 0L),
            )
        }

        val total = data.optJSONObject("meta")?.optInt("estimate_sum", 0) ?: 0
        return OnlineSearchResult(
            list = list,
            total = if (total > 0) total else list.size,
            isEnd = if (total > 0) page * pageSize >= total else list.size < pageSize,
        )
    }

    private fun buildSearchJson(keyword: String, page: Int, pageSize: Int): String {
        val comm = "\"comm\":{" +
            "\"ct\":\"11\",\"cv\":\"14090508\",\"v\":\"14090508\",\"tmeAppID\":\"qqmusic\"," +
            "\"phonetype\":\"EBG-AN10\",\"deviceScore\":\"553.47\",\"devicelevel\":\"50\"," +
            "\"newdevicelevel\":\"20\",\"rom\":\"HuaWei/EMOTION/EmotionUI_14.2.0\",\"os_ver\":\"12\"," +
            "\"OpenUDID\":\"0\",\"OpenUDID2\":\"0\",\"QIMEI36\":\"0\",\"udid\":\"0\",\"chid\":\"0\"," +
            "\"aid\":\"0\",\"oaid\":\"0\",\"taid\":\"0\",\"tid\":\"0\",\"wid\":\"0\",\"uid\":\"0\"," +
            "\"sid\":\"0\",\"modeSwitch\":\"6\",\"teenMode\":\"0\",\"ui_mode\":\"2\"," +
            "\"nettype\":\"1020\",\"v4ip\":\"\"}"
        val searchId = (1..16).map { (0..9).random() }.joinToString("")
        val req = "\"req\":{\"module\":\"music.search.SearchCgiService\"," +
            "\"method\":\"DoSearchForQQMusicMobile\",\"param\":{" +
            "\"search_type\":0,\"searchid\":\"$searchId\"," +
            "\"query\":\"${escapeJson(keyword)}\",\"page_num\":$page,\"num_per_page\":$pageSize," +
            "\"highlight\":0,\"nqc_flag\":0,\"multi_zhida\":0,\"cat\":2,\"grp\":1,\"sin\":0,\"sem\":0}}"
        return "{$comm,$req}"
    }

    private fun escapeJson(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    private fun zzcSign(text: String): String {
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val part1 = PART_1_INDEXES.filter { it < hash.length }.joinToString("") { hash[it].toString() }
        val part2 = PART_2_INDEXES.filter { it < hash.length }.joinToString("") { hash[it].toString() }
        val scramble = ByteArray(SCRAMBLE_VALUES.size)
        SCRAMBLE_VALUES.forEachIndexed { index, value ->
            val hex = hash.substring(index * 2, minOf(index * 2 + 2, hash.length))
            scramble[index] = (value xor (if (hex.isEmpty()) 0 else hex.toInt(16))).toByte()
        }
        val encoded = Base64.encodeToString(scramble, Base64.NO_WRAP)
            .replace("+", "").replace("/", "").replace("=", "")
        return "zzc$part1$encoded$part2".lowercase()
    }

    private fun decodeBody(data: ByteArray): String? {
        val direct = String(data, Charsets.UTF_8).trim()
        if (direct.startsWith("{") || direct.startsWith("[")) return direct
        return runCatching {
            val offset = (0 until minOf(data.size, 10)).firstOrNull {
                data[it] == 0x78.toByte() && it + 1 < data.size
            } ?: 0
            InflaterInputStream(ByteArrayInputStream(data.copyOfRange(offset, data.size))).use { inflater ->
                val out = ByteArrayOutputStream()
                inflater.copyTo(out)
                out.toString(Charsets.UTF_8.name())
            }
        }.getOrNull()
    }

    private companion object {
        const val TX_SEARCH_URL = "https://u.y.qq.com/cgi-bin/musics.fcg"
        val PART_1_INDEXES = intArrayOf(23, 14, 6, 36, 16, 40, 7, 19)
        val PART_2_INDEXES = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
        val SCRAMBLE_VALUES = intArrayOf(
            89, 39, 179, 150, 218, 82, 58, 252, 177, 52,
            186, 123, 120, 64, 242, 133, 143, 161, 121, 179,
        )
    }
}
