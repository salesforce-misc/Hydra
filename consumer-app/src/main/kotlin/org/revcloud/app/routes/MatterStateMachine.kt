package org.revcloud.app.routes

import mu.KotlinLogging
import org.revcloud.hydra.Hydra
import org.revcloud.hydra.statemachine.Transition

class MatterStateMachine {

    private val logger = KotlinLogging.logger {}
    private val stateMachine = Hydra.create {
        initialState(State.Solid)
        state<State.Solid> {
            on<Event.OnMelted> {
                transitionTo(State.Liquid, SideEffect.LogMelted)
            }
        }
        state<State.Liquid> {
            on<Event.OnFrozen> {
                transitionTo(State.Solid, SideEffect.LogFrozen)
            }
            on<Event.OnVaporized> {
                transitionTo(State.Gas, SideEffect.LogVaporized)
            }
        }
        state<State.Gas> {
            on<Event.OnCondensed> {
                transitionTo(State.Liquid, SideEffect.LogCondensed)
            }
        }
        onTransition {
            val validTransition = it as? Transition.Valid ?: return@onTransition
            when (validTransition.sideEffect) {
                SideEffect.LogMelted -> logger.info { ON_MELTED_MESSAGE }
                SideEffect.LogFrozen -> logger.info { ON_FROZEN_MESSAGE }
                SideEffect.LogVaporized -> logger.info { ON_VAPORIZED_MESSAGE }
                SideEffect.LogCondensed -> logger.info { ON_CONDENSED_MESSAGE }
            }
        }
    }

    companion object {
        const val ON_MELTED_MESSAGE = "I melted"
        const val ON_FROZEN_MESSAGE = "I froze"
        const val ON_VAPORIZED_MESSAGE = "I vaporized"
        const val ON_CONDENSED_MESSAGE = "I condensed"

        sealed class State {
            object Solid : State()
            object Liquid : State()
            object Gas : State()
        }

        sealed class Event {
            object OnMelted : Event()
            object OnFrozen : Event()
            object OnVaporized : Event()
            object OnCondensed : Event()
        }

        sealed class SideEffect {
            object LogMelted : SideEffect()
            object LogFrozen : SideEffect()
            object LogVaporized : SideEffect()
            object LogCondensed : SideEffect()
        }
    }
}