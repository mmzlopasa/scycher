package dev.scyde.scycher.data

import kotlinx.serialization.Serializable

/*
 * Every visual and behavioural knob of Scycher lives here. The whole object is
 * serialised to JSON, so backup/restore is a single file.
 */

@Serializable
data class LauncherConfig(
    val version: Int = 2,
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
    /** Per-app icon replaced by an image (app key -> file path). */
    val customIcons: Map<String, String> = emptyMap(),
    /** Swipe up on an app icon opens another app (app key -> app key). */
    val iconSwipeUp: Map<String, String> = emptyMap(),
    /** User-assigned App Library categories (app key -> category). */
    val customCategories: Map<String, String> = emptyMap(),
    val security: SecurityConfig = SecurityConfig(),
    val search: SearchConfig = SearchConfig(),
)

@Serializable
data class SecurityConfig(
    val pinHash: String? = null,
    val biometric: Boolean = true,
    val protectedApps: Set<String> = emptySet(),
    val lockHiddenApps: Boolean = true,
    val lockSettings: Boolean = false,
    val relockSeconds: Int = 30,
)

@Serializable
data class SearchConfig(
    val calculator: Boolean = true,
    val settingsResults: Boolean = true,
    val shortcutResults: Boolean = true,
    val engine: SearchEngine = SearchEngine.Google,
    val showProviders: Boolean = true,
)

enum class SearchEngine(val label: String, val url: String) {
    Google("Google", "https://www.google.com/search?q="),
    DuckDuckGo("DuckDuckGo", "https://duckduckgo.com/?q="),
    Bing("Bing", "https://www.bing.com/search?q="),
    Brave("Brave", "https://search.brave.com/search?q="),
    Ecosia("Ecosia", "https://www.ecosia.org/search?q="),
    Perplexity("Perplexity", "https://www.perplexity.ai/search?q="),
}

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
    /** Labels in CAPITALS. */
    val labelUppercase: Boolean = false,
    /** Light tick when a page snaps into place. */
    val pageHaptics: Boolean = true,
    /** iOS-style blue dot next to apps installed recently and not opened yet. */
    val newAppDot: Boolean = true,
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
    val pressScale: Float = 0.92f,
    val glassRim: Boolean = false,
    /** With Scycher Glyphs: give apps without a glyph a matching tile made from their own monochrome icon. */
    val themeOthers: Boolean = true,
)

enum class IconShape { Squircle, RoundedSquare, Circle, Teardrop, Pebble, Square, Hexagon, System }
enum class IconStyle { Default, Dark, Clear, Tinted, Monochrome, Auto }
enum class BadgeStyle { Count, Dot, Hidden }

@Serializable
data class DockConfig(
    val enabled: Boolean = true,
    val maxIcons: Int = 10,
    val iconSizeDp: Int = 60,
    val style: SurfaceStyle = SurfaceStyle.Liquid,
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
    val magnify: Boolean = false,
    /** iPad-style App Library button at the end of the dock. */
    val libraryButton: Boolean = true,
)

enum class SurfaceStyle { Liquid, Glass, Frosted, Solid, Tinted, Clear }

@Serializable
data class WidgetStyleConfig(
    val style: SurfaceStyle = SurfaceStyle.Liquid,
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
    /** Suggestions learn which apps you open at this time of day. */
    val smartSuggestions: Boolean = true,
)

enum class LibraryStyle { Grid, Tabs, Categories, List }
enum class LibrarySort { Alphabetical, MostUsed, InstallDate }

