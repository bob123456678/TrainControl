# User interface - validation round 3 of the fixes `7b0d5c2f..67f79912`

**Status:** open

**Prefix:** `TDU3`

**Reviewed:** branch `autonomy-diagram-r0` at `67f79912`, 2026-09-24.  The previous round's document `docs/reviews-2026-09-24/TDU2.md` and its dispositions, and the eighteen commits `7b0d5c2f..67f79912`; lane: the ones touching `src/org/traincontrol/gui`, the message bundles and the tests that claim them - `54f31622`, `5e1b0623`, `a4ed4480`, `809d91a0`, `fea75279`, `49a4f0b7`, `aee9d2bf`, the bundle and refusal halves of `84a89426`, `5b656ebe`, `4776026a`, the gui and test halves of `ef1adfb6`, the code, test, behaviour.md, open-questions.md and MT-573 halves of `244331ce`, and the TDU2 rows of `67f79912`.

**Method:** Read `docs/reviews/FANOUT.md`, `docs/reviews/README.md` and `TDU2.md`, then `git log --stat` of the range and `git show` of every lane commit, then the code at HEAD around each fix.  For TDU2-A1: the three placement doors (`TrainControlUI.rememberPlacement` and the paste door above it, `LayoutRightclickAutonomyMenu.placeFacing`, `GraphLocAssign.commitAndRecord`) against `TailCrossedPrompt.placementStillStands`, `DiagramPick` (`of`, `ask`, `arm`, `clicked`, `finish`), and everything the live window can do while the question waits that changes the three things the check reads - `Layout.moveLocomotive`, `Point.setLocomotive`/`setArrivedFrom`/`setArrivedAlong`, `AutonomySession.placeLocomotive`/`writePointProperty`, and the rebuild paths (`rebuildRunningLayoutFromSetup`, `AutonomyEditorPanel.rebuildRunningLayoutSoon`, `AutonomyViewerPanel.load`, `MarklinControlStation.parseAuto`, `putTheTrainsBack`, `captureFromLayout`'s merge), and the Autonomy menu's configuration and page items.  For TDU2-C1: `takesTheClick`, `LayoutLabel`'s sensor listener and the claim's click sequence.  For TDU2-C2: the three run doors at HEAD (Start, Execute Timetable, Return Home), the gate itself, `loadAutoLayoutSettings`, the home planner's entry points, and the two claims' index arithmetic.  For TDU2-C4: all eight bundles (key sets, ASCII, placeholders, apostrophes) with read-only `grep`/`diff` pipelines, and the two refusals' callers.  For TDU2-C5 and C7: a grep over `src`, `test` and the reference documents for the phrases the fixes replaced.  For TDU2-C3 and C6: `open-questions.md` and MT-573 with its new comment, against `refreshReturnHomeButton`, the editor's open and close, the exit capture and start-up resume.  Each claim was read for whether its named mutation reaches its assertion.  Nothing was run, compiled or started; no file was written but this one (the harness saved one oversized `git show` output to its own tool-results folder).  One recursive `grep -rln "TDU2-A1" .` from the repository root searched `cs2_sample_layout/` along with everything else before I excluded it; it listed nothing there and nothing from that folder was read or used.  The frozen railway was not needed.  Every finding that depends on behaviour says what execution would settle it.

## A - high

None found.

## B - medium

### TDU3-B1 - a rebuild while the tail question waits replaces the Point the TDU2-A1 check reads, so the check passes on a Point no longer on the railway: the answer never reaches the running layout, and after a configuration switch it is written into the other configuration

| | |
|---|---|
| **Disposition** | Fixed - claim testAnAnswerAfterARebuildReachesTheRailway in 6bfab5bb (red first), fix 32a75f8d: the answer is asked of the running railway's copy of the square (TailCrossedPrompt.whereTheAnswerGoes) - after a rebuild of the same setup it reaches the new copy, through the new railway's edges; after another configuration the copy holds that configuration's train or none and the answer goes nowhere; and nowhere once the window's session is another.  Mutation R3a red.  The configuration switch is covered by the same question rather than a claim of its own. |
| **Where** | `TailCrossedPrompt.java:520-542` (`placementStillStands`, and its javadoc: *"anything done in the wait that could matter changes one of the three"*); the three doors - `TrainControlUI.java:8150-8191`, `LayoutRightclickAutonomyMenu.java:1232-1261`, `GraphLocAssign.java:311-328`; the rebuilds - `AutonomyEditorPanel.java:9435-9460` (`rebuildRunningLayoutSoon`, posted by every setup change made in an `AutonomyEditorPanel`, the diagram's right-click menu included), `TrainControlUI.java:6575-6703` (`rebuildRunningLayoutFromSetup` - `load(active, false, false)`), `AutonomyViewerPanel.java:746-839` (`load`), `MarklinControlStation.java:1059-1097` (`parseAuto`: the old Layout is invalidated and stopped, its Points left as they are), `TrainControlUI.java:6434-6490` (`putTheTrainsBack` carries each train's side and recorded road to the new Point); the doors reachable in the wait - `AutonomyMenu.java:309-316` (Configurations), `:791` (a page left out: `reloadActiveDiagramConfiguration`); `AutonomySession.java:8435-8460` (`writePointProperty` writes to `store.getActiveConfiguration()`); `AutonomySession.java:3156-3163`, `:5321-5337` (the capture replaces `arrivedAlong` from the running layout); commit `5e1b0623` |

TDU2-A1's fix asks, after the answer comes back, whether the copy still holds the train it placed, with the side and road it left.  It asks the `Point` object the door captured before the question.  A rebuild does not change that object: `parseAuto` builds a new `Layout` with new Points, and the old one keeps its locomotive, side and road untouched.  So after any rebuild during the wait the check is true whatever has happened on the railway, and the door writes as it did before the fix - to a Point nothing reads, and to the setup of whichever configuration is active by then.

Rebuilds are ordinary gestures, and nothing stands them down while a question is armed: any setting changed from the diagram's right-click menu (a home, a direction, a caption, the *Farthest sensor the tail crossed* radio - each posts `rebuildRunningLayoutSoon`), a page left out from the Autonomy menu, a configuration chosen from its Configurations submenu, an editor closed.

What reaches the railway:

1. **Same configuration.**  Paste 75 407 DB at Tunnel from the north; the question lights TunnelPre and 12,7.  Before answering, right-click another station and set its home.  The rebuild puts the train back on a new Tunnel copy with its side and no road (none was recorded yet).  Click TunnelPre.  The check passes on the old copy; the road goes to the old copy and to the setup.  The running layout never gets it: the train's claim stops at the switch (MT-477's hand-placed rule) while its tail lies towards TunnelPre, so a train can be routed through the switch into it - the collision the question exists to prevent - and nothing tells the operator that the answer did not take.  The next rebuild brings the road in from the setup; the exit capture, if it comes first, writes the running layout's none over it (`arrivedAlong` is a captured key), and the answer is gone.
2. **Another configuration.**  The same, choosing another configuration from the Autonomy menu instead.  `load` captures the outgoing configuration (the train, its side, no road), switches the store's active configuration and rebuilds.  Then the answer, or Cancel on the small window still showing: the paste door writes the road (or none) and the train's facing onto the same square of the configuration now loaded, and saves it; the right-click door writes the same.  If that configuration has a train of its own there, its setup now carries another train's facing and road until its next capture; if not, the square keeps a facing and the road is dropped - the setup of a configuration the operator never touched is changed and written to disk.
3. The same shape where the session itself is replaced (`resetAutonomySession`, a diagram re-downloaded): the door writes into, and saves, the session object it holds.  Not traced further.

