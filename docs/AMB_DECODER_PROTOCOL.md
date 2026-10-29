# AMB Decoder Protocol Reference

> **Implementation status:** the RC-4 text protocol is implemented in the shared `decoder-protocol/` module (`Rc4TextParser`, `EpochAnchor`, `SeqGapDetector`), used by `app/`'s decoder listener and `decoder-simulator/`. Only the RC-4 text protocol is documented or supported here.

## Overview

AMB/MyLaps RC timing decoders used by clubs speak the RC-4 text protocol, described below. The decoder's other (binary) protocol is not documented or supported by this project.

| Protocol | Common name | Firmware | Port | Format |
|----------|------------|----------|------|--------|
| RC-4 text | AmbRc / P9.8 / Enhanced | < 4.5 | **5100** | SOH-prefixed, tab-separated ASCII |

The **RC-4 text protocol** is the dominant protocol for RC club use. The majority of club decoders run firmware below 4.5 and use the text protocol. The Go prototype in `oggthemiffed/RCTimingForwarder` was written specifically for this protocol and connects to port 5100.


---

## Protocol Selection (firmware version boundary, port numbers)

### Firmware 4.5 — the critical boundary for RC club use

MyLaps firmware 4.5 was a deliberately disruptive update:

- **Added:** simultaneous support for drone racing and RC car transponders on the same decoder.
- **Removed:** support for **all MRT transponders** (both red and blue light rechargeable MRT units), the original AMBrc DP transponder, and house handout transponders.
- **Protocol:** firmware 4.5 uses exclusively the modern RC4 protocol, which this project does not support.

**Impact:** clubs that support the MRT rechargeable transponder (the most common affordable transponder in UK/AU club racing) remain on **firmware ≤ 4.4** and therefore use the **RC-4 text protocol on port 5100**. This is the user's case.

Firmware 4.4 supports:
- MYLAPS RC4 3-wire transponders
- MYLAPS RC4 Hybrid 2-wire transponders
- Older AMBrc 2-wire transponders
- MRT rechargeable transponders (red / blue light)
- House handout transponders

### Protocol is not auto-detected

The decoder menu option "Protocol RS232" (General menu) selects the RS232 output protocol; "Enhanced" is the RC-4 text protocol (the default). The TCP/IP output is not separately configurable in the menu.

**For practical implementation: connect to port 5100 (text protocol) on firmware < 4.5.**

---

## RC-4 Text Protocol (firmware < 4.5, port 5100)

### Overview

- **Also called:** AmbRc protocol, P9.8, Enhanced protocol, P98
- **Port:** TCP 5100. The decoder **listens**; the client connects.
- **Encoding:** 7-bit ASCII
- **Line ending:** `CR LF` (`\r\n`, i.e. `0x0D 0x0A`)
- **Field separator:** horizontal tab (`\t`, `0x09`)
- **SOH prefix:** each line begins with byte `0x01` (ASCII SOH / Start of Header)
- **No client handshake required:** the decoder begins streaming immediately after TCP connection. No greeting message is needed from the client.
- **Flow:** decoder streams records continuously. Client is read-only (no commands sent back).

### Connection & Handshake

```
Client → TCP connect to decoder on port 5100
Decoder → streams STATUS and PASSING records immediately
```

There is **no application-level handshake**. The decoder starts streaming `#` (STATUS) records every 5 seconds and `@` (PASSING) records as transponders are detected. The Go client in `RCTimingForwarder/amb_rc4_decoder_client.go` confirms this: it connects and immediately reads lines without sending anything.

**⚠ AMB20 mode note:** Very old AMBrc decoders power up in AMB20 compatibility mode (truncated transponder IDs) until AMBrc software triggers the switch to AMBrc mode. The RC-4 decoder (firmware ≤ 4.4) powers up directly in RC-4 / AmbRc mode — no mode-switch command is needed.

### Line Format

Every line has this structure:

```
SOH <type> TAB <fields...> TAB <crc> CRLF
```

Where:
- `SOH` = `0x01` (one byte, not printable)
- `<type>` = single ASCII character: `#` (STATUS) or `@` (PASSING)
- `TAB` = `0x09`
- `<crc>` = hex CRC, ASCII string prefixed with `x` (e.g. `x6B89`), no `0x` prefix
- `CRLF` = `0x0D 0x0A`

### Record Types

Two record types are observed in the captures:

| Type char | Record name | Description |
|-----------|-------------|-------------|
| `#` | STATUS | Background noise / heartbeat, sent every ~5 seconds |
| `@` | PASSING | Transponder detection event |

No other record types are observed. There is no separate WATCHDOG or RESEND in this protocol.

### STATUS Record — Field Reference

```
0x01 '#' TAB decoderID TAB seqnum TAB noiseLevel TAB unknownField TAB crc CRLF
```

| Position | Field name | Type | Units | Example | Notes |
|----------|-----------|------|-------|---------|-------|
| 0 | SOH | byte `0x01` | — | `\x01` | Fixed prefix |
| 1 | type | char `'#'` | — | `#` | Record type discriminator |
| 2 | decoder_id | uint, ASCII decimal | — | `20` | Decoder unit identifier. Value `20` observed in all captures. |
| 3 | seq_num | uint, ASCII decimal | — | `0`, `1`, `2563` | Monotonically increasing per record (shared with PASSING). Wraps at max uint. |
| 4 | noise_level | uint, ASCII decimal | raw 0–255+ | `72`, `73`, `64` | Background loop noise. Target < 40 for good install. |
| 5 | unknown | uint, ASCII decimal | — | `0` | Always `0` in all observed records. Possibly unused or reserved field. |
| 6 | crc | hex string | — | `x6B89` | CRC of record. Prefixed with literal `x`, no `0x`. 4 hex digits. |

**Example (raw, with `^A`=SOH, `^I`=TAB, `^M`=CR):**
```
^A#^I20^I0^I72^I0^Ix6B89^M
```

**Example (decoded):**
```
# | decoder_id=20 | seq=0 | noise=72 | unknown=0 | crc=x6B89
```

STATUS records arrive approximately every **5 seconds**. They serve as a heartbeat — if no STATUS record arrives for > 30 seconds, assume the decoder connection is lost.

### PASSING Record — Field Reference

```
0x01 '@' TAB decoderID TAB seqnum TAB transponderCode TAB timeSinceStart TAB hitCounts TAB signalStrength TAB passingStatus TAB crc CRLF
```

| Position | Field name | Type | Units | Example | Notes |
|----------|-----------|------|-------|---------|-------|
| 0 | SOH | byte `0x01` | — | `\x01` | Fixed prefix |
| 1 | type | char `'@'` | — | `@` | Record type discriminator |
| 2 | decoder_id | uint, ASCII decimal | — | `20` | Same decoder ID as STATUS |
| 3 | seq_num | uint, ASCII decimal | — | `2`, `7`, `2575` | Shared sequence counter with STATUS records |
| 4 | transponder_code | uint, ASCII decimal | — | `8533156`, `3885036` | Transponder hardware ID (chip number). Two distinct transponders visible in captures. |
| 5 | time_since_start | float, ASCII decimal | **seconds** | `596.126`, `1164.389` | **Seconds since decoder power-on.** Not a Unix timestamp. See §Timestamp Handling. |
| 6 | hit_counts | uint, ASCII decimal | count | `400`, `266` | Number of loop crossings during passing window. Higher = slower / better signal. |
| 7 | signal_strength | uint, ASCII decimal | raw 0–255 | `163`, `107` | Signal strength of transponder. Target > 60 above noise. |
| 8 | passing_status | uint, ASCII decimal | — | `2` | Always `2` in all observed records. Meaning unclear; likely "valid passing". |
| 9 | crc | hex string | — | `x14F5` | CRC. Same format as STATUS. |

**Example (raw):**
```
^A@^I20^I2^I8533156^I596.126^I400^I163^I2^Ix14F5^M
```

**Example (decoded):**
```
@ | decoder_id=20 | seq=2 | transponder=8533156 | time=596.126s | hits=400 | strength=163 | status=2 | crc=x14F5
```

### Full Annotated Sample (from `dump_amb_ip.dump`)

```
# 20  0    72  0   x6B89     ← STATUS:  seq=0,   noise=72
# 20  1    73  0   xF2E9     ← STATUS:  seq=1,   noise=73
@ 20  2    8533156  596.126  400  163  2  x14F5  ← PASSING: transponder 8533156 at 596.126s
# 20  3    73  0   xB66A     ← STATUS:  seq=3,   noise=73
# 20  4    72  0   xE28F     ← STATUS:  seq=4,   noise=72
@ 20  7    8533156  614.635  389  157  2  xF896  ← PASSING: same transponder at 614.635s
@ 20  10   8533156  625.565  252  102  2  x31C9  ← PASSING: lap time = 625.565 - 614.635 = 10.930s
@ 20  22   8533156  671.684  392  158  2  xA648  ← PASSING: lap time = 671.684 - ...
@ 20  27   3885036  688.41   70   103  2  x4C27  ← PASSING: second transponder appears
```

