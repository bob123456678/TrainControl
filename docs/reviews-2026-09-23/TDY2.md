# Today's work (2026-09-23) on autonomy-diagram-r0 - validation of TDY and the round-1 diff

**Status:** closed

**Prefix:** `TDY2`

**Reviewed:** branch `autonomy-diagram-r0` at `08a47bdd`, 2026-09-23.  Baseline: TDY's range `68852cfc..281c79de`, and everything since `281c79de` (33 commits to `08a47bdd`).

**Method:** read every fix commit for the six TDY findings (`ac5fe2f2`, `8873b0a4`, `eab6e4ae`, `a67452cd`,
`ce8983ce`, `273727eb`, `919e0dc8`, `cdedb3fc`, `59fdb67d`) with `git show`, and each claim against its parent
(`git show <hash>^:path`) to decide whether it was red for the reason it names; then the two later repairs the task named
(`12ed2faa`, `b4b061e0`) and their first passes (`6b7301fc`, `0be2bbe4`), the test-run isolation (`6ce9d625`,
`ca7a423a`), AUT-B1's place rule, GUI-B1/B3 (`1c855483`, `2e565b5e`) and AUT-C1 (`5e2fbda5`, `0d060ffb`).  For each
changed rule, the enforcing method and every caller: `HomeStaging.atHome` / `homeCopiesOf` / `canGetHome` /
`whyNotHome` / `canRestOnSquare`, `AutonomyBuilder.homeCopy` / `placementCopy` / `facingOf` / `homeFacingsAt`,
`AutonomySession.homeFacingOf` / `writeHome` / `copyFacing` / `placeableFacingsFor` / `moveOntoFacingCopy` /
`flipFacing` / `faceTheWayItCameIn` / `facingChoices` / `facingsThatCannotBeHeld` / `captureFromLayout`,
`AutonomyEditorPanel.buildFacingMenu` and its home door, `TrainControlUI.followDirectionChanges` and the paste,
`Layout.walkOneTail` / `isPathClear` / `anotherTailOn` / `unlockPath` / `executePathInternal`.  Grepped src and test for
every reader of a renamed or re-purposed field (`copyArrival`, `clearedEdges`, `releasedEarly`, `takingPath`,
`DATA_FILE_NAME`, `userNodeForPackage`).  Python, read-only, over the eight message bundles (ASCII, keys,
placeholders) and over `test/layouts/live-snapshot/config/autonomy` to confirm which squares a finding reaches.  Other
validators' drafts were glanced at only to mark overlap (GUI2-B2).  **Nothing was run**; every finding carries a
verification request.

## A - high

### TDY2-A1 - Since GUI-B1, a train turned round (by the throttle or the Facing menu) at a square whose other facing only a barred copy holds stays on the copy facing the old way, and is dispatched the wrong way

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 (claims 8346be65, red first): placementCopy and copyFacing never choose a copy facing another way than the record - one trains may arrive at first, else one facing that way anyway; the throttle's direction-follow and the Facing menu now move the train; the blessed baseline is back, byte for byte, to before 1c855483 |
| **Where** | `AutonomySession.java:1557-1576` (`copyFacing`, now the only copy chooser of `moveOntoFacingCopy`), `:1602-1630` (`moveOntoFacingCopy`), `:1803-1900` (`flipFacing`), `:7150-7166` (`setFacingAndMove`), `:6124-6155` (`facingChoices`), `:5213-5230` (`facingsThatCannotBeHeld`); `AutonomyBuilder.java:727-770` (`placementCopy`); introduced by `1c855483` (GUI-B1) |

**Overlaps GUI2-B2** (a draft heading in the GUI validator's document when this was written: "the Facing menu and the
direction-follow still offer and save a facing only a barred copy holds ... the record and the railway now disagree").
What this adds is the consequence that sets the severity - the train is then dispatched against its decoder direction -
and that the checker written for exactly this state is blind to it.  One finding for the coordinator, filed from two
lanes.

GUI-B1 made "the copy facing X" mean "a copy facing X that trains may arrive at" in three places - the build's
`placementCopy`, the running layout's `copyFacing`, and through it `moveOntoFacingCopy` - and returns nothing (or, in
the build, a copy facing the OTHER way) when no such copy faces X.  It did not change what those facings are offered
and followed from: `facingChoices` (the editor's Facing menu, `flipFacing`, `faceTheWayItCameIn`, and the
`facingsThatCannotBeHeld` checker) is still `facingsFor(tile).values()`, every copy the build made, barred ones included.

