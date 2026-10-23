# User manual

This is the path for a club from nothing to a finished race day, in the order you will meet things. Each step is a few sentences and points to the guide with the detail. Two printable guides come with the app: the **Admin Configuration Guide** and the **Race Meeting Guide**. Open them from the help panel in the app (the **?** button) or at `/print/admin-guide` and `/print/meeting-guide` on the timing laptop, and print them to PDF if you want paper at the track. The full guides, with a menu and search, are also served by the app itself at `/docs` on the laptop's address (the **Docs** link in the sidebar and race control, and on the About page with a QR code). They work without the internet.

RCTC (RC Timing Control) is one program on a laptop at the track. Only officials sign in: admins, race directors and referees. Racers never log in. Their entries come from a RaceHub export file or an RC-Timing CSV, or an official adds them as walk-ins.

---

## Before race day

### 1. Install it

Put RCTC on the laptop that sits by the decoder. Install it as a background service with the installer for your system: [installing.md](installing.md). To run it from Docker on a laptop or small server instead, see [docker.md](docker.md#running-the-clubs-server-with-docker).

Then open **http://localhost:8080** on the laptop. The About page (`/about`) lists the addresses phones, tablets and boards on the venue network should use. If a phone can't connect, the last part of [First run](installing.md#first-run) covers the firewall and network settings.

If you want to look around before setting up your own club, [trial-quickstart.md](trial-quickstart.md) loads a demo club with a simulated decoder, and [docker.md](docker.md#try-the-demo) runs the same demo in Docker.

### 2. Run the setup wizard

The first time, a wizard asks for the club profile, a track, a race format, the first staff account and the decoder. Everything it asks for can be changed later from the admin panel. The steps are described in the Admin Configuration Guide, section 8 (**First-Run Setup Wizard**).

### 3. Connect the decoder

Under **Admin → Decoder**, enter the decoder's address, leave the protocol on RC4 for firmware below 4.5, and click **Test Connection**, then **Save**. Never connect RCTC and another timing program to the same decoder at once. Setup, hardware notes and troubleshooting are in [decoder.md](decoder.md).

### 4. Set up the club, tracks, formats and officials

- **Club Profile**, **Tracks** (name, length and venue notes) and **Formats** (how a race is run) are in the Admin Configuration Guide, sections 1 to 3. **Scoring Systems** (saved sets of scoring rules that formats and championships pick from) are in [Scoring](scoring.md#6-scoring-systems).
- **Officials** (Admin → Officials) adds the people who sign in, with their roles, sets passwords and disables people who no longer help. Section 7 of the guide covers it, and [installing.md](installing.md#locked-out) covers a club with no admin who can sign in.
- **Competitors** (Admin → Competitors) lists every driver. If the announcer says a name wrongly, click **Say as…** on the driver (admins, race directors and referees can), type how it should sound, click **Play** to hear it in the announcer voice, then **Save**. It stays with the driver for later meetings and imports never change it. Each change is logged with who made it, and admins can see who changed it last. If a driver was entered twice, **Merge…** on the Competitors page lists the likely duplicates and moves one's entries onto the other.
- The classes you can pick for an event come from the club's list of racing classes. There is no admin screen for adding to that list yet; the API reference (`docs/api.md` in the source repository) shows how.

### 5. Create the event and bring in the entries

Create the event, add its classes with a format each, and publish it. Then fill it with entries on its **Entries** tab from whichever source you have: a RaceHub Entry Export file, an RC-Timing driver CSV, an entry feed (a web address that serves the file), or walk-ins by hand. A file you choose yourself shows what will change before anything is saved. An entry feed set to fetch automatically imports changes that apply cleanly on its own, and holds only the revisions that need a decision for you to review. To take a class out of an event, use **Remove** on its row in the event's class list. A class that already has races can't be removed until you delete the event's run order; a class with entries asks you to confirm, and withdraws those entries (they are kept, marked withdrawn) before removing the class. Sections 4 and 5 of the Admin Configuration Guide cover events, classes and every import. `docs/api.md` in the source repository, under "The file format for any booking system", describes the file format a booking system can produce.

### 6. Set up championships

A championship adds up results from several events with a best-X-from-Y rule, a points scale and exclusions. Section 6 of the Admin Configuration Guide covers it, and the help panel on the championship page explains each tab.

---

## Race types and formats

A class races to a **format**, which you pick in step 4 and again for each class in step 5. The full rules, with the details of how each one starts and ends, are in [race-types.md](race-types.md); how results are ranked is in [scoring.md](scoring.md). In short:

| Format | What it is | Finals |
|---|---|---|
| **Timed** | One timed race per heat, the same start every round | The field is split evenly over the class's finals, no bumping |
| **Bump Up** | Qualifying heats, then finals where the best finishers of each lower final move up into the final above | **Grid size** cars per final, **bump spots** places in each higher final filled from the final below |
| **Shikabane** | Marshal-less racing where a stopped car is out and the lowest points win; optional A, B and C mains with promotion | Mains work like Bump Up finals; see [shikabane-guide.md](shikabane-guide.md) |
| **Points Finals** | Qualifying heats, then finals that each score on their own. Has a **Scoring system** picker in the format editor; pick **BRCA 1/10 off-road** to fill in the BRCA rules ([scoring.md](scoring.md#6-scoring-systems)) | The field is split evenly over the finals, no bumping; or fixed-size finals filled from A, with a 3-leg A final (best 2 count) |

**Balancing the heats.** Select a practice or qualifying heat that has not been called and the **Heat line-ups** panel shows who is in each heat of that round. Use **Move to…** beside a driver to put them in another heat, which is how you even up qualifying once you know who has turned up after practice. The heats don't have to stay the same size (5 and 3 is fine). The selected heat is highlighted. Above the heats, choose whether a move applies to **just this round** or to **every round where both heats are still waiting** (the default), so a qualifying move can cover all the qualifying rounds at once. Practice results are not used to arrange qualifying, and a driver keeps their car number when moved.

**Bump-up finals are fully supported.** To run them, give the class a Bump Up format and set its grid size and bump spots. When you generate the rounds, say how many finals each class has (A, A+B, A+B+C). When the last qualifying heat finishes, press **Calculate finals** in the Finals line-up panel (select any final in the run order). Check **Cars per final**, which starts at the size of a qualifying heat. The finals are calculated lowest final first, and you can then move drivers between finals or up and down the grid before the final is called. When a lower final finishes, its best finishers are added to the back of the grid of the final above, and race control shows an alert before you start it. A class without a Bump Up format gets no bumping.

**Shikabane** needs some hands-on work: race control marks every dead car and zombie revival, false starts need the car moved on the track, and the mains and zombie round are run lowest first. [shikabane-guide.md](shikabane-guide.md) lists what is done by hand and in what order.

**How a race starts** is set in the format, separately for heats and finals:

- **Grid**: every car goes together on the start horn.
- **Stagger**: after the horn each driver is called by name every few seconds in grid order, and each car's time starts when it is called. A car that crosses the line before it is called is flagged on the Referee View as a jump start, for the referee to penalise.
- **Rolling**: drivers are not called. The clock waits until the first car crosses the line, and then runs for everyone, so every car's time ends together. Race control says "waiting for first car" until then.

**How a race ends**: a timed race ends when every car has taken the flag after the clock runs out. If a car never comes round, the track's max last lap time finishes the race for you. **Practice** rounds are run like heats but never score; **open practice** is a separate timing session with no heats or result.

**Qualifying** is ranked by the format's qualifying type. Low points over the best X rounds (**Round by round**) is the one that is fully worked out; the other three types currently rank by total laps and then best lap.

---

## On the day

### 7. Add a walk-in and swap a transponder

RCTC treats every entrant as present; check-in is RaceHub's. For a driver who did not book, open **Race Control**, choose the event, then **Walk-in**, pick their class and enter them (admins and race directors). Then generate or regenerate rounds so they are in the run order. A class with no confirmed entries is left out of the run order (no heats and no finals), and if no class has any entries, Generate Rounds refuses with a message asking you to add entries or walk-ins first. Add walk-ins before generating: once a race has started the run order can't be regenerated, so a class that was left out stays out. If a driver changes their transponder on the day, open **Race Control**, choose the event, then **Transponders**. Find the entry by name and enter the new number. A swap is logged, and a later RaceHub re-import keeps it. Section 1 of the Race Meeting Guide covers the pre-meeting checks.

### 8. Run the races

Start the event, generate the rounds, then for each race call the grid, start, and finish it. Race control shows live timing, links unknown transponders and handles stops, restarts and bump-ups, and the Referee View raises incidents and applies penalties. The Grid Called panel has a Drivers tab to read the grid from and a Marshals tab for recording a driver who missed marshal duty. Sections 2 to 7 and 9 of the Race Meeting Guide take you through a race from grid to result. The **Print** link in race control opens the print centre ([screens-and-printouts.md](screens-and-printouts.md#printouts)). Open practice, run when no race is on, is in the help panel on the Practice page.

To run the races without pressing anything between them, use **Auto run** at the top of the run order in race control. Press **Go** and, each time a race finishes, the next one waits the gap you set (3 minutes unless changed), is called to the grid 30 seconds before it starts, gets the usual start lead-in, and starts. The announcer says the time to the next race at 10, 5, 2 and 1 minutes when the gap is long enough. The countdown to the next start is shown large in the Auto run panel and on the now-and-next pit screen (a hold before the finals shows "Waiting for the finals to be released" there instead of a count). **30s** and **10s** bring the next start forward and **Stop** switches auto run off. By default it holds after the last qualifier so you can calculate and check the finals in the Finals line-up panel, and calculate them again if a result changed; press **Resume** to carry on, or untick the hold to run straight into the finals. It also holds if the next race has no drivers or a command is refused, and says why. Auto run only starts when you press **Go**. If you would rather it switched itself on when you start a race by hand, tick "Switch on by itself when a race is started by hand" in the panel (off by default); after the first start the rest then follow without pressing Go, and **Stop** keeps it off until you press Go. It is switched off when the app restarts.

### 9. Show it on screens

Spectator boards and a streaming overlay for OBS need no sign-in: `/boards/now-next`, `/boards/live-race`, `/boards/results` and `/boards/overlay` on the laptop's address. Section 10 of the Race Meeting Guide lists what each shows and the options for the overlay; [screens-and-printouts.md](screens-and-printouts.md) covers every screen for the pits and OBS and every printout in one place.

---

## After the race

### 10. Results, corrections and championships

When all races are done, complete the event. Results are on the public pages, printable, and downloadable from the event's page. If a result needs correcting after the finish, the stored result is worked out again and championship points follow. Sections 6 and 8 of the Race Meeting Guide cover finishing, corrections and publishing results. Championship standings update from the saved results and are on the championship's Standings tab and its public page.

### 11. Backups

RCTC backs up its database when you complete the event (that closes the race day) and every night, and **Admin → Backups** lists the copies and takes one on demand. Keep a copy off the laptop. Restoring is done on the laptop with the service stopped: [installing.md](installing.md#restoring-a-backup).

### 12. Send results back to RaceHub

For events whose entries came from RaceHub, results are queued when a race finishes, when one is corrected and when you complete the event (that closes the day), then sent in the background. The address and key go in the settings file ([installing.md](installing.md#sending-results-to-racehub)), and **Admin → Results to RaceHub** shows what has gone. The file format is in [results-export-v1.md](results-export-v1.md).

### 13. Share a live feed

To let people away from the track follow a race, RCTC can send a live feed to a relay. It is set up in the settings file ([installing.md](installing.md#sending-a-live-feed)) and switched on per event in race control. The format is in [live-feed-v1.md](live-feed-v1.md).

---

## When something goes wrong

- **No live laps, or the decoder shows red:** [decoder.md](decoder.md#troubleshooting).
- **A phone can't reach the app:** [installing.md](installing.md#first-run).
- **No admin can sign in:** [installing.md](installing.md#locked-out).
- **Need an old copy of the data back:** [installing.md](installing.md#restoring-a-backup).
- **Upgrading:** [installing.md](installing.md#upgrading).
