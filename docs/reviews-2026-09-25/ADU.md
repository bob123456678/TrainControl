# UI validation - Adam's answers of 2026-09-24, round 1

**Status:** open

**Prefix:** `ADU`

**Reviewed:** branch `autonomy-diagram-r0` at `d05a6356`, 2026-09-25.  Range `45cfa410..d05a6356` (`7dc22256` claims, `f17f5a5c` fixes, `ac4c7567` TDA-C10 re-read, `e269aaec` records, `d05a6356` status lines).  Lane: `src/org/traincontrol/gui`, the eight message bundles, and the claims and manual tests for them.

**Method:** I read `docs/reviews/FANOUT.md` and `docs/reviews/README.md` first.  Then I read `git show` of all five commits in full, and the findings each answer settled, as they stood at `d05a6356` (TDU.md, TDU2.md).  I read the `open-questions.md` and `behaviour.md` diffs in `e269aaec`, which hold the questions as they were put to Adam.  At HEAD I read the code around every change, and each door it has to agree with:
- **Placement.** The right-click Place (`LayoutRightclickAutonomyMenu.placeableCopies`, `placeSomewhereLegal`, `copyToPlaceOn`, `placeFacing`), set against the paste (`TrainControlUI` 7040-7260, 8239), the locomotive dialog (`GraphLocAssign` 225-330, 844) and the editor's Place (`AutonomyEditorPanel` 5440-5480).  With them I read `AutonomySession.facingOf`, `facingAfterAPaste`, `departableFacingsFor`, `placeableFacingsFor` and `copyFacing`, the builder's split order (`AutonomyBuilder.splitSides`, the node loop at 600-650, `facingOf(Node)`, `homeCopy`), and `Layout.moveLocomotive`'s two forms.
- **Return Home.** The Return Home item (`HomeLocomotiveMenu`) and the menu constructor around it (`canStartAutonomy`, the LD-C6 comment).  Then `whyAHandSendIsRefused`, `whyAutonomyWillNotStart`, `autonomyHasErrors`, `requestReturnToHome`, the Return Home button's refresh and `isReturnHomeOffered`, and `AutonomySession.check` / `hasErrors` / `errorCount`, for what the item now costs.
- **The tail answer.** `TailCrossedPrompt.sameSetup` / `whereTheAnswerGoes`, and `back` / `reachOf`, which decide what the tail question offers.
- **The import.** The legacy import (`AutonomyViewerPanel.importLegacyGraph`) and Load Autonomy at start-up (`TrainControlUI` 9721).
- **Findings text.** Both findings renderers (`AutonomyEditorPanel` 9563, `AutonomyViewerPanel` 1622) and every other reader of a finding's message key.
- **The rules underneath.** `Edge.isMeasured`, and the `getLength() <= 0` tests left in `TailCrossedPrompt` and `Layout`.  `Layout.heldBackAlong` and its message, `Layout.whyABerthCannotHoldIt`, and `GraphReducer`'s getters against every gui caller.

**Claims, frozen railway, bundles.** I read these claims: `testTheRightClickPlaceKeepsTheTrainsHeading`, `testABrokenSetupIsRefusedAndAMendedOneIsNot`, `testAnAnswerFollowsTheTrainIntoAnotherConfiguration`, `testALegacyImportLeavesLoadAutonomyAlone`, the TDA-C10 and TDA4-C2 claims and their bundle checks, and MT-580 to MT-583, MT-503 and MT-573.  From the frozen railway (`test/layouts/live-snapshot/config/autonomy/`) I read `barredArrivals`, `pointNames` and `tileLengths` in setup.json, and the page-5 points of configuration-Main.json, with read-only `python -c`.  I checked the eight bundles with read-only Python fed through stdin: key sets, ASCII, placeholder sets, apostrophes, and the new and removed keys.

