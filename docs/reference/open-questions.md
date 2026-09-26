# What is still open, and what was decided

The companion to [`behaviour.md`](behaviour.md). That document says what the railway **should** do;
this one says what is **known to be wrong with it**, what was **ruled on and closed**, and where the
rules **deliberately under-claim**.

It replaces reading 208 review documents.  (The 2026-09-21 half is counted: 67 files went - 44 review documents in `docs/reviews/`, 21 in the three dated folders, and two for-Adam notes that were never reviews, so 65 reviews.  **The 2026-09-08 half, 143, is not reproducible from its commit**: that one deleted 145 `.md` files of which at least three were not reviews - a consolidation plan, an archive README and the catalogue itself - so the honest total is 207 or 208 depending on whether an archived route-inventory dump counts.  Left at 208, the figure three documents have always used, with the derivation written here so the next reader need not guess.  VD12-R12 said 206 and was wrong; VD14-R6 found this.) Those are gone from the tree and live in git history
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
is not one.

The railway defects live in the Inbox of `docs/manual-tests/issues.md`. **It is not empty**, and this
paragraph said it was for twelve days (IND9X-B3, 2026-09-09).
Today it holds 92 entries - 60 OB and 32 FR, recounted from the file on 2026-09-25.  On 2026-09-24 eighteen entries
left it on Adam's word.  Seven were given their receipts then - OB-240 to OB-243, OB-271, OB-275
and FR-095, each fixed, answered or withdrawn by its own text - and eleven had been receipted long before
and were still sitting in the Inbox, which `triage.py` already treated as closed.  OB-272 to OB-277 and
FR-094/FR-095 had been filed that morning, FR-096 was filed after the tidy; FR-094 left it
when it was built, and OB-208, OB-270, OB-272 to OB-274, OB-276, OB-277 and FR-096 when they were.  Fifty more left it that
evening on his word - *"anything already reviewed and certified as working should be cleared"*: every entry whose receipt, or the hands-on test it became, is fixed and validated, and OB-218, declined.  OB-283 was filed after that, to carry the open reversal question below (DCN-C14).  OB-284 was filed after it, for the paste question the review's third round raised (AUT3-B2, DCN3-B1).  On 2026-09-24 OB-230, OB-283 and OB-284 were given their receipts on Adam's rulings, and OB-285 and OB-286 were filed from the follow-ups (AUT2-C2, GUI2-C4); OB-287 and FR-098 that evening, from the pass over the waiting tests.  OB-288 to OB-290 came from Adam's round of manual tests the same day, and were given their receipts on it; OB-291 from his next, and given its receipt on it too, and FR-099 with it; OB-292 was filed from the conversation after, and FR-100 and FR-101 from his next round; OB-293 and FR-102 from the one after, and OB-294 with them; OB-295 and OB-296 from the validation round that evening (TDA-C9, TDU-B4), OB-297 from its third round (TDA2-C1), and OB-298 from a test run the same evening; all four were given their receipts on 2026-09-25, on his answers, and OB-299 was filed that day from the second validation round of that work (ADU2-C2), and OB-300 from the 3.0.0 release review (RLA-C6), and OB-301 and OB-302 from its second round (RLA2-C2, RLU2-C12), and OB-303 to OB-305 from its third (RLA3-C1, RLA3-C5, RLU3-C5).  Every
earlier figure here was wrong in the same way, by being written rather than recounted: 99, then 111
counted before six entries that had already been appended, then 118 (IND9X-B3, VD12-R13, VD13-R3).
Most of them carry a receipt row that says the work was done - the protocol is that an entry is
cleared out when its fix has a test, and clearing has lagged. Read the receipts, not the presence of
an entry.

---

# Part 1 — Railway behaviour

Organised to match `behaviour.md`. **Open** is what is still wrong; **Decided** is what Adam ruled on,
with the reason, so it stops being re-argued; **Limits** are deliberate under-claims that will
otherwise be reported as bugs.

## Reversals

**Open.** Nothing: the two questions the validation round of 2026-09-24 left here were answered the same night.

**Decided, and each of these reversed an implemented behaviour** - which is exactly what this section
is for.

