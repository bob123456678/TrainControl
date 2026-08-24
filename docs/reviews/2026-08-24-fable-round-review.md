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

None found *in the original pass*. In particular, the OB-067 change was checked hardest for exactly
this and came back clean — the specific doors checked are itemised in D1 through D5.

Two A findings were raised afterwards, against the fixes rather than against the round: **FBR-A1** and
**FBR-A2**, in the validation pass at the foot of this document.

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

---

## Validation pass, 2026-08-24

**What was validated, and how.** Commit `071f9728` ("The Fable review, acted on"), read against the tree
at `dc15c29b` — the only commit after it touches `docs/manual-tests/tests.md` and nothing else.
Adversarial: the question asked of each fix was not "is this plausible" but "what arrangement makes it
wrong". Read-only on src and test; three things were **run**, all from scratch classes compiled outside
the repository:

- a scratch TestNG class (package `core`, never added to the tree) that builds the staging fixtures and
  calls `HomeStaging.search()` reflectively, to find out what the search would have answered where the
  scan short-circuits it;
- a standalone probe of the `latch`/`built` ordering in `MarklinControlStation.init`, with its control;
- `core.testHomeStaging` (60 green), `core.testMessageBundles` (10 green) and
  `regression.testEditorSurfaceRules` (12 green, 1 red — see FBR-C8).

| | Verdict | Why |
|---|---|---|
| **FBR-B1** | **does not hold** | `couldEverRest` still calls a blocker standing on **its own home** immovable. That is the OB-073 fixture, and `search()` finds a valid three-move plan for it which replays clean against the model — so IMPOSSIBLE is still a false proof. **FBR-B2** |
| **FBR-C1** | **holds** | The field is `private final`, assigned once in the constructor; `noPathsNow` is written and read only on the event thread. Nothing stale can land anywhere. |
| **FBR-C2** | **holds** | ASCII-only (0 non-ASCII bytes in all eight bundles), key sets identical to English, every `{0}`/`{1}` preserved and in a sensible place for the language, no straight apostrophe. |
| **FBR-C3** | **does not hold** | `built.set(true)` runs *after* the `countDown` that releases `await()`, so a **successful** build can be read as failed and `System.exit(0)` a working application. 4554 misses in 20000 in a faithful probe; 0 in 20000 in the control. **FBR-A1** |
| **FBR-C4** | **does not hold** | The caption filter refuses squares that resolve perfectly well — `importLegacy` captions every station square **with itself** — and the dialog silently deletes existing entries it now refuses. **FBR-A2**, **FBR-B3**, **FBR-C7** |
| **FBR-C5** | **holds** | The `@param` names the actual parameter, and the distinction it draws between `wholeRoot` and the local `root` is the real one. |
| **FBR-C6** | **holds** | Both inner methods are `synchronized public` **instance** methods on the same Layout, neither static; monitors are reentrant. The holder carries per-call copies, not Layout state. |
| `testABlockerWithAHomeOfItsOwnIsNotAProofOfImpossibility` | **holds with a caveat** | It can fail and it fails for the right reason — but it does not assert that `setBlockedBy` took effect, which is the one precondition whose loss would let it pass vacuously. **FBR-D19** |
| `testTheBlockedPointsPickerOffersOnlySquaresThatResolve` | **holds with a caveat** | It can fail; it pins both filters and asserts its bounds. But it asserts only that the filters are *present*, and its own failure message states the premise FBR-B3 and FBR-C7 show is false. **FBR-D20** |

---

### FBR-A1 — the `built` flag is read before it is written, and exits a working application

**Where.** `src/org/traincontrol/marklin/MarklinControlStation.java` lines 3712–3770, against
`src/org/traincontrol/gui/TrainControlUI.java` line 5338.

**What is wrong.** `latch.countDown()` is the **last statement of `setViewListener`**. The posted lambda
therefore does this, in this order:

```
theUI.setViewListener(model, latch);   // ... whose last statement is latch.countDown()
built.set(true);                       // line 3721 - AFTER the latch has been released
```

and the main thread does this:

```
latch.await();                         // released by the countDown INSIDE setViewListener
if (!built.get()) { model.logf("ui.fatalErrorInitializing"); System.exit(0); }
```

Nothing orders `built.set(true)` before `built.get()`. The countDown/await pair establishes a
happens-before edge *at* the countDown, and the write is after it, so the edge does not cover it. This is
not a visibility problem that `AtomicBoolean` solves: at the moment the waiter reads, the write has not
been executed. The two are separated by exactly one method return.

