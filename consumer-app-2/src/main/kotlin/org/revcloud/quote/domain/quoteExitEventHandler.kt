package org.revcloud.quote.domain

import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.hydra.statemachine.Transition
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance

context(Hydra<Quote, Event, Action>, RabbitMQInstance, StatePersistence, Env, KLogger)
fun quoteTransitionHandler(transition: Transition<Quote, Event, Action>) {
  val validTransition = (transition as? Transition.Valid) ?: return
  when (validTransition.action) {
  Action.PersistQuoteSync -> persistQuoteSyncStep(validTransition.event as Event.Place)
    Action.PersistQuoteAsync -> persistQuoteAsyncStep(validTransition.event as Event.Place)
    Action.PriceQuote -> priceQuoteStep()
    Action.TaxQuote -> taxQuoteStep()
    Action.OnPersistFailed, Action.OnPriceFailed, Action.OnTaxFailed, Action.OnCompleted -> postProcess()
    else -> postProcess()
  }
}
