/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import com.google.common.truth.Truth.assertThat
import com.salesforce.hydra.statemachine.Transition
import org.junit.jupiter.api.Test

class OrderMachineKtTest {

  @Test
  fun reservingReservedRoutesToChargingEmittingChargePayment() {
    val machine = orderMachine()
    val from = Reserving(amountCents = 4_999, address = "1 Market St", sku = "SKU-1", qty = 1)

    val transition = machine.readTransitionAndNotifyListeners(from, Reserved)

    assertThat(transition.isValid).isTrue()
    val valid = transition as Transition.Valid<OrderState, OrderEvent, OrderAction>
    assertThat(valid.toState)
      .isEqualTo(Charging(amountCents = 4_999, address = "1 Market St", sku = "SKU-1", qty = 1))
    assertThat(valid.action).isEqualTo(ChargePayment(amountCents = 4_999))
  }

  @Test
  fun reservingOutOfStockRoutesToCancelled() {
    val machine = orderMachine()
    val from = Reserving(amountCents = 4_999, address = "x", sku = "SKU-1", qty = 1)

    val transition = machine.readTransitionAndNotifyListeners(from, OutOfStock)

    val valid = transition as Transition.Valid<OrderState, OrderEvent, OrderAction>
    assertThat(valid.toState).isInstanceOf(Cancelled::class.java)
    assertThat(valid.action).isEqualTo(NotifyCustomer(reason = "out of stock"))
  }
}
