package dev.scyde.scycher.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.SearchEngine
import dev.scyde.scycher.data.Security
import dev.scyde.scycher.data.SecurityConfig
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.PinPad
import dev.scyde.scycher.ui.home.rememberAppIcon

/* ------------------------------------- security ------------------------------------- */

@Composable
fun SecuritySettings() {
    val ctx = LocalContext.current
    val sec = LocalConfig.current.security
    val apps by ScycherApp.instance.apps.apps.collectAsState()
    fun set(block: (SecurityConfig) -> SecurityConfig) = update { it.copy(security = block(it.security)) }
    // 0 = idle, 1 = enter new, 2 = confirm new, 3 = verify old (for change/remove)
    var step by remember { mutableStateOf(0) }
    var first by remember { mutableStateOf("") }
    var afterVerify by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (step != 0) {
        Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
            when (step) {
                3 -> PinPad("Enter current PIN", onCancel = { step = 0 }) { p ->
                    val ok = Security.verify(sec, p)
                    if (ok) { val a = afterVerify; afterVerify = null; step = 0; a?.invoke() }
                    ok
                }
                1 -> PinPad("New PIN", "4–8 digits, then OK", onCancel = { step = 0 }) { p -> first = p; step = 2; true }
                else -> PinPad("Confirm PIN", onCancel = { step = 0 }) { p ->
                    val ok = p == first
                    if (ok) { set { it.copy(pinHash = Security.hash(p)) }; Security.markUnlocked(); step = 0 }
                    ok
                }
            }
        }
        return
    }

    Page("Security & app lock") {
        InfoText("Protect apps, hidden apps and Scycher's settings with a PIN. Fingerprint / face unlock is used when available.")
        Group {
            if (sec.pinHash == null) {
                NavRow("Set PIN", Icons.Rounded.Lock, Color(0xFF30D158)) { step = 1 }
            } else {
                NavRow("Change PIN", Icons.Rounded.Lock, Color(0xFF30D158)) { afterVerify = { step = 1 }; step = 3 }
                Divider()
                NavRow("Remove PIN", null) {
                    afterVerify = { set { it.copy(pinHash = null, protectedApps = emptySet(), lockSettings = false) } }
                    step = 3
                }
            }
        }
        if (sec.pinHash != null) {
            Group {
                SwitchRow("Use fingerprint / face", sec.biometric, if (Security.biometricAvailable(ctx)) null else "Not available on this device") { v -> set { it.copy(biometric = v) } }
                Divider()
                SwitchRow("Lock hidden apps", sec.lockHiddenApps, "PIN needed to view hidden apps in the App Library") { v -> set { it.copy(lockHiddenApps = v) } }
                Divider()
                SwitchRow("Lock Scycher settings", sec.lockSettings) { v -> set { it.copy(lockSettings = v) } }
                Divider()
                IntSlider("Stay unlocked for", sec.relockSeconds, 0..300, " s") { v -> set { it.copy(relockSeconds = v) } }
            }
            Group("Locked apps", footer = "Tip: long-press any app › Lock App.") {
                apps.forEachIndexed { i, a ->
                    val on = a.key in sec.protectedApps
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            set { it.copy(protectedApps = if (on) it.protectedApps - a.key else it.protectedApps + a.key) }
                        }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val img = rememberAppIcon(a.key)
                        if (img != null) Image(img, null, Modifier.size(34.dp)) else Spacer(Modifier.size(34.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(a.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (on) Icon(Icons.Rounded.Lock, null, tint = Color(0xFF30D158))
                    }
                    if (i < apps.lastIndex) Divider()
                }
            }
        }
    }
}

/** Shown instead of settings while they're locked. */
@Composable
fun SettingsGate(onUnlocked: () -> Unit) {
    val sec = LocalConfig.current.security
    val nav = LocalNav.current
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        PinPad(
            "Settings",
            onBiometric = if (sec.biometric && Security.biometricAvailable(ctx)) {
                { Security.authenticate(nav, "Settings", onUnlocked) {} }
            } else null,
            onCancel = { nav.finish() },
        ) { p -> val ok = Security.verify(sec, p); if (ok) { Security.markUnlocked(); onUnlocked() }; ok }
    }
}

/* -------------------------------------- search -------------------------------------- */

@Composable
fun SearchSettings() {
    val s = LocalConfig.current.search
    fun set(block: (dev.scyde.scycher.data.SearchConfig) -> dev.scyde.scycher.data.SearchConfig) = update { it.copy(search = block(it.search)) }
    Page("Search") {
        InfoText("Swipe up (or use a Search widget) and just type: apps, app shortcuts, system settings and maths all show up instantly.")
        Group("Results") {
            SwitchRow("Calculator", s.calculator, "Type 12*7+3 and get the answer") { v -> set { it.copy(calculator = v) } }
            Divider()
            SwitchRow("System settings", s.settingsResults, "Wi‑Fi, Bluetooth, battery…") { v -> set { it.copy(settingsResults = v) } }
            Divider()
            SwitchRow("App shortcuts", s.shortcutResults, "Actions inside apps, e.g. “New tab”") { v -> set { it.copy(shortcutResults = v) } }
            Divider()
            SwitchRow("Web, YouTube, Maps & Play Store", s.showProviders) { v -> set { it.copy(showProviders = v) } }
        }
        Group("Web search engine") {
            ChoiceRow("Engine", SearchEngine.entries.toList(), s.engine, { it.label }) { v -> set { it.copy(engine = v) } }
        }
    }
}
