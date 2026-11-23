package com.pafisolutions.tarjepafi.iotmonitoring.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Port of {@code iot-domain.spec.ts} and {@code iot-events-domain.spec.ts} of the old web app (rule 6.C). */
class IotDomainTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-10-07T09:50:00");

  private static Reader reader(ReaderStatus status, LocalDateTime lastSeenAt) {
    return new Reader(
        "RDR-A-301",
        "Lector Aula A-301",
        DeviceType.CLASSROOM_READER,
        "sp-a301",
        status,
        new ReaderConfiguration(ReaderMode.ATTENDANCE, 30, 60, "2.4.1"),
        LocalDateTime.parse("2026-08-10T09:00:00"),
        lastSeenAt);
  }

  @Test
  void isValidId_followsTheRdrPattern() {
    assertThat(Reader.isValidId("RDR-A-304")).isTrue();
    assertThat(Reader.isValidId("RDR-AB-304")).isFalse();
    assertThat(Reader.isValidId("rdr-a-304")).isFalse();
    assertThat(Reader.isValidId(null)).isFalse();
  }

  @Test
  void canProcessTaps_onlyActiveOrDegraded() {
    assertThat(reader(ReaderStatus.ACTIVE, NOW).canProcessTaps()).isTrue();
    assertThat(reader(ReaderStatus.DEGRADED, NOW).canProcessTaps()).isTrue();
    for (ReaderStatus status :
        List.of(
            ReaderStatus.REGISTERED,
            ReaderStatus.INACTIVE,
            ReaderStatus.OFFLINE,
            ReaderStatus.MAINTENANCE)) {
      assertThat(reader(status, NOW).canProcessTaps()).as(status.name()).isFalse();
    }
  }

  @Test
  void can_activateFromRegisteredOrInactive_deactivateFromActiveDegradedOrOffline() {
    assertThat(reader(ReaderStatus.REGISTERED, null).can(ReaderAction.ACTIVATE)).isTrue();
    assertThat(reader(ReaderStatus.MAINTENANCE, null).can(ReaderAction.ACTIVATE)).isFalse();
    assertThat(reader(ReaderStatus.OFFLINE, NOW).can(ReaderAction.DEACTIVATE)).isTrue();
    assertThat(reader(ReaderStatus.INACTIVE, NOW).can(ReaderAction.DEACTIVATE)).isFalse();
    assertThat(reader(ReaderStatus.MAINTENANCE, NOW).availableActions())
        .containsExactly(ReaderAction.END_MAINTENANCE);
  }

  @Test
  void apply_activate_recordsCommunicationAtThatMoment() {
    Reader reader = reader(ReaderStatus.REGISTERED, null);

    reader.apply(ReaderAction.ACTIVATE, NOW);

    assertThat(reader.getStatus()).isEqualTo(ReaderStatus.ACTIVE);
    assertThat(reader.getLastSeenAt()).isEqualTo(NOW);
  }

  @Test
  void apply_notAllowedTransition_throws() {
    Reader reader = reader(ReaderStatus.INACTIVE, NOW);

    assertThatThrownBy(() -> reader.apply(ReaderAction.DEACTIVATE, NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void hasLostConnection_afterMoreThanThreeHeartbeats() {
    Reader reader = reader(ReaderStatus.ACTIVE, LocalDateTime.parse("2026-10-07T09:47:00"));

    assertThat(reader.hasLostConnection(LocalDateTime.parse("2026-10-07T09:50:00"))).isFalse();
    assertThat(reader.hasLostConnection(LocalDateTime.parse("2026-10-07T09:50:01"))).isTrue();
    assertThat(
            reader(ReaderStatus.INACTIVE, LocalDateTime.parse("2026-10-01T00:00:00"))
                .hasLostConnection(NOW))
        .isFalse();
    assertThat(reader(ReaderStatus.ACTIVE, null).hasLostConnection(NOW)).isTrue();
  }

  @Test
  void heartbeat_offlineReader_returnsToActive() {
    Reader reader = reader(ReaderStatus.OFFLINE, LocalDateTime.parse("2026-10-07T09:00:00"));

    reader.heartbeat(NOW);

    assertThat(reader.getStatus()).isEqualTo(ReaderStatus.ACTIVE);
    assertThat(reader.getLastSeenAt()).isEqualTo(NOW);
  }

  @Test
  void heartbeat_olderThanTheLastContact_keepsTheLastContact() {
    Reader reader = reader(ReaderStatus.ACTIVE, NOW);

    reader.heartbeat(NOW.minusMinutes(5));

    assertThat(reader.getLastSeenAt()).isEqualTo(NOW);
  }

  @Test
  void applySignal_weakSignalDegradesAndGoodSignalRestores() {
    Reader reader = reader(ReaderStatus.ACTIVE, NOW);

    reader.applySignal(true);
    assertThat(reader.getStatus()).isEqualTo(ReaderStatus.DEGRADED);
    reader.applySignal(false);
    assertThat(reader.getStatus()).isEqualTo(ReaderStatus.ACTIVE);

    Reader inMaintenance = reader(ReaderStatus.MAINTENANCE, NOW);
    inMaintenance.applySignal(true);
    assertThat(inMaintenance.getStatus()).isEqualTo(ReaderStatus.MAINTENANCE);
  }

  @Test
  void validate_acceptsAValidConfiguration() {
    assertThat(ReaderConfiguration.validate(ReaderMode.ACCESS_CONTROL, 30, 60, "2.4.1")).isEmpty();
  }

  @Test
  void validate_reportsEveryBrokenRule() {
    assertThat(ReaderConfiguration.validate(ReaderMode.ACCESS_CONTROL, 0, 60, "2.4.1"))
        .contains("COMMUNICATION_INTERVAL_INVALID");
    assertThat(ReaderConfiguration.validate(ReaderMode.ACCESS_CONTROL, 30, 20, "2.4.1"))
        .containsExactly("HEARTBEAT_LOWER_THAN_COMMUNICATION");
    assertThat(ReaderConfiguration.validate(ReaderMode.ACCESS_CONTROL, 30, 60, "2.4"))
        .containsExactly("FIRMWARE_FORMAT");
    assertThat(ReaderConfiguration.validate(null, null, null, null))
        .containsExactly(
            "MODE_REQUIRED",
            "COMMUNICATION_INTERVAL_INVALID",
            "HEARTBEAT_INTERVAL_INVALID",
            "FIRMWARE_FORMAT");
  }

  @Test
  void normalizeUid_upperCaseBytesSeparatedByColons() {
    assertThat(CardTapEvent.normalizeUid("04 3d 9b 12 8f")).isEqualTo("04:3D:9B:12:8F");
    assertThat(CardTapEvent.normalizeUid("043d9b128f")).isEqualTo("04:3D:9B:12:8F");
  }

  @Test
  void isValidUid_fourToSevenBytes() {
    assertThat(CardTapEvent.isValidUid("04:3D:9B:12")).isTrue();
    assertThat(CardTapEvent.isValidUid("04:3D:9B:12:8F:01:02")).isTrue();
    assertThat(CardTapEvent.isValidUid("04:3D:9B")).isFalse();
    assertThat(CardTapEvent.isValidUid("04:3D:9B:12:8F:01:02:03")).isFalse();
    assertThat(CardTapEvent.isValidUid("ZZ:3D:9B:12")).isFalse();
  }

  @Test
  void resolve_pendingEvent_storesTheOutcomeOnceOnly() {
    CardTapEvent event = new CardTapEvent("e1", "RDR-A-301", "04:3D:9B:12:8F", NOW, TapSource.READER);
    assertThat(event.isPending()).isTrue();

    event.resolve(TapOutcome.ATTENDANCE, "success", "tap.attendanceTaken", null, "Asistencia tomada");

    assertThat(event.getOutcome()).isEqualTo(TapOutcome.ATTENDANCE);
    assertThatThrownBy(() -> event.resolve(TapOutcome.REJECTED, "error", "k", null, "x"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void incident_resolveKeepsTheFirstResolution() {
    Incident incident =
        new Incident("i1", "RDR-T-004", IncidentType.DEVICE_OFFLINE, NOW, "Sin heartbeat", null);
    assertThat(incident.isOpen()).isTrue();

    incident.resolve(NOW.plusMinutes(5));
    incident.resolve(NOW.plusMinutes(9));

    assertThat(incident.isOpen()).isFalse();
    assertThat(incident.getResolvedAt()).isEqualTo(NOW.plusMinutes(5));
  }

  @Test
  void isWeak_belowMinus75dBm() {
    assertThat(TelemetrySample.isWeak(-76)).isTrue();
    assertThat(TelemetrySample.isWeak(-75)).isFalse();
    assertThat(TelemetrySample.isWeak(-48)).isFalse();
  }
}
