/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: 
 *           Apache License Version 2.0 
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package org.revcloud.quote.routes

import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.effect
import arrow.core.raise.fold
import com.salesforce.hydra.Hydra
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import mu.KLogger
import org.revcloud.quote.domain.Action
import org.revcloud.quote.domain.Event
import org.revcloud.quote.domain.Quote
import org.revcloud.quote.env.Env
import org.revcloud.quote.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.publish

context(Application, Hydra<Quote, Event, Action>, StatePersistence, Env, KLogger)
fun quoteRoutes() = routing {
  post("/quote/place") {
    respond(HttpStatusCode.Created) {
      val event = receiveCatching<Event>()
      info { "Received Event: $event" }
      call.publish(rabbitMQ.exchange, rabbitMQ.exchange, null, event)
    }
  }
}

typealias KtorCtx = PipelineContext<Unit, ApplicationCall>

context(KtorCtx)
suspend inline fun <reified A : Any> respond(
  status: HttpStatusCode,
  crossinline block: suspend Raise<DomainError>.() -> A
): Unit = effect { block(this) }.fold({ call.respond(status, it) }) { call.respond(status, it) }

context(Raise<IncorrectJson>)
@OptIn(ExperimentalSerializationApi::class)
private suspend inline fun <reified A : Any> PipelineContext<Unit, ApplicationCall>
  .receiveCatching(): A =
  catch({ call.receive() }) { e: MissingFieldException -> raise(IncorrectJson(e)) }

sealed interface DomainError

sealed interface ValidationError : DomainError

@OptIn(ExperimentalSerializationApi::class)
data class IncorrectJson(val exception: MissingFieldException) : ValidationError
