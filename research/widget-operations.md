# Research: widget-hosting operational concerns for v1

Answers issue #19, "Which widget-hosting operational concerns must v1 handle?". Researched 2026-11-11. Builds on the [widget hosting research](https://github.com/jgoedde/pie-launcher-remake/blob/research/widget-hosting/research/widget-hosting.md) (#2) and the widget editing decisions (#9).

**Short answer.**

| Concern | Verdict | What v1 does |
|---|---|---|
| Memory pressure from image-heavy `RemoteViews` | **Required (cheap guardrails only)** | Async inflation via `setExecutor`, per-widget error view, no launcher-side caching of widget bitmaps, keep `stopListening` on `onStop`. Log `MemoryLimiter` exits at startup. No budgeting or eviction logic. |
| Backup/restore of appWidget ids (`ACTION_APPWIDGET_HOST_RESTORED`, `OPTION_APPWIDGET_RESTORE_COMPLETED`) | **Ignorable, with one required guard** | Don't handle the broadcast. Turn off Android backup for the app (`android:allowBackup="false"`, or at least exclude the Room DB), so a restore can never bring back a layout full of stale ids. |
| `onProvidersChanged` / `onAppWidgetRemoved` | **Nice-to-have / required** | `onAppWidgetRemoved` → drop the row (required, see #2). `onProvidersChanged` → re-check widgets marked broken (nice-to-have). |
| `OPTION_APPWIDGET_DISPLAY_ID` (API 37) | **Ignorable** | The platform fills it in for us inside `updateAppWidgetSize(Bundle, List<SizeF>)`. Phone, single display. |
| Engagement metrics / visibility tracking (API 36.1) | **Nice-to-have** | Clicks and scrolls are recorded automatically. Impressions need `start/stopVisibilityTracking()`. It's a ~20-line hook that only benefits provider apps, so do it if cheap, after v1 otherwise. |

Legend: **[V]** = verified against the cited primary source. **[U]** = unverified or inferred, so check on a device.

Source snapshots used:
- AOSP `frameworks/base` branch `android17-release` (fetched 2026-11-11). AOSP `main` on googlesource is frozen at March 2025 and **does not contain** the 36.1/37 APIs, so `android17-release` is the right snapshot.
- AOSP `packages/apps/Launcher3` branch `android17-release` @ `c612e6e` (2026-04-16).
- developer.android.com: [Android 17 behavior changes (targeting 37)](https://developer.android.com/about/versions/17/behavior-changes-17), [Android 17 behavior changes (all apps)](https://developer.android.com/about/versions/17/behavior-changes-all), [AppWidgetManager ref](https://developer.android.com/reference/android/appwidget/AppWidgetManager), [AppWidgetHostView ref](https://developer.android.com/reference/android/appwidget/AppWidgetHostView), [host guide](https://developer.android.com/develop/ui/views/appwidgets/host), [Glance metrics guide](https://developer.android.com/develop/ui/compose/glance/metrics).

Paths below are shortened to `fw/` = `frameworks/base/+/refs/heads/android17-release/` and `L3/` = `Launcher3/+/refs/heads/android17-release/` on android.googlesource.com.

---

## 1. Memory pressure from image-heavy `RemoteViews`

### What it is
- **Per-widget bitmap cap, enforced on the provider side.** `system_server` computes `mMaxWidgetBitmapMemory = 6 * w * h` of the real display size ("1.5 times the size of the display") [V: `fw/services/appwidget/.../AppWidgetServiceImpl.java` `computeMaximumWidgetBitmapMemory`]. On each `updateAppWidget`, `ensureWidgetViewsMemoryLimitLocked` estimates the `RemoteViews` bitmap memory. If it is over the cap, the update is dropped (`widget.views = null`) and `IllegalArgumentException` is thrown **in the provider's process**. For providers targeting ≤ 36 this is only a `Slog.w` [V: same file; [behavior-changes-17](https://developer.android.com/about/versions/17/behavior-changes-17#memory-limit-widget)]. Bitmap-backed `Icon`s are counted only for providers targeting > 37 (target 37 just warns about them) [V: `targetSdk > 37 ? totalMemoryUsage : bitmapCacheMemoryUsage`].
- **The cap is per widget, not per host.** For a 1080 × 2400 phone that is about 15.5 MB per widget update. N image-heavy widgets can land N × that in our heap [V: arithmetic from the formula; the per-widget scope is from the code, which checks `widget.views` of one widget].
- **URI images bypass the cap entirely.** `setImageViewUri` / `Icon.createWithContentUri`: "the widget host process decodes the referenced `content://` file at full resolution into its own heap" [V: [behavior-changes-17](https://developer.android.com/about/versions/17/behavior-changes-17)]. That's us.
- **Android 17 app memory limits (all apps)**: limits based on total device RAM. A kill shows up as `ApplicationExitInfo` `REASON_OTHER` with `"MemoryLimiter:AnonSwap"` in the description. `adb shell am memory-limiter status|ignore|manual` inspects and overrides the limits. The status "includes the memory limits imposed on visible and non-visible processes" [V: [behavior-changes-all](https://developer.android.com/about/versions/17/behavior-changes-all#app-memory-limits)]. Whether a launcher (the home/top app) gets a different limit is not documented [U].
- `AppWidgetHostView` catches `RuntimeException`/`Exception` from RemoteViews inflation and swaps in its error view (`handleViewError`), but it does **not** catch `OutOfMemoryError` [V: `fw/core/java/android/appwidget/AppWidgetHostView.java`, all catch blocks are `Exception`/`RuntimeException`].

### What Launcher3 does
Nothing widget-specific to budget memory [V: grep of `L3/src` and `L3/quickstep/src`]:
- `BaseLauncherAppWidgetHostView` calls `setExecutor(Executors.THREAD_POOL_EXECUTOR)` (async inflation), overrides `getErrorView()`, and on `onLayout` `RuntimeException` posts `switchToErrorView()` [V: `L3/src/com/android/launcher3/widget/BaseLauncherAppWidgetHostView.java`].
- `Launcher.onTrimMemory(≥ TRIM_MEMORY_UI_HIDDEN)` only calls `SQLiteDatabase.releaseMemory()` for the widget-preview DB. The "clear widget bitmaps from the tray" line is a TODO [V: `L3/src/com/android/launcher3/Launcher.java`].
- `Utilities.isBinderSizeError` (`TransactionTooLargeException` / `DeadObjectException`) is tolerated in `LauncherWidgetHolder` and `WidgetsModel` [V].

### Verdict: required, but only cheap guardrails
The fix for oversized images belongs to the provider; the platform already rejects them for target-37 providers. A host can't shrink a provider's bitmaps, and Launcher3 doesn't try. For v1:
1. `setExecutor(<background executor>)` on every `AppWidgetHostView` (Launcher3 parity).
2. Override `getErrorView()` with our own "Widget can't be shown" view, so one broken widget never takes down the Page.
3. Don't cache widget bitmaps or previews in the launcher. We use the system picker (#9), so we hold no preview images at all.
4. Keep `stopListening()` on `onStop` (#2). Non-visible widgets then get no updates, though their views stay in memory.
5. At startup, read `ActivityManager.getHistoricalProcessExitReasons` and log entries whose description contains `MemoryLimiter`. That's a one-off diagnostic, so an unexplained launcher restart can be traced to a widget. Use `adb shell dumpsys appwidget` to see per-widget bitmap usage [V: behavior-changes-17].
6. No widget-count limit, no memory budget, no eviction in v1. Revisit only if device testing hits `MemoryLimiter` kills.

## 2. Backup/restore of appWidget ids

### What it is
- After a restore, the AppWidget service remaps ids. To the host package it sends `ACTION_APPWIDGET_HOST_RESTORED` with `EXTRA_APPWIDGET_OLD_IDS`, `EXTRA_APPWIDGET_IDS` (index-aligned) and `EXTRA_HOST_ID`. The host must "translate each appWidgetId in the OLD_IDS array to its new value" in its own restored layout [V: [AppWidgetManager ref](https://developer.android.com/reference/android/appwidget/AppWidgetManager#ACTION_APPWIDGET_HOST_RESTORED); `AppWidgetServiceImpl.sendWidgetRestoreBroadcastLocked` sets `setPackage(host.packageName)` + `EXTRA_HOST_ID`]. Providers get the mirror `ACTION_APPWIDGET_RESTORED`.
- `OPTION_APPWIDGET_RESTORE_COMPLETED` (API 30) is set by the **provider** app after it has finished restoring a widget. It is persisted by the service [V: ref; `AppWidgetServiceImpl` `restore_completed` attribute]. It's not a host duty.
- Widget state is only backed up for packages that host or provide live widgets (`packageNeedsWidgetBackupLocked`) [V]. It travels with the host package's backup, so a host that opts out of backup gets no widget restore [U: inferred from `getWidgetState(packageName)` being per package; not traced through the backup service].

### What Launcher3 does
- Declares `AppWidgetsRestoredReceiver` for `android.appwidget.action.APPWIDGET_HOST_RESTORED` [V: `L3/AndroidManifest-common.xml`]. It checks `EXTRA_HOST_ID == APPWIDGET_HOST_ID`, stores the old→new id arrays in prefs, and warns if no launcher restore is pending [V: `L3/src/com/android/launcher3/AppWidgetsRestoredReceiver.java`]. `RestoreDbTask` later rewrites its favorites DB, and widgets whose provider isn't installed yet show as `PendingAppWidgetHostView` until `restoreStatus == RESTORE_COMPLETED` [V: `WidgetInflater.kt`, `LauncherAppWidgetInfo`].
- Ships a `LauncherBackupAgent` with `fullBackupOnly` and a backup scheme [V: `L3/AndroidManifest.xml`]. Supporting restore is a sizeable subsystem.

### Verdict: ignorable, plus one required guard
#9 already decided "Backup/restore of widget ids is not in v1". The trap: Android's auto backup is **on by default**, and it would back up and restore the Room DB (ADR 0002). A restored layout with old ids and no `HOST_RESTORED` handling points at ids that are unbound or belong to nothing: blank or broken widgets. So v1 must:
- set `android:allowBackup="false"` in the manifest. This also keeps the AppWidget service from restoring our host's widget state [U, see above]. The Pie Config isn't hurt: it has its own export (ADR 0002). Alternatively, exclude the Room DB via `dataExtractionRules`. `allowBackup="false"` is simpler and matches "layout is never exported".
- keep the startup reconciliation from #2 (`getAppWidgetIds()` vs DB, delete orphans, mark broken rows). It covers any id drift.

Handling `ACTION_APPWIDGET_HOST_RESTORED` belongs with a future "move to a new phone" feature, not v1.

## 3. `onProvidersChanged` / `onAppWidgetRemoved`

- `AppWidgetHost.onProvidersChanged()` is "called when the set of available widgets changes (ie. widget containing packages are added, updated or removed, or widget components are enabled or disabled.)". `onAppWidgetRemoved(id)` is called when the service removes a widget [V: `fw/core/java/android/appwidget/AppWidgetHost.java`]. The service fires providers-changed from package broadcasts (`updateProvidersForPackageLocked` → `onProvidersChangedLocked`) [V: `AppWidgetServiceImpl`].
- Launcher3: `ListenableAppWidgetHost.onProvidersChanged` notifies listeners on the main thread, which refresh the widget picker model and pending widgets. `onAppWidgetRemoved` is routed via the model thread [V: `L3/src/com/android/launcher3/widget/ListenableAppWidgetHost.kt`].
- **Verdict:** `onAppWidgetRemoved` → delete the Room row: **required**, already in #2. `onProvidersChanged` → re-run "is this provider still installed / back again?" for rows marked broken: **nice-to-have**. We have no own picker to refresh (#9), and the startup reconciliation already catches most cases.

## 4. `OPTION_APPWIDGET_DISPLAY_ID` (API 37)

### What it is
"A bundle extra (int) that indicates which display the widget is currently being displayed on", meant for `DisplayManager.getDisplay(int)` → `DisplayMetrics` [V: [AppWidgetManager ref](https://developer.android.com/reference/android/appwidget/AppWidgetManager#OPTION_APPWIDGET_DISPLAY_ID), added in API 37]. It sits behind the exported aconfig flag `widget_display_changes`, "better support for widgets on connected displays" [V: `fw/core/java/android/appwidget/flags.aconfig`].

### Who sets it
**The platform does, for us.** `AppWidgetHostView.updateAppWidgetSize(Bundle, List<SizeF>)` adds `OPTION_APPWIDGET_DISPLAY_ID = getDisplay().getDisplayId()` when the flag is on and the view is attached [V: `fw/core/java/android/appwidget/AppWidgetHostView.java`]. The service persists it and defaults to `Display.DEFAULT_DISPLAY` when it's missing [V: `AppWidgetServiceImpl` `display_id` serialisation]. Launcher3 never references the constant [V: grep of `L3/`].

### Verdict: ignorable
We already call `updateAppWidgetSize(Bundle, List<SizeF>)` (#2). One caveat: call it after the `AndroidView` is attached, so `getDisplay()` is non-null. Otherwise the extra is missing and providers fall back to the default display, which is correct on a phone anyway. Whether the flag is enabled on GrapheneOS builds doesn't matter for the same reason [U].

## 5. Engagement metrics / visibility tracking (API 36.1)

### What it is
- Providers call `AppWidgetManager.queryAppWidgetEvents(begin, end)` to get their own widgets' `AppWidgetEvent`s: clicked ids, scrolled ids, visible duration, position. Events are "only kept by the system for a few days" [V: [AppWidgetManager ref](https://developer.android.com/reference/android/appwidget/AppWidgetManager#queryAppWidgetEvents(long,%20long))]. They're aggregated hourly by default (`widget_events_report_interval_ms`) [V: [Glance metrics guide](https://developer.android.com/develop/ui/compose/glance/metrics)].
- **The host is the only data source.** `AppWidgetHostView.InteractionLogger` is the view's `RemoteViews.InteractionHandler`:
  - **Clicks** (`onInteraction` → `addClickedId`) and **scrolls** (`onScroll` → `addScrolledId`) are recorded automatically for any `AppWidgetHostView`. Position comes from `onLayout`, using `getGlobalVisibleRect` plus the first scrolled parent's offset as the "page" [V: `fw/.../AppWidgetHostView.java`].
  - **Impressions** are recorded only between `startVisibilityTracking()` and `stopVisibilityTracking()`. Even then a span counts only while `isTracking && hasWindowFocus() && isVisibleToUser()`. `mIsTracking` defaults to `false` [V: same file]. If we never call `startVisibilityTracking()`, providers get clicks and scrolls but zero impressions.
  - `AppWidgetHost` flushes the collected events to the service (`reportWidgetEvents`) in `stopListening()`, in `deleteAppWidgetId()` and when views are cleared [V: `fw/.../AppWidgetHost.java` `reportAllWidgetEvents`]. The service merges them only for ids bound to the calling host; it needs no permission [V: `AppWidgetServiceImpl.reportWidgetEvents`].
- The host guide's "Host responsibilities" list doesn't mention metrics [V: [host guide](https://developer.android.com/develop/ui/views/appwidgets/host)]. It's a courtesy, not a contract.

### What Launcher3 does
`WidgetVisibilityTracker` calls `view.startVisibilityTracking()` for every widget on a visible workspace page while the launcher is in `NORMAL` state with no floating view open, and `stopVisibilityTracking()` otherwise. It re-evaluates on page switch, state-transition complete, drag-layer hierarchy change and widget added [V: `L3/src/com/android/launcher3/widget/WidgetVisibilityTracker.kt`, wired in `Launcher.java`]. `QuickstepWidgetHolder` forwards `collectWidgetEvent` across its shared listeners [V].

### Verdict: nice-to-have
This doesn't affect our UX, and our device runs no metrics consumer. It only gives provider apps impression data. The Compose equivalent is small. In the Page composable, for each hosted view, call `startVisibilityTracking()` when `pagerState.settledPage == thisPage` and no overlay (Pie, drawer, edit mode, context menu) is open, and `stopVisibilityTracking()` otherwise. The platform's own `hasWindowFocus && isVisibleToUser` gate handles screen-off and partial visibility [U: interaction with Compose `AndroidView` + `HorizontalPager` off-screen pages untested]. Ship it in v1 if it fits into the widget-hosting ticket; otherwise defer. Nothing breaks without it. Clicks and scrolls are reported anyway, and the flush already happens through our `stopListening()` on `onStop`.

---

## Open questions for device testing
- What memory limit the Android 17 memory limiter applies to the default home app, checked with `am memory-limiter status` on the GrapheneOS device.
- Whether `widget_display_changes` and `engagement_metrics` are enabled on the GrapheneOS Android 17 build. Low stakes either way.
- Whether `allowBackup="false"` really suppresses AppWidget-service restore for our host on a Seedvault restore.
