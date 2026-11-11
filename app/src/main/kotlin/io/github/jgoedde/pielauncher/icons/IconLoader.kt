package io.github.jgoedde.pielauncher.icons

import android.app.Application
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.jgoedde.pielauncher.core.icons.IconOwner
import io.github.jgoedde.pielauncher.core.icons.MaskKey
import io.github.jgoedde.pielauncher.core.icons.MaskSource
import io.github.jgoedde.pielauncher.core.icons.maskSource
import io.github.jgoedde.pielauncher.platform.AppsRepository
import io.github.jgoedde.pielauncher.platform.IconSettingsStore
import io.github.jgoedde.pielauncher.platform.LauncherApp
import io.github.jgoedde.pielauncher.platform.PinnedShortcut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads every icon the launcher draws through the Themed Icon pipeline (PRD 8): picks the mask
 * source, renders the mask (or the Original Icon) and caches it.
 *
 * Masks are kept on disk by [MaskStore] (keyed by package, `versionCode`, size and algorithm
 * version) with this in-memory [LruCache] in front. App icons are cached in memory per
 * [LauncherApp] instance; the apps repository creates new instances after every package change.
 * Shortcut icons are cached per Shortcut version (its last-changed time). A package change also
 * drops that package's masks from both caches.
 *
 * With Themed Icons off every icon is an Original Icon, PWAs included (PRD 8.4); the switch is
 * part of the in-memory key, and masks on disk are kept for when it is turned back on.
 */
