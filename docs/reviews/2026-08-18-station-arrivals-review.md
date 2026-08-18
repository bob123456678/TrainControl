# Station arrivals, the translation layer, and the running path

**Prefix for citing this document: `SA`.**

Reviewed at commit `0c96536` on branch `autonomy-diagram-r0`, 18 August 2026, against TrainControl
v3.0.0 [Beta]. Code HAS been changed since: every finding below carries its disposition in the status
table of its section, and the prose under each finding records what was believed when it was written.

## Scope

Three commits, and the code around them:

| commit | what it did |
|---|---|
| `3e9d249` | the running path drawn as a line along the track, with black direction chevrons |
| `2c0eb75` | `StationIndex` - one translation between squares and running Points - and the three faults its absence had caused |
| `0c96536` | stations may refuse arrivals from a given side |

## Passes

| pass | model | method |
|---|---|---|
| feature review | Fable | the three commits and the code around them, read against the split-point model |
| changes pass | Opus | the five commits to 81a51ac, told what the first pass had already found and to look for what it missed |
| regressions pass | Opus | what worked before this week and does not now, across the shared files the branch touches |
| graph derivation | Opus | TileGraph/GraphReducer/AutonomyBuilder split logic, against the ground-truth oracle |
| running model | Opus | Layout/Point/Edge, the new exclusivity sweep, path locking, bfs |

---

## A - high

| id | finding | status |
|---|---|---|
| A1 | barring an arrival side of a turn-around station invalidates the whole configuration | fixed, `d4cc22a` |
| A2 | excluding a page permanently destroyed the arrival restrictions on it | fixed, `a5de425` |
| A3 | Cancel in the diagram editor destroyed the setup of every square it had touched | fixed, next commit |
| A4 | station captions do not exist at all without a local layout folder | OPEN - for Adam |
| A5 | undo does not undo the caption edits the diagram editor performs | OPEN - for Adam |
| A6 | the caption migration rewrites .cs2 pages in place, with no atomic write | OPEN - for Adam |

### A1 - a barred terminus copy is emitted as a terminus that is not a destination

`AutonomyBuilder` emits two flags from what used to be one fact. `station` became per-copy when
arrival restrictions arrived; the terminus flag beside it still read the SQUARE:

```java
json.put("station", point.isStation() && arrivalAllowed(node));   // per copy
...
json.put(point.isStation() ? "terminus" : "reversing", true);     // per square
```

So the turn-round copy of a barred side came out as `station: false, terminus: true`.
`Point.setTerminus` refuses exactly that pair, and `Layout.parseAuto` answers a refusal by
invalidating the **whole** layout, naming a Point copy nothing on the diagram carries.

The reachable gesture is the feature's most natural use: a terminus platform with two ways in, one of
them barred. Every path afterwards is refused with "configuration is invalid and must be reloaded" -
the same symptom the duplicate-locomotive fault produced a week earlier, and for the same structural
reason: a rule enforced in `Point` that the builder can violate silently.

Fixed by deciding the value once and using it for both flags. A barred turn-round copy is now emitted
as a plain `reversing` point, which is what it is: somewhere trains turn round and nobody is sent.

Test: `testBarringASideOfATurnAroundStationStillLoads` asserts no emitted point pairs `terminus: true`
with `station: false`. Seen failing first, on the exact JSON the builder produced.

### A2 - the save-time pruning was measured against the graph, which leaves out excluded pages

Introduced by the B1 fix, and found by the second pass.

`forgetArrivalsThatNoLongerExist` intersected each stored restriction with the square's live arrival
sides.  Those come from the StationIndex, which is derived from the graph - and the graph leaves out
excluded pages by construction.  So a square on an excluded page has no arrival sides at all, the
intersection emptied, and the restriction was deleted outright.  Re-including the page gave nothing
back, and nothing reported the loss because it happened before the reconciliation that reports things.

