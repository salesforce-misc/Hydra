package org.revcloud.app.routes

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.revcloud.hydra.Hydra
import org.revcloud.hydra.statemachine.Transition

private val logger = KotlinLogging.logger {}
val matterMachine = Hydra.create<Matter, Action, SideEffect> {
    initialState(Matter.Solid)
    state<Matter.Solid> {
        on<Action.OnMelted> {
            transitionTo(Matter.Liquid, SideEffect.LogMelted)
        }
    }
    state<Matter.Liquid> {
        on<Action.OnFrozen> {
            transitionTo(Matter.Solid, SideEffect.LogFrozen)
        }
        on<Action.OnVaporized> {
            transitionTo(Matter.Gas, SideEffect.LogVaporized)
        }
    }
    state<Matter.Gas> {
        on<Action.OnCondensed> {
            transitionTo(Matter.Liquid, SideEffect.LogCondensed)
        }
    }
    onTransition {
        val validTransition = it as? Transition.Valid ?: return@onTransition
        when (validTransition.sideEffect) {
            SideEffect.LogMelted -> logger.info { ON_MELTED_MESSAGE }
            SideEffect.LogFrozen -> logger.info { ON_FROZEN_MESSAGE }
            SideEffect.LogVaporized -> logger.info { ON_VAPORIZED_MESSAGE }
            SideEffect.LogCondensed -> logger.info { ON_CONDENSED_MESSAGE }
            else -> logger.error { "Invalid SideEffect" }
        }
    }
}

const val ON_MELTED_MESSAGE = "I melted"
const val ON_FROZEN_MESSAGE = "I froze"
const val ON_VAPORIZED_MESSAGE = "I vaporized"
const val ON_CONDENSED_MESSAGE = "I condensed"

@Serializable
sealed class Matter {
    @Serializable
    object Solid : Matter()

    @Serializable
    object Liquid : Matter()

    @Serializable
    object Gas : Matter()
}

@Serializable
sealed class Action {
    @Serializable
    object OnMelted : Action()

    @Serializable
    object OnFrozen : Action()

    @Serializable
    object OnVaporized : Action()

    @Serializable
    object OnCondensed : Action()
}

@Serializable
sealed class SideEffect {
    @Serializable
    object LogMelted : SideEffect()

    @Serializable
    object LogFrozen : SideEffect()

    @Serializable
    object LogVaporized : SideEffect()

    @Serializable
    object LogCondensed : SideEffect()
}
