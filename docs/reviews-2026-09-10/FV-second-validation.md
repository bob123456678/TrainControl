# A second validation, of the FIXES rather than the findings

**Status:** open

**Prefix for citing these findings elsewhere:** `FV3`

**Not `FV`, and that is not a preference.** The brief said `FV` had been checked free. It is not:
`triage.db` holds thirteen rows under `FV` (from `2026-08-24-fix-verification.md` and
`2026-08-02-final-validation-pass.md`) and twenty-six under `FV2`, several of which are cited from
`src/` by name - `HomeStaging.java:929` cites `FV2-B2`, `:1694` cites `FV2-C6`. `FV3` is free: no row
in the catalogue, no citation anywhere in `src/`, `test/` or `docs/`. The file keeps the name the brief
gave it; the citation prefix is the thing that has to be unique, and this collision is the exact case
the README's prefix rule exists for. **Cite these findings as `FV3-A1` and so on.** Recorded again as
`FV3-C7` so the next person does not repeat the check.

Reviewed 2026-09-10/11 on branch `autonomy-diagram-r0` at `0f46aff4`, with `src/`, `test/`, `docs/` and
`build.xml` clean throughout.

---

## What was validated, and how

Every one of the **32 dispositioned findings** in [`N8-eight-day-review.md`](N8-eight-day-review.md)
(8), [`S14-application-review.md`](S14-application-review.md) (14) and
[`NSV-validation.md`](NSV-validation.md) (10). The question asked of each was the brief's: **is the fix
right, is it complete, and is it worse than the defect?**

**What was executed.** Fifteen test classes came back with a result, through `docs/tools/one.sh`:

```
--- core.testRoutes                                  30 tests, Failures: 1, Skips: 0   <- FV3-B1
--- core.testParseCS2Layout                          27 tests, Failures: 0, Skips: 0
--- core.testMultiUnitMembership                     14 tests, Failures: 0, Skips: 0
--- core.testAutoLayout                              33 tests, Failures: 0, Skips: 0
--- core.testRouteRoundTrip                           6 tests, Failures: 0, Skips: 0
--- core.testParseCS3Routes                           9 tests, Failures: 0, Skips: 0
--- core.testRouteCommandParity                       3 tests, Failures: 0, Skips: 0
--- core.testAutonomyDiagramSession                 123 tests, Failures: 0, Skips: 0
--- regression.testPageIdsAreDurable                  19 tests, Failures: 0, Skips: 0
--- regression.testDeleteForgetsTheWholeSquare         2 tests, Failures: 0, Skips: 0
--- regression.testTheDiagramCeiling                   4 tests, Failures: 0, Skips: 0
--- regression.testEveryCitationResolves               4 tests, Failures: 0, Skips: 0
--- regression.testEveryTestIsInTheBattery             5 tests, Failures: 0, Skips: 0
--- regression.testSwitchingToACentralStationLayout   12 tests, Failures: 0, Skips: 0
--- regression.testTheGoldenLayoutHoldsTogether        6 tests, Failures: 0, Skips: 0
--- regression.testRenameRoundTripThroughTheUIPath      3 tests, Failures: 0, Skips: 0
--- regression.testARunSurvivesAPageRename              2 tests, Failures: 0, Skips: 0
--- regression.testDataSafetyRoundTrips                 2 tests, Failures: 0, Skips: 0
--- core.testAdvancedRoutes                            16 tests, Failures: 0, Skips: 0

--- core.testLocomotive                              HUNG, no result - FV3-B1
```

The last four were run late, after the hung JVM had been reaped and the runner had come free, together
with second runs of `core.testAutonomyDiagramSession` (123, 0, 0) and `core.testMultiUnitMembership`
(14, 0, 0) against the working tree as the `FV3-A1` repair left it - so that repair breaks nothing that
is measured. `regression.testRenameRoundTripThroughTheUIPath` deserves a note: it drives
`LayoutPageEdit.renameOrDuplicate`, which is the method `FV3-A2` is about, and it is green - it asserts
about ids, names and the setup and never looks at a link tile.

`core.testLocomotive` did not return. A thread dump taken while it was parked (`jstack`, read-only)
names the exact line, and it is a finding of its own - `FV3-B1`. Because the hang holds the runner's
user-wide lock and I am not permitted to kill a process, the four classes queued behind it
(`core.testAdvancedRoutes`, `regression.testRenameRoundTripThroughTheUIPath`,
`regression.testARunSurvivesAPageRename`, `regression.testDataSafetyRoundTrips`) were not run. They are
listed under "What I could not execute".

**Two probes, written to the scratch directory and never to `test/`.** Each was compiled with
`javac -d <scratch>` against `src` on the sourcepath and run from a plain `main`, so no class was added
to the repository, `build.xml` was not touched, and neither `regression.testEveryTestIsInTheBattery`
nor `regression.testSwitchingToACentralStationLayout` could be reddened by their existence - both were
run afterwards and are green. This is deliberately not what the two previous passes did: `n8Probe.java`
and `nsvProbe.java` were put in `test/`, and one of them reddened
`regression.testSwitchingToACentralStationLayout` for a whole afternoon (`S14-D7`).

- `fvProbe` drives `LayoutDiagram.writeIndexAndKeepLinksAimed` with the page list built exactly as
  `LayoutPageEdit.renameOrDuplicate` and `TrainControlUI` build it, and prints where each arrow lands.
  Evidence under `FV3-A2`.
- `fvProbe2` writes an autonomy setup over two pages, then reopens it three ways - page absent (the
  behaviour before `NSV-B3`), page replaced by the blank placeholder (`NSV-B3` as shipped), page really
  there (the control) - and prints `pagesSafeToJudge()`, whether the save declined, and whether the
  setting is still in the file afterwards. Evidence under `FV3-A1`.

**Mutations, each taken against a whole-file copy in the scratch directory and restored byte for byte
(md5 verified).**

| # | mutation | run | result |
|---|---|---|---|
| 1 | `@Test(enabled = false)` on the four tests `4c2d7415` added to `testRoutes` | `core.testRoutes` | 26 tests, **1 failure** - so the failure is not caused by the new tests |
| 2 | `S14-C7`'s `else if (command == CMD_ACC_SENSOR)` back to a bare `else` | `core.testRoutes` | 30 tests, **1 failure** - so the failure is not caused by that fix either |
| 3 | `assertTrue(model.setFeedbackState("4", true))` added as a precondition at `testRoutes:310` | `core.testRoutes` | **red at line 310**: `feedback 4 is in the database expected [true] but found [false]`. That is `FV3-B1` proved |
| 4 | `NSV-C3` undone: `addRowsAndColumns` back to `roomToGrow(1, 1)` | `regression.testTheDiagramCeiling` | **4 tests, 0 failures** - the round's new ceiling test cannot fail for the defect it is filed under |

**Measured without running anything.** A Python re-implementation of the index reader over all thirteen
`gleisbild.cs2` files in the tree (file order, duplicate names, stated ids); a read-only simulation of
`triagedb.prune_findings` against a COPY of `triage.db` for all three live review folders; a byte scan
of the eight message bundles; the `d86914a9..0f46aff4` diff in full.

**`cs2_sample_layout/` was read and never written.** `one.sh`'s before-and-after fingerprint did not
fire on any run. The three files `git status` lists there were modified before this pass began.

**The battery was not run**, on the brief's instruction - and could not have completed in any case
(`FV3-B1`).

---

## Verdicts

