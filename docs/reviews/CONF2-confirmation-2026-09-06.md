# Second confirmation pass: did the fixes for CONF and REG6-B3 stick, and what did they break?

**Status:** open

**Prefix for citing these findings elsewhere:** `CONF2` (confirmed unused - `grep -rn "CONF2-" docs/` returned nothing before this file).

**Reviewed:** branch `autonomy-diagram-r0`, on 2026-09-06. **The tree moved during this pass.** It was opened at HEAD `8da9416f`, and `62f845a5` ("The stop is the gate's own answer, so the two cannot drift") landed while the CONF-A1 trace was being written. Everything below is stated against **HEAD `62f845a5`**, with the `8da9416f` state named explicitly wherever the two differ - because they differ on the single most important question in the brief.

Subject: `64e79293`, `8da9416f`, `62f845a5`, read against `docs/reviews/CONF-confirmation-2026-09-06.md` and the three reports it answers. Scope was the six items in the brief plus the five previously-confirmed items named for regression. Nothing else in the tree was re-reviewed.

**Method - and its limit.** Reading and tracing only. No build, no test run; that was the instruction. `cs2_sample_layout/` was read (`grep` only, to count `canReverse`/`mustReverse` markings) and never written. No file except this one was created or modified. Source pins asserted by `testNonReversibleTrains.testTheRunAsksThatRule` were checked by flattening `Layout.java` the same way the test does and testing `String.contains` - that is string matching on the source, not a test run, and all six hold.

---

## Verdict

**The critical question in the brief was a real defect, and it was real for about twenty-eight minutes.**

At `8da9416f`, `ManualReversalPrompt.forJourney`'s policy answered `asksAbout(at)` as `turn && asking.asksAbout(at)`. `Layout.shouldReverseAt:5214` reads `asksAbout` as *the* test for "is this turn compulsory". So when the operator answered **keep direction**, `turn` was false, `asksAbout` collapsed to false for every square, every may-reverse **turning copy** on the journey looked compulsory, and line 5214 turned the train - **against the operator's explicit no, at the exact door built to honour it.** Both call sites reached it: the intermediate stop and `Layout:6192`, which is the arrival case Adam described in words ("send a train to a 'may reverse' point").

**`62f845a5` fixes it, and fixes it the right way.** `ManualReversalPrompt.java:140` is now `return asking.asksAbout(at);` - answer-independent - and REG6-B4 is settled where it belongs: `Layout.java:5876` no longer uses a separate stop predicate at all, it asks `shouldReverseAt` itself, so a train stops exactly where it is about to be turned. That is a better answer than the one this pass was going to propose, and it removes `stopsToDecideAt` rather than keeping a predicate nothing needs.

**What `62f845a5` did not do is write down what it changed about the policy contract, or test it.** Moving the gate to `shouldReverseAt` means `ReversalPolicy.shouldReverse` - the method that puts a modal dialog up - is now consulted **while the train is moving**. That is DIR-A1's mechanism. It is not reachable today, because both live doors hand over a pre-answered policy. It is reachable by writing four characters, because `ManualReversalPrompt.forOperator` is public, returns a policy whose `shouldReverse` *is* the dialog, and two javadocs plus one test now positively assure the next author that it is safe (`CONF2-B1`). And the clause that caused `CONF-A1` - `turn &&` - has been added and removed twice today with nothing red either time (`CONF2-B2`).

Score on the brief: **five of six CONFIRMED FIXED**, one (`CONF-A2`) **superseded rather than fixed** - the predicate it created is gone, and the property it wanted holds more strongly without it. All five regression re-checks CONFIRMED. Two new findings, both introduced by `62f845a5`, both medium.

---

## A - high

None. Nothing in this scope produces wrong behaviour on the layout at HEAD.

The `8da9416f` defect described in the verdict **would** have been an A. It is not carried as one here because it is fixed in the tree this document reviews; it is recorded in full as `CONF2-D1` so the calibration is not lost.

---

## B - medium

| | | |
|---|---|---|
| **CONF2-B1** | The reversal policy's contract is inverted, and three places now say the opposite | Open |
| **CONF2-B2** | `62f845a5` shipped untested, and the clause it removed has no pin | Open |

### CONF2-B1 - `shouldReverse` is now asked before the train is stopped, and the documentation says it cannot be

`Layout.java:5876-5879`. Until `62f845a5` the loop stopped the train first and asked afterwards:

```
if (isCurrentLayout() && stopsToDecideAt(current, reversals))   // cheap, silent, non-blocking
{
    loc.setSpeed(0).waitForSpeedBelow(1);
    if (shouldReverseAt(...))                                   // may block; train is standing
```

