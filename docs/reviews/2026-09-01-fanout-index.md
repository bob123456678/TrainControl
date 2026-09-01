# The 2026-09-01 fan-out: six passes over v3.0.0, and what came of them

**Status:** open

**Prefix for citing this index elsewhere:** `FX2`

**Reviewed:** branch `autonomy-diagram-r0` at `828b1ff1` / `b00ac0c1`, by six reviewers running in
parallel on 2026-09-01. Fixes and dispositions below were applied after them, up to `9f1b80c8`.

This is an index, not a review. The README is explicit that documents recording separate passes must
not be merged — each holds its own scope, method and blind spots, and that is the calibration data. So
the findings stay where they were made; this says who found what, what has been fixed, and what is
waiting on Adam.

---

## The six passes

| Prefix | Pass | Model | Findings |
|---|---|---|---|
| `RTG` | [Autonomy algorithm and routing](2026-09-01-autonomy-routing-review.md) | Fable | 2 A, 2 B, 4 C, 10 D |
| `SVN` | [The past week of commits](2026-09-01-week-of-commits-review.md) | Opus | 4 A, 17 B, 17 C, 18 D |
| `D24` | [The last day of commits](2026-09-01-last-day-review.md) | Opus | 1 A, 5 B, 11 C, 8 D |
| `R28` | [Regression against v2.8.1](2026-09-01-regression-vs-2.8.1-review.md) | Opus | 1 A, 2 B, 5 C, 12 D |
| `TCX` | [The test suite](2026-09-01-test-suite-review.md) | Opus | 4 A, 13 B, 9 C, 9 D |
| `CMT` | [Comments and documentation](2026-09-01-comments-and-docs-review.md) | Sonnet | 4 B, 3 C, 4 D |

**The round did not run to completion.** The highest-severity findings were validated and either fixed
or deferred; a large tail of C items is recorded in the individual documents and has not been
dispositioned. That is stated here rather than left to be inferred from a tidy-looking table.

---

## What the round cost, before what it found

**Two batteries ran concurrently.** One of the reviewers started `battery.sh` despite the briefing's
rule 1 forbidding it in bold. Every test JVM and battery shell was killed, all six agents were warned,
and the live layout was fingerprinted and backed up. No damage was done *during* the fan-out: the
`cs2_sample_layout/` diff-stat was identical before and after.

Two mechanical facts made it possible, and both are now fixed (`b00ac0c1`, `a33b9ae1`):

- `battery.sh`'s lock refused only when `kill -0` **succeeded**, so any pid it could not resolve across
  MSYS sessions fell through and started a second run. It now asks Windows, which can see across
  sessions, and keeps alive / dead / unanswerable apart instead of collapsing them into two.
- `ps -W` in this Git Bash cannot see `java.exe` at all. It reported zero while two batteries ran.

And the guard written that morning was itself wrong twice, found by this round as `SVN-A2`: it matched
only a flag this project's own runners set, so an `ant` or NetBeans run was invisible; and a probe that
failed was indistinguishable from one that answered "none".

**The standing lesson: an instruction in a prompt is a request; a check in the tool is a rule.** Keep
telling agents not to run tests, but never let that be the only thing between them and the railway.

---

## Fixed

| Finding | What it was | Where |
|---|---|---|
| `SVN-A2` | The concurrency guard matched only our own flag, and swallowed its own probe failures | `a33b9ae1` |
| `D24-B1` | `HomeStaging.connected` proved journeys impossible using a stricter rule than the executor enforces | `9f1b80c8` |
| `TCX-A3` | `testTrainTailClearsEdges` asserted a string that this release made non-unique, so the assertion could no longer fail | `9f1b80c8` |
| `CMT-B1` | `canRest`'s javadoc still listed the terminus rule deleted from its body the same day | `9f1b80c8` |
| `CMT-B2` | `refreshAllProtectingSignals` still documented three call sites removed on OB-166 | `9f1b80c8` |
| `CMT-B3` | `AutomationAPI.md` instructed the reader to use a deleted graph window and a deleted menu item | `9f1b80c8` |
| `CMT-B4` | Both user documents still said only a reversible locomotive can reach a terminus | `9f1b80c8` |
| `OB-167` follow-up | A switched-off square drew a cross only when it was not a station — which is nearly never | `e9435bfc` |
| `RTG-B1` | The five-train test hand-started trains onto inactive destinations, stranding two of them before the run began | `7d8543f3` |

