package org.revcloud.app.routes

import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.effect
import arrow.core.raise.fold
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
import org.revcloud.app.domain.Action
import org.revcloud.app.env.Env
import org.revcloud.app.repo.StatePersistence
import pl.jutupe.ktor_rabbitmq.publish

context(Application, StatePersistence, Env, KLogger)
fun eventRoutes() = routing {
  post("/place") {
    respond(HttpStatusCode.Created) {
      val action = receiveCatching<Action>()
      info { "Received Action: $action" }
      call.publish(rabbitMQ.exchange, rabbitMQ.exchange, null, action)
    }
  }
}

typealias KtorCtx = PipelineContext<Unit, ApplicationCall>

context(KtorCtx)
suspend inline fun <reified A : Any> respond(
  status: HttpStatusCode,
  crossinline block: suspend context(Raise<DomainError>) () -> A
): Unit = effect {
  block(this)
}.fold({ call.respond(status, it) }, { call.respond(status, it) })

context(Raise<IncorrectJson>)
@OptIn(ExperimentalSerializationApi::class)
private suspend inline fun <reified A : Any> PipelineContext<Unit, ApplicationCall>.receiveCatching(): A =
  catch({ call.receive() }) { e: MissingFieldException -> raise(IncorrectJson(e)) }

sealed interface DomainError

sealed interface ValidationError : DomainError

@OptIn(ExperimentalSerializationApi::class)
data class IncorrectJson(val exception: MissingFieldException) : ValidationError