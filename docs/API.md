# TarjePAFI API · frontend guide

Contract for the person building the frontend. It grows one context per phase; a phase's section is **frozen** once it is
verified on the deployed API (plan D14): later changes are additive, or announced in the changelog at the end.

## Where

| | |
|---|---|
| Base URL (deployed demo) | `https://tarjepafi-api-pafi7k2.azurewebsites.net/api/v1` |
| Swagger UI | `https://tarjepafi-api-pafi7k2.azurewebsites.net/swagger-ui.html` |
| OpenAPI JSON | `https://tarjepafi-api-pafi7k2.azurewebsites.net/v3/api-docs` |
| Health | `https://tarjepafi-api-pafi7k2.azurewebsites.net/actuator/health` |
| Local | `http://localhost:8080/api/v1` (see `README.md`, profile `dev,demo`) |

The demo environment is temporary: it is stopped between work sessions (the first request after a stop can take about a
minute) and deleted after the showcase. The first request after the database sat idle can add a second or two.

**Frontend dev server port: run it on `4781`** (`http://localhost:4781`, e.g. `ng serve --port 4781`). That origin is allowed by CORS,
locally and on the deployed demo (`http://localhost:4200` is still accepted but do not rely on it). The browser origins listed in the
`CORS_ALLOWED_ORIGINS` setting are accepted.
Tell the backend owner your dev and deployed origins.

## Conventions

- JSON, `camelCase` field names, string ids, dates and times as local date-times without offset
  (`2026-10-07T09:50:00`, campus time, zone `America/Lima`). Never send an offset.
- Authentication: `Authorization: Bearer <token>` on every call except sign-in. The token is a JWT valid for 8 hours.
  There is no refresh: sign in again.
- Errors are RFC 9457 problem documents with an extra `code` you can map to your own texts:

  ```json
  { "title": "Unauthorized", "status": 401, "detail": "Invalid e-mail or password",
    "instance": "/api/v1/auth/sign-in", "code": "INVALID_CREDENTIALS" }
  ```

  Validation failures (`400`, `VALIDATION_FAILED`) add `fields: [{ "field": "email", "message": "must not be blank" }]`.
  A missing, expired or tampered token gives `401` with an empty body (no `code`): treat any `401` outside sign-in as
  "sign in again".

| Status | `code` | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Body is missing a field or is malformed; see `fields`. |
| 400 | `INVALID_EMAIL` | The e-mail is not an institutional address (`@upc.edu.pe`). |
| 401 | `INVALID_CREDENTIALS` | Unknown e-mail or wrong password (same answer on purpose). |
| 401 | `INVALID_TOKEN` | The token is valid but its user no longer exists or is disabled. |
| 403 | `ACCOUNT_DISABLED` | Correct credentials, but the account is `INACTIVE` or `SUSPENDED`. |
| 403 | none | Authenticated, but the role may not call this route. |

## IAM (phase 1, deployed)

Roles: `ADMIN`, `TEACHER`, `STAFF`, `STUDENT`. Statuses: `ACTIVE`, `INACTIVE`, `SUSPENDED`.

### `POST /auth/sign-in` (public)

```json
{ "email": "docente@upc.edu.pe", "password": "Docente123*" }
```

`200`:

```json
{ "token": "eyJhbGciOi…",
  "user": { "id": "usr-002", "email": "docente@upc.edu.pe", "role": "TEACHER", "status": "ACTIVE",
            "cardHolderId": null, "fullName": "…", "createdAt": "2026-…", "updatedAt": "2026-…" } }
```

Errors: `400 VALIDATION_FAILED`, `400 INVALID_EMAIL`, `401 INVALID_CREDENTIALS`, `403 ACCOUNT_DISABLED`.
The password is never returned. Do not store it; store `token` and `user`.

### `GET /auth/me`

Returns the `user` object of the bearer token (use it to restore a session on reload). `401` if the token is not valid
(`INVALID_TOKEN` when the user was disabled after signing in).

### `GET /simulation/clock` and `PUT /simulation/clock` (demo only)

The campus clock the rules read. `GET` returns `{ "now": "2026-10-07T09:50:00", "simulated": true }`. `PUT` with
`{ "now": "…" }` moves it and needs the `ADMIN` role; it answers `409` when the clock follows real time. The demo
environment runs the simulated clock, starting at `2026-10-07T09:50:00` and not ticking.

