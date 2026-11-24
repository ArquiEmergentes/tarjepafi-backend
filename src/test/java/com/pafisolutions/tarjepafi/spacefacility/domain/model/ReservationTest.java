package com.pafisolutions.tarjepafi.spacefacility.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation.CheckInResult;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.Reservation.Status;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** The lifecycle of a reservation and the 10 minute rule of US28. */
class ReservationTest {

  private static final LocalDateTime START = LocalDateTime.parse("2026-10-07T10:00:00");
  private static final LocalDateTime END = LocalDateTime.parse("2026-10-07T11:00:00");

  private static Reservation reservation() {
    return new Reservation(
        "res-1", "RES-2026-00001", "sp-cub-l1", "ch-1", START, END, LocalDateTime.parse("2026-10-07T09:50:00"));
  }

  @Test
  void new_reservation_isConfirmedWithAWindowOfTenMinutesFromTheStart() {
    Reservation reservation = reservation();

    assertThat(reservation.getStatus()).isEqualTo(Status.CONFIRMED);
    assertThat(reservation.getCheckInWindowEnd()).isEqualTo(LocalDateTime.parse("2026-10-07T10:10:00"));
    assertThat(reservation.getCheckedInAt()).isNull();
  }

  @Test
  void checkIn_insideTheWindow_checksInAndKeepsTheMoment() {
    Reservation reservation = reservation();

    CheckInResult result = reservation.checkIn(LocalDateTime.parse("2026-10-07T10:07:00"));

    assertThat(result).isEqualTo(CheckInResult.CHECKED_IN);
    assertThat(reservation.getStatus()).isEqualTo(Status.CHECKED_IN);
    assertThat(reservation.getCheckedInAt()).isEqualTo(LocalDateTime.parse("2026-10-07T10:07:00"));
  }

  @Test
  void checkIn_exactlyAtTheStartAndAtTheEndOfTheWindow_isAccepted() {
    assertThat(reservation().checkIn(START)).isEqualTo(CheckInResult.CHECKED_IN);
    assertThat(reservation().checkIn(START.plusMinutes(10))).isEqualTo(CheckInResult.CHECKED_IN);
  }

  @Test
  void checkIn_beforeTheStart_isNotStartedAndChangesNothing() {
    Reservation reservation = reservation();

    assertThat(reservation.checkIn(LocalDateTime.parse("2026-10-07T09:59:59"))).isEqualTo(CheckInResult.NOT_STARTED);
    assertThat(reservation.getStatus()).isEqualTo(Status.CONFIRMED);
  }

  @Test
  void checkIn_afterTheWindow_isExpiredAndChangesNothing() {
    Reservation reservation = reservation();

    assertThat(reservation.checkIn(LocalDateTime.parse("2026-10-07T10:10:01"))).isEqualTo(CheckInResult.EXPIRED);
    assertThat(reservation.getStatus()).isEqualTo(Status.CONFIRMED);
  }

  @Test
  void checkIn_twice_isAlreadyActive() {
    Reservation reservation = reservation();
    reservation.checkIn(START);

    assertThat(reservation.checkIn(START.plusMinutes(30))).isEqualTo(CheckInResult.ALREADY_ACTIVE);
  }

  @Test
  void forfeit_onlyApplyToAConfirmedReservation() {
    Reservation reservation = reservation();
    reservation.forfeit();
    assertThat(reservation.getStatus()).isEqualTo(Status.FORFEITED);

    Reservation checkedIn = reservation();
    checkedIn.checkIn(START);
    assertThatThrownBy(checkedIn::forfeit).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void complete_onlyApplyToACheckedInReservation() {
    Reservation checkedIn = reservation();
    checkedIn.checkIn(START);
    checkedIn.complete();
    assertThat(checkedIn.getStatus()).isEqualTo(Status.COMPLETED);

    assertThatThrownBy(reservation()::complete).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void cancel_onlyApplyToAConfirmedReservation() {
    Reservation reservation = reservation();
    reservation.cancel();
    assertThat(reservation.getStatus()).isEqualTo(Status.CANCELLED);
    assertThat(reservation.isOpen()).isFalse();

    assertThatThrownBy(reservation::cancel).isInstanceOf(IllegalStateException.class);
    Reservation checkedIn = reservation();
    checkedIn.checkIn(START);
    assertThatThrownBy(checkedIn::cancel).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void checkIn_ofAFinishedReservation_isRefused() {
    Reservation reservation = reservation();
    reservation.forfeit();

    assertThatThrownBy(() -> reservation.checkIn(START)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void overlaps_slotsThatShareTimeOverlapAndTouchingOnesDoNot() {
    Reservation reservation = reservation();

    assertThat(reservation.overlaps(START.minusMinutes(30), START.plusMinutes(1))).isTrue();
    assertThat(reservation.overlaps(START.plusMinutes(10), START.plusMinutes(20))).isTrue();
    assertThat(reservation.overlaps(END.minusMinutes(1), END.plusHours(1))).isTrue();
    assertThat(reservation.overlaps(END, END.plusHours(1))).isFalse();
    assertThat(reservation.overlaps(START.minusHours(1), START)).isFalse();
  }

  @Test
  void coversInstant_includesBothEnds() {
    Reservation reservation = reservation();

    assertThat(reservation.coversInstant(START)).isTrue();
    assertThat(reservation.coversInstant(END)).isTrue();
    assertThat(reservation.coversInstant(END.plusSeconds(1))).isFalse();
  }
}
