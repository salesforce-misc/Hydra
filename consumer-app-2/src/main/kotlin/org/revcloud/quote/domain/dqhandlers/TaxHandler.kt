package org.revcloud.quote.domain.dqhandlers

import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.domain.priceQuote
import org.revcloud.quote.domain.taxQuote
import org.revcloud.quote.env.Env
import org.revcloud.quote.framework.DQHandler
import org.revcloud.quote.repo.StatePersistence

context(Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
class TaxHandler: DQHandler<Quote, Event, Action>() {
  override val stateType = Quote::class.java
  override fun handleEvent(eventToConsume: Event): Event {
    return taxQuote(eventToConsume as Event.Tax)
  }
}
