package tv.music.player.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class QqDesBlockTest {

    private val keyA = byteArrayOf(0x21, 0x40, 0x23, 0x29, 0x28, 0x2A, 0x24, 0x25)
    private val keyB = byteArrayOf(0x31, 0x32, 0x33, 0x5A, 0x58, 0x43, 0x21, 0x40)
    private val keyC = byteArrayOf(0x21, 0x40, 0x23, 0x29, 0x28, 0x4E, 0x48, 0x4C)

    @Test
    fun decryptBlockMatchesReference() {
        val input = hex("3ad1c0e6d4425208")
        assertEquals("067e838db4be7e48", QqDes.cryptBlockForTest(input, keyC, true).toHex())
    }

    @Test
    fun encryptBlockMatchesReference() {
        assertEquals(
            "81abb646b909e4df",
            QqDes.cryptBlockForTest(ByteArray(8), keyB, false).toHex(),
        )
        assertEquals(
            "a2753fc39b04be29",
            QqDes.cryptBlockForTest(hex("0123456789abcdef"), keyA, false).toHex(),
        )
    }

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
