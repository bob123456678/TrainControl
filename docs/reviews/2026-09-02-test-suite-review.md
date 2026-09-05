# Test suite review — v3.0.0 pre-release, second pass

**Status:** open

**Citation prefix: `TS3`.** Cite findings from this document as `TS3-A1`, `TS3-B2`, and so on.

| | |
|---|---|
| Reviewed | `test/core` (72 classes), `test/regression` (51), `test/ui` (22), `test/support` (3); `docs/tools/battery.sh`, `docs/tools/one.sh`, `docs/tools/reap.ps1`; the `test-one-class` list in `build.xml` |
| Version | v3.0.0, branch `autonomy-diagram-r0` |
| Commit | `cf048f9b`, with `1cfdf370`, `87b6c10a`, `975f157d` and `8d1c17ca` read as the freshest and least reviewed code |
| Date | 2026-09-02 |
| Method | **Reading only.** No test, `ant`, `javac`, `java`, TestNG, `battery.sh` or `one.sh` was run at any point, by me or by any agent under me. I spawned no subagents. Where a claim would need a run to settle, it says so. |

The question asked was **which tests cannot fail**, and **which rules the code enforces have no test at
all**. Both are answered below and interleaved by severity, as the convention requires.

**How severity is assigned.** A gap or a vacuous assertion is not itself wrong behaviour, so the letter
is the severity of the defect that would go unnoticed. Each entry says which.

**Where the findings are concentrated.** Nine of the fourteen findings are in, or were created by, code
written on 2026-09-02 — the four tests added and the five strengthened in `1cfdf370` / `87b6c10a` /
`975f157d` / `8d1c17ca`, and the guard `87b6c10a` widened. That is the
prediction the briefing made and it held: the freshest test code, written to fix tests that could not
fail, contains a control that cannot fail (`TS3-B1`), a mutation claim the test survives (`TS3-B3`), an
unguarded negative (`TS3-B4`) and a shared-state leak the same file forbids in writing (`TS3-B2`).

The one A is not in a test at all. It is in the harness, and it has been true for four days.

---

## Status

### A — high

| | Finding | Status |
|---|---|---|
| TS3-A1 | `battery.sh` has been calling the JVM reaper at a path that does not exist since 2026-08-30, with both streams discarded | open |

### B — medium

| | Finding | Status |
|---|---|---|
| TS3-B1 | `testAShutPlainSquareReachesTheRunningGraph`'s control compares a base name against emitted copy names, so it can never fail | open |
| TS3-B2 | `testTheAuditSeesTheReversalRoomRuleTheStagingPlannerDoesNotHave` leaves `HS alpha` 40 units long and reversible for the other 84 tests in the class | open |
| TS3-B3 | `testSwitchingAnAccessoryByHandAsksAboutProtectingSignals` survives the mutation its own javadoc names, and is a whole-file `contains` with the comments left in | open |
| TS3-B4 | `testEveryDoorThatRunsARouteAsksIfItIsAlreadyRunning`'s third assertion is an unguarded negative over a `bodyOf` result | open |
| TS3-B5 | `one.sh` does not read the `Configuration Failures` line, so a class whose teardown throws reads as green on the runner used for every single-class run | open |
| TS3-B6 | SVN-B10 widened the start guard and left the three affordances asking the old question; nothing tests the guard, and the test named for the invariant no longer reads it | open |
| TS3-B7 | The staging planner's "a longer approach is more room" rule has no test, and a `continue` turned into a refusal would pass every test in the suite | open |

### C — low

| | Finding | Status |
|---|---|---|
| TS3-C1 | `testAShutPlainSquareReachesTheRunningGraph` has no floor on `copies`, though its own comment says the number is the point — the floor its sibling was given in the same commit | open |
| TS3-C2 | The audit test's two preconditions restate values it set two lines earlier, and its name says the opposite of what it asserts | open |
| TS3-C3 | `testATrainTooLongForTheBerthIsNotBackedOverTheSwitch`'s second stated mutation cannot be told apart by its own fixture, and now describes the shipped implementation | open |
| TS3-C4 | `testErrorsStopTheSetupRunning` states twice, in a javadoc and in a failure message, that `hasErrors()` has no callers. It has had one since `87b6c10a` | open |
| TS3-C5 | `one.sh` runs the tests after a failed compile, and pipes `javac` into `head -8` — the construct the same file's comment says truncated a run | open |
| TS3-C6 | `testNonReversibleTrains` restores a null train length as zero | open |

### D — not defects

| | Finding | Status |
|---|---|---|
| TS3-D1 | Withdrawn as a B: the missing floor in `testAShutPlainSquareReachesTheRunningGraph` is latent, not live — the plain square really does split | closed — downgraded to TS3-C1 |
| TS3-D2 | Withdrawn: `Layout.measuredRoomToReverseInto` has no direct test and does not need one | closed — withdrawn |
| TS3-D3 | Withdrawn: `testNonReversibleTrains` mutates a locomotive from Adam's live database, and it cannot persist | closed — checked clean |
| TS3-D4 | All five tests strengthened in `1cfdf370` were checked against their own mutations and all five are genuinely fixed | closed — checked clean |
| TS3-D5 | `testTwoHomesOnOneSquareDoNotBothSurviveTheLoader` (`8d1c17ca`) is a well-built test | closed — checked clean |
| TS3-D6 | The `build.xml` battery list and the tree agree exactly | closed — checked clean |
| TS3-D7 | `battery.sh`'s own result classification is sound, and is the only place in the tooling that is | closed — checked clean |

Where I stand on the still-open `TCX` findings is a section of its own at the end.

---

## A — high

### TS3-A1 — `battery.sh` calls the reaper at a path that has not existed since 2026-08-30

**FIXED 2026-09-02 (`3c014e77`).**  `battery.sh` builds the path from its own directory - `REAPER="$(cd "$(dirname "$0")" && pwd)/reap.ps1"` - so moving the folder moves the reaper with it, and it says so loudly when the file is missing rather than sending the error to `/dev/null`.  The finding is the round's best result: four days of batteries reaped nothing and reported nothing.

*The original finding:*

**Disposition: fixed** - see the closure line above this one, which is where it was recorded; this audit confirms it.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.** Verified by reading and by `git log`. Severity: this is the guard that keeps a run's
own leftover test JVMs off the machine, and a second set of test JVMs is the mechanism by which the
operator's railway was damaged on 2026-08-30. The failure is silent by construction.

`docs/tools/battery.sh:349-352`, inside the per-class loop:

```bash
    # Only THIS RUN's leftover JVMs - reap.ps1 says why that is narrower than "every test JVM", and
    # narrower again than "every java.exe".
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(pwd)/tools/reap.ps1" \
        -RunId "$RUN_ID" >/dev/null 2>&1
```

There is no `tools/` directory in this repository:

