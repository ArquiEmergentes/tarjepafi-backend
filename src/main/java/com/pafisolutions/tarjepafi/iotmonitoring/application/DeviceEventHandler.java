package com.pafisolutions.tarjepafi.iotmonitoring.application;

import com.pafisolutions.tarjepafi.credentials.application.CardHolderDirectoryPort;
import com.pafisolutions.tarjepafi.credentials.application.CardHolderDirectoryPort.CardLookup;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.HeartbeatReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.ReaderErrorReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.TapReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.application.DeviceMessage.TelemetryReceived;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.CardTapEvent;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.IncidentType;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Reader;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TapOutcome;
import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.TelemetrySample;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.CardTapEventRepository;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.ReaderRepository;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.TelemetryRepository;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import com.pafisolutions.tarjepafi.shared.application.IntegrationEvent;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the backend does with a validated device message (TS10, TS11). Every handler is idempotent or
 * harmless when a message arrives twice.
 */
@Service
public class DeviceEventHandler {

  private static final Logger log = LoggerFactory.getLogger(DeviceEventHandler.class);

  private final ReaderRepository readers;
  private final CardTapEventRepository taps;
  private final TelemetryRepository telemetry;
  private final IncidentService incidents;
  private final CardHolderDirectoryPort directory;
  private final CampusClock clock;
  private final ApplicationEventPublisher events;

  public DeviceEventHandler(
      ReaderRepository readers,
      CardTapEventRepository taps,
      TelemetryRepository telemetry,
      IncidentService incidents,
      CardHolderDirectoryPort directory,
      CampusClock clock,
      ApplicationEventPublisher events) {
    this.readers = readers;
    this.taps = taps;
    this.telemetry = telemetry;
    this.incidents = incidents;
    this.directory = directory;
    this.clock = clock;
    this.events = events;
  }

  /**
   * TS10. A tap is stored once per {@code eventId}. A reader that is unknown or cannot process taps, and
   * a card that is unknown or inactive, reject the tap right away (and open an incident for the reader);
   * otherwise the tap stays PENDING and is announced as {@code iot.card-tap} for the context that resolves it.
   */
  @Transactional
  public void handle(TapReceived message) {
    if (taps.existsById(message.eventId())) {
      log.debug("Tap {} was already processed", message.eventId());
      // The reader is told, but nothing is stored or resolved twice (AD13).
      events.publishEvent(
          new ReaderDisplayRequested(
              message.readerId(),
              message.eventId(),
              "info",
              DisplayMessages.lines(DisplayMessages.render("tap.duplicateEvent", Map.of()))));
      return;
    }
    CardTapEvent event =
        new CardTapEvent(
            message.eventId(), message.readerId(), message.cardUid(), message.tappedAt(), message.source());
    Optional<Reader> found = readers.findById(message.readerId());
    if (found.isEmpty()) {
      incidents.openIfAbsent(
          message.readerId(),
          IncidentType.CONFIGURATION_ERROR,
          "An unregistered reader (" + message.readerId() + ") sent a tap.",
          clock.now());
      reject(event, "tap.readerOutOfService", "READER_NOT_REGISTERED", null, null);
      return;
    }
    Reader reader = found.get();
    if (!reader.canProcessTaps()) {
      incidents.openIfAbsent(
          reader.getId(),
          IncidentType.READ_ERROR,
          "Reader " + reader.getId() + " sent a tap while " + reader.getStatus() + ".",
          clock.now());
      reject(event, "tap.readerOutOfService", "READER_OUT_OF_SERVICE", reader, null);
      return;
    }
    reader.seenAt(message.tappedAt());

    Optional<CardLookup> lookup = directory.findByCardUid(message.cardUid());
    if (lookup.isEmpty()) {
      reject(event, "tap.cardNotRegistered", "CARD_NOT_REGISTERED", reader, null);
      return;
    }
    CardLookup card = lookup.get();
    if (!card.active() || card.holder() == null) {
      reject(event, "tap.cardInactive", "CARD_INACTIVE", reader, card);
      return;
    }
    directory.markUsed(card.credentialId(), message.tappedAt());
    taps.save(event);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("tapEventId", event.getId());
    data.put("readerId", reader.getId());
    data.put("spaceId", reader.getSpaceId());
    data.put("cardUid", event.getCardUid());
    data.put("credentialId", card.credentialId());
    data.put("cardHolderId", card.holder().id());
    data.put("holderRole", card.holder().role());
    data.put("tappedAt", event.getTappedAt().toString());
    data.put("source", event.getSource().name());
    events.publishEvent(new IntegrationEvent("iot.card-tap", data));
  }

