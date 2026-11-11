# Research: hosting app widgets in a Compose launcher (Android 17+)

Answers issue #2, "How does a Compose launcher host app widgets?". Researched 2026-11-11.

**Short answer.** The platform API is still the classic `AppWidgetHost` / `AppWidgetManager` / `AppWidgetHostView` trio. Nothing about it is Compose-specific. A Compose launcher holds one `AppWidgetHost` per process, hosts each `AppWidgetHostView` inside an `AndroidView`, and runs the steps allocate id → bind (user-consent dialog) → configure → persist id + placement. It reports size changes with `updateAppWidgetSize(Bundle, List<SizeF>)`. All of this is reusable. The Launcher3-specific parts are the grid/span maths (`DeviceProfile`), drag-and-drop, the `PendingAppWidgetHostView` placeholder machinery and the internal favorites DB. For Android 15–17 a host should also consider: generated previews (`getWidgetPreview`, API 35), visibility tracking for engagement metrics (`start/stopVisibilityTracking`, API 36.1), `OPTION_APPWIDGET_DISPLAY_ID` (API 37), and the API 36 background-activity-start modes for launching configuration activities.

Legend: **[V]** = verified against the cited primary source. **[U]** = unverified or inferred, so check on a device.

Source snapshots used:
- AOSP `frameworks/base` and `packages/apps/Launcher3`, `refs/heads/main` on android.googlesource.com (fetched 2026-11-11).
- Lawnchair `16-dev` @ `6c592e7` (a Launcher3 fork). Its widget code is Launcher3's, with minor additions.
- Kvaesitso `main` @ `c4805f3`: a real **Jetpack Compose** launcher that hosts widgets. It is the closest prior art for our stack.

---

## 1. Required APIs

