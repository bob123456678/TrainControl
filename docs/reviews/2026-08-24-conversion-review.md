# The FR-013 stage-1 conversion review: hunting the silent no-op

**Status:** open

**Prefix:** CR. Cite these findings as CR-C1 etc. from other documents.

**Reviewed:** `0c79bbe7` ("FR-013 stage 1: the store holds squares, not strings"), at the head of
`autonomy-diagram-r0`, on 2026-08-24. The whole range `1babf184..HEAD` was in scope; within that range
`AutonomyCompanionStore.java` was touched only by `0c79bbe7`, and that file got the depth. The other
five commits (the getPoints/getEdges revert, the two audits' fixes, the FR-019 backup dialog, the
FR-020 Central-Station backup) were read for correctness at the diff level, not exercised.

**Covered:** every call of `get` / `containsKey` / `contains` / `remove` / `equals` in
`AutonomyCompanionStore.java` with the static type of its argument established against the collection's
key type; the read boundary (`readSquare*` vs the deleted two-step); `toStored`/`fromStored` call
sites; the held-entries mechanism against the new readers; the public accessors' key forms; a
round-trip on the real sample layout; the javadoc attachments; six mutation experiments. **Not
covered:** the UI halves of the smaller commits were not executed (no EDT test was run); the full
99-class battery was not re-run (nine classes were, all green); `ui.testRenderingCost` ignored per
OB-084; AutonomySession and the graph layers were checked only at their calls into the store.

Nothing here was fixed. Every finding is open.

---

## The verdict first

**The conversion is sound.** The hunt this review was commissioned for - a `String` handed to an
Object-taking method of a now-TileKey-keyed collection, or a `TileKey` handed to something still
string-keyed - found **no live instance beyond the five the commit already fixed**. The sweep was
exhaustive over the file (the full call list is in D1), and the conclusion is backed by receipts
rather than by reading: six mutants that reintroduce the defect class at six different sites, five of
them caught by the suite (D8), one not (CR-C3 - a coverage hole, not a defect; the code at that site
is correct at HEAD).

The findings below are all C: dead code the conversion left behind, one genuine behaviour change at
the read boundary that needs a corrupt file to matter, one one-sided test guard, and one stale data
file. No setting is silently lost, reattached, or misrouted by the code as committed.

---

## A - high

None found.

## B - medium

None found.

## C - low

| Finding | Status |
|---|---|
| CR-C1 | open |
| CR-C2 | open |
| CR-C3 | open |
| CR-C4 | open |

### CR-C1: the conversion's dead leftovers, one of them a trap

The commit message says the old string surgery "did not move, it went". Most of it went; four pieces
stayed, all in `AutonomyCompanionStore.java`, all with zero callers:

