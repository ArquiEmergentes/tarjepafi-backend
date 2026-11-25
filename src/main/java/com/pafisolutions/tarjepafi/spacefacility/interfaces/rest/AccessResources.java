package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import com.pafisolutions.tarjepafi.spacefacility.application.AccessQueryService;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.AccessRecord;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.WorkShift;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** The JSON shapes of the Space &amp; Facility API beyond the space itself. */
public final class AccessResources {

  private AccessResources() {}

  /**
   * An attempt to enter. {@code accessType}: CAMPUS_ENTRY, CAMPUS_EXIT or SPACE_ACCESS; {@code decision}:
   * GRANTED or DENIED; {@code denialReason}: NONE, CARD_NOT_REGISTERED, CARD_INACTIVE, UNAUTHORIZED_ROLE or
   * SPACE_IN_MAINTENANCE. {@code cursor} grows with every new record (poll with it).
   */
  public record AccessRecordResource(
      @Schema(example = "acc-00442") String id,
      @Schema(example = "120") long cursor,
      @Schema(example = "evt-00440") String tapEventId,
      @Schema(example = "04:3D:9B:12:8F") String cardUid,
      @Schema(example = "cred-001") String credentialId,
      @Schema(example = "ch-u202312966") String cardHolderId,
      @Schema(example = "sp-ent-01") String spaceId,
      @Schema(example = "RDR-T-001") String readerId,
      @Schema(example = "CAMPUS_ENTRY") String accessType,
      @Schema(example = "GRANTED") String decision,
      @Schema(example = "NONE") String denialReason,
      @Schema(example = "2026-10-05T07:17:18") LocalDateTime attemptedAt) {

    static AccessRecordResource from(AccessRecord record) {
      return new AccessRecordResource(
          record.getId(),
          record.getSeq() == null ? 0 : record.getSeq(),
          record.getTapEventId(),
          record.getCardUid(),
          record.getCredentialId(),
          record.getCardHolderId(),
          record.getSpaceId(),
          record.getReaderId(),
          record.getAccessType(),
          record.getDecision(),
          record.getDenialReason(),
          record.getAttemptedAt());
    }
  }

  public record AccessRecordPage(List<AccessRecordResource> items, @Schema(example = "120") String nextCursor) {}

  /** {@code status}: OPEN or CLOSED; {@code workedMinutes} is null while OPEN. */
  public record WorkShiftResource(
      @Schema(example = "ws-01261") String id,
      @Schema(example = "19") long cursor,
      @Schema(example = "ch-t0001") String cardHolderId,
      @Schema(example = "2026-10-05") LocalDate date,
      @Schema(example = "2026-10-05T06:54:51") LocalDateTime entryAt,
      @Schema(example = "2026-10-05T15:01:51") LocalDateTime exitAt,
      @Schema(example = "487") Integer workedMinutes,
      @Schema(example = "CLOSED") String status) {

    static WorkShiftResource from(WorkShift shift) {
      return new WorkShiftResource(
          shift.getId(),
          shift.getSeq() == null ? 0 : shift.getSeq(),
          shift.getCardHolderId(),
          shift.getDate(),
          shift.getEntryAt(),
          shift.getExitAt(),
          shift.getWorkedMinutes(),
          shift.getStatus());
    }
  }

  /** {@code tracked} is false when the occupancy of that space is not counted (only the campus is). */
  public record OccupancyResource(@Schema(example = "600") int capacity, @Schema(example = "132") int currentOccupancy, @Schema(example = "false") boolean isFull, @Schema(example = "true") boolean tracked) {

    static OccupancyResource from(AccessQueryService.Occupancy occupancy) {
      return new OccupancyResource(
          occupancy.capacity(), occupancy.currentOccupancy(), occupancy.isFull(), occupancy.tracked());
    }
  }

  public record PresenceResource(
      @Schema(example = "2026-10-05T07:17:18") LocalDateTime entryAt, @Schema(example = "2026-10-05T12:40:00") LocalDateTime exitAt, @Schema(example = "323") Integer durationMinutes, @Schema(example = "CLOSED") String status) {}

  public record PresencesResource(List<PresenceResource> items, @Schema(example = "323") int totalMinutes) {}

  public record CreateSpaceRequest(
      @Schema(example = "EVT-01") @NotBlank String code,
      @Schema(example = "Auditorio") @NotBlank String name,
      @Schema(example = "EVENT_VENUE") @NotBlank String type,
      @Schema(example = "Pabellón A") @NotBlank String building,
      @Schema(example = "1") int floor,
      @Schema(example = "300") @Min(0) int capacity,
      @Schema(example = "OPEN") @NotBlank String accessPolicy,
      @Schema(example = "[\"STUDENT\",\"TEACHER\"]") List<String> allowedRoles) {}

  public record UpdateSpaceStatusRequest(@Schema(example = "MAINTENANCE") @NotBlank String status) {}

  public record UpdateAccessPolicyRequest(@Schema(example = "RESTRICTED") @NotBlank String accessPolicy, @Schema(example = "[\"STAFF\"]") List<String> allowedRoles) {}
}
