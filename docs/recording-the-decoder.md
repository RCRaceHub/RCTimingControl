# Recording the decoder to a file

This guide shows how to capture what a real decoder sends into a file. The file is test data: the simulator can replay it later, so the whole club software can be tried on real laps without a decoder or a track. It also shows how to share one decoder between several programs.

It works with RC-4 text decoders (firmware below 4.5, port 5100), such as the AMB RC3 and RC4.

## What you need

- A computer on the same network as the decoder (Windows, Linux or macOS) with RCTimingControl installed (see [installing.md](installing.md)), or Docker.
- The decoder's IP address. It is the host you already enter under **Admin → Decoder**.

## Record to a file

Open a terminal (Command Prompt on Windows) and run:

| System | Command |
|--------|---------|
| Windows | `"C:\Program Files\RCTimingControl\RCTimingControl.exe" record --host=192.168.1.50 --out=capture.dump` |
| macOS | `/Applications/RCTimingControl.app/Contents/MacOS/RCTimingControl record --host=192.168.1.50 --out=capture.dump` |
| Linux | `/opt/rctimingcontrol/bin/RCTimingControl record --host=192.168.1.50 --out=capture.dump` |

Replace `192.168.1.50` with your decoder's address. It prints `Recording ...` and keeps going until you press `Ctrl+C`. Leave it running for the whole session. If the connection drops, it reconnects by itself. If the file already exists, new records are added to the end.

| Flag | Meaning |
|------|---------|
| `--host` | The decoder's address (required) |
| `--port` | The decoder's port (default 5100) |
| `--out` | The file to write (default `decoder-<date>-<time>.dump` in the current folder) |
| `--listen-port` | Share the decoder with other programs; see below |

The recorder only listens. It never sends anything to the decoder.

## What you get

- `capture.dump` holds every record exactly as the decoder sent it.
- `capture.dump.times` holds the same records, each after the time (milliseconds since 1970, UTC) at which the recorder received it. Keep it with the dump: it shows real timing against the decoder's own clock.

## Replay it

Replay a capture through the simulator at real speed, or faster with `--speed`:

```
RCTimingControl simulate --mode=playback --file=capture.dump --speed=4
```

Then set the decoder host to `localhost` under **Admin → Decoder**, as in [decoder.md](decoder.md#using-the-simulator-development).

## Check what a recording holds

`dump-summary` reads a capture and reports what is in it, so you can compare it with the laps RCTimingControl credited:

```
RCTimingControl dump-summary --file=capture.dump
```

It prints the number of passings and status records, any gaps in the decoder's sequence numbers (a gap means a record was lost between the decoder and the file), and for each transponder the passings and the shortest and longest time between them. The times are the decoder's own clock.

The dump does not say which race was running. The `capture.dump.times` file beside it holds the computer's clock for each record, and `dump-summary` uses it to split the recording in two ways:

- `--split-gap=120` starts a new section after 120 seconds with no passings, which separates races that have a break between them.
- `--windows=races.csv` counts passings against each race's own times. The file has one race per line, `name,start,end`, in UTC, for example `Heat 1,2026-11-07T10:15:00Z,2026-11-07T10:23:30Z`. Copy the start and finish times from the race history in RCTimingControl (allow for your time zone: UTC is one hour behind British Summer Time). Passings outside every window are listed under "Outside every race window".

Both need the `.times` file, so keep it with the dump.

## Share one decoder between programs

A decoder may accept only one connection at a time, and the limit on port 5100 is not documented. Add `--listen-port` and the recorder keeps the single connection and passes every record on to the others:

```
RCTimingControl record --host=192.168.1.50 --listen-port=5101 --out=capture.dump
```

Then point RCTimingControl, RCResults or another recorder at the **recorder's computer, port 5101**, instead of at the decoder.

- Each program gets every record from the moment it connects. There is no history to replay, so a program that connects mid-race sees laps from then on.
- A slow or stuck program is dropped without affecting the recording or the others.
- The port has no password, like the decoder. Keep it on the venue network.

## Test it before race day

1. Start your normal timing program so the decoder already has a client.
2. Start the recorder from a second computer.
3. Pass a transponder over the loop and check that both still show the lap.

If the recorder gets nothing, or the other program stops, the decoder takes one client only. Use the recorder with `--listen-port` as the single connection instead.

## Run it in Docker

Build the small recorder image (it holds only the recorder) from the source folder, and mount a folder for the files:

```
docker build -f docker/Dockerfile.recorder -t rctc-recorder .
docker run -it --rm -v "$PWD/captures:/captures" rctc-recorder --host=192.168.1.50 --out=/captures/capture.dump
```

Add `-p 5101:5101` and `--listen-port=5101` to share the decoder. `Ctrl+C` (with `-it`) or `docker stop` stops it cleanly.

## Troubleshooting

- **"Connection failed" repeats:** check the address, that the computer can reach the decoder (try `ping`), and that nothing else is holding the decoder's connection.
- **The file stays empty:** the decoder sends a status record every 5 seconds, so an empty file means no connection. Check the port; firmware 4.5 and later is not supported.
- **It stops with a file error:** the disk is full or the folder is not writable. Free space or choose another `--out`.