**Why it matters.** It is the failure the fix was written to avoid, inverted. On a **successful**
start-up the main thread can read `false`, log "fatal error initializing", and `System.exit(0)` an
application whose window is fully built. Before FBR-C3 a successful start-up always worked; after it,
sometimes it does not. The `finally` countDown is correct and is not the problem — it fires only after
`built.set`, or after a genuine failure.

**How it was verified.** By reading, and then measured. A standalone probe reproducing the exact shape —
worker sleeps, calls a method whose last statement is `countDown()`, then sets the flag; waiter reads the
flag straight after `await()`:

```
rounds=20000  sawFalse=4554        (the shipped ordering)
CONTROL rounds=20000  sawFalse=0   (flag set BEFORE the countDown)
```

The control is the same probe with the two statements swapped, per the SOP's rule about comparing a thing
against itself before concluding two things differ. 0 against 4554 is the ordering and nothing else. The
absolute rate is machine- and timing-specific, and the real `setViewListener` is far heavier than the
probe's `sleep(2)` — the number to take away is not "23%", it is "not rare, and not theoretical".

The path is not reached by the battery: every test calls `init(..., showUI=false, ...)`, which skips the
whole branch. A green battery says nothing about it, and confirming it needs a hands-on start.

**What should be done.** Remove the ordering rather than tighten it. `setViewListener`'s `countDown` is
its last statement and the lambda's `finally` already counts down, so passing the latch in is now
redundant: pass `null` (or drop the parameter) and let the `finally` be the only release — it runs after
`built.set(true)` on success, and after the catch on failure. Setting `built` inside `setViewListener`
immediately before its own countDown works too, but leaves two releases to keep in step.

Two smaller things while in there. `System.exit(0)` reports success to whatever launched the process;
`exit(1)` says what happened. And `model.logf` on this path reaches the window through `invokeLater`, so
the exit almost always beats it to the screen — the `log.info` behind it is the only reason the message
is seen at all.

---

