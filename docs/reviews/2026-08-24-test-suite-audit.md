# Test-suite audit: false assumptions, missing ground truth, incomplete coverage - 2026-08-24

**Prefix for citing this document: `TA`.** The commission asked for `TS`, but `TS` is declared by
[2026-08-19-test-suite-review.md](2026-08-19-test-suite-review.md) and a colliding prefix silently
breaks citations, which is the one thing the convention exists to prevent. `TA` is unused.

**Status:** open

**Version reviewed: `0b53abbc` (`autonomy-diagram-r0` HEAD), 2026-08-24.** Subject: the test suite
itself - 102 classes, ~49,500 lines under `test/` - read for three commissioned questions: tests
that assert something the code does not promise or that pass for a reason other than the one they
name; oracles derived from the code under test rather than from an independent statement; and
classes of behaviour nothing exercises. Application defects are out of scope except where a test
failed to catch one.

**Method.** Corpus-wide sweeps (randomness, timing assertions, swallowed catches, source-reading
windows, silent returns); full reads of the six source-reading suites, the ground-truth pin, the
settings matrix, and `testHomeStaging`'s staging tests; five scoped reconnaissance passes over the
persistence, network, autonomy-diagram, routing/refusal and ui clusters, every claim promoted to a
finding here re-verified against the source by this reviewer; and **20 mutation/probe experiments
(23 runs)**, each compiling a mutant into a scratch directory outside the repository and running the
guard class(es) one JVM at a time. No repository file was modified. Eight experiments demonstrated a
false pass or a hole; eight demonstrated a guard firing correctly (recorded under D, because knowing
where the real protection is matters as much as the gaps); one **disproved** a reviewer claim before
it could become a finding (TA-D4); the rest were probes and controls. A finding marked **[receipt]**
was demonstrated by execution; **[read]** was verified by reading the exact production and test code
but not run.

**Covered:** the six source-reading suites (all mutation-tested), `testHomeStaging`'s staging half,
`testAutonomyGroundTruth` and the `test/autonomy_formats/` fixtures, the persistence cluster
(`testDataSafetyRoundTrips`, `testAtomicWrite`, `testLoadData`, `testLocDB`,
`testUiStateIsNotLostWhenUnreadable`, `testLayoutFolderRobustness`, `testConfirmedGoodState`,
`testPageIdsAreDurable`), the network cluster (`testNetworkProxy`, `testMockCentralStation`,
`testControlStationFaults`, `testCS2Message`, `testFeedback`, `CS3TestServer`,
`testParseWebServer`), the autonomy-diagram cluster (Session, Store, SampleLayout, Monitor, Reducer,
and skims of Tiles/Ports/Reversal), the routing/refusal cluster (`testWhyStuck`,
`testStuckTrainAdvisory`, `testTriggerWaitsSayNothing`, `testNonReversibleTrains`,
`testMaxActiveTrains`, `testAutoLayoutRace`, `testRouteReachesTheRails`,
`testStationBlockedByAnotherPoint`, `testHomeAssignmentRules`, `testFacingFollowsTheTrack`,
`testBothProtectingSignalsAreThrown`, `testAutonomySimulationSanity`), the whole `test/ui/` folder,
`build.xml`'s battery, and the CRLF question across everything that reads source.

**Not covered**, beyond sweeps: the parser suites against CS2/CS3 fixture files (`testParseCS2Layout`,
`testParseCS2Routes`, `testParseCS3Loks`, `testParseCS3Routes`), the BFS/route corpus
(`testLayoutBfs`, `testLayoutBfsEquivalence`, `testRoutes`, `testAdvancedRoutes`,
`testLayoutPickPath`, `testRoutePicking`, `testRouteRoundTrip`, `testLayoutTimetable`,
`testTimetableOnDerivedGraph`, `testReturnHomeOnRealLayout` beyond seed/floor checks),
`testInvalidInput`, `testAccessory`, `testThreeWaySwitch`, `testTileSelection`, `testImportRename`,
`testMultiUnitMembership`, `testAutoLayout`, `testLocomotive` (beyond one finding),
`testLayoutRenameKeys`, `testLayoutReloadFence`, and roughly a dozen regression classes not named
above. The 2026-08-19 `TS` review read several of those in full; its open findings were
spot-checked rather than re-audited (see "Standing findings" at the end).

Not re-found, per the commission: OB-084 (`testRenderingCost` nondeterminism).

---

## Status

