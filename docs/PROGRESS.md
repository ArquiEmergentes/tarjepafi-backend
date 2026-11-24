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
  `~/.tarjepafi-neon.env`. CORS allows `http://localhost:4781` (the port the frontend must use) and `http://localhost:4200` until the frontend owner gives their origins.
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
- **Phase 4 is deployed and verified (2026-11-24)**, PR #6 merged, CI image restarted on Azure; the Verify block passed 11 of 11
  against the ACI broker and the deployed API, and the 28 REST requests of `iot.http` gave the documented answers.
  What it contains: `V6`, reader management,
  the MQTT ingestion pipeline (`DeviceEventConsumer`, idempotent, DLQ), heartbeats, offline detection, incidents, the
  tap-resolved consumer with the display reply, the temporary fallback resolver, polling endpoints and simulated taps.
  116 tests green on JDK 25. `docs/API.md` has the IoT section and `docs/http/iot.http` exists.
- **Phase 5 is deployed and verified (2026-11-24)**, PR #8.
- **Phase 6 firmware runs on the deployed API (2026-11-24)**, branch `feature/wokwi-reader-firmware`. `firmware/reader-esp32/`
  ran on wokwi.com against the deployed backend: demo steps 1 to 7 passed by hand (reader ACTIVE, clock 10:00, teacher
  arrival, student attendance, blocked card, offline switch with `buffered: true` and an emptied buffer, API tap as plan B).
  Backend changes made for it: `buffered` stored and exposed (`V9`), live device messages stamped with campus now,
  telemetry ordered by `seq` (`V10`), Wokwi tag overlay `WokwiTagSeeder` (`TARJEPAFI_DEMO_WOKWI_TAGS=true`, set on the
  App Service), the Swagger "Authorize" button. Still open: 6.7 to 6.9 and the Verify block (clean rehearsal twice from
  the public Wokwi URL).

- **Phase 7 is deployed and verified (2026-11-24)**, PR #16: `V11`, `data_records` ingestion from `data.events.queue`, dashboard, reports with a `GENERATING` state, contract frozen, 161 tests green. Phase 6 still needs 6.7, 6.9 and its Verify (see Next step).

- **Phase 8 is deployed and verified (2026-11-24)**, PR #18: `V12`, reservations (create, cancel, queries), check-in by tap at `RESERVATION_REQUIRED` spaces, the forfeit and completion sweep, the release e-mail (SMTP when `SPRING_MAIL_HOST` is set, only logged otherwise), demo seed (cubicle `CUB-L01` requires a reservation, new reader `RDR-K-003`), contract frozen, 183 tests green.

## Next step

0. Phase 9 (hardening and evidence): load tests for QAS01 and QAS02, sign-in lockout and rate limiting, role matrix review, broker TLS, report evidence. Phase 6 still needs 6.7, 6.9 and its Verify.
1. Phase 6: reset the demo data and restart the app, run the demo script twice from the saved public wokwi.com project
   (6.7, 6.8; rotate the `reader-demo` password afterwards, the project shows it), hand the Wokwi URL and the tag table to
   the frontend owner (6.9), then tick the Verify block.
2. Hand the frontend owner the deployed URL, Swagger UI and `docs/API.md` (plan section 8) and ask for their origins
   (`CORS_ALLOWED_ORIGINS`); today only `http://localhost:4200` is allowed. IAM, credentials and IoT are frozen.
3. Small debts: Swagger request/response examples for IAM, credentials and IoT (D14).

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
- [x] 4.9 Contract (polling-friendly tap events, incidents, reader status); frozen after Verify (28 of 28 requests of `iot.http` as documented on the deployed API)
- [x] Verify block passed (2026-11-24, deployed: `mosquitto_pub` tap creates one event seen by the `after` poll, a repeat yields one, malformed payload in the DLQ, unregistered reader gives an incident, `mosquitto_sub` on `.../display` sees the reply)

### Phase 5 · Tap resolution: attendance and access
- [x] 5.1 Attendance tables and domain (`V7`: sessions, session participants, attendance records; `AttendanceDomainTest`, 10 tests)
- [x] 5.2 `CardTapEventConsumer` (TS13), `attendance.card-tap.queue`, `AttendanceTapService`
- [x] 5.3 Auto-close (`SessionAutoCloseJob`, every 30 s, and before every tap or teacher read), teacher endpoints `/class-sessions/**` (TS14–TS17). TS12 as "class-session consumers" is **not** built: sessions come from the seed only, see the log
- [x] 5.4 Space & Facility: `V8` (access records, work shifts, `@Version` on spaces), `SpaceCardTapEventConsumer` (TS22 without reservations), `AccessControlService`, TS18, TS19, TS28, TS30 and the access-record / work-shift reads
- [x] 5.5 Fallback resolver removed
- [x] 5.6 Contract written (`docs/API.md`, `docs/http/attendance.http`, `space-facility.http`, `demo.http`); **not frozen until the Verify block passes on the deployed API**
- [x] Verify block passed (milestone M2, 2026-11-24, deployed: 17 checks of the demo walkthrough through the API, 16 passed and the 17th was the script tapping a reader the clock jump had made OFFLINE, which is the documented behaviour). Local: `TapResolutionApiTests` (6) replay the demo walkthrough and acceptance 01 to 04 and 07 against real PostgreSQL and RabbitMQ, 132 tests green; still to run on the deployed API through `demo.http`