## Credentials (phase 3)

Cards (`credentials`), the people they belong to (`card-holders`), the campus `settings`, and the catalogues the
screens need (`spaces`, `courses`, `class-schedules`, `enrollments`). Field names follow the old web app's
`*.resource.ts`. **Every `/credentials` and `/card-holders` route needs the `ADMIN` role** (a teacher gets `403`);
`/settings` and the catalogues accept any signed-in user. Lists are not paged (at most a few hundred rows).

### Card holders

`CardHolder`: `{ id, code, fullName, email, role, program, enrollmentStatus }` with `role` one of `STUDENT`,
`TEACHER`, `STAFF`, and `enrollmentStatus` `COMPLETED` or `PENDING` (null unless the holder is a student).

| Route | Notes |
|---|---|
| `GET /card-holders` | Filters `role`, `q` (text in name or code), `code` (exact, ignoring case: answers a list with 0 or 1 holder). Ordered by name. |
| `GET /card-holders/{id}` | `404 HOLDER_NOT_FOUND`. |

### Cards

`Credential`: `{ id, cardUid, cardHolderId, status, issuedAt, statusChangedAt, lastUsedAt, availableActions, holder }`.
`status`: `ACTIVE`, `BLOCKED`, `LOST`, `DEACTIVATED`. `cardHolderId` and `holder` (a `CardHolder`) are `null` while
the card is in stock. `availableActions` is what the current status allows, from `BLOCK`, `REPORT_LOST`, `DEACTIVATE`,
`REACTIVATE`: use it to enable or disable your buttons. `cardUid` looks like `04:3D:9B:12:8F`.

State machine: `BLOCK` and `REPORT_LOST` need `ACTIVE`; `DEACTIVATE` needs `ACTIVE`, `BLOCKED` or `LOST`; `REACTIVATE`
needs `BLOCKED`, `LOST` or `DEACTIVATED`. A holder may have only one `ACTIVE` card.

| Route | Story | Notes |
|---|---|---|
| `GET /credentials` | | Newest first, each with its `holder`. Filters `status`, `cardHolderId`, `q` (text in UID, holder name or code). |
| `GET /credentials/{id}` | | `404 CREDENTIAL_NOT_FOUND`. |
| `GET /credentials/{id}/history` | | Newest first: `{ id, credentialId, fromStatus, toStatus, reason, changedBy, changedAt, note }`. `reason`: `WITHDRAWAL`, `REENROLLMENT`, `ENROLLMENT_NOT_COMPLETED`, `LOST`, `MANUAL`. `changedBy` is a user id or `SYSTEM`. Linking a holder adds an entry whose `fromStatus` equals `toStatus`. |
| `POST /credentials/{id}/block` | US10 | Body `{ "note": "..." }`, **required**: `400 NOTE_REQUIRED`. |
| `POST /credentials/{id}/report-lost` | US10 | Optional body `{ "note" }`. History reason `LOST`. |
| `POST /credentials/{id}/deactivate` | US10 | Optional body `{ "note" }`. |
| `POST /credentials/{id}/reactivate` | US13 | Optional body `{ "note" }`. History reason `REENROLLMENT` when a student's `DEACTIVATED` card comes back, else `MANUAL`. |
| `POST /credentials/{id}/link-holder` | US11 | Body `{ "holderCode": "U202312966" }` (ignores case). Links a stock card or moves it to another holder. |

The five `POST` actions answer `200` with the updated `Credential`. Errors:

| Status | `code` | When |
|---|---|---|
| 400 | `NOTE_REQUIRED` | `block` without a note. |
| 400 | `VALIDATION_FAILED` | `link-holder` without `holderCode`. |
| 404 | `CREDENTIAL_NOT_FOUND`, `HOLDER_NOT_FOUND` | Unknown id or holder code. |
| 409 | `ACTION_NOT_ALLOWED` | The status does not allow the action. |
| 409 | `HOLDER_HAS_ACTIVE_CARD` | `reactivate`, or `link-holder` of an active card, would give the holder a second active card. |
| 409 | `ALREADY_LINKED` | `link-holder` to the holder the card already has. |
| 422 | `ENROLLMENT_NOT_COMPLETED` | `reactivate` for a student whose enrollment is `PENDING`. |

### Permission exceptions (US14)

A card can have its own rule for a space; it beats the roles the space admits (`allowedRoles`).
`Permission`: `{ id, credentialId, spaceId, effect }`, `effect` `ALLOW` or `DENY`.