**On the railway.**  Case 1 leaves a standing train's tail unclaimed past the switch while the operator believes it recorded - TDU2-A1's consequence, reached by a different gesture.  Mitigations: it needs a setup change or a load while the question waits; the grey on the diagram visibly stops at the switch; the next rebuild restores the road from the setup; case 2's damage is repaired at that configuration's next capture (before a load, at exit), and a rebuild puts each train back on its copy by name, so it matters mainly after a crash.  Graded B; C if Adam rules that a setup change made while the question waits is not a gesture to design for.

**The disposition.**  "Fixed" is true of the three ways TDU2-A1 named (TDU3-D1) and the javadoc's premise is false: a rebuild matters and changes none of the three.

**Verification request.**  Fixture: `regression.testTheTailIsPickedOnTheDiagram`, as `testALateAnswerDoesNotWriteOverAnotherTrain` sets it up (train A at Tunnel (southbound) from the north, `tailAtTheLanding` = "N", `rememberPlacement` inside `invokeLater`, `armed` non-null).  From the test thread, `SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup())` - what the right-click menu's setup change posts.  Preconditions: `model.getAutoLayout().getPoint("Tunnel (southbound)")` is not `q.tunnel` and holds A.  Re-read TunnelPre's label from `ui.getDiagramTileRegistry()` (the rebuild may re-register labels) and click it; wait for the door to return.  **Proves it:** the new Tunnel Point's `getArrivedAlong()` is null while `ui.getAutonomySession().getArrivedAlong(tile)` holds TunnelPre's road.  **Refutes it:** the new Point has TunnelPre's road.  For case 2, with a second configuration in the sandboxed store, replace the rebuild with `getAutonomyViewerPanel().load(other, false)` and press Cancel: **proves it** where `other`'s entry for Tunnel's square has gained a `facing` (or lost an `arrivedAlong`) it did not have.  Suggested fix: ask also that the Point is still the running layout's (`layout.getPoint(point.getName()) == point` on `model.getAutoLayout()`) and that the store's active configuration and the session are the ones the question was asked under - or take the question down as Cancel in `parseAuto`'s callers and `resetAutonomySession`.

