package dev.scyde.scycher.ui.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.BuiltinKind
import dev.scyde.scycher.data.HomeItem
import dev.scyde.scycher.data.IconShapes
import dev.scyde.scycher.data.SurfaceStyle
import dev.scyde.scycher.data.WeatherRepository
import dev.scyde.scycher.gestures.SystemActions
import dev.scyde.scycher.notifications.ScycherNotificationListener
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.fontFamilyOf
import dev.scyde.scycher.ui.common.glass
import dev.scyde.scycher.ui.common.toColor
import dev.scyde.scycher.ui.home.LocalLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* ----------------------------------- plumbing ----------------------------------- */

data class WidgetColors(val text: Color, val secondary: Color, val accent: Color)

@Composable
fun widgetColors(item: HomeItem): WidgetColors {
    val w = LocalConfig.current.widgets
    val text = item.extra["text"]?.toLongOrNull()?.toColor() ?: w.textColor.toColor()
    val accent = item.extra["accent"]?.toLongOrNull()?.toColor() ?: w.accentColor.toColor()
    return WidgetColors(text, text.copy(alpha = 0.62f), accent)
}

fun defaultStyleFor(kind: BuiltinKind?): String? = when (kind) {
    BuiltinKind.Greeting -> SurfaceStyle.Clear.name
    BuiltinKind.Weather -> "Auto"
    else -> null
}

@Composable
fun WidgetSurface(item: HomeItem, modifier: Modifier = Modifier, autoBrush: Brush? = null, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    val w = LocalConfig.current.widgets
    val styleName = item.extra["style"] ?: defaultStyleFor(item.builtin)
    val tint = item.extra["tint"]?.toLongOrNull()?.toColor() ?: w.tintColor.toColor()
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val shape = remember(w.cornerRadiusDp, dens) { IconShapes.continuousRect(w.cornerRadiusDp * dens) }
    val base = modifier.fillMaxSize()
    val surf = if (styleName == "Auto" && autoBrush != null) {
        base.clip(shape).background(autoBrush)
    } else {
        val style = styleName?.let { s -> SurfaceStyle.entries.firstOrNull { it.name == s } } ?: w.style
        base.glass(shape, style, tint, w.opacity, w.border)
    }
    BoxWithConstraints(surf, content = content)
}

/** Scale unit: 1f ≈ a 170dp square widget. */
private fun BoxWithConstraintsScope.unit(): Float = min(maxWidth.value / 170f, maxHeight.value / 170f).coerceIn(0.45f, 2.4f)

@Composable
private fun rememberNow(seconds: Boolean): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(seconds) {
        while (true) {
            now = ZonedDateTime.now()
            val ms = System.currentTimeMillis()
            delay(if (seconds) 1000 - ms % 1000 else 60_000 - ms % 60_000)
        }
    }
    return now
}

