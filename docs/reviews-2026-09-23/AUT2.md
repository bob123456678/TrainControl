# Autonomy validator - round 1 fixes to AUT, and the autonomy diff since 281c79de

**Status:** open

**Prefix:** `AUT2`

**Reviewed:** branch `autonomy-diagram-r0` at `08a47bdd`, 2026-09-23.  Baseline: the AUT lane's review at `281c79de`; two-dot diffs `281c79de..08a47bdd`, and each fix commit against its parent (`git show <hash>^:path`).

**Method:** a reading pass; nothing executed (Rule 1).  Read every commit in `281c79de..08a47bdd` that touches `automation`, `automationui` or the placement doors (`ac5fe2f2`, `8873b0a4`, `0be2bbe4`, `919e0dc8`, `b4b061e0`, `1c855483`, `2e565b5e`, `5e2fbda5`, `0d060ffb`, `6b7301fc`, `c7308e7c`, `12ed2faa`, `3492e38c`, `4ed14f85`, `a67452cd`, `ce8983ce`, `e2223851`, `fd6341dd`) as a diff, then the code around each change as it stands at HEAD: `isPathClear`'s covered-track sweep, `anotherTailOn`/`tailLiesOn`, `walkStandingTrains`/`walkOneTail` whole, `isTheSquareOf`/`tileOf` against `AutonomyBuilder.blockFor` and `GraphReducer.placesAlong`/`locationsOf`, `configureAndLockPath` whole and its one production caller `executePathInternal` (both refusal paths, the success path, the exception handler in `executePath`), `unlockPath` whole, every read and write of `clearedEdges`/`releasedEarly`/`takingPath`, both planner tail checks and `atHome`/`homeCopiesOf`, `placementCopy`/`homeCopy`/`facingOf`, `placeableFacingsFor`/`copyFacing`/`moveOntoFacingCopy`/`facingAfterAPaste`, and `captureFromLayout`'s facing write.  Each claim test was read against the pre-fix code to decide whether it was red for the right reason.  Facts about the railway were read with Python (reading only, from stdin) from `test/baseline` (the blessed configuration before and after `1c855483`, and its input companion and setup), `test/layouts/live-snapshot` and `test/operator_layout` (companion placements, barred arrivals, tile types per page, page ids).  `findings.tsv` was grepped for the planner tail checks, the AUT rows, and the topics below; nothing here re-raises a closed finding.

## A - high

### AUT2-A1 - GUI-B1's repair makes the build stand a placed train on a copy facing another way than its recorded facing: the re-blessed baseline shows two trains turned round, silently, and the next capture writes the new facing over the operator's

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 (claims 8346be65, red first), with TDY2-A1.  The doors' one-placeable-copy rule (facingAfterAPaste fed placeableFacingsFor) is kept - it is OB-270's 'no impossible facing is saved' - and recorded for Adam |
| **Where** | `AutonomyBuilder.java:759-770` (`placementCopy`'s two new fallbacks, `1c855483`); `AutonomySession.java:7021-7023` (`facingAfterAPaste`'s one-copy branch, now fed `placeableFacingsFor` at `GraphLocAssign.java:238`, `:287` and `AutonomyEditorPanel.java:5441`); `test/baseline/configuration.json` (re-blessed in `1c855483`); `test/core/testATrainIsPutOnlyWhereItCanStart.java:80-104` |

**What changed.**  Before `1c855483`, `placementCopy` put a placed train on the copy facing its recorded `facing` (the plain copy before the turning one), and fell back to copy 0 only when NO copy faced that way.  GUI-B1 added `arrivalAllowed` to both facing loops, and then two new fallbacks:

```java
// AND WHERE NO COPY TRAINS MAY ARRIVE AT FACES THAT WAY, one they may - the facing is one no train can stand in.
for (...) if (!nodes.get(copy).reverse && arrivalAllowed(nodes.get(copy))) return copy;
for (...) if (arrivalAllowed(nodes.get(copy))) return copy;
```

These pick a copy by whether trains may arrive at it, with no regard to its facing.  So where the recorded facing is one that only a barred copy holds, the train is now emitted on a copy that faces a different way.  The premise in the comment, "the facing is one no train can stand in", is false.  A barred arrival bars STOPPING there, not standing there.  The builder says so itself where it emits the flag: *"The copy still exists and still carries traffic; it is simply not somewhere a train can be sent."*  A train that stood there before the arrival was barred, one driven in by hand, and one placed by hand all face that way.

