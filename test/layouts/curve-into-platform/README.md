# curve-into-platform

**A station approached round a curve**, with an ordinary straight station on the same page as its
control.

    y=2   buffer
    y=3   NorthSiding
    y=4   │
    y=5   CurvedPlatform ── InnerApproach ── OuterApproach  buffer
    ...
    y=8   buffer  StraightOuter ── StraightPlatform ── StraightInner  buffer

`CurvedPlatform` is an `s88bogen` whose two ports are **N and E**: the track comes in from the east and
leaves to the north. So a train that arrived by E is facing N, and the compass opposite of its facing —
S — is a side that square has no track on at all.

That is the shape behind **REV9-B3** (`docs/reviews-2026-09-09/REV-reversal-mechanics-review.md`): the
hand-placement door computes an ordinary station's arrival side as the compass opposite of the facing,
which on a curved station records a side no build edge enters by, and the tail walk in
`Layout.edgesCoveredByStandingTrains` then silently blocks nothing. Every tail fixture in this suite
before this one was a straight chain where the two vocabularies agree, which is why it went unseen.

`StraightPlatform` is the same code path over a straight row and is the control: there the compass
answer and the build's answer are the same side, and everything passes.

## Invariants — a test may rely on these

- **The page** is `1 - Curve`, page id `1`.
- **The topology** above. `CurvedPlatform` is entered by `[N, E]` and `StraightPlatform` by `[E, W]` —
  that difference is the whole point of the fixture, and `testTheFixtureHasACurvedStationAndAStraightOne`
  asserts it before anything else runs.
- **The station names**: `CurvedPlatform` (`5,5`), `InnerApproach` (`7,5`), `OuterApproach` (`9,5`),
  `NorthSiding` (`5,3`), `StraightOuter` (`3,8`), `StraightPlatform` (`5,8`), `StraightInner` (`7,8`).
  All seven are stations.
- **The s88 addresses**: OuterApproach 30, InnerApproach 32, CurvedPlatform 34, NorthSiding 36,
  StraightOuter 40, StraightPlatform 42, StraightInner 44.
- **The four stub ends are termini** — `OuterApproach`, `NorthSiding`, `StraightOuter` and
  `StraightInner` carry `mustReverse` in `configuration-Main.json`, for the reason `single-switch`'s
  README gives: an unmarked dead end is a trap, and the railway would be one-way without them.
- **Neither platform is a may-reverse square.** The defect this fixture exists for lives on the
  door's *ordinary station* branch; a square trains may turn at is asked about instead.
- **What it builds to**: 7 reduced points, 10 reduced edges, 10 Points (both platforms and
  `InnerApproach` split), 10 edges.

## Not invariants — set these in code

- **Lengths.** No tile here has one. `testACurvedPlatformRecordsASideTheBuildUses` gives every square a
  length of 1 in its own `@BeforeClass`, because the tail walk refuses an unmeasured segment.
- **Train placements and locomotive properties.** The test creates its own locomotive and sets its
  train length.
- **Priorities, homes, exclusions, timetables, globals.**

## Used by

- `core.testACurvedPlatformRecordsASideTheBuildUses`
