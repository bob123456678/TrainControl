# Today's work (2026-09-23) on autonomy-diagram-r0 - round-3 validation of TDY2's fixes and the round-2 diff

**Status:** open

**Prefix:** `TDY3`

**Reviewed:** branch `autonomy-diagram-r0` at `2f4448b6`, 2026-09-23.  Baseline: TDY2 (written at `08a47bdd`) and the range `08a47bdd..2f4448b6` - `264f2a73`, `8346be65`, `8370abb1`, `4132d260`, `b53439dc`, `9c85db2a`, `1facc0c2`, `91daff27`, `2f4448b6`.

**Method:** read every commit in the range with `git show`, and each claim in `8346be65` / `9c85db2a` against the
code before its fix.  For `8370abb1`'s heart - a train may now stand on a copy trains may not arrive at, by design - the
enforcing methods and every caller: `AutonomyBuilder.placementCopy` / `startableCopy` / `homeCopy` / `homeFacingsAt` /
`facingOf` and the per-copy emission (`station`, `reversing`, `homeFacingFixed`); `AutonomySession.copyFacing` /
`moveOntoFacingCopy` / `flipFacing` / `setFacingAndMove` / `faceTheWayItCameIn` / `facingChoices` /
`facingsThatCannotBeHeld` / `placeableFacingsFor` / `facingOnTheRailway` / `homeFacingOf` / `knownHomeFacing` /
`writeHome` / `setHome` / `captureFromLayout` / `importLegacy` / `facingAfterAPaste`; `TrainControlUI`'s paste,
`copyFacing`, `placeableFacings`, `followDirectionChanges`, `rebuildRunningLayoutFromSetup`, `whereTheTrainsAre` /
`putTheTrainsBack`, `reconcileFacingWhenIdle`; `GraphLocAssign.commitAndRecord`; `AutonomyEditorPanel.buildFacingMenu`,
the home door, the Why answer and the menu-only panel the track diagram's right-click uses;
`LayoutRightclickAutonomyMenu`'s placeable copies; `Layout.explainCannotStart` / `explainDestinations` /
`getPossiblePaths` / `isPathClear`'s non-station start rule / `moveLocomotive` / `parseAuto`'s placement warning;
`HomeStaging.snapshot` / `plan` / `whyNotHome` / `atHome` / `firstClearRoute`.  For `walkOneTail`'s fork rule, the loop,
its two callers and every argument they pass, and the frozen railway's placed trains (python, read-only, over
`test/layouts/live-snapshot/config/autonomy`).  For `executePathInternal`, every return in `configureAndLockPath` and
every other `takingPath.remove`.  Python, read-only, over the seven translated bundles before and after `91daff27`.
The finding store was grepped for each subject raised.  **Nothing was run**; every finding carries a verification
request, and says when it rests on reading alone.

## A - high

