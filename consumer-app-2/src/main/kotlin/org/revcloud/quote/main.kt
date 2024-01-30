/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.quote

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import io.ktor.server.netty.Netty
import kotlinx.coroutines.awaitCancellation
import org.revcloud.quote.env.Env
import org.revcloud.quote.env.app
import org.revcloud.quote.env.init

fun main(): Unit = SuspendApp {
  val env = Env()
  resourceScope {
    val dependencies = init(env)
    server(Netty, host = env.http.host, port = env.http.port) { app(dependencies) }
    awaitCancellation()
  }
}
