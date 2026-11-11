# Pie Launcher

An Android home-screen launcher where apps are opened almost exclusively through a radial pie menu instead of a static icon grid.

## Home screen

**Pie Zone**:
The widget-free area in the lower-middle of the home screen, placed for easy thumb reach, where a touch-down summons the Pie.
_Avoid_: Reserved zone, trigger area

**Widget Zone**:
One of the two areas above and below the Pie Zone that hold widgets and other conventional launcher content.
_Avoid_: Reserved zone

**Page**:
One horizontally swipeable home-screen screen; every Page has the Pie Zone in the same position.
_Avoid_: Screen, workspace

**App Drawer**:
The searchable list of all launchable apps, as known from standard launchers.
_Avoid_: App grid, all-apps

## Pie

**Pie**:
The radial menu that appears under the finger on touch-down in the Pie Zone; dragging to a Pie Item and releasing activates it.
_Avoid_: Wheel, radial menu

**Layer**:
A named, colored set of Pie Items shown together in the Pie; one Layer is the Base Layer shown when the Pie opens.
_Avoid_: Category, folder, level

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

**Shortcut**:
An app-provided action (static, dynamic or pinned) that launches a specific part of an app; pinned Shortcuts include browser-installed PWAs.
_Avoid_: Deep link, quick action

## Configuration

**Pie Config**:
The user-authored JSON document that defines all Layers and their Pie Items; imported as a file.
_Avoid_: Settings, layer config

**Inventory**:
A launcher-exported listing of every launchable app and Shortcut with the identifiers a Pie Config can reference.
_Avoid_: App list, catalog
