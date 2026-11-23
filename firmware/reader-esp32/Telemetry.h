// Periodic Heartbeat and TelemetryCaptured messages, plus an immediate announcement after every (re)connection.
#pragma once

#include <Arduino.h>

#include "EventPublisher.h"
#include "ReaderNetwork.h"
#include "TapBuffer.h"

class Telemetry {
 public:
  Telemetry(EventPublisher &publisher, ReaderNetwork &network, TapBuffer &buffer);

  /**
   * Sends a heartbeat and a telemetry sample right away. Called after a new MQTT session and before the buffer is
   * flushed, so the backend sees the reader ACTIVE again before the buffered taps arrive.
   */
  bool announce(unsigned long now);

  /** Sends whatever is due. Does nothing while offline or before NTP has set the clock. */
  void loop(unsigned long now);

 private:
  bool sendTelemetry();

  EventPublisher &publisher_;
  ReaderNetwork &network_;
  TapBuffer &buffer_;
  unsigned long lastHeartbeatAt_ = 0;
  unsigned long lastTelemetryAt_ = 0;
};
