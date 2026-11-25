package com.pafisolutions.tarjepafi.academicattendance.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
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
      @Schema(example = "ses-02-7") String id,
      @Schema(example = "CLASS") String type,
      @Schema(example = "sch-02") String scheduleId,
      @Schema(example = "Aplicaciones Web · SW51") String title,
      @Schema(example = "sp-a301") String spaceId,
      @Schema(example = "2026-10-07") LocalDate date,
      @Schema(example = "2026-10-07T10:00:00") LocalDateTime scheduledStart,
      @Schema(example = "2026-10-07T11:30:00") LocalDateTime scheduledEnd,
      @Schema(example = "10") int toleranceMinutes,
      @Schema(example = "IN_PROGRESS") String status) {

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

  public record CourseResource(@Schema(example = "crs-01") String id, @Schema(example = "1ASI0728") String code, @Schema(example = "Aplicaciones Web") String name, @Schema(example = "Desarrollo de Software") String subject, @Schema(example = "SW51") String section) {

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
      @Schema(example = "att-01004") String id,
      @Schema(example = "105") long cursor,
      @Schema(example = "07b3d078-c9a5-48b6-9f91-6bcd3d880d7e") String tapEventId,
      @Schema(example = "ses-02-7") String sessionId,
      @Schema(example = "ch-u202312966") String cardHolderId,
      @Schema(example = "STUDENT") String role,
      @Schema(example = "2026-10-07T10:02:00") LocalDateTime checkInAt,
      @Schema(example = "2026-10-07T11:20:00") LocalDateTime checkOutAt,
      @Schema(example = "78") Integer durationMinutes,
      @Schema(example = "PRESENT") String status,
      @Schema(example = "2026-10-07T10:03:00") LocalDateTime confirmedAt,
      @Schema(example = "NFC") String source,
      @Schema(example = "usr-002") String modifiedBy,
      @Schema(example = "2026-10-07T11:40:00") LocalDateTime modifiedAt,
      @Schema(example = "El lector no registro su tarjeta") String editReason) {

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
      @Schema(example = "0") int pending, @Schema(example = "1") int present, @Schema(example = "1") int late, @Schema(example = "17") int absent, @Schema(example = "1") int justified, @Schema(example = "0") int unmarked, @Schema(example = "20") int total) {

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
      @Schema(example = "ch-u202312966") String cardHolderId, @Schema(example = "Ana Torres") String fullName, @Schema(example = "U202312966") String code, AttendanceRecordResource record) {}

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

  public record AttendanceRecordPage(List<AttendanceRecordResource> items, @Schema(example = "105") String nextCursor) {}

  /** {@code status}: PRESENT, LATE, ABSENT or JUSTIFIED. */
  public record EditAttendanceRequest(
      @Schema(example = "PRESENT") @NotNull AttendanceStatus status, @Schema(example = "El lector no registro su tarjeta") @NotBlank @Size(max = 255) String reason) {}

  public record JustifyRequest(@Schema(example = "Certificado medico") @NotBlank @Size(max = 255) String reason) {}

  public record ConfirmedResource(@Schema(example = "3") int confirmed) {}
}
