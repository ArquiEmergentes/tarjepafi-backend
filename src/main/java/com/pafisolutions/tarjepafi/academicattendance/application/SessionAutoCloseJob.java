package com.pafisolutions.tarjepafi.academicattendance.application;

import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TS12: every 30 seconds (campus clock) the sessions that ended more than 15 minutes ago are closed. With
 * the simulated clock this only happens when the clock is moved; the next tap or teacher read also closes them.
 */
@Component
@ConditionalOnProperty(
    name = "tarjepafi.attendance.auto-close.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SessionAutoCloseJob {

  private final SessionClosingService closing;
  private final CampusClock clock;

  public SessionAutoCloseJob(SessionClosingService closing, CampusClock clock) {
    this.closing = closing;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${tarjepafi.attendance.auto-close-ms:30000}")
  public void run() {
    closing.closeDue(clock.now());
  }
}
