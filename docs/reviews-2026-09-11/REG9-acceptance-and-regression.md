# REG9 - does the battery protect v3.0.0?

**Status:** open

**Prefix for citing these findings elsewhere:** `REG9`

Checked free before use: `grep -rn REG9 docs/ src/ test/` returns nothing outside this file, and
`grep -c REG9 docs/manual-tests/findings.tsv` is `0`.

**Commit reviewed:** `a6180402` - "The locomotive keys work from any part of the window",
2026-09-11 05:31:48 -0700, branch `autonomy-diagram-r0`. The window is `24313004..HEAD`: 1,012
commits, the whole of v3.0.0.

**Date:** 2026-09-11.

---

## The question this pass asked

Not "is this code correct" - six documents dated 2026-09-09 to 2026-09-11 have just read it. The
question here is **if this code broke tomorrow, would anything go red, and would the red thing say
something true.**

So the instrument is mutation, not reading. Eleven production guards were taken back out, one or a few
at a time, in the working tree - plus one reachability probe - and the test classes that claim to
cover them were run against the mutated tree. Every finding below quotes the runner's own stdout.

### What was run

`TC_SCRATCH=<scratch> bash docs/tools/one.sh <classes>`, JDK 8, per class, never `battery.sh`.
**44 class-runs in total**, over 38 distinct classes of the 213 in the battery. The mutations applied,
and the verdict each got:

| mutation | what it restores | classes run | caught? |
|---|---|---|---|
| `MarklinControlStation` prune guard -> `if (false)` | RC-A3: an unreadable page deletes the sensors only it had | 1 | **yes** |
| `pathPreferenceCarried` flag deleted | RC-A8: the routing rule silently reverts to RANDOM | 4 | **yes** |
| `putTheTrainsBack` per-train rule -> `if (false)` | D2-A1/W7-A1: a stale setup placement overwrites the running railway | 4 | **yes** |
| `CARRIED_SETTINGS` loses `maxTrainLength` | IPR-A1: an upgrading user's station capacities | 4 | **yes** |
| `Util.writeAtomically` writes in place | a failed save truncates the target | 6 | **yes** |
| `reconcileFacingWhenIdle` never drains the turns | IND9-B4: a destination turn never reaches the graph | 5 | **yes**, but not by the test that claims it - `REG9-C1` |
| `LayoutPageEdit` rename -> `save()` | MT-135 loss by rename | 6 + probe | **not a live defect** - `REG9-D6` |
| `RouteEditorFrame` -> `isValidAddress` | S14-C1: logical MM2 320 / DCC 2048 refused | 5 | **no** - `REG9-C2` |
| `Layout.executePath` drops the `takingPath` check | one locomotive dispatched onto two paths at once | 7 | **no** - `REG9-A2` |
| `refuseWhileEditorOpen` -> always allow | trains started under an open editor | 14 | **no** - `REG9-A1` |
| `refuseWhileAutonomyRunning` -> always allow | the whole diagram replaced under a running railway | 14 | **no** - `REG9-A1` |

Two whole-tree shape scans were also written and run (a no-assertion `@Test` scan and a
self-comparing-assertion scan) - see `REG9-D9`. **No throwaway TestNG class was written this round**:
every measurement is a mutation of production code plus an existing class, which is a stronger claim
than a probe because it uses the battery as the battery will be used. One test file was edited
temporarily, to raise a ratchet and see what the assertion behind it says - `REG9-B1` - and restored.

### What was NOT covered

Stated plainly, because an implied sweep is worse than a gap:

- **The battery was not run.** 30 classes of 213 were. A mutation that no class in my 30 catches may
  still be caught by one of the other 183; for `REG9-A1` and `REG9-A2` I also grepped the whole of
  `test/` for the names involved and found nothing, which is the second leg of both claims.
- **Nothing on the real railway.** No hands-on test was run, nothing under `cs2_sample_layout/` was
  read into a fixture or written.
- **The Central Station and locomotive layers** (`MarklinFeedback`, `NetworkProxy`, `CSDetect`,
  `MarklinLocomotive`) were left to `W21`, which weighted them deliberately a day ago.
- **The page-link/arrow work** of `T10`, `W21` and `TWV` was not re-measured; `TWV` did that on
  2026-09-11 and I took its results.
