package com.pafisolutions.tarjepafi.academicattendance.interfaces.rest;

import com.pafisolutions.tarjepafi.academicattendance.application.TeacherWorkspaceService;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceRecord;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceStatus;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.ClassSchedule;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Course;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Session;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** The JSON shapes of the teacher endpoints. Field names follow the web app's attendance resources. */
public final class AttendanceResources {

  private AttendanceResources() {}

  /** {@code type}: CLASS or MEETING. {@code status}: SCHEDULED, IN_PROGRESS or FINISHED. */
  public record SessionResource(
      String id,
      String type,
      String scheduleId,
      String title,
      String spaceId,
      LocalDate date,
      LocalDateTime scheduledStart,
      LocalDateTime scheduledEnd,
      int toleranceMinutes,
      String status) {

    public static SessionResource from(Session session) {
      return new SessionResource(
          session.getId(),
          session.getType().name(),
          session.getScheduleId(),
          session.getTitle(),
          session.getSpaceId(),
          session.getDate(),
          session.getScheduledStart(),
          session.getScheduledEnd(),
          session.getToleranceMinutes(),
          session.getStatus().name());
    }
  }

  public record CourseResource(String id, String code, String name, String subject, String section) {

    static CourseResource from(Course course, ClassSchedule schedule) {
      return course == null
          ? null
          : new CourseResource(
              course.getId(),
              course.getCode(),
              course.getName(),
              course.getSubject(),
              schedule == null ? null : schedule.getSection());
    }
  }

  /**
   * One attendance mark. {@code status}: PENDING, PRESENT, LATE, ABSENT or JUSTIFIED. {@code source}: NFC,
   * TEACHER_EDIT or SYSTEM. {@code cursor} grows with every new record (poll with it).
   */
  public record AttendanceRecordResource(
      String id,
      long cursor,
      String tapEventId,
      String sessionId,
      String cardHolderId,
      String role,
      LocalDateTime checkInAt,
      LocalDateTime checkOutAt,
      Integer durationMinutes,
      String status,
      LocalDateTime confirmedAt,
      String source,
      String modifiedBy,
      LocalDateTime modifiedAt,
      String editReason) {

    public static AttendanceRecordResource from(AttendanceRecord record) {
      if (record == null) {
        return null;
      }
      return new AttendanceRecordResource(
          record.getId(),
          record.getSeq() == null ? 0 : record.getSeq(),
          record.getTapEventId(),
          record.getSessionId(),
          record.getCardHolderId(),
          record.getRole().name(),
          record.getCheckInAt(),
          record.getCheckOutAt(),
          record.getDurationMinutes(),
          record.getStatus().name(),
          record.getConfirmedAt(),
          record.getSource().name(),
          record.getModifiedBy(),
          record.getModifiedAt(),
          record.getEditReason());
    }
  }

  /** Students per status; {@code unmarked} are enrolled students without a record. */
  public record CountsResource(
      int pending, int present, int late, int absent, int justified, int unmarked, int total) {

    static CountsResource from(TeacherWorkspaceService.Counts counts) {
      return new CountsResource(
          counts.byStatus().get(AttendanceStatus.PENDING),
          counts.byStatus().get(AttendanceStatus.PRESENT),
          counts.byStatus().get(AttendanceStatus.LATE),
          counts.byStatus().get(AttendanceStatus.ABSENT),
          counts.byStatus().get(AttendanceStatus.JUSTIFIED),
          counts.unmarked(),
          counts.total());
    }
  }

  public record SessionSummaryResource(
      SessionResource session,
      CourseResource course,
      AttendanceRecordResource teacherRecord,
      CountsResource counts) {

    static SessionSummaryResource from(TeacherWorkspaceService.SessionSummary summary) {
      return new SessionSummaryResource(
          SessionResource.from(summary.session()),
          CourseResource.from(summary.course(), summary.schedule()),
          AttendanceRecordResource.from(summary.teacherRecord()),
          CountsResource.from(summary.counts()));
    }
  }

  public record RosterRowResource(
      String cardHolderId, String fullName, String code, AttendanceRecordResource record) {}

  public record SessionDetailResource(
      SessionResource session,
      CourseResource course,
      AttendanceRecordResource teacherRecord,
      CountsResource counts,
      List<RosterRowResource> roster) {

    static SessionDetailResource from(TeacherWorkspaceService.SessionDetail detail) {
      SessionSummaryResource summary = SessionSummaryResource.from(detail.summary());
      return new SessionDetailResource(
          summary.session(),
          summary.course(),
          summary.teacherRecord(),
          summary.counts(),
          detail.roster().stream()
              .map(
                  row ->
                      new RosterRowResource(
                          row.cardHolderId(),
                          row.fullName(),
                          row.code(),
                          AttendanceRecordResource.from(row.record())))
              .toList());
    }
  }

  public record MeetingResource(SessionResource session, AttendanceRecordResource record) {}

  public record ClassAttendanceResource(
      AttendanceRecordResource record, SessionResource session, CourseResource course) {}

  public record AttendanceRecordPage(List<AttendanceRecordResource> items, String nextCursor) {}

  /** {@code status}: PRESENT, LATE, ABSENT or JUSTIFIED. */
  public record EditAttendanceRequest(
      @NotNull AttendanceStatus status, @NotBlank @Size(max = 255) String reason) {}

  public record JustifyRequest(@NotBlank @Size(max = 255) String reason) {}

  public record ConfirmedResource(int confirmed) {}
}
