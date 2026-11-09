package dev.scyde.scycher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.GestureAction
import dev.scyde.scycher.data.HomeItem
import dev.scyde.scycher.data.HomeLayout
import dev.scyde.scycher.data.HomePage
import dev.scyde.scycher.data.ItemType
import dev.scyde.scycher.data.LayoutOps
import dev.scyde.scycher.gestures.SystemActions
import dev.scyde.scycher.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

sealed interface DragSource {
    data class Page(val index: Int) : DragSource
    data object Dock : DragSource
    data object Library : DragSource
    data class Folder(val folderId: Long) : DragSource
}

@Stable
class DragSession(
    val item: HomeItem,
    val source: DragSource,
    val grab: Offset,
    val size: IntSize,
    start: Offset,
    active: Boolean,
) {
    var pointer by mutableStateOf(start)
    var active by mutableStateOf(active)
    val start = start
}

data class MenuRequest(val item: HomeItem?, val appKey: String?, val anchor: androidx.compose.ui.geometry.Rect, val source: DragSource)

sealed interface RenameTarget {
    data class App(val key: String) : RenameTarget
    data class Folder(val id: Long) : RenameTarget
}

/** Interface implemented by the activity for flows that need activity results. */
interface HostCallbacks {
    fun pickWallpaper()
    fun pickPhoto(itemId: Long)
    fun addSystemWidget(provider: android.appwidget.AppWidgetProviderInfo, w: Int, h: Int)
    fun hostView(): View
    fun requestDefaultLauncher()
    fun pickIcon(appKey: String)
    fun authenticate(title: String, onSuccess: () -> Unit, onFallback: () -> Unit)
    fun requestCalendarPermission()
}

data class PinRequest(val title: String, val onSuccess: () -> Unit)
data class AppChooserRequest(val title: String, val onPick: (String?) -> Unit)

@Stable
class LauncherState(val app: ScycherApp, val scope: CoroutineScope, val host: HostCallbacks) {
    val store = app.store
    val repo = app.apps
    val context: Context get() = app

