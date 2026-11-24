package com.pafisolutions.tarjepafi.datamanagement.application;

import com.pafisolutions.tarjepafi.datamanagement.application.DataIngestionService.Fact;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataRegistrationException;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataType;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.SourceContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Which integration events are facts, and what each one records. The events are published by the other
 * contexts; their data are plain JSON types (see each context's events).
 */
final class DataFacts {

  private DataFacts() {}

  static List<Fact> from(String routingKey, LocalDateTime envelopeAt, Map<String, Object> data) {
    if (routingKey == null || data == null) {
      return DataIngestionService.none();
    }
    return switch (routingKey) {
      case "iot.card-tap" ->
          List.of(
              new Fact(
                  SourceContext.IOT_MONITORING,
                  DataType.CARD_READING,
                  text(data, "readerId"),
                  time(data, "tappedAt", envelopeAt),
                  DataIngestionService.payload(
                      "cardUid", text(data, "cardUid"),
                      "outcome", "ACCEPTED",
                      "spaceId", text(data, "spaceId"),
                      "cardHolderId", text(data, "cardHolderId"))));
      case "iot.tap-rejected" ->
          List.of(
              new Fact(
                  SourceContext.IOT_MONITORING,
                  DataType.CARD_READING,
                  text(data, "readerId"),
                  time(data, "tappedAt", envelopeAt),
                  DataIngestionService.payload(
                      "cardUid", text(data, "cardUid"),
                      "outcome", "REJECTED",
                      "reason", text(data, "reason"),
                      "spaceId", text(data, "spaceId"))));
      case "iot.incident-opened" ->
          List.of(
              new Fact(
                  SourceContext.IOT_MONITORING,
                  DataType.DEVICE_TELEMETRY,
                  text(data, "readerId"),
                  time(data, "detectedAt", envelopeAt),
                  DataIngestionService.payload(
                      "incidentType", text(data, "type"), "incidentId", text(data, "incidentId"))));
      case "attendance.recorded", "attendance.confirmed", "attendance.edited" ->
          List.of(
              new Fact(
                  SourceContext.ACADEMIC_ATTENDANCE,
                  DataType.ATTENDANCE,
                  text(data, "attendanceRecordId"),
                  envelopeAt,
                  DataIngestionService.payload(
                      "sessionId", text(data, "sessionId"),
                      "role", text(data, "role"),
                      "status", text(data, "status"))));
      case "space.access-granted", "space.access-denied" ->
          List.of(
              new Fact(
                  SourceContext.SPACE_AND_FACILITY,
                  DataType.ACCESS,
                  text(data, "accessRecordId"),
                  time(data, "attemptedAt", envelopeAt),
                  DataIngestionService.payload(
                      "decision", text(data, "decision"),
                      "denialReason", text(data, "denialReason"),
                      "accessType", text(data, "accessType"),
                      "spaceId", text(data, "spaceId"))));
      case "space.work-shift-opened", "space.work-shift-closed" ->
          List.of(
              new Fact(
                  SourceContext.SPACE_AND_FACILITY,
                  DataType.WORK_SHIFT,
                  text(data, "workShiftId"),
                  time(data, data.containsKey("exitAt") ? "exitAt" : "entryAt", envelopeAt),
                  DataIngestionService.payload(
                      "status", text(data, "status"), "workedMinutes", data.get("workedMinutes"))));
      case "credentials.credential-status-changed" ->
          List.of(
              new Fact(
                  SourceContext.CREDENTIALS,
                  DataType.CREDENTIAL_CHANGE,
                  text(data, "credentialId"),
                  envelopeAt,
                  DataIngestionService.payload(
                      "fromStatus", text(data, "fromStatus"),
                      "toStatus", text(data, "toStatus"),
                      "reason", text(data, "reason"))));
      default -> DataIngestionService.none();
    };
  }

  private static String text(Map<String, Object> data, String field) {
    Object value = data.get(field);
    return value == null ? null : String.valueOf(value);
  }

  private static LocalDateTime time(Map<String, Object> data, String field, LocalDateTime fallback) {
    String value = text(data, field);
    if (value == null) {
      return fallback;
    }
    try {
      return LocalDateTime.parse(value);
    } catch (DateTimeParseException exception) {
      throw new DataRegistrationException(field + " is not a campus date-time: " + value);
    }
  }
}
