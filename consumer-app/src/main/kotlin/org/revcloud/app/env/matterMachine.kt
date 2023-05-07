package org.revcloud.app.env

import kotlinx.serialization.Serializable

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
