# TarjePAFI backend · instructions for coding agents

Start here, in this order:

1. `docs/PROGRESS.md`: current state, next step, checklist, handoff log.
2. `docs/IMPLEMENTATION_PLAN.md`: decisions (section 2), report vs. web app conflicts (section 3),
   frontend handoff (section 8),
   architecture and device contract (section 4), phases with their Verify blocks (section 7).

Rules:

- Work on the first unticked step. Do not skip a phase's Verify block.
- Tick a step in `docs/PROGRESS.md` only after you ran its verification. Before stopping, update
  "Current state", "Next step" and the log.
- **Do not change the web app** (`../tarjepafi-webapp`). Another team member builds the frontend from this API
  (plan revision 4). The old web app is a read-only reference: port rules from the TypeScript file named in the plan
  (folder `webapp/`, branch `feat/connection-tests`) together with its unit tests.
- The API contract is a deliverable (plan D14): every endpoint gets Swagger docs with examples and error codes, a
  section in `docs/API.md` and requests in `docs/http/<context>.http`. Once a phase is verified on the deployed API,
  its contract is frozen: only additive changes, or announce the change in the `docs/API.md` changelog.
- Every phase from 2 on is redeployed and verified on the deployed API: the target is a live showcase from a public
  Wokwi project (plan section 6).
- If you change a decision of the plan, edit the plan in the same change and say why in the log.
- Conventions (report 7.1.2 and 7.1.3): GitFlow (`feature/<kebab-case>` off `develop`, PR to merge),
  Conventional Commits in English, code and comments in English, packages
  `com.pafisolutions.tarjepafi.<context>.<domain|application|interfaces|infrastructure>`, constructor
  injection, value objects as records, tests named `method_condition_expectedResult`, tables snake_case plural.
- A bounded context never touches another context's tables or internal classes: use integration events on
  `tarjepafi.events` or the owning context's facade/port.
- Time-dependent rules read the time from `CampusClock`, never from `LocalDateTime.now()`.

- Do not commit, push, create cloud resources or spend Azure credit unless the person you work for asks in
  this session. Never put secrets in the repo or in these documents.

Commands:

```bash
docker compose up -d                       # PostgreSQL (host port 5433) + RabbitMQ (AMQP 5672, MQTT 1883, UI 15672)
mvn verify                                 # needs JDK 25; with an older JDK add -Djava.version=24
mvn spring-boot:run -Dspring-boot.run.profiles=dev,demo
```
