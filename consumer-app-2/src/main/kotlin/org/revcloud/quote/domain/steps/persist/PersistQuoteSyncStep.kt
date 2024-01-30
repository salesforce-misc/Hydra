/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.quote.domain.steps.persist

import com.salesforce.hydra.Hydra
import mu.KLogger
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.domain.persistQuote
import org.revcloud.quote.env.Env
import org.revcloud.quote.framework.SyncStep
import org.revcloud.quote.repo.StatePersistence

context(Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
class PersistQuoteSyncStep : SyncStep<Quote, Event, Action>() {
  override val stateType = Quote::class.java

  override fun handleEvent(event: Event): Event? {
    val placeEvent =
      (event as? Event.Place)
        ?: return null // ! TODO 22/05/23 gopala.akshintala: Use an INVALID event
    info { "Persist Quote step synchronously" }
    return persistQuote(Event.Persist(placeEvent.quotePayload))
  }
}
