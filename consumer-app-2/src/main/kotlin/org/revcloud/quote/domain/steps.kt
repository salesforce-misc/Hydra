package org.revcloud.quote.domain

import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.publish

context(RabbitMQInstance, Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
fun persistQuoteStep() {
  info { "Persist Quote step, spawns async process" }
  publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.Persist)
}

context(RabbitMQInstance, Hydra<Quote, Event, Action>, Env, KLogger)
fun priceQuoteStep() {
  info { "Price Quote step, spawns async process" }
  publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.Price)
}

context(RabbitMQInstance, Hydra<Quote, Event, Action>, Env, KLogger)
fun taxQuoteStep() {
  info { "Tax Quote step, spawns async process" }
  publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.Tax)
}

context(Hydra<Quote, Event, Action>, Env, KLogger)
fun postProcess() {
  info { "Post Processing the Quote" }
}
