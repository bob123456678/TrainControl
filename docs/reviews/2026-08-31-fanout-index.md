# The 2026-08-31 fan-out: five passes over rc4, and what came of them

**Status:** open

**Prefix for citing this index elsewhere:** `FAN`

**Reviewed:** `v3_0_0_rc4` (`e4c94ac9`), by five reviewers running in parallel on 2026-08-31.

This is an index, not a review. The README is explicit that documents recording separate passes must
not be merged - each one holds its own scope, method and blind spots, and that is the calibration
data. So the findings stay where they were made, and this says who found what, what has been fixed,
and what is waiting on Adam.

---

## The five passes

| Prefix | Pass | Document |
|---|---|---|
| `DAY` | The last day of commits - 42 of them, 2026-08-30 to rc4 | [day-of-commits](2026-08-31-day-of-commits-review.md) |
| `RGN` | Regression against `v2_7_4c`, the last release | [regression](2026-08-31-regression-review.md) |
| `IPR` | Independent - the reviewer chose the upgrade path | [independent](2026-08-31-independent-review.md) |
| `TCS` | The test suite: which of these tests cannot fail | [test suite](2026-08-31-test-suite-review.md) |
| `CDR` | Comments and documentation | [comments and docs](2026-08-31-comments-and-docs-review.md) |

None of them ran anything. Test JVMs share the Java **Preferences** store, and the sandbox fixture
redirects a key in it; five reviewers running tests at once means one fixture restoring that key while
another test is still running, which is how `cs2_sample_layout` was damaged on 2026-08-30. So they
read, and the verification below was done serially afterwards.

**The convergence is the most useful thing in the round.** Two passes with no knowledge of each other
went to the upgrade path and found the same class of defect there; two more, plus the main session
attacking its own work, landed on the same `claimHome` line. A finding that arrives twice by different
routes is worth more than either report alone.

---

## Fixed, with a test that was seen to fail first

| Finding | Who | What it was |
|---|---|---|
| `DAY-A1` | day-of-commits, and the main session independently | Two locomotives could be homed on one platform. MT-165 let a positional home onto a split square and left `claimHome`'s injectivity test comparing Points, so the far copy of a platform did not look spoken for. Return Home then answers IMPOSSIBLE naming both, for the rest of the session. Unreachable before MT-165, so last night's fix introduced it. |
| `DAY-B2` | day-of-commits | `atHome` reached five comparisons and not the two launch-pad ones, so a train on the far copy of its own home read as not standing there. |
| `RGN-A1` | regression | Importing a 2.7.4c `autonomy.json` kept only the points: thirteen run-wide settings and thirty-six timetable legs went, measured on Adam's own file. |
| `IPR-A1` | independent | The same import wrote a station's CAPACITY into the track's LENGTH - two different measurements, both present in a legacy file - so stations lost their limits and six squares gained lengths nobody measured. |
| `TCS-A2` | test suite | The only OB-159 test passed with OB-159 put back: it sampled a pixel the caption never paints. |
| group D | a test-suite descendant | The check that keeps the suite off Adam's real railway could not see half the windows - it matched only the unqualified constructor, and compared first occurrences. Seven of the sixteen classes that build a window were invisible to it. |

The rule "the copies of a square are one piece of track" now lives once, as `Point.isSamePlaceAs`.
Having it in `HomeStaging.atHome` and not in `Layout.claimHome` is precisely what `DAY-A1` was.

---

## Validation: the first round of fixes was attacked, and six things were wrong with it

An Opus validator was pointed at the two fix commits and told to attack them rather than confirm
them. It found six, two worse than what they replaced. All six were re-derived here before being
acted on, and all six are fixed in `f976306f`.

**The one that mattered.** The globals copy took every key that was not `points` or `edges`, and
`activateRoutes` / `activateRouteIDs` do not stay inside the configuration: `parseAuto` ends in
`applyAutonomyRouteActivations`, which walks the LIVE Central Station route database, disables every
route whose id is not listed, and enables and FIRES the ones that are. Adam's own legacy file carries
`activateRoutes: true` with an **empty** id list. So the fix for a data-loss bug would have switched
off every route he has, on import and again on every diagram edit. Both keys are excluded now.