| Route | Notes |
|---|---|
| `GET /credentials/{id}/permissions` | Ordered by `spaceId`. |
| `PUT /credentials/{id}/permissions/{spaceId}` | Body `{ "effect": "ALLOW" }`. Creates or replaces the exception; `404 SPACE_NOT_FOUND`; `400 VALIDATION_FAILED` for a missing or unknown `effect`. |
| `DELETE /credentials/{id}/permissions/{spaceId}` | `204`; `404 PERMISSION_NOT_FOUND` when the card has none for that space. |

### Enrollment verification (US15)

After `settings.enrollmentDeadline` (read from the campus clock, so the simulated clock applies), the **active** cards
of **students** whose enrollment is `PENDING` are deactivated by user `SYSTEM` with reason `ENROLLMENT_NOT_COMPLETED`.
Before the deadline nothing changes. The backend also runs it daily at 00:05 campus time.

| Route | Notes |
|---|---|
| `GET /credentials/enrollment-verification/preview` | Changes nothing. |
| `POST /credentials/enrollment-verification` | Runs it now. Calling it again finds nothing left to do. |

Both answer `{ executedAt, deadline, deadlinePassed, deactivated: [Credential], unchangedCount, failedCount }`.
`deactivated` lists the cards a run would deactivate (preview) or did deactivate (run).

### Settings

`GET /settings` → `{ term: "2026-2", enrollmentDeadline: "2026-10-03T23:59:59", defaultToleranceMinutes: 10 }`.

### Catalogues (read only, any signed-in user)

| Route | Fields and filters |
|---|---|
| `GET /spaces`, `GET /spaces/{id}` | `{ id, code, name, type, building, floor, capacity, status, accessPolicy, allowedRoles }`. Filter `type` (`CLASSROOM`, `LABORATORY`, `RESTRICTED_AREA`, `CAMPUS_ENTRANCE`, `CAMPUS_EXIT`, `MEETING_ROOM`, `LIBRARY`, `STUDY_CUBICLE`). `status`: `AVAILABLE` or `MAINTENANCE`; `accessPolicy`: `OPEN` or `RESTRICTED`; `allowedRoles` from `STUDENT`, `TEACHER`, `STAFF`. `404 SPACE_NOT_FOUND`. |
| `GET /courses` | `{ id, code, name, subject }`, ordered by code. |
| `GET /class-schedules` | `{ id, courseId, section, teacherId, spaceId, dayOfWeek, startTime, endTime, term }`; `dayOfWeek` 1 = Monday to 7 = Sunday, times `HH:mm`; `teacherId` is a card holder id. Filters `teacherId`, `spaceId`, `term`. |
| `GET /enrollments` | `{ id, studentId, scheduleId, term, status }`, `status` `ACTIVE` or `WITHDRAWN`; `studentId` is a card holder id. Filters `studentId`, `scheduleId`, `term`, `status`. |

### Events (for information)

Every change above is also published to the `tarjepafi.events` exchange after it commits
(`credentials.credential-status-changed`, `credentials.holder-linked`, `credentials.permission-set`,
`credentials.permission-removed`). The frontend does not consume them; the data management context will.

## IoT readers (phase 4)

The NFC readers, their health, their incidents and every card tap. **Every `/iot/**` route needs the `ADMIN` role.**
Field names follow the old web app's `iot.resources.ts`. Times are campus local date-times.

### Readers

`Reader`: `{ id, name, deviceType, spaceId, status, configuration, registeredAt, lastSeenAt, availableActions }`.
`id` looks like `RDR-A-301`. `deviceType`: `CLASSROOM_READER`, `TURNSTILE`, `DOOR_READER`, `DESK_READER`.
`status`: `REGISTERED`, `ACTIVE`, `DEGRADED`, `OFFLINE`, `INACTIVE`, `MAINTENANCE`; only `ACTIVE` and `DEGRADED` process
taps. `configuration`: `{ readerMode: ATTENDANCE | ACCESS_CONTROL, communicationInterval, heartbeatInterval (seconds),
firmwareVersion: "2.4.1" }`. `lastSeenAt` is null until the reader is first seen. `availableActions` from `ACTIVATE`,
`DEACTIVATE`, `START_MAINTENANCE`, `END_MAINTENANCE`.