| id | one line | verdict |
|---|---|---|
| N8-A1 | a page link is a position in the name-sorted page list, so every page operation re-aims every arrow | **FIX WRONG** - see `FV3-A2`. Add, Rename, Duplicate and Combine re-aim against a list that is not name-sorted, so they change nothing; Delete is the only one that works |
| N8-B1 | the duplicate-id withdrawal deletes offsets the file attributes unambiguously | **FIX CONFIRMED** - keyed by name; the test catches the key-by-id mutation. Two cautions under the table |
| N8-B2 | the rebuild was made conditional on the store and four callers change the diagram | **FIX INCOMPLETE** - `clear()` still forgets the page BEFORE emptying it (`FV3-C3`), and the cut path now calls the lazy session builder (`FV3-C6`). Nothing pins either half |
| N8-B3 | the catalogue holds fifteen findings `X8V` never made | **FIX CONFIRMED** - measured: the fifteen are gone, `X8V-C1` and `X8V-C3` read `closed`, and a read-only prune simulation over all three live folders would delete nothing |
| N8-C1 | `fillCombinedPage` grows a page past `MAX_SIZE` with no check | **FIX INCOMPLETE** - the ceiling is asked after the page has been created, indexed and excluded, so a refusal strands a stray page (`FV3-C4`) |
| N8-C2 | `behaviour.md` says nothing about the route-command delay floor | **FIX CONFIRMED** - §7b written; two inaccuracies in it under `FV3-C5` |
| N8-C3 | `Layout.java:2404`'s citation names the method the comment is inside | **FIX CONFIRMED** |
| N8-C4 | the `showTab` citation says the same thing twice | **FIX CONFIRMED** |
| S14-A1 | `importRoutes` arms every route during the parse | **FIX CONFIRMED** - disarmed at construction; the operator's arming door reconstructs the route and still arms it; the round-trip rewrite lost no claim |
| S14-A2 | the duplicate-page-id guard went to the wrong reader | **FIX CONFIRMED** - measured: with absent `.id` = 0, none of the thirteen index files has a duplicate resolved id (two did). Four sites still state the retired rule (`FV3-C1`, `FV3-C2`) |
| S14-B1 | `setF` fans out before bounds-checking | **FIX CONFIRMED** |
| S14-B2 | `RouteCommand.fromJSON` does not clamp a speed | **FIX CONFIRMED** - clamped at the factory, every caller checked, negative still normalises to `-1` |
| S14-B3 | `Edge.toJSON` does not write `entrySide` | **FIX CONFIRMED** - and `entrySide` is a `String`, so the unguarded `put` cannot write an enum the reader's `instanceof String` would reject |
| S14-B4 | the save path reads `linkedLocomotives` with no lock | **FIX INCOMPLETE** - the design is right and pinned, but publishing is no longer `synchronized`, so it no longer excludes `unlinkLocomotive` (`FV3-B2`) |
| S14-C1 | a logical accessory address checked against the raw maximum | **FIX CONFIRMED** - both branches, including three-way, now agree with the diagram editor |
| S14-C2 | the s88 route monitor is not a daemon | **FIX CONFIRMED** for the daemon and the name. The `shutdown()` half the finding also proposed was not done and was not claimed |
| S14-C3 | `loadReturnToHomeTimetable`'s javadoc contradicts its body | **FIX INCOMPLETE** - one of the two stale paragraphs was corrected; *"This replaces the current timetable. Save it first if it matters"* is still there, and it is the other one the finding named |
| S14-C4 | `toggleF(int)`'s javadoc says one second | **FIX CONFIRMED** |
| S14-C5 | `AutoJSONExport` truncates its target | **FIX CONFIRMED (refuted)** - re-checked: five other exports truncate, nothing was changed, and nothing should have been |
| S14-C6 | `getPowerState()` reads an unsynchronised field | **FIX CONFIRMED** |
| S14-C7 | an unknown id creates a feedback module from a frame the parser will not read | **FIX INCOMPLETE** - the gate is on the COMMAND only. `MarklinFeedback.parseMessage` also refuses `getLength() != 8`, so a `0x11` frame of another length still creates a module and parses nothing into it, which is the step the finding wanted closed |
| S14-C8 | the delay floor is not in `behaviour.md` | **FIX CONFIRMED** - see `N8-C2` and `FV3-C5` |
| NSV-B1 | `catalog-findings.py` re-ingests the phantom findings on every run | **FIX CONFIRMED** - parser fixed and the prune simulated read-only; two residual risks named below |
| NSV-B2 | `preSet`/`set` is an unlocked two-call protocol | **FIX INCOMPLETE** - the one-call form is right and the `restoreState` precondition genuinely holds (verified, below), but see `FV3-B2` |
| NSV-B3 | a page whose file will not parse shortens the page list | **FIX WRONG** - see `FV3-A1`. The placeholder defeats `pagesSafeToJudge`, and the next autonomy save deletes that page's whole setup. Measured |
| NSV-C1 | `newRoute`'s null-name arm does not disable the route it refuses | **FIX CONFIRMED** |
| NSV-C2 | the CS3 route import does not clamp a speed | **FIX CONFIRMED** - covered by the factory clamp |
| NSV-C3 | `addRowsAndColumns` asks the ceiling about one row and one column | **FIX CONFIRMED** in the code. Its test cannot fail for this defect - see the test section |
| NSV-C4 | the member speed clamp is one-sided | **FIX CONFIRMED** |
| NSV-C5 | `fillCombinedPage`'s javadoc states as settled fact the thing `N8-A1` shows is false | **FIX WRONG** - the sentence the finding is about is unchanged; the edit landed on `TileGraph.allPages`, which was `NSV-B3`'s trailer. And the sentence is still false (`FV3-A2`) |
| NSV-C6 | the "written in exactly one place" justification is untrue | **FIX CONFIRMED** |
| NSV-C7 | `lastLatency` is a non-volatile `double` | **FIX CONFIRMED** |

**23 confirmed, 6 incomplete, 3 wrong, 0 unverifiable.**

---

## Reasoning, longest where I disagreed

### `NSV-B3` - the placeholder page is a data-loss path, and it is the worst one this project has

`CS2File` now substitutes a blank `LayoutDiagram` - same name, same page id, one text tile at 1,1,
marked `unreadable` - for a page whose file will not read. The reasoning in the commit is about link
tiles and is sound as far as it goes. What it does not price is that **the page's absence was load
bearing**, and two separate protections were keyed to it.

`AutonomySession.pagesSafeToJudge()` is `!store.isPageNumberingSuspect() && store.pagesNotLoaded(loadedNames).isEmpty()`,
and `loadedNames` is built from the pages the session holds. `pagesNotLoaded` compares NAMES. Its own
javadoc names this case in as many words:

> *"CS2File deliberately skips a page whose file will not parse or is not there, and says so; on this
> layout, which lives in OneDrive, an unhydrated placeholder or a file held by the sync client is
> enough ... reconcile deleted its contents anyway."*

The placeholder carries the missing page's name, so `pagesNotLoaded` now returns empty. It also carries
the missing page's id, so `AutonomySession.open` puts that id in `setPageIds`, `pageIsHere` answers
true, and the entries that used to be HELD out of memory and written back verbatim (OB-067) are
released into the live collections instead. Both protections go at once. `save()` then reconciles
against an `existing` set that, for that page, contains exactly one square - the text tile at 1,1 - so
every station, point name, length, facing, direction, signal pairing, caption, barred arrival and
placement on that page is dropped and written.

