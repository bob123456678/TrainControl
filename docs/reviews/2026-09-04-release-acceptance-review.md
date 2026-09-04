# The acceptance review: is 3.0.0 safe to release

**Status:** open

**Prefix for citing these findings elsewhere:** `ACC`

**Reviewed:** branch `autonomy-diagram-r0`, on 2026-09-04, at `489439fa` (two commits past
`v3_0_0_rc12`) - and the railway moved while it was reviewed: `e2afe88c` (OB-172, OB-173, DAY-C2)
was committed mid-pass. Every cited line was read from the tree WITH those edits present, so the
line numbers hold at `e2afe88c`; the commit's own code was NOT reviewed (only its message was read
and its non-overlap with the cited regions confirmed - ACC-D13). `cs2_sample_layout/` was read and
never written; every test ran through `one.sh`. TrainControl itself was running on the live railway
throughout, which mattered once (ACC-D7).

**Scope asked for:** (1) do the 218 commits since 2026-08-28 hold together; (2) regressions against
2.8.1 (`master`) and against the 2026-09-02 fixing round (`1cfdf370..`). Priorities: data loss, a
train commanded onto track it should not be on, a setting silently discarded, a 2.8.1 capability
gone. Coverage was narrower than intended - ACC-D13 says exactly where the edges are.

---

## Verdict

**Not yet - one A-grade defect must be fixed first, and it is small.** ACC-A1: the 2026-09-03
"stop, alert, then unlock" ruling was wired into `executePath`'s failure handler unconditionally,
so a failure during the LOCKING phase is now recovered twice - the second recovery erases the
failed train from the model (the square it is physically standing on reads as empty, and
`pickPath` can route another train into it) and decrements lock claims other running trains hold on
shared throats. Every layer of that chain was verified by reading; the fix is a gate in one
handler. After that, the rest of the list is short: **(2)** run the departure half of
[MT-250](../manual-tests/tests.md#mt-250) on the railway - the `mustBackIn` ruling now sends
EN57-203 nose-first into TunnelLongPark, and whether it can leave again is the priced, still-unrun
residual of that ruling (ACC-D9); **(3)** two rulings for Adam that are cheap to make and wrong to
make silently: ACC-B2 (the editor-close reload lost the emergency stop the VD11-A2 census never
weighed) and ACC-B3 (a setup edit declined during a run can be silently reverted by the exit
capture, and the decline message promises the opposite); **(4)** decide ACC-B1 - a 2.8.1
upgrader's hand-written `lockedges` are dropped by the legacy import without being counted in the
"left behind" report, and Adam's own legacy file carries 116 of them; measure the residue or add
the fifth line to the report; **(5)** commit `e2afe88c` (OB-172/OB-173/DAY-C2, landed mid-review)
postdates every review including this one - it needs a read before the tag; only its commit
message was read here.
Everything else here holds: the `mustBackIn` removal is confined to manual Return Home exactly as
claimed (ACC-D1), the bundles are clean (ACC-D4), no capability or contract regression against
2.8.1 was found by exhaustive set-diff (ACC-D14), and 299 tests across eight classes ran green with
zero skips (ACC-D7). The C findings can follow the release, with one worth a release-note line:
downgrading to 2.8.1 destroys locomotive keyboard pages 11-50 (ACC-C8).

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|
| A1 | A | A lock-phase failure is recovered twice: the ruled unconditional unlock in `executePath`'s handler re-releases a path `configureAndLockPath` already gave back, erasing the failed train from the model and decrementing other trains' lock claims | `Layout.java:5180, 3028, 3232, 3409`, `Edge.java:525` |
| B1 | B | Legacy import silently drops hand-written `lockedges` - the one dropped field missing from `whatALegacyImportLeaves`'s report; 116 refs in Adam's own 2.8.1 file | `AutonomySession.java:523,916`, `Layout.java:8165`, `GraphReducer.java:1219` |
| B2 | B | The VD11-A2 stop-narrowing missed a caller its own census never counted: the reload after the track-diagram editor closes gets no emergency stop, though its geometry was just replaced under any hand-throttled train | `TrainControlUI.java:3586, 20298` |
| B3 | B | A setup edit declined during a run can be silently reverted by the exit capture, and the decline message promises "it will be picked up the next time the setup is loaded" | `TrainControlUI.java:5490, 2248`, `AutonomySession.java:1982` |
| C1 | C | Three comments still describe the removed `mustBackIn` rule as if it stood | `Layout.java:7879`, `AutonomyBuilder.java:581`, `AutonomyChecks.java:727` |
| C2 | C | The parity report's four "missing route" rows are deliberate (Adam's 2026-08-18 signal ruling) but the report never says so - and it is a generated report living in this folder | `2026-09-03-parity-report.md:22-29` |
| C3 | C | Changelog sentence "lists only the stations autonomy itself would send it to" is imprecise: the base list is square-level by ruling, so a non-reversible train sees termini autonomy would refuse it | `Readme.md:380` |
| C4 | C | A legacy placement on a split square with no recorded facing gets one at random - undeclared, unreported, two imports of one file can differ | `AutonomySession.java:669-677` |
| C5 | C | `importLegacy`'s javadoc promises "Nothing is written to disk - the caller saves, so a bad match can still be cancelled"; its only caller saves on the next line with no cancel step | `AutonomySession.java:492`, `AutonomyViewerPanel.java:1156-1158` |
| C6 | C | The undo door discards `saveQuietly()`'s failure result that its sibling door logs | `LayoutEditor.java:428` vs `:596` |
| C7 | C | A modern points-ARRAY export re-imported is classified `LEGACY_GRAPH` and silently loses new-model fields; no genuine 2.8.1 file is affected | `AutonomySession.java:1136` |
| C8 | C | Downgrading to 2.8.1 destroys locomotive keyboard pages 11-50: master restores `NUM_LOC_MAPPINGS` (10) and its next save writes 10 back | HEAD `TrainControlUI.java:387` vs `master:1700` |
| C9 | C | Three graph-display preference constants are dead, their stored values orphaned, and the R28-C3 comment's "no surface shows homes" claim went stale when the caption toggle landed | `TrainControlUI.java:230, 245-246, 261` |
| C10 | C | The "..." escape to the autonomy tab is created inside the loop over the base list, so it never renders when that list is empty - exactly the case where everything visible was filtered | `LayoutRightclickAutonomyMenu.java:383-425` |
| D1 | D | `pickPath`, its yield-probe mirror, and `barredFromAutonomy` all keep the terminus clause after the `mustBackIn` removal - the removal is confined to manual Return Home as claimed | `Layout.java:3835, 3577, 4146` |
| D2 | D | The staging audit is coherent after its fifth exemption went with the rule - the remaining four count themselves correctly | `HomeStaging.java:600-680` |
| D3 | D | The tightened reaches-nothing notice asks the runtime's own square-level clauses and its NOTICE severity is pinned by test | `AutonomyChecks.java:450`, `AutonomySession.java:2885` |
| D4 | D | All eight bundles: pure ASCII, identical 1890-key sets, zero straight apostrophes in the 508 MessageFormat-formatted keys | `src/org/traincontrol/resources/` |
| D5 | D | REL-A1 and REL-A2 verified fixed in code, not from their dispositions; REL-B3/B4 are recorded decisions | `LayoutLabel.java:1465`, `Layout.java:7879` |
| D6 | D | Adam's setup.json residuals (IPR-A1, IPR-C1) verified cleared; station 5:6,4 keeps its caption | `cs2_sample_layout/config/autonomy/setup.json` |
| D7 | D | 299 tests, 8 classes, 0 failures, 0 skips; the one live-layout guard alarm isolated to Adam's own editor closing, not a test | below |
| D8 | D | The twice-rewritten destination menu asks one named predicate per question, both tested; terminus-in-base-list is Adam's 2026-09-04 ruling | `LayoutRightclickAutonomyMenu.java:280-460`, `testAutoLayout.java:1601-1693` |
| D9 | D | The question, not a defect: nobody has yet watched a nose-first train LEAVE the terminus the planner now sends it into - MT-250 names it as the thing to check | MT-250 |
| D10 | D | Data-safety of the session/store: ten historically catastrophic paths checked and closed, from a delegated sweep with the load-bearing claims re-verified here | `AutonomySession.java`, `AutonomyCompanionStore.java` |
| D11 | D | The id-as-name page pun's square-key path is a known trap Adam ruled to leave for FR-013 - a decision, recorded | `AutonomyCompanionStore.java:4858-4933` |
| D12 | D | The open review backlog going into the release is test-quality debt and Adam's design questions, not undispositioned defects | `2026-09-03-questions-for-adam.md`, `2026-09-03-c-sweep-report.md` |
| D13 | D | What this review did NOT cover - read this before trusting the verdict's breadth | below |
| D14 | D | Regression-vs-master set-diffs: menus, shortcuts, preference keys, public API, RouteEditorFrame a strict superset, saved-state serialization unchanged, autonomy.json contract a superset, CS2/CS3 parsing only widened | below |
| D15 | D | The graph window's deletion is the recorded diagram-autonomy design; per-station capabilities all survive on the diagram, with one residual (no UI for hand-built edges/config commands) named | below |
| D16 | D | The fixing-round sweep's clean checks: HomeStaging post-removal, `roomAtTheEnd` plumbing, the FR-058 final state clause-for-clause, VD10-A1's own ordering and lock order, protecting-signal unification at all three doors, and eight more | below |

