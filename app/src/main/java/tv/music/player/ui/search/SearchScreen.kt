package tv.music.player.ui.search

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.playback.DemoCatalog
import tv.music.player.core.playback.PlaybackController
import tv.music.player.core.playback.DemoTrack
import tv.music.player.core.playback.formatDuration
import tv.music.player.core.search.OnlineSearchApi
import tv.music.player.core.search.decodeTracks
import tv.music.player.core.search.encodeTracks
import tv.music.player.core.search.toDemoTrack
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.ArtworkPlaceholder
import tv.music.player.ui.components.Keycap
import tv.music.player.ui.components.LocalCapsuleHost
import tv.music.player.ui.components.ScreenHeader
import tv.music.player.ui.components.SkeletonBox
import tv.music.player.ui.components.TvBackdrop
import tv.music.player.ui.components.TvIcon
import tv.music.player.ui.components.TvSafeArea
import tv.music.player.ui.navigation.LocalActiveRoute
import tv.music.player.ui.navigation.TvRoute

@Composable
fun SearchScreen(
    playback: PlaybackController,
    onlineSearch: OnlineSearchApi,
    navController: NavHostController,
) {
    val group = rememberTvFocusGroup("search")
    val capsule = LocalCapsuleHost.current
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var resultsJson by rememberSaveable { mutableStateOf<String?>(null) }
    val results = remember(resultsJson) { decodeTracks(resultsJson) }
    var total by rememberSaveable { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf("夜航星", "晴天") }

    val caretAlpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "caret-alpha",
    )

    fun append(chars: String) {
        if (query.length >= 24) return
        query += chars
        if (resultsJson != null) resultsJson = null
    }

    fun backspace() {
        if (query.isNotEmpty()) query = query.dropLast(1)
        if (resultsJson != null) resultsJson = null
    }

    fun runSearch() {
        val term = query.trim()
        if (term.isEmpty()) {
            capsule.show("先输入关键词：数字键或九宫格")
            return
        }
        history.remove(term)
        history.add(0, term)
        while (history.size > 4) history.removeAt(history.lastIndex)
        resultsJson = null
        total = 0
        loading = true
        scope.launch {
            val result = runCatching { onlineSearch.search(term, 1, 30) }
            loading = false
            result.onSuccess { searchResult ->
                val tracks = searchResult.list.map { it.toDemoTrack() }
                resultsJson = tracks.encodeTracks()
                total = searchResult.total
                if (tracks.isEmpty()) {
                    capsule.show("没有找到「$term」")
                } else {
                    delay(60)
                    group.focus("results:0")
                    capsule.show("QQ 音乐 · 找到 ${searchResult.total} 首")
                }
            }.onFailure {
                capsule.show("搜索失败，请检查网络后重试")
            }
        }
    }

    val activeRoute = LocalActiveRoute.current
    val isActive = activeRoute == TvRoute.Search.route
    LaunchedEffect(isActive) {
        if (isActive) group.restore("t9:0")
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused && isActive) group.restore("t9:0")
    }

    val playTrack: (DemoTrack) -> Unit = { track ->
        playback.play(track, results.orEmpty())
        capsule.show("正在解析音源…")
        navController.navigate(TvRoute.Player.route) { launchSingleTop = true }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    val digit = digitForKey(event.key)
                    when {
                        digit != null -> {
                            append(digit.toString())
                            true
                        }
                        event.key == Key.Backspace -> {
                            backspace()
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        TvBackdrop()
        TvSafeArea {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    title = "搜索",
                    subtitle = "T9 九宫格 · 数字键直接输入 · QQ 音乐在线搜索",
                    group = group,
                    onBack = { navController.popBackStack() },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Keycap("MENU")
                            Spacer(Modifier.width(8.dp))
                            Text("输入法切换", style = TvType.caption, color = TvColors.TextTertiary)
                        }
                    },
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.weight(1f)) {
                    Column(
                        Modifier.width(460.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        QueryPanel(query = query, caretAlpha = caretAlpha)
                        HotWords(
                            group = group,
                            onPick = {
                                query = it
                                resultsJson = null
                                runSearch()
                            },
                        )
                        HistoryWords(
                            group = group,
                            history = history,
                            onPick = {
                                query = it
                                resultsJson = null
                                runSearch()
                            },
                            onClear = {
                                history.clear()
                                capsule.show("已清除搜索历史")
                            },
                        )
                        Text(
                            "输入后按搜索：QQ 音乐提供元数据，本机 LX 音源脚本解析播放地址。",
                            style = TvType.caption,
                            color = TvColors.TextTertiary,
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when {
                            loading -> LoadingResults()
                            results != null -> ResultsPanel(
                                tracks = results,
                                total = total,
                                group = group,
                                onBackToKeyboard = {
                                    resultsJson = null
                                    group.focus("t9:0")
                                },
                                onPlay = playTrack,
                            )
                            else -> T9Panel(
                                group = group,
                                onDigit = ::append,
                                onBackspace = ::backspace,
                                onClear = {
                                    query = ""
                                    resultsJson = null
                                },
                                onSearch = ::runSearch,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryPanel(query: String, caretAlpha: Float) {
    val accent = LocalTvAccent.current
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(108.dp),
        shape = RoundedCornerShape(20.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.78f),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("搜索词", style = TvType.caption, color = TvColors.TextTertiary)
                Spacer(Modifier.weight(1f))
                Text("最多 24 字符", style = TvType.caption, color = TvColors.TextTertiary)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = query.ifEmpty { "输入歌名 / 歌手" },
                    style = TvType.display,
                    color = if (query.isEmpty()) TvColors.TextTertiary else TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "|",
                    style = TvType.display,
                    color = accent.copy(alpha = caretAlpha),
                )
            }
        }
    }
}

@Composable
private fun HotWords(group: TvFocusGroup, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("热搜", style = TvType.title, color = TvColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("热门搜索", style = TvType.caption, color = TvColors.TextTertiary)
        }
        DemoCatalog.hotWords.chunked(4).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { columnIndex, word ->
                    val index = rowIndex * 4 + columnIndex
                    SearchChip(
                        text = word,
                        group = group,
                        focusKey = "hot:$index",
                        onClick = { onPick(word) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryWords(
    group: TvFocusGroup,
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("最近搜索", style = TvType.title, color = TvColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            if (history.isNotEmpty()) {
                Text("${history.size} 条", style = TvType.caption, color = TvColors.TextTertiary)
            }
            Spacer(Modifier.weight(1f))
            ActionChip(
                label = "清除",
                group = group,
                focusKey = "history:clear",
                onClick = onClear,
            )
        }
        if (history.isEmpty()) {
            Text("暂无搜索记录", style = TvType.caption, color = TvColors.TextTertiary)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                history.take(4).forEachIndexed { index, word ->
                    SearchChip(
                        text = word,
                        group = group,
                        focusKey = "history:$index",
                        onClick = { onPick(word) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchChip(
    text: String,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.width(100.dp).height(34.dp),
        shape = RoundedCornerShape(50),
        baseColor = TvColors.SurfaceElevated,
        focusedScale = 1.08f,
        glowSize = 10.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = TvType.caption,
                color = TvColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class T9Key(val digit: String, val letters: String, val special: Boolean = false)

private val t9Keys = listOf(
    T9Key("1", ".,?!"),
    T9Key("2", "ABC"),
    T9Key("3", "DEF"),
    T9Key("4", "GHI"),
    T9Key("5", "JKL"),
    T9Key("6", "MNO"),
    T9Key("7", "PQRS"),
    T9Key("8", "TUV"),
    T9Key("9", "WXYZ"),
    T9Key("清空", "", special = true),
    T9Key("0", "空格"),
    T9Key("⌫", "删除", special = true),
)

@Composable
private fun T9Panel(
    group: TvFocusGroup,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(22.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.7f),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("九宫格输入", style = TvType.label, color = TvColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Text("方向键移动 · OK 输入", style = TvType.caption, color = TvColors.TextTertiary)
            }
            Spacer(Modifier.height(10.dp))
            t9Keys.chunked(3).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { columnIndex, key ->
                        val index = rowIndex * 3 + columnIndex
                        T9Button(
                            key = key,
                            group = group,
                            focusKey = "t9:$index",
                            onClick = {
                                when (key.digit) {
                                    "清空" -> onClear()
                                    "⌫" -> onBackspace()
                                    "0" -> Unit
                                    else -> onDigit(key.digit)
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.weight(1f))
            TvSurface(
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(50),
                baseColor = LocalTvAccent.current.copy(alpha = 0.18f),
                focusedScale = 1.02f,
                glowSize = 12.dp,
                group = group,
                focusKey = "t9:search",
                onClick = onSearch,
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvIcon(Icons.Rounded.Search, Modifier.size(20.dp), tint = LocalTvAccent.current)
                    Spacer(Modifier.width(8.dp))
                    Text("搜索", style = TvType.label, color = LocalTvAccent.current)
                }
            }
        }
    }
}

@Composable
private fun T9Button(
    key: T9Key,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.width(108.dp).height(58.dp),
        shape = RoundedCornerShape(14.dp),
        baseColor = TvColors.SurfaceElevated,
        focusedScale = 1.06f,
        glowSize = 10.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                key.digit,
                style = if (key.special) TvType.label else TvType.title,
                color = if (key.special) TvColors.TextSecondary else TvColors.TextPrimary,
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    key.letters,
                    style = TvType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                    color = TvColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun ResultsPanel(
    tracks: List<DemoTrack>,
    total: Int,
    group: TvFocusGroup,
    onBackToKeyboard: () -> Unit,
    onPlay: (DemoTrack) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("搜索结果", style = TvType.title, color = TvColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text(
                if (total > tracks.size) "${tracks.size} / $total 首 · QQ 音乐"
                else "${tracks.size} 首 · QQ 音乐",
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
            Spacer(Modifier.weight(1f))
            ActionChip(
                label = "继续输入",
                group = group,
                focusKey = "results:back",
                onClick = onBackToKeyboard,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (tracks.isEmpty()) {
            Text(
                "没有找到歌曲，换个关键词试试",
                style = TvType.body,
                color = TvColors.TextSecondary,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(tracks) { index, track ->
                    ResultRow(
                        track = track,
                        group = group,
                        focusKey = "results:$index",
                        onClick = { onPlay(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    track: DemoTrack,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.fillMaxWidth().height(68.dp),
        shape = RoundedCornerShape(14.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.62f),
        focusedScale = 1.03f,
        glowSize = 10.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkPlaceholder(track.colorSeed, Modifier.size(48.dp), corner = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = TvType.label,
                    color = TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${track.artist} · ${track.album}",
                    style = TvType.caption,
                    color = TvColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatDuration(track.durationMs),
                style = TvType.caption,
                color = TvColors.TextTertiary,
            )
            Spacer(Modifier.width(10.dp))
            TvIcon(Icons.Rounded.PlayArrow, Modifier.size(20.dp), tint = LocalTvAccent.current)
        }
    }
}

@Composable
private fun LoadingResults() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("搜索中", style = TvType.title, color = TvColors.TextPrimary)
            Spacer(Modifier.width(8.dp))
            Text("演示延迟 650ms", style = TvType.caption, color = TvColors.TextTertiary)
        }
        repeat(4) {
            SkeletonBox(Modifier.fillMaxWidth().height(68.dp), RoundedCornerShape(14.dp))
        }
    }
}

private fun digitForKey(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}