### FBR-A2 — the blocked-points picker now deletes the restrictions it refuses to show

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java` `promptBlockingPoints` — the two new
filters at the head of the loop, against `session.getStore().setBlockingPoints(station, chosen)` at the
foot of it.

**What is wrong.** `chosen` is built **only** from `choices`, and `setBlockingPoints`
(`AutonomyCompanionStore` line 228) *replaces* the stored list. So any existing `blockedPoints` entry the
picker filters out is not shown, is not carried, and is written away the moment the user presses OK —
with no warning, no `check()` finding, and nothing on screen to say it ever existed. Cancel is safe; OK is
the destructive one, and OK is what a user presses after adding one more blocker.

The filters added this round make that set large:

- **every caption square.** `AutonomySession.importLegacy` line 717 does `store.setCaption(tile, tile)` —
  it captions each imported station **with itself**, deliberately ("Every station has to be shown on the
  diagram... The station's own square is the one place that is always right"). After a legacy import,
  therefore, *every station square* has a non-null caption target and is refused by the new filter.
  OB-083's narrower test (`station.equals(getCaptionTarget(tile))`) did not refuse them: a self-caption
  on station X is not a caption on the station being edited.
- **every square absent from `session.getGraph().getTiles()`** — which includes any named square on a
  page the user has excluded from autonomy. Excluding a page is reversible; deleting the restriction
  behind it is not.

**Why it matters.** A `blockedPoints` entry is a safety restriction: FR-001 holds a station back while the
watched square is occupied. Losing one silently is the A case in this folder's rubric, and the finding
this came from opens with the same sentence — "A safety restriction the operator believes is on and is
not is worse than one they were never offered." The fix reproduces that from the other end.

There is one mercy. When the filters remove *everything*, `choices.isEmpty()` returns before the write, so
a fully self-captioned layout gets the "no other points to block with" dialog rather than a wipe. The loss
needs at least one surviving choice, which is the ordinary mixed case.

**How it was verified.** By reading `promptBlockingPoints` end to end — `already` feeds only the check
boxes' initial state, and `chosen` is assembled from `choices` alone — then `setBlockingPoints` (replaces
outright), `AutonomySession.importLegacy` lines 706–719 (`setPointName`, then `setCaption(tile, tile)`),
and `AutonomyCompanionStore.getCaptionTarget` (non-null for any square present in `captions`).

**What should be done.** Carry the refused entries through untouched — union `chosen` with the members of
`already` that were filtered out — whatever else is decided about the filters. That part must not wait on
the argument in FBR-B3, because it destroys data rather than merely hiding it. The check-side warning the
original FBR-C4 recommended, and the disposition declined, remains the better long-term answer for
entries that go stale on their own.

---

### FBR-B2 — `couldEverRest` still turns a movable blocker into a proof of impossibility

**Where.** `src/org/traincontrol/automation/HomeStaging.java` `heldByAnImmovable`, lines 1079–1088,
reached from `couldEverRest` and thence from the impossibility scan in `plan()`.

**What is wrong.** `heldByAnImmovable` calls an occupant immovable when it has no home **or when it is
already standing on its home**. The second is not true. Nothing in the search refuses to move a
locomotive off its home: `astar` (lines 588–631) offers every locomotive every free station, and the only
exemption is the launch-pad rule at line 603. The goal test is `misplaced(current) == 0`, so a locomotive
moved off its home simply has to come back before the plan ends — which is an ordinary three-move plan,
and exactly the one this arrangement needs.

The first half is not sound either, for the same reason: a homeless locomotive is a free agent,
`misplaced` never counts it, and the expansion moves it wherever it likes. It survives only because a
homeless locomotive standing on the graph is nearly unreachable — `Layout.claimHome` (line 4836) gives a
hand-placed free agent a positional home wherever it is put, and `rebuildHomeStations` re-derives one for
anything an assignment did not speak for.

**Why it matters.** The same reason FBR-B1 mattered, unchanged: IMPOSSIBLE is shown to the operator as a
proof with the locomotives named, and it skips the search. What FBR-B1's fix bought is the blocker with a
home **elsewhere** — a train that has been running, whose positional home is where it started. What it did
not buy is the blocker that has not moved since the graph loaded, whose home is therefore the square it is
standing on. Both are ordinary; the round fixed one of them.

**How it was verified.** By running it. A scratch TestNG class (not added to the tree) rebuilt the `ring`
fixture and asked `plan()` and, reflectively, `search()`:

```
fixture: A at HS A homed to HS B; HS B blockedBy [HS D]; B standing on HS D, homed to HS D
plan()   -> IMPOSSIBLE, blocked=[HS alpha], 0 moves
search() -> [HS bravo -> HS C, HS alpha -> HS B, HS bravo -> HS D]
```

The three moves were then replayed against the model, each asserting its destination free beforehand,
exactly as `applyPlan` does. All three applied and A ended on HS B. So a plan exists, the search finds it,
it is executable, and the scan says it is impossible.

**The disposition overstates this, and the commit message repeats it.** The FBR-B1 row says:

> "the round's own OB-073 test stays green, because its blocker is unhomed and IMPOSSIBLE is still the
> right answer for it"

Both halves are wrong. In `testAHomeHeldBackByAnOccupiedPointIsRefusedWhenPlanning` the blocker is **not**
unhomed: `assign` is called for LOC_A only, which makes LOC_B a free agent at that moment, and then
`layout.moveLocomotive(LOC_B, "HS D", false)` runs `claimHome` and gives it a positional home at HS D.
That is precisely why `heldByAnImmovable` fires for it — through the "already home" branch, not the "no
home" one. And IMPOSSIBLE is not the right answer: that same fixture, run above, has the executable
three-move plan. The test stays green only because it asserts `!= READY` rather than `== IMPOSSIBLE`, so
it cannot tell a proof from a refusal.

**What should be done.** The blockedBy test does not belong in the scan at all — which is what FBR-B1's
own "What I would do" offered first ("keep only the state-independent half"). There is no
state-independent statement to make about an FR-001 blocker: staging can move anything that is not sitting
on a launch pad, so no occupancy of a watched square proves anything. Drop `couldEverRest` back to the
plain `canRest(loc, home)` and let the search answer. The state-aware `canRest` at line 679, inside
`firstClearRoute`, is the one that was ever needed and it stays.

That will turn `testAHomeHeldBackByAnOccupiedPointIsRefusedWhenPlanning` red, and that is confirmation,
not regression — the SOP has the paragraph. Its `assertNotEquals(outcome, READY)` pins the wrong property.
What OB-073 was about is that a plan must be **executable**, and the fix for that is the check inside
`firstClearRoute`. Invert it to `applyPlan` the result and assert everyone gets home: that is the property
which would have caught OB-073 and which does not forbid the correct answer.

---

### FBR-B3 — the caption filter refuses squares that are real track

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java` line 2618,
`if (session.getCaptionTarget(tile) != null) continue;`