| Route | Story | Notes |
|---|---|---|
| `GET /iot/readers` | | Ordered by id. Filters `status`, `spaceId`. |
| `GET /iot/readers/{id}` | | `{ reader, telemetry, incidents, recentTaps }`: last 20 samples and last 20 taps, newest first, all incidents. `404 READER_NOT_FOUND`. |
| `POST /iot/readers` | TS05 | `201`. Body `{ id, name, deviceType, spaceId, configuration }`. Starts `REGISTERED`. |
| `PUT /iot/readers/{id}/configuration` | TS06 | Body = `configuration`. Invalid data changes nothing. |
| `POST /iot/readers/{id}/activate` | TS07 | From `REGISTERED` or `INACTIVE`; `lastSeenAt` becomes now. |
| `POST /iot/readers/{id}/deactivate` | TS08 | From `ACTIVE`, `DEGRADED` or `OFFLINE`. |
| `POST /iot/readers/{id}/start-maintenance`, `.../end-maintenance` | TS09 | Maintenance can start from any status but `MAINTENANCE`; ending it makes the reader `ACTIVE`. |
| `GET /iot/readers/{id}/telemetry` | | Newest first. `limit` (default 50, at most 500). |
| `GET /iot/readers/{id}/incidents` | | Newest first. `open=true` unresolved only, `open=false` resolved only. |
| `GET /iot/readers/{id}/tap-events` | | Polling, see below. |
| `POST /iot/readers/{id}/taps` | | Taps a card as the simulator would, see below. |

`Telemetry`: `{ id, readerId, capturedAt, signalStrength (dBm, closer to 0 is better), uptime (seconds), firmwareVersion,
status }`. A weak signal (below -75 dBm) turns an `ACTIVE` reader `DEGRADED`; a good one turns it back.

Errors of `POST /iot/readers` and `PUT .../configuration`:

| Status | `code` | When |
|---|---|---|
| 400 | `READER_ID_INVALID` | The id does not look like `RDR-A-301`. |
| 400 | `READER_CONFIGURATION_INVALID` | Intervals not greater than 0, heartbeat shorter than the communication interval, or firmware not like `2.4.1`. The extra property `errors` lists every broken rule: `MODE_REQUIRED`, `COMMUNICATION_INTERVAL_INVALID`, `HEARTBEAT_INTERVAL_INVALID`, `HEARTBEAT_LOWER_THAN_COMMUNICATION`, `FIRMWARE_FORMAT`. |
| 400 | `VALIDATION_FAILED` | A required field is missing. |
| 404 | `SPACE_NOT_FOUND`, `READER_NOT_FOUND` | Unknown space or reader. |
| 409 | `READER_ID_TAKEN` | A reader with that id exists. |
| 409 | `ACTION_NOT_ALLOWED` | The status does not allow the action. |

### Incidents

`Incident`: `{ id, readerId, type, detectedAt, description, resolvedAt }`; `type`: `CONNECTIVITY_FAILURE`,
`READ_ERROR`, `DEVICE_OFFLINE`, `CONFIGURATION_ERROR`, `HARDWARE_FAILURE`; `resolvedAt` is null while open. They are
opened by the backend: a reader silent for more than 3 heartbeats goes `OFFLINE` (`DEVICE_OFFLINE`, closed again when it
sends something), a tap from an unregistered reader (`CONFIGURATION_ERROR`) or from one that cannot process taps
(`READ_ERROR`), or an error report from the reader itself. A reader never has two open incidents of the same type.

| Route | Notes |
|---|---|
| `GET /iot/incidents` | Newest first. Filters `readerId`, `open`. |
| `POST /iot/incidents/{id}/resolve` | `404 INCIDENT_NOT_FOUND`, `409 INCIDENT_ALREADY_RESOLVED`. |

### Tap events and polling

`TapEvent`: `{ id, cursor, readerId, cardUid, tappedAt, outcome, tone, messageKey, messageParams, displayMessage, source, buffered }`.
`outcome`: `PENDING` (not resolved yet), `ATTENDANCE`, `ACCESS`, `WORK_SHIFT`, `REJECTED`. `tone`: `success`, `info`,
`error` (null while `PENDING`). `displayMessage` is the Spanish text the reader showed; `messageKey` (`tap.*`) and
`messageParams` let you translate it yourself. `source`: `READER` or `SIMULATOR`. `cardUid` looks like `04:3D:9B:12:8F`.
Since phase 5 the outcome is the real one: attendance rules for classrooms and meetings, access rules for everything
else (see the next two sections). A tap from a reader that cannot process taps, or with an unknown or inactive card,
resolves as `REJECTED` (`tap.readerOutOfService`, `tap.cardNotRegistered`, `tap.cardInactive`). The message
`tap.received` ("Lectura recibida") is no longer produced.

