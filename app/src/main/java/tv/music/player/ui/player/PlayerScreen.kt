package tv.music.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.playback.DemoCatalog
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.playback.PlayerSnapshot
import tv.music.player.core.playback.formatDuration
import tv.music.player.core.settings.AppSettings
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.core.theme.rememberCoverArt
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.ArtworkPlaceholder
import tv.music.player.ui.components.KeyHintBar
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.ProgressLine
import tv.music.player.ui.components.QueueRow
import tv.music.player.ui.components.TagChip
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea

@Composable
fun PlayerScreen(
    playback: PlaybackController,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val snapshot by playback.state.collectAsStateWithLifecycle()
    val cover = rememberCoverArt()
    val accent = LocalTvAccent.current
    val capsule = LocalCapsuleHost.current
    val group = rememberTvFocusGroup("player")
    val stageRequester = remember { FocusRequester() }

    var controlsVisible by remember { mutableStateOf(false) }
    var queueVisible by remember { mutableStateOf(false) }
    var longSeekHandled by remember { mutableStateOf(false) }

    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(controlsVisible, queueVisible, windowFocused) {
        if (!windowFocused) return@LaunchedEffect
        when {
            queueVisible -> {
                delay(90)
                group.focus("queue:0")
            }
            controlsVisible -> {
                delay(90)
                group.focus("controls:play")
            }
            else -> {
                delay(60)
                runCatching { stageRequester.requestFocus() }
            }
        }
    }

    BackHandler(enabled = queueVisible) { queueVisible = false }
    BackHandler(enabled = !queueVisible) { onBack() }

    fun seekWithLongPress(direction: Int, repeatCount: Int) {
        if (repeatCount >= 3) {
            if (!longSeekHandled) {
                longSeekHandled = true
                if (direction < 0) playback.previous() else playback.next()
                capsule.show(if (direction < 0) "上一首" else "下一首")
            }
        } else {
            playback.seekBy(direction * 10_000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(stageRequester)
            .focusable()
            .onKeyEvent { event ->
                if (controlsVisible || queueVisible) {
                    false
                } else {
                    when {
                        event.type == KeyEventType.KeyUp &&
                            (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> {
                            longSeekHandled = false
                            false
                        }
                        event.type != KeyEventType.KeyDown -> false
                        else -> when (event.key) {
                            Key.DirectionCenter, Key.Enter -> {
                                playback.toggle()
                                true
                            }
                            Key.DirectionLeft -> {
                                seekWithLongPress(-1, event.nativeKeyEvent.repeatCount)
                                true
                            }
                            Key.DirectionRight -> {
                                seekWithLongPress(1, event.nativeKeyEvent.repeatCount)
                                true
                            }
                            Key.DirectionUp -> {
                                controlsVisible = true
                                true
                            }
                            Key.DirectionDown -> {
                                queueVisible = true
                                true
                            }
                            Key.Menu -> {
                                capsule.show("歌词样式轮盘将在 M2 接入")
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
    ) {
        PlayerBackdrop(coverImage = cover, blur = settings.blurBackground, accent = accent)

        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                PlayerTopBar(snapshot = snapshot, settings = settings)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LyricsView(snapshot = snapshot, scale = settings.lyricSize.scale)
                }
                if (!controlsVisible && !queueVisible) {
                    KeyHintBar(
                        hints = listOf(
                            "OK" to "播放 / 暂停",
                            "← →" to "快退 / 快进 10s",
                            "长按 ← →" to "上一首 / 下一首",
                            "↑" to "控制条",
                            "↓" to "播放队列",
                        )
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = 20.dp),
            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(120)),
        ) {
            ControlsPanel(
                snapshot = snapshot,
                settings = settings,
                group = group,
                playback = playback,
                onOpenQueue = { queueVisible = true },
                onClose = { controlsVisible = false },
            )
        }

        AnimatedVisibility(
            visible = queueVisible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(220)) { it } + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(200)) { it } + fadeOut(tween(140)),
        ) {
            QueuePanel(
                snapshot = snapshot,
                group = group,
                playback = playback,
                onClose = { queueVisible = false },
            )
        }
    }
}

@Composable
private fun PlayerBackdrop(
    coverImage: tv.music.player.core.theme.CoverArt,
    blur: Boolean,
    accent: Color,
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            bitmap = coverImage.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (blur) Modifier.blur(90.dp) else Modifier),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            TvColors.Background.copy(alpha = 0.32f),
                            TvColors.Background.copy(alpha = 0.55f),
                            TvColors.Background.copy(alpha = 0.92f),
                        )
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun PlayerTopBar(snapshot: PlayerSnapshot, settings: AppSettings) {
    val accent = LocalTvAccent.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ArtworkPlaceholder(
            seed = snapshot.track.colorSeed,
            modifier = Modifier.size(88.dp),
            corner = 18.dp,
        )
        Spacer(Modifier.width(18.dp))
        Column {
            Text(
                if (snapshot.track.isOnline) "正在播放 · 在线音源" else "正在播放 · 布局 A 全屏歌词",
                style = TvType.caption,
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                snapshot.track.title,
                style = TvType.display,
                color = TvColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${snapshot.track.artist} · ${snapshot.track.album}",
                style = TvType.label,
                color = TvColors.TextSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (snapshot.buffering) {
                    TagChip("解析中", accent = true)
                } else {
                    TagChip("音质 ${settings.quality.label}", accent = true)
                }
                TagChip("队列 ${snapshot.queue.size}")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "返回 上一级 · 长按返回 回到首页",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun LyricsView(snapshot: PlayerSnapshot, scale: Float) {
    if (snapshot.track.isOnline) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (snapshot.buffering) "正在解析音源…" else "在线播放",
                style = TextStyle(
                    fontSize = (34f * scale).sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "歌词时间轴将在 M2 接入",
                style = TextStyle(fontSize = (20f * scale).sp),
                color = Color.White.copy(alpha = 0.45f),
            )
        }
        return
    }

    val currentIndex = DemoCatalog.lyrics
        .indexOfLast { it.timeMs <= snapshot.positionMs }
        .coerceAtLeast(0)
    val window = (-2..2).map { currentIndex + it }.filter { it in DemoCatalog.lyrics.indices }

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        window.forEach { index ->
            val isCurrent = index == currentIndex
            val fontSize = if (isCurrent) (44f * scale).sp else (25f * scale).sp
            Text(
                text = DemoCatalog.lyrics[index].text,
                style = TextStyle(
                    fontSize = fontSize,
                    lineHeight = (fontSize.value * 1.35f).sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.34f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ControlsPanel(
    snapshot: PlayerSnapshot,
    settings: AppSettings,
    group: TvFocusGroup,
    playback: PlaybackController,
    onOpenQueue: () -> Unit,
    onClose: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.95f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown &&
                (event.key == Key.DirectionUp || event.key == Key.DirectionDown)
            ) {
                onClose()
                true
            } else {
                false
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkPlaceholder(snapshot.track.colorSeed, Modifier.size(58.dp), corner = 12.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        snapshot.track.title,
                        style = TvType.title,
                        color = TvColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${snapshot.track.artist} · ${snapshot.track.album}",
                        style = TvType.caption,
                        color = TvColors.TextSecondary,
                    )
                }
                TagChip("音质 ${settings.quality.label}", accent = true)
                Spacer(Modifier.width(10.dp))
                ActionChip(
                    label = "队列 ${snapshot.queue.size}",
                    icon = Icons.Rounded.QueueMusic,
                    group = group,
                    focusKey = "controls:queue",
                    onClick = onOpenQueue,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(snapshot.positionMs),
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                )
                Spacer(Modifier.width(14.dp))
                ProgressLine(snapshot.progress, Modifier.weight(1f), height = 6.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    formatDuration(snapshot.durationMs),
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(
                    icon = Icons.Rounded.Replay10,
                    contentDescription = "快退 10 秒",
                    group = group,
                    focusKey = "controls:replay",
                    onClick = { playback.seekBy(-10_000L) },
                )
                TransportButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    group = group,
                    focusKey = "controls:prev",
                    onClick = playback::previous,
                )
                TransportButton(
                    icon = if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (snapshot.isPlaying) "暂停" else "播放",
                    group = group,
                    focusKey = "controls:play",
                    onClick = playback::toggle,
                    size = 68.dp,
                    primary = true,
                )
                TransportButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    group = group,
                    focusKey = "controls:next",
                    onClick = playback::next,
                )
                TransportButton(
                    icon = Icons.Rounded.Forward10,
                    contentDescription = "快进 10 秒",
                    group = group,
                    focusKey = "controls:forward",
                    onClick = { playback.seekBy(10_000L) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "返回 / ↓ 收起控制条 · 方向键在控制条内移动",
                style = TvType.caption,
                color = TvColors.TextTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    size: Dp = 54.dp,
    primary: Boolean = false,
) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        baseColor = if (primary) accent else TvColors.SurfaceHigh,
        focusedScale = 1.14f,
        glowSize = 14.dp,
        focusedBorderColor = if (primary) Color.White.copy(alpha = 0.92f) else null,
        focusedGlowColor = if (primary) Color.White.copy(alpha = 0.45f) else null,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TvIcon(
                icon = icon,
                modifier = Modifier.size(if (primary) 32.dp else 23.dp),
                tint = if (primary) Color(0xFF06110F) else TvColors.TextPrimary,
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
private fun QueuePanel(
    snapshot: PlayerSnapshot,
    group: TvFocusGroup,
    playback: PlaybackController,
    onClose: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.fillMaxHeight().width(392.dp),
        shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.98f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("播放队列", style = TvType.headline, color = TvColors.TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text("${snapshot.queue.size} 首", style = TvType.caption, color = TvColors.TextTertiary)
                Spacer(Modifier.weight(1f))
                ActionChip(
                    label = "关闭",
                    group = group,
                    focusKey = "queue:close",
                    onClick = onClose,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "OK 切歌 · 返回收起 · 当前曲目高亮",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(snapshot.queue, key = { _, track -> track.id }) { index, track ->
                    QueueRow(
                        track = track,
                        index = index,
                        isCurrent = track.id == snapshot.track.id,
                        group = group,
                        focusKey = "queue:$index",
                        onClick = {
                            playback.playFromQueue(track)
                            onClose()
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "队列重排（上移/下移/置顶/删除）在 M1 接入",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
        }
    }
}
