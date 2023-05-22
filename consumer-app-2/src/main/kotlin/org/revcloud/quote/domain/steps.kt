package org.revcloud.quote.domain

import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import pl.jutupe.ktor_rabbitmq.publish

context(RabbitMQInstance, Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
fun persistQuoteSyncStep(placeEvent: Event.Place) {
  info { "Persist Quote step synchronously" }
  handlePersistQuote(Event.Persist(placeEvent.quotePayload))
}

context(RabbitMQInstance, StatePersistence, Env, KLogger)
fun persistQuoteAsyncStep(placeEvent: Event.Place) {
  info { "Persist Quote step, spawns async process" }
  publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.Persist(placeEvent.quotePayload))
}

context(RabbitMQInstance, StatePersistence, Env, KLogger)
fun priceQuoteStep() {
  info { "Price Quote step, spawns async process" }
  publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.Price)
}

context(RabbitMQInstance, StatePersistence, Env, KLogger)
fun taxQuoteStep() {
  info { "Tax Quote step, spawns async process" }
  publish(rabbitMQ.exchange, rabbitMQ.routingKey, null, Event.Tax)
}

context(StatePersistence, Env, KLogger)
fun postProcess() {
  info { "Post Processing the Quote" }
}