### TDY3-A1 - A train the throttle has stood on a copy trains may not arrive at is not carried across the next uncaptured rebuild: `putTheTrainsBack` re-places it with `moveLocomotive`, which refuses a copy that is not a station, so the model puts the train back where the stale setup last had it

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first): putTheTrainsBack may stand a train back on a copy of a station trains may not arrive at |
| **Where** | `TrainControlUI.java:6494` (`putTheTrainsBack`, the railway-wins branch); `Layout.java:9037-9044` (`moveLocomotive`'s "Can only place loc on a station"); reached through `AutonomyEditorPanel.rebuildRunningLayoutSoon` (`:9355-9380`) from the track diagram's right-click menu, which captures nothing first (`TrainControlUI.java:6680-6697`, the comment that says so) |

**New in kind with `8370abb1`, old in mechanism.**  Since `8370abb1` the throttle's direction-follow and the Facing
menu stand a train on a copy no train may arrive at whenever only that copy faces the new way (`copyFacing`'s new
fallback, `AutonomySession.java:1582-1598`) - which is the fix TDY2-A1 asked for.  The rebuild that the diagram's
right-click gestures trigger cannot carry that state.

`rebuildRunningLayoutFromSetup` rebuilds from the setup WITHOUT capturing (its own comment: *"the track diagram viewer's
right-click menu is an `AutonomyEditorPanel` too, every setting on it ends here, and NOTHING on that path captures.
Setting a home after a run then put each moved train back at its pre-run square - OB-183 again"*).  The remedy for
OB-183 is `whereTheTrainsAre` before the load and `putTheTrainsBack` after it - and the railway-wins branch re-places
each train with the placement gesture:

```java
if (!built.moveLocomotive(was.getKey(), was.getValue()[0], false)) continue;
```

`moveLocomotive` refuses any target that is not a destination (`autolayout.errorPointIsNotStation`).  AMS-C2 added the
`continue` for "a station demoted with the train still on it" and accepted that the train is then left where the
build put it.  Since `8370abb1` the same branch is reached by an ordinary operator gesture.

**Failure scenario, on the frozen railway** (`live-snapshot`: BottomMainA `1 - Main:20,12` bars arrivals from the east,
and its setup records 75 407 DB there facing E):

1. A run moves 75 407 DB off BottomMainA and hand-sends (or autonomy sends) another train T to BottomMainA; it arrives
   from the west on "BottomMainA (eastbound)", a station.  Nothing writes the run back to the setup (behaviour.md 6a):
   the setup still has 75 407 DB at BottomMainA facing E, and T at its departure square X.
2. The operator reverses T on the throttle to take it back west.  `flipFacing(T, running)` finds T on the railway at
   BottomMainA, reads the setup's `E` there (75 407 DB's), writes `W`, and `moveOntoFacingCopy` stands T on
   "BottomMainA (westbound)" - `station: false`.  Correct, and what TDY2-A1 asked for.
3. The operator changes anything from the track diagram's right-click menu - a home, a length, a name, on any square.
   The panel's `setupChanged` -> `rebuildRunningLayoutSoon` -> `rebuildRunningLayoutFromSetup(true, <empty set>)`.
   The build stands T at X (the setup's placement) and 75 407 DB on "BottomMainA (westbound)" (the setup now says W
   there).  `putTheTrainsBack`: 75 407 DB is moved back to where the railway had it (a station - accepted); for T,
   `back` = "BottomMainA (westbound)", which does not hold T, so `moveLocomotive(T, "BottomMainA (westbound)")` - refused,
   one log line *"BottomMainA (westbound) is not a station."*, `continue`.
4. The model now has T standing at X, a station, facing whatever the setup recorded there.  T is physically at
   BottomMainA.  Autonomy - or a hand send from the diagram, whose menu is built from the model - dispatches T from X:
   a route is locked from X while T, given speed at BottomMainA, drives over track nothing reserved.  BottomMainA reads
   empty in the model (its sensor is still occupied, which is what keeps other trains out of the platform itself).

`8370abb1`'s promise - *"Stood on a copy autonomy cannot start from, it is refused with a sentence that says why"* -
does not survive step 3: after it the train is not on that copy in the model, it is on a station elsewhere, and
autonomy will start it.  Before `1c855483` the same chain existed (the flip then also used a barred copy); between
`1c855483` and `8370abb1` it did not, because nothing stood a train on a barred copy.

**Why nothing compensates.**  The rebuild does not fold the railway back (by design - ACC-B3, OB-144), the carry is
the only thing that preserves a run's positions, and I found no dispatch-time check that a train's start sensor is
occupied (`isPathClear` asks the END's feedback only).  The log line names a copy and says nothing about a train
having been left somewhere else.

Reading only; the chain is quoted code at every step.  **Verification request (execution, deterministic, no window):**
`TrainControlUI.putTheTrainsBack(Layout, Map, Consumer, Set)` is public static.  In
`core.testATrainIsPutOnlyWhereItCanStart`'s fixture: build, stand `PROBE` on "BottomMainA (westbound)" directly
(`running.getPoint(...).setLocomotive(probe)`, or through `flipFacing` as `testAReversalOnTheThrottleIsFollowed` does),
with the SETUP placing `PROBE` on another station square (or nowhere).  `Map standing =
TrainControlUI.whereTheTrainsAre(running)`; rebuild (`model.parseAuto(session.buildConfiguration())`);
`TrainControlUI.putTheTrainsBack(model.getAutoLayout(), standing, s -> {}, new HashSet<>())`; find `PROBE`.
**Proves:** `PROBE` stands on the setup's square (or on nothing), not on "BottomMainA (westbound)".  **Refutes:** it
stands on "BottomMainA (westbound)".  **Suggested fix:** in the railway-wins branch, when `back` is not a destination,
stand the train there with `setLocomotive` (sweeping its other copies, and putting side and road back) - the
reasoning `moveOntoFacingCopy`'s javadoc gives for not using the placement gesture applies word for word: the train is
already there, this is not a placement.  At the least, when the carry declines, take the train OFF the square the build
put it on and say so, rather than leave it standing somewhere it is not.

### TDY3-A2 - The throttle's direction-follow flips the setup's record of the square, not the heading of the copy the train stands on; after a run that record is nothing or the previous occupant's, so a reversal is dropped or followed backwards, and the train is dispatched against its decoder

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first): flipFacing reads the railway first |
| **Where** | `AutonomySession.java:1889` (`Side recorded = getFacing(tile);` in `flipFacing`), `:1906-1908`; caller `TrainControlUI.java:12648` (`followDirectionChanges`) |

**Not introduced in this range** - it has stood since REG6-A2's fix (2026-09-06) made `flipFacing` find the train's
square on the running layout but left the value it flips as the setup's.  It is raised here because TDY2-A1's fix
rests on it: `8370abb1` makes the follow move the train onto "the copy facing the new way", and the new way is computed
as *the other of the setup's recorded facing*.  Every claim for the follow seeds that record to match the train first
(`testAReversalOnTheThrottleIsFollowed`: `session.setFacing(mainA, Side.E)`; the same in
`testAutonomyDiagramSession`'s flip tests), so none can see what happens when it does not.

```java
Side recorded = getFacing(tile);          // the SETUP's FACING for the square
List<Side> choices = facingChoices(tile);
if (recorded == null || choices.size() != 2) continue;
...
final Side now = choices.get(0) == recorded ? choices.get(1) : choices.get(0);
setFacing(tile, now);
moveOntoFacingCopy(running, locomotive, tile, now);
return tile;
```

The class knows the record is stale exactly here: `faceTheWayItCameIn`'s javadoc (REV9-A1) - *"`flipFacing` is relative
- it takes what the setup has recorded for the square and writes the other choice ... `captureFromLayout` never clears
`FACING`, so an arrival square carries either nothing or the previous occupant's answer.  Flipping nothing wrote nothing
and the turn was lost; flipping the previous occupant's answer wrote the turn backwards"*.  The idle drain was given an
absolute answer for that reason; the throttle door, which runs in the same state (after a run, while idle), was not.
And the reading that knows - `facingOnTheRailway`, the copy the train stands on - already exists and is what the
Facing menu and the diagram arrow read since OB-181 (`AutonomyEditorPanel.java:3715`, `TrainControlUI.java:5702`).

**Failure scenarios, after any run (hand send, autonomy, Return Home), with the railway idle:**

1. **Nothing recorded at the arrival square** - on the frozen railway, for instance, BottomMainB (`1 - Main:20,13`,
   EN57-947's home) carries no `facing`.  A train is sent there and arrives on a plain copy (a turn on arrival would be
   written absolutely by the idle drain, which is the case REV9-A1 fixed); the operator reverses it on the throttle.
   `recorded == null`, the loop `continue`s, `flipFacing` returns null: nothing written, nothing moved, nothing logged
   (`followDirectionChanges` returns silently on null) - and `lastSeenDirection` has already taken the new direction,
   so the change is not retried.  The model keeps the copy it arrived on; the decoder points the other way.
2. **The previous occupant's facing, the other way** - the frozen railway has one already: BottomInnerOtherside
   (`1 - Main:14,3`) carries `facing: E` and no train.  A train that arrives at such a square facing W and is reversed on
   the throttle: `recorded = E`, `now = W`, `setFacing(W)`, `moveOntoFacingCopy(W)` finds the train already on the W
   copy and returns; `flipFacing` returns the square and the log says *"...changed direction, so its facing at ... was
   updated to match"*.  The model still says W; the decoder now drives E.

Either way the next autonomy departure or hand send locks a route for the model's heading while the decoder drives the
train the other way over track nothing reserved - TDY2-A1's consequence, through the door TDY2-A1's fix repaired, for
any train at any two-facing square, with no barred copy involved.

Reading only.  **Verification request (execution, deterministic, no window):** in `testATrainIsPutOnlyWhereItCanStart`'s
fixture, pick a split square with two station copies facing opposite ways (BottomMainB's plain copies, or any square
`facingChoices` gives two for); build; stand `PROBE` on the copy facing E with `running.moveLocomotive`; do NOT place it
in the setup, and clear the square's setup facing (`session.setFacing(square, null)`) - the state a run leaves.
`session.flipFacing(PROBE, running)`.  **Proves (1):** returns null and `PROBE` still stands on the E copy.  Then
`session.setFacing(square, Side.W)` (a previous occupant's answer) and flip again.  **Proves (2):** returns the square,
`getFacing(square) == E`, and `PROBE` still stands on the E copy although the throttle has just reversed it from E.
**Refutes:** in both, `PROBE` ends on the W copy.  **Suggested fix:** where the train was found on the running layout,
take `recorded` from the copy it stands on (`facingsFor(tile).get(point.getName())`), and the setup's value only when the
railway has no answer - the running-first rule `facingOf`, `homeFacingOf` and the Facing menu already follow.

## B - medium

### TDY3-B1 - A legacy import invents the first copy's facing, and since `8370abb1` the build honours an invented facing that only a copy trains may not arrive at holds: an imported train at BottomMainA is stood where autonomy will not start it - GUI2-B1's case, through the import door

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first), with REG3-C1 |
| **Where** | `AutonomySession.java:793-803` (`importLegacy`: `setFacing(tile, ways.get(0))` over `facingsFor(tile).values()`); `AutonomyBuilder.java:759-775` (`placementCopy`'s new third and fourth loops); door `AutonomyViewerPanel.java:1183` |

GUI2-B1 was that a train with no recorded facing was stood on copy 0, and copies are emitted by arrival side N, E, S,
W - so at BottomMainA copy 0 is the barred "BottomMainA (westbound)".  `8370abb1` fixed it where nothing is recorded
(`startableCopy`).  But the legacy import does not leave the facing unrecorded; it invents one, deliberately
deterministic, from the same order:

```java
if (getFacing(tile) == null)
{
    java.util.List<Side> ways = new ArrayList<>(facingsFor(tile).values());
    if (!ways.isEmpty()) { setFacing(tile, ways.get(0)); result.facingsInvented++; }
}
```

`facingsFor` lists every copy, barred ones included, so at BottomMainA `ways.get(0)` is W - the facing only the barred
copy holds.  At `08a47bdd` the build turned that into the startable copy (GUI-B1's fallback, *"where no copy trains
may arrive at faces that way, one they may"*); since `8370abb1` `placementCopy`'s new loops treat the recorded W as a
physical fact and stand the imported train on "BottomMainA (westbound)", `station: false` - never started by autonomy,
for a heading the import's own comment says *"is no more likely to be right"*.  The same wherever the first-emitted
copy is a barred one whose facing no allowed copy holds - the barred side sorting first among the square's arrivals
(N, E, S, W) on a square with no turning copy.  Of the seven barred squares on the frozen railway that is BottomMainA
(arrivals E and W).  TopMainR1Inter and TopMainR2Inter (E barred) emit their allowed "(southbound)" copy first (read off
`test/baseline/configuration.json`, whose build has the same two squares); BottomInner's barred W always sorts last;
Tunnel's copies are north- and southbound, so its allowed N arrival comes first; and on the may-turn squares
(BottomMainPost, RampDown) every facing has an allowed holder, which `placementCopy`'s first two loops take.  (Inferred
from names and geometry, not from a build of the live snapshot.)
So this is a regression inside the range, at a one-off migration door and on one square of his railway; the operator is
told only that N facings were guessed (`autosetup.ui.facingsGuessed`).

Reading only.  **Verification request (execution, deterministic):** on the live-snapshot session, clear BottomMainA's
placement and facing (`placeLocomotive(mainA, null)`, `setFacing(mainA, null)`), then `session.importLegacy(legacy)`
with `{"points": [{"name": "X", "s88": <BottomMainA's s88, from running.getPoint("BottomMainA (eastbound)").getS88()>,
"loc": {"name": PROBE}}]}`; build.  **Proves:** `getFacing(mainA) == W` and `PROBE` stands on a Point whose
`isDestination()` is false.  **Refutes:** a destination.  On `08a47bdd` the same stands it on "BottomMainA (eastbound)".
**Suggested fix:** invent over the copies a train may be put down on (`homeFacingsFor` / the build's `arrivalAllowed`
copies), or leave the facing null and let `startableCopy` decide - an invented facing is not a physical fact.

### TDY3-B2 - The sentences that refuse a train on a copy trains may not arrive at do not say why, and the remedy Return Home gives turns the model round: every placement door can only stand the train on the other copy, so following the advice - or cutting and pasting the train back where it is - records a train pointing west as pointing east

| | |
|---|---|
| **Disposition** | Partly fixed - the sentences say why and what to do (4be3798a, claim d65df6cb).  Open - Adam's decision: OB-284, whether a paste keeps a heading only a barred copy holds |
| **Where** | `HomeStaging.java:1946-1949` (`whyHomeStartNotAStation`, named with `Layout.placeNameOf`); `messages.properties:213`; `Layout.java:4944` (`startNotStation`, named with the copy's full name); the placement doors: paste `TrainControlUI.java:7254-7266` and `:8181-8183`, `GraphLocAssign.java:229-241` / `:285-287`, both through `facingAfterAPaste(placeableFacingsFor(...))` |

`8370abb1`'s design is that a train facing the way only a barred copy faces stands on that copy, and *"autonomy refuses
to start it there and says why"*.  What it says:

- Return Home, for such a train not at home (IMPOSSIBLE for the whole plan, by the all-or-nothing rule): *"it stands on
  BottomMainA, which is not a station - place it on a station first"*.  `placeNameOf` strips the copy suffix (Adam:
  *"the whole copy thing needs to be masked from the user"*), so the operator is told that a station on his diagram is
  not a station.
- The locomotive panel and the editor's Why: *"It is standing on BottomMainA (westbound), which is not a station, so
  autonomy will not start it from there"* - the copy name, the machinery the masking ruling hides everywhere else, and
  still no reason (arrivals from the east are barred there, and the train faces that way).

Neither says the true remedy (drive it off by hand - a hand send from a non-station copy is allowed, `isPathClear`
refuses one only while `isAutoRunning()` - or turn it on the throttle).  Return Home's remedy is actively wrong now:
**placing** the train is a placement door, and since GUI-B1 every placement door records and stands a train only over
`placeableFacingsFor` - at BottomMainA that is the eastbound copy alone, so `facingAfterAPaste` returns E (*"ONE COPY
MEANS ONE ANSWER"*).  Follow the advice with the train physically pointing west and the model says east; the next
dispatch is TDY2-A1's again.  A cut and paste back onto the same square does the same: `cutFacing` is W (read from the
railway), the landing's placeable copies hold only E, the paste records E.  That contradicts OB-270 (*"no train should
inadvertently change direction when pasted"*), which the same doors were built to honour, and `8370abb1`'s own reading
of *"we shouldn't allow an impossible facing to be saved"* - that a barred arrival bars STOPPING, not standing.

This is the guard/affordance shape between doors: the physical-fact doors (throttle, Facing menu, build) now accept a
barred copy; the placement doors do not.  What each should do is Adam's call: (a) let the placement doors stand a train
on a barred copy when its heading says so (moveLocomotive refuses non-destinations, so that means the `setLocomotive`
route); (b) keep them as they are and make the refusal sentences say what is true - *"it faces the way trains may not
arrive at BottomMainA from; drive it off by hand, or turn it round"* - and not advise a placement.

Reading only.  **Verification request (execution, deterministic, no window):** `AutonomySession.facingAfterAPaste(
session.placeableFacingsFor(mainA, running), Side.W, null)` returns E (the paste's and the dialog's rule for a train
pointing west).  For the sentence: stand `PROBE` on "BottomMainA (westbound)" with a home elsewhere, `planReturnToHome()`
- IMPOSSIBLE, and the reason reads "it stands on BottomMainA, which is not a station".  The full paste round trip needs
`ui.testACutTrainArrivesTheWayItWouldDrive`'s window: cut from "BottomMainA (westbound)", paste on BottomMainA, read the
copy - eastbound proves it.

## C - low

### TDY3-C1 - Why Not Moving? with Path Type Manual answers "autonomy will not start it from there" for a train on a non-station copy, which a hand send leaves from

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first) |
| **Where** | `AutonomyEditorPanel.java:7888-7894` (`composeWhy` asks `explainCannotStart` before the `byHand` split); `Layout.java:4934-4949` (`explainCannotStart`) |

MT-434 made the Why answer follow Path Type: on Manual it gives the reasons a hand send meets
(`explainDestinationsGrouped(standing, byHand)`).  But `explainCannotStart` is asked first on both tiers and returns
early, and two of its four reasons are autonomy's alone: a non-station start (`isPathClear` refuses it only while
`isAutoRunning()`; `getPossiblePaths` offers routes from it, and `LayoutRightclickAutonomyMenu.java:228` builds the send
menu for a train standing on one) and an inactive start (SPEC-B3: a hand send allows it).  So on Manual the answer
refuses a train the diagram's own menu will send.  Pre-existing since MT-434; since `8370abb1` the non-station case is
the designed result of reversing a train at BottomMainA, so it is now ordinary rather than a demoted-station corner.
Reading only.  **Verification request:** stand `PROBE` on "BottomMainA (westbound)" (not auto-running):
`layout.explainCannotStart(probe)` is non-null while `layout.getPossiblePaths(probe, true)` is non-empty - the Manual
Why answer and the send menu disagree.  **Suggested fix:** give `explainCannotStart` the tier, and on a hand send ask
only the reasons a hand send meets (not placed, paused if that binds hand sends).

### TDY3-C2 - A home set for a train standing on a copy trains may not arrive at saves a facing no train can be brought home in, which `writeHome` says cannot happen

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first) |
| **Where** | `AutonomySession.java:7410-7414` (`writeHome`), `:7444-7490` (`homeFacingOf`), against `:7356-7363` (`setHome`'s explicit-facing check) |

`writeHome` writes `HOME_FACING = homeFacingOf(tile, locomotive)` with the comment *"Only the facing the train standing
here has, which is a copy a train stands on, so no impossible facing is saved"*.  Since `8370abb1` a train can stand on
the barred copy, and `homeFacingOf` reads its facing from the railway first: homed there, the train's home is saved
`homeFacing: W` at BottomMainA, a facing `homeFacingsFor` (and so the door's own prompt, and `setHome`'s explicit
branch) would never offer.  The editor does not ask (`knownHomeFacing` is W), and the build ignores it (`homeCopy` finds
no allowed copy facing W; `homeFacingFixed` is not written), so the home silently becomes the square - behaviour.md 6
allows exactly that (*"a copy no train may arrive at - is still the square"*), so the effect is benign; what is wrong is
the comment, and a saved value Adam's ruling says should not be saved.  Reading only.  **Verification request:**
stand `PROBE` on "BottomMainA (westbound)", `setRunningLayoutSource`, `session.setHome(mainA, PROBE)`; `getPointProperty(
mainA, "homeFacing")` is "W" while `homeFacingsFor(mainA)` is {E}.  **Suggested fix:** write the railway's facing only
where `homeFacingsFor(tile)` contains it; otherwise write nothing (the square), as the explicit branch does.

### TDY3-C3 - Nothing pins the build's preference for a copy trains may arrive at among copies facing the recorded way, and the claim class's MUTATION note says it does

| | |
|---|---|
| **Disposition** | Fixed - d65df6cb: the claim, proven by its mutation in the round's mutation run |
| **Where** | `test/core/testATrainIsPutOnlyWhereItCanStart.java:47` (the MUTATION note); `AutonomyBuilder.java:743-757` (`placementCopy`'s first two loops) |

The note: *"have `placementCopy` or `moveOntoFacingCopy` take the first copy facing that way again, and its claim
fails"*.  For `placementCopy` no claim can now fail that way.  GUI-B1's build claim asserted `isDestination()` at
BottomMainA with the setup facing W; `8346be65` rewrote it into `testTheBuildKeepsTheFacingTheSetupRecords`, where the
first copy facing W IS the right answer.  The other build claim sets no facing (`startableCopy`).  The only fixture
where the first copy facing a way is barred while an allowed copy faces it too is BottomMainPost facing S (N-plain,
barred, before the S-turning copy) - and there only `copyFacing` is claimed (`testTheFacingDoorNeverMoves...`), not the
build.  `testAPastedTrainKeepsItsDirection.testAPlacementLandsOnTheCopyItsFacingNames` asserts the facing, not the
copy's station flag, and the baseline has no placed train at such a square.  So deleting the two
`arrivalAllowed` loops (or reverting `placementCopy` to "first copy facing that way") would leave the suite green, and
the build would stand a train recorded S at BottomMainPost on the barred N-plain copy.  Reading only.
**Verification request (execution, mutation):** delete `placementCopy`'s first two loops and run
`core.testATrainIsPutOnlyWhereItCanStart` and `core.testAPastedTrainKeepsItsDirection` - all green proves it.
**Suggested fix:** a build claim at BottomMainPost with the setup facing S, asserting the train stands on a destination
facing S.

### TDY3-C4 - Comments and one document `8370abb1` made false: `copyFacing` no longer refuses a barred copy, and a placement with no facing no longer gets the first copy

| | |
|---|---|
| **Disposition** | Fixed - 031a7ddb |
| **Where** | as listed |

- `TrainControlUI.java:7433-7434` (`copyFacing`): *"The session's rule, which every door that puts a train down asks
  (GUI-B1): only a copy a train may be put down on"* - since `8370abb1` the session's `copyFacing` returns a copy no
  train may be put down on when only such a copy faces that way.  The paste is safe only because its heading comes from
  `placeableFacings`, and the javadoc above (`:7412-7420`, "or null when no copy faces that way") is now true for the
  wrong reason.
- `TrainControlUI.java:8214-8218` (`placeableFacings` javadoc): *"a copy trains may not arrive at cannot be placed on
  (`copyFacing` refuses it)"* - `moveLocomotive` refuses it; `copyFacing` hands it back.
- `behaviour.md:638`: *"a copy no train may be placed on is still refused (`copyFacing`)"* - the same.
- `AutonomySession.java:6136-6139` (`facingChoices` javadoc): *"the first answer is the one a placement with no recorded
  facing actually gets"* - since GUI2-B1's fix it gets `startableCopy`; at BottomMainA the first answer is W and the
  build stands the train facing E.  `buildFacingMenu` ticks `facings.get(0)` on that premise when neither the railway nor
  the setup has an answer (`AutonomyEditorPanel.java:3772`) - reachable only with no running layout, so comment-grade.
- `AutonomySession.java:6158-6159`, `:5218-5221` and `AutonomyEditorPanel.java:3746-3747`: *"`placementCopy` falls
  through to the first copy"* - it falls through to `startableCopy` now.
- `AutonomyBuilder.java:636-645`: the orphaned javadoc for `placementCopy` (it sits above `homeCopy`'s, detached from the
  method it describes) says *"With nothing authored the first copy is used"* - false since `8370abb1`.
- `AutonomySession.java:7410-7413` (`writeHome`) - TDY3-C2.
- `behaviour.md:1002` (`4132d260`): *"share one prompt:the number box"* - a missing space.

Reading only; no verification needed beyond reading the lines.

## D - not defects

### TDY3-D1 - TDY2-A1 verified: the build, the throttle's follow and the Facing menu all keep the recorded facing, in every case TDY2 named

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`8370abb1`, claims `8346be65`.)  At BottomMainA (arrivals E and W, E barred, no turning copies) `facingChoices` is
[W, E] in build order.  **Throttle:** `flipFacing` - recorded E, `now = W`, `moveOntoFacingCopy(W)` -> `copyFacing(W)`:
no placeable copy faces W, `turning` stays null, the new loop over `facingsFor` returns "BottomMainA (westbound)" (not
terminus, not reversing) - the train moves.  Flipping back: recorded W, `now = E`, the placeable loop returns the
eastbound copy.  **Facing menu:** `setFacingAndMove(mainA, W)` - the same `moveOntoFacingCopy`.  **Build:**
`placementCopy(W)` - loops 1-2 find no allowed copy facing W, loop 3 returns the barred plain copy.  The barred turning
copy of a must-/may-turn square is emitted `reversing` (not terminus, since `stops` is false), so `copyFacing`'s "not
terminus, not reversing" and `placementCopy`'s `!reverse` pick the same copy.  `facingsThatCannotBeHeld` and the menu ask
`facingChoices`, which now agrees with what both doors and the build do - the "record W, stand E" state the checker was
blind to can no longer be made.  **Claims, read against `8346be65`:** `testTheBuildKeepsTheFacingTheSetupRecords` fails
there on `assertEquals(... , Side.W ...)` because GUI-B1's fallback stood the train on the E copy;
`testAReversalOnTheThrottleIsFollowed` and `testTheFacingMenuMovesTheTrainOntoACopyFacingThatWay` fail on the same
assertion because `copyFacing` returned null and the train stayed; `testATrainWithNoFacingIsPutWhereItCanStart` fails on
`isDestination()` at BottomMainA (copy 0 is the westbound copy).  Each asserts the train's copy, the variable.  GUI-B1's
own case still stands and is still pinned for `copyFacing` (`testTheFacingDoorNeverMovesATrainOntoACopyItCannotStartFrom`,
BottomMainPost S: the placeable loop returns the turning copy before the fallback is reached); for the build it is not
pinned - TDY3-C3.

### TDY3-D2 - The blessed baseline is back byte for byte

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`git diff 1c855483^ 8370abb1 -- test/baseline/` is empty.  The two trains it moves back (EN57-203 onto "TopMainR1Inter
(westbound)", 2-8-4 3505 SP onto "TopMainR1 (southbound)", both `station: false`) are recorded in the baseline's setup
facing N at `1,10` (E barred) and S at `4,4` (N barred) - facings only the barred copies hold - so they are the state
TDY2-A1 is about, as the commit says.

### TDY3-D3 - TDY2-C5 verified, and the fork-rule change alters no tail on the frozen railway

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`8370abb1`, claim `8346be65`.)  At the standing square, after the fork rule has settled on one neighbour, the chosen
rail is swapped for one that ENDS at the square and starts at a copy of the same neighbour, so the main loop reads it
from the end and spends the square first - the side rule's choice since SVZ-B1.  Where no arriving rail exists (a train
turned where it stands) nothing is swapped and AUT-C3's block read still applies.  The claim
`testATrainOnASquareEmittedOnceSpendsItFirst` is red on `8346be65` for the reason it names: `getNeighborsAndIncoming`
lists the leaving rail first, whose places `[t1, ON-square]` are read from the start, so `t1` is claimed and the
square is not - both assertions.  **Reach:** the swap sits in the fork-rule branch, which the standing square takes only
when `arrivedFrom` is null.  On `live-snapshot` all four placed trains carry a side (0,11 N; 20,12 W; 4,5 N; 13,9 S);
`walkStandingTrains` anchors a running train at its head with `entrySideOf(driven last, head)`; the planner's
`tailAlong` passes `entrySideOf(road's last edge, standing)` - and `entrySideOf` falls back to `sideTowards`, so it is
never null on a built edge.  So no tail on the frozen railway changes; the change reaches a hand-placed train with no
side, or a hand-written file.  Where it does, it can move the measurement rule's verdict (the arriving rail's length is
path + the standing square, the leaving rail's path + the neighbour's square) - which now matches what the same train
with a recorded side already got.  The covered edge becomes the arriving direction instead of the leaving one; the
reverse is refused by place since AUT-B1, and on the first hop both touch the occupied square.

