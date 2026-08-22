# The route inventory dumps of 18 August

**Status:** closed

What the seven numbered files in `docs/reviews/` were, what they said, and why they are gone.

They were **generated output**, not documents: `testRouteInventory` wrote them there on 2026-08-18 while
the diagram-derived model was being compared against the hand-authored one. The harness now writes to a
temporary directory instead, so nothing has produced files there since, and nothing referenced them -
not a review, not a commit message, not the test that made them.

408 KB of dumps, of which two files were byte-identical copies of each other.

## What they held

Each pair was one configuration: a `.json` graph and a `.txt` report of every route the running model
would offer, with intermediate points, for a person to read.

| File | points | edges | destinations | What it was |
|---|---|---|---|---|
| `1-derived-active` | 78 | 88 | 23 | The diagram-derived graph, active configuration |
| `2-stuck-1b` | 79 | 81 | 35 | Configuration 1b, kept because trains stuck on it |
| `3-hand-authored-2.8.1` | 62 | 92 | 16 | The hand-written 2.8.1 configuration, as the baseline |
| `4-bundle-1d` | 77 | 85 | 23 | Configuration 1d |
| `5-bundle-1e` | 78 | 88 | 23 | Configuration 1e |
| `6-probe-bottommaina` | - | - | - | Why BottomMainA offered no routes: every candidate BLOCKED, with the path that blocked it |
| `7-as-the-ui-sees-it` | - | 88 | - | What the placement view would show for Autonomy 1f - and that **no edge carried a length** |

`1-derived-active.json` and `4-bundle-1d.json` were the same file byte for byte.

## What they were for, and what came of it

The question was whether the derived model routed trains the way the hand-authored one did. The counts
are the useful residue: the derived graph carries **more points and fewer edges** than the hand-authored
one - 78 and 88 against 62 and 92 - which is the shape to expect. It mints a Point per sensor, including
the ones a person would not have bothered naming, and it refuses to route over anything it cannot
command.

Two things in these dumps were acted on at the time:

- **`6-probe-bottommaina`** showed a station offering nothing at all, with every candidate route blocked
  by a train standing somewhere along it. That is the observation behind "why is it not moving" being
  built at all: the information existed and was being thrown away on every attempt.
- **`7-as-the-ui-sees-it`** recorded that no edge carried a length, which is why edge lengths and the
  train-length checks got attention afterwards.

Both are now covered by tests and by the editor's own findings list, which is a better home for them
than a text file nobody regenerates.

## Why they are not kept

A generated report in a review folder goes stale in silence. There is no date on the face of it, nothing
says which commit produced it, and the next person to read one cannot tell whether it describes the
railway as it is or as it was five hundred commits ago. `testRouteInventory` can produce all of them
again in a minute, against whatever the code is now - which is the only version worth reading.

See the "Three kinds of document" rule in [../README.md](../README.md).
