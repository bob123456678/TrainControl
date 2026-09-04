# The second validation: is the release blocker's new test honest, and did the fixes for the first validation break anything

**Status:** open

**Prefix for citing these findings elsewhere:** `OV2`

**Reviewed:** branch `autonomy-diagram-r0`, on 2026-09-04, at `233ee0e9`. The subject is the round
that closed [the first validation](2026-09-04-opus-validation.md) — `9c00b076` (OPV-B1, C1, C2) and
`233ee0e9` (OPV-C3 through C7) — plus `504ad2ab`'s `IPR-B3`, which the first validation did not see.
Every disposition in the OPV document was read as a claim and checked against the code at HEAD, never
accepted. Five test classes were run through `docs/tools/one.sh`: **211 tests, 0 failures, 0 skips**,
and the live-layout guard did not fire (OV2-D9). `cs2_sample_layout/` was read and never written;
`battery.sh` was not run, per Adam's ruling. Nothing in the tree was edited except this file.

**Method.** The first validation's central charge was that eleven findings were closed and one test
was written. Four tests have since been added. This pass spent most of its budget on **whether those
tests reach what they say they reach** — by running the class and reading the exception's own stack
trace out of `one-run.txt` rather than reading the test's javadoc. That single step is what produced
OV2-B1, and nothing else in this document would have found it: the test passes, its mutation genuinely
fails, and every line of it reads correctly.

---

## Verdict

**This tree is close, and one thing must change before it is tagged: the release blocker's test does
not execute the release blocker's code path.** `testALockPhaseFailureLeavesTheTrainWhereItStands`
injects a `null` into an edge's `lockEdges` and says that makes `Edge.setOccupied` throw inside
`configureAndLockPath`'s lock loop. It does not. `isPathClear` walks the same `lockEdges` list four
statements earlier, at `Layout.java:2277`, and the `NullPointerException` comes out **there** — before
`takingPath` is claimed, before `edgesLocked++`, before anything is locked. The stack trace in this
run's output says so verbatim (OV2-B1). The gate is therefore pinned by a strictly weaker window than
the one ACC-A1 is about: nothing was ever locked, so the two consequences ACC-A1 names — the start
reservation cleared out from under a train `handleMisconfiguredPath` had just put back, and
never-taken edges released into lock-edge counts shared with other running dispatches — are still
exercised by no test at all, and the test's javadoc tells the next reader that they are. The shortest
list before a tag is **one item**: re-aim that test at the lock loop. A buildable injection is given
in OV2-B1 and needs no production change. Everything else here can ship: five C findings, of which the
one worth doing at the same time is OV2-C1 — `OPV-C4`'s move of the menu ellipsis deleted it outright
in the case `ACC-C10` was actually written for. The rest of the round is sound and is written up under
D: the `block` key really is singular now and really is tested three ways, `IPR-B3`'s joiner close
mirrors `read`'s recursion exactly and cannot pass an ambiguous outline, the comment repairs are true,
and the two line citations `OPV-C6` named are now correct — though a third one, in the same family and
broken by the same commit, was missed (OV2-C4).

---

## Summary

| # | Severity | One line | Where |
|---|---|---|---|
| B1 | B | The ACC-A1 test throws in `isPathClear`, not in the lock loop: nothing is ever locked, `handleMisconfiguredPath` never runs, and three claims in its javadoc are false | `testAutoLayout.java:1701-1783`, `Layout.java:2277` |
| C1 | C | OPV-C4 moved the ellipsis inside the More Destinations gate, so when both lists are empty — the case ACC-C10 is about — there is now no escape at all | `LayoutRightclickAutonomyMenu.java:445, 488` |
| C2 | C | `sayIfDeclined` now answers two different questions; the door it excludes DOES carry an edit, and only an unwritten unreachability makes that safe | `TrainControlUI.java:5546, 5568-5581, 5691` |
| C3 | C | ACC-C6's fix was not swept to its third sibling: the page-exclusion write still discards `saveQuietly()`'s answer | `TrainControlUI.java:22557` vs `LayoutEditor.java:433, 605` |
| C4 | C | OPV-C6 repaired two citations and left a third that the same commit broke, 125 lines out, in the file it edited | `AutonomyEditorPanel.java:6458` |
| C5 | C | The exit gate's four conditions are now written out twice and must agree forever, and the copy omits the capture's fifth | `TrainControlUI.java:2286-2299` |
| D1 | D | OPV-C1: `block` is singular everywhere, `"blocks"` occurs nowhere, and the new test asserts each key separately with a control | `AutonomySession.java:1025`, `testAutonomyDiagramSession.java:5811` |
| D2 | D | OPV-C3: the lead sentence is true and the duplicated paragraph appears once; nothing else in the file says otherwise | `AutonomySession.java:667-695` |
| D3 | D | OPV-C7: the OB-172 paragraph is in the javadoc of the `paint()` that carries it, and `paintChildren` has no orphan heading | `LayoutGrid.java:740-778` |
| D4 | D | OPV-C4's two hazards: the ellipsis still cannot double, and the item order under the heading is right | `LayoutRightclickAutonomyMenu.java:366, 403, 488` |
| D5 | D | IPR-B3: the joiner close is exactly `read`'s recursion boundary; the rule still bites, and no ambiguous outline gets through | `ConditionOutline.java:178-188, 230-299` |
| D6 | D | OPV-C6's two named citations are correct at HEAD | `Layout.java:5103, 5205, 5213, 5216` |
| D7 | D | OPV-C5's second half really does gate the message on the capture's own conditions | `TrainControlUI.java:2286-2299` |
| D8 | D | OPV-C2 is dispositioned honestly ("half done"); its other two halves are untouched and unclaimed | `2026-09-04-release-acceptance-review.md:289` |
| D9 | D | 211 tests across five classes, 0 failures, 0 skips, no live-layout alarm | below |
| D10 | D | **The four still-untested findings: which are worth a test, what it would assert, and which need an `MT-`** | below |
| D11 | D | What this validation did NOT cover | below |

