# Duplication and design review — 2026-08-22

**Status:** open

**Prefix for citing this document: `DD`.**

**Reviewed at `915ed88e`** (`autonomy-diagram-r0` HEAD), 2026-08-22. The working tree was already dirty
when this pass began — four files under `cs2_sample_layout/config/`, which is relevant to DD-B6's last
point about tests that write to a tracked fixture. **Nothing was changed by this pass.** This document
is the only file it wrote.

**Scope.** Code duplication and design shape, not correctness. Another reviewer covered correctness in
the same window (`2026-08-22-independent-week-review.md`, prefix `IR`), and this pass deliberately did
not re-run that. Where a defect appears below it is cited as *evidence that a duplication costs
something*, not as a new correctness finding — but several of them are live, and those are marked.

**Method.** Read the two editors and the two diagram surfaces, `RouteEditorFrame`, the store, the menu
family, the `TileGraph → GraphReducer → AutonomyBuilder` chain, `TrainControlUI`'s hand-written half,
and `test/`, with `git log -S` / `git log -p` used throughout to find commits that had to make one
change in several places, and commits that fixed one copy and left another.

Five parallel readings plus a direct reading of the store, and **every claim promoted to A or B was
re-checked against the source by hand before it was written down.** Two did not survive that check and
one was downgraded; all three are recorded in **D**, which is the part of this document most worth
reading second. Nothing was compiled and nothing was run — in particular the test battery was not run,
because it binds UDP 15730.

Severities follow [README.md](README.md), read for a design review as the brief asked:

| | |
|---|---|
| **A** | Duplication or a design fault that has already produced defects, or is producing them now. |
| **B** | Duplication or coupling that will produce a defect the next time somebody touches it. |
| **C** | Worth doing, no urgency. |
| **D** | Considered and rejected — including things that look alike and must not be merged. |

---

## Ranking

By expected value: pain removed divided by risk taken. This is the order I would do them in, and it is
not the order they are numbered in.

| Rank | Finding | Why here |
|---|---|---|
| 1 | **DD-A2** | One line in `build.xml` per class. The guard that exists to prevent DD-A1 is not being run. |
| 2 | **DD-A3**, **DD-A4** | Two live user-visible route-editor defects, ~40 lines between them, no structural change. |
| 3 | **DD-A6** | Re-wire one call and two safety warnings come back. ~80 lines, low risk. |
| 4 | **DD-B4** | One pure function closes a drift Adam already reported once. Testable without the railway. |
| 5 | **DD-B3**, **DD-B5** | `discard()` and the empty-menu guard become impossible to forget. ~55 lines. |
| 6 | **DD-A7** (parts 2 and 3) + **DD-B9** | The terminus badge, and one reachability walk instead of two. Part 1 changes which findings fire, so it wants its own commit. |
| 7 | **DD-B6** (first four pieces) | `TestFolder`, `TestGlobals`, `Pages`, `SampleLayout`. Pure deletion of duplicates. |
| 8 | **DD-A1** | The largest win and the largest blast radius. Do it *after* DD-A2, never before. |
| 9 | **DD-B7**, **DD-B2**, **DD-B1** | Menu work. Needs a hands-on pass, so it batches badly with anything else. |
| 10 | **DD-C\*** | Cleanups. Any of them can ride along with work already in the area. DD-C9 and DD-C10 are ten minutes each. |
| — | **DD-B6** (`TestModel.release`) | High value, highest risk. Its own commit, its own proof. |

---

## A — duplication that has already produced defects

| # | Finding | Status |
|---|---|---|
| DD-A1 | `AutonomyCompanionStore`: eleven collections, fourteen per-collection sites, and the four commits it took to finish adding the eleventh | Open |
| DD-A2 | The matrix test that guards DD-A1 is one of thirty-five test classes `ant test` never runs | Open |
| DD-A3 | `RouteEditorFrame`: the greying fix `27261d16` claims to have landed is overwritten six lines later | Open |
| DD-A4 | `RouteEditorFrame`: `asShown` is applied in one of the conditions table's four paths, in the direction its own javadoc says loses a signal | Open |
| DD-A5 | A pruning rule lifted between two registries without its precondition stopped two of three real signal tiles updating | Fixed in `d6b9b00c`; the copies are still unequal |
| DD-A6 | `HomeLocomotiveMenu` lost four of its five callers; two safety warnings are now unreachable and their tests still pass | Open |
| DD-A7 | The checker re-implements rules the builder enforces; it disagreed with the railway once, and two of the copies have drifted again since | Open |

---

### DD-A1 — the store says the same thing eleven times, fourteen times over

**Where.** `src/org/traincontrol/automationui/AutonomyCompanionStore.java`, 2870 lines.

Eleven collections, declared in six separate places rather than together:

| Field | Line | Shape |
|---|---|---|
| `pointNames` | 74 | `Map<String,String>` |
| `stations` | 75 | `Set<String>` |
| `tileLengths` | 76 | `Map<String,Integer>` |
| `tileDirections` | 77 | `Map<String,String>`, keys carry a `#route` suffix |
| `barredArrivals` | 91 | `Map<String,String>` |
| `portals` | 172 | `Map<String,String>`, **value is also a square** |
| `stationSignals` | 187 | `Map<String,List<String>>`, **values are squares** |
| `captions` | 300 | `Map<String,String>`, **value is also a square** |
| `linkNames` | 301 | `Map<String,String>` |
| `excludedPages` | 302 | `Set<String>`, keyed by **page**, not square |
| `disabledPortals` | 669 | `Set<String>` |

And a twelfth that is not a field at all: the `points` object inside every entry of `configurations`
(310), which is square-keyed and has to be handled in five of the sites below.

The brief said "at least six places". It is fourteen:

| Site | Lines |
|---|---|
| `sharedFields()` — write | 785–795 |
| `KNOWN_SHARED` — must agree with the above | 2188–2191 |
| `readShared()` — read | 2209–2230 |
| `readShared()` — untranslate (a *second* list inside the same method) | 2233–2241 |
| `clear()` | 2273–2295 |
| `clearShared()` | 1027–1043 |
| `renamePage()` | 1284–1346 |
| `moveTiles()` | 1469–1518 |
| `forgetSquares()` | 1751–1820 |
| `snapshotPage()` | 1545–1577 |
| `restorePage()` | 1596–1631 |
| `reconcile()` | 2021–2070 |
| `applyTo()` | 2132–2171 |
| the translate/untranslate helper family | 2405–2551, 2686–2696 |

Together with the move/rekey helper family (1665–1938, 2735–2806) that is roughly **830 of the file's
2870 lines** — 29% — doing per-collection bookkeeping.

**Evidence. The eleventh setting took five commits and five days to finish adding.**

`disabledPortals` was introduced on 2026-08-17 by `ed47019f` *"Links: report each problem once, judge
it by reachability, and let autonomy be told to ignore one"*. That commit wired it into the
declaration, the getter/setter, `readShared`, `untranslateSet`, `sharedFields`, `KNOWN_SHARED`,
`applyTo` and `clearShared`. It missed four sites, and each was found later as a bug:

| Commit | Date | What it had to add |
|---|---|---|
| `d4cc22ad` | 08-18 | the rename loop in `renamePage` (now 1306–1314) |
| `941070da` | 08-20 | `moveMembers(disabledPortals, byKey)` in `moveTiles` (1493) |
| `38f4fa89` | 08-20 | `membersOnPage` / `putMembersBack` in `snapshotPage`/`restorePage` (1555, 1606) |
| `174178c5` | 08-21 | `disabledPortals.remove(key)` in `forgetSquares` (1769) |

`renamePage`'s own comment records what the first of those cost: *"a link switched off is remembered by
its square, so a rename turned every one of them back on — silently, and only on the renamed page"*
(1304–1305).

**And the same shape has produced at least four more, all documented in the code itself:**

- **`clear()` was missing `stationSignals.clear()` while `clearShared()` had always had it.** Fixed in
  `174178c5`; the comment at 2280–2283 says what it cost: *"a pairing made since the last save survived
  a discard, and the next save wrote it to disk. A signal somebody had cancelled was then thrown on
  real hardware."* Two near-identical clearing methods, seventeen and twenty-three lines apart in
  content, one of them wrong.
- **`captions` was missing from `KNOWN_SHARED`.** The comment at 2176–2187 explains why that is worse
  than untidy: an unlisted field is treated as something a newer build wrote and is written back *after*
  the real one, so *"every edit to that field since the load is reverted the moment anything saves"*.
  The migration recorded the captions, saved, had them overwritten with the empty copy read a moment
  earlier, and stripped the labels they had been migrated from.
- **`tileDirections` were left behind by every move**, because their keys carry a `#state,index`
  suffix and `moveKeys` matches whole keys (1471–1475). The fix was a second, separately named helper,
  `moveSuffixedKeys` (1829).
- **`captions` were rekeyed on the key side only**, so *"every caption on the page [pointed] at a
  station on a page that no longer exists, and the next save deleted them for good as unreconcilable"*
  (1297–1300). The same lesson had to be learned again for `portals`, and again for the `configurations`
  `points` object in both `renamePage` (1328–1332) and `moveTiles` (1495–1497) — the second comment
  says *"See renamePage, which learned this the same way."*

**Two collections are missing from a site right now.** `reconcile()` (2010–2073) calls `dropMissing`
for `tileLengths`, `tileDirections`, `barredArrivals` and `stationSignals`, handles `captions`,
`pointNames` and `portals` with their own bespoke rules — and says nothing at all about `linkNames`
(no `reconcile` reference anywhere in its fourteen sites, 645–660) or `disabledPortals`. So a link name
and a disabled flag on a square whose tile has been deleted from the diagram persist in the file
indefinitely, and a link later drawn on that square inherits both. There is no comment claiming this is
deliberate, and there is one for every other decision in that method.

One line of `forgetSquares` is already dead as a result of the shape: `tileDirections.remove(key)`
at 1755 can never match, because those keys are suffixed — the loop at 1773–1779 is what actually does
it. Nothing is wrong; the line is just the eleventh member of a list, written without checking whether
it applies.

**What I would do instead.** A registry of *kept collections*, each knowing how to do the bookkeeping
to itself. Java 8, no libraries:

```java
private abstract static class Kept
{
    final String field;                  // the JSON field name and the store's own field name
    abstract void rekeyPage(String from, String to);
    abstract void move(Map<String,String> moves);
    abstract void forget(Set<String> squares, Map<String,String> arriving);
    abstract Object snapshot(String page);
    abstract void restore(String page, Object was);
    abstract void clear();
    abstract void write(JSONObject root);
    abstract void read(JSONObject root);
}
```

Four concrete kinds — `KeptSet`, `KeptMap<T>` (with a value codec and a `suffixed` flag),
`KeptSquareMap` (key *and* value are squares), `KeptListMap` (values are lists of squares) — plus one
for the configurations' `points`. Declaration becomes:

