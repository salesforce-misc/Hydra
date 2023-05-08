package org.revcloud.app.env

import arrow.fx.coroutines.continuations.ResourceScope
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import kotlinx.coroutines.Dispatchers
import mu.KLogger
import mu.KotlinLogging
import org.revcloud.app.domain.Action
import org.revcloud.app.domain.Order
import org.revcloud.app.domain.OrderMachine
import org.revcloud.app.domain.SideEffect
import org.revcloud.app.repo.StatePersistence
import org.revcloud.app.repo.statePersistence
import org.revcloud.hydra.Hydra
import kotlin.time.Duration.Companion.seconds

class Dependencies(
  val env: Env,
  val statePersistence: StatePersistence,
  val orderMachine: Hydra<Order, Action, SideEffect>,
  val healthCheck: HealthCheckRegistry,
  val logger: KLogger
)

context(ResourceScope)
suspend fun init(env: Env): Dependencies {
  val hikari = hikari(env.dataSource)
  val healthCheck = HealthCheckRegistry(Dispatchers.Default) { register(HikariConnectionsHealthCheck(hikari, 1), 5.seconds) }
  val sqlDelight = sqlDelight(hikari)
  val orderMachine = OrderMachine.orderMachine;

  return Dependencies(
    env,
    statePersistence(sqlDelight.stateQueries),
    orderMachine,
    healthCheck,
    KotlinLogging.logger {}
  )
}
