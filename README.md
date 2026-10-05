# Temporal Saga Cancellation Demo

A small Spring Boot + Temporal (Java SDK) project showing what goes wrong when a saga workflow
gets **cancelled while a step is still in flight**, and how to write the saga so that
cancellation never leaves an orphaned hold behind.

The scenario: an operation holds money in two downstream systems, `credit` (1 000) and `debit`
(5 000). If anything fails or the workflow is cancelled, the saga must release (unhold)
whatever it has already held.

## How it works

```
HTTP client ──► Spring controller ──► Temporal workflow ──► HoldingActivity
                                                               │
                         ┌─────────────────────────────────────┘
                         ▼
          Toxiproxy :8666 (hold calls, +8s latency)  ──┐
          Toxiproxy :8667 (unhold calls, +2s latency) ─┴──► /product/{credit|debit}/{hold|unhold}
                                                              (ProductLedger, same app on :8080)
```

- **`ProductLedger`** is a fake downstream system that lives inside the same app. It logs
  every request **in the order it actually arrives**, e.g.

  ```
  skyro <- HOLD credit holding-123 amount=1000 => APPLIED
  skyro <- UNHOLD credit holding-123 => RELEASED            # good
  skyro <- UNHOLD credit holding-123 => NOTHING_TO_RELEASE  # unhold arrived before the hold: leak
  ```

- **Toxiproxy** sits between the activities and the ledger. Hold calls are slow (8s) and unhold
  calls are fast (2s), so a compensation can overtake the hold it is supposed to reverse.
  `ToxiproxyAdmin` applies those latencies on startup.

## Prerequisites

- Docker (with Compose)
- JDK 25 (the Gradle toolchain requires it; the wrapper downloads Gradle itself)

## Running

```bash
# 1. Start Temporal (dev server) and Toxiproxy
docker compose up -d --wait

# 2. Start the app (worker + REST API on :8080)
./gradlew bootRun
```

- Temporal UI: <http://localhost:8233>
- App: <http://localhost:8080>

Stop everything with `docker compose down`.

### Configuration

All in `src/main/resources/application.yml`:

| Property                   | Default          | Meaning                              |
|----------------------------|------------------|--------------------------------------|
| `demo.hold-latency`        | `8s`             | Delay added to every hold call       |
| `demo.compensation-latency`| `2s`             | Delay added to every unhold call     |
| `TEMPORAL_HOST` / `TEMPORAL_PORT` | `localhost` / `7233` | Temporal frontend address |

Keep the compensation latency lower than the hold latency, otherwise the race is not visible
(the app logs a warning in that case).

## Demos

Each async demo starts a workflow and returns its id right away. You then **cancel it while a
hold is in flight** and watch the app log.

Cancel from the Temporal UI (open the workflow → *Request Cancellation*), or from the CLI
without installing anything:

```bash
docker exec saga-demo-temporal temporal workflow cancel \
  --address 127.0.0.1:7233 --workflow-id <id>
```

Timing: with the default latencies the credit hold is in flight during the first ~8s, the
debit hold during the next ~8s (in the sequential demos).

### 1. Naive saga: `POST /holdings`

```bash
curl -X POST localhost:8080/holdings
```

Code: `HoldingWorkflowV1Impl`. This is the "textbook" saga:

- compensation is registered **after** the hold returns;
- `saga.compensate()` runs inside the already-cancelled scope;
- activities use the default `TRY_CANCEL` cancellation type.

Cancel during the credit hold: the workflow stops immediately, but the HTTP hold is still
travelling and lands in the ledger as `APPLIED`, with no unhold ever sent. Cancel during the
debit hold: the unhold activities are scheduled in a cancelled scope and never run. Either way,
**holds leak**.

### 2. Local activities: `POST /holdings-local`

```bash
curl -X POST localhost:8080/holdings-local
```

Code: `HoldingWorkflowLocalActivitiesV1Impl`.

- holds run as **local activities**, unholds as regular activities;
- compensation is registered **before** each hold;
- `CancellationScope.throwCanceled()` is called explicitly, because local activities don't fail
  on workflow cancellation by themselves;
- compensation runs in a **detached** cancellation scope, so it is not cancelled with the workflow.

Expected log: holds are `APPLIED` first, then the unholds are `RELEASED`.

### 3. Parallel holds: `POST /holdings-parallel`

```bash
curl -X POST localhost:8080/holdings-parallel
```

Code: `HoldingWorkflowParallelExecutionV1Impl`.

- both holds start at once via `Async.function`;
- both compensations are registered up front;
- activities use `ActivityCancellationType.WAIT_CANCELLATION_COMPLETED`, so the workflow waits
  for in-flight holds to actually finish instead of moving on while they are still travelling;
- `Promise.allOf(...).get()` (not `cancellableGet`) waits for both, then `throwCanceled()`
  and a detached compensation.

Expected log: both holds `APPLIED`, then both unholds `RELEASED`.

### 4. Production-style synchronous call: `POST /holdings/sync`

```bash
curl -i -X POST "localhost:8080/holdings/sync?sourceId=order-42"
```

Code: `ProdHoldingAmountOperationService` + `ProdHoldingOperationWorkflowV1Impl`.

The HTTP caller blocks while the saga runs. The service waits **4s** for the result; with an 8s
hold latency that deadline always runs out, so the service **cancels the workflow itself** and
returns `504 Gateway Timeout`. No manual cancellation needed.

The workflow combines everything from the previous demos:

- compensation registered before each hold;
- `WAIT_CANCELLATION_COMPLETED`, so an in-flight hold finishes before compensation starts;
- `CancellationScope.throwCanceled()` after the last step;
- compensation in a detached scope; compensation errors are logged, not rethrown;
- non-cancellation failures become an `ApplicationFailure` with code `201231`, mapped to
  `422 Unprocessable Content` by the service.

Idempotency: the workflow id is `holding-v1-<sourceId>`. Retrying with the same `sourceId`
while the workflow is running joins the existing execution instead of starting a new one;
the id can only be reused after a failed run (`ALLOW_DUPLICATE_FAILED_ONLY`).

`/holdings-local/sync` and `/holdings-parallel/sync` call the same service, so they behave
identically to `/holdings/sync`. The `timeoutMillis` query parameter is accepted but not used yet.

## Project layout

```
src/main/java/com/skyro/saga/
├── controller/   REST endpoints that start workflows
├── service/      synchronous caller with deadline + cancellation
├── temporal/     workflow interfaces and implementations (one per demo)
│   └── activity/ HoldingActivity: HTTP calls to the product via Toxiproxy
├── product/      fake downstream system (ProductLedger) and its HTTP client
└── toxiproxy/    applies the latency toxics on startup
```

All workflows and activities run on one task queue: `saga-demo-local-holding-queue-v1`.
