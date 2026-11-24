#include "Telemetry.h"

#include "ClockUtil.h"
#include "ReaderSettings.h"

Telemetry::Telemetry(EventPublisher &publisher, ReaderNetwork &network, TapBuffer &buffer)
    : publisher_(publisher), network_(network), buffer_(buffer) {}

bool Telemetry::sendTelemetry() {
  return publisher_.publishTelemetry(network_.signalStrength(), millis() / 1000, buffer_.size(), buffer_.dropped());
}

bool Telemetry::announce(unsigned long now) {
  if (!network_.online() || !clockutil::synced()) {
    return false;
  }
  bool ok = publisher_.publishHeartbeat() && sendTelemetry();
  if (ok) {
    lastHeartbeatAt_ = now;
    lastTelemetryAt_ = now;
  }
  return ok;
}

void Telemetry::loop(unsigned long now) {
  if (!network_.online() || !clockutil::synced()) {
    return;
  }
  if (now - lastHeartbeatAt_ >= settings::HEARTBEAT_INTERVAL_MS) {
    lastHeartbeatAt_ = now;
    publisher_.publishHeartbeat();
  }
  if (now - lastTelemetryAt_ >= settings::TELEMETRY_INTERVAL_MS) {
    lastTelemetryAt_ = now;
    sendTelemetry();
  }
}
