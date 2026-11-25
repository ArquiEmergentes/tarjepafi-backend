package com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.CardTapEvent;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.DeviceType;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Incident;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Reader;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderMode;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TelemetrySample;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** The JSON shapes of the IoT API. Field names follow the web app's {@code iot.resources.ts}. */
public final class IotResources {

  private IotResources() {}

  public record ConfigurationResource(
      @Schema(example = "ATTENDANCE") String readerMode, @Schema(example = "30") int communicationInterval, @Schema(example = "60") int heartbeatInterval, @Schema(example = "2.4.1") String firmwareVersion) {}

  /**
   * A reader. {@code status}: REGISTERED, ACTIVE, DEGRADED, OFFLINE, INACTIVE or MAINTENANCE.
   * {@code deviceType}: CLASSROOM_READER, TURNSTILE, DOOR_READER or DESK_READER.
   * {@code availableActions} from ACTIVATE, DEACTIVATE, START_MAINTENANCE, END_MAINTENANCE.
   */
  public record ReaderResource(
      @Schema(example = "RDR-A-301") String id,
      @Schema(example = "Lector Aula A-301") String name,
      @Schema(example = "CLASSROOM_READER") String deviceType,
      @Schema(example = "sp-a301") String spaceId,
      @Schema(example = "ACTIVE") String status,
      ConfigurationResource configuration,
      @Schema(example = "2026-08-10T09:00:00") LocalDateTime registeredAt,
      @Schema(example = "2026-10-07T09:49:30") LocalDateTime lastSeenAt,
      @Schema(example = "[\"DEACTIVATE\",\"START_MAINTENANCE\"]") List<String> availableActions) {

    public static ReaderResource from(Reader reader) {
      return new ReaderResource(
          reader.getId(),
          reader.getName(),
          reader.getDeviceType().name(),
          reader.getSpaceId(),
          reader.getStatus().name(),
          new ConfigurationResource(
              reader.getConfiguration().getReaderMode().name(),
              reader.getConfiguration().getCommunicationInterval(),
              reader.getConfiguration().getHeartbeatInterval(),
              reader.getConfiguration().getFirmwareVersion()),
          reader.getRegisteredAt(),
          reader.getLastSeenAt(),
          reader.availableActions().stream().map(Enum::name).toList());
    }
  }

  /** A reader with its latest telemetry, all its incidents and its latest taps (newest first). */
  public record ReaderDetailResource(
      ReaderResource reader,
      List<TelemetryResource> telemetry,
      List<IncidentResource> incidents,
      List<TapEventResource> recentTaps) {}

  /** {@code signalStrength} in dBm (closer to 0 is better), {@code uptime} in seconds. */
  public record TelemetryResource(
      @Schema(example = "tel-00267") String id,
      @Schema(example = "RDR-A-301") String readerId,
      @Schema(example = "2026-10-07T09:49:30") LocalDateTime capturedAt,
      @Schema(example = "-48") int signalStrength,
      @Schema(example = "186570") long uptime,
      @Schema(example = "2.4.1") String firmwareVersion,
      @Schema(example = "ACTIVE") String status) {

    public static TelemetryResource from(TelemetrySample sample) {
      return new TelemetryResource(
          sample.getId(),
          sample.getReaderId(),
          sample.getCapturedAt(),
          sample.getSignalStrength(),
          sample.getUptime(),
          sample.getFirmwareVersion(),
          sample.getStatus().name());
    }
  }

  /**
   * {@code type}: CONNECTIVITY_FAILURE, READ_ERROR, DEVICE_OFFLINE, CONFIGURATION_ERROR or HARDWARE_FAILURE.
   * {@code resolvedAt} is null while the incident is open.
   */
  public record IncidentResource(
      @Schema(example = "inc-00380") String id,
      @Schema(example = "RDR-T-002") String readerId,
      @Schema(example = "CONNECTIVITY_FAILURE") String type,
      @Schema(example = "2026-10-05T07:05:00") LocalDateTime detectedAt,
      @Schema(example = "The reader has not sent heartbeats since 2026-10-05T07:00:00.") String description,
      @Schema(example = "2026-10-05T07:20:00") LocalDateTime resolvedAt) {

    public static IncidentResource from(Incident incident) {
      return new IncidentResource(
          incident.getId(),
          incident.getReaderId(),
          incident.getType().name(),
          incident.getDetectedAt(),
          incident.getDescription(),
          incident.getResolvedAt());
    }
  }

  /**
   * A card tap. {@code outcome}: PENDING (not resolved yet), ATTENDANCE, ACCESS, WORK_SHIFT or REJECTED.
   * {@code tone}: success, info or error (null while PENDING). {@code messageKey} and {@code messageParams}
   * describe the text; {@code displayMessage} is that text in Spanish as the reader showed it.
   * {@code cursor} orders events and is what the {@code after} parameter of the list calls compares with.
   * {@code source}: READER or SIMULATOR.
   */
  public record TapEventResource(
      @Schema(example = "07b3d078-c9a5-48b6-9f91-6bcd3d880d7e") String id,
      @Schema(example = "248") String cursor,
      @Schema(example = "RDR-A-301") String readerId,
      @Schema(example = "01:02:03:04") String cardUid,
      @Schema(example = "2026-10-07T10:00:00") LocalDateTime tappedAt,
      @Schema(example = "ATTENDANCE") String outcome,
      @Schema(example = "success") String tone,
      @Schema(example = "tap.attendanceTaken") String messageKey,
      Map<String, Object> messageParams,
      @Schema(example = "Asistencia tomada · Aplicaciones Web · 10:00–11:30") String displayMessage,
      @Schema(example = "READER") String source,
      @Schema(example = "false") boolean buffered) {

    private static final TypeReference<Map<String, Object>> PARAMS = new TypeReference<>() {};

    public static TapEventResource from(CardTapEvent event, ObjectMapper mapper) {
      Map<String, Object> params = null;
      if (event.getMessageParams() != null) {
        try {
          params = mapper.readValue(event.getMessageParams(), PARAMS);
        } catch (JacksonException exception) {
          params = null;
        }
      }
      return new TapEventResource(
          event.getId(),
          event.getCursor() == null ? null : String.valueOf(event.getCursor()),
          event.getReaderId(),
          event.getCardUid(),
          event.getTappedAt(),
          event.getOutcome().name(),
          event.getTone(),
          event.getMessageKey(),
          params,
          event.getDisplayMessage(),
          event.getSource().name(),
          event.isBuffered());
    }
  }

  /** A page of tap events, newest first, and the cursor to send as {@code after} on the next poll. */
  public record TapEventPage(List<TapEventResource> items, @Schema(example = "248") String nextCursor) {}

  /** {@code errors} lists the rules that failed, see the error table of the API guide. */
  public record ConfigurationRequest(
      @Schema(example = "ATTENDANCE") ReaderMode readerMode,
      @Schema(example = "30") Integer communicationInterval,
      @Schema(example = "60") Integer heartbeatInterval,
      @Schema(example = "2.4.1") String firmwareVersion) {}

  public record RegisterReaderRequest(
      @Schema(example = "RDR-A-310") @NotBlank String id,
      @Schema(example = "Lector Aula A-310") @NotBlank String name,
      @Schema(example = "CLASSROOM_READER") @NotNull DeviceType deviceType,
      @Schema(example = "sp-a303") @NotBlank String spaceId,
      @NotNull @Valid ConfigurationRequest configuration) {}

  public record TapRequest(@Schema(example = "01:02:03:04") @NotBlank String cardUid) {}
}
