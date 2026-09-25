# User interface - validation round 2 of the fixes `fc1ce476..7b0d5c2f`

**Status:** closed

**Prefix:** `TDU2`

**Reviewed:** branch `autonomy-diagram-r0` at `7b0d5c2f`, 2026-09-24.  The previous round's document `docs/reviews-2026-09-24/TDU.md` and its dispositions, and the twenty commits `fc1ce476..7b0d5c2f`; lane: the ones touching `src/org/traincontrol/gui`, the message bundles and the tests that claim them - `ce2c908f`, `fc574093`, `664fe577`, `492ad392`, `26bdd47b`, `858317f2`, `a961695e`, `b62f1976`, `865b4168`, `28469a46`, `39bb947a`, `be87cb65`, `7b0d5c2f`, and the bundle halves of `1cd99bbc` and `10d7f523`.

**Method:** Read `docs/reviews/FANOUT.md`, `docs/reviews/README.md` and `TDU.md`, then `git log --stat` of the range and `git show` of every lane commit, then the code at HEAD around each fix: the three doors that ask the tail question (`TrainControlUI.rememberPlacement` and the paste door above it, `LayoutRightclickAutonomyMenu.placeFacing`, `GraphLocAssign.commitAndRecord`) against `TailCrossedPrompt.DiagramPick` (arming, the secondary loop, `finish`, `takesTheClick`), `AutonomySession.placeLocomotive` / `forgetPlacementsElsewhere`, `Layout.moveLocomotive` and `Point`'s arrival fields; the five dispatch doors (Start, Execute Timetable, Return Home, the two hand doors) for the setup refusal and the Atomic Routes gate, and the Return Home affordances (`HomeLocomotiveMenu`, `refreshReturnHomeButton`); `AutonomyChecks`' ERROR findings, to see whether any is one Return Home would clear; the Bulk Tools train walk; the comments `b62f1976` rewrote, against the methods they now name; the claims `testTheTailIsPickedOnTheDiagram`, `testAHandSendIsRefusedWhileTheSetupIsBroken`, `testNonAtomicRoutesNeedTheirLengths`' new case, `testMassAssignLengths`' new case and `testTheEditorSaysWhatItsToolsDo`' new pin, each read for whether its named mutation reaches its assertion; `behaviour.md`, `issues.md` and the new tracker entries MT-573 to MT-575 from `28469a46`.  The bundles were checked with two read-only Python snippets piped through stdin (no file written; the first stopped on a Windows path separator and was rerun).  Nothing was run, compiled or started; `cs2_sample_layout/` was not opened; the frozen railway was not needed.  Every finding that depends on behaviour says what execution would settle it.

## A - high

### TDU2-A1 - the tail question on the diagram suspends the placement door for as long as it waits, and the door then writes its answer onto whatever stands on that copy by then - after a run, another train's road

| | |
|---|---|
| **Disposition** | Fixed - claim 54f31622 (red first), fix 5e1b0623: each placement door writes the answer only while its placement still stands - the train, the side and the road it left.  Pin a4ed4480 on the other doors.  Mutations S1, S2, S3 red. |
| **Where** | `TrainControlUI.java:8150-8186` (`rememberPlacement`: the question, then `session.setArrivedAlong`, `point.setArrivedAlong(road)`, `session.setFacing`, `session.save()`); `LayoutRightclickAutonomyMenu.java:1228-1256`; `GraphLocAssign.java:311-320`; `TailCrossedPrompt.java:633-672` (`ask`), `:770-783` (`finish`, the only place `armed` is cleared), `:572` (`HOLD_MS` ends the highlight, not the question), `:331-338` (`roadToRecord`); TDU-B2's disposition (`fc574093`) |

TDU-B2 found two things: while the question waits on the diagram *"the whole window is live"*, and the paste door read its landing back from fields a second placement overwrites.  `fc574093` reads the fields into locals - the second half.  The first half is untouched, and TDU-B2 had already named its other form: *"the right-click and dialog doors, which keep their state in locals, write their answer and save onto whatever the square holds by then"*.  The paste door is now one of those.  So the disposition "Fixed" is true of the fields and not of the finding; and the consequence reaches further than TDU-B2 said.

