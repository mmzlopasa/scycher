package dev.scyde.scycher.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.LauncherConfig
import dev.scyde.scycher.ui.common.Chips
import dev.scyde.scycher.ui.common.ColorPalette
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.toColor
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    val nav = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        intent.getStringExtra("screen")?.let { nav += it }
        setContent { SettingsRoot(this) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.value++
    }

    companion object { val resumeTick = kotlinx.coroutines.flow.MutableStateFlow(0) }
}

val LocalNav = staticCompositionLocalOf<SettingsActivity> { error("nav") }
val Bg = Color(0xFF000000)
val Card = Color(0xFF1C1C1E)
val Sub = Color(0xFF8E8E93)

fun update(block: (LauncherConfig) -> LauncherConfig) = ScycherApp.instance.store.update(block)

@Composable
fun SettingsRoot(activity: SettingsActivity) {
    val cfg by ScycherApp.instance.store.config.collectAsState()
    val accent = cfg.look.accentColor.toColor()
    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = Bg, surface = Card)) {
        CompositionLocalProvider(LocalConfig provides cfg, LocalNav provides activity) {
            var unlocked by remember { mutableStateOf(!cfg.security.lockSettings || cfg.security.pinHash == null || dev.scyde.scycher.data.Security.recentlyUnlocked(cfg.security)) }
            if (!unlocked) { SettingsGate { unlocked = true }; return@CompositionLocalProvider }
            val top = activity.nav.lastOrNull()
            BackHandler(top != null) { activity.nav.removeAt(activity.nav.lastIndex) }
            AnimatedContent(
                activity.nav.size to top, Modifier.fillMaxSize().background(Bg),
                transitionSpec = {
                    if (targetState.first > initialState.first) (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(240))) togetherWith (slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(180)))
                    else (slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(240))) togetherWith (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(180)))
                },
                label = "nav"
            ) { (_, screen) ->
                when (screen) {
                    null -> MainScreen()
                    "home" -> HomeScreenSettings()
                    "icons" -> IconSettings()
                    "dock" -> DockSettings()
                    "widgets" -> WidgetSettings()
                    "library" -> LibrarySettings()
                    "gestures" -> GestureSettings()
                    "homegestures" -> HomeGestureSettings()
                    "appearance" -> AppearanceSettings()
                    "hidden" -> HiddenApps()
                    "backup" -> BackupSettings()
                    "about" -> AboutScreen()
                    "security" -> SecuritySettings()
                    "search" -> SearchSettings()
                    else -> MainScreen()
                }
            }
        }
    }
}

/* ------------------------------------ building blocks ------------------------------------ */

@Composable
fun Page(title: String, back: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back) Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { if (nav.nav.isNotEmpty()) nav.nav.removeAt(nav.nav.lastIndex) else nav.finish() }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = LocalConfig.current.look.accentColor.toColor(), modifier = Modifier.size(18.dp))
                Text("Back", color = LocalConfig.current.look.accentColor.toColor(), fontSize = 17.sp)
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 18.dp)) {
                Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                content()
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun Group(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) Text(
        title.uppercase(), color = Sub, fontSize = 13.sp, letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 7.dp)
    ) else Spacer(Modifier.height(18.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card), content = content)
    if (footer != null) Text(footer, color = Sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp))
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(Color(0xFF38383A)))

@Composable
fun NavRow(title: String, icon: ImageVector?, iconBg: Color = Color(0xFF636366), value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Text(title, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = Sub, fontSize = 16.sp)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color(0xFF5A5A5E))
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = Sub, fontSize = 13.sp)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF30D158), checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF39393D), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent)
        )
    }
}

@Composable
fun SliderRow(
    title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0,
    format: (Float) -> String = { it.roundToInt().toString() }, onChange: (Float) -> Unit,
) {
    val accent = LocalConfig.current.look.accentColor.toColor()
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Text(title, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(format(local), color = Sub, fontSize = 16.sp)
        }
        Slider(
            local, { local = it; onChange(it) }, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent, inactiveTrackColor = Color(0xFF39393D), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
        )
    }
}

@Composable
fun IntSlider(title: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) =
    SliderRow(title, value.toFloat(), range.first.toFloat()..range.last.toFloat(), steps = (range.last - range.first - 1).coerceIn(0, 100),
        format = { "${it.roundToInt()}$suffix" }) { onChange(it.roundToInt()) }

@Composable
fun PercentSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f, onChange: (Float) -> Unit) =
    SliderRow(title, value, range, format = { "${(it * 100).roundToInt()}%" }, onChange = onChange)

@Composable
fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String = { it.toString() }, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp)
        Chips(options, selected, label, onPick)
    }
}

@Composable
fun ColorRow(title: String, value: Long, onPick: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp)
        ColorPalette(value, onPick = onPick)
        HueSlider(value, onPick)
    }
}

/** Custom color: hue + lightness sliders. */
@Composable
private fun HueSlider(value: Long, onPick: (Long) -> Unit) {
    val hsv = remember(value) { FloatArray(3).also { android.graphics.Color.colorToHSV(value.toInt(), it) } }
    var hue by remember(value) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(value) { mutableFloatStateOf(hsv[1]) }
    var v by remember(value) { mutableFloatStateOf(hsv[2]) }
    var open by remember { mutableStateOf(false) }
    Text(if (open) "Hide custom color" else "Custom color…", color = LocalConfig.current.look.accentColor.toColor(), fontSize = 14.sp,
        modifier = Modifier.clickable { open = !open }.padding(vertical = 4.dp))
    if (open) {
        fun emit() = onPick(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, v)).toLong() and 0xFFFFFFFFL)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hue", color = Sub, fontSize = 13.sp, modifier = Modifier.width(70.dp)); Slider(hue, { hue = it; emit() }, valueRange = 0f..360f)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Saturation", color = Sub, fontSize = 13.sp, modifier = Modifier.width(70.dp)); Slider(sat, { sat = it; emit() })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Brightness", color = Sub, fontSize = 13.sp, modifier = Modifier.width(70.dp)); Slider(v, { v = it; emit() })
        }
    }
}

@Composable
fun InfoText(text: String, color: Color = Sub) {
    Text(text, color = color, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}
