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
- **Protocol:** `RC4` for firmware below 4.5 (port 5100). Most club decoders use this. `P3` is not supported yet.
- **Port:** fills in from the protocol. Change it only if your decoder uses another port.

Click **Test Connection**. It shows **Connected** once the decoder is streaming.

---

## Step 3 — Start the decoder (simulator or real hardware)

### Using the simulator (development)

The simulator emulates an AMB decoder on `:5100`. Start it before you test the connection.

```bash
# Generative mode: synthetic PASSING records, 6 transponders, ~10–15 s laps with jitter
make simulator

# Playback mode: replays a captured .dump file
make simulator-playback

# Playback from a custom dump file
make simulator-playback DUMP_FILE=path/to/capture.dump
```

Generative mode options (pass them with `--args` if you run the module directly):
- `--transponders=101,102,...`: transponder IDs (default matches the dev seed, 101–106)
- `--interval-ms=12500`: base lap time in milliseconds
- `--jitter-ms=2500`: spread of the per-lap noise. Each driver also has a fixed pace up to 5% faster or slower than the base lap, laps vary down to the millisecond (bell-curve noise, clamped to ±`jitterMs`), and about one lap in 15 is a slow lap (a spin or marshal stop) adding up to 2.5 × `jitterMs`. `0` gives steady laps at each driver's pace.

The simulator prints each PASSING record to stdout.

### Using real hardware

Set the decoder's IP address as the host in **Admin → Decoder**. Port 5100 is the RC-4 text protocol (firmware ≤ 4.4). See `docs/AMB_DECODER_PROTOCOL.md` in the source repository for protocol details.

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