| Route | Notes |
|---|---|
| `GET /iot/tap-events` | Every reader. Filter `readerId`. |
| `GET /iot/readers/{id}/tap-events` | One reader. |
| `GET /iot/tap-events/{id}` | `404 TAP_EVENT_NOT_FOUND`. |

The two list routes answer `{ items: [TapEvent], nextCursor: "1234" }`, newest first, at most `limit` items (default 50,
at most 200). **To poll** (every 2 seconds is fine): call once without `after`, keep `nextCursor`, and call again with
`?after=<nextCursor>`; you get only the events stored since, and `nextCursor` stays the same when nothing is new. A tap
still `PENDING` in one answer is not returned again with its result: look it up by id (or poll without `after` and
replace by id) until its `outcome` changes; resolution normally takes well under a second. `400 INVALID_CURSOR` for a
cursor that is not a number.

### Simulate a tap

`POST /iot/readers/{id}/taps` with `{ "cardUid": "04:3D:9B:12:8F" }` (any case, with or without colons) makes the
backend publish a tap to the broker as the reader would. The call waits up to 3 seconds: `200` with the resolved
`TapEvent`, or `202` with the event still `PENDING`. A tap that is rejected (unknown reader, reader out of service,
unknown or inactive card) is still `200` with outcome `REJECTED`. `400 INVALID_CARD_UID`.

### What a real reader sends (Wokwi)

Readers speak MQTT to the broker; the topics and payloads are the device contract v1 in the plan (section 4). The
backend replies on `tarjepafi/readers/{readerId}/display` with `{ eventId, tone, lines }`. Messages that break the
contract are retried three times and then parked in `iot.device-events.queue.dlq`. The frontend never talks MQTT.

## Attendance and class sessions (phase 5)

How a tap becomes attendance (the reader shows the answer, you read the result with the routes below): a student's tap in
a classroom with a class in progress (15 minutes before the start until the end) and an active enrollment creates a mark
that stays `PENDING` until the teacher taps; the teacher's first tap starts the session and confirms the pending marks
(`PRESENT` up to start + tolerance, otherwise `LATE`), the second tap is the departure (duration) and closes the session,
a third one only answers "already registered". 15 minutes after the end a session nobody closed is closed by the system:
every enrolled student without a mark gets an `ABSENT` record (`source: SYSTEM`); `PENDING` marks stay for the teacher.
Meetings work like classes for the invited teachers (no `PENDING`). Time rules use the campus clock.

These routes are for the **TEACHER** role (an admin gets `403`); the teacher is the caller's card holder.

`Session`: `{ id, type (CLASS, MEETING), scheduleId, title, spaceId, date, scheduledStart, scheduledEnd, toleranceMinutes,
status (SCHEDULED, IN_PROGRESS, FINISHED) }`.
`AttendanceRecord`: `{ id, cursor, tapEventId, sessionId, cardHolderId, role (STUDENT, TEACHER), checkInAt, checkOutAt,
durationMinutes, status (PENDING, PRESENT, LATE, ABSENT, JUSTIFIED), confirmedAt, source (NFC, TEACHER_EDIT, SYSTEM),
modifiedBy, modifiedAt, editReason }`.

