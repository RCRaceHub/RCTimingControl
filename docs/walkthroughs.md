# Walkthroughs

Step-by-step guides for the jobs you do most, with a picture of each screen. They follow one imaginary club, **Wyvern RC Club**, through a race day on the demo data ([trial-quickstart.md](trial-quickstart.md) loads the same club so you can click along). The [User manual](user-manual.md) explains every setting; this page shows the order to do things in.

The pictures are made by a script from the real app, so they match the current screens. To remake them after a screen changes, see "Remaking the pictures" in `docs/development.md`.

1. [First run: set up a new club](#1-first-run-set-up-a-new-club)
2. [Get a meeting ready: entries and walk-ins](#2-get-a-meeting-ready-entries-and-walk-ins)
3. [Run qualifying and the finals](#3-run-qualifying-and-the-finals)
4. [Referee: incidents and penalties](#4-referee-incidents-and-penalties)
5. [Swap a transponder on the day](#5-swap-a-transponder-on-the-day)
6. [Open practice](#6-open-practice)
7. [Finish the meeting: results and printing](#7-finish-the-meeting-results-and-printing)
8. [Championships](#8-championships)
9. [Put it on screens](#9-put-it-on-screens)

---

## 1. First run: set up a new club

**You need:** RCTC installed on the laptop ([installing.md](installing.md)) and the decoder's address if you have it.

1. Open **http://localhost:8080** on the laptop. Once the club is set up this address shows the sign-in page, and only officials can sign in.

   ![The sign-in page](images/login.png)

   A new install asks you to create the first administrator. This account can do everything, so use a real name and a password you will remember.

   ![Creating the first administrator](images/setup-admin-account.png)

2. The setup wizard follows. The list on the left shows the seven steps and ticks them off. **Club profile** asks for the club's name and time zone (the contact details are optional).

   ![Club profile step of the setup wizard](images/setup-club-profile.png)

3. **Track** takes the track's name and, if you know it, its length. You can add more tracks later.

   ![Track step of the setup wizard](images/setup-track.png)

4. **Race format** makes your first format, which says how a race is run. Start with a plain timed race. [race-types.md](race-types.md) describes the others.

   ![Race format step of the setup wizard](images/setup-format.png)

5. **Staff account** adds a second official, for example the race director. Tick their roles. Racers never sign in, so this is only for the people who run the meeting.

   ![Staff account step of the setup wizard](images/setup-staff.png)

6. **Decoder** takes the decoder's address. Leave the protocol on RC4 for firmware below 4.5 and press **Test Connection** if the decoder is on the network. You can skip this and do it later under **Decoder** in the sidebar.

   ![Decoder step of the setup wizard](images/setup-decoder.png)

7. **Network name** gives the laptop a name such as `rctiming.local`, so phones and tablets can use an address that does not change.

   ![Network name step of the setup wizard](images/setup-network.png)

8. The last step links your classes to RaceHub's. Skip it if you do not use RaceHub.

   ![RaceHub classes step of the setup wizard](images/setup-racehub.png)

Anything you skip can be done later from the sidebar. A club that rebuilds its laptop often can skip the wizard completely: [provisioning.md](provisioning.md).

**Next:** add the classes your club races under **Racing setup**, then create a meeting (walkthrough 2).

![Racing setup, classes](images/admin-racing-setup-classes.png)

---

## 2. Get a meeting ready: entries and walk-ins

**You need:** the club's classes and formats set up, and a list of entries from RaceHub or a spreadsheet. The demo club already has an event called **Demo: Bump-up finals** with fourteen drivers.

1. Go to **Events**. The list shows every meeting and its state. **Create Event** starts a new one. **Find RaceHub events** lists the meetings RaceHub has released to timing, and **Take** creates one here.

   ![The Events page](images/admin-events.png)

2. Open the event. The **Overview** tab holds its name, date and track.

   ![An event's overview](images/event-overview.png)

3. On the **Classes** tab, add each class that is racing and pick a format for it.

   ![An event's classes](images/event-classes.png)

4. The **Entries** tab shows who is entered, with their transponder numbers. Fill it from whichever source you have: **Import entries from RaceHub** (an Entry Export file), **Import from a CSV file** (an RC-Timing driver list), an entry feed, or **Add entry** by hand.

   ![An event's entries](images/event-entries.png)

5. A CSV import shows you what it would change before anything is saved. Check the list, then press **Import entries**. Nothing is saved if you press Cancel. The RaceHub route works the same way: its file is checked, previewed, then applied.

   ![Previewing a CSV import](images/event-csv-import.png)

6. When the entries are in, press **Close Entries** on the event page. The public schedule then shows entries as closed. You can still add walk-ins afterwards.

   ![Closing entries](images/event-close-entries.png)

7. On the day, press **Start Event**. The event is now In Progress and appears under **Race control**.

   ![Starting the event](images/event-start.png)

**A driver who turns up without booking:** open **Race control**, choose the event, then **Walk-in**. Pick the class.

![The walk-in page](images/walk-in.png)

Choose a driver who has raced before, to bring their transponders across, or type a new name, then enter the transponder number and press **Add entry**. Do this before you generate rounds: once a race has started the run order can no longer be regenerated.

![Adding a walk-in](images/walk-in-form.png)

---

## 3. Run qualifying and the finals

**You need:** the event In Progress (walkthrough 2) and the decoder connected. The bar at the top of race control shows **DECODER connected** in green.

1. Open **Race control** and choose the event. A new event has no races yet.

   ![Race control before any races exist](images/cockpit-empty.png)

2. Press **Generate Rounds**. Say how many practice and qualifying rounds you want and how many finals each class has, then press **Generate Rounds** again. This walkthrough uses one practice round and one qualifying round to stay short; a real meeting usually has three qualifiers.

   ![The Generate Rounds window](images/generate-rounds.png)

3. The run order appears down the left, with the next race marked **NEXT**. Select a race to work on it.

   ![The run order](images/run-order.png)

4. Press **Call Grid**. The drivers for that heat are listed. The **Warm-up** column shows which transponders the decoder has seen since the grid was called, so a flat battery or a missing transponder can be fixed before the start.

   ![A grid called](images/grid-called.png)

5. Press **Start Race**. Live timing shows each car's laps, last and best lap and the gap to the car ahead. A **CLOSE** tag and a tinted row mean two cars are running close together.

   ![A race running](images/race-running.png)

   **Auto run.** Starting a race by hand switches **Auto run** on for the event, so the next heat is called and started for you after the gap you set. Press **Stop** in the Auto run panel if you would rather start each race yourself.

6. **A transponder the system does not know** appears under **Unknown Transponders**. Press **Link to entry**, choose the driver it belongs to and press **Link Entry**. **Keep monitoring** leaves it for later.

   ![Linking an unknown transponder](images/link-unknown-transponder.png)

7. When the clock runs out and the cars have taken the flag, press **Finish Race**. The result is saved and shown. Pick the next race in the run order and repeat.

   ![A race result](images/race-result.png)

8. After the last qualifying heat, race control asks you to set the finals. Press **Calculate finals** for a class (or **Calculate finals for all classes**).

   ![Qualifying is over: set the finals](images/finals-before.png)

9. The page shows who is in each final and the qualifying table behind it. Use **Move to…** or the arrows to change someone's place. Then press **Confirm line-up and go to race control**.

   ![The finals line-up](images/finals-line-up.png)

10. Run the finals in the same way, lowest final first. With a **Bump Up** format, the best finishers of each lower final move up into the final above when it finishes, and race control tells you so. Check the grid before you start the next final.

    ![Bump-up applied after the B final](images/bump-up-applied.png)

11. The result of the last final is the winner's result.

    ![The A final result](images/final-result.png)

If a race goes wrong, **More** beside **Stop** offers **Restart** and **Abandon**. [race-types.md](race-types.md) explains how each race type starts and ends.

---

## 4. Referee: incidents and penalties

**You need:** a race running, and an account with the referee role (the demo admin has it).

1. Open **Referee** in the top bar. It follows the live race and adds a **Battles** strip, a button beside each driver, and a log of **What happened**.

2. **Raise Incident** records something you saw, such as contact or a shortcut, without changing the result. **Apply Penalty** changes it.

3. To penalise, press **Apply Penalty** (or **Penalty** beside the driver). Pick the driver, choose **Time penalty (s)** or **Lap deduction**, enter the amount and a reason, and press **Apply Penalty**.

   ![Applying a time penalty](images/referee-penalty.png)

4. The penalty is added to the result at once and appears in the log with your name and the reason.

   ![The Referee View after a penalty](images/referee-view.png)

---

## 5. Swap a transponder on the day

**You need:** the driver's entry in the event. A car's transponder is replaced, for example after a flat battery, by changing it on the entry, and the change is written to the audit log.

1. Open **Race control**, choose the event, then **Transponders**. Type the driver's name.

   ![The transponder swap page](images/transponder-swap.png)

2. Choose the driver, pick which transponder to change (**Primary**, **Secondary**, **Third** or **Fourth**), type the new number and press **Swap transponder**.

   ![Swapping a transponder](images/transponder-swap-form.png)

A number that another driver in the event already holds is refused. If one driver uses the same transponder in two classes, RCTC accepts it with a warning, and a number that matches more than one entry in the running race goes to the referee rather than being credited to the wrong car.

---

## 6. Open practice

**You need:** the decoder connected. Open practice runs outside any heat: one long session, with laps grouped into runs by a pause.

1. Choose **Practice** in the top bar of race control (or **Practice** in the admin sidebar).

   ![The practice page](images/practice-landing.png)

2. Press **New Session**. Name it, say how many laps to average, and how long a pause starts a new run.

   ![The new practice session window](images/practice-new-session.png)

3. Press **Start Practice**. Every transponder the decoder sees appears with its laps, best lap and best average over the number of laps you chose. A driver whose transponder is not on an entry shows as **Unknown**: press **Name driver** to give them a name, or **Change** to correct one.

   ![A practice session running](images/practice-running.png)

4. Press **Stop Practice** when you have finished. The session is kept, and a day view lists every session.

---

## 7. Finish the meeting: results and printing

1. Open the **Print** page for the event. It has a sheet for each heat, a summary for each round, the final results and a **Meeting pack** that prints the parts you tick in one go. Each opens in a new page; use the browser's Print, or Save as PDF.

   ![The Print page](images/print-centre.png)

2. When every race is done, open the event and press **Complete Event**. This closes the race day, takes a backup and sends the results to RaceHub if the event came from there.

   ![Completing the event](images/event-complete.png)

3. The public pages need no sign-in. **Events** at `/events` is the schedule.

   ![The public event schedule](images/public-events.png)

   Anyone on the venue network can read the results without signing in, at `/results` on the laptop's address. The page updates by itself and has a QR code so people can follow it on their phone. **Qualifying** shows the standings after the heats.

   ![Qualifying results](images/results-qualifying.png)

4. **Finals** shows each final's result, and **Podium** the top three of each class.

   ![Finals results](images/results-finals.png)

   ![Podium](images/results-podium.png)

If a result needs correcting after the finish, correct it and the stored result is worked out again, with championship points following. [screens-and-printouts.md](screens-and-printouts.md) lists every printed sheet.

---

## 8. Championships

A championship adds up results from several events with a best-X-from-Y rule and a points scale. [scoring.md](scoring.md) explains the rules.

1. Open **Championships**. Each series shows its name and its best-X-from-Y rule.

   ![The Championships page](images/admin-championships.png)

2. Open one. **Config** holds the best-X-from-Y rule and the bonus points, then there are tabs for **Classes**, **Events**, **Points scale**, **Standings** and **Exclusions**. A **Scoring system** picker at the top fills the rules from a saved set.

   ![A championship's settings](images/admin-championship.png)

3. **Standings** are worked out from the saved results each time, so a corrected result changes them at once.

   ![A championship's standings](images/admin-championship-standings.png)

4. The standings are also public, at `/championships/` followed by the championship's number.

---

## 9. Put it on screens

Spectator screens need no sign-in. Open them on any phone, tablet or TV on the venue network; the **About** page lists the addresses to use.

![The About page](images/about.png)

- **Now and next** (`/boards/now-next`): the race on track and what is coming, with a countdown.

  ![The now and next board](images/board-now-next.png)

- **Live race** (`/boards/live-race`): live timing and the position chart.

  ![The live race board](images/board-live-race.png)

- **Live top table** (`/boards/top`): the qualifying table as it changes.

  ![The live top table](images/board-top.png)

- **Streaming overlay** (`/boards/overlay`): for OBS, with a transparent background.

  ![The streaming overlay](images/board-overlay.png)

Everything that decides what the screens show lives on one page, **Displays**, with three tabs. **Custom views** builds your own screens from panels, such as one for the commentator and one for a trackside monitor.

![Displays: custom views](images/admin-displays-views.png)

**Screens** manages Raspberry Pi display screens and the messages they show.

![Displays: screens](images/admin-displays-screens.png)

**Overlay designer** builds the overlay's address with a live preview and saves looks as named themes.

![Displays: overlay designer](images/admin-displays-overlay.png)

Sound has its own page, **Audio settings**, split into tabs: announcements, wording, timings and tones, outputs and horn sounds.

![Audio settings](images/admin-audio-announcements.png)

The **Docs** link in the sidebar opens this guide and the others inside the app, so they work at the track without the internet.

![The docs site](images/docs-home.png)
