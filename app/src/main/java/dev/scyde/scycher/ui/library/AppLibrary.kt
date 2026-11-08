package dev.scyde.scycher.ui.library

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.produceState
import dev.scyde.scycher.data.Calculator
import dev.scyde.scycher.data.SettingsIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.AppInfo
import dev.scyde.scycher.data.HomeItem
import dev.scyde.scycher.data.ItemType
import dev.scyde.scycher.data.LibrarySort
import dev.scyde.scycher.data.LibraryStyle
import dev.scyde.scycher.data.SurfaceStyle
import dev.scyde.scycher.ui.DragSource
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
import dev.scyde.scycher.ui.home.itemGestures
import dev.scyde.scycher.ui.home.labelStyle
import dev.scyde.scycher.ui.home.rememberAppIcon
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import java.text.Normalizer

private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** Fuzzy-ish ranking: prefix > word-prefix > initials > contains. */
private fun score(label: String, q: String): Int {
    val l = norm(label); val n = norm(q)
    if (n.isEmpty()) return 1
    if (l.startsWith(n)) return 100
    val words = l.split(' ', '-', '.', '_').filter { it.isNotEmpty() }
    if (words.any { it.startsWith(n) }) return 80
    if (words.joinToString("") { it.take(1) }.startsWith(n)) return 60
    if (l.contains(n)) return 40
    var i = 0
    for (ch in l) if (i < n.length && ch == n[i]) i++
    return if (i == n.length && n.length >= 3) 10 else 0
}

