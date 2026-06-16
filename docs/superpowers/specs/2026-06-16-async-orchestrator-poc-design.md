# Async Orchestrator PoC + Expanded Docs — Design

**Date:** 2026-06-16
**Status:** Approved (pending written-spec review)

## Goal

Make the central claim of `docs/modules/ROOT/pages/async-orchestration.adoc` real and provable:

> "Because the position is an explicit, persisted value, a run that dies mid-flight resumes from exactly where it stopped."

Deliver a proof-of-concept showing how an **asynchronous orchestrator** uses Hydra as a pure, side-effect-free routing function while the **durable cursor** for a DAG lives in a database — persisted by one worker, resurrected by a *different* worker (a fresh `Hydra` instance with zero in-memory position) that dequeues from a real broker. Then expand the doc with a concrete worked walkthrough.

## What Hydra provides (the hook)

`Hydra.readTransitionAndNotifyListeners(fromStateClass, fromState, event)` answers *"given I just finished step X with exit event E, what is the next step?"* and fires listeners **without** mutating Hydra's in-memory `AtomicReference`. The orchestrator persists the real position; Hydra is consulted as a pure router. This PoC is built entirely around that method.

## Scope

- **Two idiomatic implementations of the same DAG**, each a complete runnable PoC:
  - **Kotlin** orchestrator — durable cursor via **kotlin-exposed**.
  - **Java** orchestrator — durable cursor via **jOOQ** (programmatic DSL, **no build-time codegen**).
- **Transport:** RabbitMQ work queue (real broker).
- **Infra:** Testcontainers — real **Postgres** + real **RabbitMQ**, spun up per test run. Tests `assumeTrue` Docker is available and skip gracefully otherwise.
- **Each language ships:** an integration-test suite (3 scenarios) **and** a small `main()` demo runnable against local Docker.
- **Docs:** expand `async-orchestration.adoc` in place with a worked walkthrough, a hand-written SVG sequence diagram, a crash/resume narrative, and a "run the proof" reference.

Non-goals: a generic orchestration framework, retry/backoff policy tuning, dead-letter handling beyond what the crash/resume scenario needs, distributed tracing.

## Section 1 — The fulfillment DAG (the Hydra machine)

One Hydra machine = the map of legal moves. State carries context threaded forward (amountCents, address, sku, qty). Events are the exit signal of each step. Actions are the side-effect commands the *next* worker performs.

```
Validating ── Validated ───────────▶ Reserving   (action: ReserveInventory)
Reserving  ── Reserved ─────────────▶ Charging    (action: ChargePayment)
Reserving  ── OutOfStock ───────────▶ Cancelled   (action: NotifyCustomer)
Charging   ── Charged ──────────────▶ Shipping    (action: ShipParcel)
Charging   ── PaymentDeclined ──────▶ Cancelled   (action: ReleaseInventory)
Shipping   ── Shipped ──────────────▶ Notifying   (action: SendShipNotice)
Notifying  ── Notified ─────────────▶ Completed   (terminal, no action)
```

States: `Validating`, `Reserving`, `Charging`, `Shipping`, `Notifying`, `Completed`, `Cancelled`.
Events: `Validated`, `Reserved`, `OutOfStock`, `Charged`, `PaymentDeclined`, `Shipped`, `Notified`.
Actions: `ReserveInventory`, `ChargePayment`, `ShipParcel`, `SendShipNotice`, `NotifyCustomer`, `ReleaseInventory`.

Defined idiomatically per language:
- Kotlin — `sealed interface` + `data class` for State/Event/Action.
- Java — `sealed interface` + `record` (mirrors existing `OrderDomain.java`).

Both serialize to/from JSON via Moshi (sealed-class support already enabled in `build.gradle.kts`: `moshi { enableSealed = true }`).

## Section 2 — Components (per language, mirrored)

### 2.1 `CursorStore` — the source of truth
Postgres table:

```sql
CREATE TABLE order_run (
  run_id        TEXT PRIMARY KEY,
  current_state JSONB        NOT NULL,   -- serialized OrderState
  status        TEXT         NOT NULL,   -- RUNNING | COMPLETED | CANCELLED | FAILED
  version       BIGINT       NOT NULL,   -- optimistic-concurrency guard
  updated_at    TIMESTAMPTZ  NOT NULL
);
```

- Kotlin: **Exposed** `Table` object + DSL (`insert`, `update`, `select`).
- Java: **jOOQ** `DSL.using(connection)` with `DSL.table`/`DSL.field` references — no generated classes.
- `advance(runId, expectedVersion, newState, newStatus)` does a **compare-and-set** on `version` (`UPDATE ... WHERE run_id = ? AND version = ?`). A zero-row update means another worker already advanced this run — the caller treats it as an idempotent skip. This CAS is what makes "the DB row, not the JVM, is the source of truth" enforceable under concurrent/duplicate delivery.

### 2.2 `Transport` — RabbitMQ work queue
- One durable queue. Message body (Moshi JSON): `{ runId, fromStateJson, exitEventJson }`.
- Producer publishes the next step; consumer (worker) acks **after** the cursor is advanced and the next message is enqueued, so a crash before ack causes redelivery (at-least-once) — handled idempotently by the CAS in 2.1.

