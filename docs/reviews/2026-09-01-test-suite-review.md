# Test suite review — v3.0.0 pre-release

**Status:** open

**Citation prefix: `TCX`.** Cite findings from this document as `TCX-A1`, `TCX-B2`, and so on.

| | |
|---|---|
| Reviewed | Everything under `test/` — 146 classes, ~80,500 lines, 1,319 `@Test` methods |
| Version | v3.0.0, branch `autonomy-diagram-r0` |
| Commit | `b00ac0c1`. **Not `e9435bfc`** — the tree moved between the briefing being written and this pass starting, and the uncommitted `TileAnnotation.java` / `LocomotivePlaceholder.java` / `testAutonomyDiagramMonitor.java` changes the briefing named are now committed. The only files `git status` shows as modified are two under `cs2_sample_layout` — see **TCX-A4**, which should be read before anything else in this document. |
| Date | 2026-09-01 |
| Method | **Reading only.** No test, build, `ant`, `javac`, `java`, `battery.sh` or `one.sh` was run at any point, by me or by any agent working under me. Where a claim would need a run to settle, it is written up as an open question below rather than answered. |

Two questions were asked, in priority order: **what is not tested that should be**, and **which tests do not test what they
claim**. Findings from both are interleaved and lettered by severity, as the convention requires.

**How severity is assigned to a missing test.** A gap is not itself wrong behaviour, so the letter is the severity of the
defect that would go unnoticed because the test is absent. A vacuous test is graded the same way: by what it claims to
protect and does not. Each entry says which.

**Which findings answer which question.**

*What is not tested that should be* — TCX-A1, A2, B1, B2, B3, B4. All six are physical constraints of the railway or the
interaction between two of them, which is the character Adam asked for: room to reverse over a switch, the typed maximum
at a platform, whether the planner and the runtime agree about either, whether the guard can reach his actual track, and
the mechanical rule that a three-way turnout must never have both blade sets over at once. TCX-B12, B13 and C1–C4 are
gaps of a smaller kind — coverage that exists but does not bite.

*Tests that do not test what they claim* — TCX-A3, B5 through B11, and C3 and C5 through C9. Nine of these are assertions
that cannot fail; two are floors that pin the wrong quantity; one is an unseeded generator.

TCX-A4 answers neither question. It is what `git status` showed while I was working out which commit I was reading.

---

## Status

### A — high

| | Finding | Status |
|---|---|---|
| TCX-A1 | The reversal-room rule is not asked when the train reverses mid-path | open |
| TCX-A2 | `HomeStaging` re-implements `isPathClear`'s rules and did not get the new length rule | open |
| TCX-A3 | `testTheClearingLoopAsksTheRule`'s second assertion went vacuous when `isPathClear` gained the same string | open |
| TCX-A4 | `cs2_sample_layout` is modified in the working tree right now, and nothing can say by whom | **open — look at this first** |

### B — medium

| | Finding | Status |
|---|---|---|
| TCX-B1 | `validateTrainLength` — the model's original length rule — is never tested at the door that uses it | open |
| TCX-B2 | The editor's length notice asks for a different measurement from the one the guard needs | **DEFERRED — needs Adam** |
| TCX-B3 | The reversal-room guard's reach on the real railway is never measured, and on his data it is nearly zero | open |
| TCX-B4 | `configureEdge`'s release-before-throw ordering — a mechanical constraint of the real turnout — is untested | open |
| TCX-B5 | `testAnUnmarkedLayoutIsUntouched` compares a builder against an identically-configured builder | open |
| TCX-B6 | `badgeAt` confounds `parking` and `shut`, so two badge tests pass under the wrong rule | open |
| TCX-B7 | `testAutonomySimulationSanity`'s headline assertion cannot fire in simulate mode | open |
| TCX-B8 | `testNothingIsLoadedWhenAlreadyHome` survives the mutation it was written for | open |
| TCX-B9 | `testCopiesOfOneSquareAreTheSamePlaceAndNeighboursAreNot` asserts a floor of one where it needs two | open |
| TCX-B10 | `testRoutes` has three unseeded `Random`s, and its property test's only assertion carries no message | open |
| TCX-B11 | `testTheGoldenLayoutHoldsTogether` reads Adam's real railway in place rather than through `LayoutSandbox.open(File)` | open |
| TCX-B12 | Three of `testTheGoldenLayoutHoldsTogether`'s four tests have no floor on cases exercised | open |
| TCX-B13 | `testFullAutonomyDoesNotDriveThroughAReversingPoint` has no control | open |

### C — low

| | Finding | Status |
|---|---|---|
| TCX-C1 | `testTheStationRules`/`SensorRules` mirrors run once, on fixtures with no blocks | open |
| TCX-C2 | `testStationPriorityDistribution`'s floor is one sample in four hundred | open |
| TCX-C3 | Assertions the fixture guarantees, in six places | open |
| TCX-C4 | Two floors that pin the wrong quantity (`testAutonomyDiagramReducer`, `testAutonomyDiagramReversal`) | open |
| TCX-C5 | `testEveryTestShapedMethodCarriesAnAnnotation` accepts any `@`-annotation, including `@Test(enabled = false)` | open |
| TCX-C6 | `testNetworkProxy`'s `dependsOnMethods` chain turns one failure into two silent skips | open |
| TCX-C7 | Three javadocs in `testHomeStaging` now contradict the assertions beneath them | open |
| TCX-C8 | `testAClearFromARetiredLayoutStandsDown` asserts the same condition as its own precondition | open |
| TCX-C9 | `testRoutePicking`'s failure messages quote arithmetic that is wrong by a factor of 1000 | open |

### D — not defects

| | Finding | Status |
|---|---|---|
| TCX-D1 | The sandbox allow-list in `testSwitchingToACentralStationLayout` is **not** stale | closed — checked clean |
| TCX-D2 | That guard is not floorless — I withdrew that claim | closed — withdrawn |
| TCX-D3 | `testConfirmedGoodState`'s early return is behind an explicit opt-in property | closed — checked clean |
| TCX-D4 | `testAutonomyGroundTruth`'s pinned answer is 1,399 lines, so it is not vacuous | closed — checked clean |
| TCX-D5 | `maxTrainLength` is stored per square, not per copy — a worry of mine that was unfounded | closed — withdrawn |
| TCX-D6 | `validateTrainLength`'s unguarded unbox is unreachable in practice | closed — checked clean |
| TCX-D7 | Shared-s88 physics is well covered; so is whole-block occupancy | closed — checked clean |
| TCX-D8 | `testTheParkingBerthsGetTheirTrainsBack` is a well-built test | closed — checked clean |
| TCX-D9 | The single `@Test(enabled = false)` is documented and deliberate | closed — checked clean |

---

## A — high

### TCX-A1 — The reversal-room rule is not asked when the train reverses mid-path

**Status: open.** This is a missing test for a case the guard does not cover. Severity is that of the defect it would
catch: a train backed over a switch it does not fit behind, which is the hazard Adam raised on 2026-09-01.

