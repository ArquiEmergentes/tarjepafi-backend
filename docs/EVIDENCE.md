# Evidence of the backend (phase 9.3)

What exists, how it was checked and how to check it again. Everything here comes from the repository, the test suite
or the deployed API of 2026-11-25; nothing needs a password except where it says so.

## 1. What was built

A modular monolith (Spring Boot 4, Java 25, PostgreSQL, RabbitMQ) with one package per bounded context of the report:

| Context | Package | What it owns |
|---|---|---|
| Identity | `iam` | users, JWT sign-in, roles, sign-in lock |
| Credentials | `credentials` | card holders, cards and their status history, permission exceptions, enrollment verification |
| Space and Facility | `spacefacility` | spaces, access records, work shifts, occupancy, reservations (US03, US28) |
| IoT Monitoring | `iotmonitoring` | readers, telemetry, incidents, card tap events, the MQTT ingestion |
| Academic Attendance | `academicattendance` | class sessions, attendance, meetings, teacher routes |
| Data Management | `datamanagement` | immutable data records, dashboard, reports |
| Shared | `shared` | campus clock, integration events, messaging topology, security, errors |

A bounded context never reads another's tables: they talk through integration events on the `tarjepafi.events`
exchange (`iot.card-tap`, `attendance.recorded`, `space.access-granted`, ...) or through a facade or port of the owner
(`CardHolderDirectoryPort`, `AttendanceFacade`). Readers (ESP32) speak MQTT to RabbitMQ with a versioned device contract
(`docs/IMPLEMENTATION_PLAN.md`, section 4); the firmware is in `firmware/reader-esp32/` and runs on wokwi.com.

## 2. Phases and where each one was verified

