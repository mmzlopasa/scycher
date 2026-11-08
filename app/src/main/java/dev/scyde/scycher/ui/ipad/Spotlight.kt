package dev.scyde.scycher.ui.ipad

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.Calculator
import dev.scyde.scycher.data.SettingsIndex
import dev.scyde.scycher.data.SurfaceStyle
import dev.scyde.scycher.ui.common.LocalConfig
import dev.scyde.scycher.ui.common.LocalWallpaper
import dev.scyde.scycher.ui.common.LocalWallpaperGeometry
import dev.scyde.scycher.ui.common.drawBlurBackdrop
import dev.scyde.scycher.ui.common.glass
import dev.scyde.scycher.ui.common.panelGlass
import dev.scyde.scycher.ui.common.rememberSaturationFilter
import dev.scyde.scycher.ui.common.toColor
import dev.scyde.scycher.ui.home.AppIcon
import dev.scyde.scycher.ui.home.LocalLauncher
import dev.scyde.scycher.ui.library.score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** iPadOS Spotlight: pull down on the home screen, type, done. */
@Composable
fun Spotlight() {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val wp = LocalWallpaper.current
    val geo = LocalWallpaperGeometry.current
    val filter = rememberSaturationFilter()
    val repo = ScycherApp.instance.apps
    val ctx = LocalContext.current
    val apps by repo.apps.collectAsState()
    val q = state.spotlightQuery
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(state.spotlight) { state.spotlight = false }

    AnimatedVisibility(state.spotlight, enter = fadeIn(spring(stiffness = 700f)), exit = fadeOut(spring(stiffness = 900f))) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus(); keyboard?.show() } }
        val visible = remember(apps, cfg.hiddenApps, cfg.customLabels) {
            apps.filter { it.key !in cfg.hiddenApps }.map { a -> cfg.customLabels[a.key]?.let { a.copy(label = it) } ?: a }
        }
        val hits = remember(visible, q) {
            if (q.isBlank()) visible.map { it to repo.hourlyScore(it.key) }.sortedByDescending { it.second }.map { it.first }.take(8)
            else visible.map { it to score(it.label, q) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }.take(16)
        }
        val calc = remember(q) { if (cfg.search.calculator && Calculator.looksLikeMath(q)) Calculator.evaluate(q) else null }
        val settings = remember(q) { if (cfg.search.settingsResults) SettingsIndex.search(q) else emptyList() }
        val allShortcuts by produceState<List<android.content.pm.ShortcutInfo>>(emptyList()) {
            if (cfg.search.shortcutResults) value = withContext(Dispatchers.IO) { repo.allShortcuts() }
        }
        val shortcuts = remember(q, allShortcuts) {
            if (q.length < 2) emptyList() else allShortcuts.filter { score((it.shortLabel ?: "").toString(), q) >= 40 }.take(5)
        }
        fun go(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; state.spotlight = false }

        Box(
            Modifier.fillMaxSize()
                .drawBehind { drawBlurBackdrop(wp, geo, 1f, 0.35f, filter) }
                .pointerInput(Unit) { detectTapGestures { state.spotlight = false } }
        ) {
            Column(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 40.dp).widthIn(max = 680.dp).fillMaxWidth()
                    .padding(horizontal = 20.dp).imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth().glass(CircleShape, SurfaceStyle.Liquid, Color.White, 0.28f, true, 0.25f)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text("Search", color = Color.White.copy(alpha = 0.55f), fontSize = 19.sp)
                        BasicTextField(
                            q, { state.spotlightQuery = it }, singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 19.sp),
                            cursorBrush = SolidColor(cfg.look.accentColor.toColor()),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                val first = hits.firstOrNull()
                                if (q.isNotBlank() && first != null) { state.launch(first.key, null); state.spotlight = false }
                                else if (q.isNotBlank()) go(Intent(Intent.ACTION_VIEW, Uri.parse(cfg.search.engine.url + Uri.encode(q))))
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (q.isNotEmpty()) Icon(Icons.Rounded.Close, null, tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp).clickable { state.spotlightQuery = "" })
                }
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())
                        .pointerInput(Unit) { detectTapGestures { } },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (calc != null) Card {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Calculate, null, tint = cfg.look.accentColor.toColor(), modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(q, color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp, modifier = Modifier.weight(1f))
                            Text("= $calc", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                        }
                    }
                    if (hits.isNotEmpty()) Card(if (q.isBlank()) "Siri-style suggestions" else "Top hits") {
                        val cols = 8
                        for (row in hits.chunked(cols)) Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                            for (a in row) Box(
                                Modifier.weight(1f).clickable { state.launch(a.key, null); state.spotlight = false },
                                contentAlignment = Alignment.TopCenter
                            ) { AppIcon(a.key, 54.dp, true) }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (shortcuts.isNotEmpty()) Card("Shortcuts") {
                        for (s in shortcuts) {
                            val icon by produceState<ImageBitmap?>(null, s.id) { value = withContext(Dispatchers.IO) { repo.shortcutIcon(s) } }
                            ResultRow(null, icon, (s.shortLabel ?: "").toString(), repo.map.value.values.firstOrNull { it.pkg == s.`package` }?.label) {
                                repo.startShortcut(s, null); state.spotlight = false
                            }
                        }
                    }
                    if (settings.isNotEmpty()) Card("Settings") {
                        for (st in settings) ResultRow(Icons.Rounded.Settings, null, st.label, "Settings") { go(Intent(st.action)) }
                    }
                    if (q.isNotBlank() && cfg.search.showProviders) Card("Search") {
                        ResultRow(Icons.Rounded.Language, null, "Search ${cfg.search.engine.label} for “$q”", null) {
                            go(Intent(Intent.ACTION_VIEW, Uri.parse(cfg.search.engine.url + Uri.encode(q))))
                        }
                        ResultRow(Icons.Rounded.SmartDisplay, null, "YouTube", null) {
                            go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
                        }
                        ResultRow(Icons.Rounded.Map, null, "Maps", null) { go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)))) }
                        ResultRow(Icons.Rounded.Shop, null, "App Store (Play)", null) {
                            go(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(q))))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().panelGlass(70f).padding(vertical = 8.dp)) {
        if (title != null) Text(
            title, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
        )
        content()
    }
}

@Composable
private fun ResultRow(icon: ImageVector?, image: ImageBitmap?, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).background(Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            if (image != null) Image(image, null, Modifier.size(24.dp))
            else if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
    }
}