Six lines below the call, `save()` carries the comment explaining why nothing may reconcile against
the graph - written after this exact mistake destroyed every setting on an excluded page once before.
The new field was the only one not going through `reconcile`, and it walked into it.

The same shape catches a station fed only through a link, which splits into nothing.

Now skipped for any square the index has never heard of, which keeps the stale-side pruning B1 wanted:
a square that is still in the graph with fewer sides is still pruned.

Test: `testExcludingAPageKeepsItsArrivalRestrictions`.

### A3 - Cancel discarded the track and kept the deletions in the setup

The diagram editor works on the live {T}LayoutDiagram{T} objects.  Cancel reverts by re-reading the pages
from disk into NEW objects - so the session is left holding the edited ones, and
{T}resetAutonomySession{T} then saves through {T}AutonomySession.save(){T}, which reconciles the setup
against exactly those mutated pages.

Delete a few sensor squares, change your mind, press Cancel: the track comes back and the names,
lengths, directions, captions and arrival restrictions of those squares are gone from
{T}setup.json{T}, permanently, with nothing said.

Fixed by separating the two things {T}save(){T} does.  The paths that save because the diagram is being
REPLACED - a re-download, an editor closing - now write without reconciling; reconciliation waits for
the next explicit save, when the pages are current and its report can be shown to somebody.  Nothing is
lost by waiting: a setup whose diagram genuinely changed is tidied a moment later.

Not covered by a test.  Reproducing it needs the editor's whole open-mutate-cancel lifecycle, which is
Swing end to end; the fix is one call changing to a narrower one, and the narrower one cannot delete
anything.

### A4, A5, A6 - open, and for Adam

These are branch-level, not from this cycle's work, and each needs a decision rather than a patch.

**A4 - captions require a local layout folder.**  The live caption branch in {T}LayoutGrid{T} is keyed on
{T}ui.autonomyCaptionAt(...){T}, which needs an {T}AutonomySession{T}, which returns null outright when
there is no local layout path.  For a user reading the diagram straight from the Central Station -
the "switch to CS layout" mode - that means no station captions at all, and the squares fall through
to drawing the literal text {T}Point:Bahnhof{T}.  Master decided the same thing from the tile's own
label and needed nothing else.

This is the price of moving captions out of the diagram, and it is a product question: either autonomy
captions become available without a local folder, or that mode keeps the old {T}Point:{T} rendering as a
fallback.  Worth settling before release, since it removes an everyday display from a whole class of
users.

**A5 - undo does not cover the caption edits.**  The editor's undo stack holds
{T}LayoutDiagramComponent{T}s only.  Deleting a captioned sensor calls {T}forgetCaptionsAt{T} and moving
one calls {T}moveCaption{T}; neither is snapshotted, so Ctrl+Z brings the tile back without its caption,
or moves the tile back and leaves the caption at the new square.  Fixing it properly means the undo
stack carrying setup state as well as diagram state, which is a design decision.

**A6 - the migration rewrites .cs2 pages in place.**  {T}migrateStationLabels{T} strips the old
{T}Point:{T} labels and calls {T}saveChanges{T}, which truncates and writes without the atomic staging
{T}Util.writeAtomically{T} gives the locomotive database and the UI state.  A crash mid-write costs a
page of track diagram.  It is also one-way: once stripped, an older build shows no station labels at
all.  The fix is small - route it through {T}writeAtomically{T} and take a backup first - but it changes
what happens to a user's files, which is Adam's call.

## B - medium

| id | finding | status |
|---|---|---|
| B1 | a restriction naming a side the square no longer has locks the menu and cannot be cleared | fixed, `d4cc22a` |
| B2 | a train on a non-destination copy had no right-click menu at all | fixed, `81a51ac` |
| B3 | with no train there, the menu still hung off whichever copy sorted first | fixed, next commit |
| B4 | a locomotive could be placed at random onto a barred copy | fixed, `a5de425` |
| B5 | switching pages killed the live captions of the page left behind | fixed, next commit |
| B6 | the path search moved from a worker thread onto the EDT | OPEN - for Adam |

