package dev.scyde.scycher.ui.home

import android.content.pm.ShortcutInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.HomeItem
import dev.scyde.scycher.data.ItemType
import dev.scyde.scycher.data.LayoutOps
import dev.scyde.scycher.gestures.SystemActions
import dev.scyde.scycher.notifications.ScycherNotificationListener
import dev.scyde.scycher.ui.DragSource
import dev.scyde.scycher.ui.MenuRequest
import dev.scyde.scycher.ui.RenameTarget
import dev.scyde.scycher.ui.common.CenterDialog
import dev.scyde.scycher.ui.common.GlassButton
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.LocalWallpaper
import dev.scyde.scycher.ui.common.LocalWallpaperGeometry
import dev.scyde.scycher.ui.common.MenuDivider
import dev.scyde.scycher.ui.common.MenuRow
import dev.scyde.scycher.ui.common.Scrim
import dev.scyde.scycher.ui.common.drawBlurBackdrop
import dev.scyde.scycher.ui.common.panelGlass
import dev.scyde.scycher.ui.common.rememberSaturationFilter
import dev.scyde.scycher.ui.common.toColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* ------------------------------------ menu ------------------------------------ */

@Composable
fun ContextMenu() {
    val state = LocalLauncher.current
    val m = state.menu
    val last = remember { MutableBox<MenuRequest?>(null) }
    if (m != null) last.value = m
    Scrim(m != null, 0.22f) { state.menu = null }
    val req = last.value ?: return
    val density = LocalDensity.current
    AnimatedVisibility(
        m != null,
        enter = fadeIn(spring(stiffness = 900f)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 700f), 0.85f),
        exit = fadeOut(spring(stiffness = 1200f)) + scaleOut(targetScale = 0.9f),
    ) {
        Layout(
            content = { MenuCard(req) },
            modifier = Modifier.fillMaxSize(),
        ) { ms, c ->
            val p = ms[0].measure(Constraints(maxWidth = c.maxWidth, maxHeight = c.maxHeight))
            val margin = with(density) { 16.dp.roundToPx() }
            val gap = with(density) { 10.dp.roundToPx() }
            val a = req.anchor
            layout(c.maxWidth, c.maxHeight) {
                val x = (a.center.x - p.width / 2f).toInt().coerceIn(margin, (c.maxWidth - p.width - margin).coerceAtLeast(margin))
                val below = a.bottom.toInt() + gap
                val y = if (below + p.height < c.maxHeight - margin) below else (a.top.toInt() - gap - p.height).coerceAtLeast(margin)
                p.place(x, y)
            }
        }
    }
}

