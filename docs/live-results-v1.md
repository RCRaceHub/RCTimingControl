# Live Results v1

While a race is on, RCTimingControl sends its running order to RaceHub every few seconds, so RaceHub can show the standings as they happen. This is separate from [Results Export v1](results-export-v1.md), which sends the finished results and stays authoritative.

## What is sent

One snapshot per race on the grid, running or stopped, as a `POST` to `<RaceHub address>/api/rctc/v1/live` with the club's key (the same bearer key as results). The body is `type: "live_results"` with its own `schema_version` (1), and:

- `provisional`: always `true`. The order is as timed so far, before penalties or corrections.
- `racehub_event_id`, `event` and `race`: which event and race, its `status` (`GRID`, `RUNNING`, `STOPPED`, `FINISHED`, or `PENDING` if race control restarted it) and the race clock.
- `standings`: the running order, then cars on the grid that have not crossed the line. Display names only, as on the live feed; no ids and nothing personal.
- `sequence`: counts up across every snapshot and starts from the clock, so RaceHub keeps only the highest one it has seen.

## When it is sent

- A lap changes the order, and the snapshot goes out at most every 3 seconds per race.
- A race with no change is sent again every 15 seconds, so RaceHub knows the feed is alive.
- When a race finishes, one last snapshot says it is over. The finished race still arrives through Results Export v1, and RaceHub should then drop its live snapshot.

Only events imported from RaceHub are sent, and only when the club's results publishing choice includes RaceHub and the RaceHub address and key are set.

## If RaceHub is slow or down

Nothing waits for it. A snapshot is the whole race as it stands, so nothing is queued or retried: after a failure RCTC waits 5 seconds, doubling to 60, and then sends a fresh snapshot. Timing and race control are never held up. A RaceHub that does not have the `/live` route yet answers 404 and is treated the same way.

## Settings

See `rctiming.racehub.live.*` in [development.md](development.md): `enabled`, `min-interval-ms` and `resend-every-ms`.
