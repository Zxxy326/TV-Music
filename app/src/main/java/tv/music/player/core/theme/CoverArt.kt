package tv.music.player.core.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

class CoverArt(val image: ImageBitmap, val accent: Color)

@Composable
fun rememberCoverArt(): CoverArt = remember { buildCoverArt() }

private fun buildCoverArt(): CoverArt {
    val size = 512
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.shader = LinearGradient(
        0f, 0f, size.toFloat(), size.toFloat(),
        intArrayOf(0xFF24184E.toInt(), 0xFF4B2A7A.toInt(), 0xFF0E5F5C.toInt()),
        null,
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

    paint.shader = null
    paint.color = 0x33FFFFFF
    canvas.drawCircle(size * 0.23f, size * 0.22f, size * 0.30f, paint)
    paint.color = 0x22FFB25C
    canvas.drawCircle(size * 0.80f, size * 0.78f, size * 0.28f, paint)

    val cx = size * 0.62f
    val cy = size * 0.58f
    paint.style = Paint.Style.FILL
    paint.color = 0xFF0B0D12.toInt()
    canvas.drawCircle(cx, cy, size * 0.31f, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f
    for (i in 0..5) {
        paint.color = ColorUtils.setAlphaComponent(0xFF35D6C4.toInt(), 150 - i * 20)
        canvas.drawCircle(cx, cy, size * (0.285f - i * 0.045f), paint)
    }
    paint.style = Paint.Style.FILL
    paint.color = 0xFF35D6C4.toInt()
    canvas.drawCircle(cx, cy, size * 0.055f, paint)
    paint.color = 0xFF0B0D12.toInt()
    canvas.drawCircle(cx, cy, size * 0.016f, paint)

    val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
    val palette = Palette.from(scaled).maximumColorCount(12).generate()
    val swatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
    val accent = normalizeAccent(Color(swatch?.rgb ?: 0xFF35D6C4.toInt()))

    return CoverArt(bitmap.asImageBitmap(), accent)
}

private fun normalizeAccent(color: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.45f)
    hsl[2] = hsl[2].coerceIn(0.58f, 0.72f)
    return Color(ColorUtils.HSLToColor(hsl))
}
