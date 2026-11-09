package dev.scyde.scycher.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.BuildConfig
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.BadgeStyle
import dev.scyde.scycher.data.FontChoice
import dev.scyde.scycher.data.GeoResult
import dev.scyde.scycher.data.GestureAction
import dev.scyde.scycher.data.GestureConfig
import dev.scyde.scycher.data.HomeLayout
import dev.scyde.scycher.data.IconConfig
import dev.scyde.scycher.data.IconPack
import dev.scyde.scycher.data.IconRenderer
import dev.scyde.scycher.data.IconShape
import dev.scyde.scycher.data.IconStyle
import dev.scyde.scycher.data.LibrarySort
import dev.scyde.scycher.data.LibraryStyle
import dev.scyde.scycher.data.OpenAnimation
import dev.scyde.scycher.data.PageIndicatorStyle
import dev.scyde.scycher.data.PageTransition
import dev.scyde.scycher.data.SurfaceStyle
import dev.scyde.scycher.data.WallpaperMode
import dev.scyde.scycher.gestures.ScycherGestureService
import dev.scyde.scycher.gestures.SystemActions
import dev.scyde.scycher.notifications.ScycherNotificationListener
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.toColor
import dev.scyde.scycher.ui.home.rememberAppIcon
import kotlinx.coroutines.launch
import java.time.ZoneId

/* --------------------------------------- main --------------------------------------- */

@Composable
fun MainScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val tick by SettingsActivity.resumeTick.collectAsState()
    val isDefault = remember(tick) { SystemActions.isDefaultLauncher(ctx) }
    Page("Settings", back = true) {
        // Hero
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1B1340), Color(0xFF3A1C71), Color(0xFF0B0B12))))
                .padding(22.dp)
        ) {
            Text("SCYCHER", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text("SCYDE Launcher · v${BuildConfig.VERSION_NAME}", color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            if (!isDefault) Text(
                "Set as default home app", color = Color(0xFFB9A7FF), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                }
            ) else Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF30D158), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scycher is your home screen", color = Color(0xFF30D158), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Group {
            NavRow("Navigation gestures", Icons.Rounded.Swipe, Color(0xFF0A84FF)) { nav.nav += "gestures" }
            Divider()
            NavRow("Home screen gestures", Icons.Rounded.Gesture, Color(0xFF5E5CE6)) { nav.nav += "homegestures" }
        }
        Group {
            NavRow("Home screen", Icons.Rounded.Home, Color(0xFFFF9F0A)) { nav.nav += "home" }
            Divider()
            NavRow("Icons", Icons.Rounded.Apps, Color(0xFF30D158)) { nav.nav += "icons" }
            Divider()
            NavRow("Dock", Icons.Rounded.Dock, Color(0xFF64D2FF)) { nav.nav += "dock" }
            Divider()
            NavRow("Widgets", Icons.Rounded.Widgets, Color(0xFFFF375F)) { nav.nav += "widgets" }
            Divider()
            NavRow("App Library", Icons.Rounded.GridView, Color(0xFFBF5AF2)) { nav.nav += "library" }
            Divider()
            NavRow("Appearance & wallpaper", Icons.Rounded.Palette, Color(0xFFFF453A)) { nav.nav += "appearance" }
        }
        Group {
            NavRow("Security & app lock", Icons.Rounded.Lock, Color(0xFF30D158)) { nav.nav += "security" }
            Divider()
            NavRow("Search", Icons.Rounded.Search, Color(0xFF0A84FF)) { nav.nav += "search" }
            Divider()
            NavRow("Hidden apps", Icons.Rounded.VisibilityOff, Color(0xFF8E8E93)) { nav.nav += "hidden" }
            Divider()
            val notif = remember(tick) { ScycherNotificationListener.isEnabled(ctx) }
            NavRow("Badges & Now Playing", Icons.Rounded.Notifications, Color(0xFFFF3B30), if (notif) "On" else "Off") {
                SystemActions.openNotificationAccess(ctx)
            }
            Divider()
            NavRow("Backup & restore", Icons.Rounded.Backup, Color(0xFF636366)) { nav.nav += "backup" }
            Divider()
            NavRow("About", Icons.Rounded.Info, Color(0xFF636366)) { nav.nav += "about" }
        }
    }
}

/* --------------------------------------- gestures --------------------------------------- */

private fun copy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("adb", text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}

@Composable
private fun CommandBox(cmd: String) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0E0E10)).clickable { copy(ctx, cmd) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(cmd, color = Color(0xFF64D2FF), fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ContentCopy, null, tint = Sub, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, okText: String = "Active", offText: String = "Not active") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = dev.scyde.scycher.ui.common.TypeScale.Body, modifier = Modifier.weight(1f))
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null, tint = if (ok) Color(0xFF30D158) else Color(0xFFFF9F0A), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (ok) okText else offText, color = if (ok) Color(0xFF30D158) else Color(0xFFFF9F0A), fontSize = 15.sp)
    }
}

