package tv.music.player.core.search

import android.content.Context

class SearchHistoryStore(
    context: Context,
    private val limit: Int = 8,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("search_history", Context.MODE_PRIVATE)

    fun load(): List<String> = decode(prefs.getString(KEY, "").orEmpty())

    fun add(term: String): List<String> {
        val next = merge(load(), term, limit)
        prefs.edit().putString(KEY, encode(next)).apply()
        return next
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "recent_terms"
        private const val SEPARATOR = '\u001F'
        const val MAX_TERM_LENGTH = 24

        fun encode(items: List<String>): String = items.joinToString(SEPARATOR.toString())

        fun decode(raw: String): List<String> =
            raw.split(SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(8)

        fun merge(existing: List<String>, term: String, limit: Int = 8): List<String> {
            val clean = term.trim().take(MAX_TERM_LENGTH)
            if (clean.isEmpty()) return existing.take(limit)
            return buildList {
                add(clean)
                existing.forEach { add(it) }
            }.distinct().take(limit)
        }
    }
}