- **The message bundles, the timetable and the route capture** were not mutated at all.
- **Concurrency** was measured only as "is the guard asserted", never by racing two threads.

### The tree

Every file mutated was copied to the scratch directory before the first edit and restored from that
copy. At the end, `git status --porcelain` is exactly:

```
 M cs2_sample_layout/config/autonomy/configuration-Main.json
 M cs2_sample_layout/config/autonomy/setup.json
```

- the two lines Adam already had - plus this document, and
`git diff --stat -- src test docs build.xml` is empty. Nothing under `cs2_sample_layout/` was written,
moved, staged or checked out. Nothing was committed. No process was killed.

---

## A - high

| id | title | disposition |
|---|---|---|
| REG9-A1 | the two method-level refusals that stop trains being started over an open editor, and the diagram being replaced under a running railway, are asserted by nothing - both can be disabled outright and 14 classes stay green | open |
| REG9-A2 | the guard that stops one locomotive being dispatched onto two paths at once is asserted by nothing - removed, seven dispatch classes and 179 tests stay green | open |

### REG9-A1 - `refuseWhileEditorOpen` and `refuseWhileAutonomyRunning` have no test of any kind

`src/org/traincontrol/gui/TrainControlUI.java:5837` (`refuseWhileAutonomyRunning`) and `:5858`
(`refuseWhileEditorOpen`). Twenty-three call sites between them - `:7878`, `:7892`, `:19176`,
`:20606`, `:21196`, `:21607`, `:21633`, `:21635`, `:21715`, `:21717`, `:22005`, `:22423`, `:23446`,
`:24198`, `:25015`, `:25017`, `:25336`, `:25342`, `:25435`, `:25437`.

These two methods are the enforcement of the rule `behaviour.md` §6a states in its own words:

> **Nothing that changes the shape of the graph may run while trains are moving or a plan is being
> made.** ... **The greying on the menu is not this guard.** Menu items are greyed when the popup
> *opens* and the action fires when it is *clicked* ... The refusal has to be in the method.

What they stand in front of is the worst of v3.0.0's failure modes, and `38ccbfc8` (2026-08-24) names
them: *"Execute Timetable and Return Home started trains under an open editor"*; *"Switching to the CS
layout, and swapping the layout folder, replace the whole diagram and had no check of any kind -
during a run the diagram goes, `resetAutonomySession` skips its capture BECAUSE trains are running,
the stop controls are removed from the window, and `Layout.runLocomotives` keeps driving."* That
commit changed `src/` only and shipped no test, and none has been written since.

**How I know.**

First, by search: `grep -rn "refuseWhileEditorOpen\|refuseWhileAutonomyRunning" test/` returns
**nothing**. Neither does a search for the doors themselves - `requestReturnToHome`,
`switchCSLayoutMenuItem`, `chooseLocalDataFolder` appear in no test, and every `executeTimetable` hit
under `test/` is `Layout.executeTimetable`, the model method, not the button handler that carries the
refusal. `testEditorSurfaceRules`, the 56-test source-shape guard class that exists for exactly this
surface, asserts about `isLayoutEditorOpen()`'s *implementation* (`:3246`) and about nothing that
calls it.

Second, by mutation. Both guard bodies were made to permit everything:

```java
private boolean refuseWhileEditorOpen()
{
    if (true) return false; // MUTATED: the editor-open refusal never refuses
```

```java
private boolean refuseWhileAutonomyRunning(Component source)
{
    if (true) return false; // MUTATED: the running-layout refusal never refuses
```

Fourteen classes were run against that tree, chosen as everything that plausibly touches the editor,
the autonomy session or the window's state:

```
--- regression.testEditorSurfaceRules
Total tests run: 56, Failures: 0, Skips: 0
--- regression.testEditorSwitchClearsPageState
Total tests run: 8, Failures: 0, Skips: 0
--- regression.testErrorsStopTheSetupRunning
Total tests run: 8, Failures: 0, Skips: 0
--- regression.testLayoutReloadFence
Total tests run: 2, Failures: 0, Skips: 0
--- core.testAutonomyPathValidation
Total tests run: 15, Failures: 0, Skips: 0
--- core.testMaxActiveTrains
Total tests run: 4, Failures: 0, Skips: 0
--- regression.testLocomotiveIdentityPropagates
Total tests run: 13, Failures: 0, Skips: 0
--- regression.testAutoLayoutRace
Total tests run: 6, Failures: 0, Skips: 0
--- regression.testTheEditorTellsAutonomy
Total tests run: 5, Failures: 0, Skips: 0
--- regression.testEscapeClosesTheEditor
Total tests run: 5, Failures: 0, Skips: 0
--- regression.testTheAutonomyEditorKnowsWhichSquare
Total tests run: 3, Failures: 0, Skips: 0
--- regression.testTheMenusComeBackAtOneMoment
Total tests run: 2, Failures: 0, Skips: 0
--- ui.testBusyDialogInteraction
Total tests run: 7, Failures: 0, Skips: 0
```