### TDY3-D4 - TDY2-C6 / AUT2-C1 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`8370abb1`, claim `8346be65`.)  Every `return false` in `configureAndLockPath` either precedes `takingPath.put`
(`isAlreadyUnderway`, `isPathClear`) or removes by key after it (configure failure, actuation validation) - and by key
is its own, since a second dispatch of the same train is refused in the monitor while the first holds the claim.  So
the caller has nothing to remove, and removes nothing.  The claim holds the layout's monitor until both dispatches are
BLOCKED on it - no method on the path before `configureAndLockPath` is synchronized on the layout (`isValid`,
`isCurrentLayout`, the outer `isAlreadyUnderway` are not), so both are parked at the lock, as it says - and the run
list's monitor so the winner waits after claiming; on `8346be65` the loser's `remove(loc, path)` equals the winner's
list and `containsKey` fails.  The one other by-key removal a refused dispatch reaches is `executePath`'s exception
handler (`Layout.java:7949`) after `configureAndLockPath` has already removed and rethrown - a third dispatch would have
to claim in the gap between those two statements; not raised.

### TDY3-D5 - TDY2-C4 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`1facc0c2`, claim `9c85db2a`.)  `homeFacingOf` returns null once the train is found anywhere on the running layout
but not on a copy of this square; the editor's home door now asks exactly when `knownHomeFacing` (the same method) is
null, so the question and the answer read one store.  Both of TDY2-C4's cases walk right: the setup has the train here
but it drove off - null, asked; the railway has it here but the setup does not - the copy's facing, not asked.  The
claim is red on `9c85db2a`, where `knownHomeFacing` was added over the old `homeFacingOf` and answered the setup's E.
The bulk door (`homeEveryPlacedTrain`) reads setup placements and cannot differ from the railway while the editor is open
(no run can start); with the fix it writes no facing rather than a stale one where they do.  A train on a barred copy
is the one case the new reading makes worse in the record, not in behaviour - TDY3-C2.

