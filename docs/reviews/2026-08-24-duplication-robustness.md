# Duplication and robustness review — 2026-08-24

**Status:** open

**Prefix for citing this document: `DR`.** Taken elsewhere and not reused here: `DD`, `FR`, `FV`,
`GC`, `IR`, `ISD`, `LT`, `AR`, `FBR`, `FSR`, `IAR`.

**Reviewed at `f1befe72`** (`autonomy-diagram-r0` HEAD, "The second Fable round"), 2026-08-24. The
working tree held one untracked review document (`2026-08-24-independent-application-review.md`,
prefix `IAR`, running in parallel — cited below where our subjects touch) and five untracked jars
under `resources/`. **No source or test file was changed by this pass.** This document is the only
file it wrote into the repository. Mutated copies of `AutonomyCompanionStore.java` were compiled and
run in the session scratchpad, outside the tree, to test whether the guards can fail — the receipts
are in DR-A1 and DR-D1.

**Scope.** Duplication and robustness in the past week's model changes, commissioned by Adam "to
minimize issues such as what we dealt with with page management and autonomy linkage." The shape
being hunted is the one both of those shared: one decision, written down in several places, which
then drift. Files: `AutonomyCompanionStore`, `AutonomySession`, `AutonomyBuilder`, `TileGraph`,
`GraphReducer`, `AutonomyChecks`, `Layout`, `Point`, `Edge`, `HomeStaging`, `LayoutDiagram`, and the
five source-reading ratchet tests.

**Method.** The store and the five ratchets were read in full by hand. Two parallel readings covered
the automation package and the derivation chain against `git log --since=2026-08-17 -p`; **every
claim promoted to A or B below was re-checked against the source by hand before it was written
down**, and two claims from those readings did not survive the check — both are recorded in DR-D4.
Three test classes were run individually at baseline (green), and two mutants of the store were
compiled in the scratchpad and run against the guards; nothing bound the UDP port twice and
`ui.testRenderingCost` (OB-084) was not run.

**Severities** follow [README.md](README.md), read for a duplication review the way DD read them:

| | |
|---|---|
| **A** | Drift or the unhappy path silently loses data, and nothing would catch it. |
| **B** | Drift produces incorrect results, a wrong report, or a crash in specific configurations. |
| **C** | Worth fixing, no urgency. |
| **D** | Not defects: checks that came back clean, claims withdrawn, twins that must stay twins. |