@Composable
private fun T(
    text: String, size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = 1, lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val font = fontFamilyOf(LocalConfig.current.look.font)
    Text(
        text, modifier, style = TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = font, lineHeight = lineHeight),
        textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun BuiltinWidget(item: HomeItem, modifier: Modifier = Modifier) {
    when (item.builtin) {
        BuiltinKind.ClockDigital -> DigitalClock(item, modifier)
        BuiltinKind.ClockAnalog -> AnalogClock(item, modifier)
        BuiltinKind.Weather -> WeatherWidget(item, modifier)
        BuiltinKind.Calendar -> CalendarWidget(item, modifier)
        BuiltinKind.Battery -> BatteryWidget(item, modifier)
        BuiltinKind.NowPlaying -> NowPlayingWidget(item, modifier)
        BuiltinKind.WorldClock -> WorldClockWidget(item, modifier)
        BuiltinKind.Note -> NoteWidget(item, modifier)
        BuiltinKind.Photo -> PhotoWidget(item, modifier)
        BuiltinKind.Search -> SearchWidget(item, modifier)
        BuiltinKind.Greeting -> GreetingWidget(item, modifier)
        null -> Box(modifier)
    }
}

/* ------------------------------------- clocks ------------------------------------- */

@Composable
private fun DigitalClock(item: HomeItem, modifier: Modifier) {
    val w = LocalConfig.current.widgets
    val c = widgetColors(item)
    val now = rememberNow(w.clockSeconds)
    val pattern = if (w.clock24h) "HH:mm" else "h:mm"
    WidgetSurface(item, modifier) {
        val u = unit()
        val wide = maxWidth > maxHeight * 1.4f
        val time = now.format(DateTimeFormatter.ofPattern(pattern))
        val timeSize = min(maxHeight.value * if (wide) 0.48f else 0.36f, maxWidth.value * if (wide) 0.22f else 0.30f).sp
        Column(
            Modifier.fillMaxSize().padding((16 * u).dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally
        ) {
            T(now.format(DateTimeFormatter.ofPattern("EEEE")).uppercase(), (12 * u).sp, c.accent, FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.Bottom) {
                T(time, timeSize, c.text, FontWeight(w.clockWeight), lineHeight = timeSize)
                if (w.clockSeconds) T(now.format(DateTimeFormatter.ofPattern(":ss")), timeSize * 0.4f, c.secondary, FontWeight(w.clockWeight), Modifier.padding(bottom = (timeSize.value * 0.12f).dp))
                if (!w.clock24h) T(if (now.hour < 12) " AM" else " PM", timeSize * 0.28f, c.secondary, FontWeight.Medium, Modifier.padding(bottom = (timeSize.value * 0.14f).dp))
            }
            T(now.format(DateTimeFormatter.ofPattern("d MMMM yyyy")), (14 * u).sp, c.secondary, FontWeight.Medium)
        }
    }
}

@Composable
private fun AnalogClock(item: HomeItem, modifier: Modifier) {
    val w = LocalConfig.current.widgets
    val c = widgetColors(item)
    val now = rememberNow(w.clockSeconds)
    WidgetSurface(item, modifier) {
        val u = unit()
        Box(Modifier.fillMaxSize().padding((12 * u).dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val r = size.minDimension / 2f
                val ctr = center
                for (i in 0 until 60) {
                    val major = i % 5 == 0
                    val a = Math.toRadians(i * 6.0 - 90)
                    val r1 = r * (if (major) 0.84f else 0.90f)
                    val r2 = r * 0.96f
                    drawLine(
                        if (major) c.text else c.secondary.copy(alpha = 0.4f),
                        Offset(ctr.x + r1 * cos(a).toFloat(), ctr.y + r1 * sin(a).toFloat()),
                        Offset(ctr.x + r2 * cos(a).toFloat(), ctr.y + r2 * sin(a).toFloat()),
                        strokeWidth = if (major) r * 0.035f else r * 0.015f, cap = StrokeCap.Round
                    )
                }
                val h = (now.hour % 12) + now.minute / 60f
                val m = now.minute + now.second / 60f
                rotate(h * 30f, ctr) { drawLine(c.text, ctr, Offset(ctr.x, ctr.y - r * 0.5f), r * 0.07f, StrokeCap.Round) }
                rotate(m * 6f, ctr) { drawLine(c.text, ctr, Offset(ctr.x, ctr.y - r * 0.76f), r * 0.045f, StrokeCap.Round) }
                if (w.clockSeconds) rotate(now.second * 6f, ctr) {
                    drawLine(c.accent, Offset(ctr.x, ctr.y + r * 0.15f), Offset(ctr.x, ctr.y - r * 0.84f), r * 0.02f, StrokeCap.Round)
                }
                drawCircle(c.accent, r * 0.05f, ctr)
                drawCircle(Color.Black, r * 0.02f, ctr)
            }
        }
    }
}

@Composable
private fun WorldClockWidget(item: HomeItem, modifier: Modifier) {
    val w = LocalConfig.current.widgets
    val c = widgetColors(item)
    val now = rememberNow(false)
    val local = now.offset.totalSeconds
    WidgetSurface(item, modifier) {
        val u = unit()
        val zones = w.worldClocks.take(if (maxHeight < 200.dp && maxWidth < 300.dp) 2 else 4)
        Column(Modifier.fillMaxSize().padding((14 * u).dp), verticalArrangement = Arrangement.SpaceEvenly) {
            for (z in zones) {
                val zt = runCatching { now.withZoneSameInstant(ZoneId.of(z)) }.getOrNull() ?: continue
                val diffH = (zt.offset.totalSeconds - local) / 3600
                val day = when (zt.toLocalDate().compareTo(now.toLocalDate())) { 0 -> "Today"; 1 -> "Tomorrow"; else -> "Yesterday" }
                val isDay = zt.hour in 6..19
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay, null, tint = if (isDay) Color(0xFFFFD60A) else c.secondary, modifier = Modifier.size((16 * u).dp))
                    Spacer(Modifier.width((8 * u).dp))
                    Column(Modifier.weight(1f)) {
                        T(z.substringAfterLast('/').replace('_', ' '), (14 * u).sp, c.text, FontWeight.SemiBold)
                        T("$day, ${if (diffH >= 0) "+" else ""}${diffH}h", (11 * u).sp, c.secondary)
                    }
                    T(zt.format(DateTimeFormatter.ofPattern(if (w.clock24h) "HH:mm" else "h:mm")), (24 * u).sp, c.text, FontWeight.Light)
                }
            }
        }
    }
}

