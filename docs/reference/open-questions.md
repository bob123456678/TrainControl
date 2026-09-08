# What is still open, and what was decided

The companion to [`behaviour.md`](behaviour.md). That document says what the railway **should** do;
this one says what is **known to be wrong with it**, what was **ruled on and closed**, and where the
rules **deliberately under-claim**.

It replaces reading 135 review documents. Those stay in `docs/reviews/` for now and in git history
afterwards; nothing below needs them to be understood.

---

## How big the backlog actually is

**Re-counted 2026-09-08, and the numbers below are what the sections now say.** The version of this
table that stood until then disagreed with its own bodies in every row - it said the September work was
both ten open rows and "all now closed" in the same cell, counted the per-package sweeps as four open
while §2d was headed CLOSED, and carried a headline of ninety that its own contents contradicted by at
least fourteen. An index that has to be checked against the thing it indexes is worse than no index,
and this one was quoted in three other documents.

| | state |
|---|---|
| September - reversals, length, blocking | **closed.** Fixed through the week; the last of them, the
  arrival-side vocabulary, on 2026-09-08 as OB-182 |
| §2a - one rule written several times | **closed 2026-09-08.** The last live copy was the
  sendable-destination rule, in three spellings; `testTheSendableDestinationRuleIsWrittenOnce` now
  sweeps every source file rather than one |
| §2b - tests that do not test what they claim | **closed 2026-09-07**, except the one named there |
| §2c - structure | **open, and deliberately.** Adam: extract only what a feature needs. Nothing here
  is a defect; it is a shape somebody may want to change one day |
| §2d - per-package sweeps | **closed 2026-09-07** |
| Test-suite quality (`TA-*`, `TS-*`) | **largely overtaken.** The fixture rebuilt itself on
  2026-09-08: the suite had been running against a five-edge skeleton of the railway, and several of
  these rows describe what that skeleton could not reach |

**Nothing on this list is a known railway-behaviour defect.** Every remaining row is about the codebase
or the tests. Worth stating plainly, because an unread backlog reads like a railway full of bugs and it
is not one - the railway defects live in `docs/manual-tests/issues.md`, and that inbox is empty.

---

# Part 1 — Railway behaviour

Organised to match `behaviour.md`. **Open** is what is still wrong; **Decided** is what Adam ruled on,
with the reason, so it stops being re-argued; **Limits** are deliberate under-claims that will
otherwise be reported as bugs.

## Reversals

**Open:** none.

**Recently closed.**

- **`REG7-A1` — FIXED 2026-09-07, on Adam's ruling.** *"Manual only reverses if the user explicitly
  said it via the popup, unless you're going to a terminal."*

  A manual path may route through the turning copy of a may-reverse square, and a turning copy leaves
  only by the side the train came in at — so a train that does not turn there runs on onto track its
  path does not hold. Both of his rules are kept: the answer is honoured everywhere, and the journey
  that depends on a different answer is refused before it starts, naming the square. A terminus is
  exempt.

- **`REG7-A2` — FIXED 2026-09-07, and it was sharper than reported.** Not "a spurious double-follow":
  `flipFacing` gathered candidate squares from the running layout and then **appended the setup's**,
  and the loop below *skips* a square it cannot decide rather than stopping. After a run the arrival
  square often has no recorded facing yet, so the loop moved past it to the next candidate — the
  square the train had **left**, which the setup still names until the next capture and which does
  have a facing. That empty platform had its direction flipped for a train standing elsewhere.

  The setup now answers only when the railway has no opinion. `DIR-C3` is intact and pinned.

  The reviewer's stated mechanism — that the baseline cannot tell the run's own reversal from the
  operator's — does not hold: after a run that reverses, the train is still on the copy it arrived
  on while its physical direction has flipped, so following it is the reconciliation Adam asked for.
**Decided.**

- *May-reverse always prompts in manual mode; yes = keep direction, and yes is the default.* Escape,
  the X, and a dialog that cannot be shown all mean keep. Because the safe answer must be the easy
  one, and the action must never be what a dismissed dialog does.
- *Asked at departure, once per journey.* Asking during the run means the train is already dispatched
  and travelling while somebody reads a dialog.
