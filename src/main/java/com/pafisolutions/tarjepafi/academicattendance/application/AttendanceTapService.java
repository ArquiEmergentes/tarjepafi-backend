package com.pafisolutions.tarjepafi.academicattendance.application;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceRecord;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceRole;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceSource;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceStatus;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.ClassSchedule;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Course;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Session;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.SessionType;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.AttendanceRecordRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.ClassScheduleRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.CourseRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.EnrollmentRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.SessionParticipantRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.SessionRepository;
import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import com.pafisolutions.tarjepafi.shared.application.TapContext;
import com.pafisolutions.tarjepafi.shared.application.TapResult;
import com.pafisolutions.tarjepafi.spacefacility.application.SpaceCatalog;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the taps that belong to attendance (TS13): students and teachers in a classroom and teachers in
 * a meeting. Port of {@code attendance-tap.service.ts}. Everything is decided from the tap's own time, and a
 * tap that already has a record is ignored (AD13).
 */
@Service
public class AttendanceTapService implements AttendanceFacade {

  private static final Logger log = LoggerFactory.getLogger(AttendanceTapService.class);
  private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

  private final SpaceCatalog spaces;
  private final SessionRepository sessions;
  private final SessionParticipantRepository participants;
  private final AttendanceRecordRepository records;
  private final ClassScheduleRepository schedules;
  private final CourseRepository courses;
  private final EnrollmentRepository enrollments;
  private final SessionClosingService closing;
  private final ApplicationEventPublisher events;

