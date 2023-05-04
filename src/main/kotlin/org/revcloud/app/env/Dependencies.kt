package org.revcloud.app.env

import arrow.fx.coroutines.continuations.ResourceScope
import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.hikari.HikariConnectionsHealthCheck
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.seconds

class Dependencies(
  val healthCheck: HealthCheckRegistry
)

suspend fun ResourceScope.dependencies(env: Env): Dependencies {
  val hikari = hikari(env.dataSource)
  val sqlDelight = sqlDelight(hikari)

  val checks =
    HealthCheckRegistry(Dispatchers.Default) {
      register(HikariConnectionsHealthCheck(hikari, 1), 5.seconds)
    }

  return Dependencies(
    checks
  )
}
