package com.pafisolutions.tarjepafi.credentials.application;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 * What the Credentials context offers to the other contexts (TS29): look a card up by its UID, read a
 * holder, evaluate the permission rule of US14 and record that a card was used. Nobody else writes to the
 * credentials tables.
 */
public interface CardHolderDirectoryPort {

  /** A holder as other contexts see it; {@code role} is STUDENT, TEACHER or STAFF. */
  record HolderInfo(
      String id,
      String code,
      String fullName,
      String email,
      String role,
      String program,
      String enrollmentStatus) {}

  /** A card and its holder; {@code status} is ACTIVE, BLOCKED, LOST or DEACTIVATED. */
  record CardLookup(String credentialId, String cardUid, String status, HolderInfo holder) {
    public boolean active() {
      return "ACTIVE".equals(status);
    }
  }

  /** {@code basis} is EXCEPTION (the card has its own rule for the space) or ROLE. */
  record AccessDecision(boolean allowed, String basis) {}

  Optional<HolderInfo> findHolder(String cardHolderId);

  /** The card by UID, ignoring case; empty when it is not registered. */
  Optional<CardLookup> findByCardUid(String cardUid);

  /** The active card of the holder, or else the most recently issued one. */
  Optional<CardLookup> findCardOf(String cardHolderId);

  /**
   * Rule 6.B (US14): the exception of the card for the space, else the roles the space admits.
   *
   * @param allowedRoles roles of the space, as STUDENT, TEACHER or STAFF
   */
  AccessDecision resolveAccess(String credentialId, String spaceId, Collection<String> allowedRoles);

  /** Step 5 of the read flow: every successful read updates {@code lastUsedAt}. */
  void markUsed(String credentialId, LocalDateTime at);
}