@Composable
fun GestureSettings() {
    val ctx = LocalContext.current
    val cfg = LocalConfig.current
    val n = cfg.nav
    val tick by SettingsActivity.resumeTick.collectAsState()
    var refresh by remember { mutableStateOf(0) }
    val canWrite = remember(tick, refresh) { SystemActions.canWriteSecure(ctx) }
    val hyper = remember(tick, refresh) { SystemActions.hyperGesturesEnabled(ctx) }
    val lineHidden = remember(tick, refresh) { SystemActions.gestureLineHidden(ctx) }
    val access = remember(tick) { SystemActions.isAccessibilityEnabled(ctx) }
    val running by ScycherGestureService.running.collectAsState()
    fun nav(block: (dev.scyde.scycher.data.NavGestureConfig) -> dev.scyde.scycher.data.NavGestureConfig) = update { it.copy(nav = block(it.nav)) }

    Page("Navigation gestures") {
        InfoText(
            "HyperOS turns off full-screen gestures as soon as a third-party launcher is the default. " +
                "Scycher gives you two ways to get them back. Option A is the real system gesture navigation (best). " +
                "Option B works without a computer."
        )

        Group("Option A · HyperOS system gestures", footer = "Swipe up = Home (opens Scycher) · swipe up & hold = Recents · swipe in from the side = Back. Uses the original HyperOS animations. If swipe up sometimes stays in an app, turn on “Always return Home”.") {
            StatusRow("Permission (one-time ADB)", canWrite, "Granted", "Needed")
            Divider()
            StatusRow("Full-screen gestures", hyper, "On", "Off")
            if (canWrite) {
                Divider()
                SwitchRow("Use HyperOS gestures with Scycher", hyper) {
                    if (!SystemActions.setHyperGestures(ctx, it)) Toast.makeText(ctx, "Couldn't change setting", Toast.LENGTH_SHORT).show()
                    refresh++
                }
                Divider()
                SwitchRow("Hide gesture line", lineHidden, "The pill at the bottom of the screen") {
                    SystemActions.setHyperGestures(ctx, hyper, it); refresh++
                }
                Divider()
                SwitchRow(
                    "Always return Home", n.homeRescue && access,
                    "Fixes swipe up not going home in some apps (e.g. Android Settings). Uses Scycher Gestures (Accessibility)."
                ) { v ->
                    nav { it.copy(homeRescue = v, enabled = if (v) false else it.enabled) }
                    if (v && !access) {
                        Toast.makeText(ctx, "Turn on “Scycher Gestures”, then come back", Toast.LENGTH_LONG).show()
                        SystemActions.openAccessibilitySettings(ctx)
                    }
                }
            } else {
                Divider()
                InfoText(
                    "1. On the tablet open Settings › About tablet and tap “OS version” 7 times.\n" +
                        "2. Settings › Additional settings › Developer options: turn on “USB debugging”.\n" +
                        "3. Connect the tablet to a computer with Android platform-tools and run (tap to copy):"
                )
                CommandBox(SystemActions.ADB_GRANT)
                InfoText("4. Return here and turn on “Use HyperOS gestures with Scycher”. You only do this once — it survives reboots.\n\nPrefer one command? This turns gestures on directly:")
                CommandBox(SystemActions.ADB_DIRECT)
                InfoText("No computer? Use “Wireless debugging” in Developer options with an ADB app on another phone (e.g. LADB / Bugjaeger) and run the same command.")
            }
        }

        Group("Option B · Scycher Gestures", footer = "Thin invisible strips on the edges turn swipes into Home, Back and Recents. If HyperOS stops the service, set Scycher's battery saver to “No restrictions” and enable Autostart.") {
            StatusRow("Accessibility service", access && running, "Running", if (access) "Starting…" else "Off")
            Divider()
            NavRow(if (access) "Accessibility settings" else "Turn on Scycher Gestures", null) { SystemActions.openAccessibilitySettings(ctx) }
            Divider()
            NavRow("Battery & Autostart", null) {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                }
            }
            Divider()
            SwitchRow("Enable edge gestures", n.enabled) { v -> nav { it.copy(enabled = v) } }
        }
        if (n.enabled) {
            Group("Bottom edge") {
                SwitchRow("Swipe up for Home", n.bottomHome) { v -> nav { it.copy(bottomHome = v) } }
                Divider()
                SwitchRow("Swipe up & hold for Recents", n.holdForRecents) { v -> nav { it.copy(holdForRecents = v) } }
                Divider()
                SwitchRow("Show handle", n.showHandle) { v -> nav { it.copy(showHandle = v) } }
                Divider()
                IntSlider("Handle width", n.handleWidthDp, 40..300, " dp") { v -> nav { it.copy(handleWidthDp = v) } }
                Divider()
                PercentSlider("Handle opacity", n.handleOpacity) { v -> nav { it.copy(handleOpacity = v) } }
                Divider()
                ColorRow("Handle color", n.handleColor) { v -> nav { it.copy(handleColor = v) } }
                Divider()
                IntSlider("Touch area height", n.bottomHeightDp, 8..48, " dp") { v -> nav { it.copy(bottomHeightDp = v) } }
                Divider()
                IntSlider("Touch area width", n.bottomWidthPercent, 20..100, "%") { v -> nav { it.copy(bottomWidthPercent = v) } }
            }
            Group("Side edges") {
                SwitchRow("Swipe in for Back", n.sideBack) { v -> nav { it.copy(sideBack = v) } }
                Divider()
                SwitchRow("Left edge", n.backLeft) { v -> nav { it.copy(backLeft = v) } }
                Divider()
                SwitchRow("Right edge", n.backRight) { v -> nav { it.copy(backRight = v) } }
                Divider()
                SwitchRow("Back arrow animation", n.backIndicator) { v -> nav { it.copy(backIndicator = v) } }
                Divider()
                IntSlider("Edge width", n.edgeWidthDp, 6..40, " dp") { v -> nav { it.copy(edgeWidthDp = v) } }
                Divider()
                IntSlider("Edge height", n.edgeHeightPercent, 20..100, "%") { v -> nav { it.copy(edgeHeightPercent = v) } }
            }
            Group("Feel") {
                SliderRow("Sensitivity", n.sensitivity, 0.5f..2f, format = { "%.1f×".format(it) }) { v -> nav { it.copy(sensitivity = v) } }
                Divider()
                SwitchRow("Haptic feedback", n.vibrate) { v -> nav { it.copy(vibrate = v) } }
                Divider()
                SwitchRow("Pass taps through", n.forwardTaps, "Taps on the strips reach the app below") { v -> nav { it.copy(forwardTaps = v) } }
            }
        }
    }
}

