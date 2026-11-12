# Widgets reflow like Launcher3

Status: accepted (supersedes "No reflow" in PRD 7.3 and issue #9)

Moving or resizing a widget pushes neighbouring widgets aside, as AOSP Launcher3's `ReorderAlgorithm` does. If nothing fits, the dragged widget shrinks down to its minimum size, and the drop is refused only if it still does not fit. We first chose spring-back with no reflow because it is simpler. The owner wants Launcher3 behaviour, and a 4-column grid fills up fast without reflow. Reflow happens within one Widget Zone only. Nothing is ever pushed into or across the Pie Zone. The algorithm lives in pure Kotlin in `:core` (ADR 0001), so it can be unit-tested without a device.