### TDU3-B2 - MT-573's new comment sends a train away after step 1's backup, so step 5's restore leaves the setup saying the train is at home while it stands at another station

| | |
|---|---|
| **Disposition** | Fixed - as TDD3-B1: a comment on MT-573 replacing the one before it - send the train away before step 1. |
| **Where** | `docs/manual-tests/tests.md:28165-28193` (MT-573: step 1 copies `config/autonomy` away, step 5 copies it back; the comment added by `244331ce` as TDU2-C6/TDD2-C9's fix); start-up resume `TrainControlUI.java:23293`; the editor's capture on opening, the exit capture (`resetAutonomySession`/`captureRunningLayout`) |

TDU2-C6 found that step 3 cannot be reached with every train at home.  The comment's remedy: *"Before breaking the setup, send one train by hand to another station ... After step 5, send it back or press Return Home."*  Before step 2 is after step 1, so the backup holds the train at its home.  The hand send moves it; the editor's open and the exit capture write where it now stands into the live folder; step 5 then copies the backup back over it.  At the next start the active configuration is resumed from that backup: the diagram draws the train at its home, and it stands at the other station.  "Send it back" cannot be done - to the program it is already home, so Return Home is greyed and a hand send would start it from a square it is not on.

**On the railway.**  A train the program places wrongly: its real station is taken for empty and its home for occupied, and the next run or send routes on that picture.  Mitigations: the diagram shows it at home, which Adam would likely notice; he knows a restore reverts placements.  Graded B because the manual test's own instructions produce the state, and an entry is append-only, so the correction has to be a second comment.  (Smaller: *"after a restart every train is at home"* is TDU2-C6's "how a session usually starts" hardened into a rule; a train already away needs no send.)

**Verification request.**  Desk check, no railway: in a scratch copy of a layout folder, back up `config/autonomy`, start TrainControl, hand-send one train from its home to another station, close, restore the backup, start again.  **Proves it:** the train is drawn on its home square.  **Refutes it:** it is drawn where it was sent.  Suggested fix, as a new comment: send the train away before step 1, so the backup records it away and "after step 5, press Return Home" works; or, after step 5, put the train back where it stands (Control+X, Control+V) before anything runs.

## C - low

### TDU3-C1 - the claims prove less than the dispositions: TDU2-A1's facing half and TDU2-C2's stop at Return Home are unclaimed, and the three doors write the facing three ways

| | |
|---|---|
| **Disposition** | Fixed - 32a75f8d: the paste and right-click doors write the facing before the question, as the locomotive dialog does, so only the road waits for the answer; Return Home's and Execute Timetable's failed gates claimed door by door (testAGateThatFailsStopsEveryRunDoor).  Mutations R3g, R3h, R3i red. |
| **Where** | `TrainControlUI.java:8189` (`if (stands) session.setFacing(...)`); `LayoutRightclickAutonomyMenu.java:1259` (`facing != null && session != null && stands`); `GraphLocAssign.java:294` (facing written before the question, unconditionally); `test/regression/testTheTailIsPickedOnTheDiagram.java:484-632` (the claim asserts `q.tunnel.getArrivedAlong()` only; the pin asserts `placementStillStands(` sits between `askAfterPlacement(` and `setArrivedAlong(`); `TrainControlUI.java:24076-24081` (Return Home's catch and `return;`); `test/ui/testNonAtomicRoutesNeedTheirLengths.java:658-663` (the `return;` is looked for in Start's handler only) |