`Layout.isPathClear` gained the rule this round, at `src/org/traincontrol/automation/Layout.java:2330-2364`. Its trigger is
this, at line 2335:

```java
            if (ending.isTerminus() || ending.isReversing())
```

`ending` is `path.get(path.size() - 1).getEnd()`. So the rule fires on **where the path stops**, not on **whether the path
turns the train round**. A path that reverses at an intermediate point and then finishes at an ordinary station is never
length-checked at all.

The model already has the other predicate, in the same file, two hundred lines further down —
`Layout.java:3427-3438`:

```java
    private boolean reversesAlongTheWay(List<Edge> path)
    {
        for (Edge e : path)
        {
            if (e.getEnd().isReversing())
```

and `executePath` really does stop the train and turn it there — `Layout.java:5296-5308`, `if (current.isReversing() &&
isCurrentLayout())` … `loc.setSpeed(0).switchDirection()`. So a train on such a path physically comes to a stand at the
reversing point, exactly as it does at a reversing destination, and the guard that exists for that stand does not run.

The inconsistency is internal, and that is what makes it nameable without a ruling: a path **ending** at a reversing point
that is not a terminus **is** checked, because of the `|| ending.isReversing()` clause. The model has therefore already
accepted that a mid-run reversal deserves the check. It simply only applies it when the reversal happens to be the last
point on the path.

**The test that is missing, and what it would assert.** `test/core/testNonReversibleTrains.java` already builds
`backingInLayout()` (line 409) and `longerBackingInLayout()` (line 373), both of which put the reversing point in the
middle and a terminus at the end. A third fixture of the same shape with an **ordinary** station at the end — `A —2→
B(reversing) —3→ C(station, neither terminus nor reversing)` — with `loc.setTrainLength(10)` would assert:

```java
assertFalse(layout.isPathClear(path, loc, false),
    "a train of ten was sent to turn round on a five-unit run-in and nothing objected, because the "
    + "rule asks what the DESTINATION is rather than whether the train reverses");
```

and a control at `setTrainLength(4)` that it is still allowed. Today the first of those assertions passes only under
`assertTrue`.

**Which stretch it should be measured against is Adam's, not mine.** Summing every segment of the path is right for the
final resting position at a terminus, because a train backing in extends back the way it came. It is not obviously right
at an intermediate reversal, where the segments before the turn and the segments after it are different pieces of rail
and the sum measures neither. I am not filing that as a defect, because I could not settle the geometry from the code
alone — see the open question at the end. **The gap that needs no ruling is that no test covers the shape at all.**

**Reachable by:** manual dispatch (`isPathClear` is the tier every door passes through, by the doctrine at
`Layout.java:2280-2297`), and the staging planner. Not by full autonomy, which refuses `reversesAlongTheWay` paths at
selection (`Layout.java:3477`).

---

### TCX-A2 — `HomeStaging` re-implements `isPathClear`'s rules and did not get the new one

**Status: open.** Missing test. Severity: the planner produces a plan the runtime then refuses on its first move — which
is the exact failure mode the file's own javadoc describes for the previous rule that went missing.

`src/org/traincontrol/automation/HomeStaging.java:591-593` states the burden out loud:

> The planner has to answer that question for hypothetical futures, which is why it cannot simply call `isPathClear` -
> that reads live feedback. So it re-implements the rules, and every time a rule was mis-copied the result was a plan the
> runtime then refused, or no plan where one existed.

`canRest` (`HomeStaging.java:1619-1637`) carries the typed maximum:

```java
        return at.isDestination()
            && at.isActive()
            && !at.getExcludedLocs().contains(loc)
            && at.validateTrainLength(loc);
```

It carries no edge-length rule, and `grep -n "getLength()" HomeStaging.java` returns nothing at all. `connected`
(`:1672-1716`) is length-blind too. So the reversal-room rule added to `isPathClear` this round exists on one side of the
pair and not the other, which is this project's most-repeated defect class — the `docs/reviews/README.md` entry "When you
fix a call site, grep for its twins before closing the finding" lists five instances of it from July alone.

The instrument that would notice is `auditAgainstRuntime` (`HomeStaging.java:602-695`), which compares `getPossiblePaths`
(and therefore `isPathClear`) against `firstClearRoute` for the present state. It is asserted in four places, all on
synthetic fixtures built inside `test/core/testHomeStaging.java` — lines 2610, 2631, 3703 and 3751 — and none of those
fixtures sets an edge length, a train length and a reversing or terminus destination together. The audit is also "logged
rather than enforced" (`:597`), so on the real layout a divergence produces a log line nobody is asserting on.

**The test that is missing.** A staging fixture whose home is a terminus reached over measured edges shorter than the
train, asserting either that the planner does not offer that home, or — the weaker and more honest form, which does not
prejudge whether the planner should carry the rule — that `auditAgainstRuntime()` returns non-zero and names it. The
second form is better: it tests the invariant rather than the design choice.

---

### TCX-A3 — `testTheClearingLoopAsksTheRule`'s second assertion went vacuous this round

**Status: open.** Verified by reading. This is a test that no longer tests what it claims, and it stopped doing so
*because of this release's own change*.

`test/core/testTrainTailClearsEdges.java:199-213`:

```java
        assertTrue(source.contains("loc.getTrainLength()"),
            "the clearing loop no longer passes the locomotive's length, so the rule compares "
            + "against nothing and every edge is handed back the moment the head leaves it");
```

`source` is the whole text of `Layout.java`. `loc.getTrainLength()` now occurs **four** times in that file:

```
2330:        if (loc != null && loc.getTrainLength() != null && loc.getTrainLength() > 0
2352:                if (measured && loc.getTrainLength() > room)
5380:                                loc.getTrainLength()))
5388:                                        loc.getTrainLength(),
```

Only 5380 and 5388 are the clearing loop. 2330 and 2352 are the reversal-room rule added on 2026-09-01. So deleting the
length argument from the clearing loop entirely — which is the mutation the message describes, and which would hand every
edge back the moment the head leaves it — leaves this assertion green, because `isPathClear` still contains the string.

The assertion above it (line 205, pinning `"tailHasProvablyPassed(pathIsUnmeasured, waiting[1],"`) still bites, and
`testAnEdgeTheRuleRefusesToClearStaysHeldWhileARealPathRuns` (line 307) is a genuinely behavioural guard on the same
property, so nothing is unprotected. What is wrong is that a false assertion is sitting in the file reading as a check.
The narrow fix is to pin the whole call — `"tailHasProvablyPassed(pathIsUnmeasured, waiting[1],\n"` through
`"loc.getTrainLength()))"` — or to count occurrences rather than test for presence, which is what its sibling at line 275
already does (`assertEquals(gates, 2, ...)`).

**This is the general hazard with source-text assertions, and it is worth stating once:** a `contains` over a 7,920-line
file is a check on the file, not on the method. Every one of them in this suite is listed under TCX-C3.

