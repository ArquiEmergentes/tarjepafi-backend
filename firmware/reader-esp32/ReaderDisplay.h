// LCD 20x4 (I2C), green and red LEDs and the buzzer. Everything is non-blocking: loop() switches LEDs off and tells
// when a message has been shown long enough.
#pragma once

#include <Arduino.h>
#include <LiquidCrystal_I2C.h>

#include "ReaderSettings.h"

enum class Tone { Success, Info, Error };

/** A reply from the backend on tarjepafi/readers/{id}/display: {eventId, tone, lines}. */
struct DisplayReply {
  static constexpr size_t MAX_LINES = 6;
  static constexpr size_t MAX_LINE_LENGTH = 48;

  char eventId[65];
  Tone tone;
  char lines[MAX_LINES][MAX_LINE_LENGTH + 1];
  size_t lineCount;

  /** Parses the JSON payload. Returns false when it is not a valid reply. */
  static bool parse(const char *payload, size_t length, DisplayReply &out);
};

class ReaderDisplay {
 public:
  ReaderDisplay();

  void begin();

  /** The idle screen. Rows are rewritten only when their text changes, so it can be called every loop. */
  void showIdle(const char *readerId, const char *status, size_t pending, uint32_t dropped);

  /** Immediate feedback after a card is read; stays until a reply, a timeout message or holdMs. */
  void showReading(unsigned long now, unsigned long holdMs);

  /** Up to four rows; longer texts are word-wrapped, extra rows are cut. */
  void showMessage(Tone tone, const char *const *lines, size_t count, unsigned long now);
  void showReply(const DisplayReply &reply, unsigned long now);

  /** True while a message (or "Leyendo...") should stay on screen instead of the idle screen. */
  bool busy(unsigned long now) const;

  void loop(unsigned long now);

 private:
  void writeRow(uint8_t row, const char *text);
  void signal(Tone tone, unsigned long now);

  LiquidCrystal_I2C lcd_;
  char shown_[settings::LCD_ROWS][settings::LCD_COLUMNS + 1];
  unsigned long holdUntil_ = 0;
  unsigned long ledsOffAt_ = 0;
  bool ledsOn_ = false;
};
