# live-snapshot

**A frozen copy of Adam's real railway**, for the tests that need geometry no hand-authored fixture is
going to have: complex pathing, random-movement checks, and the base for mutations.

Adam: *"make sure you still have a copy of the live layout for ones that require complex pathing and
random movement checks. I'd recommend using these complex setups as the base for mutations."*

## Where it came from

Taken from **commit `e6f4649ceea0258e5fa6a378616b0158d04c6289`** (branch `autonomy-diagram-r0`,
2026-09-09), via `git show HEAD:cs2_sample_layout/...` — **not** from the working tree. That matters:
his tree carried uncommitted operating changes to `setup.json`, `configuration-Main.json` and
`1 - Main.cs2` when this was frozen, and a fixture taken from a working tree is a fixture nobody can
reproduce.

All ten tracked files were copied byte for byte:

    config/autonomy/configuration-Main.json
    config/autonomy/setup.json
    config/autonomy_legacy/autonomy.json
    config/gleisbild.cs2
    config/gleisbilder/1 - Main.cs2
    config/gleisbilder/2 - Bottom.cs2
    config/gleisbilder/3 - Top Parking.cs2
    config/gleisbilder/4 - Combined.cs2
    config/gleisbilder/5 - Test.cs2
    config/gleisbilder/routes.json

To refresh it, re-run the same `git show HEAD:` copy from a clean commit and update the hash above —
never copy from the working tree, and never write to `cs2_sample_layout`.

## Why a copy rather than the original

Eighteen test classes open `cs2_sample_layout` today. That is Adam's live railway: it changes as he
operates it, so their ground truth moves underneath them. On 2026-09-09 three broke in one morning —
one had picked its subject out of the live railway by a property he had since changed, and two had
borrowed a real locomotive's length and were refused when the length rule widened.

This folder is the same railway with the clock stopped. `support.Scenario` still copies it to a sandbox
before anything reads it, so the checked-in fixture is never written to either.

## Invariants — a test may rely on these

- **The five pages**, in index order: `1 - Main`, `2 - Bottom`, `3 - Top Parking`, `4 - Combined`,
  `5 - Test`.
- **Everything in the files** — station names, s88 addresses, portals, priorities, homes, lock edges,
  the whole authored setup as it stood at that commit. It is a snapshot; that is the point.
- **What it builds to**: 128 reduced edges, 96 Points, 149 edges. Pinned exactly in
  `testTheFrozenRailwayIsStillTheRailway`, because far below that is the five-edge skeleton
  `support.LayoutSandbox.wiredPages` describes — a railway whose switches have no accessories — and a
  test standing on it passes by asserting about null.

## Not invariants — set these in code

- **Lengths, train placements and locomotive properties**, exactly as in the hand-authored scenarios.
  The snapshot *does* carry the placements and lengths that were in the file at that commit, so a test
  that cares about either should set them rather than read them: they are ground truth for the file,
  not a promise about the railway. A test that picks a subject out of this railway *by a property* —
  "the first station with a length", "a locomotive long enough" — is the failure mode the whole library
  exists to end, and freezing the folder does not fix it. Name the square.

## Used by

- `core.testTheFrozenRailwayIsStillTheRailway`
- `core.testTheShadingFollowsTheTrain`
- `regression.testEscapeClosesTheEditor`
- `regression.testTheDiagramRefreshDoesNotWaitOnTheRailway`
- `regression.testTheGreyDoesNotRebuildTheDiagram`
- `regression.testTheWashIsNoLongerThanTheTrain`
- `ui.testBlockedTrackIsGreyWhileAutonomyRuns`
- `ui.testTheShadingIsRedrawnWhenATrainMoves`
- `ui.testTheTrainIsShownAsALine`
