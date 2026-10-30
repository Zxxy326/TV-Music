package tv.music.player.core.source

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.music.player.BuildConfig
import tv.music.player.core.lx.LxFileStore
import tv.music.player.core.lx.LxJsEngine
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

data class MarketSource(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val url: String,
    val sha256: String,
)

data class MarketUiState(
    val loading: Boolean = false,
    val installingId: String? = null,
    val items: List<MarketSource> = emptyList(),
    val installedIds: Set<String> = emptySet(),
    val error: String? = null,
    val message: String? = null,
)

class SourceMarketStore(
    context: Context,
    private val fileStore: LxFileStore,
    private val engine: LxJsEngine,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("source_market", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _state = MutableStateFlow(
        MarketUiState(installedIds = prefs.getStringSet(KEY_INSTALLED, emptySet()).orEmpty())
    )
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    private var loading = false

    fun load() {
        if (loading || _state.value.installingId != null) return
        loading = true
        _state.value = _state.value.copy(loading = true, error = null)
        scope.launch {
            try {
                val items = fetchIndex()
                _state.value = _state.value.copy(loading = false, items = items, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "market load failed", t)
                _state.value = _state.value.copy(
                    loading = false,
                    error = "读取音源清单失败：${t.message ?: "网络不可用"}",
                )
            } finally {
                loading = false
            }
        }
    }

    fun install(source: MarketSource) {
        if (_state.value.installingId != null) return
        if (source.id in _state.value.installedIds) {
            _state.value = _state.value.copy(message = "「${source.name}」已安装")
            return
        }
        if (!isAllowedUrl(source.url)) {
            _state.value = _state.value.copy(message = "安装地址无效或不是 HTTPS")
            return
        }
        if (!SHA256_PATTERN.matches(source.sha256.lowercase(Locale.ROOT))) {
            _state.value = _state.value.copy(message = "清单缺少有效的 SHA-256，已拒绝安装")
            return
        }
        _state.value = _state.value.copy(installingId = source.id, message = null)
        scope.launch {
            try {
                val bytes = download(source.url)
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                if (!digest.equals(source.sha256, ignoreCase = true)) {
                    _state.value = _state.value.copy(
                        installingId = null,
                        message = "「${source.name}」SHA-256 校验失败，已拒绝安装",
                    )
                    return@launch
                }
                val baseName = source.url.substringAfterLast('/').substringBefore('?')
                    .ifBlank { source.name }
                val fileName = withContext(Dispatchers.IO) {
                    fileStore.writeBytes(bytes, baseName)
                }
                if (fileName.isNullOrBlank()) {
                    _state.value = _state.value.copy(
                        installingId = null,
                        message = "写入脚本失败，请检查存储空间",
                    )
                    return@launch
                }
                val reloaded = engine.reload()
                val installed = _state.value.installedIds + source.id
                prefs.edit().putStringSet(KEY_INSTALLED, installed).apply()
                _state.value = _state.value.copy(
                    installingId = null,
                    installedIds = installed,
                    message = if (reloaded) {
                        "已安装并重载「${source.name}」"
                    } else {
                        "已安装「${source.name}」，但音源重载失败，请检查脚本"
                    },
                )
                Log.i(TAG, "installed ${source.id} -> $fileName reloaded=$reloaded")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "install failed", t)
                _state.value = _state.value.copy(
                    installingId = null,
                    message = "安装失败：${t.message ?: "网络错误"}",
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private suspend fun fetchIndex(): List<MarketSource> = withContext(Dispatchers.IO) {
        val urls = manifestUrls()
        if (urls.isEmpty()) error("音源清单地址未配置或不是 HTTPS")
        val errors = mutableListOf<String>()
        for (raw in urls) {
            val base = raw.toHttpUrlOrNull() ?: continue
            val requestUrl = base.newBuilder()
                .addQueryParameter("t", (System.currentTimeMillis() / 60_000L).toString())
                .build()
            try {
                val request = Request.Builder()
                    .url(requestUrl)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", UA)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        errors += "${base.host}: HTTP ${response.code}"
                        return@use
                    }
                    return@withContext parseIndex(response.body.string())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                errors += "${base.host}: ${t.message ?: "请求失败"}"
            }
        }
        error(errors.joinToString("；").ifBlank { "没有可用的音源清单地址" })
    }

    private fun parseIndex(text: String): List<MarketSource> {
        val root = JSONObject(text)
        val array = root.optJSONArray("sources") ?: return emptyList()
        val items = mutableListOf<MarketSource>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("url").trim()
            val name = item.optString("name").trim()
            if (url.isBlank() || name.isBlank()) continue
            items += MarketSource(
                id = item.optString("id").trim().ifBlank { url },
                name = name,
                version = item.optString("version").trim(),
                author = item.optString("author").trim(),
                description = item.optString("description").trim(),
                url = url,
                sha256 = item.optString("sha256").trim().lowercase(Locale.ROOT),
            )
        }
        return items
    }

    private suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body
            val length = body.contentLength()
            if (length > MAX_SCRIPT_BYTES) error("脚本超过 8 MB")
            val bytes = body.bytes()
            if (bytes.size < 200) error("脚本内容过小")
            if (bytes.size > MAX_SCRIPT_BYTES) error("脚本超过 8 MB")
            bytes
        }
    }

    private fun manifestUrls(): List<String> {
        val configured = BuildConfig.SOURCE_MARKET_URL.trim()
        if (configured.isBlank()) return emptyList()
        val urls = linkedSetOf(configured)
        if (configured.contains("Zxxy326/TV-Music")) {
            urls += "https://raw.githubusercontent.com/Zxxy326/TV-Music/main/sources.json"
            urls += "https://testingcf.jsdelivr.net/gh/Zxxy326/TV-Music@main/sources.json"
            urls += "https://cdn.jsdelivr.net/gh/Zxxy326/TV-Music@main/sources.json"
        }
        return urls.filter { isAllowedUrl(it) }
    }

    private fun isAllowedUrl(raw: String): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        return when (url.scheme) {
            "https" -> true
            "http" -> BuildConfig.DEBUG
            else -> false
        }
    }

    private companion object {
        const val TAG = "TvMusicMarket"
        const val KEY_INSTALLED = "installed_ids"
        const val MAX_SCRIPT_BYTES = 8L * 1024L * 1024L
        val UA = "TVMusic-Market/1.0 (Android ${android.os.Build.VERSION.RELEASE})"
        val SHA256_PATTERN = Regex("^[a-f0-9]{64}$")
    }
}