That is right for the doors that PLACE a train (the heading there is an inference, and GUI-B1's scenario was one).  It
is wrong for the two doors whose facing is a physical fact:

1. **The throttle.**  `TrainControlUI.followDirectionChanges` -> `flipFacing(name, running)` (Adam: *"a locomotive
   direction command WILL update the direction on the graph if it does not match"*).  At BottomMainA on his railway as
   frozen (`test/layouts/live-snapshot`, arrivals from the east barred - GUI-B1's own premise, asserted by
   `testATrainIsPutOnlyWhereItCanStart`), `facingChoices` is {E, W}, so a train recorded E and reversed on the throttle
   is flipped to W: `setFacing(tile, W)` writes the setup, then `moveOntoFacingCopy` -> `copyFacing(tile, W, running)`
   finds no destination copy facing W and returns null, and `moveOntoFacingCopy` returns without moving
   (`if (onto == null || train == null) return;`).  `flipFacing` still returns the tile, so the log says
   `autosetup.infoFacingFollowedDirection` - followed.  The running train stays on `BottomMainA (eastbound)`, whose
   outgoing edges run east, while its decoder now drives west.  The model has no train direction (the copy IS the
   direction; runs only `switchDirection()` at reversing points, `Layout.java:8402`, `:8772`), so the next hand send or
   autonomy departure locks a path east and the train drives west over track nothing reserved.
   Before `1c855483` the same flip stood it on `BottomMainA (westbound)`: no station, so autonomy would not start it
   (`autolayout.why.startNotStation` says so) - stuck, but truthful about the direction.
2. **The editor's Facing menu** (`AutonomyEditorPanel.buildFacingMenu` -> `setFacingAndMove`), which an operator uses to
   tell the record which way the train really points.  It offers W at BottomMainA (from `facingChoices`), records W and
   moves nothing - the same wrong-way state, reached by correcting the record.

**And nothing reports it.**  The next build (`placementCopy`, GUI-B1) stands a train whose setup says W on the E copy -
the silent turn-round that `facingsThatCannotBeHeld` was written to report (*"So the train quietly turns round ... What
was missing was being told at all"*) - and that checker asks `facingChoices`, which still contains W, so it is silent.
`buildFacingMenu`'s own comment says offering a facing the build cannot hold is "`facingsThatCannotBeHeld` manufactured
on purpose"; since GUI-B1 W is such a facing and the menu offers it.  The next `captureFromLayout` then writes E back
over the operator's W.

GUI-B1's claim `testTheBuildNeverStandsATrainOnACopyItCannotStartFrom` passes on exactly this state (setup W, train on
the E copy) and calls it correct; its class comment accepts "or nowhere" for the Facing door.  Neither claim has the
train's physical heading in view.  Adam's two rulings meet here - *"we shouldn't allow an impossible facing to be
saved"* (said of input facings) and the throttle-follow ruling - and the current code honours neither: the impossible
facing IS saved, and the flip is NOT followed.

**On his railway as frozen** (`test/layouts/live-snapshot/config/autonomy/configuration-Main.json`, read with python):
`1 - Main:20,12` (BottomMainA) is not may-reverse (no `canReverse`, so no turning copy holds W), bars arrivals from the
east (`setup.json` `barredArrivals`: `"5:20,12": "E"`), and has 75 407 DB standing on it recorded `facing: E` - the
train, the square and the recorded facing `flipFacing` needs.  Reversing 75 407 DB on the throttle there while nothing
runs is the whole trigger.

Reading and reasoning; every step is quoted code.  **Verification request (execution, deterministic, fast):** in
`core.testATrainIsPutOnlyWhereItCanStart`'s fixture (live-snapshot, `session()`, `build(session)`,
`session.setRunningLayoutSource(() -> running)`): `running.moveLocomotive(PROBE, <the copy of BottomMainA facing E in
`session.facingsFor(mainA)`, "BottomMainA (eastbound)" in GUI-B1's text>, false)`,
`session.placeLocomotive(mainA, PROBE)`, `session.setFacing(mainA, Side.E)`, then `session.flipFacing(PROBE, running)`.
**Proves:** it returns `mainA`, `session.getFacing(mainA) == W`, and the train still stands on the copy whose facing
(`session.facingsFor(mainA)`) is E.  **Refutes:** the train stands on a W-facing copy, or `flipFacing` returns null and
leaves the facing E.  Run the same on `1c855483^` to see the pre-GUI-B1 answer (the train on `BottomMainA (westbound)`).
The Facing menu half is the same with `session.setFacingAndMove(mainA, Side.W)` in place of the flip.

**Suggested fix (Adam decides which):** where the facing is a physical fact (`flipFacing`, `setFacingAndMove`), let
`moveOntoFacingCopy` fall back to a non-destination copy facing that way when no destination copy does - truthful, and
autonomy already refuses to start from it with a sentence that says why; or refuse the flip/menu choice out loud and
leave the setup alone.  Either way `facingChoices` (menu and checker) should ask the same question as `copyFacing`, so
the checker reports a W record at BottomMainA instead of the build turning the train round in silence.

## B - medium

None found.

## C - low

### TDY2-C1 - Layout.setHomeLocomotive still says Return Home brings a train back "to it or its turning twin", the rule TDY-B1 removed

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `Layout.java:1409-1414` (`setHomeLocomotive`) |

```java
// THE COPY IS THE FACING, and the operator chose this one (...).  A home named here is a home on this copy; Return Home
// brings its locomotive back to it or its turning twin.
p.setHomeFacingFixed(loc != null);
```

Since `ac5fe2f2` the turning twin is exactly what is NOT home (`HomeStaging.atHome` compares `getCopyFacing`, and the
twin faces the other way).  The fix rewrote the same sentence in `Point.homeFacingFixed`'s javadoc, in
`AutonomyBuilder.HOME_FACING_FIXED` and in behaviour.md 6; this one, at the door that sets the flag, was missed.
Stale comment only.  **Suggested fix:** "...back to a copy of the square facing the way this one faces".

### TDY2-C2 - The half-measured notice's javadoc still says the berth rule declines to judge a berth measured on its own square - the premise TDY-B2's fix reverses

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `AutonomySession.java:8878-8879` (javadoc of `stationsWithAHalfMeasuredApproach`) |

"`Layout.whyABerthCannotHoldIt` declines to judge only when NOTHING on the approach but the berth's own square is
measured (PRW-B1)."  Since OB-278 (`5cb7cf01`) it declines only when nothing at all, the berth included, is measured
(`anyMeasured` runs over every span, `Layout.java` "THE BERTH'S OWN SQUARE COUNTS (OB-278)"), and `a67452cd`'s new
comment in the body of the same method says so.  The javadoc now describes the state TDY-B2 was about as exempt.  Stale
comment only.

### TDY2-C3 - Two comments in executePath's failure handler still say unlockPath reads clearedEdges

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `Layout.java:7886-7893`, `:7979-7981` |

"`unlockPath`'s non-atomic branch reads that map to know which edges the tail already gave up" and "`unlockPath`
consults `clearedEdges` for the edges the tail gave up early, so it has to run while that map still has them".  Since
`919e0dc8` (GUI-A1 second pass) `unlockPath` reads `releasedEarly` on both roads and never reads `clearedEdges`.  The
ordering they defend is still right - `releasedEarly` is removed beside `clearedEdges`, after `unlockPath`, at all three
sites (`:8035-8036`, `:8855-8856`, `locDeleted` `:1092-1093`) - so only the map's name is wrong; but these are the
comments a reader follows to decide what may be cleared before the unlock.  **Suggested fix:** name `releasedEarly`
(and say `clearedEdges` is what `getActiveAccs` reads).

### TDY2-C4 - TDY-C2 is fixed for the case it led with; its third case is not, because the editor still decides whether to ask from the setup

| | |
|---|---|
| **Disposition** | Fixed - 1facc0c2 (claim 9c85db2a, red first): homeFacingOf never falls back to the setup for a train the railway has elsewhere; the editor asks exactly when knownHomeFacing has no answer |
| **Where** | `AutonomyEditorPanel.java:5096-5111` (`picked.equals(locomotiveAt(tile))`); `AutonomySession.java:7402-7432` (`homeFacingOf`) |

TDY-C2 named three readings of the setup where the railway knows better.  `919e0dc8` makes `homeFacingOf` read the
running layout first, which settles the first (a train turned here, not yet captured) and the second (the setup has the
train here with no `FACING`).  The third - *"where the train has since driven here but the setup does not know yet, the
operator is asked a question the railway could answer"* - is untouched: the editor asks the facing question when
`!picked.equals(locomotiveAt(tile))`, and `locomotiveAt` is the setup.  And its mirror is new with the fix: where the
setup still has the train here but it has since driven away, nothing is asked, the running-layout loop finds it on no
copy of this square, and the setup's `FACING` - the heading of a train that is no longer here - is saved as the home's
facing.  The door that decides whether to ask and the method that answers when it does not ask now read different
stores, which is the guard/affordance shape.  The disposition says "Fixed" without the residual.

Narrow (it needs the editor open over a railway that has run since the last capture) and nothing is driven wrongly: a
wrong home facing means Return Home brings the train back the other way round.  Reading only.  **Verification
request:** as TDY-C2's own (`testAHomeTakesTheFacingTheTrainHasOnTheRailway`'s fixture), but place the train in the
setup at BottomMainB and on the railway at RampDown, then call the editor door's decision (`locomotiveAt(tile)` equals
the train, so no prompt) and `session.setHome(square, train)`: a `HOME_FACING` written proves it.  **Suggested fix:**
decide whether to ask by the same running-first reading (`facingOf(locomotive, running)`'s square), so the question is
asked exactly when `homeFacingOf` would have no answer from the train standing there.

### TDY2-C5 - AUT-C3's second pass spends the standing square only on a split square, so a built single-copy square whose first hop runs away from it still leaves its own square unspent

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 (claim 8346be65, red first): the fork rule takes the rail arriving at the square the train stands on |
| **Where** | `Layout.java:7430` (`here.getBlock() != null`), `:6985-6992` (`isTheSquareOf`) |

`12ed2faa` guards the AUT-C3 read with `here.getBlock() != null`, because a hand-written graph's places do not end with
the square.  But the build writes `block` only on a square emitted as more than one Point (`AutonomyBuilder.blockFor`:
`nodes.size() > 1 ? tile.toString() : null`), so the guard also excludes every built square that is one Point - where
the arriving rail's last place provably IS the square (`GraphReducer.placesAlong` always ends with
`edge.getEnd().toString()`).  The first hop runs away from such a square when the train has no recorded side at a
square with one neighbour: `walkOneTail`'s fork rule takes the first candidate per neighbour, and
`getNeighborsAndIncoming` lists outgoing rails first - a dead-end terminus is the ordinary case, and it is emitted as
its turning copy alone (`AutonomyBuilder.java:612-628`), so it has no block.  There the square is neither claimed nor
spent and the tail reaches one square further back than the train lies, which is what OB-278 (*"Everywhere"*) removed.

