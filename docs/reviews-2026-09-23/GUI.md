# User interface since v2.8.1 - review

**Status:** open

**Prefix:** `GUI`

**Reviewed:** branch `autonomy-diagram-r0` at `281c79de`, 2026-09-23.  Baseline: v2.8.1, branch `master` at `5f0a75e3` (two-dot diffs).

**Method:** The whole diff since v2.8.1 is 73,500 inserted lines in `gui/` and the bundles, and the interface had a
dedicated review on 2026-09-19 (UIX, GUX, SVB - 50 findings, all closed).  So this pass read the whole diff since
that review (`b2a81548..HEAD`, 149 commits, about 4,700 lines in `gui/`, `automationui/` and the bundles) line by
line, and went back into the older code only where a new change leaned on it.  Scripts under
`scratchpad/review-2026-09-23/gui-work/` (read-only Python) checked: every `{n}` placeholder in all eight bundles
against the English; every `I18n.t`/`I18n.f` call's arity against its value; every `bundle.getString` key in the
forms; keys added or reworded since v2.8.1 whose translations were never written or never updated; and a call
graph of which `Layout` members reach its monitor, to test `testNothingOnTheEventThreadTakesTheRailwaysMonitor`'s
hand-kept list.  Nothing was run that starts Java.  Findings marked "reading only" say so.

## A - high

### GUI-A1 - the Atomic Routes gate switches atomic mode on in the middle of a run, and the run's unlock then gives back track it already gave back

| | |
|---|---|
| **Disposition** | Fixed - 0be2bbe4 (claim 5d871f6d, red first), second pass 919e0dc8 (claim b6d08239, red against the first repair): the unlock reads what was released early (`releasedEarly`), not what the tail cleared; Execute Timetable asks the gate after its refusals; RC-A9's claim seeds the new record too (b4b061e0) |
| **Where** | `TrainControlUI.java:6127` (`keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack`), its call at `TrainControlUI.java:23100` (Execute Timetable) and `AutoLocomotiveStatus.java:1159`; consequence in `Layout.java:4032` (`unlockPath`) |

**The rule it breaks.**  Atomic mode is a property of a whole run, not of a moment.  With it off, `executePath`
gives edges back one by one as the tail clears them (`clearedEdges`), another train may then claim them, and
`unlockPath`'s non-atomic branch knows this - it skips an edge already given back, because *"now that it counts,
the second release would take away a claim somebody else made in between"* (`Layout.java`, the RC-A9/OB-164
paragraph).  The atomic branch has no such check, because an atomic run never gave anything back early:

```java
if (this.atomicRoutes)
{
    if (i == 0) e.getStart().setLocomotive(null);
    e.setUnoccupied();                                   // counted: Edge.occupancy--
    if (i < path.size() - 1) e.getEnd().setLocomotive(null);
}
```

`unlockPath` reads `this.atomicRoutes` when the run ENDS (`Layout.java:8678`), not when it started.  So a run that
began non-atomic and ends atomic takes the atomic branch over edges it released early: it decrements the claim of
whichever train took each one since (`Edge.setUnoccupied` is a counter, floored at 0), and it sets
`setLocomotive(null)` on its start and every intermediate Point - which a train now standing there, or a path that
has reserved it ("reserving sets the locomotive exactly as arriving does", `Point.setLocomotive`), loses.

**It is the written contract of the setter, and the gate is the first caller to break it.**
`Layout.setAtomicRoutes`'s javadoc (`Layout.java:10381`, SVN-C17, 2026-09-03) says **"NOT WHILE ANYTHING IS
RUNNING"**, and closes that finding on the ground that *"the interface refuses the checkbox while
`isAutoLayoutRunning()`, which includes hand dispatches"*.  (It also says the true-to-false direction "is the one
that costs"; the false-to-true direction costs too, as above - the double release and the emptied Points.  That
sentence wants correcting whatever is done about the door.)

**The settings checkbox keeps that precondition; the gate does not.**  `atomicRoutesMouseReleased` does nothing
while `isAutoLayoutRunning()` - the settings panel refuses every change while trains run.  The gate, lifted from the
two file doors (where the `Layout` is brand new and nothing can be running) to the five dispatch doors, writes
`layout.setAtomicRoutes(true)` with no such test (`TrainControlUI.java:6127-6180`).  Two of those doors are reached
while trains are running:

- **Execute Timetable** calls the gate at `TrainControlUI.java:23100`, *before* its own `isAutonomyBusy()` refusal
  (`:23113`, inside the `invokeLater` at `:23104`), and the button stays enabled during an autonomy run (nothing but the handler itself greys it).  So the
  press is refused - "wait for active locomotives to stop" - after it has already switched the running railway to
  atomic and logged `warnAtomicRoutesKeptOnTrains`.
