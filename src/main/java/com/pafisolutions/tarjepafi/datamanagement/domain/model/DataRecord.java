package com.pafisolutions.tarjepafi.datamanagement.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * An immutable fact recorded from an integration event; the source of reports and the dashboard. The same
 * {@code eventId} can produce one record per data type and subject, never two of the same.
 */
@Entity
@Table(name = "data_records")
public class DataRecord {

  @Id private String id;

  @Generated(event = EventType.INSERT)
  @Column(name = "seq", insertable = false, updatable = false)
  private Long seq;

  @Column(name = "event_id", nullable = false, updatable = false)
  private String eventId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_context", nullable = false, updatable = false)
  private SourceContext sourceContext;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_type", nullable = false, updatable = false)
  private DataType dataType;

  @Column(name = "subject_id", nullable = false, updatable = false)
  private String subjectId;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private LocalDateTime occurredAt;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private LocalDateTime recordedAt;

  @Convert(converter = PayloadConverter.class)
  @Column(nullable = false, updatable = false)
  private Map<String, Object> payload;

  protected DataRecord() {}

  /**
   * @throws DataRegistrationException when the source does not produce that type or a field is missing
   */
  public DataRecord(
      String id,
      String eventId,
      SourceContext sourceContext,
      DataType dataType,
      String subjectId,
      LocalDateTime occurredAt,
      LocalDateTime recordedAt,
      Map<String, Object> payload) {
    if (sourceContext == null || dataType == null || !dataType.acceptsSource(sourceContext)) {
      throw new DataRegistrationException("A " + dataType + " fact cannot come from " + sourceContext);
    }
    if (eventId == null || eventId.isBlank() || subjectId == null || subjectId.isBlank()) {
      throw new DataRegistrationException("A data record needs an eventId and a subjectId");
    }
    if (occurredAt == null || recordedAt == null) {
      throw new DataRegistrationException("A data record needs occurredAt and recordedAt");
    }
    this.id = id;
    this.eventId = eventId;
    this.sourceContext = sourceContext;
    this.dataType = dataType;
    this.subjectId = subjectId;
    this.occurredAt = occurredAt;
    this.recordedAt = recordedAt;
    this.payload = new LinkedHashMap<>(payload == null ? Map.of() : payload);
  }

  public String getId() {
    return id;
  }

  public Long getSeq() {
    return seq;
  }

  public String getEventId() {
    return eventId;
  }

  public SourceContext getSourceContext() {
    return sourceContext;
  }

  public DataType getDataType() {
    return dataType;
  }

  public String getSubjectId() {
    return subjectId;
  }

  public LocalDateTime getOccurredAt() {
    return occurredAt;
  }

  public LocalDateTime getRecordedAt() {
    return recordedAt;
  }

  public Map<String, Object> getPayload() {
    return Collections.unmodifiableMap(payload);
  }

  /** A payload field as text, or null when absent. */
  public String text(String field) {
    Object value = payload.get(field);
    return value == null ? null : String.valueOf(value);
  }
}
