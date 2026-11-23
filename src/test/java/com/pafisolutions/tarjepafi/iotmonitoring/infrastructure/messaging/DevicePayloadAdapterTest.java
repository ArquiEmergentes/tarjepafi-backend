package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.HeartbeatReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.ReaderErrorReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.TapReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.TelemetryReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.IncidentType;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TapSource;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The device contract (v1) as the adapter reads it: valid messages, and every way to break it. */
class DevicePayloadAdapterTest {

  /** Campus time is the instant minus five hours, so the conversion is visible in the assertions. */
  private final CampusClock clock =
      new CampusClock() {
        @Override
        public LocalDateTime now() {
          return LocalDateTime.parse("2026-10-07T09:50:00");
        }

        @Override
        public LocalDateTime fromDeviceInstant(Instant deviceInstant) {
          return LocalDateTime.ofInstant(deviceInstant, java.time.ZoneOffset.ofHours(-5));
        }

        @Override
        public boolean isSimulated() {
          return false;
        }

        @Override
        public void moveTo(LocalDateTime time) {
          throw new UnsupportedOperationException();
        }
      };

  private final DevicePayloadAdapter adapter = new DevicePayloadAdapter(JsonMapper.builder().build(), clock);

  private DeviceMessage parse(String key, String json) {
    return adapter.parse(key, json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void parse_tap_convertsTheInstantToCampusTimeAndNormalizesTheUid() {
    DeviceMessage message =
        parse(
            "tarjepafi.readers.RDR-A-301.tap",
            """
            {"v":1,"type":"CardTapped","eventId":"8f0c-1","readerId":"RDR-A-301",
             "cardUid":"04:3d:9b:12:8f","tappedAt":"2026-11-15T15:04:05Z","buffered":false}
            """);

    assertThat(message)
        .isEqualTo(
            new TapReceived(
                "8f0c-1",
                "RDR-A-301",
                "04:3D:9B:12:8F",
                LocalDateTime.parse("2026-11-15T10:04:05"),
                TapSource.READER));
  }

  @Test
  void parse_tapWithSimulatorSource_keepsIt() {
    TapReceived tap =
        (TapReceived)
            parse(
                "tarjepafi.readers.RDR-A-301.tap",
                """
                {"v":1,"type":"CardTapped","eventId":"e","readerId":"RDR-A-301",
                 "cardUid":"043D9B128F","tappedAt":"2026-11-15T15:04:05Z","source":"SIMULATOR"}
                """);

    assertThat(tap.source()).isEqualTo(TapSource.SIMULATOR);
    assertThat(tap.cardUid()).isEqualTo("04:3D:9B:12:8F");
  }

  @Test
  void parse_telemetry_isReadAndTheTimeConverted() {
    TelemetryReceived telemetry =
        (TelemetryReceived)
            parse(
                "tarjepafi.readers.RDR-A-301.telemetry",
                """
                {"v":1,"type":"TelemetryCaptured","readerId":"RDR-A-301","capturedAt":"2026-11-15T15:04:30Z",
                 "signalStrength":-48,"uptime":1234,"firmwareVersion":"2.4.1","bufferedCount":0}
                """);

    assertThat(telemetry.signalStrength()).isEqualTo(-48);
    assertThat(telemetry.uptime()).isEqualTo(1234);
    assertThat(telemetry.capturedAt()).isEqualTo(LocalDateTime.parse("2026-11-15T10:04:30"));
  }

  @Test
  void parse_heartbeatAndError_areRead() {
    assertThat(
            parse(
                "tarjepafi.readers.RDR-A-301.heartbeat",
                "{\"v\":1,\"type\":\"Heartbeat\",\"readerId\":\"RDR-A-301\",\"sentAt\":\"2026-11-15T15:04:30Z\"}"))
        .isInstanceOf(HeartbeatReceived.class);
    ReaderErrorReceived error =
        (ReaderErrorReceived)
            parse(
                "tarjepafi.readers.RDR-A-301.error",
                """
                {"v":1,"type":"ReaderError","readerId":"RDR-A-301","code":"HARDWARE_FAILURE",
                 "message":"PN532 not found","occurredAt":"2026-11-15T15:04:30Z"}
                """);
    assertThat(error.type()).isEqualTo(IncidentType.HARDWARE_FAILURE);
  }

  @Test
  void parse_brokenPayloads_areMalformed() {
    String key = "tarjepafi.readers.RDR-A-301.tap";
    String ok = "\"v\":1,\"type\":\"CardTapped\",\"eventId\":\"e\",\"readerId\":\"RDR-A-301\"";
    for (String json :
        new String[] {
          "not json",
          "[]",
          "{}",
          "{\"v\":2,\"type\":\"CardTapped\"}",
          "{" + ok + ",\"cardUid\":\"04:3D:9B:12:8F\"}",
          "{" + ok + ",\"cardUid\":\"04:3D\",\"tappedAt\":\"2026-11-15T15:04:05Z\"}",
          "{" + ok + ",\"cardUid\":\"04:3D:9B:12:8F\",\"tappedAt\":\"yesterday\"}",
          "{\"v\":1,\"type\":\"Telemetry\",\"eventId\":\"e\",\"readerId\":\"RDR-A-301\",\"cardUid\":\"04:3D:9B:12:8F\",\"tappedAt\":\"2026-11-15T15:04:05Z\"}",
          "{\"v\":1,\"type\":\"CardTapped\",\"eventId\":\"e\",\"readerId\":\"RDR-A-999\",\"cardUid\":\"04:3D:9B:12:8F\",\"tappedAt\":\"2026-11-15T15:04:05Z\"}"
        }) {
      assertThatThrownBy(() -> parse(key, json))
          .as(json)
          .isInstanceOf(MalformedDevicePayloadException.class);
    }
  }

  @Test
  void parse_unexpectedRoutingKeyOrKind_isMalformed() {
    String json = "{\"v\":1,\"type\":\"Heartbeat\",\"readerId\":\"RDR-A-301\",\"sentAt\":\"2026-11-15T15:04:30Z\"}";
    assertThatThrownBy(() -> parse("other.topic", json)).isInstanceOf(MalformedDevicePayloadException.class);
    assertThatThrownBy(() -> parse("tarjepafi.readers.RDR-A-301.display", json))
        .isInstanceOf(MalformedDevicePayloadException.class);
  }

  @Test
  void parse_telemetryOutOfRange_isMalformed() {
    assertThatThrownBy(
            () ->
                parse(
                    "tarjepafi.readers.RDR-A-301.telemetry",
                    "{\"v\":1,\"type\":\"TelemetryCaptured\",\"readerId\":\"RDR-A-301\",\"capturedAt\":\"2026-11-15T15:04:30Z\",\"signalStrength\":12,\"uptime\":5,\"firmwareVersion\":\"2.4.1\"}"))
        .isInstanceOf(MalformedDevicePayloadException.class);
  }
}
