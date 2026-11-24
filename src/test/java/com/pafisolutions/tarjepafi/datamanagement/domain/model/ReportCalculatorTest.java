package com.pafisolutions.tarjepafi.datamanagement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rule 6.F as the web app's report-calculator.ts computes it, and the rules of a data record. */
class ReportCalculatorTest {

  private static int sequence;

  private static DataRecord fact(
      SourceContext source, DataType type, String subject, String at, String recordedAt, Map<String, Object> payload) {
    return new DataRecord(
        "dr-" + (++sequence),
        "evt-" + sequence,
        source,
        type,
        subject,
        LocalDateTime.parse(at),
        LocalDateTime.parse(recordedAt),
        payload);
  }

  private static DataRecord attendance(String subject, String status, String recordedAt) {
    return fact(
        SourceContext.ACADEMIC_ATTENDANCE,
        DataType.ATTENDANCE,
        subject,
        recordedAt,
        recordedAt,
        Map.of("role", "STUDENT", "status", status));
  }

  private static DataRecord access(String decision, String at) {
    return fact(
        SourceContext.SPACE_AND_FACILITY, DataType.ACCESS, "acc-" + (++sequence), at, at, Map.of("decision", decision));
  }

  private static DataRecord reading(String reader, String at) {
    return fact(SourceContext.IOT_MONITORING, DataType.CARD_READING, reader, at, at, Map.of("cardUid", "04:3D:9B:12:8F"));
  }

  @Test
  void summarize_noRecords_isZeroWithoutPeakHour() {
    ReportCalculator.Result result = ReportCalculator.summarize(List.of());

    assertThat(result.totalRecords()).isZero();
    assertThat(result.summary()).isEqualTo(new ReportSummary(0, 0, 0, null, 0));
  }

  @Test
  void summarize_attendance_countsTheLastFactOfEachMark() {
    List<DataRecord> records =
        List.of(
            attendance("att-1", "PENDING", "2026-10-05T09:55:00"),
            attendance("att-1", "PRESENT", "2026-10-05T10:01:00"), // confirmed by the teacher
            attendance("att-2", "LATE", "2026-10-05T10:20:00"),
            attendance("att-3", "ABSENT", "2026-10-05T11:45:00"),
            attendance("att-4", "PENDING", "2026-10-05T11:50:00"), // not resolved: left out
            attendance("att-5", "JUSTIFIED", "2026-10-05T11:50:00")); // not resolved: left out

    ReportSummary summary = ReportCalculator.summarize(records).summary();

    assertThat(summary.attendanceRate()).isEqualTo(66.7); // 2 attended of 3 resolved
  }

  @Test
  void summarize_attendanceOfTeachers_isNotCounted() {
    DataRecord teacher =
        fact(
            SourceContext.ACADEMIC_ATTENDANCE,
            DataType.ATTENDANCE,
            "att-t",
            "2026-10-05T10:00:00",
            "2026-10-05T10:00:00",
            Map.of("role", "TEACHER", "status", "PRESENT"));

    assertThat(ReportCalculator.summarize(List.of(teacher)).summary().attendanceRate()).isZero();
  }

  @Test
  void summarize_access_everythingNotGrantedIsDenied() {
    List<DataRecord> records =
        List.of(
            access("GRANTED", "2026-10-05T07:00:00"),
            access("GRANTED", "2026-10-05T07:01:00"),
            access("DENIED", "2026-10-05T07:02:00"));

    ReportSummary summary = ReportCalculator.summarize(records).summary();

    assertThat(summary.accessGranted()).isEqualTo(2);
    assertThat(summary.accessDenied()).isEqualTo(1);
  }

  @Test
  void summarize_peakHour_isTheBusiestHourAndTheEarliestOnATie() {
    List<DataRecord> records =
        List.of(
            reading("RDR-A-301", "2026-10-05T09:10:00"),
            reading("RDR-A-301", "2026-10-05T09:50:00"),
            reading("RDR-A-302", "2026-10-05T08:05:00"),
            reading("RDR-A-302", "2026-10-05T08:06:00"),
            reading("RDR-A-302", "2026-10-05T10:06:00"));

    assertThat(ReportCalculator.summarize(records).summary().peakHour()).isEqualTo("08:00");
  }

  @Test
  void summarize_readersWithIncidents_countsEachReaderOnce() {
    List<DataRecord> records = new ArrayList<>();
    for (String reader : List.of("RDR-A-301", "RDR-A-301", "RDR-T-002")) {
      records.add(
          fact(
              SourceContext.IOT_MONITORING,
              DataType.DEVICE_TELEMETRY,
              reader,
              "2026-10-05T07:05:00",
              "2026-10-05T07:05:00",
              Map.of("incidentType", "CONNECTIVITY_FAILURE")));
    }
    records.add(
        fact(
            SourceContext.IOT_MONITORING,
            DataType.DEVICE_TELEMETRY,
            "RDR-A-302",
            "2026-10-05T07:05:00",
            "2026-10-05T07:05:00",
            Map.of("signalStrength", -50))); // telemetry without an incident

    assertThat(ReportCalculator.summarize(records).summary().readersWithIncidents()).isEqualTo(2);
  }

  @Test
  void dailyBreakdown_groupsByDayOldestFirst_andHasNoRateWithoutResolvedAttendance() {
    List<DataRecord> records =
        List.of(
            reading("RDR-A-301", "2026-10-06T09:00:00"),
            attendance("att-1", "PRESENT", "2026-10-05T10:00:00"),
            access("GRANTED", "2026-10-05T07:00:00"),
            access("DENIED", "2026-10-05T07:10:00"));

    List<ReportCalculator.Day> days = ReportCalculator.dailyBreakdown(records);

    assertThat(days).extracting(ReportCalculator.Day::date)
        .containsExactly(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06"));
    assertThat(days.get(0).attendanceRate()).isEqualTo(100.0);
    assertThat(days.get(0).accessGranted()).isEqualTo(1);
    assertThat(days.get(0).accessDenied()).isEqualTo(1);
    assertThat(days.get(1).readings()).isEqualTo(1);
    assertThat(days.get(1).attendanceRate()).isNull();
  }

  @Test
  void dataRecord_sourceThatDoesNotProduceTheType_isRejected() {
    assertThatThrownBy(
            () ->
                new DataRecord(
                    "dr-x",
                    "evt-x",
                    SourceContext.CREDENTIALS,
                    DataType.ACCESS,
                    "acc-1",
                    LocalDateTime.parse("2026-10-05T07:00:00"),
                    LocalDateTime.parse("2026-10-05T07:00:00"),
                    Map.of()))
        .isInstanceOf(DataRegistrationException.class);
  }

  @Test
  void dataRecord_withoutSubject_isRejected() {
    assertThatThrownBy(
            () ->
                new DataRecord(
                    "dr-x",
                    "evt-x",
                    SourceContext.SPACE_AND_FACILITY,
                    DataType.ACCESS,
                    " ",
                    LocalDateTime.parse("2026-10-05T07:00:00"),
                    LocalDateTime.parse("2026-10-05T07:00:00"),
                    Map.of()))
        .isInstanceOf(DataRegistrationException.class);
  }
}
