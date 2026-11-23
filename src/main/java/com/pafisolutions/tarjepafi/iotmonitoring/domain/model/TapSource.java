package com.pafisolutions.tarjepafi.iotmonitoring.domain.model;

public enum TapSource {
  /** A physical or Wokwi reader publishing over MQTT. */
  READER,
  /** Injected by an administrator through the API. */
  SIMULATOR
}
