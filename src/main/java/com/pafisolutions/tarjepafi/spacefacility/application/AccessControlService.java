package com.pafisolutions.tarjepafi.spacefacility.application;

import com.pafisolutions.tarjepafi.academicattendance.application.AttendanceFacade;
import com.pafisolutions.tarjepafi.credentials.application.CardHolderDirectoryPort;
import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import com.pafisolutions.tarjepafi.shared.application.TapContext;
import com.pafisolutions.tarjepafi.shared.application.TapResult;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.AccessRecord;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.Space;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.WorkShift;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.AccessRecordRepository;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.WorkShiftRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the taps that belong to Space &amp; Facility (TS22 without reservations): access to a space, the
 * campus turnstiles and the work shifts of staff. Port of {@code access-control.service.ts}.
 */
@Service
public class AccessControlService {

  private static final Logger log = LoggerFactory.getLogger(AccessControlService.class);
  private static final Set<String> TURNSTILES = Set.of("CAMPUS_ENTRANCE", "CAMPUS_EXIT");

  private final SpaceCatalog spaces;
  private final AccessRecordRepository accessRecords;
  private final WorkShiftRepository shifts;
  private final CardHolderDirectoryPort directory;
  private final AttendanceFacade attendance;
  private final ApplicationEventPublisher events;

  public AccessControlService(
      SpaceCatalog spaces,
      AccessRecordRepository accessRecords,
      WorkShiftRepository shifts,
      CardHolderDirectoryPort directory,
      AttendanceFacade attendance,
      ApplicationEventPublisher events) {
    this.spaces = spaces;
    this.accessRecords = accessRecords;
    this.shifts = shifts;
    this.directory = directory;
    this.attendance = attendance;
    this.events = events;
  }

  /** Answers the tap when it is Space &amp; Facility's to resolve (see the routing table of the plan). */
  @Transactional
  public void onCardTap(TapContext tap) {
    Optional<Space> found = spaces.find(tap.spaceId());
    if (found.isPresent() && !belongsToSpace(tap, found.get())) {
      return;
    }
    if (accessRecords.existsByTapEventId(tap.tapEventId())) {
      log.debug("Tap {} already has its access record", tap.tapEventId());
      return;
    }
    TapResult result;
    if (found.isEmpty()) {
      result = TapResult.rejected("tap.accessDenied");
    } else if (TURNSTILES.contains(found.get().getType())) {
      result = turnstile(tap, found.get());
    } else {
      result = spaceAccess(tap, found.get());
    }
    events.publishEvent(result.toEvent("space", tap));
  }

  /**
   * A card that is not registered or not active, read in a space that is not a classroom, leaves a denied
   * access record (IoT rejected the tap itself, so it never reaches {@link #onCardTap}).
   */
  @Transactional
  public void onTapRejected(Map<String, Object> data) {
    String reason = String.valueOf(data.get("reason"));
    Object spaceId = data.get("spaceId");
    if (spaceId == null || !Set.of("CARD_NOT_REGISTERED", "CARD_INACTIVE").contains(reason)) {
      return;
    }
    Optional<Space> space = spaces.find(spaceId.toString());
    String tapEventId = String.valueOf(data.get("tapEventId"));
    if (space.isEmpty() || "CLASSROOM".equals(space.get().getType())) {
      return;
    }
    if (accessRecords.existsByTapEventId(tapEventId)) {
      return;
    }
    saveAccess(
        tapEventId,
        String.valueOf(data.get("cardUid")),
        (String) data.get("credentialId"),
        (String) data.get("cardHolderId"),
        space.get(),
        String.valueOf(data.get("readerId")),
        LocalDateTime.parse(String.valueOf(data.get("tappedAt"))),
        false,
        reason);
  }

  /** Access to a space: maintenance denies; then the card's own rule, then the roles of the space. */
  private TapResult spaceAccess(TapContext tap, Space space) {
    boolean granted = decide(tap, space);
    return granted
        ? TapResult.access("success", "tap.accessGranted")
        : TapResult.rejected("tap.accessDenied");
  }

  /** Turnstile: staff open and close their shift; students and teachers only leave an access record. */
  private TapResult turnstile(TapContext tap, Space space) {
    if (!decide(tap, space)) {
      return TapResult.rejected("tap.accessDenied");
    }
    if (!"STAFF".equals(tap.holderRole())) {
      return TapResult.access("success", "tap.accessGranted");
    }
    return "CAMPUS_ENTRANCE".equals(space.getType()) ? shiftEntry(tap) : shiftExit(tap);
  }

