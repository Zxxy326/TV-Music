package tv.music.player.core.companion

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.PlaybackController
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

const val COMPANION_PORT = 8390

data class CompanionStatus(
    val running: Boolean = false,
    val port: Int = COMPANION_PORT,
    val urls: List<String> = emptyList(),
    val error: String? = null,
)

class CompanionServer(
    private val context: Context,
    private val playback: PlaybackController,
    private val bridge: CompanionBridge,
) {
    private val prefs = context.getSharedPreferences("companion_pairing", Context.MODE_PRIVATE)
    private val _code = MutableStateFlow(loadCode())
    val code: StateFlow<String> = _code.asStateFlow()

    private val _status = MutableStateFlow(CompanionStatus())
    val status: StateFlow<CompanionStatus> = _status.asStateFlow()

    private val running = AtomicBoolean(false)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var clients: ExecutorService? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(COMPANION_PORT))
            serverSocket = socket
            clients = Executors.newCachedThreadPool()
            acceptThread = Thread({ acceptLoop(socket) }, "tv-music-companion").apply {
                isDaemon = true
                start()
            }
            _status.value = CompanionStatus(
                running = true,
                port = COMPANION_PORT,
                urls = lanUrls(),
                error = null,
            )
            Log.i(TAG, "companion listening on ${COMPANION_PORT}, urls=${_status.value.urls}")
        } catch (t: Throwable) {
            running.set(false)
            runCatching { serverSocket?.close() }
            serverSocket = null
            acceptThread = null
            clients?.shutdownNow()
            clients = null
            _status.value = CompanionStatus(
                running = false,
                port = COMPANION_PORT,
                urls = emptyList(),
                error = t.message ?: "启动失败",
            )
            Log.w(TAG, "companion start failed", t)
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        clients?.shutdownNow()
        clients = null
        _status.value = CompanionStatus(running = false)
    }

    fun regenerateCode(): String {
        val next = newCode()
        prefs.edit()
            .putString(KEY_CODE, next)
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
        _code.value = next
        return next
    }

    fun refreshAddresses() {
        if (!running.get()) return
        _status.value = _status.value.copy(urls = lanUrls(), error = null)
    }

    private fun loadCode(): String {
        val saved = prefs.getString(KEY_CODE, null)
        val createdAt = prefs.getLong(KEY_CREATED_AT, 0L)
        val expired = System.currentTimeMillis() - createdAt > CODE_LIFETIME_MS
        if (!saved.isNullOrBlank() && !expired) return saved
        val next = newCode()
        prefs.edit()
            .putString(KEY_CODE, next)
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
        return next
    }

    private fun newCode(): String = (SecureRandom().nextInt(9000) + 1000).toString()

    private fun lanUrls(): List<String> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
            .sortedBy { address ->
                when {
                    address.startsWith("192.168.") -> 0
                    address.startsWith("10.") -> 1
                    else -> 2
                }
            }
            .map { "http://$it:$COMPANION_PORT" }
    }.getOrDefault(emptyList())

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get()) {
            val client = try {
                socket.accept()
            } catch (e: SocketException) {
                break
            } catch (t: Throwable) {
                if (!running.get()) break
                Log.w(TAG, "accept failed", t)
                continue
            }
            val pool = clients ?: break
            runCatching {
                pool.execute {
                    runCatching { handle(client) }
                        .onFailure { Log.w(TAG, "request failed ${it.message}") }
                    runCatching { client.close() }
                }
            }.onFailure { runCatching { client.close() } }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 8_000
        val input = BufferedInputStream(socket.getInputStream())
        val header = readHeader(input) ?: return
        val requestLine = header.lineSequence().firstOrNull().orEmpty()
        val parts = requestLine.split(' ')
        if (parts.size < 2) return respond(socket, 400, "text/plain", "bad request")
        val method = parts[0].uppercase()
        val target = parts[1]
        val path = target.substringBefore('?')
        val params = parseQuery(target.substringAfter('?', ""))

        if (method == "GET" && path == "/") {
            return respond(socket, 200, "text/html", COMPANION_PAGE)
        }
        if (method == "GET" && path == "/favicon.ico") {
            return respond(socket, 204, "text/plain", "")
        }
        if (path != "/api/state" &&
            path != "/api/input" &&
            path != "/api/control" &&
            path != "/api/queue/play"
        ) {
            return respondJson(socket, 404, JSONObject().put("ok", false).put("error", "not_found"))
        }
        if (params["code"] != _code.value) {
            return respondJson(socket, 401, JSONObject().put("ok", false).put("error", "pairing"))
        }

        when (path) {
            "/api/state" -> {
                if (method != "GET") return respondJson(socket, 405, JSONObject().put("ok", false))
                respondJson(socket, 200, stateJson())
            }
            "/api/input" -> {
                if (method != "POST") return respondJson(socket, 405, JSONObject().put("ok", false))
                val text = params["text"].orEmpty().trim().take(48)
                if (text.isBlank()) {
                    respondJson(socket, 400, JSONObject().put("ok", false).put("error", "empty"))
                } else {
                    bridge.requestSearch(text)
                    respondJson(socket, 200, JSONObject().put("ok", true))
                }
            }
            "/api/control" -> {
                if (method != "POST") return respondJson(socket, 405, JSONObject().put("ok", false))
                handleControl(socket, params)
            }
            "/api/queue/play" -> {
                if (method != "POST") return respondJson(socket, 405, JSONObject().put("ok", false))
                handleQueuePlay(socket, params)
            }
        }
    }

    private fun handleControl(socket: Socket, params: Map<String, String>) {
        val action = params["action"].orEmpty()
        val value = params["value"]?.toLongOrNull() ?: 0L
        when (action) {
            "toggle" -> mainScope.launch { playback.toggle() }
            "play" -> if (!playback.state.value.isPlaying) {
                mainScope.launch { playback.toggle() }
            }
            "pause" -> if (playback.state.value.isPlaying) {
                mainScope.launch { playback.toggle() }
            }
            "next" -> {
                mainScope.launch { playback.next() }
                bridge.openPlayer()
            }
            "previous" -> {
                mainScope.launch { playback.previous() }
                bridge.openPlayer()
            }
            "seekBy" -> mainScope.launch { playback.seekBy(value) }
            "seekTo" -> mainScope.launch { playback.seekTo(value) }
            else -> return respondJson(
                socket,
                400,
                JSONObject().put("ok", false).put("error", "unknown_action"),
            )
        }
        respondJson(socket, 200, JSONObject().put("ok", true))
    }

    private fun handleQueuePlay(socket: Socket, params: Map<String, String>) {
        val index = params["index"]?.toIntOrNull()
        val queue = playback.state.value.queue
        if (index == null || index !in queue.indices) {
            return respondJson(socket, 400, JSONObject().put("ok", false).put("error", "index"))
        }
        val track = queue[index]
        mainScope.launch { playback.play(track, queue) }
        bridge.openPlayer()
        respondJson(socket, 200, JSONObject().put("ok", true))
    }

    private fun stateJson(): JSONObject {
        val snapshot = playback.state.value
        val root = JSONObject()
            .put("ok", true)
            .put("playing", snapshot.isPlaying)
            .put("buffering", snapshot.buffering)
            .put("positionMs", snapshot.positionMs)
            .put("durationMs", snapshot.durationMs)
            .put("track", trackJson(snapshot.track))
        val queue = JSONArray()
        snapshot.queue.forEach { track ->
            queue.put(trackJson(track).put("current", sameTrack(track, snapshot.track)))
        }
        root.put("queue", queue)
        return root
    }

    private fun trackJson(track: DemoTrack): JSONObject = JSONObject()
        .put("title", track.title)
        .put("artist", track.artist)
        .put("album", track.album)
        .put("cover", track.coverUrl)
        .put("online", track.isOnline)

    private fun sameTrack(a: DemoTrack, b: DemoTrack): Boolean {
        if (a == b) return true
        if (a.isOnline && b.isOnline) {
            return a.onlineSource == b.onlineSource && a.onlineSongId == b.onlineSongId
        }
        return !a.isOnline && !b.isOnline && a.id == b.id
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val index = part.indexOf('=')
            val rawKey = if (index >= 0) part.substring(0, index) else part
            val rawValue = if (index >= 0) part.substring(index + 1) else ""
            val key = runCatching { URLDecoder.decode(rawKey, "UTF-8") }.getOrDefault(rawKey)
            val value = runCatching { URLDecoder.decode(rawValue, "UTF-8") }.getOrDefault(rawValue)
            key to value
        }.toMap()
    }

    private fun readHeader(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        var state = 0
        while (buffer.size() < 16_384) {
            val b = input.read()
            if (b == -1) break
            buffer.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> 4
                b == '\r'.code -> 1
                else -> 0
            }
            if (state == 4) break
        }
        if (buffer.size() == 0) return null
        return buffer.toString(Charsets.ISO_8859_1.name())
    }

    private fun respondJson(socket: Socket, statusCode: Int, body: JSONObject) {
        respond(socket, statusCode, "application/json", body.toString())
    }

    private fun respond(socket: Socket, statusCode: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (statusCode) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }
        val headers = "HTTP/1.1 $statusCode $reason\r\n" +
            "Content-Type: $contentType; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        val output = BufferedOutputStream(socket.getOutputStream())
        output.write(headers.toByteArray(Charsets.US_ASCII))
        if (bytes.isNotEmpty()) output.write(bytes)
        output.flush()
    }

    private companion object {
        const val TAG = "TvMusicCompanion"
        const val KEY_CODE = "pairing_code"
        const val KEY_CREATED_AT = "pairing_created_at"
        const val CODE_LIFETIME_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
