package com.pafisolutions.tarjepafi.iotmonitoring.domain.model;

/**
 * What came of a tap. A tap starts PENDING until the context that resolves it answers; IoT itself
 * rejects taps from readers that cannot process them and from cards that are unknown or inactive.
 */
public enum TapOutcome {
  PENDING,
  ATTENDANCE,
  ACCESS,
  WORK_SHIFT,
  REJECTED
}
