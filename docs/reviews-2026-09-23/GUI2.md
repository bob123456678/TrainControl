# User interface round 1 fixes - validation

**Status:** open

**Open at close:** GUI2-C4 - carried in the finding store.

**Prefix:** `GUI2`

**Reviewed:** branch `autonomy-diagram-r0` at `08a47bdd`, 2026-09-23.  Baseline: the GUI lane's review at `281c79de` (`docs/reviews-2026-09-23/GUI.md`); range `281c79de..08a47bdd` (33 commits).

**Method:** Read every fix commit named in GUI.md's dispositions (`0be2bbe4`, `919e0dc8`, `b4b061e0`, `1c855483`, `55959c9c`, `59fdb67d`, `fd6341dd`, `ff129a4d`, `e2223851`) and every claim commit (`5d871f6d`, `b6d08239`, `2e565b5e`, `41589728`, `d64023cb`) against the code around them at HEAD and, for the claims, against the pre-fix code.  Walked `Layout.unlockPath` for an atomic run, a non-atomic run, a run switched false-to-true and true-to-false mid-way, and the failure handler's unlock.  Traced every protocol source for GUI-C5 (`CS2File` layout and both route parsers, `CommandRow`, `RouteCommand.getProtocol`, the editor's address popup).  Traced every door that reaches `ArrivalSidePrompt`, `FacingPrompt`, `copyFacing`, `placeableFacingsFor`, `moveOntoFacingCopy`, `facingChoices` and `placementCopy`.  Read-only Python under `scratchpad/review-2026-09-23/gui2-work/`: `c1.py` (every key `ff129a4d` changed, all seven bundles: placeholders, ASCII apostrophes against MessageFormat arguments, non-ASCII bytes, still-English; prints one language's values for reading), `terms.py` (anchor terms across bundles), `translit.py` (values since v2.8.1 that spell accented letters in ASCII).  Read the frozen railway's `setup.json` and `configuration-Main.json` (`test/layouts/live-snapshot`) for barred sides and placements.  Nothing was run that starts Java; every finding below rests on reading and says what execution would settle it.

## A - high

