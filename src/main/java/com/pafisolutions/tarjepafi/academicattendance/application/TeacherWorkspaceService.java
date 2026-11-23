package com.pafisolutions.tarjepafi.academicattendance.application;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceRecord;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceRole;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceSource;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceStatus;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.ClassSchedule;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Course;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Enrollment;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Session;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.SessionParticipant;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.SessionStatus;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.AttendanceRecordRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.ClassScheduleRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.CourseRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.EnrollmentRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.SessionParticipantRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.SessionRepository;
import com.pafisolutions.tarjepafi.credentials.application.CardHolderDirectoryPort;
import com.pafisolutions.tarjepafi.credentials.application.CardHolderDirectoryPort.HolderInfo;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a teacher sees and does (TS14 to TS17, US16 to US23): own sessions with their roster, meetings,
 * the history of their own marks, confirming PENDING marks and editing a mark with a reason. Port of
 * {@code teacher-workspace.service.ts}. Expired sessions are closed before every read.
 */
@Service
@Transactional
public class TeacherWorkspaceService {

  public static final int DEFAULT_LIMIT = 100;
  public static final int MAX_LIMIT = 500;

  /** Counts per status of the students; {@code unmarked} are enrolled students with no record yet. */
  public record Counts(Map<AttendanceStatus, Integer> byStatus, int unmarked, int total) {}

  public record SessionSummary(
      Session session,
      Course course,
      ClassSchedule schedule,
      AttendanceRecord teacherRecord,
      Counts counts) {}

  public record RosterRow(String cardHolderId, String fullName, String code, AttendanceRecord record) {}

  public record SessionDetail(SessionSummary summary, List<RosterRow> roster) {}

  public record MeetingRow(Session session, AttendanceRecord record) {}

  public record ClassAttendance(AttendanceRecord record, Session session, Course course) {}

  /** Newest first, and the cursor to send next time. */
  public record RecordPage(List<AttendanceRecord> items, String nextCursor) {}

  private final SessionRepository sessions;
  private final SessionParticipantRepository participants;
  private final AttendanceRecordRepository records;
  private final ClassScheduleRepository schedules;
  private final CourseRepository courses;
  private final EnrollmentRepository enrollments;
  private final SessionClosingService closing;
  private final CardHolderDirectoryPort directory;
  private final CampusClock clock;
  private final ApplicationEventPublisher events;

  public TeacherWorkspaceService(
      SessionRepository sessions,
      SessionParticipantRepository participants,
      AttendanceRecordRepository records,
      ClassScheduleRepository schedules,
      CourseRepository courses,
      EnrollmentRepository enrollments,
      SessionClosingService closing,
      CardHolderDirectoryPort directory,
      CampusClock clock,
      ApplicationEventPublisher events) {
    this.sessions = sessions;
    this.participants = participants;
    this.records = records;
    this.schedules = schedules;
    this.courses = courses;
    this.enrollments = enrollments;
    this.closing = closing;
    this.directory = directory;
    this.clock = clock;
    this.events = events;
  }

  /** The teacher's classes on a date (today by the campus clock when null). */
  public List<SessionSummary> sessionsOn(String teacherId, LocalDate date) {
    closing.closeDue(clock.now());
    List<String> scheduleIds = taughtBy(teacherId).stream().map(ClassSchedule::getId).toList();
    if (scheduleIds.isEmpty()) {
      return List.of();
    }
    LocalDate day = date == null ? clock.now().toLocalDate() : date;
    return sessions.findByScheduleIdInAndDateOrderByScheduledStart(scheduleIds, day).stream()
        .map(session -> summarize(session, teacherId))
        .toList();
  }

  public SessionDetail detail(String teacherId, String sessionId) {
    closing.closeDue(clock.now());
    Session session = ownSession(teacherId, sessionId);
    List<AttendanceRecord> marks = records.findBySessionId(sessionId);
    Map<String, AttendanceRecord> byStudent =
        marks.stream()
            .filter(r -> r.getRole() == AttendanceRole.STUDENT)
            .collect(Collectors.toMap(AttendanceRecord::getCardHolderId, Function.identity()));
    Set<String> ids = new LinkedHashSet<>();
    activeEnrollments(session.getScheduleId()).forEach(e -> ids.add(e.getStudentId()));
    ids.addAll(byStudent.keySet());
    List<RosterRow> roster = new ArrayList<>();
    for (String id : ids) {
      Optional<HolderInfo> holder = directory.findHolder(id);
      roster.add(
          new RosterRow(
              id,
              holder.map(HolderInfo::fullName).orElse(id),
              holder.map(HolderInfo::code).orElse("—"),
              byStudent.get(id)));
    }
    roster.sort(Comparator.comparing(RosterRow::fullName));
    return new SessionDetail(summarize(session, teacherId), roster);
  }

