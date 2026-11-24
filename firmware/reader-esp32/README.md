# TarjePAFI reader (ESP32 + MFRC522, Wokwi)

Firmware of the classroom card reader used in the live showcase (plan phase 6). It speaks the device contract v1
of [`docs/IMPLEMENTATION_PLAN.md`](../../docs/IMPLEMENTATION_PLAN.md) section 4 over plain MQTT (port 1883) with the
RabbitMQ broker, and shows the backend's reply on a 20x4 LCD.

The folder is flat on purpose: wokwi.com projects are a flat list of files, so the same files are uploaded there,
run with the Wokwi VS Code extension, or built with PlatformIO.

## Files

| File | Purpose |
|---|---|
| `sketch.ino` | Main loop: offline switch, tap flow, replies, buffer flush. No `delay()`, everything on `millis()` |
| `ReaderSettings.h` | Pins, timings, buffer size, firmware version |
| `NfcReader.h/.cpp` | MFRC522: UID as upper-case hex with colons, the same card ignored for 2 s |
| `ReaderNetwork.h/.cpp` | Wi-Fi, NTP, MQTT session with exponential backoff (1 s to 30 s), offline switch |
| `EventPublisher.h/.cpp` | Contract v1 JSON payloads, UUID v4 event ids, QoS 1 publish |
| `TapBuffer.h/.cpp` | Ring buffer of 500 taps; when full the oldest is dropped and counted |
| `ReaderDisplay.h/.cpp` | LCD, green and red LEDs, buzzer; parses the display reply |
| `Telemetry.h/.cpp` | Periodic heartbeat (60 s) and telemetry (30 s), plus both right after each reconnection |
| `ClockUtil.h/.cpp` | UTC time from NTP, ISO 8601 formatting |
| `config.example.h` | Template for `config.h` (Wi-Fi, broker, reader id). `config.h` is git-ignored |
| `diagram.json` | Wokwi circuit |
| `libraries.txt` | Libraries wokwi.com installs |
| `wokwi.toml`, `platformio.ini` | Wokwi VS Code extension + PlatformIO build |

## Pin map

| Part | Part pin | ESP32 DevKit C v4 |
|---|---|---|
| MFRC522 (`board-mfrc522`) | SDA (SPI chip select) | GPIO 5 |
| | SCK | GPIO 18 |
| | MOSI | GPIO 23 |
| | MISO | GPIO 19 |
| | RST | GPIO 4 (not 21 as in Wokwi's sample: 21 is the LCD's I2C SDA) |
| | 3.3V / GND | 3V3 / GND |
| LCD 20x4 I2C (`wokwi-lcd2004`, address 0x27) | SDA | GPIO 21 |
| | SCL | GPIO 22 |
| | VCC / GND | 5V / GND |
| Green LED (220 Ω) | anode | GPIO 26 |
| Red LED (220 Ω) | anode | GPIO 27 |
| Buzzer | + (pin 2) | GPIO 25 |
| Offline slide switch | common (pin 2) | GPIO 32 (input pull-up) |
| | right (pin 3) | GND |

Switch to the **left** (default): online. Switch to the **right**: forced offline (taps are buffered).

## Messages

Publishes, all QoS 1, to `tarjepafi/readers/{READER_ID}/…`:

| Topic | When | Payload |
|---|---|---|
| `tap` | every card read | `{"v":1,"type":"CardTapped","eventId","readerId","cardUid","tappedAt","buffered"}` |
| `heartbeat` | every 60 s and after each reconnection | `{"v":1,"type":"Heartbeat","readerId","sentAt"}` |
| `telemetry` | every 30 s and after each reconnection | `{"v":1,"type":"TelemetryCaptured","readerId","capturedAt","signalStrength","uptime","firmwareVersion","bufferedCount","droppedCount"}` |
| `error` | once, if the MFRC522 does not answer at start-up | `{"v":1,"type":"ReaderError","readerId","code":"HARDWARE_FAILURE","message","occurredAt"}` |

