# Multiple decoders and split times

**Status: idea, under discussion.** Nothing here is built, no issue exists yet, and the decisions below are proposals. It records a design conversation so the work can be picked up later. Check each claim about the code against `main` before relying on it (the notes were written without a full read of the timing code, see [Checked against the code](#checked-against-the-code)).

## Decisions so far

From the design conversation on 2026-11-16. Everything else in this page is still a proposal.

| Question | Decision |
|---|---|
| Hardware | Plan for **several separate decoders**, one per loop. Research the single decoder with several loop inputs as well, to know what hardware it needs (see [Hardware research](#hardware-research)). |
| Splits per track | Varies. Zero must work as today, and the design allows any number. |
| What splits are for | **Display and commentary first, then lap validation** (flagging a lap where a car skipped a loop). Not a scoring input. |
| Clock accuracy | **Spike first, then decide.** Two simulated decoders, measure the error under simple anchoring and under re-anchoring. |

Lap validation brings in a rule the first draft did not have: a lap whose splits show a car skipped a loop needs a defined outcome (flag to the referee, never an automatic penalty), and a *missed read* must be told apart from a *skipped loop*. See [Behaviour to define](#behaviour-to-define).

## Goal

Let a track have more than one timing loop, as in F1 timing: one **finish loop** that defines laps, and any number of **split loops** that cut the lap into zones (sectors). Each split loop is read by its own decoder, or by its own loop input if the hardware has several.

What it gives:

- Sector times per lap, and each driver's sector bests.
- A theoretical best lap (the sum of a driver's best sectors).
- Gaps at each split, so the battle tracker and the boards can show a gap before the finish line, not only once a lap.
- Sector colouring (overall best, personal best) on the boards and the OBS overlay.
- Commentator and announcer notes mid-lap, such as "he took a tenth out of him in the second sector".

## Principles

1. **Only the finish loop defines a lap.** Lap counts, lap times, positions, results, championship points and marshal adjustments work exactly as they do now. A split passing never starts, ends or scores a lap.
2. **Splits are extra data on the lap in progress.** A split belongs to the lap the car is currently on.
3. **A split loop can fail without breaking a race.** A dead decoder, a missed read or a loop with no hits leaves a gap in the split data. It never raises an error, never holds up a lap and never stops timing.
4. **A track with no split loops behaves as today.** Existing clubs change nothing. A single decoder stays the default.
5. **Live state stays in memory.** Consistent with the existing rule not to store live positions: split positions are worked out in memory and broadcast, and each lap's splits are saved when the lap completes.

## What changes

### Decoders

Today the decoder's host and port are one value in the club profile, read by `DecoderListener`, which publishes one state to `/topic/system/decoder-status`.

- Replace it with a **list of decoders**: a name, host, port, and the timing point or points it reads.
- One Netty connection per decoder, each with its own reconnect, each reporting its own connected state. The status topic carries one entry per decoder, and the UI shows which one is down.
- The decoder's own `decoderId` field in the RC-4 passing record identifies where a passing came from. Decide whether to trust it or key on the connection (see open questions).
- A club with one decoder sees no change in the setup screens until it adds a second.

### Timing points

A track gets an ordered list of **timing points**:

| Field | Meaning |
|---|---|
| name | "Start/finish", "Split 1", "Back straight" |
| kind | `FINISH` (exactly one) or `SPLIT` |
| order | Position round the lap, from the finish loop |
| decoder | Which decoder reads it, and which loop input if the decoder has several |

Sector `n` is the time between point `n-1` and point `n` (the last sector ends at the finish loop). A track with only a finish point has no sectors.

### Data

- A **lap splits** table keyed by lap and timing point, holding the split's timestamp (UTC microseconds, like every other timestamp). A missed read is a missing row.
- Sector times are worked out from consecutive splits and never stored, or are cached only if a query needs it. Calculate on demand from the saved splits, as the championship points already are.
- Flyway migration for the tables. The jOOQ DSL is generated from it. Splits are read through the query module, with an entity and repository in the domain module only if something writes them one at a time.
- Format and track config is snapshot-at-assignment, so a track's timing points are copied into an event the same way, and editing a track later does not change an event already set up.

### Live flow

```
Decoder A (finish) ─┐
Decoder B (split 1) ├─ DecoderListener (one connection each) → timingExecutor → LapTimingService
Decoder C (split 2) ┘
```

- Every passing still goes through the single `timingExecutor`, so handling stays one at a time. Across decoders that order is arrival order, not timestamp order (see [Ordering](#ordering-across-decoders)).
- A passing from the finish decoder works as it does now. A passing from a split decoder looks up the entry, finds its current lap, and records the split on it.
- `LiveTimingHub` gets the splits on the existing timing topic (or a new method and topic if the payload gets large). Services still never touch `SimpMessagingTemplate`.

### Unknown transponders at split loops

A transponder that matches no entry at a split loop is **ignored silently**. Only the finish loop sends an unknown transponder to the referee. Otherwise a pit-lane or marshal transponder passing a split loop would flood the referee's prompts.

## The hard part: clocks

RC-4 timestamps are seconds since that decoder powered on. There is no absolute time, and each decoder has its own clock.

How it works today (`EpochAnchor`): on the first passing, the server records its own arrival time and treats it as the decoder's time zero. Every later passing is that anchor plus the decoder's own offset. Two consequences:

- **Lap times on one decoder are exact.** A lap is a difference between two passings on the same clock, so the anchor cancels out.
- **Anything between two decoders is not.** A split time is a difference between two decoders' clocks, so it carries the difference between their anchors. Each anchor is off by the network and processing delay at that decoder's first passing, so the error is a fixed bias per decoder pair, plus drift from the two crystals running at slightly different rates over a long session. The anchor is never corrected, so drift accumulates for as long as the decoder stays powered.

For RC sectors shown to a hundredth that error may be tolerable, but it is not known, and it must never leak into lap times.

Options, in order of effort:

1. **Accept the bias.** Anchor each decoder separately as now. Cheap. Fine if the error is small against sector length, and it needs measuring before anyone trusts it.
2. **Re-anchor continuously.** Estimate each decoder's offset from the *smallest* delay seen between the decoder's clock and the server's arrival time over a sliding window. The fastest arrival is closest to the true offset, which removes most of the network jitter and tracks drift. The same technique as NTP's one-way delay estimate. Moderate effort, contained inside `decoder-protocol/`, and the unit tests are easy to write.
3. **Calibrate with a real car.** Run a transponder past two adjacent loops at a known pace and solve for the offset between them. Accurate, but it needs an operator step.
4. **Hardware time sync.** Newer decoders may support setting or reading an absolute clock. Unchecked, and RC-4 text decoders (the club hardware this project targets) have no such protocol.

Suggested first step is option 2, measured against option 1 in the spike below.

### Ordering across decoders

`timingExecutor` handles passings in arrival order. Across decoders, a split passing can arrive just before or just after the finish passing that closes the previous lap, because arrival order depends on the network and not on where the car was. The service must attribute a split to a lap by **timestamp window** (after the previous finish passing, before the next), not by "the lap currently open when it arrived". Define this rule exactly, and test the boundary both ways.

## Behaviour to define

- **Missed splits.** The sector either side of a missed split is unknown, not zero. Show a dash, and leave out of theoretical best.
- **Double reads.** A transponder can read twice on one pass, as at the finish loop today. Reuse the finish loop's minimum-lap or debounce rule for splits, scaled to the sector, and decide whether split loops need their own.
- **Marshal laps and penalties.** A marshal adding or removing a lap must not shift splits onto the wrong lap. Splits belong to the lap row, so a manual lap that was never crossed has no splits.
- **Race restart or abandon.** Splits go with the lap, so they follow whatever happens to the lap.
- **Secondary transponder.** An entry's primary or secondary number resolves to the same entry at a split loop, as at the finish loop.
- **Warm-up lap.** The grid warm-up topic already shows which cars the decoder has seen. Decide whether split loops contribute (probably not).
- **Backups and exports.** Splits sit in the one SQLite file, so backups carry them. Decide whether Results Export v1 and the live feed carry splits. They don't today, and RaceHub's contract would need a version decision. Leave out of the first release.
- **Two programs on one decoder.** `docs/decoder.md` already warns against connecting RCTC and another program to one decoder. That still applies to each decoder in the list.

- **Lap validation.** Splits can show a car cut the track (it read at split 1 and split 3 but not 2). A single missing read is common and is not proof of a cut. Proposal: flag the lap for the referee when one split is missing, never penalise automatically, and keep the finish loop's lap count authoritative. Decide how many missing splits in a row, or in a race, make a flag worth raising.

## Hardware research

A first look on 2026-11-16, from public pages only. None of it is confirmed against club hardware, and several points come from third-party sources, so treat it as leads to check.

- **Several decoders is the supported pattern.** MYLAPS describes multi-loop setups as what makes sector timing possible. Clubspeed's X2/AMB documentation says each sector needs its own decoder and loop, with up to six sectors ([Clubspeed](https://help.clubspeed.com/clubspeed-knowledge-center/sector-timing-phase-1-x2/x3-amb/mylaps-installation/testing)). That is the car and kart X2 product, so it only shows the pattern. Whether it matches RC4 hardware is unchecked.
- **One RC4 decoder.** The [RC4 manual](https://www.manualslib.com/manual/1318712/Mylaps-Rc4.html) lists three opto-coupled inputs (5 to 12 V, 5 to 15 mA). A third-party listing describes its auxiliary port as two photocell inputs, a sync pulse and a control port for a GPS receiver. The loop is a separate antenna cable embedded in the track. This suggests one decoder reads one loop, with the photocell inputs as optical triggers that could be extra timing points. Unconfirmed, and RCTC does not read photocell passings today.
- **Clock sync leads.** A MYLAPS decoder manual describes the decoder's date and time as UTC when synchronised to GPS ([MYLAPS decoder manual](https://support.bmileisure.com/hubfs/MYLAPS_Decoder_Manual_vJan2010.pdf)), and the RC4 has a GPS and sync-pulse port. If decoders could be GPS or pulse synchronised, cross-decoder splits would stop depending on server arrival time. Open: whether the RC-4 text protocol (firmware below 4.5, the dominant club hardware) carries that time at all. The project documents `timeSinceStart_s` as seconds since power-on.
- **Newer protocols.** One RC4 manual calls the P3 protocol the preferred one for software developers. RCTC supports RC-4 text only, so P3 may be the route to richer multi-loop data, but the firmware 4.5 boundary (it disables MRT transponders) is why most clubs stay on text.
- **Multi-loop on one decoder.** Nothing found that confirms a single RC decoder can read several separate loops and report which one. The USAC multiloop protocol notes (a third-party extension of MYLAPS's multiloop protocol) list timestamps as milliseconds on a decoder-relative clock, which matches the power-on-offset problem above.

To close the research:

1. Read the RC4 and X2 decoder manuals' specification sections for the number of loop inputs, and what the sync pulse and GPS port do.
2. Check the existing captures (`docs/recording-the-decoder.md`) for any field that identifies the input or loop in a passing record.
3. Ask MYLAPS or the club's reseller whether two RC4 decoders can be synchronised, and whether firmware 4.4 supports it.
4. Decide, from the answers, whether the unit of configuration is a decoder, a decoder input, or both.

## Display and scoring

- **No scoring effect.** Sector times never alter a result. Gaps and positions for scoring come from the finish loop only.
- **Boards and overlay:** sector bests, colouring, and a per-sector gap strip. The spectator boards keep their fixed dark palette. The overlay is the most likely first consumer.
- **Battle tracker:** `BattleTracker` could use split gaps for finer, more frequent gap updates. This is the biggest visible gain and the biggest change to existing code, so it comes after storage and display work.
- **Race control:** show each decoder's connection state, and flag a split loop that has not read any car for a while during a race.

## Simulator

`decoder-simulator/` is one fake decoder. It needs to emit several decoders on separate ports (or loops), with a configurable lap and sector layout, plus options to drop splits and to skew one decoder's clock, so the clock handling and missed-split cases are testable without hardware.

## Suggested order of work

1. **Spike (no schema change).** Run two simulated decoders. Measure the cross-decoder error under option 1 and option 2 above, with jitter and a drifting clock. The result decides how far to trust cross-decoder splits.
2. **Multiple decoders.** The decoder list, one connection each, per-decoder status. No split logic yet. A club with one decoder sees no change.
3. **Timing points and storage.** Track config, migration, recording splits on laps by timestamp window. Splits visible in a race control view only.
4. **Sector maths and display.** Sector times, bests, theoretical best, overlay and boards.
5. **Gaps at splits and the battle tracker.**
6. **Export decisions.** Splits in Results Export v1 and the live feed, if wanted.

Each step ships on its own and leaves a one-decoder club unchanged.

## Open questions

- Does one decoder support several loop inputs at all (see [Hardware research](#hardware-research))? This decides whether the unit of configuration is a decoder or a decoder input.
- Is `decoderId` in the passing record unique and stable across decoders and power cycles? If not, key on the connection.
- How many splits per track are realistic, and is a track with none always valid? (The proposal: yes.)
- Is a split-only lap ever valid, for example when the finish loop misses a car? (The proposal: no, a lap needs a finish passing.)
- Should split loops have their own minimum-time rule, and what is a sensible default per sector length?
- Should splits go to RaceHub in Results Export v1, or stay local until the integration contract settles?
- Does any decoder firmware the club uses expose an absolute clock, so option 4 is worth pursuing?

## Checked against the code

Verified when this was written:

- `DecoderListener` (`app/.../timing/DecoderListener.java`) is a `SmartLifecycle` reading one decoder from the club profile.
- `EpochAnchor` (`decoder-protocol/.../timing/EpochAnchor.java`) anchors to the server's arrival time at the first passing and re-anchors on a decoder restart (a regression of more than 10 seconds). Its Javadoc says differences between passings are exact to the millisecond. The cross-decoder error described above follows from that and has not been measured.
- `docs/architecture.md` describes the single `timingExecutor` and the `/topic/system/decoder-status` topic.

Not verified, and worth reading before design starts: how `LapTimingService` finds an entry's current lap, how `BattleTracker` takes its inputs, and whether the club profile's decoder settings are used anywhere beyond `DecoderListener`.

## Picking this up later

Read this page, then [the research notes](research.md). The next actions are in [Suggested order of work](#suggested-order-of-work), step 1, and the "How to close the research" list in the research notes. Before writing code, read `LapTimingService`, `BattleTracker` and the club profile's decoder settings (see [Checked against the code](#checked-against-the-code)).

A prompt that restarts the conversation cold:

> Read `docs/plans/split-timing/README.md` and `docs/plans/split-timing/research.md`. We are designing multiple decoders and split times for RCTC. The decisions so far are in the README. Start with the open questions and the research list, ask me one at a time, and update the docs as we decide. Do not write production code yet.

## Related

- [Research notes](research.md)

- [Connecting the decoder](../../decoder.md)
- [AMB decoder protocol](../../AMB_DECODER_PROTOCOL.md)
- [Architecture](../../architecture.md), "TCP decoder"