**Every guard is asked before the wait and every write happens after it.**  The paste door asks "not while busy" (`:6982`) and "not with an editor open" (`:6995`) on the way in; the menus are built with the same questions.  Then `DiagramPick.ask` enters a secondary loop with no timeout: `armed` is cleared only by `finish` - a click on a lit sensor, Not known, Cancel, Escape or closing the small window.  Meanwhile Start, both hand doors, Return Home, Execute Timetable, Control+V/X/Delete, the right-click Place and the editors are all live, and nothing takes the question down when one of them is used.  After the wait, nothing re-checks that autonomy is idle or that the Point still holds the train that was placed.

The sequence that reaches the railway:

1. Paste (or right-click Place, or Place Locomotive... from the diagram's menu) a train onto a square whose tail question goes on the diagram - by construction a square with a fork within a train-length behind it (Tunnel on the frozen railway).
2. Leave the question.  Press Start, or hand-send the train away.
3. A run brings another train onto the same copy.  Its arrival writes the road it drove onto the Point, and its tail is claimed along that road through the junction (behaviour.md 5c; Adam, MT-335, quoted at `Point.getArrivedAlong`).
4. Press Cancel or Not known on the small window still at the top of the main window.  For a train pasted from another square `roadToRecord` gives null, and the door runs `point.setArrivedAlong(null)` on that Point - the second train's road is erased on the running layout, from the event thread, mid-run.  A click on a lit sensor writes the first train's road there instead.  The setup is written and saved too.
5. With no road, *"a train placed by hand (no route) still stops at the fork"* - the claim runs to the switch where the rails part and stops (MT-477).  The leg the second train's tail really lies on past the switch is not claimed, and `isPathClear` may route a third train through the switch into it.

The same late write lands two other ways.  A second train placed on the same square during the wait: placing displaces the whole square (`Layout.java:9464-9479`), the second placement writes its own facing and road, and the first door's answer then writes the first train's facing (and, where both stand on one copy, its road) over them and saves - the setup facing disagrees with the copy, GUI-B1's state, which a restart after a crash turns into a train dispatched the wrong way.  And Delete or Control+X on the square during the wait leaves a road and a facing on an empty square in the setup; the next occupant's `placeLocomotive` clears the road but not the facing (IND9-A1's hazard, mostly overwritten by doors that write a facing).  It also breaks `Point.setLocomotive`'s stated invariant that a hand gesture writes a train's state only *"while autonomy is stopped"*.  None of this was reachable before FR-100: the list was modal.

**On the railway.**  A train routed onto track a standing train's tail lies over - the collision the tail question exists to prevent.  Mitigations: it needs a question left unanswered across a run, then answered after another train has come to rest on that same copy (the square's copy facing the same way); the small window stays on screen and the squares flash for half an hour, which invites an early answer; the setup half is repaired by the exit capture.  Graded A on consequence; B if Adam rules that leaving the question open across a Start is not a gesture to design for.

**Verification request.**  Fixture: `regression.testTheTailIsPickedOnTheDiagram` (frozen railway, real window).  Inside `invokeLater`, run `GraphLocAssign.commitAndRecord` (or the paste door) for a five-unit train A at Tunnel (southbound) arriving from the north, so the pick is armed.  From the test thread: `moveLocomotive(B, "Tunnel (southbound)", false)` (which displaces A), then give B's Point the TunnelPre choice's road R, as a run's arrival does; assert the precondition `getArrivedAlong()` is R.  Press the small window's Cancel (the dialog titled `autolayout.ui.askArrivalSideTitle`) and wait for the door to return.  **Proves it:** the Point's road is null (or A's) and `placesCoveredByStandingTrains` no longer holds B's places towards TunnelPre.  **Refutes it:** R stands.  On the railway: 75 407 DB pasted at Tunnel from the north, the question left, the train hand-sent away, a second five-unit train hand-sent into Tunnel southbound, then Cancel - the grey behind Tunnel stops at the switch (proves) or runs on to TunnelPre (refutes).  Suggested fix: the finding's own alternative - while a question is armed, refuse a placement, Start, the hand doors, the run doors and the editors with a sentence naming it (or take the question down as Cancel when one is used); and in each of the three doors, after the question, write nothing unless the Point still holds the placed train (by reference) and autonomy is idle.

## B - medium

None found.