- TDU2-A1 found two late writes: the road over another train's, and *"the first train's facing ... over them and saves - the setup facing disagrees with the copy, GUI-B1's state"*.  The disposition says each door writes "the answer" only while the placement stands, and the fix guards the facing at the paste and right-click doors.  Nothing claims it: delete `if (stands) ` at `TrainControlUI.java:8189`, or `&& stands` at `LayoutRightclickAutonomyMenu.java:1259`, and the claim (road only), the pin (the call before `setArrivedAlong(`) and `core.testAPasteDoesNotTurnTheTrainRound` (which reads `session.setFacing(tile, facingChosen != null ? facingChosen`, still present) all stay green.
- TDU2-C2's disposition: *"a gate that fails stops Start and Return Home"*.  The claim commit's own title says "stops Start", and the assertion looks only in Start's handler: delete `return;` at `TrainControlUI.java:24080` and Return Home runs non-atomic past a failed gate with every claim green.
- The facing itself: `GraphLocAssign` writes it before the question, where no wait can make it late; the paste and right-click doors write it after, and drop it whenever the check fails - including where the train still stands and only the road changed, because the operator answered from the right-click menu's *Farthest sensor the tail crossed* instead of the lit squares.  There the paste's facing is never recorded and the setup keeps the square's previous one until a capture.  One rule in three shapes.  Moving the paste and right-click doors' facing write above the question, as `GraphLocAssign` has it, would leave only the road depending on the wait.

Railway consequence of the gaps: none today; a future edit can undo either half unnoticed.  **Verification request:** `mutate.py` with each of the three deletions above against `regression.testTheTailIsPickedOnTheDiagram`, `core.testAPasteDoesNotTurnTheTrainRound` and `ui.testNonAtomicRoutesNeedTheirLengths`.  **Proves it:** all green.  **Refutes it:** any red.

### TDU3-C2 - an answer the TDU2-A1 check drops is dropped without a word, and behaviour.md does not say it happens

