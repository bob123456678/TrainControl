# User interface - the commits of 2026-09-23 and 2026-09-24

**Status:** closed

**Prefix:** `TDU`

**Reviewed:** branch `autonomy-diagram-r0` at `fc1ce476`, 2026-09-24.  Range: `git log --since="2026-09-23 00:00"` (218 commits, `12d74ad5` .. `fc1ce476`); lane: the 64 commits touching `src/org/traincontrol/gui` and `src/org/traincontrol/resources`, the four commits to `docs/manual-tests/triage.py`, and the tests that claim them.

**Method:** Read `docs/reviews/FANOUT.md` and `docs/reviews/README.md`, then `git show` of every lane commit, and at HEAD the code around each change: the two hand doors (`AutoLocomotiveStatus.locAvailPathsMouseClicked`, `LayoutRightclickAutonomyMenu.destinationItem`) against the three other dispatch doors (Start, Execute Timetable, Return Home); every door that calls `TailCrossedPrompt.askAfterPlacement` (the paste in `TrainControlUI.rememberPlacement`, `LayoutRightclickAutonomyMenu.placeFacing`, `GraphLocAssign.commitAndRecord` from both menus) and the new `DiagramPick`; the four placement doors against OB-270 / GUI-B1 / OB-284's facing rule; the grey and orange marks (`LayoutLabel.theRoadToFade`, `routesOf`, `TrainControlUI.blockedTrack`, `AutonomySession.routesBlockedByStandingTrains`); the Bulk Tools walks; the MT-467 sync sweep (every `isLocalRouteId` and `isLocked` reader); the OB-254 removal (method names diffed across `a4674c9b` in memory - only the eight JSON-tab handlers went); the Echo preference down to `MarklinControlStation.exec`; `LayoutEditor.orientationThatJoins`.  The message bundles were checked by read-only Python piped through stdin: all eight have the same 1825 keys, every file is pure ASCII, no placeholder set differs between languages, no value with a `{n}` holds a straight apostrophe in any language, every `I18n.f` with a literal key passes at least as many arguments as its message has placeholders (555 calls), and none of the 26 keys removed in the range is still asked for.  Claims read: `testAHandSendIsRefusedWhileTheSetupIsBroken`, `testTheTailIsPickedOnTheDiagram`, `testATrainDoesNotRunIntoItsOwnTail`, `testATailPastASwitchIsAskedAbout` (head), `testTheEditorSaysWhatItsToolsDo`.  The frozen railway was not needed; `cs2_sample_layout/` was not opened.  Nothing was run, compiled or started; every finding that depends on behaviour says what execution would settle it.  One slip to record: a shell redirection meant for comparison wrote a scratch file `nul_before` into `%TEMP%` (outside the repository and the scratchpad); it was deleted a minute later and nothing read it.

## A - high

None found.

## B - medium

### TDU-B1 - MT-263 refuses a hand send while the setup has errors at two of the four doors that move trains outside Start; Execute Timetable and Return Home still run over the broken setup

| | |
|---|---|
| **Disposition** | Fixed - claim 664fe577 (red first), fix 492ad392: Execute Timetable and Return Home ask the setup question before they grey or dispatch.  Mutation D1 red.  Extends MT-263 on the rule's own stated reason; Adam can reverse it. |
| **Where** | `TrainControlUI.java:23817-23841` (`whyAHandSendIsRefused`), asked at `AutoLocomotiveStatus.java:1086` and `LayoutRightclickAutonomyMenu.java:1317` (`cbbca27d`); not asked at `TrainControlUI.java:26624-26801` (`executeTimetableActionPerformed`) or `:23915-24130` (`requestReturnToHome`, reached from the Return Home button and `HomeLocomotiveMenu.java:63` on both right-click menus) |

The rule `cbbca27d` wrote, in its own javadoc: *"A hand send runs over the railway the same setup built, so it is refused on the question Start is refused on - `autonomyHasErrors`"*.  That reasoning is not about the hand: a timetable run and a Return Home run dispatch over the same railway, through the same `executePath`, and are started by a button with nobody asking the setup.  `executeTimetableActionPerformed` asks the editor, the power, `isAutonomyBusy`, an empty timetable, the conditional-route warning and the start squares - never `autonomyHasErrors`.  `requestReturnToHome` asks the editor, `isAutonomyBusy` and the power.  `TrainControlUI.java:6082-6092` already lists the five dispatch doors for the Atomic Routes gate, and `ui.testNonAtomicRoutesNeedTheirLengths.testEveryDispatchDoorAsksTheGate` holds that gate at all five; MT-263's refusal is at two.

