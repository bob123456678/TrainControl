# User interface round 2 fixes - validation

**Status:** open

**Prefix:** `GUI3`

**Reviewed:** branch `autonomy-diagram-r0` at `2f4448b6`, 2026-09-23.  Baseline: the GUI2 validation at `08a47bdd` (`docs/reviews-2026-09-23/GUI2.md`); range `08a47bdd..2f4448b6` (9 commits).

**Method:** Read every commit in the range (`264f2a73`, `8346be65`, `8370abb1`, `4132d260`, `b53439dc`, `9c85db2a`, `1facc0c2`, `91daff27`, `2f4448b6`) and, for the UI, the code around each change at HEAD.  For `8370abb1` walked every caller of `copyFacing` / `moveOntoFacingCopy` (paste, Place Locomotive dialog, Facing menu, throttle direction-follow, idle drain after a turn) and every surface that shows a train standing on a non-station copy: `buildFacingMenu` and its tick, `facingsThatCannotBeHeld`, `Layout.explainCannotStart` and both Why Not Moving? tools (`AutonomyEditorPanel.composeWhy`, `AutoLocomotiveStatus`), the station caption (`occupantsAt`/`speakerAt`, `facingArrowOf`), the diagram's right-click menu, the load-time log line.  Read the four claims of `8346be65` in `testATrainIsPutOnlyWhereItCanStart` against `8370abb1^`.  For `264f2a73` walked every road into `openLayoutEditor` and every writer of the Edit button's enabled state.  For `1facc0c2` walked `knownHomeFacing`/`homeFacingOf` for a square that is not split, a train standing here, standing elsewhere, not on the railway, and standing here on a barred copy, and followed the saved facing into `AutonomyBuilder.homeCopy`.  For `91daff27`: read-only Python piped through stdin (no files written) over all seven bundles before and after - placeholders, ASCII apostrophes, `\n` and every other escape, word counts, and "maps back to the old value by the language's own transliteration" (all 506 clean); then every changed word pair listed per language (all seven read in full as word pairs), and every pair whose unaccented form is also a word (Italian e/li/la/se/ne, Spanish esta/si/el/aun/que/como/donde, French a/la/ou and the participles, Dutch een, Polish ja/ta/te/ze/pol and the case endings) read in its sentence; and the unaccented forms still left in values changed since `master`.  Nothing was run that starts Java; every finding below rests on reading and says what execution would settle it.

## A - high

None found.

## B - medium

None found.

## C - low

### GUI3-C1 - the sentence `8370abb1` relies on to say why the train will not start names the builder's copy, calls a station "not a station", offers no remedy - and on Manual Path Type the editor's Why Not Moving? says "cannot be sent anywhere" for a train the right-click menu will send

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first) |
| **Where** | `Layout.java:4944` (`explainCannotStart`: `I18n.f("autolayout.why.startNotStation", at.getName())`); `AutonomyEditorPanel.java:7888-7894` (`composeWhy` returns it before the `byHand` branch); `AutoLocomotiveStatus.java:727-741`; `messages*.properties` `autolayout.why.startNotStation` |

`8370abb1` chose GUI2-A1's option (a): a train turned to face the way only a barred copy faces now stands on that copy, and the commit, the claim class and `copyFacing`'s javadoc all justify it the same way - *"autonomy refuses to start it there and says why"*, *"which is the truth"*.  What it says, for 75 407 DB reversed on the throttle at BottomMainA on the frozen railway, is:

> 75 407 DB is standing at BottomMainA, and autonomy has nowhere to send it.
> It is standing on BottomMainA (westbound), which is not a station, so autonomy will not start it from there.

Three things are wrong with that as the operator's only explanation of a state two ordinary gestures now create on purpose (the Facing menu and a reversal on the throttle; three of the four placed trains in `configuration-Main.json` stand on squares with a barred side):

