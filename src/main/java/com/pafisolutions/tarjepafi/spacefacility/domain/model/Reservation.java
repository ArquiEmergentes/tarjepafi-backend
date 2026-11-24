package com.pafisolutions.tarjepafi.spacefacility.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * The reservation of a space for a time slot (SpaceReservation of the report). It is CONFIRMED when created and
 * the holder has {@link #CHECK_IN_TOLERANCE} from the start to tap the card at the space: then it is CHECKED_IN,
 * and COMPLETED when the slot ends. Without that tap it is FORFEITED and the slot is free again. An administrator
 * can CANCEL it while it is still CONFIRMED.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

  /** Rule of US28: the space is released 10 minutes after the start when nobody checked in. */
  public static final Duration CHECK_IN_TOLERANCE = Duration.ofMinutes(10);

  public enum Status {
    CONFIRMED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED,
    FORFEITED
  }

  /** What a tap of a holder means for one of their reservations. */
  public enum CheckInResult {
    CHECKED_IN,
    ALREADY_ACTIVE,
    NOT_STARTED,
    EXPIRED
  }

  @Id private String id;

  @Generated(event = EventType.INSERT)
  @Column(name = "seq", insertable = false, updatable = false)
  private Long seq;

  @Column(name = "reservation_code", nullable = false, updatable = false)
  private String reservationCode;

  @Column(name = "space_id", nullable = false, updatable = false)
  private String spaceId;

  @Column(name = "card_holder_id", nullable = false, updatable = false)
  private String cardHolderId;

  @Column(name = "start_time", nullable = false, updatable = false)
  private LocalDateTime startTime;

  @Column(name = "end_time", nullable = false, updatable = false)
  private LocalDateTime endTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(name = "check_in_window_end", nullable = false, updatable = false)
  private LocalDateTime checkInWindowEnd;

  @Column(name = "checked_in_at")
  private LocalDateTime checkedInAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Version private long version;

  protected Reservation() {}

  /** A CONFIRMED reservation whose check-in window ends {@link #CHECK_IN_TOLERANCE} after the start. */
  public Reservation(
      String id,
      String reservationCode,
      String spaceId,
      String cardHolderId,
      LocalDateTime startTime,
      LocalDateTime endTime,
      LocalDateTime createdAt) {
    this.id = id;
    this.reservationCode = reservationCode;
    this.spaceId = spaceId;
    this.cardHolderId = cardHolderId;
    this.startTime = startTime;
    this.endTime = endTime;
    this.createdAt = createdAt;
    this.status = Status.CONFIRMED;
    this.checkInWindowEnd = startTime.plus(CHECK_IN_TOLERANCE);
  }

  /** Whether the slot [start, end] overlaps this reservation's slot (touching ends do not overlap). */
  public boolean overlaps(LocalDateTime start, LocalDateTime end) {
    return startTime.isBefore(end) && endTime.isAfter(start);
  }

  public boolean coversInstant(LocalDateTime at) {
    return !at.isBefore(startTime) && !at.isAfter(endTime);
  }

  public boolean isCheckInExpired(LocalDateTime at) {
    return at.isAfter(checkInWindowEnd);
  }

  /** What the holder's tap at {@code at} does to this reservation; it only changes it when CHECKED_IN. */
  public CheckInResult checkIn(LocalDateTime at) {
    if (status == Status.CHECKED_IN) {
      return CheckInResult.ALREADY_ACTIVE;
    }
    if (status != Status.CONFIRMED) {
      throw new IllegalStateException("Reservation " + id + " is " + status);
    }
    if (at.isBefore(startTime)) {
      return CheckInResult.NOT_STARTED;
    }
    if (isCheckInExpired(at)) {
      return CheckInResult.EXPIRED;
    }
    this.status = Status.CHECKED_IN;
    this.checkedInAt = at;
    return CheckInResult.CHECKED_IN;
  }

  /** No check-in within the window: the reservation is lost. Only a CONFIRMED one can be forfeited. */
  public void forfeit() {
    requireStatus(Status.CONFIRMED, "forfeited");
    this.status = Status.FORFEITED;
  }

  /** A CHECKED_IN reservation whose slot ended. */
  public void complete() {
    requireStatus(Status.CHECKED_IN, "completed");
    this.status = Status.COMPLETED;
  }

  /** Administrative cancellation; only a CONFIRMED reservation can be cancelled. */
  public void cancel() {
    requireStatus(Status.CONFIRMED, "cancelled");
    this.status = Status.CANCELLED;
  }

  public boolean isOpen() {
    return status == Status.CONFIRMED || status == Status.CHECKED_IN;
  }

  private void requireStatus(Status expected, String action) {
    if (status != expected) {
      throw new IllegalStateException("Reservation " + id + " is " + status + " and cannot be " + action);
    }
  }

  public String getId() {
    return id;
  }

  public Long getSeq() {
    return seq;
  }

  public String getReservationCode() {
    return reservationCode;
  }

  public String getSpaceId() {
    return spaceId;
  }

  public String getCardHolderId() {
    return cardHolderId;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public Status getStatus() {
    return status;
  }

  public LocalDateTime getCheckInWindowEnd() {
    return checkInWindowEnd;
  }

  public LocalDateTime getCheckedInAt() {
    return checkedInAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