---

## A findings

### ACC-A1 - A lock-phase failure is recovered twice, and the second recovery undoes the first

| | |
|---|---|
| **Severity** | A |
| **Disposition** | fixed 2026-09-04 - the handler gates the release on `activeLocomotives` membership, captured BEFORE the removal consumes it; the first version of the gate read it after and stopped the mid-run release happening at all, which `testAFailedPathStopsTheRunAndGivesTheTrackBack` caught |
| **Confidence** | Every layer verified by this reviewer by reading the four methods and the Edge contract; not executed. Reachability is the exact failure class `f2818206` built its recovery and an injection test for. |

Two fixes, each sound alone, combine into the defect:

**The first recovery** (`f2818206`, Aug 23): a throw out of `configureAndLockPath`'s lock loop
releases exactly the taken prefix - `handleMisconfiguredPath(path.subList(0, edgesLocked), loc)` -
removes the `takingPath` claim, and **rethrows** (`throw lockFailure;`, `Layout.java:3028`).
`handleMisconfiguredPath` ends by re-reserving the locomotive at its start:
`path.get(0).getStart().reserve(loc);` (`Layout.java:3232`), under a comment that says why -
*"Provably at its start... Re-reserved rather than placed."* Its safety argument, still standing at
`Layout.java:2922`: the rethrow *"went straight out to executePath's handler, which deliberately
does not unlock."*