### TDY3-D6 - TDY2-C1, C2 and C3 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`4132d260`.)  `setHomeLocomotive` now says a copy facing the way this one faces, not its turning twin; the
half-measured javadoc says the rule declines only when nothing, the berth included, is measured; both executePath
comments name `releasedEarly` and say `clearedEdges` is what `getActiveAccs` reads.  `git grep` finds no remaining
"non-atomic branch" or unlock-reads-`clearedEdges` sentence in src or test.

### TDY3-D7 - The other doors a barred-copy train meets: checked, and what they do

| | |
|---|---|
| **Disposition** | Closed - checked clean |

- **The capture** reads every Point's `loc` and names facings by copy through `facingByName`, station or not, so a
  train on "BottomMainA (westbound)" is captured at BottomMainA facing W, and the next build (`placementCopy(W)`) stands
  it back on the same copy.  With the setup consistent, a rebuild needs no carry (`back` already holds the train).
- **The idle drain** (`faceTheWayItCameIn`) is unaffected: a train that turned at a turning copy arrived by an allowed
  side, and that copy faces the side, so `copyFacing`'s placeable loop always answers before the new fallback.
- **The tail** of a train re-stood on the barred copy: `moveOntoFacingCopy` carries `arrivedFrom` and `arrivedAlong`,
  and the first hop takes the rail by that side, with AUT-C3's block read spending the square - the carriages are where
  they stopped, as behaviour.md 4 says.
