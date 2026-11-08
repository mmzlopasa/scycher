package dev.scyde.scycher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.GestureAction
import dev.scyde.scycher.data.HomeItem
import dev.scyde.scycher.data.HomePage
import dev.scyde.scycher.data.ItemType
import dev.scyde.scycher.data.LayoutOps
import dev.scyde.scycher.data.PageIndicatorStyle
import dev.scyde.scycher.data.PageTransition
import dev.scyde.scycher.data.SurfaceStyle
import dev.scyde.scycher.data.WallpaperMode
import dev.scyde.scycher.notifications.ScycherNotificationListener
import dev.scyde.scycher.ui.DragSource
import dev.scyde.scycher.ui.LauncherState
import dev.scyde.scycher.ui.MenuRequest
import dev.scyde.scycher.ui.common.GlassButton
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.LocalWallpaper
import dev.scyde.scycher.ui.common.LocalWallpaperGeometry
import dev.scyde.scycher.ui.common.WallpaperGeometry
import dev.scyde.scycher.ui.common.drawWallpaper
import dev.scyde.scycher.ui.common.glass
import dev.scyde.scycher.ui.library.AppLibrary
import dev.scyde.scycher.ui.widgets.WidgetPicker
import dev.scyde.scycher.ui.widgets.WidgetSettingsSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun LauncherRoot(state: LauncherState) {
    val app = ScycherApp.instance
    val cfg by app.store.config.collectAsState()
    val wp by app.wallpaper.state.collectAsState()
    val badges by ScycherNotificationListener.badges.collectAsState()
    val geo = remember { WallpaperGeometry() }
    val density = LocalDensity.current

    CompositionLocalProvider(
        LocalConfig provides cfg,
        LocalWallpaper provides wp,
        LocalWallpaperGeometry provides geo,
        LocalBadges provides badges,
        LocalLauncher provides state,
    ) {
        val pager = rememberPagerState(pageCount = { state.store.value.layout.pages.size })
        state.pager = pager

        // Wallpaper parallax follows the pager
        geo.parallaxRange = if (cfg.look.parallax && cfg.look.wallpaperMode == WallpaperMode.Scycher && cfg.layout.pages.size > 1)
            with(density) { 90.dp.toPx() } else 0f
        LaunchedEffect(pager, cfg.layout.pages.size) {
            snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { pos ->
                val n = max(1, cfg.layout.pages.size - 1)
                geo.parallax = -(pos / n).coerceIn(0f, 1f) * geo.parallaxRange
            }
        }
        // Flip pages while dragging near the edges
        val drag = state.drag
        LaunchedEffect(drag?.active) {
            val d = drag ?: return@LaunchedEffect
            if (!d.active) return@LaunchedEffect
            val edge = with(density) { 36.dp.toPx() }
            while (state.drag === d) {
                delay(120)
                val x = d.pointer.x
                val dir = when {
                    x < edge -> -1
                    x > state.rootSize.width - edge -> 1
                    else -> 0
                }
                if (dir != 0 && !state.dockBounds.contains(d.pointer)) {
                    delay(480)
                    val x2 = d.pointer.x
                    val still = if (dir < 0) x2 < edge else x2 > state.rootSize.width - edge
                    if (still && state.drag === d) { state.edgeFlip(dir); delay(500) }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { geo.rootSize = Size(it.width.toFloat(), it.height.toFloat()); state.rootSize = geo.rootSize }
                .drawBehind { drawWallpaper(wp, geo, cfg.look.wallpaperDim) }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val lib = state.library.value
                        val e = state.enter.value
                        val s = (1f - lib * 0.08f) * (0.9f + 0.1f * e)
                        scaleX = s; scaleY = s
                        alpha = (1f - lib * 1.2f).coerceIn(0f, 1f) * (0.4f + 0.6f * e)
                    }
                    .homeGestures(state)
            ) {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Spacer(Modifier.height(if (state.editMode) 56.dp else cfg.home.topPaddingDp.dp))
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        beyondViewportPageCount = cfg.layout.pages.size,
                        key = { cfg.layout.pages.getOrNull(it)?.id ?: it },
                    ) { index ->
                        val page = cfg.layout.pages.getOrNull(index) ?: return@HorizontalPager
                        Box(Modifier.fillMaxSize().pageTransition(state, index, cfg.home.pageTransition)) {
                            PageGrid(index, page)
                        }
                    }
                    PageIndicator(cfg.layout.pages.size, Modifier.align(Alignment.CenterHorizontally))
                    Dock()
                }
                EditBar(Modifier.align(Alignment.TopCenter))
            }

            AppLibrary()
            FolderOverlay()
            CategoryOverlay()
            ContextMenu()
            DragShadow()
            WidgetPicker()
            WidgetSettingsSheet()
            RenameDialog()
            Onboarding()
        }
    }
}

