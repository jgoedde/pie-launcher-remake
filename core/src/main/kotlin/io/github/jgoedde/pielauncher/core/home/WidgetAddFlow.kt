package io.github.jgoedde.pielauncher.core.home

/** What the widget add flow needs to know of the widget's provider (PRD 7.4). */
data class AddableWidget(
    val hasConfigureActivity: Boolean,
    val configurationOptional: Boolean,
    val reconfigurable: Boolean,
) {
    /** Launcher3's rule: a configure activity runs on add unless the provider sets both `configuration_optional` and `reconfigurable`. */
    val needsConfigure: Boolean get() = hasConfigureActivity && !(configurationOptional && reconfigurable)
}

/**
 * The widget add flow (PRD 7.4) behind the add trampoline and the pin sheet: find the spot first (nothing is bound
 * or configured that cannot go anywhere) → allocate an id and bind → configure if needed, with the
 * pending preview in the target cells → place, re-checking the spot → accept the Pin Request, if
 * any, only once placed. Driven by [Event]s, it answers with [Effect]s for its host to run.
 * Every exit but [WidgetAddOutcome.Added] deletes the unplaced id and its pending preview.
 *
 * For the pin sheet ([WidgetAddTarget.PinSheet]) the spot found is the sheet's "Add to home
 * screen", offered enabled or disabled with the reason; the flow goes on only once the user adds,
 * and a cancelled bind dialog deletes the id and offers again, keeping the sheet (Launcher3).
 */
