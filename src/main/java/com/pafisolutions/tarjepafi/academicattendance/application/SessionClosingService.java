package com.pafisolutions.tarjepafi.academicattendance.application;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceRecord;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceRole;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceSource;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceStatus;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Enrollment;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Session;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.SessionParticipant;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.AttendanceRecordRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.EnrollmentRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.SessionParticipantRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.SessionRepository;
import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closing a session (rule 6.D, port of {@code session-closing.service.ts}): 15 minutes after its end a
 * session nobody closed becomes FINISHED and every enrolled student (or invited teacher) without a mark
 * gets an ABSENT record from the system. PENDING marks stay for the teacher to confirm.
 */
@Service
public class SessionClosingService {

  private static final Logger log = LoggerFactory.getLogger(SessionClosingService.class);

  private final SessionRepository sessions;
  private final SessionParticipantRepository participants;
  private final EnrollmentRepository enrollments;
  private final AttendanceRecordRepository records;
  private final ApplicationEventPublisher events;

  public SessionClosingService(
      SessionRepository sessions,
      SessionParticipantRepository participants,
      EnrollmentRepository enrollments,
      AttendanceRecordRepository records,
      ApplicationEventPublisher events) {
    this.sessions = sessions;
    this.participants = participants;
    this.enrollments = enrollments;
    this.records = records;
    this.events = events;
  }

  /** Closes every session that is due at {@code now}; returns how many were closed. */
  @Transactional
  public int closeDue(LocalDateTime now) {
    List<Session> due = sessions.findOpen().stream().filter(s -> s.shouldAutoClose(now)).toList();
    for (Session session : due) {
      close(session, session.closesAt());
    }
    if (!due.isEmpty()) {
      log.info("Closed {} session(s) that were due at {}", due.size(), now);
    }
    return due.size();
  }

  /** Closes one session: ABSENT for whoever did not mark, then FINISHED. */
  @Transactional
  public void close(Session session, LocalDateTime closedAt) {
    Set<String> marked =
        records.findBySessionId(session.getId()).stream()
            .map(AttendanceRecord::getCardHolderId)
            .collect(Collectors.toSet());
    int absent = 0;
    for (String holderId : expectedAttendees(session)) {
      if (marked.contains(holderId)) {
        continue;
      }
      AttendanceRecord record =
          new AttendanceRecord(
              "att-" + UUID.randomUUID(),
              null,
              session.getId(),
              holderId,
              session.isMeeting() ? AttendanceRole.TEACHER : AttendanceRole.STUDENT,
              null,
              AttendanceStatus.ABSENT,
              closedAt,
              AttendanceSource.SYSTEM);
      records.save(record);
      events.publishEvent(
          new IntegrationEvent("attendance.recorded", AttendanceEvents.recorded(record, null)));
      absent++;
    }
    session.finish();
    sessions.save(session);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("sessionId", session.getId());
    data.put("type", session.getType().name());
    data.put("spaceId", session.getSpaceId());
    data.put("closedAt", closedAt.toString());
    data.put("absentCount", absent);
    events.publishEvent(new IntegrationEvent("attendance.session-closed", data));
  }

  private List<String> expectedAttendees(Session session) {
    if (session.isMeeting()) {
      return participants.findBySessionId(session.getId()).stream()
          .map(SessionParticipant::getCardHolderId)
          .toList();
    }
    if (session.getScheduleId() == null) {
      return List.of();
    }
    return enrollments.search(null, session.getScheduleId(), null, "ACTIVE").stream()
        .map(Enrollment::getStudentId)
        .toList();
  }
}