```
$ ls -d tools
ls: cannot access 'tools': No such file or directory

$ find . -name "reap.ps1" -not -path "./build/*"
./docs/tools/reap.ps1
```

The script's own usage line says it is run from the project root (`battery.sh:9`, *"Usage, from the
project root: TC_SCRATCH=... bash docs/tools/battery.sh"*), so `$(pwd)` is the project root and the
argument resolves to `<root>/tools/reap.ps1`.

**When this broke, and why nobody saw it.** The string `tools/reap.ps1` was written on 2026-08-25
(`a9e9267b`, "The test battery stops killing TrainControl, and moves into the repository") and has never
been edited since:

```
$ git log --format='%h %ad %s' --date=short -S 'tools/reap.ps1' -- docs/tools/battery.sh tools/battery.sh
a9e9267b 2026-08-25 The test battery stops killing TrainControl, and moves into the repository
```

On 2026-08-30, `fb3722f5` moved the whole folder — its message says *"tools/ is now docs/tools/, with
all eleven referring files repointed."* Eleven files were repointed; `battery.sh`'s own call to its
sibling in the same folder was not. And the call ends `>/dev/null 2>&1`, so PowerShell's *"The argument
… to the -File parameter does not exist"* goes nowhere. **Every battery since 2026-08-30 has reaped
nothing and said nothing about it.**

**What it costs.** Two things, and the second is the one that matters:

1. A class whose JVM does not exit — a non-daemon thread, an AWT event queue, a socket that has not
   closed — is left running while the next class starts. `battery.sh:273-275` records what that used to
   look like: *"an orphaned JVM poisoned every class after it."* The reaper is the only thing in the
   loop that clears one.
2. Those leftovers carry `-Dtraincontrol.anyReceivePort=true` and a `testng` command line, which is
   exactly what the start-of-run probe at `battery.sh:122-124` matches. So the next battery — or
   `one.sh`, which carries the identical probe — refuses to start with *"TEST JVMS ARE ALREADY RUNNING"*
   and tells the reader *"Nothing needs deleting: this check clears itself when those processes exit."*
   They will not exit. The advice is wrong in exactly the case that produces it, and the remedy a person
   reaches for is to kill java.exe by hand, which is the blast radius `reap.ps1` exists to avoid.

**This is the pattern the harness keeps producing, named in its own comments.** `battery.sh:58-60`:
*"That is the third defect in this harness to report a FALSE RESULT rather than an error, and they all
have the same shape: the runner decided what a run meant while something else was changing what it was
running."* This one is a fourth, and it is worse than the three, because it produces no result at all —
the guard simply is not there.

**The test that is missing, and it is not a Java one.** `docs/reviews/2026-09-01-fanout-index.md:116-120`
already says the shell scripts have no tests and that "exercised by hand" is a weaker claim that shipped
`FV2-A1`. The cheapest real check is inside `battery.sh` itself: resolve the reaper's path into a
variable, `[ -f "$REAP" ] || { echo "*** THE REAPER IS NOT AT $REAP - leftover JVMs will not be cleared ***"; }`,
and drop the `2>&1` so a PowerShell failure is visible. The general rule the file already states applies:
*an instruction in a prompt is a request; a check in the tool is a rule* — and a check whose output is
discarded is neither.

**Not verified by running.** I did not execute the script. The claim rests on the path not existing,
which `ls` and `find` settle, and on `$(pwd)` being the project root, which the script's own usage line
settles.

**An observation, not a finding, recorded because it is what this guard exists around.** `git status`
while I was establishing which commit I was reading shows `cs2_sample_layout` modified again — and this
time the track diagram itself is among the files, which it was not when `TCX-A4` was raised:

```
 cs2_sample_layout/config/autonomy/configuration-Main.json | 60 +++++++++---------
 cs2_sample_layout/config/autonomy/setup.json              | 36 +++++++++----
 cs2_sample_layout/config/gleisbilder/1 - Main.cs2         | 22 +++-----
```

I have not touched that folder and I make no claim about who did — `TCX-A4` and `FX2-1` are the record of
how hard that question is to answer, and Adam's ruling there was to restore and re-patch. It is here only
because `TS3-A1` is about a guard that has not run for four days over exactly this folder, and because
`FX2-1` is closed, so a reader could reasonably assume the folder is clean.

---

## B — medium

### TS3-B1 — `testAShutPlainSquareReachesTheRunningGraph`'s control cannot fail

**Disposition: fixed, confirmed 2026-09-05** - cited by the fix at `test/core/testAutonomyDiagramSession.java`.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.** Verified by reading. Added on 2026-09-02 in `1cfdf370` to protect the D24-B5 fix.

`test/core/testAutonomyDiagramSession.java:971-973`:

```java
        assertFalse(inactivePointNames(session).contains(session.pointNameForTile(plain)),
            "control: the square is already inactive in the built graph before anything switched it "
            + "off, so this fixture cannot show the flag arriving");
```

`inactivePointNames` (`:1006-1023`) reads the built configuration's `points` array and collects
`p.getString("name")` — **emitted Point names**. `session.pointNameForTile(plain)` (`AutonomySession.java:2585-2588`)
delegates to `StationIndex.nameOf(tile)`, which is `nameBySquare`, i.e. `builder.uniqueNames()` — the
**base name**.

Those are not the same string whenever the square splits. `AutonomyBuilder.nodeName`
(`src/org/traincontrol/automationui/AutonomyBuilder.java:708-712`):

```java
    private String nodeName(String base, Node node)
    {
        if (node.arrival == null || nodesFor(node.tile).size() == 1) return base;

        String name = base + " (" + heading(node.arrival) + (node.reverse ? ", reverse)" : ")");
```

The fixture is `deadEndRun()` (`:872-887`), a straight run with sensors at `(1,1)`, `(4,1)` and `(7,1)`.
The square under test is the middle one, `(4,1)`, which has track on both sides, so `splitSides`
(`AutonomyBuilder.java:430-447`) returns two arrival sides and `nodesFor` (`:492-552`) emits two nodes —
neither of which is called by the base name. **The built configuration therefore never contains the base
name, whether the square is switched off or not, so this `assertFalse` is satisfied by construction.**

The rest of the test knows this perfectly well: sixteen lines further down it asks the same question the
right way, over `session.pointNamesFor(name)` (`:986-997`). The control is the one place it does not.

**What the control was for, and what now gets through.** It exists to prove the fixture does not ship the
square already inactive — because if it did, the flag would be in the build before `setPointProperty` was
called, and every assertion below would pass without the mutation the test is written against ever
mattering. A fixture edit that switched the square off — or a builder change that emitted
`active: false` for a plain sensor by default — makes the whole test vacuous, and the control that would
have caught it cannot.

**The assertion that is missing.** The same shape as the loop below it:

```java
        for (String copy : session.pointNamesFor(session.pointNameForTile(plain)))
        {
            assertFalse(inactivePointNames(session).contains(copy),
                "control: " + copy + " is already inactive in the built graph before anything switched "
                + "it off, so this fixture cannot show the flag arriving");
        }
```

Reachability of the underlying defect: D24-B5 was *"trains carried on running through the square the
editor draws a cross on"*, which is an A on the layout. The test's live assertions still bite today, so
the fix is not unprotected — what is unprotected is the test's own soundness. B.

---

### TS3-B2 — the audit test leaves `HS alpha` 40 units long and reversible for the other 84 tests

**Disposition: fixed, confirmed 2026-09-05** - cited by the fix at `test/core/testHomeStaging.java`.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.** Verified by reading. Added on 2026-09-02 in `975f157d`.

`test/core/testHomeStaging.java:165-177`:

```java
        Layout layout = load(shortBerth());

        Locomotive tooLong = loc(LOC_A);

        tooLong.setTrainLength(40);
        ...
        tooLong.setReversible(true);
```

There is no `try`/`finally`, no `@AfterMethod` — the class has only `@BeforeClass` (`:58`) and
`@AfterClass` (`:70`) — and no restore anywhere in the method. `loc(name)` is
`model.getLocByName(name)` (`:264-267`), so `tooLong` is one of the three shared
`MarklinControlStation` locomotives that the class's 85 `@Test` methods draw on. `Locomotive.reversible` is a plain `boolean` (`Locomotive.java:111`)
that the MM2 constructor never assigns, so it starts `false`; `trainLength` starts `0`
(`Locomotive.java:159`, `:284`).

**The rule this breaks is written down in the same file, three times, one of them as a helper built for
exactly this.** `testHomeStaging.java:1312-1315`:

```java
        // Point state dies with the layout, which every load() replaces - but locomotive state does not,
        // because load() re-parses the graph and not the database.  Whatever this test changes about the
        // locomotive has to go back even if an assertion below fails, or the next test in the class runs
        // against a locomotive this one quietly left non-reversible.
```

and `:2279-2284`:

```java
    /**
     * Sets reversibility on both test locomotives and returns what they were.
     *
     * Locomotive state outlives load(), which re-parses the graph and not the database, so anything
     * changed here has to be put back exactly - restoring a hardcoded "true" would hand every later
     * test a locomotive this one had quietly made reversible.
     */
    private static boolean[] setReversible(boolean state, String... names)
```

Three existing sites obey it: `:1316-1318`/`:1355-1359`, `:2328`/`:2369` through the helper, and
`:3480`/`:3512`. The new test is the fourth site and the only one that does not.

**What it costs today, honestly.** I could not find a sibling whose result changes. `reversible = true`
only widens what `mustBackIn` permits, and `trainLength = 40` is inert wherever the destination's
`maxTrainLength` is `0` (which every `station()` fixture leaves it) and wherever the path is unmeasured
(`Layout.measuredRoomToReverseInto` returns null unless *every* segment has a length, and `shortBerth()`
is the only fixture in the file that sets any). So this is a landmine rather than a live failure.

It is graded B rather than C for two reasons. The first is that the order is not fixed: TestNG's
within-class ordering is unspecified, and `testAStationKnowsWhichLocomotivesItCouldNeverHold` captures
`wasReversible`/`wasLength` into its own `finally` (`:1316-1317`), so if it runs after the audit test it
adopts `true`/`40` as "the original" and writes them back — the leak survives its own cleanup. The
second is that the next fixture in this file to record an edge length arms it, and the reversal-room
rule is precisely the feature this round added.

**The fix is the helper that already exists**, plus a `finally` restoring the length.

---

### TS3-B3 — `testSwitchingAnAccessoryByHandAsksAboutProtectingSignals` survives its own stated mutation

**Disposition: fixed, confirmed 2026-09-05** - cited by the fix at `test/regression/testEditorSurfaceRules.java`.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.** Verified by reading. Added on 2026-09-02 in `87b6c10a`.

`test/regression/testEditorSurfaceRules.java:622` states the claim:

```
     * MUTATION: deleting either call to `protectsAnOccupiedSquare` fails this.
```

and `:627-633` is the check:

```java
        String label = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/LayoutLabel.java")), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(label.contains("protectsAnOccupiedSquare"),
            "the diagram's accessory tile does not ask whether the accessory is protecting a platform "
            + "with a train at it, so clicking a signal green by hand goes unwarned while a route "
            + "doing the same thing is refused");
```

There are **two** calls in that file, not one — `src/org/traincontrol/gui/LayoutLabel.java:400-405`:

```java
                                        boolean protecting =
                                            tcUI.getModel().getAutoLayout().protectsAnOccupiedSquare(
                                                c.getAccessory())
                                            || (c.getAccessory2() != null
                                                && tcUI.getModel().getAutoLayout()
                                                    .protectsAnOccupiedSquare(c.getAccessory2()));
```

Delete the second limb — the one that asks about `getAccessory2()` — and `label.contains(...)` is still
true, because the first limb still spells the name. So the mutation the javadoc names is only half
caught, and the half it misses is the second address of a two-address signal, which is a case this
repository has a whole regression class about. `test/regression/testBothProtectingSignalsAreThrown.java:23-26`
states the stake: *"They are commanded together and show the same aspect … and a platform guarded at one
end and open at the other is worse than one guarded at neither, because it looks protected."* A
two-address protecting signal whose far head goes unasked is that state, arrived at through the other
door.

**And the check is a whole-file `contains` with the comments left in.** Its sibling forty lines above
(`:585`, `:602`) is careful about this — `withoutComments(bodyOf(ui, "..."))` — and this one is not. The
comment sitting directly above the call, `LayoutLabel.java:398-399`, reads *"Asked of the layout, which
is where the rule lives now, so this and MarklinRoute.heldReason cannot drift apart."* One more sentence
of that kind naming the method, and both calls could be deleted with the test still green. That is
`TCX-A3` exactly — *"a `contains` over a 7,920-line file is a check on the file, not on the method"* —
reintroduced the day after it was fixed.

The same applies to the `MarklinRoute.java` half at `:635-640`: one call, at `MarklinRoute.java:475`, and
a whole-file `contains` over it.

**The assertions that are missing.** `withoutComments(bodyOf(label, "public void mouseClicked("))` — or
whichever handler holds it — and a count rather than a presence, in the shape
`testEditorSurfaceRules.java:1580-1591` already uses for `StationCaption.onPill(`:

```java
        assertEquals(asks, 2,
            "the diagram's accessory tile asks about protecting signals at " + asks + " places, and "
            + "there are two: the accessory and its second address");
```

Severity: the defect that gets through is a green aspect shown by hand, unwarned, at a platform with a
train standing at it, for a two-address signal. That is SVN-B16 half-reverted. B.

