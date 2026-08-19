# Disposition audit of the autonomy-diagram cycle, and the two commits that closed it

**Prefix for citing this document: `DA`.**

Reviewed at `bf11048` on branch `autonomy-diagram-r0`, 2026-08-17.

**`DA-A1`, `DA-A2`, `DA-B1` and `DA-B5` were fixed the same day**, each with a test written and seen to
fail first. Everything else below is open. Section status tables carry each finding's disposition.

`DA-A1`'s fix deleted the branch it was reporting on rather than adding a third case to it: the file's
rotation is now derived from the type word being written, which reproduces the original number exactly
and cannot disagree with a second rule. `orientationMatchesFile` and the preserved rotation went with
it.

## Scope and method

Two questions, neither of which is "is the new code correct":

1. **Do the dispositions hold?** [2026-08-17-autonomy-diagram-review.md](2026-08-17-autonomy-diagram-review.md)
   (`AD`) records 45 findings from eleven passes and marks every one Fixed. For each, three checks:
   does the fix address what the finding *describes* rather than something adjacent; is it complete;
   and does it introduce anything. The completeness check was run as an explicit grep for sibling call
   sites, because that is the failure this repository's review README names as its most repeated
   mistake - five times in the July cycle, twice while fixing an earlier instance of it.
2. **Did anything that worked before stop working?** `ddff66e` and `bf11048` against `8cee6a3`,
   looking for behaviour changes rather than defects - particularly on the parse and export path,
   which the autonomy work changed and the ordinary diagram editor shares.

The A findings, the store/session/file layer, both commits and all eight message bundles were
verified by hand.

**Verdict: no disposition is outright wrong.** Every `AD` fix exists and addresses its described
failure. Five are incomplete in the specific shape the README predicts - a guard added to one call
site and not its twin - one has an unfixed sibling in an adjacent method, one is sound but its error
reporting regressed, and one covers its described scenario with a residual beside it. Two of the
cycle's own fixes introduced new defects, holding the rate the earlier rounds set.

| | Count |
|---|---|
| Dispositions sound | 38 of 45 |
| Incomplete - unfixed twin | 5 (`AD-A2`, `AD-A3`, `AD-A6`, `AD-B8`, `AD-C15`) |
| Sound, with an unfixed sibling elsewhere | 1 (`AD-B3`) |
| Sound, reporting regressed | 1 (`AD-B12`) |
| Sound for the described scenario, residual beside it | 1 (`AD-A5`) |
| New defects introduced by the cycle's fixes | 2 (`DA-A1`, `DA-B5`, both from `ddff66e`) |

`DA-A1`, `DA-A2`, `DA-B1` and `DA-B5` are the ones worth fixing before merge. `DA-A1` and `DA-A2`
lose or corrupt layout data through ordinary editor use, by users who have never touched autonomy.

---

## A - High

| | Finding | Status |
|---|---|---|
| A1 | The `AD-A3` fix mis-rotates semaphore signals in the two cases its test does not cover | **Fixed 2026-08-17** |
| A2 | Editor undo, redo, copy and move strip the data the `AD-A2`/`AD-A3` fixes depend on | **Fixed 2026-08-17** |

### A1. The signal-rotation fix is asymmetric, and one half of it is a regression

The parser subtracts 1 from `drehung` for any type word containing `_f_`
(`marklin/file/CS2File.java:2319-2322`), correcting semaphore artwork on the way in. The `AD-A3` fix
preserves the original type word on the way out (`base/LayoutDiagramComponent.java:846-849`) and
writes back the original rotation - but only when `originalDrehung != null && orientationMatchesFile()`.
The `else if` at line 860 writes `this.orientation`, which is in **corrected** space, under a word
that re-triggers the correction on the next load.

Two triggers, neither covered by `testASignalKeepsItsExactTypeAndRotation`, which tests only the
drehung-present-and-unedited case:

- **No `.drehung` key**, the CS2 default for rotation 0. Parse gives orient 0, the `_f_` correction
  makes it 3, the first ordinary Save takes the `else if` and writes `.drehung=3`, and the next load
  subtracts again to show 2. A quarter turn from a single save, on the diagram and on the Central
  Station - which is `AD-A3`'s own failure, still live.
- **A rotated `_f_` signal.** `orientationMatchesFile()` is false by construction once the user
  rotates, so the corrected-space number is written under the correcting word and the signal comes
  back one step off.

