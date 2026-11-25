# Frontend handoff

For the person (and the Claude agent) who builds the TarjePAFI frontend against this backend. `docs/API.md` is the contract;
this file is the map around it: where to start, which screens the API supports, how live data works, how the Wokwi demo
shows up on screen, and what to watch out for. The backend is finished (phases 0 to 9) and its contracts are frozen:
changes from now on are additive and announced in the changelog at the end of `docs/API.md`.

## 1. Read, in this order

1. This file.
2. `docs/API.md`: conventions, errors, one section per context, the changelog.
3. Swagger UI of the deployed API (try the calls; **Authorize** takes the sign-in token without `Bearer `).
4. `docs/http/*.http`: ready requests for every context (`demo.http` is the demo script). They run in VS Code (REST Client) or IntelliJ.
5. `docs/ROLE_MATRIX.md`: who may call what.
6. The old web app (`tarjepafi-webapp`, branch `feat/sprint1-fase7-testing-suite`, folder `webapp/`) is the reference for the screens, the
   domain model and the Spanish texts (`public/i18n/es.json`). It talked to a json-server; the backend replaces that, with the same field
   names where the plan allowed it. Do not change it, copy from it.

A prompt to start the agent with:

```text
You are building the frontend of TarjePAFI Smart Campus. The backend is deployed and finished. Read docs/FRONTEND_HANDOFF.md, then
docs/API.md, in the backend repo (tarjepafi-backend), and use the old web app (tarjepafi-webapp/webapp) only as a reference for screens
and Spanish texts. Build against the deployed API (base URL in docs/API.md); never mock what the API already offers. Run the dev server on
port 4781. Ask me before doing anything the handoff says the backend cannot do.
```

## 2. Environments and accounts

| | |
|---|---|
| Base URL | `https://tarjepafi-api-pafi7k2.azurewebsites.net/api/v1` (Swagger at `/swagger-ui.html`) |
| Dev server | `http://localhost:4781` is allowed by CORS. Tell the backend owner the **deployed origin** of the frontend: it has to be added to `CORS_ALLOWED_ORIGINS` before the showcase |
| Cold start | the deployed app is stopped between sessions; the first call can take about a minute, and after a restart the seed needs about 2 more minutes (until `GET /iot/readers/RDR-A-301` answers 200) |
| Admin | `admin@upc.edu.pe`, password from the backend owner (it is not in the repository) |
| Teacher | `docente@upc.edu.pe` / `Docente123*` (D0001, the demo teacher) |
| Staff, student | `trabajador@upc.edu.pe` / `User123*`, `u202312966@upc.edu.pe` / `User123*` (they can sign in; there are no routes for them yet, see section 7) |

Sign in once, keep `token` and `user`, send `Authorization: Bearer <token>` on every call. The token lasts 8 hours and there is no refresh:
any `401` outside sign-in means "go to the login". Five wrong attempts for an e-mail answer `429 TOO_MANY_ATTEMPTS` with `retryAfterSeconds`: show it.

## 3. Which screens the API supports

Administrator (everything under these routes is ADMIN only):

