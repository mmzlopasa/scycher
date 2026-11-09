package dev.scyde.scycher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Scycher Glyphs" – Scycher's own icon pack in an iOS spirit: one clean glyph on a vivid gradient
 * tile. Apps are matched by *role* (browser, camera, messages…), never by brand, so no third-party
 * logo is redrawn. Icons are returned as AdaptiveIconDrawables so every Scycher shape and style
 * (Dark, Clear, Tinted, Automatic) works on them.
 */
object GlyphPack {
    const val ID = "scycher:glyphs"

    enum class Glyph(
        val vector: ImageVector?,
        val top: Long,
        val bottom: Long,
        val glyph: Long = 0xFFFFFFFF,
        val glyph2: Long? = null,
    ) {
        Browser(Icons.Rounded.Explore, 0xFF4AB3FF, 0xFF1462E6),
        Messages(Icons.Rounded.Chat, 0xFF6BE27E, 0xFF20B23B),
        Chat(Icons.Rounded.Forum, 0xFF62D0FF, 0xFF1F9BE6),
        Mail(Icons.Rounded.Mail, 0xFF5AC8FA, 0xFF1A73F0),
        Phone(Icons.Rounded.Call, 0xFF6BE07B, 0xFF22B33A),
        Camera(Icons.Rounded.PhotoCamera, 0xFFE9E9EE, 0xFF9B9BA1, 0xFF2C2C2E),
        Photos(Icons.Rounded.LocalFlorist, 0xFFFFFFFF, 0xFFF0F0F5, 0xFFFF9F0A, 0xFFFF2D78),
        Music(Icons.Rounded.MusicNote, 0xFFFF6482, 0xFFF5203D),
        Video(Icons.Rounded.PlayArrow, 0xFF48484A, 0xFF141416),
        Tv(Icons.Rounded.Tv, 0xFF3A3A3C, 0xFF0E0E10, 0xFFFF453A),
        Maps(Icons.Rounded.Map, 0xFF8EE6A6, 0xFF2DBE5B),
        Calendar(null, 0xFFFFFFFF, 0xFFF2F2F7),
        Clock(null, 0xFF1C1C1E, 0xFF000000),
        Calculator(Icons.Rounded.Calculate, 0xFF4A4A4C, 0xFF1C1C1E, 0xFFFF9F0A),
        Notes(Icons.Rounded.StickyNote2, 0xFFFFFFFF, 0xFFF2F2F7, 0xFFFFD60A, 0xFFFFB300),
        Files(Icons.Rounded.Folder, 0xFF6FC3FF, 0xFF0A7CF5),
        Docs(Icons.Rounded.Description, 0xFF5AA8FF, 0xFF2A5FE0),
        Settings(Icons.Rounded.Settings, 0xFFCFCFD4, 0xFF8E8E93, 0xFF3A3A3C),
        Store(Icons.Rounded.ShoppingBag, 0xFF5AC8FA, 0xFF0A6CF0),
        Weather(Icons.Rounded.WbSunny, 0xFF5AC8FA, 0xFF1E6FE0, 0xFFFFE066, 0xFFFFC300),
        Contacts(Icons.Rounded.Person, 0xFFD8D8DD, 0xFFA6A6AB),
        Recorder(Icons.Rounded.Mic, 0xFF3A3A3C, 0xFF000000, 0xFFFF453A),
        Health(Icons.Rounded.Favorite, 0xFFFFFFFF, 0xFFF2F2F7, 0xFFFF5A7A, 0xFFFF2D55),
        Wallet(Icons.Rounded.AccountBalanceWallet, 0xFF2C2C2E, 0xFF000000, 0xFF5AC8FA, 0xFFBF5AF2),
        Books(Icons.AutoMirrored.Rounded.MenuBook, 0xFFFFB340, 0xFFFF7A00),
        Podcasts(Icons.Rounded.Podcasts, 0xFFD58BFF, 0xFF8E44EC),
        Translate(Icons.Rounded.Translate, 0xFF4AA8FF, 0xFF0A64D8),
        Fitness(Icons.Rounded.DirectionsRun, 0xFF2C2C2E, 0xFF000000, 0xFFB6FF3B, 0xFF4CD964),
        Security(Icons.Rounded.Shield, 0xFF6BE07B, 0xFF1FAF5A),
        Downloads(Icons.Rounded.Download, 0xFF6FC3FF, 0xFF0A84FF),
        Compass(Icons.Rounded.Navigation, 0xFF2C2C2E, 0xFF000000, 0xFFFFFFFF, 0xFFFF453A),
        Themes(Icons.Rounded.Palette, 0xFFFF8AD8, 0xFFB04CFF),
        ScreenRecord(Icons.Rounded.Videocam, 0xFFFF7A7A, 0xFFE5383B),
        Scanner(Icons.Rounded.QrCodeScanner, 0xFF4AE3D0, 0xFF0FB5A2),
        Cleaner(Icons.Rounded.CleaningServices, 0xFFFFD36B, 0xFFFF9F0A),
    }