| # | Finding | Severity | Status |
|---|---|---|---|
| TA-A1 | The SV-B1 encoding guard never asserts the accented page's id, so a decode-drift regression renumbers every page and reattaches every setting with the test green **[receipt]** | A | Open |
| TA-B1 | `testEveryTestIsInTheBattery` is satisfied by a `<test-one-class>` line inside an XML comment **[receipt]** | B | Open |
| TA-B2 | `deletePage`'s per-setting gathering has no behavioural guard; a gather that mentions the collection and gathers nothing passes 85 tests across the four guard classes **[receipt]** | B | Open |
| TA-B3 | `testLoadData`'s seven old-build fixtures are asserted only for non-emptiness; a restore that keeps one component of each file passes all seven **[receipt]** | B | Open |
| TA-B4 | `testHomeAssignmentRules`' wiring check reads tokens, not call sites; deleting the only caller of the safety rule leaves it green - DD-A6's exact shape **[receipt]** | B | Open |
| TA-B5 | `testMockCentralStation`'s sync-safety block: a timeout assertion that cannot fail, a cumulative counter as proof of a fetch, a vacuous `before`, and a premise (`sync deletes locomotives`) that is false while the DB syncs *do* delete from (routes) has no garbled-fetch test **[read]** | B | Open |
| TA-B6 | The CS3 `isNotFoundError` JSON branch - the one a real pre-2.6.0 CS3 exercises - is never executed by any test; `CS3TestServer` encodes the opposite (HTTP 404) assumption **[receipt]** | B | Open |
| TA-B7 | The outgoing UDP wire format has no oracle anywhere; the short-datagram guard is untested; the reopen test never asserts reception resumes (it does not - the reader is left dead) **[read]** | B | Open |
| TA-B8 | `testFacingFollowsTheTrack`'s oracle is built from the same `getRoutes` map as the subject, so the MT-125 rule is satisfied by construction; and the class's only test silently `return`s when the reducer is null **[read]** | B | Open |
| TA-B9 | No test ever asserts `DiagramMonitor` *publishes*; both monitor tests hand it a null layout so `compute()` - the milestone/run/lock-wash core - is unreachable, and a monitor that computes nothing forever passes the suite **[read]** | B | Open |
| TA-B10 | `testRouteEditorLocked` cannot see table cell editors, so the `isCellEditable` locked gate - the guard for "the plus, the trash, the arrows" its own javadoc names - is untested; the focusability loop is vacuous-if-empty with its companion guarding a different frame **[read]** | B | Open |
| TA-C1 | `testABlockerWithAHomeOfItsOwn...` asserts `!= IMPOSSIBLE`, which cannot tell a refusal from a success; a planner with its search removed passes it **[receipt]** | C | Open |
| TA-C2 | `testCopiesOfOneSquareAreTheSamePlaceAndNeighboursAreNot` runs on a fixture that never splits, so `sameSquare`'s square lookup is never reached; the sibling carries the property **[receipt]** | C | Open |
| TA-C3 | `testAnUnmarkedLayoutIsUntouched` compares the builder's output to itself; there is no recorded "before", so the byte-format claim protects nothing **[receipt]** | C | Open |
| TA-C4 | `testItIsSaidOnce` is enforced by the log's duplicate suppression, not by the `advised` flag it tests; deleting the flag leaves it green **[receipt]** | C | Open |
| TA-C5 | `testDiagramExport`: the shortcut-parity test renders the same call twice; the size cap's expectation is the constant under test; nothing tests `writePng`, the entry point the application uses **[read]** | C | Open |
| TA-C6 | `testDiagramLooksRight`'s class javadoc claims OB-026 coverage; the on-rails measurement is printed, not asserted, and both run endpoints - including the curved station the class is about - are exempted **[read]** | C | Open |
| TA-C7 | Three `testWhyStuck` tests assert `contains(name)` where both candidate messages interpolate the name, so the occupied/excluded identities are unpinned **[read]** | C | Open |
| TA-C8 | `testAutonomySimulationSanity`: the path-validation assertion is free under `simulate: true`, and "changed stations" is counted by the dispatch loop's own callback **[read]** | C | Open |
| TA-C9 | Four more OB-084-class timing/environment dependencies: the sync-overlap `sleep(3000)`, exact counter deltas with a live UDP listener bound, a first-match log search, and a timed region containing its own 5-second fallback (which races toward a false PASS) **[read]** | C | Open |
| TA-C10 | Environment/liveness preconditions failing as accidents: `getLocList().get(0)` errors as `IndexOutOfBoundsException` on an empty DB **[receipt, incidental]**; five bare monitor threads with no liveness assert; `testNoRestrictionAddsNoLocks` all-continue loop with no precondition | C | Open |
| TA-C11 | Guards that cannot fire, dead clauses, and a swallow: the `.tmp` stray-file check (staging suffix is `.part`); `testLocDB`'s year-overlap clause and `validateNewAddress` never load-bearing; `testLocomotive`'s swallowed `changeLocAddress`; the `assertNotNull(testLayoutPickPath.class)` signpost | C | Open |
| TA-C12 | `testACorruptSetupFileIsRefusedRatherThanEmptied` refuses on a store that was already empty, so "leaves the previous contents in place" is asserted against nothing; the property is pinned elsewhere | C | Open |
| TA-C13 | Refusal messages and refusal terms with no test: `errorMaxActiveTrainsExceeded`, `errorTerminusNotAllowedForNonReversibleLoc`, `why.paused`/`startNotStation`/`startInactive`/`reversing`, `errorInactiveIntermediatePoint`; `trainsUnderway`'s union property and its `isAutoRunning()` term | C | Open |
| TA-C14 | The `legacy-graph*.json` fixtures are genuine ground truth by origin and inert in use: they exercise one bit of `detectImportFormat` that a two-line synthetic object also exercises, and no provenance is recorded anywhere | C | Open |
| TA-C15 | Smaller instances of shapes above, each verified: the reversal name-collision test asserts global uniqueness where the claim is directional; `testRenamingAStationTouchesNoPage`'s precondition proves the store, not the file write; `testAutoLayoutRace`'s live-map identity premise is unasserted; one `testEditorSurfaceRules` window is not comment-stripped (currently unexploitable) | C | Open |
| TA-D1 | A fresh CRLF checkout: all nine source-reading classes green - the FBR-C8 class is closed **[receipt]** | - | Recorded |
| TA-D2 | The source-reading guards kill real mutations: store-collections, javadoc ratchet, MT-116 at both sites **[receipts]** | - | Recorded |
| TA-D3 | The settings matrix behaviourally catches a neutered `forgetSquares` handling via its built-over row **[receipt]** | - | Recorded |
| TA-D4 | A claimed hole disproved by running it: reconcile's keep-side for blocked points IS guarded (`testTheRestrictionSurvivesTheFile` failed the mutant) **[receipt]** | - | Recorded |
| TA-D5 | Where the real protection sits when a named test cannot fail: the siblings behind TA-C1/C2/C3, each confirmed to fail the same mutant **[receipts]** | - | Recorded |
| TA-D6 | Clean checks across the clusters: what was read and held, including a premise correction about the ui folder's event thread | - | Recorded |
| TA-D7 | Standing findings from earlier reviews spot-checked: TS-B1, TS-C2 (partial), TS-C3, TD-5 all still true | - | Recorded |

No prior finding is re-lettered here; where an entry confirms an earlier document's finding it cites
that document's identifier.

---

# 1. False assumptions

Tests that assert something the code does not promise, or that would pass for a reason other than
the one they name.

## TA-A1. The SV-B1 guard cannot see the regression class that matters most

**A.** [testPageIdsAreDurable.java:577](../../test/regression/testPageIdsAreDurable.java)
`testAnIndexInThePlatformEncodingIsStillReadableAndWritable`, against
`LayoutDiagram.readIndexLines` (src/org/traincontrol/base/LayoutDiagram.java:927).

The test builds a page named `Bahnhof Süd`, rewrites the index in ISO-8859-1, and correctly asserts
the precondition (strict UTF-8 refuses the bytes). Then every assertion is about the *other* page:
`ids.get("Alpha") == 1`, `ids.size() == 2`, and after a write, `Alpha == 1` and `Zulu != null`. The
accented page's id - the thing the encoding fallback exists to preserve - is never asserted, before
or after the write.

**Receipt.** Mutant: replace the ISO-8859-1 fallback with a lenient UTF-8 decode
(`CodingErrorAction.REPLACE`) - a plausible "simplification" that decodes the name as
`Bahnhof S�d`. Compiled into scratch, run against the repo: **`testPageIdsAreDurable` 11/11 green.**
Under that mutant, `writeLayoutIndex`'s `existing.get(layout)` misses the real name, issues the page
a fresh id, and every stored setting keyed to the old id reattaches elsewhere - which is SV-B1, the
loss this class documents at length, reproduced with its guard green. Severity A because the cost is
the operator's accumulated setup silently rekeyed, in the exact area this test reads as protecting.