| | |
|---|---|
| **Disposition** | Fixed - 32a75f8d: a dropped answer is logged naming the train and the square, in eight languages; behaviour.md 5c states the rule (59cf3645). |
| **Where** | `TrainControlUI.java:8159-8191`, `LayoutRightclickAutonomyMenu.java:1244-1261`, `GraphLocAssign.java:321`; `docs/reference/behaviour.md:1102-1107` (5c's FR-100 paragraph) |

The operator clicks a lit sensor; the squares go dark; nothing is recorded and nothing says why - no log line, no sentence.  TDU2-A1's suggested fix had one (*"refuse ... with a sentence naming it"*).  In the cases the check was written for the answer is moot (another train stands there, or the train has gone), but the operator cannot tell "dropped because the train moved" from "it did not work".  And 5c's FR-100 paragraph, which states TDU-C1, B3 and B2, does not state this rule or that the facing goes with it.  Suggested fix: one log line naming the train and the square when a door drops its answer, and a sentence in 5c.

### TDU3-C3 - comments the load-door and hand-door sweeps did not reach

| | |
|---|---|
| **Disposition** | Fixed - 59cf3645: Start's handler and its claim say the third door and the file door; testAutoLayout counts the load door; the Auto tab's comment names every door. |
| **Where** | `TrainControlUI.java:26888-26889`; `test/ui/testNonAtomicRoutesNeedTheirLengths.java:445-446`; `test/core/testAutoLayout.java:639-640`; `AutoLocomotiveStatus.java:1083-1085` |

- Start's handler - edited by `aee9d2bf` - still says *"THE FOURTH DOOR, and the one the other three cannot cover ... both file doors re-ask this afterwards"*, and the claim's javadoc at `testNonAtomicRoutesNeedTheirLengths.java:445-446` says *"The other three doors ... both file doors"*.  Since OB-254 there is one file door, so three doors and "the other two".  TDU2-C5's fix swept "both load doors" in two javadocs; these say "file doors".
- `testAutoLayout.java:639-640`: *"at all seven of its doors - the checkbox, both load doors, and the five that dispatch a train"* - six.
- `AutoLocomotiveStatus.java:1083-1085`, the Auto tab's hand door: *"asked here and at the diagram's right-click destinations"*.  Its twin in the right-click menu now names all four doors (TDU2-C7); this one still names two.

Nothing behaves differently.

### TDU3-C4 - Execute Timetable is still the one run door whose failed gate leaves its button greyed for the session

| | |
|---|---|
| **Disposition** | Fixed - claim in 6bfab5bb (red first), fix 32a75f8d: Execute Timetable's gate is caught, logged, and the button given back.  Mutation R3g red. |
| **Where** | `TrainControlUI.java:26699` (`executeTimetable.setEnabled(false)`), `:26701-26864` (the `invokeLater` block, no `finally`), `:26814` (the gate) |

TDU2-C2 made a failed gate stop Start and Return Home and give their buttons back (Start's `finally`, Return Home's).  Execute Timetable's gate is a bare call inside the `invokeLater` block that follows the greying; a throw ends the block before the dispatch, so the run is stopped, but no path gives the button back until something else re-enables it.  It was the same before the range (the call moved down within the same block).  The gate reads two lists and can hardly throw, so no railway consequence is expected.  Suggested fix: the same `try`/`catch` with `setEnabled(true)` in the catch.

### TDU3-C5 - TDU2-C3 is written as Adam's decision without the recommendation FANOUT asks for

| | |
|---|---|
| **Disposition** | Fixed - 59cf3645: open-questions carries the recommendation - each door as its Start twin is. |
| **Where** | `docs/reference/open-questions.md:218-221`; `docs/reviews/FANOUT.md` (the round, step 2: *"Adam's decision (write the question, the options and a recommendation)"*) |

The entry is accurate (TDU3-D4) and gives two options, but no recommendation, so the end-of-session report card will have none to show.  One that follows the precedent already in the code: grey the right-click Return Home item with the setup's sentence as its tooltip, as Start's item beside it is greyed, and keep the Return Home and Execute Timetable buttons live and explaining, as Start's button deliberately is - so every affordance matches its Start twin, and the guard and its affordance ask the same question.  No railway consequence.

## D - checked and clean

### TDU3-D1 - TDU2-A1 for the three ways it named: a run, a second placement, a removal

| | |
|---|---|
| **Disposition** | Checked - clean. |

Each changes what the check reads.  A run's arrival puts another locomotive on the copy (`Point.setLocomotive`), and the reference check fails.  A second placement on the square displaces the whole square (`clearBlockExcept`) and writes its own facing and road; the same train put back on the same copy with Not known is the one case that still passes, and there the later-answered question wins, which is harmless.  Control+X or Delete empties the copy and clears the setup through `placeLocomotive(tile, null)`.  `DiagramPick.of` returns null while a question is armed, so a second door gets the modal list and cannot nest a second pick.  The claim (`54f31622`) calls the real `rememberPlacement` on the event thread, asserts the pick is armed, places the second train with a driven road and presses Cancel; unfixed, the TDU-B2 fields are null on the fresh window, so `roadToRecord` gives null and the road is erased - red for the finding's own reason.  The pin reads the first `askAfterPlacement(` in each of the three files, which is the door's call in each (no earlier mention), so deleting a door's check fails it by name.  The half of the suggested fix not taken - "and autonomy is idle" - was checked: the check passes only while the train still stands as placed, so what is written mid-run is the right answer about it; `moveLocomotive` refuses while running, so no placement door can act mid-run; and a departure between the check and the write leaves a road on an empty copy, which the next occupant's `setLocomotive` clears.  The rebuild case is TDU3-B1; the facing and claim gaps TDU3-C1.

### TDU3-D2 - TDU2-C1: the guard is put down by the next sensor click

| | |
|---|---|
| **Disposition** | Checked - clean. |

`takesTheClick` is reached only from a sensor tile's left `mouseClicked` (`LayoutLabel.java:330`, after `openStationMenu` and the right-button return), once per click.  The answering click sets `answeredBy`; the second click of that double-click finds nothing armed, reads it, clears it and is swallowed; any later click starts clean, so a later double-click flips and flips back.  The claim's second half fails with the clearing removed (count 2 on the same label is swallowed, leaving one flip).  Notes, not raised: the code comment says "the next click on anything", the javadoc "any sensor" - only sensor clicks reach it, and a stale guard could only ever swallow a count-2 click on the same label, which the first click of any new double-click clears anyway; a triple-click that answers now flips the sensor with its third click, where before the fix clicks 2 and 3 were both swallowed; and neither half of the claim asserts that one click flips the sensor, so it would pass vacuously if the flip ever went asynchronous.

### TDU3-D3 - TDU2-C2: the three run doors ask the gate after every refusal

| | |
|---|---|
| **Disposition** | Checked - clean. |

Execute Timetable's call is below the busy, empty, conditional-route and start-square refusals, on the event thread.  Return Home's is on the worker after `plan.isPossible()` (which covers no plan, impossible and `LOCOMOTIVES_RUNNING` from `loadReturnToHomeTimetable`), through `invokeAndWait` from a plain thread that holds nothing; the planner does not read the setting (no "atomic" in `HomeStaging`; `planReturnToHome` and `loadReturnToHomeTimetable` do not ask it), and `loadAutoLayoutSettings`, which the gate calls, sets values and ticks only - it re-enables none of the controls the staging flow greyed.  A failed gate returns into the `finally`, which hands the timetable back and restores only what was taken.  Start returns before `started.set(true)`, and its `finally` gives the button back.  `testEveryDispatchDoorAsksTheGate` still finds Return Home's call before `executeTimetable();`.  The claims' anchors are unique in their handlers, so moving either call back above a refusal fails them.  Gaps: TDU3-C1, TDU3-C4.

### TDU3-D4 - TDU2-C3: the open question is filed and describes the code

| | |
|---|---|
| **Disposition** | Checked - clean. |

`open-questions.md` under Setup and start-up has it, with the store row "Open - Adam's decision".  At HEAD Return Home's item still greys on busy and the triage only (`HomeLocomotiveMenu.java:59`), `refreshReturnHomeButton` asks no setup question, and Execute Timetable's button stays live and refuses; Start's right-click item greys with the setup's reason.  A genuine guard-and-affordance choice with precedent both ways, so Adam's to make.  The missing recommendation is TDU3-C5.

### TDU3-D5 - TDU2-C4 and the message bundles after the range

| | |
|---|---|
| **Disposition** | Checked - clean. |

Three keys changed, in all eight bundles, and nothing else.  1827 keys in each, identical key sets, no non-ASCII byte in any file.  `autosetup.ui.checkHalfMeasuredApproach` names "the length rules" in every language (laengdereglerne, Laengenregeln, reglas de longitud, regles de longueur, regole di lunghezza, lengteregels, reguly dlugosci) with `{0}` and `{2}`; `autolayout.errorBerthApproachPartlyUnmeasured` says the train is taken to reach further back than it may, with `{0}`, in all eight; `checkRunInShorterThanTheBerth` says switch, crossing or reversal with `{0}`, `{2}`, `{3}`; no straight apostrophe in any of them.  Both appended notes now follow ". ", and neither refusal they follow ends with a full stop in any language, so no "..".  The hand doors show the joined sentence in their dialogs, the railway logs it.  The platform notice keeps "switch or reversal", correctly: `84a89426` changes only the berth's walk.

### TDU3-D6 - TDU2-C5 and TDU2-C7 at the sites they named

| | |
|---|---|
| **Disposition** | Checked - clean. |

behaviour.md 5d names the one load door and where Validate went; the gate's javadocs at `TrainControlUI.java:5946` and `:6028` say the load door; `whyAHandSendIsRefused`' javadoc, the right-click door's comment and the claim class's javadoc name all four doors; `testTheTailIsPickedOnTheDiagram`'s class javadoc points to the event-thread pin; `takesTheClick` and `answeredBy` say one double-click.  Siblings not reached: TDU3-C3.

### TDU3-D7 - TDU2-C6: step 3 of MT-573 can now be reached

| | |
|---|---|
| **Disposition** | Checked - clean. |

With a train sent away before the setup is broken, the running layout keeps it there: the editor's close refuses to load a setup with blocking problems and leaves the running layout alone, so the triage finds a train away and Return Home is offered and refuses with the setup's sentence.  What the comment's order does to step 5 is TDU3-B2.

### TDU3-D8 - the range's other gui commits: One-Way Run's prompt, the renamed Bulk Tools items, the guard tooltips

| | |
|---|---|
| **Disposition** | Checked - clean. |

- `4776026a` (TDD2-C7): putting One-Way Run down on a page left out now resets the hint, as `putToolsDown` does; the claim (`5b656ebe`) reads `tool` and the hint's text.  It does not call `clearGesture`, but for this tool the only state is `oneWayFrom`, which it clears.
- `ef1adfb6` (TDD2-C13, C15): the comments and test messages use the labels the bundle has ("Mass Assign Station Max Train Lengths", "Mass Assign Locomotive Train Lengths"), and no old name is left in `src`, `test` or the reference documents.  The tooltip pin compares each item's tooltip with the first 40 characters of its own guard's text; the two texts differ within those 40 ("Signals held at red while ..." and "Signals set to red when ..."), so swapping them fails it.
