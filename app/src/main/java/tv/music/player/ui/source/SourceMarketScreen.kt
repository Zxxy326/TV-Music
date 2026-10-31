package tv.music.player.ui.source

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.source.MarketRelease
import tv.music.player.core.source.SourceMarketStore
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.ScreenHeader
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun SourceMarketScreen(
    store: SourceMarketStore,
    onOpenRelease: (String) -> Unit,
    onBack: () -> Unit,
) {
    val group = rememberTvFocusGroup("source-market")
    val capsule = LocalCapsuleHost.current
    val accent = LocalTvAccent.current
    val state by store.state.collectAsStateWithLifecycle()
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.SourceMarket.route

    LaunchedEffect(Unit) {
        if (state.releases.isEmpty() && !state.loading) store.load()
    }
    LaunchedEffect(isActive, state.releases) {
        if (isActive) {
            group.restore(
                state.releases.firstOrNull()?.let { "market:release:${it.tag}" }
                    ?: "market:refresh"
            )
        }
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) {
            group.restore(
                state.releases.firstOrNull()?.let { "market:release:${it.tag}" }
                    ?: "market:refresh"
            )
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            capsule.show(it)
            store.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "音源市场",
                    subtitle = "像素音源市场 · GitHub Release · 选择安装",
                    group = group,
                    onBack = onBack,
                    trailing = {
                        Text(
                            "已装 ${state.installedNames.size} 个脚本",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    },
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionChip(
                        label = if (state.loading) "读取中…" else "刷新市场",
                        icon = Icons.Rounded.Refresh,
                        group = group,
                        focusKey = "market:refresh",
                        onClick = { if (!state.loading) store.load() },
                    )
                    Text(
                        "安装前会下载音源 zip，按 GitHub 的 SHA-256 校验后逐个安装 .js；脚本由第三方发布，请确认来源可信。",
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.error.orEmpty() + if (state.fromCache) " · 当前显示上次缓存的清单" else "",
                        style = TvType.caption,
                        color = if (state.fromCache) TvColors.TextTertiary else TvColors.Danger,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                when {
                    state.releases.isNotEmpty() -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.releases, key = { it.tag }) { release ->
                            ReleaseRow(
                                release = release,
                                group = group,
                                onClick = { onOpenRelease(release.tag) },
                            )
                        }
                    }
                    state.loading -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("正在读取 GitHub Release…", style = TvType.body, color = TvColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "首次读取需要访问 api.github.com；网络受限时请配置代理。",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    }
                    else -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TvIcon(Icons.Rounded.Storefront, Modifier.size(44.dp), tint = accent.copy(alpha = 0.8f))
                        Spacer(Modifier.height(12.dp))
                        Text("音源市场暂时不可用", style = TvType.body, color = TvColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "点「刷新市场」重试；GitHub 匿名接口每小时限 60 次。",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                KeyHintBar(
                    hints = listOf(
                        "OK" to "查看版本",
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
private fun ReleaseRow(
    release: MarketRelease,
    group: TvFocusGroup,
    onClick: () -> Unit,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(86.dp),
        shape = RoundedCornerShape(14.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.62f),
        focusedScale = 1.03f,
        glowSize = 10.dp,
        group = group,
        focusKey = "market:release:${release.tag}",
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvSurface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                baseColor = accent.copy(alpha = 0.14f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvIcon(Icons.Rounded.CloudDownload, Modifier.size(24.dp), tint = accent)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(release.tag, style = TvType.label, color = TvColors.TextPrimary)
                    if (release.displayName != release.tag) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            release.displayName,
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    buildString {
                        append(release.displayDate)
                        if (release.zipSize > 0) append(" · ").append(formatSize(release.zipSize))
                        if (release.displayNotes.isNotBlank()) {
                            append(" · ").append(release.displayNotes.take(80))
                        }
                    },
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("查看", style = TvType.caption, color = accent)
        }
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1024L * 1024L) "%.1f MB".format(bytes / 1024.0 / 1024.0)
    else "${(bytes / 1024L).coerceAtLeast(1L)} KB"
