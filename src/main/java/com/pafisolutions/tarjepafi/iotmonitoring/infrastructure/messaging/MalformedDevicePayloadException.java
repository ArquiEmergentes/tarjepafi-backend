package com.pafisolutions.tarjepafi.iotmonitoring.infrastructure.messaging;

/** A device message that does not follow the contract. It is retried and then parked in the DLQ. */
public class MalformedDevicePayloadException extends RuntimeException {

  public MalformedDevicePayloadException(String message) {
    super(message);
  }

  public MalformedDevicePayloadException(String message, Throwable cause) {
    super(message, cause);
  }
}
