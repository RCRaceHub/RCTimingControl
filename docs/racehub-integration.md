# RaceHub and RCTC together

How the two programs share the work, what travels between them, and how to set the link up. RaceHub is the club's booking site on the internet. RCTC is the timing program on the laptop at the track. A club can use RCTC with RaceHub, or on its own. For a click-by-click guide with pictures, follow [walkthrough 11: Connect to RaceHub](walkthroughs/11-racehub.md).

## Who owns what

| | RaceHub | RCTC |
|---|---|---|
| Running place | The internet | The laptop at the venue |
| Racer accounts and sign-in | Yes. Racers book and manage their entries here. | **None.** Only officials sign in to RCTC. |
| Booking, payment, check-in | Yes | No. RCTC treats every entry it receives as checked in, and officials can add walk-ins by hand. |
| Organising the meeting (which classes run, entry list, race format chosen) | Yes | Receives it as an import |
| Heats, seeding, finals, run order, penalties, race control | No | Yes, all of it |
| Reading the decoder and timing laps | No | Yes |
| Race history | Keeps the summaries it is sent | Keeps everything, forever: every lap and result |
| Championships | Shows them | Calculates them, from RCTC's results |

RCTC only ever calls out to RaceHub. RaceHub never connects to the laptop, so the laptop needs no public address, no open port and no internet while racing.

## What travels each way

**From RaceHub to RCTC** (RCTC asks, RaceHub answers):

- The club's **classes** and **race formats**, as lists RCTC links to its own once.
- The **meetings** the club has released to timing, for the **Find RaceHub events** list.
- Each meeting's **entries**: driver, class, car number, primary and optional secondary transponder, and, for drivers who agreed, a headshot and car photo. Every field is listed in `docs/racehub-expectations.md` in the source.

**From RCTC to RaceHub:**

- The **results** of each meeting: sent when a race finishes, when a finished race is corrected, and when the event is completed (which closes the day). Each send carries the whole event and RaceHub keeps the newest, so sending twice does no harm. The format is in [results-export-v1.md](results-export-v1.md).
- Nothing about officials, passwords, lap-by-lap times for practice or anyone's contact details. RCTC stores no contact, date of birth, guardian or payment data at all.

If the network is down, results wait in a queue on the laptop and go when it is back. Racing is never held up.

## What it needs

- **A club-level key.** RaceHub makes it for the club. One key belongs to one club, and one key holds both abilities RCTC needs: `entries:read` (to read entries, classes, formats and meetings) and `results:write` (to send results). RaceHub's own guide says where to make and revoke keys; RCTC only needs the key text.
- **RaceHub's address.** One address, RaceHub's host (such as `https://racehub.example`), set once. Results, the meetings list, the class and format lists and imports all find their paths under it, so there is nothing else to enter. The **RaceHub** page, **Find RaceHub events** and the classes step in the setup wizard all show the same field, and the settings file can hold it too.
- **Links, made once.** Each of the club's RaceHub classes is linked to one of your racing classes, and each RaceHub race format to one of your format templates. After that every meeting finds its classes and format through the links. A class is never matched by its name when an entry arrives.

## Where the key goes

Either place works, and the key is never shown again once saved:

1. **The settings file.** `rctiming.racehub.token` in `application.properties` in the data folder (or `RCTIMING_RACEHUB_TOKEN` in Docker), and `rctiming.racehub.url` for the address (an address saved on the admin page overrides it, like the key). Changes need the app restarted. See [installing.md](installing.md#sending-results-to-racehub) and [provisioning.md](provisioning.md).
2. **The admin page.** Paste it into **RaceHub key** on the **Connection** tab of **Admin → RaceHub**. It applies at once, is stored encrypted in the data folder, and **overrides the settings file** until you press **Clear**. This is the way to rotate a key without a restart: make the new key in RaceHub, paste it, check **Find RaceHub events** still lists your meetings, then revoke the old key in RaceHub.

If RaceHub sits behind Cloudflare Access, the service token goes in the settings file too ([installing.md](installing.md#if-racehub-is-behind-cloudflare-access)).

## Checking the link

RaceHub has no ping route of its own, so RCTC uses what the key already does:

- **Test connection** (**Admin → RaceHub → Connection**, ADMIN only) makes two calls. A read of the ready-meetings list shows that RaceHub is reachable, that the answer is RaceHub's, whether it accepts the key and that the key can read (`entries:read`), and how many meetings are released. A results call with an empty JSON object as the body shows that the key can send (`results:write`): RaceHub checks the ability first and then refuses the empty object (422), so nothing is stored. Each result says what to fix.
- **The status on race control** shows **RACEHUB linked**, **unreachable**, **key refused** or **problem**, with the results waiting to send. RCTC repeats only the read once a minute (well inside RaceHub's 30 calls a minute per key) while an address and key are set, and answers the page from that last check, so the page never waits on the network. It is hidden for a club with no RaceHub address and key. The setting `rctiming.racehub.check-interval-ms` changes the interval.

## The path of a meeting

1. In RaceHub, the organiser sets the meeting up, picks its race format and releases it to timing.
2. In RCTC, **Events → Find RaceHub events → Take** creates the event and starts its entry feed. Entries are fetched straight away and then every few minutes. Changes that apply cleanly go in by themselves; anything that needs a decision waits on the event's **Entries** tab.
3. A new class, a class with no link yet, or a transponder clash is shown in the import preview for an official to settle. Nothing is dropped, and a held import saves nothing until it is resolved.
4. On the day, officials add walk-ins in RCTC, start the meeting and race as normal. Late entry changes from RaceHub still arrive while it runs, and automatic fetching stops once the event is completed.
5. Results are sent as races finish. **Admin → RaceHub → Sent results** shows each send, and **Re-upload** on the event's row in **Events** sends it all again.

## Using RCTC without RaceHub

Nothing here is needed to run a club. With no key and no results site, RCTC is standalone: the **RaceHub** page is left out of the admin menu (**Club profile** has a link to it if you later want it), entries come from an RC-Timing CSV or are added by hand, and results are printed, downloaded or published as a results site. A club can also be connected and still run a local meeting that has nothing to do with RaceHub, and open practice is always local. See [user-manual.md](user-manual.md) for those paths.

## Where to read more

- [Walkthrough 11: Connect to RaceHub](walkthroughs/11-racehub.md), step by step with pictures
- `docs/racehub-expectations.md` and `docs/racehub-import.md` in the source, for developers: every field RCTC reads, and the import end to end
- [results-export-v1.md](results-export-v1.md), what RCTC sends back
- [installing.md](installing.md), settings file and key rotation