Relative to the first pass (`6b7301fc`) this is a narrowing; relative to `281c79de` it is unchanged, so it is a sibling
the fix did not reach rather than a regression.  Refusing side, narrow (hand-placed, no recorded side, one-neighbour
square).  Reading only.  **Verification request:** in `testATrainCoversTheTrackBehindIt`, a Point with NO block, one
neighbour, an arriving edge with places `[t1, SQ]` spans `[1, 2]` and the reverse edge `[t1, N]`, a two-unit train
standing there with `arrivedFrom` null: `placesCoveredByStandingTrains()` containing `t1` proves it.  **Suggested fix:**
tell a built configuration by something it always has (every edge carrying places that end with its end square) rather
than by the block, which only split squares carry.

### TDY2-C6 - AUT-C1's "this call's own claim only" is `takingPath.remove(loc, path)`, which is equality, and a duplicate dispatch on the same route has an equal path

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 (claim 8346be65, red first), with AUT2-C1 |
| **Where** | `Layout.java:8203` (`executePathInternal`), `Edge.java:255-263` (`equals` by name) |

`5e2fbda5` changed the refused-dispatch clean-up from `takingPath.remove(loc)` to `takingPath.remove(loc, path)` so that
a dispatch refused by the new in-monitor `isAlreadyUnderway` check removes only a claim that is its own.
`ConcurrentHashMap.remove(key, value)` compares with `equals`, `List.equals` compares elements, and `Edge.equals`
compares names - so when the refused dispatch asked for the SAME route as the one that won (a double click, or a hand
send racing autonomy to the same square: the likeliest duplicate there is), its path equals the winner's and the
winner's claim is removed, exactly as before the fix.  The window is short - between the winner leaving
`configureAndLockPath`'s monitor and its own `takingPath.remove(loc)` after `activeLocomotives.put` - so the cost is the
transient undercount AUT-C1 described (a second driver needs a third dispatch to win the monitor inside that window),
but the comment's promise holds only for differing routes.  (Before `5e2fbda5` a same-route duplicate was refused by `isPathClear` and cleaned up the same way, so for that
case the fix changes nothing.)  The claim `testTheLockRefusesALocomotiveAlreadyClaimingARoute` asserts the refusal only,
calling `configureAndLockPath` directly, and on a different route (`DSP_a -> DSP_c`); the clean-up half has no claim.
Reading only.  **Verification request:** drive `executePath` twice for one train on the same edges from two threads, or
simpler, call the clean-up's condition directly: after the first `configureAndLockPath(route, train)` succeeds, run the
refused branch's `takingPath.remove(train, <a new list with the same edges>)` by reflection and read `takingPath` - the
first dispatch's claim gone proves it.  **Suggested fix:** `takingPath.remove(loc, path)` only when `takingPath.get(loc) == path` (identity), or
return a flag from `configureAndLockPath` saying whether it took the claim.