/* ------------------------------------- weather ------------------------------------- */

fun weatherIcon(code: Int, day: Boolean): ImageVector = when (code) {
    0 -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    1, 2 -> if (day) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    3 -> Icons.Rounded.Cloud
    45, 48 -> Icons.Rounded.Dehaze
    51, 53, 55, 56, 57 -> Icons.Rounded.Grain
    61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Rounded.WaterDrop
    71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
    95, 96, 99 -> Icons.Rounded.FlashOn
    else -> Icons.Rounded.Cloud
}

private fun weatherBrush(code: Int, day: Boolean): Brush = when {
    !day -> Brush.verticalGradient(listOf(Color(0xFF0B1026), Color(0xFF2B3A67)))
    code in listOf(0, 1) -> Brush.verticalGradient(listOf(Color(0xFF2E7DD7), Color(0xFF6DB3F2)))
    code in listOf(2, 3, 45, 48) -> Brush.verticalGradient(listOf(Color(0xFF5B6B7F), Color(0xFF8E9BAE)))
    code >= 95 -> Brush.verticalGradient(listOf(Color(0xFF2C2F48), Color(0xFF575C7A)))
    else -> Brush.verticalGradient(listOf(Color(0xFF3F5770), Color(0xFF6E86A0)))
}

@Composable
private fun WeatherWidget(item: HomeItem, modifier: Modifier) {
    val repo = ScycherApp.instance.weather
    val weather by repo.weather.collectAsState()
    val cfg = LocalConfig.current.widgets
    LaunchedEffect(cfg.weatherCity, cfg.weatherFahrenheit) { repo.refreshIfStale(force = weather?.city != cfg.weatherCity) }
    val wd = weather
    val auto = item.extra["style"] == null || item.extra["style"] == "Auto"
    val c = if (auto) WidgetColors(Color.White, Color.White.copy(alpha = 0.75f), Color.White) else widgetColors(item)
    WidgetSurface(item, modifier, autoBrush = weatherBrush(wd?.code ?: 0, wd?.isDay ?: true)) {
        val u = unit()
        if (wd == null) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                T(cfg.weatherCity, (16 * u).sp, c.text, FontWeight.SemiBold)
                T("Loading weather…", (13 * u).sp, c.secondary)
            }
            return@WidgetSurface
        }
        val big = maxHeight > 300.dp && maxWidth > 260.dp
        val wide = maxWidth > maxHeight * 1.4f
        Column(Modifier.fillMaxSize().padding((16 * u).dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    T(wd.city, (15 * u).sp, c.text, FontWeight.SemiBold)
                    T("${wd.temp.toInt()}°", (46 * u).sp, c.text, FontWeight.Light, lineHeight = (50 * u).sp)
                }
                if (wide || big) Column(horizontalAlignment = Alignment.End) {
                    Icon(weatherIcon(wd.code, wd.isDay), null, tint = c.text, modifier = Modifier.size((26 * u).dp))
                    T(WeatherRepository.describe(wd.code), (13 * u).sp, c.text, FontWeight.Medium)
                    T("H:${wd.max.toInt()}°  L:${wd.min.toInt()}°", (13 * u).sp, c.text, FontWeight.Medium)
                }
            }
            if (!wide && !big) {
                Spacer(Modifier.weight(1f))
                Icon(weatherIcon(wd.code, wd.isDay), null, tint = c.text, modifier = Modifier.size((20 * u).dp))
                T(WeatherRepository.describe(wd.code), (13 * u).sp, c.text, FontWeight.Medium)
                T("H:${wd.max.toInt()}°  L:${wd.min.toInt()}°", (13 * u).sp, c.text, FontWeight.Medium)
            } else {
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (h in wd.hours.take(6)) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        T(h.hour, (12 * u).sp, c.secondary, FontWeight.Medium)
                        Icon(weatherIcon(h.code, h.isDay), null, tint = c.text, modifier = Modifier.padding(vertical = (4 * u).dp).size((18 * u).dp))
                        T("${h.temp.toInt()}°", (14 * u).sp, c.text, FontWeight.Medium)
                    }
                }
                if (big) {
                    Spacer(Modifier.height((10 * u).dp))
                    Box(Modifier.fillMaxWidth().height(0.6.dp).background(c.text.copy(alpha = 0.25f)))
                    for (d in wd.days.drop(1).take(4)) Row(Modifier.fillMaxWidth().padding(top = (8 * u).dp), verticalAlignment = Alignment.CenterVertically) {
                        val name = runCatching { LocalDate.parse(d.day).dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()) }.getOrDefault(d.day)
                        T(name, (14 * u).sp, c.text, FontWeight.Medium, Modifier.width((60 * u).dp))
                        Icon(weatherIcon(d.code, true), null, tint = c.text, modifier = Modifier.size((18 * u).dp))
                        Spacer(Modifier.weight(1f))
                        T("${d.min.toInt()}°", (14 * u).sp, c.secondary)
                        Box(Modifier.padding(horizontal = (8 * u).dp).width((60 * u).dp).height((4 * u).dp).clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(Color(0xFF64D2FF), Color(0xFFFFD60A)))))
                        T("${d.max.toInt()}°", (14 * u).sp, c.text)
                    }
                }
            }
        }
    }
}

