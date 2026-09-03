# Fourth validation pass, 2026-09-02: the fixes made in answer to the first three

**Status:** open

**Prefix for citing this document elsewhere: `V34`.** Cite findings from here as `V34-B1`, `V34-C3`,
`V34-D5` and so on. Nothing in `docs/reviews/` or `docs/` declares `V34`; `V3`, `V31`, `V32`, `V33`,
`V2`, `FV2`, `SV2`, `TV2`, `VAL` and `VB` are all taken by earlier passes.

| | |
|---|---|
| **Reviewed** | branch `autonomy-diagram-r0`, v3.0.0 |
| **HEAD at the start and end of this pass** | `3c014e77` — "Validation round 1: an A in the runner, the affordance sweep's last site, and four wrong claims" |
| **Scope** | that commit and nothing else — nine files, 205 insertions. It is the fixes made in answer to `V31`, `V32` and `V33`, and it is the least-reviewed code in the repository. |
| **Not in scope** | the three validation documents themselves, the findings they raised, and everything under `2e83b737`. |

**Method, and its one limitation.** No test was run and no JVM was started: a battery was running
during this pass, and two runs redirecting the Java Preferences store at once is how the operator's
real railway was damaged on 2026-08-30. Every claim below is from reading the changed code, reading
the production method each test exercises, and tracing what the mutation the fix names would change.
Where a conclusion could only be settled by execution, it says so.

One thing was measured rather than reasoned about: TestNG 6.14.3's summary format was read out of the
constant pools of `org/testng/SuiteRunnerWorker.class` and `org/testng/reporters/TextReporter.class`
in `resources_test/testng-6.14.3.jar`, with `unzip` and a byte scan. No JVM was involved.

`cs2_sample_layout/` was not read, written or checked out. Only this document was edited.

---

## Summary

**No A.** The four behaviour-bearing halves of this commit — the runner's classification, the strip's
predicate, the surface rule's new body check, and the staging control — are all correct, and the four
corrected claims are all true where they replaced something false. What is wrong is one discarded call
that two places now assert is load-bearing, one finding answered with a rename, and six smaller things.

| | Finding | Severity | Disposition |
|---|---|---|---|
| B1 | `AutonomyOverlayToggle.syncRun` keeps an `autonomyErrorCount()` call whose result nothing reads; the comment beside it and the new surface rule both say it is needed, and the strip now walks the uncached `check()` twice per sync | B | Open |
| C1 | `mustBackInIsNotTheReason` is `isReversible()` under another name with an unused `layout` parameter — `V33-C8`'s second precondition is unchanged, and its replacement first one duplicates the assertion twelve lines below it | C | Open |
| C2 | The new helper was inserted between `shortBerth()`'s javadoc and `shortBerth()`, so that javadoc now documents the helper and `shortBerth()` has none | C | Open |
| C3 | `one.sh` counts skips into the same counter as failures while its comment — lifted from `battery.sh` — says they are "counted apart"; the exit code lost `battery.sh`'s 2-for-skips split, and two more of its sibling's corrections are still missing | C | Open |
| C4 | The 600 ms correction was written at one site. `runAndTimeTheRoute` and `routeFinished`, in the same file, still say the clear waits for the route — and the debug line that exists to settle that question times the spawn | C | Open |
| C5 | The three-way comment accounts for two of the three kinds of drive `execSwitching` issues; the green-to-red drives, which occur in two of its three branches, are not mentioned | C | Open |
| C6 | The two new assertions in `testTheAffordancesAskTheGuardsOwnQuestion` are whole-file `contains` on raw source, while the guard half beside them is body-extracted and comment-stripped — the shape `V32-B2` was raised for, at the door beside it | C | Open |
| C7 | `testThePaletteStillPlacesTiles`'s new `editor[0].dispose()` is the third thing in that `finally` that runs before `sandbox.close()`, which is the line that puts the operator's layout preference back | C | Open |
| C8 | Every finding this commit answers is still marked open in its own document; only `SVN-B7` was dispositioned. `V32-C6` said exactly this of the previous commit | C | Open |
| D1..D9 | Nine checks that came back clean, each with the mutation or the alternative explanation traced | D | Closed |

---

## B — medium

| | Finding | Status |
|---|---|---|
| **V34-B1** | The strip's error count is read and thrown away, and two places now assert it must stay | open |

### V34-B1 — the affordance fix left a call nothing reads, and pinned it

`src/org/traincontrol/gui/AutonomyOverlayToggle.java:342-356`:

```java
        int errors = ui != null ? ui.autonomyErrorCount() : lastTotalErrors;

        // THE GUARD'S OWN QUESTION, not the count (V31-B1, V32-B1).
        //
        //   ...
        // The count above is still read, and still right for what it is used for below: the strip says
        // HOW MANY, and the band's colour follows it.
        boolean broken = ui != null ? ui.autonomyHasErrors() : lastTotalErrors > 0;

        fixing = source != null && source == start && broken;
```