    var editMode by mutableStateOf(false)
    val library = Animatable(0f)
    var libraryQuery by mutableStateOf("")
    var libraryFocus by mutableIntStateOf(0)
    var openFolderId by mutableStateOf<Long?>(null)
    /** Where the folder icon sits on screen, so the folder can grow out of it and shrink back. */
    var folderAnchor: androidx.compose.ui.geometry.Rect? = null
    fun openFolder(id: Long, bounds: Rect?) {
        folderAnchor = bounds?.let { androidx.compose.ui.geometry.Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
        openFolderId = id
    }
    var openCategory by mutableStateOf<Pair<String, List<String>>?>(null)
    var menu by mutableStateOf<MenuRequest?>(null)
    var drag by mutableStateOf<DragSession?>(null)
    var widgetPicker by mutableStateOf(false)
    var widgetSettingsId by mutableStateOf<Long?>(null)
    var rename by mutableStateOf<RenameTarget?>(null)
    var resizingId by mutableStateOf<Long?>(null)
    var pin by mutableStateOf<PinRequest?>(null)
    // iPad surfaces
    var spotlight by mutableStateOf(false)
    var spotlightQuery by mutableStateOf("")
    var controlCenter by mutableStateOf(false)
    val today = Animatable(0f)
    var todayEdit by mutableStateOf(false)
    var customize by mutableStateOf(false)
    var wallpaperSheet by mutableStateOf(false)
    /** Where the widget picker adds widgets: "home" or "today". */
    var pickerTarget by mutableStateOf("home")
    var appChooser by mutableStateOf<AppChooserRequest?>(null)
    var categoryFor by mutableStateOf<String?>(null)
    var showHiddenApps by mutableStateOf(false)
    /** Icons with a swipe-up action and stack widgets claim vertical swipes in these root rects. */
    val swipeZones = HashMap<Long, Pair<androidx.compose.ui.geometry.Rect, String>>()

    /** Root-space top-left a just-dropped item should settle from. */
    val dropOrigins = HashMap<Long, Offset>()
    fun takeDropOrigin(id: Long): Offset? = dropOrigins.remove(id)
    /** The item that was just added (it fades/scales in once). */
    var lastAddedId by mutableStateOf<Long?>(null)
    /** Items playing their removal animation. */
    val removing = androidx.compose.runtime.mutableStateListOf<Long>()

    fun removeAnimated(item: HomeItem) {
        if (item.id in removing) return
        removing += item.id
        scope.launch {
            kotlinx.coroutines.delay(160)
            removeItem(item)
            removing -= item.id
        }
    }

    /** Incremented on every onResume so UI can re-check permissions etc. */
    var resumeTick by mutableIntStateOf(0)

    lateinit var pager: PagerState

    // Geometry reported by layout (root coordinates)
    var gridOrigin = Offset.Zero
    var cellSize = Size(1f, 1f)
    var dockBounds = androidx.compose.ui.geometry.Rect.Zero
    var dockCenters: List<Float> = emptyList()
    var rootSize = Size.Zero

    /** Home enter animation (returning from an app). */
    val enter = Animatable(1f)

    val cols get() = store.value.home.columns
    val rows get() = store.value.home.rows

    fun newId() = store.newId()

    /* ------------------------------ overlays ------------------------------ */

    fun anyOverlay() = wallpaperSheet || spotlight || controlCenter || customize || today.targetValue > 0f || pin != null || appChooser != null || categoryFor != null || menu != null || openFolderId != null || openCategory != null || widgetPicker ||
        widgetSettingsId != null || rename != null || library.targetValue > 0f

    /** Back: closes only the topmost layer, like iPadOS. */
    fun closeOverlays(): Boolean {
        when {
            pin != null -> pin = null
            menu != null -> menu = null
            rename != null -> rename = null
            categoryFor != null -> categoryFor = null
            appChooser != null -> appChooser = null
            widgetSettingsId != null -> widgetSettingsId = null
            widgetPicker -> { widgetPicker = false; pickerTarget = "home" }
            openCategory != null -> openCategory = null
            openFolderId != null -> openFolderId = null
            spotlight -> { spotlight = false; spotlightQuery = "" }
            controlCenter -> controlCenter = false
            wallpaperSheet -> wallpaperSheet = false
            customize -> customize = false
            library.targetValue > 0f -> closeLibrary()
            today.targetValue > 0f -> closeToday()
            editMode -> editMode = false
            else -> return false
        }
        return true
    }

    /** Home: everything closes at once. */
    fun closeAll(): Boolean {
        val any = anyOverlay() || editMode
        pin = null; menu = null; rename = null; categoryFor = null; appChooser = null; widgetSettingsId = null
        widgetPicker = false; pickerTarget = "home"; openCategory = null; openFolderId = null
        spotlight = false; spotlightQuery = ""; controlCenter = false; wallpaperSheet = false; customize = false
        if (library.targetValue > 0f || library.value > 0f) closeLibrary()
        if (today.targetValue > 0f || today.value > 0f) closeToday()
        editMode = false
        return any
    }

    /** [velocity] is the finger's release velocity in progress-units/s (Apple: hand the velocity to the spring). */
    fun openLibrary(search: Boolean = false, velocity: Float = 0f) {
        libraryQuery = ""
        scope.launch { library.animateTo(1f, dev.scyde.scycher.ui.common.Motion.sheet(), initialVelocity = velocity) }
        if (search || store.value.library.autoKeyboard) libraryFocus++
    }

    fun openToday(velocity: Float = 0f) { scope.launch { today.animateTo(1f, dev.scyde.scycher.ui.common.Motion.sheet(), initialVelocity = velocity) } }
    fun closeToday(velocity: Float = 0f) { todayEdit = false; scope.launch { today.animateTo(0f, dev.scyde.scycher.ui.common.Motion.exit(), initialVelocity = velocity) } }

    fun closeLibrary(velocity: Float = 0f) {
        showHiddenApps = false
        scope.launch { library.animateTo(0f, dev.scyde.scycher.ui.common.Motion.exit(), initialVelocity = velocity) }
        libraryQuery = ""
    }

    fun onHomePressed() {
        if (drag != null) cancelDrag()
        if (closeAll()) return
        if (store.value.home.homeButtonGoesToFirstPage && ::pager.isInitialized && pager.currentPage != 0)
            scope.launch { pager.animateScrollToPage(0) }
    }

    /* ------------------------------ actions ------------------------------ */

    fun launch(key: String, bounds: Rect?) {
        val go = { repo.launch(key, host.hostView(), bounds, store.value.look.openAnimation); Unit }
        if (key in store.value.security.protectedApps) requireUnlock("Unlock ${repo.label(key)}", go) else go()
    }

    /** Runs [action] after PIN / biometric check (or right away if no PIN is set). */
    fun requireUnlock(title: String, action: () -> Unit) {
        val sec = store.value.security
        if (sec.pinHash == null || dev.scyde.scycher.data.Security.recentlyUnlocked(sec)) { action(); return }
        val askPin = { pin = PinRequest(title, action) }
        if (sec.biometric && dev.scyde.scycher.data.Security.biometricAvailable(context)) host.authenticate(title, action, askPin)
        else askPin()
    }

    fun setCustomIcon(key: String, path: String?) {
        store.value.customIcons[key]?.takeIf { it != path }?.let { old -> scope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { java.io.File(old).delete() } } }
        store.update { c -> c.copy(customIcons = if (path == null) c.customIcons - key else c.customIcons + (key to path)) }
        scope.launch { kotlinx.coroutines.delay(300); repo.invalidate(key) }
    }