@Composable
fun AppLibrary() {
    val state = LocalLauncher.current
    val cfg = LocalConfig.current
    val lc = cfg.library
    val p = state.library.value
    val fromLibraryDrag = state.drag?.let { it.source == DragSource.Library && it.active } == true
    if (p <= 0.001f && state.library.targetValue == 0f && state.drag?.source != DragSource.Library) return

    val wp = LocalWallpaper.current
    val geo = LocalWallpaperGeometry.current
    val filter = rememberSaturationFilter()
    val repo = ScycherApp.instance.apps
    val apps by repo.apps.collectAsState()
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val query = state.libraryQuery

    BackHandler(p > 0f) { state.closeLibrary() }

    val visible = remember(apps, cfg.hiddenApps, cfg.customLabels, cfg.customCategories, lc.sort) {
        val list = apps.filter { it.key !in cfg.hiddenApps }
            .map { a -> cfg.customLabels[a.key]?.let { a.copy(label = it) } ?: a }
            .map { a -> cfg.customCategories[a.key]?.let { a.copy(category = it) } ?: a }
        when (lc.sort) {
            LibrarySort.Alphabetical -> list.sortedBy { norm(it.label) }
            LibrarySort.MostUsed -> list.sortedByDescending { repo.usageCount(it.key) }
            LibrarySort.InstallDate -> list.sortedByDescending { it.installTime }
        }
    }
    val results = remember(visible, query) {
        if (query.isBlank()) visible
        else visible.map { it to score(it.label, query) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
    val suggestions = remember(visible, cfg.recentApps, lc.smartSuggestions, state.libraryFocus, p > 0.5f) {
        val recent = cfg.recentApps.mapNotNull { k -> visible.firstOrNull { it.key == k } }
        val smart = if (lc.smartSuggestions) visible.map { it to repo.hourlyScore(it.key) }.filter { it.second > 0.5f }
            .sortedByDescending { it.second }.map { it.first } else emptyList()
        (smart + recent + visible.sortedByDescending { repo.usageCount(it.key) }).distinctBy { it.key }.take(lc.columns.coerceAtMost(8))
    }
    val hidden = remember(apps, cfg.hiddenApps) { apps.filter { it.key in cfg.hiddenApps } }

    LaunchedEffect(state.libraryFocus) {
        if (state.libraryFocus > 0) runCatching { focus.requestFocus(); keyboard?.show() }
    }
    LaunchedEffect(p == 0f) { if (p == 0f) { focusManager.clearFocus(); keyboard?.hide() } }

    // Pull-down-to-close at the top of the list
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val v = state.library.value
                if (available.y < 0 && v < 1f && state.library.targetValue >= 0f && source == NestedScrollSource.UserInput) {
                    val nv = (v - available.y / 1200f).coerceAtMost(1f)
                    state.scope.launch { state.library.snapTo(nv) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    val nv = (state.library.value - available.y / 1200f).coerceAtLeast(0f)
                    state.scope.launch { state.library.snapTo(nv) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val v = state.library.value
                if (v < 1f) {
                    if (v < 0.75f || available.y > 1500f) state.closeLibrary()
                    else state.library.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 500f))
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val dragAlpha = if (fromLibraryDrag) 0f else 1f
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = (p * 1.4f).coerceAtMost(1f) * dragAlpha }
            .drawBehind { drawBlurBackdrop(wp, geo, 1f, lc.dim, filter) }
            .then(if (fromLibraryDrag) Modifier else Modifier.pointerInput(Unit) { detectTapGestures { } })
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .graphicsLayer {
                    translationY = (1f - p) * 140.dp.toPx()
                    val s = 0.94f + 0.06f * p; scaleX = s; scaleY = s
                }
                .nestedScroll(nested)
        ) {
            // Search
            Row(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = { if (state.library.value < 0.75f) state.closeLibrary() else state.openLibrary() },
                        ) { c, d -> c.consume(); state.scope.launch { state.library.snapTo((state.library.value - d / 1200f).coerceIn(0f, 1f)) } }
                    }
                    .glass(CircleShape, SurfaceStyle.Glass, Color.White, 0.14f, true, 0.2f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search apps", color = Color.White.copy(alpha = 0.55f), fontSize = 17.sp)
                    BasicTextField(
                        query, { state.libraryQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                        cursorBrush = SolidColor(cfg.look.accentColor.toColor()),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            results.firstOrNull()?.let { state.launch(it.key, null); state.closeLibrary() }
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) Icon(
                    Icons.Rounded.Close, null, tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp).clickable { state.libraryQuery = "" }
                ) else if (hidden.isNotEmpty()) Icon(
                    if (state.showHiddenApps) Icons.Rounded.VisibilityOff else Icons.Rounded.Lock, "Hidden apps",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp).clickable {
                        if (state.showHiddenApps) state.showHiddenApps = false
                        else if (cfg.security.lockHiddenApps) state.requireUnlock("Hidden apps") { state.showHiddenApps = true }
                        else state.showHiddenApps = true
                    }
                )
            }

            val content = Modifier.weight(1f).fillMaxWidth().imePadding()
            when {
                query.isNotBlank() -> SearchResults(results, query, content) { action ->
                    try { ctx.startActivity(action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                    state.closeLibrary()
                }
                state.showHiddenApps -> GridLibrary(hidden, emptyList(), content, header = "Hidden apps")
                lc.style == LibraryStyle.List -> ListLibrary(visible, content)
                lc.style == LibraryStyle.Tabs -> TabsLibrary(visible, if (lc.showSuggestions) suggestions else emptyList(), content)
                lc.style == LibraryStyle.Categories -> CategoryLibrary(visible, suggestions, content)
                else -> GridLibrary(visible, if (lc.showSuggestions) suggestions else emptyList(), content)
            }
        }
    }
}

@Composable
private fun LibraryIcon(app: AppInfo, modifier: Modifier = Modifier) {
    val state = LocalLauncher.current
    val lc = LocalConfig.current.library
    val item = remember(app.key) { HomeItem(-(app.key.hashCode().toLong() and 0xFFFFFFF) - 1000, ItemType.App, app = app.key) }
    Box(
        modifier.itemGestures(item, DragSource.Library) { b -> state.launch(app.key, b); state.closeLibrary() },
        contentAlignment = Alignment.TopCenter,
    ) { AppIcon(app.key, lc.iconSizeDp.dp, lc.showLabels) }
}

@Composable
private fun GridLibrary(apps: List<AppInfo>, suggestions: List<AppInfo>, modifier: Modifier, header: String? = null) {
    val lc = LocalConfig.current.library
    val gs = rememberLazyGridState()
    LazyVerticalGrid(
        GridCells.Fixed(lc.columns), modifier, gs,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (suggestions.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header("Suggestions") }
            items(suggestions, key = { "s" + it.key }) { LibraryIcon(it) }
            item(span = { GridItemSpan(maxLineSpan) }) { Header("All Apps") }
        }
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { Header(header) }
        items(apps, key = { it.key }) { LibraryIcon(it) }
    }
}

