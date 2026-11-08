<div align="center">

# SCYCHER

**The SCYDE Launcher** — a premium, glass-styled home screen for Android tablets where *every* detail is yours to tune.

Built by [SCYDE](https://scyde.dev) · tuned for the Xiaomi Redmi Pad 2 Pro (HyperOS)

</div>

## Download

Grab the latest signed `Scycher-x.y.z.apk` from the **[`releases/`](releases/)** folder (or GitHub Releases once CI signing is configured) and install it on the tablet.
Then press Home and choose **Scycher** → *Always* (or Settings › Apps › Default apps › Home app).

## Highlights

- **Real glass** – dock, widgets, folders, menus and App Library blur and saturate the wallpaper behind them (iOS-style material with sheen and hairline borders).
- **Dock** – glass / frosted / solid / tinted / clear, any corner radius, size, spacing, shadow, magnification on touch, iPadOS-like recent apps section.
- **Widgets** – Clock, Analog clock, Weather (Open-Meteo, no account), Calendar, Battery, Now Playing (with controls), World clock, Note, Photo, Search, Greeting – each in several sizes, each with its own style, tint, text and accent colors. Any Android app widget works too and can sit on glass.
- **Home screen** – free grid (3–10 × 3–9), drag & drop, folders (drop an app on another), widget resizing, pages, 7 page transitions (Depth, Cube, Carousel, Zoom, Stack…), parallax wallpaper.
- **Icons** – Squircle, rounded, circle, teardrop, pebble, square, hexagon; Default / Dark / Tinted / Monochrome styles (Android 13 themed icons), ADW/Nova icon packs, shadows, glass rim, notification badges (count or dot), custom labels.
- **App Library** – grid, smart categories or A–Z list, fuzzy search (initials work: `gm` → Google Maps), web / Play Store search, hidden apps.
- **Long-press menus** with app shortcuts, add to home/dock, rename, info, uninstall.
- **Home gestures** – swipe up/down, double tap, two-finger swipes and pinch can open the library, notifications, quick settings, lock the screen, take a screenshot or launch any app.
- **Backup & restore** of the whole setup as one JSON file.

## Swipe navigation gestures on HyperOS

HyperOS disables full-screen gestures as soon as a third-party launcher is default. Scycher solves this two ways (Settings › Navigation gestures):

**A. Real HyperOS gestures (recommended, one-time ADB)**

1. Tablet: *Settings › About tablet* → tap *OS version* 7×. Then *Additional settings › Developer options* → enable *USB debugging*.
2. On a computer with [platform-tools](https://developer.android.com/tools/releases/platform-tools):
   ```
   adb shell pm grant dev.scyde.scycher android.permission.WRITE_SECURE_SETTINGS
   ```
3. Open Scycher Settings › Navigation gestures and switch on **Use HyperOS gestures with Scycher** (you can also hide the gesture line there).

   Shortcut: `adb shell settings put global force_fsg_nav_bar 1` turns the gestures on directly.

**B. Scycher Gestures (no computer)** – an accessibility service with thin invisible edge strips: swipe up = Home, swipe up & hold = Recents, swipe in from the left/right edge = Back, with a back-arrow animation, adjustable handle, sizes, sensitivity and haptics. Give Scycher *No restrictions* battery mode and *Autostart* so HyperOS doesn't stop it.

## Building

```
./gradlew :app:assembleRelease
```

Every push to `main` builds an APK in GitHub Actions (artifact); every `v*` tag publishes a signed release.
Signing uses the `SCYCHER_KEYSTORE_B64` and `SCYCHER_KEYSTORE_PASSWORD` repository secrets, so updates install over the previous version.

## Privacy

No analytics, no ads, no accounts. The only network request is the weather forecast to `api.open-meteo.com` for the city you choose. Accessibility and notification access are optional and only used for the features described above.

---

© SCYDE · [scyde.dev](https://scyde.dev)
