package tv.music.player.core.lyrics

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * QQ 音乐 QRC 使用的改版 DES（S2 盒与标准 DES 不同），
 * 移植自 qrcDecrypt / LyricDecoder 的三段式解密。仅用于歌词解密。
 */
object QqDes {

    private val KEY_A = byteArrayOf(0x21, 0x40, 0x23, 0x29, 0x28, 0x2A, 0x24, 0x25)
    private val KEY_B = byteArrayOf(0x31, 0x32, 0x33, 0x5A, 0x58, 0x43, 0x21, 0x40)
    private val KEY_C = byteArrayOf(0x21, 0x40, 0x23, 0x29, 0x28, 0x4E, 0x48, 0x4C)
    private val SHIFTS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)

private val S_BOXES = arrayOf(
    intArrayOf(14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7, 0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8, 4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0, 15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13),
    intArrayOf(15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10, 3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5, 0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15, 13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9),
    intArrayOf(10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8, 13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1, 13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7, 1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12),
    intArrayOf(7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15, 13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9, 10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4, 3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14),
    intArrayOf(2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9, 14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6, 4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14, 11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3),
    intArrayOf(12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11, 10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8, 9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6, 4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13),
    intArrayOf(4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1, 13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6, 1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2, 6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12),
    intArrayOf(13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7, 1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2, 7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8, 2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11),
)

private val IP0 = intArrayOf(
    57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
    61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7,
)

private val IP1 = intArrayOf(
    56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2,
    60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6,
)

private val P_TABLE = intArrayOf(
    15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9,
    1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24,
)

private val KEY_PERM_C = intArrayOf(
    56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1,
    58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35,
)

private val KEY_PERM_D = intArrayOf(
    62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5,
    60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3,
)

