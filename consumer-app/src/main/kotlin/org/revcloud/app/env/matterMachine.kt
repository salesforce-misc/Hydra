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
    object Melt : Action()

    @Serializable
    object Freeze : Action()

    @Serializable
    object Vaporize : Action()

    @Serializable
    object Condense : Action()
}

@Serializable
sealed class SideEffect {
    @Serializable
    object Melted : SideEffect()

    @Serializable
    object Frozen : SideEffect()

    @Serializable
    object Vaporized : SideEffect()

    @Serializable
    object Condensed : SideEffect()
}