### GUI2-A1 - since GUI-B1, turning a train to face the way only a barred copy faces is written to the setup but no longer followed on the railway, so autonomy can start the train in the direction opposite to its decoder

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 (claims 8346be65, red first), with TDY2-A1 |
| **Where** | `AutonomySession.java:1602-1620` (`moveOntoFacingCopy`, now `copyFacing`), `:1803-1900` (`flipFacing`), `:7150-7166` (`setFacingAndMove`), `:6124` (`facingChoices`); `AutonomyBuilder.java:757-768` (`placementCopy`'s new fallback); callers `TrainControlUI.java:12647` (`followDirectionChanges`), `AutonomyEditorPanel.java:3782` (the Facing menu) |

**What the fix changed.**  `1c855483` made `moveOntoFacingCopy` stand the train only on a copy `placeableFacingsFor` lists (a destination), and made `placementCopy` fall back, when no allowed copy faces the recorded way, to an allowed copy facing the OTHER way ("the facing is one no train can stand in").  Before it, both took the first copy facing the recorded way, barred or not.  Neither writer of the facing was changed: `setFacingAndMove` still calls `setFacing(tile, facing)` first and unconditionally, and `flipFacing` still picks `now` from `facingChoices(tile)`, which is `facingsFor(tile).values()` - every copy the build emitted, barred ones included.

**Why a direction is not just a record here.**  `flipFacing` exists because the decoder is the truth: *"The decoder is what the railway actually does; the setup is a record of it ... this follows the track"* (its javadoc, and Adam's ruling DIR-B3 that it must move the train on the running layout too).  `Layout.executePath` never sets a locomotive's direction - the only `switchDirection` calls are the reversals at `Layout.java:8402` and `:8772` - so which copy a train stands on is the only thing that tells autonomy which way the train will move when it is given speed.

**Failure scenario, on Adam's railway as frozen.**  `75 407 DB` stands on BottomMainA (`1 - Main:20,12`, facing E, `setup.json` bars arrivals from the east there).  Its copies are "BottomMainA (eastbound)" (a station) and "BottomMainA (westbound)" (arrived from the east, faces W, `station: false`).  With autonomy idle, the operator reverses the locomotive from its panel or the Central Station:

1. `followDirectionChanges` -> `flipFacing(name, running)`: `choices = facingChoices(tile)` = [E, W], recorded E, so `now = W`; `setFacing(tile, W)`.
2. `moveOntoFacingCopy(running, name, tile, W)` -> `copyFacing(tile, W, running)` iterates `placeableFacingsFor`, which holds only the eastbound copy, and returns null; `if (onto == null || train == null) return;` - the train stays on the eastbound copy.
3. `flipFacing` still returns the tile, so the log says `autosetup.infoFacingFollowedDirection` and `autonomySetupChanged()` runs.  A rebuild puts the train back on the eastbound copy by name, or `placementCopy(W)` - whose first two loops now skip the barred copy - falls through to the eastbound one.  The next editor open's `captureFromLayout` writes E back over the W, so even the record disappears.
4. Autonomy is started.  The train stands on a station copy facing east, so it is dispatched on an eastbound path, which is locked and whose turnouts are thrown - and its decoder, reversed in step 1, drives it west, onto track nothing reserved for it.

Before `1c855483` step 2 moved it onto "BottomMainA (westbound)": the model then agreed with the decoder, and `explainCannotStart` refused to start a train standing on a non-station.  So the fix exchanged "stranded, and told why" for "started the wrong way, silently" on exactly this configuration.  The Facing menu (`setFacingAndMove`) is the same with a person asserting the heading instead of a decoder: it offers W (`buildFacingMenu` lists `facingChoices`), records W, does not move the train, and reopens with E ticked because it reads the railway first (OB-181).  `facingsThatCannotBeHeld` asks `facingChoices` too, so no finding reports the disagreement.

**Reach.**  Any split square with exactly one arrival side barred and no turning, which is where only a barred copy holds the second heading.  `setup.json` bars a side at seven squares; three of the four trains placed in `configuration-Main.json` stand on one of them (BottomMainA; `0,11` with `2-8-4 3505 SP`; `13,9` with `EN57-947`).  May-turn squares such as BottomMainPost are not affected: both headings have an allowed copy there, and the fix is right for them (the GUI-B1 idle-drain case).  A terminus already has this shape (`flipFacing` declines at one facing, by design); this extends it to squares where the model used to follow.

**Why nothing compensates.**  Nothing between a hand reversal and Start compares the decoder with the copy; `lastSeenDirection` records only that a change happened, and mid-run direction commands are ignored on purpose (MON-C15), so the run that follows cannot correct it either.

**Verification request (needs execution; core test, no window).**  Model on `core/testATrainIsPutOnlyWhereItCanStart` (live snapshot, a session with a running layout).  Stand the probe train on "BottomMainA (eastbound)", `session.placeLocomotive(mainA, PROBE)`, `session.setFacing(mainA, Side.E)`, `session.setRunningLayoutSource(() -> running)`, then call `session.flipFacing(PROBE, running)`.  **Proves it:** `flipFacing` returns non-null and `session.getFacing(mainA) == W` while the probe still stands on "BottomMainA (eastbound)".  **Refutes it:** the train is moved onto a W-facing copy, or the facing is left at E and the call returns null.  Repeat with `setFacingAndMove(mainA, W)` for the menu door.  Against `1c855483^` the same probe ends on "BottomMainA (westbound)", which is the behaviour this finding says was traded away.

**For Adam - a decision, not a patch.**  When a train really is turned to face the way only a barred copy faces, the choices are: (a) stand it on the barred copy, where autonomy will not start it and says why (the behaviour before `1c855483`); (b) refuse the facing, which a decoder command cannot be refused, so for `flipFacing` it would mean logging that the railway and the model now disagree; (c) today's: record it, leave the train, report "followed".  Whichever is chosen, the Facing menu should offer only what the door will then do (Guard and affordance ask one question), and `placementCopy`'s turn-it-round fallback should be reconsidered with it, since at a load it performs the same silent reversal on a facing a real turn wrote.

## B - medium

### GUI2-B1 - `placementCopy` still returns copy 0 when the setup has no facing, so a train placed without one is stood on a barred copy - GUI-B1's consequence through the branch the fix did not reach

| | |
|---|---|
| **Disposition** | Fixed - 8370abb1 (claim 8346be65, red first): with no facing recorded the build asks startableCopy |
| **Where** | `AutonomyBuilder.java:727-733` (the two early `return 0`), against `homeCopy` at `:680-714`; reached from `AutonomyEditorPanel.java:5437-5441` and `GraphLocAssign.java:282-287` when `facingAfterAPaste` answers null |

GUI-B1's disposition says the build's `placementCopy` "asks `arrivalAllowed` as `homeCopy` does".  It asks it only when the setup holds a parseable facing:

```java
private int placementCopy(List<Node> nodes, JSONObject extras)
{
    if (extras == null || !extras.has(FACING)) return 0;
    TilePorts.Side facing = side(extras.optString(FACING, null));
    if (facing == null) return 0;
    ... four loops, each asking arrivalAllowed ...
```

`homeCopy`, its model, falls through to "the plain allowed copy, then any allowed copy" when no facing is given; `placementCopy` returns copy 0 before reaching those loops.  Copies are emitted by arrival side in `N, E, S, W` order (`splitSides` is a `TreeSet` of `TilePorts.Side`), so copy 0 is the barred one wherever the barred side sorts first: BottomMainA (arrival sides E and W, E barred -> copy 0 is "BottomMainA (westbound)", `station: false`) and BottomMainPost (N barred -> copy 0 is the N-plain copy, `station: false`).

**A setup with a placement and no facing is ordinary.**  `facingAfterAPaste` returns null for a square with several placeable copies when the train has no heading to keep - the doc comment says "an absent facing at least makes the menu ask".  So: the editor's Place/Add to Autonomy on BottomMainPost for a locomotive not yet placed anywhere (`facingOf(name)` null, `placeableFacingsFor` = {S-plain: N, S-turning: S}) writes `loc` and a null facing, `placementChanged` names the train so `putTheTrainsBack` lets the build's copy stand, and the build stands it on the N-plain copy - on the railway, drawn at the station, and never started by autonomy (`autolayout.why.startNotStation`).  The Place Locomotive dialog does the same one load later.  Setups saved before facings were recorded, and a hand-edited `configuration-*.json`, reach it at every load.

**Verification request (needs execution; core test).**  Add to `core/testATrainIsPutOnlyWhereItCanStart`: `session.placeLocomotive(post, PROBE); session.setFacing(post, null);` then build and find the Point the probe stands on.  **Proves it:** `isDestination()` is false.  **Refutes it:** it is a destination.  The same with BottomMainA.

**Suggested fix.**  Drop the two early returns into the existing fallback loops (the same four `homeCopy` has), so "no facing" chooses an allowed plain copy.  That is a guess either way - nothing says which way an unrecorded train faces - but it is the one guess autonomy can start.

## C - low

### GUI2-C1 - three comments in `Layout` still say `unlockPath` reads `clearedEdges`, and `setAtomicRoutes`'s new javadoc claims more than the unlock does

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 |
| **Where** | `Layout.java:7886-7893`, `:7979-7981`, `:8490-8492`; `Layout.java:10555-10570` (`setAtomicRoutes` javadoc) |

Since `919e0dc8` both roads of `unlockPath` read `releasedEarly`, but the failure handler's paragraph still says *"`unlockPath`'s non-atomic branch reads that map [clearedEdges] to know which edges the tail already gave up"* and *"`unlockPath` consults `clearedEdges` for the edges the tail gave up early, so it has to run while that map still has them"*, and the milestone loop's VD10-C15 paragraph says the non-atomic branch is *"the one that reads this map"* (`clearedEdges`).  The ordering they defend is still right - `releasedEarly` is removed after the unlock too - but a reader following them to find what the unlock trusts is sent to the set the second pass says it must NOT trust.

The setter's javadoc now says *"true-to-false starts them, and the same record covers them.  So neither direction gives an edge back twice or leaves one held"*.  The second half is not so for true-to-false: an edge the tail cleared while the run was atomic is dropped from `waitingToClear` unreleased; after the switch, releasing the next edge early empties that edge's end Point (`getStart().setLocomotive(null)` of the next edge); a second train may reserve that Point through another edge into it; `unlockPath` then takes the careful road, finds the end held by another train, and leaves the edge's own claim in place for the session - the SVN-C17 cost the old javadoc described.  Unreachable today (the checkbox refuses while `isAutoLayoutRunning()`, and the only other writer writes `true`), so a sentence, not a behaviour.  Reading only.

### GUI2-C2 - nothing pins the write GUI-A1's repair depends on: every claim seeds `releasedEarly` by reflection

| | |
|---|---|
| **Disposition** | Fixed - a9d5a2f0: a real non-atomic path, the first edge claimed by another train at the route's end; removing the line that records an early release now fails it (round 4's mutation run - it survived every earlier claim) |
| **Where** | `Layout.java:8549-8556` (`released.add(givenBack)`); `test/core/testAnUnlockGivesBackOnlyWhatItHolds.java:101`, `test/core/testAutoLayout.java:2335`, `test/core/testAutonomyPathValidation.java:769` |

`unlockPath` now trusts `releasedEarly` completely: an edge not in it is released again.  The three claims that exercise that trust all put the edge into `releasedEarly` themselves, through `getDeclaredField("releasedEarly")`; the one real-path claim in the area (`testTrainTailClearsEdges.testAnEdgeTheRuleRefusesToClearStaysHeldWhileARealPathRuns`) runs atomic, where nothing is recorded; the source-shape claim there counts `.add(path.get(waiting[0]))`, which the new line (`released.add(givenBack)`) does not match.  So deleting the recording line would, as far as reading can tell, leave the suite green while every non-atomic run's early releases were given back a second time at its end - RC-A9/OB-164's hazard, the claim-under-a-train decrement.  The GUI lane's own verification request for GUI-A1 (dispatch a real non-atomic run in simulate mode, let the tail give an edge back, claim it for a second train, finish) is the claim that would pin it.

**Verification request (needs execution; a mutation run).**  Comment out `if (released != null) released.add(givenBack);` and run `core.testAnUnlockGivesBackOnlyWhatItHolds`, `core.testAutoLayout`, `core.testAutonomyPathValidation`, `core.testTrainTailClearsEdges`, `regression.testARouteDoesNotThrowSwitchesUnderATrain`.  **Proves it:** all green.  **Refutes it:** one fails, naming the double release.

### GUI2-C3 - about 160 translated values added since v2.8.1 spell their accented letters in ASCII, and the round-1 tooltip edit made two of them mix both spellings in one sentence

| | |
|---|---|
| **Disposition** | Fixed - 91daff27: 506 values, only diacritics changed (checked by mapping each back to ASCII) |
| **Where** | `messages_{de,da,it,es,fr}.properties`; in range, `4fb36b4b`'s `autolayout.ui.tooltip.pathPreference{SHORTEST,LONGEST}_LENGTH` in `de` and `it` |

`gui2-work/translit.py` finds, among values added or changed since `master`, 46 German (`laesst`, `geloescht`, `Laenge`, `fuer`, `Fahrstrasse`, `Rueckmelder`...), 40 Danish (`paa`, `saa`), 29 Italian (`puo`, `piu`, `cosi`), 27 Spanish (`estacion`, `tambien`, `configuracion`) and 22 French (`etre`, `arrete`, `donnees`, `itineraire(s)`, `supprimees`) - words a native speaker would not write without the accent, in bundles whose other values carry `\uXXXX` escapes.  None of them existed at v2.8.1, so this is GUI-C1's shape (translations that look done and are not quite) by a route GUI-C1's scripts did not look for; it is not a restatement of it.  In the review range, `4fb36b4b` rewrote the second sentence of the SHORTEST/LONGEST tooltips with escapes and left the first: German now reads *"Die Route ueber die laengste Strecke.  Ein Abschnitt ohne Länge ..."* and Italian *"Il percorso sul binario piu lungo.  ... è il percorso con più tratti."*  The `ff129a4d` translations themselves are clean (see GUI2-D6).  Cosmetic.  **Verification request (no execution):** `python gui2-work/translit.py de` (or `da`, `it`, `es`, `fr`) lists the keys; the patterns are word lists, so a few hits may be words that are legitimately unaccented - read before bulk-fixing.

### GUI2-C4 - the three-way clause makes Highlight on Diagram wash one three-way in both colours when a route commands one of its decoders and checks the other

| | |
|---|---|
| **Disposition** | Open - for the next round: cosmetic, rare |
| **Where** | `RouteEditorFrame.java:3039-3054` (commanded-over-checked, per address and protocol) against `LayoutDiagramComponent.java:1237-1247` (matched per tile) |

The editor's rule is *"A square that is BOTH commanded and checked is drawn as commanded ... two washes on one tile is a colour neither of them chose"*, and it is enforced by removing from `checked` every (address, protocol) that `commanded` holds.  Since GUI-C5 a three-way at 10 answers to 10 and to 11, so a route that commands accessory 11 and has a condition on accessory 10 (or the reverse) keeps both - `highlightAccessories(commanded, HIGHLIGHT)` and `highlightAccessories(checked, HIGHLIGHT_CONDITION)` both light the same tile, and `lit` counts it twice.  Rare (a CS2 route setting the second road usually commands both decoders, and then the dedupe holds), and cosmetic.  Reading only.  **Suggested fix:** dedupe by tile inside `lightWhere` (commanded first), or fold a three-way's two addresses together before the removal.

### GUI2-C5 - small

| | |
|---|---|
| **Disposition** | Fixed - 4132d260 (the opener); GUI-C1's disposition corrected |
| **Where** | `PositionAwareJFrame.java:198`; `docs/reviews-2026-09-23/GUI.md` GUI-C1 row |

- `fd6341dd` removed `hasRememberedBounds` and left the next javadoc's `/**` at column 0 (the rest of the file indents it four).  Harmless.
- GUI-C1's disposition says "the 53 keys ... including the three reworded ones"; `ff129a4d` changed 52 keys and its own message says "two whose translation said what the English used to".  The third, `ui.main.validateConfigOpenGraphUI` in French, already reads "Valider la configuration", which is right - so nothing is missing, but the disposition should say two and why the third needed nothing.

## D - not defects

### GUI2-D1 - GUI-A1: verified, in every case the task named

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `Layout.java:4060-4200` (`unlockPath`), `:8216`, `:8514-8556`, `:7995-8036`, `:8851-8856`; `TrainControlUI.java:23148-23195` |

- **Atomic run, setting unchanged:** the milestone loop adds to `clearedEdges` and `continue`s before the release, so `releasedEarly` stays empty, `heldItAll` is true and the atomic road runs exactly as before GUI-A1.  `testAnAtomicRunGivesBackEverythingItHeld` is red against `0be2bbe4` (which chose by `clearedEdges`: the careful road skipped `first`, occupancy stays 1) and green here - the assertion `occupancy(first) == 0` carries it.
- **Non-atomic run:** `clearedEdges.add` and `releasedEarly.add` happen in the same iteration (the latter after the release, inside `synchronized (activeLocomotives)`), so the careful road sees the same set it used to; `atomicRoutes` false sends it there regardless of `heldItAll`.
- **False-to-true mid-run** (the gate's write): releases stop; the careful road skips the released edges, gives back the rest, and never touches a Point another train holds.  The end Point of the last early-released edge is never emptied by that release (*"the far end is not cleared"*), so no later edge's start can have been taken by another train.  `testAnEdgeGivenBackEarlyIsNotGivenBackAgain` is red against `5d871f6d`'s parent (atomic road: `first` to 0, U2 emptied) for the reason it names.
- **True-to-false mid-run:** unreachable (the checkbox refuses while `isAutoLayoutRunning()`, `parseAuto` writes only a new `Layout`, the gate writes only `true`); where it would matter, see GUI2-C1's second paragraph.
- **A run that failed part-way:** the handler reads `hadItsPath`, calls `unlockPath` with `releasedEarly` still populated, and removes both maps after it, as the ordinary ending does.  `locDeleted` removes both (a deleted-while-running locomotive would lose the record, as it always lost `clearedEdges` - pre-existing and refused elsewhere by MT-141).
- **A throw between release and record:** the record is now written after `setUnoccupied()` and `getStart().setLocomotive(null)`, so a throw between them would leave a released edge unrecorded; `setLocomotive(null)` skips the sweep and `refreshProtectingSignal` catches everything, so nothing there throws.
- **Execute Timetable:** the gate now runs after the power, busy and empty-timetable refusals (claim `testExecuteTimetableAsksTheGateAfterItsRefusals`, a source-order check).  Two refusals still come after it - declining the conditional-route warning and "locomotive must be moved to start" - but both are reached only when `isAutonomyBusy()` is false, where switching to atomic is what Start would do anyway.  The commands-panel hand dispatch still asks the gate while a hand-dispatched train runs; with the unlock repaired that is safe.
- RC-A9's claim (`b4b061e0`) seeds `releasedEarly` alongside `clearedEdges`, which is the product's new contract, not a weakened claim.

### GUI2-D2 - GUI-C5: every protocol a route command can carry is the enum a tile carries, and both read "missing" as MM2

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `LayoutDiagramComponent.java:1237-1247`; `Accessory.java:14-16, 455-480`; `RouteCommand.java:86-92, 323-325, 520-521, 951`; `CS2File.java:812-817, 996-1018, 1429-1436, 2682-2702`; `CommandRow.java:523`; `LayoutEditor.java:4014` |

Both sides are `Accessory.accessoryDecoderType` (`MM2`, `DCC`).  A route command's `getProtocol()` is `determineAccessoryDecoderType(config.get(PROTOCOL))`, which never returns null: no key, or an unknown word, is `DEFAULT_IMPLICIT_PROTOCOL` = MM2.  Every builder of a route command passes a non-null protocol (`RouteCommandAccessory` dereferences it).  A tile's protocol is never null in practice - `CS2File` starts from MM2 and overrides from the magnetartikel map or a local `.prot=`, the editor's new tile is MM2 and the address popup sets one - and `answersToAccessoryAddress` reads a null as MM2 anyway.  The CS2 route parser takes a three-way's second decoder's protocol from `addressMap.get(id)`, the same entry the layout parser gave the tile, so the second decoder matches.  One limit, pre-existing and consistent on both sides: `addressMap` is keyed by address alone (merge keeps the first), so on a Central Station layout an MM2 5 and a DCC 5 in the same magnetartikel file read as one protocol for tiles and routes alike - the highlight then agrees with what TrainControl itself believes.  `highlightAddresses(..., ACCESSORY)` is now called only from a test; the route editor uses `highlightAccessories`.  Coverage note: the claims pin `answersToAccessoryAddress` and `highlightAccessories`, not `RouteEditorFrame`'s `byDecoder` call - reverting the editor to `highlightAddresses(commanded.keySet(), ACCESSORY)` would light both numbered-alike decoders again and no claim would notice.

### GUI2-D3 - GUI-C7: both questions, and the third door, name the square by its base name

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `ArrivalSidePrompt.java:98-101, 328, 352`; `TrainControlUI.java:7228-7229`; `LayoutRightclickAutonomyMenu.java:1156` |

`ArrivalSidePrompt.squareNameFor` is used by `ask`, which every door reaches (`forPlacement` from the paste and from `LayoutRightclickAutonomyMenu`'s placement; `GraphLocAssign` uses the combo, which names no square).  It uses the static `StationIndex.withoutArrivalSuffix`; the facing question uses `session.baseNameOf`, which asks the index first and falls back to the same static.  They differ only on a de-duplicated copy name ("Main (eastbound) (2)", produced when another Point was hand-named with a heading) - which `AutonomySession` now refuses at naming (`endsWithAnArrivalHeading`), so only a legacy name reaches it.  `FacingPrompt.forHome` and the tail question already named the square the operator's way.

### GUI2-D4 - GUI-C8: opening the autonomy editor with another editor open is safe in every state

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `TrainControlUI.java:8594-8601, 8709-8717` against `openLayoutEditor` `:5076-5145` |

`openAutonomyEditor(null)` goes to `openLayoutEditor`, whose order is: local layout, session, busy, then `!editLayoutButton.isEnabled()` -> `showOpenEditor()` (visible, front, focus) and return.  Nothing is written before that return - `captureRunningLayout()`, the page selection, the preference and the new `LayoutEditor` all come after it - so a track editor with unsaved edits, on any page, in either mode, is only brought forward, never switched or rebuilt.  `whyAutonomyEditorCannotOpen` (the label's first test) answers "already open" only when the button is grey and no editor is displayable, which keeps the label silent in the one state where `openLayoutEditor` would show a dialog.  No other door that opens an editor still asks `refuseWhileEditorOpen` (the eleven remaining callers load, switch, start or edit routes).

### GUI2-D5 - GUI-B2, GUI-B3, the B1 doors other than GUI2-A1/B1, GUI-C2, C3, C4, C6: verified

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | as below |

- **GUI-B2** (`AutonomyEditorPanel.java:7007-7018`): an unmeasured, unanswered sample opens empty, and an untouched OK goes to `applyLengthAnswer(tile, null)`; the claim's first assertion is red against the extracted `41589728` ("0").  Pre-existing and unchanged in kind: with a selection the prefill is the FIRST square's, so an untouched OK over a mixed selection applies that square's answer to all of them (it used to answer 0 to every one; now it clears every one).  One door only (`applyLength`, reached by the menu and Control+E).
- **GUI-B3** (`GraphLocAssign.java:225-246`): `edit.p` is replaced by `copyFacing(square, intended)` before `commitChanges`, and the facing is recorded over the same `placeableFacingsFor`, so the copy and the record agree; the tail question after the commit is asked of the new copy.  The dialog's arrival-side combo was built for the copy the menu was opened on, but its sides come from the square (`sidesFromTheBuild`/`choicesFor`), as in the paste.  Claim `testTheDialogPutsTheTrainOnTheCopyItRecords` compares `getFacing(mainA)` with the copy's facing - the right variable.
- **GUI-B1's paste, Place and dialog doors** now share `placeableFacingsFor`; the idle drain (`faceTheWayItCameIn`) at BottomMainPost is fixed by `copyFacing` preferring the allowed turning copy.  The remaining shapes are GUI2-A1 and GUI2-B1.
- **GUI-C2**: `unshaded` clears the tooltip, and only the joiner and "+" rows reach it.
- **GUI-C3**: heading, hint and pick prompt reworded in all eight bundles, `{0}` kept, no ASCII apostrophe.
- **GUI-C4**: the seven comments match `applyCaptionMode`/`textLabelsChanged`/`cycleCaptionMode` as they now are.
- **GUI-C6**: no `{1}` in any `infoGraphExported`; `hasRememberedBounds` has no reference left in `src/` or `test/`; the catch comment describes what both callers do.

### GUI2-D6 - GUI-C1: the 52 changed keys are clean in all seven bundles, and use each bundle's own words

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `ff129a4d`; `gui2-work/c1.py`, `terms.py` |

Mechanically (all 52 keys x 7 languages): every `{n}` set matches the English; no ASCII apostrophe in a value with MessageFormat arguments (French, Italian and Dutch use U+2019); no doubled apostrophe where there are no arguments; every file is pure ASCII with `\uXXXX` escapes; none is still English; the only key added since v2.8.1 that is identical in all eight is `ui.main.toolbar.dataSourceCentralStation`, deliberately.  Read in full in German, French, Spanish, Italian, Dutch and Danish, and Polish for sense - all 52 in each, well past ten.  Terms follow the neighbours: the quoted Return Home name in `confirmExcludingHome` is each bundle's own `ui.main.returnHome` label in all seven; "home station" is each bundle's usual word (Heimatbahnhof, gare d'attache, estación base, stazione di appartenenza, thuisstation, stacja macierzysta; Danish "hjemstation", where the bundle already mixes it with "hjemmestation"); "track diagram" takes each bundle's majority term (Polish "schemat torów" 26 against "plan torów" 13, Italian "schema dei binari" 34 against "tracciato" 10, Spanish "diagrama de vías" 23 against "plano" 8).  Register follows the bundle (Italian and Polish informal, German and French formal; Spanish is mixed already).  The sidebar's "Autonomy Setup" shortened to the one word in each language is the commit's stated choice for a 150-pixel sidebar.

## What this pass did not cover

- **Execution.**  GUI2-A1, B1 and C2 rest on reading until their probes are run; A1's premise (dispatch never sets the direction) was checked by grep over `Layout.java`, not by driving a train.
- **The other lanes' fixes in the range** (REG-B2/B3/C3, TDY-*, AUT-*, DCN-*, the test data-folder isolation in `Util`) were read only where they touch the interface (the start-up JSON gate, the import notice and its eight messages, `canStartAutonomy`, `preferencesFor`/`dataPath` call sites); their own validators own them.  One observation passed on: `Util.LOC_ICON_FOLDER` is not routed through `dataPath`, so a test that crops an icon writes the real `tc_loc_icons` folder.
- **Dutch and Polish** in GUI2-C3: the transliteration word lists for those two found nothing, but the lists were not built for them.
- **Anything needing a window**: no dialog was opened, and the double wash in GUI2-C4 was not seen.