Two transponder IDs appear in the captures: `8533156` and `3885036`. Lap times calculated from successive `time_since_start` values for the same transponder.

### Timestamp Handling — Converting `time_since_start` to Wall Clock

This is the **most critical implementation concern** for the text protocol.

#### What `time_since_start` is

`time_since_start` is a **floating-point number of seconds elapsed since the decoder was powered on** (or possibly since last decoder reset/session start). It has **millisecond precision** (3 decimal places observed, e.g. `596.126`, `1175.823`).

**It is NOT a Unix timestamp.** It contains no date or absolute time reference.

#### How the decoder reports its power-on reference

The RC-4 text protocol does **not** transmit an explicit power-on reference timestamp in a separate "sync" message. The decoder has no facility to tell the client "I was started at wall-clock time X".

There are three approaches to wall-clock anchoring:

**Option A — Server receipt time (simplest, recommended for club use)**

Record the server's wall-clock time (`Instant.now()`) at the moment the first (or any) line is received. All subsequent timestamps are offsets from that reference. This introduces ≤ network RTT error (< 1ms on LAN) and is perfectly adequate for RC lap timing where lap times are > 8 seconds.

```
Instant decoderEpoch = null;

void onFirstRecord(double timeSinceStart) {
    // Anchor: server clock - reported offset = decoder power-on instant
    decoderEpoch = Instant.now().minusMillis((long)(timeSinceStart * 1000));
}

Instant toWallClock(double timeSinceStart) {
    return decoderEpoch.plusMillis((long)(timeSinceStart * 1000));
}
```

**Option B — Use the first PASSING record in a race session as the lap zero reference**

For race timing purposes the absolute wall-clock time of a lap is less important than the elapsed race time and lap durations. Record the `time_since_start` of the race start signal as `raceStartTime` and compute `elapsedRaceTime = timeSinceStart - raceStartTime` for each passing. This is entirely relative and avoids the wall-clock problem.


**Option C — absolute decoder timestamps (not available in the RC-4 text protocol):** the text protocol has no absolute timestamp, so use Option A or B.

#### Precision

The `time_since_start` field has 3 decimal places = **1ms resolution** in the text protocol. This is consistent with the decoder's 3ms timebase listed in official specifications. Do not assume sub-millisecond accuracy.

#### Decoder clock drift

The decoder's internal oscillator can drift a few milliseconds per minute. For a 5-minute RC heat (the typical race duration), drift is negligible for lap timing purposes.

### Heartbeat / Keepalive

STATUS (`#`) records are sent by the decoder every **~5 seconds** regardless of transponder activity. They serve as a heartbeat. If no STATUS record arrives within 30 seconds, declare the decoder connection dead and attempt reconnection.

There is no client-to-decoder keepalive in the RC-4 text protocol. The decoder simply pushes records; the client reads them.

### CRC Format

The CRC field is the last tab-delimited field on each line. Format: literal ASCII character `x` followed by 4 uppercase hexadecimal digits. Example: `x6B89`, `xF2E9`, `x14F5`.

The CRC algorithm is **not documented in any available open-source reference**. The Go implementation in `RCTimingForwarder/amb_rc4_processor.go` and the Android `P98Parser.kt` both declare CRC checking as unimplemented (returning `true` / not validating). For initial implementation, log CRC values but do not reject records on mismatch.

---

## Implementation Architecture

### `TimingSource` Interface

Both protocols expose the same event to the application: a transponder was detected at a specific point in time. A single interface covers both:

```java
public interface TimingSource {
    /** Start listening for events. Called once on startup. */
    void start();
    /** Stop gracefully. */
    void stop();
}

public record LapPassingEvent(
    long    transponderId,   // chip ID from hardware
    Instant timestamp,       // wall-clock UTC (server-anchored offset)
    int     signalStrength,  // raw units
    int     hitCount,        // number of loop crossings
    int     decoderId,       // hardware decoder ID
    long    sequenceNumber   // per-decoder monotonic counter
) {}
```

