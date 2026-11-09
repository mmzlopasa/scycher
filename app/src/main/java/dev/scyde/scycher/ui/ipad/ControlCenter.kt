package dev.scyde.scycher.ui.ipad

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scyde.scycher.data.IconShapes
import dev.scyde.scycher.data.SurfaceStyle
import dev.scyde.scycher.gestures.SystemActions
import dev.scyde.scycher.notifications.ScycherNotificationListener
import dev.scyde.scycher.ui.common.LocalWallpaper
import dev.scyde.scycher.ui.common.LocalWallpaperGeometry
import dev.scyde.scycher.ui.common.drawBlurBackdrop
import dev.scyde.scycher.ui.common.glass
import dev.scyde.scycher.ui.common.rememberSaturationFilter
import dev.scyde.scycher.ui.home.LocalLauncher
import kotlin.math.pow

/** Screen brightness / rotation need "Modify system settings" (granted from a system screen). */
object SystemControls {
    fun canWrite(ctx: Context) = Settings.System.canWrite(ctx)

    fun requestWrite(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun maxBrightness(ctx: Context): Int {
        val id = android.content.res.Resources.getSystem().getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        var max = if (id != 0) runCatching { android.content.res.Resources.getSystem().getInteger(id) }.getOrDefault(255) else 255
        val cur = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        if (cur > max) max = if (cur <= 2047) 2047 else 4095 // HyperOS uses an extended range
        return max.coerceAtLeast(1)
    }

    /** 0..1, perceptual. */
    fun brightness(ctx: Context): Float {
        val cur = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return (cur.toFloat() / maxBrightness(ctx)).coerceIn(0f, 1f).pow(1f / 2.2f)
    }

    fun setBrightness(ctx: Context, p: Float) {
        if (!canWrite(ctx)) return
        runCatching {
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (p.coerceIn(0.02f, 1f).pow(2.2f) * maxBrightness(ctx)).toInt().coerceAtLeast(1))
        }
    }

    fun rotationLocked(ctx: Context) = Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
    fun setRotationLocked(ctx: Context, locked: Boolean) {
        if (canWrite(ctx)) runCatching { Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 0 else 1) }
        else requestWrite(ctx)
    }

    fun volume(ctx: Context): Float {
        val am = ctx.getSystemService(AudioManager::class.java)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    fun setVolume(ctx: Context, p: Float) {
        val am = ctx.getSystemService(AudioManager::class.java)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, (p * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), 0) }
    }
}

private val ModuleTint = Color(0xFF26262C)

