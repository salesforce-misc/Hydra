/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra.config

import com.salesforce.hydra.internal.Machine
import com.salesforce.hydra.internal.Machine.State
import com.salesforce.hydra.internal.Machine.State.TransitionTo
import com.salesforce.hydra.internal.Matcher
import com.salesforce.hydra.statemachine.Transition
import java.util.function.BiConsumer
import java.util.function.Consumer

class MachineBuilder<StateT : Any, EventT : Any, ActionT : Any>(
  machine: Machine<StateT, EventT, ActionT>? = null
) {
  private var initialState = machine?.initialState
  private val stateDefinitions = LinkedHashMap(machine?.stateDefinitions ?: emptyMap())
  private val onTransitionListeners = ArrayList(machine?.onTransitionListeners ?: emptyList())

  fun initialState(initialState: StateT) {
    this.initialState = initialState
  }

  fun <S : StateT> state(
    stateMatcher: Matcher<StateT, S>,
    init: Consumer<StateDefinitionBuilder<S>>
  ) {
    stateDefinitions[stateMatcher] = StateDefinitionBuilder<S>().apply { init.accept(this) }.build()
  }

  fun <S : StateT> state(
    stateMatcher: Matcher<StateT, S>,
    init: StateDefinitionBuilder<S>.() -> Unit
  ) {
    stateDefinitions[stateMatcher] = StateDefinitionBuilder<S>().apply(init).build()
  }

  inline fun <reified S : StateT> state(noinline init: StateDefinitionBuilder<S>.() -> Unit) {
    state(Matcher.any(), init)
  }

  fun <S : StateT> state(clazz: Class<S>, init: Consumer<StateDefinitionBuilder<S>>) {
    state(Matcher.any(clazz), init)
  }

  inline fun <reified S : StateT> state(
    state: S,
    noinline init: StateDefinitionBuilder<S>.() -> Unit
  ) {
    state(Matcher.eq<StateT, S>(state), init)
  }

  fun <S : StateT> state(state: S, clazz: Class<S>, init: Consumer<StateDefinitionBuilder<S>>) {
    state(Matcher.eq(state, clazz), init)
  }

  fun onTransition(listener: Consumer<Transition<StateT, EventT, ActionT>>) {
    onTransitionListeners.add(listener)
  }

  fun build(): Machine<StateT, EventT, ActionT> {
    return Machine(
      requireNotNull(initialState),
      stateDefinitions.toMap(),
      onTransitionListeners.toList()
    )
  }

  inner class StateDefinitionBuilder<S : StateT> {

    private val stateDefinition = State<StateT, EventT, ActionT>()

    fun <E : EventT> any(eventClass: Class<E>): Matcher<EventT, E> = Matcher.any(eventClass)

    inline fun <reified E : EventT> any(): Matcher<EventT, E> = Matcher.any()

    fun <R : EventT> eq(value: R, eventClass: Class<R>): Matcher<EventT, R> =
      Matcher.eq(value, eventClass)

    inline fun <reified R : EventT> eq(value: R): Matcher<EventT, R> = Matcher.eq(value)

    fun <E : EventT> on(
      eventMatcher: Matcher<EventT, E>,
      createTransitionTo: S?.(E) -> TransitionTo<StateT, ActionT>
    ) {
      stateDefinition.transitions[eventMatcher] = { state, event ->
        @Suppress("UNCHECKED_CAST") createTransitionTo((state as S?), event as E)
      }
    }

    fun <E : EventT> on(
      eventClass: Class<E>,
      createTransitionTo: S?.(E) -> TransitionTo<StateT, ActionT>
    ) = on(any(eventClass), createTransitionTo)

    inline fun <reified E : EventT> on(
      noinline createTransitionTo: S?.(E) -> TransitionTo<StateT, ActionT>
    ) = on(any(), createTransitionTo)

    fun <E : EventT> on(
      event: E,
      eventClass: Class<E>,
      createTransitionTo: S?.(E) -> TransitionTo<StateT, ActionT>
    ) = on(eq(event, eventClass), createTransitionTo)

    inline fun <reified E : EventT> on(
      event: E,
      noinline createTransitionTo: S?.(E) -> TransitionTo<StateT, ActionT>
    ) = on(eq(event), createTransitionTo)

    fun onEnter(listener: BiConsumer<S, EventT>) =
      with(stateDefinition) {
        onEnterListeners.add { state, cause ->
          @Suppress("UNCHECKED_CAST") listener.accept(state as S, cause)
        }
      }

    fun onExit(listener: BiConsumer<S, EventT>) =
      with(stateDefinition) {
        onExitListeners.add { state, cause ->
          @Suppress("UNCHECKED_CAST") listener.accept(state as S, cause)
        }
      }

    fun build() = stateDefinition

    @JvmOverloads
    fun transitionTo(state: StateT, action: ActionT? = null): TransitionTo<StateT, ActionT> =
      TransitionTo(state, action)

    fun S.dontTransition(action: ActionT? = null): TransitionTo<StateT, ActionT> =
      transitionTo(this, action)
  }
}
