# Connecting the decoder

RCTC reads live laps straight from the AMB/MyLaps decoder over TCP, with nothing else to install or set up. You point RCTC at the decoder once, and it reconnects on its own if the link drops.

In development you use the built-in **fake decoder simulator** in place of hardware.

---

## Never connect two timing programs to one decoder

**Never connect RCTC and another timing program (such as RCResults) to the same decoder, or the same simulator instance, at the same time.** Both dial out to the decoder's TCP port. The RC-4 text protocol's tolerance for several clients at once is unconfirmed, so one may silently stop receiving PASSING records. Neither program can detect the other. This is operator discipline.

---

## How it fits together

```
AMB Decoder (TCP :5100)  or  Fake Decoder Simulator
                   │
                   │  RCTC dials out to the decoder
                   ▼
          Spring Boot app (RCTC)
                   │  STOMP WebSocket
                   ▼
             Browser clients
```

RCTC is the only client that connects to the decoder. Nothing connects in to RCTC except browsers.

---

## Step 1 — Start the app

```bash
make up     # Piper (optional)
make dev    # Spring Boot on :8080
```

---

## Step 2 — Set the decoder address

In the app, go to **Admin → Decoder** (`/admin/decoder`), or use the setup wizard on first run.

- **Decoder Host:** the decoder's IP address on your venue LAN, or `localhost` for the simulator.
- **Protocol:** `RC4` for firmware below 4.5 (port 5100). Most club decoders use this.
- **Port:** fills in from the protocol. Change it only if your decoder uses another port.

Click **Test Connection**. It shows **Connected** once the decoder is streaming.

---

## Step 3 — Start the decoder (simulator or real hardware)

### Using the simulator (development)

The simulator emulates an AMB decoder on `:5100`. Start it before you test the connection.

```bash
# Generative mode: synthetic PASSING records, 16 transponders, ~10–15 s laps with jitter
make simulator

# Playback mode: replays a captured .dump file
make simulator-playback

# Playback from a custom dump file
make simulator-playback DUMP_FILE=path/to/capture.dump
```

Generative mode options (pass them with `--args` if you run the module directly):
- `--transponders=101,102,...`: transponder IDs (default is the demo club's 101–116)
- `--interval-ms=12500`: base lap time in milliseconds
- `--jitter-ms=2500`: spread of the per-lap noise. Each driver also has a fixed pace up to 5% faster or slower than the base lap, laps vary down to the millisecond (bell-curve noise, clamped to ±`jitterMs`), and about one lap in 15 is a slow lap (a spin or marshal stop) adding up to 2.5 × `jitterMs`. `0` gives steady laps at each driver's pace.

The simulator prints each PASSING record to stdout.

### Using real hardware

Set the decoder's IP address as the host in **Admin → Decoder**. Port 5100 is the RC-4 text protocol (firmware ≤ 4.4). See `docs/AMB_DECODER_PROTOCOL.md` in the source repository for protocol details.

### Trying split loops with the simulator

Track mode runs a start/finish decoder and fake split decoders that see the same cars, so you can try split loops without a second real decoder:

```bash
# Finish decoder on 5100, two split decoders on 5101 and 5102
RCTimingControl simulate --mode=track --port=5100 --split-ports=5101,5102 \
    --split-positions=0.35,0.7 --split-delay-ms=0,25
```

- `--split-positions` is how far round the lap each split is, from 0 to 1 (default: spread evenly).
- `--split-delay-ms` holds a split decoder's records back, so RCTC anchors its clock late. It fakes the clock offset between two real decoders. Set the matching **clock offset** on that split decoder (a negative number to correct a delay) and see whether the sector times line up.

Point the main decoder at `localhost:5100` under **Admin → Decoder**, and add the split decoders on ports 5101 and 5102.

### Recording a real decoder to a file

To capture real race data as test data, or to share one decoder between several programs, see [recording-the-decoder.md](recording-the-decoder.md).

---

## Split loops (several decoders)

The decoder above is the **start/finish loop**: it is the only one that credits laps. You can add decoders at split points round the track through the admin API `/api/v1/admin/split-decoders` (an ADMIN task; the admin page for it is still to come). Each has a name, address, port, a **split number** (1 for the first split after the finish loop, up to 9, each used once) and a **clock offset**.

A split decoder never credits a lap. When a car crosses the finish loop and has crossed every enabled split in order, RCTC works out the sector times: finish to split 1, split 1 to split 2, and so on to the finish. A lap with a missing or out-of-order split gets no sector times, never wrong ones. Nothing about lap counting or results changes. In this first version the sector times are worked out in memory only; showing and saving them is planned (see issue #672).

RC-4 decoders send seconds since power-on, not a clock time, so RCTC lines each decoder up with the server clock when its first passing arrives. Two decoders can therefore disagree by some milliseconds. The **clock offset** (milliseconds, added to every passing from that decoder) lets you correct a known difference.

---

## Status

The race-control bar shows **DECODER** in one of three states:

| State | Meaning |
|-------|---------|
| Connected (green) | Streaming PASSING records |
| Reconnecting (amber) | The link dropped. RCTC retries with backoff, 1 s up to 30 s. |
| Disconnected (red) | No decoder configured, or RCTC is not connected |

---

## Troubleshooting

### DECODER stays red after Test Connection

Check that the simulator is running, or that the decoder is powered on and reachable from the timing PC. Confirm the host and port in **Admin → Decoder**. Ping the decoder to confirm the network path.

### DECODER shows reconnecting

The connection dropped, or the decoder is not accepting connections. RCTC retries on its own. If it stays amber, check the decoder and the network.

### Unknown transponder alerts appear in race control

A PASSING record arrived for a transponder number not assigned to any entry in the current race. Use **Link to entry** in race control to assign it. Laps already counted are credited to the entry automatically, and the number is kept on the entry, so it counts in the event's later races without linking again.

---

## Decoder Makefile targets

| Target | Description |
|--------|-------------|
| `make simulator` | Run the fake decoder in generative mode on :5100 |
| `make simulator-playback` | Replay `sample-passings.dump` through the fake decoder |
| `make simulator-playback DUMP_FILE=…` | Replay a custom dump file |