@Composable
fun HomeGestureSettings() {
    val g = LocalConfig.current.gestures
    fun set(block: (GestureConfig) -> GestureConfig) = update { it.copy(gestures = block(it.gestures)) }
    var picking by remember { mutableStateOf<String?>(null) }
    Page("Home gestures") {
        InfoText("What happens when you swipe on an empty part of the home screen. Lock screen, recents and screenshots need Scycher Gestures (Accessibility) turned on.")
        Group {
            GestureRow("Swipe up", g.swipeUp, g.swipeUpApp, { a -> set { it.copy(swipeUp = a) } }) { picking = "swipeUp" }
            Divider()
            GestureRow("Swipe down (middle & left)", g.swipeDown, g.swipeDownApp, { a -> set { it.copy(swipeDown = a) } }) { picking = "swipeDown" }
            Divider()
            GestureRow("Swipe down (right side)", g.swipeDownRight, g.swipeDownRightApp, { a -> set { it.copy(swipeDownRight = a) } }) { picking = "swipeDownRight" }
            Divider()
            GestureRow("Double tap", g.doubleTap, g.doubleTapApp, { a -> set { it.copy(doubleTap = a) } }) { picking = "doubleTap" }
            Divider()
            GestureRow("Two-finger swipe down", g.twoFingerDown, g.twoFingerDownApp, { a -> set { it.copy(twoFingerDown = a) } }) { picking = "twoFingerDown" }
            Divider()
            GestureRow("Two-finger swipe up", g.twoFingerUp, g.twoFingerUpApp, { a -> set { it.copy(twoFingerUp = a) } }) { picking = "twoFingerUp" }
            Divider()
            GestureRow("Pinch in", g.pinchIn, g.pinchInApp, { a -> set { it.copy(pinchIn = a) } }) { picking = "pinchIn" }
            Divider()
            GestureRow("Pinch out", g.pinchOut, g.pinchOutApp, { a -> set { it.copy(pinchOut = a) } }) { picking = "pinchOut" }
        }
        Group(footer = "Like iPad: swipe right on the first page to see a column of widgets.") {
            SwitchRow("Today View", g.todayView) { v -> set { it.copy(todayView = v) } }
            Divider()
            SwitchRow("Haptic feedback", g.haptics) { v -> set { it.copy(haptics = v) } }
        }
    }
    picking?.let { which ->
        AppPicker(onDismiss = { picking = null }) { key ->
            set {
                when (which) {
                    "swipeUp" -> it.copy(swipeUpApp = key)
                    "swipeDown" -> it.copy(swipeDownApp = key)
                    "doubleTap" -> it.copy(doubleTapApp = key)
                    "twoFingerDown" -> it.copy(twoFingerDownApp = key)
                    "twoFingerUp" -> it.copy(twoFingerUpApp = key)
                    "pinchOut" -> it.copy(pinchOutApp = key)
                    "swipeDownRight" -> it.copy(swipeDownRightApp = key)
                    else -> it.copy(pinchInApp = key)
                }
            }
            picking = null
        }
    }
}

@Composable
private fun GestureRow(title: String, action: GestureAction, app: String?, onAction: (GestureAction) -> Unit, onPickApp: () -> Unit) {
    val label = if (action == GestureAction.LaunchApp) ScycherApp.instance.apps.label(app ?: "").ifEmpty { "Choose app" } else null
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
        Row {
            Text(title, color = Color.White, style = dev.scyde.scycher.ui.common.TypeScale.Body, modifier = Modifier.weight(1f))
            if (label != null) Text(label, color = LocalConfig.current.look.accentColor.toColor(), fontSize = 15.sp, modifier = Modifier.clickable { onPickApp() })
        }
        dev.scyde.scycher.ui.common.Chips(GestureAction.entries.toList(), action, { it.label }) {
            onAction(it); if (it == GestureAction.LaunchApp) onPickApp()
        }
    }
}

