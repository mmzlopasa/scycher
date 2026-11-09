package dev.scyde.scycher

import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.spring
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dev.scyde.scycher.data.DefaultLayout
import dev.scyde.scycher.data.LayoutOps
import dev.scyde.scycher.ui.HostCallbacks
import dev.scyde.scycher.ui.LauncherState
import dev.scyde.scycher.ui.home.LauncherRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LauncherActivity : ComponentActivity(), HostCallbacks {

    private val app get() = application as ScycherApp
    internal lateinit var state: LauncherState
    private var wentAway = false

    // pending widget flow
    private var pendingWidgetId = -1
    private var pendingSize = 1 to 1
    private var pendingProvider: AppWidgetProviderInfo? = null
    private var pendingPhotoItem = -1L
    private var pendingIconKey: String? = null

    private val pickIconImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val key = pendingIconKey
        if (uri == null || key == null) return@registerForActivityResult
        lifecycleScope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(filesDir, "icons").apply { mkdirs() }
                    val out = File(dir, "${key.hashCode().toUInt()}_${System.currentTimeMillis()}.img")
                    contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                    out
                }.getOrNull()
            }
            f?.let { state.setCustomIcon(key, it.path) }
        }
    }

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { state.resumeTick++ }

    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val ok = app.wallpaper.setFromUri(uri, alsoSystem = true)
            if (!ok) Toast.makeText(this@LauncherActivity, "Couldn't set wallpaper", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val id = pendingPhotoItem
        if (uri == null || id < 0) return@registerForActivityResult
        lifecycleScope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(filesDir, "photos").apply { mkdirs() }
                    val out = File(dir, "$id.jpg")
                    contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
                    out
                }.getOrNull()
            }
            f ?: return@launch
            LayoutOps.findItem(app.store.value.layout, id)?.let {
                state.updateItem(it.copy(extra = it.extra + ("photo" to f.path) + ("v" to System.currentTimeMillis().toString())))
            }
        }
    }

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingWidgetId
        if (r.resultCode == Activity.RESULT_OK && id >= 0) configureOrAdd(id) else if (id >= 0) app.widgets.delete(id)
    }

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.attributes = window.attributes.apply {
            if (android.os.Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // UI scope must carry Compose's frame clock so Animatable/pager animations can run
        state = LauncherState(app, CoroutineScope(lifecycleScope.coroutineContext + AndroidUiDispatcher.Main + motionScale), this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { state.closeOverlays() }
        })

        setContent { LauncherRoot(state) }

        // First run: seed a beautiful layout once apps are known.
        lifecycleScope.launch {
            app.apps.loaded.filter { it }.first()
            val cfg = app.store.value
            val empty = cfg.layout.dock.isEmpty() && cfg.layout.pages.all { it.items.isEmpty() }
            if (empty && !cfg.onboarded) {
                val l = DefaultLayout.build(this@LauncherActivity, app.apps.apps.value, cfg.home.columns, cfg.home.rows) { app.store.newId() }
                app.store.updateLayout { l }
            }
            // remove widget ids that leaked from cancelled flows
            app.widgets.cleanup(app.store.value.layout.pages.flatMap { p -> p.items.map { it.widgetId } }.filter { it >= 0 }.toSet())
        }

        // Re-fit grid when rows/cols shrink, react to status bar options
        lifecycleScope.launch {
            app.store.config.map { it.home.columns to it.home.rows }.distinctUntilChanged().collect { (c, r) ->
                app.store.updateLayout { LayoutOps.refit(it, c, r) { app.store.newId() } }
            }
        }
        lifecycleScope.launch {
            app.store.config.map { listOf(it.look.hideStatusBar, it.look.darkStatusIcons, it.look.hideNavBar) }.distinctUntilChanged().collect { (hide, dark, hideNav) ->
                val c = WindowInsetsControllerCompat(window, window.decorView)
                c.isAppearanceLightStatusBars = dark
                c.isAppearanceLightNavigationBars = dark
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (hide) c.hide(WindowInsetsCompat.Type.statusBars()) else c.show(WindowInsetsCompat.Type.statusBars())
                if (hideNav) c.hide(WindowInsetsCompat.Type.navigationBars()) else c.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
        lifecycleScope.launch {
            app.store.config.map { it.look.orientation }.distinctUntilChanged().collect { o ->
                requestedOrientation = when (o) {
                    dev.scyde.scycher.data.OrientationMode.Auto -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    dev.scyde.scycher.data.OrientationMode.Landscape -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    dev.scyde.scycher.data.OrientationMode.Portrait -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        app.wallpaper.syncTheme()
        if (app.store.value.icons.style == dev.scyde.scycher.data.IconStyle.Auto) app.apps.invalidateIcons()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            WallpaperManager.getInstance(this).setWallpaperOffsets(window.decorView.windowToken, 0.5f, 0.5f)
        } catch (_: Throwable) {}
    }

    /** Android's "Remove animations" (developer options / accessibility) sets the animator scale to 0. */
    fun reduceMotion(): Boolean =
        android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    /** Launcher transitions follow Scycher's "Animation speed" and the system animation scale. */
    private val motionScale = object : androidx.compose.ui.MotionDurationScale {
        override val scaleFactor: Float
            get() {
                val sys = android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
                return if (sys == 0f) 0f else sys / app.store.value.look.animationSpeed.coerceIn(0.25f, 4f)
            }
    }

    override fun onStart() {
        super.onStart()
        app.widgets.start()
        dev.scyde.scycher.gestures.SystemActions.watchTorch(this)
    }

    override fun onResume() {
        super.onResume()
        state.resumeTick++
        app.weather.refreshIfStale()
        app.wallpaper.syncTheme()
        app.apps.refreshLiveIcons()
        liveTicker?.cancel()
        liveTicker = lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000 - System.currentTimeMillis() % 60_000)
                app.apps.refreshLiveIcons()
            }
        }
        if (wentAway) {
            wentAway = false
            if (app.store.value.home.returnAnimation && !reduceMotion()) state.scope.launch {
                state.enter.snapTo(0f)
                state.enter.animateTo(1f, dev.scyde.scycher.ui.common.Motion.ui())
            }
        }
    }

    private var liveTicker: kotlinx.coroutines.Job? = null

    override fun onPause() {
        super.onPause()
        liveTicker?.cancel(); liveTicker = null
    }

    override fun onStop() {
        super.onStop()
        wentAway = true
        app.widgets.stop()
        // Leaving home for an app closes transient UI like iOS does
        state.menu = null
        if (state.library.value > 0f && state.drag == null) state.scope.launch { state.library.snapTo(0f) }
        state.openFolderId = null
        state.openCategory = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.ACTION_MAIN == intent.action && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (hasWindowFocus() || !wentAway) state.onHomePressed()
        }
    }

    /* ------------------------------ HostCallbacks ------------------------------ */

    override fun hostView(): View = window.decorView

    override fun pickWallpaper() {
        pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun pickPhoto(itemId: Long) {
        pendingPhotoItem = itemId
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun pickIcon(appKey: String) {
        pendingIconKey = appKey
        pickIconImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun authenticate(title: String, onSuccess: () -> Unit, onFallback: () -> Unit) =
        dev.scyde.scycher.data.Security.authenticate(this, title, onSuccess, onFallback)

    override fun requestCalendarPermission() {
        calendarPermission.launch(android.Manifest.permission.READ_CALENDAR)
    }

    override fun requestDefaultLauncher() {
        try { roleRequest.launch(dev.scyde.scycher.gestures.SystemActions.defaultLauncherIntent(this)) } catch (_: Throwable) {}
    }

    override fun addSystemWidget(provider: AppWidgetProviderInfo, w: Int, h: Int) {
        val id = app.widgets.allocate()
        pendingWidgetId = id; pendingSize = w to h; pendingProvider = provider
        if (app.widgets.bindIfAllowed(id, provider.provider)) {
            configureOrAdd(id)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
            try { bindWidget.launch(i) } catch (_: Throwable) { app.widgets.delete(id) }
        }
    }

    private fun configureOrAdd(id: Int) {
        val info = app.widgets.info(id) ?: pendingProvider
        if (info?.configure != null) {
            try {
                app.widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
                return
            } catch (_: Throwable) { /* fall through and add unconfigured */ }
        }
        finishAdd(id)
    }

    private fun finishAdd(id: Int) {
        val provider = (app.widgets.info(id) ?: pendingProvider)?.provider?.flattenToString() ?: ""
        state.addSystemWidgetItem(id, provider, pendingSize.first, pendingSize.second)
        pendingWidgetId = -1
    }

    @Deprecated("Needed for AppWidgetHost configure flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
            if (resultCode == Activity.RESULT_OK && id >= 0) finishAdd(id) else if (id >= 0) app.widgets.delete(id)
        }
    }

    companion object { private const val REQ_CONFIGURE = 0x51 }
}
