# The autonomy setup and its editor, reviewed for the 2.7.x to 3.0.0 delta

**Status:** open 2026-09-19 - B1, B2 and B3 fixed the same day (MT-458, MT-459, MT-460); C1, C2 and C3 fixed; nothing open

**Prefix:** SET (checked free: no `SET-` ref in the finding catalogue of `docs/manual-tests/triage.db`, no `SET-[A-D]n` spelling anywhere in `docs/reviews/`, `src/` or `test/`, and no other document declares it)

**Covers** `src/org/traincontrol/automationui/**` and the three editor classes in `gui/` - `AutonomyEditorPanel`, `AutonomyViewerPanel`, `LayoutRightclickAutonomyMenu` - at `5b8dc021` (2026-09-17, branch `autonomy-diagram-r0`), over the range `v2_7_4c..HEAD`, in which the whole package is new.  One of four reviewers Adam asked for on the 2.7.x-to-3.0.0 delta.  **Read-only: nothing was run, built or edited**, so every proof below is a test to write or steps to take, not a result.  Where a trace had to leave the scope to find the layer that enforces a rule (`Layout.fromJSON`, `Point`, `parseAuto`, `TrainControlUI`'s close and capture), it was read and is cited, not reviewed.

**What it leaves to the September reviews.**  AMS (setup and session), AMG (graph build) and MAL (Mass Assign Lengths, 16 Sep) already covered this package to `92cff9e8`.  Nothing they found is re-reported; their ids are cited where a claim here rests on or extends theirs.  The ground this pass took as its own is (a) the five commits since - the walk prompt's placement and focus (`64938e7a`, `e8e837c8`), the flash (`8095407a`), Mass Assign Max Train Lengths (`edd1af9e`) and Clear All Max Train Lengths (`9f2a2f6b`) - and (b) the interactions a per-method pass does not see: a bulk door and a single door writing one property, a key and a menu naming one target, a cache and its inputs, what Cancel puts back, and what the shared-square rule of MAL-C2 does to a square two legs run over.

**On the pending tests.**  B2 touches MT-454 and MT-455 (the prompt's square count and the outline are wrong on the squares it is about).  B1 and B3 would each want an MT if Adam accepts them; none is written here - the tracker is not this pass's to edit.

---

## A - wrong behaviour on the layout, or data silently lost

None.

Two findings below were weighed for A and left at B, and the reasons are recorded so the next reader can disagree.  **B2** admits a train one square's share longer than the track past a shared square really holds - the direction the room rule exists to refuse - but the error is bounded by one square, and the prompt's square count and the outline both show the hole to a careful reader.  **B1** takes the railway out of service, but with a log line that names the square and the rule, and a menu label that shows the bad number; Adam's standing rule is that an error with a remedy is not a loss.

---

## B - incorrect results, or a refusal, in specific configurations

| id | status | where |
|---|---|---|
| SET-B1 | fixed - MT-458 | `AutonomyEditorPanel.promptNumber` - a negative maximum is written, the configuration then refuses to load, and no setup check or bulk clear can see it |
| SET-B2 | fixed to Adam's ruling - MT-459 | `AutonomySession.stretchesALengthRuleReads` - a crossing or double curve shared by two legs is in one piece; the other leg is over-measured by its share |
| SET-B3 | fixed - MT-460 | `AutonomyEditorPanel.applyLength` after Mass Assign Lengths - the single door shows and writes one square's share as if it were the run |

### SET-B1 - the Maximum Train Length door takes a negative number, and the setup stops loading

| | |
|---|---|
| **Where** | `src/org/traincontrol/gui/AutonomyEditorPanel.java` 4012-4050 (`promptNumber`); reached from the station menu at 1464-1470 and from Control+B through `promptMaxTrainLengthFor` at 5657-5675 (`LayoutEditor.java` 7342).  The layer that refuses is `src/org/traincontrol/automation/Layout.java` 10712-10732 |
| **Gesture** | Right-click a station, **Maximum Train Length (any)**, type `-3`, OK.  Or hover it and press Control+B.  The same door on the track diagram viewer's Autonomy Setup menu (`TrainControlUI.buildAutonomyTileMenu` hands the viewer the same `AutonomyEditorPanel`) |

`promptNumber` is the shared prompt for `priority` and `maxTrainLength`.  It parses any integer and writes `value == unset ? null : value` - so `-3` is written as `-3`.  Nothing between the door and the running layout clamps it: `setPointProperty` writes the JSON, the builder copies every extra verbatim (`AutonomyBuilder.java` 930-976, `json.put(key, extras.get(key))`), and `Layout.fromJSON` at 10714 requires `instanceof Integer && >= 0`, else `layout.invalidate("... maxTrainLength must be >= 0")`.  `fromJSON` returns the invalidated layout (11540) and `parseAuto` (`MarklinControlStation.java` ~1018-1035) installs it rather than throwing, so on the non-interactive rebuild the editor triggers the only notice is the log: `invalidate(message)` writes the line (1569-1574) and `autonomyLoadedFromDiagram` then writes *loaded configuration* without asking `isValid()` (`TrainControlUI.java` 4018-4069) - no dialog.  The railway is invalid from the next `setupChanged()` on, and stays so across restarts because the exit capture is skipped for an invalid layout (`TrainControlUI.java` 2497-2500, `isValid()` in `wouldCapture`) - which, for once, is the right outcome: a capture would have replaced `-3` with the running layout's `0` and hidden the fault.

**What makes it worse than a typo.**  Three readers disagree about the sign, and each is right on its own:

- `hasNoMaximumTrainLength` (`AutonomySession.java` 3138-3147) says `<= 0` is "no maximum" - so Mass Assign Max Train Lengths OFFERS the station, and its `assignMaxTrainLength` overwrites the `-3` with what is typed.  That is the way out, and it works.
- `tilesWithAMaxTrainLength` (6793-6797) counts `> 0` only - so **Clear All Max Train Lengths greys itself and its tooltip says there is nothing to clear**, on a railway whose one maximum is the thing stopping it loading.
- `AutonomyChecks` never looks at the value (its only mention of `maxTrainLength` is a javadoc at 297), so the findings list is clean while the configuration will not build.  `hasBlockingProblems()` is the graph's problems, not the load's.

The menu label does show it - `number(target, "maxTrainLength", 0)` returns `-3` and the label reads **Maximum Train Length (-3)** - which is the remedy, and why this is a B and not an A.

**Twins.**  `promptPercent` (4090-4115), the speed door beside it, range-checks `(0, 200]` and says so; `priority` legitimately takes negatives, which is why `promptNumber` cannot simply refuse them.  `Point.setMaxTrainLength` (`Point.java` 1004-1009) clamps a negative to 0 - so the model and its own reader disagree about the same value, one layer apart.  The bulk door `assignMaxTrainLength` refuses `< 1` (3125); the single door refuses nothing.

**Prove it.**  In `core.testMassAssignLengths`, on `openBerthBehindASwitch(key(5, 1))`:

```
session.setPointProperty(key(5, 1), "maxTrainLength", -3);      // what promptNumber writes for "-3"
assertTrue(session.tilesWithAMaxTrainLength().isEmpty(),         // green today - the clear cannot see it
    "Clear All counts a maximum the load will refuse");
Layout built = Layout.fromJSON(session.buildConfiguration(), control);
assertTrue(built.isValid(), "a maximum typed through the editor stopped the configuration loading: "
    + Layout.lastError);                                         // RED today
```

The first assert is the precondition that makes the second mean something (a fix that clamps in `fromJSON` alone would leave the clear blind).  The fix is a decision for Adam: refuse `< 0` at the max door (the shape `promptPercent` has, the message `errorMaxTrainLengthZero`'s neighbour), or clamp in `fromJSON` as `setMaxTrainLength` does; either way `tilesWithAMaxTrainLength` and `hasNoMaximumTrainLength` should then agree that nothing `< 0` exists.  Manual: MT-456's railway, step 7 with `-3`, then Start.

### SET-B2 - a square two legs run over is in one piece, and the other leg is over-measured by its share

| | |
|---|---|
| **Where** | `src/org/traincontrol/automationui/AutonomySession.java` 2835-2886 (`stretchesALengthRuleReads`, the `placed.add` at ~2860 that gives a square to the first leg reaching it); read back by `GraphReducer.sumLength` (`GraphReducer.java` 1320-1330), which adds every tile of every leg |
| **Gesture** | Bulk Tools, **Mass Assign Lengths...**, on a page with a crossing or a double curve whose both roads carry track.  **Adam's 1 - Main has four**: the crossing at 18,10 (north a straight, south and east a left switch, west an s88 - both roads joined) and double curves at 20,10, 21,10 and 11,11, each with track on all four sides (read from `test/layouts/live-snapshot/config/gleisbilder/1 - Main.cs2`) |

MAL-C2's rule - *each square is in exactly one piece, the first that reaches it* - was written for a sensor square that ENDS several legs, where it is right: the second answer must not overwrite the first.  A crossing or a double curve is different.  `TilePorts` gives `CROSSING` routes N-S and E-W and `DOUBLE_CURVE` two unconnected curves (271, 250); neither `isSwitch()` (AMG-B2/C3: the room walk counts through them).  So the square is an intermediate tile of TWO legs, and both legs' `getLength()` include it - correctly, since a rail of each leg physically crosses it.

The walk gives it to whichever leg `legsOnce()`'s `TreeMap` sorts first.  For the other leg:

- the piece has a hole - `piece.getTiles()` skips the square - so `outlineAndReveal` outlines a run with a gap in it and the prompt says *"squares: k"* where the run is k+1 squares long;
- the user is asked for *"the whole length of this stretch, between A and B"* and measures A to B, crossing included;
- `assignStretchLength` shares that total over the k squares, and the reducer then adds the shared square's share (from the first leg's answer) on top: **the second leg measures typed + share**.

Every reader of a leg's length is admitting under that error: `roomAfterTheLastSwitch` where the shared square lies past the last switch on an approach (18,10 is beside two switches), `Layout.measuredRouteIn`'s FR-087 allowance, and the tail and berth walks, which spend a longer square as fewer squares covered.  One square's share - on Adam's railway a unit or two - but in the direction every one of his length rulings has refused.

**Why a ruling rather than a fix.**  It is the switch question of MAL-B1 again: a square whose length two legs both read cannot take one leg's share without being wrong for the other.  The answers on the table are (1) treat it as a switch is treated - in no piece, asked for on its own (*"one length for all crossings"* or per square), so both legs' pieces are cut at it and both totals are exact; or (2) leave it in the first piece and have the second piece's prompt say the square is already measured and its length is added.  (1) is the ruling's own shape.  Whichever he chooses, `squaresNeedingALength` and the walk must ask the same question of it, which they do today only by accident of it being in a piece.

**Prove it.**  A fixture of two straight legs crossing, in `core.testMassAssignLengths`: sensor 1,2 - straight 2,2 - `CROSSING` 3,2 - straight 4,2 - sensor 5,2, and sensor 3,0 - `straightNS` 3,1 - the same crossing - `straightNS` 3,3 - sensor 3,4 (the crossing helper from `testAutonomyDiagramReducer.testTheRoomWalkStopsOnlyAtASwitchThatCanBeThrown`, 770).  Then:

```
List<Stretch> pieces = session.stretchesNeedingALength();
// precondition: two pieces, the crossing in exactly one of them, the other 4 squares long
for (Stretch p : pieces) assertTrue(session.assignStretchLength(p, 10));   // "the whole stretch is 10"
for (ReducedEdge leg : session.getReducer().getEdges())
    assertEquals(leg.getLength(), 10, "the leg " + leg + " measures more than was typed for it");   // RED: one leg is 12
```

The control is a plain straight leg with no shared square, which measures exactly 10 today.  Manual: MT-454 on 1 - Main, watching the prompt that outlines the run through 18,10 - the outline has a hole and the count is one short.

### SET-B3 - after Mass Assign Lengths, Segment Length shows and writes one square's share as if it were the run

| | |
|---|---|
| **Where** | `src/org/traincontrol/gui/AutonomyEditorPanel.java` 6594-6660 (`applyLength`: prefill from `getTileLength(sample)`, write to `leaderOf(tile)` only), 8617-8622 (`leaderOf`), 1205 and 1886-1889 (the menu binds every item to `leaderOf(tile)`), 5560-5603 (Control+E, the same target); against `AutonomySession.assignStretchLength` 3027-3058, which writes every square of the piece |
| **Gesture** | Mass Assign Lengths on a page; then right-click any square in a run of two or more plain squares it measured and choose **Segment Length...** (or Control+E over it) |

`behaviour.md` 1255-1260 states the model the single door is built on: *"a run of plain track has one square that speaks for it, and both doors write there, so measuring a run through both does not count it twice."*  Both doors meant the menu and Control+E.  Mass Assign Lengths is a third door and it does not write there: `assignStretchLength` shares the whole over EVERY square of the piece (MAL-B2 chose the split deliberately), so after a walk the followers of a run carry lengths of their own.

What the single door then does:

- the dialog opens prefilled with `getTileLength(leader)` - one square's share, typically 1 or 2 - on a run the user measured as 7.  With Show Lengths on, every square shows its own share, which at least says the numbers are per square; with it off, nothing does;
- OK writes the typed number to the leader ONLY (`session.setTileLength(target, length)` over `targets`, which is the leader when nothing is selected).  The followers keep their shares, so **the run measures typed + the followers' shares** - the reducer sums every tile;
- clearing the field and pressing OK (OB-043: *"treat it as 0"*) zeroes the leader and leaves the run measured;
- right-clicking a follower opens the dialog on the LEADER's number; the follower's own share is shown on the grid and reachable through no dialog - only a shift-click selection or Clear All Track Lengths can touch it.

This is a document-versus-code disagreement and I believe the code is the wrong half: the run model is what makes Control+E and the menu safe, and it was true until `92cff9e8`.  The alternative - amend the document to say a run has no single spokesman once Mass Assign has been through - leaves the single door quietly adding to a number the user thinks they are replacing.  The fix Adam may prefer: on a run, prefill with the sum over the run's tiles and write the typed total to the leader with the followers zeroed - the same pieces-and-shares picture the walk paints, read back the same way.  The station and sensor squares at a piece's ends are not run tiles (`isRunTile` refuses feedback) and would keep MAL-B2's unit.

**Prove it.**  In `core.testMassAssignLengths`, a fixture with a run of two plain squares: sensor 1,1 - straight 2,1 - straight 3,1 - sensor 4,1 (a station).  Mass-assign the one piece 7 (4,1 first as the standing square: 2; then 1,1: 2, 2,1: 2, 3,1: 1, by 3040-3052).  Then the single door's target and write, using the public seam the Control+E tests use:

```
TileKey leader = panel.squareTheLengthWouldGoOn(key(3, 1));       // the run's leader, 2,1 or 3,1
session.setTileLength(leader, 4);                                  // what applyLength writes for "4"
int run = store.getTileLength(key(2, 1)) + store.getTileLength(key(3, 1));
assertEquals(run, 4, "the run was given 4 through Segment Length and measures " + run);   // RED: 5
```

Control: the same write on a run nothing has mass-assigned measures exactly 4 today.  `regression.testControlEAsksTheMenusQuestion` pins that the two single doors agree with each other; nothing pins either against the walk.

---

## C - low: cosmetic, narrow, or documentation

| id | status | where |
|---|---|---|
| SET-C1 | fixed - behaviour.md 5b and the keyboard table | `docs/reference/behaviour.md` - Mass Assign Max Train Lengths, Clear All Max Train Lengths and Clear All Track Lengths are not in it; Control+B is missing from the keyboard table |
| SET-C2 | fixed - a demoted station keeps no maximum | `AutonomySession.setStation(tile, false)` leaves `maxTrainLength`; Clear All Max Train Lengths then counts a square that is not a station |
| SET-C3 | fixed - one redraw per clear | `AutonomyEditorPanel.clearAllMaxTrainLengths` refreshes three times for one gesture; its sibling once |

### SET-C1 - behaviour.md does not know the three bulk doors, and one key

Section 5b (832-863) describes Mass Assign Lengths and the Unmeasured Track display to MAL's ruling and stops there.  Nothing in the document mentions **Mass Assign Max Train Lengths** (FR-091), **Clear All Max Train Lengths** (FR-092) or **Clear All Track Lengths** (FR-069, 2026-09-08); 5a's station-capacity table says what the maximum means but not how it is set in bulk or cleared.  The keyboard table at 1242-1254 lists Control+S, Control+E and Control+H; **Control+B** appears only in the OB-197 paragraph beneath it as a key that used to select a locomotive.  The document is the authority on intent, and these four are intent Adam stated in his own words (FR-091, FR-092, `ed9414d6`).  Code is right; document lags.

### SET-C2 - a demoted station keeps its maximum, and the bulk clear counts it as a station

`setStation(tile, false)` (5381-5410) sweeps the caption, the barred arrivals, the protecting signal and, since AMS-B2, the occupancy restriction - not `maxTrainLength`.  `tilesWithAMaxTrainLength` reads every point in the JSON with a maximum above 0, station or not, so **Clear All Max Train Lengths (n)** and its confirmation *"on {0} stations"* count a square no menu offers a maximum on (the item is nested in `isStation`, 1461).  Inert on the railway as far as I traced it - `Layout.isPathClear` asks `validateTrainLength` of the path's ending only (`Layout.java` 9144), and a non-station is nobody's ending - and re-promotion brings the old limit back, which may even be wanted, as the never-cleared facing is.  Low: a count that is one too many, and a number the user cannot see except by clearing everything.  If Adam wants demotion to sweep it, the line goes beside AMS-B2's; if not, the count should read stations only.  **Prove:** `setStation(key(5,1), true); setPointProperty(key(5,1), "maxTrainLength", 8); setStation(key(5,1), false); assertTrue(session.tilesWithAMaxTrainLength().isEmpty())` - red either way until he rules which.

### SET-C3 - three refreshes for one clear

`clearAllMaxTrainLengths` (9555-9583) calls `refresh()` and then `setupChanged()`, and `item()` (2433-2462) calls `refresh()` again after the action - three full `session.check()` passes for one gesture.  `clearAllTileLengths` beside it (9517-9548) calls neither `refresh()` nor a second; `setupChanged()` and the `item()` refresh are enough.  Cosmetic - a pause on a large railway.

---

## D - not defects: looked wrong and is not, and checks that came back clean

| id | what |
|---|---|
| SET-D1 | **Cancel puts lengths and maxima back.**  `LayoutEditor.takeTheUndoPoint` (535-542) snapshots `store.snapshotSetup()`, whose shared half is the `kept()` registry - `tileLengths` and `stations` included (`AutonomyCompanionStore.java` 4895-4901) - plus every configuration's points, so `maxTrainLength` is in it.  `discardAutonomyWork` (615-624) restores that before the window closes, and `TrainControlUI.autonomyEditorClosed` (6530-6543) REBUILDS the running layout from the restored setup and only then captures it - so the close capture, which does carry `maxTrainLength` (a `POINT_OPERATIONAL_KEY`), writes the restored value, not the edited one.  `regression.testCancelUndoesAutonomyEdits.testDiscardOnTheWayOutPutsTheHomeBack` (WKW-C1) is the same mechanism for `home`; a twin claim for a maximum set through the walk would cost ten lines and pin the order |
| SET-D2 | **The bulk and single doors write the maximum the same way.**  `promptNumber` writes null for 0, `assignMaxTrainLength` refuses 0 and writes `>= 1`, `clearEveryMaxTrainLength` writes null; the capture writes an explicit `0` on every destination (`Point.toJSON` 1089).  Every reader - `hasNoMaximumTrainLength`, `tilesWithAMaxTrainLength`, `number()`, `Layout.fromJSON`'s `else setMaxTrainLength(0)` - treats absent and 0 alike.  The one value they disagree about is a negative, which is B1 |
| SET-D3 | **Affordance and guard ask one question on all four new bulk items.**  Mass Assign Lengths greys on `stretchesNeedingALengthOn(page).size() + switchesNeedingALengthOn(page).size()` and the walk asks both again (9220-9221); Mass Assign Max on `stationsWithoutAMaximumOn(page)` both sides (2189, 9309); Clear All Track Lengths on `tilesWithALength()` (2245, 9519); Clear All Max on `tilesWithAMaxTrainLength()` (2264, 9557).  Control+B and the menu: `offersAMaximumTrainLength` is `offersALength` + a reducer Point + `isStation`, on `leaderOf(tile)` - the three nestings `buildTileMenu` reaches the item by (1205, 1228, 1263).  `regression.testControlBAsksTheMenusQuestion` and `testControlEAsksTheMenusQuestion` hold both pairs over every square of the live snapshot |
| SET-D4 | **The Unmeasured Track cache is forgotten wherever its inputs change.**  `unmeasuredSquares` depends on lengths, the legs (so the excluded pages) and switch types.  Every door that moves one of those reaches `refresh()` or `setupChanged()`, both of which null it: `applyLength` and Control+E (MAL-B3), both walks, both clears, `setPageExcluded` (refresh AND setupChanged), and the toggle itself (780-784).  The track editor changes shape only outside autonomy mode, and `arriveAt` rebuilds the panel |
| SET-D5 | **Clear All Max Train Lengths clears every page, excluded ones included** - as Clear All Track Lengths and Clear All Home Locomotives beside it do, and as the confirmation says.  Deliberate |
| SET-D6 | **Mass Assign Max is not behind `modelsAnyLength()` while the `NO_MAX_TRAIN_LENGTH` notice is.**  The javadoc at 3083-3092 says why: opening the walk is saying lengths are being modelled.  Not a disagreement |
| SET-D7 | **One full builder per station in the maximum walk.**  `assignMaxTrainLength` goes through `setPointProperty`, which re-derives the station index - the cost `clearEveryHome` and `clearEveryMaxTrainLength` split out.  Here it sits between two modal prompts, so it is the single door's cost, not a bulk gesture's |
| SET-D8 | **`legsOnce()` keys a leg by its tile SET.**  The two directions of a leg collapse to one, which is the intent; a bridge crossed twice by one leg is one leg (MAL-D2).  Two DISTINCT legs sharing a square have different sets and both survive - which is what makes B2 reachable rather than hidden |
| SET-D9 | **A maximum set in the editor survives the close.**  `setupChanged()` rebuilds the running layout before any capture, so the exit capture reads the new value from the running Points; the explicit `maxTrainLength: 0` the capture writes on destinations does not fight the clear, because `tilesWithAMaxTrainLength` counts `> 0` only.  A declined rebuild during a run is AMS-B1's flag and skips the capture |
| SET-D10 | **FR-090's remembered position reads a disposed dialog.**  `walkPromptAt = dialog.getLocation()` runs after `setVisible` returns, which for the Enter path is after `dispose()`; `Component.getLocation()` returns the x, y fields, which dispose does not reset.  Fine |
| SET-D11 | **Escape in the walk prompts** - `dialogAnswer` (MAL-B4) is asked by both walks through the one `askForWholeLength`; the FR-091 walk inherited the fix, not the defect |

---

## What this pass did not cover

- **Nothing was run.**  Every "RED" above is a prediction from reading the two layers involved; the SOP's own warning about simulating one's model of the code applies to this document.  The three tests are written so the first thing they assert is the precondition that makes the claim mean something.
- `AutonomyViewerPanel` beyond `load` and its capture gate; `LayoutRightclickAutonomyMenu` beyond `addSetupMenu` - its placement, facing and destination items are the runtime's business (AMR) and AMS's capture rules.
- `DiagramMonitor`, `TileOverlay`'s paint path, `StationIndex`, `LayoutPageEdit`, `TilePorts`' geometry and `GraphReducer`'s room walk - AMG's ground, and AMG-B2 is ruled.
- The eight message bundles for FR-091 and FR-092 - MAL-C7's kind of check was not repeated.
- The tests' mutation claims in `core.testMassAssignLengths` were read, not re-run.
- Whether the shared-square count of B2 on Adam's other pages matters: 2 - Bottom has four double curves, 3 - Top Parking a double slip and three curves; all three of those pages are excluded in the live snapshot's setup, so only 1 - Main was traced.

## Seen outside my scope

- `Layout.fromJSON` (`automation/Layout.java` 10712-10732) invalidates the whole layout on a negative `maxTrainLength` where `Point.setMaxTrainLength` (`Point.java` 1004-1009) clamps one to 0 - the two layers disagree about the same value, and B1 is what the disagreement costs from the editor's side.  The runtime reviewer may want the model half.
- `Layout.isPathClear` asks `validateTrainLength` of the ending Point only (9144); the trace for C2 rests on that and was read, not reviewed.