---

### TCX-A4 — `cs2_sample_layout` is modified in the working tree, and nothing can say by whom

**Status: open — look at this first.** I have not touched it. I am reporting what `git status` showed while I was
establishing which commit I was reviewing.

Two files under Adam's real railway are modified relative to `a91a6495` ("New graph state", 30 Aug):

```
 cs2_sample_layout/config/autonomy/configuration-Main.json | 83 +++++++++-------------
 cs2_sample_layout/config/autonomy/setup.json              | 16 ++---
```

The changes are semantic, not line endings — `git diff --ignore-all-space` shows the same 83 and 16. Among them:

| Change | From | To |
|---|---|---|
| `globals.pathPreference` | `MOST_STATIONS` | `RANDOM_ANY_STATION` |
| `globals.atomicRoutes` | `true` | `false` |
| `"active"` keys on points | 4 present | **0 — all four removed** |
| A timetable leg's locomotive | `EN57-947` | `MT-x233 Test Loc` |
| A point's placed locomotive | `75 407 DB` | `MT-233 Test Loc 2` |
| Several `facing`, `home` and `loc` values | — | moved between points |

`atomicRoutes: false` in particular contradicts what the source says he runs — `Layout.java:5375-5377`, *"with
atomicRoutes on, which is what Adam runs, the lock is held for the whole run by design"*. Four points he had switched off
are now on. Those are settings, not derived state.

**I cannot tell whether a test did this or Adam did.** Both readings are supported:

- *Adam did it.* `MT-233 Test Loc` is a locomotive he created by hand during manual test MT-233 on 2026-08-30 — his own
  words are quoted in `test/core/testRoutePicking.java:465`: *"added MT-233 Test Loc. After initial placement, error:
  Invalid speed specified. It was added via control+V on the track diagram viewer."* Flipping `pathPreference` and
  `atomicRoutes` is exactly what manual testing looks like.
- *A test did it.* This is the folder `LayoutSandbox` exists to keep test JVMs out of, 56 test classes still open whatever
  layout the machine names (TCX-D1), and one of them reads this folder by name in place (TCX-B11). The coordinator
  reported killing two concurrently running batteries today.

**That ambiguity is the finding, and it is a finding about this suite.** It is the same one the code already records:
`LayoutSandbox.java:20-23` says that on 2026-08-25 *"the churn masked a change he had made himself, and telling the two
apart meant reading the JSON semantically"*. Six days later a reviewer is doing exactly that again and still cannot
answer. Neither `testTheGoldenLayoutHoldsTogether`'s `@AfterClass` fingerprint (which sees only its own JVM) nor
`battery.sh`'s (which sees only a whole run) can attribute a change — they answer "something wrote", never "this did".

**What I am asking for, in order:**

1. Ask Adam whether these two files are his work before anything else runs. If they are, commit them so the next reviewer
   is not asking the same question.
2. If they are not his, they are recoverable from `a91a6495` — which is the one piece of luck here, and it is luck, not
   design. `git diff` is the only reason this is not the unrecoverable loss the comments describe.
3. Either way, TCX-B11 removes the ambiguity for the future at the cost of one line.

---

## B — medium

### TCX-B1 — `validateTrainLength` is never tested at the door that uses it

**Status: open.** Missing test.

`Point.validateTrainLength` is the model's original length rule, and `isPathClear` refuses on it at
`Layout.java:2258-2267`. The refusal message key is `autolayout.errorTrainLengthTooLong`
(`src/org/traincontrol/resources/messages.properties:169`).

```
$ grep -rn "errorTrainLengthTooLong" test/
(no matches)
```

Nothing in 146 test classes asserts that `isPathClear` refuses an over-long train at a station with a maximum. What exists
is: the null case at `test/core/testInvalidInput.java:659-661`, a JSON round-trip at `test/core/testAutoLayout.java:1156`,
and the staging-side rule at `test/core/testHomeStaging.java:1199-1202` (which exercises `HomeStaging.canRest`, not
`isPathClear`).

Two specific defects a test would catch:

1. The clause sits immediately above the new reversal-room rule. A refactor of one is the natural place to lose the other.
2. `Point.validateTrainLength:912` opens `if (!this.isDestination) return true;`. A manually-picked route may end at a
   point that is not a destination, and the typed maximum is then not consulted at all. Whether that is right is a
   question; that nothing records the answer is not.

**On the real layout this clause is inert**, which is why it could break unnoticed. Every one of the 30 points in
`cs2_sample_layout/config/autonomy/configuration-Main.json` that carries a `maxTrainLength` carries the value `0`, and
`validateTrainLength:913` returns true on zero. See TCX-B3 for what that means for the new rule as well.

---

### TCX-B2 — The editor notice asks for a different measurement from the one the guard needs

**Status: DEFERRED — needs Adam.**

**The one-sentence question for him:** should the "add a track length here" notice name only the square trains turn round
at, or every square on the run-in to it — because recording exactly what it currently asks for does not make the guard
able to judge anything?

`AutonomySession.reversalsWithoutLength()` (`src/org/traincontrol/automationui/AutonomySession.java:1921-1943`) asks one
question per reversing square:

```java
            if (store.getTileLength(tile) <= 0) out.add(tile);
```

`isPathClear`'s guard needs something strictly stronger — **every** segment of the path measured, or it declines to judge
(`Layout.java:2341-2350`):

```java
                for (Edge segment : path)
                {
                    if (segment.getLength() <= 0)
                    {
                        measured = false;
                        break;
                    }
```

An edge's length is the sum of its tiles' lengths (`GraphReducer.sumLength`, `:1052-1062`), so setting a length on the
reversing square makes the edge that *ends* there measured and leaves every earlier edge of the run-in at zero. Follow the
notice on every square it names and the guard still refuses to judge the path.

Adam's words, quoted at `AutonomyChecks.java:675-676`, read the broader way: *"Add notices to the autonomy editor to add
track lengths between stations and switches that accept reversal"* — the track **between**, not the switch square.

**The test that is missing, whichever way he rules.** Record a length on every square `reversalsWithoutLength()` names,
then assert that `isPathClear` can now actually judge a reversal path over that track — i.e. that it refuses an over-long
train there. That is the assertion that ties the notice to the rule it exists to enable. The test that exists,
`testTheEditorAsksForLengthsWhereTrainsReverse` (`test/core/testAutonomyDiagramSession.java:1857-1888`), is a good test of
the notice in isolation and asserts nothing about the guard.

