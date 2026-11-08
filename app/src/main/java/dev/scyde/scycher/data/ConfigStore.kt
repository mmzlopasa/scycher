package dev.scyde.scycher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Single source of truth for configuration + home layout. Writes are debounced and atomic.
 */
class ConfigStore(context: Context) {
    private val file = File(context.filesDir, "scycher.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        coerceInputValues = true
    }

    private val _config = MutableStateFlow(load())
    val config: StateFlow<LauncherConfig> = _config

    private val idGen = AtomicLong(System.currentTimeMillis())
    fun newId(): Long = idGen.incrementAndGet()

    init {
        @OptIn(FlowPreview::class)
        _config.drop(1).debounce(250).onEach { save(it) }.launchIn(scope)
    }

    val value: LauncherConfig get() = _config.value

    fun update(block: (LauncherConfig) -> LauncherConfig) = _config.update(block)

    fun updateLayout(block: (HomeLayout) -> HomeLayout) = _config.update { it.copy(layout = block(it.layout)) }

    private fun load(): LauncherConfig = try {
        if (file.exists()) migrate(json.decodeFromString(LauncherConfig.serializer(), file.readText())) else LauncherConfig()
    } catch (t: Throwable) {
        Log.e("Scycher", "config load failed", t)
        File(file.parentFile, "scycher.broken.json").let { b -> runCatching { file.copyTo(b, true) } }
        LauncherConfig()
    }

    /** v1 -> v2: iPad look (Liquid Glass, Spotlight on swipe down, Control Center on the right). */
    private fun migrate(c: LauncherConfig): LauncherConfig {
        var x = c
        if (x.version < 2) {
            x = x.copy(
                version = 2,
                gestures = if (x.gestures.swipeDown == GestureAction.Notifications) x.gestures.copy(swipeDown = GestureAction.Search) else x.gestures,
                dock = if (x.dock.style == SurfaceStyle.Glass) x.dock.copy(style = SurfaceStyle.Liquid) else x.dock,
                widgets = if (x.widgets.style == SurfaceStyle.Glass) x.widgets.copy(style = SurfaceStyle.Liquid) else x.widgets,
            )
        }
        return x
    }

    @Synchronized
    private fun save(cfg: LauncherConfig) {
        try {
            val tmp = File(file.parentFile, "scycher.json.tmp")
            tmp.writeText(json.encodeToString(LauncherConfig.serializer(), cfg))
            if (!tmp.renameTo(file)) {
                file.delete(); tmp.renameTo(file)
            }
        } catch (t: Throwable) {
            Log.e("Scycher", "config save failed", t)
        }
    }

    fun export(): String = Json { prettyPrint = true; encodeDefaults = true }
        .encodeToString(LauncherConfig.serializer(), value)

    fun import(text: String): Boolean = try {
        val cfg = json.decodeFromString(LauncherConfig.serializer(), text)
        _config.value = cfg
        true
    } catch (t: Throwable) {
        false
    }

    fun resetLook() = update {
        LauncherConfig(onboarded = true, layout = it.layout, hiddenApps = it.hiddenApps, customLabels = it.customLabels)
    }
}

/* ---------------------------- layout helpers ---------------------------- */

object LayoutOps {

    fun occupied(page: HomePage, cols: Int, rows: Int, ignoreId: Long? = null): Array<BooleanArray> {
        val grid = Array(rows) { BooleanArray(cols) }
        for (it in page.items) {
            if (it.id == ignoreId) continue
            for (yy in it.y until (it.y + it.h).coerceAtMost(rows))
                for (xx in it.x until (it.x + it.w).coerceAtMost(cols))
                    if (yy >= 0 && xx >= 0) grid[yy][xx] = true
        }
        return grid
    }

    fun fits(page: HomePage, cols: Int, rows: Int, x: Int, y: Int, w: Int, h: Int, ignoreId: Long? = null): Boolean {
        if (x < 0 || y < 0 || x + w > cols || y + h > rows) return false
        val g = occupied(page, cols, rows, ignoreId)
        for (yy in y until y + h) for (xx in x until x + w) if (g[yy][xx]) return false
        return true
    }