## C - low

### TDU2-C1 - the double-click guard is never put down: a later double-click on the sensor that last answered a question flips it once instead of twice

| | |
|---|---|
| **Disposition** | Fixed - claim 809d91a0 (red first), fix fea75279: the next click puts the guard down, so a later double-click on the sensor that answered flips it and back.  Mutation R2a red. |
| **Where** | `TailCrossedPrompt.java:550-560` (`clickCount > 1 && label == answeredBy`; `answeredBy` set at `:743`, `:756`, never cleared); `fc574093`; behaviour.md 5c (*"The rest of a double-click on the sensor that answered is the question's"*) |

TDU-B3's fix keys the guard on the label and the click count, and `answeredBy` stays set until another question is answered on another sensor.  With no question waiting, a double-click on that sensor an hour later: the first click flips it, the second (count 2) is swallowed - the sensor is left changed where before the fix a double-click flipped it and flipped it back.  Clicking it quickly on and off to pulse it leaves it on.  The change is announced through `MarklinFeedback.setState` as a real one, which is the faked occupancy TDU-B3 was about, in the other direction and on one sensor.  The javadoc (*"for the rest of its double-click"*) and behaviour.md describe the narrower guard that was meant.  (It also keeps a dead `LayoutLabel` reachable after the diagram is rebuilt.)  On the railway: one sensor left showing occupied after a double-click, which an armed s88 route or a run reads; a single click puts it right.  **Verification request:** in the same class, after `testADoubleClickAnswersAndDoesNotFlipTheSensor` has left `answeredBy` on TunnelPre's label, with nothing armed, dispatch `MOUSE_CLICKED` with count 1 and then 2 on that label.  **Proves it:** the feedback state differs from before.  **Refutes it:** unchanged.  Suggested fix: clear `answeredBy` on the next click whose count is 1.

### TDU2-C2 - "after every refusal" is still not true at Execute Timetable and Return Home, and Start's gate now fails open

| | |
|---|---|
| **Disposition** | Fixed - claims 49a4f0b7 (red first), fix aee9d2bf: Execute Timetable asks the gate below the conditional-route warning and the start-square check, Return Home once its plan is known to be possible, on the event thread; a gate that fails stops Start and Return Home. |
| **Where** | `TrainControlUI.java:26710` (Execute Timetable's gate) before `:26712-26759` (the conditional-route warning - declining it refuses the press) and `:26761-26785` (a locomotive not at its start); `:23970` (Return Home's gate) before `:24044-24057` (the plan refused on the worker); `:26961-26968` (Start: a gate that throws is logged and the run goes ahead); `492ad392` |

TDU-C8 took Execute Timetable as the model - *"GUI-A1 moved the Atomic Routes gate after Execute Timetable's refusals"* - and put Start's call after all of Start's refusals, the conditional-route warning included.  But Execute Timetable's call sits above two of its own refusals, so declining the same warning switches the railway to atomic at one door and not at the other; and Return Home's plan refusals (no plan found, impossible) come on the worker after its gate.  All of these are idle presses - both run doors refuse a busy railway before the gate - in the safe direction, with a log line: TDU-C8's own grade.  Separately, Start's call is now inside a `try` on the worker, and an exception from the gate before `setAtomicRoutes(true)` is logged and the run starts non-atomic; before, on the event thread ahead of the greying, a throw ended the press.  The gate reads two lists and can hardly throw - noted because Start is now the one door where a failed gate does not stop the dispatch.  `testStartAndReturnHomeAskTheGateAfterTheirRefusals` holds the orders it names (busy and power; power and no trains), not these.  Verification by reading.  Suggested fix: Execute Timetable's call below the start-square loop; Return Home's after `plan.isPossible()`, on the event thread as Start's is (if the planner does not depend on the setting); at Start, give the button back and return when the gate throws.

### TDU2-C3 - Return Home and Execute Timetable stay offered over a broken setup, and every press is refused

| | |
|---|---|
| **Disposition** | Fixed - Adam, 2026-09-24: *"Yes, go with your recommendation."*  The right-click Return Home item greys with the setup's sentence; the buttons stay live and explain.  Claim 7dc22256, fix f17f5a5c.  MT-580. |
| **Where** | `HomeLocomotiveMenu.java:55-73` (`offered = !busy && isReturnHomeOffered()`); `TrainControlUI.java:24243-24271` (`refreshReturnHomeButton` - no setup question); the right-click Start item greys on `canStartAutonomy` (`:23732-23736`); `492ad392` |