Measured, with `fvProbe2`. A setup is written over two pages with a point name on `second:3,2`, then
reopened three ways:

```
FV CASE 1  page skipped entirely (behaviour before NSV-B3)
   pagesSafeToJudge()                 = false
   save() declined to prune           = true
   named as absent                    = [second]
   "Second Platform" still on disk    = true

FV CASE 2  blank placeholder in the list (NSV-B3 as shipped)
   pagesSafeToJudge()                 = true
   save() declined to prune           = false
   named as absent                    = []
   "Second Platform" still on disk    = false

FV CASE 3  the real page, nothing missing (control)
   pagesSafeToJudge()                 = true
   save() declined to prune           = false
   "Second Platform" still on disk    = true
```

The control is what makes this decisive: the setting survives when the page is really there and
survives when the page is really missing, and is destroyed only in the state the fix introduced.

This is MT-135's loss exactly - *"Immediately after rename, all stations are gone... Renaming the page
back did not restore the stations"* - reached by a third route, and traded for a mis-aimed arrow that
`NSV` itself graded B on the strength of one autonomy consumer. It is the brief's first failure mode,
and the guard's own javadoc describes the case the fix walked into.

`saveChanges` refusing is correct and is not the problem: no caller is stranded, the two editor doors
catch and show the message and keep the window open, and `combineLinkedPages` reaches its refusal
before it has written anything. The problem is that refusing to write the PAGE says nothing about the
SETUP, and the setup is what is lost.

**What I would change.** Have `pagesNotLoaded` ask the page rather than the name - `AutonomySession`
already knows which of its pages are placeholders, because `LayoutDiagram.isUnreadable()` exists and is
read by exactly one method today. Excluding an unreadable page's name from `loadedNames` restores both
protections and costs the link fix nothing, because the page still stands in the list.

### `N8-A1` - the re-aim is computed in the wrong coordinate system, and does nothing where it was measured

A link tile holds a position in `MarklinControlStation.getLayoutList()`, which **sorts by name**. The
fix reads the old order from the index, writes, and re-aims each arrow by looking its old destination
up by name in `layoutList` - the list the caller hands in. For that to be right, `layoutList` has to be
the new name-sorted list. It is not, in four of the five gestures:

- `LayoutPageEdit.renameOrDuplicate` takes `model.getLayoutList()` (sorted), then for a rename removes
  the old name and **puts the new one back in the old slot** - deliberately, with a comment saying so -
  and for an add or duplicate **appends**. Neither re-sorts.
- `TrainControlUI.combineLinkedPages` takes the sorted list and **appends** the combined page. The
  default name the program itself offers is `"<page> and neighbours"`, which sorts immediately after
  the page it was made from, not last.
- Only the delete path is safe, because removing an element from a sorted list leaves it sorted.

Measured with `fvProbe`, on the same five pages and the same gesture `N8-A1` measured:

```
FV ADD  list handed in  = [1 - Main, 2 - Bottom, 3 - Top Parking, 4 - Combined, 5 - Test, 1 - Main and neighbours]
FV ADD  sorted list     = [1 - Main, 1 - Main and neighbours, 2 - Bottom, 3 - Top Parking, 4 - Combined, 5 - Test]
FV ADD  arrow was       = 2 -> 3 - Top Parking
FV ADD  arrow now       = 2 -> 2 - Bottom        (correct answer: 3)

FV REN  list handed in  = [1 - Main, 9 - Bottom, 3 - Top Parking, 4 - Combined, 5 - Test]
FV REN  sorted list     = [1 - Main, 3 - Top Parking, 4 - Combined, 5 - Test, 9 - Bottom]
FV REN  main arrow now  = 1 -> 3 - Top Parking   (correct: 4)
FV REN  other arrow now = 2 -> 4 - Combined      (correct: 1)

FV DEL  main arrow now  = 1 -> 3 - Top Parking   (correct: 1)
FV DEL  arrow at a gone page now = -1            (Adam's ruling, and it is right)
```

So adding a page under the name Combine itself offers leaves the arrow pointing at `2 - Bottom` when it
should point at `3 - Top Parking` - which is the same wrong answer `N8-A1` reported before the fix.
Renaming `2 - Bottom` to `9 - Bottom` leaves both measured arrows wrong. **The fix is a no-op in the two
cases the finding was written from.**

There is a second, compounding error. `writeLayoutIndex` writes the pages in the order it is given, so
after an add or a rename the index file is no longer in name order - the probe prints
`FV ADD index after = [..., 1 - Main and neighbours]` with the new page last. `pageNamesInIndex` reads
that file order and hands it back as `before` on the NEXT operation, where it is again treated as the
sorted list. Each operation makes the next one's answer worse.

And a third, which is the part that is worse than the defect. Where the index names two pages the same,
`layoutDB` aliases them into one entry, so the index has more names than `getLayoutList()` has. `before`
is then longer than the list the arrows index into, and the re-aim **moves an arrow that was correct**:

```
FV DUP  index names     = [Alpha, Delta, Delta, Zulu]
FV DUP  layoutDB holds  = [Alpha, Delta, Zulu]
FV DUP  arrow was       = 2 -> Zulu
FV DUP  arrow now       = 2 -> Delta            (correct: 3)
```

`CS2File.parseLayoutIndex`'s own comment says duplicate names are a thing that happens - *"two pages may
carry the same name"* - and `NSV-B3` names the same aliasing. No file in this repository has one today
(measured: thirteen indexes, no duplicate names, all thirteen already in name order), so the first two
errors are live and the third is a trap.

**What I would change.** `repointLinksAcross` should be given a sorted copy of the new page list and a
sorted copy of the old one - the two lists a link's number actually indexes into - rather than the file
order and the caller's working list. `Collections.sort` on two local copies inside
`writeIndexAndKeepLinksAimed` is the whole fix, and it keeps the rule in the one place the commit
correctly put it. The duplicate-name case needs `before` to be derived from the loaded page list rather
than from the index.

**A repair for this is in the working tree too, and it is NOT measured here.** Like the one under
`FV3-A1`, it appeared uncommitted while this document was being written, cites `FV3-A2`, and takes the
shape recommended above - `Collections.sort(before)` and `Collections.sort(after)`, plus a branch for the
renamed page's own file. It also deletes `pageNamesInIndex` outright, which would close `FV3-D5` with it.
I could not re-measure it: the probe that produced every number above calls `pageNamesInIndex`, which no
longer exists, and re-pointing a probe at an API that is being changed under it would be measuring a
half-finished edit rather than a fix. `fvProbe.java` is in this session's scratch directory and wants
about ten minutes of adaptation once the edit settles; **the ADD and RENAME cases are what have to come
back right, and the duplicate-name case is what has to stop moving a correct arrow.** Until that is run,
this finding is open on the evidence above.

A consequence worth carrying: `saveChanges(null, false)` writes to the path the object was constructed
with, and a rename does not update that path. So when the renamed page's own arrows change, the re-aim
is written to the file the rename has just deleted, recreating it as an orphan, while the new file keeps
the stale numbers. That falls out with the ordering fix only if the renamed page is saved under its new
name.

### `S14-B4` and `NSV-B2` - the design is right, and it dropped an exclusion

Everything the disposition claims about the field is true and I could not break it: every published map
is unmodifiable, a rebuild assigns, `unlinkLocomotive` copies, `getLinkedLocomotives()` wraps, and
nothing in `src/` or `test/` mutates what it hands back. The three tests are real tests (below).

