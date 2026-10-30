package tv.music.player.core.library

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.search.decodeTracks
import tv.music.player.core.search.encodeTracks
import java.io.File
import java.util.concurrent.Executors

fun DemoTrack.libraryKey(): String = when {
    localUri.isNotBlank() -> "local:$localUri"
    isOnline -> "online:$onlineSource:$onlineSongId"
    else -> "demo:$id"
}

class LibraryStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "library").also { it.mkdirs() }
    private val favoritesFile = File(dir, "favorites.json")
    private val recentFile = File(dir, "recent.json")
    private val executor = Executors.newSingleThreadExecutor()

    private val _favorites = MutableStateFlow(read(favoritesFile))
    val favorites: StateFlow<List<DemoTrack>> = _favorites.asStateFlow()

    private val _recent = MutableStateFlow(read(recentFile))
    val recent: StateFlow<List<DemoTrack>> = _recent.asStateFlow()

    fun isFavorite(track: DemoTrack): Boolean =
        _favorites.value.any { it.libraryKey() == track.libraryKey() }

    @Synchronized
    fun toggleFavorite(track: DemoTrack): Boolean {
        val key = track.libraryKey()
        val current = _favorites.value
        val existing = current.any { it.libraryKey() == key }
        val next = if (existing) current.filterNot { it.libraryKey() == key } else listOf(track) + current
        _favorites.value = next
        write(favoritesFile, next)
        return !existing
    }

    @Synchronized
    fun recordPlayed(track: DemoTrack) {
        if (track.title.isBlank()) return
        val key = track.libraryKey()
        val next = (listOf(track) + _recent.value.filterNot { it.libraryKey() == key })
            .take(MAX_RECENT)
        _recent.value = next
        write(recentFile, next)
    }

    private fun read(file: File): List<DemoTrack> = runCatching {
        if (!file.isFile) emptyList() else decodeTracks(file.readText()).orEmpty()
    }.getOrDefault(emptyList())

    private fun write(file: File, tracks: List<DemoTrack>) {
        executor.execute {
            runCatching {
                val temp = File(dir, file.name + ".tmp")
                temp.writeText(tracks.encodeTracks())
                if (!temp.renameTo(file)) {
                    file.writeText(temp.readText())
                    temp.delete()
                }
            }
        }
    }

    private companion object {
        const val MAX_RECENT = 30
    }
}
