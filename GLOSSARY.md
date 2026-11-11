# Pie Launcher

An Android home-screen launcher where apps are opened almost exclusively through a radial pie menu instead of a static icon grid.

## Home screen

**Pie Zone**:
The widget-free area in the lower-middle of the Home Page, placed for easy thumb reach, where a touch-down summons the Pie.
_Avoid_: Reserved zone, trigger area

**Widget Zone**:
An area of a Page that holds widgets on a cell grid: the Home Page has two, above and below the Pie Zone; every other Page is one Widget Zone filling its height.
_Avoid_: Reserved zone

**Page**:
One horizontally swipeable home-screen screen.
_Avoid_: Screen, workspace

**Home Page**:
The leftmost Page, which cannot be removed and is the only Page with a Pie Zone; pressing Home returns to it.
_Avoid_: Default page, main screen

**App Drawer**:
The searchable, alphabetical list of all launchable apps and pinned Shortcuts, as known from standard launchers.
_Avoid_: App grid, all-apps

**Drawer Entry**:
One row in the App Drawer: either a launchable app or a pinned Shortcut.
_Avoid_: Drawer item, tile

**New Badge**:
The marker on a Drawer Entry that was recently added and has not been launched yet.
_Avoid_: Unread dot, highlight

## Pie

**Pie**:
The radial menu that appears under the finger on touch-down in the Pie Zone; dragging to a Pie Item and releasing activates it.
_Avoid_: Wheel, radial menu

**Layer**:
A named, colored set of Pie Items shown together in the Pie; one Layer is the Base Layer shown when the Pie opens.
_Avoid_: Category, folder, level, sub-pie

**Base Layer**:
The Layer the Pie shows when it opens.
_Avoid_: Home layer, root layer

**Pie Item**:
One selectable entry in a Layer.
_Avoid_: Slot, entry

**Layer Switch**:
A Pie Item that, when hovered for the Hover Delay, replaces the current Layer with its target Layer.
_Avoid_: Layer link

**Inner Circle**:
The circle just inside the ring of Pie Items; once the finger is outside it, only the finger's direction from the Pie's centre decides which Pie Item is hovered.
_Avoid_: Dead zone, band

**Progress Disc**:
The filling disc inside the Inner Circle that shows how much of the Hover Delay has elapsed.
_Avoid_: Loading indicator, progress ring

**Hover Delay**:
The short dwell time a finger must rest on a Pie Item before a Layer Switch fires or an app's Shortcuts open.
_Avoid_: Throttle, debounce

**Unavailable Item**:
A Pie Item whose target app or Shortcut is currently missing from the device; it keeps its place in the Layer, shown greyed out, and becomes usable again when the target returns.
_Avoid_: Dangling item, broken item, dead link

**Shortcut**:
An app-provided action (static, dynamic or pinned) that launches a specific part of an app; pinned Shortcuts include browser-installed PWAs.
_Avoid_: Deep link, quick action

**Pin Request**:
An app's request to add one of its Shortcuts to the launcher; Pie Launcher always accepts it, adding the Shortcut to the App Drawer and the Inventory but never to a Layer.
_Avoid_: Add to home screen, install request

## Icons

**Themed Icon**:
An app or Shortcut icon drawn as a monochrome mask tinted with Monet colours on a Monet background; the mask is the icon's own monochrome layer, else a Generated Monochrome.
_Avoid_: Material You icon, mono icon

**Generated Monochrome**:
A monochrome mask computed from a full-colour icon with AOSP's luminance algorithm, for icons that ship no monochrome layer.
_Avoid_: Auto-icon, forced mono

**Original Icon**:
An icon shown in its own colours, untinted; used for Shortcuts whose icon is a bitmap (e.g. contact photos), except PWAs.
_Avoid_: Raw icon, colour icon

**PWA**:
A pinned Shortcut owned by a browser package (an app that handles `https` `VIEW` intents as `BROWSABLE`); always a Themed Icon, even though its favicon is a bitmap.
_Avoid_: Web app, WebAPK

## Configuration

**Pie Config**:
The JSON document that defines all Layers, their Pie Items and which Layer is the Base Layer; authored by the user and imported as a file, or the Default Config until the first import.
_Avoid_: Settings, layer config

**Default Config**:
The Pie Config the launcher generates on first start, a single Base Layer of common apps, so the Pie works before anything is imported.
_Avoid_: Fallback config, starter config

**Inventory**:
A launcher-exported listing of every launchable app and Shortcut with the identifiers a Pie Config can reference.
_Avoid_: App list, catalog
