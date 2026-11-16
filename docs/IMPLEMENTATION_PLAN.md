# TarjePAFI · Backend + IoT firmware implementation plan

This is the working plan for the Backend API (`tarjepafi-backend`) and the Wokwi reader firmware.
It is written so that any team member (or their coding agent) can pick up where the last one stopped.

- **Progress and handoff notes live in [`PROGRESS.md`](PROGRESS.md).** Read it first, update it last.
- Sources: the report (`tarjepafi-report/README.md`, sections 3.2, 3.4, 4.1, 5.x, 7.1) and the web app
  (`tarjepafi-webapp`, branch `feat/connection-tests`, folder `webapp/`).
- When the report and the web app disagree: the web app's **domain model, resource field names and screens** win
  (they are built and demoed); the report wins for the **API shape** (routes, commands, where rules run) and for
  everything the web app does not implement. Section 3 lists the conflicts.
- **Revision 2 (2026-11-15):** the team decided that the web app is refactored to fit the backend, not the other
  way round. The json-server compatibility layer of revision 1 is dropped (see D2), and the target is a live
  showcase: Wokwi in a browser → deployed backend → deployed web app (section 6).

---

## 1. What exists today

| Piece | State |
|---|---|
| `tarjepafi-report` | Full spec: 28 user stories, 30 technical stories, ADD decisions, tactical design per bounded context. |
| `tarjepafi-webapp` (`develop`) | Angular 21 app, Admin + Teacher views + NFC simulator. **Fat client**: every business rule runs in the browser (`*/application/*.ts`) on top of json-server generic CRUD. `main` is empty; `feat/sprint1-fase7-testing-suite` adds Playwright-BDD tests, including `@integration` features that describe the Fake API contract. |
| `tarjepafi-backend` | Empty repo (no commits). |
| Firmware | Nothing. No repo exists in the organisation for it yet. |

### How the web app talks to its API today (to be replaced context by context, see D2)

- Base URL from `src/environments/environment.ts` → `http://localhost:3000/api/v1`.
- One generic class, `shared/infrastructure/base-api-endpoint.ts`: `GET /{collection}`, `GET /{collection}/{id}`,
  `POST /{collection}` (client supplies the `id`), `PUT /{collection}/{id}` (full replace), `DELETE /{collection}/{id}`.
  The test suite also uses `PATCH` (merge).
- 20 collections + 1 singleton, camelCase names:
  `users, cardHolders, credentials, credentialStatusHistory, credentialPermissions, spaces, accessRecords, workShifts,
  courses, classSchedules, enrollments, sessions, sessionParticipants, attendanceRecords, readers, telemetry,
  incidents, cardTapEvents, dataRecords, reports` and `GET /settings`.
- json-server query dialect actually used (grep `getAll(` in the web app): equality on any top-level field,
  **repeated parameter = OR** (`sessionId=a&sessionId=b`), `_sort`, `_order`, `_limit`, and the suffixes
  `_gte`, `_lte`, `_ne`.
- Date-times are **local campus time without offset**, e.g. `2026-10-07T09:50:00`.
- Login is done client-side: `GET /users?email=` and a plaintext password comparison in the browser.
- All time-dependent rules use a **simulated clock** kept in `localStorage` (starts Wed 2026-10-07 09:50; seed data
  covers the week of 5–10 Oct 2026).

---

## 2. Key decisions

