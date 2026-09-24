# Documentation validation of round 3 (DCN4)

**Status:** open

**Prefix:** `DCN4`

**Reviewed:** branch `autonomy-diagram-r0` at `9f5d8e23`, 2026-09-23.  Baseline: round 3's documentation validation `DCN3` at `2f4448b6`, and every commit in `2f4448b6..9f5d8e23` - `d65df6cb` (claims), `4be3798a` (fixes), `9a9a8564` (tracker), `031a7ddb` (text), `9f5d8e23` (catalogue).

**Method:** Reading, `git show` / `git diff` / `git grep` / `git log -S`, `grep`, and read-only Python piped through stdin (nothing written to disk): the round's added lines scanned for sentences joined across a boundary and for fused words (a token rare in the repository that splits into two common ones); `tests.md` parsed with the exact regexes and splice rule `docs/manual-tests/triage.py` uses (`ANCHOR_RE`, `HEADING_RE`, `Entry.with_comment`); the Inbox recounted from `issues.md`; `findings.tsv` counted the way `regression.testTheRecordsCountTheStore` counts it; `sqlite3` on `triage.db` opened `mode=ro&immutable=1`; every citation added to `src/`/`test/` in the range resolved against the store and the tracker.  Every round-3 disposition that says Fixed was walked against its commit and its finding's Where list; every message the round changed was grepped for where it is quoted; the four new MT entries' steps and Expected were read against the code at HEAD and the frozen railway (`test/layouts/live-snapshot`).  **Nothing was built or run, no `triage.py` / `triagedb.py` / `catalog-findings.py` command was run, and nothing was written but this report.**

## A - high

None.

## B - medium

### DCN4-B1 - MT-487 to MT-490 were appended without their anchors, so the tracker's tooling cannot see them: they are missing from the generated ledger and the triage app, `verify-ledger` reports clean, and a verdict on MT-486 is spliced in under MT-490

| | |
|---|---|
| **Disposition** | Fixed - 12ef4379: the four anchors, the ledger regenerated, every new entry checked with `check --tag`.  The append was a script's: it checked `show`, which prints only verdicts, so an entry with none looked the same whether the tool could see it or not |
| **Where** | `docs/manual-tests/tests.md:25014-25119` (MT-487 to MT-490, added in `9a9a8564`); the ledger, `tests.md:29-72` ("446 of 486", `:71`); `docs/manual-tests/triage.py:248` (`ANCHOR_RE`), `:386-404` (`parse_tests_text`), `:359-380` (`Entry.with_comment`) |

Every earlier entry opens with `<a id="mt-NNN"></a>` on its own line; the four entries `9a9a8564` appended open straight at `### MT-487 - ...`.  The tracker has one parse (`parse_tests_text`), and it splits the file **at anchors only** - an entry is the text from one anchor to the next.  Emulating it read-only on HEAD's file:

- 486 entries are parsed, against 490 `### MT-` headings.  The last entry is anchor `mt-486`, and its block holds the headings `MT-486, MT-487, MT-488, MT-489, MT-490`.  Its tag, date, title and Disposition are read from the first match - MT-486's - so the four new tests do not exist as entries.
- The open set the ledger is generated from is 40 entries, ending at MT-486 - exactly the 40 rows the ledger shows.  So `verify-ledger` has nothing to report, and `regenerate-ledger` would leave the four out again: the fix is the anchors, not a regeneration.  The ledger's footer still reads *"446 of 486"* with 490 entries in the file.
- `Entry.with_comment` appends *"above the rule that closes the entry - which is the last '---' line in the block"*.  MT-486's block now ends at MT-490's closing rule, so a result Adam submits for MT-486 through the triage app lands after MT-490's *"What this is"* line - under the wrong test, the shape `DCN2-C9` was.  And none of MT-487 to MT-490 can be picked in the app's Tests tab at all.

