package org.revcloud.app.domain

import kotlinx.serialization.Serializable


@Serializable
sealed class Order {
  @Serializable
  object Idle : Order()

  @Serializable
  object Placed : Order()

  @Serializable
  object Processed : Order()

  @Serializable
  object Delivered : Order()
  
}

@Serializable
sealed class Action {
  @Serializable
  object Place : Action()

  @Serializable
  object PaymentSuccessful : Action()

  @Serializable
  object PaymentFailed : Action()

  @Serializable
  object Ship : Action()

  @Serializable
  object Cancel : Action()
}



@Serializable
sealed class SideEffect(val msg: String) {
  @Serializable
  object OnPlaced : SideEffect("Order placed")

  @Serializable
  object OnPaid : SideEffect("Order paid")

  @Serializable
  object OnShipped : SideEffect("Order shipped")

  @Serializable
  object OnCancelled : SideEffect("Order cancelled")
}
