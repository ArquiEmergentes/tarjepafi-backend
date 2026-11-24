package com.pafisolutions.tarjepafi.spacefacility.application;

import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * TS23: once a minute (real time) the reservations are swept with the campus clock. With the simulated clock this
 * only changes anything after the clock is moved.
 */
@Component
public class ReservationToleranceScheduler {

  private final ReservationService reservations;
  private final CampusClock clock;

  public ReservationToleranceScheduler(ReservationService reservations, CampusClock clock) {
    this.reservations = reservations;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${tarjepafi.reservations.sweep-ms:60000}")
  public void run() {
    reservations.sweep(clock.now());
  }
}