/* ------------------------------------- calendar ------------------------------------- */

@Composable
private fun CalendarWidget(item: HomeItem, modifier: Modifier) {
    val c = widgetColors(item)
    val now = rememberNow(false)
    val today = now.toLocalDate()
    WidgetSurface(item, modifier) {
        val u = unit()
        val wide = maxWidth > maxHeight * 1.4f
        val big = maxHeight > 300.dp
        if (!wide && !big) {
            Column(Modifier.fillMaxSize().padding((16 * u).dp)) {
                T(today.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault()).uppercase(), (13 * u).sp, Color(0xFFFF453A), FontWeight.Bold)
                T(today.dayOfMonth.toString(), (58 * u).sp, c.text, FontWeight.Light, lineHeight = (60 * u).sp)
                Spacer(Modifier.weight(1f))
                T(today.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()), (14 * u).sp, c.secondary, FontWeight.Medium)
            }
        } else {
            Row(Modifier.fillMaxSize().padding((14 * u).dp)) {
                if (wide && !big) {
                    Column(Modifier.weight(0.8f).fillMaxHeight()) {
                        T(today.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault()).uppercase(), (13 * u).sp, Color(0xFFFF453A), FontWeight.Bold)
                        T(today.dayOfMonth.toString(), (52 * u).sp, c.text, FontWeight.Light, lineHeight = (56 * u).sp)
                        Spacer(Modifier.weight(1f))
                        T(today.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()), (13 * u).sp, c.secondary, FontWeight.Medium)
                    }
                    Spacer(Modifier.width((10 * u).dp))
                }
                MonthGrid(today, c, u, Modifier.weight(1.3f).fillMaxHeight(), showTitle = big)
            }
        }
    }
}

