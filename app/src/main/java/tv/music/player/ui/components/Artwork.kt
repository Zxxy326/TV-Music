package tv.music.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun artworkColors(seed: Int): Pair<Color, Color> {
    val hue = ((seed * 37) % 360 + 360) % 360
    val second = (hue + 46) % 360
    return Color.hsv(hue.toFloat(), 0.46f, 0.52f) to Color.hsv(second.toFloat(), 0.62f, 0.2f)
}

@Composable
fun ArtworkPlaceholder(
    seed: Int,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    showNote: Boolean = true,
) {
    val (top, bottom) = remember(seed) { artworkColors(seed) }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(top, bottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.28f, size.height * 0.22f),
                    radius = unit * 0.7f,
                ),
                radius = unit * 0.7f,
                center = Offset(size.width * 0.28f, size.height * 0.22f),
            )
            val ringCenter = Offset(size.width * 0.72f, size.height * 0.74f)
            for (i in 0..3) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f - i * 0.02f),
                    radius = unit * (0.34f - i * 0.055f),
                    center = ringCenter,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.16f),
                radius = unit * 0.03f,
                center = ringCenter,
            )
        }
        if (showNote) {
            TvIcon(
                icon = Icons.Rounded.MusicNote,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(0.3f),
                tint = Color.White.copy(alpha = 0.86f),
            )
        }
    }
}
