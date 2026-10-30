package tv.music.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors

@Composable
fun TvBackdrop(modifier: Modifier = Modifier) {
    val accent = LocalTvAccent.current
    Canvas(modifier.fillMaxSize()) {
        drawRect(TvColors.Background)
        drawRect(
            Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.02f),
                radius = size.width * 0.55f,
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(size.width * 0.04f, size.height * 0.96f),
                radius = size.width * 0.48f,
            )
        )
    }
}
