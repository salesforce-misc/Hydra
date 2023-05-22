package org.revcloud.quote.domain

import kotlinx.serialization.Serializable


@Serializable
sealed class Quote {
  @Serializable
  object Idle: Quote()

  @Serializable
  object PersistInProgress: Quote()

  @Serializable
  object FailedToPersist: Quote()
  
  @Serializable
  object PricingInProgress: Quote()

  @Serializable
  object FailedToPrice: Quote()

  @Serializable
  object TaxInProgress: Quote()

  @Serializable
  object FailedToTax: Quote()

  @Serializable
  object Completed: Quote()
}

@Serializable
sealed class Event {
  @Serializable
  class Place(val quotePayload: Map<String, String>): Event()

  @Serializable
  class Persist(val quotePayload: Map<String, String>): Event()

  @Serializable
  class PersistSuccess(val prePersist: Map<String, String>, val persistResult: Map<String, String>): Event()

  @Serializable
  object PersistFailed: Event()

  @Serializable
  object Price: Event()

  @Serializable
  class PricingSuccess(val prePricing: Map<String, String>): Event()

  @Serializable
  object PricingFailed: Event()

  @Serializable
  object Tax: Event()

  @Serializable
  object TaxFailed: Event()

  @Serializable
  object TaxSuccess: Event()
}



@Serializable
sealed class Action {
  @Serializable
  object PersistQuoteSync: Action()
  
  @Serializable
  object PersistQuoteAsync: Action()

  @Serializable
  object OnPersistFailed: Action()

  @Serializable
  object PriceQuote: Action()

  @Serializable
  object OnPriceFailed: Action()

  @Serializable
  object TaxQuote: Action()

  @Serializable
  object OnTaxFailed: Action()

  @Serializable
  object OnCompleted: Action()
}