**The second recovery** (`416e34c2`, Sep 3, Adam's ruling *"Force a graceful stop, alert the user,
then unlock"*): `executePath`'s `catch (RuntimeException e)` now calls `this.unlockPath(path, loc)`
**unconditionally** (`Layout.java:5181`). `configureAndLockPath` is called inside
`executePathInternal`, inside that try - so the rethrown lock failure lands in a handler that now
unlocks the **whole** path, whose taken prefix was already given back and whose locomotive was just
re-reserved at its start. The ruling was made about a MID-RUN failure; the lock-phase rethrow is a
different case that inherited it - the same lifted-precondition shape as the comment at `:2922`
itself warns about, now pointing the other way.

What the second, whole-path `unlockPath` does then, verified clause by clause in the non-atomic
branch (`"atomicRoutes": false` is Adam's own configuration, per the code's note at the
`clearedEdges` comment in the same handler):

1. **The train is erased from the model.** `Layout.java:3409`: `if ((i == 0 || i !=
   path.size() - 1) && loc.equals(e.getStart().getCurrentLocomotive()))
   e.getStart().setLocomotive(null);` - at `i == 0` the start point holds exactly the reservation
   the first recovery just wrote, so it is cleared. The failed train stands physically on a square
   the model believes empty: `pickPath` and `isPathClear` will route another train into it, Return
   Home cannot plan for it, and nothing says so. (The atomic branch clears the start too,
   `:3316`.)
2. **Other trains' lock claims are stolen.** For never-taken edges, `e.setUnoccupied()` runs (or,
   where another locomotive holds the end point, the skip branch releases the edge's lock edges
   directly, `:3395-3400`). `Edge.setUnoccupied()` cascades `setLockedEdgeUnoccupied()` to every
   lock edge, and `Edge.release()`'s own javadoc (`Edge.java:452-470`, the REL-C16/DAY-C4
   correction) states the precondition this violates in as many words: each lock edge decrements
   *"with no knowledge of whether this edge was ever taken"*, and *"what makes the over-release
   unreachable is narrower, and is written at the call site"* - the call site being
   `configureAndLockPath`'s own recovery range, which this second release is not. Occupancy is a
   count, so a claim another running dispatch holds on a shared throat or crossing is silently
   decremented; a route can then throw ironwork under that train. The taken prefix's lock edges are
   decremented a second time the same way.

`f3dc0aec` (VD10-A1) fixed the `clearedEdges` ordering inside this same handler the same day and
did not revisit the lock-phase interaction - the third-generation shape this review was pointed at.

Three things belong to the fix, whatever shape it takes: the handler must not unlock a path
`configureAndLockPath` never returned from (the locomotive joining `activeLocomotives` only after
that call returns - `Layout.java:5279` - is a discriminator already in hand); the stale comment at
`Layout.java:2922` ("deliberately does not unlock") must follow; and one adjacent question should
go to Adam with it - on a genuine **mid-run** failure the ruled unlock also clears the failed
locomotive off whatever point it had reached, so after "stop, alert, unlock" the model has
forgotten the train entirely, and the alert tells the operator to look at the track without saying
that. *Found by the delegated fixing-round sweep; every claim above was independently re-verified
here before filing.*

---

## B findings

### ACC-B1 - The legacy import drops hand-written `lockedges` and does not count them among what it leaves behind

| | |
|---|---|
| **Severity** | B |
| **Disposition** | fixed 2026-09-04 - `autosetup.ui.leftEdgeLocks` is the fifth line, in all eight bundles; counted rather than compared, for the reason written at the count.  MT-269 asks Adam to read it if he ever re-imports |
| **Confidence** | The drop, the reporting omission, and the derivation mechanism were each verified by reading by this reviewer. Whether any of the 116 refs on Adam's own layout lacks a geometric counterpart was NOT measured. |

A 2.8.1 `autonomy.json` can carry `lockedges` on an edge - "when this edge is taken, these others
are locked too" - and 2.8.1 honours them: `Layout.java:8165` reads them
(`edge.has("lockedges")`). Adam's own legacy file at
`cs2_sample_layout/config/autonomy_legacy/autonomy.json` carries **116 lock references across 50 of
its 90 edges** (measured by this review).

The diagram import carries none of them. `AutonomySession.java:523` lists what a point keeps -
`CARRIED_SETTINGS = Arrays.asList("priority", "speedMultiplier", "excludedLocs", "active",
"maxTrainLength")` - and `importLegacy` never reads the file's `edges` array. That much is arguably
by design: the new model derives locks from geometry. `GraphReducer.deriveLocks()`
(`GraphReducer.java:1219-1257`) indexes every edge by the tile locations it occupies and locks any
two edges that share one. What that cannot reproduce is a hand lock between edges that share **no**
tile - the deliberately conservative kind: parallel adjacent tracks, an electrical section, a
clearance rule. Such a lock simply vanishes.

The defect this finding is about is narrower than the drop itself: **the vanishing is unreported.**
`whatALegacyImportLeaves` (`AutonomySession.java:916-957`) counts exactly four things - edge
`commands`, edge `length`s, the `timetable`, and `activateRoutes` - and the import dialog built on
it presents that as the whole account. Everything else the import drops is spoken; this is the
fifth thing, and it is silent. The consequence of a load-bearing residue is the priority class this
review was asked to look for: two trains permitted to move at once where the hand-tuned file forbade
it, with nothing saying why.

What would settle it: `test/core/testAutonomyDiagramSampleLayout.java:1919` already computes the
legacy-vs-derived lock comparison and prints it **as a diagnostic only** - its own text calls the
unmatched refs "the evidence on whether conservative hand-written locks were load bearing". Nothing
asserts subsumption. Running that comparison against the live diagram and either asserting the
residue is empty or adding `autosetup.ui.leftEdgeLocks` to the report is the whole fix, and the
second half is one string and one counter.

*Found by the delegated data-loss sweep; every claim above that carries a line number was re-read
and re-verified directly, and the 116/50/90 count was re-measured from the file.*

### ACC-B2 - The editor-close reload lost the emergency stop, and the census that justified narrowing it never counted this caller

