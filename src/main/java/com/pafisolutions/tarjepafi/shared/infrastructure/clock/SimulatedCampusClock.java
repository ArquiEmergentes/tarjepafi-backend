package com.pafisolutions.tarjepafi.shared.infrastructure.clock;

import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Demo clock that mirrors the web app's {@code SimulatedClock}: it starts at a configured time and
 * is moved explicitly. By default it stands still between moves (like the web app); with
 * {@code ticking} it advances with real time from the last move.
 *
 * <p>State is in memory, so it only suits a single instance. That is acceptable for demos; production
 * uses {@link SystemCampusClock}.
 */
public class SimulatedCampusClock implements CampusClock {

  private final Clock realClock;
  private final boolean ticking;
  private LocalDateTime base;
  private Instant movedAt;

  public SimulatedCampusClock(Clock realClock, LocalDateTime initial, boolean ticking) {
    this.realClock = realClock;
    this.ticking = ticking;
    this.base = initial.truncatedTo(ChronoUnit.SECONDS);
    this.movedAt = realClock.instant();
  }

  @Override
  public synchronized LocalDateTime now() {
    if (!ticking) {
      return base;
    }
    return base.plus(Duration.between(movedAt, realClock.instant())).truncatedTo(ChronoUnit.SECONDS);
  }

  @Override
  public synchronized LocalDateTime fromDeviceInstant(Instant deviceInstant) {
    Duration age = Duration.between(deviceInstant, realClock.instant());
    // A device clock slightly ahead of the server must not produce a tap in the simulated future.
    if (age.isNegative()) {
      age = Duration.ZERO;
    }
    return now().minus(age).truncatedTo(ChronoUnit.SECONDS);
  }

  @Override
  public boolean isSimulated() {
    return true;
  }

  @Override
  public synchronized void moveTo(LocalDateTime time) {
    this.base = time.truncatedTo(ChronoUnit.SECONDS);
    this.movedAt = realClock.instant();
  }
}
