package com.pafisolutions.tarjepafi.datamanagement.interfaces.rest;

import com.pafisolutions.tarjepafi.datamanagement.application.DataQueryService;
import com.pafisolutions.tarjepafi.datamanagement.application.ReportService;
import com.pafisolutions.tarjepafi.datamanagement.interfaces.rest.DataResources.DashboardResource;
import com.pafisolutions.tarjepafi.datamanagement.interfaces.rest.DataResources.DataRecordPage;
import com.pafisolutions.tarjepafi.datamanagement.interfaces.rest.DataResources.DataRecordResource;
import com.pafisolutions.tarjepafi.datamanagement.interfaces.rest.DataResources.ReportDetailResource;
import com.pafisolutions.tarjepafi.datamanagement.interfaces.rest.DataResources.ReportResource;
import com.pafisolutions.tarjepafi.datamanagement.interfaces.rest.DataResources.RequestReportRequest;
import com.pafisolutions.tarjepafi.shared.application.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Data records, dashboard and reports (TS24 to TS27). Needs the ADMIN role. */
@RestController
@Tag(name = "Data management (admin)")
public class DataManagementController {

  private final DataQueryService queries;
  private final ReportService reports;

  public DataManagementController(DataQueryService queries, ReportService reports) {
    this.queries = queries;
    this.reports = reports;
  }

  // ---------- TS25: data records and dashboard ----------

  @GetMapping("/api/v1/data-records")
  @Operation(
      summary = "Recorded facts, newest first (poll with `after`)",
      description =
          "Filters `type` (CARD_READING, DEVICE_TELEMETRY, ATTENDANCE, ACCESS, WORK_SHIFT, CREDENTIAL_CHANGE), "
              + "`source` (IOT_MONITORING, ACADEMIC_ATTENDANCE, SPACE_AND_FACILITY, CREDENTIALS), `subjectId`, "
              + "and `from` / `to` (campus date-times of `occurredAt`). Send the previous `nextCursor` as `after` "
              + "to get only what is new; at most `limit` items (default 50, at most 200). 400 INVALID_DATA_TYPE / "
              + "INVALID_SOURCE_CONTEXT / INVALID_RANGE / INVALID_CURSOR.")
  public DataRecordPage dataRecords(
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String subjectId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) Integer limit) {
    DataQueryService.RecordPage page = queries.records(type, source, subjectId, from, to, after, limit);
    return new DataRecordPage(
        page.items().stream().map(DataRecordResource::from).toList(), page.nextCursor());
  }

  @GetMapping("/api/v1/dashboard")
  @Operation(
      summary = "Figures of one day from the recorded facts",
      description =
          "`date` (yyyy-MM-dd) defaults to today by the campus clock. Attendance counts students by the last "
              + "fact of each mark; access is granted against denied; readings are the card readings of the day "
              + "by hour with the 8 most recent. Reader status and card status are not here: read them from the "
              + "IoT and credentials endpoints.")
  public DashboardResource dashboard(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return DashboardResource.from(queries.dashboard(date));
  }

  // ---------- TS26, TS27: reports ----------

  @PostMapping("/api/v1/reports/{type}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      summary = "Request a weekly, monthly or semester report (TS26)",
      description =
          "`type` is `weekly`, `monthly` or `semester`. The body is optional: `periodStart` and `periodEnd` "
              + "(yyyy-MM-dd, the end inclusive) default to the week (Monday to Sunday), month or academic term "
              + "(March to July, August to December) that contains today by the campus clock. Answers 202 with "
              + "the report GENERATING; poll `GET /reports/{id}` until `status` is GENERATED (or ERROR). 400 "
              + "INVALID_REPORT_TYPE / INVALID_PERIOD.")
  public ReportResource request(
      @PathVariable String type,
      @RequestBody(required = false) RequestReportRequest body,
      @AuthenticationPrincipal AuthenticatedUser caller) {
    return ReportResource.from(
        reports.request(
            type,
            body == null ? null : body.periodStart(),
            body == null ? null : body.periodEnd(),
            caller.id()));
  }

  @GetMapping("/api/v1/reports")
  @Operation(
      summary = "Reports (TS27)",
      description = "The ones still GENERATING first, then the most recently generated.")
  public List<ReportResource> reports() {
    return reports.list().stream().map(ReportResource::from).toList();
  }

  @GetMapping("/api/v1/reports/{id}")
  @Operation(
      summary = "A report with its figures per day (TS27)",
      description =
          "`days` has the days of the period with at least one fact and is empty until the report is "
              + "GENERATED. 404 REPORT_NOT_FOUND.")
  public ReportDetailResource report(@PathVariable String id) {
    return ReportDetailResource.from(reports.detail(id));
  }
}
