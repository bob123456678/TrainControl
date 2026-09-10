# Where we stopped — 2026-09-10

Everything is committed on `autonomy-diagram-r0`. `git status` is clean apart from the files under
`cs2_sample_layout/` that your own running TrainControl rewrites.

| | |
|---|---|
| manual tests awaiting you | 29 |
| open issues in the Inbox | 22 |
| open review findings | 30 |

## The one number to read first

The room rule now applies at every square a route runs through, which is your ruling 1b. Over 1848
routable station pairs and six train lengths — 11088 journeys — it refuses about **1660 more** than
before, on top of the 1760 the berth rule already refused. Roughly one journey in three.

**That is not the number that matters, and the review found the one that does.** A third of journeys
refused *evenly* strands nobody. Refused at the squares on every road out, it strands everybody
standing there — and it does:

| train | destinations offered in total | squares offering NOTHING |
|---|---|---|
| 1 unit | 735 | 0 |
| 2 units | 389 | **8** |
| 3–6 units | ~380 | **8** |

Seven of those eight are closed **by the ruling** — they offered 33 or 34 destinations under the
berth-only rule and offer none under this one: `BottomMainA (eastbound)`, `BottomMainB (eastbound)`,
`BottomMainB (eastbound, reverse)`, `BottomMainC (eastbound)`, `BottomMainC (westbound, reverse)`,
`BottomMainPost (northbound, reverse)`, `BottomMainPost (southbound)`. The eighth, `ParkingTrack12`,
is the track rather than the rule.

**The remedy is almost certainly a tape measure.** Every square that does the refusing measures ONE
unit — `5:22,7`, `5:19,12`, `5:14,13`. If those are the real lengths then the railway honestly cannot
take those trains there; if they are placeholders, measuring them gives all thirty-five (square,
length) pairs back at once. `OB-196` carries it, and nothing is blocked on it:
`core.testWhichSquaresTheRoomRuleClosesOff` pins the two figures and prints the list on every run.

## What you answered, and what shipped

- **Ruling 1b — fit everywhere, not only at the destination.** One walk asked of every prefix; the
  destination is the last iteration rather than a rule of its own. Both walks were renamed, because
  both names had stopped being true.

  **One thing your ruling did not state, that I had to decide:** a square the train passes *through*
  is judged only where a **switch** is behind it. Without that the walk counts "the route so far" as
  the room, which measures nothing — the track behind where the train started has not been looked at
  and the train is standing on it. The first cut refused a four-unit train four units of room. On your
  railway the condition costs nothing: **0** of the 1660.

- **OB-195 — narrow the notice.** The extra `isMustTurnAround` clause is gone. **Can Be Chosen in Full
  Autonomy** is the one switch that decides who autonomy may send. §3 carries it.

- **FR-066 — Control+E.** You picked the key. The first cut had two defects the review caught, both
  the shape the method's own javadoc said it avoided: it acted on a text label (whose menu offers no
  length at all) and it wrote to the hovered square where the menu writes to the run's leader. Both
  doors now ask `offersALength` and write to `squareTheLengthWouldGoOn`.

- **Question 1 and the magenta** — confirmed as they shipped.

## Still to do

1. **The viewer's missing capture**, then the four removals and two fixes from the editability
   assessment. One change against five findings (OB-144, OB-183, D2-A1, D3-C5, REV-B2).
2. **OB-196** — the seven closed squares, above. Yours to decide.
3. **OB-197** — Control plus any unbound letter selects a locomotive button in the main window. A
   one-line change, but it takes something away, so it is yours.
4. **OB-198** — the editor's hovered square is never cleared, so a shortcut after a page step acts on
   the old grid.

## What else changed today

**The census moved off your railway.** It was reading `cs2_sample_layout`, and one of the squares it
pinned — `TopR1ParkShort` at three units — existed only in your *uncommitted* working copy. A clean
checkout measured a different railway and nothing went red. It reads `test/layouts/live-snapshot` now,
which was the last item on the "get the tests off his railway" list.

**Route choice is not reproducible between runs.** Same railway, same trains, same code: four runs of
the census gave 2116, 2146, 1648 and 1617. `bfs(from, to, exclude)` returns *some* route avoiding the
ones already found rather than the next in a defined order, because the adjacency is keyed by objects
whose hash is their identity. That is why the census asserts a band. Whether the variety is wanted is
a question for you.

**The timetable flake has a diagnostic that can work now.** It was taken *after* `stopLocomotives()`,
and the thing it reports is exactly the flag that call clears — so `Auto running: false` was
guaranteed on the failing path and told nobody anything. Three battery failures were "diagnosed" with
it.

**Nine findings of the review are closed in the code**, four of them comments that claimed to mirror a
rule they no longer did — the defect this project keeps finding in itself.