/* ----------------------------------- gestures ----------------------------------- */

private fun Modifier.homeGestures(state: LauncherState): Modifier = composed {
    val cfg = LocalConfig.current
    val g = cfg.gestures
    val density = LocalDensity.current
    val threshold = with(density) { 70.dp.toPx() }
    pointerInput(g, state.editMode) {
        val slop = viewConfiguration.touchSlop
        val longPress = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (state.editMode || state.drag != null || state.anyOverlay()) return@awaitEachGesture
            val startTime = down.uptimeMillis
            var mode = 0 // 0 undecided, 1 vertical, 2 multi-touch, -1 ignore
            var total = Offset.Zero
            var startSpan = 0f; var startCentroid = Offset.Zero
            var lastSpan = 0f; var lastCentroid = Offset.Zero
            var downFired = false
            val vt = VelocityTracker()
            val height = size.height.toFloat()
            val driveLibrary = g.swipeUp == GestureAction.OpenLibrary || g.swipeUp == GestureAction.Search
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = ev.changes.filter { it.pressed }
                if (mode == 0 && pressed.size >= 2) {
                    mode = 2
                    startCentroid = pressed.fold(Offset.Zero) { a, c -> a + c.position } / pressed.size.toFloat()
                    startSpan = pressed.map { (it.position - startCentroid).getDistance() }.average().toFloat()
                    lastSpan = startSpan; lastCentroid = startCentroid
                }
                when (mode) {
                    0 -> {
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        total += ch.positionChange()
                        vt.addPosition(ch.uptimeMillis, ch.position)
                        if (total.getDistance() < slop && ch.uptimeMillis - startTime > longPress) mode = -1
                        else if (total.getDistance() > slop) {
                            mode = if (abs(total.y) > abs(total.x) * 1.25f && ev.changes.none { it.isConsumed }) 1 else -1
                        }
                    }
                    1 -> {
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch != null) {
                            total += ch.positionChange()
                            vt.addPosition(ch.uptimeMillis, ch.position)
                            ev.changes.forEach { it.consume() }
                            if (total.y < 0 && driveLibrary) {
                                val p = (-total.y / (height * 0.45f)).coerceIn(0f, 1f)
                                state.scope.launch { state.library.snapTo(p) }
                            } else if (driveLibrary && state.library.value > 0f) {
                                state.scope.launch { state.library.snapTo(0f) }
                            }
                            if (!downFired && total.y > threshold) {
                                downFired = true
                                state.runGesture(g.swipeDown, g.swipeDownApp)
                            }
                        }
                    }
                    2 -> {
                        ev.changes.forEach { it.consume() }
                        if (pressed.size >= 2) {
                            lastCentroid = pressed.fold(Offset.Zero) { a, c -> a + c.position } / pressed.size.toFloat()
                            lastSpan = pressed.map { (it.position - lastCentroid).getDistance() }.average().toFloat()
                        }
                    }
                }
                if (pressed.isEmpty()) break
            }
            when (mode) {
                1 -> {
                    val vy = vt.calculateVelocity().y
                    if (total.y < 0) {
                        if (driveLibrary) {
                            val open = vy < -900f || state.library.value > 0.3f
                            if (open) state.openLibrary(g.swipeUp == GestureAction.Search) else state.closeLibrary()
                        } else if (-total.y > threshold) state.runGesture(g.swipeUp, g.swipeUpApp)
                    }
                }
                2 -> {
                    val dy = lastCentroid.y - startCentroid.y
                    when {
                        startSpan > 0 && lastSpan / startSpan < 0.68f -> state.runGesture(g.pinchIn, g.pinchInApp)
                        dy > threshold -> state.runGesture(g.twoFingerDown, g.twoFingerDownApp)
                        dy < -threshold -> state.runGesture(g.twoFingerUp, g.twoFingerUpApp)
                    }
                }
            }
        }
    }
}

