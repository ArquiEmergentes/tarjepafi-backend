package com.pafisolutions.tarjepafi.shared.domain;

/**
 * A request that is well formed but breaks a business rule. The {@code code} is stable and is what
 * the web app maps to a translated message; the message is for logs and API consumers.
 */
public class BusinessRuleException extends RuntimeException {

  /** How the REST layer should answer. */
  public enum Kind {
    INVALID,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    UNPROCESSABLE,
    TOO_MANY_REQUESTS
  }

  private final Kind kind;
  private final String code;
  private final java.util.Map<String, Object> properties = new java.util.LinkedHashMap<>();

  public BusinessRuleException(Kind kind, String code, String message) {
    super(message);
    this.kind = kind;
    this.code = code;
  }

  public Kind kind() {
    return kind;
  }

  public String code() {
    return code;
  }

  /** Adds a property to the problem document, for example the list of broken rules. */
  public BusinessRuleException with(String name, Object value) {
    properties.put(name, value);
    return this;
  }

  public java.util.Map<String, Object> properties() {
    return java.util.Collections.unmodifiableMap(properties);
  }
}