TDU-B1's fix added the refusal and not the affordance.  On the diagram's right-click menu over a broken setup, Start is greyed with the setup's reason, and Return Home beside it is live and answers a click with the refusal; the Return Home button keeps the triage's tooltip.  For the hand destinations this is what Adam asked for (*"which should throw an error instead"*, TDU-D2), and Start's button is deliberately live and explains; but Return Home's item follows the other rule - *"shown always and greyed when there is nothing to do ... and says why it is unavailable"* (its javadoc).  Nothing moves; it is the guard-and-affordance question, for Adam.  Options: grey the item and the button with `whyAHandSendIsRefused()` as the tooltip, or keep them live as the hand doors are and say so in behaviour.md 1.

### TDU2-C4 - TDA-C7 renamed the rule in English only, and the berth refusal's own sentence still says "room rule" and "the whole approach is claimed"

| | |
|---|---|
| **Disposition** | Fixed - 84a89426: the half-measured notice names no single rule in any language; the berth refusal's note says the train is taken to reach further back than it may, not that the whole approach is claimed; both notes follow a full stop. |
| **Where** | `autosetup.ui.checkHalfMeasuredApproach` (English `messages.properties:1395` "the berth rule"; the seven others: rumreglen, Platzregel, regla de espacio, regle de place, regola dello spazio, ruimteregel, regula miejsca); `autolayout.errorBerthApproachPartlyUnmeasured` (`messages.properties:172`), appended at `Layout.java:10287-10290`; `10d7f523` |

behaviour.md keeps the room rule (the track past the last switch) and the berth rule (5b) apart.  `10d7f523` changed the notice to name the berth rule in English and left the room rule in the seven translations, whose new endings are otherwise right.  And the refusal the notice predicts - `errorBerthWouldFoulAnotherRoad` with its appended sentence - still says *"the room rule counts an unmeasured square as nothing - so the whole approach is claimed"*, the phrase TDA-C7 took out of the notice: the walk stops once the train is spent (`Layout.java:10268`), and the sentence is appended whenever any square it claimed is unmeasured, so it can say the whole approach was claimed when the walk stopped part-way.  Wording only.  Suggested fix: the berth rule's name in the seven notices and in `errorBerthApproachPartlyUnmeasured`, and "so the whole approach is claimed" only where the walk ran out of places.

### TDU2-C5 - behaviour.md still lists the old autonomy tab's Validate as a load door

| | |
|---|---|
| **Disposition** | Fixed - 244331ce: behaviour.md names the one load door, the editor's apply, and says where Validate went; the gate's two javadocs that said both load doors say the load door. |
| **Where** | `docs/reference/behaviour.md:1576`; against `TrainControlUI.java:6086-6088` and `:4255` |

5d: *"the two load doors (Validate on the autonomy tab, and the editor's apply) force the setting back ON"*.  Validate went with the JSON tab in `a4674c9b` (OB-254; TDU-D5 checked that removal), and the gate's own javadoc says so.  TDU-C10's pass over behaviour.md (`28469a46`) wrote the range's UI rules and did not reach this sentence.  One load door now.  Documentation only.

### TDU2-C6 - MT-573 cannot reach its step 3 when every train is home

| | |
|---|---|
| **Disposition** | Fixed - as TDD2-C9: the comment on MT-573 (244331ce). |
| **Where** | `docs/manual-tests/tests.md:28159-28185` (MT-573); Return Home greyed when there is nothing to do (`HomeLocomotiveMenu.java:55-73`, `TrainControlUI.java:24243-24271`); against MT-539's step 2, *"Send it somewhere else"* |

MT-573 breaks the setup and presses Return Home, expecting the setup's refusal.  The button and the item are offered only when the triage finds a train away from home; with every train at its home - how a session usually starts - Return Home is greyed, step 3 cannot be done, and a report of "greyed" reads as neither a pass nor a fail.  Entries are append-only, so a comment on MT-573: before step 2, send one train away from its home (and bring it back after step 5).  No railway consequence.