**What is wrong.** The comment beside it, the disposition, and the new test's failure message all rest on
one claim: "A caption is not track: the reducer emits no point for it." A caption is a square that carries
text about a station, and it may perfectly well be a piece of track with a sensor on it.

- `mayCarryACaption` (line 1766) returns `!isSwitch() && !isSignal()`, and its own comment spells out that
  this is deliberate — "Feedback is the platform road - the square the comment above recommends for a
  station name... so widening the rule for curves and bumpers quietly took away the commonest place of
  all." Captioning a **feedback** square is supported, and is described as the commonest case of all.
- `GraphReducer` and `AutonomyBuilder` never consult `getCaptionTarget`. A captioned feedback square is
  reduced and emitted like any other; `TileGraph.getFeedbackTiles`'s javadoc says every feedback tile
  becomes a Point "without the user asking".
- and `importLegacy` self-captions every station square, as set out in FBR-A2.

So the filter removes genuine, working blockers — including, on any imported layout, all of them.

**Why it matters.** Two ways. A restriction that could have been set can no longer be, on the squares most
likely to want one (a platform road named for its station). And through FBR-A2, a restriction that already
exists and is *actively firing* is deleted the next time the dialog is confirmed.

**How it was verified.** By reading `mayCarryACaption` and its caller at line 1405; `autoCaption`'s two
placement passes, which skip `isFeedback()` for *automatic* placement only and say why the manual rule is
wider; `importLegacy` lines 706–719; and by grepping `getCaptionTarget` across `src` — it appears in
`AutonomySession`, `AutonomyEditorPanel` and `TrainControlUI`, and in neither of the two classes that
decide what becomes a Point.

**What should be done.** Keep OB-083's narrow test — the square captioning *this* station, which is the
self-selection Adam asked to make impossible — and drop the widened one, or replace it with the question
actually being asked: does this square resolve to a node. `getTiles().containsKey(tile)` is a reasonable
approximation of that and can stay. The general answer is still the `check()` warning FBR-C4 proposed.

---

### FBR-C7 — the silent drop FBR-C4 was written about cannot happen

**Where.** `src/org/traincontrol/automationui/AutonomyBuilder.java` line 863,
`if (copies.isEmpty()) continue;`

**What is wrong.** `nodesFor` (line 492) cannot return an empty list. If `splitSides` is empty it adds one
node unconditionally; otherwise, for each side, either `!must && (onwards || !canTurn)` adds the plain copy
or `canTurn` adds the turning one — and `must` implies `canTurn`, because `mandatory` is a subset of
`reversible ∪ mandatory`. Every path through the method adds at least one Node. The `continue` FBR-C4 is
built on is unreachable.

What an unresolvable blocker actually produces is `nodeName(names.get(square), copies.get(0))` with
`names.get(square)` null — `uniqueNames()` is keyed by the reducer's points — and `nodeName` returns its
`base` unchanged when `node.arrival == null`, so a null goes into the `watching` array. Where that lands
in the emitted JSON, and what `parseAuto` does with it, was **not** traced to the bottom and is not
asserted here. What is certain is that the guard the finding cites is dead, so "the builder drops it with
a bare `continue`" describes a line that never runs.