| API | Role | Notes |
|---|---|---|
| `AppWidgetHost(context, hostId)` | Connection to AppWidgetService | `hostId` must be unique within our package and **stable forever**. Persisted widget ids are scoped to it [V: [host guide](https://developer.android.com/develop/ui/views/appwidgets/host)]. Launcher3 uses `1024` ([`LauncherWidgetHolder.APPWIDGET_HOST_ID`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/LauncherWidgetHolder.java)); Kvaesitso uses `44203`. |
| `AppWidgetHost.startListening()` / `stopListening()` | Receive RemoteViews updates | Docs say to call them in `onStart()`/`onStop()` [V: [AppWidgetHost ref](https://developer.android.com/reference/android/appwidget/AppWidgetHost)]. On `startListening()` the service returns the queued `PendingHostUpdate`s (view updates, provider changes, removals), so nothing is lost while stopped [V: AOSP `AppWidgetHost.java`]. |
| `AppWidgetHost.allocateAppWidgetId()` / `deleteAppWidgetId(id)` | Widget instance lifecycle | Delete on removal **and** on every cancelled bind/configure, or ids leak [V: host guide]. |
| `AppWidgetHost.createView(ctx, id, info)` / override `onCreateView` | Get the `AppWidgetHostView` | Override `onCreateView` to return our own subclass. Launcher3 does this to catch long-press [V]. |
| `AppWidgetHost.onProvidersChanged()`, `onProviderChanged(id, info)`, `onAppWidgetRemoved(id)` (API 30) | Callbacks | Refresh the picker; update cached provider info after an app update; drop a widget whose provider removed it [V: ref + Launcher3 `LauncherAppWidgetHost.java`]. |
| `AppWidgetHost.getAppWidgetIds()` (API 26) | Reconcile | Use it to garbage-collect ids that are bound to our host but not in our DB [V: ref]. |
| `AppWidgetHost.startAppWidgetConfigureActivityForResult(activity, id, flags, reqCode, options)` (API 21) | Configure / reconfigure | Needs a **bound** id. Throws `ActivityNotFoundException`. Required when the provider is in another profile [V: ref]. Internally it calls `activity.startIntentSenderForResult(...)`, so the result arrives in the Activity's `onActivityResult` [V: AOSP]. |
| `AppWidgetManager.getInstalledProvidersForProfile(UserHandle)` | Picker list | Filtered by package visibility (`filterAppAccess`) [V: AOSP `AppWidgetServiceImpl.getInstalledProvidersForProfile`]. Launcher3 and Lawnchair declare `QUERY_ALL_PACKAGES` [V: manifests]. |
| `AppWidgetManager.bindAppWidgetIdIfAllowed(id, user, provider, options)` | Bind without UI if already allowed | Returns `false` unless we hold `BIND_APPWIDGET` **or** the user has allow-listed us [V: AOSP `bindAppWidgetId` → `hasCallerBindPermissionOrBindWhiteListedLocked`]. |
| `Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)` + `EXTRA_APPWIDGET_ID`, `EXTRA_APPWIDGET_PROVIDER`, `EXTRA_APPWIDGET_PROVIDER_PROFILE`, `EXTRA_APPWIDGET_OPTIONS` | System consent dialog | The user picks "allow" (once) or "always allow" (adds us to the bind allow-list) [V: host guide]. |
| `AppWidgetHostView.updateAppWidgetSize(Bundle, List<SizeF>)` (API 31) | Size reporting | The 4-int overload is deprecated in API 31 [V: AOSP `@Deprecated`]. Triggers the provider's `onAppWidgetOptionsChanged` [V: host guide]. |
| `AppWidgetManager.updateAppWidgetOptions(id, Bundle)` | Size reporting without a view | Merges with `putAll` and broadcasts options-changed [V: AOSP]. Launcher3 calls this off the main thread and skips it if the sizes are unchanged ([`WidgetSizes.updateWidgetSizeRangesAsync`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/util/WidgetSizes.java)). |
| `AppWidgetHostView.setColorResources(SparseIntArray)` / `resetColorResources()` (API 31) | Dynamic colors | Lets us push our Monet/M3 palette into widgets that use `system_accent*`/`system_neutral*` colors [V: host guide]. |
| `AppWidgetHostView.setOnLightBackground(bool)` (API 29), `setExecutor(Executor)` (API 26) | Light-bg layouts; async inflation | [V: ref page] |
| `AppWidgetHostView.startVisibilityTracking()` / `stopVisibilityTracking()` (36.1) | Impressions for widget engagement metrics | Call when the widget becomes visible or hidden. Durations are reported when the host stops listening or the widget is deleted [V: [AppWidgetHostView ref](https://developer.android.com/reference/android/appwidget/AppWidgetHostView)]. |
| `AppWidgetManager.getWidgetPreview(provider, user, category)` (API 35) | Generated previews for the picker | See the Android 15 section. **Requires bind allow-listing** for third-party providers [V: AOSP]. |
| `LauncherApps.getPinItemRequest(intent)` + `PinItemRequest.accept(Bundle)` | App-initiated "add widget to home" (`requestPinAppWidget`) | Launcher3 handles `android.content.pm.action.CONFIRM_PIN_APPWIDGET` in `AddItemActivity` [V: Launcher3 manifest / `AddItemActivity.java`]. Optional for v1. |

**Permission reality check:** `android.permission.BIND_APPWIDGET` is `signature|privileged` [V: [frameworks/base core/res/AndroidManifest.xml](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml)]. Launcher3 and Lawnchair declare it, but a sideloaded Pie Launcher will **not** be granted it. We always depend on the user consent dialog, and in practice on the user ticking "always allow" once. The docs still say to declare the permission in the manifest [V: host guide]; declaring it is harmless.

## 2. Flow step by step

### 2.1 Host lifecycle
1. Create **one** `AppWidgetHost` with a hard-coded `HOST_ID`. Use the application context and keep it as a process singleton, for example via DI or a `staticCompositionLocalOf`. Kvaesitso's [`ProvideAppWidgetHost`](https://github.com/MM2-0/Kvaesitso/blob/c4805f3f18d5bcf071c5287f81466efd79169b20/app/ui/src/main/java/de/mm20/launcher2/ui/base/AppWidgetHost.kt) does this with `remember { AppWidgetHost(context.applicationContext, 44203) }`.
2. Start listening when the home activity is visible and stop when it is not. Kvaesitso uses `lifecycle.repeatOnLifecycle(RESUMED) { startListening(); try { awaitCancellation() } finally { stopListening() } }`. Launcher3 starts only once *started + resumed + normal state* all hold, but stops only on `onStop` ([`LauncherWidgetHolder.setShouldListenFlag`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/LauncherWidgetHolder.java)). This avoids flapping while the drawer or an overlay is up. Recommended for us: start on `STARTED`, stop on `onStop`. [V for both sources; the recommendation is ours.]
3. Wrap `startListening()` in a try/catch for binder transaction-size errors. Launcher3 tolerates them because the listening relationship is established anyway (comment referencing issue 14255011) [V].

### 2.2 Adding a widget (picker → placed)
1. **List providers:** `getInstalledProvidersForProfile(Process.myUserHandle())`, filtered to `widgetCategory & WIDGET_CATEGORY_HOME_SCREEN`. Hide `WIDGET_FEATURE_HIDE_FROM_PICKER` [V: AppWidgetProviderInfo ref]. Work profile is out of scope for v1 (map #1).
2. **Allocate:** `id = host.allocateAppWidgetId()`.
3. **Bind:** `if (!awm.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, sizeOptions))` launch `ACTION_APPWIDGET_BIND` for result. On `RESULT_CANCELED`, call `deleteAppWidgetId(id)` and abort [V: Launcher3 `Launcher.handleActivityResult` / `completeTwoStageWidgetDrop`; Kvaesitso `BindAndConfigureAppWidgetActivity`].
   - Pass the initial size bundle (`OPTION_APPWIDGET_SIZES` + min/max) as `options` so the first render already has the right size [U: Launcher3 has a TODO that it does not pass options in the bind intent; it passes `info.bindOptions` to `bindAppWidgetIdIfAllowed`].
4. **Configure (if needed):** this is needed iff `info.configure != null && !(CONFIGURATION_OPTIONAL && RECONFIGURABLE)`. Both flags must be set to skip it [V: Launcher3 [`WidgetAddFlowHandler.needsConfigure`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/WidgetAddFlowHandler.java); host guide]. Call `host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, options)`.
   - `RESULT_OK` → place the widget. `RESULT_CANCELED`, a missing `EXTRA_APPWIDGET_ID` result, `ActivityNotFoundException` or `SecurityException` → `deleteAppWidgetId(id)` and do not add [V: Launcher3 `LauncherWidgetHolder.startConfigActivity` catches both exceptions and synthesizes `RESULT_CANCELED`; the Glance config guide says the activity must return `RESULT_CANCELED` on back so "the host doesn't add the widget"].
   - The provider gets **no** `ACTION_APPWIDGET_UPDATE` when it has a configuration activity; the activity must push the first update itself [V: [Glance config guide](https://developer.android.com/develop/ui/compose/glance/configuration)]. Expect a blank or `initialLayout` view until it does.
5. **Persist** `{appWidgetId, provider ComponentName, page, zone, x, y, w, h}` in our own store. The platform stores none of the placement [V: host guide: "Any host-specific state…must be persisted by the hosting package"].
6. **Render** via `createView` in an `AndroidView` (section 3), then report size.

In Compose, the bind and configure results must come back through an Activity. There are two options:
- (a) Use `rememberLauncherForActivityResult(StartActivityForResult())` for the bind intent. The configure step is harder: `startAppWidgetConfigureActivityForResult` needs a real `Activity`, and its result lands in `onActivityResult`, not in an ActivityResult registry callback [U: the platform `getIntentSenderForConfigureActivity` is `@hide`, so `StartIntentSenderForResult` cannot be fed directly].
- (b) Do what Kvaesitso does: a tiny transparent **trampoline Activity** (`BindAndConfigureAppWidgetActivity`) that runs allocate → bind → configure with classic `onActivityResult`, deletes the id on any failure, and returns the id to Compose through an `ActivityResultContract`. **Recommended.** It keeps the multi-step state machine out of composition and survives process death better than composition state [V: [Kvaesitso WidgetPickerSheet.kt](https://github.com/MM2-0/Kvaesitso/blob/c4805f3f18d5bcf071c5287f81466efd79169b20/app/ui/src/main/java/de/mm20/launcher2/ui/launcher/sheets/WidgetPickerSheet.kt)].

### 2.3 Reconfigure
Offer "Reconfigure" in the widget's edit or long-press menu iff `info.configure != null && widgetFeatures & WIDGET_FEATURE_RECONFIGURABLE` (API 28 flag; the platform honours it from Android 12) [V: AppWidgetProviderInfo ref; Glance config guide]. Call `startAppWidgetConfigureActivityForResult` with the **existing** id. On cancel, **do not delete**; keep the widget as it was [V: Launcher3 `REQUEST_RECONFIGURE_APPWIDGET` branch: "Leave the widget in the pending state if the user canceled"].

### 2.4 Resize and size reporting
- Defaults come from `targetCellWidth/Height` (API 31, in grid cells) when present, otherwise from `minWidth/minHeight` (dp, exposed as px on the info object). Resize bounds come from `minResizeWidth/Height`, `maxResizeWidth/Height` (API 31) and `resizeMode` [V: AppWidgetProviderInfo ref]. Do not exceed `maxResize*` [V: host guide].
- After every layout change, call `hostView.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp)))`, or `awm.updateAppWidgetOptions` with `OPTION_APPWIDGET_SIZES` + `MIN/MAX_WIDTH/HEIGHT`. Sizes are in **dp**, and should not include the padding the host adds [V: Launcher3 `WidgetSizes.getWidgetSizePx` subtracts `widgetPadding`].
- Phones typically report 2 sizes (portrait and landscape). Never send more than `MAX_INIT_VIEW_COUNT` (16) [V: host guide]. Our launcher is presumably portrait-locked [U: not decided], so one `SizeF` per widget is enough. Kvaesitso sends exactly one size, taken from `BoxWithConstraints`.
- Avoid redundant updates: each one is a broadcast to the provider. Launcher3 compares with `getAppWidgetOptions(id)` first and runs on a background executor [V].

### 2.5 Removal, provider changes, restore
- User removes the widget → `host.deleteAppWidgetId(id)` and drop the DB row.
- `onAppWidgetRemoved(id)` (API 30) → drop the row [V: Launcher3 routes it to the model thread].
- On startup, reconcile `host.getAppWidgetIds()` against the DB. Delete orphans; mark DB rows whose provider is gone or uninstalled as broken. Launcher3 shows `PendingAppWidgetHostView` for those [V: class exists; reconciliation details U].
- Backup/restore: the service remaps ids and sends `ACTION_APPWIDGET_HOST_RESTORED` with old/new ids to the host [V: AOSP `AppWidgetServiceImpl` sends it]. Launcher3 also tracks `OPTION_APPWIDGET_RESTORE_COMPLETED`. Probably not needed for a personal-use v1 [U].

## 3. Embedding in Compose

The Kvaesitso pattern ([external/AppWidgetHost.kt](https://github.com/MM2-0/Kvaesitso/blob/c4805f3f18d5bcf071c5287f81466efd79169b20/app/ui/src/main/java/de/mm20/launcher2/ui/launcher/widgets/external/AppWidgetHost.kt)) [V]:

```kotlin
BoxWithConstraints(modifier) {
  key(widgetId) {
    AndroidView(
      factory = { ctx -> host.createView(ctx.applicationContext, widgetId, info) },
      update = { v ->
        v.setColorResources(m3Mapping)               // or resetColorResources()
        v.updateAppWidgetSize(Bundle(), arrayListOf(SizeF(maxWidth.value, maxHeight.value)))
        v.setPadding(pad)
      })
  }
}
```

What we would change or add for Pie Launcher:
- **Subclass `AppWidgetHostView`** and return it from a custom `AppWidgetHost.onCreateView`. Override `onInterceptTouchEvent` to run our own long-press detection (enter edit mode, move, resize), as Launcher3's `LauncherAppWidgetHostView` + `CheckLongPressHelper` does. A Compose `pointerInput` on a parent sees events that the View has already consumed, so View-level interception is the dependable way to get a long-press on a widget [V: Launcher3 pattern; the Compose-parent claim is U].
- **Gesture conflicts:** our home screen has horizontal Page swipes, swipe-up for the drawer and swipe-down for the shade, and widgets can contain scrollable lists. Launcher3 calls `dragLayer.requestDisallowInterceptTouchEvent(true)` on `ACTION_DOWN` when the widget is scrollable (`checkScrollableRecursively`) [V]. Kvaesitso sets `isNestedScrollingEnabled = true` on `ListView`/`ScrollView` children [V]. Whether Compose's `AndroidView` interop respects `requestDisallowInterceptTouchEvent` for the pager and our swipe detectors needs a prototype [U].
- Use `key(appWidgetId)` so a recycled composition never shows widget A's view under widget B's id. Do not call `createView` twice for the same id while a view is alive; the host keeps one listener per id [V: AOSP `mListeners` SparseArray keyed by id].
- Pass `setExecutor(backgroundExecutor)` for async RemoteViews inflation [V: API exists; benefit U].
- Wire `startVisibilityTracking()`/`stopVisibilityTracking()` to "this Page is the visible Page and the widget is on screen". `Modifier.onVisibilityChanged` or pager state are candidates [U].
- Context: Kvaesitso inflates with `applicationContext`, Launcher3 with the launcher Activity context. The Activity context is safer for click-launch ActivityOptions and theming [U].

## 4. Android 15–17 changes relevant to a host

| Version | Change | Host impact |
|---|---|---|
| 15 (API 35) | **Generated previews**: `AppWidgetManager.getWidgetPreview(provider, user, category)` returns `RemoteViews`; `AppWidgetProviderInfo.generatedPreviewCategories` tells which categories have one [V: refs]. | The picker should prefer the generated preview, then `previewLayout` (API 31), then `previewImage`, then the icon [V: precedence per `previewLayout` doc; generated-first per `setWidgetPreview` doc: "used instead of previewLayout or previewImage"]. **Pitfall:** for other apps' providers the service returns a preview only if the caller has `BIND_APPWIDGET` **or is bind-allow-listed** [V: AOSP `getWidgetPreview`]. Until the user has ticked "always allow" once, generated previews come back `null` for us. Launcher3 gates this behind the `Flags.generatedPreviews()` aconfig flag (`WidgetManagerHelper.loadGeneratedPreview`) [V]. |
| 15 (API 35) | `notifyAppWidgetViewDataChanged` deprecated (collection widgets move to `RemoteCollectionItems`) [V: AppWidgetManager ref]. | Provider-side; no host action. |
| 16 (API 36) | `WIDGET_CATEGORY_NOT_KEYGUARD` [V]. | Irrelevant for a home-screen host. |
| 16 (API 36) | `ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED` deprecated in favour of `..._ALLOW_IF_VISIBLE` / `..._ALLOW_ALWAYS` [V: ActivityOptions ref]. | The configure-activity `IntentSender` is created with the creator BAL mode **DENIED** [V: AOSP `createAppWidgetConfigIntentSender`], so any background-start privilege must come from us, the sender. Kvaesitso passes `ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ALLOWED)` as `options` on API 34+ [V]. We should pass `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` [U: whether a visible launcher strictly needs it]. |
| 16 QPR2 (36.1) | **Widget engagement metrics**: `AppWidgetManager.queryAppWidgetEvents`, `AppWidgetEvent`; host-side `AppWidgetHostView.start/stopVisibilityTracking()` [V: refs; [Glance metrics guide](https://developer.android.com/develop/ui/compose/glance/metrics)]. | Optional, but impressions are only recorded if the host calls these. Clicks and scrolls are presumably captured inside `AppWidgetHostView` itself [U]. Neo-Launcher hit compile issues because its prebuilt framework jar lacked these methods; that does not apply to a plain-SDK Compose app [V: [Neo-Launcher PR #567](https://github.com/NeoApplications/Neo-Launcher/pull/567)]. |
| 17 (API 37) | `OPTION_APPWIDGET_DISPLAY_ID` options extra [V: AppWidgetManager ref]. | Host *should* put the display id into the options bundle so providers can pick `DisplayMetrics` [U: whether the platform fills it automatically]. Low priority on a phone. |
| 17 (API 37) | RemoteViews bitmap memory budget, enforced for **providers** targeting 37 (about 6 × screen w × h bytes per update) [V: [behavior-changes-17](https://developer.android.com/about/versions/17/behavior-changes-17)]. URI-backed images bypass the IPC cap but are decoded in the **host's heap** [V: same]. | The launcher process carries widget image memory. Combined with Android 17's new **app memory limits** for all apps [V: [behavior-changes-all](https://developer.android.com/about/versions/17/behavior-changes-all)], many image-heavy widgets could get the launcher killed (`MemoryLimiter:AnonSwap` in `ApplicationExitInfo`). Worth monitoring. |
| — | No new or deprecated `AppWidgetHost` APIs in 15–17 beyond the above. The API 37 diff lists only `AppWidgetManager` as changed [V: [api_diff 37](https://developer.android.com/sdk/api_diff/37-incr/changes/pkg_android.appwidget)]. | — |

No **public** system widget-picker API exists for third-party launchers. `ACTION_APPWIDGET_PICK` exists, but every launcher studied (Launcher3, Lawnchair, Kvaesitso) builds its own picker from `getInstalledProvidersForProfile` [V for the three launchers; whether `ACTION_APPWIDGET_PICK` still works well on 17 is U].

## 5. Pitfalls

1. **No `BIND_APPWIDGET` for us.** Every first widget add goes through the system dialog. Tell the user to tick "always allow"; it also unlocks generated previews (section 4).
2. **Leaked ids:** delete the id on every cancelled or failed bind and configure, and reconcile with `getAppWidgetIds()` at startup.
3. **Configure skip rule:** skip only if *both* `configuration_optional` and `reconfigurable` are set.
4. **Reconfigure cancel ≠ delete.**
5. **No initial update** for widgets with a configuration activity. A blank widget until the activity pushes RemoteViews is normal.
6. **`stopListening()` while hidden** is expected; updates are replayed on `startListening()`. Do not stop on `onPause` if the home screen stays visible behind an overlay (Launcher3 stops only on `onStop`).
7. **Binder size errors** from `startListening()`/`createView()` with many large widgets: catch them and show an error or placeholder view, as Launcher3 does.
8. **Package visibility** filters `getInstalledProvidersForProfile` and `getWidgetPreview`. Declare `QUERY_ALL_PACKAGES`; we are not on Play, so policy is no issue. A narrower `<queries>` intent filter for `android.appwidget.action.APPWIDGET_UPDATE` *may* suffice [U].
9. **Size units:** `AppWidgetProviderInfo.minWidth` etc. are **px** on the object, while `OPTION_APPWIDGET_SIZES` are **dp** [V: refs]. Mixing them up gives wrong layouts.
10. **Touch conflicts** between scrollable widgets and Page swipe, drawer and shade gestures. Prototype early [U].
11. **Host memory on Android 17** (section 4).
12. **`setColorResources`** overrides widget colors; if we push M3 colors, offer it per widget or globally, with `resetColorResources()` as the off switch. Kvaesitso exposes it as a per-widget `useThemeColors` [V].

## 6. Reference code

| What | Where |
|---|---|
| Official host guide | https://developer.android.com/develop/ui/views/appwidgets/host |
| Config activity contract | https://developer.android.com/develop/ui/compose/glance/configuration |
| Platform host class | [frameworks/base AppWidgetHost.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/appwidget/AppWidgetHost.java) |
| Platform service (permission checks, preview access, config IntentSender) | [AppWidgetServiceImpl.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/appwidget/java/com/android/server/appwidget/AppWidgetServiceImpl.java): `bindAppWidgetId`, `getWidgetPreview`, `createAppWidgetConfigIntentSender`, `getInstalledProvidersForProfile` |
| Launcher3 host wrapper + listening state machine | [widget/LauncherWidgetHolder.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/LauncherWidgetHolder.java) |
| Launcher3 `AppWidgetHost` subclass (callbacks, view recycling) | [widget/LauncherAppWidgetHost.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/LauncherAppWidgetHost.java) |
| Launcher3 host view (long-press, scroll intercept, deferred updates) | [widget/LauncherAppWidgetHostView.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/LauncherAppWidgetHostView.java) |
| Launcher3 configure rule | [widget/WidgetAddFlowHandler.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/WidgetAddFlowHandler.java) |
| Launcher3 size bundle | [widget/util/WidgetSizes.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/util/WidgetSizes.java) |
| Launcher3 provider listing + generated previews | [widget/WidgetManagerHelper.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/WidgetManagerHelper.java) |
| Launcher3 bind/config result handling | [Launcher.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/Launcher.java): `addAppWidgetImpl`, `handleActivityResult`, `completeTwoStageWidgetDrop` |
| Launcher3 pin-widget requests | [dragndrop/AddItemActivity.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/dragndrop/AddItemActivity.java) |
| Launcher3 resize UI | `src/com/android/launcher3/AppWidgetResizeFrame.java` (not read in detail) |
| Lawnchair (same files under `src/com/android/launcher3/widget/`, plus a headless host for its smartspace) | [lawnchair/src/app/lawnchair/HeadlessWidgetsManager.kt](https://github.com/LawnchairLauncher/lawnchair/blob/6c592e79246de0e383a2e69ef332730e5ae1ce29/lawnchair/src/app/lawnchair/HeadlessWidgetsManager.kt) |
| **Kvaesitso (Compose) host provider** | [ui/base/AppWidgetHost.kt](https://github.com/MM2-0/Kvaesitso/blob/c4805f3f18d5bcf071c5287f81466efd79169b20/app/ui/src/main/java/de/mm20/launcher2/ui/base/AppWidgetHost.kt) |
| **Kvaesitso AndroidView embedding + M3 color mapping** | [ui/launcher/widgets/external/AppWidgetHost.kt](https://github.com/MM2-0/Kvaesitso/blob/c4805f3f18d5bcf071c5287f81466efd79169b20/app/ui/src/main/java/de/mm20/launcher2/ui/launcher/widgets/external/AppWidgetHost.kt) |
| **Kvaesitso bind+configure trampoline Activity** | [ui/launcher/sheets/WidgetPickerSheet.kt](https://github.com/MM2-0/Kvaesitso/blob/c4805f3f18d5bcf071c5287f81466efd79169b20/app/ui/src/main/java/de/mm20/launcher2/ui/launcher/sheets/WidgetPickerSheet.kt) |

### Reusable vs Launcher3-specific
- **Reusable as a pattern**, to rewrite in Kotlin: the listening state machine, `needsConfigure`, the bind → configure → cancel-deletes result handling, size-bundle construction and dedup, scroll-aware touch interception, generated-preview loading, and the provider-changed and removed callbacks.
- **Launcher3-specific, skip:** `DeviceProfile`/`InvariantDeviceProfile` span maths, `CellLayout` and drag-layer plumbing, `PendingAppWidgetHostView`/`ListenableHostView` deferred inflation (tied to its async workspace loader), `CustomWidgetManager` (plugins), the favorites `ContentProvider`, the `ResourceBasedOverride` factories, and the aconfig flags.
- **Closest fit for us: Kvaesitso.** It shows the Compose way: a CompositionLocal host, `AndroidView` + `BoxWithConstraints` size reporting, and a trampoline Activity. Its widgets live in a scrolling column, not a grid, so the grid, resize and long-press parts must come from Launcher3's ideas.