    fun findFree(page: HomePage, cols: Int, rows: Int, w: Int, h: Int): Pair<Int, Int>? {
        for (y in 0..rows - h) for (x in 0..cols - w) if (fits(page, cols, rows, x, y, w, h)) return x to y
        return null
    }

    fun itemAt(page: HomePage, x: Int, y: Int, ignoreId: Long? = null): HomeItem? =
        page.items.firstOrNull { it.id != ignoreId && x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + it.h }

    /** Places an item on the first page with room (appending a page if needed). */
    fun placeAnywhere(layout: HomeLayout, cols: Int, rows: Int, item: HomeItem, preferPage: Int = 0, newPageId: Long): HomeLayout {
        val order = (layout.pages.indices.drop(preferPage) + layout.pages.indices.take(preferPage))
        for (i in order) {
            val p = layout.pages[i]
            val spot = findFree(p, cols, rows, item.w, item.h) ?: continue
            return layout.copy(pages = layout.pages.toMutableList().also {
                it[i] = p.copy(items = p.items + item.copy(x = spot.first, y = spot.second))
            })
        }
        return layout.copy(pages = layout.pages + HomePage(newPageId, listOf(item.copy(x = 0, y = 0))))
    }

    fun removeItem(layout: HomeLayout, id: Long): HomeLayout = layout.copy(
        pages = layout.pages.map { p -> p.copy(items = p.items.filterNot { it.id == id }) },
        dock = layout.dock.filterNot { it.id == id },
    )

    fun findItem(layout: HomeLayout, id: Long): HomeItem? =
        layout.dock.firstOrNull { it.id == id } ?: layout.pages.firstNotNullOfOrNull { p -> p.items.firstOrNull { it.id == id } }

    fun replaceItem(layout: HomeLayout, item: HomeItem): HomeLayout = layout.copy(
        pages = layout.pages.map { p -> p.copy(items = p.items.map { if (it.id == item.id) item else it }) },
        dock = layout.dock.map { if (it.id == item.id) item else it },
    )

    /** Remove references to apps that were uninstalled. */
    fun prune(layout: HomeLayout, installed: Set<String>): HomeLayout {
        fun keep(i: HomeItem) = i.type != ItemType.App || i.app in installed
        fun fixFolder(i: HomeItem): HomeItem? = if (i.type == ItemType.Folder) {
            val a = i.apps.filter { it in installed }
            when {
                a.isEmpty() -> null
                else -> i.copy(apps = a)
            }
        } else i
        return layout.copy(
            pages = layout.pages.map { p -> p.copy(items = p.items.filter(::keep).mapNotNull(::fixFolder)) },
            dock = layout.dock.filter(::keep).mapNotNull(::fixFolder),
        )
    }

    /** Re-fit items when the grid gets smaller. */
    fun refit(layout: HomeLayout, cols: Int, rows: Int, idGen: () -> Long): HomeLayout {
        var result = layout.copy(pages = layout.pages.map { it.copy(items = emptyList()) })
        val overflow = mutableListOf<HomeItem>()
        layout.pages.forEachIndexed { idx, p ->
            for (item in p.items) {
                val w = item.w.coerceAtMost(cols); val h = item.h.coerceAtMost(rows)
                val sized = item.copy(w = w, h = h)
                val page = result.pages[idx]
                if (fits(page, cols, rows, sized.x, sized.y, w, h)) {
                    result = result.copy(pages = result.pages.toMutableList().also { it[idx] = page.copy(items = page.items + sized) })
                } else {
                    val spot = findFree(page, cols, rows, w, h)
                    if (spot != null) result = result.copy(pages = result.pages.toMutableList().also {
                        it[idx] = page.copy(items = page.items + sized.copy(x = spot.first, y = spot.second))
                    }) else overflow += sized
                }
            }
        }
        for (o in overflow) result = placeAnywhere(result, cols, rows, o, 0, idGen())
        return result
    }
}