@Composable
private fun MonthGrid(today: LocalDate, c: WidgetColors, u: Float, modifier: Modifier, showTitle: Boolean) {
    val first = today.withDayOfMonth(1)
    val wf = WeekFields.of(Locale.getDefault())
    val firstDow: DayOfWeek = wf.firstDayOfWeek
    val lead = (first.dayOfWeek.value - firstDow.value + 7) % 7
    val days = today.lengthOfMonth()
    val cells = (0 until 42).map { i -> val d = i - lead + 1; if (d in 1..days) d else null }
    val weeks = cells.chunked(7).filter { w -> w.any { it != null } }
    Column(modifier, verticalArrangement = Arrangement.SpaceEvenly) {
        if (showTitle) T(today.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()).uppercase() + " " + today.year, (13 * u).sp, Color(0xFFFF453A), FontWeight.Bold)
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val dow = firstDow.plus(i.toLong())
                T(dow.getDisplayName(JTextStyle.NARROW, Locale.getDefault()), (10 * u).sp, c.secondary, FontWeight.SemiBold, Modifier.weight(1f), TextAlign.Center)
            }
        }
        for (w in weeks) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            for (d in w) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (d != null) {
                    val isToday = d == today.dayOfMonth
                    Box(
                        Modifier.size((20 * u).dp).clip(CircleShape).background(if (isToday) Color(0xFFFF453A) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) { T(d.toString(), (11 * u).sp, c.text, if (isToday) FontWeight.Bold else FontWeight.Medium, align = TextAlign.Center) }
                }
            }
        }
    }
}

/* ------------------------------------- battery ------------------------------------- */

