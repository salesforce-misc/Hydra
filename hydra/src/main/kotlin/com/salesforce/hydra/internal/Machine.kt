/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra.internal

import com.salesforce.hydra.statemachine.Transition
import java.util.function.Consumer

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
        } ?: Transition.Invalid(null, event)

    data class TransitionTo<out StateT : Any, out ActionT : Any>
    internal constructor(val toState: StateT, val action: ActionT?)
  }
}
