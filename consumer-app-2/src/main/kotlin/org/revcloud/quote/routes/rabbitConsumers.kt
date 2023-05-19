package org.revcloud.quote.routes

import io.ktor.server.application.Application
import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.consume
import pl.jutupe.ktor_rabbitmq.rabbitConsumer
import kotlin.random.Random

context(Application, Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
fun rabbitConsumers() = rabbitConsumer {
  consume<Event.Place>(rabbitMQ.queue) { event ->
    info { "Consumed event: $event" }
    eventHandler(event)
  }
  consume<Event.Persist>(rabbitMQ.queue) { event ->
    info { "Consumed event: $event to Persist Quote" }
    eventHandler(Event.PersistSuccess(mapOf("id" to "1")))
  }
  consume<Event.Price>(rabbitMQ.queue) { event ->
    info { "Consumed event: $event to Price Quote" }
    if (Random.nextBoolean()) {
      eventHandler(Event.PricingSuccess(mapOf("id" to "1")))
    } else {
      eventHandler(Event.PricingFailed)
    }
  }
  consume<Event.Tax>(rabbitMQ.queue) { event ->
    info { "Consumed event: $event to Tax Quote" }
    if (Random.nextBoolean()) {
      eventHandler(Event.TaxSuccess)
    } else {
      eventHandler(Event.TaxFailed)
    }
  }
}
