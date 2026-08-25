# Independent pass over the last day — 2026-08-25

**Status:** open

**Prefix:** `FB`. Cite findings from here as `FB-A1`, `FB-C2` and so on.

**What was reviewed:** `48d98f4c..b7032a34` — the same day as [the four-reviewer pass](2026-08-25-last-day.md),
about 130 commits by the time this ran, **including that pass's own fixes**. One reviewer, given the
whole range and told nothing about what the earlier four had found.

**Why a separate document.** It records a separate pass, and the folder's rule is that merging
destroys who found what and when. `LD` findings and `FB` findings are cross-referenced, not combined.
FB-A2 is a defect *in* the fix for LD-A2, which is exactly the relationship that would be lost by
folding them together.

**Method.** The brief pushed hard toward executing rather than reading, because the previous pass had
measured that reading finds nothing that reading produced. Both A findings were reproduced with
timestamped probes before being reported.

**Version:** v3.0.0 Beta, branch `autonomy-diagram-r0`. A1, A2, C1 and C2 are fixed in `47870317`;
all `fixed unvalidated` until Adam runs the layout.

---

## Summary

**Two serious findings, and one of them is a defect in a fix from the previous pass.** That is the
result worth taking seriously: the earlier round's fixes were reviewed by four reviewers and a battery,
and one of them was broken in precisely the scenario it existed for.

---

## A — high

Wrong behaviour on the layout, or data silently lost.

| | Finding | Disposition |
|---|---|---|
| **A1** | The route guard was asked once, and a route takes seconds to run | fixed in `47870317` |
| **A2** | The pre-edit note's "made harmless" fallback fails in the one case it exists for | fixed in `47870317` |

### FB-A1 — the route guard was asked once, and a route takes seconds to run

`MarklinRoute.execRoute`, the check before the command loop.

**Found by:** executed. A probe fired a two-command route 200 ms before `executePath` locked a path
owning the same turnout, with the first command carrying a 2.5 s delay:

```
24.209  route starts - guard sees nothing active, commits
24.711  autonomy configures turnout 384 STRAIGHT and locks the path
25.014  train dispatched
26.764  the route sets 384 TURN     <- against the locked path, run still in progress
```

A poller confirmed `getActiveAccs()` contained the turnout throughout. No refusal, no log line.

`accessoryHeldByAutonomy()` was evaluated a single time, before the `for (RouteCommand rc : this.route)`
loop — and that loop sleeps `SLEEP_INTERVAL` plus each command's own delay between every pair of
commands. So any dispatch that locked a path while a route was part way through was invisible to it.

**This is AU-A2 itself, surviving in a window seconds wide**, through all three doors including the s88
trigger with nobody present. Adam's railway has 39 s88-triggered routes, many multi-command; a route
fired just before, or straddling, a dispatch is not exotic.

**What the operator sees:** nothing. A train under autonomy takes a diverging turnout its own path had
set and validated — the derailment or collision AU-A2 was fixed to prevent.

The guard is now asked again immediately before each accessory command. **The cost is stated rather
than hidden:** a route can now be stopped part way through, with some of its ironwork set and the rest
not. That is real, and it is the smaller cost — the alternative is throwing a switch under a train that
is crossing it. Once the re-check trips, every later accessory is skipped too, so the route does not go
on flipping between the two states as conditions change under it.

The reviewer named this tension when reporting it, which is the right way to report a finding whose fix
has a price.

### FB-A2 — the pre-edit note's "made harmless" fallback fails in the one case it exists for

`AutonomyCompanionStore.forgetBeforeEdit`. **This is a defect in the fix for [LD-A2](2026-08-25-last-day.md).**

LD-A2 added a fallback: when the note's `delete()` fails — a sync client holding the file, which that
code's own comment calls "an ordinary Tuesday" — overwrite it with the *current* setup, so a leftover
note reverts to the present and therefore changes nothing.

The overwrite went through `writeJson` → `Util.writeAtomically` → `Files.move(REPLACE_EXISTING)`. **On
Windows, replacing a file requires the same DELETE access the `delete()` had just failed to get.** So
the fallback fails in exactly the scenario it was written for, and the note keeps its **pre-edit**
contents. At the next start `unfinishedEdit()` finds a well-formed, current-version note, accepts it,
and reverts — discarding the edit the operator saved, while logging that the edit did not finish, which
is false.

**Found by:** executed, on a copy of the real configuration. Took the note, made and saved an edit, held
the note open with a shared-read/write handle, called `forgetBeforeEdit` → returned false with the note
unchanged. A fresh store then reverted the saved edit away.

A companion probe established the asymmetry that makes it fixable: in that same lock state `delete()`
fails and the atomic move fails, but **a plain truncating write succeeds**. Of the three primitives,
the fallback had picked the one sharing the delete's failure mode.

