package tv.music.player.core.smb

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/** 让 smb:// 走 jcifs，其余 scheme 继续走 DefaultDataSource。 */
class PreferredDataSourceFactory(
    private val appContext: Context,
    private val httpFactory: DefaultHttpDataSource.Factory,
    private val smbFactory: DataSource.Factory,
) : DataSource.Factory {
    private val defaultsFactory = DefaultDataSource.Factory(appContext, httpFactory)

    override fun createDataSource(): DataSource =
        SplitDataSource(defaultsFactory, smbFactory)
}

private class SplitDataSource(
    private val defaultsFactory: DataSource.Factory,
    private val smbFactory: DataSource.Factory,
) : DataSource {
    private var delegate: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme == "smb") {
            smbFactory.createDataSource()
        } else {
            defaultsFactory.createDataSource()
        }
        listeners.forEach(source::addTransferListener)
        delegate = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = delegate?.uri

    override fun close() {
        delegate?.close()
        delegate = null
    }
}

class SmbDataSource(
    private val configStore: SmbConfigStore,
    private val repository: SmbRepository,
) : BaseDataSource(true) {
    private var stream: InputStream? = null
    private var remaining = 0L
    private var currentUri: Uri? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val config = configStore.current() ?: throw IOException("尚未配置 SMB 共享")
        val url = dataSpec.uri.toString()
        val length = try {
            repository.length(config, url)
        } catch (t: Throwable) {
            throw IOException("SMB 打开失败：${t.message.orEmpty()}", t)
        }
        if (length < 0L) throw IOException("无法读取 SMB 文件大小")
        if (dataSpec.position > length) {
            currentUri = dataSpec.uri
            transferStarted(dataSpec)
            opened = true
            remaining = 0L
            return 0L
        }
        val input = try {
            repository.openStream(config, url)
        } catch (t: Throwable) {
            throw IOException("SMB 读取失败：${t.message.orEmpty()}", t)
        }
        try {
            skipFully(input, dataSpec.position)
        } catch (t: Throwable) {
            runCatching { input.close() }
            throw IOException("SMB 定位失败：${t.message.orEmpty()}", t)
        }
        stream = input
        currentUri = dataSpec.uri
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            min(dataSpec.length, length - dataSpec.position)
        } else {
            length - dataSpec.position
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val input = stream ?: return C.RESULT_END_OF_INPUT
        val toRead = min(length.toLong(), remaining).toInt()
        val count = input.read(buffer, offset, toRead)
        if (count == -1) {
            remaining = 0L
            return C.RESULT_END_OF_INPUT
        }
        remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        runCatching { stream?.close() }
        stream = null
        currentUri = null
        remaining = 0L
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun skipFully(input: InputStream, position: Long) {
        var remainingToSkip = position
        while (remainingToSkip > 0L) {
            val skipped = input.skip(remainingToSkip)
            if (skipped > 0L) {
                remainingToSkip -= skipped
            } else if (input.read() == -1) {
                break
            } else {
                remainingToSkip--
            }
        }
    }
}
