# Results Export v1

Results Export v1 is the file RCTimingControl sends back to RaceHub with an event's results (#27). It is the return half of the boundary that RaceHub's Entry Export v1 starts: entries come in by import, and results go out in this file, keyed by the same RaceHub ids.

- The JSON Schema is `app/src/main/resources/resultsexport/results-export-v1.schema.json` (draft 2020-12).
- A worked example is `app/src/test/resources/resultsexport/results-export-v1-example.json`. A test checks that the app writes exactly that file for its example data and that it passes the schema, so the two can't drift apart.
- The Java model is `resultsexport/ResultsExportV1`, and the query that fills it is `query/resultsexport/ResultsExportQuery`.

## What it carries

Each export is the **whole event as it stands**, not just the race that changed. RaceHub can replace what it holds for the event with each one it accepts.

| Part | Contents |
|------|----------|
| Top level | `schema_version` (always `1`), `revision`, `generated_at`, and `source` (`system` and the club's name) |
| `event` | `racehub_event_id` (the RaceHub event the entries were imported from), `rctc_event_id`, `name`, `date`, `status` |
| `races[]` | Every finished race, in running order: its class (`rctc_event_class_id`, the `racehub_event_class_ids` that the entries on its grid were booked under, finishers or not, and `class_name`), `round_type`, `round_number`, `heat_number`, `final_letter`, `status` (`FINISHED` or `ABANDONED`) and `finished_at` |
| `races[].results[]` | One row per finisher: `position`, `laps`, `total_time_ms`, `best_lap_ms`, `car_number`, `display_name`, the RaceHub ids, RCTC's own ids and the `penalties` given in that race |
| `championships[]` | For each championship the event is a round of: its `round_number`, and per class the full `standings` as they stand now, with this event's position and points for each driver and whether they were dropped or excluded |

Points belong to championship rounds, not to single races, so they appear only in `championships`.

### Ids

A result row for an entry imported from RaceHub has `external_source: "RACEHUB"`, and carries RaceHub's `entry_id`, `driver_profile_id` and `racehub_event_class_id`. The schema requires `entry_id` and `driver_profile_id` on such a row.

A result row for a walk-in added at the track has `external_source: null` and null RaceHub ids. It is identified by `rctc_entry_id` and `rctc_competitor_id` instead. Every row carries the RCTC ids, so RaceHub can match a walk-in across exports. An entry from anywhere other than RaceHub, such as the RC-Timing CSV import or another booking system's entry file, is sent the same way, since RaceHub doesn't know its ids.

A standing belongs to a driver, not an entry, so its `external_source` and `driver_profile_id` come from the driver's competitor record: a RaceHub driver who also ran as a walk-in is still named by their RaceHub id. A standing with `external_source: "RACEHUB"` always has a `driver_profile_id`.

### What it leaves out

No contact details, date of birth, guardian or payment data: RCTC doesn't hold any. No laps or passings: RaceHub gets summaries only (open question O6 in the local-only plan defaults to summaries).

### Penalties

Each penalty has `included_in_result`, which says whether the row's `laps`, `total_time_ms` and `position` already allow for it. Every penalty given since the race last started is included: a `LAP` penalty given while the race ran came straight off the live lap count, and the rest are applied when the result is stored or corrected (#63). A penalty left over from a run that was restarted has `included_in_result: false`.

A race whose result was stored by a version before #63, and not corrected since, keeps its result as timed: only a `LAP` penalty given while it ran is included, and a reader that re-ranks it should apply the penalties with `included_in_result: false`.

A lap adjustment by the race director after the finish isn't listed, but the row's `laps` and `position` allow for it.

### Abandoned races

An abandoned race is listed with `status: "ABANDONED"`. Its `results` are the order when it was abandoned, for information only.

## When it is sent

Only events whose entries were imported from RaceHub are sent, since those are the ones with a `racehub_event_id`. An export is queued:

| When | `reason` |
|------|----------|
| A race finishes or is abandoned | `RACE_FINISHED` |
| A finished race is corrected: a referee gives a penalty or the race director adjusts laps | `CORRECTION` |
| The event is marked `COMPLETED` (the race day is closed) | `DAY_CLOSE` |

A correction recalculates the race's stored result before the export is built, so the correction export carries the new laps, times and positions.

Every export for an event takes the next `revision`, starting at 1. A higher revision always replaces a lower one, so RaceHub should ignore an export whose revision is not higher than the one it already holds.

The finish, correction or day close marks the event as needing an export in the same database transaction, so the request survives the app stopping straight afterwards. A background job then builds the export and adds it to the `results_outbox` table, and a background sender posts the oldest one that is due, every 15 seconds. Neither holds up race control: a race finishes the same way whether RaceHub can be reached or not.

An import must carry RaceHub's event id, and an event takes entries from only one RaceHub event; the import refuses a file that breaks either rule.

## How it is sent

```http
POST <rctiming.racehub.results-url>
Authorization: Bearer <rctiming.racehub.token>
Content-Type: application/json
Idempotency-Key: rctc-results-<racehub_event_id>-r<revision>

{ ...Results Export v1... }
```

- Any `2xx` answer means sent.
- Anything else, or no answer, means failed. The export is tried again after 30 seconds, then after a wait that doubles each time up to 30 minutes. A failure holds back only that event's later exports for the round, so an event's exports arrive in revision order while a refused event does not stop the others from being sent.
- When a newer export for the same event is queued, any older one that hasn't gone yet is marked `SUPERSEDED` and never sent, because the newer one carries everything it did.
- The same revision may arrive more than once (for example when RaceHub saved it but its answer was lost). The `Idempotency-Key` and the revision both let RaceHub recognise a repeat and answer `2xx` without changing anything.

The address must be `https`, since the club's key goes with every request; plain `http` is accepted only to the laptop itself, for testing. Without both the address and the key, nothing is sent and exports wait in the queue until they are set. See [installing.md](installing.md#sending-results-to-racehub) for the settings.

## Seeing and downloading exports

Admins see every export, its status (waiting, failed, sent or superseded), the attempts and the last error under **Results to RaceHub** in the admin panel, and can send a waiting one at once. An event's page has a **Download results** button that saves the file as it stands now, for any event, imported from RaceHub or not. See `docs/api.md` in the source repository for the endpoints.

## Changing the format

A change that a reader of v1 could misread needs `schema_version` 2. Adding a field is not such a change: the schema allows fields it doesn't name, and a reader should ignore any it doesn't know. Update the schema, the example file and this page together.
