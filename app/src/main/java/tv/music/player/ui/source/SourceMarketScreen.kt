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
import tv.music.player.core.source.MarketSource
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
    onBack: () -> Unit,
) {
    val group = rememberTvFocusGroup("source-market")
    val capsule = LocalCapsuleHost.current
    val accent = LocalTvAccent.current
    val state by store.state.collectAsStateWithLifecycle()
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.SourceMarket.route
    var armedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (state.items.isEmpty() && !state.loading && state.error == null) store.load()
    }
    LaunchedEffect(isActive) {
        if (isActive) group.restore(if (state.items.isEmpty()) "market:refresh" else "market:${state.items.first().id}")
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) {
            group.restore(if (state.items.isEmpty()) "market:refresh" else "market:${state.items.first().id}")
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            capsule.show(it)
            store.clearMessage()
        }
    }
    LaunchedEffect(armedId) {
        if (armedId != null) {
            delay(3_000L)
            armedId = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "音源市场",
                    subtitle = "远程清单 · SHA-256 校验 · 一键安装",
                    group = group,
                    onBack = onBack,
                    trailing = {
                        Text(
                            "已安装 ${state.installedIds.size} / ${state.items.size}",
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
                        label = if (state.loading) "读取中…" else "刷新清单",
                        icon = Icons.Rounded.Refresh,
                        group = group,
                        focusKey = "market:refresh",
                        onClick = { if (!state.loading) store.load() },
                    )
                    Text(
                        "安装前会下载 .js 并校验哈希；脚本由清单提供方发布，请确认来源可信。",
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                when {
                    state.items.isNotEmpty() -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            MarketRow(
                                item = item,
                                installed = item.id in state.installedIds,
                                installing = state.installingId == item.id,
                                armed = armedId == item.id,
                                group = group,
                                onClick = {
                                    when {
                                        item.id in state.installedIds ->
                                            capsule.show("「${item.name}」已安装，可在音源分组查看")
                                        state.installingId != null ->
                                            capsule.show("正在安装，请稍候")
                                        armedId == item.id -> {
                                            armedId = null
                                            store.install(item)
                                        }
                                        else -> {
                                            armedId = item.id
                                            capsule.show("再按一次 OK 安装「${item.name}」（第三方脚本，请确认来源）")
                                        }
                                    }
                                },
                            )
                        }
                    }
                    state.loading -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("正在读取音源清单…", style = TvType.body, color = TvColors.TextSecondary)
                    }
                    state.error != null -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error.orEmpty(), style = TvType.body, color = TvColors.Danger)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "可在 sources.json 里维护音源列表；安装地址必须是 HTTPS。",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    }
                    else -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(top = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("暂无可用音源", style = TvType.body, color = TvColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "维护者在 sources.json 的 sources 数组填入脚本地址与 SHA-256 后，这里会自动出现条目。",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                KeyHintBar(
                    hints = listOf(
                        "OK" to "安装 / 查看",
                        "返回" to "上一级",
                        "长按返回" to "回到首页",
                    )
                )
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun MarketRow(
    item: MarketSource,
    installed: Boolean,
    installing: Boolean,
    armed: Boolean,
    group: TvFocusGroup,
    onClick: () -> Unit,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(82.dp),
        shape = RoundedCornerShape(14.dp),
        baseColor = if (armed) accent.copy(alpha = 0.12f) else TvColors.Surface.copy(alpha = 0.62f),
        focusedScale = 1.03f,
        glowSize = 10.dp,
        group = group,
        focusKey = "market:${item.id}",
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
                    Text(item.name, style = TvType.label, color = TvColors.TextPrimary)
                    if (item.version.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text("v${item.version}", style = TvType.caption, color = TvColors.TextTertiary)
                    }
                    if (item.author.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text("· ${item.author}", style = TvType.caption, color = TvColors.TextTertiary)
                    }
                }
                if (item.description.isNotBlank()) {
                    Text(
                        item.description,
                        style = TvType.caption,
                        color = TvColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
