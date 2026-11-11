# How Vanadium and Firefox PWAs reach the launcher

Research for [#3](https://github.com/jgoedde/pie-launcher-remake/issues/3) (map [#1](https://github.com/jgoedde/pie-launcher-remake/issues/1)). Researched 2026-11-11 against Android API reference, Chromium `main`, Firefox (`mozilla-firefox/firefox` `main`), GrapheneOS Launcher3 branch `17`, and AOSP `ShortcutService`.

Claims marked **[unverified]** were not confirmed in a primary source or on a device.

## Answer in one paragraph

On GrapheneOS without Google Play, **both browsers produce the same thing: a pinned shortcut**, requested with `ShortcutManager.requestPinShortcut()`. Neither produces a WebAPK. The launcher must be the default home app, must export an activity for `android.content.pm.action.CONFIRM_PIN_SHORTCUT`, and must call `PinItemRequest.accept()`. It lists the shortcuts later with `LauncherApps.getShortcuts(FLAG_MATCH_PINNED)` and opens them with `LauncherApps.startShortcut()`. The config references a PWA as **(package name, shortcut id)**, the same key Launcher3 stores. The key survives browser updates and reboots. It does **not** survive uninstalling or clearing data of the browser, or of the launcher. Re-pinning gives a new id for Vanadium (random UUID each time). Firefox reuses the same id (the manifest `start_url`).

## 1. Vanadium (Chromium)

### WebAPK vs. pinned shortcut

- Chromium's WebAPK install is a three-way process. A Google minting server builds and signs the APK, then **Google Play installs it** through `GooglePlayWebApkInstallDelegate` ([chrome/browser/android/webapk/README.md](https://github.com/chromium/chromium/blob/main/chrome/browser/android/webapk/README.md)).
- In open-source Chromium, `AppHooks.getGooglePlayWebApkInstallDelegate()` returns `null` ([AppHooks.java](https://github.com/chromium/chromium/blob/main/chrome/android/java/src/org/chromium/chrome/browser/AppHooks.java)). With no delegate, `WebApkInstaller` stops with `WebApkInstallResult.NO_INSTALLER` ([WebApkInstaller.java](https://github.com/chromium/chromium/blob/main/chrome/android/java/src/org/chromium/chrome/browser/webapps/WebApkInstaller.java)). The real delegate exists only in Google's downstream Chrome.
- Vanadium is built from open-source Chromium. Its patch set has no WebAPK patch: searching the `GrapheneOS/Vanadium` repo for "webapk" finds only a string-rebranding patch. The GrapheneOS team rejected server-side minting and wants generation fully on-device ([Vanadium#179](https://github.com/GrapheneOS/Vanadium/issues/179)). Vanadium release notes up to 157.0.8103.50.0 (2026-11-10) do not mention WebAPK support.
- **Result: Vanadium never makes a WebAPK.** "Add to Home screen" / "Install app" always becomes a pinned shortcut. A GrapheneOS bug report on Android 17 says the same: Vanadium items are `ITEM_TYPE_DEEP_SHORTCUT` pinned shortcuts in package `app.vanadium.browser` ([os-issue-tracker#8257](https://github.com/GrapheneOS/os-issue-tracker/issues/8257)). Users also report that Chrome falls back to "add shortcut" on GrapheneOS when the WebAPK install fails ([os-issue-tracker#6071](https://github.com/GrapheneOS/os-issue-tracker/issues/6071)). That is not relevant here because the owner has no Play.
- **[unverified]** An August 2025 GrapheneOS post on X (linked from #6071) apparently talks about WebAPK installs. I could not read it. It most likely refers to Chrome with sandboxed Google Play, not Vanadium.

### What Chromium sends

[`ShortcutHelper::AddToLauncherWithSkBitmap`](https://github.com/chromium/chromium/blob/main/chrome/browser/android/shortcut_helper.cc):

- `webapp_id = base::Uuid::GenerateRandomV4()`: a **new random UUID on every add**.
- If the manifest `display` is standalone, fullscreen or minimal-ui, the intent opens Chromium's `WebappLauncherActivity` (`ACTION_START_WEBAPP`, extras `EXTRA_ID`, `EXTRA_URL`, …, plus a MAC). Otherwise it is a plain `ACTION_VIEW` of the URL, scoped to the browser package ([ShortcutHelper.java](https://github.com/chromium/chromium/blob/main/chrome/android/java/src/org/chromium/chrome/browser/ShortcutHelper.java)).
- [`WebappsUtils.addShortcutWithShortcutManager`](https://github.com/chromium/chromium/blob/main/components/webapps/browser/android/java/src/org/chromium/components/webapps/WebappsUtils.java) builds `ShortcutInfo(id = webapp_id)` with short and long label = title. The icon is `Icon.createWithAdaptiveBitmap` if the manifest icon is maskable, otherwise a plain bitmap. It then calls `requestPinShortcut(info, null)`.
- If `isRequestPinShortcutSupported()` returns false, Chromium falls back to the legacy `com.android.launcher.action.INSTALL_SHORTCUT` broadcast to the default launcher. **Do not implement that path.** Implement the modern one so the fallback is never taken.

## 2. Firefox (Fenix / Android Components `feature-pwa`)

[`WebAppShortcutManager.requestPinShortcut`](https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/android-components/components/feature/pwa/src/main/java/mozilla/components/feature/pwa/WebAppShortcutManager.kt):

- Firefox does nothing unless `ShortcutManagerCompat.isRequestPinShortcutSupported()` returns true. In that case nothing is added and there is no legacy fallback, so **the launcher must support pin requests or Firefox PWAs never appear**.
- **Installable PWA:** the page is secure, has a manifest whose `display` is not `browser`, and has large icons ([`installableManifest()`](https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/android-components/components/feature/pwa/src/main/java/mozilla/components/feature/pwa/ext/SessionState.kt)). In that case `buildWebAppShortcut` uses **shortcut id = `manifest.startUrl`**. The intent goes to `WebAppLauncherActivity` with action `mozilla.components.feature.pwa.PWA_LAUNCHER` and data = start URL. Fenix leaves `supportWebApps` at its default `true` ([Core.kt](https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/fenix/app/src/main/java/org/mozilla/fenix/components/Core.kt)).
- **Any other site** ("Add to Home screen" for a non-PWA): `buildBasicShortcut` uses **id = `UUID.randomUUID()`** and an `ACTION_VIEW` intent.
- Firefox later calls `ShortcutManager.updateShortcuts()` with the same `startUrl` ids to refresh labels and icons. The launcher sees this through `LauncherApps.Callback.onShortcutsChanged`.
- Package: `org.mozilla.firefox` for the release build. Beta and Nightly use other package names.

## 3. What the launcher must implement

All facts from the [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps), [PinItemRequest](https://developer.android.com/reference/android/content/pm/LauncherApps.PinItemRequest), [ShortcutQuery](https://developer.android.com/reference/android/content/pm/LauncherApps.ShortcutQuery) and [ShortcutManager](https://developer.android.com/reference/android/content/pm/ShortcutManager) references.

1. **Be the default home app.** `hasShortcutHostPermission()` is true only for "the current launcher (or default launcher if there is no set current launcher)" or the active voice-interaction service. Without it, `getShortcuts`, `pinShortcuts` and `startShortcut` throw `SecurityException`. There is no manifest permission to request. The ShortcutManager docs say pin requests are delivered to the **default launcher** and `isRequestPinShortcutSupported()` reflects the default launcher.
2. **Confirm activity.** Export an activity with an intent filter for `android.content.pm.action.CONFIRM_PIN_SHORTCUT`. Launcher3 does this in `AddItemActivity`, which also handles `CONFIRM_PIN_APPWIDGET` ([GrapheneOS Launcher3 `17` manifest](https://github.com/GrapheneOS/platform_packages_apps_Launcher3/blob/17/AndroidManifest-common.xml)). In the activity:
   - Call `launcherApps.getPinItemRequest(intent)` and check `requestType == REQUEST_TYPE_SHORTCUT` and `isValid()`.
   - `getShortcutInfo()` is always non-null. Show its label and icon and let the user accept.
   - Call `accept()`. Only when it returns `true` is the shortcut pinned. Record `(package, id, user)` in the launcher's own store (the inventory).
   - If the user declines, do nothing. The browser gets no answer.
   - `ShortcutInfo.isPinned()` may already be true when the same PWA is added again (Firefox with the same `startUrl`). `accept()` must still be called.
   - Pie Launcher has no home grid, so "accept" means "add to inventory". Optionally add it straight to a Layer **[design choice, not researched]**.
3. **List.** Call `getShortcuts(ShortcutQuery().setQueryFlags(FLAG_MATCH_PINNED), Process.myUserHandle())`. This returns only the shortcuts **this launcher** pinned. `FLAG_MATCH_PINNED_BY_ANY_LAUNCHER` is ignored for normal launchers: it needs the assistant role or the system `ACCESS_SHORTCUTS` permission. For the inventory export, combine this with the app shortcuts (`FLAG_MATCH_MANIFEST | FLAG_MATCH_DYNAMIC`) using the same `(package, id)` key.
4. **Launch.** Call `startShortcut(packageName, shortcutId, sourceBounds, opts, user)`. It throws `ActivityNotFoundException` if the shortcut is gone or disabled. Handle that with an "item unavailable" state.
5. **Icons and labels.** Use `getShortcutIconDrawable(info, density)` and `getShortcutBadgedIconDrawable`. Chromium sends an adaptive icon only for maskable manifest icons. Neither browser sets a monochrome layer, so PWAs **always take the auto-generated monochrome icon path** for themed icons. This follows from the code: neither builder sets a monochrome drawable.
6. **Watch for changes.** Register `LauncherApps.Callback`. Use `onShortcutsChanged` to refresh labels and icons and to detect removal, and `onPackageRemoved` for uninstall.
7. **Unpinning.** `pinShortcuts(pkg, ids, user)` **replaces** the whole pinned set for that package. To remove one PWA, pass the remaining ids. Never pass an empty list by accident.
8. **Boot race.** On GrapheneOS Android 17, Launcher3 deleted Vanadium shortcuts after reboot. It queried before `ShortcutService` had restored them and treated an empty result as "gone" ([os-issue-tracker#8257](https://github.com/GrapheneOS/os-issue-tracker/issues/8257)). **Never delete a config reference because a lookup returned empty.** Show it as unavailable and re-query after `ACTION_USER_UNLOCKED` or `onShortcutsChanged`.

## 4. Identifier for the pie config

Use **`{ "package": "<pkg>", "shortcutId": "<id>" }`**, with the main user implied. Main-profile-only is a decision in #1. This is the triple Launcher3 stores as `ShortcutKey` and the triple `startShortcut` takes. The inventory export should print exactly these values together with the label, so the owner can copy them into the JSON.

| Event | Vanadium (`app.vanadium.browser`) | Firefox (`org.mozilla.firefox`) |
|---|---|---|
| Id format | random UUID per add | PWA: manifest `start_url`. Non-PWA site: random UUID |
| Browser update | survives (see note below) | survives |
| Reboot | survives, but see the boot race in §3.8 | survives |
| Remove and re-add the same PWA | **new id**, config must be edited | **same id** (unless the site changes `start_url`) |
| Browser uninstall or "clear data" | **all pins deleted** | **all pins deleted** |
| Launcher uninstall, reinstall or "clear data" | **all of this launcher's pins dropped**, every PWA must be added again | same |
| Switching from Launcher3 to Pie Launcher | the old Launcher3 pins are invisible to Pie Launcher; add each PWA again while Pie Launcher is default | same |

Sources for the lifecycle rows: AOSP [`ShortcutService.cleanUpPackageLocked`](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/core/java/com/android/server/pm/ShortcutService.java). Its javadoc says it is "called when an app is uninstalled, or an app gets 'clear data'ed". It removes the package as publisher, removes it as launcher (`user.removeLauncher`), and removes its pins from all launchers. On a package update, `handlePackageUpdateFinished` only restores and rescans manifest shortcuts and does not touch pins. Pinned shortcuts belong to `ShortcutService`, not to the app, and an app can only disable them, not delete them (ShortcutManager `disableShortcuts`).

**[unverified]** The `aosp-mirror` GitHub copy may lag behind Android 17 / GrapheneOS 17. I assume the behaviour is unchanged because this has been stable since API 25. A device test should confirm it.

**[unverified]** Backup and restore of a launcher's pins (ShortcutService supports restoring launcher pins through backup) was not examined. GrapheneOS backup (Seedvault) behaviour is unknown.

## 5. Open points for a device check

- Confirm on the owner's phone that Vanadium "Install app" returns a `PinItemRequest` whose id is a UUID and whose package is `app.vanadium.browser`, and the same for Firefox with a `start_url` id. A tiny debug launcher that logs `getShortcutInfo()` is enough.
- Confirm that clearing Vanadium's data removes its pins on Android 17.
