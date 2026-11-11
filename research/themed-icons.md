# Research: Material You themed icons for every app

Ticket: [#4](https://github.com/jgoedde/pie-launcher-remake/issues/4) · Map: [#1](https://github.com/jgoedde/pie-launcher-remake/issues/1) · Researched 2026-11-11

Tags used below: **[verified]** means read in primary source (docs or code at the pinned commit). **[unverified]** means inferred or not checked on a device. Fix or confirm these during the prototype.

## TL;DR / recommendation

1. **Themed icon = alpha mask + two Monet colors.** Draw a solid background in the "themed background" color, then the app's monochrome layer tinted with the "themed foreground" color, all clipped to the icon shape. This is exactly what AOSP Launcher3 does ([`MonoIconThemeController.createThemedAdaptiveIcon`](#aosp-launcher3--iconloaderlib)). [verified]
2. **Mask source, in priority order:**
   1. `AdaptiveIconDrawable.getMonochrome()` (API 33+), when the app ships a `<monochrome>` layer. [verified]
   2. Otherwise, **auto-generate one with AOSP's `MonochromeIconFactory` algorithm**: flatten bg+fg → luminance-as-alpha → min/max contrast stretch → auto-invert if the edges are "on" → S-curve contrast boost. This is what Lawnchair's "force monochrome" does: it just turns on AOSP's code path. [verified]
   3. Non-adaptive (legacy bitmap) icons: wrap them into an `AdaptiveIconDrawable` first (AOSP `BaseIconFactory.wrapToAdaptiveIcon`), then apply step 2. [verified]
3. **Cache the mask, not the colored icon.** Store a per-component `ALPHA_8` mask, keyed by package `sourceDir`, locale and SDK. Tint at draw time with the current Monet colors. A wallpaper or theme change then costs no regeneration, only a recolor. This is the AOSP design: the `mono_icon` BLOB column holds the raw ALPHA_8 bytes, and the system-state key has no colors in it. [verified]
4. **Shortcuts go through the same pipeline.** `LauncherApps.getShortcutIconDrawable()` → if it is adaptive with a monochrome layer, use that. Otherwise auto-generate, **except for file- or URI-backed shortcut icons**, which Launcher3/Lawnchair skip (photos, favicons). [verified in code; behavior for PWAs unverified]
5. **Licensing:** the AOSP iconloaderlib code (Apache-2.0) can be copied or ported, keeping its headers and NOTICE. Do **not** copy Kvaesitso code (GPL-3.0) unless the project goes GPL-3.0. Pie Launcher has no LICENSE yet, so pick one first.

Note: since **Android 16 QPR2** the platform docs say "Android automatically themes app icons for apps that don't provide their own" ([adaptive icons guide](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)). [verified quote] The AOSP code shows this auto-theming lives **inside the launcher** (Launcher3 aconfig flag `force_monochrome_app_icons` → `MonochromeIconFactory`), not in `PackageManager`. So a third-party launcher most likely still gets `getMonochrome() == null` for such apps and must generate the mask itself. [unverified on a device: confirm on Android 17 by logging `getMonochrome()` for an app known to lack a monochrome layer]

## 1. Official path

### Platform API
- `AdaptiveIconDrawable` (API 26): foreground and background layers, 108×108 dp each. The inner 72 dp is visible and the outer 18 dp is reserved; `getExtraInsetFraction()` gives that inset. [verified, [reference](https://developer.android.com/reference/android/graphics/drawable/AdaptiveIconDrawable)]
- `getMonochrome()` (API 33): "Returns the monochrome version of this drawable. Callers can use a tinted version of this drawable instead of the original drawable on surfaces stressing user theming." Nullable. Constructor `AdaptiveIconDrawable(bg, fg, monochrome)` (API 33). `getIconMask()` returns the device's shape `Path`. [verified]
- Guide: apps opt in by adding `<monochrome android:drawable=…/>` inside `<adaptive-icon>`. "Starting with Android 16 QPR 2, Android automatically themes app icons for apps that don't provide their own." [verified, [guide](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)]
- Getting the icon: `LauncherApps.getActivityList(pkg, user)` → `LauncherActivityInfo.getIcon(density)` (Launcher3 also uses `IconProvider.getIcon(info, iconDpi)`, which handles dynamic clock and calendar icons).

### Monet colors used by Launcher3
From iconloaderlib `res/values-v31/colors.xml` and `res/values-night-v31/colors.xml` (AOSP main) [verified]:

| | foreground (`themed_icon_color`) | background (`themed_icon_background_color`) |
|---|---|---|
| light | `@android:color/system_accent1_700` | `@android:color/system_accent1_100` |
| dark | `@android:color/system_accent1_200` | `@android:color/system_accent2_800` |

These system palette colors exist since API 31. In Compose, use the same resource ids via `colorResource(android.R.color.system_accent1_700)`. You could also derive from `dynamicLightColorScheme`/`dynamicDarkColorScheme` roles (e.g. primary / primaryContainer). That is close but **not identical** to Pixel. Use the raw resource ids to match the system.

### How Launcher3 renders it
`MonoIconThemeController.createThemedAdaptiveIcon()` builds `AdaptiveIconDrawable(ColorDrawable(bg), monochrome.setTint(fg))`. For a generated mask it uses `InsetDrawable(BitmapDrawable(mask).colorFilter = BlendModeColorFilter(fg, SRC_IN), extraInset/2)` instead. `ClippedMonoDrawable` insets by `-extraInsetFraction` and clips to `iconMask`. In the bitmap cache path, `createThemedBitmap()` renders the mono drawable with `BaseIconFactory.MODE_ALPHA` into an ALPHA_8 bitmap, scaled by the normalizer's scale for a full-bleed adaptive icon. [verified]

Compose port: draw a `Path`-clipped shape filled with `bg`, then `drawImage(maskBitmap, colorFilter = ColorFilter.tint(fg, BlendMode.SrcIn))`. Kvaesitso does it this way (`PorterDuffColorFilter(tint, SRC_IN)` in `ShapedLauncherIcon.kt`). [verified]

## 2. Fallback: auto-generating a monochrome layer

### Option A: AOSP `MonochromeIconFactory` (recommended; Lawnchair uses it)
Source: `iconloaderlib/src/com/android/launcher3/icons/MonochromeIconFactory.java` [verified]. Algorithm:
1. Work bitmap size = `iconBitmapSize * 2 / (1 + 2*extraInset)`, ARGB_8888 canvas.
2. Fill black, draw `background` then `foreground` (full 108 dp layers, unmasked).
3. Draw into an `ALPHA_8` bitmap through a `ColorMatrix` that sets **alpha = (R+G+B)/3** (luminance-as-alpha).
4. Find min/max alpha and stretch linearly to 0..255.
5. **Auto-invert:** average the alpha of the top and bottom edge bands (the region outside the visible viewport, i.e. the background). If it maps above 0.5, invert, so a light background with a dark glyph becomes a glyph mask.
6. Contrast S-curve: values above 128 are pushed toward 255 and values below toward 0.
7. Wrap in `ClippedMonoDrawable` (clip to icon shape).

Trade-offs:
- + Pure public API (`Canvas`, `ColorMatrix`, `ALPHA_8`, `getExtraInsetFraction`, `getIconMask`). Portable to an app; `@TargetApi(33)`.
- + Keeps internal detail (anti-aliased gradients, multi-part logos), and it is what Pixel users already see, so it looks "native".
- − Luminance-based, so it fails on **mid-tone logo on mid-tone background** (low contrast → muddy mask) and on multi-color logos with similar luminance (e.g. Google-style 4-color glyphs become flat blobs). Photographic or gradient icons give noisy masks.
- − Edge-band heuristic misjudges icons whose background is not uniform.
- Cost: one software draw at ~2× icon size plus two O(n) passes over ~(2·192)²≈150k bytes for a 192 px icon. Expected a few ms per icon on a worker thread. [unverified timing; measure in prototype]

### Option B: foreground silhouette (Kvaesitso "force themed")
`ForceThemedIconTransformation` replaces the background with a color layer and draws the **foreground layer tinted SRC_IN** at `scale/1.2`. The foreground's alpha channel becomes the mask. [verified, `services/icons/.../transformations/ForceThemedIconTransformation.kt`]
- + Trivial and fast. Great when the foreground is a clean glyph on a transparent layer (most modern adaptive icons).
- − Any detail drawn *inside* the foreground (colored regions, inner strokes) is lost, so you get a solid blob. Fails for icons whose logo lives in the background layer or whose foreground is full-bleed. For legacy wrapped icons it gives a filled square or circle.

### Option C: hybrid (suggested for prototype)
Use B when the foreground layer has meaningful transparency and the luminance spread inside its opaque area is low (single-color glyph). Use A otherwise. Optionally a per-app override list (user picks A/B/original). [design idea, unverified quality]

### Option D: icon-pack themed sources (Lawnicons / Arcticons)
Lawnchair can read a pack's `res/xml/grayscale_icon_map` (Lawnicons format) as hand-drawn monochrome masks, before falling back to auto-gen (`LawnchairIconProvider.getThemedIconMap`, `resolveThemedIcon`). Lawnicons is a separate app the user installs. Highest quality, but it adds an icon-pack dependency and parsing. Treat it as a later feature. [verified in code]

## 3. Shortcut icons

- `LauncherApps.getShortcutIconDrawable(ShortcutInfo, density)` returns a `Drawable`. Static (manifest) shortcuts declared with an `<adaptive-icon>` resource can carry a `<monochrome>` layer. Dynamic and pinned shortcuts are usually built with `Icon.createWithAdaptiveBitmap()`, a bitmap-backed adaptive icon with **no monochrome layer**. [docs verified; the "usually" is unverified]
- Launcher3 sends shortcut icons through the **same** `createBadgedIconBitmap` → `IconThemeController.createThemedBitmap` path as apps (`CacheableShortcutCachingLogic.loadIcon`). It passes `isFileDrawable = shortcutInfo.hasIconFile() || hasIconUri()`. Lawnchair's fork **skips forced mono generation for file/URI icons** (`getMonochromeDrawable(... isFileDrawable)`). [verified] `hasIconFile`/`hasIconUri` are `@hide`. A third-party launcher can't call them [unverified whether there's a public equivalent], so we need our own heuristic or a per-item opt-out.
- Kvaesitso: for shortcuts, it uses `icon.monochrome` when themed and present. Otherwise it shows the original fg/bg (no forced theming of shortcuts). [verified, `data/appshortcuts/.../LauncherShortcut.kt`]
- **Implication for Pie Launcher:** shortcuts can be themed the same way. For PWA pinned shortcuts (Vanadium/Firefox favicons) the auto-mask is often poor. Decide between theming them (option A/B), badging the original favicon, or using the parent browser's themed icon plus a small favicon. [open question]
- Shortcut cache freshness in Launcher3: manifest shortcuts → app state only; dynamic/pinned → `lastChangedTimestamp` + app state. [verified]

## 4. Caching strategy

What AOSP does (`iconloaderlib/.../cache/BaseIconCache.kt`, `IconProvider.java`) [verified]:
- SQLite table `icons(componentName, profileId, freshnessId, icon BLOB, mono_icon BLOB, icon_color, flags, label)`, PK (component, user).
- `freshnessId = IconProvider.getStateForApp(appInfo)` = `systemState + appInfo.sourceDir` (the sourceDir hash changes on every install or update), plus the day for the calendar app. `systemState = locales + "," + SDK_INT`. Lawnchair appends its own icon-setting prefs (`LawnchairIconProvider.getStateForApp`).
- `mono_icon` stores the raw ALPHA_8 bytes (`width*height`). Decode copies them into an ALPHA_8 bitmap, then `.copy(HARDWARE)`.
- **No Monet colors in the key.** Colors are applied at draw time, so wallpaper changes never invalidate the DB.
- Icon-size or DPI change → `updateIconParams` clears and rebuilds.
- Clock/calendar icons are dynamic (`ClockDrawableWrapper`, date-change broadcast via `IconProvider.registerIconChangeListener`).

Kvaesitso, for contrast: an in-memory `LruCache<String, LauncherIcon>(200)` only, evicted on settings changes. [verified]

Recommendation for Pie Launcher (small app count, personal use):
- **Memory:** `LruCache` of ImageBitmap masks + full-color bitmaps keyed by `(component|shortcutId, user)`.
- **Disk:** Room/SQLite or plain files in `cacheDir`: `{key, freshness, colorBitmap PNG/raw, monoMask ALPHA_8 raw, maskSource(app|generated|none)}`. Freshness = `sourceDir + lastUpdateTime + versionCode + SDK_INT + iconSizePx + algorithmVersion`. Include `algorithmVersion` so tuning the generator invalidates old masks.
- **Invalidation:** `LauncherApps.Callback` (`onPackageChanged/Added/Removed`, `onShortcutsChanged`). Monet colors come from resources: on Android 12+ a wallpaper color change applies a new overlay, which triggers a configuration change and recreates the activity [unverified detail]. Reading colors in Compose from `colorResource` / `LocalContext` resources recolors automatically. No regeneration is needed. Optionally `WallpaperManager.addOnColorsChangedListener`.
- **Startup:** load masks from disk synchronously for the Pie's Base Layer items, and generate missing ones on `Dispatchers.Default` in the background.

## 5. Reference code locations

AOSP (Apache-2.0), `platform/frameworks/libs/systemui`, branch main:
- [`MonochromeIconFactory.java`](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/src/com/android/launcher3/icons/MonochromeIconFactory.java): generation algorithm
- [`mono/MonoIconThemeController.kt`](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/src/com/android/launcher3/icons/mono/MonoIconThemeController.kt): mask selection, render, decode, `createThemedAdaptiveIcon`
- [`mono/ThemedIconDrawable.kt`](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/src/com/android/launcher3/icons/mono/ThemedIconDrawable.kt): `getColors()`
- [`res/values-v31/colors.xml`](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/res/values-v31/colors.xml), [`res/values-night-v31/colors.xml`](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/res/values-night-v31/colors.xml)
- [`BaseIconFactory.java`](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/src/com/android/launcher3/icons/BaseIconFactory.java): `createBadgedIconBitmap`, `wrapToAdaptiveIcon`, `IconNormalizer` use
- [`IconProvider.java`](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/src/com/android/launcher3/icons/IconProvider.java): `getStateForApp`, `updateSystemState`
- [`cache/BaseIconCache.kt`](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/src/com/android/launcher3/icons/cache/BaseIconCache.kt): SQLite schema and freshness
- Launcher3 aconfig flag `force_monochrome_app_icons` ("Enable the ability to generate monochromatic icons, if it is not provided by the app", bug 270396209), as mirrored in [Lawnchair `aconfig/launcher.aconfig`](https://github.com/LawnchairLauncher/lawnchair/blob/6c592e79246de0e383a2e69ef332730e5ae1ce29/aconfig/launcher.aconfig)

Lawnchair (Apache-2.0), [`LawnchairLauncher/lawnchair@6c592e7`](https://github.com/LawnchairLauncher/lawnchair/tree/6c592e79246de0e383a2e69ef332730e5ae1ce29) (branch 16-dev) with the submodule [`LawnchairLauncher/platform_frameworks_libs_systemui@233c33f`](https://github.com/LawnchairLauncher/platform_frameworks_libs_systemui/tree/233c33f3934cb364d997f42d989785d51d09530a):
- [`lawnchair/src/app/lawnchair/icons/LawnchairThemeManager.kt`](https://github.com/LawnchairLauncher/lawnchair/blob/6c592e79246de0e383a2e69ef332730e5ae1ce29/lawnchair/src/app/lawnchair/icons/LawnchairThemeManager.kt): picks `MonoIconThemeController(shouldForceThemeIcon = true)` when "force monochrome" is on
- [`lawnchair/src/app/lawnchair/icons/LawnchairIconProvider.kt`](https://github.com/LawnchairLauncher/lawnchair/blob/6c592e79246de0e383a2e69ef332730e5ae1ce29/lawnchair/src/app/lawnchair/icons/LawnchairIconProvider.kt): icon packs, `grayscale_icon_map`, cache-state key
- [`lawnchair/src/app/lawnchair/icons/ThemedIconCompat.kt`](https://github.com/LawnchairLauncher/lawnchair/blob/6c592e79246de0e383a2e69ef332730e5ae1ce29/lawnchair/src/app/lawnchair/icons/ThemedIconCompat.kt): parses `<monochrome>` from manifest XML (compat for older APIs; not needed on 17+)
- [`src/com/android/launcher3/icons/CacheableShortcutInfo.kt`](https://github.com/LawnchairLauncher/lawnchair/blob/6c592e79246de0e383a2e69ef332730e5ae1ce29/src/com/android/launcher3/icons/CacheableShortcutInfo.kt): shortcut icon load and freshness
- [`quickstep/.../uioverrides/SystemApiWrapper.kt`](https://github.com/LawnchairLauncher/lawnchair/blob/6c592e79246de0e383a2e69ef332730e5ae1ce29/quickstep/src/com/android/launcher3/uioverrides/SystemApiWrapper.kt): `isFileDrawable` (hidden APIs)
- Fork's [`mono/MonoIconThemeController.kt`](https://github.com/LawnchairLauncher/platform_frameworks_libs_systemui/blob/233c33f3934cb364d997f42d989785d51d09530a/iconloaderlib/src/com/android/launcher3/icons/mono/MonoIconThemeController.kt): adds `shapePath`, `isFileDrawable`, `shouldForceThemeIcon` to the AOSP logic

Kvaesitso (GPL-3.0), [`MM2-0/Kvaesitso@c4805f3`](https://github.com/MM2-0/Kvaesitso/tree/c4805f3f18d5bcf071c5287f81466efd79169b20):
- [`services/icons/.../transformations/ForceThemedIconTransformation.kt`](https://github.com/MM2-0/Kvaesitso/blob/c4805f3f18d5bcf071c5287f81466efd79169b20/services/icons/src/main/java/de/mm20/launcher2/icons/transformations/ForceThemedIconTransformation.kt): silhouette fallback
- [`services/icons/.../IconService.kt`](https://github.com/MM2-0/Kvaesitso/blob/c4805f3f18d5bcf071c5287f81466efd79169b20/services/icons/src/main/java/de/mm20/launcher2/icons/IconService.kt): LruCache(200), transformation pipeline
- [`app/ui/.../component/ShapedLauncherIcon.kt`](https://github.com/MM2-0/Kvaesitso/blob/c4805f3f18d5bcf071c5287f81466efd79169b20/app/ui/src/main/java/de/mm20/launcher2/ui/component/ShapedLauncherIcon.kt): Compose rendering, SRC_IN tint
- [`data/appshortcuts/.../LauncherShortcut.kt`](https://github.com/MM2-0/Kvaesitso/blob/c4805f3f18d5bcf071c5287f81466efd79169b20/data/appshortcuts/src/main/java/de/mm20/launcher2/appshortcuts/LauncherShortcut.kt): shortcut monochrome handling

## 6. License notes

- **AOSP iconloaderlib: Apache-2.0.** You may copy or port it into an open-source project under any license compatible with Apache-2.0 (Apache, MIT-with-NOTICE, GPL-3.0 all OK). Keep the copyright header, state changes, and ship a NOTICE/attribution.
- **Lawnchair: Apache-2.0** (`LICENSE.txt`: "Copyright (c) 2005-2008, The Android Open Source Project / Copyright (c) 2024, Lawnchair"). Same obligations. Prefer porting from AOSP directly; Lawnchair adds little beyond prefs.
- **Kvaesitso: GPL-3.0.** Copying its code makes Pie Launcher GPL-3.0. Reimplementing the *idea* (tint the foreground alpha) is fine; it is a trivial technique.
- **Lawnicons** (if Option D is used later): consumed at runtime as a separate app, so there is no code-license issue. [unverified license of the pack's assets for redistribution, but we wouldn't redistribute]
- Pie Launcher currently has **no LICENSE file**. Choose one before copying any code.

## 7. Open questions surfaced

1. On Android 17, does `getMonochrome()` really return null for apps without a monochrome layer, or does the platform inject the QPR2 auto-generated layer for all launchers? This decides whether we need our own generator at all. Verify on device.
2. Which generator is best (A, B, or hybrid C), and does any app need a per-app override? This feeds the map's existing "Monochrome fallback quality" prototype.
3. PWA/pinned-shortcut icons (favicons): theme them, keep them in original colors, or badge them?
4. Should there be a user toggle for themed vs. original icons, or are icons always themed? And should the Pie use the same colors as Launcher3 (accent1/accent2) or Layer colors?
5. Project license choice (Apache-2.0 makes AOSP porting straightforward).
6. Icon-pack support (Lawnicons `grayscale_icon_map`): v1 or later?
