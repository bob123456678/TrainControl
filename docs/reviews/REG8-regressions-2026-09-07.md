# REG8 - Regression review: did the week's changes collide?

**Status:** open

**Version reviewed:** commit `8ea1ff15` ("The behaviour reference answers Adam's annotations, and
the tail cannot be un-set", 2026-09-07), branch `autonomy-diagram-r0`. **Reviewed:** 2026-09-07.
**No code was changed as part of this review and no tests were run** - read-only, per the brief.
Every claim rests on reading the enforcing method at HEAD; the diffs were used to find the hunks and
to date the collisions, never as the evidence.

**Prefix for citing this document: `REG8`.**

**Scope and method.** This pass asks one question about the ~75 commits of 2026-08-30 .. 2026-09-07:
where two of the week's changes answer one rule differently, where a reversed ruling left something
behind, and where a test was made to pass at the wrong layer. It deliberately does not re-report
`REG7`'s findings (same window, different question); where a REG7 finding is touched, it is cited.
The four theme groups from the brief were each traced end to end: the reversal ruling chain
(`8fca0215` .. `bc6120f1`), the facing/`arrivedFrom` chain (`eeee62c7` .. `8f006b3a`), the length
and blocking chain (`28ef127a` .. `cb4cd2a7`), and the closing sweeps (`fa70939b` .. `8ea1ff15`).
`docs/reference/behaviour.md` was read in full as the rulings baseline and checked in both
directions - code against doc, and doc against code.

---

## Status

| # | Finding | Severity | Confidence | Disposition |
|---|---------|----------|------------|-------------|
| REG8-A1 | Every build or reload silently erases `arrivedFrom` from every occupied square: `parseAuto` applies it and then `setLocomotive` - carrying `8f006b3a`'s own occupant-change clear - wipes it in the same loop iteration. The test that "proves" survival asserts the JSON string and the session store, never the parsed railway | A | Confirmed (trace) | Open |
| REG8-B1 | `reconcileFacingWhenIdle` (from `bc6120f1`) levels the direction baseline from live state on any idle refresh; interleaved with a genuine operator reversal it can swallow the one kind of direction command the graph must follow, and the "no backlog" rule holds only if a refresh happens to run between run-end and the next echo | B | Possible (needs a run) | Open |
| REG8-B2 | The diagram's Edit/Assign-locomotive door records the placement in the running layout only - no `session.placeLocomotive`, no `setFacing` - while its editor twin was given both this week. A placement made there reverts on the next configuration load unless an editor capture happens first | B | Confirmed (trace) | Open |
| REG8-C1 | `behaviour.md` contradicts itself twice, both left by `8ea1ff15`'s revision: §3 says a mid-run reversal is both "followed once the run ends" and "ignored, not queued"; §4 says the arrival side "can be set or cleared" six lines above "Clearing is not offered" | C | Confirmed | Open |
| REG8-C2 | `behaviour.md` §3 claims "a journey to a may-reverse destination is still refused if the operator declines the turn it needs"; `d45d7951` removed the entire refusal mechanism and nothing enforces it - though tracing shows the case cannot arise, so the doc describes machinery that neither exists nor is needed | C | Confirmed | Open |
| REG8-C3 | The call-site comment above the room check in `isPathClear` still states the withdrawn all-or-nothing rule ("a path carrying any unmeasured segment is not judged at all") that `4499dddc` replaced in the method it introduces | C | Confirmed | Open |
| REG8-C4 | `testADirectionChangeIsNotSwallowed`'s stated rationale is `ca0265f4`'s withdrawn "deferred until safe" ruling; it enforces `putIfAbsent` + guard-first, which is only correct in combination with the idle reconcile, and nothing ties the two together | C | Confirmed | Open |
| REG8-C5 | Three orphan bundle keys were carefully reworded this week (`layout.ui.tooltipGrowDiagram`, `tooltipShrinkDiagram`, `layout.ui.errorNoStationsInGraph`) - no Java in `src/` or `test/` reads any of them, and none did 200 commits ago | C | Confirmed | Open |
| REG8-C6 | `REG7`'s status table is stale: it shows `REG7-A1` and `REG7-A2` Open although `d45d7951` dissolved the first and `82e10084` fixed the second, and no commit since `8f006b3a` has touched the document | C | Confirmed | Open |
| REG8-D | Collision pairs checked and found compatible (see section) | - | - | Recorded |

---

## REG8-A1 - The tail record does not survive the one path every configuration takes

**Severity A - data silently lost, and a protection Adam explicitly asked for silently switched
off. CONFIRMED by trace; the ten-line probe that would seal it is described below.**

**The two things that disagree**, both from the same commit:

1. `8f006b3a` ("Two reviewers, one real A - and the tail now blocks both directions") asserts, in
   its own message, that *"arrivedFrom IS emitted by the builder and DOES survive a save and
   reload"*, refuting `VAL8-A2`, and wires the placement prompt to write both stores.
2. The same commit adds the `VAL8-B4` fix: `Point.setLocomotive` clears `arrivedFrom` whenever the
   occupant changes (`src/org/traincontrol/automation/Point.java:534`:
   `if (l != previousOccupant) this.arrivedFrom = null;`).

Both are individually right. Together they erase the field on every load, because of an ordering in
`parseAuto` that neither half looked at:

- `Layout.fromJSON`'s per-point loop applies `arrivedFrom` first
  (`src/org/traincontrol/automation/Layout.java:8336-8340`), while the point is still empty;
- the same iteration then reaches the `loc` block and places the occupant
  (`Layout.java:8618` "Set the locomotive", `:8724` `placeOn.setLocomotive(l)`);
- `previousOccupant` is null and `l` is not, so the clear fires and the value applied two hundred
  lines earlier is gone.

A saved layout writes both keys on one point (`Point.toJSON`, `Point.java:1191-1193`), and the
builder passes the setup's answer through as an extra - so the JSON is correct and the parsed
railway is not. Every occupied square - which is every square where a tail exists - comes out of
`parseAuto` with `arrivedFrom == null`.

**What a user sees.** Hand-place a train on a platform with two ways in, answer the arrival-side
prompt, watch the covered squares grey (`cb4cd2a7`). Restart TrainControl (or load a
configuration - `AUTO_LOAD_AUTONOMY` defaults on, so a restart is enough). The greying is gone, and
a route is offered across the rail the train's tail is lying on. The walk still blocks squares
whose geometry forces the answer (`behaviour.md` §4, the `VAL8-C1` correction), so the loss is
confined to exactly the squares where `arrivedFrom` picks between candidates - which are exactly
the squares the prompt exists for.

**Why the test did not catch it.** `testTheArrivalSideReachesTheRailwayAndSurvivesASave`
(`test/core/testAutonomyDiagramSession.java:546-582`) asserts that the **built JSON string**
contains `"arrivedFrom": "W"` and that the **reopened session** still answers `W`. Its own javadoc
says *"the BUILD hop is what parseAuto is handed"* - but the built string is never handed to
`parseAuto`, and no assertion ever asks the `Point`. It verifies the layer above the defect on one
side and the layer below it on the other. This is the `assert-the-variable-not-the-control` shape:
the sentence in `8f006b3a`'s message ("DOES survive a save and reload") is true of the store and
false of the railway, and `VAL8-A2`'s conclusion - the answer the operator gives blocks nothing -
is resurrected by the very commit that refuted it, through a different mechanism.

**A second half the fix must not miss.** `arrivedFrom` is also absent from
`POINT_OPERATIONAL_KEYS` (`src/org/traincontrol/automationui/AutonomySession.java:2451-2453`), so
`captureFromLayout` neither captures a runtime arrival into the setup nor removes a stale setup
value when the train has moved on (this is the surviving half of `REG7-B1`). Fixing only the
`parseAuto` ordering would make builds re-apply stale setup-side answers to squares whose occupant
has changed - the exact case `VAL8-B4`'s clear exists for. The ordering fix and the capture-key fix
have to land together, or the clear must be re-run selectively after placement. `new-field-needs-
the-copy-constructor` is this shape by name: the field was added on 2026-09-06 (`faee88ed`,
`81f8c18c`) and the save/load and capture paths each got half an answer.

**What I would run** (not run here, read-only): build a `LayoutSandbox` session on the test
fixture, `setArrivedFrom`, `buildConfiguration()`, hand the string to `Layout.fromJSON`, and assert
`layout.getPoint(...).getArrivedFrom()` is non-null. By the trace above it fails today.

**Confidence:** high. Every hop is a read of the enforcing line at HEAD; the only unexecuted step
is the composition.

**Disposition:** open.

---

## REG8-B1 - The idle leveling can eat the one command it must follow

**Severity B. POSSIBLE - a timing window, not a certainty; read-only review cannot measure it.**

**The two things that disagree:** two of Adam's §3 rules, which the `bc6120f1` mechanism keeps
apart only by scheduling luck:

- *"A direction command from the track DOES update the graph, if it disagrees"* (idle case, must be
  followed);
- *"a direction command arriving while a run is under way is ignored, not queued"* (`bc6120f1`).

The mechanism: `followDirectionChanges` (`src/org/traincontrol/gui/TrainControlUI.java:10162`)
drops mid-run echoes without updating the baseline (`putIfAbsent` only, `:10193`), and
`reconcileFacingWhenIdle` (`TrainControlUI.java:6125`, called from `updateVisiblePoints` at
`:25520`) levels `lastSeenDirection` to each placed locomotive's **live** `goingForward()` whenever
the railway is idle.

Two windows, one in each direction:

1. **A genuine idle reversal can be leveled away before it is followed.** The model applies the
   direction to the `Locomotive` before `repaintLoc` fires `followDirectionChanges` on the message
   thread. If any refresh runs `updateVisiblePoints` in that gap - another locomotive's repaint, a
   sensor event, a UI action; the reconcile runs on every one - the baseline is set to the *new*
   direction first, `was == forward` holds, and the operator's reversal is never mirrored to the
   graph. The command the rule says DOES update the graph, silently doesn't.
2. **The "no backlog" guarantee is an ordering nobody enforces.** A reversal made during a run
   leaves the baseline stale (deliberately, `putIfAbsent`). If the first direction echo after
   `isRunning()` goes false arrives before any `updateVisiblePoints` has run, the comparison sees
   the stale baseline and follows the run's own reversal - the exact backlog `bc6120f1` was written
   to end. In practice a run's end triggers refreshes within moments, so this window is small; but
   nothing *orders* the reconcile before the next echo, and `bc6120f1`'s message ("the baseline is
   brought UP TO DATE while running") describes a property the guarded branch does not have - the
   leveling lives only in the refresh.

**What a user would see** (case 1): with autonomy idle, flip a locomotive's direction from the
Central Station while the window is busy repainting (e.g. another train's speed changing); the
diagram's facing arrow does not follow, with nothing logged. Intermittent by nature.

**What I would run:** an instrumented loop - idle layout, one placed locomotive, alternate
`switchDirection` echoes with concurrent `updateVisiblePoints` calls on another thread, count
`flipFacing` invocations against echoes. A deficit reproduces case 1. Case 2 needs an echo injected
between `isRunning()` going false and the first refresh - harness-level, same shape as
`testTheRebuildIsOnePass`'s split.

**Confidence:** the mechanism is read directly from HEAD; whether the interleavings occur at a rate
that matters is not knowable from reading. Filed at B rather than A for that reason.

**Disposition:** open.

---

## REG8-B2 - The diagram's Edit/Assign door still writes only the running layout

**Severity B. CONFIRMED by reading both doors side by side.**

**The two things that disagree:** two copies of one gesture, after a week of fixing exactly this
shape at one of them.

- The **editor's** Edit/Assign-locomotive door
  (`src/org/traincontrol/gui/AutonomyEditorPanel.java:4057-4108`): after `commitChanges()` it
  writes `session.placeLocomotive(...)` ("so the next build puts the train where it now is"),
  records the facing (`REG6-B5`, fixed by `af1aada8`), and fires `setupChanged()`.
- The **diagram's** Edit/Assign-locomotive door
  (`src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:652-678`): the same
  `GraphLocAssign` dialog, the same `commitChanges()` (`GraphLocAssign.java:250`,
  `moveLocomotive`) - and then only repaints. No `session.placeLocomotive`, no facing, no capture.
  `Layout.moveLocomotive` does not sync the setup itself (read at `Layout.java:6624` ff.), and the
  diagram's *other* placement door proves the omission is not a design: `placeFacing`
  (`LayoutRightclickAutonomyMenu.java:961-1001`) does all three writes and carries the comment
  explaining that a layout-only placement "kept its old placement too - and the next build emitted
  it at two Points, which fromJSON answers by invalidating the whole layout."

**Which commit introduced the disagreement:** the door itself is old; the *disagreement* is this
week's, made by the one-sided sweeps - `af1aada8` ("Sweep the placement siblings") and the
`VD11-A1` round fixed the editor's copy and `placeFacing`, and the surface-count test that guards
the rule (`testEditorSurfaceRules`) scans `AutonomyEditorPanel.java` only, so a door in another
file is invisible to it (the same blind spot the 2026-08-23 night review's NR-4 documented for
`setFacing`).

**What a user sees.** Right-click an occupied square on the track diagram -> "Edit Locomotive
At..." -> assign a different locomotive -> OK. The diagram shows it. Restart the application, or
load a configuration: the OLD locomotive is back (its setup placement was never changed), and if
the new one was also recorded somewhere else in the setup, the next build can emit the
two-placements invalidation `placeFacing`'s comment warns about. The window closes only if the
autonomy editor happens to be opened first (its `captureFromLayout` reconciles `loc`).

**Confidence:** high on the omission (it is a read of two adjacent methods); medium on the worst
consequence (the invalidation path was not executed).

**Disposition:** open. Note this is distinct from `REG7-B2`, which is about `arrivedFrom` at the
placement doors and remains open on its own terms - this door misses the placement itself.

---

## REG8-C1 - The rulings reference answers two questions both ways

**Severity C - no wrong behaviour on the layout; but this document exists to stop rules being
re-reversed, and it currently argues both sides of two of them. CONFIRMED.**

Both contradictions were left by `8ea1ff15`'s revision, which added the new rulings without
removing the sentences they superseded:

1. **§3 Direction commands** (`docs/reference/behaviour.md:152-164`). The bullet from the original
   `738ef56f` draft - written when `ca0265f4`'s behaviour was current - still says a reversal made
   during a run *"is followed once the run ends, not discarded."* Five lines below, the paragraph
   `8ea1ff15` added for `bc6120f1` says a mid-run command *"is ignored, not queued... there is no
   backlog."* The code implements the second (`TrainControlUI.java:10185-10196`, `:6125`). A future
   session quoting the bullet has licence to reintroduce the deferred-follow behaviour Adam
   explicitly withdrew - the precise failure mode the document's own preamble warns about ("a rule
   without its reason gets reversed again").
2. **§4 How arrivedFrom is set** (`behaviour.md:186-187` vs `:201-206`). The bullet says the side
   *"can be set or cleared afterwards"* from the right-click menu; the paragraph fifteen lines
   below rules *"Clearing is not offered"* (Adam: "clearing should not be possible, only setting"),
   which is what `8ea1ff15`'s code half implemented (`AutonomyEditorPanel.buildArrivedFromMenu` has
   no clear item).

**Fix is two sentence deletions.** Disposition: open.

---

## REG8-C2 - The doc claims a refusal the same day's commit removed root and branch

**Severity C. CONFIRMED - doc wrong, code right.**

`behaviour.md:137-139` (written by `8ea1ff15`): *"A journey to a may-reverse destination is still
refused if the operator declines the turn it needs."* But `d45d7951` ("Only the destination is
asked about") deleted the whole refusal apparatus the same evening - `whereTheJourneyWouldStrand`,
the check at both doors, and `autolayout.ui.journeyNeedsTheTurn` from all eight bundles; `git grep`
finds no trace at HEAD, and nothing else refuses a manual journey on the operator's answer.

Tracing shows the sentence describes a case that cannot arise, which is presumably why nothing
enforces it: a turning copy of a may-reverse **station** is emitted as a terminus
(`AutonomyBuilder.java:971-995` - "a terminus is a destination that reverses on arrival"), and a
journey ending at a terminus is never asked (`ManualReversalPrompt.forJourney`,
`ManualReversalPrompt.java:83-91`); a turning copy trains may not arrive at is emitted as a plain
reversing point and is not a destination, so `getPossiblePaths` (`Layout.java:4575`,
`end.isDestination()`) never offers it. The only asked case is an arrival at the **plain** copy,
where declining the turn leaves the train exactly as it drove in - nothing to refuse. The doc
should say that, rather than assert a refusal that a reader will test and fail to find.

Disposition: open (doc correction only).

---

## REG8-C3 - The comment above the room check states the rule its method no longer implements

**Severity C. CONFIRMED.**

`Layout.java:2432-2436`, immediately above the `measuredRoomToReverseInto` call: *"AND THE TOTAL
HAS TO BE COMPLETE... A path carrying any unmeasured segment is not judged at all."* That was true
when `f82a693f` wrote it (2026-09-01) and was precisely what Adam overturned on 2026-09-06
(`4499dddc` "Room is the total of what is measured, not all-or-nothing"; `0edbcd85`'s segment
bound). The method now implements the new ruling correctly (`Layout.java:7117-7210`: an unmeasured
segment ends the count without cancelling it; a bounded-but-unmeasured stretch is capped by its
segment) and carries the new reasoning in full - but a reader who trusts the call-site paragraph,
which is the first one they meet, learns the withdrawn rule. `fix-one-site-sweep-the-siblings`, in
comment form: `4499dddc` swept the method and not the caller.

Disposition: open (comment fix only).

---

## REG8-C4 - The ordering test argues for the ruling Adam withdrew

**Severity C. CONFIRMED.**

`test/ui/testADirectionChangeIsNotSwallowed.java` (added by `ca0265f4`) pins guard-before-recording
and `putIfAbsent` in `followDirectionChanges`, and its javadoc explains why in `ca0265f4`'s terms:
the change was *"not deferred until it was safe; it was swallowed"* - i.e. a mid-run reversal SHOULD
surface after the run. `bc6120f1` reversed that ruling nine hours later (mid-run reversals are
ignored, full stop) and kept the test untouched. Under the new ruling the pinned mechanism is only
correct **in combination with** `reconcileFacingWhenIdle` - a plain `put` in the guarded branch
would satisfy the ignore ruling on its own - and nothing in either the test or the code ties the
pair together: the test never mentions the reconcile, and deleting `reconcileFacingWhenIdle`
entirely would leave both of this test's methods green while restoring the backlog (see REG8-B1's
window 2, made permanent). A guard that reads as protection for a behaviour, whose text argues for
the opposite behaviour, and which cannot see the half that makes the current behaviour hold, is due
a rewrite: assert the pair, in the new ruling's words.