**The `restoreState` precondition holds, and it is not obvious.** The two-call `preSet`/`set` form
survives for `restoreState`, on the stated ground that nothing else is staging at start-up. I checked
it rather than took it: `preSetLinkedLocomotives` has exactly one production caller,
`newLocomotive(MarklinSimpleComponent)`, which is private and is reached only from `restoreState:441`.
The awkward part is that `restoreState` calls `syncWithCS2()` **between** the staging loop and the
applying loop, and `syncWithCS2` does rebuild consists - but it now uses the ONE-call form, which never
touches `preLinkedLocomotives`. So the staged lists survive the sync. The precondition is true, and it
is true only because of the change `NSV-B2` made; had `syncWithCS2` been left on the pair, `restoreState`
would have been the interleaving.

**What the fix removed.** Before it, the publish step ran inside `synchronized (this)` and
`unlinkLocomotive` is `synchronized`, so the two were mutually excluded. Now `setLinkedLocomotives(Map)`,
`setLinkedLocomotives()` and `applyLinkedLocomotives` are all unsynchronised, and `unlinkLocomotive` is a
read-copy-write on a volatile reference. A rebuild that overlaps an unlink silently discards whichever
landed first. `unlinkLocomotive`'s one production caller is `MarklinControlStation:3199`, the loop that
takes a DELETED locomotive out of every consist that references it; the rebuild is `syncWithCS2`, off the
event thread. Losing the unlink puts a deleted locomotive back into a consist - which is the defect that
loop exists to prevent - and the SOP's own entry for this is *"Before mutating shared state, find out who
else reads it and under which lock"*.

Filed as `FV3-B2` rather than as a reopening, because it is a new hazard rather than the old one: I did
not produce the interleaving, and neither did `S14` or `NSV`.

### `N8-B3` / `NSV-B1` - the prune cannot drop a legitimate row today, and here is the measurement

A wrong prune deletes rows and takes our own `status` and `status_note` with them, so this was worth
executing rather than reading. I copied `triage.db` to the scratch directory, imported
`catalog-findings.py` with `importlib` without running `main`, parsed each live review folder the way
`add_one_folder` does, and compared the parsed refs against the rows the database holds for those
documents - the exact set `prune_findings` would delete:

```
=== docs/reviews            : 0 rows across 0 documents   -> nothing would be deleted
=== docs/reviews-2026-09-09 : 70 rows across 7 documents  -> nothing would be deleted
=== docs/reviews-2026-09-10 : 190 rows across 7 documents -> nothing would be deleted
```

Nothing was written. The fifteen phantoms are gone and `X8V-C1` and `X8V-C3` both read `closed`. Two
residual risks for whoever touches this next, neither of them live:

- `prune_findings` is authoritative per document, and the parser now skips short-form rows above the
  document's first `## [A-D]` banner. A document whose only table for a ref sits above that banner would
  have the ref deleted rather than merely not added. The banner regex fails SAFE when it matches nothing
  (`findings_start` stays 0 and the whole document is read), which is the direction that matters.
- `out[ref] = cells` is now last-wins. That is right for the README's layout, in which the status table
  heads its severity section, and wrong for any document that tabulates a ref again later.

### `S14-A1` - confirmed, and the round-trip rewrite strengthened rather than weakened its claim

The brief asked specifically whether the two rewritten round-trip tests dropped a claim. They did not.
`MarklinRoute.equals` compares `id`, `s88`, `enabled`, `triggerType`, `conditions` and `route`.
`assertSameApartFromArming` asserts all six - with `enabled` asserted explicitly as `false` rather than
compared - **and** the name, which `equals` never compared. Nothing was lost and one claim was added.

`testParseCS3Routes` is the class that could have been weakened silently: `testCS2` does
`routesTCNot3.removeAll(routesCS3)`, which is `equals`, and one side of that comparison now always has
`enabled == false`. It is green, and the reason is that `test/TC_routes.json` has fifty routes and
**none** with `auto: true` (measured). So the class is one fixture edit away from a red with nothing to
do with CS3 parsing. Worth a line in that test rather than a finding.

The other consumers of a parsed route's `enabled` flag are all correct under the ruling: `exportRoutes`
writes what the database holds, and the database now holds `false`, which is the documented consequence;
and the operator's arming door (`RouteEditorFrame` -> `editRoute`, and Bulk Enable through
`bulkEnableOrDisable`) is a delete-then-re-add that CONSTRUCTS a new `MarklinRoute`, so it arms. There is
no path by which an imported route can be turned on and fail to watch its sensor.

### `S14-A2` - right at the root, and the sweep stopped at two of six

The ruling is correct on the data. With an absent `.id` read as 0, none of the thirteen `gleisbild.cs2`
files in the tree has a duplicate resolved page id, where two did before (`Oles kreds` and
`sample_layout`); and no file anywhere states `.id=0`, which is what makes 0 a safe value to mint. Both
readers ask `pageIdOrPosition`, so they cannot disagree.

The disposition says *"The two comments that asserted the old rule now state this one."* There were six,
and four still assert it - `FV3-C1` and `FV3-C2`. One of them is inside the method the same commit
rewrote.

---

## A - high

| id | what | disposition |
|---|---|---|
| FV3-A1 | `NSV-B3`'s placeholder page defeats `pagesSafeToJudge` and the held-entry mechanism, so the next autonomy save deletes the unreadable page's entire setup - the MT-135 loss, introduced by the fix | **a repair landed in the working tree while this pass was running; re-measured, the data is now safe - see below and `FV3-A3`** |
| FV3-A2 | `N8-A1`'s re-aim is computed against a list that is not name-sorted, so it does nothing for Add, Rename, Duplicate or Combine, and corrupts a correct arrow where the index names two pages the same | open |
| FV3-A3 | that repair went to two of the three identical loops, so the save now refuses to prune and reports itself as an ordinary clean save - the refusal is invisible and names no page | open |

### FV3-A1 - a page that could not be read now loses its autonomy setup instead of keeping it

Stated in full under "Reasoning" above, with the three-case measurement and its control. The short form:
before `NSV-B3`, an unreadable page was absent from the session, `pagesNotLoaded` named it,
`pagesSafeToJudge()` was false, `save()` declined to prune, and the page's settings were written back
verbatim and survived. After it, the placeholder carries the page's NAME and ID, so both the
name-keyed guard and the id-keyed hold answer "present", and the reconcile compares the setup against a
page holding one text tile.

The cost is every station, point name, length, facing, direction, signal pairing, caption, barred arrival
and placement on that page, deleted and written, silently - and `AutonomySession.save`'s own comment says
three of the four doors that reach it discard the report. The trigger is an unhydrated OneDrive file,
which `readLayoutIndexIds`'s comment calls *"an ordinary Tuesday on this railway"*.

Graded A on the SOP's own wording - *"data silently lost"* - and because the thing traded away was an
arrow that `NSV` graded B while conceding no train is routed through one.

**A repair landed in the working tree while this document was being written**, uncommitted, citing
`FV3-A1` in its comments - `AutonomySession.open` no longer offers a placeholder's id to `setPageIds`,
and `pagesSafeToJudge`'s loop no longer counts a placeholder as loaded. It is the change this finding
asked for and it is in the right two places. Re-measured with the same probe against the tree as it now
stands:

```
FV CASE 1  page skipped entirely          pagesSafeToJudge = false   "Second Platform" on disk = true
FV CASE 2  blank placeholder (repaired)   pagesSafeToJudge = false   "Second Platform" on disk = TRUE
FV CASE 3  the real page (control)        pagesSafeToJudge = true    "Second Platform" on disk = true
```

