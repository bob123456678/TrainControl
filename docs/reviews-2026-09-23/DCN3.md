# Documentation validation of round 2 (DCN3)

**Status:** open

**Prefix:** `DCN3`

**Reviewed:** branch `autonomy-diagram-r0` at `2f4448b6`, 2026-09-23.  Baseline: round 2's validation at `08a47bdd`, and every commit in `08a47bdd..2f4448b6` - `264f2a73`, `8346be65`, `8370abb1`, `4132d260`, `b53439dc`, `9c85db2a`, `1facc0c2`, `91daff27`, `2f4448b6`.

**Method:** Reading, `git show` / `git blame` / `git log -S` / `git diff`, `grep`, and read-only Python piped through stdin (nothing written to disk): a scan of every line added in the range for words joined across a line break; a character-level check of the 506 values `91daff27` changed against their old values, with the context-dependent accents read in their sentences; a word diff of the three lanes' D sections before and after `2f4448b6`; a recount of `tests.md`'s dispositions and ledger rows; and `sqlite3` on `triage.db` opened `mode=ro&immutable=1`.  Every round-2 disposition that says Fixed was walked against its commit and the finding's Where list.  For the placement question the task named, every comment, javadoc, test message, behaviour.md paragraph and user-guide sentence about which copy a train is stood on was found by `grep` (`copyFacing`, `placementCopy`, `placeableFacings`, `copy 0`, `first copy`, `put down on`, `may stand on`, `impossible facing`, `not a station`) and read against `placementCopy`, `startableCopy`, `copyFacing`, `moveOntoFacingCopy`, `placeableFacingsFor`, `facingAfterAPaste` and their five callers at HEAD.  `findings.tsv` was grepped for the paste prompt's choices and the facing rules before writing B1.  **Nothing was built or run, and nothing was written but this report.**

## A - high

None.

## B - medium

### DCN3-B1 - Round 2 left two placement rules that disagree about a facing only a barred copy holds, and the one decision that would settle it is catalogued Closed

| | |
|---|---|
| **Disposition** | Fixed (the record) - 9a9a8564 and 031a7ddb: OB-284 filed, behaviour.md points at it, AUT2-A1 reopened as Adam's decision.  The decision itself is Adam's |
| **Where** | `AutonomyBuilder.java:742-776` (`placementCopy`), `AutonomySession.java:1543-1599` (`copyFacing`), `:1630-1635` (`moveOntoFacingCopy`) - against `AutonomySession.java:1507-1540` (`placeableFacingsFor`), `:7029-7045` (`facingAfterAPaste`), `TrainControlUI.java:8176-8183`, `:8214-8221`, `GraphLocAssign.java:233-241`, `:284-287`, `AutonomyEditorPanel.java:5442-5445`; `docs/reference/behaviour.md:632-639`; `docs/reviews-2026-09-23/AUT2.md:17`; `triage.db` row `AUT2-A1` |

This restates the doors half of AUT2-A1, which the coordinator kept deliberately.  What is new: the fix made the two halves of the code state opposite premises, behaviour.md states the one the fix abandoned, and the question the disposition says was "recorded for Adam" is recorded nowhere he works from.

**The two rules at HEAD.**  `8370abb1` wrote, in `placementCopy` (`AutonomyBuilder.java:759-765`):

> *"The copy IS the direction ... a train stood on a copy facing the other way is dispatched along a route locked one way while its decoder drives it the other ... **A barred arrival bars STOPPING there, not standing there**"*

and `copyFacing`'s javadoc and the test class javadoc say the same.  So the build, the throttle's direction-follow and the Facing menu now stand a train on a copy trains may not arrive at, facing its recorded way, and autonomy refuses to start it ("It is standing on {0}, which is not a station").

The placement doors were left on the opposite premise.  `placeableFacingsFor` (`AutonomySession.java:1508-1513`): *"a heading only such a copy holds is one **no placement may give** (Adam ... 'we shouldn't allow an impossible facing to be saved')"*; `TrainControlUI.java:8217-8220`, `AutonomyEditorPanel.java:5442-5443` and `GraphLocAssign.java:284` repeat it.  Fed only placeable copies, `facingAfterAPaste`'s one-copy branch (`:7045`, still reasoning *"everywhere else it is the only heading a train can have on that square"*, and still documenting `held` at `:7029` as *"every copy the landing square builds to"* although all five callers pass `placeableFacingsFor`) returns the one placeable facing whatever the train's heading.

