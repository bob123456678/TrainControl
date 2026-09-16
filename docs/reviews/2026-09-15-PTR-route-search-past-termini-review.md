# The route search past termini (OB-229), reviewed

**Status:** open 2026-09-15 - round 1 fixed B1 (claim `19a680f3`, fix `b4776f6b`), C1, C2 and C4; C3 answered by the battery

**Prefix:** PTR (checked free, with PTV and PTW for the validation rounds: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, every declaration spelling in `docs/reviews/`, and a grep of `src/`, `test/` and `docs/`)

**Covers** `6dbc66cb..5742180a`: `4b519e71` (claim), `badb0a2c` (the fix - `Layout.bfs` does not extend a route through a terminus that is not its end, Adam's choice *"Search past termini"*), `4f944486` and `33505fba` (tracker), `ea274760` (test sandbox), `492848ef` (no round trip onto a train's own square, Adam: *"we should never do a round trip just to change direction"*), `838e3150` (the v2.8.1 station-paths pin) and `5742180a` (the room-rule census).  One reviewer (Fable), read-only; every fix below was run.

**On the pending tests.**  B1 touches MT-439 and MT-441, and adds MT-442.

---

## A - wrong behaviour on the layout

None.  The reviewer confirmed that the search now prunes exactly the points `isPathClear` refuses as intermediate termini (PTR-D1), and that no door other than a hand-edited timetable can carry a round trip onto a train's own square (PTR-D3).

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| PTR-B1 | Fixed | `Layout.firstClearOrWhyNot` - Why Not Moving? said "No track route leads there." where the track connects through a terminus |

### PTR-B1 - Why Not Moving? blamed missing track for a terminus in the way

| | |
|---|---|
| **Disposition** | Fixed |

Before OB-229 the search returned the route through the terminus, `isPathClear` refused it, and the reason was *"Contains an intermediate terminus station"*.  After it the search returns nothing on its first call, and the answer fell through to *"No track route leads there."* - whose own comment says it is the difference between "build some track" and "wait a minute".  The station is on the diagram and connected.  Touches MT-439 and MT-441.

**Confirmed by running.**  `core.testARouteIsFoundPastATerminus.testThroughTheTerminusAloneItIsStillRefused`: the only route to RU9_E runs through the terminus RU9_T.  Red: *"Why Not Moving? says 'No track route leads there.' of RU9_E, whose only route runs through the terminus RU9_T"*.

**Fixed, round 1.**  `bfs` keeps OB-229's rule and gains a private form that may walk through termini; when `firstClearOrWhyNot` finds no route at all it asks that form, and a route there makes the reason the route check's own sentence.  A station no track reaches still says so - the same claim asserts it of a station joined to nothing.  MT-442.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| PTR-C1 | Fixed | the census javadoc said refused-on-the-way was pinned at zero |
| PTR-C2 | Fixed | nothing asserted the new `ON_THE_WAY` square still refuses |
| PTR-C3 | Answered | two sibling pins walk `bfs` on the snapshot and were not shown re-run |
| PTR-C4 | Fixed | over-stated comments beside the change |

### PTR-C1 - the census javadoc

**Fixed:** it says zero until OB-229 and points at the band's own comment.

### PTR-C2 - the listed square was never asserted to refuse

The size test was `squares.size() >= ON_THE_WAY.length - 2`, which one entry never fails.  **Fixed:** with any square listed, at least one must still refuse.  Green before and after - the listed square refuses on this railway today; the claim now fails if it stops.

**And it was already covered, which this entry got wrong** (AMV-C3).  The band floor asserts at least 50 journeys are refused on the way, every one of them putting its refusing square into the map the subset check then requires to be inside `ON_THE_WAY` - so the listed square stopping would already have failed one of those two, and the new line can never be the first to fail.  It is not vacuous, and it is not the guard this entry claimed it was.

### PTR-C3 - the sibling pins

`core.testWhichSquaresTheRoomRuleClosesOff` and `core.testTheFrozenRailwayIsStillTheRailway` also walk `bfs` on the snapshot.  **Answered by running:** the full battery after `5742180a` was 257 classes green, those two among them - the zero the first pins and the half the second requires both hold under the new search.  The commit messages did not say so; this entry does.

### PTR-C4 - comments

`testTheLengthGuardsOnTheRealLayout`'s helper said the only track to TunnelLongPark passes a terminus (the precondition proves it of the shortest route) and that its search was `bfs` as it was (it omits the destination check); `Layout.bfs` said all three callers pass exclusions (there are five in the application, and a test passes none).  **Fixed.**

---

## D - looked wrong and is not

| id | what |
|---|---|
| PTR-D1 | `bfs`'s pruning is exactly `isPathClear`'s intermediate-terminus set: a terminus start is exempt in both, another copy of the start square is refused by both, and a turning copy is built `terminus: true` and read the same way by both |
| PTR-D2 | A long loop holds the layout monitor for its whole configuration (150 ms an edge and more), and only other dispatches wait; `bfs` is shortest-first, so a loop is chosen only when nothing shorter is clear.  Accepted with the routes Adam approved |
| PTR-D3 | A round trip onto a train's own square reaches `executePath` only from a hand-edited timetable; every other door refuses it, and `atHome` uses the same block test as the guard, so the guard never refuses a move the planner needs |
| PTR-D4 | The OB-229 claim is red for the reason it names and the round-trip claim's count of 0 can only mean the planner refused too; neither `bfs` test class marks a terminus |

---

## What the passes missed

- **A fix that removes a wrong answer can remove the right reason with it.**  OB-229's search stopped returning routes the railway refuses - and with them the only evidence Why Not Moving? had for saying why.  The claim asserted a reason existed, not what it said.