@Composable
private fun BatteryWidget(item: HomeItem, modifier: Modifier) {
    val ctx = LocalContext.current
    val c = widgetColors(item)
    var level by remember { mutableStateOf(0) }
    var charging by remember { mutableStateOf(false) }
    var temp by remember { mutableStateOf(0f) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0); val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                level = (l * 100 / s.coerceAtLeast(1))
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
                temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            }
        }
        ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    val color = when {
        charging -> Color(0xFF30D158)
        level <= 20 -> Color(0xFFFF453A)
        else -> c.text
    }
    WidgetSurface(item, modifier) {
        val u = unit()
        val mw = maxWidth; val mh = maxHeight
        val wide = maxWidth > maxHeight * 1.4f
        val ring: @Composable (Dp) -> Unit = { sz ->
            Box(Modifier.size(sz), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = size.minDimension * 0.10f
                    val inset = sw / 2
                    drawArc(c.text.copy(alpha = 0.16f), 0f, 360f, false, Offset(inset, inset), Size(size.width - sw, size.height - sw), style = Stroke(sw))
                    drawArc(color, -90f, 360f * level / 100f, false, Offset(inset, inset), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
                }
                Icon(if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.Bolt, null, tint = c.text, modifier = Modifier.size(sz * 0.38f))
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding((18 * u).dp), verticalAlignment = Alignment.CenterVertically) {
                ring((mh.value * 0.62f).dp)
                Spacer(Modifier.width((18 * u).dp))
                Column {
                    T("$level%", (40 * u).sp, c.text, FontWeight.Light)
                    T(if (charging) "Charging" else "This tablet", (14 * u).sp, c.secondary, FontWeight.Medium)
                    T("${"%.1f".format(temp)} °C", (12 * u).sp, c.secondary)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding((14 * u).dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                ring((min(mw.value, mh.value) * 0.52f).dp)
                Spacer(Modifier.height((8 * u).dp))
                T("$level%", (22 * u).sp, c.text, FontWeight.Medium)
            }
        }
    }
}

/* ----------------------------------- now playing ----------------------------------- */

@Composable
private fun NowPlayingWidget(item: HomeItem, modifier: Modifier) {
    val ctx = LocalContext.current
    val c = widgetColors(item)
    val np by ScycherNotificationListener.nowPlaying.collectAsState()
    val connected by ScycherNotificationListener.connected.collectAsState()
    val art = remember(np?.art) { np?.art?.asImageBitmap() }
    WidgetSurface(item, modifier) {
        val u = unit()
        val mw = maxWidth; val mh = maxHeight
        val wide = maxWidth > maxHeight * 1.4f
        val n = np
        if (!connected && !ScycherNotificationListener.isEnabled(ctx)) {
            Column(
                Modifier.fillMaxSize().clickable { SystemActions.openNotificationAccess(ctx) }.padding((16 * u).dp),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.MusicNote, null, tint = c.accent, modifier = Modifier.size((28 * u).dp))
                Spacer(Modifier.height((8 * u).dp))
                T("Now Playing", (16 * u).sp, c.text, FontWeight.SemiBold)
                T("Tap to allow access", (12 * u).sp, c.secondary, maxLines = 2)
            }
            return@WidgetSurface
        }
        val artBox: @Composable (Dp) -> Unit = { sz ->
            Box(Modifier.size(sz).clip(RoundedCornerShape(sz * 0.18f)).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.MusicNote, null, tint = c.secondary, modifier = Modifier.size(sz * 0.45f))
            }
        }
        val controls: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((14 * u).dp)) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = c.text, modifier = Modifier.size((28 * u).dp).clickable { n?.controller?.transportControls?.skipToPrevious() })
                Icon(if (n?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = c.text,
                    modifier = Modifier.size((36 * u).dp).clickable { n?.controller?.transportControls?.let { if (n.playing) it.pause() else it.play() } })
                Icon(Icons.Rounded.SkipNext, null, tint = c.text, modifier = Modifier.size((28 * u).dp).clickable { n?.controller?.transportControls?.skipToNext() })
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding((16 * u).dp), verticalAlignment = Alignment.CenterVertically) {
                artBox((mh.value * 0.66f).dp)
                Spacer(Modifier.width((16 * u).dp))
                Column(Modifier.weight(1f)) {
                    T(n?.title?.ifEmpty { null } ?: "Not Playing", (17 * u).sp, c.text, FontWeight.SemiBold)
                    T(n?.artist ?: "", (14 * u).sp, c.secondary)
                    Spacer(Modifier.height((10 * u).dp))
                    controls()
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding((14 * u).dp)) {
                artBox((min(mw.value, mh.value) * 0.38f).dp)
                Spacer(Modifier.weight(1f))
                T(n?.title?.ifEmpty { null } ?: "Not Playing", (14 * u).sp, c.text, FontWeight.SemiBold)
                T(n?.artist ?: "", (12 * u).sp, c.secondary)
                Spacer(Modifier.height((6 * u).dp))
                controls()
            }
        }
    }
}

/* ---------------------------------- note / photo ---------------------------------- */

@Composable
private fun NoteWidget(item: HomeItem, modifier: Modifier) {
    val c = widgetColors(item)
    WidgetSurface(item, modifier) {
        val u = unit()
        Column(Modifier.fillMaxSize().padding((16 * u).dp)) {
            T(item.extra["title"] ?: "Note", (14 * u).sp, Color(0xFFFFD60A), FontWeight.Bold)
            Spacer(Modifier.height((6 * u).dp))
            T(item.extra["note"]?.ifBlank { null } ?: "Tap in edit mode to write something.", (15 * u).sp, c.text, maxLines = 20, lineHeight = (20 * u).sp)
        }
    }
}

