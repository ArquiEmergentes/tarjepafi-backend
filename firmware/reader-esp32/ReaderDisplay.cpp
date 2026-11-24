#include "ReaderDisplay.h"

#include <ArduinoJson.h>
#include <Wire.h>

static void copyText(char *out, size_t size, const char *text) {
  strncpy(out, text == nullptr ? "" : text, size - 1);
  out[size - 1] = '\0';
}

bool DisplayReply::parse(const char *payload, size_t length, DisplayReply &out) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, length) != DeserializationError::Ok) {
    return false;
  }
  const char *eventId = doc["eventId"] | "";
  if (eventId[0] == '\0') {
    return false;
  }
  copyText(out.eventId, sizeof out.eventId, eventId);
  const char *tone = doc["tone"] | "info";
  out.tone = strcmp(tone, "success") == 0 ? Tone::Success : strcmp(tone, "error") == 0 ? Tone::Error : Tone::Info;
  out.lineCount = 0;
  for (JsonVariantConst line : doc["lines"].as<JsonArrayConst>()) {
    if (out.lineCount == MAX_LINES) {
      break;
    }
    copyText(out.lines[out.lineCount++], MAX_LINE_LENGTH + 1, line | "");
  }
  return true;
}

ReaderDisplay::ReaderDisplay() : lcd_(settings::LCD_ADDRESS, settings::LCD_COLUMNS, settings::LCD_ROWS) {
  for (auto &row : shown_) {
    row[0] = '\0';
  }
}

void ReaderDisplay::begin() {
  pinMode(settings::PIN_LED_GREEN, OUTPUT);
  pinMode(settings::PIN_LED_RED, OUTPUT);
  pinMode(settings::PIN_BUZZER, OUTPUT);
  digitalWrite(settings::PIN_LED_GREEN, LOW);
  digitalWrite(settings::PIN_LED_RED, LOW);

  Wire.begin(settings::PIN_I2C_SDA, settings::PIN_I2C_SCL);
  lcd_.init();
  lcd_.backlight();
  lcd_.clear();
  for (auto &row : shown_) {
    memset(row, ' ', settings::LCD_COLUMNS);
    row[settings::LCD_COLUMNS] = '\0';
  }
  writeRow(0, "TarjePAFI");
  writeRow(1, "Iniciando...");
}

void ReaderDisplay::writeRow(uint8_t row, const char *text) {
  char padded[settings::LCD_COLUMNS + 1];
  size_t length = strnlen(text, settings::LCD_COLUMNS);
  memcpy(padded, text, length);
  memset(padded + length, ' ', settings::LCD_COLUMNS - length);
  padded[settings::LCD_COLUMNS] = '\0';
  if (strcmp(padded, shown_[row]) == 0) {
    return;  // I2C writes are slow; skip rows that did not change
  }
  memcpy(shown_[row], padded, sizeof padded);
  lcd_.setCursor(0, row);
  lcd_.print(padded);
}

void ReaderDisplay::showIdle(const char *readerId, const char *status, size_t pending, uint32_t dropped) {
  char header[settings::LCD_COLUMNS + 1];
  snprintf(header, sizeof header, "TarjePAFI %10s", readerId);
  char counters[settings::LCD_COLUMNS + 1] = "";
  if (dropped > 0) {
    snprintf(counters, sizeof counters, "Pend %u Perd %lu", static_cast<unsigned>(pending),
             static_cast<unsigned long>(dropped));
  } else if (pending > 0) {
    snprintf(counters, sizeof counters, "Pendientes: %u", static_cast<unsigned>(pending));
  }
  writeRow(0, header);
  writeRow(1, "Acerque su tarjeta");
  writeRow(2, status);
  writeRow(3, counters);
}

void ReaderDisplay::showReading(unsigned long now, unsigned long holdMs) {
  writeRow(0, "");
  writeRow(1, "    Leyendo...");
  writeRow(2, "");
  writeRow(3, "");
  holdUntil_ = now + holdMs;
  tone(settings::PIN_BUZZER, 2500, 40);
}

void ReaderDisplay::showMessage(Tone toneKind, const char *const *lines, size_t count, unsigned long now) {
  // Word-wrap every line to the LCD width and keep the first four rows.
  char rows[settings::LCD_ROWS][settings::LCD_COLUMNS + 1] = {};
  uint8_t row = 0;
  for (size_t i = 0; i < count && row < settings::LCD_ROWS; ++i) {
    const char *text = lines[i];
    size_t remaining = strlen(text);
    do {
      size_t take = remaining;
      if (take > settings::LCD_COLUMNS) {
        take = settings::LCD_COLUMNS;
        for (size_t cut = settings::LCD_COLUMNS; cut > 0; --cut) {
          if (text[cut] == ' ') {
            take = cut;
            break;
          }
        }
      }
      memcpy(rows[row], text, take);
      rows[row][take] = '\0';
      ++row;
      text += take;
      remaining -= take;
      while (*text == ' ') {
        ++text;
        --remaining;
      }
    } while (remaining > 0 && row < settings::LCD_ROWS);
  }
  for (uint8_t r = 0; r < settings::LCD_ROWS; ++r) {
    writeRow(r, rows[r]);
  }
  holdUntil_ = now + settings::MESSAGE_HOLD_MS;
  signal(toneKind, now);
}

void ReaderDisplay::showReply(const DisplayReply &reply, unsigned long now) {
  const char *lines[DisplayReply::MAX_LINES];
  for (size_t i = 0; i < reply.lineCount; ++i) {
    lines[i] = reply.lines[i];
  }
  showMessage(reply.tone, lines, reply.lineCount, now);
}

void ReaderDisplay::signal(Tone toneKind, unsigned long now) {
  digitalWrite(settings::PIN_LED_GREEN, toneKind == Tone::Error ? LOW : HIGH);
  digitalWrite(settings::PIN_LED_RED, toneKind == Tone::Error ? HIGH : LOW);
  ledsOn_ = true;
  switch (toneKind) {
    case Tone::Success:
      ledsOffAt_ = now + 1000;
      tone(settings::PIN_BUZZER, 2000, 120);
      break;
    case Tone::Info:
      ledsOffAt_ = now + 400;
      tone(settings::PIN_BUZZER, 1500, 80);
      break;
    case Tone::Error:
      ledsOffAt_ = now + 1500;
      tone(settings::PIN_BUZZER, 400, 400);
      break;
  }
}

bool ReaderDisplay::busy(unsigned long now) const {
  return static_cast<long>(holdUntil_ - now) > 0;
}

void ReaderDisplay::loop(unsigned long now) {
  if (ledsOn_ && static_cast<long>(now - ledsOffAt_) >= 0) {
    digitalWrite(settings::PIN_LED_GREEN, LOW);
    digitalWrite(settings::PIN_LED_RED, LOW);
    ledsOn_ = false;
  }
}
