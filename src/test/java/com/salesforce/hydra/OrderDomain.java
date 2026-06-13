/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra;

/**
 * Domain types for the example Order machine.
 *
 * <p>A Mealy machine has three independent "alphabets", and Hydra gives each its own type parameter
 * — {@code Hydra<StateT, EventT, ActionT>}:
 *
 * <ul>
 *   <li>{@link OrderState} — where the order <em>is</em> (a position; may carry context).
 *   <li>{@link OrderEvent} — an <em>input</em>: something that happened <em>to</em> the order (a
 *       cause; often a bare signal).
 *   <li>{@link OrderAction} — an <em>output</em>: a command the machine <em>emits</em> on a
 *       transition (an effect; carries the data the doer needs).
 * </ul>
 *
 * <p>Events and Actions are deliberately <em>different</em> types: you push Events in and receive
 * Actions out, and the compiler enforces that one-way arrow.
 */
final class OrderDomain {

  private OrderDomain() {}

  // ── State: where the order is. Carries the context that later steps need. ──
  sealed interface OrderState {}

  record Cart() implements OrderState {}

  record Placed(long amountCents, String address) implements OrderState {}

  record Shipped(long amountCents, String address) implements OrderState {}

  record Cancelled() implements OrderState {}

  // ── Event: INPUT — something that happened TO the order. May be bare or carry data. ──
  sealed interface OrderEvent {}

  record Checkout(long amountCents, String address) implements OrderEvent {}

  record PaymentSucceeded() implements OrderEvent {} // a bare signal — no data

  record PaymentFailed(String reason) implements OrderEvent {}

  record Cancel() implements OrderEvent {} // also bare

  // ── Action: OUTPUT — a command the machine EMITS on a transition. Always carries data. ──
  sealed interface OrderAction {}

  record ChargeCard(long amountCents) implements OrderAction {}

  record ShipParcel(String address) implements OrderAction {}

  record RefundCard(long amountCents) implements OrderAction {}
}
