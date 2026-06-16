# Async Orchestrator PoC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **GLOBAL CODING DIRECTIVES (apply to EVERY task — these override any code snippet below that conflicts).** The snippets in each task are *intent specs*, not literal copy-paste; write the code in the style mandated here.
>
> 1. **Functional style, immutability-first.** Transform, don't mutate. Never mutate a parameter — derive and return new values. Prefer `map`/`filter`/`flatMap`/`fold`/`associate`/`groupBy`/`firstOrNull` over imperative loops and mutable accumulators. Reserve mutation for the genuinely concurrent recording sinks (`Effects`) where it's intrinsic. Build immutable data flow: pass state through parameters, return new state. Use `when`/`if` *expressions* that return, not statement blocks that mutate.
> 2. **Return results; don't throw for domain outcomes.** A function's return type should encode the outcomes a caller routinely handles. Kotlin: model expected outcomes in the return type (sealed result / nullable for plain absence with `?:` fallback) rather than throwing; reserve exceptions for genuinely exceptional, unrecoverable conditions (e.g. the deliberate `SimulatedCrash` test hook, or wrapping a checked `SQLException` at a boundary). Use `firstOrNull`/`singleOrNull` + elvis over throwing on "not found". Java: where a domain error carries a reason, prefer returning a typed result over throwing; absence → `Optional`/nullable/empty.
> 3. **Newest syntax the repo's language level allows — and no newer.** JDK 21 language level, Kotlin per the catalog. Reach freely for the long-finalized floor (records, `switch` *expressions* with `->`, pattern-matching `instanceof`/`switch` with record patterns, text blocks, `final var` for *every* Java local, `List.of`/`Map.of`, `.toList()`, `merge`/`computeIfAbsent`; Kotlin: `val` by default, data classes, sealed hierarchies, single-expression functions, scope functions). For anything past that floor, **verify with the context7 MCP** that the feature is *finalized* (not preview) at JDK 21 / the catalog's Kotlin level before using it — do not trust memory on the preview→final boundary.
> 4. **Kotlin style mirrors the Java house style (`my-java-coding-style`), translated.** `final var` ↔ `val`; streams ↔ Kotlin stdlib collection ops; `Either`/`Optional` ↔ sealed-result/nullable. Name for **intent, not type or brevity** (`expectedVersion`, `advancedCursor`, `eligibleStates` — never `v`, `c`, `tmp`, `data`). Single-expression declarative functions where they read clearly. Javadoc/KDoc only where it adds a mental model the name+signature can't — anchor non-obvious contracts with a one-line analogy or an input→output example; never restate the name. Use the house comment tags when leaving notes: `// * NOTE <d MMM yyyy> gopala.akshintala: …`, `// ! TODO …`, `// ! FIXME …`.
> 5. **Dependencies are at latest (see Task 2).** The version catalog is being upgraded to latest in Task 2; write against current (not deprecated) APIs of every library, and verify any non-trivial library API against **context7** rather than memory.
>
> If applying a directive forces a real deviation from a task's snippet (an API moved, a feature isn't finalized, a snippet mutates a parameter), make the change, keep the task's *intent*, and note it in your report.

**Goal:** Prove Hydra's central async claim — *"a run that dies mid-flight resumes from exactly where it stopped"* — with a real, runnable PoC: the same order-fulfillment DAG driven by two idiomatic orchestrators (Kotlin/Exposed, Java/jOOQ) over real RabbitMQ + Postgres (Testcontainers), where the durable cursor in the DB is the only source of truth.

**Architecture:** Hydra is consulted as a pure router (`readTransitionAndNotifyListeners`) — its in-memory `AtomicReference` is never read. A worker pulls a message from RabbitMQ, reads the run's `fromState` from the Postgres cursor row, asks Hydra for the next `Transition`, CAS-advances the cursor (`UPDATE ... WHERE version = ?`), performs the emitted action's side effect, then enqueues the next event. A crash between CAS-advance and enqueue triggers RabbitMQ redelivery; a *fresh* worker reads the already-advanced cursor and **resumes from it** (re-enqueues the next step without re-performing the side effect) — exactly-once effects, zero in-memory memory.

**Tech Stack:** Kotlin 2.3 + Java 21 · Hydra (this repo) · kotlin-exposed 1.0.0-beta-4 · jOOQ 3.20.3 (no codegen) · RabbitMQ amqp-client 5.25.0 · Postgres JDBC 42.7.7 · Testcontainers 1.21.3 (postgresql, rabbitmq) · kotlinx-serialization-json 1.9.0 (Kotlin) · Jackson 2.19.0 (Java) · JUnit 5 · Truth.

---

## Deviations from the spec (intentional, with rationale)

