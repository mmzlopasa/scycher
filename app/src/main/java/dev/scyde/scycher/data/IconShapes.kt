package dev.scyde.scycher.data

import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

object IconShapes {

    fun path(shape: IconShape, w: Float, h: Float = w): Path {
        val p = Path()
        when (shape) {
            IconShape.Squircle -> superellipse(p, w, h, 5.0)
            IconShape.RoundedSquare -> p.addRoundRect(RectF(0f, 0f, w, h), w * 0.24f, h * 0.24f, Path.Direction.CW)
            IconShape.Circle, IconShape.System -> p.addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
            IconShape.Square -> p.addRoundRect(RectF(0f, 0f, w, h), w * 0.08f, h * 0.08f, Path.Direction.CW)
            IconShape.Teardrop -> {
                val r = w / 2f
                p.addRoundRect(
                    RectF(0f, 0f, w, h),
                    floatArrayOf(r, r, r, r, w * 0.12f, h * 0.12f, r, r),
                    Path.Direction.CW
                )
            }
            IconShape.Pebble -> {
                p.addRoundRect(
                    RectF(0f, 0f, w, h),
                    floatArrayOf(w * 0.5f, h * 0.42f, w * 0.38f, h * 0.5f, w * 0.5f, h * 0.42f, w * 0.38f, h * 0.5f),
                    Path.Direction.CW
                )
            }
            IconShape.Hexagon -> {
                // rounded hexagon via quads
                val cx = w / 2; val cy = h / 2; val r = w / 2
                val pts = (0 until 6).map { i ->
                    val a = Math.toRadians(60.0 * i - 90)
                    (cx + r * cos(a)).toFloat() to (cy + r * sin(a)).toFloat()
                }
                val k = 0.18f
                for (i in pts.indices) {
                    val (x0, y0) = pts[(i + 5) % 6]; val (x1, y1) = pts[i]; val (x2, y2) = pts[(i + 1) % 6]
                    val ax = x1 + (x0 - x1) * k; val ay = y1 + (y0 - y1) * k
                    val bx = x1 + (x2 - x1) * k; val by = y1 + (y2 - y1) * k
                    if (i == 0) p.moveTo(ax, ay) else p.lineTo(ax, ay)
                    p.quadTo(x1, y1, bx, by)
                }
                p.close()
            }
        }
        return p
    }

    private fun superellipse(p: Path, w: Float, h: Float, n: Double) {
        val a = w / 2.0; val b = h / 2.0
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = cos(t); val s = sin(t)
            val x = a + a * sign(c) * abs(c).pow(2.0 / n)
            val y = b + b * sign(s) * abs(s).pow(2.0 / n)
            if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
        }
        p.close()
    }

    /** Compose shape matching the bitmap mask, used for shadows / press highlight. */
    fun composeShape(shape: IconShape): Shape = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
            Outline.Generic(path(shape, size.width, size.height).asComposePath())
    }

    /** iOS-like continuous corner rounded rect for surfaces. */
    fun continuousRect(radius: Float): Shape = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val r = radius.coerceAtMost(minOf(size.width, size.height) / 2f)
            val p = Path()
            val w = size.width; val h = size.height
            // Smooth (G2-ish) corners using cubic curves with 1.28 extension like iOS
            val k = 1.28f * r
            val c = 0.55f * r
            val ex = k.coerceAtMost(w / 2); val ey = k.coerceAtMost(h / 2)
            p.moveTo(ex, 0f)
            p.lineTo(w - ex, 0f)
            p.cubicTo(w - ex + c, 0f, w, ey - c, w, ey)
            p.lineTo(w, h - ey)
            p.cubicTo(w, h - ey + c, w - ex + c, h, w - ex, h)
            p.lineTo(ex, h)
            p.cubicTo(ex - c, h, 0f, h - ey + c, 0f, h - ey)
            p.lineTo(0f, ey)
            p.cubicTo(0f, ey - c, ex - c, 0f, ex, 0f)
            p.close()
            return Outline.Generic(p.asComposePath())
        }
    }
}
