package io.github.jgoedde.pielauncher.core.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPreviewSourceTest {
    @Test
    fun `a generated preview wins over the provider's preview layout and image`() {
        val source = WidgetPreviewSource.of(PreviewFacts(generated = true, layout = true, image = true))

        assertEquals(WidgetPreviewSource.Generated, source)
    }

    @Test
    fun `without a generated preview the preview layout wins over the image`() {
        assertEquals(WidgetPreviewSource.Layout, WidgetPreviewSource.of(PreviewFacts(layout = true, image = true)))
    }

    @Test
    fun `the preview image is used when it is all the provider has`() {
        assertEquals(WidgetPreviewSource.Image, WidgetPreviewSource.of(PreviewFacts(image = true)))
    }

    @Test
    fun `a widget with no preview at all gets a placeholder`() {
        assertEquals(WidgetPreviewSource.Placeholder, WidgetPreviewSource.of(PreviewFacts()))
    }

    @Test
    fun `an app-supplied preview of a Pin Request wins over everything`() {
        val source = WidgetPreviewSource.of(PreviewFacts(appSupplied = true, generated = true, layout = true, image = true))

        assertEquals(WidgetPreviewSource.AppSupplied, source)
    }
}