---

### TS3-B4 — `testEveryDoorThatRunsARouteAsksIfItIsAlreadyRunning`'s third assertion is an unguarded negative

**Disposition: fixed, confirmed 2026-09-05** - fixed under a different tag - `testEditorSurfaceRules.java:605` now guards the negative with `assertFalse(door.isEmpty(), ...)`, citing `V33-C7`.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.** Verified by reading. Added on 2026-09-02 in `87b6c10a`.

`test/regression/testEditorSurfaceRules.java:602-608`:

```java
        String door = withoutComments(bodyOf(ui,
            "private void RouteListMouseClicked(java.awt.event.MouseEvent evt)"));

        assertFalse(door.contains("routesExecuting"),
            "the route row's own click handler asks whether the route is already running instead of "
            + "leaving it to executeRoute.  One door asking for itself is how the other two came to "
            + "be missing it.  Body: " + door);
```

`bodyOf` returns the empty string when the declaration is not found — `:1058`, *"@return the body, or ""
when the declaration is not there"*, and `:1062-1064`:

```java
        int at = source.indexOf(declaration);

        if (at < 0) return "";
```

`"".contains("routesExecuting")` is false, so **any change that stops the declaration matching leaves
this assertion green while checking nothing.** The declaration it matches is NetBeans-generated
(`TrainControlUI.java:19284`, `private void RouteListMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_RouteListMouseClicked`)
and is derived from the component's name, so renaming `RouteList` in the designer renames the method and
silences this without anyone touching the test.

**This is the only unguarded negative source-text assertion in the file.** I checked every one of the
others, and each is preceded by a positive assertion over the same body, which an empty body fails
loudly: `:1113`/`:1119`, `:1931`/`:1936`, `:2293`/`:2298`, `:2309`/`:2313`, `:2405`/`:2409`. Two are
guarded explicitly — `:1111` (`assertFalse(describe.isEmpty(), ...)`) and `:2487`
(`assertFalse(tidy.isEmpty(), ...)`) — and `testErrorsStopTheSetupRunning.java:207` does the same thing
for the same reason. The convention exists in three places in this suite and the new test is the one
that missed it.

**The assertion that is missing** is one line, in the file's own idiom:

```java
        assertFalse(door.isEmpty(),
            "cannot find RouteListMouseClicked - the assertion below passes on an empty body");
```

Severity: the defect that gets back through is SVN-B7, two threads running one route and each unlocking
what the other locked. The first two assertions in the same test still bite, so the whole guard is not
unprotected — only the "and the door does not ask it again" half. B.

---

### TS3-B5 — `one.sh` does not read the `Configuration Failures` line

**FIXED 2026-09-02 (`3c014e77`).**  `one.sh` reads `Configuration Failures` and calls a non-zero count out on its own line, with the sentence `battery.sh` carries for the same check: the teardowns in this suite put Adam's signals back and clear the auto layout, so one that threw would leave the railway changed and be reported as a clean run.  The same round gave it the skip and zero-test call-outs (`V33-B1`) and a non-zero exit (`V33-C4`).

`docs/tools/one.sh:174` is the whole of its result reading:

```bash
    grep -E "Total tests run|FAILED|java.lang.Assertion|at regression|at core" "$S/one-run.txt"
```

`docs/tools/battery.sh:370-379` says why that is not enough, and its comment is a measurement rather than
a worry:

```bash
    # TestNG's summary is TWO lines, and this read one (found 2026-08-25, by review).
    #
    #     Total tests run: 1, Failures: 0, Skips: 0
    #     Configuration Failures: 1, Skips: 0
    #
    # A class whose @AfterClass throws prints exactly that, and the first line alone says green.  The
    # teardowns in this suite are load-bearing: testBothProtectingSignalsAreThrown puts two of Adam's
    # real signals back to GREEN in its teardown and says why, and testARouteDoesNotThrowSwitchesUnderATrain
    # clears the auto layout in its own.  A teardown that threw would leave the railway changed and be
    # reported as a clean run.
    configFailures=$(echo "$out" | grep 'Configuration Failures' | tail -1)
```

`one.sh`'s pattern does not match `Configuration Failures` — the summary word is `Failures`, and the
pattern's literal is `FAILED`, and `grep -E` is case-sensitive. So the second line is not printed at
all, and the reader sees only `Total tests run: N, Failures: 0, Skips: 0`.

**One caveat I could not settle by reading.** At verbosity 2 and above TestNG also prints a
`FAILED CONFIGURATION:` banner, which *would* match the pattern. `one.sh` sets no `-verbose`, so it runs
at the command-line default, and I did not run it to find out what that prints. The count line is
certainly lost either way, and `battery.sh` — which reads the whole output and was written from a
measurement of it — chose to test the count rather than trust a banner.

**Which class this matters most for is the one that watches the railway.**
`test/regression/testTheGoldenLayoutHoldsTogether.java:143-166` is the write-detector, and it is an
`@AfterClass`:

```java
    @AfterClass
    public void testNothingWroteToTheGoldenLayout() throws Exception
```

It reads `cs2_sample_layout` in place (`:63`, `private static final File GOLDEN = new File("cs2_sample_layout");` —
`TCX-B11`, still open), so if it fires, something has written to Adam's real railway. Run through
`one.sh` — which is the runner used for every single-class iteration, and the one a person reaches for
when validating a fix to that class — **that failure prints nothing and the class reads green.**

This is `TV2-A1` again, one item short. That finding was *"`one.sh` had none of the five concurrency
corrections … because it lived only in a scratch directory where no review could see it."* The file is
in the repository now and got the concurrency five. It did not get the result-classification four, of
which this is the sharpest.

**The fix is the four arms of `battery.sh:381-439`, copied.** The other three — `Total tests run: 0`,
`Skips: N`, and the "no summary" split by heap — are visible to a reader of `one.sh`'s output because the
summary line is printed whole, so they are worth having but are not silent. The `Configuration Failures`
line is silent.

---

### TS3-B6 — the widened start guard has no test, and the test named for the invariant no longer reads it

**Disposition: fixed, confirmed 2026-09-05** - cited by the fix at `src/org/traincontrol/gui/TrainControlUI.java` and `test/regression/testErrorsStopTheSetupRunning.java`.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.** Missing test. Verified by reading.

`87b6c10a` (SVN-B10) changed what the start guard asks. `src/org/traincontrol/gui/TrainControlUI.java:5183`:

```java
        if (!getAutonomySession().hasErrors()) return false;
```

where it previously read `int errors = getAutonomySession().errorCount(); if (errors == 0) return false;`.
`AutonomySession.hasErrors()` is strictly wider (`AutonomySession.java:3572-3575`):

```java
    public boolean hasErrors()
    {
        return hasBlockingProblems() || errorCount() > 0;
    }
```

