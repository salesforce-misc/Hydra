package org.revcloud.hydra.internal

import java.util.function.Consumer
import org.revcloud.hydra.statemachine.Transition

data class Machine<StateT : Any, EventT : Any, ActionT : Any>(
  val initialState: StateT,
  val stateDefinitions: Map<Matcher<StateT, StateT>, State<StateT, EventT, ActionT>>,
  val onTransitionListeners: List<Consumer<Transition<StateT, EventT, ActionT>>>
) {

  class State<StateT : Any, EventT : Any, ActionT : Any> internal constructor() {
    val onEnterListeners = mutableListOf<(StateT, EventT) -> Unit>()
    val onExitListeners = mutableListOf<(StateT, EventT) -> Unit>()

    // ! TODO 01/06/23 gopala.akshintala: Create a different transition which doesn't take StateT
    // when it is null
    val transitions =
      linkedMapOf<Matcher<EventT, EventT>, (StateT?, EventT) -> TransitionTo<StateT, ActionT>>()

    fun getTransitionForEvent(event: EventT): Transition<StateT, EventT, ActionT> =
      transitions.entries
        .firstOrNull { (eventMatcher, _) -> eventMatcher.matches(event) }
        ?.let { (_, createTransitionTo) ->
          val (toState, action) = createTransitionTo(null, event)
          Transition.Valid(null, event, toState, action)
        }
        ?: Transition.Invalid(null, event)

    data class TransitionTo<out StateT : Any, out ActionT : Any>
    internal constructor(val toState: StateT, val action: ActionT?)
  }
}
