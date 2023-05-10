package org.revcloud.app.routes

import io.ktor.server.application.Application
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KLogger
import org.revcloud.app.domain.Action
import org.revcloud.app.domain.Order
import org.revcloud.app.domain.SideEffect
import org.revcloud.app.env.Env
import org.revcloud.app.repo.StatePersistence
import org.revcloud.hydra.Hydra
import org.revcloud.hydra.statemachine.Transition
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.consume
import pl.jutupe.ktor_rabbitmq.publish
import pl.jutupe.ktor_rabbitmq.rabbitConsumer
import kotlin.random.Random

context(Application, Hydra<Order, Action, SideEffect>, StatePersistence, Env, KLogger)
fun rabbitConsumers() = rabbitConsumer {
  consume<Action>("queue") { action ->
    info { "Consumed Action $action" }
    val order: Order = state // * NOTE 08/05/23 gopala.akshintala: This is needed for encoding below 
    runBlocking { insert(Json.encodeToString(order)) }
    val transition = transition(action)
    onTransition(transition)
  }
}

context(RabbitMQInstance, Env, KLogger)
fun onTransition(transition: Transition<Order, Action, SideEffect>) {
  val sideEffect = (transition as? Transition.Valid)?.sideEffect ?: return
  when (sideEffect) {
    SideEffect.OnPlaced -> {
      info { "${SideEffect.OnPlaced.msg}, Payment in Progress" }
      if (Random.nextBoolean()) {
        publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Action.PaymentSuccessful)
      } else {
        publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Action.PaymentFailed)
      }
    }
    SideEffect.OnPaid -> {
      info { "${SideEffect.OnPaid.msg}, Shipping in Progress" }
      if (Random.nextBoolean()) {
        publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Action.Ship)
      } else {
        publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Action.Cancel)
      }
    }
    SideEffect.OnShipped -> {
      info { "${SideEffect.OnShipped.msg}, Delivered successfully!!" }
    }
    SideEffect.OnCancelled -> {
      info { SideEffect.OnCancelled.msg }
    }
  }
}
