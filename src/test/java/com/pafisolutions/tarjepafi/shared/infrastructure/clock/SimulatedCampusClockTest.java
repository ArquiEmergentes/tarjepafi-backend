package com.pafisolutions.tarjepafi.shared.infrastructure.clock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SimulatedCampusClockTest {

  private static final LocalDateTime DEMO_START = LocalDateTime.parse("2026-10-07T09:50:00");
  private static final Instant REAL_NOW = Instant.parse("2026-11-15T20:00:00Z");

  /** Real clock that the test advances by hand. */
  private static final class MutableClock extends Clock {
    private Instant instant = REAL_NOW;

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public Instant instant() {
      return instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  @Test
  void now_whenNotTicking_staysAtTheLastMove() {
    MutableClock real = new MutableClock();
    SimulatedCampusClock clock = new SimulatedCampusClock(real, DEMO_START, false);

    real.advance(Duration.ofMinutes(7));

    assertThat(clock.now()).isEqualTo(DEMO_START);
  }

  @Test
  void now_whenTicking_advancesWithRealTime() {
    MutableClock real = new MutableClock();
    SimulatedCampusClock clock = new SimulatedCampusClock(real, DEMO_START, true);

    real.advance(Duration.ofMinutes(7));

    assertThat(clock.now()).isEqualTo(DEMO_START.plusMinutes(7));
  }

  @Test
  void moveTo_setsTheNewTime() {
    SimulatedCampusClock clock = new SimulatedCampusClock(new MutableClock(), DEMO_START, false);

    clock.moveTo(LocalDateTime.parse("2026-10-07T10:20:00"));

    assertThat(clock.now()).isEqualTo(LocalDateTime.parse("2026-10-07T10:20:00"));
  }

  @Test
  void fromDeviceInstant_whenTapIsLive_returnsSimulatedNow() {
    SimulatedCampusClock clock = new SimulatedCampusClock(new MutableClock(), DEMO_START, false);

    assertThat(clock.fromDeviceInstant(REAL_NOW)).isEqualTo(DEMO_START);
  }

  @Test
  void fromDeviceInstant_whenTapWasBufferedOffline_keepsItsAge() {
    SimulatedCampusClock clock = new SimulatedCampusClock(new MutableClock(), DEMO_START, false);

    LocalDateTime tappedAt = clock.fromDeviceInstant(REAL_NOW.minus(Duration.ofMinutes(3)));

    assertThat(tappedAt).isEqualTo(DEMO_START.minusMinutes(3));
  }

  @Test
  void fromDeviceInstant_whenDeviceClockIsAhead_doesNotReturnAFutureTime() {
    SimulatedCampusClock clock = new SimulatedCampusClock(new MutableClock(), DEMO_START, false);

    assertThat(clock.fromDeviceInstant(REAL_NOW.plusSeconds(30))).isEqualTo(DEMO_START);
  }

  @Test
  void systemClock_convertsDeviceInstantToCampusZone() {
    SystemCampusClock clock = new SystemCampusClock(new MutableClock(), ZoneId.of("America/Lima"));

    assertThat(clock.fromDeviceInstant(Instant.parse("2026-11-15T15:04:05Z")))
        .isEqualTo(LocalDateTime.parse("2026-11-15T10:04:05"));
  }
}