@Composable
private fun MenuCard(req: MenuRequest) {
    val state = LocalLauncher.current
    val repo = ScycherApp.instance.apps
    val cfg = LocalConfig.current
    val item = req.item
    val key = req.appKey
    fun close() { state.menu = null }

    Column(
        Modifier
            .width(270.dp)
            .panelGlass(60f)
            .pointerInput(Unit) { detectTapGestures { } }
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
    ) {
        when {
            item == null && key == null -> {
                MenuRow("Edit Home Screen", Icons.Rounded.Apps) { close(); state.editMode = true }
                MenuDivider()
                MenuRow("Add Widget", Icons.Rounded.Widgets) { close(); state.widgetPicker = true }
                MenuDivider()
                MenuRow("Wallpaper", Icons.Rounded.Wallpaper) { close(); state.host.pickWallpaper() }
                MenuDivider()
                MenuRow("Scycher Settings", Icons.Rounded.Settings) { close(); state.openSettings() }
            }
            key != null -> {
                val shortcuts by produceState<List<ShortcutInfo>>(emptyList(), key) {
                    value = withContext(Dispatchers.IO) { repo.shortcuts(key) }
                }
                for (s in shortcuts) {
                    ShortcutRow(s) { close(); repo.startShortcut(s, null) }
                    MenuDivider()
                }
                if (shortcuts.isNotEmpty()) MenuDivider(thick = true)
                val src = req.source
                if (src == DragSource.Library) {
                    MenuRow("Add to Home Screen", Icons.Rounded.AddToHomeScreen) { close(); state.addAppToHome(key); state.closeLibrary() }
                    MenuDivider()
                }
                if (src != DragSource.Dock && cfg.dock.enabled && cfg.layout.dock.none { it.app == key }) {
                    MenuRow("Add to Dock", Icons.Rounded.Dock) {
                        close()
                        if (src is DragSource.Page && item != null) state.removeItem(item)
                        state.addAppToDock(key)
                    }
                    MenuDivider()
                }
                if (src != DragSource.Library) {
                    MenuRow("Edit Home Screen", Icons.Rounded.Apps) { close(); state.editMode = true }
                    MenuDivider()
                }
                MenuRow("Rename", Icons.Rounded.Edit) { close(); state.rename = RenameTarget.App(key) }
                MenuDivider()
                MenuRow("App Info", Icons.Rounded.Info) { close(); repo.appDetails(key) }
                MenuDivider()
                when (src) {
                    DragSource.Library -> {
                        MenuRow("Hide from Library", Icons.Rounded.VisibilityOff) {
                            close(); state.store.update { it.copy(hiddenApps = it.hiddenApps + key) }
                        }
                        MenuDivider()
                    }
                    is DragSource.Folder -> {
                        MenuRow("Remove from Folder", Icons.Rounded.RemoveCircleOutline) {
                            close()
                            state.removeFromFolder(src.folderId, key)
                            state.addAppToHome(key)
                        }
                        MenuDivider()
                    }
                    DragSource.Dock -> {
                        MenuRow("Remove from Dock", Icons.Rounded.RemoveCircleOutline, destructive = true) { close(); item?.let { state.removeItem(it) } }
                        MenuDivider()
                    }
                    is DragSource.Page -> {
                        MenuRow("Remove from Home", Icons.Rounded.RemoveCircleOutline, destructive = true) { close(); item?.let { state.removeItem(it) } }
                        MenuDivider()
                    }
                }
                val app = repo.map.value[key]
                if (app != null && !app.isSystem) MenuRow("Uninstall", Icons.Rounded.DeleteOutline, destructive = true) { close(); repo.uninstall(key) }
            }
            item != null && item.type == ItemType.Folder -> {
                MenuRow("Rename Folder", Icons.Rounded.Edit) { close(); state.rename = RenameTarget.Folder(item.id) }
                MenuDivider()
                MenuRow("Edit Home Screen", Icons.Rounded.Apps) { close(); state.editMode = true }
                MenuDivider()
                MenuRow("Remove Folder", Icons.Rounded.DeleteOutline, destructive = true) { close(); state.removeItem(item) }
            }
            item != null && item.isWidget -> {
                MenuRow("Edit Widget", Icons.Rounded.Tune) { close(); state.widgetSettingsId = item.id }
                MenuDivider()
                MenuRow("Edit Home Screen", Icons.Rounded.Apps) { close(); state.editMode = true }
                MenuDivider()
                MenuRow("Remove Widget", Icons.Rounded.DeleteOutline, destructive = true) { close(); state.removeItem(item) }
            }
        }
    }
}

@Composable
private fun ShortcutRow(s: ShortcutInfo, onClick: () -> Unit) {
    val repo = ScycherApp.instance.apps
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, s.id) {
        value = withContext(Dispatchers.IO) { repo.shortcutIcon(s) }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text((s.shortLabel ?: s.longLabel ?: "").toString(), color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
        icon?.let { Image(it, null, Modifier.size(22.dp)) }
    }
}

/* ----------------------------------- folders ----------------------------------- */

@Composable
fun FolderOverlay() {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val id = state.openFolderId
    val last = remember { MutableBox<Long?>(null) }
    if (id != null) last.value = id
    val folder = last.value?.let { LayoutOps.findItem(cfg.layout, it) }
    val dragging = state.drag?.let { it.active && it.source is DragSource.Folder } == true
    AppsPanel(
        visible = id != null && folder != null,
        title = folder?.name ?: "Folder",
        apps = folder?.apps ?: emptyList(),
        source = folder?.let { DragSource.Folder(it.id) } ?: DragSource.Library,
        hidden = dragging,
        onTitle = { folder?.let { state.rename = RenameTarget.Folder(it.id) } },
        onDismiss = { state.openFolderId = null },
    )
}

@Composable
fun CategoryOverlay() {
    val state = LocalLauncher.current
    val c = state.openCategory
    val last = remember { MutableBox<Pair<String, List<String>>?>(null) }
    if (c != null) last.value = c
    val dragging = state.drag?.let { it.active && it.source == DragSource.Library } == true
    AppsPanel(
        visible = c != null, title = last.value?.first ?: "", apps = last.value?.second ?: emptyList(),
        source = DragSource.Library, hidden = dragging, onTitle = null,
        onDismiss = { state.openCategory = null },
    )
}