Disposition: open.

---

## REG8-C5 - Care spent rewording keys nothing reads

**Severity C. CONFIRMED.**

`layout.ui.tooltipGrowDiagram` and `layout.ui.tooltipShrinkDiagram` (reworded by `0dc7263e`,
answering REL-C12's complaint about their text) and `layout.ui.errorNoStationsInGraph` (reworded by
`fb3722f5`) have no reader: no reference in any `.java` or `.form` under `src/` or `test/`, at HEAD
or 200 commits ago. The review finding that prompted the rewording judged the sentences and not
their reachability. Either a caller was lost before the window (the grow/shrink strip went with
`ecb59315` "my hand-built strip deleted") or the keys never had one; either way the fix polished
dead text in eight languages. The keys should go, or the finding that a control lost its tooltip
should be raised instead - deciding which needs somebody who knows whether Adam's size buttons were
meant to carry these tooltips.

All other keys added or reworded this week check out: the eight bundles carry identical 1,916-key
sets, all pure ASCII, and every apparently-unused new key is reached by dynamic construction
(`"route.kind." + kind.name()` at `CommandRow.java:153`; `"autolayout.ui.pathPreference" +
option.name()` at `TrainControlUI.java:9290/9370`).

Disposition: open.

---

## REG8-C6 - REG7's own table has gone stale inside three days

