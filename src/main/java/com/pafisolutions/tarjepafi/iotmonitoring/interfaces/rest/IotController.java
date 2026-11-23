package com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest;

import com.pafisolutions.tarjepafi.iotmonitoring.application.IncidentService;
import com.pafisolutions.tarjepafi.iotmonitoring.application.TapEventQueryService;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.IncidentResource;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.TapEventPage;
import com.pafisolutions.tarjepafi.iotmonitoring.interfaces.rest.IotResources.TapEventResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/** Cross-reader views: every tap event and every incident. Needs the ADMIN role. */
@RestController
@RequestMapping("/api/v1/iot")
@Tag(name = "IoT events")
public class IotController {

  private final TapEventQueryService tapEvents;
  private final IncidentService incidents;
  private final ObjectMapper mapper;

  public IotController(
      TapEventQueryService tapEvents, IncidentService incidents, ObjectMapper mapper) {
    this.tapEvents = tapEvents;
    this.incidents = incidents;
    this.mapper = mapper;
  }

  @GetMapping("/tap-events")
  @Operation(
      summary = "Tap events of every reader, newest first (poll with `after`)",
      description =
          "Filter `readerId`. Send the previous `nextCursor` as `after` to get only what is new; without it "
              + "you get the latest `limit` events (default 50, at most 200). 400 INVALID_CURSOR.")
  public TapEventPage list(
      @RequestParam(required = false) String readerId,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) Integer limit) {
    TapEventQueryService.Page page = tapEvents.list(readerId, after, limit);
    return new TapEventPage(
        page.items().stream().map(event -> TapEventResource.from(event, mapper)).toList(),
        page.nextCursor());
  }

  @GetMapping("/tap-events/{id}")
  @Operation(summary = "One tap event", description = "404 TAP_EVENT_NOT_FOUND.")
  public TapEventResource get(@PathVariable String id) {
    return TapEventResource.from(tapEvents.get(id), mapper);
  }

  @GetMapping("/incidents")
  @Operation(
      summary = "Incidents of every reader, newest first",
      description = "Filters `readerId` and `open` (true: unresolved, false: resolved).")
  public List<IncidentResource> incidents(
      @RequestParam(required = false) String readerId, @RequestParam(required = false) Boolean open) {
    return incidents.list(readerId, open).stream().map(IncidentResource::from).toList();
  }

  @PostMapping("/incidents/{id}/resolve")
  @Operation(
      summary = "Mark an incident as resolved",
      description = "404 INCIDENT_NOT_FOUND, 409 INCIDENT_ALREADY_RESOLVED.")
  public IncidentResource resolve(@PathVariable String id) {
    return IncidentResource.from(incidents.resolve(id));
  }
}
