# Progress and handoff log

How to use this file:

1. Read "Current state" and "Next step" before touching anything.
2. Work on the next unticked step of [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) section 7.
3. Before you stop, tick what is **verified** (not merely written), rewrite "Current state" and "Next step",
   and add a dated line to the log with anything the next person could not guess from the code.

## Current state

- Date: 2026-11-16
- **Phase 1 is done (milestone M1)**: the web app signs in against the real backend (see the log).
- **Phase 2 is in progress; nothing exists in Azure yet.** Work is on `feature/demo-environment` (off `develop`,
  uncommitted). Decisions of 2026-11-16 (plan revision 3): Neon free PostgreSQL, RabbitMQ on Azure Container Instances,
  backend on App Service B1, images on GitHub Container Registry, everything temporary and deleted after the
  showcase. Written and verified **locally**: `Dockerfile` (image runs, health UP, sign-in 200, non-root, about 300 MiB),
  `docker/rabbitmq/` image with the restricted `reader-demo` user (permissions tested with real MQTT clients),
  Hikari settings for a serverless database, `mvn verify` (29 tests) on JDK 25. Written but **not run anywhere**:
  `.github/workflows/ci.yml` (YAML parses, never executed on GitHub) and the commands in `docs/DEPLOYMENT.md`.
- Azure: logged in (`az login`, Azure for Students, 2026-11-16); no resource created; `Microsoft.ContainerInstance`
  is not registered yet. Region: Chile Central (decided 2026-11-16); the Neon project exists; the web app goes on Azure Static Web Apps.
  ACI lists Chile Central; the GHCR packages are public (anonymous pull 200).
  Verified locally on 2026-11-16: the published images against the real Neon project (Flyway 2 migrations, health UP,
  sign-in 200/401, sign-in after >5 min idle 1.2 s). Still undeployed on Azure.
- Git: `main` and `develop` exist. The web app's step 1.7 is commit `6b6add6` on `feat/sprint1-fase7-testing-suite`
  (pushed); the local branch `feature/backend-auth` points at it.
- Build: JDK 25 is installed and `mvn verify` is green on it, but `JAVA_HOME` still points at JDK 24: set it to
  `C:\Program Files\Java\jdk-25.0.5.0.3` for Maven.
- Phases 3 to 9 are not started. There is no firmware yet. Nothing is deployed.

## Next step

1. A person must: make the two GHCR packages public (CI already ran green on GitHub; see "What a person must do" in
   `docs/DEPLOYMENT.md`). Region and Neon are settled.
2. Create the Azure resources in the order of `docs/DEPLOYMENT.md` (resource group, App Service plan and web app,
   ACI broker), asking the person before each paid resource. Then run the phase 2 Verify block against the deployed
   environment, tick 2.1 to 2.3 and 2.5, and fill the real names into `DEPLOYMENT.md`.
3. Step 2.4 (web app production config, Static Web Apps, CORS) needs the web app repository: branch off
   `feat/connection-tests`.
4. If the cloud steps are blocked, phase 3 needs no cloud and can be done first (come back before phase 4 is verified).

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
- [x] 1.7 Web app `feature/backend-auth`
- [x] Verify block passed (milestone M1, 2026-11-16)

### Phase 2 · Demo environment (first deployment)
- [ ] 2.1 Dockerfile and CI (Dockerfile verified locally; workflow not yet run on GitHub)
- [ ] 2.2 Azure resources (App Service, PostgreSQL, broker)
- [ ] 2.3 Broker users (`tarjepafi-backend`, restricted `reader-demo`) (verified locally on the image; not yet on ACI)
- [ ] 2.4 Web app production config and CORS
- [ ] 2.5 `docs/DEPLOYMENT.md`
- [ ] Verify block passed

### Phase 3 · Credentials and catalogues
- [ ] 3.1 Tables
- [ ] 3.2 Domain
- [ ] 3.3 Endpoints (US10, US11, US13, US14, US15)
- [ ] 3.4 `CardHolderDirectoryPort` (TS29), events
- [ ] 3.5 Catalogue read endpoints (spaces, courses, schedules, enrollments)
- [ ] 3.6 Web app `feature/backend-credentials`
- [ ] Verify block passed

### Phase 4 · IoT: readers, messaging backbone, ingestion
- [ ] 4.1 RabbitMQ topology
- [ ] 4.2 Tables and domain
- [ ] 4.3 Reader endpoints (TS05–TS09)
- [ ] 4.4 `DeviceEventConsumer` + `DevicePayloadAdapter` (TS10)
- [ ] 4.5 Heartbeats and offline detection (TS11)
- [ ] 4.6 `POST /iot/readers/{id}/taps`
- [ ] 4.7 Tap-resolved consumer and display reply
- [ ] 4.8 Temporary fallback resolver
- [ ] 4.9 Web app `feature/backend-iot`
- [ ] Verify block passed

### Phase 5 · Tap resolution: attendance and access
- [ ] 5.1 Attendance tables and domain
- [ ] 5.2 `CardTapEventConsumer` (TS13)
- [ ] 5.3 Auto-close, teacher endpoints, TS12, TS14–TS17
- [ ] 5.4 Space & Facility consumer and endpoints (TS18, TS19, TS22, TS28, TS30)
- [ ] 5.5 Remove fallback resolver
- [ ] 5.6 Web app `feature/backend-taps`
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
- [ ] Verify block passed (milestone M3)

### Phase 7 · Data Management
- [ ] 7.1 Event ingestion (TS24)
- [ ] 7.2 Query, dashboard and report endpoints (TS25–TS27)
- [ ] 7.3 Web app `feature/backend-data`, json-server removed
- [ ] Verify block passed

### Phase 8 · Reservations
- [ ] TS20, TS21, TS22 check-in, TS23, e-mail adapter, web app screens
- [ ] Verify block passed

### Phase 9 · Hardening and evidence
- [ ] 9.1 Load tests (QAS01, QAS02)
- [ ] 9.2 Sign-in hardening, role review, broker TLS
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
