# RLA2 - Autonomy lane, round 2: validating the dispositions of RLA and what the fixes left

**Status:** open

**Prefix:** RLA2

**Reviewed:** branch `autonomy-diagram-r0` at `7e2fc558` (the records commit), working tree clean (`git --no-optional-locks status --porcelain` empty), 2026-09-25, read-only.

## Method

Read `docs/reviews-2026-09-25/RLA.md` whole, and `RLU.md` and `RLD.md` whole for the findings whose fixes land in this lane's files; `docs/reviews/README.md` for the shape. Read every fix commit of `git log --oneline 146b25eb..HEAD` as a diff (`git show`), and the ones in the autonomy area again in the surrounding code at HEAD: `be3019da`/`06ecf625` (RLA-B1), `01cf0d72`/`2fa033f3`/`26b18432` (RLA-B2, RLA-C1, RLA-C2, RLA-C3, RLD-C3, RLD-C5), `ef4eeee9`/`3af66907` (RLA-C5, RLU-C4), `0167541e` (RLA-C7), `babde2bb` (RLA-C8, RLU-C2), `459444f7` (RLU-C9), `27c668af`/`b5226b46` (RLA-C4), `7e2fc558` (records); `ab3a37c2`, `e7a2f1fa`, `693dd0a1`, `bb197b16`, `66319d3c`, `e4f61822`, `b1b653a0` and `11bf5b82` only by `--stat` and message, being outside autonomy. Around them, read at HEAD: `Layout` - `sanitizeMultiUnits`, `clearMultiUnitConflictsWith`, `moveLocomotive`'s and the loader's sweeps, `explainCannotStart`, `whyItReachesNoStation`, `whyTheStartIsRefused`, `canReachAnyDestination`, `isSendableDestination`, the candidate filter's terminus clause, `ratioOf`; `MarklinLocomotive` - `commandedLocomotives`, `isSimultaneousMultiUnitCompatible`, `getModelMultiUnitLocomotives`, `setModelMultiUnitLocomotives`; `MarklinControlStation` - `syncWithCS2`'s address and multi-unit branches, `changeLocAddress`, `importRoutes`, `routesSavedArmed`; `MarklinRoute.hasS88`/`fromJSON`; `TrainControlUI` - the three locomotive edit doors, `openLayoutEditorWindow`, `isLayoutEditorOpen`, the diagram keys' editor guard, `wrappedToFit`; `AutonomyViewerPanel` - `importConfiguration` (both branches), `importLegacyGraph`, `activateTheConfigurationNamed`, `loadAfterImport`, `load`, `save`, `exportConfiguration`'s file name; `AutonomySession` - `importLegacy` whole, `captureFromLayout` whole, `POINT_OPERATIONAL_KEYS`, `configurationExtras`, `getFacing`, `findTheImportedFacings`, `configurationToLoadAfterImport`, `importBundle`, `save`; `AutonomyCompanionStore` - `save`, `snapshotSetup`/`restoreSetup`, `createConfiguration`, `importBundle` and its rollback, `importConfiguration`, `fileNameTaken`; `AutonomyBuilder`'s per-copy emission (`station`, `terminus`/`reversing`, extras carried onto every copy); `Point.toJSON` and its terminus/reversing setters; `TailCrossedPrompt` - `askAfterPlacement`, `Answer`, `whereTheAnswerGoes`, `DiagramPick.of` and its narrowed list - with all three callers of `askAfterPlacement` (`GraphLocAssign.commitAndRecord`, `LayoutRightclickAutonomyMenu`, the paste door) and their editor-open guards; `AutonomyMenu`'s Configuration submenu. Tests read: the claims above, `testWhyStuck`'s fixture, `testASecondImportFillsGapsAndDoesNotOverwrite.testAnImportGoesIntoTheConfigurationNamed`, `testAutonomyDiagramSession.testAnImportLeavesOneHomePerLocomotive`, and `testMockCentralStation`'s sync harness (to frame a request). Records: behaviour.md's answered-0, BPV-A1, OB-183 and import paragraphs, `Automation.md`'s Why not Moving? entry, OB-300 in `issues.md`. One mechanical check: the eight bundles compared in memory by a Python heredoc reading `git show HEAD:` output - nothing written to disk. Every finding was searched for in `docs/manual-tests/findings.tsv` (multi-unit, re-address, sync, capture and import, facing on import, turning stations) and is not there; related rows are named where they bear. The two known items the brief lists (the 46/44 citation count, RLD-C1) are not reported. Nothing was compiled or run, no JVM, no git state changed, nothing under `cs2_sample_layout/` read or written; the one file written is this report.

