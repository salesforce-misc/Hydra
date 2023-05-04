package org.revcloud.hydra.config

import org.revcloud.hydra.internal.Graph
import org.revcloud.hydra.internal.Matcher
import org.revcloud.hydra.internal.Graph.State
import org.revcloud.hydra.internal.Graph.State.TransitionTo
import org.revcloud.hydra.statemachine.Transition
import java.util.function.Consumer

class ConfigBuilder<StateT : Any, EventT : Any, SideEffectT : Any>(
  graph: Graph<StateT, EventT, SideEffectT>? = null
) {
  private var initialState = graph?.initialState
  private val stateDefinitions = LinkedHashMap(graph?.stateDefinitions ?: emptyMap())
  private val onTransitionListeners = ArrayList(graph?.onTransitionListeners ?: emptyList())

  fun initialState(initialState: StateT) {
    this.initialState = initialState
  }

  fun <S : StateT> state(
    stateMatcher: Matcher<StateT, S>,
    init: Consumer<StateDefinitionBuilder<S>>
  ) {
    stateDefinitions[stateMatcher] = StateDefinitionBuilder<S>().apply { init.accept(this) }.build()
  }

  inline fun <reified S : StateT> state(init: Consumer<StateDefinitionBuilder<S>>) {
    state(Matcher.any(), init)
  }

  inline fun <reified S : StateT> state(state: S, noinline init: StateDefinitionBuilder<S>.() -> Unit) {
    state(Matcher.eq<StateT, S>(state), init)
  }

  fun onTransition(listener: Consumer<Transition<StateT, EventT, SideEffectT>>) {
    onTransitionListeners.add(listener)
  }

  fun build(): Graph<StateT, EventT, SideEffectT> {
    return Graph(requireNotNull(initialState), stateDefinitions.toMap(), onTransitionListeners.toList())
  }

  inner class StateDefinitionBuilder<S : StateT> {

    private val stateDefinition = State<StateT, EventT, SideEffectT>()

    inline fun <reified E : EventT> any(): Matcher<EventT, E> = Matcher.any()

    inline fun <reified R : EventT> eq(value: R): Matcher<EventT, R> = Matcher.eq(value)

    fun <E : EventT> on(
      eventMatcher: Matcher<EventT, E>,
      createTransitionTo: S.(E) -> TransitionTo<StateT, SideEffectT>
    ) {
      stateDefinition.transitions[eventMatcher] = { state, event ->
        @Suppress("UNCHECKED_CAST")
        createTransitionTo((state as S), event as E)
      }
    }

    inline fun <reified E : EventT> on(
      noinline createTransitionTo: S.(E) -> TransitionTo<StateT, SideEffectT>
    ) = on(any(), createTransitionTo)

    inline fun <reified E : EventT> on(
      event: E,
      noinline createTransitionTo: S.(E) -> TransitionTo<StateT, SideEffectT>
    ) = on(eq(event), createTransitionTo)

    fun onEnter(listener: (S, EventT) -> Unit) = with(stateDefinition) {
      onEnterListeners.add { state, cause ->
        @Suppress("UNCHECKED_CAST")
        listener(state as S, cause)
      }
    }

    fun onExit(listener: (S, EventT) -> Unit) = with(stateDefinition) {
      onExitListeners.add { state, cause ->
        @Suppress("UNCHECKED_CAST")
        listener(state as S, cause)
      }
    }

    fun build() = stateDefinition

    fun S.transitionTo(state: StateT, sideEffect: SideEffectT? = null): TransitionTo<StateT, SideEffectT> =
      TransitionTo(state, sideEffect)

    fun S.dontTransition(sideEffect: SideEffectT? = null): TransitionTo<StateT, SideEffectT> = transitionTo(this, sideEffect)
  }
}
