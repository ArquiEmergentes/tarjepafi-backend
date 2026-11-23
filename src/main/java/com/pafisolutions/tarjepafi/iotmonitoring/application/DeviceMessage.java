package com.pafisolutions.tarjepafi.iotmonitoring.application;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.IncidentType;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TapSource;
import java.time.LocalDateTime;

/**
 * What a reader sent, already validated and with its times converted to campus time (D7, D8). The
 * infrastructure adapter builds these from the raw device payloads (device contract v1).
 */
public sealed interface DeviceMessage {

  String readerId();

  /** A card was read. {@code eventId} is unique per tap and makes delivery idempotent. */
  record TapReceived(
      String eventId, String readerId, String cardUid, LocalDateTime tappedAt, TapSource source)
      implements DeviceMessage {}

  /** Periodic telemetry; it also counts as a heartbeat. */
  record TelemetryReceived(
      String readerId,
      LocalDateTime capturedAt,
      int signalStrength,
      long uptime,
      String firmwareVersion)
      implements DeviceMessage {}

  record HeartbeatReceived(String readerId, LocalDateTime sentAt) implements DeviceMessage {}

  /** The reader reports a problem of its own. */
  record ReaderErrorReceived(
      String readerId, IncidentType type, String message, LocalDateTime occurredAt)
      implements DeviceMessage {}
}
