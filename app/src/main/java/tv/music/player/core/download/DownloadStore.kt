package tv.music.player.core.download

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import tv.music.player.core.library.libraryKey
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.search.toDemoTrack
import tv.music.player.core.search.toJsonObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadStatus { QUEUED, DOWNLOADING, DONE, FAILED }

data class DownloadEntry(
    val track: DemoTrack,
    val fileName: String,
    val status: DownloadStatus,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null,
) {
    val key: String get() = track.libraryKey()
    val progress: Float
        get() = when {
            status == DownloadStatus.DONE -> 1f
            totalBytes > 0L -> (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
            else -> 0f
        }
}

/**
 * 在线歌曲下载到应用专属外部目录，完成后播放器优先读本地文件。
 */
class DownloadStore(
    context: Context,
    private val resolveUrl: suspend (track: DemoTrack) -> String?,
) {
    private val appContext = context.applicationContext
    private val dir = (
        appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?.let { File(it, "downloads") }
            ?: File(appContext.filesDir, "downloads")
        ).also { it.mkdirs() }
    private val indexFile = File(dir, "downloads.json")

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val slots = Semaphore(2)
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<DownloadEntry>> = _entries.asStateFlow()

    init {
        dir.listFiles { file -> file.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
    }

    fun entryFor(track: DemoTrack): DownloadEntry? =
        _entries.value.firstOrNull { it.key == track.libraryKey() }

    fun pathFor(track: DemoTrack): String? {
        val entry = entryFor(track) ?: return null
        if (entry.status != DownloadStatus.DONE) return null
        val file = File(dir, entry.fileName)
        return if (file.isFile && file.length() > 0L) file.absolutePath else null
    }

    fun enqueue(track: DemoTrack) {
        if (!track.isOnline || track.title.isBlank()) return
        val key = track.libraryKey()
        if (_entries.value.any { it.key == key && it.status != DownloadStatus.FAILED }) return

        val fileName = uniqueFileName(track)
        val entry = DownloadEntry(track, fileName, DownloadStatus.QUEUED)
        _entries.update { current -> listOf(entry) + current.filterNot { it.key == key } }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                slots.withPermit { download(entry) }
            } finally {
                jobs.remove(key, currentCoroutineContext()[Job])
            }
        }
        jobs[key] = job
        job.start()
    }

    fun cancel(track: DemoTrack) {
        val key = track.libraryKey()
        val entry = entryFor(track) ?: return
        _entries.update { current -> current.filterNot { it.key == key } }
        jobs.remove(key)?.cancel()
        File(dir, entry.fileName).delete()
        File(dir, entry.fileName + PART_SUFFIX).delete()
    }

    fun delete(track: DemoTrack) {
        val key = track.libraryKey()
        val entry = entryFor(track) ?: return
        _entries.update { current -> current.filterNot { it.key == key } }
        File(dir, entry.fileName).delete()
        File(dir, entry.fileName + PART_SUFFIX).delete()
        persist()
    }

    private suspend fun download(entry: DownloadEntry) {
        val key = entry.key
        val track = entry.track
        val part = File(dir, entry.fileName + PART_SUFFIX)
        try {
            update(key) { it.copy(status = DownloadStatus.QUEUED, error = null, bytesRead = 0L, totalBytes = 0L) }
            val url = resolveUrl(track)
            currentCoroutineContext().ensureActive()
            if (url.isNullOrBlank()) {
                fail(key, "未找到可用音源，换一首或检查音源脚本")
                return
            }
            if (looksLikeHls(url)) {
                fail(key, "该音源是 HLS 流，暂不支持离线下载")
                return
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    fail(key, "下载失败：HTTP ${response.code}")
                    return
                }
                val type = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                if (type.startsWith("text/") || type.contains("json") || type.contains("html")) {
                    fail(key, "音源返回的不是音频（${type.substringBefore(';')}）")
                    return
                }
                val body = response.body ?: run {
                    fail(key, "下载失败：响应为空")
                    return
                }
                val total = body.contentLength()
                val ext = audioExtension(url, type)
                if (!entry.fileName.endsWith(".$ext", ignoreCase = true)) {
                    // 实际类型与预判不一致时改名，避免扩展名误导播放器与用户
                    val renamed = File(dir, entry.fileName.substringBeforeLast('.') + "." + ext)
                    if (!renamed.exists()) {
                        update(key) { it.copy(fileName = renamed.name) }
                    }
                }
                part.delete()
                var read = 0L
                var lastNotify = 0L
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            read += count
                            val now = System.currentTimeMillis()
                            if (now - lastNotify >= 250L) {
                                lastNotify = now
                                update(key) {
                                    it.copy(
                                        status = DownloadStatus.DOWNLOADING,
                                        bytesRead = read,
                                        totalBytes = total.coerceAtLeast(0L),
                                    )
                                }
                            }
                        }
                    }
                }
                if (read < MIN_BYTES) {
                    fail(key, "下载内容过小，可能不是有效音频")
                    return
                }
                val target = resolvedTargetFile(key)
                if (target == null) {
                    fail(key, "下载已取消")
                    return
                }
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                update(key) {
                    it.copy(
                        fileName = target.name,
                        status = DownloadStatus.DONE,
                        bytesRead = target.length(),
                        totalBytes = target.length(),
                        error = null,
                    )
                }
                persist()
                Log.i(TAG, "downloaded $key -> ${target.name} (${target.length()} bytes)")
            }
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (t: Throwable) {
            fail(key, t.message ?: "下载失败")
            Log.w(TAG, "download failed $key", t)
        } finally {
            if (_entries.value.none { it.key == key && it.status == DownloadStatus.DONE }) {
                part.delete()
            }
        }
    }

    private fun resolvedTargetFile(key: String): File? {
        val entry = _entries.value.firstOrNull { it.key == key } ?: return null
        return File(dir, entry.fileName)
    }

    private fun update(key: String, block: (DownloadEntry) -> DownloadEntry) {
        _entries.update { list ->
            list.map { if (it.key == key) block(it) else it }
        }
    }

    private fun fail(key: String, message: String) {
        update(key) { it.copy(status = DownloadStatus.FAILED, error = message) }
    }

    private fun uniqueFileName(track: DemoTrack): String {
        val base = sanitizeFileName("${track.title} - ${track.artist}".trim().trimEnd('-', ' '))
            .ifBlank { sanitizeFileName(track.title) }
            .ifBlank { "track" }
            .take(MAX_NAME)
        val taken = _entries.value.map { it.fileName }.toSet()
        var name = "$base.mp3"
        var index = 2
        while (name in taken || File(dir, name).exists()) {
            name = "$base ($index).mp3"
            index++
        }
        return name
    }

    @Synchronized
    private fun persist() {
        val payload = JSONArray()
        _entries.value.filter { it.status == DownloadStatus.DONE }.forEach { entry ->
            payload.put(
                JSONObject()
                    .put("track", entry.track.toJsonObject())
                    .put("file", entry.fileName)
                    .put("size", entry.bytesRead),
            )
        }
        runCatching {
            val temp = File(dir, indexFile.name + ".tmp")
            temp.writeText(payload.toString())
            if (!temp.renameTo(indexFile)) {
                indexFile.writeText(temp.readText())
                temp.delete()
            }
        }.onFailure { Log.w(TAG, "persist failed", it) }
    }

    private fun load(): List<DownloadEntry> {
        val text = runCatching { if (indexFile.isFile) indexFile.readText() else "" }.getOrDefault("")
        if (text.isBlank()) return emptyList()
        val loaded = runCatching {
            val array = JSONArray(text)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val track = item.optJSONObject("track")?.toDemoTrack() ?: continue
                    val fileName = item.optString("file")
                    val file = File(dir, fileName)
                    if (fileName.isBlank() || !file.isFile || file.length() <= 0L) continue
                    add(
                        DownloadEntry(
                            track = track,
                            fileName = fileName,
                            status = DownloadStatus.DONE,
                            bytesRead = file.length(),
                            totalBytes = file.length(),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        return loaded
    }

    private companion object {
        const val TAG = "TvMusicDownload"
        const val PART_SUFFIX = ".part"
        const val MIN_BYTES = 32L * 1024L
        const val MAX_NAME = 80
        const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

internal fun looksLikeHls(url: String): Boolean {
    val path = url.toHttpUrlOrNull()?.encodedPath?.lowercase(Locale.ROOT) ?: return false
    return path.endsWith(".m3u8")
}

internal fun sanitizeFileName(raw: String): String {
    val cleaned = raw.map { char ->
        when {
            char.code < 0x20 -> ' '
            char in "\\/:*?\"<>|" -> '_'
            else -> char
        }
    }.joinToString("")
    return cleaned.trim().trimEnd('.', ' ').take(MAX_FILE_NAME)
}

internal fun audioExtension(url: String, contentType: String): String {
    val type = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
    val fromType = when {
        type.contains("flac") -> "flac"
        type.contains("mpeg") -> "mp3"
        type.contains("mp4") || type.contains("aac") -> "m4a"
        type.contains("ogg") -> "ogg"
        type.contains("wav") -> "wav"
        else -> null
    }
    val path = runCatching {
        url.toHttpUrlOrNull()?.encodedPath?.lowercase(Locale.ROOT)
    }.getOrNull().orEmpty()
    val fromPath = listOf("flac", "mp3", "m4a", "aac", "ogg", "wav")
        .firstOrNull { path.endsWith(".$it") }
    return fromType ?: fromPath ?: "mp3"
}

private const val MAX_FILE_NAME = 80
