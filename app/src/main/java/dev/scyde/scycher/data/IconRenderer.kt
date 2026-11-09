package dev.scyde.scycher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.ColorUtils

/** Renders any app icon into a crisp bitmap with Scycher's shape & style. */
object IconRenderer {

    // one Paint per thread: icons render in parallel from several IO threads
    private val paints = ThreadLocal.withInitial { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    private val bgPaint: Paint get() = paints.get()!!

    fun render(src: Drawable, size: Int, cfg: IconConfig): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()
        val shape = cfg.shape
        val adaptive = src as? AdaptiveIconDrawable

        if (shape == IconShape.System && cfg.style == IconStyle.Default) {
            src.setBounds(0, 0, size, size)
            src.draw(c)
            return bmp
        }

        val path = IconShapes.path(if (shape == IconShape.System) IconShape.Circle else shape, s, s)
        val save = c.save()
        c.clipPath(path)

        when (cfg.style) {
            IconStyle.Default -> {
                if (adaptive != null) {
                    drawLayer(c, adaptive.background, size)
                    drawLayer(c, adaptive.foreground, size)
                } else if (cfg.reshapeLegacy) {
                    bgPaint.shader = null
                    bgPaint.color = cfg.legacyBackground.toInt()
                    c.drawRect(0f, 0f, s, s, bgPaint)
                    drawInset(c, src, size, 0.70f)
                } else {
                    c.restoreToCount(save)
                    src.setBounds(0, 0, size, size); src.draw(c)
                    return bmp
                }
            }
            IconStyle.Dark -> {
                bgPaint.shader = LinearGradient(0f, 0f, 0f, s, 0xFF2C2C30.toInt(), 0xFF0E0E10.toInt(), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, s, s, bgPaint)
                bgPaint.shader = null
                if (adaptive != null) {
                    val mono = monochrome(adaptive)
                    if (mono != null) {
                        mono.colorFilter = PorterDuffColorFilter(0xFFF2F2F7.toInt(), PorterDuff.Mode.SRC_IN)
                        drawLayer(c, mono, size)
                        mono.colorFilter = null
                    } else drawLayer(c, adaptive.foreground, size)
                } else drawInset(c, src, size, 0.66f)
            }
            IconStyle.Clear -> {
                // iOS 26 "Clear": no plate (the launcher puts glass behind), frosted white glyph
                val mono = adaptive?.let { monochrome(it) }
                if (mono != null) {
                    mono.colorFilter = PorterDuffColorFilter(0xF0FFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
                    drawLayer(c, mono, size)
                    mono.colorFilter = null
                } else {
                    val fg = adaptive?.foreground ?: src
                    val m = ColorMatrix(floatArrayOf(
                        0.25f, 0.5f, 0.1f, 0f, 120f,
                        0.25f, 0.5f, 0.1f, 0f, 120f,
                        0.25f, 0.5f, 0.1f, 0f, 120f,
                        0f, 0f, 0f, 0.92f, 0f,
                    ))
                    fg.colorFilter = ColorMatrixColorFilter(m)
                    if (adaptive != null) drawLayer(c, fg, size) else drawInset(c, fg, size, 0.62f)
                    fg.colorFilter = null
                }
            }
            IconStyle.Auto -> {
                if (adaptive != null) {
                    drawLayer(c, adaptive.background, size)
                    drawLayer(c, adaptive.foreground, size)
                } else {
                    bgPaint.shader = null
                    bgPaint.color = cfg.legacyBackground.toInt()
                    c.drawRect(0f, 0f, s, s, bgPaint)
                    drawInset(c, src, size, 0.70f)
                }
            }
            IconStyle.Tinted, IconStyle.Monochrome -> {
                val tint = cfg.tintColor.toInt()
                val bgTop = if (cfg.style == IconStyle.Tinted) ColorUtils.blendARGB(0xFF151517.toInt(), tint, 0.18f) else 0xFF1C1C1E.toInt()
                bgPaint.shader = LinearGradient(0f, 0f, 0f, s, bgTop, 0xFF0A0A0C.toInt(), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, s, s, bgPaint)
                bgPaint.shader = null
                val fgColor = if (cfg.style == IconStyle.Tinted) tint else 0xFFEDEDF2.toInt()
                val mono = adaptive?.let { monochrome(it) }
                if (mono != null) {
                    mono.colorFilter = PorterDuffColorFilter(fgColor, PorterDuff.Mode.SRC_IN)
                    drawLayer(c, mono, size)
                    mono.colorFilter = null
                } else {
                    // luminance -> tint
                    val fg = adaptive?.foreground ?: src
                    val r = ((fgColor shr 16) and 0xFF) / 255f
                    val g = ((fgColor shr 8) and 0xFF) / 255f
                    val b = (fgColor and 0xFF) / 255f
                    val m = ColorMatrix(floatArrayOf(
                        0.30f * r, 0.59f * r, 0.11f * r, 0f, 40f * r,
                        0.30f * g, 0.59f * g, 0.11f * g, 0f, 40f * g,
                        0.30f * b, 0.59f * b, 0.11f * b, 0f, 40f * b,
                        0f, 0f, 0f, 1f, 0f,
                    ))
                    fg.colorFilter = ColorMatrixColorFilter(m)
                    if (adaptive != null) drawLayer(c, fg, size) else drawInset(c, fg, size, 0.66f)
                    fg.colorFilter = null
                }
            }
        }

        if (cfg.glassRim) {
            // subtle top highlight that gives a glassy rim
            bgPaint.shader = LinearGradient(0f, 0f, 0f, s * 0.5f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, s, s * 0.5f, bgPaint)
            bgPaint.shader = null
        }
        c.restoreToCount(save)
        return bmp
    }

    /** A user picked image as icon: center-cropped into the current shape. */
    fun renderImage(src: Bitmap, size: Int, cfg: IconConfig): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()
        val shape = if (cfg.shape == IconShape.System) IconShape.Squircle else cfg.shape
        c.clipPath(IconShapes.path(shape, s, s))
        val scale = maxOf(s / src.width, s / src.height)
        val w = src.width * scale; val h = src.height * scale
        c.drawBitmap(src, null, android.graphics.RectF((s - w) / 2, (s - h) / 2, (s + w) / 2, (s + h) / 2), bgPaint.apply { shader = null; color = 0xFFFFFFFF.toInt() })
        return bmp
    }

    private fun monochrome(a: AdaptiveIconDrawable): Drawable? =
        if (Build.VERSION.SDK_INT >= 33) a.monochrome?.mutate() else null

    /** Adaptive layers are 108dp with a 72dp safe zone -> expand by 25% on each side. */
    private fun drawLayer(c: Canvas, d: Drawable?, size: Int) {
        d ?: return
        val inset = size / 4
        d.setBounds(-inset, -inset, size + inset, size + inset)
        d.draw(c)
    }

    private fun drawInset(c: Canvas, d: Drawable, size: Int, scale: Float) {
        val iw = (size * scale).toInt()
        val off = (size - iw) / 2
        d.setBounds(off, off, off + iw, off + iw)
        d.draw(c)
    }
}