Counts: no A, 3 B, 9 C, 7 D. The three B are dispositions that did not reach a sibling door or that a fix exposed; each needs execution and carries a request.

---

### RLA2-B1 - Importing an exported configuration over the one running, by its name: "Replace it with the imported one?" is undone by the reload, which writes the running configuration's settings and timetable back over the file

| | |
|---|---|
| **Disposition** | Fixed - Import refuses the name of the configuration in use and says where to choose the file's configuration once it is imported under another; the round-1 capture into the running configuration is gone.  claims 5322fb65 (red first) and 1d9c8c66, fix cc56a7a8; mutation S1 red. |
| **Grade** | B |
| **Names** | RLA-C2 - its fix reached the old-file branch of the door and not the bundle branch |
| **Where** | `AutonomyViewerPanel.importConfiguration`, bundle branch: `importBundle` (:1093), `loadAfterImport(name.trim())` (:1115) -> `loadAfterImport(name, true)` (:686) -> `load(name, true, true)` (:758, capture :772-786) -> `AutonomySession.captureFromLayout` (:5290; `POINT_OPERATIONAL_KEYS` :3209; `globals` replaced whole at :5551) |
| **Needs execution** | yes - see the request |

RLA-C2's fix recognises that the reload after an import into the configuration running writes the running railway back over what the import wrote, and fixes it for an old autonomy.json only (capture first, reload without capturing). The other branch of the same door has the same shape. For a bundle, `store.importBundle` puts the file's configuration in place of the one named and `save()` writes it; then `loadAfterImport(name)` reloads the running configuration with `captureRunningState` true. When the name typed is the running configuration's, that capture lands on the configuration just imported: for every square the running layout holds, `loc`, `home`, `maxTrainLength`, `active`, `priority`, `speedMultiplier`, `excludedLocs`, `arrivedFrom` and `arrivedAlong` are replaced by the running layout's, and removed where it carries the default (`Point.toJSON` writes `active`, `priority` and `speedMultiplier` only when they differ from it); and `globals` - the whole settings panel, and the timetable, which rides in globals - is replaced by `Layout.toJSON`'s top-level keys. The reload then builds and saves that. `load()`'s own javadoc names the case: capturing "is exactly wrong when the SETUP is newer". What survives of the file is what the capture has no key for: the shared half, `mustReverse`/`canReverse`/`parking`, Can Be Chosen, facings of empty squares. behaviour.md (:2433) says a bundle "replaces the configuration".

**Reach.** The backup round trip. Export suggests `<configuration>.json` (`exportConfiguration`, :1410) and Import suggests the file's name, so restoring a backup of the configuration being run lands on its name and on "Replace it with the imported one?". Yes; the message says it imported; the railway runs the pre-import settings and timetable. Placements following the running layout is Adam's OB-183 rule; a maximum, a switched-off square, a priority and a timetable are not facts of the railway.

**On the railway.** Nothing moves that was not already moving that way - the railway keeps what it ran - but a restore silently does not restore, and a backup kept to recover a station's maximum or a timetable leaves them as they were. B, as RLA-B2 was: a Yes that does something else.

**Direction.** In the bundle branch, when the name is the running configuration's, reload without capturing (the setup is the newer), or capture only where trains stand before putting the file's configuration in; and say in behaviour.md which.

**Verification request.** Live-snapshot sandbox with his configuration X running, as `testTheImportDoorReadsAnOldFile` opens it. Export X from the menu to a temp file; edit the file's JSON (as the RLA-C3 claim edits the MT-491 file) so `configuration.globals.maxActiveTrains` and one station square's `maxTrainLength` hold values X does not have. Import it from the menu, accept the suggested name X, answer Yes to replace, accept the reload. **Proves:** after the reload, X's `maxActiveTrains` and that station's maximum are the values X had before the import. **Refutes:** they are the file's.

---

### RLA2-B2 - The Central Station sync re-addresses a locomotive and re-reads a Central Station multi-unit's members, and sweeps nothing