**A second, smaller gap in the same area.** `AutonomyChecks.run` takes twenty positional parameters
(`AutonomyChecks.java:360-367`), of which nine are `Set<TileKey>` and several are adjacent. Swapping two compiles
silently. Nine of the check ids are driven end-to-end through `session.check()` in the tests (`CAPTION_COVERED`,
`REVERSING_LEADS_NOWHERE`, `MAY_TURN_ON_DEAD_END`, `STATION_REACHES_NOTHING`, `TERMINUS_STRANDED`, `UNLABELLED_STATION`,
`NO_ARRIVALS_LEFT`, `DUPLICATE_LOCOMOTIVE`, `DUPLICATE_SENSOR_PAGE`); `REVERSAL_NEEDS_LENGTH`, `HOME_NEEDS_REVERSIBLE`,
`SIGNAL_GONE`, `FACING_IMPOSSIBLE`, `NO_TRAIN_LENGTH` and `NO_MAX_TRAIN_LENGTH` are not. The new one is tested at the
session method and not through the wiring at `AutonomySession.java:3203-3214`, so a mis-ordered argument there leaves the
notice absent and the test green.

---

### TCX-B3 — The guard's reach on the real railway is never measured, and on his data it is nearly zero

**Status: open.** Missing test. This is the "distinguish *could* happen from *does* happen" check applied to the new
feature, and it is checkable against the data in the repository.

Read from `cs2_sample_layout/config/autonomy/setup.json` and `configuration-Main.json` (read-only):

| | |
|---|---|
| `tileLengths` entries on the whole railway | **6** — `5:20,13`, `5:0,11`, `5:20,14`, `5:1,10`, `5:14,3`, `5:5,4` |
| Which page those are on | **all six on page 5, "Test"** |
| Points on the railway | 71 |
| Points carrying `maxTrainLength` | 30, **every one of them `0`** |
| `canReverse` squares | 5 |
| `mustReverse` squares | 17 |

So: `store.measuresAnyTrack()` is true, which arms the editor notice for every reversing square without a length — around
twenty of them, all at once, the first time he opens the editor. And `isPathClear`'s guard requires every edge of a path
to be measured, which no path on pages 1–4 can be. **The rule is armed on the Test page and inert on the whole working
railway**, while the notice is loud everywhere.

That is not necessarily wrong — it is the state the notice exists to move him out of — but nothing measures it, so nobody
will notice if it stays that way, or if a later change makes the guard fire where it should not.

**The test that is missing.** On the golden layout, enumerate the paths that end at a terminus or reversing point and
report how many the guard can actually judge (every edge measured) versus how many it declines. Assert a floor of zero
today if you like; the value is the number appearing in the output where somebody reads it. `testRouteInventory` already
does exactly this kind of reporting, and `testTheGoldenLayoutHoldsTogether` is where a real-layout assertion of this shape
belongs.

---

### TCX-B4 — The release-before-throw ordering of accessory commands is untested

**Status: open.** Missing test. Severity: a turnout commanded into a combination that routes nowhere, on real hardware.

`Layout.configureEdge` sorts an edge's accessory commands so that releases are issued before throws
(`Layout.java:2489-2497`):

```java
        names.sort((a, b) ->
        {
            boolean aThrows = Accessory.isThrow(e.getConfigCommands().get(a));
            boolean bThrows = Accessory.isThrow(e.getConfigCommands().get(b));

            return aThrows == bThrows ? a.compareTo(b) : (aThrows ? 1 : -1);
        });
```

The reason is mechanical, and it is written down at `Layout.java:2476-2480`: *"a three-way turnout is two drives on
consecutive addresses: commanding the diverging one before the other has been released puts both blade sets over at once,
a combination that routes nowhere and that some mechanisms bind in."*

`Accessory.isThrow` itself is tested — `test/core/testAccessory.java:516-520`. The **ordering** is not. No test in the
suite adds two config commands with mixed settings to one edge and asserts the order they reach the control station in.
`test/core/testThreeWaySwitch.java` is about the route editor's three-way shapes and the pause between commands, not about
this.

**The test that is missing, and the trap in writing it.** `Edge.configCommands` is a plain `HashMap`
(`src/org/traincontrol/automation/Edge.java:41`) — "the map's own key order, which is no order at all". A fixture with one
release and one throw would pass under *no sort at all* about half the time, depending on which way the two names hash.
The test must therefore either (a) run both insertion orders and assert the release is recorded first in both, or (b) use
several accessories and assert the full ordering, releases first and then throws, each group by name. A recording control
station is already available — `test/core/testMockCentralStation.java` and the accessory-actuation counters
`testAutonomySimulationSanity` uses.

---

### TCX-B5 — `testAnUnmarkedLayoutIsUntouched` compares a builder against an identically-configured builder

**Status: open.** Verified by reading. The test cannot fail for the reason it states.

`test/core/testAutonomyDiagramReversal.java:349-367`:

```java
        String withoutFeature = new AutonomyBuilder(reducer, null)
            .withPointExtras(map(extras())).build();

        String withEmptyMark = new AutonomyBuilder(reducer, null)
            .withPointExtras(map(extras()))
            .withReversibleTiles(Collections.<TileKey>emptySet()).build();

        assertEquals(withEmptyMark, withoutFeature);
```

`AutonomyBuilder.reversible` is already `Collections.emptySet()` from its field initialiser
(`src/org/traincontrol/automationui/AutonomyBuilder.java:205`), and `withReversibleTiles`
(`:287-291`) assigns the same empty set and calls `nodeCache.clear()` on a cache that is empty on a fresh builder. The two
builders are in provably identical state before `build()` is called. Only a nondeterministic `build()` could make this
fail — and that is a different property, already pinned by `testAutonomyDiagramReducer.testReductionIsDeterministic`.

The javadoc's claim — *"a configuration that uses none of this has to come out byte for byte as it did before"* — is not
tested, because "before" is not present in the comparison. Testing it needs a pinned baseline, which
`test/regression/testConfirmedGoodState.java` is the existing machinery for.

---

### TCX-B6 — `badgeAt` confounds `parking` and `shut`

**Status: open.** Verified by reading. Two tests pass under a wrong rule.

`test/core/testAutonomyDiagramMonitor.java:1204-1205`:

```java
            new TileAnnotation.Badge(station, station && turns, !station && turns, shut, true,
                Side.W, Side.E, false, shut),
```

The nine-argument constructor is
`Badge(station, terminus, reversing, parking, named, a, b, optional, shut)` — `TileAnnotation.java:213-214`. So `shut` is
passed as **both** `parking` and `shut`, and every fixture `badgeAt` builds has `parking == shut`.

`isImpassable()` is `return shut;` (`TileAnnotation.java:291-294`). `testASquareNothingCanUseIsDrawnAsACross`
(`:1028`) and `testTheCrossKeepsItsWeightAsTheTileGrows` (`:1116`) both vary only `shut`, so neither can distinguish
`isImpassable() { return shut; }` from `isImpassable() { return parking; }`. The test's own stated mutation — making
`isImpassable` ignore `shut` — is caught only in the form that returns a constant, not in the form that returns the other
flag.

The file already knows this trap: the sibling colour test at line 1080 uses `plainBadge(parking, shut)` (`:1149`), which
separates them, and its javadoc says *"a fixture that set both would pass on the old rule as well"*. The warning was
written and then not applied to `badgeAt`.

