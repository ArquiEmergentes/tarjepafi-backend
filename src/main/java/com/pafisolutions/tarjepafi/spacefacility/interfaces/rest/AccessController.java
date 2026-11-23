package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

import com.pafisolutions.tarjepafi.spacefacility.application.AccessQueryService;
import com.pafisolutions.tarjepafi.spacefacility.application.SpaceManagementService;
import com.pafisolutions.tarjepafi.spacefacility.interfaces.rest.AccessResources.AccessRecordPage;
import com.pafisolutions.tarjepafi.spacefacility.interfaces.rest.AccessResources.AccessRecordResource;
import com.pafisolutions.tarjepafi.spacefacility.interfaces.rest.AccessResources.CreateSpaceRequest;
import com.pafisolutions.tarjepafi.spacefacility.interfaces.rest.AccessResources.OccupancyResource;
import com.pafisolutions.tarjepafi.spacefacility.interfaces.rest.AccessResources.PresenceResource;
import com.pafisolutions.tarjepafi.spacefacility.interfaces.rest.AccessResources.PresencesResource;
import com.pafisolutions.tarjepafi.spacefacility.interfaces.rest.AccessResources.UpdateAccessPolicyRequest;
import com.pafisolutions.tarjepafi.spacefacility.interfaces.rest.AccessResources.UpdateSpaceStatusRequest;
import com.pafisolutions.tarjepafi.spacefacility.interfaces.rest.AccessResources.WorkShiftResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Space administration, access records, work shifts and campus presences. Needs the ADMIN role. */
@RestController
@Tag(name = "Space access (admin)")
public class AccessController {

  private final SpaceManagementService management;
  private final AccessQueryService queries;

  public AccessController(SpaceManagementService management, AccessQueryService queries) {
    this.management = management;
    this.queries = queries;
  }

  // ---------- TS18, TS28: space management ----------

  @PostMapping("/api/v1/spaces")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Register a space (TS18)",
      description =
          "Created AVAILABLE. `type` is one of CLASSROOM, LABORATORY, RESTRICTED_AREA, CAMPUS_ENTRANCE, "
              + "CAMPUS_EXIT, MEETING_ROOM, LIBRARY, STUDY_CUBICLE, EVENT_VENUE. 400 INVALID_SPACE_TYPE / "
              + "INVALID_CAPACITY / INVALID_ACCESS_POLICY / INVALID_ROLE, 409 SPACE_CODE_EXISTS.")
  public SpaceResource create(@Valid @RequestBody CreateSpaceRequest body) {
    return SpaceResource.from(
        management.create(
            body.code(),
            body.name(),
            body.type(),
            body.building(),
            body.floor(),
            body.capacity(),
            body.accessPolicy(),
            body.allowedRoles()));
  }

  @PatchMapping("/api/v1/spaces/{id}/status")
  @Operation(
      summary = "Set a space AVAILABLE or MAINTENANCE (TS18)",
      description =
          "Taps in a space under maintenance are denied with SPACE_IN_MAINTENANCE afterwards. 404 "
              + "SPACE_NOT_FOUND, 400 INVALID_SPACE_STATUS.")
  public SpaceResource changeStatus(
      @PathVariable String id, @Valid @RequestBody UpdateSpaceStatusRequest body) {
    return SpaceResource.from(management.changeStatus(id, body.status()));
  }

  @PatchMapping("/api/v1/spaces/{id}/access-policy")
  @Operation(
      summary = "Change the access policy and roles of a space (TS28)",
      description =
          "`accessPolicy` OPEN or RESTRICTED and `allowedRoles` from STUDENT, TEACHER, STAFF; later taps are "
              + "evaluated with them. 404 SPACE_NOT_FOUND, 400 INVALID_ACCESS_POLICY / INVALID_ROLE.")
  public SpaceResource changeAccessPolicy(
      @PathVariable String id, @Valid @RequestBody UpdateAccessPolicyRequest body) {
    return SpaceResource.from(
        management.changeAccessPolicy(id, body.accessPolicy(), body.allowedRoles()));
  }

  @GetMapping("/api/v1/spaces/{id}/occupancy")
  @Operation(
      summary = "Capacity and current occupancy of a space (TS19)",
      description =
          "Only the campus is counted: for CAMPUS_ENTRANCE and CAMPUS_EXIT spaces `currentOccupancy` is the "
              + "number of people who entered today and have not left. Other spaces answer `tracked: false` "
              + "and 0. 404 SPACE_NOT_FOUND.")
  public OccupancyResource occupancy(@PathVariable String id) {
    return OccupancyResource.from(queries.occupancy(id));
  }

  // ---------- Reads ----------

  @GetMapping("/api/v1/access-records")
  @Operation(
      summary = "Access attempts, newest first (poll with `after`)",
      description =
          "Filters `spaceId`, `cardHolderId`, `decision` (GRANTED or DENIED). Send the previous `nextCursor` "
              + "as `after` to get only what is new; at most `limit` items (default 50, at most 200). 400 "
              + "INVALID_CURSOR.")
  public AccessRecordPage accessRecords(
      @RequestParam(required = false) String spaceId,
      @RequestParam(required = false) String cardHolderId,
      @RequestParam(required = false) String decision,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) Integer limit) {
    AccessQueryService.AccessPage page =
        queries.accessRecords(spaceId, cardHolderId, decision, after, limit);
    return new AccessRecordPage(
        page.items().stream().map(AccessRecordResource::from).toList(), page.nextCursor());
  }

  @GetMapping("/api/v1/work-shifts")
  @Operation(
      summary = "Work shifts of the staff, newest first",
      description = "Filters `cardHolderId`, `status` (OPEN or CLOSED) and `date` (yyyy-MM-dd).")
  public List<WorkShiftResource> workShifts(
      @RequestParam(required = false) String cardHolderId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return queries.workShifts(cardHolderId, status, date).stream()
        .map(WorkShiftResource::from)
        .toList();
  }

  @GetMapping("/api/v1/card-holders/{cardHolderId}/campus-presences")
  @Operation(
      summary = "Stays on campus of a holder between `from` and `to` (TS30)",
      description =
          "`from` and `to` are campus date-times (yyyy-MM-ddTHH:mm:ss). A stay is a granted entry up to the "
              + "next granted exit; one without exit is OPEN and does not add to `totalMinutes`. 400 INVALID_RANGE.")
  public PresencesResource presences(
      @PathVariable String cardHolderId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
    AccessQueryService.Presences presences = queries.presences(cardHolderId, from, to);
    return new PresencesResource(
        presences.items().stream()
            .map(
                p -> new PresenceResource(p.entryAt(), p.exitAt(), p.durationMinutes(), p.status()))
            .toList(),
        presences.totalMinutes());
  }
}
