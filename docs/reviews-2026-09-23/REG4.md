# Regression validation, round 4: round 3's REG fixes and the range since, seen by a user upgrading from v2.8.1

**Status:** closed

**Prefix:** `REG4`

**Reviewed:** branch `autonomy-diagram-r0` at `9f5d8e23`, 2026-09-23.  Baseline: REG3 at `2f4448b6` and the five commits since (`git log 2f4448b6..9f5d8e23`: `d65df6cb`, `4be3798a`, `9a9a8564`, `031a7ddb`, `9f5d8e23`); `master` at `5f0a75e3` (v2.8.1) for "what did 2.8.1 do".

**Method:** read-only.  `git show` of the five commits; REG3's five dispositions walked against `4be3798a` / `031a7ddb`, and the one claim (`testAnImportedFacingGuessCanStart`) read against the pre-fix import at `d65df6cb`.  The import's guess traced through `homeFacingsFor` -> `AutonomyBuilder.homeFacingsAt` / `nodesFor` / `facingOf` / `placementCopy` / `startableCopy`, and against the order `importLegacy` writes a point's facing and its turn flags.  "What would 2.8.1 have run them in" answered from Adam's frozen 2.8.1 file (`test/layouts/live-snapshot/config/autonomy_legacy/autonomy.json`: each placed point's out-edges), the frozen setup (`barredArrivals`, `tileDirections`), the page file `1 - Main.cs2` (tile types and rotations around each placement) and the blessed build `test/baseline/configuration.json` / `graph.txt` (which copies a two-way square emits, and where each leads); and `master`'s `Layout.java` for 2.8.1's direction commands and its non-atomic release rule.  Every caller of `explainCannotStart` and `moveLocomotive` (src and test); every `public`/`protected` line in `git diff 2f4448b6 9f5d8e23 -- src`; the Autonomy tab's mount conditions, the Start guard and the download item for REG3-C3; `tailHasProvablyPassed` and its one caller for REG3-C2's sentence.  The eight bundles counted for non-ASCII bytes; the range's added lines scanned for fused words; `findings.tsv` recounted.  Python only read files, through stdin.  Nothing compiled, run or written except this file; `cs2_sample_layout/` not opened.

## A - high

### REG4-A1 - the import's facing guess ignores the direction the file itself ran each train in: wherever the diagram lets trains arrive from both sides, a 2.8.1 train can be stood facing opposite to how 2.8.1 drove it, and the log says "or just run them"

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933 (claim 1c6ae0fe, red first): the import takes each train's facing from the side its point's edges leave by, the next point on each side, and guesses only where they cannot say; the log line no longer says "just run them".  MT-491 (12ef4379).  A rule taken in Adam's absence - see the round's report |
| **Where** | `AutonomySession.java:765-790` (the reasoning: "the file cannot say", "no more likely to be right"), `:799-826` (the guess); `messages.properties:1487` (`autosetup.ui.facingsGuessed`); `Layout.java:8463`, `:8833` (the only direction commands autonomy sends: `switchDirection()` at a turning point); `test/layouts/live-snapshot/config/autonomy_legacy/autonomy.json`; `test/baseline/configuration.json`, `test/baseline/graph.txt:38-39` |

The task's central question - for a 2.8.1 user's file, what facing each train now gets, and is it what 2.8.1 ran it in - has an answer the code's own comment rules out.  The comment says the old graph "had one Point per sensor and no notion of facing, so the file cannot say", and that choosing the first copy "is no more likely to be right" than any other (`:772-786`).  **The file can say, for every point whose outgoing edges leave it one way.**  A 2.8.1 graph encodes direction as one-way edges (a train on P goes only along edges that START at P), and that is how Adam's is written: 88 of the 90 edges in his frozen file have no reverse twin (only BottomInner <-> BottomInnerOtherside runs both ways), so for almost every point the file names where 2.8.1 would send a train standing there - and, since 2.8.1 never commanded a direction except to reverse at a terminus, which way that train's decoder drives it.

**On his own file.**  The import carries no direction from the file's edges onto the diagram (`importLegacy` skips `edges`; only `whatALegacyImportLeaves` reads them, to count commands, lengths and locks), so a fresh upgrade imports onto the diagram's defaults: plain track both ways, and a switch out of its toe only (`TileGraph.defaultDirection`, `:736-760`).  The four placements, with where 2.8.1 sent each train (`edges` whose `start` is the point) and what the import guesses there:

- **TopMainR1** (`5:5,4`, a straight N-S sensor): the file's only out-edge is TopMainR1 -> TopMainPost, north (`5:7,2`), through the switch at `5:5,2` whose leg down to TopMainR1 is a fork; in from TopMainR1Pre, south, over plain track.  By default a train may come DOWN through that switch (toe to fork) and may not go up it, so the square is arrived at from both N and S; copy zero arrives by N and faces **S**, and its departure, south to TopMainR1Pre, is open.  The import stands 2-8-4 3505 SP facing south; 2.8.1 only ever drove it north.  Adam's own setups show the switch had to be opened for his 2.8.1 direction: `5:5,2` (`#0,0` and `#1,0`) is set `BOTH` in the live snapshot, and the same switch (`5:4,2`, the diagram one column over) in the blessed baseline - whose build then emits "TopMainR1 (southbound)" first, with its one edge to TopMainR1Pre (`test/baseline/configuration.json`; `graph.txt:38-39`), and whose setup records the 2-8-4 facing S.  (Which leg of `5:5,2` is the toe is read from the tile row, not built; the probe settles it.)
- **TopMainR1Inter**, **TopMainR2Inter** (N-E curves): out-edges to TopMainR1Pre / TopMainR2Pre, leaving by E; copy zero arrives by N and faces E - matches.
- **Tunnel** (N-S): out-edges to BottomMainAPre / BottomMainBCPre, south; copy zero arrives by N and faces S - matches.

REG3-C1's fix changes none of this where no side is barred (every facing is then one trains may arrive in, so the guess is copy zero's as before - REG4-D2).  On Adam's current railway the square above TopMainR1 is directed (`5:5,3#0,0: TOWARD_B`, the signal), which may be why his own imports have not shown it; a fresh upgrader has no such setting.

