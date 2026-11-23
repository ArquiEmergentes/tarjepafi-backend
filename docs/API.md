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

CORS: the browser origins listed in the `CORS_ALLOWED_ORIGINS` setting are accepted (today `http://localhost:4200`).
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
- 2026-11-23 · Credentials section (cards, card holders, permissions, enrollment verification, settings, catalogues). Not frozen until verified on the deployed API.
