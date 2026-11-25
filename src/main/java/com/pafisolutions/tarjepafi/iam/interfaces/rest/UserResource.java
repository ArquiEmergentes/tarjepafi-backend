package com.pafisolutions.tarjepafi.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import com.pafisolutions.tarjepafi.iam.domain.model.User;
import java.time.LocalDateTime;

/** Same field names as the web app's {@code user.resource.ts}. The password is never exposed. */
public record UserResource(
    @Schema(example = "usr-002") String id,
    @Schema(example = "docente@upc.edu.pe") String email,
    @Schema(example = "TEACHER") String role,
    @Schema(example = "ACTIVE") String status,
    @Schema(example = "ch-d0001") String cardHolderId,
    @Schema(example = "Daniel Rojas") String fullName,
    @Schema(example = "2026-08-17T08:00:00") LocalDateTime createdAt,
    @Schema(example = "2026-08-17T08:00:00") LocalDateTime updatedAt) {

  public static UserResource from(User user) {
    return new UserResource(
        user.getId(),
        user.getEmail(),
        user.getRole().name(),
        user.getStatus().name(),
        user.getCardHolderId(),
        user.getFullName(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