**Fix shape.** Two lines: `assertEquals(ids.get("Bahnhof Süd"), Integer.valueOf(2))` after the
read, and the same against `after`. The lenient-decode mutant above is the mutation that must fail.

## TA-B1. The battery guard is satisfied by a comment

**B.** [testEveryTestIsInTheBattery.java:79](../../test/regression/testEveryTestIsInTheBattery.java):
`xml.contains("<test-one-class class=\"" + name + "\"/>")` is a substring check on the raw
`build.xml`.

**Receipt.** Scratch working copy with `<test-one-class class="testHomeStaging"/>` wrapped in
`<!-- -->` - the natural "temporarily disable while debugging" edit, under which `ant test` never
runs the class: **3/3 green.** Control: the line deleted outright fails correctly (1 failure). So
the guard built after DD-A2 - thirty-five classes silently out of the battery - has a bypass along
the most likely path back into that state.

**Fix shape.** Strip XML comments before the `contains` (the suite already owns three copies of a
comment stripper), or parse the XML and read the actual macro invocations. The comment-out mutant
above is the mutation that must fail.

## TA-B4. A wiring check that cannot detect the loss of a caller

**B.** [testHomeAssignmentRules.java:129-139](../../test/regression/testHomeAssignmentRules.java).
The class javadoc names the defect precisely - "a rule with no caller passes every test written
about the rule" (DD-A6) - and guards it with three substring searches for tokens like
`HomeStaging.canBeHome` in `AutonomyEditorPanel.java`. The tokens live inside private helpers
(`mayRestHere`, the `homeBrokenBy` overload); the substring proves the helper's *body* still exists,
not that anything calls it.

**Receipt.** Scratch source copy with AutonomyEditorPanel.java:2775 deleted - the single call site
of `mayRestHere`, making rule 2's "home the locomotive cannot reach" warning unreachable exactly as
DD-A6 made `HomeLocomotiveMenu` unreachable: **5/5 green.** The same holds by inspection for
deleting the `homeBrokenBy` call at :3221 or the `promptHome` lambda at :935 (all three rules
unreachable at once).

**Fix shape.** Assert on the *call sites* (`mayRestHere(tile, picked)`,
`homeBrokenBy(tile, picked)`, `() -> promptHome(target)`), or reach the private methods reflectively
and drive them. The caller-deletion mutant is the mutation that must fail.

## TA-B5. The Central-Station sync-safety block asserts less than every sentence it says

**B.** [testMockCentralStation.java](../../test/core/testMockCentralStation.java), four defects in
one block, each verified against the source:

1. **:308 - a timeout assertion that cannot fail.** The "unreachable station" is a just-closed
   *local* port (:289-294), which refuses the connection in microseconds; `took < 30000` is then
   trivially true, and deleting `setConnectTimeout` at CS2File.java:416 changes nothing. The
   scenario the message describes - a station switched off, SYNs black-holed, the interface hanging -
   is this month's freeze class, and it is untested. Mutation that must fail a real version: remove
   the connect timeout with the fixture pointed at a non-routable address (e.g. 192.0.2.1) and a
   bound of ~5s.
2. **:347 - a cumulative static counter as proof of a fetch.** `requests` is class-static, never
   reset, and incremented by three earlier tests; `assertTrue(requests.get() > 0)` passes on a sync
   that fetched nothing. Its own comment claims the property that the `int before = requests.get()`
   pattern at :153 actually delivers.
3. **:381 - a vacuous `before`, guarding a code path that does not exist.** No
   `assertTrue(before > 0)`; and the class javadoc's premise ("a failed sync would wipe every
   locomotive") is false as the code stands - `syncWithCS2` never deletes locomotives. The database
   a sync *does* delete from is routes (`deleteRoute` at MarklinControlStation.java:1213/1226), and
   no test covers a garbled route fetch not deleting routes. That is the untested version of the
   risk this class was written for.
4. **:167/:205 - parity by sorted names only.** Addresses, decoder types, function maps and the
   entire `RouteCommand` list are outside the comparison; both sides share the parser, so only
   transport-level drops are caught - which is what the class javadoc promises, but not what the
   per-test docstrings claim.

## TA-C1. `!= IMPOSSIBLE` cannot tell a refusal from a success

**C.** [testHomeStaging.java:1576](../../test/core/testHomeStaging.java)
`testABlockerWithAHomeOfItsOwnIsNotAProofOfImpossibility` asserts `outcome != IMPOSSIBLE` plus the
blocked list - the same shape as the `!= READY` assertion this very file inverted on 2026-08-24
(FBR-B2), whose javadoc records the standard: assert the property that does not forbid the right
answer.

**Receipts.** (1) Probe: a strengthened copy asserting `READY` + `applyPlan` + `assertEveryoneHome`
is **green today**, so the stronger assertion costs nothing. (2) Mutant `SEARCH_LIMIT = 0` - a
planner that can never produce a plan and answers NO_PLAN_FOUND: **the test passes**; the full class
catches the mutant elsewhere (7 failures), which is why this is C and not B. The javadoc promises
"the search that would have found the two-move answer"; the assertions do not.

**Fix shape.** The three probe lines above. The SEARCH_LIMIT mutant is the mutation that must fail.

## TA-C2. A fixture too weak to reach the method under test

**C.** [testAutonomyDiagramSession.java:2274](../../test/core/testAutonomyDiagramSession.java)
`testCopiesOfOneSquareAreTheSamePlaceAndNeighboursAreNot`, on `pageOnDisk()` - a straight run whose
sensors sit at the ends, so each station reduces to exactly one Point, `copies.size() == 1`, and the
double loop degenerates to `sameSquare(x, x)`, which short-circuits on `a.equals(b)` before the
square lookup the test (and its whole doc comment) is about. The asserted precondition is the weak
one (`!copies.isEmpty()`).

**Receipt.** Mutant `StationIndex.sameSquare` gutted to `return a.equals(b)` - an index that cannot
recognise two copies of one platform as one place: **the test passes**; the sibling
`testTheIndexRoundTripsSquaresAndPoints` (which was moved to a splitting fixture for exactly this
reason, per its own comment at :3312) **fails**. The property is protected; this test contributes
nothing to it while reading as its primary guard.

**Fix shape.** Same fixture swap the sibling got, plus `assertTrue(copies.size() > 1, ...)`. The
gutted-`sameSquare` mutant is the mutation that must fail.

## TA-C4. The once-ness is enforced by the log, not by the flag

**C.** [testStuckTrainAdvisory.java:247](../../test/regression/testStuckTrainAdvisory.java)
`testItIsSaidOnce` counts captured log lines. Every repeat of the advisory is character-identical
(same locomotive, same sensor, `0` minutes for the whole 5-second test), and
`MarklinControlStation.log` drops messages equal to `lastMessage` - so one line arrives however many
times the advisory fires.

