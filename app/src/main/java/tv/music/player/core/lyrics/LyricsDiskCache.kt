package tv.music.player.core.lyrics

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

class LyricsDiskCache(
    private val dir: File,
    private val ttlMs: Long = 14L * 24 * 60 * 60 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun read(songmid: String): Lyrics? = runCatching {
        val file = fileFor(songmid)
        if (!file.isFile) return@runCatching null
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != VERSION) return@runCatching null
            val savedAt = input.readLong()
            if (savedAt <= 0L || clock() - savedAt !in 0..ttlMs) {
                file.delete()
                return@runCatching null
            }
            val instrumental = input.readBoolean()
            val source = input.readUTF()
            val lineCount = input.readInt()
            if (lineCount !in 0..MAX_LINES) return@runCatching null
            val lines = ArrayList<LyricLine>(lineCount)
            repeat(lineCount) {
                val time = input.readLong()
                val duration = input.readLong()
                val text = input.readUTF()
                val translation = if (input.readBoolean()) input.readUTF() else null
                val wordCount = input.readInt()
                if (wordCount !in 0..MAX_WORDS) return@runCatching null
                val words = ArrayList<LyricWord>(wordCount)
                repeat(wordCount) {
                    words += LyricWord(input.readLong(), input.readLong(), input.readUTF())
                }
                lines += LyricLine(
                    timeMs = time,
                    text = text,
                    translation = translation,
                    durationMs = duration,
                    words = words,
                )
            }
            if (lines.isEmpty() && !instrumental) return@runCatching null
            Lyrics(lines = lines, instrumental = instrumental, sourceLabel = source)
        }
    }.getOrNull()

    fun write(songmid: String, lyrics: Lyrics) {
        if (lyrics.lines.isEmpty() && !lyrics.instrumental) return
        runCatching {
            dir.mkdirs()
            val target = fileFor(songmid)
            val temp = File(dir, target.name + ".tmp")
            DataOutputStream(BufferedOutputStream(temp.outputStream())).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeLong(clock())
                out.writeBoolean(lyrics.instrumental)
                out.writeUTF(lyrics.sourceLabel)
                out.writeInt(lyrics.lines.size)
                lyrics.lines.forEach { line ->
                    out.writeLong(line.timeMs)
                    out.writeLong(line.durationMs)
                    out.writeUTF(line.text)
                    out.writeBoolean(line.translation != null)
                    line.translation?.let(out::writeUTF)
                    out.writeInt(line.words.size)
                    line.words.forEach { word ->
                        out.writeLong(word.timeMs)
                        out.writeLong(word.durationMs)
                        out.writeUTF(word.text)
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) temp.delete()
            prune()
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(songmid: String): File {
        val name = songmid.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("")
        return File(dir, "$name.lrc")
    }

    private fun prune() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_FILES)
            .forEach { it.delete() }
    }

    private companion object {
        const val MAGIC = 0x54564C43
        const val VERSION = 1
        const val MAX_LINES = 20_000
        const val MAX_WORDS = 20_000
        const val MAX_FILES = 240
    }
}