    /** Keys showing live content (calendar date, clock time) – refreshed every minute. */
    val liveKeys: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

    @Volatile private var roles: Map<String, Glyph>? = null

    fun resetRoles() { roles = null }

    private fun rolesFor(ctx: Context): Map<String, Glyph> {
        roles?.let { return it }
        val pm = ctx.packageManager
        fun pkg(i: Intent): String? = try {
            pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it != "android" }
        } catch (_: Throwable) { null }
        fun cat(c: String) = pkg(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, c))
        val m = HashMap<String, Glyph>()
        fun put(p: String?, g: Glyph) { if (p != null) m.putIfAbsent(p, g) }
        put(pkg(Intent(Intent.ACTION_VIEW, Uri.parse("https://scyde.dev"))), Glyph.Browser)
        put(cat(Intent.CATEGORY_APP_MESSAGING), Glyph.Messages)
        put(cat(Intent.CATEGORY_APP_EMAIL), Glyph.Mail)
        put(pkg(Intent(Intent.ACTION_DIAL)), Glyph.Phone)
        put(pkg(Intent(MediaStore.ACTION_IMAGE_CAPTURE)), Glyph.Camera)
        put(cat(Intent.CATEGORY_APP_GALLERY), Glyph.Photos)
        put(cat(Intent.CATEGORY_APP_MUSIC), Glyph.Music)
        put(cat(Intent.CATEGORY_APP_MAPS), Glyph.Maps)
        put(cat(Intent.CATEGORY_APP_CALENDAR), Glyph.Calendar)
        put(cat(Intent.CATEGORY_APP_CONTACTS), Glyph.Contacts)
        put(cat(Intent.CATEGORY_APP_CALCULATOR), Glyph.Calculator)
        if (Build.VERSION.SDK_INT >= 29) put(cat(Intent.CATEGORY_APP_FILES), Glyph.Files)
        put(pkg(Intent(Settings.ACTION_SETTINGS)), Glyph.Settings)
        put(pkg(Intent(AlarmClock.ACTION_SHOW_ALARMS)), Glyph.Clock)
        put("com.android.vending", Glyph.Store)
        roles = m
        return m
    }

    /** Package-name hints for roles Android has no intent for (works across HyperOS, Google and AOSP apps). */
    private val hints = listOf(
        "deskclock" to Glyph.Clock, "clock" to Glyph.Clock,
        "calendar" to Glyph.Calendar, "calculator" to Glyph.Calculator,
        "notes" to Glyph.Notes, "keep" to Glyph.Notes, "weather" to Glyph.Weather,
        "soundrecorder" to Glyph.Recorder, "recorder" to Glyph.Recorder,
        "screenrecorder" to Glyph.ScreenRecord,
        "music" to Glyph.Music, "spotify" to Glyph.Music, "deezer" to Glyph.Music, "tidal" to Glyph.Music,
        "videoplayer" to Glyph.Video, "youtube" to Glyph.Video,
        "netflix" to Glyph.Tv, "hbo" to Glyph.Tv, "disney" to Glyph.Tv, "primevideo" to Glyph.Tv, "twitch" to Glyph.Tv,
        "podcast" to Glyph.Podcasts, "translate" to Glyph.Translate, "books" to Glyph.Books, "kindle" to Glyph.Books,
        "health" to Glyph.Health, "fitness" to Glyph.Fitness, "strava" to Glyph.Fitness,
        "wallet" to Glyph.Wallet, "pay" to Glyph.Wallet, "securitycenter" to Glyph.Security,
        "download" to Glyph.Downloads, "compass" to Glyph.Compass, "thememanager" to Glyph.Themes,
        "scanner" to Glyph.Scanner, "cleaner" to Glyph.Cleaner, "fileexplorer" to Glyph.Files, "files" to Glyph.Files,
        "docs" to Glyph.Docs, "office" to Glyph.Docs, "contacts" to Glyph.Contacts, "gallery" to Glyph.Photos,
        "photos" to Glyph.Photos, "camera" to Glyph.Camera, "mms" to Glyph.Messages, "messaging" to Glyph.Messages,
        "whatsapp" to Glyph.Chat, "telegram" to Glyph.Chat, "signal" to Glyph.Chat, "viber" to Glyph.Chat,
        "discord" to Glyph.Chat, "messenger" to Glyph.Chat, "gm" to Glyph.Mail, "mail" to Glyph.Mail, "outlook" to Glyph.Mail,
        "maps" to Glyph.Maps, "waze" to Glyph.Maps, "chrome" to Glyph.Browser, "browser" to Glyph.Browser,
        "firefox" to Glyph.Browser, "opera" to Glyph.Browser, "brave" to Glyph.Browser,
        "settings" to Glyph.Settings, "dialer" to Glyph.Phone, "vending" to Glyph.Store, "market" to Glyph.Store,
    )

    fun glyphFor(ctx: Context, pkg: String): Glyph? {
        rolesFor(ctx)[pkg]?.let { return it }
        val segments = pkg.lowercase().split('.')
        // short hints ("gm", "pay") must match a whole package segment, longer ones may be contained
        return hints.firstOrNull { (h, _) -> if (h.length <= 3) h in segments else pkg.lowercase().contains(h) }?.second
    }

    /** The pack icon for an app, or null if the pack has nothing for it. */
    fun icon(ctx: Context, key: String, info: LauncherActivityInfo, themeOthers: Boolean, dpi: Int): Drawable? {
        val g = glyphFor(ctx, info.componentName.packageName)
        if (g != null) {
            if (g == Glyph.Calendar || g == Glyph.Clock) liveKeys += key
            return build(g)
        }
        return if (themeOthers) themed(info.getIcon(dpi)) else null
    }

    fun build(g: Glyph): Drawable {
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(g.top.toInt(), g.bottom.toInt()))
        val fg: Drawable
        val mono: Drawable
        when (g) {
            Glyph.Calendar -> { fg = CalendarFace(false); mono = CalendarFace(true) }
            Glyph.Clock -> { fg = ClockFace(false); mono = ClockFace(true) }
            else -> {
                val path = pathOf(g.vector!!)
                fg = GlyphDrawable(path, g.glyph.toInt(), g.glyph2?.toInt())
                mono = GlyphDrawable(path, 0xFFFFFFFF.toInt(), null)
            }
        }
        return if (Build.VERSION.SDK_INT >= 33) AdaptiveIconDrawable(bg, fg, mono) else AdaptiveIconDrawable(bg, fg)
    }

    /** Any other app: its own monochrome glyph on a gradient tile made from its colours. */
    private fun themed(src: Drawable?): Drawable? {
        val a = src as? AdaptiveIconDrawable ?: return null
        if (Build.VERSION.SDK_INT < 33) return null
        val mono = a.monochrome ?: return null
        val avg = averageColor(a)
        val hsv = FloatArray(3); android.graphics.Color.colorToHSV(avg, hsv)
        hsv[1] = (hsv[1] * 1.25f).coerceIn(0.35f, 0.85f)
        val top = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1] * 0.85f, (hsv[2] * 1.15f).coerceIn(0.7f, 1f)))
        val bottom = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], (hsv[2] * 0.8f).coerceIn(0.45f, 0.85f)))
        val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
        val fg = mono.constantState?.newDrawable()?.mutate() ?: mono.mutate()
        fg.colorFilter = PorterDuffColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
        val m2 = mono.constantState?.newDrawable()?.mutate() ?: mono
        return AdaptiveIconDrawable(bg, fg, m2)
    }

    private fun averageColor(d: Drawable): Int {
        val b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); d.setBounds(0, 0, 24, 24); d.draw(c)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        for (x in 0 until 24) for (y in 0 until 24) {
            val p = b.getPixel(x, y)
            if ((p ushr 24) < 128) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++
        }
        b.recycle()
        if (n == 0) return 0xFF5E5CE6.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }

    private val pathCache = HashMap<ImageVector, Path>()

    /** Material Symbols (Apache 2.0) vector → android Path in a 24×24 viewport. */
    fun pathOf(v: ImageVector): Path = synchronized(pathCache) {
        pathCache.getOrPut(v) {
            val out = Path()
            fun walk(group: VectorGroup) {
                for (node in group) when (node) {
                    is VectorPath -> out.addPath(PathParser().addPathNodes(node.pathData).toPath().asAndroidPath())
                    is VectorGroup -> walk(node)
                }
            }
            walk(v.root)
            out
        }
    }

    /* ---------------------------------- drawables ---------------------------------- */

    /** Adaptive foreground: layer bounds are 1.5× the visible tile, so sizes are relative to bounds/1.5. */
    private abstract class Fg : Drawable() {
        protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
        protected fun tile() = bounds.width() / 1.5f
    }

    private class GlyphDrawable(val path: Path, val color: Int, val color2: Int?) : Fg() {
        private val m = android.graphics.Matrix()
        private val p = Path()
        override fun draw(canvas: Canvas) {
            val t = tile()
            val size = t * 0.56f
            val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
            m.reset()
            m.setScale(size / 24f, size / 24f)
            m.postTranslate(cx - size / 2f, cy - size / 2f)
            path.transform(m, p)
            paint.style = Paint.Style.FILL
            if (color2 != null && paint.colorFilter == null) {
                paint.shader = LinearGradient(0f, cy - size / 2, 0f, cy + size / 2, color, color2, Shader.TileMode.CLAMP)
            } else {
                paint.shader = null; paint.color = color
            }
            canvas.drawPath(p, paint)
        }
    }

    private class CalendarFace(val mono: Boolean) : Fg() {
        override fun draw(canvas: Canvas) {
            val t = tile()
            val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
            val today = LocalDate.now()
            paint.shader = null
            paint.textAlign = Paint.Align.CENTER
            if (!mono) {
                paint.color = 0xFFFF3B30.toInt()
                paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                paint.textSize = t * 0.15f
                canvas.drawText(today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(), cx, cy - t * 0.20f, paint)
            }
            paint.color = if (mono) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()
            paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            paint.textSize = t * (if (mono) 0.5f else 0.44f)
            canvas.drawText(today.dayOfMonth.toString(), cx, cy + t * (if (mono) 0.17f else 0.22f), paint)
        }
    }

    private class ClockFace(val mono: Boolean) : Fg() {
        override fun draw(canvas: Canvas) {
            val t = tile()
            val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
            val r = t * 0.40f
            paint.shader = null
            paint.style = Paint.Style.FILL
            val face = if (mono) 0x00000000 else 0xFFFFFFFF.toInt()
            val ink = if (mono) 0xFFFFFFFF.toInt() else 0xFF1C1C1E.toInt()
            if (!mono) { paint.color = face; canvas.drawCircle(cx, cy, r, paint) }
            paint.color = ink
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0)
                val r1 = r * (if (i % 3 == 0) 0.76f else 0.84f); val r2 = r * 0.92f
                paint.strokeWidth = t * (if (i % 3 == 0) 0.022f else 0.012f)
                canvas.drawLine(cx + r1 * sin(a).toFloat(), cy - r1 * cos(a).toFloat(), cx + r2 * sin(a).toFloat(), cy - r2 * cos(a).toFloat(), paint)
            }
            val now = LocalTime.now()
            val h = Math.toRadians(((now.hour % 12) + now.minute / 60.0) * 30)
            val mi = Math.toRadians(now.minute * 6.0)
            paint.strokeWidth = t * 0.035f
            canvas.drawLine(cx, cy, cx + r * 0.5f * sin(h).toFloat(), cy - r * 0.5f * cos(h).toFloat(), paint)
            paint.strokeWidth = t * 0.025f
            canvas.drawLine(cx, cy, cx + r * 0.75f * sin(mi).toFloat(), cy - r * 0.75f * cos(mi).toFloat(), paint)
            paint.style = Paint.Style.FILL
            paint.color = if (mono) ink else 0xFFFF9500.toInt()
            canvas.drawCircle(cx, cy, t * 0.03f, paint)
        }
    }
}