**What a wrong guess does.**  Autonomy never sets a decoder's direction; it only calls `switchDirection()` at a turning point (`Layout.java:8463`, `:8833`), and "the copy IS the direction" (TDY2-A1).  A train whose decoder drives it north, stood on a copy facing south, is dispatched along a path locked south and drives north over track nothing reserved - TDY2-A1's consequence, graded A there.  At 2.8.1 the same train could only be sent along its out-edges, so the graph and the decoder agreed by construction; this is new to the upgrade.  The log line the import writes for exactly these trains ends "Check them on the diagram, **or just run them - the first journey records which way each one really faces**": for a wrong guess the first journey records nothing true - the train is driving away from the sensors its path waits for - and "just run them" is the instruction that leads into the hazard.  Nothing compensates: `facingsThatCannotBeHeld` asks only whether a copy holds the facing, and the hand-dispatch menu offers the destinations the (wrong) copy leads to.

**Grading.**  By consequence, A.  Mitigations, so the coordinator can weigh them: the code predates this range (ACC-C4, 2026-09-04, made the guess deterministic; nobody asked whether the file held the answer - no row in `findings.tsv` does); the import announces that facings were guessed and the diagram draws each train's arrow; and a square the diagram lets trains arrive at from one side only has one copy and no guess to get wrong.  **This rests on reading - the frozen files, the blessed build, and tile rotations for the one diverging square - and needs the probe below.**

