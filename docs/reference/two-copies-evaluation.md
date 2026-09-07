# The two copies: could following the edges do the same job?

Adam, annotating [`behaviour.md`](behaviour.md): *"Having two copies seems like unnecessary
complexity. I wonder if it can be done more easily by simply following the edges? Don't implement
until evaluating."*

This is the evaluation. **Nothing has been changed.**

---

## The headline, and it is a measurement rather than an argument

**On your railway, the split does not fire.** `testEverySquareOnThisLayoutBuildsToOneCopy` walks every
named square in your actual configuration and records how many copies each builds to. Every one builds
to **exactly one** — including the squares marked may-reverse.

The reason is specific and worth knowing: a may-reverse square on your layout is a **dead end**, and
`nodesFor` deliberately does not emit a plain copy where an arriving train's only track ahead is the
track it came in on. It would be a Point that can be reached and never left, and — being a station —
one autonomy could pick as a destination, after which the train's day is over. So the turning copy is
emitted alone.

**The complexity you are noticing is real, and it is paid entirely in the code.** Your graph does not
carry it today. That changes the question from "is this worth simplifying" to "is this worth
simplifying *now*, before anything on the railway needs it".

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

**Don't do it now, and don't rule it out.** Three reasons, in order:

1. **It buys nothing on your railway today.** Every square builds to one copy. The bugs it would
   prevent are bugs in code paths your graph does not currently exercise.
2. **It is a change to the thing every feature stands on.** Locking, blocking, length, destinations and
   placement all read the graph. This is a bigger blast radius than `DD-C1`, which is at least only a
   file.
3. **The day it becomes worth doing is knowable, and there is already a tripwire for it.**
   `testEverySquareOnThisLayoutBuildsToOneCopy` goes red the first time a square genuinely builds to
   more than one copy. That is the day the cost starts being paid on the railway rather than in the
   source, and the day to re-read this document.

If it is ever done, do it in the order: teach the search to carry the arrival side; make the graph
emit one node per square behind a flag; run both and compare the paths they produce on your real
layout, square by square, before deleting anything.

---

*Written 2026-09-07, at Adam's request, on the annotation that asked for it. No code was changed.*