Times are UTC with milliseconds (`2026-11-23T15:04:05.123Z`). `droppedCount` is an optional field; the backend
ignores it today.

Subscribes (QoS 1) to `tarjepafi/readers/{READER_ID}/display` and shows `{eventId, tone, lines}` when `eventId` is
the last live tap. Replies to flushed buffered taps are only logged on the serial monitor.

## Tap flow

1. Card read → "Leyendo..." and a short click at once.
2. Online and clock synced → publish the tap (`buffered: false`). Otherwise → buffer it and show
   "Guardado sin conexion" with the pending count.
3. The reply for that `eventId` is shown with its tone: green LED and high beep (`success`), short green blink
   (`info`), red LED and low beep (`error`).
4. No reply after 3 s → "Lectura enviada / Sin respuesta del servidor". A reply that arrives up to 15 s after the
   tap is still shown (the deployed backend can take more than 3 s, see `docs/PROGRESS.md`, 2026-11-24).
5. On every new MQTT session: heartbeat and telemetry first (so an OFFLINE reader is ACTIVE again), then the buffer
   is sent oldest first, with `buffered: true` and the original `tappedAt`.

Connecting to the broker blocks the loop while it waits for the CONNACK (up to 10 s): the MQTT library has no
asynchronous connect. Card reads made during that time are picked up when the loop resumes.

## Run it on wokwi.com

1. Start the backend first: the broker creates the `reader-demo` user only once the backend has declared its
   exchange.
2. On https://wokwi.com create a new **ESP32** project.
3. Replace `sketch.ino` and `diagram.json` with the files of this folder.
4. Add every `.h` and `.cpp` file of this folder as a new tab with the same name, and `libraries.txt`.
   Skip `platformio.ini`, `wokwi.toml`, `README.md` and `config.example.h`: wokwi.com does not accept them (it only
   takes code files, `diagram.json` and `libraries.txt`) and does not need them; they are for the VS Code extension
   and PlatformIO.
5. Add a tab `config.h` with the content of `config.example.h` and the real `reader-demo` password
   (ask the backend owner; it is not in the repo).
6. Press play. The serial monitor shows Wi-Fi, NTP and `[mqtt] connected`; the LCD shows "En linea".
7. Click the MFRC522 to open its panel, pick a card and press **Tap**.

A saved wokwi.com project is public, `config.h` included. Use only `reader-demo` there and rotate its password after
each showcase (plan section 6).

### Wokwi cards

The MFRC522 part offers these presets (Wokwi docs). The Blue Card UID can be changed with the part's `uid` attribute.

| Key | Card | UID |
|---|---|---|
| `b` | Blue Card | `01:02:03:04` |
| `g` | Green Card | `11:22:33:44` |
| `y` | Yellow Card | `55:66:77:88` |
| `r` | Red Card | `AA:BB:CC:DD` |
| `n` | NFC Tag | `04:11:22:33:44:55:66` documented; the simulated reader returns `04:11:22:33` (4 bytes) |
| `k` | Key Fob | `C0:FF:EE:99` |

These UIDs are not registered in the demo seed yet (plan phase 6 step 6). Until then every tap is answered with the
"card not registered" message, which still proves the round trip.

## Run it with VS Code or PlatformIO

```bash
cp config.example.h config.h   # then set MQTT_PASSWORD
pio run                         # builds .pio/build/reader-esp32/firmware.bin
```

Then "Wokwi: Start Simulator" in VS Code (needs a Wokwi licence), which uses `wokwi.toml` and `diagram.json`.

## Serial monitor codes

`[mqtt] connect failed, lastError=… returnCode=…`:

| lastError | returnCode | Meaning |
|---|---|---|
| -3 | — | No network path to the broker (DNS, TCP, port) |
| -9 | 6 | No CONNACK within the timeout |
| -10 | 4 | Wrong user name or password |
| -10 | 5 | User not authorised |

## Demo script

See plan section 6, "What the demo must show". This reader covers steps 1 and 3 to 6; step 6 uses the offline switch.
