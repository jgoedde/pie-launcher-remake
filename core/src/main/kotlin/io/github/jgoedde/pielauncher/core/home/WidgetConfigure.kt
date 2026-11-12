package io.github.jgoedde.pielauncher.core.home

/** Launcher3's configure rule (PRD 7.4). */
object WidgetConfigure {
    /** A configure activity runs on add unless the provider sets both `configuration_optional` and `reconfigurable`. */
    fun isNeeded(
        hasConfigureActivity: Boolean,
        configurationOptional: Boolean,
        reconfigurable: Boolean,
    ): Boolean = hasConfigureActivity && !(configurationOptional && reconfigurable)
}
