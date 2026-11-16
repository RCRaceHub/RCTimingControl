# Split timing: research notes

Companion to [the plan](README.md). Gathered on 2026-11-16 from public web pages only, with three searches. **Nothing here is confirmed against club hardware.** Each finding says how far to trust it. Where a source is third-party or a search snippet, the claim needs checking against the manufacturer's manual before it drives a design decision.

## Questions this research was meant to answer

1. Can one decoder read several loops and say which one a passing came from?
2. Is several decoders, one per loop, the normal way to get sector timing?
3. Can decoders share an absolute clock, so splits between decoders do not depend on server arrival time?

## Findings

### 1. One decoder, several loops: not confirmed

- The RC4 manual lists three opto-coupled inputs (5 to 12 V DC, 5 to 15 mA) and says the decoder is compatible with RC3, RC4, RC4 hybrid and DR5 transponders. Source: [RC4 manual on ManualsLib](https://www.manualslib.com/manual/1318712/Mylaps-Rc4.html). The manual's input list is about control inputs, not detection loops.
- MYLAPS's own RC4 page describes the loop as embedded in the track and acting as the system's antenna. Source: [MYLAPS RC4 decoder and detection loop](https://www.mylaps.com/es/sistema-rc4/rc4-decoder/). One loop per decoder is the reading that fits, but the page does not say so outright.
- A third-party listing describes the RC4's auxiliary port as two photocell inputs, a sync pulse and a control port for a GPS receiver. Lower confidence: it is a reseller description, and the search result was a snippet. If it is right, the photocell inputs could act as extra optical timing points, but RCTC does not read photocell passings today and I have not seen what such a record looks like in RC-4 text.
- No source found that says a single RC decoder reads several separate loops and reports which one.

Verdict: assume one loop per decoder until a manual says otherwise.

### 2. Several decoders for sectors: the normal pattern, for car and kart timing

- MYLAPS says a multi-loop set-up makes sector timing possible. This came from a search snippet of MYLAPS's motorized timing pages, and I did not confirm which page, so see [MYLAPS motorized timing](https://www.mylaps.com/timing-solutions-motorized/) as a starting point only.
- Clubspeed's documentation for X2 and AMB systems says each sector needs its own decoder and timing loop, with up to 6 sectors. Source: [Clubspeed sector timing](https://help.clubspeed.com/clubspeed-knowledge-center/sector-timing-phase-1-x2/x3-amb/mylaps-installation/testing).
- Both are about the X2 car and kart product, not RC4. They show the industry pattern. They do not show that RC4 decoders can be used the same way, or that two RC4 decoders can be synchronised.
- MYLAPS names RC4, RC4 Pro and DR5 for RC racing and says it offers no dedicated software for the RC4 system, only third-party packages. Source: [MYLAPS RC and drone transponders](https://www.mylaps.com/?p=10053). I found nothing confirming X2 decoders with RC transponders.

Verdict: matches the plan's primary assumption (one decoder per loop). Whether two RC4 decoders work together at a club is a question for MYLAPS or the reseller.

### 3. Clock synchronisation: leads, none confirmed for RC-4 text

- A MYLAPS decoder manual describes the decoder's date and time as UTC when synchronised to GPS. Source: [MYLAPS decoder manual, January 2010](https://support.bmileisure.com/hubfs/MYLAPS_Decoder_Manual_vJan2010.pdf). This is a car and kart decoder manual.
- The X2/ProChip manuals describe a GPS antenna connecting separately from the loop. Source: [ProChip X2 user manual](https://manualmachine.com/mylaps/x2prochipd/11291178-user-manual).
- Support pages describe a status indicator that shows steady when the decoder is locked to UTC. Source: [BMI Leisure troubleshooting](https://support.bmileisure.com/support-center/timing-noise-issues-mylaps-troubleshooting).
- A third-party description of the RC4 mentions a GPS receiver port and a sync pulse input (see finding 1).
- The USAC multiloop protocol repository (third-party extension of MYLAPS's multiloop protocol) lists message timestamps as hex milliseconds from 0 to FFFFFFFF. That reads as a decoder-relative clock. Source: [USAC MLP README](https://github.com/usacnation/usac-mlp/blob/main/README.md). It does not say so outright.
- The project documents the RC-4 text passing time as `timeSinceStart_s`, seconds since power-on, with no absolute time. See `docs/AMB_DECODER_PROTOCOL.md`.

Verdict: GPS or a sync pulse is a plausible way to put two decoders on one clock. Open: whether RC4 hardware at firmware below 4.5 can use it, and whether the text protocol would then carry the synchronised time. If it does not, the software clock estimate in the plan's option 2 is the fallback.

### 4. Protocol versions

- One RC4 manual calls P3 the preferred protocol for software developers and keeps the older Enhanced protocol for compatibility. The search did not return the P3 specification, so I do not know whether it carries loop or input identity, or an absolute time.
- Sqorz documentation says a new Smart decoder may need MYLAPS to enable P3 before software can talk to it ([Sqorz](https://docs.sqorz.com/howtos/Troubleshooting/Troubleshooting-a-Smart-Decoder/)). That is a practical deployment note for car and kart decoders.
- RCTC supports RC-4 text only, on firmware below 4.5, because firmware 4.5 disables MRT transponders, which clubs commonly use. If P3 were the only route to loop identity or absolute time, that would be a real cost for clubs.

Verdict: unknown. This is the most useful thing to find next.

## What this means for the plan

- Planning for one decoder per loop is sound.
- The clock spike matters, because there may be no hardware sync available at firmware below 4.5.
- Photocell inputs on the RC4's auxiliary port are a possible second kind of timing point, worth a look but not part of the first design.

## How to close the research

1. Read the specification and installation sections of the RC4 and X2 decoder manuals for loop inputs, the sync pulse and the GPS port.
2. Look in the project's decoder captures (`docs/recording-the-decoder.md`) for any passing-record field that identifies a loop, input or photocell.
3. Ask MYLAPS or the club's reseller: can two RC4 decoders be synchronised, on firmware 4.4, and does the text protocol then carry the shared time?
4. Find the P3 protocol specification, and note whether it identifies the loop and carries absolute time.
5. Run the clock spike in the plan against the simulator.

## Limits of this research

- Three web searches, snippets only. No page was opened in full.
- Several sources are third-party (resellers, a software vendor's wiki, a protocol extension repository) and may describe other MYLAPS products.
- The search tool is US-only. UK or EU reseller documentation, which matches the BRCA-affiliated clubs this project serves, may say more.
