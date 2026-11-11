package io.github.jgoedde.pielauncher.pie

import android.app.Application
import android.content.ComponentName
import dev.zacsweers.metro.Inject
import io.github.jgoedde.pielauncher.core.config.PieColor
import io.github.jgoedde.pielauncher.core.config.PieConfig
import io.github.jgoedde.pielauncher.core.config.PieItem
import io.github.jgoedde.pielauncher.platform.LauncherApp

/** A Pie Item matched against what is on the device right now. */
sealed interface ResolvedItem {
    /** An app item whose target is launchable: [app] is what a release starts. */
    data class App(
        val app: LauncherApp,
    ) : ResolvedItem

    /** A Layer Switch, drawn on the target Layer's colour. */
    data class LayerSwitch(
        val color: PieColor,
    ) : ResolvedItem

    /** The target is missing or disabled: greyed out, release does nothing. */
    data object Unavailable : ResolvedItem
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
 * Matches Pie Items against the launchable apps (PRD 9.4). An app item names its activity, or
 * else stands for what `getLaunchIntentForPackage` returns. Shortcut items stay unavailable until
 * Pie Shortcuts are resolved (#30).
 */
@Inject
class PieItemResolver(
    private val application: Application,
) {
    fun resolve(
        config: PieConfig,
        apps: List<LauncherApp>,
    ): ResolvedPie {
        val byComponent = apps.associateBy { it.componentName }
        val items =
            config.layers.flatMap { it.items }.distinct().associateWith { item ->
                when (item) {
                    is PieItem.App -> launchTarget(item)?.let(byComponent::get)?.let(ResolvedItem::App) ?: ResolvedItem.Unavailable
                    is PieItem.LayerSwitch -> ResolvedItem.LayerSwitch(config.layer(item.layer)?.effectiveColor ?: PieColor.Role.Primary)
                    is PieItem.Shortcut -> ResolvedItem.Unavailable
                }
            }
        return ResolvedPie(config, items)
    }

    private fun launchTarget(item: PieItem.App): ComponentName? =
        item.activity?.let { ComponentName(item.packageName, it) }
            ?: application.packageManager.getLaunchIntentForPackage(item.packageName)?.component
}
