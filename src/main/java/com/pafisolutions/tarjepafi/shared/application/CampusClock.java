package com.pafisolutions.tarjepafi.shared.application;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Source of "now" for every time-dependent rule (attendance windows, session closing, heartbeats,
 * enrollment deadline). Rules must never call {@code LocalDateTime.now()} directly.
 *
 * <p>All values are campus local time without offset, truncated to seconds, which is the format the
 * web app exchanges.
 */
public interface CampusClock {

  /** Current campus time. */
  LocalDateTime now();

  /**
   * Converts a timestamp taken by a device (UTC instant) to campus time. With a simulated clock the
   * value is shifted so that a tap taken "just now" lands on the simulated now, while a tap that was
   * buffered offline keeps its distance to it.
   */
  LocalDateTime fromDeviceInstant(Instant deviceInstant);

  /** True when the clock can be moved through {@link #moveTo(LocalDateTime)}. */
  boolean isSimulated();

  /**
   * Moves a simulated clock.
   *
   * @throws UnsupportedOperationException when the clock follows real time
   */
  void moveTo(LocalDateTime time);
}
