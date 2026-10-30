package tv.music.player.core.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.music.player.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(
        val info: UpdateInfo,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : UpdateState {
        val progress: Float
            get() = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    data class Downloaded(
        val info: UpdateInfo,
        val file: File,
        val sizeBytes: Long,
    ) : UpdateState

    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

sealed interface InstallOutcome {
    data object Launched : InstallOutcome
    data object NeedPermission : InstallOutcome
    data class Failed(val message: String) : InstallOutcome
}

/**
 * 从 update.json 检查新版本，下载并校验 APK，再交给系统安装器覆盖安装。
 * 发布清单地址见 BuildConfig.UPDATE_MANIFEST_URL。
 */
class UpdateStore(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val dir = File(appContext.filesDir, "updates").also { it.mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersionName: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong()

    private var job: Job? = null

    init {
        cleanStaleFiles()
    }

    fun check() {
        if (job?.isActive == true) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            try {
                val info = fetchManifest()
                resolveManifest(info)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "update check failed", t)
                _state.value = UpdateState.Failed("检查更新失败：${t.message ?: "网络不可用"}")
            }
        }
    }

    fun download() {
        if (job?.isActive == true) return
        val info = when (val current = _state.value) {
            is UpdateState.Available -> current.info
            is UpdateState.Failed -> current.info
            else -> null
        } ?: return
        val url = info.apkUrl.toHttpUrlOrNull()
        if (url == null || !isAllowedUrl(info.apkUrl)) {
            _state.value = UpdateState.Failed("安装包地址无效或不是 HTTPS", info)
            return
        }
        job = scope.launch { downloadInternal(info, url) }
    }

    fun cancelDownload() {
        val downloading = _state.value as? UpdateState.Downloading ?: return
        _state.value = UpdateState.Available(downloading.info)
        job?.cancel()
    }

    fun install(activity: Activity): InstallOutcome {
        val downloaded = _state.value as? UpdateState.Downloaded
            ?: return InstallOutcome.Failed("请先下载更新包")
        val file = downloaded.file
        if (!file.isFile || file.length() <= 0L) {
            _state.value = UpdateState.Available(downloaded.info)
            return InstallOutcome.Failed("更新包不存在，请重新下载")
        }
        val problem = validateApk(file)
        if (problem != null) {
            file.delete()
            _state.value = UpdateState.Failed(problem, downloaded.info)
            return InstallOutcome.Failed(problem)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !pm.canRequestPackageInstalls()) {
            return InstallOutcome.NeedPermission
        }
        return try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                clipData = ClipData.newRawUri("update", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            InstallOutcome.Launched
        } catch (e: ActivityNotFoundException) {
            InstallOutcome.Failed("系统没有可用的安装器")
        } catch (t: Throwable) {
            InstallOutcome.Failed(t.message ?: "无法启动安装器")
        }
    }

    fun openInstallSettings(activity: Activity) {
        val appSettings = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${activity.packageName}"),
        )
        runCatching { activity.startActivity(appSettings) }
            .onFailure {
                runCatching { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                    .onFailure { Log.w(TAG, "no install settings screen", it) }
            }
    }

    private fun resolveManifest(info: UpdateInfo) {
        if (info.versionCode <= currentVersionCode) {
            _state.value = UpdateState.UpToDate(currentVersionName)
            return
        }
        if (!isAllowedUrl(info.apkUrl)) {
            _state.value = UpdateState.Failed(
                "新版本 ${info.versionName} 的安装包地址无效（正式版必须使用 HTTPS）",
                info,
            )
            return
        }
        if (!SHA256_PATTERN.matches(info.sha256)) {
            _state.value = UpdateState.Failed(
                "更新清单缺少有效的 SHA-256，已拒绝下载",
                info,
            )
            return
        }
        val existing = File(dir, fileName(info))
        if (existing.isFile && existing.length() > 0L) {
            if (info.sizeBytes <= 0L || existing.length() == info.sizeBytes) {
                _state.value = UpdateState.Downloaded(info, existing, existing.length())
                return
            }
            existing.delete()
        }
        _state.value = UpdateState.Available(info)
    }

    private fun fetchManifest(): UpdateInfo {
        val urls = manifestUrls()
        if (urls.isEmpty()) error("更新地址未配置或不是 HTTPS")
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
                    return parseManifest(response.body.string())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                errors += "${base.host}: ${t.message ?: "请求失败"}"
            }
        }
        throw IllegalStateException(errors.joinToString("；").ifBlank { "没有可用的更新地址" })
    }

    private fun parseManifest(text: String): UpdateInfo {
        val root = JSONObject(text)
        val code = root.optLong("versionCode", 0L)
        val name = root.optString("versionName").trim()
        require(code > 0L && name.isNotBlank()) { "更新清单缺少 versionCode/versionName" }
        return UpdateInfo(
            versionCode = code,
            versionName = name,
            apkUrl = root.optString("apkUrl").trim(),
            sha256 = root.optString("sha256").trim().lowercase(Locale.ROOT),
            sizeBytes = root.optLong("sizeBytes", 0L).coerceAtLeast(0L),
            notes = root.optString("notes").trim(),
        )
    }

    private suspend fun downloadInternal(info: UpdateInfo, url: HttpUrl) {
        val target = File(dir, fileName(info))
        val part = File(dir, "${target.nameWithoutExtension}.part.apk")
        try {
            _state.value = UpdateState.Downloading(info, 0L, info.sizeBytes)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    fail("下载失败：HTTP ${response.code}", info)
                    return
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0L } ?: info.sizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                var lastNotify = 0L
                part.delete()
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            read += count
                            val now = System.currentTimeMillis()
                            if (now - lastNotify >= 250L) {
                                lastNotify = now
                                _state.value = UpdateState.Downloading(info, read, total)
                            }
                        }
                    }
                }
                if (read < MIN_APK_BYTES) {
                    fail("安装包内容过小，下载可能被拦截", info)
                    return
                }
                if (info.sizeBytes > 0L && read != info.sizeBytes) {
                    fail("安装包大小与清单不一致", info)
                    return
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    fail("SHA-256 校验失败，安装包可能损坏或被篡改", info)
                    return
                }
                target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                val problem = validateApk(target)
                if (problem != null) {
                    target.delete()
                    fail(problem, info)
                    return
                }
                keepOnly(target)
                _state.value = UpdateState.Downloaded(info, target, target.length())
                Log.i(TAG, "update downloaded ${info.versionName} (${target.length()} bytes)")
            }
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (t: Throwable) {
            part.delete()
            fail("下载失败：${t.message ?: "网络错误"}", info)
            Log.w(TAG, "update download failed", t)
        }
    }

    @Suppress("DEPRECATION")
    private fun validateApk(file: File): String? {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return "更新包无法解析"
        if (archive.packageName != appContext.packageName) {
            return "更新包包名不匹配（${archive.packageName}）"
        }
        if (archive.longVersionCode <= currentVersionCode) {
            return "更新包版本不高于当前版本"
        }
        val archiveSigners = archive.signingInfo?.apkContentsSigners
        if (archiveSigners.isNullOrEmpty()) return "更新包未签名"
        val selfSigners = pm.getPackageInfo(appContext.packageName, flags)
            .signingInfo?.apkContentsSigners
        if (selfSigners.isNullOrEmpty()) return "无法读取当前应用签名"
        val matches = archiveSigners.any { candidate ->
            selfSigners.any { own -> candidate.toByteArray().contentEquals(own.toByteArray()) }
        }
        return if (matches) null else "更新包签名与当前应用不一致，无法覆盖安装"
    }

    private fun fail(message: String, info: UpdateInfo) {
        _state.value = UpdateState.Failed(message, info)
    }

    private fun keepOnly(file: File) {
        dir.listFiles { item -> item.isFile && item.name.endsWith(".apk") }?.forEach { item ->
            if (item != file) item.delete()
        }
    }

    private fun cleanStaleFiles() {
        dir.listFiles { item -> item.isFile && item.name.contains(".part") }?.forEach { it.delete() }
        dir.listFiles { item -> item.isFile && item.name.startsWith(FILE_PREFIX) && item.name.endsWith(".apk") }
            ?.forEach { file ->
                val code = file.name.removePrefix(FILE_PREFIX).removeSuffix(".apk").toLongOrNull()
                if (code != null && code <= currentVersionCode) file.delete()
            }
    }

    private fun manifestUrls(): List<String> {
        val configured = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (configured.isBlank()) return emptyList()
        val urls = linkedSetOf(configured)
        if (configured.contains("Zxxy326/TV-Music")) {
            urls += "https://raw.githubusercontent.com/Zxxy326/TV-Music/main/update.json"
            urls += "https://testingcf.jsdelivr.net/gh/Zxxy326/TV-Music@main/update.json"
            urls += "https://cdn.jsdelivr.net/gh/Zxxy326/TV-Music@main/update.json"
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

    private fun fileName(info: UpdateInfo): String = "$FILE_PREFIX${info.versionCode}.apk"

    private companion object {
        const val TAG = "TvMusicUpdate"
        const val FILE_PREFIX = "tv-music-"
        const val MIN_APK_BYTES = 1024L * 1024L
        val UA = "TVMusic-Updater/1.0 (Android ${Build.VERSION.RELEASE})"
        val SHA256_PATTERN = Regex("^[a-f0-9]{64}$")
    }
}
