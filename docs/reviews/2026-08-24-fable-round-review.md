# Review of the 2026-08-24 fix round

**Status:** open

**Prefix:** `FBR` — cite findings from here as `FBR-B1`, `FBR-C2`, and so on.

**What was reviewed, and when.** The commits from `a2decb01` (OB-068/OB-069, 2026-08-24 01:42) through
`ef70c3ab` (OB-067, 2026-08-24 04:10) on `autonomy-diagram-r0` — the round covering OB-068 to OB-083,
FR-014, FR-015, FR-017, and the OB-067 held-pages work — read on 2026-08-24 as the code stands at
`ef70c3ab`. The two triage-tool commits inside that range (`86acd990`, `50b1bbd2`) were read for their
src changes; their Python changes were skimmed only.

**Method and its limits.** Read-only. Nothing was built or run except
`triage.py verify-ledger`, which is read-only by design. Every finding below says how it was verified;
where a claim could not be traced end to end it is in D, marked unverified, rather than asserted.
OB-067 got the deepest pass, per the brief: every reader and writer of the shared setup object was
enumerated, and the held-entry lifecycle was walked through load, save, export, import, snapshot,
restore, rename, delete and the on-disk repair path.

---

## A — high. Wrong behaviour on the layout, or data silently lost.

None found. In particular, the OB-067 change was checked hardest for exactly this and came back clean —
the specific doors checked are itemised in D1 through D5.

---

## B — medium. Incorrect results, or crashes in specific configurations.

| | Finding | Disposition |
|---|---|---|
| **FBR-B1** | The staging impossibility scan turns a movable blocker into a proof of impossibility | **fixed** |

### FBR-B1 — plan() reports IMPOSSIBLE for a fleet the search could stage

**Where.** `src/org/traincontrol/automation/HomeStaging.java` line 328, inside `plan()`.

**What is wrong.** The OB-073 fix added the state-aware `canRest(l, home, this.start)` to the
impossibility scan. Every other test in that scan — inactive origin, exclusion, length, terminus,
disconnection — is state-independent, which is what makes the scan's own claim true: "impossible by
construction: no move can ever end there". The new test is not state-independent. It reads the START
state, so a locomotive standing on the watched square makes the scan declare the goal unreachable —
even when that locomotive is itself being staged to a home elsewhere, in which case the plan's own
first move vacates the square and the leg is perfectly possible.

Concretely: A homed to station S, S `blockedBy` [D], B standing on D with a home somewhere else.
`plan()` returns `IMPOSSIBLE` with A in `getBlocked()`, and no search is run. The correct answer is a
two-move plan — B off D first, then A to S — which `search()` would find, because its `canRest` is
asked of the evolving state (`state.remove(at); state.put(to, l)` at lines 1103–1105 vacate squares as
moves are taken).

**Why it matters.** IMPOSSIBLE is presented to the operator as a proof, with named blocked locomotives,
and it short-circuits the search entirely. The failure is safe — no train moves wrongly — but the
answer is wrong, and it dead-ends the operator in a configuration staging exists to handle: a fleet
whose members are in each other's way.

**How it was verified.** By reading. `plan()` (lines 300–360) adds to `unreachable` on the strength of
`canRest(l, home, this.start)` and returns `Plan(IMPOSSIBLE, ...)` before `search()` when the list is
non-empty. `canRest(Locomotive, Point, Map)` at line 987 refuses when `heldBySomebodyElse` is true of
the watched square or a sensor sibling; `this.start` is the pre-plan occupancy. The search's state
mutation was read to confirm the occupancy is transient there. The round's own test
(`testAHomeHeldBackByAnOccupiedPointIsRefusedWhenPlanning`) does not reach this: its blocker LOC_B has
no home, so it genuinely cannot be moved and IMPOSSIBLE is the right answer for that fixture. The
follow-up commit `e7cbb25f` records that the scan is what that test pins — the scan is also what this
finding is about.

**What I would do.** In the scan, keep only the state-independent half (the stateless `canRest`), or
refuse via the state-aware form only when every occupant of the watched squares is itself unstageable
(no home, or already home). Either way, add the fixture this review describes — blocker homed
elsewhere — and expect READY. The search-side check at line 669 is correct and stays.

---

## C — low. Cosmetic, dead code, or narrow edge cases.

