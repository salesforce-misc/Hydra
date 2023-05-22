package org.revcloud.quote.domain.steps

import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.env.Env
import org.revcloud.quote.framework.AsyncStep
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance

context(Hydra<Quote, Event, Action>, RabbitMQInstance, StatePersistence, Env, KLogger)
class PriceQuoteStep: AsyncStep() {
  override fun handleEvent(eventToPublish: Event): Event? {
    info { "Price Quote step, spawns async process" }
    return eventToPublish
  }
}