---

### TCX-B7 — `testAutonomySimulationSanity`'s headline assertion cannot fire in simulate mode

**Status: open.** Verified by reading.

`test/core/testAutonomySimulationSanity.java:205` and `:257`:

```java
            assertTrue(layout.getPathValidationFailureCount() == 0,
                "No path validation warning must occur during a simulated run (failures="
```

`test/autonomy_sanity.json:143` sets `"simulate": true`. `Layout.java:2940-2945` returns before the validation:

```java
        if (this.simulate || !PATH_INTEGRITY_VALIDATION)
        {
            return true;
        }
```

and the counter is only ever incremented in `handleMisconfiguredPath`. So no regression in the path-integrity guard can
make `testSimulatedAutonomyRaisesNoWarning` fail — the assertion is satisfied by the mode the test runs in, not by the
code it names.

The file knows: `testPathValidationCanActuallyFireOutsideSimulateMode` (`:450`) is the armed replacement and its comment
says why. The soak's headline assertion was left in place beside it, so the class still reads as covering the mechanism
twice when it covers it once. `testSimulatedAutonomyRaisesNoWarning` is at `:171`.

---

### TCX-B8 — `testNothingIsLoadedWhenAlreadyHome` survives the mutation it was written for

**Status: open.** Verified by reading.

`test/core/testHomeStaging.java:799-813`. The javadoc says the point is *"so a stale plan cannot be left sitting in the
timetable waiting to be executed by accident"* — that is, it guards the early return at
`Layout.loadReturnToHomeTimetable:6503`, `if (!plan.isPossible()) return plan;`.

```java
        Layout layout = load(ring(LOC_A, LOC_B, null));
        layout.setTimetable(new java.util.LinkedList<>());

        HomeStaging.Plan plan = layout.loadReturnToHomeTimetable();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.ALREADY_HOME);
        assertTrue(layout.getTimetable().isEmpty(), "an untouched layout loads no moves");
```

Delete the guard and the test still passes: an `ALREADY_HOME` plan is built with `empty()` moves
(`HomeStaging.java:344`), so the loop stages nothing and `setTimetable([])` leaves a timetable the test itself emptied one
line earlier still empty. What the mutation actually changes is `this.timetableSequential = true`, and the test never
looks at that flag.

The fix is one line: assert `assertFalse(layout.isTimetableSequential(), ...)`, or seed the timetable with an entry before
calling so that "nothing was loaded" is distinguishable from "nothing was there". The sibling
`testStagingPlansAreFlaggedSequentialAndOtherTimetablesAreNot` (`:780`) asserts the flag, but only on the path where a
plan *is* possible.

---

### TCX-B9 — `testCopiesOfOneSquareAreTheSamePlaceAndNeighboursAreNot` asserts a floor of one where it needs two

**Status: open.** Verified by reading.

`test/core/testAutonomyDiagramSession.java:3441-3450`:

```java
        assertFalse(copies.isEmpty(), "precondition: the station has at least one Point");

        // Every copy of one square is that square
        for (String one : copies)
        {
            for (String other : copies)
            {
                assertTrue(session.sameSquare(one, other),
```

The fixture is `pageOnDisk()` (`:1497-1520`), which draws a straight run with a `FEEDBACK` at each **end** — `(1,1)` and
`(4,1)`, nothing beyond either. A square with one arrival side emits one Node, so `copies.size() == 1`, and the nested
loop only ever evaluates `sameSquare(x, x)`. `AutonomySession.sameSquare:2241` short-circuits:

```java
        return a.equals(b) || getStationIndex().sameSquare(a, b);
```

So the half of the test named in its own title — "copies of one square are the same place" — cannot fail whatever the
station index does. The second half (`assertFalse(session.sameSquare(one, elsewhere))`, `:3462`) is sound.

This is the same defect the author found and fixed in the sibling at `:4557`, whose comment reads *"Run on a station at
the end of a line - one way in, one Point - the closing check below reduced to sameSquare(x, x)"*. That test was moved on
to `pageWithATwoEndedStation()`; this one was not. The floor should be `assertTrue(copies.size() > 1, ...)`.

---

### TCX-B10 — `testRoutes` has three unseeded `Random`s, and its property test's only assertion carries no message

**Status: open.** Verified by reading. This is the suite's one violation of the fixed-seed rule in
`docs/reviews/README.md`.

`test/core/testRoutes.java`:

```
 49:    private static final Random RANDOM = new Random();
 58:        Random random = new Random();
932:        while (newRoutes.size() < (new Random()).nextInt(40) + 1)
```

None is seeded, so no failure is reproducible. The worst of them is `testExpressions` (`:601-616`), a property test over
twenty randomly generated boolean expressions whose single assertion is:

```java
            assertEquals(node, parsedNode);
```

— no message, therefore no expression and no seed in the failure. The generated text goes to `System.out` at `:608-610`,
which is the only trace, and it is not part of the assertion. A failure here is not diagnosable and, per the README, gets
deleted rather than fixed.

`testJSONExportImport` (`:932`) additionally makes the *amount of work the test does* random between 1 and 40 cases, with
nothing recording which. The file's own comment at `:944` records that this generator has already produced one silent
defect — colliding ids from `nextInt(1000)`, "a few percent of the time" — which is exactly the class of bug an unseeded
generator makes unreproducible.

Minimum fix: `Long.getLong("routes.seed", <literal>)` with the seed appended to every failure message, in the pattern
`testReturnHomeOnRealLayout.java:70-79` and `testTimetableOnDerivedGraph.java:97-99` already use.

---

### TCX-B11 — `testTheGoldenLayoutHoldsTogether` reads Adam's real railway in place

**Status: open.**

`test/regression/testTheGoldenLayoutHoldsTogether.java:63`:

```java
    private static final File GOLDEN = new File("cs2_sample_layout");
```

and it reads that folder directly: `fingerprint(GOLDEN)` (`:82`), `new CS2File(path, model)` (`:90`), `parseLayout`
(`:93`), `new AutonomyCompanionStore(GOLDEN)` (`:95`), `store.setPageIds(...)` (`:97`), `store.load()` (`:98`). It also
builds a model at `:86` with no sandbox.

`support.LayoutSandbox.open(File)` exists for precisely this, and its javadoc says so (`LayoutSandbox.java:63-70`): *"The
operator's own layout is the case this exists for … It is COPIED here, exactly as the fixture is, so the original is only
ever read - and the copy is what the preference points at, so nothing downstream can reach the original even by
accident."* `test/core/testTheParkingBerthsGetTheirTrainsBack.java:92` already uses it that way, over the same folder.

The class defends itself with an `@AfterClass` fingerprint, which is good and which its javadoc argues for well. But a
fingerprint is a **detector**, not a preventer: it fires after the write, and the thing being protected is stated in the
same file as *"his accumulated setup and it is not recoverable"*. Copying first makes the write impossible.

