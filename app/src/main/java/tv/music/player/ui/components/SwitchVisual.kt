package tv.music.player.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors

@Composable
fun SwitchVisual(checked: Boolean) {
    val accent = LocalTvAccent.current
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(150),
        label = "switch-knob",
    )
    Box(
        Modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) accent.copy(alpha = 0.9f) else TvColors.SurfaceHigh)
    ) {
        Box(
            Modifier
                .offset(x = knobOffset, y = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) Color(0xFF07100E) else TvColors.TextTertiary)
        )
    }
}
