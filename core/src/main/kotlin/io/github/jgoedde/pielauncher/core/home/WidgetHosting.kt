package io.github.jgoedde.pielauncher.core.home

/**
 * The startup reconcile of the widget layout against the host (PRD 7.1): ids bound to the host
 * with no layout row leak and must be deleted; rows whose id the host no longer knows are dropped.
 */
data class WidgetReconcile(
    val orphanHostIds: Set<Int>,
    val orphanRows: Set<Int>,
) {
    companion object {
        fun of(
            hostIds: Set<Int>,
            layoutIds: Set<Int>,
        ) = WidgetReconcile(orphanHostIds = hostIds - layoutIds, orphanRows = layoutIds - hostIds)
    }
}

/** Launcher3's configure rule (PRD 7.4). */
object WidgetConfigure {
    /** A configure activity runs on add unless the provider sets both `configuration_optional` and `reconfigurable`. */
    fun isNeeded(
        hasConfigureActivity: Boolean,
        configurationOptional: Boolean,
        reconfigurable: Boolean,
    ): Boolean = hasConfigureActivity && !(configurationOptional && reconfigurable)
}
