package org.revcloud.quote.domain

import mu.KLogger
import kotlin.random.Random

fun persistQuote(event: Event.Persist): Event {
  return if (Random.nextBoolean()) Event.PersistSuccess(mapOf("id" to "1"), mapOf("id" to "2")) else Event.PersistFailed
}

fun priceQuote(event: Event.Price): Event {
  return if (Random.nextBoolean()) Event.PricingSuccess(mapOf("id" to "1")) else Event.PricingFailed
}

fun taxQuote(event: Event.Tax): Event {
  return if (Random.nextBoolean()) Event.TaxSuccess else Event.TaxFailed
}

context(KLogger)
fun postProcess() {
  info { "Post Processing the Quote" }
}
