package com.pafisolutions.tarjepafi.iotmonitoring.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** How a reader behaves. Intervals are in seconds. */
@Embeddable
public class ReaderConfiguration {

  private static final Pattern FIRMWARE = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

  @Enumerated(EnumType.STRING)
  @Column(name = "reader_mode", nullable = false)
  private ReaderMode readerMode;

  @Column(name = "communication_interval", nullable = false)
  private int communicationInterval;

  @Column(name = "heartbeat_interval", nullable = false)
  private int heartbeatInterval;

  @Column(name = "firmware_version", nullable = false)
  private String firmwareVersion;

  protected ReaderConfiguration() {}

  public ReaderConfiguration(
      ReaderMode readerMode,
      int communicationInterval,
      int heartbeatInterval,
      String firmwareVersion) {
    this.readerMode = readerMode;
    this.communicationInterval = communicationInterval;
    this.heartbeatInterval = heartbeatInterval;
    this.firmwareVersion = firmwareVersion == null ? null : firmwareVersion.trim();
  }

  /**
   * Rule 6.C: the intervals are greater than 0, the heartbeat is not shorter than the communication
   * interval and the firmware looks like {@code x.y.z}.
   *
   * @return the codes of every broken rule; empty when the configuration is valid
   */
  public static List<String> validate(
      ReaderMode mode, Integer communicationInterval, Integer heartbeatInterval, String firmware) {
    List<String> errors = new ArrayList<>();
    if (mode == null) {
      errors.add("MODE_REQUIRED");
    }
    boolean communicationOk = communicationInterval != null && communicationInterval > 0;
    boolean heartbeatOk = heartbeatInterval != null && heartbeatInterval > 0;
    if (!communicationOk) {
      errors.add("COMMUNICATION_INTERVAL_INVALID");
    }
    if (!heartbeatOk) {
      errors.add("HEARTBEAT_INTERVAL_INVALID");
    }
    if (communicationOk && heartbeatOk && heartbeatInterval < communicationInterval) {
      errors.add("HEARTBEAT_LOWER_THAN_COMMUNICATION");
    }
    if (firmware == null || !FIRMWARE.matcher(firmware.trim()).matches()) {
      errors.add("FIRMWARE_FORMAT");
    }
    return errors;
  }

  public ReaderMode getReaderMode() {
    return readerMode;
  }

  public int getCommunicationInterval() {
    return communicationInterval;
  }

  public int getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public String getFirmwareVersion() {
    return firmwareVersion;
  }
}