```java
private final Map<String,String>  pointNames  = kept.map("pointNames");
private final Set<String>         stations    = kept.set("stations");
private final Map<String,String>  tileDirections = kept.suffixedMap("tileDirections");
private final Map<String,String>  captions    = kept.squareValuedMap("captions", SPARE_OWN_LABEL);
```

and each of the eight bookkeeping sites becomes `for (Kept k : kept) k.move(byKey);`. `KNOWN_SHARED`
becomes `kept.fieldNames()` and cannot disagree with `sharedFields()` because there is one list.

**Type erasure is a real constraint here and it is survivable.** The comment at 1887–1891 is right that
`moveListValues(Map<String,List<String>>)` cannot be an overload of `moveKeys(Map<String,T>)`. But that
argument has been over-applied: the differences move from *overload resolution* into *distinct
classes*, and each class names its own type once. Three of the nine translate helpers disappear
outright (see DD-C3).

**What must stay hand-written even after this lands:**

- `reconcile()` and `applyTo()`. Those are *policy*, not bookkeeping — a name has a different lifetime
  from a length (1996–2005), a half-pairing is worse than none (2055), a direction naming a route the
  tile no longer has is simply not applied (2169). Forcing them through the registry would produce a
  hook per collection and no shared code.
- Four per-collection exceptions that need hooks: captions' "spare the label the arriving station is
  landing on" (1761–1766); `stationSignals`' "remove the square from the list, drop the entry when the
  list empties" (1799–1807); `stationSignals`' "write a single signal as a bare string, an array only
  for a second" (2482–2485); `portals`' mutual unpairing.
- `excludedPages`. It is keyed by page, not square, and belongs outside the registry.

**Size and risk, honestly.** This touches ~830 lines and adds ~250 of registry classes; net probably
−400 lines and a day or two of work. The risk is the highest in this document: the store *is* the
user's entire setup, and every failure mode it has ever had has been silent data loss discovered days
later. **Do not attempt it until DD-A2 is fixed and the matrix has been extended** (see there). With
those in place the acceptance gate is mechanical: every cell that passes now must still pass.

There is a much cheaper 80% available, and it is DD-A2.

---

### DD-A2 — the guard that exists to prevent DD-A1 is not in the battery

**Where.** `build.xml:75–155`; `test/testAutonomyStoreSettingsMatrix.java` (464 lines).

`testAutonomyStoreSettingsMatrix` is the right answer to DD-A1 and its header says so: eleven settings
down one side, five operations along the other, every cell asserted, and a reflective guard
(`testEveryCollectionInTheStoreIsAccountedFor`, 334–363) that fails the build if a field is added to the
store without being put in the matrix or explicitly declared not square-keyed. Its failure message is
the best sentence in the test corpus: *"Every one of the settings bugs in this project so far has been
a collection that one operation did not know about."*

**It does not run.** `build.xml` invokes tests one class at a time — 41 `<test-one-class>` lines. `test/`
holds 76 test classes. **Thirty-five of them are never run by `ant test`**, and
`testAutonomyStoreSettingsMatrix` is one:

```
testAutonomyGroundTruth      testAutonomyStoreSettingsMatrix  testAutonomyTileMove
testBusyDialogInteraction    testCommandRow                   testConditionOutline
testConditionRows            testControlStationFaults         testDiagramExport
testDiagramResize            testDiagramShiftKeepsSetup       testDiscardedEditsDoNotDeleteSetup
testLayoutEditorBulkEdits    testLayoutFolderRobustness       testMaxActiveTrains
testMockCentralStation       testNoSelfRecursiveWrappers      testNonReversibleTrains
testRenderingCost            testRouteCapture                 testRouteCommandParity
testRouteEditorLocked        testRouteEditorValidation        testRouteInventory
testRoutePicking             testRouteReachesTheRails         testStationLabelsFollowMoves
testStuckTrainAdvisory       testThreeWaySwitch               testTileSelection
testTimetableOnDerivedGraph  testTracedPathIsContinuous       testUiStateIsNotLostWhenUnreadable
testWhyStuck                 testDiagramShiftKeepsSetup
```

(`testAutoDetect` is deliberately excluded and is not counted above.)

`build.xml:99` states the rule — *"Adding a test class means adding a line here"* — which is a
maintenance obligation with no enforcement, and it has been missed thirty-five times. Everything on that
list is newer than the last `build.xml` edit (`e98fda19`, 2026-08-17); the route editor's whole test
suite is on it.

**The comment naming the way out is also stale.** `build.xml:97–99` says the alternative to the list is
*"a shutdown path on `MarklinControlStation` that releases the socket"*. That path was built five days
later — `MarklinControlStation.shutdown()` (`src/org/traincontrol/marklin/MarklinControlStation.java:3227`,
calling `NetworkProxy.stopListening()`) landed in `0b5f5e73` on 2026-08-21 — and has **zero callers in
`test/`**.

**What I would do.** Three things, in order and separately:

1. **Add the thirty-five lines.** Ten minutes. Expect fallout — several of these have never run in the
   battery and some leak state (DD-B6) — but a red suite that tells the truth is worth more than a green
   one that runs 54% of itself. Then add a check that fails when a `test/*.java` file has no
   `<test-one-class>` line, so this cannot recur; a tiny `testEveryTestClassIsInTheBuildFile` doing a
   directory listing against a parse of `build.xml` is the same trick the matrix already uses on itself.
2. **Extend the matrix by three operations.** It covers move, build-over, snapshot/restore, rename and
   save/load: five of the eight bookkeeping sites in DD-A1. It does not cover `reconcile()`,
   `clear()`-then-load (which is exactly the operation that threw a cancelled signal on real hardware),
   or `exportBundle`/`importBundle` (which is `sharedFields` + `KNOWN_SHARED`, the pair that lost the
   captions). Three operations × eleven settings = 33 more cells, and two of them fail today —
   `linkNames` and `disabledPortals` are absent from `reconcile`.
3. **Only then** consider DD-A1.

**Size and risk.** Step 1 is one file and no code. Step 2 is ~150 lines of test in a file that already
has the shape. Both are as low-risk as work gets here, and together they remove most of DD-A1's
expected cost without taking any of DD-A1's risk. This is the highest-value item in the document.

---

### DD-A3 — the commands table's greying was wired and then thrown away six lines later

**Where.** `src/org/traincontrol/gui/RouteEditorFrame.java:1626–1689` (the helper), `2844` (the call),
`2850–2876` (the renderer that replaces it).

`greyWhatCannotBeEdited(JTable)` captures the table's current `Object.class` renderer (1628) and
re-registers a wrapper for `Object.class` (1630). In `CommandTable`'s constructor:

```java
2844:  greyWhatCannotBeEdited(this);                                   // installs the wrapper
2846:  actOnRowMarks(this, DELETE, UP, DOWN, UP, DUPLICATE);
2850:  setDefaultRenderer(Object.class, new DefaultTableCellRenderer() { … });   // discards it
```

The wrapper never runs on the commands table. Verified by reading both methods.

**What it costs.** The replacement at 2850–2876 sets **foreground only**. The helper it displaced sets
a background as well, and its comment (1665–1671) says precisely why foreground alone is useless:

> *"Most of these cells are EMPTY — a function number on a row that is not a function, a protocol on a
> locomotive command — and grey text in an empty cell is exactly as visible as black text in an empty
> cell."*

So in the **commands** table an unusable cell — the protocol on a Stop, the delay on a Route, the
function number on a Signal — still looks exactly like an empty cell waiting to be filled in, and the
way to find out is still to click it. In the **conditions** table it is shaded.

This is a regression of a fix the log already claims landed. `27261d16` *"Adam's second round"* states
it plainly:

> *"Both are the same defect from opposite sides: `greyWhatCannotBeEdited` was only ever wired to the
> conditions table, so every command cell looked alike whatever its kind — a function number on a
> signal, a protocol on a stop — and the way to find out was to click it."*

It added line 2844 and did not notice line 2850. Adam's original finding is unfixed.

**Corollary.** The carve-out at 1676, `column != POSITION`, has never executed. `POSITION` is a
commands-only column; the conditions table's column 2 is `INDENT`, which carries its own renderer. It
was written in `6a97f36c` for the table that never receives this renderer.

**What I would do.** Fold the kept-row test into `greyWhatCannotBeEdited` and delete the anonymous
renderer, so there is one `Object.class` renderer per table and no ordering to get wrong. Better still,
have the helper *return* the composed renderer rather than installing it, so a later
`setDefaultRenderer` is a visible overwrite rather than an invisible one. **~30 lines, low risk**,
verifiable by eye in one launch.

---

### DD-A4 — `asShown` is applied in one of four paths, and its own javadoc says what that costs

**Where.** `RouteEditorFrame.java:839–858` (`asShown`), and the four `ConditionTable` paths:

```
3102  getValueAt      CommandRow term = asShown(CommandRow.of(row.getCommand()));
3132  isCellEditable  CommandRow term =         CommandRow.of(row.getCommand());
3162  setValueAt      CommandRow term =         CommandRow.of(row.getCommand());
3340  getCellEditor   CommandRow term =         CommandRow.of(row.getCommand());
```

`CommandTable` has no such split: `load()` (1018–1019) stores `Entry.of(asShown(loaded.getRow()))`, so
the `SIGNAL` kind is materialised into the row itself and all four paths agree.

**What it costs.** Open a route whose *condition* names an address the layout holds a **signal** at.

- The Setting cell **displays** `red` — 3102 runs `asShown`, which promotes the kind to `SIGNAL` and
  maps `turn → red` (855–857).
- The Setting cell's **dropdown** offers `straight` and `turn` — 3340 uses the raw row, kind
  `ACCESSORY`, and `settingWords` (776–799) answers by kind.

`asShown`'s own javadoc (849–856) describes the consequence, having already fixed it once in the other
table:

> *"a signal row still carrying 'turn' had a setting its own dropdown does not contain: the combo fell
> back to the first entry, green, and one click into that cell and out again committed it. **A route
> that put a signal to danger quietly became one that cleared it.**"*

The same mechanism runs here with the words the other way round. The condition's sense flips.

This also means `1eb8b103`'s LT-B2 is half-landed: typing a signal's address into a condition now sets
the kind (3212–3224) and its default setting, but the dropdown for that row still offers switch words.

**Root cause, and why it is a design finding rather than a typo.** The two tables store *different
things*. `CommandTable` holds a `CommandRow`, which carries the Switch/Signal distinction.
`ConditionTable` holds a built `RouteCommand`, which cannot — both kinds build a `RouteCommandAccessory`
(`base/CommandRow.java:465–474`) — so the distinction has to be re-derived on every read, and three of
the four readers forgot. Choosing "Signal" in the conditions kind dropdown does not stick for the same
reason.

**What I would do.** Two stages.

*Now, ~10 lines:* one private `term(int line)` on `ConditionTable` that applies `asShown`, used by all
four paths. That closes the defect without changing what the table stores.

*Later, ~120 lines, medium risk:* the real shared abstraction is not "a table", it is **a `CommandRow`
presented as cells**. One class:

```java
enum Field { KIND, TARGET, NUMBER, SETTING, PROTOCOL, DELAY }

static String  display (CommandRow row, Field f);
static boolean editable(CommandRow row, Field f);
static TableCellEditor editor(CommandRow row, Field f);
static CommandRow write (CommandRow row, Field f, String text, LayoutLookup layout);
```

Both models map their own column integers to a `Field` and delegate. This makes DD-A4 structurally
impossible and removes a live hazard: the two tables' column indices differ (kind is 3 in commands and
4 in conditions; delete is 9 and 8) **except for setting and protocol, which are both 6 and 7 by
coincidence** — which is exactly the kind of accident that makes a copy-pasted edit look correct.

Note for whoever does the second stage: `testRouteEditorLocked` / `testRouteEditorValidation` reach the
model through hard-coded column indices (`setCommandKindForTest` etc., 1265–1278, columns 3/4/6). Move
those to `Field` names in the same commit. Both suites are on DD-A2's not-run list.

---

### DD-A5 — a rule lifted from one registry into three others, without the precondition that made it safe

**Where.** `gui/DiagramTileRegistry.java:74–82`; `gui/LayoutLabel.java:523–537` (`forgetReplaced`);
`marklin/MarklinAccessory.java:134–144`, `marklin/MarklinFeedback.java:68–79`,
`marklin/MarklinRoute.java:291–302`.

Already fixed — recorded here because it is the clearest priced example in the repository of what this
document is about, and because the copies are still unequal.

`ddff66e0` put a prune rule into `DiagramTileRegistry`: drop a registered label when it shares a window
with the arriving one and is no longer displayable. `0b5f5e73` lifted it into the three device
collections. `d6b9b00c` — *"Two more reviews: one of them found that a fix of mine was the bug"* — had
to undo it, and its message is the finding:

> *"That rule is sound because the registry's map is keyed by SQUARE, so everything under one key is
> about one square; the device collections are keyed by DEVICE, and one accessory is routinely drawn on
> several squares of a page. LayoutGrid registers every label in its build loop and attaches the
> container afterwards, so during a build nothing is displayable yet — and each arriving label therefore
> evicted its own siblings. **Signal 116 is on three squares of "2 - Bottom"; two of them stopped
> updating the moment the page was drawn.**"*

On a layout driving real hardware, two of three tiles for one signal silently stopped reflecting its
state. The commit also notes that the hands-on test written for the change could not have caught it,
because the third tile still worked.

**What remains.** The same iterate-and-prune loop is now written out **six** times — the two registries
plus the three `Marklin*` collections plus `TrainControlUI.addLayoutStation`'s block (1006–1020) — with
three different keys and three different conditions (four clauses at `LayoutLabel.java:531–532`, three at
`DiagramTileRegistry.java:78`). `TrainControlUI.java:1000–1001` says out loud: *"DiagramTileRegistry
carries this same rule, and the comment explaining it, for exactly the same reason."*

**What I would do.** One `TileLabelRegistry<K>` holding the loop, parameterised on an explicit
`keyedBySquare` flag so the precondition is a *field* rather than a paragraph. **Medium risk** — this is
the exact code that produced the defect above, and the failure mode is silent during a run. Only with a
red-before-green test per registry; `testEveryTileOfOneAccessoryStaysRegistered` already exists as the
model, and it is on DD-A2's not-run list.

---

### DD-A6 — `HomeLocomotiveMenu` lost four of its five callers, and two safety warnings went with them

**Where.** `src/org/traincontrol/gui/HomeLocomotiveMenu.java` (456 lines);
`gui/AutonomyEditorPanel.java:2133–2156` (`promptHome`), `2387–2431` (`promptLocomotives`);
`automation/HomeStaging.java:923` (`canBeHome`), `:941` (`homeBrokenByExcluding`).

The class's own javadoc (18–29) says *"Three menus reach this … One copy, three callers."* Verified by
grep: today there is **one** live caller — `addReturnHomeItem`, from
`LayoutRightclickAutonomyMenu.java:107`. `addStationItem` (92), `addClearAllItem` (150),
`editHomeLocomotive` (238, reached only from the dead `addStationItem`) and `confirmExclusion` (377)
have no callers in `src/`. Their callers were `GraphRightClickGeneralMenu`, `GraphRightClickPointMenu`
and `GraphViewer`, deleted in `d8db4879` *"The graph window is gone"*.

**What it costs.** The surviving home path, `AutonomyEditorPanel.promptHome`, is 24 lines. The abandoned
copy was 120, and the extra 96 were rules:

- **`HomeStaging.canBeHome` has no production caller.** Only `HomeLocomotiveMenu.java:339` (dead) and
  `test/testHomeStaging.java`. Its warning existed *"because … every future Return Home report
  IMPOSSIBLE, and the advice that dialog gives is to check the track, which is the wrong remedy"*.
  `HomeStaging.java:330` already carries a comment predicting exactly this.
- **`HomeStaging.homeBrokenByExcluding` likewise** — reached only from the dead `confirmExclusion` and
  from `testHomeStaging.java:2007–2013`. The live exclusion path (`AutonomyEditorPanel:1064–1066` →
  `promptLocomotives`) writes straight through, so excluding a locomotive from the station that is its
  home now silently leaves a station and a locomotive disagreeing about each other.
- **The "keep an assignment naming a locomotive not on the graph" rule is gone.** `promptHome` offers
  only `placedLocomotives()` (2137). A non-editable `JOptionPane` combo cannot preselect a value absent
  from its model, so a station whose home names a since-removed locomotive opens showing "None", and OK
  clears it. `HomeLocomotiveMenu.java:258–265` documents that trap in words.

`promptLocomotives:2420` still branches on `key.equals("home")` — a leftover from when home went through
it, and a tell that the split happened without a sweep.

**This is the worst kind of duplication:** not two copies drifting, but one copy left behind holding the
rules while the other one is used. And the tests still pass, because they call the dead code directly —
so the suite reports the guards as working.

**What I would do.** Decide it explicitly, and either way say so in the code:

- *Preferred:* re-wire `promptHome` and the exclusion path through `editHomeLocomotive` /
  `confirmExclusion`. Three rules come back in one move, and their tests start pinning something a user
  can reach. **~80 lines, low risk, highest user-visible payoff per line in this document.**
- *Or:* delete the 200 unreachable lines, delete `canBeHome` and `homeBrokenByExcluding`, delete their
  tests, and record in the review that three rules were consciously dropped.

Doing neither is the current state, and it is the only option that is definitely wrong.

---

### DD-A7 — the checker re-implements what the builder enforces, and the copies drift

**Where.** `automationui/AutonomyBuilder.java:401–418` (`splitSides`), `:482–514` (`nodesFor`),
`:779`, `:873–881`; `automationui/AutonomySession.java:2611–2621`, `2626–2671` (`check`), `:3499–3501`;
`gui/AutonomyEditorPanel.java:3665–3690` (the path test).

The pipeline is `LayoutDiagram → TileGraph → GraphReducer → AutonomyBuilder → (autonomy JSON) →
automation.Layout`. `AutonomyChecks`/`AutonomySession.check()` and the editor's path test both read the
middle of it and answer questions the *end* of it decides. Where they answer them by re-deriving rather
than by asking, they drift — and the failure is the worst kind to diagnose: **the interface tells the
user one thing and the railway does another.**

**It has already happened once.** `db1db789` *"Make the station-reachability checks split-aware (CG4)"*:

> *"`checkStations` built its reachability from a plain tile-to-tile adjacency over
> `reducer.getEdges()`, which ignores the arrival-side split: at a double curve … it reported a station
> pair reachable that the runtime bfs never routes, and no `STATION_UNREACHABLE` warning was raised.
> **The checker disagreed with the railway.**"*

The fix was right — it deleted the third copy and pointed the checker at `reachableTiles` — but it fixed
one question, not the pattern.

**Two more copies have drifted since, and I verified both by reading:**

1. **The trapped-arrival rule.** `AutonomyBuilder.splitSides` returns
   `Collections.emptyList()` — *do not split this square at all* — the moment **any** incoming edge has
   a null entry side, i.e. an arrival through a link (`:412–417`, with a comment explaining that
   splitting would strand the train). `AutonomySession.check()` (2635–2639) instead *skips* those edges
   and keeps the rest. So a square reached both through a link and by ordinary track is emitted by the
   builder as one unconstrained Point, while the checker reports `ARRIVAL_TRAPPED` against it. The
   session's own comment (2650–2656) claims *"the builder … hands the answer here"* — it does not; it
   recomputes it differently. The same shape recurs in the `MAY_TURN_ON_DEAD_END` loop at 2611–2621.
2. **Terminus versus reversing.** `AutonomyBuilder.build` (873–881) emits `stops ? "terminus" :
   "reversing"`, where `stops = point.isStation() && arrivalAllowed(node)` (779). The badge drawn on the
   diagram (`AutonomySession:3499–3501`) recomputes it as `store.isStation(tile) && isTurnAround(tile)`
   — **no `arrivalAllowed`**. On a station whose turning copy has a barred arrival, the build emits
   `reversing` and the diagram draws a terminus.

**And one gap that is open by construction.** The editor's path test (`AutonomyEditorPanel:3676–3686`)
calls `GraphReducer.findPath` with `mayTurn` and `mustTurn` only. It knows nothing of `barredArrivals`,
`manualOnly`/`autoDestination`, or `arrivalAllowed`. So it will draw "you can get there" for a platform
whose arrival side the user has barred, while the built configuration marks that copy `station:false`
and `Layout` will never route to it. The comment immediately above it (3670–3673) says the test exists
precisely so as not to give *"a second opinion instead of reporting what a train would find"* — which is
what it now does for every filter added after it was written.

Nothing pins these together: `testAutonomyGroundTruth` pins `Layout` against a **hand-authored** config,
not a diagram-built one, and it is on DD-A2's not-run list.

**Related: "is this a station" has three notions** — `store.isStation(tile)` (14 UI call sites),
`ReducedPoint.isStation()` (checks and builder), and the emitted `stops`. `AutonomyChecks` uses the
second, so `UNLABELLED_STATION` and `STATION_UNREACHABLE` fire on a wider set than the graph actually
has destinations for, and `StationIndex.speakerAt` (439–443) papers over the difference at runtime with
`point.isDestination()`.

**What I would do.** Not one big change. Three, separable:

1. **Have `check()` ask the builder** for its trapped and split answers rather than recomputing them.
   ~45 lines. **Medium risk** — it changes which findings fire, and the portal case is currently a false
   positive users may have learned to ignore, so it needs saying in the changelog.
2. **Give the badge `arrivalAllowed`**, or better, have both read one `terminusOrReversing(node)`.
   Small.
3. **Decide what the path test answers**, and say so on screen: either give `findPath` the same
   barred-arrival and destination filters the build applies, or label the result "over the track as
   drawn". Small code, **a real behaviour decision for Adam** rather than a fix.

