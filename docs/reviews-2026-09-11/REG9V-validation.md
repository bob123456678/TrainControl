# REG9V - validation of REG9, the acceptance and regression pass

**Status:** open

**Prefix for citing these findings elsewhere:** `REG9V`

Checked free before use: `grep -rn REG9V docs/ src/ test/` returns nothing outside this file, and
`grep -c REG9V docs/manual-tests/findings.tsv` is `0`.

**Document validated:** `docs/reviews-2026-09-11/REG9-acceptance-and-regression.md`, 18 findings
(2 A, 2 B, 2 C, 12 D).

**Commit validated at:** `a6180402`, branch `autonomy-diagram-r0` - the same commit REG9 names.

**Date:** 2026-09-11.

---

## How this pass was run

REG9's instrument is mutation, so this pass re-applied every mutation it claims and ran the classes
again, plus - for the findings that turn on "nothing catches this" - classes REG9 did not run. Where
REG9 quotes only a pass/fail count, this pass read `testng-results.xml` and checked **which** assertion
fired and **what its message says**, because a class can go red for a reason that has nothing to do
with the mutation.

**62 distinct test classes** were run, by `TC_SCRATCH=<scratch> bash docs/tools/one.sh`, JDK 8, one
class per JVM. `battery.sh` was not run. Every mutated file was copied to the scratch directory before
the first edit and restored from that copy; the restores are byte-exact (`git diff --stat -- src test
docs build.xml` is empty).

Two whole-tree scans were written: a re-run of REG9's own cannot-fail scan, widened, and a new scan for
the defect shape `REG9-C1` describes. Neither wrote to the repository.

---

## Verdicts

| id | verdict | evidence |
|---|---|---|
| REG9-A1 | **CONFIRMED** (stronger than stated) | both guards disabled together; 8 classes / 87 tests green, 3 of them classes REG9 did not run. One number is wrong: 20 call sites, not 23. |
| REG9-A2 | **CONFIRMED** (stronger than stated) | guard removed; 8 classes / 109 tests green, 4 of them classes REG9 did not run. REG9 missed the identical sibling guard nine lines above it - `REG9V-A1`. |
| REG9-B1 | **CONFIRMED, mechanism understated and the prescribed fix does not work** | red at `:741`, `expected [32] but found [34]`. But **four** assertions are shadowed, not one, and moving the count below the offender check leaves three ratchets still in front of it - measured, `REG9V-B2`. |
| REG9-B2 | **CONFIRMED** | `grep -rn "markerFile\|addShutdownHook\|repair(" test/` returns nothing; 97 files use `LayoutSandbox` and all are users. Claimed from reading and correctly labelled as such. |
| REG9-C1 | **CONFIRMED**, mechanism exact | offsets measured: `writes` = 286525 (line 5959, inside `takeThePendingTurns()`), `levels` = 340773 (line 6899); `reconcileFacingWhenIdle` opens at line 6813. Drain mutation: claiming class green 3/3, `testTheTurnAtTheDestinationReachesTheDiagram` 2 failures. |
| REG9-C2 | **CONFIRMED** (stronger than stated) | defect restored verbatim; 10 classes / 85 tests green, against REG9's 3 / 24. |
| REG9-D1 | **CONFIRMED** | `testAnUnreadablePageDoesNotDeleteTheSensorsOnlyItHad` fails at `:230`, message names RC-A3 and the deleted sensor. |
| REG9-D2 | **CONFIRMED** | `testTheOldChoiceIsKeptUntilSomethingCanStoreIt` fails at `:333`, `expected [SHORTEST_LENGTH] but found [null]`. |
| REG9-D3 | **CONFIRMED**, but the body text names the wrong regression | `if (false)` reddens the two OB-183 tests; MT-337 is reddened by the **opposite** mutation. REG9's summary table is right and its D3 prose contradicts it - `REG9V-C2`. |
| REG9-D4 | **CONFIRMED** | `testLegacyNamesLandOnTheSquaresCarryingTheirSensors` fails at `:2731`, `expected [240] but found [null]`, message names the station length limit. |
| REG9-D5 | **CONFIRMED** | both tests red, but by `NoSuchFileException` in a helper, not by their own assertion - `REG9V-D1`. |
| REG9-D6 | **CONFIRMED**, and its open question is now closed | reachability probe reproduces (3/3 and 1/2). Forcing the reconcile to proceed under `save()` destroys 81 of 83 settings, so REG9's guess was right: `pagesSafeToJudge()` is what holds. |
| REG9-D7 | **CONFIRMED** | same measurement as C1. |
| REG9-D8 | **CONFIRMED exactly** | 213 `test-one-class` entries, 0 duplicates, 214 `test*.java` on disk, one missing: `testAutoDetect`. No orphans. |
| REG9-D9 | **CONFIRMED exactly** | 221 files, 1758 `@Test` methods, **0** self-comparing assertions, **12** assertion-free bodies - the same 12, in the same 6 classes. |
| REG9-D10 | **CONFIRMED exactly** | the only `@Test(enabled = false)` in the suite, at `testAutonomyDiagramSession.java:3679`; `findings.tsv:1961` carries TST-B15 disposition `open`. |
| REG9-D11 | **CONFIRMED exactly** | 362 entries; 285 `fixed validated`, 32 `superseded`, 33 `needs test`, 12 `fixed unvalidated`. MT-141, MT-247 and MT-337 are all among the 12. |
| REG9-D12 | **NOT A DEFECT**, but internally inconsistent | the document states its own coverage three different ways - `REG9V-C1`. |

