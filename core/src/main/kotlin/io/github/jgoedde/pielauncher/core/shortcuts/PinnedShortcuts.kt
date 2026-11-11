package io.github.jgoedde.pielauncher.core.shortcuts

/** Identifies one Shortcut: its app's package, the app-chosen id and the user (profile) serial. */
data class ShortcutKey(
    val packageName: String,
    val id: String,
    val userSerial: Long,
)

/** What accepting a Pin Request did to the App Drawer. */
enum class PinOutcome {
    /** A new Drawer Entry appears (with a New Badge). */
    Added,

    /** The Shortcut already has a Drawer Entry; only its label and icon are refreshed. */
    AlreadyPinned,
}

/** The outcome of pinning [key] given the [stored] pinned Shortcut records. */
fun pinOutcome(
    stored: Collection<ShortcutKey>,
    key: ShortcutKey,
): PinOutcome = if (key in stored) PinOutcome.AlreadyPinned else PinOutcome.Added

/** A pinned Shortcut record kept by the launcher, with the label last seen for it. */
data class StoredPin(
    val key: ShortcutKey,
    val label: String,
)

/** A pinned Shortcut as the system reports it right now; [shortcut] is the platform object. */
data class LivePin<S>(
    val key: ShortcutKey,
    val label: String,
    val enabled: Boolean,
    val shortcut: S,
)

/** One pinned Shortcut as the Inventory lists it. */
data class PinRecord(
    val key: ShortcutKey,
    val label: String,
    val disabled: Boolean,
)

/** Pinned Shortcuts reconciled from the launcher's records and the system's live lookup. */
data class MergedPins<S>(
    /** Enabled pins, shown as Drawer Entries. */
    val inDrawer: List<S>,
    /** Every known pin, for the Inventory: the stored records first, then pins found only live. */
    val records: List<PinRecord>,
    /** Records to insert or update: live pins that have none yet, or whose label changed. */
    val toStore: List<StoredPin>,
)

/**
 * Reconciles the [stored] records with the [live] pinned Shortcuts.
 *
 * A stored record missing from [live] is kept as it is, never dropped: right after boot the lookup
 * can come back empty although the pins still exist (GrapheneOS Launcher3 race).
 */
fun <S> mergePins(
    stored: List<StoredPin>,
    live: List<LivePin<S>>,
): MergedPins<S> {
    val liveByKey = live.associateBy { it.key }
    val storedKeys = stored.mapTo(mutableSetOf()) { it.key }
    val storedRecords =
        stored.map { record ->
            val now = liveByKey[record.key]
            PinRecord(record.key, now?.label ?: record.label, disabled = now?.enabled == false)
        }
    val liveOnly = live.filter { it.key !in storedKeys }
    return MergedPins(
        inDrawer = live.filter { it.enabled }.map { it.shortcut },
        records = storedRecords + liveOnly.map { PinRecord(it.key, it.label, disabled = !it.enabled) },
        toStore =
            stored.mapNotNull { record ->
                liveByKey[record.key]?.takeIf { it.label != record.label }?.let { StoredPin(record.key, it.label) }
            } + liveOnly.map { StoredPin(it.key, it.label) },
    )
}

/**
 * The ids to pass to `pinShortcuts(package, ids, user)` when [removing] is unpinned: every other
 * pin of its package and user, from the [stored] records and the [live] lookup combined. The
 * records keep the list complete when the lookup came back short; an empty result therefore means
 * [removing] really was the package's last pin.
 */
fun idsToKeepPinned(
    removing: ShortcutKey,
    stored: Collection<ShortcutKey>,
    live: Collection<ShortcutKey>,
): List<String> =
    (stored + live)
        .filter { it.packageName == removing.packageName && it.userSerial == removing.userSerial && it != removing }
        .map { it.id }
        .distinct()
