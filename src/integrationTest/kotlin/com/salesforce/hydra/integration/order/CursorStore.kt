/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import javax.sql.DataSource
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Shared sealed-class-aware codec; reused by the transport so the wire and the store agree. */
val orderJson: Json = Json { encodeDefaults = true }

/** Terminal status of a run; the store advances this alongside the state. */
enum class RunStatus {
  RUNNING,
  COMPLETED,
  CANCELLED,
  FAILED,
}

/** Snapshot of a run's durable position — the SOURCE OF TRUTH for where Hydra picks back up. */
data class Cursor(
  val runId: String,
  val state: OrderState,
  val status: RunStatus,
  val version: Long,
)

private const val RUN_ID_MAX_LENGTH = 64
private const val STATUS_MAX_LENGTH = 16

/** Single-row-per-run table; state is a serialized [OrderState] (TEXT, not jsonb). */
object OrderRun : Table("order_run") {
  val runId = varchar("run_id", RUN_ID_MAX_LENGTH)
  val currentState = text("current_state")
  val status = varchar("status", STATUS_MAX_LENGTH)
  val version = long("version")
  override val primaryKey = PrimaryKey(runId)
}

/**
 * Durable cursor store for order runs, backed by Postgres via Exposed.
 *
 * The CAS contract on [advance]: a non-null return means THIS call advanced the run; a null return
 * means another worker already advanced past [expectedVersion] (or the run was deleted) — a normal
 * idempotent-skip / resume signal, NOT an error.
 */
class CursorStore(dataSource: DataSource) {
  private val db = Database.connect(datasource = dataSource)

  fun createSchema(): Unit = transaction(db) { SchemaUtils.create(OrderRun) }

  /** Insert a fresh row at version 0, RUNNING; returns the seeded cursor. */
  fun seed(runId: String, initial: OrderState): Cursor =
    transaction(db) {
      val encodedState = orderJson.encodeToString(OrderState.serializer(), initial)
      OrderRun.insert {
        it[OrderRun.runId] = runId
        it[currentState] = encodedState
        it[status] = RunStatus.RUNNING.name
        it[version] = 0L
      }
      Cursor(runId = runId, state = initial, status = RunStatus.RUNNING, version = 0L)
    }

  /** Returns the current cursor, or null if no row exists for [runId]. */
  fun load(runId: String): Cursor? =
    transaction(db) {
      OrderRun.selectAll()
        .where { OrderRun.runId eq runId }
        .singleOrNull()
        ?.let { row ->
          Cursor(
            runId = row[OrderRun.runId],
            state = orderJson.decodeFromString(OrderState.serializer(), row[OrderRun.currentState]),
            status = RunStatus.valueOf(row[OrderRun.status]),
            version = row[OrderRun.version],
          )
        }
    }

  /**
   * Compare-and-set the cursor: succeeds iff the row's version still equals [expectedVersion].
   * Returns the new cursor on success, or null if the CAS lost (someone else advanced first).
   */
  fun advance(
    runId: String,
    expectedVersion: Long,
    newState: OrderState,
    newStatus: RunStatus,
  ): Cursor? =
    transaction(db) {
      val nextVersion = expectedVersion + 1
      val encodedState = orderJson.encodeToString(OrderState.serializer(), newState)
      val updatedRows =
        OrderRun.update({ (OrderRun.runId eq runId) and (OrderRun.version eq expectedVersion) }) {
          it[currentState] = encodedState
          it[status] = newStatus.name
          it[version] = nextVersion
        }
      if (updatedRows == 1) {
        Cursor(runId = runId, state = newState, status = newStatus, version = nextVersion)
      } else {
        null
      }
    }
}