## D - not defects

### TDY2-D1 - TDY-B1 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`ac5fe2f2`, claim `8873b0a4`).  `HomeStaging.atHome` now compares

  `home.getCopyFacing()` with `where.getCopyFacing()`; the build writes `COPY_FACING = facingOf(node)` on every copy of a
  split square (`AutonomyBuilder.java:1156`), so a turning copy carries its own (the arrival side) and its plain twin the
  onward side; `parseAuto` reads `copyFacing` onto the same Point (`Layout.java:11453`).  Both of the reviewer's cases
  walk right: the arrival-W turning copy (faces W) is not home for a home held on the arrival-W plain copy (faces E), and
  the converse - a train on the arrival-E turning copy, facing E - now is.  The A* goal (`misplaced` -> `atHome`) can no
  longer end on the twin.  Every "is it home" question in the class goes through `atHome` (lines 257, 487, 603, 810, 948,
  980, 989, 1015, 2032, 2645), and the pre-check's copies through `homeCopiesOf`, which filters by the same field (D3).
  No reader of `copyArrival` is left in src or test (`git grep`); a configuration written with it today (the baseline
  never carried it) parses with `copyFacing` null, which makes the home the square - the pre-OB-282 rule, not a crash.
  **The claim** `testATrainTurnedRoundOnItsHomeIsNotHome` is red on `8873b0a4` for the right reason: the plain copy and
  its twin had equal `copyArrival`, so `triage` answered `ALREADY_HOME`, which is the assertion.  It asserts only
  "not `ALREADY_HOME`", so an `atHome` that answered false everywhere would also pass it - but
  `testItComesHomeOnTheCopyItWasHomedOn` in the same class needs the train counted home at the end, so the pair is not
  vacuous.  The one missed sentence is TDY2-C1.