---

## B findings

### OV2-B1 — The release blocker's test never reaches the lock loop; it throws in `isPathClear`

| | |
|---|---|
| **Severity** | B |
| **Disposition** | fixed - the test is re-aimed at the lock loop with the injection this finding supplies (a config command mapped to a null state on an accessory that exists), and its precondition now asserts the stack contains `configureEdge`, which is the thing no assertion about the outcome could have caught.  MUTATION: removing the gate fails it.  The second consequence is NOT asserted and the javadoc says so - any claim on the untaken edge makes isPathClear refuse the path, so the fixture and the observation exclude each other |
| **Confidence** | **Executed, not argued.** `core.testAutoLayout` was run through `one.sh` (26/0/0) and the exception the test catches was read out of `one-run.txt`: `Layout.isPathClear(Layout.java:2277)` → `isPathClear(:2161)` → `configureAndLockPath(:2940)` → `executePathInternal(:5392)` → `executePath(:5069)` → `testALockPhaseFailureLeavesTheTrainWhereItStands(testAutoLayout.java:1763)`. The absence of `handleMisconfiguredPath` is corroborated independently: its `autolayout.errorPathMisconfigured` line does not appear anywhere in the run's output. What I did NOT do is execute the mutation — I may not edit the tree — so "removing the gate still fails it" is derived by reading `unlockPath`'s atomic branch, not measured. |

The test's own account of what it does (`testAutoLayout.java:1755-1757`):

```java
// THE THROW, inside the lock loop: setOccupied cascades over lockEdges and this one holds a
// null.  Nothing in the tree does this, which is the point ...
path.get(0).addLockEdge(null);
```

and its javadoc: *"A null in an edge's `lockEdges` makes `Edge.setOccupied` throw as it cascades -
which happens inside the lock loop, after `edgesLocked++`, which is precisely the window the recovery
was written for."*

**`isPathClear` walks the same list first, and it is not inside the try.** `configureAndLockPath`
opens `synchronized (this)` at `:2937` and asks `isPathClear` at `:2940`; the try that captures
`lockFailure` does not begin until `:2951`. And `isPathClear` iterates every path edge's lock edges
unconditionally (`Layout.java:2275-2284`):

```java
for (Edge e2 : e.getLockEdges())
{
    if (e2.isLockHeld(loc))
```

`e2` is the injected `null`, so the `NullPointerException` is thrown at `:2277`, escapes
`configureAndLockPath` entirely, and arrives at `executePath`'s handler having done **none** of the
following:

- `takingPath.put(loc, path)` (`:2949`) never ran;
- `edgesLocked` is 0, so the `if (edgesLocked > 0)` at `:3019` is false and
  **`handleMisconfiguredPath` never runs** — no prefix was released, and
  `path.get(0).getStart().reserve(loc)` (`:3232`, *"Provably at its start"*) never executed;
- `e.setOccupied()` (`:2978`) never ran, so no edge and no lock edge was ever claimed;
- `throw lockFailure` (`:3028`) never ran — what propagates is the `isPathClear` NPE.

**What that costs, precisely.** Three of the javadoc's statements are false of the test that carries
them, and each is load-bearing:

1. *"which happens inside the lock loop, after `edgesLocked++`"* — it happens four statements before
   the loop is entered.
2. *"`configureAndLockPath` had already released what it took and re-reserved the train on the point
   it never left"*, in the failing message of the assertion at `:1778` — it had not. The start point
   holds the locomotive because **the test put it there** at `:1746` (`start.setLocomotive(loc)`) and
   nothing in the run touched it again.
