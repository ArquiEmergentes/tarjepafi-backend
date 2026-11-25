# Performance evidence (phase 9, QAS01 and QAS02)

> The class decided on 2026-11-25 that the quality scenarios are not evaluated. This is kept as information: the numbers
> below were measured, the deployed demo was not tuned further and its database stays in `us-east-2`.

| QAS | Requirement | Where it holds |
|---|---|---|
| QAS01 | a card reading is confirmed (display reply) within 1 second | locally, with margin (p95 37 ms); **not on the deployed demo** (see below) |
| QAS02 | at least 200 readings per minute without loss or slowdown | locally, with margin (305 per minute, p95 26 ms); on the deployed demo: see the latest measurement |

## How it is measured

- **`TapLoadTests`** (JUnit, real PostgreSQL and RabbitMQ in containers, only on request:
  `mvn test -Dtest=TapLoadTests -Dtarjepafi.load=true`, results in `target/load-test-results.md`). It publishes taps to the
  broker the way a reader does and times each one until its display reply arrives. Taps come from 36 students over four
  readers (two turnstiles, two doors), so every tap is accepted and writes an access record.
- **`scripts/loadtest/mqtt_tap_load.py`** does the same against the deployed environment over MQTT (the real path of a
  reader, from this laptop to the broker in Azure Chile Central). It writes real taps: reset the demo afterwards.

## Local results (laptop, PostgreSQL next to the app)

| Scenario | Taps | Rate | Lost | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|---|
| QAS01, normal operation | 60 | 257 per minute | 0 | 28 ms | 37 ms | 126 ms | 126 ms |
| QAS02, sustained for one minute | 300 | 305 per minute | 0 | 22 ms | 26 ms | 31 ms | 35 ms |
| QAS02, burst at the class start | 200 at once | 12 000 per minute | 0 | 979 ms | 1 502 ms | 1 521 ms | 1 526 ms |

The latency of the last third of the sustained run (22 ms median) is the same as the first third (23 ms): no degradation.
The burst is queued by the broker and taken in about 1.5 seconds without losing anything. A tap costs about 22 SQL
statements across its stages (device event, card lookup, tap event, access record or attendance, display reply, data
record), which is what matters on a database that is far away.

## Deployed demo (App Service B1, Neon free PostgreSQL in `us-east-2`, app in Chile Central)

First measurement, before the consumer concurrency was raised (taps published over MQTT):

| Scenario | Taps | Replies | p50 | p95 |
|---|---|---|---|---|
| 2 taps per second for 30 s | 60 | 54 (6 still queued when the run ended) | 18.2 s | 31.1 s |
| 5 taps per second for 60 s | 300 | 113 | 51.2 s | 93.0 s |

Nothing is lost (the messages wait in the durable queues and are processed later), but the pipeline takes about one tap
per second because every consumer handled one message at a time and each message needs several round trips of about
150 ms to a database on another continent: 22 statements per tap. That is the cause, not the code: the same code answers
in 28 ms with a local database. So QAS01 and QAS02 are **not met by the free-tier demo stack as it was deployed**, and
they are met by the application when the database is close.

Changes made for it (phase 9): the consumers that only need the database run several at a time
(`tarjepafi.amqp.concurrency.*`: device events 4 to 8, tap resolution 2 to 4, space access 2 to 4, data records 2 to 4);
the attendance consumer stays at one on purpose, because the marks of a class depend on the order of the teacher's and the
students' taps; the connection pool is 10 (was 5); `Reader` updates only the columns that changed, so the concurrent
handling of taps, heartbeats and telemetry of one reader cannot overwrite each other. The sign-in lock and the other
changes of the phase do not touch this path.

The remaining lever is the distance to the database: a Neon project in `sa-east-1` (São Paulo), about 3 times closer than
Ohio, or any PostgreSQL in Chile Central. It is a change of the `DB_URL` setting and a data reset, with no code change.
