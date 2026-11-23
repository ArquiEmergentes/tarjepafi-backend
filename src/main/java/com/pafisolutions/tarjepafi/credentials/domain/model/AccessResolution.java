package com.pafisolutions.tarjepafi.credentials.domain.model;

import java.util.Collection;
import java.util.Optional;

/** Why a card may or may not enter a space: its own exception or the roles the space admits. */
public record AccessResolution(boolean allowed, Basis basis) {

  public enum Basis {
    EXCEPTION,
    ROLE
  }

  /**
   * Rule 6.B (US14): the exception of the card for the space comes first; without one, the roles the
   * space admits decide.
   */
  public static AccessResolution resolve(
      Optional<CredentialPermission> exception,
      Collection<HolderRole> allowedRoles,
      HolderRole holderRole) {
    if (exception.isPresent()) {
      return new AccessResolution(exception.get().allows(), Basis.EXCEPTION);
    }
    return new AccessResolution(holderRole != null && allowedRoles.contains(holderRole), Basis.ROLE);
  }
}