`errors` is assigned on line 342 and never read again. `syncRun` runs from line 297 to line 414 and the
token appears three times in it — the assignment, and twice inside comments (342, 347, 359). Nothing
after line 356 mentions it, including through the early return at 366-370.

**The comment on lines 352-353 is false in both halves.** The strip's "HOW MANY" is not this number: it
is the `totalErrors` / `pageErrors` arguments handed to `setFindings` (line 473) and cached in
`lastPageErrors` / `lastTotalErrors` (lines 480-483). And the band's colour does not follow the count
either — `paintState` (line 642) reads `excluded` and `fixing`, and nothing else:

```java
        java.awt.Color background = excluded ? EXCLUDED_BACKGROUND
            : fixing ? FIX_BACKGROUND : java.awt.Color.WHITE;
```

**And the new surface rule asserts the same false reason.** `test/regression/testErrorsStopTheSetupRunning.java:246-248`:

```java
        assertTrue(toggle.contains("autonomyErrorCount()"),
            "AutonomyOverlayToggle no longer reads the error COUNT at all - it needs it to say how "
            + "many and to colour the band");
```

It does not need it for either. What that assertion pins today is one dead statement, and the next
person to delete it — which is the correct edit — gets a test failure telling them the strip cannot
say how many without it. That is the trap `V32-B1` describes in the opposite direction: a rule that
enforces the thing it exists to catch.

**The cost is not only tidiness.** `AutonomySession.check()` is not cached — `LayoutRightclickAutonomyMenu.java:185-190`
says so, in the comment written to stop the same waste at that door: *"Both of these reach
AutonomySession.check(), which is not cached: it rebuilds the termini and turn-around sets over every
point in the graph."* `autonomyErrorCount()` walks it (`TrainControlUI.java:20218-20223` →
`AutonomySession.errorCount()`, `AutonomySession.java:3557`), and `autonomyHasErrors()` walks it again
(`hasErrors()` is `hasBlockingProblems() || errorCount() > 0`, `AutonomySession.java:3583-3586`). So
`syncRun` now does two whole-graph walks on the event thread where the pre-fix code did one, and
discards the first.

`syncRun` is not rare. `TrainControlUI.java:3515-3524` binds it to the `enabled` and `text` properties
of both run buttons, and its own comment there says *"Fourteen places in this class switch these two
buttons on and off - power, placement, a locomotive still rolling, a refused load, the tabs being
pulled"*. One of those fires while trains are moving.

**Fix.** Delete line 342, delete the sentence at 352-353, and change the assertion at 246-248 to read
the count where the strip actually gets it — or drop it, since `LayoutRightclickAutonomyMenu` is the
surface that genuinely needs `autonomyErrorCount()` and is asserted separately on line 251.

*If a reader prefers the README's letter table literally, "dead code" is a C. It is filed as B because
the dead read is asserted to be live in two places, one of which will resist its removal, and because
it doubled an uncached graph walk on the EDT that a sibling door was corrected for two commits ago.*

---

## C — low

| | Finding | Status |
|---|---|---|
| **V34-C1** | `mustBackInIsNotTheReason` is the same tautology renamed | open |
| **V34-C2** | `shortBerth()`'s javadoc now documents the new helper | open |
| **V34-C3** | `one.sh` counts skips as failures while saying it does not | open |
| **V34-C4** | The 600 ms correction was not swept to its twins in the same file | open |
| **V34-C5** | The three-way comment omits the drives commanded to red | open |
| **V34-C6** | The two new affordance assertions are whole-file greps | open |
| **V34-C7** | The palette test's new dispose runs before the preference restore | open |
| **V34-C8** | The findings this commit answers are still open in their own documents | open |

### V34-C1 — the helper is `isReversible()` with a parameter it ignores, so `V33-C8` is not fixed

**FIXED, by `V36-B2` and `V37-B2` rather than here** (verified 2026-09-03).

`mustBackInIsNotTheReason` no longer exists.  What stands in its place says out loud that there is no
precondition about `mustBackIn` at all, records both attempts at one and why each was wrong - the first
restated the setter, the second asked the railway for a reversing point, which is backwards - and the
confound is ruled out by a CONTROL in the same test instead: a train that fits reaches the berth.

The one residue this finding named is still there and is left: the fixture's length assertion duplicates
the `> 0 && < 40` check twelve lines below it.  Two statements of one precondition is not worth an edit
to a test this heavily reasoned about.

`test/core/testHomeStaging.java:189-200`:

```java
        tooLong.setReversible(true);

        // NOT a precondition on the two lines above - those set these values, and asserting them back
        // proves only that a setter sets (V33-C8).  What is worth checking is the thing the fixture
        // has to be true for the assertions to mean anything, and neither of these is set here.
        assertTrue(layout.getEdge("HS A", "HS D").getLength() < tooLong.getTrainLength(),
            ...);

        assertTrue(mustBackInIsNotTheReason(layout, tooLong),
            "precondition: a non-reversible train is refused a terminus by mustBackIn instead, so "
            + "this would pass whether or not the room rule exists");
```

