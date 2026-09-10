# Where we stopped — 2026-09-10

Everything is committed on `autonomy-diagram-r0`. `git status` is clean apart from the files under
`cs2_sample_layout/` that your own running TrainControl rewrites.

| | |
|---|---|
| manual tests awaiting you | 38 — nine of them new today |
| open issues in the Inbox | 21 |
| open review findings | 30 |

## What you ruled, and what it cost

### Ruling 1b — a train must fit at every square its route runs through

One walk asked of every prefix of the route; the destination is the last iteration rather than a rule
of its own. Both walks were renamed, because both names had stopped being true.

**One thing your ruling did not state, that I had to decide:** a square the train passes *through* is
judged only where a **switch** is behind it. Without that the walk counts "the route so far" as the
room, which measures nothing — the track behind where the train started has not been looked at, and
the train is standing on it. The first cut refused a four-unit train four units of room. On the
snapshot the condition costs nothing: **0** of the 1660.

**The numbers, corrected.** I reported eight closed squares and forty stranded pairs. A validator
found the census emptied the railway *once* instead of once per train length, so each pass after the
first ran with the previous probe still standing somewhere. It is **seven** squares and **35** pairs.
`ParkingTrack12` was never one of them.

And on your answer that A/B/C are distinct pieces of track measured at 1 only for testing —
`test/layouts/live-snapshot` now carries **no lengths at all**, and the two censuses set the three
tight tiles themselves and say in the file that it is a stress configuration rather than a survey of
your track.

### Occupancy restrictions bind every tier

Both fences are gone — `isAutoRunning` until 2026-09-09, `isFullAutonomyRunning` after it. One rule,
one answer, asked once in `isPathClear`.

**The planner moved with it, and had to.** `HomeStaging` stopped applying the rule when the fence
narrowed, precisely so it would not offer a leg the runtime refuses; with no fence it would offer
exactly that, and a plan whose first move fails is OB-073. Its copy is back, reading the occupancy the
**plan** has reached, and `auditAgainstRuntime` proves the two agree.

**One consequence to know about:** two stations that hold each other back are now a genuine deadlock —
whichever train arrives last finds its station closed. Return Home reports **no plan found**. It does
*not* say "impossible": the check that used to make that claim was wrong three separate times and was
removed rather than repaired, so the weaker true answer is the one that stands.

### The rest

- **OB-195** — the extra `isMustTurnAround` clause is gone. **Can Be Chosen in Full Autonomy** is the
  one switch that decides.
- **OB-197** — Control plus a letter no longer selects a locomotive button. That also made the
  free-key list honest: `B J O P Q U W`.
- **OB-196 declined** — not a defect, on your answer.
- **FR-066** — Control+E. The first cut had two defects a review caught, both the shape its own
  javadoc said it avoided. There is one door now: `buildTileMenu` adds its item inside
  `if (offersALength(tile))` and binds it to the square that predicate names.

## Reversal commands on the wire — the gap you asked about

You asked whether anything checked that reversal commands are *issued* when intended and not
otherwise. Half of it was covered: one test watched the wire, on one square kind, and made a
comparative claim. **A terminus had no emission test anywhere** — nine classes build terminus fixtures
and none of them asserted a direction command.

`regression.testWhatReachesTheTracksAtEachKindOfArrival` covers all of it now, absolutely rather than
comparatively. What makes an absolute claim possible is the window: dispatch sets a direction as the
train departs, so counting over a whole journey cannot tell an arrival that turned the train from one
that did not. `CB_PRE_ARRIVAL` fires once, on the last leg, before the destination's sensor — a mark
there and a count afterwards is the arrival and nothing else.

| the square | what must reach the tracks |
|---|---|
| a terminus | one direction command, always |
| a reversing point answered YES | one |
| a reversing point answered NO | none |
| a plain station | none |
| a may-reverse square passed through | none at the arrival |

Both mutations were run: removing `arrived.isTerminus() ||` reddens exactly one of them, forcing the
condition true reddens exactly three.

## Still to do

1. **The viewer's missing capture**, then the four removals and two fixes from the editability
   assessment. One change against five findings (OB-144, OB-183, D2-A1, D3-C5, REV-B2).
2. **OB-198** — the editor's hovered square is never cleared, so a shortcut after a page step acts on
   the old grid. Pre-existing; inherited by Control+E.
3. **Route choice is still not reproducible between runs** — you said to keep an eye on it. Four runs
   of the census gave 2116, 2146, 1648 and 1617, because `bfs` returns *some* route avoiding the ones
   already found rather than the next in a defined order.

## The manual tests

**Nine new** — MT-341 to MT-349: Control+E and its two guards, Control+letter, the occupancy rule by
hand and under Return Home, the route-wide refusal naming the square on the way, the compulsory-turn
notice, and a train really reversing at a terminus and not at a through platform.

**Five refreshed**, all of them tests that need measured track — MT-262, MT-278, MT-279, MT-280,
MT-333. You cleared the lengths from the working diagram, so each would have reported "nothing greys"
and meant nothing by it; each now says which length to set first. Two of them also predate the two
marks, and say so.

**None closed.** I read the twenty-nine open ones for a subject that has gone and did not find one —
the five above are stale in their *instructions* rather than in their subject, which is why they were
refreshed instead. Name any you want gone and they go.
