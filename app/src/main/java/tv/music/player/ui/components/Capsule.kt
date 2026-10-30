package tv.music.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.music.player.core.theme.LocalTvAccent
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType

class CapsuleHostState(private val scope: CoroutineScope) {

    var message by mutableStateOf<String?>(null)
        private set

    var visible by mutableStateOf(false)
        private set

    private var hideJob: Job? = null

    fun show(text: String, durationMs: Long = 2_400L) {
        hideJob?.cancel()
        message = text
        visible = true
        hideJob = scope.launch {
            delay(durationMs)
            visible = false
        }
    }
}

@Composable
fun rememberCapsuleHostState(): CapsuleHostState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CapsuleHostState(scope) }
}

val LocalCapsuleHost = staticCompositionLocalOf<CapsuleHostState> {
    error("CapsuleHostState 未提供")
}

@Composable
fun CapsuleHost(modifier: Modifier = Modifier) {
    val host = LocalCapsuleHost.current
    val accent = LocalTvAccent.current
    AnimatedVisibility(
        visible = host.visible,
        modifier = modifier,
        enter = fadeIn(tween(140)) + slideInVertically(tween(180)) { -it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { -it },
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .clip(RoundedCornerShape(50))
                .background(TvColors.SurfaceElevated)
                .border(1.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(50))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = host.message.orEmpty(),
                style = TvType.body,
                color = TvColors.TextPrimary,
                maxLines = 2,
            )
        }
    }
}