**18 of 18 survive as findings.** None died. Two carry a mechanism error that changes what should be
done about them (`REG9-B1`, `REG9-D3`), and one headline count is wrong (`REG9-A1`).

---

## A - high

| id | title | disposition |
|---|---|---|
| REG9V-A1 | the dispatch guard nine lines above the one REG9-A2 found is equally unasserted, shares its message key, and is the case an operator reaches without any race at all | open |

### REG9V-A1 - `activeLocomotives.containsKey(loc)` has no test either

`src/org/traincontrol/automation/Layout.java:6257`, in `executePathInternal`:

```java
if (this.activeLocomotives.containsKey(loc))
{
    this.control.logf("autolayout.errorLocomotiveBusy", loc.getName());
    return false;
}
```

This is the refusal for a locomotive that is **already running**. `REG9-A2`'s guard, at `:6279`, is
the refusal for one that is **already being dispatched**. They sit nine lines apart, they log the same
message key, and REG9 measured only the second.

**How I know.** By mutation, `if (false && this.activeLocomotives.containsKey(loc))`, then:

```
--- core.testAutoLayout                  Total tests run: 33, Failures: 0, Skips: 0
--- core.testLayoutPickPath              Total tests run: 22, Failures: 0, Skips: 0
--- core.testMaxActiveTrains             Total tests run:  4, Failures: 0, Skips: 0
--- core.testAutonomyPathValidation      Total tests run: 15, Failures: 0, Skips: 0
--- regression.testLayoutReloadFence     Total tests run:  2, Failures: 0, Skips: 0
--- regression.testAutoLayoutRace        Total tests run:  6, Failures: 0, Skips: 0
--- core.testHomeStaging                 Total tests run: 92, Failures: 0, Skips: 0
--- core.testAutonomySimulationSanity    Total tests run:  6, Failures: 0, Skips: 0
```

180 tests, nothing red. By search: `grep -rn "errorLocomotiveBusy" test/` returns nothing, and
`autolayout.errorLocomotiveBusy` resolves to `Locomotive {0} is currently busy`
(`src/org/traincontrol/resources/messages.properties:223`), which also appears nowhere under `test/`.
Because **both** guards log that one key, no assertion on the log text could ever tell them apart even
if one existed - which is worth knowing before the test for `REG9-A2` gets written.

