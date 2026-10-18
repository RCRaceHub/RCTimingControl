# Race types and how they run

What each kind of race in RCTC is, how it starts, how its time runs out and how it ends, and how one round feeds the
next. It describes what the code does today; where that differs from what the screen or a club might expect, it says so
under [Known gaps](#known-gaps). How a result is ranked and scored is in [Scoring](scoring.md).

There are two separate choices to keep apart:

- The **round type** says what a round is for: practice, qualifying or a final (section 1).
- The **race format** is set per class and says how its races are run: how long, how they start and how the finals are
  laid out (sections 2 and 3). A format is one of **Timed**, **Bump Up** or **Points Finals**.

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

- Drivers are dealt into heats in entry order, back and forth ("snake draft"): with 3 heats, drivers 1, 2, 3 go to
  heats 1, 2, 3, drivers 4, 5, 6 go to heats 3, 2, 1, and so on.
- The number of heats is the smallest that keeps each at or under the maximum cars per heat. Once a class has at least
  twice the minimum cars per heat, it gets at least two heats, so the drivers of one heat can marshal the other.
- Only confirmed entries are put in a heat when the run order is made.
- The run order can be thrown away and generated again only while every race is still pending and nothing has been
  recorded against them (penalties, incidents, marshal laps or transponder links).

### Starting order in each round

- **First round of its kind** (the first practice round, and the first qualifying round): the grid is in car number
  order within each heat. Practice does not carry over, so qualifying always starts in car number order.
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
| `ROLLING` | Drivers are not called one by one. Cars take the start as they cross the line | Every car has the full race length from the start of the clock (see [Known gaps](#known-gaps)) |

N is the format's **stagger interval** (seconds). A stagger interval of 0 or less is taken as 1 second. Race control
shows how the race will start, and the drivers' call order for a staggered start, before it begins.

### Stagger in detail

In a staggered start the driver in grid position 1 leaves first, position 2 N seconds later, and so on. The timing code
gives each car its own start offset (position × N seconds) and works out each car's own time on the track from it. A
driver with no grid position is called last. So a car's flag comes N seconds after the car before it, and the race is
over for everyone only when the last car to leave has had its full time (section 3).

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

## 4. The three formats

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

How the finals run:

1. When the last qualifying heat of a class finishes, the finals are **seeded automatically**. The lowest final is
   filled first from the bottom of the qualifying order with a full grid; each higher final takes what is left, with
   the bump spots left empty. Worked examples are in [Scoring, section 3](scoring.md#3-seeding-the-finals). An official
   can seed again by hand, for instance after a correction, until any final has started.
2. A final **cannot be called to the grid or started** until every qualifying heat of its class has finished. An
   abandoned heat counts as finished.
3. Finals run **lowest first**: C, then B, then A.
4. When a lower final finishes, its best finishers that are not already in the next final up are added to the back of
   that grid, up to its bump spots, marked as bumped. Race control gets a bump-up alert so the director sees it before
   starting the next final. The A final is never bumped from.
5. A driver who bumped up is on the grid of the higher final and is classed there. A driver who bumped up and did not
   start the higher final is classed last in it (see [Scoring, section 5](scoring.md#5-championship-points)).

The grid size and bump spots shape the seeding. The number of finals comes from the round generator (below), not from
the format.

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

### Settings the round generator decides

Some numbers are not read from the format:

- **How many practice and qualifying rounds** an event has, and the **cars per heat**, come from the options in the
  round generator when an official makes the run order. The format's own qualifying-heats count is not used for this.
- **How many finals** a class has comes from the round generator's per-class setting, else the class's own finals count,
  else one.
- The format's race padding is not read when timing a race: the gap between races is the event's auto run gap
  (section 6).

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

- the event asks to hold after the last qualifier, so the finals can be checked and seeded before anyone lets them run
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

- **Rolling start.** The race clock and every car's own time start when the director presses Start, exactly as for a
  grid start. Race control describes a rolling start as "the clock starts when the first car crosses the line", but
  the clock is not held back. The only difference today is that drivers are not called one by one.
- **Qualifying types.** `FTQ`, `FASTEST_LAP` and `CONSECUTIVE_LAPS` all rank the same way (see
  [Scoring](scoring.md#known-gaps)).
- **Fixed number of laps.** A race is run for a time, not a number of laps. A race given no length is finished by hand.
- **Format fields not used yet.** The Bump Up format's qualifying heats and the Points Finals format's finals count
  are not read; the round generator's options decide those. A format's race padding is not used either.
