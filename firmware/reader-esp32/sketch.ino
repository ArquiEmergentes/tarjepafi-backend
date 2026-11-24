// TarjePAFI reader firmware for the ESP32 DevKit + MFRC522 + LCD 20x4, runnable on wokwi.com.
// Device contract v1 (docs/IMPLEMENTATION_PLAN.md section 4): publishes tap, heartbeat, telemetry and error messages
// to tarjepafi/readers/{readerId}/..., listens on tarjepafi/readers/{readerId}/display.
// The loop never calls delay(): every module is scheduled with millis().

#include <Arduino.h>

#include "ClockUtil.h"
#include "EventPublisher.h"
#include "ReaderNetwork.h"
#include "NfcReader.h"
#include "ReaderDisplay.h"
#include "ReaderSettings.h"
#include "TapBuffer.h"
#include "Telemetry.h"
#include "config.h"  // Wi-Fi, broker and reader id; copy config.example.h, never commit it

ReaderNetwork network({WIFI_SSID, WIFI_PASSWORD, WIFI_CHANNEL, MQTT_HOST, MQTT_PORT, MQTT_USER, MQTT_PASSWORD,
                        READER_ID});
EventPublisher publisher(network, READER_ID);
TapBuffer tapBuffer;
NfcReader nfc(settings::PIN_RFID_SS, settings::PIN_RFID_RST);
ReaderDisplay display;
Telemetry telemetry(publisher, network, tapBuffer);

// The live tap waiting for its display reply.
struct AwaitedReply {
  bool active = false;
  bool timedOut = false;
  char eventId[37] = "";
  unsigned long sentAt = 0;
} awaited;

DisplayReply incomingReply;
bool incomingReplyReady = false;

bool announcePending = false;   // a new MQTT session that has not sent its heartbeat yet
bool hardwareErrorPending = false;
uint64_t hardwareErrorAtMs = 0;

bool switchRaw = false;
bool switchStable = false;
unsigned long switchChangedAt = 0;

// Runs inside network.loop(); must not publish.
void onDeviceMessage(const char *topic, const char *payload, size_t length) {
  DisplayReply reply;
  if (!DisplayReply::parse(payload, length, reply)) {
    Serial.printf("[display] ignored malformed message on %s\n", topic);
    return;
  }
  if (awaited.active && strcmp(reply.eventId, awaited.eventId) == 0) {
    incomingReply = reply;
    incomingReplyReady = true;
  } else {
    // Replies for flushed buffered taps (or for a tap superseded by a newer one) are only logged.
    Serial.printf("[display] reply for %s (not the current tap): %s\n", reply.eventId,
                  reply.lineCount > 0 ? reply.lines[0] : "");
  }
}

bool offlineSwitchOn() {
  return digitalRead(settings::PIN_OFFLINE_SWITCH) == LOW;  // slid right: common terminal shorted to GND
}

void readOfflineSwitch(unsigned long now) {
  bool raw = offlineSwitchOn();
  if (raw != switchRaw) {
    switchRaw = raw;
    switchChangedAt = now;
  }
  if (switchRaw != switchStable && now - switchChangedAt >= settings::SWITCH_DEBOUNCE_MS) {
    switchStable = switchRaw;
    network.setForcedOffline(switchStable);
  }
}

void bufferTap(const char *eventId, const char *uid, unsigned long now) {
  BufferedTap tap;
  strncpy(tap.eventId, eventId, sizeof tap.eventId - 1);
  tap.eventId[sizeof tap.eventId - 1] = '\0';
  strncpy(tap.cardUid, uid, sizeof tap.cardUid - 1);
  tap.cardUid[sizeof tap.cardUid - 1] = '\0';
  tap.tappedAtMs = clockutil::synced() ? clockutil::nowMs() : 0;
  tap.tappedMillis = now;
  bool kept = tapBuffer.push(tap);
  Serial.printf("[tap] buffered %s %s (%u pending%s)\n", eventId, uid, static_cast<unsigned>(tapBuffer.size()),
                kept ? "" : ", oldest dropped");

  char pending[21];
  snprintf(pending, sizeof pending, "Pendientes: %u", static_cast<unsigned>(tapBuffer.size()));
  const char *lines[] = {"Guardado sin", "conexion", pending};
  display.showMessage(Tone::Info, lines, 3, now);
}

