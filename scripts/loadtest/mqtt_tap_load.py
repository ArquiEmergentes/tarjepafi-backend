"""Load test of the deployed tap pipeline over MQTT, the way a reader uses it (QAS01 and QAS02).

It publishes card taps to tarjepafi/readers/<reader>/tap (QoS 1) and times each one until the display reply arrives on
tarjepafi/readers/<reader>/display. Run it against a freshly reset demo environment and reset it afterwards: the taps
are real (tap events, access records, data records).

    pip install paho-mqtt
    set MQTT_PASSWORD=<the reader-demo password>
    python scripts/loadtest/mqtt_tap_load.py --host <broker host> --taps 300 --rate 5

Never put the password in this file or on the command line.
"""
import argparse
import json
import os
import statistics
import threading
import time
import uuid
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

# Active student cards of the demo seed that are not re-pointed by the Wokwi tag overlay.
CARDS = [
    "04:73:3C:F2:5C", "04:03:14:9D:35", "04:59:13:A4:5F", "04:5A:BE:4B:39", "04:A0:3C:AE:C6", "04:6E:08:89:99",
    "04:BF:0B:FB:4B", "04:EE:6F:CE:8D", "04:54:0D:05:B5", "04:C7:04:F2:BF", "04:3D:C6:0E:F8", "04:4E:69:51:E6",
    "04:80:0E:7E:03", "04:BB:D1:9E:A8", "04:8D:D9:71:78", "04:36:56:CD:66", "04:E5:D4:81:E2", "04:F6:47:B8:D1",
    "04:6E:70:04:5F", "04:25:35:FF:C9", "04:64:4A:8F:CD", "04:8A:79:47:B7", "04:19:63:75:A2", "04:22:A8:33:5B",
    "04:AE:51:8D:11", "04:8C:E6:65:FA", "04:24:44:52:A5", "04:63:1A:47:0B", "04:52:37:5A:CD", "04:1B:39:A1:B3",
    "04:56:9E:B3:89", "04:0F:99:3A:5D", "04:A4:45:65:72", "04:AC:B9:25:82", "04:9B:69:A8:28",
]
READERS = ["RDR-T-001", "RDR-T-002"]


def percentile(sorted_values, p):
    if not sorted_values:
        return -1
    index = min(len(sorted_values) - 1, max(0, int(round(p / 100 * len(sorted_values) + 0.4999)) - 1))
    return sorted_values[index]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument("--user", default="reader-demo")
    parser.add_argument("--taps", type=int, default=60)
    parser.add_argument("--rate", type=float, default=2.0, help="taps per second; 0 = as fast as possible")
    parser.add_argument("--wait", type=float, default=60.0, help="seconds to wait for the last replies")
    args = parser.parse_args()
    password = os.environ.get("MQTT_PASSWORD")
    if not password:
        raise SystemExit("Set MQTT_PASSWORD to the broker password of the reader user")

    sent, replied = {}, {}
    lock = threading.Lock()

    def on_message(client, userdata, message):
        arrived = time.perf_counter()
        try:
            event_id = json.loads(message.payload).get("eventId")
        except ValueError:
            return
        with lock:
            if event_id in sent and event_id not in replied:
                replied[event_id] = arrived

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="load-" + uuid.uuid4().hex[:8])
    client.username_pw_set(args.user, password)
    client.on_message = on_message
    client.connect(args.host, args.port, keepalive=30)
    client.loop_start()
    for reader in READERS:
        client.subscribe(f"tarjepafi/readers/{reader}/display", qos=1)
    time.sleep(1)

    start = time.perf_counter()
    interval = 1.0 / args.rate if args.rate > 0 else 0
    for i in range(args.taps):
        if interval:
            delay = start + i * interval - time.perf_counter()
            if delay > 0:
                time.sleep(delay)
        event_id = str(uuid.uuid4())
        reader = READERS[i % len(READERS)]
        payload = {
            "v": 1, "type": "CardTapped", "eventId": event_id, "readerId": reader, "cardUid": CARDS[i % len(CARDS)],
            "tappedAt": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
            "buffered": False,
        }
        with lock:
            sent[event_id] = time.perf_counter()
        client.publish(f"tarjepafi/readers/{reader}/tap", json.dumps(payload), qos=1)
    published_in = time.perf_counter() - start

    deadline = time.perf_counter() + args.wait
    while time.perf_counter() < deadline:
        with lock:
            if len(replied) >= args.taps:
                break
        time.sleep(0.1)
    total = time.perf_counter() - start
    client.loop_stop()
    client.disconnect()

    ordered = sorted(sent.items(), key=lambda item: item[1])
    latencies = [round((replied[e] - t) * 1000) for e, t in ordered if e in replied]
    ranked = sorted(latencies)
    third = max(1, len(latencies) // 3)
    print(f"taps published: {args.taps} in {published_in:.0f} s ({args.taps * 60 / max(published_in, 1):.0f} per minute)")
    print(f"replies received: {len(latencies)} of {args.taps} (lost: {args.taps - len(latencies)}), all done in {total:.0f} s")
    if latencies:
        print(
            f"latency publish -> display reply: p50 {percentile(ranked, 50)} ms, p95 {percentile(ranked, 95)} ms, "
            f"p99 {percentile(ranked, 99)} ms, max {ranked[-1]} ms"
        )
        print(
            f"median of the first third: {round(statistics.median(latencies[:third]))} ms, "
            f"of the last third: {round(statistics.median(latencies[-third:]))} ms"
        )


if __name__ == "__main__":
    main()
