# RLA - Autonomy lane: release-readiness review of TrainControl 3.0.0

**Status:** open

**Prefix:** RLA

**Reviewed:** branch `autonomy-diagram-r0` at `1c6978c6` (2026-09-25 13:36), read-only, on 2026-09-25.  HEAD moved to `146b25eb` while I wrote this; that commit changes records only (tests.md, triage.db, and behaviour.md's sentence that with an editor open the tail list opens from the editor), and none of the findings below depends on it - RLA-C4 is about that behaviour for every door.

## Method

Read in depth, as diffs and then in the surrounding code at `1c6978c6`: every commit of `git log de75f3d1..1c6978c6` that touches `src/` - route choice by track length (`10e325bc`), OB-299's Why not Moving? sentence and its eight bundles (`ef618c8e`), the old autonomy.json import into the configuration named (`fd3237ea`), Routes > Import (`6d75bfd7`), BPV-A1 (`6033fb45`) and the tail question's owner (`aa2cb504`, MT-575) - and, because the brief names it and it landed after the last validation round's reviewers had reported, the zeros work itself (`3567d45d`, with its pins `6821c1da` and `1382b3f7`). Around them I read, whole or in the relevant methods: `Edge.isMeasured` and the place lists; in `Layout` the route cost (`costOf`, `ratioOf`, `lengthOf`), `explainCannotStart`, `whyTheStartIsRefused`, `canReachAnyDestination`, `isABarredCopyOfAStation`, `walkOneTail`, `claimUpToWhereTheRailsPart`, `measuredRouteIn`, `theApproachItselfHoldsIt`, `whyABerthCannotHoldIt`, `whyItWouldMeetItsOwnTail`, `whyTooLongForThisRoute`, `measuredRoomAtTheEndOf`, `pathIsUnmeasured`, `sanitizeMultiUnits` / `clearMultiUnitConflictsWith`, and the loader's placement block; `HomeStaging`'s length checks and tail calls; `GraphReducer.roomAfterTheLastSwitch` and `answeredAtZero`; in `AutonomySession` `importLegacy`, `whatALegacyImportLeaves`, `captureFromLayout`, `configurationToLoadAfterImport`, `rebuild`, `anythingMeasuredOn` and `routesIn`; `AutonomyCompanionStore`'s configuration methods and the bundle import's rollback; `AutonomyViewerPanel`'s Import, load, revert and save; `TailCrossedPrompt.askAfterPlacement`, `reply` and `DiagramPick.of`; `TrainControlUI`'s three locomotive edit doors and the Routes > Import door; `MarklinControlStation.changeLocAddress` and `importRoutes`; `MarklinLocomotive`'s multi-unit compatibility and `canBeLinkedTo`; `AutoLocomotiveStatus` and `AutonomyEditorPanel.composeWhy` as the callers of `explainCannotStart`. Of the tests I read the claims for route choice (`d8a36039`), MT-575 (`8a692f86`), `core.testASecondImportFillsGapsAndDoesNotOverwrite.testAnImportGoesIntoTheConfigurationNamed`, and the assertions of `regression.testTheImportDoorReadsAnOldFile` about the configuration in use and the second import; I did not audit the other superseding tests. Rules from `docs/reference/behaviour.md` (the answered-0 paragraph, the own-tail paragraph, BPV-A1's and the import's), `docs/reviews/README.md` and `FANOUT.md`; every finding below was searched for in `docs/manual-tests/findings.tsv` and is not there. One mechanical check: the `{n}` placeholders of every key in the seven translations were compared with English's, reading the bundles out of `git show` into an inline `python -c` - nothing written to disk. Nothing was compiled or run; no JVM; no git state changed; nothing under `cs2_sample_layout/` was read.

**All reading was pinned to `1c6978c6` through `git show`, not the working tree, because the working tree was being changed while I read it** - a mutation run, by the look of it: at the start `src/org/traincontrol/gui/TailCrossedPrompt.java` showed as modified, and one read of `Layout.java` line 7793 came back as `if (segment.getLength() <= 0) break;` (the zeros fix undone), which HEAD does not carry and a minute later the file no longer did. Every line number below is HEAD's.

Counts: no A, 2 B, 8 C, 5 D. Findings needing execution say so, with a verification request.

---

### RLA-B1 - Re-addressing a member of a standing multi-unit onto a standing train's address takes nothing off the graph

| | |
|---|---|
| **Disposition** | Fixed - the edit doors' sweep now also sweeps every standing multi-unit that drives the edited train (a consist linked here, or one the Central Station holds), as putting that head down again would; the head is swept as itself, so its own member standing nowhere is still no conflict and BPV-A1 stays fixed.  Claim 06ecf625 (red first: both stood), fix be3019da; mutation R1 red.  Master has the same shape; carried to 2.8.2 with the release's other ports. |
| **Grade** | B |
| **Where** | `Layout.sanitizeMultiUnits` (Layout.java:9238), called by the Change Name or Address dialog (`TrainControlUI.changeLocAddress`, sweep at :20493); `MarklinControlStation.changeLocAddress` (:3234) |
| **Needs execution** | yes - see the request |

**What BPV-A1 assumed.** `6033fb45` made the edit doors' sweep return at once when the edited locomotive stands nowhere, on the premise written into behaviour.md: *a train that stands nowhere clashes with nothing that stands*. That holds for a rename - a name drives nothing - and it is what fixes BPV-A1. It does not hold for a **member of a consist whose head stands**: the member stands nowhere, but it is driven. `MarklinLocomotive.commandedLocomotives()` fans every speed and direction command of the head out to the member's address.

**The case.** A consist M - a multi-unit linked in TrainControl - standing on a station, with member X; another locomotive Z standing on another station. Autonomy stopped. In X's **Change Name or Address** dialog the operator types Z's address - a typo is enough, because this dialog refuses no address in use (only **Add Locomotive** says `loc.errorAddressInUse`). `MarklinControlStation.changeLocAddress` then re-links every consist, and `canBeLinkedTo` keeps X in M: it compares X with M's own address and M's other members, not with trains on the railway. Then `sanitizeMultiUnits(X)` returns without asking anything - X stands nowhere. M and Z both stay on the graph.

**Its siblings disagree.** Put M down by the right-click **Place** (`moveLocomotive` -> `clearMultiUnitConflictsWith(M)`) and Z is taken off: `M.isSimultaneousMultiUnitCompatible(Z)` walks M's members, finds X's address equal to Z's, and answers false. The loader asks the same at the next start. Before BPV-A1 the edit door's sweep, asked of X, took both M (wrongly - that was BPV-A1) and Z off.

**On the railway.** Start runs both. Every command to M reaches Z's decoder, so Z drives off a platform the model shows it standing on, while autonomy also dispatches Z as a train of its own; nothing claims the track Z actually moved over. **Mitigations:** it needs a consist member re-addressed onto a standing train's decoder; the next Place of M, or the next start, sweeps it; and even the sweep cannot stop one decoder moving two physical locomotives - what it stops is autonomy running Z as a separate train while the model stands it where it was. So B, not A.

**Direction for a fix.** Ask of the edited train where it stands, and of every standing train that commands it: for each standing head whose `commandedLocomotives()` contains the edited locomotive, `clearMultiUnitConflictsWith(head)`. A member's rename still takes nothing off - the head is skipped as itself and its member stands nowhere - so BPV-A1 stays fixed.

**Verification request.** Beside `core.testALocomotiveDoesNotEvictItself`: link X to M, place M on station A and Z on station B, `changeLocAddress(X, Z's address, Z's decoder type)`, then `sanitizeMultiUnits(X)`. **Proves:** M and Z both still stand. **Refutes:** one of them is off the graph. Control: with the same edit, `moveLocomotive(M, A)` takes Z off.

---

### RLA-B2 - Importing an old autonomy.json into a configuration that exists: "Replace it with the imported one?" merges, and can stand one train on two squares, which refuses the setup

| | |
|---|---|
| **Disposition** | Fixed - for an old file into a configuration of that name the door asks to fill in what it does not have (a bundle still asks to replace), once the file's kind is known; and a train the configuration already has standing, or a home it already has, is not placed again - named in the message.  Claims 01cf0d72 (red first, each for its own reason), fix 2fa033f3; mutations R5 red, R6 red. |
| **Grade** | B |
| **Where** | `AutonomyViewerPanel.importConfiguration` (the question at :1016, asked before the file's kind is known); `AutonomySession.importLegacy` (`placedAlready` at :923, placement at :1052, home at :1088); `autosetup.ui.confirmImportOverwrites` |
| **Needs execution** | yes - see the request |

**The question says the opposite of what happens, for an old file.** The Autonomy menu's **Import** asks one question for every file whose name is taken: *"A configuration named {0} already exists.  Replace it with the imported one?"* For an exported bundle that is true - `AutonomyCompanionStore.importBundle` puts the imported configuration in place, and puts everything back if it fails. For an old autonomy.json, since `fd3237ea` the file goes into the configuration named, and `importLegacy` only fills gaps: a placement where the square has none, a home where the square has none, settings where the square says nothing. `regression.testTheImportDoorReadsAnOldFile.testASecondImportFromTheMenuKeepsAHandMadeChange` pins exactly that - it answers **Yes** to the Replace question and asserts that nothing was replaced (MT-298's rule). The behaviour is Adam's; the sentence is not.

**And the fill can put one train on two squares.** "One locomotive stands in one place" is kept across the file only: `placedAlready` is a new set per import and never reads the configuration being written into. The prompt offers the file's own name, so importing the same file a second time lands on the first import's configuration. If that configuration has been loaded and run in between - or its trains re-placed in the editor - a train stands where it stopped, the file's square for it is empty, and the import puts it there as well. The import's own dialog says nothing (`infoLegacyDuplicateLocs` counts duplicates within the file). The next time that configuration is loaded the setup is refused: *"... holds a locomotive that is also recorded as standing somewhere else.  A locomotive can only be in one place, and autonomy refuses the whole setup while it is in two"* (`autosetup.ui.checkDuplicateLocomotive`). Homes go the same way: a home moved by hand gains the file's home as a second one for the same train, and `Layout.rebuildHomeStations` keeps one by iteration order (OB-075's shape).