1. **It shows the copy name.**  `explainCannotStart` passes `at.getName()`; every other sentence in `Layout` passes `placeNameOf(...)`, whose javadoc quotes Adam (2026-09-13): *"the whole copy thing needs to be masked from the user"*.  The line above it, from `AutoLocomotiveStatus.stationName`, already says "BottomMainA".
2. **It is false about the square.**  BottomMainA is a station - it is in `setup.json`'s `stations`, captioned, and the train was standing on it as a station a moment earlier.  What is true is that trains may not arrive from the east there, and the train now faces as if it had.  Masking the name (1) without changing the sentence makes it plainly contradictory ("standing at BottomMainA ... standing on BottomMainA, which is not a station").
3. **No remedy.**  The two ways out - turn the train round (a throttle reversal now moves it back onto the eastbound copy), or allow arrivals from the east in the editor - are nowhere.  (`error-must-have-a-remedy`: a refusal the operator cannot act on is the rule being wrong.)

**And the Manual tier.**  `composeWhy(..., byHand)` asks `explainCannotStart` first and returns `autosetup.ui.whyCannotStart` - *"<b>{0}</b> cannot be sent anywhere: {1}"* - whatever Path Type says.  By hand the train CAN be sent: `Layout.isPathClear` refuses a non-station start only `if (this.isAutoRunning() && !e.getStart().isDestination() ...)` (`Layout.java:2438`), `getPossiblePaths(loc, true)` starts from any Point holding the train, and the diagram's right-click menu gathers paths for a locomotive on a non-destination copy on purpose (`LayoutRightclickAutonomyMenu.java:228, 487` - the W21-B3 comment: *"a train can still be placed on it by hand"*).  So on Manual the tool answers with an autonomy refusal the operator is not subject to - the shape Adam reported as OB-225/MT-439 (*"in manual mode, I still get reasons like 'tunnellongpark will never be chosen in autonomy'"*), reached by the one door `7a484ea9` did not make tier-aware.  `startInactive` is the same shape (inactive starts are also refused only under `isAutoRunning()`, `Layout.java:2429`); `paused` I did not trace.

Pre-existing text; what is new is that `8370abb1` made this state a designed outcome and cites the sentence as its safety net.  Nothing is driven wrongly.  Reading only.

**Verification request (needs execution; core test, no window).**  In `core.testATrainIsPutOnlyWhereItCanStart`'s fixture, after `testTheFacingMenuMovesTheTrainOntoACopyFacingThatWay`'s steps (`setFacingAndMove(mainA, Side.W)` with a running layout): `String why = running.explainCannotStart(model.getLocByName(PROBE))`.  **Proves (1)-(2):** `why` contains `"(westbound)"` and the startNotStation text.  **Proves the Manual half:** `running.getPossiblePaths(model.getLocByName(PROBE), true)` is non-empty (paths a hand send accepts) while `why` is non-null.  **Refutes:** `getPossiblePaths` is empty, or a hand `executePath` along one of them is refused for the start.

**Suggested fix (Adam's wording to choose).**  A reason of its own for a train on a copy trains may not arrive at, named by place and saying what to do - e.g. *"It is facing the way trains may not arrive at {0}, so autonomy will not start it there.  Turn it round, or allow arrivals from that side."* - and in `composeWhy`, skip `explainCannotStart`'s autonomy-only reasons when `byHand`.

### GUI3-C2 - a train homed while it stands on a barred copy is saved with a home facing no train can be brought back in: `writeHome`'s "a copy a train stands on, so no impossible facing is saved" stopped being true with `8370abb1`

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first) |
| **Where** | `AutonomySession.java:7411-7414` (`writeHome`), `:7425-7485` (`knownHomeFacing`, `homeFacingOf`); against `:7356-7363` (`setHome(tile, loc, facing)` asks `homeFacingsFor`) and `AutonomyBuilder.java:680-712` (`homeCopy`), `:1480-1490` (`homeFacingsAt`); door `AutonomyEditorPanel.java:5101-5117` |

OB-282's two writers of `HOME_FACING` guard differently.  The operator's answer is filtered - `setHome(tile, loc, facing)` writes it only `if (... homeFacingsFor(tile).contains(facing))`, Adam's *"we shouldn't allow an impossible facing to be saved"*.  The facing of the train standing there is not filtered, and `writeHome` says why it need not be: *"Only the facing the train standing here has, which is a copy a train stands on, so no impossible facing is saved"*.  That premise held while every door stood trains only on copies trains may arrive at (GUI-B1's first repair).  `8370abb1` now stands a train on a barred copy on purpose (the Facing menu, the throttle, and a load of a facing only such a copy holds), so a copy a train stands on can be one `homeFacingsAt` excludes (`if (node.arrival != null && arrivalAllowed(node))`).

