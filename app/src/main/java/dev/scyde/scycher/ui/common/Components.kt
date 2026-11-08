package dev.scyde.scycher.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.data.IconShapes
import dev.scyde.scycher.data.SurfaceStyle

val PanelShape = IconShapes.continuousRect(64f)

/** Modal glass panel used for menus / sheets / dialogs. */
fun Modifier.panelGlass(radiusPx: Float = 70f): Modifier =
    this.glass(IconShapes.continuousRect(radiusPx), SurfaceStyle.Liquid, Color(0xFF1E1E24), 1.05f, border = true, borderOpacity = 0.2f)

@Composable
fun Scrim(visible: Boolean, alpha: Float = 0.35f, onDismiss: () -> Unit) {
    val a by animateFloatAsState(if (visible) alpha else 0f, label = "scrim")
    if (a > 0.001f) Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = a))
            .pointerInput(visible) { if (visible) detectTapGestures { onDismiss() } }
    )
}

@Composable
fun BottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    maxWidth: Int = 760,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scrim(visible, 0.4f, onDismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it } + fadeIn(),
            exit = slideOutVertically(spring(stiffness = 600f)) { it } + fadeOut(),
        ) {
            Column(
                Modifier
                    .widthIn(max = maxWidth.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .panelGlass(80f)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(top = 10.dp)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(40.dp, 5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
fun GlassButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    accent: Color = Color(0xFFB9A7FF),
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "btn")
    Row(
        modifier
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(CircleShape)
            .then(
                if (prominent) Modifier.background(accent)
                else Modifier.glass(CircleShape, SurfaceStyle.Liquid, Color(0xFF2A2A30), 0.8f, true, 0.2f)
            )
            .clickable(src, null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val c = if (prominent) Color.Black else Color.White
        if (icon != null) {
            Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
            if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
        }
        if (text.isNotEmpty()) Text(text, color = c, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun MenuRow(text: String, icon: ImageVector?, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (destructive) Color(0xFFFF453A) else Color.White
        Text(text, color = c, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (icon != null) Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MenuDivider(thick: Boolean = false) {
    Box(Modifier.fillMaxWidth().height(if (thick) 7.dp else 0.6.dp).background(Color.White.copy(alpha = if (thick) 0.05f else 0.12f)))
}

val Palette = listOf(
    0xFFFFFFFF, 0xFF1C1C1E, 0xFF8E8E93, 0xFFFF3B30, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF,
    0xFF0A84FF, 0xFF5E5CE6, 0xFFB9A7FF, 0xFFBF5AF2, 0xFFFF375F, 0xFFAC8E68, 0xFF00C7BE, 0xFF2C2C2E,
)

@Composable
fun ColorPalette(selected: Long, colors: List<Long> = Palette, onPick: (Long) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (c in colors) {
            val sel = (c and 0xFFFFFFFF) == (selected and 0xFFFFFFFF)
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(c.toColor())
                    .border(if (sel) 3.dp else 1.dp, if (sel) Color.White else Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable { onPick(c) }
            )
        }
    }
}

@Composable
fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (o in options) {
            val sel = o == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Color.White else Color.White.copy(alpha = 0.10f))
                    .clickable { onPick(o) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(label(o), color = if (sel) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
fun CenterDialog(visible: Boolean, onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Scrim(visible, 0.45f, onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp).panelGlass()
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(20.dp),
                content = content
            )
        }
    }
}

val LabelStyle = TextStyle(color = Color.White, fontSize = 13.sp)
