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
        rs[resultsexport.ResultsSiteService<br/>static results pages]
        lf[livefeed.*<br/>publisher]
        bk[backup.*, retention.*]
        tls[infrastructure.tls.*<br/>club CA, server certificate,<br/>HTTPS connector]
        tlsfiles[(tls folder<br/>ca.p12, server.p12)]
        cs[domain.club.ClubSettingsService<br/>settings file export and import]
        props[infrastructure.settings.SettingsFile<br/>application.properties]
        db[(SQLite file<br/>WAL, one writer + read pool)]
    end

    rh[RaceHub<br/>cloud]
    relay[Live feed relay<br/>remote viewers]

    dec -->|passings| dl --> te --> lts
    lts --> hub -->|/topic/race/id/timing, battles| br
    br -->|REST + JWT| sec --> api
    api --> svc --> dom --> db
    api --> sm
    api --> qry --> db
    sm --> dom
    sm -->|RaceStatusChangedEvent| rx
    sm -->|RaceStatusChangedEvent| lf
    sm -->|RaceStatusChangedEvent| rs
    rs -->|reads the export document| qry
    sm --> hub
    te -.->|finds the running race| dom
    lts -.->|resolves transponder to entry| dom
    rx -->|Results Export v1, HTTPS| rh
    rh -.->|Entry Export v1 file, imported by an official| api
    api -->|asks which events are ready, HTTPS, club key| rh
    lf -->|Live Feed v1, WebSocket| relay
    bk --> db
    br -->|HTTPS, optional| tls
    tls --> tlsfiles
    api --> cs --> dom
    api -->|HTTPS switch, next start| props
    tls -.->|ca.crt download| api
```

## Reading the picture

- **Write side and read side.** Entities and their `@Repository` classes (`domain.*`) change data; `query.*` builds views in SQL. Both go through jOOQ. See [architecture](../architecture.md#cqrs-lite-split).
- **The timing thread.** The decoder thread only hands passings to `timingExecutor`; they are handled one at a time, in decoder order, and passings are applied only on that thread. Marshal laps, penalties and transponder links come from request threads, and `LiveRaceState`'s locking serialises them with the passings. Live positions are never written to the database during a race.
- **HTTPS.** When `rctiming.https.enabled` is on, `HttpsConnectorCustomizer` adds a second Tomcat connector beside HTTP. `TlsService` keeps a club certificate authority and a server certificate in the data folder's `tls` folder, re-issues the server certificate when the laptop gains a name or it nears expiry (checked every ten minutes), and tells the connector to reload. Only the authority's certificate is served, at `/api/v1/about/ca.crt`.
- **Events.** `RaceStatusChangedEvent` is how the state machine tells audio, the race clock, the live feed, results export and practice that a race moved, without depending on them.