  private boolean decide(TapContext tap, Space space) {
    boolean granted = true;
    String denial = "NONE";
    if (space.isInMaintenance()) {
      granted = false;
      denial = "SPACE_IN_MAINTENANCE";
    } else if (!directory
        .resolveAccess(tap.credentialId(), space.getId(), space.getAllowedRoles())
        .allowed()) {
      granted = false;
      denial = "UNAUTHORIZED_ROLE";
    }
    saveAccess(
        tap.tapEventId(),
        tap.cardUid(),
        tap.credentialId(),
        tap.cardHolderId(),
        space,
        tap.readerId(),
        tap.at(),
        granted,
        denial);
    return granted;
  }

  private void saveAccess(
      String tapEventId,
      String cardUid,
      String credentialId,
      String cardHolderId,
      Space space,
      String readerId,
      LocalDateTime at,
      boolean granted,
      String denialReason) {
    AccessRecord record =
        new AccessRecord(
            "acc-" + UUID.randomUUID(),
            tapEventId,
            cardUid,
            credentialId,
            cardHolderId,
            space.getId(),
            readerId,
            accessTypeOf(space),
            granted ? "GRANTED" : "DENIED",
            denialReason,
            at);
    accessRecords.save(record);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("accessRecordId", record.getId());
    data.put("tapEventId", tapEventId);
    data.put("spaceId", space.getId());
    data.put("readerId", readerId);
    data.put("cardHolderId", cardHolderId);
    data.put("accessType", record.getAccessType());
    data.put("decision", record.getDecision());
    data.put("denialReason", denialReason);
    data.put("attemptedAt", at.toString());
    events.publishEvent(
        new IntegrationEvent(granted ? "space.access-granted" : "space.access-denied", data));
  }

  private static String accessTypeOf(Space space) {
    return switch (space.getType()) {
      case "CAMPUS_ENTRANCE" -> "CAMPUS_ENTRY";
      case "CAMPUS_EXIT" -> "CAMPUS_EXIT";
      default -> "SPACE_ACCESS";
    };
  }

  private boolean belongsToSpace(TapContext tap, Space space) {
    return switch (space.getType()) {
      case "CLASSROOM" -> "STAFF".equals(tap.holderRole());
      case "MEETING_ROOM" ->
          !("TEACHER".equals(tap.holderRole())
              && attendance.hasMeetingInProgress(space.getId(), tap.at()));
      default -> true;
    };
  }

  // ---------- Work shifts ----------

  private TapResult shiftEntry(TapContext tap) {
    if (shifts.findFirstByCardHolderIdAndStatus(tap.cardHolderId(), WorkShift.OPEN).isPresent()) {
      return TapResult.workShift("info", "tap.shiftAlreadyOpen", Map.of());
    }
    WorkShift shift = WorkShift.open("ws-" + UUID.randomUUID(), tap.cardHolderId(), tap.at());
    shifts.save(shift);
    publishShift("space.work-shift-opened", shift, tap);
    return TapResult.workShift("success", "tap.shiftOpened", Map.of());
  }

  private TapResult shiftExit(TapContext tap) {
    Optional<WorkShift> open =
        shifts.findFirstByCardHolderIdAndStatus(tap.cardHolderId(), WorkShift.OPEN);
    if (open.isPresent()) {
      WorkShift shift = open.get();
      shift.close(tap.at());
      shifts.save(shift);
      publishShift("space.work-shift-closed", shift, tap);
      return TapResult.workShift(
          "success",
          "tap.shiftClosed",
          Map.of("worked", formatDuration(shift.getWorkedMinutes() == null ? 0 : shift.getWorkedMinutes())));
    }
    if (shifts.existsByCardHolderIdAndDate(tap.cardHolderId(), tap.at().toLocalDate())) {
      return TapResult.workShift("info", "tap.exitAlreadyRegistered", Map.of());
    }
    return TapResult.rejected("tap.noEntryFound");
  }

  private void publishShift(String routingKey, WorkShift shift, TapContext tap) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("workShiftId", shift.getId());
    data.put("cardHolderId", shift.getCardHolderId());
    data.put("tapEventId", tap.tapEventId());
    data.put("status", shift.getStatus());
    data.put("entryAt", shift.getEntryAt().toString());
    if (shift.getExitAt() != null) {
      data.put("exitAt", shift.getExitAt().toString());
      data.put("workedMinutes", shift.getWorkedMinutes());
    }
    events.publishEvent(new IntegrationEvent(routingKey, data));
  }

  /** "45 min" or "1 h 28 min": the format of the old web app. */
  static String formatDuration(int totalMinutes) {
    int minutes = Math.max(0, totalMinutes);
    int hours = minutes / 60;
    int rest = minutes % 60;
    return hours == 0 ? rest + " min" : String.format("%d h %02d min", hours, rest);
  }
}
