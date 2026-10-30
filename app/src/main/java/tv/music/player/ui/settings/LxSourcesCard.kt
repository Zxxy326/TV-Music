package tv.music.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.lx.LxJsEngine
import tv.music.player.core.lx.LxScriptInfo
import tv.music.player.core.lx.LxSourceInfo
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType
import tv.music.player.ui.components.ActionChip
import tv.music.player.ui.components.CapsuleHostState

@Composable
fun LxSourcesCard(
    lxEngine: LxJsEngine,
    group: TvFocusGroup,
    capsule: CapsuleHostState,
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

    LaunchedEffect(Unit) { refresh() }

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
