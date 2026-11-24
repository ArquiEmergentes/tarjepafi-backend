package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

import com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation;
import java.time.LocalDateTime;

/**
 * A reservation. {@code status}: CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED or FORFEITED. {@code checkedInAt} is
 * null until the holder taps the card at the space.
 */
public record ReservationResource(
    String id,
    String reservationCode,
    String spaceId,
    String cardHolderId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String status,
    LocalDateTime checkInWindowEnd,
    LocalDateTime checkedInAt,
    LocalDateTime createdAt) {

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
