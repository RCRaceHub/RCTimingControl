# Scoring rules

How RCTC ranks a driver in a heat, orders qualifying, seeds the finals and awards championship points, and what
each setting changes. It describes what the code does today; where that differs from what a club might expect,
it says so under [Known gaps](#known-gaps). The sources are `QualifyingStandingsService`, `AutoFinalsSeedingService`,
`BumpUpSeedingService`, `ResultSnapshotService` and `ChampionshipStandingsQuery`.

## 1. Placing in one race

Within a heat or final, drivers are ordered by:

1. most laps completed, then
2. earliest finish (the lower total time).

Ties on laps and total time keep the timed order. Corrections are applied before the result is stored:

- A **lap penalty** (referee) or a marshal lap adjustment takes laps off (or adds them).
- A **time penalty** adds its seconds to the driver's total time, so it only changes the order between drivers on
  the same number of laps. Live timing adds it as soon as the referee gives it (to the order, the gaps and the
  total), so the order on screen is already the order the result will have.
- A **marshal penalty of kind "finals lap"** takes one lap off in the first final the driver races in their class
  (see section 4).

Example: Ann 30 laps in 5:01.0, Bob 30 laps in 5:03.0, Cat 29 laps. Order Ann, Bob, Cat. A 5 second time penalty
on Ann makes her 5:06.0, so Bob is first. A one lap penalty on Bob makes him 29 laps, so he drops behind Ann and
ties on laps with Cat, then goes ahead or behind Cat on total time.

## 2. Qualifying order

The format's **qualifying type** decides how a class's heats are combined.

| Qualifying type | What RCTC does |
|---|---|
| `ROUND_BY_ROUND` | Low points, "best X from Y" (section 2.1) |
| `FTQ`, `FASTEST_LAP`, `CONSECUTIVE_LAPS` | All three currently rank the same way: total laps over all the class's finished qualifying heats, most first, then the single best lap, quickest first, then the entry id so exact ties always come out the same. They are separate choices in the format editor but are not yet scored differently. |

Only finished heats count. An abandoned heat's laps do not count, but everyone on its grid is still ranked (with no
laps if they never crossed the line). Withdrawn entries are left out. Before the first round of a round by round
class has finished, the table shows the laps order so it is never blank.

### 2.1 Low-points qualifying (`ROUND_BY_ROUND`)

1. A round is scored only once **all its heats are finished** (and not all abandoned). A round with a heat still to
   run is left out until it is done.
   The live top board (`/boards/top`) is the one place a part-run round is ranked: it scores the round from its
   finished heats plus the heat on track (counted from its live laps and total time), and lists drivers of unrun
   heats as waiting, behind everyone with a result. Nothing from that projection is stored or used for seeding.
2. In each scored round, everyone on the grid of any of the class's heats in that round is ranked together: most
   laps, then lowest total time. Their **place is their points** (1 for the best, then 2, 3 and so on). Drivers
   with exactly the same laps and total time share the place.
3. A driver who was **not in a round** (a late walk-in, or a no show) scores **one more than the number of drivers
   in that round**.
4. Each driver's **best X rounds** count and the **lowest total wins**. X is the format's "best heats count"
   (`bestHeatsCount`). A count of 0 means every round counts.
5. Ties on points go to the driver with the most laps in their counting rounds, then the quickest lap in them,
   then the entry id.

Example: five drivers, three rounds, best 2 count.

| Round | 1st | 2nd | 3rd | 4th | 5th |
|---|---|---|---|---|---|
| 1 | Ann | Bob | Cat | Dan | Eve |
| 2 | Bob | Ann | Dan | Eve | Cat |
| 3 | Cat | Ann | Bob | Eve | Dan |

| Driver | Places | Best 2 | Total |
|---|---|---|---|
| Ann | 1, 2, 2 | 1 + 2 | 3 |
| Bob | 2, 1, 3 | 1 + 2 | 3 |
| Cat | 3, 5, 1 | 1 + 3 | 4 |
| Dan | 4, 3, 5 | 3 + 4 | 7 |
| Eve | 5, 4, 4 | 4 + 4 | 8 |

Each heat's result shows the driver's points for its round (the place in the table above), once every heat of the round has finished. See [Screens and printouts](screens-and-printouts.md).