| | |
|---|---|
| **Disposition** | Fixed - the sync sweeps every locomotive it re-addresses or gives other Central Station members, as the edit doors do, when autonomy is not running.  claims 5322fb65 (red first) and 1d9c8c66, fix cc56a7a8; mutation S5 red. |
| **Grade** | B |
| **Names** | RLA-B1 - a door its fix did not reach; and 06ecf625, which pins only half of the head test |
| **Where** | `MarklinControlStation.syncWithCS2` (:1444): the address branch (:1592-1640; `setAddress` at :1617, consists re-linked after) and `setModelMultiUnitLocomotives` (:1682); `Layout.sanitizeMultiUnits` (:9253), whose three callers are all window doors (`TrainControlUI` :20549, :20956, :26446) |
| **Needs execution** | yes - see the request |

be3019da sweeps every standing multi-unit that drives the edited train - "a consist linked here, or one the Central Station holds" - but only when one of the three window doors asks. The sync is the fourth writer of a locomotive's address (GSR-B2 calls it that, for another guard). When the Central Station reports a locomotive of the same decoder type at another address, the sync sets it (deferred while autonomy runs, applied at the next sync once stopped) and re-links every consist, "the same repair changeLocAddress performs" - and asks the graph nothing. So a member X of a standing multi-unit M, given standing Z's address on the Central Station's own screen, leaves M and Z standing: RLA-B1's case exactly. A standing train given another standing train's address - which the window's door has swept since before BPV-A1 - is not swept here either.

The Central Station half is worse placed. A Central Station multi-unit's members change only on the Central Station, and reach TrainControl only through this line, which runs on every sync with no running check and no sweep: a standing Z added to a standing M's traction leaves both standing, the Central Station fans M's commands out to Z, and autonomy runs Z as a train of its own. So the `getModelMultiUnitLocomotives().contains(l)` half of be3019da's head test is asked only when a window door edits such a member, never when the membership changes; and 06ecf625 links a TrainControl consist, so dropping that half stays green.

**On the railway.** RLA-B1's consequence, and B for RLA-B1's mitigations: it needs the Central Station edit to collide with a standing train; the next load's sweep (`fromJSON`) or a Place of M clears it; and no sweep stops one decoder moving two locomotives - what it stops is autonomy running Z separately while the model stands it where it was.

**Direction.** After the sync's locomotive pass, with an autonomy layout present and not running, sweep for every locomotive whose address or Central Station members changed (or `clearMultiUnitConflictsWith` every standing train), under the layout's lock - the sync runs off the event thread.

**Verification request.** `core.testMockCentralStation`'s mock station, with an autonomy layout built as in 06ecf625: M linked to X, M standing on one station, Z on another; the served locomotive file gives X Z's address; autonomy stopped; `model.syncWithCS2()`. **Proves:** `loc.addressUpdated` is logged for X and M and Z both still stand. **Refutes:** Z is taken off. Second half, where the mock can serve a multi-unit: a `MULTI_UNIT` M standing, whose served member list adds a standing Z. **Proves:** both stand after the sync.

---

### RLA2-B3 - Imported into the configuration running, a train the old file places now reaches the running railway facing the way the square's last occupant faced

| | |
|---|---|
| **Disposition** | Fixed - on the running railway by RLA2-B1's refusal; and a train an old file places faces the way the file ran it over the square's recorded facing, keeping a recorded one only where the file cannot say.  claims 5322fb65 (red first) and 1d9c8c66, fix cc56a7a8; mutation S2 red. |
| **Grade** | B |
| **Names** | RLA-C2 - its fix is what lands these placements on the running railway; CONF-B2 and REG4-A1 are the same shape at other doors |
| **Where** | `AutonomySession.importLegacy` :1124 (`if (getFacing(tile) == null) facingToFind.put(tile, name)`); `captureFromLayout` keeps `facing` on a square with no train (:5454-5462); `AutonomyViewerPanel.importLegacyGraph` :1246-1263 and :1375; behaviour.md :1973 |
| **Needs execution** | yes - see the request |

The import works out a placed train's facing from the file - the side the old graph's one-way edges leave the square by (REG4-A1, REG4-C1) - only where the square has no recorded facing. A recorded facing is never cleared: an empty square keeps its last occupant's (the capture: "that is the better guess for the next one"; behaviour.md: "no evidence that it is this train's"). So a train the import places on a square some other train once stood on takes that train's facing; the file's evidence is not consulted, and nothing is counted (`facingsInvented` and `facingsNotHeld` stay 0). The builder stands a placed train on the copy its facing names, so this is which way autonomy drives it out.

