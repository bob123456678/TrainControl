# Regression validation, round 3: round 2's REG fixes and the range since, seen by a user upgrading from v2.8.1

**Status:** open

**Prefix:** `REG3`

**Reviewed:** branch `autonomy-diagram-r0` at `2f4448b6`, 2026-09-23.  Baseline: REG2 at `08a47bdd` and the nine commits since (`git log 08a47bdd..2f4448b6`); `master` at `5f0a75e3` (v2.8.1) for "what did 2.8.1 do".

**Method:** read-only.  `git show` of all nine commits; the REG2 dispositions walked against `8370abb1` / `4132d260` and the two claims in `8346be65` read against their pre-fix code.  Sweeps: every caller of `parseRoutesFromJson`, `MarklinRoute.fromJSON`, `executeAutoRoute` and `whyAutonomyWillNotStart` (src and test); every writer of `FACING` that places a train (the legacy import, the doors, the build's `placementCopy`, the session's `copyFacing`); `barredArrivals` from store to builder; `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack` and every `setAtomicRoutes` in `src/`; the JSON window's mount (`mountAutonomyControls`).  `91daff27` checked by a read-only Python pass over the eight bundles at `5f0a75e3`, `91daff27^` and `91daff27` (keys parsed, `\uXXXX` decoded, values compared and folded to ASCII per language).  Added lines of every `.md` and `.java` file in the range scanned for words and sentences fused by a dropped space.  Adam's frozen railway read in `test/operator_layout/` and `test/layouts/live-snapshot/` (setup, configuration, the 2.8.1 `autonomy_legacy/autonomy.json`) and `test/baseline/configuration.json`; `cs2_sample_layout/` not opened.  Nothing compiled, run or written except this file.