Each code fix was seen failing first and mutation-confirmed. `D24-B1`'s test fails with `IMPOSSIBLE`
before the fix; `TCX-A3`'s fails when the clearing loop's argument is replaced with `0`.

---

## Withdrawn

| Finding | Why it is not a defect |
|---|---|
| `TCX-B7` | `testAutonomySimulationSanity` does assert `getPathValidationFailureCount() == 0` on a `simulate: true` fixture where the guard cannot run — but a prior review found exactly this (`TST-A4`) and added `testPathValidationCanActuallyFireOutsideSimulateMode` 200 lines below in the same file, which arms the mechanism separately and is mutation-documented. The compensation was missed. |

---

## Deferred — needs Adam

Each of these is a decision about what the railway should do, not a defect with an obvious fix.

### FX2-1. Your live layout configuration has lost settings (`SVN-A1`, `TCX-A4`, `RTG-A2`)

**The question: which of these were you, and shall I restore the rest from git?**

`cs2_sample_layout/config/autonomy/configuration-Main.json` differs from `HEAD` at 14 of 71 points. Some
of it is unmistakably your own work, and some of it is loss. A blanket `git checkout` would destroy the
first half, so nothing has been touched. A full copy of the folder is in the scratchpad.

Clearly yours — the "may reverse, unselectable in autonomy" arrangement we discussed, applied:

| Point | Change |
|---|---|
| `1 - Main:4,5` | `active:false` removed, `autoDestination:false` added |
| `1 - Main:8,6` | `active:false` removed, `autoDestination:false` added |
| `1 - Main:20,14` | gained `home: "EN57-947"` |

Apparent loss — no replacement setting, and siblings that got one:

| Point | Lost |
|---|---|
| `1 - Main:13,9` | `excludedLocs` (EA 3005 DSB, ER 2035 DSB, MF 5028 DSB, MY 1150 DSB, MZ 1425 DSB, SP45-204) **and** `priority: -3` |
| `1 - Main:14,3` | the same six `excludedLocs` **and** `priority: -2` |
| `1 - Main:10,6` | `active:false`, with no `autoDestination` in its place |
| `1 - Main:9,6` | `active:false`, with no `autoDestination` in its place |

The file also carries `MT-x233 Test Loc` and `MT-233 Test Loc 2` in your timetable and on points, which
is consistent with your MT-233 session but is also what a test JVM writing there would look like.
Neither `battery.sh`'s fingerprint nor the golden-layout guard can attribute a change, because both
sample the folder around a whole run — that ambiguity is the same one `LayoutSandbox` records from
2026-08-25 and it is worth fixing separately.

### FX2-2. A route refused at a human door discards its emergency stop (`SVN-A4`)

**The question: when you click Cancel on "…would switch X, which is on track a train is running over.
Run it anyway?", do you mean "don't throw that switch" or "don't run this route at all"?**

Today it means the second. `TrainControlUI.java:16100` and `LayoutLabel.java:537` both `return` before
`execRoute` is ever called, so a route that cuts power *and* sets a trap point does neither.

Everywhere else in the system means the first. Refused mid-run, `MarklinRoute` sets `skipAccessories`
and carries on, so `rc.isStop()` still cuts power; the s88 door does the same. That behaviour has a test
whose comment states the principle outright: *"'Refused whole' is a good argument about accessories… It
is not an argument for suppressing a stop, which is safe to obey whatever else is true."* That test
covers only the automatic door.

If you mean the first, the two doors should skip the accessories and run the rest, and the dialog should
probably say so. Not changed unilaterally: it is a safety behaviour and both readings are defensible.

### FX2-3. The reversal-room rule is unsound in two ways (`D24-B2`, `SVN-B2`, `RTG-B2`, `TCX-A1`)

**The question: when you said "sum the track segments leading up to it", did "it" mean the reversal, or
the berth the train ends up standing in?**