Ann and Bob tie on 3. If Ann's counting rounds (round 1 and the second place with more laps) add up to 61 laps and Bob's (2 and 1) to 60, Ann is
ahead. The drop is worth knowing: Dan's 5 in round 3 does not count against him.

A late arrival: Fran joins after round 1 and races rounds 2 and 3 only. Round 1 had five drivers, so Fran scores 6
for it. With best 2 counting that is only used if it is one of her two best.

| Setting | Effect |
|---|---|
| `bestHeatsCount` = 0 | Every round counts, so a single bad round hurts |
| `bestHeatsCount` = 2 of 3 rounds | The worst round is ignored |
| `bestHeatsCount` larger than the rounds run so far | All the rounds run so far count |

Only a bump-up format has `bestHeatsCount`; a points-finals or single race format with `ROUND_BY_ROUND` counts
every round.

### BRCA qualifying (Points Finals)

A Points Finals format with round by round qualifying has three extra settings, all off by default so existing
formats score as above. They follow the BRCA 1/10 off-road rules.

| Setting | Effect |
|---|---|
| Best heats count | How many rounds count (0 counts them all), as for a bump-up format. BRCA counts the best 2 of 4 |
| Tied cars do not share a place | The Nth car in order in a round scores N points even when it ties with another on laps and time. The order of two exactly tied cars falls to the entry id |
| BRCA qualifying | A driver with no laps in a round (a no show or a car that never started) scores **every registered entry in the class plus one**, rather than a place. A tie on points goes to the best single counting round, then the second best (so 1 + 3 beats 2 + 2), then the most laps over the counting rounds, then the least time over them |

Example, best 2 of 4 counting: Ann scores 1, 3, 5, 6 and Bob 2, 2, 4, 7. Both total 4 from their best two. Ann's best
round (1) beats Bob's (2), so Ann is ahead, whatever their laps.

### Maximum race window

