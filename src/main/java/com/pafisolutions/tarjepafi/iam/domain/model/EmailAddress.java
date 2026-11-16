package com.pafisolutions.tarjepafi.iam.domain.model;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** An institutional e-mail address. Only the university domain is accepted. */
public record EmailAddress(String value) {

  public static final String DOMAIN = "@upc.edu.pe";

  private static final Pattern LOCAL_PART = Pattern.compile("[a-z0-9]+([._-][a-z0-9]+)*");

  public EmailAddress {
    if (!isValid(value)) {
      throw new IllegalArgumentException("Not an institutional e-mail address");
    }
  }

  /** Trims and lower-cases the input; empty when it is not an institutional address. */
  public static Optional<EmailAddress> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String normalised = raw.trim().toLowerCase(Locale.ROOT);
    return isValid(normalised) ? Optional.of(new EmailAddress(normalised)) : Optional.empty();
  }

  private static boolean isValid(String value) {
    if (value == null || !value.endsWith(DOMAIN) || value.length() > 254) {
      return false;
    }
    return LOCAL_PART.matcher(value.substring(0, value.length() - DOMAIN.length())).matches();
  }
}
