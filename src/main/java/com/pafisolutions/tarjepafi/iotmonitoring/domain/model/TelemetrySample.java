package com.pafisolutions.tarjepafi.iotmonitoring.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One telemetry message of a reader. {@code signalStrength} is in dBm: closer to 0 is better. */
@Entity
@Table(name = "telemetry")
public class TelemetrySample {

  /** Below this level the signal is weak. */
  public static final int WEAK_SIGNAL_DBM = -75;

  @Id private String id;

  @Column(name = "reader_id", nullable = false)
  private String readerId;

  @Column(name = "captured_at", nullable = false)
  private LocalDateTime capturedAt;

  @Column(name = "signal_strength", nullable = false)
  private int signalStrength;

  @Column(nullable = false)
  private long uptime;

  @Column(name = "firmware_version", nullable = false)
  private String firmwareVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReaderStatus status;

  protected TelemetrySample() {}

  public TelemetrySample(
      String id,
      String readerId,
      LocalDateTime capturedAt,
      int signalStrength,
      long uptime,
      String firmwareVersion,
      ReaderStatus status) {
    this.id = id;
    this.readerId = readerId;
    this.capturedAt = capturedAt;
    this.signalStrength = signalStrength;
    this.uptime = uptime;
    this.firmwareVersion = firmwareVersion;
    this.status = status;
  }

  public static boolean isWeak(int signalStrength) {
    return signalStrength < WEAK_SIGNAL_DBM;
  }

  public String getId() {
    return id;
  }

  public String getReaderId() {
    return readerId;
  }

  public LocalDateTime getCapturedAt() {
    return capturedAt;
  }

  public int getSignalStrength() {
    return signalStrength;
  }

  public long getUptime() {
    return uptime;
  }

  public String getFirmwareVersion() {
    return firmwareVersion;
  }

  public ReaderStatus getStatus() {
    return status;
  }
}