**On the railway.** Nothing moves - the refusal comes before anything runs - and the operator can take the train off one square in the editor. What he meets is a question whose Yes does something else, and after an evening's running a configuration that will not load. B.

**Direction for a fix.** For an old file, ask in its own words (it fills what the configuration does not already say), and seed `placedAlready` and `homedAlready` from the configuration imported into, reporting the ones skipped.

**Verification request.** On a sandbox copy of his railway, as `testTheImportDoorReadsAnOldFile` builds it: import the MT-298 file into "MT-298 import"; in that configuration move one placed train to another station (clear `loc` on its square, set it on another) and save; import the same file again from the menu, answering Yes. **Proves:** the configuration names that train on two squares, and loading it reports `checkDuplicateLocomotive`. **Refutes:** one square only.

---

### RLA-C1 - behaviour.md says the imported configuration is made the one in use; with one loaded it is not, and the dialog does not say where the trains went

| | |
|---|---|
| **Disposition** | Fixed - the message names the configuration the trains went into and, where another is in use, where to choose it; behaviour.md says the imported one is chosen only while the import writes.  Claims 01cf0d72 (red first, each for its own reason), fix 2fa033f3; mutation R10 red. |
| **Grade** | C |
| **Where** | behaviour.md, the paragraph added by `43cb3ba6`; `AutonomyViewerPanel.activateTheConfigurationNamed` javadoc (:1129); `loadAfterImport` / `AutonomySession.configurationToLoadAfterImport` |
| **Needs execution** | no |

