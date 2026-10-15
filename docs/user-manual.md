# User manual

This is the path for a club from nothing to a finished race day, in the order you will meet things. Each step is a few sentences and points to the guide with the detail. Two printable guides come with the app: the **Admin Configuration Guide** and the **Race Meeting Guide**. Open them from the help panel in the app (the **?** button) or at `/print/admin-guide` and `/print/meeting-guide` on the timing laptop, and print them to PDF if you want paper at the track.

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

- **Club Profile**, **Tracks** (name, length and venue notes) and **Formats** (how a race is run) are in the Admin Configuration Guide, sections 1 to 3.
- **Officials** (Admin → Officials) adds the people who sign in, with their roles, sets passwords and disables people who no longer help. Section 7 of the guide covers it, and [installing.md](installing.md#locked-out) covers a club with no admin who can sign in.
- **Competitors** (Admin → Competitors) lists every driver. If the announcer says a name wrongly, click **Say as…** on the driver (admins, race directors and referees can), type how it should sound, click **Play** to hear it in the announcer voice, then **Save**. It stays with the driver for later meetings and imports never change it. Each change is logged with who made it, and admins can see who changed it last. If a driver was entered twice, **Merge…** on the Competitors page lists the likely duplicates and moves one's entries onto the other.
- The classes you can pick for an event come from the club's list of racing classes. There is no admin screen for adding to that list yet; the API reference ([api.md](api.md)) shows how.

### 5. Create the event and bring in the entries

Create the event, add its classes with a format each, and publish it. Then fill it with entries on its **Entries** tab from whichever source you have: a RaceHub Entry Export file, an RC-Timing driver CSV, an entry feed (a web address that serves the file), or walk-ins by hand. A file you choose yourself shows what will change before anything is saved. An entry feed set to fetch automatically imports changes that apply cleanly on its own, and holds only the revisions that need a decision for you to review. Sections 4 and 5 of the Admin Configuration Guide cover events, classes and every import. [api.md](api.md#the-file-format-for-any-booking-system) describes the file format a booking system can produce.

### 6. Set up championships

A championship adds up results from several events with a best-X-from-Y rule, a points scale and exclusions. Section 6 of the Admin Configuration Guide covers it, and the help panel on the championship page explains each tab.

---

## On the day

### 7. Swap a transponder

If a driver changes their transponder on the day, open **Race Control**, choose the event, then **Transponders**. Find the entry by name and enter the new number. A swap is logged, and a later RaceHub re-import keeps it. Section 1 of the Race Meeting Guide covers the pre-meeting checks.

### 8. Run the races

Start the event, generate the rounds, then for each race call the grid, start, and finish it. Race control shows live timing, links unknown transponders and handles stops, restarts and bump-ups, and the Referee View raises incidents and applies penalties. Sections 2 to 7 and 9 of the Race Meeting Guide take you through a race from grid to result. Open practice, run when no race is on, is in the help panel on the Practice page.

### 9. Show it on screens

Spectator boards and a streaming overlay for OBS need no sign-in: `/boards/now-next`, `/boards/results` and `/boards/overlay` on the laptop's address. Section 10 of the Race Meeting Guide lists what each shows and the options for the overlay.

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
