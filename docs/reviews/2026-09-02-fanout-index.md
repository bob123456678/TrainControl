# The 2026-09-02 round: seven reviewers, four validation passes, and what came of them

**Status:** open

**Prefix for citing this index elsewhere:** `FX3`

**Reviewed:** branch `autonomy-diagram-r0`, prepared for release as v3.0.0. The fan-out read
`cf048f9b`; the validation rounds each read the commit before them. Fixes below run to the head of the
branch.

This is an index, not a review. The README is explicit that documents recording separate passes must
not be merged - each holds its own scope, method and blind spots, and that is the calibration data. So
the findings stay where they were made; this says who found what, what was fixed, and what is left.

---

## What this round was asked to do

Adam, 2026-09-02: *"It looks like the 9/1 code review has many outstanding items or dispositions that
need updating... iterate on and address all outstanding issues that can be validated from the many 9/1
reviews. Then, repeat a new fanout per the same model setup as 2026-09-01-fanout-index, except this
time send out an additional Fable reviewer (not replacing the Opus) to look at the past 3 days of
commits. Once the results are in, perform up to 5 validation rounds as you iterate on the findings."*

Three things, in that order, and all three were done.

---

## Part one: the 2026-09-01 round, dispositioned

That round left **A: 0 open, B: 20, C: 64** undispositioned, and the index said so rather than leaving
it to be inferred from a tidy table. Every finding in it now carries a disposition line under its own
heading.

| | |
|---|---|
| Fixed on 2026-09-02 | 18 |
| Closed by Adam's rulings of 2026-09-01 | 14 |
| Corrected after being wrongly closed | 1 (`TCX-B2`) |
| Still open | 19 B, 63 C |

The one correction is worth its own line. `TCX-B2` was marked closed by `FX2-3` in the morning sweep,
and that overstated the ruling: `FX2-3` put the reversal-room RULE's soundness to Adam and he answered
"OK"; the question `TCX-B2` asks - should the notice name the reversal square or every square on the
run-in - was never put to him. The README is explicit that a finding is closed when it is fixed,
withdrawn or explicitly declined, and never because a later document covers the same area.

The headline items:

| Finding | What it was |
|---|---|
| `SVN-A3` | the page-switch teardown ran its continuation BEFORE the refresh, so the refresh arrived last and re-enabled Edit Layout with the editor still open |
| `TCX-A2` | the reversal-room rule was in `isPathClear` and nowhere in the staging planner, so Return Home offered berths the railway refused on the first move |
| `SVN-B6` / `D24-B5` | **Out of service did nothing at all on a plain square**, in two places at once, while the editor drew a cross for it |
| `SVN-B13` | two locomotives homed on two copies of one square walked past the loader - the state `sharesSection` answers IMPOSSIBLE about for the rest of a session |
| `SVN-B7`, `SVN-B10`, `SVN-B16` | three guards that were each on one door of several |
| `R28-C1` | **Clear All Home Locomotives**, which Adam asked for back, with its 2.8.1 confirmation |
| five `TCX` items | tests that could not fail |

---

## Part two: the fan-out

Seven reviewers, the 2026-09-01 setup plus the additional Fable pass Adam asked for.

| Prefix | Pass | Model | Findings |
|---|---|---|---|
| [`RT3`](2026-09-02-autonomy-routing-review.md) | Autonomy algorithm and routing | Fable | 2 B, 1 C, 15 D |
| [`D3F`](2026-09-02-three-days-review.md) | The past three days of commits | Fable *(the added pass)* | 1 B, 6 C, 10 D |
| [`WK3`](2026-09-02-week-of-commits-review.md) | The past week of commits | Opus | 1 A, 2 B, 3 C, 11 D |
| [`DY3`](2026-09-02-last-day-review.md) | The last day of commits | Opus | 1 A, 1 B, 10 C, 12 D |
| [`RG3`](2026-09-02-regression-vs-2.8.1-review.md) | Regression against v2.8.1 | Opus | 2 B, 6 C, 13 D |
| [`TS3`](2026-09-02-test-suite-review.md) | The test suite | Opus | 1 A, 7 B, 6 C, 7 D |
| [`CD3`](2026-09-02-comments-and-docs-review.md) | Comments and documentation | Sonnet | 3 B, 7 C, 9 D |

**Every A and every cross-confirmed B was in the morning's own fixes.** That is the round's most
useful result and it is stated first rather than buried: seven reviewers pointed at code written in
the previous six hours.

