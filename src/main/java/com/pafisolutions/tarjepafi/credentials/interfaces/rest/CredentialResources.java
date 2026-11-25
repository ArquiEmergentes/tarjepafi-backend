package com.pafisolutions.tarjepafi.credentials.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
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
      @Schema(example = "ch-u202312966") String id,
      @Schema(example = "U202312966") String code,
      @Schema(example = "Ana Torres") String fullName,
      @Schema(example = "u202312966@upc.edu.pe") String email,
      @Schema(example = "STUDENT") String role,
      @Schema(example = "Ingenieria de Software") String program,
      @Schema(example = "COMPLETED") String enrollmentStatus) {

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
      @Schema(example = "cred-001") String id,
      @Schema(example = "04:3D:9B:12:8F") String cardUid,
      @Schema(example = "ch-u202312966") String cardHolderId,
      @Schema(example = "ACTIVE") String status,
      @Schema(example = "2026-03-01T09:00:00") LocalDateTime issuedAt,
      @Schema(example = "2026-03-01T09:00:00") LocalDateTime statusChangedAt,
      @Schema(example = "2026-10-07T09:50:00") LocalDateTime lastUsedAt,
      @Schema(example = "[\"BLOCK\",\"DEACTIVATE\",\"REPORT_LOST\"]") List<String> availableActions,
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
      @Schema(example = "csh-0001") String id,
      @Schema(example = "cred-001") String credentialId,
      @Schema(example = "ACTIVE") String fromStatus,
      @Schema(example = "BLOCKED") String toStatus,
      @Schema(example = "MANUAL") String reason,
      @Schema(example = "usr-001") String changedBy,
      @Schema(example = "2026-10-05T12:00:00") LocalDateTime changedAt,
      @Schema(example = "Tarjeta bloqueada por solicitud del estudiante") String note) {

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
  public record PermissionResource(@Schema(example = "perm-001") String id, @Schema(example = "cred-001") String credentialId, @Schema(example = "sp-lab-e") String spaceId, @Schema(example = "ALLOW") String effect) {

    public static PermissionResource from(CredentialPermission permission) {
      return new PermissionResource(
          permission.getId(),
          permission.getCredentialId(),
          permission.getSpaceId(),
          permission.getEffect().name());
    }
  }

  public record SettingsResource(
      @Schema(example = "2026-2") String term, @Schema(example = "2026-10-03T23:59:59") LocalDateTime enrollmentDeadline, @Schema(example = "10") int defaultToleranceMinutes) {

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
      @Schema(example = "2026-10-07T10:00:00") LocalDateTime executedAt,
      @Schema(example = "2026-10-03T23:59:59") LocalDateTime deadline,
      @Schema(example = "true") boolean deadlinePassed,
      List<CredentialResource> deactivated,
      @Schema(example = "40") int unchangedCount,
      @Schema(example = "0") int failedCount) {

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
  public record StatusChangeRequest(@Schema(example = "Solicitud del estudiante") String note) {}

  public record LinkHolderRequest(@Schema(example = "U202312966") @NotBlank String holderCode) {}

  public record PermissionRequest(@Schema(example = "ALLOW") @NotNull PermissionEffect effect) {}
}
