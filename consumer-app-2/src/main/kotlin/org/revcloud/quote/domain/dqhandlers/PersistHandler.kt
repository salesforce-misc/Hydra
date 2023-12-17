package org.revcloud.quote.domain.dqhandlers

import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.domain.persistQuote
import org.revcloud.quote.env.Env
import org.revcloud.quote.framework.DQHandler
import org.revcloud.quote.repo.StatePersistence

context(Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
class PersistHandler : DQHandler<Quote, Event, Action>() {
  override val stateType = Quote::class.java

  override fun handleEvent(eventToConsume: Event): Event? {
    info { "Consumed event: $eventToConsume to Persist Quote" }
    return persistQuote(
      eventToConsume as Event.Persist
    ) // ! TODO 22/05/23 gopala.akshintala: handle if it's not the expected type
  }
}
