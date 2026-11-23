#include "EventPublisher.h"

#include <ArduinoJson.h>

#include "ClockUtil.h"
#include "ReaderSettings.h"

static const int CONTRACT_VERSION = 1;
static const int QOS_AT_LEAST_ONCE = 1;

static bool send(ReaderNetwork &network, const char *kind, const JsonDocument &doc) {
  char payload[384];
  size_t length = serializeJson(doc, payload, sizeof payload);
  if (length == 0 || length >= sizeof payload) {
    Serial.printf("[event] %s payload too large\n", kind);
    return false;
  }
  return network.publish(kind, payload, QOS_AT_LEAST_ONCE);
}

EventPublisher::EventPublisher(ReaderNetwork &network, const char *readerId)
    : network_(network), readerId_(readerId) {}

void EventPublisher::newEventId(char out[37]) {
  uint8_t bytes[16];
  for (int i = 0; i < 16; i += 4) {
    uint32_t random = esp_random();
    memcpy(bytes + i, &random, 4);
  }
  bytes[6] = (bytes[6] & 0x0F) | 0x40;  // version 4
  bytes[8] = (bytes[8] & 0x3F) | 0x80;  // RFC 4122 variant
  snprintf(out, 37, "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x", bytes[0], bytes[1],
           bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7], bytes[8], bytes[9], bytes[10], bytes[11],
           bytes[12], bytes[13], bytes[14], bytes[15]);
}

bool EventPublisher::publishTap(const char *eventId, const char *cardUid, uint64_t tappedAtMs, bool buffered) {
  char tappedAt[32];
  clockutil::formatIso(tappedAtMs, tappedAt, sizeof tappedAt);
  JsonDocument doc;
  doc["v"] = CONTRACT_VERSION;
  doc["type"] = "CardTapped";
  doc["eventId"] = eventId;
  doc["readerId"] = readerId_;
  doc["cardUid"] = cardUid;
  doc["tappedAt"] = tappedAt;
  doc["buffered"] = buffered;
  return send(network_, "tap", doc);
}

bool EventPublisher::publishTelemetry(int signalStrength, uint32_t uptimeS, uint32_t bufferedCount,
                                      uint32_t droppedCount) {
  char capturedAt[32];
  clockutil::formatIso(clockutil::nowMs(), capturedAt, sizeof capturedAt);
  JsonDocument doc;
  doc["v"] = CONTRACT_VERSION;
  doc["type"] = "TelemetryCaptured";
  doc["readerId"] = readerId_;
  doc["capturedAt"] = capturedAt;
  doc["signalStrength"] = signalStrength;
  doc["uptime"] = uptimeS;
  doc["firmwareVersion"] = settings::FIRMWARE_VERSION;
  doc["bufferedCount"] = bufferedCount;
  doc["droppedCount"] = droppedCount;
  return send(network_, "telemetry", doc);
}

bool EventPublisher::publishHeartbeat() {
  char sentAt[32];
  clockutil::formatIso(clockutil::nowMs(), sentAt, sizeof sentAt);
  JsonDocument doc;
  doc["v"] = CONTRACT_VERSION;
  doc["type"] = "Heartbeat";
  doc["readerId"] = readerId_;
  doc["sentAt"] = sentAt;
  return send(network_, "heartbeat", doc);
}

bool EventPublisher::publishError(const char *code, const char *message, uint64_t occurredAtMs) {
  char occurredAt[32];
  clockutil::formatIso(occurredAtMs, occurredAt, sizeof occurredAt);
  JsonDocument doc;
  doc["v"] = CONTRACT_VERSION;
  doc["type"] = "ReaderError";
  doc["readerId"] = readerId_;
  doc["code"] = code;
  doc["message"] = message;
  doc["occurredAt"] = occurredAt;
  return send(network_, "error", doc);
}