    fun chooseSwipeUpApp(key: String) {
        appChooser = AppChooserRequest("Swipe up on ${repo.label(key)} opens…") { target ->
            store.update { c -> c.copy(iconSwipeUp = if (target == null) c.iconSwipeUp - key else c.iconSwipeUp + (key to target)) }
        }
    }

    fun runGesture(action: GestureAction, appKey: String?) {
        when (action) {
            GestureAction.None -> {}
            GestureAction.OpenLibrary -> openLibrary()
            GestureAction.Search -> { spotlightQuery = ""; spotlight = true }
            GestureAction.ControlCenter -> controlCenter = true
            GestureAction.TodayView -> openToday()
            GestureAction.Notifications -> SystemActions.openNotifications(context)
            GestureAction.QuickSettings -> SystemActions.openQuickSettings(context)
            GestureAction.LockScreen -> SystemActions.lockScreen(context)
            GestureAction.Recents -> SystemActions.recents(context)
            GestureAction.EditMode -> editMode = true
            GestureAction.ScycherSettings -> openSettings()
            GestureAction.Screenshot -> SystemActions.screenshot(context)
            GestureAction.Flashlight -> SystemActions.toggleTorch(context)
            GestureAction.PowerMenu -> SystemActions.powerMenu(context)
            GestureAction.SplitScreen -> SystemActions.splitScreen(context)
            GestureAction.Widgets -> { editMode = true; widgetPicker = true }
            GestureAction.LaunchApp -> appKey?.let { launch(it, null) }
            GestureAction.Assistant -> SystemActions.assistant(context)
            GestureAction.Camera -> SystemActions.camera(context)
            GestureAction.VolumePanel -> SystemActions.volumePanel(context)
            GestureAction.NextWallpaper -> scope.launch { dev.scyde.scycher.ScycherApp.instance.wallpaper.nextPack() }
            GestureAction.WallpaperLightDark -> scope.launch { dev.scyde.scycher.ScycherApp.instance.wallpaper.toggleVariant() }
        }
    }