**The data is safe.** Case 2's held entry is out of memory again (`getPointName` answers null before the
save, as in case 1) and the file keeps its contents. What the repair did not finish is `FV3-A3`.

This entry stays at A and stays open until the third loop is fixed and something pins it; the verdict
on `NSV-B3` in the table above is about `0f46aff4`, which is the commit this pass was asked to validate.

### FV3-A2 - the arrows are re-aimed against the wrong list

Stated in full under "Reasoning" above, with four measured gestures. Graded A rather than at `N8-A1`'s
re-graded B for one reason: `N8-A1` was a defect that had always been there, and this is a repair that
**reports itself as done**. `behaviour.md` §8 now states the re-aim as intended behaviour, three
call sites carry comments saying the rule lives in one place so it cannot be got wrong, and the arrows
still move. A silent defect with a paragraph of documentation saying it cannot happen is worse than the
same silent defect on its own.

### FV3-A3 - the repair for `FV3-A1` reaches two of three identical loops, so the refusal is silent

Three methods in `AutonomySession` build a set of page names by walking `pages`. The repair added
`if (page.isUnreadable()) continue;` to two of them - `open` (`:127`) and `pagesSafeToJudge` (`:6862`) -
and not to the third, which is `save`'s own copy twelve lines above the call it makes to
`pagesSafeToJudge` (`:6948-6952`).

So inside `save()`:

```java
for (LayoutDiagram page : pages) { loadedNames.add(page.getName()); }   // :6950 - no skip

java.util.List<String> absent = store.pagesNotLoaded(loadedNames);      // EMPTY: the placeholder
                                                                       // still supplies the name
boolean incomplete = !pagesSafeToJudge();                               // true: that one was fixed

report = incomplete ? Reconciliation.declined(absent) : store.reconcile(existing);
```

`Reconciliation.declined(absent)` with an empty list produces a report whose `wasDeclined()` is
`!declinedBecauseAbsent.isEmpty()` - **false**. Measured, on the repaired tree:

```
FV CASE 1  page skipped entirely   declined = true    named as absent = [second]
FV CASE 2  blank placeholder       declined = FALSE   named as absent = []
```

Pruning is correctly refused, which is the half that matters and is why this is `A3` rather than a
reopening of `A1`. What is lost is the telling. `DR-B10` exists for exactly this - the finding was that
*"an EMPTY reconciliation and a REFUSED one were the same object"* - and its test says what it costs:
*"a save that left the whole setup alone reported itself as an ordinary clean save, so every door showing
this to somebody would say nothing"*. The operator gets no dialog and no page name, so the one moment at
which putting the missing file back would fix everything passes in silence, which is the sentence
`AutonomySession.save`'s own comment already uses about this case.

`testASaveThatDeclinesToTidySaysWhichPagesStoppedIt` does not catch it: it builds its incomplete session
by leaving the page out altogether, which is the shape that no longer occurs now that `CS2File` always
supplies a placeholder. That test should gain a placeholder arm rather than being replaced.

One line, in the same shape as the other two. And the three loops should be one method - they are three
copies of "the page names this session is holding", which is how two of them came to be fixed and one
not.

## B - medium

| id | what | disposition |
|---|---|---|
| FV3-B1 | `core.testLocomotive` hangs for ever and `core.testRoutes` fails, both because `setFeedbackState` silently does nothing for a feedback module the restored live database does not hold | open |
| FV3-B2 | the consist publish step is no longer `synchronized`, so it no longer excludes `unlinkLocomotive`; the javadoc and the guard test still say the monitor is what makes the removal safe | open |
| FV3-B3 | `behaviour.md` §8 documents an arrow re-aim the code does not perform, and presents the placeholder page as a safety measure without its cost | open |

### FV3-B1 - two release-battery classes are hostage to what Adam's application last saved

`MarklinControlStation.setFeedbackState(name, state)` looks the module up by name and **returns false
without doing anything** when it is not in the database. Two tests ignore that return value, and neither
creates the module it uses:

- `core.testRoutes.testNodeExpressionEvaluation:310` does `model.setFeedbackState("4", true)` and then,
  eighteen assertions later at `:422`, evaluates an expression containing `Feedback 4,1` and fails with
  `expected [true] but found [false]` - a message that says nothing about feedback 4. Proved by mutation:
  adding `assertTrue(model.setFeedbackState("4", true))` as a precondition turns the failure into
  `feedback 4 is in the database expected [true] but found [false]` **at line 310**. The four tests this
  round added are not the cause - with all four disabled the class is 26 tests and still one failure.
- `core.testLocomotive.testLocomotiveConstructor:187` is worse. A background thread does
  `model.setFeedbackState("1001", true)`, which is the only thing that can release
  `l.waitForOccupiedFeedback("1001")` on the main thread - an **untimed `Object.wait()`** with no
  advisory and no timeout. With module 1001 absent, the setter is a no-op and the test parks for ever.
  Confirmed with `jstack` on the live JVM:

```
"main" #1 ... in Object.wait() ... WAITING (on object monitor)
	at org.traincontrol.base.Locomotive.waitForOccupiedFeedback(Locomotive.java:809)
	at org.traincontrol.base.Locomotive.waitForOccupiedFeedback(Locomotive.java:948)
	at core.testLocomotive.testLocomotiveConstructor(testLocomotive.java:187)
```

The precondition immediately above the wait, `assertFalse(model.getFeedbackState("1001"))`, reads as a
check that the module is there and is exactly the opposite: `getFeedbackState` also returns false for a
module that does not exist, so the assertion passes **because** the test is about to hang.

Neither `1001` nor `4` is created anywhere in `test/`. Both come from `LocDB.data`, the live 399,607-byte
file Adam's application last wrote at 17:35 on 2026-09-10, which is why these classes were green for
earlier passes today and are not now.

Two consequences for the release. The bar is `Failures: 0` AND `Skips: 0`, and the battery cannot reach
it - it cannot even finish, because a hung class holds the runner's user-wide lock until somebody kills
the JVM by hand. That is the second time today this has stranded the runner, and the brief attributed
the first to a modal dialog; this one is an untimed railway wait inside a unit test, which no amount of
avoiding dialogs will catch.

The fix is the SOP's own rule - *"Assert the precondition that makes a test meaningful"*: make both tests
create the feedback module they use, or assert `setFeedbackState`'s return value. The wait itself wants a
bounded form for test use; `waitForOccupiedFeedback(name, minDuration, adviseAfterMs)` exists and its
javadoc explains why the plain form stays endless for production callers.

### FV3-B2 - the fix that removed one race removed an exclusion as well

`applyLinkedLocomotives` and both `setLinkedLocomotives` overloads are unsynchronised; the publish used
to sit inside `synchronized (this)`. `unlinkLocomotive` is `synchronized` and does read-copy-write on the
volatile reference. The two are therefore no longer mutually excluded, and a rebuild that overlaps an
unlink discards it - putting a deleted locomotive back into a consist, which is exactly what
`MarklinControlStation:3199`'s loop exists to prevent. The threads are the ones `NSV` already established:
`syncWithCS2` off the event thread, `deleteLoc` on it.

Two records still state the retired reason, which is how the next reader will get this wrong:

- `unlinkLocomotive`'s javadoc: *"synchronized, on the same lock setSpeed and setDirection hold: those
  iterate linkedLocomotives, which is a plain LinkedHashMap, so removing from it on another thread ...
  could otherwise throw ConcurrentModificationException"*. It is not a plain `LinkedHashMap` any more and
  nothing removes from it. The body comment added by the fix says so, thirty lines below the javadoc that
  contradicts it.
