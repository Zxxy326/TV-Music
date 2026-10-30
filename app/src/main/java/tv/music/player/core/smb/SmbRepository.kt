package tv.music.player.core.smb

import jcifs.CIFSContext
import jcifs.context.BaseContext
import jcifs.config.PropertyConfiguration
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.music.player.core.playback.DemoTrack
import java.io.IOException
import java.util.Properties
import kotlin.math.abs

/** jcifs-ng 直连 SMB2/3 共享，只读元数据并返回 smb:// URI，播放时按需流式读取。 */
class SmbRepository {
    private var cachedKey: String? = null
    private var cachedContext: CIFSContext? = null

    suspend fun scan(config: SmbConfig): List<DemoTrack> = withContext(Dispatchers.IO) {
        val context = buildContext(config)
        val root = try {
            SmbFile(config.rootUrl, context)
        } catch (t: Throwable) {
            throw IOException("SMB 地址无效：${t.message.orEmpty()}", t)
        }
        if (!root.exists()) throw IOException("无法访问共享，请检查地址、权限与网络")
        if (!root.isDirectory) throw IOException("目标不是文件夹")
        val result = mutableListOf<DemoTrack>()
        walk(root, config, result, 0)
        result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    fun openStream(config: SmbConfig, url: String) = SmbFile(url, buildContext(config)).inputStream

    fun length(config: SmbConfig, url: String): Long = SmbFile(url, buildContext(config)).length()

    @Synchronized
    fun buildContext(config: SmbConfig): CIFSContext {
        val key = "${config.rootUrl}|${config.domain}|${config.user}|${config.password.hashCode()}"
        cachedContext?.let { if (cachedKey == key) return it }
        runCatching { cachedContext?.close() }
        cachedKey = null
        cachedContext = null
        val context = createContext(config)
        cachedKey = key
        cachedContext = context
        return context
    }

    private fun createContext(config: SmbConfig): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.useSMB2Negotiation", "true")
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.connTimeout", "10000")
            setProperty("jcifs.smb.client.responseTimeout", "15000")
            setProperty("jcifs.smb.client.soTimeout", "15000")
            setProperty("jcifs.netbios.cachePolicy", "0")
        }
        val base = BaseContext(PropertyConfiguration(props))
        val user = config.user.ifBlank { "guest" }
        return base.withCredentials(
            NtlmPasswordAuthenticator(config.domain, user, config.password)
        )
    }

    private fun walk(
        dir: SmbFile,
        config: SmbConfig,
        out: MutableList<DemoTrack>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_TRACKS) return
        val children = try {
            dir.listFiles()
        } catch (t: Throwable) {
            throw IOException("读取共享目录失败：${t.message.orEmpty()}", t)
        } ?: return
        for (child in children) {
            if (out.size >= MAX_TRACKS) return
            if (child.isDirectory) {
                walk(child, config, out, depth + 1)
            } else if (child.isFile && isAudio(child.name)) {
                val url = child.getURL().toString()
                val hash = abs(url.hashCode()).coerceAtLeast(1)
                out += DemoTrack(
                    id = -hash,
                    title = child.name.substringBeforeLast('.'),
                    artist = "SMB 共享",
                    album = config.share,
                    durationMs = 0L,
                    colorSeed = hash % 98,
                    localUri = url,
                    localSource = SOURCE_SMB,
                )
            }
        }
    }

    private fun isAudio(name: String): Boolean {
        val lower = name.lowercase()
        return AUDIO_EXTENSIONS.any { lower.endsWith(it) }
    }

    companion object {
        const val SOURCE_SMB = "SMB"
        private const val MAX_DEPTH = 8
        private const val MAX_TRACKS = 2000
        private val AUDIO_EXTENSIONS = listOf(".mp3", ".flac", ".m4a", ".aac", ".ogg", ".wav", ".opus")
    }
}
