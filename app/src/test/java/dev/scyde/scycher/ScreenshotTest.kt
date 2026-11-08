package dev.scyde.scycher

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.scyde.scycher.data.WallpaperMode
import dev.scyde.scycher.settings.SettingsActivity
import kotlinx.coroutines.runBlocking
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
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
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
    }
}
