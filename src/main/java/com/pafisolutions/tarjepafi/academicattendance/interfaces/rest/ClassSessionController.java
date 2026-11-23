package com.pafisolutions.tarjepafi.academicattendance.interfaces.rest;

import com.pafisolutions.tarjepafi.academicattendance.application.TeacherWorkspaceService;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceStatus;
import com.pafisolutions.tarjepafi.academicattendance.interfaces.rest.AttendanceResources.AttendanceRecordPage;
import com.pafisolutions.tarjepafi.academicattendance.interfaces.rest.AttendanceResources.AttendanceRecordResource;
import com.pafisolutions.tarjepafi.academicattendance.interfaces.rest.AttendanceResources.ClassAttendanceResource;
import com.pafisolutions.tarjepafi.academicattendance.interfaces.rest.AttendanceResources.ConfirmedResource;
import com.pafisolutions.tarjepafi.academicattendance.interfaces.rest.AttendanceResources.CourseResource;
import com.pafisolutions.tarjepafi.academicattendance.interfaces.rest.AttendanceResources.EditAttendanceRequest;
import com.pafisolutions.tarjepafi.academicattendance.interfaces.rest.AttendanceResources.JustifyRequest;
import com.pafisolutions.tarjepafi.academicattendance.interfaces.rest.AttendanceResources.MeetingResource;
import com.pafisolutions.tarjepafi.academicattendance.interfaces.rest.AttendanceResources.SessionDetailResource;
import com.pafisolutions.tarjepafi.academicattendance.interfaces.rest.AttendanceResources.SessionResource;
import com.pafisolutions.tarjepafi.academicattendance.interfaces.rest.AttendanceResources.SessionSummaryResource;
import com.pafisolutions.tarjepafi.shared.application.security.AuthenticatedUser;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The teacher's view of their classes and meetings (TS14 to TS17). Needs the TEACHER role. */
@RestController
@RequestMapping("/api/v1/class-sessions")
@Tag(name = "Class sessions (teacher)")
public class ClassSessionController {

  private final TeacherWorkspaceService workspace;

  public ClassSessionController(TeacherWorkspaceService workspace) {
    this.workspace = workspace;
  }

  @GetMapping("/mine")
  @Operation(
      summary = "My classes of a day, with attendance counts",
      description =
          "`date` (yyyy-MM-dd) defaults to today by the campus clock. Sessions past their end plus 15 minutes "
              + "are closed before the answer is built.")
  public List<SessionSummaryResource> mine(
      @AuthenticationPrincipal AuthenticatedUser caller,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return workspace.sessionsOn(holder(caller), date).stream()
        .map(SessionSummaryResource::from)
        .toList();
  }

  @GetMapping("/meetings/mine")
  @Operation(summary = "Meetings and trainings I am invited to, newest first, with my mark")
  public List<MeetingResource> meetings(@AuthenticationPrincipal AuthenticatedUser caller) {
    return workspace.myMeetings(holder(caller)).stream()
        .map(row -> new MeetingResource(SessionResource.from(row.session()), AttendanceRecordResource.from(row.record())))
        .toList();
  }

  @GetMapping("/attendance/mine")
  @Operation(summary = "My own marks in my classes (arrival, departure, duration), newest first")
  public List<ClassAttendanceResource> myAttendance(@AuthenticationPrincipal AuthenticatedUser caller) {
    return workspace.myClassAttendance(holder(caller)).stream()
        .map(
            item ->
                new ClassAttendanceResource(
                    AttendanceRecordResource.from(item.record()),
                    SessionResource.from(item.session()),
                    CourseResource.from(item.course(), null)))
        .toList();
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "One of my classes with its roster",
      description = "404 SESSION_NOT_FOUND, 403 NOT_YOUR_SESSION.")
  public SessionDetailResource detail(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String id) {
    return SessionDetailResource.from(workspace.detail(holder(caller), id));
  }

  @GetMapping("/{id}/attendance")
  @Operation(
      summary = "Attendance records of a session, newest first (poll with `after`)",
      description =
          "Send the previous `nextCursor` as `after` to get only the records created since. 404 "
              + "SESSION_NOT_FOUND, 403 NOT_YOUR_SESSION, 400 INVALID_CURSOR. A record that changes keeps its "
              + "cursor: reload the roster (`GET /{id}`) to see edits.")
  public AttendanceRecordPage records(
      @AuthenticationPrincipal AuthenticatedUser caller,
      @PathVariable String id,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) Integer limit) {
    TeacherWorkspaceService.RecordPage page = workspace.recordsOf(holder(caller), id, after, limit);
    return new AttendanceRecordPage(
        page.items().stream().map(AttendanceRecordResource::from).toList(), page.nextCursor());
  }

  @PostMapping("/{id}/confirm-pending")
  @Operation(
      summary = "Confirm the PENDING marks of a session (TS14)",
      description =
          "Each one becomes PRESENT or LATE by its own time. 409 NOTHING_TO_CONFIRM, 409 SESSION_NOT_STARTED.")
  public ConfirmedResource confirmPending(
      @AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String id) {
    return new ConfirmedResource(workspace.confirmPending(holder(caller), id));
  }

  @PutMapping("/{id}/attendance/{studentId}")
  @Operation(
      summary = "Set a student's status with a reason (TS15)",
      description =
          "Status PRESENT, LATE, ABSENT or JUSTIFIED; `reason` is required (at most 255). Creates the record "
              + "when the student has none. 400 REASON_REQUIRED / STATUS_NOT_ALLOWED, 409 SESSION_NOT_STARTED, "
              + "422 STUDENT_NOT_ENROLLED.")
  public AttendanceRecordResource edit(
      @AuthenticationPrincipal AuthenticatedUser caller,
      @PathVariable String id,
      @PathVariable String studentId,
      @Valid @RequestBody EditAttendanceRequest body) {
    return AttendanceRecordResource.from(
        workspace.edit(holder(caller), caller.id(), id, studentId, body.status(), body.reason()));
  }

  @PostMapping("/{id}/attendance/{studentId}/justify")
  @Operation(
      summary = "Justify an absence (TS16)",
      description = "Sets JUSTIFIED with the reason. Same errors as setting a status.")
  public AttendanceRecordResource justify(
      @AuthenticationPrincipal AuthenticatedUser caller,
      @PathVariable String id,
      @PathVariable String studentId,
      @Valid @RequestBody JustifyRequest body) {
    return AttendanceRecordResource.from(
        workspace.edit(
            holder(caller), caller.id(), id, studentId, AttendanceStatus.JUSTIFIED, body.reason()));
  }

  private static String holder(AuthenticatedUser caller) {
    if (caller.cardHolderId() == null) {
      throw new BusinessRuleException(
          Kind.FORBIDDEN, "NO_CARD_HOLDER", "The account is not linked to a card holder");
    }
    return caller.cardHolderId();
  }
}