behaviour.md: an old autonomy.json goes into the configuration named, *"created where there is none of that name, and made the one in use; the configuration in use before is left as it was."* The javadoc of `activateTheConfigurationNamed` says the same. The code makes it the one in use only while the import writes: `loadAfterImport` asks `configurationToLoadAfterImport(running, imported)`, which returns the configuration already loaded wherever one is, puts the store's pointer back on it and reloads it. `testTheImportDoorReadsAnOldFile` asserts that - *"which stays in use: importing is not a request to switch"*. So the document is true only when no configuration is loaded, which on Adam's railway is never. Meanwhile the import's message says *"placed 4 locomotives"* and the diagram shows none of them, and nothing names the configuration they went into. Fix the record to the code (or the code to the record - Adam's choice), and name the configuration in the message.

---

### RLA-C2 - Importing an old file into the loaded configuration by name: the reload writes the running railway back over what the import just wrote

| | |
|---|---|
| **Disposition** | Fixed - into the configuration running, by its name, the running layout is captured first and the reload after the import does not capture again, so placements and homes the import brought stay.  A maximum stays as the running configuration has it: the capture writes 0 for every square the railway holds, and the gap-fill keeps a stated value (MT-298), so the claim was reworked to a placement - red against e7a2f1fa's door (placed 3, the reload took all 3 back).  Claims 01cf0d72 (red first, each for its own reason), fix 2fa033f3; mutation R9 red.  Superseded 2026-09-25: round 2 refused the name (RLA2-B1), and round 3 took an old file back in without its trains, capturing first (RLD3-C1). |
| **Grade** | C |
| **Where** | `AutonomyViewerPanel.importLegacyGraph` -> `loadAfterImport` -> `load(name, true)` -> `AutonomySession.captureFromLayout` (`POINT_OPERATIONAL_KEYS`, AutonomySession.java:3167) |
| **Needs execution** | yes - see the request |

