package dev.scyde.scycher

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.scyde.scycher.data.WallpaperMode
import dev.scyde.scycher.settings.SettingsActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private fun capture(activity: android.app.Activity): Bitmap {
    val v = activity.window.decorView
    val bmp = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(0xFF000000.toInt())
    v.draw(c)
    return bmp
}

private fun save(bmp: Bitmap, name: String) {
    val dir = File(System.getProperty("user.dir"), "build/shots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<LauncherActivity>()

    private fun settle(ms: Long = 1500) {
        // Animations started from the activity use the Choreographer clock, composition uses the
        // test clock – step both a few times so they converge.
        repeat(6) {
            rule.mainClock.advanceTimeBy(ms / 6)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(ms / 6, java.util.concurrent.TimeUnit.MILLISECONDS)
            rule.waitForIdle()
        }
    }

    private fun shot(name: String) = save(capture(rule.activity), name)

    @Test
    fun screens() {
        rule.mainClock.autoAdvance = false
        settle(2500)
        shot("01_onboarding")
        val app = ScycherApp.instance
        // give the glass something to blur
        val wp = File(app.filesDir, "wallpaper.jpg")
        val bmp = Bitmap.createBitmap(1280, 800, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint()
        p.shader = android.graphics.LinearGradient(0f, 0f, 1280f, 800f, intArrayOf(0xFF1B1340.toInt(), 0xFF7A2E8E.toInt(), 0xFFF0855A.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1280f, 800f, p)
        p.shader = null; p.color = 0xFF62D2FF.toInt(); c.drawCircle(980f, 220f, 160f, p)
        p.color = 0xFFFFD60A.toInt(); c.drawCircle(260f, 640f, 120f, p)
        wp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        app.store.update { it.copy(onboarded = true, look = it.look.copy(wallpaperMode = WallpaperMode.Scycher)) }
        runBlocking { app.wallpaper.reload().join() }
        settle(2500)
        shot("02_home")

        val state = rule.activity.state
        rule.runOnUiThread { state.openLibrary() }
        settle(1500)
        shot("03_library")
        rule.runOnUiThread { state.closeLibrary() }
        settle(1500)

        rule.runOnUiThread { state.editMode = true }
        settle(800)
        shot("04_edit")
        rule.runOnUiThread { state.widgetPicker = true }
        settle(1500)
        shot("05_widget_picker")
        rule.runOnUiThread { state.widgetPicker = false; state.editMode = false }
        settle(1200)
        rule.runOnUiThread {
            state.menu = dev.scyde.scycher.ui.MenuRequest(null, null, androidx.compose.ui.geometry.Rect(900f, 600f, 901f, 601f), dev.scyde.scycher.ui.DragSource.Page(0))
        }
        settle(1000)
        shot("06_menu")
        rule.runOnUiThread { state.menu = null }
        settle(800)

        // --- Smart Launcher style features ---
        rule.runOnUiThread {
            app.store.update { it.copy(library = it.library.copy(style = dev.scyde.scycher.data.LibraryStyle.Tabs)) }
            state.openLibrary()
        }
        settle(1500)
        shot("10_library_tabs")
        rule.runOnUiThread { state.libraryQuery = "12*7+3" }
        settle(800)
        shot("11_search_calculator")
        rule.runOnUiThread { state.libraryQuery = "blue" }
        settle(800)
        shot("12_search_settings")
        rule.runOnUiThread { state.closeLibrary() }
        settle(1200)

        val id = { app.store.newId() }
        rule.runOnUiThread {
            app.store.updateLayout { l ->
                val W = dev.scyde.scycher.data.ItemType.Widget
                l.copy(pages = l.pages.take(1) + dev.scyde.scycher.data.HomePage(id(), listOf(
                    dev.scyde.scycher.data.HomeItem(id(), W, 0, 0, 4, 2, builtin = dev.scyde.scycher.data.BuiltinKind.Stack),
                    dev.scyde.scycher.data.HomeItem(id(), W, 4, 0, 2, 2, builtin = dev.scyde.scycher.data.BuiltinKind.ClockDigital, extra = mapOf("variant" to "Stacked")),
                    dev.scyde.scycher.data.HomeItem(id(), W, 0, 2, 4, 2, builtin = dev.scyde.scycher.data.BuiltinKind.Agenda),
                    dev.scyde.scycher.data.HomeItem(id(), W, 4, 2, 2, 2, builtin = dev.scyde.scycher.data.BuiltinKind.ClockDigital, extra = mapOf("variant" to "Minimal")),
                    dev.scyde.scycher.data.HomeItem(id(), W, 0, 4, 4, 1, builtin = dev.scyde.scycher.data.BuiltinKind.Toggles),
                )))
            }
        }
        settle(800)
        rule.runOnUiThread { state.scope.launch { state.pager.scrollToPage(1) } }
        settle(1500)
        shot("13_new_widgets")

        rule.runOnUiThread {
            app.store.update { it.copy(look = it.look.copy(adaptiveColors = true, vignette = 0.5f)) }
        }
        settle(1500)
        shot("14_adaptive_colors")

        rule.runOnUiThread {
            app.store.update { it.copy(security = it.security.copy(pinHash = dev.scyde.scycher.data.Security.hash("1234"), biometric = false)) }
            state.pin = dev.scyde.scycher.ui.PinRequest("Unlock Chrome") {}
        }
        settle(1200)
        shot("15_pin")
        rule.runOnUiThread { state.pin = null; app.store.update { it.copy(look = it.look.copy(adaptiveColors = false, vignette = 0f)) } }
        settle(800)

        // --- iPad surfaces ---
        rule.runOnUiThread { state.scope.launch { state.pager.scrollToPage(0) } }
        settle(800)
        rule.runOnUiThread { state.spotlight = true; state.spotlightQuery = "" }
        settle(1200)
        shot("18_spotlight")
        rule.runOnUiThread { state.spotlightQuery = "25*4" }
        settle(800)
        shot("19_spotlight_query")
        rule.runOnUiThread { state.spotlight = false; state.controlCenter = true }
        settle(1500)
        shot("20_control_center")
        rule.runOnUiThread { state.controlCenter = false; state.openToday() }
        settle(1500)
        shot("21_today_view")
        rule.runOnUiThread { state.closeToday() }
        settle(1500)
        rule.runOnUiThread { state.editMode = true; state.customize = true }
        settle(1500)
        shot("22_customize")
        rule.runOnUiThread {
            state.customize = false; state.editMode = false
            app.store.update { it.copy(icons = it.icons.copy(style = dev.scyde.scycher.data.IconStyle.Clear)) }
        }
        settle(1500)
        shot("23_clear_icons")

        // --- break-ui: worst-case data ---
        rule.runOnUiThread {
            app.store.update { c ->
                val W = dev.scyde.scycher.data.ItemType.Widget
                val longNote = "Ponedjeljak: nazvati Aleksandru Wiśniewsku-Kowalczyk oko ugovora. ".repeat(40)
                c.copy(
                    icons = c.icons.copy(style = dev.scyde.scycher.data.IconStyle.Default),
                    widgets = c.widgets.copy(
                        weatherCity = "Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch",
                        worldClocks = listOf("America/Argentina/ComodRivadavia", "Pacific/Port_Moresby", "Not/AZone", "Asia/Ho_Chi_Minh"),
                    ),
                    layout = c.layout.copy(pages = c.layout.pages + dev.scyde.scycher.data.HomePage(app.store.newId(), listOf(
                        dev.scyde.scycher.data.HomeItem(app.store.newId(), W, 0, 0, 4, 1, builtin = dev.scyde.scycher.data.BuiltinKind.Greeting,
                            extra = mapOf("name" to "Aleksandra Wiśniewska-Kowalczyk von Oberhauser-Wettstein")),
                        dev.scyde.scycher.data.HomeItem(app.store.newId(), W, 0, 1, 2, 2, builtin = dev.scyde.scycher.data.BuiltinKind.Note,
                            extra = mapOf("title" to "Benachrichtigungseinstellungen und Datenschutzerklärung", "note" to longNote)),
                        dev.scyde.scycher.data.HomeItem(app.store.newId(), W, 2, 1, 2, 2, builtin = dev.scyde.scycher.data.BuiltinKind.Weather),
                        dev.scyde.scycher.data.HomeItem(app.store.newId(), W, 4, 0, 2, 3, builtin = dev.scyde.scycher.data.BuiltinKind.WorldClock),
                        dev.scyde.scycher.data.HomeItem(app.store.newId(), dev.scyde.scycher.data.ItemType.Folder, 0, 4, name = "Paramètres de confidentialité et de sécurité", apps = listOf("x/y", "a/b")),
                        dev.scyde.scycher.data.HomeItem(app.store.newId(), W, 1, 3, 1, 1, builtin = dev.scyde.scycher.data.BuiltinKind.Calendar),
                    )))
                )
            }
        }
        settle(800)
        rule.runOnUiThread { state.scope.launch { state.pager.scrollToPage(state.store.value.layout.pages.lastIndex) } }
        settle(1500)
        shot("24_break_ui")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun settings() {
        rule.waitForIdle()
        save(capture(rule.activity), "07_settings")
        rule.runOnUiThread { rule.activity.nav += "gestures" }
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        save(capture(rule.activity), "08_gestures")
        rule.runOnUiThread { rule.activity.nav.clear(); rule.activity.nav += "dock" }
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        save(capture(rule.activity), "09_dock")
        rule.runOnUiThread { rule.activity.nav.clear(); rule.activity.nav += "security" }
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        save(capture(rule.activity), "16_settings_security")
        rule.runOnUiThread { rule.activity.nav.clear(); rule.activity.nav += "appearance" }
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        save(capture(rule.activity), "17_settings_appearance")
    }
}