(`regression.testSwitchingToACentralStationLayout` was the fourteenth and reported `Failures: 1` - the
same failure it reports on the unmutated tree, `REG9-B1`, and not this.)

**What I would change.** A source-shape guard would be cheap and would be worth having: assert that
each of the four doors named in `38ccbfc8` still begins with the call, the way
`testLocomotiveAddressRules.testTheDialogAsksTheRuleRatherThanRestatingIt` does for `AddLocomotive` -
and that guard's own javadoc gives the reason ("`grep -rn AddLocomotive test/` before this method
found nothing at all: the dialog had no test of its own"). Better, and only a little dearer, is a
behavioural test for one door of each kind: the suite already builds a real `LayoutEditor` in eight
classes and a real window in 34, so `requestReturnToHome()` with an editor open, asserting that
`Layout.isRunning()` is still false, needs no new machinery. The value of doing both is that the
shape guard catches a deleted call and the behavioural one catches a guard that stops guarding.

### REG9-A2 - nothing asserts that a locomotive cannot be dispatched twice

`src/org/traincontrol/automation/Layout.java:6279`.

```java
if (this.takingPath.containsKey(loc))
{
    this.control.logf("autolayout.errorLocomotiveBusy", loc.getName());
    return false;
}
```

Added by `38ccbfc8`, `src/`-only, no test. Its own comment states the case: `activeLocomotives` is
joined only *after* `configureAndLockPath` returns, and that call throws every turnout and signal on
the path with a wait between each - so for seconds the check above this one answers "not busy", and a
second dispatch of the same locomotive passed. *"Both threads then drive one physical train and each
one's completion unlocks points the other is still relying on."* Two gestures a couple of seconds
apart reach it.

**How I know.**

By search: `grep -rn "errorLocomotiveBusy" test/` returns nothing, and no test in the suite dispatches
one locomotive twice - the three `takingPath` mentions under `test/` are all prose in javadoc about
the train cap and about lock cleanup.

By mutation:

```java
if (false && this.takingPath.containsKey(loc)) // MUTATED: double dispatch allowed
```

and then every class that reaches `executePath`:

```
--- core.testAutoLayout
Total tests run: 33, Failures: 0, Skips: 0
--- core.testLayoutPickPath
Total tests run: 22, Failures: 0, Skips: 0
--- regression.testARouteDoesNotThrowSwitchesUnderATrain
Total tests run: 14, Failures: 0, Skips: 0
--- regression.testBothProtectingSignalsAreThrown
Total tests run: 8, Failures: 0, Skips: 0
--- core.testAutonomySimulationSanity
Total tests run: 6, Failures: 0, Skips: 0
--- core.testHomeStaging
Total tests run: 92, Failures: 0, Skips: 0
--- core.testMaxActiveTrains
Total tests run: 4, Failures: 0, Skips: 0
```

179 tests, nothing red.

**Why it is A rather than B.** The other guards in this method are covered - `testAutonomyPathValidation`
has an explicit test that a path naming a missing accessory never releases the locomotive - so the
gap is specifically the one clause whose failure puts two commanders on one train.

**What I would change.** The race itself is not the thing to test; the predicate is. `takingPath` is
private, but `executePath` is public and `configureAndLockPath` is the slow part: a test that starts
one `executePath` on a daemon thread, waits until the path's first edge reports a lock held (the
pattern `testAutonomyPathValidation.testTwoPathsHoldingOneCrossingBothHaveToLetGo` already uses at
`:252-262`), and then asserts that a second `executePath` for the same locomotive returns `false`,
is deterministic and needs nothing new. Its control is the same call for a *different* locomotive,
which must return `true`.

---

## B - medium