### B1 - stale barred sides were counted, hidden, and unremovable

The Arrivals submenu decides whether a side may still be shut from the size of the stored barred set:

```java
allow.setEnabled(barred.contains(side) || barred.size() < ways.size() - 1);
```

`barred` was the raw stored set, never intersected with the square's current arrival sides, and
`reconcile` only drops the entry when the whole TILE goes - not when the tile survives a diagram edit
that changes which sides trains arrive by.

Station with ways `[E,W]`, `E` barred, diagram redrawn so the ways become `[N,S]`: for both N and S,
`contains` is false and `1 < 1` is false, so both boxes read "allowed" and both are disabled. Nothing
can be restricted, the stale `E` is never offered so it cannot be cleared, and nothing on screen
explains any of it.

Fixed in two places for two different reasons. `getBarredArrivals` intersects with the live arrival
sides, so no reader can be misled - that is the correctness fix. `save()` drops dead sides from the
file, so a restriction cannot lie dormant and return the day the diagram is edited back into a shape
that has that side again.

Test: `testARestrictionOnASideTheSquareNoLongerHasIsIgnored` bars the side facing track it then takes
up, and checks both halves.

### B2 - the diagram menu hung off the designation, not the locomotive

Found while dispositioning D4 rather than by the review pass.

`LayoutRightclickAutonomyMenu` gated its whole autonomy block on `current.isDestination()`.  A
locomotive standing on a copy that is not a destination therefore had no menu at all - no remove, no
paths, no name - and this feature makes that reachable: barring a side makes THAT copy a
non-destination, and a train can still be placed on it by hand or left there by an earlier setup.

It is the same trap the autonomy editor had a day earlier, where the remove item hung off the station
designation instead of off the locomotive, and it was not caught by the same reasoning being applied
to the other surface.  The block now opens for a destination OR for any square with a train on it;
placing a locomotive stays destination-only, which is what it was.

Not covered by a test: the menu is Swing construction with no seam, the same reason the editor's
equivalent was not.  Both are one right-click to check by hand.

### B3 - the B2 fix covered the train and not the empty platform

`speakerAt` prefers an occupied copy and otherwise returns the first in emission order, which is the
order the sides happen to sort in.  With one side barred and no train standing there, whether the menu
spoke for the open copy or the shut one depended on WHICH side had been barred: bar the east of an
east-west platform and the first copy is the shut one, so the whole autonomy block was skipped and the
square looked as though it had no autonomy at all.  Bar the west and everything worked.

B2 fixed "a train on a copy that is not a destination"; this is the same fault with no train on it.
`speakerAt` now prefers a copy trains may stop at before falling back to the first.

### B4 - and placement then ignored the rule the same hunk had just added

`placeableCopies` filtered on having somewhere to go and not on being a destination, while the menu
item above it had just been gated on `isDestination`.  So the guard was defeated by the action it
guarded: the copy is picked at RANDOM from that list, so it landed on the barred copy about half the
time, and parseAuto then warns about a locomotive on a non-station every time the configuration loads.

Now filtered by destination, falling back to the shut copies only when nothing is open - a square where
trains may not stop is still somewhere a train physically is, and refusing to place one there is a
different message from the "no way out" one this list exists to produce.

Neither has a test: both are Swing menu construction, the same seam problem as B2.  All three are one
right-click to check.

### B5 - the prune deleted the captions of every cached page

Mine, from the popup fix two commits earlier.  {T}pruneLayoutStations{T} dropped every registered caption
label that was not displayable, on every repaint.  But the main window CACHES a page's grid and
re-attaches it when the user comes back, so that page's labels are detached - and perfectly alive -
the whole time another page is showing.

Visit page A, then B, then C: A's labels are detached when C is built, so they are dropped.  Go back to
A and its captions are frozen at whatever they last said, showing trains at platforms they left
minutes ago.

