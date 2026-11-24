package com.pafisolutions.tarjepafi.datamanagement.infrastructure.seed;

import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataRecord;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataType;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.Report;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.ReportSummary;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.SourceContext;
import com.pafisolutions.tarjepafi.datamanagement.infrastructure.persistence.DataRecordRepository;
import com.pafisolutions.tarjepafi.datamanagement.infrastructure.persistence.ReportRepository;
import com.pafisolutions.tarjepafi.shared.infrastructure.seed.DemoSeed;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Imports the demo data records and reports. Rows that already exist are left alone. It runs last, after the
 * seeders of the contexts that produce the facts.
 */
@Component
@Profile(DemoSeed.PROFILE)
@Order(50)
public class DemoDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  private final DemoSeed seed;
  private final DataRecordRepository records;
  private final ReportRepository reports;

  public DemoDataSeeder(DemoSeed seed, DataRecordRepository records, ReportRepository reports) {
    this.seed = seed;
    this.records = records;
    this.reports = reports;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<DataRecord> newRecords = new ArrayList<>();
    for (JsonNode row : seed.collection("dataRecords")) {
      if (records.existsById(row.path("id").asString())) {
        continue;
      }
      newRecords.add(
          new DataRecord(
              row.path("id").asString(),
              row.path("eventId").asString(),
              SourceContext.valueOf(row.path("sourceContext").asString()),
              DataType.valueOf(row.path("dataType").asString()),
              row.path("subjectId").asString(),
              LocalDateTime.parse(row.path("occurredAt").asString()),
              LocalDateTime.parse(row.path("recordedAt").asString()),
              payload(row.path("payload"))));
    }
    // The sequence is the recording order: the calculator keeps the last fact of each attendance.
    newRecords.sort(Comparator.comparing(DataRecord::getRecordedAt));
    records.saveAll(newRecords);

    List<Report> newReports = new ArrayList<>();
    for (JsonNode row : seed.collection("reports")) {
      if (reports.existsById(row.path("id").asString())) {
        continue;
      }
      JsonNode summary = row.path("summary");
      newReports.add(
          new Report(
              row.path("id").asString(),
              Report.Type.valueOf(row.path("reportType").asString()),
              LocalDateTime.parse(row.path("periodStart").asString()),
              LocalDateTime.parse(row.path("periodEnd").asString()),
              Report.Status.valueOf(row.path("status").asString()),
              row.path("generatedAt").isString()
                  ? LocalDateTime.parse(row.path("generatedAt").asString())
                  : null,
              row.path("totalRecords").asInt(),
              summary.isObject()
                  ? new ReportSummary(
                      summary.path("attendanceRate").asDouble(),
                      summary.path("accessGranted").asInt(),
                      summary.path("accessDenied").asInt(),
                      summary.path("peakHour").isString() ? summary.path("peakHour").asString() : null,
                      summary.path("readersWithIncidents").asInt())
                  : null,
              row.path("requestedBy").asString(),
              Instant.now()));
    }
    reports.saveAll(newReports);

    if (!newRecords.isEmpty() || !newReports.isEmpty()) {
      log.info("Demo seed: {} data records and {} reports imported", newRecords.size(), newReports.size());
    }
  }

  private static Map<String, Object> payload(JsonNode node) {
    Map<String, Object> payload = new LinkedHashMap<>();
    if (node.isObject()) {
      node.properties()
          .forEach(
              entry -> {
                JsonNode value = entry.getValue();
                if (value.isNumber()) {
                  payload.put(entry.getKey(), value.numberValue());
                } else if (value.isBoolean()) {
                  payload.put(entry.getKey(), value.asBoolean());
                } else if (!value.isNull()) {
                  payload.put(entry.getKey(), value.asString());
                }
              });
    }
    return payload;
  }
}