private fun Modifier.pageTransition(state: LauncherState, page: Int, t: PageTransition): Modifier =
    this.graphicsLayer {
        val pager = state.pager
        val p = (page - pager.currentPage) - pager.currentPageOffsetFraction // >0: right of current
        val a = abs(p).coerceAtMost(1f)
        when (t) {
            PageTransition.Slide -> {}
            PageTransition.Depth -> { val s = 1f - 0.10f * a; scaleX = s; scaleY = s; alpha = 1f - 0.55f * a }
            PageTransition.Zoom -> { val s = 1f - 0.22f * a; scaleX = s; scaleY = s; alpha = 1f - a }
            PageTransition.Cube -> {
                cameraDistance = 14f * density
                transformOrigin = TransformOrigin(if (p < 0) 1f else 0f, 0.5f)
                rotationY = 70f * p.coerceIn(-1f, 1f)
            }
            PageTransition.Carousel -> {
                cameraDistance = 16f * density
                rotationY = -28f * p.coerceIn(-1f, 1f)
                val s = 1f - 0.14f * a; scaleX = s; scaleY = s; alpha = 1f - 0.4f * a
            }
            PageTransition.Fade -> { translationX = -p * size.width; alpha = 1f - a }
            PageTransition.Stack -> if (p < 0) {
                translationX = -p * size.width
                val s = 1f - 0.08f * a; scaleX = s; scaleY = s; alpha = 1f - a
            }
        }
    }

/* ------------------------------------ grid ------------------------------------ */

@Composable
private fun PageGrid(index: Int, page: HomePage) {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val h = cfg.home
    val cols = h.columns; val rows = h.rows
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = h.horizontalPaddingDp.dp)
            .onGloballyPositioned {
                if (index == state.pager.currentPage) {
                    state.gridOrigin = it.positionInRoot()
                    state.cellSize = Size(it.size.width / cols.toFloat(), it.size.height / rows.toFloat())
                }
            }
    ) {
        val cw = maxWidth / cols
        val ch = maxHeight / rows
        // Background: long-press = menu / edit, double-tap = gesture, tap in edit = done
        Box(
            Modifier.fillMaxSize().pointerInput(state.editMode, cfg.gestures.doubleTap) {
                detectTapGestures(
                    onTap = { if (state.editMode) { state.editMode = false; state.tidyPages() } },
                    onDoubleTap = if (cfg.gestures.doubleTap != GestureAction.None && !state.editMode) {
                        { state.runGesture(cfg.gestures.doubleTap, cfg.gestures.doubleTapApp) }
                    } else null,
                    onLongPress = { pos ->
                        if (!state.editMode) {
                            val o = state.gridOrigin + pos
                            state.menu = MenuRequest(null, null, Rect(o, Size(1f, 1f)), DragSource.Page(index))
                        }
                    },
                )
            }
        )
        DropPreview(index, page, cw.value, ch.value)
        for (item in page.items) key(item.id) {
            Box(
                Modifier
                    .offset(cw * item.x, ch * item.y)
                    .size(cw * item.w, ch * item.h),
                contentAlignment = Alignment.Center,
            ) {
                GridItem(item, index, cw.value, ch.value)
            }
        }
    }
}

@Composable
private fun GridItem(item: HomeItem, pageIndex: Int, cwDp: Float, chDp: Float) {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val dragging = state.drag?.let { it.active && it.item.id == item.id && it.source !is DragSource.Library } == true
    val edit = state.editMode
    val isWidget = item.isWidget
    val pad = if (isWidget) cfg.widgets.cellPaddingDp.dp else 0.dp
    Box(
        Modifier
            .then(if (isWidget) Modifier.fillMaxSize().padding(pad) else Modifier)
            .graphicsLayer { alpha = if (dragging) 0f else 1f }
            .jiggle(edit, item.id)
    ) {
        Box(
            Modifier.itemGestures(item, DragSource.Page(pageIndex)) { bounds ->
                when (item.type) {
                    ItemType.App, ItemType.Shortcut -> if (!edit) item.app?.let { state.launch(it, bounds) }
                    ItemType.Folder -> state.openFolderId = item.id
                    ItemType.Widget -> if (edit) state.widgetSettingsId = item.id else widgetTap(state, item)
                    ItemType.SystemWidget -> if (edit) state.widgetSettingsId = item.id
                }
            }.then(if (isWidget) Modifier.fillMaxSize() else Modifier)
        ) {
            ItemVisual(item, cfg.icons.sizeDp.dp, cfg.home.showLabels, if (isWidget) Modifier.fillMaxSize() else Modifier)
            if (edit && item.type == ItemType.SystemWidget) {
                // Swallow touches so the widget does not react while editing
                Box(Modifier.matchParentSize())
            }
        }
        if (edit) {
            RemoveButton(Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp)) {
                if (item.type == ItemType.Folder || item.isWidget) state.removeItem(item)
                else state.removeItem(item)
            }
            if (isWidget) ResizeHandle(item, pageIndex, cwDp, chDp, Modifier.align(Alignment.BottomEnd))
        }
    }
}

