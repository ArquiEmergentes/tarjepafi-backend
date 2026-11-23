package com.pafisolutions.tarjepafi.credentials.domain.model;

import static com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatus.ACTIVE;
import static com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatus.BLOCKED;
import static com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatus.DEACTIVATED;
import static com.pafisolutions.tarjepafi.credentials.domain.model.CredentialStatus.LOST;

import java.util.Set;

/** The card state machine (rule 6.B): which statuses an action starts from and where it ends. */
public enum CredentialAction {
  BLOCK(Set.of(ACTIVE), BLOCKED),
  REPORT_LOST(Set.of(ACTIVE), LOST),
  DEACTIVATE(Set.of(ACTIVE, BLOCKED, LOST), DEACTIVATED),
  REACTIVATE(Set.of(BLOCKED, LOST, DEACTIVATED), ACTIVE);

  private final Set<CredentialStatus> from;
  private final CredentialStatus to;

  CredentialAction(Set<CredentialStatus> from, CredentialStatus to) {
    this.from = from;
    this.to = to;
  }

  public boolean allowedFrom(CredentialStatus status) {
    return from.contains(status);
  }

  public CredentialStatus target() {
    return to;
  }
}
