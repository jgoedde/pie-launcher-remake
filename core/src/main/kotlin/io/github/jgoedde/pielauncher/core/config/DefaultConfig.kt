package io.github.jgoedde.pielauncher.core.config

/** One launcher activity of the main profile, as the Default Config sees it. */
data class LaunchableApp(
    val packageName: String,
    val activity: String,
    val label: String,
)

/**
 * The Default Config (PRD 9.6): one Base Layer `home` in `primary` holding the default apps, then
 * other launchable apps alphabetically by label until there are [MAX_ITEMS] items.
 */
object DefaultConfig {
    const val MAX_ITEMS = 8
    const val BASE_LAYER = "home"

    /**
     * [defaults] are the packages the default-app intents resolved to (dialer, messaging, camera,
     * browser, Settings), null where one resolved to nothing; they are kept when launchable, once
     * each. An item names its activity only when its package has several launcher activities, so
     * a single-activity app follows whatever `getLaunchIntentForPackage` returns. Null when
     * [launchable] is empty: a Layer needs at least one item.
     */
    fun build(
        defaults: List<String?>,
        launchable: List<LaunchableApp>,
        labelOrder: Comparator<String> = String.CASE_INSENSITIVE_ORDER,
    ): PieConfig? {
        val byPackage = launchable.groupBy { it.packageName }
        val defaultPackages = defaults.filterNotNull().filter { it in byPackage }.distinct()
        val fill =
            launchable
                .filter { it.packageName !in defaultPackages }
                .sortedWith(compareBy(labelOrder) { it: LaunchableApp -> it.label }.thenBy { it.activity })
                .map { PieItem.App(it.packageName, it.activity.takeIf { _ -> byPackage.getValue(it.packageName).size > 1 }) }
        val items = (defaultPackages.map { PieItem.App(it) } + fill).take(MAX_ITEMS)
        if (items.isEmpty()) return null
        return PieConfig(
            version = PieConfigCodec.SUPPORTED_VERSION,
            baseLayer = BASE_LAYER,
            layers = listOf(Layer(id = BASE_LAYER, name = "Home", color = PieColor.Role.Primary.key, items = items)),
        )
    }
}
