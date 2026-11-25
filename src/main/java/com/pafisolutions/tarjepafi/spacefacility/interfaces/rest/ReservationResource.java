package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation;
import java.time.LocalDateTime;

/**
 * A reservation. {@code status}: CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED or FORFEITED. {@code checkedInAt} is
 * null until the holder taps the card at the space.
 */
public record ReservationResource(
    @Schema(example = "res-8f1c2e4a") String id,
    @Schema(example = "RES-2026-04872") String reservationCode,
    @Schema(example = "sp-cub-l1") String spaceId,
    @Schema(example = "ch-u202312966") String cardHolderId,
    @Schema(example = "2026-10-07T10:00:00") LocalDateTime startTime,
    @Schema(example = "2026-10-07T11:00:00") LocalDateTime endTime,
    @Schema(example = "CONFIRMED") String status,
    @Schema(example = "2026-10-07T10:10:00") LocalDateTime checkInWindowEnd,
    @Schema(example = "2026-10-07T10:05:00") LocalDateTime checkedInAt,
    @Schema(example = "2026-10-07T09:50:00") LocalDateTime createdAt) {

  public static ReservationResource from(Reservation reservation) {
    return new ReservationResource(
        reservation.getId(),
        reservation.getReservationCode(),
        reservation.getSpaceId(),
        reservation.getCardHolderId(),
        reservation.getStartTime(),
        reservation.getEndTime(),
        reservation.getStatus().name(),
        reservation.getCheckInWindowEnd(),
        reservation.getCheckedInAt(),
        reservation.getCreatedAt());
  }
}