{T}DiagramTileRegistry{T} carries this exact rule, with a comment explaining it, and I did not carry it
across - the same mistake the July cycle recorded five times, and the reason its SOP says to grep for
the twins of anything you fix.  Each grid now drops the labels it replaces as it registers their
successors: per square, so pages cannot touch each other's, and per owning container, so a popped-out
window showing the same page does not have its live labels dropped by a repaint of the main one.

### B6 - open, and for Adam

{T}repaintAutoLocListLite{T} used to run its {T}updateState{T} loop on a worker thread; it now runs inline
inside an {T}invokeLater{T}, so on the EDT.  That loop calls {T}getPossiblePaths{T}, which is synchronized
on the {T}Layout{T} and does an O(points squared) search - once per locomotive panel, on every arrival
and departure.

Two consequences: the interface stalls for the duration on a large layout, and the EDT can now block on
the Layout monitor while a driving thread holds it, which is a deadlock surface that did not exist.

The thread removal was right - the old code mutated Swing state off the EDT - but the fix wants the
search on a worker and only the Swing writes marshalled.  Left open because it is a threading change in
the running path and deserves to be made deliberately rather than at the end of a long session.

## C - low

| id | finding | status |
|---|---|---|
| C1 | `StationIndex` derived lazily on whichever thread asked first | fixed, `d4cc22a` |
| C2 | `DiagramMonitorDriver` was a fifth hand-assembled builder | fixed, `d4cc22a` |
| C3 | `renamePage` orphaned captions and switched-off links | fixed, `d4cc22a` |
| C4 | arrival restrictions survived station demotion | fixed, `d4cc22a` |
| C5 | `arrivalSides()` rebuilt a whole builder per call | fixed, `d4cc22a` |
| C6 | the dedupe lost the short-circuit that makes a Point equal to itself | fixed, next commit |
| C7 | `TileOverlay.isBlank` was not updated for segments while `equals` was | fixed, next commit |
| C8 | two of the new tests could not fail | fixed, next commit |

### C1 - the index was derived by whoever asked first, which is often the feedback thread

`getStationIndex()` derived lazily. The cache is dropped on the event thread by `setPointProperty`,
which is documented as usable while autonomy is running; the next reader is typically
`updateStationLabels`, once per Point per feedback event, on the control station's thread. That reader
therefore walked the configuration's `JSONObject`s - unsynchronised `HashMap`s - while the event thread
could be writing them. `volatile` publishes the reference safely and says nothing about the derivation.

Fixed by deriving eagerly at both invalidation points, which are both on the event thread. Readers now
only ever see a finished immutable index, which is what the class docs already claimed. The lazy path
remains for the window before the first rebuild, when nothing is running.

### C2 - a fifth builder, already drifted

`DiagramMonitorDriver.bind()` assembled its own `AutonomyBuilder` without `withPointExtras` or
`withBarredArrivals`. Harmless today - neither affects the naming - and exactly the drift class the
same commit set out to remove. Now `session.builder(null)`.

### C3 - captions and switched-off links did not follow a page rename

Pre-existing, and in a method `0c96536` had just edited to add the new field. `captions` is keyed by
the square the text sits on and points at the square of the station, so both halves must move; a
rename moved neither, and the next save deleted every caption on the page as unreconcilable.
`disabledPortals` was not rekeyed either, so a rename switched every disabled link back on.

Test: the store's existing `testRenamingAPageCarriesEverythingOnIt` grew both, plus the new
`barredArrivals`.

### C4 - a restriction outlived the station it restricted

Inert while demoted, and therefore a trap: re-promoting the square months later resurrects a rule
nobody remembers writing. Now cleared on demotion, symmetrical with the caption rule the previous
commit introduced.

### C5 - the per-call builder came back

`arrivalSides()` ran a whole builder pass per call, and it is called per station per repaint. Moved
into `StationIndex`, where the rest of the derivation lives.

### C6 - a Point stopped being in the same place as itself

