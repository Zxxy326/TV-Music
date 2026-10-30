package tv.music.player.core.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.search.decodeTracks
import tv.music.player.core.search.encodeTracks
import java.io.File
import kotlin.math.abs

object AudioPermission {
    val name: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
}

/**
 * 设备音乐索引：MediaStore 扫描 + SAF 文件夹递归导入。
 * 只保存元数据与 content:// URI，不复制音频。
 */
class LocalLibraryStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("local_library", Context.MODE_PRIVATE)
    private val dir = File(appContext.filesDir, "local").also { it.mkdirs() }
    private val indexFile = File(dir, "library.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tracks = MutableStateFlow(loadCached())
    val tracks: StateFlow<List<DemoTrack>> = _tracks.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun scanDevice() {
        if (_scanning.value) return
        scope.launch {
            _scanning.value = true
            _message.value = null
            try {
                val found = queryMediaStore() + scanSavedTrees()
                val merged = (found + _tracks.value.filter { it.localSource == SOURCE_SAF || it.localSource == SOURCE_SMB })
                    .distinctBy { it.localUri }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                _tracks.value = merged
                persist(merged)
                _message.value = if (merged.isEmpty()) {
                    "没有发现音频文件，可试试“添加文件夹”"
                } else {
                    "已扫描 ${merged.size} 首"
                }
            } catch (t: Throwable) {
                Log.w(TAG, "scan failed", t)
                _message.value = "扫描失败：${t.message ?: "无法读取存储"}"
            } finally {
                _scanning.value = false
            }
        }
    }

    fun replaceSourceTracks(source: String, tracks: List<DemoTrack>) {
        val merged = (_tracks.value.filterNot { it.localSource == source } + tracks)
            .distinctBy { it.localUri }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        _tracks.value = merged
        persist(merged)
    }

    fun addTree(uri: Uri) {
        val resolver = appContext.contentResolver
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
        val saved = treeUris().toMutableList()
        if (!saved.contains(uri.toString())) saved += uri.toString()
        prefs.edit().putStringSet(KEY_TREES, saved.toSet()).apply()
        scope.launch {
            _scanning.value = true
            _message.value = null
            try {
                val imported = scanTree(uri)
                val merged = (_tracks.value + imported)
                    .distinctBy { it.localUri }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                _tracks.value = merged
                persist(merged)
                _message.value = if (imported.isEmpty()) "该文件夹没有音频文件" else "已添加 ${imported.size} 首"
            } catch (t: Throwable) {
                Log.w(TAG, "tree scan failed", t)
                _message.value = "读取文件夹失败：${t.message ?: "权限已失效"}"
            } finally {
                _scanning.value = false
            }
        }
    }

    private suspend fun queryMediaStore(): List<DemoTrack> = withContext(Dispatchers.IO) {
        if (!AudioPermission.granted(appContext)) {
            throw SecurityException("未授予音频读取权限")
        }
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 20000"
        val result = mutableListOf<DemoTrack>()
        appContext.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                result += localTrack(
                    uri = uri,
                    title = cursor.getString(titleCol).orEmpty(),
                    artist = cursor.getString(artistCol).orEmpty(),
                    album = cursor.getString(albumCol).orEmpty(),
                    durationMs = cursor.getLong(durationCol),
                    source = SOURCE_MEDIA,
                )
            }
        }
        result
    }

    private suspend fun scanSavedTrees(): List<DemoTrack> = withContext(Dispatchers.IO) {
        treeUris().flatMap { raw ->
            runCatching { scanTree(Uri.parse(raw)) }.getOrDefault(emptyList())
        }
    }

    private suspend fun scanTree(tree: Uri): List<DemoTrack> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(appContext, tree) ?: return@withContext emptyList()
        val result = mutableListOf<DemoTrack>()
        walk(root, result, depth = 0)
        result
    }

    private fun walk(dir: DocumentFile, out: MutableList<DemoTrack>, depth: Int) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (out.size >= MAX_FILES) return
            if (child.isDirectory) {
                walk(child, out, depth + 1)
            } else if (child.isFile && isAudio(child.name, child.type)) {
                val uri = child.uri.toString()
                if (out.any { it.localUri == uri }) continue
                val name = child.name.orEmpty()
                out += localTrack(
                    uri = uri,
                    title = name.substringBeforeLast('.'),
                    artist = "外部文件夹",
                    album = dir.name.orEmpty(),
                    durationMs = 0L,
                    source = SOURCE_SAF,
                )
            }
        }
    }

    private fun localTrack(
        uri: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        source: String,
    ): DemoTrack {
        val hash = abs(uri.hashCode()).coerceAtLeast(1)
        val cleanTitle = title.trim().ifBlank { "未命名音频" }
        return DemoTrack(
            id = -hash,
            title = cleanTitle,
            artist = artist.trim().let { if (it.isBlank() || it == "<unknown>") "未知歌手" else it },
            album = album.trim(),
            durationMs = durationMs.coerceAtLeast(0L),
            colorSeed = hash % 98,
            localUri = uri,
            localSource = source,
        )
    }

    private fun treeUris(): List<String> =
        prefs.getStringSet(KEY_TREES, emptySet()).orEmpty().sorted()

    private fun loadCached(): List<DemoTrack> = runCatching {
        if (!indexFile.isFile) emptyList()
        else decodeTracks(indexFile.readText()).orEmpty()
    }.getOrDefault(emptyList())

    private fun persist(tracks: List<DemoTrack>) {
        scope.launch {
            runCatching {
                val temp = File(dir, indexFile.name + ".tmp")
                temp.writeText(tracks.encodeTracks())
                if (!temp.renameTo(indexFile)) {
                    indexFile.writeText(temp.readText())
                    temp.delete()
                }
            }.onFailure { Log.w(TAG, "persist failed", it) }
        }
    }

    private fun isAudio(name: String?, mime: String?): Boolean {
        val type = mime.orEmpty().lowercase()
        if (type.startsWith("audio/")) return true
        val lower = name.orEmpty().lowercase()
        return AUDIO_EXTENSIONS.any { lower.endsWith(it) }
    }

    private companion object {
        const val TAG = "TvMusicLocal"
        const val KEY_TREES = "tree_uris"
        const val SOURCE_MEDIA = "媒体库"
        const val SOURCE_SAF = "文件夹"
        const val SOURCE_SMB = "SMB"
        const val MAX_DEPTH = 8
        const val MAX_FILES = 5000
        val AUDIO_EXTENSIONS = listOf(".mp3", ".flac", ".m4a", ".aac", ".ogg", ".wav", ".opus")
    }
}
