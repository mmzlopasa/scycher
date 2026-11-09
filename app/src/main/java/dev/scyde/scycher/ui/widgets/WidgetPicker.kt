package dev.scyde.scycher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.BuiltinKind
import dev.scyde.scycher.data.HomeItem
import dev.scyde.scycher.data.ItemType
import dev.scyde.scycher.data.LayoutOps
import dev.scyde.scycher.data.SurfaceStyle
import dev.scyde.scycher.ui.common.BottomSheet
import dev.scyde.scycher.ui.common.Chips
import dev.scyde.scycher.ui.common.ColorPalette
import dev.scyde.scycher.ui.common.GlassButton
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.SectionTitle
import dev.scyde.scycher.ui.common.toColor
import dev.scyde.scycher.ui.home.LocalLauncher
import dev.scyde.scycher.ui.home.MutableBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

@Composable
fun WidgetPicker() {
    val state = LocalLauncher.current
    var tab by remember { mutableStateOf(0) }
    BottomSheet(state.widgetPicker, { state.widgetPicker = false }, maxWidth = 980) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Widgets", color = Color.White, style = dev.scyde.scycher.ui.common.TypeScale.Title1, modifier = Modifier.weight(1f))
            Chips(listOf(0, 1), tab, { if (it == 0) "Scycher" else "Apps" }) { tab = it }
        }
        Box(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
            if (tab == 0) BuiltinGallery() else SystemGallery()
        }
    }
}

@Composable
private fun BuiltinGallery() {
    val state = LocalLauncher.current
    LazyVerticalGrid(
        GridCells.Adaptive(280.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(BuiltinKind.entries.toList(), key = { it.name }) { kind ->
            var size by remember { mutableStateOf(kind.sizes.first()) }
            Column(
                Modifier.clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = 0.06f)).padding(14.dp)
            ) {
                Text(kind.label, color = Color.White, style = dev.scyde.scycher.ui.common.TypeScale.Headline)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    val unit = minOf(250f / size.first, 160f / size.second, 85f)
                    val preview = remember(kind, size) { HomeItem(-7, ItemType.Widget, w = size.first, h = size.second, builtin = kind) }
                    Box(Modifier.size((unit * size.first).dp, (unit * size.second).dp)) { BuiltinWidget(preview) }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Chips(kind.sizes, size, { "${it.first}×${it.second}" }) { size = it }
                    }
                    GlassButton("", Icons.Rounded.Add, prominent = true, accent = Color.White) {
                        state.addBuiltinWidget(kind, size.first, size.second)
                    }
                }
            }
        }
    }
}

private fun cellsFor(info: AppWidgetProviderInfo, cellW: Float, cellH: Float, cols: Int, rows: Int): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0)
        return info.targetCellWidth.coerceIn(1, cols) to info.targetCellHeight.coerceIn(1, rows)
    val w = ceil(info.minWidth / cellW.coerceAtLeast(1f)).toInt().coerceIn(1, cols)
    val h = ceil(info.minHeight / cellH.coerceAtLeast(1f)).toInt().coerceIn(1, rows)
    return w to h
}

