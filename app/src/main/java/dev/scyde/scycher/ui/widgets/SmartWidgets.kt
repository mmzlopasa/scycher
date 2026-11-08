package dev.scyde.scycher.ui.widgets

import android.Manifest
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.data.BuiltinKind
import dev.scyde.scycher.data.HomeItem
import dev.scyde.scycher.gestures.SystemActions
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.home.LocalLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

/* ------------------------------------ smart stack ------------------------------------ */

val StackDefaults = listOf(BuiltinKind.ClockDigital, BuiltinKind.Weather, BuiltinKind.Calendar, BuiltinKind.Battery)
val Stackable = BuiltinKind.entries.filter { it != BuiltinKind.Stack && it != BuiltinKind.Search && it != BuiltinKind.Photo }

fun stackKinds(item: HomeItem): List<BuiltinKind> =
    item.extra["kinds"]?.split(',')?.mapNotNull { n -> BuiltinKind.entries.firstOrNull { it.name == n } }
        ?.filter { it != BuiltinKind.Stack }?.takeIf { it.isNotEmpty() } ?: StackDefaults

/** Several widgets in one slot – swipe up/down or let it rotate on its own. */
@Composable
fun StackWidget(item: HomeItem, modifier: Modifier) {
    val state = LocalLauncher.current
    val kinds = stackKinds(item)
    val pager = rememberPagerState { kinds.size }
    val rotate = item.extra["rotate"] != "false"
    LaunchedEffect(kinds.size, rotate) {
        if (!rotate || kinds.size < 2) return@LaunchedEffect
        while (true) {
            delay(12_000)
            if (!pager.isScrollInProgress && !state.editMode) pager.animateScrollToPage((pager.currentPage + 1) % kinds.size)
        }
    }
    Box(modifier.fillMaxSize()) {
        VerticalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !state.editMode, beyondViewportPageCount = 1) { i ->
            val inner = remember(item, i) { item.copy(builtin = kinds[i], extra = item.extra - "kinds" - "rotate") }
            BuiltinWidget(inner, Modifier.fillMaxSize())
        }
        if (kinds.size > 1) Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in kinds.indices) Box(
                Modifier.size(5.dp, if (i == pager.currentPage) 12.dp else 5.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.9f else 0.4f))
            )
        }
    }
}

/* -------------------------------------- clock variants -------------------------------------- */

@Composable
internal fun ClockVariant(item: HomeItem, modifier: Modifier, variant: String, now: ZonedDateTime, pattern: String) {
    val w = LocalConfig.current.widgets
    val c = widgetColors(item)
    WidgetSurface(item, modifier) {
        val u = unit()
        val mw = maxWidth; val mh = maxHeight
        when (variant) {
            "Stacked" -> {
                val hh = now.format(DateTimeFormatter.ofPattern(if (w.clock24h) "HH" else "hh"))
                val mm = now.format(DateTimeFormatter.ofPattern("mm"))
                val size = min(mh.value * 0.36f, mw.value * 0.42f).sp
                Row(Modifier.fillMaxSize().padding((14 * u).dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(hh, size, c.text, FontWeight.Black, lineHeight = size * 0.92f)
                        T(mm, size, c.accent, FontWeight.Black, lineHeight = size * 0.92f)
                    }
                    if (mw > mh * 1.3f) Column(horizontalAlignment = Alignment.End) {
                        T(now.format(DateTimeFormatter.ofPattern("EEEE")), (18 * u).sp, c.text, FontWeight.SemiBold)
                        T(now.format(DateTimeFormatter.ofPattern("d MMMM")), (15 * u).sp, c.secondary)
                    }
                }
            }
            else -> { // Minimal
                val size = min(mh.value * 0.5f, mw.value * 0.24f).sp
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    T(now.format(DateTimeFormatter.ofPattern(pattern)), size, c.text, FontWeight.Thin, lineHeight = size)
                    T(now.format(DateTimeFormatter.ofPattern("EEE d")).uppercase(), (12 * u).sp, c.secondary, FontWeight.Medium)
                }
            }
        }
    }
}

/* ------------------------------------------ agenda ------------------------------------------ */

data class AgendaEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int)

