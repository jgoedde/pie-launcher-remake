# What do Android 17 and Compose M3 Expressive offer a launcher?

Research for [#5](https://github.com/jgoedde/pie-launcher-remake/issues/5) (map [#1](https://github.com/jgoedde/pie-launcher-remake/issues/1)). Researched 2026-11-11 from primary sources: developer.android.com, Google Maven, AOSP (`android17-release` branch) and Lawnchair source (`16-dev` @ `6c592e7`, 2026-11-11). Terms follow `GLOSSARY.md`.

Anything not confirmed in a primary source is marked **[unverified]**.

## TL;DR

- **Android 17 is API level 37** (`Build.VERSION_CODES.CINNAMON_BUN = 37`). It shipped stable in June 2026 (AOSP tag `android-17.0.0_r1`). GrapheneOS has been on Android 17 since then and is on 17 QPR1-based releases now. Minor SDK versions exist (37.1, 37.2).
- **Material 3 Expressive is not in a stable release yet.** The current stable Compose BOM (`2026.09.00`) pins `material3:1.4.0`, and 1.4.0 *removed* every `@ExperimentalMaterial3ExpressiveApi` API. Expressive lives in `material3:1.5.0`, which is at **`1.5.0-rc01` (2026-11-04)**. Most expressive components graduated during the 1.5 cycle. `MaterialShapes` and `LoadingIndicator` are still experimental. **There is no radial or pie menu component.** The Pie has to be custom-drawn, and the building blocks are `MotionScheme.expressive()`, `MaterialShapes` + `graphics-shapes` `Morph`, and theme color roles.
- **Being a home app** takes nothing new in 17: a `MAIN` + `HOME` + `DEFAULT` intent filter, `RoleManager.ROLE_HOME` to prompt, and `QUERY_ALL_PACKAGES` (or `<queries>` for `MAIN`/`LAUNCHER`). `LauncherApps` results are filtered by package visibility. Shortcuts need the Home role. Edge-to-edge is mandatory (no opt-out since targetSdk 36). Predictive back is on by default since targetSdk 36.
- **Gestures.** The bottom home/recents gesture strip (`mandatorySystemGestures`) **cannot be excluded** by any app. Touches that start there never reach the launcher. The App Drawer swipe-up and the Pie Zone must start **above** that inset, which is exactly how Launcher3 works. On a third-party home app, the system launcher (Launcher3/Quickstep) keeps the nav gestures and shows its *fallback* Recents. Lawnchair gets around this only with root (QuickSwitch). The home activity is exempt from the 200 dp limit on gesture exclusion rects.
- **The notification shade** has **no public SDK API**, and API 37 added none. Launchers call the hidden `StatusBarManager.expandNotificationsPanel()` by reflection with the normal-level `EXPAND_STATUS_BAR` permission, falling back to an AccessibilityService `GLOBAL_ACTION_NOTIFICATIONS` (Lawnchair does exactly this).
- **Android 17 changes that matter to us:** large-screen orientation and resizability locks are ignored (no opt-out at targetSdk 37), widget RemoteViews bitmap memory limits apply on the provider side and affect what the widget host renders, app memory limits, and stricter background activity launch (BAL) rules for `IntentSender`. Nothing in API 37 touches `LauncherApps`.

---

## 1. Versions (exact)

| Thing | Value | Source |
|---|---|---|
| Android 17 API level | **37**. `Build.VERSION_CODES.CINNAMON_BUN`, "Constant Value: 37 (0x00000025)". `compileSdk = 37`, `targetSdk = 37` | [Build.VERSION_CODES](https://developer.android.com/reference/android/os/Build.VERSION_CODES#CINNAMON_BUN), [SDK setup](https://developer.android.com/about/versions/17/setup-sdk), [AOSP codenames table](https://source.android.com/docs/setup/reference/build-numbers) ("Android17 / 17 / API level 37 / 26Q2") |
| Android 17 stable | AOSP build `CP2A.260605.016`, tag `android-17.0.0_r1` (security patch level 2026-06-05). The tag and branch `android17-release` exist in `platform/build`. Public release date of **2026-06-16** comes from secondary sources only **[unverified against a Google page]** | [build numbers](https://source.android.com/docs/setup/reference/build-numbers), `git ls-remote https://android.googlesource.com/platform/build` |
| Beta timeline | Beta 1 2026-02-13, Beta 3 (Platform Stability) 2026-03-26, Beta 4 2026-04-16, Beta 4.1 2026-06-01 | [Android 17 release notes](https://developer.android.com/about/versions/17/release-notes) |
| Minor SDK | API reference shows "Added in version 37.2" (for example `RoleManager.ROLE_REGISTERED_APP_STORE`). QPR betas are listed as 37.1 and 37.2 | [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager) |
| GrapheneOS | Release 2026061600 was "our final release based on Android 16 QPR2/QPR3 since we've completed our initial port to Android 17". Current releases carry "Pixel firmware/driver updates from Android 17 QPR1" | [GrapheneOS releases](https://grapheneos.org/releases) |
| Compose BOM (stable) | **`2026.09.00`**, published to Google Maven **2026-09-09** (Last-Modified of POM). It pins `ui` 1.12.1 and `material3` **1.4.0** | [BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [Maven metadata](https://dl.google.com/android/maven2/androidx/compose/compose-bom/maven-metadata.xml) |
| Compose BOM (alpha channel) | `compose-bom-alpha:2026.11.00` (2026-11-04) pins `material3` 1.5.0-rc01 and `ui`/`foundation` 1.13.0-rc01 | [compose-bom-alpha POM](https://dl.google.com/android/maven2/androidx/compose/compose-bom-alpha/2026.11.00/compose-bom-alpha-2026.11.00.pom) |
| material3 stable | **`1.4.0` — 2025-09-24** (no 1.4.x patch releases on Maven) | [material3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.4.0) |
| material3 pre-release | **`1.5.0-rc01` — 2026-11-04** (beta01 2026-10-07, beta02 2026-10-21) | [material3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.5.0-rc01) |
| Tooling | Android 17 SDK requires AGP ≥ 8.9.0-rc01 and Android Studio Meerkat 2024.3.1+ | [SDK setup](https://developer.android.com/about/versions/17/setup-sdk) |

**Implication:** an M3 Expressive app has to depend on `material3:1.5.0-rc01` (or 1.5.0 once it ships, likely within weeks given rc01 [unverified forecast]). Do that either by overriding the BOM's material3 version or by using `compose-bom-alpha`.

## 2. Material 3 Expressive in Compose

### Stability

- 1.4.0-beta01 (2025-07-30), breaking change: "All public APIs tagged with `ExperimentalMaterial3ExpressiveApi` … have been removed, please switch to 1.5.0-alpha to continue enjoying these features." So **stable 1.4.0 has no expressive APIs**. It does use `MotionScheme` internally ("Material 3 components are now using the new MotionScheme"). ([1.4.0-beta01](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.4.0-beta01), [1.4.0](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.4.0))
- Graduations during 1.5.0 alphas (each one no longer needs the opt-in) ([1.5 notes](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.5.0-rc01)):
  - alpha15 (2026-02-25): "Graduate motion scheme from experimental".
  - alpha18 (2026-04-22): "Promote `materialExpressTheme`, `expressiveLightColorScheme`". This one is `MaterialExpressiveTheme`, and the note has a typo. alpha18 also added "`Material3ExpressiveApi` annotation that doesn't require `OptIn`" and promoted the Wavy progress indicators.
  - alpha19 (2026-05-06): promoted ToggleButtons, expressive menus, **FAB and FAB Menu**, and expressive buttons. It also did "**Revert `MaterialShapes` and `LoadingIndicator` promotions to stable.**"
  - alpha20: SplitButton. alpha22: ButtonGroup and FloatingToolbar. alpha23: flexible TopAppBars and expressive list items.
- Still experimental as of the current reference docs: **`MaterialShapes`** is `@ExperimentalMaterial3ExpressiveApi sealed class MaterialShapes` ([reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialShapes)). `LoadingIndicator` has no later re-promotion in the notes **[unverified whether rc01 differs]**.

### Components and APIs relevant to us

- **`MotionScheme`**: `MotionScheme.expressive()` / `standard()`, with `default/fast/slow` × `Spatial/Effects` `FiniteAnimationSpec`s (springs). Read it via `MaterialTheme.motionScheme`. `LocalMotionScheme` was removed in alpha27. **This is how the Pie open/close and hover animations should get their feel.** ([MotionScheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme))
- **`MaterialShapes`**: 37 normalized `RoundedPolygon`s (Circle, Cookie4/6/7/9/12Sided, Clover4/8Leaf, Sunny, Burst, Pill, Arch, …) meant to be used directly or in a `Morph`. Plus `toShape()`. They are a good fit for Pie Item backgrounds that morph on hover (for example Circle → Cookie). The underlying library is `androidx.graphics:graphics-shapes` (stable **1.1.0**), which material3 no longer depends on transitively since 1.4.0-beta02 ("Remove graphics-shapes dependency"), so add it explicitly if used. ([graphics-shapes Maven](https://dl.google.com/android/maven2/androidx/graphics/graphics-shapes/maven-metadata.xml))
- **`androidx.compose.foundation.shape.PolygonShape`** (new, changed in material3 1.5.0-beta01 notes): a polygon `Shape` in foundation, which is only in the 1.13 pre-releases **[unverified exact foundation version]**.
- **`FloatingActionButtonMenu`**: a vertical expanding list from a FAB. It is **not radial**.
- **`LoadingIndicator` / `ContainedLoadingIndicator`**: shape-morphing indicators (experimental).
- **`MaterialExpressiveTheme`** + expressive color schemes. Monet comes from `dynamicLightColorScheme`/`dynamicDarkColorScheme` as before.
- Expressive `DropdownMenu` with groups and submenus (`DropdownMenuPopupPositionProviders`). These could serve the long-press Shortcuts menu in the App Drawer.
- `SearchBar` / `SearchBarState` (stable since alpha24) for App Drawer text search. `VerticalDragHandle` and `HorizontalCenteredHeroCarousel` are in 1.4.0.
- **Nothing radial:** the material3 package lists no radial, pie or wheel menu ([package summary](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary)). The Pie is a custom `Canvas`/`Layout` composable themed with M3 tokens.

## 3. Becoming a home app on Android 17

### Manifest and the Home role

- Intent filter: `MAIN` + `CATEGORY_HOME` + `CATEGORY_DEFAULT`. AOSP Launcher3 also uses `singleTask`, `clearTaskOnLaunch="true"`, `stateNotNeeded="true"`, `resumeWhilePausing="true"`, `taskAffinity=""`, `windowSoftInputMode="adjustPan"`, `resizeableActivity="true"`, `screenOrientation="unspecified"`, and a wide `configChanges` list. ([Launcher3 AndroidManifest.xml](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/android17-release/AndroidManifest.xml))
- Prompt the user with `RoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)` (`"android.app.role.HOME"`, API 29+) and check `isRoleAvailable`/`isRoleHeld`. ([RoleManager](https://developer.android.com/reference/android/app/role/RoleManager))
- Shortcuts: `LauncherApps.hasShortcutHostPermission()` is true only for "the current launcher (or default launcher if there is no set current launcher)" and the active voice interaction service. **Pie Shortcuts and App Drawer Shortcuts only work while we hold the Home role.** ([LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps#hasShortcutHostPermission()))

### Package visibility

- `LauncherAppsService.getLauncherActivities` → `queryIntentLauncherActivities` passes the **caller's UID** into `PackageManagerInternal.queryIntentActivities`. That means `LauncherApps.getActivityList()` **is filtered by package visibility**. ([LauncherAppsService.java @ android17-release](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/pm/LauncherAppsService.java))
- Options ([declaring](https://developer.android.com/training/package-visibility/declaring)):
  - `<queries><intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent></queries>` makes every launchable app visible, which is enough for the App Drawer.
  - `QUERY_ALL_PACKAGES` makes all apps visible. Docs call it "not recommended" with Play policy restrictions, but **we don't ship on Play**. AOSP Launcher3 and Lawnchair both declare it.
- Launchers are **not** in the "visible automatically" list ([automatic](https://developer.android.com/training/package-visibility/automatic)).
- Recommendation: declare `QUERY_ALL_PACKAGES`. It also covers widget providers, icon packs, and resolving browser and PWA packages without enumerating intents. **[unverified]:** whether `AppWidgetManager.getInstalledProviders` is visibility-filtered. AOSP behavior suggests it is.
- API 37 diff: **no changes to `LauncherApps`, `LauncherActivityInfo` or `ShortcutInfo`** (the `android.content.pm` diff only touches PackageInfo, PackageInstaller, PackageManager, PermissionInfo and ServiceInfo). ([API diff 36→37](https://developer.android.com/sdk/api_diff/37/changes))

### Edge-to-edge

- targetSdk 36+ on Android 16+ ignores `windowOptOutEdgeToEdgeEnforcement`, so **edge-to-edge is mandatory**. ([Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16)) In Compose, use `enableEdgeToEdge()` and `WindowInsets.*` (`systemBars`, `systemGestures`, `mandatorySystemGestures`).

### Predictive back on the home activity

- targetSdk 36+: predictive back animations are on by default, `onBackPressed` is not called and `KEYCODE_BACK` is not dispatched. You can still opt out with `android:enableOnBackInvokedCallback="false"`. ([Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16))
- Launcher3 sets `enableOnBackInvokedCallback="true"` and registers one `OnBackAnimationCallback` at `PRIORITY_DEFAULT`. It dispatches back in priority order (cancel action mode → cancel drag → top floating view → custom handlers → state such as All Apps). ([QuickstepLauncher.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/android17-release/quickstep/src/com/android/launcher3/uioverrides/QuickstepLauncher.java), [Launcher.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/android17-release/src/com/android/launcher3/Launcher.java))
- For us: use `PredictiveBackHandler` (activity-compose) to close the App Drawer and the Shortcuts menu with progress-driven animation, and disable it on the bare home Page. **[unverified]:** what the system does with an unhandled back on the home task (presumably nothing visible, since there is no back-to-home target).

### Orientation (new in 17)

- At targetSdk 37, `screenOrientation`, `resizeableActivity`, `min/maxAspectRatio` and `setRequestedOrientation()` are **ignored on displays with smallest width ≥ 600 dp**, with no opt-out. Exceptions are games and users opting in through settings. On phones (sw < 600 dp), portrait lock still works. ([behavior-changes-17](https://developer.android.com/about/versions/17/behavior-changes-17), [details](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored)) **[unverified]:** whether HOME activities are exempt. The doc lists no such exemption.

## 4. Gesture navigation conflicts

### What the platform guarantees

- **Home and quick-switch gestures** live in the bottom strip, and "**Apps can't opt out of these gestures** as they can with the back gesture". Apps learn the strip size from `WindowInsets.getMandatorySystemGestureInsets()` (Compose: `WindowInsets.mandatorySystemGestures`). ([Gesture navigation guide](https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav))
- **Back gesture** (left and right edges) *can* be excluded with `View.setSystemGestureExclusionRects` (Compose: `Modifier.systemGestureExclusion`). The reference says: "the system will put a limit of 200dp on the vertical extent of the exclusions … The limit does not apply while the navigation bar is stickily hidden, **nor to the input method and home activity**." ([View#setSystemGestureExclusionRects](https://developer.android.com/reference/android/view/View#setSystemGestureExclusionRects(java.util.List%3Candroid.graphics.Rect%3E)))

### How Launcher3 handles it (android17-release)

- `BaseDragLayer` stores `insets.getMandatorySystemGestureInsets()` as `mSystemGestureRegion`. Touch controllers and floating views only intercept events **inside** the screen minus that region. ([BaseDragLayer.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/android17-release/src/com/android/launcher3/views/BaseDragLayer.java))
- Swipe-up to All Apps: `PortraitStatesTouchController.canInterceptTouch` lets you swipe to all apps "from anywhere" in NORMAL state, meaning anywhere on the workspace above the gesture strip.
- Swipe-down for the shade: `StatusBarTouchController` ignores events "below the navbar height" (`y > height - insets.bottom`). It triggers when `dy > touchSlop && dy > |dx|`.
- `LauncherRootView` excludes the full height from the back gesture only when it has to disallow back (`setDisallowBackGesture`).
- **Recents on a third-party home:** `OverviewComponentObserver` says "The default home app is a different launcher. Use the fallback Overview instead". The system launcher (Launcher3/Quickstep, the `config_recentsComponentName` holder) keeps handling the bottom swipe for every home app and shows its own `RecentsActivity`. ([OverviewComponentObserver.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/android17-release/quickstep/src/com/android/quickstep/OverviewComponentObserver.java))

### How Lawnchair handles it

- It inherits all of Launcher3's controllers. System Recents integration ("QuickSwitch") is "**root required**" (Android 15–16 on the 16-dev branch). Without it, `QuickStepContract.sRecentsDisabled = true` and the system fallback Recents is used. ([Lawnchair README](https://github.com/LawnchairLauncher/lawnchair/blob/16-dev/README.md), `lawnchair/src/app/lawnchair/LawnchairApp.kt`)

### Implications for Pie Launcher

- A **touch-down in the Pie Zone does not conflict** with the nav gesture *as long as the Pie Zone's bottom edge sits above `WindowInsets.mandatorySystemGestures.bottom`*. Touches that start in the strip go to the system and never reach us. The strip height varies by device and navigation mode, so position the Pie Zone relative to the inset rather than with a fixed dp offset from the screen bottom **[recommendation]**.
- **Swipe up → App Drawer** must start above the strip as well (same as Launcher3). A swipe from the very bottom edge is always system home/recents, and nothing can change that. On GrapheneOS the system launcher's fallback Recents will appear, and **there is no way around this without root**.
- The **Pie drag** after touch-down can freely cross the bottom strip and the side edges. Gestures are routed by where they *start*. This follows from the docs, which describe edge-start swipes, and Launcher3's start-point checks **[unverified on device]**.
- **Horizontal Page swipes** that start at the left or right edge will trigger back. We could exclude edge rects over the Widget Zones without the 200 dp cap (home activity exemption), but that disables back there. Launcher3 doesn't do this. Treat it as an open product question.

## 5. Notification shade from the launcher (swipe down)

- **No public API.** `StatusBarManager.expandNotificationsPanel()` is `@hide @UnsupportedAppUsage @TestApi` in android17-release. It has **no** `maxTargetSdk`, unlike `collapsePanels()`, which has `maxTargetSdk = R` and needs `STATUS_BAR` from S on. ([StatusBarManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/java/android/app/StatusBarManager.java))
- Server side: `StatusBarManagerService.expandNotificationsPanel()` calls `enforceExpandStatusBar()`, which checks `android.permission.EXPAND_STATUS_BAR`, and that permission has `protectionLevel="normal"` (install-time, no prompt). It does nothing if `DISABLE2_NOTIFICATION_SHADE` is set. ([StatusBarManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/statusbar/StatusBarManagerService.java), [core AndroidManifest.xml](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/res/AndroidManifest.xml))
- Hidden-API status: by the [non-SDK interface policy](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces), `@UnsupportedAppUsage` without `maxTargetSdk` puts it on the "unsupported" list, which reflection can reach at any targetSdk (with a logcat warning). It is not on the explicit `max-target-o` lists in `boot/hiddenapi`. **[unverified on a GrapheneOS device at targetSdk 37]**
- API 37 added only `StatusBarManager.showPowerMenu(Executor, OutcomeReceiver)` and nothing for expanding the shade. ([API diff: StatusBarManager](https://developer.android.com/sdk/api_diff/37/changes/android.app.StatusBarManager))
- **Prior art:** Lawnchair declares `EXPAND_STATUS_BAR` and calls `Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(getSystemService("statusbar"))`. On failure it falls back to its AccessibilityService with `GLOBAL_ACTION_NOTIFICATIONS` (`lawnchair/src/app/lawnchair/gestures/handlers/OpenNotificationsHandler.kt`). Launcher3 instead streams the touch to SystemUI through `SystemUiProxy.onStatusBarTouchEvent`, which gives the finger-following shade. That is privileged and only works for the recents-provider launcher.
- So for us: reflection plus `EXPAND_STATUS_BAR` gives an *animated open* once the swipe-down passes touch slop, but not a finger-tracking pull. The accessibility fallback is optional.

## 6. Other Android 17 changes affecting a third-party launcher

| Change | Scope | Relevance | Source |
|---|---|---|---|
| Orientation, resizability and aspect locks ignored on sw ≥ 600 dp, no opt-out | targetSdk 37 | Only on tablets and foldables. Phone portrait lock unaffected | [behavior-changes-17](https://developer.android.com/about/versions/17/behavior-changes-17) |
| Widget RemoteViews bitmap memory ceiling (`6 * width * height` bytes). `IllegalArgumentException` for providers targeting 37 | widget *provider* side | Some widgets may fail to update. URI images are decoded "at full resolution" in the **widget host process**, which is our heap | same |
| App memory limits (`ApplicationExitInfo` description "MemoryLimiter") | all apps | Keep icon caches bounded | [behavior-changes-all](https://developer.android.com/about/versions/17/behavior-changes-all) |
| BAL hardening extended to `IntentSender`, use `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` | targetSdk 37 | Mostly irrelevant, because a launcher starts activities while visible. Matters only for any PendingIntent the launcher sends from the background | [behavior-changes-17](https://developer.android.com/about/versions/17/behavior-changes-17) |
| `AppWidgetManager.queryAppWidgetEvents`, `OPTION_APPWIDGET_DISPLAY_ID` | new API | Widget host extras | [API diff](https://developer.android.com/sdk/api_diff/37/changes/android.appwidget.AppWidgetManager) |
| Users can hide home-screen app labels, and bubble any app by long-pressing launcher icons | Pixel/system launcher features | These are in Pixel Launcher, not platform APIs for third-party launchers **[unverified that any API backs them]** | [release notes](https://developer.android.com/about/versions/17/release-notes) |
| App-lock APIs (`LOCK_APPS` permission behind `android.security.app_lock_apis`, and `LauncherAppsService` adds an app-lock flag to queries) | in AOSP source | Not public SDK at 37 per the diff **[unverified]** | Launcher3 `AndroidManifest-common.xml`, LauncherAppsService |
| No changes to `LauncherApps` / `ShortcutInfo` / `RoleManager.ROLE_HOME` | — | The home-app surface is the same as Android 16 | [API diff](https://developer.android.com/sdk/api_diff/37/changes) |

## Open questions surfaced

1. **Expressive dependency policy:** ship on `material3:1.5.0-rc01` now, or wait for 1.5.0 stable? And how to handle the still-experimental `MaterialShapes`/`LoadingIndicator` (opt in or avoid)?
2. **Pie Zone placement relative to `mandatorySystemGestures`:** the "fixed in code" Pie Zone should be defined against the inset, not the raw screen bottom.
3. **Shade pull feel:** a reflection open (not finger-tracking) is the only option. Is that acceptable, and do we want the AccessibilityService fallback (it costs a user-enabled service)?
4. **Recents on GrapheneOS** will always be the system launcher's fallback Overview. Accept this as a known limitation, since there is no root.
5. **Horizontal Page swipe vs. back gesture** at screen edges: exclude edge rects over the Widget Zones (allowed uncapped for the home activity) or not?
6. **Widget host:** `BIND_APPWIDGET` / `bindAppWidgetIdIfAllowed` + the user bind prompt, and the Android 17 widget bitmap limits. Not researched here.
