# Live Feed v1

Live Feed v1 is what RCTimingControl sends to a relay so that people away from the track can follow a race as it runs (#28). The app at the venue never takes connections from the internet: it opens one outbound WebSocket to the relay and pushes messages down it. The relay passes them on to viewers.

- The JSON Schema is `app/src/main/resources/livefeed/live-feed-v1.schema.json` (draft 2020-12).
- A worked example is `app/src/test/resources/livefeed/live-feed-v1-example.json`. A test checks that the app writes exactly that file for its example data and that it passes the schema.
- The Java model is `livefeed/LiveFeedV1`, and `livefeed/LiveFeedPublisher` sends it.

## What it carries

Each message is one race as it stands: the whole running order, not a change since the last message. A viewer can show any message it gets without having seen the ones before.

| Part | Contents |
|------|----------|
| Top level | `schema_version` (always `1`), `type` (always `"race"`), `sequence` and `sent_at` |
| `event` | `rctc_event_id`, `name`, `date` |
| `race` | `rctc_race_id`, `class_name`, `round_type` (`PRACTICE`, `QUALIFIER` or `FINAL`), `round_number`, `heat_number`, `final_letter` (`Z` is a Shikabane zombie round), `status` (`GRID`, `RUNNING`, `STOPPED`, `FINISHED`, or `PENDING` after a restart) and `clock` |
| `race.clock` | `elapsed_ms` (race time so far, not counting time stopped), `duration_ms` and `remaining_ms` (null when the format sets no length) and `running` |
| `standings[]` | In position order: `position`, `display_name`, `car_number`, `laps`, `last_lap_ms`, `best_lap_ms`, `gap_to_leader_ms`, `gap_to_ahead_ms` and `laps_down` |

Every car in the race is listed. Cars that haven't crossed the line yet come after the rest in grid order, with 0 laps, so a race on the grid lists its whole field. A car one or more laps behind the leader has `laps_down` above 0 and no time gaps. A race's last message, when it finishes, carries the finishing order.

`sequence` goes up with every message, across all races, so a viewer can ignore a message older than one it already has. It starts from the app's clock time in milliseconds when the app starts, so it keeps going up across restarts, and it can skip numbers (for messages that couldn't be sent while the relay was down).

The clock in a message is right when it is sent. While `running` is true a viewer can count it on locally between messages, as the test viewer does.

### Display names only

The feed is meant to be public, so it names people only by the display name shown on the venue's own boards. It carries no transponder numbers, RaceHub ids, BRCA numbers, clubs or contact details. The schema refuses any field in a standing that it doesn't name, and a test checks it.

## When it is sent

Nothing is sent until the feed is set up (see below) **and** the race director turns it on for the event, with the **Send this event** switch in race control's status bar. The switch is saved per event and is off for a new event.

For an event with the feed on, each race is sent from the moment it is called to the grid:

- once a second at most, whenever its running order, status or length changes;
- every 5 seconds even when nothing changes, so a viewer joining late catches up and clocks stay right;
- one last time when it finishes (or goes back to pending on a restart), and then no more.

The connection is closed after 2 minutes with nothing to send, and opened again for the next race.

## How it is sent

```http
GET <rctiming.livefeed.relay-url>
Upgrade: websocket
Authorization: Bearer <rctiming.livefeed.token>
```

The app connects to the relay with the club's key and sends each message as one WebSocket text frame. It never expects anything back. The relay should refuse a connection with the wrong key.

The address must be `wss`, since the key goes with the connection; plain `ws` is accepted only to the laptop itself, for testing. See [installing.md](installing.md#sending-a-live-feed) for the settings.

### Dropouts

The feed runs on a thread of its own and only reads the live timing that race control already works out. A slow or missing relay delays only that thread, so timing, race control, the boards and the announcer carry on as normal.

When the connection drops, the app tries again after 1 second, then after a wait that doubles each time up to 30 seconds. When it is back, every race being followed is sent again in full straight away, so a relay that restarted has the current state at once. Messages the relay missed while it was down are not sent: the next message replaces them.

Race control shows the feed's state next to the decoder's: **ready** (connected or able to connect, nothing to send), **connecting**, **sending** or **reconnecting**. It is hidden when the feed isn't set up.

## The test relay

`decoder-simulator/` has a small relay and viewer page for trying the feed out without a real relay. Start it on the same laptop as the app:

```bash
RCTimingControl relay                       # an installed app
./gradlew :decoder-simulator:runRelay       # a development checkout
```

It takes `--port` (default `8099`), `--token` (default `dev-relay-token`) and `--bind` (default `127.0.0.1`), and prints the two settings to give the app. Put those in `application.properties` in the data folder, restart the app, turn the feed on for an event in race control and start a race. The viewer page at <http://localhost:8099/> then shows it live. Stopping the relay and starting it again shows the feed coming back.

The test relay keeps the newest message for each race and gives it to a viewer as soon as it connects. It keeps nothing on disk and isn't meant to face the internet.

## Changing the format

A change that a reader of v1 could misread needs `schema_version` 2. Adding a field to the top level, `event`, `race` or `clock` is not such a change: a reader should ignore fields it doesn't know. A new field in a standing needs the schema changed too, since standings refuse unknown fields to keep personal data out. Update the schema, the example file and this page together.