Both dedupe call sites used `session.sameSquare`, which short-circuits on the names being equal before
consulting the index.  Moved into `StationIndex.distinctDestinations` they lost it - and the index
answers "different place" about two Points it has never heard of, which is exactly a configuration built
before the last diagram edit, or a hand-written one.  A path from P back to P was then offered as a
destination while the train stood on P.  The short-circuit is back, in the index.

### C7 - blank and equal disagreed

`equals` and `hashCode` grew the segments deliberately, so a train claiming the same square from a
different side counts as a changed picture.  `isBlank` did not, and `paint` returns early on it - so
an overlay carrying a line with no state would have forced a repaint and then drawn nothing.  Nothing
emits that pair today; the first thing to want a neutral line would have found it invisible.

### C8 - two tests that agreed with themselves

`testTheIndexRoundTripsSquaresAndPoints` ran on a station at the end of a line, which emits ONE Point -
so its closing check reduced to `sameSquare(x, x)`, and the whole test would have passed against an
index that could not split at all.  It now uses the two-ended fixture and asserts more than one copy as
a precondition.

`testASquareWithALinePaints` asserted `!isBlank()` on an ACTIVE overlay, which is already non-blank
with no segments at all - so it agreed with a rule it was not testing.  It now also covers the case C7
is about, and that half was seen failing before C7 was fixed.

## D - not defects

| id | finding | status |
|---|---|---|
| D1 | the unreachable-station error named a coordinate, not a station | fixed, `d4cc22a` |
| D2 | `DiagramMonitor.indexEdges`/`indexPoints` were dead code with a trap in them | deleted, `d4cc22a` |
| D3 | `speakerAt`'s javadoc became false one commit later | corrected, `d4cc22a` |
| D4 | the right-click menu still answers for one train on a shared square | open - see below |
| D5 | the crowded caption's width budget ignored its own separators | fixed, next commit |
| D6 | `arrivalSidesOf` bypasses the node cache | accepted, see below |
| D7 | the short `AutonomyChecks.run` overloads omit the new check | accepted, see below |
| D8 | "out of service" was reported as wiping captions and restrictions | withdrawn - not a defect |
| D9 | the autosave preference is ignored | accepted, deliberate - but see below |

### D1

`checkArrivalsLeft` reported `entry.getKey().toString()` - "main:3,1" - where every sibling check
reports the point's name. The one ERROR a user is guaranteed to meet named a coordinate. It also took
a `labelled` parameter it never used.

### D2

Two public statics with no callers, which indexed BASE names only - so anything that started calling
them would have got a monitor silently missing every split copy. Deleted rather than fixed: the
builder already answers this correctly, and a second answer is what this area keeps going wrong on.

### D3

"any copy will do: they are the same square and carry the same settings" stopped being true the moment
a barred copy differed in its station flag. Harmless for the current callers, which prefer the
occupant, but a trap as written.

### D4 - open

With two trains on one square the caption now names both; `LayoutRightclickAutonomyMenu` still answers
for the first.  (Its sibling problem - no menu at all on a non-destination copy - turned out to be a
real defect and is B2.) Not a defect in the changed code - it is the pre-existing single-train assumption,
newly visible because the caption no longer shares it. Left open deliberately: the menu's actions
(remove, facing) need a train chosen, and inventing a submenu for a case the user has hit once is
worth a decision rather than a guess.

### D5

`crowdedLabel` divided the ten-character allowance by the number of trains and then added a bar
between them and a two-character arrow after each - so two trains came to seventeen characters against
a single train's fourteen, and the javadoc's claim that the pair "still fits where one name used to"
was not what the arithmetic did.  The furniture is budgeted now.

### D6 - accepted

`arrivalSidesOf` walks the reduced edges rather than reusing `nodesFor`'s cache, so deriving the
index is O(squares x edges).  C5 moved this off the repaint path, so it now runs once per rebuild; the
cost is not worth a second cache that could disagree with the first.

### D7 - accepted

