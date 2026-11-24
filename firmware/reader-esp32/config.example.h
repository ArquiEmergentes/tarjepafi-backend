// Copy to config.h (git-ignored) and fill in the broker password. Never commit config.h.
// On wokwi.com, add a tab named config.h with this content. Anyone who opens a public Wokwi project can read it:
// only ever use the restricted reader-demo broker user here, and rotate its password after each showcase.
#pragma once

#define WIFI_SSID "Wokwi-GUEST"
#define WIFI_PASSWORD ""
#define WIFI_CHANNEL 6  // Wokwi-GUEST is on channel 6; use 0 on real hardware to scan

#define MQTT_HOST "tarjepafi-broker-pafi7k2.chilecentral.azurecontainer.io"
#define MQTT_PORT 1883
#define MQTT_USER "reader-demo"
#define MQTT_PASSWORD "change-me"

// Must be a reader registered in the backend; RDR-A-301 is the classroom reader of the demo script.
#define READER_ID "RDR-A-301"
