package org.revcloud.quote.framework

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KLogger
import org.revcloud.hydra.Hydra
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence

context(Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
abstract class DQHandler {
  
  protected abstract fun handleEvent(eventToConsume: Event): Event?
  
  fun execute(event: Event): Event? {
    info { "Consumed Event: $event and doing some pre stuff" }
    persistState()
    return runCatching {
      val exitEvent = handleEvent(event)!! // ! TODO 22/05/23 gopala.akshintala: Make it non-nullable 
      transition(exitEvent)// * NOTE 22/05/23 gopala.akshintala: Callback 
    }.also { 
      info { "Doing post stuff" }
      persistState()
    }.getOrNull()?.event
  }

  private fun persistState() {
    val quote: Quote = state // * NOTE 08/05/23 gopala.akshintala: This is needed for encoding below 
    runBlocking { insert(Json.encodeToString(quote)) }
  }

}