| | Finding | Disposition |
|---|---|---|
| **FBR-C1** | The stale-answer guards in FR-017 and OB-079 cannot fire, and their comments say why they should | **fixed** |
| **FBR-C2** | FR-017's seven message keys shipped in English in all seven non-English bundles | **fixed** |
| **FBR-C3** | OB-077's comment promises a recovery the caller does not perform | **fixed** |
| **FBR-C4** | OB-083 closed one instance of a silently-dropped blocker; the general case remains | **fixed** |
| **FBR-C5** | readShared's javadoc names a parameter that was renamed | **fixed** |
| **FBR-C6** | The FR-017 window's reasons and grouping are read under two separate acquisitions of the Layout monitor | **fixed** |

### FBR-C1 — `asked != locomotive` is always false

**Where.** `src/org/traincontrol/gui/AutoLocomotiveStatus.java` — line 588 (`showWhyNot`, FR-017) and
line 771 (the hover thread, OB-079).

**What is wrong.** Both background threads capture `final Locomotive asked = locomotive;` and guard the
EDT callback with `asked != locomotive` (or `asked == locomotive`), each under a comment saying "the
panel is reused as trains are placed and cleared, and a slow answer must not land in a window that is
now about a different train". The field is `private final Locomotive locomotive` (line 24), assigned
once in the constructor. The comparison can never be unequal; the guard is dead code and the comment
describes a reuse pattern this class does not have.

**Why it matters.** No misbehaviour — the panels are per-locomotive, so a stale answer cannot land on
the wrong train, guard or no guard. But the comment is the kind this codebase's own SOP warns about: a
reader trusting it will believe the panel is mutable-reuse and look for the update path that reassigns
`locomotive`, which does not exist. Written twice in one round, at both sites of the same pattern.

**How it was verified.** Grepped every reference to `locomotive` in the file; one declaration, one
assignment, in the constructor.

**What I would do.** Delete the guards and the reuse sentence, or — if panel reuse is genuinely planned —
say that instead. If the panel can be disposed while the thread runs, the harmless late `setToolTipText`
on a dead component is worth one honest sentence.

### FBR-C2 — untranslated FR-017 keys

**Where.** `autolayout.ui.whyTitle`, `whyWorking`, `whyStanding`, `whyHeaderCandidates`,
`whyHeaderBarred`, `whyNothingToReport`, `whyCouldNotWorkOut` in
`src/org/traincontrol/resources/messages_{da,de,es,fr,it,nl,pl}.properties`.

**What is wrong.** All seven keys carry the English text in all seven non-English bundles. Their
immediate neighbours in the same `autolayout` section are translated — `errorCannotStartWithErrors`,
`warnNoWayOutOfPoint`, and this same round's `warnTimetableEntry` (OB-069) all got real translations in
the same commits. The `autosetup.ui` section does have an untranslated-English precedent, but the
section these keys live in does not.

**How it was verified.** Grepped the seven bundles for the new keys and compared against the
neighbouring keys' treatment.

**What I would do.** Translate them the way `warnTimetableEntry` was, or record that the autolayout
window is deliberately English-only — one or the other, so the next reader is not left inferring.

### FBR-C3 — after OB-077 the failed start-up no longer hangs, and also does not exit

**Where.** `src/org/traincontrol/marklin/MarklinControlStation.java` lines 3700–3762.

**What is wrong.** The fix is real: the latch is counted down in `finally` and `Throwable` is caught, so
a failed `setViewListener` no longer hangs `latch.await()` forever. But the new comment says counting
down after a failed build "hands control back to a caller that can log and exit", and the caller does
neither. After `await()` returns, the code posts `theUI.display()` via `invokeLater` unconditionally
and proceeds to `proxy.setModel(model)`. The `catch (Exception)` wrapping that `invokeLater` — the only
path to `ui.fatalErrorInitializing` and `System.exit(0)` — cannot observe an exception thrown inside
the posted lambda, so it is unreachable for this failure. The result of a failed build is now a live
process with no usable window, a log line, and `display()` throwing on the EDT.

**Why it matters.** Strictly better than the hang, and C for that reason. But the recovery the comment
claims is the recovery a future maintainer will assume exists.

**How it was verified.** Read the full flow from the latch to `proxy.setModel`; confirmed nothing after
`await()` inspects whether the build succeeded.

**What I would do.** Have the lambda record success in a flag the caller checks after `await()`, and
exit (or show a bare dialog) on failure — or amend the comment to say what actually happens.

### FBR-C4 — a chosen blocker that resolves to no node is dropped without a word

