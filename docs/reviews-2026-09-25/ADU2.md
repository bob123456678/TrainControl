# UI validation - Adam's answers of 2026-09-24, round 2

**Status:** closed

**Prefix:** `ADU2`

**Reviewed:** branch `autonomy-diagram-r0` at `117298de`, 2026-09-25.  Range `d05a6356..117298de`: `000c81c8` (the 24 September round closed), `2c7a5b47` (claims), `1098c9b8` (fixes), `b6835920` (pins), `117298de` (records).  Lane: `src/org/traincontrol/gui`, the eight message bundles, and the claims, tests and manual tests that speak for them.

**Method:** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first, then round 1's `docs/reviews-2026-09-25/ADU.md` with its dispositions, and TDU-C6 and TDA-C9 as they stood at `d05a6356` (with the TDU-C6 question as put to Adam, `45cfa410:docs/reference/open-questions.md`).  I read `git show` of every commit in the range in full for the gui files, the bundles and the tests, and the automationui/automation hunks wherever they reach what the operator sees (`AutonomySession.departableFacingsFor`, `copyFacing`, `facingAfterAPaste`, `facingOf`, `piecesToMeasure`, `refusingFigures`/`measuredWayIn`, `runInFigures`; `Layout.moveLocomotive`, `measuredRouteIn`, `measuredRoomAtTheEndOf`, `whyTooLongForThisRoute`, `askedFor`/`askedForOnTheWayRound`, `walkOneTail`, `entrySideOf`, `whyABerthCannotHoldIt`; `ArrivalSidePrompt`, `StationIndex.speakerAt`, `HomeStaging.whyNotHome`).  At HEAD I read the right-click menu's constructor and every Place and Return Home method, `HomeLocomotiveMenu` whole, the window's Start/hand-send/Return Home rules, the paste door (`TrainControlUI` 7060-7260) and the dialog's placement lines, and the diffs of `behaviour.md`, `open-questions.md`, `Automation.md`, `issues.md` and `tests.md` (MT-559, MT-571, MT-580 to MT-584).  I grepped `test/` for every method whose signature or text the round changed (`addReturnHomeItem`, `copyToPlaceOn`, `placeSomewhereLegal`, `moveLocomotive(locName`, `whyAHandSendIsRefused`) and read each hit.  With read-only Python fed on stdin I checked the eight bundles (key sets, ASCII, placeholder sets, the changed key), recounted orphaned javadocs the way `testJavadocsAreAttached` counts them, and read `barredArrivals`, `blockedPoints` and `tileLengths` from the frozen railway's `setup.json`.

**What was not done.** Nothing was run, compiled or started; no git state was changed; no file was written but this one; `cs2_sample_layout/` was not opened.  The mutation results the round-1 dispositions quote (V1-V13) were not re-run - I checked by reading whether each named mutation reaches an assertion.  Every finding that depends on behaviour carries a verification request.

## A - high

None found.

## B - medium

None found.

## C - low

### ADU2-C1 - The round's signature change broke a test it did not sweep: `testTheReturnHomeMenuDoesNotWaitOnTheRailway` still looks up the two-argument `addReturnHomeItem`, and errors on every run with a display

| | |
|---|---|
| **Disposition** | Fixed - the test looks up the three-argument form and hands null, as the menu does where Start is offered, and its javadoc says what the item reads (d514de19).  Green in the fix run, on a display. |
| **Where** | `test/regression/testTheDiagramRefreshDoesNotWaitOnTheRailway.java:360-367` (`getDeclaredMethod("addReturnHomeItem", JComponent.class, TrainControlUI.class)`, `invoke(null, new JPanel(), ui)`); against `HomeLocomotiveMenu.java:65` (1098c9b8: the only declaration is now `(JComponent, TrainControlUI, String)`) and the sibling the fix did update, `testAHandSendIsRefusedWhileTheSetupIsBroken.java:227-231` |

