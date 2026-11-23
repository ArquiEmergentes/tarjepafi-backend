package com.pafisolutions.tarjepafi.iotmonitoring.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** A problem detected on a reader, or reported by something that claims to be one. */
@Entity
@Table(name = "incidents")
public class Incident {

  @Id private String id;

  @Column(name = "reader_id", nullable = false)
  private String readerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IncidentType type;

  @Column(name = "detected_at", nullable = false)
  private LocalDateTime detectedAt;

  @Column(nullable = false)
  private String description;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  protected Incident() {}

  public Incident(
      String id,
      String readerId,
      IncidentType type,
      LocalDateTime detectedAt,
      String description,
      LocalDateTime resolvedAt) {
    this.id = id;
    this.readerId = readerId;
    this.type = type;
    this.detectedAt = detectedAt;
    this.description = description.length() <= 500 ? description : description.substring(0, 500);
    this.resolvedAt = resolvedAt;
  }

  public boolean isOpen() {
    return resolvedAt == null;
  }

  public void resolve(LocalDateTime at) {
    if (resolvedAt == null) {
      this.resolvedAt = at;
    }
  }

  public String getId() {
    return id;
  }

  public String getReaderId() {
    return readerId;
  }

  public IncidentType getType() {
    return type;
  }

  public LocalDateTime getDetectedAt() {
    return detectedAt;
  }

  public String getDescription() {
    return description;
  }

  public LocalDateTime getResolvedAt() {
    return resolvedAt;
  }
}