**Where.** `src/org/traincontrol/automationui/AutonomyBuilder.java` line 863
(`if (copies.isEmpty()) continue;`), fed by the picker in
`src/org/traincontrol/gui/AutonomyEditorPanel.java` `promptBlockingPoints`.

**What is wrong.** OB-083 excluded the station's own caption square from the blocked-points picker,
because choosing it was self-selection by the back door. The picker still offers every other named
tile in the store (`getNamedTiles()`), including squares that the builder will discard when it emits
`blockedBy`: any square whose `nodesFor` is empty is skipped silently. Two reachable kinds: a named
square on a page excluded from autonomy, and — the same species OB-083 itself established exists — a
named caption square belonging to a *different* station. In both cases the user picks a restriction,
the dialog accepts it, `check()` has no rule about it, and the emitted configuration simply does not
contain it. FR-001 then never holds the station back, and nothing anywhere says so.

**Why it matters.** A safety restriction the user believes is active and is not. It stops short of B
because the demonstrated-by-reading instance needs a cross-page pick onto an excluded page (or the
named-caption data OB-083 encountered), and I could not establish from the code how common either is
in real setups.

**How it was verified.** Read `promptBlockingPoints` (choices come from `getNamedTiles()`, filtered
only for self and self-caption), `AutonomyBuilder` lines 853–868 (the silent `continue`), and
`AutonomyChecks` (no finding class covers an unresolvable blocker). Partially pre-existing — the drop
predates the round — but the round's fix closed exactly one instance of it and left the shape.

**What I would do.** Either filter the picker to squares that will produce nodes, or better, add a
`check()` warning for a `blockedPoints` entry whose square resolves to no node — that also covers
entries that go stale later, which no picker filter can.

### FBR-C5 — stale `@param`

**Where.** `src/org/traincontrol/automationui/AutonomyCompanionStore.java` line 3412: the javadoc for
`readShared` still reads `@param root`; OB-067 renamed the parameter to `wholeRoot` and introduced a
local named `root` that means something different (the filtered copy). Trivial, but this is precisely
the method where the two names now carry a real distinction.

### FBR-C6 — reasons and grouping read under separate locks

**Where.** `src/org/traincontrol/gui/AutoLocomotiveStatus.java` `whyNotReport`, lines 619–660.

**What is wrong.** `explainDestinations(loc)` and `destinationsBarredFromAutonomy(loc)` are each
`synchronized` on the Layout, taken one after the other from the background thread. The design's stated
point — commit `8f5b8d9d`: one `barredFromAutonomy` so "the reason and the group it is printed under"
cannot disagree — holds within one call, but the window's two halves can still be computed against two
different layout states if a dispatch completes between the calls (a station toggled, a train arrived).
A line can then sit in the wrong group for one opening of the window. Transient and cosmetic; the next
click recomputes. Narrow enough that C is generous, but it contradicts the commit's own rationale, so
it is recorded.

**What I would do.** A single synchronized Layout method returning both maps, or accept it with a
sentence at the call site.

---

## D — not defects. Things that looked wrong and are not, findings withdrawn, and checks that came back clean.

| | What was checked |
|---|---|
| **FBR-D1** | OB-067: every write path merges the held entries back |
| **FBR-D2** | OB-067: `pageIsHere` really does mirror `pageOf` |
| **FBR-D3** | OB-067: the index always precedes the read |
| **FBR-D4** | OB-067: value-side holding is not a new loss |
| **FBR-D5** | Withdrawn: a page named after an absent page's id being held |
| **FBR-D6** | OB-071: no page-key colon split remains on the first colon |
| **FBR-D7** | OB-070: both exit doors go through the editor question |
| **FBR-D8** | OB-069: the repair targets the container the timetable actually lives in |
| **FBR-D9** | OB-076: the editor-open proxy is true during a layout refresh, and that is fine |
| **FBR-D10** | The manual-test ledger is clean after the round's document churn |
| **FBR-D11** | FR-015: paths, failure reporting, and the test |
| **FBR-D12** | OB-072: both abandonment paths now set the flag |
| **FBR-D13** | The rewritten source-rule guards can fail |

### FBR-D1 — held entries survive every write

The brief's sharpest question about OB-067. `sharedFields()` (line 1425) is the only builder of the
shared object and it ends by calling `mergeHeld` for all twelve fields; `save()` (line 713) is the only
writer of `setup.json` (`writeJson` at 1917 writes configurations only, `deleteEverything` deletes).
`exportBundle`, `snapshotSetup`, `importBundle`'s pre-merge and both rollback paths (load's and
import's) all go through `sharedFields()`, so a held entry rides through export, snapshot/restore, a
failed load, and a failed import. `withoutAbsentPages` re-derives the held set on every read, and both
`clear()` and `clearShared()` clear it, so it cannot leak across layouts. The `pages` record for absent
ids is merged back too (lines 1449–1452), so the renumber-vs-rename evidence survives a save during the
absence. Checked and clean.