### TDY2-D2 - TDY-B2 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`a67452cd`).  The loop now walks the leg's path plus `square`, the leg's end.  The claim

  `testTheBerthsOwnSquareCountsInTheHalfMeasuredNotice` has no separate red commit, so it was read against `a67452cd^`:
  `openBerthBehindASwitch` gives two legs into 5,1 (from 1,1 over 2,1 / 3,1 / 4,1, and from 3,0 over 3,1 / 4,1); with
  only the berth measured the old loop saw nothing measured on either (first assertion red), and with the approach
  measured and the berth cleared it counted nothing unmeasured (second assertion red) - `session.setTileLength(tile, 0)`
  removes the answer (`AutonomyCompanionStore.setTileLength`), so the berth is unanswered there, as the claim needs.
  Both hold on the new code.  The two older claims that gained `setTileLength(key(5, 1), 2)` needed it: their
  `assertFalse` halves would otherwise see the unmeasured berth - the fixture change keeps their subject (route tile,
  answered zero) the only variable.  The notice and `whyABerthCannotHoldIt` now read the same places (the approach's
  path plus its end); they still count "unmeasured" differently - the rule over the places its walk claims, the notice
  over the whole leg - which changes a number in a warning, not whether one is given.  Stale javadoc: TDY2-C2.