Typing the loaded configuration's name at the prompt and answering Yes writes the old file into the configuration being run. The import saves and reports what it placed and carried; then `loadAfterImport` reloads that configuration through `load(..., capture = true)`, which first captures the running layout into it by name - and the capture replaces, or removes where the running layout carries none, `loc`, `home`, `maxTrainLength`, `active`, `priority`, `speedMultiplier` and `excludedLocs` for every square that is a point of the running layout. So the placements, homes and maximum train lengths the dialog has just counted are gone again after the reload; only the shared half (names, stations, lengths, directions) survives. For placements that is Adam's rule - *"where a train IS is a fact"* (OB-183) - but for homes and maxima it is an import silently undone. Reachable only by naming the loaded configuration (the prompt offers the file's name); before `fd3237ea` this was every import onto a railway that had configurations.

**Verification request.** Sandbox copy of his railway with "Main" loaded; Import the MT-491 file from the menu, typing "Main", Yes; accept the reload. **Proves:** the homes and a station maximum the file carries, present in Main's file after the import's save, are absent after the reload. **Refutes:** they are there.

---

### RLA-C3 - A failed old-file import leaves the new configuration chosen; the bundle door puts everything back

| | |
|---|---|
| **Disposition** | Fixed - the file is read to its end before anything is saved, and a failure puts the setup back as it was (snapshot and restore, as the bundle door does).  Claims 01cf0d72 (red first, each for its own reason), fix 2fa033f3; mutation R7 red. |
| **Grade** | C |
| **Where** | `AutonomyViewerPanel.importLegacyGraph` (:1149-1300); compare `AutonomyCompanionStore.importBundle`'s rollback (:2315) |
| **Needs execution** | yes - see the request |

`activateTheConfigurationNamed` creates the configuration in memory and moves the store's pointer to it before anything is read. The door's `catch (RuntimeException)` shows *"could not be read"* and returns; nothing puts the pointer back or forgets the configuration. Worse, `save()` runs before `whatALegacyImportLeaves`, which reads `timetable`, `edges`, `commands` and `lockedges` with `getJSONArray` after only a `has()` check - so a file where one of those is not an array is imported, saved with the new configuration chosen, and then reported as unreadable; `loadAfterImport` never runs, and **Load Autonomy** resumes the imported configuration at the next start instead of the one that was running. The bundle import, its sibling, restores the shared half and forgets or restores the configuration (`forgetConfiguration`). Narrow: a genuine 2.8.1 file writes those keys as arrays; a hand-edited one need not.

**Verification request.** A copy of the MT-491 file with `"timetable": {}`; Import it from the menu into a new name. **Proves:** the message says the file could not be read, and `setup.json`'s chosen configuration is the new one. **Refutes:** the chosen configuration is still the one that was running.

---

### RLA-C4 - With the layout editor open, every door's tail question hangs from the editor, a minimised editor included (MT-575)

| | |
|---|---|
| **Disposition** | Fixed - the list hangs from the editor only while it is showing and not minimised; otherwise from the main window.  Claim 27c668af (red first: owned by the iconified editor), fix b5226b46; mutation R17 red.  The half about a main-window door with the editor showing is left as it is: the list opens in front of the window covering the main one, where it is seen; a door-by-door owner is not worth its risk now. |
| **Grade** | C |
| **Where** | `TailCrossedPrompt.askAfterPlacement` (TailCrossedPrompt.java:212); `TrainControlUI.openLayoutEditorWindow` (:6753); `DiagramPick.of` (:742) |
| **Needs execution** | yes - see the request |

`aa2cb504` hangs the list from the editor whenever `openLayoutEditorWindow()` is not null, whichever door asked. With an editor open `DiagramPick.of` declines (TDU-C1), so every tail question is the list - including the main window's own right-click **Place** and Control+X/V on the track diagram - and it now opens centred on the editor wherever that window is; on two screens that is the other screen. And `openLayoutEditorWindow` asks `isDisplayable()`, which a minimised frame is: the list's owner is then an iconified window, and on Windows an owned window of a minimised frame is hidden - a modal question the operator cannot see, blocking the application until the editor is restored. I have not run it. Direction: hang it from the editor only when the editor's door asked (the door knows), or from the window that has focus.

**Verification request.** On the real window: open the editor (Autonomy > Edit Autonomy), minimise it, then from the main window's right-click **Place** put 75 407 DB, length 5, on Tunnel. **Proves:** no question visible and the main window unresponsive until the editor is restored (or, not minimised, the list opening over the editor for a main-window door). **Refutes:** the list shows over the main window.

---

### RLA-C5 - Why not Moving?'s "Turn it round" (OB-299) sends the operator to a copy without asking whether a train may start there

| | |
|---|---|
| **Disposition** | Fixed - turning round is offered only onto a copy a train may be started from (not barred, a station, switched on) that reaches a station.  Claim ef4eeee9 (red first), fix 3af66907; mutation R15 red. |
| **Grade** | C |
| **Where** | `Layout.whyItReachesNoStation` (Layout.java:5046, the test at :5052); `isABarredCopyOfAStation` (:9396) |
| **Needs execution** | yes - see the request |

The new sentence - *"From {0}, facing the way this train faces, no station autonomy may choose can be reached ...  Turn it round, or drive it off by hand."* - is chosen wherever another copy of the square reaches a station (`canReachAnyDestination(other)`). It does not ask whether a train may be started on that copy: a copy that is no station (the barred copy `isABarredCopyOfAStation` names), a turning copy, or one switched off. Turned round onto a barred copy, the next Why not Moving? says *"Trains may not arrive at {0} facing the way this one faces ... Drive it off by hand, turn it round, or open that side"* - and turning round leads back to the first sentence. The record says that on the frozen railway both copies with a way out that reach no station are themselves barred (`974a47c0`'s note on OB-299), so there the start is refused first and this sentence is never reached: this is a trap for another railway, or for his after a change to Trains May Arrive.... Direction: offer "turn it round" only where the other copy would pass `whyTheStartIsRefused`, and otherwise the either-way sentence.

**Verification request.** Over a build of his railway: for every station square with a copy a train may start on (`whyTheStartIsRefused` null) that reaches no station, list its other copies that reach one and whether a train may start on each. **Proves:** a square whose only such copies may not be started on. **Refutes:** none on his railway (then it stays a C for the trap alone).

---

### RLA-C6 - The own-tail rule is the one length rule the answered-0 work did not reach

| | |
|---|---|
| **Disposition** | Follow-up - filed as OB-300: judging a return over a way round answered 0 throughout needs the rule to tell an answered 0 from an unmeasured one place by place, a change to a core safety rule that no loop on his railway can reach (every one passes measured platforms). |
| **Grade** | C |
| **Where** | `Layout.whyItWouldMeetItsOwnTail` (the test at Layout.java:10620); `Edge.isMeasured`'s javadoc; behaviour.md's answered-0 paragraph |
| **Needs execution** | yes - see the request |

behaviour.md now says an answered 0 is *"measured track of no length, to every length rule"*, and lists the route in, the room walk, the tail claim, the berth rule and the tail question; `Edge.isMeasured`'s javadoc lists the same. The own-tail rule is on neither list and does not ask it: a return is judged only where `travelled - routeRunWhenLeft > 0`, and `travelled` adds only lengths above 0. So a way round answered 0 throughout - measured, of no length, which by the rule holds no train at all - is not judged, and a train of any length is let round it into its own body. Every other rule the zeros work reached errs towards refusing; this one errs towards letting through. On his railway every loop passes measured platforms, so it is probably unreachable there. Direction: judge a return once the way round is measured (a length or answered throughout), and add the rule to both lists.

**Verification request.** A hand-built loop as in `core.testATrainDoesNotRunIntoItsOwnTail`, its legs given places and every place answered 0; a train of 3 whose body lies on the loop's first place; a route round the loop back to it. **Proves:** `whyItWouldMeetItsOwnTail` returns null. **Refutes:** it refuses.

---

### RLA-C7 - Balanced priority ties a route answered 0 throughout with a route of one leg nobody measured

| | |
|---|---|
| **Disposition** | Fixed in the records - the divisor's floor is right (no division by zero); `ratioOf`'s javadoc and behaviour.md now say balanced priority ties a route answered 0 throughout with one of a single unmeasured section (0167541e). |
| **Grade** | C |
| **Where** | `Layout.ratioOf` (Layout.java:299) |
| **Needs execution** | yes - a unit test beside `testAnAnsweredZeroCountsNothingInTheLength` |

`10e325bc` says *"Balanced priority divides by the same length, so it follows"*. It divides by `Math.max(1, lengthOf(path))`, so a route of 0 and a route of 1 both divide by 1 and tie, where **Over the Shortest Track** now tells them apart - and `ratioOf`'s javadoc still says distance is measured *"exactly as SHORTEST_LENGTH measures it, floor included, so the two rules cannot disagree about which of two routes is longer"*. (Checked: no division by zero.) Small, and only between routes that short; either fix the comment or give the divisor room (for example length plus one throughout).

**Verification request.** Two routes to two stations of equal priority, one over hops answered 0 throughout (length 0), one over a single unmeasured leg (length 1), path preference Balanced. **Proves:** both are chosen over repeats. **Refutes:** the 0 route always.

---

### RLA-C8 - Routes > Import's question puts every armed route's name on one line

| | |
|---|---|
| **Disposition** | Fixed - the question is wrapped to fit the screen.  Claim b1b653a0 (red first: a 1058-character line), fix babde2bb; mutation R14 red. |
| **Grade** | C |
| **Where** | `TrainControlUI`, the Routes > Import door (I18n call at TrainControlUI.java:25080); `route.ui.confirmRearmImported` |
| **Needs execution** | yes - see the request |

`6d75bfd7` gives the question the names, joined with ", ", as MT-496 asks. The sentence goes to `JOptionPane` unwrapped - the rest of the application wraps long sentences (`AutonomyEditorPanel.wrapped`, GUI4-C5) - so a backup with many routes saved armed makes a question as wide as its list of names; with enough of them wider than the screen, where the centred Yes and No can fall off its edge. How many of his routes are armed is in his route data, which I did not read.

**Verification request.** Count `"auto": true` in a Routes > Export of the test run's copy of his routes; raise the question with that file on the real window and compare the dialog's width with the screen's. **Proves:** wider than the screen. **Refutes:** it fits.

---

### RLA-D1 - The answered-0 rule errs the safe way at every rule it reached, and the planner asks the same rules

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `3567d45d`, `10e325bc`; `HomeStaging` |

Checked each door of the zeros work against its consequence, since an answered 0 stands for track that is short rather than absent. The tail claim (`walkOneTail`, Layout.java:7793) claims a 0 and walks on, so it claims more than the real tail - the safe side. The route in (`measuredRouteIn`) adds nothing for it, so it admits only on positive track. The room walk (`measuredRoomAtTheEndOf`) now answers 0 where it declined to judge, so it refuses more; the reducer's room of 0 (`GraphReducer.roomAfterTheLastSwitch`) reaches the runtime's `room + getRoomAtTheEnd()`, not its `< 0` branch, so the two read it alike. The berth rule claims an approach answered 0 throughout whole and refuses on any road sharing it. The tail question walks on and offers more sensors. The release escape (`pathIsUnmeasured`) and the non-atomic release add no distance for a 0, so edges are held longer. The editor's refusing figure (`routesIn`, `answeredThroughout`) and `anythingMeasuredOn` agree with `Edge.isMeasured`, route tiles included (`answeredAtZero` counts a tile that takes no length as answered). The Return Home planner asks `measuredRoomAtTheEndOf`, `theApproachItselfHoldsIt`, `whyABerthCannotHoldIt`, `whyItWouldMeetItsOwnTail` and `edgesATailWouldCover` - the runtime's own methods - so it cannot disagree with the runtime about a 0. The one exception is RLA-C6. Nothing to fix here.

---

### RLA-D2 - BPV-A1's rename case and its siblings: three edit doors, one sweep; placing and loading sweep first

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `6033fb45`; TrainControlUI.java:20493, :20900, :26387; Layout.java `moveLocomotive`, the loader's placement block |

A member's rename no longer asks its head whether it is compatible with its own member, so the head stays on its platform. The three doors that edit a locomotive - **Change Name or Address**, the multi-unit links dialog, and the Central Station's rename proposal - all reach the same `sanitizeMultiUnits`; `moveLocomotive` and `Layout.fromJSON` call `clearMultiUnitConflictsWith` before putting the train down, as the commit says. The only other direct placements (`Layout.java:3517`, `AutonomySession.java:2049`) re-stand the same train on another copy of its square, where no new conflict can arise. The premise's one gap is RLA-B1.

---

### RLA-D3 - Every translated message carries English's placeholders; OB-299's sentences are safe for MessageFormat

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `src/org/traincontrol/resources/messages*.properties` at HEAD |

Compared mechanically, key by key: the set of `{n}` placeholders in each of the seven translations equals English's for every key - no difference anywhere. OB-299's two new sentences carry `{0}` (and `{1}` for the either-way one) in all eight languages, use typographic apostrophes (U+2019) where the language has one, so `I18n.f`'s MessageFormat keeps its placeholders, and `{1}` is filled from the editor's own menu label (**Can Be Chosen in Full Autonomy**, `autosetup.ui.menuAutoDestination`).

---

### RLA-D4 - Importing into a named configuration leaves the loaded one loaded and untouched

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `fd3237ea`; `AutonomyViewerPanel.load` |

With another configuration loaded, the running layout is captured into it by name (`captureFromLayout(..., ui.getActiveDiagramConfiguration())`, not the store's pointer, which is on the imported one at that moment) and it is reloaded; the old file's placements, homes and facings do not reach the configuration being run - what the commit claims and the door test pins. (The wording that says otherwise is RLA-C1; naming the loaded configuration on purpose is RLA-C2.)

---

### RLA-D5 - Routes > Import's Yes count now matches what the model armed

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `6d75bfd7`; `MarklinControlStation.importRoutes` |

The door counts a route saved armed that is enabled after the import; the model enables exactly the saved-armed routes that have a sensor and were installed, and builds every other route disarmed. Every old route is deleted first, so a name can only find the imported route. The two counts can differ only for a file naming one route twice, which Routes > Export cannot write. The log and the dialog now say the same.