- `core.testMultiUnitMembership.testUnlinkLocomotiveIsSynchronized` pins the keyword with that same
  reason in its message. The keyword should stay - it is now what makes the read-copy-write atomic - but
  for a different reason, and the test says the old one.

Not executed: I did not produce the interleaving, which is the same honest limit `S14-B4` and `NSV`
both recorded.

### FV3-B3 - `behaviour.md` says the arrows are re-aimed, and they are not

§8's new paragraph states, as intended behaviour: *"Every operation that changes the set of page names
re-aims the arrows. Adding, renaming, duplicating, deleting and combining all move the alphabet, and an
arrow follows the page it pointed at."* Measured, only deleting does (`FV3-A2`). The README's rule is
that where the document and the code disagree one of them is a bug and Adam decides which; here the
document is right about the intent and the code does not implement it, so the document is the thing to
keep and the code is the bug.

The same section's placeholder paragraph - *"A page whose file will not read keeps its place in the
list ... Such a page is never saved"* - is accurate about the page and silent about the setup, which is
what is actually at risk (`FV3-A1`). Two claims in §8 and §7b are also wrong in detail, at C: see
`FV3-C5`.

The rest of §7b and §8 describes the code as it now is. I checked each sentence: the import arriving
disarmed, construction arming a route, the 150 ms floor existing, zero being left alone by the editor,
the speed clamp and the negative rule, the absent id being zero, the id not being a position, the -1
link clicking to nothing, and a link not being offered its own page - all true against the source.

## C - low

| id | what | disposition |
|---|---|---|
| FV3-C1 | `readLayoutIndexPageExtras`'s javadoc says "Keyed by id, not by name" and "An ABSENT id is the page's position" - in the method the same commit rewrote to do neither | open |
| FV3-C2 | three further sites still state the retired absent-id rule | open |
| FV3-C3 | `clear()` still forgets the whole page BEFORE emptying it, so `N8-B2`'s unconditional rebuild rebuilds from the page it is about to throw away | open |
| FV3-C4 | `fillCombinedPage` refuses after the combined page exists on disk and in the index, stranding a stray copy | open |
| FV3-C5 | two claims in `behaviour.md` §7b are wrong in detail, and one of them is the sentence §7b was written to establish | open |
| FV3-C6 | the cut half of cut-and-paste now calls the lazy `getAutonomySession()` builder, which it deliberately did not before | open |
| FV3-C7 | the `FV` and `FV2` prefixes are both taken | open |
| FV3-C8 | a link that points nowhere silently becomes a link to the first page the moment its address popup is accepted | open |

### FV3-C1 - the rewritten method's javadoc describes the code it replaced

`LayoutDiagram.readLayoutIndexPageExtras` (`:1246-1267`) still carries **"Keyed by id, not by name.** A
rename is the one operation where the writer is holding a name the index has never seen, and the id is
precisely what is carried across it", then "An ABSENT id is the page's position, through
`pageIdOrPosition`", then `@return each page id against the lines of its block`. The method is keyed by
name, does not read `.id` at all - it `continue`s past it - and returns `Map<String, List<String>>`. The
body comment thirty lines below says the opposite of the javadoc above it, in full and correctly. This is
the README's "one status, one location" applied to a comment: a reader who stops at the javadoc, which is
what a javadoc is for, gets the retired design.

### FV3-C2 - three more statements of the rule `S14-A2` retired

- `LayoutDiagram.java:1808`, inside `writeLayoutIndex`: *"An absent id is read as the page's POSITION -
  see `pageIdOrPosition`, which CS2File's page loop calls - so omitting it for the first page only worked
  while ids and positions were the same thing ... an omitted id would be read as 1."* This is in the
  method whose allocator the ruling's safety depends on, four lines below a new comment that states the
  new rule correctly.
- `test/core/testParseCS2Layout.java:961` and its assertion message at `:995`: *"An absent id is the
  page's POSITION, which is what `pageIdOrPosition` says"*. Its MUTATION note - *"make
  `readLayoutIndexPageExtras` skip a page whose `.id` is absent"* - describes code that no longer exists.
- `test/regression/testPageIdsAreDurable.java:801`: *"CS2File reads an absent id as the page's POSITION."*

The finding's own disposition claims two comments were swept. Six state the rule; two were.

### FV3-C3 - the rebuild `N8-B2` restored still misses `clear()`

`LayoutEditor.clear()` calls `forgetWholePage()` and then `layout.clear()`. With `touched()` now
unconditional, the rebuild runs while the page is still full, and nothing rebuilds after it is emptied -
so Clear Page leaves the tile graph holding every tile it has just deleted. `NSV`'s adjudication 2 named
this in as many words - *"`clear()` should call `forgetWholePage()` after `layout.clear()`, or the rebuild
it then gets is from the diagram it is about to throw away"* - and it was not done. Same weight as
`N8-B2` after its re-grade: a stale badge from `showStaticAutonomyLayer`, not a railway decision.

Nothing pins either half of `N8-B2`. `N8-D5` is still true: `testDeleteForgetsTheWholeSquare` asserts the
return value twice and never asks what was rebuilt, and it was not changed by this round.

### FV3-C4 - the ceiling refuses after the page has been made

`combineLinkedPages` creates the combined page on disk (`saveChanges(combined, true)`), writes the index,
excludes the page from autonomy, saves the setup, calls `refreshLayouts()`, and only then calls
`fillCombinedPage`, where the new `height > MAX_SIZE || width > MAX_SIZE` check throws. So refusing
leaves a page in the index and on disk that is an unfilled copy of the page the operator combined from,
excluded from autonomy, for them to find and delete. `N8-C1`'s own closing sentence asked for the
opposite - *"refuse before the page is created rather than after"* - and its preferred fix, putting the
ceiling in `LayoutDiagram.addRowsAndColumns` where both paths run through it, is still not done. No test.

### FV3-C5 - what §7b gets wrong

- *"the executor waits the LARGER of that delay and 150 ms"*. It waits `SLEEP_INTERVAL + max(delay, 150)`.
  `NSV-D5` specifically said to carry this - *"`S14-C8` carries the more useful detail ... the editor's
  number is the floor on the delay and not on the wait"* - and §7b states the number that is not the wait.
  This is `S14-C4`'s own complaint, one document over.
- *"Zero is not a delay ... and it is not raised."* `delayTheRailwayWillUse(0)` returns 0, so the editor
  shows 0 - but `execRoute` waits `SLEEP_INTERVAL + max(0, 150)`, the same pause it gives a delay of 150.
  So zero is the one number in that column which is NOT what the railway will wait, in a paragraph whose
  point is that the editor shows what the railway will wait *"in both directions"*. The method is even
  named `delayTheRailwayWillUse`.
- *"clamped where the command is built, so every door agrees - typed into the editor, read from a JSON
  file, or imported from a Central Station"*. The editor does not clamp; `RouteEditorFrame:2419`
  range-checks and refuses to save, which `NSV-C2` established and which is a different behaviour.

And in §8: *"four have a first page with no `.id`, their stated ids run 1..n"*. `tc_backup`'s run 2..5.
The conclusion is unaffected.

### FV3-C6 - the cut path now builds the autonomy session

