package tv.music.player.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tv.music.player.core.companion.CompanionServer
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.settings.AppSettings
import tv.music.player.core.settings.SettingsRepository
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.CapsuleHostState

@Composable
fun CompanionCard(
    server: CompanionServer,
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    group: TvFocusGroup,
    capsule: CapsuleHostState,
) {
    val status by server.status.collectAsStateWithLifecycle()
    val code by server.code.collectAsStateWithLifecycle()
    val accent = LocalTvAccent.current
    val scope = rememberCoroutineScope()
    val enabled = settings.companionEnabled
    val url = if (status.running && status.urls.isNotEmpty()) {
        "${status.urls.first()}/?code=$code"
    } else {
        null
    }
    val qr = remember(url) { url?.let(::generateQr)?.asImageBitmap() }

    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.7f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (!enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusTitle(status = "已关闭", accent = false)
                    Spacer(Modifier.weight(1f))
                    ToggleChip(enabled, group, settingsRepository, scope, capsule)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "开启后手机与电视连同一 Wi-Fi / 局域网，用手机输入搜索、暂停切歌和点队列；电视端会显示二维码与 4 位配对码。",
                    style = TvType.caption,
                    color = TvColors.TextTertiary,
                )
            } else if (!status.running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusTitle(status = if (status.error == null) "正在启动…" else "启动失败", accent = false)
                    Spacer(Modifier.weight(1f))
                    ToggleChip(enabled, group, settingsRepository, scope, capsule)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    status.error?.let { "启动失败：$it" } ?: "正在启动手机伴侣…",
                    style = TvType.caption,
                    color = if (status.error == null) TvColors.TextSecondary else TvColors.Danger,
                )
                Spacer(Modifier.height(8.dp))
                ActionChip(
                    label = "重试启动",
                    group = group,
                    focusKey = "companion:retry",
                    onClick = {
                        scope.launch(Dispatchers.IO) { server.start() }
                        capsule.show("正在重新启动手机伴侣")
                    },
                )
            } else if (url == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusTitle(status = "运行中 · 未找到局域网地址", accent = true)
                    Spacer(Modifier.weight(1f))
                    ToggleChip(enabled, group, settingsRepository, scope, capsule)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "请检查 Wi-Fi / 有线网络后刷新地址。",
                    style = TvType.caption,
                    color = TvColors.Danger,
                )
                Spacer(Modifier.height(8.dp))
                ActionChip(
                    label = "刷新地址",
                    group = group,
                    focusKey = "companion:refresh",
                    onClick = {
                        server.refreshAddresses()
                        capsule.show("正在重新检测局域网地址")
                    },
                )
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(176.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        if (qr != null) {
                            Image(
                                bitmap = qr,
                                contentDescription = "手机伴侣配对二维码",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("手机伴侣", style = TvType.label, color = TvColors.TextPrimary)
                                Text(
                                    "运行中 · 手机与电视连接同一局域网",
                                    style = TvType.caption,
                                    color = accent,
                                )
                            }
                            ToggleChip(enabled, group, settingsRepository, scope, capsule)
                        }
                        Text("扫码，或在同一局域网打开：", style = TvType.caption, color = TvColors.TextTertiary)
                        Text(
                            url,
                            style = TvType.body,
                            color = TvColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("配对码", style = TvType.caption, color = TvColors.TextSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(code, style = TvType.title, color = accent)
                            Spacer(Modifier.weight(1f))
                            ActionChip(
                                label = "换配对码",
                                group = group,
                                focusKey = "companion:code",
                                onClick = {
                                    server.regenerateCode()
                                    capsule.show("配对码已更新，请用新二维码重新连接")
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            ActionChip(
                                label = "刷新地址",
                                group = group,
                                focusKey = "companion:refresh",
                                onClick = {
                                    server.refreshAddresses()
                                    capsule.show("正在重新检测局域网地址")
                                },
                            )
                        }
                        Text(
                            "手机输入搜索 · 遥控播放 · 队列点播",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    }
                }
            }
            status.error?.let {
                Spacer(Modifier.height(6.dp))
                Text("服务错误：$it", style = TvType.caption, color = TvColors.Danger)
            }
        }
    }
}

@Composable
private fun StatusTitle(status: String, accent: Boolean) {
    Column {
        Text("手机伴侣", style = TvType.label, color = TvColors.TextPrimary)
        Text(
            status,
            style = TvType.caption,
            color = if (accent) LocalTvAccent.current else TvColors.TextSecondary,
        )
    }
}

@Composable
private fun ToggleChip(
    enabled: Boolean,
    group: TvFocusGroup,
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    capsule: CapsuleHostState,
) {
    ActionChip(
        label = if (enabled) "关闭" else "开启",
        group = group,
        focusKey = "companion:toggle",
        onClick = {
            scope.launch {
                settingsRepository.setCompanionEnabled(!enabled)
                capsule.show(if (enabled) "手机伴侣已关闭" else "手机伴侣已开启 · 手机扫码连接")
            }
        },
        primary = !enabled,
    )
}

private fun generateQr(text: String): Bitmap? = runCatching {
    val size = 512
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix.get(x, y)) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}.getOrNull()
