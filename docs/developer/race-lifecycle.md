# Race lifecycle

`RaceStateMachineService` owns every status change (`domain.race`). Each command checks one table of allowed transitions and throws `IllegalStateTransitionException` (HTTP 409) otherwise.

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> GRID: callGrid
    GRID --> PENDING: take the grid call back
    GRID --> RUNNING: start
    RUNNING --> STOPPED: stop
    STOPPED --> RUNNING: start (resume, keeps first start time)
    RUNNING --> FINISHED: finish
    STOPPED --> FINISHED: finish
    PENDING --> FINISHED: abandon
    GRID --> FINISHED: abandon
    RUNNING --> FINISHED: abandon
    STOPPED --> FINISHED: abandon
    note right of FINISHED
        abandon sets finishedAt and abandonedAt.
        restart is not in the table: it works from any state,
        clears the times and the result snapshot, and only
        broadcasts (no RaceStatusChangedEvent).
    end note
```

`callGrid` and the first `start` of a final also require every qualifying heat of its class to be finished (an abandoned heat counts as finished).

## What happens when a race reaches FINISHED

`move(race, FINISHED)` in `RaceStateMachineService`:

1. saves the race (so listeners read its new status and times from the database);
2. publishes `RaceStatusChangedEvent` and broadcasts to `/topic/race/{id}/state`;
3. `applyFinishingOrderToNextRace`: a practice or qualifier heat sets the starting grid of the same heat in the next round; a lower final promotes bump-up drivers into the next final up and broadcasts a bump-up alert;
4. `ResultSnapshotService.snapshot` persists the result snapshot (the only time positions are stored);
Finals are not seeded by the finish: an official presses Calculate finals, and `AutoFinalsSeedingService.reseed` seeds them from the snapshots; `FinalsLineupService` then edits the grids until a final is called. Once every qualifying heat is finished the cockpit gives way to the finals line-up screen (`FinalsLineupService.review`, `events.finals_lineup_confirmed_at`) until race control confirms the line-ups; seeding again clears the confirmation.

## Rounds around a race

```mermaid
flowchart LR
    P[PRACTICE round] -->|finishing order = next practice grid only| Q[QUALIFIER rounds]
    Q -->|each heat's finishing order = same heat's grid in the next round| Q
    Q -->|all heats finished, official presses Calculate finals| S[finals calculated, then reviewed and edited in the line-up panel]
    S --> F[FINAL rounds: A, B, C ...]
    F -->|B/C finish: bump-up| F
```

Practice does not set qualifying order (qualifying starts in car number order); qualifier to qualifier does. Heat 1 of a class starts in car number order; every later round starts each heat in the finishing order of the same heat in the round before, taken from the stored result so penalties and marshal laps count, and re-applied if a correction changes it before that heat starts. Cars that did not appear in the result start behind the finishers. Car numbers never change with the grid.

## The event around the races

The race day has its own states (`domain.event`). `EventStateMachineService` only checks the transition; `EventService.transition` saves it, and completing an event publishes `EventCompleted`, which asks for a day-close [results export](sequences.md#results-export-to-racehub).

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> PUBLISHED
    PUBLISHED --> OPEN
    PUBLISHED --> DRAFT
    OPEN --> ENTRIES_CLOSED
    ENTRIES_CLOSED --> IN_PROGRESS
    IN_PROGRESS --> COMPLETED
    COMPLETED --> [*]
```
