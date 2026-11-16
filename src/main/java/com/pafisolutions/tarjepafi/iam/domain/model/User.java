package com.pafisolutions.tarjepafi.iam.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** A person who can sign in. Students and staff exist as users but have no web app screens yet. */
@Entity
@Table(name = "users")
public class User {

  @Id private String id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status;

  @Column(name = "card_holder_id")
  private String cardHolderId;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected User() {}

  public User(
      String id,
      EmailAddress email,
      String passwordHash,
      UserRole role,
      UserStatus status,
      String cardHolderId,
      String fullName,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.id = id;
    this.email = email.value();
    this.passwordHash = passwordHash;
    this.role = role;
    this.status = status;
    this.cardHolderId = cardHolderId;
    this.fullName = fullName;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Only active accounts may sign in or keep using a token. */
  public boolean isEnabled() {
    return status == UserStatus.ACTIVE;
  }

  public String getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserRole getRole() {
    return role;
  }

  public UserStatus getStatus() {
    return status;
  }

  public String getCardHolderId() {
    return cardHolderId;
  }

  public String getFullName() {
    return fullName;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