| Phase | Scope | Verified on the deployed API | Checks |
|---|---|---|---|
| 0 | project, Flyway, messaging backbone | local (containers) | `MessagingBackboneTests` |
| 1 | IAM: sign-in, tokens, roles | yes | `AuthApiTests` (13) |
| 2 | Azure deployment (App Service, ACI broker, Neon) | yes | `docs/DEPLOYMENT.md` |
| 3 | Credentials and catalogues | yes | `CredentialsApiTests` (30), `CredentialsDomainTest` (9) |
| 4 | Readers, MQTT ingestion, tap events, simulated taps | yes | `IotApiTests` (20), `IotDomainTest` (16), `DevicePayloadAdapterTest` (8) |
| 5 | Tap resolution: attendance and space access, work shifts | yes (17 checks, milestone M2) | `TapResolutionApiTests` (6), `AttendanceDomainTest` (10) |
| 6 | Wokwi reader firmware, tag overlay, `buffered` flag | demo script steps 1 to 7 by hand | `firmware/reader-esp32/README.md` |
| 7 | Data records, dashboard, reports | yes (11 checks, the weekly report equals the old web app's calculation) | `DataManagementApiTests` (10), `ReportCalculatorTest` (9), `DataFactsTest` (9) |
| 8 | Reservations, release after 10 minutes, e-mail | yes (20 checks) | `ReservationApiTests` (8, with a real mail catcher), `ReservationTest` (12) |
| 9 | Sign-in lock, role matrix, evidence | yes (12 checks) | `SignInThrottleTest` (9), `RoleMatrixApiTests` (2) |

The day-by-day record, with what failed and how it was fixed, is the log of `docs/PROGRESS.md`.

## 3. Tests

199 tests, 195 of them run on every build (`mvn verify`, also in CI). The integration tests use Testcontainers (real
PostgreSQL 17 and RabbitMQ 4, and Mailpit for the e-mail) and are skipped when Docker is not running. The 4 load tests
(`TapLoadTests`) run only on request, see `docs/PERFORMANCE.md`.

| Area | Tests |
|---|---|
| IAM (sign-in, e-mail, lock) | 31 |
| Credentials | 39 |
| IoT monitoring | 47 |
| Academic attendance | 16 |
| Space and Facility (reservations) | 22 |
| Data management | 28 |
| Shared (clock, messaging, role matrix, load) | 15 |
| Application context | 1 |

## 4. Quality scenarios

| Scenario | How it is covered | Evidence |
|---|---|---|
| QAS05 invalid event or unregistered card is rejected without affecting the others | a malformed payload ends in the dead letter queue after 3 tries; an unknown card is answered `tap.cardNotRegistered` | `IotApiTests` |
| QAS06 no duplicate for a repeated event | a tap and a data fact are recorded once per event id; the reader is told `tap.duplicateEvent` | `IotApiTests`, `DataManagementApiTests` (replays) |
| QAS09 protected endpoints | every route is called with no token and with each role | `RoleMatrixApiTests`, `docs/ROLE_MATRIX.md` |
| QAS10 deactivated card is refused at the first attempt | a blocked card is answered `tap.cardInactive` and leaves a denied access record (every status other than ACTIVE takes the same path) | `IotApiTests`, `TapResolutionApiTests` |
| QAS04 events survive a short outage of the backend | queues are durable and the messages wait for the consumers | by design of the topology; not load tested |
| QAS01, QAS02, QAS03, QAS07, QAS08, QAS11, QAS12 | not evaluated in the course; QAS01 and QAS02 were measured anyway | `docs/PERFORMANCE.md` |

## 5. Deployment

| Piece | Resource |
|---|---|
| API | Azure App Service B1 (Linux, container from GitHub Container Registry), Chile Central: `https://tarjepafi-api-pafi7k2.azurewebsites.net` |
| Broker | RabbitMQ with the MQTT plugin in Azure Container Instances, plain MQTT on port 1883 for the readers (user `reader-demo`, restricted and rotated after each showcase) |
| Database | Neon PostgreSQL (free plan, `us-east-2`) |
| Images and CI | GitHub Actions: test, build and push the image on every merge to `develop`; the app is restarted by hand to pick it up |
| Cost control | everything is tagged `delete-after=2026-12-31`; the broker can be stopped between sessions |

Settings and procedure: `docs/DEPLOYMENT.md`. Swagger UI of the API: `/swagger-ui.html`; the OpenAPI document: `/v3/api-docs`.
The request examples for every context are in `docs/http/*.http`, and the written contract is `docs/API.md`.

## 6. The API contract

Every context has its section in `docs/API.md` (conventions, errors with their stable `code`, polling with cursors, the
changelog of additive changes) and a requests file. The list below is generated from the OpenAPI document of the deployed
API with `python scripts/evidence/endpoint_inventory.py <api host>`.

66 operations in 13 groups

| Group | Method | Path | Summary | Documented responses |
|---|---|---|---|---|
| Academic catalogues | GET | `/api/v1/class-schedules` | List the weekly class slots | 200 |
| Academic catalogues | GET | `/api/v1/courses` | List the courses ordered by code | 200 |
| Academic catalogues | GET | `/api/v1/enrollments` | List the enrollments | 200 |
| Authentication | GET | `/api/v1/auth/me` | The user the bearer token belongs to | 200 |
| Authentication | POST | `/api/v1/auth/sign-in` | Sign in with an institutional e-mail and password | 200 |
| Card holders | GET | `/api/v1/card-holders` | List or search the card holders by name | 200 |
| Card holders | GET | `/api/v1/card-holders/{id}` | One card holder | 200 |
| Class sessions (teacher) | GET | `/api/v1/class-sessions/attendance/mine` | My own marks in my classes (arrival, departure, duration), newest first | 200 |
| Class sessions (teacher) | GET | `/api/v1/class-sessions/meetings/mine` | Meetings and trainings I am invited to, newest first, with my mark | 200 |
| Class sessions (teacher) | GET | `/api/v1/class-sessions/mine` | My classes of a day, with attendance counts | 200 |
| Class sessions (teacher) | GET | `/api/v1/class-sessions/{id}` | One of my classes with its roster | 200 |
| Class sessions (teacher) | GET | `/api/v1/class-sessions/{id}/attendance` | Attendance records of a session, newest first (poll with `after`) | 200 |
| Class sessions (teacher) | PUT | `/api/v1/class-sessions/{id}/attendance/{studentId}` | Set a student's status with a reason (TS15) | 200 |
| Class sessions (teacher) | POST | `/api/v1/class-sessions/{id}/attendance/{studentId}/justify` | Justify an absence (TS16) | 200 |
| Class sessions (teacher) | POST | `/api/v1/class-sessions/{id}/confirm-pending` | Confirm the PENDING marks of a session (TS14) | 200 |
| Credentials | GET | `/api/v1/credentials` | List the cards, newest first, each with its holder | 200 |
| Credentials | POST | `/api/v1/credentials/enrollment-verification` | Run the enrollment verification now (US15) | 200 |
| Credentials | GET | `/api/v1/credentials/enrollment-verification/preview` | What the enrollment verification would do now (US15); changes nothing | 200 |
| Credentials | GET | `/api/v1/credentials/{id}` | One card | 200 |
| Credentials | POST | `/api/v1/credentials/{id}/block` | Block an active card (US10) | 200 |
| Credentials | POST | `/api/v1/credentials/{id}/deactivate` | Deactivate a card that is ACTIVE, BLOCKED or LOST (US10) | 200 |
| Credentials | GET | `/api/v1/credentials/{id}/history` | Status history of a card, newest first | 200 |
| Credentials | POST | `/api/v1/credentials/{id}/link-holder` | Link the card to a holder, or move it to another one (US11) | 200 |
| Credentials | GET | `/api/v1/credentials/{id}/permissions` | Access exceptions of a card (US14) | 200 |
| Credentials | PUT | `/api/v1/credentials/{id}/permissions/{spaceId}` | Create or replace the card's exception for a space (US14) | 200 |
| Credentials | DELETE | `/api/v1/credentials/{id}/permissions/{spaceId}` | Remove the card's exception for a space (US14) | 204 |
| Credentials | POST | `/api/v1/credentials/{id}/reactivate` | Reactivate a BLOCKED, LOST or DEACTIVATED card (US13) | 200 |
| Credentials | POST | `/api/v1/credentials/{id}/report-lost` | Report an active card as lost (US10) | 200 |
| Data management (admin) | GET | `/api/v1/dashboard` | Figures of one day from the recorded facts | 200 |
| Data management (admin) | GET | `/api/v1/data-records` | Recorded facts, newest first (poll with `after`) | 200 |
| Data management (admin) | GET | `/api/v1/reports` | Reports (TS27) | 200 |
| Data management (admin) | GET | `/api/v1/reports/{id}` | A report with its figures per day (TS27) | 200 |
| Data management (admin) | POST | `/api/v1/reports/{type}` | Request a weekly, monthly or semester report (TS26) | 202 |
| IoT events | GET | `/api/v1/iot/incidents` | Incidents of every reader, newest first | 200 |
| IoT events | POST | `/api/v1/iot/incidents/{id}/resolve` | Mark an incident as resolved | 200 |
| IoT events | GET | `/api/v1/iot/tap-events` | Tap events of every reader, newest first (poll with `after`) | 200 |
| IoT events | GET | `/api/v1/iot/tap-events/{id}` | One tap event | 200 |
| IoT readers | GET | `/api/v1/iot/readers` | List the readers ordered by id | 200 |
| IoT readers | POST | `/api/v1/iot/readers` | Register a reader (TS05) | 201 |
| IoT readers | GET | `/api/v1/iot/readers/{id}` | A reader with its last 20 telemetry samples, its incidents and its last 20 taps | 200 |
| IoT readers | POST | `/api/v1/iot/readers/{id}/activate` | Activate a REGISTERED or INACTIVE reader (TS07) | 200 |
| IoT readers | PUT | `/api/v1/iot/readers/{id}/configuration` | Replace the reader configuration (TS06) | 200 |
| IoT readers | POST | `/api/v1/iot/readers/{id}/deactivate` | Deactivate an ACTIVE, DEGRADED or OFFLINE reader (TS08) | 200 |
| IoT readers | POST | `/api/v1/iot/readers/{id}/end-maintenance` | End the maintenance: the reader becomes ACTIVE (TS09) | 200 |
| IoT readers | GET | `/api/v1/iot/readers/{id}/incidents` | Incidents of a reader, newest first | 200 |
| IoT readers | POST | `/api/v1/iot/readers/{id}/start-maintenance` | Put a reader in maintenance (TS09) | 200 |
| IoT readers | GET | `/api/v1/iot/readers/{id}/tap-events` | Tap events of a reader, newest first (poll with `after`) | 200 |
| IoT readers | POST | `/api/v1/iot/readers/{id}/taps` | Tap a card on a reader as the simulator would (plan step 6) | 200 |
| IoT readers | GET | `/api/v1/iot/readers/{id}/telemetry` | Telemetry samples of a reader, newest first | 200 |
| Reservations (admin) | GET | `/api/v1/reservations/active` | Reservations in progress (TS21) | 200 |
| Reservations (admin) | GET | `/api/v1/reservations/{reservationId}` | One reservation (TS21) | 200 |
| Reservations (admin) | POST | `/api/v1/reservations/{reservationId}/cancel` | Cancel a reservation (TS20) | 200 |
| Reservations (admin) | GET | `/api/v1/spaces/{spaceId}/reservations` | Reservations of a space between `from` and `to` (TS21) | 200 |
| Reservations (admin) | POST | `/api/v1/spaces/{spaceId}/reservations` | Reserve a space for a card holder (TS20) | 201 |
| Settings | GET | `/api/v1/settings` | Campus settings: term, enrollment deadline and default attendance tolerance | 200 |
| Space access (admin) | GET | `/api/v1/access-records` | Access attempts, newest first (poll with `after`) | 200 |
| Space access (admin) | GET | `/api/v1/card-holders/{cardHolderId}/campus-presences` | Stays on campus of a holder between `from` and `to` (TS30) | 200 |
| Space access (admin) | POST | `/api/v1/spaces` | Register a space (TS18) | 201 |
| Space access (admin) | PATCH | `/api/v1/spaces/{id}/access-policy` | Change the access policy and roles of a space (TS28) | 200 |
| Space access (admin) | GET | `/api/v1/spaces/{id}/occupancy` | Capacity and current occupancy of a space (TS19) | 200 |
| Space access (admin) | PATCH | `/api/v1/spaces/{id}/status` | Set a space AVAILABLE or MAINTENANCE (TS18) | 200 |
| Space access (admin) | GET | `/api/v1/work-shifts` | Work shifts of the staff, newest first | 200 |
| Spaces | GET | `/api/v1/spaces` | List the campus spaces ordered by code | 200 |
| Spaces | GET | `/api/v1/spaces/{id}` | One space | 200 |
| simulation-clock-controller | GET | `/api/v1/simulation/clock` |  | 200 |
| simulation-clock-controller | PUT | `/api/v1/simulation/clock` |  | 200 |

## 7. Decisions and limits worth knowing

- The deployed demo uses the **simulated campus clock** (starts on 2026-10-07 09:50, moved from `PUT /simulation/clock`),
  because the demo data are those of the old web app. Time rules read it, never the system clock.
- Plain MQTT (port 1883) is used between the readers and the broker. It was decided to keep it: the broker user is
  restricted to the devices exchange, the Wokwi project is public by nature and the password is rotated after each showcase.
- The database sits in `us-east-2` and the app in Chile Central, so each of the ~22 SQL statements of a tap costs a
  long round trip; locally a tap is answered in 28 ms. See `docs/PERFORMANCE.md`.
- The daily enrollment verification is off in the demo profile (it deactivated cards when it ran with the demo clock past
  the deadline); the administrator runs it by hand.
- The sign-in lock and the per-address limit live in memory: they fit a single instance.
- Swagger carries an example for every field of every request and response model (checked by `OpenApiDocumentationTests`, so a new
  field without one fails the build), a `Problem` response on every route and a description of how to sign in and authorize.

## 8. How to check it again

```bash
docker compose up -d                  # PostgreSQL, RabbitMQ, mail catcher
mvn verify                            # 199 tests, 195 run (4 load tests are on request)
mvn spring-boot:run -Dspring-boot.run.profiles=dev,demo
mvn test -Dtest=TapLoadTests -Dtarjepafi.load=true     # QAS01 and QAS02 locally, results in target/load-test-results.md
```

The demo script (plan, section 6) is run from the public Wokwi project against the deployed API; its seven steps and the
reads that prove them are in `docs/http/demo.http` and `firmware/reader-esp32/README.md`.