**The copy IS the direction** (behaviour.md 3), and nothing else holds it.  The runtime never commands an absolute direction.  `Layout` only toggles `switchDirection()` at a reversal (`Layout.java:8402`, `:8772`).  A train stood on the copy facing the other way therefore sets off in its real direction when it is next dispatched, and the route locked is the other one.

**On the blessed railway.**  `test/baseline/layout/config/autonomy/configuration-Main.json` records EN57-203 at `1 - Main:1,10` facing **N**, and 2-8-4 3505 SP at `1 - Main:4,4` facing **S**.  The setup there bars arrivals from E at `5:1,10` and from N at `5:4,4`.  The blessed `test/baseline/configuration.json`:

| | at `1c855483^` | at `1c855483` (and HEAD) |
|---|---|---|
| the train EN57-203 | `TopMainR1Inter (westbound)`, copyFacing **N**, station false | `TopMainR1Inter (southbound)`, copyFacing **E**, station true |
| the train 2-8-4 3505 SP | `TopMainR1 (southbound)`, copyFacing **S**, station false | `TopMainR1 (northbound)`, copyFacing **N**, station true |

The build turned both trains round.  `TopMainR1Inter` is a curve, so N and E on it are the two directions of travel.  The commit message calls this *"now blessed on station copies"*, and the facings are not mentioned.

**Then the record is lost.**  `captureFromLayout` writes `FACING` from the copy the train stands on (`AutonomySession.java:4851-4860`: *"learned rather than asked for"*).  It runs at the exit save, when an editor is opened or closed, and at a reload (`TrainControlUI.java:2566`, `:3094`, `AutonomyViewerPanel.java:765`).  So the operator's S becomes N with nothing said.  The old outcome had a remedy: the loader logged `warnLocomotivePlacedOnNonStation`, and `explainCannotStart` said *"It is standing on {0}, which is not a station"*.  Now there is no line at all.

**The same substitution at the doors.**  `facingAfterAPaste` returns the single entry when `held.size() == 1`.  Its reasoning is *"ONE COPY MEANS ONE ANSWER ... everywhere else it is the only heading a train can have on that square"*, which was true while `held` was `facingsFor` (every copy the track has).  Fed `placeableFacingsFor`, a square with one arrival-allowed copy answers that copy's facing whatever the train's heading.  At BottomMainA (E barred on the live snapshot), the editor's Place and the Place Locomotive dialog now record a W-heading train as facing E, and the dialog stands it on the eastbound copy, without asking.  The paste has done the same since `d30e3733` (OB-270).  This is the lifted-rule shape: the one-copy rule lost the precondition that made it true.

**Adam's rulings.**  OB-270: *"no train should inadvertently change direction when pasted."*  The build changes a train's direction with no gesture at all.  *"we shouldn't allow an impossible facing to be saved"* (OB-282, said of homes) argues for refusing to save, or asking, and not for saving a different facing.

**The claim cannot see it.**  `testTheBuildNeverStandsATrainOnACopyItCannotStartFrom` sets the facing to W at BottomMainA and asserts only `standing.isDestination()`.  On HEAD that train stands on `BottomMainA (eastbound)`, facing E, and the test passes.  It checks the control and not the variable.

**Reach today.**  On the frozen live snapshot none of the four placed trains is in this state: 75 407 DB is at BottomMainA facing E, 2-8-4 is at `0,11` facing E, EN57-947 is at `13,9` facing W, and each has an arrival-allowed copy facing that way.  It bites the first time Adam bars an arrival at a square where a train stands facing that way, or places one by hand facing a barred way.  The baseline shows his railway has been in that state.  A, because the operator's record is silently replaced and the model then disagrees with the physical train about which way it will move.

**Verification request.**  (1) *No execution needed for the substitution:* `git show 1c855483^:test/baseline/configuration.json` against `1c855483`, and the companion file above; the table is read from them.  (2) *Execution, for the overwrite:* in `core.testATrainIsPutOnlyWhereItCanStart.testTheBuildNeverStandsATrainOnACopyItCannotStartFrom`, after `standing` is found, assert `standing.getCopyFacing()` equals `"W"`.  **Predicted on HEAD:** `"E"`, which proves it.  Then call `session.captureFromLayout(model.getAutoLayout().toJSON())` and read `session.getFacing(mainA)`.  **Predicted:** `E`, which proves the recorded W is overwritten.  **Refutes it:** the train is not on an E-facing copy.  (3) *The doors:* `AutonomySession.facingAfterAPaste(session.placeableFacingsFor(mainA, running), Side.W, null)` should give `E`, against `facingAfterAPaste(session.facingsFor(mainA), Side.W, null)`, which gives `W`.