**On the railway.**  MT-263's own reproduction (untick Exclude Page on 4 - Combined, so the graph will not build) leaves the running layout as it was last built.  Start refuses, both hand doors now refuse - and Return Home, or Execute Timetable with a captured timetable, drives trains over the stale graph the operator has just been told "cannot be used yet".  Mitigations: Adam's report named the two hand doors (*"via both the track diagram viewer and the autonomy tab"*), so whether the two run doors belong under the rule is his to say; Return Home needs a plan to succeed and Execute Timetable needs captured entries.  Graded B because the rule's own stated reason covers them and the hazard (moving trains over a setup whose graph no longer describes the diagram) is the one Start is refused for.

**Verification request.**  Source claim, in `testAHandSendIsRefusedWhileTheSetupIsBroken`: extend `door(...)` to `executeTimetableActionPerformed(` and `requestReturnToHome(`, asking that each reaches `autonomyHasErrors` (directly or through `whyAHandSendIsRefused`) before `executeTimetable(` / `loadReturnToHomeTimetable(`.  **Proves it:** red today at both.  **Refutes it:** Adam rules that only a hand send is refused - then say so in both methods and in behaviour.md.  Behavioural (railway, MT-263's steps 1-3): with the graph broken, press Return Home - trains move (proves) or the setup's sentence appears (refutes).

### TDU-B2 - FR-100 made the tail question non-modal: while it waits on the diagram the whole window is live, and the paste door reads its landing back from instance fields a second placement overwrites

| | |
|---|---|
| **Disposition** | Fixed - claim ce2c908f (red first), fix fc574093: the paste reads its landing into locals before the question.  Mutation F2 red. |
| **Where** | `TailCrossedPrompt.java:606-645` (`DiagramPick.ask`: a `SecondaryLoop` on the event thread), `:676` (`new JDialog(ui, ..., false)` - not modal); `TrainControlUI.java:8069-8177` (`rememberPlacement`), fields `:7359-7389`; `26f5f6ba` |

Before `26f5f6ba` the tail question was a modal `JOptionPane`, so nothing else in the window could happen between a placement door asking it and writing the answer.  `DiagramPick.ask` now pumps events in a secondary loop under a non-modal dialog - by design, so the diagram can be clicked - and the commit message says *"so the three placement doors carry on unchanged"*.  They carry on unchanged, but they were written for a modal wait.

The paste door is the one that breaks.  Its state for the landing is in fields, set before the move and read in `rememberPlacement` on both sides of the question:

```java
String tail = tailAtTheLanding;                                   // read before - safe
...
TailCrossedPrompt.Answer answer = TailCrossedPrompt.askAfterPlacement(...);   // waits, window live
java.util.List<Edge> road = answer.roadToRecord(roadBeforeTheLanding,
    tile.equals(squareBeforeTheLanding), sideBeforeTheLanding, tail);          // read after
...
session.setFacing(tile, facingChosenAtTheLanding != null ? facingChosenAtTheLanding
    : AutonomySession.facingAfterAPaste(placeableFacings(tile), facingAtTheLanding, point.getName()));
```

While the question waits, the operator can click back into the main window, point at another station and press Control+V (the only way into `rememberPlacement`; `locomotiveGestureOnDiagram` refuses only while autonomy is busy or an editor is open).  That second paste runs to completion inside the loop - its own tail question falls back to the modal list, because `DiagramPick.of` returns null while `armed != null` - and rewrites `facingAtTheLanding`, `facingChosenAtTheLanding`, `roadBeforeTheLanding`, `sideBeforeTheLanding` and `squareBeforeTheLanding`.  When the first question is answered, the first train's setup facing is written from the second train's heading (or cleared, where its square cannot hold it), and `session.save()` puts that on disk.  The live train stands on the right copy; the setup now says otherwise.

**On the railway.**  The same state GUI-B1 was filed for: the setup and the copy disagree, *"until the next load turned it round"* - and a train the model has turned is dispatched along a route locked one way while its decoder drives the other.  Mitigated: exit's capture writes the railway's copy back into the setup, and the rebuild's put-back follows the railway (TDY3-A1), so it survives only to a start that follows a crash or a kill.  The same wait also leaves Start, Return Home, both hand doors, Remove and Delete live while a tail question is unanswered (the train is then treated as Not known, which the operator could have chosen anyway); and the right-click and dialog doors, which keep their state in locals, write their answer and save onto whatever the square holds by then.  Graded B; A if a turned-round train can be reached without the crash.

**Verification request.**  In `testTheTailIsPickedOnTheDiagram` (display needed): paste train 1 at Tunnel (southbound) through the window's door on the event thread (`invokeLater`), so the pick is armed; paste train 2 at a may-turn square with a facing answered by `FacingPrompt`'s test seam; then click TunnelPre's tile.  **Proves it:** the setup's facing at Tunnel equals train 2's heading (or is null) while the railway has train 1 on the other copy.  **Refutes it:** the second paste is refused, or the facing at Tunnel is train 1's.  Suggested fix: carry the landing in locals through `rememberPlacement` (it is one call), or refuse a placement while `armed != null`.

### TDU-B3 - a double-click on a lit sensor answers the tail question and then flips the sensor, which is the faked occupancy change `takesTheClick` exists to prevent

| | |
|---|---|
| **Disposition** | Fixed - claim ce2c908f (red first), fix fc574093: the rest of a double-click on the answering sensor is the question's.  Mutation F1 red. |
| **Where** | `LayoutLabel.java:327-335`; `TailCrossedPrompt.java:533-538` (`takesTheClick`), `:704-734` (`clicked`), `:736-749` (`finish`: `if (armed == this) armed = null`); `LayoutDiagramComponent.java:168-178`; `MarklinFeedback.java:120-153` |

The s88 tile's listener:

```java
if (TailCrossedPrompt.takesTheClick(LayoutLabel.this)) return;
component.execSwitching();
```

The first click of a double-click lands on a lit sensor, `clicked` calls `finish`, and `finish` sets `armed = null` before the second `MOUSE_CLICKED` (click count 2) arrives.  For that one `takesTheClick` answers false, and `execSwitching` flips the feedback: `setState(!isSet())`, which `MarklinFeedback.setState` announces through `network.feedbackChanged` exactly as a wired sensor change.  The FR-100 comment says the check is *"asked before the sensor is flipped, so answering the question does not also fake an occupancy change"* - true for a single click only.  The prompt tells the operator to *"Click it on the diagram"*, and double-clicking a square is a common reflex.

**On the railway.**  The sensor the operator is pointing at is one the train's tail has just passed.  Flipped, it reads occupied or free against the real railway until the real sensor next changes; anything watching sensors - an armed s88-conditional route, and autonomy once started - acts on it.  No autonomy runs during a placement (every placement door refuses while busy), which is the mitigation; conditional routes do not wait for autonomy.

**Verification request.**  In `testTheTailIsPickedOnTheDiagram.testAClickOnALitSensorAnswersTheQuestion`: dispatch two `MOUSE_CLICKED` events on TunnelPre's label (click count 1, then 2) and read TunnelPre's feedback state before and after.  **Proves it:** the state has changed.  **Refutes it:** unchanged.  Suggested fix: let the pick swallow clicks for a moment after it finishes, or have `finish` leave a short-lived guard that `takesTheClick` still honours.

### TDU-B4 - the right-click "Place {0}" item stands a train on a random copy of the square, the one placement door that OB-270, GUI-B1 and OB-284's facing rule never reached

| | |
|---|---|
| **Disposition** | Fixed - OB-296, Adam, 2026-09-24: *"Yes, keep the train's heading."*  Claim 7dc22256, fix f17f5a5c.  MT-581. |
| **Where** | `LayoutRightclickAutonomyMenu.java:683-718`, `:1078-1086` (`placeSomewhereLegal`: `usable.get(new java.util.Random().nextInt(usable.size()))`); against `TrainControlUI.java:7235-7247` (paste), `GraphLocAssign.java:239-253` (dialog), `AutonomyEditorPanel.java:5470-5471` (editor Place) |

Three placement doors now keep the train's heading over the copies it could leave by (`departableFacingsFor`, OB-284) and stand it on the copy that faces that way (OB-270: *"no train should inadvertently change direction when pasted"*).  The fourth - the diagram's right-click **Place {0}** for the active locomotive, which moves it from wherever it stands - still takes one of `placeableCopies()` at random and records that copy's heading.  behaviour.md already calls this superseded: *"Adam's 2026-09-06 wording for that arm was 'pick randomly from the allowed departure destinations' ... it is superseded by his ruling of 2026-09-12 - 'as long as the direction isnt flipped' ... a placement that answers differently each time it is repeated is drift"*.

**On the railway.**  The same train placed the same way on the same station faces either way, half the time the other way round from how it was driving; the record and the copy agree, so nothing later corrects it, and a train whose model heading is opposite to its physical one is sent the wrong way.  Mitigated: the heading is drawn on the diagram and can be turned from the same menu.  **Verification (no execution needed for the rule; to see it):** on the frozen railway, place 75 407 DB with "Place 75 407 DB" at BottomMainA ten times from the same starting square and record `facingOf` - two answers prove it.  Adam's call whether this door should follow the paste's rule or keep choosing for the operator.

## C - low

### TDU-C1 - the autonomy editor's Place / Edit Locomotive... door now puts the tail question on the main window's diagram, where the editor's own lit squares cannot answer it

| | |
|---|---|
| **Disposition** | Fixed - claim ce2c908f (red first), fix fc574093: with an editor window open the question is the list.  Mutation F3 red. |
| **Where** | `AutonomyEditorPanel.java:5288-5317` (`GraphLocAssign(parentWindow(), ...)`, then `commitAndRecord`); `GraphLocAssign.java:311-313` (`askAfterPlacement(..., edit.parent, ...)`); `TailCrossedPrompt.java:574-603` (`DiagramPick.of`: `parent instanceof TrainControlUI`); `LayoutGrid.java:1868-1881` (every grid with a `ui` registers its labels, the editor's included); `LayoutLabel.java:304-306` (the s88 listener only when `!edit`) |

From the editor, `edit.parent` is the main window, so `DiagramPick.of` accepts it; `putsTheQuestionOnTheDiagram` asks whether the sensor squares are drawn, and the registry answers with the editor's labels as well as the main window's.  The lit wash lands on both.  A click on the editor's lit square goes to the editor's own mouse handling - editor labels are built with `edit = true` and never get the listener that calls `takesTheClick` - so it answers nothing; the prompt window is owned by the main window and placed at its top (`:698`), which the editor may cover.  The operator is left with Not known or Cancel, i.e. the tail unknown, which is where MT-477 started.  The editor already has its own pick for exactly this (`armTailPick`, `:3948`).  Reading only; the registry's handling of edit labels is the part to confirm.

**Verification request.**  Display: open the autonomy editor on 1 - Main of the frozen railway, use Place Locomotive... to put a five-unit train at Tunnel arriving from the north (two choices, both on the page), and click TunnelPre in the editor.  **Proves it:** the question is still waiting.  **Refutes it:** the click answers.  Suggested fix: from the editor, ask with the list (or the editor's pick), e.g. pass the editor window as `parent`.

### TDU-C2 - FR-100's claim drives only the off-thread latch; every real placement door asks on the event thread, through the secondary loop nothing tests

| | |
|---|---|
| **Disposition** | Fixed - pin testAQuestionAskedOnTheEventThreadIsAnsweredByAClick in ce2c908f (green, the path works); mutation F4 (the loop not exited) red. |
| **Where** | `test/regression/testTheTailIsPickedOnTheDiagram.java` (javadoc: *"asked the way a placement door asks it - owned by the main window - off the event thread"*; `Executors.newSingleThreadExecutor()`); `TailCrossedPrompt.java:610-622` |

The paste, the right-click Place and the dialog all run on the event thread, so in the application `DiagramPick.ask` always takes the `SecondaryLoop` branch.  The claim submits `askAfterPlacement` from an executor, which takes the `CountDownLatch` branch.  A defect confined to the loop branch - `loop.exit()` not reached, `out[0]` read before it is written, a nested modal dialog stranding the loop - passes this class.  The javadoc's "the way a placement door asks it" is not the way.  **Verification request:** add a claim that calls `askAfterPlacement` inside `invokeLater` (collecting the answer into an `AtomicReference`), clicks the label from the test thread, and waits on the reference.  **Proves the gap:** a mutation that removes `loop.exit()` from the arm callback leaves today's class green.

### TDU-C3 - MT-263's claim pins the static sentence and a substring of the method the doors call; turning the doors' method off survives both tests

| | |
|---|---|
| **Disposition** | Fixed - pin testABrokenSetupIsRefusedAndAMendedOneIsNot on a real window in 664fe577; mutation D2 (guard inverted) red. |
| **Where** | `TrainControlUI.java:23817-23824`; `test/regression/testAHandSendIsRefusedWhileTheSetupIsBroken.java` (`testTheRefusalSaysWhatIsWrongWithTheSetup` reflects the static `whyAHandSendIsRefused(int, int)`; `testBothHandDoorsAskItFirst` checks `body.contains("autonomyHasErrors()")`) |

The doors call the instance `whyAHandSendIsRefused()`.  Its guard is `if (!autonomyHasErrors()) return null;`.  Invert it (`if (autonomyHasErrors()) return null;`), or return null unconditionally after the first line: the static still words everything correctly, and the body still contains the substring, so both tests stay green while no hand send is ever refused.  The javadoc's *"make the rule answer nothing for a broken setup and the first does"* is true only of the static.  **Verification request:** run the two mutations above against the class; both survive (proves).  Suggested fix: a claim on a window with a session whose `hasErrors()` is true (the `testErrorsStopTheSetupRunning` fixture has one) that asks the instance method for non-null, and with the errors cleared, for null.

### TDU-C4 - OB-294's sentence at the two hand doors has no claim; either door's block can be deleted with the battery green

| | |
|---|---|
| **Disposition** | Fixed - pin testBothHandDoorsSayWhenATrainWouldMeetItsOwnTail in 664fe577; mutation D3 red. |
| **Where** | `AutoLocomotiveStatus.java:1162-1171`; `LayoutRightclickAutonomyMenu.java:1375-1383` (`121b1c3c`); `test/core/testATrainDoesNotRunIntoItsOwnTail.java` (the only test naming `whyItWouldMeetItsOwnTail`) |

`121b1c3c` says the refusal is asked *"with its sentence at both hand doors"*; `c3b8f1e7`'s claims drive `Layout` (`debugPath`, `isPathClear`, Return Home) and never a door.  Removed from a door, the send still fails - `isPathClear` refuses at dispatch - but with *"check the log"* instead of the sentence naming the longest train that goes, which is what Adam asked the door to say (MT-262's rule).  The berth and length refusals at the same doors are unpinned the same way, so this is the pattern, not a slip - but it is a fix shipped without a claim.  **Verification request:** delete either door's `ownTail` block and run `core.testATrainDoesNotRunIntoItsOwnTail` - green (proves).  A source claim in the shape of `testBothHandDoorsAskItFirst` (each door asks `whyItWouldMeetItsOwnTail(` after `whyABerthCannotHoldIt(` and before `ManualReversalPrompt.forJourney(`) would hold all three.

### TDU-C5 - MT-477's "the roads part" treats an unmeasured rail as covering nothing, so any junction with one measured and one unmeasured rail back asks every train, however short

| | |
|---|---|
| **Disposition** | Not a defect - on a built graph a rail's length includes the square it arrives at, so a train short enough for its own square has that square measured, neither rail is unmeasured, and both cover the same square.  Only a hand-written rail with no places could differ. |
| **Where** | `TailCrossedPrompt.java:927-941` (`tailsPart`), `:983-987` (`reachOf`: `if (rail.getLength() <= 0) return covered;` - empty), `:814`, `:824`, `:844-858` (`195aa1f1`) |

`tailsPart` compares the sets of places each rail's tail would cover.  A measured rail's set always holds the standing square (`reachOf` spends from the end nearest the train, and the arrival square is the last place id); an unmeasured rail's set is empty.  Two different sets, so `parts` is true for any train length of 1 or more, `walk` counts a fork, and `wouldAsk` puts the question.  Both answers then offer "towards ..." choices (`beyond = -1` for the unmeasured hop, `offerShort && left > 0`).  For a train that fits on its own square every answer covers the same track - the standing square, claimed whichever road it came along - which is the case the class comment says is not asked (*"A tail that ends before the rails part covers the same squares whichever road it is on, and is not asked about"*).  Nothing is driven wrongly; a question is put that means nothing, on a partly measured railway at every such junction.  Reading only.

**Verification request.**  `core.testTheTailCrossedQuestion`: two rails into one station copy from the same side, one with lengths and one with none; a one-unit train, standing square measured.  **Proves it:** `wouldAsk` is true.  **Refutes it:** false.  Suggested fix: have `reachOf` return the standing square (or the shared tail of `lastSharedPlace`) for an unmeasured rail, or compare only places past the standing square.

### TDU-C6 - a deliberate 0 is "not missing" to the editor's walk and its Unmeasured Track display, but the Atomic Routes refusal still names that track and says "Measure them", with nothing in the editor offering it

| | |
|---|---|
| **Disposition** | Fixed - Adam, 2026-09-24: *"0 lengths count as measures, so non-atomic should be allowed."*  `Edge.isMeasured` at the Atomic Routes gate, the release escape and the route in.  Claims 7dc22256, fix f17f5a5c. |
| **Where** | `Layout.java:9523-9534` (`unmeasuredTrackThatCouldBeReleased`: `if (edge.getLength() > 0) continue;`, no `isPlaceAnswered`); `messages.properties:172` (`autolayout.errorNonAtomicNeedsLengths`); against `AutonomyEditorPanel.java:9894` (`needsALength`) and `87361089` (*"an answered 0 is not listed as missing"*) |

OB-274 kept 0 as an answer with *"same meaning to the model"*, and `87361089` took answered zeros off every list of what still needs measuring.  The Atomic Routes gate was not in that sweep: an edge whose squares are all answered 0 still has length 0, is counted, and the checkbox refuses with *"Atomic Routes cannot be switched off while track autonomy runs over has no length, {0} in all ({1}) ... Measure them and you can switch it off again."*  Then Mass Assign Lengths says *"Every stretch of track, every switch and every crossing on this page already has a length"* and Unmeasured Track shows nothing.  The gate is right to refuse - a zero-length edge is released under a train - so this is not over-refusal; it is `error-must-have-a-remedy`: the remedy named has no door, and the editor contradicts the refusal.  The case is Adam's own (*"for adjacent tracks"*).  **Verification request:** frozen railway, answer one stretch 0 with Segment Length, untick Atomic Routes - the refusal names that stretch (proves) and Mass Assign Lengths offers nothing.  Options: say in the refusal that answered-0 track counts as unmeasured and must be given a length for non-atomic running, or have Unmeasured Track mark such track when Atomic Routes is off.

### TDU-C7 - the train walk's second mode (MT-533) reuses sentences that are false in it

| | |
|---|---|
| **Disposition** | Fixed - claim 26bdd47b (red first), fix 858317f2: the tooltip with no train placed, and the every-train mode's answer to 0.  Mutation W1 red. |
| **Where** | `AutonomyEditorPanel.java:10045-10115` (`massAssignTrainLengths`, `known` mode), `:2340-2342` (the item's tooltip); `messages.properties:1442`, `:1443` |

With no train missing a length the walk now goes through every train, each prompt saying *"is {4} long.  Enter a new length, or Skip to keep it"*.  Typing 0 there (or 41) gets `errorTrainLengthOutOfRange`: *"0 means the train has no length, which is what it has now"* - it has one; the prompt just said so.  And when no locomotive is placed at all, the item's tooltip is `infoEveryTrainHasALength` - *"... already has a train length - this goes through them all, so any can be changed"* - while a click answers `infoNoTrainToMeasure`, *"No locomotive is placed for autonomy to run"*.  Cosmetic; the walk writes nothing wrong.  Suggested fix: a second out-of-range sentence for the known mode, and the tooltip chosen on `trainLengths().isEmpty()` as the click chooses.

### TDU-C8 - GUI-A1 moved the Atomic Routes gate after Execute Timetable's refusals; Start and Return Home still switch the railway to atomic on a press they then refuse

| | |
|---|---|
| **Disposition** | Fixed - claim 664fe577 (red first), fix 492ad392: Return Home asks the gate after its refusals, and Start on its worker after its own, on the event thread. |
| **Where** | `0be2bbe4`; `TrainControlUI.java:26842` (Start: the gate, then power, the conditional-route warning, no locomotives and busy on the worker at `:26859-26916`); `:23924` (Return Home: the gate, then `isAutonomyBusy` at `:23928` and power at `:23939`) |

GUI-A1's rule, in the Execute Timetable comment: *"After every refusal above, not before them: a press refused as 'wait for active locomotives to stop' switched the running railway to atomic on its way to being refused, and a refused press should change nothing."*  The two sibling doors keep the old order.  Return Home refused as "locomotives running" (from a right-click menu opened before autonomy started, `HomeLocomotiveMenu.java:57`) switches a non-atomic run to atomic mid-run - the trigger of GUI-A1's own A, now defused by its `Layout` half.  What is left is a setting changed by a refused press, in the safe direction, with a log line.  **Verification (reading suffices):** the order is visible at the lines above.  Suggested fix: move each call below the refusals, as `0be2bbe4` did.

### TDU-C9 - comments the range left describing the code before it

| | |
|---|---|
| **Disposition** | Fixed - b62f1976, and a fifth: tilesBlockedByStandingTrains' javadoc said the same. |
| **Where** | listed below |

- `TrainControlUI.java:7547-7561` (`blockedTrack`) says the grey *"is the whole edge - which is what `Layout.edgesCoveredByStandingTrains` actually refuses"* and, from OB-208, *"this is the whole of every covered edge again"*; `LayoutLabel.java:1788-1791` says the same.  Since OB-280 (`1a76d9bf`) it is the places standing trains claim (`routesBlockedByStandingTrains`' own javadoc says why the whole edge was wrong).
- `AutonomyEditorPanel.java:9864-9865` (`massAssignLengths`): *"0 is refused with a sentence saying why - it is the same as no length at all - and asked again"*.  OB-274 (`65e7fc73`) removed that refusal; 0 is an answer.
- `AutonomyEditorPanel.java:3863-3864` (`appendTailCrossed`): *"Nothing is added unless `wouldAsk` says ... the tail has crossed a sensor on at least one of them"*.  Since MT-477 (`195aa1f1`) it is also asked where the tail has passed the switch and reached no sensor.
- `TrainControlUI.java:7413-7415` (`copyFacing`): *"The doors only ever ask it for a facing such a copy holds - `placeableFacings` - so for them it never falls back to a barred copy."*  Since OB-284 (`94d26378`) `placeableFacings` returns the departable copies, and falling back to a barred copy is exactly what the paste now does (`moveLocomotive(..., true)`).  The method's name now says the opposite of its javadoc.

Nothing behaves differently; each is a comment a reader would trust.  Reading only.

### TDU-C10 - behaviour.md does not yet describe the range's UI rules, and names two walks by their old labels

| | |
|---|---|
| **Disposition** | Fixed - behaviour.md (this round's documents commit): OB-294, MT-263 with the run doors, FR-100, the forty-unit range and the renamed items. |
| **Where** | `docs/reference/behaviour.md:1010-1018` |

Bulk Tools: *"Mass Assign Train Lengths ... walks every train autonomy would run that has no length ... a length is 1 to 20"* - MT-533 made it never greyed and walk every train when none is missing, and `ea8e4ae4` made the range 1 to 40; the items are now "Mass Assign Locomotive Train Lengths (N missing)", "Mass Assign Station Max Train Lengths" and "Clear All Station Max Train Lengths".  No section states MT-263 (a hand send is refused while the setup has errors), OB-294 (a train is not sent round a loop into its own tail; searched for "own tail", "OB-294", "round a loop"), or FR-100 (the tail question on the diagram, the list only for a choice on another page).  The project's rule is that behaviour.md holds the rule and comments cite it.  Reading only.

### TDU-C11 - FR-102's claim reaches only the fallback arm of `whyWaitsOn`

| | |
|---|---|
| **Disposition** | Fixed - pin testWhyNotMovingFollowsTheRunningRailway in a961695e; mutations P3 (setup read while a railway runs) and P4 (outline kept after the click) red. |
| **Where** | `AutonomyEditorPanel.java:7136-7156` (`whyWaitsOn`); `test/regression/testTheEditorSaysWhatItsToolsDo.java` (`testWhyNotMovingOutlinesTheTrains`) |

The javadoc: *"The train as the running railway has it where there is one - a run moves trains the setup has not been told about - and as the setup places it otherwise."*  The claim builds `new AutonomyEditorPanel(session, "main", () -> { })`, which never calls `setRunningLayoutSource`, so `runningLayout` is null and only `session.getLocomotiveNameAt(tile)` is exercised.  Mutating the railway arm - returning true for every Point, or always reading the setup - survives.  **Verification request:** run those two mutations against the class (survive = proves); a second case with a running layout whose train stands where the setup does not have it would close it.

## D - checked and clean

### TDU-D1 - OB-294 at the hand doors asks the railway's own method, in the same place at both

| | |
|---|---|
| **Disposition** | Checked - clean. |


`AutoLocomotiveStatus.java:1164` and `LayoutRightclickAutonomyMenu.java:1376` both call `Layout.whyItWouldMeetItsOwnTail(path, loc)` - the instance form `isPathClear` asks at `Layout.java:2862` and why-not-moving at `:5648` - after the power, setup, length and berth refusals and before the reversal question.  Not `synchronized`, as `isPathClear` is not (OB-192); it reads `Layout.points` (a `HashMap` structurally changed only when a layout is built).  Return Home's planner asks the static form with the body `bodyOfATrainAt` gives it (`HomeStaging.java:1138`, `:1322`).  The message's placeholders match the call (`{0}` name, `{1}` place, `{2}` way round, `{3}` length) in all eight bundles.

### TDU-D2 - MT-263's two doors ask in the right order

| | |
|---|---|
| **Disposition** | Checked - clean. |


Power, then `whyAHandSendIsRefused`, then everything else, at both doors; nothing is asked or dispatched before it.  The sentences are the setup's (`errorCannotBuildDetail` / `...One`), never Start's.  (That the destination items stay offered while the setup has errors, where Start's item greys, is what Adam asked for - *"which should throw an error instead"* - so not raised under the affordance rule.)

### TDU-D3 - the MT-467 sync sweep is complete

| | |
|---|---|
| **Disposition** | Checked - clean. |


Every door that decided a Central Station sync by `isLocalRouteId` on an existing route now reads `isLocked()` before the write (`TrainControlUI.java` delete `:19603`, renumber's old end `:20286`, bulk toggle `:21667`, single toggle `:21726`).  The two remaining `isLocalRouteId` callers ask about a NEW id - the route editor's save of a new route (`RouteEditorFrame.java:3392`) and renumber's new end - where the id range is the right question (collision with ids the station may have issued).

### TDU-D4 - the message bundles

| | |
|---|---|
| **Disposition** | Checked - clean. |


1825 keys in each of the eight bundles, identical key sets; every file pure ASCII; no placeholder set differs between languages; no straight apostrophe in any value that goes through `MessageFormat` with a placeholder, in any language; 555 `I18n.f` calls with literal keys all pass enough arguments; none of the 26 keys removed in the range (`cf7b43dc`, `65e7fc73`) is referenced; of the 66 keys added or changed in English, the only value left identical to English is `ui.main.toolbar.debug` ("Debug") in German and Italian, which is the word there.  The Guard enum's four sentence pairs pass station and signal in the order their placeholders expect.

### TDU-D5 - the old autonomy tab's removal took only the JSON handlers

| | |
|---|---|
| **Disposition** | Checked - clean. |


Method names diffed across `a4674c9b`: the eight removed are `autosaveActionPerformed`, `decodeAutonomyJson`, `exportJSONActionPerformed`, `jsonDocumentationButtonActionPerformed`, `loadDefaultBlankGraphActionPerformed`, `loadJSONButtonActionPerformed`, `resumesFromJsonAtStart`, `validateButtonActionPerformed`; none added or lost elsewhere.  The Auto tab's inner tabs are inserted by component at fixed indices (`:4321-4326`) and `jumpToAutonomyLocTab` selects index 0, the locomotive tab, as before.  The Documentation item opens the guide off the event thread; `Util.openUrl` shows no dialog.

### TDU-D6 - Echo Sent Commands cannot reach a live railway

| | |
|---|---|
| **Disposition** | Checked - clean. |


`DEBUG_SIMULATE_PACKETS` is read only in `MarklinControlStation.exec`'s `!on` branch (transmission disabled) and only with `debug && DEBUG_LOG_NETWORK`; the menu is mounted only in debug and simulation, and a test run neither reads nor writes the stored choice.

### TDU-D7 - Import Routes' re-arm question

| | |
|---|---|
| **Disposition** | Checked - clean. |


Asked on the event thread before anything is replaced, No the default; `routesSavedArmed` catches `JSONException`, so a malformed file still reaches the import's own failure dialog.

### TDU-D8 - the orange and grey marks across a route tile, and per road

| | |
|---|---|
| **Disposition** | Checked - clean. |


`routesOf` translates a transparent tile's roads through `TileGraph.transparentRouteOf` (OB-279); the grey's diff in `repaintTheWashWhereItChanged` compares road sets, so a double curve whose blocked arc changes is redrawn; `theRoadToFade` fades the whole square where the claimed place names no road.

### TDU-D9 - smaller changes read and found as described

| | |
|---|---|
| **Disposition** | Checked - clean. |


OB-292's orientation runs only in the single-tile `execCopy` and never on a move (it applies in the track editor as well as the autonomy editor, which Adam's request named - worth one line to him if that matters); OB-289's strut follows the Text Labels box; OB-272's `drawn` decides caption and writing separately only inside the autonomy editor; MT-479's wrap width is fixed once and the heading keeps the taller height; AUT-C2's guard-is-both refusal sits in `addGuardSignal`, which both the click and the address path use; GUI-C8's `whyAutonomyEditorCannotOpen` does not refuse a displayable open editor, so the label brings it forward; ROUTE_TRAIN_LENGTH_MAX feeds both dropdowns and the walk.  The triage app's markdown rendering writes nothing back (only `feedback` and `detail`, which are editable, are ever read), and its language launch passes `-Duser.language`/`-Duser.country` to a simulation.
