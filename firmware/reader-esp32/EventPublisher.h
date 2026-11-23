// Builds the device contract v1 payloads (plan section 4) and publishes them through ReaderNetwork.
#pragma once

#include <Arduino.h>

#include "ReaderNetwork.h"

class EventPublisher {
 public:
  EventPublisher(ReaderNetwork &network, const char *readerId);

  /** A random UUID v4 such as 8f0c2d4e-1b7a-4c3e-9f21-0a1b2c3d4e5f (37 bytes with the terminator). */
  static void newEventId(char out[37]);

  /** tarjepafi/readers/{id}/tap, QoS 1. */
  bool publishTap(const char *eventId, const char *cardUid, uint64_t tappedAtMs, bool buffered);

  /** tarjepafi/readers/{id}/telemetry, QoS 1. droppedCount is an optional field the backend ignores today. */
  bool publishTelemetry(int signalStrength, uint32_t uptimeS, uint32_t bufferedCount, uint32_t droppedCount);

  /** tarjepafi/readers/{id}/heartbeat, QoS 1. */
  bool publishHeartbeat();

  /** tarjepafi/readers/{id}/error, QoS 1. code is an incident type such as HARDWARE_FAILURE. */
  bool publishError(const char *code, const char *message, uint64_t occurredAtMs);

 private:
  ReaderNetwork &network_;
  const char *readerId_;
};
