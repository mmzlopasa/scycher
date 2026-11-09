package dev.scyde.scycher.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.scyde.scycher.ScycherApp
import dev.scyde.scycher.data.LauncherConfig
import dev.scyde.scycher.data.WallpaperMode
import dev.scyde.scycher.data.WallpaperPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun assetImage(path: String): ImageBitmap? {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    return img
}

/**
 * Wallpaper picker: the built-in pack (each tile shows its dark and light half), dark / light / auto,
 * plus photo and system wallpaper. Shared by the launcher sheet and Settings.
 */
@Composable
fun WallpaperGallery(cfg: LauncherConfig, onPickPhoto: () -> Unit, columns: Int = 4) {
    val app = ScycherApp.instance
    val scope = rememberCoroutineScope()
    val look = cfg.look
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Chips(listOf("auto", "dark", "light"), look.wallpaperVariant, {
            when (it) { "auto" -> "Match dark mode"; "dark" -> "Dark"; else -> "Light" }
        }) { v ->
            app.store.update { it.copy(look = it.look.copy(wallpaperVariant = v)) }
            look.wallpaperPack?.let { key -> scope.launch { app.wallpaper.applyPack(key, v) } }
        }
        Spacer(Modifier.height(12.dp))
        for (row in WallpaperPack.designs.chunked(columns)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (d in row) {
                    val selected = look.wallpaperMode == WallpaperMode.Scycher && look.wallpaperPack == d.key
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                            .clickable { scope.launch { app.wallpaper.applyPack(d.key, look.wallpaperVariant) } }
                            .semantics { contentDescription = "${d.name} wallpaper"; this.selected = selected },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val shape = RoundedCornerShape(18.dp)
                        Row(
                            Modifier.fillMaxWidth().aspectRatio(0.78f).clip(shape)
                                .border(if (selected) 3.dp else 0.8.dp, if (selected) Color.White else Palette2.Separator, shape)
                        ) {
                            // left half dark, right half light – the pair at a glance
                            for (v in listOf("dark", "light")) {
                                Box(Modifier.weight(1f).fillMaxSize()) {
                                    assetImage(WallpaperPack.thumb(d.key, v))?.let {
                                        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                            alignment = if (v == "dark") Alignment.CenterStart else Alignment.CenterEnd)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(d.name, style = TypeScale.Subhead.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                            color = if (selected) Palette2.Label else Palette2.Label2)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            GlassButton("Choose photo…", Icons.Rounded.Image) { onPickPhoto() }
            GlassButton("Use system wallpaper", Icons.Rounded.Smartphone) {
                app.store.update { it.copy(look = it.look.copy(wallpaperMode = WallpaperMode.System, wallpaperPack = null, wallpaperApplied = null)) }
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
