package tv.music.player.core.search

/**
 * 九宫格多击输入：2=ABC … 9=WXYZ，连按同一个键在字母间循环。
 * 两次按键超过 [timeoutMs] 视为新字母。
 */
class PinyinT9(private val timeoutMs: Long = 900L) {
    private var digit: Char? = null
    private var index: Int = 0
    private var lastAt: Long = 0L
    private var lastLetter: Char? = null

    fun reset() {
        digit = null
        index = 0
        lastAt = 0L
        lastLetter = null
    }

    fun type(digit: Char, query: String, now: Long): String {
        val letters = lettersFor(digit) ?: return query
        val previous = lastLetter
        val cycling = digit == this.digit &&
            now - lastAt in 0..timeoutMs &&
            previous != null && query.endsWith(previous)
        val nextIndex = if (cycling) (index + 1) % letters.length else 0
        val nextLetter = letters[nextIndex]
        this.digit = digit
        index = nextIndex
        lastAt = now
        lastLetter = nextLetter
        return (if (cycling) query.dropLast(1) else query) + nextLetter
    }

    companion object {
        private val MAP = mapOf(
            '2' to "abc",
            '3' to "def",
            '4' to "ghi",
            '5' to "jkl",
            '6' to "mno",
            '7' to "pqrs",
            '8' to "tuv",
            '9' to "wxyz",
        )

        fun lettersFor(digit: Char): String? = MAP[digit]
    }
}