The seven short `AutonomyChecks.run` overloads chain down with an empty map, so a caller not using
the full form would silently miss the new check.  Only one caller exists and it uses the full form, and
this matches the pre-existing chaining for termini, trapped and the rest.  Recorded because D2 deleted
two statics on exactly this reasoning; the difference is that those had no correct caller at all.

### D8 - withdrawn

The regression pass reported that setting a station to "closed" wipes its caption and arrival
restrictions.  It does not: that radio calls {T}setUsage(target, isStation, false){T}, passing the
CURRENT station flag, so it changes only whether the square is open.  Only "can pass through" demotes,
and that gesture does say the square is no longer a station.

Recorded rather than deleted because the finding was reasonable from reading {T}setUsage{T} alone - the
call sites are what settle it.

### D9 - accepted, with a caveat for Adam

The Autosave preference is deliberately ignored and its checkbox hidden, with a comment saying so:
what it saves is the state of the railway, and nobody sets one up in order to discard it.

The regression pass is still right about one consequence.  A legacy user who kept a hand-authored
{T}autonomy.json{T} and turned autosave off precisely to protect it now has it rewritten with generated
coordinates on exit.  Not changed here, because reverting a deliberate decision at the end of a review
is the wrong way round - but worth a look before release.

### Checked and found correct

Recorded because knowing what was verified is worth as much as knowing what was wrong.

- **Persistence of `barredArrivals` is complete**: written with page-id translation, listed in
  `KNOWN_SHARED` so the unknown-field round-trip cannot clobber it, read and untranslated on load,
  rekeyed on rename, dropped by `reconcile` when the tile goes, exported in the bundle and merged
  keep-local on import.
- **`StationIndex` invalidation is otherwise sound**: every session mutator reaches either `touched()`
  (which rebuilds) or `setPointProperty` (which re-derives). `captureFromLayout` deliberately does not,
  and that is correct - it writes only operational keys and the facing, none of which can change the
  split or the naming. All four UI sites calling `store.setActiveConfiguration` directly are followed
  by a rebuild or a save.
- **`DiagramMonitor.lay`**: a revisited square keeps both passes; a cross-page link yields a null side
  so the line stops mid-square with no arrow, by design; a locked edge under a running one is
  suppressed per tile. That last one also hides a locked line on a different route through a crossing
  square the running path crosses - a deliberate trade, documented at the code.
- **Graphics discipline**: `TileOverlay.paint` restores colour, composite, stroke and the AA hint in a
  finally block.
- **Monitor threading**: the layout callback only sets a flag; computing is on the timer thread;
  publishing marshals through `invokeLater` with a generation check; `refresh()` is synchronized
  against out-of-order publishes.
- **The "last way in" guard** is correct once stale sides are excluded (B1); the builder never bars the
  unsplit copy, so a link-fed station cannot be made unreachable this way.
- **`distinctDestinations`** fixes a latent bug in the code it replaced: unknown destinations all
  collapsed under the key `"null"`, so only the first survived.

---

## Core graph — two passes over the derivation and the running model

The owner suspected bugs still in the core graph and asked for a focused fanout, with the pinned
v2.8.1 routes (test/autonomy_formats/v2_8_1-station-paths.txt) as an oracle.

| id | finding | status |
|---|---|---|
| CG1 | the exclusivity sweep collapsed a locked path's reservation to its destination | fixed, `16852e4` |
| CG2 | a path-integrity failure stranded the train on no point at all | fixed, `16852e4` |
| CG3 | setLocomotive's header claimed an atomicity the code does not provide | fixed, `16852e4` |
| CG4 | the station-reachability CHECKS ignore the split and over-report | CONFIRMED, folded into the shadow-station work |
| CG5 | splitSides collapses on a null entry side | not a defect - unreachable, see below |

### CG1 - reserving a path is not placing a train, and my sweep could not tell them apart

The one-locomotive-per-place sweep (`a51a6eb`) assumed `setLocomotive` only ever places a train.
`configureAndLockPath` uses it to reserve every point along a locked path at once - which is how a
junction the train has passed is held against a second train reaching it another way. So locking
A->B->C swept the train off each point as the next was reserved, leaving it on C alone and freeing B.

