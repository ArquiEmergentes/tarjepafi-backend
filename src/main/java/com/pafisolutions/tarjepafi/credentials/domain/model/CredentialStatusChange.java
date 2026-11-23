package com.pafisolutions.tarjepafi.credentials.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One entry of a card history. A change that keeps the status (linking a holder) is allowed. */
@Entity
@Table(name = "credential_status_history")
public class CredentialStatusChange {

  /** The author of automatic changes; manual ones carry the id of the administrator. */
  public static final String SYSTEM_ACTOR = "SYSTEM";

  @Id private String id;

  @Column(name = "credential_id", nullable = false)
  private String credentialId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", nullable = false)
  private CredentialStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false)
  private CredentialStatus toStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private StatusChangeReason reason;

  @Column(name = "changed_by", nullable = false)
  private String changedBy;

  @Column(name = "changed_at", nullable = false)
  private LocalDateTime changedAt;

  private String note;

  protected CredentialStatusChange() {}

  public CredentialStatusChange(
      String id,
      String credentialId,
      CredentialStatus fromStatus,
      CredentialStatus toStatus,
      StatusChangeReason reason,
      String changedBy,
      LocalDateTime changedAt,
      String note) {
    this.id = id;
    this.credentialId = credentialId;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.reason = reason;
    this.changedBy = changedBy;
    this.changedAt = changedAt;
    this.note = note;
  }

  public boolean isAutomatic() {
    return SYSTEM_ACTOR.equals(changedBy);
  }

  public String getId() {
    return id;
  }

  public String getCredentialId() {
    return credentialId;
  }

  public CredentialStatus getFromStatus() {
    return fromStatus;
  }

  public CredentialStatus getToStatus() {
    return toStatus;
  }

  public StatusChangeReason getReason() {
    return reason;
  }

  public String getChangedBy() {
    return changedBy;
  }

  public LocalDateTime getChangedAt() {
    return changedAt;
  }

  public String getNote() {
    return note;
  }
}
