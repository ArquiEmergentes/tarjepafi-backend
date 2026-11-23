package com.pafisolutions.tarjepafi.academicattendance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** One person's attendance in one session (port of {@code attendance-record.entity.ts}). */
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {

  public static final int EDIT_REASON_MAX_LENGTH = 255;

  @Id private String id;

  /** Position in the table, assigned by the database: the cursor of the polling endpoints. */
  @Generated(event = EventType.INSERT)
  @Column(name = "seq", insertable = false, updatable = false)
  private Long seq;

  @Column(name = "tap_event_id")
  private String tapEventId;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(name = "card_holder_id", nullable = false)
  private String cardHolderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AttendanceRole role;

  @Column(name = "check_in_at")
  private LocalDateTime checkInAt;

  @Column(name = "check_out_at")
  private LocalDateTime checkOutAt;

  @Column(name = "duration_minutes")
  private Integer durationMinutes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AttendanceStatus status;

  @Column(name = "confirmed_at")
  private LocalDateTime confirmedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AttendanceSource source;

  @Column(name = "modified_by")
  private String modifiedBy;

  @Column(name = "modified_at")
  private LocalDateTime modifiedAt;

  @Column(name = "edit_reason")
  private String editReason;

  protected AttendanceRecord() {}

  public AttendanceRecord(
      String id,
      String tapEventId,
      String sessionId,
      String cardHolderId,
      AttendanceRole role,
      LocalDateTime checkInAt,
      AttendanceStatus status,
      LocalDateTime confirmedAt,
      AttendanceSource source) {
    this.id = id;
    this.tapEventId = tapEventId;
    this.sessionId = sessionId;
    this.cardHolderId = cardHolderId;
    this.role = role;
    this.checkInAt = checkInAt;
    this.status = status;
    this.confirmedAt = confirmedAt;
    this.source = source;
  }

  /** PRESENT when the mark is not after start + tolerance; otherwise LATE. */
  public static AttendanceStatus resolveStatus(
      LocalDateTime checkInAt, LocalDateTime punctualUntil) {
    return checkInAt.isAfter(punctualUntil) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
  }

  public boolean isPending() {
    return status == AttendanceStatus.PENDING;
  }

  public boolean attended() {
    return status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE;
  }

  public boolean hasCheckedOut() {
    return checkOutAt != null;
  }

  /** Confirms a PENDING mark with the status its own time deserves. */
  public void confirm(LocalDateTime at, LocalDateTime punctualUntil) {
    if (!isPending() || checkInAt == null) {
      return;
    }
    status = resolveStatus(checkInAt, punctualUntil);
    confirmedAt = at;
  }

  /** The teacher leaves: stores the time and the duration in minutes. */
  public void checkOut(LocalDateTime at) {
    checkOutAt = at;
    durationMinutes = checkInAt == null ? null : (int) Duration.between(checkInAt, at).toMinutes();
  }

  /** Rule 6.E: the teacher changes a status and must say why. */
  public void edit(AttendanceStatus newStatus, String reason, String by, LocalDateTime at) {
    String trimmed = reason.trim();
    status = newStatus;
    source = AttendanceSource.TEACHER_EDIT;
    modifiedBy = by;
    modifiedAt = at;
    editReason =
        trimmed.length() > EDIT_REASON_MAX_LENGTH
            ? trimmed.substring(0, EDIT_REASON_MAX_LENGTH)
            : trimmed;
    if (confirmedAt == null) {
      confirmedAt = at;
    }
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

  public String getSessionId() {
    return sessionId;
  }

  public String getCardHolderId() {
    return cardHolderId;
  }

  public AttendanceRole getRole() {
    return role;
  }

  public LocalDateTime getCheckInAt() {
    return checkInAt;
  }

  public LocalDateTime getCheckOutAt() {
    return checkOutAt;
  }

  public Integer getDurationMinutes() {
    return durationMinutes;
  }

  public AttendanceStatus getStatus() {
    return status;
  }

  public LocalDateTime getConfirmedAt() {
    return confirmedAt;
  }

  public AttendanceSource getSource() {
    return source;
  }

  public String getModifiedBy() {
    return modifiedBy;
  }

  public LocalDateTime getModifiedAt() {
    return modifiedAt;
  }

  public String getEditReason() {
    return editReason;
  }
}