| | |
|---|---|
| **A** | none |
| **B** | none |
| **C1** | the legacy import still guesses copy zero's facing, and since `8370abb1` honours it onto a barred copy: an imported train can be stood where autonomy will not start it, while the import's log says "just run them" (GUI2-B1's shape, through the import door) |
| **C2** | `AutomationAPI.md:451` (4132d260) says an unmeasured edge can no longer cause an instant unlock; the gate that makes that true is in the window, and a program driving `Layout` - the page's audience - still gets instant unlocks |
| **C3** | `AutomationAPI.md:221-231` still gives paste -> Validate Configuration -> Start as the way in; at v3.0.0 that tab exists only where there is no setup - a Central Station layout - and Start is refused there |
| **C4** | twelve words and sentences fused by a dropped space in the round's text edits (`Readme.md` x4, `AutomationAPI.md` x5, `behaviour.md`, a javadoc, a test comment) |
| **C5** | comments the REG2-C2 and REG2-C6 fixes left behind: "Four answers" / "THREE reasons" (now five / four), and "`auto` is read and then overridden" (it is now overwritten unread) |
| **D** | REG3-D1 to REG3-D14 |

## A - high

None.

## B - medium

None.  Weighed: C1.  It is GUI2-B1's consequence (graded B there) reached through a door that fix did not cover - but only where a station already had an arrival side barred when the file was imported, the train's facing was never known anyway, the refusal is explained on hover, and the Facing menu is a way past.  By population, C.

## C - low

### REG3-C1 - the legacy import's copy-zero facing is now honoured onto a barred copy: an imported train can be stood where autonomy will not start it, and the log says "just run them"

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first): the import guesses the first facing trains may arrive in |
| **Where** | `AutonomySession.java:789-803` (the import's invented facing); `AutonomyBuilder.java:727-777` (`placementCopy`, the new fallback at `:759-776`), `:790-803` (`startableCopy`); `messages.properties:1486` (`autosetup.ui.facingsGuessed`); `AutonomyViewerPanel.java:1234-1236` |

This is the task's question - what a 2.8.1 file's import does with a train whose facing only a barred copy holds - and the answer is that the import can *create* that train.

**The guess.**  The old file has no facing, so the import invents one, and it is copy zero's:

    AutonomySession.java:793-803
        if (getFacing(tile) == null)
        {
            java.util.List<Side> ways = new ArrayList<>(facingsFor(tile).values());
            if (!ways.isEmpty()) { setFacing(tile, ways.get(0)); result.facingsInvented++; }
        }

`facingsFor` is every copy in emission order (`StationIndex.java:81-82`, "so 'the first copy' means the same thing to everything that asks"), barred or not - `placeableFacingsFor` exists at `:1524` precisely because it is unfiltered.  Copies are emitted N, E, S, W, so `ways.get(0)` is the facing of the copy arriving by the north (or east) side - the very guess GUI2-B1 took out of the build ("copy zero ... at BottomMainA and BottomMainPost, a copy that is no station").  The comment above it says "Not legality-checked ... A copy that cannot be left is reported by the checks" - but `facingsThatCannotBeHeld` asks `facingChoices`, which is `facingsFor` again (`:6175`), so a barred-only facing is never reported.

**Where the barring comes from.**  `barredArrivals` is store-level track data (`AutonomyCompanionStore.java:93`), not per configuration - `importLegacyGraph` says so: "names, stations, lengths, directions - belongs to the TRACK" (`AutonomyViewerPanel.java:1128-1133`).  So any user who has set **Trains May Arrive...** on a station before importing their 2.8.1 file - or who imports into a new configuration on such a layout, or re-imports after Clear Locomotives - imports onto barred squares.

**What changed in the range.**  At `08a47bdd` `placementCopy` fell through, when no copy trains may arrive at faced the recorded way, to one they may: the guess was silently corrected and the train ran.  `8370abb1` added "A COPY THAT FACES IT ANYWAY" (`:759-776`), rightly for a facing somebody *knows* (TDY2-A1: the copy is the direction).  For the import's facing nobody knows anything, and the train is now stood on the barred copy - built `station: false` (`AutonomyBuilder.java:990`) - and `explainCannotStart` answers *"It is standing on {0}, which is not a station, so autonomy will not start it from there."* (`Layout.java:4944`).  GUI2-B1's fix (`startableCopy`, "the one guess autonomy can start") is never reached, because the import wrote a facing.  The log line the import writes for exactly these trains then says:

    messages.properties:1486
        {0} trains had the way they face chosen for them ... The first one that fits was used.  Check them on the
        diagram, or just run them - the first journey records which way each one really faces.

"Fits" implies a check that is not made, and "just run them" is false for these: autonomy will never make the first journey.

**Concretely, on Adam's railway.**  BottomMainA (`5:20,12`) has arrivals from the east barred; its copy zero is the east-arrival copy, "BottomMainA (westbound)", facing W, `station: false` - the fixture `testATrainIsPutOnlyWhereItCanStart` already uses.  A legacy point on its sensor carrying a locomotive gets facing W and is stood there.  (Adam's own frozen 2.8.1 file does not bite: its four placements are at TopMainR1, TopMainR1Inter, TopMainR2Inter and Tunnel; the two `Inter` squares have E barred but their copy zero is the N-arrival copy, which trains may arrive at.  So this is a population finding, not his railway's.)

**2.8.1 parity.**  At 2.8.1 the train stood on the file's point and autonomy started it.  A fresh import onto a layout with no barred side is unaffected (every copy passes `arrivalAllowed`, so the new fallback is unreachable - REG3-D9).

**Verification request (needs execution; core test).**  In `core.testATrainIsPutOnlyWhereItCanStart` (live-snapshot sandbox): `session.placeLocomotive(mainA, null)` (clears the loc and its facing), then `session.importLegacy(new JSONObject("{points:[{name:'P', s88:<BottomMainA's sensor>, loc:{name:PROBE}}]}"))` - confirm first that `result.placed == 1` (the frozen legacy file gives BottomMainA s88 9; if that sensor is shared the import lists it as unmatched - use any non-turning square whose barred side sorts first).  Then `build(session)` and `standingOn(...)`.  **Proves:** `session.getFacing(mainA) == Side.W`, the Point is not `isDestination()`, and `explainCannotStart(probe)` is `autolayout.why.startNotStation`.  **Refutes:** a destination copy.  The same probe at `08a47bdd` stands the train on the eastbound copy - the control that shows `8370abb1` moved it.

**Suggested fix.**  Let the import guess what the build would guess with no facing: choose among `facingsFor(tile)` only copies whose arrival side is not in `getBarredArrivals(tile)`, the plain one first (`startableCopy`'s order) - or write no facing and let `startableCopy` choose, with the capture recording it.  Either keeps TDY2-A1's rule intact for facings somebody set.  And "The first one that fits" should say what it now means.

### REG3-C2 - `AutomationAPI.md` now promises the programmatic route something only the window enforces: "an unmeasured edge can no longer cause an instant unlock"

| | |
|---|---|
| **Disposition** | Fixed - 031a7ddb |
| **Where** | `AutomationAPI.md:451` (written in `4132d260` for DCN2-C3); `TrainControlUI.java:6129-6167` (the gate); `Layout.java:10605-10608` (`setAtomicRoutes`), `:4900-4920` (`tailHasProvablyPassed`), `:8304-8313` |

The page is "Automating your layout: the programmatic route" - "driving TrainControl from Java" with `Layout` (`AutomationAPI.md:1-4`, `:17-19`).  Its non-atomic section now reads:

    AutomationAPI.md:451
        Edges will only be unlocked once the cumulative traversed edge length exceeds the current train's length.  Since
        v3.0.0 `atomicRoutes` stays on while any edge autonomy runs over, or any train, has no length, so an unmeasured
        edge can no longer cause an instant unlock.

True in the application: `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack` forces the setting at the parse doors and the five dispatch doors.  But that method is on `TrainControlUI`, and it is the only writer of `setAtomicRoutes(true)` in `src/` (`git grep "setAtomicRoutes("`: `TrainControlUI.java:6154`, the checkbox at `:23047`, and `Layout.fromJSON`'s copy of the file's value).  `Layout.setAtomicRoutes` is a bare setter, and `Layout` itself never asks `unmeasuredTrackThatCouldBeReleased` or `trainsWithNoLength` - they are queries only the window calls.  So a program that does `layout.setAtomicRoutes(false)` over unmeasured track gets what the removed sentence said: `pathIsUnmeasured` is true for a path with no length anywhere (`:8304-8313`) and `tailHasProvablyPassed` then returns true at once (`:4915`); and a zero-length train passes `behind >= 0` on the first ask.  The old sentence ("A length value of 0 ... will result in instant unlocks") was true for this reader; the new one tells them the opposite, on the one setting whose failure is two trains on one piece of track.

DCN2-C3's own wording ("since the Atomic Routes gate ... no instant unlock can happen") was about the application; the fix carried it onto a page whose reader has no window.  **Verification request:** none needed - `git grep -n "setAtomicRoutes(" src` shows the writers.  To see it: a `Layout` built in code with every edge length 0 and `setAtomicRoutes(false)`, one path run - the first edge is released before the train arrives.  **Suggested fix:** "In the application, since v3.0.0 Atomic Routes is kept on while ... A program driving `Layout` directly is not stopped: with `atomicRoutes` false, an unmeasured path or a train with no length still unlocks each edge as soon as it is passed."  (Or, Adam's call, move the gate into `Layout` so the sentence is true for both.)

### REG3-C3 - `AutomationAPI.md`'s "Running ... via TrainControl UI" still gives paste, Validate, Start as the way in; at v3.0.0 that tab exists only where autonomy cannot start

| | |
|---|---|
| **Disposition** | Fixed - 031a7ddb |
| **Where** | `AutomationAPI.md:221-231` (`:228` edited in `4132d260`); `TrainControlUI.java:3947-3998` (`mountAutonomyControls`), `:5931-5936` (`refuseAutonomyStartWhileBroken`) |

`4132d260` corrected the button's name at `:228` ("Validate Configuration"), which re-dates the paragraph as current.  It says:

    AutomationAPI.md:223-231
        ... the logic above can be expressed in a JSON format and executed via the TrainControl UI's "Autonomy" tab ...
        To get started, paste the JSON in TrainControl's "Autonomy" tab, then click on "Validate Configuration" ...
        If there are no errors, autonomous operation can be activated by clicking on "Start Autonomous Operation".

At HEAD the JSON tab, the Validate / Initialize New Configuration / Load JSON buttons are mounted only when `getAutonomySession()` is null (`:3947-3968`), i.e. when the layout is not stored on this computer; wherever a setup can exist the tab is removed and the buttons hidden (`:3983-3995`).  And where the tab does exist, pressing Start is refused first thing - `isRemoteLayout()` -> "Autonomy needs a layout stored on this computer" (`:5931-5936`; since REG2-C2 the greyed menu item says so too).  So a 2.8.1 JSON user following the page finds no tab on a downloaded layout, and on a Central Station layout finds the tab, validates, and cannot start.  The v3.0.0 way in for their file is **Import** on the autonomy configuration menu (`Readme.md:128`, `:374`), which this page does not mention; the deprecation banner at `:6-11` says the *graph window* was removed, not that the paste tab went with the setup.

**Verification request:** none; reading.  **Suggested fix:** a banner over `:221` in the style of `:369`: "Since v3.0.0 this tab is shown only for a layout read from the Central Station, where autonomy cannot be started.  Download the layout (Layouts -> Download Central Station Layout Files) and import your autonomy.json from the autonomy menu instead."

### REG3-C4 - twelve fused words and sentences in the round's text edits

| | |
|---|---|
| **Disposition** | Fixed - 031a7ddb, and the edit helper that made the joins |
| **Where** | as listed (HEAD line numbers) |

Every one is a join at the boundary of an edit - the edit dropped the space it replaced against:

- `Readme.md:128` "your layout**as** a graph" (4132d260) - the sentence also lost its subject in the rewrite: "Set up on the track diagram (...) - an older JSON configuration file can be imported ... - represent your layout as a graph and enable complete automation", and "represent your layout as a graph" is what v3.0.0's own first changelog line says is gone.
- `Readme.md:374` "as before.An autonomy.json" (8370abb1)
- `Readme.md:379` "and**can** clear" (4132d260)
- `Readme.md:380` "driven away.To keep" (4132d260)
- `AutomationAPI.md:10` "derived from it.The settings"; `:228` "\"Validate Configuration\".Any errors"; `:384` "a point you**only** ever drive"; `:486` "in either mode.Once you are finished"; `:493` "autonomy editor.Note that" (all 4132d260)
- `docs/reference/behaviour.md` (`:1002`) "share one prompt:the number box" (4132d260)
- `PositionAwareJFrame.java:199` "can actually occupy**on** the screen" (4132d260 - the re-indent of REG2-D9's cosmetic note)
- `test/core/testAutoLayout.java:2253` "release them again.The comment" (4132d260)

Found by scanning the added lines of the range for a lower-case letter or closing punctuation followed directly by `.`/`:` and a capital, and for new tokens that split into two words of the replaced line; the twelve above are all the hits.  The `Readme.md` four are in the changelog Adam's users read.  **Verification request:** none; `git grep -n -E "as before\.An|layoutas|andcan|driven away\.To|it\.The settings|youonly|either mode\.Once|editor\.Note|prompt:the|occupyon|again\.The comment" 2f4448b6`.  **Suggested fix:** the spaces; and whatever tool made the round's text edits is worth a look - one pass produced all of them.

### REG3-C5 - comments the REG2-C2 and REG2-C6 fixes made incomplete

| | |
|---|---|
| **Disposition** | Fixed - 031a7ddb |
| **Where** | `TrainControlUI.java:24566-24571` (`whyAutonomyWillNotStart` javadoc); `LayoutRightclickAutonomyMenu.java:429`; `MarklinControlStation.java:4163-4166` (`importRoutes`) |

- The javadoc still reads "**Four answers, in the order they have to be asked**" and lists four; since `8370abb1` there are five, and the new one - over a Central Station layout - is asked FIRST.  The reason is in a body comment, but the list a reader consults is the javadoc's.
- The menu's tooltip comment: "And say which of the **THREE** reasons it is (V32-C1)" - four, now.
- `importRoutes`: "A file's `auto` flag is therefore **read and then overridden**".  Since REG2-C6's fix it is overwritten before anything reads it (`:4145`, before `fromJSON`).  This is the sentence whoever acts on REG2-C7 will read, and REG2-C7's suggested fix (log which routes were saved armed) now has to capture the value in `parseRoutesFromJson` before `:4145`, since `importRoutes` receives only disarmed routes.

Optional, same fix: the greyed Start's new tooltip, `autosetup.ui.menuNoSetupPossible` ("Autonomy needs a layout stored on this computer"), names no remedy; the bundle already has the tooltip form that does - `autosetup.ui.tooltipNeedsLocalLayout` ("... Use Layouts -> Download to make one ...").  **Verification request:** none; reading.

## D - not defects (checks that came back clean)

### REG3-D1 - REG2-C6 verified: an imported route is built unarmed, and nothing else reads the file's `auto`

`parseRoutesFromJson` now does `entry.put("auto", false)` before `MarklinRoute.fromJSON` (`MarklinControlStation.java:4143-4148`), so the full constructor's `executeAutoRoute()` (`MarklinRoute.java:144`, `:165`) starts no thread and `route.running` (`:182-185`) is never logged.  The `JSONObject` is local to the method; nothing reads it afterwards.  Callers of `parseRoutesFromJson`: `importRoutes` in `src/`, and two tests - `testRouteRoundTrip:134` (round-trips commands; `enabled` unasked) and `testParseCS3Routes:44` (compares with `equals`, which includes `enabled`, `MarklinRoute.java:1416`) - both unchanged in outcome because the old code's `disable()` already left `enabled` false, and `test/TC_routes.json` has no armed route anyway.  No other door builds a `MarklinRoute` from JSON (the autonomy file's `activateRouteIDs` are ids, applied by `applyAutonomyRouteActivations`, which does `enable()` then `executeAutoRoute()` itself, `MarklinControlStation.java:1127-1133`; a hand enable goes through `writeRouteEnabledState` -> `editRoute`, which constructs anew).  Without the parked monitor the old code left, a later enable starts a fresh one instead of reusing it - no double-firing either way.  One loosening, harmless: a route entry with no `auto`, or a non-boolean one, used to fail the whole import at `getBoolean("auto")` (at 2.8.1 too) and now imports; entries missing `name` or `id` still fail before anything is deleted (`testFailedRouteImportLeavesExistingRoutesIntact`'s `{'name': 'incomplete'}` throws at `getInt("id")`).  The claim (`testAnImportSaysItsRoutesAreOff`, the `assertFalse` added in `8346be65`) fails at `8346be65`: route 0 is saved `auto: true` with s88 8870, so the old parse started a monitor that logs synchronously (`logf` -> `log`, no queue) - but from its own thread, so the red depends on that thread reaching its first statement before the assertion (it has the whole delete-and-add loop to do it in, and the commit records it seen red).  A deterministic form, if wanted: assert no live thread named `route monitor 9830 REG-B3 probe 0` (`MarklinRoute.java:251`) after the import - the old code's parked thread is alive until the sensor pulses.

### REG3-D2 - REG2-C2 verified at every caller of `whyAutonomyWillNotStart`

Three callers (`git grep`): the greyed Start's tooltip (`LayoutRightclickAutonomyMenu.java:444`) - now the Central Station sentence, the same one the guard shows on press; `refuseAutonomyStartWhileBroken` (`TrainControlUI.java:5961`) - the new first line is unreachable there, the guard has already answered `isRemoteLayout()` at `:5931`; and the scripting door `requestStartAutonomy` (`:24671`) - over a Central Station layout with the button disabled it now throws the layout reason instead of "wait for the trains", which is the guard's first question too.  The source-shape guards still hold (`testErrorsStopTheSetupRunning`: the wrapper still names `autonomyErrorCount()`, `blockingProblemCount()`, `autonomyHasErrors()`; `testTheRefusalToStartSaysWhichThing.testNoDoorWordsItItself`: the wait-for-trains key still appears once, in the rule).  The claim at `8346be65`: with the layout path emptied the session is null, so the old wrapper returned `whyAutonomyWillNotStart(0, 0, false)` = wait for trains, and the new `assertEquals` fails for the stated reason.  What the fix left is REG3-C5.

### REG3-D3 - REG2-C1 verified

`Automation.md:28` now says "stored on this computer" and names **Layouts -> Download Central Station Layout Files** - both real (`ui.main.toolbar.layouts`, `ui.main.toolbar.downloadCSLayout`, the item is on `layoutMenu`, `TrainControlUI.java:18934`).  "A diagram read from the Central Station ... cannot carry an autonomy setup" matches `mountAutonomyControls`' null-session branch and the guard.  The REG-C3 row in the store now names `8370abb1`.  Not re-raised: the changelog still has no line saying autonomy stopped running on a Central Station diagram - RGN-A2's half, closed; the Download item is its mention.

### REG3-D4 - REG2-C4 verified

"untick Can Be Chosen in Full Autonomy on its right-click menu" names the real control (`autosetup.ui.menuAutoDestination`, only on the editor's station menu, `AutonomyEditorPanel.java:1494`), and "its right-click menu" is how the neighbouring changelog lines name the same menu.  "leave it switched on" does not tell a user whose stations the import switched off to switch them back on - that is REG-B1's import question, deferred.

### REG3-D5 - REG2-C5 verified, with its two siblings

`AutomationAPI.md:459`, the Orange legend (`:384`) and the timetable note (`:486`) now state the 2026-09-06 rule and the start exemption, which `isPathClear` still grants (REG2-D3).  Harmless, as REG2-C4 said of the changelog: "Since v3.0.0 a path chosen by hand may not pass through ... one either" dates the pass-through rule to v3.0.0, while 2.8.1 already refused it; `:384`'s "(Until then a route you picked could finish on a disabled point ...)" gets the history right.

### REG3-D6 - REG2-C3's changelog line is true; the rest is Adam's, stated as a decision

`Readme.md:374` against the arm (`TrainControlUI.java:9696-9716`) and `resumesFromJsonAtStart` (`:9744-9751`): the configuration loads unless the box is unticked; the JSON graph loads only where the key was stored by the menu item and there is text.  "Ticked by hand" is accurate.  That a box shown ticked has to be unticked and ticked to be "ticked by hand" is REG2-C3's open half, recorded in the store as `Open - Adam's decision` with the question stated.

### REG3-D7 - REG2-C7's disposition is accurate

Still open for Adam, with the question stated; the store row matches.  Implementation note is in REG3-C5 (the value is now overwritten before it is read).

### REG3-D8 - `91daff27` changes no value a 2.8.1 user had

Parsed all seven translated bundles at `5f0a75e3`, `91daff27^` and `91daff27`: 506 values changed (da 95, de 79, es 88, fr 90, it 58, nl 4, pl 92), no key added or removed; **0** of the 506 had, at `91daff27^`, the value `master` shipped - every one was added or changed since v2.8.1.  Each new value folded to ASCII (de ae/oe/ue/ss, da ae/oe/aa, the rest by stripping combining marks, Polish l) equals the old value folded the same way - only diacritics changed; no raw non-ASCII byte in any bundle (the Java 8 rule); no ASCII apostrophe introduced for `MessageFormat` to eat.  `it`'s "se stesso" -> "sé stesso" is the accent too (both spellings are accepted).

### REG3-D9 - the facing change, for everyone else who upgrades

- **The JSON path** (a 2.8.1 user still on `autonomy.json`, which includes every Central Station layout): unaffected - `Layout.fromJSON` builds Points, there are no copies, and `placementCopy` / `copyFacing` are not asked.
- **A fresh import**: unaffected - with no side barred, `arrivalAllowed` is true for every copy (`AutonomyBuilder.java:420-427`), so `placementCopy`'s first loop always finds the facing and the new fallback is unreachable.  The exception is REG3-C1.
- **A setup saved before today**: gets what it got before `1c855483` - `git diff 1c855483^ 8370abb1 -- test/baseline/configuration.json` is empty, as the commit says.  Only a setup captured between `1c855483` and `8370abb1` (hours, this branch) can carry a facing the silent turn wrote.

### REG3-D10 - AUT2-C1's claim removal leaks nothing, on either path

`configureAndLockPath`'s refusals (`Layout.java:3690-3830`): already under way and not clear both return before `takingPath.put` (`:3711`); a configure failure, a failed actuation check and a throw each `remove(loc)` before leaving.  So `executePathInternal` no longer removing anything cannot leave a claim behind - including for a JSON-path user, whose `Layout` is the same class (`takingPath` is new since 2.8.1, `git show master:...Layout.java` has none).

### REG3-D11 - `4132d260` changes no code

Its `src/` and `test/` hunks are comments and javadoc except one test failure message (`testAutoLayout`, "reads releasedEarly").

### REG3-D12 - TDY2-C4 (`9c85db2a`, `1facc0c2`) owes a 2.8.1 user nothing

Homes held to a facing are new since 2.8.1.  The editor now asks exactly when `knownHomeFacing` is null, and `homeFacingOf` declines to answer from the setup once the running layout has the train anywhere (`AutonomySession.java:7457-7482`).

### REG3-D13 - the round's catalogue matches REG2

Every REG2 id has a row (`findings.tsv:1885-1903`), C1/C2/C4/C5/C6 Closed with the commits the document names, C3 and C7 `Open - Adam's decision`.

### REG3-D14 - the other round-2 text claims checked

`AutomationAPI.md:10` (the graph window was removed in v3.0.0 - GraphStream is gone, `Readme.md:457`); "Validate Configuration" is the button's label (`ui.main.validateConfigOpenGraphUI`); the routing table's "Least recently visited" matches `autolayout.ui.pathPreferenceLEAST_RECENTLY_VISITED`; "Load Autonomy" is the item's label (`ui.main.toolbar.loadAutonomy`).

## What this pass did not cover

- **Execution.**  Nothing run.  REG3-C1 has a runnable probe; C2's truth is settled by `git grep`, and a probe is sketched.
- **A lead for the TDY/AUT lanes, not judged here:** `homeCopy` (`AutonomyBuilder.java:680-714`) still refuses a barred-only home facing and falls back to a copy trains may arrive at, while `placementCopy` now honours the same facing for a placement.  A train standing on a barred copy (as `8370abb1` now allows) and homed there gets a home facing (`homeFacingOf` reads the copy it stands on) that the build puts on the opposite-facing copy.  Homes are new since 2.8.1, so it is outside this lane's question.
- **The round's AUT/TDY/GUI fixes on their own terms** (`placementCopy`'s rule, the tail walk, the claim tests) - other validators' lanes; read here only for what a 2.8.1 user meets.
- **`Automation.md` says nothing about importing an older `autonomy.json`** - the user guide's only upgrading instruction is the prerequisite; the changelog carries the import.  Not new in this range.