private fun widgetTap(state: LauncherState, item: HomeItem) {
    when (item.builtin) {
        dev.scyde.scycher.data.BuiltinKind.Search -> state.openLibrary(search = true)
        dev.scyde.scycher.data.BuiltinKind.Note, dev.scyde.scycher.data.BuiltinKind.Photo -> state.widgetSettingsId = item.id
        dev.scyde.scycher.data.BuiltinKind.Weather -> ScycherApp.instance.weather.refreshIfStale(force = true)
        else -> {}
    }
}

@Composable
private fun ResizeHandle(item: HomeItem, pageIndex: Int, cwDp: Float, chDp: Float, modifier: Modifier) {
    val state = LocalLauncher.current
    val density = LocalDensity.current
    val cwPx = with(density) { cwDp.dp.toPx() }; val chPx = with(density) { chDp.dp.toPx() }
    Box(
        modifier
            .offset(6.dp, 6.dp)
            .size(28.dp)
            .clip(CircleShape)
            .glass(CircleShape, SurfaceStyle.Frosted, Color(0xFF3A3A3C), 0.85f, true, 0.3f)
            .pointerInput(item.id) {
                var acc = Offset.Zero
                var start = item
                detectDragGestures(
                    onDragStart = { acc = Offset.Zero; start = state.store.value.layout.pages.getOrNull(pageIndex)?.items?.firstOrNull { it.id == item.id } ?: item; state.resizingId = item.id },
                    onDragEnd = { state.resizingId = null },
                    onDragCancel = { state.resizingId = null },
                ) { c, d ->
                    c.consume()
                    acc += d
                    val nw = (start.w + (acc.x / cwPx).roundToInt()).coerceIn(1, state.cols - start.x)
                    val nh = (start.h + (acc.y / chPx).roundToInt()).coerceIn(1, state.rows - start.y)
                    val page = state.store.value.layout.pages.getOrNull(pageIndex) ?: return@detectDragGestures
                    val cur = page.items.firstOrNull { it.id == item.id } ?: return@detectDragGestures
                    if ((nw != cur.w || nh != cur.h) && LayoutOps.fits(page, state.cols, state.rows, cur.x, cur.y, nw, nh, cur.id)) {
                        state.updateItem(cur.copy(w = nw, h = nh))
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.OpenInFull, null, tint = Color.White, modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = 90f })
    }
}

/** Shows where the dragged item will land. */
@Composable
private fun DropPreview(index: Int, page: HomePage, cwDp: Float, chDp: Float) {
    val state = LocalLauncher.current
    val target by remember(index, page) {
        derivedStateOf {
            val d = state.drag
            if (d == null || !d.active || index != state.pager.currentPage || state.dockBounds.inflate(24f).contains(d.pointer)) return@derivedStateOf null
            val cw = state.cellSize.width; val ch = state.cellSize.height
            val item = d.item
            val isIcon = item.type == ItemType.App || item.type == ItemType.Folder
            val (cx, cy) = if (isIcon) floor((d.pointer.x - state.gridOrigin.x) / cw).toInt() to floor((d.pointer.y - state.gridOrigin.y) / ch).toInt()
            else ((d.pointer.x - d.grab.x - state.gridOrigin.x) / cw).roundToInt() to ((d.pointer.y - d.grab.y - state.gridOrigin.y) / ch).roundToInt()
            val tx = cx.coerceIn(0, state.cols - item.w); val ty = cy.coerceIn(0, state.rows - item.h)
            val ignore = if (d.source is DragSource.Page) item.id else null
            val occ = if (cx in 0 until state.cols && cy in 0 until state.rows) LayoutOps.itemAt(page, cx, cy, ignore) else null
            when {
                item.type == ItemType.App && occ != null && (occ.type == ItemType.App || occ.type == ItemType.Folder) -> Triple(occ.x, occ.y, 1 to 1) to true
                LayoutOps.fits(page, state.cols, state.rows, tx, ty, item.w, item.h, ignore) -> Triple(tx, ty, item.w to item.h) to false
                else -> null
            }
        }
    }
    val t = target ?: return
    val (pos, merge) = t
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .offset((cwDp * pos.first).dp, (chDp * pos.second).dp)
            .size((cwDp * pos.third.first).dp, (chDp * pos.third.second).dp)
            .padding(6.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = if (merge) 0.22f else 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
    )
}

/* ------------------------------- indicator / edit bar ------------------------------- */

@Composable
private fun PageIndicator(count: Int, modifier: Modifier) {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val style = cfg.home.pageIndicator
    if (style == PageIndicatorStyle.Hidden || (count <= 1 && !state.editMode)) {
        Spacer(Modifier.height(10.dp)); return
    }
    val pos = state.pager.currentPage + state.pager.currentPageOffsetFraction
    Row(
        modifier
            .padding(vertical = 8.dp)
            .then(
                if (style == PageIndicatorStyle.Pill)
                    Modifier.glass(CircleShape, SurfaceStyle.Glass, Color.White, 0.14f, true, 0.18f).padding(horizontal = 10.dp, vertical = 7.dp)
                else Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            ),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until count) {
            val sel = (1f - abs(pos - i)).coerceIn(0f, 1f)
            val w = when (style) {
                PageIndicatorStyle.Lines -> 14.dp + 12.dp * sel
                PageIndicatorStyle.Pill -> 7.dp + 9.dp * sel
                else -> 7.dp
            }
            val hgt = if (style == PageIndicatorStyle.Lines) 3.dp else 7.dp
            Box(Modifier.size(w, hgt).clip(CircleShape).background(Color.White.copy(alpha = 0.35f + 0.65f * sel)))
        }
    }
}

