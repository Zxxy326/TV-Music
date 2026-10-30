package tv.music.player.core.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerSnapshot(
    val track: DemoTrack = DemoCatalog.tracks.first(),
    val queue: List<DemoTrack> = DemoCatalog.tracks,
    val isPlaying: Boolean = false,
    val positionMs: Long = 32_000L,
) {
    val durationMs: Long get() = track.durationMs
    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * M0 的占位播放器：只驱动 UI 与键位验证，M1 由 Media3 播放服务替换。
 */
class DemoPlaybackController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerSnapshot())
    val state: StateFlow<PlayerSnapshot> = _state.asStateFlow()

    private var ticker: Job? = null

    fun toggle() {
        val playing = !_state.value.isPlaying
        _state.value = _state.value.copy(isPlaying = playing)
        if (playing) startTicker() else stopTicker()
    }

    fun play(track: DemoTrack) {
        _state.value = _state.value.copy(track = track, positionMs = 0L, isPlaying = true)
        startTicker()
    }

    fun next() {
        val snapshot = _state.value
        val index = snapshot.queue.indexOfFirst { it.id == snapshot.track.id }
        val nextIndex = if (index < 0) 0 else (index + 1) % snapshot.queue.size
        _state.value = snapshot.copy(track = snapshot.queue[nextIndex], positionMs = 0L)
        if (snapshot.isPlaying) startTicker()
    }

    fun previous() {
        val snapshot = _state.value
        if (snapshot.positionMs > 4_000L) {
            _state.value = snapshot.copy(positionMs = 0L)
            return
        }
        val index = snapshot.queue.indexOfFirst { it.id == snapshot.track.id }
        val prevIndex = if (index <= 0) snapshot.queue.lastIndex else index - 1
        _state.value = snapshot.copy(track = snapshot.queue[prevIndex], positionMs = 0L)
    }

    fun seekBy(deltaMs: Long) {
        val snapshot = _state.value
        _state.value = snapshot.copy(
            positionMs = (snapshot.positionMs + deltaMs).coerceIn(0L, snapshot.durationMs)
        )
    }

    fun seekTo(positionMs: Long) {
        val snapshot = _state.value
        _state.value = snapshot.copy(positionMs = positionMs.coerceIn(0L, snapshot.durationMs))
    }

    fun playFromQueue(track: DemoTrack) = play(track)

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(250L)
                val snapshot = _state.value
                if (!snapshot.isPlaying) break
                val position = snapshot.positionMs + 250L
                if (position >= snapshot.durationMs) {
                    next()
                } else {
                    _state.value = snapshot.copy(positionMs = position)
                }
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