@Composable
fun ControlCenter() {
    val state = LocalLauncher.current
    val ctx = LocalContext.current
    val wp = LocalWallpaper.current
    val geo = LocalWallpaperGeometry.current
    val filter = rememberSaturationFilter()
    BackHandler(state.controlCenter) { state.controlCenter = false }
    val tick = state.resumeTick

    AnimatedVisibility(state.controlCenter, enter = fadeIn(dev.scyde.scycher.ui.common.Motion.ui()), exit = fadeOut(dev.scyde.scycher.ui.common.Motion.exit())) {
        Box(
            Modifier.fillMaxSize().drawBehind { drawBlurBackdrop(wp, geo, 1f, 0.3f, filter) }
                .pointerInput(Unit) { detectTapGestures { state.controlCenter = false } }
        ) {
            androidx.compose.animation.AnimatedVisibility(
                true, Modifier.align(Alignment.TopEnd),
                // Grows out of the top-right corner, where the finger pulled it from; leaves the same way.
                enter = androidx.compose.animation.scaleIn(dev.scyde.scycher.ui.common.Motion.ui(), 0.9f, androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
                exit = androidx.compose.animation.scaleOut(dev.scyde.scycher.ui.common.Motion.exit(), 0.9f, androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
            ) {
                val u = 84.dp; val gap = 14.dp
                Column(
                    Modifier.statusBarsPadding().padding(top = 28.dp, end = 28.dp).width(u * 4 + gap * 3)
                        .pointerInput(Unit) { detectTapGestures { } },
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Connectivity(u * 2 + gap)
                        NowPlayingModule(u * 2 + gap)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                var locked by remember(tick) { mutableStateOf(SystemControls.rotationLocked(ctx)) }
                                SmallTile(u, if (locked) Icons.Rounded.ScreenLockRotation else Icons.Rounded.ScreenRotation, locked, Color(0xFFFF453A)) {
                                    SystemControls.setRotationLocked(ctx, !locked); locked = SystemControls.rotationLocked(ctx)
                                }
                                SmallTile(u, Icons.Rounded.Lock) { state.controlCenter = false; SystemActions.lockScreen(ctx) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                SmallTile(u, Icons.Rounded.Screenshot) { state.controlCenter = false; SystemActions.screenshot(ctx) }
                                SmallTile(u, Icons.Rounded.Notifications) { state.controlCenter = false; SystemActions.openNotifications(ctx) }
                            }
                        }
                        var bright by remember(tick) { mutableFloatStateOf(SystemControls.brightness(ctx)) }
                        TallSlider(u, u * 2 + gap, bright, Icons.Rounded.Brightness6) { v ->
                            if (!SystemControls.canWrite(ctx)) { SystemControls.requestWrite(ctx); return@TallSlider }
                            bright = v; SystemControls.setBrightness(ctx, v)
                        }
                        var vol by remember(tick) { mutableFloatStateOf(SystemControls.volume(ctx)) }
                        TallSlider(u, u * 2 + gap, vol, Icons.Rounded.VolumeUp) { v -> vol = v; SystemControls.setVolume(ctx, v) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        SmallTile(u, Icons.Rounded.Timer) { open(ctx, Intent(AlarmClock.ACTION_SET_TIMER)); state.controlCenter = false }
                        SmallTile(u, Icons.Rounded.Calculate) {
                            open(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)); state.controlCenter = false
                        }
                        SmallTile(u, Icons.Rounded.CameraAlt) { open(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); state.controlCenter = false }
                        SmallTile(u, Icons.Rounded.Settings) { open(ctx, Intent(Settings.ACTION_SETTINGS)); state.controlCenter = false }
                    }
                    if (!SystemControls.canWrite(ctx)) Text(
                        "Tap brightness to allow Scycher to change brightness & rotation.",
                        color = dev.scyde.scycher.ui.common.Palette2.Label2, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

private fun open(ctx: Context, i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

@Composable
private fun Module(w: Dp, h: Dp, content: @Composable BoxScope.() -> Unit) {
    val shape = remember { IconShapes.continuousRect(80f) }
    Box(Modifier.size(w, h).glass(shape, SurfaceStyle.Liquid, ModuleTint, 0.9f, true, 0.2f), content = content)
}

@Composable
private fun RoundToggle(icon: ImageVector, on: Boolean, onColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(58.dp).clip(CircleShape).background(if (on) onColor else Color.White.copy(alpha = 0.16f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun Connectivity(size: Dp) {
    val ctx = LocalContext.current
    val torch by SystemActions.torchOn.collectAsState()
    val tick = LocalLauncher.current.resumeTick
    val wifiOn = remember(tick) {
        runCatching { ctx.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java).isWifiEnabled }.getOrDefault(false)
    }
    val btOn = remember(tick) {
        runCatching { ctx.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
    }
    Module(size, size) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundToggle(Icons.Rounded.Wifi, wifiOn, Color(0xFF0A84FF)) {
                    SystemActions.openPanel(ctx, "android.settings.panel.action.WIFI", Settings.ACTION_WIFI_SETTINGS)
                }
                RoundToggle(Icons.Rounded.Bluetooth, btOn, Color(0xFF0A84FF)) {
                    SystemActions.openPanel(ctx, Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_BLUETOOTH_SETTINGS)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundToggle(if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, torch, Color(0xFFFFD60A)) { SystemActions.toggleTorch(ctx) }
                RoundToggle(Icons.Rounded.Notifications, false, Color(0xFF5E5CE6)) {
                    SystemActions.openPanel(ctx, "android.settings.ZEN_MODE_SETTINGS", Settings.ACTION_SOUND_SETTINGS)
                }
            }
        }
    }
}

@Composable
private fun NowPlayingModule(size: Dp) {
    val ctx = LocalContext.current
    val np by ScycherNotificationListener.nowPlaying.collectAsState()
    val art = remember(np?.art) { np?.art?.asImageBitmap() }
    Module(size, size) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.6f))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(np?.title?.ifEmpty { null } ?: "Not Playing", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(np?.artist ?: "", color = dev.scyde.scycher.ui.common.Palette2.Label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val n = np
                Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(34.dp).clickable { n?.controller?.transportControls?.skipToPrevious() })
                Icon(if (n?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.size(44.dp).clickable {
                        if (n == null) SystemActions.openNotificationAccess(ctx)
                        else n.controller.transportControls.let { if (n.playing) it.pause() else it.play() }
                    })
                Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(34.dp).clickable { n?.controller?.transportControls?.skipToNext() })
            }
        }
    }
}

@Composable
private fun SmallTile(u: Dp, icon: ImageVector, on: Boolean = false, onColor: Color = Color.White, onClick: () -> Unit) {
    Module(u, u) {
        Box(
            Modifier.fillMaxSize().clickable(onClick = onClick).then(if (on) Modifier.background(onColor.copy(alpha = 0.85f)) else Modifier),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}

/** iPad-style tall pill slider. */
@Composable
private fun TallSlider(w: Dp, h: Dp, value: Float, icon: ImageVector, onChange: (Float) -> Unit) {
    val shape = remember { IconShapes.continuousRect(80f) }
    Box(
        Modifier.size(w, h).glass(shape, SurfaceStyle.Liquid, ModuleTint, 0.9f, true, 0.2f)
            .pointerInput(Unit) {
                detectTapGestures { o -> onChange((1f - o.y / size.height).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { c, _ -> c.consume(); onChange((1f - c.position.y / size.height).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f)).background(Color.White.copy(alpha = 0.92f)))
        Icon(icon, null, tint = if (value > 0.12f) Color(0xFF3A3A3C) else Color.White, modifier = Modifier.padding(bottom = 18.dp).size(28.dp))
    }
}
