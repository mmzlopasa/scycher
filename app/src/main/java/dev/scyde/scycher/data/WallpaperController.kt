package dev.scyde.scycher.data

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class WallpaperState(
    /** Full resolution wallpaper drawn by Scycher (null when the system draws it). */
    val image: ImageBitmap? = null,
    /** Small, heavily blurred copy used for glass surfaces. */
    val blurred: ImageBitmap? = null,
    /** True if the system wallpaper is very bright (dark text/icons preferred). */
    val light: Boolean = false,
    val token: Int = 0,
)

/**
 * Real glass needs to know what's behind the surface. Scycher can either draw its own wallpaper
 * (perfect blur everywhere) or try to read the system wallpaper (works on many ROMs, falls back to
 * a frosted tint where reading is not allowed).
 */
class WallpaperController(private val context: Context, private val store: ConfigStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val file = File(context.filesDir, "wallpaper.jpg")
    private val _state = MutableStateFlow(WallpaperState())
    val state: StateFlow<WallpaperState> = _state
    private val wm = WallpaperManager.getInstance(context)

    init {
        store.config.map { it.look.wallpaperMode to it.look.glassBlurRadius }.distinctUntilChanged()
            .onEach { reload() }.launchIn(scope)
        if (Build.VERSION.SDK_INT >= 27) {
            try {
                wm.addOnColorsChangedListener({ _, _ -> reload() }, Handler(Looper.getMainLooper()))
            } catch (_: Throwable) {}
        }
    }

    fun reload() = scope.launch {
        val mode = store.value.look.wallpaperMode
        val radius = store.value.look.glassBlurRadius
        val token = _state.value.token + 1
        val light = isLightWallpaper()
        if (mode == WallpaperMode.Scycher && file.exists()) {
            val bmp = decodeScaled(file) ?: return@launch
            _state.value = WallpaperState(bmp.asImageBitmap(), blur(bmp, radius)?.asImageBitmap(), light, token)
        } else {
            val sys = readSystemWallpaper()
            _state.value = WallpaperState(null, sys?.let { blur(it, radius)?.asImageBitmap() }, light, token)
            sys?.recycle()
        }
    }

    private fun isLightWallpaper(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 27) {
            val c = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            c != null && (c.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        } else false
    } catch (_: Throwable) { false }

    @SuppressLint("MissingPermission")
    private fun readSystemWallpaper(): Bitmap? = try {
        val d: Drawable? = wm.peekDrawable() ?: wm.drawable
        d?.let { toBitmap(it, 720) }
    } catch (t: Throwable) {
        null // Not allowed on newer Android without special access – glass falls back to frosted tint.
    }

    private fun toBitmap(d: Drawable, maxSide: Int): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null) {
            val b = d.bitmap
            val s = maxSide.toFloat() / max(b.width, b.height)
            return if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).roundToInt(), (b.height * s).roundToInt(), true) else b.copy(Bitmap.Config.ARGB_8888, false)
        }
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 1080
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 1920
        val s = minOf(1f, maxSide.toFloat() / max(w, h))
        val b = Bitmap.createBitmap((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(b); d.setBounds(0, 0, b.width, b.height); d.draw(c)
        return b
    }

    private fun decodeScaled(f: File): Bitmap? = try {
        val dm = context.resources.displayMetrics
        val target = max(dm.widthPixels, dm.heightPixels) * 1.15f
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= target) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (t: Throwable) { Log.w("Scycher", "wallpaper decode", t); null }

    /** Sets a new Scycher wallpaper from a picked image. */
    suspend fun setFromUri(uri: Uri, alsoSystem: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            if (alsoSystem) {
                try {
                    decodeScaled(file)?.let { b ->
                        if (Build.VERSION.SDK_INT >= 24) wm.setBitmap(b, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                        else wm.setBitmap(b)
                    }
                } catch (t: Throwable) { Log.w("Scycher", "set system wallpaper", t) }
            }
            store.update { it.copy(look = it.look.copy(wallpaperMode = WallpaperMode.Scycher)) }
            reload(); true
        } catch (t: Throwable) { Log.w("Scycher", "wallpaper", t); false }
    }

    fun hasCustom() = file.exists()

    companion object {
        /** Fast box blur on a down-scaled copy (3 passes ≈ gaussian). */
        fun blur(src: Bitmap, radius: Int): Bitmap? {
            if (radius <= 0) return src.copy(Bitmap.Config.ARGB_8888, false)
            val scale = 160f / max(src.width, src.height)
            val w = (src.width * scale).roundToInt().coerceAtLeast(4)
            val h = (src.height * scale).roundToInt().coerceAtLeast(4)
            val small = Bitmap.createScaledBitmap(src, w, h, true)
            val px = IntArray(w * h)
            small.getPixels(px, 0, w, 0, 0, w, h)
            val r = (radius * scale * 2.2f).roundToInt().coerceIn(1, 24)
            val tmp = IntArray(w * h)
            repeat(3) {
                boxH(px, tmp, w, h, r)
                boxV(tmp, px, w, h, r)
            }
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(px, 0, w, 0, 0, w, h)
            if (small != src) small.recycle()
            return out
        }

        private fun boxH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
            val div = 2 * r + 1
            for (y in 0 until h) {
                var ra = 0; var rr = 0; var rg = 0; var rb = 0
                val row = y * w
                for (i in -r..r) {
                    val p = src[row + i.coerceIn(0, w - 1)]
                    ra += p ushr 24; rr += (p shr 16) and 0xFF; rg += (p shr 8) and 0xFF; rb += p and 0xFF
                }
                for (x in 0 until w) {
                    dst[row + x] = ((ra / div) shl 24) or ((rr / div) shl 16) or ((rg / div) shl 8) or (rb / div)
                    val pOut = src[row + (x - r).coerceIn(0, w - 1)]
                    val pIn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                    ra += (pIn ushr 24) - (pOut ushr 24)
                    rr += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                    rg += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                    rb += (pIn and 0xFF) - (pOut and 0xFF)
                }
            }
        }

        private fun boxV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
            val div = 2 * r + 1
            for (x in 0 until w) {
                var ra = 0; var rr = 0; var rg = 0; var rb = 0
                for (i in -r..r) {
                    val p = src[i.coerceIn(0, h - 1) * w + x]
                    ra += p ushr 24; rr += (p shr 16) and 0xFF; rg += (p shr 8) and 0xFF; rb += p and 0xFF
                }
                for (y in 0 until h) {
                    dst[y * w + x] = ((ra / div) shl 24) or ((rr / div) shl 16) or ((rg / div) shl 8) or (rb / div)
                    val pOut = src[(y - r).coerceIn(0, h - 1) * w + x]
                    val pIn = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                    ra += (pIn ushr 24) - (pOut ushr 24)
                    rr += ((pIn shr 16) and 0xFF) - ((pOut shr 16) and 0xFF)
                    rg += ((pIn shr 8) and 0xFF) - ((pOut shr 8) and 0xFF)
                    rb += (pIn and 0xFF) - (pOut and 0xFF)
                }
            }
        }
    }
}
