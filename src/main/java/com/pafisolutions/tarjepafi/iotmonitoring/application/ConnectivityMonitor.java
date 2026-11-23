package com.pafisolutions.tarjepafi.iotmonitoring.application;

import com.pafisolutions.tarjepafi.iotmonitoring.domain.model.Reader;
import com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.persistence.ReaderRepository;
import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TS11: every 30 seconds (campus clock) a reader in service that stayed silent for more than three
 * heartbeats goes OFFLINE and gets a DEVICE_OFFLINE incident. With the simulated clock this only happens
 * when the clock is moved, which is what the demo does.
 */
@Component
public class ConnectivityMonitor {

  private static final Logger log = LoggerFactory.getLogger(ConnectivityMonitor.class);

  private final ReaderRepository readers;
  private final DeviceEventHandler handler;
  private final CampusClock clock;

  public ConnectivityMonitor(ReaderRepository readers, DeviceEventHandler handler, CampusClock clock) {
    this.readers = readers;
    this.handler = handler;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${tarjepafi.iot.connectivity-check-ms:30000}")
  void evaluate() {
    evaluate(clock.now());
  }

  /** Marks the silent readers OFFLINE as of {@code now}; returns how many changed. */
  public int evaluate(LocalDateTime now) {
    List<Reader> silent = readers.findAll().stream().filter(reader -> reader.hasLostConnection(now)).toList();
    int changed = 0;
    for (Reader reader : silent) {
      try {
        if (handler.markOffline(reader.getId(), now)) {
          changed++;
        }
      } catch (RuntimeException exception) {
        log.warn("Reader {} could not be marked offline", reader.getId(), exception);
      }
    }
    if (changed > 0) {
      log.info("Connectivity check: {} readers went offline", changed);
    }
    return changed;
  }
}