@Serializable
data class GestureConfig(
    val swipeUp: GestureAction = GestureAction.OpenLibrary,
    val swipeDown: GestureAction = GestureAction.Search,
    /** iPad: pulling down on the right side opens Control Center. */
    val swipeDownRight: GestureAction = GestureAction.ControlCenter,
    val swipeDownRightApp: String? = null,
    /** Swipe right on the first page opens Today View. */
    val todayView: Boolean = true,
    val doubleTap: GestureAction = GestureAction.LockScreen,
    val twoFingerDown: GestureAction = GestureAction.QuickSettings,
    val twoFingerUp: GestureAction = GestureAction.Search,
    val pinchIn: GestureAction = GestureAction.EditMode,
    val pinchOut: GestureAction = GestureAction.OpenLibrary,
    val pinchOutApp: String? = null,
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
    Search("Spotlight search"),
    ControlCenter("Control Center"),
    TodayView("Today View"),
    Notifications("Notifications"),
    QuickSettings("Quick settings"),
    LockScreen("Lock screen"),
    Recents("Recent apps"),
    EditMode("Edit home screen"),
    ScycherSettings("Scycher settings"),
    Screenshot("Screenshot"),
    Flashlight("Flashlight"),
    PowerMenu("Power menu"),
    SplitScreen("Split screen"),
    Widgets("Add widget"),
    Assistant("Assistant"),
    Camera("Camera"),
    VolumePanel("Volume"),
    NextWallpaper("Next wallpaper"),
    WallpaperLightDark("Wallpaper dark / light"),
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
    /** Ambient theme: accent, dock and widget colors follow the wallpaper. */
    val adaptiveColors: Boolean = false,
    /** Built-in wallpaper (WallpaperPack key) or null for a photo / the system wallpaper. */
    val wallpaperPack: String? = null,
    /** "auto" follows the system dark mode, or "dark" / "light". */
    val wallpaperVariant: String = "auto",
    /** Which pack image is currently applied, e.g. "bloom_dark". */
    val wallpaperApplied: String? = null,
    /** Also set pack wallpapers as the system home + lock screen wallpaper. */
    val wallpaperSetSystem: Boolean = true,
    val wallpaperBlur: Float = 0f,
    val vignette: Float = 0f,
    val hideNavBar: Boolean = false,
    val orientation: OrientationMode = OrientationMode.Auto,
    /** Keep the screen on while the home screen is showing. */
    val keepScreenOn: Boolean = false,
    /** Rotate through the Scycher wallpaper pack automatically. */
    val wallpaperShuffle: WallpaperShuffle = WallpaperShuffle.Off,
    val wallpaperShuffleAt: Long = 0,
)

enum class WallpaperShuffle(val label: String, val millis: Long) {
    Off("Off", 0), Hourly("Every hour", 3_600_000), Daily("Every day", 86_400_000), Weekly("Every week", 604_800_000)
}

enum class OrientationMode { Auto, Landscape, Portrait }

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
    /** With HyperOS gestures (option A): Scycher also watches the bottom edge and makes sure swipe up always goes home. */
    val homeRescue: Boolean = false,
    /** User turned gestures on in Scycher: switch the service back on if HyperOS disables it. */
    val keepServiceOn: Boolean = false,
)

@Serializable
data class HomeLayout(
    val pages: List<HomePage> = listOf(HomePage(1L)),
    val dock: List<HomeItem> = emptyList(),
    /** Widgets in Today View (left of the first page). */
    val today: List<HomeItem> = DefaultToday,
)

val DefaultToday = listOf(
    HomeItem(900_001, ItemType.Widget, w = 4, h = 2, builtin = BuiltinKind.Weather),
    HomeItem(900_002, ItemType.Widget, w = 2, h = 2, builtin = BuiltinKind.Calendar),
    HomeItem(900_003, ItemType.Widget, w = 2, h = 2, builtin = BuiltinKind.Battery),
    HomeItem(900_004, ItemType.Widget, w = 4, h = 2, builtin = BuiltinKind.Agenda),
    HomeItem(900_005, ItemType.Widget, w = 4, h = 2, builtin = BuiltinKind.NowPlaying),
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
    Stack("Smart stack", listOf(2 to 2, 4 to 2, 4 to 4)),
    Agenda("Up next", listOf(4 to 2, 2 to 2, 4 to 4)),
    Toggles("Quick toggles", listOf(4 to 1, 2 to 2, 4 to 2)),
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
