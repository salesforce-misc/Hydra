package org.revcloud.hydra.config

import java.util.function.BiConsumer
import java.util.function.Consumer
import org.revcloud.hydra.internal.Machine
import org.revcloud.hydra.internal.Machine.State
import org.revcloud.hydra.internal.Machine.State.TransitionTo
import org.revcloud.hydra.internal.Matcher
import org.revcloud.hydra.statemachine.Transition

class MachineBuilder<StateT : Any, EventT : Any, SideEffectT : Any>(
  machine: Machine<StateT, EventT, SideEffectT>? = null
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

  fun <S : StateT> state(clazz: Class<S>, init: Consumer<StateDefinitionBuilder<S>>) {
    state(Matcher.any(clazz), init)
  }

  inline fun <reified S : StateT> state(noinline init: StateDefinitionBuilder<S>.() -> Unit) {
    state(Matcher.any(), init)
  }

  fun <S : StateT> state(state: S, clazz: Class<S>, init: StateDefinitionBuilder<S>.() -> Unit) {
    state(Matcher.eq(state, clazz), init)
  }

  fun onTransition(listener: Consumer<Transition<StateT, EventT, SideEffectT>>) {
    onTransitionListeners.add(listener)
  }

  fun onTransition(listener: (Transition<StateT, EventT, SideEffectT>) -> Unit) {
    onTransitionListeners.add(listener)
  }

  fun build(): Machine<StateT, EventT, SideEffectT> {
    return Machine(requireNotNull(initialState), stateDefinitions.toMap(), onTransitionListeners.toList())
  }

  inner class StateDefinitionBuilder<S : StateT> {

    private val stateDefinition = State<StateT, EventT, SideEffectT>()

    fun <E : EventT> any(eventClass: Class<E>): Matcher<EventT, E> = Matcher.any(eventClass)

    inline fun <reified E : EventT> any(): Matcher<EventT, E> = Matcher.any()

    fun <R : EventT> eq(value: R, eventClass: Class<R>): Matcher<EventT, R> = Matcher.eq(value, eventClass)

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

    fun <E : EventT> on(eventClass: Class<E>, createTransitionTo: S.(E) -> TransitionTo<StateT, SideEffectT>
    ) = on(any(eventClass), createTransitionTo)

    inline fun <reified E : EventT> on(
      noinline createTransitionTo: S.(E) -> TransitionTo<StateT, SideEffectT>
    ) = on(any(), createTransitionTo)

    fun <E : EventT> on(
      event: E,
      eventClass: Class<E>,
      createTransitionTo: S.(E) -> TransitionTo<StateT, SideEffectT>
    ) = on(eq(event, eventClass), createTransitionTo)

    inline fun <reified E : EventT> on(
      event: E,
      noinline createTransitionTo: S.(E) -> TransitionTo<StateT, SideEffectT>
    ) = on(eq(event), createTransitionTo)

    fun onEnter(listener: BiConsumer<S, EventT>) = with(stateDefinition) {
      onEnterListeners.add { state, cause ->
        @Suppress("UNCHECKED_CAST")
        listener.accept(state as S, cause)
      }
    }

    fun onEnter(listener: S.(EventT) -> Unit) = with(stateDefinition) {
      onEnterListeners.add { state, cause ->
        @Suppress("UNCHECKED_CAST")
        listener(state as S, cause)
      }
    }

    fun onExit(listener: BiConsumer<S, EventT>) = with(stateDefinition) {
      onExitListeners.add { state, cause ->
        @Suppress("UNCHECKED_CAST")
        listener.accept(state as S, cause)
      }
    }

    fun onExit(listener: S.(EventT) -> Unit) = with(stateDefinition) {
      onExitListeners.add { state, cause ->
        @Suppress("UNCHECKED_CAST")
        listener(state as S, cause)
      }
    }

    fun build() = stateDefinition

    @JvmOverloads
    fun transitionTo(state: StateT, sideEffect: SideEffectT? = null): TransitionTo<StateT, SideEffectT> =
      TransitionTo(state, sideEffect)

    fun S.dontTransition(sideEffect: SideEffectT? = null): TransitionTo<StateT, SideEffectT> = transitionTo(this, sideEffect)
  }
}