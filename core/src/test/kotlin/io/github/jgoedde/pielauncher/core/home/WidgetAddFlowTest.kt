package io.github.jgoedde.pielauncher.core.home

import io.github.jgoedde.pielauncher.core.home.WidgetAddFlow.Effect
import io.github.jgoedde.pielauncher.core.home.WidgetAddFlow.Event
import io.github.jgoedde.pielauncher.core.home.WidgetZoneId.HomeUpper
import org.junit.Assert.assertEquals
import org.junit.Test

/** The widget add flow (PRD 7.4): find the spot, bind, configure, place, accept a Pin Request. */
class WidgetAddFlowTest {
    private val plain = AddableWidget(hasConfigureActivity = false, configurationOptional = false, reconfigurable = false)
    private val configurable = plain.copy(hasConfigureActivity = true)
    private val tap = WidgetAddTarget.Tap(zone = null)
    private val pin = WidgetAddTarget.Pin(handle = 9, WidgetDrop(HOME_PAGE, HomeUpper, CellRect(0, 0, 2, 1)))

    @Test
    fun `a widget the launcher may bind and that needs no configure is placed and added`() {
        val home = FakeHome()

        home.start(WidgetAddFlow(tap, plain))

        assertEquals(WidgetAddOutcome.Added(widgetId = 1, page = HOME_PAGE), home.outcome)
        assertEquals(setOf(1), home.placed)
    }

    @Test
    fun `a widget the user lets the launcher bind in the system dialog is added`() {
        val home = FakeHome(bindAllowed = false)

        home.start(WidgetAddFlow(tap, plain))
        assertEquals(listOf("bind"), home.screens)
        home.answer(Event.BindAnswered(granted = true))

        assertEquals(WidgetAddOutcome.Added(widgetId = 1, page = HOME_PAGE), home.outcome)
    }

    @Test
    fun `cancelling the bind dialog deletes the id`() {
        val home = FakeHome(bindAllowed = false)

        home.start(WidgetAddFlow(tap, plain))
        home.answer(Event.BindAnswered(granted = false))

        home.assertLeftNothing(WidgetAddOutcome.Cancelled)
    }

    @Test
    fun `a widget that needs configuring shows its pending preview while the configure screen is up, then is added`() {
        val home = FakeHome()

        home.start(WidgetAddFlow(tap, configurable))
        assertEquals(listOf("configure"), home.screens)
        assertEquals(1, home.pending)
        home.answer(Event.Configured(ok = true))

        assertEquals(WidgetAddOutcome.Added(widgetId = 1, page = HOME_PAGE), home.outcome)
        assertEquals(null, home.pending)
    }

    @Test
    fun `configure runs unless the provider marks it both optional and reconfigurable`() {
        fun screensFor(widget: AddableWidget) = FakeHome().apply { start(WidgetAddFlow(tap, widget)) }.screens

        assertEquals(emptyList<String>(), screensFor(plain))
        assertEquals(listOf("configure"), screensFor(configurable.copy(configurationOptional = true)))
        assertEquals(listOf("configure"), screensFor(configurable.copy(reconfigurable = true)))
        assertEquals(emptyList<String>(), screensFor(configurable.copy(configurationOptional = true, reconfigurable = true)))
    }

    @Test
    fun `cancelling the configure screen deletes the id and the pending preview`() {
        val home = FakeHome()

        home.start(WidgetAddFlow(tap, configurable))
        home.answer(Event.Configured(ok = false))

        home.assertLeftNothing(WidgetAddOutcome.Cancelled)
    }

    @Test
    fun `a configure screen that cannot start is a cancel`() {
        val home = FakeHome(configureStarts = false)

        home.start(WidgetAddFlow(tap, configurable))

        home.assertLeftNothing(WidgetAddOutcome.Cancelled)
    }

    @Test
    fun `the launcher never configures a pinned widget, and accepts its Pin Request once placed`() {
        val home = FakeHome()

        home.start(WidgetAddFlow(pin, configurable))

        assertEquals(emptyList<String>(), home.screens)
        assertEquals(listOf(1), home.accepted)
        assertEquals(WidgetAddOutcome.Added(widgetId = 1, page = HOME_PAGE), home.outcome)
    }

