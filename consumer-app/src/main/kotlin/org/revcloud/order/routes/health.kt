package org.revcloud.order.routes

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.ktor.Cohort
import io.ktor.server.application.Application
import io.ktor.server.application.install

context(Application)
fun health(healthCheck: HealthCheckRegistry) {
  install(Cohort) { healthcheck("/readiness", healthCheck) }
}
