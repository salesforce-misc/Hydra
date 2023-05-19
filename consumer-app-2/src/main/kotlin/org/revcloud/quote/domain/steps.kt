package org.revcloud.quote.domain

import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.publish


context(RabbitMQInstance, Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
fun persistQuoteStep() {
  publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.Persist)
}

context(RabbitMQInstance, Hydra<Quote, Event, Action>, Env, KLogger)
fun priceQuoteStep() {
  publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.Price)
}

context(RabbitMQInstance, Hydra<Quote, Event, Action>, Env, KLogger)
fun taxQuoteStep() {
  publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.Price)
}

context(Hydra<Quote, Event, Action>, Env, KLogger)
fun postProcess() {
  info { "Post Processing" }
}
