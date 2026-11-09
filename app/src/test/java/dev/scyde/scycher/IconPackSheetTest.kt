package dev.scyde.scycher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import dev.scyde.scycher.data.GlyphPack
import dev.scyde.scycher.data.IconConfig
import dev.scyde.scycher.data.IconRenderer
import dev.scyde.scycher.data.IconStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every Scycher glyph in three appearances on a pack wallpaper – a contact sheet for review. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconPackSheetTest {
    @Test
    fun sheet() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val glyphs = GlyphPack.Glyph.entries
        val cols = 9
        val icon = 150
        val gap = 46
        val rowsPer = (glyphs.size + cols - 1) / cols
        val styles = listOf(IconStyle.Default, IconStyle.Dark, IconStyle.Tinted)
        val w = cols * (icon + gap) + gap
        val h = styles.size * (rowsPer * (icon + gap) + gap)
        for (variant in listOf("dark", "light")) {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            ctx.assets.open("wallpapers/bloom_$variant.webp").use { BitmapFactory.decodeStream(it) }?.let {
                c.drawBitmap(it, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            styles.forEachIndexed { si, style ->
                val cfg = IconConfig(style = style, tintColor = 0xFFB9A7FF)
                glyphs.forEachIndexed { i, g ->
                    val bmp = IconRenderer.render(GlyphPack.build(g), icon, cfg)
                    val x = gap + (i % cols) * (icon + gap)
                    val y = si * (rowsPer * (icon + gap) + gap) + gap + (i / cols) * (icon + gap)
                    c.drawBitmap(bmp, x.toFloat(), y.toFloat(), null)
                }
            }
            val dir = File(System.getProperty("user.dir"), "build/shots").apply { mkdirs() }
            File(dir, "30_icon_pack_$variant.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
