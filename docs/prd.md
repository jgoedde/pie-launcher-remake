# Pie Launcher v1 — Product Requirements

Status: implementation-ready. This document stitches together every decision recorded on the map [Pie Launcher v1 spec](https://github.com/jgoedde/pie-launcher-remake/issues/1). Each section links the ticket that holds the full reasoning; when this document and a ticket disagree, the later ticket wins and this document must be fixed.

Companion documents:

- [`GLOSSARY.md`](../GLOSSARY.md): the domain vocabulary. Use these terms in code, UI strings and commits. Capitalised terms in this document (Pie, Layer, Pie Item, …) are glossary terms.
- [`docs/adr/`](adr/): architecture decisions 0001–0007.
- [`spec/pie-config.schema.json`](../spec/pie-config.schema.json), [`spec/pie-config.example.json`](../spec/pie-config.example.json), [`spec/inventory.example.json`](../spec/inventory.example.json): the Pie Config format and the Inventory export.
- [`LICENSE`](../LICENSE) (Apache-2.0) and [`NOTICE`](../NOTICE).

## 1. Product

Pie Launcher is an Android home-screen launcher for one owner's daily phone (Pixel 6, GrapheneOS, Android 17). It is open source and distributed as an APK via GitHub Releases. It succeeds [Fly-Pie-Launcher](https://github.com/jgoedde/Fly-Pie-Launcher) (React Native): the concepts carry over, the code and config format do not.

There is no static app-icon grid. Apps open almost always through the **Pie**: a touch-down in the widget-free **Pie Zone** in the lower middle of the **Home Page** opens a radial menu at the finger; the user drags towards a **Pie Item** and releases to launch it. The rest of the home screen holds widgets like a normal launcher. An **App Drawer** lists every app and pinned Shortcut with search. Icons are Material You **Themed Icons** throughout, including apps without a monochrome layer and browser **PWAs**.

The Pie is configured only by importing a JSON **Pie Config** file. There is no interactive Pie editor. The launcher exports an **Inventory** of every app and Shortcut id so the user can write that file.

### Working mode

The owner (@jgoedde) makes decisions and does human tasks (device checks, signing keys, smoke tests). Agents write all code.

## 2. Platform and stack

- Kotlin, Jetpack Compose, Material 3 Expressive. `material3` 1.5.0-rc01 via the alpha Compose BOM 2026.11.00; `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` allowed ([ADR 0003](adr/0003-material3-expressive-rc.md)). Move to 1.5.0 stable when it ships.
- `minSdk = targetSdk = compileSdk = 37` (Android 17). No support for older versions. compileSdk uses minor API level 2 (`android-37.2`) because the Compose BOM requires 37.1 or later.
- applicationId and package root `io.github.jgoedde.pielauncher`.
- Portrait only. Edge-to-edge (mandatory at targetSdk 37); predictive back is on by default.
- Main user profile only (no work profile, no Private Space).

Source: [What do Android 17 and Compose M3 Expressive offer a launcher?](https://github.com/jgoedde/pie-launcher-remake/issues/5), [What is the app architecture?](https://github.com/jgoedde/pie-launcher-remake/issues/10).

## 3. Home screen

Sources: [How does widget editing work in the Widget Zones?](https://github.com/jgoedde/pie-launcher-remake/issues/9), [What does the Home Page look like outside the Pie?](https://github.com/jgoedde/pie-launcher-remake/issues/14).

### 3.1 Pages

- Pages scroll horizontally in a stock Compose `HorizontalPager`.
- The **Home Page** is always the leftmost Page and cannot be removed. Launcher start and the Home button land on it. It is the only Page with a Pie Zone.
- Every other Page is one Widget Zone filling its full height. No Pie there.
- New Pages are added only on the right: by dwelling with a dragged widget at the right edge, and when a tap-add from the Widget Picker or a widget Pin Request finds no room on any existing Page (a new Page is appended for it). There is no "Add Page" menu entry. Empty non-Home Pages are removed when Edit Mode ends. No Page reordering.
- Page indicator in Launcher3 style, just above the gesture strip, visible only while swiping Pages and in Edit Mode.

### 3.2 Home Page layout

All values are code constants, never configuration.

- Top to bottom: **upper Widget Zone** (takes the remaining height), **Pie Zone** (~240 dp tall, full width), **lower Widget Zone** (exactly two cell rows).
- Everything sits above `WindowInsets.mandatorySystemGestures`. Touches in that bottom strip never reach the launcher.
- The lower Widget Zone is also where a swipe-up for the App Drawer typically starts.
- The Pie Zone is invisible at rest. In Edit Mode it is drawn as a blocked area.
- Nothing is built in: no search bar, no at-a-glance, no clock. Those come from real widgets; search lives in the App Drawer.
- Status and navigation bars transparent, no scrim at rest. Status-bar icon colour follows `WallpaperColors` (light/dark hint).

### 3.3 Cell grid

- Every Widget Zone uses a 4-column grid with a constant gap between cells (~16 dp, a code constant). Cell width = (zone width − 3 gaps) / 4. Cells are roughly square: row count = the largest number of rows (with gaps between them) that fits the zone height. Leftover height is spread evenly as padding; cells are not stretched.
- Non-Home Pages use the same cell size over their full height.
- A widget belongs to exactly one Widget Zone. Nothing ever occupies the Pie Zone.
- A widget spans whole cells plus the gaps between them. Spacing between widgets comes from the gap only (section 7.1).

## 4. Pie

Sources: [How should the Pie look and behave?](https://github.com/jgoedde/pie-launcher-remake/issues/6) (prototype on branch [`prototype/pie`](https://github.com/jgoedde/pie-launcher-remake/blob/prototype/pie/prototypes/pie/pie-prototype.html)), [What does the Home Page look like outside the Pie?](https://github.com/jgoedde/pie-launcher-remake/issues/14), [Which icon theming options does v1 offer?](https://github.com/jgoedde/pie-launcher-remake/issues/17).

All numbers below are code defaults, **not** Pie Config fields and not settings. Pixel values come from the browser prototype; convert them to dp and tune on the device.

### 4.1 Gesture

1. Touch-down in the Pie Zone opens the Pie centred at the finger, always on the **Base Layer**. The touch-down consumes the whole gesture: a Pie drag never changes the Page or opens the Drawer or shade.
2. The user drags. The hovered Pie Item is determined by the hit model (4.3).
3. Release:
   - on an app item: launch the app (activity from the config, else `getLaunchIntentForPackage`);
   - on a Shortcut item: `LauncherApps.startShortcut`;
   - on a Shortcut in an open Shortcut list: launch that Shortcut;
   - on a Layer Switch, on an Unavailable Item, or with nothing selected: cancel, nothing happens.
4. The Pie closes on release.

The Pie is not available in Edit Mode.

### 4.2 Layout and look

- Ring layout: the Layer's 1–12 Pie Items evenly spaced, clockwise from 12 o'clock in config order. Pie radius ~120 px.
- Hover indicator: an outline around the icon, and the icon scales up.
- While the Pie is open a full-screen scrim (~40 % black) covers everything including widgets and the status bar, fading with the Pie motion. Widgets get no touches until release.
- The ring and Shortcut list may overlap both Widget Zones.
- The Pie never leaves the screen: its centre is clamped so the ring stays inside the window minus system-bar insets, plus ~8 dp margin.
- Icons: the same Themed Icon (or Original Icon) as in the App Drawer, clipped to the Icon Shape (section 8).
- Layer Switch item: the target Layer's Material Symbol drawn on the target Layer's colour, symbol colour light or dark for contrast, clipped to the Icon Shape. Not affected by the Themed Icons switch.
- Unavailable Item: stays in its slot, greyed out with a generic icon (section 9.4).
- Visual direction: M3 Expressive presets (`MotionScheme.expressive`, `MaterialShapes` / graphics-shapes `Morph`), not custom motion curves. Soft rather than spiky shapes; motion calmer and slower than the prototype. Final motion tuning happens on the device during the build.

### 4.3 Hit model

- **Band model.** The **Inner Circle** radius is the Pie radius minus the capture radius (30 px), ≈ 90 px. Once the finger is outside the Inner Circle, only its direction from the Pie centre counts; there is no outer limit. The line from the centre to the finger picks the item whose sector it crosses.
- **Hysteresis:** the current item stays hovered until the angle deviates by more than 65 % of a sector.
- **Inside the Inner Circle** nothing is hovered. The innermost ~26 px are the **centre**: moving there also closes an open Shortcut list.
- An open Shortcut list card is hit-tested first, before the ring.

### 4.4 Hover Delay and Progress Disc

- **Hover Delay** ~675 ms. One value gates both Layer Switches and opening an app's Shortcuts.
- While the finger dwells on a Layer Switch, or on an app item that has Shortcuts, the **Progress Disc** (full Inner Circle size) fills as a clock sweep. Colour: the target Layer's colour for a Layer Switch, otherwise the Monet primary colour.
- Hovering a Shortcut item, an app without Shortcuts or an Unavailable Item shows no Progress Disc and nothing fires.

### 4.5 Layer Switch

- When the Hover Delay elapses on a Layer Switch, the target Layer replaces the current one, and the Pie **re-centres at the finger position** (then clamped). The finger starts in the centre of the new Layer with nothing selected.
- Layer Switches may point back to the Base Layer.

### 4.6 Shortcut list

- When the Hover Delay elapses on an app item, a list card (Fly-Pie style) opens beside the icon with that app's Shortcuts (manifest and dynamic, as `LauncherApps.getShortcuts` returns them for the activity), capped like the App Drawer menu (section 6.6).
- It opens toward the side with room, horizontally and vertically. Example: Pie opened at the bottom of the Pie Zone with the lowest item hovered → the list opens upward.
- The list stays open while the thumb travels to it. Releasing on a row launches it. Moving over another Pie Item or back into the centre closes it.

### 4.7 Haptics

A light tick on hover enter; a stronger tick on Layer Switch, on Shortcut list open, and on launch.

## 5. Home gestures

Sources: [Do hosted widgets and launcher gestures coexist in Compose?](https://github.com/jgoedde/pie-launcher-remake/issues/13) (spike on branch [`prototype/widget-gestures`](https://github.com/jgoedde/pie-launcher-remake/tree/prototype/widget-gestures)), [How do home gestures behave at the system edges?](https://github.com/jgoedde/pie-launcher-remake/issues/18).

| Gesture (outside the Pie Zone) | Result |
|---|---|
| Swipe up | App Drawer opens, following the finger (section 6) |
| Swipe down | Notification shade opens (animated, not finger-tracking) |
| Horizontal swipe | Page change (`HorizontalPager`) |
| Long-press on a widget | Widget popup and Resize Frame together; drag to move (section 7.3) |
| Long-press on empty Widget Zone space | Empty-space menu (section 7.2) |
| Double-tap | nothing (out of scope) |

### 5.1 Implementation rules

- Pages use the stock `HorizontalPager`. Swipe-up and swipe-down are ordinary Compose vertical drag detection on the home surface (Main pass). No custom intercept layer.
- `AndroidView` honours `requestDisallowInterceptTouchEvent`. On a scrollable widget, vertical drags belong to the widget: no Drawer or shade from inside a list. Horizontal drags still change the Page unless the widget scrolls horizontally.
- Widget long-press is detected in a View wrapper: a `FrameLayout` around `AppWidgetHostView` with a timer in `onInterceptTouchEvent` (Launcher3 `CheckLongPressHelper` style), firing after 0.75 × the system long-press timeout with a `LONG_PRESS` haptic. It cancels on touch slop; once it fires it takes the touch away from the widget.
- On a scrollable widget the wrapper calls `requestDisallowInterceptTouchEvent` on touch-down and reverts it when a widget drag starts.

### 5.2 Notification shade

- Opens only by reflection on the hidden `StatusBarManager.expandNotificationsPanel()`, with the normal-level `EXPAND_STATUS_BAR` permission (verified on GrapheneOS at targetSdk 37).
- Triggers once the drag passes touch slop with `dy > slop && dy > |dx|` (Launcher3 `StatusBarTouchController`).
- No AccessibilityService fallback. If reflection fails, swipe-down does nothing and the failure is logged once. No toast.

### 5.3 Back-gesture exclusion

- At rest on the home surface, both side edges are excluded from the system back gesture over the full height (Widget Zones and Pie Zone, every Page). Exclusion width = the left/right `systemGestures` inset. The home activity is exempt from the 200 dp cap.
- Effect: a Page swipe can start at the very edge, and a touch-down at the edge of the Pie Zone opens the Pie (clamped).
- The exclusion is dropped while an overlay is open: App Drawer, Edit Mode, or any popup (widget popup, empty-space menu). Back then closes that overlay.

### 5.4 Home button / gesture

- On a non-Home Page: animated scroll to the Home Page.
- On the Home Page with nothing open: nothing.
- In Edit Mode: leaves Edit Mode. An open App Drawer closes.

## 6. App Drawer

Source: [How does the App Drawer behave?](https://github.com/jgoedde/pie-launcher-remake/issues/7).

### 6.1 Open and close

- Opens by swipe-up outside the Pie Zone, following the finger; pulling back cancels.
- Opaque Monet surface background, no blur.
- Closes via back, swipe down while scrolled to the top, Home, or launching any entry.
- Every open starts with an empty search and the list scrolled to the top.

### 6.2 Entries

- Vertical list, one **Drawer Entry** per row: icon + label.
- One row per launcher activity (a package with several launcher activities gets several rows).
- Pinned Shortcuts (PWAs and any other pinned Shortcut) are top-level rows next to apps, sorted and searched like apps, with a small badge of the owning app.
- The launcher's own "Pie Launcher Settings" activity is a row like any app (section 10).
- Order: alphabetical by label, case- and accent-insensitive, locale-aware collation (`Collator`). No recent/frequent section.
- New apps and new pinned Shortcuts appear immediately.

### 6.3 New Badge

- A small "New" chip/dot on the Drawer row only (never in the Pie), for newly installed apps and newly pinned Shortcuts.
- Cleared on the entry's first launch (from Drawer, Pie or search) or after 7 days, whichever is first. App updates do not count as new. Does not affect sort order.
- Stored in Room (section 11).

### 6.4 Search

- Search field at the top. Opening the Drawer focuses it and opens the keyboard. Scrolling the list hides the keyboard.
- Matches the label only (not the package name).
- Ranking tiers: label prefix > word prefix / word initials ("gm" → Google Maps) > substring > fuzzy (typo-tolerant). Ties alphabetical. Implemented in `:core`.
- Budget: ≤ ~4 ms per keystroke for ~500 entries. If fuzzy cannot meet it, drop the fuzzy tier and keep the others.
- Results top-down, best hit first, directly under the field and highlighted. Keyboard Enter/Go launches the top hit.
- No results: hint text with a large Material Symbol `search_off` inside an M3 Expressive shape (e.g. cookie/clover) in a Monet container colour, gently morphing on appear. No web search. Enter does nothing.

### 6.5 Alphabet fast-scroller

- On the right edge. Shows only letters that have entries. Accented letters fold into their base (Ä → A). Digits and symbols go into a "#" bucket at the top.
- While dragging: large letter bubble and a haptic tick on each letter change.
- Hidden while a search query is active.

### 6.6 Long-press menu

- App: its Shortcuts first, then **Widgets** (only if the app has at least one widget provider; opens the Widget Picker filtered to that app, section 7.4), then **App info**, then **Uninstall** (hidden for system apps).
- At most 5 Shortcuts: manifest Shortcuts before dynamic ones, each by the app's rank; the rest are not shown.
- Pinned Shortcut: **Remove** (section 7.5) and **App info** of the owning app.
- Placement: the menu opens above and to the left of the touch point, away from a right thumb. Its end edge sits at the touch X and its bottom edge on the pressed row's top edge, so the row stays visible. Physical direction, also in RTL; no handedness setting.
- No room above (within 48dp of the window edge, as M3 menus keep): it flips below the row, still opening to the left. No room to the left: it shifts right until it fits. No room above or below: it clamps to the window edges. Item order stays the same either way.

## 7. Widgets

Sources: [How does a Compose launcher host app widgets?](https://github.com/jgoedde/pie-launcher-remake/issues/2) ([findings](https://github.com/jgoedde/pie-launcher-remake/blob/research/widget-hosting/research/widget-hosting.md)), [Which widget-hosting operational concerns must v1 handle?](https://github.com/jgoedde/pie-launcher-remake/issues/19) ([findings](https://github.com/jgoedde/pie-launcher-remake/blob/research/widget-operations/research/widget-operations.md)), [Widgets: add, edit, move and resize like Launcher3](https://github.com/jgoedde/pie-launcher-remake/issues/34) ([findings](https://github.com/jgoedde/pie-launcher-remake/blob/research/launcher3-widget-ux/research/launcher3-widget-ux.md)), [ADR 0006](adr/0006-own-widget-picker.md), [ADR 0007](adr/0007-widget-reflow.md).

Widget handling mimics AOSP Launcher3 on Android 17. The reference is the GrapheneOS stock launcher on the owner's Pixel 6. All numbers below are code constants.

### 7.1 Hosting and rendering

- Classic `AppWidgetHost` / `AppWidgetManager` / `AppWidgetHostView`; nothing Compose-specific. One process-wide `AppWidgetHost` with a fixed host id, listening while the Home Activity is started (`startListening` on `onStart`, `stopListening` on `onStop`).
- Each widget is an `AppWidgetHostView` inside an `AndroidView`, wrapped in the long-press `FrameLayout` (5.1).
- Manifest needs `<queries><intent><action android:name="android.appwidget.action.APPWIDGET_UPDATE"/></intent></queries>`, otherwise `getInstalledProviders()` returns only system packages.
- **No framework padding.** `setPadding` is a no-op while the widget is being set. A widget fills its cells; spacing comes from the cell gap (3.3).
- **Rounded corners.** On layout, the wrapper clips the widget's background to `system_app_widget_background_radius`. The background is, in order: the single `@android:id/background` view; else the first drawing descendant or one with ≥ 2 visible children; else the host itself. A widget whose background has that id and sets `clipToOutline` rounds itself and is not clipped again.
- `setOnLightBackground` follows the wallpaper's dark-text hint. No `setColorResources`.
- **Size reporting.** Sizes go out only through `AppWidgetManager.updateAppWidgetOptions`: the size list for the widget's span (cell size × span plus the gaps between cells, in dp, portrait only) and the min/max derived from it. Sent at bind, after add, after a shrink on drop, and live during resize; only when the list changed. `AppWidgetHostView.updateAppWidgetSize` is not used.
- **Spans** follow Launcher3 `initSpans`: `span(size) = max(1, ceil((size + gap) / (cell + gap)))`. Default span from `minWidth/minHeight`, unless `targetCellWidth/Height` lies within [min span, max span] on both axes. Min span from `minResizeWidth/Height`, capped at the default span. Max span from `maxResizeWidth/Height`, else the grid. An axis is resizable only if its `resizeMode` bit is set and a real range exists.
- **Too large:** the min span exceeds the largest Widget Zone (the Home Page's upper zone).
- Memory guardrails: async inflation via `setExecutor`, own `getErrorView()`, no launcher-side widget bitmap caching, and at startup log any `MemoryLimiter` exits from `ApplicationExitInfo`. No eviction or widget limit. Host views of a destroyed Activity are released, so a rotation or theme change leaves no stale view.
- `onAppWidgetRemoved`: drop the layout row. Re-checking broken widgets on providers-changed is nice-to-have.
- At startup, reconcile Room against `getAppWidgetIds()`: delete host ids with no row and rows with no host id.
- Visibility tracking for engagement metrics (`start/stopVisibilityTracking()` for the visible Page in the normal state) is nice-to-have. Clicks and scrolls are recorded automatically.
- Prior art: Kvaesitso (GPL, read only, section 13) for Compose embedding; Launcher3/Lawnchair (Apache-2.0) for the listening state machine, configure rules, size bundles, touch interception, `ReorderAlgorithm` and the Resize Frame. Launcher3's `DeviceProfile` and drag plumbing are not reused.

### 7.2 Menus and Edit Mode

- **Long-press on empty Widget Zone space** opens a Launcher3-style popup anchored at the touch point: "Wallpaper & style" (system wallpaper and style picker), "Widgets" (Widget Picker, 7.4), "Home settings" (Launcher Settings). No "Add Page".
- **Long-press on a widget** opens, at the same moment, the widget popup and the Resize Frame (7.3). The popup has "Remove", plus "Widget settings" when the provider is `reconfigurable`. Moving the finger past touch slop after the long-press closes the popup and starts a move.
- "Widget settings" reopens the configure screen through the trampoline Activity. A cancel keeps the widget as it was.
- **Edit Mode** lasts from the long-press on a widget until the move has ended and the Resize Frame has closed. Home holds one state: idle, popup and frame open, dragging, or resizing. Outside idle the Pie Zone is drawn blocked, the Pie is off and the Page indicator is visible.
- Back or Home returns to idle and commits the current layout; it does not undo it.

### 7.3 Move, reflow and resize

Reflow follows Launcher3 `ReorderAlgorithm` ([ADR 0007](adr/0007-widget-reflow.md)). The logic is the **Widget Layout** module in `:core`.

- **Move.** A dragged widget snaps to cells, and its target cells are previewed. It may cross the Pie Zone into the other Widget Zone. Dwelling ~500 ms at the left or right edge changes the Page (creating a new Page at the right end, 3.1).
- **Reflow.** After the finger rests 650 ms, the reorder preview is computed in this order: push neighbours aside; else the closest empty space without moving anything; else drop in place; else shrink the dragged widget one cell at a time (X, then Y) down to its min span. Moving neighbours animate (150 ms) and wiggle (12 %) in their temporary spots. The drop commits the preview shown.
- Reflow stays within the target Widget Zone. Nothing is pushed into or across the Pie Zone. A widget moved into the two-row lower Widget Zone shrinks to fit when its min span allows.
- A drop on the Pie Zone, or where nothing fits even at min span, is rejected: "No room on this home screen", and the widget springs back.
- A widget that shrank on drop gets its new size (7.1).
- **Drop-target bar** at the top while dragging: "Remove", plus "Widget settings" when reconfigurable.
- **Resize Frame.** A rounded outline in the accent colour (2 dp stroke, system widget corner radius), with a dot handle at the middle of each edge (48 dp touch target), only on the resizable axes. While one handle is dragged the others fade out. Past 0.66 of a cell the widget grows or shrinks by one cell with a `SEGMENT_TICK` haptic. The size is bounded by the min/max span and the zone edges. Neighbours are pushed aside; when they can't move, the size does not grow. The widget redraws live. On release the frame snaps to the new bounds (150 ms). A tap outside closes it and commits the layout. A widget below its min span (after a display change) can still be resized.

### 7.4 Add

- **Widget Picker** ([ADR 0006](adr/0006-own-widget-picker.md)): its own Activity with a Compose bottom sheet titled "Widgets". Opened from the empty-space menu, or filtered to one app from the App Drawer "Widgets" entry (6.6). The system `ACTION_APPWIDGET_PICK` picker is not used.
  - Closes on swipe down, handle drag, back, or screen lock.
  - One expandable header per app (icon, name, widget count), apps alphabetical. No tabs (no Featured, no Work).
  - Each tile: preview drawn at the default size, label, "W × H", description. Preview source, in order: generated preview (only when the framework returns one), `previewLayout`, `previewImage`, a placeholder of the cell size.
  - Search: lowercased whitespace tokens over app name, widget label and description. An app-name match shows all of that app's widgets. Order stays alphabetical by app.
  - Too-large widgets are greyed out with "Too large for this home screen".
- **Tap-add.** Tapping a tile shows an "Add" button over it; tapping the tile again hides it. "Add" closes the picker and places the widget at its default span, at the first free spot scanning row by row: first the long-pressed Widget Zone, then the other one. From the filtered picker: the Home Page, upper zone first. If nothing fits, a Page is appended (3.1).
- **Drag-to-place.** Touch and hold a preview: `LONG_PRESS` haptic, the picker finishes, and the widget follows the finger over Home through system drag-and-drop (`DRAG_FLAG_GLOBAL`, own MIME type with the provider component). Home turns it into the normal move with reflow (7.3).
- **Bind and configure.**
  1. Allocate an id, then `bindAppWidgetIdIfAllowed` with the size options for the target span; else the system `ACTION_APPWIDGET_BIND` dialog.
  2. Configure via `startAppWidgetConfigureActivityForResult` through the trampoline Activity, unless the provider sets both `configuration_optional` and `reconfigurable`.
  3. Meanwhile a placeholder with the preview sits in the target cells.
  4. On success, persist id + placement and open the Resize Frame. Any cancel deletes the id and removes the placeholder.

### 7.5 Remove

- Via the widget popup, or by dropping on "Remove" in the drop-target bar. No confirmation dialog.
- The widget disappears at once and an "Item removed" snackbar with "Undo" shows for 4 s (180 ms show and hide).
- `deleteAppWidgetId` and the row delete run only when the snackbar times out. Undo restores the row and the view in the same cells, with its state intact.

### 7.6 App-initiated widget pinning

- `requestPinAppWidget` (`PinItemRequest` of type widget) opens a Launcher3-style sheet in the pin Activity, built from the Widget Picker's preview and tile components. It shares the Pin Request plumbing with section 7.7.
- Title: the app's name. Hint: "Touch and hold to move a widget." A preview card shows label, "W × H" and description; an app-supplied preview takes precedence.
- Buttons "Cancel" and "Add to home screen".
- "Add to home screen": bind (fall back to the bind dialog; a cancelled bind deletes the id and keeps the sheet open), `accept()` with the size options and the id, then place at the first free spot on the Home Page, upper zone first. If nothing fits, a Page is appended (3.1). The launcher shows no configure screen; the app configures the widget itself.
- Touch and hold the preview card: same drag-to-place as the Widget Picker.
- "Too large for this home screen" is computed before the sheet shows and disables the button.
- Declining leaves nothing behind.

### 7.7 Pin Requests (Shortcuts)

Sources: [How do Vanadium and Firefox PWAs reach the launcher?](https://github.com/jgoedde/pie-launcher-remake/issues/3) ([findings](https://github.com/jgoedde/pie-launcher-remake/blob/research/pwa-mechanics/research/pwa-mechanics.md)), [What happens when a browser asks to pin a PWA?](https://github.com/jgoedde/pie-launcher-remake/issues/11).

- Vanadium, Chrome and Firefox all deliver PWAs as pinned Shortcuts via `ShortcutManager.requestPinShortcut`. No WebAPKs.
- The launcher must be the default home app (grants `hasShortcutHostPermission`) and export an activity for `android.content.pm.action.CONFIRM_PIN_SHORTCUT`.
- **Every Pin Request is accepted**, PWAs and any other app's Shortcut alike. No launcher confirmation (the requesting app already asked). The activity stays invisible: it calls `PinItemRequest.accept()`, records `(package, shortcutId, user)`, and shows a toast "'X' added to App Drawer".
- Destination: App Drawer (with New Badge) and Inventory only. Never into a Layer.
- Same id pinned again (Firefox reuses `start_url`): still `accept()`, no second Drawer Entry, label and icon refreshed, toast "'X' is already in the App Drawer". Vanadium mints a new UUID on every add, so a duplicate entry appears; that is accepted.
- List pinned Shortcuts with `getShortcuts(FLAG_MATCH_PINNED)` (only this launcher's pins), launch with `startShortcut`, watch `onShortcutsChanged`.
- **Remove** (Drawer long-press): the entry disappears at once with an undo snackbar (~5 s). Only after it expires call `pinShortcuts(pkg, remainingIds, user)`: Android deletes a purely pinned Shortcut once unpinned, so undo after the call is impossible. Never pass an accidentally empty list.
- A removed, missing or disabled Shortcut referenced by a Layer becomes an Unavailable Item; the Pie Config is never edited and no warning is shown before removal.
- Disabled Shortcuts (`disableShortcuts` by their app) leave the App Drawer but stay in the Inventory export with `"disabled": true`.
- Never drop a reference because a lookup returned empty at boot (GrapheneOS Launcher3 race, os-issue-tracker#8257).

## 8. Icons and theming

Sources: [How do we theme every app icon with Material You?](https://github.com/jgoedde/pie-launcher-remake/issues/4) ([findings](https://github.com/jgoedde/pie-launcher-remake/blob/research/themed-icons/research/themed-icons.md)), [Which device facts does the owner's phone confirm?](https://github.com/jgoedde/pie-launcher-remake/issues/15), [Which monochrome fallback do we ship?](https://github.com/jgoedde/pie-launcher-remake/issues/16) (prototype on branch [`prototype/mono-fallback`](https://github.com/jgoedde/pie-launcher-remake/tree/prototype/mono-fallback/prototypes/mono-fallback)), [Which icon theming options does v1 offer?](https://github.com/jgoedde/pie-launcher-remake/issues/17) ([findings](https://github.com/jgoedde/pie-launcher-remake/blob/research/theme-picker-provider/research/theme-picker-provider.md)).

### 8.1 Themed Icon

- A Themed Icon is an Icon-Shape-clipped solid Monet background plus an alpha mask tinted with a Monet foreground. Colours (Launcher3 pairs): light `accent1_700` on `accent1_100`; dark `accent1_200` on `accent2_800`. Light/dark follows the system dark mode only.
- Same look in the Pie, the App Drawer and Shortcut lists.

### 8.2 Mask source

| Icon | Mask |
|---|---|
| Adaptive with monochrome layer (`getMonochrome()` non-null) | that layer |
| Adaptive without monochrome, or legacy bitmap (wrapped to adaptive Launcher3-style) | **Generated Monochrome**: port of AOSP `MonochromeIconFactory` |
| Shortcut with resource/vector icon and no monochrome layer | Generated Monochrome |
| Shortcut with a bitmap icon (e.g. contact photo), not a PWA | **Original Icon**, untinted (still clipped to the Icon Shape) |
| **PWA** (pinned Shortcut whose package resolves `VIEW https` with `CATEGORY_BROWSABLE`) | always Generated Monochrome on the favicon |

- 36 % of the owner's apps take the Generated Monochrome path (22 adaptive without monochrome, 8 bitmap-only), so it is a core look, not an edge case.
- AOSP algorithm (current source): flatten background and foreground over black, use the grey value as alpha, stretch min/max, invert if the top and bottom edge rows (outside the visible 72 dp) are bright. No S-curve.
- Bitmap detection: the launcher cannot see a Shortcut's icon source type (`hasIconResource()` etc. are `@hide`). A foreground that is a `BitmapDrawable`, or a plain `BitmapDrawable`, counts as a bitmap.
- No per-app or per-item override.
- Shortcut icons come from `LauncherApps.getShortcutIconDrawable` and use the same pipeline.

### 8.3 Cache

- Masks are ALPHA_8 files in `cacheDir`, keyed by package + `versionCode` (plus size and algorithm version), with an in-memory `LruCache` in front. Tint at draw time, so wallpaper changes need no regeneration. No Coil.
- Invalidate via `LauncherApps.Callback` on package change.

### 8.4 Themed Icons switch and Icon Shape (system picker)

- One global **Themed Icons** switch, default on. Off = every icon is an Original Icon, PWAs included. Layer Switches are not affected.
- **Icon Shape:** the AOSP Launcher3 shape list (circle, rounded square, 4-sided cookie, 7-sided cookie, arch; Apache-2.0 port), default circle. One shape clips everything: Themed Icons, Original Icons and Layer Switches.
- Both are set in the system "Wallpaper & style" (App icons → Style "Minimal" = themed; Shape). The launcher implements the ThemePicker provider contract:
  - Home Activity meta-data `com.android.launcher3.themedicon.option` and `com.android.launcher3.grid.control`, both pointing at one provider.
  - `icon_themed`: query returns one row with `boolean_value` 1/0; update stores it, returns > 0, calls `notifyChange`.
  - `shape_options`: rows `shape_key`, `shape_title`, `path` (SVG path, 100×100 viewport), `is_default` as the string `"true"`/`"false"`, exactly one default. Update `shape` with `shape_key`, then `notifyChange`.
  - Grid queries (`list_options`, `default_grid`) and `get_preview` return null; the picker then hides those sections.
  - Provider protected with `android:permission="android.permission.BIND_WALLPAPER"`.
  - Both values persist in Preferences DataStore.
- Launcher Settings only link to "Wallpaper & style".
- The picker caches the provider in a singleton; after switching the default launcher it may keep the old one until its process dies.
- **Acceptance criterion:** Style and Shape changed in "Wallpaper & style" take effect on the owner's Pixel 6. Only if that fails does the launcher get its own fallback settings for them.

## 9. Pie Config

Sources: [What is the v1 pie-config JSON schema?](https://github.com/jgoedde/pie-launcher-remake/issues/8), [Which device facts does the owner's phone confirm?](https://github.com/jgoedde/pie-launcher-remake/issues/15), [ADR 0005](adr/0005-no-runtime-json-schema-validation.md).

### 9.1 Format

The normative format is [`spec/pie-config.schema.json`](../spec/pie-config.schema.json) (JSON Schema 2020-12; semantic rules in its `$comment`). Example: [`spec/pie-config.example.json`](../spec/pie-config.example.json). Summary:

- Top level `{ "$schema", "version": 1, "baseLayer": "<layer id>", "layers": [...] }`.
- Layer: `id` (slug `^[a-z0-9-]+$`), `name` (only in messages and exports, never in the Pie), `color` (`#RRGGBB` or Monet role `primary|secondary|tertiary`, default `primary`), `icon` (Material Symbols Rounded name, default `layers`), `items` (1–12, clockwise from 12 o'clock).
- Pie Items by `type`:
  - `{ "type": "app", "package", "activity"? }`; without `activity`, launch what `getLaunchIntentForPackage` returns.
  - `{ "type": "layer", "layer" }`: Layer Switch.
  - `{ "type": "shortcut", "package", "shortcutId" }`: pinned, manifest and dynamic Shortcuts alike. `shortcutId` is any non-empty string (UUIDs, full URLs with `?&=`).
- No per-item visual options.

Layer Switch icons need the Material Symbols Rounded names at runtime, both to validate `icon` and to draw it. Bundle the Material Symbols Rounded font (Apache-2.0, list it in `NOTICE`) with its codepoints file and render by codepoint.

### 9.2 Decoding and validation

- kotlinx.serialization plus hand-written semantic validation in `:core`. No JSON Schema library at runtime.
- A JVM test validates the `spec/` examples and all test fixtures against the schema with a test-only JSON Schema library, so schema and code cannot drift.

### 9.3 Import

- File picked via SAF (`OpenDocument`, JSON).
- **Atomic:** any error rejects the file; nothing changes.
- **Strict:** unknown fields are errors (`$schema` allowed); a `version` higher than supported is rejected.
- **Errors** (all reported at once with JSON path + message): schema violations; unknown `baseLayer`; duplicate Layer ids; Layer Switch to an unknown Layer or to its own Layer; unknown icon name.
- **Warnings** (import may proceed): unresolved app/activity/Shortcut reference; Layer unreachable from the Base Layer; duplicate target within one Layer; app item without `activity` whose package has several launcher activities (names the launched one and the alternatives).
- After validation a confirmation dialog is shown (section 10.3). On "Apply" the config is written to internal storage in normalized form (pretty-printed, same content) and becomes the active Pie Config; the import time and warnings are kept for the status row.

### 9.4 Runtime

- The Pie Config file in internal storage is the source of truth ([ADR 0002](adr/0002-persistence-split.md)). It is decoded on start and after each import.
- An **Unavailable Item** (target app, activity or Shortcut missing or disabled) keeps its slot, greyed out with a generic icon. Releasing on it does nothing. It reactivates automatically when the target returns.
- References are **never** auto-deleted or edited.

### 9.5 Exports

Single files written via SAF (`CreateDocument`), no ZIP.

- **Inventory:** `{ exportedAt, apps: [{package, activity, label, default}], shortcuts: [{package, shortcutId, label, kind: pinned|manifest|dynamic, disabled?}] }`, each list sorted by label. `default` marks the activity an app item without `activity` launches. Example: [`spec/inventory.example.json`](../spec/inventory.example.json).
- **Current config:** a byte copy of the stored, normalized Pie Config file. Import and the Default Config write `$schema` into that file, pointing at `spec/pie-config.schema.json` at the release tag the app was built from (`main` for untagged builds), so an IDE offers autocomplete for the exported file.

### 9.6 Default Config

- Generated once on first start, then treated exactly like an imported file.
- One Base Layer `home`, colour `primary`. Items: default apps resolved via intents (dialer `ACTION_DIAL`, `CATEGORY_APP_MESSAGING`, `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`, `CATEGORY_APP_BROWSER`, Settings), then other launchable apps alphabetically by label until there are 8 items (fewer if the device has fewer apps).
- "Reset to Default Config" in Launcher Settings regenerates it.

## 10. Launcher Settings

Source: [What does the launcher settings screen hold?](https://github.com/jgoedde/pie-launcher-remake/issues/21).

### 10.1 Entry points

- Empty-space long-press menu → "Home settings".
- Its own Drawer Entry "Pie Launcher Settings" (a separate `LAUNCHER` activity), so settings stay reachable when the home screen is full or the Pie Config is broken. Being an app, it can also be a Pie Item.

### 10.2 Structure

One scrolling M3 Expressive screen with a `LargeTopAppBar` that collapses on scroll. Categories as headings; each category's `ListItem` rows sit in one grouped container in the Android 16/17 Settings style (first and last row more rounded, small gaps between rows).

1. **Default-home banner**: only when Pie Launcher is not the default home app. Highlighted card in `tertiaryContainer`; requests `RoleManager.ROLE_HOME`.
2. **Pie Config**:
   - Status row: "Imported 11 Nov 2026 · 4 Layers · 2 warnings" or "Default Config". Tapping it reopens the warnings of the last import plus current Unavailable Items.
   - Import Pie Config, Export Pie Config, Export Inventory.
   - Reset to Default Config (in `error` colour).
3. **Appearance**: link to "Wallpaper & style".
4. **Footer**: one small line: version (versionName + build) · GitHub link · Open-source licenses. Licenses open the only sub-page, a static screen built from `NOTICE` (no license library).

### 10.3 Confirmations (stock M3 `AlertDialog`)

- **Import:** after validation, a dialog shows the summary ("4 Layers, 31 Items · 2 warnings") and the warning list, buttons "Apply" / "Cancel". On errors the dialog lists the errors and has no "Apply".
- **Reset:** "Reset to Default Config?" with confirm "Reset" (`error` colour); the dismiss slot holds "Export first" and "Cancel". "Export first" opens the SAF export and keeps the dialog open afterwards.

### 10.4 Not in settings

No Hover Delay, gesture mappings, Drawer, widget, Page, icon theming or diagnostics/log-export options.

## 11. Architecture

Source: [What is the app architecture?](https://github.com/jgoedde/pie-launcher-remake/issues/10).

- **Modules** ([ADR 0001](adr/0001-pure-kotlin-core-module.md)):
  - `:core`, pure Kotlin/JVM: Pie geometry and hit-testing, the Pie gesture state machine, Layer navigation, Pie Config decoding and validation, App Drawer search ranking, and the widget domain in the `home` package (with the other Home Page logic):
    - **Widget Layout**: all widgets of a Page (Widget Zone, cell rect, span bounds) and each zone's grid. Operations add, drag over, drop, resize, remove and undo; each returns a new layout or a rejection ("no room", "too large", Pie Zone). Holds the reorder rules, cell bounds and hit-testing (7.3).
    - **Widget Catalog**: pure functions over provider facts: spans, the too-large flag, search, grouping by app, the size list (7.1, 7.4).
  - `:app`: Android, Compose UI and platform adapters, packaged by feature: `pie`, `home`, `drawer`, `widgets`, `icons`, `config`, `platform`. `widgets` holds the host, the host view wrapper, the Widget Picker and pin Activities, bind/configure, size reporting and the drag hand-off; it maps platform provider info to Widget Catalog facts.
- **State:** plain `ViewModel` + `StateFlow`, unidirectional, no MVI framework. The Pie gesture is not routed through a ViewModel: the `:core` state machine lives in the Compose pointer handler; the ViewModel only supplies the resolved Pie Config.
- **One source for apps and Shortcuts:** a repository in `platform` listens to `LauncherApps.Callback` and Shortcut changes and exposes one `StateFlow`. The App Drawer, Pie Item resolution (including Unavailable Items) and the Inventory export all read from it.
- **Persistence** ([ADR 0002](adr/0002-persistence-split.md)): Pie Config = JSON file in internal storage; Room = widget layout (Pages; per widget: id, provider `ComponentName`, Page index, zone, cell rect as one embedded value) and Drawer state (New Badge, pinned Shortcut records); Preferences DataStore = settings (Themed Icons, Icon Shape, last-import metadata).
- **DI:** Metro 1.x ([ADR 0004](adr/0004-metro-dependency-injection.md)).
- **Activities:** one portrait-only Home Activity (`MAIN` + `HOME` + `DEFAULT`), the Widget Picker Activity (ADR 0006), the widget-configure trampoline Activity, the pin Activity (invisible for `CONFIRM_PIN_SHORTCUT`, the pin sheet for widgets), and the Settings Activity (`LAUNCHER`). No navigation library: Drawer, popups and Edit Mode are state-driven overlays.
- **Permissions:** `QUERY_ALL_PACKAGES`, `EXPAND_STATUS_BAR`, plus the `<queries>` entry from 7.1.
- **Manifest:** `android:allowBackup="false"`, so auto-backup never restores a Room layout with stale widget ids. No backup/restore of widget ids in v1.
- **Tests:** JUnit in `:core` carries most testing; few unit tests in `:app`; no instrumented or screenshot tests in CI.

## 12. Build, CI and release

Sources: [What is the app architecture?](https://github.com/jgoedde/pie-launcher-remake/issues/10), [How are releases built and shipped?](https://github.com/jgoedde/pie-launcher-remake/issues/20).

- Gradle version catalog, current AGP, Kotlin 2.x. Build types debug and release only; no flavors, no `build-logic` convention plugins. No detekt.
- **CI** `.github/workflows/ci.yml`, one job `check`: `ktlintCheck lint testDebugUnitTest assembleDebug assembleRelease` on PRs and pushes to `main`. `assembleRelease` on CI is signed with the debug key (PRs have no secrets) so R8 breakage fails the PR. The debug APK is uploaded as a workflow artifact. The owner protects `main` with `check` as the only required status check.
- **Debug signing:** a fixed debug keystore committed to the repo (deliberately public). Debug build type: `applicationIdSuffix = ".debug"`, label "Pie Launcher (debug)", so it installs next to the release.
- **Release** `.github/workflows/release.yml`, triggered by a tag `vX.Y.Z`:
  - fails if the tagged commit is not on `main`;
  - reruns the `check` tasks, builds `assembleRelease`;
  - creates a **draft** GitHub Release with `pie-launcher-X.Y.Z.apk` (one universal APK, no ABI splits, no AAB) and `mapping.txt`, with `generate_release_notes: true`;
  - the owner smoke-tests the APK on the Pixel, then publishes the draft.
- **Versioning:** the tag is the single source of truth; the workflow passes it to Gradle. `versionName = X.Y.Z` (no pre-release suffixes), `versionCode = X*1_000_000 + Y*1_000 + Z`. Untagged builds: `0.0.0-dev`, versionCode 1. Releases start at `0.1.0`; `1.0.0` is tagged when v1 is complete.
- **Release signing:** keystore generated once by the owner; repo secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`; offline backup in a password manager. No local release signing.
- **R8:** release `isMinifyEnabled = true`, `isShrinkResources = true`, full mode. kotlinx.serialization's bundled rules cover config decoding; the `StatusBarManager` reflection needs no rule.
- No CHANGELOG file.

## 13. Licensing rules for agents

Source: [Which license does the project use?](https://github.com/jgoedde/pie-launcher-remake/issues/12).

- The project is Apache-2.0. Copyright holder in `NOTICE`: "Copyright 2026 Julian Gödde".
- Code may be ported only from Apache-2.0 or more permissive sources (AOSP, Lawnchair). A ported file keeps its original copyright header plus a "Modified by Pie Launcher" line, and its source is added to `NOTICE` with the first ported file.
- Expected ports: AOSP `MonochromeIconFactory`, the Launcher3 Icon Shape list, Launcher3 touch helpers.
- GPL sources (Kvaesitso and others) may be read for ideas and behaviour, never copied or closely paraphrased.
- Bundled assets (Material Symbols font) are listed in `NOTICE` too.

## 14. Onboarding

What the owner does to move from the current launcher to Pie Launcher. The build must make each step possible; nothing here needs an in-app wizard.

1. Install the APK from GitHub Releases.
2. Open "Pie Launcher Settings" from the current launcher. Tap the default-home banner and make Pie Launcher the home app.
3. The Default Config is generated on first start, so the Pie works immediately.
4. **Re-add every PWA in its browser** (Vanadium, Firefox: "Add to Home screen" / "Add app"). Pins made for the previous launcher are invisible to Pie Launcher. Vanadium gives each re-added PWA a new id; Firefox reuses the start URL.
5. Add widgets via the empty-space menu → "Widgets".
6. Export the Inventory, write the Pie Config by hand (the old Fly-Pie config is not migrated), import it.

Data-loss notes:

- **Clearing Pie Launcher's data** (or uninstalling it) wipes the Pie Config, the widget layout and all pinned Shortcuts. PWAs must then be re-added in their browsers. Export the Pie Config first.
- **Clearing a browser's data** drops its pinned PWAs; Pie Items pointing at them become Unavailable Items until re-added (Vanadium re-adds get new ids, so the config must be edited).
- `allowBackup="false"`: a reinstall or a new phone starts from scratch. Losing the release signing key forces a reinstall, with the same effect.

## 15. Known limitations

- Recents/Overview is always the system launcher's fallback Overview; changing it needs root.
- The bottom `mandatorySystemGestures` strip belongs to the system; the Pie Zone and swipe-up must start above it.
- The launcher's swipe-down opens the shade animated, not finger-tracking. A swipe-down starting in the status bar is the system's native tracking shade. If the hidden API breaks, swipe-down silently does nothing.
- Vanadium creates a duplicate Drawer Entry when the same PWA is added twice.
- A removed pinned Shortcut cannot be restored after the undo snackbar expires.
- No Pie editor: Pie changes always go through export → edit JSON → import.
- Main profile only; work profile and Private Space apps are not shown.
- Portrait only.

## 16. On-device acceptance checks

To verify on the owner's Pixel 6 (GrapheneOS, API 37) during the build:

- "Wallpaper & style" Style and Shape take effect (else add the launcher-side fallback settings, section 8.4).
- Widgets (section 7):
  - Widget Picker shows previews; tap-add and drag-to-place both work.
  - The bind dialog appears when the launcher is not allow-listed; generated previews appear once it is.
  - Long-press on a widget opens the popup and the Resize Frame together; moving starts a drag.
  - Scrollable widget lists scroll; horizontal Page swipes still work.
  - Reflow on move and on resize looks and feels like the stock launcher.
  - Remove with undo restores the widget intact.
  - Corners are rounded; no extra padding; `dumpsys appwidget` shows reported sizes equal to the cell size.
  - A pin sheet from a real app (e.g. a clock or notes app) adds the widget, by button and by drag.
  - The existing Analog clock test widget can be removed through the new UI.
- Pin Requests from Vanadium and Firefox land in the App Drawer.
- Pie motion, shapes and the numeric defaults in section 4 feel right; tune in code.
- Drawer search stays within the ~4 ms per keystroke budget.

## 17. Out of scope for v1

- Remote-loadable config (URL/Gist).
- Work profile / Private Space apps.
- Browser Actions (URL items opened in the default browser).
- Interactive Pie editor UI; "Add to Layer" on a Pin Request.
- Migration of the old Fly-Pie config.
- Hiding apps from the App Drawer.
- Double-tap gesture.
- Play Store / Obtainium distribution.
- App icons on non-Home Pages.
- Landscape layouts.
- Per-app / per-item icon override.
- Icon packs (e.g. Lawnicons).
- Widget-id backup/restore (`ACTION_APPWIDGET_HOST_RESTORED`).
- TalkBack operation of widgets: no accessibility custom actions for move, resize, remove or "Widget settings".
- Widget Picker tabs (Featured, Personal, Work), picker blur, a floating or two-pane picker.
- The +/− single-pointer resize buttons (Launcher3 `fix_widget_single_ptr_resize`).
- Folders, app icons on the Home Page, multi-select home editing.
- AccessibilityService shade fallback.

## 18. Gaps filled by this document

These points were not stated in any ticket. This document fills them with the obvious default. They can be changed without reopening the map:

- Releasing on a Layer Switch, or hovering a Shortcut item, does nothing beyond what section 4 says (no Progress Disc for Shortcut items or apps without Shortcuts).
- The Pie is disabled in Edit Mode.
- The Pie's Shortcut list shows the manifest and dynamic Shortcuts that `LauncherApps.getShortcuts` returns for the activity, in the order returned.
- **Edit Mode** lasts from a long-press on a widget until its move has ended and its Resize Frame has closed (`GLOSSARY.md`).
- Leaving Edit Mode with back or Home commits the current layout; it does not undo it.
- The cell gap (~16 dp) is a code constant, not derived from Launcher3's `DeviceProfile`.
- The imported file is stored normalized, so the config export can be the byte copy ADR 0002 describes and still be the normalized output the schema ticket asked for.
- Widget removal undo delays `deleteAppWidgetId` and the row delete until the snackbar expires, mirroring Shortcut removal.
- A tap-add from the App Drawer's filtered Widget Picker goes to the Home Page, upper Widget Zone first, as a Pin Request does.
- The Default Config fills up to 8 items with other launchable apps alphabetically.
- Material Symbols Rounded is bundled as a font with its codepoints file, for icon validation and drawing.
- Themed Icons and Icon Shape values are stored in Preferences DataStore.