| id | title | disposition |
|---|---|---|
| REG9-B1 | the battery is RED at `HEAD`, and the assertion that is red sits in front of the live-railway guard in the same test, so that guard does not run while it is red | open |
| REG9-B2 | the `LayoutSandbox` marker that keeps a killed test JVM from leaving the operator's layout preference on a fixture has no test | open |

### REG9-B1 - `testSwitchingToACentralStationLayout` fails on the tree as committed

`test/regression/testSwitchingToACentralStationLayout.java:741`, `testNoTestOpensTheOperatorsRailway`.

**How I know.** Run on the clean working tree, with every mutation of mine restored and
`git diff --stat -- src test docs build.xml` empty:

```
--- regression.testSwitchingToACentralStationLayout
Total tests run: 12, Failures: 1, Skips: 0

*** 1 of the classes above did not come back clean ***
```

and from `testng-results.xml`:

```
34 test classes were found to build a window, not the 32 there were when this was pinned. Fewer
means the pattern has gone stale and is checking less than it thinks; more means a new class
builds a window and this line wants updating expected [32] but found [34]
```

The brief for this pass says the battery is green at 211 classes, 0 failures. It is not. `TWV-B2`
reported this class red at `abbed984` for a *different* test in it
(`testEverySandboxIsOpenedInsideATry`); that one was fixed, and the class has been red ever since for
this one instead. The ratchet was last set to 32 at `a281e3a2`; three test classes have been added
since (`testAPlacedTrainRecordsWhereItCameFrom`, `testTheArrowsKeepTheirAim`,
`testTheKeyMapReachesTheWholeWindow`) across four commits, and none of the four updated it.

**The second half is what makes it a B rather than a C.** The count assertion is at `:741`. The
assertion that actually protects Adam's railway - `assertEquals(offenders.toString(), "[]", ...)`,
the one that names a class building a window with no sandbox, or with the sandbox opened too late - is
at `:818`, in the same method, *after* it. TestNG stops at the first failure, so while the ratchet is
stale the offender check does not execute at all. The class reads as "1 failure, a number wants
bumping" and is in fact running one of its two checks.

**How I know that too.** I copied the file to the scratch directory, changed the single literal `32`
to `34`, and ran it:

```
--- regression.testSwitchingToACentralStationLayout
Total tests run: 12, Failures: 0, Skips: 0
```

so the offender check does pass once it is reached, and the two new window-building classes are
correctly sandboxed. The file was restored from the copy; `git diff --stat -- test` is empty.

**What I would change.** Bump the number to 34 and add the two-line note the comment block above it
asks every author for. Then move the count assertion *below* the offender assertion, and say why: the
ratchet is a staleness detector and the offender list is the safety check, and a staleness detector
that pre-empts a safety check is worse than no ratchet. The same shape should be checked for in the
other ratcheted tests in that class.

### REG9-B2 - the sandbox repair written after the live railway was damaged is itself untested

`test/support/LayoutSandbox.java:96-232`.

`6fe9ec38` (2026-09-11) added a marker file, a repair that runs at the next sandbox open, and a
shutdown hook, because two killed test JVMs had left the machine-global layout preference naming a
temp fixture: *"The same key is the one the APPLICATION reads, so Adam's own window would have opened
that folder too."* Ninety-seven test classes depend on that machinery being right.

**How I know.** Reading, not running - stated as the SOP asks. `grep -rn "LayoutSandbox" test/` gives
97 files, all of them *users*; nothing names `markerFile`, the repair or the hook, and
`testSwitchingToACentralStationLayout`'s two sandbox guards check the *call shape at the call sites*
(`open` inside a `try`, before the model), not the sandbox's own behaviour. I did not mutate it,
because a mutation that mis-handles the preference key is the one class of mutation that can damage
the thing this whole review is forbidden to touch.

**What I would change.** A test that does not go near the real preference: give `LayoutSandbox` a
seam for the node it writes (or test the marker read/write pair directly), then assert the three
claims its javadoc makes - the marker is written *before* the preference is taken, a second sandbox
does not overwrite an existing marker, and a repair restores the recorded value and deletes the
marker. All three are pure file and string work.

---

## C - low

| id | title | disposition |
|---|---|---|
| REG9-C1 | `testTheBaselineIsLevelledWhenTheRailwayGoesIdle`'s ordering assertion compares whole-file offsets and cannot fail; the rule it claims is covered by a different class | open |
| REG9-C2 | the route editor's logical-address rule (S14-C1) has no regression test | open |

