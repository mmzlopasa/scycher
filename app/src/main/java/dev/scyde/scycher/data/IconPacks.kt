package dev.scyde.scycher.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val pkg: String, val label: String)

/** ADW / Nova compatible icon pack support (appfilter.xml). */
class IconPack(private val context: Context, val pkg: String) {
    private val res: Resources = context.packageManager.getResourcesForApplication(pkg)
    private val map = HashMap<String, String>()

    init {
        parse()
    }

    @SuppressLint("DiscouragedApi")
    private fun parse() {
        val parser: XmlPullParser? = run {
            val id = res.getIdentifier("appfilter", "xml", pkg)
            if (id != 0) res.getXml(id) else try {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("appfilter.xml"), "utf-8")
                }
            } catch (_: Exception) { null }
        }
        parser ?: return
        try {
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (comp != null && drawable != null && comp.startsWith("ComponentInfo{")) {
                        val key = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                        map.putIfAbsent(key, drawable)
                    }
                }
                ev = parser.next()
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("DiscouragedApi")
    fun icon(componentKey: String, density: Int): Drawable? {
        val name = map[componentKey] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return try { res.getDrawableForDensity(id, density, null) } catch (_: Exception) { null }
    }

    companion object {
        fun list(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            val found = LinkedHashMap<String, IconPackInfo>()
            for (action in listOf("org.adw.launcher.THEMES", "com.gau.go.launcherex.theme", "com.novalauncher.THEME")) {
                @Suppress("DEPRECATION")
                val list = pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
                for (ri in list) {
                    val p = ri.activityInfo.packageName
                    found.getOrPut(p) { IconPackInfo(p, ri.loadLabel(pm).toString()) }
                }
            }
            return found.values.sortedBy { it.label.lowercase() }
        }
    }
}
