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

---

## A - high

| id | finding | status |
|---|---|---|
| A1 | barring an arrival side of a turn-around station invalidates the whole configuration | fixed, `0e5c67a` |

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

## B - medium

| id | finding | status |
|---|---|---|
| B1 | a restriction naming a side the square no longer has locks the menu and cannot be cleared | fixed, `0e5c67a` |

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

## C - low

| id | finding | status |
|---|---|---|
| C1 | `StationIndex` derived lazily on whichever thread asked first | fixed, `0e5c67a` |
| C2 | `DiagramMonitorDriver` was a fifth hand-assembled builder | fixed, `0e5c67a` |
| C3 | `renamePage` orphaned captions and switched-off links | fixed, `0e5c67a` |
| C4 | arrival restrictions survived station demotion | fixed, `0e5c67a` |
| C5 | `arrivalSides()` rebuilt a whole builder per call | fixed, `0e5c67a` |

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

## D - not defects

| id | finding | status |
|---|---|---|
| D1 | the unreachable-station error named a coordinate, not a station | fixed, `0e5c67a` |
| D2 | `DiagramMonitor.indexEdges`/`indexPoints` were dead code with a trap in them | deleted, `0e5c67a` |
| D3 | `speakerAt`'s javadoc became false one commit later | corrected, `0e5c67a` |
| D4 | the right-click menu still answers for one train on a shared square | open - see below |

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
for the first. Not a defect in the changed code - it is the pre-existing single-train assumption,
newly visible because the caption no longer shares it. Left open deliberately: the menu's actions
(remove, facing) need a train chosen, and inventing a submenu for a case the user has hit once is
worth a decision rather than a guess.

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