**Receipt.** Mutant: `advised = true;` deleted from Locomotive.java:783, so the advisory fires on
every poll forever: **the test passes.** The test credits its assertion to the flag; the log's
dedup does the work - the same shape as the `applyPlan` replay that FSR-C1 caught.

**Fix shape.** Count invocations of the `waitedTooLongFor` hook (the `Watcher` subclass three
sibling tests already use), asserting exactly one. The `advised`-deletion mutant is the mutation
that must fail.

## TA-C5. An export test that compares a render to itself

**C.** [testDiagramExport.java:189](../../test/ui/testDiagramExport.java)
`testTheActivePageDrawsTheSamePictureAsChoosingIt` calls `DiagramExport.render(page, 60, ui)`
twice - the identical call on the identical object - and asserts pixel equality. It proves the
renderer is deterministic and nothing about the active-page shortcut its name and javadoc describe;
that shortcut (`TrainControlUI.activeLayoutPage`) is never called by any test. **[read]** Also in
this class: `testAnAbsurdSizeIsCapped` compares a render at 100000 against a render at
`DiagramExport.MAX_TILE_SIZE` - the expectation is the constant under test, so setting the constant
to 100000 passes while producing the unopenable image the message describes; and no test anywhere
calls `DiagramExport.writePng`, the only entry point the application ships.

**Fix shape.** Drive `activeLayoutPage()` with a selection made in `LayoutList` and compare against
`render` of the *named* page (mutation that must fail: `getSelectedItem()` → `getItemAt(0)`); pin
the cap with a literal (`assertTrue(width < 20000)`).

## TA-C6. The claim says OB-026 is locked; the assertion was deliberately not written

**C.** [testDiagramLooksRight.java](../../test/ui/testDiagramLooksRight.java). The class javadoc
(:159-161) says "This is the check that would have caught OB-026 ... the route line is drawn ALONG
the railway, so its ink is on the rails." The method then measures exactly that (`offArt`) and -
with an honest, well-argued paragraph about tolerance-tuning (:296-308) - **prints it instead of
asserting it**, and exempts both run endpoints from the per-square ink check (:274, justified by
MT-076), the curved arrival being the tile OB-026 was about. A straight chord across the curve
strays nowhere, keeps its ink, and passes. The method-level honesty is exemplary; the class-level
claim was never reconciled with it, and a reader trusting :161 believes OB-026 is
regression-locked. **[read]**

**Fix shape.** Either soften the class claim, or assert `offArt` on the curve tile alone (where the
chord-vs-arc argument does not apply to a *station* curve) - mutation that must fail: revert the
overlay to a straight chord on curve tiles.

## TA-C7. `contains(name)` where both candidate messages contain the name

