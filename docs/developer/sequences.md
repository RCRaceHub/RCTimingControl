# Sequences

Each diagram follows one thing through the code. Class names are the real ones, under `dev.monkeypatch.rctiming`.

- [A lap passing, decoder to browser](#a-lap-passing-decoder-to-browser)
- [Starting and finishing a race](#starting-and-finishing-a-race)
- [Results export to RaceHub](#results-export-to-racehub)
- [Officials' printouts](#officials-printouts)
- [The overlay's chart and ticker](#the-overlays-chart-and-ticker)
- [Live feed to the relay](#live-feed-to-the-relay)
- [Importing entries from RaceHub](#importing-entries-from-racehub)
- [Signing in and staying signed in](#signing-in-and-staying-signed-in)

## A lap passing, decoder to browser

The decoder thread never touches the database. Everything after `timingExecutor.execute` runs on the one timing thread, so passings are handled one at a time in decoder order.

```mermaid
sequenceDiagram
    participant Dec as AMB decoder
    participant DL as DecoderListener<br/>(Netty thread)
    participant TE as timingExecutor<br/>(timing thread)
    participant RR as RaceRepository
    participant LTS as LapTimingService
    participant WU as WarmUpCheckService
    participant LRS as LiveRaceState
    participant Hub as LiveTimingHub
    participant B as Browsers

    Dec->>DL: RC-4 PASSING line (transponder, time)
    DL->>TE: onPassing(generation, passing)
    TE->>TE: drop it if the source was retired meanwhile
    TE->>RR: findFirstByStatus(RUNNING)
    RR-->>TE: running race id, or NO_RACE
    TE->>LTS: publish LapPassingEvent(raceId, transponder, rtcTime)
    alt no race is running
        LTS->>LTS: ignore (practice listens to the same event)
        opt a race is on the grid and the number is one of its entries
            Note over WU: a second listener on the same event
            WU->>Hub: broadcastWarmUp(raceId, seenEntryIds), first sighting only
            Hub-->>B: /topic/race/id/warmup
        end
    else a race is running
        LTS->>LRS: getRuntimeLink(transponder), else resolveEntryId
        LTS->>LRS: applyLapPassing(event, entryId, now, windowEnded)
        opt no entry matches (or more than one), first sighting of that transponder
            LTS->>Hub: broadcastUnknownTransponder(raceId, transponder)
            Hub-->>B: /topic/race/id/unknown-transponder
        end
        opt a stagger start and the car crossed before its start offset
            LTS->>LRS: recordJumpStart(entryId, earlyMs), first time only
            LTS->>Hub: broadcastJumpStarts(raceId, jumpStarts)
            LTS-->>LTS: publish JumpStartDetectedEvent (JumpStartAudit writes JUMP_START_DETECTED)
            Hub-->>B: /topic/race/id/jump-start
        end
        LTS->>Hub: broadcastTimingUpdate(raceId, calculatePositions())
        Hub-->>B: /topic/race/id/timing
        LTS->>LRS: advanceBattles(rows), BattleTracker
        LTS->>Hub: broadcastBattles(raceId, battles)
        Hub-->>B: /topic/race/id/battles
        opt every car has had its whole window
            LTS->>LTS: publish AllCarsFlaggedEvent(raceId)
            Note over LTS: AutoFinish finishes the race
        end
    end
```

A car that crosses the line before its turn in a stagger start is flagged to the referee, who decides on a penalty or takes no action; the passing is still timed. The detection is audited as `JUMP_START_DETECTED`. Taking no action (`RefereeService.dismissJumpStart`) writes a `JUMP_START_DISMISSED` audit row and marks the entry dismissed in `LiveRaceState`; the list is broadcast again, the alert goes and the entry keeps its lozenge. A penalty dismisses it the same way and writes a `JUMP_START_PENALISED` row beside its `PENALTY_APPLIED` one.

An unknown transponder is never silently credited: it goes to the race director, who links it to an entry (`TransponderLinkService`), and the link applies back over the passings already seen.

## Starting and finishing a race

```mermaid
sequenceDiagram
    participant RD as Race director (browser)
    participant C as api.racecontrol controller
    participant SM as RaceStateMachineService
    participant RRp as RaceRepository
    participant Bus as ApplicationEventPublisher
    participant Hub as LiveTimingHub
    participant RS as ResultSnapshotService

    RD->>C: POST start
    C->>SM: start(race)
    SM->>SM: requireAllowed(RUNNING), requireQualifyingComplete
    SM->>RRp: save(race with startedAt)
    SM->>Bus: RaceStatusChangedEvent(RUNNING)
    Note over Bus: LapTimingService, RaceClockService, AutoRunService,<br/>results export, live feed, practice and audio react
    SM->>Hub: broadcastStateChange(RUNNING)
    Hub-->>RD: /topic/race/id/state

    RD->>C: POST finish
    C->>SM: finish(race)
    SM->>RRp: save(race with finishedAt)
    SM->>Bus: RaceStatusChangedEvent(FINISHED)
    SM->>Hub: broadcastStateChange(FINISHED)
    SM->>SM: applyFinishingOrderToNextRace (grid for the next heat, or bump-up)
    SM->>RS: snapshot(raceId)
    RS->>RS: positions from LiveRaceState, saved as a ResultSnapshot
    RS->>RS: after commit, LapTimingService.releaseState(raceId)
```

Shikabane eliminations are not state changes either: `RaceEliminationService` saves a `RaceElimination` row for a car marked dead (or a revival), and `ResultSnapshotService` reads the active rows when it works out the heat result, ordering the dead after the survivors through `ShikabaneOrdering`. On a finished race it publishes `FinishedRaceCorrected` so the result is rebuilt. When a Shikabane heat finishes, `RaceStateMachineService` calls `ShikabaneGridService` after the snapshot to draw the next heat's grid (reverse of the result, then cumulative `ShikabaneHeatScores`). A Shikabane class with `mains` above 0 has finals too: `AutoFinalsSeedingService` (on the official's Calculate finals) seeds them through `BumpUpSeedingService` using the config's `mainGridSize` and `promotionSpots`, and a finishing lower main promotes its best finishers as in a bump-up format. A zombie round is a final with letter `Z` that `BumpUpSeedingService` leaves out of the seeding, fills with everyone outside A when the main below A finishes, and whose winner is promoted into A through the extra bump place A is given.

Marshal laps are not state changes: `MarshalService` saves a `MarshalAdjustment` and calls `LapTimingService.applyMarshalAdjustment`, which recalculates and rebroadcasts positions.

## Race audio routing

Race control works out every sound (horns, beeps, spoken lines) in the browser; nothing about audio is decided on the server. Each sound goes through `routeCue` in `lib/audioOutput.ts`, which looks up the outputs the club ticked for it in `lib/audioRouting.ts` (kept in `localStorage`). It plays the sound on race control's tab if that output is ticked and sends the rest to the other tabs of the same browser over a `BroadcastChannel`. An audio output page announces itself on the channel every three seconds; a sound ticked for an output that has not been heard from for six seconds plays on race control instead of being lost. An audio output page (`/audio-output/{id}`) listens for cues meant for its output and plays them on its sound card (`setSinkId`) and speaker side (a stereo pan), speaking text, cut into pieces of at most 100 characters and played in turn, with the club's Piper voice because the browser voice cannot leave the default card.

```mermaid
sequenceDiagram
    participant RC as Race control tab
    participant Route as routeCue
    participant Store as Routing (localStorage)
    participant Chan as BroadcastChannel
    participant Out as Audio output tab
    participant Piper as Server (Piper preview)

    RC->>Route: routeCue(sound, volume, cue)
    Route->>Store: outputs ticked for this sound
    alt race control ticked
        Route->>RC: play horn, beep, clip or browser voice here
    end
    Route->>Chan: cue for the other ticked outputs
    Chan-->>Out: cue (only if its output id is listed)
    alt cue is a horn or beep
        Out->>Out: play on the chosen sound card and side
    else cue is a spoken line
        Out->>Piper: POST spoken-name/preview (text)
        Piper-->>Out: wav, played on the chosen sound card
        Note over Out: browser voice on the default card if Piper is down
    end
```

## Results export to RaceHub

The export is an outbox, so a finish never waits on the network and a crash loses nothing.

```mermaid
sequenceDiagram
    participant SM as RaceStateMachineService
    participant T as ResultsExportTriggers
    participant ES as ResultsExportService
    participant DB as SQLite (events, results_outbox)
    participant S as ResultsSender
    participant RH as RaceHub

    SM-->>T: RaceStatusChangedEvent(FINISHED)<br/>(also FinishedRaceCorrected, EventCompleted)
    T->>ES: requestForRace(raceId, reason)
    ES->>DB: mark the event "export pending" (same transaction as the change)
    loop every ~2 s (scheduler thread)
        T->>ES: pendingEventIds()
        T->>ES: queuePending(eventId)
        ES->>DB: build Results Export v1, insert outbox row
    end
    loop every ~15 s (scheduler thread)
        S->>DB: due outbox rows
        S->>RH: POST results (idempotency key per item)
        alt accepted
            S->>DB: mark sent
        else failed
            S->>DB: record the error, retry later with a growing wait
        end
    end
```

Format: [Results Export v1](../results-export-v1.md).

## Officials' printouts

The round summary, final results, meeting pack and heat result are print-styled web pages (the browser does the paper or Save as PDF). They read from one officials-only endpoint, so they show real names where the public pages follow each competitor's public name setting, and they add the grid each driver started from.

```mermaid
sequenceDiagram
    participant B as Browser (print page)
    participant C as RaceControlController
    participant Q as EventResultsQuery
    participant R as ResultSnapshotQuery
    participant DB as SQLite (read pool)

    B->>C: GET /race-control/event/{id}/results (or /race/{id}/result)
    C->>Q: forEvent / forRace
    Q->>DB: finished races in run order, grid positions
    loop each finished race
        Q->>R: load(raceId)
        R->>DB: stored result snapshot and club branding
    end
    Q-->>C: EventResultsDto (real names, start positions)
    C-->>B: JSON
    B->>B: group by class, round or finals, then window.print()
```

The qualifying standings in a meeting pack come from the existing `GET /race-control/event/{id}/qualifying`. The championship sheet is the public standings page plus `GET /championships/{id}/info` for the class names.

## The overlay's chart and ticker

The streaming overlay and the Live race screen (`/boards/live-race`) all draw the same position chart. The running order comes live over STOMP; the places lap by lap come from one anonymous read that is repeated whenever the field's total laps change. The ticker's text is read on a slow poll. The chart read holds `LiveRaceState`'s lock only to copy the rows and lap times; the ordering is worked out after the lock is released.

```mermaid
sequenceDiagram
    participant B as Browser (OBS)
    participant BC as BoardController
    participant LRS as LiveRaceState
    participant LO as LapOrder
    participant BTS as BoardTickerService
    participant Q as BoardQuery,<br/>PreRaceReadinessQuery,<br/>QualifyingTableService

    B->>BC: GET /boards/races/{id}/position-history<br/>(when total laps change)
    BC->>LRS: lapOrder() (under the state lock)
    LRS->>LO: LapOrder.of(rows, state)
    LO-->>BC: places(): lap, entry, position
    BC-->>B: [{lap, entryId, position}]
    B->>BC: GET /boards/ticker (every 20 s)
    BC->>BTS: forEvent(eventId)
    BTS->>Q: next race, grid call, marshal duty, top qualifiers
    BTS-->>BC: names through PublicNames
    BC-->>B: TickerDto
```

The Now and next board polls the same ticker endpoint for the next drivers and marshals.

The Live top screen (`/boards/top`) and the overlay's timing tower (the projected qualifying place on each row of a qualifying heat) poll `GET /boards/live-qualifying` every three seconds. `BoardController` picks the qualifying heat (the one on track, else the last one finished) and passes the heat's live rows from `LapTimingService.peek` to `LiveQualifyingService`. That asks `QualifyingStandingsService.projectedResultsFor` twice: once with the heat counted (`Overlay.live`, or `Overlay.stored` after it finished) and once without it (`Overlay.without`) for the places before. Only the board's response is built; nothing is stored.

```mermaid
sequenceDiagram
    participant B as Browser (/boards/top)
    participant BC as BoardController
    participant LTS as LapTimingService
    participant LQ as LiveQualifyingService
    participant QS as QualifyingStandingsService
    B->>BC: GET /boards/live-qualifying (every 3 s)
    BC->>LTS: peek(raceId).calculatePositions()
    BC->>LQ: forHeat(raceId, liveRows)
    LQ->>QS: projectedResultsFor(class, Overlay.live)
    LQ->>QS: projectedResultsFor(class, Overlay.without)
    LQ-->>BC: rows with position, previous place, live, waiting
    BC-->>B: LiveQualifyingDto
```

A saved board view (`/boards/view/{slug}`) reads only its layout from `GET /boards/views/{slug}` (`PublicBoardViewController` and `BoardViewService`). The page follows the race on track once through `useBoardRace` and each panel then polls the same public endpoints as the stand-alone boards, so a view adds no timing load of its own.

`LapOrder` is also what `ResultSnapshotService` uses to store the lap history of a finished race, so the printed chart and the live chart agree.

## Live feed to the relay

```mermaid
sequenceDiagram
    participant P as LiveFeedPublisher<br/>(own thread, 1 s tick)
    participant LTS as LapTimingService
    participant Conn as LiveFeedConnection
    participant R as Relay
    participant V as Remote viewers

    loop every second
        P->>LTS: running order of each race on the grid or running<br/>in an event with the feed on
        alt order or status changed, or a few seconds passed
            P->>Conn: send Live Feed v1 message
            Conn->>R: over the one outbound WebSocket
            R-->>V: fan out
        end
    end
    Note over P,Conn: A slow or missing relay only delays this thread.<br/>A dropped connection is reopened with a growing wait.
```

Format: [Live feed v1](../live-feed-v1.md).

## Importing entries from RaceHub

```mermaid
sequenceDiagram
    participant O as Official (browser)
    participant C as api.admin import controller
    participant I as RaceHubImportService
    participant DB as SQLite

    O->>C: POST racehub-import?dryRun=true (Entry Export v1 file)
    C->>I: importEntries(actor, eventId, export, dryRun = true)
    I->>I: check schema_version = 1 and revision
    I->>DB: read class mappings, existing entries and competitors
    I-->>O: RaceHubImportResult (new, changed, withdrawn, unmapped classes, warnings, errors)
    O->>C: same POST with dryRun=false (apply)
    C->>I: importEntries(actor, eventId, export, false)
    alt errors or unmapped classes
        I-->>O: nothing is applied
    else clean
        I->>DB: upsert competitors and entries by entry_id<br/>(higher entry_version wins, withdraw never delete)
        I->>DB: audit rows
        I-->>O: result
    end
```

Entries are never created for a racer login: competitors have no account.

## Signing in and staying signed in

```mermaid
sequenceDiagram
    participant B as Browser
    participant A as AuthService
    participant J as JwtTokenService
    participant F as JwtAuthenticationFilter
    participant W as WebSocketJwtChannelInterceptor

    B->>A: POST /api/v1/auth/login (email, password)
    A->>A: check password, official, enabled (audit every attempt)
    A->>J: generateAccessToken(user) (15 min by default)
    A-->>B: access token + refresh token
    B->>F: REST with Authorization: Bearer
    F->>F: valid token signs the request in, else anonymous
    Note over F: SecurityConfig then decides if anonymous is enough
    B->>W: STOMP CONNECT with the token
    W->>W: anonymous may subscribe to a race's timing and state topics only, and never send
    B->>A: POST /api/v1/auth/refresh before expiry
    A->>A: rotate (compare-and-set), reuse of an old token is refused
    A-->>B: new access token + new refresh token
```
