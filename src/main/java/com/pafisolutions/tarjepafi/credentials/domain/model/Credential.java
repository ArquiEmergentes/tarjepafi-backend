package com.pafisolutions.tarjepafi.credentials.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** An NFC card. A null {@code cardHolderId} means it is in stock, not linked to anyone yet. */
@Entity
@Table(name = "credentials")
public class Credential {

  @Id private String id;

  @Column(name = "card_uid", nullable = false, unique = true)
  private String cardUid;

  @Column(name = "card_holder_id")
  private String cardHolderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CredentialStatus status;

  @Column(name = "issued_at", nullable = false)
  private LocalDateTime issuedAt;

  @Column(name = "status_changed_at", nullable = false)
  private LocalDateTime statusChangedAt;

  @Column(name = "last_used_at")
  private LocalDateTime lastUsedAt;

  protected Credential() {}

  public Credential(
      String id,
      String cardUid,
      String cardHolderId,
      CredentialStatus status,
      LocalDateTime issuedAt,
      LocalDateTime statusChangedAt,
      LocalDateTime lastUsedAt) {
    this.id = id;
    this.cardUid = cardUid;
    this.cardHolderId = cardHolderId;
    this.status = status;
    this.issuedAt = issuedAt;
    this.statusChangedAt = statusChangedAt;
    this.lastUsedAt = lastUsedAt;
  }

  public boolean isActive() {
    return status == CredentialStatus.ACTIVE;
  }

  public boolean isLinked() {
    return cardHolderId != null;
  }

  public boolean can(CredentialAction action) {
    return action.allowedFrom(status);
  }

  /** The actions available in the current status, in the order of {@link CredentialAction}. */
  public List<CredentialAction> availableActions() {
    return Arrays.stream(CredentialAction.values()).filter(this::can).toList();
  }

  /**
   * Applies a transition.
   *
   * @throws IllegalStateException when the action is not allowed from the current status
   */
  public void apply(CredentialAction action, LocalDateTime at) {
    if (!can(action)) {
      throw new IllegalStateException(action + " is not allowed from " + status);
    }
    this.status = action.target();
    this.statusChangedAt = at;
  }

  public void linkTo(String cardHolderId) {
    this.cardHolderId = cardHolderId;
  }

  public void markUsed(LocalDateTime at) {
    this.lastUsedAt = at;
  }

  public String getId() {
    return id;
  }

  public String getCardUid() {
    return cardUid;
  }

  public String getCardHolderId() {
    return cardHolderId;
  }

  public CredentialStatus getStatus() {
    return status;
  }

  public LocalDateTime getIssuedAt() {
    return issuedAt;
  }

  public LocalDateTime getStatusChangedAt() {
    return statusChangedAt;
  }

  public LocalDateTime getLastUsedAt() {
    return lastUsedAt;
  }
}
