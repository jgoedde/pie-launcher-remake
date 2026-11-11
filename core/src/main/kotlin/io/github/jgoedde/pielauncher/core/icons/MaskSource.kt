package io.github.jgoedde.pielauncher.core.icons

/** Whose icon is drawn; decides the mask source together with [IconTraits]. */
enum class IconOwner {
    /** A launchable app (adaptive, or a legacy bitmap wrapped to adaptive). */
    App,

    /** A Shortcut that is not a PWA. */
    Shortcut,

    /** A pinned Shortcut owned by a browser package. */
    Pwa,
}

/** What the launcher can see of an icon drawable. */
data class IconTraits(
    /** The adaptive icon has a monochrome layer. */
    val hasMonochrome: Boolean,
    /** A plain bitmap, or an adaptive icon whose foreground is a bitmap. */
    val isBitmap: Boolean,
)

/** Where a Themed Icon's mask comes from, or that the icon stays an Original Icon. */
enum class MaskSource {
    /** The icon's own monochrome layer. */
    MonochromeLayer,

    /** A Generated Monochrome computed from the full-colour icon. */
    Generated,

    /** No mask: the icon is drawn in its own colours (still clipped to the Icon Shape). */
    Original,
}

/** The mask source per PRD 8.2. */
fun maskSource(
    owner: IconOwner,
    traits: IconTraits,
): MaskSource =
    when {
        owner == IconOwner.Pwa -> MaskSource.Generated
        traits.hasMonochrome -> MaskSource.MonochromeLayer
        owner == IconOwner.Shortcut && traits.isBitmap -> MaskSource.Original
        else -> MaskSource.Generated
    }