Before RLA-C2's fix this could not reach the railway running: the reload's capture took an import's placements back out of the configuration running. The fix captures first and reloads without capturing, so the placements the import adds - trains the running layout has nowhere, on squares it has empty - are loaded; and on his railway nearly every station has had a train on it, so nearly every such train gets a previous occupant's facing. The same has held since 6cb11933 for a second import into any configuration that has been run.

**On the railway.** A train modelled facing one way and standing the other is driven off the wrong end, onto track its route did not claim. CONF-B2 (a placement door keeping the previous occupant's facing) was graded B; so this. Mitigations: an old file imported by name into a configuration that has run; a train that configuration has nowhere; the message counts it as placed and the diagram shows its facing.

**Direction.** For a train the import places, the file's facing wins over the square's recorded one - a facing on an empty square is not this train's. Whether an old file's placements should reach the railway running at all is RLA2-C4's question.

**Verification request.** Live-snapshot sandbox, his configuration X running. Pick a train T of the MT-491 file whose square S is empty in X once T is taken off the graph; take T off; set X's `facing` at S, in the store, to the side opposite the one the file's edges leave S by (as an occupant facing the other way would have left it) and reload X without a capture. Import the MT-491 file from the menu into X by name, Yes. **Proves:** after the reload T stands on S facing the pre-set side, and the import reports no facing guessed or not held. **Refutes:** T faces the side the file ran it. **Control:** the same file into a new configuration faces T the file's way (MT-491's claim).

---

### RLA2-C1 - behaviour.md still says a locomotive edit sweeps only where the edited train stands

| | |
|---|---|
| **Disposition** | Fixed in the records - behaviour.md states the rule the code has (cc56a7a8). |
| **Grade** | C |
| **Names** | RLA-B1 - a document its fix left false |
| **Where** | `docs/reference/behaviour.md` :1950-1957; be3019da changed only `sanitizeMultiUnits`' javadoc |
| **Needs execution** | no |

The paragraph BPV-A1 added is headed "A locomotive edit takes a train off the graph only through the edited train's own presence" and says the doors' "sweep for multi-unit conflicts asks only when the edited train stands on the graph: a train that stands nowhere clashes with nothing that stands." be3019da reversed exactly that premise - a member standing nowhere is driven by a standing head, and the sweep now asks of every such head - and wrote the new rule into the javadoc, not here. behaviour.md is the intended behaviour; as it reads, RLA-B1's case is the rule and be3019da the defect. Rewrite the heading and the sentence: an edit sweeps from the edited train where it stands and from every standing multi-unit that drives it, as putting that multi-unit down again would; a member's rename still takes nothing off.

---

### RLA2-C2 - Change Name or Address: a refused new name returns after the address has changed, before the sweep

| | |
|---|---|
| **Disposition** | Follow-up - filed as OB-301: pre-existing, and it needs two mistakes in one dialog; the next Place or load sweeps it. |
| **Grade** | C |
| **Names** | RLA-B1 - an exit of the door it names that skips the sweep |
| **Where** | `TrainControlUI.changeLocAddress` (:20433): the address is applied at :20484; the name refusals return at :20496, :20505, :20514 and :20529; the sweep (:20549) and the refresh before it are skipped |
| **Needs execution** | yes - see the request |

The door applies the new address first and checks the new name after. An operator who changes both, and whose name is refused - empty, too long, already a locomotive's, or holding a character routes cannot parse - is shown the refusal and the method returns: the address change stands, and neither the multi-unit sweep nor the refresh (buttons, route list, selector, station labels) runs. Given another standing train's address, the edited train (standing, or a member of a standing multi-unit) and that train both stay on the graph: RLA-B1's consequence, through the door RLA-B1 named. Pre-existing, and narrow - two mistakes in one dialog. Direction: check the name before applying the address, or sweep and refresh on every exit once the address has changed.

**Verification request.** The dialog driven by an answerer, as the window tests drive theirs: train X standing on A and Z on B; X's Change Name or Address with Z's address and the name of an existing locomotive; OK. **Proves:** after "already exists", X has Z's address and X and Z both stand. **Refutes:** Z is taken off.

---

### RLA2-C3 - A home the configuration already has is skipped without a word, and nothing claims the home half

| | |
|---|---|
| **Disposition** | Fixed - a home the configuration already has is kept and named in the message.  claims 5322fb65 (red first) and 1d9c8c66, fix cc56a7a8; mutations S3 red, S4 red. |
| **Grade** | C |
| **Names** | RLA-B2 - its disposition says the home half is "named in the message"; it is not |
| **Where** | `AutonomySession.importLegacy` (seed :951-977, homes :1130-1140); `LegacyImport.duplicateHomes` (:534), read nowhere in `src/` |
| **Needs execution** | no |