1098c9b8 gave `addReturnHomeItem` a third parameter for ADU-C3 and updated one of the two test classes that reflect on it.  In the other, the lookup now throws `NoSuchMethodException`.  The runnable wraps it in a `RuntimeException`; `onTheEventThread` runs it through `invokeAndWait` inside a `Future` and catches only `TimeoutException` (1231-1294), so the `ExecutionException` leaves `whileTheRailwayIsHeld` on its first, unheld call and the test errors before any monitor is taken.  It is skipped headless (`@BeforeClass`, 104-107), so every battery on Adam's machine sees it red.

**Consequence.**  None on the railway.  But until it is mended the OB-192 door for the right-click Return Home item - the event thread must not wait on the railway's monitor while the popup is built - is measured by nothing but the source census.  **Mitigated** by `testNothingOnTheEventThreadTakesTheRailwaysMonitor`, which still reads `HomeLocomotiveMenu`'s source, and by the full battery before closing.  It is FANOUT step 4's miss: `grep addReturnHomeItem test/` finds both classes.

**Fix.**  `String.class` in the lookup and `door.invoke(null, new JPanel(), ui, null)` - null is the path the menu takes wherever Start is offered.  The javadoc above it (349-351) still says the item "reaches `triageReturnToHome`", which has not been true since OB-192's second round.

**Verification request.**
- **Fixture:** `regression.testTheDiagramRefreshDoesNotWaitOnTheRailway` on a display, at `117298de`.
- **Proves it:** `testTheReturnHomeMenuDoesNotWaitOnTheRailway` fails with `NoSuchMethodException: ...HomeLocomotiveMenu.addReturnHomeItem(javax.swing.JComponent, org.traincontrol.gui.TrainControlUI)`.
- **Refutes it:** green.

### ADU2-C2 - The right-click Place's list and its comments still describe the choice ADU-B1 replaced; with its heading, a train can now be stood on a copy that reaches no station, silently, where the list used to keep it off