3. The `assertFalse(layout.isRunning(), ...)` at `:1775` is labelled *"THE PRECONDITION, so a passing
   assertion below cannot come from the train never having been placed at all."* It cannot do that
   job: `isRunning()` is `running || !activeLocomotives.isEmpty() || locomotiveThreads.get() > 0`
   (`Layout.java:1663-1667`) and says nothing whatever about where a locomotive is standing. What it
   does check is real but different — that `stopLocomotives()` ran, which is RC-A11's half of the
   handler.

**Is the gate pinned at all?** Yes, and this is worth stating plainly rather than overclaiming. The
window the test does reach is OPV-D1's second bullet — *"a throw out of `isPathClear`, which sits
inside `synchronized (this)` but outside the try/catch"* — and in that window `hadItsPath` is also
`false`. Deleting `if (hadItsPath)` sends `unlockPath(path, loc)` into its atomic branch (a fresh
`Layout` has `atomicRoutes = true`, `Layout.java:607`), whose `i == 0` clause clears the start
**before** `setUnoccupied()` reaches the null and throws:

```java
if (i == 0)
{
    e.getStart().setLocomotive(null);
}

e.setUnoccupied();
```

so `start.getCurrentLocomotive()` is null and the assertion fails. The stated mutation holds. What
does not hold is the scenario: **ACC-A1's own two consequences remain untested.** Nothing in `test/`
drives a throw out of the lock loop *after* an edge has been taken, so nothing exercises the
interaction between `handleMisconfiguredPath`'s recovery and the handler's release — which is the
entire subject of the finding, of the gate, and of the comment at `Layout.java:5196-5222`.

**Graded B rather than C** for the reason OPV-B1 was: the codebase's rule is *"prove the guard
actually guards"*, and a test whose comment asserts it covers a scenario it does not is worse than the
absence it replaced — the next reader has no reason to look. It is not graded A because the railway is
not worse than it was: the gate itself is correct (OPV-D1 enumerated it, and I re-read `:5111-5225`
and agree), and it is pinned against outright deletion.

**A buildable injection that does reach the lock loop, traced through every call.** A `null` lock edge
can never get past `isPathClear` — it scans every path edge's lock edges unconditionally, and
`setOccupied`'s cascade is only one level deep, so a null buried in a lock edge's own list is not
reached either. A config command mapped to a **null state on an accessory that exists** does reach it:

- `isPathClear`'s preview calls `configureEdge(e, validity)` with a non-null `preConfigure`
  (`Layout.java:2510-2513`). `names.sort` uses `Accessory.isThrow(null)`, which is null-safe
  (`Accessory.java`, `state == TURN || state == RED`); `acc` is non-null so the `acc == null` arm is
  skipped; the `preConfigure != null` arm at `:2617` puts the null into `configHistory` and leaves
  `configIsValid` true. The preview passes.
- The lock loop then calls `configureEdge(e, null)` (`:2991`), which takes the `else` at `:2630` and
  evaluates `state.toString().toLowerCase()` at `:2635` — **`NullPointerException`, inside the try,
  after `edgesLocked++`, after `e.setOccupied()`, after `e.getEnd().reserve(loc)`.**
- `handleMisconfiguredPath` then runs for real: its name-collection loop is inside a `try/catch`
  (`:3187-3209`) so the same null cannot stop it, the edge is released, the end point cleared, and
  `path.get(0).getStart().reserve(loc)` re-reserves the start. `throw lockFailure` follows.

Put the bad command on the **first** edge of a **two**-edge path and the test can then assert both of
ACC-A1's consequences rather than one: that the start still holds the locomotive *after the recovery
put it back* (not after the fixture put it there), and that the second edge — counted by
`edgesLocked` as never taken, and therefore outside `path.subList(0, edgesLocked)` — has not been
released, by asserting a lock edge it shares with a second locomotive's held dispatch still reads
occupied. The mutation is unchanged and now fails on the assertion rather than on a secondary NPE,
because there is no null in any `lockEdges` list.

*The precondition that would have caught this, and which the README already asks for: assert that
`getPathValidationFailureCount()` went up. It is incremented by `handleMisconfiguredPath` (`:3247`)
and exposed for tests at `:3269` for exactly this kind of question. In the fixture as it stands it
would be zero.*

---

## C findings

### OV2-C1 — OPV-C4's move deleted the ellipsis in the case ACC-C10 was written for

