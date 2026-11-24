[README.md](https://github.com/user-attachments/files/35321266/README.md)
# Pulse  — Webhook Workflow Orchestrator

A distributed scheduler for **platform / integration engineering teams** that glue internal services to third-party APIs through webhooks.
Spring Boot 3 · Kafka · Redis · Postgres · WebSocket · React (Vite + Tailwind).

**New in V3:** a redesigned blue interface with a plain-language overview, and the **run inspector**: open any run and read the exact JSON Pulse sent and received. The scheduling engine is unchanged from V2.

## Why not Jenkins?

| | Jenkins | Pulse |
|---|---|---|
| Unit of work | Build on an agent (JVM, plugins, workspace) | An HTTP webhook, or a multi-stage pipeline of them |
| Calling 3rd-party APIs | Plugins / shell `curl` | First-class: methods, headers, payload templating, retries, response assertions |
| Waiting on external systems | Blocks an executor | **Callback stages** park the run (no thread held) until the other side POSTs back to `{{callback.url}}` |
| Human gates | `input` step holds an executor | **Approval stages** — persisted, resumable, no thread held |
| Secrets | Credentials plugin, masked in logs | Built-in AES-256-GCM vault: BEARER / BASIC / HEADER / HMAC (`sha256=` body signature) |
| Passing data between steps | Files / env vars | `{{stages.<name>.response.body.path}}` templating |
| Scaling | Agents + labels | Kafka consumer groups; **worker pools** via tag → one topic per tag |
| Live view | Page refresh | WebSocket push |

## Features
- **SINGLE** or **PIPELINE** execution mode. Stages: condition (`ON_SUCCESS`/`ON_FAILURE`/`ALWAYS`), manual approval, await-callback with timeout, expected status codes, body-contains assertion, per-stage credential.
- **Parameterized runs** (`{{params.x}}`), prompted in the UI on "Run now".
- **Downstream chaining** with save-time cycle detection and a runtime depth cap of 10.
- **Worker pools**: set `WORKER_TAGS=heavy` on a replica; jobs with `workerTag: heavy` go only to it.
- **Credentials vault** referenced as `{{secrets.name}}` or attached to a job/stage.
- Per-execution log lines and parameter snapshot.

## Run inspector (V3)
Select any run (Overview, Activity, a job's run history, or "See what happened" on a failure) to open the inspector:
- **Request sent**: method, URL, headers and body as JSON (collapsible tree or raw).
- **Response received**: status, headers and body, with the time taken.
- **Details**: worker, run ID, parameters used and the log timeline.
- Pipeline runs get a chip per stage, so you can step through the whole chain. Live runs refresh themselves.
- **Copy as cURL**, **Copy JSON** and **Download** export what you are looking at.
- **Secrets are masked before anything is saved.** `Authorization`, `Proxy-Authorization`, `Cookie` and `Set-Cookie` keep only their scheme (`Bearer ••••••`), and every vault secret value found anywhere in the request or response is replaced with `••••••`. Response bodies are capped at 16,000 characters.
- Runs recorded before V3 have no captured request; the inspector says so.

## Template syntax
`{{params.name}}` · `{{secrets.name}}` · `{{stages.<stage>.response.status}}` / `.statusCode` / `.body` / `.body.a.b[0].c` / `.headers.X-Id` · `{{callback.url}}` `{{callback.token}}` `{{callback.executionId}}`

A callback stage must put `{{callback.url}}` in its payload/headers so the remote system knows where to call back:
`POST /api/callbacks/{executionId}?token=…` (public endpoint, token-guarded) with `{success, message, output}`.

## Run
```bash
# Backend + infra
docker compose up --build        # backend :8080, login admin / admin123
# Frontend
cd frontend && npm install && npm run dev   # http://localhost:5173
```
IntelliJ: run infra with `docker compose up postgres redis kafka`, run `SchedulerApplication`.
**Production:** set `JWT_SECRET`, `ADMIN_PASSWORD`, `CREDENTIALS_ENCRYPTION_KEY` (`openssl rand -base64 32`), and `CALLBACK_BASE_URL` (must be reachable by the systems you call).

## API
| Endpoint | Purpose |
|---|---|
| `POST/GET/PUT/DELETE /api/jobs[/{id}]` | CRUD (stages, parameters, tags, downstream) |
| `POST /api/jobs/{id}/trigger` (body: params map) | Run now |
| `POST /api/jobs/{id}/pause\|resume` | Pause/resume schedule |
| `GET /api/jobs/{id}/executions` | History |
| `GET /api/executions?status=&page=&size=` | All runs, newest first (size capped at 100) |
| `GET /api/executions/{id}/exchange` | What was sent and received for one run (secrets masked) |
| `GET /api/executions/{id}`, `/pipeline-runs/{runId}`, `/pending-approvals` | Inspect |
| `POST /api/executions/{id}/approve\|reject` | Approval gate |
| `POST /api/callbacks/{id}?token=` | Resume a callback stage (no JWT) |
| `GET/POST/DELETE /api/credentials` | Vault (values never returned) |
| `GET /api/dashboard/stats` | Dashboard |

## Verification status — read this
- Frontend: `npm run build` passes. A 37-step browser run against a mock API covered login, approvals, the inspector (tree, raw, copy, cURL, download, stage switching, masking), filters, creating jobs and credentials, and the phone layout. A separate run confirmed live updates arrive over a real SockJS/STOMP connection. Every request body the UI sends was compared with the backend's request classes.
- Backend: **not compiled or run** in the authoring environment (Maven Central was unreachable). It was reviewed line by line and checked with a script (imports, brace balance, every Lombok getter/setter/builder call against the declared fields). Run `mvn clean install` first (it also runs the unit tests in `src/test`: JWT, job validation, pipeline validation, template engine) and expect to fix a small compile issue if one slipped through. The V3 capture code (`WebhookCallService`) was also compiled with `javac` against stubs and run against a real local HTTP server: masking, GET handling and failure capture all behaved. V3 adds `WebhookCallServiceTest`, which runs a real local HTTP server and checks that secrets never reach the captured request or response.
- Not exercised against real Kafka/Postgres/Redis: the `@KafkaListener` SpEL topic list, `KafkaAdmin.NewTopics`, and the jsonb column mappings.
- V3 adds nullable columns to `job_executions` (request and response capture); `ddl-auto=update` creates them on start, so an existing V2 database works.
- **Use a fresh database.** If you ran V1 before, run `docker compose down -v` first: Hibernate's `ddl-auto=update` does not widen the status check constraints or drop old NOT NULL constraints on existing tables.
- Running the backend from your IDE: start only the infrastructure (`docker compose up postgres redis kafka`); the defaults (`localhost`) match. Kafka exposes `localhost:9092` to the host and `kafka:19092` to containers.

## Known gaps
- Resume uses the job's *current* stage definitions, not a snapshot at run start.
- A resumed pipeline can't read earlier stages' response headers through `{{stages...headers}}` (the inspector does show them).
- Pipeline stage retries are in-process and blocking.
- SINGLE mode has no response assertions (use a one-stage pipeline).
- Downstream trigger condition is per job; depth cap resets across approval/callback pauses.
- Credentials key is not rotatable.
- DLT topic is provisioned but unused (malformed Kafka messages are logged and skipped).
- A stage tagged for a pool that no replica serves waits silently.