### Phase 6 · Wokwi firmware and the live showcase
- [x] 6.1 Project files and circuit
- [x] 6.2 Modules
- [x] 6.3 Tap flow
- [x] 6.4 Buffer flush on reconnect
- [x] 6.5 Offline switch
- [x] 6.6 Wokwi tag UIDs in the seed overlay
- [x] 6.7 Public wokwi.com project saved, URL documented
- [x] 6.8 Demo script rehearsed (2026-11-24, by hand against the deployed API, no errors)
- [x] 6.9 Wokwi URL and tag table handed to the frontend owner
- [ ] Verify block passed (milestone M3; M4 with the frontend is rehearsed with its owner)

### Phase 7 · Data Management
- [x] 7.1 Event ingestion (TS24)
- [x] 7.2 Query, dashboard and report endpoints (TS25–TS27)
- [x] 7.3 Contract written (`docs/API.md`, `docs/http/data-management.http`); **frozen (Verify passed on the deployed API)**
- [x] Verify block passed (2026-11-24, deployed, image of `develop` at `56b007c`, `V11` ran, Neon truncated before so the demo seed loaded): 11 checks through the API. The weekly report of 5 to 11 October went `GENERATING` (202) then `GENERATED` with 497 records, 77.5 % attendance, 104 granted, 15 denied, peak 07:00, 7 readers with incidents; the report of 5 and 6 October equals the seeded `rep-002`; the dashboard of 6 October gives PRESENT 11 / LATE 6 / ABSENT 5 / PENDING 18, 77.3 %, 38 / 6, 95 readings; the teacher gets 403, an unknown type 400, an unknown report 404. One live simulated tap added exactly one `CARD_READING` and one `ATTENDANCE` fact. Locally `DataManagementApiTests` (10) cover the same plus replays and the dead letter queue

