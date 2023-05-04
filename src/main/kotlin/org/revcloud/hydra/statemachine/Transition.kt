package org.revcloud.hydra.statemachine

@Suppress("UNUSED")
sealed class Transition<out StateT : Any, out EventT : Any, out SideEffectT : Any> {
  abstract val fromState: StateT
  abstract val event: EventT

  data class Valid<out StateT : Any, out EventT : Any, out SideEffectT : Any> internal constructor(
    override val fromState: StateT,
    override val event: EventT,
    val toState: StateT,
    val sideEffect: SideEffectT?
  ) : Transition<StateT, EventT, SideEffectT>()

  data class Invalid<out StateT : Any, out EventT : Any, out SideEffectT : Any> internal constructor(
    override val fromState: StateT,
    override val event: EventT
  ) : Transition<StateT, EventT, SideEffectT>()
}