**Why this is the sharper half of the pair.** `REG9-A2`'s guard can only fire while another thread is
inside `configureAndLockPath`, so its test has to be concurrent by construction. This one is the plain
sequential case - dispatch a locomotive that is already out - and needs no threads at all.

**What I did not determine.** Whether another guard masks it in practice. `:6287`
(`!loc.equals(start.getCurrentLocomotive())`) would catch a train that has left its start point but not
one still standing there. I did not build the fixture to find out, and the test that closes this
finding should assert the refusal rather than assume the path is open.

**Severity note.** This and `REG9-A2` are graded A because of what the guard protects, not because the
product is wrong today - the guards are present and correct at `HEAD`. Under
`docs/reviews/README.md`'s ladder ("wrong behaviour on the layout, or data silently lost") a coverage
gap is not itself an A. REG9 applies the same convention without stating it; both documents should say
so, because an A that no operator can hit reads differently in an acceptance decision.

---

## B - medium

| id | title | disposition |
|---|---|---|
| REG9V-B1 | a SECOND class is red at `HEAD`, from the same commit, and REG9 did not find it | open |
| REG9V-B2 | REG9-B1's prescribed fix does not restore the check it is trying to restore - three more ratchets sit between them | open |

### REG9V-B1 - `testEveryScenarioIsUsedAndSaysSo` is also red on the clean tree

REG9-B1's headline is that the battery is red at `HEAD`. It is, and by more than the one class REG9
names. I ran 26 whole-tree-scanning classes - chosen because they are the ones whose greenness depends
on the shape of the tree, which is the failure mode `REG9-B1` describes - and found a second:

```
--- regression.testEveryScenarioIsUsedAndSaysSo
Total tests run: 3, Failures: 1, Skips: 0
```

`testEveryReadmeNamesExactlyItsUsers`, at `testEveryScenarioIsUsedAndSaysSo.java:154`:

```
1 scenario README(s) disagree with the test sources about who uses them. The list under "## Used by"
is maintained by hand and checked here, because it changes when a DIFFERENT file changes and so goes
stale without anybody touching it: [live-snapshot: ... (listed and not a user: []; a user and not
listed: [regression.testAPlacedTrainRecordsWhereItCameFrom])]
```

**It is the same cause as `REG9-B1`.** `testAPlacedTrainRecordsWhereItCameFrom` is one of the three
classes REG9 itself identifies as having arrived since the window ratchet was pinned at `a281e3a2`.
The commit that added it updated neither the ratchet nor the scenario README, and REG9 found one of
the two consequences because it happened to run the class that reports it.

**What that says about `REG9-B1` as a finding.** It is not "the battery has a stale number in it". It
is "a class of hand-maintained, tree-shaped lists went stale together and nobody re-ran the guards".
The fix for `REG9-B1` should be scoped to that, not to the one literal.

Unlike `REG9-B1`, this one shadows nothing: the failing assertion at `:154` is the last in its method,
and the other two `@Test` methods in the class pass. It is a one-line README edit.

**What else I ran and found clean.** The other 24 tree-scanning classes -
`testEveryTestIsInTheBattery`, `testJavadocsAreAttached`, `testEveryCitationResolves`,
`testEditorSurfaceRules`, `testNothingOnTheEventThreadTakesTheRailwaysMonitor`,
`testEveryWindowWearsTheIcon`, `testMessageBundles`, `testEveryLanguageFits`,
`testControlEAsksTheMenusQuestion`, `testTriggerWaitsSayNothing`, `testConfirmedGoodState`,
`testTheArrowsKeepTheirAim`, `testNoTwoShortcutsShareAKey`, `testNoSelfRecursiveWrappers`,
`testTheBuildSaysWhatItIs`, `testTheCheckerAgreesWithTheBuild`,
`testStoreCollectionsAreHandledEverywhere`, `testTheAutoTierScopeMatchesTheRuntime`,
`testDataSafetyRoundTrips`, `testTheMenuShowsWhereALinkGoes`, `testStationLabelPrefill`,
`testStationLabelsFollowMoves`, `testFacingFollowsTheTrack`, `testTheGoldenLayoutHoldsTogether` - are
all `Failures: 0, Skips: 0`. **187 classes of 213 were still not run**, so this is a lower bound on how
red the battery is, not a count.