class WidgetAddFlow(
    private val target: WidgetAddTarget,
    /** Null: the provider or Pin Request is gone; the flow ends at once. */
    private val widget: AddableWidget?,
    state: State = State.Start,
) {
    var state: State = state
        private set

    private val pinned get() = target is WidgetAddTarget.Pin || target is WidgetAddTarget.PinSheet
    private val sheet get() = target is WidgetAddTarget.PinSheet

    /**
     * The effects that start the flow, or pick it up after a recreate: a lookup or placement whose
     * coroutine died with the old host runs again; a system screen's result is still on the way.
     */
    fun start(): List<Effect> =
        when (val state = state) {
            State.Start -> {
                if (widget == null) {
                    finish(WidgetAddOutcome.Cancelled)
                } else {
                    findSpot()
                }
            }
            State.FindingSpot, is State.Offering -> findSpot()
            is State.CheckingSpot -> listOf(Effect.FindSpot(target, state.id))
            is State.Placing -> listOf(Effect.Place(state.id, target))
            State.Binding, is State.AskingToBind, is State.Configuring, is State.Accepting, State.Done -> emptyList()
        }

    fun on(event: Event): List<Effect> {
        val state = state
        return when (event) {
            is Event.SpotFound ->
                when (state) {
                    State.FindingSpot -> {
                        val spot = event.placement.result
                        if (sheet) return offer(spot)
                        if (spot !is LayoutResult.Changed) return finish(spot.outcome())
                        this.state = State.Binding
                        listOf(Effect.Bind(spot.widget.cells.span))
                    }
                    // The spot may have been taken while the bind dialog was up.
                    is State.CheckingSpot -> {
                        val spot = event.placement.result
                        val page = event.placement.zone?.page
                        if (spot !is LayoutResult.Changed || page == null) return abandon(state.id, spot.outcome())
                        this.state = State.Configuring(state.id)
                        listOf(Effect.ShowPending(state.id, page, spot), Effect.Configure(state.id))
                    }
                    else -> emptyList()
                }
            is Event.Configured ->
                when {
                    state !is State.Configuring -> emptyList()
                    event.ok -> place(state.id)
                    else -> abandon(state.id, WidgetAddOutcome.Cancelled)
                }
            is Event.Accepted ->
                when {
                    state !is State.Accepting -> emptyList()
                    event.ok -> finish(WidgetAddOutcome.Added(state.id, state.page))
                    else -> listOf(Effect.RemovePlaced(state.id)) + finish(WidgetAddOutcome.Cancelled)
                }
            Event.AddChosen -> {
                val ready = (state as? State.Offering)?.add as? PinAdd.Ready ?: return emptyList()
                this.state = State.Binding
                listOf(Effect.Bind(ready.span))
            }
            is Event.Bound -> if (state == State.Binding) bound(event.id) else emptyList()
            is Event.AskingToBind -> {
                if (state == State.Binding) this.state = State.AskingToBind(event.id)
                emptyList()
            }
            is Event.BindAnswered ->
                when {
                    state !is State.AskingToBind -> emptyList()
                    event.granted -> bound(state.id)
                    // The sheet stays: the user may add again, or drag.
                    sheet -> listOf(Effect.DeleteUnplaced(state.id)) + findSpot()
                    else -> abandon(state.id, WidgetAddOutcome.Cancelled)
                }
            is Event.Placed ->
                when (state) {
                    is State.Placing -> {
                        val placed = event.placement.result
                        val page = event.placement.zone?.page
                        if (placed !is LayoutResult.Changed || page == null) return abandon(state.id, placed.outcome())
                        if (!pinned) return finish(WidgetAddOutcome.Added(state.id, page))
                        // Accepted only once placed, so the app never hears "added" for a widget that found no room.
                        this.state = State.Accepting(state.id, page)
                        listOf(Effect.Accept(state.id, placed.widget.cells.span))
                    }
                    else -> emptyList()
                }
            Event.IdGone -> state.id?.let { abandon(it, WidgetAddOutcome.Cancelled) }.orEmpty()
            // Destroyed without a result: nothing to finish, only to clean up.
            Event.Destroyed -> {
                this.state = State.Done
                when (state) {
                    is State.Accepting -> listOf(Effect.RemovePlaced(state.id))
                    else -> listOfNotNull(state.id?.let(Effect::DeleteUnplaced))
                }
            }
        }
    }

    private fun findSpot(): List<Effect> {
        state = State.FindingSpot
        return listOf(Effect.FindSpot(target))
    }

    private fun offer(spot: LayoutResult): List<Effect> {
        val add =
            when (spot) {
                is LayoutResult.Changed -> PinAdd.Ready(spot.widget.cells.span)
                is LayoutResult.Rejected -> PinAdd.Disabled(if (spot.reason == Rejection.TooLarge) Rejection.TooLarge else Rejection.NoRoom)
                LayoutResult.Unchanged -> PinAdd.Disabled(Rejection.NoRoom)
            }
        state = State.Offering(add)
        return listOf(Effect.Offer(add))
    }

    private fun bound(id: Int): List<Effect> {
        // The platform contract: the launcher never configures a pinned widget (the app does).
        if (pinned || widget?.needsConfigure != true) return place(id)
        state = State.CheckingSpot(id)
        return listOf(Effect.FindSpot(target, id))
    }

    private fun place(id: Int): List<Effect> {
        state = State.Placing(id)
        return listOf(Effect.Place(id, target))
    }

    /** Ends with [outcome], deleting the unplaced widget [id] and its pending preview. */
    private fun abandon(
        id: Int,
        outcome: WidgetAddOutcome,
    ): List<Effect> = listOf(Effect.DeleteUnplaced(id)) + finish(outcome)

    private fun finish(outcome: WidgetAddOutcome): List<Effect> {
        state = State.Done
        return listOf(Effect.Finish(outcome))
    }

    private fun LayoutResult.outcome(): WidgetAddOutcome =
        when ((this as? LayoutResult.Rejected)?.reason) {
            Rejection.TooLarge -> WidgetAddOutcome.TooLarge
            null -> WidgetAddOutcome.Cancelled
            else -> WidgetAddOutcome.NoRoom
        }

    /** Where the flow is; [encode] it into the saved state, so a system screen's result finds the flow after process death. */
    sealed interface State {
        /** The widget id allocated and not given up yet, if any. */
        val id: Int? get() = null

        fun encode(): String =
            when (this) {
                is AskingToBind -> "$ASKING_TO_BIND $id"
                is CheckingSpot -> "$CHECKING_SPOT $id"
                is Configuring -> "$CONFIGURING $id"
                is Placing -> "$PLACING $id"
                is Accepting -> "$ACCEPTING $id $page"
                Start -> START
                // Looked up again on restore: the room may have changed meanwhile.
                FindingSpot, is Offering -> FINDING_SPOT
                Binding -> BINDING
                Done -> DONE
            }

        companion object {
            private const val START = "start"
            private const val FINDING_SPOT = "finding_spot"
            private const val BINDING = "binding"
            private const val ASKING_TO_BIND = "asking_to_bind"
            private const val CHECKING_SPOT = "checking_spot"
            private const val CONFIGURING = "configuring"
            private const val PLACING = "placing"
            private const val ACCEPTING = "accepting"
            private const val DONE = "done"

            /** The state [encode] gave [encoded]; null for anything else. */
            fun decode(encoded: String?): State? {
                val words = encoded?.split(' ') ?: return null
                val ints = words.drop(1).map { it.toIntOrNull() ?: return null }
                return when (words.first()) {
                    START -> Start.takeIf { ints.isEmpty() }
                    FINDING_SPOT -> FindingSpot.takeIf { ints.isEmpty() }
                    BINDING -> Binding.takeIf { ints.isEmpty() }
                    DONE -> Done.takeIf { ints.isEmpty() }
                    ASKING_TO_BIND -> ints.singleOrNull()?.let(::AskingToBind)
                    CHECKING_SPOT -> ints.singleOrNull()?.let(::CheckingSpot)
                    CONFIGURING -> ints.singleOrNull()?.let(::Configuring)
                    PLACING -> ints.singleOrNull()?.let(::Placing)
                    ACCEPTING -> if (ints.size == 2) Accepting(ints[0], ints[1]) else null
                    else -> null
                }
            }
        }

        data object Start : State

        data object FindingSpot : State

        /** The pin sheet shows, offering [add]. */
        data class Offering(
            val add: PinAdd,
        ) : State

        /** An id is being allocated and bound. */
        data object Binding : State

        /** The system bind dialog asks the user about widget [id]. */
        data class AskingToBind(
            override val id: Int,
        ) : State

        /** Bound widget [id] needs configuring; its spot is looked up again for the pending preview. */
        data class CheckingSpot(
            override val id: Int,
        ) : State

        /** The configure screen of widget [id] is up, its pending preview in the target cells. */
        data class Configuring(
            override val id: Int,
        ) : State

        data class Placing(
            override val id: Int,
        ) : State

        /** Widget [id] is on [page]; its Pin Request is being accepted. */
        data class Accepting(
            override val id: Int,
            val page: Int,
        ) : State

        data object Done : State
    }

    /** What happened, as the host reports it. */
    sealed interface Event {
        /** The user chose the pin sheet's "Add to home screen". */
        data object AddChosen : Event

        data class SpotFound(
            val placement: PagePlacement,
        ) : Event

        /** Widget [id] is allocated and bound. */
        data class Bound(
            val id: Int,
        ) : Event

        /** Widget [id] is allocated; the launcher may not bind it, so the system bind dialog is up. */
        data class AskingToBind(
            val id: Int,
        ) : Event

        /** The bind dialog closed: [granted] or not. */
        data class BindAnswered(
            val granted: Boolean,
        ) : Event

        /** The configure screen closed: [ok], or cancelled or failed to start. */
        data class Configured(
            val ok: Boolean,
        ) : Event

        data class Placed(
            val placement: PagePlacement,
        ) : Event

        /** The Pin Request was accepted, or not ([ok] false: invalid or failed). */
        data class Accepted(
            val ok: Boolean,
        ) : Event

        /** The widget id is no longer bound (the process died and the startup reconcile deleted it). */
        data object IdGone : Event

        /** The host was destroyed without finishing (declined, or Home cleared the launcher task). */
        data object Destroyed : Event
    }

    /** What the host does next; each answers with the [Event] its doc names. */
    sealed interface Effect {
        /**
         * Look up where a widget of the provider would go at [target]: [Event.SpotFound]. With the
         * bound widget's [id], [Event.IdGone] instead when that id is no longer bound.
         */
        data class FindSpot(
            val target: WidgetAddTarget,
            val id: Int? = null,
        ) : Effect

        /**
         * Allocate an id and bind it with the size options of [span]: [Event.Bound], or start the
         * system bind dialog and send [Event.AskingToBind], then [Event.BindAnswered].
         */
        data class Bind(
            val span: CellSpan,
        ) : Effect

        /** Show the pin sheet, offering [add]: [Event.AddChosen] once the user adds. */
        data class Offer(
            val add: PinAdd,
        ) : Effect

        /** Show widget [id] as a pending preview at [spot] on [page]. */
        data class ShowPending(
            val id: Int,
            val page: Int,
            val spot: LayoutResult.Changed,
        ) : Effect

        /** Start the configure screen of widget [id]: [Event.Configured]. */
        data class Configure(
            val id: Int,
        ) : Effect

        /** Place widget [id] at [target], re-checking its spot: [Event.Placed], or [Event.IdGone]. */
        data class Place(
            val id: Int,
            val target: WidgetAddTarget,
        ) : Effect

        /** Accept the Pin Request for placed widget [id] at [span]: [Event.Accepted]. */
        data class Accept(
            val id: Int,
            val span: CellSpan,
        ) : Effect

        /** Delete unplaced widget [id] and its pending preview. */
        data class DeleteUnplaced(
            val id: Int,
        ) : Effect

        /** Take placed widget [id] back out: its row and its id. */
        data class RemovePlaced(
            val id: Int,
        ) : Effect

        /** End the add with [outcome]. */
        data class Finish(
            val outcome: WidgetAddOutcome,
        ) : Effect
    }
}
