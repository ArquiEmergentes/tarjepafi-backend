package com.pafisolutions.tarjepafi.iotmonitoring.domain.model;

import static com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus.ACTIVE;
import static com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus.DEGRADED;
import static com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus.INACTIVE;
import static com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus.MAINTENANCE;
import static com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus.OFFLINE;
import static com.pafisolutions.tarjepafi.iotmonitoring.domain.model.ReaderStatus.REGISTERED;

import java.util.Set;

/** Manual transitions of a reader (rule 6.C). */
public enum ReaderAction {
  ACTIVATE(Set.of(REGISTERED, INACTIVE), ACTIVE),
  DEACTIVATE(Set.of(ACTIVE, DEGRADED, OFFLINE), INACTIVE),
  START_MAINTENANCE(Set.of(REGISTERED, ACTIVE, INACTIVE, OFFLINE, DEGRADED), MAINTENANCE),
  END_MAINTENANCE(Set.of(MAINTENANCE), ACTIVE);

  private final Set<ReaderStatus> from;
  private final ReaderStatus to;

  ReaderAction(Set<ReaderStatus> from, ReaderStatus to) {
    this.from = from;
    this.to = to;
  }

  public boolean allowedFrom(ReaderStatus status) {
    return from.contains(status);
  }

  public ReaderStatus target() {
    return to;
  }
}
