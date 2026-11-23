package com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest;

import com.pafisolutions.tarjepafi.iotmonitoring.application.IncidentService;
import com.pafisolutions.tarjepafi.iotmonitoring.application.ReaderService;
import com.pafisolutions.tarjepafi.iotmonitoring.application.TapEventQueryService;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.CardTapEvent;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Reader;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderAction;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.messaging.TapSimulationService;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.ConfigurationRequest;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.IncidentResource;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.ReaderDetailResource;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.ReaderResource;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.RegisterReaderRequest;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.TapEventPage;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.TapEventResource;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.TapRequest;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.TelemetryResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/** Reader management and monitoring. Every route under {@code /api/v1/iot} needs the ADMIN role. */
@RestController
@RequestMapping("/api/v1/iot/readers")
@Tag(name = "IoT readers")
public class ReaderController {

  private final ReaderService readers;
  private final IncidentService incidents;
  private final TapEventQueryService tapEvents;
  private final TapSimulationService simulation;
  private final ObjectMapper mapper;

  public ReaderController(
      ReaderService readers,
      IncidentService incidents,
      TapEventQueryService tapEvents,
      TapSimulationService simulation,
      ObjectMapper mapper) {
    this.readers = readers;
    this.incidents = incidents;
    this.tapEvents = tapEvents;
    this.simulation = simulation;
    this.mapper = mapper;
  }

  @GetMapping
  @Operation(
      summary = "List the readers ordered by id",
      description = "Optional filters: `status`, `spaceId`.")
  public List<ReaderResource> list(
      @RequestParam(required = false) ReaderStatus status,
      @RequestParam(required = false) String spaceId) {
    return readers.list(status, spaceId).stream().map(ReaderResource::from).toList();
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "A reader with its last 20 telemetry samples, its incidents and its last 20 taps",
      description = "404 READER_NOT_FOUND.")
  public ReaderDetailResource get(@PathVariable String id) {
    Reader reader = readers.get(id);
    return new ReaderDetailResource(
        ReaderResource.from(reader),
        readers.telemetry(id, 20).stream().map(TelemetryResource::from).toList(),
        incidents.list(id, null).stream().map(IncidentResource::from).toList(),
        tapEvents.list(id, null, 20).items().stream()
            .map(event -> TapEventResource.from(event, mapper))
            .toList());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Register a reader (TS05)",
      description =
          "The reader starts REGISTERED. 400 READER_ID_INVALID (the id must look like RDR-A-301), "
              + "400 READER_CONFIGURATION_INVALID (property `errors` lists MODE_REQUIRED, "
              + "COMMUNICATION_INTERVAL_INVALID, HEARTBEAT_INTERVAL_INVALID, HEARTBEAT_LOWER_THAN_COMMUNICATION, "
              + "FIRMWARE_FORMAT), 400 VALIDATION_FAILED, 404 SPACE_NOT_FOUND, 409 READER_ID_TAKEN.")
  public ReaderResource register(@Valid @RequestBody RegisterReaderRequest body) {
    ConfigurationRequest configuration = body.configuration();
    return ReaderResource.from(
        readers.register(
            body.id(),
            body.name(),
            body.deviceType(),
            body.spaceId(),
            configuration.readerMode(),
            configuration.communicationInterval(),
            configuration.heartbeatInterval(),
            configuration.firmwareVersion()));
  }

  @PutMapping("/{id}/configuration")
  @Operation(
      summary = "Replace the reader configuration (TS06)",
      description =
          "Intervals in seconds, greater than 0; heartbeat not shorter than the communication interval; "
              + "firmware like 2.4.1. An invalid configuration changes nothing: "
              + "400 READER_CONFIGURATION_INVALID with the list in `errors`. 404 READER_NOT_FOUND.")
  public ReaderResource configure(
      @PathVariable String id, @Valid @RequestBody ConfigurationRequest body) {
    return ReaderResource.from(
        readers.reconfigure(
            id,
            body.readerMode(),
            body.communicationInterval(),
            body.heartbeatInterval(),
            body.firmwareVersion()));
  }

