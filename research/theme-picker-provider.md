# Research: letting "Wallpaper & style" (ThemePicker) control themed icons and icon shape in Pie Launcher

Date: 2026-11-11. Question: what must a third-party launcher implement so the system
"Wallpaper & style" app (AOSP ThemePicker on top of WallpaperPicker2) can (a) toggle themed icons
and (b) pick the app icon shape, while leaving grid size out of it?

## Sources and snapshot

AOSP `main` on android.googlesource.com has been frozen since March 2025 (`refs/heads/main` ==
`master` == `805d10b`). Android 17 code is published only as release branches, so this research uses
**`android17-release`**:

| Repo | Ref | Commit |
|---|---|---|
| [platform/packages/apps/ThemePicker](https://android.googlesource.com/platform/packages/apps/ThemePicker/+/9b9023c7448f040ceffc1d0468022b8e39974361) | android17-release | `9b9023c` (2026-03-27) |
| [platform/packages/apps/WallpaperPicker2](https://android.googlesource.com/platform/packages/apps/WallpaperPicker2/+/41c4c1b61bb84259de8a192613a95cde9c2737df) | android17-release | `41c4c1b` |
| [platform/packages/apps/Launcher3](https://android.googlesource.com/platform/packages/apps/Launcher3/+/c612e6ece389f21c40f8cb9cd9a4b44239f00009) | android17-release | `c612e6e` |
| [GrapheneOS/platform_packages_apps_ThemePicker](https://github.com/GrapheneOS/platform_packages_apps_ThemePicker/tree/870a412786fad48d8bf2696483583b67871ec5d1) | `17` | `870a412` |
| [GrapheneOS/platform_packages_apps_Launcher3](https://github.com/GrapheneOS/platform_packages_apps_Launcher3/tree/0ca6a5b36620dd6a4f83b4162ef006889184e19e) | `17` | `0ca6a5b` |
| [GrapheneOS/platform_build](https://github.com/GrapheneOS/platform_build/tree/17) / [platform_build_release](https://github.com/GrapheneOS/platform_build_release/tree/6f3f4075803f96161d037b35c168d623bd2b0d4f) | `17` | `6f3f407` |
| [LawnchairLauncher/lawnchair](https://github.com/LawnchairLauncher/lawnchair/tree/f4b71266faeff9e77709349d52aa762547cd718f) | `16-dev` | `f4b7126` |
| [NeoApplications/Neo-Launcher](https://github.com/NeoApplications/Neo-Launcher) | `main` | (as of 2026-11-11) |

Labels: **[verified]** = read in the source at the refs above. **[inferred]** = follows from the
verified code but not run on a device. **[unverified]** = not confirmed.

Paths below are shortened: `TP/` = ThemePicker, `WP2/` = WallpaperPicker2, `L3/` = Launcher3.

---

## 1. How ThemePicker finds the launcher's provider

There are **two** different meta-data keys. Both are read from the **default HOME activity**:

| Feature | Meta-data key on the HOME `<activity>` | Defined in |
|---|---|---|
| Grid **and shape** (and home preview) | `com.android.launcher3.grid.control` | `WP2/res/values/strings.xml` `grid_control_metadata_name` |
| Themed icons ("Style" tab) | `com.android.launcher3.themedicon.option` | `TP/res/values/strings.xml:397` `themed_icon_metadata_key` |

Each value is a **content-provider authority**. Both can name the same provider (Launcher3 uses
`${packageName}.grid_control` for both on GrapheneOS). [verified]

Discovery code (`WP2/src/com/android/wallpaper/util/PreviewUtils.kt`, `homeAuthority()` + `init`): [verified]

1. `packageManager.resolveActivity(Intent(ACTION_MAIN).addCategory(CATEGORY_HOME), MATCH_DEFAULT_ONLY or GET_META_DATA)`,
   which gives the current default home.
2. `activityInfo.metaData.getString(key)` gives the authority. If it is missing, the feature is unavailable and only logged (`Couldn't resolve … from …`).
3. `packageManager.resolveContentProvider(authority, 0)`. If it is null, the feature is unavailable.
4. **The only check:** if the provider has a `readPermission`, the picker calls
   `checkSelfPermission(readPermission)`. If the picker doesn't hold that permission, the provider is dropped.

ThemePicker has **no** system-app, signature or package-allowlist check on the launcher. [verified]
(`DefaultShapeGridManager`, `ThemePickerIconStyleRepository` and the legacy `ThemedIconUtils` all use
this same logic.) The only package-specific string is `launcher_overlayable_package =
com.android.launcher3` (`TP/res_override/values/override.xml`). It is used only to load **grid** option
icons (`getGridOptionDrawable`), so it doesn't matter when grid is not offered. [verified]

Package visibility: the ThemePicker manifest declares `<queries><intent>MAIN + HOME</intent></queries>`
("used to query the launcher Activity for grid preview metadata"), so any HOME app, and its
provider, is visible to it. [verified in GrapheneOS `TP/AndroidManifest.xml`; package-wide visibility
through an intent match is standard Android 11+ behaviour, inferred]

Caching pitfall: `previewUtils` is cached in a `@Singleton` (`DefaultShapeGridManager`,
`ThemePickerIconStyleRepository`) once `supportsPreview()` is true. It is never rebuilt, so if the user
changes the default launcher while the picker process is alive, the picker keeps talking to the old
launcher until the process dies. [verified code / inferred effect]

## 2. Contract

Provider: `exported="true"`. The authority is the meta-data value. All URIs are
`content://<authority>/<path>`, and the launcher sees `uri.path` with a leading `/`.

### 2a. Themed icons only

Needs meta-data `com.android.launcher3.themedicon.option` → authority. [verified]

| Operation | URI path | Details |
|---|---|---|
| query | `icon_themed` (Launcher3 also accepts `get_icon_themed`) | Cursor with one row, column `boolean_value` (int `1`/`0`). The picker reads `getInt(getColumnIndex("boolean_value")) == 1`. |
| update | `icon_themed` (Launcher3 also accepts `set_icon_themed`) | `ContentValues{"boolean_value": Boolean}`. **Must return `> 0`.** `setIconStyle` returns `rowsUpdated > 0`, otherwise it logs "Apply unsuccessful". |
| notifyChange | `icon_themed` (picker also observes `set_icon_themed`, notifyForDescendants) | Required. After apply, the picker waits for `selectedIconStyle` to emit a new value (`drop(1).take(1)` with `ICON_UPDATE_TIMEOUT`). Without the notify, the apply stalls until the timeout. Launcher3 calls `notifyChange(uri, null)` after every successful update. |

Sources: `TP/.../icon/data/repository/ThemePickerIconStyleRepository.kt`, `TP/.../icon/ui/viewmodel/AppIconPickerViewModel.kt`
(`iconStyleAndShapeOnApply`), `L3/src/com/android/launcher3/graphics/GridCustomizationsProxy.java`. [verified]

**UI on Android 17:** the `extendible_theme_manager` flag is ENABLED (see §3). With it, the old
"Themed icons" switch becomes an App icons → **Style** tab with options **Default** / **Minimal**
(`ThemePickerIconStyle.DEFAULT` / `MONOCHROME`, strings `app_icons_style_default` and
`app_icons_style_minimal`). Minimal is listed only when the themed-icon provider resolves. The tab
needs more than one style, so it appears exactly when the provider is found. [verified]

**Gotcha: themed-icon apply also depends on a shape row.** `iconStyleAndShapeOnApply` is a
`combine(…, selectedShape, …)` where `selectedShape = selectedShapeOption.filterNotNull()`. If
`shape_options` is missing, empty, or has no single `is_default` row, `selectedShape` never emits.
The combine then never produces an apply action, so **Apply does nothing for the Style tab either**.
[inferred from verified code] Mitigation: also declare `grid.control` and return at least one valid
shape row (a single row hides the Shape tab, see below).

The legacy, non-extendible path (`shapeAndThemedIconOnApply`, `ThemedIconSwitchProvider`) uses the same
URIs and columns. It waits for the update **without** a timeout, so a missing notify would hang it.
This path is inactive on Android 17 builds. [verified]

### 2b. Icon shape only

Needs meta-data `com.android.launcher3.grid.control` → authority. [verified]

| Operation | URI path | Details |
|---|---|---|
| query | `shape_options` | Columns `shape_key` (String), `shape_title` (String), `path` (String), `is_default` (read as **String**, then `.toBoolean()`; Launcher3 adds a Java boolean to the `MatrixCursor`, which becomes `"true"`/`"false"`). **Exactly one** row must have `is_default=true`, otherwise the whole list is discarded. |
| update | `shape` | `ContentValues{"shape_key": String}`. The return value is ignored by `applyShapeOption`. Launcher3 returns 1. |
| notifyChange | `shape` (observed with notifyForDescendants) | Triggers a re-query of `shape_options`. |

The Shape tab is shown only if `shape_options` has **more than one** row
(`ShapeRepository.isShapeOptionsAvailable = available && getShapeOptions().size > 1`). [verified]

`path` is SVG path data in a **100×100 viewport**, parsed with `androidx.core.graphics.PathParser`
(`TP/.../icon/ui/view/ShapeTileDrawable.kt`). Launcher3 examples: `ShapesProvider.kt` keys `circle`,
`square`, `four_sided_cookie`, `seven_sided_cookie`, `arch`. Circle is
`M50 0A50 50,0,1,1,50 100A50 50,0,1,1,50 0`. [verified]

**The shape is a launcher setting, not a system one.** On Android 16+ the shape list comes from the
launcher's provider and the choice is stored in the launcher (`L3` `GridCustomizationsProxy`: `SET_SHAPE`
→ `mPrefs.put(PREF_ICON_SHAPE, shape_key)`). The launcher receives only the **key**. It owns the path
for that key and must apply it to its own icon rendering. ThemePicker still contains the old overlay
category `android.theme.customization.adaptive_icon_shape` (`ResourceConstants.java`), but the
Android 17 shape picker (`ShapeRepository` → `DefaultShapeGridManager`) doesn't use it.
`ShapeRepository.defaultShapePath` reads the system `config_icon_mask` only as a default
value. [verified]

### 2c. Grid and preview (not wanted): what happens if they are unsupported

- `list_options` returns null or an empty cursor → `emptyList()` → `isGridCustomizationAvailable = false`
  → the Grid section is hidden. A single grid row hides it too (it needs `size > 1`). Malformed rows
  are caught and logged. [verified, `DefaultShapeGridManager`, `GridRepository2`]
- `call("get_preview")` returns null → `WorkspacePreviewBinder` logs "Result bundle from rendering
  preview is null." and shows the wallpaper with no workspace overlay. No crash.
  [verified, `WP2/.../common/preview/ui/binder/WorkspacePreviewBinder.kt`]
- Once `grid.control` resolves, the picker *will* call `get_preview` (with display id, width, height
  and a SurfaceView host token). It also sends preview-update `Message`s (`MESSAGE_ID_UPDATE_SHAPE` with
  `shape_key`, `MESSAGE_ID_UPDATE_ICON_THEMED` with `boolean_value`, `MESSAGE_ID_UPDATE_COMMAND` with
  `update_method`) to the callback that `get_preview` returns. With a null preview there is no
  callback and nothing to update. The user just won't see a live preview of shape or Minimal
  before applying. [verified code / inferred UX]
- Other paths the picker may hit, all safe to answer with `null`/`0`:
  `workspace_items_label_hidden` (query), `set_workspace_items_label_hidden` (update). A null cursor
  means "show labels". [verified]

Minimal provider for Pie Launcher: `shape_options`, `shape`, `icon_themed`, `get_icon_themed`,
`set_icon_themed`. Return `null` for `list_options`, `default_grid`, `get_preview`, and anything else.

## 3. GrapheneOS (Android 17, branch `17`)

- **ThemePicker is shipped.** `GrapheneOS/platform_build` `target/product/handheld_product.mk:41` lists
  `ThemePicker` and `ThemesStub`. The GrapheneOS ThemePicker fork has only 4 small commits on top of
  AOSP (GrapheneOS camera and app icons for previews, colour bundles, strings), and none touch the
  provider logic. [verified]
- **Flags in the Android 17 release config** (`cp1a` inherits `bp4a` → `bp3a` → …, in
  `platform_build_release`): `com.android.launcher3.enable_launcher_icon_shapes` ENABLED (bp4a),
  `com.android.systemui.shared.new_customization_picker_ui` ENABLED (bp3a),
  `com.android.systemui.shared.extendible_theme_manager` ENABLED (cp1a). So Android 17 GrapheneOS uses
  the new picker with **Style (Default/Minimal)** and **Shape** tabs. [verified config; that the
  device build uses `cp1a`/`cp2a` is inferred from naming]
- **GrapheneOS Launcher3 declares both keys.** `quickstep/AndroidManifest-launcher.xml:66-71` has
  `grid.control` and `themedicon.option` → `${packageName}.grid_control`, added by GrapheneOS commit
  [`94cf861` "expose themed icon provider" (2022)](https://github.com/GrapheneOS/platform_packages_apps_Launcher3/commit/94cf8616cec45532d80c97948d1494cd8a271a7f).
  AOSP Launcher3 (android14/15/16/17-release) declares **only** `grid.control`. On stock AOSP the
  Style tab therefore appears only with a launcher, such as Pixel's, that adds the themed-icon
  key. [verified]
- I did not see it on a device. The exact on-screen labels on a Pixel 6 with GrapheneOS 17 are
  [unverified].

## 4. Prior art in third-party launchers

- **Lawnchair** (`16-dev`): inherits the Launcher3 `LauncherCustomizationProvider` and
  `GridCustomizationsProxy`, including `icon_themed` and `shape`. Its HOME activity manifest
  (`quickstep/AndroidManifest-launcher.xml`, merged by `build.gradle`) declares **only `grid.control`**
  and no `themedicon.option`. So the picker could show Grid and Shape for Lawnchair, but not the
  themed-icon Style tab. Lawnchair's own themed icons come from icon-pack and `grayscale_icon_map`
  sources, not the system toggle. [verified manifest / inferred picker result]
- **Neo Launcher** (`main`): `Omega/AndroidManifest.xml` declares **both** `grid.control` and
  `themedicon.option` → `${packageName}.grid_control`. Its provider in `AndroidManifest-common.xml` sets
  `android:permission="android.permission.BIND_WALLPAPER"`, and the code includes Launcher3's
  `GridCustomizationsProxy` with `icon_themed`. This is the closest working pattern. [verified source;
  that the picker shows it on a device is unverified]
- **Kvaesitso**: GitHub code search finds neither key. [verified absence by search]
- I found no public report that confirms the system picker showing the toggle for any third-party
  launcher. [unverified]

Pitfalls:

1. Without `themedicon.option`, there is no Style tab (the Lawnchair case).
2. Without a valid `shape_options` row (exactly one `is_default`), the Style tab's Apply may never
   fire (§2a).
3. Each update must return `>0` and call `notifyChange` on the updated URI. Otherwise Apply looks
   unsuccessful or waits for the timeout.
4. The meta-data must sit on the activity that is the **resolved default HOME** (the one with the HOME
   intent filter), not on `<application>`.
5. If the launcher changes the setting in its own UI, it should also `notifyChange` on `icon_themed` and
   `shape` so an open picker updates.
6. `is_default` is read as a string: use the strings `"true"`/`"false"` or a boolean value, not `1`/`0`.
7. The picker caches the provider in a singleton, so after a launcher switch the user may need to restart the picker.

## 5. Protection for update calls

- AOSP Launcher3 leaves the manifest provider permission-less. Its comment says the caller should
  hold `android.permission.BIND_WALLPAPER` or `${applicationId}.permission.GRID_CONTROL`. The check
  is enforced in code: `LauncherCustomizationProvider.enforceCallerPermission()` accepts
  `checkCallingPermission(BIND_WALLPAPER)`, then `GRID_CONTROL` (declared `signature|knownSigner`
  with `@array/grid_control_known_signers`), then a temporary SHA-256 signer allowlist. Anything else
  gets a `SecurityException`. [verified]
- **ThemePicker holds `android.permission.BIND_WALLPAPER`** (`TP/AndroidManifest.xml`; WallpaperPicker2
  too). It is a privileged system-ext app (`Android.bp`: `privileged: true`, `system_ext_specific`).
  `BIND_WALLPAPER` is `signature|privileged` (`frameworks/base/core/res/AndroidManifest.xml`,
  android16-release), so ordinary apps cannot get it. It does **not** hold
  `com.android.launcher3.permission.READ_SETTINGS`, and nothing in the picker requires that. [verified]
- **Recommendation:** protect the whole provider with `android:permission="android.permission.BIND_WALLPAPER"`
  (the Neo Launcher pattern), or at least `readPermission` plus `writePermission`. This works because
  `PreviewUtils` checks `readPermission` against the picker's own grants, and the picker holds
  `BIND_WALLPAPER`. An unprotected exported provider would let any app flip themed icons or the
  shape. A third-party launcher can't use the `knownSigner` route for the platform-signed picker in
  a portable way, because signing keys differ per ROM. [inferred]
