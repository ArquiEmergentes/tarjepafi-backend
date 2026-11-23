#include "ReaderNetwork.h"

#include <time.h>

#include "ClockUtil.h"
#include "ReaderSettings.h"

ReaderNetwork *ReaderNetwork::instance_ = nullptr;

ReaderNetwork::ReaderNetwork(const NetworkConfig &config)
    : config_(config), mqtt_(1024), backoffMs_(settings::MQTT_BACKOFF_MIN_MS) {}

void ReaderNetwork::begin(MessageHandler handler) {
  instance_ = this;
  handler_ = handler;
  // Wokwi gives every simulated ESP32 the same MAC, and RabbitMQ closes an older session that reuses a client id, so
  // the id gets a random suffix: two open copies of the public project must not kick each other off in a loop.
  snprintf(clientId_, sizeof clientId_, "tarjepafi-%s-%08lx", config_.readerId, static_cast<unsigned long>(esp_random()));
  snprintf(displayTopic_, sizeof displayTopic_, "tarjepafi/readers/%s/display", config_.readerId);

  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(config_.wifiSsid, config_.wifiPassword, config_.wifiChannel);
  lastWifiAttemptAt_ = millis();
  Serial.printf("[wifi] connecting to %s\n", config_.wifiSsid);

  mqtt_.begin(config_.mqttHost, config_.mqttPort, net_);
  mqtt_.setKeepAlive(settings::MQTT_KEEP_ALIVE_S);
  mqtt_.setTimeout(settings::MQTT_COMMAND_TIMEOUT_MS);
  mqtt_.onMessage(ReaderNetwork::onMessage);
}

void ReaderNetwork::loop(unsigned long now) {
  maintainWifi(now);
  maintainMqtt(now);
}

void ReaderNetwork::maintainWifi(unsigned long now) {
  if (WiFi.status() == WL_CONNECTED) {
    if (!timeConfigured_) {
      Serial.printf("[wifi] connected, ip %s\n", WiFi.localIP().toString().c_str());
      configTime(0, 0, "pool.ntp.org", "time.google.com");  // SNTP keeps the clock synced from here on
      timeConfigured_ = true;
    }
    return;
  }
  if (now - lastWifiAttemptAt_ >= settings::WIFI_RETRY_INTERVAL_MS) {
    lastWifiAttemptAt_ = now;
    Serial.println("[wifi] not connected, retrying");
    WiFi.disconnect();
    WiFi.begin(config_.wifiSsid, config_.wifiPassword, config_.wifiChannel);
  }
}

void ReaderNetwork::maintainMqtt(unsigned long now) {
  if (mqtt_.connected()) {
    mqtt_.loop();
    if (mqtt_.connected()) {
      return;
    }
    Serial.printf("[mqtt] connection lost, lastError=%d\n", mqtt_.lastError());
    nextMqttAttemptAt_ = now + backoffMs_;
    return;
  }
  if (forcedOffline_ || WiFi.status() != WL_CONNECTED) {
    return;
  }
  if (static_cast<long>(now - nextMqttAttemptAt_) < 0) {
    return;
  }
  if (connectMqtt()) {
    backoffMs_ = settings::MQTT_BACKOFF_MIN_MS;
    reconnected_ = true;
  } else {
    nextMqttAttemptAt_ = millis() + backoffMs_;
    Serial.printf("[mqtt] next attempt in %lu ms\n", backoffMs_);
    backoffMs_ = backoffMs_ * 2 > settings::MQTT_BACKOFF_MAX_MS ? settings::MQTT_BACKOFF_MAX_MS : backoffMs_ * 2;
  }
}

bool ReaderNetwork::connectMqtt() {
  Serial.printf("[mqtt] connecting to %s:%u as %s (client %s)\n", config_.mqttHost, config_.mqttPort,
                config_.mqttUser, clientId_);
  if (!mqtt_.connect(clientId_, config_.mqttUser, config_.mqttPassword)) {
    // lastError -3: network, -9: no CONNACK in time, -10: refused (returnCode 4 bad credentials, 5 not authorised).
    Serial.printf("[mqtt] connect failed, lastError=%d returnCode=%d\n", mqtt_.lastError(), mqtt_.returnCode());
    return false;
  }
  if (!mqtt_.subscribe(displayTopic_, 1)) {
    Serial.printf("[mqtt] subscribe to %s failed, lastError=%d\n", displayTopic_, mqtt_.lastError());
    mqtt_.disconnect();
    return false;
  }
  Serial.printf("[mqtt] connected, subscribed to %s\n", displayTopic_);
  return true;
}

void ReaderNetwork::setForcedOffline(bool offline) {
  if (offline == forcedOffline_) {
    return;
  }
  forcedOffline_ = offline;
  if (offline) {
    Serial.println("[net] offline switch ON: MQTT session closed, taps are buffered");
    if (mqtt_.connected()) {
      mqtt_.disconnect();
    }
  } else {
    Serial.println("[net] offline switch OFF: reconnecting");
    backoffMs_ = settings::MQTT_BACKOFF_MIN_MS;
    nextMqttAttemptAt_ = millis();
  }
}

bool ReaderNetwork::online() {
  return !forcedOffline_ && mqtt_.connected();
}

bool ReaderNetwork::consumeReconnected() {
  bool value = reconnected_;
  reconnected_ = false;
  return value;
}

bool ReaderNetwork::publish(const char *kind, const char *payload, int qos) {
  if (!online()) {
    return false;
  }
  char topic[96];
  snprintf(topic, sizeof topic, "tarjepafi/readers/%s/%s", config_.readerId, kind);
  bool ok = mqtt_.publish(topic, payload, false, qos);
  if (ok) {
    Serial.printf("[mqtt] -> %s %s\n", topic, payload);
  } else {
    Serial.printf("[mqtt] publish to %s failed, lastError=%d\n", topic, mqtt_.lastError());
  }
  return ok;
}

int ReaderNetwork::signalStrength() const {
  int rssi = WiFi.RSSI();
  return constrain(rssi, -150, 0);
}

const char *ReaderNetwork::statusText() {
  if (forcedOffline_) {
    return "Modo sin conexion";
  }
  if (WiFi.status() != WL_CONNECTED) {
    return "Conectando WiFi...";
  }
  if (!clockutil::synced()) {
    return "Sincronizando hora";
  }
  if (!mqtt_.connected()) {
    return "Conectando broker...";
  }
  return "En linea";
}

void ReaderNetwork::onMessage(String &topic, String &payload) {
  if (instance_ != nullptr && instance_->handler_ != nullptr) {
    instance_->handler_(topic.c_str(), payload.c_str(), payload.length());
  }
}