    @Test
    fun `nothing is bound when there is no room, or the widget is too large`() {
        val noRoom = FakeHome(spot = rejected(Rejection.NoRoom)).apply { start(WidgetAddFlow(tap, plain)) }
        val tooLarge = FakeHome(spot = rejected(Rejection.TooLarge)).apply { start(WidgetAddFlow(tap, plain)) }

        noRoom.assertLeftNothing(WidgetAddOutcome.NoRoom)
        assertEquals(0, noRoom.allocations)
        tooLarge.assertLeftNothing(WidgetAddOutcome.TooLarge)
        assertEquals(0, tooLarge.allocations)
    }

    @Test
    fun `a provider or Pin Request gone before the add starts is a cancel`() {
        val home = FakeHome()

        home.start(WidgetAddFlow(pin, widget = null))

        home.assertLeftNothing(WidgetAddOutcome.Cancelled)
        assertEquals(0, home.allocations)
    }

    @Test
    fun `a spot taken while the bind dialog was up is no room, and the id is deleted`() {
        val home = FakeHome(bindAllowed = false)

        home.start(WidgetAddFlow(tap, configurable))
        home.spot = rejected(Rejection.NoRoom)
        home.answer(Event.BindAnswered(granted = true))

        assertEquals(listOf("bind"), home.screens)
        home.assertLeftNothing(WidgetAddOutcome.NoRoom)
    }

    @Test
    fun `a spot taken while the configure screen was up is no room, and the id and pending preview are deleted`() {
        val home = FakeHome()

        home.start(WidgetAddFlow(tap, configurable))
        home.spot = rejected(Rejection.NoRoom)
        home.answer(Event.Configured(ok = true))

        home.assertLeftNothing(WidgetAddOutcome.NoRoom)
    }

    @Test
    fun `a Pin Request that cannot be accepted takes the placed widget back out`() {
        val home = FakeHome(acceptSucceeds = false)

        home.start(WidgetAddFlow(pin, plain))

        home.assertLeftNothing(WidgetAddOutcome.Cancelled)
    }

    @Test
    fun `a Pin Request is not accepted when the widget finds no room`() {
        val home = FakeHome(bindAllowed = false)

        home.start(WidgetAddFlow(pin, plain))
        home.spot = rejected(Rejection.NoRoom)
        home.answer(Event.BindAnswered(granted = true))

        assertEquals(emptyList<Int>(), home.accepted)
        home.assertLeftNothing(WidgetAddOutcome.NoRoom)
    }

    @Test
    fun `an id the startup reconcile deleted after process death ends the add as a cancel`() {
        val before = FakeHome()
        before.start(WidgetAddFlow(tap, configurable))
        val saved = before.flow.state.encode()

        // A fresh process: the reconcile deleted the unplaced id.
        val after = FakeHome()
        after.resume(WidgetAddFlow(tap, configurable, WidgetAddFlow.State.decode(saved)!!))
        after.answer(Event.Configured(ok = true))

        after.assertLeftNothing(WidgetAddOutcome.Cancelled)
    }

    @Test
    fun `the flow resumes a bind dialog or configure screen after a recreate`() {
        val home = FakeHome(bindAllowed = false)
        home.start(WidgetAddFlow(tap, configurable))
        home.resume(WidgetAddFlow(tap, configurable, WidgetAddFlow.State.decode(home.flow.state.encode())!!))
        home.answer(Event.BindAnswered(granted = true))
        home.resume(WidgetAddFlow(tap, configurable, WidgetAddFlow.State.decode(home.flow.state.encode())!!))
        home.answer(Event.Configured(ok = true))

        assertEquals(WidgetAddOutcome.Added(widgetId = 1, page = HOME_PAGE), home.outcome)
    }

    @Test
    fun `a saved state that is not one decodes to null`() {
        listOf(null, "", "configuring", "configuring x", "configuring 1 2", "nope 1").forEach {
            assertEquals(it, null, WidgetAddFlow.State.decode(it))
        }
    }