**The three affordances were not swept.** `TrainControlUI.canStartAutonomy()` (`:20156-20160`):

```java
    public boolean canStartAutonomy()
    {
        return this.startAutonomy != null && this.startAutonomy.isEnabled()
            && autonomyErrorCount() == 0;
    }
```

and `autonomyErrorCount()` (`:20177-20182`) is `session.errorCount()`. `AutonomyOverlayToggle.java:342`
and `LayoutRightclickAutonomyMenu.java:203` read the same number. So the guard now refuses in a state
none of the three affordances can see: `graph != null && graph.hasBlockingProblems()` with
`check()` returning nothing, which is the case `check()`'s own guard produces
(`AutonomySession.java:3405`, `if (graph == null || reducer == null) return new ArrayList<...>();`) and
which the widening was written for — `TrainControlUI.java:5180-5182`, *"It can legitimately be zero while
this refuses: a graph that cannot be BUILT is a blocking problem."*

That is OB-057 and OB-090 in their original shape: a Start button that is offered and then refused.

**Nothing tests the guard.** `grep -rn refuseAutonomyStartWhileBroken test/` finds it only in two prose
comments in `testErrorsStopTheSetupRunning.java` (`:29`, `:182`). The test whose whole name is the
invariant, `testTheAffordancesAskTheGuardsOwnQuestion` (`:198-225`), reads the three affordances and not
the guard — and one of its assertions now enforces the divergence, `:214-216`:

```java
        assertFalse(canStart.contains("hasBlockingProblems()"),
            "canStartAutonomy() is asking hasBlockingProblems() - the narrower, graph-only question "
            + "OB-090 was about, blind to an unnamed station or any other check-only error");
```

The guard's own question now *is* `hasBlockingProblems() || errorCount() > 0`, and the test forbids the
affordance from asking the first half of it.