The ruling both sides cite does not settle it.  Adam's full sentence (behaviour.md §6, `HomeStaging.java:2542`, filed under OB-282, homes) is *"it's also reasonable to expect that the input facings are ones realistic on the layout.  we shouldn't allow an impossible facing to be saved."*  Round 2's premise is that a train standing on a barred copy is realistic - it gets there by the throttle, or by being there when the side was barred - and under that premise the doors refuse a realistic facing.  (The test class javadoc, `:37`, and `TrainControlUI.java:8179` cite the quote under OB-270; AUT2-A1 read it as said of homes.  Which it was is Adam's to say.)

**Concrete case, on `live-snapshot`** (BottomMainA, arrivals from the east barred, so the only copy facing west is no station - the precondition `testATrainIsPutOnlyWhereItCanStart` asserts):

- a train at BottomMainA facing east, reversed on the throttle: `flipFacing` -> `moveOntoFacingCopy` stands it on the westbound barred copy, facing west (the round's claim `testAReversalOnTheThrottleIsFollowed`);
- the same train, heading west, put on BottomMainA by the Place Locomotive dialog, the editor's Place, or a paste: `facingAfterAPaste({E}, W, ...)` answers E, the train is stood on the eastbound copy and the setup records east - turned round, which is what 8370abb1's own comment calls the defect, and what Adam's OB-270 ruling (*"no train should inadvertently change direction when pasted"*) forbids.

So one railway state gets two answers depending on the door, and the premise each door's comment gives contradicts the other's.  Before 8370abb1 every door agreed (never a barred copy); round 2 changed three of them and documented why the old premise was false, while the comments on the remaining three still rest on it.  This is the shape BRIEFING.md names: an affordance and a guard that no longer ask the same question.

**behaviour.md states the rule round 2 abandoned.**  §3's paste paragraph (`behaviour.md:637-639`, `a32fe7f1`): *"a copy no train may be placed on is still refused (`copyFacing`), and the heading is chosen and recorded over the copies a train may stand on, so no impossible facing is saved."*  `copyFacing` no longer refuses one (`AutonomySession.java:1582-1597`), and the rule 8370abb1 chose - a train may stand on a barred copy, and autonomy refuses to start it there - is written nowhere in behaviour.md, which is the document the project declares to be the intended functionality.  AUT2-A1's suggested fix was headed "**Adam decides the rule**" and offered two options (stand it there, or leave it unplaced with a notice); the coordinator chose one, which is reasonable, but the choice is recorded only in code comments.

**"Recorded for Adam" is recorded nowhere he looks.**  AUT2-A1's disposition: *"The doors' one-placeable-copy rule ... is kept - it is OB-270's 'no impossible facing is saved' - and recorded for Adam"*.  The store has `AUT2-A1` at status **Closed** (read-only query), so it is not in the "Open - Adam's decision" set that the store, `open-questions.md` and the round's commit message (*"Open: AUT2-C2, GUI2-C2, GUI2-C4 for the next round; REG2-C3 and REG2-C7 for Adam"*) list; `grep -n "AUT2-A1\|one-placeable\|facingAfterAPaste"` over `open-questions.md`, `issues.md` and `behaviour.md` finds nothing.  When this folder is deleted, as the round's rule requires, the question survives only as the tail of a Closed row's disposition text.

**Reach.**  None of the four trains on the frozen railway is in this state today (AUT2-A1's own census).  It needs a barred side on a square where a train stands facing that way, or is put down heading that way - which the blessed baseline shows Adam's railway has had (EN57-203 at TopMainR1Inter, 2-8-4 at TopMainR1).  Rests on reading plus the round's own claims; the doors half is deterministic.

**Verification request.**  (1) *No execution:* `select status from finding where ref='AUT2-A1'` on `triage.db` opened `mode=ro&immutable=1` - `Closed` confirms the record half.  (2) *Execution, one JVM, deterministic:* in `core.testATrainIsPutOnlyWhereItCanStart`'s fixture (live-snapshot), after `build(session)`, assert `AutonomySession.facingAfterAPaste(session.placeableFacingsFor(mainA, running), Side.W, null)`.  **Proves it:** `E` (the doors turn a west-heading train east), while `testAReversalOnTheThrottleIsFollowed` in the same class stands a west-facing train on the westbound copy.  **Refutes it:** `W` or null.

**Suggested fix.**  Adam's call, stated as one: *"At a square where the only copy facing a train's way is one trains may not arrive at, should putting the train down (paste, Place, Place Locomotive) stand it there facing its way - autonomy will then refuse to start it, as the build, the Facing menu and the throttle now do - or turn it to face the way it can be started, as those three doors do today?"*  Record it where the round's other decisions are (store status `Open - Adam's decision`, and `open-questions.md`), correct behaviour.md §3 to the rule that stands, and make the comments on the losing side say so.

## C - low

### DCN3-C1 - GUI-B1's round-1 wording survives beside the round-2 rewrite: four places still say a train is never put on a copy trains may not arrive at, one of them in the rewritten class's own javadoc

| | |
|---|---|
| **Disposition** | Fixed - 031a7ddb |
| **Where** | `TrainControlUI.java:7433-7434`, `:8217-8218`; `AutonomySession.java:7411-7413` (`writeHome`); `test/core/testATrainIsPutOnlyWhereItCanStart.java:37-42`, `:229` |

`8370abb1` rewrote `placementCopy`, `copyFacing`, `moveOntoFacingCopy` and the test class's first paragraph to say a train stands on a copy facing its way even where trains may not arrive at it.  These were not touched and say the opposite, in the present tense (behaviour.md's sentence is in DCN3-B1):

- `TrainControlUI.copyFacing` (`:7433`, `1c855483`): *"The session's rule, which every door that puts a train down asks (GUI-B1): **only a copy a train may be put down on** (MT-394)"* - it delegates to `AutonomySession.copyFacing`, which since `8370abb1` returns a barred copy when no placeable one faces that way (`AutonomySession.java:1582-1597`).
- `TrainControlUI.placeableFacings` javadoc (`:8217`): *"a copy trains may not arrive at cannot be placed on (**`copyFacing` refuses it**)"*.
- `AutonomySession.writeHome` (`:7411`): *"Only the facing the train standing here has, which is a copy a train stands on, **so no impossible facing is saved**"*.  A train can stand on a barred copy again, and `homeFacingOf` then returns that copy's facing, which is saved.  Harmless - the build marks a home facing-fixed only on the copy `homeCopy` chose, and `homeCopy` honours a facing only on a copy trains may arrive at (`AutonomyBuilder.java:1190-1196`, behaviour.md §6: *"A home with no facing - an old setup, or a copy no train may arrive at - is still the square"*) - but the protection is in the build, not where this comment puts it.
- `testATrainIsPutOnlyWhereItCanStart`'s class javadoc keeps its round-1 paragraph (`:37-42`) under the new one: a barred facing reaching the setup, *"and two things then acted on it **without asking whether trains may arrive at that copy**: the build, which put the train on the copy facing that way, and the Facing door ... **The train then stood on a copy that is not a station, and autonomy would not start it**"* - told as the defect, it is word for word what the paragraph above it (`:25-33`) now calls the truth.  And `:229`: *"The Facing door moves a train onto a copy facing that way that it can be started from, **or nowhere**"* - `testTheFacingMenuMovesTheTrainOntoACopyFacingThatWay` twenty lines above asserts it moves the train onto a copy it cannot be started from.  (The class name now says the opposite of half its claims; renaming is not proposed, since names are cited.)

Reading and `git blame`; nothing to execute.  **Suggested fix:** say at each that a copy trains may arrive at comes first and a barred copy facing that way is the fallback; at `:37-42` put the paragraph in the past tense ("GUI-B1's first repair ..."), and `:229` "... one it can be started from where one faces that way".

### DCN3-C2 - GUI2-B1 made "falls through to copy 0" false, and eleven present-tense sentences still give it as the mechanism - three of them in failure messages

| | |
|---|---|
| **Disposition** | Fixed - 031a7ddb |
| **Where** | `AutonomySession.java:5219-5221`, `:6158-6159`; `AutonomyEditorPanel.java:3746-3747`; `TrainControlUI.java:8104-8108`; `test/regression/testEditorSurfaceRules.java:68-69`; `test/core/testAPastedTrainKeepsItsDirection.java:50-51`, `:292`, `:397-398`, `:442`, `:534`, `:594` |

Since `8370abb1` `placementCopy` never returns copy 0 by default: no facing, or a facing no copy holds, goes to `startableCopy` - the first plain copy trains may arrive at (`AutonomyBuilder.java:729-734`, `:776`).  (Since `1c855483` the no-match arm had already stopped being "the first copy".)  Still stated as the present mechanism:

- *"`placementCopy` falls through to the first copy and quietly turns the train round"* - `AutonomySession.facingChoices` (`:6158`), `AutonomyEditorPanel.buildFacingMenu`'s comment (`:3746`), `testEditorSurfaceRules` (`:68`); and `facingsThatCannotBeHeld`'s javadoc (`:5219`): *"the builder falls through to the first one"*.
- *"`placementCopy` falls through to COPY 0 when nothing matches.  Copy 0 is whichever the builder happened to emit first"* - `TrainControlUI.java:8106-8108`, and `testAPastedTrainKeepsItsDirection` `:50`, `:397`, `:534`, plus three assertion messages (`:292`, `:442`, `:594`) that would explain a future failure by a mechanism that no longer exists.

The reasoning each supports mostly still holds (`startableCopy` is still order-dependent, so OB-183's stability test is still needed, and a facing no copy holds still turns the train round), so this is wording, not logic.  Past-tense sites (`testAutonomyDiagramSession.java:606`, `testEditorSurfaceRules.java:263`, `:347`) are fine.  `grep -rn -i "copy 0\|first copy"` over `src` and `test`.  **Suggested fix:** "falls back to the first copy trains may arrive at (`startableCopy`)".

### DCN3-C3 - The placement class's MUTATION note promises a `placementCopy` claim the round removed: GUI-B1's build-side preference is no longer pinned there

| | |
|---|---|
| **Disposition** | Fixed - d65df6cb, with TDY3-C3 |
| **Where** | `test/core/testATrainIsPutOnlyWhereItCanStart.java:47-49`; `AutonomyBuilder.java:742-756` |

The note: *"MUTATION: have `placementCopy` or `moveOntoFacingCopy` take the first copy facing that way again, and its claim fails"*.  At `08a47bdd` the `placementCopy` half was carried by `testTheBuildNeverStandsATrainOnACopyItCannotStartFrom` (facing W at BottomMainA, asserting `isDestination`).  `8346be65` replaced it with `testTheBuildKeepsTheFacingTheSetupRecords`, which asserts the facing - and at BottomMainA the only copy facing west is the barred one (the test's own precondition, `:105-107`), so "the first copy facing that way" and the fixed code stand the train on the same copy.  The other build claim sets no facing.  So dropping `&& arrivalAllowed(...)` from `placementCopy`'s first two loops - GUI-B1's case, which the class javadoc says "stands" - fails nothing in this class.  The `moveOntoFacingCopy` half is still carried (`testTheFacingDoorNever...` at BottomMainPost, where the first south-facing copy is barred and the turning one is not).  A reading of the test against the code; whether another class catches it needs the run below.

**Verification request (execution):** drop `&& arrivalAllowed(nodes.get(copy))` from the two facing loops of `placementCopy` (`:748`, `:756`) and run `core.testATrainIsPutOnlyWhereItCanStart`, then the battery.  **Predicted:** this class green; confirms the note is false here.  If nothing in the battery fails, the build half of GUI-B1 is unpinned.  **Suggested fix:** a build claim at BottomMainPost - place the probe with facing S, build, assert `standing.isDestination()` and that it faces S (it should land on `BottomMainPost (northbound, reverse)`).

### DCN3-C4 - The round's text commits joined words across twelve line breaks: `layoutas`, `youonly`, `andcan`, `occupyon`, and eight sentences run together

| | |
|---|---|
| **Disposition** | Fixed - 031a7ddb, with REG3-C4 |
| **Where** | `Readme.md:128`, `:374`, `:379`, `:380`; `AutomationAPI.md:10`, `:228`, `:384`, `:486`, `:493`; `docs/reference/behaviour.md:1002`; `src/org/traincontrol/gui/PositionAwareJFrame.java:199`; `test/core/testAutoLayout.java:2253` |

Each is a replaced span whose trailing space or line break was dropped: `layoutas a graph` (Readme feature list), `as before.An autonomy.json` (changelog, from `8370abb1`), `andcan clear` and `driven away.To keep` (changelog), `derived from it.The settings`, `"Validate Configuration".Any errors`, `a point youonly ever drive across`, `either mode.Once you are finished`, `autonomy editor.Note that` (AutomationAPI.md), `share one prompt:the number box` (behaviour.md §5b), `occupyon the screen` (the javadoc GUI2-C5's fix re-indented), `again.The comment` (testAutoLayout).  Seven of the twelve are in the two documents a non-technical user reads.  Found by a read-only scan of every added line in the range for a lower-case letter or quote followed by `.`/`:` and a capital, and for tokens that split into two common words; the scan found no others in `*.md` or `*.java`.  **Suggested fix:** one space each; `AutomationAPI.md:10` also re-wrap, since the join left a 200-character banner line.

### DCN3-C5 - `setAtomicRoutes`' javadoc now says both that neither direction leaves an edge held and that one direction can, and that the road is not chosen by the setting when the code asks the setting first

| | |
|---|---|
| **Disposition** | Fixed - 031a7ddb, and the second edit in 6cb11933 (DCN4-C1) |
| **Where** | `Layout.java:10587-10595` (`setAtomicRoutes`); `:7918-7920`; against `:4083-4093` (`unlockPath`) |

GUI2-C1's fix appended a correction and kept the sentence it corrects: *"So neither direction gives an edge back twice **or leaves one held** ... That is about giving back twice.  **True-to-false can still leave one edge held** (GUI2-C1)"*.  The reader is told both.  And the paragraph's premise - *"`unlockPath` decides how to give track back by whether that run gave any back early ... **not by the setting at its end**"* - is half the condition: the atomic road is `this.atomicRoutes && heldItAll` (`:4093`), so a run that gave nothing back early takes the careful road when the setting is off at its end - which is the road GUI2-C1's own case goes down.  The failure handler's new comment (`:7918-7920`, *"on both of its roads, which it chooses by whether there are any"*) repeats the half.  Unreachable today (the checkbox refuses mid-run), so a sentence, as GUI2-C1 said.  Reading only.  **Suggested fix:** delete "or leaves one held"; "decides by whether that run gave any back early and, where it gave none, by the setting".

### DCN3-C6 - The sentence round 2 relies on to explain a train it stands on a barred copy tells the operator that his station is not a station, by the copy's name

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a, with GUI3-C1 |
| **Where** | `messages.properties:1784` (`autolayout.why.startNotStation`) and the seven translations; `Layout.java:4944` (`explainCannotStart`); cited as the remedy at `AutonomyBuilder.java:763-764`, `AutonomySession.java:1555-1557`, `testATrainIsPutOnlyWhereItCanStart.java:31-33` |

Round 2's comments say that where only a barred copy faces a train's way, *"it is refused with a sentence that says why"* and *"autonomy will not start it there, and says so - 'It is standing on {0}, which is not a station' - which is the truth"*.  `{0}` is `at.getName()`, the copy: the locomotive list (`AutoLocomotiveStatus.java:735`, `:897`) and the editor (`AutonomyEditorPanel.java:7888`) will say *"It is standing on BottomMainA (westbound), which is not a station, so autonomy will not start it from there."*  To the operator BottomMainA is a station - he made it one and barred one side - and nothing names the actual reason (trains may not arrive there from the east, so a train facing west cannot be started) or the remedy (turn it with the Facing menu, or drive it off by hand).  GUI-C7's round-1 rule, *"NAMED AS THE SQUARE, not as the copy ... a copy's name states a heading"*, was applied to the placement questions and not here.  Round 1 kept trains off such copies; since `8370abb1` a build of a setup recording that facing, or a reversal on the throttle at a one-way platform, reaches it again, as before round 1.  Rests on reading.  **Verification request (execution, optional):** in `testAReversalOnTheThrottleIsFollowed`'s fixture, after the flip, `running.explainCannotStart(model.getLocByName(PROBE))`; confirmed if it names `BottomMainA (westbound)` and says "not a station".  **Suggested fix:** a second sentence for a copy that is a barred arrival of a station square - base name, the barred side, and "turn it round with Facing, or drive it off by hand" - translated with escapes.  Adam may prefer to fold this into DCN3-B1's question.

### DCN3-C7 - Three small record inaccuracies: MT-482's hold comment credits the wrong commit, two converted D titles were cut mid-word, and REG-C3 / DCN2-C5 do not name the commit that fixed `Readme.md:128`

| | |
|---|---|
| **Disposition** | Fixed - 9a9a8564 (MT-482) and the round-3 catalogue commit (the two D titles; REG-C3 and DCN2-C5 name 4132d260) |
| **Where** | `docs/manual-tests/tests.md` MT-482 (the `b53439dc` comment); `triage.db` rows `TDY-D9`, `TDY-D13`; `docs/reviews-2026-09-23/REG.md:183`, `DCN2.md:107` |

- **MT-482** (`b53439dc`): *"6b7301fc and **12ed2faa (the square a train stands on is spent first on every kind of square)**"*.  `12ed2faa` narrowed it ("spent only where the arriving rail's place provably is that square"); TDY2-C5 is the finding that it was NOT spent on a square emitted once, and `8370abb1` - written minutes before this comment, and on this entry's path (it changes what is grey at a dead end's turning copy) - is not named.  A re-run on today's build covers it either way; the comment should name `8370abb1` and not credit `12ed2faa` with it.  (MT-475 and MT-481's round-1 hold comments predate `8370abb1`; "a re-run on the current build" in each still covers it.)
- **The heading conversion** (`2f4448b6`) cut two bullet titles at the wrong place, and the store keeps the titles: `TDY-D9`'s is *"...new control asks the model (ui.isTrackCovere"* (truncated mid-identifier, backticks lost), and `TDY-D13`'s is *"FR-096's entry guard"*, with the verdict left in a body that now begins *"is thrown only where..."*.  The DCN D rows kept a trailing colon (`"Readme facts checked and right:"`) - harmless.  Every other converted D item's text is word for word the bullet's (diffed).
- **REG-C3's corrected disposition** names `e2223851` and `8370abb1`; its Where includes `Readme.md:128`, which `4132d260` changed.  DCN2-C5's disposition ("Fixed - 8370abb1") has the same gap.

Reading, `git show`, a read-only query.  **Suggested fix:** a one-line correction under MT-482 (append-only); re-title the two rows; add `4132d260` to both dispositions.

## D - not defects (checks that came back clean)

### DCN3-D1 - TDY2-A1 / GUI2-A1 / AUT2-A1, the build and the two follow doors: the round-2 comments say what the code does

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`placementCopy` (`AutonomyBuilder.java:742-776`): facing copies trains may arrive at first, plain before turning; then any copy facing that way, plain first; then `startableCopy`.  `copyFacing` (`AutonomySession.java:1562-1600`): placeable copies facing that way first, then any copy facing that way; `@return ... null when no copy of the square faces that way` is now exact.  `moveOntoFacingCopy`'s comment ("never a copy facing another way ... `copyFacing` says why") matches.  The builder's *"The copy still exists and still carries traffic"* (`:982-983`) is the premise the new comments rest on.  `git diff 1c855483^ 2f4448b6 -- test/baseline/` is empty: the baseline is back byte for byte, as the commit message says.  `startableCopy`'s "copies are emitted N, E, S, W" / "copy zero ... the north or east side" is right for BottomMainA (E before W) and BottomMainPost (N first).  The contradictions are in the OTHER comments (C1, C2) and at the doors (B1).

### DCN3-D2 - TDY2-C4 (`1facc0c2`): the new comments match the four cases the editor meets

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Traced `knownHomeFacing` -> `homeFacingOf` for: the train standing here on a named copy (that copy's facing, no question); standing elsewhere on the railway (`onTheRailway`, null, the editor asks over `homeFacingsFor`); on this square but on no named copy (null, and `homeFacingsFor` is empty so `FacingPrompt.wouldAsk` is false - no question, no facing); no running layout (the setup's facing only if the setup has it here).  The editor comment's two cases are the first ("asked about for nothing": standing here while the setup had it elsewhere) and the second ("given the setup's stale facing without a question": the setup still has it here after it drove off).  `knownHomeFacing`'s javadoc is exact.  The claim `testATrainThatHasDrivenOffGivesNoFacing` asserts the variable (`knownHomeFacing` null), with two preconditions that make it reach the new branch.

### DCN3-D3 - The other code comments of `8370abb1` checked

| | |
|---|---|
| **Disposition** | Closed - checked clean |

AUT2-C1 / TDY2-C6 (`Layout.java:3689-3693`, `:8216-8225`): every refusal inside `configureAndLockPath` takes nothing or removes its own claim first, as both say.  TDY2-C5 (`:7412-7418`): `getNeighborsAndIncoming` builds the outgoing list first (`:2236-2262`, sorted within each half), the fork loop keeps the first rail per neighbour (`if (neighbours.add(...)) segment = candidate`), and `AutonomyBuilder.blockFor` writes a block only on a split square - all as the comment says.  REG2-C2 (`TrainControlUI.java:24576-24580`): `menuNoSetupPossible` is the key `AutonomyMenu` shows over a Central Station layout.  REG2-C6 (`MarklinControlStation.java:4134-4148`): `MarklinRoute.fromJSON` reads `enabled` from `auto` (`:1321`), the constructor calls `executeAutoRoute` (`:144`), which starts a monitor only `if (this.enabled && this.hasS88())`, and the monitor logs `route.running`.

### DCN3-D4 - The comment fixes of `4132d260`, site by site

| | |
|---|---|
| **Disposition** | Closed - checked clean |

DCN2-C1: all seven sites (`showTextLabels`, `hideTextLabels`' summary, `LayoutEditor.java:1608-1614`, `:1854-1858`, `AutonomyEditorPanel.java:813-815`, `textLabelsChanged`, `LayoutGrid.java:1452`) now state OB-272's rule; "the other four options" is right (five options, the switch expresses one).  DCN2-C2 / TDY2-C3 / AUT2-C3 / GUI2-C1's three sites: `releasedEarly` named at `:7915-7924`, `:8009-8011`, `:8519-8521`, and it is removed after the unlock at both endings (`:8066`, `:8885`); testAutoLayout's javadoc, MUTATION note and message name it.  AUT2-C3's identity-loop note (`:7505-7508`) and `:2507-2509`.  TDY2-C1 (`Layout.java:1411-1413`): `HomeStaging.atHome` compares `getCopyFacing` when the home is facing-fixed (`:2550`).  TDY2-C2: `whyABerthCannotHoldIt` returns null only when no span, the berth's included, is measured (`:9805-9814`).  GUI2-C5 / DCN2-C11: `resumesFromJsonAtStart` names `activateRoutes` and `activateRouteIDs`; the `PositionAwareJFrame` opener is indented (but see C4).  The exception is the `setAtomicRoutes` javadoc (C5).

### DCN3-D5 - The user documents' round-2 sentences checked against the code

| | |
|---|---|
| **Disposition** | Closed - checked clean |

REG2-C1 / DCN2-C5: `Layouts -> Download Central Station Layout Files` exists (`ui.main.toolbar.layouts`, `downloadCSLayout`).  Readme `:128`: the Autonomy menu has `Import...` (`AutonomyMenu.java:253`, `:307`).  REG2-C3's changelog line: the start-up JSON load runs only under `AutoLoadAutonomyMenuItem.isSelected()` and `resumesFromJsonAtStart` (a stored key and a non-empty graph), so "only where Load Autonomy was ticked by hand" holds.  REG2-C4 / DCN2-C4: "Can Be Chosen in Full Autonomy"; AutomationAPI.md `:384`, `:459`, `:486` state the 2026-09-06 rule (start exempt, nothing sent to or through).  DCN2-C3: every remaining "graph UI" / "Validate JSON" / "Export Current Graph" line (`:371`, `:400`, `:515`) sits under a v3.0.0 banner; `:415` points at §5b; `:440`'s "a station's settings by right-clicking its square, the rest in the autonomy settings" and `:479-480` hold - the editor's square menu carries **Edit Locomotive At...** (`AutonomyEditorPanel.java:5263`), i.e. `GraphLocAssign` with speed and the two functions.  `:449-451` matches behaviour.md §5d.  DCN2-C6: `migrateStationLabels` converts on every `open`, and only a label naming a station the setup knows (`tileNamed`).  DCN2-C8: `disabledLinks` and `tileLengths` are the store's keys (`AutonomyCompanionStore.java:4225`, `:4235`); the four `menuOnly` items are right (`:1674`, `:2018`, `:3882-3884`).  DCN2-C11: the routing table's ten names are the ten dropdown labels, sentence-cased; the three walks and the §5d pointer are in behaviour.md.

### DCN3-D6 - DCN2-C7's rewrite: "moves every white one" holds, and the setup-versus-railway half is narrow

| | |
|---|---|
| **Disposition** | Closed - checked clean |

A home is offered only for a placed locomotive (`promptHome` -> `homeChoices(..., placedLocomotives(), current)`, `AutonomyEditorPanel.java:5038`), so a white caption is a placed train away from home, which Return Home stages.  DCN2-C7's second gap - the caption reads the setup, Return Home the railway - is narrowed by the editor capturing the running layout when it opens (`TrainControlUI.java:6848-6856`), and the Homes caption exists only in the autonomy editor (`LayoutGrid.java:1417-1419`).  Not re-raised.

### DCN3-D7 - The tracker commit `b53439dc`, apart from MT-482's attribution (C7)

| | |
|---|---|
| **Disposition** | Closed - checked clean |

MT-293 is **superseded** with a dated comment naming MT-478, which is **fixed validated** - the README's shape for a replaced entry.  MT-023's correction is appended under the old comment, not written over it, and the date matches behaviour.md §7c.  The ledger header's *"394 fixed validated and 52 superseded"*, 446 of 486, and the 40 ledger rows match a recount of every entry's Disposition line (394 + 52 + 37 + 3 = 486).

### DCN3-D8 - The catalogue commit `2f4448b6`: counts, statuses, and the D conversion

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Read-only query: 3,909 rows, 3,552 distinct refs - the figures behaviour.md `:2222` and open-questions.md `:354` quote; 183 rows from the ten documents (AUT 12, GUI 18, REG 16, TDY 19, DCN 33; AUT2 12, GUI2 13, REG2 19, TDY2 18, DCN2 23), and 3,726 + 183 = 3,909.  The rows not Closed are exactly AUT-C2, DCN-C3, REG-B1, REG2-C3, REG2-C7 ("Open - Adam's decision") and AUT2-C2, GUI2-C2, GUI2-C4 ("Open"), as the commit message lists.  The DCN, AUT and TDY D sections diffed word for word against their bullets at `08a47bdd`: nothing lost (TDY's two titles aside, C7).  The corrected round-1 dispositions (DCN-C4, C7, C9, C15, REG-C3, GUI-B1, GUI-C1, GUI-C4, AUT-C1, TDY-C2) each name the round-2 commit that finished the job and the finding that found it.  Every "Fixed" in AUT2, GUI2, REG2, TDY2 and DCN2 names a commit that contains the change; the only disposition I dispute is AUT2-A1's "recorded for Adam" (B1).

### DCN3-D9 - `91daff27` (GUI2-C3): 506 values, every change an added diacritic, and the context-dependent ones read right

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Read-only Python over `git show 91daff27^:` and `91daff27:` of the seven bundles: 95 + 79 + 88 + 90 + 58 + 4 + 92 = 506 changed values; every one maps back to its old value character by character when each accented letter may stand for its stripped form or its ASCII spelling (ae/oe/ue/ss, Danish ae/oe/aa, Polish l); key sets unchanged (1,821 each); all seven files still pure ASCII with escapes.  The accents that change meaning were read in their sentences: es *si/sí, el/él, esta/está, que/qué, mas/más, aun/aún*, fr *a/à, ou/où, la/là*, it *e/è, se/sé, li/lì, la/là, ne/né*, nl *een/één*, pl *ze/że, ta/tą, te/tę, ja/ją* - each is the accented word the sentence needs (e.g. it `route.errorSettingNotOneOf` "0 non è 1 né 2").

### DCN3-D10 - `264f2a73`'s new comment matches the door

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`openAutonomyEditorIfItCan` (`TrainControlUI.java:8709-8716`) asks `whyAutonomyEditorCannotOpen` and not `refuseWhileEditorOpen`, and `whyAutonomyEditorCannotOpen` (`:5045-5060`) returns null for an open, displayable editor - as the guard test's new comment says.

### DCN3-D11 - The paste's may-turn prompt offers every copy's facing, and that cannot offer a barred-only one in practice

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Checked because round 2 changed what `copyFacing` returns for such a facing: the prompt's choices are `facingsFor(aimed).values()` (`TrainControlUI.java:7221-7222`), not the placeable ones.  It is asked only on `mayTurnTiles()`, which excludes compulsory turns (`AutonomySession.java:4521-4526`); a may-turn square with one side barred still has the other side's plain and turning copies, facing both ways, so each offered facing has a placeable copy.  Only a may-turn square barred on every side could offer one, and then `moveLocomotive` refuses the non-station with `errorPointIsNotStation` and the clipboard is kept.  `facingsThatCannotBeHeld` likewise asks `facingChoices` (every copy), so a train on a barred copy is not reported as an impossible facing - consistent with round 2.

## What this pass did not cover

- **Whether the round-2 claims were red for the right reason** beyond the placement class (C3) and TDY2-C4's claim (D2).  The five claim classes of `8346be65` belong to the TDY, GUI, AUT and REG validators; I read only their javadocs and messages.
- **The behaviour behind B1 and C6.**  Both rest on reading the code and the round's own claims; B1's doors half and C6's message each carry a one-JVM verification request.
- **Translations beyond `91daff27`.**  The range changed no English bundle value, so no translation fell behind.  `91daff27` was checked for what it changed (D9), not for whether values it did not touch still spell accents in ASCII.
- **`tests.md` beyond the three entries `b53439dc` touched** and the held entries whose paths `8370abb1` crossed (MT-475, 481, 482, 483).  No MT was written for TDY2-A1; the behaviour it chose (a train reversed at a one-way platform is not started) is operator-visible, and whether it needs a hands-on entry is part of B1's question.
- **`testATrainIsDispatchedOnce`'s class MUTATION note** ("both tests") predates the range (three tests at `08a47bdd`, four now) and was not raised.