| Route | Answer and errors |
|---|---|
| `GET /class-sessions/mine?date=` | My classes of the day (default today by the campus clock): `[{ session, course: { id, code, name, subject, section }, teacherRecord, counts: { pending, present, late, absent, justified, unmarked, total } }]`. |
| `GET /class-sessions/{id}` | The same plus `roster: [{ cardHolderId, fullName, code, record }]` (`record` is null for a student without a mark). `404 SESSION_NOT_FOUND`, `403 NOT_YOUR_SESSION`. |
| `GET /class-sessions/{id}/attendance` | **Polling.** `{ items: [AttendanceRecord], nextCursor }`, newest first, `after`, `limit` (default 100, at most 500). A record that changes keeps its cursor: reload the detail to see edits. `400 INVALID_CURSOR`. |
| `POST /class-sessions/{id}/confirm-pending` | `{ confirmed: n }`: every `PENDING` mark becomes `PRESENT` or `LATE` by its own time (TS14). `409 SESSION_NOT_STARTED` (still `SCHEDULED`), `409 NOTHING_TO_CONFIRM`. |
| `PUT /class-sessions/{id}/attendance/{studentId}` | Body `{ status (PRESENT, LATE, ABSENT, JUSTIFIED), reason }`; `reason` is required, at most 255 (TS15). Creates the record when the student has none. Answers the `AttendanceRecord` (`source: TEACHER_EDIT`, `modifiedBy`, `editReason`). `400` (blank reason / status), `409 SESSION_NOT_STARTED`, `422 STUDENT_NOT_ENROLLED`. |
| `POST /class-sessions/{id}/attendance/{studentId}/justify` | Body `{ reason }`: sets `JUSTIFIED` (TS16). Same errors. |
| `GET /class-sessions/attendance/mine` | My own marks in my classes, newest first: `[{ record, session, course }]` with arrival, departure and duration. |
| `GET /class-sessions/meetings/mine` | Meetings and trainings I am invited to, newest first: `[{ session, record }]` (`record` null until I tap). |

Messages the reader shows for attendance (`messageKey`): `tap.attendanceTaken` (params `course`, `time`),
`tap.alreadyRegistered`, `tap.noClassInProgress`, `tap.notEnrolled`, `tap.teacherArrival` (`course`, `count`),
`tap.teacherArrivalNoRecords`, `tap.teacherExit` (`duration`, like `1 h 30 min`), `tap.exitAlreadyRegistered`,
`tap.noAssignedClass`, `tap.notInvited`, `tap.meetingRegistered`, `tap.meetingRegisteredLate`. Outcome `ATTENDANCE`
(`REJECTED` for the refusals).

## Space access (phase 5)

Who resolves a tap: a reader in a classroom is attendance's (students and teachers; **staff** are space access),
a teacher in a meeting room with a meeting in progress is attendance's, everything else is space access: spaces,
laboratories, library, restricted areas and the campus turnstiles. The rules, in order: space under maintenance denies;
the card's own exception (`ALLOW`/`DENY`, see Credentials) wins; otherwise the role must be in the space's
`allowedRoles`. Every attempt leaves an access record. A card that is not registered or not active, read at a reader
that is not in a classroom, leaves a `DENIED` record too (`CARD_NOT_REGISTERED`, `CARD_INACTIVE`).
At the campus entrance and exit, **staff** open and close their work shift (outcome `WORK_SHIFT`); students and
teachers only leave the access record (outcome `ACCESS`).

Routes need the **ADMIN** role (reads of spaces stay open to any signed-in user, see Catalogues).

`AccessRecord`: `{ id, cursor, tapEventId, cardUid, credentialId, cardHolderId, spaceId, readerId, accessType
(CAMPUS_ENTRY, CAMPUS_EXIT, SPACE_ACCESS), decision (GRANTED, DENIED), denialReason (NONE, CARD_NOT_REGISTERED,
CARD_INACTIVE, UNAUTHORIZED_ROLE, SPACE_IN_MAINTENANCE), attemptedAt }`.
`WorkShift`: `{ id, cursor, cardHolderId, date, entryAt, exitAt, workedMinutes, status (OPEN, CLOSED) }`.

