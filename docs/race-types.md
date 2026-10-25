# Race types and how they run

What each kind of race in RCTC is, how it starts, how its time runs out and how it ends, and how one round feeds the
next. It describes what the code does today; where that differs from what the screen or a club might expect, it says so
under [Known gaps](#known-gaps). How a result is ranked and scored is in [Scoring](scoring.md).

There are two separate choices to keep apart:

- The **round type** says what a round is for: practice, qualifying or a final (section 1).
- The **race format** is set per class and says how its races are run: how long, how they start and how the finals are
  laid out (sections 2 and 3). A format is one of **Timed**, **Bump Up**, **Points Finals** or **Shikabane**.

Open practice (section 7) is separate from both: it is not part of an event's run order.

## 1. Round types

An event's run order is made by the round generator: some practice rounds, one or more qualifying rounds, then the
finals. Every round holds one race per heat of every class.

| Round type | What it is for | Counts towards |
|---|---|---|
| Practice | A warm-up for the heats, run as a race | Nothing. Practice never scores and never sets the qualifying order |
| Qualifying | Heats that decide who races in which final | The class's qualifying order and the finals seeding |
| Final | The deciding races, lettered A, B, C and so on | Final positions and championship points |

### Heats

The round generator splits each class into heats and keeps the same heats for every practice and qualifying round.

- Drivers are dealt into heats back and forth ("snake draft"): with 3 heats, drivers 1, 2, 3 go to
  heats 1, 2, 3, drivers 4, 5, 6 go to heats 3, 2, 1, and so on. The order is the class's **championship standing**
  when the event is a later round of a championship that scores the class, best first, so the leaders are spread
  across the heats; each class uses its own table (2WD from the 2WD standings, 4WD from the 4WD standings). Drivers with
  no points yet, such as one new to the championship, are seeded below everyone who has some, and the first round of a
  championship, or an event in no championship, deals in entry order. If an event is in more than one
  championship that scores the class, the one where it is the later round is used.
- The number of heats is the smallest that keeps each at or under the maximum cars per heat. Once a class has at least
  twice the minimum cars per heat, it gets at least two heats, so the drivers of one heat can marshal the other.
- Only confirmed entries are put in a heat when the run order is made.
- The run order can be thrown away and generated again only while every race is still pending and nothing has been
  recorded against them (penalties, incidents, marshal laps or transponder links).

### Starting order in each round

- **First round of its kind** (the first practice round, and the first qualifying round): the grid is in car number
  order within each heat. Practice does not carry over, so qualifying always starts in car number order.
- **Car numbers** are a driver's start position in the first race after the field was arranged, and they only change
  when it is arranged again. A driver who is car 1 in practice is still car 1 in qualifying, and takes a new number
  only when the finals are calculated (their place on that final's grid, so each final numbers its cars 1 to n). Moving a driver between heats or finals keeps their
  number; the exception is a move into a heat that already has that number, where the driver takes the next free one.
  A driver bumped up into a higher final is numbered by their place on that grid instead (see Finals, below).
- **Later practice and qualifying rounds**: when a heat finishes, its finishing order becomes the grid of the same
  heat in the next round. The best finisher is on grid position 1.
- **Finals**: seeded from the qualifying order (section 4).

The grid matters most in a staggered start (section 2), where it is the order drivers are called in.

## 2. How a race starts

The start type is set in the format, separately for heats and for finals (a Timed format has one start type for both).
It is read when the grid is called, so a format change made after the rounds were generated applies from then on. A
class with no format, or none set, starts heats staggered and finals on the grid.

Whatever the start type, the race moves through the same steps:

`Pending` → `Grid` (called) → `Running` → `Stopped` (optional) → `Running` → `Finished`

A grid call can be taken back to Pending. A stopped race can be resumed, which keeps the original start time so the
clock carries on from where it stopped, or finished. Restart puts a race back to Pending from any state (it deletes the
race's laps and result). Abandon finishes the race at once, keeps the laps so far and marks it abandoned, and works on
a race that never started.

| Start type | What happens | Cars' own time |
|---|---|---|
| `GRID` | The start horn and every car goes together | Every car has the full race length from the start |
| `STAGGER` | The start horn, then each driver is called by name every N seconds in grid order | A car's time starts when it is called, so the last car to leave is last to finish |
| `ROLLING` | Drivers are not called one by one. Cars take the start as they cross the line, and the clock waits for the first one | Every car has the same end time: the clock starts at the first car's first crossing |

N is the format's **stagger interval** (seconds). A stagger interval of 0 or less is taken as 1 second. Race control
shows how the race will start, and the drivers' call order for a staggered start, before it begins.

### Stagger in detail

In a staggered start the driver in grid position 1 leaves first, position 2 N seconds later, and so on. The timing code
gives each car its own start offset (position × N seconds) and works out each car's own time on the track from it. A
driver with no grid position is called last. So a car's flag comes N seconds after the car before it, and the race is
over for everyone only when the last car to leave has had its full time (section 3).

#### Jump starts

A car that crosses the line before it has been called (its start offset has not yet come on the race clock) has jumped
the start. A quarter of a second of slack is allowed for the gap between the clock and the decoder. The timing code
reports the first early crossing of each car to the referee, who decides whether to give a penalty or to take no action. Either choice removes the alert box (so does a penalty given by any route), but the car keeps its JUMP START mark in the timing table, and the audit log (and the race history) records the jump start as `JUMP_START_DETECTED` when it happens and the referee's decision as `JUMP_START_DISMISSED` (no action) or `JUMP_START_PENALISED`; the passing is
still timed as normal and nothing is applied automatically. An entry added after the race was set up has no slot and is never flagged. Other start types have no jump start. The list is kept
with the race's live timing, so it is gone once the race finishes.

### Rolling start in detail

When the director presses Start on a rolling start race, the race is running but its **clock is held at zero**. It
starts counting when the first car entered in the race crosses the line, and from then on it is one clock for
everyone, so every car's time runs out at the same moment, and each car takes the flag the next time it counts a lap
after that. A crossing from a transponder that matches no entry in the race does not start the clock. If the race is
stopped before any car crossed, the clock waits again when it is resumed; once it has counted, a resume carries on as
for any other start. Race control shows "waiting for first car" while the clock is held. The race's start time is still the moment Start
was pressed, but total times in the result run from the first car's first crossing.

### The first lap

For every start type, a car's **first crossing after the start only begins the timing of its first lap**; it is not a
lap. The first counted lap is the second crossing. So a car's first lap time is measured from its first crossing, not
from the start horn.

### Lap filters

Two track rules apply to every race type, from the track's lap thresholds for the class (the class's rule if it has one,
else the track default):

- **Minimum lap time**: a crossing sooner after the car's last counted one than this is ignored (a double read of the
  loop, or a cut). It is read once when the race starts, so a change made during the race does not apply.
- **Max last lap**: how long RCTC waits for the last car after the clock has run out (section 3).

## 3. How a race ends

A race can have a **length**, in minutes, from its format. A race with no length (minutes set to 0) has no clock to run
out: nothing finishes it automatically, so the race director finishes it.

For a race with a length:

1. The clock counts up while the race is running. It stops while the race is stopped and carries on when it resumes.
2. When a car's own time on the track has run out, the **next lap it counts is its last**. That car has taken the
   flag: it is shown as finished, and any later crossings are kept in the history but not counted. A crossing too soon
   to count under the minimum lap time does not flag a car, and neither does a car's very first crossing.
3. When every car that has crossed the line has taken the flag, and the last car to start has had its full time, RCTC
   finishes the race by itself. The result, the next heat's grid and any bump-up follow as if the director had pressed
   Finish. It is audited as a system action.
4. If a car never takes the flag (it broke down, say), the **max last lap** is the backstop. Once the clock has run out,
   the race is finished when no lap has counted for that long, counted from the moment the clock ran out if none has
   counted since. A track with no max last lap leaves it to the director.
5. A car that never crossed the line is not waited for.

The race director can finish a running or a stopped race at any time. Marshal laps (+1 and -1) and referee penalties
change the laps a car is credited with, not the timing.

When a race finishes, its result is stored and results are queued for RaceHub. See [Scoring](scoring.md) for how the
result is ranked.

## 4. The formats

### 4.1 Timed

A **single timed race** per heat: it runs for the format's duration, with one start type (grid, stagger or rolling) for
every round. Use it for a class that races a set time and needs no particular finals layout.

| Setting | What it does |
|---|---|
| Duration (min) | The length of every race: practice, qualifying and finals |
| Start type | How every race starts |
| Stagger interval (s) | Seconds between drivers in a staggered start |
| Qualifying type | How qualifying is ranked ([Scoring](scoring.md#2-qualifying-order)) |

Finals still use the run order the round generator makes. They are seeded like Points Finals (section 4.3): the field
is split evenly over the class's finals, with no bumping up.

### 4.2 Bump Up

Qualifying heats, then finals where the **top finishers of each lower final move up into the final above**. The A final
has the best qualifiers, and its last places are filled by the best finishers from the B final.

| Setting | What it does |
|---|---|
| Heat duration (min) | The length of practice and qualifying races and of finals (finals use the heat length) |
| Qualifying start type | How practice and qualifying heats start |
| Finals start type | How finals start |
| Grid size | Cars per final |
| Bump spots | Places in each higher final filled by the final below (at most grid size - 1) |
| Best heats count | How many of a driver's qualifying rounds count (low-points qualifying only; 0 means all) |
| Stagger interval (s), qualifying type | As above |
| A final legs, legs counted | Run the A final as several races (BRCA: 3) and add each driver's best legs (BRCA: 2). See [Scoring](scoring.md#a-final-in-legs) |
| Cars per final, fill A first | Cars per final (0 splits the field evenly) and whether the finals are filled from the top: A takes ranks 1 to that number, B the next block and the lowest final what is left. See [Scoring](scoring.md#3-seeding-the-finals) |
| Max race window (s) | A hard limit on how long a race runs from its start (390 for BRCA's 6:30), 0 for none. When it is reached the race finishes and a car still on its last lap keeps only its completed laps. See [Scoring](scoring.md#maximum-race-window) |
| Best heats count, tied cars do not share a place, BRCA qualifying | How round by round qualifying is scored; see [Scoring](scoring.md#brca-qualifying-points-finals) |

How the finals run:

1. When the last qualifying heat of the event finishes, race control shows a **finals line-up screen** in place of the
   cockpit (race directors and admins; referees keep the cockpit). It only calculates the finals, shows every class's
   finals on one page for editing, and has **Confirm line-up and go to race control**, which brings the cockpit back
   with Call Grid. Calculating again later (from the cockpit's panel too) clears the confirmation, so the screen
   returns for the new line-ups. On that screen, or in the **Finals line-up** panel of a final in race control, press
   **Calculate finals** (nothing is calculated for you). Set **Cars per final** first; it starts at the size of a
   qualifying heat, so the marshalling is the same as in qualifying. **Calculate finals for all classes** does every
   class that is ready in one go with each class's default. The lowest final is filled first from the bottom
   of the qualifying order with a full grid; each higher final takes what is left, with the bump spots left empty.
   Worked examples are in [Scoring, section 3](scoring.md#3-seeding-the-finals). The panel then shows who is in each
   final, and you can move a driver to another final, or move a driver up or down the grid, until the final is called
   to the grid. The finals need not stay the same size. Pressing the button again (after a correction, say) replaces
   your edits, and asks first.
2. A final **cannot be called to the grid or started** until every qualifying heat of its class has finished. An
   abandoned heat counts as finished.
3. Finals run **lowest first**: C, then B, then A.
4. When a lower final finishes, its best finishers that are not already in the next final up are added to the back of
   that grid, up to its bump spots, marked as bumped. They take the places in finishing order (the winner of the
   lower final gets the first bump place, the runner-up the next) and are numbered by that place. Race control gets
   a bump-up alert so the director sees it before starting the next final. The A final is never bumped from.
5. A driver who bumped up is on the grid of the higher final and is classed there. A driver who bumped up and did not
   start the higher final is classed last in it (see [Scoring, section 5](scoring.md#5-championship-points)).

The grid size and bump spots shape the seeding. The number of finals comes from the round generator (below), not from
the format.

**To run bump-up finals:** give the class a Bump Up format with a grid size and bump spots, then generate the rounds and
choose how many finals the class has. A class whose format is not Bump Up is seeded without bump spots, as in Points
Finals. Everything else (seeding, calling the finals in order, moving drivers up, the alert in race control) happens
for you.

### 4.3 Points Finals

Qualifying heats, then finals that **do not bump**: the field is split evenly over the finals and each final is a
race of its own.

| Setting | What it does |
|---|---|
| Heat duration (min) | The length of practice and qualifying races |
| Final duration (min) | The length of each final |
| Qualifying start type, finals start type | How heats and finals start |
| Stagger interval (s), qualifying type | As above |

Seeding gives each final `ceil(drivers / finals)` cars, filled from the bottom: with 14 drivers and two finals, the B
final is ranks 8 to 14 and the A final is ranks 1 to 7. No place in a final is left open, so nobody bumps up, and a
final can be called as soon as qualifying is complete. Finals are placed as one set (A, then B without anyone from A,
and so on) for championship points, as in [Scoring](scoring.md#5-championship-points).

**Scoring system.** The Points Finals form has a **Scoring system** picker above the scoring options. Pick **BRCA 1/10
off-road** (or a system an admin has added) to fill them in; see [Scoring](scoring.md#6-scoring-systems).

**BRCA 1/10 off-road.** Pick the **BRCA 1/10 off-road** scoring system. It sets the best 2 rounds counting, tied cars not
sharing a place, BRCA qualifying (flat points for a driver who did not run, and its tie-break), a 6:30 (390 second) race
window, 10 cars per final filled from A down with a short last final, and an A final of 3 legs of which the best 2 count.
The finals below A are single races. The format's own settings (4 qualifying rounds of 5 minutes, 3 finals) are still set
in the form. Clone the system on the **Scoring Systems** page for a club's own variation. The rules are in
[Scoring](scoring.md#brca-qualifying-points-finals).

### 4.4 Shikabane

A format for Shikabane RC racing, where a car that flips, breaks down or stalls stays where it stopped as an obstacle
(a *corpse*) and is **eliminated**. The order of elimination sets each car's heat penalty points, and over the heats
the **lowest total wins**.

| Setting | What it does |
|---|---|
| Heat duration (min) | The length of every heat, used when Heat laps is 0 |
| Mains | How many mains (A, B, C) follow the heats, 0 for none (up to 3) |
| Zombie round | Runs a last-chance race for everyone not in the A main, between the lower mains and A (needs two or more mains) |
| Cars per main, Promotion spots | The size of each main, and how many places of a lower main move up into the one above |
| Heat laps | A heat ends after this many laps (typically 3 in Japanese club racing, sometimes 5). 0 runs it for the heat duration instead |
| Start type, stagger interval (s) | How heats start, as above |
| False start penalty (points) | Points added to a car given a false start |
| Zombie bonus (points) | Points taken off a car for each time it is revived |

The timing system cannot tell a dead car from a slow one, so **race control marks each death by hand**. In live timing
of a Shikabane race each car has an **Eliminate** button while the race is running or stopped. A dead car shows its
place in the order of elimination, and gets **Revive** and **Undo** buttons:

- **Eliminate** puts the car next in the elimination order.
- **Revive** is for a zombie, a dead car another car has flipped back onto its wheels. It is no longer eliminated, and
  if it dies again it goes to the back of the order.
- **Undo** takes back a mark made by mistake.

Only the race director and admins can mark; referees can look. There is also a **False start** button, which gives the
car the format's penalty points; moving the car to the back of the grid is done on the track. The same buttons are on a
finished heat's results, for a death noticed late, and the result is worked out again. A restart clears the marks.

The heat result puts the cars still alive first, in the usual order (most laps, then earliest finish), then the dead,
the last car to die ahead of the one before it. So a car's place is its heat points: 1 for the winner, up to the
number of cars for the first to die. A Shikabane class is ranked as low-points qualifying, which adds the heat points
over the heats. See [Scoring](scoring.md#shikabane-heats).

**Ending a heat by laps.** With Heat laps above 0 the heat has no clock. A car takes the flag on the lap that makes
its target, and the heat finishes by itself once every car that has crossed the line has taken the flag. A car that
has died never takes the flag, so the **max last lap** is the backstop here too, counted from the moment the first
car took the flag. Until then, race control finishes the heat by hand.

**Splitting a class into several heats.** When a class has more cars than fit on the track it is split into heats
(the generator's cars per heat). Each heat is an independent race of its own: places and points are worked out within
the heat, and points from different heats are not merged into one overall order. Qualifying tables and the boards
show a Heat column and the position restarts in each heat.

**Heats and grids.** A Shikabane run order is the practice and qualifying rounds the generator makes. With Mains at 0
there are no finals and the last heat is scored with the others. The grids are:

| Heat | Grid |
|---|---|
| 1 | A random draw, made when the run order is generated |
| 2 | The reverse of heat 1's result, so the winner starts last |
| 3 and later | Cumulative points so far, fewest at the front (ties: most laps, then car) |

The next grid is drawn when a heat finishes, for the same drivers (the generator keeps heats the same in every round).
If an elimination or penalty is added to a finished heat, a grid already drawn from it is not drawn again.

**Mains.** With Mains above 0 the generator makes that many finals, and they are seeded like a bump-up format's finals
from the heat totals: the best drivers in the A main and the worst in the lowest. Each main holds Cars per main, less
the places kept for promotion. When a lower main finishes, its first Promotion spots places are added to the back of
the main above, so a driver can climb from the C main to the A main. A main is run like a heat, with the same
Eliminate, Revive and False start buttons, and is ranked by its own result. With the
zombie round on, a race of everyone not in the A main runs after the lower mains and before A. Its winner takes one
extra place at the back of the A grid, which A is given for it. See
[Running a Shikabane meeting](shikabane-guide.md) for the order to do it in.

### Settings the round generator decides

The round generator is where an official sets the numbers for one event, so some of them are only a starting point
taken from the format:

- **How many qualifying rounds**: the generator opens with the format's **qualifying heats** (a Bump Up or Points
  Finals format's setting; with several classes, the most any of them asks for), else 3. The official can change it
  before generating.
- **How many finals** a class has: the generator opens with a Points Finals format's **finals count** (up to 3), else
  2. What the official enters is what the class gets.
- **How many practice rounds** and the **cars per heat** are only set in the generator.
- There is no race padding setting. The gap between races is the event's auto run gap (section 6).

## 5. Qualifying

How a class's qualifying heats combine into one order is the format's **qualifying type**. It is set per format, and is
described in full, with worked examples, in [Scoring, section 2](scoring.md#2-qualifying-order).

- `ROUND_BY_ROUND`: low-points qualifying. In each round the drivers are ranked together (most laps, then lowest time)
  and their place is their points. The best X rounds count, lowest total wins.
- `FTQ`, `FASTEST_LAP`, `CONSECUTIVE_LAPS`: currently rank the same way: total laps over the class's finished
  qualifying heats, then the best single lap.

A round counts only once all its heats are finished. A driver who missed a round scores one more than the number of
drivers in it.

## 6. Running the day

Race control runs each race with the commands above (call grid, start, stop, finish, abandon, restart, skip to another
race). Skipping changes nothing on the server: it only moves the director's selection.

**Auto run** runs an event's races one after another without anyone pressing a button. When a race finishes, the next
one waits the event's gap, is called to the grid 30 seconds before it starts, gets its lead-in count and starts. It
announces the time to the next race at 10, 5, 2 and 1 minutes when the gap is long enough. It **holds** instead of
carrying on when:

- the event asks to hold after the last qualifier, so the finals can be calculated and checked before anyone lets them run
  (held for the finals),
- the next race has no drivers, or
- a command is refused.

It stops by itself when the run order is finished. The plan lives in memory only, so restarting the app switches auto
run off. Starting a race by hand switches it on for the event.

## 7. Open practice

Open practice is a **session**, outside an event's run order: one long timing session that stays open and records laps
for any transponder that crosses while it is running, whether or not the transponder is entered in an event. It has no
heats, no grid, no start, no clock to run out and no result that scores.

- **Runs.** A transponder that has been quiet for longer than the session's **pause** (default 3 minutes) starts a new
  run. The first crossing of a run only starts it, so the pause is never counted as a lap time.
- **Best laps.** For each driver, each run lists its laps, best lap and average. The best average is taken over N
  consecutive laps (default 5, set per session), and only within one run.
- **Drivers.** An official can name a transponder on the spot, by picking a competitor or adding a new one. Naming
  applies to the laps already recorded, and is saved with the session.
- **Day view.** Sessions of one day can be viewed together, grouped by competitor (else transponder), with the average
  of N laps taken within one run.
- A crossing counts for a race when one is running; otherwise it goes to practice.

## Known gaps

- **Qualifying types.** `FTQ`, `FASTEST_LAP` and `CONSECUTIVE_LAPS` all rank the same way (see
  [Scoring](scoring.md#known-gaps)).
- **Shikabane.** It is for fun days, so its races never score in a championship. Heat points are not normalised between heats of different sizes (each split heat is its own race). A false start gives
  points only: the car's move to the back of the grid is made on the track.
- **Fixed number of laps.** Only a Shikabane format can end a race after a number of laps. Other formats run for a time,
  and a race given no length is finished by hand.