- **Hand sends** from a non-station copy are allowed (`isPathClear` refuses a non-station start only while
  `isAutoRunning()`; `getPossiblePaths` enumerates from any start; the right-click send menu is built for an occupied
  non-station) - the guard has a way past.
- **Autonomy** skips it with `explainCannotStart`'s sentence; the load logs `warnLocomotivePlacedOnNonStation`.
- **Return Home** snapshots every Point's occupant, refuses a non-station start (SG-A2) and says so - with the sentence
  TDY3-B2 is about.
- **The paste's may-turn question** offers every copy's facing, and a facing only a barred copy holds there would now
  reach `copyFacing`'s fallback and then `moveLocomotive`'s refusal (the paste keeps its clipboard).  Not reachable:
  `mayTurnTiles` excludes must-turn squares, and on a may-turn square each allowed side has a turning copy facing it
  and a plain copy facing onward, so every facing has an allowed holder whenever one side is allowed.
- **Placement doors** (`LayoutRightclickAutonomyMenu`, paste, `GraphLocAssign`) choose over destination copies only, so
  `copyFacing`'s fallback is never reached from them - their heading is always a placeable facing or null.

### TDY3-D8 - GUI2-B1's path and `startableCopy`

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`placementCopy` with no facing, or an unparseable one, or one no copy faces, asks `startableCopy` - the allowed plain
copy, then any allowed copy, then 0 - the same fall-back tail as `homeCopy`.  At BottomMainPost with nothing recorded
the train stands on the S-plain copy (facing N), a station.  `facingsThatCannotBeHeld` reports the third case, as before.