The README makes the ledger *"where to start"* and the app the way Adam answers entries; these are the four tests the round wrote for him to see its behaviour on the railway (the reversal at a one-way platform, the route import notice, the square-named questions, the editor brought forward).  Nothing on the railway is wrong; the record is.  Nothing compensates: the guard (`verify-ledger`) knows only the entries the same parse produced.

**Verification request (no execution of Java):** read-only, either `py -3 docs\manual-tests\triage.py tests --open` (MT-487 to MT-490 absent) or the emulation: split `tests.md` on `^<a id="(mt-\d+)"></a>\s*$` and list the `### MT-` headings inside the last block (`MT-486` to `MT-490` together).  **Refuted** if the tool lists the four as entries.  **Suggested fix:** an anchor line (`<a id="mt-487"></a>` and a blank line) above each of the four headings, then `regenerate-ledger` - four new ledger rows and "446 of 490".  Worth asking what appended them: every earlier append carried the anchor, and README step 9 names this failure (*"a hand-patch that 'just adds the row'"*).

## C - low

### DCN4-C1 - DCN3-C5 is half fixed: `setAtomicRoutes`' javadoc still says the road is not chosen by the setting at the run's end, and the code asks the setting first

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933 |
| **Where** | `Layout.java:10658-10661` (`setAtomicRoutes` javadoc); `:7950-7951` (the failure handler); against `:4093` (`unlockPath`: `if (this.atomicRoutes && heldItAll)`) |

DCN3-C5 had two halves and suggested two edits: delete "or leaves one held", and replace *"decides how to give track back by whether that run gave any back early ... **not by the setting at its end**"* with "by whether that run gave any back early and, where it gave none, by the setting".  `031a7ddb` made the first edit only; the javadoc still says "not by the setting at its end", and the failure handler still says `unlockPath` chooses its two roads *"by whether there are any"*.  `unlockPath` takes the atomic road only when `this.atomicRoutes && heldItAll` - a run that gave nothing back early takes the careful road when the setting is off at its end, which is the road the javadoc's own GUI2-C1 sentence two lines later relies on.  The disposition says Fixed.  Restates DCN3-C5; what is new is that its disposition is not true.  Reading only; unreachable today (the checkbox refuses mid-run).  **Suggested fix:** DCN3-C5's second edit, at both sites.

### DCN4-C2 - TDY3-C4 is dispositioned Fixed with two of its eight sentences untouched, and two siblings of the same sentence that neither round found

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933, with TDY4-C1, and the two siblings (`checkFacings`, `testFacingFollowsTheTrack`) |
| **Where** | `AutonomyBuilder.java:639-647` (the orphaned `placementCopy` javadoc); `AutonomySession.java:6188-6189` (`facingChoices` javadoc); siblings `AutonomyChecks.java:712-718` (`checkFacings`), `test/regression/testFacingFollowsTheTrack.java:130-133` |

TDY3-C4 listed eight sites; `031a7ddb` and `4be3798a` fixed six.  Still at HEAD:

- `AutonomyBuilder.java:643`: *"With nothing authored **the first copy is used**, which is a guess"* - since GUI2-B1, `placementCopy` sends no facing to `startableCopy` (`:729`).
- `AutonomySession.java:6188-6189` (`facingChoices`): *"so the **first answer is the one a placement with no recorded facing actually gets**"* - the premise GUI3-C4's own claim calls false (`testEditorSurfaceRules.java:3315`: *"which stopped being so ..."*).  At BottomMainA the first answer is W and the build stands such a train facing E.

The same mechanism, in places no finding named:

- `AutonomyChecks.checkFacings` (`:716`): *"The build has to put it somewhere, so **it uses the first copy of the square**"* - the javadoc of the check that raises the impossible-facing finding, stating why the finding matters by a mechanism that no longer exists; DCN3-C2's grep for "falls through" missed it.
- `testFacingFollowsTheTrack.java:130-133`: the order is *"load-bearing, because **the FIRST answer is the facing a placement with nothing recorded on it actually gets**"*.  Still true on an unbarred square, which `1 - Main:6,1` is, so the test's reasoning holds there; the sentence is stated as a general rule.