| | |
|---|---|
| **Disposition** | Fixed - the item is offered over every copy a train could leave by, as the action chooses, and the action is handed all of them in the menu's order; the comments state the rule the code has.  Claim d514de19 (the item's guard) and pin 8548dbf6 (the action's list), fix 86b458c8; mutations W14, W15 red.  The missing sentence for a copy that reaches no station is filed as OB-299. |
| **Where** | `LayoutRightclickAutonomyMenu.java:705-720` (the item greyed by `placeableCopies`), `:1006-1073` (`placeableCopies`), `:1117-1131` (`copyToPlaceOn`: `departableFacingsFor`, then `copyFacing`), comments at `:685-686`, `:694-699`, `:1040-1042`, `:1064-1066`, `:1070`, `:1224-1227`; `AutonomySession.departableFacingsFor` (`:1853`, any copy with an outgoing edge) and `copyFacing` (`:1891`, destination copies first); `core.testAutoLayout.testACopyWithNowhereToBeSentIsNotPlaceable` (`:2160-2194`, the "I place a train and it never moves" fault) |

The fix is Adam's rule, and it is the paste's: keep any heading the train can leave by (OB-284), onto the paste's copy.  But this door had its own list for a reason, and three things now disagree with the code.

**The choice.**  Until 1098c9b8 the train went onto a copy from `placeableCopies`, which puts a copy that reaches a station autonomy may choose before a "stranded" one (outgoing edges, but only to plain points, reversing points or parking - `canReachAnyDestination` false).  Now the heading is taken over every copy with an outgoing edge, and `copyFacing` prefers destination copies - and a stranded copy is a destination.  So a train facing the way of a stranded copy is stood on it, and autonomy never moves it: the fault that list was written against.  Before, it was turned onto the live copy (a direction fault, which is worse, so the reading holds).  What is missing is a word.  A barred copy has `startFacingBarred` in Why not Moving? and `whyHomeStartFacingBarred` in Return Home.  A stranded copy has nothing at the door, and Why not Moving? lists each destination's own reason instead of the copy's.

**The guard.**  The item is enabled by `placeableCopies`, which never offers a barred copy (`shut`) even when it reaches a station.  The action chooses over `departableFacingsFor`, which includes it.  Where every copy with a way out is barred, the item is greyed with *"There is no way to drive a train out of this square"* while a barred copy reaches a station and the paste would stand the train there - the guard and the affordance asking different questions.  This is narrow: it needs a station whose unbarred copies have no outgoing edge.

**The comments**, each now false of this door:
- 685-686: *"somewhere trains may not stop is not somewhere to start one from"* - the door now puts a train on a copy trains may not stop at.
- 694-699: *"where the square can hold that heading, and otherwise the first the build made"*.
- 1040-1042: barred copies *"come second"*.
- 1064-1066: *"since W21-B3 those are exactly what `moveLocomotive` declines"* - this door moves with the form that accepts them.
- 1070: *"Stranded copies stay: they ARE destinations"* - a barred stranded copy is not.
- 1224-1227: the fallback *"is the case `moveLocomotive` refuses"*.

**Consequence.**  A train placed from the diagram that autonomy never starts, with no sentence saying why, and only at a station with a copy that leads nowhere autonomy sends trains.  **Mitigated:** the heading is the train's own; the paste, the dialog and the editor's Place already do this; the Facing item turns it.

**Verification request.**
- **Fixture:** `live-snapshot`, built as `testATrainIsPutOnlyWhereItCanStart` builds it.  For every station square, and each copy of `facingsFor(square)`, print `isDestination()`, whether `getNeighbors` is non-empty, `canReachAnyDestination`, and the heading.
- **Proves it reaches his railway:** a destination copy with a way out that fails `canReachAnyDestination`, at a square where another copy passes.  `copyToPlaceOn(session, square, running, <that heading>, <placeableCopies' list>)` returns it, and `1098c9b8^`'s form (chosen over the list only) returned the live copy.
- **Refutes it:** no such copy.  Then only the guard and the comments are stale on his railway.

**Remedy.**  Rewrite the comments to the rule the code has now.  Grey the item on `departableFacingsFor`, keeping "no way out" for a square where nothing departs.  Consider a Why not Moving? sentence for a copy that reaches no station, as a barred copy has.

### ADU2-C3 - ADU-C8 is half done: the berth warning still opens by saying the berth takes no train at all, in all eight languages, and the claim checks only the English closing clause

| | |
|---|---|
| **Disposition** | Fixed - *refuses every train with a length* opens the warning, in all eight languages.  Claim d514de19 (English), pin b152d025 (each language's old opening), fix 86b458c8; mutation W17 red. |
| **Where** | `autosetup.ui.checkBerthTrackGivenNoLength` (`messages.properties:1399`, and the same key in the other seven); `core.testMassAssignLengths.testTheBerthGivenNoRoomIsSaidInEveryLanguage` (`test/core/testMassAssignLengths.java:2488-2493`); against `Layout.whyABerthCannotHoldIt` (`Layout.java:10214`: no length, no refusal) |

The fix added "with a length" to the closing clause, *"every train with a length arriving that way is refused"*.  The sentence still opens *"{0} can take no train that comes in over the track behind it:"*, and so does every translation:
- da *"kan ikke tage imod et tog"*;
- de *"kann keinen Zug aufnehmen"*;
- es *"no puede recibir ningún tren"*;
- fr *"ne peut accueillir aucun train"*;
- it *"non può accogliere alcun treno"*;
- nl *"kan geen trein opnemen"*;
- pl *"nie przyjmie żadnego pociągu"*.

The berth rule still admits a train with no length.  So the headline says what ADU-C8 found false, and the corrected clause now contradicts it inside one sentence - and the headline is what the operator reads.

**The claim** asserts the phrase in `messages.properties` only.  The seven translations were changed (the diff is right in each), but reverting any of them passes, and nothing looks at the opening clause.  The disposition's *"in all eight languages, and the bundle claim looks for it"* is true of the code, and of the claim in English only.

**Consequence.**  Wording.  **Mitigated** on a railway where every train has a length.

**Fix.**  Put "with a length" in the opening clause (*"{0} can take no train with a length that comes in over the track behind it"*) in all eight.  Have the claim assert, for each bundle, that its text differs from the pre-`1098c9b8` text, or check a phrase listed per language.

### ADU2-C4 - The own-tail note still says "{0} stretch(es) of track ... have no length at all", but since this round {0} counts the pieces, switches and crossings Mass Assign asks for - in eight languages

| | |
|---|---|
| **Disposition** | Fixed as ADA2-C7. |
| **Where** | `autolayout.errorOwnTailPartlyUnmeasured` (`messages.properties:171`; in the others *sporstykke(r)*, *Gleisabschnitt(e)*, *tramo(s)*, *tronçon(s)*, *tratto/i*, *spoordeel/-delen*, *odcinek/odcinki*), appended at `Layout.java:10584`; the count `askedForOnTheWayRound` (`:10624`) over `askedFor` (`:10601`), keyed by `AutonomySession.piecesToMeasure` (1098c9b8: "piece N" per piece, "square <tile>" per switch and shared square); Mass Assign's own words: `promptMassAssignLength` (*"Stretch {0} of {1}"*), `promptMassAssignSwitches`, `promptMassAssignCrossings` |

The number's meaning has moved twice.  OB-297 (round 1) changed it from legs to pieces.  ADA-C1 (this round) changed it to *what Mass Assign Lengths would ask a length for*: each piece, each switch and each square two roads cross, once each.  The records commit updated two documents to match:
- the user guide: *"how many pieces - the same ones Mass Assign Lengths asks you for"* (`Automation.md:166`);
- MT-571's note: *"pieces, switches and crossings"*.

The sentence Adam actually reads was not touched.  A loop measured except for two switches is refused with *"2 stretch(es) of track on the way round have no length at all"*.  Mass Assign then asks for no stretch, and asks for two switches under its own heading.  One list now has three vocabularies: the note's "stretches of track", the guide's "pieces", and Mass Assign's "stretch / switches / squares two roads cross".

**Consequence.**  Wording; the remedy is still reached, because Mass Assign asks for everything the note counts.  **Mitigated:** on the frozen railway every leg is measured and the note does not appear.

**Fix.**  Name the list the count is now - for example *"{0} of the things Mass Assign Lengths asks a length for on the way round - stretches, switches or crossings - have none yet ..."* - in eight languages.  Use the same word in `Automation.md`.

**Verification request.**
- **Fixture:** `core.testTheOwnTailArithmetic`: a way round whose only marked places carry switch keys (`"square ..."`, two of them), and a 20-unit train.
- **Proves it:** the refusal ends *"2 stretch(es) of track on the way round have no length at all"*.
- **Refutes it:** the note does not count switch marks.

### ADU2-C5 - ADU-C3 was closed on its healthy row: over a setup with errors a right-click still walks the setup five times where it walked three, and LD-C6's "Asked ONCE each" is false there

| | |
|---|---|
| **Disposition** | Fixed - the menu reads Start's sentence and the hand doors' in one call over the three numbers (`whyStartAndAHandSendAreRefused`), so a right-click over a broken setup walks the setup twice where it walked five times.  Claim d514de19, narrowed before the fix to the block that builds the two items (a377fc5c: the destination item's click-time guard is right and stays); pins 8548dbf6 (live over a broken setup, and each sentence to its item); fix 86b458c8; mutations W11, W12, W13 red.  The older pins that asked for `whyAutonomyWillNotStart()` in the menu accept the combined reading and check it asks the rule. |
| **Where** | `LayoutRightclickAutonomyMenu.java:413-423` (LD-C6), `:423`, `:445`, `:464`; `TrainControlUI.java:23771-23775` (`canStartAutonomy`), `:23824-23836` (`whyAutonomyWillNotStart()`), `:23874-23881` (`whyAHandSendIsRefused()`); `AutonomySession.errorCount` (`:6250`, one `check()` each call), `hasErrors` (`:6276`) |

**Where the fix acts, it is right.**  `canStart` is `startAutonomy.isEnabled() && !isRemoteLayout() && !autonomyHasErrors()`.  So wherever Start is offered the setup has no errors, and `whyAHandSendIsRefused()` would have answered null anyway.  The shortcut is equivalent, and a healthy right-click asks `check()` once again.

**The broken row is unchanged.**  ADU-C3's table had two rows.  Over MT-573's break (one error finding), building the menu costs:
1. `canStartAutonomy`, through `errorCount`;
2. Start's tooltip, through `autonomyErrorCount`;
3. Start's tooltip again, through `autonomyHasErrors`;
4. the Return Home sentence, through `autonomyHasErrors`;
5. the Return Home sentence again, through `autonomyErrorCount`.

That is five `check()` walks against three before TDU2-C3, and the fix did not change it.  The disposition says only *"a healthy right-click asks the setup once again"*, which is true; the other half of the finding was closed without a word.  LD-C6's comment - *"Asked ONCE each"* - is false on this path.

**Consequence.**  Latency on the event thread while the setup is broken, which is when the operator is right-clicking to find out why.  No freeze: no monitor is taken.

**Fix.**  Read the error count, the blocking count and `hasErrors` once in the constructor.  Hand them to both static rules, `whyAutonomyWillNotStart(int, int, boolean)` and `whyAHandSendIsRefused(int, int)`, which are functions of exactly those numbers.

**Verification request.**
- **Fixture:** ADU-C3's own: count `AutonomySession.check()` entries while the menu is built for a station over MT-573's break.
- **Proves it:** 5.
- **Refutes it:** 3 or fewer.

### ADU2-C6 - OB-295: the dialog that sets "Unavailable While Occupied" still says "Autonomy will not send a train to {0}", which is narrower than what the rule does now

| | |
|---|---|
| **Disposition** | Fixed - *Trains will not be routed to or through {0} while any of these is occupied or has a route running into it*, in eight languages (86b458c8).  Its test was written after the fix (8548dbf6), so it is held by mutation W18, not by a red first. |
| **Where** | `autosetup.ui.promptBlockedByPoints` (`messages.properties:410`, same key in the seven), shown at `AutonomyEditorPanel.java:4780-4781`; against `Layout.isPathClear` (`:2373`) → `heldBackAlong` (`:5602`), which `getPossiblePaths` (`:5902-5933`) asks for the hand doors as well as for autonomy; `behaviour.md` lines 66-78 |

**The reading holds.**  Adam: *"trains shouldn't be sent to THIS square while trains are STANDING ON or hold a lock on the other specified station(s)"*.  The work read "sent to" as "a route arrives at it", which covers every square on a route.  The question was about a square trains only pass, where no train is ever sent as a destination.  Read as destination-only, the standing half he named would do nothing there, which was TDA-C9's own complaint.  The costs are in `behaviour.md`: a station with the restriction is also shut to routes *through* it while the watched train stands, and two restrictions can hold each other (ADA-C3).

**What the operator reads.**  Setting it on BottomMainAPre, as MT-584 step 2 does, he reads *"Autonomy will not send a train to BottomMainAPre while any of these is in use"*.  Two words are too narrow.  They already were for the lock half, which `behaviour.md` says always closed routes through the square; OB-295 has made the standing half act the same way, and at a square trains only pass, routes through are all either half acts on:
- "to": the rule shuts routes through the square;
- "Autonomy": hand sends are refused by the same rule.

`behaviour.md` says *"the cost is the one the setting names"* - the name "Unavailable" does, but this sentence does not.

**Consequence.**  Wording.  **Mitigated:** no square on the frozen railway carries the restriction (`blockedPoints` is empty).

**Fix.**  For example *"Trains will not be routed to or through {0} while any of these is occupied or has a route running into it"*, in eight languages.

### ADU2-C7 - The platform notice can quote a refusing figure below its own {3}; the records say it never does

| | |
|---|---|
| **Disposition** | Fixed as ADA2-C2. |
| **Where** | `AutonomySession.refusingFigures` / `measuredWayIn` (1098c9b8, `:10021-10155`; a leg counts when it `has("roomAtTheEnd")`, which the builder writes for every leg over a switch, `-1` when unmeasured - `AutonomyBuilder.java:1336`) against `runInFigures` (`:9982`: a room `<= 0` is skipped for {3}); `behaviour.md` §5 (TDA-C10 paragraph, 117298de): *"Only for the notice's own ways in, over a switch or from a turn, so it is never less than {3}"* |

This belongs to the autonomy lane; I raise it because the sentence is in the findings list the operator reads.  Take a platform with two ways in over switches.  On L1 the room is measured at 5; on L2 the room past the switch is unmeasured, but the leg carries 3 measured units.
- {3} skips L2 (room -1) and says 5.
- `refusingFigures` counts L2 and says 3.

**The figure is still the railway's refusal:** `measuredRoomAtTheEndOf` bounds an unmeasured room by the leg's own length (`Layout.java:10934-10955`), and the route in holds no more.  So the notice reads *"only 5 ... a train longer than 5 stands across that switch ... Coming in the shortest way, over 3 of measured track, a train longer than 3 is refused instead"*.  That is true, but it pairs two different ways in without saying so, and `behaviour.md`'s *"never less than {3}"* is false for it.

**Consequence.**  Wording, and only between Mass Assign sittings; on the frozen railway every way in is measured.  Fix the sentence in `behaviour.md`, or have the figure count only ways whose room {3} counts.

**Verification request.**
- **Fixture:** `testAutonomyDiagramSession`'s platform fixture with a second way in over a switch.  Leave its squares between the switch and the platform unanswered, and measure 3 before the switch, from a station.
- **Proves it:** `runInsShorterThanTheBerth()` gives `{max, 5, 3}`, and `whyTooLongForThisRoute` refuses a 4-unit train on that way in.
- **Refutes it:** the figure is absent or at least 5.

### ADU2-C8 - `testOnlyThePutBackAcceptsABarredCopy` names as its mutation a change that is already the code

| | |
|---|---|
| **Disposition** | Fixed - the mutation line names what the census catches (d514de19). |
| **Where** | `test/regression/testTheRefusalsAreAskedAtTheDoors.java:289` (*"MUTATION: pass true from the paste door and this fails"*), against `:340-343` and `TrainControlUI.java:7255` (the paste passes `true` since OB-284) |

1098c9b8 rewrote this javadoc to name the three doors and added the right-click Place to the pinned list, but left the mutation line alone.  The paste already passes `true`, so the named mutation changes nothing.  The final mutation run reads these lines as its specification (FANOUT, "Before closing").  The mutations the census does catch are a door passing `false`, or any other door passing `true`.  The method's name no longer says what it pins either; ids are not renamed, so the javadoc should carry that.  **Consequence:** none on the railway.  **Fix:** *"MUTATION: pass false from any of the three doors, or true from any other, and this fails."*

## D - checked, clean

### ADU2-D1 - ADU-B1, ADU-C1, ADU-C2: the right-click Place takes the paste's heading and the paste's copy, and moves onto a barred copy; the dispositions are true

| | |
|---|---|
| **Disposition** | Checked - clean. |

**The code.**  `copyToPlaceOn` is `facingAfterAPaste(departableFacingsFor(station, running), keep, null)`, then `copyFacing(station, intended, running)`.  Those are the paste's two halves (`TrainControlUI.java:7220-7226` over `placeableFacings`, which is `departableFacingsFor` at 8239) and the dialog's (`GraphLocAssign.java:246-249`).  The heading is read before the move (`facingOf(loc, running)`, 1093).  The move uses `moveLocomotive(..., false, true)` (1252), which admits a barred copy of a station and nothing else that is no station (`Layout.java:9457`), and displaces the whole square (9496) as the paste does.

**On his railway.**  A train facing west placed at BottomMainA stands facing west on the barred copy.
- Its arrival side is recorded as W, the one unbarred side (`unbarredArrivalSides`, OB-204).  That is where Adam wanted BottomMainA's tail (*"the orange tail facing west"*), and it is true of a train that came in the only open way and was turned.  The paste records the same.
- Autonomy says why it will not start it (`startFacingBarred`), and Return Home says so too (`HomeStaging.java:2085`).

**The one difference from the paste.**  At a square trains may turn at, the paste asks the heading (`FacingPrompt`); this door keeps it without asking.  That predates the round, and Adam's answer asked only that the heading be kept.

**The claims.**
- The claim runs the rule on the built frozen railway at BottomMainA, with its barred westbound copy asserted as a precondition.
- The pin (b6835920) runs it each way at every station, with a floor saying at least one station tells the first copy from `copyFacing`.
- The door's order (heading read, rule asked, then the move) and its four-argument move are pinned in source.
- REG4-C3's census lists the door.
- MT-581's comment and `behaviour.md`'s paragraph match the code.

**One note on the pin.**  It compares the door with `copyFacing(side)` directly, not with `facingAfterAPaste` followed by `copyFacing`.  The two agree only where every heading's copy has a way out.  That holds on the frozen railway (the pin would be red otherwise); on a railway with a departure barred it would be a false red, never a missed defect.

### ADU2-D2 - ADU-C4, ADU-C5 and ADU-C6: the dispositions are true

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **ADU-C4.**  The tooltip is `AutonomyEditorPanel.wrapped(broken)`.  Both javadocs (`HomeLocomotiveMenu.java:46-53`, `TrainControlUI.java:24502-24508`) now say the item and the button part over a broken setup on purpose.  The claim asserts `<html` and compares the unwrapped text.  `wrapped` escapes only `& < >`, and neither setup sentence contains one.
- **ADU-C5.**  MT-580's comment says the two tooltips differ by design, and what each says.
- **ADU-C6.**  The claim waits on `awaitReturnHomeTriage` and asserts `isReturnHomeOffered()` before the break.  The break, the item's build and its reads are one event-thread task, so no triage can grey the button in between.  Mutation V13 (grey on the triage only) therefore leaves the item enabled and fails `outcome[4]`.  The claim is still skipped headless.

### ADU2-D3 - TDU-C6: the narrow reading holds against his words and the 2026-09-23 ruling, every UI door agrees with it, and ADU-C7's disposition is true

| | |
|---|---|
| **Disposition** | Checked - clean. |

**The reading.**  The question put to him was about the Atomic Routes refusal: it named answered-0 track and said *"Measure them"*, with no door to measure it.  *"So non-atomic should be allowed"* is the consequence he wanted.  The 2026-09-23 ruling (OB-274, confirmed when asked directly) has every length rule read an answered 0 as unmeasured.  Reading the new answer at the gate and its escape keeps both rulings.  The wider reading was built for a day; it admitted a train whose tail lay on track the standing walk never claims (ADA-A1), and it would need the tail walks to follow.  Putting that wider reach to him, with its cost (open-questions.md, Length), is the right handling.

**The doors.**
- The checkbox (`TrainControlUI.java:5949`), the load door and `unmeasuredTrackAutonomyRunsOver` all go through `unmeasuredTrackThatCouldBeReleased`, which asks `Edge.isMeasured` (`Layout.java:9604`).  The escape asks it too (`:4967`).
- Segment Length's javadoc (`AutonomyEditorPanel.java:7077-7078`), the session's and the reducer's javadocs, and `Edge.isMeasured` (`:493-501`) now name only the gate and the escape.
- `promptTileLength` (*"0 means it does not count"*) and Mass Assign's *"(0 = none)"* stay true: a 0 adds nothing.

**ADU-C7.**  The tail question (`TailCrossedPrompt` `back`/`reachOf`), the standing walk and now `measuredRouteIn` (`Layout.java:10145-10163`) all stop at a leg with no length.  The tail doors agree with each other.

**Caveat.**  open-questions' *"your own railway has no stretch answered 0 end to end"* rests on the frozen copy: 158 tile lengths, none 0, files dated 2026-09-23.  This lane may not read the live railway.

### ADU2-D4 - The eight message bundles

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **Keys and characters.**  Each bundle has 1829 keys, the same set in each, and every file is pure ASCII (no byte over 127).  No key's placeholder set differs between languages.
- **The changed key.**  `checkBerthTrackGivenNoLength` carries `{0}` only, with no straight apostrophe, in every language.  Each translation's new clause says "with a length" naturally: *med en længde*, *mit einer Länge*, *con longitud*, *ayant une longueur*, *con una lunghezza*, *met een lengte*, *o określonej długości*.  ADU2-C3 is about the clause before it.
- **Scope.**  The round's bundle diffs touch that key alone.

### ADU2-D5 - ADU-C9 is done, and the round orphaned no javadoc

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **ADU-C9's three edits.**  The AUT2-A1 javadoc is back above `testTheBuildKeepsTheFacingTheSetupRecords` (`testATrainIsPutOnlyWhereItCanStart.java:221-225`).  The list's comment reads *"was drawn at RANDOM"*.  The broken comment line is reflowed.
- **The census, recounted** as `testJavadocsAreAttached` counts: 87 orphans in `src/`, and per file exactly `ORPHANS_BY_FILE`.
- **The round's commits.**  No hunk of `2c7a5b47`, `1098c9b8` or `b6835920` puts a javadoc straight after another.  `git blame` on every back-to-back javadoc in the touched files names older commits.

### ADU2-D6 - The answers this round did not touch still stand as round 1 found them, and their readings hold

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **TDD5-C1, *"Follow the train"*.**  `TailCrossedPrompt`, `GraphLocAssign` and `AutonomyViewerPanel` are unchanged across the range.  Only the claim's mutation text changed (`testTheTailIsPickedOnTheDiagram.java:1076`), and it now names the running railway as well as the configuration.  The reading holds: the answer is written to the configuration running now, for the same train on the same square, from the same side, over the same road.  The other reading - wherever the train now stands - would put an answer about one approach onto another.
- **TDD-C11.**  Unchanged.  ADU-D3 stands.
- **TDU2-C3's buttons.**  Return Home and Execute Timetable are unchanged.  Both are live and refuse at press time with the setup's sentence.
- **TDA-C8.**  The arrivals menu's hint says *"Which sides a train may arrive by and stop.  Passing through is not affected"*.  That is his *"Arrivals THAT STOP THERE"*.
- **TDA-C10.**  Both renderers still pass the figure as {4}.  `runInFigures` gives it only where it is above 0 and under the maximum (ADU2-C7 is about which ways in it counts).
- **The event thread.**  The new code on the click - `copyToPlaceOn`, `departableFacingsFor`, `copyFacing`, `facingOf` - calls no `synchronized` method of `Layout`.  The move stays inside `placeFacing`, which the monitor census allows.  The reducer handed out only once reduced (ADA-C2) is null-checked by every gui reader.