  @PostMapping("/{id}/activate")
  @Operation(
      summary = "Activate a REGISTERED or INACTIVE reader (TS07)",
      description = "404 READER_NOT_FOUND, 409 ACTION_NOT_ALLOWED.")
  public ReaderResource activate(@PathVariable String id) {
    return ReaderResource.from(readers.changeStatus(id, ReaderAction.ACTIVATE));
  }

  @PostMapping("/{id}/deactivate")
  @Operation(
      summary = "Deactivate an ACTIVE, DEGRADED or OFFLINE reader (TS08)",
      description = "404 READER_NOT_FOUND, 409 ACTION_NOT_ALLOWED.")
  public ReaderResource deactivate(@PathVariable String id) {
    return ReaderResource.from(readers.changeStatus(id, ReaderAction.DEACTIVATE));
  }

  @PostMapping("/{id}/start-maintenance")
  @Operation(
      summary = "Put a reader in maintenance (TS09)",
      description = "Allowed from every status but MAINTENANCE. 404 READER_NOT_FOUND, 409 ACTION_NOT_ALLOWED.")
  public ReaderResource startMaintenance(@PathVariable String id) {
    return ReaderResource.from(readers.changeStatus(id, ReaderAction.START_MAINTENANCE));
  }

  @PostMapping("/{id}/end-maintenance")
  @Operation(
      summary = "End the maintenance: the reader becomes ACTIVE (TS09)",
      description = "404 READER_NOT_FOUND, 409 ACTION_NOT_ALLOWED.")
  public ReaderResource endMaintenance(@PathVariable String id) {
    return ReaderResource.from(readers.changeStatus(id, ReaderAction.END_MAINTENANCE));
  }

  @GetMapping("/{id}/telemetry")
  @Operation(
      summary = "Telemetry samples of a reader, newest first",
      description = "`limit` defaults to 50 (at most 500). 404 READER_NOT_FOUND.")
  public List<TelemetryResource> telemetry(
      @PathVariable String id, @RequestParam(defaultValue = "50") int limit) {
    return readers.telemetry(id, limit).stream().map(TelemetryResource::from).toList();
  }

  @GetMapping("/{id}/incidents")
  @Operation(
      summary = "Incidents of a reader, newest first",
      description = "`open=true` keeps the unresolved ones, `open=false` the resolved ones. 404 READER_NOT_FOUND.")
  public List<IncidentResource> incidents(
      @PathVariable String id, @RequestParam(required = false) Boolean open) {
    readers.get(id);
    return incidents.list(id, open).stream().map(IncidentResource::from).toList();
  }

  @GetMapping("/{id}/tap-events")
  @Operation(
      summary = "Tap events of a reader, newest first (poll with `after`)",
      description =
          "Send the previous `nextCursor` as `after` to get only what is new; without it you get the latest "
              + "`limit` events (default 50, at most 200). `nextCursor` stays the same when nothing is new. "
              + "400 INVALID_CURSOR. 404 READER_NOT_FOUND.")
  public TapEventPage tapEvents(
      @PathVariable String id,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) Integer limit) {
    readers.get(id);
    return page(tapEvents.list(id, after, limit));
  }

  @PostMapping("/{id}/taps")
  @Operation(
      summary = "Tap a card on a reader as the simulator would (plan step 6)",
      description =
          "Body `{\"cardUid\": \"04:3D:9B:12:8F\"}`. The tap is published to the broker like a real one, goes "
              + "through the same pipeline and the call waits up to 3 seconds for the resolution: "
              + "200 with the resolved event, or 202 with the event still PENDING. An unregistered or "
              + "out-of-service reader or an unknown card is answered 200 with outcome REJECTED. "
              + "400 INVALID_CARD_UID.")
  public ResponseEntity<TapEventResource> tap(
      @PathVariable String id, @Valid @RequestBody TapRequest body) {
    CardTapEvent event = simulation.tap(id, body.cardUid());
    if (event == null) {
      throw new IllegalStateException("The tap was not stored; is the message broker reachable?");
    }
    HttpStatus status = event.isPending() ? HttpStatus.ACCEPTED : HttpStatus.OK;
    return ResponseEntity.status(status).body(TapEventResource.from(event, mapper));
  }

  private TapEventPage page(TapEventQueryService.Page page) {
    return new TapEventPage(
        page.items().stream().map(event -> TapEventResource.from(event, mapper)).toList(),
        page.nextCursor());
  }
}
