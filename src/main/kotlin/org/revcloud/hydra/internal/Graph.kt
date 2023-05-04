package org.revcloud.hydra.internal

import org.revcloud.hydra.statemachine.Transition
import java.util.function.Consumer

data class Graph<StateT : Any, EventT : Any, SideEffectT : Any>(
  val initialState: StateT,
  val stateDefinitions: Map<Matcher<StateT, StateT>, State<StateT, EventT, SideEffectT>>,
  val onTransitionListeners: List<Consumer<Transition<StateT, EventT, SideEffectT>>>
  ) {
  
  class State<StateT : Any, EventT : Any, SideEffectT : Any> internal constructor() {
    val onEnterListeners = mutableListOf<(StateT, EventT) -> Unit>()
    val onExitListeners = mutableListOf<(StateT, EventT) -> Unit>()
    val transitions = linkedMapOf<Matcher<EventT, EventT>, (StateT, EventT) -> TransitionTo<StateT, SideEffectT>>()

    data class TransitionTo<out StateT : Any, out SideEffectT : Any> internal constructor(
      val toState: StateT,
      val sideEffect: SideEffectT?
    )
  }
}