**Prior findings are cited, not re-found.** DD-A1/OB-025 (the store's collections) is open and stays
theirs; what this pass adds is that the mechanism grew a new, unguarded storey this week (DR-A1).
DD-A7 and DD-B9 are open; what this pass adds is that both families gained new members this week
(DR-B6). Status updates for everything cited are collected in DR-D5.

---

## Ranking

By expected value, not by number:

| Rank | Finding | Why here |
|---|---|---|
| 1 | **DR-A1** | One string in a hand list away from the exact OB-067 loss, proven by a mutation every guard passed. The fix is a test column and a constant. |
| 2 | **DR-B8** | The matrix runs id-blind, so the whole translation layer — where both of this month's losses lived — is outside the one guard that claims the store. |
| 3 | **DR-B1** | Three lines. Without them the only instrument keeping eight duplicated rules honest cries wolf on every FR-001 layout. |
| 4 | **DR-B9**, **DR-B10** | Logging parity at four doors, and showing a report five doors already compute and throw away. |
| 5 | **DR-B7** | The `getHomeStations` fix, applied to the two siblings its own javadoc describes. |
| 6 | **DR-B3**, **DR-B2** | Rule-copy consolidations in `Layout`/`HomeStaging`. Real work; do after the cheap ones. |
| 7 | **DR-B4**, **DR-B5**, **DR-C5** | Page-id surface. Most of it is dissolved by FR-013, which argues for doing FR-013 rather than patching these separately. |
| 8 | **DR-B6** | New members of the DD-A7/DD-B9 families. Fold into whatever finally acts on DD-A7. |
| 9 | **DR-C\*** | Ride-alongs. |

---

## A — one edit from silent data loss, and nothing would catch it

| # | Finding | Status |
|---|---|---|
| DR-A1 | The held-entries mechanism keeps three hand lists of field names outside every guard; a dropped name silently deletes a page's settings from disk | Open |

### DR-A1 — the held-entries field lists are outside every ratchet, and a drift there is the OB-067 loss again

**Where.** `src/org/traincontrol/automationui/AutonomyCompanionStore.java` —
`withoutAbsentPages` (3239–3281, four shape-classified arrays of field names),
the merge-back array inside `sharedFields` (1476–1481), against
`test/regression/testStoreCollectionsAreHandledEverywhere.java` (`SITES`, 90–98) and
`test/regression/testAutonomyStoreSettingsMatrix.java`.

**The mechanism.** OB-067's fix (`ef70c3ab`) holds entries for absent pages out of memory and merges
them back verbatim at save. To do it, the store now carries the twelve kept collections' *field
names* in three more hand-written places: the four hold lists in `withoutAbsentPages` (which also
encode each field's shape — plain, square-valued, list-valued, page-keyed), and the twelve-name
merge array in `sharedFields`. These sit on top of `KNOWN_SHARED`, the writer list, the reader list
and the untranslate list — the same class of list DD-A1 counted fourteen of.

**None of them is governed.** `testStoreCollectionsAreHandledEverywhere.SITES` names thirteen sites
and `withoutAbsentPages` is not one; the merge array lives inside `sharedFields`' body, so the
mention test is satisfied by the `root.put(...)` writer line whether or not the field is in the
array. `heldForAbsentPages` itself is exempted in `NOT_KEPT` (line 84) with a reason that is true of
the *map* ("no bookkeeping site touches it") and does not cover the *field lists*.

**Proven by mutation, not asserted.** In a scratch copy of the store, `"blockedPoints"` was removed
from the merge array in `sharedFields` — nothing else — and the mutant compiled and run against the
three guards that claim this ground:

```
regression.testStoreCollectionsAreHandledEverywhere   Total tests run: 3, Failures: 0
regression.testAutonomyStoreSettingsMatrix            Total tests run: 7, Failures: 0
regression.testPageIdsAreDurable                      Total tests run: 8, Failures: 0
```

A probe against the same mutant then reproduced the loss: a `blockedPoints` entry on page Ghost,
Ghost's file goes absent, one load-and-save later the file holds
`"pointNames": {"1:3,3": "Ghost Platform"}` — the held name merged back — and
`"blockedPoints": {}`. **One save while a page's file was missing silently deleted that page's
FR-001 restrictions from disk, with every guard green.** That is the sentence OB-067 was closed with,
minus one string in one array.

The other direction of the drift is as quiet: a collection added to the merge array but not to the
hold lists enters memory with a page **id** standing where a page **name** belongs — the exact pun
the mechanism exists to eliminate (`heldForAbsentPages`' own javadoc, 406–441).

**Why A.** The store gained its twelfth collection this week (`blockedPoints`, FR-001) and its
eleventh took five commits to finish wiring (DD-A1). Collection number thirteen is a matter of time,
the ratchet will force it into thirteen sites and say nothing about these three — and the failure
mode is silent loss written to disk, discovered days later, which is this file's entire history.

**What I would do.** Two things, separable:

1. **Extend the matrix by one operation column: "save and load while a page is absent."** Write the
   setting on page Ghost, drop Ghost from the index, reload, save, reload, assert the setting is
   still in the file and not on any live page. Twelve cells; the mutation above fails four ways
   against it. ~40 lines in a file that already has the shape. This is the guard with teeth, because
   the hold lists' *shape* dimension (`valueIsASquare`, `valueIsSquares`) cannot be checked by name
   mention at all — only behaviour catches a field held with the wrong shape.
2. **Add `withoutAbsentPages` to `SITES`**, and derive the merge array and the hold lists' union
   from one constant so they cannot disagree by count. ~10 lines.

**Cost and risk.** Test-only plus one constant; low. What could go wrong: the matrix column must set
page ids (see DR-B8) or it exercises nothing — the two findings want one commit.

---

## B — drift produces wrong results or a crash, in reachable configurations

| # | Finding | Status |
|---|---|---|
| DR-B1 | The staging audit lacks an FR-001 exemption and falsely accuses the planner on every FR-001 layout | Open |
| DR-B2 | FR-001 is written three inequivalent ways: runtime, planner, test oracle | Open |
| DR-B3 | The "sendable destination" conjunction exists three times, and the why-window can never name the FR-001 reason | Open |
| DR-B4 | Two parsers of `gleisbild.cs2`; a swallowed index-read failure renumbers every page; two pages on one id is silent everywhere | Open |
| DR-B5 | `pageIsHere` restates `pageOf` and must "agree with it exactly" by prose alone | Open |
| DR-B6 | The arrival-sides walk and the facing rule each gained a new copy this week (delta on DD-A7/DD-B9) | Open |
| DR-B7 | `getPoints()`/`getEdges()` are live views read off-thread; the `getHomeStations` fix was not swept to its siblings | Open |
| DR-B8 | The settings matrix runs with no page index, so the id-translation layer is untested by it | Open |
| DR-B9 | `parseAuto` resolves names at four doors; two log the drop and two are silent, and all four persist it | Open |
| DR-B10 | The absent-page rule is enforced four different ways and reported to the user at none of its six doors | Open |

### DR-B1 — the audit that keeps the planner honest cries wolf on FR-001

**Where.** `src/org/traincontrol/automation/HomeStaging.java:405–468` (`auditAgainstRuntime`);
`src/org/traincontrol/automation/Layout.java:1924` (the `isAutoRunning()` fence on the FR-001
clause in `isPathClear`), `:5442–5445` (the one production call, behind `isDebug()`).

`auditAgainstRuntime` is the compensating mechanism for the eight rules `HomeStaging` re-implements
against its shadow state (its own javadoc: *"it re-implements the rules, and every time a rule was
mis-copied the result was a plan the runtime then refused, or no plan where one existed"*). It
compares `getPossiblePaths` (runtime) against `firstClearRoute` (planner) per locomotive, and it
already carries **three** hand-written exemptions for divergences that are correct rather than
defects: an inactive origin (419), an inactive destination (441), an excluded destination (448) —
each with a comment explaining that the runtime's rule is not in force at rest.

OB-073 (`72195922`) created a fourth such divergence and did not add the exemption. The planner's
state-aware `canRest` (1014–1046) applies FR-001 always; the runtime's copy is fenced behind
`isAutoRunning()` (Layout:1924), and the audit runs from `planReturnToHome` with the layout at rest.
So on any layout using FR-001, a train standing on a watched square makes the audit log
`autolayout.warnStagingPlannerTooStrict` — a false accusation against the planner, from the
instrument that exists to keep the copies honest. Debug-only, so it fails loudly, wrongly, in a
channel that is only read when something else is already being chased — which is exactly when a
phantom divergence costs the most.

**What I would do.** The fourth exemption, three lines, in the shape of the three above it — skip a
destination whose `getBlockedBy()` chain holds somebody other than `loc`. What could go wrong:
an exemption written too wide (skipping every FR-001 destination rather than the currently-held
ones) would blind the audit to a real mis-copy of the rule; mirror the planner's `canRest` loop
exactly, and say so beside it.

### DR-B2 — FR-001 exists in three forms that answer differently on real layouts

**Where.** `Layout.java:1928–1955` (runtime: `getBlockLocomotive()`, block-aware, fenced);
`HomeStaging.java:1014–1046` (planner: s88-sibling-aware via `pointsBySensor`, unfenced, null-guards
a blocker the other copy's constructor makes impossible);
`test/core/testHomeStaging.java:176–196` (the replay oracle: `getCurrentLocomotive()`, point-level,
and no exemption for the departing train).

The three differ in the "same piece of track" question. The runtime asks the tile/block; the planner
asks sensor siblings, which is a **superset** — and `AutonomyBuilder.java:836–839` states outright
that genuinely different places share a sensor on real layouts (*"a station, its approach guard and
a reversing point can be three Points on one feedback — so the sensor cannot say which Points are
one square"*). On such a layout the planner refuses arrivals the runtime allows: fails safe, but it
is the "planner is the stricter half" failure this class has been burned by before
(`HomeStaging.java:720–737`), and its symptom is OB-073's own — `NO_PLAN_FOUND` or a fleet
half-staged. `canEnter`'s comment (`HomeStaging.java:866–870`) already says the point-level and
block-level occupancy rules *"agree by coincidence rather than by construction"*; the builder
comment above is the layout on which the coincidence ends.

The oracle is the weakest of the three: `applyPlan` asserts the watched square's
`getCurrentLocomotive()` is null with no departing-train exemption, so a legal plan whose move *is*
the departing train would fail the test that grades the other two copies.

**What I would do.** Decide block-vs-sensor once, in one method both sides call with their own
occupancy source — the `PathRules` shape the DD appendix sketched, scoped to this one rule rather
than all eight. Then give the oracle the same exemption the two production copies share. Risk:
medium — this is the rule that throws real ironwork behind arriving trains; red-before-green per
copy, and the audit (DR-B1) is the regression net once it stops crying wolf.

### DR-B3 — the "sendable destination" conjunction is written three times, and the why-window has a ceiling it cannot see over

**Where.** `Layout.java:3195–3197` (`pickPath`'s filter), `:3366–3378` (`barredFromAutonomy`),
`:5195–5201` (`canReachAnyDestination`); the three candidate skips written twice at `:3403–3409`
and `:3460–3462` (the second admitting it: *"The same three skips explainDestinations makes"*); the
priority comparator written twice (`:3148–3158`, `:3450–3451`).

FR-017 (`8f5b8d9d`) did the right thing at the decision layer — `explainDestinations` calls
`isPathClear` itself, so the yes/no can never be a second copy, and `barredFromAutonomy` is one
function with two callers, built (its javadoc says) because *"two copies of this list would be two
answers to 'can autonomy pick this station'."* In building it, the standing terms of `pickPath`'s
conjunction acquired their third expression (`canReachAnyDestination`, the stuck-train advisory, is
the second — deliberately minus the per-locomotive exclusion). They agree today. The next term added
to one of them — and FR-001 was a term added this month — has three homes and no guard; when they
drift, the advisory or the why-window disagrees with the railway, which is DD-A7's defect class at
the Layout tier.

Two attribution defects are live now, not waiting:

1. **The FR-001 reason can never appear in the FR-017 window.** `firstClearOrWhyNot`
   (`Layout.java:3540–3541`) substitutes `autolayout.why.blockedWhileRunning` for `getLastError()`
   whenever autonomy is running — deliberately, because `lastError` is static and cross-thread
   (`:517`, comment at 3529–3537) — and the FR-001 clause only *fires* when autonomy is running
   (`:1924`). So `autolayout.errorDestinationBlockedByPoint`, the one message that names the watched
   square, is structurally unreachable from the window: a permanently held-back station reads as
   "blocked by a train or a route in progress", i.e. temporarily busy. The user Adam built FR-017
   for is told to wait for a condition that will not clear by waiting.
2. **The "same order pickPath walks" claim at `:3446–3447` is inexact.** `pickPath` shuffles before
   sorting (`:3145`) and, under a non-random path preference, ranks within a priority band by cost
   (`:3212–3226`); `explainDestinations` does neither. With more than one available candidate the
   window's first entry is not reliably the one pickPath takes — a comment claiming agreement
   between two copies that the code does not enforce.

**What I would do.** (1) Pass the reason out of `isPathClear` per call instead of through the static
— a small `String[] whyOut` or a return object on the one internal path FR-017 uses — so the fence
at 3540 can go; that is also the only way FR-001 becomes explainable. (2) Extract the standing
conjunction into one predicate with the exclusion term parameterised, used by all three; keep the
transient occupancy term out of it, since `explainDestinations` deliberately separates standing from
transient. (3) Reword or implement the ordering claim. Risk: (1) touches the hottest path in the
file; the reason-out parameter must not change the decision, only carry the words.

### DR-B4 — the page-id rule has two parsers, one swallowed failure that renumbers everything, and one silent collision

**Where.** `src/org/traincontrol/base/LayoutDiagram.java:840–885` (`readLayoutIndexIds`),
`:900–986` (`writeLayoutIndex`); `src/org/traincontrol/marklin/file/CS2File.java:2004`
(`page.put("id", m.get("id") != null ? m.get("id") : String.valueOf(position))`);
`AutonomyCompanionStore.setPageIds:465–479`.

Three parts, one subject:

1. **Two parsers of one file.** `readLayoutIndexIds` and `CS2File`'s page loop both parse
   `gleisbild.cs2`, both applying "absent id = position" — connected only by the sentence at
   `LayoutDiagram.java:835` (*"which is what CS2File does with the same file"*). One feeds the id
   allocator; the other feeds `setPageIds` and therefore every stored key. If they drift — and a
   corrupt `.id=` line already takes different branches: `LayoutDiagram:869–872` silently falls back
   to position, `CS2File` passes the unparsed string through — the setup is keyed by ids the index
   does not believe, which is the misattachment class with no rename anywhere in sight.
2. **A swallowed read failure renumbers every page.** `readLayoutIndexIds:880–884` answers an
   unreadable index with an empty map, and its comment — *"every page gets a fresh id, which is
   what happens today anyway"* — predates `1a1ec889` and is no longer true: with `existing` empty,
   `writeLayoutIndex` reissues 1..n to every page and writes it. A transient OneDrive lock on
   `gleisbild.cs2` at the moment of any page add/rename/delete is enough. The store's renumber
   detection and `pageOf`'s follow-the-name branch then self-heal it *provided* `setup.json`'s
   `"pages"` record survived — the recovery leans entirely on the mechanism DR-A1 shows is
   unguarded.
3. **Two pages claiming one id is silent everywhere.** Nothing in the chain compares:
   `writeLayoutIndex` writes duplicates back out, `setPageIds:477` inverts last-wins into
   `pageIdToName`, and `pageIdConflicts` only fires when an id's *name* changed. Half of one page's
   setup resolves to the other with no warning to fire.

**What I would do.** A loud log (not a silent empty map) at `readLayoutIndexIds:880`, a duplicate-id
check in `writeLayoutIndex` (refuse or reissue, either is better than write-through), and one shared
"parse the index" door if anyone is in the file anyway. Much of the *key* side of this is FR-013's
territory; the parser and allocator side is not, and stays live after FR-013. See also IAR-A1, which
proves the adjacent retired-highest-id reissue against a live probe — these two findings want to be
fixed by whoever does FR-018, together.

### DR-B5 — `pageIsHere` restates `pageOf`, and only a comment keeps them agreeing

**Where.** `AutonomyCompanionStore.java:3681–3691` (`pageOf`), `:3702–3727` (`pageIsHere`), whose
own comment says the quiet part: *"The two ways pageOf succeeds, in its order, because this has to
agree with it exactly."*

`pageOf` cannot signal failure — it returns the id, and an id is a legal page name — so `pageIsHere`
was written to re-ask its two questions in boolean form. One resolution rule, two methods, agreement
by prose. When they drift: entries held that should have loaded (a page's setup vanishes from the
screen while staying safe in the file), or entries loaded that should have been held (the id-as-name
pun of OB-067). FR-018, if taken, changes `pageOf`'s rules — and this is the twin that must change
in the same commit or the held set and the live set stop partitioning.

**What I would do.** One private resolver returning a sentinel (`null` for "not here"), with
`pageOf` and `pageIsHere` as two-line wrappers. ~15 lines, low risk, and it removes the only reason
the comment exists. FR-013 dissolves the question entirely; do this only if FR-013 stays parked.

### DR-B6 — the DD-A7/DD-B9 families each gained a member this week

Cited, not re-found: DD-A7(1) — the checker's trapped-arrival walk skipping null-entry-side edges
where `AutonomyBuilder.splitSides:437–441` bails on the whole square — is open and unchanged
(`AutonomySession.java:2649`, `:2671`). What is new:

- **A third inline copy of the arrival-sides walk** arrived with `facingChoices`
  (`AutonomySession.java:3021–3041`, commit `8a5e1951`), same loop, same
  `getEntrySide() != null` skip — while the correct door (`arrivalSides` → `StationIndex` →
  `AutonomyBuilder.arrivalSidesOf` → `splitSides`) exists at `:1836` and none of the three loops
  uses it. The builder's own comment at `AutonomyBuilder.java:419–420` (*"The same answer the split
  itself uses, so the editor cannot offer a restriction on a side the build has no copy for"*) is
  the model those loops should follow.
- **A new same-rule pair kept in step by a sentence.** `AutonomySession.onwardFrom:3064` (over
  `getRoutes`) against `AutonomyBuilder.facingOf:655` (over the copy's routes): the session's
  javadoc at `:3013–3015` asserts *"onwardFrom and AutonomyBuilder.facingOf now answer alike"* —
  an agreement claim with no test behind it, in the family whose last drift was MT-125 (a curve
  offering compass-opposite facings).

When they drift, the menu offers facings the build has no copy for, or the checker reports a trap
the builder does not emit — the interface telling the user one thing and the railway doing another,
which is DD-A7's opening sentence. **What I would do:** route the three loops through
`arrivalSides`, and add the one agreement test DD-A7 asked for and still does not have (build from
the sample diagram; assert checker verdicts against the built graph for trapped arrivals and
reachability). Until that test exists, every consolidation here is unverifiable by anything but
another reading.

### DR-B7 — the `getHomeStations` fix was not swept to the two siblings read in the same call

**Where.** `Layout.java:4869–4884` — `getEdges()` and `getPoints()` return
`this.edges.values()` / `this.points.values()`, live views of plain `HashMap`s (`:563–564`);
`HomeStaging.snapshot` walks `layout.getPoints()` at `HomeStaging.java:117` and `:148` with no lock
(the class has zero `synchronized`). Contrast `getHomeStations` (`Layout.java:945–961`), which
copies under the monitor and whose javadoc names this exact hazard — *"a
ConcurrentModificationException in a worker with nothing to catch it, or the quieter outcome of a
plan derived from half the homes"* — and closes with *"the version of this that has to be got right
once rather than at every call site."* It was got right once, at one of three accessors the same
planner reads in one call. This is the repository's most-repeated defect shape (the README's
"grep for its twins" rule), applied to its own fix.

Reachable: the staging search runs off the EDT (`a51a6eb8`), and points/edges are mutated by
deletion and sync paths under the Layout monitor.

**What I would do.** Copy-under-monitor in `getPoints()`/`getEdges()`, same one-line shape, same
javadoc rationale — after checking the hot readers: `pickPath` and `bfs` iterate `points.values()`
internally rather than through the public accessors, so the copies land only on external callers.
Cost: two small allocations per external call; risk low.

### DR-B8 — the settings matrix never exercises the id-translation layer

**Where.** `test/regression/testAutonomyStoreSettingsMatrix.java` — `store()` builds
`new AutonomyCompanionStore(null)` and nothing in the class calls `setPageIds`, so
`testEverySettingSurvivesASaveAndLoad` (298–330) round-trips every setting **by name**:
`toStored`/`fromStored` pass keys through unchanged, `withoutAbsentPages` returns early
(`pageIdToName.isEmpty()`, store:3243), and the held path never runs.

Consequence: the matrix — the guard whose charter is "every setting against every structural thing"
— cannot catch the *written raw* defect class, which the store has already had once
(`excludedPages`, the comment at store:1466–1469: *"This was the one collection written raw, and it
broke the rule setPageIds states"*). A thirteenth collection whose author forgets the translate
call passes all sixty cells today. Together with DR-A1 this means both new storeys of the store —
translation and holding — sit outside the one test that reflects over its fields.

**What I would do.** Give the save/load column ids: `setPageIds` before save, a re-keyed index on
reload, assert the setting still reads back — one fixture change exercises translation for all
twelve rows; then the held column from DR-A1. `testPageIdsAreDurable` covers this ground for
`pointNames` only, which is the "tested one setting, the code was written one setting at a time"
history the matrix's own javadoc warns about.

### DR-B9 — `parseAuto` resolves names at four doors; two log, two are silent, and all four persist the drop

**Where.** `Layout.java` — home: `:6140–6162` (logs `warnHomeLocomotiveNotInDatabase`, drops);
`blockedBy`: `:6826–6854` (logs `warnBlockingPointNotFound`, drops); `excludedLocs`: `:6172–6186`
(**silent** — `if (getLocByName(locName) != null)` and the name is gone); `locomotivesToRun`:
`:6810–6822` (**silent** — a comment explains the skip, nothing reaches the log).

The policy is right (drop one entry, never refuse the layout — the tolerant direction, chosen
deliberately after this month's invalidate-the-world failures). The application of it is a guard at
two doors and not their twins. What the silent pair costs when hit: an *exclusion* silently
vanishing is a safety restriction the operator believes is in force and is not — a locomotive sent
to a platform it was barred from; a *run-list* name silently vanishing is a train that never moves
with nothing saying why. Both are then re-serialised without the entry, so the drop is permanent
and invisible. Reachability is narrowed by this week's rename/delete repairs (stale names should now
mostly be repaired before they get here), which is exactly why the remaining hits — a hand-edited
file, a partially-loaded database — deserve the log line the other two doors already have.

**What I would do.** Two `control.logf` calls in the shape of the two that exist. Ten minutes.

### DR-B10 — the absent-page rule is enforced four ways and reported at none of its six doors

**Where.** The decision "a page that is not loaded must not be judged" is enforced by four different
mechanisms: `AutonomySession.save` (`:3919–3930`, `pagesNotLoaded` + `isPageNumberingSuspect` →
decline to reconcile), the store's held entries (OB-067), `captureFromLayout`'s `pagesInPlay`
(`:2395–2412`), and `forgetArrivalsThatNoLongerExist`'s station-index membership test (`:207`).
All four are individually correct — verified, see DR-D2 — but:

- **The fourth runs outside the guard.** `save()` calls `forgetArrivalsThatNoLongerExist()` at
  `:3865`, before `incomplete` is computed at `:3921`. It is protected today by two accidents (held
  entries never reach the live map; the index-membership skip), not by the rule the method is built
  around, and nothing says so at the call.
- **Nobody is ever told.** `absent` is computed and used only as a boolean; the comment at
  `:3922–3925` says a caller *"can ask store.pagesNotLoaded the same question"* and **no caller
  does** — the method's only two references are inside `save()` itself. Meanwhile the next page
  operation quietly retires the absent page's id (FR-018, decided but not implemented) — so the
  loss OB-068 prevented is now prevented silently, while the id that would let the page reattach is
  destroyed in the background, also silently.
- **The reconciliation report is discarded at five of six doors.** Only
  `AutonomyEditorPanel.java:5835` keeps `session.save()`'s return; `AutonomyMenu:691`,
  `AutonomyViewerPanel:1296`, `LayoutRightclickAutonomyMenu:683`, `TrainControlUI:2908` and `:4124`
  drop it. The `Reconciliation` class's own javadoc states the principle being broken: *"Nothing
  here is acted on silently: the whole point is that a diagram changing under a setup should be
  visible."*

**What I would do.** A fifth enforcement site is coming — every pruner ever added will need this
guard — so name the rule once: a session-level `pagesSafeToJudge()` (loaded, not excluded where
relevant, numbering not suspect) that `save`, capture and any future pruner consult; move the
`forgetArrivals` call below the `incomplete` computation with a sentence; and surface `absent` at
the doors that already show dialogs. FR-018 option 2 (warn before writing an index while a known
page is unloaded) lands naturally on the same helper.

---

## C — worth doing, no urgency

| # | Finding | Status |
|---|---|---|
| DR-C1 | `Layout.parseAuto` carries two contradictory accounts of `blockedBy` resolution, six lines apart | Open |
| DR-C2 | `Point.java` still documents the pre-`5555d9f9` string world in three javadocs, and two comments both claim to be "the boundary" | Open |
| DR-C3 | Residual blind spots in the five ratchets, named | Open |
| DR-C4 | The `takingPath` release is a two-line pairing repeated at three of four exits | Open |
| DR-C5 | The last-colon key surgery is written four times; FR-013 dissolves it | Open |
| DR-C6 | Held entries of a page deleted while absent are permanent; a corrupt `.id=` silently becomes a position | Open |
| DR-C7 | A new per-point operational property needs five to seven sites in four files, unguarded | Open |
| DR-C8 | Small robustness: a "null" reason, an ignored return, a rebuild inside a loop | Open |

### DR-C1 — two contradictory comments on one `if` block

`Layout.java:6104–6111` says of `blockedBy` names: *"nothing resolves them at load — the rule asks
by name at the moment it is applied"*. Lines 6113–6117, inside the same block: *"Kept as names for
now and resolved after the loop"* — and `:6826–6854` resolves them and logs the misses. The first
comment describes a design that was replaced; the runtime rule at `:1949` asks the resolved
`Point` objects. This is the defect class `fe53a5ef` (OB-080) was opened for, one commit later.
Delete the stale half.

### DR-C2 — `Point.java`'s javadocs did not make the `5555d9f9` crossing

`Point.java:781–782` (*"The locomotive assigned to this station, by name"*), `:790–801`
(`setHomeLoc`'s javadoc about blank names and trimming, over a `Locomotive` parameter), `:570`
(`@param pointNames … or null to clear` on `setBlockedBy(List<Point>)`). And `Point.java:66–68` /
`:85–87` claim `toJSON`/`parseAuto` are the only name↔object boundary while
`Layout.setHomeLocomotive:871–903` claims the same title for itself (*"Resolved once, here, which
is the boundary"*). In a codebase where the comment is the safety mechanism, each of these is a
small loaded gun; the two "boundary" claims should be one sentence in one place.

### DR-C3 — the ratchets' residual blind spots, named so they are decisions

All five ratchets can fail (DR-D1); these are the ways through them that remain, recorded here the
way `testStoreCollectionsAreHandledEverywhere` records its own mention-granularity weakness:

- **Mention granularity:** removing only the *key-gathering* loop for `blockedPoints` from
  `deletePage` (keeping the values loop) passes the collections guard — the name is still in the
  body — and no behavioural test covers `deletePage` for that collection (the matrix has no
  deletePage column; `testPageIdsAreDurable` covers names/stations/lengths/exclusion/placements).
- **Holder shapes:** `testLocomotiveIdentityPropagates.holdersIn` (490–503) matches
  `private final Map<Locomotive,…>|Set<Locomotive>|List<Locomotive>` — a value-side holder
  (`Map<String, Locomotive>`), a non-final field, or a `HashMap`-typed declaration is invisible to
  the sweep guard. The value-side case is the by-name direction this week's work was about.
- **Wrapper doors:** the rename-door counter (`:697–711`) recognises `model.renameLoc(` textually;
  a door calling through a helper is not counted and not checked, while the `sites.size() == 2`
  assertion keeps the count green.
- **Cache reset shape:** `testEmptyingThePageCacheHandsItsLabelsBack` matches
  `layoutCache = new HashMap`; a future `layoutCache.clear()` empties the cache past the guard.
- **Variable indirection generally:** `testTheRunningLayoutIsRebuiltFromOnePlace` counts the literal
  `load(activeDiagramConfiguration, false, false)`; assigning the configuration to a local first
  duplicates the load invisibly. This is inherent to textual guards and worth a line in each file's
  header rather than a rewrite.

### DR-C4 — the `takingPath` pairing at three of four exits

`Layout.java:2456–2459`, `:2471–2472`, `:2490–2491`: each failure exit of the lock sequence repeats
`handleMisconfiguredPath(...); this.takingPath.remove(loc);` with a different span argument, and
`handleMisconfiguredPath` deliberately does not do the second half. A fifth exit added without it
lowers the running-trains cap by one for the session, silently (`trainsUnderway:1728–1735` unions
`takingPath`). One private `abandonLocking(spanToRelease, loc)` makes the pairing impossible to
half-copy. (The `f2818206` rollback itself is sound — see DR-D2.)

### DR-C5 — the last-colon rule in four places

`AutonomyCompanionStore.parseTileKey:4099`, `toStored:3633`, `fromStored:3648`, `allHere:3746` all
split a key on its last colon, post-OB-071. Correct today, four chances to be wrong the next time
key syntax moves. FR-013 (typed `TileKey` keys) deletes all four; this is an argument for FR-013,
not for a fifth helper.

### DR-C6 — two small page-id leftovers

A page deleted *while its file is absent* cannot have its held entries removed — `deletePage` works
on live collections, holds are keyed by field — so they sit in `setup.json` under a retired id
forever (harmless, invisible, and adjacent to IAR-A1's re-owning hazard). And a corrupt `.id=` line
parses to `null` and silently becomes a position (`LayoutDiagram:869–872`) — the identity the commit
abolished, restored by a typo, with no warning. Both are one log line each.

### DR-C7 — the N-place map for a new per-point property (question 2's answer beyond the store)

Adding one operational point property (the next `maxTrainLength`) touches:
`AutonomySession.POINT_OPERATIONAL_KEYS` (:1667, capture in and out),
`AutonomyBuilder`'s emit-or-skip chain (:930–945, five named carve-outs already),
`Layout.parseAuto` (reader), `Point.toJSON` (writer), the editor menu that writes it, and possibly
`CARRIED_SETTINGS` (:466, legacy import) and `AutonomyChecks`. Five to seven sites, four files, no
guard connecting them — the store's DD-A1 shape one layer up, at smaller scale. The `FACING`
special case inside `captureFromLayout` (:2350–2357, written but never cleared) shows each new key
also tends to need a bespoke clause. Worth a `testPointPropertyMatrix` eventually; recorded now so
the next property's author knows the count.

### DR-C8 — three small ones

`Layout.firstClearOrWhyNot:3545–3548` returns `String.valueOf(e.getMessage())` as the user-facing
reason — a null message renders as the literal "null" beside a station (the AR-1 lesson, one file
over). `LayoutEditor:353` ignores `saveQuietly`'s boolean while `:443` checks it.
`AutonomySession.forgetPlacementsElsewhere:2999` calls `touched()` — dirty **and rebuild** — inside
its loop over point keys; N stale placements is N rebuilds mid-operation.

---

## D — not defects: verdicts, clean checks, twins that must stay, and claims withdrawn

| # | | Status |
|---|---|---|
| DR-D1 | The five ratchet tests: can each actually fail? Verdicts with mutation receipts | Recorded |
| DR-D2 | Robustness paths checked and found sound | Recorded |
| DR-D3 | Deliberate twins that must not be merged | Recorded |
| DR-D4 | Two claims from the parallel readings, checked and rejected | Recorded |
| DR-D5 | Status of the prior findings this pass was told to cite | Recorded |

### DR-D1 — the ratchets, tested for falsifiability (question 4)

One of these was found unfalsifiable last week — the OB-081 guard in
`testLocomotiveIdentityPropagates`, whose window search matched a javadoc that comment-stripping had
already removed, so *"the window became the rest of the file, and the rule passed whatever the code
did"* (`717c1291`). Each of the five was therefore checked, by real mutation where the guard's
subject allowed it and by reasoned mutation where it did not:

| Test | Verdict | Evidence |
|---|---|---|
| `testStoreCollectionsAreHandledEverywhere` | **Can fail** within its charter | Real mutation: all `blockedPoints` mentions removed from `deletePage` in a scratch store → 1 failure, site named. Blind spots: DR-A1 (sites it does not govern), DR-C3 (mention granularity). |
| `testAutonomyStoreSettingsMatrix` | **Can fail** | Real mutation: `moveListValues/moveKeys(blockedPoints, …)` removed from `moveTiles` → 2 failures (move and built-over cells). Blind spots: id-blind save/load (DR-B8); no reconcile, deletePage or held-save columns (the reconcile and clear-then-load columns DD-A2 asked for are still absent). |
| `testPageIdsAreDurable` | **Can fail** | Behavioural, fixture-driven; its held-entry test asserts on the written file, both directions. It covers `pointNames` only — the twelve-fold version is DR-A1's proposal. |
| `testJavadocsAreAttached` | **Can fail, both directions** | Reasoned: a new stacked javadoc → 99 > `ALLOWED` = 98, fails with the file named; fixing one → the `assertEquals` floor forces banking the improvement. The detector reads raw source, so no comment-stripping trap applies. |
| `testEditorSurfaceRules` | **Each rule can fail** | Reasoned per rule: a second `session.setFacing(` → the count of 1 breaks; a fourth writing file → the named list breaks; the clear moved out of `finally` → the 400-char window has no `finally`; a second bare `load(activeDiagramConfiguration, false, false)` → the count of 1 breaks. Residual escapes in DR-C3. The file has already eaten its own cooking twice (TD-3's comment-window trap, FBR-C8's CRLF trap) and carries the fixes. |
| `testLocomotiveIdentityPropagates` | **Can fail now** | The OB-081 guard is bounded by the closing `catch` or the next call site, asserts the window was actually found (`end > at && end - at < 6000`), and pins the door count at 2 — the `717c1291` fix, verified by reading the bounding logic. Its commit records mutation-checking in both directions. Blind spots: DR-C3 (holder shapes, wrapper doors). |

The general verdict: all five can fail for in-charter mutations, and three of the five have already
had a "the guard was the defect" round recorded in their own comments — which is the strongest
argument this codebase has produced for writing the blind spots down (DR-C3) rather than assuming
the ratchet covers what its name says.

### DR-D2 — robustness paths checked and found sound (question 3's good half)

Verified by reading, and where noted by the tests that pin them:

- **`store.load()` cannot leave a half-loaded store.** Parse-before-clear for setup and every
  configuration; a *type* failure inside `readShared` rolls back to a pre-taken snapshot
  (`:664–682`); a corrupt configuration is refused as a named `IOException`. IAR-D3 reached the
  same verdict independently.
- **`renameConfiguration` is move-then-rewrite with a two-sided rollback** (`:1885–1944`) — no
  window in which the configuration is on disk nowhere; failure restores memory and disk both.
- **`deleteConfiguration` lets the file decide** (`:1957–1971`), so memory and disk cannot disagree
  silently under a sync-client hold.
- **The decline-to-prune guard works and covers both triggers** (suspect numbering, absent pages) —
  the one pruner outside it is DR-B10's first bullet.
- **`captureFromLayout`'s prune** correctly spares excluded pages, unloaded pages and unparseable
  keys (`:2402–2417`).
- **The `f2818206` lock rollback** releases on the thrown path, keeps the mid-run "do not unlock"
  rule where its precondition holds, and the what-to-say loop can no longer prevent its own cleanup
  (`handleMisconfiguredPath:2621–2645`). IAR-D8 concurs.
- **Iteration-over-mutation:** a programmatic sweep of the six chain files found one
  iterate-then-mutate site and it collects first, removes after (`AutonomySession:2404–2417`);
  every other prune copies defensively. `Layout`'s four sites use explicit iterators or
  build-then-replace.
- **`locDeleted`'s sweep** covers all six holders plus the timetable, and the reflective
  `collectionsHolding` check backs the textual guard behaviourally.
- **`parseAuto` is uniformly tolerant** — drop the entry, keep the layout — which is the right
  polarity for a file that can outlive its database; DR-B9 is only about *saying so* at two doors.
- **`repairAutonomyLocomotive`** handles all four states (session/no session × setup/no setup)
  without fabricating a setup, and reaches the editor's three snapshot holders (Cancel, undo, redo).
  IAR-D7 concurs on the on-disk half.

### DR-D3 — deliberate twins, correctly two

- **`blockedBy` names and lock edges** (`AutonomyBuilder:844–846`, `Point.java:538–556`): one
  setting emitted as two mechanisms answering different questions (destination rest vs route
  crossing), each documented. Merging them would re-import the parked-train deadlock the lock-edge
  comment records. Do not merge; DR-B2 is about the three copies of the *first* mechanism only.
- **`heading()` vs `facingOf()`** (`AutonomyBuilder:736–752`): known-divergent, ticketed (UR-16 /
  MT-138), divergence documented in place. A decision, not a drift.
- **`reconcile` and `applyTo` stay hand-written** — reaffirming DD-D9 unchanged; nothing this week
  weakened that reasoning, and DR-A1's registry-adjacent proposal deliberately stops at test
  columns and one shared constant for exactly that reason.
- **`repairLocomotiveInPoints`** — one method, three callers (configurations, page snapshots, the
  Cancel setup) with the javadoc *"one method with three callers rather than three copies that can
  disagree"* — is the pattern this review would like the rest of the report to converge on, and is
  recorded here as the model, as DD-D5 recorded `AutonomyMenu.actions()`.
- **`TileGraph.gridSideTowards` / `sideTowardNeighbour`** — DD-C9's fix, done right: renamed apart
  *and* documented as different questions (`TileGraph:1057–1062`).

### DR-D4 — claims checked and rejected

Recorded because both read convincingly and both would have been wrong to ship:

- **"FR-001 is missing from `pickPath`'s selection filter and from the yield probe."** Structurally
  true — neither conjunction names it — and behaviourally false: both reach `isPathClear` per
  candidate path (`:3210`, `:3643`), which applies the clause whenever it is in force. The cost is
  attribution (DR-B3), never the decision. The README's rule applies: *"Trace whether any caller can
  actually reach the defect before assigning severity."*
- **"The `testHomeStaging` replay oracle has no FR-001 clause at all."** It does
  (`testHomeStaging:180–190`, added with FBR-B1) — the accurate finding is that the clause it has is
  a third, point-level, exemption-less variant of the rule (DR-B2), which is a different and smaller
  claim than the one first reported.

### DR-D5 — status of the findings this pass was told to cite

| Prior finding | Status at `f1befe72` |
|---|---|
| DD-A1 / OB-025 | Open at Adam's request. The store is now **twelve** collections (`blockedPoints`, FR-001) across **~17** per-collection sites (deletePage, the hold lists and the merge array are new since DD counted fourteen). The twelfth was wired everywhere first time — the ratchet pair earned its keep — but the new sites sit outside it: DR-A1. |
| DD-A2 | Closed (`ae94421a`); its step-2 matrix extensions (reconcile, clear-then-load, import/export columns) remain undone — noted in DR-D1. |
| DD-A6 | Open. `canBeHome`/`homeBrokenByExcluding` still have no production caller; the editor still carries its own string-based `homeBrokenBy` twin (`AutonomyEditorPanel:3267`). |
| DD-A7 (1)(2)(3) | Open, all three, at moved line numbers: builder `splitSides:437–441` vs session `:2649/:2671`; badge `AutonomySession:3723–3729` still without `arrivalAllowed` (builder `:824`); editor path test `AutonomyEditorPanel:4771–4788` still turn-sets-only, now with a fresh comment claiming agreement it does not have. The family grew this week: DR-B6. The agreement test DD-A7 asked for still does not exist. |
| DD-B8 | Open; `touched()` at `:3848`, the two inline `dirty = true` unchanged. |
| DD-B9 | Open; `findPath`/`reachableTiles` walks and turn blocks unchanged, `onwardSides`/`onwardFrom` unchanged, plus the new session-level pair: DR-B6. |
| DD-C9 | **Fixed** (`gridSideTowards`/`sideTowardNeighbour`, javadoc cites DD-C9). Recorded in DR-D3. |
| DD-D9 | Reaffirmed; see DR-D3. |
| IAR-A1 / IAR-D6 | The parallel pass's absent-page re-owning finding and its verdict that the held mechanism is the working half — both consistent with what this pass found; DR-A1 and DR-B4 are the adjacent, non-overlapping hazards (the held mechanism's *lists*, and the parser/collision side of the id scheme). |

---

## What this pass did not look at

Named so the next reviewer knows what is uncovered rather than clean:

- **The UI chrome.** Menus, editors and panels were read only where a model rule crossed into them
  (the ratchets' subjects, the save doors, the snapshot holders). DD-B1…B7's territory was not
  re-walked.
- **`MarklinControlStation` and `CS2File`** beyond the page-index loop and the rename origin.
  Both were flagged uncovered by DD and remain so.
- **The route editor and `base/` vs `marklin/`** — unchanged verdicts from DD stand unexamined.
- **Anything requiring the railway or a display.** The mutation runs exercised the store only;
  DR-B1, DR-B2 and DR-B7 rest on reading and would each be settled further by one debug-mode
  session on the real layout with FR-001 configured.
- **`ui.testRenderingCost`** was not run (OB-084, filed).

---

## Dispositions

**Claude, 2026-08-24.** Five fixed, the rest filed together as
[OB-086](../manual-tests/issues.md) rather than half-done.

| | What was done |
|---|---|
| **DR-A1** | Fixed. The three hand lists are one map of field name to value shape, read by both the hold and the merge, so they cannot be half-updated. The behavioural guard the finding asked for is in `testPageIdsAreDurable`, mutation-checked against the exact mutation this finding used: held-but-not-merged now fails it, naming the collection. Recorded in the test itself: removing a field from the map ENTIRELY does not fail it, and that is not a hole, because the field then round-trips by the ordinary path. |
| **DR-B3 (1)** | Fixed. `blockingOccupantOf` is one method, called by the runtime check and by the explanation, so the FR-001 reason no longer has to travel through the static that the window deliberately ignores. A permanently held-back station described as temporarily busy is the opposite of what FR-017 was built for. Seen red for the right reason. |
| **DR-B4 (2)** | Fixed. An unreadable index is recorded rather than swallowed, and `writeLayoutIndex` refuses to renumber every page on the strength of a read that failed. The page the caller wanted is not saved; that is the lesser harm, and it can be saved again in a moment. |
| **DR-B7** | Fixed by sweeping the sibling, which is what the finding is about: `getPoints` and `getEdges` copy under the monitor exactly as `getHomeStations` does, citing its javadoc's reasoning. |
| **DR-B9** | Fixed. Both silent doors log, in the shape of the two that already did. |
| **DR-B3 (2), B1, B2, B4 (1)(3), B5, B6, B8, B10, and the eight C findings** | Filed as OB-086, with a starting point - DR-B8 first, because until the matrix sets page ids every other fix in that list is checked by a guard that cannot see the layer they are about - and with what must NOT be consolidated recorded. |

**On the shape of what was fixed.** DR-A1 and DR-B3 are both defects in work I did this week - the
held-entries mechanism and the FR-017 window - and both are the same mistake: a rule given a second
home, once as four arrays and once as a static the reader deliberately ignores. Adam asked for this
pass because of page management and autonomy linkage. It found the same thing again, in the fixes for
those.
