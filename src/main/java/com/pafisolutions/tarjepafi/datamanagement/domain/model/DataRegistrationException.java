package com.pafisolutions.tarjepafi.datamanagement.domain.model;

/** A fact that cannot be recorded: an incoherent source and type, or a missing field (the plan's DataRegistrationError). */
public class DataRegistrationException extends RuntimeException {

  public DataRegistrationException(String message) {
    super(message);
  }
}