  /** The records of one session, newest first (D13): poll with the previous {@code nextCursor}. */
  public RecordPage recordsOf(String teacherId, String sessionId, String after, Integer limit) {
    ownSession(teacherId, sessionId);
    long cursor = parseCursor(after);
    int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
    List<AttendanceRecord> items =
        records.findOfSession(sessionId, cursor == 0 ? null : cursor).stream().limit(size).toList();
    long next = items.isEmpty() ? cursor : items.get(0).getSeq();
    return new RecordPage(items, String.valueOf(next));
  }

  /** The teacher's own marks in their classes, newest session first (US17, US20, US22, US23). */
  public List<ClassAttendance> myClassAttendance(String teacherId) {
    closing.closeDue(clock.now());
    List<AttendanceRecord> mine =
        records.findByCardHolderId(teacherId).stream()
            .filter(r -> r.getRole() == AttendanceRole.TEACHER)
            .toList();
    Map<String, Session> bySession = sessionsById(mine.stream().map(AttendanceRecord::getSessionId));
    return mine.stream()
        .filter(r -> bySession.containsKey(r.getSessionId()) && !bySession.get(r.getSessionId()).isMeeting())
        .map(r -> new ClassAttendance(r, bySession.get(r.getSessionId()), courseOf(bySession.get(r.getSessionId()))))
        .sorted(
            Comparator.comparing((ClassAttendance a) -> a.session().getScheduledStart()).reversed())
        .toList();
  }

  /** Meetings and trainings the teacher is invited to (US21), newest first. */
  public List<MeetingRow> myMeetings(String teacherId) {
    closing.closeDue(clock.now());
    List<String> ids =
        participants.findByCardHolderId(teacherId).stream()
            .map(SessionParticipant::getSessionId)
            .toList();
    Map<String, AttendanceRecord> marks =
        records.findByCardHolderId(teacherId).stream()
            .collect(Collectors.toMap(AttendanceRecord::getSessionId, Function.identity()));
    return sessionsById(ids.stream()).values().stream()
        .filter(Session::isMeeting)
        .sorted(Comparator.comparing(Session::getScheduledStart).reversed())
        .map(session -> new MeetingRow(session, marks.get(session.getId())))
        .toList();
  }

  /**
   * Rule 6.E (TS15, TS16): the teacher sets a student's status in their own session, once it has started,
   * and must give a reason of at most 255 characters. Creates the record when the student has none.
   */
  public AttendanceRecord edit(
      String teacherId,
      String teacherUserId,
      String sessionId,
      String studentId,
      AttendanceStatus status,
      String reason) {
    closing.closeDue(clock.now());
    Session session = editable(teacherId, sessionId);
    if (status == AttendanceStatus.PENDING) {
      throw new BusinessRuleException(Kind.INVALID, "STATUS_NOT_ALLOWED", "A teacher cannot set PENDING");
    }
    if (reason == null
        || reason.isBlank()
        || reason.trim().length() > AttendanceRecord.EDIT_REASON_MAX_LENGTH) {
      throw new BusinessRuleException(
          Kind.INVALID, "REASON_REQUIRED", "The reason is required and at most 255 characters");
    }
    LocalDateTime now = clock.now();
    Optional<AttendanceRecord> existing =
        records.findBySessionId(sessionId).stream()
            .filter(r -> r.getCardHolderId().equals(studentId) && r.getRole() == AttendanceRole.STUDENT)
            .findFirst();
    AttendanceRecord record;
    if (existing.isPresent()) {
      record = existing.get();
    } else {
      boolean enrolled =
          enrollments.search(studentId, session.getScheduleId(), null, "ACTIVE").size() > 0;
      if (!enrolled) {
        throw new BusinessRuleException(
            Kind.UNPROCESSABLE, "STUDENT_NOT_ENROLLED", "The student is not enrolled in this class");
      }
      record =
          new AttendanceRecord(
              "att-" + UUID.randomUUID(),
              null,
              sessionId,
              studentId,
              AttendanceRole.STUDENT,
              null,
              AttendanceStatus.ABSENT,
              null,
              AttendanceSource.TEACHER_EDIT);
    }
    record.edit(status, reason, teacherUserId, now);
    records.save(record);
    events.publishEvent(new IntegrationEvent("attendance.edited", AttendanceEvents.recorded(record, null)));
    return record;
  }

