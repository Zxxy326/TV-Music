package tv.music.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import tv.music.player.core.theme.TvColors

@Composable
fun TvIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = TvColors.TextSecondary,
    contentDescription: String? = null,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}