private val KEY_COMPRESSION = intArrayOf(
    13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3,
    25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46, 54, 29, 39,
    50, 44, 32, 47, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31,
)

    fun cryptBlockForTest(block: ByteArray, key: ByteArray, decrypt: Boolean): ByteArray {
        val out = block.copyOf()
        cryptBlock(out, 0, schedule(key, decrypt))
        return out
    }

    fun decryptQrc(data: ByteArray): ByteArray {
        require(data.size % 8 == 0) { "QRC 密文长度不是 8 的倍数" }
        val out = data.copyOf()
        val scheduleC = schedule(KEY_C, decrypt = true)
        val scheduleB = schedule(KEY_B, decrypt = false)
        val scheduleA = schedule(KEY_A, decrypt = true)
        var offset = 0
        while (offset + 8 <= out.size) {
            cryptBlock(out, offset, scheduleC)
            cryptBlock(out, offset, scheduleB)
            cryptBlock(out, offset, scheduleA)
            offset += 8
        }
        return out
    }

    fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 4)
            val buffer = ByteArray(16 * 1024)
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                if (read == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buffer, 0, read)
                }
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun schedule(key: ByteArray, decrypt: Boolean): Array<IntArray> {
        var c = 0
        var d = 0
        for (i in 0 until 28) {
            c = c or bitNum(key, KEY_PERM_C[i], 31 - i)
            d = d or bitNum(key, KEY_PERM_D[i], 31 - i)
        }
        val schedule = Array(16) { IntArray(6) }
        for (i in 0 until 16) {
            val shift = SHIFTS[i]
            c = ((c shl shift) or (c ushr (28 - shift))) and 0xFFFFFFF0.toInt()
            d = ((d shl shift) or (d ushr (28 - shift))) and 0xFFFFFFF0.toInt()
            val target = if (decrypt) 15 - i else i
            val sub = schedule[target]
            for (j in 0 until 24) {
                sub[j / 8] = sub[j / 8] or bitNumIntR(c, KEY_COMPRESSION[j], 7 - (j % 8))
            }
            for (j in 24 until 48) {
                sub[j / 8] = sub[j / 8] or bitNumIntR(d, KEY_COMPRESSION[j] - 27, 7 - (j % 8))
            }
        }
        return schedule
    }

    private fun cryptBlock(bytes: ByteArray, offset: Int, schedule: Array<IntArray>) {
        val block = ByteArray(8)
        System.arraycopy(bytes, offset, block, 0, 8)
        val state = IntArray(2)
        ip(state, block)
        for (i in 0 until 15) {
            val t = state[1]
            val value = f(state[1], schedule[i])
            state[1] = value xor state[0]
            state[0] = t
        }
        state[0] = f(state[1], schedule[15]) xor state[0]
        invIp(state, block)
        System.arraycopy(block, 0, bytes, offset, 8)
    }

    private fun ip(state: IntArray, bytes: ByteArray) {
        var s0 = 0
        var s1 = 0
        for (i in 0 until 32) {
            s0 = s0 or bitNum(bytes, IP0[i], 31 - i)
            s1 = s1 or bitNum(bytes, IP1[i], 31 - i)
        }
        state[0] = s0
        state[1] = s1
    }

    private fun invIp(state: IntArray, bytes: ByteArray) {
        val order = intArrayOf(3, 2, 1, 0, 7, 6, 5, 4)
        for (index in 0 until 8) {
            var value = 0
            for (round in 0 until 4) {
                val bit = 24 + index - round * 8
                value = (value shl 1) or ((state[1] ushr bit) and 1)
                value = (value shl 1) or ((state[0] ushr bit) and 1)
            }
            bytes[order[index]] = value.toByte()
        }
    }

    private fun f(state: Int, key: IntArray): Int {
        val t1 = bitNumIntL(state, 31, 0) or
            ((state and 0xF0000000.toInt()) ushr 1) or
            bitNumIntL(state, 4, 5) or
            bitNumIntL(state, 3, 6) or
            ((state and 0x0F000000) ushr 3) or
            bitNumIntL(state, 8, 11) or
            bitNumIntL(state, 7, 12) or
            ((state and 0x00F00000) ushr 5) or
            bitNumIntL(state, 12, 17) or
            bitNumIntL(state, 11, 18) or
            ((state and 0x000F0000) ushr 7) or
            bitNumIntL(state, 16, 23)

        val t2 = bitNumIntL(state, 15, 0) or
            ((state and 0x0000F000) shl 15) or
            bitNumIntL(state, 20, 5) or
            bitNumIntL(state, 19, 6) or
            ((state and 0x00000F00) shl 13) or
            bitNumIntL(state, 24, 11) or
            bitNumIntL(state, 23, 12) or
            ((state and 0x000000F0) shl 11) or
            bitNumIntL(state, 28, 17) or
            bitNumIntL(state, 27, 18) or
            ((state and 0x0000000F) shl 9) or
            bitNumIntL(state, 0, 23)

        val lrg = intArrayOf(
            (t1 ushr 24) and 0xFF,
            (t1 ushr 16) and 0xFF,
            (t1 ushr 8) and 0xFF,
            (t2 ushr 24) and 0xFF,
            (t2 ushr 16) and 0xFF,
            (t2 ushr 8) and 0xFF,
        )
        for (i in 0 until 6) lrg[i] = lrg[i] xor key[i]

        val boxed = (S_BOXES[0][sBoxBit(lrg[0] shr 2)] shl 28) or
            (S_BOXES[1][sBoxBit(((lrg[0] and 0x03) shl 4) or (lrg[1] shr 4))] shl 24) or
            (S_BOXES[2][sBoxBit(((lrg[1] and 0x0F) shl 2) or (lrg[2] shr 6))] shl 20) or
            (S_BOXES[3][sBoxBit(lrg[2] and 0x3F)] shl 16) or
            (S_BOXES[4][sBoxBit(lrg[3] shr 2)] shl 12) or
            (S_BOXES[5][sBoxBit(((lrg[3] and 0x03) shl 4) or (lrg[4] shr 4))] shl 8) or
            (S_BOXES[6][sBoxBit(((lrg[4] and 0x0F) shl 2) or (lrg[5] shr 6))] shl 4) or
            S_BOXES[7][sBoxBit(lrg[5] and 0x3F)]

        var value = 0
        for (c in 0 until 32) {
            value = value or bitNumIntL(boxed, P_TABLE[c], c)
        }
        return value
    }

    private fun sBoxBit(value: Int): Int {
        val part1 = value and 0x20
        val part2 = (value and 0x1F) shr 1
        val part3 = (value and 0x01) shl 4
        return part1 or part2 or part3
    }

    private fun bitNum(bytes: ByteArray, bit: Int, dest: Int): Int {
        val byteIndex = (bit / 32) * 4 + 3 - (bit % 32) / 8
        val bitPosition = 7 - (bit % 8)
        val extracted = ((bytes[byteIndex].toInt() and 0xFF) shr bitPosition) and 1
        return extracted shl dest
    }

    private fun bitNumIntR(value: Int, bit: Int, dest: Int): Int =
        ((value ushr (31 - bit)) and 1) shl dest

    private fun bitNumIntL(value: Int, bit: Int, dest: Int): Int =
        (((value shl bit) and 0x80000000.toInt()) ushr dest)
}
