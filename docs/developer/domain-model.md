# Domain model

Entities are plain classes holding ids, not associations, so the diagrams show ids as the links. Each entity has a `@Repository` extending `persistence/JooqRepository`.

## Entities (write side)

```mermaid
classDiagram
    direction LR
    class Event {
        name
        eventDate
        trackId
        racehubEventId
        liveFeedEnabled
        resultsExportRevision
    }
    class EventClass {
        eventId
        racingClassId
        templateId
    }
    class Competitor {
        displayName
        externalId (RaceHub driver)
        brcaNumber
        homeClub
    }
    class Entry {
        competitorId
        eventId
        eventClassId
        transponderNumberSnapshot
        secondaryTransponderNumber
        externalEntryId
        externalEntryVersion
        withdrawnAt
    }
    class Round {
        eventId
        type : RoundType
        roundNumber
        sequenceInEvent
    }
    class Race {
        roundId
        eventClassId
        heatNumber
        finalLetter
        status : RaceStatus
        startedAt
        finishedAt
        abandonedAt
        bumpSlots
    }
    class RaceEntry {
        raceId
        entryId
        gridPosition
        carNumber
    }
    class ResultSnapshot {
        raceId
        positionsJson
        timedPositionsJson
        lapHistoryJson
    }
    class MarshalAdjustment
    class Penalty
    class IncidentReport

    Event "1" --> "*" EventClass
    Event "1" --> "*" Round
    Event "1" --> "*" Entry
    Competitor "1" --> "*" Entry
    EventClass "1" --> "*" Entry
    Round "1" --> "*" Race
    EventClass "1" --> "*" Race
    Race "1" --> "*" RaceEntry
    Entry "1" --> "*" RaceEntry
    Race "1" --> "0..1" ResultSnapshot
    Race "1" --> "*" MarshalAdjustment
    Race "1" --> "*" Penalty
    Race "1" --> "*" IncidentReport
    Entry "1" --> "*" MarshalAdjustment
```

Transponders belong to an `Entry`, not to a `Competitor`: a primary and an optional secondary number per entry, not unique across the system.

## Services around the race (write side)

```mermaid
classDiagram
    direction TB
    class RaceStateMachineService {
        callGrid(Race)
        start(Race)
        stop(Race)
        finish(Race)
        abandon(Race)
        restart(Race)
    }
    class LapTimingService {
        onLapPassing(LapPassingEvent)
        applyMarshalAdjustment()
        applyLapPenalty()
        releaseState(raceId)
    }
    class LiveRaceState {
        applyLapPassing()
        calculatePositions()
        runtime transponder links
    }
    class LiveTimingHub {
        broadcastTimingUpdate()
        broadcastStateChange()
        broadcastUnknownTransponder()
    }
    class ResultSnapshotService {
        snapshot(raceId)
    }
    class RoundGeneratorService
    class BumpUpSeedingService
    class AutoFinalsSeedingService
    class DecoderListener

    DecoderListener ..> LapTimingService : LapPassingEvent
    LapTimingService o-- LiveRaceState : one per running race
    LapTimingService --> LiveTimingHub
    RaceStateMachineService --> LapTimingService
    RaceStateMachineService --> LiveTimingHub
    RaceStateMachineService --> ResultSnapshotService
    RaceStateMachineService --> RoundGeneratorService
    RaceStateMachineService --> BumpUpSeedingService
    RaceStateMachineService --> AutoFinalsSeedingService
```

## Tables (entity relationship)

The core of the SQLite schema (`app/src/main/resources/db/migration/sqlite`). Audit tables, tokens and the practice tables are left out.

```mermaid
erDiagram
    tracks ||--o{ events : "hosts"
    events ||--o{ event_classes : ""
    events ||--o{ rounds : ""
    events ||--o{ entries : ""
    events ||--o{ results_outbox : ""
    racing_classes ||--o{ event_classes : ""
    race_format_templates ||--o{ event_classes : "snapshot at assignment"
    competitors ||--o{ entries : ""
    event_classes ||--o{ entries : ""
    rounds ||--o{ races : ""
    event_classes ||--o{ races : ""
    races ||--o{ race_entries : ""
    entries ||--o{ race_entries : ""
    races ||--o| result_snapshots : ""
    races ||--o{ marshal_adjustments : ""
    races ||--o{ penalties : ""
    races ||--o{ incident_reports : ""
    entries ||--o{ marshal_adjustments : ""
    championships ||--o{ championship_classes : ""
    championships ||--o{ championship_event_links : ""
    events ||--o{ championship_event_links : ""
    racing_classes ||--o{ championship_classes : ""
    users ||--o{ user_roles : ""
```
