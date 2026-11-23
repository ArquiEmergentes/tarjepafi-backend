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