Two independent problems, both recorded in a comment at the guard:

1. **`getLength() > 0` does not mean "measured".** On a diagram-built graph an edge's length is
   `GraphReducer.sumLength`, which adds `Math.max(0, tileLength)` over the tiles it spans — so one
   measured tile out of five yields a positive length and the guard reads it as measured. The total then
   under-counts and refuses trains that fit: the exact failure the guard's own comment says it removed,
   one layer down. Fixing it properly means an edge reporting itself unmeasured unless every tile is,
   which also changes tail-clearing on live track — hence your call.
2. **It may be summing the wrong segments.** It adds the whole path. Where a train turns round part way
   along and backs in, the track it comes to rest on is only the part after the reversal, so a 10 + 1 + 2
   path admits an eight-unit train into three units of room. And a path that reverses mid-way but ends at
   an ordinary station gets no check at all.

Related: on your railway only **6 tiles carry a length**, all on the Test page, so the guard is inert
today while the new editor notice will fire on roughly 20 squares (`TCX-B2`, `TCX-B3`, `SVN-B1`).
Following the notice as written does not arm the guard, because the notice asks for the reversing
square's length and the guard needs the whole run-in.

### FX2-4. A destination with no way out (`RTG-A1`)

**The question: should the builder refuse to emit a turning copy it cannot trace a way out of, or should
the editor warn?**

`AutonomyBuilder` emits a may-turn station's turning copy as a terminus destination while its edge loop
can trace no return edge against the main line's one-way arrows. `BottomMainB (eastbound, reverse)` on
your layout is a destination with zero exits; `pickPath` has no way-out clause and will send a reversible
train there. The editor's checks are structurally blind to it. You have already ruled on the outcome —
"it should be easily possible to get back" — but not on the mechanism.

This is why `testTheParkingBerthsGetTheirTrainsBack` still fails. `RTG-B1`, the second half of that
diagnosis, **has been fixed** (`7d8543f3`): the test hand-started its parked trains onto whatever the
manual door offered first, and that door deliberately offers destinations autonomy will never choose —
on your configuration the first is `ParkingTrack6`, which is `active:false`. It now walks the options
for one `isChoosableByAutonomy` end.

That fix is what makes this item's severity legible. **The blocked list drops from three locomotives to
one, and the survivor is `BottomMainB (eastbound, reverse)`** — so two of the three failures were the
fixture's own doing and were hiding the one that is real. The test stays excluded until the graph defect
above is settled.

One thing it surfaced, worth its own look: `TunnelCenterPark` offers **ten** manual routes and not one of
them ends anywhere autonomy would choose, so that train is no longer started at all rather than being
started into a siding.

### FX2-5. Deleting a locomotive strips it from every route (`R28-A1`)

**The question: should deleting a locomotive silently remove its speed, direction and function commands
from your routes, as it does now, or leave them as v2.8.1 did?**

`Route.locomotiveDeleted` removes them; v2.8.1 kept them. The reasoning is written down and sound — a
command naming a locomotive that does not exist does nothing when the route fires, and leaving it makes
the route look complete while it is not. The case it does not cover is delete-then-re-add under the same
name, which v2.8.1 survived and this does not. The confirmation dialog does not mention it, and the one
log line is about conditions, not commands.

### FX2-6. Two things a v2.8.1 user could do and cannot now (`R28-B1`, `R28-B2`)

- Per-edge accessory commands are dropped when a legacy `autonomy.json` is imported **and cannot be
  authored anywhere in 3.0.0** — no GUI caller of `addConfigCommand` exists; commands are derived from
  tile geometry. In the graph shipped here, 69 edges carry commands naming 15 distinct signals. Whether
  the `protectingSignal` mechanism is meant to replace that is your call; the migration does not convert
  them.
- `Export Current Configuration` is unreachable in both configurations — hidden on a local layout, greyed
  on a Central Station layout — and the only replacement is behind `isDebug()`, which is set solely by
  launching with two or more command-line arguments.

---

## Not dispositioned

The C-level findings in all six documents, and the B-level findings not listed above, are recorded in
their own documents and have not been validated. They are not claimed to be either real or withdrawn.
