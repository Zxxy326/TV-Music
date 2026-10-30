package tv.music.player.core.companion

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface CompanionEvent {
    data class Search(val query: String) : CompanionEvent
    data class Message(val text: String) : CompanionEvent
    data object OpenPlayer : CompanionEvent
}

class CompanionBridge {
    private val _events = MutableSharedFlow<CompanionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CompanionEvent> = _events.asSharedFlow()

    private val _pendingQuery = MutableStateFlow<String?>(null)
    val pendingQuery: StateFlow<String?> = _pendingQuery.asStateFlow()

    fun requestSearch(query: String) {
        val term = query.trim()
        if (term.isEmpty()) return
        _pendingQuery.value = term
        _events.tryEmit(CompanionEvent.Search(term))
    }

    fun consumeQuery() {
        _pendingQuery.value = null
    }

    fun openPlayer() {
        _events.tryEmit(CompanionEvent.OpenPlayer)
    }

    fun message(text: String) {
        if (text.isNotBlank()) _events.tryEmit(CompanionEvent.Message(text))
    }
}