@Composable
private fun PhotoWidget(item: HomeItem, modifier: Modifier) {
    val c = widgetColors(item)
    val path = item.extra["photo"]
    val bmp by produceState<ImageBitmap?>(null, path, item.extra["v"]) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val f = File(path)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, o)
                var s = 1
                while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 1100) s *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }.getOrNull()
        }
    }
    WidgetSurface(item, modifier) {
        val img = bmp
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.PhotoLibrary, null, tint = c.text, modifier = Modifier.size(32.dp))
            T("Choose a photo", 14.sp, c.secondary)
        }
    }
}

/* ---------------------------------- search / greeting ---------------------------------- */

@Composable
private fun SearchWidget(item: HomeItem, modifier: Modifier) {
    val c = widgetColors(item)
    val state = LocalLauncher.current
    val w = LocalConfig.current.widgets
    val styleName = item.extra["style"]
    val style = styleName?.let { s -> SurfaceStyle.entries.firstOrNull { it.name == s } } ?: w.style
    val tint = item.extra["tint"]?.toLongOrNull()?.toColor() ?: w.tintColor.toColor()
    Box(Modifier.fillMaxSize().then(modifier), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).glass(CircleShape, style, tint, w.opacity, w.border)
                .clickable { state.openLibrary(search = true) }.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = c.secondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            T("Search", 17.sp, c.secondary)
        }
    }
}

@Composable
private fun GreetingWidget(item: HomeItem, modifier: Modifier) {
    val c = widgetColors(item)
    val now = rememberNow(false)
    val greet = when (now.hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; in 18..22 -> "Good evening"; else -> "Good night" }
    val name = item.extra["name"]?.takeIf { it.isNotBlank() }
    WidgetSurface(item, modifier) {
        val u = min(maxHeight.value / 100f, maxWidth.value / 360f).coerceIn(0.5f, 2.5f)
        Column(Modifier.fillMaxSize().padding(horizontal = (8 * u).dp), verticalArrangement = Arrangement.Center) {
            T(if (name != null) "$greet, $name" else greet, (34 * u).sp, c.text, FontWeight.Bold)
            T(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), (17 * u).sp, c.secondary, FontWeight.Medium)
        }
    }
}

/* ---------------------------------- system widgets ---------------------------------- */

@Composable
fun SystemWidget(item: HomeItem, modifier: Modifier, preview: Boolean) {
    val app = ScycherApp.instance
    val state = LocalLauncher.current
    val w = LocalConfig.current.widgets
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    val shape = remember(w.cornerRadiusDp, dens) { IconShapes.continuousRect(w.cornerRadiusDp * dens) }
    if (preview) {
        val label = remember(item.widgetId) { app.widgets.info(item.widgetId)?.loadLabel(app.packageManager) ?: "Widget" }
        Box(modifier.fillMaxSize().glass(shape, SurfaceStyle.Glass, Color(0xFF1C1C24), 0.4f, true), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        return
    }
    BoxWithConstraints(
        modifier.fillMaxSize().then(
            if (w.systemWidgetGlass) Modifier.glass(shape, w.style, w.tintColor.toColor(), w.opacity, w.border) else Modifier.clip(shape)
        )
    ) {
        val wDp = maxWidth.value; val hDp = maxHeight.value
        AndroidView(
            factory = { ctx ->
                app.widgets.view(ctx, item.widgetId)?.also { v ->
                    v.onLongPress = {
                        val loc = IntArray(2); v.getLocationInWindow(loc)
                        state.menu = dev.scyde.scycher.ui.MenuRequest(
                            item, null,
                            androidx.compose.ui.geometry.Rect(loc[0].toFloat(), loc[1].toFloat(), (loc[0] + v.width).toFloat(), (loc[1] + v.height).toFloat()),
                            dev.scyde.scycher.ui.DragSource.Page(state.currentPage())
                        )
                    }
                } ?: FrameLayout(ctx)
            },
            update = { v -> (v as? dev.scyde.scycher.widgets.ScycherWidgetView)?.applySize(wDp, hDp) },
            modifier = Modifier.fillMaxSize().clip(shape),
        )
    }
}

