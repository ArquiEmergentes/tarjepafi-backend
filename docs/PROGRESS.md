# Progress and handoff log

How to use this file:

1. Read "Current state" and "Next step" before touching anything.
2. Work on the next unticked step of [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) section 7.
3. Before you stop, tick what is **verified** (not merely written), rewrite "Current state" and "Next step",
   and add a dated line to the log with anything the next person could not guess from the code.

## Current state

- Date: 2026-11-24
- **Plan revision 4 (team decision of 2026-11-22): no web app work in this plan.** Another team member rebuilds the
  frontend from this backend and the Wokwi reader. Scope here: backend, firmware, deployment, and the API contract
  as a deliverable (plan D2, D13, D14, section 8). The web app steps of the checklist were replaced by contract steps.
- **Phase 1 is done (milestone M1, 2026-11-16).** Its web app step 1.7 (commit `6b6add6` in `tarjepafi-webapp`) is
  kept only as a reference for the frontend owner.
- **Phase 2 is deployed and verified on Azure (2026-11-23).** Resource group
  `rg-tarjepafi-demo` (Chile Central), App Service B1 `asp-tarjepafi-demo`, backend `tarjepafi-api-pafi7k2`
  (`https://tarjepafi-api-pafi7k2.azurewebsites.net`), broker `aci-tarjepafi-broker`
  (`tarjepafi-broker-pafi7k2.chilecentral.azurecontainer.io`), Neon database. Images come from GHCR (public).
  Generated secrets live outside the repo, in `~/.tarjepafi-secrets.env`, and the Neon values in
  `~/.tarjepafi-neon.env`. CORS allows `http://localhost:4200` only until the frontend owner gives their origins.
