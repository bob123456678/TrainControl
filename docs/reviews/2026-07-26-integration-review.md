# Integration review - 2026-07-26

Reviewed: the v2.8.0 working tree, after the A/B/C, P, N, M and T fixes were applied.

The earlier reviews each looked at one change at a time. This pass asks a different question: now that
all of them are in, does any pair of them interact, and does any of them depend on an assumption that a
neighbouring code path breaks? It was prompted by M2 turning out to be unreachable - if a finding could
be wrong about what the surrounding code permits, so could a fix.

Findings use the A/B/C/D convention in [README.md](README.md).

| ID | Severity | Status |
|---|---|---|
| A1 | High | Fixed |
| D1 | Not a defect | Closed, checked clean |
| D2 | Not a defect | Closed, checked clean |

---

## A1. Renaming a locomotive that belongs to a multi-unit breaks every lookup of it

`MarklinLocomotive.hashCode` is built from the name, address and decoder type. All three are mutable in
place - `rename` assigns to `name`, `setAddress` to `address` and `type`. A consist stores its members
as **keys** of a `Map<Locomotive, Double>`.

So renaming a locomotive that is currently a member of another's multi-unit changes its hash while it
is sitting in that map. The entry is still there and iteration still finds it - which is why the
consist goes on driving correctly, and why nothing looks wrong - but every hash lookup misses it:

| | after a member is renamed |
|---|---|
| `setSpeed` / `setDirection` fan-out | works (iterates, never looks up) |
| `getLinkedLocomotiveNames` | works (iterates, reads the live name) |
| `isLinkedTo` | **fails** - `containsKey` on the drifted hash |
| `unlinkLocomotive` | **fails** - `remove` on the drifted hash |

Two consequences follow, both reached by ordinary use:

**The nesting guard stops firing.** `TrainControlUI.changeLinkedLocomotives` refuses to turn an
already-linked locomotive into a multi-unit head, via `model.isLocLinkedToOthers(l) != null`, which is
`isLinkedTo` in a loop. This is the guard that makes M2's nested-consist scenario unreachable - the
reason M2 was withdrawn. Rename the member first and the guard no longer objects, so the arrangement
M2 described becomes constructible after all.

**The delete sweep stops sweeping.** `deleteLoc` removes the deleted locomotive from every consist by
calling `unlinkLocomotive`, a map removal. A renamed member is not found, so it stays linked - which is
exactly the defect the sweep was added to fix, reachable again by renaming first.

The three re-keying operations do not agree on this, and only one of them is right:

- `changeLocAddress` rebuilds every consist after re-keying (the loop at the end of the method), so
  the drifted hashes are repaired. Correct, though incidentally rather than by stated intent.
- `renameLoc` does neither - it does not unlink, and it does not rebuild.
- `deleteLoc` unlinks, which is correct for a real deletion.

**Severity.** Rated A rather than B because the trigger is renaming a locomotive - an everyday action,
not a specific configuration - and because both consequences are silent. The consist keeps working, so
nothing prompts the user to look.

**Fix applied.** `MarklinLocomotive.rehashLinkedLocomotives` re-inserts every entry of the membership
map, recomputing each bucket from the member's current fields, and `renameLoc` calls it for every
locomotive after the rename. It is `synchronized` on the same lock as `unlinkLocomotive`, for the same
reason.

Mirroring `changeLocAddress`'s full rebuild was considered first and rejected. `setLinkedLocomotives`
ends by calling `setDirection` on any non-empty consist, which puts a real direction command on the
track - so every rename would have commanded every multi-unit on the layout, stopping consists that
happened to be running. `changeLocAddress` needs that rebuild because an address change can create a
*conflict*, and a member that now collides has to be dropped; `testMultiUnitCreation` asserts exactly
that. A rename cannot create a conflict - names are unique by the `l2 == null` guard, and nothing about
linkage depends on a name - so the only damage is to the hash, and a re-key is the whole repair.

`hashCode` now carries a note that its fields are mutable in place and that re-keying operations must
repair the consists holding the locomotive, so a fourth mutator has a chance of finding this.

The deeper fix - making `MarklinLocomotive` immutable in its hashed fields, or keying consists by name
- is much larger and was not attempted.

**Tests.** [`test/testMultiUnitMembership.java`](../../test/testMultiUnitMembership.java).
`testRenamedMemberIsStillRecognisedAsLinked` and `testDeletingARenamedMemberRemovesItFromTheConsist`
were written against the unfixed tree, confirmed failing there, and pass with the fix. The same file
also pins the delete-sweep behaviour, which had no coverage at all - the defect above was found while
writing that coverage, not by reading the fix again.

---

## D1. The delete sweep's own thread safety

Checked as part of this pass and found genuinely wrong, then fixed during it, so it is recorded on the
M4 entry in [the independent review](2026-07-26-independent-review.md) rather than duplicated here. In
short: the sweep mutated a plain `LinkedHashMap` that `setSpeed` iterates under the locomotive's lock,
from a thread that did not hold it. It now goes through `MarklinLocomotive.unlinkLocomotive`, which is
`synchronized` on that lock.

Still unsynchronised and deliberately left alone: `setLinkedLocomotives` and
`preSetLinkedLocomotives`, which mutate the same map from the multi-unit dialog. Synchronising those
means holding a locomotive lock across `canBeLinkedTo`, which logs through the UI, so it needs its own
analysis rather than a keyword.

## D2. Interactions checked and clean

- **M1's speed clamp does not affect direction.** The clamp is on the magnitude only; the sign lives in
  `setDirection`'s separate fan-out, which the clamp never touches.
- **All three readers of `secondsToNext` agree with T1's convention** (the gap belongs to the entry it
  precedes): the replay loop, the edit dialog, and the export.
- **`TimetablePath.equals`/`hashCode` include `secondsToNext`**, which would be a mutable-key hazard of
  the same shape as A1 - but the type is never placed in a set or a map, and never compared. Noted in
  case that changes.
- **`deleteLoc`'s other callers are genuine deletions**, so the sweep is right for all of them. The two
  re-key callers - `renameLoc` and `changeLocAddress` - both use `locDB.delete` directly and so do not
  trigger it.
- **C2's function-count change was never reachable from the UI**, which does not offer `numF`.
- **B7 holds at all three `_setSwitched` call sites.**