### REG9-C1 - an ordering assertion measured across the whole file

`test/ui/testADirectionChangeIsNotSwallowed.java:126-137`.

```java
int levels = ui.indexOf("lastSeenDirection.put(loc.getName(), loc.goingForward())");
int writes = ui.indexOf("takeReversalsOnArrival()");

assertTrue(writes > 0 && writes < levels,
    "the reversals the railway made at a destination are not written to the graph before the"
    + " baseline is levelled - so the levelling wipes the only record of them, which is the"
    + " defect IND9-B4 fixed");
```

`ui` is the whole of `TrainControlUI.java`. `takeReversalsOnArrival()` appears twice in it: at `:5959`
inside `takeThePendingTurns`, which has nothing to do with this rule, and at `:6840` inside
`reconcileFacingWhenIdle`, which is the one the assertion is about. `indexOf` returns the first, so
`writes` is 5959 and `levels` is 6899, and the comparison is satisfied by two lines in two different
methods. The ordering *inside* `reconcileFacingWhenIdle` - which is the whole claim - is never looked
at.

**How I know.** By mutation. The drain inside `reconcileFacingWhenIdle` was replaced so that no
destination turn ever reaches the graph at all - strictly worse than the ordering defect the
assertion describes:

```java
java.util.Map<String, String> turnedRound = new java.util.HashMap<>(); // MUTATED
```

```
--- ui.testADirectionChangeIsNotSwallowed
Total tests run: 3, Failures: 0, Skips: 0
--- regression.testTheTurnAtTheDestinationReachesTheDiagram
Total tests run: 4, Failures: 2, Skips: 0
```

So the assertion is vacuous, **and the behaviour is covered** - by
`testTheTurnAtTheDestinationReachesTheDiagram`, which drives it rather than reading it. That is why
this is a C: nothing is unprotected, but one of the three assertions in that class reads as protection
and is not, and its class javadoc cites it as the thing that stops `reconcileFacingWhenIdle` being
deleted.

**What I would change.** Scope the search to the method, the way
`testTheRunningGuardComesBeforeTheRecording` in the same file already does for
`followDirectionChanges` - take `bodyOf(ui, "public void reconcileFacingWhenIdle()")` and index
within that. Two lines. While there, the assertion that `reconcileFacingWhenIdle` still exists
(`:120`) should note that the behaviour is really pinned by
`testTheTurnAtTheDestinationReachesTheDiagram`, so a later reader does not trust this file for more
than it gives.

### REG9-C2 - the top address of each protocol is refused or accepted by nothing under test

`src/org/traincontrol/base/Accessory.java:53` (`isValidLogicalAddress`) and
`src/org/traincontrol/gui/RouteEditorFrame.java:2445`.

`0f46aff4` fixed S14-C1 - the route editor checked a *logical* address against a *raw* maximum, so
logical MM2 320 and logical DCC 2048 were refused there and accepted by the diagram editor - and
shipped no test. `grep -rn "isValidLogicalAddress" test/` returns nothing.

**How I know.** By mutation, putting the defect back verbatim:

```java
if (address <= 0 || !Accessory.isValidAddress(address, speaks)) // MUTATED
```

```
--- ui.testRouteEditorValidation
Total tests run: 6, Failures: 0, Skips: 0
--- regression.testRouteEditorRoundTripCases
Total tests run: 4, Failures: 0, Skips: 0
--- core.testAccessory
Total tests run: 14, Failures: 0, Skips: 0
```

(`core.testAccessory` does test 320 and 2048, at `:370-390` - but through `MarklinAccessory`, which
converts. That is the two-callers-disagreeing shape the fix was written for, restated in the suite.)

**Why C.** Two addresses out of 320 and 2048 respectively, and the direction of the failure is an
over-strict refusal rather than a wrong command - but Adam's standing preference is that he would
rather have no check than one that refuses something legal, so the regression is one he would notice
and mind.

**What I would change.** Four assertions on `Accessory.isValidLogicalAddress` - 1 and MAX+1 accepted,
0 and MAX+2 refused, for MM2 and DCC - plus one call-site assertion that `addressProblem` adds no
complaint for 320/MM2, following `testLocomotiveAddressRules`' own split between the rule and the
door that asks it. That file's class javadoc already argues for exactly this pairing.

