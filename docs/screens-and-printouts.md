# Screens and printouts

Everything the app can put in front of people at the track: the screens for the pits and venue TVs, the streaming overlay for OBS, and what can be printed. Racers never sign in. The screens below need no sign-in, and the printouts open from race control, which does.

Addresses on this page start from the laptop's address. The About page (`/about`) lists the addresses a phone, tablet or TV on the venue network should use, for example `http://192.168.1.20:8080/boards/now-next`.

## Screens for the pits and venue TVs

| Screen | Address | What it shows | Good for |
|--------|---------|---------------|----------|
| Now and next | `/boards/now-next` | The race on track with live timing and its status (Racing or Stopped), then the next race. Between races it shows the next race and the last finished race's results. A QR code in the corner opens the qualifying standings on a phone | The main pit TV |
| Results | `/boards/results` | The results of the last finished race | A second screen by the timing desk or the drivers' stand |
| Qualifying standings | `/qualifying` | The overall qualifying table for every class: each round's place (R1, R2 and so on), dropped rounds struck through, total points, laps and best lap. It refreshes by itself as heats finish | Phones (via the QR code), a tablet in the pits, or a TV |
| Event schedule | `/events` | The events, with links to each finished race's results and the championship standings | Phones |
| Race result | `/results/{raceId}` | One finished race with lap times per driver | Phones, reached from the schedule |
| Championship standings | `/championships/{id}` | The championship table per class | Phones, reached from the schedule |
| Docs | `/docs` | The user guides, with a menu and search. They are read from the app itself, so they work without the internet. The About page shows the address with a QR code | Anyone who needs the instructions at the track |

Add `?event=ID` to `/boards/now-next`, `/boards/results`, `/boards/overlay` or `/qualifying` to follow one event. The ID is the number at the end of the event's address in Admin (for example `/admin/events/7`). Without it a board follows the event that is racing, or the most recent one in progress.

### Setting up a TV

1. Plug a TV, mini PC or tablet into the venue network.
2. Open the screen's address in a browser, then go full screen (usually F11).
3. Leave it. The boards re-check the app every few seconds, and they carry on if the network drops out and comes back.

A few things help at the track:

- Use the laptop's network address from the About page. `localhost` only works on the laptop itself.
- The QR codes on the boards and printouts hold the same address, so a phone on the venue wifi opens the live page. A phone that is not on the venue wifi can't reach it.
- Names on every public screen follow each competitor's public name setting (full name, initials only, or hidden with the car number standing in), so a junior's name can be kept off the screens.

## The streaming overlay for OBS

The overlay shows the race on track over your video on a transparent background: the running order, laps, last lap and the race clock (time to go, or time so far when the format sets no length). Rows flash on each lap as on the live timing screen (purple race best, blue personal best, green faster, red slower). A ticker underneath rolls through the laps run in the heat, the heat's fastest lap and the round's fastest. Between races a small "No race on track" panel stands in, so a blank page never looks like a broken overlay.

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

For example `/boards/overlay?top=6&theme=light`. OBS runs on the laptop or a second PC; if it runs on the timing laptop itself, `localhost` is fine.

## Printouts

Print from the browser (Ctrl+P, or the Print button), and choose **Save as PDF** to keep a copy or send one. Pages are laid out for A4 portrait.

| Printout | Where to open it | Contents |
|----------|------------------|----------|
| Heat result | Race control, a finished race, **Print** | Position, driver, car, laps, total time, best lap, average lap and gap. The penalties given in the heat are listed under the table |
| Lap-by-lap sheet | The heat result page, tick **Include lap-by-lap times** | Every lap time, one column per driver in finishing order, each driver's quickest lap in bold. It prints on its own page. The tick is kept in the page address (`?laps=1`) so a reload keeps it |
| Qualifying standings | `/qualifying`, **Print** | Each class's overall table with each round's place, dropped rounds struck through, one class per page, and a QR code to the live page |
| Practice results | Race control, a practice session, **Print** | The session's results |
| Race result (public) | `/results/{raceId}`, **Print Results** | One race with its lap times |
| Meeting guide and admin guide | `/print/meeting-guide` and `/print/admin-guide` | The step-by-step guides for race day and for setting up the club |

The buttons and tick boxes on these pages are hidden on paper.

### Standings after each round

The qualifying standings page (`/qualifying`) is the standings after each round: it updates as each round's last heat finishes, and a printout taken then is the standings so far. For low-points qualifying ("best X of Y rounds"), each round shows the driver's place in it, and a round that doesn't count towards the total is struck through. A round lost to a marshal penalty counts as the worst place.

## Not built yet

Planned, tracked in issue #317:

- Printing the round summary (all heats of a round) and the final results.
- Start position on the heat result, and real names (not the public name setting) on printouts opened by officials.
- Championship standings laid out for print, and a meeting pack that prints a chosen set for every class.
- A fixed network name for the laptop (for example `rctiming.local`) so printed QR codes survive an address change.
- A results site for clubs without RaceHub. The choice of where results are published (nothing, RaceHub, a results site or both) is made under **Admin → Results to RaceHub**, but choosing the results site sends nothing yet.
- QR codes that link to RaceHub's public pages instead of this laptop (#316).