**Suggested fix (Adam decides the rule).**  Never choose a copy that faces another way than the record.  Where only a barred copy holds the recorded facing, either keep the pre-`1c855483` behaviour (stand it there; the "not a station" sentence is the remedy), or leave the train unplaced with a notice naming the square and the facing.  At the doors, ask (FacingPrompt) or refuse, rather than record the one placeable facing.  The claim should assert the facing.

## B - medium

None.

## C - low

### AUT2-C1 - AUT-C1's "removes only its own claim" compares routes by value, so the likeliest double dispatch (the same route twice) still deletes the winner's claim; and the caller's removal can now only ever remove someone else's

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 (claim 8346be65, red first): the caller's removal is gone |
| **Where** | `Layout.java:8203` (`this.takingPath.remove(loc, path)`), `Layout.java:3686-3692` (the new comment) |

`takingPath` is a `ConcurrentHashMap<Locomotive, List<Edge>>`.  `remove(key, value)` removes when `value.equals(current)`, and `List.equals` compares elements with `Edge.equals`, which is by name (`Edge.java:255-263`).  The reviewer asked for *"identity of the list"*; the code compares by value.

**The race still loses the claim when both dispatches carry the same route.**  Thread 1 and thread 2 both pass the outer `isAlreadyUnderway` check (`:8132`) while another train holds the monitor.  Thread 1 claims and locks.  Thread 2 is refused by the new inner check (`:3694`) and returns false, and `executePathInternal` then runs `takingPath.remove(loc, path)`.  Its `path` equals thread 1's, so thread 1's claim is removed while it is still validating.  That leaves exactly AUT-C1's second bullet: the train in neither map, `getActiveAccs` omitting its thrown turnouts, the tail walk picking an arbitrary reservation, and the cap undercounting.  Two dispatches of one train to one destination (the Auto tab twice, or the tab and the right-click menu) is the likeliest double dispatch, and the only one the fix does not cover.  The fix does close the different-route case the reviewer described.

**And the line has no legitimate case left.**  Every false return from `configureAndLockPath` has either taken nothing (the underway refusal and the `isPathClear` refusal) or has already removed its own claim before returning (`configureFailed` at `:3798`, the validation failure at `:3817`).  The inner check means no other dispatch can hold a claim for that locomotive meanwhile.  So the caller's removal is a no-op, or it removes another dispatch's claim.  The new comment at `:3691` (*"the caller's clean-up ... removes only a claim that is this call's own"*) states the intent, not what the code does.  The claim `testTheLockRefusesALocomotiveAlreadyClaimingARoute` calls `configureAndLockPath` directly and never reaches `:8203`, so this half has no claim.

Rests on reading plus the documented `ConcurrentHashMap.remove(Object, Object)` contract.  **Verification request (needs execution, deterministic):** a fixture like `core.testATrainIsDispatchedOnce`'s, in simulation.  The test thread takes `synchronized (layout)` and also holds the `activeLocomotives` monitor (the field, via reflection).  Start two threads, each calling `layout.executePath(route, train, 50, null)` with **equal** routes (two `new ArrayList<>(theRoute(layout))`).  Wait until both are `BLOCKED`, then release the layout monitor only.  One thread claims and locks, then blocks on `activeLocomotives` before `activeLocomotives.put`.  The other is refused and returns; join it.  **Proves it:** `layout.isAlreadyUnderway(train)` is `false` at that moment.  **Control:** with **different** routes out of the start (`DSP_a -> DSP_b` and `DSP_a -> DSP_c`) it is `true`.  Release the `activeLocomotives` monitor to finish.  **Suggested fix:** delete the removal at `:8203`, since every path that claims already releases its own claim, and correct the comment.

### AUT2-C2 - the planner blames the first train it finds on an edge and passes the edge if that train has moved, so an unmoved train's tail further along the same edge is not seen; AUT-B1's new clause inherits the shape

