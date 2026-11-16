package com.pafisolutions.tarjepafi.iam.interfaces.rest;

import com.pafisolutions.tarjepafi.iam.domain.model.User;
import java.time.LocalDateTime;

/** Same field names as the web app's {@code user.resource.ts}. The password is never exposed. */
public record UserResource(
    String id,
    String email,
    String role,
    String status,
    String cardHolderId,
    String fullName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

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