@Composable
private fun AppsPanel(
    visible: Boolean,
    title: String,
    apps: List<String>,
    source: DragSource,
    hidden: Boolean,
    onTitle: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val wp = LocalWallpaper.current
    val geo = LocalWallpaperGeometry.current
    val filter = rememberSaturationFilter()
    val a by animateFloatAsState(if (visible && !hidden) 1f else 0f, spring(stiffness = 500f), label = "panel")
    if (a <= 0.001f && !visible) return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = a }
            .drawBehind { drawBlurBackdrop(wp, geo, 1f, 0.25f, filter) }
            .pointerInput(visible) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.systemBarsPadding().graphicsLayer { val s = 0.85f + 0.15f * a; scaleX = s; scaleY = s },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(bottom = 18.dp)
                    .then(if (onTitle != null) Modifier.clickable { onTitle() } else Modifier)
            )
            val cols = if (apps.size <= 4) 2.coerceAtLeast(apps.size).coerceAtMost(4) else if (apps.size <= 9) 3 else 4
            val iconSize = cfg.icons.sizeDp.dp
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .panelGlass(90f)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                for (row in apps.chunked(cols)) Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    for (k in row) {
                        val item = remember(k, source) { HomeItem(-(k.hashCode().toLong() and 0xFFFFFFF) - 100, ItemType.App, app = k) }
                        Box(
                            Modifier.width(iconSize * 1.45f).itemGestures(item, source) { b ->
                                state.launch(k, b); onDismiss()
                            },
                            contentAlignment = Alignment.TopCenter
                        ) { AppIcon(k, iconSize, true) }
                    }
                }
            }
        }
    }
}

/* ------------------------------------ rename ------------------------------------ */

@Composable
fun RenameDialog() {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val t = state.rename
    val repo = ScycherApp.instance.apps
    var text by remember(t) {
        mutableStateOf(
            when (t) {
                is RenameTarget.App -> repo.label(t.key)
                is RenameTarget.Folder -> LayoutOps.findItem(cfg.layout, t.id)?.name ?: "Folder"
                null -> ""
            }
        )
    }
    val focus = remember { FocusRequester() }
    fun save() {
        when (t) {
            is RenameTarget.App -> state.store.update {
                it.copy(customLabels = if (text.isBlank()) it.customLabels - t.key else it.customLabels + (t.key to text.trim()))
            }
            is RenameTarget.Folder -> LayoutOps.findItem(state.store.value.layout, t.id)?.let { state.updateItem(it.copy(name = text.trim().ifEmpty { "Folder" })) }
            null -> {}
        }
        state.rename = null
    }
    CenterDialog(t != null, { state.rename = null }) {
        Column {
            Text("Rename", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                text, { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(cfg.look.accentColor.toColor()),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(14.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (t is RenameTarget.App) GlassButton("Reset") {
                    state.store.update { it.copy(customLabels = it.customLabels - t.key) }; state.rename = null
                }
                Spacer(Modifier.weight(1f))
                GlassButton("Cancel") { state.rename = null }
                GlassButton("Save", prominent = true, accent = cfg.look.accentColor.toColor()) { save() }
            }
        }
        LaunchedEffect(t) { if (t != null) runCatching { focus.requestFocus() } }
    }
}

/* ---------------------------------- onboarding ---------------------------------- */

@Composable
fun Onboarding() {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    if (cfg.onboarded) return
    val ctx = LocalContext.current
    val wp = LocalWallpaper.current
    val geo = LocalWallpaperGeometry.current
    val filter = rememberSaturationFilter()
    val tick = state.resumeTick
    val isDefault = remember(tick) { SystemActions.isDefaultLauncher(ctx) }
    val gestures = remember(tick) { SystemActions.hyperGesturesEnabled(ctx) || SystemActions.isAccessibilityEnabled(ctx) }
    val notif = remember(tick) { ScycherNotificationListener.isEnabled(ctx) }
    val accent = cfg.look.accentColor.toColor()
    Box(
        Modifier.fillMaxSize()
            .drawBehind { drawBlurBackdrop(wp, geo, 1f, 0.75f, filter) }
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.systemBarsPadding().widthIn(max = 560.dp).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SCYCHER", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
            Text("by SCYDE · scyde.dev", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Your home screen, every pixel your way. Three quick steps and you're done.",
                color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth().panelGlass(70f).padding(vertical = 6.dp)) {
                Step("Make Scycher your home screen", "Required so the Home button opens Scycher.", isDefault, accent) {
                    state.host.requestDefaultLauncher()
                }
                MenuDivider()
                Step("Swipe gestures", "Unlock HyperOS full-screen gestures or use Scycher Gestures.", gestures, accent) {
                    state.openSettings("gestures")
                }
                MenuDivider()
                Step("Badges & Now Playing", "Notification dots on icons and the music widget.", notif, accent) {
                    SystemActions.openNotificationAccess(ctx)
                }
            }
            Spacer(Modifier.height(24.dp))
            GlassButton("Start", Icons.AutoMirrored.Rounded.ArrowForward, prominent = true, accent = Color.White) {
                state.store.update { it.copy(onboarded = true) }
            }
        }
    }
}

@Composable
private fun Step(title: String, subtitle: String, done: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (done) accent else Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.Add, null, tint = if (done) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}
