package com.pafisolutions.tarjepafi.datamanagement.interfaces.rest;

import com.pafisolutions.tarjepafi.datamanagement.application.DataQueryService.Dashboard;
import com.pafisolutions.tarjepafi.datamanagement.application.DataQueryService.HourCount;
import com.pafisolutions.tarjepafi.datamanagement.application.ReportService.ReportDetail;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataRecord;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.Report;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.ReportCalculator;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.ReportSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** The JSON shapes of the Data Management API. Field names follow the web app's data-management model. */
public final class DataResources {

  private DataResources() {}

  /**
   * An immutable fact. {@code dataType}: CARD_READING, DEVICE_TELEMETRY, ATTENDANCE, ACCESS, WORK_SHIFT or
   * CREDENTIAL_CHANGE; {@code sourceContext}: IOT_MONITORING, ACADEMIC_ATTENDANCE, SPACE_AND_FACILITY or
   * CREDENTIALS. {@code payload} depends on the type.
   */
  public record DataRecordResource(
      String id,
      String cursor,
      String eventId,
      String sourceContext,
      String dataType,
      String subjectId,
      LocalDateTime occurredAt,
      LocalDateTime recordedAt,
      Map<String, Object> payload) {

    public static DataRecordResource from(DataRecord record) {
      return new DataRecordResource(
          record.getId(),
          record.getSeq() == null ? null : String.valueOf(record.getSeq()),
          record.getEventId(),
          record.getSourceContext().name(),
          record.getDataType().name(),
          record.getSubjectId(),
          record.getOccurredAt(),
          record.getRecordedAt(),
          record.getPayload());
    }
  }

  /** A page of facts, newest first, and the cursor to send as {@code after} on the next poll. */
  public record DataRecordPage(List<DataRecordResource> items, String nextCursor) {}

  public record AttendanceFigures(Map<String, Integer> byStatus, Double rate) {}

  public record AccessFigures(int granted, int denied) {}

  public record ReadingFigures(int today, List<HourCount> byHour, List<DataRecordResource> recent) {}

  /**
   * The figures of one day, from the recorded facts. {@code attendance.byStatus} counts students by the last
   * fact of each mark; {@code rate} is a percentage (null when nothing was resolved).
   */
  public record DashboardResource(
      LocalDate date,
      LocalDateTime now,
      AttendanceFigures attendance,
      AccessFigures access,
      ReadingFigures readings,
      int readersWithIncidents) {

    public static DashboardResource from(Dashboard dashboard) {
      return new DashboardResource(
          dashboard.date(),
          dashboard.now(),
          new AttendanceFigures(dashboard.attendanceByStatus(), dashboard.attendanceRate()),
          new AccessFigures(dashboard.accessGranted(), dashboard.accessDenied()),
          new ReadingFigures(
              dashboard.readingsToday(),
              dashboard.readingsByHour(),
              dashboard.recentReadings().stream().map(DataRecordResource::from).toList()),
          dashboard.readersWithIncidents());
    }
  }

  public record ReportSummaryResource(
      double attendanceRate,
      int accessGranted,
      int accessDenied,
      String peakHour,
      int readersWithIncidents) {

    static ReportSummaryResource from(ReportSummary summary) {
      return summary == null
          ? null
          : new ReportSummaryResource(
              summary.attendanceRate(),
              summary.accessGranted(),
              summary.accessDenied(),
              summary.peakHour(),
              summary.readersWithIncidents());
    }
  }

  /**
   * A report. {@code reportType}: WEEKLY, MONTHLY or SEMESTER; {@code status}: GENERATING (poll until it
   * changes), GENERATED or ERROR. {@code summary} and {@code generatedAt} are null while GENERATING.
   */
  public record ReportResource(
      String id,
      String reportType,
      LocalDateTime periodStart,
      LocalDateTime periodEnd,
      String status,
      LocalDateTime generatedAt,
      int totalRecords,
      ReportSummaryResource summary,
      String requestedBy) {

    public static ReportResource from(Report report) {
      return new ReportResource(
          report.getId(),
          report.getReportType().name(),
          report.getPeriodStart(),
          report.getPeriodEnd(),
          report.getStatus().name(),
          report.getGeneratedAt(),
          report.getTotalRecords(),
          ReportSummaryResource.from(report.summary()),
          report.getRequestedBy());
    }
  }

  /** One day of a report. {@code attendanceRate} is null when no attendance was resolved that day. */
  public record DayResource(
      LocalDate date, int readings, Double attendanceRate, int accessGranted, int accessDenied) {

    static DayResource from(ReportCalculator.Day day) {
      return new DayResource(
          day.date(), day.readings(), day.attendanceRate(), day.accessGranted(), day.accessDenied());
    }
  }

  public record ReportDetailResource(ReportResource report, List<DayResource> days) {

    public static ReportDetailResource from(ReportDetail detail) {
      return new ReportDetailResource(
          ReportResource.from(detail.report()),
          detail.days().stream().map(DayResource::from).toList());
    }
  }

  /** Both dates are optional (yyyy-MM-dd, the end inclusive); without them the period that contains today is used. */
  public record RequestReportRequest(LocalDate periodStart, LocalDate periodEnd) {}
}