/** Smart Launcher style: category tabs on top, apps of that category below. */
@Composable
private fun TabsLibrary(apps: List<AppInfo>, suggestions: List<AppInfo>, modifier: Modifier) {
    val lc = LocalConfig.current.library
    val cats = remember(apps) { apps.groupBy { it.category }.toList().sortedByDescending { it.second.size } }
    val tabs = remember(cats, suggestions) {
        buildList {
            if (suggestions.isNotEmpty()) add("For you" to suggestions)
            add("All" to apps)
            addAll(cats)
        }
    }
    var sel by remember { androidx.compose.runtime.mutableIntStateOf(if (suggestions.isNotEmpty()) 1 else 0) }
    val current = tabs.getOrNull(sel) ?: tabs.first()
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { i, (name, list) ->
                val on = i == sel
                Row(
                    Modifier.clip(CircleShape)
                        .then(if (on) Modifier.background(Color.White) else Modifier.glass(CircleShape, SurfaceStyle.Glass, Color.White, 0.10f, true, 0.15f))
                        .clickable { sel = i }.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, color = if (on) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text(list.size.toString(), color = if (on) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
            }
        }
        androidx.compose.animation.AnimatedContent(current.first, label = "tab") { name ->
            val list = tabs.firstOrNull { it.first == name }?.second ?: emptyList()
            LazyVerticalGrid(
                GridCells.Fixed(lc.columns), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) { items(list, key = { it.key }) { LibraryIcon(it) } }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
}

@Composable
private fun ListLibrary(apps: List<AppInfo>, modifier: Modifier) {
    val state = LocalLauncher.current
    val ls = rememberLazyListState()
    val scope = state.scope
    val groups = remember(apps) {
        apps.groupBy { a -> a.label.firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) norm(it.toString()).uppercase() else "#" } ?: "#" }
    }
    val letterIndex = remember(groups) {
        var i = 0
        groups.mapValues { (_, v) -> val idx = i; i += v.size + 1; idx }
    }
    Row(modifier) {
        LazyColumn(Modifier.weight(1f), ls, contentPadding = PaddingValues(start = 24.dp, end = 8.dp, bottom = 40.dp)) {
            groups.forEach { (letter, list) ->
                item(key = "h$letter") {
                    Text(letter, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                }
                items(list, key = { it.key }) { app ->
                    val item = remember(app.key) { HomeItem(-(app.key.hashCode().toLong() and 0xFFFFFFF) - 1000, ItemType.App, app = app.key) }
                    Row(
                        Modifier.fillMaxWidth()
                            .itemGestures(item, DragSource.Library) { b -> state.launch(app.key, b); state.closeLibrary() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val img = rememberAppIcon(app.key)
                        dev.scyde.scycher.ui.home.IconImage(img, 44.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(app.label, style = labelStyle(Color.White, 17f).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Start))
                    }
                }
            }
        }
        // A–Z fast scroller
        Column(
            Modifier.fillMaxHeight().padding(end = 10.dp, top = 8.dp, bottom = 40.dp).width(26.dp)
                .pointerInput(groups) {
                    detectVerticalDragGestures { c, _ ->
                        val keys = groups.keys.toList()
                        val i = ((c.position.y / size.height) * keys.size).toInt().coerceIn(0, keys.lastIndex)
                        scope.launch { ls.scrollToItem(letterIndex[keys[i]] ?: 0) }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            for (l in groups.keys) Text(
                l, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { scope.launch { ls.scrollToItem(letterIndex[l] ?: 0) } }
            )
        }
    }
}

@Composable
private fun CategoryLibrary(apps: List<AppInfo>, suggestions: List<AppInfo>, modifier: Modifier) {
    val state = LocalLauncher.current
    val cats = remember(apps, suggestions) {
        val m = linkedMapOf<String, List<AppInfo>>()
        if (suggestions.isNotEmpty()) m["Suggestions"] = suggestions
        apps.groupBy { it.category }.toList().sortedByDescending { it.second.size }.forEach { (k, v) -> m[k] = v }
        m.toList()
    }
    LazyVerticalGrid(
        GridCells.Adaptive(170.dp), modifier,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(cats, key = { it.first }) { (name, list) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.fillMaxWidth().aspectRatio(1f).panelGlass(70f).padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    for (r in 0 until 2) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (c in 0 until 2) {
                            val idx = r * 2 + c
                            Box(Modifier.weight(1f).fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                                if (idx < 3 || list.size <= 4) {
                                    list.getOrNull(idx)?.let { app ->
                                        val item = remember(app.key) { HomeItem(-(app.key.hashCode().toLong() and 0xFFFFFFF) - 1000, ItemType.App, app = app.key) }
                                        Box(Modifier.itemGestures(item, DragSource.Library) { b -> state.launch(app.key, b); state.closeLibrary() }) {
                                            dev.scyde.scycher.ui.home.IconImage(rememberAppIcon(app.key), 56.dp)
                                        }
                                    }
                                } else {
                                    // stack of the rest -> opens the category
                                    Box(
                                        Modifier.size(56.dp).clickable { state.openCategory = name to list.map { it.key } },
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            for (rr in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                for (cc in 0 until 2) {
                                                    val k = list.getOrNull(3 + rr * 2 + cc)?.key
                                                    val img = rememberAppIcon(k)
                                                    if (img != null) Image(img, null, Modifier.size(27.dp)) else Spacer(Modifier.size(27.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(name, style = labelStyle(Color.White, 14f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SearchResults(results: List<AppInfo>, query: String, modifier: Modifier, onAction: (Intent) -> Unit) {
    val cfg = LocalConfig.current
    val lc = cfg.library
    val sc = cfg.search
    val ctx = LocalContext.current
    val state = LocalLauncher.current
    val repo = ScycherApp.instance.apps
    val calc = remember(query, sc.calculator) { if (sc.calculator && Calculator.looksLikeMath(query)) Calculator.evaluate(query) else null }
    val settings = remember(query, sc.settingsResults) { if (sc.settingsResults) SettingsIndex.search(query) else emptyList() }
    val allShortcuts by produceState<List<android.content.pm.ShortcutInfo>>(emptyList(), sc.shortcutResults) {
        if (sc.shortcutResults) value = withContext(Dispatchers.IO) { repo.allShortcuts() }
    }
    val shortcuts = remember(query, allShortcuts) {
        if (query.length < 2) emptyList()
        else allShortcuts.filter { s -> score((s.shortLabel ?: s.longLabel ?: "").toString(), query) >= 40 }.take(6)
    }
    LazyVerticalGrid(
        GridCells.Fixed(lc.columns), modifier.navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (calc != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().panelGlass(60f).clickable {
                    ctx.getSystemService(android.content.ClipboardManager::class.java)
                        ?.setPrimaryClip(android.content.ClipData.newPlainText("result", calc))
                    android.widget.Toast.makeText(ctx, "Copied $calc", android.widget.Toast.LENGTH_SHORT).show()
                }.padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Calculate, null, tint = cfg.look.accentColor.toColor(), modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(16.dp))
                Text(query.trim(), color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text("= $calc", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            }
        }
        items(results, key = { it.key }) { LibraryIcon(it) }
        if (shortcuts.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header("Shortcuts") }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().panelGlass(60f)) {
                    shortcuts.forEachIndexed { i, sh ->
                        val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, sh.id) {
                            value = withContext(Dispatchers.IO) { repo.shortcutIcon(sh) }
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { repo.startShortcut(sh, null); state.closeLibrary() }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            icon?.let { androidx.compose.foundation.Image(it, null, Modifier.size(26.dp)) } ?: Spacer(Modifier.size(26.dp))
                            Spacer(Modifier.width(14.dp))
                            Text((sh.shortLabel ?: sh.longLabel ?: "").toString(), color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(repo.map.value.values.firstOrNull { it.pkg == sh.`package` }?.label ?: "", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                        }
                        if (i < shortcuts.lastIndex) Box(Modifier.fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.12f)))
                    }
                }
            }
        }
        if (settings.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Header("Settings") }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().panelGlass(60f)) {
                    settings.forEachIndexed { i, st ->
                        ActionRow(Icons.Rounded.Settings, st.label) { onAction(Intent(st.action)) }
                        if (i < settings.lastIndex) Box(Modifier.fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.12f)))
                    }
                }
            }
        }
        if (sc.showProviders) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().panelGlass(60f)) {
                ActionRow(Icons.Rounded.Language, "Search ${sc.engine.label} for “$query”") {
                    onAction(Intent(Intent.ACTION_VIEW, Uri.parse(sc.engine.url + Uri.encode(query))))
                }
                Box(Modifier.fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.12f)))
                ActionRow(Icons.Rounded.SmartDisplay, "Search YouTube") {
                    onAction(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))))
                }
                Box(Modifier.fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.12f)))
                ActionRow(Icons.Rounded.Map, "Search Maps") {
                    onAction(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))
                }
                Box(Modifier.fillMaxWidth().height(0.6.dp).background(Color.White.copy(alpha = 0.12f)))
                ActionRow(Icons.Rounded.Shop, "Search Play Store") {
                    onAction(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query))))
                }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = Color.White, fontSize = 16.sp)
    }
}
