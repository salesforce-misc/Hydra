package org.revcloud.app.domain

import kotlinx.serialization.Serializable


@Serializable
sealed class Order {
  @Serializable
  object Idle : Order()

  @Serializable
  object Place : Order()

  @Serializable
  object Process : Order()

  @Serializable
  object Deliver : Order()
  
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
  object Placed : SideEffect("Order placed")

  @Serializable
  object Paid : SideEffect("Order paid")

  @Serializable
  object Shipped : SideEffect("Order shipped")

  @Serializable
  object Cancelled : SideEffect("Order cancelled")
}