---

## D - not defects

| id | title | disposition |
|---|---|---|
| REG9-D1 | RC-A3, the unreadable-page sensor prune | covered - mutation red |
| REG9-D2 | RC-A8/RC-A2, the routing preference migration | covered - mutation red |
| REG9-D3 | D2-A1/W7-A1, the per-train placement rule | covered - mutation red |
| REG9-D4 | IPR-A1, the settings a legacy import carries | covered - mutation red |
| REG9-D5 | `Util.writeAtomically` | covered - mutation red |
| REG9-D6 | MT-135 by rename, `saveWithoutReconciling` | covered; the mutation is not a live defect |
| REG9-D7 | IND9-B4, the destination turn reaching the graph | covered, by a different class than the one that claims it |
| REG9-D8 | the battery's class list is complete | clean |
| REG9-D9 | whole-tree scan for `@Test` methods that cannot fail | clean |
| REG9-D10 | the one `@Test(enabled = false)` in the suite | deliberate, documented, and an open product defect |
| REG9-D11 | 45 manual tests are outstanding - the acceptance surface the battery does not reach | not a defect; for the acceptance decision |
| REG9-D12 | what this pass did not reach | - |

### REG9-D1 - the unreadable-page prune is protected

`MarklinControlStation.java:868` was changed from `if (couldNotBeRead > 0)` to `if (false)`, which
restores RC-A3: a five-page folder whose third page will not read loads four pages and permanently
deletes every sensor that only appeared on the third.

```
--- regression.testLayoutFolderRobustness
Total tests run: 8, Failures: 1, Skips: 0
```

The class also carries the control - `testAFolderThatReadsCompletelyStillPrunes` - without which the
fix would be "never prune anything". This is the best-protected data-loss path I measured.

### REG9-D2 - the routing preference migration is protected

`if (this.pathPreferenceCarried) return;` (RC-A8) was deleted from `migrateStoredPathPreference`.

```
--- regression.testTheRoutingChoiceSurvivesTheUpgrade
Total tests run: 6, Failures: 1, Skips: 0
```

### REG9-D3 - the per-train placement rule is protected

`putTheTrainsBack`'s `placementsJustEdited == null || !placementsJustEdited.contains(...)` was forced
to `if (false)`, restoring "the setup always wins" - the regression two independent reviews found in
MT-337.

```
--- regression.testAnEditedPlacementSurvivesTheRebuild
Total tests run: 3, Failures: 2, Skips: 0
```

Two of three, which is what the commit message for `74015c16` predicts: the MT-337 test and the
D2-A1 test differ by exactly the thing the code could not previously distinguish, and neither passes
under the other's rule.

### REG9-D4 - the settings a legacy import carries are protected

`"maxTrainLength"` was removed from `CARRIED_SETTINGS`.

```
--- core.testAutonomyDiagramSession
Total tests run: 124, Failures: 1, Skips: 0
```

`regression.testOneChangeSticks` stayed green, which is right - it asks a different question.

### REG9-D5 - the atomic write is protected

`Util.writeAtomically` was made to write straight to the target (`File staging = target;`), so a
failed write truncates the file it was saving.

```
--- core.testAtomicWrite
Total tests run: 7, Failures: 2, Skips: 0
```

### REG9-D6 - the rename path: the test binds, and the mutation is not a live defect

`LayoutPageEdit.java:315` `session.saveWithoutReconciling()` was changed to `session.save()`, which is
the MT-135-by-rename shape verbatim. Six classes stayed green, including the two written for this
path. That looked like a hole, so I asked the sharper question first - **is the line even reached** -
by replacing it with an unchecked throw:

```java
if (true) throw new RuntimeException("REG9 reachability probe"); // MUTATED
```

```
--- regression.testRenameRoundTripThroughTheUIPath
Total tests run: 3, Failures: 3, Skips: 0
--- regression.testARunSurvivesAPageRename
Total tests run: 2, Failures: 1, Skips: 0
```

So the tests execute that exact line, and `testRenameRoundTripThroughTheUIPath` asserts precisely the
loss - `assertEquals(settingsOn(after, renamed, ...), before, "the renamed page did not bring its
settings with it. This is the MT-135 loss...")` - with a precondition that the page has square
settings to lose. Its passing under `save()` is therefore a positive measurement that `save()` does
not prune here, not an absence of coverage. **I did not determine which guard does that work** - the
likeliest is `pagesSafeToJudge()` declining because the store has been rekeyed to a name the loaded
pages do not carry - so `saveWithoutReconciling` at this site is defence in depth rather than the
load-bearing fix. That is worth knowing and is not a defect.