- `untranslateTileMap` (~line 4018) and `untranslateSet` (~line 4065) - the commit deleted their
  call sites (`readShared`'s old eleven-call translate pass) but not the methods.
- `readStringListMap` (~line 4633) - replaced by `readSquareListMap`, never removed.
- `translateKeys(map, storing)`'s `storing == false` branch (~line 3996) - every caller passes
  `true`. The dead branch is also a trap: it would run `fromStored` over a MEMORY key, and
  `fromStored` resolves the page part through `pageOf` - so a page legally named "2" (Adam's ruling:
  it must stay legal) would be misrouted by exactly the id/name pun FR-013 exists to dissolve. If the
  branch is ever resurrected it resurrects OB-067.

Cosmetic residue in the same family: `reconcile`'s broken-pairings pass still collects
`entry.getKey().toString()` and re-parses it a few lines later (`unpairPortal(parseTileKey(key))` -
correct, since `parseTileKey` inverts `toString` exactly, but a pointless string round trip), and
several loops still carry the vestigial `TileKey tile = key; if (tile != null)` shape left over from
when `key` was a string being parsed (`getNamedTiles`, `getBarredArrivals()`, `getBlockingPoints`,
`applyTo`).

None of this is wrong behaviour. It is bulk for the next reader and one loaded gun.

### CR-C2: `readSquareListMap` manufactures empty lists the old reader refused, and they violate the version contract

**Receipt (run, not reasoned).** A `setup.json` whose `stationSignals` holds one entry with a value
that does not parse as a square (`"1:3,4": "not-a-square"`), loaded and saved through the store:

```
getProtectingSignals: []
version written: 1
stationSignals after save: {"1:3,4":[]}
```

Two behaviour changes against the old boundary, one of them contract-breaking:

1. The old pair (`readStringListMap` + `untranslateTileListMap`) kept an unparseable member verbatim
   as a string and wrote it back for ever. The new reader drops it - defensible, and its javadoc says
   so - but when it was the entry's ONLY member the result is an entry with an EMPTY list, which the
   old reader explicitly refused to create (`if (!values.isEmpty()) into.put(...)`). Nothing in the
   live API can create an empty list any other way (`setProtectingSignals`, `setBlockingPoints`,
   `forgetSquares` and `reconcile` all remove-on-empty); this is the one door.

2. `translateTileListMap` writes an empty list as `[]` (its one-is-a-bare-string rule only fires at
   size 1), while `versionWritten()` returns 2 only for `size() > 1`. So the file above carries an
   array while stamped **version 1** - and the VERSION javadoc's whole reason for existing is that a
   version-1 reader "reads that field with a string accessor and throws an unchecked exception on an
   array - after load() has already emptied the store". The invariant "a version-1 file contains no
   arrays" is broken exactly and only by this empty list.

Reachability, honestly: it takes a corrupt or hand-edited value first, and the first `reconcile` in a
session removes the empty entry (its `kept.isEmpty()` branch). The exposure is the save paths that do
NOT reconcile - `repairOnDisk` (a locomotive rename against a setup nothing has open) round-trips the
empty entry indefinitely - plus a pre-3.0.0 build ever reading the file. C, not B, by "distinguish
could from does". The one-line fix is the old reader's guard: don't put an empty list. `blockedPoints`
shares the reader and the shape.

### CR-C3: reconcile's orphan-station guard is one-sided - the battery cannot see over-deletion

The code at HEAD is correct; this is a hole in what guards it, in the defect class this store keeps
losing data to (deletion by reconciliation).

**Receipt.** A mutant of `reconcile`'s orphan-station loop that asks `keys.contains` with the printed
form (`!keys.contains((Object) key.toString())` - always true, so every unnamed station is treated as
"tile gone" and dropped) passed, in full: `core.testAutonomyDiagramStore` 64/64,
`regression.testAutonomyStoreSettingsMatrix` 7/7, `core.testAutonomyDiagramSession` 85/85,
`regression.testAutonomyTileMove` 16/16, `regression.testStationLabelsFollowMoves` 12/12 - 184 tests
green while reconcile silently deletes every unnamed station whose tile still exists. A direct probe
confirms the difference: HEAD keeps the station and reports clean; the mutant drops it and reports
`station at main:3,4`.

The mirror mutant - the no-op direction, `stations.remove((Object) key.toString())` in the same loop -
IS caught (`testAnUnnamedStationGoesWhenItsTileDoes`, the fifth of the commit's five fixes). So the
guard asserts that the deletion happens, and nothing asserts it happens only to the right squares.
`testAnUnchangedDiagramReconcilesCleanly` would catch it if its fixture contained an unnamed station;
it does not. The settings matrix has no reconcile column at all - its operations are move, build-over,
restore, rename, save/load.

Suggested shape (not done here): a station with no name on a tile that still exists, asserted to
survive `reconcile` with a clean report - one test, and it turns this review's strongest surviving
mutant red. A reconcile column in the matrix would be the systematic version.

### CR-C4: the sample layout's setup.json is stamped version 1 while carrying a two-signal array

`cs2_sample_layout/config/autonomy/setup.json` at HEAD has `"5:20,13": ["5:8,12", "5:21,13"]` under
`stationSignals` and `"version": 1`. That state predates this review's range (it is in `1babf184`,
and the file's last commit is 345f4764, the hand-restore after the page-rename loss), so it is not
this commit's defect - but it is the on-disk instance of the contract CR-C2 describes: a version-1
reader of this file throws after emptying the store.

The current code is not the problem: `versionWritten()` correctly answers 2 for this data, and the
round-trip receipt (D2) shows the next save re-stamps the file to version 2. When that diff appears
in the working tree, it is expected and correct - recorded here so it is not mistaken for the store
inventing a version bump.

---

## D - not defects: what was checked and found sound

| Check | Result |
|---|---|
| D1 sweep of Object-taking calls | clean |
| D2 round trip on the real layout | clean (see CR-C4 for the version diff) |
| D3 held-entries mechanism | clean |
| D4 toStored/fromStored callers | clean |
| D5 javadoc attachments | clean |
| D6 printed-key accessors and parse round trip | clean |
| D7 configuration keys and the '#' handling | consistent |
| D8 the attacks that failed | 5 of 6 mutants caught |
| D9 the five smaller commits | no findings at diff level |
| D10 view-to-copy accessor changes | no caller affected |

### D1: the sweep

Every call in `AutonomyCompanionStore.java` of `Map.get`, `Map.containsKey`, `Map.remove`,
`Set.contains`, `Set.remove`, `List.contains`, and every `equals` with a `TileKey` on either side, was
read with the argument's static type established. The TileKey-keyed ten (`pointNames`, `stations`,
`tileLengths`, `barredArrivals`, `stationSignals`, `blockedPoints`, `portals`, `captions`,
`linkNames`, `disabledPortals`) are queried with `TileKey` everywhere, including the subtle sites:
`forgetSquares`'s four value-pruning loops, `reconcile`'s three passes, `moveTiles`'s landing-set
construction, the caption-spared-by-its-own-station test (`key.equals(arriving.get(names))`), and both
configuration-points loops (parse first, then `byKey.containsKey(tile)`). The string world
(`tileDirections` suffixed keys, `excludedPages` page names, `configurations`, the four page-id maps,
`heldForAbsentPages`, JSONObject keys) is queried with strings everywhere, and every crossing between
the worlds goes through `parseTileKey`, `toString`, or the suffix-aware `isOnPage(String, ...)` /
`rekeyOne(String, ...)`. The five fixes named in the commit message are all present. No sixth
instance was found. The compiler protects everything outside the file: no public signature changed in
this commit, so no caller could have been left holding the wrong form.

### D2: the round trip (the receipt the task asked for)

A scratch harness copied `cs2_sample_layout`'s autonomy folder, loaded it through the store with the
page numbering the file was written under (the `repairOnDisk` pattern), and measured:

```
pages=5 pointNames=47 captions=35 tileDirections=63 stations=33
save1==save2 bytes: true
```

The counts match the layout's known contents exactly. Save-load-save is **byte-identical**. The first
save differs from the original file in precisely three ways, all benign: `blockedPoints` and
`linkNames` appear as `{}` (the original predates those fields), and `version` goes 1 -> 2 - which is
CR-C4's stale stamp being corrected, not a translation fault. Every key in every square-keyed field
round-tripped identically, id-form out and id-form back.

### D3: held entries never reach a reader, and every Held shape matches its reader

Verified structurally: `readShared` reads the copy `withoutAbsentPages` returns, so a held entry is
filtered out before any `readSquare*` call sees the object; `mergeHeld` writes held entries back only
where the live save has no entry under that key, and live and held keys cannot collide (a held page's
id is by definition not a live page's id). All twelve `HELD_FIELDS` shapes were checked against their
readers one by one - `tileDirections` as PLAIN with its suffixed key handled by `allHere`'s
last-colon split, `portals`/`captions` as SQUARE_VALUE with the value checked, the two list-valued
fields with both string and array forms checked, `excludedPages` as PAGE_LIST through `pageIsHere`
directly. `regression.testPageIdsAreDurable` (which SV-C1 hardened for exactly this mechanism) is
green at HEAD: 11/11.

### D4: toStored / fromStored

Every `toStored` caller (`translateSuffixedKeys`, `translateKeys(_, true)`, `translateTileListMap`,
`translateTileMap`, `translateSet`, `translateLengths`, `translatePortals`) feeds it the printed form
of a live memory key, and all are reached only from `sharedFields`. Every `fromStored` caller (the
five `readSquare*` readers, `untranslate` for tileDirections) feeds it a key straight off a file or a
snapshot in stored form, exactly once per fill - each fill is preceded by a clear (`load`,
`restoreSetup`, `importBundle` all clear first). No caller passes an already-stored key and nothing
translates twice; the only door to a double translation is the dead branch in CR-C1.

### D5: the javadocs

`regression.testJavadocsAreAttached` is green at HEAD (the ratchet holds at <= 98). An adjacency scan
of the whole file - every doc block against the declaration that follows it, `@param` names against
signatures - found the docs beside this commit's insertions all attached to the method they describe:
both `isOnPage` overloads, both `rekeyOne` overloads, the four `*Suffixed` helpers, the five
`readSquare*` readers, `asStrings`, `dropMissingMembers`, `dropMissingSuffixed`,
`translateSuffixedKeys`. The four stacked double-javadocs in the file (above `getNamedTiles`,
`forgetTiles`, `isOnPage(TileKey,...)`, `reconcileCaptions`) all predate `1babf184` verbatim and are
the governed, ratcheted kind - not this commit's orphans.

### D6: the printed-key accessors

`getPointNames()` deliberately returns printed keys, per Adam's rule; its one consumer
(`AutonomySession.tileForPointName`) parses each key back with `parseTileKey` and uses it immediately.
No other public accessor returns the wrong form: `getExcludedPages` is page names by design, the
`Reconciliation` lists are printed strings for a human by design (and say so), and everything else
speaks `TileKey`. `parseTileKey` inverts `TileKey.toString` exactly - last-colon and last-comma
splitting survives page names containing `:`, `,` and `#`, and negative coordinates - so the printed
channel cannot corrupt a key.

### D7: configuration keys never carry a '#'

`setPointProperty` and `captureFromLayout` both key configuration points by `tile.toString()` - bare
`page:x,y`, never suffixed. So `deletePage`'s `lastIndexOf('#')` strip in its configuration loop is
defensive-only, and `moveTiles`'s configuration loop parsing keys without a strip is not a
discrepancy: no suffixed configuration key exists to be missed.

### D8: the attacks that failed (and where each was finally caught)

Six mutants of the store, each reintroducing the printed-form no-op at a different site, compiled to a
scratch directory and run against the suite. **This is the calibration table for trusting D1:**

| Mutant site | Store suite (64) | Matrix (7) | Caught by |
|---|---|---|---|
| `captionsFor` printed-equals (commit fix #3, control) | **3 fail** | - | store suite, caption tests |
| `forgetSquares` tileDirections `contains` (commit fix #2, control) | pass | **1 fail** | matrix, built-over cell |
| `forgetSquares` portals-VALUE pruning | pass | pass | **testDiagramShiftKeepsSetup** (stale far-end partner: "expected [null] but found [1 - Main:2,4]") |
| `forgetSquares` captions-VALUE pruning | pass | pass | **testStationLabelsFollowMoves** |
| `reconcile` orphan-station `contains` (over-delete direction) | pass | pass | **nothing** - CR-C3 |
| `reconcile` orphan-station `remove` (no-op direction, commit fix #5, control) | **1 fail** | pass | testAnUnnamedStationGoesWhenItsTileDoes |

Five of six die somewhere in the battery, though notably three of them survive the two suites closest
to the store and are caught only by the editor-level regression classes - the guard against this
defect class is spread thinner than the store suite alone suggests. The sixth is CR-C3.

### D9: the five smaller commits

Read at diff level, no findings: the `getPoints`/`getEdges` revert (5ad62001/67ce9f84) is a true
revert to the code that ran all week, the AB-BA analysis in its comment matches the call chains named,
and DR-B7's hazard is explicitly left open and filed rather than silently dropped; `readIndexLines`'s
ISO-8859-1 fallback is total, so the SV-B1 permanent-refusal path is closed; `askOnEventThread` guards
the on-EDT `invokeAndWait` deadlock and treats an unanswerable question as "no"; the FR-020 temp
download is removed in a `finally` on every path, including the failure path before the archive
write; the FR-019 Explorer launch runs off the EDT; the eight message-bundle additions across the
range are pure ASCII (checked byte-wise, per the properties rule).

### D10: view-to-copy changes

`getPointNames()` changed from a live unmodifiable view to an unmodifiable copy. Its sole consumer
iterates immediately; no caller held the view across store mutations, so no behaviour depends on the
old liveness. The other accessors that build copies (`getCaptions`, `getBarredArrivals()`,
`getProtectingSignals()`, `getBlockingPoints()`) already did so before this commit.

---

## Dispositions

**Claude, 2026-08-24.** All four acted on; three fixed, one noted.

| | What was done |
|---|---|
| **CR-C1** | Fixed. `untranslateTileMap`, `untranslateSet` and `readStringListMap` are gone - each had exactly one occurrence, its own declaration. `translateKeys` is storing-only: its `storing=false` branch called `fromStored` on a key that is already a memory key, which is the OB-067 pun waiting for a caller, and no caller ever passed false. |
| **CR-C2** | Fixed. `readSquareListMap` drops an entry whose members all failed to parse rather than storing it empty. Storing it empty writes `"key": []`, and an array is the form this file only gained at version 2 - so a setup stamped version 1 would go to disk carrying one, which is precisely what the version gate exists to keep away from an older TrainControl. The pair readers already drop a half-parsed entry; an empty signal list is the same thing said differently. |
| **CR-C3** | Fixed, and it was the best finding here. No fixture anywhere had a live UNNAMED station, so the orphan rule was pinned in one direction only and a mutant that strips the designation off every unnamed square passed 184 tests across five classes. `testAnUnnamedStationGoesWhenItsTileDoes` now has one, and that mutant fails on it. |
| **CR-C4** | Noted, not changed. The sample layout on disk is stamped version 1 while carrying a two-signal array; it predates this range and the next save re-stamps it correctly. Recorded so the diff is not read as a regression later. |

### What this pass is worth beyond its findings

Zero A and zero B on a wide conversion of the class holding the user's setup, with the commissioned
hunt coming back empty beyond the five the commit already fixed - and, more usefully, a **round trip on
the real 502-tile layout: save, load, save, byte-identical**, with all five pages, 47 point names, 35
captions and 63 tile directions intact.

Six mutation receipts, and the two most interesting are the ones that SURVIVED the store-adjacent
suites: pruning the portals value and the captions value were caught only by `testDiagramShiftKeepsSetup`
and `testStationLabelsFollowMoves`. That is worth knowing before anyone decides which classes are
enough to run.
