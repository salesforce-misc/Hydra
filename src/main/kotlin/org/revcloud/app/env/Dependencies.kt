package org.revcloud.app.env

import arrow.fx.coroutines.continuations.ResourceScope
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import kotlinx.coroutines.Dispatchers
import org.revcloud.app.repo.StatePersistence
import org.revcloud.app.repo.statePersistence
import kotlin.time.Duration.Companion.seconds

class Dependencies(
  val statePersistence: StatePersistence,
  val healthCheck: HealthCheckRegistry
)

suspend fun ResourceScope.dependencies(env: Env): Dependencies {
  val hikari = hikari(env.dataSource)
  val checks = HealthCheckRegistry(Dispatchers.Default) {
    register(HikariConnectionsHealthCheck(hikari, 1), 5.seconds)
  }

  val sqlDelight = sqlDelight(hikari)
  return Dependencies(
    statePersistence(sqlDelight.stateQueries),
    checks
  )
}
