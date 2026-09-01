# The 2026-09-01 fan-out: six passes over v3.0.0, and what came of them

**Status:** open

**Prefix for citing this index elsewhere:** `FX2`

**Reviewed:** branch `autonomy-diagram-r0` at `828b1ff1` / `b00ac0c1`, by six reviewers running in
parallel on 2026-09-01. Fixes and dispositions below were applied after them, up to `2de95ad0`.

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
| `FV2` | [Validation of the fixes](2026-09-01-fix-validation.md) | Opus | 1 A, 3 B, 10 C, 5 D |
| `SV2` | [Second validation](2026-09-01-second-validation.md) | Opus | 2 A, 2 B, 7 C, 6 D |
| `TV2` | [Third validation](2026-09-01-third-validation.md) | Opus | 1 A, 3 B, 7 C, 5 D |

**The round did not run to completion.** The highest-severity findings were validated and either fixed
or deferred; a large tail of C items is recorded in the individual documents and has not been
dispositioned. That is stated here rather than left to be inferred from a tidy-looking table.

---

## What the round cost, before what it found

**Two batteries ran concurrently.** One of the reviewers started `battery.sh` despite the briefing's
rule 1 forbidding it in bold. Every test JVM and battery shell was killed, all six agents were warned,
and the live layout was fingerprinted and backed up. No damage was done *during* the fan-out: the
`cs2_sample_layout/` diff-stat was identical before and after.

**And the first three explanations of it were all wrong.** This section said, in turn, that the lock
failed open on `kill -0`, then that the correction for that asked Windows about an MSYS pid, then that
`ps -W` cannot see `java.exe`. The first two were real defects in the guard and are fixed. Neither was
the cause, and the third was simply false:

- **The cause (`SV2-A2`).** `LOCK="$S/battery.lock"` and `$S` is `TC_SCRATCH`, **a directory per agent
  session**. Two sessions took two different lock files and neither ever looked at the other, so no
  version of the liveness test could have stopped a single one of these overlaps. The repository's own
  crash dumps say so: `battery-40080` ran out of `.../0362837d-.../scratchpad/tc` and `battery-32945`
  out of `.../51b92044-.../scratchpad`, dying seconds apart, and four distinct session ids appear
  across those files. The lock now lives under `TEMP`, which is one directory for the whole user —
  which is the scope the hazard has, since the Java Preferences store these runs fight over is per
  user.
- **The compile window (`SV2-A2` again).** A battery spends its first minute or two in `javac` and owns
  no JVM at all, and the process probe looked only for `java.exe`. Both overlapping runs were in
  `javac`. The probe now matches `javac.exe` for this project too — measured at 1 during a real compile.
- **Retracted: `ps -W` sees java perfectly well.** With a test JVM running, `ps -W | grep -ci java`
  returns 2. The zero I reported was my own grep or my timing, not a limitation of the tool, and it was
  cited here and in a memory as though it were a fact about the environment.

The two real guard defects are fixed as well (`b00ac0c1`, `a33b9ae1`, `c9153aaf`): the lock's liveness
test asked `kill -0`, which fails open, and its replacement asked Windows about `$$`, which is the MSYS
pid, so every LIVE battery read as stale (`FV2-A1`). The lock holds `/proc/$$/winpid` now and accepts
either answer.

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
| `FV2-A1` | **The first `battery.sh` lock correction was a regression** - it asked Windows about an MSYS pid, so every live battery read as stale | `f59fa45e` |
| `FV2-B2` | `D24-B1` was half-fixed: the runtime turns a train at a terminus as well as a reversing point, and the terminus limb is the one that reaches Adam's berths | `f59fa45e` |
| `FV2-B3` | The `CMT-B4` doc fix invented two rules nothing enforces, including the reverse of the tier doctrine | `c9153aaf` |
| `FV2-B1`, `FV2-C2` | The deferral said the reversal-room guard was inert on Adam's railway; it is live, on two reversal squares, one of which is a home | `c9153aaf` |
| `FV2-C1` | The cross's colour clause is unreachable in production, and its comment claimed otherwise | `f59fa45e` |
| `FV2-C5` | `battery.sh`'s numeric arm matched on the first character, so a malformed count skipped the warning added for it | `f59fa45e` |
| `FV2-C7` | `isShut()` and `isImpassable()` were the same predicate, with no caller for the first | `f59fa45e` |
| `FV2-C10` | "Each code fix was mutation-confirmed" covered a shell script that has no test | `f59fa45e` |
| `SV2-A1` | **The FV2-B2 fix to `firstClearRoute` was a regression that would have driven a train nose-first into a berth it cannot leave** | `208b3ee1` |
| `SV2-A2` | The lock lived in a per-session directory, so it could never have seen the other battery; and the probe was blind to the compile window | `208b3ee1` |
| `SV2-B1` | The terminus test could not fail - it asked only that the outcome was not IMPOSSIBLE | `208b3ee1` |
| `SV2-C6` | "`ps -W` cannot see java.exe" was false and was cited as fact | `208b3ee1` |
| `TV2-A1` | **`one.sh` had none of the five concurrency corrections** - no lock, the narrow probe, the old numeric arm - because it lived only in a scratch directory where no review could see it | `_pending_` |
| `TV2-C1` | The javac clause matched `*TrainControl*`, which is on neither runner's compile command line; the "measurement" that confirmed it was matching a different clause | `_pending_` |
| `TV2-B2`, `TV2-B3` | Two sentences in `AutomationAPI.md` describing behaviour the code does not have | `_pending_` |