**And the sandbox guard had, again, the hole it was written to close.** `withoutStrings` ran over
text a regex had already stripped comments from, so a string containing a line-comment marker lost
its closing quote and every quote after it was inverted; and a character literal holding a quote -
real code at `testARenameReachesTheTimetableOnScreen.java:96` - hid that class's window entirely. One
scanner reads character literals, string literals and both kinds of comment in a single pass over the
raw source now, and a probe carrying both constructs above a qualified window is caught.

Also: the setup exemption asked whether a file held ANY `@Before` annotation rather than whether the
sandbox was inside one (switching the count rule off for seven of sixteen classes); the timetable
copy is excluded, measured - two of thirty-six legs could survive and the next capture would write
the wreckage back permanently; `maxTrainLength` gap-fill accepted a zero, and 25 of Adam's 62 legacy
points carry an explicit zero; and the OB-159 sample sat 1.3 device pixels inside the painted art at
every size, with its control assertion after the assertion it controlled.

**This is the round's clearest lesson and it is not a new one.** Every fix in the first round was
made with a test seen failing first, and two of them still shipped defects worse than the bug. The
separate adversarial pass is what caught them, not the tests.

### And a second validation pass, which found four more

The scanner was ported character for character and run against an independent Java lexer over all 258
files: zero divergences, so it stands. What did not:

- **`inASetupMethod` looked back a fixed distance** from the enclosing method's header, which reaches
  over the previous method's body and finds ITS annotation - so a short `@BeforeClass` above a
  window-building test exempted the whole class. Bounded at the previous closing brace now.
- **And the test written for that caught the fix itself being broken.** The bound was generated as
  `"\n    }"` - a literal backslash and an n - so it never matched, the region fell back to the whole
  prefix, and every class with any `@Before` anywhere was exempt. Worse than what it replaced, green
  under the battery, and found only because the helper finally had a test.
- **One of my own assertions could not fail:** `assertFalse(globals.has("activateRouteIDs"))`, the
  more important half of the exclusion that stops an import disabling every route, was written against
  a fixture that never set that key.
- The window count stood at a floor of 14 against a real 17, and two comments still counted settings
  the code no longer carries.

Both helpers now have tests of their own. They decide whether the suite may open the operator's
railway, and until this round each had one call site and no assertion.

---

## Confirmed, not yet fixed

Verified by the main session, listed in severity order. Each is a finding in the document its prefix
names; this is not a second disposition, it is a pointer.

- **`RGN-A2` - the Auto tab is greyed for a legacy user, after their layout has been loaded.** Every
  link read and confirmed: `getAutonomySession()` returns a session for any local layout folder,
  startup parses the legacy file when no configuration is active, and `refreshAutonomyTabState` then
  computes `loaded` as false because `activeDiagramConfiguration` is null. So autonomy is live and
  the tab holding the run list, the timetable, the settings and Return Home is greyed, while
  `mountAutonomyControls` has removed the JSON window. **This one wants Adam's ruling** - see below.
- **`IPR-A2`** - `AutonomyReport.isClean()` counts dropped tile properties but `show()` builds its
  text from two name lists, so a save that prunes only tile properties shows no dialog at all.
- **`TCS-A1`** - the MT-149 timetable test drives the signature by reflection and never reaches
  `repaintTimetable`'s guard or the new call site.
- **`TCS-A3`** - the OB-164 lock-release guard has no test; the test naming that branch never
  populates `clearedEdges`.
- **`DAY-B1`** - MT-149's `repaintTimetable()` sits past two early returns about the companion setup
  file, so a timetable on a layout with no `config/autonomy/` still does not redraw on a rename.
