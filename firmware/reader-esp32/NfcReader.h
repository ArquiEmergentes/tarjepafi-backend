// MFRC522 card reader: polls for cards and returns each new UID as upper-case hex with colons (04:3D:9B:12).
#pragma once

#include <Arduino.h>
#include <MFRC522.h>

class NfcReader {
 public:
  NfcReader(uint8_t ssPin, uint8_t rstPin);

  /** Starts SPI and the MFRC522. Returns false when the chip does not answer (version register 0x00 or 0xFF). */
  bool begin();

  /** The version register read by begin(), for the serial log. */
  uint8_t version() const { return version_; }

  /**
   * Polls at most every NFC_POLL_INTERVAL_MS. Returns true and fills uidOut with a card that was not read in the
   * last SAME_CARD_IGNORE_MS; the same card read again within that time is ignored.
   */
  bool poll(unsigned long now, char *uidOut, size_t size);

 private:
  MFRC522 rfid_;
  uint8_t version_ = 0;
  unsigned long lastPollAt_ = 0;
  char lastUid_[32] = "";
  unsigned long lastUidAt_ = 0;
};
