package org.revcloud.app.routes

import io.ktor.server.application.Application
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KLogger
import org.revcloud.app.domain.Action
import org.revcloud.app.domain.Order
import org.revcloud.app.domain.SideEffect
import org.revcloud.app.repo.StatePersistence
import org.revcloud.hydra.Hydra
import org.revcloud.hydra.statemachine.Transition
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.consume
import pl.jutupe.ktor_rabbitmq.publish
import pl.jutupe.ktor_rabbitmq.rabbitConsumer
import kotlin.random.Random

context(Application, Hydra<Order, Action, SideEffect>, StatePersistence, KLogger)
fun rabbitConsumers() = rabbitConsumer {
  consume<Action>("queue") { action ->
    info { "Consumed Action $action" }
    val order: Order = state
    runBlocking { insert(Json.encodeToString(order)) }
    val transition = transition(action)
    onTransition(transition)
  }
}

context(RabbitMQInstance, KLogger)
fun onTransition(transition: Transition<Order, Action, SideEffect>) {
  val validTransition = transition as? Transition.Valid ?: return
  when (validTransition.sideEffect) {
    SideEffect.Placed -> {
      info { "${SideEffect.Placed.msg}, Payment in Progress" }
      if (Random.nextBoolean()) {
        publish("exchange", "routingKey", null, Action.PaymentSuccessful)
      } else {
        publish("exchange", "routingKey", null, Action.PaymentFailed)
      }
    }
    SideEffect.Paid -> {
      info { "${SideEffect.Paid.msg}, Shipping in Progress" }
      if (Random.nextBoolean()) {
        publish("exchange", "routingKey", null, Action.Ship)
      } else {
        publish("exchange", "routingKey", null, Action.Cancel)
      }
    }
    SideEffect.Shipped -> {
      info { "${SideEffect.Shipped.msg}, Delivered successfully!!" }
    }
    SideEffect.Cancelled -> {
      info { SideEffect.Cancelled.msg }
    }
    else -> error { "Invalid Side Effect" }
  }
}
