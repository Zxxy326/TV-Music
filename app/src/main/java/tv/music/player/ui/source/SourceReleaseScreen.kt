package tv.music.player.ui.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Code
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.source.MarketEntry
import tv.music.player.core.source.ReleaseState
import tv.music.player.core.source.SourceMarketStore
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.ProgressLine
import tv.music.player.ui.components.ScreenHeader
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun SourceReleaseScreen(
    store: SourceMarketStore,
    tag: String,
    onBack: () -> Unit,
) {
    val group = rememberTvFocusGroup("source-release")
    val capsule = LocalCapsuleHost.current
    val accent = LocalTvAccent.current
    val state by store.state.collectAsStateWithLifecycle()
    val release = state.releases.firstOrNull { it.tag == tag }
    val detail = state.details[tag] ?: ReleaseState.Idle
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.MarketRelease.route
    var armedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tag) { store.openRelease(tag) }
    LaunchedEffect(armedKey) {
        if (armedKey != null) {
            delay(3_000L)
            armedKey = null
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            capsule.show(it)
            store.clearMessage()
        }
    }
    val focusKey = when (val d = detail) {
        is ReleaseState.Ready -> d.entries.firstOrNull()?.let { "release:entry:${it.key}" } ?: "release:retry"
        is ReleaseState.Error -> "release:retry"
        else -> "back"
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(tag, isActive, windowFocused, detail is ReleaseState.Ready, focusKey) {
        if (!isActive || !windowFocused) return@LaunchedEffect
        when {
            detail is ReleaseState.Ready && detail.entries.isNotEmpty() ->
                group.focusWhenReady(focusKey, timeoutMs = 4_000L)
            detail is ReleaseState.Error -> group.focusWhenReady("release:retry", timeoutMs = 2_000L)
            else -> group.focusWhenReady("back", timeoutMs = 1_000L)
        }
    }

    val fileNames = state.installedNames
    val entries = (detail as? ReleaseState.Ready)?.entries.orEmpty()
    val installedCount = entries.count { it.installed || it.fileName in fileNames }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = release?.tag ?: "音源包",
                    subtitle = "选择要安装的 .js · 安装后自动重载引擎",
                    group = group,
                    onBack = onBack,
                    trailing = {
                        Text(
                            if (entries.isEmpty()) "GitHub Release"
                            else "$installedCount / ${entries.size} 已安装",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    },
                )
                Spacer(Modifier.height(14.dp))
                TvSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    baseColor = TvColors.Surface.copy(alpha = 0.66f),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(release?.zipName ?: "音源包", style = TvType.label, color = TvColors.TextPrimary)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                buildString {
                                    append(release?.displayDate.orEmpty())
                                    if ((release?.zipSize ?: 0L) > 0) {
                                        append(" · ").append(formatSize(release?.zipSize ?: 0L))
                                    }
                                },
                                style = TvType.caption,
                                color = TvColors.TextTertiary,
                            )
                        }
                        if (!release?.displayNotes.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                release.displayNotes,
                                style = TvType.caption,
                                color = TvColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "脚本由第三方发布；下载后按 GitHub 的 SHA-256 校验 zip，再逐个解压安装。",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (val d = detail) {
                    is ReleaseState.Ready -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(d.entries, key = { _, entry -> entry.key }) { index, entry ->
                            EntryRow(
                                entry = entry,
                                installed = entry.installed || entry.fileName in fileNames,
                                installing = state.installingKey == entry.key,
                                armed = armedKey == entry.key,
                                group = group,
                                focusKey = "release:entry:${entry.key}",
                                onClick = {
                                    when {
                                        entry.installed || entry.fileName in fileNames ->
                                            capsule.show("「${entry.displayName}」已安装，可在音源分组查看")
                                        state.installingKey != null ->
                                            capsule.show("正在安装，请稍候")
                                        armedKey == entry.key -> {
                                            armedKey = null
                                            store.install(tag, entry.key)
                                        }
                                        else -> {
                                            armedKey = entry.key
                                            capsule.show("再按一次 OK 安装「${entry.displayName}」（第三方脚本）")
                                        }
                                    }
                                },
                            )
                        }
                    }
                    is ReleaseState.Downloading -> StatusBlock(
                        title = "正在下载音源包…",
                        detail = "${(d.progress * 100).toInt()}%",
                        progress = d.progress,
                    )
                    ReleaseState.Inspecting -> StatusBlock(
                        title = "正在解压并读取脚本…",
                        detail = "较大压缩包可能需要几秒",
                        progress = null,
                    )
                    is ReleaseState.Error -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("载入失败：${d.message}", style = TvType.body, color = TvColors.Danger)
                        Spacer(Modifier.height(12.dp))
                        ActionChip(
                            label = "重试",
                            icon = Icons.Rounded.Refresh,
                            group = group,
                            focusKey = "release:retry",
                            onClick = { store.openRelease(tag) },
                        )
                    }
                    ReleaseState.Idle -> StatusBlock(
                        title = "准备下载…",
                        detail = "等待网络",
                        progress = null,
                    )
                }
                Spacer(Modifier.height(8.dp))
                KeyHintBar(
                    hints = listOf(
                        "OK" to "安装 / 重试",
                        "返回" to "上一级",
                        "长按返回" to "回到首页",
                    )
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ColumnScope.StatusBlock(
    title: String,
    detail: String,
    progress: Float?,
) {
    Column(
        Modifier.fillMaxWidth().weight(1f).padding(top = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = TvType.body, color = TvColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text(detail, style = TvType.caption, color = TvColors.TextTertiary)
        if (progress != null) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.width(360.dp)) {
                ProgressLine(progress, Modifier.fillMaxWidth(), height = 6.dp)
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: MarketEntry,
    installed: Boolean,
    installing: Boolean,
    armed: Boolean,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(13.dp),
        baseColor = if (armed) accent.copy(alpha = 0.12f) else TvColors.Surface.copy(alpha = 0.62f),
        focusedScale = 1.03f,
        glowSize = 10.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvSurface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(11.dp),
                baseColor = accent.copy(alpha = 0.14f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvIcon(Icons.Rounded.Code, Modifier.size(21.dp), tint = accent)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.folder.isNotBlank()) {
                        Text(
                            entry.folder,
                            style = TvType.caption,
                            color = accent,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        entry.displayName,
                        style = TvType.label,
                        color = TvColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.version.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text("v${entry.version}", style = TvType.caption, color = TvColors.TextTertiary)
                    }
                    if (entry.author.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text("· ${entry.author}", style = TvType.caption, color = TvColors.TextTertiary)
                    }
                }
                Text(
                    "${entry.fileName} · ${formatSize(entry.sizeBytes)}",
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                when {
                    installing -> "安装中…"
                    installed -> "已安装"
                    armed -> "再按一次"
                    else -> "安装"
                },
                style = TvType.caption,
                color = when {
                    installed -> accent
                    armed -> TvColors.Danger
                    else -> TvColors.TextTertiary
                },
            )
        }
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1024L * 1024L) "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else "${(bytes / 1024L).coerceAtLeast(1L)} KB"
