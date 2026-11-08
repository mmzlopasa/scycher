package dev.scyde.scycher.ui.ipad

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.HomeItem
import dev.scyde.scycher.data.IconShapes
import dev.scyde.scycher.data.IconStyle
import dev.scyde.scycher.data.SurfaceStyle
import dev.scyde.scycher.ui.common.BottomSheet
import dev.scyde.scycher.ui.common.Chips
import dev.scyde.scycher.ui.common.ColorPalette
import dev.scyde.scycher.ui.common.GlassButton
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.LocalWallpaper
import dev.scyde.scycher.ui.common.LocalWallpaperGeometry
import dev.scyde.scycher.ui.common.SectionTitle
import dev.scyde.scycher.ui.common.drawBlurBackdrop
import dev.scyde.scycher.ui.common.glass
import dev.scyde.scycher.ui.common.rememberSaturationFilter
import dev.scyde.scycher.ui.home.LocalLauncher
import dev.scyde.scycher.ui.home.RemoveButton
import dev.scyde.scycher.ui.home.rememberAppIcon
import dev.scyde.scycher.ui.widgets.BuiltinWidget
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/* ------------------------------------------ Today View ------------------------------------------ */

/** iPad Today View: a scrollable column of widgets that slides in from the left of the first page. */
@Composable
fun TodayView() {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val p = state.today.value
    if (p <= 0.001f && state.today.targetValue == 0f) return
    val wp = LocalWallpaper.current
    val geo = LocalWallpaperGeometry.current
    val filter = rememberSaturationFilter()
    val panelW = 420.dp
    val panelPx = with(LocalDensity.current) { panelW.toPx() }
    BackHandler(p > 0f) { state.closeToday() }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = (p * 1.3f).coerceAtMost(1f) }
            .drawBehind { drawBlurBackdrop(wp, geo, 1f, 0.15f, filter) }
            .pointerInput(Unit) { detectTapGestures { state.closeToday() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (state.today.value < 0.7f) state.closeToday() else state.openToday() },
                ) { c, d -> c.consume(); state.scope.launch { state.today.snapTo((state.today.value + d / panelPx).coerceIn(0f, 1f)) } }
            }
    ) {
        Column(
            Modifier.systemBarsPadding().fillMaxHeight().width(panelW + 40.dp)
                .graphicsLayer { translationX = -(1f - p) * panelPx * 0.6f }
                .verticalScroll(rememberScrollState())
                .padding(start = 28.dp, end = 12.dp, top = 24.dp, bottom = 40.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val now = remember { ZonedDateTime.now() }
            Column {
                Text(now.format(DateTimeFormatter.ofPattern("EEEE")), color = Color.White.copy(alpha = 0.75f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(now.format(DateTimeFormatter.ofPattern("d MMMM")), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
            val unit = panelW / 4
            val items = cfg.layout.today
            // pack: two small widgets side by side, bigger ones full width
            var i = 0
            while (i < items.size) {
                val a = items[i]
                val b = items.getOrNull(i + 1)
                if (a.w <= 2 && b != null && b.w <= 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TodayWidget(a, (panelW - 16.dp) / 2, unit * a.h)
                        TodayWidget(b, (panelW - 16.dp) / 2, unit * b.h)
                    }
                    i += 2
                } else {
                    TodayWidget(a, if (a.w <= 2) (panelW - 16.dp) / 2 else panelW, unit * a.h * (if (a.w <= 2) 1f else 1f))
                    i += 1
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(if (state.todayEdit) "Done" else "Edit", prominent = state.todayEdit, accent = Color.White) { state.todayEdit = !state.todayEdit }
                if (state.todayEdit) GlassButton("Add widget", Icons.Rounded.Add) { state.pickerTarget = "today"; state.widgetPicker = true }
            }
        }
    }
}

@Composable
private fun TodayWidget(item: HomeItem, w: Dp, h: Dp) {
    val state = LocalLauncher.current
    Box(Modifier.size(w, h)) {
        BuiltinWidget(item, Modifier.fillMaxSize())
        if (state.todayEdit) {
            RemoveButton(Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp)) {
                state.store.updateLayout { it.copy(today = it.today.filterNot { t -> t.id == item.id }) }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = Color.White, modifier = Modifier.size(30.dp).clickable { move(state, item, -1) })
                Icon(Icons.Rounded.KeyboardArrowDown, "Move down", tint = Color.White, modifier = Modifier.size(30.dp).clickable { move(state, item, 1) })
            }
        }
    }
}

private fun move(state: dev.scyde.scycher.ui.LauncherState, item: HomeItem, dir: Int) {
    state.store.updateLayout { l ->
        val list = l.today.toMutableList()
        val i = list.indexOfFirst { it.id == item.id }
        val j = i + dir
        if (i < 0 || j !in list.indices) l else { list.add(j, list.removeAt(i)); l.copy(today = list) }
    }
}

/* ------------------------------------------ Customize ------------------------------------------ */

/** iOS 18+/26 "Customize" sheet: icon appearance, size and tint in one place. */
@Composable
fun CustomizeSheet() {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val ic = cfg.icons
    fun icons(block: (dev.scyde.scycher.data.IconConfig) -> dev.scyde.scycher.data.IconConfig) =
        state.store.update { it.copy(icons = block(it.icons)) }
    BottomSheet(state.customize, { state.customize = false }) {
        Text("Customize", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        SectionTitle("Appearance")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (style in listOf(IconStyle.Default, IconStyle.Dark, IconStyle.Clear, IconStyle.Tinted, IconStyle.Auto)) {
                StylePreview(style, style == ic.style) { icons { it.copy(style = style) } }
            }
        }
        SectionTitle("Size")
        Box(Modifier.padding(horizontal = 16.dp)) {
            Chips(listOf("Small", "Large"), if (ic.sizeDp >= 74) "Large" else "Small", { it }) { v ->
                state.store.update {
                    if (v == "Large") it.copy(icons = it.icons.copy(sizeDp = 78), home = it.home.copy(showLabels = false))
                    else it.copy(icons = it.icons.copy(sizeDp = 64), home = it.home.copy(showLabels = true))
                }
            }
        }
        SectionTitle("Shape")
        Box(Modifier.padding(horizontal = 16.dp)) {
            Chips(dev.scyde.scycher.data.IconShape.entries.toList(), ic.shape, { it.name }) { v -> icons { it.copy(shape = v) } }
        }
        if (ic.style == IconStyle.Tinted) {
            SectionTitle("Tint")
            Box(Modifier.padding(horizontal = 16.dp)) { ColorPalette(ic.tintColor) { v -> icons { it.copy(tintColor = v) } } }
        }
        SectionTitle("Wallpaper")
        Box(Modifier.padding(horizontal = 16.dp)) {
            Chips(listOf("Normal", "Dimmed", "Darker"), when { cfg.look.wallpaperDim > 0.35f -> "Darker"; cfg.look.wallpaperDim > 0.05f -> "Dimmed"; else -> "Normal" }, { it }) { v ->
                state.store.update { it.copy(look = it.look.copy(wallpaperDim = when (v) { "Darker" -> 0.45f; "Dimmed" -> 0.2f; else -> 0f })) }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun StylePreview(style: IconStyle, selected: Boolean, onClick: () -> Unit) {
    val apps by ScycherApp.instance.apps.apps.collectAsState()
    val key = apps.firstOrNull()?.key
    val shape = remember { IconShapes.composeShape(dev.scyde.scycher.data.IconShape.Squircle) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(6.dp)) {
        Box(
            Modifier.size(64.dp).glass(
                shape,
                when (style) { IconStyle.Dark -> SurfaceStyle.Solid; IconStyle.Clear -> SurfaceStyle.Liquid; else -> SurfaceStyle.Tinted },
                when (style) { IconStyle.Dark -> Color(0xFF1C1C1E); IconStyle.Tinted -> Color(0xFF5E5CE6); IconStyle.Default -> Color.White; else -> Color.White },
                when (style) { IconStyle.Default -> 0.9f; IconStyle.Clear -> 0.15f; else -> 0.6f },
                true, if (selected) 0.9f else 0.2f
            ),
            contentAlignment = Alignment.Center
        ) {
            val img = rememberAppIcon(key)
            if (img != null && style == IconStyle.Default) Image(img, null, Modifier.size(44.dp))
            else Text(
                when (style) { IconStyle.Dark -> "Dark"; IconStyle.Clear -> "Clear"; IconStyle.Tinted -> "Tint"; IconStyle.Auto -> "Auto"; else -> "" },
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when (style) { IconStyle.Auto -> "Automatic"; else -> style.name }, color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/* ------------------------------------- dock App Library button ------------------------------------- */

@Composable
fun DockLibraryButton(iconSize: Dp) {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val shape = remember(cfg.icons.shape) { IconShapes.composeShape(cfg.icons.shape) }
    val keys = (cfg.recentApps + ScycherApp.instance.apps.apps.collectAsState().value.map { it.key }).distinct().take(4)
    Box(
        Modifier.size(iconSize).glass(shape, SurfaceStyle.Liquid, Color.White, 0.25f, true, 0.3f)
            .clickable { state.openLibrary() }.padding(iconSize * 0.16f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (r in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in 0 until 2) {
                    val img = rememberAppIcon(keys.getOrNull(r * 2 + c))
                    val s = (iconSize * 0.68f - 3.dp) / 2
                    if (img != null) Image(img, null, Modifier.size(s)) else Spacer(Modifier.size(s))
                }
            }
        }
    }
}
