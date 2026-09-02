# Seventh validation, 2026-09-02: the fixes made in answer to the sixth pass

**Status:** open

**Prefix:** `V37`. Cite findings from here as `V37-B1`, `V37-C2`, `V37-D5` and so on. `V3`, `V31`,
`V32`, `V33`, `V34`, `V35`, `V36`, `V2`, `FV2`, `SV2`, `TV2`, `VAL` and `VB` are taken by earlier
passes; nothing in `docs/` declares `V37`.

| | |
|---|---|
| **Reviewed** | branch `autonomy-diagram-r0`, v3.0.0 |
| **HEAD at the start and end of this pass** | `609a57b1` — "Validation round 3: the branch that would have absorbed the failure it was written for" |
| **Scope** | that commit and nothing else — three test files, 34 insertions, 73 deletions. It answers `V36-A1`, `V36-B1`, `V36-B2` and `V36-C3`. |
| **Not in scope** | the validation documents themselves, the findings they raised, and everything under `6507ee63`. |

**Method.** Reading only. No test, `battery.sh`, `one.sh`, `ant`, `javac`, `java` or TestNG invocation
was made — a battery was running throughout this pass. Where a claim about a test helper had to be
settled exactly, the helper was reimplemented in Python over the real source file and its output read;
that starts no JVM and touches nothing. `cs2_sample_layout/` was neither read nor written; facts about
the operator's railway come from `test/operator_layout/`. Only this document was created.

---

## Summary

**No A.** The commit touches test code only, and the removal it is named for is done properly: the
trapped branch is gone, `canReachAnyDestination` is no longer asked, no helper died with it, and every
import in the file still has a live use. `V36-A1` and `V36-B1` are genuinely fixed.

**Two B's, both of the same kind, and it is the kind this project keeps producing.** In each case the
code change is right and the sentence recording it is false — and in this project the sentence is the
design record.

The five-train test kept the *failure message* the deleted branch was written to justify. It still says
*"Every train is standing somewhere that CAN reach another station - checked above"* when nothing is
checked above any more, and it still tells the reader the fault is *"the planner being short rather
than the railway"* — which is the opposite of this commit's own diagnosis of the live flake, and the
opposite of what the comment eight lines above it now says.

`testHomeStaging`'s deleted precondition is justified by a control **that is in a different test**. The
comment points at "the CONTROL at the end of this test", a twenty-unit train refused and an eight-unit
one accepted; this test's train is forty units and it has no accepted case at all. The 20/8 pair is
3,100 lines away in `testALongerApproachIsStillTriedWhenTheShortOneHasNoRoom`, on another fixture. So
`V36-B2`'s confound is still guarded by nothing but `setReversible(true)` — and is now recorded as
having been ruled out.

| | Finding | Severity | Disposition |
|---|---|---|---|
| B1 | The branch is gone; its failure message is not. The assertion still claims a reachability check that no longer runs, and blames the planner for what this commit says is the arrangement | B | Open |
| B2 | The precondition was deleted on the strength of a control in another test. This test has no accepted case, so `V36-B2`'s mutation still passes green — and the comment now says it cannot | B | Open |
| C1 | `V36-C`'s recorded reason is false: `autonomyHasErrors()` never appears in a comment in that file, and `V36-C3` said so | C | Open |
| C2 | The twin site five lines below is still a whole-file `contains`, in the same test method, under the comment arguing that shape is inadequate | C | Open |
| C3 | "NOT caused by anything changed today" is contradicted by today's commits: `56c6080e` changed the frozen fixture this test runs on — the diagram itself, a `canReverse` flag, and every train's starting square | C | Open |
| D1..D9 | Nine checks that came back clean, including the room-rule claim (true) and the "third rule" question (there is none) | D | Closed |

---

## A — high

**None.** The commit changes three test files and no production code, and the removal it is named for is
complete. See `V37-D1` and `V37-D2`.

---

## B — medium

