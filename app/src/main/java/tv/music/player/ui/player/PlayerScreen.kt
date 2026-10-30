package tv.music.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.lyrics.LyricLine
import tv.music.player.core.lyrics.LyricWord
import tv.music.player.core.lyrics.Lyrics
import tv.music.player.core.lyrics.LyricsRepository
import tv.music.player.core.lyrics.LyricsState
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.playback.PlayerSnapshot
import tv.music.player.core.playback.formatDuration
import tv.music.player.core.settings.AppSettings
import tv.music.player.core.settings.LyricStyle
import tv.music.player.core.settings.SettingsRepository
import tv.music.player.core.settings.formatLyricOffset
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
    settingsRepository: SettingsRepository,
    lyricsRepository: LyricsRepository,
    onBack: () -> Unit,
) {
    val snapshot by playback.state.collectAsStateWithLifecycle()
    val cover = rememberCoverArt()
    val accent = LocalTvAccent.current
    val capsule = LocalCapsuleHost.current
    val group = rememberTvFocusGroup("player")
    val stageRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var controlsVisible by remember { mutableStateOf(false) }
    var queueVisible by remember { mutableStateOf(false) }
    var lyricsPanelVisible by remember { mutableStateOf(false) }
    var lyricOption by remember { mutableIntStateOf(settings.lyricStyle.ordinal) }
    var longSeekHandled by remember { mutableStateOf(false) }
    var lyricsState by remember(snapshot.track.id) { mutableStateOf<LyricsState>(LyricsState.Loading) }
    var lyricsAttempt by remember(snapshot.track.id) { mutableIntStateOf(0) }

    val style = settings.lyricStyle

    LaunchedEffect(snapshot.track.id, lyricsAttempt) {
        lyricsState = LyricsState.Loading
        lyricsState = lyricsRepository.load(snapshot.track)
    }

    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(controlsVisible, queueVisible, lyricsPanelVisible, windowFocused) {
        if (!windowFocused) return@LaunchedEffect
        when {
            lyricsPanelVisible -> return@LaunchedEffect
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
    BackHandler(enabled = lyricsPanelVisible) { lyricsPanelVisible = false }
    BackHandler(enabled = !queueVisible && !lyricsPanelVisible) { onBack() }

    fun saveSetting(message: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { capsule.show(message) }
                .onFailure { capsule.show("保存失败：${it.message.orEmpty()}") }
        }
    }

    fun openLyricsPanel() {
        lyricOption = settings.lyricStyle.ordinal
        lyricsPanelVisible = true
    }

    fun activateLyricOption(index: Int) {
        when (index) {
            0, 1, 2, 3, 4, 5 -> {
                val style = LyricStyle.entries[index]
                saveSetting("歌词样式：${style.label}") { settingsRepository.setLyricStyle(style) }
            }
            6 -> saveSetting(if (settings.showLyricTranslation) "已隐藏歌词翻译" else "已显示歌词翻译") {
                settingsRepository.setShowLyricTranslation(!settings.showLyricTranslation)
            }
            7 -> {
                val next = (settings.lyricOffsetMs + 500).coerceIn(-5_000, 5_000)
                saveSetting("歌词偏移：${formatLyricOffset(next)}") { settingsRepository.setLyricOffset(next) }
            }
            8 -> {
                val next = (settings.lyricOffsetMs - 500).coerceIn(-5_000, 5_000)
                saveSetting("歌词偏移：${formatLyricOffset(next)}") { settingsRepository.setLyricOffset(next) }
            }
            9 -> saveSetting("歌词偏移已归零") { settingsRepository.setLyricOffset(0) }
            10 -> {
                lyricsAttempt++
                capsule.show("正在重新获取歌词")
            }
        }
    }

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
                if (lyricsPanelVisible) {
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                lyricOption = (lyricOption + 10) % 11
                                true
                            }
                            Key.DirectionRight -> {
                                lyricOption = (lyricOption + 1) % 11
                                true
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                activateLyricOption(lyricOption)
                                true
                            }
                            Key.Menu -> {
                                lyricsPanelVisible = false
                                true
                            }
                            else -> false
                        }
                    }
                } else if (controlsVisible || queueVisible) {
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
                                openLyricsPanel()
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
    ) {
        if (style == LyricStyle.MINIMAL) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else {
            PlayerBackdrop(coverImage = cover, blur = settings.blurBackground, accent = accent)
        }

        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                if (style != LyricStyle.MINIMAL) {
                    PlayerTopBar(snapshot = snapshot, settings = settings)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LyricsView(
                        snapshot = snapshot,
                        state = lyricsState,
                        settings = settings,
                        style = style,
                    )
                }
                if (lyricsPanelVisible) Spacer(Modifier.height(190.dp))
                if (!controlsVisible && !queueVisible && !lyricsPanelVisible) {
                    KeyHintBar(
                        hints = listOf(
                            "OK" to "播放 / 暂停",
                            "← →" to "快退 / 快进 10s",
                            "长按 ← →" to "上一首 / 下一首",
                            "↑" to "控制条",
                            "↓" to "播放队列",
                            "MENU" to "歌词设置",
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
                onOpenLyrics = {
                    controlsVisible = false
                    openLyricsPanel()
                },
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

        AnimatedVisibility(
            visible = lyricsPanelVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = 20.dp),
            enter = slideInVertically(tween(200)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(120)),
        ) {
            LyricsSettingsPanel(
                settings = settings,
                lyricsState = lyricsState,
                selectedIndex = lyricOption,
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
private fun LyricsView(
    snapshot: PlayerSnapshot,
    state: LyricsState,
    settings: AppSettings,
    style: LyricStyle,
) {
    val scale = settings.lyricSize.scale
    when (state) {
        LyricsState.Loading -> LyricsMessage(
            title = if (snapshot.buffering) "正在解析音源…" else "正在获取歌词…",
            subtitle = null,
            scale = scale,
        )
        is LyricsState.Failed -> LyricsMessage(
            title = "歌词获取失败",
            subtitle = "${state.message} · 按 MENU 重新获取",
            scale = scale,
            accent = true,
        )
        is LyricsState.Unavailable -> LyricsMessage(
            title = state.message,
            subtitle = if (state.message.contains("纯音乐")) "这首歌没有填词" else "按 MENU 可重新获取",
            scale = scale,
        )
        is LyricsState.Ready -> {
            val lyrics = state.lyrics
            when (style) {
                LyricStyle.CLASSIC -> ScrollingLyrics(
                    lyrics = lyrics,
                    positionMs = snapshot.positionMs,
                    offsetMs = settings.lyricOffsetMs,
                    showTranslation = settings.showLyricTranslation,
                    scale = scale,
                    currentSp = 44f,
                    otherSp = 25f,
                    animations = settings.animationsEnabled,
                )
                LyricStyle.MINIMAL -> ScrollingLyrics(
                    lyrics = lyrics,
                    positionMs = snapshot.positionMs,
                    offsetMs = settings.lyricOffsetMs,
                    showTranslation = settings.showLyricTranslation,
                    scale = scale,
                    currentSp = 50f,
                    otherSp = 26f,
                    animations = settings.animationsEnabled,
                )
                LyricStyle.SUBTITLE -> SubtitleLyrics(
                    lyrics = lyrics,
                    positionMs = snapshot.positionMs,
                    offsetMs = settings.lyricOffsetMs,
                    showTranslation = settings.showLyricTranslation,
                    scale = scale,
                )
                LyricStyle.KARAOKE -> KaraokeLyrics(
                    lyrics = lyrics,
                    positionMs = snapshot.positionMs,
                    offsetMs = settings.lyricOffsetMs,
                    showTranslation = settings.showLyricTranslation,
                    scale = scale,
                    animations = settings.animationsEnabled,
                    bilingual = false,
                )
                LyricStyle.BOUNCE -> BounceLyrics(
                    lyrics = lyrics,
                    positionMs = snapshot.positionMs,
                    offsetMs = settings.lyricOffsetMs,
                    showTranslation = settings.showLyricTranslation,
                    scale = scale,
                    animations = settings.animationsEnabled,
                )
                LyricStyle.BILINGUAL -> KaraokeLyrics(
                    lyrics = lyrics,
                    positionMs = snapshot.positionMs,
                    offsetMs = settings.lyricOffsetMs,
                    showTranslation = settings.showLyricTranslation,
                    scale = scale,
                    animations = settings.animationsEnabled,
                    bilingual = true,
                )
            }
        }
    }
}

@Composable
private fun LyricsMessage(
    title: String,
    subtitle: String?,
    scale: Float,
    accent: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = TextStyle(fontSize = (34f * scale).sp, fontWeight = FontWeight.Bold),
            color = if (accent) LocalTvAccent.current else Color.White,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = subtitle,
                style = TextStyle(fontSize = (20f * scale).sp),
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun ScrollingLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    currentSp: Float,
    otherSp: Float,
    animations: Boolean,
) {
    val currentIndex = lyrics.indexAt(positionMs, offsetMs.toLong())
    val scrollState = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    val heights = remember(lyrics) { mutableStateMapOf<Int, Int>() }
    val halfSpacer = with(LocalDensity.current) { (viewportPx / 2).toDp() }

    LaunchedEffect(
        currentIndex,
        heights.size,
        heights[currentIndex],
        viewportPx,
        showTranslation,
        scale,
    ) {
        if (currentIndex < 0 || viewportPx == 0) return@LaunchedEffect
        val currentHeight = heights[currentIndex] ?: 0
        if (currentHeight == 0) return@LaunchedEffect
        var before = 0
        for (index in 0 until currentIndex) before += heights[index] ?: 0
        val target = (before + currentHeight / 2).coerceIn(0, scrollState.maxValue)
        if (animations) scrollState.animateScrollTo(target) else scrollState.scrollTo(target)
    }

    Box(Modifier.fillMaxSize().onSizeChanged { viewportPx = it.height }) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (viewportPx > 0) Spacer(Modifier.height(halfSpacer))
            lyrics.lines.forEachIndexed { index, line ->
                LyricRow(
                    line = line,
                    index = index,
                    currentIndex = currentIndex,
                    showTranslation = showTranslation,
                    scale = scale,
                    currentSp = currentSp,
                    otherSp = otherSp,
                    onMeasured = { heights[index] = it },
                )
            }
            if (viewportPx > 0) Spacer(Modifier.height(halfSpacer))
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    index: Int,
    currentIndex: Int,
    showTranslation: Boolean,
    scale: Float,
    currentSp: Float,
    otherSp: Float,
    onMeasured: (Int) -> Unit,
) {
    val active = index == currentIndex
    val distance = if (currentIndex < 0) 99 else kotlin.math.abs(index - currentIndex)
    val targetAlpha = when (distance) {
        0 -> 1f
        1 -> 0.45f
        else -> 0.2f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(220),
        label = "lyric-alpha-$index",
    )
    val fontSize = (if (active) currentSp else otherSp) * scale
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasured(it.height) }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = line.text,
            style = TextStyle(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.3f).sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            ),
            color = Color.White.copy(alpha = alpha),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        val translation = line.translation
        if (showTranslation && !translation.isNullOrBlank()) {
            Text(
                text = translation,
                style = TextStyle(
                    fontSize = (fontSize * 0.52f).sp,
                    lineHeight = (fontSize * 0.7f).sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color.White.copy(alpha = alpha * 0.62f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun SubtitleLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
) {
    val index = lyrics.indexAt(positionMs, offsetMs.toLong()).coerceAtLeast(0)
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 4.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Crossfade(
            targetState = index,
            animationSpec = tween(240),
            label = "subtitle-lyrics",
        ) { current ->
            val line = lyrics.lines.getOrNull(current) ?: return@Crossfade
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedLyric(line.text, (64f * scale).sp)
                if (showTranslation && !line.translation.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedLyric(line.translation, (30f * scale).sp, alpha = 0.9f)
                }
                val next = lyrics.lines.getOrNull(current + 1)
                if (next != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = next.text,
                        style = TextStyle(
                            fontSize = (28f * scale).sp,
                            lineHeight = (34f * scale).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutlinedLyric(text: String, fontSize: androidx.compose.ui.unit.TextUnit, alpha: Float = 1f) {
    val base = TextStyle(
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.16f).sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    val strokePx = with(LocalDensity.current) { 2.5.dp.toPx() }
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = base.copy(drawStyle = Stroke(width = strokePx)),
            color = Color.Black.copy(alpha = alpha),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = text,
            style = base,
            color = Color.White.copy(alpha = alpha),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SyncedLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    animations: Boolean,
    content: @Composable (LyricLine, Boolean) -> Unit,
) {
    val currentIndex = lyrics.indexAt(positionMs, offsetMs.toLong())
    val scrollState = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    val heights = remember(lyrics) { mutableStateMapOf<Int, Int>() }
    val halfSpacer = with(LocalDensity.current) { (viewportPx / 2).toDp() }

    LaunchedEffect(currentIndex, heights.size, heights[currentIndex], viewportPx) {
        if (currentIndex < 0 || viewportPx == 0) return@LaunchedEffect
        val currentHeight = heights[currentIndex] ?: 0
        if (currentHeight == 0) return@LaunchedEffect
        var before = 0
        for (index in 0 until currentIndex) before += heights[index] ?: 0
        val target = (before + currentHeight / 2).coerceIn(0, scrollState.maxValue)
        if (animations) scrollState.animateScrollTo(target) else scrollState.scrollTo(target)
    }

    Box(Modifier.fillMaxSize().onSizeChanged { viewportPx = it.height }) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (viewportPx > 0) Spacer(Modifier.height(halfSpacer))
            lyrics.lines.forEachIndexed { index, line ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { heights[index] = it.height }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    content(line, index == currentIndex)
                }
            }
            if (viewportPx > 0) Spacer(Modifier.height(halfSpacer))
        }
    }
}

@Composable
private fun KaraokeLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    animations: Boolean,
    bilingual: Boolean,
) {
    SyncedLyrics(lyrics, positionMs, offsetMs, animations) { line, active ->
        if (active) {
            KaraokeLine(
                line = line,
                positionMs = positionMs,
                offsetMs = offsetMs,
                showTranslation = showTranslation,
                scale = scale,
                bilingual = bilingual,
            )
        } else {
            PlainLyricLine(
                line = line,
                scale = scale,
                showTranslation = showTranslation && bilingual,
                active = false,
            )
        }
    }
}

@Composable
private fun KaraokeLine(
    line: LyricLine,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    bilingual: Boolean,
) {
    val accent = LocalTvAccent.current
    val measurer = rememberTextMeasurer()
    val fontSize = (42f * scale).sp
    val style = TextStyle(
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.28f).sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        val widthPx = constraints.maxWidth
        val layout = remember(line.text, style, widthPx) {
            measurer.measure(line.text, style, constraints = Constraints(maxWidth = widthPx))
        }
        val heightDp = with(density) { layout.size.height.toDp() }
        val textX = ((widthPx - layout.size.width) / 2f).coerceAtLeast(0f)
        Canvas(Modifier.fillMaxWidth().height(heightDp)) {
            drawText(layout, color = Color.White.copy(alpha = 0.28f), topLeft = Offset(textX, 0f))
            val sung = karaokeChars(line, positionMs + offsetMs)
            if (sung > 0f) {
                drawKaraokeSweep(layout, sung, accent, textX)
            }
        }
    }
    if (showTranslation && !line.translation.isNullOrBlank()) {
        Spacer(Modifier.height(if (bilingual) 8.dp else 4.dp))
        Text(
            text = line.translation,
            style = TextStyle(
                fontSize = ((if (bilingual) 25f else 22f) * scale).sp,
                lineHeight = ((if (bilingual) 32f else 28f) * scale).sp,
                fontWeight = FontWeight.Medium,
            ),
            color = if (bilingual) accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKaraokeSweep(
    layout: androidx.compose.ui.text.TextLayoutResult,
    charsSung: Float,
    accent: Color,
    textX: Float,
) {
    for (lineIndex in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(lineIndex)
        val lineEnd = layout.getLineEnd(lineIndex)
        if (lineEnd <= lineStart) continue
        val top = layout.getLineTop(lineIndex)
        val bottom = layout.getLineBottom(lineIndex)
        when {
            charsSung >= lineEnd -> clipRect(left = textX, top = top, right = textX + layout.size.width, bottom = bottom) {
                drawText(layout, color = accent, topLeft = Offset(textX, 0f))
            }
            charsSung > lineStart -> {
                val charIndex = charsSung.toInt().coerceIn(lineStart, lineEnd - 1)
                val box = layout.getBoundingBox(charIndex)
                val boundary = textX + box.left + box.width * (charsSung - charIndex)
                clipRect(left = textX, top = top, right = boundary, bottom = bottom) {
                    drawText(layout, color = accent, topLeft = Offset(textX, 0f))
                }
            }
        }
    }
}

private fun karaokeChars(line: LyricLine, timeMs: Long): Float {
    val max = line.text.length.toFloat()
    if (max <= 0f) return 0f
    if (line.words.isEmpty()) {
        val duration = line.durationMs.takeIf { it > 0L } ?: 4_000L
        val fraction = ((timeMs - line.timeMs).toFloat() / duration).coerceIn(0f, 1f)
        return fraction * max
    }
    var sung = 0
    for (word in line.words) {
        val end = word.timeMs + word.durationMs
        if (timeMs < word.timeMs) return sung.toFloat().coerceAtMost(max)
        if (timeMs < end) {
            val fraction = if (word.durationMs <= 0L) 0f else
                (timeMs - word.timeMs).toFloat() / word.durationMs
            return (sung + fraction * word.text.length).coerceAtMost(max)
        }
        sung += word.text.length
    }
    return max
}

@Composable
private fun PlainLyricLine(
    line: LyricLine,
    scale: Float,
    showTranslation: Boolean,
    active: Boolean,
) {
    val fontSize = ((if (active) 40f else 24f) * scale).sp
    Text(
        text = line.text,
        style = TextStyle(
            fontSize = fontSize,
            lineHeight = (fontSize.value * 1.3f).sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        ),
        color = Color.White.copy(alpha = if (active) 0.95f else 0.35f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
    if (showTranslation && !line.translation.isNullOrBlank()) {
        Text(
            text = line.translation,
            style = TextStyle(
                fontSize = (18f * scale).sp,
                lineHeight = (24f * scale).sp,
                fontWeight = FontWeight.Normal,
            ),
            color = Color.White.copy(alpha = 0.38f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun BounceLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    animations: Boolean,
) {
    SyncedLyrics(lyrics, positionMs, offsetMs, animations) { line, active ->
        if (active) {
            BounceLine(
                line = line,
                positionMs = positionMs,
                offsetMs = offsetMs,
                showTranslation = showTranslation,
                scale = scale,
                animations = animations,
            )
        } else {
            PlainLyricLine(line = line, scale = scale, showTranslation = false, active = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BounceLine(
    line: LyricLine,
    positionMs: Long,
    offsetMs: Int,
    showTranslation: Boolean,
    scale: Float,
    animations: Boolean,
) {
    val accent = LocalTvAccent.current
    val t = positionMs + offsetMs
    val words = if (line.words.isNotEmpty()) {
        line.words
    } else {
        listOf(LyricWord(line.timeMs, line.durationMs.takeIf { it > 0L } ?: 4_000L, line.text))
    }
    val activeIndex = words.indexOfLast { t >= it.timeMs && t < it.timeMs + it.durationMs }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.Center,
    ) {
        words.forEachIndexed { index, word ->
            val active = index == activeIndex
            val emphasize = active && word.text.any { it.isLetterOrDigit() }
            val sung = word.timeMs <= t
            val bounce by animateFloatAsState(
                targetValue = if (emphasize) 1.18f else 1f,
                animationSpec = if (animations) {
                    spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
                } else {
                    tween(0)
                },
                label = "bounce-$index",
            )
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = bounce
                        scaleY = bounce
                    }
                    .background(
                        color = if (emphasize) accent.copy(alpha = 0.24f) else Color.Transparent,
                        shape = shape,
                    )
                    .padding(horizontal = 1.dp, vertical = 2.dp),
            ) {
                Text(
                    text = word.text,
                    style = TextStyle(
                        fontSize = (36f * scale).sp,
                        lineHeight = (42f * scale).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = when {
                        active -> accent
                        sung -> Color.White.copy(alpha = 0.92f)
                        else -> Color.White.copy(alpha = 0.3f)
                    },
                )
            }
        }
    }
    if (showTranslation && !line.translation.isNullOrBlank()) {
        Text(
            text = line.translation,
            style = TextStyle(
                fontSize = (21f * scale).sp,
                lineHeight = (27f * scale).sp,
                fontWeight = FontWeight.Medium,
            ),
            color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp, start = 24.dp, end = 24.dp),
        )
    }
}

@Composable
private fun LyricsSettingsPanel(
    settings: AppSettings,
    lyricsState: LyricsState,
    selectedIndex: Int,
) {
    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.96f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("歌词", style = TvType.title, color = TvColors.TextPrimary)
                    Text(
                        "${settings.lyricStyle.label} · ${settings.lyricStyle.hint}",
                        style = TvType.caption,
                        color = TvColors.TextSecondary,
                    )
                }
                TagChip("偏移 ${formatLyricOffset(settings.lyricOffsetMs)}", accent = true)
                Spacer(Modifier.width(12.dp))
                TagChip(
                    when (lyricsState) {
                        LyricsState.Loading -> "加载中"
                        is LyricsState.Ready -> "${lyricsState.lyrics.lines.size} 行"
                        is LyricsState.Failed -> "获取失败"
                        is LyricsState.Unavailable -> "暂无歌词"
                    }
                )
                Spacer(Modifier.width(16.dp))
                Text("MENU / 返回 关闭", style = TvType.caption, color = TvColors.TextTertiary)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LyricStyle.entries.forEachIndexed { index, option ->
                    LyricChoice(
                        label = option.label,
                        selected = selectedIndex == index,
                        active = option == settings.lyricStyle,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LyricChoice(
                    label = if (settings.showLyricTranslation) "翻译 开" else "翻译 关",
                    selected = selectedIndex == 6,
                    active = settings.showLyricTranslation,
                )
                LyricChoice(label = "提前 0.5s", selected = selectedIndex == 7)
                LyricChoice(label = "延后 0.5s", selected = selectedIndex == 8)
                LyricChoice(label = "归零", selected = selectedIndex == 9)
                LyricChoice(label = "重新获取", selected = selectedIndex == 10)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "← → 选择 · OK 执行 · MENU / 返回 关闭；偏移 ±0.5s 步进、范围 ±5s。逐字样式已接入，样式轮盘与布局 B 后续补齐。",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun LyricChoice(label: String, selected: Boolean, active: Boolean = false) {
    val accent = LocalTvAccent.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (active) accent.copy(alpha = 0.18f) else TvColors.SurfaceElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else TvColors.Divider,
                shape = shape,
            )
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = TvType.label,
            color = if (active || selected) accent else TvColors.TextPrimary,
            maxLines = 1,
        )
    }
}


@Composable
private fun ControlsPanel(
    snapshot: PlayerSnapshot,
    settings: AppSettings,
    group: TvFocusGroup,
    playback: PlaybackController,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onClose: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.95f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                when (event.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        onClose()
                        true
                    }
                    Key.Menu -> {
                        onOpenLyrics()
                        true
                    }
                    else -> false
                }
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
