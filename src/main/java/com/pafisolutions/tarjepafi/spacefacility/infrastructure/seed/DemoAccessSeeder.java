package com.pafisolutions.tarjepafi.spacefacility.infrastructure.seed;

import com.pafisolutions.tarjepafi.shared.infrastructure.seed.DemoSeed;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.AccessRecord;
import com.pafisolutions.tarjepafi.spacefacility.domain.model.WorkShift;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.AccessRecordRepository;
import com.pafisolutions.tarjepafi.spacefacility.infrastructure.persistence.WorkShiftRepository;
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
 * Imports the demo access records and work shifts. Existing rows are left alone. Both go in by time, so
 * their cursor grows with time like it would have live.
 */
@Component
@Profile(DemoSeed.PROFILE)
@Order(40)
public class DemoAccessSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoAccessSeeder.class);

  private final DemoSeed seed;
  private final AccessRecordRepository accessRecords;
  private final WorkShiftRepository shifts;

  public DemoAccessSeeder(
      DemoSeed seed, AccessRecordRepository accessRecords, WorkShiftRepository shifts) {
    this.seed = seed;
    this.accessRecords = accessRecords;
    this.shifts = shifts;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<JsonNode> access = new ArrayList<>();
    for (JsonNode row : seed.collection("accessRecords")) {
      if (!accessRecords.existsById(row.path("id").asString())) {
        access.add(row);
      }
    }
    access.sort(Comparator.comparing(row -> row.path("attemptedAt").asString()));
    List<AccessRecord> newAccess = new ArrayList<>();
    for (JsonNode row : access) {
      newAccess.add(
          new AccessRecord(
              row.path("id").asString(),
              row.path("tapEventId").asString(),
              row.path("cardUid").asString(),
              text(row, "credentialId"),
              text(row, "cardHolderId"),
              row.path("spaceId").asString(),
              row.path("readerId").asString(),
              row.path("accessType").asString(),
              row.path("decision").asString(),
              row.path("denialReason").asString(),
              LocalDateTime.parse(row.path("attemptedAt").asString())));
    }
    accessRecords.saveAll(newAccess);

    List<JsonNode> shiftRows = new ArrayList<>();
    for (JsonNode row : seed.collection("workShifts")) {
      if (!shifts.existsById(row.path("id").asString())) {
        shiftRows.add(row);
      }
    }
    shiftRows.sort(Comparator.comparing(row -> row.path("entryAt").asString()));
    List<WorkShift> newShifts = new ArrayList<>();
    for (JsonNode row : shiftRows) {
      newShifts.add(
          WorkShift.of(
              row.path("id").asString(),
              row.path("cardHolderId").asString(),
              LocalDate.parse(row.path("date").asString()),
              LocalDateTime.parse(row.path("entryAt").asString()),
              row.path("exitAt").isString() ? LocalDateTime.parse(row.path("exitAt").asString()) : null,
              row.path("workedMinutes").isNumber() ? row.path("workedMinutes").asInt() : null,
              row.path("status").asString()));
    }
    shifts.saveAll(newShifts);

    if (!newAccess.isEmpty() || !newShifts.isEmpty()) {
      log.info(
          "Demo seed: {} access records, {} work shifts imported", newAccess.size(), newShifts.size());
    }
  }

  private static String text(JsonNode row, String field) {
    JsonNode value = row.path(field);
    return value.isString() ? value.asString() : null;
  }
}
