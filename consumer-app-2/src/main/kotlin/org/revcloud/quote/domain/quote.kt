/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.quote.domain

import kotlinx.serialization.Serializable

@Serializable
sealed class Quote {
  @Serializable object Idle : Quote()

  @Serializable object PersistInProgress : Quote()

  @Serializable object FailedToPersist : Quote()

  @Serializable object PricingInProgress : Quote()

  @Serializable object FailedToPrice : Quote()

  @Serializable object TaxInProgress : Quote()

  @Serializable object FailedToTax : Quote()

  @Serializable object Completed : Quote()
}

@Serializable
sealed class Event {
  @Serializable class Place(val quotePayload: Map<String, String>) : Event()

  @Serializable class Persist(val quotePayload: Map<String, String>) : Event()

  @Serializable
  class PersistSuccess(
    val prePersist: Map<String, String>,
    val persistResult: Map<String, String>
  ) : Event()

  @Serializable data object PersistFailed : Event()

  @Serializable data object Price : Event()

  @Serializable class PricingSuccess(val prePricing: Map<String, String>) : Event()

  @Serializable data object PricingFailed : Event()

  @Serializable data object Tax : Event()

  @Serializable data object TaxFailed : Event()

  @Serializable data object TaxSuccess : Event()
}

@Serializable
sealed class Action {
  @Serializable data object PersistQuoteSync : Action()

  @Serializable data object PersistQuoteAsync : Action()

  @Serializable data object OnPersistFailed : Action()

  @Serializable data object PriceQuote : Action()

  @Serializable data object OnPriceFailed : Action()

  @Serializable data object TaxQuote : Action()

  @Serializable data object OnTaxFailed : Action()

  @Serializable data object OnCompleted : Action()
}
