// UTC wall-clock helpers. The time comes from NTP (configured by ReaderNetwork once Wi-Fi is up).
#pragma once

#include <Arduino.h>

namespace clockutil {

/** True once NTP has set the clock (any time after 2023-11-14). */
bool synced();

/** Milliseconds since the Unix epoch, UTC. Meaningless until synced(). */
uint64_t nowMs();

/** Formats epoch milliseconds as an ISO 8601 UTC instant, for example 2026-11-23T15:04:05.123Z. */
void formatIso(uint64_t epochMs, char *out, size_t size);

}  // namespace clockutil