void handleTap(const char *uid, unsigned long now) {
  Serial.printf("[tap] card %s\n", uid);
  display.showReading(now, settings::REPLY_TIMEOUT_MS);

  char eventId[37];
  EventPublisher::newEventId(eventId);

  if (network.online() && clockutil::synced() &&
      publisher.publishTap(eventId, uid, clockutil::nowMs(), false)) {
    awaited.active = true;
    awaited.timedOut = false;
    strncpy(awaited.eventId, eventId, sizeof awaited.eventId);
    awaited.sentAt = millis();  // publishing waited for the PUBACK
    return;
  }
  awaited.active = false;  // a newer tap supersedes any reply still awaited
  bufferTap(eventId, uid, millis());
}

void handleReplies(unsigned long now) {
  if (incomingReplyReady) {
    incomingReplyReady = false;
    if (awaited.active && strcmp(incomingReply.eventId, awaited.eventId) == 0) {
      Serial.printf("[display] reply after %lu ms\n", now - awaited.sentAt);
      display.showReply(incomingReply, now);
      awaited.active = false;
    }
  }
  if (!awaited.active) {
    return;
  }
  unsigned long waited = now - awaited.sentAt;
  if (!awaited.timedOut && waited >= settings::REPLY_TIMEOUT_MS) {
    awaited.timedOut = true;
    Serial.printf("[display] no reply for %s after %lu ms\n", awaited.eventId, waited);
    const char *lines[] = {"Lectura enviada", "Sin respuesta", "del servidor"};
    display.showMessage(Tone::Info, lines, 3, now);
  }
  if (waited >= settings::LATE_REPLY_WINDOW_MS) {
    awaited.active = false;  // a reply arriving later than this is only logged
  }
}

// Sends buffered taps in order, with buffered: true and their original tappedAt.
void flushBuffer() {
  if (tapBuffer.empty() || !network.online() || !clockutil::synced()) {
    return;
  }
  for (size_t sent = 0; sent < settings::FLUSH_MAX_PER_LOOP && !tapBuffer.empty(); ++sent) {
    const BufferedTap *tap = tapBuffer.peek();
    uint64_t tappedAtMs = tap->tappedAtMs;
    if (tappedAtMs == 0) {
      // Tapped before NTP had set the clock: rebuild the time from millis().
      tappedAtMs = clockutil::nowMs() - static_cast<uint64_t>(millis() - tap->tappedMillis);
    }
    if (!publisher.publishTap(tap->eventId, tap->cardUid, tappedAtMs, true)) {
      return;  // keep it and retry on a later loop
    }
    tapBuffer.pop();
    if (tapBuffer.empty()) {
      Serial.println("[tap] buffer flushed");
    }
  }
}

void reportHardwareError() {
  if (!hardwareErrorPending || !network.online() || !clockutil::synced()) {
    return;
  }
  if (hardwareErrorAtMs == 0) {
    hardwareErrorAtMs = clockutil::nowMs();
  }
  if (publisher.publishError("HARDWARE_FAILURE", "MFRC522 not found", hardwareErrorAtMs)) {
    hardwareErrorPending = false;
  }
}

void setup() {
  Serial.begin(115200);
  Serial.printf("\nTarjePAFI reader %s, firmware %s\n", READER_ID, settings::FIRMWARE_VERSION);

  pinMode(settings::PIN_OFFLINE_SWITCH, INPUT_PULLUP);
  display.begin();

  if (nfc.begin()) {
    Serial.printf("[nfc] MFRC522 ready, version 0x%02X\n", nfc.version());
  } else {
    Serial.printf("[nfc] MFRC522 not found (version 0x%02X)\n", nfc.version());
    hardwareErrorPending = true;
  }

  network.begin(onDeviceMessage);
  switchRaw = switchStable = offlineSwitchOn();
  network.setForcedOffline(switchStable);
}

void loop() {
  unsigned long now = millis();

  readOfflineSwitch(now);
  network.loop(now);
  if (network.consumeReconnected()) {
    announcePending = true;
  }
  if (announcePending && telemetry.announce(millis())) {
    announcePending = false;
  }

  now = millis();  // connecting or publishing may have taken a while
  char uid[32];
  if (nfc.poll(now, uid, sizeof uid)) {
    handleTap(uid, now);
  }
  handleReplies(millis());

  if (!announcePending) {
    flushBuffer();
    reportHardwareError();
    telemetry.loop(millis());
  }

  now = millis();
  display.loop(now);
  if (!display.busy(now)) {
    display.showIdle(READER_ID, network.statusText(), tapBuffer.size(), tapBuffer.dropped());
  }
}