| | |
|---|---|
| **Severity** | C |
| **Disposition** | fixed - the escape is lifted out of the `otherPaths` branch entirely, so it fires whenever anything was left out whichever list is empty; the comment records all three wrong placements |
| **Confidence** | Fully verified by reading `LayoutRightclickAutonomyMenu.java:280-506` and the diff of `233ee0e9`. The reachability of `paths.isEmpty() && otherPaths.isEmpty() && possible > 0` is derived from the classification loop at `:328-362`, where every path is either dropped by `isOfferableToOperator` or lands in one of the two lists — not observed on screen. This class is package-private GUI code with no test at the assembly layer. |

`ACC-C10` added the escape outside both gates, and `OPV-C4` moved it inside the More Destinations
block. It is now at `:488`, nested in `if (!otherPaths.isEmpty())` at `:445`:

```java
if (!otherPaths.isEmpty())
{
    ...
    add(more);

    if (paths.isEmpty() && possible > otherPaths.size())
    {
        JMenuItem wayOut = new JMenuItem("...");
```

Before the move, the same guard sat at the top level, where `paths.isEmpty() && possible >
otherPaths.size()` with `otherPaths` empty reduces to `possible > 0` — and that fired. It cannot now.

The lost case is not hypothetical and it is the one the finding names. `possible` is `paths.size()`
taken before the split (`:296`); the loop at `:328-362` drops a path entirely when
`isOfferableToOperator(end, locomotive)` is false, and otherwise sorts it into `shownPaths` or
`otherPaths`. So both lists are empty and `possible > 0` exactly when **every** destination this train
could reach was switched off or excludes it — which is `ACC-C10`'s own sentence, *"a locomotive whose
choosable destinations are all inactive or excluded"*, and `OPV-C4`'s own sentence, *"when `otherPaths`
is empty too — the case the finding is actually about, everything filtered."*

`OPV-C4` raised that case as a complaint about the escape appearing **without a heading above it**.
The fix answered it by putting the escape under the heading, and the heading only exists when
`otherPaths` is non-empty — so the case was resolved by removing the item rather than by giving it a
heading. The comment written beside it at `:478-482` still describes the case it no longer covers.

*The whole fix is to lift the block back out of `if (!otherPaths.isEmpty())` and give it the same
two-line heading gate `VD11-C3` gave the submenu at `:453-463` — separator and disabled locomotive
name when `paths.isEmpty()` and the submenu did not already add them.*

### OV2-C2 — `sayIfDeclined` now answers two questions, and the door it excludes does carry an edit

| | |
|---|---|
| **Severity** | C |
| **Disposition** | documented rather than changed - the unreachability is now written at the flag: the editor cannot be opened while a run is going (OB-047) and refuseWhileEditorOpen guards every door that starts one, so the decline that door would record cannot happen.  With a note that relaxing that guard means giving this its own parameter |
| **Confidence** | Both call sites and both halves read. **I could not reach the failure either**, and looked harder than OPV-C5 did: `LayoutRightclickAutonomyMenu`'s constructor returns a single disabled item and nothing else while `isLayoutEditorOpen()` (`:142-149`), so no hand dispatch can start from the viewer while the editor is open; `buildAutonomyTileMenu` returns null on the same test (`TrainControlUI.java:4201`); `addSetupMenu` returns on `isAutonomyBusy()` (`:754`); and the editor cannot be opened while busy (`TrainControlUI.java:4631`). So this is structurally real and, as far as two passes can tell, unreachable — the folder's B3/C7/C15 shape. I did not enumerate the route-trigger and timetable doors. |

The fix moved the flag onto `sayIfDeclined` (`TrainControlUI.java:5577`) with the reasoning that

> `sayIfDeclined` is exactly the question "did this door carry an edit", which is why the flag now
> rides on it rather than on the decline alone.

That equivalence is not true, and the parameter's own javadoc four lines above the fix still states
the other meaning, unchanged (`:5546`):

> `@param sayIfDeclined` true where the caller has not already warned about editing during a run

Those are different questions, and the door that separates them is precisely the one the fix excluded.
`autonomyEditorClosed()` (`:5653-5692`) is what runs when the **setup editor** closes — the window
whose entire purpose is editing the setup — and it passes `false` not because it carries no edit but
because, in its own words at `:5683`, *"somebody editing during a run has already been warned once."*
So the excluded door is a door that carries edits; what makes excluding it safe is that autonomy
cannot be busy when it runs, and that is a precondition nothing writes down. This is the same shape
the fix itself was correcting from the other side.

The half of `OPV-C5` that was reachable — the message naming the wrong cause — is genuinely fixed, and
is written up under OV2-D7.

*The cheap repair is a parameter that says what it now means, or a second one: `sayIfDeclined` for the
message and `carriedAnEdit` for the flag, with `autonomyEditorClosed()` passing `(false, true)`. If
the answer is instead that the door is unreachable, that sentence belongs at `:5577` in place of the
equivalence, because it is the only thing holding the flag up.*

### OV2-C3 — ACC-C6's fix was not swept to its third sibling