| # | Decision | Why |
|---|---|---|
| D1 | **Modular monolith**, one Spring Boot app, package per bounded context: `iam`, `credentials`, `academicattendance`, `spacefacility`, `iotmonitoring`, `datamanagement`, plus `shared`. | ADD iteration 1 of the report. Section 7.1.2 mentions "one repo per bounded context", but the ADD decision and the single existing repo say monolith. |
| D2 | **Backend-first API; the web app is refactored to fit it** (team decision, revision 2). The backend exposes the report's task-oriented REST API (kebab-case routes, command endpoints, server-side rules). Each context phase ends with a web app branch that rewrites that context's `infrastructure/*api-endpoint*.ts` and `application/*.ts` against the new endpoints. To keep that refactor small, **JSON resource field names stay as in the web app's `*.resource.ts`** and application services keep their public signals and method names, so `presentation/` and `domain/` are untouched. No json-server compatibility layer is built. | Rules are implemented once, in Java. Presentation never injects an API endpoint directly (verified by grep), so the blast radius is `application/` + `infrastructure/`: about 3,250 of 9,200 lines. |
| D3 | **Tap rules must run server-side before the Wokwi reader is useful**, and the web simulator must then call the same pipeline. | A physical reader does not go through the browser. Two rule implementations (TS + Java) would drift. |
| D4 | **JWT auth from the start; passwords never leave the server.** The web app gets a sign-in call and a bearer interceptor in phase 1. No endpoint ever returns `password`. | TS02–TS04, TS-CON05, QAS09. |
| D5 | **Readers speak MQTT to RabbitMQ** (MQTT plugin). The backend only speaks AMQP. | TS-CON09 fixes RabbitMQ; 4.3.4 says "HTTPS/MQTT"; an ESP32 has mature MQTT clients and no practical AMQP 0-9-1 client. |
| D6 | **A `credentials` module exists** even though chapter V has no such bounded context. It owns `card_holders`, `credentials`, status history and permissions, and implements `CardHolderDirectoryPort` (TS29) for the other contexts. | The web app already models it as a context, and US10/11/13/14/15 need an owner. |
| D7 | **REST keeps local date-times (America/Lima, no offset); messages carry UTC instants.** Conversion happens in the ACL (`DevicePayloadAdapter`). | Web app compatibility vs. 7.1.3 messaging convention. |
| D8 | **`CampusClock` port with two modes**: `system` (real time, production) and `simulated` (demo). In simulated mode the clock is settable through `/api/v1/simulation/clock`, and device timestamps are shifted by `simulatedNow − realNow`. | Seed sessions live in October 2026; a Wokwi reader stamps real time. Without this no demo tap ever hits a class in progress. |
| D9 | **String ids generated by the server** (`VARCHAR` primary keys; reader ids such as `RDR-A-301` are chosen by the admin). Seed ids are kept as they are. `event_id` columns are `UNIQUE` (idempotency, AD13). | Seed data and reader ids are strings; firmware sends UUIDs. |
| D10 | **Demo data = the web app's `server/db.json`**, imported by an idempotent seeder under the `demo` profile, with passwords BCrypt-hashed on import. | Same data as the deployed demo and the existing acceptance tests. |
| D12 | **The showcase runs entirely on public infrastructure**: backend, PostgreSQL, RabbitMQ (MQTT reachable from the internet) and web app are deployed; the reader is a saved public project on wokwi.com. Deployment is therefore phase 2, not the last phase, and every later phase is redeployed. | Wokwi's free browser simulator only reaches the internet (`Wokwi-GUEST`), never a laptop. Deploying early removes the biggest demo risk. |
| D13 | **Live views poll** (2 s) while visible: reader detail, session detail, access records, dashboard, simulator log. SSE can replace polling later without changing the screens. | A Wokwi tap must show up in the web app without a manual reload; polling needs no extra infrastructure and works with the bearer interceptor. |
| D11 | Firmware lives in `firmware/reader-esp32/` inside this repo until the organisation creates a dedicated repo. | Nothing to push it to yet; keeps the handoff in one place. |

### Stack

| Concern | Choice |
|---|---|
| Language / framework | Java 25, Spring Boot 4.1.x (4.1.2 was the latest GA on Maven Central on 2026-11-15), Maven |
| Persistence | PostgreSQL 17, Spring Data JPA, Flyway migrations, `jsonb` for `payload`, `summary`, reader `configuration` |
| Messaging | RabbitMQ 4 with `rabbitmq_mqtt` and `rabbitmq_management`, Spring AMQP |
| Security | Spring Security, BCrypt, JWT (HS256, secret from env) |
| Docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Mockito, Testcontainers (PostgreSQL, RabbitMQ), the web app's Playwright-BDD `@integration` features as contract tests |
| Firmware | C++ / Arduino framework, ESP32 DevKit, PlatformIO project that also runs in Wokwi |
| Local infra | Docker Compose |
| Deploy | Azure App Service B1 (backend container from GitHub Container Registry) + Neon free PostgreSQL + RabbitMQ on Azure Container Instances (revision 3, see phase 2) |

### Tools a contributor needs

| Tool | Status on the first dev machine (2026-11-15) |
|---|---|
| JDK 25 | Installed (2026-11-16) and first on `PATH`; `JAVA_HOME` may still point at JDK 24, so set it for Maven. `mvn verify` is green on it. |
| Maven 3.9 | Installed |
| Docker Desktop | Installed, daemon was stopped |
| Node 20.19+ / npm | Installed (web app and contract tests) |
| Wokwi | VS Code extension (needs a free licence) or wokwi.com. See section 6 for the networking caveat. |
| PlatformIO (optional) | Missing; only needed to build the firmware outside wokwi.com |

---

## 3. Report vs. web app conflicts and how each is resolved

| Topic | Report | Web app | Resolution |
|---|---|---|---|
| Routes | `/iot/readers`, `/class-sessions`, `/admin/spaces`, kebab-case | flat camelCase collections | Report routes (D2). Resource field names stay camelCase as in the web app. |
| Attendance model | attendance window `OPEN/CLOSED`, statuses `PRESENT/LATE/REJECTED/JUSTIFIED` | teacher-confirmed model: `PENDING → PRESENT/LATE`, `ABSENT` at close, teacher edits with reason | Web app model. `JUSTIFIED` is added to the enum for TS16. Window endpoints (TS14) map to session `start`/`finish`. |
| Space model | `RESERVATION_REQUIRED`, `RESTRICTED_STAFF_ONLY`, `EVENT_VENUE`, occupancy | `accessPolicy OPEN/RESTRICTED` + `allowedRoles[]`, no occupancy | Web app model, extended with `capacity/currentOccupancy`, `EVENT_VENUE` and `RESERVATION_REQUIRED` when reservations arrive (phase 8). |
| Staff hours | `CampusPresence` (TS30) | `workShifts` | One table `work_shifts`; TS30 endpoint is a query over it. |
| Reports | `POST /reports/weekly` → 202, async consolidation | client creates `GENERATING`, computes summary, `PUT`s `GENERATED` | Report flow, server-side (phase 7). |
| Event ids | UUID | any string | D9. |
| Time zone | UTC ISO 8601 in messages | local, no offset | D7. |
| Bounded contexts | 5 | 6 (adds `credentials`) | D6. |