  public AttendanceTapService(
      SpaceCatalog spaces,
      SessionRepository sessions,
      SessionParticipantRepository participants,
      AttendanceRecordRepository records,
      ClassScheduleRepository schedules,
      CourseRepository courses,
      EnrollmentRepository enrollments,
      SessionClosingService closing,
      ApplicationEventPublisher events) {
    this.spaces = spaces;
    this.sessions = sessions;
    this.participants = participants;
    this.records = records;
    this.schedules = schedules;
    this.courses = courses;
    this.enrollments = enrollments;
    this.closing = closing;
    this.events = events;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasMeetingInProgress(String spaceId, LocalDateTime at) {
    return meetingInProgress(spaceId, at).isPresent();
  }

  /** Answers the tap when it is attendance's to resolve, and does nothing otherwise. */
  @Transactional
  public void onCardTap(TapContext tap) {
    Optional<String> type = spaces.find(tap.spaceId()).map(space -> space.getType());
    if (type.isEmpty() || !belongsToAttendance(tap, type.get())) {
      return;
    }
    if (records.existsByTapEventId(tap.tapEventId())) {
      log.debug("Tap {} already has its attendance record", tap.tapEventId());
      return;
    }
    closing.closeDue(tap.at());
    TapResult result =
        "MEETING_ROOM".equals(type.get())
            ? meetingTap(tap)
            : "TEACHER".equals(tap.holderRole()) ? teacherInClassroom(tap) : studentInClassroom(tap);
    events.publishEvent(result.toEvent("attendance", tap));
    if ("REJECTED".equals(result.outcome())) {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("tapEventId", tap.tapEventId());
      data.put("readerId", tap.readerId());
      data.put("cardHolderId", tap.cardHolderId());
      data.put("spaceId", tap.spaceId());
      data.put("reason", result.messageKey());
      events.publishEvent(new IntegrationEvent("attendance.rejected", data));
    }
  }

  private boolean belongsToAttendance(TapContext tap, String spaceType) {
    return switch (spaceType) {
      case "CLASSROOM" -> !"STAFF".equals(tap.holderRole());
      case "MEETING_ROOM" ->
          "TEACHER".equals(tap.holderRole()) && hasMeetingInProgress(tap.spaceId(), tap.at());
      default -> false;
    };
  }

  // ---------- Classroom · student ----------

  private TapResult studentInClassroom(TapContext tap) {
    Optional<Session> found =
        sessionsIn(tap, SessionType.CLASS).stream().filter(s -> s.isInProgress(tap.at())).findFirst();
    if (found.isEmpty() || found.get().getScheduleId() == null) {
      return TapResult.rejected("tap.noClassInProgress");
    }
    Session session = found.get();
    if (enrollments
        .search(tap.cardHolderId(), session.getScheduleId(), null, "ACTIVE")
        .isEmpty()) {
      return TapResult.rejected("tap.notEnrolled");
    }
    List<AttendanceRecord> marks = records.findBySessionId(session.getId());
    if (marks.stream().anyMatch(r -> r.getCardHolderId().equals(tap.cardHolderId()))) {
      return TapResult.attendance("tap.alreadyRegistered", Map.of(), "info");
    }
    // When the teacher already arrived the status is known now; otherwise it waits as PENDING.
    boolean teacherArrived =
        marks.stream()
            .anyMatch(r -> r.getRole() == AttendanceRole.TEACHER && r.getCheckInAt() != null);
    AttendanceStatus status =
        teacherArrived
            ? AttendanceRecord.resolveStatus(tap.at(), session.punctualUntil())
            : AttendanceStatus.PENDING;
    record(tap, session, AttendanceRole.STUDENT, status);
    return TapResult.attendance(
        "tap.attendanceTaken",
        Map.of("course", courseName(session), "time", timeRange(session)),
        "success");
  }

  // ---------- Classroom · teacher ----------

  private TapResult teacherInClassroom(TapContext tap) {
    List<Session> own =
        sessionsIn(tap, SessionType.CLASS).stream()
            .filter(s -> isTaughtBy(s, tap.cardHolderId()) && s.isWithinTeacherWindow(tap.at()))
            .toList();
    for (Session session : own) {
      List<AttendanceRecord> marks = records.findBySessionId(session.getId());
      Optional<AttendanceRecord> mine =
          marks.stream()
              .filter(
                  r ->
                      r.getRole() == AttendanceRole.TEACHER
                          && r.getCardHolderId().equals(tap.cardHolderId()))
              .findFirst();
      if (mine.isPresent() && mine.get().hasCheckedOut()) {
        return TapResult.attendance("tap.exitAlreadyRegistered", Map.of(), "info"); // 3rd tap
      }
      if (mine.isPresent() && !session.isFinished()) {
        return teacherExit(tap, session, mine.get()); // 2nd tap
      }
      if (mine.isEmpty() && session.isInProgress(tap.at())) {
        return teacherArrival(tap, session, marks); // 1st tap
      }
    }
    return TapResult.rejected("tap.noAssignedClass");
  }

  /** 1st tap: arrival, the session starts and the PENDING marks are confirmed with their own time. */
  private TapResult teacherArrival(TapContext tap, Session session, List<AttendanceRecord> marks) {
    AttendanceStatus status = AttendanceRecord.resolveStatus(tap.at(), session.punctualUntil());
    record(tap, session, AttendanceRole.TEACHER, status);
    session.start();
    sessions.save(session);

    List<AttendanceRecord> pending =
        marks.stream()
            .filter(r -> r.getRole() == AttendanceRole.STUDENT && r.isPending())
            .toList();
    for (AttendanceRecord mark : pending) {
      mark.confirm(tap.at(), session.punctualUntil());
      records.save(mark);
      Map<String, Object> data = AttendanceEvents.recorded(mark, tap.readerId());
      events.publishEvent(new IntegrationEvent("attendance.confirmed", data));
      if (mark.getStatus() == AttendanceStatus.LATE) {
        events.publishEvent(new IntegrationEvent("attendance.late-detected", data));
      }
    }
    String course = courseLabel(session);
    if (pending.isEmpty()) {
      return TapResult.attendance("tap.teacherArrivalNoRecords", Map.of("course", course), "success");
    }
    return TapResult.attendance(
        "tap.teacherArrival", Map.of("course", course, "count", pending.size()), "success");
  }

  /** 2nd tap: departure, the duration is stored and the session closes. */
  private TapResult teacherExit(TapContext tap, Session session, AttendanceRecord mine) {
    mine.checkOut(tap.at());
    records.save(mine);
    closing.close(session, tap.at());
    int minutes = mine.getDurationMinutes() == null ? 0 : mine.getDurationMinutes();
    return TapResult.attendance(
        "tap.teacherExit", Map.of("duration", formatDuration(minutes)), "success");
  }

  // ---------- Meeting room · teacher ----------

  private TapResult meetingTap(TapContext tap) {
    Session meeting = meetingInProgress(tap.spaceId(), tap.at()).orElseThrow();
    if (!participants.existsBySessionIdAndCardHolderId(meeting.getId(), tap.cardHolderId())) {
      return TapResult.rejected("tap.notInvited");
    }
    if (records.findBySessionId(meeting.getId()).stream()
        .anyMatch(r -> r.getCardHolderId().equals(tap.cardHolderId()))) {
      return TapResult.attendance("tap.alreadyRegistered", Map.of(), "info");
    }
    AttendanceStatus status = AttendanceRecord.resolveStatus(tap.at(), meeting.punctualUntil());
    record(tap, meeting, AttendanceRole.TEACHER, status);
    return TapResult.attendance(
        status == AttendanceStatus.PRESENT ? "tap.meetingRegistered" : "tap.meetingRegisteredLate",
        Map.of("title", meeting.getTitle()),
        "success");
  }

  // ---------- Internals ----------

  private Optional<Session> meetingInProgress(String spaceId, LocalDateTime at) {
    return sessions
        .findBySpaceIdAndDateAndTypeOrderByScheduledStart(spaceId, at.toLocalDate(), SessionType.MEETING)
        .stream()
        .filter(s -> s.isInProgress(at))
        .findFirst();
  }

  private List<Session> sessionsIn(TapContext tap, SessionType type) {
    return sessions.findBySpaceIdAndDateAndTypeOrderByScheduledStart(
        tap.spaceId(), tap.at().toLocalDate(), type);
  }

  private boolean isTaughtBy(Session session, String teacherId) {
    return session.getScheduleId() != null
        && schedules
            .findById(session.getScheduleId())
            .map(ClassSchedule::getTeacherId)
            .filter(teacherId::equals)
            .isPresent();
  }

  private AttendanceRecord record(
      TapContext tap, Session session, AttendanceRole role, AttendanceStatus status) {
    AttendanceRecord record =
        new AttendanceRecord(
            "att-" + UUID.randomUUID(),
            tap.tapEventId(),
            session.getId(),
            tap.cardHolderId(),
            role,
            tap.at(),
            status,
            status == AttendanceStatus.PENDING ? null : tap.at(),
            AttendanceSource.NFC);
    records.save(record);
    Map<String, Object> data = AttendanceEvents.recorded(record, tap.readerId());
    events.publishEvent(new IntegrationEvent("attendance.recorded", data));
    if (status == AttendanceStatus.LATE) {
      events.publishEvent(new IntegrationEvent("attendance.late-detected", data));
    }
    return record;
  }

  private Optional<Course> courseOf(Session session) {
    if (session.getScheduleId() == null) {
      return Optional.empty();
    }
    return schedules.findById(session.getScheduleId()).flatMap(s -> courses.findById(s.getCourseId()));
  }

  private String courseName(Session session) {
    return courseOf(session).map(Course::getName).orElse(session.getTitle());
  }

  private String courseLabel(Session session) {
    return courseOf(session)
        .map(course -> course.getName() + " – " + course.getSubject())
        .orElse(session.getTitle() + " – ");
  }

  private static String timeRange(Session session) {
    return HOUR.format(session.getScheduledStart()) + "–" + HOUR.format(session.getScheduledEnd());
  }

  /** "45 min" or "1 h 28 min": the format of the old web app. */
  static String formatDuration(int totalMinutes) {
    int minutes = Math.max(0, totalMinutes);
    int hours = minutes / 60;
    int rest = minutes % 60;
    return hours == 0 ? rest + " min" : String.format("%d h %02d min", hours, rest);
  }
}