### REG9-D7 - the destination turn reaching the graph is covered

See `REG9-C1` for the measurement. The rule holds; only one assertion about it is vacuous.

### REG9-D8 - every test class is in the battery

`build.xml` carries 213 `test-one-class` entries. Comparing them against every `test*.java` under
`test/`:

```
--- in test/ not in build.xml ---
MISSING: testAutoDetect
```

which is the one deliberate exclusion, and `build.xml:98-100` says why (it probes the network for a
real Central Station at a hardcoded address). `regression.testEveryTestIsInTheBattery` enforces this
and is itself in the list.

### REG9-D9 - the cannot-fail scan came back clean

A shape scan over all 221 files in `test/` for `@Test` methods with no assertion at all, and for
`assertEquals`/`assertSame`/`assertNotEquals` whose two arguments are syntactically identical:

- **0 tautological comparisons.**
- **12 `@Test` methods with no assertion in their own body**, every one of which delegates to a helper
  in the same class that asserts - checked by hand:
  `testACurvedPlatformRecordsASideTheBuildUses` (4), `testTimetableOnDerivedGraph`,
  `testAMovedTileCarriesItsSetup` (2), `testStationLabelsFollowMoves` (3),
  `testTheDiagramRefreshDoesNotWaitOnTheRailway`, `testDiagramLooksRight`.

This repeats a scan `W21` ran a day earlier and agrees with it. It is a shape scan and knows only what
it lists: the one cannot-fail assertion I did find - `REG9-C1` - has a perfectly ordinary shape and
was found by mutation, not by this.

### REG9-D10 - the disabled test is honest, and the bug behind it is open

`test/core/testAutonomyDiagramSession.java:3679`,
`testALegacyImportDoesNotReExcludeAPageTheOperatorTurnedBackOn`, `@Test(enabled = false)`. It is the
only disabled test in the suite. Its javadoc says why in full: it encodes what
`testRunningAgainOverASettledSetupChangesNothing` promises rather than what the code does, and
enabling it is the reproduction of TST-B15. `docs/manual-tests/findings.tsv:1961` still carries
TST-B15 with disposition `open`.

That is the right way to park a known defect, and it is not a test defect. It is worth Adam seeing
before acceptance that **a legacy import silently re-excludes a page the operator deliberately turned
back on**, and that this is known and unfixed.

### REG9-D11 - 45 manual tests are outstanding

Counted from `docs/manual-tests/tests.md`: 362 entries, 285 `fixed validated`, 32 `superseded`,
**33 `needs test`** and **12 `fixed unvalidated`**. The twelve are the ones whose code has moved since
their last run, so their previous result no longer stands - and three of them are A-class behaviour:

- MT-141, editing a placement while trains are out;
- MT-247, a refused route cutting power at the two human doors;
- MT-337, a train moved by hand staying put when a home changes.

This is not a defect and not a criticism of the battery - these need the railway. It is the shape of
the acceptance surface: the battery can be green and 45 hands-on questions still unanswered.

### REG9-D12 - what this pass did not reach

Repeated here so it sits with the findings rather than only in the header: 30 classes of 213 were run;
`battery.sh` was not; nothing hands-on was done; the Central Station and locomotive layers, the
message bundles, the timetable and the route capture were not mutated; and concurrency was measured as
"is the guard asserted", never by racing threads. `REG9-A1` and `REG9-A2` each rest on a mutation
sweep **and** on a whole-tree grep for the names involved; every other "no" above rests on the sweep
alone and could in principle be caught by a class I did not run.

---

## What I would look at first, for acceptance

1. **`REG9-B1`** - the battery is red. It is a one-line bump, but until it is done the release cannot
   truthfully be said to have a green battery, and the guard hiding behind that red line is the one
   that protects the live railway.
2. **`REG9-A1`** - twenty-three doors, one of them "replace the whole diagram while trains are
   running", and not one assertion. The cheapest half (a source-shape guard over the four doors) is an
   hour.
3. **`REG9-A2`** - the one clause in `executePath` whose failure puts two commanders on one train.
