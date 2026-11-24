package com.pafisolutions.tarjepafi.iotmonitoring.application;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.DeviceType;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Reader;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderAction;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderConfiguration;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderMode;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TelemetrySample;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.ReaderRepository;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.TelemetryRepository;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException;
import com.pafisolutions.tarjepafi.shared.domain.BusinessRuleException.Kind;
import com.pafisolutions.tarjepafi.spacefacility.application.SpaceCatalog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reader management (rule 6.C, TS05 to TS09): register, configure, change status and read. */
@Service
public class ReaderService {

  private final ReaderRepository readers;
  private final TelemetryRepository telemetry;
  private final SpaceCatalog spaces;
  private final CampusClock clock;
  private final ApplicationEventPublisher events;

  public ReaderService(
      ReaderRepository readers,
      TelemetryRepository telemetry,
      SpaceCatalog spaces,
      CampusClock clock,
      ApplicationEventPublisher events) {
    this.readers = readers;
    this.telemetry = telemetry;
    this.spaces = spaces;
    this.clock = clock;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public List<Reader> list(ReaderStatus status, String spaceId) {
    return readers.search(status, spaceId);
  }

  @Transactional(readOnly = true)
  public Reader get(String readerId) {
    return find(readerId);
  }

  /** The newest samples first. */
  @Transactional(readOnly = true)
  public List<TelemetrySample> telemetry(String readerId, int limit) {
    find(readerId);
    return telemetry.findByReaderIdOrderByCapturedAtDescSeqDesc(
        readerId, PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
  }

  /** TS05: the id is unique and looks like RDR-A-301; the reader starts REGISTERED. */
  @Transactional
  public Reader register(
      String rawId,
      String name,
      DeviceType deviceType,
      String spaceId,
      ReaderMode mode,
      Integer communicationInterval,
      Integer heartbeatInterval,
      String firmwareVersion) {
    String id = rawId == null ? "" : rawId.trim().toUpperCase();
    if (!Reader.isValidId(id)) {
      throw new BusinessRuleException(
          Kind.INVALID, "READER_ID_INVALID", "The reader id must look like RDR-A-301");
    }
    ReaderConfiguration configuration =
        validConfiguration(mode, communicationInterval, heartbeatInterval, firmwareVersion);
    if (readers.existsById(id)) {
      throw new BusinessRuleException(Kind.CONFLICT, "READER_ID_TAKEN", "Reader " + id + " exists");
    }
    if (!spaces.exists(spaceId)) {
      throw new BusinessRuleException(Kind.NOT_FOUND, "SPACE_NOT_FOUND", "Unknown space " + spaceId);
    }
    Reader reader = readers.save(Reader.register(id, name.trim(), deviceType, spaceId, configuration, clock.now()));
    publishStatus(reader, null, "REGISTER");
    return reader;
  }

  /** TS06: an invalid configuration changes nothing and answers with every broken rule. */
  @Transactional
  public Reader reconfigure(
      String readerId,
      ReaderMode mode,
      Integer communicationInterval,
      Integer heartbeatInterval,
      String firmwareVersion) {
    Reader reader = find(readerId);
    reader.reconfigure(validConfiguration(mode, communicationInterval, heartbeatInterval, firmwareVersion));
    return reader;
  }

  /** TS07 to TS09: activate, deactivate, start or end maintenance. */
  @Transactional
  public Reader changeStatus(String readerId, ReaderAction action) {
    Reader reader = find(readerId);
    if (!reader.can(action)) {
      throw new BusinessRuleException(
          Kind.CONFLICT, "ACTION_NOT_ALLOWED", action + " is not allowed from " + reader.getStatus());
    }
    ReaderStatus from = reader.getStatus();
    LocalDateTime now = clock.now();
    reader.apply(action, now);
    publishStatus(reader, from, action.name());
    return reader;
  }

  private Reader find(String readerId) {
    return readers
        .findById(readerId)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    Kind.NOT_FOUND, "READER_NOT_FOUND", "Unknown reader " + readerId));
  }

  private static ReaderConfiguration validConfiguration(
      ReaderMode mode, Integer communicationInterval, Integer heartbeatInterval, String firmware) {
    List<String> errors =
        ReaderConfiguration.validate(mode, communicationInterval, heartbeatInterval, firmware);
    if (!errors.isEmpty()) {
      throw new BusinessRuleException(
              Kind.INVALID, "READER_CONFIGURATION_INVALID", "The reader configuration is not valid")
          .with("errors", errors);
    }
    return new ReaderConfiguration(mode, communicationInterval, heartbeatInterval, firmware);
  }

  private void publishStatus(Reader reader, ReaderStatus from, String cause) {
    events.publishEvent(
        new IntegrationEvent(
            "iot.reader-status-changed",
            Map.of(
                "readerId", reader.getId(),
                "spaceId", reader.getSpaceId(),
                "fromStatus", from == null ? "" : from.name(),
                "toStatus", reader.getStatus().name(),
                "cause", cause)));
  }
}