  /** The teacher confirms the PENDING marks left, for instance when they never tapped in. */
  public int confirmPending(String teacherId, String sessionId) {
    closing.closeDue(clock.now());
    Session session = editable(teacherId, sessionId);
    List<AttendanceRecord> pending =
        records.findBySessionId(sessionId).stream()
            .filter(r -> r.getRole() == AttendanceRole.STUDENT && r.isPending())
            .toList();
    if (pending.isEmpty()) {
      throw new BusinessRuleException(
          Kind.CONFLICT, "NOTHING_TO_CONFIRM", "There are no pending attendance marks");
    }
    for (AttendanceRecord record : pending) {
      record.confirm(clock.now(), session.punctualUntil());
      records.save(record);
      events.publishEvent(
          new IntegrationEvent("attendance.confirmed", AttendanceEvents.recorded(record, null)));
    }
    return pending.size();
  }

  // ---------- Internals ----------

  private List<ClassSchedule> taughtBy(String teacherId) {
    return schedules.search(teacherId, null, null);
  }

  /** The session when the teacher teaches its class; 404 for an unknown or someone else's session. */
  private Session ownSession(String teacherId, String sessionId) {
    Session session =
        sessions
            .findById(sessionId)
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        Kind.NOT_FOUND, "SESSION_NOT_FOUND", "Unknown session " + sessionId));
    boolean own =
        session.getScheduleId() != null
            && schedules
                .findById(session.getScheduleId())
                .filter(s -> s.getTeacherId().equals(teacherId))
                .isPresent();
    if (!own) {
      throw new BusinessRuleException(
          Kind.FORBIDDEN, "NOT_YOUR_SESSION", "The session is not one of your classes");
    }
    return session;
  }

  private Session editable(String teacherId, String sessionId) {
    Session session = ownSession(teacherId, sessionId);
    if (session.getStatus() == SessionStatus.SCHEDULED) {
      throw new BusinessRuleException(
          Kind.CONFLICT, "SESSION_NOT_STARTED", "The session has not started yet");
    }
    return session;
  }

  private List<Enrollment> activeEnrollments(String scheduleId) {
    return scheduleId == null ? List.of() : enrollments.search(null, scheduleId, null, "ACTIVE");
  }

  private SessionSummary summarize(Session session, String teacherId) {
    List<AttendanceRecord> marks = records.findBySessionId(session.getId());
    int enrolled = activeEnrollments(session.getScheduleId()).size();
    List<AttendanceRecord> students =
        marks.stream().filter(r -> r.getRole() == AttendanceRole.STUDENT).toList();
    Map<AttendanceStatus, Integer> byStatus = new EnumMap<>(AttendanceStatus.class);
    for (AttendanceStatus status : AttendanceStatus.values()) {
      byStatus.put(status, 0);
    }
    students.forEach(r -> byStatus.merge(r.getStatus(), 1, Integer::sum));
    int total = Math.max(enrolled, students.size());
    Counts counts = new Counts(byStatus, Math.max(0, total - students.size()), total);
    AttendanceRecord teacherRecord =
        marks.stream()
            .filter(r -> r.getRole() == AttendanceRole.TEACHER && r.getCardHolderId().equals(teacherId))
            .findFirst()
            .orElse(null);
    ClassSchedule schedule =
        session.getScheduleId() == null ? null : schedules.findById(session.getScheduleId()).orElse(null);
    return new SessionSummary(session, courseOf(session), schedule, teacherRecord, counts);
  }

  private Course courseOf(Session session) {
    if (session.getScheduleId() == null) {
      return null;
    }
    return schedules
        .findById(session.getScheduleId())
        .flatMap(s -> courses.findById(s.getCourseId()))
        .orElse(null);
  }

  private Map<String, Session> sessionsById(java.util.stream.Stream<String> ids) {
    List<String> unique = ids.distinct().toList();
    return sessions.findAllById(unique).stream()
        .collect(Collectors.toMap(Session::getId, Function.identity()));
  }

  private static long parseCursor(String after) {
    if (after == null || after.isBlank()) {
      return 0;
    }
    try {
      return Math.max(0, Long.parseLong(after.trim()));
    } catch (NumberFormatException exception) {
      throw new BusinessRuleException(Kind.INVALID, "INVALID_CURSOR", "The cursor must be a number");
    }
  }
}
