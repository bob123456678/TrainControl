# Sixth validation, 2026-09-02: the fixes made in answer to the fourth pass

**Status:** open

**Prefix:** `V36`. Cite findings from here as `V36-A1`, `V36-B2`, `V36-D3` and so on. `V3`, `V31`,
`V32`, `V33`, `V34`, `V35`, `V2`, `FV2`, `SV2`, `TV2`, `VAL` and `VB` are taken by earlier passes;
nothing in `docs/` declares `V36`.

| | |
|---|---|
| **Reviewed** | branch `autonomy-diagram-r0`, v3.0.0 |
| **HEAD at the start and end of this pass** | `6507ee63` — "Validation round 2, and the five-train test stops blaming the planner for the diagram" |
| **Scope** | that commit and nothing else — four files, 89 insertions, 35 deletions. It answers `V34-B1`, `V34-C1`, `V34-C2` and a battery failure in `testTrainsComeHomeToTheirPlatforms`. |
| **Not in scope** | the validation documents themselves, the findings they raised, and everything under `3c014e77`. |

**Method.** Reading only. No test, `battery.sh`, `one.sh`, `ant`, `javac`, `java` or TestNG invocation
was made — a battery was running throughout this pass. Every claim below comes from reading the changed
code, the production method each test exercises, and tracing what the mutation each fix names would
change. Where a conclusion could only be settled by execution, it says so. Facts about the operator's
railway were measured against the frozen copy under `test/operator_layout/config/`;
`cs2_sample_layout/` was neither read nor written. Only this document was created.

---

## Summary

**One A.** Three of the four halves of this commit are sound in what they do: the strip really does stop
reading a dead count, the display number really does arrive from `setFindings`' arguments, and `V34-C2`
is properly fixed. The fourth — the branch added to the five-train test — is built on a predicate that
answers a different question from the one the test needs, and its assertion joins two facts that have no
implication between them. It can turn a correct plan red and, worse, it can absorb the exact planner
defect the test exists to find and report PASS.

`V34-C1`'s fix replaced a tautology with a precondition whose direction is the reverse of what its own
message claims, and which the fixture cannot fail; the confound `V34-C1` named is now unguarded.

| | Finding | Severity | Disposition |
|---|---|---|---|
| A1 | `canReachAnyDestination` is autonomy's dispatch question, not Return Home's. The `trapped` screen has false positives in both flag directions, and `assertFalse(plan.isPossible())` joins two independent facts — so a short planner passes and a correct plan can fail | A | Open |
| B1 | The trapped branch `return`s past the class's headline assertion on a random condition, with no floor, no counter and a green result — in a test whose own javadoc says it is meant to be RED | B | Open |
| B2 | `testHomeStaging`'s new precondition is stated backwards and cannot fail; the `mustBackIn` confound is now guarded only by `setReversible(true)`, which is the line `V34-C1` objected to | B | Open |
| C1 | `TrainControlUI.autonomyErrorCount()`'s javadoc still says "the strip and the right-click tooltip need the NUMBER". The strip does not, as of this commit — the sibling of the comment `V34-B1` was raised for, not swept | C | Open |
| C2 | `AutonomyOverlayToggle.syncRun` now carries two stacked headings, the first of which still describes the statement this commit deleted; and `setFindings`' comment still says "the run button turns on the error count" | C | Open |
| C3 | `V34-C6` is untouched, and is now the *only* rule holding the strip: one whole-file `contains` over raw source, beside a sibling in the same method that is body-extracted and comment-stripped | C | Open |
| C4 | The new comment claims `canReachAnyDestination` "is what the editor's new copy check reports on". It is a second, independently written predicate that disagrees with `AutonomySession.destinationCopiesReachingNoStation()` in both directions — so `MT-253`'s "the only thing on your whole layout" does not bound how often the branch fires | C | Open |
| C5 | `LD-C6`'s cost at this door is reduced from three walks of `check()` to two, not closed; the comment reads as though it were | C | Open |
| C6 | `V34-B1`, `V34-C1` and `V34-C2` are still marked **Open** in their own document. `V34-C8` said exactly this of the previous commit; this is the third commit in a row to answer findings without dispositioning them | C | Open |
| D1..D9 | Nine checks that came back clean, each with the mutation or the alternative explanation traced | D | Closed |