Then pin it: one test that builds a configuration from the sample diagram and asserts that the
checker's verdict and the built graph's agree, for reachability and for trapped arrivals. That is the
test `db1db789` should have left behind, and it is the only thing that stops a fourth copy appearing.

---

## B — duplication that will produce a defect next time somebody touches it

| # | Finding | Status |
|---|---|---|
| DD-B1 | Two facing submenus, computed from two sources, in one popup | Open |
| DD-B2 | `menuOnly` is one boolean deciding four unrelated questions | Open |
| DD-B3 | Four grid-construction sites; `discard()` reaches three of them | Open |
| DD-B4 | The station caption's text is computed twice and the copies have drifted | Open |
| DD-B5 | The right-click entry point is written four times; the guard is on three | Open |
| DD-B6 | No test harness: 47 copies of the control-station init, six of a recursive delete, and leaked globals | Open |
| DD-B7 | `item(text, Runnable)` exists three times with three different failure behaviours | Open |
| DD-B8 | `AutonomySession` has two "something changed" protocols | Open |
| DD-B9 | One reachability rule, written out three times, agreeing only because somebody keeps it agreeing | Open |

---

### DD-B1 — two facing submenus in one popup

**Where.** `gui/LayoutRightclickAutonomyMenu.java:286–312` and `:385–391`;
`gui/AutonomyEditorPanel.java:1808–1839` (`buildFacingMenu`);
`gui/TrainControlUI.java:2546–2553` (`buildAutonomyFacingMenu`).

Right-click a square holding a train, with autonomy stopped and more than one facing available, and the
popup gets **both**:

| | 286–312 | 385–391 |
|---|---|---|
| label | `layout.ui.menuFacing` — "Facing" | `autosetup.ui.menuFacingGroup` — "{loc} Is Facing…" |
| controls | `JCheckBoxMenuItem` | `JRadioButtonMenuItem` |
| choices from | `session.facingsFor(station)` | `session.facingChoices(target)` |
| action | `placeFacing()` (622–661): **moves the train**, records placement and facing, saves, repaints | `session.setFacing(target, facing)` — records a note and nothing else |

I checked the brace nesting: the two are in the same branch of the same constructor and both `add()`
unconditionally when their own guards pass. They are computed from different sources — one from the
placeable copies, one from the reduced edges — so they can list **different directions**.

**How it happened.** LT-M3 (`docs/reviews/2026-08-21-layout-test-feedback.md`) asked for
"{loc} Is Facing…" to move from the deep menu up to the diagram's menu. `97c0470e` did that by adding a
two-hop accessor (`TrainControlUI.buildAutonomyFacingMenu` exists solely to reach across the split) —
without noticing that the diagram's menu already had a facing submenu of its own.

**Why it is B and not A.** Two menus that look like the same question, one of which turns the train and
one of which only writes a note, is a user-visible fault; but I have not driven it, and which one a user
reaches for is not something I can assert from the source.

**What I would do.** One implementation. Delete 286–312, keep `buildFacingMenu`, and move
`placeFacing`'s write-through into it so the surviving one does the whole job. **~40 lines, medium
risk** — the two differ in *behaviour*, so the merged version must keep the write-through, and it needs
a hands-on test.

---

### DD-B2 — `menuOnly` decides four different questions

**Where.** `gui/AutonomyEditorPanel.java:1792` (field), `1891–1894` (setter), read at
802, 808, 821, 844, 1019, 1175, 1273, 1854, 1901. Two call sites:
`TrainControlUI.java:2594` passes `true`; `LayoutEditor.java:1163` leaves the default `false`.

Its javadoc (1783–1791) claims one job: two items ask for a second click, which the menu cannot host, so
they open the editor instead. That is honest at 1175 and 1901. Since then it has picked up three more:

| Axis | Sites | Question it is really asking |
|---|---|---|
| 1 | 1175, 1901 | can a second click be completed here? |
| 2 | 802, 808, 821, 844, 1019 | does the **parent menu** already offer this item? |
| 3 | 1273 | is this a diagram edit rather than an autonomy edit? |
| 4 | 1854 | should a link jump, or scroll? |

Axis 2 is a fact about the *containing menu*, not about this panel, and it is the one that has already
been fought over: LT-M1 asked for five item groups to be hidden in the deep menu; **LT-M9 reversed part
of it one commit later** — *"Put 'Add a Locomotive to Autonomy…' back into the deep menu, against
LT-M1"* — because a single boolean could not distinguish "duplicate of the parent" from "different
question that happens to look similar".

**The concrete scenario.** Anything reached from a third surface — a pop-out diagram window, a palette,
the sidebar work now in progress — has to pick a side of all four axes at once, and there is no value of
`menuOnly` that is right.

**What I would do.** Replace the boolean with a small immutable context:

```java
final class TileMenuContext {
    final boolean hasGrid;             // a second click can be completed here
    final boolean ownsDiagram;         // caption and label edits are ours to make
    final Set<String> alreadyOffered;  // items the parent menu carries
    final boolean writesThroughToLayout;
}
```

**~150 lines in one file, two call sites, medium risk.** The risk is not the mechanics — it is
re-expressing the LT-M1…LT-M11 rulings exactly. Port them one item at a time with
`2026-08-21-layout-test-feedback.md:46–56` open as the specification.

---

### DD-B3 — four grid-construction sites, three of which call `discard()`

**Where.** `gui/TrainControlUI.java:19879–19907`, `gui/LayoutEditor.java:3563–3592`,
`gui/LayoutPopupUI.java:53–68`, `gui/DiagramExport.java:107–137`.

`LayoutGrid` (104–600) is a single, well-factored builder. What is duplicated is the seven-step sequence
around it — discard, `removeAll`, construct, title, size, `pack`, window bounds, `setVisible`,
`windowClosing` — written out separately in each host, with four different sizing policies and four
different teardown policies.

That the editor was born by copying the popup is still legible in the source: `LayoutEditor.java:3802`
carries the comment *"Scale the popup according to the size of the layout"* in a class that is not a
popup, and `:3842` carries *"Hide the window on close so that LayoutLabels know they can be deleted"*
above a `windowClosing` that calls `confirmExit()` and hides nothing. Both date from `d1ce0700`
*"Native editing — initial"*.

**Evidence.** `512043bb` introduced `LayoutGrid.discard()` and wired it into two of the three windows.
`174178c5` had to add the third, and the comment it wrote is the finding
(`LayoutPopupUI.java:46–53`):

> *"The grid being replaced is told to stop first. … both go on firing into the panel after the panel
> has been emptied. The outgoing grid then drops a spinner into the page the NEW grid has just drawn.
> **Both other places that build a grid over an existing panel call this; this one did not.**"*

The same commit had to add two `parent != null` guards inside `LayoutLabel` (467, 806) because the
*fourth* site, `DiagramExport`, passes a null master and nobody had considered it — its commit body:
*"Exporting a picture of the diagram permanently stopped tile updates for it."*

**The fourth site still does not participate.** `grep -rn "discard()" src/` returns three call sites:
`LayoutEditor:3570`, `LayoutPopupUI:53`, `TrainControlUI:19879`. `DiagramExport` never discards, so its
grid's failsafe (8 s) and grace (120 ms) timers stay armed on a throwaway panel. It works only because
`LayoutGrid`'s 8-second failsafe is shorter than `DiagramExport.TILE_WAIT_SECONDS = 30` — a correctness
dependency between two files that neither states.

**What I would do.** A `DiagramGridHost` holding the current grid and exposing one `rebuild(...)` that
does discard → `removeAll` → construct → store. Each site keeps its own sizing and cache policy
afterwards; no Swing component moves into or out of a generated form, so no GEN-BEGIN block is touched.
**~40 lines added, ~30 removed, low risk.** It makes `discard()` structurally impossible to forget,
which is the exact bug `174178c5` fixed by hand.

---

### DD-B4 — the station caption is drawn twice, and the copies have drifted

**Where.** `gui/LayoutGrid.java:350–425` (build time, the autonomy editor) and
`gui/TrainControlUI.java:3120–3221` (`updateStationLabels`, run time, the running diagram). They share
three constants (`LayoutGrid.java:46–50`) and nothing else.

`cdea0f81` (AR-13/AR-14) had to copy the running diagram's appearance into the grid, and admitted it
in the comment it added (`LayoutGrid.java:361–369`): *"The running diagram's own style for a named
train: black on translucent white, so it reads over whatever tile art is underneath."* An earlier
commit is named for the same act: `f3eb2168` *"In the autonomy editor, show station labels **as the
diagram shows them**"*.

**The copy is still incomplete.** `LayoutGrid:361` writes the raw name from `autonomyLocomotiveAt`.
`TrainControlUI:3152` writes `"[" + name.substring(0, min(len, LAYOUT_STATION_MAX_LENGTH)) +
facingArrowOf(...) + "]"`. Neither the ten-character cap, nor the brackets, nor the facing arrow, nor
the two-trains-on-one-square handling (`crowdedLabel`, 3024–3053, from `6be8999c`) exists in the editor
copy — `grep` for `crowdedLabel|facingArrowOf` returns nothing in `LayoutGrid.java` or `LayoutLabel.java`.

**The concrete scenario.** The next change to how a train's name reads on the diagram will be made in
one of these and not the other, exactly as the last two were.

**What I would do.** Extract the *text and colour decision* — a pure function of (the locomotive(s), the
square, whether this is the editor or the running view) returning text plus foreground plus background.
Called from both. **Low risk, high value**: no Swing lifecycle, unit-testable without the railway, and
it closes an open drift Adam already reported once. **Do not** try to make one call the other — see
DD-D1.

---

### DD-B5 — the right-click entry point is written four times, and the guard is on three

**Where.**

| Site | Guard | Trigger |
|---|---|---|
| `gui/LayoutLabel.java:604–638` | `if (menu.getComponentCount() > 0)` (634) | — |
| `gui/LayoutPopupUI.java:222–237` | `:231` | — |
| `gui/TrainControlUI.java:18302–18319` | `:18313` | — |
| `gui/LayoutGrid.java:314–323` | **none** | `e.getButton() == BUTTON3` |

`git log -S'menu.getComponentCount() > 0'` returns exactly one commit, `7d6d742f`, which added the guard
to three files together — the three added lines being byte-identical between two of them. The fourth
site was added later, by `a4651d20`, and never received it. `LayoutLabel`'s comment says why it matters:
*"a one-item-high grey box appearing under the pointer reads as a fault."*

Separately, the six `RightClick*Menu` classes carry five verbatim copies of the
`mousePressed`/`mouseReleased`/`isPopupTrigger`/`showPopup` adapter, while the four diagram sites use
`e.getButton() == BUTTON3` — two incompatible conventions for the same gesture, and `isPopupTrigger` is
the portable one.

**What I would do.** One static entry point:
`showAutonomyMenu(TrainControlUI ui, TileKey station, TileKey here, Component at, int x, int y)`
containing the `invokeLater` + guard + `show`. **~15 lines, low risk**, closes the missing guard.

---

