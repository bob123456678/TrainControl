# REV — Reversal mechanics: behaviour.md §3/§4 against the code

**Prefix for citing these findings elsewhere:** `REV9`

**Date:** 2026-09-08 (re-dispatch of the §§3/4 pass the independent review reported as never returned).

## Scope and method

Read in full, against `docs/reference/behaviour.md` §3 (Reversals) and §4 (Which way a train is
pointing, and where its tail is), in both directions — rules stated but not enforced, rules enforced
but not stated, and rules pinned by nothing or by source-grep only:

- `src/org/traincontrol/automation/Layout.java` — `shouldReverseAt` (5480), the `ReversalPolicy`
  interface and `asksAbout` default (5387–5445), `ALWAYS_REVERSE` and the four-argument overload
  (5824–5856), `executePathInternal`'s mid-path reversal branch (6400–6475) and arrival block
  (6744–6820), `entrySideOf` (5579), `sideTowards` (5781), `edgesCoveredByStandingTrains` (5627),
  `reversesAlongTheWay` (3726), `reversedOnArrival` / `takeReversalsOnArrival` /
  `restoreReversalsOnArrival` (670, 3052, 3079), `announceRunFinished` (7133) and its three callers,
  `mayReverseAt` (5808), the intermediate-terminus refusal in `isPathClear` (2260).
- `src/org/traincontrol/gui/ManualReversalPrompt.java` — whole file.
- `src/org/traincontrol/gui/ArrivalSidePrompt.java` — whole file.
- `src/org/traincontrol/automationui/AutonomySession.java` — `flipFacing` (1498),
  `moveOntoFacingCopy` (1410), `getFacing`/`setFacing` (5575/5595), `captureFromLayout` (3639).
- `src/org/traincontrol/automationui/AutonomyBuilder.java` — the `Node` class and `leavesBy`
  (60–137), `splitSides`/`nodesFor` (430–550), the point emission with the terminus/reversing flags
  (800–1010), the edge emission filter (1040–1060).
- `src/org/traincontrol/gui/TrainControlUI.java` — `reconcileFacingWhenIdle` (6625),
  `attachAutonomyRefresh` (3785), `updateVisiblePoints` (26199), `followDirectionChanges` (10860),
  the paste door (6430–6570), `rememberPlacement` (6874).
- `src/org/traincontrol/automation/Point.java` — `setLocomotive`'s `arrivedFrom` clear (534),
  `setDestination`/`setTerminus` (346, 367).
- `src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java` (place items, 930–1000),
  `src/org/traincontrol/gui/GraphLocAssign.java` (335).
- Tests: `testNonReversibleTrains`, `testAReversalCommandIsEmitted`, `testAutonomySimulationSanity`,
  `testACompulsoryTurnIsNotAQuestion`, `testAutonomyDiagramReversal`,
  `testEverySquareBuildsToTheCopiesTheSetupImplies`, `testTheTurnRuleDoesNotChangeTheRealRailway`,
  `testTheGraphIsToldWhenARunEnds`, `testADirectionChangeIsNotSwallowed`,
  `testATrainCoversTheTrackBehindIt`, `testAutonomyDiagramSession` (flipFacing tests),
  `testAPastedTrainKeepsItsDirection`.
- Today's three commits: `41913ecd`, `759aabda`, `e23f26d1` (via `git show`; the reverted narrowing
  is read through its blocking test).

No tests were run and no source file was modified, per the dispatch constraints. Everything below is
from reading; where a claim needs a measurement this pass could not make, the finding says so.

VAL9-A1 (Set parity — now a toggle at Layout.java:6815) and VAL9-C2 (the drain losing turns when
there is no session — now covered by the `finally` restore at TrainControlUI.java:6670) were checked
and found addressed as described, not worse. What IS worse than described is what happens when the
drain has a session and `flipFacing` still cannot write — see A1, which is also this pass's best
explanation for OB-190.

---

## Does the split really implement Adam's rule?

