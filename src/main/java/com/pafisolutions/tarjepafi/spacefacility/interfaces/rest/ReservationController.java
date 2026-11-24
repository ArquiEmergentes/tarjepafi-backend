package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

import com.pafisolutions.tarjepafi.spacefacility.application.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Reservations of spaces with the RESERVATION_REQUIRED policy (TS20, TS21). Needs the ADMIN role. */
@RestController
@Tag(name = "Reservations (admin)")
public class ReservationController {

  private final ReservationService reservations;

  public ReservationController(ReservationService reservations) {
    this.reservations = reservations;
  }

  @PostMapping("/api/v1/spaces/{spaceId}/reservations")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Reserve a space for a card holder (TS20)",
      description =
          "The space needs the `RESERVATION_REQUIRED` policy and cannot be in maintenance; the holder needs an "
              + "active card and a role the space admits; the slot must start after the campus time and not overlap "
              + "another CONFIRMED or CHECKED_IN reservation. Created CONFIRMED with `checkInWindowEnd` = "
              + "`startTime` + 10 minutes. 404 SPACE_NOT_FOUND, 400 INVALID_TIME_SLOT, 422 SPACE_NOT_RESERVABLE / "
              + "HOLDER_NOT_FOUND / HOLDER_WITHOUT_ACTIVE_CREDENTIAL / ROLE_NOT_ALLOWED / SLOT_NOT_IN_FUTURE, "
              + "409 RESERVATION_OVERLAP.")
  public ReservationResource create(
      @PathVariable String spaceId, @Valid @RequestBody CreateReservationRequest body) {
    return ReservationResource.from(
        reservations.create(spaceId, body.cardHolderId(), body.startTime(), body.endTime()));
  }

  @PostMapping("/api/v1/reservations/{reservationId}/cancel")
  @Operation(
      summary = "Cancel a reservation (TS20)",
      description =
          "Only a CONFIRMED reservation can be cancelled. 404 RESERVATION_NOT_FOUND, 409 INVALID_RESERVATION_STATE.")
  public ReservationResource cancel(@PathVariable String reservationId) {
    return ReservationResource.from(reservations.cancel(reservationId));
  }

  @GetMapping("/api/v1/reservations/active")
  @Operation(
      summary = "Reservations in progress (TS21)",
      description =
          "CONFIRMED or CHECKED_IN reservations of the whole campus whose slot contains the campus time now; an "
              + "empty list when there are none.")
  public List<ReservationResource> active() {
    return reservations.active().stream().map(ReservationResource::from).toList();
  }

  @GetMapping("/api/v1/reservations/{reservationId}")
  @Operation(
      summary = "One reservation (TS21)",
      description = "With its status, slot and `checkedInAt`. 404 RESERVATION_NOT_FOUND.")
  public ReservationResource get(@PathVariable String reservationId) {
    return ReservationResource.from(reservations.get(reservationId));
  }

  @GetMapping("/api/v1/spaces/{spaceId}/reservations")
  @Operation(
      summary = "Reservations of a space between `from` and `to` (TS21)",
      description =
          "Reservations whose slot touches the range, oldest first; `from` and `to` are campus date-times and are "
              + "optional. 404 SPACE_NOT_FOUND, 400 INVALID_RANGE.")
  public List<ReservationResource> ofSpace(
      @PathVariable String spaceId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
    return reservations.ofSpace(spaceId, from, to).stream().map(ReservationResource::from).toList();
  }
}
