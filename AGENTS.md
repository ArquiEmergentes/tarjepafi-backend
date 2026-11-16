# TarjePAFI backend · instructions for coding agents

Start here, in this order:

1. `docs/PROGRESS.md`: current state, next step, checklist, handoff log.
2. `docs/IMPLEMENTATION_PLAN.md`: decisions (section 2), report vs. web app conflicts (section 3),
   architecture and device contract (section 4), phases with their Verify blocks (section 7).

Rules:

- Work on the first unticked step. Do not skip a phase's Verify block.
- Tick a step in `docs/PROGRESS.md` only after you ran its verification. Before stopping, update
  "Current state", "Next step" and the log.
- The web app (`../tarjepafi-webapp`, base branch `feat/connection-tests`, folder `webapp/`) is refactored to fit the backend, one context per phase
  (plan D2 and section 8). Its domain model and resource field names are kept; its API calls and client-side
  rules are replaced. When porting a rule, port it from the TypeScript file named in the plan and port its unit
  tests too. Never rename a signal or method that `presentation/` uses.
- Every phase from 2 on is redeployed and verified on the deployed environment: the target is a live showcase
  from a public Wokwi project (plan section 6).
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
