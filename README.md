# RCTimingControl

> **Early pre-release — v0.1**

RC club race timing and race control, replacing RCResults. It runs on a laptop at the track, reads the AMB/MyLaps decoder directly, and serves race control, the announcer and the spectator boards to browsers on the venue network. It is the timing side of the RaceHub suite: entries come from a RaceHub export or are added as walk-ins, and only officials sign in.

## Install on a venue laptop

Download the installer for the laptop from the [latest release](https://github.com/RCRaceHub/RCTimingControl/releases/latest): the `.msi` for Windows, the `.pkg` for macOS or the `.deb` for Ubuntu and other Debian-based Linux. It carries its own Java runtime and installs the app as a background service that starts with the laptop.

1. Run the installer.
2. Open **http://localhost:8080** on the laptop and follow the setup wizard to create the first official and the club.
3. Open the About page to see the addresses phones, tablets and boards on the venue network should use.

See [docs/installing.md](docs/installing.md) for each system, the data folder, backups and upgrades, and the [user manual](docs/user-manual.md) for everything from the setup wizard to race day.

The club's server can also run in Docker on a laptop or a small server on the club network: see [docs/docker.md](docs/docker.md#running-the-clubs-server-with-docker).

## Try it out

To explore the app with a demo club and a simulated decoder sending live laps, the quickest way on any system is Docker:

```bash
git clone https://github.com/RCRaceHub/RCTimingControl.git
cd RCTimingControl
docker compose -f docker-compose.demo.yml up --build
```

Then open **http://localhost:8080** and sign in as `admin@example.com` / `trial123`. See [docs/docker.md](docs/docker.md), and [docs/trial-quickstart.md](docs/trial-quickstart.md#things-to-try) for what to try. The trial guide also covers the demo on an installed copy. When you are ready to set up your own club, the [user manual](docs/user-manual.md) takes you from install to race day.

---

## For developers

| Component | Description |
|-----------|-------------|
| `app/` | Spring Boot 3.4 backend — REST API, officials' JWT auth, WebSocket timing hub, direct AMB decoder listener, RaceHub import, events, championships, backups; serves the built frontend |
| `frontend/` | React 18 + Vite + Tailwind + shadcn/ui — admin panel, race control, spectator boards and public results |
| `decoder-simulator/` | Fake AMB decoder over TCP for development and trying the app out (generative and playback modes); the app runs it with `simulate` |
| `decoder-protocol/` | Shared AMB/MyLaps decoder protocol parsing (RC-4 text; P3 binary is deferred) — used by `app/` and `decoder-simulator/` |
| `mapstruct-jooq/` | Compile-time plugin that lets MapStruct map jOOQ records to entities ([docs/development.md](docs/development.md)) |
| `docker-compose.yml` | Piper (TTS), optional, for announcer voices in development. The database is a SQLite file and club logos and TTS clips are stored on local disk, so nothing else needs Docker. |
| `docker/`, `docker-compose.demo.yml`, `docker-compose.club.yml` | The app's Docker image, the demo with a simulated decoder, and the club's own server with announcer voices ([docs/docker.md](docs/docker.md)) |

### Quick start (dev)

**Prerequisites:** Java 21, Node 22.12+, `make`; Docker only for the optional Piper voices

```bash
make dev-start
```

Starts the Spring Boot backend (dev profile, SQLite database in `app/data/db`), the Vite frontend and, if Docker is available, Piper — all in the background.

| Service | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8080 |

```bash
make stop       # shut everything down
make clean-db   # wipe the database and start fresh
```

Two officials (an admin and a race director) and a seed event with six competitors are created automatically in dev mode — see [docs/testing.md](docs/testing.md) for credentials.

For manual setup or individual service control see the [Development guide](docs/development.md).

### Live timing (decoder)

RCTC reads the AMB decoder directly over TCP. There is no separate process to run. Set the decoder's host and port in **Admin → Decoder**. In development, a built-in fake decoder replaces the physical hardware.

```bash
make simulator   # Terminal 2 — fake decoder on :5100
```

Then set the decoder host to `localhost` and click **Test Connection**. See the [decoder setup guide](docs/decoder.md) for the full walkthrough and hardware setup.

### Spectator boards

Point a TV's browser at `/boards/now-next` (the line-up of the race on track, then what's next), `/boards/live-race` (live timing and the position chart) or `/boards/results` (the last finished race). No login is needed. Add `?event=ID` to pin a board to one event; otherwise it follows the event that is racing.

### Streaming overlay

For a live stream, add a **Browser** source in OBS pointing at `http://<laptop>:8080/boards/overlay` (about 450 × 520 pixels). It shows the race on track over the video on a transparent background: the running order, laps, last lap and the race clock (time to go, or time so far when the format sets no length). It is empty between races. Options go in the address:

| Option | Default | Effect |
|--------|---------|--------|
| `top=N` | `10` | Show the first N cars (up to 40) |
| `class=hide` | shown | Leave out the race and class name |
| `theme=light` | `dark` | Dark text on a light panel instead of light on dark |
| `event=ID` | the event racing | Follow one event |

For example `/boards/overlay?top=6&theme=light`.

### Running tests

```bash
make test       # full integration suite — app + decoder simulator (no Docker needed)
make test-fast  # skip jOOQ codegen for faster reruns
```

See [docs/testing.md](docs/testing.md) for the full test matrix, including `decoder-protocol/`.

---

## What's implemented

| Area | What it does |
|------|-------------|
| Club setup | First-run wizard, club profile, tracks and decoder loops, racing classes, race formats, officials and their roles |
| Entries | RaceHub Entry Export v1 import with class mapping, walk-ins added by hand, competitors with no login, a primary and secondary transponder per entry |
| Race day | Transponder swaps on the day, round generator, race control (grid, start/stop, marshal laps), referee tools, unknown-transponder linking, open practice |
| Live timing | Direct AMB RC-4 decoder listener, WebSocket live display, voice announcements (Piper TTS or the browser's voice), spectator boards, a streaming overlay for OBS, a live feed to a relay for remote viewers |
| Results | Result snapshots, best-X-from-Y championship standings, public results pages, printable results, results sent back to RaceHub through a queue that waits for the network |
| Running it | One installer per system that runs as a background service, scheduled backups and a restore command, a command-line admin password reset, in-app help and printable guides |


---

## Docs

- [User manual](docs/user-manual.md) — from install to race day, with a pointer to the detail for each step
- [Installing](docs/installing.md) — install on a venue laptop, data folder, backups, upgrades
- [Trying it out](docs/trial-quickstart.md) — a demo club and a simulated decoder, no developer setup needed
- [Running with Docker](docs/docker.md) — the demo on any system with Docker, and the club's own server on a laptop or small server
- [Decoder setup guide](docs/decoder.md) — connecting the AMB decoder, simulator, status
- [Recording the decoder](docs/recording-the-decoder.md) — capture real decoder data to a file, or share one decoder between programs
- [API reference](docs/api.md) — all endpoints with example requests
- [Development guide](docs/development.md) — environment setup, config, env vars
- [Architecture](docs/architecture.md) — module structure, design decisions
- [Testing guide](docs/testing.md) — running tests, manual UAT checklists
- [AMB decoder protocol](docs/AMB_DECODER_PROTOCOL.md) — RC-4 text and P3 binary wire formats
- [Results Export v1](docs/results-export-v1.md) — the results file sent back to RaceHub
- [Live Feed v1](docs/live-feed-v1.md) — the live feed for remote viewers, and the test relay
