package org.revcloud.quote.routes

import com.salesforce.hydra.Hydra
import io.ktor.server.application.Application
import mu.KLogger
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.domain.dqhandlers.PersistHandler
import org.revcloud.quote.domain.dqhandlers.PriceHandler
import org.revcloud.quote.domain.dqhandlers.TaxHandler
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.consume
import pl.jutupe.ktor_rabbitmq.rabbitConsumer

context(Application, Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
fun rabbitConsumers() = rabbitConsumer {
  consume<Event.Place>(rabbitMQ.queue) { event ->
    info { "Consumed event: $event" } // * NOTE 22/05/23 gopala.akshintala: Start for Place Quote
    transition(event)
  }
  consume<Event.Persist>(rabbitMQ.queue) { event -> PersistHandler().execute(event) }
  consume<Event.Price>(rabbitMQ.queue) { event -> PriceHandler().execute(event) }
  consume<Event.Tax>(rabbitMQ.queue) { event -> TaxHandler().execute(event) }
}
