# A fourth pass over the eight days, entered through the page index and the page list

**Prefix for citing these findings elsewhere:** `N8`

**Status:** open

Reviewed 2026-09-10 on branch `autonomy-diagram-r0`. The brief named `d86914a9` plus twenty
uncommitted files; **those files were committed while this pass was running**, as `a281e3a2` ("The
validation of the independent pass, worked: an id two pages both claim is given to neither, and a
delay the railway will not wait is no longer the number the editor shows"). Everything measured below
is against `a281e3a2` with `src/` and `test/` clean, which is the same code the brief described. See
`N8-D6` for what moved and what it cost.

Scope: the eight-day window of `git log --since=2026-09-02`, weighted on the round that was
uncommitted when this began — `AutonomyCompanionStore`, `AutonomySession`, `LayoutDiagram`,
`LayoutEditor`, `RouteEditorFrame`, `TrainControlUI`, `MarklinLocomotive`, `behaviour.md` and the five
test files — plus `docs/manual-tests/`, which three passes in a row have declined.

`E8`, `E8V`, `X8` and `X8V` were read in full first and nothing below re-files them. `N8` was checked
against `docs/reviews/`, `docs/reviews-2026-09-09/`, `docs/reviews-2026-09-10/`, `src/`, `test/`,
`docs/manual-tests/findings.tsv` and `triage.db` before it was chosen; it collides with nothing. Live
prefixes in this folder are `E8`, `E8V`, `X8`, `X8V` and `S14`.

---

## Method

**What was executed.** Seven runs through `docs/tools/one.sh`, all with `TC_SCRATCH` in this session's
scratch directory. Baseline first, at `a281e3a2` with nothing mutated:

```
--- core.testParseCS2Layout                     Total tests run: 27, Failures: 0, Skips: 0
--- core.testRoutes                             Total tests run: 26, Failures: 0, Skips: 0
--- regression.testDeleteForgetsTheWholeSquare  Total tests run:  2, Failures: 0, Skips: 0
--- regression.testTheDiagramCeiling            Total tests run:  3, Failures: 0, Skips: 0
--- regression.testTheMenusComeBackAtOneMoment  Total tests run:  2, Failures: 0, Skips: 0
--- regression.testEveryTestIsInTheBattery      Total tests run:  5, Failures: 0, Skips: 0
--- regression.testEveryCitationResolves        Total tests run:  3, Failures: 0, Skips: 0
```

**Four mutations, each restored from a copy taken immediately before the edit.**

| # | mutation | run | result |
|---|---|---|---|
| 1 | `LayoutDiagram.attribute`'s withdrawal removed — the pre-`X8V-B1` last-wins map | `core.testParseCS2Layout` | 1 red: `testTwoPagesClaimingOneIdKeepNeithersKeys`. The withdrawal is genuinely pinned |
| 2 | `AutonomySession.java:2112` back to an unconditional `touched()` | probe, `regression.testDeleteForgetsTheWholeSquare` | the probe's graph answer flips from `false` to `true` (`N8-B2`); the test class stays **green**, so nothing pins the rebuild (`N8-D5`) |
| 3 | `roomToGrow` charging each increment against the other dimension | `regression.testTheDiagramCeiling` | 1 red, as that class's own MUTATION note says |
| 4 | `delayTheRailwayWillUse` returning `asked` unchanged | `core.testRoutes` | 1 red, as that test's MUTATION note says |

**One printing probe was written, run five times and deleted.** `test/regression/n8Probe.java` — no
assertions. It copied `Oles kreds` to a temporary folder and round-tripped its index; built a synthetic
index of the same shape; and opened `test/layouts/live-snapshot` through `support.LayoutSandbox` and
`support.Scenario` to measure page links and the tile graph. Deleted after the last run, because a
class in `test/` with no `build.xml` entry reddens `regression.testEveryTestIsInTheBattery` — which was
then re-run green. Its output is quoted under the findings that used it.

**`cs2_sample_layout/` was read and never written.** `one.sh`'s before-and-after fingerprint did not
fire on any of the seven runs. The three files `git status` shows modified there are Adam's running
application's; they were modified before this pass began and nothing here touched them.

**The battery was not run**, on the brief's instruction. `X8V-D12` measured it green at `d86914a9`;
`a281e3a2` is not covered by anybody's battery run.

**What was read and not executed.** The whole of `a281e3a2`'s diff; `LayoutDiagram`'s three readers of
`gleisbild.cs2` and `writeLayoutIndex`; every `Kept.forget` override and every caller of
`AutonomySession.moveTiles`/`forgetTiles`; `LayoutEditor`'s rotate, shift and grow gestures and its key
handler; `RouteEditorFrame`'s delay column and `MarklinRoute.execRoute`'s sleep;
`TrainControlUI.combineLinkedPages`, `pagesLinkedFrom`, `fillCombinedPage`, `goToLayoutPage` and the
Manage Pages menu; `MarklinLocomotive.isSimultaneousMultiUnitCompatible` and its one caller;
`TilePorts` and `AutonomySession.getBarredArrivals`; `docs/manual-tests/triagedb.py` and the catalogue
it renders. Each finding says which of the two it rests on.

---

## A — high

| id | what | disposition |
|---|---|---|
| N8-A1 | a page link's stored address is a POSITION in the name-sorted page list, so every Manage Pages operation silently re-aims every arrow on the layout | open |

### N8-A1 — adding, renaming, duplicating or deleting a page repoints every page-link arrow, and the paragraph added yesterday says the ordering decides nothing

**What is wrong.** A `pfeil` tile stores an index, and the index is resolved against
`MarklinControlStation.getLayoutList()`, which sorts by name (`MarklinControlStation.java:3638-3644`).
So the number written in the file means "the Nth page in the alphabet", and the alphabet moves whenever
the set of names does.

- `LayoutLabel.java:343` and `:347` — clicking the arrow calls `goToLayoutPage(component.getRawAddress())`.
- `TrainControlUI.goToLayoutPage` (`:18622`) — `this.LayoutList.setSelectedIndex(index)`, and
  `LayoutList` is filled from `getLayoutList()` (`TrainControlUI.java:8494`, `:9547`, `:22396`).
- `TrainControlUI.pagesLinkedFrom` (`:24902-24927`) — `all.get(index)`, same list, and this is what
  Combine Linked Pages walks.
- `LayoutDiagramComponent.java:886` — the index is written straight back out as ` .artikel=N`, so the
  stale meaning is committed to the file.
- `LayoutDiagramComponent.java:716` — the tooltip is `I18n.f("layout.linkPage", getRawAddress() + 1)`,
  "Link to page 3". The interface presents the link as a position, which is exactly what it is.

Nothing re-aims a link when the page list changes. `grep -rn "isLink()" src/` returns fifteen sites and
none of them is in a rename, add, duplicate or delete path, and none of the four operations warns.

**How I know.** Executed, on `test/layouts/live-snapshot` — a frozen copy of Adam's own railway, whose
five pages and three arrows on `1 - Main` are byte-for-byte the ones in `cs2_sample_layout` today.

Adding a page, using the default name **Combine linked pages** itself offers
(`layout.ui.combinedPageName={0} and neighbours`, `messages.properties:1115`):

```
N8 pages = [1 - Main, 2 - Bottom, 3 - Top Parking, 4 - Combined, 5 - Test]
N8 LINK BEFORE 1 - Main (0,0)  index=2 -> 3 - Top Parking
N8 LINK BEFORE 1 - Main (15,5) index=1 -> 2 - Bottom
N8 LINK BEFORE 1 - Main (4,6)  index=2 -> 3 - Top Parking
N8 LINK BEFORE 4 - Combined (22,5) index=1 -> 2 - Bottom

N8 pages after the add = [1 - Main, 1 - Main and neighbours, 2 - Bottom, 3 - Top Parking, 4 - Combined, 5 - Test]
N8 LINK AFTER-ADD 1 - Main (0,0)  index=2 -> 2 - Bottom
N8 LINK AFTER-ADD 1 - Main (15,5) index=1 -> 1 - Main and neighbours
N8 LINK AFTER-ADD 1 - Main (4,6)  index=2 -> 2 - Bottom
N8 LINK AFTER-ADD 4 - Combined (22,5) index=1 -> 1 - Main and neighbours
```

Four of the seven arrows on the layout now lead somewhere else. Renaming one page is the same and
worse, because a rename can move a page anywhere in the order — `2 - Bottom` renamed to `9 - Bottom`:

```
N8 pages after the rename = [1 - Main, 3 - Top Parking, 4 - Combined, 5 - Test, 9 - Bottom]
N8 LINK RENAME-AFTER 1 - Main (0,0)  index=2 -> 4 - Combined
N8 LINK RENAME-AFTER 1 - Main (15,5) index=1 -> 3 - Top Parking
N8 LINK RENAME-AFTER 1 - Main (4,6)  index=2 -> 4 - Combined
N8 LINK RENAME-AFTER 4 - Combined (22,5) index=1 -> 3 - Top Parking
```

Bottom is now reachable from no arrow at all, and the two arrows that pointed at Top Parking point at
the combined page. Duplicate Current Page has the same shape by construction — it names the copy
`"<name> copy"` (`TrainControlUI.java:24726`), which sorts immediately after the original — and so does
Delete. Four of the five items on Manage Pages, and Combine is the fifth.

**And `behaviour.md` now says the opposite, in a paragraph written yesterday.** `behaviour.md:1105-1113`,
added in `a281e3a2`:

> **Pages are ordered by name, not by the number the Central Station orders them with.** … `getLayoutList`
> sorts, and that sort is what the window and every menu show. … it is not a position here. … if a
> reissued id later disagrees with it, **neither number decides anything TrainControl does**.

The ruling it records is Adam's and is about page *ids*, and about ids it is right. The sentence as
written is wider than the ruling: a page's position in that sorted list is what every arrow on the
diagram is stored as, so the ordering decides where four of seven arrows go. `behaviour.md` does not
mention page links anywhere — `grep -n -i "link" docs/reference/behaviour.md` returns nothing.

**Why this is A.** The diagram is the layout in this program's vocabulary, the operation that breaks it
is the most ordinary thing on the Layouts menu, the breakage is silent, it is written to the file, and
the only repair is re-aiming each arrow by hand through `LayoutEditorAddressPopup`. It is the same
shape as MT-135 — a rename reattaching settings to the wrong page — in the one place that rename did
not reach. No locomotive moves, so Adam may want it at B; I have graded it on "wrong behaviour on the
layout" rather than on danger.

**What I would change.** Store what a link points at by page ID rather than by position, and translate
to a position only at the moment the file is written — the id is already a page's identity
(`writeLayoutIndex`'s own comment says so) and it already survives a rename. That is a format change
and needs Adam. The smaller answer, if the format must stay: re-aim every link in
`duplicateOrRenameCurrentLayout`, the delete path and `combineLinkedPages`, by computing each page's old
and new index and rewriting the `pfeil` tiles — which is one loop and the thing `writeLayoutIndex`'s
`renamedFromTo` map already carries the information for. Either way `behaviour.md` §8 needs the
sentence narrowed to ids, and a line saying what a page link is stored as.

---

## B — medium

| id | what | disposition |
|---|---|---|
| N8-B1 | the duplicate-id withdrawal deletes scroll offsets the file attributes unambiguously, and on the genuine export the code it replaced put them on the right page | open |
| N8-B2 | making the graph rebuild conditional on the STORE removed it from four callers whose DIAGRAM changed, which is the case the comment above it says it is owed for | open |
| N8-B3 | the finding catalogue holds fifteen findings `X8V` never made, four of them at severity A, and one real disposition overwritten — and the guard that calls it "current" checks only that three files exist | open |

### N8-B1 — `0 stationer` loses the scroll position the station wrote inside it, and it did not before

**What is wrong.** `LayoutDiagram.attribute` (`LayoutDiagram.java:1138-1156`) withdraws an id that two
pages claim, so neither page's unmodelled keys are kept:

```java
if (!claimed.add(id))
{
    out.remove(id);

    return;
}
```

`X8V-B1` asked for the withdrawal at the WRITE — *"Have `writeLayoutIndex` drop the extras of an id it
has just reissued: one line inside the `while (!issued.add(id))` loop"* — which loses only the page that
loses the argument. Done at the READ instead, it also loses the page that wins it, and that page is the
one the keys actually belong to.

**The file is not ambiguous.** `.xoffset` and `.yoffset` are written *inside* a `seite` block, so the
file states exactly whose they are. The ambiguity is in the in-memory representation — a
`Map<Integer, List<String>>` keyed by id — and the fix resolves it by throwing the data away rather
than by keying it somewhere unambiguous.

**How I know.** Executed, at `a281e3a2` with `src/` clean, on the genuine Central Station export this
repository ships:

```
N8 ids    = {0 stationer=1, 1 gods=1, 2 opstilling=2, 3 s88=3, 4 indre=4, 5 ydre=5,
             6 autonom kreds=6, 7 autonom annotated=7}
N8 extras = {2=[ .xoffset=5,  .yoffset=5]}
N8 OLES 0-stationer offsets survived = false
N8 OLES 2-opstilling offsets survived = true
N8 OLES ids after = {0 stationer=1, 1 gods=8, 2 opstilling=2, ...}
```

`0 stationer` carries `.xoffset=1` / `.yoffset=3` and no `.id`, which resolves to its position, 1; the
page after it states `.id=1`. Both are withdrawn and the offsets are gone from the written index.
`2 opstilling`, which collides with nobody, keeps its own — so the guard is not simply refusing
everything.

**And the code it replaced was right on this file.** The last line above is the control and it needs no
mutation: the page that loses the argument, `1 gods`, is reissued **id 8**, and `writeLayoutIndex`
computes `next` as one above every id in the file (`LayoutDiagram.java:1537-1541`), so a reissued id can
never be one the extras map has anything filed under. `appendPageExtras(contents, pageExtras, 8)`
therefore finds nothing, and `0 stationer` looked up id 1 and got its own keys. `X8V-B1` measured the
same thing and wrote it down — *"the probe checked where the offsets landed: they are correct"*. The
synthetic case it built to show the hazard has the keys on the page that LOSES; every real file in this
repository has them on the page that wins.

The same shape, hand-made and run:

```
N8 SYNTH ids    = {Alpha=1, Beta=1}
N8 SYNTH extras = {}
```

Alpha has no `.id` and carries `.xoffset=42`/`.yoffset=43`; Beta states `.id=1`. Nothing is kept.

**Why B and not A.** What is thrown away is a scroll position, which is cosmetic on the station, and it
was being thrown away entirely until `X8-B2` two commits ago. What makes it a finding at all is the rule
the mechanism exists to enforce, which `LayoutDiagram` states in its own words: *"what TrainControl does
not understand it is not entitled to throw away"*. This now throws it away in the one real case the
machinery was built for. **It cannot bite Adam's own layout today** — `cs2_sample_layout`'s five pages
carry ids 5, 1, 2, 3, 4 and all state one, so there is no collision (`N8-D8`). It bites the shape a
Central Station writes, which is four of the twelve `gleisbild.cs2` files here.

**What I would change.** Key the kept lines by something the file states unambiguously — the block's
ordinal position, alongside the id — and resolve to the id only where `writeLayoutIndex` looks them up,
withdrawing at that point for an id it has just reissued, which is what `X8V-B1` asked for. Then
`testTwoPagesClaimingOneIdKeepNeithersKeys` inverts into its own control: the page that keeps the id
keeps its keys, and the page that is reissued gets none.

### N8-B2 — the rebuild was made conditional on the store, and four of the five callers change the diagram

**What is wrong.** `AutonomySession.java:2112`:

```java
if (changed || (moves != null && !moves.isEmpty())) touched();
```

The paragraph immediately above it states the rule correctly and then applies it to only half the cases:

> **STILL UNCONDITIONAL FOR A MOVE, AND THAT IS THE LOAD-BEARING HALF (X8V-B2).** A move changes the
> DIAGRAM, and the graph is built from the diagram - so the rebuild is owed whatever the store happened
> to be holding about those squares…
>
> For a built-over-only call - `forgetTiles`, which is what a delete, a paste, a fill and a clear reach
> - **there is nothing to rebuild FROM unless something was stored**…

A delete, a paste, a fill and a clear all change the diagram too. `forgetTiles` is reached from
`LayoutEditor:2433` (`forgetBuiltOver`, reached by `pasteSelection` and `fillSelection`), `:2514`
(`execCopy` — every single-tile paste and every palette drop onto a blank square), `:3701` (`delete`)
and `:5205` (`clear`), and all four have just added, replaced or
removed track. The second sentence is about the store; the first is about the diagram; the graph is
built from the diagram.

**How I know.** Executed, then mutation-verified in both directions. The probe took the first blank
square of `live-snapshot`'s main page, put a straight on it through `LayoutDiagram.addComponent`, and
then made the one call `execCopy` makes:

```
N8 GRAPH blank square = 1 - Main:1,0
N8 GRAPH in graph before the placement = false
N8 GRAPH tile on the page now = true
N8 GRAPH forgetTiles answered = false
N8 GRAPH in graph after the placement = false
```

With `AutonomySession.java:2112` mutated back to an unconditional `touched()` — the behaviour before
`a281e3a2` — the same probe answers:

```
N8 GRAPH in graph after the placement = true
```

So the rebuild is what carried a placement into the graph, and it has been removed for exactly the calls
that make one.

**How far it reaches, and the honest limit.** Leaving the track editor for another page or for autonomy
mode goes through `layoutEditingCompleteThen` / `autonomyEditorClosed` (`LayoutEditor.java:6053-6101`),
both of which re-read the layout and rebuild the session — so the stale graph is corrected before the
autonomy editor is ever shown, and the track-editor keys refuse to run in autonomy mode
(`LayoutEditor.java:7202`). **I did not find a reader inside track mode that acts on the stale graph**,
so this is a trap rather than a live wrong answer today. It is filed at B rather than C because the
argument written into the code for making it conditional is wrong as stated, and the next person to add
a reader to the track editor — a findings count in the sidebar, a greyed link, a covered-track mark —
inherits a graph that is up to date for some edits and not for others with nothing to say which.

**And nothing pins it.** Under the same mutation, `regression.testDeleteForgetsTheWholeSquare` came back
`Total tests run: 2, Failures: 0, Skips: 0`. The new `testForgettingNothingSaysNothingChanged` pins the
RETURN VALUE, which is the half `X8V-B2` was about; the rebuild it also changed is unmeasured by
anything (`N8-D5`).

**What I would change.** Split the two answers, which are two questions: return whether the store
changed — which is what the four callers read, and what `X8V-B2` correctly fixed — and rebuild whenever
the caller says the diagram moved. `forgetTiles` is only ever called after the diagram has moved, so the
honest version is `touched()` unconditionally and `return changed;`, with the comment saying that the
saving `X8V-B2` was after is the `saveQuietly` the caller now declines, not the rebuild.

### N8-B3 — the catalogue that outlives the reviews holds fifteen findings `X8V` never made, and the test that calls it current cannot see any of it

**What is wrong.** `docs/manual-tests/triage.db`, and the `findings.tsv` rendered from it, hold rows for
`X8V-validation.md` that do not correspond to findings:

```
$ sqlite3 (read-only) — refs filed under X8V-validation.md that appear nowhere in the document
in db not in doc: X8V-A1a X8V-A1b X8V-A1c X8V-A2 X8V-B1a X8V-B1b X8V-B2a X8V-B2b
                  X8V-B3a X8V-B3b X8V-B4 X8V-B5 X8V-B6 X8V-C2a X8V-C5a
count 15
their severities: A A A A  B B B B B B B B B  C C
```

Their titles are the rows of that document's **mutation table** in Method — "the fourth arm of
`parseFileContents` never matches", "`delete` back to `forgetCaptionsAt`", "the `initCopy` guard
removed" — and their dispositions are the results column, "2 of 2 red", "1 of 1 red". The mutation ids
were read as finding ids. Four of them are filed at severity **A**, and `X8V-validation.md`'s A section
says, in full: *"Nothing."*

**One of them landed on a real finding.** `X8V-C1` exists, and its disposition in the database is
`1 red: testNoCommentCitesALineNumber` — the mutation table's `C1` row. Its own status table
(`X8V-validation.md:281`) says `closed`. The real disposition is gone.

**And a second row disagrees on its own.** `X8V-C3` is recorded `open`; `X8V-validation.md:283` says
`closed`, and the document's status line says *"All ten findings dispositioned"*. That is
`E8V-C3` again — *"`findings.tsv` records all seventeen E8 findings as `open`, in the commit that fixed
them"* — recorded as fixed, and back one round later.

**Why this is B and not C.** This is not a tidiness problem. `regression.testEveryCitationResolves`'s
own assertion message says what the catalogue is for: *"Since the review folder was deleted on
2026-09-08 it and the mirror beside it are the only record of what 2,265 findings were about"*, and
*"the reviews are being deleted on the strength of it"*. A record that gains fifteen findings and loses
one disposition per round is not a record; and the documents it replaces are being removed.

**The guard cannot see it.** `testTheCatalogueIsThereAndSaysWhereItCameFrom`
(`test/regression/testEveryCitationResolves.java:287-311`) is titled *"The catalogue is regenerable and
current"* and says it *"does hold the three things that would make it a lie"*. The three are: the file
exists; the file contains the string `triagedb`; and two other files exist. Nothing compares a row with
the document it came from. `E8`, `E8V` and `X8` are clean — only `X8V` is polluted, because it is the
first document whose Method table used letter-number ids — so the catalogue has been right until now and
nothing would ever have said when it stopped being.

**How I know.** Read the four documents' status tables, then queried `triage.db` read-only and diffed
its refs against every `X8V-[A-D]\d+` in `X8V-validation.md`. The count above is that diff. Also read
`triagedb.py`: `load_findings` and `add_findings` take rows from the caller and there is no document
scanner, so these are hand-entered rows rather than a parser fault — which is why a guard is the only
thing that could have caught it.

**What I would change.** Delete the fifteen rows, restore `X8V-C1`'s disposition to `closed` and
`X8V-C3`'s to `closed`, and give `testTheCatalogueIsThereAndSaysWhereItCameFrom` the check its title
claims: for every `*.md` in `docs/reviews*/` that declares a prefix, every `PREFIX-[A-D]<n>` row in the
catalogue must appear in that document, and every id in that document's status tables must appear in the
catalogue with the same disposition. That is the one check that makes deleting a review document safe.

---

## C — low

| id | what | disposition |
|---|---|---|
| N8-C1 | `fillCombinedPage` grows a page past `MAX_SIZE` with no check — the sibling `X8-C5`'s consolidation did not sweep | open |
| N8-C2 | `behaviour.md` says nothing about the route-command delay floor, which now rewrites what the operator typed and what the editor shows | open |
| N8-C3 | `Layout.java:2404`'s rewritten citation names the method the comment is already inside | open |
| N8-C4 | `TrainControlUI.java:18559-18561`'s rewritten citation now says the same thing twice | open |

### N8-C1 — the one ceiling is asked by every gesture that grows a page except the one that can grow it most

`X8-C5` and `X8V-C2` turned three copies of `MAX_SIZE` into `LayoutEditor.roomToGrow` (`:4479`) on the
stated grounds that everything which grows a page should ask one predicate. `growEdges` (`:4896`),
`LayoutEditor.addRowsAndColumns` (`:5041`), `canShiftDown` (`:4532`), `canShiftRight` (`:4552`) and
`LayoutEditorRightclickMenu:445` all ask it now. `TrainControlUI.fillCombinedPage` does not:

`src/org/traincontrol/gui/TrainControlUI.java:24961-24971`

```java
width = Math.max(width, page.getSx());
height += page.getSy() + 1;
...
made.addRowsAndColumns(Math.max(0, height - made.getSy()), Math.max(0, width - made.getSx()));
```

It goes straight to `LayoutDiagram.addRowsAndColumns`, which has no ceiling — the same door
`X8-C5` found `shiftDown` going through.

**How I know.** Read, then measured the sizes rather than argued them. `live-snapshot`'s five pages
parse at 24x15, 23x15, 21x18, 31x15 and 31x17, and `1 - Main`'s three arrows reach two of them, so
`pagesLinkedFrom("1 - Main")` is three pages and the combined page comes out **24 x 51** against a
ceiling of 60. Nine rows of headroom, and one more linked page puts it over. `cs2_sample_layout`'s
pages are the same five.

So this is a trap rather than a live defect on Adam's railway, which is why it is C — and a page above
the ceiling is tolerated anyway (`X8V-C2` measured three fixture pages parsing at 17x129). What it
costs is that such a page can never be grown again and Increase Size is greyed on it with a tooltip
about a limit the operation that made it did not apply.

**What I would change.** Put the ceiling in `LayoutDiagram.addRowsAndColumns`, where both paths run
through it, which is `X8-C5`'s own "What I would change" and is still the smaller fix. Failing that,
ask `roomToGrow` in `fillCombinedPage` and refuse before the page is created rather than after.

### N8-C2 — the editor now rewrites the number the operator typed, and `behaviour.md` has never mentioned delays

`X8-C2` and `X8V-C7` made the 150 ms floor visible: `RouteEditorFrame.delayTheRailwayWillUse`
(`:2755-2762`) raises any delay between 1 and 149 to 150, and it is asked twice — by `delayOf` as the
number is typed, and by the delay column's `getValueAt` as it is shown. Both are right and both are
tested. Neither is written down.

`grep -n "delay" docs/reference/behaviour.md` returns nothing. So a number the operator types is
silently changed, a number stored in his route file is shown as a different number, and the document
that is meant to hold the intended behaviour of the railway does not know the floor exists. On a
release whose bar is that the behaviour is documented, this is the same class as `E8-C10` (Control+E),
which was graded C and fixed. Read only.

The page ceiling (`N8-C1`, `X8-C5`, `X8V-C2`) and yesterday's Manage Pages changes — "Edit Current
Page" removed, "Combine linked pages" moved — are in the same position: tested, and absent from
`behaviour.md`.

### N8-C3 — a citation rewritten to name the method it is already inside

`X8V-C1` asked for `Layout.java:2404` to be repointed at *"`isPathClear`'s inactive-endpoint check"*.
It now reads:

> `isPathClear`'s stricter form - any edge with an inactive endpoint - keeps its `isAutoRunning` fence

The comment is at `Layout.java:2404`, inside `isPathClear`, which runs from `:2258`; the check it means
is the `if (this.isAutoRunning() && (!e.getStart().isActive() || !e.getEnd().isActive()))` at `:2314`,
also inside `isPathClear`. Naming the enclosing method points a reader at the comment's own location.
It is not wrong, which is why it is C and not a reopening of `X8V-C1` — but `CommandRow`'s rule is "by
method name" because a name is supposed to locate the code, and this one does not. Read only.

### N8-C4 — the `showTab` citation now says it twice

`TrainControlUI.java:18559-18561`:

> `// UXR-C21: showTab(Icon) removed. Dead code - its only call site`
> `// (the commented-out showTab in LocomotiveSelector.addLocomotiveActionPerformed) has been`
> `// commented out, and it never picked up C20's isEnabledAt/getTabCount guard…`

The repoint that `X8V-C1` asked for was made by substituting the new description into the old sentence,
so the call site is now described as commented out inside the parenthesis and again after it. Read
only.

---

## D — not defects, and one withdrawal

| id | what | disposition |
|---|---|---|
| N8-D1 | `X8-A2`'s predicate has exactly one production caller and it asks both ways | clean |
| N8-D2 | rotating a tile does not misapply a barred arrival — the surrounding machinery compensates | clean, and it settles what `X8` left unfiled |
| N8-D3 | Control+R and Control+T pass the same possibly-null label `X8-B1` was about, and are safe | clean |
| N8-D4 | `testTheDiagramCeiling` and `testADelayBelowTheFloorIsShownAsTheFloor` both go red under their own stated mutations | clean, measured |
| N8-D5 | nothing pins the conditional rebuild in either direction | measured — it is `N8-B2`'s evidence, not a finding of its own |
| N8-D6 | the tree was committed under this review, and another reviewer was mutating it | recorded |
| N8-D7 | `ListMapKept.forget` reporting a change for an already-empty list | **withdrawn** — originally C; the case cannot arise |
| N8-D8 | `cs2_sample_layout`'s page ids do not collide, so `N8-B1` cannot bite Adam's own layout | clean |

### N8-D1 — the multi-unit predicate is asked both ways, by the only thing that asks it

`grep -rn "isSimultaneousMultiUnitCompatible" src/ test/` gives one production call site,
`Layout.sanitizeMultiUnits` (`Layout.java:6993-6995`), and it asks both directions. The javadoc
`a281e3a2` added (`MarklinLocomotive.java:1090-1102`) says so and says the predicate is not symmetric,
which is what `X8V-C8` asked for. Read.

### N8-D2 — a rotated tile cannot end up barring the wrong side

`X8` left this unfiled rather than guess: *"the rotate gesture… tells the autonomy setup nothing, where
every other mutating gesture does, and `tileDirections` is keyed by which route crosses the square while
`barredArrivals` stores side names — both of which rotation could change the meaning of."*

It cannot, and the reason is in two places. `TilePorts.Side` is an absolute compass enum — `N, E, S, W`
(`TilePorts.java:51-53`) — so "no train may arrive from the south" means the same thing at any
orientation; what rotation changes is which sides the tile has ports on, and
`TilePorts.ports(type, orientation, state)` (`:378`) rotates the port set to follow the tile. And
`AutonomySession.getBarredArrivals` (`:3250-3267`) already names rotation as the case it exists for:

> A restriction is stored against a side of the square as the diagram was when it was set, and the
> diagram moves: **a tile replaced or rotated**… `barred.retainAll(arrivalSides(tile))`

So a side that no longer exists is dropped and a side that survives keeps its meaning. `tileDirections`
is keyed by `(square, route)` and a route that no longer crosses the square is simply never asked for.
The effect of a rotate is that a restriction the operator set may be silently *dropped*, which fails
open on a preference rather than on an interlock. Read, against `TilePorts` and both readers.

### N8-D3 — the two other key branches that pass a possibly-null label are safe

`X8-B1` guarded `initCopy`; `LayoutEditor.java:7254` (`Control+R` → `rotate`) and `:7262`
(`Control+T` → `editText`) pass `getLastHoveredLabel()` with no null check, which is the same shape.
Both are safe, and not by accident of the fix: `LayoutGrid.getCoordinates` (`:2048-2062`) returns
`{-1, -1}` for a label it does not hold, every cell of the grid is populated including the spacer row
and column (`LayoutGrid.java:1147-1191`), so `getCoordinates(null)` cannot match a cell; and
`layout.getComponent(-1, -1)` is bounds-guarded and answers null, which both methods treat as "nothing
here". Read, all four methods.

### N8-D4 — the two newest tests fail for the reasons they name

`regression.testTheDiagramCeiling` claims *"MUTATION: charge each increment against the other dimension
and `testATallPageMayStillGrowSideways` fails"*. Charged: `Total tests run: 3, Failures: 1`.
`core.testRoutes.testADelayBelowTheFloorIsShownAsTheFloor` claims *"return `asked` unchanged from
`delayTheRailwayWillUse` and the first two claims fail"*. Returned: `Total tests run: 26, Failures: 1`.
Both restored and the tree verified clean.

### N8-D5 — the rebuild half of `X8V-B2`'s fix is measured by nothing

With `AutonomySession.java:2112` mutated back to an unconditional `touched()`,
`regression.testDeleteForgetsTheWholeSquare` came back `Total tests run: 2, Failures: 0, Skips: 0`. The
class's new test asserts the return value and its control asserts the return value; neither asks what
was rebuilt. Recorded here rather than as a finding because it is the evidence under `N8-B2` and would be
closed by the same change.

### N8-D6 — the tree was committed, and a second reviewer was mutating it, while this was being measured

Two things moved under this pass and both have to be said, because one of them spoiled a measurement.

**The twenty uncommitted files were committed as `a281e3a2`** at about 18:05, part way through. Nothing
in them changed; `git status --porcelain` simply stopped listing them. The baseline the brief supplied
therefore cannot be compared line for line any more, and the check that matters was done instead:
`git diff --stat -- src/ test/ docs/ build.xml` is empty at the end of this pass, and the only files
`git status` shows are the three under `cs2_sample_layout/` that Adam's running application rewrites.

**Another review session was mutating `src/` at the same time.** At 18:10 a mutation labelled
`S14 MUTATION - REVIEW MEASUREMENT ONLY, TO BE REVERTED` appeared inside
`LayoutDiagram.readLayoutIndexIds`, dropping a page whose id another page claims. One measurement was
taken while it was in place and was wrong because of it — the `Oles kreds` ids came back missing
`0 stationer` entirely — and was discarded and retaken. Two consequences worth carrying forward:

- a restore from a whole-file copy would have deleted the other session's in-flight mutation, so only
  the exact edit was reverted, with `Edit` rather than `cp`;
- every measurement after that was preceded by `git diff --quiet -- src/` and the answer recorded.

`docs/reviews-2026-09-10/S14-application-review.md` appeared during the last run, so `S14` is a live
prefix in this folder as of today.

### N8-D7 — withdrawn: `ListMapKept.forget` cannot report a change for an already-empty list

Raised at **C**. `AutonomyCompanionStore.java:4459-4485` ends each entry with

```java
if (pair.getValue().isEmpty())
{
    pairs.remove();
    any = true;
}
```

which sets `any` on removing an entry whose list was already empty before the call — an over-report,
and over-reporting is what `X8V-B2` was about. Withdrawn on reading the writers: `stationSignals` and
`blockedPoints` are the only two `ListMapKept`s, and both setters remove the key rather than store an
empty list (`:250`, `:342-345`). No producer can leave one behind, so the branch can only fire on an
entry this same loop has just emptied — which is a real change. The distinction is worth keeping in
mind if either collection ever gains a third writer.

### N8-D8 — Adam's own layout has no colliding page ids

`cs2_sample_layout/config/gleisbild.cs2` states `.id=` on all five pages: 5, 1, 2, 3, 4. Every id is
claimed once, so `attribute` withdraws nothing and `N8-B1` costs him nothing today. The four files in
the repository that do have the collision all have it the same way — a first `seite` with no `.id` at
all, which is what a Central Station writes: `Oles kreds`, `sample_layout`, `test/layout` and
`tc_backup`.

---

## What I did not cover

- **The battery.** It was green at `d86914a9` (`X8V-D12`, 209 classes). `a281e3a2` has not been
  measured by anybody's battery run, and this pass ran seven classes.
- **The network layer and the CS3 locomotive import**, which `X8` also declined.
- **`docs/manual-tests/tests.md` and `issues.md`.** `findings.tsv` and `triage.db` were audited for the
  four documents of this folder and nothing else; the 38 hands-on tests awaiting Adam are still
  unaudited by anybody, which is now four passes in a row.
- **Whether a Central Station minds a page file whose `page=` or link index disagrees with its own
  ordering.** `N8-A1` establishes what TrainControl does; what the station does with the same file is
  the open question `X8-A1` and `X8V-C3` both raised, and it needs the real hardware.
- **The 181 commits before 2026-09-07** were sampled through what the recent work touches, not read.
