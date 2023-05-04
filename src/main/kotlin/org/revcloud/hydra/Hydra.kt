package org.revcloud.hydra

import org.revcloud.hydra.internal.Graph
import org.revcloud.hydra.config.ConfigBuilder
import org.revcloud.hydra.statemachine.Transition
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class Hydra<StateT : Any, EventT : Any, SideEffectT : Any> private constructor(
  private val graph: Graph<StateT, EventT, SideEffectT>
) {

  private val stateRef = AtomicReference(graph.initialState)

  val state: StateT
    get() = stateRef.get()

  fun transition(event: EventT): Transition<StateT, EventT, SideEffectT> {
    val transition = synchronized(this) {
      val fromState = stateRef.get()
      val transition = fromState.getTransition(event)
      if (transition is Transition.Valid) {
        stateRef.set(transition.toState)
      }
      transition
    }
    transition.notifyOnTransition()
    if (transition is Transition.Valid) {
      with(transition) {
        with(fromState) {
          notifyOnExit(event)
        }
        with(toState) {
          notifyOnEnter(event)
        }
      }
    }
    return transition
  }

  fun with(init: ConfigBuilder<StateT, EventT, SideEffectT>.() -> Unit): Hydra<StateT, EventT, SideEffectT> {
    return create(graph.copy(initialState = state), init)
  }

  private fun StateT.getTransition(event: EventT): Transition<StateT, EventT, SideEffectT> {
    for ((eventMatcher, createTransitionTo) in getDefinition().transitions) {
      if (eventMatcher.matches(event)) {
        val (toState, sideEffect) = createTransitionTo(this, event)
        return Transition.Valid(this, event, toState, sideEffect)
      }
    }
    return Transition.Invalid(this, event)
  }

  private fun StateT.getDefinition() = graph.stateDefinitions
    .filter { it.key.matches(this) }
    .map { it.value }
    .firstOrNull() ?: error("Missing definition for state ${this.javaClass.simpleName}!")

  private fun StateT.notifyOnEnter(cause: EventT) {
    getDefinition().onEnterListeners.forEach { it(this, cause) }
  }

  private fun StateT.notifyOnExit(cause: EventT) {
    getDefinition().onExitListeners.forEach { it(this, cause) }
  }

  private fun Transition<StateT, EventT, SideEffectT>.notifyOnTransition() {
    graph.onTransitionListeners.forEach { it.accept(this) }
  }

  companion object {
    @JvmStatic
    fun <StateT : Any, EventT : Any, SideEffectT : Any> create(
      init: Consumer<ConfigBuilder<StateT, EventT, SideEffectT>>
    ): Hydra<StateT, EventT, SideEffectT> = create(null, init)

    @JvmStatic
    private fun <StateT : Any, EventT : Any, SideEffectT : Any> create(
      graph: Graph<StateT, EventT, SideEffectT>?,
      init: Consumer<ConfigBuilder<StateT, EventT, SideEffectT>>
    ): Hydra<StateT, EventT, SideEffectT> = Hydra(ConfigBuilder(graph).apply { init.accept(this) }.build())
  }
}