    @Test
    fun `the trampoline destroyed mid-flow deletes the id and the pending preview`() {
        val inBindDialog = FakeHome(bindAllowed = false).apply { start(WidgetAddFlow(tap, plain)) }
        val inConfigure = FakeHome().apply { start(WidgetAddFlow(tap, configurable)) }

        inBindDialog.destroy()
        inConfigure.destroy()

        inBindDialog.assertLeftNothing(expected = null)
        inConfigure.assertLeftNothing(expected = null)
    }

    @Test
    fun `the trampoline destroyed after the add leaves the widget`() {
        val home = FakeHome().apply { start(WidgetAddFlow(tap, plain)) }

        home.destroy()

        assertEquals(setOf(1), home.placed)
        assertEquals(setOf(1), home.allocated)
    }

    /**
     * Runs the flow's effects the way the add trampoline does, against an in-memory Home: ids,
     * the pending preview and the placed widgets. A system screen (bind dialog, configure screen)
     * stays up until the test answers it with [answer].
     */
    private class FakeHome(
        /** What every look for a spot, and every placement, finds. */
        var spot: PagePlacement = roomAt(HomeUpper),
        var bindAllowed: Boolean = true,
        var configureStarts: Boolean = true,
        var acceptSucceeds: Boolean = true,
    ) {
        private var nextId = 1
        val allocated = mutableSetOf<Int>()
        val placed = mutableSetOf<Int>()
        var pending: Int? = null
        var outcome: WidgetAddOutcome? = null
        val screens = mutableListOf<String>()

        /** The widgets whose Pin Request was accepted. */
        val accepted = mutableListOf<Int>()
        var allocations = 0
        lateinit var flow: WidgetAddFlow

        fun start(flow: WidgetAddFlow) {
            this.flow = flow
            run(flow.start())
        }

        /** Picks up [flow], restored from a saved state, where the last one left off. */
        fun resume(flow: WidgetAddFlow) {
            this.flow = flow
        }

        fun answer(event: Event) = run(flow.on(event))

        /** The add ended with [expected], leaving no id, no pending preview and no placed widget behind. */
        fun assertLeftNothing(expected: WidgetAddOutcome?) {
            assertEquals(expected, outcome)
            assertEquals(emptySet<Int>(), allocated)
            assertEquals(null, pending)
            assertEquals(emptySet<Int>(), placed)
        }

        /** Home clears the launcher task, destroying the trampoline. */
        fun destroy() = answer(Event.Destroyed)

        private fun run(effects: List<Effect>) {
            for (effect in effects) {
                when (effect) {
                    is Effect.FindSpot -> answer(Event.SpotFound(spot))
                    is Effect.Bind -> {
                        val id = nextId++
                        allocations++
                        allocated += id
                        if (bindAllowed) {
                            answer(Event.Bound(id))
                        } else {
                            screens += "bind"
                            answer(Event.AskingToBind(id))
                        }
                    }
                    is Effect.ShowPending -> pending = effect.id
                    is Effect.Configure ->
                        if (configureStarts) screens += "configure" else answer(Event.Configured(ok = false))
                    is Effect.Place ->
                        if (effect.id !in allocated) {
                            answer(Event.IdGone)
                        } else {
                            if (spot.result is LayoutResult.Changed) {
                                placed += effect.id
                                if (pending == effect.id) pending = null
                            }
                            answer(Event.Placed(spot))
                        }
                    is Effect.Accept -> {
                        if (acceptSucceeds) accepted += effect.id
                        answer(Event.Accepted(acceptSucceeds))
                    }
                    is Effect.DeleteUnplaced -> {
                        allocated -= effect.id
                        if (pending == effect.id) pending = null
                    }
                    is Effect.RemovePlaced -> {
                        placed -= effect.id
                        allocated -= effect.id
                    }
                    is Effect.Finish -> outcome = effect.outcome
                }
            }
        }
    }

    private companion object {
        fun roomAt(zone: WidgetZoneId): PagePlacement {
            val widget = LaidOutWidget(-1, zone, CellRect(0, 0, 2, 1), SpanBounds.fixed(CellSpan(2, 1)))
            return PagePlacement(PageZone(HOME_PAGE, zone), LayoutResult.Changed(homePage(widget), widget))
        }

        fun rejected(reason: Rejection) = PagePlacement(null, LayoutResult.Rejected(reason))
    }
}
