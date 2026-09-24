# Today's work (2026-09-23) on autonomy-diagram-r0 - round-4 validation of TDY3's fixes and the round-3 diff

**Status:** open

**Open at close:** TDY4-C5 - carried in the finding store.

**Prefix:** `TDY4`

**Reviewed:** branch `autonomy-diagram-r0` at `9f5d8e23`, 2026-09-23.  Baseline: TDY3 (written at `2f4448b6`) and the range `2f4448b6..9f5d8e23` - `d65df6cb` (claims), `4be3798a` (fixes), `9a9a8564` (tracker), `031a7ddb` (text), `9f5d8e23` (catalogue).

**Method:** read `git show` of every commit in the range, and each claim added in `d65df6cb` against the code at
`d65df6cb` (= `4be3798a^`) to decide whether it is red there and on which assertion.  For `4be3798a`: `flipFacing`, both
`facingOnTheRailway`s, `moveOntoFacingCopy`, `copyFacing`, `facingChoices`, `followDirectionChanges`;
`Layout.moveLocomotive` (both forms, whole body - `clearBlockExcept`, `claimHome`, `reversedOnArrival`),
`isABarredCopyOfAStation`, `Point.isSamePlaceAs` / block, `AutonomyBuilder.blockFor` (the per-road block on double
curves and overpasses), `placementCopy` / `startableCopy` / `homeCopy` / `homeFacingsAt` / `arrivalAllowed`, every
caller of `moveLocomotive` and of `Point.setDestination` (none at runtime); `TrainControlUI.whereTheTrainsAre` /
`putTheTrainsBack` / `rebuildRunningLayoutFromSetup`, the diagram's right-click door (`buildAutonomyTileMenu`,
`buildAutonomyFacingMenu`, `LayoutRightclickAutonomyMenu:818`), `AutonomyEditorPanel.setArrivalAllowed` / `radio` /
`buildFacingMenu` / the home door / `composeWhy`; `AutonomySession.importLegacy` / `homeFacingsFor` / `knownHomeFacing` /
`writeHome` / `homeFacingOf` / `setHome` / `setFacingAndMove` / `getLocomotiveNameAt`; `HomeStaging.whyNotHome` /
`atHome` / `snapshot`; `Layout.explainCannotStart` (both forms), `pickPath` and every reader of `isAutonomyPaused`.
Python, read-only, over the eight bundles (the two new keys decoded, ASCII-only, no ASCII apostrophe for
`MessageFormat`), over `test/layouts/live-snapshot` (the legacy file, the setup's `barredArrivals`), over
`issues.md`'s Inbox (recount) and the five round-3 documents (heading count against the store's row delta).  The finding
store was grepped for each subject raised.  **Nothing was run**; every finding says whether it needs execution.

## A - high

None.  Both of TDY3's A findings are gone in the cases TDY3 named (TDY4-D1, TDY4-D2); what the A1 fix accepts beyond
its premise is TDY4-B1.

## B - medium

