# API Reference

Base URL: `http://localhost:8080/api/v1`

All protected endpoints require `Authorization: Bearer <access_token>`.

---

## Auth

### Login

```http
POST /auth/login
Content-Type: application/json

{
  "email": "david@example.com",
  "password": "supersecret123"
}
```

**200 OK**
```json
{
  "accessToken": "eyJ...",
  "id": "1",
  "email": "david@example.com",
  "firstName": "David",
  "lastName": "Anderson",
  "roles": ["ADMIN", "RACE_DIRECTOR"]
}
```

Sets `refresh_token` HttpOnly cookie (7-day TTL, path `/api/v1/auth/refresh`).  
**401 Unauthorized** — invalid credentials (no detail returned, by design).  
**403 Forbidden** — the account holds no official role (`ADMIN`, `RACE_DIRECTOR`, `REFEREE`), or it has been disabled; a disabled account's problem detail carries `"reason": "disabled"`. Only officials sign in; there is no self-registration or emailed password reset. An admin sets passwords on the Officials page, and a locked-out club uses `RCTimingControl reset-admin-password` (see [installing.md](installing.md#locked-out)).

---

### Refresh access token

Requires the `refresh_token` cookie (sent automatically by the browser).

```http
POST /auth/refresh
```

**200 OK** — new access token + rotated refresh cookie.  
**401 Unauthorized** — cookie missing, expired, or revoked, or the account has been disabled.

### Sign out

Uses the same cookie, and the same path, as refresh: the browser sends the cookie only to `/auth/refresh`.

```http
DELETE /auth/refresh
```

**204 No Content** — always, whether or not the cookie was valid. Revokes the refresh token the cookie holds and every token it was rotated into (they share a family), and expires the cookie, so this browser cannot sign back in, even if a refresh was in flight. Other browsers the same official is signed in on are not affected, and the access token already issued works until it expires (15 minutes).

---

## Admin — Club

All admin endpoints require a staff role: `ADMIN`, `RACE_DIRECTOR`, or `REFEREE`.

Reading is open to every official. **Changing** club config, tracks, racing classes, race formats, event classes, events and championships (every `POST`, `PUT`, `PATCH` and `DELETE` on those) needs `ADMIN`, and answers **403** to a race director or referee. Championship **exclusions** are the exception: any official may add or remove one, because a referee records a disqualification. That flow is to be revisited after user testing. The one exception is `POST /admin/events/{id}/transition` (publish, open, close entries, start, complete), which a `RACE_DIRECTOR` may also call, as may generating rounds and seeding finals.

### Get club profile

```http
GET /admin/club/profile
Authorization: Bearer <token>
```

```json
{
  "id": 1,
  "name": "Southside RC Club",
  "email": "info@southsiderc.com",
  "phone": "07700 900000",
  "websiteUrl": "https://southsiderc.com",
  "latitude": 51.5,
  "longitude": -0.1,
  "timezone": "Europe/London"
}
```

---

### Create / update club profile

```http
PUT /admin/club/profile
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Southside RC Club",
  "email": "info@southsiderc.com",
  "phone": "07700 900000",
  "websiteUrl": "https://southsiderc.com",
  "latitude": 51.5074,
  "longitude": -0.1278,
  "timezone": "Europe/London"
}
```

Upserts the singleton club profile row. `timezone` must be a valid IANA timezone ID.

---

### Governing body affiliations

```http
GET    /admin/club/affiliations
POST   /admin/club/affiliations          # 201
PUT    /admin/club/affiliations/{id}
DELETE /admin/club/affiliations/{id}     # 204
```

Request body:
```json
{
  "code": "BRCA",
  "displayName": "British Radio Car Association",
  "membershipRequired": true
}
```

`membershipRequired` is stored but not checked when entries are added: entries come from the RaceHub import or are walk-ins added on the day.

---

## Admin — Tracks

### List / get tracks

```http
GET /admin/tracks
GET /admin/tracks/{id}
```

Response includes nested `decoderLoops` and `lapThresholds` arrays.

---

### Create track

```http
POST /admin/tracks
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Main Circuit",
  "venueNotes": "Carpet surface, 90m",
  "trackLength": 90.0
}
```

---

### Decoder loops

```http
POST   /admin/tracks/{trackId}/loops
PUT    /admin/tracks/loops/{loopId}
DELETE /admin/tracks/loops/{loopId}     # 204
```

Request body:
```json
{
  "loopId": "LOOP_01",
  "displayName": "Start/Finish",
  "loopType": "FINISH_LINE",
  "isScoringLoop": true
}
```

`loopType` values: `FINISH_LINE`, `CHICANE`, `OTHER`

---

### Lap thresholds

```http
POST   /admin/tracks/{trackId}/thresholds
DELETE /admin/tracks/thresholds/{thresholdId}     # 204
```

Request body:
```json
{
  "racingClassId": null,
  "minLapMs": 15000,
  "maxLastLapMs": 90000
}
```

`racingClassId: null` = track-wide default. Set to a class ID to override for a specific class.