The two Java fixes were seen failing first and mutation-confirmed: `D24-B1`'s test fails with
`IMPOSSIBLE` before the fix, and `TCX-A3`'s fails when the clearing loop's argument is replaced with
`0`. **`SVN-A2` is a shell script and has no test** - its branches were exercised by hand, which is a
weaker claim, and `FV2-A1` below shows how much weaker: the first attempt was exercised with a value
the script never writes, and shipped a regression.

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

**Correction, and it makes this more urgent than first written (`FV2-B1`, `FV2-C2`).** This item and
`MT-248` originally said the guard was "inert on his railway — six tiles carry lengths, all on the Test
page". Both halves were wrong, and checking the configuration settles it:

- `setup.json`'s page table maps page id **5 to `1 - Main`**, not to `5 - Test`. All six measured tiles
  are on your main page.
- **Two of the six are reversal squares themselves** — the ones the guard governs:

| Tile | Name | Recorded length | Flags |
|---|---|---|---|
| `5:20,13` | `BottomMainB` | 4 | `canReverse: true` |
| `5:20,14` | `BottomMainC` | 2 | `canReverse: true`, `home: EN57-947` |

An edge landing on one of these is fully measured, so `measured` stays true and the guard really does
run, with room 4 and room 2 respectively. **It is live behaviour on your railway, not a dormant rule.**

**Measured, and it walks back my own alarm.** I wrote that Return Home was probably being refused into
EN57-947's home at `BottomMainC` right now. It is not: **EN57-947 has no train length set at all**, and
the guard's first condition is `getTrainLength() != null && > 0`, so that locomotive is exempt. Read from
the live locomotive database, where 54 locomotives do carry a length and it is not one of them.

The exposure belongs to the others: **42 of those 54 have a train length greater than 2**, and `BottomMainC`
has two units of room. Any of them routed or homed into that berth is refused today. Whether that is
right depends on the ruling below, but it is not hypothetical and it is not about EN57-947.

The rest still holds: the editor notice will fire on roughly 20 squares, and following it does not arm
the guard, because the notice asks for the reversing square's length while the guard needs the whole
run-in measured (`TCX-B2`, `TCX-B3`, `SVN-B1`).

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
- `Export Current Configuration`. **`R28-B2` overstates this and is partly withdrawn.** The button is
  hidden on a diagram-derived layout (`mountAutonomyControls` at `TrainControlUI.java:3430`) but it is
  *not* unreachable in the other configuration: on a JSON-era layout it is made visible at `:3394` and
  enabled at `:19922` once a validation succeeds, and again at `:19066` when autonomy stops. So the
  capability survives where it belongs.

  What is real is narrower: **a user who has moved to a diagram-derived setup can no longer export the
  configuration the builder derived**, and the editor panel offers no export of its own — `grep` finds no
  export action in `AutonomyEditorPanel.java`. Whether that matters depends on whether you ever want the
  derived JSON out of the application; say if you do and it is a small addition.

---

## What the validation pass changed about this document

`FV2` was run over the fixes above rather than over the code, and it found one A and three Bs — every
one of them in **my own work**, not in the reviewers'. That is the useful part of the round and it is
recorded rather than smoothed over:

- **`FV2-A1`** — the `battery.sh` lock correction shipped a regression. `echo $$ > "$LOCK"` writes the
  MSYS pid and the new check asked Windows about it, so a **live** battery read as stale and the script
  announced "clearing a stale lock" before starting a second run inside the compile window the lock
  exists to cover. It passed its test because the test wrote a Windows pid into the lock by hand — the
  branch was verified with a value the script never writes.
- **`FV2-B2`** — `D24-B1` was listed as fixed when only one of its two limbs was. `executePath` turns a
  train on arrival at a terminus **or** a reversing point, in one statement; the terminus limb is the
  one that reaches real track, because parking berths are termini and terminus copies are emitted as
  destinations.
- **`FV2-B3`** and **`FV2-B1`/`FV2-C2`** — two claims of mine that were simply false, one in the user
  documentation and one in this index.

Four withdrawals or partial withdrawals now stand in this round: `TCX-B7` (already compensated),
`R28-B2` (partly), and `FV2`'s corrections to `FX2-3`'s stated reasoning.

**The lesson worth keeping**, since it is the second time this round: *verify the integration, not the
branch.* Both the concurrency guard and its correction were "tested" by exercising a code path with a
value the real caller never produces.

---

## Not dispositioned

The C-level findings in all six documents, and the B-level findings not listed above, are recorded in
their own documents and have not been validated. They are not claimed to be either real or withdrawn.
