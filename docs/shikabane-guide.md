# Running a Shikabane meeting

A step-by-step guide to running Shikabane (marshal-less) racing in RCTC, based on how the Kai City club in Japan runs
it. Nobody steps on the track: a car that flips, breaks down or stalls stays where it stopped and becomes an obstacle
for everyone else. The reference for each setting is in [Race types](race-types.md#44-shikabane) and the scoring is in
[Scoring](scoring.md#shikabane-heats); this page is the order to do things in on the day.

## How a Shikabane meeting works

- **Heats are short.** Typically 3 laps, and a driver runs 3 heats (three sets of three laps). Some clubs use 5 laps.
- **Cars finish by crossing the line.** The order across the line decides the places. A car that cannot finish stays
  put and is out, a *corpse*.
- **Lowest points win.** A car's points for a heat are its place: 1 for the winner. A car that is out gets a worse
  place than any car that finished, and the first to die gets the worst of all. A car that never finishes scores the
  worst points. Points are added over the heats and the lowest total is best.
- **Reverse grid by points.** Heat 1 is a random draw. Heat 2 starts in the reverse of heat 1's result. Heat 3 and later
  start by total points so far, fewest at the front and most at the back.
- **Zombies.** If another car hits a corpse and flips it back onto its wheels, it is a zombie and may drive on. The
  driver who did it gets a bonus (1 point off the heat total by default).
- **Mains.** After the heats, the drivers are split into an A main, B main and C main by their points. The best finishers
  of a lower main are promoted into the main above (one by default), so a driver in the C main can in theory win the B main, move up and win
  the A main and the event.

## Before the day: set up the format

Go to **Admin, Formats** and add a format of type **Shikabane** (or give a class the format in the event). The settings:

| Setting | What to put | Kai City style |
|---|---|---|
| Heat Laps (0 = timed) | Laps in each heat. 0 runs the heat for a time instead | 3 |
| Heat Duration (min, if timed) | Only used when Heat Laps is 0 | 5 |
| Start Type | How the heat starts. Grid is the usual start | Grid |
| False Start Penalty | Points added to a car that jumps the start | 3 |
| Zombie Bonus | Points taken off a driver who revives a dead car | 1 |
| Mains | How many mains follow the heats, 0 for none, up to 3 | 3 (A, B, C) |
| Cars per Main | The size of each main. It grows if the drivers would not fit in the mains you asked for | 8 |
| Promotion Spots | How many places of a lower main move up | 1 |
| Zombie round | Tick to run a zombie round before the A main (needs at least two mains) | On |

Then pick that format for the class in the event's class list. For a track that always ends a heat the same way, also set
the track's **max last lap**: with a lap-count heat it is how long RCTC waits for a car that is stuck before it ends
the heat.

## Generating the rounds

In **Race control, Generate Rounds**:

1. Set **Qualifying rounds** to the number of heats a driver runs (3 for Kai City). Practice rounds are optional.
2. Set **Max cars/heat** to the most cars you want on the track together. A class with more cars than that is split
   into several heats; each heat is its own race and its points are never added to another heat's.
3. If the format has mains, the generator makes them as the class's finals, and the **Finals** box shows how many. Change it to run fewer or more.

The first heat's grid is drawn at random. The later grids are set for you as each heat finishes (see below).

## Running a heat

1. Call the grid and start the heat as for any race. With Heat Laps set there is no clock: each car takes the flag on
   the lap that makes the target and the heat ends itself when every car that crossed the line has.
2. **Mark every death.** RCTC cannot tell a dead car from a slow one, so when a car stops for good press
   **Eliminate** on its row in live timing. Cars that finished come first in the result, then the dead, the last to
   die ahead of the first to die.
3. **Zombies.** When a dead car is flipped back on its wheels and drives on, press **Revive** on it. If it dies again,
   press Eliminate again and it goes to the back of the order.
4. **False starts.** Press **False start** on the car; it is given the format's penalty points. Moving the car to the
   back of the grid is done on the track.
5. **Mistakes.** **Undo** takes back a mark. The same buttons are on the finished heat's results, so a death noticed
   late can still be added and the result and the next grid are worked out again.
6. If a car is stuck and the heat will not end, press **Finish** yourself.

Only the race director and admins can mark eliminations. Referees can look.

## Between heats

When a heat finishes, the next heat's grid is set for you: the reverse of the result after heat 1, and the points order
after that. Look at the grid before you call it. A heat that has already been called keeps its grid. The qualifying
table (race control and the boards) shows points for each driver, with a Heat column when the class is split.

## The mains

When the last heat finishes, the mains are seeded for you from the points: the best drivers in the A main, the worst in
the lowest main. Run the lowest main first. When it finishes, its first **Promotion Spots** places are added to the back
of the main above, and race control shows an alert before you start it. Run the mains the same way as heats: mark the
deaths, finish when every car has taken the flag.

**The zombie round.** With the zombie round ticked, the generator adds a race between the lowest mains and the A main,
named "Zombie round". The A main is given one extra place for its winner. When the main just below A finishes, its
promoted drivers move up, and everyone else who raced a main (and is not in A) is put on the zombie round's grid. Run
it before the A main, marking deaths the same way. Its winner is added to the back of the A main's grid, and race control
shows an alert. If you generate rounds with the zombie round ticked but only one main, there is no zombie round.

Mains are scored by their own result: the A main winner is the event winner. A main's promotion is worked out when it
finishes, from its result at that moment, so mark every death before you press Finish. A death added to a finished
main afterwards changes its result but does not move anybody up or down again.

## What you do by hand

RCTC times the cars but cannot see the track, so these are done by people:

| When | What you do |
|---|---|
| Before the day | Set up the Shikabane format and give the class it. Make sure the class's transponders are in the entries |
| Before each heat | Look at the grid RCTC drew (heat 1 is random, later heats follow the rules above) and put the cars on their marks. Odd positions on the inside, even on the outside, two by two |
| Every heat | Press **Eliminate** the moment a car is dead, **Revive** when a zombie drives on, and **False start** for a jumped start. RCTC cannot tell a dead car from a slow one |
| A false start | Move the car to the back of the grid on the track. RCTC only adds the penalty points |
| A stuck heat | If a car has stopped and the heat does not end, press **Finish**. With a track max last lap set, RCTC ends it for you once nothing has counted for that long |
| After the heat | Check the result and the marks, and add any death noticed late. The next grid is redrawn for you if it has not been called yet |
| After the last heat | If you changed a heat result late, press the finals **re-seed** button before the mains start. RCTC seeds the mains on its own when the last heat finishes, but not again after a later change |
| Each main | Run the lowest main first, then the zombie round (if any), then the A main. RCTC does not stop you starting the A main early, so do not start it until the races below it have finished and the alert has shown who moved up |
| Marking in a main | Mark deaths before pressing Finish. Promotion is worked out when the main finishes and is not redone for a death added afterwards |
| With Auto run | Auto run starts the next race by itself after the gap, and a lap-count heat finishes by itself, so have someone marking deaths during the heat. Leave the hold before the mains switched on so you can check the seeding |
| Championships | Championship points use the generic scoring, so work out any Shikabane overall total yourself from the qualifying table |

## What is not built yet

- Championship points for a Shikabane class still use the generic scoring, not the overall Shikabane total.
- Points are not adjusted between heats of different sizes. When a class is split into several heats, the mains are
  still seeded from all the drivers' heat points together, so keep the heats the same size.