**Severity C. CONFIRMED.**

`REG7-regressions-2026-09-07.md`'s status table shows every finding Open. But `5f492d9f` fixed
REG7-A1 on Adam's ruling and `d45d7951` then dissolved it (the mechanism it described no longer
exists); `82e10084` says in its subject line that REG7-A2 was "traced to something sharper, and
fixed"; and `8f006b3a` closed the prompt-side half of REG7-B1. No commit since `8f006b3a` touches
the document. This is the `audit-the-bodies-not-the-index` failure the 2026-09-04 audit
(`eb5c73a8`, "49 stale open markers") existed to end, recurring in the newest document in the
folder. REG7-B2, B3, B4, C1, C2 and C3 I checked against HEAD and believe genuinely open (B2 is
narrowed but not closed by the week's placement sweeps - see REG8-B2).

Disposition: open - the fix is REG7's table, not code.

---

## REG8-D - Collisions looked for and NOT found

The pairs below were checked for interference and found compatible. This is the part of the review
that cannot be reconstructed from the findings alone.

**D1 - Greying vs routing for covered track (`cb4cd2a7` vs `28ef127a`/`8f006b3a`).** One rule, not
two: the diagram's grey set is computed by `AutonomySession.tilesCoveredByStandingTrains`
(`AutonomySession.java:4826`), which iterates `Layout.edgesCoveredByStandingTrains()` - the same
map `isPathClear` consults (`Layout.java:2418`). `8f006b3a`'s both-directions fix therefore reached
both surfaces at once. No second implementation to drift.

**D2 - Destination-only prompting vs the turn rules (`d45d7951` vs `62f845a5`/`5670d7a9`).** All
four arrival cases traced through `shouldReverseAt` (`Layout.java:5248-5330`) with the
destination-only policy (`ManualReversalPrompt.forJourney`): an intermediate turning copy turns
(compulsory-turn branch, `:5317`); an intermediate plain copy passes (policy answers false); a
terminus is never asked and always turns; a destination plain copy follows the answer. Autonomy
(`null`/`ALWAYS_REVERSE`) is unwidened. The test rewrite in `d45d7951` replaced the withdrawn
refusal's assertions with equally strong ones about the new rule - it was not weakened - and
`testEveryCopyOfAMayReverseSquareIsAskedAbout` still pins the plain-copy question with its control
assertion intact.

**D3 - The `eeee62c7` revert (`c9098a9b`).** The reverted sweep half is gone without residue
(`git log -S` finds no survivor of the dropped-facing logic); the deliberately kept facing-menu
half is coherent - its two bundle keys (`autosetup.ui.facingOnlyOne`, `hintFacingOnlyOne`) are
referenced (`AutonomyEditorPanel.java:3068`, `:3071`) and present in all eight bundles. The final
paste rule (`97f7e1c7`, the BFS walk) matches Adam's quoted ruling in `behaviour.md` §4, and
`testAPastedTrainKeepsItsDirection` was rewritten to walk-based assertions rather than left
pinning a superseded attempt.

**D4 - Message bundles.** Identical key sets across all eight languages (1,916 keys each), zero
non-ASCII bytes, no week-added key orphaned (see REG8-C5 for the three pre-existing orphans), and
`d45d7951` removed its withdrawn message from all eight, symmetrically with `5f492d9f`'s addition.

**D5 - The room rule's two callers (`4499dddc`/`0edbcd85` vs `HomeStaging`).**
`Layout.isPathClear:2484` and `HomeStaging.java:1071` call the same
`Layout.measuredRoomToReverseInto` with identical semantics (`room != null && length > room`
refuses); exactly-fits admits at both (strict `>`, `a86d685c`); the planner asks before recording
the arrival (the WK3-B2 ordering), so the offer and the execution cannot disagree. The segment
bound only ever refuses relative to not-judging, as `behaviour.md` §5b states.

**D6 - Return Home's tier (`d9fb7f88` vs §1).** `isAutoDestination` appears nowhere in
`HomeStaging`; the inactive-**start** refusal is present (`HomeStaging.java:459`, `:944`) and the
manual exemption intact in `isPathClear` (start deliberately unchecked). The one deliberate
difference `behaviour.md` §1 records is exactly what the code has.

**D7 - The Path Type check's copy of the auto rule (`37a54cbc`/`6c95ffde`).**
`stationsAutonomyWillNotChoose` (`AutonomySession.java:3370`) now carries both halves the runtime
refuses on (`!isAutoDestination || isMustTurnAround`, with `isMustTurnAround` correctly narrower
than `isTurnAround`). It omits the runtime's third bar - inactive - but the editor's route test
hands `session.shutTiles()` to `findPath` as barred, so an inactive square already reports "no
path" in both tiers and the omission cannot surface. Compatible, though the compensation is worth a
comment.

**D8 - "Train arrived from" on both surfaces, and the un-set removal (`31ca168e`/`8ea1ff15`).**
One builder (`AutonomyEditorPanel.buildArrivedFromMenu`) is served to the track diagram through
`TrainControlUI.buildAutonomyTileMenu` (`TrainControlUI.java:4249` ff., with `setLayoutSource`
wired so the live-Point write works there too). Removing the "Not known" item in the one builder
therefore removed it at both doors at once - the `guard-and-affordance` failure cannot occur here.

**D9 - "A berth a train does not fit is not offered either" (`8baec7a2`).** Not a second length
predicate: the offer lists come from `getPossiblePaths`, whose paths pass `isPathClear`, which
carries both length rules. `isOfferableToOperator` (`Layout.java:4219`) deliberately knows nothing
about length. One rule, reached by the menu through the execution check.

**D10 - The closing sweeps (`073ab12a`/`c68f1785`) against the week's rulings.** The C3 guard
(deletePoint/deleteEdge refuse while running or staging) matches `behaviour.md` §6a including the
menu-greying caveat; C13's `getAccessoryStateIfPresent` matches §8's counter-not-claim ruling with
creation kept on the commanding path; C6 (atomic Point id) and C7 (rounded speed) match their doc
entries. The dismissed-paste fix (`073ab12a`) moved the question before the move at the one door
that asks it, which is what makes the §4 abandon rule implementable at all.

---

## Limits of this review

Read-only, so three things are stated as traces rather than as observations: REG8-A1's composition
(each link read, the whole never executed - the probe is described in the finding), REG8-B1's
interleavings (mechanism read, rates unknowable without instrumentation), and REG8-B2's worst case
(the two-placement invalidation path was not driven). I did not re-read the five earlier days of
the window (editor flicker, OB-172, the audit rounds) beyond confirming their bundle and
disposition hygiene; REG7 covered that ground and its open B/C findings there were spot-checked
against HEAD, not re-derived. The manual-send-answered-"turn"-at-a-plain-copy path ends at
`switchDirection` on arrival; whether the graph then stands the train on the turning copy was not
traced to the end and is worth one hands-on check when the layout is next up (it is adjacent to
REG7-A1's dissolved territory, not to any finding here).
