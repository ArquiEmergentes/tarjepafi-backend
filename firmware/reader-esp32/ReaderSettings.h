// Pins, timings and sizes of the TarjePAFI reader. No secrets here: those live in config.h (git-ignored).
#pragma once

#include <Arduino.h>

namespace settings {

constexpr const char *FIRMWARE_VERSION = "1.0.0";

// MFRC522 on the ESP32 VSPI bus (SCK 18, MISO 19, MOSI 23 are the SPI defaults). RST is on GPIO 4, not 21,
// because 21 is the I2C SDA of the LCD.
constexpr uint8_t PIN_RFID_SS = 5;
constexpr uint8_t PIN_RFID_RST = 4;

// LCD 20x4 behind a PCF8574 I2C expander.
constexpr uint8_t PIN_I2C_SDA = 21;
constexpr uint8_t PIN_I2C_SCL = 22;
constexpr uint8_t LCD_ADDRESS = 0x27;
constexpr uint8_t LCD_COLUMNS = 20;
constexpr uint8_t LCD_ROWS = 4;

constexpr uint8_t PIN_LED_GREEN = 26;
constexpr uint8_t PIN_LED_RED = 27;
constexpr uint8_t PIN_BUZZER = 25;
// Slide switch: common terminal on this pin, right terminal on GND. Left (default) = online, right = forced offline.
constexpr uint8_t PIN_OFFLINE_SWITCH = 32;

// Card reading.
constexpr unsigned long NFC_POLL_INTERVAL_MS = 50;
constexpr unsigned long SAME_CARD_IGNORE_MS = 2000;

// Tap flow.
constexpr unsigned long REPLY_TIMEOUT_MS = 3000;    // then "Sin respuesta" is shown
constexpr unsigned long LATE_REPLY_WINDOW_MS = 15000;  // a reply for the last tap is still shown until then
constexpr unsigned long MESSAGE_HOLD_MS = 4000;     // how long a reply stays before the idle screen returns

// Periodic messages. RDR-A-301 in the demo seed has heartbeatInterval 60 s and communicationInterval 30 s; the
// backend marks a reader OFFLINE after three missed heartbeat intervals.
constexpr unsigned long HEARTBEAT_INTERVAL_MS = 60000;
constexpr unsigned long TELEMETRY_INTERVAL_MS = 30000;

// MQTT. The ACI broker has answered CONNACK after about 3 s, so the 1 s library default is too short.
constexpr unsigned long MQTT_COMMAND_TIMEOUT_MS = 10000;
// Short on purpose: the Wokwi simulator runs at roughly half of real time, so a 30 s keep-alive of the simulated
// ESP32 is more than a minute for the broker, which closes a silent session after 1.5 x the keep-alive (seen in
// the broker log as a reconnection about every 70 s).
constexpr int MQTT_KEEP_ALIVE_S = 6;
constexpr unsigned long MQTT_BACKOFF_MIN_MS = 1000;
constexpr unsigned long MQTT_BACKOFF_MAX_MS = 30000;
constexpr unsigned long WIFI_RETRY_INTERVAL_MS = 15000;

// Offline buffer.
constexpr size_t TAP_BUFFER_CAPACITY = 500;
constexpr size_t FLUSH_MAX_PER_LOOP = 5;

constexpr unsigned long SWITCH_DEBOUNCE_MS = 50;

}  // namespace settings
