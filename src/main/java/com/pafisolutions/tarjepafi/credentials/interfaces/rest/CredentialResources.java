package com.pafisolutions.tarjepafi.credentials.interfaces.rest;

import com.pafisolutions.tarjepafi.credentials.application.CredentialService.CredentialView;
import com.pafisolutions.tarjepafi.credentials.application.EnrollmentVerificationService;
import com.pafisolutions.tarjepafi.credentials.domain.model.CampusSettings;
import com.pafisolutions.tarjepafi.credentials.domain.model.CardHolder;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialPermission;
import com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatusChange;
import com.pafisolutions.tarjepafi.credentials.domain.model.PermissionEffect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/** The JSON shapes of the credentials API. Field names follow the web app's {@code *.resource.ts}. */
public final class CredentialResources {

  private CredentialResources() {}

  /** {@code role}: STUDENT, TEACHER or STAFF. {@code enrollmentStatus}: COMPLETED or PENDING, null unless a student. */
  public record CardHolderResource(
      String id,
      String code,
      String fullName,
      String email,
      String role,
      String program,
      String enrollmentStatus) {

    public static CardHolderResource from(CardHolder holder) {
      return new CardHolderResource(
          holder.getId(),
          holder.getCode(),
          holder.getFullName(),
          holder.getEmail(),
          holder.getRole().name(),
          holder.getProgram(),
          holder.getEnrollmentStatus() == null ? null : holder.getEnrollmentStatus().name());
    }
  }

  /**
   * A card. {@code status}: ACTIVE, BLOCKED, LOST or DEACTIVATED. {@code cardHolderId} and {@code holder}
   * are null while the card is in stock. {@code availableActions} lists what the status allows:
   * BLOCK, REPORT_LOST, DEACTIVATE, REACTIVATE.
   */
  public record CredentialResource(
      String id,
      String cardUid,
      String cardHolderId,
      String status,
      LocalDateTime issuedAt,
      LocalDateTime statusChangedAt,
      LocalDateTime lastUsedAt,
      List<String> availableActions,
      CardHolderResource holder) {

    public static CredentialResource from(CredentialView view) {
      var credential = view.credential();
      return new CredentialResource(
          credential.getId(),
          credential.getCardUid(),
          credential.getCardHolderId(),
          credential.getStatus().name(),
          credential.getIssuedAt(),
          credential.getStatusChangedAt(),
          credential.getLastUsedAt(),
          credential.availableActions().stream().map(Enum::name).toList(),
          view.holder() == null ? null : CardHolderResource.from(view.holder()));
    }
  }

  /**
   * One entry of the card history. {@code reason}: WITHDRAWAL, REENROLLMENT, ENROLLMENT_NOT_COMPLETED,
   * LOST or MANUAL. {@code changedBy} is a user id or SYSTEM. Linking a holder keeps the status.
   */
  public record StatusChangeResource(
      String id,
      String credentialId,
      String fromStatus,
      String toStatus,
      String reason,
      String changedBy,
      LocalDateTime changedAt,
      String note) {

    public static StatusChangeResource from(CredentialStatusChange change) {
      return new StatusChangeResource(
          change.getId(),
          change.getCredentialId(),
          change.getFromStatus().name(),
          change.getToStatus().name(),
          change.getReason().name(),
          change.getChangedBy(),
          change.getChangedAt(),
          change.getNote());
    }
  }

  /** An access exception of a card for a space; {@code effect}: ALLOW or DENY. */
  public record PermissionResource(String id, String credentialId, String spaceId, String effect) {

    public static PermissionResource from(CredentialPermission permission) {
      return new PermissionResource(
          permission.getId(),
          permission.getCredentialId(),
          permission.getSpaceId(),
          permission.getEffect().name());
    }
  }

  public record SettingsResource(
      String term, LocalDateTime enrollmentDeadline, int defaultToleranceMinutes) {

    public static SettingsResource from(CampusSettings settings) {
      return new SettingsResource(
          settings.getTerm(), settings.getEnrollmentDeadline(), settings.getDefaultToleranceMinutes());
    }
  }

  /**
   * Result of the enrollment verification (US15). {@code deactivated} holds the cards that were (or, in
   * the preview, would be) deactivated.
   */
  public record EnrollmentVerificationResource(
      LocalDateTime executedAt,
      LocalDateTime deadline,
      boolean deadlinePassed,
      List<CredentialResource> deactivated,
      int unchangedCount,
      int failedCount) {

    public static EnrollmentVerificationResource from(EnrollmentVerificationService.Summary summary) {
      return new EnrollmentVerificationResource(
          summary.executedAt(),
          summary.deadline(),
          summary.deadlinePassed(),
          summary.deactivated().stream().map(CredentialResource::from).toList(),
          summary.unchangedCount(),
          summary.failedCount());
    }
  }

  /** Optional body of block, report-lost, deactivate and reactivate. The note is required to block. */
  public record StatusChangeRequest(String note) {}

  public record LinkHolderRequest(@NotBlank String holderCode) {}

  public record PermissionRequest(@NotNull PermissionEffect effect) {}
}