Fixed by splitting the operation: `Point.reserve` assigns without sweeping, `configureAndLockPath`
reserves with it, and only genuine placement still sweeps. Pinned by
`testLockingAPathReservesEveryPointOnIt`, seen collapsing to the destination without the fix.

### CG2 - and on failure it stranded the train

Same root. `handleMisconfiguredPath` releases the path's end points and promises to leave the train
"at its start" - but the start had already been swept during locking, so the train ended on no point,
invisible to `pickPath` and dropped out of autonomy until a reload. The reserve fix restores the
start; `handleMisconfiguredPath` also re-reserves it defensively for the loop-back case.
`testAFailedConfigurationLeavesTheTrainAtItsStart` covers it, next to the path-integrity suite -
that class builds a UI and only runs with a display, so it runs in Adam's environment, not the headless
harness.

### CG3

The sweep and the assignment are not atomic; the comment claimed the worst a race could do was leave a
train nowhere for an instant, when the worst is two places - the very state the change forbids. Nothing
does that today (one thread places a given locomotive), and the comment now says so instead of claiming
a property the code does not provide.

### CG4 - the checks disagree with the runtime through a double curve

`AutonomyChecks.checkStations` and the sample-layout reachability helpers build their adjacency from
tile-to-tile reducer edges, which ignore the arrival-side split. A `FEEDBACK_DOUBLE_CURVE` carries
two independent curves; the tile adjacency lets a route cross between them, so the check reports a
station pair reachable that the split-aware runtime `bfs` never routes - and no `STATION_UNREACHABLE`
warning is raised. The editor's "test a path" is NOT affected: it uses `GraphReducer.findPath`, which
is split-aware.

CONFIRMED, Rank C - a missing warning in a narrow topology, not a train driven wrong. The correct fix
mirrors `findPath`'s (tile, arrival-side) BFS in the checker, which is the exact machinery the
shadow-station validation will either bless or delete. Folded into that task so the checker is not
rewritten twice.

### CG5 - the split derivation itself: a clean bill

The derivation reviewer could not construct a case where `nodesFor`/`leavesBy`/`arrivesBy`/
`onwardFrom` emits a wrong one-way edge, drops a legitimate route, produces a name collision, or makes
a copy that is wrongly trapped or unreachable. Ports, orientation, the (4-orientation) rotation, switch
state-replacement, portal stubs, double-curve confinement and naming uniqueness were all checked and
pinned by existing tests. `splitSides`' null-entry-side collapse is unreachable (a portal never
terminates a walk at a Point), so it is dead-defensive, not a bug.

That clean bill matters for the shadow-station question: the split is doing exactly the work it claims,
which is why retiring it is a re-architecture to carry heading in the search, not a deletion of dead
code.

## What this pass did not cover

- **No hardware.** Every finding is from reading code and running the headless suite. Semaphore
  rotation, real s88 timing, and anything needing a Central Station are untested here;
  `testAutoDetect` fails in this environment for want of one at 192.168.50.25.
- **No Swing rendering was looked at.** The arrival chevrons, the running-path line and the crowded
  caption were reasoned about, not seen. Their legibility at small tile sizes, and against dark tile
  art, is a manual check.
- **Import of a legacy graph carrying arrival restrictions** was not exercised - the legacy format has
  no such concept, so there is nothing to map, but the assertion is by inspection.
- **The regression pass did not run anything.** Every claim in it is from reading code and git history;
  A4, A5, A6 and B6 are open on that basis and want a person at the machine before they are called
  settled.
- **Nothing was checked against a Central Station.** The autosave and legacy-autonomy.json paths (D9)
  matter most to users who have one and no local layout folder, which is precisely the configuration
  none of these passes could exercise.
- **Concurrency was reasoned about, not stressed.** C1 was found by reading; no test drives an edit
  and a feedback burst against each other.
