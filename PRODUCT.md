# PRODUCT

**Scycher** – the SCYDE Launcher. A home screen for Android tablets (built and tuned on the Xiaomi Redmi Pad 2 Pro, HyperOS) that looks and moves like iPadOS and carries the power features of Smart Launcher.

## Who and where

One person, on their own tablet, many times a day: on the sofa, in bed, at a desk in landscape. Mostly dark rooms and evening light; wallpapers are personal photos or vivid gradients. The launcher is seen hundreds of times a day, so speed and quiet matter more than spectacle.

## Platform

`android` (Jetpack Compose), tablet first, phone tolerated. Minimum Android 8.0, target Android 15.

## Brief (pinned)

- Look like iPad / iPadOS (Liquid Glass era): translucent materials, continuous corners, SF-like type hierarchy, springs, Spotlight, Control Center, Today View, Customize sheet, App Library.
- Every detail customisable (grid, icons, dock, widgets, gestures, glass, motion).
- Feature parity with Smart Launcher 6/7: categories, smart search, ambient colors, app lock, hidden apps, per-app icons and gestures, widget stacks.
- Working swipe navigation on HyperOS with a third-party launcher.

Because the brief pins an iOS visual language, Scycher deliberately does **not** follow Material 3 styling for its own surfaces. It still honours Android's guarantees: system Back, window insets, TalkBack, sp text, 48 dp touch targets and the system "Remove animations" setting.

## Surfaces and modes

| Surface | Mode |
| --- | --- |
| Home screen, dock, folders | Operate |
| App Library, Spotlight | Operate |
| Control Center, Today View | Operate |
| Widgets | Read |
| Settings | Operate |
| Onboarding | Persuade (first run only) |