- ~~**`DAY-B3`**~~ - **closed 2026-08-31.** The editor accepted a home on a split square and the
  loader dropped it, so `AutonomyBuilder.homeCopy`'s answer was discarded every time. Adam ruled on
  what a home is - "the home should just be the logical point, and the direction is wherever the
  locomotive was facing when it started moving" - and all three refusals went with it. See
  [MT-245](../manual-tests/tests.md#mt-245).
- **`RGN-B1`** - `migrateStationLabels` rewrites the user's own `.cs2` page files, one way, with no
  changelog line. Already happened on the real layout.
- **`RGN-B2`** - `skipAccessories` guards only the accessory branch, so a conflicted s88 route still
  runs stop, speed and function commands. The changelog says it "stops".
- **`IPR-B1`** - "Highlight on Diagram" calls `getAddress()` unguarded, so a route with a locomotive
  speed row throws out of the listener.
- **`TCS-B1`** - four classes close the sandbox in a `finally` and hand back an undisposed window.
  **Executed:** all four were run singly under the fingerprint guard and none wrote to
  `cs2_sample_layout`. Latent, not active - but it is the shape that caused the damage.
- **`TCS-B2`** - `testEveryLanguageFits`' own two safety guards pass on nothing.
- **An intermittent, found by the round's own batteries.**
  `core.testTimetableCaptureThroughARealRun.testARealRunCapturesNothingWithCaptureOff` failed one
  battery in three at the same commit: "no locomotive moved in 480 seconds ... Auto running: false",
  with all three trains reported free to be given a route, no exception and no stop message in the
  log. It passes alone, and the two batteries either side of it were green on byte-identical runtime
  code, so it is load sensitivity rather than a regression - but a test that fails one run in three
  is not a passing test, and the interesting part is that autonomy was not running rather than that
  nothing moved. Worth a look before the next release; the class already had its dispatch wait raised
  once for the same reason (commit 697417f9).
- **`CDR-B1`** - `LayoutLabel`'s javadoc still says z-order decides train-vs-label visibility.
- A dozen `MUTATION` claims across the suite name a mutation their test does not catch, or name the
  wrong assertion. The list is in the test-suite document; the two that matter most are the OB-116
  arrow test, whose own revert makes it **skip** rather than fail, and `testDiagramExport`, which
  never runs the listener it is about.

---

## What needs Adam, and why I did not decide it

1. **`RGN-A2`, the legacy Auto tab.** It may be intended that an upgrading user must import before
   getting the Auto tab. But then startup should not parse and activate their legacy file first -
   trains can be running with no interface to reach them, which is OB-104 turned around. The two
   halves disagree with each other today, and which one is wrong is a product decision.
2. **Legacy edge lengths.** A 2.7.4c file records track length per EDGE; the diagram records it per
   SQUARE. Thirty of Adam's ninety edges carry one. Which square an edge's length belongs to, when an
   edge spans several, cannot be answered without guessing - so the import now carries everything
   except these, and says so where it does it.
3. **The six-name exclusion list.** `cs2_sample_layout/config/autonomy/configuration-Main.json` is
   modified in the working tree - his own testing, written 01:59, before any of this ran. The
   test-suite reviewer noticed that the `excludedLocs` list at `1 - Main:14,3` is gone, and the
   language-harness javadoc records the 2026-08-30 incident as having lost an exclusion list. It may
   never have been put back. Nobody here should guess at his railway.
4. **The changelog.** Two shipped bugs fixed this cycle have no bullet - the non-atomic lock release
   (`OB-164`'s underlying defect, present at `v2_7_4c`) and the timetable not redrawing after a
   rename. Both are checked against the tag rather than assumed. Adam's rule is that only defects a
   real user could have hit belong there, and both qualify.

---

## What this round did not cover

- **95 of the suite's 200 `MUTATION` claims are still unchecked.** At the rate the checked ones
  produced defects, that probably hides two or three more.
- Autonomy runtime parity against 2.7.4c: `tools/parity/` still records an open loss, and no pass
  went there.
- The backup archive and the diagram editor, named by the regression pass as untouched.
- `Layout.java` and `TrainControlUI.java` were never read end to end by anybody. They are too large,
  and every pass sampled them.
- Nobody re-read the ~10,000 lines of test code written in the last two days, including the tests for
  the two fixes the day-of-commits pass found defects in.
