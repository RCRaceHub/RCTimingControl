# Screens and printouts

Everything the app can put in front of people at the track: the screens for the pits and venue TVs, the streaming overlay for OBS, and what can be printed. Racers never sign in. The screens below need no sign-in, and the printouts open from race control, which does.

Addresses on this page start from the laptop's address. The About page (`/about`) lists the addresses a phone, tablet or TV on the venue network should use, for example `http://192.168.1.20:8080/boards/now-next`.

## Screens for the pits and venue TVs

| Screen | Address | What it shows | Good for |
|--------|---------|---------------|----------|
| Now and next | `/boards/now-next` | The line-up of the race on track in running order, with its status (Racing or Stopped), then the next race with the drivers on its grid and the marshals. For a final that takes bump-ups, the marshals read "To be confirmed when the final below finishes" until it has, then list the drivers who stayed down (those who moved up race instead). Between races it shows the next race, its drivers and marshals, and the last finished race's results. When auto run is on it also shows a large countdown to the next start, or "Waiting for the finals to be released" while it is holding. A QR code in the corner opens the qualifying standings on a phone | The main pit TV |
| Live race | `/boards/live-race` | The race on track with its live timing table (laps, total time, last and best lap, gap) and the position chart | A second TV, or the one TV when the timing matters more than the line-up |
| Results | `/boards/results` | The results of the last finished race | A second screen by the timing desk or the drivers' stand |
| Qualifying standings | `/qualifying` | The overall qualifying table for every class: each round's place (R1, R2 and so on), dropped rounds struck through, total points, laps and best lap. It refreshes by itself as heats finish | Phones (via the QR code), a tablet in the pits, or a TV |
| Finals results | `/finals` | Every class's finals, A first then B, C and so on, each best first with car, laps, time and best lap. An A final run in legs shows the combined result (points in each leg, dropped legs struck through). A final that has not run says so. It refreshes by itself as finals finish, and has a Print button and a QR code | Phones (via the QR code), a tablet in the pits, or a TV |
| Event schedule | `/events` | The events, with each finished race's results linked under its round and class (Heat 1, Heat 2, or Results for a final), plus the qualifying standings, the finals results and the championship standings | Phones |
| Race result | `/results/{raceId}` | One finished race with lap times per driver | Phones, reached from the schedule |
| Championship standings | `/championships/{id}` | The championship table per class | Phones, reached from the schedule |
| Docs | `/docs` | The user guides, with a menu and search. They are read from the app itself, so they work without the internet. The About page shows the address with a QR code | Anyone who needs the instructions at the track |

Officials don't have to type these addresses. A **Boards** menu sits in the Admin sidebar (above Docs) and in the top bar of race control. It lists every board and results page, and each opens in a new tab. In race control, and on an event's page in Admin, the links are already pinned to that event.

Add `?event=ID` to `/boards/now-next`, `/boards/live-race`, `/boards/results`, `/boards/overlay`, `/qualifying` or `/finals` to follow one event. The ID is the number at the end of the event's address in Admin (for example `/admin/events/7`). Without it a board follows the event that is racing, or the most recent one in progress.

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
| Race result (public) | `/results/{raceId}`, **Print Results** | One race with its lap times |
| Meeting guide and admin guide | `/print/meeting-guide` and `/print/admin-guide` | The step-by-step guides for race day and for setting up the club |

The sheets opened from race control (heat result, round summary, final results and meeting pack) show each driver's real name, because officials print them. Anything on a public page or the championship sheet follows the public name setting.

The buttons and tick boxes on these pages are hidden on paper.

### Standings after each round

The qualifying standings page (`/qualifying`) is the standings after each round: it updates as each round's last heat finishes, and a printout taken then is the standings so far. For low-points qualifying ("best X of Y rounds"), each round shows the driver's place in it, and a round that doesn't count towards the total is struck through. A round lost to a marshal penalty counts as the worst place.

## Not built yet

Planned, tracked in issue #359:

- A fixed network name for the laptop (for example `rctiming.local`) so printed QR codes survive an address change.
- A results site for clubs without RaceHub. The choice of where results are published (nothing, RaceHub, a results site or both) is made under **Admin → Results to RaceHub**, but choosing the results site sends nothing yet.
- QR codes that link to RaceHub's public pages instead of this laptop (#316).

### Points on a heat result

In a qualifying heat of a class that ranks qualifying by low points (round by round), every heat result shows a **Points** column: the heat result in race control, the heat result and round summary printouts, the results board and the public results page. It holds the points the driver scored in that heat's round, which is their place across all the class's heats in the round (1 for the best), or the BRCA no-show score where that setting is on. Because a round is ranked across all its heats, a driver who wins heat 2 may not have 1 point. The column appears once every heat of the round has finished, since the places are not settled before then, and is left out for practice, finals and classes ranked another way. It is the round's own score before any drop for best X from Y; the qualifying standings show which rounds count. In a Shikabane class the points are the driver's place in their own heat, since its heats are scored separately. Championship points are on the championship standings and, for a final, in the next section.

### Championship points on a final

When an event is part of a championship, the results of its finals (race control's finished-race panel, the final results and meeting pack printouts, the results board, the public results page and the public finals page) show a **Champ. pts** column: what each finalist's place across the event's finals is worth in the championship's points scale. The place is the one the championship standings use, so a B final winner is placed after everyone in the A final, and a driver who bumped up has their A final place. The column is shown only for a class the championship scores, only once the final can be placed (every final above it has finished), and is nil for a driver excluded from the event. It does not include the top qualifier or A-final winner bonuses, which the championship standings add. If the event is in several championships, the lowest-numbered one that scores the class is used. An A final run in legs shows its combined order instead of the column.