| | |
|---|---|
| **Severity** | C |
| **Disposition** | fixed, and swept - the third site logs, and `testEverySaveOfTheSetupReadsWhetherItWorked` now holds every saveQuietly caller outside AutonomySession to it.  MUTATION: dropping the `!` from any call site fails it |
| **Confidence** | Fully verified: `grep -rn "saveQuietly" src/` returns exactly three call sites outside `AutonomySession` itself. Two read the answer, one does not. Not executed — an I/O failure at that instant is not something I can produce. |

`ACC-C6` was *"the undo door swallows the save failure its sibling logs"*, and the fix is correct at
its own site (`LayoutEditor.java:433-438`, OV2-D6 in spirit). There are **three** callers, not two:

```java
LayoutEditor.java:433    if (!autonomy.saveQuietly() && parent.getModel() != null)   // fixed by ACC-C6
LayoutEditor.java:605    if (!autonomy.saveQuietly() && parent.getModel() != null)   // rememberAutonomy
TrainControlUI.java:22557    if (session.exists()) session.saveQuietly();            // still discards it
```

The third is the Combine Pages path, and what it is writing is not incidental: the two lines above it
set `setPageExcluded(combined, true)`, and the comment at `:22538-22540` says what losing that
exclusion costs — *"Included for even one build, every sensor on it becomes a second Point for a
sensor that already has one."* A write that fails there is silent, and the next build walks the
combined page.

This is the folder's own most-repeated mistake, named in the README: *"When you fix a call site, grep
for its twins before closing the finding."* One command, and it was not run for this one.

### OV2-C4 — OPV-C6 fixed two citations and left a third that the same commit broke

| | |
|---|---|
| **Severity** | C |
| **Disposition** | fixed - the third citation names `autonomyEditorClosed()` |
| **Confidence** | Fully verified. Every `:NNNN`-shaped citation in the six files this round touched was located and its current target read (listed in OV2-D6). The origin commit of the broken one was found with `git log -S`, and what stood at that line when it was written was read out of that commit. |

`OPV-C6`'s two named citations are now symbol-named and correct (OV2-D6). A third, in the same family,
is 125 lines out — and the commit that broke it is `233ee0e9`, the commit that fixed the other two, in
the same file it edited. `AutonomyEditorPanel.java:6458`:

> `TrainControlUI:5566` passes false because it has already said it.

At `853bc9f9`, where that sentence was written, `TrainControlUI.java:5565` was
`public void autonomyEditorClosed()` — off by one and unmistakable. `233ee0e9` inserted six lines at
`:5568` and ten at `:2286`, and the call it names is now at `:5691`. Line 5566 today is the middle of
the `ACC-B3` comment: *"save on the way out folds the RUNNING layout back over the configuration"* —
about the same subject, which is what makes it read as right.

A smaller one of the same kind shipped in `9c00b076`, in the test written to answer `OPV-B1`:
`testAutonomyDiagramSession.java:5800` cites *"`Layout:7533` reads it"*, and `if (point.has("block"))`
is at `Layout.java:7534`. (`AutonomyBuilder:844`, cited beside it, is exact.)

*Both are the finding's own remedy applied one file wider: name the statement. `autonomyEditorClosed()`
and `the "block" read in fromJSON` do not drift.*

### OV2-C5 — The exit gate's four conditions are now written out twice, and the copy omits the fifth

| | |
|---|---|
| **Severity** | C |
| **Disposition** | fixed - one `wouldCapture` predicate, asked once, carrying every term including the session check the copy had dropped |
| **Confidence** | Verified by reading `TrainControlUI.java:2286-2333`. Whether `getAutonomySession()` can return null with `activeDiagramConfiguration != null` was not established — the omission is filed as a duplication hazard, not as a demonstrated wrong message. |

The `OPV-C5` fix put the capture's conditions in front of the message, which is right (OV2-D7). It did
it by copying them:

```java
if (captureSession && setupEditDeclinedDuringRun
    && this.activeDiagramConfiguration != null && this.model.hasAutoLayout()
    && this.model.getAutoLayout().isValid()
    && !this.model.getAutoLayout().isRunning())
{
    this.model.log("Where each locomotive finished has not been saved, because ...");
}
else if (captureSession && this.activeDiagramConfiguration != null
        && this.model.hasAutoLayout()
        && this.model.getAutoLayout().isValid()
        && !this.model.getAutoLayout().isRunning())
```

Two copies of one predicate whose whole purpose is that they agree; the next condition added to the
capture will be added to one of them. And the capture already has a fifth the copy does not carry —
`if (session != null)` at `:2305`, inside the `else` — so a null session is still a silent skip that
the message would blame on the declined edit, which is the exact defect `OPV-C5` was filed for,
surviving in the one condition that lives inside the block rather than on it.