### FBR-D2 — `pageIsHere` against `pageOf`

The comment claims exact agreement and the code delivers it: branch one is `pageOf`'s renumber branch
verbatim; branch two is its id branch; the name fallback returns true exactly where `pageOf` returns
the input unchanged and that input is a legal in-memory name. Walked all four branches against
`pageOf`'s two. Clean.

### FBR-D3 — read order

`withoutAbsentPages` is useless without the index, so the ordering was checked at every load site:
`AutonomySession.open()` calls `setPageIds` before `load()` (lines 103–104); `discardEdits` re-loads
with the index already set; `repairOnDisk` deliberately loads twice, setting the index from the file's
own record between the reads, so its second read holds nothing (every id in `pageNamesWhenWritten` is
in the index it just built) and its first read holds nothing (index empty short-circuits). The
first-read early return also means the repair path keeps id-form keys in memory with empty name maps,
so its writes are identity — which `testRepairingOnDiskChangesOnlyTheLocomotive` already pins. Clean.

### FBR-D4 — value-side holding looks like loss and is not

An entry whose KEY page is loaded is still held when a VALUE square is on an absent page — a station's
protecting-signal list, a portal pair, a caption. While the page is away that pairing is invisible in
the editor, and an edit to the same key wins over the held copy at save ("never over a live one"). This
was checked as a candidate regression and is not one: before OB-067 the same entry loaded with an
unresolvable id-form square in it, was equally ineffective at runtime (the absent page contributes no
nodes), and was equally lost to an overwrite of the same key. The held form is strictly no worse and
survives saves the old form survived. Recorded because it is the round's most loss-shaped behaviour and
the next reviewer will meet it too.

### FBR-D5 — withdrawn: the id-that-is-also-a-live-name hold

Started as a C: `pageIsHere`'s third branch returns false for a stored part that is in
`pageNamesWhenWritten` but resolves nowhere — so a *loaded* page that (a) has no id of its own and (b)
is named identically to an absent page's id would have its entries held and hidden. Withdrawn: with
durable ids (`1a1ec889`) every indexed page carries one, and I could not construct a loaded, id-less
page from the current code. Where the string genuinely is ambiguous, holding is the safe reading — the
entry is preserved verbatim either way. The original severity was C; the reason it was wrong is that
the precondition (an id-less loaded page) is no longer reachable.

### FBR-D6 — the colon sweep

After OB-071 the store's four page-key split sites (`toStored`, `fromStored`, `allHere`,
`parseTileKey`) all use `lastIndexOf(':')`, and `isOnPage`/`rekeyOne` route through `parseTileKey`. The
three remaining `indexOf(':')` sites in the tree (`CommandRow` 495, `RouteEditorFrame` 761/780) parse
route-command settings, not tile keys. No twin left behind.

### FBR-D7 — the exit doors

`WindowClosed` is reached from the window's `windowClosing` (line 7694) and from the Exit menu item
(line 16799); no other `System.exit` is reachable from a running session (the others are start-up fatal
paths and example code). OB-070's question is asked at both doors.

### FBR-D8 — OB-069's repair aims at the right container

Verified that timetable entries are `JSONObject`s keyed `"loc"` (`TimetablePath.toJSON`,
`AutonomyBuilder.LOCOMOTIVE`) and that captured configurations nest the timetable under `"globals"`
(`AutonomySession` line 2429). `repairLocomotiveInTimetable` reads exactly that path with exactly that
key. The `entry == null` skip drops a non-object element, but no writer of this file produces one.

### FBR-D9 — the editor-open proxy

`isLayoutEditorOpen()` is `!editLayoutButton.isEnabled()`, and `layoutEditingComplete` disables that
button for the length of a post-edit refresh — so OB-076's two refusals also refuse during the refresh,
when no editor is open. Checked and judged not a defect: refusing setup writes while the diagram is
being rebuilt is right, and every other user of the proxy has the same semantics. The keyboard door's
log line names an editor as the reason during that window, which is slightly wrong and not worth a
finding.

### FBR-D10 — ledger

