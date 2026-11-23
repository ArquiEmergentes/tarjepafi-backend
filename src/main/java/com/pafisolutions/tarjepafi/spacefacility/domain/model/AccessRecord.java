package com.pafisolutions.tarjepafi.spacefacility.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * An attempt to enter a space or the campus (US12). {@code accessType}: CAMPUS_ENTRY, CAMPUS_EXIT or
 * SPACE_ACCESS; {@code decision}: GRANTED or DENIED; {@code denialReason}: NONE, CARD_NOT_REGISTERED,
 * CARD_INACTIVE, UNAUTHORIZED_ROLE or SPACE_IN_MAINTENANCE.
 */
@Entity
@Table(name = "access_records")
public class AccessRecord {

  @Id private String id;

  @Generated(event = EventType.INSERT)
  @Column(name = "seq", insertable = false, updatable = false)
  private Long seq;

  @Column(name = "tap_event_id", nullable = false)
  private String tapEventId;

  @Column(name = "card_uid", nullable = false)
  private String cardUid;

  @Column(name = "credential_id")
  private String credentialId;

  @Column(name = "card_holder_id")
  private String cardHolderId;

  @Column(name = "space_id", nullable = false)
  private String spaceId;

  @Column(name = "reader_id", nullable = false)
  private String readerId;

  @Column(name = "access_type", nullable = false)
  private String accessType;

  @Column(nullable = false)
  private String decision;

  @Column(name = "denial_reason", nullable = false)
  private String denialReason;

  @Column(name = "attempted_at", nullable = false)
  private LocalDateTime attemptedAt;

  protected AccessRecord() {}

  public AccessRecord(
      String id,
      String tapEventId,
      String cardUid,
      String credentialId,
      String cardHolderId,
      String spaceId,
      String readerId,
      String accessType,
      String decision,
      String denialReason,
      LocalDateTime attemptedAt) {
    this.id = id;
    this.tapEventId = tapEventId;
    this.cardUid = cardUid;
    this.credentialId = credentialId;
    this.cardHolderId = cardHolderId;
    this.spaceId = spaceId;
    this.readerId = readerId;
    this.accessType = accessType;
    this.decision = decision;
    this.denialReason = denialReason;
    this.attemptedAt = attemptedAt;
  }

  public boolean isGranted() {
    return "GRANTED".equals(decision);
  }

  public String getId() {
    return id;
  }

  public Long getSeq() {
    return seq;
  }

  public String getTapEventId() {
    return tapEventId;
  }

  public String getCardUid() {
    return cardUid;
  }

  public String getCredentialId() {
    return credentialId;
  }

  public String getCardHolderId() {
    return cardHolderId;
  }

  public String getSpaceId() {
    return spaceId;
  }

  public String getReaderId() {
    return readerId;
  }

  public String getAccessType() {
    return accessType;
  }

  public String getDecision() {
    return decision;
  }

  public String getDenialReason() {
    return denialReason;
  }

  public LocalDateTime getAttemptedAt() {
    return attemptedAt;
  }
}
