package io.github.jgoedde.pielauncher.core.home

/** The facts about a widget's view tree that background detection needs; the app adapts `View`. */
interface WidgetViewTree<V> {
    fun children(view: V): List<V>

    /** Whether the view's visibility is `VISIBLE`. */
    fun isVisible(view: V): Boolean

    /** Whether the view draws something itself: a background, a foreground, or not `willNotDraw`. */
    fun draws(view: V): Boolean

    /** Whether the view's id is `@android:id/background`. */
    fun hasBackgroundId(view: V): Boolean

    fun clipsToOutline(view: V): Boolean
}

/** A rectangle in pixels, in a widget host's coordinates; [right] and [bottom] are exclusive. */
data class OutlineBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Finds the view a widget's rounded corners are fitted to (Launcher3 `RoundedCornerEnforcement`).
 */
object WidgetBackground {
    /**
     * The background of the widget hosted in [host]:
     * 1. the single view with id `@android:id/background`;
     * 2. otherwise, from the host's first child, the first visible view that draws, or that has
     *    at least two visible children with content;
     * 3. otherwise, [host] itself.
     */
    fun <V> find(
        host: V,
        tree: WidgetViewTree<V>,
    ): V {
        val marked = mutableListOf<V>()
        collectMarked(host, tree, marked)
        if (marked.size == 1) return marked.single()
        val first = tree.children(host).firstOrNull() ?: return host
        return findUndefined(first, tree) ?: host
    }

    /** Whether the widget rounds its own corners: its [background] is marked and clips to its outline. */
    fun <V> optsOut(
        background: V,
        tree: WidgetViewTree<V>,
    ): Boolean = tree.hasBackgroundId(background) && tree.clipsToOutline(background)

    /**
     * The rounded-corner outline of a widget whose [background] has these bounds in its host's
     * coordinates: the background clamped to the host's [hostWidth] × [hostHeight], so a
     * background larger than the host (fixed-size content, e.g. DeskClock's analog clock preview)
     * never draws past the host. A background entirely outside the host leaves the whole host.
     */
    fun outline(
        background: OutlineBounds,
        hostWidth: Int,
        hostHeight: Int,
    ): OutlineBounds {
        val left = background.left.coerceIn(0, hostWidth)
        val top = background.top.coerceIn(0, hostHeight)
        val right = background.right.coerceIn(0, hostWidth)
        val bottom = background.bottom.coerceIn(0, hostHeight)
        return if (left < right && top < bottom) OutlineBounds(left, top, right, bottom) else OutlineBounds(0, 0, hostWidth, hostHeight)
    }

    private fun <V> collectMarked(
        view: V,
        tree: WidgetViewTree<V>,
        into: MutableList<V>,
    ) {
        if (tree.hasBackgroundId(view)) into += view
        tree.children(view).forEach { collectMarked(it, tree, into) }
    }

    private fun <V> findUndefined(
        view: V,
        tree: WidgetViewTree<V>,
    ): V? {
        if (!tree.isVisible(view)) return null
        if (tree.draws(view)) return view
        var found: V? = null
        for (child in tree.children(view)) {
            val visible = findUndefined(child, tree) ?: continue
            if (found != null) return view
            found = visible
        }
        return found
    }
}
