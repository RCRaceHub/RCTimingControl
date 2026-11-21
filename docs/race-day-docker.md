# Race day with Docker: recorder and a fresh RCTimingControl

This sets up a laptop for a live meeting with Docker:

- A **recorder** that is the only program connected to the decoder. It writes everything the decoder sends to a file on the laptop's disk, and shares the feed with RCTimingControl **and** the club's existing timing system at the same time.
- A **fresh RCTimingControl** that keeps its database, backups and uploads in a folder on the laptop's disk, not inside Docker.

It is for RC-4 text decoders (firmware below 4.5, port 5100, such as the AMB RC3). Background on the recorder is in [recording-the-decoder.md](recording-the-decoder.md); general Docker notes are in [docker.md](docker.md).

```
                      decoder (192.168.1.50:5100)
                                 |
                          [ recorder ] --> raceday/captures/*.dump
                          port 5101
                         /                \
              RCTimingControl         club timing system
              (recorder:5101)        (laptop address:5101)
```

## Before you start

- The laptop and the decoder are on the same network, and the decoder's address is fixed (for example `192.168.1.50`). Check it with `ping 192.168.1.50`.
- Docker is installed and running. On Windows use [Docker Desktop](https://www.docker.com/products/docker-desktop/) with the WSL 2 backend and run the commands below from a WSL terminal, inside the repository (clone it into the WSL home folder, for example `~/RCTimingControl`, not under `/mnt/c`, so builds and file permissions behave).
- The first start builds the images and needs the internet. It takes several minutes, so do it before race day. After that everything runs offline.

## Set up

1. In the repository folder, create a file called `.env`:

   ```properties
   # The decoder's address (required)
   DECODER_HOST=192.168.1.50
   # This laptop's address on the club network, so the About page shows what phones and boards can use
   CLUB_ADDRESS=192.168.1.20
   ```

   Optional: `DECODER_PORT` (default 5100), `PROXY_PORT` (the port the club's other timing system uses on this laptop, default 5101) and `HOST_PORT` (the web port, default 8080).

2. Build and start:

   ```bash
   docker compose -f docker-compose.raceday.yml up -d --build
   ```

3. Check that the recorder has connected and is writing:

   ```bash
   docker compose -f docker-compose.raceday.yml logs recorder
   ls -l raceday/captures
   ```

   The log shows `Recording 192.168.1.50:5100 ...`. The decoder sends a status record every 5 seconds, so the `.dump` file starts growing within a few seconds.

4. Open **http://localhost:8080** and follow the setup wizard to create the first official and the club. When it asks for the decoder, or later under **Admin → Decoder**, enter host **`recorder`** and port **`5101`** (not the decoder's own address). Click **Test Connection**, then **Save**.

5. Point the club's existing timing system at **the laptop's address, port 5101**, instead of at the decoder. It receives every record from the moment it connects.

If the other timing system must read the decoder directly, don't use this setup: a decoder may allow only one connection.

## Where everything is

All of it is in the `raceday/` folder beside `docker-compose.raceday.yml`, so you can look at it, copy it and back it up with ordinary tools:

| Folder | Holds |
|---|---|
| `raceday/captures/` | `capture-<date>-<time>.dump` (every record exactly as the decoder sent it) and the matching `.dump.times` file (the laptop's clock for each record). Keep the two together. |
| `raceday/data/` | RCTimingControl's database `rctiming.db`, `backups/`, `uploads/` and the `jwt-secret` sign-in key. |

Dates in the recording's file name are UTC (one hour behind British Summer Time). Each time the recorder container starts it opens a new file, so stopping and starting it splits the recording; if the decoder connection drops it reconnects and carries on in the same file. After the meeting, check a recording with [`dump-summary`](recording-the-decoder.md#check-what-a-recording-holds) or replay it with the simulator.

The `raceday/` folder is ignored by git. It is created on first start; it is owned by the container's user (uid 10001), so use `sudo` if you need to delete it.

## Day-to-day

```bash
docker compose -f docker-compose.raceday.yml stop       # stop everything, keep all data
docker compose -f docker-compose.raceday.yml start      # start it again
docker compose -f docker-compose.raceday.yml logs -f    # watch the logs
docker compose -f docker-compose.raceday.yml ps         # is everything up?
```

Both services restart automatically when Docker starts. If the laptop sleeps, laps are lost, so turn off sleep on race day.

- **Firewall:** other devices must reach port 8080 (web) and 5101 (the shared decoder feed). On Windows, allow Docker Desktop through the firewall; on Linux with `ufw`, `sudo ufw allow 8080/tcp` and `sudo ufw allow 5101/tcp`.
- **Starting fresh:** stop the stack, then move or delete `raceday/data` (for a new database) or `raceday/captures`. Never delete `raceday/data` during a meeting.
- **Upgrading:** `git pull`, then `docker compose -f docker-compose.raceday.yml up -d --build`. Take a backup under **Admin → Backups** first.

## Test it before race day

1. Start the stack and the club's timing system, both reading from the recorder.
2. Pass a transponder over the loop (or replay a recording with the simulator in place of the decoder: `DECODER_HOST` can be the simulator's address).
3. Check that both programs show the lap, and that the `.dump` file grew.

## Troubleshooting

- **`DECODER_HOST` error on start:** the `.env` file is missing or has no `DECODER_HOST` line.
- **"Connection failed" repeats in the recorder log:** check the address, `ping` it, and that nothing else holds the decoder's connection.
- **RCTimingControl shows the decoder as disconnected:** check **Admin → Decoder** says host `recorder`, port `5101`, and that `docker compose ... ps` shows the recorder running.
- **A permissions error writing to `raceday/`:** run `docker compose -f docker-compose.raceday.yml up -d` again; the `init` step fixes the folder's owner. If the folder sits on a USB stick or network share that ignores ownership, use a folder on the laptop's own disk.
