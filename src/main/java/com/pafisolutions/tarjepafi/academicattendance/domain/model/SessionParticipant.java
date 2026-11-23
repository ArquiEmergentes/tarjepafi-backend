package com.pafisolutions.tarjepafi.academicattendance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A teacher invited to a meeting or training. */
@Entity
@Table(name = "session_participants")
public class SessionParticipant {

  @Id private String id;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(name = "card_holder_id", nullable = false)
  private String cardHolderId;

  protected SessionParticipant() {}

  public SessionParticipant(String id, String sessionId, String cardHolderId) {
    this.id = id;
    this.sessionId = sessionId;
    this.cardHolderId = cardHolderId;
  }

  public String getId() {
    return id;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getCardHolderId() {
    return cardHolderId;
  }
}
