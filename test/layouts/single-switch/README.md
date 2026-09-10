# single-switch

**A turnout with two routes through it**, on one page, drawn as one row with a branch that curves away
north.

    y=2                                       bogen  BranchPlatform  buffer
    y=3   buffer  WestEnd  ──  Approach  ──  SWITCH  ──  MainPlatform  buffer

The switch's toe faces WEST, so a train coming from the approach may take either arm and a train coming
back from either arm trails through it. That is the shape this scenario exists for: **two logical routes
over one piece of metal**, which is what lock edges are, and which no hand-built fixture in this suite
has ever had. `test/README.md` recorded that gap as MON-C17 to C21 before a defect walked through it.

It also produces something else the suite has never had by hand: **a split square**. Trains arrive at
`Approach` from both sides, so the builder emits it as two Points sharing one block — `Approach
(eastbound)` and `Approach (westbound)`.

## Invariants — a test may rely on these

- **The page** is `1 - Junction`, page id `1`.
- **The topology** above: four feedback sensors, one `linksweiche` at `5,3` whose trailing direction is
  opened in `setup.json` (a switch defaults to base-to-forks, and an unopened one makes this a one-way
  railway with half the edges missing).
- **The station names**: `WestEnd` (`1,3`), `Approach` (`3,3`), `MainPlatform` (`7,3`),
  `BranchPlatform` (`6,2`). All four are stations.
- **The s88 addresses**: WestEnd 10, MainPlatform 12, BranchPlatform 14, Approach 16. The switch is
  accessory address 1 (`artikel=2` in the file, which the parser halves).
- **The three stub ends are termini** — `WestEnd`, `MainPlatform` and `BranchPlatform` carry
  `mustReverse` in `configuration-Main.json`, because that is what a buffer stop is. Without it an
  unmarked dead end is a trap: the builder emits a plain copy with no way out, and the railway is
  one-way.
- **What it builds to**: 4 reduced points, 5 Points, 6 edges.

## Not invariants — set these in code

- **Lengths.** No tile in this scenario has one. A test that needs measured track calls
  `session.setTileLength(tile, n)` itself. Adam's rule: *"hand-authored for topology, lengths in code."*
- **Train placements and locomotive properties.** Nothing stands anywhere here and no locomotive is
  named. A test creates its own (`model.newMM2Locomotive`) rather than borrowing one from the real
  database — two tests broke on 2026-09-09 for borrowing a real locomotive's length.
- **Priorities, homes, exclusions, timetables, globals.** The configuration is empty of them
  deliberately.

## Used by

- `core.testTwoRoutesShareOneSwitch`
- `core.testACoveredSwitchClosesTheOtherRoad`
- `core.testALongTrainIsOfferedNothingOnAOneUnitRailway`
- `core.testATrainMustFitEverySquareOnItsRoute`
- `core.testACompulsoryTurnIsChosenLikeAnyOtherStation`