| Route | Answer and errors |
|---|---|
| `GET /access-records` | **Polling.** `{ items, nextCursor }`, newest first. Filters `spaceId`, `cardHolderId`, `decision`; `after`, `limit` (default 50, at most 200). `400 INVALID_CURSOR`. |
| `GET /work-shifts` | `[WorkShift]`, newest first. Filters `cardHolderId`, `status`, `date`. |
| `POST /spaces` | Register a space (TS18), body `{ code, name, type, building, floor, capacity, accessPolicy, allowedRoles }`; `type` also accepts `EVENT_VENUE`. `201` AVAILABLE. `409 SPACE_CODE_EXISTS`, `400 INVALID_SPACE_TYPE`, `INVALID_CAPACITY`, `INVALID_ACCESS_POLICY`, `INVALID_ROLE`. |
| `PATCH /spaces/{id}/status` | Body `{ status }` (`AVAILABLE`, `MAINTENANCE`). Later taps there are denied with `SPACE_IN_MAINTENANCE`. `404 SPACE_NOT_FOUND`, `400 INVALID_SPACE_STATUS`. |
| `PATCH /spaces/{id}/access-policy` | Body `{ accessPolicy, allowedRoles }` (TS28); later taps use them. `404`, `400 INVALID_ACCESS_POLICY` / `INVALID_ROLE`. |
| `GET /spaces/{id}/occupancy` | `{ capacity, currentOccupancy, isFull, tracked }` (TS19). Only the campus is counted: for `CAMPUS_ENTRANCE` / `CAMPUS_EXIT` spaces it is the number of people who entered today and have not left (`tracked: true`); every other space answers `tracked: false` and 0. |
| `GET /card-holders/{id}/campus-presences?from=&to=` | `{ items: [{ entryAt, exitAt, durationMinutes, status (OPEN, CLOSED) }], totalMinutes }` (TS30): a stay is a granted entry up to the next granted exit; an open one has no duration and is not added. `400 INVALID_RANGE` when `from` is after `to`. |

Messages for space access: `tap.accessGranted`, `tap.accessDenied`, `tap.shiftOpened`, `tap.shiftAlreadyOpen`,
`tap.shiftClosed` (param `worked`, like `8 h 13 min`), `tap.noEntryFound`, `tap.exitAlreadyRegistered`. A tap whose
event arrives twice is stored once and the reader is told `tap.duplicateEvent` ("Evento ya procesado").

Events published (for information; data management consumes them): `attendance.recorded`, `attendance.confirmed`,
`attendance.late-detected`, `attendance.edited`, `attendance.rejected`, `attendance.session-closed`,
`attendance.tap-resolved`, `space.access-granted`, `space.access-denied`, `space.work-shift-opened`,
`space.work-shift-closed`, `space.tap-resolved`.

## Demo accounts (profile `demo`)

| E-mail | Password | Role | Status |
|---|---|---|---|
| `docente@upc.edu.pe` | `Docente123*` | TEACHER | ACTIVE |
| `lparedes@upc.edu.pe` | `Docente123*` | TEACHER | ACTIVE |
| `trabajador@upc.edu.pe` | `User123*` | STAFF | ACTIVE |
| `u202312966@upc.edu.pe` | `User123*` | STUDENT | ACTIVE |
| `jsalazar@upc.edu.pe` | `Docente123*` | TEACHER | INACTIVE (sign-in gives `ACCOUNT_DISABLED`) |
| `soporte@upc.edu.pe` | `Admin123*` | ADMIN | SUSPENDED (sign-in gives `ACCOUNT_DISABLED`) |

An active administrator (`admin@upc.edu.pe`) exists on the deployed demo; ask the backend owner for its password.
Locally it is seeded with `Admin123*`.

Request examples that run against any environment: `docs/http/iam.http`.

## Changelog

- 2026-11-23 · First version: IAM and the simulated clock.
- 2026-11-24 · IoT section (readers, incidents, tap events with polling, simulated taps), verified on the deployed API and **frozen**: from now on only additive changes, announced here. Until phase 5 every valid tap resolves as `ACCESS` / `tap.received`; `outcome`, `tone`, `messageKey` and `displayMessage` of those events will change when the attendance and access rules arrive.
- 2026-11-23 · Credentials section (cards, card holders, permissions, enrollment verification, settings, catalogues), verified on the deployed API and **frozen**: from now on only additive changes, announced here.
- 2026-11-24 · Attendance and space access sections (phase 5): teacher routes under `/class-sessions`, `/access-records`, `/work-shifts`, space management and occupancy, campus presences. Verified on the deployed API and **frozen**. A tap that writes records may take more than 3 seconds to resolve on the deployed stack: `POST /iot/readers/{id}/taps` then answers `202` with the event `PENDING`; poll `GET /iot/tap-events/{id}`. **Behaviour change of the frozen IoT section:** valid taps no longer resolve as `ACCESS` / `tap.received`; `outcome`, `tone`, `messageKey` and `displayMessage` now come from the attendance and access rules (the shape of `TapEvent` did not change). `POST /spaces` and `PATCH /spaces/**` are new admin-only routes next to the open `GET`s.
- 2026-11-24 · Additive: `TapEvent.buffered` (boolean, true when the reader stored the tap while offline and sent it afterwards; false for simulated and live taps). New Swagger "Authorize" button for the bearer token.