- *A compulsory turn is never a question, in any tier.* A turning copy's only exits leave by the side
  the train came in on, so declining is not an outcome — it drives the train off its reserved path.
- *A journey ending at a terminus is not asked about.* The answer would be discarded: the turn on the
  way in is how a train backs into a terminus (MT-245).
- *The graph never commands the track; a track command does update the graph.*

**Limits.** The prompt names one square per journey — the destination when it qualifies, otherwise
the first on the route. A journey passing several may-reverse squares gets one answer for all of
them. Deliberate: a question per square is unusable, and one journey half-turned is worse than
either.

## Length, blocking, and the tail

**Open:** none.

**Decided.**

- *Exactly-fits is admitted.* Otherwise every berth measured to the train that lives in it becomes
  unusable.
- *The total across a stretch is what counts*, not any single tile.
- *Unmeasured track contributes no room, but does not cancel a refusal the measured part earned.*
  A stretch where **nothing** is measured is not judged at all — refusing on no information would
  make an unmeasured layout unusable.
- *A stretch is bounded by the segment it lies in.* If the room after the last switch is unmeasured
  but the segment measures one, a four-unit train does not fit. A part cannot be longer than the
  whole; this over-states real room, so it only ever refuses more.
- *The tail blocks edges, not points* — "because the points are technically unoccupied".

**Limits.**

- The tail walk **stops at a fork** and **at unmeasured track**. A tail that really does reach past
  either is not blocked. Both under-claim knowingly: blocking on a guess is still a refusal, and it
  stops trains that could have run.
- The room rule measures **from the last switch**, so track measured on the far side of a switch does
  not count toward a berth. This surprised Adam once and is correct: a train that fits between the
  switch and the berth fits behind any earlier switch too.
- **`arrivedFrom` narrows the tail walk; it does not switch it on.** Where the geometry leaves one
  way back the walk follows it regardless, and `arrivedFrom` only picks between candidates where
  there are several. On a railway with none recorded, blocking still happens wherever the track
  is unambiguous — less than the feature can do, but not nothing.

## Routing tiers

**Open:** none.

**Decided.**

- *Return Home sits with Manual* on where a train may be sent. `isAutoDestination` appears nowhere in
  `HomeStaging`.
- *Return Home refuses an inactive start where manual allows it* — deliberate, 2026-09-07. The
  exemption is for a person who has looked at the railway; Return Home is a plan for every staged
  locomotive at once.
- *Inactive means nothing can pass*, in every tier, with the square a train already occupies exempt.

**Limits.** The Path Type control answers about **destination eligibility only**. The tiers differ in
other ways (Return Home runs under the autonomy `running` flag, so `isRunning()` is true for it);
that is not what the control is about, and the tooltip says so.

---

# Part 2 — Codebase and test suite

This is where the whole real backlog lives. Grouped by what a fix would actually be, because the
review documents grouped by reviewer and that is not useful for deciding what to do.

> **Audited 2026-09-07.** This part had gone stale in two places within a day of being written: it
> called `TA-A1` the one A-grade row still open when it had been closed, and listed `2d` as never
> triaged when all twenty-nine of its findings are now closed. That is the third index to drift from
> its own bodies this week - after `REG7`'s status table and the note I added while auditing it - and
> the first two were other people's documents. **The rate is the finding**: a status line written
> beside work in progress is stale by the end of the session that wrote it, which is the argument for
> keeping status in as few places as possible.

## 2a. One rule written several times

The recurring defect shape in this codebase, and the one that has produced the most real bugs.

**Closed since this was written.** `DR-B3` — the sendable-destination conjunction is written once,
inside `isSendableDestination`, and a surface rule fails a second copy. `DR-B10` — the absent-page rule
is on the session and its `Reconciliation` carries the names, so the six doors report rather than
decline in silence. `DD-B5` — one way into the right-click menu, with the comment saying so. `DD-C9` —
the two one-letter-apart methods are gone; `sideTowardNeighbour` asks the tile grid and `sideTowards`
asks the graph, which are different questions in different domains.

**Closed 2026-09-07.**