### REG9V-B2 - moving the count assertion below the offender assertion does not un-shadow it

`REG9-B1`'s second half is right and understates itself. Its recommendation - "move the count assertion
*below* the offender assertion" - does not achieve what it is for.

`testNoTestOpensTheOperatorsRailway` has **five** assertions, not two. In source order:

| line | assertion | what it is |
|---|---|---|
| 573 | `assertTrue(root.isDirectory(), ...)` | sanity |
| **741** | `assertEquals(checked, 32, ...)` | the stale window ratchet - **red now** |
| 790 | `assertTrue(loose <= MODELS_WITHOUT_A_SANDBOX, ...)` | model ratchet, upper |
| 796 | `assertEquals(loose, MODELS_WITHOUT_A_SANDBOX, ...)` | model ratchet, exact |
| 810 | `assertEquals(looseNames, pinned, ...)` | the VAL-C8 names pin |
| **818** | `assertEquals(offenders.toString(), "[]", ...)` | **the safety check** |

So four assertions are dead while `:741` is red, not one - and one of the four, the names pin at
`:810`, exists specifically because "the count alone can absorb a repair and a new violation in the
same round". That one is dead too.

**Measured, not reasoned.** I hid one sandbox open in a window-building class
(`testTheKeyMapReachesTheWholeWindow`, `support.LayoutSandbox.open()` written as
`support.LayoutSandbox . open()`, which still compiles and still builds a window but no longer matches
the scanner's pattern), with the window ratchet already bumped to 34 so `:741` would pass. The class
went red - **at `:790`, the model ratchet**, with:

```
there are now 56 test classes that build a model without pointing the layout preference at a sandbox
first, up from 55 ... expected [true] but found [false]
```

The offender assertion at `:818` still never executed. Moving only `:741` below `:818`, as REG9
proposes, would have changed nothing about that run.

**What I would change instead.** Compute every finding first and assert the safety check first, or
collect all five into one accumulated failure message. The general rule `REG9-B1` states is correct and
worth writing into the file - *a staleness detector that pre-empts a safety check is worse than no
ratchet* - but it has to be applied to all four detectors, not to the one that happens to be red today.

**Control.** With the ratchet at 34 and nothing else changed, the class is `Total tests run: 12,
Failures: 0, Skips: 0`, so the offender check does pass once reached, and the scanner is not blind -
it detected my hidden sandbox open. Both halves of REG9's claim about the fix being a one-line bump
are true; only the reordering is insufficient.

---

## C - low

| id | title | disposition |
|---|---|---|
| REG9V-C1 | REG9 states its own coverage three different ways, and none matches its evidence | open |
| REG9V-C2 | REG9-D3's evidence block names the opposite regression from its own summary table | open |

### REG9V-C1 - 38, 30, or 32 classes

The document says three things about the one number its every "no" depends on:

- header: *"**44 class-runs in total**, over 38 distinct classes of the 213"*
- What was NOT covered: *"The battery was not run. 30 classes of 213 were."*
- `REG9-D12`: *"30 classes of 213 were run"*

Counting the distinct classes that appear in the document's own quoted `--- <class>` runner lines gives
**32**. None of the three figures matches the evidence, and two of them contradict each other in the
same document. For a pass whose findings are all of the form "I ran N classes and nothing caught it",
the value of every one of them is exactly the credibility of N.

Separately, `REG9-A1` says *"Twenty-three call sites between them"* and then lists twenty line numbers.
Twenty is right:

```
$ grep -c "refuseWhileEditorOpen()\|refuseWhileAutonomyRunning(" src/org/traincontrol/gui/TrainControlUI.java
23
```

- which counts the two declarations and one mention inside a comment at `:6207`. The listed twenty are
correct; the headline is the unfiltered `grep -c`.

Neither error changes a verdict. Both are the kind that a later reader quotes.

### REG9V-C2 - REG9-D3 mislabels which regression its mutation restores

REG9's summary table says the `putTheTrainsBack` mutation restores *"D2-A1/W7-A1: a stale setup
placement overwrites the running railway"*. Its `REG9-D3` body says the same mutation restores *"the
setup always wins - the regression two independent reviews found in MT-337"*.

The table is right and the body is wrong. Both directions are pinned, and they are pinned by different
mutations:

```
if (false)  ->  Failures: 2
    testAStalePlacementDoesNotOverwriteTheRunningRailway   ... "the teleport Adam reported as OB-183"
    testATrainTheRebuildDroppedIsStillPutBack              ... "Dropping it here is OB-183"

if (true)   ->  Failures: 1
    testTheEditorsPlacementIsNotUndone  ... "which is exactly what MT-337 reported: placing from the
                                             editor appears to do nothing"
```

MT-337 is caught by the mutation REG9 did **not** run. The conclusion REG9 draws - that the coverage is
real and that the two tests differ by exactly what the code could not previously distinguish - is
correct, and better supported than it knows: three tests across two opposed mutations, not two of three
under one.

---

## D - not defects

| id | title | disposition |
|---|---|---|
| REG9V-D1 | `testAtomicWrite` catches the `writeAtomically` mutation by crashing, not by asserting | clean, worth knowing |
| REG9V-D2 | the `REG9-C1` defect shape is unique in the suite | clean |

### REG9V-D1 - the atomic-write coverage is real, and its red says nothing

`REG9-D5` is confirmed: `File staging = target;` reddens `core.testAtomicWrite` 2 of 7. But neither
failure is an assertion. Both are:

```
[FAIL] testAFailedObjectSerializationLeavesThePreviousFileIntact
    java.nio.file.NoSuchFileException: ...\state.dat
    at core.testAtomicWrite.contents(testAtomicWrite.java:63)
[FAIL] testAFailedWriteLeavesThePreviousFileIntact
    java.nio.file.NoSuchFileException: ...\state.dat
    at core.testAtomicWrite.contents(testAtomicWrite.java:63)
```

The `catch` block's `staging.delete()` deletes the target, and the helper that was going to read it
back throws first. The assertion that names the data loss never runs. This is a true red caused by
exactly the mutation, so `REG9-D5` stands - but REG9's own question is *"would the red thing say
something true"*, and here what it says is a file-not-found. The method names carry the meaning, which
is enough; it is recorded because it is the one place in the twelve where the red is accidental rather
than argued, and a later refactor of `contents()` could turn it into an error instead of a failure.

### REG9V-D2 - nothing else in the suite has REG9-C1's shape

`REG9-D9` admits its shape scan could not have found `REG9-C1` ("a perfectly ordinary shape"). So I
wrote the scan that would: every `<var>.indexOf("<literal>")` where `<var>` holds a whole source file,
reporting any needle that occurs more than once in that file. Four hits:

```
test/core/testAutonomyDiagramSession.java:2230      "reachableTiles("            AutonomyChecks.java   x2
test/core/testTrainTailClearsEdges.java:290         "tailHasProvablyPassed("     Layout.java           x2
test/regression/testDiagramDrawingSettings.java:83  "DIAGRAM_RESTRICTION_ARROWS," TrainControlUI.java  x3
test/ui/testADirectionChangeIsNotSwallowed.java:132 "takeReversalsOnArrival()"   TrainControlUI.java   x2
```

The first three are the loop form - `for (int at = s.indexOf(x); at >= 0; at = s.indexOf(x, at + 1))`
- which visits every occurrence on purpose and counts them; all three then assert on the count. They
are correct and are false positives of my scan. Only the fourth takes the first occurrence and compares
it, which is `REG9-C1`. So the defect is a singleton, and fixing it does not imply a sweep.

I also re-ran REG9's own cannot-fail scan, widened to include `assertTrue`/`assertFalse` on a boolean
literal. 221 files, 1758 `@Test` methods, 0 literal-boolean assertions, 0 self-comparing assertions,
and the same 12 assertion-free bodies in the same 6 classes. `REG9-D9` reproduces exactly.

---

## What this pass did not reach

- **The battery was not run.** 62 distinct classes of 213 were. `REG9V-B1` found a second red class by
  running 26 of them; **187 classes were never run by either document**, and a third red class would be
  found the same way. Nobody has measured how red the battery is. Before acceptance somebody has to run
  `battery.sh` once - that is the only thing that answers the question this pair of documents keeps
  approximating.
- **`REG9-B2` was confirmed by reading, not by running.** I agree with REG9's reason for not mutating
  `LayoutSandbox`: the failure mode of a mistake there is damage to the one folder neither document may
  touch. So the claim that the marker/repair/hook machinery is untested rests on `grep` in both
  documents, and 97 classes still depend on it.
- **Nothing hands-on, and nothing under `cs2_sample_layout/`.** No file there was read into a fixture,
  written, moved, staged or checked out.
- **Reachability of the two dispatch guards was not established.** `REG9V-A1` and `REG9-A2` are
  coverage findings. Whether a second dispatch is refused anyway by `isPathClear` or by the
  start-point check is a question about `Edge.isOccupied`'s owner-blind `occupancy > 0` that I read but
  did not run. If it is, the severity of both drops.
- **The Central Station and locomotive layers, the timetable, and the route capture** were not mutated,
  the same gap REG9 declares. `core.testMessageBundles` and `ui.testEveryLanguageFits` were run and are
  green, which is less than the bundles deserve.
- **Concurrency** was measured, as in REG9, only as "is the guard asserted".

## The tree

Every file mutated - `MarklinControlStation.java`, `TrainControlUI.java`, `AutonomySession.java`,
`Util.java`, `LayoutPageEdit.java`, `RouteEditorFrame.java`, `Layout.java`,
`testSwitchingToACentralStationLayout.java`, `testTheKeyMapReachesTheWholeWindow.java` - was copied to
the scratch directory before its first edit and restored from that copy. At the end:

```
$ git diff --stat -- src test docs build.xml
(empty)

$ git status --porcelain
 M cs2_sample_layout/config/autonomy/configuration-Main.json
 M cs2_sample_layout/config/autonomy/setup.json
?? docs/reviews-2026-09-11/REG9-acceptance-and-regression.md
?? docs/reviews-2026-09-11/REG9V-validation.md
```

The two `cs2_sample_layout` lines are the ones Adam already had. Nothing under that folder was written,
moved, staged or checked out. Nothing was committed. No process was killed.

---

## What I would look at first, for acceptance

1. **Run `battery.sh` once.** Two documents have now each found a red class by sampling, and 187
   classes remain unsampled. "The battery is green" is currently an assumption, and it is the
   assumption the release is being accepted on.
2. **`REG9-B1` plus `REG9V-B1` together**, as one fix: the ratchet, the scenario README, and the
   reordering that `REG9V-B2` shows has to cover four detectors rather than one.
3. **`REG9-A1`**, unchanged - twenty doors, one of them "replace the whole diagram while trains are
   running", and no assertion.
4. **`REG9-A2` and `REG9V-A1` as a pair.** They are nine lines apart and share a message key; writing a
   test for one and not the other is the mistake that produced them.
