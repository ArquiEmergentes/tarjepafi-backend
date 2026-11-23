package com.pafisolutions.tarjepafi.credentials.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** The single row of campus settings. */
@Entity
@Table(name = "settings")
public class CampusSettings {

  public static final String ID = "campus";

  @Id private String id;

  @Column(nullable = false)
  private String term;

  @Column(name = "enrollment_deadline", nullable = false)
  private LocalDateTime enrollmentDeadline;

  @Column(name = "default_tolerance_minutes", nullable = false)
  private int defaultToleranceMinutes;

  protected CampusSettings() {}

  public CampusSettings(String term, LocalDateTime enrollmentDeadline, int defaultToleranceMinutes) {
    this.id = ID;
    this.term = term;
    this.enrollmentDeadline = enrollmentDeadline;
    this.defaultToleranceMinutes = defaultToleranceMinutes;
  }

  public String getTerm() {
    return term;
  }

  public LocalDateTime getEnrollmentDeadline() {
    return enrollmentDeadline;
  }

  public int getDefaultToleranceMinutes() {
    return defaultToleranceMinutes;
  }
}