and the helper, `testHomeStaging.java:259-262`:

```java
    private static boolean mustBackInIsNotTheReason(Layout layout, Locomotive loc)
    {
        return loc != null && loc.isReversible();
    }
```

`layout` is never used. The body is `loc.isReversible()`, which is the exact expression `V33-C8` was
raised about, evaluated eleven lines after `tooLong.setReversible(true)` on line 189 and eight lines
after nothing else touches it. **The sentence "neither of these is set here" is false of the second
assertion**: `isReversible` is set here, on line 189, by the statement the comment is standing under.

The first replacement is sound in itself — `getEdge("HS A", "HS D").getLength()` comes from the fixture
— but it duplicates a check already twelve lines below it, `testHomeStaging.java:205-211`, which asks
the same edge for `> 0 && < 40` and prints what it found. So of the two preconditions this commit
replaced, one is the same tautology under a new name and the other restates an assertion already in the
method.

**It does not weaken the test.** The audit test still discriminates: `isPathClear` is asked directly at
lines 214-218 as a control, the terminus flag at 202, and the measured-and-short edge at 205. What is
wrong is that `V33-C8` reads as answered and is not.

**Is the helper worth its name?** Its answer is right for this fixture and for the wrong reason. The
runtime rule is `HomeStaging.mustBackIn` (`HomeStaging.java:1585-1588`):

```java
        return at != null && at.isTerminus() && loc != null && !loc.isReversible();
```

so `!mustBackIn(loc, at)` equals `loc.isReversible()` **only where `at` is a terminus** — and the helper
takes no point at all. Asked about a non-terminus it would answer `false` for a non-reversible train and
claim `mustBackIn` is the reason, when `mustBackIn` never fires there. It also does not call
`mustBackIn`, so it is a hand copy of half of a rule, in the file whose subject is the planner drifting
from a hand copy of the runtime's rules. Its javadoc's *"on a ring with no reversing point that is every
terminus"* is about `connected`'s reversing-point requirement, not about `mustBackIn`, which never looks
at the layout — which is the one thing the unused `layout` parameter would have let it check.

### V34-C2 — `shortBerth()` lost its javadoc to the new helper

**FIXED, by the same rounds** (verified 2026-09-03).  `shortBerth()` carries its own javadoc again, and
the helper whose insertion orphaned it is gone.

`test/core/testHomeStaging.java:243-264`:

```java
    /**
     * The ring, with HS D a terminus reached over a short measured edge.
     *
     * @return the configuration
     */
    /**
     * Whether the terminus rule would refuse this train anyway, which would make a room test vacuous.
     *   ...
     */
    private static boolean mustBackInIsNotTheReason(Layout layout, Locomotive loc)
    ...
    private static String shortBerth()
```

The helper was inserted after the closing `*/` of `shortBerth()`'s javadoc, so that block — "The ring,
with HS D a terminus reached over a short measured edge... `@return the configuration`" — now sits on
`mustBackInIsNotTheReason`, which returns a boolean. `shortBerth()` has none. Javadoc would attach the
first block to the method and warn about the orphaned `@return`; the compiler will not.

This is the failure mode recorded in the README under *"Check the shape of what you changed"* — an
insertion computed from the preceding javadoc, landing between a javadoc and its method.

### V34-C3 — `one.sh` counts a skip as a failure, and says it does not

**FIXED 2026-09-03.**  `one.sh` now does what its comment claimed:

- **Skips are counted apart**, in their own counter, and exit **2** - battery.sh's number, for
  battery.sh's reason.  A class that skips because it needs a display no longer makes the runner report
  a failure.
- **Failures are asked first**, so a class that both fails and skips is headlined as a failure.
- **The no-summary branch splits** "no heap (machine busy, rerun)" from a class that has to be read,
  matching both wordings JDK 8 uses.
- **The "always exited 0" claim** now says what it means: whatever the tests did.  The live-layout, lock
  and probe branches have always had statuses of their own.

The javac hunk this finding calls a fifth divergence is not one: `one.sh` captures javac's output to a
file and greps the file, so there is no pipeline for SIGPIPE to reach back through.

The narrow hole recorded here - `Configuration Failures: 0, Skips: 3` passing the configuration check -
is left as recorded: config skips accompany test skips, which the branch above now catches.

The classification chain is correct (see `V34-D1`); what it does with the answer is not.

`docs/tools/one.sh:221-240`:

```sh
    # GREEN IS NOT "no failures" (the other half of the same omission, V33-B1).
    #
    # A class whose @BeforeClass throws reports every test SKIPPED and none failed, so "Failures: 0"
    # is true of a class that tested nothing at all.  Counted apart rather than as a failure, because
    # a skip can be legitimate - several classes need a display and say so.
    if echo "$summary" | grep -qE "Total tests run: 0"
    then
        echo "*** $T RAN NOTHING - $summary"

        BAD=$((BAD+1))
    elif ! echo "$summary" | grep -q "Skips: 0"
    then
        echo "*** $T SKIPPED TESTS - $summary"
        ...
        BAD=$((BAD+1))
```

"Counted apart rather than as a failure" is `battery.sh`'s sentence and it is true there:
`battery.sh:449-467` puts these in `skip`, prints them under their own heading, and
`battery.sh:511-521` exits **1** for failures and **2** for skips, with the reason written out — *"A
class that tested nothing counts. It is not a failure, but it is not a pass either."* `one.sh` has one
counter, one message — "*** $BAD of the classes above did not come back clean ***" — and exit 1 for
everything. Nothing is counted apart. A class that skips because it needs a display now makes `one.sh`
exit 1, which is the state its own comment says it is avoiding.

Two smaller divergences from the sibling it is catching up with:

- **Order.** `battery.sh` asks `Failures: 0` first (line 430); `one.sh` asks it last (line 237). A class
  that both fails and skips is reported by `one.sh` as "SKIPPED TESTS - ... A skipped class is not a
  green class", with the failures visible only in the raw grep above. Same count, wrong headline.
- **The no-summary branch.** `battery.sh:423-430` splits "no heap (machine busy, rerun)" from a real
  failure, with the note that reading them identically *"cost a round of hunting for a fault in three
  classes that were fine"*. `one.sh:184-192` still reports one thing. The commit message says this hunk
  is "the third correction this round that one.sh was missing and battery.sh already had"; that is a
  fourth, and `V33-C3`'s javac items are a fifth (`one.sh:153-154` still pipes javac's errors through
  `head -8`, which is the SIGPIPE shape the comment at 158-163 says was removed from the loop).

Also: **"This always exited 0"** (`one.sh:169`) is not accurate — the live-layout branch has exited 1
since the file was written (now line 261), and the lock and probe branches exit 2. The claim it wants to
make is "it always exited 0 whatever the tests did", which is true. And the new paragraph was appended
to the previous comment block with no blank line, so `one.sh:158-172` now reads as one comment with two
headings.

One narrow hole worth knowing rather than fixing: TestNG prints the `Configuration Failures` line when
either the failures **or** the skips are non-zero, so `Configuration Failures: 0, Skips: 3` passes the
check on line 210. Config skips essentially always accompany test skips, which line 231 catches, so
nothing reachable falls through — but the branch is about failures only and the message says so.

### V34-C4 — the 600 ms correction was written once, and its twins in the same file still disagree

**FIXED 2026-09-03.**  All three sites now say the same thing, and it is the true one.

`routeFinished`'s "later of the two" paragraph says that on today's code the floor and a fixed hold are
the same thing, because `execRoute` starts a thread and returns - and why the floor's shape is still
worth keeping if execution ever becomes synchronous.  `runAndTimeTheRoute`'s javadoc no longer claims it
does not put the button back, which it does, on the last line of its `finally`.  And the debug line says
what it measures: the dialog and the spawn, which is what it was added to settle - not the route.

The new claim is right (`V34-D7`). Two comments 8,000 lines away in the same file say the opposite and
were not touched.

`TrainControlUI.java:16123-16126`, new:

> And the window is short. `execRoute` returns as soon as it has spawned the route thread, so the name
> is cleared 600 ms later whatever the route is still doing - this is a debounce, not a lock

`TrainControlUI.java:16216-16217`, unchanged:

```java
            // The route's own ending puts the button back again, through the floor in routeFinished -
            // so a fast route still shows, and a slow one is not declared finished early.
```

`TrainControlUI.java:24570-24581`, unchanged:

> LATER OF THE TWO: when the route ended, or when the grey has been on screen long enough... A floor
> rather than a fixed hold, because the two say different things when a route is slow: a fixed second
> would put the button back while the route was still running... This way the button is grey exactly
> while pressing it again would be refused

Neither is true, for the reason the new comment gives: `work.run()` ends at
`this.model.execRoute(route)` (line 16186) → `MarklinControlStation.java:3145` `r.execRoute(false)` →
`MarklinRoute.java:501` `new Thread(...)`, which returns at once. So `routeFinished` is reached
milliseconds after `routeStarted`, `showFor` is always ≈ 600, and the "later of the two" never picks the
route's ending. A slow route **is** declared finished early — at 600 ms.

Two smaller consequences in the same place:

- `runAndTimeTheRoute`'s javadoc (line 16199-16201) says *"It used to put the button back here as well.
  It no longer does: see routeStarted, where the clearing became a fixed second."* It does put it back
  here — `routeFinished(route)` is the last line of its `finally` — and the clearing is in
  `routeFinished` / `clearRunningRoute`, not in `routeStarted`.