**Why it matters.** For the record rather than for behaviour — the picker filter is defensible on other
grounds, and the consequence of an unresolvable blocker may well be worse than a silent drop rather than
better. But the false premise is now written in three places (the source comment at line 2610, the new
test's failure message, and the disposition), and the next reader inherits it. This is the SOP's "verify
the layer you are actually claiming about", one layer below where FBR-C4 stopped.

---

### FBR-C8 — `regression.testEditorSurfaceRules` is red at HEAD, on line endings

**Where.** `test/regression/testEditorSurfaceRules.java` line 726,
`int ends = source.indexOf("\n    }\n", at);` in `testTheCaptionItemsNameTheStationTheyAreAbout` (added
by `b33c8cbf`, FR-014).

**What is wrong.** `src/org/traincontrol/gui/AutonomyEditorPanel.java` is CRLF in the working tree —
`core.autocrlf` is `true` here, and the file has 5859 CRLFs and no bare LF. The needle wants a bare LF
*after* the closing brace, which CRLF never provides, so `ends` is -1 and the test fails on "could not
find the end of addCaptionItems". Measured: 13 tests, 1 failure, and that is the one.

The sibling rule added this round survives only by luck. `"\n        if (choices.isEmpty())"` has its `\n`
followed by spaces, and `\r\n        if` contains that substring — so it matches under CRLF while its
neighbour does not. Two source rules in one file, one of them portable by accident.

**Why it matters.** Not for shipped behaviour — it is a source-reading rule and the source is correct. It
matters because the Dispositions open with "Battery green after the round", and this class is not green in
the tree the round left behind. Whether it was green when the round measured it depends on what that
file's line endings were at that moment, which cannot now be recovered; the repository copy is LF
(`git show b33c8cbf:...` matches), so the rule was written against an LF working tree. The tree is now
mixed — `TrainControlUI.java` and `AutonomyBuilder.java` are LF on disk while `AutonomyEditorPanel.java`,
`Layout.java`, `HomeStaging.java` and `MarklinControlStation.java` are CRLF — which is what tooling that
rewrites files in text mode leaves behind.

**What should be done.** Make the rule line-ending-blind: normalise once after reading
(`source = source.replace("\r\n", "\n")`) in this test and its sibling, rather than chasing the
terminator. It is one line, and it removes a whole class of "green on my checkout".

---

### D — checks that came back clean, including the attacks that found nothing

| | What was checked |
|---|---|
| **FBR-D14** | FBR-B1: the sensor-sibling loop does match `canRest`'s, and the planned locomotive's exemption is right |
| **FBR-D15** | FBR-B1: the "lost proof" direction is nearly empty — the blocker that cannot move is already flagged by another scan test |
| **FBR-D16** | FBR-C1: the field, the guard that stayed, and the thread each is touched on |
| **FBR-D17** | FBR-C2: escapes, apostrophes, placeholders, key sets — and what `testMessageBundles` does not cover |
| **FBR-D18** | FBR-C6: reentrancy, staticness, and what the holder hands out |
| **FBR-D19** | The staging test can fail, and the one precondition it does not assert |
| **FBR-D20** | The picker test can fail, and what it cannot tell you |
| **FBR-D21** | Withdrawn: `locationOf` picking the wrong copy of a split square |

### FBR-D14 — the sibling loop and the exemption

Compared statement by statement against `canRest(Locomotive, Point, Map)` at line 994: same iteration over
`getBlockedBy()`, same null skip, same `getS88() == null` skip, same
`pointsBySensor.getOrDefault(..., emptyList())`, same `sibling.equals(watched)` skip. Only the predicate
differs, which is the point of the method. No drift.

The exemption of the planned locomotive (`there.equals(loc)` in `heldByAnImmovable`) is right, and matches
both the runtime rule quoted in `canRest`'s javadoc — "the condition should not apply to trains leaving,
only departing" — and `heldBySomebodyElse`. A locomotive standing on the square that holds its own
destination back vacates it by leaving.

### FBR-D15 — the scan did not lose a proof it used to have

The worry was the mirror of FBR-B1: an occupant with a home elsewhere that nonetheless cannot move, so the
scan now spends the whole 15-second budget to answer "maybe" where it could have said "no". Walked the
ways an occupant can be stuck, and in almost all of them the scan flags **the occupant itself** and still
returns IMPOSSIBLE:

- standing on an inactive point — caught by `!locationOf(this.start, l).isActive()`;
- its own home disconnected, inactive, excluding it, too short, or a terminus it cannot reverse out of —
  caught by `couldEverRest`/`connected` applied to the occupant;
- on a launch pad with a home elsewhere — a pad has no *incoming* edges, so it can still leave.

What is left is an occupant that can move but has nowhere free to step aside to. That now costs a search
budget and answers NO_PLAN_FOUND, which claims less than the truth rather than more — the trade the
`SEARCH_BUDGET_MS` javadoc already accepts in its own words. Not a defect.

### FBR-D16 — the dead guards really were dead

`private final Locomotive locomotive` at line 24, assigned once at line 53 in the constructor; `final`
makes the compiler the proof, and grepping every use of the identifier in the file finds no second
assignment. `asked != locomotive` could not have been true, and the reuse sentence described nothing.

The surviving `noPathsNow` half is correct and is not the same kind of thing. It is a plain `boolean` field
written at lines 287 and 387 inside `updateState`, which manipulates Swing components directly and says so
("this runs on the EDT"), and read at line 792 inside an `invokeLater`. Same thread both ways, so no
visibility question arises, and the value genuinely can change between the hover and the answer. It was
the wrong half to delete, and it was kept.

### FBR-D17 — the bundles

Checked by script across all eight bundles: **0 non-ASCII bytes** in every file; key sets identical to
English in all seven translations (0 missing, 0 extra); the placeholder set of each of the seven new values
identical to English's (`whyTitle` `{0}`, `whyStanding` `{0}` and `{1}`, both headers `{0}`, the other
three none); and no `'` in any new value — `fr` and `it` use `’` throughout, as the codebase does.
Placeholder *positions* read correctly by eye in each language: `{0}` is the locomotive and `{1}` the
station in every `whyStanding`, and the counts sit at the end of both headers.