**Verification request (needs execution; core test on a copy of the live snapshot).**  In a `LayoutSandbox` of `live-snapshot`, remove `tileDirections` and `barredArrivals` from the sandbox's `setup.json` before `session.open` (a fresh upgrade's diagram), create and activate a new configuration, `importLegacy` the frozen `autonomy_legacy/autonomy.json` unchanged.  For TopMainR1's tile: **proves** - `session.getFacing(tile) == Side.S`, and after `build(session)` the paths `layout.getPossiblePaths(train, true)` offers the 2-8-4 all start TopMainR1 -> TopMainR1Pre, while the file's out-edges from TopMainR1 name only TopMainPost.  **Refutes:** facing N, or a single copy there (the switch's defaults already make the square one-way, and the finding then has no instance on Adam's file - the mechanism stands for any square arrived at both ways).  A general form, worth keeping as a claim: for every placed point whose legacy out-edges all end at points matched to tiles, the imported facing is the side the reduced edge to those tiles leaves by.

**Suggested fix (Adam's decision on the rule; the mechanism is mechanical).**  Read the facing from the file where it holds one: for the placed point, map each out-edge's `end` to its tile through the same `bySensor` map, take the exit side of the reduced edge from the placement's tile to that tile, and where every out-edge leaves by one side, record the facing of the copy that departs by it.  Guess only where the file's out-edges leave both ways (where 2.8.1 itself could send the train either way) or cannot be matched.  And the log line: "or just run them" should go - for a guessed facing, "check each one on the diagram, and turn any that face the wrong way with the Facing menu before starting autonomy" is the safe instruction.

## B - medium

None.

## C - low

### REG4-C1 - the import's new guess is made before the same import marks the square must-reverse: at a terminus or reversing square with one side barred, it now always picks the facing only the barred copy holds

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933 (claim 1c6ae0fe, red first): the facings are found after the points loop, over the finished setup |
| **Where** | `AutonomySession.java:794-826` (the guess, `homeFacingsFor` at `:804`), `:898-916` (the same point's `MUST_REVERSE`, written after the guess); `AutonomyBuilder.java:628-630` (`nodesFor`: a must-turn square has only turning copies), `:826` (a turning copy faces its arrival side), `:727-777` (`placementCopy`, the TDY2-A1 fallback at `:759`); `test/core/testAnImportedFacingGuessCanStart.java:98`, `:145`; `messages.properties:1487` |

REG3-C1's fix (`4be3798a`) makes the import guess "the first facing trains may arrive in": among `facingsFor(tile)`, the first that is in `homeFacingsFor(tile)` - the facings of the copies whose arrival side is not barred (`AutonomyBuilder.homeFacingsAt`).  Both are asked of the setup **as it stands at that moment of the import**, and the import writes a legacy `terminus`/`reversing` point's `MUST_REVERSE` onto the square only further down the same loop iteration (`:898-916`, after the placement block).  `MUST_REVERSE` is per configuration (`getPointProperty` reads the active configuration's `points`), so on an import into a configuration that does not already mark the square - a first import, or an import into a new configuration, the population REG3-C1 named - the guess is computed over the square's **plain** copies and the build then emits only its **turning** copies (`nodesFor`, `:628`: `if (!must && ...) out.add(plain)`; `:630` the turning copy).

The two sets of copies hold the same facings the other way round.  On a through square with arrival sides A and B:

- before the mark: plain-A faces B, plain-B faces A;
- after it: turn-A faces A, turn-B faces B (`facingOf`, `:826`: "Turned round: pointing back at the side it came in by").

With A barred, `homeFacingsFor` at guess time is {facing of plain-B} = {A}, so the guess is **A**.  After the import, the only copy facing A is turn-A - arrived by the barred side.  `placementCopy`'s two loops over copies trains may arrive at find nothing facing A, and the TDY2-A1 fallback (`:759-776`, "A COPY THAT FACES IT ANYWAY") stands the train on turn-A, built `station: false`.  The imported train is on the barred copy, autonomy will not start it, and `explainCannotStart` says "It is facing the way trains may not arrive at {0} ... Turn it round, or open that side" - REG3-C1's outcome exactly, while the log line (`autosetup.ui.facingsGuessed`, `:1487`) says "The first one that fits was used ... or just run them".

**The fix made this case worse, not just left it.**  Before `4be3798a` the guess was copy zero's facing, so it came out right when the barred side was the one the build emits first (N or E) and wrong when it was the second; the new rule picks the barred facing whichever side is barred.  Concretely, BottomMainA (`5:20,12`, E barred, sides E and W) with the legacy point marked `terminus: true`: pre-mark copies are plain-E facing W (barred) and plain-W facing E, so the new guess is **E**; post-mark the copies are turn-E facing E (barred) and turn-W facing W, so the train stands on turn-E.  At `d65df6cb^` the guess was copy zero's W, which lands on turn-W - a station.

**Why nothing catches it.**  The claim (`testAnImportedFacingGuessCanStart`) takes `mayArrive = session.homeFacingsFor(mainA)` **before** the import (`:98`) and asserts the guess is in it (`:145`); BottomMainA's legacy point is not a terminus, so before and after agree and the case never arises.  `facingsThatCannotBeHeld` does not fire (a turning copy does hold facing A).  Only a square carrying both a barred side and a legacy turn flag reaches it: Adam's own frozen file does not (none of its seven barred squares is a legacy terminus or reversing point, and its four placements - TopMainR1, TopMainR1Inter, TopMainR2Inter, Tunnel - are plain stations), but `AutonomyBuilder.java:988-989` calls restricting a terminus platform "the most natural use this setting has".

**2.8.1 parity.**  At 2.8.1 the train stood on the file's point and autonomy started it (a terminus reverses it on arrival; the point had no facing).  After the import it does not start until the operator turns it round or opens the side - there is a way past, and the sentence names it.  Graded C with REG3-C1, whose consequence this is, on a narrower population; but it is the fix failing its own case, and in the direction of more trains stood on barred copies than before it.

**Verification request (needs execution; core test).**  In `core.testAnImportedFacingGuessCanStart`, a second method identical to the first except that the BottomMainA legacy point also gets `point.put("terminus", true)`.  After `session.importLegacy(legacy)`: assert `session.isMustTurnAround(mainA)` (precondition: the import marked it), then assert `session.homeFacingsFor(mainA).contains(session.getFacing(mainA))` - **re-asked after the import**.  **Proves:** red, the guess is E and `homeFacingsFor` is now {W}; and a `build(session)` / standing check as in `testATrainIsPutOnlyWhereItCanStart` finds the train on a Point that is not `isDestination()` with `layout.isABarredCopyOfAStation(it)` true.  **Refutes:** green.  **Control:** the same method at `d65df6cb^` (copy zero, W) is green - the fix is what turns it red.  The first method's assertion is worth moving to after the import too, since "a way trains may arrive" is a question about the finished setup.

**Suggested fix.**  First REG4-A1's: where the file's out-edges say which way the train runs, that is the facing (at a turning square the side it leaves by is the side its turning copy arrived by, so a file that agrees with the barring lands on a copy trains may arrive at).  Where the file does not answer, REG3-C1's second option: write no facing and let `startableCopy` choose at build time, over the finished setup, with the first run's capture recording the truth - `placementCopy` already does exactly that for a missing facing (`:729`), and GUI3-C4 already ticks nothing in the Facing menu where nothing is recorded.  (The count `facingsInvented` and its log line then describe trains that were left to the build.)  Alternatively make the guess in a second pass after the whole points loop, or move the turn-flag block above the placement block; either still leaves the guess one step removed from the rule that places the train.  Whichever: `startableCopy`'s order, the plain copy first - the current membership test also takes a facing that only a turning copy holds on a may-reverse square with one side barred (the train is stood already turned round; startable, but not the "has not turned round yet" preference `placementCopy` states at `:746`).

### REG4-C2 - REG3-C2's replacement sentence is wrong in both directions for its reader: an edge of length 0 no longer unlocks at once, and a train of length 0 does

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933: REG3-C2's wording |
| **Where** | `AutomationAPI.md:451` (`031a7ddb`); `Layout.java:4900-4919` (`tailHasProvablyPassed`), `:8336-8345` (`pathIsUnmeasured`, asked of the whole path); `master:src/org/traincontrol/automation/Layout.java:3337-3344` (v2.8.1's rule) |

The new sentence:

    AutomationAPI.md:451
        ... A program driving `Layout` directly sets `atomicRoutes` itself, and with it off an edge of length 0 still
        unlocks at once.

What `Layout` does since VAL-A1 / WK-B1: an edge is handed back only when `tailHasProvablyPassed(pathIsUnmeasured, behind, trainLength)` is true, and that is `pathIsUnmeasured || behind >= trainLength` - where `pathIsUnmeasured` is "NO edge anywhere on this path has a length", computed once over the whole path (`:8336-8345`).  So:

- **An edge of length 0 on a path with any measured edge is held** - until measured track run since it covers the train, or the path ends - not unlocked at once: `behind` counts only measured distance run since that edge ended, and the javadoc says so - "on a path with lengths on SOME edges, an edge whose measured distance behind never reaches the train's length is held until the route ends" (`:4890-4891`).  The sentence's "still" is false too: at v2.8.1 the rule was `lengthTraversed >= loc.getTrainLength() || lengthTraversed == 0` (`master` `Layout.java:3344`), under which a zero-length edge did unlock at once - v3.0.0 changed exactly this.
- **Every edge unlocks at once when the TRAIN has no length**, measured track included: `trainLength` 0 or null makes `behind >= 0` true on the first ask.  The sentence says nothing of it, though it is half of the application's own gate ("or any train, has no length", the sentence before it) and the half REG3-C2's suggested wording carried ("an unmeasured path or a train with no length still unlocks each edge as soon as it is passed").

The page's reader is the one person with no gate between them and the second case.  **Verification request:** none needed for the text - `Layout.java:4900-4919` is the whole rule; a probe for the train half is REG3-C2's (a `Layout` built in code, edges measured, a locomotive with `trainLength` 0, `setAtomicRoutes(false)`, one path: the first edge is released as soon as the head passes the second point).  **Suggested fix:** "... with it off, a path on which no edge has a length, or a train with no length, still unlocks each edge as soon as the train has passed it; an unmeasured edge on a partly measured path is held until the path ends."

### REG4-C3 - the four-argument `moveLocomotive` left two descriptions of the three-argument rule behind: its own javadoc's "The same", and behaviour.md's "refuses in four cases"

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933: the javadoc says what the method does, behaviour.md section 4 names the put-back, and `testOnlyThePutBackAcceptsABarredCopy` reads the fourth argument |
| **Where** | `Layout.java:9003-9049` (three-argument form, `isABarredCopyOfAStation` inserted after it, then the four-argument form); `docs/reference/behaviour.md:498-500` |

- The four-argument form's javadoc opens "The same, and able to stand a train back on a copy of a station square ..." - but `isABarredCopyOfAStation` was inserted between the two overloads, so in the source "The same" follows a boolean query, and "`@param purge` as the three-argument form" is the only pointer back.  This is the public method AutomationAPI.md's readers get; "Moves a locomotive to a station, as `moveLocomotive(String, String, boolean)` does - and, when `evenOntoABarredCopy`, also onto ..." says it without depending on member order.  (Or move `isABarredCopyOfAStation` above the three-argument form.)
- `behaviour.md:498-500`: "Every door that puts a train down asks `Layout.moveLocomotive`, and that refuses in four cases: ... and the target is not a destination."  Since `4be3798a` the rebuild's put-back asks the four-argument form, which accepts a non-destination - a barred copy of a station - and behaviour.md records neither that exception nor its reason (TDY3-A1: the copy is the direction).  The source-shape guard `testEveryPlacementDoorUsesTheRailwaysAnswer` counts `moveLocomotive(` calls guarded by `if (!` and cannot see the fourth argument, so the commit message's "every placement door still refuses one" is held by nothing that would notice a door passing `true`.

**Verification request:** none; reading.

## D - not defects (checks that came back clean)

### REG4-D1 - REG3-C1 verified for the case its reviewer named, and the claim is red for the right reason

| | |
|---|---|
| **Disposition** | Closed - checked clean |

At BottomMainA (E barred, no turn flag) the new rule takes the facing of the first copy trains may arrive at: copies emitted plain-E (faces W, barred) then plain-W (faces E); `homeFacingsFor` = {E}; guess E; `placementCopy`'s first loop finds plain-W, facing E and allowed - a station.  The claim `testAnImportedFacingGuessCanStart` was added in `d65df6cb`, before the fix: its precondition asserts copy zero's facing is not in `mayArrive`, so at `d65df6cb` the import's `ways.get(0)` = W fails the carrying `assertTrue(mayArrive.contains(guessed))` (`:145`) for the stated reason, and `build.xml` runs it.  What it does not reach is REG4-C1 (it reads `mayArrive` before the import).  The log line's "The first one that fits was used" is now true in the sense of "a way trains may arrive", except in REG4-C1's case.  REG3's lead for other lanes (`homeCopy` refusing a barred home facing that `placementCopy` honours) is closed by GUI3-C2: `knownHomeFacing` returns a facing only if `homeFacingsFor` contains it (`AutonomySession.java:7479-7492`), and `writeHome` saves only that.

### REG4-D2 - what the fix changes for a 2.8.1 file, square by square

| | |
|---|---|
| **Disposition** | Closed - checked clean |

- **No side barred** (every fresh upgrade, and every square nobody has restricted): every copy's facing is in `homeFacingsFor`, so the guess is `ways.get(0)`, exactly as before `4be3798a`.  Whether that is the way 2.8.1 ran the train is REG4-A1.
- **A side barred, no turn flag:** the facing of the first copy trains may arrive at - the train is taken to have come in by an open side and to face onward; startable (D1).
- **A side barred, and the file's point is a terminus or reversing point the configuration does not yet mark:** REG4-C1.
- **A side barred on a square already marked may-reverse:** the first facing any allowed copy holds, which can be the turning copy's (the train stood already turned round); startable - noted under REG4-C1's fix.
- **A square nothing arrives at by a side** (unsplit): no facing written, as before.
- **The JSON path** (`Layout.fromJSON` - every Central Station layout, and an `autonomy.json` loaded at start where Load Autonomy was ticked by hand): no copies, no facing; nothing here is reached.
- **Adam's frozen file on his frozen setup:** unchanged by the fix - its three barred placement squares (TopMainR1Inter, TopMainR2Inter, Tunnel) each have an allowed copy zero (REG3's reading), so `ways.get(0)` is already in `homeFacingsFor`.

### REG4-D3 - `Layout.moveLocomotive(String, String, boolean)` behaves exactly as before

| | |
|---|---|
| **Disposition** | Closed - checked clean |

It is now `return moveLocomotive(locomotive, targetPoint, purge, false);`.  In the four-argument body the only changed line is the destination test, `!target.isDestination() && !(evenOntoABarredCopy && isABarredCopyOfAStation(target))`, which with `false` short-circuits to the old `!target.isDestination()` without calling the new method.  Both are `synchronized` on the Layout (re-entrant).  Every refusal, log line, default-speed rule, `clearBlockExcept`, `claimHome` and callback is the same code.  One caller passes `true`, `TrainControlUI.putTheTrainsBack` (`:6498`); every other call in `src/` and `test/` is the three-argument form.  For a Java user's `Layout` built from JSON or by hand, `isABarredCopyOfAStation` is always false - it needs `Point.isSamePlaceAs`, which needs a block, which only `AutonomyBuilder` emits - so even the four-argument form with `true` is the three-argument form there.

### REG4-D4 - `explainCannotStart`: what changed for callers outside the window

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`git grep "explainCannotStart("`: in `src/`, `AutoLocomotiveStatus.java:735`, `:897` (one-argument) and `AutonomyEditorPanel.java:7891` (two-argument) - all window code; in `test/`, `testWhyStuck`, `testTimetableCaptureThroughARealRun` and `testTheDiagramRefreshDoesNotWaitOnTheRailway` (they compare with `I18n` keys or print the sentence) and the round's claim.  The one-argument form's answers changed three ways: a barred copy of a station gets `autolayout.why.startFacingBarred` instead of `startNotStation`, and `startNotStation` / `startInactive` name `placeNameOf(at)` instead of `at.getName()`.  On a JSON-path layout the first cannot occur (no blocks, D3), and `placeNameOf` strips only a trailing " (northbound|southbound|eastbound|westbound[, ...])", which a 2.8.1 point name carries only if somebody typed it.  The two-argument form is new, so no existing caller changed meaning.  `AutomationAPI.md` does not mention `explainCannotStart`.

### REG4-D5 - public signatures in the range: three additions, nothing changed or removed

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`git diff 2f4448b6 9f5d8e23 -- src` adds `Layout.explainCannotStart(Locomotive, boolean)`, `Layout.isABarredCopyOfAStation(Point)` and `Layout.moveLocomotive(String, String, boolean, boolean)`; no `public`/`protected` line is removed or edited.  `ViewListener` and `View` are untouched (the one `MarklinControlStation` hunk is a comment).  `AutonomySession.facingOnTheRailway` is private; `knownHomeFacing`'s signature is unchanged and its narrower answer reaches only the editor's home door.

### REG4-D6 - REG3-C3 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

The banner over `AutomationAPI.md:223` is true: the JSON tab and its buttons are mounted only when `getAutonomySession()` is null (`TrainControlUI.java:3947-3969`) - a layout with no local path (from the Central Station, or none) - and Start is refused first thing there by `isRemoteLayout()` (`:5931`).  "Layouts -> Download Central Station Layout Files" is the real item (`ui.main.toolbar.downloadCSLayout`), and it sets `LAYOUT_OVERRIDE_PATH_PREF` to the downloaded folder (`:27204`), so after it the session exists and the autonomy menu's Import is there.  (A local path whose folder has gone missing also shows the tab without the remote refusal - a broken installation, not an upgrade path.)

### REG4-D7 - REG3-C4 verified, and no new joins in the range

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`git grep` for all twelve joined forms at `9f5d8e23`, outside the review documents, finds none.  The range's added lines in `.md` and `.java` were re-scanned the way REG3 did it - lower case or closing punctuation run straight into `.`/`:` and a capital, and new words that split into two words of the removed text: the only hits are Java identifiers and "whatever" / "anywhere".

### REG4-D8 - REG3-C5 verified

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`whyAutonomyWillNotStart`'s javadoc says "Five answers" and lists the Central Station one first (`TrainControlUI.java:24572-24580`), matching the body at `:24589`; the menu comment says "FOUR reasons" and names the fourth; `importRoutes`' comment says the file's `auto` is overwritten in `parseRoutesFromJson` before anything reads it, and that REG2-C7's report would have to read it there.  The optional half (the greyed Start's tooltip names no remedy) was not taken; `autosetup.ui.menuNoSetupPossibleDownload` exists beside it for whoever does.

### REG4-D9 - REG3-C2's application half is true

| | |
|---|---|
| **Disposition** | Closed - checked clean |

"In the application, since v3.0.0, Atomic Routes stays on while any edge autonomy runs over, or any train, has no length": `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack` (`TrainControlUI.java:6129-6167`) is asked at the two parse doors and the five dispatch doors (its seven callers: `:23202`, `:24371`, `:24701`, `:25644`, `AutoLocomotiveStatus.java:1159`, `AutonomyViewerPanel.java:822`, `LayoutRightclickAutonomyMenu.java:1377`) and asks both `unmeasuredTrackAutonomyRunsOver` and `trainsWithoutALength`.  The sentence after it is REG4-C2.

### REG4-D10 - the round's other user-facing sentences

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`Readme.md:128` ("Set up on the track diagram ... - an older JSON configuration file can be imported from the autonomy menu - and enable complete automation ...") and the three changelog lines `031a7ddb` re-spaced (`:374`, `:379`, `:380`) are true as they stand; REG3-D3 to D6 still hold.  The changelog has no line saying an imported train's facing is guessed.  Not raised: Adam wants a non-technical changelog, and the import's log line carries it - REG4-A1's fix, if taken, is what would deserve a line.

### REG4-D11 - the bundles

| | |
|---|---|
| **Disposition** | Closed - checked clean |

The two new keys (`autolayout.why.startFacingBarred`, `autolayout.whyHomeStartFacingBarred`) are in all eight bundles; every bundle is pure ASCII at `9f5d8e23` (0 bytes above 127, counted per file); no ASCII apostrophe was added for `MessageFormat` to eat (French and Italian escape U+2019, the typographic apostrophe); and `{1}` is filled with `autosetup.ui.menuArrivalsGroup`, the menu's own label in each language.  GUI3-C3's Spanish corrections change strings no 2.8.1 user had in that form.

### REG4-D12 - the catalogue and the tracker

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`findings.tsv` has 19 REG3 rows (C1-C5, D1-D14), each disposition as the document states it; 3,991 rows carry a document, which with the 45 cited-only references is the 4,036 rendered.  OB-284 is filed with the question stated as Adam's.  MT-487's expected message matches `route.infoImportedArriveDisarmed` and names the two real controls (`route.ui.menuEnableAutoExecution`, `ui.main.bulkEnable`).

## What this pass did not cover

- **Execution.**  Nothing run.  REG4-A1 and REG4-C1 each have a probe that settles them; A1's rests on reading the frozen files and the blessed build, and its one diverging square (TopMainR1) on the reduction a fresh diagram makes under its default directions, which I read from `test/baseline` and the tile rows rather than derived for the live snapshot.
- **The other three placements' geometry** (TopMainR1Inter, TopMainR2Inter, Tunnel) was read from tile types and rotations on the page file, not from a build; the probe in A1 checks all four.
- **A lead for the GUI lane, not judged here:** GUI3-C1 gave `explainCannotStart` a by-hand tier and the editor's Why not Moving? uses it, but the locomotive list's "No available paths" tooltip and report (`AutoLocomotiveStatus.java:735`, `:897`) still ask the one-argument (autonomy) form while listing the paths a person may send by hand (`getPossiblePaths(locomotive, true)`, `:193`) - and `explainDestinations(loc)` beside them is the autonomy tier too, so this predates the range (MT-434 changed only the editor).  In manual mode, a train on a barred copy with every hand destination refused for other reasons is told autonomy's reason instead of those.
- **`Automation.md` still says nothing about importing an older `autonomy.json`** (REG3's note); with REG4-A1 in mind, the user guide is where "check which way each imported train faces before starting autonomy" would be read.
- **The round's TDY/AUT/GUI fixes on their own terms** (`flipFacing` reading the railway, `putTheTrainsBack`'s put-back, the Facing menu's tick, the translations of the two new sentences) - other validators' lanes; read here only for what a 2.8.1 user meets and for the public API.
- **The legacy import's edge directions.**  REG4-A1 is about the facing only.  The import also carries no one-way information from the file's edges onto the diagram (88 of Adam's 90 edges are one-way; `importLegacy` skips `edges`), so a fresh upgrade runs on the diagram's defaults - plain track both ways, switches out of the toe - until the operator sets directions, and `whatALegacyImportLeaves`, the list Adam asked for of what the import drops (MT-257), does not name direction.  Whether the import should translate them, or at least list them, is a design question for the lane that owns the import; not graded here.
