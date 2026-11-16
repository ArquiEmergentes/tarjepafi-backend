# Progress and handoff log

How to use this file:

1. Read "Current state" and "Next step" before touching anything.
2. Work on the next unticked step of [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) section 7.
3. Before you stop, tick what is **verified** (not merely written), rewrite "Current state" and "Next step",
   and add a dated line to the log with anything the next person could not guess from the code.

## Current state

- Date: 2026-11-15
- Phase 0 is written and verified, but **nothing is committed**: the repo still has no commits and no `develop`
  branch (step 0.1). All files are untracked in the working tree.
- The plan is at **revision 2**: the team decided the web app is refactored to fit the backend, so the json-server
  compatibility layer was dropped and the phases were renumbered (plan D2, D12, D13 and section 7). The goal is a
  live showcase: public Wokwi project → deployed backend → deployed web app (plan section 6).
- **Phase 1 backend (steps 1.1 to 1.6) is written and verified**: `users` table, sign-in, JWT, `GET /auth/me`,
  admin and demo user seeders, problem-details error handler with `code`. `mvn -Djava.version=24 verify` runs 29
  tests green (12 of them hit the real API on Testcontainers).
- Step 1.7 (web app sign-in adapter) is **written** on local branch `feature/backend-auth` (off
  `feat/connection-tests`, uncommitted): `ng build` and the 49 unit tests pass, and the backend side of the
  contract was exercised with curl (see log). **Not yet checked in a browser**: nobody has signed in through the
  Angular login screen against the backend, so 1.7 stays unticked.
- Azure: the team has Azure for Students credit; no resource exists yet. Phase 2 lists what a person must do
  first (`az login`). If that is not done when you get there, do phase 3 and come back.
- Phases 2 to 9 are not started. There is no firmware yet. Nothing is deployed.

## Next step

1. Step 0.1: initial commit on `main`, create `develop`, put the work on a feature branch, open a PR. (Needs the
   person to ask for commits, see `AGENTS.md`.)
2. Finish step 1.7 by checking it in a browser: start `docker compose up -d --wait`, the backend
   (`PORT=8081 mvn -Djava.version=24 spring-boot:run -Dspring-boot.run.profiles=dev,demo`), json-server
   (`npm run api` in `tarjepafi-webapp/webapp`) and `npm start`; set `apiBaseUrl` to `http://localhost:8081/api/v1`
   if you keep 8081. Sign in as each demo user, reload the page (session restore + `auth/me`), and check that a
   401 signs out. Then fix the e2e suite (see log) and commit the web app branch when the person asks.
3. Then tick the Phase 1 Verify block (M1), then Phase 2 (needs the team's Azure subscription, R2).

## Checklist

### Phase 0 · Foundations
- [ ] 0.1 Initial commit on `main`, `develop` created, work on `feature/project-scaffold`
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
- [ ] 1.7 Web app `feature/backend-auth`
- [ ] Verify block passed (milestone M1)

### Phase 2 · Demo environment (first deployment)
- [ ] 2.1 Dockerfile and CI
- [ ] 2.2 Azure resources (App Service, PostgreSQL, broker)
- [ ] 2.3 Broker users (`tarjepafi-backend`, restricted `reader-demo`)
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
- 2026-11-15 · Team decisions: web app base branch is `feat/connection-tests` (identical tree to
  `feat/sprint1-fase7-testing-suite`; `develop` reverted the test suite). `src/main/resources/seed/db.json` is
  byte-identical to that branch's `server/db.json`. The broker will be RabbitMQ on Azure (student credit).
  Planning was done in one chat; implementation continues in new sessions, so this file is the only memory.