### 2.3 `Orchestrator.dispatch(message)` — the loop body
1. Construct/reuse a **stateless** `Hydra` (its `stateRef` is irrelevant and never read).
2. Deserialize `fromState` + `exitEvent`.
3. `var t = hydra.readTransitionAndNotifyListeners(fromState.getClass(), fromState, exitEvent)`.
4. On `Transition.Valid`:
   - `advance(runId, version, t.toState, statusFor(t.toState))` (CAS). If zero rows → already advanced → ack and return (idempotent).
   - Perform the emitted `Action`'s side effect via an `Effects` sink (exhaustive switch / `when`).
   - If `t.toState` is non-terminal, derive the next exit event and **enqueue** the next message.
   - Ack.
5. On `Invalid` / `NoFromState` → mark run `FAILED`, ack (no redelivery loop).

### 2.4 `Worker` — the resurrection
- A RabbitMQ consumer running on a **separate thread pool**, holding a **freshly constructed Hydra**. It never reads an in-memory position; it reads `fromState` from the message (which itself was sourced from the durable cursor). This is the literal "different thread/host, fresh JVM state" handoff.
- Kotlin worker uses coroutines (`Dispatchers.IO`) for the consume loop; Java worker uses an `ExecutorService`.

**Invariant under test:** the only authority on "where is this run" is the `order_run` row. Hydra is a pure function from `(fromState, event)` to `Transition`.

## Section 3 — Integration test scenarios (3, run by BOTH languages)

1. **End-to-end across workers.** Seed `order_run` at `Validating`; publish the first message. The pipeline hops worker→worker (each a fresh Hydra) over the real broker until the cursor reads `Completed`. Assert the ordered side effects: `ReserveInventory, ChargePayment, ShipParcel, SendShipNotice`. Assert final cursor `status = COMPLETED`.

2. **Crash mid-flight, resume from cursor (headline proof).** Configure a worker to die *after* it CAS-advances the cursor past `Charging` but *before* it acks/enqueues the next step. RabbitMQ redelivers the in-flight message; a brand-new worker picks it up, the CAS sees the run already advanced and idempotently skips the duplicate, and the run continues to `Completed`. Assert `ChargePayment` fired **exactly once** and the cursor reaches `Completed`. This is the direct proof of "resumes from exactly where it stopped."

3. **Failure edge.** Drive `Reserving` with `OutOfStock`; assert routing to `Cancelled` with `NotifyCustomer` side effect, cursor `status = CANCELLED`, and no `ChargePayment`.

`main()` demo runs scenario 1 against local Docker with readable log output (per-step: cursor advance, action performed, next enqueue).

## Section 4 — Build & dependencies

Add to `gradle/libs.versions.toml` and wire into the existing `integrationTest` `JvmTestSuite` (already declared in `build.gradle.kts`):

- Testcontainers BOM + `testcontainers`, `junit-jupiter`, `postgresql`, `rabbitmq` modules.
- Postgres JDBC driver.
- kotlin-exposed: `exposed-core`, `exposed-jdbc` (and `exposed-json` for the JSONB column).
- jOOQ (`org.jooq:jooq`, latest).
- RabbitMQ `amqp-client`.
- kotlinx-coroutines-core (Kotlin worker).
- Reuse existing Moshi (`moshix.adapters`) for message/state JSON.

New source dirs:
- `src/integrationTest/kotlin/com/salesforce/hydra/integration/order/...`
- `src/integrationTest/java/com/salesforce/hydra/integration/order/...`

The `integrationTest` suite compiles both Kotlin and Java with **no extra wiring**: `hydra.kt-conventions` applies `kotlin("jvm")`, and the Kotlin plugin auto-attaches a `kotlin` source dir to every source set (including the `JvmTestSuite`-created `integrationTest`). The sibling `revoman-root` repo proves this exact setup — same convention plugins, a dual-language `src/integrationTest/{java,kotlin}`, and **no** explicit source-dir config in its `build.gradle.kts`. Tests guard on Docker with `org.junit.jupiter.api.Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable())` so the build degrades gracefully without Docker.

Run: `./gradlew integrationTest` (or `--tests "...OrderOrchestrator*Test"`).

## Section 5 — Docs (`async-orchestration.adoc`)

Expand the existing page in place (keep current concept intro + "The pattern" list):

- **Worked walkthrough** — a concrete fulfillment-pipeline section: the DAG definition, the `order_run` schema, the `dispatch` loop, and worker resurrection. Kotlin shown inline with short Java callouts where the stack differs (Exposed vs jOOQ).
- **Sequence diagram** — a hand-written **SVG** (`docs/modules/ROOT/images/async-resume.svg`), authored + QA'd with `rsvg-convert` per the diagram-as-SVG memory. Shows: Worker A → advance cursor (DB) → enqueue (broker) → [crash] → redeliver → Worker B (fresh Hydra) → read cursor → continue. Theme-consistent with the existing `order-machine.svg`.
- **Crash/resume narrative** — tied to scenario 2, explaining at-least-once delivery + CAS idempotency.
- **"Run the proof"** — reference block pointing at the integration tests and `./gradlew integrationTest`.

## Risks / Notes

- **jOOQ no-codegen** keeps the build simple but means hand-written `field("...")` refs; acceptable for a PoC and closest in spirit to Exposed.
- **Testcontainers + Docker** required for the tests to actually run; CI without Docker will skip (not fail).
- **integrationTest dual-language wiring** — confirmed zero-config (Kotlin plugin auto-attaches the `kotlin` source dir; `revoman-root` is the proof). No risk.
- Keep Hydra itself untouched — the PoC lives entirely in the `integrationTest` source set + docs. No changes to `src/main`.
