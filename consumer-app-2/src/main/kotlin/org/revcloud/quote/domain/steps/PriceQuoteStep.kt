/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.quote.domain.steps

import com.salesforce.hydra.Hydra
import mu.KLogger
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.env.Env
import org.revcloud.quote.framework.AsyncStep
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance

context(Hydra<Quote, Event, Action>, RabbitMQInstance, StatePersistence, Env, KLogger)
class PriceQuoteStep : AsyncStep<Quote, Event, Action>() {
  override val stateType = Quote::class.java

  override fun handleEvent(eventToPublish: Event): Event? {
    info { "Price Quote step, spawns async process" }
    return eventToPublish
  }
}
