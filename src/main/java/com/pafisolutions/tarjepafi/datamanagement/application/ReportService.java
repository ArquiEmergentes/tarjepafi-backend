package com.pafisolutions.tarjepafi.datamanagement.application;

import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataRecord;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.Report;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.ReportCalculator;
import com.pafisolutions.tarjepafi.datamanagement.infrastructure.persistence.DataRecordRepository;
import com.pafisolutions.tarjepafi.datamanagement.infrastructure.persistence.ReportRepository;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TS26 and TS27: request, consolidate, list and read reports. */
@Service
public class ReportService {

  private static final Logger log = LoggerFactory.getLogger(ReportService.class);

  private final ReportRepository reports;
  private final DataRecordRepository records;
  private final CampusClock clock;
  private final long generationDelayMs;

  public ReportService(
      ReportRepository reports,
      DataRecordRepository records,
      CampusClock clock,
      @Value("${tarjepafi.reports.generation-delay-ms:2000}") long generationDelayMs) {
    this.reports = reports;
    this.records = records;
    this.clock = clock;
    this.generationDelayMs = generationDelayMs;
  }

  /** A report with the figures per day, for the detail view and the CSV export. */
  public record ReportDetail(Report report, List<ReportCalculator.Day> days) {}

  /**
   * Creates the report GENERATING; {@link #completeDue()} consolidates it a moment later.
   *
   * @param type weekly, monthly or semester (any case)
   * @param startDate first day of the period, or null for the one that contains today
   * @param endDate last day (inclusive) of the period, or null
   */
  @Transactional
  public Report request(String type, LocalDate startDate, LocalDate endDate, String requestedBy) {
    Report.Type reportType = parseType(type);
    LocalDate[] period = defaultPeriod(reportType, clock.now().toLocalDate());
    LocalDate start = startDate == null ? period[0] : startDate;
    LocalDate end = endDate == null ? period[1] : endDate;
    LocalDateTime from = start.atStartOfDay();
    LocalDateTime to = end.atTime(23, 59, 59);
    if (!Report.isValidPeriod(from, to)) {
      throw new BusinessRuleException(
          Kind.INVALID, "INVALID_PERIOD", "The start of the period cannot be after its end");
    }
    return reports.save(
        new Report(
            "rep-" + UUID.randomUUID(),
            reportType,
            from,
            to,
            Report.Status.GENERATING,
            null,
            0,
            null,
            requestedBy,
            Instant.now()));
  }

  /** Generating first, then the most recently generated. */
  @Transactional(readOnly = true)
  public List<Report> list() {
    return reports.findAll().stream()
        .sorted(
            Comparator.comparing((Report r) -> !r.isGenerating())
                .thenComparing(
                    Comparator.comparing(
                            Report::getGeneratedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .reversed())
                .thenComparing(Comparator.comparing(Report::getId).reversed()))
        .toList();
  }

  @Transactional(readOnly = true)
  public ReportDetail detail(String id) {
    Report report =
        reports
            .findById(id)
            .orElseThrow(
                () -> new BusinessRuleException(Kind.NOT_FOUND, "REPORT_NOT_FOUND", "Unknown report " + id));
    List<ReportCalculator.Day> days =
        report.getStatus() == Report.Status.GENERATED
            ? ReportCalculator.dailyBreakdown(recordsOf(report))
            : List.of();
    return new ReportDetail(report, days);
  }

  /**
   * Consolidates the reports that have been GENERATING for at least the configured delay (real time, so it
   * does not depend on the campus clock). A report that cannot be consolidated ends in ERROR.
   *
   * @return how many reports were completed
   */
  @Transactional
  public int completeDue() {
    Instant cutoff = Instant.now().minusMillis(generationDelayMs);
    List<Report> due =
        reports.findByStatusAndRequestedAtBeforeOrderBySeqAsc(Report.Status.GENERATING, cutoff);
    for (Report report : due) {
      LocalDateTime now = clock.now();
      try {
        ReportCalculator.Result result = ReportCalculator.summarize(recordsOf(report));
        report.complete(result.summary(), result.totalRecords(), now);
      } catch (RuntimeException exception) {
        log.error("Report {} could not be generated", report.getId(), exception);
        report.fail(now);
      }
    }
    return due.size();
  }

  private List<DataRecord> recordsOf(Report report) {
    return records.findByOccurredAtBetweenOrderBySeqAsc(report.getPeriodStart(), report.getPeriodEnd());
  }

  private static Report.Type parseType(String type) {
    try {
      return Report.Type.valueOf(type == null ? "" : type.trim().toUpperCase());
    } catch (IllegalArgumentException exception) {
      throw new BusinessRuleException(
          Kind.INVALID, "INVALID_REPORT_TYPE", "The type must be weekly, monthly or semester");
    }
  }

  /** The period that contains {@code today}: Monday to Sunday, the calendar month, or the academic term. */
  static LocalDate[] defaultPeriod(Report.Type type, LocalDate today) {
    return switch (type) {
      case WEEKLY -> {
        LocalDate monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        yield new LocalDate[] {monday, monday.plusDays(6)};
      }
      case MONTHLY -> {
        YearMonth month = YearMonth.from(today);
        yield new LocalDate[] {month.atDay(1), month.atEndOfMonth()};
      }
      // Academic terms: March to July, and August to December.
      case SEMESTER ->
          today.getMonthValue() >= 8
              ? new LocalDate[] {LocalDate.of(today.getYear(), 8, 1), LocalDate.of(today.getYear(), 12, 31)}
              : new LocalDate[] {LocalDate.of(today.getYear(), 3, 1), LocalDate.of(today.getYear(), 7, 31)};
    };
  }
}
