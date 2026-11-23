package com.pafisolutions.tarjepafi.credentials.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The person a card belongs to. Its data comes from the (simulated) academic system. */
@Entity
@Table(name = "card_holders")
public class CardHolder {

  @Id private String id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private HolderRole role;

  @Column(nullable = false)
  private String program;

  @Enumerated(EnumType.STRING)
  @Column(name = "enrollment_status")
  private EnrollmentStatus enrollmentStatus;

  protected CardHolder() {}

  public CardHolder(
      String id,
      String code,
      String fullName,
      String email,
      HolderRole role,
      String program,
      EnrollmentStatus enrollmentStatus) {
    this.id = id;
    this.code = code;
    this.fullName = fullName;
    this.email = email;
    this.role = role;
    this.program = program;
    this.enrollmentStatus = enrollmentStatus;
  }

  public boolean isStudent() {
    return role == HolderRole.STUDENT;
  }

  /** A student without a completed enrollment may not keep an active card. */
  public boolean hasPendingEnrollment() {
    return isStudent() && enrollmentStatus != EnrollmentStatus.COMPLETED;
  }

  public String getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getFullName() {
    return fullName;
  }

  public String getEmail() {
    return email;
  }

  public HolderRole getRole() {
    return role;
  }

  public String getProgram() {
    return program;
  }

  public EnrollmentStatus getEnrollmentStatus() {
    return enrollmentStatus;
  }
}
