package io.github.jgoedde.pielauncher.core.inventory

import io.github.jgoedde.pielauncher.core.config.LaunchableApp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Instant

class InventoryTest {
    private val specDir = File(System.getProperty("pie.specDir"))

    private val exportedAt = Instant.parse("2026-11-11T14:02:00.250Z").toEpochMilli()

    private val apps =
        listOf(
            LaunchableApp("com.android.settings", "com.android.settings.Settings", "Settings"),
            LaunchableApp("com.android.dialer", "com.android.dialer.main.impl.MainActivity", "Phone"),
            LaunchableApp("md.obsidian", "md.obsidian.MainActivity", "Obsidian"),
            LaunchableApp("app.alextran.immich", "app.alextran.immich.MainActivity", "Immich"),
            LaunchableApp("org.mozilla.firefox", "org.mozilla.fenix.HomeActivity", "Firefox"),
            LaunchableApp("me.zhanghai.android.files", "me.zhanghai.android.files.filelist.FileListActivity", "Files"),
            LaunchableApp("com.android.settings", "com.android.settings.Settings\$DataUsageSummaryActivity", "Data usage"),
            LaunchableApp("com.google.android.deskclock", "com.android.deskclock.DeskClock", "Clock"),
            LaunchableApp("app.grapheneos.camera", "app.grapheneos.camera.ui.activities.MainActivity", "Camera"),
        )

    private val defaults = apps.filterNot { it.label == "Data usage" }.associate { it.packageName to it.activity }

    private val pinned =
        listOf(
            InventoryShortcut("org.thoughtcrime.securesms", "chat-17", "Old group", ShortcutKind.Pinned, disabled = true),
            InventoryShortcut("app.vanadium.browser", "3f2c9a4e-7b1d-4c5e-9f0a-2d6b8e1c4a73", "Music PWA", ShortcutKind.Pinned),
            InventoryShortcut("org.mozilla.firefox", "https://app.example.org/", "Example PWA", ShortcutKind.Pinned),
        )

    private val published =
        listOf(
            InventoryShortcut("org.mozilla.firefox", "private_browsing", "New private tab", ShortcutKind.Manifest),
            InventoryShortcut("md.obsidian", "recent-note-42", "Daily note", ShortcutKind.Dynamic),
        )

    private fun parse(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `the export matches the spec example`() {
        val text = Inventory.encode(exportedAt, apps, defaults, pinned, published)

        assertEquals(parse(File(specDir, "inventory.example.json").readText()), parse(text))
    }

    @Test
    fun `a pinned Shortcut the app also publishes is listed once, with the published kind`() {
        val pinnedToo = InventoryShortcut("md.obsidian", "recent-note-42", "Daily note", ShortcutKind.Pinned)
        val text = Inventory.encode(exportedAt, apps, defaults, pinned + pinnedToo, published)

        val shortcuts =
            parse(text)
                .jsonObject
                .getValue("shortcuts")
                .jsonArray
                .map { it.jsonObject }
        val daily = shortcuts.filter { it.getValue("shortcutId").jsonPrimitive.content == "recent-note-42" }
        assertEquals(1, daily.size)
        assertEquals(
            "dynamic",
            daily
                .single()
                .getValue("kind")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `Shortcuts with the same label are ordered by package, then id`() {
        val a = InventoryShortcut("app.vanadium.browser", "b-id", "Music", ShortcutKind.Pinned)
        val b = InventoryShortcut("app.vanadium.browser", "a-id", "Music", ShortcutKind.Pinned)
        val c = InventoryShortcut("app.aaa", "z-id", "Music", ShortcutKind.Pinned)

        val ids =
            parse(Inventory.encode(exportedAt, apps, defaults, listOf(a, b, c), emptyList()))
                .jsonObject
                .getValue("shortcuts")
                .jsonArray
                .map {
                    it.jsonObject
                        .getValue("shortcutId")
                        .jsonPrimitive.content
                }
        assertEquals(listOf("z-id", "a-id", "b-id"), ids)
    }
}
