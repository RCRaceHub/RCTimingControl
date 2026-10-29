# RCTimingControl

## What This Is

RC club race timing and race control, built to replace RCResults. RCTC is the timing side of the RaceHub suite. RaceHub is a separate cloud app that owns booking, racer accounts and event entry. RCTC runs only at the venue (the local-only timing plan, tracking issue #8).

It is one program on a laptop at the track. It installs as a background service, reads the AMB/MyLaps decoder directly over TCP, and serves race control, the announcer and the spectator boards to browsers on the venue network. It keeps the club's results and championships. Only officials sign in. Entries arrive in a RaceHub export file or are added as walk-ins.

## Core Value

Officials run a full race meeting from a laptop at the track, on Windows, macOS or Linux, with live timing fed directly from AMB/MyLaps hardware and no dependence on the internet. Entries come in from RaceHub in one file, and the club's results and championships stay on the laptop, backed up.

## Requirements

### Validated

- [x] Admins can create and configure events with multiple races and classes — Phase 3
- [x] Admins can set up championships with configurable "best X from Y rounds" scoring — Phases 3 and 7
- [x] Race control runs in the browser at the track: start and stop races, marshal laps, call the grid, print results — Phase 4
- [x] Live lap timing read from the AMB/MyLaps decoder over TCP (RC-4 text), shown live in the browser — Phases 5 and 6, then read directly by the app (#9)
- [x] Race results published after each race, with individual lap times — Phase 7
- [x] Championship standings and the event schedule visible without login — Phase 7
- [x] Entries imported from RaceHub's Entry Export v1, with class mapping — #15, #16
- [x] Walk-in entries added by hand — #17
- [x] Competitors with no login; results, live timing and standings group by competitor — #12, #13
- [x] A primary and an optional secondary transponder per entry; a number already used in the event is warned about, not refused — #14
- [x] Only officials sign in — #18
- [x] On-the-day transponder swaps; spectator boards — #19, #20 (the check-in desk was removed)
- [x] A full meeting runs with no internet connection at the venue — #8, #21
- [x] One SQLite file, behind a seam that lets the database be swapped later — #26
- [x] Scheduled backups and a restore command — #22
- [x] One installer per system that runs the app as a background service — #23, #24
- [x] Results Export v1 sent back to RaceHub through an outbound queue — #27
- [x] Live feed to a relay so remote viewers can follow timing — #28
- [x] Streaming overlay page for OBS — #29
- [x] Officials managed after setup (roles, passwords, disable), and a command-line admin password reset — #61

### Active

- [ ] Entry import from other systems: RC-Timing CSV and pull from a URL (#38)

### Out of Scope

- Racer self-service portal, racer accounts and online entry — **removed** in #18: RaceHub owns racer identity, garage and entry.
- Forwarder process and gRPC link to a cloud service — **removed** in #10: the app reads the decoder directly.
- Separate offline race-day app with cloud sync — **removed** in #21: race day runs locally in RCTC itself.
- Internet deployment (Docker stacks, nginx, TLS) — **removed** in #24: the app stays on the venue network.
- Native mobile app — the web UI works on phones.
- Payment processing — handled by RaceHub, or at the track.

## Context

- **Replacing:** RCResults (rc-timing.com / rc-results.com), a Windows-only client.
- **Suite:** RaceHub handles booking in the cloud. The boundary is RaceHub's Entry Export v1 file, which carries no contact, date of birth, guardian or payment data. Results go back in a Results Export v1 file, keyed by the same RaceHub ids (#27).
- **Timing hardware:** AMB/MyLaps transponder decoders over TCP. RC-4 text (firmware below 4.5, port 5100) is what club hardware uses.
- **Club workflow:** Events have multiple races across classes. Championship series span multiple events, scored by configurable best-X-from-Y rounds (for example, best 4 of 6).
- **Users:** officials only (admins, race directors, referees), with stackable roles. Competitors and spectators use the boards and public pages without signing in.
- **Deployment:** a native installer on the venue laptop, with its own Java runtime, running as a background service. Every client is a browser on the venue network.

## Constraints

- **Tech stack:** Java backend (club expertise; rewriting was considered and rejected in #8), React frontend in the browser.
- **Compatibility:** AMB/MyLaps TCP protocol.
- **Cross-platform:** the laptop can run Windows, macOS or Linux.
- **No internet dependency:** nothing on race day may need the internet.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Browser-based race control | Works on any laptop, and on officials' phones and tablets, with no client install | Shipped |
| AMB/MyLaps TCP integration | Club hardware; the RC-4 text protocol is read directly by the app | Shipped (RC-4) |
| "Best X from Y" championship scoring | Club's primary format; configurable so other clubs can adapt | Shipped |
| Split into a cloud app and a separate offline race-day app | Kept race day running through venue outages | Shipped, then retired in #21. Plan archived at `docs/plans/archive/2026-08-06-001-feat-offline-race-day-resilience-split-plan.md` |
| RCTC is local-only timing; RaceHub owns booking (2026-10-03) | Race day never depends on the cloud, removing the duplicated offline app, the forwarder and the racer portal | Done for #9 to #26; see #8 |
| SQLite, kept swappable (2026-10-03) | One file in the app process, no database server to run at the venue | Shipped in #26 |
| Two transponders per entry (2026-10-04) | Matches RaceHub's export; more per entry may come later | Shipped in #14 |

## Evolution

Keep this document accurate as the project changes.

**When completing a significant feature or fix:**
1. Requirements validated? → Move to Validated with a short note
2. Requirements invalidated? → Move to Out of Scope with reason
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions table
5. "What This Is" still accurate? → Update if drifted

**Periodically (e.g. after a batch of issues closed):**
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context to reflect current state

---
*Last updated: 2026-10-05 — local-only timing in place through #29*
