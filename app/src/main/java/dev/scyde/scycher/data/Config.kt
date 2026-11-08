package dev.scyde.scycher.data

import kotlinx.serialization.Serializable

/*
 * Every visual and behavioural knob of Scycher lives here. The whole object is
 * serialised to JSON, so backup/restore is a single file.
 */

@Serializable
data class LauncherConfig(
    val version: Int = 1,
    val onboarded: Boolean = false,
    val home: HomeConfig = HomeConfig(),
    val icons: IconConfig = IconConfig(),
    val dock: DockConfig = DockConfig(),
    val widgets: WidgetStyleConfig = WidgetStyleConfig(),
    val library: LibraryConfig = LibraryConfig(),
    val gestures: GestureConfig = GestureConfig(),
    val look: LookConfig = LookConfig(),
    val nav: NavGestureConfig = NavGestureConfig(),
    val hiddenApps: Set<String> = emptySet(),
    val customLabels: Map<String, String> = emptyMap(),
    val layout: HomeLayout = HomeLayout(),
    val recentApps: List<String> = emptyList(),
)

@Serializable
data class HomeConfig(
    val columns: Int = 6,
    val rows: Int = 5,
    val horizontalPaddingDp: Int = 28,
    val topPaddingDp: Int = 12,
    val showLabels: Boolean = true,
    val labelSizeSp: Float = 12.5f,
    val labelColor: Long = 0xFFFFFFFF,
    val labelShadow: Boolean = true,
    val labelWeight: Int = 500,
    val labelMaxLines: Int = 1,
    val pageIndicator: PageIndicatorStyle = PageIndicatorStyle.Pill,
    val pageTransition: PageTransition = PageTransition.Depth,
    val lockLayout: Boolean = false,
    val homeButtonGoesToFirstPage: Boolean = true,
    val returnAnimation: Boolean = true,
)

enum class PageIndicatorStyle { Pill, Dots, Lines, Hidden }
enum class PageTransition { Slide, Depth, Zoom, Cube, Carousel, Fade, Stack }

@Serializable
data class IconConfig(
    val sizeDp: Int = 64,
    val shape: IconShape = IconShape.Squircle,
    val style: IconStyle = IconStyle.Default,
    val tintColor: Long = 0xFFB9A7FF,
    val iconPack: String? = null,
    val shadow: Boolean = true,
    val shadowStrength: Float = 0.35f,
    val reshapeLegacy: Boolean = true,
    val legacyBackground: Long = 0xFFFFFFFF,
    val badgeStyle: BadgeStyle = BadgeStyle.Count,
    val badgeColor: Long = 0xFFFF3B30,
    val pressScale: Float = 0.88f,
    val glassRim: Boolean = false,
)

enum class IconShape { Squircle, RoundedSquare, Circle, Teardrop, Pebble, Square, Hexagon, System }
enum class IconStyle { Default, Dark, Tinted, Monochrome }
enum class BadgeStyle { Count, Dot, Hidden }

@Serializable
data class DockConfig(
    val enabled: Boolean = true,
    val maxIcons: Int = 10,
    val iconSizeDp: Int = 60,
    val style: SurfaceStyle = SurfaceStyle.Glass,
    val opacity: Float = 0.28f,
    val tintColor: Long = 0xFFFFFFFF,
    val cornerRadiusDp: Int = 32,
    val paddingDp: Int = 12,
    val iconSpacingDp: Int = 14,
    val bottomMarginDp: Int = 14,
    val showLabels: Boolean = false,
    val border: Boolean = true,
    val borderOpacity: Float = 0.22f,
    val showRecents: Boolean = true,
    val recentsCount: Int = 3,
    val fullWidth: Boolean = false,
    val shadow: Boolean = true,
    val magnify: Boolean = true,
)

enum class SurfaceStyle { Glass, Frosted, Solid, Tinted, Clear }

@Serializable
data class WidgetStyleConfig(
    val style: SurfaceStyle = SurfaceStyle.Glass,
    val opacity: Float = 0.30f,
    val tintColor: Long = 0xFF1C1C24,
    val textColor: Long = 0xFFFFFFFF,
    val accentColor: Long = 0xFFFF9F0A,
    val cornerRadiusDp: Int = 30,
    val border: Boolean = true,
    val cellPaddingDp: Int = 7,
    val clock24h: Boolean = true,
    val clockSeconds: Boolean = false,
    val clockWeight: Int = 200,
    val weatherCity: String = "Zagreb",
    val weatherLat: Double = 45.815,
    val weatherLon: Double = 15.982,
    val weatherFahrenheit: Boolean = false,
    val worldClocks: List<String> = listOf("Europe/Zagreb", "America/New_York", "Asia/Tokyo", "America/Los_Angeles"),
    val systemWidgetGlass: Boolean = true,
)

@Serializable
data class LibraryConfig(
    val columns: Int = 7,
    val iconSizeDp: Int = 58,
    val style: LibraryStyle = LibraryStyle.Grid,
    val showLabels: Boolean = true,
    val dim: Float = 0.45f,
    val blur: Boolean = true,
    val autoKeyboard: Boolean = false,
    val showSuggestions: Boolean = true,
    val sort: LibrarySort = LibrarySort.Alphabetical,
)