### TDU2-C7 - comments and javadocs the fixes left naming two doors, or the wrong thread

| | |
|---|---|
| **Disposition** | Fixed - 244331ce: whyAHandSendIsRefused, the right-click door and the claim class name every door that asks; the diagram class's javadoc says the event-thread path is TDU-C2's pin; takesTheClick says one double-click. |
| **Where** | `TrainControlUI.java:23822-23832`; `LayoutRightclickAutonomyMenu.java:1314-1316`; `test/regression/testAHandSendIsRefusedWhileTheSetupIsBroken.java:29-33`; `test/regression/testTheTailIsPickedOnTheDiagram.java:37-39`; `TailCrossedPrompt.java:559` |

- `whyAHandSendIsRefused`' javadoc: *"both hand doors ask it"*.  Since `492ad392` four doors ask it, two of them run doors, and the method's name says hand; a reader looking from the rule for who refuses a broken setup finds two of the five.  The right-click door's comment (*"asked at both hand doors - this one and the Auto tab's list of paths"*) and the claim class's javadoc (*"at both hand doors"*) say the same.
- `testTheTailIsPickedOnTheDiagram`'s class javadoc still says the off-thread ask is *"the way a placement door asks it"* - the sentence TDU-C2 was about.  The pin was added; the sentence was kept.
- `answeredBy`'s javadoc: *"for the rest of its double-click"* (see TDU2-C1).

Nothing behaves differently.

## D - checked and clean

### TDU2-D1 - TDU-B1: the two run doors refuse a broken setup, as the disposition says

| | |
|---|---|
| **Disposition** | Checked - clean. |

Return Home asks `whyAHandSendIsRefused()` after the editor refusal and before busy, power and the plan (`TrainControlUI.java:23937-23945`); Execute Timetable before it greys its button (`:26663-26673`), so the button stays usable.  No ERROR finding in `AutonomyChecks` is one a Return Home run would clear - blocking graph problems, an unnamed or unlabelled station, a sensor on two pages, a terminus with two ways in, no stations, and a locomotive placed twice in the setup (which the build refuses outright anyway) - so the refusal does not stand in front of its own remedy.  `testTheRunDoorsAskItToo` reads unstripped source, but no comment in either door holds the literal call, so removing it moves the index past the act or to -1: red.  behaviour.md 1 states the rule; MT-573 carries the railway check (TDU2-C6).  Order note, not raised: the run doors ask the setup before the power, the hand doors after it, so with both wrong the two kinds of door give different first sentences.

### TDU2-D2 - TDU-B3: the double-click that answers does not flip the sensor

| | |
|---|---|
| **Disposition** | Checked - clean. |

The second click arrives after `finish` has cleared `armed`, and `answeredBy` swallows it.  On the event-thread path `loop.exit()` ends the nested loop after the answering event, so the second click is dispatched by the outer loop, where the same test applies.  The claim dispatches counts 1 and 2 and compares the feedback state (mutation F1).  The guard's scope is TDU2-C1.

### TDU2-D3 - TDU-C1: with an editor window open the question is the list

| | |
|---|---|
| **Disposition** | Checked - clean. |

`DiagramPick.of` returns null when `isLayoutEditorOpen()` (`TailCrossedPrompt.java:602-605`); the autonomy editor is hosted in `LayoutEditor` (`LayoutEditor.java:1678`), which is what `openEditor` holds.  The paste door, the diagram keys and the diagram's autonomy menu (`TrainControlUI.java:4644`) stand down while an editor is open, so only the editor's own door reaches the new branch, and it gets the list owned by the main window, as before FR-100.  The claim sets `openEditor` and asserts nothing is armed (F3).

### TDU2-D4 - TDU-C2, TDU-C3 and TDU-C4: the pins catch their mutations

| | |
|---|---|
| **Disposition** | Checked - clean. |

- TDU-C2: the pin asks inside `invokeLater` (the secondary loop), clicks through `invokeAndWait` (dispatched by the nested loop) and waits on the reference; without `loop.exit()` the reference stays null (F4).
- TDU-C3: the pin asks the instance method on a real window over a sandboxed live-snapshot, broken by an unnamed station and mended.  "Answers nothing" fails at the broken case; an inverted guard fails earlier, at the precondition on the unbroken setup - red, but with a message that blames the fixture (*"the frozen railway's setup is refused before anything is broken"*) rather than the guard.  Worth a word in the message; not raised.
- TDU-C4: berth, then own tail, then the reversal question, at both doors; no comment in either door holds the literal names, so deleting a block moves the own-tail index past the question or to -1 (D3).