- The debug line at 16218-16223, *"Route X finished executing in Nms"*, measures the dialog plus the
  spawn, not the route. `routeStarted`'s comment at 24540-24542 says that measurement exists because
  *"'nothing happens visually' cannot be told apart from 'it happened and was too quick to see' without
  a measurement - and I have guessed wrong about which of those it is once already."* It is measuring
  the wrong interval to settle that.

Nothing behaves wrongly. But this file now argues both sides of the question the commit set out to
settle, and the reader who finds 24569 first will restore the sentence that was just corrected.

### V34-C5 — the three-way comment accounts for two of the three kinds of drive

**FIXED 2026-09-03.**  The comment accounts for all four `(accessory, accessory2)` states rather than
for three branches, and names the third kind of drive: a drive commanded RED cannot clear protection,
because red is the direction protection itself commands.  The conclusion the finding checked and upheld
is stated as the conclusion.

`src/org/traincontrol/gui/LayoutLabel.java:415-424`:

> A signal or a lamp goes through `Accessory.doSwitch()`, which is `isStraight() ? turn() :
> straight()`. A THREE-WAY does not: `execSwitching` drives its two accessories directly, in three
> cases. It reaches the same place - in every one of those cases the drive commanded from red to green
> is exactly the one whose `isStraight()` is false, and the drives re-commanded green were green already

The three cases, `LayoutDiagramComponent.java:144-164`, with `setSwitched(false)` = straight = green and
`setSwitched(true)` = turned = red (`Accessory.java:190-192`, `147-149`, `230-233`):

| branch | `accessory` | `accessory2` |
|---|---|---|
| both straight | `setSwitched(true)` — **green → red** | `setSwitched(false)` — green, already green |
| `accessory2` straight (so `accessory` turned) | `setSwitched(false)` — red → green | `setSwitched(true)` — **green → red** |
| `accessory2` turned | `setSwitched(false)` — green already, or red → green | `setSwitched(false)` — red → green |

The sentence names the red→green drives and the green-already drives. The third kind — a drive commanded
green→red — happens in two of the three branches and is not mentioned, so the accounting does not cover
what the code does in the two cases it claims to cover.

**The conclusion survives**, and I checked it rather than assuming it: a drive commanded to red is the
protective direction and cannot clear protection, and across all four `(accessory, accessory2)` states
every drive commanded green is either on an accessory whose `isStraight()` is false or was green
already. So the tile's test at `LayoutLabel.java:1393` — `if (accessory.isStraight()) return false;` —
has neither a false negative nor a false positive on a three-way, which is what the comment is defending
(recorded as `V34-D8`). One sentence, not the finding.

### V34-C6 — the two new affordance assertions are whole-file `contains`

**FIXED 2026-09-03**, by the work under `V36-C3` and `V37-C1`.  The strip's assertion reads the body
with comments stripped, and the residual this finding named - that nothing pinned the answer REACHING
`fixing` - is now its own rule, mutation-confirmed against a body that asks the guard's question and
decides on `lastTotalErrors > 0`.

`test/regression/testErrorsStopTheSetupRunning.java:242-251`. The guard half of this test, immediately
above, does it properly — `bodyOf(ui, "private boolean refuseAutonomyStartWhileBroken()")` and
`bodyOf(ui, "public boolean canStartAutonomy()")`, both through `withoutComments`. The two lines added
by this commit read the raw file:

```java
        String toggle = read("src/org/traincontrol/gui/AutonomyOverlayToggle.java");
        ...
        assertTrue(toggle.contains("autonomyHasErrors()"), ...);
```

`read` (line 258) does not strip comments and does not extract a body. So the assertion is satisfied by
the name appearing anywhere in the file, including in a comment, and it does not check that the answer
reaches `fixing`. Computing `broken` and leaving `fixing = ... && errors > 0` passes it — which is
`V32-B2`'s shape exactly, at the door beside the one this same commit fixed with `bodyOf`. `V32-C2`
already named the omission ("does not pin `autonomyHasErrors()`'s body"); the fix added a third
whole-file grep rather than closing it.

A plain revert is still caught, because `autonomyHasErrors()` appears nowhere else in that file today.
That is what keeps this a C rather than a B.

### V34-C7 — the new dispose runs ahead of the preference restore

**FIXED 2026-09-03.**  `sandbox.close()` is in its own `finally` now, so the preference restore cannot
be skipped by anything the disposals throw.  Nothing throws there today - which is why this was a C -
but the ordering made the block's most important line depend on its most expensive ones.

`test/regression/testThePaletteStillPlacesTiles.java:188-206`:

```java
        finally
        {
            // THE EDITOR TOO, not only the window that owns it (V33-C11).
            ...
            if (editor[0] != null)
            {
                javax.swing.SwingUtilities.invokeAndWait(() -> editor[0].dispose());
            }

            if (ui[0] != null) { ... ui[0].dispose() ... }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
```

