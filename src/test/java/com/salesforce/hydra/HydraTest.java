/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra;

import static com.google.common.truth.Truth.assertThat;

import com.salesforce.hydra.OrderDomain.Cancel;
import com.salesforce.hydra.OrderDomain.Cancelled;
import com.salesforce.hydra.OrderDomain.Cart;
import com.salesforce.hydra.OrderDomain.ChargeCard;
import com.salesforce.hydra.OrderDomain.Checkout;
import com.salesforce.hydra.OrderDomain.OrderAction;
import com.salesforce.hydra.OrderDomain.OrderEvent;
import com.salesforce.hydra.OrderDomain.OrderState;
import com.salesforce.hydra.OrderDomain.PaymentFailed;
import com.salesforce.hydra.OrderDomain.PaymentSucceeded;
import com.salesforce.hydra.OrderDomain.Placed;
import com.salesforce.hydra.OrderDomain.RefundCard;
import com.salesforce.hydra.OrderDomain.ShipParcel;
import com.salesforce.hydra.OrderDomain.Shipped;
import com.salesforce.hydra.statemachine.Transition;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HydraTest {

  private static final long AMOUNT = 4_999L;
  private static final String ADDRESS = "1 Market St, San Francisco";

  Hydra<OrderState, OrderEvent, OrderAction> orderMachineStartingAt(OrderState initialState) {
    return orderMachine.cloneWith(mb -> mb.initialState(initialState));
  }

  @Test
  void testInitialStateIsCart() {
    assertThat(orderMachine.getState()).isEqualTo(new Cart());
  }

  @Test
  @DisplayName("Checkout (event w/ data) -> Placed, emits ChargeCard (action w/ data)")
  void testCheckoutPlacesOrderAndEmitsCharge() {
    final var machine = orderMachineStartingAt(new Cart());

    final var transition = machine.transition(new Checkout(AMOUNT, ADDRESS));

    assertThat(transition.isValid()).isTrue();
    assertThat(machine.getState()).isEqualTo(new Placed(AMOUNT, ADDRESS));
    assertThat(actionOf(transition)).isEqualTo(new ChargeCard(AMOUNT));
  }

  @Test
  @DisplayName(
      "Mealy star edge: bare event PaymentSucceeded -> ShipParcel action sourced from STATE")
  void testPaymentSucceededShipsUsingAddressFromState() {
    // The event carries NO data; the emitted action's address comes from the Placed STATE.
    // This is why Event and Action are different types: input signal vs. data-carrying output.
    final var machine = orderMachineStartingAt(new Placed(AMOUNT, ADDRESS));

    final var transition = machine.transition(new PaymentSucceeded());

    assertThat(transition.isValid()).isTrue();
    assertThat(machine.getState()).isEqualTo(new Shipped(AMOUNT, ADDRESS));
    assertThat(actionOf(transition)).isEqualTo(new ShipParcel(ADDRESS));
  }

  @Test
  @DisplayName("PaymentFailed -> Cancelled with NO action (action payload is optional)")
  void testPaymentFailedCancelsWithoutAction() {
    final var machine = orderMachineStartingAt(new Placed(AMOUNT, ADDRESS));

    final var transition = machine.transition(new PaymentFailed("card declined"));

    assertThat(transition.isValid()).isTrue();
    assertThat(machine.getState()).isEqualTo(new Cancelled());
    assertThat(actionOf(transition)).isNull();
  }

  @Test
  @DisplayName("Cancel a Shipped order -> Cancelled, emits RefundCard sourced from STATE")
  void testCancelShippedOrderRefunds() {
    final var machine = orderMachineStartingAt(new Shipped(AMOUNT, ADDRESS));

    final var transition = machine.transition(new Cancel());

    assertThat(transition.isValid()).isTrue();
    assertThat(machine.getState()).isEqualTo(new Cancelled());
    assertThat(actionOf(transition)).isEqualTo(new RefundCard(AMOUNT));
  }

  @Test
  @DisplayName("Invalid transition: Cancel while in Cart - rejected, state unchanged")
  void testCancelInCartIsInvalid() {
    final var machine = orderMachineStartingAt(new Cart());

    final var transition = machine.transition(new Cancel());

    assertThat(transition.isValid()).isFalse();
    assertThat(transition).isInstanceOf(Transition.Invalid.class);
    assertThat(machine.getState()).isEqualTo(new Cart());
  }

  @Test
  @DisplayName("Read-only transition computes the move and fires listeners without mutating state")
  void testReadTransitionDoesNotMutateState() {
    final var machine = orderMachineStartingAt(new Placed(AMOUNT, ADDRESS));

    final var transition =
        machine.readTransitionAndNotifyListeners(
            Placed.class, new Placed(AMOUNT, ADDRESS), new PaymentSucceeded());

    assertThat(transition.isValid()).isTrue();
    assertThat(actionOf(transition)).isEqualTo(new ShipParcel(ADDRESS));
    // State is untouched — durable truth lives outside the in-memory machine.
    assertThat(machine.getState()).isEqualTo(new Placed(AMOUNT, ADDRESS));
    Mockito.verify(onShippedEnter).accept(new Shipped(AMOUNT, ADDRESS), new PaymentSucceeded());
  }

  @Test
  @DisplayName("onExit / onEnter listeners fire on a valid transition")
  void testEnterAndExitListenersFire() {
    final var machine = orderMachineStartingAt(new Placed(AMOUNT, ADDRESS));

    machine.transition(new PaymentSucceeded());

    Mockito.verify(onPlacedExit).accept(new Placed(AMOUNT, ADDRESS), new PaymentSucceeded());
    Mockito.verify(onShippedEnter).accept(new Shipped(AMOUNT, ADDRESS), new PaymentSucceeded());
  }

  @Test
  @DisplayName("Handling an action: switch over the emitted command and perform the side effect")
  void testDispatchActionPerformsSideEffect() {
    final var machine = orderMachineStartingAt(new Cart());

    // 1. Drive the machine. Hydra returns the action — it does NOT perform it.
    final var transition = machine.transition(new Checkout(AMOUNT, ADDRESS));
    final OrderAction action = actionOf(transition);

    // 2. WE interpret the action and perform the effect (exhaustive over the sealed type).
    perform(action, effects);

    // 3. The effect happened because we dispatched it — Hydra only handed us the command.
    Mockito.verify(effects).chargeCard(AMOUNT);
    Mockito.verifyNoMoreInteractions(effects);
  }

  /** A sink for the real-world side effects an {@link OrderAction} commands. */
  interface Effects {
    void chargeCard(long amountCents);

    void shipParcel(String address);

    void refundCard(long amountCents);
  }

  /** Interpret a command and perform it. Exhaustive switch — the compiler enforces coverage. */
  private static void perform(OrderAction action, Effects effects) {
    switch (action) {
      case ChargeCard c -> effects.chargeCard(c.amountCents());
      case ShipParcel s -> effects.shipParcel(s.address());
      case RefundCard r -> effects.refundCard(r.amountCents());
    }
  }

  @SuppressWarnings("unchecked")
  private static OrderAction actionOf(Transition<OrderState, OrderEvent, OrderAction> transition) {
    return ((Transition.Valid<OrderState, OrderEvent, OrderAction>) transition).getAction();
  }

  final Effects effects = Mockito.mock();
  final BiConsumer<Placed, OrderEvent> onPlacedExit = Mockito.mock();
  final BiConsumer<Shipped, OrderEvent> onShippedEnter = Mockito.mock();

  public final Hydra<OrderState, OrderEvent, OrderAction> orderMachine =
      Hydra.create(
          mb -> {
            mb.initialState(new Cart());

            mb.state(
                Cart.class,
                sb ->
                    sb.on(
                        Checkout.class,
                        (cart, checkout) ->
                            sb.transitionTo(
                                new Placed(checkout.amountCents(), checkout.address()),
                                new ChargeCard(checkout.amountCents()))));

            mb.state(
                Placed.class,
                sb -> {
                  sb.onExit(onPlacedExit);
                  // Star edge: bare event in, data-carrying action out — address read from STATE.
                  sb.on(
                      PaymentSucceeded.class,
                      (placed, event) ->
                          sb.transitionTo(
                              new Shipped(placed.amountCents(), placed.address()),
                              new ShipParcel(placed.address())));
                  // No-action transition — the action payload is optional.
                  sb.on(PaymentFailed.class, (placed, event) -> sb.transitionTo(new Cancelled()));
                });

            mb.state(
                Shipped.class,
                sb -> {
                  sb.onEnter(onShippedEnter);
                  sb.on(
                      Cancel.class,
                      (shipped, event) ->
                          sb.transitionTo(new Cancelled(), new RefundCard(shipped.amountCents())));
                });

            mb.state(Cancelled.class, sb -> {});
          });
}
