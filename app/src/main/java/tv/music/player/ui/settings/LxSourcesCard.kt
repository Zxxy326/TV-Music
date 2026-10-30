package tv.music.player.ui.settings

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.lx.LxJsEngine
import tv.music.player.core.lx.LxScriptInfo
import tv.music.player.core.lx.LxSongInfo
import tv.music.player.core.lx.LxSourceInfo
import tv.music.player.core.lx.toLxMusicInfoMap
import tv.music.player.core.search.OnlineSearchApi
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.CapsuleHostState

@Composable
fun LxSourcesCard(
    lxEngine: LxJsEngine,
    group: TvFocusGroup,
    capsule: CapsuleHostState,
    refreshToken: Int = 0,
) {
    var scripts by remember { mutableStateOf<List<LxScriptInfo>>(emptyList()) }
    var sources by remember { mutableStateOf<Map<String, LxSourceInfo>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var reloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        lxEngine.ready()
        scripts = lxEngine.scriptInfos()
        sources = lxEngine.getSources()
        loading = false
    }

    LaunchedEffect(refreshToken) { refresh() }

    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.7f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("LX 音源引擎", style = TvType.label, color = TvColors.TextPrimary)
                Text(
                    if (loading) "正在加载脚本…"
                    else "已加载 ${scripts.size} 个脚本 · ${sources.size} 个源",
                    style = TvType.caption,
                    color = if (scripts.isEmpty()) TvColors.TextTertiary else TvColors.TextSecondary,
                )
                if (scripts.isNotEmpty()) {
                    Text(
                        scripts.joinToString("　") { "${it.name} ${it.version}" },
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (sources.isNotEmpty()) {
                    Text(
                        sources.keys.joinToString("  "),
                        style = TvType.caption,
                        color = TvColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                lxEngine.lastError?.let {
                    Text("错误：$it", style = TvType.caption, color = TvColors.Danger)
                }
            }
            Spacer(Modifier.width(16.dp))
            ActionChip(
                label = if (reloading) "重载中…" else "重新加载",
                group = group,
                focusKey = "lx:reload",
                onClick = {
                    if (reloading) return@ActionChip
                    reloading = true
                    scope.launch {
                        val ok = lxEngine.reload()
                        refresh()
                        reloading = false
                        capsule.show(
                            if (ok) "音源已重新加载 · ${sources.size} 个源"
                            else "音源加载失败：${lxEngine.lastError.orEmpty()}"
                        )
                    }
                },
            )
        }
    }
}

private data class SourceProbe(
    val source: String,
    val name: String,
    val searchMs: Long,
    val playMs: Long,
    val song: String,
    val error: String?,
)

@Composable
fun SourceTestCard(
    lxEngine: LxJsEngine,
    onlineSearch: OnlineSearchApi,
    group: TvFocusGroup,
    capsule: CapsuleHostState,
    quality: String,
    onImport: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var probes by remember { mutableStateOf<List<SourceProbe>>(emptyList()) }

    fun startTest() {
        if (running) return
        running = true
        probes = emptyList()
        progress = "准备测试…"
        scope.launch {
            val qqStart = SystemClock.elapsedRealtime()
            val qqResult = withTimeoutOrNull(15_000L) {
                runCatching { onlineSearch.search("晴天", 1, 1) }.getOrNull()
            }
            val qqMs = SystemClock.elapsedRealtime() - qqStart
            val track = qqResult?.list?.firstOrNull()
            val songInfo = track?.let {
                LxSongInfo(
                    id = it.songmid,
                    songmid = it.songmid,
                    hash = it.strMediaMid.ifBlank { it.songmid },
                    name = it.title,
                    singer = it.artist,
                    artistIds = it.artistIds,
                    albumName = it.album,
                    duration = it.durationMs / 1000L,
                    pic = it.coverUrl,
                    source = it.source,
                ).toLxMusicInfoMap()
            }
            val sources = lxEngine.getSources()
            val results = mutableListOf<SourceProbe>()
            sources.entries.forEachIndexed { index, entry ->
                val source = entry.key
                if (songInfo == null) {
                    results += SourceProbe(
                        source = source,
                        name = entry.value.name,
                        searchMs = qqMs,
                        playMs = 0L,
                        song = "",
                        error = "QQ 元数据失败",
                    )
                } else {
                    progress = "取流中 ${index + 1}/${sources.size} · $source"
                    val playStart = SystemClock.elapsedRealtime()
                    val url = withTimeoutOrNull(20_000L) {
                        runCatching {
                            lxEngine.getPlayUrl(source, songInfo, quality)
                        }.getOrNull()
                    }
                    results += SourceProbe(
                        source = source,
                        name = entry.value.name,
                        searchMs = qqMs,
                        playMs = SystemClock.elapsedRealtime() - playStart,
                        song = track.title,
                        error = if (url.isNullOrBlank()) "取流失败" else null,
                    )
                }
                probes = results.toList()
            }
            running = false
            progress = ""
            val ok = results.count { it.error == null }
            capsule.show("源测试完成：$ok/${results.size} 通过")
        }
    }

    TvSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.7f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("源测试", style = TvType.label, color = TvColors.TextPrimary)
                    Text(
                        when {
                            running -> progress
                            probes.isEmpty() -> "用 QQ 搜索的《晴天》逐个源取流；每个源最多 20 秒"
                            else -> "完成 ${probes.size} 个源 · 通过 ${probes.count { it.error == null }} 个"
                        },
                        style = TvType.caption,
                        color = TvColors.TextSecondary,
                    )
                }
                ActionChip(
                    label = "导入脚本",
                    group = group,
                    focusKey = "source:import",
                    onClick = onImport,
                )
                Spacer(Modifier.width(10.dp))
                ActionChip(
                    label = if (running) "测试中…" else "开始测试",
                    group = group,
                    focusKey = "source:test",
                    onClick = ::startTest,
                    primary = true,
                )
            }
            if (probes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                probes.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { probe -> ProbeChip(probe) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RowScope.ProbeChip(probe: SourceProbe) {
    val ok = probe.error == null
    val accent = LocalTvAccent.current
    val color = if (ok) accent else TvColors.Danger
    TvSurface(
        modifier = Modifier.weight(1f).height(56.dp),
        shape = RoundedCornerShape(12.dp),
        baseColor = color.copy(alpha = 0.10f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
        content = {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "${probe.source} · ${probe.name}",
                    style = TvType.caption,
                    color = TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (ok) "QQ ${probe.searchMs}ms · 取流 ${probe.playMs}ms · ${probe.song}"
                    else "失败 · ${probe.error}",
                    style = TvType.caption,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}