`sandbox.close()` is the line that puts `LAYOUT_OVERRIDE_PATH_PREF` back
(`test/support/LayoutSandbox.java:92-105`), and its own javadoc says why it matters: *"a test that
leaves a path behind has changed which layout the application opens the next time the operator starts
it, which is worse than the churn this class exists to remove."* Anything that throws before it skips
it, and the preference is per-user and survives the JVM.

`LayoutEditor.dispose()` is overridden (`LayoutEditor.java:529-545`) and does file IO —
`endEditSession()` → `AutonomyCompanionStore.forgetBeforeEdit()` — on a note the comment there says
*"lives under OneDrive and the delete can lose to a sync client."* **I found no throwing path today**:
`forgetBeforeEdit` catches `IOException | RuntimeException` (line 4521) and returns false, and the log
call is null-guarded. So this is not a live defect.

The hazard is pre-existing — `ui[0].dispose()` and `model.stop()` were already ahead of the restore —
and the commit widened it by one, in a `finally` whose most important line is its last. One line fixes
it: put `sandbox.close()` in its own nested `finally`, or first.

### V34-C8 — the findings answered are still open in their own documents

**FIXED 2026-09-03.**  This C-round sweep is the answer, and it is doing the whole set rather than this
document: every finding cited here now carries its verdict where the reader is told to look for it, and
the four places where a disposition table said `open` over prose that said `FIXED` have been corrected.

The commit dispositioned one finding: `SVN-B7` in `docs/reviews/2026-09-01-week-of-commits-review.md`,
and did it well — the entry now says both what was fixed and that the reasoning behind it was wrong.

Nothing else was touched. `docs/reviews/2026-09-02-third-validation.md:27-40` still shows `A1`, `B1`,
`C4`, `C8`, `C9`, `C10`, `C11` as **Open**, and all seven are answered by this commit.
`2026-09-02-first-validation.md:40` still shows `V31-B1` as open, and `2026-09-02-second-validation.md:59-60`
shows `V32-B1` and `V32-B2` as open. A reader of any of those three documents today cannot tell which
of their findings are still live, which is what the README's "one status, one location" rule exists to
prevent.

This is a recurrence rather than a new observation: `2026-09-02-second-validation.md:43` records
`V32-C6` — "Dispositions | both | **Not done at all**" — of the two commits before this one.

---

## D — checked and sound

These are the ones that can be trusted, and on a validation pass that is the useful half.

| | Check | Result |
|---|---|---|
| **V34-D1** | `one.sh`'s classification chain | correct on every path |
| **V34-D2** | `broken` is the guard's own question, and the null fallback | correct, and identical to the old behaviour |
| **V34-D3** | `bodyOf` on `aboutToClearProtection`, and a parameter rename | reliable, and fails loudly |
| **V34-D4** | The longer-approach control depends on the room rule and nothing else | traced; no other rule can refuse a 20-unit train here |
| **V34-D5** | The restore still covers a locomotive mutated twice | yes |
| **V34-D6** | "The queue is FIFO", and the pre-fix code failing all twenty | correct |
| **V34-D7** | `Route.setExecuting`, "every door reaches it", and the 600 ms window | all three correct |
| **V34-D8** | The three-way conclusion, over all four states | correct — no false negative and no false positive |
| **V34-D9** | The `SVN-B7` disposition | honest; the play-button door really did stop asking |

### V34-D1 — `one.sh`'s classification, path by path

**The grep patterns match what TestNG actually prints.** Read out of the jar rather than assumed:
`org/testng/SuiteRunnerWorker.class` holds `"Total tests run: "`, `", Failures: "`, `", Skips: "` and
`"Configuration Failures: "`; `org/testng/reporters/TextReporter.class` holds the indented per-test
forms `"    Tests run: "` and `"    Configuration Failures: "`. The case is exactly as written on
`one.sh:181` and `208`, the word is `Failures`, and the per-test line says "Tests run" rather than
"Total tests run" — so `grep -q "Total tests run"` on line 184 cannot be satisfied by the per-test block,
and `tail -1` on lines 194 and 208 takes the suite-level line, which is printed last. Both summary
lines are conditional on being non-zero in TestNG, which is why the `[ -n "$config" ]` guard on line 210
is needed and right.

**Every path counts once, and only when it should.**

| path | line | counts? | correct |
|---|---|---|---|
| no summary at all | 184-192 | yes, then `continue` | yes |
| `Configuration Failures: N`, N≠0 | 210-219 | yes, then `continue` | yes |
| `Total tests run: 0` | 226-230 | yes | yes |
| `Skips:` not 0 | 231-236 | yes | yes |
| `Failures:` not 0 | 237-240 | yes, silently | yes — the summary line was already printed by the grep on 181 |
| clean | — | no | yes |

No path can count twice: the first two `continue`, and the last three are one `if`/`elif` chain.
Neither `continue` skips anything that still needed doing — the loop body ends at line 240, and the
fingerprint is taken outside it at 243.