**Scenario, frozen railway.**  75 407 DB at BottomMainA, reversed on the throttle -> stands on "BottomMainA (westbound)" (arrivals from the east barred).  Editor, Home -> 75 407 DB.  `knownHomeFacing(mainA, "75 407 DB")` reads the copy it stands on and answers W, so `1facc0c2`'s door asks nothing (right: `homeFacingsFor` is {E}, one choice, so `FacingPrompt.wouldAsk` would be false anyway) and `setHome(tile, picked, null)` -> `writeHome` saves `homeFacing: "W"`.  The build's `homeCopy` then finds no allowed copy facing W and falls back to the eastbound one, and emits no `homeFacingFixed` (`AutonomyBuilder.java:1192-1195` sets it only when the chosen copy faces the saved way) - so the record is inert: Return Home treats the home as unfixed, the only arrival it can make is from the west, and `captureFromLayout` never rewrites an unfixed `HOME_FACING` (`AutonomySession.java:4886`).  So: a value the rule says must not be saved, sitting in the setup, disagreeing with what the build does, and a comment that now says the opposite.  Nothing is driven wrongly.  Reading only.

**Verification request (needs execution; core test).**  `testATrainIsPutOnlyWhereItCanStart`'s fixture: `setFacingAndMove(mainA, Side.W)` with a running layout, then `session.setHome(mainA, PROBE, null)`.  **Proves it:** `session.getPointProperty(mainA, "homeFacing")` is `"W"` while `session.homeFacingsFor(mainA)` is `[E]`.  **Refutes it:** the property is absent or E.

**Suggested fix.**  Filter `homeFacingOf`'s railway answer by `homeFacingsFor(tile)` in `writeHome` (as `setHome(..., facing)` does), and correct the comment.  Whether `knownHomeFacing` should then answer null there is moot for the door: one allowed facing is never asked.

### GUI3-C3 - one accent `91daff27` added is wrong: Spanish "la estación qué elija"

| | |
|---|---|
| **Disposition** | Fixed - 031a7ddb |
| **Where** | `messages_es.properties:1670` (`autosetup.ui.tooltipShowStationHere`); and, unchanged by the commit, `:759` (`route.ui.promptWhichRoute`), `:1759` (`layout.ui.promptWhichDiagram`) |

`Muestra qué ocurre en la estación qué elija.` - the first `qué` is right (an indirect question, "shows what happens"), the second is a relative pronoun ("the station that you choose") and takes no accent: `...en la estación que elija.`  The old value had `que` in both places; the commit accented both.  It is the only wrong accent I found among the 506 (see GUI3-D5 for what was read).  Nearby, not the commit's doing: `Qué ruta?` and `Qué diagrama?` open with no `¿`, where this bundle's other questions have one (`¿Qué longitud cuenta esta pieza de vía?`, `¿Cómo debería llamarse esta configuración?`).  Cosmetic.  **Verification (no execution):** read the three values.

### GUI3-C4 - GUI2-B1's sibling: with nothing saying which way a train faces, the Facing menu still ticks the first facing - "the one a placement with no recorded facing actually gets" - but since `8370abb1` the build stands it on a copy it can start from, which on a barred square faces the other way

| | |
|---|---|
| **Disposition** | Fixed - 4be3798a (claim d65df6cb, red first); a square with one facing is ticked again in 6cb11933 (DCN4-C3) |
| **Where** | `AutonomyEditorPanel.java:3772` (`recorded == null ? facing == facings.get(0) : ...`); `AutonomySession.java:6137-6138` (`facingChoices` javadoc), `:6158`, `:5220`; `AutonomyEditorPanel.java:3747`; against `AutonomyBuilder.java:729-735, 780-800` (`startableCopy`) |

