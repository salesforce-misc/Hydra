package org.revcloud.quote.domain

import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.hydra.statemachine.Transition
import org.revcloud.quote.domain.steps.PriceQuoteStep
import org.revcloud.quote.domain.steps.TaxQuoteStep
import org.revcloud.quote.domain.steps.persist.PersistQuoteAsyncStep
import org.revcloud.quote.domain.steps.persist.PersistQuoteSyncStep
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance

context(Hydra<Quote, Event, Action>, RabbitMQInstance, StatePersistence, Env, KLogger)
fun quoteTransitionHandler(transition: Transition<Quote, Event, Action>) {
  val validTransition = (transition as? Transition.Valid) ?: return
  when (validTransition.action) {
    Action.PersistQuoteSync -> PersistQuoteSyncStep().execute(validTransition.event as Event.Place)
    Action.PersistQuoteAsync -> PersistQuoteAsyncStep().execute(validTransition.event as Event.Place)
    Action.PriceQuote -> PriceQuoteStep().execute(Event.Price)
    Action.TaxQuote -> TaxQuoteStep().execute(Event.Tax)
    Action.OnPersistFailed, Action.OnPriceFailed, Action.OnTaxFailed, Action.OnCompleted -> postProcess()
    else -> postProcess()
  }
}
