package com.pafisolutions.tarjepafi.shared.infrastructure.clock;

import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Real time in the campus time zone. Used in production. */
public class SystemCampusClock implements CampusClock {

  private final Clock clock;
  private final ZoneId zone;

  public SystemCampusClock(Clock clock, ZoneId zone) {
    this.clock = clock;
    this.zone = zone;
  }

  @Override
  public LocalDateTime now() {
    return fromDeviceInstant(clock.instant());
  }

  @Override
  public LocalDateTime fromDeviceInstant(Instant deviceInstant) {
    return LocalDateTime.ofInstant(deviceInstant, zone).truncatedTo(ChronoUnit.SECONDS);
  }

  @Override
  public boolean isSimulated() {
    return false;
  }

  @Override
  public void moveTo(LocalDateTime time) {
    throw new UnsupportedOperationException("The campus clock follows real time");
  }
}
