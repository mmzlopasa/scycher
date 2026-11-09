package dev.scyde.scycher.ui.common

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Scycher design tokens. Everything visual should come from here (see DESIGN.md).
 * Surfaces sit on top of a wallpaper, so "labels" are tuned for legibility on glass (vibrancy:
 * brighter + slightly heavier than flat grey).
 */
object Palette2 {
    val Label = Color.White
    val Label2 = Color.White.copy(alpha = 0.74f)
    val Label3 = Color.White.copy(alpha = 0.52f)
    val Separator = Color.White.copy(alpha = 0.14f)
    val Fill = Color.White.copy(alpha = 0.12f)
    val FillStrong = Color.White.copy(alpha = 0.20f)
    val Destructive = Color(0xFFFF453A)
    val Positive = Color(0xFF30D158)
    val Warning = Color(0xFFFF9F0A)
    val Blue = Color(0xFF0A84FF)
    /** Tint used under menus, sheets and dialogs. */
    val Panel = Color(0xFF1E1E24)
    /** Tint of Control Center modules. */
    val Module = Color(0xFF26262C)
}

/**
 * Type scale modelled on the iOS text styles. Tracking is size specific: large text is tightened,
 * small text opened up a touch – one fixed letter-spacing is wrong somewhere.
 */
object TypeScale {
    val LargeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.022).em)
    val Title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.018).em)
    val Title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.012).em)
    val Title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    val Headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.006).em)
    val Body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.004).em)
    val Callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val Subhead = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val Footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.004.em)
    val Caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.01.em)
}

/**
 * Motion tokens. Springs are expressed like Apple's (damping ratio + response); stiffness = (2π / response)².
 * Critically damped by default; bounce only where a gesture carried momentum.
 */
object Motion {
    private fun k(response: Float) = (2 * Math.PI / response).let { (it * it).toFloat() }

    /** Press / release feedback (~100 ms). */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = k(0.16f))
    /** Default UI movement, menus, fades. */
    fun <T> ui(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = k(0.32f))
    /** Sheets, drawers, App Library, Today View (Apple's drawer: damping 0.8, response 0.3 – used after a flick). */
    fun <T> sheet(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = k(0.34f))
    /** Settle after a drag/drop or reposition. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = k(0.36f))
    /** Exits are quicker than entrances. */
    fun <T> exit(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = k(0.24f))
}

/** True when the user turned animations off in Android's developer / accessibility settings. */
val LocalReduceMotion = staticCompositionLocalOf { false }