A timed race ends when its clock runs out and each car takes the flag on its next crossing. A Points Finals format can
also set a **max race window**, in seconds from the start of the race (from the last car's start in a stagger start).
BRCA uses 390 seconds, 6:30, for a 5 minute race. When the window is reached the race is finished, whatever the cars
are doing, and a car that has not crossed the line to complete its last lap keeps the laps it completed (its lap in
progress is not counted). 0 means no window; the track's max last lap wait is then the only backstop.

### A final in legs

A Points Finals format can run the **A final** as several legs (BRCA: 3). Only the A final has legs; the lower finals
stay a single race. The round generator makes one race for each leg in the A final's round, and every leg starts from the
same grid, in qualifying order: seeding and any change to the line-up in the finals panel reach all the legs that have not
been called yet. In the run order leg 1 is "A Final" and the others "A Final, Leg 2" and so on.

In each leg a driver scores their finishing place (1 for the winner, ranked by laps then time as in section 1). A driver
who did not run a leg scores one more than the number of drivers in the final. The **legs counted** setting (BRCA: 2)
keeps each driver's best legs, the lowest total wins, and a tie goes to the best single counting leg, then the second
best (1 + 3 beats 2 + 2), then the most laps and the least time over the counting legs. Until every leg has finished the order shows the legs run so far.
Officials see it from `GET /api/v1/race-control/event/{eventId}/final-legs`.

Championship points use the combined order (see section 5): the A final is placed from it once every leg has finished, and
its winner earns the A-final bonus. The public results board shows the combined order while the A final's last finished leg is the race on show, and the finals printouts always show it, with each leg's points and the legs that count.

### Shikabane heats

In a Shikabane class (see [Race types](race-types.md#44-shikabane)) a heat is placed by elimination, not only by
laps: cars alive at the end first, in the order of section 1, then the dead, last to die first. Eliminations are marked
by race control, and a car that was marked dead before it ever crossed the line still gets a place (with no laps). The
class is ranked as low-points qualifying, so a car's place in each heat is its points, less the format's zombie bonus
for each revival and plus the false start penalty for each false start. A car with no row in the result is placed after
the last that has one. With mains, the heat totals seed them as a bump-up format's finals are seeded (best drivers in
the A main) and each main is ranked by its own result; see [Race types](race-types.md#44-shikabane). When the class is split into several heats, each heat is an independent overall race: the
qualifying table lists heat by heat, with positions restarting in each, and points are never added across heats. The lowest total over the heats wins and a tie goes to the most laps over all the heats.

## 3. Seeding the finals

Finals are calculated when an official presses **Calculate finals** in race control's Finals line-up panel, which is
refused until every qualifying heat is finished (and not all abandoned) and once any final has been called. Nothing is
calculated by itself, so a re-run heat, a correction or a marshal penalty given after a heat finished is picked up by
pressing the button again, which replaces any hand edits to the line-ups. The official sets the cars per final (it
starts at the size of a qualifying heat); a bump-up or Shikabane format keeps its bump spots. If the finals have not been generated, nothing is calculated. A final cannot start until every qualifying heat of its class is
finished.

The grid of each final follows qualifying order. The layout depends on the format:

| Format | Cars per final | Bump spots |
|---|---|---|
| Bump-up | the format's grid size (grown if the field would not fit) | the format's bump spots (at most grid size - 1) |
| Points finals, timed race, or a bump-up format with no grid size | `ceil(drivers / finals)` | none |

The number of finals is the number the round generator made for the class.

If the field is too small to fill every final, the lowest finals are left empty rather than one in the middle: 8 drivers
with 4 cars a final and three finals gives an A final (ranks 1 to 4) and a B final (5 to 8), and the C final is removed from the run order. Calculating again with fewer cars per final puts a removed final back, as long as it is one the class was generated with.

A Points Finals format can set **cars per final** and **fill A first**. With fill A first every final above the lowest takes a full
grid: the A final is ranks 1 to the cars per final, B the next block, and the lowest final
takes whoever is left, so with 25 drivers and 10 cars each the C final has 5. Nobody bumps up. If the field would not
fit, the grid grows as above.

Example (bump-up, grid size 10, bump spots 2, two finals, 18 drivers):

- **B final**: the rest, ranks 9 to 18 (10 cars), gridded in qualifying order.
- **A final**: ranks 1 to 8 plus **2 bump places**, which stay empty until the B final finishes. Then the top two
  finishers of the B final are added to the back of the A grid (positions 9 and 10, marked as bumped).
- A third final (C) feeds B in the same way, and B's bumped drivers sit at the back of B's grid.

If the grid would not fit the field, it grows: with 20 drivers, grid size 10, 2 bump spots and two finals, the grid
becomes 11 (B ranks 10 to 20; A ranks 1 to 9 plus 2 bumps), so nobody is left out.

The finals are filled from the top: the A final takes the best qualifiers for its places, B the next block, and the lowest final, which runs first, takes whoever is left, so the lowest final is always the smallest. Example (points finals, two finals, 15 drivers): A has 8 (ranks 1 to 8) and B has 7. Eight drivers over three finals give 3, 3 and 2. A short bump-up field works the same way: 14 drivers, grid size 10, 2 bump spots, two finals gives A ranks 1 to 8 plus 2 bump places and B ranks 9 to 14.

## 4. Marshal duty

Everyone is assumed to be marshalling; nothing needs confirming. A driver who misses their marshal duty is
recorded by the referee, who then applies a penalty of the kind they choose. Applying a penalty to a driver with no
recorded miss records one automatically. Both kinds are recorded per penalty:

| Kind | Effect |
|---|---|
| Qualifying score | The driver's **best qualifying round does not count** (round by round only). The next best round takes its place. If that leaves fewer rounds than are counted, each missing one scores one more than the number of drivers in the largest round, so a penalty can never help a driver. Each qualifying score penalty removes one more best round. |
| Finals lap | **One lap comes off** (per penalty) the driver's result in the **first final they race** in their class, judged by the order the finals run. A class run as "best 2 of 3" finals is not charged three times. The lap is taken off when the final's result is worked out, even if the penalty was given later. |

Example (the table in 2.1, best 2 counting): Bob misses a marshal duty and gets a qualifying score penalty. His
best round (round 2, place 1) is dropped, so his counting rounds are round 1 (2) and round 3 (3): 5 points, which
puts him behind Cat (4). Ann is unchanged on 3.

A qualifying penalty after the finals were calculated needs Calculate finals pressed again to take effect on the grids.

## 5. Championship points

A class run to the Shikabane format never scores in a championship and gets no top
qualifier bonus: Shikabane is for fun days (see [Race types](race-types.md#44-shikabane)).

A championship links events (each event is a round) and scores each driver per class.

| Setting | What it does |
|---|---|
| Points scale | Points per finishing position (position 1 gets the first value and so on; a position not in the scale scores 0) |
| Category splits | Category tags such as Junior, Vet or F-Grade (set per driver on the Competitors page) that the championship gives their own tables, in addition to the class and overall tables. Each split repeats every table for the drivers who hold the tag, with the points they earned, ranked again among themselves, so a junior who is 3rd overall is 3rd in the combined table and 1st in the Junior table. Splits use the same points, best X from Y, exclusions and countback as the main tables; a driver who holds several tags appears in each. Chosen per championship on its Config tab. They are left out of the results sent to RaceHub and do not affect seeding. |
| Overall table | When switched on, the standings also have an **Overall** table that ranks every driver of every scoring class together on championship points, so a win in either class scores the same. A driver's round is their best result in any class at that event, their best X rounds count by the championship's own rule (a class override does not apply), and the bonuses they won in any class are added. It is left out of the results sent to RaceHub. |
| Classes | Only the classes added to the championship score. A championship with no classes added yet scores every class that has results. |
| Best X from Y | A driver's best X rounds count and the rest are dropped; a driver with X rounds or fewer keeps them all, so a round not raced costs nothing beyond the points it would have scored. Can be set per class; an empty X falls back to the championship's. With no X every round counts. Y is kept for display and does not change the result. A DNS or excluded round is an ordinary round here, worth 0, so it is dropped first. |
| TQ bonus points | Extra points to the driver who topped qualifying in the class at a round |
| A-final winner bonus points | Extra points to whoever won the A final in the class at a round |
| Exclusions | An official can exclude a driver from a round: that round is worth nothing, bonuses included |

How a round is scored:

- A championship scores each driver's place in the **event's overall result**. Where the class has finals, that is
  the place across the whole round of finals: the A final's order, then the B final's without the drivers who bumped
  up into the A, then the C final's, and so on. A B final win is therefore not a first place, and a driver who
  bumped up has their A final place. A class with no finals scores from its **overall qualifying order** (section 2),
  placed across all its heats, once every qualifying heat is finished. Practice never scores. The points are the
  points scale at that place.
- Each final's result shows these points beside the place (see [Screens and printouts](screens-and-printouts.md)).
- A final is not placed until every final above it has finished, since its drivers may still bump up. A driver who bumped up and did not start the higher final is classed last in it, ahead of everyone from the final below, in the order they finished that final. If nobody raced the higher final, the final below keeps its own places.
- Example of stacked finals: the A final finishes Ann, Bob, Cat and the B final Cat (who bumped up), Dan, Eve.
  The round's places are Ann 1, Bob 2, Cat 3, Dan 4, Eve 5. Cat's B final win does not count.
- Abandoned races are not excluded from position points: if one has a stored result, its rows score.
- A driver on the grid at a round with no result in it has a **DNS** round, worth 0 points, which still counts
  towards Y.
- **Drops**: every round past the driver's best X is dropped. "Worst" is fewest points, then the worse position,
  so a finish worth nothing is dropped before a DNS.
- Bonuses are **per driver, per class and per round**: winning in two classes or two rounds earns each, and winning two A finals in one event pays once. The TQ goes to the driver at the top of the class's qualifying table once all its qualifying heats are finished (lowest points for a low-points class, otherwise most laps then best lap), so it is not given out part way through qualifying. The A-final bonus goes to whoever finished 1st in a finished final lettered A.
- The class table is ordered by total points, then by **countback**: the driver with more wins in their counting
  rounds ahead, then more seconds, and so on (a dropped or excluded round is not used). Drivers still level share a
  position (1, 1, 3), in the table and in the results sent to RaceHub, and stand by name.

Example: scale 1st 15, 2nd 12, 3rd 10, 4th 8, 5th 6; TQ bonus 2; A-final bonus 3; best 2 from 3 rounds. Dan, in one
class that runs finals:

| Round | Place in the finals | Round points |
|---|---|---|
| 1 | 1st (15), also TQ and A-final winner | 15 |
| 2 | 3rd (10) | 10 |
| 3 | on the grid but did not start | 0 (DNS) |

His best 2 rounds count, so round 3 is dropped: 15 + 10 = 25. The bonuses are added after the drops, per round won,
so Dan also gets the TQ bonus (2) and the A-final bonus (3) for round 1: **30**. A bonus is never dropped with its
round. Had Dan simply not been on the grid in round 3, there would be no round 3 for him and the total is the same.

## 6. Scoring systems

A **scoring system** is a named, saved set of scoring rules, in two kinds: *race* systems (rounds counted, tied places,
A final legs, cut-off window, and default finals layout) and *championship* systems (points scale, best X from Y,
bonuses, overall table). Admins manage them on the **Scoring Systems** page in the admin panel (under Configuration): create
a race or championship system, edit or delete a custom one, clone any system, export one to a file and import a file as a
new system. The same operations are available under `/api/v1/admin/scoring-systems`. Race directors and referees can read
and export them through the API.

Four are built in and locked, a *Club default* and *BRCA 1/10 off-road* of each kind; clone one to change it. Each system
is JSON with a `type`: `CLASSIC` or `BRCA` for races, `PLACE_POINTS` for championships. A file carries only numbers and
switches, never logic, so a genuinely new kind of rule is a new `type` in the app. Values are range-checked on save and
import, and every change is audited.

**The file format.** An exported system is a JSON (or YAML) file holding just its rules; the name is given when it is
imported (the Scoring Systems page uses the file's name). A race system:

```json
{
  "type": "BRCA",
  "bestHeatsCount": 2,
  "splitTiedPlaces": true,
  "maxRaceWindowSeconds": 390,
  "finalLegs": 3,
  "legsCounted": 2,
  "carsPerFinal": 10,
  "fillTopFirst": true
}
```

A championship system:

```json
{
  "type": "PLACE_POINTS",
  "bestX": 8,
  "bestY": 10,
  "tqBonusPoints": 0,
  "aFinalWinnerBonusPoints": 0,
  "showOverallTable": true,
  "pointsScale": [{ "position": 1, "points": 100 }, { "position": 2, "points": 95 }]
}
```

Limits: best heats 0 to 50; race window 0 to 86400 seconds; A final legs 1 to 10 with legs counted no more than the legs;
cars per final 0 to 50; for a championship, best X at least 1 and no more than best Y, and best Y at most 100 (set both or
leave both out to count every round), bonuses and points 0 to 10000, places 1 to 200 in the scale, each place once. A field left out counts as 0 or
false, so check an imported file on the page. A name that is already taken for that kind (ignoring capitals and spaces)
is refused.

**Race formats.** A Points Finals format has a **Scoring system** picker. Choosing one fills best heats count, tied
places, BRCA qualifying, the race window, cars per final, fill A first, A final legs and legs counted from the system, and
saving the format copies them again. When the format is added to an event class, the class takes the system's values as
they are that day, so editing a system afterwards never changes a class already set up. Changing one of those options by
hand in the editor stops the format following the system ("Set by hand"). Editing a system does not rewrite the
formats that name it; the numbers on the formats page are those of the last save, and a class added later takes the
system's current values. Exporting a format leaves the system out of the file (the scoring options in it are already the
system's values), so a file imports the same on any club's machine. A system a format uses cannot be deleted. The
scoring itself is unchanged: the same settings are read as before, so formats saved earlier score exactly as they did.
Bump Up and Timed formats do not use systems yet.

**On printouts.** The officials' results printouts (round summary, final results, meeting pack) say "Scored under
<system name>" at the top of each class whose format was set from a scoring system. A class whose format was set by hand,
or whose system has since been deleted, shows nothing. The live boards and the public pages do not show it.

**Championships.** On a championship's Config tab, an admin can pick a championship scoring system and **Apply** it. This
copies the rounds scoring (best X from Y), the TQ and A final bonuses, the overall table setting and the whole points scale
onto the championship, in one step that is recorded in the audit log with the values before and after. Nothing is linked:
the name, classes, rounds and exclusions stay as they are, every field can still be edited, and editing the system later
does not change a championship it was applied to.

## Known gaps

- **`FTQ`, `FASTEST_LAP` and `CONSECUTIVE_LAPS`** all use total laps then best lap.
