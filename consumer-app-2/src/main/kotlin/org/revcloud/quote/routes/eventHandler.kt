package org.revcloud.quote.routes

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.hydra.statemachine.Transition
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.domain.persistQuoteStep
import org.revcloud.quote.domain.postProcess
import org.revcloud.quote.domain.priceQuoteStep
import org.revcloud.quote.domain.taxQuoteStep
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance

context(RabbitMQInstance, Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
fun eventHandler(event: Event) {
  val quote: Quote = state // * NOTE 08/05/23 gopala.akshintala: This is needed for encoding below
  runBlocking { insert(Json.encodeToString(quote)) }

  val transition = transition(event)
  val validTransition = (transition as? Transition.Valid) ?: return
  when (validTransition.action) {
    Action.PersistQuote -> persistQuoteStep()
    Action.PriceQuote -> priceQuoteStep()
    Action.TaxQuote -> taxQuoteStep()
    Action.OnPersistFailed, Action.OnPriceFailed, Action. -> postProcess()
    else -> throw IllegalArgumentException("Unknown state")
  }
}
