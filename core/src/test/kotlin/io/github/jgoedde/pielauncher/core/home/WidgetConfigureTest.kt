package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConfigureTest {
    @Test
    fun `configure is skipped only when the provider marks it both optional and reconfigurable`() {
        assertFalse(WidgetConfigure.isNeeded(hasConfigureActivity = false, configurationOptional = false, reconfigurable = false))
        assertTrue(WidgetConfigure.isNeeded(hasConfigureActivity = true, configurationOptional = false, reconfigurable = false))
        assertTrue(WidgetConfigure.isNeeded(hasConfigureActivity = true, configurationOptional = true, reconfigurable = false))
        assertTrue(WidgetConfigure.isNeeded(hasConfigureActivity = true, configurationOptional = false, reconfigurable = true))
        assertFalse(WidgetConfigure.isNeeded(hasConfigureActivity = true, configurationOptional = true, reconfigurable = true))
    }
}