### Phase 8 · Reservations
- [x] TS20, TS21, TS22 check-in, TS23, e-mail adapter, contract (`docs/API.md`, `docs/http/reservations.http`; screens are the frontend owner's). **frozen (Verify passed on the deployed API)**
- [x] Verify block passed (2026-11-24, deployed, image of `develop` at `1dee168`, `V12` ran, Neon truncated before): 20 checks through the API. The cubicle needs a reservation and `RDR-K-003` is ACTIVE; a reservation is CONFIRMED with its window ending at start + 10 minutes; overlap 409, teacher 422, classroom 422, teacher token 403; a tap with the student's card before the start is refused, one at 10:05 gives `tap.reservationActivated`, the reservation CHECKED_IN and the space OCCUPIED; cancel 200 then 409; with the clock past 12:10 the unclaimed reservation is FORFEITED by the sweep (within a minute), the used one COMPLETED and the space AVAILABLE; the slot is free again. The e-mail is only logged on the deployed app (no SMTP server), so the delivery is proven by `ReservationApiTests` (8) against Mailpit, not there

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
- 2026-11-24 · Phase 4 deployed and verified. After CI published the image (`develop` at `bc50a8a`) the App Service app was
  stopped and started; Flyway created `V6` on Neon and the demo seed imported 20 readers, 114 telemetry samples, 7 incidents
  and 246 tap events. Verify block against the deployed environment (11 of 11): a v1 tap published with `mosquitto_pub` as
  `reader-demo` became one `ACCESS` / `tap.received` event, found by the `after` poll and by
  `GET /iot/readers/RDR-A-301/tap-events`; the display reply `{eventId, tone: "info", lines: ["Lectura recibida"]}` reached
  `mosquitto_sub` on `tarjepafi/readers/RDR-A-301/display`; the same event published twice more still gave one; `this is not
  json` ended in `iot.device-events.queue.dlq` (checked with `rabbitmqctl list_queues` inside the container); a tap of an
  unregistered reader was `REJECTED` and opened a `CONFIGURATION_ERROR` incident; `reader-demo` still cannot subscribe to
  `#`. The simulated tap through the API also resolved. Afterwards the IoT tables were truncated in Neon, the DLQ purged
  and the app restarted, which restored the seed (20 readers, 5 open incidents). On Windows `az` is `az.cmd` when run from
  Python's `subprocess`.
- 2026-11-24 · Phase 5 implemented locally (not deployed). Decisions and things the next person could not guess:
  tap routing is decided inside each consumer from the space type and the holder role (`attendance.card-tap.queue` and
  `space.card-tap.queue` both bind `iot.card-tap`; space asks `AttendanceFacade.hasMeetingInProgress` for teachers in meeting
  rooms); IoT rejects unknown/inactive cards itself and now adds `spaceId`, `credentialId` and `cardHolderId` to
  `iot.tap-rejected`, which space reads to leave a DENIED access record at non-classroom readers. A tap is decided from
  its own time, not from the clock, and sessions due at that time are closed first. A duplicate event is not stored again but
  the reader is told "Evento ya procesado". The simulated clock keeps ticking in real time after a move (a tap is stamped a
  second before "now"), so ranges in tests start a little earlier than the tap. TS12 "class-session consumers" and the
  `attendance.class-session.queue` are not built: sessions exist only from the seed (14 sessions, 5 to 10 October), so the
  demo works for those days. TS19 occupancy counts only the campus (people who entered today and have not left), other
  spaces answer `tracked: false`; TS30 presences are derived from granted campus entry/exit access records (no separate
  `CampusPresence` table). `jsalazar@upc.edu.pe` is INACTIVE in the seed, so the third teacher cannot sign in.
- 2026-11-24 · Phase 5 deployed and verified. Image of `develop` at `3ed7451`, app stopped and started, Flyway ran `V7`/`V8`. The demo walkthrough gave the old web app outcomes (exit "1 h 30 min", counts absent 17 / justified 1 / late 1 / present 1, shift closed "8 h 03 min trabajadas", denied access record). Things seen: on the deployed stack a tap that writes records can take more than the 3 s the simulated-tap endpoint waits, so it answers `202` with the event `PENDING`; poll `GET /iot/tap-events/{id}` (it resolves within a few seconds). A clock jump with no heartbeats makes readers OFFLINE (30 s monitor), after which taps there are `tap.readerOutOfService`: move the clock back or restart to reseed. The broker still had the durable `iot.fallback.card-tap.queue` of phase 4 collecting messages; it was deleted by hand. Neon was truncated (context tables) and the app restarted to reseed.
- 2026-11-24 · Phase 6 groundwork. A minimal Wokwi sketch (`firmware/mqtt-smoke-test/`) reached the ACI broker over plain
  MQTT 1883 and published a `Heartbeat`. Lessons: (1) the ACI broker answered CONNACK after about 3 s (measured with a
  raw CONNECT from a laptop, bad credentials), and the `MQTT` library (256dpi) waits only 1 s by default, which shows as
  `lastError=-9 returnCode=6`: the firmware sets a 10 s timeout; (2) `returnCode=4` was a wrong `reader-demo` password,
  fixed by resetting it; the generated secrets file is not on every dev machine. Firmware decisions: library `MQTT`
  (256dpi) because `PubSubClient` cannot publish with QoS 1; ArduinoJson 7; the network module is `ReaderNetwork`
  because ESP32 Arduino core 3 already has a `NetworkManager` class; heartbeat 60 s and telemetry 30 s from the seed
  configuration of RDR-A-301; heartbeat + telemetry are sent before the buffer flush so the reader is ACTIVE again
  first; a reply up to 15 s late is still shown because the deployed backend can take more than 3 s; the MQTT client id
  gets a random suffix (Wokwi gives every ESP32 the same MAC). The backend ignores `buffered` (not stored, not exposed),
  so demo step 6 needs a small backend change.
- 2026-11-24 · Phase 6 manual run (wokwi.com against the deployed API). Steps 1 to 7 of the demo script passed. Things found and fixed:
  (1) the simulator runs at about half of real time, so the reader's clock drifted from the server's and, with the
  non-ticking simulated campus clock, every heartbeat was dated further in the past: the reader stayed OFFLINE after a clock
  move. The backend now stamps live messages (taps with `buffered: false`, heartbeat, telemetry, error) with campus now and
  only a buffered tap keeps its own time (`DevicePayloadAdapter`); consequence: telemetry samples of one second tie, so they
  are ordered by an insertion `seq` (`V10`). (2) the same slowness made the 30 s MQTT keep-alive last more than 45 s of real
  time, so RabbitMQ dropped the reader about every 70 s: the firmware keep-alive is 6 s. (3) the simulated MFRC522 returns
  the NFC Tag as `04:11:22:33` (4 bytes), not the 7 bytes in Wokwi's docs: the seed registers that. (4) the Wokwi tap button
  was not usable with the slide switch to the right: typing `o` in the serial monitor toggles forced offline too. (5) the
  backend stores `buffered` (`V9`) and `TapEvent.buffered` is in the API (additive). (6) the seed overlay maps Blue
  `01:02:03:04` to student U202312966, NFC Tag `04:11:22:33` to teacher D0001, Green `11:22:33:44` to staff T0001, Yellow
  `55:66:77:88` to a BLOCKED card (reply "Tarjeta inactiva", not "Tarjeta bloqueada"), Red and Key Fob unregistered; the old
  seed UIDs stop working while `TARJEPAFI_DEMO_WOKWI_TAGS` is true. Reply texts differ from the plan's table: "Llegada
  registrada" (not "Clase iniciada"). After a restart the API answers about 2 minutes before the seed finishes.
- 2026-11-24 · Phase 7 (local). Ported `report-calculator.ts` to `ReportCalculator`; before writing the Verify the same logic was run in Python on the seed (`dataRecords`, 503 rows) and reproduced the seeded `rep-001` and `rep-002` exactly, so the expected numbers of the weekly report of 5 to 11 October are 497 / 77.5 / 104 / 15 / 07:00 / 7. Decisions: (1) `data_records` is unique on (`event_id`, `data_type`, `subject_id`), not on `event_id` alone, because the seed has several facts per event; the envelope's `eventId` is the idempotency key of a live fact. (2) Facts: `iot.card-tap` and `iot.tap-rejected` are the `CARD_READING`s (the accepted outcome is not known to IoT at that point, so the payload says `ACCEPTED`/`REJECTED`), `attendance.recorded|confirmed|edited`, `space.access-*`, `space.work-shift-*`, `credentials.credential-status-changed` and `iot.incident-opened`; the tap-resolved events and `attendance.late-detected` are not facts. (3) The dashboard uses only data records, so reader and card status are not in it (other contexts own them). (4) Reports are created `GENERATING` and a 1 s job completes them once 2 s of real time have passed (`tarjepafi.reports.generation-delay-ms`), so a restart cannot strand one. (5) Facts are dated with the campus clock, so the demo week's report also counts the live demo's taps; the Verify numbers hold on a reset environment.
- 2026-11-24 · Phase 7 deployed and verified. Restarted after truncating the context tables; the first truncate failed because `data_records` did not exist yet (`V11` had not run), so the reset script lists the new tables only from now on and the very first deploy of a migration needs the old list. The seed finishes about 2 minutes after the API answers (data seeder last). The environment was reset again after the live tap: reports `rep-001` to `rep-003`, clock 09:50, `ses-02-7` SCHEDULED with 20 unmarked.
- 2026-11-24 · Phase 8 (local). Reservations live in `spacefacility` (the report puts `SpaceReservation` there); the policy `RESERVATION_REQUIRED` and the space status `OCCUPIED` were added to the web app's model as plan section 3 foresaw. Decisions: (1) routes are flat like the rest (`/spaces/{id}/reservations`, `/reservations/...`), not the report's `/admin/...`. (2) A tap at a reservation space looks at the first open reservation of that holder there that has not ended: before its start `tap.reservationNotStarted`, within start + 10 minutes it checks in, after that `tap.reservationExpired`; none: `tap.noReservation`; each leaves an access record (`DENIED` with `RESERVATION_NOT_STARTED`, `RESERVATION_EXPIRED` or `NO_RESERVATION`). (3) The sweep (`ReservationToleranceScheduler`, property `tarjepafi.reservations.sweep-ms`, default 60 s of real time) uses the campus clock, so with the simulated clock a forfeit follows a clock move within a minute. (4) The e-mail is sent after the commit and asynchronously (`@Async` `@TransactionalEventListener`); `MailReservationNotifier` sends through SMTP when `JavaMailSender` exists (`spring.mail.host`) and only logs otherwise, so the deployed demo logs it; a failure is logged and the reservation stays FORFEITED. (5) Local mail catcher: Mailpit in `compose.yaml` (SMTP 1025, inbox 8025) and as a Testcontainers container in the test. (6) The reservation events are not data records yet (Data Management has no `RESERVATION` type).
- 2026-11-24 · Phase 8 deployed and verified. Finding while verifying: the enrollment verification job (`EnrollmentVerificationJob`, cron `0 5 0 * * *` in real time, evaluated with the campus clock) deactivated 5 student cards (`cred-006`, `-012`, `-018`, `-030`, `-036`, reason `ENROLLMENT_NOT_COMPLETED`, changed at `2026-10-07T10:00:00`) when it fired while the demo clock stood at 10:00 and the deadline in settings is 2026-10-03. Truncating the context tables does not restore them (the credentials tables are not reset), so a reservation for `ch-u202310685` failed with `HOLDER_WITHOUT_ACTIVE_CREDENTIAL`. The cards were reactivated by SQL (status ACTIVE and the SYSTEM history rows removed). Until the job is handled, a demo reset should do the same, and any demo run that crosses 00:05 local time with the clock past 3 October deactivates those cards again. The reset script now truncates `data_records`, `reports` and `reservations` too.