- **Azure CLI sessions expire** after 90 days idle: run `az login` again before any cloud step.
- Git: `main` and `develop` exist; the revision 4 plan and the phase 2 docs are merged into `develop` (PRs #2 and #3).
  Phase 3 is on `feature/credentials-catalogues`, uncommitted.
- Build: JDK 25 is installed and `mvn verify` is green on it, but `JAVA_HOME` may still point at JDK 24: set it to
  `C:\Program Files\Java\jdk-25.0.5.0.3` for Maven.
- **Phase 3 is deployed and verified (2026-11-23)**, PR #4 merged, image built by CI, backend restarted on Azure. Credentials,
  card holders, permissions, enrollment verification, settings and the catalogues work on
  `https://tarjepafi-api-pafi7k2.azurewebsites.net`; `docs/API.md` is frozen for them. The backend declares the exchanges,
  so the broker creates `reader-demo` by itself (checked by restarting the ACI container, then the MQTT permission
  checks again).
- **Phase 4 is implemented and tested locally, not deployed** (branch `feature/iot-backbone`): `V6`, reader management,
  the MQTT ingestion pipeline (`DeviceEventConsumer`, idempotent, DLQ), heartbeats, offline detection, incidents, the
  tap-resolved consumer with the display reply, the temporary fallback resolver, polling endpoints and simulated taps.
  116 tests green on JDK 25. `docs/API.md` has the IoT section and `docs/http/iot.http` exists. Phases 5 to 9 are not
  started; there is no firmware yet.

## Next step

1. Phase 4: commit, PR to `develop` (the person decides), merge, let CI publish the image, restart the App Service app,
   then run the Verify block on the deployed environment: `mosquitto_pub` of a v1 tap payload against the ACI broker
   creates one event in `GET /iot/readers/{id}/tap-events` (and in the `after` poll), publishing it twice yields one, a
   malformed payload lands in `iot.device-events.queue.dlq`, an unregistered reader opens an incident, and
   `mosquitto_sub` on `tarjepafi/readers/<id>/display` sees the reply. Also run `docs/http/iot.http`. Then tick 4.9 and the
   Verify block and freeze the IoT section of `docs/API.md`. Afterwards truncate the IoT tables in Neon and restart the
   app so the showcase starts from the seed (as after phase 3).
2. Hand the frontend owner the deployed URL, Swagger UI and `docs/API.md` (plan section 8) and ask for their origins
   (`CORS_ALLOWED_ORIGINS`); today only `http://localhost:4200` is allowed.
3. Phase 5 (tap resolution: attendance and access).

## Checklist

### Phase 0 · Foundations
- [x] 0.1 Initial commit on `main`, `develop` created (done by the team, 2026-11-16; the scaffold went in as one commit, so there is no `feature/project-scaffold` branch)
- [x] 0.2 Maven project (Spring Boot 4.1.x, Java 25)
- [x] 0.3 Package skeleton per bounded context
- [x] 0.4 `compose.yaml` (PostgreSQL, RabbitMQ + MQTT)
- [x] 0.5 `application.yml` profiles, `.env.example`
- [x] 0.6 Flyway baseline, actuator health, springdoc
- [x] 0.7 CORS
- [x] 0.8 `shared`: `CampusClock`, date-time JSON format (Jackson default already matches the web app). The shared error handler is deferred to step 1.4, where the first real controllers need it
- [x] 0.9 README
- [x] Verify block passed (2026-11-15, on JDK 24 and port 8081, see log)

### Phase 1 · IAM and web app sign-in
- [x] 1.1 `users` table, `User` aggregate
- [x] 1.2 Shared `ProblemDetail` error handler
- [x] 1.3 `AdminDataSeeder` (TS01), `DemoDataSeeder` for users
- [x] 1.4 `POST /auth/sign-in` (TS02)
- [x] 1.5 JWT filter, `GET /auth/me` (TS03, TS04)
- [x] 1.6 Role rules replace the `open-api` switch
- [x] 1.7 Web app `feature/backend-auth` (done under revision 2; reference only since revision 4)
- [x] Verify block passed (milestone M1, 2026-11-16)

### Phase 2 · Demo environment (first deployment)
- [x] 2.1 Dockerfile and CI (CI green on GitHub, images published to GHCR)
- [x] 2.2 Azure resources (App Service B1, Neon, ACI broker), created 2026-11-23
- [x] 2.3 Broker users (`tarjepafi-backend`, restricted `reader-demo`) (verified on ACI 2026-11-23; see the log for the exchange caveat)
- [x] 2.4 CORS with the frontend owner's origins
- [x] 2.5 `docs/API.md` started, `docs/http/iam.http` (every request run with curl against the deployed API, 11 of 11 as documented)
- [x] 2.6 `docs/DEPLOYMENT.md` filled with the real names
- [x] Verify block passed (2026-11-23: health, `iam.http` 11/11, CORS, `reader-demo` MQTT checks on ACI; the reader user needed the exchange declared by hand, see the log)

### Phase 3 · Credentials and catalogues
- [x] 3.1 Tables (`V3` credentials, `V4` spaces, `V5` courses, schedules, enrollments; one active card per holder enforced by a partial unique index)
- [x] 3.2 Domain (ported `credentials-domain.spec.ts`: 9 unit tests)
- [x] 3.3 Endpoints (US10, US11, US13, US14, US15): 30 API tests against PostgreSQL and RabbitMQ containers
- [x] 3.4 `CardHolderDirectoryPort` (TS29), events (`tarjepafi.events` after commit, 2 messaging tests; the backend now declares the three exchanges)
- [x] 3.5 Catalogue read endpoints (spaces, courses, schedules, enrollments)
- [x] 3.6 Contract: Swagger, `docs/API.md`, `docs/http/credentials.http`; frozen after Verify (Swagger has the error codes in each description; request/response examples are still to add)
- [x] Verify block passed (2026-11-23, deployed API: 34 of 34 requests of `credentials.http` gave the documented status, values checked afterwards)

### Phase 4 · IoT: readers, messaging backbone, ingestion
- [x] 4.1 RabbitMQ topology (device events, tap resolved and fallback queues with their DLQs; the other queues of plan section 4 are declared by the contexts that consume them, in phase 5 onwards)
- [x] 4.2 Tables and domain (`V6`; ported `iot-domain.spec.ts` and `iot-events-domain.spec.ts`: 16 unit tests)
- [x] 4.3 Reader endpoints (TS05–TS09)
- [x] 4.4 `DeviceEventConsumer` + `DevicePayloadAdapter` (TS10): idempotent on `eventId`, malformed payloads end in the DLQ (7 adapter tests, pipeline tests against RabbitMQ)
- [x] 4.5 Heartbeats and offline detection (TS11): telemetry, heartbeat and error messages, `ConnectivityMonitor` every 30 s
- [x] 4.6 `POST /iot/readers/{id}/taps`
- [x] 4.7 Tap-resolved consumer and display reply
- [x] 4.8 Temporary fallback resolver (`tap.received`; removed in 5.5)
- [ ] 4.9 Contract (polling-friendly tap events, incidents, reader status); frozen after Verify. Written (`docs/API.md`, `docs/http/iot.http`), not yet run against the deployed API
- [ ] Verify block passed (local: 116 tests green; not yet on the deployed environment)

### Phase 5 · Tap resolution: attendance and access
- [ ] 5.1 Attendance tables and domain
- [ ] 5.2 `CardTapEventConsumer` (TS13)
- [ ] 5.3 Auto-close, teacher endpoints, TS12, TS14–TS17
- [ ] 5.4 Space & Facility consumer and endpoints (TS18, TS19, TS22, TS28, TS30)
- [ ] 5.5 Remove fallback resolver
- [ ] 5.6 Contract + `docs/http/demo.http`; frozen after Verify
- [ ] Verify block passed (milestone M2)

### Phase 6 · Wokwi firmware and the live showcase
- [ ] 6.1 Project files and circuit
- [ ] 6.2 Modules
- [ ] 6.3 Tap flow
- [ ] 6.4 Buffer flush on reconnect
- [ ] 6.5 Offline switch
- [ ] 6.6 Wokwi tag UIDs in the seed overlay
- [ ] 6.7 Public wokwi.com project saved, URL documented
- [ ] 6.8 Demo script rehearsed twice
- [ ] 6.9 Wokwi URL and tag table handed to the frontend owner
- [ ] Verify block passed (milestone M3; M4 with the frontend is rehearsed with its owner)

### Phase 7 · Data Management
- [ ] 7.1 Event ingestion (TS24)
- [ ] 7.2 Query, dashboard and report endpoints (TS25–TS27)
- [ ] 7.3 Contract; frozen after Verify
- [ ] Verify block passed

### Phase 8 · Reservations
- [ ] TS20, TS21, TS22 check-in, TS23, e-mail adapter, contract (screens are the frontend owner's)
- [ ] Verify block passed

### Phase 9 · Hardening and evidence
- [ ] 9.1 Load tests (QAS01, QAS02)
- [ ] 9.2 Sign-in hardening, role review, broker TLS
- [ ] 9.4 SSE stream, only if the frontend owner asks (D13)
- [ ] 9.3 Report evidence

## Log

- 2026-11-15 · Plan written after reviewing the report and the web app (`develop`). Web app `develop` was checked
  out locally in `tarjepafi-webapp` (it was on the empty `main`).
- 2026-11-15 · Phase 0 verified: `mvn -Djava.version=24 verify` green (7 unit tests + full context test on
  Testcontainers), `docker compose up -d --wait` healthy, RabbitMQ lists the MQTT listener on 1883, health `UP`,
  Swagger UI 200, unauthenticated API call 401, CORS header for `http://localhost:4200`, clock `GET`/`PUT` work
  under `demo,open-api`.
- 2026-11-15 · Machine-specific findings on the first dev machine: a locally installed PostgreSQL owns port 5432,
  so Compose publishes PostgreSQL on **5433** by default; another process owns 8080, so the app was verified with
  `PORT=8081`. The default app port stays 8080; if you keep 8081 locally, use it in the web app's `environment.ts`.
- 2026-11-15 · `/error` must stay in the `permitAll` list of `SecurityConfiguration`: without it every 400 is
  turned into a 401 by the error dispatch.
- 2026-11-15 · Spring Boot 4 notes: starters are split (`spring-boot-starter-webmvc`, `-flyway`, `-amqp`, each with
  a `-test` twin), Testcontainers 2 uses `org.testcontainers.postgresql.PostgreSQLContainer` (no generics), and
  springdoc 3.2.0 works with Boot 4.1.2.
- 2026-11-15 · Plan revision 2. Team decision: the web app changes to fit the backend. Measured the web app to
  size that refactor (plan section 8): `presentation/` never injects an API endpoint, so only `application/` and
  `infrastructure/` change. Deployment moved to phase 2 because the showcase runs from wokwi.com, which cannot
  reach a laptop.
- 2026-11-15 · The local `tarjepafi-webapp` checkout was found on `feat/sprint1-fase7-testing-suite` (not
  `develop`), and a `feat/connection-tests` branch exists. Agree on the base branch before phase 1 step 7 (R5).
- 2026-11-15 · Phase 1 backend. JWT is HS256 through `spring-security-oauth2-jose` (Nimbus), not the resource
  server starter; `JWT_SECRET` (32+ bytes) has a default only in the `dev` profile, so a deployment without it
  fails at start-up on purpose. A token stops working as soon as its user is no longer `ACTIVE` (checked per
  request). The `open-api` profile is gone. Boot 4 uses Jackson 3: import `tools.jackson.databind.*` and use
  `asString()`, not `asText()`.
- 2026-11-15 · `shared` must not import from a context: the bearer filter depends on the `TokenAuthenticator`
  port in `shared/application/security`, which IAM implements. New routes get their role rule in
  `SecurityConfiguration`. Demo seeders are one per context, reading their collections through `DemoSeed`.
- 2026-11-16 · Step 1.7 written in `tarjepafi-webapp/webapp` (branch `feature/backend-auth`, nothing committed).
  `environment.ts` has `apiBaseUrl` (`http://localhost:8080/api/v1`) and `legacyApiBaseUrl` (json-server);
  `BaseApiEndpoint` and `CampusSettingsApi` use the legacy one, so only `UsersApiEndpoint.signIn/me` hit the
  backend. `AuthStore` stores `{ token, user }` under `tp.session` (an old session without token is discarded on
  restore), exposes `token`, maps the problem `code` (fallback to HTTP status), and revalidates with `auth/me`.
  `iam/infrastructure/bearer-token.interceptor.ts` only touches URLs under `apiBaseUrl`, skips sign-in, and calls
  `signOut()` on 401. `UsersApiEndpoint` still extends `BaseApiEndpoint` (legacy `users` collection) though nothing
  else calls it; drop that inheritance when json-server goes.
- 2026-11-16 · Verified against the running backend (curl, port 8081, `dev,demo`): the 7 demo users (admin,
  docente, student, staff, second teacher → 200; `jsalazar` INACTIVE and `soporte` SUSPENDED → 403
  `ACCOUNT_DISABLED`), wrong password → 401 `INVALID_CREDENTIALS`, `foo@gmail.com` → 400 `INVALID_EMAIL`,
  `GET auth/me` 200 with token and 401 without, teacher token on `PUT simulation/clock` → 403, CORS preflight for
  `http://localhost:4200` allows `authorization`. `ng build` and `ng test` (49 tests) green.
- 2026-11-16 · Web app findings: `package-lock.json` on `feat/connection-tests` is out of sync (`npm ci` fails on
  missing `@emnapi/*`); installed with `npm install --no-package-lock`, lockfile untouched. The Playwright
  acceptance suite signs in through the UI, so it now needs the backend running and `apiBaseUrl` pointing at it
  (its `API` constant and `webServer` still target json-server on 3000): not run, not adapted yet. Docker Desktop
  was stopped and was started for the check; the backend process was stopped afterwards, compose is still up.
- 2026-11-16 · Phase 1 closed. Browser check (Playwright, Chromium, real UI against the backend on 8080, json-server
  on 3000, `ng serve` on 4200): 27 of 27 checks. The 5 enabled demo users land on their home (admin → dashboard,
  both teachers → my sessions, student and staff → coming-soon); `jsalazar` (INACTIVE) and `soporte` (SUSPENDED)
  show "Tu cuenta no está habilitada…", a wrong password shows "Correo o contraseña incorrectos.", `admin@gmail.com`
  shows the e-mail message; the stored session holds the token and never the password; a reload keeps the session
  and calls `GET auth/me` with a Bearer header; json-server calls carry no Authorization header and no other call
  goes to the backend; a tampered token gets 401 from `auth/me`, the session is cleared and the app ends on
  `/login?returnUrl=…`; a session in the old format (no token) is discarded; sign-out from the profile page works;
  a teacher on `/admin/dashboard` is sent to `/access-denied`. The 24 Playwright **acceptance** scenarios
  (`--project=acceptance --no-deps`) pass with sign-in going to the backend. `mvn verify` on JDK 25: 29 tests, 0
  failures. The earlier curl checks (log of the same day) cover 403 for a teacher token on an admin route.
- 2026-11-16 · Things the next person will trip over: (1) the Playwright `webServer` starts its own json-server on
  port 3000 (`reuseExistingServer: false`), so stop `npm run api` before running the suite, and start the backend
  first because the UI sign-in now goes to it; (2) the `integration` project describes the json-server contract and
  is retired by the plan, so run `npx playwright test --project=acceptance --no-deps`; (3) the web app's Playwright
  needs Chromium build 1255 (`npx playwright install chromium`); (4) `JAVA_HOME` points at JDK 24 while `java` on
  `PATH` is 25: Maven uses `JAVA_HOME`, and classes compiled by one JDK do not load on the older one
  (`UnsupportedClassVersionError`, class version 69 vs 68) — run `mvn clean` if `target/` was built by the other;
  (5) port 8080 was free again, so the backend ran on its default and `environment.ts` needed no change; (6) after a
  401 the app leaves the page by itself (it ends on `/login?returnUrl=…`), no extra redirect code was needed.
- 2026-11-16 · Phase 2 decisions and local work (plan revision 3). Azure checks (read-only): the subscription is
  Azure for Students with 11 unrelated resource groups (other coursework, not touched); `Microsoft.DBforPostgreSQL`,
  `ContainerInstance`, `OperationalInsights` and `App` were not registered; 6 vCPU quota per region; PostgreSQL
  provisioning is restricted in East US; small VMs are `NotAvailableForSubscription` in East US 2, Brazil South and
  Mexico Central; Central US and Chile Central allow everything. Prices came from aggregator sites and were not
  confirmed on Microsoft's page. TiDB was rejected (MySQL-compatible, the backend and tests are PostgreSQL).
- 2026-11-16 · Broker image lessons (`docker/rabbitmq/`), all found by testing, none obvious: (1) `rabbitmq-diagnostics ping`
  succeeds while the Erlang node is up but the broker app is not, so wait with `check_running`; (2) the official
  entrypoint drops to the `rabbitmq` user, and a CLI run as root creates `.erlang.cookie` with the wrong owner and
  the broker then dies with `eacces`, so run the CLI through `su-exec rabbitmq` and never before the node wrote the
  cookie; (3) **topic permissions can only be set on an exchange that exists**, and the backend declares
  `tarjepafi.devices`, so the script waits for it and creates the user only then (an earlier version created the
  user first and left it with no topic restriction, which let it publish to any routing key); (4) management
  statistics lag by several seconds, read queues with the `get` API when testing; (5) with `MSYS_NO_PATHCONV=1`
  `-o /dev/null` breaks curl on Git Bash, and without it `-p /` is rewritten to a Windows path.
  Verified with mosquitto clients: a tap on `tarjepafi/readers/<id>/tap` is routed to `tarjepafi.readers.<id>.tap`;
  `other/topic` and `tarjepafi/admin/x` drop the connection and reach nothing; subscribing to `#` is denied; a wrong
  password and an anonymous connection are refused; a broker message on `tarjepafi.readers.<id>.display` reaches the
  subscribed reader.
- 2026-11-16 · Backend image: `java -Djarmode=tools extract --layers` layers are merged into `/app`; the container
  runs as a non-root user and `JAVA_TOOL_OPTIONS` sets `MaxRAMPercentage=70`. App Service needs `WEBSITES_PORT=8080`.
  The `dev` profile must not be active in the cloud (it carries a default JWT secret); use `demo` plus `JWT_SECRET`.
  Health checks RabbitMQ, so with the broker stopped the backend reports `DOWN`.
- 2026-11-15 · Team decisions: web app base branch is `feat/connection-tests` (identical tree to
  `feat/sprint1-fase7-testing-suite`; `develop` reverted the test suite). `src/main/resources/seed/db.json` is
  byte-identical to that branch's `server/db.json`. The broker will be RabbitMQ on Azure (student credit).
  Planning was done in one chat; implementation continues in new sessions, so this file is the only memory.
- 2026-11-22 · Plan revision 4. Team decision: the web app is no longer this plan's job; another team member rebuilds
  the frontend from the finished backend and the Wokwi code. Consequences written into the plan: web app steps
  replaced by contract steps (Swagger, `docs/API.md`, `docs/http/*.http`, freeze per phase, D14); live lists are
  polling-friendly with an `after` cursor and SSE only on request (D13); Verify blocks prove things through API reads
  instead of screens; the demo script has an API proof per step (M3) and the frontend version becomes M4; the frontend
  handoff schedule is plan section 8. The old web app stays a read-only reference for rules, model and Spanish texts.
- 2026-11-22 · Status check: no backend work since 2026-11-16; nothing in Azure; `az` session expired; `gh` CLI is not
  installed on this machine (CI status could not be checked from here).
- 2026-11-23 · Phase 2 deployed. Created in Chile Central: resource group, App Service plan B1 and backend app
  (suffix `pafi7k2`), ACI broker, app settings from the secret files (never printed). Verified on the deployed API:
  health UP, demo teacher sign-in 200, wrong password 401, no token 401, CORS preflight accepted for
  `http://localhost:4200` and refused (403) for another origin, HTTP redirects to HTTPS (301). Broker on ACI: `reader-demo`
  can publish to `tarjepafi/readers/test/tap` and receive on `.../display`; publishing to `other/topic` and subscribing
  to `#` are refused by the broker; a wrong password is refused. **Finding:** the backend does not declare the
  `tarjepafi.devices` exchange yet (phase 3 declares it, see the plan), so the reader user did not exist after the first
  start; the 2026-11-16 note "the backend declares it" was premature. For this check the exchange was declared by hand
  with `rabbitmqadmin` inside the container. Until the backend declares it, a broker restart leaves no reader user.
  Signing in as `admin@upc.edu.pe` with the generated admin password gives 401 because the Neon database already held
  that user from the local test run (the seeder does not update existing users).
- 2026-11-23 · Phase 3 written and tested locally. Decisions: spaces and the academic catalogues live in their own
  contexts (`V4`, `V5`) and are read-only here; Credentials checks a space through `SpaceCatalog` (the facade of Space &
  Facility) and Space keeps its roles as plain text so no context imports another's classes. One ACTIVE card per holder is
  also enforced by a partial unique index. `block` needs a note, the other actions take an optional one. Reactivating a
  card of a student with a pending enrollment is `422`, a second active card is `409`. Integration events are sent after
  the transaction commits (`IntegrationEventForwarder`), so a rollback sends nothing and a broker outage does not undo a
  change. The daily enrollment verification is `@Scheduled` at 00:05 campus time (`tarjepafi.credentials.enrollment-
  verification.enabled=false` turns it off). Lessons: RabbitMQ 4 refuses transient non-exclusive queues (use exclusive
  ones in tests); a null `String` parameter inside `lower(concat(...))` makes PostgreSQL type it as `bytea`, so search
  patterns are built in Java; `Map.of` throws on `get(null)`. The old web app's `settings` is one object in `db.json`.
- 2026-11-23 · Phase 3 deployed and verified. Restarted the App Service app after CI published the image (`develop`
  at `690a11e`): Flyway created `V3` to `V5` on Neon and the demo seed imported 52 holders, 55 cards, 7 history rows,
  2 permissions, 15 spaces, 6 courses, 12 schedules and 240 enrollments. Ran `credentials.http` with curl: every
  status as documented (one run changes the data, so afterwards the card tables were truncated in Neon and the app
  restarted, which restored the seed). US15 deactivated the 5 active cards of students with a pending enrollment as
  `SYSTEM` with reason `ENROLLMENT_NOT_COMPLETED`, and a second run found nothing. The broker was restarted: the
  reader user was created without help once the backend reconnected, and the MQTT checks (publish tap, receive display,
  `#` denied, other topic refused, wrong password refused) passed again. A script bug is worth remembering: Git Bash
  `/tmp` is not the path Windows Python sees, so write response files under the scratchpad or parse in Python.
- 2026-11-24 · Phase 4 written and tested locally. Decisions: (1) a tap is stored once per `eventId` and starts `PENDING`;
  IoT itself rejects (outcome `REJECTED`, reply on the display) taps from an unregistered reader or one that cannot process
  taps, and taps of an unknown, inactive or unlinked card; those readers also get an incident (never two open incidents of
  one type per reader). Valid taps are announced as `iot.card-tap` and answered by whoever publishes
  `<context>.tap-resolved`. (2) The device contract gained `Heartbeat`, `ReaderError` and an optional `source` (plan section
  4 updated). (3) `cursor` is a database identity column (`seq`), the polling endpoints return `{items, nextCursor}`
  newest first; a still-`PENDING` tap is not repeated, clients look it up by id. (4) Only the queues with a consumer are
  declared (device events, tap resolved, fallback); the plan's other queues come with phases 5 and 7. (5) A malformed
  payload is retried three times (500 ms, 1 s) and parked in the queue's `.dlq`; retry settings are global
  (`spring.rabbitmq.listener.simple.retry`). (6) With the simulated clock nothing goes offline until the clock moves; the
  `ConnectivityMonitor` runs every 30 s and `DeviceEventHandler.markOffline` is what it calls. Lessons: Hibernate needs
  `@Generated(event = INSERT)` to read an identity column back; RabbitMQ 4 needs exclusive queues in tests; two telemetry
  samples in the same simulated second tie, so tests stamp them apart; the Bash tool of this environment chokes on some
  heredocs (backslashes, triple quotes), write such files with the Write tool.
