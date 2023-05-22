package org.revcloud.quote.routes

import io.ktor.server.application.Application
import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.domain.handlePersistQuote
import org.revcloud.quote.domain.handlePriceQuote
import org.revcloud.quote.domain.handleTaxQuote
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.consume
import pl.jutupe.ktor_rabbitmq.rabbitConsumer

context(Application, Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
fun rabbitConsumers() = rabbitConsumer {
  consume<Event.Place>(rabbitMQ.queue) { event ->
    info { "Consumed event: $event" }
    exitEventHandler(event)
  }
  consume<Event.Persist>(rabbitMQ.queue) { event ->
    handlePersistQuote(event)
  }
  consume<Event.Price>(rabbitMQ.queue) { event ->
    handlePriceQuote(event)
  }
  consume<Event.Tax>(rabbitMQ.queue) { event ->
    handleTaxQuote(event)
  }
}
