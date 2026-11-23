package com.pafisolutions.tarjepafi.iotmonitoring.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** An NFC reader installed in a space (rule 6.C). */
@Entity
@Table(name = "readers")
public class Reader {

  /** A reader that stays silent for this many heartbeats is considered offline. */
  public static final int MISSED_HEARTBEATS_FOR_OFFLINE = 3;

  private static final Pattern ID_FORMAT = Pattern.compile("^RDR-[A-Z]-\\d{3}$");

  @Id private String id;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "device_type", nullable = false)
  private DeviceType deviceType;

  @Column(name = "space_id", nullable = false)
  private String spaceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReaderStatus status;

  @Embedded private ReaderConfiguration configuration;

  @Column(name = "registered_at", nullable = false)
  private LocalDateTime registeredAt;

  @Column(name = "last_seen_at")
  private LocalDateTime lastSeenAt;

  protected Reader() {}

  public Reader(
      String id,
      String name,
      DeviceType deviceType,
      String spaceId,
      ReaderStatus status,
      ReaderConfiguration configuration,
      LocalDateTime registeredAt,
      LocalDateTime lastSeenAt) {
    this.id = id;
    this.name = name;
    this.deviceType = deviceType;
    this.spaceId = spaceId;
    this.status = status;
    this.configuration = configuration;
    this.registeredAt = registeredAt;
    this.lastSeenAt = lastSeenAt;
  }

  /** A new reader starts REGISTERED and has not been seen yet. */
  public static Reader register(
      String id,
      String name,
      DeviceType deviceType,
      String spaceId,
      ReaderConfiguration configuration,
      LocalDateTime now) {
    return new Reader(id, name, deviceType, spaceId, ReaderStatus.REGISTERED, configuration, now, null);
  }

  /** Reader ids look like {@code RDR-A-301}. */
  public static boolean isValidId(String id) {
    return id != null && ID_FORMAT.matcher(id).matches();
  }

  /** Only ACTIVE or DEGRADED readers process taps. */
  public boolean canProcessTaps() {
    return status == ReaderStatus.ACTIVE || status == ReaderStatus.DEGRADED;
  }

  public boolean can(ReaderAction action) {
    return action.allowedFrom(status);
  }

  public List<ReaderAction> availableActions() {
    return Arrays.stream(ReaderAction.values()).filter(this::can).toList();
  }

  /** How long a reader in service may stay silent: 3 heartbeats. */
  public Duration silenceTolerance() {
    return Duration.ofSeconds((long) configuration.getHeartbeatInterval() * MISSED_HEARTBEATS_FOR_OFFLINE);
  }

  /** True when a reader in service has been silent for more than 3 heartbeats. */
  public boolean hasLostConnection(LocalDateTime now) {
    if (!canProcessTaps()) {
      return false;
    }
    if (lastSeenAt == null) {
      return true;
    }
    return Duration.between(lastSeenAt, now).compareTo(silenceTolerance()) > 0;
  }

  /**
   * Applies a manual transition. Returning to service counts as a communication at that moment.
   *
   * @throws IllegalStateException when the action is not allowed from the current status
   */
  public void apply(ReaderAction action, LocalDateTime at) {
    if (!can(action)) {
      throw new IllegalStateException(action + " is not allowed from " + status);
    }
    this.status = action.target();
    if (status == ReaderStatus.ACTIVE) {
      this.lastSeenAt = at;
    }
  }

  public void markOffline() {
    this.status = ReaderStatus.OFFLINE;
  }

  /** A heartbeat or telemetry message: updates the last contact and brings an OFFLINE reader back. */
  public void heartbeat(LocalDateTime at) {
    if (lastSeenAt == null || at.isAfter(lastSeenAt)) {
      this.lastSeenAt = at;
    }
    if (status == ReaderStatus.OFFLINE) {
      this.status = ReaderStatus.ACTIVE;
    }
  }

  /** A processed tap means the reader communicated at that moment. */
  public void seenAt(LocalDateTime at) {
    if (lastSeenAt == null || at.isAfter(lastSeenAt)) {
      this.lastSeenAt = at;
    }
  }

  /** A weak signal degrades an ACTIVE reader; a good one restores a DEGRADED reader. */
  public void applySignal(boolean weak) {
    if (weak && status == ReaderStatus.ACTIVE) {
      this.status = ReaderStatus.DEGRADED;
    } else if (!weak && status == ReaderStatus.DEGRADED) {
      this.status = ReaderStatus.ACTIVE;
    }
  }

  public void reconfigure(ReaderConfiguration configuration) {
    this.configuration = configuration;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public DeviceType getDeviceType() {
    return deviceType;
  }

  public String getSpaceId() {
    return spaceId;
  }

  public ReaderStatus getStatus() {
    return status;
  }

  public ReaderConfiguration getConfiguration() {
    return configuration;
  }

  public LocalDateTime getRegisteredAt() {
    return registeredAt;
  }

  public LocalDateTime getLastSeenAt() {
    return lastSeenAt;
  }
}