| Finding | Found by | What it was |
|---|---|---|
| `WK3-A1` / `DY3-A1` | two, independently | `protectsAnOccupiedSquare` was `synchronized` and called from the event thread, four lines below the method whose javadoc says that exact call froze the window, Stop included |
| `TS3-A1` | one | `battery.sh` had called its reaper at a path that has not existed since 2026-08-30, with the error going to `/dev/null` - four days of runs reaped nothing and said nothing |
| `WK3-B2` / `D3F-B1` / `RT3-B1` | three, independently, and `TS3-B7` filed the missing test | the planner's new room check ran after the arrival was recorded as seen, so the "another route may be longer" escape it depends on was already closed |
| `WK3-B1` / `DY3-B1` / `D3F-C4` | three | the diagram tile asked the protecting-signal rule without the aspect, so setting a signal RED - the protective act - raised a warning the route door explicitly does not |
| `TS3-B6` / `WK3-C1` / `D3F-C3` | three | the start guard was widened and the affordances were not |
| `TS3-B2` | one | a test left the shared locomotive mutated for its 84 siblings |

---

## Part three: four validation rounds

Adam allowed up to five. Four ran, and each found something in the round before it.

Round four found no defect in the CODE and three false sentences in what had been written about it -
which in this project is the design record, and so is a defect. That is the shape a round stops on.

| Prefix | Round | Scope | Findings |
|---|---|---|---|
| [`V31`](2026-09-02-first-validation.md) | 1 | the 2026-09-01 round's fixes | 2 B, 4 C, 16 D |
| [`V32`](2026-09-02-second-validation.md) | 1 | the fan-out's fixes | 2 B, 6 C, 8 D |
| [`V33`](2026-09-02-third-validation.md) | 1 | every test and the harness | 1 A, 1 B, 12 C, 14 D |
| [`V34`](2026-09-02-fourth-validation.md) | 2 | round 1's fixes | 1 B, 8 C, 9 D |
| [`V35`](2026-09-02-fifth-validation.md) | 2 | "is anything worse for the operator?" | 3 C, 12 D |
| [`V36`](2026-09-02-sixth-validation.md) | 3 | round 2's fixes | 1 A, 2 B, 6 C, 9 D |
| [`V37`](2026-09-02-seventh-validation.md) | 4 | round 3's fixes | 2 B, 3 C |

`V36-A1` is the finding of the day, and it is against my own work rather than against the code.

The battery caught `testTrainsComeHomeToTheirPlatforms` failing, and my answer was a branch: ask
`canReachAnyDestination` of each train and, if one was trapped, assert that no plan exists and return.
Three things were wrong with it. The predicate is the FULL-AUTONOMY one and requires
`isAutoDestination`, which Return Home explicitly ignores. The assertion did not follow, because a
trapped train and an impossible plan are independent facts - so a genuinely short planner plus any one
dead copy gave a PASS with a message blaming the diagram. And the early return skipped the facing check
the whole class exists for.

**A test that absorbs its own failures reads as green while proving nothing, which is worse than the
red it was hiding.** The branch is out.

---

## The test that is still red, and why it stays

`core.testTrainsComeHomeToTheirPlatforms` fails about one run in five - measured, three failures in
eight observations across the day. It is left in the battery, failing, deliberately.

**The room rule added this morning cannot fire in that test at all** - it never sets a train length,
and the rule returns null at zero. That much was verified twice, independently.

**What was NOT true is the conclusion drawn from it**, and it is withdrawn here (`V37-C3`): "not
caused by anything changed today" ignores that `56c6080e` re-froze the fixture this test runs on this
morning - four tiles out of the diagram, a `canReverse` flag dropped, and every locomotive's starting
square moved. So the flake may be Adam's own diagram edits arriving in the test, which is a different
thing from a code regression and a different thing again from nothing having changed.

What fails is Return Home itself, on Adam's own diagram, with five trains and three of them left on
`Inter` squares by the run phase: `NO_PLAN_FOUND` with an empty blocked list - the planner declining to
claim anything, which is the vaguest answer it can give.

It stays red on Adam's own ruling about this test's ancestor, on 2026-09-01: **"ok, so that test should
then be red."** A test for something that does not work belongs in the battery being red rather than
in a table being quiet. The full per-train diagnostic - homes, launch pads, and whether a route exists
from where each train stands - prints on every failure.

---

## What is left for Adam

Five questions the round put to him rather than guessing at, collected as
[MT-257](../manual-tests/tests.md#mt-257), and three hands-on tests for what shipped
([MT-254](../manual-tests/tests.md#mt-254), [MT-255](../manual-tests/tests.md#mt-255),
[MT-256](../manual-tests/tests.md#mt-256)). [MT-253](../manual-tests/tests.md#mt-253) already holds the
two things his own diagram needs.

And `Readme.md` is modified in the working tree and not committed. Most of it is his own changelog
editing; one line is mine, correcting a changelog sentence that offered users a route editor which has
been deleted (`RG3-B1`). `V35-C3` points out it is one checkout from being lost.

---

## Not dispositioned

The C-level tails of the seven fan-out documents and the four validation documents are recorded in
their own files and have not been validated. They are not claimed to be either real or withdrawn. The
same is true of 63 C items and 19 B items from the 2026-09-01 round.