`deleteSelection` reads `parent.getAutonomySession()` before testing `tellAutonomy`, so the cut half of
cut-and-paste - which passes `false` deliberately, because *"the paste carries the setup"* - now reaches
the lazy builder where `delete(label, false)` never did. That getter parses every page, runs the caption
migration (which writes page files) and can put a dialog on screen; three separate comments in this
codebase, including `pageIdFloor`'s, warn against reaching it from a gesture that has nothing to do with
autonomy. In practice the session is normally already cached while the editor is open, which is why this
is C and not B. Moving the read inside the `if` is the whole fix.

### FV3-C7 - the prefix

Stated at the top. `FV`: thirteen catalogued rows. `FV2`: twenty-six, cited from `HomeStaging.java`.

### FV3-C8 - a broken link silently becomes a link to the first page

A link whose page was deleted carries `rawAddress = -1`, so `getLogicalAddress()` returns 0.
`LayoutEditorAddressPopup.setAddress("0")` computes `at = -1`, fails its own range check and sets no
selection - which leaves the combo on its first item, because a populated `JComboBox` always has one.
`getAddress()` then returns that page's position. So opening the address popup on a nowhere-pointing
arrow for any reason and pressing OK aims it at the first page in the alphabet, silently. The popup also
offers no way to say "no page", so `-1` cannot be set deliberately. Adam's ruling is that the operator
sets it on their next edit; this makes the next edit choose for them.

## D - not defects, checks that came back clean, and one withdrawal

| id | what | disposition |
|---|---|---|
| FV3-D1 | the Java-serialization path for `linkedLocomotives` | clean - **withdrawn**, originally opened as a suspected A |
| FV3-D2 | nothing mutates what `getLinkedLocomotives()` hands back | clean, measured |
| FV3-D3 | `-1` reaches nothing that does arithmetic on it | clean |
| FV3-D4 | the three new message keys | clean - present in all eight bundles, ASCII-only, placeholders match |
| FV3-D5 | `pageNamesInIndex` reads FILE order where SORTED order is meant | a trap, not the cause of `FV3-A2` - measured |
| FV3-D6 | `Edge.toJSON`'s unguarded `put` cannot write a non-String | clean |
| FV3-D7 | the tree | verified |

### FV3-D1 - withdrawn: no `locdb.data` is affected by the field change

Opened as a suspected A. `MarklinLocomotive implements java.io.Serializable` and declares no
`serialVersionUID`, so its id is computed from its fields and non-private methods - and `4c2d7415`
changed `linkedLocomotives` from `private final` to `private volatile` and added a public
`setLinkedLocomotives(Map)`. Both change the computed id, which would make every previously written
instance unreadable.

Nothing is affected, for two independent reasons. `MarklinControlStation.saveState` writes a
`List<MarklinSimpleComponent>` and nothing else - `MarklinSimpleComponent` declares its own
`serialVersionUID` and holds the consist as `Map<String, Double>` - so no `MarklinLocomotive` is ever in
`LocDB.data`. And it could not be: `Locomotive`, the superclass, is not `Serializable`, so a round trip
would lose every field it declares. The `Serializable` marker on `MarklinLocomotive` is vestigial. The
comment the fix added to `getLinkedLocomotives`, about wrapping the map of *"a MarklinLocomotive restored
by Java serialization"*, is defending against a path that does not exist - which costs nothing, and is
the right instinct.

### FV3-D2 - the unmodifiable view costs no caller anything

`grep` over every reader of `getLinkedLocomotives()`, `commandedLocomotives()`, `isLinkedTo`,
`hasLinkedLocomotives` and `getLinkedLocomotiveNames` across `src/` and `test/`: nine production readers
and none edits the map. `TrainControlUI.applyPreferredFunctions` at `:9807` and `:9861` iterates
`keySet()` on its own thread and does not modify. The only caller that tries is
`testTheConsistHandedOutCannotBeEdited`, which expects the refusal.

### FV3-D3 - what a `-1` link reaches

`goToLayoutPage` returns before the arithmetic; `pagesLinkedFrom:24997` skips `index < 0`;
`TileGraph.leadsOutsideAutonomy` guards `address < 0` and answers "outside autonomy", which is the right
answer for a destination that cannot be paired; the tooltip has its own branch and the new
`layout.linkPageNone` string; `LayoutDiagramComponent:886` writes `.artikel=-1`, which is what a text tile
already carries, and `CS2File:2584` reads it straight back. No reader was missed.

### FV3-D4 - the bundles

`layout.linkPageNone`, `layout.pageCouldNotBeReadTile` and `layout.errorPageWasNotReadSoNotSaved` are in
all eight `messages*.properties`. A byte scan of all eight files reports **zero** bytes above 127.
Placeholders match the English exactly: none in `linkPageNone` (called through `I18n.t`), `{0}` in the
other two (called through `I18n.f` with one argument).

### FV3-D5 - withdrawn as the cause of `FV3-A2`, kept as a trap

`pageNamesInIndex` returns the page names in FILE order and its javadoc argues that this is the
name-sorted order, because `writeLayoutIndex` is given `getLayoutList()`. That argument is circular once
the caller stops handing in a sorted list, and it was never true of a station-written file whose page
order is not alphabetical. Measured: all thirteen `gleisbild.cs2` files in the tree are already in name
order, and none has a duplicate page name, so no file here violates it. The live defect is in the list
the CALLER hands in, which is why `FV3-A2` is filed against that and this is a D.

### FV3-D6 - `entrySide` is a String

`Edge.entrySide` is declared `private String`, so `jsonObj.put("entrySide", this.entrySide)` writes a
string and `Layout.fromJSON`'s `edge.get("entrySide") instanceof String` accepts it. `AutonomyBuilder`
writes `.name()` because its own edge type holds an enum. There is no path on which `Layout.fromJSON`
receives a `JSONObject` that has not been through text: `Layout.fromJSON(String, ViewListener)` is the
only entry point. The asymmetry between the two writers is cosmetic.

### FV3-D7 - the tree, and the change somebody else made in it

Nothing of mine is left in it. Every mutation was taken against a whole-file copy in the scratch
directory and restored with `cp`, md5 verified against the copy each time - four mutations, four
restores, four matching hashes. Both probes live in the scratch directory, were compiled to a scratch
build directory and were never added to `test/`, so no guard class could be reddened by their existence
and none was: `regression.testEveryTestIsInTheBattery` and
`regression.testSwitchingToACentralStationLayout` are both green.