| | |
|---|---|
| **Severity** | B |
| **Disposition** | **comment fixed, behaviour left for Adam** - the census names the fifth caller now; whether closing the track diagram editor should stop the trains is his ruling and MT-269 asks it |
| **Confidence** | Both sites and the parameter plumbing verified by reading; the hand-throttled-train scenario reasoned, not executed. |

`23f233ca` (VD11-A2) made `autonomyLoadedFromDiagram` stop trains only `if (!resumed)`
(`TrainControlUI.java:3586`), and its comment censuses the callers: *"The two interactive callers
are the autonomy menu and the Configurations dialog... The two that are not are the start-up
resume... and this rebuild."* There is a third non-interactive caller the census does not name: the
reload after the **track-diagram editor** closes - `getAutonomyViewerPanel().load(wasRunning,
false)` at `TrainControlUI.java:20298`, following `resetAutonomySession()`. The plumbing is direct:
`load(name, interactive)` calls `ui.autonomyLoadedFromDiagram(name, !interactive)`
(`AutonomyViewerPanel.java:822`), so `false` arrives as `resumed = true` and the stop is skipped.

On that path the diagram **geometry** was just edited and every layout object replaced - which is
the situation the stop's own retained justification describes: *"the stop catches hand-throttled
trains that isAutonomyBusy() does not cover - a train somebody was driving keeps rolling while the
new layout thinks everything is parked"* (`:3568`). The narrowing was argued from setup edits
(typing a display name, a tile length) where stopping every train was absurd, and it is right
there; the editor-close rebuild is much closer to choosing a different railway than to typing a
name, and 2.8.1-through-rc11 behavior stopped trains there. Not filed as a defect outright because
the ruling boundary is genuinely Adam's to draw - but it should be drawn knowingly, and the census
comment corrected either way. *Found by the fixing-round sweep; verified here.*

### ACC-B3 - A declined mid-run setup edit can be silently reverted on exit, against the message's promise

| | |
|---|---|
| **Severity** | B |
| **Disposition** | fixed 2026-09-04 - a declined edit sets a flag and the exit capture is skipped entirely rather than folding the older layout over the newer file; it says so, and MT-269 asks whether losing the positions is the right trade |
| **Confidence** | Mechanism verified by reading all three sites (it is the same mechanism VD10-A2 documented); the four-step sequence was not executed. |

`rebuildRunningLayoutFromSetup(true)` (`TrainControlUI.java:5490`) declines to apply an edit made
while autonomy runs and logs: *"The edit IS saved - stop autonomy and make it again to have it take
effect now, or it will be picked up the next time the setup is loaded."* The second remedy has a
hole. The exit capture (`TrainControlUI.java:2248`) runs whenever autonomy is **stopped** at exit
(`captureSession && ... && !isRunning()`), and `captureFromLayout` writes what the running layout
knows over the configuration's `POINT_OPERATIONAL_KEYS` - *"including being REMOVED when the layout
no longer carries them"* - and `home` is one of those keys (`AutonomySession.java:1982-1984`). The
running layout was built before the edit. So: edit during a run, rebuild declined, operator stops
autonomy, exits without re-editing - the capture deletes the edit from the configuration on disk,
silently, on the exact path the message told the operator was safe. The same capture-first behavior
sits on the ordinary load path too (`AutonomyViewerPanel.load`'s own javadoc: *"Loading normally
CAPTURES first... It is exactly wrong when the SETUP is newer"* - the rebuild path passes
`captureRunningState=false` for precisely this reason; the exit door has no such discrimination).
Graded B rather than A on the "could happen vs does happen" rule - the sequence needs an edit made
during a run, which the UI already warns about - but the loss when it fires is authored data, and
the message's promise is what makes it silent. *Found by the fixing-round sweep; all three sites
verified here.*

---

## C findings

### ACC-C1 - Three comments still describe `mustBackIn` as a rule in force

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | All three sites re-read at the reviewed tree after the removal commit. |

Commit `489439fa` removed `mustBackIn` from all three of its functional sites and rewrote
`HomeStaging.java`'s own commentary honestly. Three comments elsewhere still describe the rule as
standing:

- `Layout.java:7879-7881`: *"a reversible EMU read back as non-reversible is refused a terminus by
  `mustBackIn` and planned round the long way by `pickPath`"* - the `pickPath` half is still true,
  the `mustBackIn` half is not; staging now accepts.
- `AutonomyBuilder.java:581-583` (`homeCopy` javadoc): *"staging refuses a terminus to a locomotive
  that cannot reverse unless the route turns on the way - `mustBackIn`, since `20c30781` took that
  clause out of `canRest`"* - staging no longer refuses.
- `AutonomyChecks.java:727-733` (`checkHomesThatNeedReversing` javadoc): *"which staging refuses to
  a locomotive that cannot reverse unless the route turns on the way (`mustBackIn` ...)"* - same
  stale claim, and this one matters more than cosmetics: with the rule gone, this WARNING is now the
  **only** voice telling an operator that a non-reversible locomotive homed on a must-turn square
  will be driven in nose-first. Its rationale should say that, not the opposite.

This is the codebase's own "fix one site, sweep the siblings" pattern: the commit swept
`HomeStaging.java` and the tests, and these three comments in neighbouring files were the siblings.
The historical comments left inside `HomeStaging.java` (`:951`, `:1074`, `:1716`) are correctly
written in the past tense and are not part of this finding.

### ACC-C2 - The parity report leaves its four "missing route" rows unexplained

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Three of the four rows traced to Adam's recorded ruling; the fourth reported, not re-derived. |