- **`DD-A7`** - the tier split. `AutonomyChecks` predicts what the railway will do, so where the build
  can answer, it asks the build; the seven ERROR checks that report why a build is *impossible* keep
  reasoning about the diagram, because there is no build to inspect. The trapped-arrival walk is gone.
- **`DR-B6`** - both halves. The arrival-sides walk was consolidated onto the `arrivalSides` door
  earlier; the facing rule now reads `facingsFor`, which is `builder.facingByName()`, and the session's
  second `onwardFrom` is deleted.
- **`DR-B2`** - already done and never dispositioned. FR-001 is one rule, `Point.heldBackBy`,
  parameterised by an `Occupancy`; the runtime passes the live block and the planner passes its
  planned state. Its javadoc records that the two copies it replaced "guarded it differently".

**Still open.**

- **`DD-B9`, reachability.** Two walks over two graphs: `GraphReducer.reachableTiles` over the drawn
  diagram, and the runtime's over the built one. The checker's copy re-applies the four authored sets
  - may-turn, must-turn, barred arrivals, closed squares - by hand to imitate what the builder already
  did when it chose which Points and Edges to emit.

  Not theoretical: `GraphReducer`'s own javadoc records `reachableTiles` and the editor's path test
  drifting once already - one gained a `closed` set and its sibling three lines away did not - measured
  at the time as *"the tool drew a route the runtime refuses"*.

  **Attempted 2026-09-07 and backed out.** Swapping the source the way `DD-A7`'s trapped-arrival check
  was swapped does not work, for two reasons the tests found:

  1. **Reachability is per-COPY, not per-square.** A square can hold a station copy that reaches
     nothing and a plain copy that reaches plenty. Unioning the copies - the obvious way to answer a
     question the operator asks about squares - reports the square as fine when a train standing *at
     the station* is stranded. The cross-test caught exactly this, one square each way on the sample
     layout, and its oracle has the right semantics.
  2. **An incomplete build is worse than an absent one.** The trapped-arrival check is safe because a
     missing answer means one finding goes unsaid. Reachability is a global claim: a build that emits
     few edges - which is what a half-drawn diagram, or one whose accessories are not yet wired, does -
     makes every station look unreachable and floods the panel while somebody is still drawing. Two
     session tests failed on exactly that, on small fixtures.

  **So the shape is:** walk per copy, aggregate the way the finding is worded, and gate on the build
  being complete rather than merely present - `session.getReducer().getEdges().size()` against what the
  build emitted would do it. The editor's *test a path* tool keeps the diagram walk regardless: it runs
  while drawing, on a setup that may not build at all.

**Ruled, not a defect.** The three TIERS answering "where may a train be sent" differently is
deliberate, and section 1 of [`behaviour.md`](behaviour.md) records why: `isAutoDestination` appears
nowhere in `HomeStaging`, because Return Home sits with Manual. The repeated `isDestination() &&
isActive()` pairs inside the planner are different questions about different endpoints - a candidate
station, a start, a home - that happen to share two clauses. Merging them would be the
lookalike-column trap: better naming is not a pin, and one rule made out of three questions is worse
than three.

**Why it matters more than it looks:** every A-grade finding of the last three days was an instance
of this — a guard and its affordance asking different questions, a rule enforced at one door of two.

## 2b. Tests that do not test what they claim - CLOSED 2026-09-07, except one

Audited finding by finding against the code. **Seven of the eight were already fixed or were fixed
today**, which is the same shape the package sweeps had: the work was done and the index lagged it.