@SingleIn(AppScope::class)
@Inject
class IconLoader(
    private val application: Application,
    private val launcherApps: LauncherApps,
    private val userManager: UserManager,
    appsRepository: AppsRepository,
    iconSettings: IconSettingsStore,
    appScope: CoroutineScope,
) {
    /** The Themed Icons switch; null until the settings are read. Icons load again when it changes. */
    val themedIcons: StateFlow<Boolean?> =
        iconSettings.settings.map { it.themedIcons }.stateIn(appScope, SharingStarted.Eagerly, null)

    /** In-memory key: [item] is anything that changes whenever the icon may have changed. */
    private data class MemoryKey(
        val packageName: String,
        val item: Any,
        val sizePx: Int,
        val themed: Boolean,
    )

    private val cache = LruCache<MemoryKey, LauncherIcon>(MAX_ICONS)
    private val masks = MaskStore(File(application.cacheDir, "icon-masks"))

    /** Packages that handle `https` links as browsers; their pinned Shortcuts are PWAs. */
    @Volatile private var browsers: Set<String>? = null

    init {
        appScope.launch {
            appsRepository.changedPackages.collect { packageName ->
                browsers = null
                cache
                    .snapshot()
                    .keys
                    .filter { it.packageName == packageName }
                    .forEach(cache::remove)
                withContext(Dispatchers.IO) { masks.invalidate(packageName) }
            }
        }
    }

    fun cached(
        app: LauncherApp,
        sizePx: Int,
    ): LauncherIcon? = themedIcons.value?.let { cache[app.memoryKey(sizePx, it)] }

    suspend fun appIcon(
        app: LauncherApp,
        sizePx: Int,
    ): LauncherIcon {
        val themed = themed()
        val key = app.memoryKey(sizePx, themed)
        return cache[key] ?: load(key) { activityIcon(app.info, sizePx, themed) }
    }

    fun cached(
        shortcut: PinnedShortcut,
        sizePx: Int,
    ): LauncherIcon? = themedIcons.value?.let { cache[shortcut.info.memoryKey(sizePx, pinned = true, it)] }

    /** A pinned Shortcut's own icon; null when it has none. */
    suspend fun pinnedShortcutIcon(
        shortcut: PinnedShortcut,
        sizePx: Int,
        densityDpi: Int,
    ): LauncherIcon? = shortcutIcon(shortcut.info, sizePx, densityDpi, pinned = true)

    /** The icon of the app owning [packageName] in [user], for the small badge on a pinned Shortcut. */
    suspend fun ownerIcon(
        packageName: String,
        user: UserHandle,
        sizePx: Int,
    ): LauncherIcon? {
        val themed = themed()
        val key = MemoryKey(packageName, "owner/$user", sizePx, themed)
        return cache[key] ?: withContext(Dispatchers.IO) {
            launcherApps.getActivityList(packageName, user).firstOrNull()?.let { info -> load(key) { activityIcon(info, sizePx, themed) } }
        }
    }

    /** A Shortcut's own icon (from an app's long-press menu unless [pinned]); null when it has none. */
    suspend fun shortcutIcon(
        shortcut: ShortcutInfo,
        sizePx: Int,
        densityDpi: Int,
        pinned: Boolean = false,
    ): LauncherIcon? {
        val themed = themed()
        val key = shortcut.memoryKey(sizePx, pinned, themed)
        return cache[key] ?: withContext(Dispatchers.IO) {
            val drawable = launcherApps.getShortcutIconDrawable(shortcut, densityDpi) ?: return@withContext null
            val owner = if (pinned && shortcut.`package` in browserPackages()) IconOwner.Pwa else IconOwner.Shortcut
            val maskKey =
                MaskKey(
                    shortcut.`package`,
                    item = "shortcut/$owner/${userManager.getSerialNumberForUser(shortcut.userHandle)}/${shortcut.id}",
                    version = shortcut.lastChangedTimestamp.toString(),
                    sizePx = sizePx,
                )
            load(key) { render(drawable, owner, maskKey, themed) }
        }
    }

    private suspend fun load(
        key: MemoryKey,
        render: () -> LauncherIcon,
    ): LauncherIcon = withContext(Dispatchers.IO) { render().also { cache.put(key, it) } }

    private suspend fun themed(): Boolean = themedIcons.filterNotNull().first()

    private fun activityIcon(
        info: LauncherActivityInfo,
        sizePx: Int,
        themed: Boolean,
    ): LauncherIcon {
        val packageName = info.componentName.packageName
        val version =
            try {
                val pkg = application.packageManager.getPackageInfo(packageName, 0)
                // lastUpdateTime too, so a reinstall with the same versionCode (debug builds) is new.
                "${pkg.longVersionCode}/${pkg.lastUpdateTime}"
            } catch (_: PackageManager.NameNotFoundException) {
                "unknown"
            }
        val maskKey =
            MaskKey(
                packageName,
                item = "${info.componentName.className}/${userManager.getSerialNumberForUser(info.user)}",
                version = version,
                sizePx = sizePx,
            )
        // The disk cache spares decoding the icon at all.
        if (themed) masks.read(maskKey)?.let { return it.themed() }
        return render(info.getIcon(0), IconOwner.App, maskKey, themed)
    }

    private fun render(
        drawable: Drawable,
        owner: IconOwner,
        key: MaskKey,
        themed: Boolean,
    ): LauncherIcon {
        val source = if (themed) maskSource(owner, MonochromeIconFactory.traits(drawable)) else MaskSource.Original
        if (source ==
            MaskSource.Original
        ) {
            return LauncherIcon.Original(MonochromeIconFactory.original(drawable, key.sizePx).asImageBitmap())
        }
        masks.read(key)?.let { return it.themed() }
        val mask =
            when (source) {
                MaskSource.MonochromeLayer -> MonochromeIconFactory.monochromeLayer(drawable, key.sizePx)
                MaskSource.Generated -> null
            } ?: MonochromeIconFactory.generatedMonochrome(drawable, key.sizePx)
        masks.write(key, mask)
        return mask.themed()
    }

    private fun Bitmap.themed() = LauncherIcon.Themed(asImageBitmap())

    /** Packages resolving `VIEW https` with `CATEGORY_BROWSABLE` for any host (PRD 8.2). */
    private fun browserPackages(): Set<String> =
        browsers ?: application.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_VIEW, "https://".toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            ).mapTo(mutableSetOf()) { it.activityInfo.packageName }
            .also { browsers = it }

    private fun LauncherApp.memoryKey(
        sizePx: Int,
        themed: Boolean,
    ) = MemoryKey(packageName, this, sizePx, themed)

    /** A pinned Shortcut may be a PWA; the same Shortcut in an app's menu never is. */
    private fun ShortcutInfo.memoryKey(
        sizePx: Int,
        pinned: Boolean,
        themed: Boolean,
    ) = MemoryKey(`package`, listOf(userHandle, id, lastChangedTimestamp, pinned), sizePx, themed)

    private companion object {
        const val MAX_ICONS = 300
    }
}