It now asks first and stops inside the answer:

```
if (isCurrentLayout()
    && shouldReverseAt(current, path.get(path.size() - 1).getEnd(), loc, reversals))
{
    loc.setSpeed(0).waitForSpeedBelow(1);
```

`shouldReverseAt:5222` ends `return reversals.shouldReverse(loc, current);`. So `shouldReverse` is now evaluated on a **moving** train. That is the precise mechanism of DIR-A1 - "measured at 30 when the question was put and still 30 five seconds later".

**Not reachable today.** Both doors (`AutoLocomotiveStatus.java:1043`, `LayoutRightclickAutonomyMenu.java:1031`) call `forJourney`, whose `shouldReverse` returns a captured boolean and cannot block; `KEEP_DIRECTION` returns false; `ALWAYS_REVERSE` and `null` return at `Layout:5161` without touching the policy. The commit message says this, and it is correct. Per this folder's own rule, that keeps it out of A.

**What makes it a finding is that three places now tell the next author the opposite,** and the shortest route to the defect is to believe them:

1. `Layout.java:5088-5090` - the `asksAbout` javadoc: *"**Asked BEFORE the train is stopped, and it must not block.** `shouldReverse` puts a modal dialog up; this decides whether there is anything to put up."* The second half is no longer true of anything: `asksAbout` decides no stop, it only separates compulsory turns from may-reverse ones at 5214/5220. And `@return whether to stop and ask there` (`Layout.java:5104`) names a job the method no longer has.
2. `ManualReversalPrompt.java:262-267` - the `ask` javadoc: *"**The train is stopped before this is called** ... `executePathInternal` now stops at any reversing point before deciding anything."* False as of `62f845a5`. The sting is three lines further on in the same paragraph: *"A comment asserting the comfortable version of what the code does is how that survived being written and reviewed."* It survived a second time, in the same paragraph that says so.
3. `test/core/testNonReversibleTrains.java:358-359` - the door check accepts `ManualReversalPrompt.forOperator(` or `ManualReversalPrompt.ask(` as a valid way for a door to "hand over a prompt". Handing `forOperator` to `executePath` is now exactly the DIR-A1 reintroduction, and the guard that exists to check the doors would report clean about it. `guard-knows-only-what-it-lists`.

`ManualReversalPrompt.forOperator` (`ManualReversalPrompt.java:181`) is `public static`, is documented as *"the policy both hand-driven doors hand to `executePath`"* - which is no longer what it is; `forJourney` is - and its `shouldReverse` calls `ask`. It is a loaded trap with a sign next to it saying the gun is unloaded.

**The fix is documentation plus one sentence of contract, not code.** Say at `ReversalPolicy` that `shouldReverse` is now evaluated **before** the train is brought to a stand, so an implementation must not block or show UI - the answer has to be decided at departure, which is what `df584d0b` made true and what makes the new gate safe. Correct `ManualReversalPrompt.ask`'s claim to say the caller must have stopped it, and correct `forOperator`'s javadoc to say it is `forJourney`'s input rather than a policy to hand `executePath`. If the trap is to be closed rather than signposted, make `forOperator` package-private or have it return a policy that refuses to answer from outside the EDT.

### CONF2-B2 - the clause that caused `CONF-A1` was added and removed twice today, and nothing goes red either way

`62f845a5` changed behaviour in `ManualReversalPrompt.java:140` and `Layout.java:5876` and added **no test**. Its test-file change is comment rewrites plus the deletion of the `stopsToDecideAt` pin.

Grepping `test/` for `asking.asksAbout`, `turn &&` and `forJourney` returns three hits, all of them comments or the door check at line 358. So:

- Nothing asserts that `forJourney`'s `asksAbout` is **answer-independent**. Re-adding `turn &&` to fix REG6-B4 a third time - which is a natural, well-motivated edit, and is what `8da9416f` did - reinstates the defect silently.
- Nothing exercises `shouldReverseAt` with a policy that **overrides** `asksAbout`. Every behavioural case in `testNonReversibleTrains` (lines 433-524) uses `(t, w) -> true/false` lambdas, which take the **default** `asksAbout` (`at.isReversing()`). The default and `forOperator`'s override disagree at exactly the two squares the whole feature is about: the turning copy of a may-reverse square, and the copy of a compulsory turn. **The entire override path is uncovered**, which is why a defect in it survived a fix, a review and a confirming pass.

That is the shape of the defect too - `assert-the-variable-not-the-control`. The lambdas cannot fail the way the real policy did.