| | what it was | state |
|---|---|---|
| `TA-A1` | the encoding guard never asserted the accented page's id | **closed** `156ab1dd` |
| `TA-B5` | four defects in `testMockCentralStation`'s sync-safety block | **closed** - all four: the timeout assertion now uses a non-routable address with the bound the reviewer prescribed; the cumulative counter uses the `before` pattern; the class's false premise is corrected and `testAGarbledRouteFileDoesNotDeleteTheRoutes` covers the risk it named - a sync deletes ROUTES, not locomotives; and the parity comparison covers address and decoder type rather than sorted names |
| `TA-B8` | `testFacingFollowsTheTrack`'s oracle built from the subject's own map | **closed by `DR-B6`** - `facingChoices` reads the builder now, so the premise and the answer come from different places |
| `TA-C1` | `!= IMPOSSIBLE` cannot tell a refusal from a success | **fixed today** - asserts `READY`, and that the plan moves both named locomotives |
| `TA-C3` | `testAnUnmarkedLayoutIsUntouched` compared the builder to itself | **closed** by `TCX-B5`, which added the live control |
| `TS-C1` | nothing drove a train through the reversal mechanics | **partly answered** - `testAutonomySimulationSanity` now runs real journeys that arrive, which the note said could not be done |
| `TS-C2` | three suites rolled unseeded dice | **closed** - all three carry explicit seeds |
| `TS-C3` | six tests green when their input is absent | **fixed today**, and worse than filed: three of the inputs live in `tc_backup/`, which is gitignored with zero files tracked, so those tests passed on every machine but Adam's without reading anything. `SkipException` now, which the bar counts - Failures: 0 **and** Skips: 0 |

**Still open: `DD-B6`**, the 47 copies of the control-station init - now **90 test files**. I would
leave it. It buys consistency rather than correctness, it is the largest-blast-radius change in this
part, and nothing in the four days of defects traces to it.

**What the audit found that the list did not.** Six defects reported on 2026-09-07 all had green tests
over them, and not one was a test that lied about its assertions - which is what this section is about.
They were:

- **an unexamined layer** - nothing in the suite looks at what a Swing component draws, so three
  reports about the covered-track wash were invisible;
- **fixture monoculture** - every hand-built layout is a straight chain of two or three points, so no
  fixture has a switch (no lock edges) or a curve (where the side a rail leaves by differs from the
  direction of the neighbouring point). Two defects hid there, and `VAL8-A1` hid there before them;
- **a guard asking the wrong question about the right thing** - the two-menus test asks whether they
  NAME a square alike, not whether either exists, so a menu missing from one surface is invisible to it.

**One fixture with a curve and a switch** would have caught two of the six outright. That is the next
piece of test work worth doing, ahead of anything left in this section.

## 2c. Structure

`TrainControlUI` decomposition (`DD-C1`), the store's eleven collections declared in six places
(`DD-A1`, `DD-C6`), a 426-line keyboard dispatcher (`DD-C8`), ~290 `JOptionPane` calls naming their
parent five ways (`DD-C7`), dead code from unfinished de-duplications (`DD-C4`).

None of these change behaviour. They are the reason behaviour changes keep being risky.

## 2d. Per-package sweeps — CLOSED 2026-09-07

All twenty-nine findings read against the code and ruled on: **fourteen fixed, twelve cancelled, six
left with reasons, and one reclassified as a feature that had no tests.** Full disposition in
[`package-sweeps.md`](package-sweeps.md).

It was the least-known part of the backlog and it held the largest defect of the week — `C13`, filed
as a route condition registering a phantom accessory and actually a call in the keyboard paint loop
that had put 2048 phantom switches into the live database. Twelve of the twenty-nine were already
gone, eleven of those fixed by people who did not know the finding existed.

---

## What I would do next, in order

Revised 2026-09-07, with the first and third items done.

1. **`2a`, the duplicated rules.** Now unambiguously first. Every A-grade finding of the last four
   days was an instance of it: a guard and its affordance asking different questions, a rule enforced
   at one door of two, a record kept in one store and read from another. `behaviour.md` gives each one
   a single written answer to converge on, which is what was missing when they drifted.
2. **`2b`, the tests that do not test what they claim.** Promoted from background. Two A-grade defects
   this week were invisible to tests written specifically for the property they broke, and the reason
   was the same each time: the test asked the store the code had just written rather than the consumer
   that reads it. `TS-C1` is partly answered — `testAutonomySimulationSanity` now drives real runs
   through the reversal mechanics, which the note below said could not be done.
3. **`2c` as background.** Real, not urgent, and each item wants a session with nothing else in it.

## What is deliberately not here

Rows that were **fixed but never dispositioned**, **ruled on and rejected**, **superseded** by code
that no longer exists, or **never true**. They are in git history. Carrying them forward would make
this document exactly the thing it replaces.

---

*Written 2026-09-07. Reviews in `docs/reviews/` are the working record; this is what they were
working towards. If this document and the code disagree, the code wins and this is stale — say so.*
