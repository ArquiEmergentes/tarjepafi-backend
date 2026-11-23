package com.pafisolutions.tarjepafi.shared.application;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The facts of an {@code iot.card-tap} event that the contexts resolving a tap need. IoT Monitoring has
 * already checked the reader and the card, so only ids travel (port of {@code TapContext}).
 *
 * @param holderRole STUDENT, TEACHER or STAFF
 */
public record TapContext(
    String tapEventId,
    String readerId,
    String spaceId,
    String cardUid,
    String credentialId,
    String cardHolderId,
    String holderRole,
    LocalDateTime at) {

  /** Reads the {@code data} of an {@code iot.card-tap} event. */
  public static TapContext from(Map<String, Object> data) {
    return new TapContext(
        text(data, "tapEventId"),
        text(data, "readerId"),
        text(data, "spaceId"),
        text(data, "cardUid"),
        text(data, "credentialId"),
        text(data, "cardHolderId"),
        text(data, "holderRole"),
        LocalDateTime.parse(text(data, "tappedAt")));
  }

  private static String text(Map<String, Object> data, String key) {
    Object value = data.get(key);
    if (value == null) {
      throw new IllegalArgumentException("The card tap event misses " + key);
    }
    return value.toString();
  }
}
