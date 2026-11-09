package dev.scyde.scycher.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.util.LruCache
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

data class AppInfo(
    val key: String,
    val pkg: String,
    val component: ComponentName,
    val label: String,
    val user: UserHandle,
    val category: String,
    val installTime: Long,
    val isSystem: Boolean,
)

class AppRepository(private val context: Context, private val store: ConfigStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val infos = HashMap<String, LauncherActivityInfo>()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps
    private val _map = MutableStateFlow<Map<String, AppInfo>>(emptyMap())
    val map: StateFlow<Map<String, AppInfo>> = _map
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    /** Bumped whenever icons must be re-rendered (shape/pack change, package update). */
    private val _iconGen = MutableStateFlow(0)
    val iconGeneration: StateFlow<Int> = _iconGen

    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private var iconPack: IconPack? = null
    private var iconPackName: String? = null
    private val usage = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) = reload()
        override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) = reload()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        // Re-render icons when icon config changes
        store.config.map { it.icons to maxIconDp(it) }.distinctUntilChanged().drop(1).onEach {
            invalidateIcons()
        }.launchIn(scope)
        reload()
    }

    private fun maxIconDp(c: LauncherConfig) = maxOf(c.icons.sizeDp, c.dock.iconSizeDp, c.library.iconSizeDp)

    private var reloadJob: Job? = null

    fun reload(changedPkg: String? = null) {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            val list = ArrayList<AppInfo>()
            val newInfos = HashMap<String, LauncherActivityInfo>()
            val profiles = try { userManager.userProfiles } catch (_: Exception) { listOf(Process.myUserHandle()) }
            for (user in profiles) {
                val acts = try { launcherApps.getActivityList(null, user) } catch (_: Exception) { emptyList() }
                for (a in acts) {
                    if (a.componentName.packageName == context.packageName) continue
                    val key = keyOf(a.componentName, user)
                    newInfos[key] = a
                    val ai = a.applicationInfo
                    list += AppInfo(
                        key = key,
                        pkg = a.componentName.packageName,
                        component = a.componentName,
                        label = a.label?.toString()?.trim().orEmpty().ifEmpty { a.componentName.packageName },
                        user = user,
                        category = categoryOf(ai),
                        installTime = a.firstInstallTime,
                        isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    )
                }
            }
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            list.sortWith { a, b -> collator.compare(a.label, b.label) }
            synchronized(infos) { infos.clear(); infos.putAll(newInfos) }
            GlyphPack.resetRoles()
            if (changedPkg != null) {
                synchronized(cache) {
                    cache.snapshot().keys.filter { it.startsWith("$changedPkg/") }.forEach { cache.remove(it) }
                }
                _iconGen.value++
            }
            _apps.value = list
            _map.value = list.associateBy { it.key }
            _loaded.value = true
            // prune layout from removed apps
            val keys = list.mapTo(HashSet()) { it.key }
            if (keys.isNotEmpty()) store.updateLayout { LayoutOps.prune(it, keys) }
            preload(list)
        }
    }

    private fun preload(list: List<AppInfo>) = scope.launch(Dispatchers.IO) {
        for (a in list) {
            if (cached(a.key) == null) runCatching { renderIcon(a.key) }
        }
    }

    fun invalidateIcons() {
        synchronized(cache) { cache.evictAll() }
        _iconGen.value++
        preload(_apps.value)
    }

    private fun resolvedIcons(c: IconConfig): IconConfig {
        if (c.style != IconStyle.Auto) return c
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return c.copy(style = if (night) IconStyle.Dark else IconStyle.Default)
    }

    /** Live glyph icons (calendar date, clock hands) re-render once a minute while home is visible. */
    fun refreshLiveIcons() {
        val keys = synchronized(GlyphPack.liveKeys) { GlyphPack.liveKeys.toList() }
        if (keys.isEmpty() || store.value.icons.iconPack != GlyphPack.ID) return
        synchronized(cache) { keys.forEach { cache.remove(it) } }
        _iconGen.value++
    }

    /** Re-render a single icon (e.g. after picking a custom image). */
    fun invalidate(key: String) {
        synchronized(cache) { cache.remove(key) }
        _iconGen.value++
    }

    fun cached(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }

    suspend fun icon(key: String): ImageBitmap? = cached(key) ?: withContext(Dispatchers.IO) { renderIcon(key) }

    private fun renderIcon(key: String): ImageBitmap? {
        val info = synchronized(infos) { infos[key] } ?: return null
        val cfg = store.value
        val density = context.resources.displayMetrics.density
        val sizePx = (maxIconDp(cfg) * density * 1.15f).toInt().coerceIn(48, 320)
        val packName = cfg.icons.iconPack
        if (packName != iconPackName) {
            iconPack = packName?.takeIf { it != GlyphPack.ID }?.let { runCatching { IconPack(context, it) }.getOrNull() }
            iconPackName = packName
        }
        val dpi = context.resources.displayMetrics.densityDpi
        val bmp: Bitmap = try {
            val custom = cfg.customIcons[key]?.let { path -> runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull() }
            if (custom != null) {
                val r = IconRenderer.renderImage(custom, sizePx, cfg.icons)
                custom.recycle()
                r.prepareToDraw()
                return r.asImageBitmap().also { synchronized(cache) { cache.put(key, it) } }
            }
            val glyph = if (packName == GlyphPack.ID) GlyphPack.icon(context, key, info, cfg.icons.themeOthers, dpi) else null
            val packed = if (glyph == null) iconPack?.icon(info.componentName.flattenToString(), dpi) else null
            if (glyph != null) {
                IconRenderer.render(glyph, sizePx, resolvedIcons(cfg.icons))
            } else if (packed != null) {
                Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also {
                    val c = Canvas(it); packed.setBounds(0, 0, sizePx, sizePx); packed.draw(c)
                }
            } else {
                val d = info.getIcon(dpi) ?: return null
                val raw = IconRenderer.render(d, sizePx, resolvedIcons(cfg.icons))
                if (info.user != Process.myUserHandle()) {
                    val badged = context.packageManager.getUserBadgedIcon(android.graphics.drawable.BitmapDrawable(context.resources, raw), info.user)
                    Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also {
                        val c = Canvas(it); badged.setBounds(0, 0, sizePx, sizePx); badged.draw(c)
                    }
                } else raw
            }
        } catch (t: Throwable) {
            Log.w("Scycher", "icon failed $key", t); return null
        }
        bmp.prepareToDraw()
        val img = bmp.asImageBitmap()
        synchronized(cache) { cache.put(key, img) }
        return img
    }

    fun label(key: String): String = store.value.customLabels[key] ?: _map.value[key]?.label ?: ""

    fun keyOf(cn: ComponentName, user: UserHandle): String {
        val base = cn.flattenToString()
        return if (user == Process.myUserHandle()) base else base + "#" + userManager.getSerialNumberForUser(user)
    }

    /* ------------------------------ launching ----------------------------- */

    fun launch(key: String, view: View?, bounds: Rect?, anim: OpenAnimation): Boolean {
        val app = _map.value[key] ?: return false
        val opts: Bundle? = if (view != null && bounds != null && anim != OpenAnimation.System) {
            val loc = IntArray(2); view.getLocationOnScreen(loc)
            val x = bounds.left - loc[0]; val y = bounds.top - loc[1]
            when (anim) {
                OpenAnimation.ScaleUp -> ActivityOptions.makeScaleUpAnimation(view, x, y, bounds.width(), bounds.height())
                else -> ActivityOptions.makeClipRevealAnimation(view, x, y, bounds.width(), bounds.height())
            }.toBundle()
        } else null
        return try {
            launcherApps.startMainActivity(app.component, app.user, bounds, opts)
            recordLaunch(key)
            true
        } catch (t: Throwable) {
            Log.w("Scycher", "launch failed", t)
            try {
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
                recordLaunch(key); true
            } catch (_: Throwable) { false }
        }
    }

    private fun recordLaunch(key: String) {
        val bucket = "h" + (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) / 2) + "|" + key
        usage.edit().putInt(key, usage.getInt(key, 0) + 1).putInt(bucket, usage.getInt(bucket, 0) + 1).apply()
        store.update { c ->
            c.copy(recentApps = (listOf(key) + c.recentApps.filter { it != key }).take(8))
        }
    }

    fun usageCount(key: String) = usage.getInt(key, 0)

    /** How likely the app is right now: launches in this 2-hour window (neighbours count half). */
    fun hourlyScore(key: String): Float {
        val b = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) / 2
        fun at(i: Int) = usage.getInt("h" + ((i + 12) % 12) + "|" + key, 0)
        return at(b) * 2f + (at(b - 1) + at(b + 1)) * 0.75f + usage.getInt(key, 0) * 0.05f
    }

    /** Every app shortcut (for search). */
    fun allShortcuts(): List<ShortcutInfo> {
        if (!hasShortcutPermission()) return emptyList()
        return try {
            val q = LauncherApps.ShortcutQuery().setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
            userManager.userProfiles.flatMap { u -> runCatching { launcherApps.getShortcuts(q, u) ?: emptyList() }.getOrDefault(emptyList()) }
        } catch (_: Throwable) { emptyList() }
    }

    fun appDetails(key: String) {
        val app = _map.value[key] ?: return
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (_: Throwable) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun uninstall(key: String) {
        val app = _map.value[key] ?: return
        @Suppress("DEPRECATION")
        context.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${app.pkg}"))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /* ------------------------------ shortcuts ----------------------------- */

    fun hasShortcutPermission(): Boolean = try { launcherApps.hasShortcutHostPermission() } catch (_: Exception) { false }

    fun shortcuts(key: String): List<ShortcutInfo> {
        val app = _map.value[key] ?: return emptyList()
        if (!hasShortcutPermission()) return emptyList()
        return try {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.pkg)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
            (launcherApps.getShortcuts(q, app.user) ?: emptyList())
                .filter { it.activity == null || it.activity == app.component }
                .sortedWith(compareBy({ if (it.isDynamic) 1 else 0 }, { it.rank }))
                .take(4)
        } catch (_: Throwable) { emptyList() }
    }

    fun shortcutIcon(s: ShortcutInfo): ImageBitmap? = try {
        val d = launcherApps.getShortcutIconDrawable(s, context.resources.displayMetrics.densityDpi) ?: null
        d?.let {
            val size = (40 * context.resources.displayMetrics.density).toInt()
            val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(b); it.setBounds(0, 0, size, size); it.draw(c); b.asImageBitmap()
        }
    } catch (_: Throwable) { null }

    fun startShortcut(s: ShortcutInfo, bounds: Rect?) {
        try { launcherApps.startShortcut(s, bounds, null) } catch (t: Throwable) { Log.w("Scycher", "shortcut", t) }
    }

    private fun categoryOf(ai: ApplicationInfo): String {
        val p = ai.packageName
        return when (ai.category) {
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_AUDIO -> "Music & Audio"
            ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
            ApplicationInfo.CATEGORY_IMAGE -> "Photo & Video"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_NEWS -> "News & Reading"
            ApplicationInfo.CATEGORY_MAPS -> "Travel & Maps"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            else -> when {
                listOf("chrome", "browser", "firefox", "opera", "brave", "edge").any { p.contains(it) } -> "Internet"
                listOf("mail", "gm", "messag", "whatsapp", "telegram", "signal", "viber", "discord", "mms").any { p.contains(it) } -> "Social"
                listOf("music", "spotify", "audio", "podcast", "deezer", "tidal").any { p.contains(it) } -> "Music & Audio"
                listOf("youtube", "netflix", "video", "player", "twitch", "hbo", "disney", "tv").any { p.contains(it) } -> "Entertainment"
                listOf("camera", "gallery", "photo", "lightroom", "snapseed").any { p.contains(it) } -> "Photo & Video"
                listOf("doc", "office", "notes", "drive", "calendar", "keep", "notion", "sheets", "slides", "pdf").any { p.contains(it) } -> "Productivity"
                listOf("maps", "uber", "bolt", "booking", "airbnb", "waze").any { p.contains(it) } -> "Travel & Maps"
                listOf("bank", "pay", "wallet", "revolut", "finance").any { p.contains(it) } -> "Finance"
                listOf("shop", "amazon", "aliexpress", "temu", "ebay", "store", "market").any { p.contains(it) } -> "Shopping"
                ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 -> "Utilities"
                else -> "Other"
            }
        }
    }
}
