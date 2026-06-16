/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import kotlinx.serialization.Serializable

/**
 * Where the order run currently IS — every variant carries the context the next worker needs, so
 * Hydra can resume by deserializing this single value from Postgres.
 */
@Serializable sealed interface OrderState

@Serializable
data class Validating(val amountCents: Long, val address: String, val sku: String, val qty: Int) :
  OrderState

@Serializable
data class Reserving(val amountCents: Long, val address: String, val sku: String, val qty: Int) :
  OrderState

@Serializable
data class Charging(val amountCents: Long, val address: String, val sku: String, val qty: Int) :
  OrderState

@Serializable data class Shipping(val amountCents: Long, val address: String) : OrderState

@Serializable data class Notifying(val address: String) : OrderState

@Serializable data object Completed : OrderState

@Serializable data class Cancelled(val reason: String) : OrderState

/** INPUT — the exit signal of a step, delivered to Hydra over the wire (RabbitMQ body). */
@Serializable sealed interface OrderEvent

@Serializable data object Validated : OrderEvent

@Serializable data object Reserved : OrderEvent

@Serializable data object OutOfStock : OrderEvent

@Serializable data object Charged : OrderEvent

@Serializable data class PaymentDeclined(val reason: String) : OrderEvent

@Serializable data object Shipped : OrderEvent

@Serializable data object Notified : OrderEvent

/**
 * OUTPUT — the command the next worker will perform. Computed by Hydra at dispatch time; never
 * persisted, hence not `@Serializable`.
 */
sealed interface OrderAction

data class ReserveInventory(val sku: String, val qty: Int) : OrderAction

data class ChargePayment(val amountCents: Long) : OrderAction

data class ShipParcel(val address: String) : OrderAction

data class SendShipNotice(val address: String) : OrderAction

data class NotifyCustomer(val reason: String) : OrderAction

data class ReleaseInventory(val sku: String, val qty: Int) : OrderAction