`py -3 docs\manual-tests\triage.py verify-ledger` run after reading: clean, exit 0, 38 rows matching 38
open entries, no duplicate refs. The receipts table the OB-067 commit message says was destroyed and
restored is present.

### FBR-D11 — FR-015 details

The relative `new File(DATA_FILE_NAME)` paths match how `saveState`/`restoreState` resolve the same
names (CWD), so the zip picks up the files the app actually writes. A missing source is skipped by
design; an unreadable one is named in the incomplete-backup dialog. `saveState(false)` off the EDT from
the backup thread is the pre-existing pattern of this handler, not new to FR-015. The archive test in
`testAtomicWrite` asserts entry-by-entry contents and the absent-source case; it can fail.

### FBR-D12 — OB-072

`abandoned` is now set on both paths that stop the run (lines 3921 and 3982 of `Layout.java`), before
the trains are stopped, and `return !abandoned.get()` is the single exit. The plain flow's dialog is
driven by that return.

### FBR-D13 — the guards guard

The OB-081 source rule was unfalsifiable as first written (bounded by a javadoc that `withoutComments`
had already removed) and was caught by its author in `717c1291`; the rewrite in `fc82636a` asserts the
site count (2), bounds each window by the next site or the closing catch, asserts the bound was found,
and was mutation-checked per site. Read and confirmed: removing `updateVisiblePoints` from either site
now fails the assert. The FR-017 tests likewise assert their preconditions (a train ON the barred
station, without which both orderings pass) — the commit records seeing it red for the right reason.

---

## What was not covered

The triage.py rewrite in `a1ab7b93` (its own fifteen-finding review) was skimmed, not re-reviewed. The
docs churn in `tests.md`/`issues.md` was checked only via `verify-ledger`. The capture-flag carry in
`86acd990` was read and is sound for the flag; whether entries captured since the last
`captureFromLayout` can be lost across a rebuild was not traced to the bottom and is deliberately not
asserted either way here.

---

## Dispositions

**Claude, 2026-08-24.** Every finding acted on; nothing declined. Battery green after the round, with a
caveat recorded below that this review's method would not have caught either.

| | What was done |
|---|---|
| **FBR-B1** | Fixed. The scan asks a new `couldEverRest`, which is state-independent except for the one occupancy that IS a proof - a blocker staging will never move, meaning one with no home or one already standing on its home. A blocker with a home elsewhere is left to the search, which vacates squares as it takes moves. `testABlockerWithAHomeOfItsOwnIsNotAProofOfImpossibility` was seen red for the right reason; the round's own OB-073 test stays green, because its blocker is unhomed and IMPOSSIBLE is still the right answer for it. |
| **FBR-C1** | Fixed at both sites. The identity half of each guard is gone and the sentences about panel reuse with it; the hover keeps the `noPathsNow` half, which is the part that was ever real. Exactly the defect OB-080 was about, written by me twice in one round while fixing it. |
| **FBR-C2** | Fixed. All seven keys translated in all seven bundles, following each bundle's existing wording for the same ideas. |
| **FBR-C3** | Fixed by making the comment true rather than by amending it. A `built` flag is set inside the posted lambda and checked after `await()`; a failed build now logs and exits instead of going on to `display()` a window that is not there. The old `catch` around `invokeLater` is kept with a note saying what it can and cannot see. |
| **FBR-C4** | Fixed by extending OB-083's own reasoning. The picker excludes every caption square rather than only this station's, and excludes squares absent from the graph - which covers the excluded-page case. The check-side idea in the finding is better for entries that go STALE, which no picker filter can catch; not done, and said here rather than left implied. |
| **FBR-C5** | Fixed, and the javadoc now says why the two names differ. |
| **FBR-C6** | Fixed. One `Layout.explainDestinationsGrouped` returns both halves under a single acquisition. |

### What the battery turned up, which is not this review's finding

Confirming the fixes surfaced [OB-084](../manual-tests/issues.md): `ui.testRenderingCost` fails from a
clean checkout and passes in the battery, because the classes that run before it migrate the sample
layout from setup version 1 to version 2 - and a version 1 setup builds 720 labels for the page where a
version 2 builds 621. Pre-existing, reproduced against a clean HEAD build, and filed rather than fixed:
the label duplication belongs to OB-053, which Adam has asked to be left alone.

It bears on this document only in one way. Every "101 classes green" in this session's commits was
measured with the fixture already migrated. That is honest about what ran and not about a clean
checkout, and it is worth knowing before the number is quoted again as evidence.
