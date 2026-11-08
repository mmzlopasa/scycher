package dev.scyde.scycher.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.BadgeStyle
import dev.scyde.scycher.data.IconShapes
import dev.scyde.scycher.data.HomeItem
import dev.scyde.scycher.data.ItemType
import dev.scyde.scycher.data.SurfaceStyle
import dev.scyde.scycher.ui.DragSource
import dev.scyde.scycher.ui.LauncherState
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.fontFamilyOf
import dev.scyde.scycher.ui.common.glass
import dev.scyde.scycher.ui.common.toColor
import kotlin.math.abs

val LocalBadges = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }
val LocalLauncher = staticCompositionLocalOf<LauncherState> { error("no launcher") }

@Composable
fun rememberAppIcon(key: String?): ImageBitmap? {
    if (key == null) return null
    val repo = ScycherApp.instance.apps
    val gen by repo.iconGeneration.collectAsState()
    return produceState(repo.cached(key), key, gen) { value = repo.icon(key) }.value
}

@Composable
fun labelStyle(color: Color? = null, size: Float? = null): TextStyle {
    val c = LocalConfig.current
    return TextStyle(
        color = color ?: c.home.labelColor.toColor(),
        fontSize = (size ?: c.home.labelSizeSp).sp,
        fontWeight = FontWeight(c.home.labelWeight),
        fontFamily = fontFamilyOf(c.look.font),
        textAlign = TextAlign.Center,
        shadow = if (c.home.labelShadow) Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1.5f), 6f) else null,
    )
}

@Stable
private class BoundsHolder {
    var root = Rect.Zero
    var window = Rect.Zero
    var size = IntSize.Zero
}

fun Rect.toAndroid() = android.graphics.Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

/** Tap + long-press menu + drag & drop behaviour shared by every home item. */
fun Modifier.itemGestures(
    item: HomeItem,
    source: DragSource,
    onTap: (android.graphics.Rect) -> Unit,
): Modifier = composed {
    val state = LocalLauncher.current
    val holder = remember { BoundsHolder() }
    val haptic = LocalHapticFeedback.current
    val slop = with(LocalDensity.current) { 12.dp.toPx() }
    val current by rememberUpdatedState(item)
    val tap by rememberUpdatedState(onTap)
    val edit = state.editMode && source !is DragSource.Library
    val haptics = LocalConfig.current.gestures.haptics
    this
        .onGloballyPositioned {
            holder.root = it.boundsInRoot(); holder.window = it.boundsInWindow(); holder.size = it.size
        }
        .pointerInput(item.id, source) {
            detectTapGestures(onTap = { tap(holder.window.toAndroid()) })
        }
        .pointerInput(item.id, source, edit) {
            if (edit) {
                detectDragGestures(
                    onDragStart = { local ->
                        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.beginDrag(current, source, holder.root.topLeft + local, local, holder.size, holder.root)
                    },
                    onDrag = { c, a -> c.consume(); state.dragBy(a, slop) },
                    onDragEnd = { state.endDrag() },
                    onDragCancel = { state.cancelDrag() },
                )
            } else {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.beginDrag(current, source, holder.root.topLeft + local, local, holder.size, holder.root)
                    },
                    onDrag = { c, a -> c.consume(); state.dragBy(a, slop) },
                    onDragEnd = { state.endDrag() },
                    onDragCancel = { state.cancelDrag() },
                )
            }
        }
}

/** Registers icons with a swipe-up action and smart stacks so home gestures leave them alone. */
fun Modifier.swipeZone(item: HomeItem): Modifier = composed {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val action = when {
        item.type == ItemType.App && item.app != null -> cfg.iconSwipeUp[item.app]?.let { "app:$it" }
        item.builtin == dev.scyde.scycher.data.BuiltinKind.Stack -> "stack"
        else -> null
    }
    androidx.compose.runtime.DisposableEffect(item.id, action) {
        onDispose { state.swipeZones.remove(item.id) }
    }
    if (action == null) this
    else this.onGloballyPositioned { state.swipeZones[item.id] = it.boundsInRoot() to action }
}

/** Jiggle like iOS edit mode. */
fun Modifier.jiggle(enabled: Boolean, seed: Long): Modifier = composed {
    if (!enabled) return@composed this
    val t = rememberInfiniteTransition(label = "jiggle")
    val dur = 240 + (abs(seed) % 60).toInt()
    val r by t.animateFloat(-1.4f, 1.4f, infiniteRepeatable(tween(dur), RepeatMode.Reverse), label = "rot")
    this.graphicsLayer { rotationZ = r }
}