The second is a **regression this fix introduced**. Before the branch the type word was canonicalised
to `signal`, which contains no `_f_`, so no correction fired on reload and the orientation survived -
at the cost of the variant word. The fix traded a known loss for an unknown one.

Verified by hand: the parse correction, the write branch, and that the preserved word and the
corrected value are written together. `AD-A3: incomplete.`

### A2. Undo, redo, copy and move strip the verbatim data, so "undo, then Save" deletes every unmodelled key on the page

`LayoutDiagramComponent`'s copy constructor (`base/LayoutDiagramComponent.java:105-110`) delegates to
the base constructor and then copies `label` - and nothing else. It copies neither `originalTyp`,
`originalDrehung`, nor `unmodelledKeys`, which are precisely the three fields `AD-A2` and `AD-A3`
added to stop data being lost.

`LayoutEditor.deepCopyLayout` snapshots through that constructor, and `undo()`/`redo()` replace
**every** component on the page with the stripped copies; `execCopy` does the same for a moved or
copied tile.

Failure scenario: open the diagram editor on a page that has never touched autonomy, place a tile,
press undo, press Save. Every key a later CS firmware wrote on any modelled component is deleted from
the file, every signal and lamp variant word collapses to canonical, and `DA-A1`'s rotation drift
engages on top - with nothing asked and nothing reported. This is the `AD-A2` loss class
reintroduced through the most ordinary editor gestures there are.

Verified by hand: the copy constructor's body. The whole-file unmodelled elements and blocks held on
`LayoutDiagram` do survive - the fix is correct for the parse/export pair itself, which is why this is
"incomplete" rather than "wrong". `AD-A2: incomplete.`

---

## B - Medium

| | Finding | Status |
|---|---|---|
| B1 | `AD-A6`'s gate covers the Autonomy menu but not the banner's Load button | **Fixed 2026-08-17** |
| B2 | `AD-B8`'s single gate is cleared underneath an open editor by three unguarded paths | **Fixed 2026-08-18** - the three paths refuse while an editor is open |
| B3 | `AD-B3`'s fix has an unfixed sibling in the one-way-run tracer | **Fixed 2026-08-18** - the undirected walk carries the side it arrived by |
| B4 | `AD-B4`'s residual: INFO findings are counted in one place and not the other | **Fixed** before 2026-08-18 - INFO is bucketed with notices in both counts |
| B5 | The caption migration rewrites every page on every launch when a label matches nothing | **Fixed 2026-08-17** |

### B1. `AD-A6`: the menu is gated, the banner button is not

`AutonomyMenu.rebuild()` refuses while an editor holds the diagram, and `isLayoutEditorOpen()` has no
other caller. But the banner shows exactly when a setup exists and nothing is loaded, and its Load
button calls `AutonomyViewerPanel.load()`, which saves the session and rebuilds the main window.

Failure scenario: open the ordinary diagram editor - the banner is still visible on the main window -
and click Load. The save reconciles the setup against the half-edited diagram, dropping names and
captions for tiles the user deleted and intended to Cancel, and the main grid rebuilds with the shared
diagram's edit flag set, giving the same tile-parent `ClassCastException` `AD-A6` describes.
`AD-A6: incomplete.`

### B2. `AD-B8`: three `layoutEditingComplete` paths clear the gate

`layoutRefreshComplete` unconditionally re-enables the Edit button and resets the session, and it is
reached from `RouteEditor` saving a route with tiles, from deleting a diagram page, and from the
legacy external editor path. None checks `isLayoutEditorOpen()`.

Failure scenario: with an autonomy editor open, edit and save a route from the still-live main window,
then press Edit. A second editor opens on the same diagram, and closing either unsets the other's edit
flag - the exact `AD-B8` failure. `AD-B8: incomplete.`

### B3. `AD-B3`'s sibling in the one-way tracer

`AD-B3` itself is sound. But `TileGraph.findUndirectedPath`/`undirectedNeighbours` union the sides of
all routes of a square, so a traced path can jump between tracks at a crossing or double curve, and
`applyOneWay` then silently skips the jump square and one-ways a "run" that is not continuous track.
Failure scenario: two independent tracks crossing at one square - drawing a one-way from track 1 to
track 2 reports success and restricts stretches of both.

### B4. `AD-B4`'s residual: INFO findings