`core.testMessageBundles` runs green (10 tests) and covers the apostrophe rule, the ASCII rule, key-set
equality, continuations, duplicates and the printf rule. One thing it does **not** cover: that a
translation preserves English's placeholders. `testEveryFormattedMessageHasAPlaceholder` reads
`messages.properties` only, so a translation that dropped `{1}` would pass it. That gap is not this round's
doing, and the seven new values are clean by the script above — but it is a rule worth having.

### FBR-D18 — the grouped read

`explainDestinationsGrouped`, `explainDestinations` and `destinationsBarredFromAutonomy` are all
`synchronized public` **instance** methods on `Layout`; none is static, so all three take the same monitor
and the nesting is plain reentrancy. No deadlock is introduced — nothing else is acquired inside.

The holder does not leak Layout state. Both values are collections built inside the two synchronized
methods and returned by value (`new LinkedHashMap<>` / `new LinkedHashSet<>` per call), so the getters hand
out per-call copies. They are not wrapped unmodifiable, which is untidy and harmless.

Callers were swept: `explainDestinationsGrouped` has exactly one, and the two remaining bare
`explainDestinations` calls (`AutoLocomotiveStatus.whyNotToolTip` line 809, `AutonomyEditorPanel` line
4509) want only the reasons and do no grouping, so neither is the same defect wearing a different hat.

One cost, recorded rather than raised: the Layout monitor is now held across *both* walks instead of being
released between them, so a dispatch waiting on it waits marginally longer. Both walks ran back to back
before; what changed is atomicity, not work.

### FBR-D19 — the staging test

It can fail — it is the assertion that catches the pre-fix behaviour, and the same assertion in the same
shape is what caught FBR-B2 above. It asserts the fixture arranged itself (`moveLocomotive` returning true
is a real statement that the blocker is standing on HS D), and its failure message says what it means.

The caveat is the SOP's paragraph on preconditions. Two things make this test meaningful and neither is
asserted: that `HS B.getBlockedBy()` is non-empty when `plan()` is called, and that LOC_B's home is HS C
rather than HS D at that moment. The second is fail-safe — if the home moved, the test goes red. The first
is not: if `setBlockedBy` ever stopped taking effect, the plan would be READY for a reason that has nothing
to do with the finding, and the test would pass while exercising nothing. One line closes it
(`assertFalse(layout.getPoint("HS B").getBlockedBy().isEmpty(), ...)`).

### FBR-D20 — the picker test

It can fail: both `assertTrue(building.contains(...))` calls go red if either filter is removed or moved
out of the loop, and the bound assertions (`at > 0`, `ends > at`) fail loudly rather than silently widening
the window. It reads the source deliberately and says why.