| | |
|---|---|
| **Disposition** | Open - for the next round: the planner and the runtime both settle on the first train found on an edge, so the fix is a shared rule |
| **Where** | `HomeStaging.java:1586-1653` (`passesTheTailsOfTrainsThatHaveNotMoved`), `Layout.java:7691-7702` (`anotherTailOn`) |

The method settles on ONE train, `lyingAcross`, from three sources in turn: the direct cover (`coveredAtStart.get(edge)`), the first symmetric lock partner whose tail lies on the edge (`break`), and, new with AUT-B1, `Layout.anotherTailOn(edge, mover, placesCoveredAtStart)`.  `anotherTailOn` returns the FIRST non-mover claimant in the edge's place order.  The method then asks only whether that one train has moved (`:1644-1650`): *"Still where it was?  Its tail is where it was too."*  If it has moved, the edge passes.

So an edge with two start tails on it passes as soon as the first-found train has moved, even while the second is still standing.  Take a ladder, with T1's tail on one switch tile of the mover's route and T2's on another; the plan has moved T1 away and T2 is not being homed.  The runtime (`isPathClear`, which walks the tails as they are now) refuses the leg.  That is OB-073, a plan that stops half way, which is the drift this class promises not to have (*"the two rules have to agree or the planner offers what the runtime refuses"*).  `passesTheTailsOfTrainsItHasMoved` does not have this shape, because it loops per train.