### TDY3-D9 - `91daff27` changes diacritics only

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Python over the seven bundles at `91daff27^` and `91daff27`: 506 changed values (da 95, de 79, es 88, fr 90, it 58,
nl 4, pl 92 - the commit's count); every file pure ASCII; key sets identical to the English; every changed value, with
`\uXXXX` decoded and folded by its language's rule (de ae/oe/ue/ss, da aa/ae/oe, pl l-stroke, the rest by removing
combining marks), equals the value it replaced; `{n}` placeholder sets unchanged.

### TDY3-D10 - The rest of the range

| | |
|---|---|
| **Disposition** | Closed - checked clean |

REG2-C2 (`whyAutonomyWillNotStart` answers `menuNoSetupPossible` over a Central Station layout; the key exists in all
eight bundles) and REG2-C6 (`parseRoutesFromJson` overwrites `auto` before `fromJSON`; its only caller is
`importRoutes`, so no saved-route load is disarmed) read correctly; their lanes' validators own them.  `264f2a73` removes
one door from a source-shape guard with a reason that matches `whyAutonomyEditorCannotOpen`.  `2f4448b6`'s counts in
behaviour.md and open-questions.md agree with each other (3,909 rows, 3,552 findings); the store itself was not queried.

## What this pass did not cover

- **Nothing was executed.**  TDY3-A1 and TDY3-A2 have deterministic, windowless probes on the frozen railway; A2's is
  the cheaper and bites more often (any run, then a hand reversal), A1's needs a run, a reversal at BottomMainA and a
  right-click edit before any capture.
- `b53439dc` (tests.md tracker) and the DCN/REG/GUI/AUT documents catalogued in `2f4448b6` were not re-read; their own
  validators have them.
- TDY3-A2's reach beyond `flipFacing` was not swept: any other reader that takes a square's setup `FACING` as the
  heading of the train the railway has there after a run (the placement doors read the railway first since CONF-B1).
- Known and deferred items (AUT2-C2, GUI2-C2, GUI2-C4; REG2-C3, REG2-C7, AUT-C2, DCN-C3, REG-B1's import question)
  were not revisited.
