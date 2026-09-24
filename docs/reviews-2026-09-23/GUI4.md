# User interface round 3 fixes - validation

**Status:** open

**Open at close:** GUI4-C3, GUI4-C5 - carried in the finding store.

**Prefix:** `GUI4`

**Reviewed:** branch `autonomy-diagram-r0` at `9f5d8e23`, 2026-09-23.  Baseline: the GUI3 validation at `2f4448b6` (`docs/reviews-2026-09-23/GUI3.md`); range `2f4448b6..9f5d8e23` (5 commits: `d65df6cb` claims, `4be3798a` fixes, `9a9a8564` tracker, `031a7ddb` text, `9f5d8e23` catalogue).

**Method:** Read every commit in the range, and at HEAD the code around each UI change.  For the two new sentences (`autolayout.why.startFacingBarred`, `autolayout.whyHomeStartFacingBarred`): read-only Python piped through stdin over all eight bundles (placeholders, ASCII apostrophes, escapes, pure-ASCII files, capitalisation and final stop against the sibling keys, rendered length), then each translation read against its siblings (`why.startNotStation`, `whyHomeStartNotAStation`, `whyHomeStartOutOfService`) and against the same bundle's own words for facing (`autosetup.ui.menuFacingGroup`) and for the menu `{1}` fills (`autosetup.ui.menuArrivalsGroup`, and how `checkTerminusTwoWaysIn` quotes it).  Walked every surface that shows them: `AutonomyEditorPanel.composeWhy` on each Path Type, `AutoLocomotiveStatus.whyNotReport` and `whyNotToolTip`, `HomeStaging.whyNotHome` through `Layout`'s Return Home log, the diagram's right-click menu; and every reader of what `explainCannotStart(loc, true)` skips (`isPathClear`'s fences, every reader of `isAutonomyPaused`, the two hand doors that call `executePath`).  Followed the remedy the sentences give into the editor's Trains May Arrive... menu and `AutonomySession.terminiWithTwoWaysIn`.  For GUI3-C4: every reader of the Facing menu's `ButtonGroup`, both surfaces that build it, and the javadocs GUI3-C4 named.  For GUI3-C2: `knownHomeFacing`/`writeHome` for split, unsplit and may-turn squares, and the home door.  Read each claim against `d65df6cb` (the red commit).  Checked the Spanish fixes of `031a7ddb` and scanned every Spanish value for `?` without `¿`.  Frozen railway read at `test/layouts/live-snapshot/` only.  Nothing was run; every finding below rests on reading and says what execution would settle it.

## A - high

None found.

## B - medium

None found.

## C - low

### GUI4-C1 - the German and Polish translations of both new sentences say the train faces the opposite way - towards the side trains may not arrive from - and the English they were made from admits that reading

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933, with TDY4-C3: the English no longer reads two ways, and German and Polish were translated again from it |
| **Where** | `messages_de.properties:214`, `:1785`; `messages_pl.properties:214`, `:1786`; `messages.properties:213`, `:1785` (`autolayout.whyHomeStartFacingBarred`, `autolayout.why.startFacingBarred`) |

A train on a barred copy faces AWAY from the barred side.  At BottomMainA (arrivals from the east barred) the barred copy is "BottomMainA (westbound)": the copy a train arriving from the east would stand on, facing west (GUI3-C1: *"trains may not arrive from the east there, and the train now faces as if it had"*; `facingChoices` = [W, E] with arrival E first).  The English says *"It is facing the way trains may not arrive at {0}"* - "the way" as the direction such trains travel, i.e. west.  Five translations say that: es *"en el sentido en que"*, fr *"dans le sens où"*, it *"nel senso in cui"*, nl *"in de richting waarin"* (all "the direction IN which"), da mirrors the English.  Two say the other thing:

