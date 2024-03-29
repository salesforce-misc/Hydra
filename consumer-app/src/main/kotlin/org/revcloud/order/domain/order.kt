/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.order.domain

import kotlinx.serialization.Serializable

@Serializable
sealed class Order {
  @Serializable object Idle : Order()

  @Serializable object Placed : Order()

  @Serializable object Processed : Order()

  @Serializable object Delivered : Order()
}

@Serializable
sealed class Event {
  @Serializable object Place : Event()

  @Serializable object PaymentSuccessful : Event()

  @Serializable object PaymentFailed : Event()

  @Serializable object Ship : Event()

  @Serializable object Cancel : Event()
}

@Serializable
sealed class Action(val msg: String) {
  @Serializable object OnPlaced : Action("Order placed")

  @Serializable object OnPaid : Action("Order paid")

  @Serializable object OnShipped : Action("Order shipped")

  @Serializable object OnCancelled : Action("Order cancelled")
}
