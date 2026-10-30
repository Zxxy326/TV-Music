package tv.music.player.core.smb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SmbConfig(
    val name: String = "SMB 共享",
    val host: String = "",
    val port: Int = 445,
    val share: String = "",
    val path: String = "/",
    val domain: String = "",
    val user: String = "",
    val password: String = "",
) {
    val rootUrl: String
        get() {
            val cleanPath = path.trim().trim('/')
            return buildString {
                append("smb://").append(host.trim())
                if (port > 0 && port != 445) append(":").append(port)
                append("/").append(share.trim().trim('/'))
                if (cleanPath.isNotEmpty()) append("/").append(cleanPath)
                append("/")
            }
        }
}

class SmbConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("smb_config", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<SmbConfig?> = _config.asStateFlow()

    fun current(): SmbConfig? = _config.value

    fun parse(raw: String): SmbConfig {
        val root = JSONObject(raw)
        val host = root.optString("host").trim()
        val share = root.optString("share").trim().trim('/')
        require(host.isNotBlank()) { "缺少 host" }
        require(share.isNotBlank()) { "缺少 share" }
        return SmbConfig(
            name = root.optString("name").trim().ifBlank { share },
            host = host,
            port = root.optInt("port", 445).let { if (it <= 0) 445 else it },
            share = share,
            path = root.optString("path").trim().ifBlank { "/" },
            domain = root.optString("domain").trim(),
            user = root.optString("user").trim(),
            password = root.optString("password"),
        )
    }

    fun save(config: SmbConfig): Boolean {
        return runCatching {
            val payload = JSONObject()
                .put("name", config.name)
                .put("host", config.host)
                .put("port", config.port)
                .put("share", config.share)
                .put("path", config.path)
                .put("domain", config.domain)
                .put("user", config.user)
                .put("password", config.password)
                .toString()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
            val stored = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit().putString(KEY_CONFIG, stored).apply()
            _config.value = config
            true
        }.onFailure { Log.w(TAG, "save smb config failed", it) }.getOrDefault(false)
    }

    fun clear() {
        prefs.edit().remove(KEY_CONFIG).apply()
        _config.value = null
    }

    private fun load(): SmbConfig? {
        val stored = prefs.getString(KEY_CONFIG, null) ?: return null
        return runCatching {
            val parts = stored.split(':', limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val data = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(data).toString(Charsets.UTF_8)
            parse(plain)
        }.onFailure { Log.w(TAG, "load smb config failed", it) }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "TvMusicSmb"
        const val KEY_CONFIG = "config"
        const val KEY_ALIAS = "tvmusic_smb_config"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