The test that would have caught it needs no railway: build a `ReversalPolicy` whose `asksAbout` returns false for a `reversing` Point and whose `shouldReverse` returns false, and assert `shouldReverseAt` returns **false** - then flip `asksAbout` to true and assert it returns **true**. Both against the split fixture `testNonReversibleTrains` already builds (`plain`/`turning`, lines ~420-445). Mutation to check it fails for the right reason: restore `turn &&` at `ManualReversalPrompt.java:140` and the second assertion goes red.

---

## C - low

| | | |
|---|---|---|
| **CONF2-C1** | `docs/reviews/CONF-confirmation-2026-09-06.md` `CONF-A2` proposes a predicate that no longer exists | Open |

`CONF-confirmation-2026-09-06.md:128` says of `stopsToDecideAt`: *"and that is still the fix"*. `62f845a5` deleted it deliberately, and the reason is good - "a dead predicate with a test pinning it is what ACC4-3 was about". `SPEC-validation-2026-09-06.md:212` shows its signature for the same reason.

Under this folder's rules, review prose is a historical record and does not get edited to match the code, so this is **not** a documentation defect. What is outstanding is the **disposition**: `CONF-A2` should be closed as *superseded*, naming `62f845a5`, or a reader arriving from `audit-the-bodies-not-the-index` will go looking for a method that is not there. That is one line in `CONF-confirmation-2026-09-06.md`'s status table, and it is Adam's to write, not this pass's.

---

## D - checks that came back clean, and one defect that was real

| | | |
|---|---|---|
| **CONF2-D1** | The brief's critical question: **real defect at `8da9416f`**, fixed at `62f845a5` | Open |
| **CONF2-D2** | `CONF-A1` (a) compulsory turn now turns unconditionally - **CONFIRMED FIXED** | Open |
| **CONF2-D3** | `CONF-A1` (b) a may-reverse turning copy is still the operator's to decide - **CONFIRMED FIXED at HEAD** | Open |
| **CONF2-D4** | `CONF-A1` (c) autonomy unaffected - **CONFIRMED FIXED** | Open |
| **CONF2-D5** | `CONF-A1` (d) a default-`asksAbout` policy behaves as before - **CONFIRMED FIXED** | Open |
| **CONF2-D6** | `CONF-A2` the gate and the stop cannot disagree - **SUPERSEDED, property holds more strongly** | Open |
| **CONF2-D7** | `CONF-B1` `facingOf` asks the running layout first - **CONFIRMED FIXED** | Open |
| **CONF2-D8** | `CONF-B5` `testEverySquareOnThisLayoutBuildsToOneCopy` restored - **CONFIRMED FIXED** | Open |
| **CONF2-D9** | `REG6-B3` second half, the resume speed - **CONFIRMED FIXED** | Open |
| **CONF2-D10** | The five previously-confirmed items - **NO REGRESSION** | Open |

### CONF2-D1 - the critical question, answered

**It was a real defect. Unambiguously.** The trace at `8da9416f`, for a hand-driven send whose path touches the turning copy of a may-reverse square, where the operator answers "keep direction":

| step | where | value |
|---|---|---|
| operator answers | `ManualReversalPrompt.java:115` | `turn = false` |
| policy handed to `executePath` | `ManualReversalPrompt.java:117-140` | `asksAbout(at) = turn && asking.asksAbout(at)` = **false for every square** |
| autonomy branch | `Layout.java:5161` | not taken (policy is neither null nor `ALWAYS_REVERSE`) |
| terminus branch | `Layout.java:5181` | not taken (`forJourney:83-91` already returned `KEEP_DIRECTION` for a terminus journey, so a live anon policy means the destination is not one) |
| **compulsory-turn line** | `Layout.java:5214` | `current.isReversing()` **true** (turning copy) `&& !asksAbout` **true** → **returns true** |
| result | | **the train is turned, against the answer** |

`reversals.shouldReverse` - which would have returned `false` - was never reached. The dialog was shown, answered, and overruled by the line that reads the answer's own side effect as evidence about the railway.

Reachable at both call sites: `Layout.java:5877` (intermediate) and `Layout.java:6192` (arrival - `shouldReverseAt(arrived, arrived, ...)`, so the terminus branch does not save it either). Reachable on a real railway, not only in theory: `AutonomyBuilder.java:543-545` emits a plain copy **and** a turning copy for any square marked may-reverse that has grid-side arrivals and onward track, and the turning copy carries `reversing` (`AutonomyBuilder.java:976-978`). `cs2_sample_layout/config/autonomy/configuration-Main.json` carries **5 `canReverse` markings** and 17 `mustReverse`. Not reachable on the test fixture, which is why nothing was red - see `CONF2-B2`.

