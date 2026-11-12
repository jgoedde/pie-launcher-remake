package io.github.jgoedde.pielauncher.core.config

/** The import warnings of PRD 9.3: problems that do not stop the file from being applied. */
internal class ImportWarnings(
    private val targets: DeviceTargets,
) {
    private val launcherActivities = targets.activities.map { it.packageName to it.activity }.toSet()
    private val activitiesByPackage = targets.activities.groupBy { it.packageName }

    fun of(config: PieConfig): List<ConfigWarning> {
        val reachable = reachable(config)
        return config.layers.flatMapIndexed { l, layer ->
            val path = "$.layers[$l]"
            buildList {
                if (layer.id !in reachable) {
                    add(ConfigWarning(path, "Layer \"${layer.id}\" is not reachable from the Base Layer \"${config.baseLayer}\""))
                }
                val firstIndex = mutableMapOf<PieItem, Int>()
                layer.items.forEachIndexed { i, item ->
                    (unresolved(item) ?: ambiguous(item))?.let { add(ConfigWarning("$path.items[$i]", it)) }
                    val first = firstIndex.getOrPut(target(item)) { i }
                    if (first != i) add(ConfigWarning("$path.items[$i]", "same target as $path.items[$first]"))
                }
            }
        }
    }

    /** The ids of the Base Layer and every Layer a chain of Layer Switches leads to from it. */
    private fun reachable(config: PieConfig): Set<String> {
        val seen = mutableSetOf(config.baseLayer)
        val queue = ArrayDeque(seen)
        while (queue.isNotEmpty()) {
            config.layer(queue.removeFirst())?.items?.filterIsInstance<PieItem.LayerSwitch>()?.forEach {
                if (seen.add(it.layer)) queue += it.layer
            }
        }
        return seen
    }

    /** What [item] launches: an app item without activity stands for its package's default activity. */
    private fun target(item: PieItem): PieItem =
        if (item is PieItem.App && item.activity == null) {
            targets.defaultActivities[item.packageName]?.let { item.copy(activity = it) } ?: item
        } else {
            item
        }

    /** An app item without activity in a package with several launcher activities: which one starts? */
    private fun ambiguous(item: PieItem): String? {
        if (item !is PieItem.App || item.activity != null) return null
        val all = activitiesByPackage[item.packageName].orEmpty()
        val launched = all.firstOrNull { it.activity == targets.defaultActivities[item.packageName] } ?: return null
        if (all.size < 2) return null
        val alternatives = (all - launched).sortedBy { it.label }.joinToString { "${it.activity} (${it.label})" }
        return "${item.packageName} has ${all.size} launcher activities and launches ${launched.activity} (${launched.label}); " +
            "set \"activity\" for another: $alternatives"
    }

    private fun unresolved(item: PieItem): String? =
        when (item) {
            is PieItem.App ->
                when {
                    item.activity != null ->
                        "activity ${item.packageName}/${item.activity} is not a launcher activity"
                            .takeUnless { item.packageName to item.activity in launcherActivities }
                    else ->
                        "app ${item.packageName} is not installed or does not start a launcher activity"
                            .takeUnless { item.packageName to targets.defaultActivities[item.packageName] in launcherActivities }
                }
            is PieItem.Shortcut ->
                "Shortcut \"${item.shortcutId}\" of ${item.packageName} not found"
                    .takeUnless { targets.shortcuts == null || ShortcutTarget(item.packageName, item.shortcutId) in targets.shortcuts }
            is PieItem.LayerSwitch -> null
        }
}
