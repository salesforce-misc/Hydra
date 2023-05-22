package org.revcloud.hydra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.revcloud.hydra.OrderMachine.orderMachine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.revcloud.hydra.OrderMachine.Action;
import org.revcloud.hydra.OrderMachine.Cancel;
import org.revcloud.hydra.OrderMachine.Event;
import org.revcloud.hydra.OrderMachine.Idle;
import org.revcloud.hydra.OrderMachine.OnCancelled;
import org.revcloud.hydra.OrderMachine.OnPlaced;
import org.revcloud.hydra.OrderMachine.Order;
import org.revcloud.hydra.OrderMachine.PaymentFailed;
import org.revcloud.hydra.OrderMachine.Place;
import org.revcloud.hydra.OrderMachine.Placed;
import org.revcloud.hydra.statemachine.Transition;

class HydraTest {

  Hydra<Order, Event, Action> cloneOrderMachineWithInitialState(Order state) {
    return orderMachine.cloneWith(mb -> mb.initialState(state));
  }

  @Test
  void initialStateShouldBeIdle() {
    assertThat(orderMachine.getState()).isEqualTo(Idle.INSTANCE);
  }

  @Test
  @DisplayName("FromState: Idle, Event: Place, ToState: Placed, Action: OnPlaced")
  void idleOrderPlacePlaced() {
    final var orderMachine = cloneOrderMachineWithInitialState(Idle.INSTANCE);
    final var transition = orderMachine.transition(Place.INSTANCE);
    assertThat(orderMachine.getState()).isEqualTo(Placed.INSTANCE);
    assertThat(transition)
        .isEqualTo(
            Transition.valid(Idle.INSTANCE, Place.INSTANCE, Placed.INSTANCE, OnPlaced.INSTANCE));
  }

  @Test
  @DisplayName("FromState: Placed, Event: PaymentFailed, ToState: Idle, Action: OnCancelled")
  void placedOrderPaymentFailed() {
    final var orderMachine = cloneOrderMachineWithInitialState(Placed.INSTANCE);
    final var transition = orderMachine.transition(PaymentFailed.INSTANCE);
    assertThat(orderMachine.getState()).isEqualTo(Idle.INSTANCE);
    assertThat(transition)
        .isEqualTo(
            Transition.valid(
                Placed.INSTANCE, PaymentFailed.INSTANCE, Idle.INSTANCE, OnCancelled.INSTANCE));
  }

  @Test
  @DisplayName("FromState: Idle, Event: Cancel - Invalid Transition")
  void invalidTransitionCancelInIdle() {
    final var orderMachine = cloneOrderMachineWithInitialState(Idle.INSTANCE);
    final var transition = orderMachine.transition(Cancel.INSTANCE);
    assertThat(orderMachine.getState()).isEqualTo(Idle.INSTANCE);
    assertThat(transition).isEqualTo(Transition.invalid(Idle.INSTANCE, Cancel.INSTANCE));
  }
}
