package io.github.jgoedde.pielauncher.core.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedShortcutsTest {
    private val firefoxApp = ShortcutKey("org.mozilla.firefox", "https://app.example/", userSerial = 0)

    @Test
    fun `pinning an unknown Shortcut adds it`() {
        assertEquals(PinOutcome.Added, pinOutcome(stored = emptyList(), firefoxApp))
    }

    @Test
    fun `pinning the same id again reports it as already pinned`() {
        assertEquals(PinOutcome.AlreadyPinned, pinOutcome(stored = listOf(firefoxApp), firefoxApp))
    }

    @Test
    fun `the same id from another package or user is a different Shortcut`() {
        val stored = listOf(firefoxApp.copy(packageName = "app.vanadium.browser"), firefoxApp.copy(userSerial = 10))
        assertEquals(PinOutcome.Added, pinOutcome(stored, firefoxApp))
    }

    private val vanadiumApp = ShortcutKey("app.vanadium.browser", "0b6c-uuid", userSerial = 0)

    @Test
    fun `enabled live pins are shown in the Drawer with their live label`() {
        val merged =
            mergePins(
                stored = listOf(StoredPin(firefoxApp, "Old name")),
                live = listOf(LivePin(firefoxApp, "Example", enabled = true, shortcut = "firefox")),
            )
        assertEquals(listOf("firefox"), merged.inDrawer)
        assertEquals(listOf(PinRecord(firefoxApp, "Example", disabled = false)), merged.records)
    }

    @Test
    fun `a disabled pin leaves the Drawer but stays recorded as disabled`() {
        val merged =
            mergePins(
                stored = listOf(StoredPin(firefoxApp, "Example"), StoredPin(vanadiumApp, "Notes")),
                live =
                    listOf(
                        LivePin(firefoxApp, "Example", enabled = false, shortcut = "firefox"),
                        LivePin(vanadiumApp, "Notes", enabled = true, shortcut = "vanadium"),
                    ),
            )
        assertEquals(listOf("vanadium"), merged.inDrawer)
        assertEquals(PinRecord(firefoxApp, "Example", disabled = true), merged.records.single { it.key == firefoxApp })
    }

    @Test
    fun `an empty lookup at boot keeps every stored record`() {
        val merged =
            mergePins<String>(
                stored = listOf(StoredPin(firefoxApp, "Example"), StoredPin(vanadiumApp, "Notes")),
                live = emptyList(),
            )
        assertEquals(emptyList<String>(), merged.inDrawer)
        assertEquals(
            listOf(PinRecord(firefoxApp, "Example", disabled = false), PinRecord(vanadiumApp, "Notes", disabled = false)),
            merged.records,
        )
        assertEquals(emptyList<StoredPin>(), merged.toStore)
    }

    @Test
    fun `live pins without a record are shown and get recorded`() {
        val merged =
            mergePins(
                stored = emptyList(),
                live = listOf(LivePin(vanadiumApp, "Notes", enabled = true, shortcut = "vanadium")),
            )
        assertEquals(listOf("vanadium"), merged.inDrawer)
        assertEquals(listOf(StoredPin(vanadiumApp, "Notes")), merged.toStore)
    }

    @Test
    fun `a changed live label updates the record`() {
        val merged =
            mergePins(
                stored = listOf(StoredPin(firefoxApp, "Old name")),
                live = listOf(LivePin(firefoxApp, "Example", enabled = true, shortcut = "firefox")),
            )
        assertEquals(listOf(StoredPin(firefoxApp, "Example")), merged.toStore)
    }

    private val firefoxOther = firefoxApp.copy(id = "https://other.example/")
    private val firefoxThird = firefoxApp.copy(id = "https://third.example/")

    @Test
    fun `removing keeps the package's other pins pinned`() {
        assertEquals(
            listOf(firefoxOther.id),
            idsToKeepPinned(removing = firefoxApp, stored = listOf(firefoxApp, firefoxOther, vanadiumApp), live = listOf(firefoxApp)),
        )
    }

    @Test
    fun `a pin known only from the live lookup is kept too`() {
        assertEquals(
            listOf(firefoxOther.id, firefoxThird.id),
            idsToKeepPinned(removing = firefoxApp, stored = listOf(firefoxApp, firefoxOther), live = listOf(firefoxThird)),
        )
    }

    @Test
    fun `an empty live lookup does not empty the list`() {
        assertEquals(
            listOf(firefoxOther.id),
            idsToKeepPinned(removing = firefoxApp, stored = listOf(firefoxApp, firefoxOther), live = emptyList()),
        )
    }

    @Test
    fun `pins of the same package in another user are not touched`() {
        assertEquals(
            emptyList<String>(),
            idsToKeepPinned(removing = firefoxApp, stored = listOf(firefoxApp, firefoxOther.copy(userSerial = 10)), live = emptyList()),
        )
    }
}
