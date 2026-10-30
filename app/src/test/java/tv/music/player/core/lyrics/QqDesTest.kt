package tv.music.player.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class QqDesTest {

    @Test
    fun decryptsQrcFixture() {
        val cipher = hex(
            "005e48ef96b85fc2d42dcdf2e21f0d5d977de00c85449a7c535da3bcf1beb3ca" +
                "91fe72157998641a7f62c345acb3d48f9ba0b7a183086bf74dd612acfc1ffb48" +
                "f97c21abca459f1fea0160b99e5c7e77",
        )
        val compressed = QqDes.decryptQrc(cipher)
        val xml = String(QqDes.inflate(compressed), Charsets.UTF_8)
        assertEquals(
            """<QrcInfos><LyricInfo><Lyric_1 LyricContent="[0,1000]晴(0,500)天(500,500)"/></LyricInfo></QrcInfos>""",
            xml,
        )
    }

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
}
