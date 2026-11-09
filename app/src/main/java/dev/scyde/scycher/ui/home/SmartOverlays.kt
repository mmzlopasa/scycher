package dev.scyde.scycher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.Security
import dev.scyde.scycher.ui.common.CenterDialog
import dev.scyde.scycher.ui.common.GlassButton
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.LocalWallpaper
import dev.scyde.scycher.ui.common.LocalWallpaperGeometry
import dev.scyde.scycher.ui.common.MenuDivider
import dev.scyde.scycher.ui.common.PinPad
import dev.scyde.scycher.ui.common.Scrim
import dev.scyde.scycher.ui.common.drawBlurBackdrop
import dev.scyde.scycher.ui.common.panelGlass
import dev.scyde.scycher.ui.common.rememberSaturationFilter

/** Full screen passcode prompt for protected apps / hidden apps. */
@Composable
fun PinOverlay() {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val req = state.pin
    val wp = LocalWallpaper.current
    val geo = LocalWallpaperGeometry.current
    val filter = rememberSaturationFilter()
    val ctx = LocalContext.current
    AnimatedVisibility(req != null, enter = fadeIn(), exit = fadeOut()) {
        val r = req ?: return@AnimatedVisibility
        Box(
            Modifier.fillMaxSize().drawBehind { drawBlurBackdrop(wp, geo, 1f, 0.6f, filter) }
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.systemBarsPadding()) {
                PinPad(
                    title = r.title,
                    onBiometric = if (cfg.security.biometric && Security.biometricAvailable(ctx)) {
                        { state.pin = null; state.host.authenticate(r.title, r.onSuccess) { state.pin = r } }
                    } else null,
                    onCancel = { state.pin = null },
                ) { pin ->
                    val ok = Security.verify(cfg.security, pin)
                    if (ok) { Security.markUnlocked(); state.pin = null; r.onSuccess() }
                    ok
                }
            }
        }
    }
}

/** Searchable list of apps used for "swipe up on icon opens…" and similar choices. */
@Composable
fun AppChooserOverlay() {
    val state = LocalLauncher.current
    val req = state.appChooser
    val apps by ScycherApp.instance.apps.apps.collectAsState()
    var q by remember(req) { mutableStateOf("") }
    Scrim(req != null, 0.45f) { state.appChooser = null }
    AnimatedVisibility(req != null, enter = fadeIn(), exit = fadeOut()) {
        val r = req ?: return@AnimatedVisibility
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(24.dp).panelGlass()
                    .pointerInput(Unit) { detectTapGestures { } }.padding(18.dp)
            ) {
                Text(r.title, color = Color.White, style = dev.scyde.scycher.ui.common.TypeScale.Title3)
                Spacer(Modifier.size(12.dp))
                BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f)).padding(12.dp)) {
                            if (q.isEmpty()) Text("Search", color = dev.scyde.scycher.ui.common.Palette2.Label3, fontSize = 16.sp)
                            inner()
                        }
                    }
                )
                Spacer(Modifier.size(8.dp))
                Text("None", color = Color(0xFFFF453A), fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable { state.appChooser = null; r.onPick(null) }.padding(vertical = 10.dp))
                MenuDivider()
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps.filter { q.isBlank() || it.label.contains(q, true) }, key = { it.key }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { state.appChooser = null; r.onPick(a.key) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconImage(rememberAppIcon(a.key), 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Move an app into another App Library category (or create one). */
@Composable
fun CategoryChooser() {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val key = state.categoryFor
    val apps by ScycherApp.instance.apps.apps.collectAsState()
    val current = key?.let { k -> cfg.customCategories[k] ?: apps.firstOrNull { it.key == k }?.category }
    val all = remember(apps, cfg.customCategories) {
        (apps.map { cfg.customCategories[it.key] ?: it.category } + cfg.customCategories.values).distinct().sorted()
    }
    var newName by remember(key) { mutableStateOf("") }
    fun set(cat: String?) {
        val k = key ?: return
        state.store.update { c -> c.copy(customCategories = if (cat == null) c.customCategories - k else c.customCategories + (k to cat)) }
        state.categoryFor = null
    }
    CenterDialog(key != null, { state.categoryFor = null }) {
        Column {
            Text("Category", color = Color.White, style = dev.scyde.scycher.ui.common.TypeScale.Title3)
            Text(key?.let { ScycherApp.instance.apps.label(it) } ?: "", color = dev.scyde.scycher.ui.common.Palette2.Label2, fontSize = 14.sp)
            Spacer(Modifier.size(10.dp))
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(all) { c ->
                    Row(Modifier.fillMaxWidth().clickable { set(c) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (c == current) Icon(Icons.Rounded.Check, null, tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    newName, { newName = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                    decorationBox = { inner ->
                        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f)).padding(12.dp)) {
                            if (newName.isEmpty()) Text("New category", color = dev.scyde.scycher.ui.common.Palette2.Label3, fontSize = 16.sp)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                GlassButton("Add", prominent = true, accent = Color.White) { if (newName.isNotBlank()) set(newName.trim()) }
            }
            if (key != null && cfg.customCategories.containsKey(key)) {
                Text("Reset to automatic", color = Color(0xFFFF9F0A), fontSize = 15.sp,
                    modifier = Modifier.clickable { set(null) }.padding(top = 12.dp))
            }
        }
    }
}
