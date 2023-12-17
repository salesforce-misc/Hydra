package org.revcloud.order.routes

import io.ktor.server.application.Application
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.hydra.statemachine.Transition
import org.revcloud.order.domain.Action
import org.revcloud.order.domain.Event
import org.revcloud.order.domain.Order
import org.revcloud.order.env.Env
import org.revcloud.order.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.consume
import pl.jutupe.ktor_rabbitmq.publish
import pl.jutupe.ktor_rabbitmq.rabbitConsumer

context(Application, Hydra<Order, Event, Action>, StatePersistence, Env, KLogger)
fun rabbitConsumers() = rabbitConsumer {
  consume<Event>(rabbitMQ.queue) { event ->
    info { "Consumed Event: $event" }
    val order: Order = state // * NOTE 08/05/23 gopala.akshintala: This is needed for encoding below
    runBlocking { insert(Json.encodeToString(order)) }
    val transition = transition(event)
    onTransition(transition)
  }
}

context(RabbitMQInstance, Env, KLogger)
fun onTransition(transition: Transition<Order, Event, Action>) {
  val action = (transition as? Transition.Valid)?.action ?: return
  when (action) {
    Action.OnPlaced -> {
      info { "${Action.OnPlaced.msg}, Payment in Progress" }
      if (Random.nextBoolean()) {
        publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.PaymentSuccessful)
      } else {
        publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.PaymentFailed)
      }
    }
    Action.OnPaid -> {
      info { "${Action.OnPaid.msg}, Shipping in Progress" }
      if (Random.nextBoolean()) {
        publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.Ship)
      } else {
        publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.Cancel)
      }
    }
    Action.OnShipped -> {
      info { "${Action.OnShipped.msg}, Delivered successfully!!" }
    }
    Action.OnCancelled -> {
      info { Action.OnCancelled.msg }
    }
  }
}
