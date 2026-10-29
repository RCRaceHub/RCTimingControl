# Development Guide

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21 (LTS) |
| Docker | 24+ (optional: only for Piper announcer voices) |
| Node.js | 20+ |
| Gradle | via wrapper (`./gradlew`) |

## Quick start (Makefile)

A `Makefile` at the repo root wraps all common tasks. Run `make` (or `make help`) to see the full list.

No database server to install: the backend keeps its data in a SQLite file under `app/data/db`, created and migrated on first start.

```bash
make dev-start   # Backend (dev profile) + frontend in background, plus Piper if Docker is available
make stop        # Kill backend + frontend (and Piper)
make clean-db    # Delete the dev database; the next start migrates and seeds a fresh one
make test-fast   # Run integration tests skipping jOOQ codegen
```

See [Makefile targets](#makefile-targets) below for the full reference.

### When to re-run jOOQ codegen

The generated jOOQ sources are committed (`app/src/generated/jooq`). Re-run `./gradlew :app:generateJooq` after changing or adding a migration. Codegen migrates a throwaway SQLite file (`app/build/jooq-codegen/schema.db`) and reads the schema from it, so it needs no Docker.

---

## Manual environment setup

If you prefer to run services individually (e.g. in separate terminal tabs):

### 1. Optional: start Piper

```bash
make up
# or: docker compose up -d
```

This starts **Piper** (text to speech) on `localhost:10200` for announcer voices. Without it the app runs normally with announcements off.

Club logos and TTS clips are stored on local disk under `storage.local-path` (defaults to `./data/uploads`, overridable via `STORAGE_LOCAL_PATH`) and served back by the app itself at `/storage/**` — no object-storage server to start.

### 2. Backend

```bash
make dev
# or: ./gradlew :app:bootRun --args='--spring.profiles.active=dev'
```

On first run, Flyway creates the database file and applies the baseline migrations and the dev seed data:

**Baseline (`db/migration/sqlite/`, V1–V6), grouped by area:**
- `V1` — users and roles, refresh tokens, club profile, governing bodies
- `V2` — tracks, decoder loops, lap thresholds, racing classes, format templates, events and event classes
- `V3` — competitors, entries, entry audit log, RaceHub class mappings
- `V4` — rounds, races, race entries, marshal adjustments/absences/penalties, penalties, incident reports, unknown transponder links
- `V5` — result snapshots and championships
- `V6` — practice sessions and laps

The baseline replaced the PostgreSQL history (V1–V36) in #26; `V1` lists the type conventions. Later migrations: `V7` the results export outbox (#27), `V8` the per-event live feed switch (#28), and `V9` drops the profanity blocklist (#30).

**Dev seeds (`db/seed/sqlite/`, V1000+):**
- `V1001` — tracks, racing classes and race format templates
- `V1002` — admin1 and race director accounts, club profile, "Club Championship Round 1" event (IN_PROGRESS) with 6 competitors and entries (transponders 101–106), 6 rounds (P1/P2/Q1/Q2/Q3/Final A), races and race entries

The dev profile keeps the database in `app/data/db` (relative to `app/`, gitignored). `make clean-db` deletes it.

### 3. Frontend

```bash
make ui
# or: cd frontend && npm run dev
```

Vite dev server starts on `http://localhost:5173` with API proxy to `localhost:8080`.

---

## Configuration

### Environment variables

| Variable | Default (dev) | Description |
|----------|---------------|-------------|
| `JWT_SECRET` | unset: a random key in `jwt-secret` in the data directory | Base64 HMAC-SHA256 key that signs officials' sign-in tokens |
| `RCTIMING_DATA_DIR` | per-user app-data folder (dev profile: `app/data/db`) | Folder holding the SQLite database file `rctiming.db` |
| `STORAGE_LOCAL_PATH` | `uploads` inside the data directory | Folder for the club logo and announcer clips |
| `STORAGE_PUBLIC_BASE_URL` | `/storage` | Base URL the browser loads uploads from; relative, so it works on any address |
| `SERVER_PORT`, `SERVER_ADDRESS` | `8080`, all interfaces | Port and bind address. Phones and boards reach the app on any of the laptop's addresses. |

Outside the dev profile the database lives in the user's app-data folder: `%LOCALAPPDATA%\RCTimingControl` on Windows, `~/Library/Application Support/RCTimingControl` on macOS, and `$XDG_DATA_HOME/rctimingcontrol` (or `~/.local/share/rctimingcontrol`) on Linux. The installed packages run the app as a service with `-Drctiming.data-scope=machine`, which moves it to the per-machine folder instead (see [installing.md](installing.md)).

Without `JWT_SECRET`, the first start writes a random key to `jwt-secret` in the data directory and later starts reuse it, so no two installs share a signing key.

### Serving the frontend from the app

`./gradlew -PbundleFrontend :app:bootJar` builds the frontend into the jar, and `SpaConfig` serves it with REST and WebSocket on the same port. Paths that are not a file and not under `/api`, `/ws`, `/storage` or `/actuator` get `index.html`, so deep links work on refresh. The UI and API share one origin, so no CORS settings are needed, and the WebSocket's same-origin check accepts whichever address a device used. In dev, Vite serves the frontend and proxies `/api`, `/ws` and `/storage` to the backend. `./gradlew -PbundleFrontend :app:installer` wraps the jar in a native installer; see [installing.md](installing.md#building-the-installers).

On start the app logs the addresses other devices should open, and `/about` lists them.

### Backups

The app copies its database into a backup folder when an event is marked completed (the race day closes) and every night, and keeps the newest copies. Admins can see them and take one now under **Admin → Backups**. Backing up is safe while a race is running. The database makes the copy in one read transaction (`VACUUM INTO` on SQLite), and laps keep committing meanwhile.

| Setting | Default | Meaning |
|---------|---------|---------|
| `RCTIMING_BACKUP_DIRECTORY` (`rctiming.backup.directory`) | `backups` inside the data directory | Where backups go; may be a USB stick or network share |
| `RCTIMING_BACKUP_KEEP` (`rctiming.backup.keep`) | `14` | How many backups to keep; older ones are deleted |
| `RCTIMING_BACKUP_NIGHTLY_CRON` (`rctiming.backup.nightly-cron`) | `0 0 2 * * *` | When the nightly backup runs (server time); `-` turns it off |
| `RCTIMING_RETENTION_FAILED_SIGN_IN_DAYS` (`rctiming.retention.failed-sign-in-days`) | `90` | How long refused sign-ins stay in the audit log |
| `RCTIMING_RETENTION_AUDIT_DAYS` (`rctiming.retention.audit-days`) | `730` | How long other audit rows stay; `0` keeps them for ever |
| `RCTIMING_RETENTION_CRON` (`rctiming.retention.cron`) | `0 0 3 * * *` | When the retention clean-up runs (server time); `-` turns it off. It also runs once at start-up (`rctiming.retention.run-on-startup`, default `true`), as a laptop may be off at 03:00 |
| `rctiming.network.addresses` / `rctiming.network.mdns-name` | empty | Addresses the About page shows (needed in a container) and the `.local` name advertised on the network |
| `rctiming.https.enabled` / `rctiming.https.port` | `false` / `8443` | Optional local HTTPS connector using the club's own certificate |
| `app.jwt.secret`, `app.jwt.access-token-ttl-ms`, `app.jwt.refresh-token-ttl-ms` | generated / 15 min / 7 days | Signing secret (a random `jwt-secret` file in the data folder when blank) and token lifetimes |
| `storage.local-path` / `storage.public-base-url` | database folder / `/storage` | Where uploaded images are kept and the URL they are served from |
| `rctiming.results-site.directory` / `rctiming.results-site.refresh-minutes` | empty / `5` | Folder the standalone results site is written to and how often it is rebuilt |

To restore, stop the app, then run:

```bash
java -jar app.jar restore /media/usb/rctiming/rctiming-20261004-220000-day-close.db
```

It finds the data directory the way the app does (`RCTIMING_DATA_DIR`, `--rctiming.database.data-directory=...`, or the default folder), checks the backup is sound, and moves the current database aside as `rctiming.db.before-restore-<time>`. Then it puts the backup in its place. It refuses while the app has the database open. When the app next starts, it migrates the restored database if it came from an older version. For the installed app, see [installing.md](installing.md#restoring-a-backup).

### Locked out of the admin account

Admins manage officials under **Admin → Officials**. If no admin can sign in, stop the app and run:

```bash
java -jar app.jar reset-admin-password admin@club.example
```

It finds the data directory the same way, asks for the new password twice, enables the account, gives it `ADMIN` if it had lost it, and signs it out everywhere. With no email it lists the admins. The change is logged as made from the command line. It never creates a database: with none in the data directory it stops. For the installed app, see [installing.md](installing.md#locked-out).

### Running it for real

The app runs on the venue laptop from the installer, which serves the UI, keeps its data in the per-machine folder and starts with the laptop; see [installing.md](installing.md). It is meant for the venue network only and is not deployed to the internet.

- Leave `JWT_SECRET` unset and keep the generated `jwt-secret` file private, or set it to a random 256-bit base64 value.
- Do **not** use `ddl-auto=update` or `create-drop`: Flyway manages the schema.

### Jackson 3

JSON is Jackson 3 (`tools.jackson`) throughout: Spring's REST and STOMP converters, the `JsonMapper` bean Boot builds, the `JsonTextConverter` mapper for the JSON columns, the YAML codecs and the tests. Only the annotations (`@JsonProperty`, `@JsonTypeInfo` and the like) keep the `com.fasterxml.jackson.annotation` package. Parse and write errors are the unchecked `tools.jackson.core.JacksonException`, so a `catch (IOException)` no longer sees them.

Jackson 3 changed some defaults. `config/JacksonConfig` sets them back, for the Boot mapper (as a `JsonMapperBuilderCustomizer`) and for `JsonTextConverter`, so the API output, the STOMP messages, the exported files and the JSON columns stay as Jackson 2 wrote them:

- properties keep their declared order (Jackson 3 sorts them alphabetically);
- `null` for a primitive reads as zero or `false` (Jackson 3 refuses it);
- text after the end of the JSON document is ignored;
- enums read and write by name, not `toString()`.

`JsonTextConverter` also stays strict about unknown properties and writes dates and durations as numbers, as before. One byte-level difference is left: `writeValueAsBytes` writes a character outside the Basic Multilingual Plane (an emoji, say) as its own four UTF-8 bytes, where Jackson 2 wrote a pair of `\u` escapes; any JSON parser reads both alike, and `writeValueAsString` is the same in both. A test that builds its own mapper uses `TestJson.mapper()` (or `yamlMapper()`) so it behaves the same as the app.

JJWT 0.13.0 has no Jackson 3 module (jwtk/jjwt issue 1029), so it reads and writes tokens with `jjwt-gson`. Gson takes the last of two duplicate names in a token header or claims, where `jjwt-jackson` refused them; a token still has to carry a valid signature, so this is no way in. When a JJWT release supports Jackson 3, swap that one dependency.

### Trying it out with the demo club

The `demo` profile loads the Wyvern RC Club (`db/demo/{vendor}`), and `simulate` runs the fake decoder its decoder settings point at:

```bash
./gradlew -PbundleFrontend :app:bootJar
java -jar app/build/libs/app.jar --spring.profiles.active=demo --rctiming.database.data-directory=/tmp/rctiming-demo
java -jar app/build/libs/app.jar simulate   # in a second terminal; no arguments plays the demo transponders 101 to 116
```

Open http://localhost:8080 and sign in as `admin@example.com` / `trial123`. The e2e suite runs against this setup. For an installed copy, see [trial-quickstart.md](trial-quickstart.md).

---

### Photo background removal

Removing the background of a headshot or car photo (#695) runs entirely in the browser, so it needs no internet at the track and nothing in the Java code or the installers beyond the files below. `frontend/src/lib/backgroundRemoval.ts` loads the model and the runtime lazily, the first time an official ticks "Remove the background" in `PhotoUploadField` (used by the headshot and car photo dialogs), and returns a PNG that goes through the existing upload endpoints, which already accept a PNG with transparency and re-encode it.

| Part | Where | Licence | Size |
|------|-------|---------|------|
| U²-Net-P model (`u2netp.onnx`, the small U²-Net, from the rembg release) | `frontend/public/models/`, served as `/models/u2netp.onnx` | Apache-2.0 | 4.6 MB |
| `onnxruntime-web` WebAssembly runtime | npm dependency, the `.wasm` is bundled by Vite | MIT | 14.8 MB (about 4 MB compressed) |

Together that adds about 20 MB to the jar and each installer (less once compressed), and no native library per operating system. The runtime uses one thread, because several need a cross-origin isolated page and the venue server is plain HTTP. To use a different model, replace the file and check the input size (`MODEL_SIZE`, 320) and normalisation in `backgroundRemoval.ts`; any U²-Net family model from rembg (for example `silueta.onnx`, 43 MB) drops in. A model whose licence forbids commercial use (RMBG-1.4, for example) must not be bundled. `BulkPhotoImportDialog` does the same for many files at once: `photoMatch.ts` matches each file name to a competitor or entry, and the pictures are saved one at a time through the same upload endpoints (so the same limits and audit rows apply). The RaceHub and CSV entry imports do not carry images today; bringing them in from those files is #699.

## Module structure

```
app/src/main/java/dev/monkeypatch/rctiming/
├── api/
│   ├── auth/            # Officials' sign-in and refresh
│   ├── admin/           # Admin controllers: club, tracks, classes, formats, events, entries,
│   │                    #   competitors, RaceHub import, championships, backups, results exports
│   ├── audio/           # Announcer voices and clips
│   ├── boards/          # Anonymous spectator boards and the overlay's race clock
│   ├── pub/             # Anonymous event schedule, results, championships, about
│   ├── racecontrol/     # Race lifecycle, marshal, referee, transponder swap, practice, live feed switch
│   └── setup/           # First-run setup wizard
├── domain/              # Entities, jOOQ repositories and write-side services
│   ├── club/ track/ raceclass/ format/ event/ race/ championship/ practice/
│   ├── competitor/      # Competitors (display name, RaceHub driver ID, BRCA number, club)
│   ├── entry/           # Entries with primary and secondary transponders, audit log
│   ├── racehub/         # Entry Export v1 import and class mapping
│   ├── transponder/     # Transponder swaps
│   └── user/ auth/      # Officials and refresh tokens
├── query/               # jOOQ read side: boards, championship standings,
│                        #   competitors, entries, events, race control, results export
├── service/             # Round generation, bump-up seeding, qualifying standings, result snapshots
├── timing/              # Decoder listener, live race state, race clock, STOMP broadcasts
├── practice/            # Open practice timing
├── livefeed/            # Live Feed v1 to a relay (docs/live-feed-v1.md)
├── resultsexport/       # Results Export v1 to RaceHub (docs/results-export-v1.md)
├── backup/              # Scheduled and on-demand backups, the restore command
├── infrastructure/      # Announcer (audio, tts), file storage, LAN addresses
├── persistence/         # Database choice, connection pools, converters; vendor code in vendor/
├── security/            # JWT sign-in, STOMP CONNECT check, security rules, reset-admin-password
└── config/              # Async, SPA serving, storage, TTS, WebSocket (STOMP on /ws/timing)
```

`decoder-protocol/` holds the pure RC-4 parser, and `decoder-simulator/` the fake decoder and the live feed test relay. `mapstruct-jooq/` is a small compile-time plugin for MapStruct (see "Mapping rows to entities" below); it is not part of the running app.

---

## Diagrams and Javadoc

Diagrams for the main flows, the race state machine and the domain model are in [developer/](developer/README.md). Change a diagram in the same pull request as the code it describes. They are developer docs, so they are not added to the docs site manifest. The same page says how to build the Javadoc, where CI keeps it and how to serve it at `/javadoc`.

## Running tests

```bash
make test-fast                         # app and simulator tests, using the committed jOOQ sources
./gradlew :decoder-protocol:test       # the protocol parser
./gradlew :app:test --tests "dev.monkeypatch.rctiming.timing.*"   # one package
cd frontend && npm test -- --run       # frontend unit tests
```

None of them needs Docker. Each backend test run creates a temporary SQLite database, shared by all integration test classes (one Spring context). `SqliteConcurrencyIT` writes laps while readers poll, and `CrashRecoveryIT` kills the app mid-session and checks no committed lap is lost. The Playwright end-to-end tests run against the demo club and the simulator; see [testing.md](testing.md#end-to-end-tests).

---

## Navigating the app

| Path | Access | Description |
|------|--------|-------------|
| `/login` | Public | Officials' sign-in |
| `/setup` | Public until set up | First-run setup wizard |
| `/events`, `/results/:raceId`, `/championships/:id` | Public | Event schedule, results and championship standings |
| `/boards/now-next`, `/boards/results`, `/boards/live-race`, `/boards/overlay` | Public | Spectator boards and the streaming overlay for OBS |
| `/about` | Public | Version and the addresses other devices can open |
| `/admin/*` | ADMIN / RACE_DIRECTOR / REFEREE | Admin panel. ADMIN only: club profile, tracks, formats, decoder, backups, results exports and officials (#132). Events and championships open to every official, but only an admin edits one (any official can still record a championship exclusion, a referee's disqualification); a race director also moves it through its day |
| `/admin/race-control` | ADMIN / RACE_DIRECTOR / REFEREE | Pick an in-progress event for race control |
| `/race-control/event/:id` | ADMIN / RACE_DIRECTOR / REFEREE | Race control cockpit |
| `/race-control/event/:id/walk-in` | ADMIN / RACE_DIRECTOR (REFEREE sees it, cannot add) | Add a walk-in entry |
| `/race-control/event/:id/transponders` | ADMIN / RACE_DIRECTOR / REFEREE | Transponder swap |
| `/race-control/event/:id/referee` | ADMIN / RACE_DIRECTOR / REFEREE | Referee timing view |
| `/race-control/event/:id/practice` | ADMIN / RACE_DIRECTOR / REFEREE | Open practice |
| `/print/meeting-guide`, `/print/admin-guide` | Public | Printable guides |

---

## Adding a doc to the docs site

The app serves the user-facing guides at `/docs` (#326). `docs/*.md` stays the only copy; the build imports the files listed for the site, and developer docs (this one, testing, architecture, requirements, api, plans) are not bundled.

To add a guide:

1. Write it in `docs/`. Link to other site docs as `installing.md#first-run`, never to a developer doc.
2. Add it to `DOC_MANIFEST` in `frontend/src/docs/manifest.ts` (slug, title, file, menu section). The list order is the menu order.
3. Add its path to the `import.meta.glob` list in `frontend/src/docs/sources.ts`.

`npm test` fails if a manifest entry has no file, if a file is bundled without an entry, or if a bundled doc links to a doc or heading that isn't in the site.

## Makefile targets

Run `make help` to see all targets. Quick reference:

| Target | What it does |
|--------|-------------|
| `make dev-start` | Start everything: backend (dev) + frontend (background), plus Piper if Docker is available |
| `make stop` | Kill backend and frontend processes, and stop Piper |
| `make up` | Start Piper (`docker compose up -d`); skipped without Docker |
| `make down` | `docker compose down` |
| `make clean-db` | Delete the dev SQLite database (re-runs all seeds on next start) |
| `make dev` | Backend only, foreground |
| `make dev-setup-test` | Backend without the dev seed data, to try the setup wizard from scratch |
| `make generate-db` | Regenerate the jOOQ sources from the migrations |
| `make ui` | Frontend only, foreground |
| `make build` | Compile the backend (no tests; runs jOOQ codegen only if its sources are missing) |
| `make test` | App and decoder simulator tests, regenerating jOOQ first |
| `make test-fast` | Tests skipping jOOQ codegen |
| `make simulator` | Run fake decoder in generative mode on :5100 |
| `make simulator-playback` | Replay a .dump file through the fake decoder |
| `make ui-build` | TypeScript check + production bundle |
| `make ui-lint` | ESLint |
| `make clean` | Stop everything, `./gradlew clean`, remove `frontend/dist` |
| `make installer` | Native installer for this system, with the UI built in (see [installing.md](installing.md#building-the-installers)) |

## Useful commands

```bash
# Compile check only
./gradlew :app:compileJava

# Generate jOOQ sources (needed after schema changes)
./gradlew :app:generateJooq
```


## Developer workflow

### Picking up a piece of work

1. Check [GitHub Issues](https://github.com/RCRaceHub/RCTimingControl/issues) for the next task
2. Read `CLAUDE.md` for architecture context before starting — it's the primary reference for any AI-assisted session
3. Read `docs/PROJECT.md` for requirements and `docs/REQUIREMENTS.md` for the full requirement list

### Making a change

1. Start the dev environment: `make dev-start`
2. Implement the change, following the patterns in `CLAUDE.md` (domain module and its jOOQ repositories for writes, query module for reads)
3. If adding a database column or table, add a Flyway migration (`V{next}__description.sql`) and re-run `./gradlew :app:generateJooq`
4. Write or update tests — backend integration tests in `app/src/test/`, frontend unit tests in `frontend/src/`
5. Run `make test-fast` (backend) and `npm test` in `frontend/` before pushing — add `./gradlew :decoder-protocol:test` if you touched the protocol parser

### Schema changes

Any new `V*__.sql` migration file requires jOOQ codegen to be re-run before the code will compile:

```bash
./gradlew :app:generateJooq
```

Flyway applies migrations automatically on backend startup — you do not need to run them manually.

Migrations live in one folder per database: `app/src/main/resources/db/migration/{vendor}/` (dev seeds in `db/seed/{vendor}/`, the demo club in `db/demo/{vendor}/`, test data in `app/src/test/resources/db/testdata/{vendor}/`). Until the first release on SQLite, the baseline may still be edited in place; after that, every change is a new migration.

### Keeping the database swappable

The database is chosen in one place. `rctiming.database.vendor` picks it, and `DatabaseConfig` (in `persistence/`) applies it to Flyway and jOOQ. The vendor-specific values themselves live in `persistence/vendor/DatabaseVendor`: the JDBC URL, connection settings (for SQLite: WAL, `synchronous=NORMAL`, foreign keys on, a 5 second busy timeout; the write pool also begins each transaction with `BEGIN IMMEDIATE`, so a transaction that reads first and writes later waits for the lock instead of failing at once with `SQLITE_BUSY`), how many write connections it allows, and the jOOQ dialect.

The app opens two pools on the database: a write pool (one connection for SQLite, used by Flyway and Spring transactions) and a read pool of `rctiming.database.read-connections` (default 4) whose connections refuse writes. jOOQ queries outside a transaction go to the read pool; inside a `@Transactional` one, read-only included, they share the writer. `@ReadTransaction` runs a read-only transaction on the read pool instead, so a query class's several statements see one snapshot without taking the write connection; the `query/` classes use it.

Column types the databases disagree on are mapped in Java, not by the schema: timestamps are `Instant` stored as UTC microseconds (`InstantMicrosConverter`, applied by the jOOQ codegen), date-only values are ISO text, and JSON columns are text read through subclasses of `persistence/convert/JsonTextConverter` (such as `domain/format/RaceFormatConfigConverter`).

Everywhere else the code stays database-neutral:

- Read queries use the jOOQ DSL only: no `DSL.sql(...)`, `field("...")` or other plain-SQL strings.
- Repositories use the same jOOQ DSL, extending `persistence/JooqRepository`.
- A vendor-specific statement that can't be avoided goes behind an interface in `persistence/vendor/`, with one implementation per database.

`PersistencePortabilityTest` scans the main sources, and the compiled classes for any jOOQ method marked `@PlainSQL` (so SQL in a variable is caught too), and fails the build if any of this slips in.

### Keeping the code in step with the schema

With Hibernate gone, nothing checks the schema against the entities at startup (there is no `ddl-auto: validate`). Three things catch a mismatch instead:

- **Generated sources.** Compiling `:app` runs Flyway's migrations into a scratch SQLite file and regenerates the jOOQ classes in `app/src/generated/jooq` from it. A renamed or removed column then breaks the build wherever a repository or query uses it. Commit the regenerated sources with the migration; CI builds with `-x generateJooq`, so it compiles against the committed copy.
- **Save-and-reload tests.** Each repository has an integration test that saves an entity with every field set, reloads it and changes every field (`persistence/RoundTrip.assertSavedAndReloaded`). A column missing from `toRecord` or `toEntity` fails it. A new column needs a line in the repository and in its test.
- **Real migrations in tests.** The integration tests run against a SQLite file built by the same migrations, so constraints and defaults are the real ones.

### Mapping rows to entities

A repository moves an entity to and from its table's record with a MapStruct mapper instead of a setter per column. The mapper is a package-private interface next to the repository, such as `ClubProfileMapper`; MapStruct generates its implementation during compilation, and the repository gets it from `Mappers.getMapper`.

- `toEntity(record)` and `toRecord(entity, @MappingTarget record)` match properties by name. `id` is ignored in `toRecord`, because `JooqRepository` handles it.
- `unmappedTargetPolicy = ERROR` makes a column with no entity property (or the reverse) a compile error, so the mapper keeps the safety the hand-written code had. The save-and-reload tests still apply.
- Where names differ, add an `@Mapping` (`DecoderLoopMapper` maps `isScoringLoop` to `scoringLoop`). Enums stored as text convert on their own. A JSON column uses a `default` method in the mapper that calls its `JsonTextConverter`, as `ClubProfileMapper` does for the audio settings.
- `mapstruct-jooq/` makes MapStruct ignore jOOQ's fluent `value1(..)` methods, which it would otherwise take for settable properties. It does this for every mapper in `:app`, so a mapper onto a fluent or builder-style target would need its own handling.
- MapStruct only uses public setters, and ignores a package-private one without warning. Where an entity keeps `setId` (or another setter) package-private, the repository's `toEntity` sets that value itself after the mapper runs, as `PracticeSessionRepository` does for its id and `EventRepository` does for the results export revision; the save-and-reload tests catch a value left out.
- Two repositories still map by hand because their entities are built by constructor: `OfficialAuditLogRepository` and `UnknownTransponderLinkAuditRepository`. `UserRepository` loads roles after the mapper runs, and `TrackRepository` leaves its loops and thresholds to their own repositories; both ignore those properties in the mapper.

Moving to another database means:

1. Add a constant to `DatabaseVendor` with its migrations folder name, jOOQ dialect, JDBC URL, connection settings and write-connection limit.
2. Add baseline migrations under `db/migration/{vendor}/` (and the dev seeds and test data folders).
3. Add the JDBC driver and Flyway database support to `app/build.gradle.kts`.
4. Set `rctiming.database.vendor`, and point the jOOQ codegen in `app/build.gradle.kts` at the new migrations.
5. Run the whole test suite against it.

### Backend package layout

- **`domain/<area>/`** holds an area's entities, their `JooqRepository` subclasses and the write-side services. A service that must touch tables with no entity of its own (bulk rewrites, audit logs) gets a plain `@Repository` in the same package, such as `CompetitorErasureRepository`.
- **`query/`** holds the read side: projections and aggregations, with `@ReadTransaction`.
- **`service/`** holds logic that spans several areas (round generation, bump-up seeding, result snapshots).
- **`api/<area>/`** holds controllers and their `dto/` records. A controller calls one service or query class.
- **No `DSLContext` outside repositories and query classes.** A service or controller that needs SQL asks a repository for it. The one exception is `security/ResetAdminPasswordCommand`, which runs with the app stopped on its own connection.
- **A repository for a table keyed by something other than an `id`** (`ChampionshipPointsScaleRepository`, keyed by championship and position) does not extend `JooqRepository`, and says so in its Javadoc.

### Frontend conventions

- **Query keys** come from the factories in `hooks/publicQueryKeys.ts` (anything anonymous, and the boards) and `hooks/admin/adminQueryKeys.ts`. Never write a literal `queryKey: ['...']` in a page or hook; add a factory entry. Boards keys share the `'boards'` root so a race's state change refreshes them all.
- **Queries and mutations live in hooks**, not in pages or components. A page calls a hook from `hooks/` (`useBoardQueries.ts`, `hooks/admin/useAdmin*.ts`), which owns the key, the fetcher, the polling interval and `enabled`. Where a screen needs a different interval or condition, the hook takes it as an option rather than the page rebuilding the query. The boards are converted; the race-control panels and the remaining admin pages move over as they are touched.
- **Exports:** a page that a route loads lazily, and a layout, has a `default` export. Everything else (components, hooks, helpers) is a named export.
- **Tests** sit beside the file they test, as `Name.test.tsx`. There are no `__tests__` folders.
- **Small screens:** the smallest screen to support is a 1920x1080 laptop, which at 125% or 150% Windows scaling gives a browser viewport as small as 1280x600. A page must not scroll sideways at that size. Dense screens tighten under the `short` variant (viewport 820px tall or less, defined in `index.css`), as race control does for its header, run order and live timing rows.
- **Forms:** a form with more than one field, or any validation, uses React Hook Form with a Zod schema (`zodResolver`). A single-field inline editor may use `useState`.

### Submitting a PR

```bash
gh pr create
```

Reference the GitHub Issue number in the PR description (`Closes #N`). Include the docs for the change in the same PR: Javadoc on new or changed classes and methods, the `docs/developer/` diagrams if a flow or model changed, and the user guide if the visible behaviour changed. See [developer/](developer/README.md). PRs should include passing tests — `make test` runs the full suite, including the decoder simulator tests.

### Working with Claude Code

Open the project in Claude Code from the repo root. `CLAUDE.md` is loaded automatically and gives Claude the full architecture context. For a new feature or bug fix, describe the GitHub Issue and Claude will use the existing patterns.

Key docs to reference in a session:
- `CLAUDE.md` — stack, architecture, module boundaries, rules
- `docs/PROJECT.md` — what the system is and its requirements
- `docs/REQUIREMENTS.md` — full requirement list: current, planned and removed, with the issue behind each change
- `docs/architecture.md` — deeper architecture notes
- `docs/plans/archive/v1-planning/phases/` — the original phase plans, archived as a historical decision log per phase (useful if you hit an unexpected behaviour, but not current design)