### TDU2-D5 - TDU-C5: "not a defect" is sound

| | |
|---|---|
| **Disposition** | Checked - clean. |

A rail's places end at the square it arrives at (`Layout.java:10258-10261`), `reachOf` spends from that end, and an edge's length is the sum of its squares (behaviour.md 5d).  For the question to mean nothing the tail must end on squares both rails share, the arrival square among them: if any shared square is measured, both rails have a length and cover the same set; if none is, the measured rail's tail runs past the parting onto its own track and the question is a real one.  Only a rail with no places differs, as the disposition says.

### TDU2-D6 - TDU-C7, TDU-C9, TDU-C10 and TDU-C11 hold

| | |
|---|---|
| **Disposition** | Checked - clean. |

- TDU-C7 (`858317f2`): the tooltip and the click decide on the same two questions (`AutonomyEditorPanel.java:2336-2344` against `:10057-10070`); the 0 sentence is chosen on `known`, which is non-null exactly in the every-train mode.  The claim strips tags from the dialog's HTML label, and `wrapped` inserts no breaks, so its substring can fail (W1).
- TDU-C9 (`b62f1976`): the grey reads `placesCoveredByStandingTrains`' keys (`AutonomySession.java:6985`); Mass Assign Lengths takes 0 for pieces, switches and crossings; `appendTailCrossed`'s comment matches `tailsPart`'s MT-477 arm; `copyFacing`'s second loop is the fall-back its comment now names (`AutonomySession.java:1905-1919`).
- TDU-C10 (`28469a46`): behaviour.md 1 (the setup rule at the run doors), 5b (the renamed items, 1 to 40, never greyed), 5c (FR-100 with TDU-C1, B3, B2; OB-294; TDD-A1) are there.  5d's Validate is TDU2-C5.
- TDU-C11: the pin gives the panel a running layout with the train where the setup has none and none where it has one; both named mutations fail.  The version the disposition cites, `a961695e`, built its model with no sandbox, so it would have read the machine's own layout preference (OB-111); `be87cb65` opens one first, seventeen minutes later.  The pin holds at HEAD; whether the unsandboxed version ran in between is for that window's battery log.

### TDU2-D7 - TDU-B4 and TDU-C6: the open dispositions still describe the code

| | |
|---|---|
| **Disposition** | Checked - clean. |

TDU-B4: `placeSomewhereLegal` still picks at random (`LayoutRightclickAutonomyMenu.java:1082`); `bd27c357` is dated 2026-08-18, older than the range; OB-296 is filed with the question.  TDU-C6: `unmeasuredTrackThatCouldBeReleased` still skips only edges with a length (`Layout.java:9586`), with no answered-zero test, so the question stands as written - it gives the options and no recommendation, which FANOUT asks for.

### TDU2-D8 - the message bundles after the range

| | |
|---|---|
| **Disposition** | Checked - clean. |

1827 keys in each of the eight (1825 plus `autolayout.errorOwnTailPartlyUnmeasured` and `autosetup.ui.errorTrainLengthOutOfRangeKnown`), identical key sets, every file pure ASCII; the new and changed values carry the same placeholders in every language and no straight apostrophe; `autolayout.errorWouldMeetItsOwnTail` keeps `{0}`-`{3}` in all eight.  The wording is TDU2-C4.

### TDU2-D9 - TDU-C8 for the cases it named, and TDD-C11's javadoc

| | |
|---|---|
| **Disposition** | Checked - clean. |

Start's gate runs on the worker after the power, the conditional-route warning, no locomotives and `isValid() && !isAutonomyBusy()`, on the event thread through `invokeAndWait` from a worker that holds nothing, so the wait cannot deadlock; Return Home's after busy and power.  The claim holds those orders; the rest is TDU2-C2.  `865b4168`: the javadoc now says the untick makes the next start load nothing, as the log line (`autosetup.ui.autoLoadOffAfterImport`) and `Readme.md:374` do.