@Composable
fun AppPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val apps by ScycherApp.instance.apps.apps.collectAsState()
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
        title = { Text("Choose app") },
        containerColor = Card,
        text = {
            Column {
                BasicTextField(
                    q, { q = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF2C2C2E)).padding(12.dp)
                )
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    for (a in apps.filter { q.isBlank() || it.label.contains(q, true) }) {
                        Row(Modifier.fillMaxWidth().clickable { onPick(a.key) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            val img = rememberAppIcon(a.key)
                            if (img != null) Image(img, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    )
}

/* --------------------------------------- home / icons --------------------------------------- */

@Composable
fun HomeScreenSettings() {
    val h = LocalConfig.current.home
    fun set(block: (dev.scyde.scycher.data.HomeConfig) -> dev.scyde.scycher.data.HomeConfig) = update { it.copy(home = block(it.home)) }
    Page("Home screen") {
        Group("Grid") {
            IntSlider("Columns", h.columns, 3..10) { v -> set { it.copy(columns = v) } }
            Divider()
            IntSlider("Rows", h.rows, 3..9) { v -> set { it.copy(rows = v) } }
            Divider()
            IntSlider("Side padding", h.horizontalPaddingDp, 0..120, " dp") { v -> set { it.copy(horizontalPaddingDp = v) } }
            Divider()
            IntSlider("Top padding", h.topPaddingDp, 0..80, " dp") { v -> set { it.copy(topPaddingDp = v) } }
        }
        Group("Labels") {
            SwitchRow("Show app names", h.showLabels) { v -> set { it.copy(showLabels = v) } }
            Divider()
            SliderRow("Text size", h.labelSizeSp, 9f..20f, format = { "%.1f sp".format(it) }) { v -> set { it.copy(labelSizeSp = v) } }
            Divider()
            IntSlider("Weight", h.labelWeight, 100..900) { v -> set { it.copy(labelWeight = (v / 100) * 100) } }
            Divider()
            IntSlider("Max lines", h.labelMaxLines, 1..2) { v -> set { it.copy(labelMaxLines = v) } }
            Divider()
            SwitchRow("Text shadow", h.labelShadow) { v -> set { it.copy(labelShadow = v) } }
            Divider()
            SwitchRow("Capital letters", h.labelUppercase) { v -> set { it.copy(labelUppercase = v) } }
            Divider()
            SwitchRow("Mark new apps", h.newAppDot, "Blue dot next to apps you installed recently and haven't opened") { v -> set { it.copy(newAppDot = v) } }
            Divider()
            ColorRow("Text color", h.labelColor) { v -> set { it.copy(labelColor = v) } }
        }
        Group("Pages") {
            ChoiceRow("Page transition", PageTransition.entries.toList(), h.pageTransition) { v -> set { it.copy(pageTransition = v) } }
            Divider()
            ChoiceRow("Page indicator", PageIndicatorStyle.entries.toList(), h.pageIndicator) { v -> set { it.copy(pageIndicator = v) } }
            Divider()
            SwitchRow("Home button returns to first page", h.homeButtonGoesToFirstPage) { v -> set { it.copy(homeButtonGoesToFirstPage = v) } }
            Divider()
            SwitchRow("Zoom-in when returning home", h.returnAnimation) { v -> set { it.copy(returnAnimation = v) } }
            Divider()
            SwitchRow("Lock layout", h.lockLayout, "Prevents accidental dragging") { v -> set { it.copy(lockLayout = v) } }
            Divider()
            SwitchRow("Haptic page tick", h.pageHaptics, "A light tick when a page snaps into place") { v -> set { it.copy(pageHaptics = v) } }
        }
    }
}

@Composable
fun IconSettings() {
    val ctx = LocalContext.current
    val c = LocalConfig.current.icons
    fun set(block: (IconConfig) -> IconConfig) = update { it.copy(icons = block(it.icons)) }
    val packs = remember { IconPack.list(ctx) }
    Page("Icons") {
        IconPreview(c)
        Group("Shape & style") {
            ChoiceRow("Shape", IconShape.entries.toList(), c.shape) { v -> set { it.copy(shape = v) } }
            Divider()
            ChoiceRow("Style", IconStyle.entries.toList(), c.style) { v -> set { it.copy(style = v) } }
            if (c.style == IconStyle.Tinted) {
                Divider()
                ColorRow("Tint color", c.tintColor) { v -> set { it.copy(tintColor = v) } }
            }
            Divider()
            IntSlider("Home icon size", c.sizeDp, 36..110, " dp") { v -> set { it.copy(sizeDp = v) } }
            Divider()
            SwitchRow("Glass rim highlight", c.glassRim) { v -> set { it.copy(glassRim = v) } }
            Divider()
            SwitchRow("Reshape legacy icons", c.reshapeLegacy) { v -> set { it.copy(reshapeLegacy = v) } }
            if (c.reshapeLegacy) {
                Divider()
                ColorRow("Legacy icon background", c.legacyBackground) { v -> set { it.copy(legacyBackground = v) } }
            }
        }
        Group("Icon pack") {
            ChoiceRow("Pack", listOf<String?>(null, dev.scyde.scycher.data.GlyphPack.ID) + packs.map { it.pkg }, c.iconPack, { p ->
                when (p) { null -> "System"; dev.scyde.scycher.data.GlyphPack.ID -> "Scycher Glyphs"; else -> packs.firstOrNull { it.pkg == p }?.label ?: p }
            }) { v -> set { it.copy(iconPack = v) } }
            if (c.iconPack == dev.scyde.scycher.data.GlyphPack.ID) {
                Divider()
                SwitchRow("Match other apps", c.themeOthers, "Apps without a Scycher glyph get a matching tile from their own monochrome icon (Android 13+)") { v ->
                    set { it.copy(themeOthers = v) }
                }
            }
            if (packs.isEmpty()) InfoText("Install any ADW / Nova compatible icon pack from the Play Store and it will appear here.")
        }
        Group("Shadow & touch") {
            SwitchRow("Icon shadow", c.shadow) { v -> set { it.copy(shadow = v) } }
            Divider()
            PercentSlider("Shadow strength", c.shadowStrength, 0.05f..1f) { v -> set { it.copy(shadowStrength = v) } }
            Divider()
            SliderRow("Press scale", c.pressScale, 0.7f..1f, format = { "%.2f".format(it) }) { v -> set { it.copy(pressScale = v) } }
        }
        Group("Notification badges") {
            ChoiceRow("Badge", BadgeStyle.entries.toList(), c.badgeStyle) { v -> set { it.copy(badgeStyle = v) } }
            Divider()
            ColorRow("Badge color", c.badgeColor) { v -> set { it.copy(badgeColor = v) } }
        }
    }
}

@Composable
private fun IconPreview(c: IconConfig) {
    val ctx = LocalContext.current
    val apps by ScycherApp.instance.apps.apps.collectAsState()
    val sample = remember(apps) { apps.take(6) }
    val density = ctx.resources.displayMetrics.density
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1B1340), Color(0xFF3A1C71))))
            .horizontalScroll(rememberScrollState()).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        for (a in sample) {
            val bmp = remember(a.key, c) {
                runCatching {
                    val la = ctx.getSystemService(android.content.pm.LauncherApps::class.java)
                    val info = la.getActivityList(a.pkg, a.user).firstOrNull { it.componentName == a.component }
                    info?.getIcon(ctx.resources.displayMetrics.densityDpi)?.let { IconRenderer.render(it, (60 * density).toInt(), c).asImageBitmap() }
                }.getOrNull()
            }
            if (bmp != null) Image(bmp, null, Modifier.size(60.dp))
        }
    }
}