### TDY2-D3 - TDY-C1 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`ce8983ce`, claim `273727eb`).  `canGetHome` and `whyNotHome` iterate `homeCopiesOf`,

  which keeps every copy for a home not held to a facing and otherwise the copies whose `getCopyFacing` equals the
  home's - `atHome`'s own test - and is never empty (the home is its own copy).  The claim excludes the train from the
  westbound copy only; on `273727eb` the pre-check passed on the eastbound copy and the search could only exhaust, so
  `IMPOSSIBLE` is the red assertion.  `canRestOnSquare` (behind `canBeHome`, the editor's rest warning) still asks every
  copy; that differs from the goal only where copies of one square differ in exclusions or length, which the build
  does not produce (both are square properties copied to every copy) - the claim reaches it only by editing one copy's
  `excludedLocs` directly.  Not raised.

### TDY2-D11 - TDY-C2's leading case verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`919e0dc8`, claim `cdedb3fc`).  `homeFacingOf` now looks for the train

  on the running layout first, on a copy of this square (`getStationIndex().squareOf`), and takes that copy's facing
  from `facingsFor`; the setup's `FACING` is the fallback.  The claim stands the train in the setup facing the plain
  copy's way and on the railway on the turning twin, and reads `HOME_FACING`: on `cdedb3fc` the setup's facing was
  written (red), now the twin's.  The build honours the value only where `homeCopy` finds an arrival-allowed copy facing
  that way (else the home stays the square), and capture writes it back from the home copy's own facing, so a railway
  facing cannot become a home the build puts somewhere else.  The residual cases are TDY2-C4.

### TDY2-D4 - TDY-C3 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`59fdb67d`).  All eight bundles changed `promptClickTailCrossed`, `headingTailCrossed`

  and `hintTailCrossed`; checked with python over every `messages*.properties` at `08a47bdd`: pure ASCII, identical key
  sets, identical `{n}` placeholder sets per key.

### TDY2-D5 - TDY-C4 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`59fdb67d`).  The claim now cuts from the copy facing W where BottomSecondary has

  copies, and from its one copy where it has one (`facingsFor` lists a one-copy square's single node, since
  `facingByName` keeps every node with an arrival).  Where there are two copies the premise (cut going west) is now
  held by construction; where there is one, its facing is the only heading a train there can have.  Selected rather
  than asserted, which is what TDY-C4 asked for in effect.

### TDY2-D6 - `12ed2faa` (AUT-C3 second pass) verified for AUT-C3's own case

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`isTheSquareOf` compares the tile part

  of the place and of the block: built places end with `TileKey.toString()` (`page:x,y`), blocks are `page:x,y` or
  `page:x,y/<road>` (`AutonomyBuilder.blockFor`), overpass places `tile/route` - `tileOf` cuts at the first `/` after the
  last `:`, so a page name with a slash or colon still resolves.  The AUT-C3 claim's fixture now names its block as the
  build does, so it still reaches the branch.  The residual is TDY2-C5.

### TDY2-D7 - `b4b061e0` is complete

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Every test that seeds `clearedEdges` by reflection to stand for an early

  release now seeds `releasedEarly` too (`testAnUnlockGivesBackOnlyWhatItHolds:100-101`, `testAutoLayout:2303/2335`,
  `testAutonomyPathValidation:756/769`); `testAnAtomicRunGivesBackEverythingItHeld` seeds `clearedEdges` alone on
  purpose (passed under atomic mode, not released).  In src `releasedEarly` is written at one place, the non-atomic
  early release (`Layout.java:8551`), inside the block that calls `setUnoccupied`; created at dispatch (`:8216`); read
  only by `unlockPath` (all three reads switched); removed beside `clearedEdges` after `unlockPath` at the failure
  handler, the ordinary end and `locDeleted`.  `getActiveAccs` still reads `clearedEdges` as "passed", which is right.
  The comments that still name the old map are TDY2-C3.

### TDY2-D8 - Test-run isolation leaves the application unchanged when the two properties are absent

| | |
|---|---|
| **Disposition** | Closed - checked clean |


  `Util.dataPath` returns its argument and `Util.preferencesFor` returns `Preferences.userNodeForPackage(owner)` when
  `traincontrol.dataDir` / `traincontrol.preferences` are unset or blank; every converted call site in `6ce9d625` was a
  like-for-like substitution (`DATA_FILE_NAME`, `prefix + DATA_FILE_NAME`, `new File(BACKUP_FOLDER)`,
  `userNodeForPackage(X.class)`), and no direct `userNodeForPackage` / `userRoot` is left in src outside `Util`
  (`PositionAwareJFrame` reads `TrainControlUI.getPrefs()`).  Only `docs/tools/one.sh` and `battery.sh` set the
  properties.  The isolated node copies keys only, not child nodes - no src code uses child nodes of these packages.

### TDY2-D9 - AUT-B1's place rule refuses nothing a clear road should pass

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`anotherTailOn` compares place strings,

  which are a tile or tile/route (overpass), so two levels of a bridge stay apart and an at-grade crossing or a switch
  is one place, as the lock-edge rule already treated it.  The places an edge carries exclude its start and include its
  end, so the new refusals beyond the two old questions are (a) the rail into another copy of a square a tail lies on -
  the defect - and (b) a rail into a square a standing train occupies, which occupancy already refused.  The mover's own
  tail never refuses it, in all three sites; `HomeStaging` asks it in both tail checks with the same exclusion.

### TDY2-D10 - `releasedEarly` and the careful road

| | |
|---|---|
| **Disposition** | Closed - checked clean |

With early releases recorded only where they happen, both

  directions of a mid-run Atomic Routes change end in the careful road iff something was released, and an atomic run
  that released nothing takes the atomic road whatever its tail passed - the case `testAnAtomicRunGivesBackEverythingItHeld`
  pins.

## What this pass did not cover

- **Nothing was executed.**  TDY2-A1's probe is deterministic, fast and on a frozen railway; it is the one to run first.
- **The other lanes' round-1 fixes** in `e2223851` (REG-B2, REG-B3, GUI-C2, GUI-C5, GUI-C7, GUI-C8, REG-C3), `55959c9c`
  (GUI-B2), `3492e38c` (AUT-C4), `ff129a4d` (GUI-C1 translations - only the bundle-wide ASCII/key/placeholder check was
  run), `fd6341dd` and `4fb36b4b` (comments, user guide, changelog, behaviour.md) were not reviewed; their own lanes'
  validators have them.  AUT-C1 was read only for the claim clean-up (TDY2-C6).
- **`TailCrossedPrompt`** beyond the wording fix: TDY's open items (portals, balloons, a junction whose rails share no
  square before the standing square) were not traced.
- **The planner's "first train found" limit**: `passesTheTailsOfTrainsThatHaveNotMoved` judges one tail per edge (the
  map holds one locomotive per edge, and `anotherTailOn` returns the first place's), so an edge two standing trains'
  tails both lie on is passed once the first has moved in the plan.  Pre-existing in shape, needs two tails on one
  rail, not raised.
- **GUI2-B1's branch** (`placementCopy` with no facing returns copy 0) was left to that lane.
