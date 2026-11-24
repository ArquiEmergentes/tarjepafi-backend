# Demo environment (phase 2)

Status: **written, nothing created yet.** Every command below is a runbook that has not been run. When a step is done,
write the real resource name or URL here (never a secret) and tick it in [`PROGRESS.md`](PROGRESS.md).

The environment is temporary: it lives for the development period, is stopped between sessions, is switched on for the
showcase (about 24 hours) and then **everything is deleted** (see "Teardown").

## Architecture

```
 wokwi.com ──MQTT 1883──▶ RabbitMQ on ACI ◀──AMQP 5672── App Service B1 (backend container) ──▶ Neon PostgreSQL
                              (public IP + DNS label)      image from GitHub Container Registry
                                                                    ▲ HTTPS + JWT
                                                           frontend (deployed by its owner)
```

| Piece | Where | Why |
|---|---|---|
| PostgreSQL | Neon, free plan | Same engine as the code (no rewrite), free, 1 GB per project, scales to zero. Use the **direct** host, not the `-pooler` one (Flyway takes advisory locks). |
| Broker | Azure Container Instances, image `ghcr.io/arquiemergentes/tarjepafi-rabbitmq` | Per-second billing, `az container stop` ends the bill. The image has the MQTT plugin and `mqtt.exchange` baked in. |
| Backend | Azure App Service B1 (Linux container), image `ghcr.io/arquiemergentes/tarjepafi-backend` | HTTPS out of the box (ACI has none, and a browser blocks an HTTPS page calling plain HTTP). |
| Images | GitHub Container Registry, **public** packages | Free, no pull credentials. They contain no secrets, but they do contain the demo seed (`db.json` with the documented demo passwords). |
| Frontend | Not part of this plan (revision 4) | Built and hosted by another team member; only their origin goes into `CORS_ALLOWED_ORIGINS`. |

Region: **Chile Central** (`chilecentral`), chosen by the team on 2026-11-16. App Service B1 and ACI list it as available;
the subscription's quota there is still to be confirmed when the resources are created. The database is Neon, so the
restriction on PostgreSQL in East US does not matter.
Resource group: `rg-tarjepafi-demo`. Tag it `purpose=showcase` and `delete-after=<date>`.

**Created on 2026-11-23** (suffix `pafi7k2`, tag `delete-after=2026-12-31`):

| Resource | Name | Address |
|---|---|---|
| App Service plan | `asp-tarjepafi-demo` (B1, Linux) | |
| Backend | `tarjepafi-api-pafi7k2` | `https://tarjepafi-api-pafi7k2.azurewebsites.net` |
| Broker | `aci-tarjepafi-broker` (1 vCPU, 1.5 GB) | `tarjepafi-broker-pafi7k2.chilecentral.azurecontainer.io`, MQTT 1883, AMQP 5672 |
| Database | Neon project (direct host, `sslmode=require`) | not an Azure resource |

Generated passwords are in `~/.tarjepafi-secrets.env` and the Neon values in `~/.tarjepafi-neon.env`, both outside the
repository. Since phase 3 the backend declares the `tarjepafi.devices` exchange itself, so after a broker (re)start the
reader user appears within seconds once the backend has reconnected (open `/actuator/health` once to make it reconnect
if nobody else is using it). Verified on 2026-11-23 by restarting the ACI container.

## What a person must do (an agent cannot)

1. **Neon**: create an account and a project (closest region offered, PostgreSQL 17; already created by the team), copy the direct connection
   string. Do not paste it in the repository or in chat; it goes to the App Service settings.
2. **GitHub**: after the first `ci` run on `develop`, open the two packages (`tarjepafi-backend`, `tarjepafi-rabbitmq`)
   under the organisation's Packages, link them to the repository and set the visibility to **public**.