*One boolean above both — `boolean wouldCapture = captureSession && ...` — and the two branches read
`wouldCapture` and `wouldCapture && setupEditDeclinedDuringRun`. The `session != null` test belongs in
it rather than inside the body.*

---

## D findings — checked and found sound, and coverage edges

### OV2-D1 — OPV-C1's `block` fix is real, and its test is a good one

`AutonomySession.java:1024-1025` reads `p.has("block")`. `grep -rn '"blocks"' src/ test/` returns
**nothing** in the whole tree. The singular is written at `Point.java:1019` and `AutonomyBuilder.java:844`
and read at `Layout.java:7534`, and the repo fixture `test/baseline/configuration.json:212` carries it.

`testAModernExportIsSpottedByEachOfItsKeys` (`testAutonomyDiagramSession.java:5811-5832`) loops over
`{"autoDestination", "protectingSignal", "block"}`, builds a one-point file for each, and asserts one
report line per key — then asserts a plain `{'name':'A','s88':1}` file reports **nothing**. That
control is what stops the three assertions being satisfied by a report that fires on everything, and it
is the assertion the README asks for. Its stated mutation (rename any key in the detector) holds: each
key is asked independently, so renaming one fails that key's iteration alone. 112/0/0 here.

### OV2-D2 — OPV-C3's comment repairs are true, and nothing else in the file contradicts them

`AutonomySession.java:667-670` now leads with *"And which way it is pointing, which the file cannot say
(ACC-C4)"* and records what it used to say. The code six lines down is `ways.get(0)` (`:702`). The
duplicated legality paragraph appears **once** — `grep -c "Not legality-checked"` returns 1. The only
other occurrence of "at random" in the file is `:375`, which is past tense about the same change
(*"before this it was made silently AND at random"*) and is correct.

### OV2-D3 — OPV-C7's paragraph is where the code is

`LayoutGrid.java:740-766` is the javadoc of the `paint()` override at `:767`, and carries the whole
OB-172 argument including the 38-pixels-against-30 measurement and the *"painted a second time rather
than moved out of the border"* sentence. `paintChildren` above it (`:726-738`) ends on its
`paintTrainOverCaptions` loop with no heading standing over nothing. The override itself is unchanged
and still what OPV-D6 checked.

### OV2-D4 — The ellipsis still cannot double, and the order under the heading is right

Two ways `OPV-C4`'s move could have gone wrong, both closed. The in-loop escape (`:403-421`) can only
run when `paths` is non-empty; the moved one (`:488`) requires `paths.isEmpty()`. Mutually exclusive,
so no menu carries both. And the resulting order in the case the move was about — `paths` empty,
`otherPaths` non-empty — is separator (`:455`), disabled locomotive name (`:457-462`), *More
Destinations* (`:473`), `...` (`:504`), which is the fix's stated intent and matches where the in-loop
one sits. The case it now misses is OV2-C1.

### OV2-D5 — IPR-B3's joiner close is exactly `read`'s recursion boundary

Three questions, checked separately against `ConditionOutline.java`.

**Does it still enforce the rule the class states?** Yes. The clear at `:179-182` removes only keys
**strictly deeper** than the joiner's own depth, so a run's own settled word survives every nested
group inside it. Traced on `A and (B or (C and D) and E)` — rows `(0,A) (0,AND) (1,B) (1,OR) (2,C)
(2,AND) (2,D) (1,AND) (1,E)`: the depth-1 `AND` at index 7 clears `settled[2]`, finds `settled[1] ==
OR`, and flags itself. That is the case where `read` would silently discard the second word
(`words.get(0)` at `:288`), and it is still caught. The test's own second half pins the flat case at
depth 0.

**Can an ambiguous outline now get through?** I could not construct one. `read` consumes a run by
recursing at `depth + 1` and returning the moment it meets a row shallower than that (`:235`, `:279`),
so a shallower row is exactly what ends a deeper run — and the clear is that boundary, expressed as a
map operation. `problems` and `read` agree on where a run begins and ends for every joiner row.

**The one asymmetry, and its direction.** `read`'s boundary is any shallower **row**; the clear fires
only on a shallower **joiner**. So two runs at one depth separated only by shallower *conditions*
would still be flagged, though `read` reads them as separate groups. That is over-strict rather than
permissive — the same direction `IPR-B3` was filed about, not the dangerous one — and I could not
produce such an outline from `write`, which emits a joiner row between every pair of siblings at a
level. Recorded rather than filed. `core.testConditionOutline` 21/0/0.

### OV2-D6 — OPV-C6's two citations are correct at HEAD, and the rest of the family was swept

