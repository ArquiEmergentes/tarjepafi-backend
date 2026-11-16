#!/bin/sh
# Starts RabbitMQ through the official entrypoint and, once the node answers, creates the restricted reader user.
#
# READER_USERNAME / READER_PASSWORD: when both are set, an MQTT user that can only publish to and read from the
# topics `tarjepafi/readers/#` (routing keys `tarjepafi.readers.#` on the `tarjepafi.devices` exchange). Its
# credentials end up in a public Wokwi project, so it must never be able to touch anything else.
# The backend user comes from RABBITMQ_DEFAULT_USER / RABBITMQ_DEFAULT_PASS (official image behaviour).
set -eu

# The official entrypoint starts as root and re-executes itself as `rabbitmq`. The control commands must run as that
# user too: as root they would create ~/.erlang.cookie with the wrong owner and the broker could not read it.
as_rabbitmq() {
  if [ "$(id -u)" = "0" ]; then su-exec rabbitmq "$@"; else "$@"; fi
}

create_reader_user() {
  # Never let a CLI create the cookie: only the broker node may (it does so on its first start, as `rabbitmq`).
  until [ -s /var/lib/rabbitmq/.erlang.cookie ]; do sleep 1; done
  # `ping` succeeds while the Erlang node is up but the broker app is not; check_running waits for the app.
  until as_rabbitmq rabbitmq-diagnostics -q check_running >/dev/null 2>&1; do sleep 2; done
  # Topic permissions can only be set on an exchange that exists, and the backend declares `tarjepafi.devices` when it
  # starts. Wait for it instead of creating the user first: a reader user without its topic restriction can publish to
  # any routing key. Until the backend has started, the reader user simply does not exist and no reader can connect.
  until as_rabbitmq rabbitmqctl -q list_exchanges -p / name 2>/dev/null | grep -qx tarjepafi.devices; do sleep 2; done
  name="$READER_USERNAME"
  if as_rabbitmq rabbitmqctl -q list_users | cut -f1 | grep -qx "$name"; then
    as_rabbitmq rabbitmqctl -q change_password "$name" "$READER_PASSWORD"
  else
    as_rabbitmq rabbitmqctl -q add_user "$name" "$READER_PASSWORD"
  fi
  as_rabbitmq rabbitmqctl -q set_user_tags "$name"   # no management tag
  # Resource permissions: the MQTT plugin creates `mqtt-subscription-<clientId>qos<n>` queues for the client, and the
  # reader publishes to / consumes from the device exchange only.
  as_rabbitmq rabbitmqctl -q set_permissions -p / "$name" \
    '^mqtt-subscription-.*$' \
    '^(tarjepafi\.devices|mqtt-subscription-.*)$' \
    '^(tarjepafi\.devices|mqtt-subscription-.*)$'
  # Topic permissions: only routing keys under tarjepafi.readers.
  as_rabbitmq rabbitmqctl -q set_topic_permissions -p / "$name" tarjepafi.devices \
    '^tarjepafi\.readers\..*$' '^tarjepafi\.readers\..*$'
  echo "tarjepafi: reader user '$name' ready"
}

if [ -n "${READER_USERNAME:-}" ] && [ -n "${READER_PASSWORD:-}" ]; then
  # The subshell keeps `set -e` active and the trap makes a failure visible in the container log.
  (
    trap 'rc=$?; [ "$rc" -eq 0 ] || echo "tarjepafi: reader user setup FAILED (exit $rc)" >&2' EXIT
    create_reader_user
  ) &
fi

exec docker-entrypoint.sh "$@"