- **A side no train stops from is still one a train may turn at** (Adam, 2026-09-24, TDA-C8: *"Arrivals THAT STOP
  THERE should only be allowed from the configured side(s).  Turning shouldn't need to factor this in, since the former
  would govern the behavior."*).  The build keeps the turning copy of a barred side, and the notice asking for the
  length behind a turn asks about that side again.
- **The right-click Place keeps the train's heading** (Adam, 2026-09-24, OB-296: *"Yes, keep the train's heading."*):
  the copy facing the way the train already faces, the paste's rule - never a draw.

- **A compulsory turn on the way turns the train, and keep/reverse is measured on arrival** (Adam,
  2026-09-24, OB-283: *"if it's a compulsory turn, turn it in the forced direction."*). A journey that
  passes a compulsory-turn square is turned there, so at a may-reverse destination "keep the current
  direction" can leave the train net-reversed from how it set off (measured 2026-09-08). That was the
  code's behaviour all along; what it reversed is `behaviour.md` section 3, which promised the opposite.
  Carried on OB-190's Inbox body from 2026-09-08 and then on OB-283 (DCN-C14).

- **A direction command arriving while a run is under way is ignored, not queued** (Adam, 2026-09-07,
  `bc6120f1`): *"The arrival writes the graph - but if a manual command is sent, ignore it, as this is
  likely corrective by the user."* This inverted `ca0265f4` of the day before, which had made mid-run
  reversals deferred-until-idle and argues for deferral in its own commit message;
  `reconcileFacingWhenIdle` brings the baseline forward so there is no backlog. Section 3 of
  `behaviour.md` carries the mechanism. Recorded here 2026-09-21 (W7B-C1) - it had been missing from
  this list for twelve days, which is how a settled question gets re-argued from the losing side.

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

**Decided 2026-09-25** (ADU-C7, ADA-A1): **an answered 0 is measured track of no length, to every length rule** - but the own-tail rule, filed as OB-300.
Adam: *"we can't possibly have positive lengths everywhere because the tracks just aren't that long.  We need to find a way to allow trains in atomic mode in as well if the total track lengths allow"*, and *"Build it"*.  The route in, the room walk, the walk that claims a standing train's
tail, the berth rule and the tail question count it, adding nothing, and walk on over it; a leg with any square nobody
answered - a switch or crossing on it included - still ends them.  Asked with the frozen railway's Tunnel as the example:
with BottomSecondary -> TunnelPre answered 0 throughout, a train of 5 from RampDown is admitted over the 8 measured end to
end.  Built in 3567d45d; MT-586 on the railway.

**Before that:** the three wordings the validation rounds of 2026-09-24 left here were answered the same night.

**Decided.**

- **A berth given no room is a warning of its own** (Adam, 2026-09-24, TDA4-C2: *"Give it its own sentence as a
  warning, make it sound intuitive (the effective specified length of the track is 0)"*).  Where every square before a
  parking berth's stop was answered 0, the berth takes no train that way: *"the length given for that track adds up to
  0 and every train arriving that way is refused"*, at the half-measured warning's grade, in place of the run-in
  notice's 0.
- **The platform run-in notice gives the refusing figure, where there is one** (Adam, 2026-09-24, TDA-C10: *"Add the
  refusing figure where there is one."*): the shortest measured way in, from the station or turn nearest behind, where
  it is under the stated maximum - on his railway, TopR1ParkShort's four-unit train at TopMainR1Inter.
- **An answered 0 is a measure to the Atomic Routes gate** (Adam, 2026-09-24, TDU-C6: *"0 lengths count as measures,
  so non-atomic should be allowed"*).  The gate and the release escape it guards both treat track answered 0 as
  measured, so non-atomic running is allowed over it; every other length rule reads it as OB-274 says.  The route in
  carried on over it for a day, and was put back (ADA-A1): the walk that claims a standing train's tail stops at an
  answered 0, so a train the route in admitted past one lay on track nothing claimed.
- **The own-tail note counts what Mass Assign asks for** (OB-297, done on Adam's word: *"Locations of switches are
  known."*): the build marks, place by place, the piece, switch or shared square the editor still asks a length for,
  and the note counts those - one list, so a leg measured only at its switch is counted, and a piece the editor calls
  measured is not (ADA-C1).

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
- **A crossing whose other road no train can reach still ends a berth's room for its notices** (TDA3-C2, narrowed by
  TDA5-C1).  The notices ask whether another road runs over the square, of the reduction, which has a road between
  two sensors however unreachable; the build emits no rail over it, so the berth rule does not refuse there, and the
  half-measured and run-in notices warn of a refusal that does not come.  The warning side, and a crossing nobody
  builds.
- The room rule measures **from the last switch**, so track measured on the far side of a switch does
  not count toward a berth. This surprised Adam once and is correct: a train that fits between the
  switch and the berth fits behind any earlier switch too.
- **`arrivedFrom` narrows the tail walk; it does not switch it on.** Where the geometry leaves one
  way back the walk follows it regardless, and `arrivedFrom` only picks between candidates where
  there are several. On a railway with none recorded, blocking still happens wherever the track
  is unambiguous — less than the feature can do, but not nothing.

## Routing tiers

**Open.** Nothing since 2026-09-24.

**Decided.**

- **"Unavailable while occupied" means every square a route arrives at** (Adam, 2026-09-24, OB-295: *"It means trains
  shouldn't be sent to THIS square while trains are STANDING ON or hold a lock on the other specified station(s)."*).
  The standing half is asked of every square on a route, at runtime and by Return Home's planner, as the lock half
  always was; a square trains only pass is shut to routes through it while a train stands on the one it watches.  The
  cost the setting names, and one more: two restrictions set against each other can now hold both trains, where each
  one's only way out passes the square the other holds back (ADA-C3).

- *Return Home sits with Manual* on where a train may be sent. `isAutoDestination` appears nowhere in
  `HomeStaging`.
- *Return Home refuses an inactive start where manual allows it* — deliberate, 2026-09-07. The
  exemption is for a person who has looked at the railway; Return Home is a plan for every staged
  locomotive at once.
- *Inactive means nothing can pass*, in every tier, with the square a train already occupies exempt.
- **Occupancy restrictions bind every tier** (Adam, 2026-09-10): *"If it's cleaner to go with
  consistency across the board, then let's enforce the occupancy ruling in all modes and then rely on
  isPathClear. Revert the prior lax ruling."* It was fenced twice before - behind `isAutoRunning` until
  2026-09-09 and behind `isFullAutonomyRunning` after it, on his earlier ruling that the restriction is
  *"for modifying pathing prioritization"* while the length checks are the anti-collision mechanism.
  Both fences are gone; one rule, asked once, in `Layout.isPathClear`, and `HomeStaging` keeps its own
  copy because a planner that does not apply it offers a leg the runtime refuses (OB-073). Section 1 of
  `behaviour.md` carries it. Recorded here 2026-09-21 (W7B-C1); this list stopped at 2026-09-07, and
  the question had been answered three different ways by three tiers in the week before the ruling.

**Limits.** The Path Type control answers about **destination eligibility only**. The tiers differ in
other ways (Return Home runs under the autonomy `running` flag, so `isRunning()` is true for it);
that is not what the control is about.  The tooltip used to say so in a second sentence; it was cut on
2026-09-21 with fourteen others, on Adam's *"the tooltip here is an example of one I believe is too
long"* (MT-436), so this limit is now written down here and nowhere a user sees it.

**Route choice is not reproducible between runs, and Adam asked to keep an eye on it** (2026-09-10).
`bfs` returns *some* route avoiding the ones already found rather than the next in a defined order, so
a census of the whole railway gives a different number each time: four runs gave 2,116, 2,146, 1,648
and 1,617. It is a limit rather than a defect - every route it returns is legal - but it means no
measurement over all routes can be compared with an earlier one, which is why the figures in this file
are quoted with the run that produced them. Recorded here on 2026-09-21 because it was living in a
resume note that has been deleted.

## Setup and start-up

**Open** (2026-09-25, RLA3-B1 with RLU3-C4, RLA2-C4, RLU2-C11 and RLA4-C8): **a second import of an old file cannot
tell a setting you returned to its default from one never set.**  Every editor door stores a default as nothing - a
maximum of 0, Can Be Chosen ticked, a square switched back on, priority 0, speed 100%, an exclusion list emptied, a home
taken off - and a capture writes a station maximum of 0 for every station; the gap-fill (MT-298) reads nothing as a gap,
so a second import puts the file's value back where you had returned the default (a home taken off comes back, and
Return Home sends the train there), and a captured 0 keeps the file's maximum out - into the configuration in use,
which the import captures into first, no maximum from the file arrives at all (RLU4-D3).  Options: (a) keep the
gap-fill and say in its question that settings at their default, homes taken off and emptied exclusion lists take the
file's; (b) import an old file only into a configuration of its own;
(c) remember what the first import wrote and fill only that.  Recommendation: (a) - the second import is rare, and (b)
and (c) change MT-298's rule or add state to every configuration.

**Decided** (Adam, 2026-09-24):

- **A tail answered after another configuration was loaded follows the train** (TDD5-C1: *"Follow the train."*): where
  the loaded configuration's copy holds the train as it was put, the answer goes onto the railway running and into the
  configuration now active.  A session replaced in the wait still drops it.
- **Over a setup with errors, the right-click Return Home item is greyed with the setup's sentence; the buttons stay
  live and explain** (TDU2-C3: *"Yes, go with your recommendation."*), as Start's item and button are.
- **A legacy import leaves Load Autonomy alone** (TDD-C11: *"Drop it now (isn't the setting defunct?)"*).  The setting
  is not defunct - it resumes the active configuration at start - so only the untick went: after an import the box is
  as the operator left it, and a ticked box loads the imported setup at the next start.

**Decided while he was away, and reversible** (2026-09-24, TDU-B1): Execute Timetable and Return Home refuse a setup
with errors, as Start and the hand doors do - they drive over the railway the same setup built.  It extends MT-263 on
the rule's own stated reason; his ruling named the hand doors.

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
`docs/reference/package-sweeps.md`, removed 2026-09-24 and in git history at 2d910000.

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

*Written 2026-09-07. If this document and the code disagree, the code wins and this is stale — say so.*

**2026-09-08: `docs/reviews/` was deleted**, down to its README, once its 2,226 findings were in
`docs/manual-tests/triage.db`. **2026-09-21: the same for the 65 documents written since** - 44 of them in
`docs/reviews/` itself and 21 in three dated folders beside it, on Adam's *"I don't want more
reviews living in the repo"* - 3,726 rows then.  The round of 2026-09-23 added 336 - five reviews and three rounds of validation - and 13 more came on 2026-09-24 with OB-247 (AR-17 to AR-23 and LR-1 to LR-6, which have no document), and the validation rounds of 2026-09-24 added 258 (TDA, TDU and TDD, then the same three lanes as TDA2 to TDD5), and the validation of the work on Adam's answers of that night added 52 on 2026-09-25 (ADA, ADU and ADD) and 48 in its second round (ADA2, ADU2 and ADD2), and the 3.0.0 release review 51 in its first round (RLA, RLU and RLD)
and the 2.8.2 backport validation 30 (BPV), and the release review's second round 59 (RLA2, RLU2 and RLD2), its third 45 (RLA3, RLU3 and RLD3), its fourth 48 (RLA4, RLU4 and RLD4) and its fifth 50 (RLA5, RLU5 and RLD5), which makes
4,716 finding rows in the store now, every one of them with a status.
(This paragraph's figures - 65 documents, 44 of them in `docs/reviews/`, and the finding count - are quoted from the deletion commit and the store.  A correction to 63 and 206 was itself wrong and was reverted; the 208 it was about is in the first paragraph of this file, not here.  `regression.testTheRecordsCountTheStore` now compares the finding and Inbox counts with the store rather than trusting a reader to keep them - VD13-R1, VD14-R2, VD14-R5.) Everything still open above is open in that store too,
so it can be queried rather than re-read:*

```sql
SELECT ref, severity, title, evidence FROM finding WHERE status LIKE 'Open%' ORDER BY severity, ref;
```

*Three of the items in `2c` were re-measured that day and had grown since they were filed:
`TrainControlUI` to 28,052 lines, the `JOptionPane` parents to 326 calls, and `DD-B6`'s duplicated
test init to 103 files. The prose above is the argument; the store is the list. See
[`behaviour.md`](behaviour.md) for how to read a citation.*
