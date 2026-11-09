# DESIGN

The source of truth for how Scycher looks and moves. Tokens live in
`app/src/main/java/dev/scyde/scycher/ui/common/Tokens.kt`; materials in `Glass.kt`.

## 1. World

Glass over the user's own wallpaper. The wallpaper is the colour; the interface is light, translucent and quiet on top of it. Continuous ("squircle") corners everywhere, no hard edges, no decoration that isn't a material.

## 2. Materials

| Style | Use | Recipe |
| --- | --- | --- |
| **Liquid** (default) | Dock, widgets, menus, sheets, Control Center modules, Spotlight field | Blurred + saturated wallpaper (blur 24, saturation 1.35) · tint at 55 % of the surface opacity · top-left specular glow · edge lens highlights · 1.1 dp specular rim (bright top-left/bottom-right, faint sides) |
| Glass | Legacy / user choice | Blur + tint + top sheen + hairline |
| Frosted | Small knobs (remove, resize) | Glass + 10 % white |
| Solid / Tinted / Clear | User choice | — |

Rules (Apple, *materials*): heavier/darker material separates structure (menus, sheets: `Palette2.Panel`), lighter material draws attention to controls. Never stack light glass on light glass. When the system wallpaper can't be read, Liquid falls back to a frosted tint – it never shows a flat grey box.

## 3. Colour

All text sits on glass, so labels use vibrancy values rather than greys:

| Token | Value | Use |
| --- | --- | --- |
| `Label` | white | Primary text, icons |
| `Label2` | white 74 % | Secondary text (aimed at ≥ 4.5:1 on the dark Panel material; very bright wallpapers can lower it) |
| `Label3` | white 52 % | Placeholders, tertiary |
| `Separator` | white 14 % | Hairlines in grouped lists |
| `Fill` / `FillStrong` | white 12 % / 20 % | Chips, keys, wells |
| `Destructive` | #FF453A | Delete, remove, wrong PIN |
| `Positive` | #30D158 | Switch on, granted |
| `Blue` | #0A84FF | Connectivity on |
| Accent | user / wallpaper (Ambient theme) | Cursor, toggles, sliders |

## 4. Type

iOS text-style scale (`TypeScale`), Android `sp` so it follows the system font size. Tracking is size-specific: large text tightened, small text opened.

| Style | Size / leading | Weight | Tracking |
| --- | --- | --- | --- |
| LargeTitle | 34 / 41 | Bold | −0.022 em |
| Title1 | 28 / 34 | Bold | −0.018 em |
| Title2 | 22 / 28 | Bold | −0.012 em |
| Title3 | 20 / 25 | SemiBold | −0.010 em |
| Headline | 17 / 22 | SemiBold | −0.006 em |
| Body | 17 / 22 | Regular | −0.004 em |
| Callout | 16 / 21 | Regular | 0 |
| Subhead | 15 / 20 | Regular | 0 |
| Footnote | 13 / 18 | Regular | +0.004 em |
| Caption | 12 / 16 | Medium | +0.01 em |

Widgets scale their type with widget size (unit = 170 dp square) and keep the same hierarchy.

## 5. Shape and spacing

- Continuous corners: panels 60–90 px radius, widgets `cornerRadiusDp` (default 30), modules 80 px, pills fully round.
- Spacing scale (dp): 4 · 6 · 8 · 10 · 12 · 14 · 16 · 18 · 20 · 24 · 28. Tight inside a group, generous between groups.
- Touch targets ≥ 44 dp (Android's 48 dp where the control is standalone). Small visuals (24 dp remove knob, 28 dp resize knob) sit inside a 44 dp hit area.

## 6. Motion

Springs, Apple-style (damping ratio + response; stiffness = (2π / response)²). Critically damped unless a gesture carried momentum.

| Token | Damping | Response | Use |
| --- | --- | --- | --- |
| `press` | 1.0 | 0.16 s | Touch-down feedback (scale to `pressScale`, default 0.92) |
| `ui` | 1.0 | 0.32 s | Menus, fades, appear |
| `sheet` | 0.86 | 0.34 s | App Library, Today View, sheets – after a flick |
| `settle` | 1.0 | 0.36 s | Item settling after drop / reflow |
| `exit` | 1.0 | 0.24 s | Every exit (exits are faster than entrances) |

Principles applied:

- **Respond on touch-down**, not release.
- **Velocity hand-off + momentum projection** when a drag (App Library, Today View) is released: project with deceleration 0.998, then animate from the finger's velocity.
- **Origin-aware**: context menus grow out of the pressed item, folders zoom out of their icon and back into it, Control Center grows from the top-right corner it was pulled from, Spotlight drops from above.
- **Never from zero**: appear/disappear starts at 0.86–0.94 scale.
- **Frequency budget**: returning home (hundreds of times a day) is a 3.5 % scale / 25 % fade, nothing more.
- **Interruptible**: all launcher animations are `Animatable`/springs started from the current value.
- **Reduce motion**: Android's *Remove animations* (animator scale 0) makes launcher transitions instant, disables jiggle, parallax and the folder zoom. *Animation speed* in Settings scales launcher transitions.

## 7. Components

- **Context menu** – blurred backdrop, the pressed item lifts (×1.05) above it, menu card (Panel material) grows from the item; shortcuts first, then actions, destructive last.
- **Dock** – Liquid pill, apps · divider · recents · App Library button. Empty dock shows "Drag apps here" in edit mode only.
- **Folders** – Liquid tile with a 3 × 3 preview; open as a centred panel over a blurred backdrop that zooms from the icon.
- **Control Center** – 4-column module grid (84 dp unit, 14 dp gap): connectivity, now playing, small tiles, tall brightness/volume sliders.
- **Today View** – 420 dp column of widgets; small widgets pair up, large ones span.
- **Customize sheet** – appearance (Default / Dark / Clear / Tinted / Automatic), size, shape, tint, wallpaper dim.
- **Buttons** – `GlassButton` (pill, scale 0.94 on press, ≥ 44 dp tall, labelled for TalkBack even when icon-only). Multi-select uses filled chips, never a glyph checkmark.

## 8. Do not

- Gradient text, emoji or Unicode glyphs as icons, eyebrow labels above headings (widget headers that mirror iOS widgets are the one deliberate exception), hard offset shadows, nested cards.
- Glass as decoration on content that isn't a surface.
- Animations on things the user does hundreds of times a day beyond the budget above.