---

## 4. Target architecture

```
 Wokwi ESP32 reader ──MQTT──▶ RabbitMQ ──AMQP──▶ Spring Boot (modular monolith) ──JDBC──▶ PostgreSQL
        ▲                        │                       ▲
        └──── MQTT display ◀─────┘                       │ REST + JWT
                                                   Angular web app
```

### Messaging topology

| Exchange | Type | Purpose |
|---|---|---|
| `tarjepafi.devices` | topic | Everything readers publish or receive. Set as the MQTT plugin exchange (`mqtt.exchange`), so MQTT topic `tarjepafi/readers/RDR-A-301/tap` arrives with routing key `tarjepafi.readers.RDR-A-301.tap`. |
| `tarjepafi.events` | topic | Integration events between bounded contexts (7.1.3). |
| `tarjepafi.dlx` | topic | Dead letters; every queue has a `<queue>.dlq`. |

| Queue | Binding | Consumer |
|---|---|---|
| `iot.device-events.queue` | `tarjepafi.devices` · `tarjepafi.readers.*.tap`, `.heartbeat`, `.telemetry`, `.error` | `DeviceEventConsumer` → `DevicePayloadAdapter` (TS10, TS11) |
| `attendance.card-tap.queue` | `tarjepafi.events` · `iot.card-tap` | `CardTapEventConsumer` (TS13) |
| `space.card-tap.queue` | `tarjepafi.events` · `iot.card-tap` | `SpaceCardTapEventConsumer` (TS22) |
| `iot.tap-resolved.queue` | `tarjepafi.events` · `*.tap-resolved` | IoT stores outcome + message and publishes the display reply |
| `data.events.queue` | `tarjepafi.events` · `iot.#`, `attendance.#`, `space.#`, `credentials.#` | Data Management ingestion (TS24) |
| `attendance.class-session.queue` | `tarjepafi.events` · `academic.class-session-*` | TS12 |

Who resolves a tap (same rule as `nfc-simulator.service.ts#resolve`):

| Reader's space type | Holder | Resolved by |
|---|---|---|
| `CLASSROOM` | student / teacher | Academic Attendance |
| `CLASSROOM` | staff | Space & Facility |
| `MEETING_ROOM` | teacher with a meeting in progress | Academic Attendance |
| `MEETING_ROOM` | anyone else | Space & Facility (asks `AttendanceFacade.hasMeetingInProgress`) |
| `CAMPUS_ENTRANCE` / `CAMPUS_EXIT` | any | Space & Facility (turnstile + work shifts) |
| anything else | any | Space & Facility (space access) |
| any, card unknown / inactive / no holder | — | IoT rejects directly; Space records the denied `accessRecord` if the reader is not a classroom |

Exactly one context publishes `<context>.tap-resolved` per event, carrying
`{ tapEventId, readerId, outcome, tone, messageKey, params }`.

### Device contract (v1)

Reader → broker, topic `tarjepafi/readers/{readerId}/tap`:

```json
{ "v": 1, "type": "CardTapped", "eventId": "8f0c…uuid", "readerId": "RDR-A-301",
  "cardUid": "04:3D:9B:12:8F", "tappedAt": "2026-11-15T15:04:05Z", "buffered": false }
```

Reader → broker, topic `…/telemetry` (also counts as heartbeat) every `heartbeatInterval` seconds:

```json
{ "v": 1, "type": "TelemetryCaptured", "readerId": "RDR-A-301", "capturedAt": "2026-11-15T15:04:30Z",
  "signalStrength": -48, "uptime": 1234, "firmwareVersion": "2.4.1", "bufferedCount": 0 }
```

Broker → reader, topic `tarjepafi/readers/{readerId}/display`:

```json
{ "eventId": "8f0c…uuid", "tone": "success", "lines": ["Asistencia tomada", "Aplicaciones Web", "10:00-11:30"] }
```

`tone` is `success | info | error`. Text is Spanish (reader language), rendered server-side from the same
`tap.*` keys as `webapp/public/i18n/es.json`, split on ` · `, transliterated to ASCII for the LCD.

---

## 5. Story coverage

Legend for "Web app today": **UI+rules** = implemented client-side; **UI gap** = no screen yet.

