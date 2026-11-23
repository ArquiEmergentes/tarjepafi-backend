package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.seed;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.CardTapEvent;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.DeviceType;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Incident;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.IncidentType;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Reader;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderConfiguration;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderMode;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TapOutcome;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TapSource;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TelemetrySample;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.CardTapEventRepository;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.IncidentRepository;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.ReaderRepository;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.TelemetryRepository;
import com.pafisolutions.tarjepafi.shared.infrastructure.seed.DemoSeed;
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
 * Imports the demo readers, telemetry, incidents and past tap events. Existing rows are left alone. The
 * tap events go in by time, so their cursor grows with time like it would have live.
 */
@Component
@Profile(DemoSeed.PROFILE)
@Order(25)
public class DemoIotSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoIotSeeder.class);

  private final DemoSeed seed;
  private final ReaderRepository readers;
  private final TelemetryRepository telemetry;
  private final IncidentRepository incidents;
  private final CardTapEventRepository taps;

  public DemoIotSeeder(
      DemoSeed seed,
      ReaderRepository readers,
      TelemetryRepository telemetry,
      IncidentRepository incidents,
      CardTapEventRepository taps) {
    this.seed = seed;
    this.readers = readers;
    this.telemetry = telemetry;
    this.incidents = incidents;
    this.taps = taps;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<Reader> newReaders = new ArrayList<>();
    for (JsonNode row : seed.collection("readers")) {
      if (readers.existsById(row.path("id").asString())) {
        continue;
      }
      JsonNode config = row.path("configuration");
      newReaders.add(
          new Reader(
              row.path("id").asString(),
              row.path("name").asString(),
              DeviceType.valueOf(row.path("deviceType").asString()),
              row.path("spaceId").asString(),
              ReaderStatus.valueOf(row.path("status").asString()),
              new ReaderConfiguration(
                  ReaderMode.valueOf(config.path("readerMode").asString()),
                  config.path("communicationInterval").asInt(),
                  config.path("heartbeatInterval").asInt(),
                  config.path("firmwareVersion").asString()),
              LocalDateTime.parse(row.path("registeredAt").asString()),
              time(row, "lastSeenAt")));
    }
    readers.saveAll(newReaders);

    List<TelemetrySample> newSamples = new ArrayList<>();
    for (JsonNode row : seed.collection("telemetry")) {
      if (!telemetry.existsById(row.path("id").asString())) {
        newSamples.add(
            new TelemetrySample(
                row.path("id").asString(),
                row.path("readerId").asString(),
                LocalDateTime.parse(row.path("capturedAt").asString()),
                row.path("signalStrength").asInt(),
                row.path("uptime").asLong(),
                row.path("firmwareVersion").asString(),
                ReaderStatus.valueOf(row.path("status").asString())));
      }
    }
    telemetry.saveAll(newSamples);

    List<Incident> newIncidents = new ArrayList<>();
    for (JsonNode row : seed.collection("incidents")) {
      if (!incidents.existsById(row.path("id").asString())) {
        newIncidents.add(
            new Incident(
                row.path("id").asString(),
                row.path("readerId").asString(),
                IncidentType.valueOf(row.path("type").asString()),
                LocalDateTime.parse(row.path("detectedAt").asString()),
                row.path("description").asString(),
                time(row, "resolvedAt")));
      }
    }
    incidents.saveAll(newIncidents);

    List<CardTapEvent> newTaps = new ArrayList<>();
    for (JsonNode row : seed.collection("cardTapEvents")) {
      if (!taps.existsById(row.path("id").asString())) {
        newTaps.add(
            new CardTapEvent(
                row.path("id").asString(),
                row.path("readerId").asString(),
                row.path("cardUid").asString(),
                LocalDateTime.parse(row.path("tappedAt").asString()),
                TapOutcome.valueOf(row.path("outcome").asString()),
                row.path("displayMessage").asString(),
                TapSource.SIMULATOR));
      }
    }
    newTaps.sort(Comparator.comparing(CardTapEvent::getTappedAt));
    taps.saveAll(newTaps);

    if (!newReaders.isEmpty() || !newTaps.isEmpty()) {
      log.info(
          "Demo seed: {} readers, {} telemetry samples, {} incidents, {} tap events imported",
          newReaders.size(),
          newSamples.size(),
          newIncidents.size(),
          newTaps.size());
    }
  }

  private static LocalDateTime time(JsonNode row, String field) {
    JsonNode node = row.path(field);
    return node.isString() ? LocalDateTime.parse(node.asString()) : null;
  }
}
