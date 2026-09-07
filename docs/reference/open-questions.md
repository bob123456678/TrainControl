# What is still open, and what was decided

The companion to [`behaviour.md`](behaviour.md). That document says what the railway **should** do;
this one says what is **known to be wrong with it**, what was **ruled on and closed**, and where the
rules **deliberately under-claim**.

It replaces reading 135 review documents. Those stay in `docs/reviews/` for now and in git history
afterwards; nothing below needs them to be understood.

---

## How big the backlog actually is

**90 open rows across 8 documents** — not the ~240 a naive count suggests, which counted table
headers and prose. Of those:

| | rows | |
|---|---|---|
| September (reversal, length, blocking work) | 10 | **all now closed** — they were fixed and the dispositions never updated |
| August, code structure and duplication | 46 | `DD-*`, `DR-*`, `GC-*` |
| August, test-suite quality | 30 | `TA-*`, `TS-*` |
| August, per-package sweeps | 4 | `C1-C6`, `C7-C12`, `C13-C19`, `C20-C29` — each a bundle, not one finding |

**The headline: nothing on the list is a known railway-behaviour defect.** Every open row is about the codebase or the tests. Every open row is about
the codebase or the tests. That is worth stating plainly, because a list of ninety unread items reads
like a railway full of bugs and it is not one.

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

## 2a. One rule written several times

The recurring defect shape in this codebase, and the one that has produced the most real bugs.

- **Reachability / "sendable destination"** written three ways — runtime, planner, test oracle — and
  agreeing only because somebody keeps them agreeing. `DD-B9`, `DR-B2`, `DR-B3`.
- **The checker re-implements rules the builder enforces**, and has disagreed with the railway.
  `DD-A7`.
- **The absent-page rule** enforced four ways, reported at none of its six doors. `DR-B10`.
- **The right-click entry point** written four times, guarded on three. `DD-B5`.
- **The arrival-sides walk and the facing rule** each gained another copy. `DR-B6`.
- **Two `sideTowards`/`sideToward` methods**, one letter apart, one question. `DD-C9`.

**Why it matters more than it looks:** every A-grade finding of the last three days was an instance
of this — a guard and its affordance asking different questions, a rule enforced at one door of two.

## 2b. Tests that do not test what they claim

- **`TA-A1`** — the encoding guard never asserts the accented page's id, so a decode-drift regression
  would renumber every page unnoticed. **The one A-grade row still open anywhere.**
- Tests whose oracle is built from the subject (`TA-B8`, `TA-C3`), assertions that cannot fail
  (`TA-B5`, `TA-C1`), tests green because their input is absent (`TS-C3`), unseeded randomness
  (`TS-C2`).
- **`TS-C1`** — no automated test drives a train through the runtime reversal mechanics. Known, and
  partly by design: a test that drives a train through `executePath` hangs the suite. The reversal
  rules are covered as rules and as source ordering instead.
- **`DD-B6`** — no test harness: 47 copies of the control-station init.

**This session added four more instances of the same shape**, all caught by their own controls: a
paste test that could not fail, a routing test that passed with the rule unwired, a guard that read
its own explanation as code, and a spec test comparing per-Point against a per-square answer. That is
the argument for `TA-*` being worth a pass rather than grandfathered away.

## 2c. Structure

`TrainControlUI` decomposition (`DD-C1`), the store's eleven collections declared in six places
(`DD-A1`, `DD-C6`), a 426-line keyboard dispatcher (`DD-C8`), ~290 `JOptionPane` calls naming their
parent five ways (`DD-C7`), dead code from unfinished de-duplications (`DD-C4`).

None of these change behaviour. They are the reason behaviour changes keep being risky.

## 2d. Per-package sweeps, unread

`C1-C6` (automation), `C7-C12` (marklin), `C13-C19` (base), `C20-C29` (gui). Each row is a **bundle**
of small findings, not one item. These have never been triaged and are the least-known part of the
backlog.

---

## What I would do next, in order

1. **`TA-A1`.** The only open A. A silent page renumber is exactly the class of fault that cost a
   layout restore this week.
2. **`2a`, the duplicated rules.** Highest ratio of real bugs to effort, and `behaviour.md` now gives
   each one a single written answer to converge on.
3. **Triage `2d`.** Unknown size; could be empty, could hold the next A.
4. **`2b` and `2c` as background.** Real, not urgent.

## What is deliberately not here

Rows that were **fixed but never dispositioned**, **ruled on and rejected**, **superseded** by code
that no longer exists, or **never true**. They are in git history. Carrying them forward would make
this document exactly the thing it replaces.

---

*Written 2026-09-07. Reviews in `docs/reviews/` are the working record; this is what they were
working towards. If this document and the code disagree, the code wins and this is stale — say so.*
