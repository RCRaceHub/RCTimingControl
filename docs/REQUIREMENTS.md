# Requirements: RCTimingControl

**Defined:** 2026-04-15
**Updated:** 2026-10-06
**Core Value:** Officials run a full race meeting from a laptop at the track, on Windows, macOS or Linux, with live timing fed directly from AMB/MyLaps hardware and no dependence on the internet. Entries come in from RaceHub in one file, and the club's results and championships stay on the laptop, backed up.

> **Status.** Every phase of the original v1 plan is complete, so the v1 requirements still in force are ticked. In October 2026 RCTC became local-only timing for the RaceHub suite (tracking issue #8): RaceHub owns booking, racer accounts and entry, and RCTC runs on a laptop at the venue. Requirements that plan removed are struck through with the issue that removed them, requirements it changed carry a **Changed** note, and the requirements it added are under [Local-only timing](#local-only-timing-8). Removed requirements keep their IDs so older references still resolve.

## v1 Requirements

### Authentication

- ~~**AUTH-01**: Racer can self-register with email and password~~ **Removed** (#18): only officials have accounts, and admins create them.
- [x] **AUTH-02**: Racer can log in and remain logged in across browser sessions **Changed**: only officials sign in (#18). An HttpOnly refresh cookie keeps them signed in.
- ~~**AUTH-03**: Racer can reset password via email link~~ **Removed** (#18): only officials have accounts, which an admin creates; there is no emailed reset. An admin sets an official's password instead (AUTH-06), and a locked-out club resets an admin's from the command line (AUTH-07).
- [x] **AUTH-04**: Staff users can log in with elevated privileges; access to admin panel, race control, and referee tools is gated by role
- [x] **AUTH-05**: Staff accounts can be assigned one or more roles (`ADMIN`, `RACE_DIRECTOR`, `REFEREE`); roles are stackable — a user may hold any combination simultaneously; `ADMIN` covers club config and event setup, `RACE_DIRECTOR` covers race control client, `REFEREE` covers penalties and incident management
- [x] **AUTH-06**: An admin manages officials after setup: adds one, changes roles, sets a password, and disables or re-enables an account (never deletes it). The last admin who can sign in can't be disabled or lose `ADMIN`, an admin can't disable themselves, a disabled official can't sign in or refresh, and every change is logged with who made it (#61)
- [x] **AUTH-07**: A club with no admin who can sign in resets an admin's password from the laptop with `RCTimingControl reset-admin-password` (#61)

### Racer Profile & Equipment

- ~~**RACER-01**: Racer can create and edit their profile (name, contact details)~~ **Removed** (#18): competitors have no account; RaceHub owns racer identity, garage and entry.
- ~~**RACER-02**: Racer can add and edit cars (name, primary class, notes); primary class is used for filtering entries and is not a hard constraint~~ **Removed** (#18): competitors have no account; RaceHub owns racer identity, garage and entry.
- ~~**RACER-03**: Racer owns transponders independently of cars; transponder is selected at entry time, not permanently assigned to a car~~ **Removed** (#18): competitors have no account; RaceHub owns racer identity, garage and entry.
- ~~**RACER-04**: Racer can view their own entry history and past race results~~ **Removed** (#18): competitors have no account; RaceHub owns racer identity, garage and entry.
- [x] **RACER-05**: Transponder numbers are unique system-wide; duplicate registration is rejected and flagged for admin resolution **Changed**: transponders belong to an entry, a primary and an optional secondary, and are not unique system-wide. A number already used in the event is accepted with a warning (see RACER-09), check-in refuses a number another competitor holds (#19), and a passing that matches more than one entry goes to the referee (TIMING-08) (#14).
- ~~**RACER-06**: Cars are archived not deleted; archived cars preserve race history and cannot be used for new entries~~ **Removed** (#18): competitors have no account; RaceHub owns racer identity, garage and entry.
- [x] **RACER-07**: Entry records a transponder snapshot at submission time; subsequent changes to the racer's transponder list do not affect existing entries **Changed**: the snapshot is the transponders in the RaceHub export, or those typed in for a walk-in (#15, #17).
- [x] **RACER-08**: Race director can update the transponder assignment on an entry before race start (equipment swap); change is audit-logged **Changed**: the check-in desk swaps a transponder on the day, with an audit entry (#19).
- [x] **RACER-09**: System warns when the same transponder is used in multiple entries at the same event (potential timing conflict if classes run simultaneously) **Changed**: the warning shows on import and when adding a walk-in (#14).
- ~~**RACER-10**: Admin can define and manage car tag categories (default set installed: Chassis, ESC, Motor, Servo, Battery, Body, Tyres)~~ **Removed** (#18): car tags went with racer garages; RaceHub owns car details.
- ~~**RACER-11**: Racer can add free-text values for any tag category to each of their cars~~ **Removed** (#18): competitors have no account; RaceHub owns racer identity, garage and entry.
- ~~**RACER-12**: Racer has an ability rating (0–100) per racing class, used to seed qualifying heats; updated automatically from championship points after each round~~ **Removed** (#18): ability ratings were removed with racer profiles; heats are seeded in entry order.
- ~~**RACER-13**: Racer profile stores zero or more governing body membership numbers, keyed by governing body code (e.g. `BRCA → 12345`, `ROAR → 67890`); admin can add or edit these on behalf of a racer~~ **Removed** (#18): racer profiles are gone. A competitor keeps the BRCA number RaceHub sends.
- ~~**RACER-14**: If the club has a governing body affiliation with `membershipRequired = true`, entry submission is blocked for racers without a matching membership number on their profile; admin can override on a per-entry basis with an audit log entry~~ **Removed** (#18): RaceHub checks membership at booking.

### Club Configuration

- [x] **CLUB-01**: Admin can configure zero or more governing body affiliations for the club; each affiliation records a code (e.g. `BRCA`), a display name (e.g. `British Radio Car Association`), and a `membershipRequired` flag; if `membershipRequired` is true, racers must have a matching membership number on their profile to submit an entry to any event **Changed**: affiliations are still configured, but `membershipRequired` is stored and not checked, since RaceHub handles membership at booking (#18).
- [x] **CLUB-02**: Admin can configure the club profile: name, contact details (email and phone), website URL, GPS coordinates (latitude/longitude) for public map display, IANA time zone (e.g. `Europe/London`), and logo (SVG and PNG formats both storable); profile data is used in the public portal and on printed/exported results

### Tracks

- [x] **TRACK-01**: Admin can define and manage tracks (name, venue/location notes, optional track length); a club may have multiple tracks
- [x] **TRACK-02**: Each track has a configurable minimum lap time per racing class; passing events with crossings faster than this threshold are ignored (prevents loop double-counting and track-cutting); a track-wide default applies to all classes unless a class-specific override is set. The Tracks page sets the track-wide default; a class-specific one is set through `POST /admin/tracks/{id}/thresholds`. Limits are read when a race's live timing starts.
- [x] **TRACK-03**: Each track has a configurable maximum last lap time per racing class; a timed race finishes automatically if no lap counts within this window after the clock expires (prevents infinite wait for broken cars). A race with a fixed number of laps is left for the race director.
- [x] **TRACK-04**: Admin can configure the decoder loops associated with a track; each loop has a decoder-assigned loop ID, a display name, and a type (`FINISH_LINE`, `CHICANE`, `OTHER`); one or more loops are designated as primary scoring loops; crossing events on non-primary loops are recorded but excluded from lap counting; the model accommodates multiple loops and multiple decoders per track (multi-decoder operation is deferred to post-v1)

### Racing Classes

- [x] **RACECLASS-01**: Admin can define and manage racing classes (name, description)

### Event Management

- [x] **EVENT-01**: Admin can create an event with a name, date, and venue
- [x] **EVENT-07**: Admin associates an event with a configured track; the track's lap time thresholds (TRACK-02, TRACK-03) are meant to apply to all races at that event, but are not applied yet.
- [x] **EVENT-02**: Admin can add racing classes to an event and assign a race format to each class
- ~~**EVENT-03**: Racer can enter an event online via the portal, selecting their class, car, and transponder~~ **Removed** (#18): entries come from the RaceHub import or are added as walk-ins.
- [x] **EVENT-04**: Public event schedule is visible without login **Changed**: "published" means the event shows on the public schedule. Walk-ins can be added until the event is completed (#17).
- [x] **EVENT-05**: Events follow a state machine: DRAFT → PUBLISHED → OPEN → ENTRIES_CLOSED → IN_PROGRESS → COMPLETED; invalid transitions are rejected
- [x] **EVENT-06**: Admin can combine two or more classes into a single race (run together, score separately) for events with low class turnout
- [x] **ENTRY-01**: Entry has a lifecycle (PENDING → CONFIRMED → WITHDRAWN); racer can withdraw before entries close **Changed**: officials or a RaceHub import withdraw an entry; a withdrawn entry is never deleted and keeps its race history (#15, #17).
- [x] **ENTRY-02**: Admin can view and manage all entries per event and per class
- [x] **ENTRY-03**: Each race entry (RaceEntry) carries a car number that identifies the driver for that race. Car numbers are consistent within a phase but change between phases: the round generator assigns numbers 1–N (entry order) when qualifying rounds are created, and re-numbers from qualifying results (fastest qualifier = 1) when finals are seeded — re-numbering cannot happen until qualifying results are known. Car number is distinct from grid position (grid position = where on the start line the driver stands; car number = their identifier). Car number is unique per class per race. The race director can manually reassign numbers before a round starts.

### Race Format Configuration

- [x] **FORMAT-01**: Admin can configure a standard timed race (duration, start type, qualifying type); lap time thresholds are meant to be set at the track level (see TRACK-02, TRACK-03; not applied yet)
- [x] **FORMAT-02**: Admin can configure bump-up finals (qualifying heats, heat duration, best heats count, grid size, bump spots — default 2); number of finals is calculated automatically from actual entry count at event time
- [x] **FORMAT-04**: Admin can configure points finals (qualifying heats, finals count, final duration)
- [x] **FORMAT-05**: Race format config is type-discriminated; only fields valid for the chosen type are accepted and required fields are validated
- [x] **FORMAT-06**: Assigning a format to an event class takes a snapshot of the template at assignment time; subsequent template edits do not affect existing events
- [x] **FORMAT-07**: Admin can override individual format config fields at the event-class level without modifying the underlying template
- [x] **FORMAT-08**: Start type is configurable per class per phase: STAGGER (car numbers called sequentially at configured interval), GRID (all start simultaneously on buzzer), ROLLING (time starts on first crossing after buzzer); qualifying and finals may use different start types
- [x] **FORMAT-09**: Qualifying type is configurable: FTQ (fastest time across all heats), ROUND_BY_ROUND (points per finishing position each round), FASTEST_LAP (best single lap from any heat), CONSECUTIVE_LAPS (best N consecutive laps)
- [x] **FORMAT-10**: Gap between successive races is configurable per class
- [x] **FORMAT-11**: Stagger start interval is configurable (default 1 second between car number calls)
- [x] **FORMAT-12**: For bump-up events, the system automatically calculates the number of finals and generates final grid assignments from qualifying results using the configured grid size and bump spots; the lowest final may have fewer than grid_size racers
- [x] **FORMAT-13**: Championship points for bump-up events are assigned from a single class-wide finishing order: A-final positions fill first, then non-promoted finishers from each lower final in finish order cascading down
- [x] **FORMAT-14**: Race format configurations can be exported as JSON and re-imported; imported configs are validated against the schema for the declared format type before saving; this allows format templates to be authored in external tools and shared between clubs

### Local Forwarder (removed in #10)

- ~~**FORWARDER-01**: A separate forwarder application runs on the club's local network, connects to the AMB decoder, and forwards timing data to the cloud service; the cloud service cannot reach the decoder directly~~ **Removed** (#10): the app reads the decoder directly; there is no cloud service to forward to.
- ~~**FORWARDER-02**: Forwarder connects to the decoder via TCP using the AMB P3 binary protocol (0x8E/0x8F frame delimiters, TLV body, 0x8D byte-stuffing); serial transport is not required for v1~~ **Removed** (#10): the app reads the decoder directly; there is no cloud service to forward to.
- ~~**FORWARDER-03**: Forwarder streams decoded timing events to the cloud service via gRPC bidirectional streaming; the cloud can send RESEND requests back to the forwarder over the same stream~~ **Removed** (#10): the app reads the decoder directly; there is no cloud service to forward to.
- ~~**FORWARDER-04**: Forwarder is implemented as a Java Gradle submodule within the same repository, sharing domain model classes with the main application~~ **Removed** (#10): the app reads the decoder directly; there is no cloud service to forward to.
- ~~**FORWARDER-05**: Forwarder authenticates with the cloud service using a pre-configured API token before streaming begins~~ **Removed** (#10): the app reads the decoder directly; there is no cloud service to forward to.

### Live Timing & AMB/MyLaps Integration

- [x] **TIMING-01**: Cloud service receives timing data from the forwarder via gRPC; the forwarder owns the AMB P3 TCP connection and all protocol parsing **Changed**: the app reads the decoder over TCP itself, on a background thread; the forwarder and gRPC link are gone (#9, #10).
- [x] **TIMING-02**: Forwarder auto-reconnects to the decoder on TCP connection loss; WATCHDOG record absence is the primary lost-connection signal; both forwarder↔decoder and forwarder↔cloud connection status are visible in the race control UI **Changed**: the decoder listener reconnects on its own and publishes whether it is connected to `/topic/system/decoder-status`, shown in race control (#9).
- [x] **TIMING-03**: Live lap times, positions, and gaps are displayed in the browser during a race via WebSocket
- [x] **TIMING-04**: Lap times use the RTC_TIME field from PASSING records (decoder's embedded hardware timestamp), not server receipt time **Changed**: this applies to P3, which is deferred. RC-4 text decoders send seconds since power-on, so lap timestamps are anchored to server time at the first record.
- [x] **TIMING-05**: The decoder integration uses a defined TimingSource interface; switching to a new protocol requires only a new implementation class with no changes to race control or timing logic **Changed**: protocol parsing lives in the Spring-free `decoder-protocol/` module, so a new protocol is a new parser there.
- ~~**TIMING-06**: Forwarder performs the FIRST_CONTACT handshake with the decoder on initial connection before passing data flows~~ **Removed** (#10): this was the forwarder's P3 handshake. P3 is deferred, and RC-4 has no handshake.
- ~~**TIMING-07**: Forwarder monitors PASSING_NUMBER for gaps; detected gaps trigger a RESEND request to the decoder~~ **Removed** (#10): this was the forwarder's P3 resend. P3 is deferred, and RC-4 has no resend.
- [x] **TIMING-08**: At race start the system builds an in-memory transponder→entry map for the race; passing events with unregistered transponders are logged and surfaced in the race control UI **Changed**: the map holds both transponders of each entry (#14). A passing that matches no entry, or more than one, goes to the referee's unknown-transponder flow and its laps are held back until linked.

### Race Control

- [x] **CTRL-01**: Race director can start and stop a race from the browser race control client
- [x] **CTRL-02**: Race control displays the grid call — which cars are due on track next
- [x] **CTRL-03**: Race director can add or remove marshal laps for on-track incidents, with a full audit trail
- [x] **CTRL-04**: Race results can be exported as a printable/PDF sheet at the venue
- [x] **CTRL-05**: Server enforces race state machine (PENDING → GRID → RUNNING → FINISHED); conflicting commands from multiple browser sessions are rejected
- [x] **CTRL-06**: Unknown transponder passings can be retrospectively linked to an entry by the race director, with full audit trail
- [x] **CTRL-07**: Race control displays the marshal list for the current race (the drivers who were in the previous race)
- [x] **CTRL-08**: Race director can abandon a race in progress; results up to the abandonment point are saved and the meeting advances normally
- [x] **CTRL-09**: Race director can skip to or re-run a specific race and round number
- [x] **CTRL-10**: Every race lifecycle command (call the grid, start, stop, resume, finish, abandon, restart) is audit-logged with the official who gave it and the status before and after; a restart also records what it discards (the race's times, its stored result and the live timing) (#139)

### Audit

- [x] **AUDIT-01**: Every action an official takes that changes data, and the system's own background changes, is written to the audit log with who did it (or `system`, or the command line user), when, what it was done to and, where it applies, the value before and after. The row is written in the same transaction as the change, so a refused change leaves no row, and `AuditCoverageIT` fails when a data-changing endpoint is neither audited nor listed with a reason. Recorded: sign-in, sign-out and refresh (#152); race control (#165); championships, events and their classes, the run order and finals; entry imports and the entry feed; competitor merges and check-ins; club settings, tracks, classes and race formats; manual backups, results-export retries and practice sessions; referee actions and transponder links; bump-ups (as the system) and database restores (as the command line user, written when the app next starts); see #139. Admins read the log through `GET /api/v1/admin/audit` (filter by entity, action, official, event, race and date; paged); there is no screen for it yet. A race's own history (lifecycle, penalties, incidents, marshal laps and links, read from the race tables and the audit log) shows under the referee view (#140). An entry's own history (walk-in, check-in, transponder swaps, withdrawal, merges) shows to admins on the entry list, though changes made by importing an entry file are recorded per import, not per entry (#140). The officials' history pages through everything, newest first, and each competitor's changes are readable on their own page. The command line password reset is in the audit log too, as the operating system user who ran it (#140)

### Audio Announcements

- [x] **AUDIO-01**: Race control browser produces voice announcements throughout the meeting using the Web Speech API; all announcement types are individually configurable on/off
- [x] **AUDIO-02**: Countdown announcements fire at configurable intervals before each race (default: 10m, 5m, 2m, 1m, 30s) announcing race number and time remaining
- [x] **AUDIO-03**: In STAGGER start mode, each competitor on the grid is called by name at the configured stagger interval (entries have no car number); each driver starts when their name is called. The call is one Piper clip per entry, played in turn; the browser voice speaks it if the clip isn't ready in a few seconds
- [x] **AUDIO-04**: Each lap crossing produces a high-pitched beep if the driver is improving on their best result, low-pitched otherwise
- [x] **AUDIO-05**: When a race ends, one "Race finished" announcement is made (there is no per-driver finish announcement)
- [x] **AUDIO-06**: Running order is announced at 2-minute intervals for the first 10 minutes of a race, then at 5-minute intervals
- [x] **AUDIO-07**: Admin can enable or disable individual announcement types from the settings panel
- [x] **AUDIO-08**: When a racer profile is created or updated, the server generates a TTS audio clip for the racer's name using a configured TTS provider (e.g. Google Cloud TTS) and stores it; the clip is regenerated if the display name or phonetic spelling changes **Changed**: there are no racer profiles. Name clips are generated with Piper TTS from the competitor's display name when a race reaches `GRID` (see AUDIO-09).
- [x] **AUDIO-09**: When a race transitions to `GRID` state, the server pre-generates audio clips for all predictable announcements for that race: a grid call by name for each entry (stagger start), the countdown intervals, and the race-finished announcement; clips are cached and ready before the race starts
- [x] **AUDIO-10**: Pre-generated audio clips are served via HTTP; the race control client fetches and locally caches all clips for the current race during grid preparation before the race starts
- [x] **AUDIO-11**: If a pre-generated clip is unavailable at playback time, the client falls back to Web Speech API synthesis; clip unavailability is non-blocking and never prevents a race from running
- ~~**AUDIO-12**: Racer profile includes an optional **phonetic spelling** field for their display name, editable by the racer and admins; if set, it is used as the TTS input instead of the display name~~ **Removed** (#18): phonetic spellings were part of racer profiles.
- ~~**AUDIO-13**: Racer can preview their generated name clip from their profile and select a preferred TTS voice from the voices available for the configured provider; the voice preference is stored per racer and used for all announcements of their name; admin configures the system default voice~~ **Removed** (#18): name clip previews and voice choice were part of racer profiles. Admins still set the voice.
- ~~**AUDIO-14**: Display name and phonetic spelling fields are screened against a configurable profanity blocklist before saving; if a match is found the save is rejected with a validation error informing the racer their submission contains inappropriate content; admins can extend the blocklist with club-specific terms~~ **Removed** (#30): names were screened as racers typed them into their own profiles. Racer accounts went in #18, so the blocklist and its admin screen were removed; RaceHub, where racers now enter their names, is the place to screen them.
- ~~**AUDIO-15**: Admin can review any racer's phonetic spelling, override it, or clear the generated clip to force regeneration~~ **Removed** (#18): there are no racer phonetic spellings to review.
- [x] **AUDIO-16**: A competitor has an optional **spoken name** ("say as"), set by an admin, race director or referee on the Competitors screen or at the check-in desk, with a play button to hear it in the club's voice before saving. When set, it is the speech text for every announcement of that name (grid call, running order and the browser fallback); otherwise the display name is spoken after the tidying in AUDIO-17. It belongs to the competitor, so it carries over to later meetings, and a RaceHub or CSV re-import never changes it. Each change is recorded with who made it and the old and new wording, and admins can see that history. Replaces AUDIO-12 (#119)
- [x] **AUDIO-17**: A name with no spoken name is tidied before it is spoken, in one place used by every announcement: text in brackets (a nickname or club tag) is left out, emoji and stray symbols and punctuation are dropped, spacing is collapsed, and names in ALL CAPITALS (and capitalised words of three letters or more in a mixed-case name; initials such as "AJ" stay) are spoken in normal capitalisation. A spoken name is always used exactly as typed. It changes only what is spoken, never what is displayed or exported (#120)

### Race Official Views

- [x] **OFFICIAL-01**: Race steward has a dedicated view showing live proximity alerts (which cars are closing on others)
- [x] **OFFICIAL-02**: Race steward view highlights backmarker situations (lapped cars approaching leaders)
- [x] **OFFICIAL-03**: Race referee can raise an incident report against a specific car during or after a race
- [x] **OFFICIAL-04**: Race referee can apply a lap or time penalty to a car, which immediately updates live standings

### Practice

- [x] **PRACTICE-01**: System supports timed open practice sessions using the decoder; live lap times are displayed and results are printable after the session
- [x] **PRACTICE-02**: Practice display shows each racer's best run of N consecutive laps (configurable) to indicate sustained pace, not just best single lap
- [x] **PRACTICE-03**: Practice and a live race share the one decoder, so they never run together: a practice session can't be started while a race is running, and a race starting or resuming stops any running practice session (recorded in the audit log as the system)
- [x] **PRACTICE-04**: Practice laps are grouped into runs: a transponder quiet for longer than the session's pause (default 3 minutes, set when the session is created) starts a new run, and the pause is never counted as a lap. Every lap is kept; the best-N average (N set per session, default 5) is taken within one run (#151)
- [x] **PRACTICE-05**: An official can name a transponder in a practice session as an existing competitor, or as a new walk-in competitor made on the spot. The name is saved with the session, works with no event, and also names the transponder's earlier laps; the new competitor can be matched to an entry later (#151)
- [x] **PRACTICE-06**: A day view adds each driver's practice up across every session that started on a date in the club's time zone: sessions, runs, laps, best lap and the best-N average taken within one run. A driver is the competitor the transponder was named as, else the bare transponder (#151)

### Championship & Scoring

- [x] **CHAMP-01**: Admin can configure a championship with "best X from Y rounds" scoring (default: best 4 of 6)
- [x] **CHAMP-02**: Championship scoring handles DNF, DNS, and DQ correctly (all count toward Y rounds attended; DQ/DNS score zero points)
- [x] **CHAMP-03**: Separate championship standings per racing class
- [x] **CHAMP-04**: Admin can configure the points scale (points per finishing position) per championship
- [x] **CHAMP-05**: Public championship standings table is live on the web
- [x] **CHAMP-06**: Championship can score from qualifying results, final results, or both; if both, points from each source are summed per round
- [x] **CHAMP-07**: Championship can award a configurable bonus point to the top qualifier (TQ) per class per round: a driver who tops qualifying in two classes, or at two events, earns it for each (#133)
- [x] **CHAMP-08**: Championship can award a configurable bonus point to the A-final winner per class per round, earned once per class and event (#133)
- [x] **CHAMP-09**: Individual drivers can be excluded from championship points for a specific round (DQ, non-eligible equipment, factory driver), and earns no TQ or A-final bonus for that round either; exclusion is audit-logged
- [x] **CHAMP-10**: Championship standings can be displayed in best-to-worst order per driver to surface drop scores and remaining title contenders

### Results

- [x] **RESULT-01**: Final race results are published after each race
- [x] **RESULT-02**: Results correctly reflect any marshal lap adjustments and penalties applied, including time penalties and corrections made after the race finishes (#63)
- [x] **RESULT-03**: Per-racer result history is viewable on the racer's portal page **Changed**: results group by competitor on the public pages, since there is no racer portal (#12, #13).
- ~~**RESULT-04**: Printed/PDF results optionally display a racer's car tag values beneath their name; controlled by an admin display setting~~ **Removed** (#18): car tags went with racer garages, and no results show them.
- [x] **RESULT-05**: Result records include full individual lap time data (every lap, not just totals and best lap)

### Local-only timing (#8)

- [x] **IMPORT-01**: An admin imports RaceHub's Entry Export v1 JSON file into an event; the file carries no contact, date of birth, guardian or payment data (#15)
- [x] **IMPORT-02**: Each imported entry is matched by its RaceHub `entry_id` and applied only when its `entry_version` is newer than the one already imported, so a re-import is safe (#15)
- [x] **IMPORT-03**: An entry withdrawn in RaceHub is marked withdrawn, never deleted (#15)
- [x] **IMPORT-04**: RaceHub classes map to the event's classes by name, with a per-event override (#16)
- [x] **IMPORT-05**: A dry run previews every change; an import with unmapped classes or invalid rows saves nothing (#15, #16)
- [x] **IMPORT-06**: An admin imports an RC-Timing style driver CSV into an event. The preview shows new, changed (old and new values), unchanged and missing entries; only the changed entries and missing entries the official picks are updated or withdrawn, and walk-ins and entries from other sources are never withdrawn (#39)
- [x] **IMPORT-07**: An admin sets a URL (and optional access token) an event fetches Entry Export v1 from, on demand or every few minutes. A new revision that imports cleanly is applied automatically; anything blocked waits for an official to review. The token is stored encrypted and never shown again (#42)
- [x] **WALKIN-01**: An admin or race director adds a walk-in entry by hand, with a competitor, class and transponders, until the event is completed (#17)
- [x] **WALKIN-02**: A walk-in name typed by hand that matches an existing competitor (same name, ignoring case and spacing, from any source) is refused until the official picks that competitor or confirms it is a different person, so one driver is not created twice (#123)
- [x] **COMPETITOR-01**: A competitor is the person an entry belongs to (display name, RaceHub driver ID, BRCA number, home club) and has no login (#12)
- [x] **COMPETITOR-02**: Results, live timing and championship standings group by competitor across meetings (#13)
- [x] **COMPETITOR-03**: An admin can merge a duplicate competitor into the one to keep, after a preview of what moves (entries, events, championship exclusions) and any warnings. Entries and exclusions move in one transaction, each moved entry gets an audit record, and the duplicate is deleted. The kept competitor keeps its own name and fills a missing BRCA number, home club and spoken name from the duplicate. The RaceHub id stays on the kept competitor so the next import still finds it; two different RaceHub drivers, or two active entries in the same class of an event, refuse the merge. The Competitors screen lists possible duplicates (same name or BRCA number) (#123)
- [x] **COMPETITOR-04**: An admin can download everything RCTC holds about one competitor as JSON, and separately anonymise them: name, BRCA number, RaceHub id, home club and spoken name are removed, including from stored results, queued RaceHub exports and history rows, while laps, results and championship points stay. Both are audited without the removed data; anonymising is refused while the competitor is in a live race (#263)
- [x] **PRIVACY-01**: The club has a privacy notice at `/privacy`, open to anyone, that an admin can edit and that defaults to standard text with the club's name and contact details. It is linked from the public pages, the sign-in page and the check-in desk (#267)
- [x] **TRANSPONDER-01**: Each entry has a primary and an optional secondary transponder; a number already used in the event is accepted with a warning, not refused (#14)
- [x] **CHECKIN-01**: A check-in desk marks competitors present, accepts barcode input and swaps transponders on the day with an audit entry (#19) **Changed**: a later re-import keeps a swapped number and flags the booking's different number without blocking (#50)
- [x] **CHECKIN-02**: RCTC's check-in is the only record of who has arrived on the day; RaceHub's arrival mark is imported and shown read-only, and never checks anyone in (#92, O2)
- [x] **COMPETITOR-05**: An admin or race director can set a competitor's public name to full, initials only or hidden (the car number stands in). It applies to the results page, boards and overlay, championship standings, live timing and the live feed, is audited, and is not changed by imports. Officials' screens and results sent to RaceHub keep the real name (#265)
- [x] **BOARDS-01**: Spectator boards show the race on now and next, live timing and results on screens at the venue without signing in (#20)
- [x] **LOCAL-01**: A full meeting runs with no internet connection at the venue (#21)
- [x] **DB-01**: The app keeps its data in one SQLite file in a per-machine data folder (#26)
- [x] **DB-02**: The database vendor is chosen in one place and vendor-specific code is confined to one package, enforced by a test, so the database can be swapped later (#26)
- [x] **BACKUP-01**: Backups are taken when a race day closes (an official completes the event), every night and on demand, while racing carries on; a restore command puts one back (#22)
- [x] **INSTALL-01**: One installer per system (Windows, macOS, Linux) installs the app with its own Java runtime as a background service (#23, #24)
- [x] **INSTALL-02**: A demo club and a simulated decoder can be turned on from the installed app for trying it out (#24)
- [x] **RESULTS-EXPORT-01**: Results Export v1 is pushed to RaceHub through an outbound queue that waits for a connection (#27)
- [x] **LIVE-FEED-01**: A live feed relay lets remote viewers follow timing (#28)
- [x] **OVERLAY-01**: A streaming overlay page shows live timing in OBS (#29)


## v2 Requirements

### Governing Body Integration

- ~~**CLUB-03**: If a governing body affiliation has a `validationApiUrl` configured, the system validates membership numbers against that external API on entry submission; validation result (UNVERIFIED / VALID / INVALID) is stored per racer per governing body and surfaced to admins~~ Moved to RaceHub, which checks membership at booking (#18)

### Notifications

- ~~**NOTF-01**: Racer receives email confirmation when their event entry is accepted~~ Moved to RaceHub, which owns entry (#18)
- ~~**NOTF-02**: Racer receives reminder email before an event they've entered~~ Moved to RaceHub (#18)

### Extended Official Tools

- **OFF-02**: Video review integration for disputed incidents
- **OFF-03**: Protest and appeal workflow with documented resolution

### Multi-Club / Federation Support

- **FED-01**: Multiple clubs can share the platform with data isolation
- **FED-02**: Federation-level standings across clubs

### Advanced Reporting

- **RPT-01**: Admin can export season statistics (best lap, average lap, DNF rate per racer)
- ~~**RPT-02**: Racer can export their own season summary~~ Racers have no accounts (#18)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Payment processing | RaceHub handles fees, or the club collects them at the track |
| Native mobile app | Browser-based UI is mobile-responsive; dedicated app adds no v1 value |
| ~~Offline mode~~ | **Superseded** — race day runs locally at the venue and needs no internet connection (local-only timing plan, tracking issue #8) |
| Public entry list | Not requested; reduces racer privacy before an event |
| Racer accounts and online entry | **Removed** in #18: RaceHub owns racer identity, garage and entry |
| Forwarder and cloud sync | **Removed** in #10 and #21: the app reads the decoder directly and race day runs locally |
| Internet deployment (Docker, nginx, TLS) | **Removed** in #24: the app stays on the venue network |
| Social / community features | Not a social platform — timing and management only |
| Kafka / message broker | Single-club deployment; in-process STOMP broker is sufficient |
| Timing protocols other than AMB/MyLaps | RC-4 text is implemented and P3 binary is deferred; a new protocol would be a new parser in `decoder-protocol/` |
| Reedy race format | Specialist pairing-schedule and scoring rules require expert input from an active organizer; deferred to post-v1 |

## Traceability

| Requirement | Built in | Status |
|-------------|----------|--------|
| AUTH-01 | Phase 1 | Removed (#18) |
| AUTH-02 | Phase 1 | Changed (#18) |
| AUTH-03 | Phase 1 | Removed (#18) |
| AUTH-04 | Phase 1 | Complete |
| AUTH-05 | Phase 1 | Complete |
| AUTH-06 | #61 | Complete |
| AUTH-07 | #61 | Complete |
| RACER-01 | Phase 2 | Removed (#18) |
| RACER-02 | Phase 2 | Removed (#18) |
| RACER-03 | Phase 2 | Removed (#18) |
| RACER-04 | Phase 2 | Removed (#18) |
| RACER-05 | Phase 2 | Changed (#14) |
| RACER-06 | Phase 2 | Removed (#18) |
| RACER-07 | Phase 2 | Changed (#15) |
| RACER-08 | Phase 2 | Changed (#19) |
| RACER-09 | Phase 2 | Changed (#14) |
| RACER-10 | Phase 2 | Removed (#18) |
| RACER-11 | Phase 2 | Removed (#18) |
| RACER-12 | Phase 2 | Removed (#18) |
| RACER-13 | Phase 2 | Removed (#18) |
| RACER-14 | Phase 2 | Removed (#18) |
| CLUB-01 | Phase 1 | Changed (#18) |
| CLUB-02 | Phase 1 | Complete |
| TRACK-01 | Phase 1 | Complete |
| TRACK-02 | Phase 1 | Stored only, not applied |
| TRACK-03 | Phase 1 | Stored only, not applied |
| TRACK-04 | Phase 1 | Complete |
| RACECLASS-01 | Phase 1 | Complete |
| EVENT-01 | Phase 3 | Complete |
| EVENT-02 | Phase 3 | Complete |
| EVENT-03 | Phase 2 | Removed (#18) |
| EVENT-04 | Phase 2 | Changed (#17) |
| EVENT-05 | Phase 3 | Complete |
| EVENT-06 | Phase 3 | Complete |
| EVENT-07 | Phase 3 | Complete |
| ENTRY-01 | Phase 2 | Changed (#15) |
| ENTRY-02 | Phase 3 | Complete |
| ENTRY-03 | Phase 4 | Complete |
| FORMAT-01 | Phase 1 | Complete |
| FORMAT-02 | Phase 1 | Complete |
| FORMAT-04 | Phase 1 | Complete |
| FORMAT-05 | Phase 1 | Complete |
| FORMAT-06 | Phase 1 | Complete |
| FORMAT-07 | Phase 1 | Complete |
| FORMAT-08 | Phase 1 | Complete |
| FORMAT-09 | Phase 1 | Complete |
| FORMAT-10 | Phase 1 | Complete |
| FORMAT-11 | Phase 1 | Complete |
| FORMAT-12 | Phase 1 | Complete |
| FORMAT-13 | Phase 1 | Complete |
| FORMAT-14 | Phase 1 | Complete |
| FORWARDER-01 | Phase 5 | Removed (#10) |
| FORWARDER-02 | Phase 5 | Removed (#10) |
| FORWARDER-03 | Phase 5 | Removed (#10) |
| FORWARDER-04 | Phase 5 | Removed (#10) |
| FORWARDER-05 | Phase 5 | Removed (#10) |
| TIMING-01 | Phase 5 | Changed (#9) |
| TIMING-02 | Phase 5 | Changed (#9) |
| TIMING-03 | Phase 5 | Complete |
| TIMING-04 | Phase 5 | Changed |
| TIMING-05 | Phase 5 | Changed |
| TIMING-06 | Phase 5 | Removed (#10) |
| TIMING-07 | Phase 5 | Removed (#10) |
| TIMING-08 | Phase 5 | Changed (#14) |
| CTRL-01 | Phase 4 | Complete |
| CTRL-02 | Phase 4 | Complete |
| CTRL-03 | Phase 4 | Complete |
| CTRL-04 | Phase 4 | Complete |
| CTRL-05 | Phase 4 | Complete |
| CTRL-06 | Phase 4 | Complete |
| CTRL-07 | Phase 4 | Complete |
| CTRL-08 | Phase 4 | Complete |
| CTRL-09 | Phase 4 | Complete |
| AUDIO-01 | Phase 6 | Complete |
| AUDIO-02 | Phase 6 | Complete |
| AUDIO-03 | Phase 6 | Complete |
| AUDIO-04 | Phase 6 | Complete |
| AUDIO-05 | Phase 6 | Complete |
| AUDIO-06 | Phase 6 | Complete |
| AUDIO-07 | Phase 6 | Complete |
| AUDIO-08 | Phase 6 | Changed |
| AUDIO-09 | Phase 6 | Complete |
| AUDIO-10 | Phase 6 | Complete |
| AUDIO-11 | Phase 6 | Complete |
| AUDIO-12 | Phase 6 | Removed (#18) |
| AUDIO-13 | Phase 6 | Removed (#18) |
| AUDIO-14 | Phase 6 | Removed (#30) |
| AUDIO-15 | Phase 6 | Removed (#18) |
| AUDIO-16 | Local-only plan | Complete (#119) |
| AUDIO-17 | Local-only plan | Complete (#120) |
| OFFICIAL-01 | Phase 4 | Complete |
| OFFICIAL-02 | Phase 4 | Complete |
| OFFICIAL-03 | Phase 4 | Complete |
| OFFICIAL-04 | Phase 4 | Complete |
| PRACTICE-01 | Phase 6 | Complete |
| PRACTICE-02 | Phase 6 | Complete |
| CHAMP-01 | Phase 3 | Complete |
| CHAMP-02 | Phase 3 | Complete |
| CHAMP-03 | Phase 3 | Complete |
| CHAMP-04 | Phase 3 | Complete |
| CHAMP-05 | Phase 7 | Complete |
| CHAMP-06 | Phase 3 | Complete |
| CHAMP-07 | Phase 3 | Complete |
| CHAMP-08 | Phase 3 | Complete |
| CHAMP-09 | Phase 3 | Complete |
| CHAMP-10 | Phase 3 | Complete |
| RESULT-01 | Phase 7 | Complete |
| RESULT-02 | Phase 7 | Complete |
| RESULT-03 | Phase 7 | Changed (#12) |
| RESULT-04 | Phase 7 | Removed (#18) |
| RESULT-05 | Phase 7 | Complete |
| IMPORT-01 | #15 | Complete |
| IMPORT-02 | #15 | Complete |
| IMPORT-03 | #15 | Complete |
| IMPORT-04 | #16 | Complete |
| IMPORT-05 | #15, #16 | Complete |
| IMPORT-06 | #39 | Complete |
| IMPORT-07 | #42 | Complete |
| WALKIN-01 | #17 | Complete |
| WALKIN-02 | #123 | Complete |
| COMPETITOR-01 | #12 | Complete |
| COMPETITOR-02 | #13 | Complete |
| COMPETITOR-03 | #123 | Complete |
| COMPETITOR-04 | #263 | Complete |
| PRIVACY-01 | #267 | Complete |
| COMPETITOR-05 | #265 | Complete |
| TRANSPONDER-01 | #14 | Complete |
| CHECKIN-01 | #19 | Complete |
| CHECKIN-02 | #19, #92 | Complete |
| BOARDS-01 | #20 | Complete |
| LOCAL-01 | #21 | Complete |
| DB-01 | #26 | Complete |
| DB-02 | #26 | Complete |
| BACKUP-01 | #22 | Complete |
| INSTALL-01 | #23, #24 | Complete |
| INSTALL-02 | #24 | Complete |
| RESULTS-EXPORT-01 | #27 | Complete |
| LIVE-FEED-01 | #28 | Complete |
| OVERLAY-01 | #29 | Complete |

**Coverage:**
- Requirements: 132 total (109 from the original v1 plan, 23 in the local-only timing section)
- Complete: 89, stored only and not applied: 2 (TRACK-02, TRACK-03), changed and complete: 16, removed: 25, planned: 0

---
*Requirements defined: 2026-04-15*
*Last updated: 2026-10-06 — CHECKIN-02 records that RCTC's check-in decides who has arrived; RESULT-04 removed, as no results show car tags (#92)*
*Previously updated: 2026-10-05 — IMPORT-06 added for the RC-Timing CSV entry import (#39)*
*Previously updated: 2026-10-05 — AUTH-06 and AUTH-07 added for managing officials and the command-line admin password reset (#61)*
*Previously updated: 2026-10-05 — AUDIO-14 removed with the profanity blocklist (#30)*
*Previously updated: 2026-10-05 — local-only timing (#8, #25): racer, forwarder and P3-forwarder requirements removed, changed requirements annotated, Local-only timing section added, traceability brought up to date*
*Previously updated: 2026-10-04 — removed the offline race-day app's requirements (retired in #21; race day moves to RCTC run locally at the venue, #8)*
*Previously updated: 2026-10-03 — added the offline race-day app's requirements; removed "Offline mode" from Out of Scope (superseded)*
*Previously updated: 2026-04-16 — added track entity (TRACK-01–03), EVENT-07 (track association), governing body membership (RACER-13–14, CLUB-01); removed min/max lap times from FORMAT section (moved to track config); renumbered FORMAT-10–13; removed FORMAT-03 (Reedy — deferred post-v1); added FORMAT-14 (race config JSON import/export); added AUTH-05 (stackable roles: ADMIN/RACE_DIRECTOR/REFEREE); added CLUB-02 (club profile); renumbered v2 CLUB-02→CLUB-03; updated TRACK-01 (optional track length); added TRACK-04 (decoder loop configuration); traceability populated (roadmap created)*