**A documentation drift falls out of this.** `LayoutSandbox.java:25-26` asserts, in bold: *"**The cause is not a test that
names his folder - none of them do.**"* Two now do — this class and `testTheParkingBerthsGetTheirTrainsBack` — and the
second does it correctly. That sentence is load-bearing reasoning about why the preference is the right thing to redirect,
and it is now false as written.

---

### TCX-B12 — Three of `testTheGoldenLayoutHoldsTogether`'s four tests have no floor

**Status: open.**

| Test | Loop | Terminal assertion | Floor |
|---|---|---|---|
| `testEverySquareTheSetupNamesIsOnTheDiagram` (`:180`) | `for (TileKey named : store.getNamedTiles())` (`:188`) | `assertTrue(missing.isEmpty(), ...)` (`:206`) | none |
| `testEverySquareAConfigurationNamesIsOnTheDiagram` (`:278`) | `for (String name : store.getConfigurationNames())` (`:286`) | `assertTrue(missing.isEmpty(), ...)` (`:332`) | none |
| `testEveryPageInTheIndexLoaded` (`:347`) | `for (String named : readLayoutIndexIds(...).keySet())` (`:355`) | `assertTrue(absent.isEmpty(), ...)` (`:360`) | none |
| `testTheSetupAndTheIndexAgreeAboutTheNumbering` (`:223`) | — | `assertTrue(conflicts.isEmpty(), ...)` | **`assertFalse(index.isEmpty(), ...)` at `:227`** |

`setUpClass` asserts `assertFalse(before.isEmpty(), ...)` at `:84`, which proves the *folder* has files in it — not that
`store.load()` read anything. If the companion store loaded nothing (which is the failure `getPageIdConflicts` and FR-018
exist around), `getNamedTiles()` and `getConfigurationNames()` both come back empty and two of these tests pass having
checked nothing, on the one class in the suite that looks at the real railway.

The floors are one line each and the numbers are knowable from the data — the current `setup.json` holds 30 stations and
71 configured points.

---

### TCX-B13 — `testFullAutonomyDoesNotDriveThroughAReversingPoint` has no control

**Status: open.**

`test/core/testLayoutPickPath.java:487` asserts only `assertNull(layout.pickPath(loc), ...)`. Any regression that made
`pickPath` return null — for any reason at all — passes it.

The file states the standard it misses, in its own javadoc at `:416-418`: *"The control assertion runs first: while the
middle point is an ordinary station, the far destination must be reachable through it, so a fix that simply refused every
multi-edge path could not pass this by doing nothing."* The sibling
`testAPathThroughAReversingStationIsNotChosen` (`:421`) does exactly that at `:439` — `assertEquals(destinationOf(
layout.pickPath(loc)), "FAR", "control: an ordinary station may be driven through to reach what lies beyond it")` —
before flipping the flag. This one does not.
`testAReversingPointIsStillOfferedByHand` (`:576`) uses the same graph but goes through `getPossiblePaths`, which never
consults `isReversing`, so it is not a control on `pickPath`.

The fix is the same shape as the sibling's: assert the destination *is* picked while the middle point is an ordinary
station, then set `setReversing(true)` and assert it is not.

Note this is the rule `testNonReversibleTrains.java:120-131` deliberately points at as living here. It is the right place
for it; it just needs the control.

---

## C — low

### TCX-C1 — The mirror tests run once, on fixtures with no blocks

`test/core/testRoutePicking.java`, `testTheSensorRulesAreMirrors` / `testTheStationRulesAreMirrors` /
`testTheLengthRulesAreMirrors` (`:109-166`) each run a single trial. They are deterministic today only because no two
candidate routes tie under those rules; a future fixture edit that created a tie would silently make them coin tosses,
because `Layout.getNeighbors` shuffles. The unmeasured sibling at `:272` uses 20 rounds. Separately,
`Layout.sensorsOn` (`:382-396`) de-duplicates by **block**, and no `testRoutePicking` fixture sets a block on any point —
so an implementation with no de-duplication at all (`path.size() - 1`) passes identically. The arrival-side-split case the
de-duplication exists for is untested.

### TCX-C2 — `testStationPriorityDistribution`'s floor is one sample in four hundred

`:384` skips failed samples (`if (path == null || path.isEmpty()) continue;`) and `:391` asserts only
`assertFalse(counts.isEmpty(), ...)`. So `testTheHighestPriorityBandIsTheOnlyOneUsed`'s "never chosen" assertions are
satisfiable by a single successful sample out of `SAMPLES`. Its own sibling at `:206` uses `>= SAMPLES / 10`, which is the
right shape.

### TCX-C3 — Assertions the fixture guarantees

Each of these is satisfied by the test's own setup and cannot be failed by any production defect. None is harmful on its
own; together they inflate the apparent coverage.

| File:line | Assertion | Why it cannot fail |
|---|---|---|
| `test/core/testReturnHomeSequencesAReversal.java:170-176` | both moves belong to `loc` | `reversalLayout()` (`:325`) places exactly one locomotive, so every `Move` is necessarily for it |
| `test/core/testHomeStaging.java:559` | `assertFalse(plan.isPossible())` after pinning `ALREADY_HOME` | `Plan.isPossible()` is `outcome == READY` (`HomeStaging.java:314`) |
| `test/core/testHomeStaging.java:1827` | `getBlockedBy().isEmpty()` on a point the fixture never sets one on | `ring()` emits none and `setBlockedBy` is not symmetric |
| `test/core/testHomeStaging.java:1149` | `assertSame(d.getHomeLoc(), alpha)` after a rename | `setHomeLoc`/`getHomeLoc` are a bare field write and read; a rename cannot change which object a field points at. (Would fail if the field went back to a `String`, which is the regression it was written for — so weak rather than impossible.) |
| `test/core/testAutonomySimulationSanity.java:233` | `assertTrue(acc != null)` | `setUpClass` creates all seven and already dereferences each |
| `test/core/testAutonomyPathValidation.java:638` | `loc.getSpeed() == 0` | `loc` is a fresh `dummyLoc()` never given a speed. Its sibling at `:419-423` sets the speed first, deliberately, and cites TST-C13 for why |
| `test/core/testLayoutPickPath.java:484` | `LOOP.isReversing() && !LOOP.isDestination()` | set eight lines earlier in the same method |
| `test/core/testAutonomyDiagramMonitor.java:417-418` | `getPublished()` non-null and empty on a fresh monitor | both are the field initialiser at `DiagramMonitor.java:71`; nothing is rebuilt, despite the method being named `testThePictureCanBeAskedForAgainAfterAViewIsRebuilt` |
| `test/core/testAutonomyDiagramPorts.java:173-174` | `!contains("NS")` | lines 171-172 already assert those sets equal `pairs("SW")`/`pairs("SE")` exactly |
| `test/core/testTheParkingBerthsGetTheirTrainsBack.java:156` | `STARTED_AT.size() == 5` | `place()` puts once per iteration of a five-element array and throws on anything else |