Reading and `git grep`; nothing to execute.  **Suggested fix:** "the first copy trains may arrive at (`startableCopy`)" at each; and TDY3-C4's disposition corrected.

### DCN4-C3 - Sentences `4be3798a` made false: the quoted "not a station" sentence, "four reasons", "no recorded facing", "shown, ticked", and the home door's "not standing there"

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933: the sentences, and the single facing ticked again |
| **Where** | `AutonomySession.java:1530-1535`; `Layout.java:4951`; `test/core/testWhyStuck.java:398`; `AutonomySession.java:1848-1850`; `AutonomyEditorPanel.java:3750`, `:3772-3774`; `test/regression/testEditorSurfaceRules.java:69-71`; `AutonomyEditorPanel.java:5098`; `AutonomySession.java:7405`, `:7495`; `TrainControlUI.java:8222-8224` |

- **`placeableFacingsFor` quotes the retired sentence** (`AutonomySession.java:1532-1534`): a train stood on a barred copy *"is one autonomy will not start - *"It is standing on {0}, which is not a station."*"*.  Since GUI3-C1 that train is told `autolayout.why.startFacingBarred` (*"It is facing the way trains may not arrive at {0} ... Turn it round, or open that side ..."*), and `startNotStation` is reserved for a copy of a square that is no station at all (`Layout.java:4971-4976`).  This is the javadoc that states the doors' rule OB-284 asks about, so it is the one Adam's answer will be read against.
- **"The four reasons"** - `explainCannotStart(Locomotive)`'s javadoc (`Layout.java:4951`) and `testWhyStuck.testALocomotiveOffTheGraphIsToldSo`'s (`:398`).  There are five now: paused, not on the graph, facing the barred way, not a station, switched off.
- **`flipFacing`'s javadoc** (`AutonomySession.java:1848-1850`): *"Left alone where the answer is not obvious: a locomotive that is not placed, **a square with no recorded facing**, ..."*.  Since TDY3-A2 a square with no recorded facing is followed whenever the running layout has the train there - that was the defect (*"after a run it is absent - and the reversal was dropped"*).  The body comment at `:1933-1936` says so; the javadoc a caller reads does not.
- **`buildFacingMenu`: *"So the one facing is shown, ticked"*** (`AutonomyEditorPanel.java:3750`, and the claim's javadoc `testEditorSurfaceRules.java:69-71`: *"the menu shows the one facing, ticked"*).  GUI3-C4 made the tick `recorded != null && facing == recorded` (`:3774`), so with one facing and nothing recorded or on the railway it is now shown unticked.  Here the comment is the better half: on a square holding one facing every copy faces that way, so the build can stand the train no other way and the tick was true - GUI3-C4's reason (*"which need not be the first facing"*) holds only where there are two.  Narrow (no running layout, or the setup has the train where the railway does not).  Adam's call which to change; `recorded != null ? facing == recorded : facings.size() == 1` would keep both claims (the GUI3-C4 source guard looks for `facing == facings.get(0)` only).
- **The home door** (`AutonomyEditorPanel.java:5098`): *"WHICH WAY IT SHOULD FACE THERE, **where it is not standing there to say**"*.  Since GUI3-C2 `knownHomeFacing` is also null for a train standing here facing the barred way, so the question is put to it too - rightly, but the comment says it is not.  `setHome`'s `@param facing` (`AutonomySession.java:7405`, *"or null to take the facing of the train standing there, if any"*) and `homeFacingOf`'s summary (`:7495`, *"what its home there is set with"*) need the same "where a train may be brought home in it".
- **`placeableFacings`** (`TrainControlUI.java:8222-8224`, rewritten by `031a7ddb`): a barred copy is one *"a train can come to stand there **only by turning where it is**"*.  `placementCopy`'s own comment names a second way (*"or already standing when the side was barred"*), and a build of any setup recording that facing, and the put-back after a rebuild (TDY3-A1), stand it there too.

Reading and `git grep`; nothing to execute.  **Suggested fix:** the sentences, as above.

### DCN4-C4 - `AutomationAPI.md`'s REG3-C2 correction narrowed the reviewer's right wording to a case that is false: an edge of length 0 on a measured path does not unlock at once

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933, with REG4-C2 |
| **Where** | `AutomationAPI.md:453`; against `Layout.java:4900-4920` (`tailHasProvablyPassed`), `:8515-8560` (the tail queue) |

The page's reader drives `Layout` from Java.  `031a7ddb` wrote: *"A program driving `Layout` directly sets `atomicRoutes` itself, and with it off **an edge of length 0 still unlocks at once**."*  REG3-C2 had proposed *"an unmeasured path or a train with no length still unlocks each edge as soon as it is passed"*, which is what the code does: `tailHasProvablyPassed` releases at once only when `pathIsUnmeasured` (no length anywhere on the path) or when the train's length is null/0 (`behind >= 0`).  On a path with any measured edge, a 0-length edge adds nothing to `behind` and is held until measured track behind it covers the train - VAL-A1's `[0, 100, 100]` with a 250 train is the case the method's own comment says no longer releases edge 0 at once.  So the new sentence describes a fixed defect as current behaviour, and omits the case that is real (a train with no length).  The sentence before it (*"only be unlocked once the cumulative traversed edge length exceeds the current train's length"*) and this one now contradict each other for a partly measured path.

**Verification request (optional, pure function, no fixture):** `Layout.tailHasProvablyPassed(false, 0, 250)` is `false` (a 0-length edge on a measured path is held) and `tailHasProvablyPassed(true, 0, 250)` / `(false, 0, null)` are `true`.  **Suggested fix:** REG3-C2's own wording.

### DCN4-C5 - The German and Polish translations of the two new refusals say the train faces the side trains may not come FROM, which is the opposite of the way it faces; the English sentence allows both readings

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933, with TDY4-C3 |
| **Where** | `messages_de.properties:214`, `:1785`; `messages_pl.properties:214`, `:1786` (`autolayout.whyHomeStartFacingBarred`, `autolayout.why.startFacingBarred`); `messages.properties:213`, `:1785` |

At BottomMainA with arrivals from the east barred, the train these sentences are about stands on the copy arriving from the east, which faces **west** - the way a train arriving from the east travels.  English: *"It is facing the way trains may not arrive at {0}"* - "the way" can be read as the direction of travel (west, right) or as the side they come by (east, wrong).  The da, es, fr, it and nl values keep the direction-of-travel reading (*"en el sentido en que"*, *"dans le sens où"*, *"nel senso in cui"*, *"in de richting waarin"*).  German: *"steht in der Richtung, **aus der** Züge nicht in {0} ankommen dürfen"* - the direction **from which** trains may not arrive, i.e. east.  Polish: *"w kierunku, **z którego** pociągi nie mogą przyjeżdżać"* - likewise "from which".  So a German or Polish operator is told the train faces toward the barred side.  The remedy in the same sentence (turn it round, or open that side) is right either way, so nothing is done wrongly; the sentence is what the round wrote to replace one that was *"false about the square"* (GUI3-C1).  Rests on reading; a native speaker should confirm.

**Suggested fix:** in de/pl, the direction of travel ("in Fahrtrichtung der Züge, die nicht in {0} ankommen dürfen" / "w kierunku jazdy pociągów, które nie mogą przyjeżdżać na {0}"), or - better, and for every language - make the English unambiguous by naming the side (a third argument: "trains may not arrive at {0} from the {2}"), which `explainCannotStart` can compute from the copy's arrival side.  Adam's wording to choose.

### DCN4-C6 - The new MT entries: MT-487 needs an armed route to mean anything and disarms every route on the railway without a way back; MT-490 is two doors under one title; MT-488 does not name the round-3 findings its Expected checks

| | |
|---|---|
| **Disposition** | Fixed - 12ef4379: MT-487 arms a route first and gives the routes back; MT-488's sources and sentence; MT-490 titled for its one door |
| **Where** | `docs/manual-tests/tests.md` MT-487 (`:25015-25041`), MT-488 (`:25043-25074`), MT-490 (`:25098-25119`) |

- **MT-487.**  Its second Expected - *"The log has no 'Route ... is running' line for the imported routes, and none of them fires automatically"* - can only fail if the exported file had a route saved armed with an s88 condition (`route.running` is logged by the monitor `executeAutoRoute` starts only `if (this.enabled && this.hasS88())`); the steps do not ask for one, so on a railway with none armed the check passes with nothing tested (the "assert the variable, not the control" shape).  And the steps are destructive: Import deletes every route and re-adds them all disarmed (`importRoutes`, `route.deletingExisting`), so running the entry on the live railway switches off automatic firing on every route Adam had armed - the exact situation its own "What was wrong" describes (*"found out when a train ran through a sensor that used to set a road"*).  The notice says how to re-arm but not which were armed; that is REG2-C7, still open.  The entry should say: arm one route with a sensor before exporting, note which routes are armed first (or re-arm from the exported file), and re-arm afterwards.
- **MT-490.**  Titled *""Page is left out" and Fix Setup bring an open editor forward"*; the steps exercise only the label.  One verdict will be read as covering both doors (README rule 4, *"a verdict on any item is a verdict on all of them"*).  Either drop "and Fix Setup" from the title or split it (the banner needs a setup with a blocking problem, which may be why it was left out).
- **MT-488.**  **From:** names round 2's TDY2-A1, GUI2-A1, AUT2-A1; step 3's Expected (the barred-way sentence with its remedy, and Manual not saying it cannot be sent) is GUI3-C1 / AUT3-B1 / TDY3-C1's behaviour, and after a run the follow is TDY3-A2's.  Rule 3 is what lets a result be traced to the finding that earned it.

Checked and right: MT-487's menu path (**Routes -> Export / Import**, `TrainControlUI.java:18955-18973`) and the notice's two controls (`route.infoImportedArriveDisarmed` fills in **Enable Auto Execution** and **Bulk Enable**); MT-488's premise on the frozen railway (75 407 DB at BottomMainA `1 - Main:20,12` facing E, arrivals from the east barred) and each step against `flipFacing` / `copyFacing` / `explainCannotStart(loc, byHand)`; MT-489 against the paste door (below, D).  Reading; nothing to execute.

### DCN4-C7 - behaviour.md still says a barred arrival does "nothing else", and states the barred-copy rule round 2 chose and round 3 built on only inside the paste paragraph

| | |
|---|---|
| **Disposition** | Fixed - 6cb11933: behaviour.md sections 4 and 7.  Which rule the doors keep is still OB-284 |
| **Where** | `docs/reference/behaviour.md:1828-1835` (§7, *"A BARRED ARRIVAL STOPS BEING SENT THERE AND NOTHING ELSE"*), `:497-500` (§4, `moveLocomotive` *"refuses in four cases ... the target is not a destination"*), `:636-642` (the paste paragraph); against `Layout.java:4969-4974`, `:9034-9049`, `TrainControlUI.java:6491-6498`, `AutonomySession.java:790-826` |

Restates DCN3-B1's behaviour.md half (*"the rule 8370abb1 chose ... is written nowhere in behaviour.md"*); what is new is round 3's additions and one sentence that now disagrees.  At HEAD a barred arrival also means: a train standing on that copy - reversed on the throttle, turned by the Facing menu, built from a setup recording that facing - is not started by autonomy and is told why and what to do (GUI3-C1); it is put back there after a rebuild, where every placement door still refuses it (TDY3-A1, `moveLocomotive`'s four-argument form); a legacy import's guess avoids it (REG3-C1); a home does not save its facing (GUI3-C2).  behaviour.md carries one clause of this, inside the paste paragraph (*"a reversal on the throttle or the Facing menu keeps it facing that way"*).  §7's heading says the opposite of the start refusal, and §4's four refusals no longer hold for the put-back.  The code is internally consistent and each piece has a claim; behaviour.md is the document the project declares to be the intended functionality, and these are decisions taken in Adam's absence.  Reading only.  **Suggested fix:** a paragraph under §4 (or §3's "which copy a train is on IS its direction") stating the barred-copy rule once, and §7's heading qualified ("... and a train standing facing that way is not started there").  Which rule stands is part of OB-284's answer.

## D - not defects (checks that came back clean)

### DCN4-D1 - The catalogue's counts are the store's

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Read-only query: 3,991 rows, 3,634 distinct refs; `findings.tsv` counted as `testTheRecordsCountTheStore` counts it gives the same two (45 dead-citation rows excluded); every (ref, document) pair in the store is in the mirror.  behaviour.md `:2225` (*"3,991 rows for 3,634 findings"*) and open-questions.md `:354-355` (*"added 265 - five reviews and two rounds of validation - which makes 3,991 finding rows"*) agree: 183 + 82 (AUT3 13, GUI3 13, REG3 19, TDY3 18, DCN3 19) = 265, 3,909 + 82 = 3,991, 3,552 + 82 = 3,634.  Every row has a status (3,840 Closed; 87 Open; 9 Open - Adam's decision; 8 + 8 + 39 deferred/verified).

### DCN4-D2 - The round-3 dispositions in the store match their documents, and the open set is what the commit says

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Each of the 82 round-3 rows carries its document's disposition; the rows not Closed are AUT3-B2, TDY3-B2, DCN3-B1 (`Open - Adam's decision`, OB-284) and AUT3-C3 (`Open`), and with AUT2-A1 reopened the "Adam's decision" set is AUT-C2, AUT2-A1, AUT3-B2, DCN-C3, DCN3-B1, REG-B1, REG2-C3, REG2-C7, TDY3-B2 - the known deferrals plus OB-284's four.  `9f5d8e23`'s "Open for a follow-up: AUT2-C2, GUI2-C2, GUI2-C4, AUT3-C3" is the `Open` set among the round's rows.  The two exceptions to "Fixed means fixed" are DCN4-C1 and DCN4-C2.

### DCN4-D3 - The corrected older rows (DCN3-C7) landed

| | |
|---|---|
| **Disposition** | Closed - checked clean |

REG-C3 (`REG.md:183`) and DCN2-C5 (`DCN2.md:107`) now name `4132d260` for `Readme.md:128`, and the store rows carry the new text.  TDY-D9's and TDY-D13's titles are whole sentences again and are the titles in the store.  (TDY-D13's body still opens mid-sentence, *"is thrown only where ..."*; harmless - only the title reaches the store, and the document is deleted with the round.)  AUT2-A1 reads "Partly fixed ... Open - Adam's decision: the doors' one-placeable-copy rule, OB-284", status `Open - Adam's decision`.  REG3's fourteen D items have no Disposition table and were catalogued with disposition "-" and status Closed - harmless.

### DCN4-D4 - OB-284, the Inbox count and MT-482's correction

| | |
|---|---|
| **Disposition** | Closed - checked clean |

- **OB-284** (`issues.md:1108-1114`) has OB-283's shape (Kind, Raised from, Filed) and states the question as a decision.  Its scenario is the code's: the paste takes `cutFacing` W (`facingsFor` holds every copy, `TrainControlUI.java:7151-7156`), `facingAfterAPaste(placeableFacings(...), W, ...)` answers the one placeable facing E (`AutonomySession.java:7096`), and the throttle and the Facing menu stand the train on the westbound barred copy (`copyFacing`'s fallback, `:1606-1618`).  behaviour.md `:639-642` and open-questions.md `:48` point at it.
- **Inbox:** 66 entries between `## Inbox` and `## What has been picked up`, 39 OB and 27 FR - the figures open-questions.md `:42` quotes and `testTheInboxCountIsTheInboxsOwn` reads.
- **MT-482:** the correction is appended under the existing comment with a dated attribution (rule 6).  "Spent first on every kind of square since 8370abb1" is true of the rule (8370abb1's fork-rule swap completed it for a square emitted once); TDY3-D3 and AUT3-D5 found that swap alters no tail on the frozen railway, where every placed train carries a side, so the change it names is not what moved Adam's grey - but the entry's operative sentence, "a re-run on the current build is all it needs", stands either way.  Not raised.

### DCN4-D5 - The twelve joins are gone and the round made no new ones

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`git grep` at `9f5d8e23` for all twelve (`layoutas`, `as before.An`, `andcan`, `driven away.To`, `it.The settings`, `"Validate Configuration".Any`, `youonly`, `either mode.Once`, `editor.Note`, `prompt:the`, `occupyon`, `again.The comment`) matches nothing outside the review folder and the store.  A scan of the range's 781 added lines found no letter or quote followed by sentence punctuation and a capital, and no token rare in the repository (437 files) that splits into two common words.  `Readme.md:128` now reads as a sentence without "represent your layout as a graph", which v3.0.0's changelog says is gone.  (`AutomationAPI.md:10` is still one long banner line - DCN3-C4's optional re-wrap; it renders.)

### DCN4-D6 - DCN3-C1, DCN3-C2, GUI3-C5, REG3-C5 and GUI3-C3 verified site by site

| | |
|---|---|
| **Disposition** | Closed - checked clean |

- **DCN3-C1:** `TrainControlUI.copyFacing` (`:7437-7439`) and `placeableFacings` (`:8219-8226`) now say a barred copy is a fallback no placement door reaches; `writeHome` (`AutonomySession.java:7461-7466`) filters through `knownHomeFacing`; the placement class's GUI-B1 paragraph is in the past tense and `:229` reads "one it can be started from where there is one".  (`copyFacing`'s "the doors only ever ask it for a facing ... `placeableFacings`" is imprecise for the chosen-heading caller, `:7253-7254`, which asks over `facingsFor`; DCN3-D11's argument makes it true in effect - a may-turn square with one side barred keeps both headings on placeable copies.)
- **DCN3-C2:** all eleven sites now say `startableCopy` / "the first copy trains may arrive at", present-tense where it is the mechanism, past tense at `TrainControlUI.java:8108-8113` and `testAPastedTrainKeepsItsDirection.java:50-53`; the three failure messages (`:293`, `:443`, `:596`) name the current mechanism.  The ones missed are DCN4-C2's.
- **GUI3-C5:** `occupy on`; the placement class's paragraph and `:229`; `refreshAutonomyPrompt` "asked twice (once since GUI-C8)" - its body has one `refuseWhileEditorOpen` (`TrainControlUI.java:8618`); `setAtomicRoutes` "or leaves one held" removed (the other half is DCN4-C1).
- **REG3-C5:** `whyAutonomyWillNotStart` lists five answers with the Central Station one first, as the body asks it (`:24583-24595`); the tooltip comment's "FOUR reasons"; `importRoutes`' "overwritten before anything reads it - in parseRoutesFromJson" matches `entry.put("auto", false)` before `fromJSON` (`MarklinControlStation.java:4143-4148`).
- **GUI3-C3:** es `tooltipShowStationHere` "que elija", and both `promptWhich*` open with `¿`; no Spanish value with a `?` lacks one.

### DCN4-D7 - The new comments of `4be3798a` say what the code does

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`explainCannotStart(loc, byHand)`: the by-hand tier gives only paused and not-on-the-graph, and its reason is right - `isPathClear` refuses a non-station start and an inactive point only `if (this.isAutoRunning() ...)` (`Layout.java:2429`, `:2438`).  `isABarredCopyOfAStation`: on a built graph a station square's copy is `station: false` exactly when its arrival is barred (`AutonomyBuilder.java:990`), and `isSamePlaceAs` is the shared block, so "another copy of the same square is a station" is the predicate.  The four-argument `moveLocomotive` is called with `true` only by `putTheTrainsBack` (`TrainControlUI.java:6498`), so "every placement door still refuses one" holds.  `flipFacing`'s body comment and the new `facingOnTheRailway(tile, locomotive, running)`; `writeHome` / `knownHomeFacing`; the import's guess (`homeFacingsFor` is the facings of copies trains may arrive at, `AutonomyBuilder.java:1486`) and `autosetup.ui.facingsGuessed`'s "the first one that fits"; `HomeStaging.whyNotHome`'s new branch.  Round 3's new citations (23 refs in added `src/`/`test/` lines) all resolve in the store or the tracker.

### DCN4-D8 - The two new sentences are in all eight bundles, ASCII-escaped, and the menu they name exists

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Each bundle has 1,823 keys (1,821 + the two), no raw non-ASCII byte, and no ASCII apostrophe in either new value for `MessageFormat` to eat (fr and it use `’`).  `{1}` is filled with `I18n.t("autosetup.ui.menuArrivalsGroup")`, so each language names its own label; that label is the station menu's **Trains May Arrive...** submenu in the autonomy editor (`AutonomyEditorPanel.java:1612-1627`), as both sentences say.  The quoted "not a station" sentences survive only in the past tense (`Layout.java:4969`, `testATrainIsPutOnlyWhereItCanStart.java:458-460`) - apart from DCN4-C3's first item.

### DCN4-D9 - The round's test prose and the user documents' round-3 sentences checked

| | |
|---|---|
| **Disposition** | Closed - checked clean |

The five new claims' javadocs in `testATrainIsPutOnlyWhereItCanStart` describe the fixture they build (BottomMainPost's plain copy arriving from the north faces S and is barred; its turning copy arriving from the south faces S; BottomMainA's first emitted copy arrives from the east and faces W), and the class MUTATION note's `placementCopy` half is now carried by `testTheBuildPrefersACopyItCanStartFrom` (DCN3-C3).  `testAnImportedFacingGuessCanStart`'s javadoc matches `importLegacy`'s new loop and the `facingsGuessed` log line it quotes.  `AutomationAPI.md:223`'s new banner: the JSON tab is mounted only with no session (REG3-C3's reading), and **Import...** is on the autonomy menu.  `Readme.md:374`, `:379-380` and behaviour.md `:1002` are the joins only.  behaviour.md's new paste sentence: "the copy is the direction, section 3" points at §3's *"which copy a train is on IS its direction"* (`:193`).

### DCN4-D10 - MT-489 matches the paste door

| | |
|---|---|
| **Disposition** | Closed - checked clean |

On a may-turn square the paste asks the arrival side through `ArrivalSidePrompt.forPlacement` whenever `wouldAsk` - `mayTurnHere` and more than one unbarred side - and BottomMainB has no barred side in the frozen setup (`barredArrivals`), so both questions are put; both name the square (`squareNameFor`, `baseNameOf`).  (`ui.testAPastedTrainFacesTheWayTheOperatorChose.testBothQuestionsNameTheSquare` says of the same paste *"this paste does not ask it (the train fits and the walk answers)"*, while its own helper says *"The tail question comes first at a may-reverse square"* and answers it - outside this range, left for the GUI lane.)

## What this pass did not cover

- **Execution.**  Nothing was run.  DCN4-B1's check needs no JVM (a read-only tracker query or the emulation); DCN4-C4's is a pure function.  The rest rest on reading.
- **Whether the round-3 claims were red for the right reason** - the TDY, GUI, AUT and REG validators' lanes; read here only for what their javadocs and messages say.
- **Automation.md** was not changed in the range.  Its "Trains May Arrive..." section (`:133`) says only that trains are routed to the station from the sides left on; nothing tells an operator that a train turned to face the barred way at such a station will not be started, or what the new sentence tells him to do.  A lead for DCN4-C7's paragraph, not raised (the user guide is written for non-technical readers).
- **`homeCopy` against a barred home facing** (REG3's lead) and `Layout.setHomeLocomotive` holding a home to a barred copy set from the running diagram - the TDY/AUT lanes'.
- **The store's `test` and `issue` tables** end at MT-479 and OB-270; they are rebuilt from the markdown by every CLI command, so this is how the tracker works rather than a defect of the round.
