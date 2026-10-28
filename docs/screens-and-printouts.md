# Screens and printouts

Everything the app can put in front of people at the track: the screens for the pits and venue TVs, the streaming overlay for OBS, and what can be printed. Racers never sign in. The screens below need no sign-in, and the printouts open from race control, which does.

Addresses on this page start from the laptop's address. The About page (`/about`) lists the addresses a phone, tablet or TV on the venue network should use, for example `http://192.168.1.20:8080/boards/now-next`.

## Screens for the pits and venue TVs

| Screen | Address | What it shows | Good for |
|--------|---------|---------------|----------|
| Now and next | `/boards/now-next` | The line-up of the race on track in running order, with its status (Racing or Stopped), then the next race with the drivers on its grid and the marshals. For a final that takes bump-ups, the marshals read "To be confirmed when the final below finishes" until it has, then list the drivers who stayed down (those who moved up race instead). Between races it shows the next race, its drivers and marshals, and the last finished race's results. When auto run is on it also shows a large countdown to the next start, or "Waiting for the finals to be released" while it is holding. A QR code in the corner opens the qualifying standings on a phone | The main pit TV |
| Live race | `/boards/live-race` | The race on track with its live timing table (laps, total time, last and best lap, the gap to the car ahead and to the leader, a bar for how far each car is round its lap, and a tint on cars running close) and the position chart. See [The Live race screen](#the-live-race-screen) | A second TV, or the one TV when the timing matters more than the line-up |
| Live top 30 | `/boards/top` | The practice or qualifying tables of the class on track as they would stand if the running heat ended now. See [The Live top screen](#the-live-top-screen) | A TV by the drivers' stand during qualifying |
| Board views | `/boards/view/NAME` | A screen an official builds from panels: live timing, the live top table, the next race, the marshals of the race on track, the last result and driver cards. See [Board views](#board-views) | The commentator's screen, or a monitor beside the track |
| Results | `/boards/results` | The results of the last finished race | A second screen by the timing desk or the drivers' stand |
| Results | `/results` | One page with a tab for each of **Qualifying**, **Finals**, **Podium** and, when the event is in a championship, **Championship**. It opens on Finals once any final has finished, otherwise on Qualifying; add `?tab=qualifying`, `finals`, `podium` or `championship` to open a tab directly. One **Print this tab** button and one QR code serve every tab (the code opens the tab on screen). The old addresses `/qualifying`, `/finals` and `/podium` still work and open the matching tab | Phones (via the QR code), a tablet in the pits, a TV, or a printout |
| Qualifying tab | `/results?tab=qualifying` | The overall qualifying table for every class: each round's place (R1, R2 and so on), dropped rounds struck through, total points, laps and best lap, and the championship points the place earns for a class with no finals (a class with finals shows them on the finals). It refreshes by itself as heats finish | Phones, a tablet in the pits, or a TV |
| Finals tab | `/results?tab=finals` | Every class's finals, A first then B, C and so on, each best first with car, laps, time and best lap. A final run in legs (the A final, or every final when the event's **Finals in legs** setting says so) shows the combined result (points in each leg, dropped legs struck through). A final that has not run says so. It refreshes by itself as finals finish | Phones, a tablet in the pits, or a TV |
| Podium tab | `/results?tab=podium` | Each class's first three from its A final (for an A final run in legs, from the combined order), as large coloured places with car numbers. Under them, the best-placed driver of each category (Junior, Vet and so on) among the class's finals taken together, with their overall place; the heading reads "Category leader" until every final of the class has finished, then "Category winner". Categories are the tags set on a competitor (Competitors page); a driver with no tag appears only in the podium. A class shows "The A final has not finished yet" until it has (or "was abandoned"). A driver who did not take the flag (DNF) is never placed on the podium or named a category leader, and category leaders are only worked out while the A final and every final above are finished, so a B winner is never named leader ahead of an A final still to run. Category tags are public on this page. It refreshes by itself, and prints one class per page | A TV for the prize-giving, phones or a printout |
| Event schedule | `/events` | The events, with each finished race's results linked under its round and class (Heat 1, Heat 2, or Results for a final), plus one link to the event's Results page (qualifying, finals, podium and championship) | Phones |
| Race result | `/results/{raceId}` | One finished race with lap times per driver | Phones, reached from the schedule |
| Championship standings | `/championships/{id}` | The championship table per class. The same table is the Championship tab of an event's Results page | Phones, reached from the schedule |
| Docs | `/docs` | The user guides, with a menu and search. They are read from the app itself, so they work without the internet. The About page shows the address with a QR code | Anyone who needs the instructions at the track |

Officials don't have to type these addresses. A **Boards** menu sits in the Admin sidebar (above Docs) and in the top bar of race control. It lists every board under "Screens for TVs" and the results pages (Results, Podium and the event schedule) under "Results and standings", and each opens in a new tab. In race control, and on an event's page in Admin, the links are already pinned to that event.

Add `?event=ID` to `/boards/now-next`, `/boards/live-race`, `/boards/top`, `/boards/view/NAME`, `/boards/results`, `/boards/overlay` or `/results` to follow one event. The ID is the number at the end of the event's address in Admin (for example `/admin/events/7`). Without it a board follows the event that is racing, or the most recent one in progress.

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

Each driver has a thin **lap bar** under the name: it fills as the car goes round, measured against that driver's own average lap since they last crossed the line. It is green while the lap is shorter than the average, yellow once it reaches the average, and red once it is the club's late margin past it (1 s unless changed on the Club page, Live timing tab). After a stop the bars count from the resume, so a stoppage is not counted against anyone. The bars compare the timing laptop's passing times with the clock of the device showing the board; the board works out the difference from the passings as they arrive, so a TV with a wrong clock settles after the first lap or two.

A car on the same lap within one second of the car ahead gets an amber row and a double-chevron icon beside the name, so it can be seen without relying on the colour. This follows the scored order (the gap between the two cars' last crossings, penalties included), so it changes only at the line. The **Ahead** column is that gap, and **Gap** is the gap to the leader.

In a **final**, a small green ▲ or red ▼ beside the position shows the places a car has gained or lost against its grid place (nothing when it has held its place). Race control's live timing shows the same arrows for a final. In a race with a set length, a **Pred** column shows the laps each car is on course to finish with, worked out from its average lap and the time left; the race ends for a car on its first crossing after time is up, so that lap is counted.

Options on the end of the address: `bars=hide` leaves the bars out, `arrows=hide` the arrows, `predict=hide` the Pred column, and `close=SECONDS` sets how near counts as close (`close=0` switches the tint off). Race control's live timing shows the same bar and a CLOSE badge, using the club's close gap (the Club page, Live timing tab).

**Colour key.** A small key under the live timing says what the colours mean: a row flashes purple for the fastest lap of the race, blue for the driver's own best lap, green when a lap is as quick as or quicker than their last, and red when it is slower than usual; amber marks a car close to the one ahead. Each of these also shows a small icon beside the driver's name, so the meaning does not depend on telling the colours apart: a filled star for the fastest lap of the race, a stopwatch for the driver's best, a rising line for green, a falling line for red and a double chevron for close. The icons come and go with the colours, and the key shows each one beside its colour. The Live race screen, board views, race control and the referee screen show it, and the Live top screen shows its own key for the places moved. `legend=hide` leaves it out of a board. The streaming overlay leaves it out unless you tick **Colour key** in the overlay designer or add `legend=show`.

**Battles.** While the race is running, a strip under the table shows cars running close together: two cars on the same lap within the club's battle gap (2 seconds unless an admin changes it under Club, Live timing; 0 switches battles off everywhere), labelled "Battle for P4", with the gap and an arrow for whether it is closing (green ▲) or growing (red ▼) lap on lap, and the change per lap. A chain of close cars shows as one line, "Train P4–P7". An underlined gap means the car behind is closing by 0.3 seconds a lap or more. The strip shows two battles at a time and cycles through any more every five seconds. The timing laptop works the battles out (see the API guide), so every screen agrees; the referee's screen has the same Battles panel. A pair stays a battle for two laps after it drops outside the gap, and a lap taken away by a marshal starts its trend again.

### Live timing thresholds

An admin sets the thresholds under **Club**, on the **Live timing** tab; they apply to every timing screen and take effect in a race in progress at its next update. There are six, and each shows what it does on the page: the **battle gap** (default 2 s; 0 switches battles off), the **closing or growing** limit (0.15 s a lap), the **gaining fast** limit (0.3 s a lap, never below the previous one), the **battle hold** (2 laps), the **close car tint** (1 s; 0 switches the amber row off; it and the CLOSE badge wait until a car has had a lap counted). Nobody is shown as lapped (the LAPPED badge and "+1 lap" gaps) until every car has had at least one lap counted and the **late lap bar** margin (1 s past the car's average lap before the bar turns red). The Club page has three tabs: Profile (details and logo), Live timing and Privacy notice.

### Options on the Live race screen and board views

Options go on the end of the address. They also work on a [board view](#board-views), and the overlay takes the same ones.

| Option | Effect |
|--------|--------|
| `event=ID` | Follow one event instead of the one racing now |
| `bars=hide` | Leave out the lap bars under each name |
| `arrows=hide` | Leave out the places-gained arrows shown in a final |
| `predict=hide` | Leave out the **Pred** column in a race with a set length |
| `close=SECONDS` | How near the car ahead counts as close for the amber row (default is the club's setting, 1 unless changed; `close=0` turns the tint off) |
| `battles=hide` | Leave out the battles strip |
| `legend=hide` | Leave out the small colour key under the table (shown by default) |

## The Live top screen

During qualifying, `/boards/top` shows the class on track in two tables side by side. **Round** (for example "Round 2") ranks the drivers in the round on track by laps and then total time over the round's heats so far, with best lap. **Overall** is the low-points table: places, points, best lap and who has moved. A driver whose heat in the round has not run yet is left out of the Round table until it does. A class whose heats are separate races (Shikabane) shows the Overall table only. While a heat runs it counts that heat from its live positions, as if it ended now, so the table changes with every lap. The driver rows in the running heat are amber and marked "on track". Drivers whose heat in the same round has not run yet are marked "heat to come" and sit behind everyone with a result in the round, since they have nothing to score yet.

The **Moved** column compares each driver with the table before the running heat counted: a green arrow for places gained, a red arrow for places lost, "New" for a driver with no earlier result. When the heat ends the settled table stays up with the same arrows and tints (green for up, red for down, blue for new) until the next qualifying heat starts, and it stays up as the final qualifying standings while the finals run. It shows classes ranked by low points (round by round), and says "No qualifying table yet." for any other class and when nothing has run. Names follow each competitor's public name setting.

During practice the same screen shows **Practice 1** (the session on track) beside **Overall** (each driver's best across all the event's practice so far). Both rank by one time per driver, chosen for the event with the **Practice ranked by** list in the status bar of race control: **Fastest lap** (the default), **Best 2 laps in a row** or **Best 3 laps in a row** (the quickest run of that many consecutive laps added together, never joining laps of two sessions). A driver with fewer laps than that has no time and sits behind those who have one. The tables show laps and that time, with the Moved column and tints on the Overall table, and the running session counts live. Once qualifying has started the board switches to the qualifying tables; until then the last practice stays up. Race director and admins change the setting; other officials see it. The choice applies to the whole event, and changing it re-ranks everything straight away.

During the finals the board shows the final on track. A final run in legs (the A final at nationals, or any final when the event's **Finals in legs** setting is "Every final") has the leg on track under "Leg 2 of 3" beside **Combined**, the final's order over its legs so far: points (a driver's place in each leg added up, lowest wins, with the format's number of counting legs and the same tie-breaks as the finals result), with the running leg counted as if it ended now and arrows for who moved. A final of one leg shows its own order, with laps and best lap. Between finals the one that finished last stays up until the next race starts. Names follow each competitor's public name setting.

Options on the end of the address: `top=20` shows fewer places (thirty by default and at most thirty) and `event=ID` follows one event. A small colour key under the tables (moved up, moved down, new, heat on track) can be left out with `legend=hide`.

## Board views

A board view is a screen you build yourself. Open **Admin → Board views**, press **New view**, give it a name and an address name (lower case letters, digits and dashes), choose 1 to 3 columns and add panels in the order you want them. Each panel is one of:

- **Live timing**, the race on track with its lap bars and gaps, as on the Live race screen.
- **Live top table**, the class on track as it would stand if the heat ended now, with the number of places you choose (see [The Live top screen](#the-live-top-screen)).
- **Next race**, with the start countdown, its drivers and its marshals.
- **Marshals**, the drivers marshalling the race on track.
- **Last result**, the result of the race that finished last.

A panel can be one column wide or span several. The view opens at `/boards/view/` followed by its address name, with no sign-in, and the options of the Live race screen work on it: `event=ID`, `bars=hide`, `arrows=hide`, `predict=hide` and `close=SECONDS`. **Driver cards** (#522) show each driver of the race on track with their headshot, car number and their own sponsors, for the commentator; add the panel to the commentator view or a view of your own. Where a car has a photo (Event, Entries, Car photo), the live timing table on the Live race screen, in board views and on race control and the referee screen shows it beside the driver's name (#521). A driver whose name is not shown in full has no picture or sponsors on a public screen. Two views come ready made: **commentator** (live timing across the top, the live top table and the next race below) and **monitor** (all four, for a screen beside the track). Edit or delete them like any other. Two monitors each get their own view by opening different addresses. ADMIN and RACE_DIRECTOR can change views.

### Showing less in race control

The eye button at the top of race control's run order lists the sections of the page: auto run, audio settings, live timing and the heat and final line-ups. Untick one to hide it. The choice is kept on that browser only, so a second laptop or a tablet can show a smaller page while the main laptop shows everything.

## The streaming overlay for OBS

The overlay shows the race on track over your video on a transparent background: the running order drawn as a **timing tower** (one slim row per car with its place, car number, driver and the gap to the car ahead, sliding to its new place when the order changes) and the race clock (time to go, or time so far when the format sets no length). Each row also shows the laps the car has completed, which in a timed race takes turns (on the same timer as the gap) with the laps it is on course to finish with, and in a qualifying heat the driver's **projected qualifying place** if the heat ended now (for example `Q3▲2`: third, up two since before the heat), so the overall order is seen moving heat by heat. Rows flash on each lap as on the live timing screen (purple race best, blue personal best, green faster, red slower). A small panel at the bottom left holds the **battles** (cars running close, with the gap closing or growing) and a line that rolls between the heat's fastest lap and the round's fastest; it hides itself when there is nothing to show. A **position chart** (lap across, place down, one line per car, each labelled with the driver's name) sits at the bottom right, and a **ticker** scrolls along the foot of the page with the next race, who is on its grid, who marshals it, each class's top qualifier and the fastest lap of the round. The ticker stays up between races, when the next race is the useful part. It can also scroll the heat's and the round's standouts: the most places gained and lost against the grid, and the best average over 3 and over 5 laps in a row (a round standout is left out when it repeats the heat's). Its text is large enough to read on a stream, with a wide gap and a diamond between one item and the next. Below the ticker a **sponsor bar** shows the event's sponsors: add them under Admin, Events, the event's **Sponsors** tab (a name each, plus a PNG or JPEG logo up to 2 MB and at least 16 pixels each way, and move them up or down to set the order). Each logo is checked on upload, then trimmed of transparent margin, shrunk to fit 800 by 240 pixels and stored as a PNG, so any photo or design export displays at the same height in the bar and in the Overlay Designer preview; a CMYK or unreadable file is refused with a message. Sponsors without a logo show their name; more than six take turns. The bar is left out when the event has no sponsors. Between races a small "No race on track" panel stands in, so a blank page never looks like a broken overlay.

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
| `close=SECONDS` | How near the car ahead counts as close for the amber row (default is the club's setting, 1 unless changed; 0 turns it off) |
| `battles=hide` | Leave out the battles strip under the running order (shown by default while the race is running, and only once the first lap has been counted) |
| `legend=show` | Add a small colour key (lap colours and close tint, each with its icon) at the foot of the panel; off by default so it stays off the video |
| `chart=hide` | Leave out the position chart |
| `ticker=hide` | Leave out the ticker |
| `layout=table` | The older table (laps, total time and last lap) instead of the tower |
| `predict=hide` | Leave out the predicted laps |
| `qual=hide` | Leave out the projected qualifying place |
| `sponsors=hide` | Leave out the sponsor bar |
| `separator=ADDRESS` | Put a small image between ticker items in place of the diamond: a web address (`https://…`) or a path on this server (`/storage/…`, as the club logo's). Anything else keeps the diamond; the Overlay Designer has a field for it |
| `ticker=next,drivers,marshals,tq,fastest,gain,loss,pace` | Scroll only the items you name: the next race, its drivers, its marshals, the top qualifiers, the fastest lap of the round, the most places gained, the most places lost, and the best 3 and 5 lap average. The last three show the heat's standout and, where the round has a different one, the round's |

For example `/boards/overlay?top=6&theme=light`.

The tower's gap column shows the interval to the car ahead (**INT**) by default; `gap=leader` shows the gap to the leader (**GAP**), and `gap=auto` takes turns between them with a quick fade, every 6 seconds or every `gapevery=SECONDS` (1 to 60). A small label above the column says which is on view, and a lapped car shows the laps it is down in either. You don't need to write the address by hand: **Admin, Overlay Designer** (ADMIN and RACE_DIRECTOR) lets you tick the parts you want, the layout, the ticker items, the theme and the number of places, shows the overlay live in a preview, and gives you the address to copy into OBS (#524). Nothing is saved on the server; the address holds the design, so keep the ones you use in OBS. OBS runs on the laptop or a second PC; if it runs on the timing laptop itself, `localhost` is fine.

The position chart starts at **lap 0**, where each car sits on the grid, so the first lap shows who gained or lost places off the start. A car with no grid place, and a result saved before this was added, starts at lap 1. The same position chart is on the Live race screen (`/boards/live-race`, add `?event=ID` for one event) below the live timing. The old `/boards/chart` address still works and opens the Live race screen. Names follow each competitor's public name setting.

## Printouts

Print from the browser (Ctrl+P, or the Print button), and choose **Save as PDF** to keep a copy or send one. Pages are laid out for A4 portrait.

| Printout | Where to open it | Contents |
|----------|------------------|----------|
| Heat result | Race control, **Print**, then the heat or final (or a finished race's **Print** button in the cockpit) | Position, driver, car, the grid position the driver started from, laps, total time, best lap, average lap and gap, then **Points** (see below), with the event name and date at the top. The penalties given in the heat are listed under the table, and a position chart (lap across, place down, one line per car, each labelled with the driver's name) sits under that unless you clear **Include position chart**. Names are the real ones |
| Lap-by-lap sheet | The heat result page, tick **Include lap-by-lap times** | Every lap time, one column per driver in finishing order, each driver's quickest lap in bold. It prints on its own page. The tick is kept in the page address (`?laps=1`) so a reload keeps it |
| Round summary | Race control, **Print**, then a qualifier | Every heat of that round, one class to a page, each with the full heat table |
| Final results | Race control, **Print**, then **Final results** | Every final of each class, one class to a page, the A final first, each with the full heat table. A bump-up class's drivers appear in each final they raced |
| Meeting pack | Race control, **Print**, tick the parts, **Open meeting pack** | One print job, class by class: the qualifying standings, every qualifying heat and the finals (whichever parts you tick) |
| Qualifying standings | `/results`, Qualifying tab, **Print this tab** | Each class's overall table with each round's place, dropped rounds struck through, the championship points where a class has no finals, one class per page, and a QR code to the live page |
| Championship standings | `/championships/{id}`, **Print** | One table per class with each round's points, dropped rounds struck through, one class per page, and a QR code to the live page. Names follow each competitor's public name setting |
| Practice results | Race control, **Print**, then the practice session (or the session's own **Print Results** button) | The session's results |
| Race result (public) | `/results/{raceId}`, **Print Results** | One race with its lap times and a position-by-lap chart |
| Meeting guide and admin guide | `/print/meeting-guide` and `/print/admin-guide` | The step-by-step guides for race day and for setting up the club |

Race control's **Print** page is the one place to find all of these sheets: it lists every finished heat and final, the round summaries, the final results, the meeting pack and the event's practice sessions.

The sheets opened from race control (heat result, round summary, final results and meeting pack) show each driver's real name, because officials print them. Anything on a public page or the championship sheet follows the public name setting.

The buttons and tick boxes on these pages are hidden on paper.

### Standings after each round

The Qualifying tab of the Results page (`/results?tab=qualifying`) is the standings after each round: it updates as each round's last heat finishes, and a printout taken then is the standings so far. For low-points qualifying ("best X of Y rounds"), each round shows the driver's place in it, and a round that doesn't count towards the total is struck through. A round lost to a marshal penalty counts as the worst place.

## QR codes and the network name

The QR codes on the boards, the standings pages and the About page open the page on this laptop. When the laptop announces a name (`rctiming.network.mdns-name`, see `installing.md`) and the announcement has really gone out, the code holds that name, for example `http://timing.local:8080/results?tab=qualifying`, so codes printed at one meeting still work after the laptop's address changes. When no name is announced, which includes a container on Docker's bridge network, the code holds the address the screen was opened at, or the laptop's network address if the screen is open on the laptop itself, so a phone on the venue wifi can still open it. A phone that cannot look up `.local` names (some older Android phones) needs the address instead: the About page lists both.

## Results site

Where results are published (nothing, RaceHub, a results site or both) is chosen under **Admin → Results to RaceHub**. The results site is for clubs without RaceHub: plain web pages for every event, one page per event with each class's heats, finals and championship standings, and an index of events newest first. **Download results site (zip)** builds them for upload to the club's own web space; see `installing.md` for keeping a folder up to date automatically. The pages show display names as the public screens do, and no lap-by-lap times or penalty reasons.

## Not built yet

Planned, tracked in issue #359:

- QR codes that link to RaceHub's public pages instead of this laptop (#316).

### Points on a heat result

In a qualifying heat of a class that ranks qualifying by low points (round by round), every heat result shows a **Points** column: the heat result in race control, the heat result and round summary printouts, the results board and the public results page. It holds the points the driver scored in that heat's round, which is their place across all the class's heats in the round (1 for the best), or the BRCA no-show score where that setting is on. Because a round is ranked across all its heats, a driver who wins heat 2 may not have 1 point. The column appears once every heat of the round has finished, since the places are not settled before then, and is left out for practice, finals and classes ranked another way. It is the round's own score before any drop for best X from Y; the qualifying standings show which rounds count. In a Shikabane class the points are the driver's place in their own heat, since its heats are scored separately. Championship points are on the championship standings and, for a final, in the next section.

### Championship points on a final

When an event is part of a championship, the results of its finals (race control's finished-race panel, the final results and meeting pack printouts, the results board, the public results page and the public finals page) show a **Champ. pts** column: what each finalist's place across the event's finals is worth in the championship's points scale. The place is the one the championship standings use, so a B final winner is placed after everyone in the A final, and a driver who bumped up has their A final place. The column is shown only for a class the championship scores, only once the final can be placed (every final above it has finished), and is nil for a driver excluded from the event. It does not include the top qualifier or A-final winner bonuses, which the championship standings add. If the event is in several championships, the lowest-numbered one that scores the class is used. An A final run in legs shows its combined order instead of the column.