**Six files carry changes that are not mine**, all uncommitted, all citing `FV3` findings by name - so
this document was read and acted on while it was being written: `AutonomySession.java` (`FV3-A1`),
`LayoutDiagram.java` and `TrainControlUI.java` (`FV3-A2`), `MarklinLocomotive.java` (`FV3-B2` by its
shape), and `test/core/testLocomotive.java` and `test/core/testRoutes.java` (`FV3-B1`, creating the
feedback module and asserting the setter's return value, which is what was asked for). I did not revert any of them
and did not restore over them: my own restores were all whole-file copies of files that session had not
touched - `LayoutEditor.java` was the only one at risk and its restored hash matches HEAD - and the rule
this project has learned twice about concurrent sessions is that a whole-file restore deletes somebody
else's in-flight work (`N8-D6`).

`grep -rn "FV3 MUTATION\|FV MUTATION\|REVIEW MEASUREMENT ONLY" src/ test/` returns nothing, which is the
check that says none of my four mutations leaked into their edit.

Only the `FV3-A1` repair was measured, under that finding and `FV3-A3`. The other three arrived too late
and are moving: the `FV3-A2` work deletes `pageNamesInIndex`, which the probe calls, so the probe no
longer compiles against the tree. **Nothing in this document describes those three changes as working.**

So at the end of this pass `git status --porcelain` lists the three files under `cs2_sample_layout/` that
Adam's application rewrites, this document, and six modified files that belong to whoever is working
through these findings.

**None of it has been re-measured except `FV3-A1`.** Whoever finishes this round should re-run, in this
order: `core.testLocomotive` (which should now finish at all), then the twenty classes listed at the top
of this document, then the `fvProbe` adaptation described under `FV3-A2`. Only the last of those can say
whether the arrows are right, because nothing in `test/` asks.

---

## Would each new test catch its own defect?

Asked of every test `4c2d7415` and `0f46aff4` added or changed. "By construction" means the mutation's
outcome follows from the test's own structure and was not separately run.

| test | catches its own defect? |
|---|---|
| `testARebuildDoesNotChangeTheMapAReaderIsHolding` | **Yes**, by construction. `getLinkedLocomotives()` returns an unmodifiable VIEW of the instance current at call time; a rebuild assigns a new instance, so the held view keeps reporting 2. Under `clear()`+`putAll()` the one shared instance changes underneath it and the first assertion fails. It rebuilds with DIFFERENT contents on purpose, which is what stops it passing vacuously, and it has a control |
| `testTheConsistHandedOutCannotBeEdited` | **Yes**, by construction - returning the field unwrapped makes `clear()` succeed and the test calls `fail()` |
| `testApplyingAListDoesNotDisturbWhatIsStaged` | **Yes**, by construction - delegating the one-call form to the pair overwrites the staged member and the last assertion names it |
| `testAFunctionTheHeadDoesNotHaveIsNotSentToTheMembers` | **Yes**, and it has the control that stops it passing with the fan-out deleted entirely |
| `testUnlinkLocomotiveIsSynchronized` | Catches removal of the keyword, but its stated reason is now false - see `FV3-B2` |
| `testAHalfReadRouteFileLeavesNothingWatchingASensor` | **Yes** - it pulses the sensor and asks whether the turnout moved. Its MUTATION note is wrong: it says *"take the `catch` out of `parseRoutesFromJson`"*, and there is no `catch` - the fix is `route.disable()` in the loop. A reader re-verifying it would mutate something that does not exist and conclude the test is unpinned |
| `testAParkedRouteStillFiresOnceItIsReleased` | **Yes** for `enable()`. Its comment says the release is *"as importRoutes does after newRoute succeeds"*, and `importRoutes` does no such thing - that is the whole point of the ruling |
| `testASpeedOutsideTheRangeIsClampedWhereTheCommandIsBuilt` | **Yes**, by construction, and it asserts the in-range cases too, so it is a clamp rather than a default |
| `testARouteReadFromAFileArrivesDisabled` | **Yes**, by construction, and it has two controls |
| `assertSameApartFromArming` (the two rewritten round trips) | **Yes** - it asserts every field `equals` compared plus the name, and asserts the arming explicitly rather than dropping it |
| `testEachPageKeepsItsOwnUnmodelledKeys` | **Yes** for the key-by-id mutation, through its Alpha/Beta case. The second half of its MUTATION note - *"add the withdrawal back and the second case loses Gamma's entirely"* - is stale: under the absent-id-is-zero rule Gamma and Delta no longer collide, so that mutation would not fire |
| `testAnEdgeKeepsItsArrivalSideThroughTheJSON` | **Yes**, and it is the best-built test in the round: a floor assertion on the fixture, the whole configuration rather than one edge, and a stated reason for choosing the baseline over the sample |
| `testNoCataloguedFindingHasASubLetterItsDocumentDoesNotWrite` | **Yes** for its stated mutation - and **it verifies nothing as shipped**. Measured on the mirror it reads: deleting X8V's eleven sub-lettered rows leaves exactly two, `DD-D7a` and `WP-C19d`. `DD-D7a`'s document is `2026-08-22-duplication-and-design.md`, which no longer exists under `docs/`, and `WP-C19d`'s document column is `-`. The test skips a row whose document it cannot find, so it examines two rows, checks zero of them, and passes. It counts `seen` and never asserts it, which is the README's own *"property tests need a floor on how much they exercised, or the suite can quietly degenerate into testing nothing while still passing"*. It will catch the next occurrence, because that document will still be on disk; it says nothing today |
| `testTheCeilingIsAskedAboutTheAmount` (`NSV-C3`) | **No - measured.** I put `roomToGrow(1, 1)` back in `LayoutEditor.addRowsAndColumns`, which is the defect `NSV-C3` reports, and ran the class: `Total tests run: 4, Failures: 0, Skips: 0`. The test asserts `roomToGrow`'s behaviour and `roomToGrow` is not what the fix changed. Its own javadoc concedes it - *"that the caller passes its own arguments is one line above it and is not covered"* - with the honest reason that the refusal branch is a modal dialog. It is a control, not a guard, and this is the codebase's own "extracted rule moves the bug to the call" pattern |

**And three fixes shipped with no test at all.** `grep -rn "repointPageLinks\|writeIndexAndKeepLinksAimed\|markUnreadable\|isUnreadable\|linksNowhere\|setLinkedPageIndex" test/` returns **nothing**. So
`N8-A1`'s re-aim, `NSV-B3`'s placeholder, the `-1` link and the save refusal are covered by no assertion
anywhere - which is exactly the stretch the brief flagged as compile-verified only, and it is where both
of this pass's A findings are.

---

## What I could not execute

- **`core.testLocomotive`.** It hangs (`FV3-B1`) and cannot be made to finish without either fixing the
  test or killing the JVM, and I am not permitted to do the second. It is the only class I set out to run
  that has no result. Everything else that was queued behind it ran once the hung JVM had been reaped:
  `core.testAdvancedRoutes`, `regression.testRenameRoundTripThroughTheUIPath`,
  `regression.testARunSurvivesAPageRename` and `regression.testDataSafetyRoundTrips`, all green.
- **The mutation for `testARebuildDoesNotChangeTheMapAReaderIsHolding`.** Reverting it
  faithfully needs four edits (the field initialiser, the clear branch, the publish, and
  `unlinkLocomotive`), and the property it asserts follows from the view semantics, so I recorded it as
  "by construction" rather than leaving a half-reverted file behind.
- **The battery**, on the brief's instruction - and it cannot complete while `FV3-B1` stands.

**The hang stranded a JVM for about twenty minutes and then cleared itself.** The wait
`core.testLocomotive` sits in is untimed, so the class never returned; the runner's own timeout killed
the invocation and `one.sh`'s exit trap reaped the JVM it had started, which released the lock and let
the rest of this pass run. That is the harness working as designed and it is worth saying, because the
earlier incident today needed a person. What it does not change is that the class cannot pass, that a
battery run will sit on it for as long as it is allowed to, and that the release bar is `Failures: 0`
AND `Skips: 0`. This is the second JVM stranded in one day by a test that waits without a bound, and the
first one cost this round most of its test runs. `FV3-B1` is worth fixing before anything else here,
because nobody can measure the rest of the suite until it is.
- **`FV3-B2`'s interleaving**, and `S14-B4`'s. Producing it needs a rebuild and an unlink to overlap on
  one locomotive; like `S14` and `NSV` before me, I established the call graph and not the race.
- **Whether a real Central Station resolves a `pfeil`'s `.artikel` by page position or by `.id`.** Still
  open from `NSV`, still needs the hardware, and `FV3-A2`'s fix should be written so the answer does not
  change it.