### DD-B6 — there is no test harness, and `test/` has paid for it in three currencies

**Where.** `test/`, 76 test classes, ~2 shared helpers.

**The repetition.** Counted:

| Pattern | Copies |
|---|---|
| `MarklinControlStation.init(...)` in `@BeforeClass` | **47 classes** |
| Recursive `delete(File)` helper | **6 verbatim copies**, two formattings, plus 2 `Files.walk` variants |
| `cs2_sample_layout` load + `CS2File` + `parseLayout` | **8 classes**, 12 sites |
| Hand-built `Layout`/`Point`/`Edge` fixture | **14 classes**, 240 calls |
| `LayoutDiagram` `page(name, sx, sy)` helper | **3 identical copies** |
| `AutonomyCompanionStore` fixture | **6 classes**, 23 inline constructions |
| `json(String)` single-to-double-quote helper | 2 verbatim copies |

**The drift, which is the part that matters.**

- `model.stop()` follows `init()` in **27 of the 47** and is omitted in 20. Some of those omissions are
  deliberate; most are not, and nothing distinguishes them.
- The missing-fixture policy has **four** incompatible forms: `SkipException`
  (`testRenderingCost:43`), `assertTrue` (`testTracedPathIsContinuous:55`), **silent `return`**
  (`testRouteInventory:70/80/90/100` — a pass that can mean "did not run"), and two independent copies
  of a `findLayoutFolder()` with a `user.dir` fallback.
- The URL idiom has two variants: `.replace('\\','/')` (9 sites) and `.replace(File.separatorChar,'/')`
  (2 sites).
- `feedback()` in the page builders passes **different arguments for logical and raw address**:
  `(raw/2, raw)` in `testAutonomyDiagramReducer:575` and `testAutonomyDiagramReversal:652`, and
  `(a, a)` in `testAutonomyDiagramTiles:687`. The Reducer copy's javadoc says the split is deliberate,
  *"so a Point built from the wrong one shows up immediately rather than agreeing by coincidence"*. The
  Tiles copy has no such comment and would agree by coincidence. **Two names, both documented — see
  DD-D8.**
- The s88 address for hand-built graphs is allocated per class with no registry: 47000, 47100, 47200,
  47300, 47401–47412, 9001–9003, 81–106, 170–171, 190–193. `testLayoutBfs:62` calls this *"the
  convention in testAutonomyPathValidation"* — a convention held only in prose.
- Locomotive teardown has three incompatible strategies: a named array plus a loop (4 classes), a
  hand-maintained literal list that can fall out of step with the creation side (3 classes), and
  **no cleanup at all, using `model.getLocList().get(0)`** (≈25 sites) — which assumes the operator's
  real database has at least two locomotives and that a particular two sort first.

**What it has cost, in the code's own words.**

- `f956946b`: *"newSignal adds to the live database — the user's real one, since these tests run against
  installed data — so two invented signals had been persisted into it, and the suite stopped coming back
  byte-identical."* Fixed in one file.
- `test/testRouteCommandParity.java:23–29`: a copy-pasted `init()` in a class that did not need one
  *"binds the Central Station's UDP port … which makes every later model-based class report 'Address
  already in use' out of its own setup, which TestNG then renders as a clean skip."* **One copy-paste
  turned part of the battery green by skipping.**
- Leaks, still present: `Layout.PATH_INTEGRITY_VALIDATION` set `true` at
  `testAutonomySimulationSanity:76` and never restored; `testAutonomyPathValidation:62` "restores" it by
  setting `true` again — a literal, not the captured value; `DEBUG_SIMULATE_PACKETS` set inside `@Test`
  bodies at `testLocomotive:39` and `:357` with no restore. Two classes get this right
  (`testControlStationFaults:32–47`, `testRouteReachesTheRails:48–65`) and state the rule.
- Six temp directories are never deleted, and two use `deleteOnExit()` on a directory that is then
  written into — which is a no-op.
- Three classes open an `AutonomySession` directly on the **tracked** `cs2_sample_layout` fixture
  (`testRouteInventory:124` and `:477`, `testTimetableOnDerivedGraph:355`, `testTracedPathIsContinuous:64`),
  and `AutonomySession.open()` can write (it calls `migrateStationLabels()`, which calls `store.save()`
  and `saveChanges`). One class gets this right and copies the folder first
  (`testDiscardedEditsDoNotDeleteSetup.openOn()`, 209–236), with a javadoc saying why.

**What the harness should contain.** Six small files in `test/`, default package, ~600 lines:

| File | Contents |
|---|---|
| `TestModel` | `quiet()`, `debug()`, `withUI()` — **three named factories, no defaulting one-arg form** — and `release(model)` calling the `shutdown()` that has zero test callers today |
| `TestGlobals` | `capture()` / `restore()` over the six process-global switches. Captures and restores; **never sets** |
| `TestLocs` | `mm2`/`dcc` that refuse an existing name and record what they created; `deleteAll()` that deletes **only** its own creations; `freeMM2Address(model)` that scans rather than assumes; the asserting `clearAccessoryAddress` |
| `TestFolder` | `create`, `child`, `write`, recursive `delete()` — replaces six copies and fixes six leaks |
| `SampleLayout` | one `folder()`, one `requireOrSkip()`, one `url()`, one `parse()`, and **`copy()`** for anything that will open a session |
| `Pages` | `page`, `straight`, `add`, `wire`, `graph`, and **two** feedback helpers with distinct names |

**What must stay per-test.** This is the half that decides whether the harness helps or hides:

- **The `init` flags where they *are* the fixture.** `showUI=true` at `testAutonomyPathValidation:41`
  (the popup *is* the assertion) and `testLayoutTiles:60`. `debug=true` where the simulated-echo branch
  is what makes accessory confirmations arrive at all. A blanket `quiet()` would silently change what
  22 classes test.