| Screen | Calls |
|---|---|
| Dashboard of the day | `GET /dashboard?date=` (attendance, access, readings by hour, recent taps), `GET /iot/readers` (status counts), `GET /credentials` (status counts), `GET /spaces/{id}/occupancy` (campus entrance) |
| Readers | `GET /iot/readers`, `GET /iot/readers/{id}` (reader, last 20 telemetry, incidents, last 20 taps), `POST /iot/readers`, `PUT /iot/readers/{id}/configuration`, `POST .../activate`, `.../deactivate`, `.../start-maintenance`, `.../end-maintenance` |
| Live taps | `GET /iot/tap-events` (poll with `after`), `GET /iot/readers/{id}/tap-events`, `GET /iot/tap-events/{id}` |
| Incidents | `GET /iot/incidents`, `GET /iot/readers/{id}/incidents`, `POST /iot/incidents/{id}/resolve` |
| Simulator | `POST /iot/readers/{id}/taps` with `{ cardUid }`: plan B of the demo, and a way to test without Wokwi |
| Cards | `GET /credentials`, `GET /credentials/{id}`, `.../history`, `POST .../block`, `.../deactivate`, `.../report-lost`, `.../reactivate`, `.../link-holder`; exceptions: `GET/PUT/DELETE /credentials/{id}/permissions/{spaceId}`; enrollment: `GET /credentials/enrollment-verification/preview`, `POST /credentials/enrollment-verification` |
| Card holders | `GET /card-holders?name=`, `GET /card-holders/{id}`, `GET /card-holders/{id}/campus-presences?from=&to=` |
| Spaces and access | `GET /spaces`, `GET /spaces/{id}`, `POST /spaces`, `PATCH /spaces/{id}/status`, `PATCH /spaces/{id}/access-policy`, `GET /access-records` (poll), `GET /work-shifts` |
| Reservations | `POST /spaces/{id}/reservations`, `POST /reservations/{id}/cancel`, `GET /reservations/{id}`, `GET /reservations/active`, `GET /spaces/{id}/reservations?from=&to=` |
| Reports | `POST /reports/{weekly\|monthly\|semester}` (202), `GET /reports`, `GET /reports/{id}` (figures per day); the CSV export is built in the browser from `days` |

Teacher (TEACHER only; the administrator is refused on these):

| Screen | Calls |
|---|---|
| My classes of a day | `GET /class-sessions/mine?date=` |
| A class with its roster, live | `GET /class-sessions/{id}` once, then poll `GET /class-sessions/{id}/attendance?after=` |
| Edit, justify, confirm | `PUT /class-sessions/{id}/attendance/{studentId}` (reason required), `POST .../justify`, `POST /class-sessions/{id}/confirm-pending` |
| My own marks and my meetings | `GET /class-sessions/attendance/mine`, `GET /class-sessions/meetings/mine` |

Everyone signed in: `GET /auth/me`, `GET /simulation/clock`, `GET /settings`, `GET /spaces`, `GET /courses`, `GET /class-schedules`, `GET /enrollments`.

## 4. Live data: how to make a screen move

- **Polling, not push.** Lists a live screen watches (`tap-events`, `access-records`, `data-records`, an attendance session) answer
  `{ items, nextCursor }`, newest first. First call without `after`; then send the last `nextCursor` as `after` and you get only what is new
  (an empty `items` keeps the same cursor). Poll tap events and a running class every 1 to 2 seconds during the demo, readers and the dashboard
  every 5.
- **A tap that takes long.** `POST /iot/readers/{id}/taps` answers `200` with the result, or `202` with `outcome: PENDING` when resolving
  takes more than 3 seconds: poll `GET /iot/tap-events/{id}` until `outcome` is not `PENDING`.
- **Reports.** `POST /reports/...` answers `202` with `status: GENERATING`; poll `GET /reports/{id}` every second until `GENERATED` (about 2 seconds).
- **What the reader shows.** Every tap event carries `displayMessage` (Spanish, what the LCD showed), `messageKey` (`tap.*`) and `messageParams`.
  Show `displayMessage` as it is, or map `messageKey` to your own texts with the web app's `es.json`. `outcome` is `ATTENDANCE`, `ACCESS`,
  `WORK_SHIFT` or `REJECTED`, `tone` is `success`, `info` or `error`; a tap read while the reader was offline has `buffered: true` and its
  original `tappedAt`.
- **Errors** have a stable `code`: map it to your texts; the table of every code is in each section of `docs/API.md`.

## 5. The campus clock

The demo runs on a **simulated clock** that starts on Wednesday 2026-10-07 09:50 (ten minutes before the class of the demo teacher) and only
moves when an administrator calls `PUT /simulation/clock { "now": "2026-10-07T10:00:00" }`. Every time on screen is campus time without
offset. Read the current time from `GET /simulation/clock` (not from the browser) and offer the administrator a control to move it: the
demo needs it. Things that follow a move:

- A class starts, is late or closes according to that time; reservations are forfeited 10 minutes after their start within about a minute.
- **A jump of more than a few minutes leaves the readers OFFLINE** until they send a heartbeat (the Wokwi reader sends one every minute of
  its own time, which is slower than real time): a tap in that gap is answered "Lector fuera de servicio". Wait for the reader to be `ACTIVE`
  again before the next tap, or move the clock in small steps.

