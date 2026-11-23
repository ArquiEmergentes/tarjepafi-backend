package com.pafisolutions.tarjepafi.academicattendance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Port of {@code attendance-domain.spec.ts} of the old web app (rule 6.C). */
class AttendanceDomainTest {

  private static LocalDateTime at(String time) {
    return LocalDateTime.parse("2026-10-07T" + time);
  }

  private static Session session(SessionStatus status) {
    return new Session(
        "ses-1",
        SessionType.CLASS,
        "sch-01",
        "Aplicaciones Web",
        "sp-a301",
        LocalDate.parse("2026-10-07"),
        at("10:00:00"),
        at("11:30:00"),
        10,
        status);
  }

  private static AttendanceRecord record(AttendanceStatus status, LocalDateTime checkIn) {
    return new AttendanceRecord(
        "att-1",
        "evt-1",
        "ses-1",
        "ch-u1",
        AttendanceRole.STUDENT,
        checkIn,
        status,
        status == AttendanceStatus.PENDING ? null : checkIn,
        AttendanceSource.NFC);
  }

  @Test
  void isInProgress_fromFifteenMinutesBeforeTheStartUntilTheEnd() {
    Session session = session(SessionStatus.SCHEDULED);

    assertThat(session.isInProgress(at("09:44:59"))).isFalse();
    assertThat(session.isInProgress(at("09:45:00"))).isTrue();
    assertThat(session.isInProgress(at("11:30:00"))).isTrue();
    assertThat(session.isInProgress(at("11:30:01"))).isFalse();
    assertThat(session(SessionStatus.FINISHED).isInProgress(at("10:00:00"))).isFalse();
  }

  @Test
  void isWithinTeacherWindow_goesOnUntilTheAutomaticClose() {
    Session session = session(SessionStatus.IN_PROGRESS);

    assertThat(session.isWithinTeacherWindow(at("11:45:00"))).isTrue();
    assertThat(session.isWithinTeacherWindow(at("11:45:01"))).isFalse();
    assertThat(session.isWithinTeacherWindow(at("09:44:59"))).isFalse();
  }

  @Test
  void shouldAutoClose_fifteenMinutesAfterTheEnd() {
    assertThat(session(SessionStatus.IN_PROGRESS).shouldAutoClose(at("11:45:00"))).isFalse();
    assertThat(session(SessionStatus.IN_PROGRESS).shouldAutoClose(at("11:45:01"))).isTrue();
    assertThat(session(SessionStatus.FINISHED).shouldAutoClose(at("12:00:00"))).isFalse();
  }

  @Test
  void punctualUntil_isTheStartPlusTheTolerance() {
    assertThat(session(SessionStatus.SCHEDULED).punctualUntil()).isEqualTo(at("10:10:00"));
  }

  @Test
  void resolveStatus_presentUpToStartPlusTolerance_otherwiseLate() {
    assertThat(AttendanceRecord.resolveStatus(at("10:10:00"), at("10:10:00")))
        .isEqualTo(AttendanceStatus.PRESENT);
    assertThat(AttendanceRecord.resolveStatus(at("10:10:01"), at("10:10:00")))
        .isEqualTo(AttendanceStatus.LATE);
  }

  @Test
  void confirm_pendingRecord_usesItsOwnTime() {
    AttendanceRecord late = record(AttendanceStatus.PENDING, at("10:20:00"));
    late.confirm(at("10:25:00"), at("10:10:00"));
    AttendanceRecord onTime = record(AttendanceStatus.PENDING, at("10:05:00"));
    onTime.confirm(at("10:25:00"), at("10:10:00"));

    assertThat(late.getStatus()).isEqualTo(AttendanceStatus.LATE);
    assertThat(late.getConfirmedAt()).isEqualTo(at("10:25:00"));
    assertThat(onTime.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
  }

  @Test
  void confirm_recordThatIsNotPending_isLeftAlone() {
    AttendanceRecord absent = record(AttendanceStatus.ABSENT, null);

    absent.confirm(at("10:25:00"), at("10:10:00"));

    assertThat(absent.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
  }

  @Test
  void checkOut_calculatesTheDuration() {
    AttendanceRecord teacher = record(AttendanceStatus.PRESENT, at("10:02:00"));

    teacher.checkOut(at("11:30:00"));

    assertThat(teacher.getDurationMinutes()).isEqualTo(88);
    assertThat(teacher.hasCheckedOut()).isTrue();
  }

  @Test
  void edit_keepsOriginAuthorTimeAndTrimmedReason() {
    AttendanceRecord record = record(AttendanceStatus.ABSENT, null);

    record.edit(AttendanceStatus.PRESENT, "  Justificacion medica  ", "usr-002", at("12:00:00"));

    assertThat(record.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    assertThat(record.getSource()).isEqualTo(AttendanceSource.TEACHER_EDIT);
    assertThat(record.getModifiedBy()).isEqualTo("usr-002");
    assertThat(record.getModifiedAt()).isEqualTo(at("12:00:00"));
    assertThat(record.getEditReason()).isEqualTo("Justificacion medica");
    assertThat(record.getConfirmedAt()).isEqualTo(at("12:00:00"));
  }

  @Test
  void edit_reasonLongerThan255_isCut() {
    AttendanceRecord record = record(AttendanceStatus.ABSENT, null);

    record.edit(AttendanceStatus.LATE, "x".repeat(300), "usr-002", at("12:00:00"));

    assertThat(record.getEditReason()).hasSize(255);
  }
}