`2026-09-03-parity-report.md` frames itself as "3.0.0 should offer at least what 2.8.1 does", then
lists four routes missing or reduced (`:22-29`) and ends without a word about them. Three of the
four are `* -> BottomSecondary`, which is Adam's own ruling recorded at
`2026-08-18-manual-test-plan.md:178`: *"it should NOT - a red signal after the end"* requires a stop
first - i.e. 2.8.1 offered a route Adam considers wrong and the derived graph correctly refuses it.
A reader of the parity report alone would conclude the opposite. The fourth row
(`BottomInner -> Tunnel`, 1 of 2 variants) was not re-derived by this review and is not covered by
that ruling on its face.

Two protocol notes while here: the report has no status line (defaults to open), and it is a
**generated report** - the README of this folder says those "do not belong in this folder. Either
fold the conclusion into a review and delete the dump, or leave the harness to write it to a
temporary directory." Folding the conclusion in would also be the natural place to write down why
the four rows are right.

### ACC-C3 - The changelog's destination-menu sentence promises more than the square-level split delivers

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open - one sentence, Adam's wording to choose |
| **Confidence** | Verified against the menu code and `pickPath`. |

`Readme.md:380`: *"Right-clicking a train on the track diagram lists only the stations autonomy
itself would send it to."* By Adam's 2026-09-04 ruling the base/More-Destinations split asks the
**square-level** predicate only (`isChoosableByAutonomy(end)`,
`LayoutRightclickAutonomyMenu.java:355`), while `pickPath` additionally applies two per-train
clauses. So a non-reversible locomotive's base list can include a terminus that autonomy would
never actually choose for that train. The split itself is the decision and is sound (ACC-D8); only
the changelog sentence overstates it. Given the standing rule that the changelog serves
non-technical readers, this may be fine to leave - flagged, not argued.

### ACC-C4 - A legacy import invents a facing at random and does not say so

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Verified by reading at the cited lines. |

`AutonomySession.java:669-677`: a legacy placement landing on a split square with no facing picks
one via `ways.get(new java.util.Random().nextInt(ways.size()))`. The comment above it addresses
why the choice is not legality-checked; nothing addresses that it is random - it is not counted in
`LegacyImport`, produces no per-square notice, and two imports of the same file can place the same
train pointing opposite ways. Bounded: the old format genuinely cannot state a facing, the first
real run's capture corrects it, and the editor offers the choice. A deterministic pick (first legal
facing) plus one line in the import summary would remove the surprise. *Found by the sweep, verified
here.*

### ACC-C5 - `importLegacy`'s "nothing is written to disk" contract is false at its only caller

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Verified by reading both sites. |

`AutonomySession.java:492` (javadoc): *"Nothing is written to disk - the caller saves, so a bad
match can still be cancelled."* The only caller, `AutonomyViewerPanel.java:1156-1158`, is
`importLegacy(file, known)` immediately followed by `save()` - there is no cancel step between. The
method's own half is true (it does not write); the promise about the caller is not, and it is the
kind of comment that misleads the next maintainer into "adding back" a cancel that never existed.
Loss is bounded because the import is strictly gap-filling. *Found by the sweep, verified here.*

### ACC-C6 - The undo door swallows the save failure its sibling logs

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Verified by reading both sites. |

`LayoutEditor.java:428`: the undo path calls `autonomy.saveQuietly();` and discards the boolean.
The sibling door at `:596` (`rememberAutonomy`) checks it and logs *"Could not save the autonomy
setup after a diagram edit"*. Disk is authoritative after the session rebuild, so an I/O failure at
exactly that moment means the undo silently does not stick. Same fix as the sibling, one `if`.
*Found by the sweep, verified here.*

### ACC-C7 - A points-array export re-imported takes the legacy door and sheds new-model fields

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Mechanism verified by reading; the "no genuine 2.8.1 file is affected" claim is the sweep's file inventory (five repo fixtures plus Adam's file), reported, not re-run. |

`AutonomySession.detectImportFormat` (`:1136`) classifies any file whose `points` is an **array** as
`LEGACY_GRAPH` - including a built configuration serialized by `Layout.toJSON`, which writes fields
the legacy import does not carry (`autoDestination`, `protectingSignal`, blocks) and
`whatALegacyImportLeaves` does not count. Genuine 2.8.1 files carry none of those keys, so the
supported migration is unaffected; the trap is round-tripping a modern derived-graph export through
the import door. Narrow, but it is the same silence-shape as ACC-B1 and worth one refusal or one
report line.

### ACC-C8 - Downgrading to 2.8.1 destroys locomotive keyboard pages 11 to 50

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open - a release-note line, not a code change |
| **Confidence** | Both sides verified by reading: master's restore loop and HEAD's cap. |

