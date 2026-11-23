package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

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
      String id,
      long cursor,
      String tapEventId,
      String cardUid,
      String credentialId,
      String cardHolderId,
      String spaceId,
      String readerId,
      String accessType,
      String decision,
      String denialReason,
      LocalDateTime attemptedAt) {

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

  public record AccessRecordPage(List<AccessRecordResource> items, String nextCursor) {}

  /** {@code status}: OPEN or CLOSED; {@code workedMinutes} is null while OPEN. */
  public record WorkShiftResource(
      String id,
      long cursor,
      String cardHolderId,
      LocalDate date,
      LocalDateTime entryAt,
      LocalDateTime exitAt,
      Integer workedMinutes,
      String status) {

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
  public record OccupancyResource(int capacity, int currentOccupancy, boolean isFull, boolean tracked) {

    static OccupancyResource from(AccessQueryService.Occupancy occupancy) {
      return new OccupancyResource(
          occupancy.capacity(), occupancy.currentOccupancy(), occupancy.isFull(), occupancy.tracked());
    }
  }

  public record PresenceResource(
      LocalDateTime entryAt, LocalDateTime exitAt, Integer durationMinutes, String status) {}

  public record PresencesResource(List<PresenceResource> items, int totalMinutes) {}

  public record CreateSpaceRequest(
      @NotBlank String code,
      @NotBlank String name,
      @NotBlank String type,
      @NotBlank String building,
      int floor,
      @Min(0) int capacity,
      @NotBlank String accessPolicy,
      List<String> allowedRoles) {}

  public record UpdateSpaceStatusRequest(@NotBlank String status) {}

  public record UpdateAccessPolicyRequest(@NotBlank String accessPolicy, List<String> allowedRoles) {}
}
