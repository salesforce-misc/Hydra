/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.hydra.integration.order.java;

import static org.jooq.impl.DSL.constraint;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderState;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

/**
 * Durable cursor store backed by Postgres via jOOQ (no codegen).
 *
 * <p>The CAS contract on {@link #advance}: a non-null return means THIS call advanced the run; a
 * null return means another worker already advanced past {@code expectedVersion} (or the row is
 * gone) — a normal idempotent-skip / resume signal, NOT an error. Domain outcomes are RETURNS, not
 * throws; only genuinely exceptional checked SQL/JSON failures are wrapped in RuntimeException.
 */
final class CursorStore {

  /** Terminal status of a run; the store advances this alongside the state. */
  enum RunStatus {
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
  }

  /** Snapshot of a run's durable position — the SOURCE OF TRUTH for where Hydra picks back up. */
  record Cursor(String runId, OrderState state, RunStatus status, long version) {}

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final Table<?> ORDER_RUN = table(name("order_run"));
  private static final Field<String> RUN_ID = field(name("run_id"), SQLDataType.VARCHAR);
  private static final Field<String> CURRENT_STATE = field(name("current_state"), SQLDataType.CLOB);
  private static final Field<String> STATUS = field(name("status"), SQLDataType.VARCHAR);
  private static final Field<Long> VERSION = field(name("version"), SQLDataType.BIGINT);

  private final DataSource dataSource;

  CursorStore(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  void createSchema() {
    run(
        ctx -> {
          ctx.createTableIfNotExists(ORDER_RUN)
              .column(RUN_ID)
              .column(CURRENT_STATE)
              .column(STATUS)
              .column(VERSION)
              .constraints(constraint("order_run_pk").primaryKey(RUN_ID))
              .execute();
          return null;
        });
  }

  /** Insert a fresh row at version 0, RUNNING; returns the seeded cursor. */
  Cursor seed(String runId, OrderState initial) {
    return run(
        ctx -> {
          ctx.insertInto(ORDER_RUN)
              .columns(RUN_ID, CURRENT_STATE, STATUS, VERSION)
              .values(runId, toJson(initial), RunStatus.RUNNING.name(), 0L)
              .execute();
          return new Cursor(runId, initial, RunStatus.RUNNING, 0L);
        });
  }

  /** Returns the current cursor, or null if no row exists for {@code runId}. */
  Cursor load(String runId) {
    return run(
        ctx -> {
          final var row = ctx.selectFrom(ORDER_RUN).where(RUN_ID.eq(runId)).fetchOne();
          return row == null
              ? null
              : new Cursor(
                  row.get(RUN_ID),
                  fromJson(row.get(CURRENT_STATE)),
                  RunStatus.valueOf(row.get(STATUS)),
                  row.get(VERSION));
        });
  }

  /**
   * Compare-and-set the cursor: succeeds iff the row's version still equals {@code
   * expectedVersion}. Returns the new cursor on success, or null if the CAS lost (someone else
   * advanced first).
   */
  Cursor advance(String runId, long expectedVersion, OrderState newState, RunStatus newStatus) {
    return run(
        ctx -> {
          final var nextVersion = expectedVersion + 1L;
          final var encodedState = toJson(newState);
          final var updatedRows =
              ctx.update(ORDER_RUN)
                  .set(CURRENT_STATE, encodedState)
                  .set(STATUS, newStatus.name())
                  .set(VERSION, nextVersion)
                  .where(RUN_ID.eq(runId).and(VERSION.eq(expectedVersion)))
                  .execute();
          return updatedRows == 1 ? new Cursor(runId, newState, newStatus, nextVersion) : null;
        });
  }

  @FunctionalInterface
  private interface Work<T> {
    T apply(DSLContext ctx);
  }

  private <T> T run(Work<T> work) {
    try (final var connection = dataSource.getConnection()) {
      return work.apply(DSL.using(connection, SQLDialect.POSTGRES));
    } catch (SQLException sqlException) {
      throw new RuntimeException(sqlException);
    }
  }

  private static String toJson(OrderState state) {
    try {
      return JSON.writeValueAsString(state);
    } catch (JsonProcessingException jsonProcessingException) {
      throw new RuntimeException(jsonProcessingException);
    }
  }

  private static OrderState fromJson(String json) {
    try {
      return JSON.readValue(json, OrderState.class);
    } catch (JsonProcessingException jsonProcessingException) {
      throw new RuntimeException(jsonProcessingException);
    }
  }
}