**What was not done.** Nothing was run, compiled or started, and no git state was changed.  `cs2_sample_layout/` was not opened.  One stdin script failed to parse because the heredoc ate a backslash; it wrote nothing.  Autonomy-lane internals (TDA-C8's build, OB-297's counting, OB-298's concurrency) were read only where they reach the UI.  Every finding that depends on behaviour carries a verification request.

## A - high

None found.

## B - medium

### ADU-B1 - OB-296: the right-click Place keeps the heading only among copies that are stations with a way on, so at a square with a barred side it still turns the train round, where the other three doors keep its heading

| | |
|---|---|
| **Disposition** | Fixed - the right-click Place chooses the heading over every copy the train can leave by and moves even onto a copy trains may not arrive at, as the paste and the dialog do.  Claim 2c7a5b47 on his railway at BottomMainA, widened in b6835920 to every station each way; fix 1098c9b8; mutations V2, V3, V4 red.  MT-581 has a comment adding BottomMainA. |
| **Where** | `LayoutRightclickAutonomyMenu.java:701` (`usable = placeableCopies()`), `:1002-1068` (`placeableCopies`: station copies that reach a station, and stranded copies only when none does), `:1109-1136` (`copyToPlaceOn`: over `usable` only - one usable copy means that copy's heading), `:1255` (`moveLocomotive(locName, pointName, false)`, which refuses a copy trains may not arrive at).  The other three doors: `GraphLocAssign.java:246-247`, `:844`; `TrainControlUI.java:7221`, `:8239`, `:7255`; `AutonomyEditorPanel.java:5474-5475` - all over `departableFacingsFor` (OB-284), and the paste and the dialog move with `moveLocomotive(..., false, true)` (`evenOntoABarredCopy`) |

**Adam's words and the question they answered.**  The question put to him (open-questions.md before `e269aaec`) was: *"The other three placement doors keep the train's heading and stand it on the copy that faces that way; should this one follow them?"*  He answered *"Yes, keep the train's heading."*  The other three doors keep it over **every copy the train can leave by**, `departableFacingsFor`.  That set includes a copy trains may not arrive at, per his OB-284 ruling: *"for barred arrival directions, keep the direction"*.  The train then stands on the barred copy, and autonomy says why it will not start it there.

**What the fix did.**  It changed the choice and left the candidate set alone.  `copyToPlaceOn` runs the paste's rule (`facingAfterAPaste`), but only over `placeableCopies()`.  That list drops two kinds of copy:
- a copy trains may not arrive at, which is not a station;
- a station copy that reaches no other station, whenever another copy does.

Where only one copy survives, `facingAfterAPaste` returns that copy's heading (`held.size() == 1`), and the train is turned.  Even if the barred copy were chosen, `placeFacing`'s three-argument `moveLocomotive` refuses it (W21-B3).  This door can never do what the other three do.

**On the frozen railway.**  Seven squares have a barred side (`barredArrivals`): BottomMainA (E), TopMainR1Inter (E), TopMainR2Inter (E), Tunnel (S), BottomInner (W), BottomMainPost (N) and RampDown (S).  The first five are not turning squares.  At each of them, a train facing the way the barred copy faces is put on the other copy by the right-click Place, silently.  For example, 75 407 DB facing west, placed at BottomMainA, is recorded and drawn facing east.  The paste, the locomotive dialog and the editor's Place stand the same train facing west on the barred copy.

**On the railway.**  The copy is the train's direction: the runtime never commands an absolute direction.  A train whose model heading is opposite to its physical one is sent "forward" along a route locked the other way.  That is the consequence TDU-B4 was graded B for, now on a narrower set.

It is not a regression: the random draw picked from the same list, so at these squares it always turned the train too.  But TDU-B4's disposition says Fixed, and this case is not fixed.  Two documents are now false for this door:
- behaviour.md (after `e269aaec`, the section 3 paragraph ending "`testTheRightClickPlaceKeepsTheTrainsHeading`") says the right-click Place *"follows the same rule"*.
- behaviour.md's barred-side paragraph (`:1989`) says *"a placement keeps it where the train can leave that way (OB-284)"*.

**What mitigates it.**  The diagram draws the heading arrow straight after the placement.  The Facing item can turn the train back, and `copyFacing` falls back to the barred copy for that.  A train only faces a barred way after a throttle reversal, the Facing menu, or another door's placement.

**The claim proves the rule and not the call** (see memory: *extracted rule moves the bug to the call*).  `testTheRightClickPlaceKeepsTheTrainsHeading` hands `copyToPlaceOn` a synthetic `usable` list.  Its last assertion pins this defect as correct: with one usable copy facing west and the train facing east, it expects the west copy (*"the one copy a train can leave was refused because the train faced the other way"*).  At the door, that single-copy case is exactly a barred or stranded twin being filtered out.

MT-581 places at BottomMainB, where both headings have a station copy, so it passes on the defect.

**Fix sketch.**  Take the candidates as the other doors do, `departableFacingsFor(station, running)`.  Choose the copy with `copyFacing(station, intended, running)` - see ADU-C1.  Move with `moveLocomotive(loc, copy, false, true)`.  Keep `placeableCopies` for greying the item.  Then:
- invert the claim's single-copy assertion into a claim about the input set;
- add an MT comment moving MT-581's square to BottomMainA.

**Verification request.**
- **Fixture:** `live-snapshot` in a `LayoutSandbox` with a window, as `testABrokenSetupIsRefusedAndAMendedOneIsNot` opens one.  Stand 75 407 DB facing west (for example on BottomMainB's west-facing station copy), make it the active locomotive, and build the right-click menu for BottomMainA (`5:20,12`).  Invoke `placeSomewhereLegal(placeableCopies())` by reflection.
- **Proves it:** 75 407 DB stands on the copy that arrived from the west, and `facingOf("75 407 DB", running)` is E.
- **Refutes it:** it stands facing W on the copy that arrived from the east.
- **Control:** the paste (`rememberPlacement`) of the same train at BottomMainA stands it facing W.
- **Without a window:** on the built railway, `facingsFor(BottomMainA)` shows the W-facing copy `!isDestination()`.  It is absent from `placeableCopies` and present in `departableFacingsFor`.

## C - low

### ADU-C1 - OB-296: where more than one copy faces the kept heading, the right-click Place takes the first in build order, and on BottomMainB that is the turning copy; the paste takes the plain copy

| | |
|---|---|
| **Disposition** | Fixed with ADU-B1 - the paste's copy, `copyFacing`.  V3 survived the first claim (BottomMainA has one copy each way) and is red against the pin in b6835920. |
| **Where** | `LayoutRightclickAutonomyMenu.java:1127-1132` (first `usable` copy whose heading matches); against `AutonomySession.copyFacing` (`:1880-1918`: plain copy first, turning copy as a fallback) and `AutonomyBuilder.homeCopy` (*"The plain copy before its turning twin"*) |

The copy's javadoc says *"the rule is the paste's"*, but the paste has two halves:
- `facingAfterAPaste` picks the heading;
- `copyFacing` picks the copy, preferring one that does not turn the train (MT-368, MT-394, GUI-B1, TDY2-A1).

The fix took only the first half.  The build emits copies side by side in `TreeSet` order (N, E, S, W), and for each side the plain copy comes before its turning twin.  On an east-west square that trains may turn at, the order is:
1. E-plain (faces W)
2. E-turn (faces E)
3. W-plain (faces E)
4. W-turn (faces W)

On BottomMainB (`canReverse`, MT-581's own square), an eastbound train therefore lands on the E-arrival turning copy, which the build emits as a terminus.  The paste and the dialog put it on the plain W-arrival copy.

The heading is the same, so this is not a direction fault.  But it is state that moves on its own: the next rebuild puts the train back through `placementCopy` (plain first), so its copy changes with nothing touched.  Any rule that asks `isTerminus()` / `isReversing()` of the copy a train stands on answers differently until then.  I found no such rule that changes a dispatch; the tail walk and the tail question work by place.

**Verification request.**
- **Fixture:** the built frozen railway.  Print `facingsFor(BottomMainB)` in order, with `isTerminus()`, `isReversing()`, `isDestination()` and `canReachAnyDestination()` for each copy.  Then compare `copyToPlaceOn(<placeableCopies' filter>, facingsFor(BottomMainB), E)` with `copyFacing(BottomMainB, E, running)`.
- **Proves it:** the first is the turning copy and the second the plain one.
- **Refutes it:** the same copy, or the turning copy is not in the usable set.
- **Fix:** resolve the heading with `copyFacing`, as ADU-B1's sketch does.

### ADU-C2 - OB-296 makes a pre-existing trap deterministic: `placeableCopies`' stranded fallback can hold a copy trains may not arrive at, which `moveLocomotive` refuses

| | |
|---|---|
| **Disposition** | Fixed with ADU-B1 - the move allows a barred copy (`moveLocomotive(..., false, true)`); REG4-C3's list names this door now.  Mutation V4 red. |
| **Where** | `LayoutRightclickAutonomyMenu.java:1038-1040` (`!canReachAnyDestination` is tested before `isDestination`, so a barred copy that reaches nothing lands in `stranded`); `:1066` (*"Stranded copies stay: they ARE destinations, so the railway accepts them"* - not checked, and not always true); `:1255` |

**The trap.**  Take a station square where no copy reaches another station and one copy is barred.  The menu offers Place, with `stranded` holding the barred copy.  If the train's heading is the barred copy's, the kept heading now picks that copy every time.  `moveLocomotive`'s three-argument form refuses it, and the click does nothing except write a log line: the OB-057 / OB-090 shape that TWV-C6 closed for the non-stranded list.

Before the fix, the random draw made this fail about half the time.  Now it fails every time for that heading and never for the other.  It is narrow: it needs a dead-end station with a barred side.

**Verification request.**  On the built frozen railway, list every station square whose copies all fail `canReachAnyDestination`, with `isDestination()` for each copy.
- **Proves it's reachable:** any such square with a barred copy.
- **Otherwise:** the finding stays a trap, and line 1066's sentence is still false.

ADU-B1's sketch removes the trap, because it moves with `evenOntoABarredCopy`.

### ADU-C3 - TDU2-C3: the Return Home item's setup question adds uncached `AutonomySession.check()` calls to every right-click on the diagram, on the event thread

| | |
|---|---|
| **Disposition** | Fixed - the menu works the hand doors' sentence out from Start's answer and hands it to the item; a healthy right-click asks the setup once again.  Claim 2c7a5b47 (`testTheMenuAsksTheSetupOnceWhereStartIsOffered`), fix 1098c9b8; mutation V11 red. |
| **Where** | `HomeLocomotiveMenu.java:65` (`ui.whyAHandSendIsRefused()` on every popup); `TrainControlUI.java:23874-23881` (`autonomyHasErrors()` then `autonomyErrorCount()`, each reaching `session.check()`); `AutonomySession.java:5916-6040` (`check()`: an inspection build `builtForInspection` and the run-in walks, which `ac4c7567` extended with TDA-C10's shortest-way-in pass); against `LayoutRightclickAutonomyMenu.java:405-423` (LD-C6: *"Both of these reach AutonomySession.check(), which is not cached ... four full walks of the railway on the event thread, every time somebody right-clicks a station ... Asked ONCE each"*) |

A popup menu is built on the event thread.  Counting the `check()` calls in the constructor:

| Setup | Before this range | Now |
|---|---|---|
| Healthy | 1 (`canStartAutonomy`) | 2 |
| With errors | 3 (Start's item and its tooltip) | 5 (the Return Home item adds `autonomyHasErrors` and `autonomyErrorCount`) |

LD-C6 had brought this count down on purpose, in the same constructor.  The method's own javadoc still says *"Only the cheap half of the question is asked"* (`HomeLocomotiveMenu.java:35`), which is no longer true.  It freezes nothing, since `AutonomySession` takes no lock.  It is latency on the right-click, and it grows with the railway.

**Fix.**  `canStart` is already known in the constructor.  Pass `ui.autonomyHasErrors()`'s answer into `addReturnHomeItem`, or compute `whyAHandSendIsRefused()` once and share it with Start's tooltip.

**Verification request.**
- **Fixture:** the frozen railway on a window.  Count `AutonomySession.check()` entries while `LayoutRightclickAutonomyMenu` is built for BottomMainA, first healthy and then broken as MT-573 breaks it (a breakpoint counter, or a probe on a scratch branch), and time one `check()`.
- **Proves it:** counts of 2 and 5 against 1 and 3 at `45cfa410`, with the time per call.
- **Refutes it:** the same counts.

### ADU-C4 - TDU2-C3: the item and the button now describe one setup two ways, which two javadocs say cannot happen; and the item's tooltip is not wrapped as Start's is

| | |
|---|---|
| **Disposition** | Fixed - the tooltip is wrapped as Start's is, and the javadocs say the item and the button part over a broken setup on purpose.  Claim 2c7a5b47, fix 1098c9b8; mutation V12 red. |
| **Where** | `HomeLocomotiveMenu.java:47-48` (*"the item and the button cannot describe one situation two ways, because there is only one description"*), `:50` (*"Autonomy being busy is still asked HERE and first"* - the setup is asked first now), `:77` (tooltip set unwrapped); `TrainControlUI.java:24506-24507` (`isReturnHomeOffered`: *"They cannot disagree with it, because there is nothing left for them to disagree with"*); against `LayoutRightclickAutonomyMenu.java:445` (Start's item: `AutonomyEditorPanel.wrapped(...)`) |

Over a broken setup the right-click item is greyed with the setup's sentence.  The Return Home button beside it is live, with the triage's tooltip.  That is Adam's chosen design (*"the buttons live and explaining"*), but the fix did not update the two javadocs that promise the opposite; it added a paragraph below them instead.

The recommendation said *"greyed with the setup's sentence as its tooltip, as Start's item is"*.  Start's item wraps its tooltip at 320 px (`AutonomyEditorPanel.wrapped`: *"A tooltip that wraps instead of running off the screen"*).  The Return Home item sets the setup's sentence, about 200 characters in English and longer in German, as one line.  If it is wrapped, the claim's `assertEquals(outcome[5], outcome[2])` must compare the unwrapped text.

### ADU-C5 - MT-580's expected result says the Return Home tooltip is "the sentence Start's greyed item shows"; after MT-573's break the two sentences differ, by design

| | |
|---|---|
| **Disposition** | Fixed - a comment on MT-580: the two tooltips are different sentences by design, and what each says (the round's records commit). |
| **Where** | `docs/manual-tests/tests.md:28427` (MT-580, Expected); `TrainControlUI.java:23850-23859` (`whyAutonomyWillNotStart(errors, ...)`: with error findings, *"Autonomy cannot start while the setup has {0} error(s)..."*); `:23895-23898` (`whyAHandSendIsRefused`: always the setup's own words, *"This setup cannot be used yet: ..."*, never Start's); `AutonomyChecks.java:1116` (the duplicate-sensor-page finding MT-573's break makes is an ERROR) |

MT-580 step 3 breaks the setup as MT-573 does, by unticking Exclude Page on 4 - Combined.  That produces an error finding.  Start's greyed item then says *"Autonomy cannot start while the setup has 1 error(s). Open the autonomy editor to see them."*  The Return Home item says *"This setup cannot be used yet: one thing has to be dealt with first..."*.

The code is right: MT-263 and TDU2-C3 ask for the setup's sentence, and `whyAHandSendIsRefused`'s javadoc says it never uses Start's words.  The manual test's expected result is wrong.  Adam will read two different sentences against an expectation that says one, and mark a correct behaviour failed.

**Remedy.**  MT entries are append-only, so add a comment under MT-580 correcting step 4's expectation.  The Return Home tooltip should say the setup cannot be used yet and how many things are to be dealt with.  Start's says autonomy cannot start while the setup has errors.

### ADU-C6 - TDU2-C3's claim: its greyed-state assertion can pass without the fix; only the tooltip assertion is sure to be red

| | |
|---|---|
| **Disposition** | Fixed - the claim waits for the triage and asserts Return Home is offered before anything is broken (2c7a5b47); mutation V13 (grey on the triage only) red. |
| **Where** | `test/regression/testAHandSendIsRefusedWhileTheSetupIsBroken.java:201-219`, `:245-249`; `HomeLocomotiveMenu.java:67` |

The item is enabled only when three things hold: `broken == null`, not busy, and `ui.isReturnHomeOffered()` (the Return Home button's enabled state).  The button's state comes from an asynchronous triage that the claim never waits for or asserts.

On the frozen railway one train is away from its home: EN57-947 stands at BottomInner, and its home is BottomMainB.  But if the button is still disabled when the popup is built, the item is greyed without the fix.  Then the mutation that drops `broken == null` from `offered` (keeping the tooltip line) survives, caught by nothing.  The tooltip assertion does make the claim red first, so the disposition's "red first" holds.  The claim also skips when headless (see memory: *green is not "no failures"*).

**Verification request.**
- **Mutation:** at `HomeLocomotiveMenu.java:67`, `boolean offered = !ui.isAutonomyBusy() && ui.isReturnHomeOffered();`, keeping line 77.  Run the class on a display.
- **Proves it:** green.
- **Refutes it:** red at `outcome[4]`.
- **Strengthening:** before breaking the setup, assert `ui.isReturnHomeOffered()` is true.  Send a train away if needed, and wait with `awaitReturnHomeTriage`.  Then the greyed state is the setup's doing.

### ADU-C7 - TDU-C6's "0 lengths count as measures" did not reach the tail doors: the tail question and the standing-train claim still stop at an edge answered 0 end to end, while `Edge.isMeasured` says it is the one question every rule asks

| | |
|---|---|
| **Disposition** | Not a defect under the narrow reading (ADA-A1) - an answered 0 is measured to the gate and the escape only, so the tail doors keep OB-274's reading and agree with each other; `Edge.isMeasured`'s javadoc now names only those two.  Whether to widen is the open question in open-questions.md. |
| **Where** | `TailCrossedPrompt.java:1009` (`back`: `hop.getLength() <= 0 ? -1` ends the road), `:1152` (`reachOf`: covers nothing); `Layout.java:7742` (the standing-train walk: `if (segment.getLength() <= 0) break;`, "THE MEASUREMENT RULE"), `:9255` (`claimUpToWhereTheRailsPart`); against `Edge.java:482` (*"The one question every rule that asks 'is this track measured' puts to an edge"*) |

**Is the reading beyond the gate right?**  Yes, it holds where it was carried.  Taking "0 is a measure" into the release escape and the route in keeps the gate's premise true: the gate cannot let through a railway the escape treats as unmeasured.  Over a 0, a moving train keeps its route locked until the route ends, which is the safe direction.

**The doors it did not reach.**  Two more doors ask the same question and still answer "unmeasured".  The tail question on the diagram offers no sensor beyond an edge whose every square, its sensor included, was answered 0.  The runtime's standing claim also stops there.  They agree with each other, so there is no guard-and-affordance split.

**The other reading.**  Treating 0 as a measure at these doors too would carry the walk over the edge, spending nothing, and claim the track beyond.  That protects more: physically, a train longer than its platform lies across a 0-length link onto the track past it.

**Consequence.**  A train standing with its tail across such a link does not claim the track beyond, and another train can be routed into its tail.  It is mitigated: it needs a whole edge answered 0, sensor square included, and none of the frozen railway's 158 lengths is 0.  The javadoc of `isMeasured` is false as written either way.

**Verification request.**
- **Fixture:** a platform, behind it an edge answered 0 end to end (`answerTileLengthsZero` over its squares and its sensor), then measured track.  Stand a train longer than the platform on it.
- **Ask:** the places `Layout` claims for it, and `TailCrossedPrompt.askAfterPlacement`'s choices.
- **Proves it:** nothing beyond the 0 edge is claimed or offered.

Whether these doors should follow is Adam's call.  The recommended way is the measured reading, for the safety reason above; the alternative is to narrow the javadoc to the three doors it names.

### ADU-C8 - TDA4-C2's warning says every train arriving that way is refused; a train with no length is not

| | |
|---|---|
| **Disposition** | Fixed - *every train with a length* in all eight languages, and the bundle claim looks for it.  Claim 2c7a5b47, fix 1098c9b8. |
| **Where** | `autosetup.ui.checkBerthTrackGivenNoLength` in all eight bundles (English: *"so the length given for that track adds up to 0 and every train arriving that way is refused"*); against `Layout.java:10208` (`whyABerthCannotHoldIt`: `if (loc.getTrainLength() == null \|\| loc.getTrainLength() <= 0) return null;`) |

The berth rule judges only trains with a length.  The recommendation Adam accepted worded it that way: *"X refuses every train with a length"* (open-questions.md before `e269aaec`).  The shipped sentence drops "with a length".

On a railway with some trains still unmeasured, the operator reads that the berth takes nothing and then watches a train stop there.  The half-measured warning beside it is careful here (*"can refuse trains that would otherwise fit"*).

**Fix.**  One clause in eight languages - *"every train with a length arriving that way"* - and extend the claim's bundle check to look for it.

### ADU-C9 - shapes the range left: an orphaned javadoc in a claim class, and a comment the fix made false

| | |
|---|---|
| **Disposition** | Fixed - the orphaned javadoc put back above its test (2c7a5b47), the stale comments (1098c9b8). |
| **Where** | `test/core/testATrainIsPutOnlyWhereItCanStart.java:88-107` (`7dc22256`); `LayoutRightclickAutonomyMenu.java:1031`, `:723-725` |

- **The orphaned javadoc.**  The new test was inserted between `testTheBuildKeepsTheFacingTheSetupRecords`'s javadoc (AUT2-A1, *"A facing only a copy trains may not arrive at holds still stands the train on a copy facing that way"*) and its method.  That javadoc now sits above the new test's, and the AUT2-A1 test has none (see memory: *insert above the javadoc*).  It is the only such insert in the range: every diff hunk was scanned for a `+ /**` straight after a context `*/`.
- **The stale comment.**  `placeableCopies`' comment still says *"this list is drawn at RANDOM"* (1031).
- **A broken line.**  The reflowed comment at 723-725 leaves a line ending mid-sentence (*"and a locomotive put down by hand is"*).

## D - checked, clean

### ADU-D1 - TDU2-C3 is carried out as worded, at every door it reaches

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **The item.**  The right-click Return Home item greys over a setup `autonomyHasErrors` refuses.  Its tooltip is `whyAHandSendIsRefused()`, the sentence `requestReturnToHome` refuses with, so the item and the click cannot disagree.
- **Precedence.**  The ternary at `HomeLocomotiveMenu.java:77` is right-associative: the setup's sentence first, then busy, then the triage.
- **One caller.**  `addReturnHomeItem` has one caller (`LayoutRightclickAutonomyMenu.java:462`).  There is no right-click Execute Timetable item anywhere.
- **The buttons.**  Neither the Return Home nor the Execute Timetable button was changed.  Both still refuse at press time with the setup's sentence (`TrainControlUI.java:23979`, `:26718`), which is "live and explaining" as Start's button is.  behaviour.md section 1 says this.
- **Sentence checks.**  The claim's tooltip assertion is a real claim (ADU-C6 covers the other half).  The sentence choice was checked separately: ADU-C5 is about the manual test's wording, not the code.

### ADU-D2 - TDD5-C1: "follow the train" is carried out at all three doors, and the reading holds

| | |
|---|---|
| **Disposition** | Checked - clean. |

**All three doors, by construction.**  `sameSetup` is now session identity alone.  Its signature changed, so no door could keep the configuration check: the paste (`TrainControlUI.java:8159`), the right-click Place (`LayoutRightclickAutonomyMenu.java:1298`) and the dialog (`GraphLocAssign.java:325`) all compile against it.  `whereTheAnswerGoes` still requires the running railway's copy of that name to hold the same train by reference, from the same side, with the road it had.  So the answer goes only where the new configuration stands the train as it was put.

**The reading holds.**  The question put to Adam defined "follow the train" as *"write it to the railway and the configuration running now"*, for the same train on the same square from the same side.  He chose that over dropping it.  A wider reading, writing wherever the train now stands, would put an answer about one approach onto another.

**What still drops it, and the claim.**  A replaced session still drops the answer (`testAnAnswerAfterTheSetupIsReplacedIsNotWritten` is unchanged).  The log sentence *"... or the setup was reloaded"* is still true of that case.  The claim drives the paste door on a real window, and its named mutation - re-asking the configuration - fails its `assertNotNull`.

### ADU-D3 - TDD-C11: the untick is gone everywhere, and the setting is not defunct

| | |
|---|---|
| **Disposition** | Checked - clean. |

- **Gone from the code.**  `autoLoadOffAfterLegacyImport` is removed with its only call (`AutonomyViewerPanel.java:1182-1188`).  The key `autosetup.ui.autoLoadOffAfterImport` is removed from all eight bundles and nothing in `src/` asks for it; only stale `build/classes` copies remain, which are not source.  The Readme's changelog clause is gone.
- **Not defunct.**  Load Autonomy still decides whether the active configuration resumes at start (`TrainControlUI.java:9721-9737`), so keeping the box was right.
- **The claim.**  The claim's census finds the one `.importLegacy(` door, and its scan window covers the lines where the untick stood.
- **The manual tests.**  MT-503 reads `superseded`, pointing at MT-582.

### ADU-D4 - the message bundles

| | |
|---|---|
| **Disposition** | Checked - clean. |

All eight bundles have 1829 keys, the same set in each, pure ASCII, and no placeholder set differs between languages across all keys.
- `checkRunInShorterThanThePlatformRefused` carries `{0}{2}{3}{4}` in every language.
- `checkBerthTrackGivenNoLength` carries `{0}` in every language.
- Neither holds a straight apostrophe (French and Italian use `’`).

MT-583's quoted English sentence matches the bundle.  `autolayout.errorDestinationBlockedByPoint` (*"{0} is not available while {1} is occupied"*) says nothing about a destination, so it is true of a square trains only pass (OB-295) in all eight languages.

### ADU-D5 - the findings list takes the third number, and nothing in the UI keys on the changed notices

| | |
|---|---|
| **Disposition** | Checked - clean. |

Both renderers of a finding's message key pass `getThird()` as `{4}` (`AutonomyEditorPanel.java:9564`, `AutonomyViewerPanel.java:1623`).  No other gui code reads a finding key; `grep getMessageKey()` over `src/` shows only these two.  No gui code refers to the run-in, half-measured or new berth keys.

TDA4-C2's new WARNING now appears in the viewer's list and the diagram strip's count, which show errors and warnings and drop notices.  That is the visible change Adam asked for (*"as a warning"*).  Nothing gates on a warning.

TDA-C10's figure stays at notice grade, as its berth sibling (*"refused here"*) always was; the ruling asked for the figure, not a grade.

### ADU-D6 - no other placement door draws at random, and the Atomic Routes UI doors ask `isMeasured`

| | |
|---|---|
| **Disposition** | Checked - clean. |

**No random draw left.**  `grep "new Random()"` over `gui/` and `automationui/` finds only a comment.  The four placement doors are the paste, the dialog, the editor's Place and the right-click Place.

**The Atomic Routes doors.**  The checkbox refusal (`TrainControlUI.java:5949`), the load door's log (`:6107`) and `unmeasuredTrackAutonomyRunsOver` (`:6017`) all go through `Layout.unmeasuredTrackThatCouldBeReleased`, which asks `Edge.isMeasured` (`Layout.java:9602`).  So *"Measure them"* is now said only of track with no answer, and Mass Assign and Unmeasured Track no longer contradict it.

### ADU-D7 - OB-298 leaves no stale list in the UI

| | |
|---|---|
| **Disposition** | Checked - clean. |

`GraphReducer`'s getters wrap the current field in a new unmodifiable view on each call (`:413-434`, `:894`).  No gui class keeps one across a rebuild: every `getReducer().getPoints()` / `getEdges()` use in `AutonomyEditorPanel` is read at the call.  So swapping the lists whole cannot leave a window reading a railway it has already let go.

### ADU-D8 - OB-295's refusal and its explanation ask one helper

| | |
|---|---|
| **Disposition** | Checked - clean. |

`isPathClear` (`Layout.java:2934`) and the why-window (`:5776`) both ask `heldBackAlong(path, loc)`, and name the held square and the watched square from its answer.  The hand door and Why not Moving? therefore cannot disagree about a square trains only pass.  Behaviour on the railway (every tier, the planner) is the autonomy lane's to verify.