3. **GitHub repository settings** (only once the backend's App Service app exists): variable `AZURE_WEBAPP_NAME`, secret
   `AZURE_WEBAPP_PUBLISH_PROFILE` (App Service, Download publish profile), environment `demo`.
4. `az login` with the Azure for Students subscription (done by the team on 2026-11-16).

## Create (in this order)

Pick a short unique `SUFFIX` (lower case letters and digits). Generate every password with a password manager.

```bash
RG=rg-tarjepafi-demo; LOC=chilecentral; SUFFIX=<suffix>
az provider register -n Microsoft.ContainerInstance        # free, once per subscription
az group create -n $RG -l $LOC --tags purpose=showcase delete-after=<yyyy-mm-dd>

# 1. Backend (must exist first: it declares the tarjepafi.devices exchange the reader user depends on)
az appservice plan create -g $RG -n asp-tarjepafi-demo --is-linux --sku B1 -l $LOC
az webapp create -g $RG -p asp-tarjepafi-demo -n tarjepafi-api-$SUFFIX \
  --deployment-container-image-name ghcr.io/arquiemergentes/tarjepafi-backend:develop
az webapp update -g $RG -n tarjepafi-api-$SUFFIX --https-only true
```

```bash
# 2. Broker
az container create -g $RG -n aci-tarjepafi-broker -l $LOC --os-type Linux --cpu 1 --memory 1.5 \
  --image ghcr.io/arquiemergentes/tarjepafi-rabbitmq:develop \
  --ip-address Public --dns-name-label tarjepafi-broker-$SUFFIX --ports 5672 1883 --protocol TCP \
  --environment-variables RABBITMQ_DEFAULT_USER=tarjepafi-backend READER_USERNAME=reader-demo \
  --secure-environment-variables RABBITMQ_DEFAULT_PASS=<backend-pass> READER_PASSWORD=<reader-pass>
# Broker host: tarjepafi-broker-$SUFFIX.$LOC.azurecontainer.io. The management UI (15672) is not published.
```

Backend app settings (names only; values come from Neon and the password manager):

| Setting | Value |
|---|---|
| `WEBSITES_PORT` | `8080` |
| `SPRING_PROFILES_ACTIVE` | `demo` (simulated clock and the demo seed; `dev` must **not** be active) |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Optional (phase 8): the SMTP server for the e-mail of a released reservation. Without a host the e-mail is only logged. Locally `docker compose up -d` starts Mailpit (SMTP 1025, inbox http://localhost:8025). |
| `TARJEPAFI_DEMO_WOKWI_TAGS` | `true` to re-point the demo cards at the Wokwi preset tags (Blue `01:02:03:04` student U202312966, NFC Tag `04:11:22:33` teacher D0001 (the part is documented with 7 bytes but the simulated reader returns 4), Green `11:22:33:44` staff T0001, Yellow `55:66:77:88` blocked card; Red and Key Fob stay unregistered). The old seed UIDs stop working while it is on. Default off. |
| `JWT_SECRET` | 32+ random bytes |
| `DB_POOL_SIZE` | Database connections of the app; 10 by default since phase 9 (the consumers of the taps run several at a time). |
| `TARJEPAFI_AMQP_CONCURRENCY_DEVICE_EVENTS`, `..._TAP_RESOLVED`, `..._SPACE_CARD_TAP`, `..._DATA_EVENTS`, `..._ATTENDANCE_CARD_TAP` | Optional: consumers per queue as `min-max` (defaults 4-8, 2-4, 2-4, 2-4 and 1). See `docs/PERFORMANCE.md`. |
| `TARJEPAFI_SECURITY_SIGN_IN_MAX_FAILURES_PER_EMAIL`, `..._MAX_FAILURES_PER_CLIENT`, `..._WINDOW_SECONDS`, `..._LOCK_SECONDS` | Optional: the sign-in lock (defaults 5, 30, 900 and 300). |
| `DB_URL` | `jdbc:postgresql://<neon-direct-host>/<db>?sslmode=require` |
| `DB_USERNAME`, `DB_PASSWORD` | from Neon |
| `RABBITMQ_HOST` | the ACI DNS name above |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | `tarjepafi-backend` and `<backend-pass>` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4781` (the frontend dev port, stated in `docs/API.md`) plus the frontend owner's deployed origin, comma-separated, no trailing slash |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | optional first administrator (TS01) |

The reader user only appears once the backend has started and declared `tarjepafi.devices`: **start the backend before
the first reader connects** (and after any broker restart, the backend's connection recovers by itself; the reader user is
created again by the container on its next start, see `docker/rabbitmq/tarjepafi-entrypoint.sh`).

The broker is stateless on purpose (no volume): a restart loses queued messages and recreates users from the variables.

## Verify (phase 2 Verify block)

- `https://tarjepafi-api-<suffix>.azurewebsites.net/actuator/health` is `UP` (it needs the broker running).
- `docs/http/iam.http` passes against the deployed API (sign-in, `auth/me`, 401, 403); a CORS preflight from a listed origin is accepted.
- From any machine, with the `reader-demo` user: `mosquitto_pub` to `tarjepafi/readers/test/tap` and `mosquitto_sub` on
  `tarjepafi/readers/test/display` work; publishing to any other topic drops the connection; subscribing to `#` is denied;
  a wrong password is refused. (Same checks were run against the image locally on 2026-11-16.)

## Start, stop, cost

| Action | Command | Billing |
|---|---|---|
| Stop the broker | `az container stop -g $RG -n aci-tarjepafi-broker` | stops (about $0.05/h while running) |
| Start it | `az container start -g $RG -n aci-tarjepafi-broker` | the DNS name stays; the public IP may change |
| Stop the backend | `az webapp stop -g $RG -n tarjepafi-api-$SUFFIX` | **the B1 plan keeps billing** (about $0.018/h) until deleted |
| Pause everything cheaply | stop both, or delete the plan and recreate it (a minute) | |

Start order: broker, then backend. With the broker stopped the backend's health is `DOWN` (it checks RabbitMQ).

## Teardown (after the showcase)

1. `az group delete -n rg-tarjepafi-demo --yes --no-wait` removes every Azure resource at once. Confirm with
   `az group exists -n rg-tarjepafi-demo`.
2. Delete the Neon project.
3. Delete the two GHCR packages (organisation, Packages, Package settings) and the repository variable and secret of
   step 3 above.
4. Rotate or discard the `reader-demo` password (it was in a public Wokwi project) and the Wokwi project itself.
5. Record the teardown date in `PROGRESS.md`.
