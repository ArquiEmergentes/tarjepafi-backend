# TarjePAFI · Backend API

Backend of **TarjePAFI** (PafiSolutions): Smart Campus solution based on NFC credentials and IoT readers.
Spring Boot modular monolith with one module per bounded context, PostgreSQL, and RabbitMQ as the message bus
between the readers (MQTT) and the backend (AMQP).

- Plan and architecture: [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md)
- Current status and handoff notes: [`docs/PROGRESS.md`](docs/PROGRESS.md)
- Instructions for coding agents: [`AGENTS.md`](AGENTS.md)

## Requirements

- JDK 25 (with an older JDK, add `-Djava.version=24` to the Maven commands)
- Maven 3.9+
- Docker Desktop

## Run locally

```bash
cp .env.example .env                 # optional, only to change ports or credentials
docker compose up -d                 # PostgreSQL 5433 · RabbitMQ AMQP 5672 · MQTT 1883 · UI http://localhost:15672
mvn spring-boot:run -Dspring-boot.run.profiles=dev,demo
```

| URL | What |
|---|---|
| http://localhost:8080/actuator/health | Health |
| http://localhost:8080/swagger-ui.html | API documentation |
| http://localhost:8080/api/v1 | API base path (frontend guide: `docs/API.md`) |

## Profiles

| Profile | Effect |
|---|---|
| `dev` | Default. Local infrastructure from `compose.yaml`. |
| `demo` | Simulated campus clock starting Wed 2026-10-07 09:50 and the demo data set (`src/main/resources/seed/db.json`), for example `admin@upc.edu.pe` / `Admin123*`. |

Outside the `dev` profile `JWT_SECRET` (32 bytes or more) is mandatory. `ADMIN_EMAIL` and `ADMIN_PASSWORD`
create the first administrator at start-up.

## Tests

```bash
mvn verify
```

Unit tests always run. Tests that need PostgreSQL and RabbitMQ use Testcontainers and are skipped when Docker is
not running.

## Structure

```
src/main/java/com/pafisolutions/tarjepafi/
  shared/               campus clock, security baseline, cross-cutting web configuration
  iam/                  users, sign-in, JWT
  credentials/          card holders, credentials, permissions
  academicattendance/   sessions and attendance records
  spacefacility/        spaces, access records, work shifts, reservations
  iotmonitoring/        readers, telemetry, incidents, card tap ingestion
  datamanagement/       data records and reports
```

Each bounded context has the layers `domain`, `application`, `interfaces` and `infrastructure`.