`facingChoices`' javadoc: *"Ordered the way the builder orders its copies, because it IS the builder's order now - so the first answer is the one a placement with no recorded facing actually gets."*  `buildFacingMenu` relies on that when neither the railway nor the setup has an answer: it ticks `facings.get(0)`.  GUI2-B1's fix changed the build's answer from copy zero to `startableCopy` (a plain copy trains may arrive at), and copy zero is exactly the barred one at BottomMainA (westbound, W) and BottomMainPost (N-plain, faces S).  So for a train placed there with no facing (GUI2-B1's own scenario: the editor's Place for a locomotive with no heading) the menu ticks W while the build stands it facing E.  Reached only while the menu has no running-layout answer (`facingOnTheRailway` null - the editor open over a setup that is not loaded, which `openLayoutEditor` allows so blocking errors can be fixed); once a running layout carries the train, the menu reads its copy and agrees.  The same stale premise is in three comments that say `placementCopy` "falls through to the first copy" (`AutonomySession.java:5220`, `:6158`, `AutonomyEditorPanel.java:3747`); it now falls through to `startableCopy`.  Cosmetic and narrow.

**Verification request (needs execution; core test).**  Fixture as GUI2-B1's claim, no running-layout source: `placeLocomotive(mainA, PROBE); setFacing(mainA, null)`; compare `session.facingChoices(mainA).get(0)` with the facing (`facingsFor(mainA).get(...)`) of the Point the probe stands on after `build(session)`.  **Proves it:** W against E.  **Refutes it:** equal.  **Suggested fix:** tick nothing (or the `startableCopy` facing) when nothing is recorded, and reword the javadoc and the three comments.

### GUI3-C5 - small

| | |
|---|---|
| **Disposition** | Fixed - 031a7ddb |
| **Where** | `PositionAwareJFrame.java:199`; `test/core/testATrainIsPutOnlyWhereItCanStart.java:36-41, 228`; `test/regression/testTheRefusalsAreAskedAtTheDoors.java` (ratchet comment); `Layout.java:10585-10596` |

- `4132d260`'s GUI2-C5 fix re-indented the javadoc opener and lost a space: *"The area a window can actually occupyon the screen it is on"*.
- `testATrainIsPutOnlyWhereItCanStart`'s class javadoc keeps its GUI-B1 paragraph in the present tense - *"The train then stood on a copy that is not a station, and autonomy would not start it"*, offered as the defect - two paragraphs below the new one that makes that the intended outcome; and `testTheFacingDoorNeverMovesATrainOntoACopyItCannotStartFrom`'s javadoc still says the Facing door moves the train onto a copy it can start from *"or nowhere"*, which `8370abb1` removed (it now moves it onto a barred copy facing that way where no other does).  The test itself is still right for BottomMainPost, where a station copy faces south.
- `testTheRefusalsAreAskedAtTheDoors.testTheListOfDoorsIsStillComplete`'s comment says `refreshAutonomyPrompt` "asks twice"; since GUI-C8 it asks once (the Load button).  The sentence narrates the first version's miscount, so it is history, but it reads as the current count.
- `setAtomicRoutes`' javadoc (`Layout.java:10585-10596`, GUI2-C1's second half) now reads *"So neither direction gives an edge back twice or leaves one held ... That is about giving back twice.  True-to-false can still leave one edge held"* - the appended correction is right, but the sentence it corrects still says "or leaves one held"; dropping those four words would make the paragraph agree with itself.

## D - not defects

### GUI3-D1 - GUI2-A1 / TDY2-A1 (`8370abb1`): verified - the Facing menu and the throttle now move the train onto a copy facing the chosen way, and every surface agrees about where it stands

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `AutonomySession.java:1562-1599` (`copyFacing`), `:1623-1700` (`moveOntoFacingCopy`), `:1825-1925` (`flipFacing`), `:7172-7188` (`setFacingAndMove`); `AutonomyBuilder.java:727-800` (`placementCopy`, `startableCopy`); `AutonomyEditorPanel.java:3690-3790` (`buildFacingMenu`) |