@Composable
fun AgendaWidget(item: HomeItem, modifier: Modifier) {
    val ctx = LocalContext.current
    val state = LocalLauncher.current
    val c = widgetColors(item)
    val tick = state.resumeTick
    val granted = remember(tick) { ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED }
    val events by produceState<List<AgendaEvent>>(emptyList(), granted, tick) {
        if (!granted) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                    .appendPath(now.toString()).appendPath((now + 7L * 86_400_000).toString()).build()
                val proj = arrayOf(
                    CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
                )
                ctx.contentResolver.query(uri, proj, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cur ->
                    val out = ArrayList<AgendaEvent>()
                    while (cur.moveToNext() && out.size < 8) {
                        val end = cur.getLong(2)
                        if (end < now) continue
                        out += AgendaEvent(cur.getString(0) ?: "(No title)", cur.getLong(1), end, cur.getInt(3) == 1, cur.getInt(4))
                    }
                    out
                } ?: emptyList()
            }.getOrDefault(emptyList())
        }
    }
    WidgetSurface(item, modifier) {
        val u = unit()
        val mh = maxHeight
        val today = LocalDate.now()
        Column(Modifier.fillMaxSize().padding((14 * u).dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("UP NEXT", (12 * u).sp, Color(0xFFFF453A), FontWeight.Bold, Modifier.weight(1f))
                T(today.format(DateTimeFormatter.ofPattern("EEE d MMM")), (12 * u).sp, c.secondary, FontWeight.Medium)
            }
            Spacer(Modifier.height((8 * u).dp))
            if (!granted) {
                Column(Modifier.fillMaxSize().clickable { state.host.requestCalendarPermission() }, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = c.accent, modifier = Modifier.size((26 * u).dp))
                    T("Tap to show your events", (14 * u).sp, c.text, FontWeight.Medium, maxLines = 2)
                }
            } else if (events.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    T("No upcoming events", (15 * u).sp, c.secondary, align = TextAlign.Center)
                }
            } else {
                val zone = ZoneId.systemDefault()
                val fit = ((mh.value - 50 * u) / (44 * u)).toInt().coerceIn(1, 8)
                for (e in events.take(fit)) {
                    val start = Instant.ofEpochMilli(e.begin).atZone(if (e.allDay) ZoneId.of("UTC") else zone)
                    val day = when (start.toLocalDate()) {
                        today -> "Today"; today.plusDays(1) -> "Tomorrow"
                        else -> start.format(DateTimeFormatter.ofPattern("EEE d"))
                    }
                    val time = if (e.allDay) "All day" else start.format(DateTimeFormatter.ofPattern("HH:mm"))
                    Row(Modifier.fillMaxWidth().padding(vertical = (4 * u).dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width((4 * u).dp).height((32 * u).dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color or 0xFF000000.toInt())))
                        Spacer(Modifier.width((10 * u).dp))
                        Column(Modifier.weight(1f)) {
                            T(e.title, (14 * u).sp, c.text, FontWeight.SemiBold)
                            T("$day · $time", (12 * u).sp, c.secondary)
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------ toggles ------------------------------------------ */

private data class Toggle(val label: String, val icon: ImageVector, val active: Boolean = false, val onClick: () -> Unit)

@Composable
fun TogglesWidget(item: HomeItem, modifier: Modifier) {
    val ctx = LocalContext.current
    val c = widgetColors(item)
    val torch by SystemActions.torchOn.collectAsState()
    val toggles = listOf(
        Toggle("Flashlight", if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, torch) { SystemActions.toggleTorch(ctx) },
        Toggle("Wi‑Fi", Icons.Rounded.Wifi) { SystemActions.openPanel(ctx, "android.settings.panel.action.WIFI", Settings.ACTION_WIFI_SETTINGS) },
        Toggle("Bluetooth", Icons.Rounded.Bluetooth) { SystemActions.openPanel(ctx, Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_BLUETOOTH_SETTINGS) },
        Toggle("Lock", Icons.Rounded.Lock) { SystemActions.lockScreen(ctx) },
        Toggle("Shade", Icons.Rounded.Notifications) { SystemActions.openNotifications(ctx) },
        Toggle("Controls", Icons.Rounded.Tune) { SystemActions.openQuickSettings(ctx) },
        Toggle("Screenshot", Icons.Rounded.Screenshot) { SystemActions.screenshot(ctx) },
        Toggle("Settings", Icons.Rounded.Settings) { SystemActions.openPanel(ctx, Settings.ACTION_SETTINGS, Settings.ACTION_SETTINGS) },
    )
    WidgetSurface(item, modifier) {
        val u = unit()
        val ratio = maxWidth.value / maxHeight.value
        val (cols, rows) = when {
            ratio > 3f -> 6 to 1
            ratio > 1.5f -> 4 to 2
            else -> 2 to 2
        }
        Column(Modifier.fillMaxSize().padding((10 * u).dp), verticalArrangement = Arrangement.SpaceEvenly) {
            for (r in 0 until rows) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                for (col in 0 until cols) {
                    val t = toggles.getOrNull(r * cols + col) ?: continue
                    Box(
                        Modifier.fillMaxHeight(0.86f).aspectRatio(1f).clip(CircleShape)
                            .background(if (t.active) Color.White else c.text.copy(alpha = 0.14f))
                            .clickable(onClick = t.onClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(t.icon, t.label, tint = if (t.active) Color.Black else c.text, modifier = Modifier.fillMaxSize(0.46f))
                    }
                }
            }
        }
    }
}