**No numeric false matches.** `grep -q "Failures: 0"` against `Failures: 10` does not match (the
character after `: ` is `1`), and the same for `Skips: 10` and `Total tests run: 20`. `$summary` is the
`Total tests run` line only, so `Configuration Failures: 0` cannot satisfy the `Failures: 0` test.

**`BAD` survives the loop.** It is a `for` loop in the main shell, not a `while read` behind a pipe, so
the increments are not lost to a subshell; `set -u` is satisfied because `BAD=0` precedes it and both
`summary` and `config` are assigned before use.

### V34-D2 — the strip now asks the guard's question, and the fallback is unchanged

`autonomyHasErrors()` is exactly what the guard asks. `refuseAutonomyStartWhileBroken`
(`TrainControlUI.java:5183`) is `if (!getAutonomySession().hasErrors()) return false;`, and
`autonomyHasErrors()` (`TrainControlUI.java:20192-20197`) is `session.hasErrors()`. Same method, same
session, no second copy. `canStartAutonomy` (20173-20177) already asked it. So all three affordances and
the guard now ask one question, which is what `OB-090` is about.

**The fallback the briefing asks about is equivalent.** The old line was
`fixing = ... && errors > 0` with `errors = ui != null ? ui.autonomyErrorCount() : lastTotalErrors`, so
with `ui == null` it evaluated `lastTotalErrors > 0` — which is character for character what
`broken` now evaluates. The change touches only the `ui != null` path.

**And it is unreachable anyway.** `ui` is `private final` (line 24), and the one construction is
`new AutonomyOverlayToggle(this)` at `TrainControlUI.java:5907`. `lastTotalErrors` is maintained — it is
written by `setFindings` (line 482) and re-read by `setBannerShowing` (line 683) — so even the
construction-time window the comment describes answers 0, which is right.

### V34-D3 — `bodyOf` is reliable here, and a rename fails loudly

`bodyOf` (`testEditorSurfaceRules.java:1087-1108`) takes the first occurrence of the declaration string,
then brace-matches from the next `{`. Both assumptions hold for this method:

- The string `"private static boolean aboutToClearProtection(TrainControlUI tcUI, Accessory accessory)"`
  occurs once in `LayoutLabel.java`, at line 1386. The javadoc above it (1374-1385) names
  `protectsAnOccupiedSquare` but not the declaration.
- The body (1387-1396) contains no braces at all — no nested block, no string literal, no brace in its
  one comment — so the comment- and string-blind matcher cannot run past the closing `}`.

A parameter rename breaks the declaration string, `bodyOf` returns `""`, and
`assertFalse(helper.isEmpty(), "aboutToClearProtection has moved or been renamed")` on line 656 fires
first. The message is slightly off for a rename of the parameter rather than the method, but it names
the method and the failure is unmissable — a maintenance cost, not a false green.

**And it kills the mutation it names.** Gutting the helper's last line to `return false;` removes
`protectsAnOccupiedSquare(accessory)` from the body, and line 658 fails. The test's javadoc claim
"MUTATION: deleting either call to `protectsAnOccupiedSquare` fails this" is true now and was not before
this commit.

*One thing the assertion still cannot see, noted rather than raised: the route half of the same rule
(line 665) is a whole-file `contains` on `MarklinRoute.java`. Deleting the call would fail it — the
string occurs once, at line 475 — but emptying the branch around it would not. See `V34-C6` for the same
shape at a door this commit did touch.*

### V34-D4 — the control depends on the room rule, and nothing else can refuse a 20-unit train

I traced every use of `getTrainLength()` in `src/`. Only two rules can refuse a train for its length,
and only one of them is reachable in this fixture:

- **`Point.validateTrainLength`** (`Point.java:910-916`) returns true when `getMaxTrainLength() == 0`,
  and `twoWaysToOneBerth` (`testHomeStaging.java:3461-3487`) sets no `maxTrainLength` on any point, so
  it is 0 (`setMaxTrainLength` maps null and negative to 0, line 895-901). It cannot refuse anything
  here.
- **The room rule**, `HomeStaging.java:1041-1046` → `Layout.measuredRoomToReverseInto`
  (`Layout.java:6201-6220`). Every edge in the fixture is measured, HS D is a terminus, so it is asked
  on both arrivals: the direct route sums 5, the way round by HS B sums 10. Both are less than 20, both
  are `continue`d, and HS D is never reached.

Nothing else applies. `mustBackIn` (`HomeStaging.java:1585`) is false for a reversible locomotive and
`loc.setReversible(true)` is still in force at line 3442. `canRest` and `canEnter` do not read length.
The lock-edge rule is deliberately not consulted in this search (`HomeStaging.java:986-1005`).
`planReturnToHome` (`Layout.java:6591`) is documented read-only, so calling it twice — once in the
assertion and once in its eagerly-evaluated message — changes nothing.