- **The commands panel's hand dispatch** (`AutoLocomotiveStatus.java:1159`) is gated on `!layout.isAutoRunning()`
  only, so it dispatches a second train while a first hand-dispatched train is still running (OB-164: *"the panels
  send trains"* in non-atomic mode) - and asks the gate first.

The gate only flips when a train on the roster has no length or track could be released, and edge lengths cannot
change under a running `Layout`.  But a TRAIN length can: `promptTrainLength` (the locomotive menu's "Train Length",
whose list starts at 0 = not set) has no running guard, which is exactly the VD16-B2 scenario the gate's own
comment describes - *"a length cleared a minute later"*.

**Failure scenario.**  Railway measured, every train measured, Atomic Routes unticked, autonomy running two trains.
Train A has given back edge E as its tail cleared it; train B has since been routed over E (occupancy 1).  The
operator sets some third locomotive's train length to 0 from its menu, then presses Execute Timetable.  The press is
refused, but `atomicRoutes` is now true.  A arrives: `unlockPath` takes the atomic branch, E goes to occupancy 0 with
B on it, and any Point of A's route that B has reserved is emptied.  The next dispatch can be routed onto E.

**Reach, honestly.**  It needs Atomic Routes off, which the gate itself now refuses until every rail and every train
is measured; both frozen copies of Adam's railway (`test/layouts/live-snapshot`, `test/operator_layout`) carry
`"atomicRoutes": true` today, although his configuration ran non-atomic before the gate (VD10-C15).  Mass Assign
Lengths and Mass Assign Train Lengths exist so that he can measure everything and switch it off - which is the
configuration this bites in.  Graded by consequence (occupied track reported free, the hazard the gate was written
to prevent), not by how often.

**Why nothing compensates.**  The milestone loop (`Layout.java:8354`) simply stops releasing once the flag is on,
which is safe; the harm is only the end-of-run unlock, and it has no record of which mode the run started in.
SVN-C17 was closed with a sentence rather than a lock precisely because the only writer was guarded; there are
now two writers and one guard.  Not re-raising SVN-C17: that finding was about a check-then-set race at the
checkbox, and this is an unguarded door added on 2026-09-21/22 (GS-B1, VD16-B2, VD17-B1).

**Verification request (needs execution).**  Model on `core/testTrainTailClearsEdges.testTheClearAndTheUnlockAskTheSameQuestion`
(it runs real paths with train lengths on a sandboxed railway).  Hand-built line S1 -> S2 -> S3 -> S4, every edge
measured; train A length 1, `setAtomicRoutes(false)`, simulate on.  Dispatch A S1 -> S4 and wait until A's tail has
given `S1->S2` back (it reads unoccupied).  Stand in for train B's claim the way `configureAndLockPath` makes one:
`S1->S2.setOccupied()` and `S2.setLocomotive(B)`.  Then flip the mode as the gate would - call
`ui.keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()` with a third rostered locomotive at length 0, or
`setAtomicRoutes(true)`, which is what it does - and let A finish.  **Proves it:** `S1->S2` reads unoccupied, or
S2's `getCurrentLocomotive()` is no longer B.  **Refutes it:** both survive A's unlock.  A second, cheaper check needs no run: with autonomy running, press Execute Timetable
programmatically (`executeTimetableActionPerformed(null)`) and assert `isAtomicRoutes()` is unchanged - today it
flips.

**Suggested fix.**  Either the gate returns without writing while `layout.isRunning()` (the checkbox's own rule;
the dispatch that follows is then refused or runs under the mode already in force), or `executePath` records the
mode a run started in and `unlockPath` uses that.  The second closes it for any future writer of the flag, and the
file doors keep working unchanged.  Moving Execute Timetable's gate after its busy refusal is needed either way -
a refused press should not change a setting.

## B - medium

### GUI-B1 - the placement doors other than the paste still save a facing only a barred copy holds, and the next build stands the train on that copy, where autonomy will not start it

| | |
|---|---|
| **Disposition** | Fixed - 1c855483 (claims 2e565b5e, red first), reworked in 8370abb1: the first repair stood trains on copies facing the other way (TDY2-A1, AUT2-A1, GUI2-A1) |
| **Where** | `AutonomyEditorPanel.java:5436-5439` (the editor's Place/Add to Autonomy door); `GraphLocAssign.java:263-265` (`commitAndRecord`, both Place/Edit Locomotive doors); consequence in `AutonomyBuilder.java:727-755` (`placementCopy`) |

OB-270's second half (Adam, 2026-09-23: *"we shouldn't allow an impossible facing to be saved"*) is written into
the paste door as `placeableFacings` - `facingsFor` filtered to copies that are destinations, because *"a copy
trains may not arrive at cannot be placed on"* (`TrainControlUI.java:8232-8262`).  The two other doors that put a
train down and record its facing still hand `facingAfterAPaste` the unfiltered map:

```java
// AutonomyEditorPanel.java:5438 - the editor's placement
session.setFacing(tile, AutonomySession.facingAfterAPaste(session.facingsFor(tile), heading, null));
// GraphLocAssign.java:263 - Place/Edit Locomotive, from the editor and from the track diagram
session.setFacing(tile, AutonomySession.facingAfterAPaste(session.facingsFor(tile), heading, point.getName()));
```

`facingsFor` is `StationIndex.facingsAt`, which lists every copy the build made, including one for an arrival side
the operator has barred - emitted with `"station": false` (`AutonomyBuilder.java:941`, `stops = isStation() &&
arrivalAllowed(node)`).  `facingAfterAPaste` keeps the train's previous heading wherever ANY copy holds it.

**And the build honours it.**  `placementCopy` picks the copy to emit the train on by facing alone - the plain copy
first, then any - and never asks `arrivalAllowed`, where its sibling `homeCopy` (written the same day for OB-282)
asks it at every step.  The loader then places the train on the `station: false` Point with only a log line
(`Layout.java:11607`, `warnLocomotivePlacedOnNonStation`), and from there `Layout.explainCannotStart` answers
`autolayout.why.startNotStation` - *"It is standing on {0}, which is not a station, so autonomy will not start it
from there."*

**On Adam's railway as frozen.**  `test/layouts/live-snapshot/config/autonomy/setup.json` bars arrivals from the
east at BottomMainA (`5:20,12`: `"E"`), so its copies are "BottomMainA (eastbound)" (arrived from the west, faces
E, a station) and "BottomMainA (westbound)" (arrived from the east, faces W, not a station).  Place a train whose
recorded heading is W - one standing westbound at BottomMainB, say - on BottomMainA from the editor's menu: the
setup records W, `setupChanged` rebuilds, `placementCopy` emits the train on "BottomMainA (westbound)", and the
train is on the railway, drawn at the station, and never started by autonomy.  (The editor's door names the train
in `placementsJustEdited`, so `putTheTrainsBack` lets the build's copy stand - that is what makes it immediate.)
The Place Locomotive dialog, from either surface, does the same one load later: it moves the train onto a station
copy now (see GUI-B3) and records W; a mid-session rebuild puts the train back on that copy by name, and the next
configuration load or start-up - which has no running layout to put it back from - emits it on the barred copy.

A may-turn square with a barred side has the same hole from the other direction: the heading may be a possible one
(the turning copy of the open lane holds it), and `placementCopy` still prefers the plain copy of the barred lane.
The paste's `copyFacing` skips non-destinations for exactly this reason (MT-394: *"preferring the plain copy
regardless made every west paste place nothing at all"*); the build's twin of that rule never learned it.

**And a third copy of "the copy that faces X" has the same gap: `AutonomySession.moveOntoFacingCopy`**
(`AutonomySession.java:1517-1590`).  It stands the running train on the FIRST copy in emission order whose facing
matches - `if (facing == copy.getValue() && onto == null) onto = point;` - with no destination or arrival test,
and moves it there with `Point.setLocomotive`, which (unlike `moveLocomotive`) refuses nothing.  Its callers are the
Facing menu (`setFacingAndMove`, a GUI door on both surfaces) and the idle drain after a train turns on arrival
(`faceTheWayItCameIn`, from `TrainControlUI.reconcileFacingWhenIdle` - no gesture at all).  Emission order is
arrival side N, E, S, W, plain before turning copy (`AutonomyBuilder.splitSides` is a `TreeSet` of the enum;
`nodesFor`).  At **BottomMainPost** on the frozen railway - may-turn, north arrivals barred - the copies come out
N-plain (faces S, not a station), N-turning (faces N, not a station), S-plain (faces N), S-turning (faces S).  So
a train that arrives from the south and is turned there is written facing S by the drain and moved from the
S-turning copy it stopped on to the N-plain one, which is not a station; and the Facing menu sends either answer
to a barred copy.  This half is the model's code and is reached without the interface - reading only, and worth
the model reviewer's eye as much as mine.

**Verification request (needs execution; a core test, no window).**  Model on `core/testAutonomyDiagramSession`
(it builds sessions from the frozen snapshot and inspects `buildConfiguration()`).  On the live snapshot: give a
locomotive a recorded facing W on another square, then run the editor door's two lines -
`session.placeLocomotive(<5:20,12>, name)` and `session.setFacing(<5:20,12>, AutonomySession.facingAfterAPaste(
session.facingsFor(<5:20,12>), Side.W, null))` - build, and find the Point whose `loc` is that train.
**Proves it:** that Point has `"station": false` (and after `parseAuto`, `explainCannotStart` returns the
startNotStation sentence).  **Refutes it:** the train is on a station copy or the facing is not saved.  Repeat with
`placeableFacings`-style filtering to see the fix hold.  For `moveOntoFacingCopy`: on the same snapshot, stand a
train on "BottomMainPost (northbound, reverse)" of a running layout, set its `arrivedFrom` to "S", and call
`session.faceTheWayItCameIn(name, thatPoint, running)`; **proves it** if the train ends on a Point whose
`isDestination()` is false.  First print `session.facingsFor(<5:22,6>)` - its order is the whole question.

**Suggested fix.**  Both halves, since either alone leaves a door: the two GUI doors filter as the paste does
(`placeableFacings` belongs somewhere both `TrainControlUI` and `GraphLocAssign`/`AutonomyEditorPanel` can reach -
the session is the natural owner), and `placementCopy` and `moveOntoFacingCopy` skip a copy trains may not arrive at,
as `homeCopy` and `copyFacing` do, so a facing already saved - or written by the drain - cannot put a train on a
non-station either.  Four spellings of one rule (`copyFacing`, `homeCopy`, `placementCopy`, `moveOntoFacingCopy`),
two of them missing its condition, is the shape this project's sibling-drift memory is about; one shared helper
would end it.

### GUI-B2 - Segment Length opens on an unmeasured square showing "0", so pressing OK untouched now records a deliberate 0

| | |
|---|---|
| **Disposition** | Fixed - 55959c9c (claim 41589728, red first): Segment Length opens empty where nothing has been said |
| **Where** | `AutonomyEditorPanel.java:6929-6996` (`applyLength`, prefill at `:6941-6942`), `:7011` (`applyLengthAnswer`); reached from the menu item (`:1990`) and Control+E (`:5823`) |

Since `87361089` (2026-09-23), a 0 submitted in Segment Length is recorded as **answered 0**
(`session.answerTileLengthsZero`) - kept, never offered again by Mass Assign Lengths, and, per the commit, no longer
counted as missing by the half-measured berth notice, the reversal notice and the berth refusal's "N squares still
have no length" note.  Adam's word for that state is *"a length that is set deliberately"* (OB-274).

The dialog's field is prefilled with what the run measures now, and for an unmeasured run that is
`String.valueOf(lengthShownFor(sample))` = **"0"** (`lengthShownFor` sums `max(0, getTileLength)`, `:9058`).  It
is selected so it can be typed over (OB-176), and OK is the default button.  So opening Segment Length on an
unmeasured square - or pressing Control+E over one - and pressing Enter, which before 2026-09-23 was a no-op
(`0` cleared an already-empty length), now records every square of the run as answered - which takes those squares
off the lists that say they still need measuring, and the whole piece off Mass Assign Lengths where the run is the
whole piece.
Nothing was typed; the 0 was the dialog's.  Its sibling prompt, Mass Assign Lengths' `askForWholeLength`, opens
with an empty box precisely so that an untouched OK means nothing ("Skip, and a blank box, still leave the piece
as it was").

The rules still read the square as unmeasured, so nothing unsafe follows - what is lost is the reminder, silently,
on the one surface built to collect deliberate answers.  B rather than C because the state it writes is the one
Adam asked to be reserved for a decision, and the operator is not told it was written.

**Verification request (needs execution).**  Model on `core/testMassAssignLengths` (its Segment Length claims drive
`applyLengthAnswer`) and `ui/testTheLengthPromptHasTheKeyboard` (which opens the real editor window) for the dialog: open the editor on an unmeasured run, invoke Segment Length, press OK
without typing (a `Robot` Enter, or find the `JOptionPane` and click its first button), then assert
`session.getStore().isTileLengthAnswered(tile)` is false.  **Proves it:** true.  A reading-only confirmation:
the prefill argument at `:6942` is `String.valueOf(...)` of an `int`, so it can never be empty.
**Suggested fix:** prefill an empty box when the run measures nothing and has no answer (an answered 0 can still
show "0"), which is the walk's own shape; an untouched OK then goes through `applyLengthAnswer(tile, null)`, which
changes nothing on an unmeasured run.

### GUI-B3 - OB-270 was fixed at the paste and not at "Place Locomotive At...": the dialog still puts the train on whichever copy came first and records a different heading

| | |
|---|---|
| **Disposition** | Fixed - 1c855483: the dialog commits onto the copy it records |
| **Where** | `GraphLocAssign.java:209-268` (`commitAndRecord`) and `:787` (`commitChanges`); reached from `LayoutRightclickAutonomyMenu.java:764` (track diagram) and `AutonomyEditorPanel.java:5265` (editor) |

OB-270 (2026-09-23) is written up in `behaviour.md` "Pasting a train" as a rule about the *act of putting a train
down*, not about the paste key: *"A paste writes two things - the heading in the setup and the copy the train
stands on in the running layout - and the copy IS the direction ... the copy used to be whichever
`StationIndex.speakerAt` met first, so the record said east while the train stood westbound."*  The fix
(`TrainControlUI.java:7250-7264`) moves the train onto `copyFacing(aimed, intended)` and chooses `intended` over
`placeableFacings`.

The Place/Edit Locomotive dialog is the same act by another door, and it still has the old shape:

- the Point is the one the menu was opened on - `ui.getAutonomyPointForTile(...)` on the track diagram
  (`LayoutRightclickAutonomyMenu.java:478`), `pointOnTheLayout` in the editor - which on an empty square is
  "any copy will do";
- `commitChanges` moves the train onto exactly that copy: `moveLocomotive(getLoc(), p.getName(), false)`;
- `commitAndRecord` then records `facingAfterAPaste(session.facingsFor(tile), heading, point.getName())` - the
  train's previous heading wherever ANY copy holds it (not `placeableFacings`), with `landedOn` deliberately
  unused.

So on a square with two destination copies facing opposite ways (BottomMainB, BottomMainA with its east bar
lifted - the squares OB-270 and MT-394 were measured on), placing a westbound train through the dialog puts it
on copy 0.  When copy 0 faces east, the setup says west and the running layout has it on the east copy - and the
arrival side and the tail question that follow in the same method are asked for that copy.

**How long it lasts: until the next configuration load, on both surfaces.**  The track diagram's door does not
rebuild at all (`LayoutRightclickAutonomyMenu.java:795-796`, repaint only).  The editor's door calls
`setupChanged()` (`AutonomyEditorPanel.java:5288`), but it does not name the train in `placementsJustEdited` (the
editor's own Place door does, through `placementChanged`), so `TrainControlUI.putTheTrainsBack` puts it back on
the Point it stood on before the rebuild - copy 0 - by name.  Until the next load the train stands on the copy
facing the other way from its record, and a hand dispatch or autonomy reads that copy's outgoing edges - the
"drives off its route" consequence `Layout.java` describes for a train standing on the wrong copy.  At the next
load the build follows the record and the train changes copy, i.e. direction, between one session and the next.

**Verification request (needs execution).**  Model on `ui/testACutTrainArrivesTheWayItWouldDrive` (frozen
railway, BottomMainA's east bar lifted).  Place a train westbound somewhere (so `facingOf` answers W), then on the
track diagram build `new GraphLocAssign(ui, ui.getAutonomyPointForTile(<BottomMainA square>), false,
ui.getAutonomySession(), layout)`, select that train, and call `GraphLocAssign.commitAndRecord(edit)`.  Compare
`session.getFacing(square)` with `session.facingsFor(square).get(<name of the Point the train now stands on>)`.
**Proves it:** they differ.  **Refutes it:** they agree whatever copy `getAutonomyPointForTile` returns (run it
with the train's heading set each way, since only one of the two directions can disagree).

**Suggested fix.**  The paste's own two lines: choose `intended` over `placeableFacings(tile)` (GUI-B1's half) and,
where `copyFacing(tile, intended)` finds a copy, commit onto that copy rather than `p` - before the arrival side and
the tail are asked, so they are asked of the copy the train will actually stand on.  Separate from GUI-B1 because
it bites on a square with no bar at all: both copies are stations, the facing saved is a possible one, and only
the copy the train is put on is wrong.

## C - low

### GUI-C1 - fifty messages added since v2.8.1 are English in all seven translations, and three reworded ones kept their old translation

| | |
|---|---|
| **Disposition** | Fixed - ff129a4d: 52 keys in all seven bundles, two of them reworded ones whose translation said what the English used to; the third reworded key, French validateConfigOpenGraphUI, was already right (GUI2-C5) |
| **Where** | `src/org/traincontrol/resources/messages_{da,de,es,fr,it,nl,pl}.properties` |

`testMessageBundles.testTranslationsMatchEnglishKeySet` checks that every language HAS every key, not that the
value was translated, so a key pasted into all eight files in English passes.  Comparing each key added since
`master` with its English value (`gui-work/untranslated.py`, values longer than 12 characters) finds 51 that are
byte-identical to the English in all seven translations.  One is deliberate (`ui.main.toolbar.dataSourceCentralStation`
= "Central Station", UXR-D12); the other fifty are ordinary sentences and labels a Danish or German operator reads
in English:

`ui.warnBackupIncomplete`, `autolayout.warnLocomotiveWaitingLong`, `autolayout.errorAutoDestinationInvalidValue`,
the six `autolayout.ui.pathPreference*` labels and three of their `tooltip.` sentences (FEWEST_STATIONS,
FEWEST_POINTS, MOST_POINTS), `autolayout.ui.confirmExcludingHome`, `error.startupFailed`, `error.alreadyRunning`,
`loc.errorAddressOutOfRange`, eleven route-editor keys (`route.ui.frameCommands`, `confirmDiscardChanges`,
`titleDiscardChanges`, `frameCapture`, `tooltipCapture`, `frameNeedsAName`, `frameS88NotANumber`,
`frameNeedsACommand`, `infoNothingToHighlight`, `highlightOnDiagram`, `testCondition`), eighteen layout keys
(`layout.ui.infoStationLabelsMovedToAutonomy`, `switchDiscard`, `switchSave`, `confirmSwitchWithUnsavedWork`,
`dialogSwitchConfirmation`, `hintNoAutonomyToEdit`, `sidebarAutonomy`, `sidebarTrack`, `tooltipEditLayoutPage`,
`menuEditLayoutPage`, `hintNoWayOut`, `busyLoadingLayout`, `infoLayoutLoadedFrom`, `menuDeselectAll`,
`menuSelectByDragging`, and `layout.warnPowerNotConfirmed`, `warnDiagramKeyWhileBusy`, `warnNoAutonomyPointHere`),
`ui.uiStateUnreadableKept`, and four station-menu labels (`autolayout.ui.menuMaxTrainLength`,
`menuStationPriority`, `menuExcludedLocomotives`, `menuSpeedMultiplier`).

Earlier rounds fixed this shape as it came up (FBR-C2, UXR-C6, VD13-C5), so it is not a policy; `route.ui.*` is the
largest block, from the route editor rewrite.

**And three whose English was reworded while every translation kept the old sentence** (`gui-work/stale.py`):
`autolayout.ui.infoPleaseAddLocomotivesToGraph` ("...on the autonomy graph" -> "...on the autonomy track diagram";
the German still says "zum Graph", a window that left the build), `layout.ui.toggleVisibility` ("Toggle Visibility"
-> "Visible Elements"; German "Sichtbarkeit umschalten"), and `ui.main.validateConfigOpenGraphUI` in French only.

**Verification request (no execution needed):** `python gui-work/untranslated.py master` and
`python gui-work/stale.py master` from the scratchpad folder reproduce both lists.  A guard in the style of
`testMessageBundles` - "a key added after v2.8.1 whose value is identical in all eight files", with an allowlist for
names like Central Station - would hold it.

### GUI-C2 - the route editor's "+" row wears the refusal tooltip of the row above it

| | |
|---|---|
| **Disposition** | Fixed - e2223851 (claim d64023cb, red first): `unshaded` clears the tooltip |
| **Where** | `RouteEditorFrame.java:2214` (early return) against `:2218-2234` (OB-246's tooltip) |

OB-246 (2026-09-22) puts Save's refusal on a command row as a tooltip, and its comment is explicit about why it
must be set on every cell: *"SET ON EVERY CELL, never skipped: this renderer hands back one recycled component for
the whole table, so a row that is fine would otherwise wear the tooltip of the marked row drawn a moment
earlier."*  But the tooltip is set **below** two early returns in the same method - the joiner-row return and
`if (row >= which.getRowCount() - 1) return unshaded(out, which, selected);` for the "+" row - and neither
`unshaded` nor the inner renderer's own "+" branch (`:3877-3899`, which does reset the colours, for exactly this
recycling reason) touches the tooltip.  `DefaultTableCellRenderer` does not reset a tooltip either.

So the "+" row's cells hand back whatever tooltip the last rendered cell set.  In a paint, that is the last real
row; when that row is one Save refuses (a locomotive name with a comma, a function past the end of the
locomotive), hovering the line that adds a new command shows that row's refusal (for instance `route.ui.frameNameNotUsable`)
as though it were about the "+" line.
Joiner rows cannot show it, because only the command table sets a non-null tooltip.

Reading only, and cosmetic; recorded because the comment claims the opposite of what the code does.

**Verification request:** modelled on `ui/testRouteEditorShading` / `ui/testRouteEditorValidation` (they build a
real `RouteEditorFrame`; `commandCellForTest` is the OB-246 hook that renders a cell through the table).  Make the last command row one Save refuses (a LOCOMOTIVE
row whose target contains a comma); call `commandCellForTest(last, 1)` and then `commandCellForTest(last + 1, 1)`;
assert the second component's `getToolTipText()` is null.  Today it should be the first row's refusal.
**Suggested fix:** set the tooltip (to null) before the two early returns, or in `unshaded`.

### GUI-C3 - MT-477 reworded the tail question in the dialog and not in the editor, whose heading now sits over "not reached" answers

| | |
|---|---|
| **Disposition** | Fixed - 59fdb67d, with TDY-C3 |
| **Where** | `AutonomyEditorPanel.java:3845-3885` (`appendTailCrossed`), `:3907-3945` (`armTailPick`); keys `autosetup.ui.headingTailCrossed`, `hintTailCrossed`, `promptClickTailCrossed` |

Since `195aa1f1` (MT-477) `TailCrossedPrompt.choicesFor` returns roads the tail lies on *without* reaching their
sensor, labelled `autolayout.ui.tailCrossedToward` - "towards {0} (not reached)".  The dialog's question was
reworded to match (`askTailCrossed`: *"...Where it has not reached one, pick the way it lies."*).  The editor
asks the same list through two more doors that were not:

- the facing menu's radio group is headed `headingTailCrossed` = "Farthest sensor the tail crossed", and every
  radio in it - "towards RampDown (not reached)" included - carries the tooltip `hintTailCrossed` = "The farthest
  sensor the back of this train has passed on its way in";
- Pick on the diagram outlines every choice's `getFarthest()` - which for a not-reached road is a sensor the tail
  has NOT crossed - under `promptClickTailCrossed` = "Click the farthest sensor the tail of the train at {0} has
  crossed - one of the outlined squares."  An operator who follows the sentence will not click the sensor that
  means "the tail lies that way", and one who clicks an outlined square cannot tell which kind of answer it is.

At BottomSecondary with a three-unit train - Adam's MT-477 case - every entry the editor offers is a "not reached"
one, under a heading that says they were crossed.  Reading only.

**Verification request (no execution strictly needed):** the MT-477 fixture in `core/testATailPastASwitchIsAskedAbout`
shows the choices are all `isReached() == false` there; opening the editor's facing menu on that square shows the
heading.  **Suggested fix:** the same clause the dialog gained, in the heading, the hint and the pick prompt, in
all eight bundles.

### GUI-C4 - OB-272 changed what the text switch means; six comments still say None is the text switch turned off

| | |
|---|---|
| **Disposition** | Fixed - fd6341dd, and the siblings it missed in 4132d260 (DCN2-C1) |
| **Where** | `AutonomyEditorPanel.java:6752`, `:6767`, `:6786`, `:6798`, `:6860`, `:6889`; `LayoutEditor.java` `hideTextLabels` javadoc and the RGD-C3 paragraph in `toggleText` |

Since `359e5346` the text switch is on for Labels Only and off for everything else, and Control+L in the autonomy
editor calls `cycleCaptionMode` rather than `toggleText`.  The comments around it still describe FR-061's model:
`applyCaptionMode`'s javadoc - **"Text on for the three that say something, off for None"** and "a control naming
four exclusive options"; `textLabelsChanged` - "hidden in autonomy mode because None IS that switch turned off - but
Control+L still reaches it"; `turnTextLabelsOff` - "The way to say None, which is the editor's own text switch
turned off"; `lastNamedCaptionMode` - "for Control+L to come back to"; `LayoutEditor.hideTextLabels` - "None is
this"; and `toggleText`'s "without this Control+L emptied the diagram under a control still naming a caption".
Each is now false, and the code beside each is right.  `comments-self-contained` is the house rule these break.
Reading only.  The practical consequence: `textLabelsChanged`'s re-selection branch is now reached only from a
`toggleText` that `applyCaptionMode` itself drove, where `shown == labelsOnly` by construction - so it is dead in
the autonomy editor, and its javadoc is the only place that says what it is for.

### GUI-C5 - Highlight on Diagram matches an accessory by one address and no protocol, so a three-way's second decoder and a same-numbered DCC/MM2 pair answer wrongly

| | |
|---|---|
| **Disposition** | Fixed - e2223851 (claims d64023cb, red first): `LayoutDiagramComponent.answersToAccessoryAddress` - a three-way's second decoder, one address per protocol; the route editor passes protocols (`highlightAccessories`) |
| **Where** | `TrainControlUI.java:8860-8900` (`highlightAddresses`), `:8819` (`answersTo`) |

MT-462 fixed the comparison to `getLogicalAddress()` and the kind check.  Two cases remain, both reading only:

- **A three-way turnout** answers to two consecutive accessory addresses (`LayoutDiagramComponent.toSimpleString`
  prints `layout.switchThreeWayAddr` with `getAddress()` and `getAddress() + 1`), but the tile's logical address
  is the first only.  A route that commands only the second decoder - which is how a CS2 route sets the other
  diverging road - lights nothing, and if nothing else lights the button says `route.ui.infoNothingToHighlight`.
- **The protocol is not compared.**  `RouteCommand` carries `KEY_PROTOCOL` (`getProtocol()`), and a layout may have
  an MM2 turnout 5 and a DCC turnout 5, which are different decoders.  A route commanding DCC 5 lights both.

**Verification request (needs execution):** modelled on `regression/testTheRouteHighlightAsksWhatATileIs` (MT-462's
claim, `testAnAccessoryAddressLightsTheAccessory`) - a page with a three-way at 10 and a route commanding only accessory 11; assert the
three-way is lit.  Second half: an MM2 switch 5 and a DCC switch 5 on one page, a route commanding DCC 5; assert
one tile lit.

### GUI-C6 - small stale sentences and a dead argument

| | |
|---|---|
| **Disposition** | Fixed - fd6341dd: the catch comment, the dead argument, and the dead `hasRememberedBounds` removed |
| **Where** | `FacingPrompt.java:228-232`; `AutonomyViewerPanel.java:1446` |

- `FacingPrompt.ask`'s catch says a failure to show the dialog means *"the paste keeps the heading the walk worked
  out, which is what it did before this question existed"*.  Both callers read the resulting null as a dismissal:
  the paste returns without moving the train (`TrainControlUI.java:7227`), and since OB-282 the home door sets no
  home (`AutonomyEditorPanel.java:5109`).  The behaviour is the safe one; the sentence describes the other.
- `I18n.f("autosetup.ui.infoGraphExported", out.getName(), out.getParent())` passes a second argument that none of
  the eight values has a `{1}` for (the only `I18n.f` call in `src/` with more arguments than placeholders - see
  `gui-work/calls.py`).  The sentence says "in the folder that is about to open", so the folder is deliberately not
  named; the argument is dead and reads as if it were shown.
- `PositionAwareJFrame.hasRememberedBounds` (added since v2.8.1) has no caller in `src/` or `test/`, and its
  javadoc's "the same three keys loadWindowBounds requires" is not quite so (`_y` and `_state` are not asked, and
  the on-screen test that makes `loadWindowBounds` ignore a remembered position is not either).  Dead, so harmless;
  a future caller would be told "remembered" about a window that `loadWindowBounds` then declines to restore.

### GUI-C7 - the two questions a paste asks at a may-reverse square name the square by an internal copy name that already states a direction

| | |
|---|---|
| **Disposition** | Fixed - e2223851 (claim d64023cb, red first): both questions name the square by its base name |
| **Where** | `ArrivalSidePrompt.java:318` (`at.getName()`); `TrainControlUI.java:7224-7225` (`FacingPrompt.forPlacement(..., point.getName(), this)`); also reached from `LayoutRightclickAutonomyMenu.java:1156` |

A split square's Points are named by `AutonomyBuilder.nodeName` as *base* + " (" + heading of arrival +
(", reverse")" - "BottomMainB (eastbound)", "BottomMainB (eastbound, reverse)".  Both questions put to the operator
at a may-reverse square are exactly about direction, and both name the square by such a Point:

- `ArrivalSidePrompt.ask` - `I18n.f("autolayout.ui.askArrivalSide", at.getName())`: *"Which way did this train
  come in to BottomMainB (eastbound)?"*, with buttons "From the East (right)" / "From the West (left)";
- the paste's facing question - `FacingPrompt.forPlacement(canHold, facingAtTheLanding, point.getName(), this)`:
  *"Which way should the train at BottomMainB (eastbound, reverse) face?"*, with buttons "To the East (right)" / "To the West (left)".

And the Point is the one `getAutonomyPointForTile` returned *before* either answer - on an empty square "any copy
will do" (OB-270's own words) - so the direction in the name is arbitrary, and on a turning copy it is the opposite
of the way a train on it faces.  The other prompts on the same squares already name the square the operator's way:
the tail question through `session::baseNameOf`, the home-facing question through `describeTile(tile)`.

Reading only.  **Verification request (no execution needed beyond a probe):** on the frozen snapshot, print
`ui.getAutonomyPointForTile(<BottomMainB square>).getName()` - a name with "bound" in it confirms what the two
dialogs show.  **Suggested fix:** pass `getAutonomySession().baseNameOf(point.getName())` (as the tail question
does) to both.

### GUI-C8 - the "page is left out" label refuses with a dialog where the Edit item it imitates brings the open editor forward

| | |
|---|---|
| **Disposition** | Fixed - e2223851 (claim d64023cb, red first): the label and Fix Setup open the editor, which brings an open one forward |
| **Where** | `TrainControlUI.java:8738-8749` (`openAutonomyEditorIfItCan`) against `:5043` (`whyAutonomyEditorCannotOpen`) and `:5150-5153` (`openLayoutEditor`) |

Adam's ruling for the label (2026-09-19): *"just make it attempt to click the edit button if it's enabled"*, and
the javadoc says it does - *"Opens the autonomy editor if the Edit item would be enabled, and does nothing if it
would not ... Silent rather than complaining when the refusal bites."*

With an editor already open, the Edit item IS enabled - `whyAutonomyEditorCannotOpen` deliberately returns null
("An editor that is already open is not a refusal when there is a window to bring forward"), and `openLayoutEditor`
then calls `showOpenEditor()` (OB-058).  The label passes the same first test and then, inside its `invokeLater`,
asks `refuseWhileEditorOpen()` first - which is true exactly in that state and shows
`autosetup.ui.menuEditorOpen`, "Close the editor first - autonomy cannot be changed from here while it is open".
So the one state where the two differ, the label shows a dialog it promised not to show, telling the operator to
close the window the Edit item would have handed him.  The label is visible whenever the page is excluded,
whatever else is open (`AutonomyOverlayToggle.setPageExcluded`).

The banner's **Fix Setup** button (`TrainControlUI.java:8624-8631`, `refreshAutonomyPrompt`) has the same two
lines and the same result: "Close the editor first" where the Edit item would raise the editor that can fix it.

Reading only.  **Suggested fix:** drop the `refuseWhileEditorOpen()` line from both; `openAutonomyEditor(null)`
already brings an open editor forward.  (If it is there to stop an editor being switched to another page, the editor's own
switch already asks about unsaved work.)

## D - not defects

### GUI-D1 - the eight bundles: placeholders, arity, keys the forms ask for

| | |
|---|---|
| **Disposition** | Closed - checked clean |
| **Where** | `src/org/traincontrol/resources/messages*.properties`; `gui-work/bundles.py`, `calls.py` |

All 1,820 keys: every translation has the same `{n}` set as the English (no missing, no extra, no malformed
brace).  Every literal `I18n.t`/`I18n.f` call in `src/` resolves; no `I18n.t` is handed a value with a placeholder
or a doubled apostrophe; no `I18n.f` passes fewer arguments than its value asks for (one passes more - GUI-C6).
Every `bundle.getString("...")` in the generated form code resolves (the existing guards read only `I18n.*`
calls, so this was the unguarded half).  No key is asked for only from `test/` (the dead-key guard counts test
literals as asking).  No English value changed after 2026-09-19 without all seven translations changing in the
same commit; the only English rewordings since v2.8.1 that translations missed are the three in GUI-C1.

### GUI-D2 - the Exit/Entry Guard dialog's sixteen keys and their argument order

| | |
|---|---|
| **Disposition** | Closed - checked clean |
| **Where** | `AutonomyEditorPanel.java:454-499` (`Guard`), `:6599-6650` |

The keys the `Guard` enum passes to `I18n` by variable - which `testNothingAsksForAKeyThatIsNotThere` cannot see,
since it reads literal `I18n.t("...")` calls - are all present in all eight files.  The two families put the
station and the signal in the same slots for each call site (`set`/`added`: `{0}` station, `{1}` signal; `removed`:
`{0}` signal, `{1}` station; `cleared`: `{0}` station), so `removeGuardSignal`'s single argument order suits both.
Pairing an entry signal writes the setup and calls `refresh()` without `setupChanged()`; that is sound because the
item is built only `!menuOnly` (editor only), the editor rebuilds the running layout when it closes, and nothing
can be dispatched while it is open.

### GUI-D3 - the event-thread monitor census, against a call graph

| | |
|---|---|
| **Disposition** | Closed - checked clean today; one latent gap in the guard's list |
| **Where** | `test/regression/testNothingOnTheEventThreadTakesTheRailwaysMonitor.java`; `gui-work/monitor.py` |

Walking `Layout`'s own call graph from its 27 `synchronized` members and 4 `synchronized (this)` blocks finds eight
more members that reach the monitor: the five in `REACHES_THE_MONITOR` plus `checkForSlowerLoc`,
`hasAutonomousDestination`, `runLocomotive`, `executePathInternal`, `executeTimetableInternal` - none called from
`gui/` or `automationui/`.  Across classes, `loadReturnToHomeTimetable` reaches `getHomeStations` and
`getPossiblePaths` through `HomeStaging` and is not in the list either; its one caller (`requestReturnToHome`) is
on the worker.  So the census is complete for every door that exists, and would pass in silence a future
event-thread call to any of these six - the hand list is the weak half, as its own javadoc says.  No method
reference (`::getPossiblePaths` etc.) to a monitor member exists in `src/`, which the guard's `.name(` pattern
could not have seen.  The new event-thread work since 2026-09-19 - `unmeasuredTrackThatCouldBeReleased`,
`trainsWithNoLength`, `facingByPathFrom`, `placeableFacings`, `highlightAddresses` - takes no `Layout` monitor.

### GUI-D4 - dialogs off the event thread, and dismissals read as answers

| | |
|---|---|
| **Disposition** | Closed - checked clean |
| **Where** | `gui-work/offedt.py`; the prompts |

A scan of every `new Thread`/`submit`/`execute` lambda body in `gui/` for `JOptionPane.show*`, `new JDialog` and
`setVisible(true)` finds none (the two hits are `locIcon.setVisible` in the image loader, below).  Every prompt
touched since 2026-09-19 tells a closed window from an answer: `TailCrossedPrompt.reply` (closed = no answer, OK with
nothing selected = Not Known, a "not reached" road never preselected), `FacingPrompt.ask` (closed = null, read as a
dismissal by both callers), the walk's `dialogAnswer` (closed = Stop), Segment Length's three-button dialog (closed
= no change), the function dialog (closed takes the Cancel branch and undoes Copy Customizations), and the route
editor's GUX-B1 questions (anything but Yes returns).  All are raised on the event thread or marshalled there with
`invokeAndWait` behind an `isEventDispatchThread` check.

Pre-existing and not new since v2.8.1: `locIcon.setIcon/setVisible` from the `ImageLoaderLoc` pool
(`TrainControlUI.java:12755-12792`, FR-054 added the placeholder branch in the same shape), `Forward/Backward.setSelected`
from the threads in `backwardLoc`/`forwardLoc`, and `backupDataMenuItem.setEnabled` inside the backup worker.  Swing
tolerates these in practice; noted so they are not rediscovered as new.

### GUI-D5 - the paste's facing question offers every copy's heading rather than the placeable ones

| | |
|---|---|
| **Disposition** | Closed - not reachable as far as the builder's copies go |
| **Where** | `TrainControlUI.java:7217-7229` |

OB-270 filtered the unasked branch through `placeableFacings`, and `FacingPrompt.forHome` gets `homeFacingsFor`
(arrival-allowed copies), but the asked branch still offers `facingsFor(aimed).values()` and records the answer
unfiltered (`:8194`).  An answer no destination copy holds would leave the train on another copy with the answer
recorded - OB-270's shape.  It is asked only on a may-turn square, and there `AutonomyBuilder` emits a plain and a
turning copy per arrival side, facing opposite ways, and a copy is a destination unless its arrival side is barred -
so every heading is held by some destination copy unless every arrival side is barred, when the paste is refused
earlier.  Worth the same filter for symmetry; not a defect today.

### GUI-D6 - MT-467's "the lock says the station carried it"

| | |
|---|---|
| **Disposition** | Closed - checked clean |
| **Where** | `TrainControlUI.java:19757`, `:20437`, `:21818`, `:21877`; `MarklinControlStation.java:1496-1555`, `:2062-2086` |

`setLocked(true)` is written only by the station import (every route the station sent) and carried across a
rename (`wasLocked`, X8-B5); the import first unlocks everything, so a route the station stopped sending loses it.
The single toggle (`TrainControlUI.java:21877`), the bulk batch (`:21818`), delete (`:19757`) and the id change
(`:20437`) all read it before the write that replaces the route object.

One qualification to the comment's *"it is kept with the route"*: it is kept for the session only.  The database
saves routes as `MarklinSimpleComponent`, which carries no lock, so after a restart every route reads unlocked
until the first sync re-imports them.  In that window a station route toggles or deletes without the sync - but
the window exists only while no sync has happened, which is when the station is not there to sync with, so
nothing is lost.  Recorded because the sentence reads as a property of the saved route.

## What this pass did not cover

- **The GUI code written between v2.8.1 and 2026-09-19 in depth.**  It had the UIX/GUX/SVB round (50 findings, all
  closed); this pass relied on it and went back only where a later change leaned on older code (the gate's doors,
  `GraphLocAssign`, the prompts, the tile-menu wiring, `PositionAwareJFrame`).  `LayoutEditor` (7,300 new lines),
  `LocIconCropDialog`, `StationCaption`, `LoadingSpinner`, `StartupSplash`, `RowIcons`, `UsageHistogram` and the
  `.form` files were not read.
- **`automationui/`** beyond what the interface calls (`AutonomySession` changed by 785 lines since 2026-09-19);
  the facing, length and tail rules there are the model's, and were read only to settle what a GUI door writes.
- **Anything that needs a running window**: layout, fonts and colours against `docs/UI-standards.md` were not
  checked by eye, focus order was read rather than tabbed through, and no dialog was opened.  Every finding above
  with "needs execution" in its verification request rests on reading until that is run.
- **Keyboard shortcuts** were not re-audited as a set; `regression/testNoTwoShortcutsShareAKey` holds collisions,
  and the one shortcut changed since 2026-09-19 (Control+L in the autonomy editor) was read (GUI-C4).
- **Swing off the event thread** was scanned heuristically (lambda bodies passed to `new Thread`, `submit`,
  `execute`); work done off the thread inside a method a worker merely calls is not seen by that scan.
