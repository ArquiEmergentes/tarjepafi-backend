package com.pafisolutions.tarjepafi.datamanagement.application;

import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataRecord;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.DataType;
import com.pafisolutions.tarjepafi.datamanagement.domain.model.SourceContext;
import com.pafisolutions.tarjepafi.datamanagement.infrastructure.persistence.DataRecordRepository;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TS24: turns the integration events of the other contexts into immutable data records. A fact is recorded
 * once per event: the same event delivered again (a broker redelivery) adds nothing.
 */
@Service
public class DataIngestionService {

  private static final Logger log = LoggerFactory.getLogger(DataIngestionService.class);

  private final DataRecordRepository records;
  private final CampusClock clock;

  public DataIngestionService(DataRecordRepository records, CampusClock clock) {
    this.records = records;
    this.clock = clock;
  }

  /** A fact extracted from an event, before it is stored. */
  record Fact(
      SourceContext source,
      DataType type,
      String subjectId,
      LocalDateTime occurredAt,
      Map<String, Object> payload) {}

  /**
   * @param eventId the id of the event envelope
   * @param routingKey the event type, for example {@code space.access-granted}
   * @param envelopeAt when the event was published (campus time), used when the data has no moment of its own
   * @param data the data of the event
   * @return how many records were stored (0 for an event that is not a fact or was already recorded)
   * @throws com.pafisolutions.tarjepafi.datamanagement.domain.model.DataRegistrationException when the event
   *     breaks the rules of a data record
   */
  @Transactional
  public int ingest(String eventId, String routingKey, LocalDateTime envelopeAt, Map<String, Object> data) {
    int stored = 0;
    for (Fact fact : DataFacts.from(routingKey, envelopeAt, data)) {
      if (records.existsByEventIdAndDataTypeAndSubjectId(eventId, fact.type(), fact.subjectId())) {
        log.debug("Event {} already recorded as {} of {}", eventId, fact.type(), fact.subjectId());
        continue;
      }
      records.save(
          new DataRecord(
              "dr-" + UUID.randomUUID(),
              eventId,
              fact.source(),
              fact.type(),
              fact.subjectId(),
              fact.occurredAt(),
              clock.now(),
              fact.payload()));
      stored++;
    }
    return stored;
  }

  /** Small helper for the mapping: a payload with only the non-null entries. */
  static Map<String, Object> payload(Object... keysAndValues) {
    Map<String, Object> payload = new LinkedHashMap<>();
    for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
      if (keysAndValues[i + 1] != null) {
        payload.put((String) keysAndValues[i], keysAndValues[i + 1]);
      }
    }
    return payload;
  }

  static List<Fact> none() {
    return List.of();
  }
}