**Three comments in `src/` are now false as well**, and they are the reasoning the affordances rest on:
`TrainControlUI.java:20165-20167` (*"`refuseAutonomyStartWhileBroken` reads exactly this and refuses when
it is not zero"*), `LayoutRightclickAutonomyMenu.java:181` (*"canStartAutonomy asks
refuseAutonomyStartWhileBroken's own number now"*), and `AutonomyOverlayToggle.java:336`.

**The test that is missing.** In the same idiom as the test beside it: read
`bodyOf(ui, "private boolean refuseAutonomyStartWhileBroken()")` and
`bodyOf(ui, "public boolean canStartAutonomy()")` and assert that the predicate each asks is the same
name. Something of this shape, which fails today:

```java
        assertEquals(guardAsks, affordanceAsks,
            "the door that refuses a broken setup asks " + guardAsks + " and the button that offers to "
            + "start it asks " + affordanceAsks + ", so a setup whose graph will not build shows a live "
            + "Start button that answers every press with a dialog - which is OB-057 and OB-090");
```

Severity: the reachable window is narrow — it needs `graph` non-null with blocking problems while
`reducer` is null, which happens only if `rebuild()` throws between `AutonomySession.java:349` and `:355`
on the first build. I could not construct a caller that reaches it, so this is graded on the missing test
rather than on a demonstrated defect. B rather than A for that reason, and the honest statement is that
the divergence is structural and its reachability is unsettled.

---

### TS3-B7 — the planner's "a longer approach is more room" rule has no test

**Disposition: OPEN, confirmed against the tree 2026-09-05** - unchanged - no test names `measuredRoomToReverseInto` in a staging context; the three matches are in `testAutonomyDiagramReducer` and `testNonReversibleTrains`, which measure the rule rather than the planner's `continue`.

**Audited 2026-09-05.** The `**Status: open**` line below is the original and is kept as the historical record of what was believed when it was written.  It is NOT this finding's disposition.

**Status: open.** Missing test. The rule was added on 2026-09-02 in `975f157d`.

`src/org/traincontrol/automation/HomeStaging.java:1045-1049`:

```java
                    // `continue` rather than a refusal: another route to the same berth may be longer,
                    // and a longer approach is more room.
                    Integer room = Layout.measuredRoomToReverseInto(route, loc);

                    if (room != null && loc.getTrainLength() > room) continue;
```

`continue` and `return null` are the whole difference between "this approach is too short, keep looking"
and "this berth is refused". The commit message says the distinction was *observed* while the test was
being written — *"the planner routed the long way round through an unmeasured leg, which BOTH sides
accept and correctly so, so the fixture measures every edge"* — and the fixture was then changed to
remove it. `shortBerth()` (`testHomeStaging.java:226-249`) measures every edge at 5, and its own comment
says so: *"Measured throughout, every route to HS D is too short for the train."*

So **no test in the suite distinguishes `continue` from `return null` here**, and replacing one with the
other passes everything. What that regression produces is "no plan where one existed" — which is the
failure `HomeStaging.java:591-593` names as the cost of every mis-copied rule, and which is what
`D24-B1` and `SV2-A1` both were: the planner proving something impossible on a stricter rule than the
railway enforces.

**The test that is missing.** The same fixture with two measured routes to the terminus of different
lengths — a direct approach of 5 and a way round of 5 + 5 + 5 — and a train of, say, 12. The direct
route is too short, the long way round is not, and the assertion is:

```java
        assertTrue(layout.planReturnToHome().isPossible(),
            "the planner refused a berth it can reach by a measured approach long enough for the "
            + "train, because a shorter approach to the same berth was tried first.  A leg that is "
            + "too short is a reason to keep looking, not a reason to refuse the berth");
```

with the control being that the same train and a berth reachable *only* by the short route is refused —
which is what the existing test asserts.

---

## C — low

### TS3-C1 — no floor on `copies`, in the test whose comment says the number is the point

**FIXED 2026-09-03.**  `assertTrue(copies.size() >= 2, ...)`, the floor `TCX-B9` put on the same shape 2,800 lines down.  A fixture that emits one copy now fails instead of testing nothing.

`test/core/testAutonomyDiagramSession.java:983-997`:

```java
        // EVERY COPY OF IT, not just one.  A square with track on both sides is emitted once per
        // arrival side, and a train barred from one side and admitted from the other is a square that
        // is half switched off - which is not a state the menu can express or the cross can mean.
        java.util.List<String> copies = session.pointNamesFor(name);

        assertFalse(copies.isEmpty(), "the square emitted no Points at all");

        for (String copy : copies)
        {
            assertTrue(inactivePointNames(session).contains(copy), ...
```

`assertFalse(copies.isEmpty())` is a floor of one where the comment says the case needs two. This is the
shape of `TCX-B9`, and the sibling that `TCX-B9` names was given the right floor **in the same commit**,
2,800 lines further down the same file (`:3822-3826`):

```java
        assertTrue(copies.size() >= 2,
            "precondition: this needs a square that became SEVERAL Points, and it got " + copies.size()
            + " - " + copies + ".  With one copy the loop below compares a name with itself, ...
```

Filed as C rather than B because the fixture does split today — see `TS3-D1` for why I first thought
otherwise. `assertTrue(copies.size() >= 2, ...)` is the one line.

### TS3-C2 — the audit test's preconditions restate what it just set, and its name says the opposite of what it asserts

**FIXED.**  The two restated preconditions went with `V36-B2`/`V37-B2` - what stands there says there is no precondition about `mustBackIn` at all, and rules the confound out with a control.  The method is renamed `testThePlannerAndTheRuntimeAgreeAboutRoomToReverse` (2026-09-03), which is what it asserts.

`test/core/testHomeStaging.java:179-185`:

```java
        assertEquals(tooLong.getTrainLength(), Integer.valueOf(40),
            "precondition: the locomotive has no train length, so the rule under test is not armed "
            + "and this fixture cannot show anything");

        assertTrue(tooLong.isReversible(),
            "precondition: a non-reversible train is refused a terminus by mustBackIn instead, so "
            + "this would pass whether or not the room rule exists");
```

Both values are set by this method, ten and two lines earlier respectively (`:169`, `:177`), through
setters that are bare field writes (`Locomotive.java:1372-1375`, `:1417-1420`). Neither assertion can
fail for any production defect, and both failure messages describe a state the method has just made
impossible — the first says "the locomotive has no train length" about a line that reads
`setTrainLength(40)`. They belong in `TCX-C3`'s table of assertions the fixture guarantees. The two below
them (`:187`, `:190-196`), which read what `parseAuto` made of the JSON, are real fixture checks and
should stay.

Separately, the method is named `testTheAuditSeesTheReversalRoomRuleTheStagingPlannerDoesNotHave` and its
headline assertion is `assertEquals(auditAgainstRuntime(), 0, ...)` — the audit seeing **nothing**,
because since `975f157d` the planner does have the rule. The name and the javadoc's first line describe
the pre-fix defect; a reader scanning method names is told the opposite of what the test enforces. The
sibling naming convention in this file (`testTwoHomesOnOneSquareDoNotBothSurviveTheLoader`) states the
invariant instead.

### TS3-C3 — a stated mutation the fixture cannot tell apart, and which now describes the shipped code

**FIXED 2026-09-03.**  The javadoc names the one mutation this fixture can tell apart, and records why the second was two things wrong: 5 and 3 both refuse a train of 10, and the whole run in IS the rule since Adam's 2026-09-01 ruling.  It points at the sibling that covers the distinction on a three-segment fixture.

`test/core/testNonReversibleTrains.java:260-261`:

```
     * MUTATION: dropping the reversal test refuses nothing; comparing against the whole path rather
     * than the track at the reversal accepts this, because the run in is long enough overall.
```

The fixture is `backingInLayout()` (`:409-424`) and `pathThrough` (`:429-437`) builds its path from
exactly two edges — `BACK_start -> BACK_mid -> BACK_end` — measured at 2 and 3 (`:278-279`), against a
train of 10 (`:281`). "The whole path" is 5 and "the track at the reversal" is 3 — both less
than 10, so the path is refused either way and the second mutation cannot be distinguished by this
fixture at all.

It is also now a description of the shipped implementation rather than of a mutation:
`Layout.measuredRoomToReverseInto` (`Layout.java:6188-6195`) sums every segment, which is Adam's ruling
of 2026-09-01 and is exactly what the sibling test two methods down asserts —
`testTheRoomIsEverySegmentLeadingUpToTheReversal` (`:326-368`), whose own javadoc says *"The room is the
WHOLE run in, not the last stretch of it."* The javadoc at `:260-261` was written before that ruling and
was not revisited. Documentation only; the assertions beneath it are sound and the sibling covers the
distinction properly on a three-segment fixture.

### TS3-C4 — `testErrorsStopTheSetupRunning` says twice that `hasErrors()` has no callers

**FIXED.**  Both copies are gone - the assertion message says what it proves and what covers the other half, and the javadoc was rewritten under `V32-C2`.  Verified 2026-09-03.

`test/regression/testErrorsStopTheSetupRunning.java:90-94`:

```java
        assertTrue(session.hasErrors(),
            "the setup has an error and will not admit it - though see "
            + "testTheAffordancesAskTheGuardsOwnQuestion below: hasErrors() itself has no caller left "
            + "in src/, so this line proves the METHOD works and not that anything offering to start "
            + "autonomy actually asks it (OB-090, DD-A6)");
```

and `:180-182`:

```
     * `AutonomySession.hasErrors()` - the method the test above exercises - has zero callers left in
     * `src/`: `grep -rn hasErrors src/` finds only its own declaration.
```

Both were true when written and both were made false by `87b6c10a`, which is what SVN-B10 *was*. The
grep the javadoc quotes now returns `TrainControlUI.java:5183`. The second is the more misleading,
because it is the stated reason the test reads the affordances rather than the guard — see `TS3-B6`,
which is what that reasoning now leaves uncovered.

### TS3-C5 — `one.sh` runs the tests after a failed compile, through the pipe its own comment warns about

**FIXED 2026-09-02.**  Same line as `V33-C3`, and both halves of it: `javac`'s own status decides, the output goes to a log rather than through a pipe, and a tree that does not compile stops the run with exit 2.

`docs/tools/one.sh:153-154`:

```bash
"${TC_JAVAC:-/c/Program Files/Java/jdk1.8.0_361/bin/javac}" -nowarn -encoding UTF-8 \
    -d "$BUILD" -cp "$CP" @"$S/one-files.txt" 2>&1 | grep -i "error" | head -8
```

Two things. First, the exit status of the pipeline is `head`'s, so a compile that fails does not stop the
script — it goes straight on to the TestNG loop against a partially populated `$BUILD`.
`battery.sh:256-263` refuses instead, and says so:

```bash
    echo "*** THE WORKING TREE DOES NOT COMPILE - nothing was run ***"
```

Second, `| grep | head -8` is the construct this same file documents seven lines below, at `:160-166`:

> `... | grep | head -30` closes the pipe as soon as head has its thirty lines, and the SIGPIPE that
> follows reached back far enough to swallow the NEXT class in the loop

Applied to `javac`, the same closure kills the compiler once it has emitted eight matching lines, which
leaves `$BUILD` holding whatever it had written so far and truncates the diagnosis to eight lines. The
fix was made for the TestNG invocation at `:171-172` and not for the compile twenty lines above it — which is
`docs/reviews/README.md`'s *"when you fix a call site, grep for its twins"*, inside a single file.

Graded C because it only fires on a tree that is already broken, and the errors that do print are
visible; the cost is confusion during a broken build rather than a false green.

### TS3-C6 — a null train length is restored as zero

**FIXED 2026-09-03.**  `loc.setTrainLength(wasLength)` at all three sites: a null length is restored as null, which the model distinguishes from zero.

`test/core/testNonReversibleTrains.java:307` and `:366`:

```java
            loc.setTrainLength(wasLength == null ? 0 : wasLength);
```

`Locomotive.trainLength` is an `Integer` and `Layout`'s guard tests it for null explicitly
(`Layout.java:6180`), so null and zero are distinguishable states in the model. The locomotive here is
`model.getLocByName(model.getLocList().get(0))` — whichever of Adam's real locomotives happens to be
first — and if it had a null length, the test hands it back a zero. Both answers make the rule decline,
so nothing behaves differently today; it is a restore that does not restore.

---

## D — not defects

### TS3-D1 — Withdrawn as a B: the missing floor in `testAShutPlainSquareReachesTheRunningGraph` is latent

**Originally raised as B.** I read the fixture's middle sensor as a plain, non-station square and
expected the arrival split to be gated on station-ness — which would have made `copies.size() == 1` and
the loop degenerate, i.e. `TCX-B9` reintroduced live in the commit that fixed it.

It is not gated. `AutonomyBuilder.splitSides` (`:430-447`) collects an arrival side from every reduced
edge that ends at the tile, with no station test anywhere in it, and `nodesFor` (`:492-552`) emits a plain
copy per side for an unmarked square (`if (!must && (onwards || !canTurn)) out.add(...)`). `StationIndex`
is built from `builder.baseNames()` (`StationIndex.java:76`), which walks `nodesFor` for every square
rather than for every station. So the middle sensor of `deadEndRun()` really does become two Points and
the loop really does check both today. Downgraded to `TS3-C1`.

Worth recording because the same reading is what made me look at `pointNameForTile` in the first place,
which is how `TS3-B1` was found: the square splitting is exactly what makes the control vacuous.

### TS3-D2 — Withdrawn: `Layout.measuredRoomToReverseInto` needs no direct test

Raised as a gap: `975f157d` lifted a rule into a new public static method that `grep` finds no test for,
which is the "extracted rule moves the bug to the call" shape. It is covered behaviourally on both sides.
`testNonReversibleTrains.testATrainTooLongForTheBerthIsNotBackedOverTheSwitch` (`:264-309`) exercises
refusal, acceptance and the unmeasured case with a control for each;
`testTheRoomIsEverySegmentLeadingUpToTheReversal` (`:326-368`) pins the summation over three segments and
the unmeasured-segment case; and the new `testHomeStaging` test covers the planner call site. The one
branch nothing reaches is `loc == null`, which no caller can produce. Not a gap.

### TS3-D3 — Withdrawn: the real-database mutation in `testNonReversibleTrains` cannot persist

`testNonReversibleTrains` takes `model.getLocByName(model.getLocList().get(0))` — at `:59`, `:144`, `:179`,
`:218`, `:268` and `:331`, the first locomotive of whatever database
`init(null, true, false, false, false)` (`:35`) loaded, which on this machine is Adam's own — and sets `reversible` and `trainLength` on it. I expected a persistence path. There is none from a test:
`MarklinControlStation.saveState` (`:1497`) is called only from `TrainControlUI` (`:15864`, `:18367`), on
window close and on an explicit save, and the one test that calls `saveState` is
`testUiStateIsNotLostWhenUnreadable.java:145`, which calls the *window's* overload and writes
`UIState.data`. No shutdown hook exists (`grep -rn addShutdownHook src/` is empty). The mutations are
restored in `finally` and are confined to the JVM. Checked clean, and `TS3-C6` is the only residue.

### TS3-D4 — All five tests strengthened in `1cfdf370` are genuinely fixed

Each was read against the mutation `TCX` said it survived:

- **TCX-B5**, `testAnUnmarkedLayoutIsUntouched` (`testAutonomyDiagramReversal.java:361-388`) — the new
  `assertNotEquals(withTheStationMarked, withoutFeature)` runs first and a builder that ignored
  `withReversibleTiles` now fails it. Real control.
- **TCX-B6**, `badgeAt` (`testAutonomyDiagramMonitor.java:1217-1260`) — the four-argument helper now
  passes `parking = false`, so `testASquareNothingCanUseIsDrawnAsACross` varies `shut` against a fixed
  false `parking`, and `isImpassable() { return parking; }` fails its first assertion. Real.
- **TCX-B8**, `testNothingIsLoadedWhenAlreadyHome` (`testHomeStaging.java:924-943`) — asserts
  `isTimetableSequential()` before and after, which is what the deleted guard actually decides. Real,
  and the "before" assertion is the precondition that makes the "after" one mean something.
- **TCX-B13**, `testFullAutonomyDoesNotDriveThroughAReversingPoint` (`testLayoutPickPath.java:487-496`) —
  the control runs first. `assertNotNull` is weaker than the sibling's
  `assertEquals(destinationOf(...), "FAR")` at `:439`, but `LOOP` is created with `station = false` so
  `FAR` is the only destination on the graph and the two are equivalent here.

The fifth, **TCX-B9** (`testAutonomyDiagramSession.java:3820-3826`), is fixed in the test it names —
moved onto `pageWithATwoEndedStation()` with `assertTrue(copies.size() >= 2, ...)`. The same defect was
introduced 2,800 lines above it in the same commit, which is `TS3-C1`.

### TS3-D5 — `testTwoHomesOnOneSquareDoNotBothSurviveTheLoader` is a well-built test

`8d1c17ca`'s addition (`testHomeStaging.java:3229-3286`). Both of its assertions are two-sided counts —
`assertEquals(homedThere, 1)` and `assertEquals(stillNamed, 1)` — so a loader that dropped *both* homes
fails as loudly as one that kept both, which is the failure mode a `assertFalse(...)` on either side
would have missed. It asserts `isSamePlaceAs` first, so a fixture whose `block` did not parse fails at
the precondition rather than passing vacuously. It touches no shared locomotive state. And it does not
depend on *which* home survives, which is the right thing to be indifferent about.

### TS3-D6 — the battery list and the tree agree exactly

`build.xml` carries 144 `test-one-class` entries; every one names a file that exists, and every `.java`
file under `test/` that contains `@Test` is named by one, except `core/testAutoDetect` — which is the one
documented exclusion in `testEveryTestIsInTheBattery.DELIBERATELY_OUT` (`:40-43`) and is asserted to be
the only one (`:122-126`). The three files in `test/support` carry no `@Test` and are correctly skipped
by that scan. `testTheParkingBerthsGetTheirTrainsBack`, which `TCX-D8` discusses at length, was **renamed** rather than
deleted — `7931e11a` shows it as `R078` to `test/core/testTrainsComeHomeToTheirPlatforms.java` — and the
renamed class is in the battery (`build.xml:270`). So the exclusion list really is back to one, and
`TCX-D8`'s closing observation, that the only end-to-end coverage of backing-in and split-platform facing
on the real railway sat in a file `ant test` does not run, no longer holds. It still opens
`LayoutSandbox.open(frozen)` (`:121`) rather than the live folder, which is the pattern `TCX-B11` asks
`testTheGoldenLayoutHoldsTogether` to adopt, and `TCX-C3`'s entry about
`assertEquals(STARTED_AT.size(), 5, ...)` is still there at `:183`.

`TCX-C5`'s second half still stands and I agree with it: `testTheBatteryRunsEveryTestClass` has no floor
on how many files it scanned, while its sibling at `:228` has `assertTrue(methodsChecked >= 500, ...)`.

### TS3-D7 — `battery.sh`'s result classification is sound

Read arm by arm (`:381-439`). It separates "no summary" from "no heap", counts `Configuration Failures`,
counts `Total tests run: 0` apart from passes, counts `Skips: N` apart from passes, and exits 1 / 2 / 0
accordingly. Each arm carries the incident that produced it. It is the one part of the tooling that
treats "green" and "no failures" as different questions, which is why `TS3-B5` is about the runner that
does not.

One structural note, not a defect: the live-layout fingerprint (`:331`, `:442`, compared at `:455`) is taken around the whole
run, so it can say *something wrote* and never *which class*. That is `TCX-A4`'s unanswerable question
and it is unchanged. The fix for it is `TCX-B11` — copying the golden layout rather than reading it in
place — which is still open.

---

## Where I stand on the still-open `TCX` findings

I re-checked each of these against the tree at `cf048f9b`. I agree with all of them; the notes say what I
confirmed rather than repeating the argument.

| Finding | Stands? | What I checked |
|---|---|---|
| `TCX-B1` — `validateTrainLength` untested at the door | **Yes** | `grep -rn errorTrainLengthTooLong test/` is still empty; the key exists in all eight bundles and is raised at `Layout.java:2296` |
| `TCX-B3` — the guard's reach on the real railway is unmeasured | **Yes** | Nothing in `testTheGoldenLayoutHoldsTogether` counts judgeable paths; unchanged |
| `TCX-B4` — release-before-throw ordering untested | **Yes** | Every `addConfigCommand` in `test/` uses a single setting per edge (`testAutonomyPathValidation.java:122-123`, `:156-157`; `testReturnHomeSequencesAReversal.java:572-573`). No fixture mixes a throw and a release on one edge, so the sort at `Layout.java:2489-2497` is unexercised |
| `TCX-B10` — three unseeded `Random`s in `testRoutes` | **Yes** | `testRoutes.java:49`, `:58`, `:932` are still `new Random()`. Every other generator in the suite is seeded — `testLayoutBfs.java:364`, `testLayoutBfsEquivalence.java:205`, `testReturnHomeOnRealLayout.java:72`, `testTimetableOnDerivedGraph.java:99`. This is the suite's one violation of the fixed-seed rule and it is a one-file fix |
| `TCX-B11` — the golden layout is read in place | **Yes**, and it is now load-bearing for `TS3-B5` too | `testTheGoldenLayoutHoldsTogether.java:63` is unchanged, and its `@AfterClass` detector is the failure `one.sh` cannot print |
| `TCX-B12` — three of its four tests have no floor | **Yes** | `:206`, `:332`, `:360` are still bare `assertTrue(x.isEmpty(), ...)`; only `:227` has `assertFalse(index.isEmpty(), ...)` |
| `TCX-C1`–`C4`, `C6`–`C9` | **Yes** | Spot-checked `C3`'s table (the two entries I re-read, `testLayoutPickPath.java:484` and `testAutonomyDiagramMonitor.java:417-418`, are as described) and `C5` in full |
| `TCX-C5` — annotation scan accepts any `@` | **Yes**, both halves | `testEveryTestIsInTheBattery.java:206-214` is unchanged, and `testTheBatteryRunsEveryTestClass` still has no floor while its sibling at `:228` does |

Two `TCX` entries have gone stale through no fault of theirs and should be read with that in mind:
`TCX-D8` is about a class that has since been renamed (`7931e11a`) and is now in the battery — see
`TS3-D6` — and `TCX-C7`'s third bullet cites
line numbers in `testHomeStaging` that have moved by about 120 lines since `975f157d` and `8d1c17ca`
inserted tests near the top of the file.

**The dispositions I checked, and did not disagree with.** `TCX-A2`'s fix (`975f157d`) does what it says:
the rule is lifted, both sides ask it, and `connected` is deliberately left looser — which is the
distinction `SV2-A1` was about, and it is honoured. `TCX-A3`'s fix and the five `1cfdf370` fixes are
covered under `TS3-D4` and `TS3-C1`. What I found in that commit family is not a wrong disposition; it is
six new findings of the same class — `TS3-B1`, `B2`, `B3`, `B4`, `C1`, `C2` — in the code written to fix
the old ones.

---

## What I did not look at

Said plainly, because a report that implies it covered 145 classes when it sampled is worse than one that
names the corners.

- **Read in full:** `testEditorSurfaceRules`, `testErrorsStopTheSetupRunning`, `testEveryTestIsInTheBattery`,
  `battery.sh`, `one.sh`, `reap.ps1`, and the `@Test`-level neighbourhoods of every change in
  `1cfdf370` / `87b6c10a` / `975f157d` / `8d1c17ca`.
- **Read in part:** `testHomeStaging` (85 tests; I read the fixtures, the lifecycle, and about fifteen
  methods), `testAutonomyDiagramSession` (the two new tests and their fixtures, plus the `TCX-B9` fix),
  `testAutonomyDiagramMonitor` (the badge tests only), `testNonReversibleTrains`, `testLayoutPickPath`,
  `testTheGoldenLayoutHoldsTogether`.
- **Grepped only, not read:** the other ~130 classes. The mechanical sweeps I did run across all of them
  were: every `new Random(`, `Math.random()` and `ThreadLocalRandom`; every `assertFalse(...contains(...))`
  in `test/core` and `test/regression`; every `test-one-class` entry against the tree; and the 233
  `MUTATION:` claims by count, of which I checked eight against the code they name.
- **Not looked at at all:** `test/ui` (the display-dependent classes, 14 of which `TCX` records as
  reporting `Failures: 0` while skipping everything), the fixture data under `test/autonomy_formats` and
  `test/baseline`, `docs/tools/parity/`, and the `@Test`-level content of the regression classes other
  than the four named above.
- **Nothing was run**, so every claim about what a test *would* do under a mutation is a reading. The two
  where that matters most are `TS3-B3` (I am confident the string survives the deletion; I have not seen
  it) and `TS3-A1` (I have not watched PowerShell fail, only established that the file it is given does
  not exist).
