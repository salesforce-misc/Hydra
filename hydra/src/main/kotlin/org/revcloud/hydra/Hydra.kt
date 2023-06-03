package org.revcloud.hydra

import org.revcloud.hydra.config.MachineBuilder
import org.revcloud.hydra.internal.Machine
import org.revcloud.hydra.statemachine.Transition
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class Hydra<StateT : Any, EventT : Any, ActionT : Any> private constructor(private val machine: Machine<StateT, EventT, ActionT>) {

  private val stateRef = AtomicReference(machine.initialState)

  val state: StateT
    get() = stateRef.get()

  fun cloneWith(init: Consumer<MachineBuilder<StateT, EventT, ActionT>>): Hydra<StateT, EventT, ActionT> {
    return create(machine.copy(initialState = state), init)
  }

  fun transition(event: EventT): Transition<StateT, EventT, ActionT> {
    val transition = synchronized(this) {
      val fromState = stateRef.get()
      val transition = fromState.getMatchingTransition(event)
      if (transition is Transition.Valid) {
        stateRef.set(transition.toState)
      }
      transition
    }
    notifyTransition(transition, event)
    return transition
  }

  private fun notifyTransition(transition: Transition<StateT, EventT, ActionT>, event: EventT) = with(transition) {
    if (this is Transition.Valid) {
      with(fromState) {
        this?.notifyOnExit(event)
      }
      notifyOnTransition()
      with(toState) {
        notifyOnEnter(event)
      }
    } else {
      notifyOnTransition()
    }
  }

  fun StateT.readTransitionAndNotifyListeners(event: EventT): Transition<StateT, EventT, ActionT> {
    val transition = this.getMatchingTransition(event)
    notifyTransition(transition, event)
    return transition
  }

  private fun StateT.getMatchingTransition(event: EventT): Transition<StateT, EventT, ActionT> =
    getDefinition()?.getTransitionForEvent(event) ?: Transition.NoFromState(event)

  fun readTransitionAndNotifyListeners(fromStateClass: Class<out StateT>, event: EventT): Transition<StateT, EventT, ActionT> {
    val transition = getMatchingTransition(fromStateClass, event)
    notifyTransition(transition, event)
    return transition
  }

  private fun getMatchingTransition(fromStateClass: Class<out StateT>, event: EventT): Transition<StateT, EventT, ActionT> =
    getDefinition(fromStateClass)?.getTransitionForEvent(event) ?: Transition.NoFromState(event)

  private fun getDefinition(fromStateClass: Class<out StateT>): Machine.State<StateT, EventT, ActionT>? = machine.stateDefinitions
    .filter { it.key.matches(fromStateClass) }
    .map { it.value }
    .firstOrNull()

  private fun StateT.getDefinition() = machine.stateDefinitions
    .filter { it.key.matches(this) }
    .map { it.value }
    .firstOrNull()

  private fun StateT.notifyOnEnter(cause: EventT) {
    getDefinition()?.onEnterListeners?.forEach { it(this, cause) }
  }

  private fun StateT.notifyOnExit(cause: EventT) {
    getDefinition()?.onExitListeners?.forEach { it(this, cause) }
  }

  private fun Transition<StateT, EventT, ActionT>.notifyOnTransition() {
    machine.onTransitionListeners.forEach { it.accept(this) }
  }

  companion object {
    @JvmStatic
    fun <StateT : Any, EventT : Any, ActionT : Any> create(
      init: Consumer<MachineBuilder<StateT, EventT, ActionT>>
    ): Hydra<StateT, EventT, ActionT> = create(null, init)

    fun <StateT : Any, EventT : Any, ActionT : Any> create(
      init: MachineBuilder<StateT, EventT, ActionT>.() -> Unit
    ): Hydra<StateT, EventT, ActionT> {
      return create(null, init)
    }

    @JvmStatic
    private fun <StateT : Any, EventT : Any, ActionT : Any> create(
      machine: Machine<StateT, EventT, ActionT>?,
      init: Consumer<MachineBuilder<StateT, EventT, ActionT>>
    ): Hydra<StateT, EventT, ActionT> = Hydra(MachineBuilder(machine).apply { init.accept(this) }.build())

    private fun <StateT : Any, EventT : Any, ActionT : Any> create(
      machine: Machine<StateT, EventT, ActionT>?,
      init: MachineBuilder<StateT, EventT, ActionT>.() -> Unit
    ): Hydra<StateT, EventT, ActionT> {
      return Hydra(MachineBuilder(machine).apply(init).build())
    }
  }
}