| Stories | Topic | Web app today | Phase |
|---|---|---|---|
| US05, US09, US26, US27 | Landing page | separate repo | out of scope |
| TS01–TS04, US08 (identity) | IAM, JWT | client-side login | 1 |
| TS-CON01, TS-CON04, AD15 | Azure deployment | — | 2 (first deploy), redeployed after every phase |
| US10, US11, US13, US14, US15, TS29 | Credentials | UI+rules | 3 |
| TS05–TS09 | Reader management | UI+rules | 4 |
| TS10, TS11, TS-CON09 | Ingestion, heartbeats | simulated in browser | 4 |
| US01, US02, US04, US16, US17, US18, US20–US23, US25, TS12–TS17 | Attendance | UI+rules | 5 |
| US06, US07, US12, US14, US19, US24, TS18, TS19, TS22 (no reservations), TS28, TS30 | Access control, work shifts | UI+rules | 5 |
| TS-CON06 and the live showcase | Wokwi reader, offline buffer | — | 6 |
| TS24–TS27, report US04 | Data records, reports, dashboard | UI+rules | 7 |
| US03, US28, TS20, TS21, TS23, TS-CON07 | Reservations, 10-minute release, e-mail | UI gap (Sprint 2) | 8 |
| QAS01–QAS12 | Quality scenarios | — | 9 |

---

## 6. The showcase and Wokwi notes

### What the demo must show

A presenter opens the saved Wokwi project in a browser, presses play, presents a card to the simulated reader, and
the audience sees the result in the **deployed** web app within a couple of seconds, plus the reply on the
reader's LCD.

```
 wokwi.com (browser) ──MQTT over the internet──▶ RabbitMQ (public MQTT port) ──AMQP──▶ backend (Azure) ──▶ PostgreSQL
        ▲                                             │                                   ▲
        └──────────── display reply (MQTT) ◀──────────┘                                   │ REST + JWT, polled every 2 s
                                                                               deployed web app (browser)
```

Demo script (the acceptance test of milestone M3):

1. Web app, admin: reader `RDR-A-301` shows `ACTIVE` with a recent "last seen" once the Wokwi reader is running.
2. Web app, admin: set the campus clock to Wed 2026-10-07 10:00 (class in progress in A-301).
3. Wokwi: teacher card → LCD "Clase iniciada"; web app session detail shows the session `IN_PROGRESS`.
4. Wokwi: student card → LCD "Asistencia tomada"; the attendance row appears in the teacher's session detail.
5. Wokwi: blocked card → red LED and "Tarjeta bloqueada"; the rejected tap appears in the reader's event log.
6. Wokwi: offline switch on, two taps ("Guardado sin conexion"), switch off → both appear with their original time.
7. Fallback if Wokwi or the venue network fails: the web app's simulator screen sends the same taps through
   `POST /iot/readers/{id}/taps`, which enters the same pipeline.

### Wokwi constraints

- **Network.** The browser simulator joins `Wokwi-GUEST` and reaches **the internet only**. The broker must have a
  public MQTT endpoint (D12). Reaching a laptop needs the paid private gateway and is not part of the plan.
- **Broker (decided 2026-11-16):** RabbitMQ with the MQTT plugin in an Azure Container Instances container, plain MQTT on
  port 1883 (host, port, user and password are firmware constants). ACI has no TLS ingress: TLS on 8883 would need a
  certificate baked into the image and is left to phase 9. A managed RabbitMQ (CloudAMQP free) was the zero-cost
  alternative; it was not chosen because a shared plan probably cannot set `mqtt.exchange`, which this design relies on.
- **Credentials are visible** to anyone who opens a public Wokwi project. Use a dedicated broker user (`reader-demo`)
  whose permissions are limited to the `tarjepafi.devices` exchange and topics `tarjepafi.readers.*`, and rotate
  its password after each showcase. Never put the backend's broker user in the firmware.
- **Project layout.** A wokwi.com project is a flat list of files (`sketch.ino`, extra `.h`/`.cpp` tabs,
  `diagram.json`, `libraries.txt`). Keep `firmware/reader-esp32/` flat so the same files can be pasted or uploaded
  to wokwi.com and also run with the Wokwi VS Code extension (`wokwi.toml`).