HEAD allows up to fifty locomotive mapping pages (`MAX_LOC_MAPPINGS = 50`,
`TrainControlUI.java:387`, Adam's 2026-08-27 ceiling). 2.8.1 restores only the first ten - master's
restore loop is `for (int j = 0; j < saveStates.size() && j < TrainControlUI.NUM_LOC_MAPPINGS; j++)`
(`master:TrainControlUI.java:1700`) - and its next save writes back only what it restored, so pages
11+ are gone permanently after one downgrade round-trip. The upgrade direction is safe (the restore
path deliberately grows the count past the cap to fit saved pages - HEAD `:6764`). Not a HEAD
defect; worth one sentence wherever downgrading is discussed. *Found by the regression sweep,
verified here at both cited sites.*

### ACC-C9 - Dead graph-display preference constants, orphaned settings, and a half-stale comment

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Constants and their zero references verified by grep; the stored-value orphaning follows from R28-C3's own recorded decision. |

`HIDE_REVERSING_PREF` (`TrainControlUI.java:230`), `HIDE_INACTIVE_PREF` (`:245`) and
`SHOW_STATION_LENGTH` (`:246`) are still defined and referenced by nothing in `src/`. The comment at
`:261` records the decision for their siblings ("HideReversingEdges and ShowHomeLocomotives are gone
(R28-C3)... values are left in the preferences store rather than cleared") - that part is a recorded
decision, not a defect. Two residuals: the new editor's caption toggles use fresh keys with fresh
defaults, so a 2.8.1 user's old display choices silently revert to defaults once; and the R28-C3
comment's claim that no surface in 3.0.0 shows home assignments went stale when the "Show Homes"
caption toggle landed (consumed at `LayoutGrid.java:1217-1219`,
`isShowingHomeLocomotives()`). Dead constants and one stale sentence -
sweep material for after the release. *Found by the regression sweep; constants and comment
re-verified here.*

### ACC-C10 - The "..." escape never renders when the base destination list is empty

| | |
|---|---|
| **Severity** | C |
| **Disposition** | open |
| **Confidence** | Verified by reading the menu construction. |

`LayoutRightclickAutonomyMenu` counts squares dropped by `isOfferableToOperator` toward `possible`
deliberately, *"so the ellipsis offers the autonomy tab, which lists everything"* (`:339`) - but
the ellipsis item is created inside `for (List<Edge> path : paths)` (`:383-425`), so when the base
list is empty the loop body never runs and no "..." appears. A locomotive whose choosable
destinations are all inactive or excluded gets a bare "More Destinations" submenu with no route to
the tab - the stated escape is unreachable in exactly the case where everything visible was
filtered. Narrow; the empty-`paths` heading gate added by VD11-C3 shows the case was thought about
for the separator and not for the ellipsis. *Found by the fixing-round sweep; confirmed against
this reviewer's earlier read of the same lines.*

---

## D findings - checked and found sound, decisions noted, and coverage edges

### ACC-D1 - The `mustBackIn` removal is confined to manual Return Home, as its commit claims

Verified at all three autonomy-side sites, by reading the enforcing lines rather than the commit:
`pickPath`'s candidate filter keeps `(!end.isTerminus() || loc.isReversible())`
(`Layout.java:3835`); the yield-probe that must mirror it keeps the identical clause
(`Layout.java:3577`); `barredFromAutonomy` keeps the per-train refusal with its reason string
(`Layout.java:4146-4148`). `mustBackIn` survives in `HomeStaging.java` only as three past-tense
historical comments; the method and all three call sites are gone. The three tests the commit says
were inverted ran green here (ACC-D7).

### ACC-D2 - The staging audit is coherent after its fifth exemption went

`auditAgainstRuntime` (`HomeStaging.java:600-680`) now carries four exemptions whose comments count
themselves correctly ("the fourth, in the same shape as the three above it"). The removed fifth
existed only to stop the removed rule reporting itself as a divergence; with both sides of the
comparison now symmetric on termini, nothing dangles. Confirmed by reading the whole method.

### ACC-D3 - The tightened reaches-nothing notice matches the runtime's square-level clauses

`checkReversingGoesSomewhere` (`AutonomyChecks.java:450-536`) now takes
`stationsAutonomyWillNotChoose()` (`AutonomySession.java:2885`), which asks the runtime's own
`isAutoDestination` clause of the diagram; the reversing clause is re-asked from the sets already in
hand. The commentary correctly explains why the two per-train clauses are excluded (the notice is
drawn on a square with no train in the question - the FR-058 distinction). The commit's claim that a
test pins the NOTICE severity is consistent with `testAutonomyDiagramSession` running 110/0/0 here,
though the individual test method was not read.

### ACC-D4 - The eight message bundles are clean

Checked by script, not by eye: all eight `messages*.properties` are pure ASCII (zero bytes over
0x7F), carry byte-for-byte identical key **sets** (1890 keys each, continuation lines handled), and
of the 508 keys reached through `I18n.f`/`logf` (the MessageFormat path, `I18n.java:78`), zero
contain a straight apostrophe outside a doubled pair, in any language. Limitation: keys assembled
dynamically at the call site are not captured by the grep that built the 508. `core.testMessageBundles`
ran 13/0/0 alongside.

### ACC-D5 - The release review's A findings are fixed in code, and its B3/B4 are decisions

Verified by reading the fixes, not the dispositions: REL-A1's gate is present with its marker
(`LayoutLabel.java:1465`, "AND ONLY WHILE AUTONOMY IS RUNNING ... (REL-A1)"); REL-A2's fix is the
"ABSENT MEANS NOT STATED (REL-A2)" parse in `Layout.java:7858-7883` - a placement that omits
`reversible` no longer resets it - paired with the deliberate name-only strip at the capture side
(`AutonomySession.java:3116-3127`). REL-B3 (auto-load default true) is kept deliberately and
announced in the changelog, with the refuted justifying sentence corrected (commit `9d16eeae`);
REL-B4 (derived-graph export behind a debug flag) is recorded as Adam's decision to make.

### ACC-D6 - Adam's setup.json residuals are genuinely cleared

Measured on the live file (read-only): `tileLengths` is `{}` - all six fabricated IPR-A1 entries
gone; `captions` holds 33 entries with no duplicate, and station `5:6,4` kept its caption via tile
`5:6,5` - the IPR-C1 duplicate was removed without losing the authored caption.

### ACC-D7 - The tests that were run, and the guard alarm that was not a defect

Eight classes through `one.sh`, 299 tests, **0 failures, 0 skips**: `core.testHomeStaging` (89),
`core.testAutonomyDiagramSession` (110), `core.testAutoLayout` (25),
`regression.testEditorSurfaceRules` (37), `core.testReturnHomeSequencesAReversal` (10),
`core.testNonReversibleTrains` (7), `core.testMessageBundles` (13),
`regression.testCancelRestoresPlacements` (8). The first batch ended with the live-layout guard
naming one difference: `config/autonomy/setup-before-edit.json` **disappeared** during the run.
Isolated before reporting, per the harness's own instruction: that file is the layout editor's
unfinished-edit note, written when the editor opens and cleared only by `LayoutEditor.dispose()`
(`TrainControlUI.java:16460` says it is the only clearer) - Adam, testing on the live railway,
closed his editor while the batch ran. `test/README.md:88` records the identical false alarm from
2026-09-03. The second batch ran with no warning at all. Not a defect, and the green results stand.

### ACC-D8 - The twice-rewritten destination menu holds together

The final shape asks exactly two named predicates - `isOfferableToOperator` (off the menu entirely:
inactive, or excluded for this train; `Layout.java:4103-4108`) and `isChoosableByAutonomy` (which
list; square-level) - so the guard and the affordance ask the same question, the VD11-B1 ellipsis
now counts what is actually left out (except when the base list is empty - ACC-C10), and the
More-Destinations submenu is uncapped with the screen argument written down. Both predicates are pinned in `core.testAutoLayout` (`:1601-1693`) including
the stated mutation on the terminus clause. Terminus-in-the-base-list is Adam's 2026-09-04 ruling,
recorded in the code at the split. What is NOT covered anywhere is the menu assembly itself (the
class is package-private GUI code, tested at the predicate layer only) - noted, not filed.

### ACC-D9 - The question this release should answer on the track before it ships

Not a defect: `489439fa` implements Adam's explicit ruling ("Return home is manual, take the rule
out and say why"), the price is named in the commit, in the code, and in three inverted tests, and
`pickPath` keeping the rule for autonomy is verified (ACC-D1). What remains is the check MT-250
itself names: **watch EN57-203 arrive nose-first at TunnelLongPark, then see whether it can leave.**
If it cannot, MT-250 already costs the narrower fix - allow the terminus for the HOME only, keep the
rule for intermediate stops. That is the one open acceptance item this review treats as a gate, and
it needs Adam's hands, not code.

### ACC-D10 - Data-safety of the session and store: ten paths checked and closed

From the delegated data-loss sweep, whose B and C claims were each re-verified here at their cited
lines (ACC-B1, C4-C7); its clean checks are summarized rather than re-derived: failed loads keep
what was there (read-before-clear, type-failure snapshot, newer-version refusal); save/load/clear/
rename/snapshot are driven from one `Kept` registry so no door forgets a field; page renumbering is
reported rather than adopted and settings follow the name; locomotive rename/delete reaches both
undo stacks, the cancel snapshot, inactive configurations, the captured timetable and the on-disk
pre-edit note; `deleteEverything` confirms (default No), deletes the note, and is refused while
autonomy runs; excluded pages round-trip; capture merges per point and never substitutes. Each is
pinned by a named test in `testAutonomyDiagramSession`/`testAutonomyDiagramStore` and neighbours.
Confidence: **reported by the sweep and spot-checked, not independently re-derived line by line.**

### ACC-D11 - The square-key half of the id-as-name pun is a recorded deferral

`AutonomyCompanionStore.toStored`/`resolvePage` (`:4858-4933`) document the trap themselves - a page
literally named "2", stored keyless, can reattach its squares to the page whose ID is 2 - and record
Adam's ruling to leave it for FR-013 to dissolve. A decision, not a defect; listed so the next
reviewer finds the ruling rather than refiling it.

### ACC-D12 - What is open going into the release is debt and design questions, not hidden defects

Checked against the documents rather than their tables: `2026-09-03-questions-for-adam.md`'s items
are all either done in HEAD (the V36-C4 notice - ACC-D3; the FR-058 rulings), done in data
(ACC-D6), answered inline by Adam, or hands-on (MT-265, MT-246-homing). The c-sweep's remaining
"what needs Adam" items 2 and 3 are design questions explicitly not blocking. The 36 open September
findings are test-quality items under the TSX audit (its A1 and the Bs verifiably fixed in the
commit log, 16 Cs deferred past 3.0.0 with reasons); the ~190 July/August backlog findings describe
largely rewritten code and are dispositioned as a post-3.0.0 sweep. The stale-disposition audit
(`853bc9f9`) ran the day before this review and is why the tables were trusted this far and no
further.

### ACC-D13 - What this review did NOT cover

Said plainly, because an acceptance review that overstates its coverage is worse than a narrow one:

- **Nothing in ACC-A1, B2 or B3 was executed.** All three were established by reading; A1's
  reachability rests on the failure class `f2818206` built an injection test for, not on a
  reproduction run here. A red test before the A1 fix is the codebase's own rule and still needs
  writing.
- **Commit `e2afe88c`** (OB-172/OB-173/DAY-C2 - 151 insertions across `AxisRuler`, `LayoutGrid`,
  `LayoutEditor`, `TrainControlUI`, plus two tests) landed mid-review. Its message was read and
  every line number cited in this document was re-read from the tree WITH those edits present (one
  citation drifted and was corrected - ACC-C9's `LayoutGrid` line); nothing more: the code itself
  is unreviewed by anyone.
- **Headful focus tests** were not run (Adam's application held the foreground throughout - any
  failure would have been unattributable), and **`battery.sh` was not run**, per Adam's ruling that
  it is an acceptance gate he runs, not a per-cycle check.
- Within ACC-B1, the decisive measurement - derived locks vs the 116 legacy refs on the live
  diagram - was identified but not executed. Within ACC-D15, how well the sensor-matched
  autonomy.json import covers a hand-built graph on an *unusual* layout is not provable by reading.
- The regression-vs-master comparison (ACC-D14) verified capability and contract parity by set-diff
  and by reading call sites; nothing in it was verified by executing 2.8.1 against HEAD.

### ACC-D14 - Regression against master, by set-diff rather than sampling

From the delegated regression sweep; method was exhaustive set-comparison of both branches, and this
reviewer independently re-verified the two claims promoted to findings (ACC-C8, ACC-C9). Found
sound, with the evidence that settled each:

- **Preferences**: the literal `prefs.get/put` key sets are identical across branches except the
  graph-window keys (ACC-C9); `Conversion.getFolderHash` is byte-identical, so no folder-hashed key
  drifts. **Keyboard**: the `KeyEvent.VK_*` usage set and every `KeyStroke.getKeyStroke` accelerator
  in `TrainControlUI` are identical - no shortcut lost. **Menus**: no `JMenuItem` member removed,
  nine added; the route-list right-click menu is key-for-key identical.
- **Public API**: no public method removed from `TrainControlUI`, `MarklinControlStation`, or the
  whole `automation/` and `base/` packages; `View`/`ViewListener` additive only.
- **RouteEditor -> RouteEditorFrame is a superset**: the `RouteCommand` enum is identical;
  previously read-only kinds are now buildable (`CommandRow.java:60`); unknown kinds are preserved,
  not dropped; grouped conditions survive as the indented outline ("(A or B) and C" handled,
  `ConditionOutline.java:190`); CS-owned routes open read-only with the same rule as master's
  tooltip; and the protocol column removes master's silent DCC->MM2 rewrite-on-save.
- **Saved state and downgrade**: `UIState.data` format unchanged; serialized classes byte-identical
  with unchanged `serialVersionUID`s; HEAD adds atomic writes and an unreadable-file backup -
  strictly safer. The one downgrade hazard found is ACC-C8.
- **autonomy.json contract**: HEAD reads a strict superset of master's keys; the seven new keys HEAD
  writes are ignored by master's per-key `has()` parsing; running from the diagram deliberately
  leaves autonomy.json untouched (`TrainControlUI.java:2286-2291`), so a downgrade finds the
  pre-upgrade file intact.
- **CS2/CS3 parsing**: `parseRoutes`/`parseRoutesCS3`/`parseMags` untouched; the changes widen
  acceptance (MFX UID-as-address corrected like the DCC branch, per-page fault-tolerant layout
  load that throws only when NO page reads and before clearing the current diagram, the CS2Message
  short-frame guard no longer misreads a truncated frame as an emergency stop).
- **Manual routes deferring to running autonomy** is the recorded routes-vs-autonomy doctrine, in
  the changelog in as many words, with the override wired into both surfaces
  (`execRouteOverridingConflicts`, `MarklinRoute.java:319`, called from `LayoutLabel.java:617` and
  `TrainControlUI.java:16752`) and emergency-stop routes exempt (`MarklinRoute.java:371`) - a
  deliberate, announced change a 2.8.1 user will notice, not a regression.

### ACC-D15 - The graph window's deletion is the recorded design, with one residual named

Deleted: `GraphViewer`, `GraphEdgeEdit`, `GraphLocExclude`, both graph right-click menus, and their
eighteen i18n keys (verified unreferenced). Every per-station capability survives on the diagram -
max train length, priority, speed multiplier, exclusions, assign/clear locomotives, terminus/
reversing/rename (`AutonomyEditorPanel.java:1208, 1378, 1383, 1386, 1874, 3802` and `automationui/`).
The residual: manual point creation, manual edge add/delete, and hand-editing an edge's config/lock
commands have no UI any more - connections are geometry-traced, which is the 2026-08-01
diagram-autonomy design working as decided. A user whose hand-built graph does not correspond to
drawn track falls back to the JSON window (kept exactly for layouts "with no local copy",
`TrainControlUI.java:7163`) or the sensor-matched import. The model API for edge commands survives
and is exercised by the JSON path (`Edge.addConfigCommand`, `Layout.java:8103`). A decision with a
named edge, not a defect - and the edge is where ACC-B1 lives.

### ACC-D16 - The fixing-round sweep's checks that came back clean

From the delegated fixing-round sweep (which walked `1cfdf370..HEAD` hunk by hunk); this reviewer
independently re-verified everything it promoted to A or B, and its D list is summarized with its
own evidence: `HomeStaging` after `489439fa` (all three sites removed together; the remaining
four audit exemptions each mirror a rule `pickPath` applies that `getPossiblePaths` does not; the
new room rule needs no exemption because both sides call the same `measuredRoomToReverseInto`);
the `roomAtTheEnd` plumbing end to end (builder emit 1067, `Edge.toJSON` 588, parse 8113, both
sentinels handled, and no `Edge` copy constructor exists to miss the field); the FR-058 final
state clause-for-clause against `pickPath` with the 2026-09-03 two-arg overload fully deleted and
dispatch consolidated into one `destinationItem`; VD10-A1's own fix (unlock before
`clearedEdges.remove`, matching the ordinary ending, lock order `activeLocomotives` -> layout
monitor in both places, no reverse nesting found); the protecting-signal unification at all three
doors (route, tile, keyboard - same gate, correct green-direction test at each); the keyboard
refuse path (no ActionEvent re-entry); REL-A2's four destructive else-branches removed with
explicit `0` still clearing; the SVN-B13 home rules mirroring `claimHome` deterministically; the
delete-logging order in `MarklinControlStation`; the `executeRoute` debounce funnel; the
`canStartAutonomy` widening asking `hasErrors()` at guard and both affordances; the OB-170
one-window rework (one `display()` caller, idempotent `connectingFinished`, EDT self-marshalling);
the `pageNames` sentinel conversion; and both rename doors plus the delete door repainting the
timetable (DAY-B1). Confidence: **reported by the sweep with its evidence lines; not re-derived
here except where promoted to findings.**
