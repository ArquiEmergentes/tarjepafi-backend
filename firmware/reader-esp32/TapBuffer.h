// Ring buffer of taps made while offline. When full, the oldest tap is dropped and counted.
#pragma once

#include <Arduino.h>

#include "ReaderSettings.h"

struct BufferedTap {
  char eventId[37];
  char cardUid[32];
  uint64_t tappedAtMs;     // UTC epoch ms; 0 when the clock was not synced yet
  unsigned long tappedMillis;  // millis() at the tap, to rebuild tappedAtMs once the clock is synced
};

class TapBuffer {
 public:
  /** Adds a tap. Returns false when the buffer was full and the oldest tap was dropped to make room. */
  bool push(const BufferedTap &tap);

  /** The oldest tap, or nullptr when empty. */
  const BufferedTap *peek() const;

  /** Removes the oldest tap (after it was published). */
  void pop();

  size_t size() const { return count_; }
  bool empty() const { return count_ == 0; }
  uint32_t dropped() const { return dropped_; }

 private:
  BufferedTap items_[settings::TAP_BUFFER_CAPACITY];
  size_t head_ = 0;  // index of the oldest tap
  size_t count_ = 0;
  uint32_t dropped_ = 0;
};
