package tv.music.player.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.music.player.core.focus.rememberTvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.playback.DemoPlaybackController
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType

@Composable
fun MiniPlayerBar(
    playback: DemoPlaybackController,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by playback.state.collectAsStateWithLifecycle()
    val accent = LocalTvAccent.current
    val group = rememberTvFocusGroup("mini")

    TvSurface(
        modifier = modifier.fillMaxWidth().height(68.dp),
        shape = RoundedCornerShape(20.dp),
        baseColor = TvColors.SurfaceElevated.copy(alpha = 0.97f),
        focusedScale = 1.0f,
        glowSize = 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvSurface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    baseColor = Color.Transparent,
                    focusedScale = 1.01f,
                    glowSize = 6.dp,
                    group = group,
                    focusKey = "mini:info",
                    onClick = onOpenPlayer,
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkPlaceholder(snapshot.track.colorSeed, Modifier.size(46.dp), corner = 10.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                snapshot.track.title,
                                style = TvType.label,
                                color = TvColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                snapshot.track.artist,
                                style = TvType.caption,
                                color = TvColors.TextSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                MiniButton(
                    icon = if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (snapshot.isPlaying) "暂停" else "播放",
                    group = group,
                    focusKey = "mini:toggle",
                    onClick = playback::toggle,
                )
                Spacer(Modifier.width(6.dp))
                MiniButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    group = group,
                    focusKey = "mini:next",
                    onClick = playback::next,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(snapshot.progress)
                    .height(3.dp)
                    .background(accent)
            )
        }
    }
}

@Composable
private fun MiniButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    group: tv.music.player.core.focus.TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
) {
    TvSurface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        baseColor = TvColors.SurfaceHigh.copy(alpha = 0.85f),
        focusedScale = 1.14f,
        glowSize = 10.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TvIcon(icon, Modifier.size(20.dp), tint = TvColors.TextPrimary, contentDescription = contentDescription)
        }
    }
}

@Composable
fun MiniPlayerSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier.height(20.dp))
}