The direct and lock-partner clauses had this shape before this round.  The AUT-B1 clause adds the by-place case to it rather than creating it, hence C.  It is plausible wherever two trains' tails reach one throat.  Rests on reading.  **Verification request (needs execution):** a hand-built graph in the style of `core.testHomeStaging.testASensorHeldByAStandingTrainsTailIsNotBlocked`.  Edge `E` has places `[k1, k2]`.  T1's start tail claims `k1` (through an edge that is E's symmetric lock partner, or through a sibling copy).  T2 is unhomed, and its tail claims `k2`.  T1 is homed elsewhere; the mover M is homed past `E`.  Call `passesTheTailsOfTrainsThatHaveNotMoved(E, M, state)` by reflection, with `state` = start minus T1.  **Proves it:** `true`.  **Control:** with T1 still in `state`, `false`.  **Suggested fix:** collect every non-mover claimant from all three sources, and pass only if every one of them has moved.

### AUT2-C3 - comments the round-1 fixes made false: four sites still say `unlockPath` reads `clearedEdges`, a claim's MUTATION note names a mutation that no longer fails it, and the walk's "both directions" loop is still described as the reverse-rail cover

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `Layout.java:7886-7892`, `:7979-7981`, `:8488-8492`; `test/core/testAutoLayout.java:2252`, `:2267`, `:2402`; `Layout.java:7467-7488`, `:2507-2508` |

- **GUI-A1 (`919e0dc8`).**  `unlockPath` now reads `releasedEarly` on both of its roads, and `clearedEdges` is read only by `getActiveAccs`.  The failure handler's two comments (*"`unlockPath`'s non-atomic branch reads that map"*, *"`unlockPath` consults `clearedEdges` for the edges the tail gave up early, so it has to run while that map still has them"*) and the release loop's (*"the non-atomic branch of `unlockPath` - the one that reads this map"*) are false.  The code itself is right: both endings remove `releasedEarly` after the unlock.  The ordering rule VD10-A1 protects is now about `releasedEarly`, and the comments send the next reader to the wrong map.
- **The claim's note.**  `testAFailureDoesNotReleaseAnEdgeTheTailAlreadyGaveUp` says *"MUTATION: moving `clearedEdges.remove(loc)` back above the release fails this."*  It no longer does: the test seeds `releasedEarly` (`:2335`) and the unlock reads only that.  The mutation that fails it now is moving `releasedEarly.remove(loc)`.  Its javadoc (`:2252`) and its failure message (`:2402`) name `clearedEdges` too.  **Verification (execution):** apply the stated mutation.  Predicted: the test stays green.
- **AUT-B1 (`ac5fe2f2`).**  The reviewer's suggested fix included *"drop or re-key the identity loop at 7347"*.  The loop is still there at `:7467-7488`, headed *"BOTH DIRECTIONS OF THE SAME RAIL (VAL8-A1, REG7-B3)"*, and on a built graph it matches nothing: the two directions run between different copies.  `:2507` still says *"The covered set has known this since VAL8-A1 / REG7-B3."*  On a built graph the reverse rail is now refused only by `anotherTailOn`, by place.  The disposition does not mention the loop.  Harmless code; a false account of where the protection lives.

## D - not defects (checked and sound)

### AUT2-D1 - AUT-B1 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`isPathClear` asks `anotherTailOn(e, loc, coveredPlaces)` after the direct and lock-partner tests (`Layout.java:2779`).  The claim `testATailBlocksEveryCopyOfTheRailItLiesOn` is red on `ac5fe2f2^` for the right reason.  The standing train's walk claims `P` (first hop `leadIn` from the end, then `intoPlain` from the end), `intoTurning` is neither in `coveredTrack` nor a lock partner, nothing else refuses a one-unit mover into a two-unit edge, so `assertFalse(isPathClear([intoTurning]))` fails.  Its control (`intoPlain`) passes both before and after.  Both planner checks ask the same question (`HomeStaging.java:1550`, `:1640`), though `testHomeStaging`'s guard for them is source-text only.  **What it refuses that it should not:**

  - It is keyed purely by place, never by lock edge, so the FR-001 one-directional restriction partners (RGD-B2) cannot leak through it.
  - Its new refusals beyond the old two are (a) sibling copies of one reduced edge (the target); (b) the reverse run, which `deriveLocks` skips and the identity loop never caught on a built graph (correct: the same metal); and (c) edges that share only their END square with a claimed place, which `deriveLocks` never compares because `locationsOf` runs over path steps only.  (c) is the same metal everywhere except a `FEEDBACK_DOUBLE_CURVE`.  There `placesAlong` writes the end place as the bare tile, so a train on one road refuses arrivals on the other, which never meets it (AUR-B1's rule, undone at the end place).  The two roads share one s88, so the live sensor refuses the same arrivals whenever the train or its tail is on the contact.  Adam's three `s88doppelbogen` are all on excluded pages (ids 2, 3, 4; the active pages are 5 and 1).  Recorded, not raised.
  - The self-exemption (`on.equals(mover)`) matches `isPathClear`'s.

### AUT2-D2 - AUT-B2 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`atHome` compares `getCopyFacing` (`HomeStaging.java:2550`).  The build writes `facingOf(node)` as `copyFacing` (`AutonomyBuilder.java:1156`), where a turning copy faces its arrival.  So `X (westbound)` (W) and `X (eastbound, reverse)` (W) are both home, and `X (westbound, reverse)` (E) is not: both of the reviewer's probes now come out as the finding predicted they should.  The idle drain's re-standing between same-facing copies can no longer change the answer.  Sibling `homeCopiesOf` (TDY-C1) and the IMPOSSIBLE pre-check use the same comparison.  `copyArrival` is read nowhere now.  It existed only between `74e2d8a1` and `ac5fe2f2` on one day and was never released, and the resume door rebuilds the configuration (`AutonomyViewerPanel.java:815`), so no stale key survives.  The claim `testATrainTurnedRoundOnItsHomeIsNotHome` is red on `ac5fe2f2^` because `atHome` answered ALREADY_HOME, and the test's old `arrival()` helper, which folded the twin, is gone.  One omission: the reviewer asked that Adam confirm the plain copy of one arrival and the turning copy of the other are "the same facing" for a home.  behaviour.md 6 now states it as the rule, and no question was filed.  It follows naturally from *"it should accomplish the facing"*.

### AUT2-D3 - AUT-C1's inner check verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

It sits inside `synchronized (this)` before `isPathClear` and the claim, and returns before anything is taken, so nothing is left taken or un-taken by the early return itself.  `configureAndLockPath` has one production caller (`executePathInternal`, `:8189`).  The new refusal is reachable only in the race, because autonomy, timetable and Return Home dispatch one train sequentially.  The post-claim removals inside `configureAndLockPath` (`:3785`, `:3798`, `:3817`) are now provably this call's own claim.  The test files that lock repeatedly use a fresh locomotive per call (`testAutonomyPathValidation.dummyLoc`) or unlock in between.  The claim is red on `5e2fbda5^` for the right reason (the second, disjoint route locked).  See AUT2-C1 for the half that is not done.

### AUT2-D4 - AUT-C2's disposition is truthful

| | |
|---|---|
| **Disposition** | Closed - checked clean |

It is recorded as *"Open - Adam's decision: whether the entry guard should look at the routes commanding its signal before throwing it red"*, in both AUT.md and `findings.tsv`.  `throwEntryGuard` is unchanged since `281c79de`.

### AUT2-D5 - AUT-C3 verified, second pass included

| | |
|---|---|
| **Disposition** | Closed - checked clean |


  - The first pass read the last place of an arriving rail as the standing square.  That is true of every built edge, because `placesAlong` appends `edge.getEnd().toString()`, and false of hand-written ones, which is what `12ed2faa` guards with `here.getBlock() != null` and `isTheSquareOf`.
  - **Every kind of square the build emits.**  A Point is a `FEEDBACK`, `FEEDBACK_CURVE` or `FEEDBACK_DOUBLE_CURVE` tile (`isFeedback`); an overpass never is.  The end place is always the bare `TileKey` (`page:x,y`).  `blockFor` gives a split square `tile.toString()`, and a double curve's road `tile + "/" + firstSide`.  `tileOf` strips from the first `/` after the last `:`, so both compare equal; a page name holding `/` or `:` is handled by the last-colon anchor.
  - The loop only takes rails whose end is this very copy, so a double curve's other road cannot be spent.
  - `blockFor` returns null (and the fix is skipped) only on an unsplit square, or on a double-curve road with one copy.  Neither has an "other arrival's copy" to be re-stood on.
  - `remaining <= 0` breaks before the outgoing rail is covered, which is right for a train that fits its square.
  - The claim fixture's block rename (`"X" + tag`) keeps it red on `6b7301fc^` for the right reason (the walk claims `w1`), and green after.  The control is unaffected.

### AUT2-D6 - AUT-C4 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

The fold skips `isFeedback()` squares, and the javadoc now says the sums read a route tile.  The claim `4ed14f85` is red before.  A sibling was noted: "plain track first" also admits a crossing or double-curve tile, whose length another road shares.  It is theoretical: the reviewer checked that all five of Adam's route tiles sit between straights.

### AUT2-D7 - GUI-A1's `releasedEarly` mirrors `clearedEdges` everywhere it should

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Puts: `executePathInternal`'s registration (`:8215`/`:8216`).  Removes: `locDeleted` (`:1092`/`:1093`), the failure handler (`:8035`/`:8036`) and the ordinary ending (`:8855`/`:8856`); on both endings the removal follows `unlockPath`, as VD10-A1 requires.  `setUnoccupied` is called in only one early-release site (`:8545`), and it records into `releasedEarly` under the same proof.  The atomic branch of the release loop adds to `clearedEdges` only, which is the point of the second pass.  `unlockPath` is the only reader.  `heldItAll` sends a run that switched non-atomic to atomic down the careful road, and a whole-atomic run down the atomic road.  No other `clearedEdges.clear()` exists.  Only the comments lag (AUT2-C3).

### AUT2-D8 - the rest of the autonomy diff

| | |
|---|---|
| **Disposition** | Closed - checked clean |


  - TDY-B2 (`a67452cd`) now counts the berth square in the half-measured notice.  That matches `whyABerthCannotHoldIt`, and route tiles are still skipped.
  - TDY-C1 (`ce8983ce`): `homeCopiesOf` is never empty (the home copy faces its own facing).
  - TDY-C2 (`919e0dc8`) reads the facing from the running copy first, as `facingOf` does.
  - `e2223851`/`fd6341dd` in these packages are a visibility change (`withoutArrivalSuffix`) and comments.
  - `placeableFacingsFor`/`copyFacing` return null or the unfiltered map when there is no running layout.  When `copyFacing` returns null, `moveOntoFacingCopy` moves nothing, which is safe.

## What this pass did not cover

- **Nothing was run.**  AUT2-A1's substitution is established from committed files; its overwrite, and AUT2-C1, C2 and C3's mutation note, need the coordinator's execution.
- The GUI side of `1c855483` beyond the facing: `GraphLocAssign`'s re-pointed `edit.p` and its tail/`wasOn` handling, and FacingPrompt.  The GUI lane's validator owns them; AUT2-A1's door half touches them only through `facingAfterAPaste`.
- `TrainControlUI` (259 lines changed), the translations, `MarklinControlStation`, `RouteEditorFrame`, and the test-isolation commits (`6ce9d625`, `ca7a423a`), except where a finding led there.
- Whether the runtime has any stall or timeout that would stop a train driven the wrong way (AUT2-A1) before it reaches other track; not traced.