The disposition and 2fa033f3's message: a train the configuration already has standing, "or a home it already has, is not placed again - named in the message." Placements are named (`infoLegacyAlreadyPlaced`), and 01cf0d72 asserts it. Homes are not: the seed puts the configuration's homes into `homedAlready`, a file home for one of those trains goes to `duplicateHomes++`, and no door reads `duplicateHomes` - its javadoc says it is "Counted so it can be said out loud", OB-075 (194202ba) counted it for that reason, and nothing has ever said it. behaviour.md's sentence ("a train that already has a home gets no second one") is accurate; the disposition overstates. No claim covers the home half: dropping the home seed from the loop stays green. Consequence: the configuration keeps its own home, which is right, and the operator is not told the file's was dropped. Direction: a line in the message, as for placements, and a claim beside `testASecondImportDoesNotStandATrainTwice` that moves a home.

---

### RLA2-C4 - RLA-C2 is fixed for homes, reversed for placements, and not fixed for maxima

| | |
|---|---|
| **Disposition** | Fixed as RLA2-B1 for placements - nothing is imported into the configuration running.  For maxima, not a defect: a 0 is how a configuration states "no limit", by hand or by a capture, and the gap-fill keeps a stated value as it keeps any other; behaviour.md now says so.  Superseded 2026-09-25: its premise was false - the editor stores a default as nothing, only a capture writes 0 (RLA3-B1, RLU3-C4) - and the question is put to Adam. |
| **Grade** | C |
| **Names** | RLA-C2 - "Fixed" overstates, and the placement half was decided in the fix commit |
| **Where** | `AutonomyViewerPanel.importLegacyGraph` :1239-1263, :1375; `AutonomySession.importLegacy`'s zero rule (:1148-1160); `Point.toJSON` (`maxTrainLength` written for every station); claim `testAnImportIntoTheConfigurationInUseKeepsWhatItBrought` as reworked in 2fa033f3 |
| **Needs execution** | no |

RLA-C2 said: the reload taking the import's placements back is Adam's rule (OB-183); taking back its homes and maxima is an import silently undone. After 2fa033f3:

- **Homes: fixed.** The capture first removes homes the running layout lacks, the import fills them, and the reload does not capture.
- **Placements: reversed.** Trains the running layout has nowhere are now placed from the old file onto squares it has empty, and loaded onto the running railway; the reworked claim pins exactly that (`brought.size() == placed`, red when the reload captured). That may be what Adam wants - behaviour.md's exception for an edit about a train's placement - but it is the half the finding called his rule, and it was decided in the fix. Flagged for him, with RLA2-B3 (their facing) as its cost.
- **Maxima: not fixed**, and the disposition says why: the capture writes `maxTrainLength: 0` for every station, and the gap-fill keeps a stated value. But that 0 is the serialiser's default - `Point.toJSON` writes the key for every station whatever it holds - not a statement; and the importer itself reads a 0 in the file as none ("A ZERO CAPACITY IS NOT A CAPACITY ... Zero is what 'no limit' already means"). So once a configuration has been loaded and captured, no station maximum from an old file can ever arrive in it, running or not, and the message does not say so (the `settings` count simply omits them). Whether a 0 blocks a file's value is Adam's call; the disposition's "rightly keeps" rests on reading the capture's 0 as his.

---

### RLA2-C5 - RLA-C4 fixed a state no door reaches, and its claim builds that state by calling the prompt directly

| | |
|---|---|
| **Disposition** | Fixed in the records - RLA-C4 is recorded as hardening no door reaches today, and its claim's javadoc says it pins the owner rule (cc56a7a8). |
| **Grade** | C |
| **Names** | RLA-C4 - "Fixed" for an unreachable case; its premise contradicts RLU-D1 and RLD-D7, which the records did not reconcile |
| **Where** | `TailCrossedPrompt.askAfterPlacement` (:196-226); its callers `GraphLocAssign.commitAndRecord` (:317), `LayoutRightclickAutonomyMenu` (:1313), the paste door (`TrainControlUI` :8218); their guards - the right-click menu shows only `menuEditorOpen` (:371) and gathers nothing (:222), the diagram keys refuse (:7037), the tile menu stands down (:4634); `openLayoutEditorWindow` (:6807); claim 27c668af |
| **Needs execution** | no |

