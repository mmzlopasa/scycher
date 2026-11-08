package dev.scyde.scycher.data

import android.content.Intent
import android.provider.Settings
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.Normalizer
import kotlin.math.pow

/** Small, safe expression evaluator for the search calculator: + - * / % ^ ( ) and decimals. */
object Calculator {
    private val allowed = Regex("^[0-9+\\-*/×÷%^().,\\s]+$")
    private val hasOperator = Regex("[+\\-*/×÷%^]")

    fun looksLikeMath(q: String): Boolean {
        val t = q.trim()
        return t.length >= 3 && allowed.matches(t) && hasOperator.containsMatchIn(t.drop(1)) && t.any { it.isDigit() }
    }

    fun evaluate(q: String): String? = try {
        val p = Parser(q.replace('×', '*').replace('÷', '/').replace(',', '.').replace(" ", ""))
        val v = p.expr()
        if (p.pos != p.s.length || v.isNaN() || v.isInfinite()) null else format(v)
    } catch (_: Throwable) { null }

    private fun format(v: Double): String {
        if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
        return BigDecimal(v).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()
    }

    private class Parser(val s: String) {
        var pos = 0
        fun peek() = if (pos < s.length) s[pos] else '\u0000'
        fun expr(): Double {
            var v = term()
            while (true) when (peek()) {
                '+' -> { pos++; v += term() }
                '-' -> { pos++; v -= term() }
                else -> return v
            }
        }
        fun term(): Double {
            var v = factor()
            while (true) when (peek()) {
                '*' -> { pos++; v *= factor() }
                '/' -> { pos++; v /= factor() }
                '%' -> { pos++; v = if (peek().isDigit() || peek() == '(') v % factor() else v / 100.0 }
                else -> return v
            }
        }
        fun factor(): Double {
            val b = unary()
            return if (peek() == '^') { pos++; b.pow(factor()) } else b
        }
        fun unary(): Double = when (peek()) {
            '-' -> { pos++; -unary() }
            '+' -> { pos++; unary() }
            else -> primary()
        }
        fun primary(): Double {
            if (peek() == '(') {
                pos++; val v = expr()
                if (peek() != ')') error("paren"); pos++; return v
            }
            val start = pos
            while (peek().isDigit() || peek() == '.') pos++
            if (start == pos) error("number")
            return s.substring(start, pos).toDouble()
        }
    }
}

data class SettingShortcut(val label: String, val keywords: List<String>, val action: String)

/** System settings that can be found from the search field. */
object SettingsIndex {
    val all = listOf(
        SettingShortcut("Wi‑Fi", listOf("wifi", "wi-fi", "wlan", "internet", "mreža"), Settings.ACTION_WIFI_SETTINGS),
        SettingShortcut("Bluetooth", listOf("bluetooth", "bt", "slušalice", "headphones"), Settings.ACTION_BLUETOOTH_SETTINGS),
        SettingShortcut("Display & brightness", listOf("display", "brightness", "zaslon", "svjetlina", "dark mode"), Settings.ACTION_DISPLAY_SETTINGS),
        SettingShortcut("Sound & vibration", listOf("sound", "volume", "zvuk", "vibration", "ringtone"), Settings.ACTION_SOUND_SETTINGS),
        SettingShortcut("Battery", listOf("battery", "baterija", "power", "saver"), Intent.ACTION_POWER_USAGE_SUMMARY),
        SettingShortcut("Apps", listOf("apps", "aplikacije", "applications", "manage"), Settings.ACTION_APPLICATION_SETTINGS),
        SettingShortcut("Storage", listOf("storage", "pohrana", "memory", "space"), Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SettingShortcut("Notifications", listOf("notifications", "obavijesti"), "android.settings.ALL_APPS_NOTIFICATION_SETTINGS"),
        SettingShortcut("Location", listOf("location", "gps", "lokacija"), Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        SettingShortcut("Security & privacy", listOf("security", "sigurnost", "privacy", "lock", "password"), Settings.ACTION_SECURITY_SETTINGS),
        SettingShortcut("Accessibility", listOf("accessibility", "pristupačnost"), Settings.ACTION_ACCESSIBILITY_SETTINGS),
        SettingShortcut("Developer options", listOf("developer", "razvoj", "adb", "usb debugging"), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        SettingShortcut("Date & time", listOf("date", "time", "datum", "vrijeme", "clock"), Settings.ACTION_DATE_SETTINGS),
        SettingShortcut("Language & input", listOf("language", "jezik", "keyboard", "tipkovnica"), Settings.ACTION_LOCALE_SETTINGS),
        SettingShortcut("Mobile & data usage", listOf("data", "podaci", "usage", "hotspot"), Settings.ACTION_DATA_ROAMING_SETTINGS),
        SettingShortcut("Airplane mode", listOf("airplane", "flight", "zrakoplov"), Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        SettingShortcut("Do not disturb", listOf("dnd", "do not disturb", "ne ometaj", "focus"), "android.settings.ZEN_MODE_SETTINGS"),
        SettingShortcut("NFC", listOf("nfc", "tap", "pay"), Settings.ACTION_NFC_SETTINGS),
        SettingShortcut("Default apps", listOf("default", "zadane", "home app", "launcher"), Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        SettingShortcut("All settings", listOf("settings", "postavke", "system"), Settings.ACTION_SETTINGS),
    )

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun search(q: String): List<SettingShortcut> {
        val n = norm(q.trim())
        if (n.length < 2) return emptyList()
        return all.filter { s -> norm(s.label).contains(n) || s.keywords.any { norm(it).startsWith(n) || (n.length >= 3 && norm(it).contains(n)) } }.take(4)
    }
}
