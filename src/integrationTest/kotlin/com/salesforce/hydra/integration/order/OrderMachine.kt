/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import com.salesforce.hydra.Hydra

/**
 * The map of legal fulfillment moves: which event in which state routes where, with what action.
 */
fun orderMachine(): Hydra<OrderState, OrderEvent, OrderAction> =
  Hydra.create { mb ->
    mb.initialState(Validating(amountCents = 0, address = "", sku = "", qty = 0))

    mb.state(Validating::class.java) { sb ->
      sb.on(Validated::class.java) { _ ->
        with(this!!) {
          sb.transitionTo(
            Reserving(amountCents = amountCents, address = address, sku = sku, qty = qty),
            ReserveInventory(sku = sku, qty = qty),
          )
        }
      }
    }

    mb.state(Reserving::class.java) { sb ->
      sb.on(Reserved::class.java) { _ ->
        with(this!!) {
          sb.transitionTo(
            Charging(amountCents = amountCents, address = address, sku = sku, qty = qty),
            ChargePayment(amountCents = amountCents),
          )
        }
      }
      sb.on(OutOfStock::class.java) { _ ->
        sb.transitionTo(Cancelled(reason = "out of stock"), NotifyCustomer(reason = "out of stock"))
      }
    }

    mb.state(Charging::class.java) { sb ->
      sb.on(Charged::class.java) { _ ->
        with(this!!) {
          sb.transitionTo(
            Shipping(amountCents = amountCents, address = address),
            ShipParcel(address = address),
          )
        }
      }
      sb.on(PaymentDeclined::class.java) { event ->
        with(this!!) {
          sb.transitionTo(Cancelled(reason = event.reason), ReleaseInventory(sku = sku, qty = qty))
        }
      }
    }

    mb.state(Shipping::class.java) { sb ->
      sb.on(Shipped::class.java) { _ ->
        with(this!!) {
          sb.transitionTo(Notifying(address = address), SendShipNotice(address = address))
        }
      }
    }

    mb.state(Notifying::class.java) { sb ->
      sb.on(Notified::class.java) { _ -> sb.transitionTo(Completed) }
    }

    mb.state(Completed::class.java) {}
    mb.state(Cancelled::class.java) {}
  }
