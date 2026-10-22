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
  the same number of laps.
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

Example (bump-up, grid size 10, bump spots 2, two finals, 18 drivers):

- **B final**: the lowest qualifiers, ranks 9 to 18 (10 cars), gridded in qualifying order.
- **A final**: ranks 1 to 8 plus **2 bump places**, which stay empty until the B final finishes. Then the top two
  finishers of the B final are added to the back of the A grid (positions 9 and 10, marked as bumped).
- A third final (C) feeds B in the same way, and B's bumped drivers sit at the back of B's grid.

If the grid would not fit the field, it grows: with 20 drivers, grid size 10, 2 bump spots and two finals, the grid
becomes 11 (B ranks 10 to 20; A ranks 1 to 9 plus 2 bumps), so nobody is left out.

The lowest final is always filled first, from the bottom of the standings, so it gets the full grid and the higher finals take what is left. Example (points finals, two finals, 14 drivers): 7 cars each, B final ranks 8 to 14, A final ranks 1 to 7. With 15 drivers, B has 8 (ranks 8 to 15) and A has 7. A short bump-up field works the same way: 14 drivers, grid size 10, 2 bump spots, two finals gives B ranks 5 to 14 and A ranks 1 to 4 plus 2 bump places.

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

A class run to the Shikabane format never scores in a championship, whatever the scoring source, and gets no top
qualifier bonus: Shikabane is for fun days (see [Race types](race-types.md#44-shikabane)).

A championship links events (each event is a round) and scores each driver per class.

| Setting | What it does |
|---|---|
| Points scale | Points per finishing position (position 1 gets the first value and so on; a position not in the scale scores 0) |
| Scoring source | `QUALIFYING`: the driver's best qualifying place scores. `FINALS`: the best final place scores. `BOTH`: the points for the best qualifying place **and** the best final place are added. Practice never scores. |
| Best X from Y | Once a driver has at least Y rounds, their worst Y minus X rounds are dropped. Can be set per class; either number empty falls back to the championship's. With only X set, exactly X rounds are kept. With neither, every round counts. A DNS or excluded round is an ordinary round here, worth 0, so it is dropped first. |
| TQ bonus points | Extra points to the driver who topped qualifying in the class at a round |
| A-final winner bonus points | Extra points to whoever won the A final in the class at a round |
| Exclusions | An official can exclude a driver from a round: that round is worth nothing, bonuses included |

How a round is scored:

- A driver's **qualifying position** is their best single heat finish at the event (not the combined qualifying order of section 2), and their **final position** is their best place in any round of finals, where a round of finals is placed as one: the A final's order, then the B final's without the drivers who bumped up into the A, then the C final's, and so on. A B final win is therefore not a first place, and a driver who bumped up has their A final place. The round's position is the better of the two (shown in the standings); the **points** follow the scoring source.
- A final is not placed until every final above it has finished, since its drivers may still bump up. A driver who bumped up and did not start the higher final is classed last in it, ahead of everyone from the final below, in the order they finished that final. If nobody raced the higher final, the final below keeps its own places.
- Example of stacked finals: the A final finishes Ann, Bob, Cat and the B final Cat (who bumped up), Dan, Eve.
  The round's places are Ann 1, Bob 2, Cat 3, Dan 4, Eve 5. Cat's B final win does not count.
- Abandoned races are not excluded from position points: if one has a stored result, its rows score.
- A driver on the grid at a round with no result in it has a **DNS** round, worth 0 points, which still counts
  towards Y.
- **Drops**: the worst rounds are dropped to X from Y once the driver has Y rounds. "Worst" is fewest points, then
  the worse position, so a finish worth nothing is dropped before a DNS.
- Bonuses are **per driver, per class and per round**: winning in two classes or two rounds earns each, and winning two A finals in one event pays once. The TQ goes to the driver at the top of the class's qualifying table once all its qualifying heats are finished (lowest points for a low-points class, otherwise most laps then best lap), so it is not given out part way through qualifying. The A-final bonus goes to whoever finished 1st in a finished final lettered A.
- The class table is ordered by total points, then name.

Example: scale 1st 15, 2nd 12, 3rd 10, 4th 8, 5th 6; scoring source `BOTH`; TQ bonus 2; A-final bonus 3;
best 2 from 3 rounds. Dan, in one class:

| Round | Qualifying | Final | Round points |
|---|---|---|---|
| 1 | 1st (15) | 1st (15), also TQ and A-final winner | 30 |
| 2 | 4th (8) | 3rd (10) | 18 |
| 3 | 2nd (12) | did not start | 12 |

Three rounds are run, so the worst (round 3, with the fewest points) is dropped: 30 + 18 = 48. The bonuses are
added after the drops, per round won, so Dan also gets the TQ bonus (2) and the A-final bonus (3) for round 1: **53**. A bonus is never dropped with its round.

With scoring source `FINALS`, only the final positions score: round 1 is 15 and round 2 is 10. Round 3 is a DNS worth 0 if Dan was on that final's grid (no round at all if he was not), and it is the round dropped. With the same 5 in bonuses: **30**.

## Known gaps

- **`FTQ`, `FASTEST_LAP` and `CONSECUTIVE_LAPS`** all use total laps then best lap.