### RC-4 Text Protocol — Java Implementation Notes

1. **Parser must be a pure function** — `String line → Optional<LapPassingEvent>`. No Spring dependencies. No I/O.

2. **Netty LineBasedFrameDecoder** is appropriate: the protocol is line-oriented (`\n` or `\r\n` terminated). Use `LineBasedFrameDecoder` followed by a `StringDecoder` (US_ASCII), then the application handler.

3. **SOH prefix handling:** the `0x01` byte is at position 0 of each line. Strip it before splitting on TAB.

4. **Time anchoring:** record `Instant.now()` when the first record arrives and subtract `timeSinceStart` seconds to derive `decoderEpoch`. Apply to all subsequent records.

5. **Sequence monitoring:** track the last seen `seq_num` across both STATUS and PASSING records (they share the same counter). A gap in sequence numbers does not indicate a retransmit opportunity — there is no RESEND in the text protocol. Log the gap.

6. **CRC:** log but do not reject. CRC algorithm is unconfirmed.

```java
// Skeleton RC-4 text parser (pure function — no Spring)
public class Rc4TextParser {
    private static final int SOH = 0x01;

    public Optional<LapPassingEvent> parse(String rawLine) {
        if (rawLine.isEmpty() || rawLine.charAt(0) != SOH) return Optional.empty();
        String line = rawLine.substring(1);  // strip SOH
        String[] fields = line.split("\t");
        return switch (fields[0]) {
            case "@" -> parsePassing(fields);
            case "#" -> Optional.empty();  // STATUS — used for heartbeat only
            default  -> Optional.empty();
        };
    }

    private Optional<LapPassingEvent> parsePassing(String[] f) {
        // f[0]='@' f[1]=decoderId f[2]=seqNum f[3]=transponderId f[4]=timeSinceStart
        // f[5]=hits f[6]=strength f[7]=passingStatus f[8]=crc
        if (f.length < 9) return Optional.empty();
        return Optional.of(new LapPassingEvent(
            Long.parseLong(f[3]),          // transponderId
            null,                          // timestamp — anchored externally by TimingSource
            Integer.parseInt(f[6]),        // signalStrength
            Integer.parseInt(f[5]),        // hitCount
            Integer.parseInt(f[1]),        // decoderId
            Long.parseLong(f[2])           // sequenceNumber
        ));
    }
}
```

### TCP Simulator for Development

Implement a `FakeDecoder` that listens on a configurable port and emits:
- STATUS records every 5 seconds
- Scripted PASSING records from a CSV file of `(transponder, timeSecs)` tuples

The simulator enables full end-to-end testing of the app without physical hardware.

---

## Sources

| Source | Used for |
|--------|----------|
| `oggthemiffed/RCTimingForwarder` `samples/dump_amb_ip.dump` | RC-4 text protocol IP capture — 44 lines, two transponders, field values confirmed |
| `oggthemiffed/RCTimingForwarder` `samples/dump_amb_serial.dump` | RC-4 text protocol serial capture — identical format, higher sequence numbers |
| `oggthemiffed/RCTimingForwarder` `amb_rc4_decoder_client.go` | TCP port 5100 confirmed, line-reading loop |
| `oggthemiffed/RCTimingForwarder` `amb_rc4_processor.go` | Field annotations (decoder_id, seq_num, transponder_code, time_since_start, hit_counts, signal_strength, passing_status, CRC) |
| `oggthemiffed/RCTimingForwarder` `main.go` | Port 5100; the decoder's address came from its configuration (a club address, not recorded here) |
| `skoky/ammc-android` `P98Parser.kt` | P98/RC-4 text parser — field count per record type (STATUS 5 fields, PASSING 9 fields), tab separator, CRC format, time_since_start × 1000 = milliseconds |
| AMBrc manual Rev.1.1b (FCC filing) | Power-up in AMB20 mode; AMBrc software triggers AMBrc mode switch; STATUS every 5 seconds; RS232 connection |
| MYLAPS RC manual (Jan 2010, `transponderservices.com`) | RC-4 system overview; RS232/TCP connection cable listed |
| LiveRC news article | Firmware 4.5: disables MRT transponders, adds drone support, uses RC4 protocol exclusively |
| AMBrc manual Rev.3.3 (`transponderservices.com`) | Decoder startup behaviour; noise/signal/hit descriptions |
