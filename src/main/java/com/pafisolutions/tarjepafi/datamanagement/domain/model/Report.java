package com.pafisolutions.tarjepafi.datamanagement.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A consolidated report of the data records of a period (TS26, TS27). It is created GENERATING and a job
 * completes it with the summary, or marks it ERROR.
 */
@Entity
@Table(name = "reports")
public class Report {

  public enum Type {
    WEEKLY,
    MONTHLY,
    SEMESTER
  }

  public enum Status {
    GENERATING,
    GENERATED,
    ERROR
  }

  @Id private String id;

  @Generated(event = EventType.INSERT)
  @Column(name = "seq", insertable = false, updatable = false)
  private Long seq;

  @Enumerated(EnumType.STRING)
  @Column(name = "report_type", nullable = false)
  private Type reportType;

  @Column(name = "period_start", nullable = false)
  private LocalDateTime periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDateTime periodEnd;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(name = "generated_at")
  private LocalDateTime generatedAt;

  @Column(name = "total_records", nullable = false)
  private int totalRecords;

  @Column(name = "attendance_rate")
  private Double attendanceRate;

  @Column(name = "access_granted")
  private Integer accessGranted;

  @Column(name = "access_denied")
  private Integer accessDenied;

  @Column(name = "peak_hour")
  private String peakHour;

  @Column(name = "readers_with_incidents")
  private Integer readersWithIncidents;

  @Column(name = "requested_by", nullable = false)
  private String requestedBy;

  /** Real time of the request: the job waits the configured delay from it, whatever the campus clock says. */
  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  protected Report() {}

  /** A report that has been requested and not generated yet; also the shape the seeded reports are loaded with. */
  public Report(
      String id,
      Type reportType,
      LocalDateTime periodStart,
      LocalDateTime periodEnd,
      Status status,
      LocalDateTime generatedAt,
      int totalRecords,
      ReportSummary summary,
      String requestedBy,
      Instant requestedAt) {
    this.id = id;
    this.reportType = reportType;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.status = status;
    this.generatedAt = generatedAt;
    this.totalRecords = totalRecords;
    this.requestedBy = requestedBy;
    this.requestedAt = requestedAt;
    applySummary(summary);
  }

  /** Rule 6.F: the start cannot be after the end. */
  public static boolean isValidPeriod(LocalDateTime start, LocalDateTime end) {
    return start != null && end != null && !start.isAfter(end);
  }

  public void complete(ReportSummary summary, int totalRecords, LocalDateTime at) {
    this.status = Status.GENERATED;
    this.totalRecords = totalRecords;
    this.generatedAt = at;
    applySummary(summary);
  }

  public void fail(LocalDateTime at) {
    this.status = Status.ERROR;
    this.generatedAt = at;
  }

  private void applySummary(ReportSummary summary) {
    if (summary == null) {
      this.attendanceRate = null;
      this.accessGranted = null;
      this.accessDenied = null;
      this.peakHour = null;
      this.readersWithIncidents = null;
      return;
    }
    this.attendanceRate = summary.attendanceRate();
    this.accessGranted = summary.accessGranted();
    this.accessDenied = summary.accessDenied();
    this.peakHour = summary.peakHour();
    this.readersWithIncidents = summary.readersWithIncidents();
  }

  public ReportSummary summary() {
    if (attendanceRate == null) {
      return null;
    }
    return new ReportSummary(
        attendanceRate, accessGranted, accessDenied, peakHour, readersWithIncidents);
  }

  public boolean isGenerating() {
    return status == Status.GENERATING;
  }

  public String getId() {
    return id;
  }

  public Long getSeq() {
    return seq;
  }

  public Type getReportType() {
    return reportType;
  }

  public LocalDateTime getPeriodStart() {
    return periodStart;
  }

  public LocalDateTime getPeriodEnd() {
    return periodEnd;
  }

  public Status getStatus() {
    return status;
  }

  public LocalDateTime getGeneratedAt() {
    return generatedAt;
  }

  public int getTotalRecords() {
    return totalRecords;
  }

  public String getRequestedBy() {
    return requestedBy;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }
}
