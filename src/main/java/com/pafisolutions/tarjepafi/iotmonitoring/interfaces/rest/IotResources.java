package com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest;

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
      String readerMode, int communicationInterval, int heartbeatInterval, String firmwareVersion) {}

  /**
   * A reader. {@code status}: REGISTERED, ACTIVE, DEGRADED, OFFLINE, INACTIVE or MAINTENANCE.
   * {@code deviceType}: CLASSROOM_READER, TURNSTILE, DOOR_READER or DESK_READER.
   * {@code availableActions} from ACTIVATE, DEACTIVATE, START_MAINTENANCE, END_MAINTENANCE.
   */
  public record ReaderResource(
      String id,
      String name,
      String deviceType,
      String spaceId,
      String status,
      ConfigurationResource configuration,
      LocalDateTime registeredAt,
      LocalDateTime lastSeenAt,
      List<String> availableActions) {

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
      String id,
      String readerId,
      LocalDateTime capturedAt,
      int signalStrength,
      long uptime,
      String firmwareVersion,
      String status) {

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
      String id,
      String readerId,
      String type,
      LocalDateTime detectedAt,
      String description,
      LocalDateTime resolvedAt) {

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
      String id,
      String cursor,
      String readerId,
      String cardUid,
      LocalDateTime tappedAt,
      String outcome,
      String tone,
      String messageKey,
      Map<String, Object> messageParams,
      String displayMessage,
      String source,
      boolean buffered) {

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
  public record TapEventPage(List<TapEventResource> items, String nextCursor) {}

  /** {@code errors} lists the rules that failed, see the error table of the API guide. */
  public record ConfigurationRequest(
      ReaderMode readerMode,
      Integer communicationInterval,
      Integer heartbeatInterval,
      String firmwareVersion) {}

  public record RegisterReaderRequest(
      @NotBlank String id,
      @NotBlank String name,
      @NotNull DeviceType deviceType,
      @NotBlank String spaceId,
      @NotNull @Valid ConfigurationRequest configuration) {}

  public record TapRequest(@NotBlank String cardUid) {}
}
