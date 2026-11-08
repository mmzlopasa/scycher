package dev.scyde.scycher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.HomeItem
import dev.scyde.scycher.data.IconShapes
import dev.scyde.scycher.data.ItemType
import dev.scyde.scycher.ui.DragSource
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.glass
import dev.scyde.scycher.ui.common.toColor
import kotlin.math.abs
import kotlin.math.max
import androidx.compose.runtime.collectAsState

@Composable
fun Dock(modifier: Modifier = Modifier) {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val d = cfg.dock
    if (!d.enabled) return
    val density = LocalDensity.current
    val items = cfg.layout.dock
    val apps by ScycherApp.instance.apps.map.collectAsState()
    val recents = if (d.showRecents) cfg.recentApps
        .filter { k -> k in apps && items.none { it.app == k || k in it.apps } && k !in cfg.hiddenApps }
        .take(d.recentsCount) else emptyList()

    var touchX by remember { mutableFloatStateOf(-1f) }
    val iconPx = with(density) { d.iconSizeDp.dp.toPx() }
    val centers = remember { HashMap<Int, Float>() }

    val shape = remember(d.cornerRadiusDp, density) { IconShapes.continuousRect(with(density) { d.cornerRadiusDp.dp.toPx() }) }
    Box(modifier.fillMaxWidth().padding(bottom = d.bottomMarginDp.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .then(if (d.fullWidth) Modifier.fillMaxWidth().padding(horizontal = 12.dp) else Modifier)
                .graphicsLayer {
                    if (d.shadow) { shadowElevation = 18.dp.toPx(); this.shape = shape; clip = false; ambientShadowColor = Color.Black.copy(alpha = 0.6f) }
                }
                .glass(shape, d.style, d.tintColor.toColor(), d.opacity, d.border, d.borderOpacity)
                .onGloballyPositioned { state.dockBounds = it.boundsInRoot() }
                .pointerInput(d.magnify) {
                    if (!d.magnify) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = e.changes.firstOrNull() ?: continue
                            touchX = if (ch.pressed && state.drag == null) ch.position.x else -1f
                        }
                    }
                }
                .padding(horizontal = d.paddingDp.dp + 2.dp, vertical = d.paddingDp.dp),
            horizontalArrangement = if (d.fullWidth) Arrangement.SpaceEvenly else Arrangement.spacedBy(d.iconSpacingDp.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (items.isEmpty() && recents.isEmpty()) {
                Box(Modifier.width(220.dp).height(d.iconSizeDp.dp))
            }
            items.forEachIndexed { i, item ->
                DockSlot(item, d.iconSizeDp, d.showLabels, touchX, iconPx, DragSource.Dock,
                    onCenter = { cx -> centers[i] = cx; state.dockCenters = (0 until items.size).map { centers[it] ?: 0f } })
            }
            if (recents.isNotEmpty()) {
                if (items.isNotEmpty()) Box(
                    Modifier.padding(horizontal = 2.dp).width(1.dp).height(d.iconSizeDp.dp * 0.8f)
                        .align(Alignment.CenterVertically).background(Color.White.copy(alpha = 0.25f))
                )
                for (k in recents) {
                    DockSlot(HomeItem(-k.hashCode().toLong() - 10, ItemType.App, app = k), d.iconSizeDp, d.showLabels, touchX, iconPx, DragSource.Library, onCenter = {})
                }
            }
        }
    }
}

@Composable
private fun DockSlot(
    item: HomeItem,
    iconDp: Int,
    showLabel: Boolean,
    touchX: Float,
    iconPx: Float,
    source: DragSource,
    onCenter: (Float) -> Unit,
) {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    var localCenter by remember { mutableFloatStateOf(0f) }
    val dragging = state.drag?.let { it.active && it.item.id == item.id } == true
    val mag = if (touchX >= 0f && cfg.dock.magnify) {
        val dist = abs(touchX - localCenter)
        1f + 0.28f * max(0f, 1f - dist / (iconPx * 2.2f))
    } else 1f
    val scale by animateFloatAsState(mag, spring(dampingRatio = 0.7f, stiffness = 900f), label = "mag")
    Box(
        Modifier
            .onGloballyPositioned {
                val b = it.boundsInRoot()
                onCenter(b.center.x)
                // position relative to the dock row for magnification
                localCenter = b.center.x - state.dockBounds.left
            }
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
                alpha = if (dragging) 0f else 1f
            }
            .jiggle(state.editMode && source == DragSource.Dock, item.id)
            .itemGestures(item, source) { bounds ->
                when (item.type) {
                    ItemType.Folder -> state.openFolderId = item.id
                    else -> if (!state.editMode) item.app?.let { state.launch(it, bounds) }
                }
            }
    ) {
        ItemVisual(item, iconDp.dp, showLabel)
    }
}