## 6. The live demo with Wokwi

The backend owner gives you the URL of the public Wokwi project (it is not in the repository). A reader reads a card on the simulated MFRC522;
the result reaches the API within a couple of seconds and your screens should show it. The tags and the reader:

| Wokwi card | UID | Holder |
|---|---|---|
| Blue | `01:02:03:04` | the demo student (U202312966) |
| NFC Tag | `04:11:22:33` | the demo teacher (D0001) |
| Green | `11:22:33:44` | the staff member (T0001) |
| Yellow | `55:66:77:88` | a blocked card |
| Red, Key Fob | `AA:BB:CC:DD`, `C0:FF:EE:99` | not registered |

The demo script (plan, section 6) and what each step must show:

| # | Action | The reader shows | Your screen must show |
|---|---|---|---|
| 1 | start the project | idle screen | reader `RDR-A-301` ACTIVE, `lastSeenAt` recent (`GET /iot/readers/RDR-A-301`) |
| 2 | admin sets the clock to 10:00 | | the new campus time |
| 3 | teacher card | "Llegada registrada" | class `ses-02-7` IN_PROGRESS, `teacherRecord` set |
| 4 | student card | "Asistencia tomada" | the student's mark in the roster, the new tap in the live list |
| 5 | blocked card | red LED, "Tarjeta inactiva" | the tap event `REJECTED` with that message |
| 6 | offline switch on, two taps, switch off | "Guardado sin conexion" | both tap events appear afterwards with `buffered: true` and their original times |
| 7 | plan B if Wokwi or the network fails | | a "simulate a tap" button calling `POST /iot/readers/{id}/taps` |

Other readers for the rest of the story: `RDR-T-001` / `RDR-T-002` campus entrance, `RDR-T-003` exit (staff open and close their shift),
`RDR-D-001` to `RDR-D-006` doors of laboratories and restricted areas, and `RDR-K-003` at the study cubicle `CUB-L01` (reservations:
reserve it for the student, tap the blue card between the start and 10 minutes later). The old UIDs of the seed (`04:3D:9B:12:8F` ...) are replaced
by the Wokwi ones for the demo student, teacher, staff and blocked card.

## 7. What the backend does not do

- **No self-service for students and staff.** Their accounts can sign in and read `GET /auth/me`; there is no route for "my reservations", "my
  attendance" or "my shifts" for them (only the teacher has `/class-sessions/mine`). Those screens are Sprint 2 of the web app.
- **No user management** (create, suspend, change a password): the seven demo accounts are seeded.
- **No push.** No WebSocket or SSE; polling is the contract. If polling is not enough on a screen, ask: a stream can be added (additive).
- **No refresh token, no logout call:** drop the token on the client.
- **No CSV or PDF export:** build it from the JSON (the web app did the same for reports).
- **Swagger has summaries, descriptions and error codes, but not request and response examples for every route:** use `docs/API.md` and the `.http` files.

## 8. Watch out for

- Date-times are local and have no offset: send and show them as they are, do not pass them through `new Date()` with a zone.
- `status` of a space can be `OCCUPIED` (a checked-in reservation), and its `accessPolicy` can be `RESERVATION_REQUIRED`; the frontend must tolerate
  new enum values in lists (new ones are announced in the changelog).
- The administrator cannot open the teacher routes (403) and the teacher cannot open the administrator's: route the menu by `user.role`.
- Taps made from the simulator and from Wokwi change real data (attendance, access records, reports). The backend owner resets the environment
  before a rehearsal; do not rely on data you created staying there.
- The enrollment verification is not scheduled in the demo; the administrator runs it by hand from the cards screen.
- The `reader-demo` broker password is public inside the Wokwi project and is rotated after the showcase; the frontend never needs it.

## 9. Before the showcase

- Tell the backend owner the deployed origin of the frontend (for CORS) and the date of the showcase.
- Agree on a rehearsal: the backend owner resets the data, starts the app and the broker, and you run the seven steps of section 6 together.
- Keep a plan B at hand: the simulator button (step 7) works without Wokwi.
