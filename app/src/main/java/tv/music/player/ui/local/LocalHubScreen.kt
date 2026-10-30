package tv.music.player.ui.local

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import tv.music.player.core.download.DownloadStatus
import tv.music.player.core.download.DownloadStore
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.local.LocalLibraryStore
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.ScreenHeader
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun LocalHubScreen(
    localLibrary: LocalLibraryStore,
    downloads: DownloadStore,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("local-hub")
    val accent = LocalTvAccent.current
    val localTracks by localLibrary.tracks.collectAsStateWithLifecycle()
    val downloadEntries by downloads.entries.collectAsStateWithLifecycle()
    val downloaded = downloadEntries.count { it.status == DownloadStatus.DONE }
    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.LocalHub.route

    LaunchedEffect(isActive) {
        if (isActive) group.restore("hub:device")
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) group.restore("hub:device")
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "本地音乐",
                    subtitle = "设备歌曲 · 下载管理",
                    group = group,
                    onBack = { navController.popBackStack() },
                    trailing = {
                        Text(
                            "离线可听",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    },
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HubCard(
                        icon = Icons.Rounded.Folder,
                        title = "设备歌曲",
                        subtitle = if (localTracks.isEmpty()) "扫描 U 盘、外接盘或添加文件夹" else "${localTracks.size} 首 · 来自本机与文件夹",
                        group = group,
                        focusKey = "hub:device",
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(TvRoute.DeviceMusic.route) },
                    )
                    HubCard(
                        icon = Icons.Rounded.Download,
                        title = "下载管理",
                        subtitle = if (downloaded == 0) "还没有下载，播放页控制条可下载" else "$downloaded 首 · 断网也能播放",
                        group = group,
                        focusKey = "hub:downloads",
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate(TvRoute.Downloads.route) },
                    )
                }
                Spacer(Modifier.weight(1f))
                KeyHintBar(
                    hints = listOf(
                        "OK" to "进入",
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
private fun HubCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    group: TvFocusGroup,
    focusKey: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TvSurface(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(20.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.7f),
        focusedScale = 1.03f,
        glowSize = 14.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvSurface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(18.dp),
                baseColor = accent.copy(alpha = 0.16f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvIcon(icon, Modifier.size(30.dp), tint = accent)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = TvType.headline, color = TvColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvIcon(Icons.Rounded.ChevronRight, Modifier.size(24.dp), tint = TvColors.TextTertiary)
        }
    }
}
