package com.pafisolutions.tarjepafi.shared.infrastructure.clock;

import com.pafisolutions.tarjepafi.shared.application.CampusClock;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {

  /**
   * @param zone campus time zone
   * @param mode {@code system} (real time) or {@code simulated} (demo)
   * @param initial start of the simulated clock
   * @param ticking whether the simulated clock advances with real time between moves
   */
  @ConfigurationProperties("tarjepafi.clock")
  public record ClockProperties(ZoneId zone, Mode mode, LocalDateTime initial, boolean ticking) {

    public enum Mode {
      SYSTEM,
      SIMULATED
    }

    public ClockProperties {
      zone = zone == null ? ZoneId.of("America/Lima") : zone;
      mode = mode == null ? Mode.SYSTEM : mode;
    }
  }

  @Bean
  CampusClock campusClock(ClockProperties properties) {
    Clock realClock = Clock.systemUTC();
    if (properties.mode() == ClockProperties.Mode.SIMULATED) {
      LocalDateTime initial =
          properties.initial() != null ? properties.initial() : LocalDateTime.now(properties.zone());
      return new SimulatedCampusClock(realClock, initial, properties.ticking());
    }
    return new SystemCampusClock(realClock, properties.zone());
  }
}
