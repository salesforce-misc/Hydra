/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.order.env

import arrow.fx.coroutines.continuations.ResourceScope
import com.salesforce.hydra.Hydra
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import mu.KLogger
import mu.KotlinLogging
import org.revcloud.order.domain.Action
import org.revcloud.order.domain.Event
import org.revcloud.order.domain.Order
import org.revcloud.order.domain.OrderMachine
import org.revcloud.order.repo.StatePersistence
import org.revcloud.order.repo.statePersistence

class Dependencies(
  val env: Env,
  val statePersistence: StatePersistence,
  val orderMachine: Hydra<Order, Event, Action>,
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
  val orderMachine = OrderMachine.orderMachine

  return Dependencies(
    env,
    statePersistence(sqlDelight.stateQueries),
    orderMachine,
    healthCheck,
    KotlinLogging.logger {}
  )
}
