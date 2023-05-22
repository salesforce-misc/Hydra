package org.revcloud.quote.domain

import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import kotlin.random.Random

context(RabbitMQInstance, Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
fun handlePersistQuote(event: Event.Persist) {
  info { "Consumed event: $event to Persist Quote" }
  val exitEvent = if (Random.nextBoolean()) Event.PersistSuccess(mapOf("id" to "1"), mapOf("id" to "2")) else Event.PersistFailed
  transition(exitEvent) // callBack
}

context(RabbitMQInstance, Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
fun handlePriceQuote(event: Event.Price) {
  info { "Consumed event: $event to Price Quote" }
  val exitEvent = if (Random.nextBoolean()) Event.PricingSuccess(mapOf("id" to "1")) else Event.PricingFailed
  transition(exitEvent)
}

context(RabbitMQInstance, Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
fun handleTaxQuote(event: Event.Tax) {
  info { "Consumed event: $event to Tax Quote" }
  val exitEvent = if (Random.nextBoolean()) Event.TaxSuccess else Event.TaxFailed
  transition(exitEvent)
}