| | Finding | Status |
|---|---|---|
| **V37-B1** | The removed branch's failure message survived it, and now asserts a check that does not happen | open |
| **V37-B2** | The precondition was deleted for a control that is in another test | open |

### V37-B1 — the branch is out; the sentence written to justify it is still in

**FIXED 2026-09-02 (`8c4c4aa4`).**  The failure message no longer claims a check that was removed with the branch.  It now says which question decides and points at the per-train diagnostic that answers it, instead of naming the planner in the case the commit before it said was the diagram's.

`test/core/testTrainsComeHomeToTheirPlatforms.java:249-253`:

```java
            assertTrue(plan.isPossible(),
                "no way home from " + arrangement + " (outcome " + plan.getOutcome()
                + ", blocked " + plan.getBlocked() + "). Every train is standing somewhere that CAN "
                + "reach another station - checked above - so this is the planner being short rather "
                + "than the railway; the per-train reachability printed above says which one");
```

That message was written **by** the branch, in `6507ee63`:

```
-                + ", blocked " + plan.getBlocked() + "). Five trains that set off from ordinary "
+                + ", blocked " + plan.getBlocked() + "). Every train is standing somewhere that CAN "
+                + "reach another station - checked above - so this is the planner being short rather "
```

"Checked above" was true then: lines 249-272 of that revision walked `STARTED_AT` asking
`canReachAnyDestination` of each train and returned early if any was trapped, so anything reaching the
assertion had passed the screen. This commit deleted the screen and left the sentence.

**The pass was asked whether the test now asserts what it did before the branch existed.** The
*predicate* does — `assertTrue(plan.isPossible(), …)` is byte-identical to `HEAD~2`'s. The *message*
does not: `HEAD~2` said *"Five trains that set off from ordinary platforms have to be able to get back
to them; the per-train reachability printed above says which one cannot and whether it is the graph or
the plan that is short"*, which claims nothing and points at both halves. What shipped claims a
screening that no longer runs and points at one half.

**Why this is a B and not a C.** It is not a stale phrase in a quiet corner; it is the text Adam reads
on a failure the same commit measures at three in eight runs, and it names the wrong culprit:

- The comment **eight lines above it** now says the opposite — *"the early return skipped the facing
  check this whole class exists for"*, and that a trapped train and an impossible plan *"are
  independent facts"* (lines 236-248).
- The commit message says the opposite too: *"What fails is Return Home on his own diagram with five
  trains and three of them on Inter squares: NO_PLAN_FOUND with an empty blocked list."* `TopMainR1Inter`
  and `TopMainR2Inter` are squares on his Main page (`test/operator_layout/config/autonomy/setup.json`,
  `pointNames`). A train left standing on one of those is an arrangement fact, which is exactly
  "the railway" — the alternative this message tells the reader to discard.
- `6507ee63`'s own subject line was *"the five-train test stops blaming the planner for the diagram"*.
  With the branch removed, the surviving message blames the planner for the diagram unconditionally.

**Suggested repair.** Restore `HEAD~2`'s wording, or make the message state what is actually known —
that `reachability()` was printed above and says which train and which half. Nothing in the current
code entitles it to say which of the two it is.

### V37-B2 — the precondition was deleted for a control that is not in this test

**FIXED 2026-09-02 (`8c4c4aa4`).**  The audit test has a control of its own now - a three-unit train that fits, in the same test, on the same fixture - rather than pointing at one three thousand lines away.  Mutation-confirmed: deleting the planner's room rule fails two tests where it used to fail one, which is what `V36-B2` was actually asking for.

`test/core/testHomeStaging.java:198-209`, the comment that replaced `V36-B2`'s precondition:

```java
        // NO PRECONDITION ABOUT mustBackIn AT ALL, and the two attempts at one are worth recording.
        ...
        // What rules out every alternative explanation is the CONTROL at the end of this test.  A
        // twenty-unit train refused and an eight-unit train accepted cannot both be mustBackIn, which
        // does not look at length; nor `validateTrainLength`, which is inert at `maxTrainLength` zero.
        // The pair is the precondition.
```