enum class LibraryStyle { Grid, Categories, List }
enum class LibrarySort { Alphabetical, MostUsed, InstallDate }

@Serializable
data class GestureConfig(
    val swipeUp: GestureAction = GestureAction.OpenLibrary,
    val swipeDown: GestureAction = GestureAction.Notifications,
    val doubleTap: GestureAction = GestureAction.LockScreen,
    val twoFingerDown: GestureAction = GestureAction.QuickSettings,
    val twoFingerUp: GestureAction = GestureAction.Search,
    val pinchIn: GestureAction = GestureAction.EditMode,
    val swipeUpApp: String? = null,
    val swipeDownApp: String? = null,
    val doubleTapApp: String? = null,
    val twoFingerDownApp: String? = null,
    val twoFingerUpApp: String? = null,
    val pinchInApp: String? = null,
    val haptics: Boolean = true,
)

enum class GestureAction(val label: String) {
    None("Nothing"),
    OpenLibrary("App Library"),
    Search("Search"),
    Notifications("Notifications"),
    QuickSettings("Quick settings"),
    LockScreen("Lock screen"),
    Recents("Recent apps"),
    EditMode("Edit home screen"),
    ScycherSettings("Scycher settings"),
    Screenshot("Screenshot"),
    LaunchApp("Open an app"),
}

@Serializable
data class LookConfig(
    val accentColor: Long = 0xFFB9A7FF,
    val font: FontChoice = FontChoice.System,
    val wallpaperMode: WallpaperMode = WallpaperMode.System,
    val wallpaperDim: Float = 0.0f,
    val parallax: Boolean = true,
    val animationSpeed: Float = 1.0f,
    val hideStatusBar: Boolean = false,
    val darkStatusIcons: Boolean = false,
    val glassBlurRadius: Int = 24,
    val glassSaturation: Float = 1.35f,
    val openAnimation: OpenAnimation = OpenAnimation.ScaleUp,
)

enum class FontChoice(val family: String?) {
    System(null), Light("sans-serif-light"), Medium("sans-serif-medium"), Condensed("sans-serif-condensed"),
    Serif("serif"), Mono("monospace"), Rounded("sans-serif-rounded"), Casual("casual")
}

enum class WallpaperMode { System, Scycher }
enum class OpenAnimation { ScaleUp, ClipReveal, System }

/** Settings for Scycher's own (accessibility based) navigation gestures. */
@Serializable
data class NavGestureConfig(
    val enabled: Boolean = true,
    val bottomHome: Boolean = true,
    val holdForRecents: Boolean = true,
    val sideBack: Boolean = true,
    val backLeft: Boolean = true,
    val backRight: Boolean = true,
    val edgeWidthDp: Int = 16,
    val edgeHeightPercent: Int = 70,
    val bottomHeightDp: Int = 18,
    val bottomWidthPercent: Int = 70,
    val sensitivity: Float = 1.0f,
    val showHandle: Boolean = true,
    val handleWidthDp: Int = 120,
    val handleOpacity: Float = 0.75f,
    val handleColor: Long = 0xFFFFFFFF,
    val vibrate: Boolean = true,
    val backIndicator: Boolean = true,
    val forwardTaps: Boolean = true,
)

@Serializable
data class HomeLayout(
    val pages: List<HomePage> = listOf(HomePage(1L)),
    val dock: List<HomeItem> = emptyList(),
)

@Serializable
data class HomePage(val id: Long, val items: List<HomeItem> = emptyList())

enum class ItemType { App, Folder, SystemWidget, Widget, Shortcut }

enum class BuiltinKind(val label: String, val sizes: List<Pair<Int, Int>>) {
    ClockDigital("Clock", listOf(2 to 2, 4 to 2, 4 to 4)),
    ClockAnalog("Analog clock", listOf(2 to 2, 3 to 3, 4 to 4)),
    Weather("Weather", listOf(2 to 2, 4 to 2, 4 to 4)),
    Calendar("Calendar", listOf(2 to 2, 4 to 2, 4 to 4)),
    Battery("Battery", listOf(2 to 2, 4 to 2)),
    NowPlaying("Now Playing", listOf(2 to 2, 4 to 2)),
    WorldClock("World clock", listOf(4 to 2, 2 to 2)),
    Note("Note", listOf(2 to 2, 4 to 2, 4 to 4)),
    Photo("Photo", listOf(2 to 2, 4 to 2, 4 to 4)),
    Search("Search", listOf(4 to 1, 6 to 1)),
    Greeting("Greeting", listOf(4 to 1, 4 to 2)),
}

@Serializable
data class HomeItem(
    val id: Long,
    val type: ItemType,
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 1,
    val h: Int = 1,
    /** Flattened component name for apps. */
    val app: String? = null,
    /** Folder content. */
    val apps: List<String> = emptyList(),
    val name: String? = null,
    val widgetId: Int = -1,
    val provider: String? = null,
    val builtin: BuiltinKind? = null,
    /** Per item overrides (widget style, note text, photo path, shortcut id...). */
    val extra: Map<String, String> = emptyMap(),
) {
    val isWidget get() = type == ItemType.SystemWidget || type == ItemType.Widget
}