@Composable
private fun SystemGallery() {
    val state = LocalLauncher.current
    val app = ScycherApp.instance
    val ctx = LocalContext.current
    val providers by produceState<List<Pair<String, List<AppWidgetProviderInfo>>>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = app.packageManager
            app.widgets.providers()
                .groupBy { it.provider.packageName }
                .map { (pkg, list) ->
                    val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                    label to list
                }.sortedBy { it.first.lowercase() }
        }
    }
    var query by remember { mutableStateOf("") }
    val shown = providers.filter { query.isBlank() || it.first.contains(query, true) }
    Column {
        BasicTextField(
            query, { query = it }, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(14.dp)) {
                    if (query.isEmpty()) Text("Search apps", color = dev.scyde.scycher.ui.common.Palette2.Label3, fontSize = 16.sp)
                    inner()
                }
            }
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.first + it.second.size }) { (label, list) ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f)).padding(12.dp)) {
                    Text(label, color = Color.White, style = dev.scyde.scycher.ui.common.TypeScale.Headline)
                    for (info in list) {
                        val (cw, ch) = cellsFor(info, state.cellSize.width, state.cellSize.height, state.cols, state.rows)
                        val preview by produceState<ImageBitmap?>(null, info.provider) {
                            value = withContext(Dispatchers.IO) {
                                runCatching {
                                    val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
                                    val w = d.intrinsicWidth.coerceIn(1, 600); val h = d.intrinsicHeight.coerceIn(1, 600)
                                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    d.setBounds(0, 0, w, h); d.draw(Canvas(b)); b.asImageBitmap()
                                }.getOrNull()
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                state.host.addSystemWidget(info, cw, ch)
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(96.dp, 64.dp), contentAlignment = Alignment.Center) {
                                preview?.let { Image(it, null, Modifier.size(96.dp, 64.dp), contentScale = ContentScale.Fit) }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(info.loadLabel(app.packageManager) ?: "", color = Color.White, fontSize = 15.sp)
                                Text("$cw × $ch", color = dev.scyde.scycher.ui.common.Palette2.Label3, fontSize = 13.sp)
                            }
                            GlassButton("", Icons.Rounded.Add) { state.host.addSystemWidget(info, cw, ch) }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------------------------- per-widget settings ---------------------------------- */

@Composable
fun WidgetSettingsSheet() {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val id = state.widgetSettingsId
    val last = remember { MutableBox<Long?>(null) }
    if (id != null) last.value = id
    val item = last.value?.let { LayoutOps.findItem(cfg.layout, it) }
    BottomSheet(id != null && item != null, { state.widgetSettingsId = null }) {
        if (item == null) return@BottomSheet
        fun put(k: String, v: String?) {
            val cur = LayoutOps.findItem(state.store.value.layout, item.id) ?: return
            state.updateItem(cur.copy(extra = if (v == null) cur.extra - k else cur.extra + (k to v)))
        }
        key(item.id) { Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                item.builtin?.label ?: (ScycherApp.instance.widgets.info(item.widgetId)?.loadLabel(ScycherApp.instance.packageManager) ?: "Widget"),
                color = Color.White, style = dev.scyde.scycher.ui.common.TypeScale.Title1, modifier = Modifier.padding(horizontal = 20.dp)
            )
            if (item.type == ItemType.Widget) {
                SectionTitle("Style")
                val styles = buildList {
                    add("Default"); if (item.builtin == BuiltinKind.Weather) add("Auto")
                    addAll(SurfaceStyle.entries.map { it.name })
                }
                val sel = item.extra["style"] ?: (defaultStyleFor(item.builtin) ?: "Default")
                Box(Modifier.padding(horizontal = 16.dp)) { Chips(styles, sel, { it }) { put("style", if (it == "Default") null else it) } }
                SectionTitle("Background tint")
                Box(Modifier.padding(horizontal = 16.dp)) {
                    ColorPalette(item.extra["tint"]?.toLongOrNull() ?: cfg.widgets.tintColor) { put("tint", it.toString()) }
                }
                SectionTitle("Text color")
                Box(Modifier.padding(horizontal = 16.dp)) {
                    ColorPalette(item.extra["text"]?.toLongOrNull() ?: cfg.widgets.textColor) { put("text", it.toString()) }
                }
                SectionTitle("Accent")
                Box(Modifier.padding(horizontal = 16.dp)) {
                    ColorPalette(item.extra["accent"]?.toLongOrNull() ?: cfg.widgets.accentColor) { put("accent", it.toString()) }
                }
                val kind = item.builtin
                if (kind != null) {
                    SectionTitle("Size")
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        Chips(kind.sizes, item.w to item.h, { "${it.first}×${it.second}" }) { (w, h) ->
                            val layout = state.store.value.layout
                            val pageIdx = layout.pages.indexOfFirst { p -> p.items.any { it.id == item.id } }
                            val page = layout.pages.getOrNull(pageIdx) ?: return@Chips
                            if (LayoutOps.fits(page, state.cols, state.rows, item.x, item.y, w, h, item.id)) state.updateItem(item.copy(w = w, h = h))
                            else LayoutOps.findFree(page.copy(items = page.items.filter { it.id != item.id }), state.cols, state.rows, w, h)?.let { (x, y) ->
                                state.updateItem(item.copy(x = x, y = y, w = w, h = h))
                            }
                        }
                    }
                }
                if (kind == BuiltinKind.ClockDigital) {
                    SectionTitle("Clock style")
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        Chips(listOf("Classic", "Stacked", "Minimal"), item.extra["variant"] ?: "Classic", { it }) { put("variant", it) }
                    }
                }
                when (kind) {
                    BuiltinKind.Stack -> {
                        SectionTitle("Widgets in this stack (tap to add/remove)")
                        val selected = stackKinds(item)
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            dev.scyde.scycher.ui.common.MultiChips(Stackable, selected.toSet(), { it.label }) { k ->
                                val next = if (k in selected) selected - k else selected + k
                                put("kinds", next.ifEmpty { listOf(BuiltinKind.ClockDigital) }.joinToString(",") { it.name })
                            }
                        }
                        SectionTitle("Smart rotate")
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            Chips(listOf("On", "Off"), if (item.extra["rotate"] == "false") "Off" else "On", { it }) { put("rotate", if (it == "On") null else "false") }
                        }
                    }
                    BuiltinKind.Note -> {
                        SectionTitle("Title")
                        Field(item.extra["title"] ?: "Note") { put("title", it) }
                        SectionTitle("Text")
                        Field(item.extra["note"] ?: "", single = false) { put("note", it) }
                    }
                    BuiltinKind.Greeting -> {
                        SectionTitle("Your name")
                        Field(item.extra["name"] ?: "") { put("name", it) }
                    }
                    BuiltinKind.Photo -> Row(Modifier.padding(16.dp)) {
                        GlassButton("Choose photo", Icons.Rounded.Image) { state.host.pickPhoto(item.id) }
                    }
                    BuiltinKind.Weather, BuiltinKind.ClockDigital, BuiltinKind.ClockAnalog, BuiltinKind.WorldClock -> Row(Modifier.padding(16.dp)) {
                        GlassButton("More options in Settings", Icons.Rounded.Settings) { state.widgetSettingsId = null; state.openSettings("widgets") }
                    }
                    else -> {}
                }
            } else {
                Text(
                    "System widget glass, corners and blur are set globally in Settings › Widgets.",
                    color = dev.scyde.scycher.ui.common.Palette2.Label2, fontSize = 14.sp, modifier = Modifier.padding(20.dp)
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Remove", Icons.Rounded.DeleteOutline) { state.widgetSettingsId = null; state.removeItem(item) }
                Spacer(Modifier.weight(1f))
                GlassButton("Done", prominent = true, accent = cfg.look.accentColor.toColor()) { state.widgetSettingsId = null }
            }
        } }
    }
}

@Composable
private fun Field(initial: String, single: Boolean = true, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    BasicTextField(
        text, { text = it; onChange(it) }, singleLine = single,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f)).padding(14.dp).then(if (single) Modifier else Modifier.heightIn(min = 120.dp))
    )
}