**There is no such control in this test.** `testTheAuditSeesTheReversalRoomRuleTheStagingPlannerDoesNotHave`
runs from line 162 to line 250. Its train is set once, to forty (line 181). Its only assertion labelled
"control" is line 222-226, and it is a refusal with no acceptance beside it:

```java
        // THE RUNTIME REFUSES IT, which is the half that exists.
        assertFalse(layout.isPathClear(layout.bfs(layout.getPoint("HS A"),
            layout.getPoint("HS D"), null), tooLong), …
```

The two assertions actually "at the end of this test" are the audit count (line 233) and
`assertFalse(layout.planReturnToHome().isPossible())` (line 240). Neither accepts anything.

The 20/8 pair the comment describes is in a different test, on a different fixture, 3,100 lines away:
`testALongerApproachIsStillTriedWhenTheShortOneHasNoRoom` at line 3366 — `loc.setTrainLength(8)` at
3377, `loc.setTrainLength(20)` at 3436, the acceptance at 3422-3428 and the refusal at 3438-3441, over
`twoWaysToOneBerth()` (3455-3482), which has two measured approaches to HS D of 5 and 10. **That pair
is sound** — see `V37-D4`, where the "is there a third rule" question is answered in full. It just has
nothing to do with the test whose precondition was deleted on the strength of it.

**What it costs, traced.** `V36-B2`'s mutation was: invert `setReversible(true)` on line 189. Re-run
against the current file with the planner's room rule also deleted — the divergence this test exists to
catch:

- Line 223, `isPathClear(bfs(HS A, HS D), tooLong)` — refuses, because `Layout.java:2312-2330` removed
  the terminus rule and the *runtime's* room rule at `Layout.java:2405-2409` is still there. Passes.
- Line 233, `auditAgainstRuntime() == 0` — `runtimeSays` is built from `getPossiblePaths`
  (`HomeStaging.java:620-623`), which filters on `isDestination`, block occupancy and `isPathClear`
  (`Layout.java:4371-4383`), so the runtime's room rule keeps HS D out of it. `plannerSays` is built from
  `firstClearRoute` (629), and with a non-reversible locomotive `mustBackIn` is true
  (`HomeStaging.java:1585-1588`) with no reversing point on the ring, so HS D is out of that too. Both
  loops (632, 684) find nothing. Passes — **with the planner's room rule missing.**
- Line 240 passes for the same reason.

So the mutation still yields green over the exact defect named in the test's own javadoc. That is
`V36-B2` verbatim, unfixed. What changed is that a comment now says it has been ruled out.

**Why B and not A.** As the file stands today it does discriminate: with `setReversible(true)` and the
planner's room rule deleted, HS D is in `plannerSays` and not in `runtimeSays`, the second loop at line
684 counts it, and line 233 fires. The test is sound; nothing asserts the one fixture fact that makes it
sound, and the record now says something else does.

**Suggested repair.** Put the pair in this test, which is what the comment already promises: after the
existing assertions, set the length to something the five-unit approach holds, assert the runtime
accepts it and the planner offers it, and restore the length in the `finally` that is already there
(lines 245-249). That discriminates room from `mustBackIn` without restating a setter, which is what
`V33-C8` objected to and what both earlier attempts got wrong.

---

## C — low

| | Finding | Status |
|---|---|---|
| **V37-C1** | `V36-C`'s recorded reason is false, and the finding it cites says so | open |
| **V37-C2** | The twin assertion five lines below still has the shape the fix removed | open |
| **V37-C3** | "Not caused by anything changed today" is broader than its evidence, and today's commits contradict it | open |

### V37-C1 — the strip's comment claims a comment that does not exist

`test/regression/testErrorsStopTheSetupRunning.java:243-246`:

```java
        // THE BODY, not the file (V36-C).
        //
        // A whole-file `contains` is satisfied by the method name appearing in a COMMENT, and this
        // method's comments name it twice.
```

