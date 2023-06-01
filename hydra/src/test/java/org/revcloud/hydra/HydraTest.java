package org.revcloud.hydra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.revcloud.hydra.OrderDomain.Action;
import org.revcloud.hydra.OrderDomain.Cancel;
import org.revcloud.hydra.OrderDomain.Delivered;
import org.revcloud.hydra.OrderDomain.Event;
import org.revcloud.hydra.OrderDomain.Idle;
import org.revcloud.hydra.OrderDomain.OnCancelled;
import org.revcloud.hydra.OrderDomain.OnPaid;
import org.revcloud.hydra.OrderDomain.OnPlaced;
import org.revcloud.hydra.OrderDomain.OnShipped;
import org.revcloud.hydra.OrderDomain.Order;
import org.revcloud.hydra.OrderDomain.PaymentFailed;
import org.revcloud.hydra.OrderDomain.PaymentSuccessful;
import org.revcloud.hydra.OrderDomain.Place;
import org.revcloud.hydra.OrderDomain.Placed;
import org.revcloud.hydra.OrderDomain.Processed;
import org.revcloud.hydra.OrderDomain.Ship;
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
  @DisplayName("Read transition with listeners, without changing the state")
  void readTransition() {
    final var orderMachine = cloneOrderMachineWithInitialState(Idle.INSTANCE);
    final var transition =
        orderMachine.readTransitionAndNotifyListeners(Idle.INSTANCE, Place.INSTANCE);
    assertThat(transition.isValid()).isTrue();
    assertThat(transition).isEqualTo(Transition.valid(Idle.INSTANCE, Place.INSTANCE, Placed.INSTANCE, OnPlaced.INSTANCE));
    assertThat(orderMachine.getState()).isEqualTo(Idle.INSTANCE);
    Mockito.verify(onIdleExit).accept(Idle.INSTANCE, Place.INSTANCE);
    Mockito.verify(onPlaceEnter).accept(Placed.INSTANCE, Place.INSTANCE);
  }

  @Test
  @DisplayName("Read transition using Class, with listeners, without changing the state")
  void readTransitionWithClass() {
    final var orderMachine = cloneOrderMachineWithInitialState(Idle.INSTANCE);
    final var transition = orderMachine.readTransitionAndNotifyListeners(Idle.class, Place.INSTANCE);
    assertThat(transition.isValid()).isTrue();
    assertThat(transition).isEqualTo(Transition.valid(null, Place.INSTANCE, Placed.INSTANCE, OnPlaced.INSTANCE));
    Mockito.verifyNoInteractions(onIdleExit);
    Mockito.verify(onPlaceEnter).accept(Placed.INSTANCE, Place.INSTANCE);
  }

  @Test
  @DisplayName("Valid Transition - FromState: Idle, Event: Place, ToState: Placed, Action: OnPlaced")
  void idleOrderPlacePlaced() {
    final var orderMachine = cloneOrderMachineWithInitialState(Idle.INSTANCE);
    final var transition = orderMachine.transition(Place.INSTANCE);
    assertThat(orderMachine.getState()).isEqualTo(Placed.INSTANCE);
    assertThat(transition.isValid()).isTrue();
    assertThat(transition)
        .isEqualTo(
            Transition.valid(Idle.INSTANCE, Place.INSTANCE, Placed.INSTANCE, OnPlaced.INSTANCE));
  }

  @Test
  @DisplayName("Valid Transition - FromState: Placed, Event: PaymentFailed, ToState: Idle, Action: OnCancelled")
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
  @DisplayName("Invalid Transition - FromState: Idle, Event: Cancel - Invalid Transition")
  void invalidTransitionCancelInIdle() {
    final var orderMachine = cloneOrderMachineWithInitialState(Idle.INSTANCE);
    final var transition = orderMachine.transition(Cancel.INSTANCE);
    assertThat(orderMachine.getState()).isEqualTo(Idle.INSTANCE);
    assertThat(transition.isValid()).isFalse();
    assertThat(transition).isEqualTo(Transition.invalid(Idle.INSTANCE, Cancel.INSTANCE));
  }

  final BiConsumer<Idle, Event> onIdleExit = Mockito.mock();
  final BiConsumer<Placed, Event> onPlaceEnter = Mockito.mock();
  final BiConsumer<Delivered, Event> DeliveredEnter = Mockito.mock();
  public final Hydra<Order, Event, Action> orderMachine =
      Hydra.create(
          mb -> {
            mb.initialState(Idle.INSTANCE);

            mb.state(
                Idle.class,
                sb -> {
                  sb.onExit(onIdleExit);
                  sb.on(
                      Place.class,
                      (currentState, action) ->
                          sb.transitionTo(Placed.INSTANCE, OnPlaced.INSTANCE));
                });

            mb.state(
                Placed.class,
                sb -> {
                  sb.onEnter(onPlaceEnter);
                  sb.on(
                      PaymentFailed.class,
                      (currentState, action) ->
                          sb.transitionTo(Idle.INSTANCE, OnCancelled.INSTANCE));
                  sb.on(
                      PaymentSuccessful.class,
                      (currentState, action) ->
                          sb.transitionTo(Processed.INSTANCE, OnPaid.INSTANCE));
                  sb.on(
                      Cancel.class,
                      (currentState, action) ->
                          sb.transitionTo(Idle.INSTANCE, OnCancelled.INSTANCE));
                });

            mb.state(
                Processed.class,
                sb -> {
                  sb.on(
                      Ship.class,
                      (currentState, action) ->
                          sb.transitionTo(Delivered.INSTANCE, OnShipped.INSTANCE));
                  sb.on(
                      Cancel.class,
                      (currentState, action) ->
                          sb.transitionTo(Idle.INSTANCE, OnCancelled.INSTANCE));
                });

            mb.state(Delivered.class, sb -> sb.onEnter(DeliveredEnter));
          });
}
