package com.pafisolutions.tarjepafi.datamanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pafisolutions.tarjepafi.datamanagement.application.DataIngestionService.Fact;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataRegistrationException;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataType;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.SourceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Which integration events are facts, and what each records. */
class DataFactsTest {

  private static final LocalDateTime AT = LocalDateTime.parse("2026-10-07T10:00:00");

  private static Fact single(String routingKey, Map<String, Object> data) {
    List<Fact> facts = DataFacts.from(routingKey, AT, data);
    assertThat(facts).hasSize(1);
    return facts.get(0);
  }

  @Test
  void from_cardTap_isAnAcceptedReadingOfTheReaderAtItsOwnTime() {
    Fact fact =
        single(
            "iot.card-tap",
            Map.of("readerId", "RDR-A-301", "cardUid", "01:02:03:04", "tappedAt", "2026-10-07T09:59:58"));

    assertThat(fact.source()).isEqualTo(SourceContext.IOT_MONITORING);
    assertThat(fact.type()).isEqualTo(DataType.CARD_READING);
    assertThat(fact.subjectId()).isEqualTo("RDR-A-301");
    assertThat(fact.occurredAt()).isEqualTo(LocalDateTime.parse("2026-10-07T09:59:58"));
    assertThat(fact.payload()).containsEntry("outcome", "ACCEPTED").containsEntry("cardUid", "01:02:03:04");
  }

  @Test
  void from_tapRejected_isARejectedReadingWithItsReason() {
    Fact fact =
        single(
            "iot.tap-rejected",
            Map.of("readerId", "RDR-A-301", "cardUid", "AA:BB:CC:DD", "reason", "CARD_NOT_REGISTERED"));

    assertThat(fact.type()).isEqualTo(DataType.CARD_READING);
    assertThat(fact.occurredAt()).isEqualTo(AT);
    assertThat(fact.payload()).containsEntry("outcome", "REJECTED").containsEntry("reason", "CARD_NOT_REGISTERED");
  }

  @Test
  void from_attendanceEvents_areAttendanceFactsOfTheMark() {
    for (String key : List.of("attendance.recorded", "attendance.confirmed", "attendance.edited")) {
      Fact fact =
          single(
              key,
              Map.of(
                  "attendanceRecordId", "att-1",
                  "sessionId", "ses-02-7",
                  "role", "STUDENT",
                  "status", "PRESENT"));

      assertThat(fact.type()).isEqualTo(DataType.ATTENDANCE);
      assertThat(fact.subjectId()).isEqualTo("att-1");
      assertThat(fact.payload()).containsEntry("role", "STUDENT").containsEntry("status", "PRESENT");
    }
  }

  @Test
  void from_accessEvents_areAccessFacts() {
    Fact fact =
        single(
            "space.access-denied",
            Map.of(
                "accessRecordId", "acc-1",
                "decision", "DENIED",
                "denialReason", "UNAUTHORIZED_ROLE",
                "accessType", "ROOM",
                "spaceId", "sp-lab-e",
                "attemptedAt", "2026-10-07T15:00:01"));

    assertThat(fact.source()).isEqualTo(SourceContext.SPACE_AND_FACILITY);
    assertThat(fact.type()).isEqualTo(DataType.ACCESS);
    assertThat(fact.occurredAt()).isEqualTo(LocalDateTime.parse("2026-10-07T15:00:01"));
    assertThat(fact.payload()).containsEntry("decision", "DENIED");
  }

  @Test
  void from_workShiftClosed_usesTheExitTimeAndKeepsTheWorkedMinutes() {
    Fact fact =
        single(
            "space.work-shift-closed",
            Map.of(
                "workShiftId", "ws-1",
                "status", "CLOSED",
                "entryAt", "2026-10-07T06:56:00",
                "exitAt", "2026-10-07T15:00:00",
                "workedMinutes", 484));

    assertThat(fact.type()).isEqualTo(DataType.WORK_SHIFT);
    assertThat(fact.occurredAt()).isEqualTo(LocalDateTime.parse("2026-10-07T15:00:00"));
    assertThat(fact.payload()).containsEntry("workedMinutes", 484);
  }

  @Test
  void from_credentialStatusChanged_isACredentialChange() {
    Fact fact =
        single(
            "credentials.credential-status-changed",
            Map.of("credentialId", "cred-1", "fromStatus", "ACTIVE", "toStatus", "BLOCKED", "reason", "LOST"));

    assertThat(fact.source()).isEqualTo(SourceContext.CREDENTIALS);
    assertThat(fact.type()).isEqualTo(DataType.CREDENTIAL_CHANGE);
    assertThat(fact.subjectId()).isEqualTo("cred-1");
  }

  @Test
  void from_incidentOpened_isDeviceTelemetryWithTheIncidentType() {
    Fact fact =
        single(
            "iot.incident-opened",
            Map.of("incidentId", "inc-1", "readerId", "RDR-T-002", "type", "DEVICE_OFFLINE", "detectedAt", "2026-10-07T10:03:00"));

    assertThat(fact.type()).isEqualTo(DataType.DEVICE_TELEMETRY);
    assertThat(fact.subjectId()).isEqualTo("RDR-T-002");
    assertThat(fact.payload()).containsEntry("incidentType", "DEVICE_OFFLINE");
  }

  @Test
  void from_eventsThatAreNotFacts_areIgnored() {
    for (String key :
        List.of("attendance.tap-resolved", "attendance.session-closed", "iot.reader-status-changed", "space.tap-resolved")) {
      assertThat(DataFacts.from(key, AT, Map.of("readerId", "RDR-A-301"))).isEmpty();
    }
  }

  @Test
  void from_aMomentThatIsNotADateTime_isARegistrationError() {
    assertThatThrownBy(
            () -> DataFacts.from("iot.card-tap", AT, Map.of("readerId", "RDR-A-301", "tappedAt", "yesterday")))
        .isInstanceOf(DataRegistrationException.class);
  }
}
