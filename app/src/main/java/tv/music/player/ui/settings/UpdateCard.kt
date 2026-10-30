package tv.music.player.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.core.update.InstallOutcome
import tv.music.player.core.update.UpdateInfo
import tv.music.player.core.update.UpdateState
import tv.music.player.core.update.UpdateStore
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.CapsuleHostState
import java.util.Locale

@Composable
fun UpdateCard(
    store: UpdateStore,
    group: TvFocusGroup,
    capsule: CapsuleHostState,
) {
    val state by store.state.collectAsStateWithLifecycle()
    val accent = LocalTvAccent.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(store) {
        if (store.state.value is UpdateState.Idle) store.check()
    }

    fun install() {
        if (activity == null) {
            capsule.show("无法获取当前界面，请返回后重试")
            return
        }
        when (val outcome = store.install(activity)) {
            InstallOutcome.Launched ->
                capsule.show("已打开系统安装器，请按提示完成安装")
            InstallOutcome.NeedPermission -> {
                store.openInstallSettings(activity)
                capsule.show("请允许本应用安装更新，返回后再点「安装更新」")
            }
            is InstallOutcome.Failed -> capsule.show(outcome.message)
        }
    }

    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.7f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("在线更新", style = TvType.label, color = TvColors.TextPrimary)
                    Text(
                        "当前 v${store.currentVersionName}（${store.currentVersionCode}）",
                        style = TvType.caption,
                        color = TvColors.TextSecondary,
                    )
                }
                Text(
                    statusLabel(state),
                    style = TvType.caption,
                    color = statusColor(state, accent),
                    maxLines = 1,
                )
                Spacer(Modifier.width(12.dp))
                ActionChip(
                    label = if (state is UpdateState.Checking) "检查中…" else "检查更新",
                    group = group,
                    focusKey = "update:check",
                    onClick = {
                        if (state is UpdateState.Checking || state is UpdateState.Downloading) {
                            capsule.show("正在检查或下载，请稍候")
                        } else {
                            store.check()
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            when (val current = state) {
                UpdateState.Idle -> Hint("尚未检查更新。")
                UpdateState.Checking -> Hint("正在从更新清单检查新版本…")
                is UpdateState.UpToDate ->
                    Hint("已是最新版本。发布新包后可从同一入口检查并升级。")
                is UpdateState.Available -> {
                    Text(
                        "新版本 ${current.info.versionName}（${current.info.versionCode}）",
                        style = TvType.label,
                        color = accent,
                    )
                    Notes(current.info, TvColors.TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActionChip(
                            label = "下载更新",
                            group = group,
                            focusKey = "update:action",
                            onClick = { store.download() },
                            primary = true,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            formatBytes(current.info.sizeBytes),
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    }
                }
                is UpdateState.Downloading -> {
                    Text(
                        "正在下载 ${current.info.versionName}",
                        style = TvType.label,
                        color = accent,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressBar(
                            fraction = current.progress,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${(current.progress * 100).toInt()}%",
                            style = TvType.caption,
                            color = accent,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            downloadSizeText(current.bytesRead, current.totalBytes),
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                        Spacer(Modifier.weight(1f))
                        ActionChip(
                            label = "取消下载",
                            group = group,
                            focusKey = "update:action",
                            onClick = { store.cancelDownload() },
                        )
                    }
                }
                is UpdateState.Downloaded -> {
                    Text(
                        "更新包已就绪 · ${formatBytes(current.sizeBytes)}",
                        style = TvType.label,
                        color = accent,
                    )
                    Hint("点「安装更新」后由系统安装器覆盖安装；首次需要允许本应用安装未知应用。")
                    Spacer(Modifier.height(6.dp))
                    ActionChip(
                        label = "安装更新",
                        group = group,
                        focusKey = "update:action",
                        onClick = ::install,
                        primary = true,
                    )
                }
                is UpdateState.Failed -> {
                    Text(
                        current.message,
                        style = TvType.caption,
                        color = TvColors.Danger,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    current.info?.let { info ->
                        Notes(info, TvColors.TextTertiary)
                    }
                    Spacer(Modifier.height(6.dp))
                    ActionChip(
                        label = if (current.info != null) "重新下载" else "重新检查",
                        group = group,
                        focusKey = "update:action",
                        onClick = {
                            if (current.info != null) store.download() else store.check()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = TvType.caption, color = TvColors.TextTertiary, maxLines = 2)
}

@Composable
private fun Notes(info: UpdateInfo, color: Color) {
    if (info.notes.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
            info.notes,
            style = TvType.caption,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgressBar(fraction: Float, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(TvColors.Divider)
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(accent)
            )
        }
    }
}

private fun statusLabel(state: UpdateState): String = when (state) {
    UpdateState.Idle -> "未检查"
    UpdateState.Checking -> "检查中"
    is UpdateState.UpToDate -> "已是最新"
    is UpdateState.Available -> "有新版本"
    is UpdateState.Downloading -> "下载中"
    is UpdateState.Downloaded -> "待安装"
    is UpdateState.Failed -> "失败"
}

private fun statusColor(state: UpdateState, accent: Color): Color = when (state) {
    is UpdateState.Failed -> TvColors.Danger
    is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Downloaded -> accent
    else -> TvColors.TextTertiary
}

private fun downloadSizeText(read: Long, total: Long): String =
    if (total > 0L) "${formatBytes(read)} / ${formatBytes(total)}" else formatBytes(read)

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "大小未知"
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(Locale.US, "%.0f KB", bytes / 1_024.0)
    else -> "$bytes B"
}

private fun Context.findActivity(): Activity? {
    var target: Context = this
    while (target is ContextWrapper) {
        if (target is Activity) return target
        target = target.baseContext
    }
    return null
}
