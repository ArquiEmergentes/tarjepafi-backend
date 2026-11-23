// Wi-Fi, NTP and the MQTT session with the broker, reconnecting with exponential backoff.
#pragma once

#include <Arduino.h>
#include <MQTT.h>
#include <WiFi.h>

struct NetworkConfig {
  const char *wifiSsid;
  const char *wifiPassword;
  int32_t wifiChannel;  // 0 scans all channels; Wokwi-GUEST is on 6
  const char *mqttHost;
  uint16_t mqttPort;
  const char *mqttUser;
  const char *mqttPassword;
  const char *readerId;
};

class ReaderNetwork {
 public:
  /** Called from inside loop() for every message on a subscribed topic. Must not publish. */
  using MessageHandler = void (*)(const char *topic, const char *payload, size_t length);

  explicit ReaderNetwork(const NetworkConfig &config);

  void begin(MessageHandler handler);

  /**
   * Keeps Wi-Fi and MQTT up. Connecting to the broker blocks for up to MQTT_COMMAND_TIMEOUT_MS: the MQTT library has
   * no asynchronous connect.
   */
  void loop(unsigned long now);

  /** The offline switch: drops the MQTT session and keeps it down until released. */
  void setForcedOffline(bool offline);
  bool forcedOffline() const { return forcedOffline_; }

  /** True while there is an MQTT session (and the switch is not forcing offline). */
  bool online();

  /** True once after each new MQTT session, so the caller can announce itself and flush the buffer. */
  bool consumeReconnected();

  /** Publishes to tarjepafi/readers/{readerId}/{kind}. With QoS 1 it waits for the broker's PUBACK. */
  bool publish(const char *kind, const char *payload, int qos);

  /** Wi-Fi signal in dBm, clamped to the contract's -150..0 range. */
  int signalStrength() const;

  /** Short ASCII status for the idle screen (max 20 characters). */
  const char *statusText();

 private:
  static void onMessage(String &topic, String &payload);
  void maintainWifi(unsigned long now);
  void maintainMqtt(unsigned long now);
  bool connectMqtt();

  static ReaderNetwork *instance_;

  NetworkConfig config_;
  WiFiClient net_;
  MQTTClient mqtt_;
  MessageHandler handler_ = nullptr;
  char clientId_[48];
  char displayTopic_[96];
  bool forcedOffline_ = false;
  bool timeConfigured_ = false;
  bool reconnected_ = false;
  unsigned long lastWifiAttemptAt_ = 0;
  unsigned long nextMqttAttemptAt_ = 0;
  unsigned long backoffMs_;
};