`checkClosedRuns` emits `RUN_CLOSED_BOTH_WAYS` as INFO; the editor buckets everything that is not
ERROR or NOTICE as a warning, while the diagram strip counts only ERROR and WARNING. Close one tile's
routes both ways and the editor shows "1 warning" with an amber banner while the diagram strip shows
nothing. Both originally-described defects are fixed; this is the same class at smaller scale.
`AD-B4: incomplete.`

### B5. The caption migration rewrites every page on every launch, forever, when any label matches nothing

`AutonomySession.migrateStationLabels` saves the store and then calls `saveChanges` for **every** page
containing a `Point:` label - including pages whose labels all failed `tileNamed` and were therefore
deliberately left in place. Because they are left in place, they are found again next time.
`getAutonomySession()` runs `open()` lazily but effectively at every launch, reached from
`refreshAutonomyPrompt`.

Failure scenario: a user who has **never used the new autonomy** but has legacy `Point:` labels gets
`config/autonomy/setup.json` created and their gleisbilder files rewritten on every start. Under a
OneDrive sync lock that is an `errorLabelWriteFailed` dialog on every launch that no UI action can
resolve. The sample layout's four orphan labels make this the shipped default.

Verified by hand: the unconditional `store.save()`, and the per-page `saveChanges` outside the
skip. Introduced by `ddff66e`.

---

## C - Low

| | Finding | Status |
|---|---|---|
| C1 | `AD-C15`'s twin: the Manage popup still offers the raw graph export | **Fixed** before 2026-08-18 - gated on a setup existing and no blocking problems |
| C2 | `AD-A5`'s residual: configuration files are read after `clear()` | **Fixed 2026-08-18** - configurations are read before anything is cleared |
| C3 | `AD-B12` reports the failure as a bare filename, and left dead code | **Fixed 2026-08-18** - a sentence instead of a filename, dead branch removed |
| C4 | Deleting the running configuration lacks its sibling's busy check | **Fixed** before 2026-08-18 - `delete()` refuses while autonomy is busy |
| C5 | Stale javadoc contradicts the `AD-A8`/`AD-C13` fixes | **Fixed** before 2026-08-18 - the javadoc matches the code |
| C6 | Dead overload, undeduplicated warning, cubic label rebuild | **Fixed 2026-08-18** - dead overload removed; the cubic label rebuild is deferred |

**C1.** The Auto tab's Manage popup still offers "Export raw graph as JSON" gated only on debug, in
the two states the `AutonomyMenu` fix's own comment forbids - no setup, or blocking errors.
`AD-C15: incomplete.`

**C2.** `AD-A5` moved parsing before `clear()` for setup.json, but the configuration files are read
*after* `clear()` in the same `load()`. A locked `configuration-*.json` - the finding's own OneDrive
scenario - still leaves a live, partially-loaded store, and a corrupt one throws an unchecked
`JSONException` that escapes every `catch (IOException)` around `discardEdits` and `open`.
`AD-A5: sound for the described scenario, with this residual.`

**C3.** `deleteConfiguration` throws `IOException(file.getName())`, which the caller shows as the
entire dialog text - the user sees a dialog saying only "configuration-Yard.json". The
`ERROR_LAST_CONFIGURATION` comparison beside it is now unreachable, since the store no longer throws
it. `AD-B12: sound`, reporting regressed.

**C4.** `AutonomyViewerPanel.delete()` has no `isAutonomyBusy()` guard, while its sibling
`AutonomyMenu.deleteEverything` refuses when busy. Confirming a delete mid-run goes through
`autonomySetupDeleted` to `clearAutoLayout` and stops moving trains.

**C5.** `AutonomySession.discardEdits`' javadoc still claims captions "live on the track diagram...
written to the layout file the moment they are set". False since `ddff66e` - captions live in the
store and *are* discarded. The README's rule about leaving reasoning where the next person trips over
it cuts both ways: a comment that outlived its code is worse than none.

**C6.** The page-less `autonomyStationAt(int,int)` overload is dead and would reintroduce `AD-B7` if
called; `WARN_PARALLEL_ROUTE` can be emitted once per losing fork with no dedup; the `AD-B6` label
path rebuilds `AutonomyBuilder` and `uniqueNames()` per point per feedback event, which is roughly
cubic on the feedback path - one for the deferred-optimisations list rather than a defect.

---

## D - Checked and clean

