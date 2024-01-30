/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.quote.routes

import com.sksamuel.cohort.HealthCheckRegistry
import com.sksamuel.cohort.ktor.Cohort
import io.ktor.server.application.Application
import io.ktor.server.application.install

context(Application)
fun health(healthCheck: HealthCheckRegistry) {
  install(Cohort) { healthcheck("/readiness", healthCheck) }
}
