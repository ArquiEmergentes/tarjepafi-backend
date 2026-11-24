package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.messaging;

import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.HeartbeatReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.ReaderErrorReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.TapReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.TelemetryReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.CardTapEvent;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.IncidentType;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TapSource;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Anti-corruption layer between the device contract (v1: JSON, UTC instants, snake of the plan's section
 * 4) and the application. It validates a payload and converts its times to campus time (D7, D8).
 * The reader id of the routing key {@code tarjepafi.readers.<id>.<kind>} must match the payload.
 */
@Component
public class DevicePayloadAdapter {

  private static final int CONTRACT_VERSION = 1;

  private final ObjectMapper mapper;
  private final CampusClock clock;

  public DevicePayloadAdapter(ObjectMapper mapper, CampusClock clock) {
    this.mapper = mapper;
    this.clock = clock;
  }

  /**
   * @param routingKey the key the message arrived with, for example {@code tarjepafi.readers.RDR-A-301.tap}
   * @throws MalformedDevicePayloadException when the key or the payload break the contract
   */
  public DeviceMessage parse(String routingKey, byte[] body) {
    String[] parts = routingKey == null ? new String[0] : routingKey.split("\\.");
    if (parts.length != 4 || !parts[0].equals("tarjepafi") || !parts[1].equals("readers")) {
      throw new MalformedDevicePayloadException("Unexpected routing key " + routingKey);
    }
    String readerId = parts[2];
    JsonNode json;
    try {
      json = mapper.readTree(body);
    } catch (JacksonException exception) {
      throw new MalformedDevicePayloadException("The payload is not JSON", exception);
    }
    if (json == null || !json.isObject()) {
      throw new MalformedDevicePayloadException("The payload is not a JSON object");
    }
    if (json.path("v").asInt(-1) != CONTRACT_VERSION) {
      throw new MalformedDevicePayloadException("Unsupported contract version " + json.path("v"));
    }
    if (!readerId.equals(text(json, "readerId"))) {
      throw new MalformedDevicePayloadException("The readerId does not match the routing key");
    }
    return switch (parts[3]) {
      case "tap" -> tap(json, readerId);
      case "telemetry" -> telemetry(json, readerId);
      case "heartbeat" -> heartbeat(json, readerId);
      case "error" -> error(json, readerId);
      default -> throw new MalformedDevicePayloadException("Unknown message kind " + parts[3]);
    };
  }

  private TapReceived tap(JsonNode json, String readerId) {
    expectType(json, "CardTapped");
    String eventId = required(json, "eventId");
    if (eventId.length() > 64) {
      throw new MalformedDevicePayloadException("eventId is too long");
    }
    String uid = CardTapEvent.normalizeUid(required(json, "cardUid"));
    if (!CardTapEvent.isValidUid(uid)) {
      throw new MalformedDevicePayloadException("cardUid is not a valid UID");
    }
    boolean buffered = json.path("buffered").asBoolean(false);
    TapSource source = "SIMULATOR".equals(text(json, "source")) ? TapSource.SIMULATOR : TapSource.READER;
    return new TapReceived(eventId, readerId, uid, buffered ? instant(json, "tappedAt") : live(json, "tappedAt"), source, buffered);
  }

  private TelemetryReceived telemetry(JsonNode json, String readerId) {
    expectType(json, "TelemetryCaptured");
    JsonNode signal = json.path("signalStrength");
    JsonNode uptime = json.path("uptime");
    if (!signal.isIntegralNumber() || signal.asInt() > 0 || signal.asInt() < -150) {
      throw new MalformedDevicePayloadException("signalStrength must be a dBm value between -150 and 0");
    }
    if (!uptime.isIntegralNumber() || uptime.asLong() < 0) {
      throw new MalformedDevicePayloadException("uptime must be a non-negative number of seconds");
    }
    return new TelemetryReceived(
        readerId, live(json, "capturedAt"), signal.asInt(), uptime.asLong(), required(json, "firmwareVersion"));
  }

  private HeartbeatReceived heartbeat(JsonNode json, String readerId) {
    expectType(json, "Heartbeat");
    return new HeartbeatReceived(readerId, live(json, "sentAt"));
  }

  private ReaderErrorReceived error(JsonNode json, String readerId) {
    expectType(json, "ReaderError");
    IncidentType type;
    try {
      type = IncidentType.valueOf(required(json, "code"));
    } catch (IllegalArgumentException exception) {
      throw new MalformedDevicePayloadException("Unknown error code " + json.path("code"));
    }
    return new ReaderErrorReceived(readerId, type, required(json, "message"), live(json, "occurredAt"));
  }

  private static void expectType(JsonNode json, String expected) {
    if (!expected.equals(text(json, "type"))) {
      throw new MalformedDevicePayloadException("Expected a " + expected + " message");
    }
  }

  private static String text(JsonNode json, String field) {
    JsonNode node = json.path(field);
    return node.isString() ? node.asString() : null;
  }

  private static String required(JsonNode json, String field) {
    String value = text(json, field);
    if (value == null || value.isBlank()) {
      throw new MalformedDevicePayloadException(field + " is required");
    }
    return value.trim();
  }

  /** A UTC instant such as {@code 2026-11-15T15:04:05Z}, shifted to campus time by the clock. */
  private LocalDateTime instant(JsonNode json, String field) {
    try {
      return clock.fromDeviceInstant(Instant.parse(required(json, field)));
    } catch (DateTimeParseException exception) {
      throw new MalformedDevicePayloadException(field + " is not a UTC instant", exception);
    }
  }

  /**
   * A message the reader sends as it happens (live tap, heartbeat, telemetry, error): the device time is validated
   * but campus time is the clock's now. A device clock that drifts from real time (the Wokwi simulator runs slower
   * than real time) would otherwise date every heartbeat further in the past, and with the simulated clock the
   * reader would stay OFFLINE. Only a tap buffered offline needs its own time ({@link #instant}).
   */
  private LocalDateTime live(JsonNode json, String field) {
    try {
      Instant.parse(required(json, field));
    } catch (DateTimeParseException exception) {
      throw new MalformedDevicePayloadException(field + " is not a UTC instant", exception);
    }
    return clock.now();
  }
}