The source-text assertions belong here too, as a class: `testTrainTailClearsEdges.java:205/210/275`,
`testAutonomyDiagramSession.java:1209-1235` and `:2518`, `testHomeAssignmentRules.java:129-175` (seven `String.contains`
over `AutonomyEditorPanel.java`), and the `TrainControlUI.java` text checks in `testSwitchingToACentralStationLayout.java:185-227`.
All are candid about being "read rather than run"; they pass when the code is renamed but still wrong, and TCX-A3 is what
happens when one of them goes stale. `testHomeAssignmentRules` handles the usual trap correctly by asserting
`PANEL.isFile()` first, so a wrong working directory fails loudly.

### TCX-C4 — Two floors that pin the wrong quantity

- `test/core/testAutonomyDiagramReducer.java:543` — `assertFalse(reducer.getEdges().isEmpty(), ...)` in
  `testTwoRoutesBetweenTheSameSensorsBecomeOneEdge`. The fixture's straight run produces edges whether or not the two
  curves join up, so the exact severing the comment at `:536-542` describes leaves the floor satisfied and the parallel-route
  rule untested. A floor on the *second* route is what would close it.
- `test/core/testAutonomyDiagramReversal.java:726-733` — `testASplitCopyNeverCollidesWithAnAuthoredName` asserts
  uniqueness over the emitted points and never asserts that the square actually split, so if the geometry stopped
  splitting the loop runs over unsplit points and passes. The sibling at `:270` in the same file added exactly this floor,
  for exactly this reason. Same shape, lower stakes, at `:257-261`.

### TCX-C5 — `testEveryTestShapedMethodCarriesAnAnnotation` accepts any `@`-annotation

`test/regression/testEveryTestIsInTheBattery.java:210-214` walks upward and sets `annotated = true` on any line starting
`@`. So `@SuppressWarnings("unchecked")` above a `public void testX()` with no TestNG annotation satisfies it, and so does
`@Test(enabled = false)`. Nothing anywhere in the suite checks for `enabled = false` — there is one today
(`testAutonomyDiagramSession.java:2602`, deliberate and documented), and a second added by accident would be invisible.

The same class's `testTheBatteryRunsEveryTestClass` has **no floor** on how many classes it scanned, while its sibling at
`:230` has one (`methodsChecked >= 500`). If `test/` were unreadable or the working directory wrong, it reports green
having examined nothing.

### TCX-C6 — `testNetworkProxy`'s dependency chain turns one failure into two silent skips

`test/core/testNetworkProxy.java:204` and `:225` use `dependsOnMethods` on
`testTransientReceiveErrorDoesNotStopTheCanListener`, which waits up to 15s on a `CountDownLatch` and up to 5s on a thread
count. If that test times out on a loaded machine, the two downstream tests are reported SKIPPED rather than run: one
failure and two silent skips out of four. This is the suite's only use of `dependsOnMethods`.

### TCX-C7 — Javadocs that now contradict the assertions beneath them

- `test/core/testHomeStaging.java:2224-2232` — `testANonReversibleLocomotiveMayBeParkedAtATerminus`'s javadoc says *"the
  terminus is unusable, so there is nowhere to step aside and no plan exists"*; the assertion at `:2262` is
  `assertTrue(plan.isPossible(), ...)`. The inline comment at `:2253-2261` records the 2026-08-31 reversal; the javadoc
  above it was not updated.