- **The failure scenario is gone.**  At BottomMainA, reversed on the throttle: `facingChoices` = [W, E] (arrival E sorts first), recorded E, `now` = W; `copyFacing(W)` finds no placeable copy, and its new second loop returns "BottomMainA (westbound)" (plain: not terminus, not reversing); the train is moved there with its tail and road (`arrivedFrom`/`arrivedAlong` restored), so the log line `infoFacingFollowedDirection` is now true.  A second reversal reads recorded W, `now` = E, `copyFacing(E)` returns the station copy: the gesture is its own undo.  The Facing menu is the same through `setFacingAndMove`, and it now reopens with W ticked because it reads the copy first (`facingOnTheRailway`) - GUI2-A1's "reopens with E ticked" is gone, and the menu offers only what the door then does.  `placementCopy`'s third and fourth loops mirror `copyFacing`'s second (plain first), so a rebuild and a load keep the train on the westbound copy instead of turning it round; `captureFromLayout` then writes back W, not E.
- **The placement doors are not loosened.**  The paste (`TrainControlUI.java:7240-7270`), the Place Locomotive dialog (`GraphLocAssign.java:233-243`) and the editor's Place (`AutonomyEditorPanel.java:5445`) all choose the facing from `placeableFacingsFor` before calling `copyFacing`, so its first loop always answers for them and the new fallback is unreachable from a placement.  The paste's may-turn question offers `facingsFor(...).values()` (every copy), but on a may-turn square each allowed arrival side yields a plain and a turning copy facing both ways, so both headings stay placeable wherever one side is barred (GUI-D5's reading still holds).
- **The idle drain after a turn** (`faceTheWayItCameIn`) asks for the arrival side, which a turning copy of an allowed arrival faces, so `copyFacing`'s first loop answers; the fallback is not reached there.
- **The checker is consistent**: `facingsThatCannotBeHeld` asks `facingChoices`, W is among them, and the build now holds W - so it rightly says nothing.  (No check reports "a placed train stands on a copy autonomy cannot start from"; the load logs `warnLocomotivePlacedOnNonStation`, and Why Not Moving? answers - see GUI3-C1 for what it answers.)
- **The diagram**: the caption finds the train by `occupantsAt` (every copy, barred included) and draws the arrow of the copy it stands on (`facingArrowOf`), so it shows the train, facing W; the right-click menu opens for a locomotive on a non-destination copy (`current.isDestination() || current.getCurrentLocomotive() != null`) and gathers hand paths for it.  The grey wash of the rail a standing train lies across is computed by `Layout`'s tail walk from the copy and its restored `arrivedFrom`; a westbound copy with its tail to the west has the shape of a turning copy, which that walk already serves - not traced further (the TDY/AUT lanes own the walk; `8370abb1`'s TDY2-C5 change is in it).
- **The claims** (`8346be65`), each read against `8370abb1^`: `testTheBuildKeepsTheFacingTheSetupRecords` - the old `placementCopy` fell back to an allowed copy facing E, so `assertEquals(facing of standing copy, W)` fails; `testAReversalOnTheThrottleIsFollowed` and `testTheFacingMenuMovesTheTrainOntoACopyFacingThatWay` - the old `copyFacing` returned null, the train stayed on the E copy, same assertion fails; both have preconditions that the flip acted (`flipFacing` returns `mainA`, facing recorded W) so they cannot pass vacuously.  The assertion carrying each is the copy's facing - the variable, not a control.

### GUI3-D2 - GUI2-B1 (`8370abb1`): verified

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `AutonomyBuilder.java:729-735`, `:780-800` |

Both early `return 0`s now `return startableCopy(nodes)` - the plain allowed copy, then any allowed copy, then 0 - the fallback GUI2-B1 suggested, in `homeCopy`'s order.  `testATrainWithNoFacingIsPutWhereItCanStart` covers BottomMainA and BottomMainPost; against `8370abb1^` copy zero is "BottomMainA (westbound)" / BottomMainPost's N-plain copy, both `station: false`, so `assertTrue(standing.isDestination())` fails for the reason named.  The one surface that still assumes copy zero is GUI3-C4.

### GUI3-D3 - `264f2a73`: the premise the door-refusal guard now states is true on every road into `openLayoutEditor`

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `TrainControlUI.java:5045-5060` (`whyAutonomyEditorCannotOpen`), `:5076-5160` (`openLayoutEditor`), `:6795-6802` (`showOpenEditor`), `:8603-8612`, `:8709-8717`, `:4824-4860`, `:23723-23843`; `test/regression/testTheRefusalsAreAskedAtTheDoors.java:96-104`, `testEditorSurfaceRules.java:3264-3312` |

