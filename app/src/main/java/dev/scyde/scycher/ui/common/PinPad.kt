package dev.scyde.scycher.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * iOS-like passcode pad. [onSubmit] returns true when the PIN is accepted.
 * Submits automatically after [length] digits (4–8, default 4 when [length] is null → manual OK at ≥4).
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String? = null,
    length: Int? = null,
    onBiometric: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onSubmit: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (pin.length < 4) return
        if (!onSubmit(pin)) {
            error = true
            scope.launch {
                for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, spring(stiffness = 4000f))
                pin = ""
            }
        }
    }

    fun press(d: String) {
        error = false
        if (pin.length < 8) pin += d
        if (length != null && pin.length == length) submit()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (error) "Wrong PIN" else subtitle ?: "Enter PIN",
            color = if (error) Color(0xFFFF453A) else Color.White.copy(alpha = 0.6f), fontSize = 14.sp
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.offset { IntOffset(shake.value.roundToInt(), 0) }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val n = length ?: maxOf(4, pin.length)
            for (i in 0 until n) Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(if (i < pin.length) Color.White else Color.Transparent)
                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            )
        }
        Spacer(Modifier.height(26.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        for (r in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) { for (d in r) Key(d) { press(d) } }
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            // empty: fingerprint (when available) · typing: OK
            val showOk = length == null && (pin.isNotEmpty() || onBiometric == null)
            Box(Modifier.size(74.dp).clip(CircleShape).clickable(enabled = showOk || onBiometric != null) {
                if (showOk) submit() else onBiometric?.invoke()
            }, contentAlignment = Alignment.Center) {
                if (showOk) Text("OK", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                else if (onBiometric != null) Icon(Icons.Rounded.Fingerprint, "Use fingerprint", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Key("0") { press("0") }
            Box(Modifier.size(74.dp).clip(CircleShape).clickable { if (pin.isNotEmpty()) pin = pin.dropLast(1) }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        if (onCancel != null) {
            Spacer(Modifier.height(10.dp))
            Text("Cancel", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp,
                modifier = Modifier.clip(CircleShape).clickable { onCancel() }.padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun Key(d: String, onClick: () -> Unit) {
    Box(
        Modifier.size(74.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(d, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light) }
}