---

## Admin — Racing Classes

```http
GET    /admin/classes
GET    /admin/classes/{id}
POST   /admin/classes          # 201
PUT    /admin/classes/{id}
DELETE /admin/classes/{id}     # 204
```

Request body:
```json
{
  "name": "1:10 Electric Touring Car",
  "description": "17.5T blinky spec"
}
```

---

## Admin — Race Formats

### CRUD

```http
GET    /admin/formats
GET    /admin/formats/{id}
POST   /admin/formats          # 201
PUT    /admin/formats/{id}
DELETE /admin/formats/{id}     # 204
```

The `config` field is type-discriminated by `type`. Three format types are supported:

**Timed race** (`TIMED`):
```json
{
  "name": "5-Minute Qualifier",
  "config": {
    "type": "TIMED",
    "durationSeconds": 300,
    "warmupSeconds": 30,
    "startType": "Le_MANS",
    "qualifyingType": "BEST_LAP"
  }
}
```

**Bump-up** (`BUMP_UP`):
```json
{
  "name": "Bump-Up Final",
  "config": {
    "type": "BUMP_UP",
    "durationSeconds": 300,
    "warmupSeconds": 0,
    "startType": "ROLLING",
    "qualifyingType": "BEST_LAP",
    "bumpsPerFinal": 2
  }
}
```

**Points finals** (`POINTS_FINALS`):
```json
{
  "name": "Points Finals",
  "config": {
    "type": "POINTS_FINALS",
    "durationSeconds": 300,
    "warmupSeconds": 0,
    "startType": "ROLLING",
    "qualifyingType": "BEST_LAP",
    "numberOfFinals": 3,
    "pointsTable": [10, 8, 6, 5, 4, 3, 2, 1]
  }
}
```

---

### Export format config

```http
GET /admin/formats/{id}/export
Authorization: Bearer <token>
Accept: application/json          # or: application/yaml
```

Returns the config as JSON or YAML depending on the `Accept` header.

---

### Import format config

```http
POST /admin/formats/import?name=My+Template
Authorization: Bearer <token>
Content-Type: application/json    # or: application/yaml

{
  "type": "TIMED",
  "durationSeconds": 300,
  "warmupSeconds": 30,
  "startType": "LE_MANS",
  "qualifyingType": "BEST_LAP"
}
```

YAML example:
```http
POST /admin/formats/import?name=Imported+Qualifier
Authorization: Bearer <token>
Content-Type: application/yaml

type: TIMED
durationSeconds: 300
warmupSeconds: 30
startType: LE_MANS
qualifyingType: BEST_LAP
```

**201 Created** — returns the full `RaceFormatTemplateDto`.

---

---

## Events — Public schedule

No authentication required.

```http
GET /events
```

Returns the published events (published, open, entries closed or in progress), soonest first: each one's `id`, `name`, `eventDate`, the ids of its finished races (`finishedRaceIds`) and the championship it counts towards (`championshipId`, or null). Entry windows belong to RaceHub, so the schedule does not show them.

---

## Admin — RaceHub import

Entries come from RaceHub's Entry Export v1 JSON. RaceHub owns booking, racer identity and payment; the export carries only what timing needs, with no contact, date of birth, guardian or payment data.

### Import an export

```http
POST /admin/events/{eventId}/racehub-import?dryRun=true
Authorization: Bearer <token>
Content-Type: application/json

{ "schema_version": 1, "event": { ... }, "revision": 12, "entries": [ ... ] }
```

Requires `ADMIN`. With `dryRun=true` it returns the preview and saves nothing. Each entry is matched by its RaceHub `entry_id` and applied only if its `entry_version` is higher than the one already imported, so a replay is safe. A withdrawn entry is marked `WITHDRAWN`, never deleted, and keeps its race history. Each entry's driver becomes a competitor, matched by RaceHub driver ID.

**200 OK** (or **422** for a real import blocked by unmapped classes or invalid rows, with nothing saved):
```json
{
  "dryRun": true, "blocked": false, "applied": false,
  "racehubEventName": "Winter Series Round 4", "revision": 12,
  "summary": { "created": 18, "updated": 2, "withdrawn": 1, "unchanged": 0, "stale": 0, "skipped": 0 },
  "unmappedClasses": [], "errors": [], "warnings": [],
  "rows": [ { "entryId": "…", "entryVersion": 3, "driverDisplayName": "Sam Speed", "action": "CREATE", "eventClassId": 11, "rctcEntryId": null } ]
}
```

`action` is `CREATE`, `UPDATE`, `WITHDRAW`, `UNCHANGED` (a replay), `STALE` (an older version) or `SKIP` (withdrawn and never imported).