---

## A — high

| | Finding | Status |
|---|---|---|
| **V36-A1** | The trapped screen asks autonomy's question, and its assertion proves nothing about the planner | open |

### V36-A1 — `canReachAnyDestination` is not the question this test needs, and the assertion does not follow

**FIXED 2026-09-02 (`609a57b1`).**  The branch is out, and the reason is recorded where it was.  All three limbs of the finding hold: the predicate is the full-autonomy one and requires `isAutoDestination`, which Return Home ignores; a trapped train and an impossible plan are independent facts, so the assertion did not follow; and the early return skipped the facing check the class exists for.  A test that absorbs its own failures reads as green while proving nothing.

`test/core/testTrainsComeHomeToTheirPlatforms.java:249-272`:

```java
            for (String name : STARTED_AT.keySet())
            {
                Point standing = layout.getLocomotiveLocation(model.getLocByName(name));

                if (standing != null && !layout.canReachAnyDestination(standing))
                {
                    trapped.add(name + " on " + standing.getName());
                }
            }

            if (!trapped.isEmpty())
            {
                assertFalse(plan.isPossible(), ...);
                ...
                return;
            }
```

**One: the predicate is autonomy's, and Return Home is documented as not subject to it.**

`Layout.canReachAnyDestination` (`src/org/traincontrol/automation/Layout.java:6331-6367`) accepts a
reached square only when

```java
                if (!end.equals(from) && end.isDestination() && end.isActive()
                    && end.isAutoDestination() && !end.isReversing())
```

`isAutoDestination()` is the flag whose own setter javadoc says the opposite of what this branch needs
(`src/org/traincontrol/automation/Point.java:191-194`):

> `@param status false to keep autonomy from choosing this station of its own accord. Routes the user
> picks, and Return Home, are unaffected.`

`AutonomyBuilder` says the same about the squares it emits with the flag off
(`src/org/traincontrol/automationui/AutonomyBuilder.java:207-209`):

> `Stations autonomy may not choose for itself. They are emitted with autoDestination false and are
> otherwise ordinary: a route the user picks reaches them, Return Home fills them, and trains may run
> through them if the track allows.`

And the planner agrees with the javadoc rather than with the screen. `HomeStaging.snapshot`
(`src/org/traincontrol/automation/HomeStaging.java:133`) builds the list of squares a move may end on as

```java
            if (p.isDestination() && p.isActive()) stations.add(p);
```

— no `isAutoDestination`, no `isReversing` exclusion. `canRest`
(`HomeStaging.java:1673-1691`) adds only exclusions and train length. So every square the planner may
use that autonomy may not is invisible to `canReachAnyDestination`, and a train whose only onward
stations are berths is called *trapped* by this test while Return Home can move it perfectly well.
`test/core/testAutoLayout.java:1301-1322` — `testParkingDoesNotMakeACopyPlaceable` — pins exactly that
reading of the method: *"a berth is somewhere autonomy will not send a train, so it is not somewhere to
go."*

This is not hypothetical on the frozen fixture. `test/operator_layout/config/autonomy/configuration-Main.json`
carries **20** `"autoDestination": false` entries and 12 `"parking"` entries, against 33 squares with a
`maxTrainLength` recorded. `Point.toJSON` writes the flag only when it is false
(`Point.java:1023-1025`), so all 20 are genuinely off.

**Two: `trapped` non-empty and `!plan.isPossible()` are two facts with no implication between them.**

The branch never checks that the trapped train is why the plan failed, and there are two ways for the
implication to break.