1. **Message shape corrected.** Spec proposed `{runId, fromStateJson, exitEvent}`. That makes the *message* carry `fromState`, contradicting "DB is the source of truth," and a blind version-skip on redelivery stalls the run. **Corrected protocol:** message = `{runId, event, expectedVersion}`. Worker reads `fromState` **from the cursor**. If `cursorVersion == expectedVersion` → fresh step (advance + perform effect + enqueue next). If `cursorVersion > expectedVersion` → already advanced (a prior crash happened after CAS): **resume** by re-deriving and re-enqueuing the next event from the persisted state, **without** re-performing the side effect. This is the literal proof of resurrection + exactly-once.
2. **State stored as `TEXT`, not `jsonb`.** The cursor never queries inside the JSON, so text storage removes the Exposed-json / jOOQ-JSONB API risk. Column holds the serialized `OrderState`.
3. **Per-language serializer.** Kotlin → kotlinx-serialization (sealed-native, the truest "latest Kotlin stack"); Java → Jackson (records + `@JsonTypeInfo`). Avoids forcing moshix codegen into the test source set.
4. **No coroutines.** RabbitMQ's client already dispatches consumer callbacks on its own threads — genuinely "a different thread" — so the worker needs no extra concurrency lib.
5. **Library enhancement (per user's offer).** Add a 2-arg `readTransitionAndNotifyListeners(fromState, event)` overload to `Hydra.kt` that derives the class internally, removing the redundant `finishedStep.getClass(), finishedStep` the doc currently shows. Built + unit-tested in the main suite (Task 1), then consumed by the PoC and doc.

---

## File Structure

**Library (main source — Task 1):**
- Modify: `src/main/kotlin/com/salesforce/hydra/Hydra.kt` — add 2-arg overload.
- Modify: `src/test/java/com/salesforce/hydra/HydraTest.java` — test the overload.

**Build wiring (Task 2):**
- Modify: `gradle/libs.versions.toml` — versions, libraries, bundles.
- Modify: `build.gradle.kts` — `integrationTest` suite deps + `kotlin("plugin.serialization")`.

**Kotlin PoC (`src/integrationTest/kotlin/com/salesforce/hydra/integration/order/`):**
- `OrderDomain.kt` — sealed State/Event/Action + `@Serializable`.
- `OrderMachine.kt` — the Hydra DAG factory.
- `CursorStore.kt` — Exposed table + `seed`/`load`/`advance` (CAS).
- `Transport.kt` — RabbitMQ publish/consume + message codec.
- `Effects.kt` — recording side-effect sink.
- `Orchestrator.kt` — the dispatch loop (corrected protocol).
- `OrderOrchestratorDemo.kt` — `main()` demo (scenario 1).

**Java PoC (`src/integrationTest/java/com/salesforce/hydra/integration/order/java/`):**
- `OrderDomain.java`, `OrderMachine.java`, `CursorStore.java`, `Transport.java`, `Effects.java`, `Orchestrator.java`, `OrderOrchestratorDemo.java` — mirrors of the above, Java idioms.

**Shared test infra (`src/integrationTest/kotlin/.../order/`):**
- `Infra.kt` — Testcontainers singletons (Postgres + RabbitMQ) + Docker `assumeTrue` helper, usable from both Kotlin and Java tests.

**Tests:**
- `src/integrationTest/kotlin/.../order/OrderOrchestratorKtTest.kt` — 3 scenarios.
- `src/integrationTest/java/.../order/java/OrderOrchestratorTest.java` — 3 scenarios.

**Docs (Task 9):**
- Modify: `docs/modules/ROOT/pages/async-orchestration.adoc`.
- Create: `docs/modules/ROOT/images/async-resume.svg`.

---

## Task 1: Library enhancement — 2-arg `readTransitionAndNotifyListeners`

**Files:**
- Modify: `src/main/kotlin/com/salesforce/hydra/Hydra.kt`
- Test: `src/test/java/com/salesforce/hydra/HydraTest.java`

- [ ] **Step 1: Write the failing test**

Add to `HydraTest.java` (after `testReadTransitionDoesNotMutateState`):

```java
  @Test
  @DisplayName("2-arg read-only transition derives the class from the instance")
  void testReadTransitionTwoArgOverload() {
    final var machine = orderMachineStartingAt(new Placed(AMOUNT, ADDRESS));

    final var transition =
        machine.readTransitionAndNotifyListeners(new Placed(AMOUNT, ADDRESS), new PaymentSucceeded());

    assertThat(transition.isValid()).isTrue();
    assertThat(actionOf(transition)).isEqualTo(new ShipParcel(ADDRESS));
    assertThat(machine.getState()).isEqualTo(new Placed(AMOUNT, ADDRESS));
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.salesforce.hydra.HydraTest.testReadTransitionTwoArgOverload"`
Expected: COMPILE FAIL — `readTransitionAndNotifyListeners(OrderState, OrderEvent)` not found.

- [ ] **Step 3: Add the overload**

In `Hydra.kt`, after the existing 3-arg `readTransitionAndNotifyListeners` (line ~72), add:

```kotlin
  fun readTransitionAndNotifyListeners(
    fromState: StateT,
    event: EventT,
  ): Transition<StateT, EventT, ActionT> =
    readTransitionAndNotifyListeners(fromState.javaClass, fromState, event)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.salesforce.hydra.HydraTest.testReadTransitionTwoArgOverload"`
Expected: PASS. Then `./gradlew spotlessApply test` — full suite green.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/salesforce/hydra/Hydra.kt src/test/java/com/salesforce/hydra/HydraTest.java
git commit -m "feat(hydra): add 2-arg readTransitionAndNotifyListeners(fromState, event) overload"
```

---

## Task 2: Build wiring — version catalog + integrationTest deps

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`

- [ ] **Step 0 (per user directive 4): Upgrade the ENTIRE catalog to latest, aligned to the sibling `revoman-root` catalog.** The reference is `/Users/gopala.akshintala/code-clones/work/revoman-root/gradle/libs.versions.toml` — it is the user's known-good "latest" set on the same convention plugins. For every version key present in BOTH repos, adopt revoman's value (newer-stable). Notably: `kotlin 2.3.20-RC → 2.4.0`, `spotless 8.2.1 → 8.6.0`, `arrow → 2.2.3`, `http4k → 6.53.0.0`, `okio → 3.17.0`, `kotest 6.1.3 → 6.1.11`, `mockito → 5.23.0`, `spring → 7.0.8`, `moshix 0.35.0-alpha01 → 0.36.0`, `kover → 0.9.8`, `java-vavr → 1.0.1`, `immutables → 2.12.2`, `graal → 25.0.3`, `kotlin-logging → 8.0.4`, `underscore → 1.122`, `assertj-vavr → 0.5.1`, `kotlinx-datetime → 0.8.0-0.6.x-compat`, `gradle-taskinfo → 3.0.2`. **Deliberately KEEP (revoman keeps these too):** `junit 5.11.4` (do NOT jump to 6.x), `detekt 1.23.8`, `apache-log4j 3.0.0-beta2`, `json-assert 2.0-rc1`, `truth 1.4.5`. Do NOT add revoman-only libraries (snakeyaml, datafaker, mockk, assertj-core) — this is a version bump, not a dependency import. For hydra-only entries absent from revoman (`kotlin-faker`), take the latest (`2.0.0-rc.13`). Verify with `./gradlew dependencyUpdates` (Gradle Versions Plugin — authoritative against the project's real repos; the search.maven.org solr index is stale and must not be used). Then run a FULL build (`./gradlew clean build`) to confirm the Kotlin-compiler + spotless bump keeps the existing suite green BEFORE adding PoC deps.

- [ ] **Step 1: Add versions to `[versions]` in `libs.versions.toml`** (latest, verified via `dependencyUpdates`; bump to newer stable if the plugin reports one)

```toml
exposed = "1.0.0-beta-4"
jooq = "3.20.3"
amqp-client = "5.25.0"
postgres-jdbc = "42.7.7"
testcontainers = "1.21.3"
kotlinx-serialization = "1.9.0"
jackson = "2.19.0"
```

- [ ] **Step 2: Add libraries to `[libraries]`**

```toml
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
jooq = { module = "org.jooq:jooq", version.ref = "jooq" }
amqp-client = { module = "com.rabbitmq:amqp-client", version.ref = "amqp-client" }
postgres-jdbc = { module = "org.postgresql:postgresql", version.ref = "postgres-jdbc" }
testcontainers-bom = { module = "org.testcontainers:testcontainers-bom", version.ref = "testcontainers" }
testcontainers-junit = { module = "org.testcontainers:junit-jupiter" }
testcontainers-postgres = { module = "org.testcontainers:postgresql" }
testcontainers-rabbitmq = { module = "org.testcontainers:rabbitmq" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }
```

- [ ] **Step 3: Add a bundle to `[bundles]`**

```toml
testcontainers = ["testcontainers-junit", "testcontainers-postgres", "testcontainers-rabbitmq"]
```

- [ ] **Step 4: Add the serialization plugin to `[plugins]`**

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 5: Wire `build.gradle.kts`**

Add to the `plugins { }` block:
```kotlin
  alias(libs.plugins.kotlin.serialization)
```

Replace the `register<JvmTestSuite>("integrationTest")` block's `dependencies { }` with:
```kotlin
      dependencies {
        implementation(project())
        implementation(platform(libs.testcontainers.bom))
        implementation(libs.truth)
        implementation(libs.bundles.testcontainers)
        implementation(libs.exposed.core)
        implementation(libs.exposed.jdbc)
        implementation(libs.jooq)
        implementation(libs.amqp.client)
        implementation(libs.postgres.jdbc)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.jackson.databind)
        implementation(libs.bundles.kotlin.logging)
      }
```

- [ ] **Step 6: Verify the build resolves and compiles (no tests yet)**

Run: `./gradlew integrationTestClasses`
Expected: SUCCESS (no sources yet → trivially compiles; deps resolve).

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts
git commit -m "build: wire integrationTest deps (testcontainers, exposed, jooq, rabbitmq, postgres)"
```

---

## Task 3: Kotlin — `OrderDomain.kt` (sealed, serializable)

**Files:**
- Create: `src/integrationTest/kotlin/com/salesforce/hydra/integration/order/OrderDomain.kt`

- [ ] **Step 1: Write the domain types**

```kotlin
/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import kotlinx.serialization.Serializable

// ── State: where the run IS. Carries context threaded forward. ──
@Serializable
sealed interface OrderState

@Serializable data class Validating(val amountCents: Long, val address: String, val sku: String, val qty: Int) : OrderState

@Serializable data class Reserving(val amountCents: Long, val address: String, val sku: String, val qty: Int) : OrderState

@Serializable data class Charging(val amountCents: Long, val address: String, val sku: String, val qty: Int) : OrderState

@Serializable data class Shipping(val amountCents: Long, val address: String) : OrderState

@Serializable data class Notifying(val address: String) : OrderState

@Serializable data object Completed : OrderState

@Serializable data class Cancelled(val reason: String) : OrderState

// ── Event: INPUT — the exit signal of a step. ──
@Serializable
sealed interface OrderEvent

@Serializable data object Validated : OrderEvent

@Serializable data object Reserved : OrderEvent

@Serializable data object OutOfStock : OrderEvent

@Serializable data object Charged : OrderEvent

@Serializable data class PaymentDeclined(val reason: String) : OrderEvent

@Serializable data object Shipped : OrderEvent

@Serializable data object Notified : OrderEvent

// ── Action: OUTPUT — a command the next worker performs. ──
sealed interface OrderAction

data class ReserveInventory(val sku: String, val qty: Int) : OrderAction

data class ChargePayment(val amountCents: Long) : OrderAction

data class ShipParcel(val address: String) : OrderAction

data class SendShipNotice(val address: String) : OrderAction

data class NotifyCustomer(val reason: String) : OrderAction

data class ReleaseInventory(val sku: String, val qty: Int) : OrderAction
```

- [ ] **Step 2: Compile**

Run: `./gradlew integrationTestClasses`
Expected: SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/integrationTest/kotlin/com/salesforce/hydra/integration/order/OrderDomain.kt
git commit -m "feat(poc-kt): order fulfillment domain (sealed, serializable)"
```

---

## Task 4: Kotlin — `OrderMachine.kt` (the Hydra DAG) + a smoke test

**Files:**
- Create: `src/integrationTest/kotlin/com/salesforce/hydra/integration/order/OrderMachine.kt`
- Test: `src/integrationTest/kotlin/com/salesforce/hydra/integration/order/OrderMachineKtTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.salesforce.hydra.integration.order

import com.google.common.truth.Truth.assertThat
import com.salesforce.hydra.statemachine.Transition
import org.junit.jupiter.api.Test

class OrderMachineKtTest {
  @Test
  fun reservingReservedRoutesToChargingEmittingChargePayment() {
    val machine = orderMachine()
    val from = Reserving(4999, "1 Market St", "SKU-1", 1)

    val t = machine.readTransitionAndNotifyListeners(from, Reserved)

    assertThat(t.isValid).isTrue()
    val valid = t as Transition.Valid
    assertThat(valid.toState).isEqualTo(Charging(4999, "1 Market St", "SKU-1", 1))
    assertThat(valid.action).isEqualTo(ChargePayment(4999))
  }

  @Test
  fun reservingOutOfStockRoutesToCancelled() {
    val machine = orderMachine()
    val t = machine.readTransitionAndNotifyListeners(Reserving(4999, "x", "SKU-1", 1), OutOfStock)
    assertThat((t as Transition.Valid).toState).isInstanceOf(Cancelled::class.java)
    assertThat(t.action).isEqualTo(NotifyCustomer("out of stock"))
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew integrationTest --tests "*OrderMachineKtTest"`
Expected: COMPILE FAIL — `orderMachine` unresolved.

- [ ] **Step 3: Write `OrderMachine.kt`**

```kotlin
/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import com.salesforce.hydra.Hydra

/** The whole fulfillment pipeline as one Hydra machine — the map of legal moves. */
fun orderMachine(): Hydra<OrderState, OrderEvent, OrderAction> =
  Hydra.create { mb ->
    mb.initialState(Validating(0, "", "", 0))

    mb.state(Validating::class.java) { sb ->
      sb.on(Validated::class.java) { s, _ ->
        sb.transitionTo(
          Reserving(s!!.amountCents, s.address, s.sku, s.qty),
          ReserveInventory(s.sku, s.qty),
        )
      }
    }

    mb.state(Reserving::class.java) { sb ->
      sb.on(Reserved::class.java) { s, _ ->
        sb.transitionTo(Charging(s!!.amountCents, s.address, s.sku, s.qty), ChargePayment(s.amountCents))
      }
      sb.on(OutOfStock::class.java) { s, _ ->
        sb.transitionTo(Cancelled("out of stock"), NotifyCustomer("out of stock"))
      }
    }

    mb.state(Charging::class.java) { sb ->
      sb.on(Charged::class.java) { s, _ ->
        sb.transitionTo(Shipping(s!!.amountCents, s.address), ShipParcel(s.address))
      }
      sb.on(PaymentDeclined::class.java) { s, e ->
        sb.transitionTo(Cancelled(e.reason), ReleaseInventory(s!!.sku, s.qty))
      }
    }

    mb.state(Shipping::class.java) { sb ->
      sb.on(Shipped::class.java) { s, _ -> sb.transitionTo(Notifying(s!!.address), SendShipNotice(s.address)) }
    }

    mb.state(Notifying::class.java) { sb ->
      sb.on(Notified::class.java) { _, _ -> sb.transitionTo(Completed) }
    }

    mb.state(Completed::class.java) {}
    mb.state(Cancelled::class.java) {}
  }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew spotlessApply integrationTest --tests "*OrderMachineKtTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add src/integrationTest/kotlin/com/salesforce/hydra/integration/order/OrderMachine.kt src/integrationTest/kotlin/com/salesforce/hydra/integration/order/OrderMachineKtTest.kt
git commit -m "feat(poc-kt): order fulfillment Hydra machine + DAG smoke test"
```

---

## Task 5: Kotlin — `CursorStore.kt` (Exposed, TEXT column, CAS advance) + serialization codec

**Files:**
- Create: `src/integrationTest/kotlin/com/salesforce/hydra/integration/order/CursorStore.kt`

> No standalone unit test here (it needs a DB); it is exercised end-to-end in Task 8. This is a pure data-access unit with a small, explicit interface: `seed`, `load`, `advance` (CAS). The `Json` codec is shared with `Transport.kt`.

- [ ] **Step 1: Write `CursorStore.kt`**

```kotlin
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
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** JSON codec shared by the cursor store and the transport. Sealed-class aware. */
val orderJson: Json = Json { encodeDefaults = true }

enum class RunStatus { RUNNING, COMPLETED, CANCELLED, FAILED }

/** A snapshot of the durable cursor — the SOURCE OF TRUTH for a run's position. */
data class Cursor(val runId: String, val state: OrderState, val status: RunStatus, val version: Long)

object OrderRun : Table("order_run") {
  val runId = varchar("run_id", 64)
  val currentState = text("current_state")
  val status = varchar("status", 16)
  val version = long("version")
  override val primaryKey = PrimaryKey(runId)
}

/** Durable cursor store backed by Postgres via Exposed. The DB row — not the JVM — is the truth. */
class CursorStore(dataSource: DataSource) {
  private val db = Database.connect(dataSource)

  fun createSchema() = transaction(db) { SchemaUtils.create(OrderRun) }

  fun seed(runId: String, initial: OrderState): Cursor {
    transaction(db) {
      OrderRun.insert {
        it[OrderRun.runId] = runId
        it[currentState] = orderJson.encodeToString(OrderState.serializer(), initial)
        it[status] = RunStatus.RUNNING.name
        it[version] = 0L
      }
    }
    return Cursor(runId, initial, RunStatus.RUNNING, 0L)
  }

  fun load(runId: String): Cursor? =
    transaction(db) {
      OrderRun.selectAll().where { OrderRun.runId eq runId }.singleOrNull()?.let { row ->
        Cursor(
          runId = row[OrderRun.runId],
          state = orderJson.decodeFromString(OrderState.serializer(), row[OrderRun.currentState]),
          status = RunStatus.valueOf(row[OrderRun.status]),
          version = row[OrderRun.version],
        )
      }
    }

  /**
   * Compare-and-set advance. Returns the new [Cursor] iff exactly one row matched [expectedVersion];
   * null means another worker already advanced this run (idempotent skip / resume signal).
   */
  fun advance(runId: String, expectedVersion: Long, newState: OrderState, newStatus: RunStatus): Cursor? =
    transaction(db) {
      val updated =
        OrderRun.update({ (OrderRun.runId eq runId) and (OrderRun.version eq expectedVersion) }) {
          it[currentState] = orderJson.encodeToString(OrderState.serializer(), newState)
          it[status] = newStatus.name
          it[version] = expectedVersion + 1
        }
      if (updated == 1) Cursor(runId, newState, newStatus, expectedVersion + 1) else null
    }
}
```

> NOTE: Exposed 1.0.0-beta uses the `org.jetbrains.exposed.v1.*` package layout (core vs jdbc split). `and` is `org.jetbrains.exposed.v1.core.and` — add the import if the compiler asks: `import org.jetbrains.exposed.v1.core.and`.

- [ ] **Step 2: Compile**

Run: `./gradlew integrationTestClasses`
Expected: SUCCESS. If `and`/`eq` unresolved, add `import org.jetbrains.exposed.v1.core.*` and re-run.

- [ ] **Step 3: Commit**

```bash
git add src/integrationTest/kotlin/com/salesforce/hydra/integration/order/CursorStore.kt
git commit -m "feat(poc-kt): durable cursor store (Exposed, TEXT column, CAS advance)"
```

---

## Task 6: Kotlin — `Transport.kt`, `Effects.kt`, `Orchestrator.kt` (corrected protocol)

**Files:**
- Create: `src/integrationTest/kotlin/com/salesforce/hydra/integration/order/Transport.kt`
- Create: `src/integrationTest/kotlin/com/salesforce/hydra/integration/order/Effects.kt`
- Create: `src/integrationTest/kotlin/com/salesforce/hydra/integration/order/Orchestrator.kt`

- [ ] **Step 1: Write `Effects.kt`**

```kotlin
/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A recording sink for the real-world side effects an [OrderAction] commands. Each effect is keyed
 * by runId so a test can assert exactly-once semantics across crash/resume.
 */
class Effects {
  val performed: MutableList<Pair<String, OrderAction>> = CopyOnWriteArrayList()
  private val counts = ConcurrentHashMap<String, Int>()

  fun perform(runId: String, action: OrderAction) {
    performed.add(runId to action)
    counts.merge(action::class.simpleName + ":" + runId, 1, Int::plus)
  }

  fun count(runId: String, actionType: String): Int = counts.getOrDefault("$actionType:$runId", 0)

  fun actionsFor(runId: String): List<OrderAction> = performed.filter { it.first == runId }.map { it.second }
}
```

- [ ] **Step 2: Write `Transport.kt`**

```kotlin
/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.MessageProperties
import kotlinx.serialization.Serializable

/**
 * The unit of work on the queue. Carries the run id, the exit event of the just-finished step, and
 * the cursor version the producer observed. fromState is deliberately NOT here — the worker reads it
 * from the durable cursor, keeping the DB the single source of truth.
 */
@Serializable
data class StepMessage(val runId: String, val event: OrderEvent, val expectedVersion: Long)

const val ORDER_QUEUE = "order.steps"

class Transport(private val connection: Connection) {
  fun declareQueue() {
    connection.createChannel().use { ch -> ch.queueDeclare(ORDER_QUEUE, true, false, false, null) }
  }

  fun publish(message: StepMessage) {
    connection.createChannel().use { ch ->
      ch.basicPublish(
        "",
        ORDER_QUEUE,
        MessageProperties.PERSISTENT_TEXT_PLAIN,
        orderJson.encodeToString(StepMessage.serializer(), message).toByteArray(),
      )
    }
  }

  /**
   * Consume with manual ack. [handler] returns true to ack, false to nack-requeue (used by the
   * crash scenario to simulate an in-flight redelivery). Runs on the RabbitMQ client's own thread.
   */
  fun consume(handler: (StepMessage) -> Boolean): Channel {
    val channel = connection.createChannel()
    channel.basicQos(1)
    val onDeliver = DeliverCallback { _, delivery ->
      val msg = orderJson.decodeFromString(StepMessage.serializer(), String(delivery.body))
      val ack = runCatching { handler(msg) }.getOrDefault(false)
      if (ack) channel.basicAck(delivery.envelope.deliveryTag, false)
      else channel.basicNack(delivery.envelope.deliveryTag, false, true)
    }
    channel.basicConsume(ORDER_QUEUE, false, onDeliver, CancelCallback {})
    return channel
  }
}
```

- [ ] **Step 3: Write `Orchestrator.kt` (the heart — corrected protocol)**

```kotlin
/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import com.salesforce.hydra.Hydra
import com.salesforce.hydra.statemachine.Transition

/** Thrown by a test-injected hook to simulate a worker dying mid-step. */
class SimulatedCrash(message: String) : RuntimeException(message)

/**
 * Drives the DAG one step per message. Hydra is consulted as a PURE router; the durable cursor is
 * the only source of truth. A FRESH instance of this class (with a fresh Hydra) can resume any run
 * purely from the cursor — that is the resurrection proof.
 *
 * @param crashAfterAdvance optional test hook: invoked right after the CAS-advance, before enqueue,
 *   so a test can throw [SimulatedCrash] to drop the message un-acked and force redelivery.
 */
class Orchestrator(
  private val store: CursorStore,
  private val transport: Transport,
  private val effects: Effects,
  private val crashAfterAdvance: (runId: String, toState: OrderState) -> Unit = { _, _ -> },
) {
  private val hydra: Hydra<OrderState, OrderEvent, OrderAction> = orderMachine()

  /** @return true to ack the message, false to nack-requeue. */
  fun dispatch(message: StepMessage): Boolean {
    val cursor = store.load(message.runId) ?: return true // unknown run — drop

    // Resume path: the cursor already moved past what this message expected — a prior worker crashed
    // AFTER advancing. Re-derive the next step from the PERSISTED state without re-performing effects.
    if (cursor.version > message.expectedVersion) {
      enqueueNext(message.runId, cursor)
      return true
    }
    if (cursor.status != RunStatus.RUNNING) return true // terminal — nothing to do

    val transition = hydra.readTransitionAndNotifyListeners(cursor.state, message.event)
    if (transition !is Transition.Valid) {
      store.advance(message.runId, cursor.version, Cancelled("invalid: ${message.event}"), RunStatus.FAILED)
      return true
    }

    val toState = transition.toState
    val newStatus = statusFor(toState)
    val advanced = store.advance(message.runId, cursor.version, toState, newStatus)
      ?: run { // lost the race; another worker advanced — resume from current truth
        store.load(message.runId)?.let { enqueueNext(message.runId, it) }
        return true
      }

    // CAS won. Test hook may crash HERE — after the durable advance, before the side effect/enqueue.
    crashAfterAdvance(message.runId, toState)

    transition.action?.let { effects.perform(message.runId, it) }
    enqueueNext(message.runId, advanced)
    return true
  }

  /** Enqueue the next step's exit event derived from the persisted state, if non-terminal. */
  private fun enqueueNext(runId: String, cursor: Cursor) {
    nextEventFor(cursor.state)?.let { next ->
      transport.publish(StepMessage(runId, next, cursor.version))
    }
  }

  private fun statusFor(state: OrderState): RunStatus =
    when (state) {
      is Completed -> RunStatus.COMPLETED
      is Cancelled -> RunStatus.CANCELLED
      else -> RunStatus.RUNNING
    }

  /**
   * The "doer" finishes the persisted step and reports its exit event. In this PoC the doers always
   * succeed on the happy path; failure edges are driven explicitly by the seed event in tests.
   */
  private fun nextEventFor(state: OrderState): OrderEvent? =
    when (state) {
      is Reserving -> Reserved
      is Charging -> Charged
      is Shipping -> Shipped
      is Notifying -> Notified
      else -> null // Validating is driven by the seed; Completed/Cancelled are terminal
    }
}
```

> NOTE: `nextEventFor` models successful doers for steps reached *after* the seed. `Validating → Validated` and the failure events (`OutOfStock`, `PaymentDeclined`) are injected by the test's initial publish, which is how scenario 3 drives the cancel edge.

- [ ] **Step 4: Compile**

Run: `./gradlew spotlessApply integrationTestClasses`
Expected: SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/integrationTest/kotlin/com/salesforce/hydra/integration/order/Transport.kt src/integrationTest/kotlin/com/salesforce/hydra/integration/order/Effects.kt src/integrationTest/kotlin/com/salesforce/hydra/integration/order/Orchestrator.kt
git commit -m "feat(poc-kt): transport, effects sink, and the dispatch orchestrator (resume protocol)"
```

---

## Task 7: Shared test infra — `Infra.kt` (Testcontainers singletons)

**Files:**
- Create: `src/integrationTest/kotlin/com/salesforce/hydra/integration/order/Infra.kt`

- [ ] **Step 1: Write `Infra.kt`**

```kotlin
/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.RabbitMQContainer
import javax.sql.DataSource

/** Real Postgres + RabbitMQ via Testcontainers. Singletons reused across both Kotlin and Java tests. */
object Infra {
  val dockerAvailable: Boolean by lazy { runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false) }

  val postgres: PostgreSQLContainer<*> by lazy {
    PostgreSQLContainer("postgres:16-alpine").also { it.start() }
  }

  val rabbit: RabbitMQContainer by lazy {
    RabbitMQContainer("rabbitmq:3.13-management-alpine").also { it.start() }
  }

  fun dataSource(): DataSource =
    PGSimpleDataSource().apply {
      setURL(postgres.jdbcUrl)
      user = postgres.username
      password = postgres.password
    }

  fun rabbitConnection(): Connection =
    ConnectionFactory()
      .apply {
        host = rabbit.host
        port = rabbit.amqpPort
        username = rabbit.adminUsername
        password = rabbit.adminPassword
      }
      .newConnection()
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew integrationTestClasses`
Expected: SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/integrationTest/kotlin/com/salesforce/hydra/integration/order/Infra.kt
git commit -m "test(poc): Testcontainers Postgres + RabbitMQ infra singletons"
```

---

## Task 8: Kotlin — the 3-scenario integration test + `main()` demo

**Files:**
- Create: `src/integrationTest/kotlin/com/salesforce/hydra/integration/order/OrderOrchestratorKtTest.kt`
- Create: `src/integrationTest/kotlin/com/salesforce/hydra/integration/order/OrderOrchestratorDemo.kt`

- [ ] **Step 1: Write the test (3 scenarios)**

```kotlin
/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OrderOrchestratorKtTest {

  private lateinit var store: CursorStore
  private lateinit var transport: Transport
  private lateinit var effects: Effects
  private val connection by lazy { Infra.rabbitConnection() }

  @BeforeEach
  fun setUp() {
    assumeTrue(Infra.dockerAvailable, "Docker not available — skipping integration test")
    store = CursorStore(Infra.dataSource()).also { it.createSchema() }
    transport = Transport(connection).also { it.declareQueue() }
    effects = Effects()
  }

  private fun seedRun(initial: OrderState, firstEvent: OrderEvent): String {
    val runId = UUID.randomUUID().toString()
    store.seed(runId, initial)
    transport.publish(StepMessage(runId, firstEvent, expectedVersion = 0L))
    return runId
  }

  private fun awaitStatus(runId: String, expected: RunStatus, timeoutMs: Long = 10_000) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (store.load(runId)?.status == expected) return
      TimeUnit.MILLISECONDS.sleep(50)
    }
    error("Run $runId did not reach $expected; last = ${store.load(runId)}")
  }

  @Test
  fun scenario1_endToEndAcrossWorkers() {
    val orchestrator = Orchestrator(store, transport, effects)
    val channel = transport.consume(orchestrator::dispatch)

    val runId = seedRun(Validating(4999, "1 Market St", "SKU-1", 1), Validated)
    awaitStatus(runId, RunStatus.COMPLETED)
    channel.close()

    assertThat(effects.actionsFor(runId))
      .containsExactly(
        ReserveInventory("SKU-1", 1),
        ChargePayment(4999),
        ShipParcel("1 Market St"),
        SendShipNotice("1 Market St"),
      )
      .inOrder()
    assertThat(store.load(runId)!!.state).isEqualTo(Completed)
  }

  @Test
  fun scenario2_crashMidFlightResumesFromCursor() {
    // Worker A crashes right after advancing past Charging (into Shipping), before enqueue/effect.
    @Volatile var crashArmed = true
    val crashingOrchestrator =
      Orchestrator(store, transport, effects, crashAfterAdvance = { _, toState ->
        if (crashArmed && toState is Shipping) {
          crashArmed = false
          throw SimulatedCrash("die after advancing into Shipping")
        }
      })
    // Worker B is a FRESH orchestrator (fresh Hydra, zero in-memory state) — the resurrection.
    val freshOrchestrator = Orchestrator(store, transport, Effects().let { effects })

    val channelA = transport.consume(crashingOrchestrator::dispatch)
    val runId = seedRun(Validating(4999, "1 Market St", "SKU-1", 1), Validated)

    // Let A run until it crashes on the Shipping advance (message goes back to the queue un-acked).
    Thread.sleep(1_500)
    channelA.close()

    // B picks up the redelivered message and resumes from the persisted cursor.
    val channelB = transport.consume(freshOrchestrator::dispatch)
    awaitStatus(runId, RunStatus.COMPLETED)
    channelB.close()

    // ChargePayment fired exactly once despite the crash + redelivery.
    assertThat(effects.count(runId, "ChargePayment")).isEqualTo(1)
    assertThat(store.load(runId)!!.state).isEqualTo(Completed)
  }

  @Test
  fun scenario3_failureEdgeCancels() {
    val orchestrator = Orchestrator(store, transport, effects)
    val channel = transport.consume(orchestrator::dispatch)

    // Seed directly at Reserving and drive OutOfStock.
    val runId = UUID.randomUUID().toString()
    store.seed(runId, Reserving(4999, "1 Market St", "SKU-1", 1))
    transport.publish(StepMessage(runId, OutOfStock, 0L))

    awaitStatus(runId, RunStatus.CANCELLED)
    channel.close()

    assertThat(effects.actionsFor(runId)).containsExactly(NotifyCustomer("out of stock"))
    assertThat(effects.count(runId, "ChargePayment")).isEqualTo(0)
  }
}
```

> NOTE on scenario 2: the crash hook throws inside `dispatch`, which `Transport.consume` catches → returns false → `basicNack(requeue=true)`. The CAS-advance already committed, so when the fresh worker receives the redelivery, `cursor.version > expectedVersion` is true → it takes the resume branch → enqueues the next step from the persisted `Shipping` state → pipeline completes. The crashing worker never performed the `ShipParcel` effect (it threw before `effects.perform`), and the resume branch never re-performs prior effects → exactly-once.

- [ ] **Step 2: Run to verify scenarios fail/pass correctly (needs Docker)**

Run: `./gradlew integrationTest --tests "*OrderOrchestratorKtTest"`
Expected (Docker present): 3 PASS. Expected (no Docker): 3 SKIPPED (assumeTrue).

- [ ] **Step 3: Write `OrderOrchestratorDemo.kt` (main demo)**

```kotlin
/**
 * ************************************************************************************************
 * Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier: Apache License
 * Version 2.0 For full license text, see the LICENSE file in the repo root or
 * http://www.apache.org/licenses/LICENSE-2.0
 * ************************************************************************************************
 */
package com.salesforce.hydra.integration.order

import java.util.UUID

/**
 * Runnable demo of scenario 1 against the Testcontainers-managed broker + DB. Run with:
 * `./gradlew -PrunDemo` (see build wiring) or execute this main from the IDE — it boots its own
 * containers, drives one order to Completed, and prints each step.
 */
fun main() {
  require(Infra.dockerAvailable) { "Docker is required to run the demo" }
  val store = CursorStore(Infra.dataSource()).also { it.createSchema() }
  val transport = Transport(Infra.rabbitConnection()).also { it.declareQueue() }
  val effects = Effects()
  val orchestrator = Orchestrator(store, transport, effects)
  val channel = transport.consume(orchestrator::dispatch)

  val runId = UUID.randomUUID().toString()
  store.seed(runId, Validating(4999, "1 Market St", "SKU-1", 1))
  transport.publish(StepMessage(runId, Validated, 0L))
  println("[demo] seeded run $runId at Validating; driving pipeline…")

  val deadline = System.currentTimeMillis() + 10_000
  while (System.currentTimeMillis() < deadline && store.load(runId)?.status != RunStatus.COMPLETED) {
    Thread.sleep(100)
  }
  println("[demo] final cursor = ${store.load(runId)}")
  println("[demo] effects performed = ${effects.actionsFor(runId)}")
  channel.close()
}
```

- [ ] **Step 4: Run spotless + full integrationTest**

Run: `./gradlew spotlessApply integrationTest --tests "*OrderOrchestratorKtTest"`
Expected: PASS (or SKIPPED without Docker).

- [ ] **Step 5: Commit**

```bash
git add src/integrationTest/kotlin/com/salesforce/hydra/integration/order/OrderOrchestratorKtTest.kt src/integrationTest/kotlin/com/salesforce/hydra/integration/order/OrderOrchestratorDemo.kt
git commit -m "test(poc-kt): 3-scenario integration test (e2e, crash/resume, failure) + main demo"
```

---

## Task 9: Java PoC — mirror the Kotlin stack with jOOQ + Jackson

**Files (all under `src/integrationTest/java/com/salesforce/hydra/integration/order/java/`):**
- Create: `OrderDomain.java`, `OrderMachine.java`, `CursorStore.java`, `Transport.java`, `Effects.java`, `Orchestrator.java`, `OrderOrchestratorTest.java`, `OrderOrchestratorDemo.java`

> Reuses the Kotlin `Infra` object (same source set) via `com.salesforce.hydra.integration.order.Infra.INSTANCE`. The Java stack swaps Exposed→jOOQ (no codegen) and kotlinx-serialization→Jackson.

- [ ] **Step 1: `OrderDomain.java` — sealed records + Jackson polymorphism**

```java
/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/
package com.salesforce.hydra.integration.order.java;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

final class OrderDomain {
  private OrderDomain() {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Validating.class, name = "Validating"),
    @JsonSubTypes.Type(value = Reserving.class, name = "Reserving"),
    @JsonSubTypes.Type(value = Charging.class, name = "Charging"),
    @JsonSubTypes.Type(value = Shipping.class, name = "Shipping"),
    @JsonSubTypes.Type(value = Notifying.class, name = "Notifying"),
    @JsonSubTypes.Type(value = Completed.class, name = "Completed"),
    @JsonSubTypes.Type(value = Cancelled.class, name = "Cancelled"),
  })
  sealed interface OrderState {}

  record Validating(long amountCents, String address, String sku, int qty) implements OrderState {}

  record Reserving(long amountCents, String address, String sku, int qty) implements OrderState {}

  record Charging(long amountCents, String address, String sku, int qty) implements OrderState {}

  record Shipping(long amountCents, String address) implements OrderState {}

  record Notifying(String address) implements OrderState {}

  record Completed() implements OrderState {}

  record Cancelled(String reason) implements OrderState {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Validated.class, name = "Validated"),
    @JsonSubTypes.Type(value = Reserved.class, name = "Reserved"),
    @JsonSubTypes.Type(value = OutOfStock.class, name = "OutOfStock"),
    @JsonSubTypes.Type(value = Charged.class, name = "Charged"),
    @JsonSubTypes.Type(value = PaymentDeclined.class, name = "PaymentDeclined"),
    @JsonSubTypes.Type(value = Shipped.class, name = "Shipped"),
    @JsonSubTypes.Type(value = Notified.class, name = "Notified"),
  })
  sealed interface OrderEvent {}

  record Validated() implements OrderEvent {}

  record Reserved() implements OrderEvent {}

  record OutOfStock() implements OrderEvent {}

  record Charged() implements OrderEvent {}

  record PaymentDeclined(String reason) implements OrderEvent {}

  record Shipped() implements OrderEvent {}

  record Notified() implements OrderEvent {}

  sealed interface OrderAction {}

  record ReserveInventory(String sku, int qty) implements OrderAction {}

  record ChargePayment(long amountCents) implements OrderAction {}

  record ShipParcel(String address) implements OrderAction {}

  record SendShipNotice(String address) implements OrderAction {}

  record NotifyCustomer(String reason) implements OrderAction {}

  record ReleaseInventory(String sku, int qty) implements OrderAction {}
}
```

- [ ] **Step 2: `OrderMachine.java`**

```java
/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/
package com.salesforce.hydra.integration.order.java;

import com.salesforce.hydra.Hydra;
import com.salesforce.hydra.integration.order.java.OrderDomain.*;

final class OrderMachine {
  private OrderMachine() {}

  static Hydra<OrderState, OrderEvent, OrderAction> create() {
    return Hydra.create(
        mb -> {
          mb.initialState(new Validating(0, "", "", 0));

          mb.state(
              Validating.class,
              sb ->
                  sb.on(
                      Validated.class,
                      (s, e) ->
                          sb.transitionTo(
                              new Reserving(s.amountCents(), s.address(), s.sku(), s.qty()),
                              new ReserveInventory(s.sku(), s.qty()))));

          mb.state(
              Reserving.class,
              sb -> {
                sb.on(
                    Reserved.class,
                    (s, e) ->
                        sb.transitionTo(
                            new Charging(s.amountCents(), s.address(), s.sku(), s.qty()),
                            new ChargePayment(s.amountCents())));
                sb.on(
                    OutOfStock.class,
                    (s, e) ->
                        sb.transitionTo(
                            new Cancelled("out of stock"), new NotifyCustomer("out of stock")));
              });

          mb.state(
              Charging.class,
              sb -> {
                sb.on(
                    Charged.class,
                    (s, e) ->
                        sb.transitionTo(
                            new Shipping(s.amountCents(), s.address()), new ShipParcel(s.address())));
                sb.on(
                    PaymentDeclined.class,
                    (s, e) ->
                        sb.transitionTo(
                            new Cancelled(e.reason()), new ReleaseInventory(s.sku(), s.qty())));
              });

          mb.state(
              Shipping.class,
              sb ->
                  sb.on(
                      Shipped.class,
                      (s, e) ->
                          sb.transitionTo(
                              new Notifying(s.address()), new SendShipNotice(s.address()))));

          mb.state(
              Notifying.class,
              sb -> sb.on(Notified.class, (s, e) -> sb.transitionTo(new Completed())));

          mb.state(Completed.class, sb -> {});
          mb.state(Cancelled.class, sb -> {});
        });
  }
}
```

- [ ] **Step 3: `Effects.java`**

```java
/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/
package com.salesforce.hydra.integration.order.java;

import com.salesforce.hydra.integration.order.java.OrderDomain.OrderAction;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

final class Effects {
  record Entry(String runId, OrderAction action) {}

  final List<Entry> performed = new CopyOnWriteArrayList<>();
  private final Map<String, Integer> counts = new ConcurrentHashMap<>();

  void perform(String runId, OrderAction action) {
    performed.add(new Entry(runId, action));
    counts.merge(action.getClass().getSimpleName() + ":" + runId, 1, Integer::sum);
  }

  int count(String runId, String actionType) {
    return counts.getOrDefault(actionType + ":" + runId, 0);
  }

  List<OrderAction> actionsFor(String runId) {
    return performed.stream().filter(e -> e.runId().equals(runId)).map(Entry::action).toList();
  }
}
```

- [ ] **Step 4: `CursorStore.java` (jOOQ, no codegen, TEXT column, CAS)**

```java
/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/
package com.salesforce.hydra.integration.order.java;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderState;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

final class CursorStore {
  enum RunStatus {
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
  }

  record Cursor(String runId, OrderState state, RunStatus status, long version) {}

  static final JsonMapper JSON = JsonMapper.builder().build();

  private static final org.jooq.Table<Record> ORDER_RUN = table(name("order_run"));
  private static final org.jooq.Field<String> RUN_ID = field(name("run_id"), SQLDataType.VARCHAR);
  private static final org.jooq.Field<String> CURRENT_STATE =
      field(name("current_state"), SQLDataType.CLOB);
  private static final org.jooq.Field<String> STATUS = field(name("status"), SQLDataType.VARCHAR);
  private static final org.jooq.Field<Long> VERSION = field(name("version"), SQLDataType.BIGINT);

  private final DataSource dataSource;

  CursorStore(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  private DSLContext ctx(java.sql.Connection c) {
    return DSL.using(c, SQLDialect.POSTGRES);
  }

  void createSchema() {
    run(
        ctx ->
            ctx.createTableIfNotExists(ORDER_RUN)
                .column(RUN_ID)
                .column(CURRENT_STATE)
                .column(STATUS)
                .column(VERSION)
                .constraint(DSL.primaryKey(RUN_ID))
                .execute());
  }

  Cursor seed(String runId, OrderState initial) {
    run(
        ctx ->
            ctx.insertInto(ORDER_RUN)
                .columns(RUN_ID, CURRENT_STATE, STATUS, VERSION)
                .values(runId, toJson(initial), RunStatus.RUNNING.name(), 0L)
                .execute());
    return new Cursor(runId, initial, RunStatus.RUNNING, 0L);
  }

  Cursor load(String runId) {
    return run(
        ctx -> {
          Record r =
              ctx.select(RUN_ID, CURRENT_STATE, STATUS, VERSION)
                  .from(ORDER_RUN)
                  .where(RUN_ID.eq(runId))
                  .fetchOne();
          if (r == null) return null;
          return new Cursor(
              r.get(RUN_ID),
              fromJson(r.get(CURRENT_STATE)),
              RunStatus.valueOf(r.get(STATUS)),
              r.get(VERSION));
        });
  }

  Cursor advance(String runId, long expectedVersion, OrderState newState, RunStatus newStatus) {
    int updated =
        run(
            ctx ->
                ctx.update(ORDER_RUN)
                    .set(CURRENT_STATE, toJson(newState))
                    .set(STATUS, newStatus.name())
                    .set(VERSION, expectedVersion + 1)
                    .where(RUN_ID.eq(runId).and(VERSION.eq(expectedVersion)))
                    .execute());
    return updated == 1 ? new Cursor(runId, newState, newStatus, expectedVersion + 1) : null;
  }

  // ── helpers ──
  private interface Work<T> {
    T apply(DSLContext ctx);
  }

  private <T> T run(Work<T> work) {
    try (var c = dataSource.getConnection()) {
      return work.apply(ctx(c));
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void run(java.util.function.Consumer<DSLContext> work) {
    run(
        ctx -> {
          work.accept(ctx);
          return null;
        });
  }

  private static String toJson(OrderState s) {
    try {
      return JSON.writeValueAsString(s);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static OrderState fromJson(String json) {
    try {
      return JSON.readValue(json, OrderState.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
```

> NOTE: `createTableIfNotExists(...).column(field)` infers types from the `SQLDataType` baked into each `Field`. `CLOB` maps to Postgres `TEXT`. If jOOQ's free-build edition warns on `createTable` DSL, fall back to `ctx.execute("CREATE TABLE IF NOT EXISTS order_run (run_id varchar primary key, current_state text, status varchar, version bigint)")`.

- [ ] **Step 5: `Transport.java`**

```java
/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/
package com.salesforce.hydra.integration.order.java;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.MessageProperties;
import com.salesforce.hydra.integration.order.java.OrderDomain.OrderEvent;
import java.io.IOException;
import java.util.function.Predicate;

final class Transport {
  record StepMessage(String runId, OrderEvent event, long expectedVersion) {}

  static final String ORDER_QUEUE = "order.steps.java";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Connection connection;

  Transport(Connection connection) {
    this.connection = connection;
  }

  void declareQueue() {
    try (Channel ch = connection.createChannel()) {
      ch.queueDeclare(ORDER_QUEUE, true, false, false, null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  void publish(StepMessage message) {
    try (Channel ch = connection.createChannel()) {
      ch.basicPublish(
          "",
          ORDER_QUEUE,
          MessageProperties.PERSISTENT_TEXT_PLAIN,
          JSON.writeValueAsBytes(message));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** @param handler returns true to ack, false to nack-requeue. */
  Channel consume(Predicate<StepMessage> handler) {
    try {
      Channel channel = connection.createChannel();
      channel.basicQos(1);
      channel.basicConsume(
          ORDER_QUEUE,
          false,
          (tag, delivery) -> {
            StepMessage msg = JSON.readValue(delivery.getBody(), StepMessage.class);
            boolean ack;
            try {
              ack = handler.test(msg);
            } catch (RuntimeException e) {
              ack = false;
            }
            if (ack) channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            else channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
          },
          tag -> {});
      return channel;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
```

- [ ] **Step 6: `Orchestrator.java` (corrected protocol, mirrors Kotlin)**

```java
/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/
package com.salesforce.hydra.integration.order.java;

import com.salesforce.hydra.Hydra;
import com.salesforce.hydra.integration.order.java.CursorStore.Cursor;
import com.salesforce.hydra.integration.order.java.CursorStore.RunStatus;
import com.salesforce.hydra.integration.order.java.OrderDomain.*;
import com.salesforce.hydra.integration.order.java.Transport.StepMessage;
import com.salesforce.hydra.statemachine.Transition;
import java.util.function.BiConsumer;

final class Orchestrator {
  static final class SimulatedCrash extends RuntimeException {
    SimulatedCrash(String m) {
      super(m);
    }
  }

  private final CursorStore store;
  private final Transport transport;
  private final Effects effects;
  private final BiConsumer<String, OrderState> crashAfterAdvance;
  private final Hydra<OrderState, OrderEvent, OrderAction> hydra = OrderMachine.create();

  Orchestrator(CursorStore store, Transport transport, Effects effects) {
    this(store, transport, effects, (runId, toState) -> {});
  }

  Orchestrator(
      CursorStore store,
      Transport transport,
      Effects effects,
      BiConsumer<String, OrderState> crashAfterAdvance) {
    this.store = store;
    this.transport = transport;
    this.effects = effects;
    this.crashAfterAdvance = crashAfterAdvance;
  }

  boolean dispatch(StepMessage message) {
    Cursor cursor = store.load(message.runId());
    if (cursor == null) return true;

    if (cursor.version() > message.expectedVersion()) {
      enqueueNext(message.runId(), cursor);
      return true;
    }
    if (cursor.status() != RunStatus.RUNNING) return true;

    Transition<OrderState, OrderEvent, OrderAction> transition =
        hydra.readTransitionAndNotifyListeners(cursor.state(), message.event());
    if (!(transition instanceof Transition.Valid<OrderState, OrderEvent, OrderAction> valid)) {
      store.advance(
          message.runId(),
          cursor.version(),
          new Cancelled("invalid: " + message.event()),
          RunStatus.FAILED);
      return true;
    }

    OrderState toState = valid.getToState();
    Cursor advanced =
        store.advance(message.runId(), cursor.version(), toState, statusFor(toState));
    if (advanced == null) { // lost the race — resume from current truth
      Cursor current = store.load(message.runId());
      if (current != null) enqueueNext(message.runId(), current);
      return true;
    }

    crashAfterAdvance.accept(message.runId(), toState); // test hook may throw here

    if (valid.getAction() != null) effects.perform(message.runId(), valid.getAction());
    enqueueNext(message.runId(), advanced);
    return true;
  }

  private void enqueueNext(String runId, Cursor cursor) {
    OrderEvent next = nextEventFor(cursor.state());
    if (next != null) transport.publish(new StepMessage(runId, next, cursor.version()));
  }

  private static RunStatus statusFor(OrderState state) {
    return switch (state) {
      case Completed c -> RunStatus.COMPLETED;
      case Cancelled c -> RunStatus.CANCELLED;
      default -> RunStatus.RUNNING;
    };
  }

  private static OrderEvent nextEventFor(OrderState state) {
    return switch (state) {
      case Reserving r -> new Reserved();
      case Charging c -> new Charged();
      case Shipping s -> new Shipped();
      case Notifying n -> new Notified();
      default -> null;
    };
  }
}
```

- [ ] **Step 7: `OrderOrchestratorTest.java` (3 scenarios) + `OrderOrchestratorDemo.java`**

```java
/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/
package com.salesforce.hydra.integration.order.java;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.rabbitmq.client.Connection;
import com.salesforce.hydra.integration.order.Infra;
import com.salesforce.hydra.integration.order.java.CursorStore.RunStatus;
import com.salesforce.hydra.integration.order.java.OrderDomain.*;
import com.salesforce.hydra.integration.order.java.Transport.StepMessage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderOrchestratorTest {

  private CursorStore store;
  private Transport transport;
  private Effects effects;
  private Connection connection;

  @BeforeEach
  void setUp() {
    assumeTrue(Infra.INSTANCE.getDockerAvailable(), "Docker not available — skipping");
    store = new CursorStore(Infra.INSTANCE.dataSource());
    store.createSchema();
    connection = Infra.INSTANCE.rabbitConnection();
    transport = new Transport(connection);
    transport.declareQueue();
    effects = new Effects();
  }

  private String seedRun(OrderState initial, OrderEvent first) {
    String runId = UUID.randomUUID().toString();
    store.seed(runId, initial);
    transport.publish(new StepMessage(runId, first, 0L));
    return runId;
  }

  private void awaitStatus(String runId, RunStatus expected) {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      var c = store.load(runId);
      if (c != null && c.status() == expected) return;
      try {
        Thread.sleep(50);
      } catch (InterruptedException ignored) {
      }
    }
    throw new AssertionError("Run " + runId + " did not reach " + expected);
  }

  @Test
  void scenario1_endToEndAcrossWorkers() throws Exception {
    var orchestrator = new Orchestrator(store, transport, effects);
    var channel = transport.consume(orchestrator::dispatch);

    String runId = seedRun(new Validating(4999, "1 Market St", "SKU-1", 1), new Validated());
    awaitStatus(runId, RunStatus.COMPLETED);
    channel.close();

    assertThat(effects.actionsFor(runId))
        .containsExactly(
            new ReserveInventory("SKU-1", 1),
            new ChargePayment(4999),
            new ShipParcel("1 Market St"),
            new SendShipNotice("1 Market St"))
        .inOrder();
    assertThat(store.load(runId).state()).isEqualTo(new Completed());
  }

  @Test
  void scenario2_crashMidFlightResumesFromCursor() throws Exception {
    AtomicBoolean crashArmed = new AtomicBoolean(true);
    var crashing =
        new Orchestrator(
            store,
            transport,
            effects,
            (runId, toState) -> {
              if (crashArmed.get() && toState instanceof Shipping) {
                crashArmed.set(false);
                throw new Orchestrator.SimulatedCrash("die after advancing into Shipping");
              }
            });
    var fresh = new Orchestrator(store, transport, effects);

    var channelA = transport.consume(crashing::dispatch);
    String runId = seedRun(new Validating(4999, "1 Market St", "SKU-1", 1), new Validated());
    Thread.sleep(1_500);
    channelA.close();

    var channelB = transport.consume(fresh::dispatch);
    awaitStatus(runId, RunStatus.COMPLETED);
    channelB.close();

    assertThat(effects.count(runId, "ChargePayment")).isEqualTo(1);
    assertThat(store.load(runId).state()).isEqualTo(new Completed());
  }

  @Test
  void scenario3_failureEdgeCancels() throws Exception {
    var orchestrator = new Orchestrator(store, transport, effects);
    var channel = transport.consume(orchestrator::dispatch);

    String runId = UUID.randomUUID().toString();
    store.seed(runId, new Reserving(4999, "1 Market St", "SKU-1", 1));
    transport.publish(new StepMessage(runId, new OutOfStock(), 0L));

    awaitStatus(runId, RunStatus.CANCELLED);
    channel.close();

    assertThat(effects.actionsFor(runId)).isEqualTo(List.of(new NotifyCustomer("out of stock")));
    assertThat(effects.count(runId, "ChargePayment")).isEqualTo(0);
  }
}
```

```java
/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/
package com.salesforce.hydra.integration.order.java;

import com.salesforce.hydra.integration.order.Infra;
import com.salesforce.hydra.integration.order.java.CursorStore.RunStatus;
import com.salesforce.hydra.integration.order.java.OrderDomain.Validated;
import com.salesforce.hydra.integration.order.java.OrderDomain.Validating;
import com.salesforce.hydra.integration.order.java.Transport.StepMessage;
import java.util.UUID;

final class OrderOrchestratorDemo {
  private OrderOrchestratorDemo() {}

  public static void main(String[] args) throws Exception {
    if (!Infra.INSTANCE.getDockerAvailable()) throw new IllegalStateException("Docker required");
    var store = new CursorStore(Infra.INSTANCE.dataSource());
    store.createSchema();
    var transport = new Transport(Infra.INSTANCE.rabbitConnection());
    transport.declareQueue();
    var effects = new Effects();
    var orchestrator = new Orchestrator(store, transport, effects);
    var channel = transport.consume(orchestrator::dispatch);

    String runId = UUID.randomUUID().toString();
    store.seed(runId, new Validating(4999, "1 Market St", "SKU-1", 1));
    transport.publish(new StepMessage(runId, new Validated(), 0L));
    System.out.println("[demo-java] seeded run " + runId + "; driving pipeline…");

    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline
        && store.load(runId).status() != RunStatus.COMPLETED) {
      Thread.sleep(100);
    }
    System.out.println("[demo-java] final cursor = " + store.load(runId));
    System.out.println("[demo-java] effects = " + effects.actionsFor(runId));
    channel.close();
  }
}
```

- [ ] **Step 8: Run spotless + the Java suite**

Run: `./gradlew spotlessApply integrationTest --tests "*OrderOrchestratorTest"`
Expected: 3 PASS with Docker; 3 SKIPPED without.

> NOTE: `Infra.INSTANCE.getDockerAvailable()` — Kotlin's `val dockerAvailable` exposes a JVM getter; `dataSource()`/`rabbitConnection()` are plain methods. If the Kotlin `object` accessor differs, use `Infra.INSTANCE` then the IDE-suggested accessor.

- [ ] **Step 9: Commit**

```bash
git add src/integrationTest/java/com/salesforce/hydra/integration/order/java/
git commit -m "test(poc-java): jOOQ + Jackson orchestrator, 3-scenario integration test + demo"
```

---

## Task 10: Full integration run + expand `async-orchestration.adoc` + SVG diagram

**Files:**
- Modify: `docs/modules/ROOT/pages/async-orchestration.adoc`
- Create: `docs/modules/ROOT/images/async-resume.svg`

- [ ] **Step 1: Run the entire integration suite (both languages)**

Run: `./gradlew clean integrationTest`
Expected: all 6 scenario tests + 2 machine smoke tests PASS (Docker present). Capture output for the verification step.

- [ ] **Step 2: Author `async-resume.svg`**

Hand-write a sequence diagram (theme-consistent with the existing `order-machine.svg` — open it first to match fonts/colors). Lanes: `Worker A` · `Cursor (Postgres)` · `Queue (RabbitMQ)` · `Worker B (fresh Hydra)`. Flow: A reads cursor → Hydra routes → CAS-advance (v→v+1) → **✗ crash before enqueue** → message redelivered → B reads advanced cursor → resume branch → enqueue next → … → Completed. QA with: `rsvg-convert async-resume.svg -o /tmp/async-resume.png && open /tmp/async-resume.png` (per the diagram-as-SVG memory — hand-written SVG + rsvg-convert QA, no Lucid PNG).

- [ ] **Step 3: Expand the doc**

After the existing `== The pattern` section in `async-orchestration.adoc`, add a worked-walkthrough section. Insert:

```asciidoc
== A worked example: an order-fulfillment pipeline

The pattern above is proven end-to-end by a runnable PoC in this repo's
`integrationTest` source set — the *same* DAG driven by two idiomatic
orchestrators (Kotlin + kotlin-exposed, Java + jOOQ) over a real RabbitMQ
broker and a real Postgres cursor, both spun up with Testcontainers.

=== The DAG

[source]
----
Validating ── Validated ───────────▶ Reserving   (action: ReserveInventory)
Reserving  ── Reserved ─────────────▶ Charging    (action: ChargePayment)
Reserving  ── OutOfStock ───────────▶ Cancelled   (action: NotifyCustomer)
Charging   ── Charged ──────────────▶ Shipping    (action: ShipParcel)
Charging   ── PaymentDeclined ──────▶ Cancelled   (action: ReleaseInventory)
Shipping   ── Shipped ──────────────▶ Notifying   (action: SendShipNotice)
Notifying  ── Notified ─────────────▶ Completed   (terminal)
----

=== The durable cursor

The position lives in one Postgres row, guarded by a `version` for optimistic
concurrency:

[source,sql]
----
CREATE TABLE order_run (
  run_id        VARCHAR PRIMARY KEY,
  current_state TEXT    NOT NULL,   -- the serialized OrderState
  status        VARCHAR NOT NULL,   -- RUNNING | COMPLETED | CANCELLED | FAILED
  version       BIGINT  NOT NULL
);
----

Advancing is a compare-and-set: `UPDATE ... SET version = version + 1 WHERE
run_id = ? AND version = ?`. A zero-row result means another worker already
moved this run — the signal a crashed step left behind.

=== The dispatch loop

Each message carries only `{runId, event, expectedVersion}` — *not* the state.
The worker reads the state from the cursor, so the DB stays the single source
of truth:

[source,kotlin,indent=0]
----
val cursor = store.load(message.runId) ?: return true
if (cursor.version > message.expectedVersion) {     // a prior worker already advanced…
  enqueueNext(message.runId, cursor)                // …resume from the persisted state
  return true
}
val transition = hydra.readTransitionAndNotifyListeners(cursor.state, message.event)
val advanced = store.advance(message.runId, cursor.version, transition.toState, statusFor(...))
  ?: return resumeFromTruth(message.runId)          // lost the CAS race — resume, don't redo
transition.action?.let { effects.perform(message.runId, it) }
enqueueNext(message.runId, advanced)
----

=== Crash mid-flight, resume from the cursor

image::async-resume.svg[Crash and resume sequence,align=center]

A worker can die after it CAS-advances the cursor but before it enqueues the
next step. RabbitMQ redelivers the un-acked message; a *fresh* worker — a brand
new `Hydra` with zero in-memory position — reads the already-advanced cursor,
sees `version > expectedVersion`, and resumes by enqueuing the next step
*without* re-performing the side effect. The result: the run reaches
`Completed`, and every side effect fired *exactly once*. This is the literal
proof of the line above — _a run that dies mid-flight resumes from exactly
where it stopped_.

=== Run the proof

[source,bash]
----
./gradlew integrationTest --tests "*OrderOrchestrator*"
----

Requires Docker (the tests skip gracefully when it is absent). The crash/resume
scenario (`scenario2_crashMidFlightResumesFromCursor`) is the headline test;
`scenario1` proves the happy path across workers, and `scenario3` proves a
failure edge routes to `Cancelled` with no charge.
```

- [ ] **Step 4: Build the docs / lint adoc**

Run: `./gradlew spotlessApply` (formats the `.adoc` per the `documentation` Spotless target).
Manually confirm the SVG renders in the Antora preview if available; otherwise the `rsvg-convert` QA from Step 2 suffices.

- [ ] **Step 5: Commit**

```bash
git add docs/modules/ROOT/pages/async-orchestration.adoc docs/modules/ROOT/images/async-resume.svg
git commit -m "docs: worked async-orchestration walkthrough + crash/resume SVG, referencing the PoC"
```

---

## Final verification

- [ ] **Step 1: Full clean build**

Run: `./gradlew clean build integrationTest`
Expected: BUILD SUCCESSFUL. Unit tests green (incl. the new overload test); integration tests green with Docker, skipped without.

- [ ] **Step 2: Confirm spotless + detekt clean**

Run: `./gradlew spotlessCheck detekt`
Expected: no violations.

- [ ] **Step 3: REQUIRED — use the verification-before-completion skill before claiming done.**

---

## Self-review notes (author)

- **Spec coverage:** DAG (Task 3/4), CursorStore CAS (Task 5/9-step4), Transport (Task 6/9-step5), Orchestrator (Task 6/9-step6), Worker-on-broker-thread (Transport.consume), 3 scenarios ×2 langs (Task 8/9), main() demos ×2 (Task 8/9), build wiring (Task 2), docs + SVG (Task 10), library enhancement (Task 1). All covered.
- **Type consistency:** `StepMessage(runId, event, expectedVersion)`, `Cursor(runId, state, status, version)`, `advance(runId, expectedVersion, newState, newStatus)`, `Effects.count(runId, actionType)`, `Effects.actionsFor(runId)` — used identically across Kotlin and Java tasks.
- **Risks:** (a) Exposed 1.0.0-beta `v1` package imports may need a one-line adjustment — noted in Task 5. (b) jOOQ free-edition `createTable` DSL — raw-SQL fallback noted in Task 9-step4. (c) Kotlin `object Infra` accessor names from Java — noted in Task 9-step8. None block the plan; each has an inline fallback.
