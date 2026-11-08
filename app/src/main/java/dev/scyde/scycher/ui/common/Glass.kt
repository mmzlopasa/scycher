package dev.scyde.scycher.ui.common

import android.graphics.Typeface
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.scyde.scycher.data.FontChoice
import dev.scyde.scycher.data.LauncherConfig
import dev.scyde.scycher.data.SurfaceStyle
import dev.scyde.scycher.data.WallpaperState
import kotlin.math.max
import kotlin.math.roundToInt

/** Where (in root coordinates) the wallpaper is currently drawn – shared by every glass surface. */
@Stable
class WallpaperGeometry {
    var rootSize by mutableStateOf(Size.Zero)
    /** Horizontal parallax offset in px (≤ 0). */
    var parallax by mutableFloatStateOf(0f)
    var parallaxRange by mutableFloatStateOf(0f)

    fun dst(imgW: Int, imgH: Int): Pair<Offset, Size> {
        val rw = rootSize.width; val rh = rootSize.height
        if (rw <= 0f || rh <= 0f) return Offset.Zero to Size.Zero
        val extra = parallaxRange
        val scale = max((rw + extra) / imgW, rh / imgH)
        val w = imgW * scale; val h = imgH * scale
        val left = (rw - w) / 2f + (if (extra > 0f) (extra / 2f) + parallax else 0f)
        val top = (rh - h) / 2f
        return Offset(left, top) to Size(w, h)
    }
}

val LocalWallpaper = staticCompositionLocalOf { WallpaperState() }
val LocalWallpaperGeometry = staticCompositionLocalOf { WallpaperGeometry() }
val LocalConfig = staticCompositionLocalOf { LauncherConfig() }

fun Long.toColor() = Color(this.toInt())
fun Color.toLong(): Long = (((alpha * 255).roundToInt() shl 24) or ((red * 255).roundToInt() shl 16) or
    ((green * 255).roundToInt() shl 8) or (blue * 255).roundToInt()).toLong() and 0xFFFFFFFFL

fun fontFamilyOf(choice: FontChoice): FontFamily =
    choice.family?.let { FontFamily(Typeface.create(it, Typeface.NORMAL)) } ?: FontFamily.Default

@Stable
private class PosHolder { var pos by mutableStateOf(Offset.Zero) }

/**
 * iOS-style material: blurred, saturated wallpaper behind the surface + tint + sheen + hairline border.
 */
fun Modifier.glass(
    shape: Shape,
    style: SurfaceStyle,
    tint: Color,
    opacity: Float,
    border: Boolean = true,
    borderOpacity: Float = 0.22f,
): Modifier = composed {
    val wp = LocalWallpaper.current
    val geo = LocalWallpaperGeometry.current
    val cfg = LocalConfig.current
    val holder = remember { PosHolder() }
    val sat = cfg.look.glassSaturation
    val filter = remember(sat) { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(sat) }) }
    var m = this
        .onGloballyPositioned { holder.pos = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            drawMaterial(style, tint, opacity, wp, geo, holder.pos, filter)
        }
    if (border && style != SurfaceStyle.Clear) {
        m = m.border(
            0.8.dp,
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = borderOpacity), Color.White.copy(alpha = borderOpacity * 0.25f))
            ),
            shape
        )
    }
    m
}

private fun DrawScope.drawMaterial(
    style: SurfaceStyle,
    tint: Color,
    opacity: Float,
    wp: WallpaperState,
    geo: WallpaperGeometry,
    pos: Offset,
    filter: ColorFilter,
) {
    val blurred = wp.blurred
    when (style) {
        SurfaceStyle.Glass, SurfaceStyle.Frosted -> {
            if (blurred != null) {
                val (o, s) = geo.dst(blurred.width, blurred.height)
                drawImage(
                    blurred,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(blurred.width, blurred.height),
                    dstOffset = IntOffset((o.x - pos.x).roundToInt(), (o.y - pos.y).roundToInt()),
                    dstSize = IntSize(s.width.roundToInt(), s.height.roundToInt()),
                    colorFilter = filter,
                    filterQuality = FilterQuality.Low,
                )
                drawRect(tint.copy(alpha = opacity))
                if (style == SurfaceStyle.Frosted) drawRect(Color.White.copy(alpha = 0.10f))
            } else {
                // No access to wallpaper pixels – emulate frosted glass.
                drawRect(tint.copy(alpha = (opacity + 0.18f).coerceAtMost(0.85f)))
                drawRect(Color.Black.copy(alpha = if (style == SurfaceStyle.Frosted) 0.06f else 0.16f))
            }
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.10f), 0.45f to Color.White.copy(alpha = 0.0f),
                )
            )
        }
        SurfaceStyle.Solid -> drawRect(tint.copy(alpha = (0.75f + opacity).coerceAtMost(1f)))
        SurfaceStyle.Tinted -> {
            drawRect(tint.copy(alpha = opacity))
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)))
        }
        SurfaceStyle.Clear -> {}
    }
}

/** Draws the wallpaper (Scycher mode) or nothing (system wallpaper shows through the window). */
fun DrawScope.drawWallpaper(wp: WallpaperState, geo: WallpaperGeometry, dim: Float) {
    val img = wp.image
    if (img != null) {
        val (o, s) = geo.dst(img.width, img.height)
        drawImage(
            img, IntOffset.Zero, IntSize(img.width, img.height),
            IntOffset(o.x.roundToInt(), o.y.roundToInt()), IntSize(s.width.roundToInt(), s.height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
    if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
}

/** Full-screen blurred backdrop (App Library, folders). */
fun DrawScope.drawBlurBackdrop(wp: WallpaperState, geo: WallpaperGeometry, alpha: Float, dim: Float, filter: ColorFilter?) {
    val b = wp.blurred
    if (b != null) {
        val (o, s) = geo.dst(b.width, b.height)
        drawImage(
            b, IntOffset.Zero, IntSize(b.width, b.height),
            IntOffset(o.x.roundToInt(), o.y.roundToInt()), IntSize(s.width.roundToInt(), s.height.roundToInt()),
            alpha = alpha, colorFilter = filter, filterQuality = FilterQuality.Low,
        )
        drawRect(Color.Black.copy(alpha = dim * alpha))
    } else {
        drawRect(Color(0xFF0C0C10).copy(alpha = (0.55f + dim * 0.4f).coerceAtMost(0.92f) * alpha))
    }
}

@Composable
fun rememberSaturationFilter(): ColorFilter {
    val sat = LocalConfig.current.look.glassSaturation
    return remember(sat) { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(sat) }) }
}
