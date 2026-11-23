package com.pafisolutions.tarjepafi.academicattendance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A class or a meeting on a given day. Marks are accepted from 15 minutes before the start; a session
 * nobody closes is closed 15 minutes after its end (port of {@code session.entity.ts}).
 */
@Entity
@Table(name = "sessions")
public class Session {

  public static final int EARLY_CHECK_IN_MINUTES = 15;
  public static final int AUTO_CLOSE_MINUTES = 15;

  @Id private String id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SessionType type;

  /** Null for meetings. */
  @Column(name = "schedule_id")
  private String scheduleId;

  @Column(nullable = false)
  private String title;

  @Column(name = "space_id", nullable = false)
  private String spaceId;

  @Column(name = "session_date", nullable = false)
  private LocalDate date;

  @Column(name = "scheduled_start", nullable = false)
  private LocalDateTime scheduledStart;

  @Column(name = "scheduled_end", nullable = false)
  private LocalDateTime scheduledEnd;

  @Column(name = "tolerance_minutes", nullable = false)
  private int toleranceMinutes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SessionStatus status;

  protected Session() {}

  public Session(
      String id,
      SessionType type,
      String scheduleId,
      String title,
      String spaceId,
      LocalDate date,
      LocalDateTime scheduledStart,
      LocalDateTime scheduledEnd,
      int toleranceMinutes,
      SessionStatus status) {
    this.id = id;
    this.type = type;
    this.scheduleId = scheduleId;
    this.title = title;
    this.spaceId = spaceId;
    this.date = date;
    this.scheduledStart = scheduledStart;
    this.scheduledEnd = scheduledEnd;
    this.toleranceMinutes = toleranceMinutes;
    this.status = status;
  }

  public boolean isMeeting() {
    return type == SessionType.MEETING;
  }

  public boolean isFinished() {
    return status == SessionStatus.FINISHED;
  }

  public LocalDateTime opensAt() {
    return scheduledStart.minusMinutes(EARLY_CHECK_IN_MINUTES);
  }

  public LocalDateTime closesAt() {
    return scheduledEnd.plusMinutes(AUTO_CLOSE_MINUTES);
  }

  /** Arriving after this moment is late: start plus tolerance. */
  public LocalDateTime punctualUntil() {
    return scheduledStart.plusMinutes(toleranceMinutes);
  }

  /** Accepts marks: from 15 minutes before the start until the end, and not closed. */
  public boolean isInProgress(LocalDateTime now) {
    return !isFinished() && !now.isBefore(opensAt()) && !now.isAfter(scheduledEnd);
  }

  /** The teacher may mark from the opening until the automatic close (arrival, then departure). */
  public boolean isWithinTeacherWindow(LocalDateTime now) {
    return !now.isBefore(opensAt()) && !now.isAfter(closesAt());
  }

  public boolean shouldAutoClose(LocalDateTime now) {
    return !isFinished() && now.isAfter(closesAt());
  }

  public void start() {
    if (status == SessionStatus.SCHEDULED) {
      status = SessionStatus.IN_PROGRESS;
    }
  }

  public void finish() {
    status = SessionStatus.FINISHED;
  }

  public String getId() {
    return id;
  }

  public SessionType getType() {
    return type;
  }

  public String getScheduleId() {
    return scheduleId;
  }

  public String getTitle() {
    return title;
  }

  public String getSpaceId() {
    return spaceId;
  }

  public LocalDate getDate() {
    return date;
  }

  public LocalDateTime getScheduledStart() {
    return scheduledStart;
  }

  public LocalDateTime getScheduledEnd() {
    return scheduledEnd;
  }

  public int getToleranceMinutes() {
    return toleranceMinutes;
  }

  public SessionStatus getStatus() {
    return status;
  }
}
