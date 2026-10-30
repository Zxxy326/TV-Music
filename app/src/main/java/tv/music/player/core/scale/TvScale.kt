package tv.music.player.core.scale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

val LocalTvScale = staticCompositionLocalOf { 1f }

private const val BASE_WIDTH_DP = 960f
private const val BASE_HEIGHT_DP = 540f

/**
 * 以 960×540dp（1080p@2x）为设计基准：
 * - 屏幕 dp 宽偏离基准时按比例缩放，保证同一套 dp 布局在不同密度电视上观感一致
 * - 4K 面板额外 1.15× 视觉补偿（远距离观看字号更舒服）
 */
@Composable
fun TvScaleProvider(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val widthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val widthRatio = configuration.screenWidthDp / BASE_WIDTH_DP
    val heightRatio = configuration.screenHeightDp / BASE_HEIGHT_DP
    val dpScale = minOf(widthRatio, heightRatio).coerceIn(0.6f, 1.3f)
    val uhdBoost = if (widthPx >= 2500f) 1.15f else 1f
    val total = dpScale * uhdBoost

    CompositionLocalProvider(
        LocalDensity provides Density(density.density * total, density.fontScale),
        LocalTvScale provides total,
    ) {
        content()
    }
}