Severity had it survived: **A**. It is the one outcome the feature exists to prevent, it is silent, and the operator has been told the opposite by a dialog they answered.

**Fixed at `62f845a5`**, `ManualReversalPrompt.java:140`: `return asking.asksAbout(at);`. Re-traced at HEAD with `turn = false` at a may-reverse turning copy: 5214 is `true && !true` → not taken; 5220 is `!mayReverseAt` = false → not taken; falls to `shouldReverse` → **false**. No turn, and - because the stop is now the gate - no stop either. Correct.

The `turn &&` clause was not wrong about REG6-B4; it was wrong about *where*. `62f845a5` moves that suppression into the loop, which is the layer that owns it.

### CONF2-D2 - a compulsory turn never reaches a policy

`AutonomySession.mayTurnTiles():3330-3335` is `reversibleTiles()` minus `mandatoryTurnTiles()`, so `forOperator.asksAbout` (`ManualReversalPrompt.java:222`) is false for a compulsory square by construction. `Layout.java:5214` then returns true before any policy is consulted. Holds for all three live policies: the `forJourney` anon (whose `asksAbout` now delegates straight to `forOperator`'s), `KEEP_DIRECTION` (`asksAbout` always false, so 5214 fires), and a journey where `first == null` (`ManualReversalPrompt.java:113`, which also yields `KEEP_DIRECTION`). And the copy always carries the flag: a mandatory square with split sides emits **only** turning copies (`AutonomyBuilder.java:543` - `!must` gates the plain copy away), and one without them still gets `reversing` (`AutonomyBuilder.java:976-978`).

### CONF2-D3 - a may-reverse turning copy is still a question

At HEAD: `asksAbout` true → 5214 not taken → 5220 not taken (`mayReverseAt` true) → `reversals.shouldReverse` decides. Adam's ruling - "the reason for such a turn lives in the leg after this one" - is honoured for **both** answers now. At `8da9416f` it was honoured only for "yes"; see `CONF2-D1`.

### CONF2-D4 - autonomy is unaffected

`Layout.java:5161` returns `current.isReversing()` for `null` and for `ALWAYS_REVERSE` before any of the new lines. `ALWAYS_REVERSE` is what the four-argument `executePath` overload passes (`Layout.java:5284`), which is autonomy's loop and the timetable that Return Home runs. Line 5214 is unreachable from there. And because the stop is now the gate, autonomy's stop is `current.isReversing()` too - it does not brake at plain copies, which is `SPEC-A2`/`REG6-B3` staying fixed.

### CONF2-D5 - a policy that does not override `asksAbout`

Default is `at != null && at.isReversing()` (`Layout.java:5106-5109`). Three cases, all unchanged from before `8da9416f`:

- turning copy: 5214 is `true && !true` → falls through; 5220 is `!true && ...` → falls through; `shouldReverse` decides. Same as before.
- plain copy of a split square: `isReversing` false so 5214 is skipped; `mayReverseAt` true so 5220 is skipped; `shouldReverse` decides. Same as before - and this is why `mayReverseAt` had to stay beside `asksAbout`.
- an ordinary square: 5220 is `true && true` → returns false. Same as before.

### CONF2-D6 - `CONF-A2`, superseded

`stopsToDecideAt` is gone from the source (one historical mention in a comment at `Layout.java:5863`; no callers, no tests). The question the finding asked - *can the stop and the gate disagree* - is now unaskable rather than answered: `Layout.java:5876-5877` **is** `shouldReverseAt`, so the train is stopped when and only when it is about to be turned.

For the record, the `8da9416f` predicate did also satisfy the brief's test. Each of `shouldReverseAt`'s four return-true paths was covered by the old stop: 5161 and 5181 both require `isReversing`, which the stop's first disjunct held; 5214 requires `isReversing` likewise; and the fall-through at 5222 is only reached when `mayReverseAt || asksAbout`, both of which the stop listed. It was correct **and** strictly wider - which is how it came to reinstate `REG6-B4`'s pointless braking (`mayReverseAt` is square-wide, so a "keep direction" journey stopped at every copy of every may-reverse square regardless). That is a second, independent reason `62f845a5` is the better arrangement, and it is not a finding here because the code it describes no longer exists.

### CONF2-D7 - `CONF-B1`

`AutonomySession.facingOf(String, Layout)` (`AutonomySession.java:4853`) walks `running.getPoints()` for the named locomotive first, maps the Point to a square via `getStationIndex().squareOf(...)`, and only then falls back to `placedLocomotives()`. Both call sites pass a layout: `TrainControlUI.java:5989-5990` passes `this.model.getAutoLayout()` (null-guarded), `AutonomyEditorPanel.java:3930-3931` passes the local `layout` from `layoutSource.get()`, which the method has already returned early on if null. The single-argument overload (`AutonomySession.java:4832`) has **no callers** anywhere in `src/` or `test/` - it delegates with `null` and exists only as the old signature. That is a small trap of the `CONF2-B1` kind but not worth a finding: the fallback it selects is the pre-fix behaviour, not a wrong one, and there is nothing to sweep.

### CONF2-D8 - `CONF-B5`

`test/core/testAPastedTrainKeepsItsDirection.java:274` is present, and it asserts what it claims: it walks every destination Point, maps each to a square, collects `facingsFor(square)`, and asserts `split.isEmpty()`. It also carries the floor `assertTrue(named >= 2, "no named square was reached, so this measured nothing")` - which is the precondition without which the test passes by measuring nothing (`test-green-is-not-no-failures`). Its failure message tells the reader to go back and re-read the reachability caveats in SPEC/REG6/ACC4, which is the job it was restored for.

### CONF2-D9 - `REG6-B3` second half

`Layout.java:5896-5897`:

```
int resume = Math.min((int) Math.ceil(
    (double) speed * current.getSpeedMultiplier()), 100);
```

The normal speed-setting site, `Layout.java:5779-5780`:

```
int calculatedSpeed = (int) Math.ceil((double) speed * current.getSpeedMultiplier());
calculatedSpeed = Math.min(calculatedSpeed, 100);
```

Identical: same `speed`, same `current`, same `Math.ceil`, same cap, written as one expression instead of two statements. Both sites are inside the same `if (i != path.size() - 1)` branch and read the same `current`, so there is no chance of them referring to different Points. The cap is load-bearing, not decorative - `Point.setSpeedMultiplier` accepts up to `2` (`Point.java:159`), so `speed * multiplier` genuinely can exceed 100.

### CONF2-D10 - the five regression re-checks

- **REG6-A1** - `ManualReversalPrompt.forOperator.asksAbout` (`ManualReversalPrompt.java:191-222`) contains no `isReversing()`; it answers `session.mayTurnTiles().contains(squareOf(at.getName()))` and nothing else, returning false when `session` or the station index is null. `testACompulsoryTurnIsNotAQuestion:156` pins the absence as source. **No regression.**
- **ACC4-1** - `reverseFor` (`ManualReversalPrompt.java:249-252`) is `return chose == NO;` with `NO = 1`. Tests the answer, not its opposite, so `CLOSED_OPTION` (-1), Escape, the X and a dialog that could not be shown all leave the train alone. `ManualReversalPrompt.java:327` is the single call site. **No regression.**
- **SPEC-B1** - `forJourney` (`ManualReversalPrompt.java:83-91`) returns `KEEP_DIRECTION` when the last edge's end is a terminus, before asking anything, and skips terminus ends in the scan at line 101. `Layout.java:5181` answers such journeys from the flag. Prompt and rule agree; no dialog whose answer is discarded. **No regression.**
- **REG6-A2** - `flipFacing` (`AutonomySession.java:1498-1540`) collects candidates from `running.getPoints()` first and then walks `placedLocomotives()` in full, so DIR-C3's "decide the square that can be decided" survives. `TrainControlUI.java:9961` passes `this.model.getAutoLayout()`. **No regression.**
- **SPEC-A1** - `facingAfterAPaste` (`AutonomySession.java:4804-4823`) keeps its ordering: empty → null; **one copy → that copy's side** (which is the terminus reversal); then heading-survives; then null rather than the arbitrary landing copy. Both call sites (`TrainControlUI.java:6071`, `AutonomyEditorPanel.java:3949`) read the heading **before** the move and hand it in. **No regression.**

---

## What this pass would have missed

Recorded for calibration, since the folder's rules ask for it.

Had `62f845a5` not landed, this document would have reported `CONF-A1` as a **new A** with the fix at `ManualReversalPrompt.java:140` - the same line Adam changed, for the same reason, from the same trace. It would **not** have proposed moving the stop into the gate, and would have carried `REG6-B4`'s reinstated braking as a separate B against `stopsToDecideAt`. Adam's answer is one change where this pass had two, and it deletes a predicate instead of adding a caveat to it.

What this pass adds that the commit did not: the contract that moved with the gate (`CONF2-B1`) and the fact that the whole overridden-`asksAbout` path has never been executed by a test (`CONF2-B2`). Both are the same shape as the defect itself - the second one is why it existed.
