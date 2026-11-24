# Role matrix (phase 9, QAS09)

Who can call what. It is enforced in `SecurityConfiguration` and checked route by route by
`RoleMatrixApiTests`: every route of the application is called with no token and with a token of each role (admin,
teacher, staff, student) and the answer must be allowed or refused (401 without a token, 403 with the wrong role) exactly as
below. A route that is added and not listed here falls in the last row (any signed-in user), so the test fails if a new
route was meant to be stricter.

| Routes | Anonymous | Student | Staff | Teacher | Admin |
|---|---|---|---|---|---|
| `POST /auth/sign-in`, Swagger UI, `/v3/api-docs`, `/actuator/health` | yes | yes | yes | yes | yes |
| `GET /auth/me`, `GET /simulation/clock`, reads of spaces and catalogues (`GET /spaces`, `/courses`, `/class-schedules`, `/enrollments`, ...) | no (401) | yes | yes | yes | yes |
| `PUT /simulation/clock` (demo profile only) | no | 403 | 403 | 403 | yes |
| `/credentials/**`, `/card-holders/**` | no | 403 | 403 | 403 | yes |
| `/iot/**` (readers, incidents, tap events, simulated taps) | no | 403 | 403 | 403 | yes |
| `/access-records/**`, `/work-shifts/**` | no | 403 | 403 | 403 | yes |
| `POST /spaces`, `PATCH /spaces/**`, `GET /spaces/{id}/occupancy` | no | 403 | 403 | 403 | yes |
| `/reservations/**`, `/spaces/{id}/reservations` | no | 403 | 403 | 403 | yes |
| `/data-records/**`, `/dashboard/**`, `/reports/**` | no | 403 | 403 | 403 | yes |
| `/class-sessions/**` (the teacher's classes, attendance, meetings) | no | 403 | 403 | yes | 403 |

The administrator is deliberately refused on the teacher routes: they act on the signed-in teacher's own sessions.

## Other protections reviewed

- **Passwords** are BCrypt hashes and never leave the server; a request for an unknown e-mail compares against a decoy
  hash so both failures take the same time.
- **Tokens** are signed JWTs (HS256) of 8 hours; the secret must be at least 32 bytes and has no default outside the `dev`
  profile (`JWT_SECRET`). Every request re-reads the user, so suspending an account takes effect at once.
- **Sign-in brute force** (new in phase 9): 5 failed attempts for an e-mail, or 30 from one address, lock it for 5 minutes
  with `429 TOO_MANY_ATTEMPTS`, a `Retry-After` header and `retryAfterSeconds`. The e-mail is tracked whether it exists or
  not, and a locked e-mail refuses even the right password. Tunable with `tarjepafi.security.sign-in.*`. In memory and in
  real time: it suits the single instance of this deployment. Behind the App Service front end the address is read from
  `X-Forwarded-For`, which a caller can forge, so the per-address limit is a convenience and the per-e-mail limit is the
  one that holds.
- **Broker**: readers use a dedicated user (`reader-demo`) limited to the devices exchange; its password is visible in a
  public Wokwi project and is rotated after each showcase. The backend uses another user. The MQTT port is plain (1883),
  see the decision on TLS in `PROGRESS.md`.
- **CORS** only admits the origins in `CORS_ALLOWED_ORIGINS`.
