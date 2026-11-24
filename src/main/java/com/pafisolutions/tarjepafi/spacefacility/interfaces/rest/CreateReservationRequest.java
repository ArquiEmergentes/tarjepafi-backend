package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** Campus date-times (yyyy-MM-ddTHH:mm:ss); the end must be after the start. */
public record CreateReservationRequest(
    @NotBlank String cardHolderId, @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime) {}