`Layout.java:5103` and `:5213` both now say *"at the `activeLocomotives.put` in
`executePathInternal`"* rather than a number, and that `put` is at `:5414`. The two numeric citations
left in that comment are right: `:3232` is `path.get(0).getStart().reserve(loc)`, and `:2923` is the
*"it went straight out to executePath's handler"* sentence. `TrainControlUI.java:3636` now names
`autonomyEditorClosed()`.

Every remaining `:NNNN` citation in the six files this round touched was located: `Layout:3576` (cited
from `AutonomySession.java:2963`) is exact; `GraphRightClickGeneralMenu.java:111` is a `git show`
reference into a deleted file; `TrainControlUI:2259` (from `AutonomyEditorPanel.java:6410`) is one line
above the comment it means. The one that is genuinely wrong is OV2-C4.

*One observation, argued rather than filed. `Layout.java:5216` cites `:2923` as having "promised
this — 'it went straight out to executePath's handler, which deliberately does not unlock'". At
`:2923` that clause is quoted as the rationale being **overturned**: the next sentence is "That is true
of a failure MID-RUN and false here ... The rule was lifted from the case whose precondition made it
safe." Both comments are individually true — `:2923` is about a version in which nothing released the
prefix at all, and `:5216` is about a version in which `configureAndLockPath` does — but a reader
following the citation lands on a paragraph arguing the opposite of what sent him there. Not filed as a
defect; noted in case the sentence is ever rewritten.*

### OV2-D7 — The message really is behind the capture's own conditions now

`TrainControlUI.java:2286-2289` carries all four of the `else if`'s terms. So an exit with autonomy
still running, or with no active diagram configuration, or with an invalid layout — each of which
skipped the capture silently before and was then blamed on the declined edit — no longer produces the
message. That is the reachable half of `OPV-C5` and it is correctly fixed. The residual is the
duplication and the fifth condition, OV2-C5.

The flag's remaining door, `AutonomyEditorPanel.setupChanged()` (`:6425-6461`), is reachable with
autonomy busy for the reason `VD11-C8` gives at `:6453-6458`: the rebuild is posted one event later
than the write, so a run starting inside that window finds the gate shut. The flag still does its job
for that door.

### OV2-D8 — OPV-C2's disposition is honest, and its other two halves are unclaimed

`ACC-C2`'s disposition now reads *"**half done, and the first wording of this was untrue (OPV-C2)**"*
and says the artefact will carry the paragraph after the next harness run. That is true:
`grep -c "Before treating a row as a regression"` returns 0 in
`docs/reviews/2026-09-03-parity-report.md` and 1 in `docs/tools/parity/compare.py`.