**The message bundles.** All eight parse as Properties with zero problems: pure ASCII, no malformed
`\uXXXX`, no truncated or folded values, no duplicate keys, identical 1485-key sets, and every `{n}`
placeholder matching the English file and its `I18n.f` call-site argument count. No lone MessageFormat
apostrophes in any formatted value. One harmless extra argument at `AutonomyViewerPanel.java:1051`,
which MessageFormat ignores. This was the highest-risk area of the cycle - these files were edited by
script three times, and one of those edits corrupted values in a way that took two attempts to
repair - so it is worth recording that they now check out clean by script verification rather than by
eye.

**Ordinary Save from the diagram editor.** The output deliberately differs from pre-branch: variant
type words, exact rotations, unmodelled keys, blocks and whole elements are now preserved instead of
deleted, and the CS2 array shape is restored on write. Unmodelled elements are re-emitted at the end
of the file, which is an ordering change only, the format being coordinate-keyed. Apart from `DA-A1`
and `DA-A2` above, this is strictly higher fidelity and not a regression.

**`clearAutoLayout` and `ViewListener`.** Exactly one implementer exists, so the new interface method
breaks nothing; it stops locomotives before discarding; every `getAutoLayout()` call site checked is
guarded by `hasAutoLayout()`; menu, banner and tab states all handle the unloaded state.

**`Util.backupFolder` and the menu action.** Cycle guard by canonical path, depth cap 64, per-file
best effort with full paths reported, button re-enabled in a finally, and the copy now covers
`config/` - diagrams and autonomy both. `AD-B13` and `AD-C10` sound.

**`deleteConfiguration` allowing the last delete.** The callers cope with the empty state: the menu
collapses to the single add item, the banner requires a non-empty name list, and `delete()` unloads
the running graph when the set empties. The semantics change is safe.

**Dispositions verified sound:** `AD-A1` (`KNOWN_SHARED` now lists all twelve fields `save()` writes),
`AD-A4`, `AD-A5` (core), `AD-A7`, `AD-A8`, `AD-A9`; `AD-B1`, `AD-B2`, `AD-B5`, `AD-B6`, `AD-B7`,
`AD-B9` (a fragile double-`invokeLater`, but correct), `AD-B10`, `AD-B11`, `AD-B13`; `AD-C1` through
`AD-C14`, `AD-C16`, `AD-C17`. The `AD` document's own D section is consistent with the code.

---

## What this pass missed

**The audit trusted `AD`'s prose for what each finding *was*.** Where the recorded description is
imprecise, a fix judged sound against that description could still miss what the original reviewer
actually saw. The `AD` document is a historical record of what was believed at the time and says so;
this pass inherits that limit.

**Runtime behaviour was not exercised.** Every claim rests on reading, per the standing no-CLI-builds
constraint. `DA-B5` in particular predicts a per-launch file rewrite that a single run with a
filesystem watch would confirm or kill outright, and that check is worth more than the argument for
it.

**The C findings were audited at lower depth than A and B, by instruction.** Seventeen `AD` C
findings were confirmed to exist and address their description; their sibling call sites were not
grepped with the same rigour applied to the A and B fixes. Given that `AD-C15` turned out to have an
unfixed twin anyway, the C tier probably holds one or two more of the same shape.

**The two-construct question was not asked.** This pass checked each fix against its own finding. It
did not ask whether the eleven passes' fixes interact - `DA-A1` and `DA-A2` compound, and that was
noticed only because both landed in the same file. Nothing systematically looked for other pairs.


---

## Re-checked 2026-08-18

Every row above is now closed. Four of them - B4, C1, C4, C5 - turned out to have been fixed already
and never marked, which is its own small lesson: a review that keeps reporting settled work as
outstanding is read as current and quietly wastes the next person's afternoon.

Five needed doing. B2 was the one with teeth: `duplicateOrRenameCurrentLayout` and both places a route
editor opens now refuse while an editor holds the diagram, rather than finishing by re-enabling the
Edit button and rebuilding the autonomy session underneath one. B3 was next: `findUndirectedPath`
walks `(square, side it arrived by)` instead of squares, so a one-way run drawn between two tracks that
merely cross can no longer restrict stretches of both and report success. C2 reads every configuration
file before anything is cleared, so a locked or corrupt one leaves the setup exactly as it was - and
arrives as an IOException, which is what the callers catch. C3 and C6 are tidying.

Pinned by tests: the crossing and the switch for B3, a corrupt configuration for C2. The rest are
structural or wording and are covered by the suite continuing to pass.

Left deferred: the cubic label rebuild inside C6, which belongs on the optimisation list rather than
here - it is slow, not wrong.