    fun openSettings(screen: String? = null) {
        context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { screen?.let { putExtra("screen", it) } })
    }

    fun currentPage(): Int = if (::pager.isInitialized) pager.currentPage.coerceIn(0, store.value.layout.pages.lastIndex) else 0

    fun addAppToHome(key: String) {
        val id = newId(); lastAddedId = id
        store.updateLayout { LayoutOps.placeAnywhere(it, cols, rows, HomeItem(id, ItemType.App, app = key), currentPage(), newId()) }
    }

    fun addAppToDock(key: String): Boolean {
        val l = store.value.layout
        if (l.dock.size >= store.value.dock.maxIcons) return false
        if (l.dock.any { it.app == key }) return true
        store.updateLayout { it.copy(dock = it.dock + HomeItem(newId(), ItemType.App, app = key)) }
        return true
    }

    fun addBuiltinWidget(kind: dev.scyde.scycher.data.BuiltinKind, w: Int, h: Int) {
        val id = newId()
        lastAddedId = id
        if (pickerTarget == "today") {
            store.updateLayout { it.copy(today = it.today + HomeItem(newId(), ItemType.Widget, w = w.coerceAtMost(4), h = h, builtin = kind)) }
            widgetPicker = false; pickerTarget = "home"; return
        }
        val item = HomeItem(id, ItemType.Widget, w = w.coerceAtMost(cols), h = h.coerceAtMost(rows), builtin = kind)
        store.updateLayout { LayoutOps.placeAnywhere(it, cols, rows, item, currentPage(), newId()) }
        widgetPicker = false
    }

    fun addSystemWidgetItem(widgetId: Int, provider: String, w: Int, h: Int) {
        pickerTarget = "home"
        val sid = newId(); lastAddedId = sid
        val item = HomeItem(sid, ItemType.SystemWidget, w = w.coerceIn(1, cols), h = h.coerceIn(1, rows), widgetId = widgetId, provider = provider)
        store.updateLayout { LayoutOps.placeAnywhere(it, cols, rows, item, currentPage(), newId()) }
        widgetPicker = false
    }

    fun removeItem(item: HomeItem) {
        if (item.type == ItemType.SystemWidget) app.widgets.delete(item.widgetId)
        store.updateLayout { LayoutOps.removeItem(it, item.id) }
    }

    fun updateItem(item: HomeItem) = store.updateLayout { LayoutOps.replaceItem(it, item) }

    fun addPage() {
        store.updateLayout { it.copy(pages = it.pages + HomePage(newId())) }
        scope.launch { pager.animateScrollToPage(store.value.layout.pages.lastIndex) }
    }

    fun deletePage(index: Int) {
        val l = store.value.layout
        if (l.pages.size <= 1) return
        l.pages.getOrNull(index)?.items?.filter { it.type == ItemType.SystemWidget }?.forEach { app.widgets.delete(it.widgetId) }
        store.updateLayout { it.copy(pages = it.pages.filterIndexed { i, _ -> i != index }) }
    }

    fun removeFromFolder(folderId: Long, key: String) {
        store.updateLayout { l -> removeAppFromFolder(l, folderId, key) }
    }

    private fun removeAppFromFolder(l: HomeLayout, folderId: Long, key: String): HomeLayout {
        val f = LayoutOps.findItem(l, folderId) ?: return l
        val rest = f.apps.filter { it != key }
        return when {
            rest.isEmpty() -> LayoutOps.removeItem(l, folderId)
            rest.size == 1 -> LayoutOps.replaceItem(l, f.copy(type = ItemType.App, app = rest[0], apps = emptyList(), name = null))
            else -> LayoutOps.replaceItem(l, f.copy(apps = rest))
        }
    }

    /* ---------------------------- drag & drop ---------------------------- */

    fun beginDrag(item: HomeItem, source: DragSource, rootPointer: Offset, grab: Offset, size: IntSize, anchor: androidx.compose.ui.geometry.Rect) {
        if (store.value.home.lockLayout && source !is DragSource.Library && !editMode) {
            menu = MenuRequest(item, item.app, anchor, source); return
        }
        val immediate = editMode && source !is DragSource.Library
        drag = DragSession(item, source, grab, size, rootPointer, immediate)
        if (!immediate) menu = MenuRequest(item, if (item.type == ItemType.App) item.app else null, anchor, source)
    }

    fun dragBy(delta: Offset, slopPx: Float) {
        val d = drag ?: return
        d.pointer += delta
        if (!d.active && (d.pointer - d.start).getDistance() > slopPx) {
            if (store.value.home.lockLayout) { drag = null; return }
            d.active = true
            menu = null
            if (d.source !is DragSource.Library && d.source !is DragSource.Folder) editMode = true
        }
    }

    fun cancelDrag() { drag = null }

    fun endDrag() {
        val d = drag ?: return
        drag = null
        if (!d.active) return // menu stays open
        drop(d)
        if (d.source is DragSource.Library) scope.launch { library.snapTo(0f) }
        if (d.source is DragSource.Folder) openFolderId = null
    }

    private fun drop(d: DragSession) {
        val cfg = store.value
        val p = d.pointer
        val item = d.item
        val isIcon = item.type == ItemType.App || item.type == ItemType.Folder

        // 1) Dock
        if (isIcon && cfg.dock.enabled && dockBounds.inflate(24f).contains(p)) {
            val layout = cfg.layout
            val fromDock = d.source == DragSource.Dock
            val dockCount = layout.dock.size - (if (fromDock) 1 else 0)
            if (!fromDock && dockCount >= cfg.dock.maxIcons) return
            val centers = if (fromDock) dockCenters.filterIndexed { i, _ -> layout.dock.getOrNull(i)?.id != item.id } else dockCenters
            val index = centers.count { it < p.x }.coerceIn(0, dockCount)
            store.updateLayout { l0 ->
                var l = detach(l0, d)
                val newItem = if (d.source is DragSource.Library || d.source is DragSource.Folder) item.copy(id = newId()) else item
                val list = l.dock.filterNot { it.id == newItem.id }.toMutableList()
                list.add(index.coerceAtMost(list.size), newItem.copy(x = 0, y = 0, w = 1, h = 1))
                l = l.copy(dock = list)
                l
            }
            return
        }

        // 2) Home grid
        val pageIndex = currentPage()
        val page = cfg.layout.pages.getOrNull(pageIndex) ?: return
        val cw = cellSize.width; val ch = cellSize.height
        if (cw <= 1f || ch <= 1f) return
        val cx: Int; val cy: Int
        if (isIcon) {
            cx = floor((p.x - gridOrigin.x) / cw).toInt()
            cy = floor((p.y - gridOrigin.y) / ch).toInt()
        } else {
            val tl = p - d.grab
            cx = ((tl.x - gridOrigin.x) / cw).roundToInt()
            cy = ((tl.y - gridOrigin.y) / ch).roundToInt()
        }
        val tx = cx.coerceIn(0, cols - item.w)
        val ty = cy.coerceIn(0, rows - item.h)
        val ignore = if (d.source is DragSource.Page) item.id else null
        val occupant = if (cx in 0 until cols && cy in 0 until rows) LayoutOps.itemAt(page, cx, cy, ignore) else null

        // Merge into folder
        if (item.type == ItemType.App && occupant != null && (occupant.type == ItemType.App || occupant.type == ItemType.Folder)) {
            val key = item.app ?: return
            store.updateLayout { l0 ->
                // dropped back on its own folder: nothing changes (keeps the folder and its name)
                if (d.source is DragSource.Folder && (d.source as DragSource.Folder).folderId == occupant.id) return@updateLayout l0
                val l = detach(l0, d)
                val occ = LayoutOps.findItem(l, occupant.id) ?: return@updateLayout l0
                if (occ.type == ItemType.Folder) {
                    if (key in occ.apps) l else LayoutOps.replaceItem(l, occ.copy(apps = occ.apps + key))
                } else {
                    if (occ.app == key) l else LayoutOps.replaceItem(l, occ.copy(type = ItemType.Folder, apps = listOfNotNull(occ.app, key), app = null, name = "Folder"))
                }
            }
            return
        }

        val fits = LayoutOps.fits(page, cols, rows, tx, ty, item.w, item.h, ignore)
        store.updateLayout { l0 ->
            val l = detach(l0, d)
            val target = l.pages.getOrNull(pageIndex) ?: return@updateLayout l0
            val newItem = if (d.source is DragSource.Library || d.source is DragSource.Folder) item.copy(id = newId()) else item
            val spot = if (fits) tx to ty else LayoutOps.findFree(target, cols, rows, item.w, item.h)
            if (spot == null) {
                if (d.source is DragSource.Page) return@updateLayout l0 // snap back
                return@updateLayout LayoutOps.placeAnywhere(l, cols, rows, newItem, pageIndex, newId())
            }
            val placed = newItem.copy(x = spot.first, y = spot.second)
            // Remember where the finger let go so the item settles from there instead of jumping.
            val shadowCenter = p - d.grab + Offset(d.size.width / 2f, d.size.height / 2f)
            dropOrigins[placed.id] = shadowCenter - Offset(cw * placed.w / 2f, ch * placed.h / 2f)
            l.copy(pages = l.pages.toMutableList().also { it[pageIndex] = target.copy(items = target.items + placed) })
        }
    }

    /** Removes the dragged item from where it came from. */
    private fun detach(l: HomeLayout, d: DragSession): HomeLayout = when (val s = d.source) {
        is DragSource.Page, DragSource.Dock -> LayoutOps.removeItem(l, d.item.id)
        is DragSource.Folder -> removeAppFromFolder(l, s.folderId, d.item.app ?: "")
        DragSource.Library -> l
    }

    /** Called continuously while dragging to flip pages near the screen edges. */
    fun edgeFlip(direction: Int) {
        val pages = store.value.layout.pages
        val cur = pager.currentPage
        val target = cur + direction
        if (target < 0) return
        if (target > pages.lastIndex) {
            if (pages[cur].items.isEmpty()) return
            store.updateLayout { it.copy(pages = it.pages + HomePage(newId())) }
        }
        scope.launch { pager.animateScrollToPage(target) }
    }

    /** Remove trailing empty pages after editing. */
    fun tidyPages() {
        store.updateLayout { l ->
            var pages = l.pages
            while (pages.size > 1 && pages.last().items.isEmpty()) pages = pages.dropLast(1)
            l.copy(pages = pages)
        }
    }
}
