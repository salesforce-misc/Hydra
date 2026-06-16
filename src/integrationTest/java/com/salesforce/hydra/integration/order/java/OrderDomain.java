/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra.integration.order.java;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Domain types for the Java mirror of the order-fulfillment Hydra machine.
 *
 * <p>Three independent alphabets — state (where the order is), event (input signal), action (output
 * command). Jackson's {@code @JsonTypeInfo} / {@code @JsonSubTypes} carry the variant tag on the
 * wire and in the durable cursor; actions are deliberately NOT serialized — they are computed at
 * dispatch time and never persisted.
 */
final class OrderDomain {

  private OrderDomain() {}

  // ── State: where the order is. Carried as JSON in the durable cursor. ──
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = OrderDomain.Validating.class, name = "Validating"),
    @JsonSubTypes.Type(value = OrderDomain.Reserving.class, name = "Reserving"),
    @JsonSubTypes.Type(value = OrderDomain.Charging.class, name = "Charging"),
    @JsonSubTypes.Type(value = OrderDomain.Shipping.class, name = "Shipping"),
    @JsonSubTypes.Type(value = OrderDomain.Notifying.class, name = "Notifying"),
    @JsonSubTypes.Type(value = OrderDomain.Completed.class, name = "Completed"),
    @JsonSubTypes.Type(value = OrderDomain.Cancelled.class, name = "Cancelled"),
  })
  sealed interface OrderState {}

  record Validating(long amountCents, String address, String sku, int qty) implements OrderState {}

  record Reserving(long amountCents, String address, String sku, int qty) implements OrderState {}

  record Charging(long amountCents, String address, String sku, int qty) implements OrderState {}

  record Shipping(long amountCents, String address) implements OrderState {}

  record Notifying(String address) implements OrderState {}

  record Completed() implements OrderState {}

  record Cancelled(String reason) implements OrderState {}

  // ── Event: INPUT — what just happened. Carried as JSON on the RabbitMQ body. ──
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = OrderDomain.Validated.class, name = "Validated"),
    @JsonSubTypes.Type(value = OrderDomain.Reserved.class, name = "Reserved"),
    @JsonSubTypes.Type(value = OrderDomain.OutOfStock.class, name = "OutOfStock"),
    @JsonSubTypes.Type(value = OrderDomain.Charged.class, name = "Charged"),
    @JsonSubTypes.Type(value = OrderDomain.PaymentDeclined.class, name = "PaymentDeclined"),
    @JsonSubTypes.Type(value = OrderDomain.Shipped.class, name = "Shipped"),
    @JsonSubTypes.Type(value = OrderDomain.Notified.class, name = "Notified"),
  })
  sealed interface OrderEvent {}

  record Validated() implements OrderEvent {}

  record Reserved() implements OrderEvent {}

  record OutOfStock() implements OrderEvent {}

  record Charged() implements OrderEvent {}

  record PaymentDeclined(String reason) implements OrderEvent {}

  record Shipped() implements OrderEvent {}

  record Notified() implements OrderEvent {}

  // ── Action: OUTPUT — a command computed at dispatch time. NEVER serialized. ──
  sealed interface OrderAction {}

  record ReserveInventory(String sku, int qty) implements OrderAction {}

  record ChargePayment(long amountCents) implements OrderAction {}

  record ShipParcel(String address) implements OrderAction {}

  record SendShipNotice(String address) implements OrderAction {}

  record NotifyCustomer(String reason) implements OrderAction {}

  record ReleaseInventory(String sku, int qty) implements OrderAction {}
}
