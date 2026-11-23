package com.pafisolutions.tarjepafi.academicattendance.infrastructure.seed;

import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceRecord;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceRole;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceSource;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.AttendanceStatus;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.Session;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.SessionParticipant;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.SessionStatus;
import com.pafisolutions.tarjepafi.academicattendance.domain.model.SessionType;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.AttendanceRecordRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.SessionParticipantRepository;
import com.pafisolutions.tarjepafi.academicattendance.infrastructure.persistence.SessionRepository;
import com.pafisolutions.tarjepafi.shared.infrastructure.seed.DemoSeed;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Imports the demo sessions, meeting invitations and attendance records. Existing rows are left alone.
 * The records go in by time so their cursor grows like it would have live.
 */
@Component
@Profile(DemoSeed.PROFILE)
@Order(35)
public class DemoSessionSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoSessionSeeder.class);

  private final DemoSeed seed;
  private final SessionRepository sessions;
  private final SessionParticipantRepository participants;
  private final AttendanceRecordRepository records;

  public DemoSessionSeeder(
      DemoSeed seed,
      SessionRepository sessions,
      SessionParticipantRepository participants,
      AttendanceRecordRepository records) {
    this.seed = seed;
    this.sessions = sessions;
    this.participants = participants;
    this.records = records;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<Session> newSessions = new ArrayList<>();
    for (JsonNode row : seed.collection("sessions")) {
      if (!sessions.existsById(row.path("id").asString())) {
        newSessions.add(
            new Session(
                row.path("id").asString(),
                SessionType.valueOf(row.path("type").asString()),
                text(row, "scheduleId"),
                row.path("title").asString(),
                row.path("spaceId").asString(),
                LocalDate.parse(row.path("date").asString()),
                LocalDateTime.parse(row.path("scheduledStart").asString()),
                LocalDateTime.parse(row.path("scheduledEnd").asString()),
                row.path("toleranceMinutes").asInt(),
                SessionStatus.valueOf(row.path("status").asString())));
      }
    }
    sessions.saveAll(newSessions);

    List<SessionParticipant> newParticipants = new ArrayList<>();
    for (JsonNode row : seed.collection("sessionParticipants")) {
      if (!participants.existsById(row.path("id").asString())) {
        newParticipants.add(
            new SessionParticipant(
                row.path("id").asString(),
                row.path("sessionId").asString(),
                row.path("cardHolderId").asString()));
      }
    }
    participants.saveAll(newParticipants);

    List<JsonNode> rows = new ArrayList<>();
    for (JsonNode row : seed.collection("attendanceRecords")) {
      if (!records.existsById(row.path("id").asString())) {
        rows.add(row);
      }
    }
    rows.sort(Comparator.comparing(this::moment));
    List<AttendanceRecord> newRecords = new ArrayList<>();
    for (JsonNode row : rows) {
      newRecords.add(record(row));
    }
    records.saveAll(newRecords);

    if (!newSessions.isEmpty() || !newRecords.isEmpty()) {
      log.info(
          "Demo seed: {} sessions, {} invitations, {} attendance records imported",
          newSessions.size(),
          newParticipants.size(),
          newRecords.size());
    }
  }

  private AttendanceRecord record(JsonNode row) {
    AttendanceRecord record =
        new AttendanceRecord(
            row.path("id").asString(),
            text(row, "tapEventId"),
            row.path("sessionId").asString(),
            row.path("cardHolderId").asString(),
            AttendanceRole.valueOf(row.path("role").asString()),
            time(row, "checkInAt"),
            AttendanceStatus.valueOf(row.path("status").asString()),
            time(row, "confirmedAt"),
            AttendanceSource.valueOf(row.path("source").asString()));
    if (time(row, "checkOutAt") != null) {
      record.checkOut(time(row, "checkOutAt"));
    }
    return record;
  }

  private String moment(JsonNode row) {
    String at = text(row, "checkInAt");
    if (at == null) {
      at = text(row, "confirmedAt");
    }
    return (at == null ? "" : at) + row.path("id").asString();
  }

  private static String text(JsonNode row, String field) {
    JsonNode value = row.path(field);
    return value.isString() ? value.asString() : null;
  }

  private static LocalDateTime time(JsonNode row, String field) {
    String value = text(row, field);
    return value == null ? null : LocalDateTime.parse(value);
  }
}