@Composable
private fun EditBar(modifier: Modifier) {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    AnimatedVisibility(
        state.editMode, modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton("Widget", Icons.Rounded.Add) { state.widgetPicker = true }
            GlassButton("Page", Icons.Rounded.NoteAdd) { state.addPage() }
            val cur = state.pager.currentPage
            val page = cfg.layout.pages.getOrNull(cur)
            if (cfg.layout.pages.size > 1 && page != null && page.items.isEmpty())
                GlassButton("", Icons.Rounded.DeleteOutline) { state.deletePage(cur) }
            GlassButton("", Icons.Rounded.Wallpaper) { state.host.pickWallpaper() }
            GlassButton("", Icons.Rounded.Settings) { state.openSettings() }
            Spacer(Modifier.weight(1f))
            GlassButton("Done", prominent = true, accent = Color.White) { state.editMode = false; state.tidyPages() }
        }
    }
}

/** The floating copy of the item under the finger. */
@Composable
private fun DragShadow() {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val d = state.drag ?: return
    if (!d.active) return
    val density = LocalDensity.current
    val wDp = with(density) { d.size.width.toDp() }
    val hDp = with(density) { d.size.height.toDp() }
    val overDock = state.dockBounds.inflate(24f).contains(d.pointer)
    val iconSize = if (overDock) cfg.dock.iconSizeDp.dp else if (d.source == DragSource.Dock) cfg.dock.iconSizeDp.dp else cfg.icons.sizeDp.dp
    val scale by animateFloatAsState(1.1f, spring(dampingRatio = 0.6f), label = "lift")
    Box(
        Modifier
            .graphicsLayer {
                translationX = d.pointer.x - d.grab.x
                translationY = d.pointer.y - d.grab.y
                scaleX = scale; scaleY = scale
                alpha = 0.95f
            }
            .size(wDp, hDp),
        contentAlignment = Alignment.Center,
    ) {
        val isWidget = d.item.isWidget
        ItemVisual(
            d.item, iconSize, cfg.home.showLabels && !overDock,
            if (isWidget) Modifier.fillMaxSize() else Modifier, preview = true
        )
    }
}

@Suppress("unused")
private fun IntSize.toSize() = Size(width.toFloat(), height.toFloat())
