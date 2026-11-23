package com.pafisolutions.tarjepafi.spacefacility.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** A staff member's working day, opened at the campus entrance and closed at the exit (US24). */
@Entity
@Table(name = "work_shifts")
public class WorkShift {

  public static final String OPEN = "OPEN";
  public static final String CLOSED = "CLOSED";

  @Id private String id;

  @Generated(event = EventType.INSERT)
  @Column(name = "seq", insertable = false, updatable = false)
  private Long seq;

  @Column(name = "card_holder_id", nullable = false)
  private String cardHolderId;

  @Column(name = "shift_date", nullable = false)
  private LocalDate date;

  @Column(name = "entry_at", nullable = false)
  private LocalDateTime entryAt;

  @Column(name = "exit_at")
  private LocalDateTime exitAt;

  @Column(name = "worked_minutes")
  private Integer workedMinutes;

  @Column(nullable = false)
  private String status;

  protected WorkShift() {}

  private WorkShift(
      String id,
      String cardHolderId,
      LocalDate date,
      LocalDateTime entryAt,
      LocalDateTime exitAt,
      Integer workedMinutes,
      String status) {
    this.id = id;
    this.cardHolderId = cardHolderId;
    this.date = date;
    this.entryAt = entryAt;
    this.exitAt = exitAt;
    this.workedMinutes = workedMinutes;
    this.status = status;
  }

  public static WorkShift open(String id, String cardHolderId, LocalDateTime at) {
    return new WorkShift(id, cardHolderId, at.toLocalDate(), at, null, null, OPEN);
  }

  /** Used by the demo seed to load a shift that already happened. */
  public static WorkShift of(
      String id,
      String cardHolderId,
      LocalDate date,
      LocalDateTime entryAt,
      LocalDateTime exitAt,
      Integer workedMinutes,
      String status) {
    return new WorkShift(id, cardHolderId, date, entryAt, exitAt, workedMinutes, status);
  }

  public boolean isOpen() {
    return OPEN.equals(status);
  }

  public void close(LocalDateTime at) {
    exitAt = at;
    workedMinutes = (int) Duration.between(entryAt, at).toMinutes();
    status = CLOSED;
  }

  public String getId() {
    return id;
  }

  public Long getSeq() {
    return seq;
  }

  public String getCardHolderId() {
    return cardHolderId;
  }

  public LocalDate getDate() {
    return date;
  }

  public LocalDateTime getEntryAt() {
    return entryAt;
  }

  public LocalDateTime getExitAt() {
    return exitAt;
  }

  public Integer getWorkedMinutes() {
    return workedMinutes;
  }

  public String getStatus() {
    return status;
  }
}
