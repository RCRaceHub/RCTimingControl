# Screens and printouts

Everything the app can put in front of people at the track: the screens for the pits and venue TVs, the streaming overlay for OBS, and what can be printed. Racers never sign in. The screens below need no sign-in, and the printouts open from race control, which does.

Addresses on this page start from the laptop's address. The About page (`/about`) lists the addresses a phone, tablet or TV on the venue network should use, for example `http://192.168.1.20:8080/boards/now-next`.

## Screens for the pits and venue TVs

| Screen | Address | What it shows | Good for |
|--------|---------|---------------|----------|
| Now and next | `/boards/now-next` | The line-up of the race on track in running order, with its status (Racing or Stopped), then the next race with the drivers on its grid and the marshals. For a final that takes bump-ups, the marshals read "To be confirmed when the final below finishes" until it has, then list the drivers who stayed down (those who moved up race instead). Between races it shows the next race, its drivers and marshals, and the last finished race's results. When auto run is on it also shows a large countdown to the next start, or "Waiting for the finals to be released" while it is holding. A QR code in the corner opens the qualifying standings on a phone | The main pit TV |
| Live race | `/boards/live-race` | The race on track with its live timing table (laps, total time, last and best lap, the gap to the car ahead and to the leader, a bar for how far each car is round its lap, and a tint on cars running close) and the position chart. See [The Live race screen](#the-live-race-screen) | A second TV, or the one TV when the timing matters more than the line-up |
| Live top 10 | `/boards/top` | The qualifying table of the class on track as it would stand if the running heat ended now. See [The Live top screen](#the-live-top-screen) | A TV by the drivers' stand during qualifying |
| Board views | `/boards/view/NAME` | A screen an official builds from panels: live timing, the live top table, the next race, the marshals of the race on track and the last result. See [Board views](#board-views) | The commentator's screen, or a monitor beside the track |
| Results | `/boards/results` | The results of the last finished race | A second screen by the timing desk or the drivers' stand |
| Qualifying standings | `/qualifying` | The overall qualifying table for every class: each round's place (R1, R2 and so on), dropped rounds struck through, total points, laps and best lap. It refreshes by itself as heats finish | Phones (via the QR code), a tablet in the pits, or a TV |
| Finals results | `/finals` | Every class's finals, A first then B, C and so on, each best first with car, laps, time and best lap. An A final run in legs shows the combined result (points in each leg, dropped legs struck through). A final that has not run says so. It refreshes by itself as finals finish, and has a Print button and a QR code | Phones (via the QR code), a tablet in the pits, or a TV |
| Event schedule | `/events` | The events, with each finished race's results linked under its round and class (Heat 1, Heat 2, or Results for a final), plus the qualifying standings, the finals results and the championship standings | Phones |
| Race result | `/results/{raceId}` | One finished race with lap times per driver | Phones, reached from the schedule |
| Championship standings | `/championships/{id}` | The championship table per class | Phones, reached from the schedule |
| Docs | `/docs` | The user guides, with a menu and search. They are read from the app itself, so they work without the internet. The About page shows the address with a QR code | Anyone who needs the instructions at the track |

Officials don't have to type these addresses. A **Boards** menu sits in the Admin sidebar (above Docs) and in the top bar of race control. It lists every board and results page, and each opens in a new tab. In race control, and on an event's page in Admin, the links are already pinned to that event.

Add `?event=ID` to `/boards/now-next`, `/boards/live-race`, `/boards/top`, `/boards/view/NAME`, `/boards/results`, `/boards/overlay`, `/qualifying` or `/finals` to follow one event. The ID is the number at the end of the event's address in Admin (for example `/admin/events/7`). Without it a board follows the event that is racing, or the most recent one in progress.

### Setting up a TV

1. Plug a TV, mini PC or tablet into the venue network.
2. Open the screen's address in a browser, then go full screen (usually F11).
3. Leave it. The boards re-check the app every few seconds, and they carry on if the network drops out and comes back.

A few things help at the track:

- Use the laptop's network address from the About page. `localhost` only works on the laptop itself.
- The QR codes on the boards and printouts hold the same address, so a phone on the venue wifi opens the live page. A phone that is not on the venue wifi can't reach it.
- Live timing, the pit boards, the overlay and the results screens also show each driver's **total time**: the time from the start (the first car's crossing, for a rolling start) to the driver's last crossing. It reads a dash before the driver has crossed the line.
- Every screen that shows a position also shows the car number beside it: live timing and the pit boards, the overlay, the qualifying tables, the race control, referee and cockpit screens, and the driver pickers for incidents and penalties. A car with no number recorded shows a dash.
- Names on every public screen follow each competitor's public name setting (full name, initials only, or hidden with the car number standing in), so a junior's name can be kept off the screens.

## The Live race screen

Each driver has a thin **lap bar** under the name: it fills as the car goes round, measured against that driver's own average lap since they last crossed the line. It is green while the lap is shorter than the average, yellow once it reaches the average, and red a second after. After a stop the bars count from the resume, so a stoppage is not counted against anyone. The bars compare the timing laptop's passing times with the clock of the device showing the board; the board works out the difference from the passings as they arrive, so a TV with a wrong clock settles after the first lap or two.

A car on the same lap within one second of the car ahead gets an amber row. This follows the scored order (the gap between the two cars' last crossings, penalties included), so it changes only at the line. The **Ahead** column is that gap, and **Gap** is the gap to the leader.

In a **final**, a small green ▲ or red ▼ beside the position shows the places a car has gained or lost against its grid place (nothing when it has held its place). Race control's live timing shows the same arrows for a final. In a race with a set length, a **Pred** column shows the laps each car is on course to finish with, worked out from its average lap and the time left; the race ends for a car on its first crossing after time is up, so that lap is counted.

Options on the end of the address: `bars=hide` leaves the bars out, `arrows=hide` the arrows, `predict=hide` the Pred column, and `close=SECONDS` sets how near counts as close (`close=0` switches the tint off). Race control's live timing shows the same bar and a CLOSE badge, always at one second.

**Battles.** While the race is running, a strip under the table shows cars running close together: two cars on the same lap within 2 seconds, the gap between them, and an arrow for whether it is closing (green ▲) or growing (red ▼) lap on lap, with the change per lap. A chain of close cars shows as one line, a train. A gap underlined means the car behind is closing by 0.3 seconds a lap or more. The strip shows two battles at a time and cycles if there are more. `battles=hide` leaves it out and `battleGap=SECONDS` sets how near the car ahead starts a battle (default 2). The referee's screen has the same Battles panel.

## The Live top screen

During qualifying, `/boards/top` shows the class on track as a low-points table: places, points, best lap and who has moved. While a heat runs it counts that heat from its live positions, as if it ended now, so the table changes with every lap. The driver rows in the running heat are amber and marked "on track". Drivers whose heat in the same round has not run yet are marked "heat to come" and sit behind everyone with a result in the round, since they have nothing to score yet.

The **Moved** column compares each driver with the table before the running heat counted: a green arrow for places gained, a red arrow for places lost, "New" for a driver with no earlier result. When the heat ends the settled table stays up with the same arrows and tints (green for up, red for down, blue for new) until the next race starts. It shows classes ranked by low points (round by round), and says "No qualifying table yet." for any other class and when nothing has run. Names follow each competitor's public name setting.

Options on the end of the address: `top=20` shows more places (ten by default, at most thirty) and `event=ID` follows one event.

## Board views

A board view is a screen you build yourself. Open **Admin → Board views**, press **New view**, give it a name and an address name (lower case letters, digits and dashes), choose 1 to 3 columns and add panels in the order you want them. Each panel is one of:

- **Live timing**, the race on track with its lap bars and gaps, as on the Live race screen.
- **Live top table**, the class on track as it would stand if the heat ended now, with the number of places you choose (see [The Live top screen](#the-live-top-screen)).
- **Next race**, with the start countdown, its drivers and its marshals.
- **Marshals**, the drivers marshalling the race on track.
- **Last result**, the result of the race that finished last.

A panel can be one column wide or span several. The view opens at `/boards/view/` followed by its address name, with no sign-in, and the options of the Live race screen work on it: `event=ID`, `bars=hide`, `arrows=hide`, `predict=hide` and `close=SECONDS`. Two views come ready made: **commentator** (live timing across the top, the live top table and the next race below) and **monitor** (all four, for a screen beside the track). Edit or delete them like any other. Two monitors each get their own view by opening different addresses. ADMIN and RACE_DIRECTOR can change views.

### Showing less in race control

The eye button at the top of race control's run order lists the sections of the page: auto run, audio settings, live timing and the heat and final line-ups. Untick one to hide it. The choice is kept on that browser only, so a second laptop or a tablet can show a smaller page while the main laptop shows everything.

## The streaming overlay for OBS

The overlay shows the race on track over your video on a transparent background: the running order, laps, total time, last lap and the race clock (time to go, or time so far when the format sets no length). Rows flash on each lap as on the live timing screen (purple race best, blue personal best, green faster, red slower). A small stats line under the order rolls through the laps run in the heat, the heat's fastest lap and the round's fastest. A **position chart** (lap across, place down, one line per car, each labelled with the driver's name) sits at the bottom right, and a **ticker** scrolls along the foot of the page with the next race, who is on its grid, who marshals it, each class's top qualifier and the fastest lap of the round. The ticker stays up between races, when the next race is the useful part. Between races a small "No race on track" panel stands in, so a blank page never looks like a broken overlay.

To use it in OBS:

1. Add a **Browser** source.
2. Set the address to `http://<laptop>:8080/boards/overlay`.
3. Set the size to about 450 × 520 pixels, then place and scale it over the video.

Options go on the end of the address:

| Option | Effect |
|--------|--------|
| `top=N` | Show the first N cars (default 10, up to 40) |
| `class=hide` | Leave out the race and class name |
| `theme=light` | Dark text on a light panel instead of white on dark |
| `event=ID` | Follow one event |
| `bars=hide` | Leave out the lap bars under each name |
| `arrows=hide` | Leave out the places-gained arrows shown in a final |
| `close=SECONDS` | How near the car ahead counts as close for the amber row (default 1; 0 turns it off) |
| `battles=show` | Add the battles strip under the running order (off by default here) |
| `battleGap=SECONDS` | How near the car ahead starts a battle (default 2) |
| `chart=hide` | Leave out the position chart |
| `ticker=hide` | Leave out the ticker |
| `ticker=next,drivers,marshals,tq,fastest` | Scroll only the items you name: the next race, its drivers, its marshals, the top qualifiers, the fastest lap of the round |

For example `/boards/overlay?top=6&theme=light`. OBS runs on the laptop or a second PC; if it runs on the timing laptop itself, `localhost` is fine.

The same position chart is on the Live race screen (`/boards/live-race`, add `?event=ID` for one event) below the live timing. The old `/boards/chart` address still works and opens the Live race screen. Names follow each competitor's public name setting.

## Printouts

Print from the browser (Ctrl+P, or the Print button), and choose **Save as PDF** to keep a copy or send one. Pages are laid out for A4 portrait.

| Printout | Where to open it | Contents |
|----------|------------------|----------|
| Heat result | Race control, a finished race, **Print** | Position, driver, car, the grid position the driver started from, laps, total time, best lap, average lap and gap, then **Points** (see below), with the event name and date at the top. The penalties given in the heat are listed under the table, and a position chart (lap across, place down, one line per car, each labelled with the driver's name) sits under that unless you clear **Include position chart**. Names are the real ones |
| Lap-by-lap sheet | The heat result page, tick **Include lap-by-lap times** | Every lap time, one column per driver in finishing order, each driver's quickest lap in bold. It prints on its own page. The tick is kept in the page address (`?laps=1`) so a reload keeps it |
| Round summary | Race control, **Print**, then a qualifier | Every heat of that round, one class to a page, each with the full heat table |
| Final results | Race control, **Print**, then **Final results** | Every final of each class, one class to a page, the A final first, each with the full heat table. A bump-up class's drivers appear in each final they raced |
| Meeting pack | Race control, **Print**, tick the parts, **Open meeting pack** | One print job, class by class: the qualifying standings, every qualifying heat and the finals (whichever parts you tick) |
| Qualifying standings | `/qualifying`, **Print** | Each class's overall table with each round's place, dropped rounds struck through, one class per page, and a QR code to the live page |
| Championship standings | `/championships/{id}`, **Print** | One table per class with each round's points, dropped rounds struck through, one class per page, and a QR code to the live page. Names follow each competitor's public name setting |
| Practice results | Race control, a practice session, **Print** | The session's results |
| Race result (public) | `/results/{raceId}`, **Print Results** | One race with its lap times and a position-by-lap chart |
| Meeting guide and admin guide | `/print/meeting-guide` and `/print/admin-guide` | The step-by-step guides for race day and for setting up the club |

The sheets opened from race control (heat result, round summary, final results and meeting pack) show each driver's real name, because officials print them. Anything on a public page or the championship sheet follows the public name setting.

The buttons and tick boxes on these pages are hidden on paper.

### Standings after each round

The qualifying standings page (`/qualifying`) is the standings after each round: it updates as each round's last heat finishes, and a printout taken then is the standings so far. For low-points qualifying ("best X of Y rounds"), each round shows the driver's place in it, and a round that doesn't count towards the total is struck through. A round lost to a marshal penalty counts as the worst place.

## QR codes and the network name

The QR codes on the boards, the standings pages and the About page open the page on this laptop. When the laptop announces a name (`rctiming.network.mdns-name`, see `installing.md`) and the announcement has really gone out, the code holds that name, for example `http://timing.local:8080/qualifying`, so codes printed at one meeting still work after the laptop's address changes. When no name is announced, which includes a container on Docker's bridge network, the code holds the address the screen was opened at, or the laptop's network address if the screen is open on the laptop itself, so a phone on the venue wifi can still open it. A phone that cannot look up `.local` names (some older Android phones) needs the address instead: the About page lists both.

## Results site

Where results are published (nothing, RaceHub, a results site or both) is chosen under **Admin → Results to RaceHub**. The results site is for clubs without RaceHub: plain web pages for every event, one page per event with each class's heats, finals and championship standings, and an index of events newest first. **Download results site (zip)** builds them for upload to the club's own web space; see `installing.md` for keeping a folder up to date automatically. The pages show display names as the public screens do, and no lap-by-lap times or penalty reasons.

## Not built yet

Planned, tracked in issue #359:

- QR codes that link to RaceHub's public pages instead of this laptop (#316).

### Points on a heat result

In a qualifying heat of a class that ranks qualifying by low points (round by round), every heat result shows a **Points** column: the heat result in race control, the heat result and round summary printouts, the results board and the public results page. It holds the points the driver scored in that heat's round, which is their place across all the class's heats in the round (1 for the best), or the BRCA no-show score where that setting is on. Because a round is ranked across all its heats, a driver who wins heat 2 may not have 1 point. The column appears once every heat of the round has finished, since the places are not settled before then, and is left out for practice, finals and classes ranked another way. It is the round's own score before any drop for best X from Y; the qualifying standings show which rounds count. In a Shikabane class the points are the driver's place in their own heat, since its heats are scored separately. Championship points are on the championship standings and, for a final, in the next section.

### Championship points on a final

When an event is part of a championship, the results of its finals (race control's finished-race panel, the final results and meeting pack printouts, the results board, the public results page and the public finals page) show a **Champ. pts** column: what each finalist's place across the event's finals is worth in the championship's points scale. The place is the one the championship standings use, so a B final winner is placed after everyone in the A final, and a driver who bumped up has their A final place. The column is shown only for a class the championship scores, only once the final can be placed (every final above it has finished), and is nil for a driver excluded from the event. It does not include the top qualifier or A-final winner bonuses, which the championship standings add. If the event is in several championships, the lowest-numbered one that scores the class is used. An A final run in legs shows its combined order instead of the column.
