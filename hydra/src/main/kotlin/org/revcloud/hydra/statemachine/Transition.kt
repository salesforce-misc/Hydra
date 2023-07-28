package org.revcloud.hydra.statemachine

sealed class Transition<out StateT : Any, out EventT : Any, out ActionT : Any> {
  abstract val event: EventT

  val isValid: Boolean
    get() = this is Valid

  companion object {
    @JvmStatic
    fun <StateT : Any, EventT : Any, ActionT : Any> valid(
      fromState: StateT?,
      event: EventT,
      toState: StateT,
      action: ActionT?
    ) = Valid(fromState, event, toState, action)

    @JvmStatic
    fun <StateT : Any, EventT : Any, ActionT : Any> invalid(
      fromState: StateT?,
      event: EventT,
    ) = Invalid(fromState, event)
  }

  data class Valid<out StateT : Any, out EventT : Any, out ActionT : Any>
  internal constructor(
    val fromState: StateT?,
    override val event: EventT,
    val toState: StateT,
    val action: ActionT?
  ) : Transition<StateT, EventT, ActionT>()

  data class Invalid<out StateT : Any, out EventT : Any>
  internal constructor(val fromState: StateT?, override val event: EventT) :
    Transition<StateT, EventT, Nothing>()

  data class NoFromState<out EventT : Any> internal constructor(override val event: EventT) :
    Transition<Nothing, EventT, Nothing>()
}
