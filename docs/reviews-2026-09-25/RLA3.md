# RLA3 - Autonomy lane, round 3: validating the dispositions of RLA2 and what round 2's fixes left

**Status:** open

**Prefix:** RLA3

**Reviewed:** branch `autonomy-diagram-r0` at `d779578b` (round 2's records commit). The working tree was clean (`git --no-optional-locks status --porcelain` printed nothing). Reviewed read-only on 2026-09-25.

## Method

I read my lane's round-2 document `docs/reviews-2026-09-25/RLA2.md` whole, and `RLU2.md` and `RLD2.md` whole. From round 1 I read `RLA.md` whole, and `RLU.md` and `RLD.md` by heading and disposition. I read every commit of `git log 98b300d6..HEAD` as a diff with `git show`: the claims `5322fb65` and `1d9c8c66`, the fix `cc56a7a8`, and the records `d779578b`. The autonomy-area parts of those commits I read again in the surrounding code at HEAD:

- **`AutonomyViewerPanel`:** `importConfiguration` whole (the new refusal and both branches), `importLegacyGraph`, `activateTheConfigurationNamed`, `loadAfterImport`, `load` and `exportConfiguration`.
- **`AutonomySession`:** `importLegacy` whole (the seed, placements, homes, the `CARRIED_SETTINGS` gap-fill), `findTheImportedFacings`, `configurationExtras`, `configurationToLoadAfterImport`, `POINT_OPERATIONAL_KEYS`, `getFacing`/`setFacing`, `setAutoDestination`, `assignMaxTrainLength`, `hasNoMaximumTrainLength` and `tilesWithAMaxTrainLength`.
- **`AutonomyCompanionStore`:** `createConfiguration`, `importConfiguration`, `importBundle` (head) and `fileNameTaken`.
- **`AutonomyEditorPanel`:** `promptNumber`, `setUsage`, the speed multiplier's write, and the Max Train Length, Priority and Can Be Chosen items.
- **`Point`:** `toJSON` and `setLocomotive`.
- **`Layout`:** `whyItReachesNoStation`, `whyTheStartIsRefused`, the new `whyNoTrainIsStartedFrom`, `sanitizeMultiUnits`, `clearMultiUnitConflictsWith` and `refreshUI`.
- **`MarklinControlStation`:** `syncWithCS2` whole, and `isAutonomyRunning`.
- **`MarklinLocomotive`:** `commandedLocomotives` and the three model multi-unit accessors.
- **`TrainControlUI`:** the three locomotive edit doors (what they refresh after the sweep), the Change Name or Address guard, the `syncWithCS2` wrapper and each of its sixteen callers (for what they repaint), `repaintLayout` and `repaintLoc`.
- **`AutonomyMenu`:** the two Import items and `guardWhileEditing`.

Tests read:

- The new claims and pins of `5322fb65` and `1d9c8c66`: `testAnImportIntoTheConfigurationInUseIsRefused`, `testASecondImportFacesItsTrainTheWayTheFileRanIt`, `testASecondImportKeepsAHomeTheConfigurationHas` (with `d779578b`'s change to it), `testASyncThatGivesAMemberAStandingTrainsAddressTakesThatTrainOff`, `testReAddressingAMemberOfACentralStationMultiUnitTakesTheStandingTrainOff` and `testTurningRoundIsNotOfferedOntoACopyThatIsNoStation`.
- `testASecondImportFromTheMenuKeepsAHandMadeChange` (MT-298).
- `testASetupMovesToAOnePageLayout`, the only other test that drives the Import door, for whether the refusal reaches it.
- `testAnImportedFacingGuessCanStart`, for a source pin on the facing code.

Records read:

- behaviour.md's BPV-A1/RLA-B1, OB-183, answered-0, OB-299 and import paragraphs.
- `Edge.isMeasured`'s javadoc, and every other place in `src/` and `docs/reference/` that says "every length rule".
- `Automation.md`'s "why isn't it moving" entry, and OB-301 and OB-302 in `issues.md`.

Mechanical checks, all in memory, with inline Python fed through a pipe and nothing written to disk:

- The eight bundles at `cc56a7a8^` and at HEAD, read out of `git show`: key sets, duplicates, `{n}` placeholder sets, bytes above 127, empty values and straight apostrophes in values that carry a placeholder. I also decoded the three changed or new keys in every language.
- The MT-298 and MT-491 files in `docs/manual-tests/files`, to count the settings his own file carries.
- `docs/manual-tests/triage.db`, opened read-only (`mode=ro`), for the full status notes of the round-1 rows round 2 revised.

Every finding below was searched for in `docs/manual-tests/findings.tsv`: gap-fill, defaults, second import, the sync's multi-unit half, and import facings. It is not there; the related rows (RLU2-C11, RLA2-C4, REG3-C1, TDY3-B1, ACC-C4) are named where they bear. The prefix RLA3 is free. The two known items the brief lists (RLU2-C9, RLD-C1) are not reported.

Nothing was compiled or run. No JVM was started, no git state changed, and nothing under `cs2_sample_layout/` was read or written. This file is the only one written.

**Everything below is from reading.** Each finding that needs a run says so, and says what proves or refutes it.

Counts: no A, 1 B, 6 C, 7 D. The one B is a round-2 "not a defect" (RLU2-C11, RLA2-C4) whose premise the code contradicts. Nothing else new is above C.

---

### RLA3-B1 - A second import puts the file's setting back wherever the operator returned one to its default, because every editor door stores a default as nothing and the gap-fill reads nothing as a gap (RLU2-C11, RLA2-C4)

| | |
|---|---|
| **Disposition** | Open - Adam's decision: a gap-fill cannot tell a setting returned to its default from one never set - every editor door stores a default as nothing, and a capture writes a station maximum of 0 - so a second import of an old file fills both.  Put to him in the report and open-questions.md: keep the gap-fill (and say so in its question), or import an old file only into a configuration of its own, or remember what the first import wrote. |
| **Grade** | B |
| **Names** | RLU2-C11 and RLA2-C4 (maxima half). Both dispositions say *"a 0 is how a configuration states 'no limit', by hand or by a capture; the gap-fill cannot tell the two apart and keeps both, so 'nothing already set in it is changed' is true of it"*. Their record in behaviour.md is :2445-2446. |
| **Where** | `AutonomySession.importLegacy`, where the gap test is `if (!point.has(key) \|\| extras.has(key)) continue;` (:1167) and Can Be Chosen's is `if (!extras.has(AUTO_DESTINATION))` (:1191). The editor's writes are `AutonomyEditorPanel.promptNumber` (:4287, `value == unset ? null : value`, with unset 0 for Max Train Length :1542 and Priority :1739), `setUsage` (:4187, open is `null`), the speed multiplier (:4369, 100% is `null`) and `AutonomySession.setAutoDestination` (:5219, on is `null`). The capture's source is `Point.toJSON` (:1186 `active` only when off; :1196 `maxTrainLength` on every station). The question is `autosetup.ui.confirmImportFillsGaps` (messages.properties:1524). |
| **Needs execution** | yes - see the request |

**The premise is not what the code does.** The hand never writes a 0. Typing 0 at a station's Max Train Length stores nothing (`promptNumber`, unset 0), and Clear All Max Train Lengths writes `null`. `assignMaxTrainLength`'s own javadoc says *"0 is refused ... and the store writes it as no setting at all"* (AutonomySession:3787). The only writer of a stated `maxTrainLength: 0` is the capture, and that is because `Point.toJSON` writes the key for every station whatever it holds.

So the gap-fill can tell the two apart, and it gets both backwards:

- It keeps the capture's 0, which is the serialiser's default. This is RLU2-C11's case, and the codebase's other gap-fill for maxima reads that same 0 as a gap: Mass Assign's `hasNoMaximumTrainLength` answers *"true for a station with no maximum, or a maximum of 0"*.
- It overwrites a hand-cleared maximum with the file's value. That is the case the disposition meant to protect: RLU2-C11 named it as *"the trade to decide"*.

**And it is every carried setting, not only maxima.** Every editor door stores the default as nothing:

- a square switched back on (`active` removed);
- Can Be Chosen ticked again (`autoDestination` removed);
- Priority back to 0;
- Speed back to 100%.

The capture does not protect any of them. `Point.toJSON` writes `active`, `priority` and `speedMultiplier` only off their defaults, and `autoDestination` is not a capture key at all. So on a second import of the same old file into the same configuration (MT-298's case), wherever the operator put a setting back to its default between the two imports, the file's value is written again:

- a station he let autonomy choose goes back to not chosen;
- a square he opened is shut again;
- a priority he reset returns.

The door has just asked *"Add to it what the file has and {0} does not? Nothing already set in {0} is changed."*

His own file carries all of these. The MT-298 file (the same settings as MT-491's) has:

- 18 stations switched off. The import turns them into "on, but Can Be Chosen off" (REG-B1).
- 6 plain squares switched off: LowerDown, LowerDownPre, LowerParkingInner, LowerParkingReverse, TunnelLongParkReverse and TunnelParkReverse.
- 8 priorities, 1 speed multiplier and 7 maxima.

Ticking Can Be Chosen on a parking track that came in switched off is an ordinary thing to do after an import. So is opening one of those six squares.

**On the railway.** Nothing moves that was not told to, and every value written back is one 2.8.1 ran with, so the effects are on the safe side:

- a station stops being chosen;
- a square nothing may pass through closes a road, so trains find "no available paths" where they had one;
- a priority or a speed returns.

What the operator meets is a Yes that undoes his edits after promising not to, which is MT-298's rule broken for any edit that lands on a default. RLA-B2 was graded B for a question whose Yes did something else, and this is graded the same.

The only narrowing that applies is to maxima: a capture since the hand clear protects it, because it writes 0 again. (The refusal of RLA2-B1 means he must have switched away from the configuration to import into it, and switching away captures it.) The other four settings are exposed whether or not the configuration has run.

**Direction, for Adam.** It is the shape the old file's directions met (flagged then and narrowed to *"only onto a diagram with no direction set"*): a gap-fill is only as safe as the store's ability to tell a gap from a choice. Two options:

- Have a second import fill carried settings only on squares the configuration has no entry for. The first import creates an entry for every square it touches, so a second import then adds only what is new.
- Have the editor write an explicit default once a square has been touched.

Either way, behaviour.md's *"A station maximum of 0 is a statement ... and is kept"* should be restated to match whichever is chosen.

**Verification request.** Use the fixture of `regression.testTheImportDoorReadsAnOldFile.testASecondImportFromTheMenuKeepsAHandMadeChange`: a live-snapshot sandbox, with the MT-298 file imported from the menu into "MT-298 import", and that configuration chosen. In "MT-298 import", make three changes, each with the store write its editor door makes:

- (a) On a square the first import gave `autoDestination: false` (one of the file's 18 switched-off stations), `session.setAutoDestination(tile, true)`, which is what ticking Can Be Chosen writes.
- (b) On BottomMainB (the file gives it 4), `setPointProperty(tile, "maxTrainLength", null)`, which is what typing 0 writes.
- (c) On a matched square of the six the file switches off (LowerDown, say), `setPointProperty(tile, "active", null)`, which is what the editor's open writes.

Choose the running configuration again, and import the same file from the menu into "MT-298 import", answering Yes.

- **Proves:** after the import, (a) is `autoDestination: false`, (b) is `maxTrainLength: 4` and (c) is `active: false`. Each hand change has been undone.
- **Refutes:** each stays as the hand left it.
- **Control:** the existing claim's +3 on a maximum survives.

---

### RLA3-C1 - RLA2-B2's membership half: a Central Station multi-unit's new members that arrive during a run are applied and never swept

| | |
|---|---|
| **Disposition** | Follow-up - filed as OB-303. |
| **Grade** | C |
| **Names** | RLA2-B2 - *"the sync sweeps every locomotive it ... gives other Central Station members ... when autonomy is not running"* |
| **Where** | `MarklinControlStation.syncWithCS2`: the membership write (:1689-1694, no running check) and the sweep's guard (:1714, `!this.isAutonomyRunning()`). Compare the address branch, which is deferred while running (:1615) and swept by the next sync once the run has stopped (:1638). |
| **Needs execution** | yes - see the request |

The two halves of the fix handle a run differently.

- **Address change during a run:** deferred, not applied. The first sync after the run applies it, adds the locomotive to `sweptAfterTheSync`, and sweeps. That half is sound.
- **Membership change during a run:** `held.setModelMultiUnitLocomotives(...)` applies it at once (there never was a running check there), and the sweep is skipped because autonomy runs.

The skipped sweep is not kept anywhere. The next sync compares the database's names with the station's, finds them equal, and adds nothing, so no later sync ever sweeps it. When the run ends, a standing Z that joined standing M's traction on the Central Station's screen during the run still stands beside M. The next Start runs Z as a train of its own while M's commands move it. That is exactly RLA2-B2's consequence, through the one case its fix left.

The code's own comment says a sync *"is triggered automatically from a dozen places"*. So a membership edit on the Central Station during a run reaches TrainControl during the run.

Mitigations are RLA2-B2's own: the next load's sweep, or a Place of M, clears it, and it needs a Central Station edit that adds a standing train. There is one more narrowing: the edit must be made while autonomy runs. Hence C.

**Direction:** remember the locomotives whose sweep was skipped, and sweep them when the run stops (the model knows the moment). Or, as the address branch does, leave the members unapplied until autonomy is stopped. The first keeps the Central Station's truth in the model; the second hides it during a run.

**Verification request.** Use `testMockCentralStation`'s mock station, which serves a traktion in `test/lokomotive.cs2`. Before the sync, give the database's copy of that multi-unit M a member list without Z, where Z is one of the members the file serves. Stand M and Z on two stations of an autonomy layout. Mark autonomy running, or Return Home staging in progress. Then `model.syncWithCS2()`; clear the running mark; and `model.syncWithCS2()` again.

- **Proves:** Z still stands after the second sync.
- **Refutes:** Z is taken off by the second sync, or by the stop.

---

### RLA3-C2 - RLA2-B2's claim covers the address half only: the membership half and the running guard can go with every test green

| | |
|---|---|
| **Disposition** | Follow-up - filed as OB-303. |
| **Grade** | C |
| **Names** | RLA2-B2 (*"mutation S5 red"*), whose own verification request asked for the second half *"where the mock can serve a multi-unit"* |
| **Where** | `MarklinControlStation.java:1694` (`if (membersChanged) sweptAfterTheSync.add(held);`) and `:1714` (`&& !this.isAutonomyRunning()`); `test/core/testMockCentralStation.java:434` |
| **Needs execution** | yes - see the request |

`testASyncThatGivesAMemberAStandingTrainsAddressTakesThatTrainOff` is a real claim for what it covers:

- it links the member in TrainControl;
- the served file re-addresses it onto a standing train;
- it asserts the head stays and the other train goes.

With the sweep removed (S5) it is red. But the fix has two more parts, and nothing holds either of them:

- **The membership half.** Delete :1694 and the claim stays green, since its head is a TrainControl consist whose member's address changed. `testReAddressingAMemberOfACentralStationMultiUnitTakesTheStandingTrainOff` calls `sanitizeMultiUnits` itself, not the sync.
- **The running guard.** Delete `&& !this.isAutonomyRunning()` and the claim stays green, since no test syncs while autonomy runs. That guard is what stops a sync evicting a train mid-run, which is the dangerous direction of this code.

The fixture for both exists: `test/lokomotive.cs2` serves a traktion (line 1548, with members marked `.inTraktion`).

**Verification request.** Run the two mutations, one at a time, and run `core.testMockCentralStation` and `core.testALocomotiveDoesNotEvictItself`.

- **Proves:** green under each mutation.
- **Refutes:** red.

---

### RLA3-C3 - The sync's new sweep takes a train off the graph and refreshes nothing autonomy draws; the window doors it copies refresh three things after the same call

| | |
|---|---|
| **Disposition** | Follow-up - filed as OB-303. |
| **Grade** | C |
| **Names** | RLA2-B2 (*"as the window's edit doors ask it"*); OB-081's note at the edit doors |
| **Where** | `MarklinControlStation.java:1714-1722`; `TrainControlUI` :20549-20560 (Change Name or Address: `refreshUI`, `updateVisiblePoints`, `repaintAutoLocList`), :20956-20958 and :26446-26455; the `syncWithCS2` wrapper (:12135) and its callers, which repaint the locomotives and `repaintLayout` at most |
| **Needs execution** | yes - see the request |

Each window door follows `sanitizeMultiUnits` with three refreshes:

- `getAutoLayout().refreshUI()`, which fires the layout's callbacks that redraw the autonomy surfaces;
- `updateVisiblePoints()`;
- `repaintAutoLocList(false)`.

OB-081's note beside them says why: without them the diagram *"went on showing the old name beside the train until something unrelated happened to repaint it"*.

The sync's sweep does none of these. `Point.setLocomotive(null)` fires no callback: it only refreshes the protecting signal. I read every caller of the sync wrapper. Several `repaintLayout()` (which the import door's comment says ends in `updateVisiblePoints`), and none calls `refreshUI` or `repaintAutoLocList`.

So after a sync takes Z off, the autonomy locomotive list, and whatever else the layout's callbacks redraw, can go on showing Z standing where the model has nothing. This lasts until the next arrival, placement or panel refresh. The eviction is in the log (`autolayout.warnRemovedConflictingLocomotive`). An operator who reads the panel sees Z placed, presses Start, and Z does not move; Why not Moving? then says it is not on the graph.

It is cosmetic and transient, and it is the sibling the window doors were fixed for.

**Direction:** after the sweep, when anything was taken off, post `refreshUI` and the two repaints to the event thread, as the doors do.

**Verification request.** Use the real window over a sandbox, with the mock station configured as in RLA3-C2's fixture so that a sync's sweep takes Z off. Choose Locomotives > Sync with Central Station, and after it read the autonomy tab's entry for Z.

- **Proves:** it still shows Z at its station.
- **Refutes:** it shows Z not placed.

---

### RLA3-C4 - behaviour.md says an imported train faces the way the file ran it "whatever facing the square's last occupant left there"; where the file cannot say, the code keeps that facing, does not count it as a guess, and skips REG3-C1's arrival preference

| | |
|---|---|
| **Disposition** | Fixed in the records - behaviour.md says where the file cannot say the last occupant's facing stays (81a9fffa); counting it and preferring a way trains may arrive are filed as OB-304. |
| **Grade** | C |
| **Names** | RLA2-B3 (its disposition says *"keeping a recorded one only where the file cannot say"*); REG3-C1, ACC-C4 |
| **Where** | `AutonomySession.findTheImportedFacings` :740 (`if (ran == null && getFacing(tile) != null) continue;`) and its javadoc's new paragraph; behaviour.md :2444-2445 |
| **Needs execution** | only for the last point - see the request |

The disposition is accurate and the code does what it says: the file's facing wins where the file states one. behaviour.md drops the exception. It says *"A train the file places faces the way the file ran it, whatever facing the square's last occupant left there (RLA2-B3)"*. Where the file cannot say (no edges, edges both ways, or an end this diagram does not draw), the last occupant's facing is exactly what the train gets.

Two rules the guess otherwise keeps do not reach that branch:

- **The guess is not counted.** It `continue`s before `facingsInvented++`. ACC-C4 counted guesses *"so the log can say how many were guessed"*, and the new javadoc itself calls this facing *"a guess as good as the first copy's"*. The log's "facings guessed" line leaves it out, and so does the dialog.
- **The guess does not prefer a way trains may arrive.** REG3-C1 made the guess prefer such a way, because a guessed facing that only a copy trains may not arrive at holds *"stood the imported train where autonomy will not start it"* (TDY3-B1, graded B). The right-click Place keeps a train's heading over every copy it could leave by, *"a copy trains may not arrive at included"* (behaviour.md :701). So a last occupant stood that way leaves a barred facing on the square, and the next import stands its train on it. There, Why not Moving? says so and turning it round helps.

Before the fix the same facing was kept (the square was never asked), so nothing has got worse. On his railway the file says for 88 of 90 edges (REG4-A1), which makes this rare. Hence C.

**Direction:**

- behaviour.md: "...faces the way the file ran it, and where the file cannot say, the way the square's last occupant faced".
- In code: count the kept facing with the guesses, and keep it only where it is a way trains may arrive, otherwise guess as REG3-C1 does.

**Verification request (the barred half only).** Use the RLA2-B3 claim's fixture, choosing a placed square whose file edges run both ways or none (one the first import counted in `facingsInvented`). Take the train off, set the square's facing to the side only its barred copy holds, and import the file again into that configuration.

- **Proves:** the train stands facing the barred side, and the log's guess count does not include it.
- **Refutes:** it is stood a way trains may arrive, or the count includes it.

---

### RLA3-C5 - Three skips of an old-file import are still silent: a home the file names twice, a placement onto a square that has another train, and a home onto a square that has another train's home

| | |
|---|---|
| **Disposition** | Follow-up - filed as OB-304. |
| **Grade** | C |
| **Names** | RLA2-C3 (*"`duplicateHomes` ... read nowhere in `src/` ... Counted so it can be said out loud"*); RLA-B2's direction (*"reporting the ones skipped"*) |
| **Where** | `AutonomySession.importLegacy` :1104 (`standing != null && !extras.has(LOCOMOTIVE)`: else nothing), :1147 (`!extras.has("home")`: else nothing), :1159 (`duplicateHomes++`); `LegacyImport.duplicateHomes` :528-534, read only by `testAutonomyDiagramSession` :7950; `AutonomyViewerPanel.importLegacyGraph` :1265-1292 |
| **Needs execution** | no |

Round 2 named two kinds of skip in the message: a train the configuration has standing elsewhere (`infoLegacyAlreadyPlaced`) and a home it already has (`infoLegacyHomesKept`). Three others remain silent.

- **A home the file names twice.** It still goes to `duplicateHomes`, which no door reads. Its javadoc still says *"Counted so it can be said out loud"*, and the fix's own comment separates it from the kept homes *"so the message can say which"*, but the message never does.
- **A placement onto a square where the configuration has another train standing.** It is skipped by the `if` itself. The file's train is neither placed nor named, and it may stand nowhere in the configuration afterwards.
- **A home onto a square that homes another train.** It is skipped the same way, and the file's train may be left with no home.

The dialog's counts stay true (none of these is counted as placed), so nothing is misreported, but the operator is not told which of the file's trains and homes did not come in. These are the siblings of RLA2-C3's fix.

**Direction:** name them beside the two lines round 2 added: the trains whose square was taken, and the homes dropped (the file's second home, or a square already homing another train).

---

### RLA3-C6 - Records the round's fixes left saying the opposite: RLA-C2's and RLU-C4's round-1 dispositions, and six comments that still say an answered 0 is measured "to every length rule"

| | |
|---|---|
| **Disposition** | Fixed - the six comments and open-questions.md name the own-tail exception (81a9fffa); RLA-C2's and RLU-C4's rows say what superseded them. |
| **Grade** | C |
| **Names** | RLA-C2, RLU-C4, RLA2-C8 |
| **Where** | `docs/reviews-2026-09-25/RLA.md` :80 and the store's RLA-C2 row; `RLU.md` :233 and its row. The comments are `Layout.java` :367, `AutonomySession.java` :3681, :3734 and :8712, `GraphReducer.java` :290, `AutonomyEditorPanel.java` :7078, and open-questions.md :135. |
| **Needs execution** | no |

**The round-1 dispositions.**

- **RLA-C2** still reads *"Fixed - into the configuration running, by its name, the running layout is captured first and the reload after the import does not capture again"*. This is in RLA.md and in its triage.db status note. `cc56a7a8` removed that capture and made the door refuse the name instead, and neither record points to RLA2-B1.
- **RLU-C4** still reads *"... not one where trains turn round"*, the clause RLA2-C7 showed false and `cc56a7a8` took out of all eight languages.

The round-2 rows that superseded both exist. But a reader querying RLA-C2 or RLU-C4 by id is told about a fix that is not in the code. RLA-C4 had the same need, and RLA2-C5's disposition recorded it.

**The comments.** RLA2-C8 asked for the own-tail exception in two places, and both now carry it. Six comments in `src/` still describe an answered 0 as read *"by every length rule"*, and open-questions.md's "Decided" paragraph states the rule with no mention of OB-300. The sites are:

- `lengthOf`'s javadoc (*"as every length rule reads it"*);
- the three assign-length comments in `AutonomySession`;
- `GraphReducer.answeredAtZero`'s interface javadoc;
- the editor's Segment Length javadoc.

None of these is on the own-tail path, so none is wrong about the method it sits on. But each repeats the claim the fix corrected.

**Direction:** a dated line under the two round-1 dispositions naming what replaced them, and "every length rule but the own-tail rule (OB-300)" in the six comments, or a pointer to `Edge.isMeasured`.

---

### RLA3-D1 - RLA2-B1: Import refuses the configuration in use at its one door, for both kinds of file, before anything is read; case and sanitised twins are refused as names in use; the claim bites

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA2-B1, RLD2-C2, RLD2-C3, RLA2-C4 (placements) |
| **Where** | `AutonomyViewerPanel.importConfiguration` :1018-1026; `AutonomyCompanionStore.createConfiguration` :2528-2530, `importConfiguration` :2568, `fileNameTaken` :5349; `testTheImportDoorReadsAnOldFile.testAnImportIntoTheConfigurationInUseIsRefused` |

Every import reaches `importConfiguration`. Both Autonomy menu items (:286 and :341) and the viewer panel's Manage menu call it. `importLegacy` and `importBundle` have no other production caller.

The refusal compares the trimmed name with `getActiveDiagramConfiguration()` before the file is opened, so it covers a bundle and an old file alike. It names the menu path in every language (`errorImportIntoConfigurationInUse`, `{0}` and `{1}` in all eight).

A name that differs only in case or in a character a file name cannot hold does not match, but reaches `createConfiguration` (old file) or the store's `importConfiguration` (bundle). Both throw `ERROR_NAME_IN_USE` through `fileNameTaken`, and `File.equals` ignores case on Windows. So the running configuration cannot be reached by a twin either.

With nothing running (the repair case the AutonomyMenu comment keeps Import live for), nothing is refused, and the reload does not capture. The capture into the running configuration is gone from `importLegacyGraph`, and `loadAfterImport` always reloads with the capture into the configuration running, which is now never the one imported into. So RLD2-C2's placements onto the running railway and RLD2-C3's unpinned capture are both closed by the same line.

The claim compares the configuration's JSON, the configuration names and setup.json on disk before and after, and it asserts the message. With the refusal removed the import writes into the configuration and it goes red.

`testASetupMovesToAOnePageLayout`, the only other test to drive this door, imports with nothing set up, so the refusal does not touch it.

For Adam, not a defect: the door's default path now meets the refusal for a restored backup. Export suggests `<configuration>.json`, and Import suggests the file's name. After the message he starts again from the file chooser and types another name.

---

### RLA3-D2 - RLA2-B2's address half, and RLD2-C6's Central Station pin: the sync asks the window doors' question of each re-addressed locomotive under the railway's lock, and BPV-A1 stays fixed

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA2-B2, RLD2-C6, RLA2-C9 (the `MarklinLocomotive` comments) |
| **Where** | `MarklinControlStation.syncWithCS2` :1454, :1638, :1704-1722; `Layout.sanitizeMultiUnits` :9267; `testMockCentralStation` :434; `testALocomotiveDoesNotEvictItself` :352 |

The locomotive is added to the list only on the branch that actually re-addresses it, after `setAddress` and the database re-add, so the object swept is the one the database holds. The sweep runs after `rebuildLocIdCache`, only with an autonomy layout present and not running, and inside `synchronized (layout)`.

Taking the lock is safe in both directions:

- The sync runs off the event thread, since the wrapper hands an event-thread call to `BusyDialog.run`'s worker.
- The only thing the sweep hands the event thread while holding the lock is a log line, which goes through `invokeLater`, so it never waits on the event thread.

`sanitizeMultiUnits(X)` sweeps from X where it stands and from each standing head that links X or holds it as a Central Station member. With X standing on Z's new address, Z goes and X stays, as at the window. A member re-addressed onto nothing that stands sweeps from its head, which is skipped as itself, so BPV-A1 holds. The membership list is compared by `Map` equality on names, so order does not matter, and a non-multi-unit compares null with null.

The sync claim stands the head and another train, lets the served file re-address the member, and asserts both preconditions (the address changed; the head still stands) before the eviction. With the sweep removed it is red. The Central Station pin holds its head by the Central Station list only, asserted as a precondition, and is red with the second disjunct dropped.

The two `MarklinLocomotive` comments now name `clearMultiUnitConflictsWith`, which is the only caller.

What is left is RLA3-C1 to RLA3-C3.

---

### RLA3-D3 - RLA2-B3 where the file can say: the file's facing wins over the square's, and the claim is red with the old rule

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA2-B3 |
| **Where** | `AutonomySession.importLegacy` :1141; `findTheImportedFacings` :724-760; `testASecondImportFacesItsTrainTheWayTheFileRanIt` |

Every placement the import makes is now handed to `findTheImportedFacings`, and a facing the file states and a copy holds is set over whatever the square records. A stated facing no copy holds is guessed and named in `facingsNotHeld`, as before.

A first import is unchanged: `createConfiguration(name, null)` starts empty, and facings are per configuration (`setPointProperty`). A train the configuration already has on the same square is passed over before the placement branch, so its own facing is not asked about again.

The claim edits the live configuration object (`getConfiguration` returns the store's own `JSONObject`, not a copy). It clears the train and turns the square's facing to another side a copy holds, then imports again. With the old `getFacing(tile) == null` test put back, the square is never asked and the pre-set side stays, so the claim goes red.

The square the claim finds is taken in the reducer's key order, and it does not assert that the file states that square's facing. If that square ever became one the file cannot speak for, the claim would go red and say "last occupant" when the true reason is RLA3-C4's branch. That is a red for a wrong reason, not a green one.

---

### RLA3-D4 - RLA2-C3: a home the configuration has is kept and named in all eight languages, and the claim holds both halves

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA2-C3, RLD2-C6 (homes) |
| **Where** | `AutonomySession.importLegacy` :961-990 (seed), :1147-1160; `AutonomyViewerPanel` :1289-1292; `autosetup.ui.infoLegacyHomesKept`; `testASecondImportKeepsAHomeTheConfigurationHas` |

The seed now fills `homedInConfiguration` from the configuration imported into. `homedAlready` keeps only the file's own, so a home the file gives twice still goes to `duplicateHomes`, and a home the configuration has goes to `homesKept`. A home on the same square as the configuration's is passed over earlier (`extras.has("home")`), so nothing identical is named.

The claim gives the Tunnel train a home at BottomMainB and asserts the first import put it there, which is a precondition. It moves the home to BottomMainC in the configuration, imports again, and asserts one home at BottomMainC and the named line. If the seed is started empty, the train gets a second home. If the line is dropped, the message assertion fails. Either way it goes red.

`d779578b` changed only where the train's name comes from (read from the file, for the citation census), after the fix's run. The logic is untouched, and the next battery is its first run in that form.

The new key has `{0}` in all eight bundles. The silent siblings are RLA3-C5.

---

### RLA3-D5 - RLA2-C6 and RLA2-C7 (with RLU2-C10): one predicate for the start and for "turn it round", a pin that bites, and the either-way sentence the same in eight languages

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA2-C6, RLA2-C7, RLU2-C10, RLD2-C10 |
| **Where** | `Layout.whyItReachesNoStation` :5052-5067, `whyTheStartIsRefused` :5076, `whyNoTrainIsStartedFrom` :5093; `testWhyStuck.testTurningRoundIsNotOfferedOntoACopyThatIsNoStation` :761; `autolayout.why.startReachesNoStationEitherWay` in the eight bundles; `Automation.md` :311; behaviour.md :705 |

The extraction is exact: barred, then not a station, then switched off, the same three tests in the same order. `whyTheStartIsRefused` now asks the same method for the train's own copy, so a rule added there reaches "turn it round".

The new pin builds the other copy as no station, asserts that it is the barred copy, and expects the either-way sentence. Offering turning round onto any copy that reaches a station makes it red. Dropping only the barred clause leaves `!isDestination()` refusing the same copy, since a barred copy is no station, so the result "turn it round" does not change.

Read in memory at HEAD, the eight bundles hold 1,838 keys each: the same set, no duplicates, every key's placeholders equal to English's, no byte above 127, no straight apostrophe in a value with a placeholder, and the only empty values Italian's and Polish's plural suffix. I decoded the either-way sentence in all eight: `{0}`, `{1}` and the new `{2}`, with the "trains turn round" clause gone from every language. `Layout` passes `menuArrivalsGroup` as `{2}`, and the three `testWhyStuck` expectations pass it too. `Automation.md` and behaviour.md say the same, and the user guide puts "a train may start from" on the platform's other way (RLD2-C10).

---

### RLA3-D6 - The round's other records in this lane are true: RLA2-C1, RLA2-C5, RLA2-C8 (the two places it named), RLA2-C9, and RLA2-C2's follow-up

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA2-C1, RLA2-C2, RLA2-C5, RLA2-C8, RLA2-C9, RLD2-C1 |
| **Where** | behaviour.md :1953-1960, :1022-1023, :1143-1145; `Layout.java` :9244-9246; `Edge.java` :496-497; `AutonomySession.java` :639-641; `test/ui/testThePlaceDoorsKeepTheHeading.java` :501-503; `testTheImportDoorReadsAnOldFile.java` :208, :969; `issues.md` OB-301 |

Each of these records now says what the code does:

- **behaviour.md's BPV-A1 paragraph and `sanitizeMultiUnits`' summary line** now state the rule the code has: the edited train where it stands, and every standing multi-unit that drives it. The sync is named, with its not-while-running condition.
- **behaviour.md's answered-0 sentence and `Edge.isMeasured`** name the own-tail rule and OB-300. The siblings are RLA3-C6.
- **The MT-575 paragraph and `TailCrossedPrompt`'s comment** say a minimised editor's list hangs from the main window.
- **The RLA-C4 claim's javadoc** says no door reaches it today and that it pins the owner rule.
- **`importLegacy`'s javadoc** no longer claims the save is the next line.
- **The two test javadocs** describe the fill-gaps question.

OB-301 carries RLA2-C2 with its direction and a claim to write. The deferral is sound: the door changes the address before it checks the name, but reaching it takes a second mistake in the same dialog, and the next Place or load sweeps.

---

### RLA3-D7 - The length rules, the tail and berth rules and the Return Home planner are untouched this round, so RLA-D1 still holds

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | RLA-D1 |
| **Where** | `git diff --stat 98b300d6 HEAD -- src/` |

The round changed 18 source files:

- In `Layout`: 24 lines, the extraction above and one javadoc.
- `Edge`: one javadoc.
- `MarklinControlStation`: the sync.
- `MarklinLocomotive`: two comments.
- `AutonomySession` and `AutonomyViewerPanel`: the import.
- Four window files outside this lane.
- The eight bundles.

Nothing in `walkOneTail`, `measuredRouteIn`, `measuredRoomAtTheEndOf`, `whyABerthCannotHoldIt`, `whyItWouldMeetItsOwnTail`, `HomeStaging`, `GraphReducer` or `AutonomyBuilder` changed. So the runtime and the Return Home planner still ask the same methods about an answered 0, with the own-tail rule as the one exception (OB-300).
