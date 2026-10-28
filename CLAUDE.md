# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

RC club race timing and race control, replacing RCResults. It is part of the RaceHub suite: RaceHub (a separate cloud app) owns booking, racer accounts and event entry, and RCTimingControl (RCTC) is the timing side, run only at the venue (local-only plan, tracking issue #8).

RCTC is one program on a laptop at the track. It installs as a background service, reads the AMB/MyLaps decoder directly over TCP, and serves race control, the announcer and the spectator boards to browsers on the venue network. It keeps the club's results and championships. Only **officials** sign in. Racers never log in to RCTC: their entries arrive in a RaceHub export file, or officials add them as walk-ins.

See `docs/PROJECT.md` for the requirements summary, `docs/REQUIREMENTS.md` for the full requirement list (current, planned and removed), and `docs/architecture.md`; the scoring rules and what each setting changes are in `docs/scoring.md`.

## Stack

**Backend:** Spring Boot 3.4.x, Java 21 (LTS), Gradle (Kotlin DSL)

**Frontend:** React 19 + React Router 7 + Vite, TypeScript, Tailwind CSS 4 + shadcn/ui, TanStack Query v5, TanStack Table v8, React Hook Form v7, Zod, `@stomp/stompjs` (native WebSocket — no SockJS)

**Persistence:** SQLite (one file, sqlite-jdbc; WAL, one write connection plus a read pool), Flyway for migrations. The vendor is chosen in one place (`rctiming.database.vendor`, `persistence/DatabaseConfig`); vendor-specific code lives only in `persistence/vendor/`, and `PersistencePortabilityTest` enforces it. See `docs/development.md` → "Keeping the database swappable".

**Data access:** jOOQ 3.19.x for every read and write, with no ORM (#69). jOOQ's DSL is generated from the Flyway schema into `app/src/generated/jooq`.
- **Write side (domain module):** entities are plain classes holding ids, not associations. Each has a `@Repository` class extending `persistence/JooqRepository`, which maps it to its table's record (by a MapStruct mapper next to the repository; entities built by constructor are mapped by hand in `toEntity`/`toRecord`) and keeps the method names Spring Data had (`findById`, `save`, `findByEventId` and so on). There is no dirty checking: a changed entity reaches the database only through `save`, and must be saved before anything reloads it by id. Columns written once, such as creation times, go in the repository's `insertOnly()`.
- **Read side (query module):** type-safe SQL for all projections and aggregations: scoring calculations, championship standings, lap aggregates, results views.
- Writes run in Spring transactions on the write connection. Reads outside a transaction use the read pool and see only committed data. Read-side query classes use `@ReadTransaction` (`persistence/`): one snapshot on the read pool, so they never queue behind the writer. A read that must wait for a write in progress needs `@Transactional(readOnly = true)`, which runs on the write connection.

**Race format config:** Stored as JSON text (`CHECK (json_valid(...))`) with a `type` discriminator, converted by a `JsonTextConverter` subclass in its repository. Validated against a sealed Java class hierarchy on write. Override patches (FORMAT-07) stored as a second JSON column and merged at read time. Supports JSON import/export (FORMAT-14).

**Auth:** Spring Security + JWT (stateless). JJWT 0.12.x. JWT in `Authorization: Bearer` header for REST; passed in STOMP `CONNECT` frame for WebSocket.

**Real-time:** Spring WebSocket with STOMP in-memory broker. STOMP topics:
- `/topic/race/{raceId}/timing` — live lap passings, positions, gaps
- `/topic/race/{raceId}/state` — race lifecycle changes
- `/topic/race/{raceId}/battles` — cars running close together and the trend of each gap (`timing/battle/BattleTracker`)
- `/topic/race/{raceId}/marshal` — marshal lap adjustments
- `/topic/race/{raceId}/unknown-transponder`, `/audio`, `/bump-up-alert` — race director, announcer and bump-up prompts
- `/topic/race/{raceId}/warmup` — which cars of a race on the grid the decoder has seen on the warm-up lap
- `/topic/event/{eventId}/auto-run` — an event's auto run state
- `/topic/practice/{sessionId}/timing` and `/unknown-transponder` — open practice
- `/topic/system/decoder-status` — whether the decoder is connected
- `/topic/system/live-feed-status` — whether the live feed is connected to its relay

**TCP decoder client:** Netty 4.1.x, in `app/`'s `DecoderListener` (a `SmartLifecycle`), which reads the decoder's host and port from the club profile. **RC-4 text** (`LineBasedFrameDecoder`, port 5100) for firmware < 4.5 decoders, the dominant club hardware, is implemented. **AMB P3 binary** (`ByteToMessageDecoder`, 0x8E/0x8F delimiters, TLV body, 0x8D byte-stuffing, port 5403) for firmware ≥ 4.5 is deferred. See `docs/AMB_DECODER_PROTOCOL.md`. Protocol parsing lives in the shared `decoder-protocol/` module (pure and Spring-free), used by the decoder listener and the simulator.

**Decoder simulator:** `decoder-simulator/` is a fake RC-4 decoder for development and trying the app out. The app runs it with `RCTimingControl simulate` (or `java -jar app.jar simulate`). It also holds a test relay and viewer page for the live feed, run with `RCTimingControl relay`.

**Entries:** imported from RaceHub's Entry Export v1 JSON (`domain/racehub`) or an RC-Timing style driver CSV (`domain/csvimport`, a preview-and-pick adapter), or added by hand as walk-ins. Each entry points at a **competitor** (`domain/competitor`: display name, RaceHub driver ID, BRCA number, home club) and carries a primary and an optional secondary transponder for that event.

**Packaging:** Spring Boot serves the built React app (`-PbundleFrontend`). `jpackage` wraps the jar with its own Java runtime into a Windows `.msi`, macOS `.pkg` or Linux `.deb` that installs a background service. See `docs/installing.md`. `docker/Dockerfile` builds the same jar into an image, `docker-compose.demo.yml` runs it with the demo club and the simulator for testers on any system, and `docker-compose.club.yml` runs the club's own server with Piper on a laptop or small server on the club network (#99, `docs/docker.md`). Inside a container the app can't see the host's address, so `rctiming.network.addresses` names it for the About page. There is no internet deployment.

**Testing:** JUnit 5 + Mockito on temporary SQLite databases, no Docker (backend); Vitest + React Testing Library (frontend)

### Do Not Use
- Spring Boot 2.x (EOL)
- Spring WebFlux / reactive stack (unnecessary complexity at venue scale)
- HTMX (incompatible with WebSocket-driven live timing UI)
- Next.js (no SSR needed — all data fetching is client-side)
- Create React App (unmaintained)
- Redux (Zustand or TanStack Query cover all state management needs)
- SockJS (venue LAN in 2026 does not need WebSocket fallback)
- RabbitMQ / Kafka (in-process STOMP broker is sufficient for single-club deployment)
- Liquibase (Flyway plain-SQL is simpler)
- Spring Data JPA or Hibernate (dropped for jOOQ in #69)
- PostgreSQL or Testcontainers (SQLite in-process; tests run on temporary SQLite files)
- Vendor-specific SQL in Java code (jOOQ DSL and the shared converters only; the vendor lives in `persistence/vendor/`)
- gRPC, a separate forwarder process, or cloud sync for timing (the app reads the decoder directly)
- Racer accounts, self-registration or a `RACER` role (RaceHub owns racer identity)
- nginx, TLS or internet deployment (the app stays on the venue network; it installs natively, or runs from the Docker image in `docker/`, see `docs/docker.md`)

## Architecture

**Modular monolith** — one Spring Boot process, single SQLite database file, single deployment.

### Component Boundaries

| Component | Responsibility |
|-----------|---------------|
| **Admin Panel API** | Club, tracks, classes, formats, events, championships, officials, backups |
| **Entries** | RaceHub import (upsert by `entry_id`, higher `entry_version` wins, withdraw never delete, class mapping), RC-Timing CSV import (preview new/changed/missing, apply only what the official picks, never withdraw walk-ins), walk-ins, competitors |
| **Transponder swap** | On-the-day transponder swap from race control's Transponders page, audit-logged |
| **Race Control API** | Race lifecycle commands, marshal laps, grid calls, referee tools |
| **Boards** | Spectator "now and next" and results boards, and the streaming overlay for OBS (`/boards/overlay`), all anonymous (`/api/v1/boards`); the race clock comes from `timing/RaceClockService` |
| **Backups** | Scheduled and on-demand SQLite backups, and the `restore` command |
| **Live feed** | Live Feed v1 to a relay for remote viewers (`livefeed/`): one outbound WebSocket, on its own thread, per-event switch, display names only; see `docs/live-feed-v1.md` |
| **Results export** | Results Export v1 to RaceHub (`resultsexport/`): an outbox queued on finish, correction and day close, sent in the background with retries; see `docs/results-export-v1.md` |
| **Domain Core** | Business logic, aggregates, domain events |
| **Race State Machine** | Enforces `PENDING → GRID → RUNNING → STOPPED/FINISHED` transitions |
| **Decoder Listener** | Netty client reading the decoder (RC-4 text), emits `LapPassingEvent`s and decoder status |
| **Live Timing Hub** | Broadcasts real-time updates to browsers via STOMP |
| **Domain module** | Entities, jOOQ repositories, write-side business logic |
| **Query module** | jOOQ read queries — scoring, standings, lap aggregates, results projections |

The domain and query modules are a seam: entities and their repositories stay in the domain module, and projections stay in the query module. This maps to a CQRS-lite split within the monolith — write-side logic loads and saves entities, the read side builds its views in SQL.

### Decoder → Live Display Flow

```
AMB Decoder (TCP) → DecoderListener (Netty) → LapTimingService
  → resolves transponder → entry (primary or secondary number) → competitor
  → updates the race's in-memory state and positions
  → LiveTimingHub → STOMP broadcast → browser clients
```

The decoder listener runs on a **dedicated background thread** (`SmartLifecycle`), completely isolated from the Tomcat thread pool. It hands each parsed passing to a single timing thread (`timingExecutor`), which publishes a `LapPassingEvent` to `LapTimingService` via `ApplicationEventPublisher`, so passings are handled one at a time in decoder order and the decoder thread never touches the database. A transponder that matches no entry in the running race, or more than one, goes to the referee's unknown-transponder flow and is never silently credited.

### Race State Machine

`PENDING → GRID → RUNNING → STOPPED → RUNNING` (resume) or `RUNNING → FINISHED` (a stopped race can also be finished; a grid call can be taken back to `PENDING`)

`RaceStatus` is an enum on the `Race` entity. `RaceStateMachineService` has one method per command (`callGrid`, `start`, `stop`, `finish`, `abandon`), each checked against one table of allowed transitions; `start`, `finish` and `abandon` set the race's times before the move saves it, so every listener reads them. `restart(race)` is separate because it resets a race to `PENDING` from any state. Invalid transitions throw `IllegalStateTransitionException` (HTTP 409). Every successful command publishes a `RaceStatusChangedEvent` (for audio, the race clock, the live feed, results export and practice) and has `LiveTimingHub` broadcast the new state to `/topic/race/{id}/state`; `restart` only broadcasts. Abandoning a race finishes it with an abandoned time.

Marshal laps are **not** state transitions — they are `MarshalAdjustment` records (+1/−1) with full audit trail that trigger position recalculation and re-broadcast.

### Roles

Staff roles are **stackable** — a single user account can hold any combination:

| Role | Permissions |
|------|-------------|
| `ADMIN` | Everything the other roles can do, plus club config, tracks, formats and classes, decoder, backups, results exports, officials and the audit log, the RaceHub, CSV and feed imports, creating and editing events and championships, and competitor merges |
| `RACE_DIRECTOR` | Runs the day: moves an event through its states, generates rounds and seeds finals, adds walk-ins and withdraws entries, race control commands (call grid, start, stop, finish, abandon, restart, skip), marshal laps, links unknown transponders, audio settings, the live feed switch and open practice |
| `REFEREE` | Raises incident reports, applies lap and time penalties, records marshal absences and penalties |
| Any official | Transponder swap, the run order, and read-only views of events, entries and race history |
| Competitors | No account (L10, #18): entries come from the RaceHub import or are added as walk-ins |
| Anonymous | Event schedule, live timing, results, championship standings |

### Key Data Design Notes

- Lap timestamps: for P3 binary decoders use the `RTC_TIME` field (GPS/NTP-synchronised UTC microseconds). For RC-4 text decoders use server-anchored offset (no absolute timestamp in protocol). Every timestamp column is `BIGINT` UTC microseconds, mapped to `Instant` by converters in `persistence/convert/`.
- **Do not store live race positions in the database during a race** — calculate in memory, broadcast over WebSocket, persist only the final result snapshot on `FINISHED`.
- Protocol parsing (`Rc4TextParser` in `decoder-protocol/`) must stay pure, with no Spring dependencies. Protocol I/O is separate from domain logic.
- Championship points: calculate on demand from result snapshots; do not increment incrementally.
- Transponders belong to an entry, not a racer: a primary and an optional secondary number per entry, not unique system-wide. A number already used in the event is accepted with a warning on import or walk-in (one competitor may use it in several classes); a swap refuses a number another competitor holds. RaceHub's export snapshots them at booking; swaps on the day are audit-logged. At race time a number matching more than one entry in the running race goes to the referee.
- Competitors have no login. Results, live timing and championship standings group by competitor. RCTC stores no contact, date of birth, guardian or payment data.
- Race format config is snapshot-at-assignment — template edits do not affect existing events (FORMAT-06).

## AMB Decoder Protocol (Two Protocols — Choose by Firmware)

See `docs/AMB_DECODER_PROTOCOL.md` for the full reference. Summary:

**RC-4 text protocol (firmware < 4.5, port 5100) — implement this first:**
- SOH-prefixed, tab-separated ASCII lines, CRLF terminated
- Two record types: `#` (STATUS/heartbeat every 5s) and `@` (PASSING)
- PASSING fields: `decoderId TAB seqNum TAB transponderId TAB timeSinceStart_s TAB hits TAB strength TAB status TAB crc`
- `timeSinceStart_s` is float seconds since decoder power-on — NOT a Unix timestamp
- Convert to wall clock: anchor server time at first record, add offset for each subsequent record
- No client handshake, no RESEND, no WATCHDOG — just read lines
- Port 5100 confirmed from club hardware captures

**AMB P3 binary protocol (firmware ≥ 4.5, port 5403) — implement second:**
- Frame delimiters: `0x8E` (start) / `0x8F` (end), TLV body, `0x8D` byte-stuffing
- `RTC_TIME` field = uint64 microseconds since Unix epoch (UTC) — absolute timestamp
- Monitor `PASSING_NUMBER` for gaps; RESEND requests on gaps
- WATCHDOG record absence = lost decoder connection
- No client handshake required

**Firmware 4.5 boundary:** firmware 4.5 disables MRT transponders (common cheap club transponders). Most clubs stay on firmware ≤ 4.4 and use port 5100 text protocol.

**Without hardware:** `decoder-simulator/` emits RC-4 text records (see Stack above). Wireshark captures from the club's RCResults installation confirmed port 5100.

## Build History

The ten original phases are complete. Some of what they built has since been removed by the local-only plan (#8):

1. Domain Foundation — entities, Flyway, basic CRUD, no UI
2. Racer Portal — auth, event entry, self-service frontend (removed in #18: racers book through RaceHub)
3. Admin Panel — event/championship CRUD, admin frontend
4. Race State Machine + Race Control API — lifecycle commands, HTTP 409 on bad transitions
5. WebSocket Live Timing Infrastructure — STOMP config, domain event → broadcast
6. Forwarder + AMB TCP Receiver — Netty parser, simulator (the forwarder and gRPC were replaced by the app's own decoder listener in #9 and #10)
7. Results & Championship Standings — result snapshots, best-X-from-Y scoring, PDF export
8. First-run setup wizard
9. User manual & in-app documentation
10. Docker trial environment (replaced by the installers in #23 and #24)

A later initiative extracted the shared decoder-protocol parser (`decoder-protocol/`) and built a separate offline race-day app, which was retired in #21; its plans are archived under `docs/plans/archive/`. The local-only plan then added competitors, per-event transponders, the RaceHub import and walk-ins, officials-only login, spectator boards, the SQLite database, backups and the installers. It also sends results back to RaceHub (#27) and a live feed to a relay for remote viewers (#28), and has a streaming overlay page for OBS (#29).

## Code Standards

These came out of the quality checkpoint (#606). Follow them in new code, and fix a neighbouring breach when you touch the file.

### Backend

- **Controllers are thin.** A controller validates input, calls one service and returns a DTO. It never injects a repository or a `DSLContext`; the logic and the transaction live in a service (for example `RaceControlService`). Read-side controllers call a query class.
- **Not found is a 404 thrown by the service or query** (`EntityNotFoundException`), never checked in the controller. Errors reach clients as `ProblemDetail` through `GlobalExceptionHandler`.
- **Real-time pushes go through `LiveTimingHub`.** Services never use `SimpMessagingTemplate` directly; add a method to the hub for a new topic.
- **Read transactions:** read-side query classes use `@ReadTransaction` (read pool). `@Transactional(readOnly = true)` runs on the write connection, so use it only where the read must see an uncommitted write, and say why in the Javadoc.
- **Configuration is read through `@ConfigurationProperties` records** with `@DefaultValue`, enabled by `@EnableConfigurationProperties` on the class that uses them. No `@Value` and no `Environment.getProperty`. A web setting such as `server.address` comes from Boot's `ServerProperties`, taken as an `ObjectProvider` because a non-web context (tests, the crash-recovery child) has none. New settings get a row in `docs/development.md`.
- **Time comes from an injected `Clock`.** Never call `Instant.now()` or `System.currentTimeMillis()` in a service. Use the pattern of a public `@Autowired` constructor that passes `Clock.systemUTC()` to a package-private one taking the `Clock`, and a fixed clock in tests.
- **Request and response records live in the controller package's `dto/` folder**, one file each, never declared inline in a controller and never owned by a service. A service takes plain parameters or a domain type, so it does not depend on the API layer. API DTOs are hand-written, with a static `from(entity)` where one is built from an entity.
- **Mapping:** repositories map records to entities with MapStruct, as the other repositories do; hand-written `toEntity`/`toRecord` only for entities built by constructor.
- **Tests** (`docs/testing.md`): AssertJ assertions, camelCase method names that say the behaviour, `...IT` extending `AbstractIntegrationTest` for anything that starts Spring or touches the database. No `Thread.sleep`: wait with Awaitility, a latch or an injected clock (`TestConventionsTest` enforces it). Put each test in the same package as the class it covers.

### Frontend

- **Never use `window.confirm`, `alert` or `prompt`.** Use `useConfirm` and the shared confirm dialog.
- **Never import axios in a page or component.** Read an API error with `getApiErrorData` / the helpers in `lib/errors.ts`.
- **A failed query shows `QueryError`** (inline, banner or page variant), not an ad-hoc message.
- **Colours come from theme tokens, not raw palette classes.** Use `bg-muted`, `text-destructive`, `text-success`, `text-warning`, `text-info`, `text-race-best` and tints such as `bg-success/15`; they switch with dark mode. Never write `bg-red-500`, `text-amber-600` or a `dark:` pair of palette classes. New status colours are added as tokens in `index.css`. The spectator boards and the podium sit on a fixed dark frame (the frame carries the `dark` class so tokens resolve for it) and keep their own slate palette. Build a conditional `className` with `cn()`, not a template literal.
- **Query keys come from the factories** in `hooks/publicQueryKeys.ts` and `hooks/admin/adminQueryKeys.ts`, never an inline `queryKey: ['...']`. **Exports:** `default` only for lazily routed pages and layouts, named for everything else. **Tests** sit beside the file as `Name.test.tsx`, not in `__tests__`. Details in `docs/development.md` → Frontend conventions.
- Toasts use sonner; forms use React Hook Form with Zod; data fetching uses TanStack Query.

### Before you write something new

Look for the existing helper first (hub method, error helper, confirm hook, properties record, clock pattern). A second way of doing the same thing is a review finding.

## General Good Developer Rules

1. If you raise any processes, start services or ui services, you MUST stop them after you are finished.
2. Use the `gh` CLI for all GitHub operations (push, PR creation, issue management). The repo uses SSH git remotes with `gh auth` managing the underlying token. Remote URL: `git@github.com:RCRaceHub/RCTimingControl.git`.
3. If you see the context getting filled up to a serious level (75% and above) please stop and give me a restart prompt to continue the task after i have cleared the context
4. **Sensitive documentation must NEVER be committed to the repo.** Any doc you generate that contains any of the following must be saved as `docs/local-*.md` or `*.local.md` (both patterns are gitignored) and never staged or committed:
   - Registry paths, container image locations, or package repository URLs specific to this project
   - GitHub/CI/CD configuration steps, settings URLs, or account-specific setup instructions
   - Internal tooling, build system details, or deployment pipeline internals
   - Anything describing how the infrastructure, release process, or development environment is set up internally
   - Account names, usernames, organisation names, or service endpoints
   - Anything you would not want a member of the general public or a competitor to read

   When in doubt, ask: "would this tell a stranger something useful about how this project is built or operated?" If yes — local file only.

   Examples that MUST be local: GitHub Actions setup guides, GHCR configuration steps, branch protection runbooks, environment variable references, deployment checklists, service account details.

   Examples that are safe to commit: architecture decisions, API contracts, user-facing feature docs, contribution guidelines (no internal URLs), quickstart guides for end users.

5. **Every code change carries its own documentation.** In the same PR as the change:
   - Write Javadoc for every new or changed class, interface, record and enum, and for every public or protected method that does real work. Say what it is for and why, who calls it, which thread it runs on, its transaction boundary, what it throws (and the HTTP status), and units (timestamps are UTC microseconds). Don't restate the name; trivial getters, setters and overrides need nothing. Don't document `app/src/generated/jooq`.
   - Update the Mermaid diagrams in `docs/developer/` when the change alters a flow, the race or event state machine, a component boundary or the domain model (see `docs/developer/README.md`). They are developer docs: never add them to the docs site manifest.
   - Update the user-facing guide in `docs/` when what a user sees or does changes.
   - Check with `./gradlew :app:javadoc -x generateJooq`. The Javadoc CI job runs doclint and fails on malformed comments and broken `{@link}`s.
   A PR that adds code without this is not ready for review.

6. **Every PR follows the same workflow:**
   1. Open it as a draft early, linked to its issue: first thing, or straight after the first change.
   2. Commit and push small changes often, so progress shows on the PR.
   3. Mark it ready for review only when the issue's acceptance is met and CI is green.
   4. **Always spin up a review agent** (the Agent tool) before merging, in the persona of a highly skilled senior Java engineer who knows Spring Boot, jOOQ and SQLite well. Give it the PR's diff and the files around it, never your own conclusions about it. It reviews for:
      - **Security**: authorisation on every endpoint, input validation, injection, secrets or personal data in logs and audit rows, anything a racer or a spectator on the venue network could reach.
      - **Logic errors**: transactions and their boundaries, races between the timing thread and requests, null and empty cases, off-by-ones, state-machine edges.
      - **Formatting**: indentation, imports, line length, dead code and leftover debugging.
      - **Consistency**: naming, comment density and idiom matching the surrounding code, how neighbouring classes are laid out, and the shared helpers it should reuse instead of a new one.

      A frontend-only PR gets the same review of its TypeScript and React. The agent only reports. Check each finding against the code, fix and push the real ones, and say on the PR which were fixed and which were declined and why.
   5. Copilot reviews are not used (they cost credits): don't request one.
   6. Merge only when that's done and CI is green again.