**Yes — on a graph the builder emits.** The rule (*"terminus: always reverse at the end. reversing:
only reverse if intended at the end or when backing in somewhere per the path, don't reverse if
passing onwards"*) is implemented structurally, not by a runtime check, and the structure holds:

1. `AutonomyBuilder.Node.leavesBy` (AutonomyBuilder.java:114–126): the turning copy may leave
   **only** by the side it is arrived by (`if (reverse) return arrival == exitSide;`), and the plain
   copy may **never** leave by it (`arrival != exitSide && onward.contains(exitSide)`). The edge
   emission enforces this — every emitted edge passes through `from.leavesBy(...)`
   (AutonomyBuilder.java:1046).
2. The `reversing`/`terminus` flag is emitted only on turning copies (and on the unsplittable
   must-turn square), never on a plain copy (AutonomyBuilder.java:985–995), and
   `testTheAuthoredFlagDoesNotLeakOntoThePlainCopy` pins that.
3. Therefore a path that passes onwards **cannot** be routed through a turning copy — the turning
   copy has no onward edge to route it over — and a path routed through one necessarily goes back
   the way it came, which is "backing in per the path". `isReversing()` at an intermediate of a
   built path does mean the path turns there.
4. The terminus half is direct: the arrival block turns unconditionally at a terminus
   (Layout.java:6792), `shouldReverseAt` answers a terminus-bound journey from the flag ignoring
   every policy (Layout.java:5516), and `isPathClear` refuses a terminus anywhere but the end of a
   path (Layout.java:2260), so the mid-path no-command case cannot be reached through one.

Three caveats, each a finding below:

- The invariant is **asserted only on a hand-built fixture**
  (`testThePlainCopyCarriesOnAndOnlyTheTurningCopyGoesBack`), never on the wired railway — and it
  *cannot* currently be asserted at runtime on the real build, because the builder emits entry sides
  but not exit sides, so nothing after emission can re-derive which metal side an edge leaves by
  (C2).
- The repository contains a test whose name and comment read as the invariant's refutation
  (`testAReversingCopyCanBeLeftByAnotherSide`), because it compares the build's stored entry side
  against the geometric fallback; its counter-examples (BottomMainB in by E, "out by" N) measure the
  curve between two vocabularies, not a turning copy with an onward edge (C2).
- The guarantee is the **builder's**, not the model's. `parseAuto` accepts any edge set, so a
  hand-authored configuration with `reversing` on a point that also has onward edges recreates the
  passing-turn: autonomy (`ALWAYS_REVERSE` → `current.isReversing()`) turns every train through it.
  Nothing validates a loaded configuration against the invariant. On the hand-written sample layout
  the doubled Points and one-way edges follow the same doctrine, so this is a latent limit rather
  than a live defect — but the split claim as stated ("`isReversing()` on a built graph already
  means backing in here") is exactly scoped to *built* graphs and should be quoted with that scope.

For the autonomy tier the claim is doubly guarded, by a rule §3 never states — see C1.

---

## A - high

### A1 - The idle drain forgets destination turns it could not write, and can write them backwards

**Files:** `src/org/traincontrol/gui/TrainControlUI.java:6652–6675` (the drain in
`reconcileFacingWhenIdle`), `src/org/traincontrol/automationui/AutonomySession.java:1560–1595`
(`flipFacing`'s gate and pivot).

**Mechanism.** §3 says a turn the railway makes at the destination *"records itself... the arrival
writes it down and the window applies it to the graph the next time the railway is idle."* The
apply half is:

```java
session.flipFacing(turned, built);
// Written, and only now forgotten.
pending.remove();
```

`flipFacing` returns the flipped tile, **or null when it wrote nothing** — its own javadoc says the
undecidable cases are "reported by returning null rather than guessed at", and the other caller
(`followDirectionChanges`, TrainControlUI.java:10922) checks `moved == null` before claiming
anything. The drain ignores the return: the comment "Written, and only now forgotten" is false
whenever `flipFacing` bailed, and `pending.remove()` destroys the record anyway. The RGD-C7
`finally` restore does not help — it restores only names the loop never reached, and this name was
reached.

When does `flipFacing` bail or mis-write? Its pivot is `recorded = getFacing(tile)` — the **setup's**
stored facing for the arrival square — and behaviour.md §6a states, as a rule, that this is stale at
exactly the drain's moment: *"A run moves trains, where they ended up lives only in the running
layout, and nothing writes it back to the setup when the run ends."* `captureFromLayout` adds that
`FACING` is *"only ever written here, never cleared - a square with no train on it still remembers
which way the last one was pointing"* (AutonomySession.java:3790–3797). So at the arrival tile,
`recorded` is either **absent** (square never captured with a train on it) or **the previous
occupant's facing** — and `flipFacing` was written for a caller (`followDirectionChanges`) that only
runs when the railway is idle and reconciled, where the record IS the pre-turn truth. The drain
lifted it without that precondition.

**Concrete failure, case 1 (turn lost).** Manual send to a may-reverse square; operator answers
"No" (turn). Arrival: `switchDirection()` fires (the command half works — measured in `41913ecd`),
`reversedOnArrival` toggles the name in (Layout.java:6815). Run ends, `announceRunFinished` →
refresh → drain. Candidates = the arrival tile (from the running layout). The tile has no stored
`FACING` → `recorded == null` → `continue` → `flipFacing` returns null → `pending.remove()` forgets
the turn. Outcome: the physical train is reversed; the setup facing, the diagram arrow, and the
running-layout copy all say it is not; the next dispatch offers paths for the wrong heading — and
unlike the pre-W7-A2 state this **never self-heals**, because the record is gone.

**Concrete failure, case 2 (turn written backwards).** Same journey, but the arrival tile carries a
stale `FACING` from a previous occupant that happens to equal the arriving train's **post**-turn
facing. `now = the-other-choice` = the **pre**-turn facing: `setFacing` writes the un-turned
direction as though it were the correction, and `moveOntoFacingCopy` stands the locomotive on the
copy for that wrong facing — actively moving it OFF a copy that was right. Note the squares this
feature exists for — split may-reverse stations, 13 of Adam's 33 — are precisely the ones whose
occupants legitimately alternate facing, so the stale record is unreliable there by construction.
On a terminus the stale record is systematically the post-turn side (that is what the last capture
derived from the terminus copy), which makes case 2 the *expected* case there, not the unlucky one.

**Why nothing caught it.** No executed test reaches `reconcileFacingWhenIdle` at all — it appears
only in comments and source-grep assertions (see C3). `testTheGraphIsToldWhenARunEnds` proves an
idle **announcement** happens; `testAReversalCommandIsEmitted` proves the **command** half; the
`flipFacing` tests call it with a facing already recorded and in sync. The seam between them — the
drain's contract "forget only what was written, and the write is relative to a record that must be
pre-turn" — is covered by nothing that runs.

**This is OB-190's likely mechanism**, and it makes OB-190 worse than "does the arrow turn?": in
case 1 the arrow does not turn and no later refresh can fix it; in case 2 the arrow and the
running-layout copy are corrected in the wrong direction. The repair direction that fits the
existing design: derive the post-turn facing **absolutely** from the copy the locomotive stands on
(what `captureFromLayout` already does) instead of flipping the stored record, and keep the name in
`reversedOnArrival` when `flipFacing` returns null.

## B - medium

### B1 - The reconcile move destroys `arrivedFrom` for exactly the train that turned

**Files:** `src/org/traincontrol/automationui/AutonomySession.java:1443–1451`
(`moveOntoFacingCopy`), `src/org/traincontrol/automation/Point.java:534` (the occupant-change
clear), `src/org/traincontrol/automation/Layout.java:6779` (the arrival write).

**Mechanism.** The arrival records `arrivedFrom` before the reversal, deliberately — §4: *"Turning
the train round at the platform does not move its tail: the carriages stay where they stopped."*
When the drain later succeeds (A1's good path), `moveOntoFacingCopy` re-stands the locomotive on the
sibling copy via `point.setLocomotive(null)` + `onto.setLocomotive(train)`. Both calls run
`Point.setLocomotive`'s clear — *"a different occupant did not come in that way"* — so the old
copy's `arrivedFrom` is wiped and the new copy never receives it. Nothing carries the side across
the move, although the fact is still true: the carriages have not moved.

**Concrete failure.** Manual send of a 4-length train to the plain copy of a split may-reverse
station whose setup facing is in sync (so the flip fires); answer "No" (turn). After the drain the
train stands on the turning copy with `arrivedFrom == null`: `edgesCoveredByStandingTrains` gets no
first-hop pick, and on any square with more than one way back the fork rule stops at once — the
switch the tail is physically fouling is offered to the next route. §4's own words for this state:
a protection that is not there. VAL8-B4's clear is right for a *different* occupant; this is the
*same* occupant on a sibling copy of the same square, which the clear cannot distinguish.

### B2 - §4's hand-placement rule is enforced at one door of three

**Files:** `src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:973–990` (`placeFacing`,
reached by the place item at 943–960), `src/org/traincontrol/gui/GraphLocAssign.java:335`,
against `src/org/traincontrol/gui/TrainControlUI.java:6501–6547` and `rememberPlacement:6874`
(the door that does it right).

**Mechanism.** §4: *"A hand placement works it out: a terminus forces it; an ordinary station
assumes the opposite of the facing; a may-reverse square asks."* That is implemented only at the
diagram drag/paste door (`ArrivalSidePrompt.forPlacement` → `tailAtTheLanding` →
`rememberPlacement` writes both stores). The right-click **Place locomotive** door
(`placeFacing`) writes the placement, the setup, and the facing — and never touches `arrivedFrom`:
no prompt on a may-reverse square, no assumption on a plain one, no forcing at a terminus. The
graph-window assign door (`GraphLocAssign`) likewise calls `moveLocomotive` bare.
`Point.setLocomotive` clears the old value on the occupant change, so the train lands with no tail
side at all.

**Concrete failure.** Adam's own §5 example, placed by right-click instead of by drag: a 4-length
train placed on BottomMainB via the context menu blocks nothing behind it — BottomMainC stays open —
while the identical placement by drag asks the question and blocks it. Also a documentation gap the
other way: §4's *"When would it not be known? Three cases, all narrow"* omits this fourth case,
which is not narrow — it is a placement door.

### B3 - The "opposite of the facing" assumption answers in compass, but everything else now speaks the build's sides

**Files:** `src/org/traincontrol/gui/ArrivalSidePrompt.java:81–84` (the assumption) and 253–267
(`opposite`), consumed at `src/org/traincontrol/automation/Layout.java:5666–5695`.

**Mechanism.** OB-182 moved the *offered* sides, the *written* arrival sides and the walk's
comparison onto the build's entry sides, precisely because compass and metal differ on a curve
(`e23f26d1`'s four BottomMainB-class pairs are the measurement). The non-may-reverse branch of
`forPlacement` was not swept: it still computes the pure compass opposite of the facing. On a curved
ordinary station whose sides are, say, {E, N}, a train facing N gets `arrivedFrom = "S"` — a side
the build enters the square by nowhere. The walk's first hop then matches no candidate and takes the
`segment == null → break` exit (Layout.java:5693), whose comment attributes that state to "a stale
value after an edit"; here the door manufactures it on every such placement.

**Concrete failure.** Paste a long train onto a curved plain station: no dialog (correct — not
may-reverse), a recorded side that exists in neither vocabulary, and the tail protection silently
absent. On a straight the two vocabularies agree, which is why the fixture tests
(`testATrainCoversTheTrackBehindIt:512–521`, all "E"/"W" on a straight chain) pass. The fix is the
same one made twice already for the sibling sites: the other of the square's two `facingChoices` (or
the other of its build arrival sides), not the compass opposite.

## C - low

### C1 - "In full autonomy a train is only ever reversed at a terminus" is enforced, load-bearing, and stated nowhere in behaviour.md

**Files:** `src/org/traincontrol/automation/Layout.java:3726–3737` (`reversesAlongTheWay`), asked by
`pickPath`, by the yield probe that mirrors it, and by `explainDestinations`; the rule quoted in the
comment as Adam's own ruling.

**Mechanism.** §3 describes which squares turn trains and who is asked, but never states the tier
rule the code enforces: full autonomy refuses any path that passes a reversing point at all —
berths and headshunts are off the through-network for that tier, and mid-path backing-in exists only
for manual sends and Return Home. This is a §3-grade rule (it is *why* a berth is safe from
autonomy's traffic) with three call sites that must stay mirrored, and the document that claims to
hold the intent does not hold it. It also makes §3's sentence *"Intermediates turn as the path
requires, exactly as they do for autonomy"* quietly misleading: for full autonomy the case cannot
arise, and the sentence's real reference class is the timetable/Return Home tier.

### C2 - The turning-copy invariant is asserted only on a fixture, and the one real-railway measurement of it mixes two vocabularies

**Files:** `test/regression/testTheTurnRuleDoesNotChangeTheRealRailway.java:95–175`,
`src/org/traincontrol/automation/Layout.java:5579–5589` (`entrySideOf`),
`test/core/testAutonomyDiagramReversal.java:139` (the fixture pin).

**Mechanism.** §3 states, as the reason a compulsory turn is not a question: *"a turning copy's only
outgoing edges leave by the side the train arrived from."* The builder guarantees it in the metal
vocabulary (`Node.leavesBy`). The only test that examines reversing copies on the wired railway,
`testAReversingCopyCanBeLeftByAnotherSide`, computes the in-side from the **stored** entry side and
the out-side from `entrySideOf(leaving, copy)` — which, for an edge whose START is the copy, always
falls through to the geometric `sideTowards` fallback. Its counter-examples ("in by E, out by N")
therefore measure the curve between the build's side and the neighbour's compass position — the same
divergence OB-182 documents — not a turning copy with a genuine onward edge. The test is right to
block the geometric narrowing (that was `e23f26d1`'s point), but its name and assertion read as a
refutation of §3's sentence, and its closing message invites a future author to "re-measure" with
the same mixed ruler. Meanwhile the metal invariant itself is pinned only on the small fixture: it
cannot be asserted against the wired build after emission, because exit sides are not emitted —
worth a builder-side census (assert over `nodesFor`/`leavesBy` at build time) rather than a runtime
one.

### C3 - §3's no-backlog and write-before-level rules are pinned only by source-text greps

**Files:** `test/ui/testADirectionChangeIsNotSwallowed.java` (all three test methods read
`TrainControlUI.java` as a string; the IND9-B4 ordering is `indexOf("takeReversalsOnArrival()") <
indexOf("lastSeenDirection.put(...)")`).

**Mechanism.** The §3 rules "a direction command arriving mid-run is ignored, not queued", "the
baseline is levelled when idle", and "a destination turn is written before the levelling wipes it"
are all asserted by grepping the source for tokens and comparing offsets. Nothing constructs the
window, arrives a turn, goes idle, and observes the setup — which is why A1 can sit in the exact
statement the grep proves is present. This is the dispatch's third category verbatim: a rule pinned
only by a guard that greps source text rather than running anything. The announcement half does have
a real executed test (`testTheGraphIsToldWhenARunEnds`); the consumption half has none.

## D - minor

### D1 - §3 states the dissolved stranding refusal in the present tense before revoking it

**File:** `docs/reference/behaviour.md`, §3 "The question, and when it is asked".

The paragraph *"A manual journey that needs a turn the operator declined is refused before it
starts, naming the square... The answer is always honoured; the journey that depends on a different
answer is not started"* stands two paragraphs above *"That ruling dissolved the refusal described
above... removed the same day"* and *"No journey is refused on the operator's answer any more, and
none can be."* The code agrees with the second statement (nothing refuses; `forJourney` asks only
about the destination). A reader checking code against the document meets a present-tense rule the
code deliberately does not enforce and must read on to learn it was revoked; the revoked rule should
be past-tense or folded into the revocation.

### D2 - `restoreReversalsOnArrival` is not parity-correct across a concurrent toggle

**File:** `src/org/traincontrol/automation/Layout.java:3079–3087`.

Membership is a parity, but restore is a plain `add`. If a dispatch starts during the idle drain and
its arrival toggles the same name between the drain and a failed write, restore's `add` no-ops
against the new entry: two real turns (the unwritten one plus the new one) net to zero, yet the set
holds one, and the next drain flips a facing that is correct. The comment claims the opposite
("anything already turned again in the meantime keeps its own answer"). Requires a write failure
plus a same-name arrival in a very small window, hence D; a parity-true restore would toggle, as the
arrival does.

---

## What this pass could not check

- **Anything that runs.** Per the dispatch constraints no test, build, or probe was executed, so
  every mechanism above is established by reading; the ones that most need a measurement are A1's
  two cases (a temporary probe on `flipFacing`'s return value inside the drain, over one manual
  reverse-at-destination journey, would settle both in a minute) and B3's curved-station placement.
- **What the diagram actually draws** (OB-190 proper). Whether the arrow follows the setup facing,
  the running-layout copy, or both is a rendering question this pass did not trace; A1 predicts the
  arrow misbehaves whenever the arrival tile's stored facing is absent or stale, which would make
  OB-190's hands-on answer "intermittently, and worse after A1 case 2".
- **The live state of Adam's setup files** — how many arrival squares currently carry an absent or
  stale `FACING` (this decides how often A1 fires in practice). `cs2_sample_layout/` was not read
  beyond git-tracked inspection.
- **The dialogs themselves.** `ManualReversalPrompt.ask` and `ArrivalSidePrompt.ask` need a display;
  their answer-decoding rules (`reverseFor`, the dismissal handling) are executed by existing tests
  and were read only.
- **Hand-authored legacy configurations** against the split invariant (the third caveat of the split
  answer): whether any config Adam still loads carries a `reversing` point with onward edges was not
  audited.
