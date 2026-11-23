// Copy to config.h (git-ignored) and fill in the broker password. Never commit config.h.
#pragma once

#define WIFI_SSID "Wokwi-GUEST"
#define WIFI_PASSWORD ""
#define WIFI_CHANNEL 6  // Wokwi-GUEST is on channel 6; passing it skips the scan

#define MQTT_HOST "tarjepafi-broker-pafi7k2.chilecentral.azurecontainer.io"
#define MQTT_PORT 1883
#define MQTT_USER "reader-demo"
#define MQTT_PASSWORD "change-me"

#define READER_ID "RDR-A-301"
