// TarjePAFI · MQTT smoke test for Wokwi.
// Connects to Wi-Fi (Wokwi-GUEST), syncs UTC time over NTP, connects to the broker over plain MQTT (1883)
// as the restricted reader user and publishes a device contract v1 Heartbeat every HEARTBEAT_INTERVAL_MS.
// It also subscribes to the reader's display topic to prove the receive path is allowed.
// Not the reader firmware: that one lives in firmware/reader-esp32/ (phase 6).

#include <WiFi.h>
#include <MQTT.h>   // "MQTT" by Joel Gaehwiler (256dpi/arduino-mqtt): supports QoS 1 publish
#include <time.h>
#include "config.h"

static const unsigned long HEARTBEAT_INTERVAL_MS = 15000;
static const unsigned long RECONNECT_INTERVAL_MS = 5000;

WiFiClient net;
MQTTClient mqtt(512);

char clientId[40];
char heartbeatTopic[96];
char displayTopic[96];
unsigned long lastHeartbeat = 0;
unsigned long lastConnectAttempt = 0;

static bool utcNow(char *out, size_t size) {
  struct tm utc;
  time_t now = time(nullptr);
  if (now < 1700000000) {  // NTP not synced yet
    return false;
  }
  gmtime_r(&now, &utc);
  strftime(out, size, "%Y-%m-%dT%H:%M:%SZ", &utc);
  return true;
}

static void onMessage(String &topic, String &payload) {
  Serial.printf("[mqtt] <- %s %s\n", topic.c_str(), payload.c_str());
}

static void connectWifi() {
  Serial.printf("[wifi] connecting to %s", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD, WIFI_CHANNEL);
  while (WiFi.status() != WL_CONNECTED) {
    delay(200);
    Serial.print('.');
  }
  Serial.printf("\n[wifi] connected, ip %s\n", WiFi.localIP().toString().c_str());
}

static void syncTime() {
  configTime(0, 0, "pool.ntp.org", "time.google.com");
  Serial.print("[ntp] waiting for time");
  char stamp[25];
  while (!utcNow(stamp, sizeof stamp)) {
    delay(250);
    Serial.print('.');
  }
  Serial.printf("\n[ntp] utc %s\n", stamp);
}

static void connectMqtt() {
  Serial.printf("[mqtt] connecting to %s:%d as %s (client %s)\n", MQTT_HOST, MQTT_PORT, MQTT_USER, clientId);
  if (!mqtt.connect(clientId, MQTT_USER, MQTT_PASSWORD)) {
    // lastError: -3 = network failed (DNS/TCP/firewall), -9 = no CONNACK within the command timeout,
    // -10 = connection denied (see returnCode).
    // returnCode: 4 = bad user name or password, 5 = not authorized, 6 = unknown (no CONNACK read).
    Serial.printf("[mqtt] connect FAILED, lastError=%d returnCode=%d\n", mqtt.lastError(), mqtt.returnCode());
    return;
  }
  Serial.println("[mqtt] connected");
  if (mqtt.subscribe(displayTopic, 1)) {
    Serial.printf("[mqtt] subscribed to %s\n", displayTopic);
  } else {
    Serial.printf("[mqtt] subscribe FAILED, lastError=%d\n", mqtt.lastError());
  }
}

static void publishHeartbeat() {
  char sentAt[25];
  if (!utcNow(sentAt, sizeof sentAt)) {
    Serial.println("[hb] skipped: no time yet");
    return;
  }
  char payload[160];
  snprintf(payload, sizeof payload, "{\"v\":1,\"type\":\"Heartbeat\",\"readerId\":\"%s\",\"sentAt\":\"%s\"}",
           READER_ID, sentAt);
  bool ok = mqtt.publish(heartbeatTopic, payload, false, 1);  // QoS 1: waits for PUBACK
  Serial.printf("[hb] %s %s -> %s\n", ok ? "OK" : "FAILED", heartbeatTopic, payload);
  if (!ok) {
    Serial.printf("[hb] lastError=%d\n", mqtt.lastError());
  }
}

void setup() {
  Serial.begin(115200);
  delay(100);

  // Wokwi gives every simulated ESP32 the same MAC, so add randomness: RabbitMQ drops an older session
  // that reuses a client id.
  snprintf(clientId, sizeof clientId, "tarjepafi-smoke-%08lx", (unsigned long) esp_random());
  snprintf(heartbeatTopic, sizeof heartbeatTopic, "tarjepafi/readers/%s/heartbeat", READER_ID);
  snprintf(displayTopic, sizeof displayTopic, "tarjepafi/readers/%s/display", READER_ID);

  connectWifi();
  syncTime();

  mqtt.begin(MQTT_HOST, MQTT_PORT, net);
  mqtt.setKeepAlive(30);
  // The library waits only 1000 ms for CONNACK/PUBACK/SUBACK by default. The ACI broker answered a CONNACK
  // after about 3 s in testing, which the default turns into lastError -9.
  mqtt.setTimeout(10000);
  mqtt.onMessage(onMessage);
}

void loop() {
  mqtt.loop();
  unsigned long now = millis();

  if (!mqtt.connected()) {
    if (lastConnectAttempt == 0 || now - lastConnectAttempt >= RECONNECT_INTERVAL_MS) {
      lastConnectAttempt = now;
      connectMqtt();
      if (mqtt.connected()) {
        publishHeartbeat();
        lastHeartbeat = now;
      }
    }
    return;
  }

  if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
    lastHeartbeat = now;
    publishHeartbeat();
  }
}
