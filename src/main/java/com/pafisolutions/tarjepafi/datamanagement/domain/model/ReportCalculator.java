package com.pafisolutions.tarjepafi.datamanagement.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Port of the web app's {@code report-calculator.ts}: the summary and the daily breakdown of a set of data
 * records. Pass the records in recording order (oldest first).
 */
public final class ReportCalculator {

  private static final Set<String> ATTENDED = Set.of("PRESENT", "LATE");
  private static final Set<String> RESOLVED = Set.of("PRESENT", "LATE", "ABSENT");

  private ReportCalculator() {}

  /** One day of the report. {@code attendanceRate} is null when no attendance was resolved that day. */
  public record Day(
      LocalDate date, int readings, Double attendanceRate, int accessGranted, int accessDenied) {}

  public record Result(int totalRecords, ReportSummary summary) {}

  /** Attendance, access and incident figures, and the busiest hour of the card readings. */
  public static Result summarize(Collection<DataRecord> records) {
    Map<String, Integer> byHour = new TreeMap<>();
    for (DataRecord record : records) {
      if (record.getDataType() == DataType.CARD_READING) {
        byHour.merge(String.format("%02d", record.getOccurredAt().getHour()), 1, Integer::sum);
      }
    }
    String peak = null;
    int peakCount = 0;
    for (Map.Entry<String, Integer> hour : byHour.entrySet()) {
      if (hour.getValue() > peakCount) { // TreeMap is ascending, so ties keep the earliest hour
        peak = hour.getKey();
        peakCount = hour.getValue();
      }
    }
    Set<String> incidentReaders = new HashSet<>();
    for (DataRecord record : records) {
      if (record.getDataType() == DataType.DEVICE_TELEMETRY && record.text("incidentType") != null) {
        incidentReaders.add(record.getSubjectId());
      }
    }
    int[] access = accessCounts(records);
    Double rate = attendanceRate(records);
    return new Result(
        records.size(),
        new ReportSummary(
            rate == null ? 0 : rate,
            access[0],
            access[1],
            peak == null ? null : peak + ":00",
            incidentReaders.size()));
  }

  /** The days with at least one record, oldest first. */
  public static List<Day> dailyBreakdown(Collection<DataRecord> records) {
    Map<LocalDate, List<DataRecord>> byDay = new TreeMap<>();
    for (DataRecord record : records) {
      byDay.computeIfAbsent(record.getOccurredAt().toLocalDate(), day -> new ArrayList<>()).add(record);
    }
    List<Day> days = new ArrayList<>();
    byDay.forEach(
        (date, list) -> {
          int[] access = accessCounts(list);
          int readings = (int) list.stream().filter(r -> r.getDataType() == DataType.CARD_READING).count();
          days.add(new Day(date, readings, attendanceRate(list), access[0], access[1]));
        });
    return days;
  }

  /** The last fact of each student mark, in recording order. */
  private static Collection<DataRecord> latestStudentAttendance(Collection<DataRecord> records) {
    Map<String, DataRecord> latest = new LinkedHashMap<>();
    for (DataRecord record : records) {
      if (record.getDataType() != DataType.ATTENDANCE || !"STUDENT".equals(record.text("role"))) {
        continue;
      }
      DataRecord previous = latest.get(record.getSubjectId());
      if (previous == null || !record.getRecordedAt().isBefore(previous.getRecordedAt())) {
        latest.put(record.getSubjectId(), record);
      }
    }
    return latest.values();
  }

  /** Students per status (PENDING, PRESENT, LATE, ABSENT, JUSTIFIED...) by the last fact of each mark. */
  public static Map<String, Integer> studentAttendanceByStatus(Collection<DataRecord> records) {
    Map<String, Integer> counts = new TreeMap<>();
    for (DataRecord record : latestStudentAttendance(records)) {
      counts.merge(String.valueOf(record.text("status")), 1, Integer::sum);
    }
    return counts;
  }

  /** Percentage of students present or late over the resolved marks, or null without any. */
  public static Double studentAttendanceRate(Collection<DataRecord> records) {
    return attendanceRate(records);
  }

  /**
   * Attendances are immutable facts and one mark can have several (PENDING, PRESENT, edited): only the last
   * fact of each mark counts.
   */
  private static Double attendanceRate(Collection<DataRecord> records) {
    int resolved = 0;
    int attended = 0;
    for (DataRecord record : latestStudentAttendance(records)) {
      String status = String.valueOf(record.text("status"));
      if (RESOLVED.contains(status)) {
        resolved++;
        if (ATTENDED.contains(status)) {
          attended++;
        }
      }
    }
    if (resolved == 0) {
      return null;
    }
    return Math.round((attended / (double) resolved) * 1000) / 10.0;
  }

  /** {granted, denied}: every access that is not GRANTED counts as denied, like the web app does. */
  private static int[] accessCounts(Collection<DataRecord> records) {
    int granted = 0;
    int total = 0;
    for (DataRecord record : records) {
      if (record.getDataType() == DataType.ACCESS) {
        total++;
        if ("GRANTED".equals(record.text("decision"))) {
          granted++;
        }
      }
    }
    return new int[] {granted, total - granted};
  }
}
