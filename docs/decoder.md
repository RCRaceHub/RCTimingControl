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

### Recording a real decoder to a file

To capture real race data as test data, run the recorder instead of (or, if your decoder tolerates it, beside) the app. It only listens; it sends nothing to the decoder. The same warning applies: the recorder is another client of the decoder's port, so stop other timing programs while it runs unless you have confirmed the decoder serves several.

| System | Command |
|--------|---------|
| Windows | `"C:\Program Files\RCTimingControl\RCTimingControl.exe" record --host=192.168.1.50` |
| Linux | `/opt/rctimingcontrol/bin/RCTimingControl record --host=192.168.1.50` |

Flags: `--host` (required), `--port` (default 5100), `--listen-port` (proxy mode, below) and `--out` (default `decoder-<date>-<time>.dump` in the current folder). Stop it with `Ctrl+C`. It reconnects if the decoder drops, and appends if the file already exists.

To run it in Docker instead, build the small recorder image (it holds only the recorder, no app or database) and mount a folder for the files:

```
docker build -f docker/Dockerfile.recorder -t rctc-recorder .
docker run --rm -v "$PWD/captures:/captures" rctc-recorder --host=192.168.1.50 --out=/captures/capture.dump
```

Use `docker run -it` so `Ctrl+C` stops it cleanly, or `docker stop` from another terminal. The container only needs to reach the decoder's address; no ports are published.

It writes two files. `capture.dump` holds every record exactly as the decoder sent it, and the simulator replays it: `RCTimingControl simulate --mode=playback --file=capture.dump`. `capture.dump.times` holds the same records, each after the time in milliseconds since 1970 (UTC) at which the recorder received it, which is useful for checking real timing against the decoder's own clock.

#### Proxy mode: one connection to the decoder, many programs

Add `--listen-port=5101` and the recorder also re-serves everything it reads on that port. Point RCTC (**Admin → Decoder**, host = the recorder machine, port 5101), RCResults or another recorder at it instead of at the decoder. The decoder then sees a single connection, whatever its limit, and the recording is made at the same time.

- Each client gets every record from the moment it connects (RC-4 has no handshake or history to replay, so a program that connects mid-race sees laps from then on).
- A slow or stuck client is dropped without affecting the recording or the other clients.
- Clients only receive. The proxy never sends anything to the decoder.
- The port has no password, like the decoder's own. Keep it on the venue network.
- In Docker, add `-p 5101:5101` to publish it.

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