With an editor open - a minimised one included, since `isLayoutEditorOpen` asks `isDisplayable` - every main-window door that raises the tail question refuses before it places anything; RLU-D1 and RLD-D7 checked this and said so. The only door that reaches the list with an editor open is the editor's own Place... / Edit Locomotive..., which cannot be used while its window is minimised, and whose question is modal and answered before the editor can be. A question already waiting on the diagram (FR-100) is a `DiagramPick`, whose narrowed list hangs from its own small window. So RLA-C4's premise - that with an editor open "every tail question is the list - including the main window's own right-click Place and Control+X/V" - does not hold, and neither of its cases is reachable; b5226b46 is harmless hardening. The claim reaches the state only by calling `askAfterPlacement(..., ui, ...)` itself with the editor iconified, "as the main window's doors ask it" - which they then cannot; its red-first was against a state no door makes. Direction: record RLA-C4 as not reachable, hardened; say in the claim that it pins the owner rule rather than a door.

---

### RLA2-C6 - RLA-C5's claim pins the one clause the builder never lets differ between copies; the barred-copy clause is unpinned

| | |
|---|---|
| **Disposition** | Fixed - one rule for a copy a train may be started from, asked by the start and by "turn it round"; a pin whose other copy is no station (testTurningRoundIsNotOfferedOntoACopyThatIsNoStation).  claims 5322fb65 (red first) and 1d9c8c66, fix cc56a7a8; mutation S7 red. |
| **Grade** | C |
| **Names** | RLA-C5 - a claim whose fixture supplies the answer |
| **Where** | `Layout.whyItReachesNoStation` (:5052-5068); `AutonomyBuilder` :1009 (`station` = `isStation() && arrivalAllowed(node)`) and :1147-1166 (`active` carried onto every copy); `Layout.fromJSON` :12484, the only `setActive` in `src/`; claim `testWhyStuck.testTurningRoundIsOfferedOnlyOntoACopyATrainMayStartFrom` (ef4eeee9) |
| **Needs execution** | no |

