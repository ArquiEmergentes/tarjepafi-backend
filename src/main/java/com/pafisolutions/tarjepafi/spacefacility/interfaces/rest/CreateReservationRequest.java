package com.pafisolutions.tarjepafi.spacefacility.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** Campus date-times (yyyy-MM-ddTHH:mm:ss); the end must be after the start. */
public record CreateReservationRequest(
    @Schema(example = "ch-u202312966") @NotBlank String cardHolderId, @Schema(example = "2026-10-07T10:00:00") @NotNull LocalDateTime startTime, @Schema(example = "2026-10-07T11:00:00") @NotNull LocalDateTime endTime) {}
