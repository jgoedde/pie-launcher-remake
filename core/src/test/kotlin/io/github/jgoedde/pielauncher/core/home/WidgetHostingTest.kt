package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetHostingTest {
    @Test
    fun `reconcile deletes host ids with no layout row and rows with no host id`() {
        val result = WidgetReconcile.of(hostIds = setOf(1, 2, 3), layoutIds = setOf(2, 3, 4, 5))

        assertEquals(setOf(1), result.orphanHostIds)
        assertEquals(setOf(4, 5), result.orphanRows)
    }

    @Test
    fun `reconcile changes nothing when host and layout agree`() {
        val result = WidgetReconcile.of(hostIds = setOf(7, 8), layoutIds = setOf(8, 7))

        assertTrue(result.orphanHostIds.isEmpty())
        assertTrue(result.orphanRows.isEmpty())
    }

    @Test
    fun `configure is skipped only when the provider marks it both optional and reconfigurable`() {
        assertFalse(WidgetConfigure.isNeeded(hasConfigureActivity = false, configurationOptional = false, reconfigurable = false))
        assertTrue(WidgetConfigure.isNeeded(hasConfigureActivity = true, configurationOptional = false, reconfigurable = false))
        assertTrue(WidgetConfigure.isNeeded(hasConfigureActivity = true, configurationOptional = true, reconfigurable = false))
        assertTrue(WidgetConfigure.isNeeded(hasConfigureActivity = true, configurationOptional = false, reconfigurable = true))
        assertFalse(WidgetConfigure.isNeeded(hasConfigureActivity = true, configurationOptional = true, reconfigurable = true))
    }
}