The guard's comment: *"opening with an editor already open is not refused anywhere - `openLayoutEditor` brings that window forward"*.  The roads in are the Edit button/menu (`:3559`, `:23941`), `openAutonomyEditor(tile)`, `openAutonomyEditorOnPage(page)` (the Autonomy menu's Edit item, per page), the excluded-page label and Fix Setup (both `openAutonomyEditor(null)`).  All reach the same method, whose refusals before the editor check are local-layout, "explicit autonomy with no session", and busy - each a dialog that `whyAutonomyEditorCannotOpen` asks in the same order, so the label returns silently where the method would complain.  Then `!editLayoutButton.isEnabled()` with a displayable editor -> `showOpenEditor()` and return, before the page is selected, the preference written, `captureRunningLayout()` or a new `LayoutEditor` - so a different page or the other mode (a track editor open when the autonomy editor is asked for) only brings that window forward; the operator switches inside it, where unsaved work is asked about.  The button is disabled for as long as an editor is open: every enable goes through `setEditLayoutEnabled` -> `applyLayoutEditingAvailability`, the teardowns re-enable only on a real close, and the one mid-edit re-enable (`layoutRefreshCompleteInternal` during an editor's page switch) is followed in the same event by the editor's `after` continuation (SVN-A3).  Removing the label's row from `DOORS` matches the product change; the positive claim that the label and Fix Setup do NOT ask is `testTheWaysIntoTheEditorBringAnOpenOneForward`, and the ratchet still balances (`refreshAutonomyPrompt` asks once, from Load).

### GUI3-D4 - TDY2-C4 (`1facc0c2`): the home door asks exactly when the session has no answer, and not where it should not

| | |
|---|---|
| **Disposition** | Closed - verified, one consequence of `8370abb1` filed as GUI3-C2 |
| **Where** | `AutonomyEditorPanel.java:5096-5117`; `AutonomySession.java:7425-7485` |

The door now asks `knownHomeFacing(tile, picked) == null`, which is `homeFacingOf` - the value `writeHome` saves - so the question and the save read one method.  Case by case: **square not split** - `homeFacingsFor` is empty, `wouldAsk` false, no question (and `homeFacingOf` now returns null for a train standing there, where it used to return the setup's `FACING`; `homeCopy` on a single copy does not care); **train standing here on the railway** - its copy's facing, no question (the old door asked whenever the SETUP did not have it here); **standing elsewhere** - null, asked (TDY2-C4's case; the old code saved the setup's stale facing unasked); **not on the railway, or no running layout** - the setup answers when it has the train here, else asked, as before.  The claim `testATrainThatHasDrivenOffGivesNoFacing` fails against `9c85db2a` for the reason it names (the loop finds the train elsewhere, falls through, and returns the setup's E).  Coverage note: it pins `homeFacingOf`, not the door's `if`; reverting the door to `!picked.equals(locomotiveAt(tile))` would leave the suite green (`extracted-rule-moves-the-bug-to-the-call`).

### GUI3-D5 - GUI2-C3 (`91daff27`): 505 of 506 accent restorations are right, and nothing else in any value moved

| | |
|---|---|
| **Disposition** | Closed - verified, one wrong accent filed as GUI3-C3 |
| **Where** | `messages_{de,da,it,es,fr,nl,pl}.properties` |

Mechanically, every changed key in all seven bundles (79 de, 95 da, 58 it, 88 es, 90 fr, 4 nl, 92 pl = 506): same `{n}` set, same number of ASCII apostrophes, same `\n` and every other escape, same word count, no key added or removed, every file still pure ASCII with `\uXXXX`, and each new value maps back to the old one by its language's transliteration (de ae/oe/ue/ss, da aa/ae/oe, Polish ł->l, the rest by stripping the mark) - the commit's own claim, re-derived.  Read: every changed word pair in every language (96 de, 135 da, 19 it, 98 es, 145 fr, 4 nl, 244 pl distinct pairs), and in its sentence every pair whose unaccented form is also a word - Italian è/lì/là/sé/né (all 35 è, all 12 lì: "there", "sé stessa"), Spanish está/sí/él/aún/qué/cómo/dónde (emphatic "sí puede", "a él", "aún no", "por qué", "adónde ir"; all right except GUI3-C3's second qué), French à/là/où and the participles (commandé, placé, laissé, réclamé; "les réserve" and "le règle" as verbs), Dutch "één en dezelfde", Polish ją/tą/tę/że/pól and the instrumental/accusative endings (stacją, konfiguracją, trasę, dowolną; "odpowiedz na pytanie" left unaccented as the imperative, "Ta odpowiedź" accented as the noun).  German and Danish have no ambiguous pairs.  What is left unaccented among values changed since `master` is legitimate: German ss after a short vowel and ue in neue/aktuell/zuerst, Danish kommandoer/naboer, Italian e/la/li as "and/the/them", Spanish esta as "this", Dutch een as "a", Polish ze as "with/from".  GUI2-C3's two mixed tooltips (`pathPreferenceSHORTEST/LONGEST_LENGTH`, de and it) are now accented throughout.

### GUI3-D6 - `4132d260`'s interface comments match the code

| | |
|---|---|
| **Disposition** | Closed - verified, one typo filed in GUI3-C5 |
| **Where** | `AutonomyEditorPanel.java:813-815`, `:6866-6871`; `LayoutEditor.java:1607-1614`, `:1853-1858`, `:5186-5209`; `LayoutGrid.java:1452`; `TrainControlUI.java:9732-9736` |

The caption dropdown has five options (`CAPTIONS_STATIONS` 0 to `CAPTIONS_LABELS` 4), Labels Only is the text switch turned on and the others turn it off, Control+L steps the dropdown in the autonomy editor (`cycleCaptionMode`) and flips the switch only in the plain editor, and the grid draws station captions from the dropdown whatever the switch says - as each rewritten comment now says.  "Those two keys" is now named (`activateRoutes`, `activateRouteIDs`), which the legacy import does refuse.

### GUI3-D7 - REG2-C2's interface line

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `TrainControlUI.java:24575-24582`; `autosetup.ui.menuNoSetupPossible` in eight bundles |

`whyAutonomyWillNotStart` answers "needs a layout stored on this computer" first over a Central Station layout, the reason the greyed Start item is greyed for; the key exists in all eight bundles, and its three callers (the right-click tooltip, the dialog at `:5961`, the exception at `:24671`) all show a plain sentence.  The REG lane owns the rest of the change.

### GUI3-D8 - GUI2-C1, GUI2-C5 and the two left open: dispositions truthful

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `Layout.java:7915-7924`, `:8009-8011`, `:8519-8521`, `:10585-10596`; `PositionAwareJFrame.java:198`; `GUI.md` GUI-C1 row; `findings.tsv` rows `GUI2-*` |

- **GUI2-C1**: the three sites now say `unlockPath` reads `releasedEarly`, and the failure handler's note adds that `clearedEdges` is what `getActiveAccs` reads - true (`Layout.java:1022`, inside `getActiveAccs`).  The javadoc half is right in substance; its leftover clause is in GUI3-C5.
- **GUI2-C5**: the opener is indented (with the typo in GUI3-C5), and GUI-C1's disposition now says two reworded keys plus why the third (French `validateConfigOpenGraphUI`) needed nothing.
- **GUI2-C2 and GUI2-C4** are recorded Open "for the next round", in `GUI2.md` and in the store alike; nothing in the range touched `released.add(givenBack)` or `RouteEditorFrame`'s dedupe, so both stand as written.
- The store's `GUI2-*` rows match `GUI2.md`'s dispositions (A1, B1, C1, C3, C5 closed with their commits; C2, C4 open; D1-D6 closed).

## What this pass did not cover

- **Execution.**  GUI3-C1, C2 and C4 rest on reading until their probes run; in particular that `getPossiblePaths(loc, true)` offers paths from "BottomMainA (westbound)" on the frozen railway, and what the grey wash draws for a train standing there.
- **Windows.**  No dialog, menu or caption was opened; what the operator sees is read from the code that draws it.
- **The other lanes' code in `8370abb1`** (AUT2-C1's claim removal, TDY2-C5's tail-walk fork rule, REG2-C6's unarmed import) was read only where it touches the interface.
- **`b53439dc` and `2f4448b6`** are tracker and catalogue records; read for their GUI rows only (GUI2's dispositions as quoted in `findings.tsv` match `GUI2.md`).
