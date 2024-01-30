package com.salesforce.hydra;

import static org.assertj.core.api.Assertions.assertThat;

import com.salesforce.hydra.statemachine.Transition;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HydraTest {

  Hydra<OrderDomain.Order, OrderDomain.Event, OrderDomain.Action> cloneOrderMachineWithInitialState(
      OrderDomain.Order state) {
    return orderMachine.cloneWith(mb -> mb.initialState(state));
  }

  @Test
  void initialStateShouldBeIdle() {
    assertThat(orderMachine.getState()).isEqualTo(OrderDomain.Idle.INSTANCE);
  }

  @Test
  @DisplayName("Read transition with listeners, without changing the state")
  void readTransition() {
    final var orderMachine = cloneOrderMachineWithInitialState(OrderDomain.Idle.INSTANCE);
    final var transition =
        orderMachine.readTransitionAndNotifyListeners(
            OrderDomain.Idle.INSTANCE, OrderDomain.Place.INSTANCE);
    assertThat(transition.isValid()).isTrue();
    assertThat(transition)
        .isEqualTo(
            Transition.valid(
                OrderDomain.Idle.INSTANCE,
                OrderDomain.Place.INSTANCE,
                OrderDomain.Placed.INSTANCE,
                OrderDomain.OnPlaced.INSTANCE));
    assertThat(orderMachine.getState()).isEqualTo(OrderDomain.Idle.INSTANCE);
    Mockito.verify(onIdleExit).accept(OrderDomain.Idle.INSTANCE, OrderDomain.Place.INSTANCE);
    Mockito.verify(onPlaceEnter).accept(OrderDomain.Placed.INSTANCE, OrderDomain.Place.INSTANCE);
  }

  @Test
  @DisplayName("Read transition using Class, with listeners, without changing the state")
  void readTransitionWithClass() {
    final var orderMachine = cloneOrderMachineWithInitialState(OrderDomain.Idle.INSTANCE);
    final var transition =
        orderMachine.readTransitionAndNotifyListeners(
            OrderDomain.Idle.class, OrderDomain.Place.INSTANCE);
    assertThat(transition.isValid()).isTrue();
    assertThat(transition)
        .isEqualTo(
            Transition.valid(
                null,
                OrderDomain.Place.INSTANCE,
                OrderDomain.Placed.INSTANCE,
                OrderDomain.OnPlaced.INSTANCE));
    Mockito.verifyNoInteractions(onIdleExit);
    Mockito.verify(onPlaceEnter).accept(OrderDomain.Placed.INSTANCE, OrderDomain.Place.INSTANCE);
  }

  @Test
  @DisplayName(
      "Valid Transition - FromState: Idle, Event: Place, ToState: Placed, Action: OnPlaced")
  void idleOrderPlacePlaced() {
    final var orderMachine = cloneOrderMachineWithInitialState(OrderDomain.Idle.INSTANCE);
    final var transition = orderMachine.transition(OrderDomain.Place.INSTANCE);
    assertThat(orderMachine.getState()).isEqualTo(OrderDomain.Placed.INSTANCE);
    assertThat(transition.isValid()).isTrue();
    assertThat(transition)
        .isEqualTo(
            Transition.valid(
                OrderDomain.Idle.INSTANCE,
                OrderDomain.Place.INSTANCE,
                OrderDomain.Placed.INSTANCE,
                OrderDomain.OnPlaced.INSTANCE));
  }

  @Test
  @DisplayName(
      "Valid Transition - FromState: Placed, Event: PaymentFailed, ToState: Idle, Action: OnCancelled")
  void placedOrderPaymentFailed() {
    final var orderMachine = cloneOrderMachineWithInitialState(OrderDomain.Placed.INSTANCE);
    final var transition = orderMachine.transition(OrderDomain.PaymentFailed.INSTANCE);
    assertThat(orderMachine.getState()).isEqualTo(OrderDomain.Idle.INSTANCE);
    assertThat(transition)
        .isEqualTo(
            Transition.valid(
                OrderDomain.Placed.INSTANCE,
                OrderDomain.PaymentFailed.INSTANCE,
                OrderDomain.Idle.INSTANCE,
                OrderDomain.OnCancelled.INSTANCE));
  }

  @Test
  @DisplayName("Invalid Transition - FromState: Idle, Event: Cancel - Invalid Transition")
  void invalidTransitionCancelInIdle() {
    final var orderMachine = cloneOrderMachineWithInitialState(OrderDomain.Idle.INSTANCE);
    final var transition = orderMachine.transition(OrderDomain.Cancel.INSTANCE);
    assertThat(orderMachine.getState()).isEqualTo(OrderDomain.Idle.INSTANCE);
    assertThat(transition.isValid()).isFalse();
    assertThat(transition)
        .isEqualTo(Transition.invalid(OrderDomain.Idle.INSTANCE, OrderDomain.Cancel.INSTANCE));
  }

  final BiConsumer<OrderDomain.Idle, OrderDomain.Event> onIdleExit = Mockito.mock();
  final BiConsumer<OrderDomain.Placed, OrderDomain.Event> onPlaceEnter = Mockito.mock();
  final BiConsumer<OrderDomain.Delivered, OrderDomain.Event> DeliveredEnter = Mockito.mock();
  public final Hydra<OrderDomain.Order, OrderDomain.Event, OrderDomain.Action> orderMachine =
      Hydra.create(
          mb -> {
            mb.initialState(OrderDomain.Idle.INSTANCE);

            mb.state(
                OrderDomain.Idle.class,
                sb -> {
                  sb.onExit(onIdleExit);
                  sb.on(
                      OrderDomain.Place.class,
                      (currentState, action) ->
                          sb.transitionTo(
                              OrderDomain.Placed.INSTANCE, OrderDomain.OnPlaced.INSTANCE));
                });

            mb.state(
                OrderDomain.Placed.class,
                sb -> {
                  sb.onEnter(onPlaceEnter);
                  sb.on(
                      OrderDomain.PaymentFailed.class,
                      (currentState, action) ->
                          sb.transitionTo(
                              OrderDomain.Idle.INSTANCE, OrderDomain.OnCancelled.INSTANCE));
                  sb.on(
                      OrderDomain.PaymentSuccessful.class,
                      (currentState, action) ->
                          sb.transitionTo(
                              OrderDomain.Processed.INSTANCE, OrderDomain.OnPaid.INSTANCE));
                  sb.on(
                      OrderDomain.Cancel.class,
                      (currentState, action) ->
                          sb.transitionTo(
                              OrderDomain.Idle.INSTANCE, OrderDomain.OnCancelled.INSTANCE));
                });

            mb.state(
                OrderDomain.Processed.class,
                sb -> {
                  sb.on(
                      OrderDomain.Ship.class,
                      (currentState, action) ->
                          sb.transitionTo(
                              OrderDomain.Delivered.INSTANCE, OrderDomain.OnShipped.INSTANCE));
                  sb.on(
                      OrderDomain.Cancel.class,
                      (currentState, action) ->
                          sb.transitionTo(
                              OrderDomain.Idle.INSTANCE, OrderDomain.OnCancelled.INSTANCE));
                });

            mb.state(OrderDomain.Delivered.class, sb -> sb.onEnter(DeliveredEnter));
          });
}