*A trapped train that is already home needs no move at all.* `HomeStaging.plan` skips it before the
unreachability scan (`HomeStaging.java:396`):

```java
            if (home == null || atHome(home, locationOf(this.start, l))) continue;
```

and `misplaced` does not count it (`HomeStaging.java:1916-1928`). So a train standing on a dead copy
that happens to be its own home contributes nothing to the plan, the other four are planned normally,
`plan.isPossible()` is true — and `assertFalse` fails, with a message accusing the planner of
*"a plan for an arrangement that cannot have one"*. That is a correct plan turned red. Note that the
placement door does not rule this out: `place()` and the fixture floor
(`testTrainsComeHomeToTheirPlatforms.java:191-204`, `723-740`) require a copy to be
`isDestination() && isActive() && !isTerminus()`, and never ask whether it reaches anywhere.

*And in the other direction — the one that matters more — a genuinely short planner passes.* Suppose the
planner has a real defect and answers `NO_PLAN_FOUND`, and, independently, one of the five happens to
stand somewhere `canReachAnyDestination` calls dead. `trapped` is non-empty, `assertFalse` passes, the
method returns, and TestNG reports PASS with a println blaming the diagram. The defect the test exists
to find is absorbed and misattributed. The class's own javadoc says this test is *"expected to be RED
until Return Home stages this"* (lines 47-50) — a branch that converts that red into a green is the
failure mode the whole file is written against.

**Three: the message in the surviving branch overclaims.** Lines 274-278:

> `"... Every train is standing somewhere that CAN reach another station - checked above - so this is
> the planner being short rather than the railway ..."`

Reaching *another station* is not reaching *its own home*. `canReachAnyDestination` says nothing about
the home square, so the else branch's failure can still be the railway. The sentence will send the next
reader into `HomeStaging` for a fault that is not there.