`autonomyHasErrors` occurs **once** in `src/org/traincontrol/gui/AutonomyOverlayToggle.java`, in code:

```
355:        boolean broken = ui != null ? ui.autonomyHasErrors() : lastTotalErrors > 0;
```

The only comment mention in the file is `hasErrors()` at line 345 — *"It asked a COUNT while the guard
asked `hasErrors()`"* — which does not contain the literal `autonomyHasErrors()` the assertion searched
for. The old whole-file `contains` was therefore **not** satisfied by any comment.

`V36-C3`, the finding cited, states this explicitly:

> `V34-C6` raised this and rated it C on the grounds that *"a plain revert is still caught, because
> `autonomyHasErrors()` appears nowhere else in that file today."* That is still true — line 355 is the
> only occurrence, and the new comment says `hasErrors()` rather than `autonomyHasErrors()`, so it does
> not satisfy the `contains`.

The change itself is right and worth keeping — see `V37-D5`; a body extract is proof against a future
comment, and `V34-C6`/`V36-C3` asked for exactly it. What is wrong is the reason left in the file, which
the next reader will check and find false, in the one place the project keeps its reasoning.

Two smaller things in the same block. The commit message calls the finding "V36-C"; the finding is
`V36-C3` and `V36-C1`, `C2`, `C4`, `C5`, `C6` are unrelated. And `V36-C3`'s actual substance is
untouched: it says what is unpinned *"is that the answer reaches `fixing` at all — `boolean broken =
ui.autonomyHasErrors(); fixing = source != null && source == start && errors > 0;` would still pass."*
It still would. Narrowing the haystack from the file to the body does not reach that, and nothing in the
commit claims it does — but the finding should not be read as answered.

### V37-C2 — the sibling five lines down keeps the shape

**FIXED 2026-09-02 (`8c4c4aa4`).**  The twin of the whole-file grep fixed the round before, five lines below it - the sweep-the-siblings miss made at the site of the fix for it.

`test/regression/testErrorsStopTheSetupRunning.java:257-259`, immediately after the assertion this
commit rewrote:

```java
        assertTrue(menu.contains("autonomyErrorCount()"),
            "LayoutRightclickAutonomyMenu no longer reads autonomyErrorCount() at all - the right-click "
            + "Start item's own OB-090 fix has gone");