**C.** [testWhyStuck.java:56, :104, :306](../../test/core/testWhyStuck.java). The class doctrine
(:22-26) is that each reason *discriminates*, and three of its tests deliver that with
`assertEquals`. These three assert `reason.contains(loc.getName())` - but `why.occupied` ("{0} is
standing there.") and `why.excluded` ("This station does not accept {0}.") both interpolate the
name, so swapping the two message keys in `Layout.barredFromAutonomy`/`explainDestinations` passes
all three while the operator is told a busy station refuses their locomotive. **[read]** Fix: the
`assertEquals` the neighbouring tests already use.

## TA-C9. Four more OB-084-class dependencies on load and environment

**C.** Each verified in source; none receipted (they are races):

1. [testBusyDialogInteraction.java:421](../../test/ui/testBusyDialogInteraction.java) - the overlap
   in `testASecondSyncIsTurnedAway` is synchronised by `Thread.sleep(3000)`, and the `firstIsInside`
   latch counts down *before* the sync is called, so it proves nothing about being inside the
   guarded region. Fails spuriously on a loaded machine; the highest flake risk in `test/ui/`.
2. [testControlStationFaults.java:350](../../test/core/testControlStationFaults.java) - exact
   `numMessagesProcessed` deltas asserted while a live `NetworkProxy` listener is bound to UDP
   15730. On the author's LAN - the one machine this battery actually runs on, with a real Central
   Station - any inbound frame between the two calls breaks the equality, and one landing between
   the two `receiveMessage` calls un-duplicates the pair. The ping test (:124) has the same
   exposure. Fix: a model without a listener, or `stopListening()` first.
3. [testStuckTrainAdvisory.java:159](../../test/regression/testStuckTrainAdvisory.java) - `find()`
   takes the *first* "has not reached" line; a ~1s stall reorders which advisory logs first.
4. [testRouteReachesTheRails.java:191-197](../../test/core/testRouteReachesTheRails.java) - the
   timed region contains the harness's own 5000ms wait-for-start, so a route that *drops its
   delays* (the defect under test) and finishes before the first 10ms poll burns the full 5s and
   **passes** `took >= 1000`. This one races toward a false pass, not a false failure. Fix: start
   the clock at the first `isExecuting()` observation and `fail` on start-timeout; the floor should
   also be 1350 (3 × (50+400)), not 1000.

## TA-C10, TA-C11, TA-C12 - the remaining verified small ones

**TA-C10.** Preconditions that fail as accidents or not at all:
[testTriggerWaitsSayNothing.java:124](../../test/regression/testTriggerWaitsSayNothing.java)
`model.getLocList().get(0)` throws `IndexOutOfBoundsException` on an empty locomotive DB
(observed incidentally during this audit's CRLF experiment - the deliberate live-DB dependency
deserves a named assert, not an accident); the five monitor threads (:126-133) have no liveness
assert, so five lambdas dying on their first line leaves the silence assertion green (the file's own
sibling `testStuckTrainAdvisory:200` shows the `isAlive()` pattern); and
[testStationBlockedByAnotherPoint.java:148-173](../../test/regression/testStationBlockedByAnotherPoint.java)
`testNoRestrictionAddsNoLocks` is an all-`continue` loop with no "the fixture took" assert, in a
file whose neighbouring test has exactly that guard at :111.

**TA-C11.** Guards that cannot fire: the stray-file check at
[testDataSafetyRoundTrips.java:190](../../test/regression/testDataSafetyRoundTrips.java) asserts no
`.tmp` remains, but the staging suffix is `.part` (Util.java:63) and nothing in `src/` writes
`.tmp` - deleting `staging.delete()` at Util.java:418 leaves a `.part` behind and the loop green
(the property is covered for a different directory by `testAtomicWrite:158`). `testLocDB`: the
year-overlap clause of `findSimilarLocomotives` is never load-bearing for the fixture (no candidate
has a non-zero end below the target's start), `validateNewAddress` is only ever fed valid
addresses, and the `l == target` self-exclusion is unpinned. `testLocomotive` :510-515 swallows a
`changeLocAddress` exception in the case whose assertion (size unchanged) is also satisfied by the
call failing entirely. And
[testNonReversibleTrains.java:95](../../test/core/testNonReversibleTrains.java)
`assertNotNull(testLayoutPickPath.class)` can never fail; if it is a signpost it should reference
the delegate *method* so a deletion breaks it.

**TA-C12.** [testLayoutFolderRobustness.java:152-194](../../test/regression/testLayoutFolderRobustness.java)
`testACorruptSetupFileIsRefusedRatherThanEmptied` builds a *fresh* session over the corrupt file, so
"leaves the previous contents in place" is asserted against a store that never had contents - a
`load()` that cleared before reading would still pass. The property is genuinely pinned in
`testAutonomyDiagramStore` (:208, :1852); this test's name writes a cheque its fixture cannot cash.
Also in that file: the missing-page fixture puts the missing page *last*, so the page-id-advance
rule the source comments on (`CS2File.java:2228`) is never exercised - reorder the fixture and
assert the survivor's id is `"2"`.

---

# 2. Missing ground truth

Oracles derived from the code under test, fixtures built by the thing being verified, and recorded
files with no independent statement of what they should hold.

## TA-B3. Seven old-build fixtures, and not one recorded value from any of them

**B.** [testLoadData.java](../../test/core/testLoadData.java) reads `LocDB2_3_3` through
`LocDB3_0_0` - the only genuine old-build fixtures in the repository, one taken from a working
install - and asserts, for each, exactly `!model.restoreState(f).isEmpty()`. No component count, no
locomotive name, address, decoder type, function map or icon. There is no statement anywhere of what
any fixture contains, so a migration that loads a 2.5 file and drops all but one entry is invisible.

**Receipt.** Mutant `restoreState` keeping `subList(0, 1)` of every restored list: **7/7 green.**

**Fix shape.** A committed manifest per fixture (count + a few named locomotives with addresses and
decoder types), asserted against the restore - the `v2_8_1-station-paths.txt` pin shows the house
pattern. The subList mutant is the mutation that must fail. Related uncovered recovery path: the
LocDB copy-aside (`MarklinControlStation.java:1480-1505`, the twin of the UIState behaviour
`testUiStateIsNotLostWhenUnreadable` covers) has no test, and no truncated-`.data` fixture exists.

## TA-B6. The CS3 mock encodes the opposite of the assumption the code guards

**B.** `CS2File.isNotFoundError` has two ways to say "not found": a thrown `FileNotFoundException`
(HTTP 404), and an HTTP 200 whose body is `{"error":"Not Found"}` - the shape a real CS3 answers
for an unsupported endpoint, and the entire reason the JSON branch exists.
[CS3TestServer.java:126-134](../../test/support/CS3TestServer.java) sends a real 404, so only the
exception branch is ever executed; the JSON branch - the one real pre-2.6.0 firmware would take -
is dead in every test.

**Receipt.** Mutant: the JSON branch replaced with `return false;` - on real older firmware the
version probe then misidentifies, `getCS3LocDBUrl` picks the wrong endpoint, and every locomotive
import fails: **`testParseWebServer` green.**

**Fix shape.** A `sendJsonError` mode on `CS3TestServer` (200 + that body) and one test through it.
The branch-deletion mutant is the mutation that must fail.

## TA-B8. The MT-125 oracle is the defendant

**B.** [testFacingFollowsTheTrack.java:110](../../test/regression/testFacingFollowsTheTrack.java)
asserts every offered facing "points at track", where `uses()` checks membership of the facing in
`session.getRoutes(tile)` - the same map `AutonomySession.onwardFrom` (:3065-3078) *builds the
facings from*. Every facing the real branch produces satisfies the oracle by construction; the
assertion's only content is that the compass fallback (:3076) never fires badly on this layout. A
mutation that offers every route end - including the side the train arrived by, MT-125's defect
class - passes, because the arrival side is also "used" by track. Facing feeds the one-way edges
that *are* train direction in this model, which is why this is B and not C. And at :72 the class's
only test silently `return`s when `session.getReducer()` is null - a reduction that produces
nothing turns the class green with zero assertions run. **[read]**

**Fix shape.** One hand-written expected list for a named curve
(`assertEquals(session.facingChoices(tile), Arrays.asList(NORTH, EAST))` for the curve the javadoc
already discusses), and `assertNotNull(session.getReducer(), ...)`. Mutation that must fail:
`onwardFrom` returning all route ends regardless of arrival.

## TA-C3. "Byte for byte as it did before", with no before

**C.** [testAutonomyDiagramReversal.java:335](../../test/core/testAutonomyDiagramReversal.java)
`testAnUnmarkedLayoutIsUntouched` builds the configuration twice through the same builder - once
without the reversal feature mentioned, once with an empty set - and asserts the strings equal.
Both sides assign an empty set over an empty set; there is no recorded pre-feature output.

**Receipt.** Mutant `canTurn = true` (every square turns trains round): both sides split
identically, **the test passes**; the behavioural sibling
`testWithoutTheMarkTheReversalIsSimplyNotOffered` fails, so behaviour is covered - but the
*byte-format stability for pre-feature files* this test names is protected by nothing.

**Fix shape.** A checked-in golden string (this suite already knows how to pin - see
`autonomy_formats`), or at minimum assert the output contains no `terminus`/`reversing`/`(reverse)`
token. The canTurn mutant is the mutation that must fail.

## TA-C8. The simulation grades its own homework

**C.** [testAutonomySimulationSanity.java](../../test/core/testAutonomySimulationSanity.java). The
headline assertion - `getPathValidationFailureCount() == 0`, made twice - is nearly free: the
fixture sets `"simulate": true` and `configureAndLockPath` returns before `validatePathActuation`
under exactly that flag (Layout.java:2499), so the setup's careful arming of
`PATH_INTEGRITY_VALIDATION` arms a path that cannot execute. And "the train changed stations at
least 3 times" is counted from `CB_ROUTE_END` - the dispatch loop announcing its own completion -
rather than from any statement about where the train is. **[read]** Fix shape: sample
`getLocomotiveLocation` at each callback and assert the sequence of distinct stations, which is a
fact about the train; and either delete the validation assertion or run one segment un-simulated.
Also: `DEBUG_SIMULATE_PACKETS` and `PATH_INTEGRITY_VALIDATION` are set and never restored to their
previous values - a trap for any future parallelism.

## TA-C14. Genuine ground truth, inert in use

**C.** `test/autonomy_formats/legacy-graph.json` is byte-identical to `test/autonomy.json` (the
operator's hand-written v2.8.1 configuration - genuine ground truth by origin);
`legacy-graph-sample-layout.json` is a re-serialisation of the authored
`cs2_sample_layout/config/autorun/autonomy.json`. Exactly one test reads them
(`testEveryImportableShapeIsRecognised`, via `detectImportFormat`), and the verdict they exercise is
one line - `optJSONArray("points") != null` - which a two-line synthetic object 75 lines below
exercises identically. There is no mutation of `detectImportFormat` the real files catch that the
synthetic misses; the 200-point richness (lockedges, placements, duplicate homes) is never fed to
`importLegacy`, which is only ever exercised on 1-2 point hand-built JSON. No provenance is
recorded in the folder. **[read]** Fix shape: an `importLegacy(legacy-graph-sample-layout.json)`
run against the parsed `cs2_sample_layout` diagram - the only matched pair in the repo - asserting
matched/unmatched/duplicate-home counts against hand-counted figures; and a three-line README in
`autonomy_formats/` saying where each file came from. Kill-mutation for the recommended test: any
change to `importLegacy`'s s88 matching.

For the record under this heading: `testAutonomyGroundTruth` and `v2_8_1-station-paths.txt` are the
suite's model citizens - the pin says on its face what it is, what it cannot prove, and what a
changed line means. `testConfirmedGoodState` is the opposite: a change detector whose baseline
(`test/baseline/`) still does not exist, so both tests skip on every run - TD-5 confirmed still
true, and two more entries in any "N classes green" figure that execute zero assertions.

---

# 3. Incomplete coverage

Classes of behaviour, not lines.

## TA-B2. Deleting a page: the one bookkeeping operation with no behavioural guard

**B.** The store's own doctrine (testStoreCollectionsAreHandledEverywhere's SITES comment) is that
`deletePage`'s *gathering* has to know every collection, "one missed means a page's worth of that
one setting survives the page, keyed to track that is gone" - and page-id reuse
(testPageIdsAreDurable's subject) then reattaches it to an unrelated new page. The textual guard
requires only that each collection's *name* appears in the method; the settings matrix
(testAutonomyStoreSettingsMatrix) has move, build-over, restore, rename and save/load columns - and
no deletePage column.

**Receipt.** Mutant: the `disabledPortals` gathering loop in `deletePage`
(AutonomyCompanionStore.java:2199) neutered while still naming the collection
(`squares.add(key)` → `squares.size()`) - the exact historical defect class, on the exact
collection whose four missed sites cost five commits: **`testStoreCollectionsAreHandledEverywhere`,
`testAutonomyStoreSettingsMatrix`, `testPageIdsAreDurable`, `testAutonomyDiagramStore` all green -
85 tests.**

**Fix shape.** A `deletePage` column in the matrix: write each setting on page B, `deletePage("B")`,
assert absent - and reuse the id, asserting the new page does not inherit it. The neutered-gather
mutant is the mutation that must fail. (The matrix's *other* rows genuinely cover the
forget/build-over bookkeeping - see TA-D3.)

## TA-B7. The wire the railway hangs off has no oracle

**B.** Only two test files mention `DatagramSocket` at all, and neither asserts a transmitted byte.
There is no test anywhere stating what bytes TrainControl sends for an accessory switch, a system
stop, a speed command - the CS2Message unit tests pin parsing and masking (well - see TA-D6), but
the outgoing datagram, the thing that moves the physical railway, is unverified against any
independent statement. Three adjacent holes, each verified in source **[read]**:

- **The short-datagram guard is untested.** `NetworkProxy.java:214`
  (`packet.getLength() == buffer.length`) is what stops a truncated datagram being parsed as its
  own header plus the *previous* packet's tail - a stale command re-applied under an unrelated
  command byte. Delete it and nothing fails. The missing test is a `DatagramSocket` sending 7 bytes
  to 15730 and asserting `getNumMessagesProcessed()` did not move.
- **The reopen test never asserts reception resumes - and it does not.** `ReadMessages` is started
  only in `setModel`; after `testClosingTheSocketStopsTheListener` proves the reader exits,
  `testSendReopensAClosedSocket` asserts the socket reopened and stops there. `sendMessage`'s
  reopen also does not assert the *port*: `new DatagramSocket()` (ephemeral) instead of
  `RX_PORT` passes both assertions with reception permanently dead - the A8 symptom reintroduced
  through the A8 fix's own test. Two one-line asserts close it
  (`getLocalPort() == RX_PORT`, `countReaderThreads() == 1` - the second fails today, which is a
  question for the application, surfaced here because the *test* stops one assertion short of it).
- **The `RuntimeException` catch that keeps the listener alive** (`NetworkProxy.java:246`, "a
  single malformed packet must not stop reception") is unexercised - the fake socket only ever
  throws `IOException`.

The recommended new test for the first bullet is the highest-value cheap test this audit found:
bind, send 7 bytes, assert no count. Kill-mutation: delete the length guard.

## TA-B9. Nothing ever proves the monitor says anything

**B.** Both tests in [testAutonomyDiagramMonitor.java](../../test/core/testAutonomyDiagramMonitor.java)
that install a `Publisher` hand the monitor a `LayoutSource` whose `get()` returns null and assert
the publisher was **not** called; no test in the repository asserts `publish` is *ever* invoked
with a non-empty picture. `compute()` - 128 lines holding the milestone→REACHED rule, run
concatenation, the location fallback and the lock wash - returns at its null-layout check before
any of that runs, in every test (its own javadoc concedes the surface "cannot be reached from a
test at all"). A monitor that computes and publishes nothing forever passes the class, overlay
algebra and all. **[read]** The overlay/geometry/paint halves of the class are genuinely strong
(TA-D6); the *live* half - the thing the operator watches while trains move - is the gap.
Recommended test: seed `edgesByName`/`pointTiles` (both injectable), a stub layout, and assert one
positive publish plus the second-identical-picture suppression. Kill-mutation: make `refresh()`
skip the `publisher.publish(...)` call.

## TA-B10. The locked route's editing surface, guarded around rather than through

**B.** [testRouteEditorLocked.java](../../test/ui/testRouteEditorLocked.java) names the defect
surface itself: "the plus, the trash, the arrows" on a Central-Station-owned route. The production
gate for typing into command/condition cells is `isCellEditable` → `if (locked) return false;`
(RouteEditorFrame.java:2751) - and no test anywhere references `isCellEditable`; the focusability
walk cannot see fields inside cell editors (not in the component tree until editing starts), and is
itself vacuous-if-empty with its non-vacuity companion opening a *different* frame through a
different branch. Meanwhile `testCommandTableMarks` drives `clickCommandMarkForTest`, a test hook
that dispatches straight to `moveRow`/`deleteRow` past the locked-empty-mark check the real click
path reads. Deleting the `locked` line at :2751 makes a Central Station route's cells typeable and
nothing fails. **[read]** Recommended test: on the locked frame, assert
`model.isCellEditable(row, col)` is false for every cell and every mark column returns the empty
mark. Kill-mutation: delete RouteEditorFrame.java:2751.

## TA-C13. Refusals the operator sees, asserted nowhere

**C.** The refusal *decisions* are mostly well covered (see TA-D6 - `testMaxActiveTrains` and
`testStationBlockedByAnotherPoint` are model constructions), but across the repository these
operator-facing pieces have no assertion: `errorMaxActiveTrainsExceeded`,
`errorTerminusNotAllowedForNonReversibleLoc`, `errorInactiveIntermediatePoint` (the branch the
source calls "the absolute case"), `autolayout.why.paused`, `why.startNotStation`,
`why.startInactive`, and `why.reversing` (whose absence also means nothing pins that a reversing
point lands in the barred-from-autonomy group at all - FR-017's exact mis-grouping). Two structural
terms nearby: `trainsUnderway`'s union property (a locomotive both claiming and registered must
count once - `size() + size()` passes every current test) and its `isAutoRunning()` scoping
(deleting it makes the cap bind hand dispatches, invisibly). The blocked-station suite shows what
right looks like - its message is pinned in both directions (:602/:608). **[read]**

## The broad holes, in prose

**UDP and the physical railway.** Everything under TA-B7, plus: no test of out-of-order or
non-adjacent duplicate datagrams (the dedup slot is single-entry), the CS2 ping/handshake decode
(`(UID - 0x43533200) / 2` - the serial-number arithmetic has no test and its only "captured" test
vector is the suspect one in TA-D6.3), reconnection/re-sync after a station returns, or
`isPortInUse`'s locale-dependent substring match. What simulation misses is precisely what these
would catch: the model's tests all live above the socket.

**The event thread.** The commission's premise needed correcting, and the correction is recorded
here because the next auditor will start from the same premise: the ui folder does have an event
thread - every class builds its Swing objects under `invokeAndWait`, and `testBusyDialogInteraction`
pumps a genuinely modal dialog with real thread-affinity and ordering assertions (two of them, the
only two in the suite). What remains uncovered: the three blocking `JOptionPane`/`JFileChooser`
calls on the export path, freeze shapes outside `BusyDialog` (the connect-timeout hole of TA-B5.1
is the network end of the same class of defect), and - ironically - four test classes call
`setViewListener` *off* the EDT themselves, so the tests reproduce the bug's preconditions rather
than detect them. Every `test/ui/` class skips headless; the battery runs on a machine with a
display today, so this is latent, but a green headless battery would silently drop the entire
folder plus `testUiStateIsNotLostWhenUnreadable`'s data-recovery guard.

**Persistence across builds.** Old-build fixtures exist only for LocDB (TA-B3). There is no
old-build `setup.json`, `configuration-*.json`, `UIState.data` or `gleisbild.cs2` fixture - every
test of those formats is same-build write-then-read, so a format drift that round-trips cleanly in
the current build but misreads last month's files is invisible. No truncated-file fixture exists
for any serialized format; `zipInto` (the backup writer) is itself non-atomic and only ever tested
on the happy path; `writeLayoutIndex`'s cannot-read refusal branch (`getUnreadableIndex`) is
untested. `testDataSafetyRoundTrips`' export test asserts `setup.json` unchanged around a call that
performs no file I/O - true by construction - while the export path that does touch disk
(`AutonomyViewerPanel.exportConfiguration`) is untested.

**What never runs, and where that is recorded.** `testAutoDetect` (recorded: build.xml comment, the
battery script, and `DELIBERATELY_OUT` with its reason - the good pattern, minus TA-B1's bypass);
`testConfirmedGoodState`'s two tests (recorded as TD-5/MT-140, still true); every `test/ui/` class
plus parts of five core/regression classes on any headless machine (recorded only as a build.xml
comment); `DiagramMonitor.compute()` (recorded in its own javadoc; TA-B9); and the five
`SkipException` paths feeding both tests of `testDiagramLooksRight`, any of which silently empties
the class.

---

# 4. D - checked and sound, receipts and reconciliation

**TA-D1. The CRLF class of environment-dependence is closed.** [receipt] The whole working tree
(`src`, `test`, `build.xml`) was converted to CRLF - what a fresh clone yields under
`core.autocrlf=true`, this tree being LF only by historical accident - and all nine source-reading
classes ran green: `testEditorSurfaceRules`, `testLocomotiveIdentityPropagates`,
`testStoreCollectionsAreHandledEverywhere`, `testJavadocsAreAttached`, `testEveryTestIsInTheBattery`,
`testNoSelfRecursiveWrappers`, `testTriggerWaitsSayNothing`, `testHomeAssignmentRules`,
`testMessageBundles`. The FBR-C8 repairs (the `.replace("\r", "")` lines) hold, and the patterns
that bound windows on a *leading* `\n` are CRLF-safe by shape.

**TA-D2. The source-reading guards guard.** [receipts] Three mutations, each caught: the
`moveMembers(disabledPortals, ...)` line deleted from `moveTiles` →
`testStoreCollectionsAreHandledEverywhere` failed; one orphaned javadoc pair added →
`testJavadocsAreAttached` failed (and its ratchet equality is currently exact at 98); the MT-116
guard clause deleted at *each* of the two rename sites separately → `testEditorSurfaceRules` failed
both times. The repairs recorded in these files' comments (TD-3 comment-stripping, NR-4
project-wide sweep, NR-8) were each re-verified as present.

**TA-D3. The settings matrix covers the forget bookkeeping behaviourally.** [receipt]
`forgetSquares`' `tileLengths.remove(key)` neutered to a read - the name still present, so the
textual guard stays green - and the matrix's built-over row caught it (`moveTiles` routes through
`forgetSquares`). This is why TA-B2 is specifically about `deletePage`'s *gathering*, the one step
the matrix's operations never route through.

**TA-D4. A claimed hole that was not one.** [receipt] A reviewer pass claimed reconcile's keep-side
for blocked points was uncovered ("change `kept.add` to unconditional drop and every restriction on
the layout is deleted on the next edit, tests green"). Run, it is false:
`testStationBlockedByAnotherPoint.testTheRestrictionSurvivesTheFile` failed the mutant. Recorded
per the withdrawn-findings rule: the claim was plausible, specific, and wrong, which calibrates the
unreceipted claims elsewhere in this document - it is why every B above is either receipted or
marked [read] with the exact lines re-verified.

**TA-D5. Where the real protection sits for TA-C1/C2/C3.** [receipts] Under each of those mutants
the named test stayed green and a sibling failed: `testHomeStaging` at large (7 failures under the
neutered search), `testTheIndexRoundTripsSquaresAndPoints` (gutted `sameSquare`), and
`testWithoutTheMarkTheReversalIsSimplyNotOffered` (`canTurn = true`). The suite holds; the named
tests are the untidy part.

**TA-D6. Clean checks, by cluster.**

1. **Persistence:** `testAtomicWrite`'s three-way core guarantee is real (a direct-write mutant
   fails all three, per trace); `testDataSafetyRoundTrips`' `.bak`-written-once uses a marker no
   save path emits - a genuine oracle; `testUiStateIsNotLostWhenUnreadable` asserts the copied-aside
   file by *content*, and its careful setup/teardown survive scrutiny; `testPageIdsAreDurable` is,
   TA-A1's one hole aside, the best persistence class in the suite - its fixture guards assert
   their own non-vacuity and its javadocs measure their mutation claims instead of deriving them.
2. **Network:** `testNetworkProxy`'s A8 fault injection genuinely forces the
   blocked-receive/closed-socket distinction; `testCS2Message` is the strongest bit-level coverage
   in the repo, and its three verifiable captured headers check out against the protocol.
3. **One caveat kept honest:** the fourth "captured" vector
   (`testCS2Message:46`, named "a ping") decodes to command `0x1b`, which is not `CAN_CMD_PING`
   (`0x18`) under any reading - the expectation was evidently regenerated from the decoder rather
   than read off a capture, and it is the only header in the file whose command is not a named
   constant. Worth resolving; the other three carry the file. Similarly `testFeedback` was
   suspected vacuous by a reconnaissance pass and is not: with `IGNORE_SUB_INTERVAL == 0` the gate
   is deliberately a no-op and the test pins the *sign* of the comparison (the NTP regression
   fails it); what is true is that the reject side is untested and would stay untested if the
   interval ever became non-zero.
4. **Routing/refusal:** `testStationBlockedByAnotherPoint` is a model class - refusal, restoration,
   exemption, and exemption-did-not-swallow-the-rule, with the operator message pinned in both
   directions; `testMaxActiveTrains`' refuse/allow pair on an identical fixture is the right
   construction; `testNonReversibleTrains`' paired assertions make its `assertFalse` mean the
   terminus rule; `testWhyStuck`'s discriminating tests (inactive, noRoute, notAutoDestination) use
   `assertEquals` as the doctrine demands. `testAutoLayoutRace`'s two timing bounds were analysed
   and are sound - both thresholds sit in genuine gaps, not near either outcome - and its
   advisory observations go through the hook, not the log.
5. **Autonomy diagram:** the Session/Store suites' failure-atomicity tests assert *what was left
   behind*, not just that it threw; the page rename-vs-renumber triangle asserts the negative half;
   the ports table restates the whole port map in Java with a size guard so a new tile type cannot
   arrive silently; the ground-truth pin and the sample-layout suite's one genuinely external
   oracle (every legacy s88 is derived) hold. `testHomeStaging`'s FBR/FSR repairs - the inverted
   assertion, the replay-first ordering, the widened `applyPlan` - are all present and correct as
   their comments claim.
6. **ui:** `testBusyDialogInteraction` is the strongest EDT coverage in the suite (see the premise
   correction above); `testDiagramExport`'s three `LayoutGrid` lifecycle tests (discard-on-replace,
   weak-map retention, the twelve-cycle leak check) are the most valuable tests in the folder;
   `testCommandTableMarks`' distinguishability guard against vacuity is exemplary.

**TA-D7. Standing findings spot-checked, all still true, none re-found:** TS-B1 (route-command
parity: still 7 of 12 kinds, still zero delay-bearing commands); TS-C2 (unseeded `Random`: fixed in
`testTimetableOnDerivedGraph`, still present in `testRoutes` and `testReturnHomeOnRealLayout`);
TS-C3 (`testRouteInventory`'s six silent `return`s on missing bundles - still there); TD-5 /
MT-140 (`testConfirmedGoodState` baseline still absent, both tests skip every run).

---

## The pattern, in one paragraph

This suite's distinguishing habit - javadocs that argue, preconditions that assert their own
non-vacuity, repairs annotated with the review finding that caused them - is real and holds up
under mutation better than any suite of this size has a right to. Where it fails, it fails in one
recurring way: **the assertion is one step short of the sentence above it.** The encoding test
asserts the ASCII page, not the accented one it exists for; the battery guard asserts the substring,
not the effective line; the wiring check asserts the token, not the caller; the once-advisory test
asserts the log, not the flag; the export test asserts determinism, not the shortcut; the monitor
tests assert silence, not speech. Each sentence was right and each assertion true - they just do
not meet. The receipts above are the meeting points; each B and A names the one or two lines that
close the gap, and the mutation that must fail once they are written.

---

## Dispositions

**Claude, 2026-08-24.** Four fixed, the rest filed as [OB-089](../manual-tests/issues.md).

| | What was done |
|---|---|
| **TA-A1** | Fixed. The encoding test asserted `Alpha` - pure ASCII, which survives any decoding - and never the accented page, so it was asserting the half that cannot fail. It now asserts that page by name, and the lenient-decode mutant this finding used fails it on that assertion. The hole was in a test I wrote this session to guard exactly this loss. |
| **TA-B1** | Fixed. `build.xml` is read with its comment spans removed, so commenting a class out no longer satisfies the guard against classes leaving the battery. Both mutations re-run: commented out now fails, deleted still fails. |
| **TA-B4** | Fixed. The three call sites are asserted alongside the three tokens. A private helper cannot be reached from the test's package, so this is the same source read one level up rather than a real invocation - it proves the path is still written down, which is one more link than before. The caller-deletion mutant fails it. |
| **TA-B2, B3, B5, B6, B7, B8, B9, B10 and the fifteen C findings** | Filed as OB-089, with a starting point: TA-B9 and TA-B7 are the two with no coverage rather than weak coverage, and both are close to the railway - a monitor that stops publishing and a datagram that goes out malformed are invisible to every test that exists. |

### Two things this audit did that are worth more than its findings

It **disproved a candidate before filing it** - `reconcile`'s blocked-points keep-side is guarded - and
it **corrected a premise in the brief I wrote it**. I said no test drives a real event thread;
`testBusyDialogInteraction` pumps a real modal EDT, and the genuine hole is narrower than I claimed.
Both belong in the record, because a reviewer that only ever confirms the commissioner's framing is
worth less than one that argues with it.

The twenty mutation experiments are the substance here. Eight demonstrated a false pass by making the
change the test should have caught and watching it stay green; eight demonstrated a guard firing
correctly, which makes those eight known-good rather than assumed-good. That distinction is the whole
value of the pass.