The fix asks three things of the other copy: not barred, a station, switched on. On a built railway `active` belongs to the square and is written onto every copy, and the loader is the only thing that sets it - so the other copy is switched off only when the train's own copy is, and then `whyTheStartIsRefused` answers `startInactive` first and this sentence is never reached. `station` is per copy: a copy trains may not arrive at is emitted as no station, which is what `isABarredCopyOfAStation` names - so "not barred" and "a station" are the clauses that can bite, and the barred copy is the case RLA-C5 led with. The claim switches one copy off with `Point.setActive` on a hand-built layout, a state no door builds, and pins that clause; drop the barred and station clauses and it stays green. The three clauses are also a hand copy of `whyTheStartIsRefused`'s ("The start rules `whyTheStartIsRefused` asks"), so a rule added there will not reach "turn it round". Direction: a claim whose other copy is barred (that side's arrivals closed); and one predicate both methods ask.

---

### RLA2-C7 - The either-way sentence says "a station where trains turn round is never chosen"; autonomy chooses a terminus

| | |
|---|---|
| **Disposition** | Fixed - the clause is gone from the sentence in all eight languages, the user guide and behaviour.md; the remedy names the side a train would arrive on instead (cc56a7a8); mutation S12 red. |
| **Grade** | C |
| **Names** | RLU-C4 - its reading of `isSendableDestination`, which 3af66907 wrote into eight languages and two documents |
| **Where** | `autolayout.why.startReachesNoStationEitherWay` in all eight bundles; `Automation.md` :311; behaviour.md :705; `Layout.isSendableDestination` (:11236); the candidate filter (:4474, :4828); `AutonomyBuilder` :1191 |
| **Needs execution** | no |

RLU-C4 read `isSendableDestination`'s `!isReversing()` as "not a station marked as turning trains round", and 3af66907 put that in the sentence ("switch it on and tick "{1}" - a station where trains turn round is never chosen"), in the user guide ("a station where trains turn round is never chosen") and in behaviour.md ("and not one where trains turn round"). On a built railway a station copy that turns trains round is emitted as a terminus - `json.put(stops ? "terminus" : "reversing", true)` - and only a copy that is no station is emitted reversing. A terminus passes `isSendableDestination`, so `canReachAnyDestination` counts it and this sentence is not shown where one is reachable, and autonomy sends any train that can reverse to it (the filter refuses a terminus only to one that cannot: `!end.isTerminus() || loc.isReversible()`). So the clause is false, and as advice it misleads: making a reachable station one where trains turn round is one way to end this sentence, and an operator who reads it about his termini may unmark them. No railway effect. Direction: drop the clause in all eight languages and both documents; the remedy is a station switched on with Can Be Chosen ticked.

---

### RLA2-C8 - With RLA-C6 deferred, behaviour.md and `Edge.isMeasured` still say an answered 0 is measured track to every length rule

| | |
|---|---|
| **Disposition** | Fixed in the records - behaviour.md and Edge.isMeasured name the own-tail exception and OB-300 (cc56a7a8). |
| **Grade** | C |
| **Names** | RLA-C6 - the follow-up is sound; the two documents it named were left saying the opposite |
| **Where** | behaviour.md :1022; `Edge.isMeasured`'s javadoc (:496); OB-300 in `issues.md` |
| **Needs execution** | no |

OB-300 is filed with the claim and the direction, and the reason to wait holds as recorded (every loop on his railway passes measured platforms). But behaviour.md still says an answered 0 is measured track of no length "to every length rule", and `Edge.isMeasured` "The one question every length rule asks", while one length rule - the own-tail rule, which the Return Home planner asks too - does not ask it and errs towards letting a train through. OB-300's direction is to add the rule to both lists once fixed; until then both state what the code does not do. Direction: one clause in each naming the exception and OB-300.

---

### RLA2-C9 - Three comments the round's fixes left false

| | |
|---|---|
| **Disposition** | Fixed - the three comments (cc56a7a8, and 5322fb65 for the test's javadoc). |
| **Grade** | C |
| **Names** | RLA-B1, RLA-B2, RLA-C3 |
| **Where** | `AutonomySession.java` :634; `MarklinLocomotive.java` :1180 and :1217; `test/regression/testTheImportDoorReadsAnOldFile.java` :208 |
| **Needs execution** | no |

- `AutonomySession` :634, `importLegacy`'s javadoc: "the only caller saves immediately afterwards (`AutonomyViewerPanel.importLegacyGraph`, which calls `save()` on the next line)". Since 2fa033f3 the door reads the file to its end and chooses the running configuration again before saving, and a failure before the save is rolled back without one; the save is neither the next line nor certain.
- `MarklinLocomotive` :1180 and :1217: "`Layout.sanitizeMultiUnits` is the only production caller and it asks both ways". The asker is `clearMultiUnitConflictsWith`, reached from `sanitizeMultiUnits` (for the edited train and, since be3019da, each standing head), from `moveLocomotive` and from the loader. Stale since 6033fb45; be3019da added a route to it.
- The MT-298 claim's javadoc (:208): "The second import is answered Yes when the door asks whether to replace the configuration of that name." The door now asks whether to add what the file has (`confirmImportFillsGaps`), and the RLA-B2 claim beside it asserts the replace question is not asked.

---

### RLA2-D1 - RLA-B1's sweep asks of each standing head what placing it asks, and BPV-A1 stays fixed

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | be3019da (`Layout.sanitizeMultiUnits` :9253, `clearMultiUnitConflictsWith` :9285); 06ecf625 |

`sanitizeMultiUnits(l)` sweeps from `l` where it stands, then collects every standing head that links `l` or holds it as a Central Station member and sweeps from each. `clearMultiUnitConflictsWith(head)` skips the head itself and asks `isSimultaneousMultiUnitCompatible` both ways; with X's address now Z's, the head's `commandedLocomotives()` walk finds it and Z goes. A member's rename sweeps from its head, whose member stands nowhere, so the head stays - BPV-A1. The head list is built after the first sweep, and each head is checked as still standing before its own, so nothing taken off is asked again. The claim links X to M, places M and Z, re-addresses X through the model, asserts both preconditions (still linked, now incompatible) and then that the head stays and Z goes; with the head loop removed it is red. The eviction is said in the log only (`autolayout.warnRemovedConflictingLocomotive`, naming Z, the square and M), as the placement sweep's is; that the door editing X says nothing on screen about Z leaving its platform is Adam's call, not a defect of the fix. The Central Station half of the head test is RLA2-B2.

---

### RLA2-D2 - RLA-B2's placement half: the seed reads the shape every writer writes, and the message names the trains

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `AutonomySession.importLegacy` :951-977, :1103-1112; 01cf0d72 `testASecondImportDoesNotStandATrainTwice` |

The seed reads the configuration imported into, keyed by square, `loc` as `{name}` - the shape `placeLocomotive`, `captureFromLayout` and the import itself all write. The order is unknown locomotive, then standing already, then twice in the file. A train standing on the same square is passed over earlier by `!extras.has(LOCOMOTIVE)`, so `alreadyPlaced` means "elsewhere", as `infoLegacyAlreadyPlaced` says. On RLA-C2's path the seed reads the capture just made, so the running layout's trains count as standing. A first import seeds from an empty configuration, so MT-491's "placed 4" is untouched. The claim moves one train within the configuration, imports again from the menu, and asserts one square and the named message.

---

### RLA2-D3 - RLA-C1 and RLD-C3: the message's menu path is the menu's own, and setup.json keeps the configuration running

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `AutonomyViewerPanel.importLegacyGraph` :1271-1273, :1362-1366; `AutonomyMenu` :297 |

`{2}` is `menuAutonomy` + " > " + `menuConfigurations(inUse)`, which is how `AutonomyMenu` titles the submenu while that configuration runs. The running configuration is made the store's choice before `save()`, so a declined or refused reload leaves setup.json naming it; `load()`'s revert and its save-on-success keep that. With nothing running the imported configuration is chosen, saved and loaded, and behaviour.md's rewritten paragraph says both cases.

---

### RLA2-D4 - RLA-C3: a failed old-file import puts back everything it touched before the save

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `AutonomyViewerPanel.importLegacyGraph` :1177, :1229-1275, :1381-1388; `AutonomyCompanionStore.snapshotSetup`/`restoreSetup` |

The snapshot is taken before the configuration is created and chosen and before repeated-sensor pages are shut, and holds the shared half, deep copies of every configuration and the pointer; `restoreSetup` puts all three back. `createConfiguration` writes no file, and nothing is saved before `saved = true`: the capture of RLA-C2's path, `importLegacy` and `whatALegacyImportLeaves` (its strict `getJSONArray` reads) all run inside the snapshot. The claim's `"timetable": {}` throws in `whatALegacyImportLeaves` and asserts no configuration made, the pointer, and setup.json on disk.

---

### RLA2-D5 - RLA-C7's records are true, and Routes > Import's question names exactly the routes the model re-arms

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `Layout.ratioOf` (:295); behaviour.md :1034; `MarklinControlStation.routesSavedArmed`, `importRoutes`; `MarklinRoute.fromJSON` :1320, `hasS88` :1192 |

`ratioOf` divides by `Math.max(1, lengthOf(path))`; its javadoc and behaviour.md now say a route of 0 ties with a route of 1 under balanced priority, and why. `routesSavedArmed` now keeps a route only where `Math.abs(s88) > 0`, the value `fromJSON` builds the route with and `hasS88()` tests, and `importRoutes`' re-arm loop asks `hasS88()` - so the question's names (RLA-C8, RLU-C2) and the routes Yes arms are the same set. `wrappedToFit` breaks only at spaces and keeps the paragraph breaks.

---

### RLA2-D6 - The eight bundles agree after the round's five new keys

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `src/org/traincontrol/resources/messages*.properties` at HEAD |

Read in memory from `git show HEAD:`: 1,836 keys in each of the eight; for every key the same set of `{n}` placeholders as English; no byte above 127; no straight apostrophe in a value with a placeholder; the only empty values Italian's and Polish's plural suffix. The four import keys (`infoLegacyAlreadyPlaced`, `infoLegacyImportedInto`, `infoLegacyImportedNotInUse`, `confirmImportFillsGaps`) and `logTailAnswerDroppedEditorOpened` are in all eight with their placeholders.

---

### RLA2-D7 - RLU-C9's drop: the question asks about an editor as it is put and as it is answered, and all three doors read it

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Where** | `TailCrossedPrompt.askAfterPlacement` :202, :221-225; `GraphLocAssign` :326; `LayoutRightclickAutonomyMenu` :1322; `TrainControlUI` :8226 |

`editorWasOpen` is read before the question and `isLayoutEditorOpen` again after the reply. Each of the three doors ANDs `!answer.anEditorOpenedInTheWait()` into `setupStands`, which `whereTheAnswerGoes` requires, so neither the setup nor the running copy gets the road, and `noteADroppedAnswer` logs the new line. An editor opened and closed again within the wait lets the answer through, which is right: its Cancel restored before the answer was written. The only door that asks with an editor already open is the editor's own, whose list is modal, so it has no wait.