/* --------------------------------------- dock --------------------------------------- */

@Composable
fun DockSettings() {
    val d = LocalConfig.current.dock
    fun set(block: (dev.scyde.scycher.data.DockConfig) -> dev.scyde.scycher.data.DockConfig) = update { it.copy(dock = block(it.dock)) }
    Page("Dock") {
        Group {
            SwitchRow("Show dock", d.enabled) { v -> set { it.copy(enabled = v) } }
        }
        Group("Material") {
            ChoiceRow("Style", SurfaceStyle.entries.toList(), d.style) { v -> set { it.copy(style = v) } }
            Divider()
            PercentSlider("Tint opacity", d.opacity) { v -> set { it.copy(opacity = v) } }
            Divider()
            ColorRow("Tint color", d.tintColor) { v -> set { it.copy(tintColor = v) } }
            Divider()
            SwitchRow("Hairline border", d.border) { v -> set { it.copy(border = v) } }
            Divider()
            PercentSlider("Border opacity", d.borderOpacity) { v -> set { it.copy(borderOpacity = v) } }
            Divider()
            SwitchRow("Floating shadow", d.shadow) { v -> set { it.copy(shadow = v) } }
        }
        Group("Shape & size") {
            IntSlider("Corner radius", d.cornerRadiusDp, 0..60, " dp") { v -> set { it.copy(cornerRadiusDp = v) } }
            Divider()
            IntSlider("Icon size", d.iconSizeDp, 36..96, " dp") { v -> set { it.copy(iconSizeDp = v) } }
            Divider()
            IntSlider("Inner padding", d.paddingDp, 4..32, " dp") { v -> set { it.copy(paddingDp = v) } }
            Divider()
            IntSlider("Icon spacing", d.iconSpacingDp, 0..40, " dp") { v -> set { it.copy(iconSpacingDp = v) } }
            Divider()
            IntSlider("Bottom margin", d.bottomMarginDp, 0..60, " dp") { v -> set { it.copy(bottomMarginDp = v) } }
            Divider()
            SwitchRow("Full width", d.fullWidth) { v -> set { it.copy(fullWidth = v) } }
        }
        Group("Behaviour") {
            IntSlider("Max apps", d.maxIcons, 3..16) { v -> set { it.copy(maxIcons = v) } }
            Divider()
            SwitchRow("Magnify on touch", d.magnify, "macOS-style zoom while your finger slides") { v -> set { it.copy(magnify = v) } }
            Divider()
            SwitchRow("Show app names", d.showLabels) { v -> set { it.copy(showLabels = v) } }
            Divider()
            SwitchRow("App Library button", d.libraryButton, "iPad-style button at the end of the dock") { v -> set { it.copy(libraryButton = v) } }
            Divider()
            SwitchRow("Recent apps section", d.showRecents, "Like iPadOS – recently opened apps on the right") { v -> set { it.copy(showRecents = v) } }
            Divider()
            IntSlider("Recent apps", d.recentsCount, 1..5) { v -> set { it.copy(recentsCount = v) } }
        }
    }
}