A transponder swapped on the day survives a re-import (#50). When an update carries a different number for a slot swapped on the day, the swapped number is kept, a warning names both numbers, and the file's number is stored on the entry as `importedTransponderNumber` (or `importedSecondaryTransponderNumber`). The entry list shows it. Nothing is blocked, and swapping to the file's number clears it.

### The file format, for any booking system

Entry Export v1 is the format any booking system can produce to send its entries to RCTC, not only RaceHub. Its JSON Schema (draft 2020-12) is `app/src/main/resources/racehub/entry-export-v1.schema.json`, and a test checks every import test file against it.

| Field | Meaning |
|-------|---------|
| `schema_version` | Always `1` |
| `source` | Optional. The system the ids come from: up to 30 capital letters, digits or underscores, starting with a letter. Defaults to `RACEHUB`. `CSV` is taken by the RC-Timing CSV import. |
| `event` | `id` (required), and optionally `external_reference`, `name`, `event_local_date` (YYYY-MM-DD) and `timezone` |
| `revision` | A number that goes up each time the system exports this event |
| `generated_at` | Optional ISO-8601 time the file was made |
| `entries` | One object per entry, below |

Each entry needs `entry_id`, `entry_version` (goes up each time the entry changes), `entry_status` (`CONFIRMED` or `WITHDRAWN`), `driver_profile_id` (the system's id for the driver) and `event_class_id` (its id for the class). The rest are optional: `race_day_status` (`NOT_ARRIVED` or `ARRIVED`), `rc_class_name` (matched to a racing class name), `rc_class_number`, `class_name`, `driver_display_name`, `brca_number`, `brca_source`, `home_club`, `age_category`, `grade`, `primary_transponder`, `secondary_transponder`, `club_car_requested` and `club_car_name`. Any other field is ignored, so contact, date of birth or payment data sent by mistake is never stored.

Entries, drivers and class mappings are kept per `source`, so another system's ids never meet RaceHub's: a file with `"source": "OTHER"` and the same ids as a RaceHub file makes its own entries and competitors. Only a RaceHub file links the event to a RaceHub event, so results go back to RaceHub only for RaceHub entries. In Results Export v1, an entry from any other source is sent like a walk-in, by its RCTC ids.

### Class mappings

```http
GET /admin/events/{eventId}/racehub-class-mappings
PUT /admin/events/{eventId}/racehub-class-mappings

[ { "racehubEventClassId": "c1a2…", "eventClassId": 11 } ]
```

Requires `ADMIN`. Each RaceHub class is matched to the event class whose racing class name equals its `rc_class_name`, ignoring case. These mappings set the match for classes whose names differ. `PUT` replaces the event's mappings.

The RC-Timing CSV import uses the same mappings, keyed `CSV:` and the file's class name in lower case (or `CSV:#` and a Class Number), as its `unmappedClasses` list shows. A file from another source is keyed by that source, a colon and its `event_class_id`, such as `OTHER:c1a2…`.


### Pull from a URL (entry feed)

RCTC runs on a venue laptop that other systems usually can't reach, so instead of being sent entries it can fetch an Entry Export v1 file from a URL. Each event has at most one feed.

```http
GET    /admin/events/{eventId}/entry-feed
PUT    /admin/events/{eventId}/entry-feed
DELETE /admin/events/{eventId}/entry-feed
POST   /admin/events/{eventId}/entry-feed/fetch
POST   /admin/events/{eventId}/entry-feed/preview
POST   /admin/events/{eventId}/entry-feed/apply
```

All require `ADMIN`. `GET` answers **204** when the event has no feed. `PUT` saves it:

```json
{ "url": "https://booking.example.org/events/123/entries", "token": "…", "autoFetch": true }
```

- `url` must be `https`, or `http` only to the laptop itself (`localhost` or a loopback address).
- `token` is sent as `Authorization: Bearer`. Leave it out to keep the saved token; an empty string removes it. It is stored encrypted with a key kept in the data folder (`entry-feed-key`), so a copy of the database alone, such as a backup, doesn't give it away. A token saved on another laptop can't be read and has to be entered again.
- Changing the URL drops any file fetched from the old one, and its last outcome.

The feed comes back as:

```json
{
  "url": "https://booking.example.org/events/123/entries",
  "tokenSaved": true, "tokenHint": "f00d", "autoFetch": true,
  "lastFetchAt": "2026-10-06T09:30:00Z", "lastStatus": "WAITING",
  "lastMessage": "Fetched revision 7. Check it and confirm the import.",
  "appliedRevision": 6, "waiting": true, "waitingRevision": 7
}
```

The token itself is never sent back. `tokenHint` is its last four characters, shown only when the token is at least eight long. `lastStatus` is `APPLIED`, `UNCHANGED` (the file's `revision` was already applied), `WAITING` (a fetched file waits for an official), `AUTH_FAILED` (the feed answered 401 or 403) or `FAILED`, with `lastMessage` saying why.

`fetch` fetches now and returns the feed. A failure is recorded in the feed rather than returned as an error. A new revision is checked with a dry run and held for an official (`WAITING`); nothing is imported until `apply`. `preview` and `apply` work like the [RaceHub import](#import-an-export) on the held file, including a **422** when something blocks it, and answer **404** when no file is waiting. Only the fields Entry Export v1 knows are kept in the held file.

With `autoFetch` on, the feed is fetched every five minutes (`rctiming.entry-feed.interval-ms`). A new revision that imports cleanly is applied straight away. The import is all or nothing, so a file that something would block, such as an unmapped class, is held whole for an official to review, with the reason in `lastMessage`.

---

## Admin — RC-Timing CSV import

For clubs moving off RC-Timing. The file is an RC-Timing 2025 style driver CSV: a header row naming the columns, in any order and any subset of `Name,BRCA Number,Club Number,Class Number,Class,Formula Number,Member Type Number,Grade,PT No,PT No 2,Junior,Paid Status,Car Make,Entry Desc`. It needs `Name` and `Class` or `Class Number`. RC-Timing files have no quoting, so a name or car make with a comma or double quote is rejected with the line number. The file may be UTF-8 or the Windows code page that RC-Timing and Excel save in.

```http
POST /admin/events/{eventId}/csv-import?dryRun=true
Authorization: Bearer <token>
Content-Type: multipart/form-data

file=<the .csv>
```

Requires `ADMIN`. The file has no entry ids, versions or withdrawals, so nothing is applied on trust:

- Each booked row is keyed by its BRCA number, or its name when the BRCA number is 0, and the event class it is placed in. So a class given by name in one file and by `Class Number` in the next is the same entry.
- The driver is matched to a competitor already in RCTC from any source (an earlier CSV import, RaceHub or a walk-in) by BRCA number, or by name when neither has a BRCA number, so their results stay together. A new competitor is made only when there is no single match. A competitor from RaceHub or a walk-in is never renamed by the file, and a driver already entered in a class from another source blocks the import rather than getting a second entry there.
- `Entry Desc` = `entry` (or no `Entry Desc` column) books the driver in. `update` rows are skipped, since they only change RC-Timing's member archive.
- `PT No` and `PT No 2` are the primary and secondary transponders (`0` = none). `Grade`, `Junior`, `Member Type Number`, `Formula Number` and `Car Make` are shown in the preview only. `Club Number` and `Paid Status` are ignored, and no payment data is stored.
- A class is placed by the event's class mappings, then by racing class name ignoring case, then, for a row with only a `Class Number`, by the event's classes in the order they were added. A class name that matches nothing is never placed by its number; it is listed for mapping.

The preview sorts every row into a `group`: `NEW`, `CHANGED` (with each field's old and new value), `UNCHANGED` or `SKIPPED`. It also lists, as `MISSING`, the active entries an earlier CSV import made for this event that this file leaves out. Walk-ins and RaceHub entries are never missing.

To apply, post the same file without `dryRun`, with the picks as extra form fields:

```
file=<the .csv>
update=<key of a CHANGED row>      (repeat for each row to update)
withdraw=<entryId of a MISSING row> (repeat for each entry to withdraw)
```

New rows are always created; only the picked changed rows are updated and only the picked missing entries are withdrawn (never deleted). A pick that no longer matches the file or the event blocks the import, so preview again.

**200 OK** (or **422** for a real import blocked by bad rows, unmapped classes or a stale pick, with nothing saved):
```json
{
  "dryRun": true, "blocked": false, "applied": false,
  "summary": { "newEntries": 5, "changed": 1, "unchanged": 0, "missing": 1, "skipped": 1, "created": 0, "updated": 0, "withdrawn": 0 },
  "unmappedClasses": [ { "key": "CSV:nitro truggy", "className": "Nitro Truggy", "classNumber": null, "entryCount": 2 } ],
  "errors": [], "warnings": [ "Transponder 7123456 is used by more than one entry: Ada Lovelace, Grace Hopper" ],
  "rows": [ {
    "group": "CHANGED", "key": "21/brca:12345/class:11", "line": 2, "name": "Ada Lovelace",
    "brcaNumber": 12345, "className": "2WD Buggy", "classNumber": 1, "eventClassId": 11, "entryId": 301,
    "primaryTransponder": "7723456", "secondaryTransponder": "7223456",
    "changes": [ { "field": "Transponder", "before": "7123456", "after": "7723456" } ],
    "info": { "Grade": "80", "Car Make": "Associated" }, "applied": false, "reason": null
  } ]
}
```

A `MISSING` row has no `key` or `line`; pick it by `entryId`. A `SKIPPED` row gives its `reason`. A changed field is `Status`, `Name` (only for a competitor the CSV import made), `Transponder` or `Second transponder`.

---

## Admin — Competitors

```http
GET /admin/competitors
Authorization: Bearer <token>
```

Requires an official role. Lists competitors: the people entries point at. A competitor has no account. One imported from RaceHub is keyed by its RaceHub driver ID; a walk-in is created by name. Results, live timing and championship standings group by competitor. Each row carries `spokenName`, how the name is said aloud, or `null` when none is set, and `speechName`, what the announcer actually says: the spoken name, else the display name tidied for speech (brackets, symbols and spacing removed, ALL CAPITALS in normal capitals).

### Set how a name is said aloud

```http
PUT /admin/competitors/{id}/spoken-name
Authorization: Bearer <token>
Content-Type: application/json

{ "spokenName": "Shiv-awn Keen" }
```

Requires `ADMIN`, `RACE_DIRECTOR` or `REFEREE`, so a name can be fixed on the day. Every real change is recorded with who made it (see below); saving the same value again records nothing. Send an empty string to clear it. The text is trimmed and can be at most 100 characters (400 otherwise); an unknown competitor is 404. Returns the competitor. The grid-call audio uses it from the next race to reach the grid (the clips are made then), the running order from its next announcement, and a RaceHub or CSV re-import never changes it.

### See who changed how a name is said

```http
GET /admin/competitors/{id}/changes
Authorization: Bearer <token>
```

Admin only. Newest first: `[{ "at": "...", "by": "Dana Director", "action": "SPOKEN_NAME_CHANGED", "before": null, "after": "Shiv-awn Keen" }]`. The history follows a competitor through a merge.

### Hear it in the announcer voice

```http
POST /admin/competitors/spoken-name/preview
Authorization: Bearer <token>
Content-Type: application/json

{ "text": "Shiv-awn Keen" }
```

Any official. Returns `audio/wav` made with the club's current voice. 503 when the voice service is not running.

### List possible duplicates

```http
GET /admin/competitors/possible-duplicates
Authorization: Bearer <token>
```

Admin only. Groups of competitors that may be one person entered twice: `[{ "reason": "Same name", "competitors": [ ... ] }]`. The reason is `Same name`, `Same BRCA number` or `Same name and BRCA number`. A name matches ignoring case and spacing.

### Merge a duplicate into the competitor to keep

```http
GET /admin/competitors/merge-preview?keepId=7&duplicateId=12
Authorization: Bearer <token>
```

Admin only. Shows what the merge would do and changes nothing: `keep` and `duplicate` (each with `entries`), `entriesToMove`, `eventsAffected`, `exclusionsToMove`, `resultingSpokenName`, `resultingExternalSource`, `warnings`, `blockers` and `canMerge`. `blockers` list why it can't go ahead: both are linked to different RaceHub drivers, or both have an active entry in the same class of an event.

```http
POST /admin/competitors/merge
Authorization: Bearer <token>
Content-Type: application/json

{ "keepId": 7, "duplicateId": 12 }
```

Admin only. Moves the duplicate's entries and championship exclusions to `keepId` and deletes the duplicate, in one transaction, and returns `{ "keptCompetitorId": 7, "entriesMoved": 3, "exclusionsMoved": 0 }`. Each moved entry gets an audit row with the action `COMPETITOR_MERGED`. The kept competitor keeps its own name and fills a missing BRCA number, home club and spoken name from the duplicate. A RaceHub id moves to the kept competitor when it has none, so the next import still finds them. **409** with `{ "code": "COMPETITOR_MERGE_REFUSED", "blockers": [...] }` when it can't go ahead; **400** for the same competitor twice; **404** for an unknown one.

---

## Admin — Entry management

### Add a walk-in entry

```http
POST /admin/entries
Authorization: Bearer <token>
Content-Type: application/json

{
  "eventId": 5,
  "eventClassId": 11,
  "competitorName": "Wendy Walkin",
  "primaryTransponder": "1234567",
  "secondaryTransponder": "7654321"
}
```

Requires `ADMIN` or `RACE_DIRECTOR`. Give either `competitorId` (an existing competitor) or `competitorName` (a new one), not both. **201 Created** with `{ "entry": {...}, "warnings": [...] }`; a transponder another active entry in the event uses is a warning. **409** if the competitor already has an entry in the class or the event is completed. A new `competitorName` that matches an existing competitor (same name, ignoring case and spacing) is refused with **409** and `{ "code": "POSSIBLE_DUPLICATE_COMPETITOR", "matches": [{ "id": 7, "displayName": "Wendy Walkin", "brcaNumber": null, "homeClub": null, "spokenName": null }] }`. Send that `competitorId` to use them, or add `"confirmNewCompetitor": true` to say this is a different person.

---

### Withdraw an entry

```http
POST /admin/entries/{id}/withdraw
Authorization: Bearer <token>
Content-Type: application/json

{ "reason": "Driver went home" }
```

Requires `ADMIN` or `RACE_DIRECTOR`. Marks the entry `WITHDRAWN` and writes an audit log row.

---

### See what has happened to an entry

```http
GET /admin/entries/{id}/history
Authorization: Bearer <token>
```

Admin only; **404** for an unknown entry. Oldest first: `[{ "at": "...", "actor": "Dana Director", "summary": "Withdrawn", "reason": "Driver went home" }]`. It lists a walk-in being added, transponder swaps, a competitor merge and withdrawal by an official. Changes made by importing an entry file are recorded once per import, not per entry, so they are not listed. `actor` is a name only, and `reason` is null where the action asks for none.

---

## Race control — Lifecycle commands

```
POST /race-control/race/{raceId}/call-grid
POST /race-control/race/{raceId}/start        (also resumes a stopped race)
POST /race-control/race/{raceId}/stop
POST /race-control/race/{raceId}/finish
POST /race-control/race/{raceId}/abandon
POST /race-control/race/{raceId}/restart
```

Require `RACE_DIRECTOR` or `ADMIN`. Each answers **200** with no body, **404** for an unknown race, and **409** for a command the race's state does not allow (`restart` works from any state). Each writes one row to the audit log (`GET /admin/audit`) in the same transaction as the change, so a refused command leaves no row: `RACE_GRID_CALLED`, `RACE_STARTED`, `RACE_RESUMED`, `RACE_STOPPED`, `RACE_FINISHED`, `RACE_ABANDONED`, `RACE_RESTARTED`. The row names the official, the race and the event, and holds the status before and after. A restart also keeps what it throws away in the row's `before`: the race's times, its stored result (`resultSnapshot`) and the live positions held in memory.

## Race control — Entry search & transponder swap

Open to any official (`ADMIN`, `RACE_DIRECTOR` or `REFEREE`). Withdrawn entries are never returned.

```http
GET  /race-control/events/{eventId}/entries/search?query=jane
```

`search` is a case-insensitive name match, to find the entry to swap. Each item is `{ "entryId": 101, "competitorName": "Jane Doe", "competitorId": 7, "spokenName": null, "speechName": "Jane Doe", "className": "Stock Buggy", "transponderNumber": "1234567", "secondaryTransponderNumber": null, "importedTransponderNumber": null, "importedSecondaryTransponderNumber": null }`. Refusals are Problem Details with a readable `detail` and a code in `error`.

```http
POST /race-control/events/{eventId}/entries/{entryId}/transponder-swap
Content-Type: application/json

{ "slot": "PRIMARY", "newTransponderNumber": "7654321" }
```

`slot` is `PRIMARY` or `SECONDARY`. A blank number removes the secondary. **200 OK** with `{ "entryId": 101, "slot": "PRIMARY", "oldTransponderNumber": "1234567", "newTransponderNumber": "7654321" }`; the change is written to the entry audit log as `TRANSPONDER_SWAP`, and laps count on the new number from the next passing. **409** `transponder_already_assigned` if another competitor's entry in the event uses the number (the same competitor's other entries may share it), or `entry_withdrawn`. **400** `same_as_other_transponder` or `primary_required`.

The pre-race readiness grid call (`GET /race-control/race/{raceId}/pre-race-readiness`) carries `racehubArrival` on each slot.

## Race control — Race entries, transponder links and history

```http
GET  /race-control/race/{raceId}/entries
POST /race-control/race/{raceId}/transponders/link   { "transponderNumber": "1234567", "entryId": 101 }
GET  /race-control/race/{raceId}/history
```

`entries` lists everyone in the race, in any state, for linking a transponder or picking a driver for an incident or penalty (`RACE_DIRECTOR`, `REFEREE` or `ADMIN`). `transponders/link` links an unknown transponder to an entry, credits the laps it has already done and saves the number on the entry (a swap: it replaces a number that has not passed in this race, otherwise it becomes the secondary), so later races at the event need no relinking, answering `{ "lapsCredited": 3 }` (`RACE_DIRECTOR` or `ADMIN`). `history` is the race's own audit log rows (`RACE_DIRECTOR`, `REFEREE` or `ADMIN`). A race's results come from the public `GET /results/{raceId}`, and its live timing from `GET /boards/races/{raceId}/live-timing`.

---

## Spectator boards

No authentication: these are the read-only feeds behind the venue TV boards at `/boards/now-next` and `/boards/results`. Each takes an optional `eventId`; without it the board shows the event with a race on track, or failing that the most recent event in progress, so it keeps its event between races. The board is empty (every field `null`, `results` empty) only when the requested `eventId` doesn't exist, or none was given and no event is racing or in progress.

```http
GET /boards/now-next?eventId=7
```
**200 OK** — `{ "eventId": 7, "eventName": "Club Round 3", "currentRace": {...} | null, "nextRace": {...} | null, "lastCompletedRace": {...} | null }`. Each race is `{ "raceId": 501, "label": "Qualifying 1 — Stock Buggy — Heat 2", "roundType": "QUALIFIER", "roundNumber": 1, "className": "Stock Buggy", "heatNumber": 2, "finalLetter": null, "status": "RUNNING" }`. `currentRace` is the `RUNNING` race, falling back to a `STOPPED` one; `nextRace` is a race already called to the grid, else the first `PENDING` race in run order (so a race the director skipped ahead to shows once its grid is called); `lastCompletedRace` is the most recently finished.

```http
GET /boards/results?eventId=7
```
**200 OK** — `{ "eventId": 7, "eventName": "Club Round 3", "race": {...} | null, "results": [ResultRow] }`: the last finished race and its result snapshot rows (the same rows as `GET /results/{raceId}`). `results` is empty until the snapshot is written.

```http
GET /boards/qualifying?eventId=7
```
**200 OK** — `{ "eventId": 7, "eventName": "Club Round 3", "tables": [QualifyingTable] }`: the qualifying standings per class, in the same shape as the race control qualifying table, for the public page at `/qualifying`. Event resolution is the same as the other boards. Names follow the public name rule. `tables` is empty when no event is found. Each row has `rounds`, the driver's place in each finished round in round order as `{ "place": 2, "counted": false }` (`counted` is false for a dropped round), and is empty unless the class ranks round by round.

```http
GET /boards/races/{raceId}/live-timing
```
**200 OK** — the race's current live timing rows (the same shape as the `/topic/race/{raceId}/timing` frames), or `[]` before the first passing. Boards, and the race control and referee live timing panels, use it once to fill the table, then follow STOMP.

```http
GET /boards/races/{raceId}/clock
```
**200 OK** — `{ "raceId": 412, "status": "RUNNING", "elapsedMs": 183400, "durationMs": 300000, "remainingMs": 116600, "running": true }`, for the streaming overlay at `/boards/overlay`. `elapsedMs` is race time so far, not counting time stopped; while `running` is true a client counts on from it locally. `durationMs` and `remainingMs` are null when the race's format sets no length. For a race the app hasn't seen change since it started, the time is worked out from its start and finish times, so a stop before the restart isn't allowed for. **404** for an unknown race.

```http
GET /boards/races/{raceId}/round-fastest-lap
```
**200 OK** — `{ "lapMs": 18500, "driverName": "Sam Second" }`, the fastest lap in the other finished heats of the race's round and class, for the overlay's stats ticker. Names follow the public name rule. Both fields are null when no other heat has finished.

**Anonymous STOMP.** A client may `CONNECT` to `/ws/timing` with no `Authorization` header. Such a session may only `SUBSCRIBE` to `/topic/race/{raceId}/timing` and `/topic/race/{raceId}/state`; any other subscription and every `SEND` is dropped. A `CONNECT` carrying a token that does not validate is still refused.

---

## Admin — Backups

`ADMIN` only. The app also backs up by itself when an event is marked `COMPLETED` and every night (see `docs/development.md` → Backups).

```http
GET /api/v1/admin/backups
```
**200 OK** — `{ "directory": "/media/usb/rctiming", "backups": [BackupFile] }`, newest first. Each `BackupFile` is `{ "name": "rctiming-20261004-220000-day-close.db", "sizeBytes": 3145728, "createdAt": "2026-10-04T22:00:00Z", "reason": "day-close" }`; `reason` is `manual`, `nightly` or `day-close`.

```http
POST /api/v1/admin/backups
```
**201 Created** — the new `BackupFile`. Safe while a race is running. **500** with a `detail` message when the backup folder can't be written (missing, read-only or full).

---

## Admin — Officials

`ADMIN` only. Officials are never deleted, only disabled, so their name stays on everything they did. Every change is logged with who made it.

```http
GET /api/v1/admin/officials
```
**200 OK** — `[Official]`, the ones who can sign in first, then by name. Each `Official` is `{ "id": 2, "email": "rob@club.example", "firstName": "Rob", "lastName": "Smith", "roles": ["RACE_DIRECTOR", "REFEREE"], "enabled": true, "disabledAt": null, "createdAt": "2026-10-01T18:00:00Z" }`.

```http
POST /api/v1/admin/officials
{ "email": "rob@club.example", "firstName": "Rob", "lastName": "Smith", "password": "at-least-8", "roles": ["RACE_DIRECTOR"] }
```
**201 Created** — the new `Official`, who can sign in straight away. **400** for a missing field, a password under 8 characters or no roles; **409** when the email is already used.

```http
PUT /api/v1/admin/officials/{id}/roles      { "roles": ["ADMIN", "REFEREE"] }
PUT /api/v1/admin/officials/{id}/password   { "password": "at-least-8" }
POST /api/v1/admin/officials/{id}/disable
POST /api/v1/admin/officials/{id}/enable
```
`roles`, `disable` and `enable` answer **200 OK** with the updated `Official`; `password` answers **204 No Content**. Setting a password or disabling signs the official out of every session: their refresh tokens are revoked, their live timing (STOMP) connections are closed straight away and can't reconnect with a token issued before the change, and an access token they already hold works for REST calls for at most 15 minutes. **409** with a `detail` when the change would leave no admin who can sign in, or when an admin disables themselves. **404** for an unknown id.

```http
GET /api/v1/admin/officials/changes?before=7&size=50
```
**200 OK** — changes, newest first, one page at a time. `size` is 50 unless given (at most 200); `before` is the id of the oldest change already shown, so the page after it comes back, and leaving it out starts at the newest. A page shorter than `size` is the last. Each change: `[{ "id": 7, "at": "2026-10-04T19:12:00Z", "officialId": 2, "officialName": "Rob Smith", "action": "ROLES_CHANGED", "detail": "RACE_DIRECTOR → ADMIN, RACE_DIRECTOR", "actorId": 1, "actorName": "Dave Admin" }]`. `action` is `ADDED`, `ROLES_CHANGED`, `PASSWORD_SET`, `DISABLED` or `ENABLED`. `actorId` and `actorName` are null for a change made with `reset-admin-password`.

---

## Admin — Audit log

`ADMIN` only. Every action an official takes that changes data, and the system's own background changes, is one row (see AUDIT-01 in [REQUIREMENTS.md](REQUIREMENTS.md)). Rows are only ever added.

```http
GET /api/v1/admin/audit?entityType=race&raceId=42&from=2026-10-04T00:00:00Z&page=0&size=50
```

Newest first. Every filter is optional and the ones given must all match: `entityType`, `entityId` (text, since an id can be an email), `action` (such as `RACE_STARTED`), `actorUserId`, `eventId`, `raceId`, `from` (inclusive) and `to` (exclusive), both ISO date-times. `size` is 50 unless given and at most 200. **200 OK**:

```json
{
  "entries": [{
    "id": 311, "at": "2026-10-04T19:12:00Z", "actorUserId": 1, "actor": "Dave Admin <dave@example.com>",
    "source": "UI", "action": "RACE_STARTED", "entityType": "race", "entityId": "42", "eventId": 7, "raceId": 42,
    "summary": "Started heat 1 (race 42)", "before": "GRID", "after": "RUNNING"
  }],
  "total": 1, "page": 0, "size": 50
}
```

`source` is `UI` (a request through the app), `CLI` (a command line tool, with `actorUserId` null and `actor` like `cli:rob`) or `SYSTEM` (a background job, `actor` like `system:bump-up`). `before` and `after` are the stored JSON values, or null. There is no screen for the log yet; the referee view shows a race's own history from `GET /api/v1/race-control/race/{raceId}/history`, and an admin sees an entry's on the entry list.

---

## Admin — Results to RaceHub

`ADMIN` only. Exports are queued and sent by the app itself; see [results-export-v1.md](results-export-v1.md) for the file and when it is sent.

```http
GET /api/v1/admin/results-exports
```
**200 OK** — `{ "sendingEnabled": true, "resultsUrl": "https://racehub.example/api/results", "savedUrl": null, "missingSettings": [], "exports": [Export] }`, the newest 100 first. `resultsUrl` is the address in use: `savedUrl`, the one set below, else `rctiming.racehub.results-url` from the settings file. `sendingEnabled` is false until both the RaceHub address and key are set, and exports wait until then; `missingSettings` names what is still needed. Each `Export` is `{ "id": 9, "eventId": 21, "eventName": "Club Round 3", "revision": 3, "reason": "CORRECTION", "status": "FAILED", "attempts": 2, "nextAttemptAt": "2026-10-18T15:50:00Z", "lastError": "RaceHub answered 503: ...", "createdAt": "2026-10-18T15:43:02Z", "sentAt": null }`. `reason` is `RACE_FINISHED`, `CORRECTION` or `DAY_CLOSE`; `status` is `QUEUED`, `FAILED`, `SENT` or `SUPERSEDED`.

```http
PUT /api/v1/admin/results-exports/address
```
Body `{ "resultsUrl": "https://racehub.example/api/results" }`. **200 OK** — the same body as the list. A blank `resultsUrl` clears the saved address, so the settings file's applies. **400** unless it is an `https://` address (plain `http://` is accepted only to this machine, for testing). Audited as `RACEHUB_RESULTS_URL_CHANGED`.

```http
POST /api/v1/admin/results-exports/{id}/retry
```
**204 No Content** — a waiting or failed export is sent with the next batch instead of waiting out its backoff. A sent or superseded one is left alone. **404** for an unknown id.

```http
GET /api/v1/admin/events/{eventId}/results-export
```
**200 OK** — the event's results as they stand now, as a Results Export v1 document, with `Content-Disposition: attachment; filename="results-event-{eventId}-r{revision}.json"`. Works for any event, imported from RaceHub or not, and queues nothing. **404** for an unknown event.

---

## Race control — Live feed

See [live-feed-v1.md](live-feed-v1.md) for what is sent and when. Any official may read; only `RACE_DIRECTOR` or `ADMIN` may change an event's setting.

```http
GET /api/v1/race-control/live-feed/status
```
**200 OK** — `{ "state": "CONNECTED", "relayHost": "relay.example", "missingSettings": [] }`. `state` is `NOT_SET_UP` (the relay address or key is missing, named in `missingSettings`), `IDLE`, `CONNECTING`, `CONNECTED` or `RECONNECTING`. The same object is pushed to `/topic/system/live-feed-status` whenever the state changes.

```http
GET /api/v1/race-control/events/{eventId}/live-feed
PUT /api/v1/race-control/events/{eventId}/live-feed   { "enabled": true }
```
**200 OK** — `{ "enabled": true }`: whether the event's races are sent. **404** for an unknown event; **403** for a `PUT` by a referee.

---

## Error responses

All errors use [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457):

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid timezone: Europe/Londonn"
}
```

Validation errors include a field-level `errors` map:
```json
{
  "status": 400,
  "errors": {
    "name": "must not be blank",
    "email": "must be a well-formed email address"
  }
}
```