Two caveats. It asserts the filters are *present*, not that they are *right* — so it cannot notice FBR-B3,
and its own failure message asserts the premise FBR-C7 shows is false ("A caption is not track: the
reducer emits no point for it"), which means the message will actively mislead whoever it fires at. And it
matches raw source text with `\n`-prefixed needles, which is portable across line endings only by the
accident described in FBR-C8.

### FBR-D21 — withdrawn: `locationOf` and the copies of a split square

Raised while reading `heldByAnImmovable`: it asks `locationOf(this.start, there)` — a linear scan for the
first key whose value is that locomotive — when it already knows the square, `p`. A square is several
Points on a derived graph, so if a locomotive were ever recorded on two of them the scan could answer with
the wrong copy and either invent or lose an "already home".

Withdrawn: it cannot happen. `Layout.moveLocomotive` calls `clearBlockExcept(target)` before
`setLocomotive`, and `setLocomotive` clears the train off everywhere else — "one locomotive, one place" —
so `start` holds one entry per locomotive, which is what `canEnter`'s own comment says from the other side.
Passing `p` instead of re-deriving it would still be the smaller code and would remove the assumption
rather than depend on it; that is a tidy-up, not a defect.

---

### What this validation pass did not cover

The seven findings and their two tests, and nothing else in the round. The OB-067 work behind FBR-D1
through FBR-D5 was not re-verified. `AutonomyChecks` was read only for whether a rule about unresolvable
blockers exists (it does not); no other check-side rule was reviewed. FBR-C7 stops at "the guard cannot
fire" and does not follow a null point name through `parseAuto`. Nothing was exercised through the user
interface — FBR-A1 in particular needs a hands-on start, since every test in the battery takes the
`showUI=false` branch that skips the code entirely.

---

## Dispositions for the validation pass

**Claude, 2026-08-24.** Every finding upheld and acted on. Nothing was argued with, because on reading
the code each one was right, and two of them were right about changes that were worse than the defects
they replaced.

| | What was done |
|---|---|
| **FBR-A1** | Fixed. `setViewListener` is passed `null` and the lambda's `finally` is now the only release, so the flag is always set before the waiter can read it. The exit code on that path is 1 rather than 0. Verified by reading the ordering the finding names: `countDown` really is that method's last statement. |
| **FBR-A2** | Fixed, and independently of the argument about the filters. Anything stored that the picker did not OFFER is carried into `chosen` before the check boxes are read, so OK can no longer delete what it did not show. Written as "keep what was not asked about" rather than "restore what the filters hid", so it stays right whatever the list stops offering next. |
| **FBR-B2** | Fixed by removing the occupancy test from the scan entirely, which is what FBR-B1's own "What I would do" offered first and I did not take. `couldEverRest` and `heldByAnImmovable` are gone. |
| **FBR-B3, FBR-C7** | Fixed by reverting FBR-C4's filters to the OB-083 state. The reasons are written into the code beside the surviving check, because the widening looked obviously right and the next reader deserves to know why it is not there. |
| **FBR-C8** | Fixed and verified the hard way: the source is read with carriage returns stripped, and the test was then run against a CRLF copy of both files to confirm it stays green. |
| **FBR-D19** | Taken. The sibling test now asserts `getBlockedBy().size() == 1` before doing anything else. |
| **FBR-D20** | Moot - the test was deleted with the fix it pinned. |

### The OB-073 test, inverted

`testAHomeHeldBackByAnOccupiedPointIsRefusedWhenPlanning` went red the moment the scan stopped proving
things about occupancy, which is what the SOP says to expect. It is now
`testAHomeHeldBackByAnOccupiedPointStillGetsAnExecutablePlan`: it asserts a plan comes back, replays it
move by move through `applyPlan` - every move finding its destination free at the moment it runs - and
asserts everyone ends up home.

That is the property OB-073 was ever about, and `!= READY` was not it. The old assertion could not tell
a proof from a refusal, which is exactly why it stayed green through two wrong fixes in a row.

### Two things I got wrong that are worth naming

**I acted on a finding without checking its premise.** FBR-C4 rested on `AutonomyBuilder` silently
dropping an unresolvable blocker. That line cannot be reached. I read the `continue`, agreed it looked
bad, and wrote a fix, a test and a comment - all three asserting something untrue - and the fix then
deleted the very restrictions the finding was about protecting. The SOP's first rule under "Before
calling something a finding" is to verify the layer you are claiming about, and it applies to the
reviewer's layer as much as to your own.

**And the FBR-B1 disposition overstated its own evidence.** It said the OB-073 fixture's "blocker is
unhomed and IMPOSSIBLE is still the right answer for it". Both halves were wrong - `moveLocomotive`
calls `claimHome`, and that arrangement has an executable plan - and I wrote it from the test staying
green rather than from reading what the test asserts. A green test is evidence about the assertion, not
about the behaviour.

Both are the same mistake in different directions: trusting a description of the code instead of the
code. It is the mistake this folder's README opens with, and it produced an A-severity defect on the
start-up path that no test in the battery can see, because every test runs with `showUI=false`.
