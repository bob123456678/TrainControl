# The two copies: could following the edges do the same job?

Adam, annotating [`behaviour.md`](behaviour.md): *"Having two copies seems like unnecessary
complexity. I wonder if it can be done more easily by simply following the edges? Don't implement
until evaluating."*

This is the evaluation. **Nothing has been changed.**

---

## The headline was wrong, and this is the corrected measurement (2026-09-08)

**What this document said until 2026-09-08:** *"On your railway, the split does not fire.
`testEverySquareOnThisLayoutBuildsToOneCopy` walks every named square in your actual configuration and
records how many copies each builds to. Every one builds to exactly one — including the squares marked
may-reverse."* The complexity, it concluded, is paid entirely in the code and your graph does not carry
it today.

**That number was measured on a railway in pieces, and the real one is nothing like it.** Now that
`core.testEverySquareBuildsToTheCopiesTheSetupImplies` exists and walks the wired reduction, the census
is:

| | |
|---|---|
| squares in the reduction | 58 |
| squares emitted as MORE than one Point | **30** |
| stations | 33 |
| stations emitted as more than one Point | **13** |
| squares emitted as FOUR Points | BottomMainB, BottomMainC, BottomMainPost, RampDown |

**How the old number came about.** Every sandbox test built its pages with a bare
`CS2File.parseLayout`, which reads the drawing but does not attach an `Accessory` to any switch.
`TileGraph` refuses to trace through a switch it cannot command, so the railway falls from 128 reduced
edges to 18, nearly every square is left with a single arrival side — and a single arrival side is a
single copy. The same census on that skeleton returns **1 split square out of 58**: a clean, plausible
number that reads exactly like a real one. `LayoutSandbox.wiredPages` records the recipe defect and the
day it was found, which is the day *after* this document was written.

The old headline's mechanism was wrong in the same direction. A may-reverse square here is *sometimes*
a dead end — the seventeen parking berths are, and each is emitted as one turning copy, which is the
paragraph below working exactly as described. But `BottomMainB`, `BottomMainC`, `BottomMainPost` and
`RampDown` each have two arrival sides and a turn at each, so they are emitted as **four** Points; and
`LowerFront` is a may-reverse square with somewhere to go, emitted as two.

**So the complexity is not paid entirely in the code. Your graph carries it now.** That is a change to
the premise of reason 1 below, not to reasons 2 and 3, and **what to do about it is your call** — see
"The honest recommendation", which has been marked up rather than rewritten.

---

## What the copies actually are

Not two. `nodesFor` emits one node per **arrival side**, and then a second per arrival side where
trains may turn — so a four-way square where trains may reverse is up to eight nodes, not two. The
count is `sides × (1 + mayTurn)`, less the plain copies that would have nowhere to go.

And the reason they exist is not reversal. **It is that `Edge` is directional and `Point` is the only
thing an edge can attach to.** If a square were one Point, an edge arriving from the west and an edge
departing to the west would both hang off it, and the path finder could route west-in → west-out —
which is a reversal it never accounted for and never reserved track for. The node identity *is* the
"which way am I pointing" state. Which is the same fact `behaviour.md` §8 records from the other
direction: facing is encoded as one-way edges.

## What "following the edges" would mean

One Point per square, and the arrival side carried by the **search** instead of by the graph: the BFS
state becomes `(point, arrivalSide)` rather than `point`. This is a standard transformation — the same
product graph, computed lazily instead of materialised.

It is a real option. It is not a smaller amount of state; it is the same state, moved.

### What gets better

- `placementCopy` disappears, and with it the fall-through to copy 0 that caused four of this week's
  facing defects.
- "Which copy do you mean?" stops being a question every caller has to answer. `StationIndex.speakerAt`,
  the caption doors, the right-click menus and the length rules all currently have to pick.
- The graph is smaller and reads like the diagram.

### What gets worse, and this is the larger list

Everything that holds a `Point` today gets the direction for free. Move the state into the search and
each of these has to be handed it explicitly:

| | what it would need |
|---|---|
| **Locking and reservation** | a lock is held on a Point; two directions through one square are one physical occupancy, but a path must still reserve the *approach* it uses |
| **`isPathClear`** | every length and blocking rule reads `path.get(i).getEnd()`; the ends become ambiguous |
| **`arrivedFrom`** | already a per-Point property; it would need to become per-(point, side) or move into the walk |
| **The tail walk** | starts from a side and follows track regardless of direction — it would now start from a state, not a node |
| **Destination selection** | `isSendableDestination` asks a Point four questions; "is this square sendable *arriving from the west*" is a different question |
| **The diagram** | greying, captions and the facing menu all key on squares already, so this half is neutral or better |
| **The setup** | unchanged — a placed train still needs a recorded facing, because a stationary train's direction is not derivable from anything |

The last row is the one that decides it. **The facing property does not go away.** A train standing
still has a direction, nothing in the track can tell you what it is, and that is precisely why the
setup records it. So the transformation removes the copies but not the concept, and every defect this
week that was *about the facing* — the paste direction, the arrival side, the placement copy — would
still exist in some form. What would go is the specific failure mode of picking the wrong copy.

## The honest recommendation

**Don't do it now, and don't rule it out.** Three reasons, in order — the first of which no longer
holds:

1. ~~**It buys nothing on your railway today.** Every square builds to one copy. The bugs it would
   prevent are bugs in code paths your graph does not currently exercise.~~

   **Withdrawn, 2026-09-08.** Thirty of your fifty-eight squares split, thirteen of your thirty-three
   stations do, and four stations are four Points each. The code paths this would remove are ones your
   graph exercises every day, and the four facing defects of that week were in them. **This reason is
   gone; whether it changes the decision is yours, and nothing has been done about it.**
2. **It is a change to the thing every feature stands on.** Locking, blocking, length, destinations and
   placement all read the graph. This is a bigger blast radius than `DD-C1`, which is at least only a
   file. *(Unaffected by the correction above, and now the strongest reason to wait.)*
3. **The day it becomes worth doing is knowable, and there is now a tripwire for it.**
   `core.testEverySquareBuildsToTheCopiesTheSetupImplies` pins the census above and goes red when it
   moves in either direction. Named as `testEverySquareOnThisLayoutBuildsToOneCopy` here until
   2026-09-08 — a test that did not exist, cited four times across two documents and one test, which is
   how the wrong number stood unchallenged. The day to re-read this document is the day the split
   count moves.

If it is ever done, do it in the order: teach the search to carry the arrival side; make the graph
emit one node per square behind a flag; run both and compare the paths they produce on your real
layout, square by square, before deleting anything.

---

*Written 2026-09-07, at Adam's request, on the annotation that asked for it. No code was changed. Its
headline measurement was corrected on 2026-09-08, when the test it cites was finally written; see the
top of this file. Still no code has been changed.*
