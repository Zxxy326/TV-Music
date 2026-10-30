package tv.music.player.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import tv.music.player.BuildConfig
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.settings.AppSettings
import tv.music.player.core.theme.LocalAnimationsEnabled
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.TagChip
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun SettingsScreen(
    settings: AppSettings,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("settings")
    val groups = SettingsGroups.all
    var focusedId by remember { mutableStateOf(groups.first().id) }
    val focusedGroup = groups.firstOrNull { it.id == focusedId } ?: groups.first()
    val animations = LocalAnimationsEnabled.current

    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.Settings.route
    LaunchedEffect(isActive) {
        if (isActive) group.restore("group:${groups.first().id}")
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) group.restore("group:${groups.first().id}")
    }

    Box(Modifier.fillMaxSize()) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TvSurface(
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        baseColor = TvColors.SurfaceElevated,
                        focusedScale = 1.1f,
                        glowSize = 12.dp,
                        group = group,
                        focusKey = "back",
                        recordFocus = false,
                        onClick = { navController.popBackStack() },
                    ) {
                        TvIcon(
                            icon = Icons.Rounded.ArrowBack,
                            modifier = Modifier.size(22.dp),
                            tint = TvColors.TextPrimary,
                            contentDescription = "返回",
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("设置", style = TvType.display, color = TvColors.TextPrimary)
                        Text(
                            "遥控器方向键浏览 · OK 进入 · 返回上一级",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "v${BuildConfig.VERSION_NAME} · M3c",
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.width(420.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(groups, key = { it.id }) { info ->
                            SettingsGroupRow(
                                info = info,
                                group = group,
                                selected = info.id == focusedId,
                                onClick = { navController.navigate(TvRoute.SettingsGroup.of(info.id)) },
                                onFocusedChange = { focused -> if (focused) focusedId = info.id },
                            )
                        }
                    }
                    Spacer(Modifier.width(28.dp))
                    TvSurface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(22.dp),
                        baseColor = TvColors.Surface.copy(alpha = 0.62f),
                    ) {
                        Crossfade(
                            targetState = focusedGroup,
                            animationSpec = tween(if (animations) 180 else 0),
                            label = "settings-detail",
                        ) { info ->
                            GroupPreview(info)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                KeyHintBar(
                    hints = listOf(
                        "方向" to "浏览分组",
                        "OK" to "进入设置",
                        "返回" to "上一级",
                        "长按返回" to "回到首页",
                    )
                )
                Spacer(Modifier.height(104.dp))
            }
        }
    }
}

@Composable
private fun SettingsGroupRow(
    info: SettingsGroupInfo,
    group: tv.music.player.core.focus.TvFocusGroup,
    selected: Boolean,
    onClick: () -> Unit,
    onFocusedChange: (Boolean) -> Unit,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(16.dp),
        baseColor = if (selected) accent.copy(alpha = 0.1f) else TvColors.Surface.copy(alpha = 0.56f),
        focusedScale = 1.03f,
        glowSize = 12.dp,
        group = group,
        focusKey = "group:${info.id}",
        onClick = onClick,
        onFocusedChange = onFocusedChange,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = if (selected) 0.2f else 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                TvIcon(info.icon, Modifier.size(23.dp), tint = accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(info.title, style = TvType.title, color = TvColors.TextPrimary)
                Text(
                    info.subtitle,
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                )
            }
            TvIcon(Icons.Rounded.ChevronRight, Modifier.size(20.dp), tint = TvColors.TextTertiary)
        }
    }
}

@Composable
private fun GroupPreview(info: SettingsGroupInfo) {
    val accent = LocalTvAccent.current
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            TvIcon(info.icon, Modifier.size(32.dp), tint = accent)
        }
        Spacer(Modifier.height(18.dp))
        Text(info.title, style = TvType.headline, color = TvColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(info.subtitle, style = TvType.label, color = accent)
        Spacer(Modifier.height(16.dp))
        Text(
            info.description,
            style = TvType.body,
            color = TvColors.TextSecondary,
        )
        Spacer(Modifier.height(20.dp))
        info.highlights.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { TagChip(it) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            "按 OK 进入 ${info.title}",
            style = TvType.caption,
            color = TvColors.TextTertiary,
        )
    }
}
