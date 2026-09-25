# User interface - validation round 4 of the fixes `67f79912..7c38b51a`

**Status:** open

**Prefix:** `TDU4`

**Reviewed:** branch `autonomy-diagram-r0` at `7c38b51a` (HEAD), 2026-09-24.  The previous round's document `docs/reviews-2026-09-24/TDU3.md` and its dispositions, and the four commits `6bfab5bb`, `32a75f8d`, `59cf3645` and `7c38b51a`.  Lane: their `src/org/traincontrol/gui` and message-bundle halves, the tests that claim them, the behaviour.md, open-questions.md, tests.md and issues.md text they wrote about the lane, and the three issue-list items no earlier round reviewed: OB-286, FR-098 and OB-287.

**Method:** I read `docs/reviews/FANOUT.md`, `docs/reviews/README.md` and `TDU3.md` first, and TDA3's and TDD3's dispositions where they touch the lane (TDA3-C3's bundle sweep, TDD3-C7's pins, TDD3-C8's gate claim).  Then `git log --stat` of the range and `git show` of every lane part of the four commits:

- `32a75f8d`: TailCrossedPrompt, GraphLocAssign, LayoutRightclickAutonomyMenu, TrainControlUI, LocomotiveFunctionAssign, RouteEditorFrame and the eight bundles.
- `59cf3645`: AutoLocomotiveStatus, the TrainControlUI comment, three bundles, behaviour.md 5c, open-questions.md, and the javadocs of testNonAtomicRoutesNeedTheirLengths and testAutoLayout.
- `6bfab5bb`: the five lane test classes and `build.xml`.
- `7c38b51a`: the MT-573 comment, MT-576 to MT-578, the three issues.md receipts and the recounts.

Then I read the code at HEAD that each fix depends on:

- **The three placement doors:** the paste door (`rememberPlacement`), the right-click Place facing door, and `GraphLocAssign.commitAndRecord`.
- **The tail question:** `TailCrossedPrompt` (`placementStillStands`, `whereTheAnswerGoes`, `writeRoad`, `noteADroppedAnswer`, `DiagramPick.of`/`ask`/`arm`).
- **The railway model:** `Point.setLocomotive`/`assign`/`setArrivedAlong`, `Edge.equals`, and `Layout.getPoint`/`roadNamed`/`startableTwinOf` and its constructor.
- **The setup:** `AutonomySession.placeLocomotive`/`setFacing`/`setPointProperty`/`writePointProperty`/`save`/`saveWithoutReconciling`.
- **What can happen in the wait:**
  - `rebuildRunningLayoutFromSetup`, `whereTheTrainsAre`, `putTheTrainsBack`
  - `resetAutonomySession` and its four callers, `captureRunningLayout`, `getAutonomySession`
  - `AutonomyViewerPanel.load`
  - `AutonomyEditorPanel.rebuildRunningLayoutSoon`/`appendTailCrossed`/`armTailPick`/`recordTailRoad`
  - `MarklinControlStation.getAutoLayout`/`hasAutoLayout`
- **The three run doors' gate handling** (Start, Return Home, Execute Timetable).
- **FR-098:** the whole of `LocomotiveFunctionAssign`, with `setFunctionIcon` and the standalone door.
- **OB-286:** `RouteEditorFrame.highlightOnDiagram` and both `highlightAccessories` overloads.
- **OB-287:** `refuseWhileARouteDrivesIt` and every GUI door that deletes or renames a locomotive.

For each claim I asked whether it was red for the finding's own reason and whether its named mutation reaches its assertion.  The bundles were checked with read-only `grep`/`diff` pipelines: key sets, non-ASCII bytes, placeholders, and the menu labels the notices quote.

Nothing was run, compiled or started, and no file was written but this one.  One recursive `grep -rln` for the mutation names (`R3a`, `R3g`), run from the repository root, searched `cs2_sample_layout/` along with everything else before its output was filtered.  It listed only two review documents; nothing from that folder was read or used.  The frozen railway was not needed.  Every finding that depends on behaviour says what execution would settle it.

## A - high

None found.

## B - medium

None found.

## C - low

### TDU4-C1 - after another configuration is loaded in the wait, the answer is not always dropped: it lands in the other configuration when that configuration holds the same train on the same copy, and the configuration it was asked for never gets it

| | |
|---|---|
| **Disposition** | Fixed - claim testAnAnswerIsNotWrittenIntoAnotherConfiguration in 665569f3 (red first), fix 6c7d5373: sameSetup asks for the same session and the same active configuration.  Mutation R4a red. |
| **Where** | `TailCrossedPrompt.java:564-572` (`whereTheAnswerGoes`) and its javadoc at `:551-554`; `AutonomyViewerPanel.java:760-780` (`load`: capture into the outgoing configuration, then `setActiveConfiguration` on the SAME session); `AutonomySession.java:8440-8446` (`writePointProperty` writes to the store's active configuration); the doors' `sameSetup` argument (`getAutonomySession() == session`), `TrainControlUI.java:8181`, `LayoutRightclickAutonomyMenu.java:1247`, `GraphLocAssign.java:324`; commit `32a75f8d` |

TDU3-B1's disposition, and the new javadoc, say: *"after another configuration is loaded the copy holds that configuration's train or none, and the answer goes nowhere"*.

That holds only while the other configuration does not place **the same train on the same copy from the same side with no recorded road**.  Loading a configuration does not replace the session, so `sameSetup` stays true.  `whereTheAnswerGoes` then asks the new running copy of that name.  The locomotive object is the model's, so the reference check passes, and so do the side check and the null road.  So the door writes the answer into the store's active configuration - the one just loaded - and says nothing, because to the door it was recorded.

Before the switch, `load` captured the configuration the question was asked for.  That capture took the train, its side and the running layout's empty road, so that configuration never gets the answer.

The case is narrow, but configurations made by duplicating one another share placements.  Consider a train put back where it already stood in both configurations - cut and pasted to re-answer the question - with another configuration loaded before the click.

**On the railway.**  The answer is true of the physical train, so the configuration loaded now is right to hold it.  The configuration the operator was working in keeps no road.  When it is loaded again with the train still standing there, the tail walk stops at the switch, and a train can be routed through it into the standing train's tail - the TDU2-A1 consequence.  In this case no log line says so, because the answer counted as taken.

What mitigates it:
- It needs a configuration load in the wait, onto a configuration with an identical placement.
- The grey on the diagram visibly stops at the switch.
- Where the other configuration does not hold the train, the answer is dropped and logged as intended (see TDU4-C3 for what that line says).

(The same javadoc says the rebuilt copy has *"the road the setup recorded"*.  After `rebuildRunningLayoutFromSetup` it has the running layout's road - `putTheTrainsBack`, "the railway wins".  They agree in every case traced, so this is wording only.)

**Verification request.**
- **Fixture:** `regression.testTheTailIsPickedOnTheDiagram.pasteAndAnswerLate`.  Give the sandboxed store a second configuration whose `points` entry for Tunnel's square places the same train (`loc.name`), with `arrivedFrom` "N" and the paste's facing, and no `arrivedAlong`.
- **In the wait:** `ui.getAutonomyViewerPanel().load(second, false)` on the event thread.
- **Precondition:** the running `Tunnel (southbound)` holds the train, from N, with no road.  Then click TunnelPre.
- **Proves it:** the second configuration's Tunnel entry gains TunnelPre's road, the first configuration's entry has none, and the log carries no `logTailAnswerDropped` line.
- **Refutes it:** the second configuration's entry has no road and the line is logged.

Suggested fix: remember the active configuration's name when the question is asked, and treat a different name at the answer as "not the same setup".  That makes the code say what the disposition says.  Or, if Adam prefers the answer to follow the physical train, keep the behaviour and correct the javadoc and the disposition.

### TDU4-C2 - a door whose setup was replaced in the wait still saves the old setup, with the reconciling save the reset avoids on purpose; and it asks for the railway in a way that re-creates one after Unload

| | |
|---|---|
| **Disposition** | Fixed - claim testAnAnswerAfterTheSetupIsReplacedIsNotWritten in 665569f3 (red first), fix 6c7d5373: a door whose setup was let go in the wait saves nothing, and the railway is asked for through runningNow, which makes none.  Mutations R4b, R4c, R4f red. |
| **Where** | The saves after the question: `TrainControlUI.java:8205` (`noteIfTheSetupWasNotTidied(session.save())`), `LayoutRightclickAutonomyMenu.java:1276` (`AutonomyReport.show(ui, session.save())`), `GraphLocAssign.java:349`.  `AutonomySession.java:9335` (`save`, which reconciles against the session's own pages) against `:9432-9457` (`saveWithoutReconciling` and its javadoc).  The reset: `TrainControlUI.java:3051-3083` (`resetAutonomySession`), with callers `unloadAutonomy`, `autonomySetupDeleted`, `initializeTrackDiagram` and `layoutRefreshCompleteInternal` (`:23325`, with the layout editor's pages).  `MarklinControlStation.java:1004-1019` (`getAutoLayout` creates a `Layout` when there is none).  The new post-question calls `this.model.getAutoLayout()` / `ui.getModel().getAutoLayout()` / `edit.parent.getModel().getAutoLayout()` at the three `whereTheAnswerGoes` sites.  Commit `32a75f8d` |

TDU3-B1's third case was: *"the door writes into, and saves, the session object it holds.  Not traced further."*  The fix settles the first half: with `sameSetup` false, the answer goes nowhere and is logged.  The second half is untouched.  All three doors still call `save()` on the session they captured before the question - a session the window has since dropped.

That save is the reconciling one, and it is the save `captureRunningLayout` deliberately does not make on this path.  `saveWithoutReconciling`'s javadoc gives the reason: after the layout editor, the old session's pages are the ones the editor mutated in place.  If the operator cancelled the edit, reconciling against them *"deleted the names, lengths, directions and arrival restrictions of every square the user had deleted and thought better of"*.

The reachable sequence:
1. Paste a train where the tail question is asked.
2. While it waits, open the track-diagram editor, delete a square that carries a station name, and close with Cancel.  `layoutRefreshCompleteInternal` resets the session, which captures and writes without reconciling, and reloads the configuration into a new session.
3. Answer or cancel the question.  The door's `session.save()` prunes that square's settings from the file.  The right-click door also shows the reconciliation report, listing settings removed that the operator never removed.

The new session still holds them in memory, so its next save puts them back - any setup edit, the next reset, or the exit capture.  The loss survives only if nothing saves the new session before the application ends: a crash, or a start-up resume that failed, leaving `activeDiagramConfiguration` null so the exit capture skips.

Separately, and new in this range: each door now evaluates `model.getAutoLayout()` after the wait, as `whereTheAnswerGoes`' first argument, whatever `sameSetup` says.  After **Unload** in the wait (`clearAutoLayout`, then the reset), that call builds a fresh empty `Layout`: `hasAutoLayout()` answers true about nothing, `Layout.layoutVersion` ticks, and `Layout.lastError` is cleared.  CS3-C4 describes this state as the one to avoid.  Every GUI reader I checked treats an empty railway as not loaded (`refreshAutonomyTabState`, `updateVisiblePoints`, `repaintAutoLocList`), so I found no visible effect.  It is a side effect in a check, and `hasAutoLayout() ? getAutoLayout() : null` removes it.

**On the railway.**  Nothing moves.  Setup settings can be pruned on disk and, in the right-click door, reported to the operator as removed, until the live session next saves.  Graded C because of that repair and because the sequence needs an editor opened and cancelled while the question waits.

**Verification request.**
- **Fixture:** `pasteAndAnswerLate`.  In the wait, hold the old session (`ui.getAutonomySession()`), remove one named tile from one of its `LayoutDiagram` objects in place (what the editor does), then on the event thread call `ui.resetAutonomySession()` and `ui.getAutonomyViewerPanel().load(active, false)`, as the layout editor's close does.  Press Cancel.
- **Proves it:** the setup file's entry for that tile is gone after the door returns, while `ui.getAutonomySession()` still has it.
- **Refutes it:** the file keeps it.
- **For the second half:** `unloadAutonomy()` in the wait, then Cancel.  **Proves it:** `model.hasAutoLayout()` is true after the door returns.

Suggested fix: when `sameSetup` is false, skip the save as well as the answer, since the reset has already captured and saved everything the door wrote before the question.

### TDU4-C3 - the new "was not recorded" log line has no claim, fires for Cancel and Not Known, and gives a remedy that is impossible or already done in most of the ways an answer is dropped

| | |
|---|---|
| **Disposition** | Fixed - claim testOnlyADroppedAnswerIsLogged in 665569f3 (red first), fix 6c7d5373: only an answer is logged, the square named as the diagram names it, with no remedy it cannot keep, in eight languages.  Mutations R4d, R4e red. |
| **Where** | `TailCrossedPrompt.java:599-602` (`noteADroppedAnswer`); the three `else` branches, `TrainControlUI.java:8195-8198`, `LayoutRightclickAutonomyMenu.java:1260-1263`, `GraphLocAssign.java:334-337`; `messages.properties` `autolayout.ui.logTailAnswerDropped` and its seven translations; `AutonomyEditorPanel.java:3875-3916` (where "Where the tail lies" is offered: only for the train standing on the square, inside the facing submenu); `docs/manual-tests/tests.md` MT-576 step 7; TDU3-C2's disposition; commit `32a75f8d` |

The line reads: *"Where the tail of {0} lies was not recorded: {1} changed while the question waited.  Set it again under Where the tail lies, on the right-click menu of {1}."*

- **No claim.**  Nothing in `test/` names `noteADroppedAnswer` or the key.  Deleting the three calls leaves every class green.  TDU3-C2 is dispositioned "Fixed" without one, where FANOUT step 3 asks for a claim seen failing.
- **It fires on answers that are not answers.**  The `else` branch runs for every reply, including Cancel and **Not known**.  An operator who presses Cancel on a question made stale in the wait is told something "was not recorded" when they asked for nothing to be recorded.
- **The remedy fits two of the drop's causes.**  The check drops the answer when:
  - **Another train stands on the copy**, brought by a run.  The menu then offers that train's tail, not {0}'s.
  - **{0} was taken off** (MT-576 step 6).  Then no "Where the tail lies" section is offered at all: `appendTailCrossed` returns when the square holds no train.  MT-576's expected result shows Adam this impossible instruction.
  - **The road changed.**  Either a run brought {0} back, in which case the run's road is right and nothing needs setting.  Or the operator answered from that very menu instead of the lit squares - TDU3-C1's third point, where the menu's radio writes the road and the squares stay lit.  Dismissing the question afterwards tells them to do what they just did.
  - **Another configuration was loaded** (TDU4-C1).  The instruction can only be followed after going back.
  - **The side changed, or the session was replaced.**  Here "set it again" is right.
- **{1} is the copy's name** (`point.getName()`, e.g. "Tunnel (southbound)"), where the diagram and the rest of the sentence speak of the square.  MT-576 expects "Tunnel".

**On the railway.**  None.  It is a log line, and the answer it reports is correctly not written.  But it is the operator's only explanation of a dropped answer (the reason TDU3-C2 asked for it), and in the common causes it points at a remedy that cannot be carried out or is already done.

**Verification request.**
- **Fixture:** `pasteAndAnswerLate` with a log listener on `model`.  In the wait:
  - (a) `model.getAutoLayout().moveLocomotive(null, "Tunnel (southbound)", true)`, then Cancel;
  - (b) set both stores' road to TunnelPre's, as `recordTailRoad` does, then Cancel.
- **Proves it:** the line, with its "Set it again" sentence, is logged in both.
- **Refutes it:** no line in either.

Suggested fix:
- Log only for a reply that carried an answer (`Reply.answered`).
- Say "the train has moved or another train is there" rather than instructing, or instruct only where `placed` still stands on the square.
- Name the square by its station name.
- Claim it with (a) and one answered click.

### TDU4-C4 - the claims still prove less than the dispositions: the facing move, the setup check, the running-railway argument at two of the three doors, and the edge translation are each unclaimed

| | |
|---|---|
| **Disposition** | Fixed - pins in 665569f3: the facing before the question (R4g red), a replaced session (R4b red), every door's railway argument (R4f red), and the new railway's own edges after a rebuild (R4h red). |
| **Where** | `TrainControlUI.java:8152-8154` and `LayoutRightclickAutonomyMenu.java:1218` (facing written before the question); `TailCrossedPrompt.java:567` (`!sameSetup`), `:587-588` (`writeRoad`'s `running.roadNamed` translation); `GraphLocAssign.java:322-324`, `LayoutRightclickAutonomyMenu.java:1245-1247` (the `running` argument); `test/regression/testTheTailIsPickedOnTheDiagram.java:614-640` (the pin), `:817-864` (the rebuild claim, compared through `named(...)`); `core.testAPasteDoesNotTurnTheTrainRound`; TDU3-B1's and TDU3-C1's dispositions |

TDU3-C1's first point was that the facing half of TDU2-A1's fix had no claim.  The fix restructured the doors and still claims nothing about the facing.  Its named mutations R3g to R3i are the gate's.  More generally, of `32a75f8d`'s tail-answer changes only the paste door's rebuild case is claimed behaviourally:

| Mutation | What catches it today |
|---|---|
| Move the paste door's `session.setFacing(...)` back below the question, inside `if (landing != null)` (the pre-TDU3-C1 shape); the same at the right-click door | Nothing.  The claims assert the road only; `testAPasteDoesNotTurnTheTrainRound` reads the `setFacing(tile, facingChosen != null ? facingChosen` text, which moves intact |
| Drop `!sameSetup \|\|` from `whereTheAnswerGoes` | Nothing.  No claim replaces the session in the wait |
| Pass `layout` (the dialog's pre-question railway) instead of `edit.parent.getModel().getAutoLayout()` in `GraphLocAssign`, or `running` at the right-click door - TDU3-B1 back at those two doors | Nothing.  The pin checks that `whereTheAnswerGoes(` sits between `askAfterPlacement(` and `setArrivedAlong(`, not what it is asked of |
| `writeRoad` always `landing.setArrivedAlong(road)` - the old railway's edges on the new copy | Nothing.  The rebuild claim compares `namesOfRoad` of both, and `Edge.equals` is by name |

The last row may be equivalent.  The tail walk reaches the road through `roadBackAtTheFirstHop`, comparing places with `isSamePlaceAs`, and the capture writes names, so old edges may block the same track.  If so, record it as equivalent.

**On the railway.**  None today; the code at HEAD does what the dispositions say.  But these are the halves a later simplification is likely to undo - passing the variable already in scope, or moving the facing back beside the road - and nothing would say so.

**Verification request.**  `mutate.py` with each row above, against `regression.testTheTailIsPickedOnTheDiagram`, `core.testAPasteDoesNotTurnTheTrainRound` and every class citing TDU3-B1 or TDU3-C1.  **Proves it:** all green.  **Refutes it:** any red.  For the last row, add a probe: after a rebuild, write the old railway's TunnelPre road on the new Tunnel copy and ask `edgesCoveredByStandingTrains`.  **Equivalent** if it claims the same edges as the translated road.

### TDU4-C5 - two translations of the half-measured notice still name Mass Assign Lengths by words that are not its menu label

| | |
|---|---|
| **Disposition** | Fixed - 6c7d5373: it and pl quote the menu label. |
| **Where** | `messages_it.properties:1398` against `:1423`; `messages_pl.properties:1396` against `:1421`; TDA3-C3's disposition (*"the Mass Assign label in da, de and fr"*); commit `59cf3645` |

TDA3-C3 found the notice naming the tool by words other than the menu's label, in three translations.  `59cf3645` made da, de and fr quote the label, and es and nl already did.  Two still paraphrase:

| Bundle | The notice says | The menu says |
|---|---|---|
| it | *"l'assegnazione in blocco delle lunghezze le percorre"* | *"Assegna lunghezze in blocco..."* |
| pl | *"Zbiorcze przypisanie dlugosci je przechodzi"* | *"Przypisz dlugosci zbiorczo..."* |

Both are noun phrases built from the label's own words, which is why TDA3-C3's pass let them by.  But French was the same shape (*"l'attribution groupee des longueurs"*) and was changed, so the rule the fix applied is "quote the label", and two bundles do not.

**On the railway.**  None.  An Italian or Polish reader will very likely find the item from the shared roots.  Consistency only.  Suggested fix: *"Assegna lunghezze in blocco le percorre"* and *"Przypisz dlugosci zbiorczo je przechodzi"*, or whatever phrasing a translator prefers that keeps the label verbatim, ASCII-escaped as the bundles require.

## D - checked and clean

### TDU4-D1 - TDU3-B1: after a rebuild of the same setup the answer reaches the railway's copy, and the claim is red for the finding's reason

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The code does what the disposition says.**  `whereTheAnswerGoes` asks `running.getPoint(asked.getName())`, the copy on the railway running at answer time.
  - A rebuild through `rebuildRunningLayoutFromSetup` puts the train back on the same-named Point with the running record's side and road (`whereTheTrainsAre`/`putTheTrainsBack`; the railway wins).
  - The locomotive object is the model's (`getLocByName`), so the reference check holds.
  - `startableTwinOf` redirects only from a non-destination copy, which the paste's `moveLocomotive` never lands on.
  - `Edge.equals` is by name, so a road held when the question was asked still compares equal across a rebuild.
  - `writeRoad` resolves the road by name on the new railway, all or nothing.
  - A page left out, or a split renamed by a setting on the square itself, leaves no Point of that name, and the answer is dropped.
- **The claim** (`testAnAnswerAfterARebuildReachesTheRailway`) is sound.  At the rebuild both the running copy's road and the setup's are null, so only the door's write can supply the road.  Unfixed, the check passed on the old copy and wrote there, so the new copy's road stayed null - red for the finding's own reason.  R3a (ask the old copy) reaches it.
- **What it calls.**  It invokes `rebuildRunningLayoutFromSetup()`, whose javadoc names one caller.  The right-click setup change posts the `(true, edited)` form, which differs only in the message and in `placementsJustEdited` - empty for a paste.
- **Gaps:** the configuration case (TDU4-C1), the replaced session's save (TDU4-C2), and the unclaimed halves (TDU4-C4).

### TDU4-D2 - TDU3-B2: MT-573's replacement comment makes the backup record the train where it stands

| | |
|---|---|
| **Disposition** | Checked - clean. |

The comment (`tests.md:28206-28208`) has the train sent away before step 1.  Step 1 begins *"Close TrainControl"*, and the exit save captures the running layout into the setup (`captureRunningLayout`; its guards - a declined edit waiting, a railway running or invalid - do not apply in the test's order).  So the copied folder records the train at the station it was sent to.  Step 2 breaks the setup; the editor's close leaves the running layout alone, so Return Home is offered and refuses (TDU3-D7).  Step 5 restores a setup that agrees with the railway, and "after a restart, press Return Home" is then possible.  The entry itself is untouched; the correction is a new comment saying it replaces the one before, as the append-only rule wants.

### TDU4-D3 - TDU3-C1: the facing is written before the question at both doors, and the gates are claimed door by door

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The facing.**  The paste door writes it at `TrainControlUI.java:8152-8154` and the right-click door at `LayoutRightclickAutonomyMenu.java:1218`, both before `askAfterPlacement`, as `GraphLocAssign` always did.  The move puts the paste door's `setFacing` before `setArrivedFrom`, which is harmless: `setFacing` writes one property and re-derives the station index, which does not read the facing's neighbours, and touches no arrival field.  The right-click door now saves whether or not the answer stands, where it used to skip the save on a dropped answer.  So the facing and placement it wrote reach the file, which is the better behaviour.
- **The gates.**  `testAGateThatFailsStopsEveryRunDoor` finds each door's gate call, a `catch` within 400 characters, and `return;` in its body - plus `setEnabled(true)` for Execute Timetable.  The anchors are unique in their regions: Start `26936-27120`, Return Home `23984-24221`, Execute Timetable's handler.  Removing any `return;` or that `setEnabled` fails it by name.
- **Gap:** the facing half is unclaimed (TDU4-C4).

### TDU4-D4 - TDU3-C2: a dropped answer is logged at every door and behaviour.md says so

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The log.**  `noteADroppedAnswer` sits in the `else` of all three doors.  Its arguments are non-null there: `placed` is guarded, and so is `landing` in the right-click door's branch.  The key is in all eight bundles.
- **behaviour.md 5c** (`:1107-1111`) states the rule: the running copy still holds the train, with its side and road; a rebuilt railway's new copy is asked; otherwise the answer is dropped and logged; the facing was written before.
- **What the line says and when it fires:** TDU4-C3.

### TDU4-D5 - TDU3-C3: the door counts and door lists now agree with the code

| | |
|---|---|
| **Disposition** | Checked - clean. |

The gate has six call sites:
- the load door, `AutonomyViewerPanel.java:821`;
- five dispatch doors: `AutoLocomotiveStatus.java:1192`, `LayoutRightclickAutonomyMenu.java:1420`, and `TrainControlUI.java:24114`, `:26858`, `:27042`.

With the checkbox that makes seven, as `testAutoLayout.java:639-640` now says - one load door, not both.  (TDU3-C3's "six" was a miscount; the fix counts correctly.)  Start's comment says the third door of three (checkbox, file door, Start), then *"one of five dispatch doors"*; the claim's javadoc says *"the other two doors ... the file door"*.  The Auto tab's comment names the right-click destinations, Execute Timetable and Return Home, as its twin in the right-click menu does.  Comments only; nothing behaves differently.

### TDU4-D6 - TDU3-C4: Execute Timetable's failed gate stops the run and gives the button back

| | |
|---|---|
| **Disposition** | Checked - clean. |

The gate is at `TrainControlUI.java:26855-26867`, after every refusal, on the event thread, in `try`/`catch (RuntimeException)`.  The catch logs, calls `executeTimetable.setEnabled(true)` and returns before the worker starts.  Nothing else was greyed before the gate: `startAutonomy`, Return Home and `gracefulStop` are touched only in the worker.  Unfixed, the handler had no catch, so the claim was red for the finding's reason.  R3g (removing the `setEnabled`) reaches its assertion.

### TDU4-D7 - TDU3-C5: open-questions carries a recommendation that matches the code

| | |
|---|---|
| **Disposition** | Checked - clean. |

`open-questions.md:222-227` recommends that each affordance follow its Start twin: the right-click Return Home item greyed with the setup's sentence, and the Return Home and Execute Timetable buttons live and explaining.  That is the precedent at HEAD: Start's button refuses through `refuseAutonomyStartWhileBroken` while live, and Start's right-click item greys.  The question stays Adam's.

### TDU4-D8 - FR-098: Apply is greyed while the function on show is as the locomotive holds it

| | |
|---|---|
| **Disposition** | Checked - clean. |

`somethingToApply` compares the three things Apply writes - the icon (sanitised both sides), the trigger (through the same `triggerShownFor` the display uses, so a stored timing round-trips) and the custom picture.  "reset" counts only when a picture is stored.  `refreshApply` runs at every change of any of them:
- the item listeners on both combos;
- `updateFNumber`, which covers the function number, Reset and Copy Customizations;
- the picture chooser and Delete;
- the end of Apply.

External applies (`doApply`, the standalone window) do not read the button.

- **The claim** asserts both directions: greyed on opening, after changing back, and after Apply moves on; live after an icon change, a trigger change and a picture removal.  Unfixed, the button was always enabled, so the first assertion was red.
- **A transient I checked.**  Until the deferred icon load replaces the combo's model, every entry is one shared placeholder `ImageIcon`, so `getSelectedIndex()` answers 0 and Apply can read as live.  The load runs on the event thread and ends in `fNoItemStateChanged`, so no click can land in that state.
- **A behaviour change nothing mentions.**  Apply also steps to the next function, and pressing it with nothing changed was a way to walk the functions.  It now needs the function dropdown.  That is what Adam asked for.
- MT-577's menu path and labels exist.

### TDU4-D9 - OB-286: a three-way commanded under one address and checked under the other is lit once, as commanded

| | |
|---|---|
| **Disposition** | Checked - clean. |

`highlightAccessories(checked, CONDITION, hold, commanded)` leaves out every tile any commanded address reaches, which decides the rule per tile as OB-286 proposed.  A plain turnout that answers only to a checked address is still washed orange.  `RouteEditorFrame` is the only caller of the four-argument form.  The sensor half is already per address, and a sensor tile answers to one.  The claim finds a real three-way on the frozen railway's 1 - Main and compares pixels against both washes.  Unfixed, the orange wash painted last, so it was red; the named mutation (pass null for `notThese`) reaches it.  It skips on a headless machine.  A skip reads as green (the memory note on skips), so the battery has to run with a display for the claim to count.  MT-578's tooltip ("Switch N-N+1") and button label exist.

### TDU4-D10 - OB-287: both doors and the name proposal refuse a locomotive a running route drives

| | |
|---|---|
| **Disposition** | Checked - clean. |

A test-only change.  `refuseWhileARouteDrivesIt` guards `deleteLoc` (`TrainControlUI.java:21280`) and `changeLocAddress` (`:20393`, which renames and re-addresses), and the Central Station name proposal asks for both names (`:26312`, `:26314`).  No other GUI door deletes or renames a locomotive: `LocomotiveMenuItems` and the button menu call `deleteLoc`.  The claim drives both doors on a real window with a route held running by a 20-second delay, and reads the refusal's text.  Without the refusal, the next dialog's text differs and the locomotive check still applies, so either door's mutation fails.  The proposal is pinned by source, as its javadoc says.

### TDU4-D11 - the message bundles after the range

| | |
|---|---|
| **Disposition** | Checked - clean. |

- 1828 keys in each of the eight bundles, identical key sets, and no non-ASCII byte in any file.
- `autolayout.ui.logTailAnswerDropped` has `{0}` once and `{1}` twice in every language, with no straight apostrophe (French and Italian use `’`).
- The three changed notices (da, de, fr) keep `{0}` and `{2}` and now quote their menus' labels verbatim.

The two that do not are TDU4-C5.

### TDU4-D12 - MT-576 to MT-578 against the code

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **MT-576's steps reach what they claim.**  A right-click setting on another station posts `rebuildRunningLayoutSoon`, and right-clicks are not taken by the waiting question.  The pick stays armed across the rebuild, so step 3's click answers.  Step 6's **Remove {0}** (`removeLocomotiveHere`) empties the copy, so step 7's answer is dropped.  Step 5's "paste again" needs a cut first; a tester will see that.  What step 7's log line then tells Adam is TDU4-C3.
- **MT-577 and MT-578** are checked in TDU4-D8 and TDU4-D9.
- **The index rows and counts** (531 of 578) follow the three new entries.