  /** TS11: telemetry counts as a heartbeat, updates the reader and may degrade or restore it. */
  @Transactional
  public void handle(TelemetryReceived message) {
    Optional<Reader> found = readers.findById(message.readerId());
    if (found.isEmpty()) {
      unknownReader(message.readerId(), "telemetry");
      return;
    }
    Reader reader = found.get();
    ReaderStatus before = reader.getStatus();
    reader.heartbeat(message.capturedAt());
    reader.applySignal(TelemetrySample.isWeak(message.signalStrength()));
    telemetry.save(
        new TelemetrySample(
            "tel-" + UUID.randomUUID(),
            reader.getId(),
            message.capturedAt(),
            message.signalStrength(),
            message.uptime(),
            message.firmwareVersion(),
            reader.getStatus()));
    afterContact(reader, before);
  }

  @Transactional
  public void handle(HeartbeatReceived message) {
    Optional<Reader> found = readers.findById(message.readerId());
    if (found.isEmpty()) {
      unknownReader(message.readerId(), "heartbeat");
      return;
    }
    Reader reader = found.get();
    ReaderStatus before = reader.getStatus();
    reader.heartbeat(message.sentAt());
    afterContact(reader, before);
  }

  /** A reader reporting its own failure becomes an incident. */
  @Transactional
  public void handle(ReaderErrorReceived message) {
    if (!readers.existsById(message.readerId())) {
      unknownReader(message.readerId(), "error report");
      return;
    }
    incidents.openIfAbsent(message.readerId(), message.type(), message.message(), message.occurredAt());
  }

  /** Marks a reader OFFLINE and opens its incident. Called by the connectivity monitor. */
  @Transactional
  public boolean markOffline(String readerId, LocalDateTime now) {
    Reader reader = readers.findById(readerId).orElse(null);
    if (reader == null || !reader.hasLostConnection(now)) {
      return false;
    }
    ReaderStatus before = reader.getStatus();
    reader.markOffline();
    incidents.openIfAbsent(
        reader.getId(),
        IncidentType.DEVICE_OFFLINE,
        reader.getLastSeenAt() == null
            ? "The reader has never sent a heartbeat."
            : "The reader has not sent heartbeats since " + reader.getLastSeenAt() + ".",
        now);
    events.publishEvent(
        new IntegrationEvent(
            "iot.device-offline-detected",
            Map.of(
                "readerId", reader.getId(),
                "spaceId", reader.getSpaceId(),
                "fromStatus", before.name(),
                "lastSeenAt", String.valueOf(reader.getLastSeenAt()),
                "detectedAt", now.toString())));
    return true;
  }

  private void afterContact(Reader reader, ReaderStatus before) {
    if (before == ReaderStatus.OFFLINE && reader.getStatus() != ReaderStatus.OFFLINE) {
      incidents.resolveOpen(reader.getId(), IncidentType.DEVICE_OFFLINE, clock.now());
    }
    if (before != reader.getStatus()) {
      events.publishEvent(
          new IntegrationEvent(
              "iot.reader-status-changed",
              Map.of(
                  "readerId", reader.getId(),
                  "spaceId", reader.getSpaceId(),
                  "fromStatus", before.name(),
                  "toStatus", reader.getStatus().name(),
                  "cause", "CONTACT")));
    }
  }

  private void unknownReader(String readerId, String what) {
    incidents.openIfAbsent(
        readerId,
        IncidentType.CONFIGURATION_ERROR,
        "An unregistered reader (" + readerId + ") sent " + what + ".",
        clock.now());
  }

  /** Stores the event as REJECTED, announces it and answers on the reader's screen. */
  private void reject(
      CardTapEvent event, String messageKey, String reason, Reader reader, CardLookup card) {
    String text = DisplayMessages.render(messageKey, Map.of());
    event.resolve(TapOutcome.REJECTED, "error", messageKey, null, text);
    taps.save(event);
    events.publishEvent(
        new ReaderDisplayRequested(
            event.getReaderId(), event.getId(), "error", DisplayMessages.lines(text)));
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("tapEventId", event.getId());
    data.put("readerId", event.getReaderId());
    data.put("cardUid", event.getCardUid());
    data.put("tappedAt", event.getTappedAt().toString());
    data.put("reason", reason);
    if (reader != null) {
      data.put("spaceId", reader.getSpaceId());
    }
    if (card != null) {
      data.put("credentialId", card.credentialId());
      if (card.holder() != null) {
        data.put("cardHolderId", card.holder().id());
      }
    }
    events.publishEvent(new IntegrationEvent("iot.tap-rejected", data));
  }
}