```

`menu` is `read("src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java")` (line 202) — the whole
file, comments included, no body extracted. That is the shape the comment four lines above declares
inadequate, at a site the same method already holds a variable for. This is the README's most repeated
defect — *"When you fix a call site, grep for its twins before closing the finding"* — with the twin
five lines below the fix.

Not a live hole today: `autonomyErrorCount` appears twice in that file, at line 188 in a comment and at
line 203 in code, and the comment is *"canStartAutonomy twice, autonomyErrorCount twice"* — no
parentheses, so it does not satisfy the literal. It is a C for the same reason `V34-C6` was.

`bodyOf(menu, "private void addStartItem(")` or whichever declaration line 203 sits in would close it in
one line, with the helper already in the class.

### V37-C3 — the flake's exoneration is wider than what was checked

**WITHDRAWN 2026-09-02 (`8c4c4aa4`), which is what this finding asked for.**  "Not caused by anything changed today" is off the record: `56c6080e` re-froze the fixture that test runs on the same morning - four tiles out of the diagram, a `canReverse` flag dropped, every locomotive's starting square moved - so the flake may be Adam's own diagram edits arriving in the test.  The narrower claim it rests on, that the room rule cannot fire in that test, stands and was verified twice independently.

The commit message states:

> It is NOT caused by anything changed today - the room rule cannot fire in that test at all, because it
> never sets a train length and the rule returns null at zero.

**The room-rule half is true** and I verified it independently — see `V37-D3`. The inference from it is
not: ruling out one rule does not rule out "anything changed today", and today's commits changed the
machinery this test drives *and the railway it drives it on*.

`56c6080e` ("Clear the four battery failures, and stop two tests asking the wrong question", 2026-09-02)
changed, in one commit:

- `test/core/testTrainsComeHomeToTheirPlatforms.java` (146 lines) — the test itself;
- `test/operator_layout/config/gleisbilder/1 - Main.cs2` — **the frozen diagram**. Four `gerade` tiles
  (`0x10c`-`0x10f`) are gone and `0x10c`/`0x110` became `tunnel` with rotations. That is the graph this
  test plans over;
- `test/operator_layout/config/autonomy/configuration-Main.json` — every locomotive's starting square
  moved (`EN57-947` from `13,9` to `20,14`, `EN57-203` from `7,7` to `5,4`, `2-8-4 3505 SP` from `13,11`
  to `6,4`), and `1 - Main:13,9` lost `"canReverse": true`.

A reversing point removed from the fixture bears directly on a test that deliberately makes three of its
five locomotives non-reversible (`testTrainsComeHomeToTheirPlatforms.java:609`), because
`mustBackIn` then demands a route that turns them. Whether it *is* the cause I cannot say without
running anything, and I did not. The point is that the sentence asserts it is not, and the evidence
offered reaches one production rule.

Also today, and on the same path: `e6791631` moved the room check above the `seen` marking in
`HomeStaging`'s search (checked — a no-op at length zero, `V37-D3`), and `8d1c17ca` added a square rule
to the home loader (`Layout.java:1139-1170`, checked — it only drops *assignments*, which `place()`
clears at line 577).

On the numbers themselves, which the pass was asked about: **nothing in the commit contradicts them and
the arithmetic is consistent.** "1 failure in 5 runs" and "3 in 8 observations of the whole day" are one
sample quoted at two denominators, not two measurements — 1 of the 5 plus 2 of the other 3. Quoting the
smaller rate first understates the day's, and eight observations put a very wide interval around either;
but three failures and five passes do establish the thing that matters, which is that it is a flake and
not a break. The decision to leave it red is Adam's own ruling on its ancestor and is recorded as such.

---

## D — checked and sound

| | What was checked | Result |
|---|---|---|
| **V37-D1** | The trapped branch's removal is complete — no call, no local, no dead helper, no dead import | clean |
| **V37-D2** | The assertion's predicate is `HEAD~2`'s exactly | clean |
| **V37-D3** | The room rule really cannot fire in the five-train test | clean |
| **V37-D4** | Is there a third rule that refuses 20 and accepts 8? No | clean |
| **V37-D5** | `bodyOf` handles `public final void syncRun()`, and the assertion still fails on a revert | clean |
| **V37-D6** | The deleted `reversingPointsOn` helper has no surviving references; imports still used | clean |
| **V37-D7** | The fix's reading of `V36-B2` is correct — the old precondition really was backwards | clean |
| **V37-D8** | The audit test does discriminate as written today, in both directions | clean |
| **V37-D9** | `cs2_sample_layout/` is still not opened by this suite | clean |

**V37-D1 — the removal is complete.** `canReachAnyDestination` is not called anywhere in the file; the
only mention is the word inside the new comment (line 239). The `trapped` local, its loop, the
`assertFalse`, the `System.out.println` and the `return` are all gone. Nothing became dead: the method
still has real callers at `LayoutRightclickAutonomyMenu.java:767` and four assertions in
`testAutoLayout.java:1290-1320`. No helper in the test class lost its last caller — `reachability()`
(234), `routeExists` (393, 394, 443), `arrival` (292), `ordinaryCopy` (195, 663), `pointFor` (612),
`describe` (220), `awaitStopped` (218, 270), `name` (163, 600) are all still reached. Every import was
traced to a live use: `File` 113, `ArrayList` 127/189/223/274/429/550, `LinkedHashMap` 88, `List`
throughout, `Map` 88/276/348, `SkipException` 117/142, `Edge` 404/431/509, `HomeStaging` 225/232/259,
`Layout` 77/142, `Point` throughout, `LayoutDiagram` 127, `Locomotive` 278/358, `MarklinControlStation`
73, `init` 124. Nothing unused.

**V37-D2 — the predicate is restored.** `assertTrue(plan.isPossible(), …)` at line 249 is the same
predicate `HEAD~2` asserted at the same place, with the branch's early return gone from in front of it,
so a trapped-train arrangement now reaches it and goes red. Only the message differs, which is `V37-B1`.

**V37-D3 — the room rule cannot fire in that test, and the claim is exactly right.** Three legs, all
checked:

1. `testTrainsComeHomeToTheirPlatforms.java` contains no `setTrainLength` at all. `place()` sets
   `setReversible` (609) and `setPreferredSpeed` (611) and nothing else.
2. `Locomotive.trainLength` initialises to `0` in both constructors — `Locomotive.java:159` and `284`.
   The locomotives are made by `model.newMM2Locomotive` (line 600) at addresses 2101-2105, so they carry
   the default.
3. `Layout.measuredRoomToReverseInto` returns null before looking at anything else
   (`Layout.java:6203`): `if (loc == null || loc.getTrainLength() == null || loc.getTrainLength() <= 0)
   return null;`

Both copies of the rule are gated on that null — the runtime's at `Layout.java:2405-2409`
(`if (measuredRoom != null && loc.getTrainLength() > room)`) and the planner's at
`HomeStaging.java:1041-1043` (`if (room != null && loc.getTrainLength() > room) continue;`). At length
zero neither can refuse anything. The claim holds for **both** sites, which is more than the commit
says. `e6791631`'s reordering of the planner's copy is likewise inert here: with the rule never firing,
the `continue` never happens and the search behaves as before.

**V37-D4 — there is no third rule.** The pass asked whether something other than room could refuse a
twenty-unit train and accept an eight-unit one. Every read of `getTrainLength()` in `src/` was
enumerated:

- `Point.validateTrainLength` (`Point.java:910-916`) — `if (!this.isDestination) return true; if
  (this.getMaxTrainLength() == 0) return true;`. Reached from `Layout.isPathClear:2290` and from
  `HomeStaging.canRest:1673-1691`. `Point.maxTrainLength` defaults to `0` (`Point.java:32`) and the
  parser only sets it when the key is present (`Layout.java:7393-7397`). Neither `shortBerth()` nor
  `twoWaysToOneBerth()` emits `maxTrainLength` — `station()` at `testHomeStaging.java:108-113` does not,
  and HS D is written out by hand at 263-264 and 3468-3469 without it. Inert in both fixtures, exactly
  as the comment says.
- The room rule, at its two sites above.
- `Layout.java:5437`, `tailHasProvablyPassed(…, loc.getTrainLength())` — this is the run loop deciding
  whether to unlock an edge behind a moving train. It is not on the path-selection or planning path and
  cannot refuse a destination.

`mustBackIn` (`HomeStaging.java:1585-1588`) and `canRest`'s other three clauses (`isDestination`,
`isActive`, `getExcludedLocs`) are blind to length, and `twoWaysToOneBerth` has no reversing point and
sets the locomotive reversible (3381), so `mustBackIn` is false for both trains. **The 20/8 pair is a
sound control for the room rule** — in `testALongerApproachIsStillTriedWhenTheShortOneHasNoRoom`, where
it lives. One caveat worth writing down: it is sound because the fixture sets no `maxTrainLength`, and
nothing asserts that. Adding one to `station()` would silently move the discrimination onto
`validateTrainLength`.

**V37-D5 — the strip's extraction works, and the assertion is still a real one.** The two helpers were
reimplemented in Python and run over the real `AutonomyOverlayToggle.java`:
`bodyOf(toggle, "public final void syncRun()")` finds the declaration (line 297, the only occurrence),
takes the next `{`, and brace-matches to line 416 — the method's own closing brace, 6,794 characters.
`withoutComments` of that contains `autonomyHasErrors()`. So the assertion passes today for the right
reason, and if the strip reverts to a count — `autonomyErrorCount() > 0` in place of line 355 — the
literal disappears from the body and line 252 fails. The `assertFalse(sync.isEmpty())` on line 250
catches a rename or a signature change separately, so a silent pass is not available either way.

Both helpers' known weaknesses were checked against this body rather than assumed: `bodyOf` counts
braces without understanding string or char literals, and `withoutComments` does not either — a `//` or
a `{` inside a string would break both. `syncRun`'s body contains neither; its literals are I18n keys
and its lambdas are expression-bodied. It is a trap for a later edit, not a defect now.

**V37-D6 — nothing survived the helper.** `reversingPointsOn` has no references left in `test/` or
`src/`. `testHomeStaging`'s imports are all still used — `Layout` and `Point` were the helper's
parameter types and both appear throughout the rest of the class, and the helper's `java.util.List` and
`java.util.ArrayList` were fully qualified. One cosmetic leftover: two consecutive blank lines at
`testHomeStaging.java:281-282` where the method was.

**V37-D7 — `V36-B2` was read correctly.** The deleted precondition asserted
`reversingPointsOn(layout, layout.getPoint("HS D")).isEmpty()` while its own message described the
opposite state, and `mustBackIn` (`HomeStaging.java:1585-1588`) is
`at.isTerminus() && !loc.isReversible()` with no layout term in it. Reversing points reach the question
only through `connected(from, to, mustTurn)`, which is asked *after* `mustBackIn` has returned true
(`HomeStaging.java:1611`, `1622`, `1626`), so having none is the state in which the confound is live —
the commit's restatement of this is accurate. And on the ring the assertion could not fail: the helper
skipped the destination and no other point on `shortBerth()` is a terminus or reversing. Deleting it was
right; what replaced it is `V37-B2`.

**V37-D8 — the audit test discriminates today, and both directions are covered.**
`auditAgainstRuntime` (`HomeStaging.java:602-691`) runs two loops, `runtimeSays \ plannerSays` at 632
and `plannerSays \ runtimeSays` at 684, so a planner that offers *more* than the runtime is counted —
which is the direction this test needs, since the defect it names is the planner missing a rule the
runtime has. With `setReversible(true)` as written and the planner's room rule deleted, HS D lands in
`plannerSays` (`canRest` passes, `connected` passes, `mustBackIn` false) and not in `runtimeSays`
(`getPossiblePaths` filters through `isPathClear`, which still refuses on room), the second loop counts
it, and line 233 fails with its own message. The four surviving preconditions (194, 211, 214, 223) are
all about the fixture rather than about a setter and are unchanged.

**V37-D9 — the frozen fixture is still the only layout this suite opens.**
`testTrainsComeHomeToTheirPlatforms.java:113` opens `test/operator_layout` and
`support.LayoutSandbox.open` copies it (121). Nothing in the commit touches that, and no path in the
three changed files names `cs2_sample_layout`.

---

## What this pass did not cover

- Whether the flake is in fact caused by the fixture refresh of `56c6080e`. It needs a run, and this
  pass ran nothing. `V37-C3` says only that the commit's exoneration does not reach it.
- `V36-A1`'s own evidence — the twenty stations carrying `isAutoDestination` false, and
  `Point.setAutoDestination`'s javadoc — was taken as given. The fix removes the code that depended on
  it, so the claim is no longer load-bearing.
- `V36-C1`, `C2`, `C4`, `C5` and `C6`, which this commit does not answer.

## A note on dispositions

`V36-A1`, `V36-B1`, `V36-B2` and `V36-C3` are still marked **Open** in
`docs/reviews/2026-09-02-sixth-validation.md` (lines 38-45, and each finding's own status table), after
the commit written to answer them. `V36-C6` said this of the previous commit — *"this is the third
commit in a row to answer findings without dispositioning them"* — and it is now the fourth. Two of the
four are properly fixed (`V36-A1`, `V36-B1`) and should say so; `V36-B2` is not fixed and should say
that instead of nothing.