The two halves `OPV-C2` raised in its body are untouched and are not claimed by the disposition, which
is the correct state under *"one status, one location"* but is worth saying before a tag: the parity
report still has **no status line** (so by this folder's README it is open), and it is still a
generated report living in `docs/reviews/`, which the README says *"do not belong in this folder."*
Neither blocks the tag.

### OV2-D9 — The tests that were run

Five classes through `one.sh`, one JVM each: `core.testAutoLayout` (26),
`core.testConditionOutline` (21), `regression.testEditorSurfaceRules` (37),
`core.testAutonomyDiagramSession` (112), `core.testAutonomyPathValidation` (15). **211 tests, 0
failures, 0 skips**, exit 0, and the runner's own skip check clean. The live-layout guard printed
nothing. Two files in `cs2_sample_layout/` (`config/autonomy/configuration-Main.json`,
`config/autonomy/setup.json`) were already modified in the working tree before this pass started —
Adam's application writing `loc`/`facing` as trains move — and the fingerprint before and after each
run matched, so nothing in this pass touched them.

### OV2-D10 — The four still-untested findings: what a test would assert, and where an `MT-` is the honest answer

Two are worth writing, two are not, and the two that are not are not the two that look UI-bound.

**`ACC-C4` — deterministic facing. A test is practical, and it is the best value of the four.**
`importLegacy` is already driven headlessly nineteen times in `testAutonomyDiagramSession`, and
`LegacyImport.facingsInvented` (`AutonomySession.java:377`) is a public counter that **no test asserts
today** (`grep facingsInvented test/` → nothing). The test: build a legacy file placing one locomotive
on a square the builder splits into two or more copies; import it into a fresh session; assert
`result.facingsInvented == 1` **first**, as the precondition that the invention path was actually
taken — without it the test passes on any file whose facing was already known. Then repeat the import
into a fresh session twenty times and assert every run produced the same `Side`. Twenty repeats is what
makes it a guard rather than a coin toss: under the old `new Random().nextInt(ways.size())` on a
two-copy square the test fails with probability `1 - 2^-19`, and the README's rule about a guard that
only sometimes catches the regression is the reason to say twenty rather than two. Assert the repeat
count as the floor, per *"property tests need a floor on how much they exercised."* No production
change needed.

**`ACC-C6` — the undo save's log. The behaviour needs an `MT-`; the rule does not.** Making
`saveQuietly()` return false at that instant means an unwritable setup file under an open
`LayoutEditor`, which is a window and a filesystem state — an `MT-` entry, and the honest answer for
the behaviour. But the finding's actual shape is textual: *a call whose answer is discarded at one of
its sites*. `regression/testEditorSurfaceRules` already reads `AutonomyEditorPanel.java` line by line
for exactly this class of fault and says why (*"the fault is textual ... and it is invisible to a test
that drives the model"*). A fourth rule in that class — every `saveQuietly()` call site outside
`AutonomySession` appears inside an `if (!...)` — is a dozen lines, needs no window, and **would have
failed today** on `TrainControlUI.java:22557` (OV2-C3). That is worth more than the behavioural test
would have been.

**`ACC-B3` — the exit-capture skip. The behaviour needs an `MT-`; a source rule is worth writing.**
`saveState(boolean, boolean)` is public, but it is a method on a `JFrame` whose construction takes the
process's one shot at the foreground, and `setupEditDeclinedDuringRun` is private state set through a
posted `invokeLater` inside a Swing timing window. Driving it means: a real window, a real run started
between a setup write and the event after it, and an exit. That is an `MT-` and pretending otherwise
would produce a test that mocks the window and therefore tests the mock. What **is** practical, and
what OV2-C5 argues for, is a source rule that the message branch at `TrainControlUI.java:2286` and the
capture branch at `:2296` carry the same conditions — or, better, that they read one boolean. That
rule is the thing most likely to rot, and it is the thing a person cannot see.

**`ACC-C10` — the empty-list ellipsis. Not practical, and no source rule is honest either.**
`LayoutRightclickAutonomyMenu` is package-private, its constructor takes a live `TrainControlUI`, it
reaches `getModel().getAutoLayout().getPossiblePaths()` and `ui.canStartAutonomy()`, and the thing
under test is the *sequence of `add` and `addSeparator` calls* — `ACC-D8` already says there is no test
at the assembly layer. A source-level rule here would only encode today's nesting, which is precisely
what OV2-C1 shows is the thing in question, so it would have passed before the move and after it. An
`MT-` is the honest answer: right-click a locomotive whose every destination is switched off or
excluded and say whether there is a way to the autonomy tab. **The alternative is a refactor rather
than a test** — lift the three decisions (`does the top-level list get a heading`, `does the submenu
get one`, `does the escape appear`) into a package-private pure function over
`(possible, shown, others)` and test that. Worth saying out loud because it is a real option and
because it is not a test: it is a change to production code, and it should be decided as one.

### OV2-D11 — What this validation did NOT cover

- **The mutation was not executed.** OV2-B1's "removing the gate still fails it" is derived from
  reading `unlockPath`'s atomic branch at `Layout.java:3312-3325` and from `atomicRoutes = true` being
  the field's initialiser. The instructions for this pass forbid editing any file but this one, and
  a mutation is an edit. What **was** executed is the thing that matters more: the exception's own
  stack trace, which is what shows the test missing its target.
- **No headful class was run** — `ui.testDiagramLooksRight`, `testTheDiagramPrintsItsCoordinates`,
  `testRenderingCost`. Adam's application may hold the foreground and a focus failure would be
  unattributable. `LayoutGrid`'s paint change was re-read (OV2-D3), not re-measured.
- **`battery.sh` was not run**, per Adam's ruling. Neither were `testHomeStaging`,
  `testMessageBundles`, `testJavadocsAreAttached` or the route classes: nothing in this round's diff
  touches them, and OPV-D4/D9 covered them at the previous commit.
- **The application was not driven.** OV2-C1's menu order is derived from the `add` sequence; OV2-C2's
  unreachability is a search that came back empty, not a proof.
- **OV2-C2's reachability was not settled.** I closed four doors that OPV-C5 named and one it did not
  (the viewer's own right-click menu, `LayoutRightclickAutonomyMenu.java:142`). I did **not**
  enumerate route triggers or the timetable as ways `Layout.isRunning()` could become true while the
  setup editor is open, and that is where a counter-example would be if there is one.
- **`ACC-C4`'s reproducibility across two rebuilds** is still what OPV-D15 left it as. My test
  recommendation in OV2-D10 would settle it; reading did not.
- **`RGN-C3`** (the bracket rule in `RouteCommand`, committed in the same `504ad2ab` as `IPR-B3`) was
  read as a comment change and an `MT-270` referral and taken no further — it is explicitly Adam's
  decision, not a defect.
- **`MT-250`** — whether EN57-203 can leave TunnelLongPark nose-first — is untouched by this pass and
  remains the acceptance review's one open gate.
