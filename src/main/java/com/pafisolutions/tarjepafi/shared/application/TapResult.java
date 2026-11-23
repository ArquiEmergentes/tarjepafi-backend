package com.pafisolutions.tarjepafi.shared.application;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a context resolved a tap: the outcome stored on the tap event and the message the reader shows.
 *
 * @param outcome ATTENDANCE, ACCESS, WORK_SHIFT or REJECTED
 * @param tone {@code success}, {@code info} or {@code error}
 * @param messageKey a {@code tap.*} key of the reader messages
 */
public record TapResult(String outcome, String tone, String messageKey, Map<String, Object> params) {

  public static TapResult rejected(String messageKey) {
    return new TapResult("REJECTED", "error", messageKey, Map.of());
  }

  public static TapResult attendance(String messageKey, Map<String, Object> params, String tone) {
    return new TapResult("ATTENDANCE", tone, messageKey, params);
  }

  public static TapResult access(String tone, String messageKey) {
    return new TapResult("ACCESS", tone, messageKey, Map.of());
  }

  public static TapResult workShift(String tone, String messageKey, Map<String, Object> params) {
    return new TapResult("WORK_SHIFT", tone, messageKey, params);
  }

  /** The event a context publishes to answer the tap, with routing key {@code <context>.tap-resolved}. */
  public IntegrationEvent toEvent(String context, TapContext tap) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("tapEventId", tap.tapEventId());
    data.put("readerId", tap.readerId());
    data.put("outcome", outcome);
    data.put("tone", tone);
    data.put("messageKey", messageKey);
    data.put("params", params);
    return new IntegrationEvent(context + ".tap-resolved", data);
  }
}
