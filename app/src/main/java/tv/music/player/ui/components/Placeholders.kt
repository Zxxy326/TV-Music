package tv.music.player.ui.components

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.music.player.core.theme.LocalAnimationsEnabled
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val animations = LocalAnimationsEnabled.current
    if (animations) {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val offset by transition.animateFloat(
            initialValue = -420f,
            targetValue = 1200f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
            label = "skeleton-sweep",
        )
        Box(
            modifier.background(
                Brush.linearGradient(
                    colors = listOf(TvColors.Surface, TvColors.SurfaceHigh, TvColors.Surface),
                    start = Offset(offset, 0f),
                    end = Offset(offset + 380f, 0f),
                ),
                shape,
            )
        )
    } else {
        Box(modifier.background(TvColors.Surface, shape))
    }
}

@Composable
fun SkeletonPoster(modifier: Modifier = Modifier) {
    Column(modifier.width(150.dp)) {
        SkeletonBox(Modifier.fillMaxWidth().height(134.dp), RoundedCornerShape(16.dp))
        Spacer(Modifier.height(10.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.8f).height(16.dp), RoundedCornerShape(6.dp))
        Spacer(Modifier.height(6.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.55f).height(12.dp), RoundedCornerShape(6.dp))
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .background(TvColors.SurfaceElevated, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            TvIcon(Icons.Rounded.Info, Modifier.size(26.dp), tint = TvColors.TextTertiary)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = TvType.title, color = TvColors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = TvType.caption, color = TvColors.TextSecondary)
        if (trailing != null) {
            Spacer(Modifier.height(16.dp))
            trailing()
        }
    }
}

@Composable
fun KeyHintBar(
    hints: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { (key, action) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Keycap(key)
                Spacer(Modifier.width(7.dp))
                Text(action, style = TvType.caption, color = TvColors.TextTertiary)
            }
        }
    }
}

@Composable
fun ProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 4.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(TvColors.Divider, RoundedCornerShape(50))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(tv.music.player.core.theme.LocalTvAccent.current, RoundedCornerShape(50))
        )
    }
}
