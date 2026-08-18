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

---

## A - high

| id | finding | status |
|---|---|---|
| A1 | barring an arrival side of a turn-around station invalidates the whole configuration | fixed, `d4cc22a` |
| A2 | excluding a page permanently destroyed the arrival restrictions on it | fixed, next commit |

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

## B - medium

| id | finding | status |
|---|---|---|
| B1 | a restriction naming a side the square no longer has locks the menu and cannot be cleared | fixed, `d4cc22a` |
| B2 | a train on a non-destination copy had no right-click menu at all | fixed, `81a51ac` |
| B3 | with no train there, the menu still hung off whichever copy sorted first | fixed, next commit |
| B4 | a locomotive could be placed at random onto a barred copy | fixed, next commit |

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

## What this pass did not cover

- **No hardware.** Every finding is from reading code and running the headless suite. Semaphore
  rotation, real s88 timing, and anything needing a Central Station are untested here;
  `testAutoDetect` fails in this environment for want of one at 192.168.50.25.
- **No Swing rendering was looked at.** The arrival chevrons, the running-path line and the crowded
  caption were reasoned about, not seen. Their legibility at small tile sizes, and against dark tile
  art, is a manual check.
- **Import of a legacy graph carrying arrival restrictions** was not exercised - the legacy format has
  no such concept, so there is nothing to map, but the assertion is by inspection.
- **Concurrency was reasoned about, not stressed.** C1 was found by reading; no test drives an edit
  and a feedback burst against each other.