@Composable
fun IconImage(img: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    val c = LocalConfig.current.icons
    val shape = remember(c.shape) { IconShapes.composeShape(c.shape) }
    val elevation = if (c.shadow) (size.value * 0.10f * c.shadowStrength / 0.35f).dp else 0.dp
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                shadowElevation = elevation.toPx()
                this.shape = shape
                clip = false
                ambientShadowColor = Color.Black
                spotShadowColor = Color.Black
            }
    ) {
        if (img != null) Image(img, null, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().clip(shape).background(Color.White.copy(alpha = 0.12f)))
    }
}

@Composable
fun Badge(count: Int, iconSize: Dp, modifier: Modifier = Modifier) {
    val c = LocalConfig.current.icons
    if (count <= 0 || c.badgeStyle == BadgeStyle.Hidden) return
    val color = c.badgeColor.toColor()
    if (c.badgeStyle == BadgeStyle.Dot) {
        Box(modifier.size(iconSize * 0.24f).clip(CircleShape).background(color))
    } else {
        val h = (iconSize.value * 0.34f).coerceIn(16f, 26f).dp
        Box(
            modifier.height(h).widthIn(min = h).clip(CircleShape).background(color).padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = (h.value * 0.55f).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun PressScale(pressed: Boolean): Float {
    val target = if (pressed) LocalConfig.current.icons.pressScale else 1f
    return animateFloatAsState(target, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "press").value
}

@Composable
fun AppIcon(
    key: String,
    iconSize: Dp,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
    labelColor: Color? = null,
) {
    val repo = ScycherApp.instance.apps
    val img = rememberAppIcon(key)
    val badges = LocalBadges.current
    val pkg = key.substringBefore('/')
    val cfg = LocalConfig.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            IconImage(img, iconSize)
            Badge(badges[pkg] ?: 0, iconSize, Modifier.align(Alignment.TopEnd).offset(x = iconSize * 0.12f, y = -iconSize * 0.08f))
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                labelOverride ?: (cfg.customLabels[key] ?: repo.map.collectAsState().value[key]?.label ?: ""),
                style = labelStyle(labelColor),
                maxLines = cfg.home.labelMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = iconSize * 1.45f),
            )
        }
    }
}

@Composable
fun FolderIcon(item: HomeItem, iconSize: Dp, showLabel: Boolean, modifier: Modifier = Modifier) {
    val cfg = LocalConfig.current
    val shape = remember(cfg.icons.shape) { IconShapes.composeShape(cfg.icons.shape) }
    val badges = LocalBadges.current
    val count = item.apps.sumOf { badges[it.substringBefore('/')] ?: 0 }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier.size(iconSize).glass(shape, SurfaceStyle.Glass, Color.White, 0.22f, true, 0.25f).padding(iconSize * 0.12f)
            ) {
                val mini = (iconSize * 0.76f - 4.dp) / 3
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (c in 0 until 3) {
                            val k = item.apps.getOrNull(r * 3 + c)
                            val img = rememberAppIcon(k)
                            if (img != null) Image(img, null, Modifier.size(mini))
                            else Spacer(Modifier.size(mini))
                        }
                    }
                }
            }
            Badge(count, iconSize, Modifier.align(Alignment.TopEnd).offset(x = iconSize * 0.12f, y = -iconSize * 0.08f))
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(item.name ?: "Folder", style = labelStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = iconSize * 1.45f))
        }
    }
}

/** Little "−" button in edit mode. */
@Composable
fun RemoveButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .glass(CircleShape, SurfaceStyle.Frosted, Color(0xFF3A3A3C), 0.85f, true, 0.3f)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Remove, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** Renders any home item (used on pages, dock and the drag shadow). */
@Composable
fun ItemVisual(item: HomeItem, iconSize: Dp, showLabel: Boolean, modifier: Modifier = Modifier, preview: Boolean = false) {
    when (item.type) {
        ItemType.App -> AppIcon(item.app ?: "", iconSize, showLabel, modifier)
        ItemType.Folder -> FolderIcon(item, iconSize, showLabel, modifier)
        ItemType.Widget -> dev.scyde.scycher.ui.widgets.BuiltinWidget(item, modifier)
        ItemType.SystemWidget -> dev.scyde.scycher.ui.widgets.SystemWidget(item, modifier, preview)
        ItemType.Shortcut -> AppIcon(item.app ?: "", iconSize, showLabel, modifier, labelOverride = item.name)
    }
}

@Stable
class MutableBox<T>(v: T) { var value by mutableStateOf(v) }