So the pair does what it claims: with the room rule deleted the direct approach is accepted and the
control fails; with the ordering defect restored the main assertion fails and the control still passes.
Two different mutations, one killed by each half.

### V34-D5 — the restore covers both mutations

`testHomeStaging.java:3449-3453` restores from `lengthWas` and `reversibleWas`, captured at 3378-3379
before either mutation. Because they are absolute values rather than an undo, mutating the length twice
— 8 at line 3388, 20 at line 3442 — restores identically. `Integer`-safe: `lengthWas` is an `Integer`
handed straight back to `setTrainLength`.

### V34-D6 — "the queue is FIFO", and the twenty out of twenty

`firstClearRoute` uses `Deque<Candidate> queue = new ArrayDeque<>()` with `queue.add(...)` and
`queue.poll()` (`HomeStaging.java:942`, `969`, `973`) — `addLast` and `pollFirst`, so FIFO. The
correction is right, and so is the conclusion drawn from it: HS A's neighbour loop (975-990) examines
both of its edges before anything else is dequeued, so the direct arrival at HS D is processed during
HS A's own expansion whichever order `getNeighbors` shuffles them into. Pre-fix, that arrival was
written into `seen` before the room test, and the way round by HS B — same key (`HS D/straight`, since
neither square is reversing) and the same empty command map, so dominated — was pruned at line 1048.
Deterministic, and the old comment's "about half the time" was wrong.

The kept repetition is justified in the comment on the README's grounds rather than on the old false
one, which is the right way round.

### V34-D7 — the three claims about the route guard

- **`Route.setExecuting()` is a synchronized re-entrancy guard.** `base/Route.java:115-125`:
  `synchronized public boolean setExecuting()`, returns false when `isExecuting` is already set.
- **Taken by the route thread before it does anything.** `MarklinRoute.java:500-503`: the first
  statement inside `new Thread(() -> ...)` is `if (this.setExecuting())`, with `stopExecuting` in a
  `finally`.
- **Every door reaches it, including the diagram's route tile.** `LayoutDiagramComponent.execSwitching`
  (lines 179-184) calls `this.route.execRoute(false)` directly, bypassing `TrainControlUI.executeRoute`
  entirely — so the tile is outside `routesExecuting` and inside `setExecuting`, exactly as the comment
  says.
- **600 ms, not the route's duration.** `model.execRoute` (`MarklinControlStation.java:3133-3145`) calls
  `r.execRoute(false)`, which spawns a thread and returns, so `runAndTimeTheRoute`'s `work.run()` is over
  at once, `routeFinished` runs immediately, `showFor` is the full `ROUTE_MINIMUM_VISIBLE_MS = 600`
  (`TrainControlUI.java:24610`), and the timer clears the name. The correction is accurate. It is the
  *other* comments in that file that now need it (`V34-C4`).

### V34-D8 — the three-way conclusion holds in all four states

Enumerated in the table under `V34-C5`. Every drive `execSwitching` commands to green is either on an
accessory whose `isStraight()` is false — which is precisely what `aboutToClearProtection` tests
(`LayoutLabel.java:1393`) — or on one that was green already and is therefore not a change. And every
accessory whose `isStraight()` is false, in the branch that can be reached with it in that state, **is**
commanded green. So the tile's warning has no false negative and no false positive on a three-way,
including the state where both drives are turned and both are cleared. The comment's conclusion is
right; only its accounting is short.

### V34-D9 — the `SVN-B7` disposition is honest

The claim it makes — "the play button stopped asking separately" — is true.
`TrainControlUI.java:19324-19339` now calls `executeRoute(route.getName())` with no test of its own, and
`routesExecuting.contains` survives in the file only at the funnel (16130), the hover (21388) and the
two painters (25013, 25047), which are affordances rather than doors. The surface rule at
`testEditorSurfaceRules.java:585-609` pins both halves and asks the funnel's body, not the file.

The disposition also does the thing the README asks for and the rest of this commit did not: it records
that the finding was fixed **and** that the reasoning published with the fix was wrong, without
withdrawing the fix. The stale code citation above the quote (`TrainControlUI.java:19222-19236`, now
`BulkEnableActionPerformed`) is a line-number drift in an evidence block the README explicitly treats as
a historical record, and predates this commit.

---

## What this pass did not cover

- Anything in the commit's diff to `docs/reviews/2026-09-01-week-of-commits-review.md` beyond the
  `SVN-B7` entry.
- Whether the tests actually pass. Nothing was run, and three of the findings above (`V34-B1`'s cost,
  `V34-C3`'s exit code, `V34-C7`'s throwing path) would be settled in seconds by execution and are
  argued from reading instead.
- The correctness of the underlying fixes from `e6791631` and `2e83b737` — `V31`, `V32` and `V33`
  covered those, and this pass took their findings as given and looked only at the answers to them.