The fallback now writes in place. Torn writes are acceptable here and nowhere else in this class,
because a half-written note fails `unfinishedEdit`'s shape check and is therefore refused, kept and
reported — and the content being overwritten is the thing being disposed of.

**A fix that fails only in the scenario it exists for is worse than no fix**, because it reads as
covered. The reviewer's honest limit is on the record: the sync client was simulated with a Java file
handle, and a real OneDrive lock's sharing flags may differ — but any lock that defeats `delete()`
under Windows semantics also defeats the move.

---

## C — low

| | Finding | Disposition |
|---|---|---|
| **C1** | `battery.sh` always exits 0 | fixed in `47870317` |
| **C2** | The LD-B1 relocation parks the shared switching worker on a modal dialog | fixed in `47870317` |

### FB-C1 — `battery.sh` always exits 0

Failures were reported as text only; the script's exit status was whatever the last `if` left behind,
which is success. Read-only finding, and the reviewer said so. No impact today because the battery is
read by eye — it becomes a trap the first time it goes behind `&&` or into CI, which is exactly when
nobody is reading the text.

It now exits 1 on a failure or a write to the live layout, 2 when a class tested nothing, and 0
otherwise.

### FB-C2 — the LD-B1 relocation parks the shared switching worker on a modal dialog

`LayoutLabel`, the route-conflict block moved onto the switching worker by LD-B1.

`submitSwitching` has one thread and it is shared by every tile in the application. Asking the
confirmation there holds it for as long as the dialog stands unanswered, and no tile anywhere responds
— the same freeze the power-state wait was given a deadline to avoid forty lines above, differing only
in that a person ends this one.

Read-only observation, and the reviewer framed it as a trade to be aware of rather than a defect: the
bug LD-B1 fixed was worse. Taken anyway, because it is cheap — the power is already on by the time that
code runs, which was the whole point of the relocation, so handing the rest to its own thread costs
nothing.

---

## D — not defects

| | | |
|---|---|---|
| **D1** | Five stated mutations, five killed | checked clean |
| **D2** | Store round trip over the real configuration | checked clean, byte-identical and idempotent |
| **D3** | `c0e5d4af`'s rewrite of the real configuration | checked clean, order-insensitively equal |
| **D4** | `Edge.isLockHeld`'s corrected comment | verified against the enforcing code |
| **D5** | A probe that read as a vacuous pass | **withdrawn by the reviewer before reporting** |

### FB-D1 — five stated mutations, five killed *(clean)*

Dropping `!rc.getSetting()`; the refusal returning whole and suppressing the emergency stop;
`getActiveAccs` ignoring `clearedEdges`; removing the hand-dispatch signal sweep; dropping `linkNames`
from the OB-025 registry. Each killed its test, and the registry one failed 9 of 12 settings-matrix
tests, exactly as its commit message claims.

Twenty-five classes were run green, one JVM each, with no configuration failures and no skips.

### FB-D2 — store round trip over the real configuration *(clean)*

`load()` then `save()` on a copy reproduces `setup.json` and `configuration-Main.json`
**byte-identically**, and a second round trip is idempotent. This is an independent confirmation of
[LD-D3](2026-08-25-last-day.md), reached by a different route.

### FB-D3 — `c0e5d4af`'s rewrite of the real configuration *(clean)*

Verified order-insensitively equal to its predecessor: pure list reordering, no data change.

### FB-D4 — `Edge.isLockHeld`'s corrected comment *(clean)*

The comment rewritten under [LD-C4](2026-08-25-last-day.md) was checked against the enforcing code, and
both orderings of an asymmetric lock relation were traced: one refused by `setOccupied`'s fan-out, the
other by the check loop. The correction is accurate.

### FB-D5 — a probe that read as a vacuous pass *(withdrawn by the reviewer)*

The reviewer's first attempt at the round-trip probe compared files the store never touched — wrong
constructor folder — and came back green. They noticed, said so, and re-ran it correctly.

Recorded because it is the same failure this repository keeps finding in its own tests, caught this
time by the person making it, before it became a finding. That is the behaviour worth having.

---

## What this pass did not cover

On the reviewer's own record:

- The bulk of the `TrainControlUI` diff (~2000 lines) beyond the named sites; `LocIconCropDialog`
  internals; the autonomy panels' UI behaviour, which needs a display.
- `HomeStaging` and the planner beyond its green tests; `LayoutDiagram`, `CS2File`, `NetworkProxy` and
  the CS3 backup changes — their tests ran green but were not read line by line.
- The eight translated bundles beyond key presence.
- The full battery end to end; per-class runs only.
- The textual half of the registry guard could not be evaluated from a scratch copy, because
  source-reading tests read the repository working tree. Its pass under the registry mutation is
  **inconclusive rather than evidence** — which is a distinction worth more than most findings.
