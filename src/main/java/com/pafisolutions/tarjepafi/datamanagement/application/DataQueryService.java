package com.pafisolutions.tarjepafi.datamanagement.application;

import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataRecord;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataType;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.ReportCalculator;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.SourceContext;
import com.pafisolutions.tarjepafi.datamanagement.infrastructure.persistence.DataRecordRepository;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TS25: the data records and the dashboard of a day, both computed from the recorded facts only. */
@Service
@Transactional(readOnly = true)
public class DataQueryService {

  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 200;
  private static final int RECENT_SIZE = 8;
  private static final int FIRST_HOUR = 7;
  private static final LocalDateTime MIN = LocalDateTime.of(1970, 1, 1, 0, 0);
  private static final LocalDateTime MAX = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

  private final DataRecordRepository records;
  private final CampusClock clock;

  public DataQueryService(DataRecordRepository records, CampusClock clock) {
    this.records = records;
    this.clock = clock;
  }

  public record RecordPage(List<DataRecord> items, String nextCursor) {}

  public record HourCount(String hour, int count) {}

  /** The figures of one day. {@code attendanceRate} is null when nothing was resolved. */
  public record Dashboard(
      LocalDate date,
      LocalDateTime now,
      Map<String, Integer> attendanceByStatus,
      Double attendanceRate,
      int accessGranted,
      int accessDenied,
      int readingsToday,
      List<HourCount> readingsByHour,
      List<DataRecord> recentReadings,
      int readersWithIncidents) {}

  /** Newest first. Send the previous {@code nextCursor} as {@code after} to get only what is new. */
  public RecordPage records(
      String type,
      String source,
      String subjectId,
      LocalDateTime from,
      LocalDateTime to,
      String after,
      Integer limit) {
    DataType dataType = parse(DataType.class, type, "INVALID_DATA_TYPE");
    SourceContext sourceContext = parse(SourceContext.class, source, "INVALID_SOURCE_CONTEXT");
    if (from != null && to != null && from.isAfter(to)) {
      throw new BusinessRuleException(Kind.INVALID, "INVALID_RANGE", "from must not be after to");
    }
    long cursor = parseCursor(after);
    int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
    List<DataRecord> items =
        records.findAfter(
            cursor,
            dataType,
            sourceContext,
            subjectId == null || subjectId.isBlank() ? null : subjectId,
            from == null ? MIN : from,
            to == null ? MAX : to,
            PageRequest.of(0, size));
    long next = items.isEmpty() ? cursor : items.get(0).getSeq();
    return new RecordPage(items, String.valueOf(next));
  }

  /** The dashboard of {@code date} (today by the campus clock when null). */
  public Dashboard dashboard(LocalDate date) {
    LocalDateTime now = clock.now();
    LocalDate day = date == null ? now.toLocalDate() : date;
    List<DataRecord> facts =
        records.findByOccurredAtBetweenOrderBySeqAsc(day.atStartOfDay(), day.atTime(23, 59, 59));

    List<DataRecord> readings =
        facts.stream().filter(r -> r.getDataType() == DataType.CARD_READING).toList();
    Map<Integer, Integer> byHour = new TreeMap<>();
    for (DataRecord reading : readings) {
      byHour.merge(reading.getOccurredAt().getHour(), 1, Integer::sum);
    }
    int firstHour = Math.min(FIRST_HOUR, byHour.keySet().stream().min(Integer::compare).orElse(FIRST_HOUR));
    int lastHour = Math.max(day.equals(now.toLocalDate()) ? now.getHour() : 23, firstHour);
    List<HourCount> hours = new ArrayList<>();
    for (int hour = firstHour; hour <= lastHour; hour++) {
      hours.add(new HourCount(String.format("%02d", hour), byHour.getOrDefault(hour, 0)));
    }
    List<DataRecord> recent =
        readings.stream()
            .sorted(Comparator.comparing(DataRecord::getSeq).reversed())
            .limit(RECENT_SIZE)
            .toList();

    ReportCalculator.Result summary = ReportCalculator.summarize(facts);
    return new Dashboard(
        day,
        now,
        ReportCalculator.studentAttendanceByStatus(facts),
        ReportCalculator.studentAttendanceRate(facts),
        summary.summary().accessGranted(),
        summary.summary().accessDenied(),
        readings.size(),
        hours,
        recent,
        summary.summary().readersWithIncidents());
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String value, String code) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(type, value.trim().toUpperCase());
    } catch (IllegalArgumentException exception) {
      throw new BusinessRuleException(Kind.INVALID, code, value + " is not a valid value");
    }
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
