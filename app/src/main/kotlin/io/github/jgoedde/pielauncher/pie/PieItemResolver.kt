package io.github.jgoedde.pielauncher.pie

import android.app.Application
import android.content.ComponentName
import android.content.pm.ShortcutInfo
import dev.zacsweers.metro.Inject
import io.github.jgoedde.pielauncher.core.config.Layer
import io.github.jgoedde.pielauncher.core.config.MaterialSymbols
import io.github.jgoedde.pielauncher.core.config.PieColor
import io.github.jgoedde.pielauncher.core.config.PieConfig
import io.github.jgoedde.pielauncher.core.config.PieItem
import io.github.jgoedde.pielauncher.platform.AppShortcuts
import io.github.jgoedde.pielauncher.platform.LauncherApp

/** A Pie Item matched against what is on the device right now. */
sealed interface ResolvedItem {
    /**
     * An app item whose target is launchable: [app] is what a release starts; [shortcuts] is its
     * Shortcut list, empty when it has none.
     */
    data class App(
        val app: LauncherApp,
        val shortcuts: List<ShortcutInfo>,
    ) : ResolvedItem

    /** A Shortcut item whose Shortcut is on the device and enabled. */
    data class Shortcut(
        val shortcut: ShortcutInfo,
    ) : ResolvedItem

    /** A Layer Switch: the target Layer's Material Symbol ([symbol], a codepoint) on its colour. */
    data class LayerSwitch(
        val color: PieColor,
        val symbol: Int,
    ) : ResolvedItem

    /** The target is missing or disabled: greyed out, release does nothing. */
    data object Unavailable : ResolvedItem
}

/** What a release starts. */
sealed interface LaunchTarget {
    data class App(
        val app: LauncherApp,
    ) : LaunchTarget

    data class Shortcut(
        val shortcut: ShortcutInfo,
    ) : LaunchTarget
}

/** The active Pie Config with every Pie Item resolved. The config itself is never edited. */
data class ResolvedPie(
    val config: PieConfig,
    val items: Map<PieItem, ResolvedItem>,
) {
    fun resolve(item: PieItem): ResolvedItem = items[item] ?: ResolvedItem.Unavailable

    fun isAvailable(item: PieItem): Boolean = resolve(item) !is ResolvedItem.Unavailable
}

/**
 * Matches Pie Items against the launchable apps and Shortcuts (PRD 9.4). An app item names its
 * activity, or else stands for what `getLaunchIntentForPackage` returns; its Shortcut list is the
 * activity's manifest and dynamic Shortcuts, capped like the App Drawer menu. A Shortcut item
 * matches a pinned, manifest or dynamic Shortcut of the main profile.
 */
@Inject
class PieItemResolver(
    private val application: Application,
    private val appShortcuts: AppShortcuts,
) {
    suspend fun resolve(
        config: PieConfig,
        apps: List<LauncherApp>,
    ): ResolvedPie {
        val byComponent = apps.associateBy { it.componentName }
        val items =
            config.layers.flatMap { it.items }.distinct().associateWith { item ->
                when (item) {
                    is PieItem.App ->
                        launchTarget(item)?.let(byComponent::get)?.let { ResolvedItem.App(it, appShortcuts.of(it)) }
                            ?: ResolvedItem.Unavailable
                    is PieItem.LayerSwitch -> {
                        val target = config.layer(item.layer)
                        ResolvedItem.LayerSwitch(
                            color = target?.effectiveColor ?: PieColor.Role.Primary,
                            // Import validation only lets known names in; the default always exists.
                            symbol = target?.effectiveIcon?.let(MaterialSymbols::codepoint) ?: DEFAULT_SYMBOL,
                        )
                    }
                    is PieItem.Shortcut ->
                        appShortcuts.find(item.packageName, item.shortcutId)?.let(ResolvedItem::Shortcut)
                            ?: ResolvedItem.Unavailable
                }
            }
        return ResolvedPie(config, items)
    }

    private fun launchTarget(item: PieItem.App): ComponentName? =
        item.activity?.let { ComponentName(item.packageName, it) }
            ?: application.packageManager.getLaunchIntentForPackage(item.packageName)?.component

    private companion object {
        val DEFAULT_SYMBOL: Int = checkNotNull(MaterialSymbols.codepoint(Layer.DEFAULT_ICON))
    }
}