### TDY4-B1 - The put-back now stands a train on a barred copy even where a copy trains may arrive at faces the same way, so every rebuild overrides the build's startable choice: bar the side a standing train came in by at a may-turn station and autonomy refuses it for good, where before `4be3798a` it started it

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933 (claim 1c6ae0fe, red first; in a9d5a2f0 the setup names another square, as after a run - the first form passed under the mutation, the build's own choice standing the train on the twin) |
| **Where** | `Layout.java:9108` (`moveLocomotive`'s new test: `evenOntoABarredCopy && isABarredCopyOfAStation(target)`), `Layout.java:9022-9031` (`isABarredCopyOfAStation`); caller `TrainControlUI.java:6498` (`putTheTrainsBack`, railway-wins branch); against `AutonomyBuilder.java:742-757` (`placementCopy`'s first two loops, GUI-B1) |

**New with `4be3798a`.**  The fix's own javadoc states its premise: *"a train can be THERE: reversed on the throttle,
or turned by the Facing menu, at a square whose other facing only such a copy holds - the copy is its direction"*.  The
test it added does not check that premise.  `isABarredCopyOfAStation` asks only "not a destination, and some copy in
its block is one", so the put-back accepts ANY barred copy - including one whose facing a copy trains may arrive at
also holds.  The build, for the same train and the same facing, takes the allowed copy (`placementCopy` loops 1-2,
*"AMONG THE COPIES FACING THAT WAY, ONE TRAINS MAY ARRIVE AT FIRST (GUI-B1)"*).  So the two doors that decide where a
standing train goes after a rebuild now answer differently, and the put-back runs last.

That state is reached on a square trains may turn at, where each facing has two holders (a plain copy of one arrival
and the turning copy of the other).  **Failure scenario, on the frozen railway** (BottomMainPost, may-turn, arrivals
from the north barred; copies "(southbound)" = N-plain facing S, "(southbound, reverse)" = N-turn facing N,
"(northbound)" = S-plain facing N, "(northbound, reverse)" = S-turn facing S):

1. With the north side open, a train T arrives from the north and stands on "BottomMainPost (southbound)", a station,
   facing S.  The operator opens the editor (which captures: the setup has T at BottomMainPost facing S) and unticks
   "From the north" under Trains May Arrive... - or does it from the track diagram's right-click, which captures nothing.
2. `setArrivalAllowed` -> `setupChanged` -> `rebuildRunningLayoutFromSetup`: `whereTheTrainsAre` records T on
   "(southbound)"; the build (N barred now) runs `placementCopy(S)`: loop 1 finds no allowed plain copy facing S, loop
   2 returns "(northbound, reverse)" - a destination facing S.  T stands where autonomy can start it.
3. `putTheTrainsBack`: `back` = "(southbound)", which does not hold T; `moveLocomotive(T, "(southbound)", false,
   true)` - now a barred copy of a station, accepted.  T is moved OFF the startable copy onto the barred one.
4. `explainCannotStart(T)` = *"It is facing the way trains may not arrive at BottomMainPost, so autonomy will not start
   it there.  Turn it round, or open that side..."*.  Neither remedy is needed: a copy facing S that autonomy starts
   from exists, and both copies leave by the same (south) side.  Every later capture + rebuild repeats step 3 (the
   capture records S, the build picks the turning copy, the put-back moves T back), so T stays refused until it is
   driven off by hand, reversed on the throttle, or the bar is lifted.

On `4be3798a^` step 3 was refused (`continue`), and wherever the setup had T at this square - the editor's captured
path, or a train that has not moved since it was placed - T kept the build's startable copy, so this is a regression
there; only where the setup still had T elsewhere (after a run, from the diagram) was the pre-fix result TDY3-A1 (T
left at the setup's old square), which is worse, and the fix is right to replace it.  The same happens when a square the train stands on barred is
made may-turn later (its facing gains an allowed turning holder).  On a square that may not turn the two plain copies
face opposite ways, so a barred copy is always its facing's only holder and the fix is exactly right there - which is
the BottomMainA case every claim uses.  Adam's standing preference applies (no check rather than an over-strict one).

Reading only; each step is quoted code.  **Verification request (execution, deterministic, no window):** in
`core.testATrainIsPutOnlyWhereItCanStart`'s fixture: `post = square(session, "BottomMainPost")`; keep
`was = session.getBarredArrivals(post)`; `session.setBarredArrivals(post, empty)`; `running = build(session)`;
`running.moveLocomotive(PROBE, "BottomMainPost (southbound)", false)` (true - a station while north is open);
`session.placeLocomotive(post, PROBE)`, `session.setFacing(post, Side.S)`; `where =
TrainControlUI.whereTheTrainsAre(running)`; `session.setBarredArrivals(post, was)`; `rebuilt = build(session)`;
`byTheBuild = standingOn(rebuilt)`; `TrainControlUI.putTheTrainsBack(rebuilt, where, s -> {})`; `after =
standingOn(rebuilt)`.  **Proves:** `byTheBuild.isDestination()` and faces S, while `after` is "BottomMainPost
(southbound)", `!after.isDestination()`, and `rebuilt.explainCannotStart(probe)` is the `startFacingBarred`
sentence.  **Refutes:** `after.isDestination()`.  On `4be3798a^` the same steps leave `after == byTheBuild`.
**Suggested fix:** accept a barred copy only where no destination copy of its block has the same `getCopyFacing()`
(the premise the javadoc states); where one does, stand the train on that one - the plain one before a `reversing`
one, as `placementCopy` does - and put the side and road back on the Point actually used (`putTheTrainsBack` writes
them onto `back` today).  `testATrainPutBackStandsWhereItStood` (BottomMainA, where only the barred copy faces west)
keeps passing.

## C - low

### TDY4-C1 - TDY3-C4 is recorded Fixed, but two of its eight sentences are still there: `facingChoices`' javadoc and the orphaned `placementCopy` javadoc still say a placement with no facing gets the first copy

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933: `facingChoices`' javadoc, and `placementCopy`'s moved onto it and corrected |
| **Where** | `AutonomySession.java:6188-6189` (`facingChoices` javadoc); `AutonomyBuilder.java:638-647` (the detached `placementCopy` javadoc above `homeCopy`'s) |

TDY3-C4 listed eight places `8370abb1` made false; its disposition (and `findings.tsv:2804`) reads *"Fixed -
031a7ddb"*.  `git show 031a7ddb` edits the `copyFacing` comment, the `placeableFacings` javadoc, behaviour.md:638 and
:1002, and the three "falls through to the first copy" sentences (`AutonomySession` :5269 and :6209,
`AutonomyEditorPanel` :3747); `4be3798a` rewrote `writeHome`'s.  Two bullets were not touched:

- `AutonomySession.java:6188-6189`: *"Ordered the way the builder orders its copies, because it IS the builder's order
  now - so the first answer is the one a placement with no recorded facing actually gets."*  Since GUI2-B1 that
  placement gets `startableCopy`; at BottomMainA the first answer is W and the build stands the train facing E.  Its
  last reader on that premise, `buildFacingMenu`'s `facing == facings.get(0)`, was removed by `4be3798a` (GUI3-C4) for
  exactly this reason, so the sentence now describes a contract nothing relies on and that is false.
- `AutonomyBuilder.java:643-645`: *"With nothing authored the first copy is used, which is a guess"* - false since
  GUI2-B1, and still detached from `placementCopy` (it sits directly above `homeCopy`'s javadoc, a javadoc with no
  member, so neither javadoc tool nor a reader of `placementCopy` sees it).

Reading only; `grep -n "first answer is the one a placement\|With nothing authored"` finds both at HEAD.
**Suggested fix:** "the first answer is the build's first copy, which is NOT what a placement with no recorded facing
gets (that is `startableCopy`)", and move the placementCopy javadoc onto `placementCopy` with its third sentence
corrected; then correct TDY3-C4's disposition to name the commit that finishes it.

### TDY4-C2 - On Manual, Why not Moving? still stops at "This locomotive is paused", a reason that binds only autonomy - the new tier's own comment says it gives "only what stops any route"

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933 (claim 1c6ae0fe, red first): by hand, no pause |
| **Where** | `Layout.java:4935-4946` (`explainCannotStart(loc, byHand)`, the `byHand` branch); `AutonomyEditorPanel.java:7891-7897` (`composeWhy` returns on any answer) |

`4be3798a` gave the hand tier its own answer (TDY3-C1 / GUI3-C1): *"BY HAND, ONLY WHAT STOPS ANY ROUTE (GUI3-C1).  A
route picked by hand may start from a copy that is no station and from a square switched off ... so those two reasons
are autonomy's and not the operator's"* - and then returns `autolayout.why.paused` first.  The pause is autonomy's
too: `Locomotive.setAutonomyPaused`'s javadoc is *"Flags autonomy as paused so that no further routes are started"*,
and its only enforcing readers in `src` are `Layout.pickPath` (`:4697`) and `checkForSlowerLoc` (`:4462`).
`isPathClear`, `executePath`, `getPossiblePaths` and the diagram's send menu (`LayoutRightclickAutonomyMenu.java:239`,
`:1383`) never ask it, so a paused train is sent by hand from the diagram while the editor's Why not Moving? on Path
Type Manual answers *"This locomotive is paused."* and lists no destinations - OB-225's complaint (*"in manual mode, I
still get reasons like ... will never be chosen in autonomy"*) for the one reason left.  TDY3-C1's suggested fix said
"paused if that binds hand sends"; it does not.

Pre-existing in behaviour (the pause was asked first on both tiers before), new in that the comment now states a rule
the code breaks.  Reading only.  **Verification request (execution, deterministic):** in
`testATrainIsPutOnlyWhereItCanStart`'s fixture, stand `PROBE` on a station copy, `probe.setAutonomyPaused(true)`:
`running.explainCannotStart(probe, true)` is the paused sentence while `running.getPossiblePaths(probe, true)` is
non-empty and `running.isPathClear(path, probe)` is true for one of them.  **Suggested fix:** Adam's call - drop the
pause from the hand tier, or keep it as a note above the destination list rather than an answer that ends it.

### TDY4-C3 - The new "facing the barred way" sentence never names a side, reads two ways in English, and says the opposite heading in German and Polish; it also leaves out the one remedy that changes nothing - driving the train off by hand

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933: "Trains may not arrive at {0} facing the way this one faces", with driving it off by hand first among the remedies, in all eight bundles; MT-488 says so (12ef4379) |
| **Where** | `messages*.properties`, `autolayout.why.startFacingBarred` and `autolayout.whyHomeStartFacingBarred` (added in `d65df6cb`, reached since `4be3798a`); `Layout.java:4971-4974`, `HomeStaging.java:1948-1952` (the two arguments given: the square's name and the menu's label) |

English: *"It is facing the way trains may not arrive at {0}, so autonomy will not start it there.  Turn it round, or
open that side under "Trains May Arrive..." in the autonomy editor."*  At BottomMainA (arrivals from the east barred)
the train faces WEST, and the menu item to tick is "From the east" (`autosetup.ui.menuArrivalFrom` = "From the {0}").
"The way trains may not arrive" is either the way they travel (west - then the heading is right and "that side" reads
as west, which is already open) or the side they come from (east - then "that side" is right and the heading is
wrong).  No reading makes both halves true, because neither argument names a side.

The translations split on exactly this.  da, es, fr, it, nl use the direction of travel ("en el sentido en que",
"dans le sens ou", "nel senso in cui", "in de richting waarin").  **de** - *"er steht in der Richtung, aus der Zuege
nicht in {0} ankommen duerfen"* - and **pl** - *"stoi zwrocony w kierunku, z ktorego pociagi nie moga przyjezdzac na
{0}"* - say "the direction FROM WHICH trains may not arrive": the train faces east, the opposite of the arrow the
diagram draws for it.  (Decoded from the `\uXXXX` values with Python; accents dropped here.)

TDY3-B2 also named the remedy that needs no change of heading or configuration - drive it off by hand (a hand send
from a non-station copy is allowed; `explainCannotStart(loc, true)` now returns null for exactly this train) - and the
sentence offers only a physical reversal or undoing the bar.  Reading only; no execution needed.  **Suggested fix:**
pass the side: *"It faces {2}, the way trains arriving from the {3} travel, and arrivals from the {3} are barred at
{0}.  Drive it away by hand, turn it round, or tick From the {3} under "{1}"."* - `at.getCopyFacing()` gives {2}; the
barred side is the arrival side the copy was built for (its name encodes it; the session knows it), which is not
simply the opposite of the facing on a curve.  Then re-translate all seven.

### TDY4-C4 - Comments `4be3798a` made false: `flipFacing` is still described as pivoting on the setup's facing, and the one-facing Facing menu as ticked

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933: `flipFacing`, `faceTheWayItCameIn` and the drain's comment; the single facing is ticked again, so the menu comments hold |
| **Where** | `AutonomySession.java:1848-1850`, `:1751-1753`; `TrainControlUI.java:7497`; `AutonomyEditorPanel.java:3750`; `test/regression/testEditorSurfaceRules.java:69-70` |

- `flipFacing`'s javadoc (`AutonomySession.java:1848-1850`): *"Left alone where the answer is not obvious: a locomotive
  that is not placed, a square with no recorded facing, and a square offering other than two facings"* - since
  TDY3-A2's fix a square with no recorded facing IS flipped when the railway has the train there; that was the fix.
- `faceTheWayItCameIn`'s javadoc (`:1751-1753`): *"`flipFacing` is relative - it takes what the setup has recorded for
  the square and writes the other choice"*, present tense.  It now takes the railway's copy first.  The contrast the
  paragraph draws (absolute drain, setup-relative follow) is half gone; "relative" is still true, "what the setup has
  recorded" is not.
- `TrainControlUI.java:7497`: *"This used to call `flipFacing`, which pivots on `getFacing(tile)`"* - present tense
  about `flipFacing`; it pivots on the railway's copy now, the setup only as a fallback.
- `AutonomyEditorPanel.java:3750` and its guard's javadoc `testEditorSurfaceRules.java:69-70`: *"So the one facing is
  shown, ticked"*.  GUI3-C4's change (`recorded != null && facing == recorded`) leaves the only radio unticked when
  neither the railway nor the setup names a facing - and on a one-facing square that is the one case where the facing
  IS known (every copy faces it).  Reachable only with the setup's train not on the railway at that square and no
  `facing` recorded, so the tick itself is cosmetic; either tick the single facing (`facings.size() == 1 ||
  facing == recorded`) or correct both sentences.

Reading only.

### TDY4-C5 - TDY3-A2's shape, one door along: the Facing menu is keyed on the SETUP's train, so after a run the diagram's right-click offers "P is facing" for a square the railway has T on, ticks T's facing, and the choice moves nothing

| | |
|---|---|
| **Disposition** | Open - for a follow-up: older than this review (OB-181, SPEC-B4) - after a run the Facing menu names the setup's train; key it on the railway's occupant, and move that train |
| **Where** | `AutonomyEditorPanel.java:3694` (`standing = locomotiveAt(target)` = `session.getLocomotiveNameAt`, the setup's `loc`), `:3712-3716` (the tick from `facingOnTheRailway(target, showing)` - any train on the square); `AutonomySession.java:7223-7238` (`setFacingAndMove` moves the setup's train); door `LayoutRightclickAutonomyMenu.java:818`, which captures nothing first |

Pre-existing (OB-181 / SPEC-B4), not in the range - raised because TDY3 listed *"TDY3-A2's reach beyond `flipFacing`
was not swept"* and `4be3798a` swept only `flipFacing`, and because the private `facingOnTheRailway(tile, locomotive,
running)` it added now sits beside a public `facingOnTheRailway(square, running)` that answers for ANY train.  After a
run the setup still names each square's pre-run occupant (behaviour.md 6a) and the diagram's right-click does not
capture.  With P placed at S in the setup and T standing at S on the railway: the menu is titled with P's name
(`autosetup.ui.menuFacingGroup`), ticks T's facing, and a click runs `setFacingAndMove(S, W)` - `setFacing` writes S's
setup facing (P's record) and `moveOntoFacingCopy(running, "P", ...)` finds P on no copy of S and returns.  T is not
turned; `placementChanged` rebuilds and the put-back leaves T where it was.  With nothing placed at S in the setup the
menu is absent, so T's facing cannot be told from the diagram until something captures.  No wrong heading reaches
the railway (the menu commands nothing); the operator's correction is silently dropped and the menu names a train
that is not there.

Reading only.  **Verification request (execution, deterministic, no window):** in `testATrainIsPutOnlyWhereItCanStart`'s
fixture: `session.placeLocomotive(mainA, "75 407 DB")` (the frozen setup's own placement there), `running =
build(session)`,
`session.setRunningLayoutSource(() -> running)`, then `stoodFacing(session, running, mainA, Side.E)` (the placement
displaces 75 407 DB from the square: the railway now has PROBE there, the setup still 75 407 DB).
`session.getLocomotiveNameAt(mainA)` is "75 407 DB"; `session.facingOnTheRailway(mainA, running)` is E (PROBE's);
`session.setFacingAndMove(mainA, Side.W)` leaves PROBE on "BottomMainA (eastbound)".  **Proves:** all three.
**Refutes:** PROBE ends on the westbound copy.  **Suggested fix:** key the menu on the
railway's occupant where the railway has one (the Why answer already does - `composeWhy` asks the layout), and move
that train.

## D - not defects

### TDY4-D1 - TDY3-A2 / AUT3-A1 verified: `flipFacing` pivots on the copy the railway has the train on, in both of TDY3's cases

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`4be3798a`, claim `d65df6cb`.)  `recorded = facingOnTheRailway(tile, locomotive, running)` - the new private method,
which matches THIS locomotive on a copy of THIS square and answers `facingsFor(tile).get(copy)` - and the setup's
`getFacing(tile)` only when that is null.  Case 1 (nothing recorded after a run): the railway answers, the flip moves
the train.  Case 2 (a previous occupant's facing): the railway's answer overrides it.  A second reversal reads the
copy the first one moved the train onto, so two reversals net to none on both records (the old setup-relative code had
that property only while the setup was in step).  Unchanged, and correctly so: a train the railway does not have
(DIR-C3's setup walk; `testADirectionChangeDoesNotFlipTheSquareTheTrainLeft`'s empty Layout gets null from the new
method and falls back as before); a square with other than two facings (`choices.size() != 2` still `continue`s -
none, one, or a double curve's four: DIR-C4's silence, not new); a facing the square cannot hold.  **Claim:**
`testAReversalIsFollowedWhateverTheSetupLastSaid` is red on `d65df6cb` on its one `assertEquals` for the copy's facing:
`lastSaid = null` - `recorded == null`, `continue`, PROBE stays on the eastbound copy; `lastSaid = W` - `now = E`,
`moveOntoFacingCopy(E)` finds PROBE already there.  It asserts the variable (the copy the train stands on).  The
comments this made false are TDY4-C4; the sibling door it did not sweep is TDY4-C5.

### TDY4-D2 - TDY3-A1 verified at BottomMainA, and the new acceptance refuses nothing it refused before

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`4be3798a`, claim `d65df6cb`.)  TDY3-A1's chain - T on "BottomMainA (westbound)" (barred), the setup elsewhere, an
uncaptured rebuild: `moveLocomotive(T, back, false, true)` now accepts `back`, T stands where the railway had it, and
the side and road go back on it.  The four-argument form is given `true` by `putTheTrainsBack` only: the other five
callers (`GraphLocAssign:811`, `LayoutRightclickAutonomyMenu:1205` / `:1450`, `TrainControlUI:7299` / `:7344`) use the
three-argument form, which passes `false` - "every placement door still refuses one" is true.  The rest of the body is
the same for a barred target as for a station: `clearBlockExcept`, `claimHome` (a positional home on a barred copy is
harmless - `whyNotHome` and `canGetHome` read `homeCopiesOf(home)`, the square's destination copies, and
`rebuildHomeStations` has claimed homes on barred copies at every load since `8370abb1`), `reversedOnArrival.remove`.
**`isABarredCopyOfAStation` has no false positive:** `Point.isDestination` is set only by the constructor (no caller of
`setDestination` in `src`), from `stops = point.isStation() && arrivalAllowed(node)`, so within one block a
non-station beside a station is a barred copy and nothing else.  On a double curve or overpass `blockFor` groups by
ROAD (`tile/road.first()`), so the two roads never share a block; the one miss is a road whose every arrival is barred
while the other road stays a station - its copies read as "not a station" (the old sentence, and the old put-back
refusal).  The menu allows barring two of a feedback double curve's four sides, but no such station exists on either
frozen railway; not raised.  What the new test does not bound is TDY4-B1.  **Claim:** `testATrainPutBackStandsWhereItStood`
is red on `d65df6cb` on its one `assertTrue` (the three-argument `moveLocomotive` refused, and PROBE stayed on
BottomMainPost, where the setup had it); it asserts the Point name, after a precondition that PROBE stood on a
non-destination.

### TDY4-D3 - TDY3-B1 / REG3-C1 verified: the import's guess is a way trains may arrive

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`4be3798a`, claim `d65df6cb`.)  The guess walks `facingsFor(tile).values()` in build order and takes the first facing
in `homeFacingsFor(tile)` (the facings of copies whose arrival is allowed), the first copy's only when there is none.
The bars it reads are layout-wide (`AutonomyCompanionStore.barredArrivals`, `setup.json`'s `barredArrivals`), so a
fresh configuration sees BottomMainA's (`"5:20,12": "E"` on the frozen railway) and the claim's precondition holds.  At
BottomMainA the guess is E and `placementCopy` loop 1 stands the train on the eastbound station copy.
`testTheGuessIsAWayTrainsMayArrive` is red on `d65df6cb` on `mayArrive.contains(guessed)` (W), and the legacy file it
needs is committed (`test/layouts/live-snapshot/config/autonomy_legacy/autonomy.json`, BottomMainA at s88 9), so its
`SkipException` branch is not taken.  An observation, not a defect: on a may-turn square with a barred side
(BottomMainPost) the first allowed FACING in build order is S, held only by the turning copy, so an imported train
stands on "(northbound, reverse)" where `startableCopy` would choose the plain "(northbound)" facing N - both startable,
the same guess as before the fix, and no train in the frozen legacy file stands there.

### TDY4-D4 - TDY3-C2 / GUI3-C2 verified: no home facing a train cannot be brought home in

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`4be3798a`, claim `d65df6cb`.)  `knownHomeFacing` returns the railway's facing only when `homeFacingsFor(tile)` holds
it; `writeHome` writes that or nothing.  The editor's home door asks exactly when `knownHomeFacing` is null (TDY2-C4's
rule, unchanged), over `homeFacingsFor` - so a train standing on BottomMainA's barred copy is not asked (one allowed
facing; `FacingPrompt.wouldAsk` declines) and its home is the square, which behaviour.md 6 allows.  `setHome`'s explicit
branch and the bulk door go through the same `writeHome`.  `testAHomeIsNotSetFacingAWayNoTrainArrives` is red on
`d65df6cb` (`homeFacingOf` answered "W" and it was saved).  It has no precondition that the train really stands facing
west before `setHome` - had `setFacingAndMove` stopped moving it, the claim would pass on "E" - but the two claims
beside it reach that state through the same two calls and assert it, so this is noted, not raised.

### TDY4-D5 - TDY3-C1 / GUI3-C1 / DCN3-C6 and AUT3-B1 verified: the refusals name the square and are tiered

| | |
|---|---|
| **Disposition** | Closed - checked clean |

(`4be3798a`, claim `d65df6cb`.)  `composeWhy` asks `explainCannotStart(standing, byHand)`; on Manual a non-station or
switched-off start is no longer an answer (the pause still is - TDY4-C2).  The autonomy tier names a barred copy by
`placeNameOf` with the new sentence, and `startNotStation` / `startInactive` now name the square too.  Return Home:
`whyNotHome` says `whyHomeStartFacingBarred` for a barred copy and keeps `whyHomeStartNotAStation` for a square that is
no station at all; `this.layout` cannot be null there (`snapshot` dereferences it before the constructor runs).  The
locomotive panel's two callers keep the autonomy tier, which is right for them.  `testATrainFacingABarredWayIsToldWhy`
is red on `d65df6cb` on its first `assertEquals` (the copy's name and "not a station"), and its other two asserts (the
hand tier null, Return Home's sentence) each fail there too.  The sentence's wording is TDY4-C3.

### TDY4-D6 - GUI3-C4 and TDY3-C3 / DCN3-C3: the claims read right

| | |
|---|---|
| **Disposition** | Closed - checked clean |

GUI3-C4: the Facing menu ticks only `recorded` (the railway first, then the setup).  Its claim is a source-shape guard
for one spelling (`facing == facings.get(0)`), red on `d65df6cb` because that line was there; a respelling would pass
it - the usual limit of a menu check here.  The one-facing side effect is in TDY4-C4.  TDY3-C3:
`testTheBuildPrefersACopyItCanStartFrom` is green on `d65df6cb` by design (the behaviour held), and its assertion
(`standing.isDestination()` at BottomMainPost recorded S) fails if `placementCopy`'s first two loops are deleted, since
loop 3 then returns the barred "(southbound)" copy; the mutation run itself was not re-checked.

### TDY4-D7 - The eight bundles: both new keys everywhere, ASCII-only, safe for `MessageFormat`

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Python over the eight `messages*.properties` at HEAD: each has `autolayout.why.startFacingBarred` and
`autolayout.whyHomeStartFacingBarred` with `{0}` and `{1}`, every file is pure ASCII, and no new value contains an ASCII
apostrophe (fr and it use U+2019), so `I18n.f` swallows no quoted section.  `{1}` is filled with each language's own
`autosetup.ui.menuArrivalsGroup`, so the quoted menu name matches the menu.  What two translations mean is TDY4-C3.

### TDY4-D8 - The tracker and the catalogue agree with the files

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`9f5d8e23`: the five round-3 documents carry 82 finding headings (AUT3 13, DCN3 19, GUI3 13, REG3 19, TDY3 18), and
`findings.tsv` grows by exactly 82 lines - 3,909 + 82 = 3,991 rows and 3,552 + 82 = 3,634 findings, as behaviour.md and
open-questions.md now say.  `9a9a8564`: OB-284 is filed as a question Adam can answer (keep a heading only a barred copy
holds, or keep choosing a way trains may arrive), and the Inbox recounts to 66 (39 OB, 27 FR), as open-questions.md
says.  AUT2-A1's and TDY3-B2's dispositions read "Partly fixed ... Open - Adam's decision: OB-284", which is what was
done.  MT-488 exercises TDY2-A1 with the setup and the railway in step (75 407 DB placed at BottomMainA); no hands-on
test covers TDY3-A2's case (a reversal after a run, the setup stale) or TDY3-A1's (a diagram edit in between) - both are
claimed deterministically, so this is noted for the coordinator rather than raised.

### TDY4-D9 - `031a7ddb`'s text

| | |
|---|---|
| **Disposition** | Closed - checked clean |

The joined words are split (AutomationAPI.md, Readme.md, PositionAwareJFrame, testAutoLayout), the two Spanish
questions gain their opening marks and "que" loses its accent, the "falls through to copy 0" sentences read in the past
tense or name `startableCopy`, and behaviour.md:634-642 points the paste's barred-heading question at OB-284.  The two
TDY3-C4 sentences it missed are TDY4-C1.  REG3's, GUI3's and DCN3's own items in it belong to their validators.

## What this pass did not cover

- **Nothing was executed.**  TDY4-B1 has a deterministic, windowless probe on the frozen railway and is the one worth
  running first; TDY4-C2 and TDY4-C5 have cheap probes; TDY4-C1, C3 and C4 rest on reading alone and need none.
- The mutation run behind TDY3-C3's disposition was not re-checked; the claim was read, not mutated.
- REG3's, GUI3's, DCN3's and AUT3's own findings and their parts of `031a7ddb` belong to their validators; only what
  touches `4be3798a`'s code was followed into them.
- Known and deferred items (AUT2-C2, GUI2-C2, GUI2-C4, AUT3-C3; OB-284, OB-283, REG2-C3, REG2-C7, AUT-C2, DCN-C3,
  REG-B1's import question) were not revisited.  OB-284's paste rule is Adam's; TDY4-C3's wording point stands however
  he answers it.
- An operator reversal while a destination turn is still pending in `reversedOnArrival` (not yet drained) was read but
  not traced end to end: the new pivot reads the model's copy, which is right once the idle drain has run and stale in
  the window before it - a window the old setup-relative pivot had as well.
