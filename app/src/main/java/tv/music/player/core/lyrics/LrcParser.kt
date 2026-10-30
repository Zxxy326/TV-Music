package tv.music.player.core.lyrics

private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)

private val INSTRUMENTAL_MARKERS = listOf(
    "纯音乐，请欣赏",
    "纯音乐,请欣赏",
    "此歌曲为没有填词的纯音乐",
    "暂无歌词",
)

/**
 * 兼容标准 LRC、多时间戳行、[offset] 与 LX 的 "主词//翻译" 行内格式。
 */
object LrcParser {

    fun parse(
        lrc: String?,
        translation: String? = null,
        romanization: String? = null,
    ): Lyrics {
        if (lrc.isNullOrBlank()) return Lyrics()

        val fileOffset = OFFSET_TAG.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val trans = TimedText.parse(translation)
        val roma = TimedText.parse(romanization)
        val merged = LinkedHashMap<Long, LyricLine>()

        for (cue in TimedText.parseCues(lrc, keepInline = true)) {
            if (cue.text.isBlank()) continue
            val split = cue.text.split("//", limit = 2)
            val text = split[0].trim()
            if (text.isBlank()) continue
            val inline = split.getOrNull(1)?.trim().orEmpty()
            val secondary = when {
                inline.isNotBlank() -> inline
                trans.exact(cue.timeMs) != null -> trans.exact(cue.timeMs)
                roma.exact(cue.timeMs) != null -> roma.exact(cue.timeMs)
                else -> trans.nearest(cue.timeMs) ?: roma.nearest(cue.timeMs)
            }
            val time = cue.timeMs + fileOffset
            if (time < 0) continue
            val line = LyricLine(time, text, secondary?.takeIf { it.isNotBlank() })
            val existing = merged[time]
            if (existing == null || (existing.translation.isNullOrBlank() && !line.translation.isNullOrBlank())) {
                merged[time] = line
            }
        }

        val lines = merged.values.sortedBy { it.timeMs }
        if (lines.isNotEmpty() && lines.all { it.text.isInstrumentalMarker() }) {
            return Lyrics(instrumental = true)
        }
        return Lyrics(lines = lines)
    }

    private fun String.isInstrumentalMarker(): Boolean =
        INSTRUMENTAL_MARKERS.any { contains(it) }

    private data class Cue(val timeMs: Long, val text: String)

    private class TimedText(private val byTime: Map<Long, String>) {
        fun exact(timeMs: Long): String? = byTime[timeMs]

        fun nearest(timeMs: Long): String? {
            var best: String? = null
            var bestDelta = 150L
            for ((time, text) in byTime) {
                val delta = kotlin.math.abs(time - timeMs)
                if (delta <= bestDelta) {
                    bestDelta = delta
                    best = text
                }
            }
            return best
        }

        companion object {
            fun parse(raw: String?): TimedText {
                val map = HashMap<Long, String>()
                parseCues(raw, keepInline = false).forEach { map[it.timeMs] = it.text }
                return TimedText(map)
            }

            fun parseCues(raw: String?, keepInline: Boolean = false): List<Cue> {
                if (raw.isNullOrBlank()) return emptyList()
                val cues = ArrayList<Cue>()
                raw.lineSequence().forEach { line ->
                    val tags = TIME_TAG.findAll(line).toList()
                    if (tags.isEmpty()) return@forEach
                    var text = line.substring(tags.last().range.last + 1).trim()
                    if (!keepInline) {
                        if (text.startsWith("//")) text = ""
                        text = text.split("//").first().trim()
                    }
                    if (text.isBlank() || text == "//") return@forEach
                    tags.forEach { tag ->
                        cues += Cue(tag.timeMs(), text)
                    }
                }
                return cues
            }
        }
    }

    internal fun MatchResult.timeMs(): Long {
        val minutes = groupValues[1].toLongOrNull() ?: 0L
        val seconds = groupValues[2].toLongOrNull() ?: 0L
        val fraction = groupValues[3]
        val millis = when (fraction.length) {
            1 -> fraction.toLongOrNull()?.times(100) ?: 0L
            2 -> fraction.toLongOrNull()?.times(10) ?: 0L
            3 -> fraction.toLongOrNull() ?: 0L
            else -> 0L
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }
}