/* --------------------------------------- widgets --------------------------------------- */

@Composable
fun WidgetSettings() {
    val w = LocalConfig.current.widgets
    val repo = ScycherApp.instance.weather
    val scope = rememberCoroutineScope()
    fun set(block: (dev.scyde.scycher.data.WidgetStyleConfig) -> dev.scyde.scycher.data.WidgetStyleConfig) = update { it.copy(widgets = block(it.widgets)) }
    var cityQuery by remember { mutableStateOf("") }
    var cities by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var zoneQuery by remember { mutableStateOf("") }
    val zones = remember { ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc") }.sorted() }
    Page("Widgets") {
        Group("Material (default for all widgets)") {
            ChoiceRow("Style", SurfaceStyle.entries.toList(), w.style) { v -> set { it.copy(style = v) } }
            Divider()
            PercentSlider("Tint opacity", w.opacity) { v -> set { it.copy(opacity = v) } }
            Divider()
            ColorRow("Tint color", w.tintColor) { v -> set { it.copy(tintColor = v) } }
            Divider()
            ColorRow("Text color", w.textColor) { v -> set { it.copy(textColor = v) } }
            Divider()
            ColorRow("Accent color", w.accentColor) { v -> set { it.copy(accentColor = v) } }
            Divider()
            IntSlider("Corner radius", w.cornerRadiusDp, 0..48, " dp") { v -> set { it.copy(cornerRadiusDp = v) } }
            Divider()
            IntSlider("Spacing", w.cellPaddingDp, 0..24, " dp") { v -> set { it.copy(cellPaddingDp = v) } }
            Divider()
            SwitchRow("Hairline border", w.border) { v -> set { it.copy(border = v) } }
            Divider()
            SwitchRow("Glass behind app widgets", w.systemWidgetGlass) { v -> set { it.copy(systemWidgetGlass = v) } }
        }
        Group("Clock") {
            SwitchRow("24-hour time", w.clock24h) { v -> set { it.copy(clock24h = v) } }
            Divider()
            SwitchRow("Show seconds", w.clockSeconds) { v -> set { it.copy(clockSeconds = v) } }
            Divider()
            IntSlider("Clock font weight", w.clockWeight, 100..900) { v -> set { it.copy(clockWeight = (v / 100) * 100) } }
        }
        Group("Weather", footer = "Powered by Open-Meteo. No account or location permission needed.") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("City", color = Color.White, style = dev.scyde.scycher.ui.common.TypeScale.Body, modifier = Modifier.weight(1f))
                Text(w.weatherCity, color = Sub, fontSize = 16.sp)
            }
            Divider()
            BasicTextField(
                cityQuery, { q -> cityQuery = q; if (q.length >= 2) scope.launch { cities = repo.search(q) } },
                singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2C2C2E)).padding(12.dp)) {
                        if (cityQuery.isEmpty()) Text("Search a city…", color = Sub, fontSize = 16.sp)
                        inner()
                    }
                }
            )
            for (c in cities) {
                Row(Modifier.fillMaxWidth().clickable {
                    set { it.copy(weatherCity = c.name, weatherLat = c.lat, weatherLon = c.lon) }
                    cities = emptyList(); cityQuery = ""
                    repo.refreshIfStale(force = true)
                }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(c.name, color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(c.region, color = Sub, fontSize = 14.sp)
                }
            }
            Divider()
            SwitchRow("Fahrenheit", w.weatherFahrenheit) { v -> set { it.copy(weatherFahrenheit = v) }; repo.refreshIfStale(force = true) }
        }
        Group("World clock") {
            for (z in w.worldClocks) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(z, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("Remove", color = Color(0xFFFF453A), fontSize = 15.sp, modifier = Modifier.clickable { set { it.copy(worldClocks = it.worldClocks - z) } })
                }
                Divider()
            }
            BasicTextField(
                zoneQuery, { zoneQuery = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2C2C2E)).padding(12.dp)) {
                        if (zoneQuery.isEmpty()) Text("Add a city (e.g. London, Tokyo)…", color = Sub, fontSize = 16.sp)
                        inner()
                    }
                }
            )
            if (zoneQuery.length >= 2) for (z in zones.filter { it.contains(zoneQuery.replace(' ', '_'), true) }.take(6)) {
                Text(z, color = Color.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable {
                    set { it.copy(worldClocks = (it.worldClocks + z).distinct()) }; zoneQuery = ""
                }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

/* --------------------------------------- library --------------------------------------- */

@Composable
fun LibrarySettings() {
    val l = LocalConfig.current.library
    fun set(block: (dev.scyde.scycher.data.LibraryConfig) -> dev.scyde.scycher.data.LibraryConfig) = update { it.copy(library = block(it.library)) }
    Page("App Library") {
        Group {
            ChoiceRow("Layout", LibraryStyle.entries.toList(), l.style) { v -> set { it.copy(style = v) } }
            Divider()
            ChoiceRow("Sort", LibrarySort.entries.toList(), l.sort) { v -> set { it.copy(sort = v) } }
            Divider()
            IntSlider("Columns", l.columns, 3..10) { v -> set { it.copy(columns = v) } }
            Divider()
            IntSlider("Icon size", l.iconSizeDp, 36..96, " dp") { v -> set { it.copy(iconSizeDp = v) } }
            Divider()
            SwitchRow("Show app names", l.showLabels) { v -> set { it.copy(showLabels = v) } }
            Divider()
            SwitchRow("Suggestions row", l.showSuggestions) { v -> set { it.copy(showSuggestions = v) } }
            Divider()
            SwitchRow("Smart suggestions", l.smartSuggestions, "Learns which apps you use at this time of day") { v -> set { it.copy(smartSuggestions = v) } }
            Divider()
            SwitchRow("Open keyboard automatically", l.autoKeyboard) { v -> set { it.copy(autoKeyboard = v) } }
            Divider()
            PercentSlider("Background dim", l.dim) { v -> set { it.copy(dim = v) } }
        }
    }
}

@Composable
fun HiddenApps() {
    val cfg = LocalConfig.current
    val apps by ScycherApp.instance.apps.apps.collectAsState()
    Page("Hidden apps") {
        InfoText("Hidden apps don't appear in the App Library or search. They still work and can be on your home screen.")
        Group {
            apps.forEachIndexed { i, a ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        update { c -> c.copy(hiddenApps = if (a.key in c.hiddenApps) c.hiddenApps - a.key else c.hiddenApps + a.key) }
                    }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val img = rememberAppIcon(a.key)
                    if (img != null) Image(img, null, Modifier.size(34.dp)) else Spacer(Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(a.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (a.key in cfg.hiddenApps) Icon(Icons.Rounded.VisibilityOff, null, tint = Sub)
                }
                if (i < apps.lastIndex) Divider()
            }
        }
    }
}

/* --------------------------------------- appearance --------------------------------------- */

@Composable
fun AppearanceSettings() {
    val l = LocalConfig.current.look
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    fun set(block: (dev.scyde.scycher.data.LookConfig) -> dev.scyde.scycher.data.LookConfig) = update { it.copy(look = block(it.look)) }
    val wallpaper = ScycherApp.instance.wallpaper
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { wallpaper.setFromUri(uri, alsoSystem = l.wallpaperSetSystem) }
    }
    Page("Appearance") {
        Group("Wallpaper", footer = "“Scycher” draws the wallpaper itself so every glass surface gets real blur. “System” keeps the HyperOS wallpaper; glass falls back to a frosted tint if Android doesn't allow reading it.") {
            ChoiceRow("Source", WallpaperMode.entries.toList(), l.wallpaperMode) { v ->
                if (v == WallpaperMode.Scycher && !wallpaper.hasCustom()) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else set { it.copy(wallpaperMode = v) }
            }
            Divider()
            NavRow("Scycher wallpapers", null, value = l.wallpaperPack?.let { k -> dev.scyde.scycher.data.WallpaperPack.designs.firstOrNull { it.key == k }?.name }) {
                nav.nav += "wallpapers"
            }
            Divider()
            NavRow("Choose photo…", null) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            Divider()
            ChoiceRow("Change wallpaper automatically", dev.scyde.scycher.data.WallpaperShuffle.entries.toList(), l.wallpaperShuffle, { it.label }) { v ->
                set { it.copy(wallpaperShuffle = v, wallpaperShuffleAt = System.currentTimeMillis()) }
            }
            Divider()
            SwitchRow("Also set lock screen", l.wallpaperSetSystem, "Pack wallpapers become the system home and lock screen wallpaper too") { v -> set { it.copy(wallpaperSetSystem = v) } }
            Divider()
            PercentSlider("Dim wallpaper", l.wallpaperDim, 0f..0.8f) { v -> set { it.copy(wallpaperDim = v) } }
            Divider()
            SwitchRow("Parallax", l.parallax, "Wallpaper glides as you switch pages") { v -> set { it.copy(parallax = v) } }
            Divider()
            PercentSlider("Blur wallpaper", l.wallpaperBlur) { v -> set { it.copy(wallpaperBlur = v) } }
            Divider()
            PercentSlider("Vignette", l.vignette, 0f..0.9f) { v -> set { it.copy(vignette = v) } }
        }
        Group("Ambient theme", footer = "Accent, dock tint and widget colors are picked from your wallpaper automatically and change with it.") {
            SwitchRow("Adaptive colors", l.adaptiveColors) { v -> set { it.copy(adaptiveColors = v) } }
        }
        Group("Glass") {
            IntSlider("Blur radius", l.glassBlurRadius, 0..60) { v -> set { it.copy(glassBlurRadius = v) } }
            Divider()
            SliderRow("Vibrancy (saturation)", l.glassSaturation, 0.5f..2.5f, format = { "%.2f".format(it) }) { v -> set { it.copy(glassSaturation = v) } }
        }
        Group("Style") {
            ColorRow("Accent color", l.accentColor) { v -> set { it.copy(accentColor = v) } }
            Divider()
            ChoiceRow("Font", FontChoice.entries.toList(), l.font) { v -> set { it.copy(font = v) } }
        }
        Group("Motion") {
            ChoiceRow("App open animation", OpenAnimation.entries.toList(), l.openAnimation) { v -> set { it.copy(openAnimation = v) } }
            Divider()
            SliderRow("Animation speed", l.animationSpeed, 0.5f..2f, format = { "%.1f×".format(it) }) { v -> set { it.copy(animationSpeed = v) } }
        }
        Group("System") {
            ChoiceRow("Home screen orientation", dev.scyde.scycher.data.OrientationMode.entries.toList(), l.orientation) { v -> set { it.copy(orientation = v) } }
            Divider()
            SwitchRow("Hide navigation bar", l.hideNavBar, "Full immersive home screen – swipe from the edge to reveal") { v -> set { it.copy(hideNavBar = v) } }
            Divider()
            SwitchRow("Hide status bar", l.hideStatusBar) { v -> set { it.copy(hideStatusBar = v) } }
            Divider()
            SwitchRow("Keep screen on", l.keepScreenOn, "While the home screen is showing – handy on a stand") { v -> set { it.copy(keepScreenOn = v) } }
            Divider()
            SwitchRow("Dark status icons", l.darkStatusIcons, "For bright wallpapers") { v -> set { it.copy(darkStatusIcons = v) } }
        }
    }
}

/* --------------------------------------- backup / about --------------------------------------- */

@Composable
fun BackupSettings() {
    val ctx = LocalContext.current
    val store = ScycherApp.instance.store
    var confirm by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(store.export().toByteArray()) }
            Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val ok = text != null && store.import(text)
            Toast.makeText(ctx, if (ok) "Restored" else "Not a Scycher backup", Toast.LENGTH_SHORT).show()
        }
    }
    Page("Backup & restore") {
        Group(footer = "The backup contains every setting and your full home screen layout. App widgets from other apps must be re-added after restoring on a new device.") {
            NavRow("Export backup", null) { export.launch("scycher-backup.json") }
            Divider()
            NavRow("Import backup", null) { import.launch(arrayOf("application/json", "text/*", "*/*")) }
        }
        Group {
            NavRow("Reset look to defaults", null) { confirm = "look" }
            Divider()
            NavRow("Reset home screen layout", null) { confirm = "layout" }
        }
    }
    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = Card,
            title = { Text(if (what == "look") "Reset look?" else "Reset layout?") },
            text = { Text(if (what == "look") "All visual settings go back to default. Your layout stays." else "Your pages, widgets and dock will be rebuilt from scratch.") },
            confirmButton = {
                TextButton({
                    if (what == "look") store.resetLook()
                    else {
                        val app = ScycherApp.instance
                        val c = store.value
                        val l = dev.scyde.scycher.data.DefaultLayout.build(ctx, app.apps.apps.value, c.home.columns, c.home.rows) { store.newId() }
                        store.updateLayout { l }
                    }
                    confirm = null
                }) { Text("Reset", color = Color(0xFFFF453A)) }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    Page("About") {
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(26.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF0B0B12), Color(0xFF1B1340), Color(0xFF3A1C71)))),
                contentAlignment = Alignment.Center
            ) { Text("S", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(14.dp))
            Text("SCYCHER", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text("SCYDE Launcher", color = Sub, fontSize = 15.sp)
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Sub, fontSize = 13.sp)
        }
        Group {
            NavRow("Developer", null, value = "SCYDE") { open(ctx, "https://scyde.dev") }
            Divider()
            NavRow("Website", null, value = "scyde.dev") { open(ctx, "https://scyde.dev") }
            Divider()
            NavRow("Source & releases", null, value = "GitHub") { open(ctx, "https://github.com/mmzlopasa/scycher") }
        }
        Text(
            "Made with care by SCYDE.", color = Sub, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun open(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}


@Composable
fun WallpapersScreen() {
    val cfg = LocalConfig.current
    val scope = rememberCoroutineScope()
    val wallpaper = ScycherApp.instance.wallpaper
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { wallpaper.setFromUri(uri, alsoSystem = cfg.look.wallpaperSetSystem) }
    }
    Page("Wallpapers") {
        InfoText("Original Scycher artwork. Every wallpaper has a dark and a light version – with “Match dark mode” it switches with the system theme, like on iPad.")
        Spacer(Modifier.height(8.dp))
        dev.scyde.scycher.ui.common.WallpaperGallery(cfg, onPickPhoto = {
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, columns = 3)
    }
}
