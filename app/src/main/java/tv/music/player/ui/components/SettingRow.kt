package tv.music.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.music.player.core.focus.TvFocusGroup
import tv.music.player.core.focus.TvSurface
import tv.music.player.core.theme.TvColors
import tv.music.player.core.theme.TvType

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    group: TvFocusGroup,
    focusKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    toggle: Boolean? = null,
    showChevron: Boolean = false,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
) {
    TvSurface(
        modifier = modifier.fillMaxWidth().height(74.dp),
        shape = RoundedCornerShape(16.dp),
        baseColor = TvColors.Surface.copy(alpha = 0.62f),
        focusedScale = 1.02f,
        glowSize = 12.dp,
        group = group,
        focusKey = focusKey,
        onClick = onClick,
        onKeyEvent = onKeyEvent,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = TvType.title,
                    color = TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = TvType.caption,
                        color = TvColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            if (toggle != null) {
                SwitchVisual(toggle)
            } else if (valueText != null) {
                Text(valueText, style = TvType.label, color = TvColors.TextSecondary)
            }
            if (showChevron) {
                Spacer(Modifier.width(8.dp))
                TvIcon(Icons.Rounded.ChevronRight, Modifier.size(20.dp), tint = TvColors.TextTertiary)
            }
        }
    }
}

@Composable
fun InfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(TvColors.Surface.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TvType.label, color = TvColors.TextSecondary)
            if (subtitle != null) {
                Text(subtitle, style = TvType.caption, color = TvColors.TextTertiary)
            }
        }
        Text(value, style = TvType.label, color = TvColors.TextPrimary, maxLines = 1)
    }
}
