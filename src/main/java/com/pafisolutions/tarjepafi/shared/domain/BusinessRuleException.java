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
    UNPROCESSABLE
  }

  private final Kind kind;
  private final String code;

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
}