**The fix is cheap and the material is already in the method.** `HomeStaging` distinguishes the two
answers on purpose (`HomeStaging.java:221-239`, `578-584`): `IMPOSSIBLE` is a proof and carries the
locomotives it proved it about; `NO_PLAN_FOUND` carries an empty list. The branch should assert
`plan.getOutcome() == Outcome.IMPOSSIBLE` **and** that each name in `trapped` appears in
`plan.getBlocked()` — the field the else branch already prints. Then a planner that failed for any other
reason goes red, which is what the class is for. The screen itself should ask the planner's question
(`isDestination() && isActive()`, over every copy of the train's own home) rather than autonomy's.

---

## B — medium

| | Finding | Status |
|---|---|---|
| **V36-B1** | The trapped branch returns past the headline assertion, with no floor and no counter | open |
| **V36-B2** | `testHomeStaging`'s replacement precondition is inverted, and cannot fail | open |

### V36-B1 — a random early return, and nothing measures how often it fires

**FIXED 2026-09-02 (`609a57b1`).**  Removed with the branch above - it was the same early return. The flake it was hiding is measured instead and left red: 1 failure in 5, three in eight observations across the day.

`testTrainsComeHomeToTheirPlatforms.java:271` `return`s out of
`testEveryoneComesBackFacingTheWayTheySetOff` before lines 298-330 — the block that checks every train
came back to the same arrival Point, which is what the class is named for and what its 47 lines of
javadoc are about. Everything after the `return` is skipped: the timetable load, `executeTimetable`, and
the facing comparison.

The condition is not deterministic. The arrangement comes from twenty seconds of autonomy running the
railway (lines 212-218), and the commit message says so approvingly — *"the arrangement stays random;
what it means stops being."* But what the arrangement now decides is **whether the test asserts anything
at all**, and there is no floor, no counter, and no failure when the interesting half never runs. That
is the rule `docs/reviews/README.md:238-240` states — *"Property tests need a floor on how much they
exercised… or the suite can quietly degenerate into testing nothing while still passing"* — applied to a
test that is already registered in `build.xml` and already expected to be red.

The comment defends the branch against the alternative it considered — *"Written as a branch rather than
a skip. A skip here would hide the arrangement"* — and it is right about that: a `SkipException` would be
worse, and the println is better than silence. What it does not consider is the third option, which is
to keep the branch and still fail: assert the outcome and the blocked list (see `V36-A1`), and count how
many runs reached the real assertion.

How often the branch fires cannot be settled by reading, and the commit's own evidence does not bound it
— see `V36-C4`.

### V36-B2 — the precondition says the opposite of what it asserts, and the confound is now unguarded

**FIXED 2026-09-02 (`8c4c4aa4`), a round later than it looked.**  `609a57b1` answered it with a precondition pointing at "the CONTROL at the end of this test", and that control is in a different test three thousand lines away on a different fixture - which `V37-B2` caught.  The audit test has a control of its own now: a three-unit train that fits, same test, same fixture.  Mutation-confirmed.

`test/core/testHomeStaging.java:198-206`:

```java
        // Nothing here restates `setReversible(true)` back at itself, which is what the helper this
        // replaced did ... What the fixture has to be true is that the RAILWAY offers no way to arrive
        // already turned, because if it did, mustBackIn would be satisfied and the refusal below would
        // not be about room.
        assertTrue(reversingPointsOn(layout, layout.getPoint("HS D")).isEmpty(),
            "precondition: the fixture has a reversing point on the APPROACH, so a non-reversible "
            + "train could arrive at the terminus already turned and mustBackIn would not be the "
            + "alternative explanation this is ruling out.  Found: " ...);
```

`mustBackIn` does not look at the layout at all (`HomeStaging.java:1585-1588`):

```java
        return at != null && at.isTerminus() && loc != null && !loc.isReversible();
```

The reversing points matter only to `connected(from, to, mustTurn)`, which is asked *after* `mustBackIn`
has already returned true (`HomeStaging.java:1611`, `1622`, `1626`). So the implication runs the other
way from the one written down:

- **With** a reversing point on the way, a non-reversible train can be turned, `connected(..., true)`
  succeeds, and `mustBackIn` is *not* the reason for any refusal.
- **With none** — which is what this assertion demands — a non-reversible train is refused a terminus by
  `mustBackIn` whatever the room rule says. That is the confound, not its absence.

The fixture comment nine lines above already states it correctly (lines 183-188): *"there is no reversing
point anywhere on this ring, so the planner refuses HS D for that reason whatever it knows about
length."* The new precondition asserts that same condition and calls it the thing that rules the
confound out. So does the helper's javadoc (`testHomeStaging.java:280-284`).

**What it costs.** The only thing keeping `mustBackIn` out of this fixture is `setReversible(true)` on
line 189 — exactly the statement `V34-C1` objected to being restated, now with nothing standing for it.
Delete or invert that line and the test still passes green while proving nothing about room:

- The runtime side of the control (line 220) still refuses, and for the right reason: `isPathClear` no
  longer carries a terminus rule at all (`Layout.java:2312-2330`, *"NO TERMINUS RULE HERE"*), so its
  refusal is the length rule alone.
- But the subject of the test is the **audit**, line 230: `auditAgainstRuntime() == 0`. That compares
  what `getPossiblePaths` offers against what `firstClearRoute` offers
  (`HomeStaging.java:602-631`). With a non-reversible locomotive, `pickPath`'s own filter drops the
  terminus (`Layout.java:3532`, `3790`) and the planner drops it through `mustBackIn` — the two agree,
  the audit is 0, and the assertion passes **whether or not the planner has the room rule**. The
  `assertFalse(planReturnToHome().isPossible())` on line 237 passes for the same reason.

`V34-C1` recorded *"It does not weaken the test."* That was true of the tautology. It is not true of
what replaced it.

**Two smaller things in the same helper** (`testHomeStaging.java:289-304`). It counts `p.isTerminus()`
as a place a train "may turn round", but a route never passes through a terminus — the file's own
diagnostic states the rule (`testTrainsComeHomeToTheirPlatforms.java:511-513`, *"a terminus is never
expanded THROUGH"*, enforced at line 547) — so a second, unrelated terminus added to the ring would fail
a precondition it has nothing
to do with. And the javadoc has no `@param destination` for the parameter that was added to it, while its
`@return` still reads *"empty when nothing turns round anywhere"* for a method that skips the
destination.

---

## C — low

| | Finding | Status |
|---|---|---|
| **V36-C1** | `autonomyErrorCount()`'s javadoc still names the strip as a reader | open |
| **V36-C2** | Two stacked headings in `syncRun`, the first describing the deleted statement | open |
| **V36-C3** | `V34-C6` untouched, and now the strip's only rule | open |
| **V36-C4** | The comment equates two different "reaches nothing" predicates | open |
| **V36-C5** | `LD-C6` at this door is reduced, not closed | open |
| **V36-C6** | The findings this commit answers are still open in their own document | open |

### V36-C1 — the sibling comment was not swept

**FIXED 2026-09-03.**  The sibling sentence now says what is true: the count stays for the right-click
tooltip, which needs the number, and the strip does not read it - its number comes from `setFindings`'
own arguments.  `TrainControlUI.autonomyHasErrors()`'s javadoc.

`V34-B1` was raised because a comment said the count was still read where it was not. The count read has
gone; the *other* comment saying the same thing has not. `src/org/traincontrol/gui/TrainControlUI.java:20187-20188`,
in `autonomyHasErrors()`'s javadoc:

> `The count stays where it is: the strip and the right-click tooltip need the NUMBER to say what is
> wrong. What must not differ is which question DECIDES.`

As of this commit the strip does not need the number and does not read it. The right-click tooltip still
does (`LayoutRightclickAutonomyMenu.java:203`), so half the sentence is right, which is what makes it the
kind that survives a reading. This is `docs/reviews/README.md:184-190` — *"When you fix a call site, grep
for its twins before closing the finding"* — at the shortest possible distance: one `grep` for
`autonomyErrorCount` returns both sites.

### V36-C2 — the replaced comment was added beside the one it replaces, not instead of it

**FIXED 2026-09-03.**  The `AU-C1` block described the `int errors` statement the commit deleted, and
ran without a break into the heading that corrects it.  There is one heading now, citing all four
findings, and the only sentence of the old block that still describes live code - the fallback to
`lastTotalErrors` when there is no window to ask - has moved down beside the line that does it.

`src/org/traincontrol/gui/AutonomyOverlayToggle.java:332-355`. Two headings now run together with no
blank line between them:

```java
        // The GUARD'S own number, not this strip's cached copy (AU-C1).
        //
        // `lastTotalErrors` is whatever the last setFindings call left behind ...
        //
        // Falls back to the cached number only when there is no window to ask - which is a strip built
        // before its owner, not a running application.
        // THE GUARD'S OWN QUESTION, and only that (V31-B1, V32-B1, then V34-B1).
```

The first block is the `AU-C1` note written for the `int errors = ...` statement this commit deleted; it
still says "number" of a `boolean`, and it runs straight into the new heading that corrects it. The
fallback sentence is the only line of it that still describes live code (`lastTotalErrors > 0` on line
355), and it now sits under a heading about something else.

Same file, `setFindings` at lines 486-489: *"The run button turns on the error count, so it is
re-decided here rather than only when a button's enabled state changes."* It turns on
`autonomyHasErrors()` now — that was `V31-B1`'s fix and this commit is the one that removed the last
reason the sentence was half true.

### V36-C3 — the surviving assertion is the weak one, and is now alone

**FIXED 2026-09-03**, including the residual this finding said was never pinned.

The assertion reads the body rather than the file, which `8c4c4aa4` did (see `V37-C1` for the reason
that came with it, which was wrong and has been corrected).  What this finding said remained unpinned -
*"that the answer reaches `fixing` at all"* - is now a rule: the decision must come after the question,
and `fixing`'s own expression must read the variable the guard's answer went into.

Mutation-confirmed by `fixing = source != null && source == start && lastTotalErrors > 0;`, which asks
`autonomyHasErrors()` and throws the answer away.  Every rule that existed before passed it.

`test/regression/testErrorsStopTheSetupRunning.java:243-246` is the only rule left holding the strip to
the guard's question, and it reads the raw file:

```java
        String toggle = read("src/org/traincontrol/gui/AutonomyOverlayToggle.java");
        ...
        assertTrue(toggle.contains("autonomyHasErrors()"), ...);
```

`read` (lines 256-260) does not strip comments and does not extract a body, while its sibling twenty
lines above uses `withoutComments(bodyOf(ui, "public boolean canStartAutonomy()"))` — both helpers are in
this class (lines 268-319) and `testTheStripAsksThatQuestion` at line 490 uses one of them. `V34-C6`
raised this and rated it C on the grounds that *"a plain revert is still caught, because
`autonomyHasErrors()` appears nowhere else in that file today."* That is still true — line 355 is the
only occurrence, and the new comment says `hasErrors()` rather than `autonomyHasErrors()`, so it does not
satisfy the `contains`. The rating stands; what has changed is that the commit rewrote these exact lines
and left the shape alone, and the assertion it deleted beside it was the other half of the pair.

**On the parent question — has the removal weakened the rule?** No, not by itself: the deleted assertion
pinned a dead statement and nothing about `fixing` depended on it. What is unpinned, and always was, is
that the answer reaches `fixing` at all — `boolean broken = ui.autonomyHasErrors(); fixing = source != null
&& source == start && errors > 0;` would still pass. That is `V34-C6`, unchanged.

### V36-C4 — the two "reaches nothing" checks are different predicates

**OPEN, and worth Adam's ruling rather than a quiet change** (2026-09-03).

The comment half is gone - the sentence quoted here is no longer in
`testTrainsComeHomeToTheirPlatforms`, which was rewritten around it.  The substance is confirmed and
stands: `AutonomySession.destinationCopiesReachingNoStation` counts a reached square as somewhere to go
on `station` alone, while `Layout.canReachAnyDestination` requires `isDestination() && isActive() &&
isAutoDestination() && !isReversing()`.  The editor's warning is the LOOSER of the two, so it under-reports:
a copy the runtime considers dead can pass the editor in silence.

**Why it is not simply corrected here.**  Matching the runtime makes the warning fire on more squares,
and `MT-253` records that it fires on exactly one square of Adam's railway today - RampDown southbound.
Twenty of his stations carry `autoDestination` false.  Tightening it a week before a release could put a
warning on a large part of his layout, and whether those are faults or ordinary is his call, not mine.
Filed for after 3.0.0 rather than fixed in it.

`testTrainsComeHomeToTheirPlatforms.java:244-245`:

> `` `canReachAnyDestination` is the model's own question and is what the editor's new copy check reports
> on; MT-253 asks Adam about the one it names on his layout today.``

The editor's check is `AutonomySession.destinationCopiesReachingNoStation()`
(`src/org/traincontrol/automationui/AutonomySession.java:2101-2160`), a separate walk over the built
JSON. It counts a reached square as somewhere to go when `p.optBoolean("station")` — nothing about
`active`, `autoDestination` or `reversing` — and it excludes copies of the *same square*.
`Layout.canReachAnyDestination` does the reverse on both counts. They are two hand-written copies of one
rule that disagree in both directions, in the file whose subject is a planner drifting from a hand copy
of the runtime's rules.

This matters beyond the comment. `MT-253` records that RampDown southbound is *"the only thing the new
warning reports on your whole layout"* — and that is the editor's check, which is the **looser** of the
two on flags. It therefore places no bound on how many copies `canReachAnyDestination` calls dead, and so
no bound on how often `V36-B1`'s early return fires. The one measurement the branch leans on does not
measure the branch.

### V36-C5 — the second walk is gone; the first two are not

**FIXED 2026-09-03** (the comment; the door stays open, and now says so).

The saving the commit claims is real.  What read as closed - *"that is `LD-C6`'s cost at the neighbouring
door"* - now says the door is not closed: the window walks `check()` itself to count findings and hands
the counts to `setFindings`, which calls back into `syncRun`, so a refresh is two walks rather than
three.  The comment also names what would close it (caching `check()` on the session) and why the strip
cannot simply be handed the boolean (`hasErrors()` is wider than `errors > 0`, which is `V31-B1`).

The commit's cost argument is right about what it removed: `autonomyErrorCount()` →
`AutonomySession.errorCount()` walks `check()` unconditionally (`AutonomySession.java:3557-3567`), while
`autonomyHasErrors()` → `hasErrors()` short-circuits on `hasBlockingProblems()` first
(`AutonomySession.java:3583-3586`), so on a broken graph the deleted line was the *only* walk. That is a
real saving.

What the comment then claims — *"That is `LD-C6`'s cost at the neighbouring door"* — reads as closed, and
the door is still open. `TrainControlUI` walks `session.check()` itself to count the findings
(`TrainControlUI.java:5772`), computes `errors` and `warnings` in that loop, hands them to `setFindings`
(line 5816) — and `setFindings` immediately calls `syncRun()` (`AutonomyOverlayToggle.java:490`), which
walks `check()` again through `autonomyHasErrors()`. Three walks became two, on the event thread, per
refresh. The strip cannot simply be handed the boolean, because `hasErrors()` is wider than
`errors > 0` — that width is `V31-B1` — but a cached `check()` on the session, or passing both, would
close it. Worth one clause in the comment rather than a claim.

### V36-C6 — nothing was dispositioned, for the third commit running

**FIXED 2026-09-03.**  This C-round sweep is the answer: every finding in this document now carries a
verdict where the reader looks for one, and the same is being done to the rest of the open reviews.

The commit touches four source files and no document. `docs/reviews/2026-09-02-fourth-validation.md:39-41`
still shows `V34-B1`, `V34-C1` and `V34-C2` as **Open**, and all three are answered here — two of them
correctly. `V34-C8` made this finding about the commit before it, and `V32-C6` about the one before that.
A reader of `V34` today cannot tell which of its ten findings still need somebody, which is the exact
harm `README.md:119-122` describes under *"One status, one location."*

---

## D — checked and sound

| | What was checked | Result |
|---|---|---|
| **V36-D1** | Nothing else needed the count | clean |
| **V36-D2** | The displayed number still arrives correctly | clean |
| **V36-D3** | `fixing` and the band's colour are unchanged | clean |
| **V36-D4** | The menu's own count read survives, and its assertion is right | clean |
| **V36-D5** | `V34-C2` is properly fixed | clean |
| **V36-D6** | The audit test's other three preconditions still discriminate | clean |
| **V36-D7** | `p.equals(destination)` really does exclude `HS D` | clean |
| **V36-D8** | The trapped loop's own mechanics | clean |
| **V36-D9** | The frozen fixture is still the only layout this suite opens | clean |

**V36-D1 — nothing else in the class or its callers needed `autonomyErrorCount()`.** The deleted local
was read nowhere: had it been, the deletion would not compile. The only other error state in the class is
`lastTotalErrors`, which is still written by `setFindings` (`AutonomyOverlayToggle.java:483`) and still
read twice — as the no-window fallback for `broken` (line 355) and when `setBannerShowing` replays the
last findings (line 684). Every caller was checked: `bindRunButtons` (287), `setFindings` (490),
`setLoaded` (567), `setPageExcluded` (606), and `TrainControlUI.java:3517`, `3526`. None reads a count.

**V36-D2 — the display number does come from `setFindings`' arguments.** `TrainControlUI.java:5764-5817`
walks `session.check()` and accumulates `errors`, `warnings`, `pageErrors`, `pageWarnings` itself, then
passes all four; `setFindings` colours and formats from those parameters alone
(`AutonomyOverlayToggle.java:518-534`). The zero case is handed over explicitly at line 5760 rather than
left stale. The commit's claim is accurate.

**V36-D3 — `fixing` and the band are unaffected.** The `broken` expression on line 355 is byte-identical
to what it was before this commit; only the comment above it and the dead statement changed. `paintState`
(lines 643-659) reads `excluded` and `fixing` and nothing else, so the band's colour follows `fixing`
exactly as the new comment says. The strip's ordering is also intact: `setFindings` writes
`lastTotalErrors` *before* calling `syncRun`, so the no-window fallback sees the fresh value.

**V36-D4 — the menu was left alone, and rightly.** `LayoutRightclickAutonomyMenu.java:194-208` still asks
`ui.canStartAutonomy()` for the decision and `ui.autonomyErrorCount()` only for the tooltip's number,
which is the split `TS3-B6` established. The surviving assertion at
`testErrorsStopTheSetupRunning.java:248-250` is therefore still true of a real reader, unlike the one
removed.

**V36-D5 — `V34-C2` is fixed.** `testHomeStaging.java:249-254` now shows *"The ring, with HS D a terminus
reached over a short measured edge … `@return the configuration`"* immediately above `shortBerth()` at
line 254, and the new helper at lines 279-288 carries a javadoc of its own. The orphaned `@return` is
gone.

**V36-D6 — the audit test's other preconditions still do work.** The edge-length check (194-196), the
terminus flag (208-209), the measured-and-shorter-than-40 check (211-217) and the `isPathClear` control
(220-223) are all unchanged and all bear on the fixture rather than on a setter. The control in
particular is stronger than it looks: since `Layout.java:2312-2330` removed the terminus rule from
`isPathClear`, its refusal can only be the length rule. `V36-B2` is about the audit assertion, not about
these.

**V36-D7 — the helper's exclusion works.** `Point.equals` compares names
(`Point.java:388-396`), so `p.equals(destination)` excludes `HS D` whichever object `getPoint` returned.
On today's `shortBerth()` fixture `HS A`, `HS B` and `HS C` are plain stations, so the list is empty and
the precondition passes — which is `V36-B2`'s point: it cannot fail on the fixture it guards.

**V36-D8 — the loop itself is written correctly.** `getLocomotiveLocation` is the model's own accessor,
`standing != null` is guarded, and the branch prints the arrangement rather than throwing
`SkipException` — the comment defending that choice against a skip is right, and a skip would have been
worse. `plan` is computed before the loop and not recomputed inside it. The `return` sits after
`stopLocomotives()`/`awaitStopped()`, so nothing is left driving; `@AfterClass` still closes the sandbox.

**V36-D9 — the fixture discipline is intact.** The suite opens `test/operator_layout` through
`support.LayoutSandbox` (lines 113-121) and nothing in this commit reaches for `cs2_sample_layout`.

---

## What this pass did not settle

- **How often `V36-B1`'s branch actually fires.** That needs a run, and no JVM was started. `MT-253`
  cannot answer it for the reason given in `V36-C4`.
- **Whether any of the five platforms, or the copies reachable from them, carry `autoDestination:false`
  on the frozen diagram.** The config is keyed by tile, not by station name
  (`configuration-Main.json`), and resolving the 20 flagged tiles to names needs the builder to run.
  That decides whether `V36-A1`'s false-positive path is live today or only after the next refresh of
  the fixture; the unsoundness is there either way.
- **Whether the tests pass.** Nothing was executed. `V36-B2`'s claim that the audit test would stay green
  with a non-reversible locomotive is traced through `pickPath`'s filter and `mustBackIn`, not measured.