- **Part.** Official `board-mfrc522` (https://docs.wokwi.com/parts/board-mfrc522), ESP32 wiring SDA 5, SCK 18,
  MOSI 23, MISO 19. The sample wiring puts RST on GPIO 21, which is the default I2C SDA: move RST to GPIO 4 and
  keep I2C on 21/22 for the LCD. **Verify in phase 6:** how tags are presented in the simulator and which UIDs
  they have. Seed cards use 5-byte UIDs (`04:3D:9B:12:8F`); real MIFARE UIDs are 4 or 7 bytes, so the demo seed
  gets an overlay that registers the Wokwi tag UIDs as credentials of the demo holders (student `U202312966`,
  teacher `D0001`, staff `T0001`, one blocked card, one unregistered).
- **Time.** The reader stamps real UTC time from NTP; the deployed backend runs with the simulated campus clock
  (D8), which maps device time onto campus time. The presenter moves the clock from the web app.
- **Offline.** Wokwi cannot drop Wi-Fi on demand, so the circuit has a slide switch that forces offline mode
  (TS-CON06).
- **Start-up time.** The free simulator compiles in a shared queue. Open the project and press play before the
  presentation starts.
- Circuit: ESP32 DevKit C v4, `board-mfrc522`, LCD 20x4 I2C, green LED, red LED, buzzer, offline slide switch.

---

## 7. Phases

Each phase is one or more `feature/*` branches off `develop`, merged by PR (GitFlow, Conventional Commits, code in
English; see report 7.1.2–7.1.3). A phase is done only when its **Verify** block passes. Tick steps in `PROGRESS.md`.

From phase 1 on, every phase has the same shape:

- **Backend:** Flyway migration for the context's tables (snake_case, plural, `pk_/fk_/uk_/idx_`, no foreign keys
  across contexts) → domain → application services → REST controllers → seed import of that context's collections
  from `src/main/resources/seed/db.json` (copy of the web app's `server/db.json`, D10) → tests.
- **Web app** (branch `feature/backend-<context>` off `feat/connection-tests` in `tarjepafi-webapp`): rewrite the context's
  `infrastructure/*api-endpoint*.ts` and `application/*.ts` against the new endpoints. Keep the public signals and
  method names of each store/service so `presentation/` compiles unchanged. Delete the rule code that moved
  server-side and keep `domain/` only for what forms and labels need. Update or replace the affected e2e features.
- **Redeploy** both (from phase 2 on) and run the phase's Verify block against the deployed environment too.

Rules are ported from the TypeScript file named in each phase, together with its unit tests.

### Phase 0 · Foundations (done, see `PROGRESS.md`)

Maven project, package skeleton, `compose.yaml`, profiles, Flyway baseline, actuator, springdoc, CORS, security
baseline, `CampusClock` and `/api/v1/simulation/clock`. Step 0.1 (first commit and branches) is still open.

### Phase 1 · IAM and web app sign-in

1. `iam`: `users` table, `User` aggregate, `EmailAddress` value object (`@upc.edu.pe` only), statuses
   `ACTIVE/INACTIVE/SUSPENDED`, roles `ADMIN/TEACHER/STUDENT/STAFF`.
2. Shared `ProblemDetail` error handler (`@RestControllerAdvice`): 400 validation, 404, 409, 422 business rule,
   each with a stable `code` the web app can map to an i18n key.
3. TS01 `AdminDataSeeder` (account from env, BCrypt, skip if the e-mail exists). `DemoDataSeeder` (`demo` profile)
   imports `users` from the seed file and BCrypt-hashes the passwords.
4. TS02 `POST /api/v1/auth/sign-in` → 200 `{ token, user }`; 400 invalid e-mail; 401 wrong credentials (no hint
   which field); 403 disabled account (`code` says `INACTIVE` or `SUSPENDED`).
5. TS03 `JwtAuthenticationFilter`, 401 vs 403, method security. TS04 `GET /api/v1/auth/me`.
6. Replace the `open-api` profile switch with real role rules as endpoints appear; keep it only if a test needs it.
7. **Web app** `feature/backend-auth`: `environment.ts` → backend URL; `UsersApiEndpoint.verifyCredentials` →
   `POST auth/sign-in`; token stored with the session in `auth.store.ts`; functional interceptor adding
   `Authorization: Bearer`, signing out on 401; map 403 → `ACCOUNT_DISABLED`, 401 → `INVALID_CREDENTIALS`.
   The other contexts keep pointing at json-server until their phase (two base URLs in `environment.ts`:
   `apiBaseUrl` for migrated contexts, `legacyApiBaseUrl` for the rest; remove the second one in phase 7).

**Verify:** the seven demo users of the web app README behave as documented (admin, teacher, student, staff,
INACTIVE, SUSPENDED, non-institutional e-mail); a request without token gets 401; a teacher token on an
admin-only route gets 403. **Milestone M1: the web app signs in against the real backend.**

### Phase 2 · Demo environment (first deployment)

**Prerequisites a person must provide (an agent cannot):** an Azure subscription (the team has Azure for
Students credit) with `az login` done on the machine, and a decision on where images are pushed (GitHub Container
Registry of the organisation is the default). If these are not ready, **do phase 3 first and come back**: phases
1 and 3 need no cloud. The broker is first exercised in the phase 4 Verify block and is mandatory for phase 6, so
the latest safe moment for this phase is before phase 4 is verified.

**Revision 3 (2026-11-16, team decision): the environment is temporary and cheap.** It lives for the development
period, is stopped between sessions, is switched on for the showcase (about 24 hours) and is then deleted. So the
choices below favour per-hour billing and free tiers over monthly SKUs, and the chosen stack is: **Neon free
PostgreSQL** (same engine as the code; a TiDB free tier was considered and rejected because it is MySQL-compatible and
would mean rewriting migrations, drivers and tests), **RabbitMQ on Azure Container Instances** from an image with the
configuration baked in (`docker/rabbitmq/Dockerfile`; stoppable, billed per second), **App Service B1** for the
backend container (HTTPS included; the B1 plan bills while it exists, even when the app is stopped) and **GitHub
Container Registry** (public packages) for both images. Prices were not verified against Microsoft's page: check the
Azure calculator before creating resources. Azure for Students restricts regions and SKUs per subscription: on
2026-11-16 Central US and Chile Central allowed everything needed, East US blocked PostgreSQL, and several others
blocked small VMs.

1. Multi-stage `Dockerfile`; GitHub Actions workflow (`.github/workflows/ci.yml`): build and test on every pull
   request, images pushed on `develop`/`main`, deploy to App Service on `develop` once `AZURE_WEBAPP_NAME` is set.
2. Resource group `rg-tarjepafi-demo` with the App Service plan and web app, and the ACI broker (ports 5672 and 1883
   public, 15672 not published). Neon project created by a person. Secrets as app settings; HTTPS only; profile `demo`
   on the showcase environment. The exact commands are in `docs/DEPLOYMENT.md`.
3. Broker users: `tarjepafi-backend` (AMQP, administrator, from `RABBITMQ_DEFAULT_USER`) and `reader-demo` (MQTT,
   restricted as in section 6), created by the image's entrypoint **after the backend has declared the
   `tarjepafi.devices` exchange** (RabbitMQ only accepts topic permissions on an existing exchange).
4. Web app: `environment.prod.ts` → deployed API; add the web app's public origin to `CORS_ALLOWED_ORIGINS`.
   Hosting: Azure Static Web Apps (free plan), pending the team's confirmation.
5. Document every resource name, URL and setting (not the secrets) and the teardown in `docs/DEPLOYMENT.md`.

**Verify:** deployed `/actuator/health` is `UP`; sign-in works from the deployed web app; from any machine,
`mosquitto_pub`/`mosquitto_sub` with the `reader-demo` user can publish and read `tarjepafi/readers/test/tap`,
and cannot touch any other exchange.

### Phase 3 · Credentials and catalogues

Port from `credentials.store.ts`, `credential-directory.service.ts`, `enrollment-verification.service.ts` and the
`credentials/domain` entities.

1. Tables `card_holders`, `credentials`, `credential_status_history`, `credential_permissions`, `settings`.
2. `Credential` (`ACTIVE/BLOCKED/LOST/DEACTIVATED`, one `ACTIVE` card per holder, `canBeReactivated`), status
   history, permission exceptions (`ALLOW/DENY` per space).
3. Endpoints under `/api/v1/credentials` and `/api/v1/card-holders`: list and detail (with history and
   permissions), `POST /credentials/{id}/deactivate` (US10), `/link-holder` (US11), `/reactivate` (US13),
   `PUT`/`DELETE /credentials/{id}/permissions/{spaceId}` (US14), `POST /credentials/enrollment-verification`
   (US15, also `@Scheduled` daily after `settings.enrollmentDeadline`), `GET /settings`.
4. `CardHolderDirectoryPort` (TS29) and `resolveAccess(credentialId, spaceId, role, allowedRoles)` as a facade for
   the other contexts. Publish `credentials.*` events (the exchange is declared here, used from phase 4).
5. Read-only catalogues the screens already need: `GET /api/v1/spaces` (table `spaces`, owned by `spacefacility`)
   and `GET /api/v1/courses`, `/class-schedules`, `/enrollments` (owned by `academicattendance`).
6. **Web app** `feature/backend-credentials`: the four credential endpoint classes, `credentials.store.ts`,
   `credential-directory.service.ts`, `enrollment-verification.service.ts`, `space-catalog.service.ts`,
   `academic-catalog.service.ts`.

**Verify:** rows US10, US11, US13, US14, US15 of the traceability table in `webapp/README.md` section 7, done in
the web app against the backend; acceptance features `05` and `06` green.

### Phase 4 · IoT: readers, messaging backbone, ingestion

Port from `readers.store.ts` and `iot-monitoring/domain`.

1. Declare the exchanges, queues, bindings and DLQs of section 4 (`RabbitMqTopology`).
2. Tables `readers`, `telemetry`, `incidents`, `card_tap_events`. `Reader` aggregate
   (`REGISTERED/ACTIVE/DEGRADED/OFFLINE/INACTIVE/MAINTENANCE`, `canProcessTaps()`, `hasLostConnection(now)` = 3
   missed heartbeats), `ConfigurationProfile`, `TelemetrySample`, `Incident`, `CardTapEvent`.
3. TS05–TS09 under `/api/v1/iot/readers/**`: register (duplicate id → 409), update configuration (firmware format
   validation), activate / deactivate / maintenance transitions, list, detail with telemetry, incidents and taps.
4. TS10 `DeviceEventConsumer` + `DevicePayloadAdapter`: validate the payload (malformed → retries → DLQ), reader
   must be registered and able to process taps (otherwise store the event as `REJECTED` + incident), idempotent on
   `eventId`, convert `tappedAt` (D7, D8), persist with outcome `PENDING`, publish `CardTapEvent` on `iot.card-tap`.
5. TS11 heartbeat/telemetry handling (`lastSeenAt`, status), scheduled offline detection → incident +
   `DeviceOfflineDetected`.
6. `POST /api/v1/iot/readers/{readerId}/taps` (ADMIN): injects a tap with `source = SIMULATOR` into the same
   exchange, waits up to 3 s for the resolution, returns the resolved event (200) or the pending one (202).
7. `iot.tap-resolved.queue` consumer: stores outcome, `messageKey`, `messageParams`, `tone`, `displayMessage`;
   publishes the display reply to `tarjepafi.devices`.
8. Until phase 5 exists, a temporary fallback resolver answers every valid tap with `tap.received`.
9. **Web app** `feature/backend-iot`: IoT endpoint classes, `readers.store.ts`, `tap-events.query.ts`,
   `iot-notifications.ts`; polling on reader detail (D13).

**Verify:** `mosquitto_pub` with a v1 tap payload against the **deployed** broker creates one tap event visible in
the deployed web app's reader detail without reloading; publishing it twice still yields one; a malformed payload
lands in the DLQ; an unregistered reader produces an incident; `mosquitto_sub` on `…/display` sees the reply.

### Phase 5 · Tap resolution: attendance and access

Port, test by test, from `nfc-simulator.service.ts#resolve`, `attendance-tap.service.ts`,
`session-closing.service.ts`, `teacher-workspace.service.ts`, `attendance-ledger.service.ts`,
`access-control.service.ts` and their domain entities. Routing table: section 4.

1. `academicattendance`: tables `sessions`, `session_participants`, `attendance_records` (+ catalogues from phase
   3). `Session` (`SCHEDULED/IN_PROGRESS/FINISHED`, `isInProgress`, `isWithinTeacherWindow`, `punctualUntil`,
   `shouldAutoClose` = 15 min after end), `AttendanceRecord` (`PENDING/PRESENT/LATE/ABSENT/JUSTIFIED`).
2. `CardTapEventConsumer` (TS13): student in classroom, teacher 1st/2nd/3rd tap, meeting taps; idempotent on
   `tap_event_id`; publishes `attendance.recorded`, `attendance.late-detected`, `attendance.rejected` and
   `attendance.tap-resolved`.
3. Session auto-close scheduler (reads `CampusClock`); TS12 class-session consumers; teacher endpoints under
   `/api/v1/class-sessions/**`: my sessions, my meetings, detail with roster, confirm pending, edit a record with
   reason, justify (TS14–TS17), my attendance.
4. `spacefacility`: tables `access_records`, `work_shifts`; `SpaceCardTapEventConsumer` (TS22 without
   reservations): maintenance, role + permission exceptions, turnstile shifts; `@Version` optimistic locking (ADD
   iteration 6); endpoints TS18, TS19, TS28, TS30 (`/api/v1/spaces/**`, `/api/v1/access-records`,
   `/api/v1/work-shifts`).
5. Remove the phase 4 fallback resolver.
6. **Web app** `feature/backend-taps`: `nfc-simulator.service.ts` shrinks to `POST /iot/readers/{id}/taps`
   ("Pasar tarjeta" and "Reenviar"); delete `attendance-tap.service.ts`, `access-control.service.ts`,
   `session-closing.service.ts`; `teacher-workspace.service.ts`, `attendance-overview.service.ts`,
   `attendance-ledger.service.ts`, `access-records.query.ts` call the new endpoints; `simulated-clock.service.ts`
   reads and sets `/simulation/clock` instead of `localStorage`; polling on session detail and access records.

**Verify:** the demo walkthrough of `webapp/README.md` section 4 works from the web simulator against the deployed
backend; acceptance features `01`–`04` and `07` green; a duplicate event answers "Evento ya procesado".
**Milestone M2: taps are resolved server-side, end to end.**

### Phase 6 · Wokwi firmware and the live showcase

Can start as soon as phase 4 is deployed (the device contract of section 4 is frozen there); needs phase 5 for
meaningful replies.

1. `firmware/reader-esp32/` (flat, see section 6): `sketch.ino`, modules, `diagram.json`, `libraries.txt`,
   `wokwi.toml`, `config.example.h` (`config.h` git-ignored), `README.md` with the pin map, how to create the
   wokwi.com project from these files, and the demo script.
2. Modules (`.h` + `.cpp`, no `delay()` in the loop, `millis()` scheduling):
   `NfcReader` (MFRC522, UID normalised to upper-case hex with colons, same card ignored for 2 s),
   `NetworkManager` (Wi-Fi + NTP + MQTT reconnect with backoff), `EventPublisher` (JSON payloads, UUID v4 event
   ids), `TapBuffer` (ring buffer of at least 500 taps; oldest dropped when full and counted in telemetry),
   `ReaderDisplay` (LCD, LEDs, buzzer), `Telemetry`.
3. Tap flow: read → immediate "Leyendo..." feedback → publish (QoS 1) or buffer when offline → show the display
   reply, or "Guardado sin conexion" when buffered, or a timeout message after 3 s.
4. On reconnect, flush the buffer in order with `buffered: true` and the original `tappedAt`.
5. Offline slide switch forces the offline path.
6. Seed overlay with the Wokwi tag UIDs (section 6); redeploy.
7. Save the project on wokwi.com with the `reader-demo` broker settings; put its URL in `docs/DEPLOYMENT.md` and in
   the firmware README.
8. Rehearse the demo script of section 6 twice from a machine that has never run the project.

**Verify:** the seven steps of the demo script, on the deployed environment, from the public Wokwi URL.
**Milestone M3: the showcase works.**

### Phase 7 · Data Management

Port from `data-recorder.service.ts`, `dashboard.query.ts`, `reports.store.ts`, `report-calculator.ts`.

1. Tables `data_records`, `reports`. `data.events.queue` consumer (TS24): immutable `DataRecord`, idempotent on
   `event_id`, coherence check of `sourceContext`/`dataType` → `DataRegistrationError`.
2. TS25 query endpoints and dashboard aggregates; TS26 `POST /api/v1/reports/{weekly|monthly|semester}` → 202 +
   async consolidation; TS27 report list and detail.
3. **Web app** `feature/backend-data`: delete `data-recorder.service.ts` and the client-side calculation;
   `dashboard.query.ts` and `reports.store.ts` call the endpoints (report detail polls while `GENERATING`);
   polling on the dashboard. Remove json-server, `server/`, `legacyApiBaseUrl` and `id-generator.ts`.

**Verify:** a weekly report for 5–11 Oct 2026 has the same summary the web app computed client-side (capture it
before deleting the calculator); one data record per fact, no duplicates after replaying a queue; the web app
runs with json-server stopped.

### Phase 8 · Reservations (Sprint 2 scope)

TS20, TS21, TS22 (check-in), TS23 `ReservationToleranceScheduler` every minute, e-mail port with an async SMTP
adapter (TS-CON07), `STUDY_CUBICLE` readers. Needs new web app screens (US03, US28).

**Verify:** a `CONFIRMED` reservation without check-in becomes `FORFEITED` after 10 minutes (simulated clock) and
an e-mail is captured by the local mail catcher; a tap inside the window moves it to `CHECKED_IN`.

### Phase 9 · Hardening and evidence

1. Load test for QAS02 (≥ 200 taps/min) and QAS01 (≤ 1 s to the display reply); consumer concurrency tuning.
2. Rate limiting and lockout on sign-in; review of the role matrix; broker TLS if phase 2 used plain 1883.
3. Swagger and deployment evidence for report sections 7.2.x.

---

## 8. Web app refactor: size and rules

Measured on the web app (non-test TypeScript under `src/app`):

| Layer | Files | Lines | What happens |
|---|---|---|---|
| `presentation/` | 51 | 4,472 | Untouched, except a polling hook in five views (D13). It only injects application services. |
| `domain/` | 23 | 1,485 | Mostly untouched: entities, enums and form validation stay. Rule methods that only the deleted services used can go. |
| `application/` | 27 | 2,444 | Rewritten. Roughly 1,000 lines of rules are deleted (tap resolution, access control, session closing, data recorder, enrollment check, report calculation); the stores keep their signals and call command endpoints. |
| `infrastructure/` | 27 | 806 | Endpoint classes re-pointed to the new routes; resources and assemblers stay (same field names, D2). |

Rules of the refactor:

1. One web app branch per backend phase; the app must work at the end of each (migrated contexts on the backend,
   the rest still on json-server through `legacyApiBaseUrl`).
2. Never change a signal or method name that `presentation/` uses. If a screen must change, say so in the PR.
3. A rule is deleted from the web app only in the same PR that switches it to the server.
4. Backend error `code`s map to the existing i18n keys; do not add user-facing text in the backend except the
   reader display lines.
5. The Playwright `@integration` features describe the json-server contract and are retired; the acceptance
   features (`01`–`07`) are the regression suite and must stay green after each phase.

---

## 9. Risks and open questions

| # | Item | Owner decision needed |
|---|---|---|
| R1 | JDK 25 was missing on the first dev machine. | **Resolved 2026-11-16:** installed; `mvn verify` green on it. Use JDK 25 in the CI image (phase 2). |
| R2 | Public MQTT broker for the showcase. | **Decided 2026-11-16:** RabbitMQ on Azure Container Instances (stopped between sessions), paid with the Azure for Students credit (phase 2, revision 3). |
| R3 | Between phases 1 and 7 the web app talks to two APIs (backend + json-server), so the deployed web app is only fully functional for migrated contexts. | Accepted; the showcase needs phases 1–6. |
| R4 | Wokwi tag UIDs differ from seed UIDs. | Resolved by the seed overlay in phase 6. |
| R5 | The web app's `develop` reverted the test suite; `feat/connection-tests` has it (same tree as `feat/sprint1-fase7-testing-suite`). | **Decided 2026-11-15:** every web app branch of this plan starts from `feat/connection-tests` and is merged back into it. |
| R6 | Firmware has no repository in the organisation. | Create `tarjepafi-iot-firmware` or keep it in this repo (D11). |
| R7 | Broker credentials are readable in a public Wokwi project. | Restricted `reader-demo` user, rotated after each showcase (section 6). |
| R8 | The showcase depends on wokwi.com and the venue network. | Web simulator fallback (demo script step 7) and a recorded run as backup. |
