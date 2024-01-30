/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.quote.domain

import kotlin.random.Random
import mu.KLogger

fun persistQuote(event: Event.Persist): Event {
  return if (Random.nextBoolean()) Event.PersistSuccess(mapOf("id" to "1"), mapOf("id" to "2"))
  else Event.PersistFailed
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
