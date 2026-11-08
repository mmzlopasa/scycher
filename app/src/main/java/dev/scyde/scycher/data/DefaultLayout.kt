package dev.scyde.scycher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings

/** Builds a tasteful first-run layout: widgets on page one and the essentials in the dock. */
object DefaultLayout {

    fun build(context: Context, apps: List<AppInfo>, cols: Int, rows: Int, id: () -> Long): HomeLayout {
        val pm = context.packageManager
        val byPkg = apps.groupBy { it.pkg }
        fun pkgFor(i: Intent): String? = try {
            pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it != "android" }
        } catch (_: Exception) { null }
        fun keyForPkg(p: String?) = p?.let { byPkg[it]?.firstOrNull()?.key }
        fun keyForCategory(cat: String) = keyForPkg(pkgFor(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat)))

        val dockKeys = listOfNotNull(
            keyForPkg(pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://scyde.dev")))),
            keyForCategory(Intent.CATEGORY_APP_MESSAGING),
            keyForCategory(Intent.CATEGORY_APP_EMAIL),
            keyForPkg(pkgFor(Intent(MediaStore.ACTION_IMAGE_CAPTURE))),
            keyForCategory(Intent.CATEGORY_APP_GALLERY),
            keyForCategory(Intent.CATEGORY_APP_MUSIC),
            keyForPkg("com.android.vending"),
            keyForPkg(pkgFor(Intent(Settings.ACTION_SETTINGS))),
        ).distinct().take(8)

        val dock = dockKeys.map { HomeItem(id(), ItemType.App, app = it) }

        val items = mutableListOf<HomeItem>()
        val c = cols.coerceAtLeast(4)
        if (rows >= 4) {
            items += HomeItem(id(), ItemType.Widget, 0, 0, 4.coerceAtMost(c), 1, builtin = BuiltinKind.Greeting)
            items += HomeItem(id(), ItemType.Widget, 0, 1, 2, 2, builtin = BuiltinKind.Weather)
            items += HomeItem(id(), ItemType.Widget, 2, 1, 2, 2, builtin = BuiltinKind.Calendar)
            if (c >= 6) {
                items += HomeItem(id(), ItemType.Widget, 4, 0, 2, 2, builtin = BuiltinKind.ClockAnalog)
                items += HomeItem(id(), ItemType.Widget, 4, 2, 2, 2, builtin = BuiltinKind.NowPlaying)
            }
        }
        val popularHints = listOf("youtube", "whatsapp", "spotify", "instagram", "maps", "drive", "notes", "calendar", "netflix", "telegram", "files", "clock")
        val pool = apps.filter { a -> a.key !in dockKeys }
        val chosen = popularHints.mapNotNull { h -> pool.firstOrNull { it.pkg.contains(h) } }.distinctBy { it.key }.toMutableList()
        for (a in pool) if (chosen.size < c && a !in chosen) chosen += a
        val appRow = (rows - 1).coerceAtLeast(0)
        chosen.take(c).forEachIndexed { i, a -> items += HomeItem(id(), ItemType.App, i, appRow, app = a.key) }

        return HomeLayout(pages = listOf(HomePage(id(), items), HomePage(id())), dock = dock)
    }
}