- `:2348-2353` — *"Neither the planner nor isPathClear has any rule about reversing points, so this pins the absence."*
  Both now have such rules (`HomeStaging.mustBackIn`, `Candidate.turned`, `connected(from, to, mustReverse)`, and
  `isPathClear`'s new length clause). The test still passes; it no longer pins the absence it says it pins.
- `:2699-2700` claims BottomMainC and BottomMainCTerm share feedback 4; `HomeStaging.java:1740-1744` records that this
  was measured and is false on the 3.0.0 derived graph.

Documentation only, but in a codebase whose comments are treated as the design record, a comment that says the opposite of
the code beneath it is the same hazard as a stale test.

### TCX-C8 — `testAClearFromARetiredLayoutStandsDown` asserts its own precondition

`test/core/testAutonomySimulationSanity.java:413` asserts the sensor is still set; `:402` asserts the identical condition
as the precondition. If `simClearBehind` were removed outright the sensor stays set and the test passes. No control shows
a non-retired layout's clear-behind actually clearing that sensor.

### TCX-C9 — Failure messages quoting arithmetic that is wrong by 1000

`test/core/testRoutePicking.java:634-635` and the `importantButFarAway` javadoc (`:736-740`) quote
`(9+1)*1000/18 = 555` and `(0+1)*1000/2 = 500`. `Layout.ratioOf` uses `1000000L` (`Layout.java:335`) since RC-B8. The
ordering is unchanged so the assertions still hold; only the explanation a reader gets on failure is wrong.

---

## D — not defects

### TCX-D1 — The sandbox allow-list is not stale

I checked every entry. `testSwitchingToACentralStationLayout.java:230-297` pins `MODELS_WITHOUT_A_SANDBOX = 56` and 56
names; **all 56 files still exist, still build a model, and still fail the sandbox-before-build test.** The computed set
and the pinned set are identical today — nothing missing, nothing newly appearing. `assertEquals(checked, 17)` for the
window half also holds: 11 classes build a window with a model plus 6 that build one without.

The guard is well built, and its repair history (`:580-602`, `:653-658`, `:692-696`) shows each hole being closed as it
was found. Three residual observations, none a defect:

- The needles are literal substrings (`"MarklinControlStation.init("`, `"= init(null"`). A model built through a helper,
  or with the arguments wrapped across lines, would not match. I grepped for `new MarklinControlStation`, receiver-variable
  `init(` and whitespace-flexible spellings and got the same 79 files, so they are adequate today.
- The ordering test compares *textual* position, not execution order, so a `@BeforeClass` declared below a test method
  would read as an offender. No class is in that state.
- `testUiStateIsNotLostWhenUnreadable` is on the list and *does* call `LayoutSandbox.open()` — at `:135`, nine lines after
  the model build at `:126`, deliberately, for the window. Correctly counted as loose.

### TCX-D2 — Withdrawn: `testNoTestOpensTheOperatorsRailway` is not floorless

Raised in review as having no floor on `filesUnder(root)`. It has two: `assertEquals(checked, 17)` (`:659`) and
`assertEquals(loose, 56)` (`:714`). A scan that found nothing fails both. Withdrawn before filing.

### TCX-D3 — Withdrawn: `testConfirmedGoodState`'s early returns

`test/regression/testConfirmedGoodState.java:111` and `:143` return after asserting only non-null, having overwritten the
blessed baseline. Both are inside `if (capturing)`, where `capturing` is the explicit `-Dbaseline.capture=true` opt-in —
which is what re-blessing a baseline *is*. Not a defect.

### TCX-D4 — Withdrawn: `testAutonomyGroundTruth`'s pinned comparison

Raised as vacuous if `reachableStationPairs()` returned empty. `test/autonomy_formats/v2_8_1-station-paths.txt` holds
1,399 lines, so an empty result reports 1,399 vanished pairs and fails loudly. The sibling
`testTheRailwayStillHasItsStationsAndEdges` additionally pins 44 stations, 121 edges and 91 points. Sound.

### TCX-D5 — Withdrawn: my own worry about `maxTrainLength` per copy

I expected the typed maximum to live on one emitted copy of a square and not the others, which would let a train arrive by
the unguarded side. It does not: settings are keyed by **square**
(`AutonomySession.getPointProperty(square, "maxTrainLength")`, `:4728`), so every copy inherits it. Unfounded.

### TCX-D6 — `validateTrainLength`'s unguarded unbox is unreachable

`Point.validateTrainLength:915` does `loc.getTrainLength() <= this.getMaxTrainLength()` with no null check, while
`isPathClear`'s new rule twenty lines later does check (`Layout.java:2330`). The asymmetry looks like a latent NPE. It is
not reachable: the only UI writer is a combo box of `"0".."20"` (`GraphLocAssign.java:331`, `:215-217`), the constructors
set `0` (`Locomotive.java:159`, `:284`), and `Layout.java:7413/7430` writes an int or zero. Structurally real,
unreachable — a C at most, and not worth a test.

### TCX-D7 — Shared sensors and whole-block occupancy are well covered

Both are physical constraints of the real railway and both are tested properly, so I am recording that they were checked
rather than leaving their absence to be inferred. Shared s88: `testAutoLayout.java:1236`,
`testAutonomyDiagramReversal.java:87`, `testAutonomySimulationSanity.java:262`, `testHomeStaging.java:3923` (with a
control at `:3928`), `testStationBlockedByAnotherPoint.java:498`, plus `HomeStaging.blockedSensors`/sensor-sibling
handling. Whole-block occupancy: `testAutoLayout.java:470-511`, including the `isOccupied(loc, true)` variant.

Lock edges, by contrast, have no coverage on the staging side — `HomeStaging` deliberately does not consult them
(`HomeStaging.java:966-984`) and nothing asserts that absence. Noted here rather than filed, because whether the planner
*should* consult them is a design question and the absence is deliberate.

### TCX-D8 — `testTheParkingBerthsGetTheirTrainsBack` is a well-built test

Asked for explicitly. It is one of the better tests in the suite:

- It opens `LayoutSandbox.open(live)` **before** `init` (`:92`, `:95`), with the reason in a comment — the pattern
  TCX-B11 says `testTheGoldenLayoutHoldsTogether` should adopt.
- It uses addresses of its own (`FIRST_ADDRESS = 2101`) so it cannot collide with the live locomotive database.
- The javadoc names three hypotheses that were **eliminated by experiment** before it was written up — the backing-in
  rule, the search budget, and the fifth train's copy — which is the difference between a bug report and a guess.
- It asserts facing as the **Point**, not the square, and says why that is strictly stronger on a split platform.
- Its exclusion is honest: `testEveryTestIsInTheBattery.java:44-49` carries the reason and the condition for putting it
  back, and `testTheExclusionListIsStillOneEntry` (`:113-128`) forces the next person who adds an entry to change an
  assertion deliberately.

One weakness, filed above as TCX-C3: `assertEquals(STARTED_AT.size(), 5, ...)` at `:156` cannot fail. And one structural
consequence worth Adam knowing: because this class is out of the battery, **the only end-to-end coverage of backing-in,
non-reversible trains in parking terminuses, and split-platform facing on the real railway is in a file `ant test` does
not run.** That is the right trade while the defect is open; it is worth saying out loud that the trade has a cost.

### TCX-D9 — The one disabled test is deliberate

`testAutonomyDiagramSession.java:2602`, `@Test(enabled = false)`. The javadoc (`:2593-2601`) says it encodes what the
javadoc promises rather than what the code does, that it currently fails, that this is correct, and what would have to
change to re-enable it (TST-B15). Left off rather than deleted so the failure is not hidden. That is the right handling.

---

## Whole-class skips, and what catches them

Not a finding, because the suite already knows: `test/README.md:58` states that a class skipping every test *"reads as
green to `ant test`, where `battery.sh` classifies '0 passed, N skipped' separately and fails on it"*, and
`docs/tools/battery.sh:287-326` does exactly that — it counts "Total tests run: 0" and "Skips: N" apart from passes,
prints which classes and why, and fails the run.

Recorded here so the scale is visible: **20 classes, roughly 90 test methods, can report `Failures: 0` having asserted
nothing.** Fourteen of them are display-dependent (`ui/testDiagramLooksRight` alone is 20 tests). Six are
working-directory or fixture dependent: `ui/testRenderingCost` (8), `core/testTimetableOnDerivedGraph` (1),
`regression/testConfirmedGoodState` (2), `regression/testTheGoldenLayoutHoldsTogether` (5),
`core/testTheParkingBerthsGetTheirTrainsBack` (1), `core/testReturnHomeOnRealLayout` (2). Three skips are timing- or
machine-dependent and could fire on a slow or unusual host: `testMockCentralStation:403`,
`testTimetableOnDerivedGraph:263`, `testDiagramLooksRight:537` and `:924`.

One consequence worth noting: **no `@AfterClass` in the suite carries `alwaysRun = true`.** When
`testTheGoldenLayoutHoldsTogether`'s `@BeforeClass` skips, its `@AfterClass` write-detector does not run either. Harmless
as written — `before` was never captured, so there is nothing to compare — but it means the class produces no result of
any kind, and the protection people believe is in place is `battery.sh`'s own fingerprint rather than this one.

---

## Open questions — please run these serially, or answer them

None of these can be settled by reading.

1. **Does `isPathClear` refuse the path in TCX-A1's fixture today?** I read the code and believe it returns `true`. A
   three-point fixture ending at an ordinary station would confirm it in one run.
2. **TCX-A2:** does `auditAgainstRuntime()` report a divergence when the reversal-room rule fires? If it does, the
   planner gap is already instrumented and only needs an assertion; if it does not, the audit itself has a blind spot.
3. **TCX-B2, for Adam:** should the length notice name only the reversing square, or the whole run-in to it? The guard
   needs the second and the notice currently gives the first.
4. **TCX-A1, for Adam:** when a path turns the train at an intermediate point, which stretch of track should the train
   have to fit in — the segments before the turn, the segments after it, or the whole path as the terminus case does? I
   could not settle this from the code and did not want to assert a geometry I had not confirmed.
5. **TCX-B3:** is it intended that the reversal-room guard is inert on pages 1–4 of the real layout, given only six tile
   lengths are recorded and all six are on the Test page?
6. Whether the source-text assertions listed in TCX-C3 currently pass depends on the working directory. All of them fail
   loudly rather than silently when it is wrong, except the one in TCX-A3, which now passes for the wrong reason.
