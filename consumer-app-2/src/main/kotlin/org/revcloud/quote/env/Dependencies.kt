/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.quote.env

import arrow.fx.coroutines.continuations.ResourceScope
import com.salesforce.hydra.Hydra
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import mu.KLogger
import mu.KotlinLogging
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.PlaceQuoteMachine
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.repo.StatePersistence
import org.revcloud.quote.repo.statePersistence
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance

class Dependencies(
  val env: Env,
  val statePersistence: StatePersistence,
  val rabbitMq: RabbitMQInstance,
  val quoteMachine: Hydra<Quote, Event, Action>,
  val healthCheck: HealthCheckRegistry,
  val logger: KLogger
)

context(ResourceScope)
suspend fun init(env: Env): Dependencies {
  val hikari = hikari(env.dataSource)
  val healthCheck =
    HealthCheckRegistry(Dispatchers.Default) {
      register(HikariConnectionsHealthCheck(hikari, 1), 5.seconds)
    }
  val sqlDelight = sqlDelight(hikari)
  val statePersistence = statePersistence(sqlDelight.stateQueries)
  val rabbitMq = rabbitMqInstance(env)
  val kLogger = KotlinLogging.logger {}
  val quoteMachine = PlaceQuoteMachine(rabbitMq, statePersistence, env, kLogger)

  return Dependencies(
    env,
    statePersistence,
    rabbitMq,
    quoteMachine.orderHydra,
    healthCheck,
    kLogger
  )
}
