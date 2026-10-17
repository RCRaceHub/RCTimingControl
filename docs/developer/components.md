# Components

One Spring Boot process, one SQLite file. The packages below are under `dev.monkeypatch.rctiming`.

```mermaid
flowchart LR
    subgraph venue[Venue network]
        dec[AMB decoder<br/>RC-4 text, TCP 5100]
        br[Browsers<br/>race control, announcer, boards]
    end

    subgraph app[RCTimingControl process]
        direction TB
        dl[timing.DecoderListener<br/>Netty, own thread]
        te[timingExecutor<br/>single timing thread]
        lts[timing.LapTimingService<br/>in-memory LiveRaceState]
        hub[timing.LiveTimingHub<br/>STOMP broadcast]
        api[api.*<br/>REST controllers]
        sec[security.*<br/>JWT filter and STOMP interceptor]
        dom[domain.*<br/>entities and jOOQ repositories<br/>write side]
        svc[service.*<br/>round generation, seeding, marshal, referee]
        qry[query.*<br/>jOOQ projections<br/>read side]
        sm[domain.race.RaceStateMachineService]
        rx[resultsexport.*<br/>outbox and sender]
        lf[livefeed.*<br/>publisher]
        bk[backup.*, retention.*]
        db[(SQLite file<br/>WAL, one writer + read pool)]
    end

    rh[RaceHub<br/>cloud]
    relay[Live feed relay<br/>remote viewers]

    dec -->|passings| dl --> te --> lts
    lts --> hub -->|/topic/race/id/timing| br
    br -->|REST + JWT| sec --> api
    api --> svc --> dom --> db
    api --> sm
    api --> qry --> db
    sm --> dom
    sm -->|RaceStatusChangedEvent| rx
    sm -->|RaceStatusChangedEvent| lf
    sm --> hub
    lts -.->|reads the running race| dom
    rx -->|Results Export v1, HTTPS| rh
    rh -.->|Entry Export v1 file, imported by an official| api
    lf -->|Live Feed v1, WebSocket| relay
    bk --> db
```

## Reading the picture

- **Write side and read side.** Entities and their `@Repository` classes (`domain.*`) change data; `query.*` builds views in SQL. Both go through jOOQ. See [architecture](../architecture.md#cqrs-lite-split).
- **The timing thread.** The decoder thread only hands passings to `timingExecutor`; they are handled one at a time, in decoder order, and that thread is the only one that updates a race's `LiveRaceState`. Live positions are never written to the database during a race.
- **Events.** `RaceStatusChangedEvent` is how the state machine tells audio, the race clock, the live feed, results export and practice that a race moved, without depending on them.