- **German** - *"Sie steht in der Richtung, **aus der** Züge nicht in {0} ankommen dürfen"*: "the direction FROM which trains may not arrive" is the east, and in this bundle "steht in Richtung" means *faces* - the Facing menu is `menuFacingGroup` = *"{0} steht in Richtung..."*.  So it says the train faces east.  The Home fragment (`:214`, *"er steht in der Richtung, aus der ..."*) is the same.
- **Polish** - *"Stoi zwrócona w kierunku, **z którego** pociągi nie mogą przyjeżdżać na {0}"*: "turned towards the direction from which trains may not come" - unambiguously facing the barred side (the bundle's `menuFacingGroup` is *"{0} jest skierowana w stronę..."*).  The Home fragment (*"stoi zwrócony w kierunku, z którego ..."*) is the same.  (Also, copied from the sibling `startNotStation`: *"automatyka jej stamtąd nie wyruszy"* - `wyruszyć` is intransitive, "set off", so "automation will not set her off" reads wrong; *"nie wyśle jej stamtąd"* or *"nie uruchomi jej stamtąd"*.  Pre-existing in the sibling; my Polish is the weakest of the seven, so treat this half as a question.)

The remedy still works whatever the reader believes (turning the train round puts it on the station copy), so nothing is driven wrongly; what is wrong is a description of the train the operator can see is false.  The English is the root: two of seven translators read "the way trains may not arrive" as "the side by which", and so can a reader of the English.  (The remedy's "open that side" also has no antecedent - no side was named.)  **Verification (no execution):** read the four values beside the same bundle's `menuFacingGroup`.  **Suggested fix:** in de, `aus der` -> `in der` (*"in der Richtung, in der Züge nicht in {0} ankommen dürfen"*); in pl, `z którego` -> `w którym`.  Better, and Adam's wording to choose: name the side, which `explainCannotStart` and `whyNotHome` can derive from the barred copy - e.g. *"It faces the way a train arriving from the {2} would, and trains may not arrive at {0} from the {2}.  Turn it round, or tick "From the {2}" under "{1}" ..."* - which also names the exact item to tick.

### GUI4-C2 - on Manual, Why Not Moving? still tells the operator a paused train "cannot be sent anywhere", which the pause does not stop - the new by-hand branch keeps `paused` under a comment saying it keeps only what stops any route

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933 (claim 1c6ae0fe, red first) |
| **Where** | `Layout.java:4931-4946` (`explainCannotStart(loc, byHand)`); `AutonomyEditorPanel.java:7891-7897` (`composeWhy`); against `Layout.java:4462`, `:4697` (the pause's only readers), `Locomotive.java:1843-1865`, `AutoLocomotiveStatus.java:1163`, `LayoutRightclickAutonomyMenu.java:213-240`, `:1383` |

GUI3-C1's Manual half asked `composeWhy` to *"skip `explainCannotStart`'s autonomy-only reasons when `byHand`"*, and noted *"`paused` I did not trace"*.  The fix's by-hand branch says:

> BY HAND, ONLY WHAT STOPS ANY ROUTE (GUI3-C1).  A route picked by hand may start from a copy that is no station and from a square switched off ... so those two reasons are autonomy's and not the operator's

and then returns `autolayout.why.paused` first.  The pause is autonomy's too.  `Locomotive.setAutonomyPaused`'s javadoc: *"Flags autonomy as paused so that no further routes are started"*; the button's tooltip (`autolayout.ui.tooltip.tempPauseLoc`): *"Temporarily pause this locomotive from automatically running."*  Its only readers in `src`, apart from the button showing its own state (`AutoLocomotiveStatus.java:248`), are `pickPath` (`Layout.java:4697`) and `checkForSlowerLoc` (`:4462`) - both autonomy - and the two `explainCannotStart`s.  Nothing a hand send passes asks it: `isPathClear`, `executePath`, `getPossiblePaths`, `isOfferableToOperator`, the right-click menu's `gatherPathOptions` and the locomotive list's double-click (`AutoLocomotiveStatus.java:1163`) all ignore it.

**Scenario.**  Autonomy tab, press 75 407 DB's pause button (standing at BottomMainA facing east).  Open the autonomy editor, Path Type Manual, Why Not Moving? on BottomMainA: *"**75 407 DB** cannot be sent anywhere: This locomotive is paused."*  Right-click the same square on the diagram: its destinations are offered, and one click sends it.  That is the OB-225/MT-434 shape - an autonomy reason given on Manual - which GUI3-C1 was filed for and whose disposition says is fixed.  Narrow (a paused train, asked on Manual), and nothing is driven wrongly.  Reading only.

**Verification request (needs execution; core, no window).**  In `testATrainIsPutOnlyWhereItCanStart`'s fixture: `stoodFacing(session, running, mainA, Side.E)`, then `model.getLocByName(PROBE).setAutonomyPaused(true)` (restore in `finally`).  **Proves it:** `running.explainCannotStart(train, true)` equals `I18n.t("autolayout.why.paused")` while `running.getPossiblePaths(train, true)` is non-empty.  **Refutes it:** `getPossiblePaths(train, true)` is empty for the paused train (then some hand door does read the pause and I missed it).  **Suggested fix:** drop the `isAutonomyPaused` line from the by-hand branch (the branch then answers only `notOnGraph`), and add the assertion above to `testATrainFacingABarredWayIsToldWhy` or a sibling.

### GUI4-C3 - the new remedy "open that side under Trains May Arrive..." is, at a station every train must turn round at, the one that raises the blocking terminus error

| | |
|---|---|
| **Disposition** | Open - Adam's decision: at a terminus with a barred side, "open that side" leads to the terminus error; not reachable on the frozen railway |
| **Where** | `Layout.java:4971-4974` (`explainCannotStart`), `HomeStaging.java:1946-1952` (`whyNotHome`); `AutonomySession.java:1584-1620` (`copyFacing`'s second loop takes a turning copy), `:9160-9193` (`terminiWithTwoWaysIn`); `AutonomyChecks.java:1080-1094` (`TERMINUS_WITH_TWO_WAYS_IN`, `Severity.ERROR`); `messages.properties:1622` (`checkTerminusTwoWaysIn`) |

A must-turn station reached from two sides is a blocking error (MT-361, Adam: *"Make this be an autonomy ERROR that the user has to fix"*), and that error's own remedy is *"untick it under "Trains May Arrive..." on this square"* - so every terminus reached from more than one side that runs has all its sides but one barred.  There, the barred side's copy is a turning copy facing back towards that side, and a train reversed on the throttle to face that way is stood on it: `flipFacing` -> `copyFacing(facing)`, whose first loop finds no station copy facing that way and whose second loop (`8370abb1`) returns the barred turning copy (`if (turning == null) turning = candidate`).  `isABarredCopyOfAStation` is then true, and both surfaces say *"... Turn it round, or open that side under "Trains May Arrive..." in the autonomy editor."*  Doing the second - ticking the barred side - makes `terminiWithTwoWaysIn` count two unbarred sides, and the checker raises `checkTerminusTwoWaysIn`, an ERROR, which stops autonomy starting at all and tells the operator to untick the same side again.  So one of the two remedies turns one train's refusal into the whole railway's, and the error's remedy undoes it: a loop (`error-must-have-a-remedy` - the remedy must not be one the rules refuse).  Turning it round is right everywhere.

Not reachable on the frozen railway as it stands: none of its must-reverse squares has a barred side (barred: BottomMainA, TopMainR1Inter, TopMainR2Inter, Tunnel, BottomInner - plain; BottomMainPost, RampDown - may turn).  Reading only.

**Verification request (needs execution; core, no window).**  `testATrainIsPutOnlyWhereItCanStart`'s fixture: `session.setPointProperty(mainA, AutonomyBuilder.MUST_REVERSE, true)` (BottomMainA keeps its east bar, so no terminus error yet - assert `session.terminiWithTwoWaysIn()` does not contain `mainA`), build, stand the probe on the station copy, `session.flipFacing(PROBE, running)`.  **Proves it:** the probe stands on a copy with `!isDestination()`, `running.explainCannotStart(train)` is the `startFacingBarred` sentence, and after `session.setBarredArrivals(mainA, empty set)` `session.terminiWithTwoWaysIn()` contains `mainA`.  **Refutes it:** after the flip the probe is still on the station copy (a must-turn square never stands a train on its barred copy).  **Suggested fix:** where the square must turn, give the sentence without the second remedy (or with the terminus error's own alternative, "set the square to may turn round").

### GUI4-C4 - GUI3-C4 is half done: `facingChoices`' javadoc, the sentence GUI3-C4 quoted, still says the first facing is what a placement with no recorded facing gets; and two comments say the lone facing is shown ticked, which since the fix it is not when nothing is recorded

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933, with TDY4-C1 and DCN4-C3 |
| **Where** | `AutonomySession.java:6188-6189`; `AutonomyEditorPanel.java:3750` and `test/regression/testEditorSurfaceRules.java:70-71`; `AutonomyBuilder.java:639-648` |

- GUI3-C4's first quotation and its suggested fix (*"reword the javadoc and the three comments"*): the three comments were reworded (`031a7ddb`), the javadoc was not.  `facingChoices`, at HEAD: *"Ordered the way the builder orders its copies, because it IS the builder's order now - so the first answer is the one a placement with no recorded facing actually gets."*  Since GUI2-B1 that placement gets `startableCopy`, which at BottomMainA and BottomMainPost is not the first facing - the very reason `4be3798a` stopped ticking `facings.get(0)`.  (`git blame`: unchanged since `ddac1b3a`, 2026-09-07.)
- `buildFacingMenu`'s single-choice comment (`:3750`, *"So the one facing is shown, ticked, with a line saying why there is nothing to choose"*) and `testTheFacingMenuAppearsWithASingleChoice`'s javadoc (*"the menu shows the one facing, ticked"*): with nothing recorded and no running layout carrying the train, the tick is now `recorded != null && ...` - false - so the lone radio is shown unticked beside the disabled *"Only one direction is possible here"*.  On a square with ONE facing that is not "nothing known"; every copy faces that way.  Either the comment (and test javadoc) should say so, or the tick should be `recorded != null ? facing == recorded : facings.size() == 1`.  Reached only when the menu has no railway answer (the editor over a setup not loaded), so cosmetic.
- Sibling in the builder, same premise: the orphaned javadoc above `homeCopy`'s (`AutonomyBuilder.java:639-648`, a `placementCopy` javadoc left behind when `homeCopy` was inserted beneath it - `insert-above-the-javadoc`) says *"With nothing authored the first copy is used, which is a guess"*.  DCN3-C2's eleven did not include it.

**Verification (no execution):** read the three places.

### GUI4-C5 - small

| | |
|---|---|
| **Disposition** | Partly fixed - 6cb11933: the locomotive list's tooltip wraps.  Open - for a follow-up: the loader's "placed on a non-station" warning for a train on a barred copy, which the load cannot yet tell apart (the square's other copies are not parsed when it is written) |
| **Where** | `AutoLocomotiveStatus.java:891-899`; `Layout.java:11849-11856`, `messages.properties:288` |

- **The locomotive list's hover tooltip shows the new sentence on one line.**  `whyNotToolTip` returns `explainCannotStart`'s sentence raw (`if (cannotStart != null) return cannotStart;`), and Swing lays a plain tooltip out on one line - which `AutonomyEditorPanel.wrapped` exists to prevent (*"A tooltip that wraps instead of running off the screen"*).  The sentences this used to show were at most ~96 characters (`startNotStation`); `startFacingBarred` renders at 182 (en) to 223 (fr) characters, two sentences.  Reached when the train stands on a barred copy AND its hand list is empty (the label only says "No available paths" then).  Fix: `AutonomyEditorPanel.wrapped(cannotStart)` (package-visible, `static`).
- **The load path still calls the station a non-station.**  `warnLocomotivePlacedOnNonStation` = *"Auto layout warning: {0} placed on a non-station and will not be run automatically"*, logged whenever a built configuration puts a train on a copy with `"station": false` - which since `8370abb1` is every build of a setup recording a facing only a barred copy holds (a reversal at BottomMainA, captured).  AUT3-B1 judged it fine because it names only the train; the noun is the one GUI3-C1's point 2 removed from the other two sentences (*"a station called not one"*).  Suggest: under `isABarredCopyOfAStation`, the Home fragment's wording or none (Why Not Moving? already explains).

## D - not defects

### GUI4-D1 - GUI3-C1 (`4be3798a`): verified for Auto - the sentence names the square, says why, gives a remedy; on Manual the barred copy and the switched-off start are no longer reasons (the pause is, GUI4-C2)

| | |
|---|---|
| **Disposition** | Closed - verified, one reason left in the Manual branch filed as GUI4-C2 |
| **Where** | `Layout.java:4931-4981`, `:9015-9030`; `AutonomyEditorPanel.java:7891`; `test/core/testATrainIsPutOnlyWhereItCanStart.java:467-521` |

- **(1) and (2) are gone.**  `explainCannotStart` asks `isABarredCopyOfAStation(at)` before `!at.isDestination()` and fills `placeNameOf(at)`, so the frozen-railway case reads *"It is facing the way trains may not arrive at BottomMainA, so autonomy will not start it there.  Turn it round, or open that side under "Trains May Arrive..." in the autonomy editor."* - no copy name, no "not a station".  `startNotStation` and `startInactive` now fill `placeNameOf` too.  In the locomotive window the line above it (`whyStanding`, via `stationName`) says "BottomMainA", so the two lines agree.
- **`isABarredCopyOfAStation` asks the builder's own rule.**  A copy is a destination iff `point.isStation() && arrivalAllowed(node)` (`AutonomyBuilder.java:990-992`), so "a non-destination copy whose block has a destination copy" is exactly "a copy of a station whose arrival side is barred"; a demoted station (no copy a destination) still gets `startNotStation`; `isSamePlaceAs` compares the builder's block, not s88.
- **(3) the remedy exists and is findable**: `{1}` is `autosetup.ui.menuArrivalsGroup`, the label of the menu the editor builds at `AutonomyEditorPanel.java:1614`/`:1626`, enabled on a station with more than one way in - which a square with a barred copy always is.  Both remedies clear the refusal on a plain square: a throttle reversal moves the train to the station copy (`copyFacing`'s first loop), and opening the side makes that copy a station at the next build.  (Not at a terminus: GUI4-C3.)
- **Manual**: `composeWhy` passes `byHand`; `explainCannotStart(loc, true)` skips the barred copy and the switched-off start, which `isPathClear` fences behind `isAutoRunning()` (`Layout.java:2429`, `:2438`) and which the editor never meets (it cannot be open while autonomy runs, OB-047).  Then `explainDestinationsGrouped(standing, true)` answers from the barred copy with `firstClearOrWhyNot(..., byHand)`, i.e. `isPathClear` - the hand rule.  Switching Path Type re-asks (`retestForTheNewTier`, `:8494-8503`).
- **The claim** (`testATrainFacingABarredWayIsToldWhy`), read against `d65df6cb`: the first `assertEquals(running.explainCannotStart(train), expected)` fails there (it returned `startNotStation` with "BottomMainA (westbound)"), and `explainCannotStart(train, true)` delegated to it, so the second fails too once the first is fixed alone; the Return Home `why.contains(expectedHome)` fails on the old `whyHomeStartNotAStation`.  Each asserts the sentence - the variable.  **Coverage note:** the claim pins `explainCannotStart(loc, true)`, not `composeWhy`'s call to it; `d65df6cb`'s `composeWhy` called the one-argument form, and reverting to it leaves the suite green (`testWhyNotMovingFollowsPathType` stands its train on a station, so never reaches the start refusal) - the GUI3-D4 shape again (`extracted-rule-moves-the-bug-to-the-call`).  A source guard that `composeWhy` passes `byHand` would close it.

### GUI4-D2 - the two new sentences, mechanically and against their siblings, in all eight bundles

| | |
|---|---|
| **Disposition** | Closed - verified, the direction in de and pl filed as GUI4-C1 |
| **Where** | `messages*.properties`: `autolayout.why.startFacingBarred`, `autolayout.whyHomeStartFacingBarred` |

Both keys present once in each bundle; `{0}` and `{1}` in every value, as the two callers pass them (`placeNameOf`, then `I18n.t("autosetup.ui.menuArrivalsGroup")`); no ASCII apostrophe in any of the sixteen (fr and it use U+2019 for l'/d'/nell'), every file still pure ASCII with `\uXXXX`.  Shape matches the siblings: every `why.*` value opens with a capital and ends with a full stop (it: `È`), every `whyHome*` value opens lower-case with no stop (it: `è`), which is how `infoReturnToHomeWhy` (`"   {0}: {1}"`) prints them one per line.  Grammar matches each sibling's: the locomotive sentences use the language's word for the locomotive (de Sie/sie, es orientada/la, fr Elle/la, it rivolta/la, pl zwrócona/ją) and the Home fragments the word for the train (de er/ihn, es orientado, fr il/le, it rivolto/lo, pl zwrócony/go), as `startNotStation` and `whyHomeStartNotAStation` do; imperative register as the siblings (de/es/nl formal, it/pl/da informal).  Each bundle quotes its own `menuArrivalsGroup` (`"Züge dürfen ankommen..."`, `"Los trenes pueden llegar..."`, ...), the label the menu shows.  The quote marks are ASCII where `checkTerminusTwoWaysIn` uses „…“/«…» for the same label; the bundles already mix the two around placeholders (`route.infoImportedArriveDisarmed` is ASCII in all seven), so not a defect.

### GUI4-D3 - each surface shows the right sentence for its question

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `AutonomyEditorPanel.java:7856-7897`; `AutoLocomotiveStatus.java:716-741`, `:891-899`; `HomeStaging.java:1937-1956`, `Layout.java:10755-10761`; `LayoutRightclickAutonomyMenu.java:213-240`, `:426-445` |

- **Why Not Moving?, Auto**: `startFacingBarred` behind *"<b>{0}</b> cannot be sent anywhere:"*.  **Manual**: no start refusal for the barred copy; the hand reasons follow (and see GUI4-C2).
- **The locomotive window** (`whyNotReport`) and **its hover tooltip** (`whyNotToolTip`) ask the one-argument, autonomy form - by design, the window groups stations by whether autonomy could choose them (Adam's ordering, in its javadoc).  One thing to know, not new: the list whose empty state they explain is the HAND list (`getPossiblePaths(locomotive, true)`, double-click to execute), so for a train on a barred copy with no hand paths they answer autonomy's question.  `startNotStation` did the same before; the new sentence is at least true.  (Its width: GUI4-C5.)
- **Return Home**: `whyNotHome` says `whyHomeStartFacingBarred` under `isABarredCopyOfAStation(from)` and `whyHomeStartNotAStation` otherwise, in the same pre-scan that refuses `!isDestination()` (`HomeStaging.java:550-559`), so the sentence describes the rule that refused.  Printed as `"   75 407 DB: it faces the way ..."`.
- **The diagram's right-click menu** shows neither sentence: it offers hand paths from a barred copy (W21-B3), and its greyed Start item's tooltip is `whyAutonomyWillNotStart`, about the setup, not a train.  Removing a train from a barred copy still works (`moveLocomotive(null, ...)` has no station check); the placement doors still pass the three-argument form and refuse a barred copy (`GraphLocAssign.java:811`, `LayoutRightclickAutonomyMenu.java:1205`, `TrainControlUI.java:7299`); only `putTheTrainsBack` (`TrainControlUI.java:6498`) passes `true`.

### GUI4-D4 - GUI3-C4 (`4be3798a`): the tick is fixed, and a Facing menu with nothing ticked is safe everywhere it is read

| | |
|---|---|
| **Disposition** | Closed - verified, the javadoc half filed as GUI4-C4 |
| **Where** | `AutonomyEditorPanel.java:3761-3785`, `:4068-4097`; `TrainControlUI.java:4664`; `test/regression/testEditorSurfaceRules.java:3312-3332` |

The tick is `recorded != null && facing == recorded`, so with no railway answer and no `FACING` nothing is ticked - GUI3-C4's W-against-E contradiction is gone.  The `ButtonGroup` is local to `buildFacingMenu` and nothing calls `getSelection()` on it: each radio carries its own action (`radio()` runs `setFacingAndMove(target, facing)` then `placementChanged`), so an unticked group only means the first click ticks one, and clicking any radio - ticked or not - fires its action.  Both surfaces get the same menu (the editor at `:1410`, the diagram through `TrainControlUI.java:4664`); the tests that open it (`testTheTailCanBeGivenInTheEditor`) find items by text, not by selection.  The recorded-but-not-offered entry (OB-177) still ticks, since `recorded` is non-null there.  **The claim** (`testTheFacingMenuTicksOnlyWhatIsKnown`) is a source check that `facing == facings.get(0)` is absent; it is present at `d65df6cb:3772`, so it was red for the reason named.  It knows only that spelling (`guard-knows-only-what-it-lists`: `facings.indexOf(facing) == 0` would pass it).

### GUI4-D5 - GUI3-C2 (`4be3798a`): verified

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `AutonomySession.java:7447-7493`; `AutonomyEditorPanel.java:5098-5121` |

`writeHome` now saves `knownHomeFacing`, which returns the railway's (or setup's) facing only if `homeFacingsFor(tile)` contains it, and the comment says so.  GUI3-C2's scenario: the probe on "BottomMainA (westbound)", `homeFacingsFor` = {E} - W filtered, nothing saved, which matches what `homeCopy` builds.  The door asks the same method, so question and save still read one rule: there `knownHomeFacing` is null, `wouldAsk({E})` is false, no question, `setHome(tile, picked, null)` saves no facing.  An unsplit square: `homeFacingsFor` is empty, so no facing is saved even from the setup's `FACING` - inert before (`homeCopy` on one copy ignores it).  A may-turn square with a barred side: both facings are homeable, and the train never stands on its barred copy (GUI3-D1).  The claim `testAHomeIsNotSetFacingAWayNoTrainArrives` asserts the saved property is not "W"; at `d65df6cb` `writeHome` wrote `homeFacingOf` unfiltered, "W", so it fails there for the reason named.

### GUI4-D6 - GUI3-C3, GUI3-C5 and the Spanish of `031a7ddb`: verified

| | |
|---|---|
| **Disposition** | Closed - verified |
| **Where** | `messages_es.properties:760`, `:1671`, `:1760`; `PositionAwareJFrame.java:199`; `test/core/testATrainIsPutOnlyWhereItCanStart.java:23-45`, `:229`; `test/regression/testTheRefusalsAreAskedAtTheDoors.java:166`; `Layout.java:10658-10664` |

- *"en la estación que elija"* - relative, unaccented; *"¿Qué ruta?"*, *"¿Qué diagrama?"* now open with `¿`.  Every Spanish value scanned: each has as many `¿` as `?` (the heading `menuArrivalsHeading`, *"Qué entradas están abiertas"*, is not a question and has neither).
- GUI3-C5's four: *"occupy on"*; the placement class's GUI-B1 history is now headed *"in the past tense"*, and its first paragraph describes the new sentence, not "not a station"; *"one it can be started from where there is one"* replaces "or nowhere"; *"asked twice (once since GUI-C8)"*; `setAtomicRoutes` now says *"neither direction gives an edge back twice"* and the correction after it agrees.

### GUI4-D7 - the record: GUI3's dispositions and MT-488

| | |
|---|---|
| **Disposition** | Closed - verified, two exceptions filed as GUI4-C2 and GUI4-C4 |
| **Where** | `docs/reviews-2026-09-23/GUI3.md`; `findings.tsv` rows `GUI3-*`; `tests.md` MT-488 |

The store's `GUI3-*` rows match `GUI3.md` (C1-C5 closed with their commits, D1-D8 closed).  Two of those "Fixed" are partial: GUI3-C1's Manual half keeps the pause (GUI4-C2), and GUI3-C4's javadoc half was not done (GUI4-C4).  MT-488's steps match the code and the frozen configuration (75 407 DB stands at `1 - Main:20,12` facing E; BottomMainA's east is barred): reversed, it faces west and its hand routes lead west; Why Not Moving? on Auto gives the new sentence, and on Manual does not say it cannot be sent - true unless the train is paused (GUI4-C2).

## What this pass did not cover

- **Execution.**  GUI4-C2 and GUI4-C3 rest on reading until their probes run; GUI4-C3's in particular assumes `flipFacing` at a must-turn square reaches `copyFacing`'s second loop, which I traced but did not see.
- **Windows.**  No dialog, menu, tooltip or banner was opened; what the operator sees is read from the code that draws it (the tooltip width in GUI4-C5 is estimated from character counts).
- **Translation quality beyond the two new keys** and the three Spanish values; Polish most weakly (GUI4-C1's second point is flagged as a question).
- **The other lanes' changes in `4be3798a`** - `flipFacing` reading the railway (TDY3-A2/AUT3-A1), `putTheTrainsBack`'s barred put-back (TDY3-A1), the import's facing guess (REG3-C1) - were read only where they touch the interface; `MarklinControlStation`, `AutomationAPI.md`, `Readme.md` in `031a7ddb` and the catalogue of `9f5d8e23` beyond the GUI rows were not reviewed.
