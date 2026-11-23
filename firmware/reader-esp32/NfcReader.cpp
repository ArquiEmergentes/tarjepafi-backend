#include "NfcReader.h"

#include <SPI.h>

#include "ReaderSettings.h"

NfcReader::NfcReader(uint8_t ssPin, uint8_t rstPin) : rfid_(ssPin, rstPin) {}

bool NfcReader::begin() {
  SPI.begin();
  rfid_.PCD_Init();
  version_ = rfid_.PCD_ReadRegister(MFRC522::VersionReg);
  return version_ != 0x00 && version_ != 0xFF;
}

bool NfcReader::poll(unsigned long now, char *uidOut, size_t size) {
  if (now - lastPollAt_ < settings::NFC_POLL_INTERVAL_MS) {
    return false;
  }
  lastPollAt_ = now;
  if (!rfid_.PICC_IsNewCardPresent() || !rfid_.PICC_ReadCardSerial()) {
    return false;
  }

  char uid[32] = "";
  size_t length = 0;
  for (byte i = 0; i < rfid_.uid.size && length + 3 < sizeof uid; ++i) {
    length += snprintf(uid + length, sizeof uid - length, i == 0 ? "%02X" : ":%02X", rfid_.uid.uidByte[i]);
  }
  rfid_.PICC_HaltA();
  rfid_.PCD_StopCrypto1();

  bool sameCard = strcmp(uid, lastUid_) == 0 && now - lastUidAt_ < settings::SAME_CARD_IGNORE_MS;
  lastUidAt_ = now;  // a card kept on the reader keeps being ignored
  if (sameCard) {
    return false;
  }
  strncpy(lastUid_, uid, sizeof lastUid_ - 1);
  lastUid_[sizeof lastUid_ - 1] = '\0';
  strncpy(uidOut, uid, size - 1);
  uidOut[size - 1] = '\0';
  return true;
}