- **`model.setNetworkCommState(false)`** (5 classes) — that call *is* the fault being arranged.
- **The values of the globals.** `PATH_VALIDATION_MS = 100` vs `1000`;
  `PATH_VALIDATION_ALERT_THRESHOLD = 3` vs `Integer.MAX_VALUE` (*"so the failure counter never resets —
  any single failure would therefore be caught"*). Share the mechanism, never the numbers.
- **Graph shapes that encode the property**: `testHomeStaging`'s `ring()`, `testAdvancedRoutes`'s
  per-test point name (*"parseAuto replaces the whole graph"*), `testLayoutRenameKeys`'s fresh feedback
  modules per call.
- **`model.getLocList().get(0)` should be removed, not centralised.** A `TestLocs.anyLocomotive()` would
  make the wrong pattern convenient at 25 sites.

**Size and risk.** `TestFolder`, `TestGlobals`, `Pages` and `SampleLayout` are ~360 lines and mostly
deletion; land them together. `TestLocs` touches the operator's real database — its own commit, and
confirm `LocDB.data` comes back byte-identical. **`TestModel.release()` is the one to be careful with**:
do not roll it out to 47 files. Add it to two, then prove `init()` → `release()` → `init()` rebinds in
one JVM. Only then attempt to collapse `build.xml`. Sharing one JVM will surface every leaked static
above as a real failure — that is the point, but budget for a round of fallout rather than a green run.

---

### DD-B7 — three copies of `item(text, Runnable)`, three different things happen when it throws

**Where.** `gui/AutonomyEditorPanel.java:1364–1397`, `gui/AutonomyMenu.java:620–626`,
`gui/AutonomyViewerPanel.java:446–451`.

| Copy | On `RuntimeException` |
|---|---|
| `AutonomyEditorPanel` | catches, shows the exception's **class** when the message is empty, logs the stack, refreshes, flashes the target |
| `AutonomyMenu` | nothing — `menuItem.addActionListener(e -> action.run());` |
| `AutonomyViewerPanel` | nothing |

The first was rewritten by `cdea0f81` for AR-1. Its comment: *"A NullPointerException has no message, so
this showed a dialog whose entire content was the word 'null' — which tells the user nothing and told me
nothing either, for a week."* The other two copies were not swept.

The same unfixed handler shape — `showMessageDialog(this, e.getMessage())` — appears **14 times** in
`LayoutEditorRightclickMenu` (60, 182, 200, 222, 238, 260, 311, 338, 361, 386, 432, 449, 482, 509), four
times in `LayoutRightclickAutonomyMenu` (101, 179, 196, 406) and eleven times in `TrainControlUI`.
`LayoutEditorRightclickMenu.addShift` (491–516) even states the principle — *"four copies of the same
try/catch is four places for one of them to drift"* — and was applied to four of that file's eighteen
items.

**Reachability, honestly.** I checked the two unguarded copies' actual actions. Both delegate to
`AutonomyViewerPanel`'s own methods, and those catch their own exceptions —
`importConfiguration` catches `IOException | RuntimeException` (1034–1039), `rename` and `delete` catch
`IOException` and guard on null. So this is a **trap for the next caller**, not a live defect, which is
why it is B and not A. The next action added to either menu that does not catch for itself will throw
out of the EDT and the item will silently do nothing.

**What I would do.** One `MenuActions` helper — `item(text, Runnable)` with the *fixed* handler,
`toggle`, `radio`, `title(popup, text)` — replacing the three copies and the 29 handlers. **~250 lines
touched across four files, low risk, mechanical.** This is the change that stops the *next* `cdea0f81`
from reaching one site out of thirty. Do it first and alone.

Related, same file pair: the disabled heading item is bold in `AutonomyEditorPanel.title` (1343–1351)
and in `LayoutEditorRightclickMenu:26–30`, and **not** bold in `LayoutRightclickAutonomyMenu:137–139`
and `:217–219` — so opening the diagram's menu over a station shows a plain grey heading with bold grey
headings in the submenu directly beneath it.

---

### DD-B8 — two "something changed" protocols in `AutonomySession`

**Where.** `automationui/AutonomySession.java:3620–3624` (`touched()`), and the two sites that do not
use it: `:2397` and `:3000–3029` (`setPointProperty`).

Sixteen mutators call `touched()` — `dirty = true; rebuild();`. Two set `dirty = true` inline;
`setPointProperty` additionally calls `deriveStationIndex()` and *deliberately does not rebuild*, with a
comment (3023–3028) explaining that the cached split names go stale otherwise.

The reasoning is right. The problem is that a new mutator now has to pick between two protocols, and
nothing but that one comment says how. **Low urgency** — sixteen against two is a healthy ratio — but the
fix is cheap: a second named method (`touchedWithoutRebuild()`, or `touched(Rebuild.NO)`) so the choice
is made from a menu of two rather than from reading one comment.

---

### DD-B9 — one reachability rule, written out three times

**Where.** `automationui/GraphReducer.java:403–515` (`findPath`), `:539–608` (`reachableTiles`),
`:610–620` (`onwardSides`); `automationui/AutonomyBuilder.java:114–136`
(`Node.leavesBy`/`arrivesBy`), `:427–439` (`onwardFrom`); and downstream, `automation/Layout.bfs`.

`findPath` and `reachableTiles` are the same `(tile, arrival-side)` frontier walk written twice — the
second one's own comment says so: *"The same three-way rule `findPath` walks"* (576–579). The three-way
turn block is byte-identical at 462–487 and 573–591. `AutonomyBuilder` encodes the same rule a third
time, as graph *shape* rather than as a walk, and `Layout.bfs` then runs on that shape.
`GraphReducer.onwardSides` and `AutonomyBuilder.onwardFrom` have the same body over `graph.exits`.

**The concrete scenario.** They agree today. The next change to the three-way turn rule — which is
exactly the kind of thing this project changes — has to be made in three files that do not reference
each other, and the third one is the one that decides what the railway actually does. DD-A7 is what that
looks like after it has gone wrong.

**What I would do.** Collapse `findPath` and `reachableTiles` onto one private walk taking a visitor:
one stops at a target and returns the edge list, the other exhausts and returns the set. **~60 lines
removed, low risk** — `testAutonomyDiagramReducer` already covers both. Leave `AutonomyBuilder`'s third
encoding alone: it is genuinely a different representation (shape, not traversal), and merging it would
mean the builder walking its own output. Pin it with the agreement test proposed in DD-A7 instead.

---

## C — worth doing, no urgency

| # | Finding | Status |
|---|---|---|
| DD-C1 | `TrainControlUI`: which pieces could be lifted out, and which cannot | Open |
| DD-C2 | One image cache, two key namespaces, no eviction, and one key that omits a parameter | Open |
| DD-C3 | Three of the store's nine translate helpers exist only because a generic was not used | Open |
| DD-C4 | Dead code left behind by de-duplications that were not finished | Open |
| DD-C5 | Five verbatim copies of the popup-trigger adapter, and two conventions for one gesture | Open |
| DD-C6 | The store's eleven collections are declared in six places | Open |
| DD-C7 | ~290 `JOptionPane` calls name their parent five different ways | Open |
| DD-C8 | A 426-line keyboard dispatcher written as an if/else chain | Open |
| DD-C9 | `TileGraph` has `sideTowards` and `sideToward` — two methods, one letter apart, answering one question two ways | Open |
| DD-C10 | The port table exists three times, one of them a Python script the javadoc asks you to hand-edit in step | Open |
| DD-C11 | The trace/segment loops re-derive geometrically what the reducer already recorded | Open |

---

### DD-C1 — `TrainControlUI`, and what could actually leave it

19,928 lines. `initComponents` is 6641–12526 — 5,885 lines, 30% — and is not fair game. That leaves
~14,000 hand-written lines. **This is not a proposal to split the class**; it is an answer to "which
pieces could be lifted out with a small, well-defined interface", so that when somebody has a reason to,
they know where the seams are.

**Liftable, in order of how clean the seam is:**

1. **Image loading and caching.** `imageCache` (432), `getImageCache` (4040), `getLocImage` (5648),
   `getLocImageMaxHeight` (5678), the six `ExecutorService` fields (366–373), `tileDecodeStarted` /
   `tileDecodeFinished` / `whenTilesSettled` (6131–6193), `noImageButton` (5633). The cache is already
   `static`; `LayoutLabel` already reaches it through the accessor. **~350 lines, near-zero entanglement,
   and moving it is the natural moment to fix DD-C2.**
2. **The locomotive-to-button map.** `buttonMapping`, `labelMapping`, `sliderMapping`, `rSliderMapping`,
   `locMapping`, `functionMapping`, `rFunctionMapping`, `pageNames` (353–363) and the ~40 methods over
   them (`switchLocMapping` 4084, `setPageName` 4149, `getPageName` 4170/4190, `renameCurrentPage` 4244,
   `addLocMappingPage` 1077, `deleteCurrentLocMappingPage` 1100, `mapLocToCurrentButton` 5311,
   `doPaste` 5271, `setCopyTarget` 5063, `copyToNextPage`/`copyToPrevPage` 5089/5103,
   `next`/`prev`/`currentLocMapping` 6472–6505, `getAllLocButtonMappings` 1592, `jumpToLocomotive` 1655).
   **168 references in the hand-written half**, and a small external surface — five methods reached from
   `AddLocomotive`, `AutoLocomotiveStatus`, `LocomotiveSelectorItem` and `RightClickMenuListener`. The
   `JButton`s come from the generated form, but the model can hold them as opaque keys. **~1,800 lines;
   the cost is `saveState`/`restoreState` (1369–1806), which serialise this together with everything
   else into one `UIState.data` blob — the extracted class would have to own its own serialisable form,
   and that file's format is load-bearing.**
3. **Layout-folder plumbing.** `createAndApplyEmptyLayout` (4886), `initializeEmptyLayout` (14623),
   `copyResource` (14662), `unzipFile` (14685), `promptUserForLayout` (17394),
   `duplicateOrRenameCurrentLayout` (17716), `combineLinkedPages` (17497), `pagesLinkedFrom` (17596),
   `fillCombinedPage` (17642), `restoreLayoutTitles`/`saveLayoutTitles` (14754/14795),
   `buildDiagramExportMenu`/`exportDiagram` (5948/5995). **~1,500 lines of file and zip work wrapped in
   dialogs.** Extractable behind an interface that takes a parent `Component` — which is also the
   change that would make DD-C7 tractable.
4. **The station-label text.** `facingArrow` (2988), `crowdedLabel` (3024), `facingArrowOf` (3066),
   `updateStationLabels` (3091), `autonomyLocomotiveAt` (2885), `autonomyStationNameAt` (2904).
   Pure text-from-model, ~300 lines, unit-testable — and the same extraction DD-B4 asks for.
5. **The preference keys.** ~45 `String` constants (178–222). Trivially movable; low value, since every
   reader already says `TrainControlUI.NAME`.

**Not liftable, and worth saying so:**

- Everything between 12528 and ~15750 that sits inside a `GEN-FIRST`/`GEN-LAST` pair. The *bodies* are
  hand-written and editable, but the methods cannot move: NetBeans owns their registration.
- The ~120 component fields declared by `initComponents`, and every method that reads one directly —
  which, in a 19,000-line form-backed class, is most of the autonomy glue (1807–4050: `mountAutonomyMenu`,
  `ensureDiagramStrip`, `refreshAutonomyPrompt`, `setAutonomyDependentTabs`, the banner and the overlay
  toggle). Those manipulate tabs and panels the form created and would have to take a dozen components
  as constructor arguments to move, which is worse than leaving them.
- `syncWithCS2` (5904) and `layoutEditingComplete` (15746–15895). Both coordinate threads, locks and
  repaints across the whole window; `README.md`'s own note about taking the CS2 sync off the EDT is the
  record of how much implicit serialisation lives there.

---

### DD-C2 — one image cache, two key namespaces, no eviction

**Where.** `gui/TrainControlUI.java:432` (`private static final ConcurrentHashMap<String, Image>`),
exposed at 4040; written from `getLocImage` (5650), `getLocImageMaxHeight` (5680),
`LayoutLabel.setImage` (651–677) and `LayoutLabel.setImageOnEDT` (730–751).

Three problems, all small:

1. **Two key schemes share one namespace.** Tile images are keyed by
   `component.getImageKey(size, edit)`; locomotive images by `url + size`. No prefix discipline.
2. **Two protocols inside one file.** `LayoutLabel.setImage` uses `get()==null` then `putIfAbsent`;
   `setImageOnEDT` uses `containsKey` then `put`. Only the first participates in the decode counting the
   spinner depends on (666, 690).
3. **`getLocImageMaxHeight` computes its key before clamping.** Line 5680 builds
   `key = url + size`; line 5693 then reassigns `size`, and 5696 stores the *clamped* image under the
   *unclamped* key — the same key `getLocImage` uses for the unclamped one.

**Reachability of (3), checked.** A parallel reading flagged this as a live bug. It is not, today.
`getLocImageMaxHeight` is called with `LOC_ICON_WIDTH` (296) only; `getLocImage` with 34, 66 and 142.
The keys never collide. It is a trap for the next caller — see DD-D7, where the severity correction is
recorded.

Nothing ever clears the map. `LayoutLabel.java:656–666` notes that on a running layout every switch
thrown is a fresh key and another decode; all of those land in a map that never gives anything back.

**What I would do.** Two maps instead of one namespace, and put `maxHeight` in the key. **~10 lines.**
Best done as part of DD-C1's first lift.

---

### DD-C3 — three of the store's translate helpers exist only because a generic was not used

**Where.** `AutonomyCompanionStore.java:2405–2551`, `2686–2696`.

Nine helpers. The comment at 1887–1891 gives the house reason — *"erasure makes both signatures the same
method, so a list-valued copy of any of these helpers needs its own name whether or not that reads
better"* — and for `moveListValues` it is correct. It has been over-applied here:

- **`untranslatePortals()` (2503–2514) is character-for-character `untranslateTileMap(portals)`**
  (2433–2444). Not erasure; just a copy. Delete it.
- **`translateLengths()` (2541–2551) is `translateKeys(tileLengths, true)`** with `Integer` values.
  `translateKeys` is only ever called with `String` values (785, 788, 789, 793), so making it
  `<T> Map<String,T> translateKeys(Map<String,T>, boolean)` removes `translateLengths` with no clash —
  there would be only one method of that name.
- **`untranslate(Map<String,String>)` (2417) can be `<T> void untranslate(Map<String,T>)`** for the same
  reason. It does not clash with `untranslateTileMap`, which translates values too and has its own name.

**~40 lines removed, no behaviour change, very low risk.** Worth doing on its own, and worth doing
*before* DD-A1 so the registry has fewer shapes to model.

---

### DD-C4 — dead code left by de-duplications that stopped halfway

- `RouteEditorFrame.isSignalAt` (868–893) — no callers anywhere; a duplicate of `kindAtAddress`
  (809–829) left behind by `ef33f4a8`.
- `base/ConditionRows.java` (193 lines) — the superseded flat-list predecessor of `ConditionOutline`
  (424 lines). Still imported by `RouteEditorFrame:28` (unused) and still carrying
  `test/testConditionRows.java`, which is on DD-A2's not-run list.
- `GraphLocAssign`'s `newOnly` parameter — two call sites, **both `false`**. Its only `true` caller was
  `GraphRightClickPointMenu:106`, deleted in `d8db4879`. A dead flag guarding a dead branch.
- `HomeLocomotiveMenu`'s four unreachable entry points — see DD-A6, where the decision is the finding
  rather than the deletion.
- `AutonomyCompanionStore.forgetSquares:1755` — `tileDirections.remove(key)` cannot match a suffixed
  key; the loop at 1773–1779 does the work.
- `.gitignore` names `autonomy-derived.json` as *"Generated by testAutonomyFromDiagram for inspection"*
  — a class renamed away in `7bcdf584`.

---

### DD-C5 — five copies of the popup-trigger adapter, and two conventions

`RightClickFunctionMenu`, `RightClickMenuListener`, `RightClickPageMenu`, `RightClickRouteMenu` and
`RightClickTimetableMenu` each carry the same `mousePressed`/`mouseReleased`/`isPopupTrigger`/`showPopup`
triple over a nested `JPopupMenu` subclass. `RightClickSelectorMenu` uses a different shape entirely —
`extends JPopupMenu`, constructed by the caller, triggered by `SwingUtilities.isRightMouseButton`.

Two shapes and two triggers for one gesture. The four diagram sites use a third
(`e.getButton() == BUTTON3`, DD-B5). `isPopupTrigger` is the portable one.

**A `PopupOnRightClick extends MouseAdapter` base with one abstract `JPopupMenu build(MouseEvent)`
removes ~50 lines and one platform difference. Low value, near-zero risk.**

---

### DD-C6 — the store's eleven collections are declared in six places

`AutonomyCompanionStore.java`: 74–77, 91, 172, 187, 300–302, 669. Each sits next to its own accessors,
which is a reasonable local decision and a bad global one — you cannot see the eleven at a glance, which
is the first thing anybody adding a twelfth needs to do. DD-A1's registry fixes this as a side effect;
if DD-A1 is not done, moving the declarations together and leaving the accessors where they are costs
nothing.

---

### DD-C7 — the dialog parent is named five different ways

`grep` over `src/org/traincontrol/gui/`: **~290 `JOptionPane.show*` calls**, 153 of them in
`TrainControlUI` alone. The parent argument is written as `this` (105), `ui` (30), `owner` (26),
`source` (1), and on the following line (125 — mostly `null`).

`2026-08-19-ui-consistency-proposal.md` already counts these from the *language* angle (which buttons
follow which locale) and that finding is not repeated here. The duplication angle is different and has
already cost something: **AR-1** — "Place Locomotive" and "Edit Locomotive" opening a popup whose entire
content was the word `null` — was a parent-window walk that could not arrive
(`AutonomyEditorPanel.parentWindow()` walked up the window ancestry, and a `JFrame` has no owner) landing
in a handler that showed `e.getMessage()`. Both halves were fixed in one place. The idiom
`SwingUtilities.getWindowAncestor(...)` appears at four more sites with four different fallbacks.

**A `Dialogs.error(Component near, String bundleKey, Object... args)` that resolves the parent by walking
up and falling back to the known main window would have made AR-1 impossible.** ~290 call sites is too
many to sweep in one go; do it opportunistically, starting with the menu classes as part of DD-B7.

---

### DD-C8 — a 426-line keyboard dispatcher as an if/else chain

`TrainControlUI.LocControlPanelKeyPressed`, 12533–12959. Six `Alt` shortcuts and twelve `Ctrl` shortcuts
in one `if / else if` chain inside a `GEN-FIRST`/`GEN-LAST` pair.

The method cannot move — NetBeans owns the registration — but **the body is hand-written and editable**,
so it can be one line delegating to a `KeyboardShortcuts` class holding a table of
`(modifier, keyCode) -> Runnable`. That would also make the set of shortcuts *enumerable*, which is
worth something: today the only way to find out what `Ctrl+M` does is to read 426 lines.

**~450 lines moved, low risk** (the chain is order-independent except for the diagram's first refusal at
12542, which stays first). I checked whether the shortcut list is also stated in the form as text, which
would make this a real drift finding — it is not; the hint panels are panels of buttons with tooltips.
So this is tidiness, not correctness. **C.**

---

### DD-C9 — `sideTowards` and `sideToward`, in the same class

**Where.** `automationui/TileGraph.java:109–128` (static, geometric: compares coordinates) and
`:1035–1047` (instance, iterates `Side.values()` calling `neighbour()`).

Two methods in one class whose names differ by a single trailing `s`, answering "which side of `from`
does `to` lie beyond?" two different ways. Both are in use — `DiagramMonitor:377` and
`AutonomyEditorPanel:3772` take the static one; `AutonomySession:3102` and `:3189` take the instance one.

They are not equivalent: the instance version goes through `neighbour()`, and its own comment (1044–1046)
notes that a portal's partner is *"a neighbour reached through no side at all"*. Any code holding a
`TileGraph` compiles against either.

`README.md`'s own rule applies here — *"when a claim rests on a method whose name reads like another
method's, open it"*. This is that hazard installed permanently, in one file. **Rename one of them
(`sideByGeometry` / `sideByNeighbour`) — no behaviour change, near-zero risk.**

Same family, cheaper: `GraphReducer.onwardSides` and `AutonomyBuilder.onwardFrom` (DD-B9);
`TileGraph.validatePortals:760–771` and `GraphReducer.noteOnce:762–771`, which de-duplicate problems
with the same loop, the second citing the first in its javadoc; and `AutonomyBuilder`'s `tilesByName`,
`baseNames` and `facingByName` (1042–1108), three copies of one loop differing only in the value put.

---

### DD-C10 — the port table exists three times, one of them in Python

**Where.** `automationui/TilePorts.java:239–344` (the static block);
`base/LayoutDiagramComponent.getNumOrientations:1046–1064`, copied verbatim into
`TilePorts.numOrientations:538–552`; and `docs/plans/portmap-verification.py:9–37`.

`TilePorts`'s javadoc (31–33) instructs the reader to keep the Python copy in step by hand. That copy
has already gone stale in its own notes — it still marks the LINK side "UNCONFIRMED" after `d4d5b7ba`
confirmed it.

**What I would do.** Make `TilePorts.numOrientations` delegate to a public static on
`LayoutDiagramComponent` — **~10 lines, near-zero risk**. The Python script is a different question: a
verification script that must be hand-synchronised with the thing it verifies is not a verification, it
is a second opinion with the same author. Either generate its table from the Java (dump `TilePorts` to
JSON in a debug build and have the script read that), or retire it and move its assertions into
`testAutonomyDiagramPorts`. The second is probably right.

---

### DD-C11 — the trace loops re-derive what the reducer already recorded

**Where.** `automationui/DiagramMonitor.java:370–388` (`lay`, emitting `TileOverlay.Segment`) and
`gui/AutonomyEditorPanel.java:3740–3760` (a near-copy emitting `TileAnnotation.Trace`).

Both recover a tile's entry and exit sides **geometrically**, from the coordinates of the neighbouring
tiles in the path — discarding the `RouteId` that `GraphReducer.TileStep` already recorded and the
`exitSide`/`entrySide` that `ReducedEdge` already carries.

On an overpass or a double curve, geometry cannot say *which of the two routes through the square* was
used; the reducer can. So both drawings can put the line on the wrong arm of a square that has two, and
they will do it identically, because one is a copy of the other. `TileOverlay.Segment` and
`TileAnnotation.Trace` are the same three fields.

**~30 lines to pass the `RouteId` through instead. Medium risk** — it touches drawing on both the
running monitor and the editor, so it wants a look at a real double curve before and after. Low
priority: today it is a cosmetic wrongness on an uncommon tile.

Smaller neighbours in the same files: `TileAnnotation.trackBends:1459–1470` and `trackCentre:1472–1489`
open with an identical four-line `sideA`/`sideB` expression, and `trackBends` open-codes
`Side.opposite()` as `Math.abs(ordinal diff) != 2`.

---

## D — considered and rejected

**This section is the point of the exercise.** Two of these are corrections to claims made earlier in
this same pass.

| # | | Status |
|---|---|---|
| DD-D1 | The icon → annotation → overlay pipeline is not duplication | Recorded |
| DD-D2 | The two tables' row storage, add, remove and move are genuinely different | Recorded |
| DD-D3 | `Ctrl+X`/`Ctrl+V` on the diagram: two meanings, deliberately | Recorded |
| DD-D4 | `LayoutEditorRightclickMenu` vs `LayoutRightclickAutonomyMenu`: different domains | Recorded |
| DD-D5 | **Correction:** `AutonomyMenu` and `AutonomyViewerPanel` already share their actions | Recorded |
| DD-D6 | The four grid sizing policies encode four requirements | Recorded |
| DD-D7 | **Correction:** the `getLocImageMaxHeight` key collision is not reachable today | Severity revised B → C |
| DD-D7a | **Downgrade:** the two unguarded `item()` copies cannot throw today, because their actions catch for themselves | Severity revised A → B (DD-B7) |
| DD-D8 | The two test `feedback()` helpers must stay two | Recorded |
| DD-D9 | `reconcile` and `applyTo` must stay hand-written even if DD-A1 lands | Recorded |
| DD-D10 | The overlay stroke formulas: extract the constants, not the loops | Recorded |
| DD-D11 | Repeated *reasoning* is house style and is not duplication | Recorded |
| DD-D12 | `exits()` vs `continuations()`, and four more pairs in the derivation chain that must stay two | Recorded |
| DD-D13 | Not covered by this pass | Recorded |

---

**DD-D1 — `LayoutLabel.paintComponent` and the overlay `paint` methods are a pipeline, not copies.**
`LayoutLabel.paintComponent` (952–982) contains no drawing primitive of its own: it calls
`super.paintComponent`, then delegates to `TileAnnotation.paint` and `TileOverlay.paint` on a scratch
`Graphics2D`. Icon selection lives once, in `base/LayoutDiagramComponent.getImageName/getImage/getImageKey`
(327–410, 730–733). The overlays load no images and hold no cache; they are immutable value objects
whose `equals`/`hashCode` exist purely to suppress redundant repaints. `LayoutLabel.java:82–94` gives
three concrete reasons the overlays must stay out of the icon: the icon cache is shared per tile *type*,
so recolouring one recolours all; `updateImage` only refreshes on an icon-*name* change, which autonomy
state never produces; and the flash highlight already owns the single `setIcon`/`lastIcon` slot. Baking
overlays into the icon would break the flash. **Do not merge.**

**DD-D2 — the commands and conditions tables are genuinely two things below the cell layer.** Having
argued in DD-A4 that their *cell semantics* should be one thing, it matters to say precisely where that
stops:

- **`shift()`** (2987 vs 3446). The conditions version must *not* move the line.
  `beee4d3a` *"Route editor: moving a condition kept deleting the word beside it"* is the record of
  treating them alike: moving the row put a condition next to the joiner above it, `tidy()` swept the
  orphaned word, and three conditions joined by two ANDs came back joined by one — *a change to when the
  route fires, made by a button that says nothing about firing*.
- **`removeAt()`** (2970 vs 3407). Conditions must remove the paired joiner and re-`tidy()`.
- **`addRow()` defaults** (2961 vs 3389). Commands may hold a blank target because they store a
  `CommandRow`; conditions may not, because they store a built `RouteCommand` that would throw.
- **Row storage.** `Entry` (2478) exists to keep unknown commands *in order* with editable ones — the fix
  from `a24361d3`. `ConditionOutline.Row` exists to give joiners a depth of their own — from `7ecbce11`.
- **Kind vocabularies.** Merging `canBeACommand` and `canBeACondition` re-introduces the defect
  `base/CommandRow.java:242–255` describes: a condition built from an unsupported kind is permanently
  false and the route silently stops firing.
- **Persistence.** Commands become a `List<RouteCommand>`; conditions become a `NodeExpression` tree.
- **The columns that exist in only one.** A command list is a sequence and a condition list is a tree; a
  condition has no delay because it is not timed.

**DD-D3 — `Ctrl+X` / `Ctrl+V` on the diagram mean two different things on purpose.**
`TrainControlUI.java:3416–3423` cuts and pastes a *locomotive* between stations; `LayoutEditor:4675–4700`
cuts and pastes a *tile*. Same keys, same visual surface, gated on which window has focus. A shared
handler would have to re-derive the mode and would be strictly worse than two handlers.

**DD-D4 — the two tile right-click menus are different domains.** One edits the drawing
(cut/copy/rotate/paste); the other operates the railway (start/stop autonomy, place locomotives).
Merging them would put "throw this signal" and "delete this tile" on one menu. They should share the
*helpers* from DD-B7 and nothing else. The same goes for `AutonomyMenu` (the menu bar), which is about
the setup as a whole — configurations, pages, import/export — and shares no item with the tile menus.

**DD-D5 — correction: `AutonomyMenu` and `AutonomyViewerPanel` already share their action bodies.**
A parallel reading reported these as two copy-pasted implementations of New/Rename/Delete
Configuration, Import and Export. Checked, and it is wrong: `AutonomyMenu.actions()` (137) returns the
`AutonomyViewerPanel`, and every item calls through it (`actions.duplicate()`, `actions.rename()`, …).
**Only the item list is duplicated, not the behaviour** — and that is the good pattern, worth copying
rather than fixing.

What *is* duplicated between them is the enablement policy: `AutonomyMenu` greys everything above Import
when nothing is loaded (446–457), and the viewer's `manageMenu()` (406–445) greys nothing. Checked the
consequence too, and it is cosmetic — `rename()` and `delete()` guard internally on `selected() == null`.
The one place where the divergence *was* real is already fixed, and the comment recording it
(`AutonomyViewerPanel:433–437`) is the cleanest statement of this document's thesis anywhere in the
repository:

> *"only when there is a derived graph to write. … offering it in either state hands somebody a file
> that describes nothing, or throws on the way to writing one. **The Autonomy menu has always refused in
> both states; this copy of the same action did not.**"*

**DD-D6 — the four grid sizing policies look like four copies and are four requirements.** The main
window is inside a scroll pane and must not fix a size; the editor adds one tile-height of slack so the
last row is not clipped (`LayoutEditor:3577–3587`); the popup adds 100px of chrome; the export takes the
max against the container's preferred size because an unrealised component has none. **Unify the call
(DD-B3), not the numbers.**

Similarly, `LayoutGrid`'s spinner/reveal lifecycle and `DiagramExport.awaitTiles` both wait on
`whenTilesSettled`, but one reveals a live component and the other blocks a worker with a
`CountDownLatch` before an offscreen `paint`. `DiagramExport.java:94–99` throws if called on the EDT
precisely because the two cannot be the same mechanism.

**DD-D7 — correction: the `getLocImageMaxHeight` cache-key collision is not reachable.** Reported to me
as a live bug worth fixing first. The code *is* wrong — the key is computed before the clamp — but the
callers make it unreachable: `getLocImageMaxHeight` is only ever called with `LOC_ICON_WIDTH` (296)
and `getLocImage` with 34, 66 and 142, so the two never share a key. **Severity revised from B to C**
(DD-C2), as a trap for the next caller rather than a defect. Recording the revision because the original
claim is the kind that reads convincingly and would have been shipped as a changelog entry.

**DD-D7a — downgrade: the two unguarded `item()` copies cannot throw today.** Reported to me as three
copies of one helper where two would let an exception escape onto the EDT and silently do nothing.
Structurally true — `AutonomyMenu:620–626` and `AutonomyViewerPanel:446–451` have no `try` — but I
traced the actions and every one of them catches for itself (`importConfiguration` catches
`IOException | RuntimeException` at `AutonomyViewerPanel:1034–1039`; `rename` and `delete` guard on
`selected() == null`). **Severity revised from A to B** and recorded as DD-B7, a trap for the next
caller. `README.md`'s rule applies: *"'Wrong code' is not 'wrong behaviour'. Trace whether any caller
can actually reach the defect before assigning severity."*

**DD-D8 — the two `feedback()` test helpers must stay two.** `testAutonomyDiagramReducer:575` and
`testAutonomyDiagramReversal:652` pass `(raw/2, raw)`; `testAutonomyDiagramTiles:687` passes `(a, a)`.
The Reducer copy's javadoc says the split is deliberate: *"The logical address is deliberately different
here (CS2File halves the raw value), so a Point built from the wrong one shows up immediately rather
than agreeing by coincidence."* Flattening them into one helper would either destroy that discrimination
or invalidate the Tiles assertions. **Two names in the harness, both documented** (DD-B6).

**DD-D9 — `reconcile` and `applyTo` stay hand-written even if DD-A1's registry lands.** They are policy,
not bookkeeping, and each of their per-collection branches has a stated reason: a name has a different
lifetime from a length; a half-pairing is worse than none; a direction naming a route the tile no longer
has is simply not applied. A registry hook per collection would be more code and less clarity.
`excludedPages` stays outside the registry for the same kind of reason — it is keyed by page.

**DD-D10 — the overlay stroke formulas: extract the constants, not the loops.** `TileOverlay.paintRun`
and `TileAnnotation.paintTraces` share identical stroke formulas (`max(3f, span/7f)` at
`TileOverlay:388` / `TileAnnotation:1002`; `max(2f, span/14f)` at `:432` / `:1071`), identical span and
centre setup, and near-identical prose. The extraction was started — `chevron()` and `midpoint()` are
already shared. But they paint from different colour sources and `paintTraces` adds a per-index nudge.
**Name the two stroke constants so they cannot drift; leave the loops alone.** Most of the benefit for a
tenth of the risk.

**DD-D11 — repeated *reasoning* is not repeated code.** Several places state the same rule in two
comments — `TrainControlUI:1000–1001` says `DiagramTileRegistry` carries the same rule "for exactly the
same reason"; `renamePage` and `moveTiles` each explain the configurations' `points` object. That is the
house style working as intended, and it is what let this pass reconstruct the history of DD-A1 and DD-A5
without guessing. **Nothing here proposes removing a comment.** Where a rule is stated twice and
*implemented* twice, the finding is about the implementation.

**DD-D12 — five pairs in the derivation chain that look like duplicates and must stay two.** Having
argued in DD-A7 and DD-B9 that the chain says several things twice, these are the ones that should:

- **`TileGraph.exits()` vs `continuations()`** (621, 1142–1200). Directed versus deliberately
  undirected — one answers "where may a train go", the other "where does the track go as drawn", and
  `setOneWayRun` needs the second. **But the hardware restriction should be shared between them:**
  `route.isTraversableFrom` is consulted at `TileGraph:621` and nowhere else, so tracing a run through a
  `CUSTOM_PERM_*` turnout succeeds in the facing direction and `applyOneWay`
  (`AutonomySession:3092–3118`) then writes a direction onto a tile whose blades oppose it.
  `TileGraph.defaultDirection`'s comment (520–524) warns that ANDing those *"leaves the tile impassable
  in both directions"*. `AutonomyEditorPanel:3940–3944` re-derives the same restriction a third time,
  for drawing. **That is a C-sized fix inside a D-sized rejection: share the restriction, keep the two
  traversals.**
- **`TileAnnotation` vs `TileOverlay`.** Documented at `TileAnnotation.java:17–20`. Merging would put
  running state and an editing decision inside one `equals`, and both exist *only* so that equality can
  suppress a repaint. See also DD-D1.
- **`TileAnnotation.Mark` vs `TilePorts.Route`.** A view type mirroring a model type, which is what
  keeps Swing out of the model.
- **`StationIndex.withoutArrivalSuffix`** (207–230) as a *fallback*. It hard-codes
  `{northbound, southbound, eastbound, westbound}` as the string inverse of `AutonomyBuilder.heading`
  (707–716), which looks like duplication and is not: the index is legitimately stale for a
  configuration built by an older run, and a textual fallback is the only thing that works then. Its
  four heading words should come from `AutonomyBuilder` rather than be typed again — that part is a
  one-line fix.
- **`TileGraph.Problem` vs `AutonomyChecks.Finding`.** Different severity vocabularies on purpose.

Recording also the one place the project has already done this work and what it learned:
`2c0eb750` *"Give the split copies one translation layer, and fix what their absence broke"* —
*"`pointNameForTile` built with no split settings at all, `tileForPointName` built with them,
`facingsFor` built with a third combination. **They were answers to different railways, and each was
confident.**"* Four hand-assembled builder configurations became `AutonomySession.builder()`
(1658–1667). That is the shape every proposal in this document is trying to reach.

**DD-D13 — not covered by this pass.** `automation/Layout.java` (6,381 lines) and duplication between
`base/` and `marklin/` were commissioned as stretch scope and are **not answered here** — the reading
was still running when this document was written. Three questions are recorded so they are not lost:

1. How much of `automation/HomeStaging.java` (1,080 lines) duplicates `Layout`'s path selection, and
   whether a change has ever had to be made in both.
2. Whether any repeated block inside `Layout.java` — the lock/unlock sequences, the "route to
   destination" versus "return home" paths — has already drifted.
3. For each `base/`↔`marklin/` pair (`Locomotive`/`MarklinLocomotive`, `Accessory`/`MarklinAccessory`,
   `Route`/`MarklinRoute`, `Feedback`/`MarklinFeedback`), whether the subclass *repeats* the parent's
   work rather than extending it, and whether parse/serialise logic appears in both `base/` and
   `marklin/file/CS2File.java`. Note that DD-A5 already found three copies of one loop living in
   `marklin/`, which suggests this is worth someone's afternoon.

---

## What this pass did not look at

Named so the next reviewer knows what is uncovered rather than clean:

- **Correctness.** Deliberately left to `IR` and `FR`. Where a live defect appears above it was found
  while reading for shape, not searched for.
- **`automation/Layout.java` and `base/` vs `marklin/`** — see DD-D13. The
  `TileGraph → GraphReducer → AutonomyBuilder` chain *was* covered: DD-A7, DD-B9, DD-C9, DD-C10, DD-C11
  and DD-D12.
- **`MarklinControlStation` (3,719 lines) and `marklin/file/CS2File.java` (2,489 lines).** Both are large
  enough to have the same problem as the store and neither was opened.
- **`AutonomyEditorPanel` (4,733 lines) as a whole.** Only its menu-building half was read.
- **The eight message bundles.** `TR-D7` checked them on 2026-08-21 and found them clean; not re-checked.
- **Anything requiring the railway or a display.** Nothing was run. Several findings above
  (DD-B1, DD-B5, DD-A3) would be settled in one launch by somebody with the window open, and DD-A3 and
  DD-A4 should get `MT-###` entries in `docs/manual-tests/tests.md` if they are acted on.
