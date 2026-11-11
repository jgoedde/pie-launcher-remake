package io.github.jgoedde.pielauncher.core.drawer

/** How long a New Badge lasts if its Drawer Entry is never launched. */
const val NEW_BADGE_LIFETIME_MILLIS: Long = 7 * 24 * 60 * 60 * 1000L

/**
 * The New Badge of the Drawer Entry identified by [entryKey], added at [addedAtMillis]. Launching
 * the entry deletes it; otherwise it expires after [NEW_BADGE_LIFETIME_MILLIS].
 */
data class NewBadge(
    val entryKey: String,
    val addedAtMillis: Long,
)

/** The entry keys whose badge is still showing at [nowMillis]. */
fun List<NewBadge>.activeAt(nowMillis: Long): Set<String> =
    filter { nowMillis - it.addedAtMillis < NEW_BADGE_LIFETIME_MILLIS }.mapTo(mutableSetOf()) { it.entryKey }
