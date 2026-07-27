# Integration review - 2026-07-26

Reviewed: the v2.8.0 working tree, after the A/B/C, P, N, M and T fixes were applied.

The earlier reviews each looked at one change at a time. This pass asks a different question: now that
all of them are in, does any pair of them interact, and does any of them depend on an assumption that a
neighbouring code path breaks? It was prompted by M2 turning out to be unreachable - if a finding could
be wrong about what the surrounding code permits, so could a fix.

Findings use the A/B/C/D convention in [README.md](README.md).

| ID | Severity | Status |
|---|---|---|
| A1 | High | Fixed. Amended twice - the first fix was incomplete, see below |
| A2 | High | Fixed |
| B1 | Medium | Fixed |
| D1 | Not a defect | Closed, checked clean |
| D2 | Not a defect | Closed, checked clean |

A1, A2 and B1 are all the same root cause, recorded separately because they have different triggers and
needed different repairs. The root cause itself is the standing item at the end of
[the independent review](2026-07-26-independent-review.md).

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

**Amended (2026-07-26, twice).** The first fix was incomplete in two ways, both found later the same
day.

*The repair was wired into `renameLoc` only.* Address and decoder type are `hashCode` inputs exactly as
the name is, so `changeLocAddress` drifted the same collections and never repaired them. Anyone
changing a locomotive's address went on silently voiding its station exclusions. `changeLocAddress` now
calls `rehashLocomotiveKeys` too.

*The removal used `removeIf`, which does not work here.* Written as "iterate rather than look up", with
a comment saying so. It is not: `Collection.removeIf` iterates and calls `Iterator.remove()`, and the
hash-based collections implement that as `removeNode(hash(key), key, ...)` - recomputing the hash from
the key's *current* state. On a drifted key that searches the wrong bucket and silently removes
nothing, which is precisely the case the method existed to handle. The comment described the API's
shape rather than its implementation.

Both removals now rebuild the collection instead, via `Locomotive.removeFrom` and
`Locomotive.removeKey` - two helpers that carry the explanation once, so the next author meets it
before making the same assumption.

This was caught by `testDeletingFindsALocomotiveWhoseHashAlreadyDrifted`, and only because that test
asserts its precondition - that `contains()` genuinely fails first. Without that assert the test would
have passed against a plain `remove()` and exercised only the cases that never needed fixing.

**Tests.** [`test/testMultiUnitMembership.java`](../../test/testMultiUnitMembership.java),
[`test/testLayoutRenameKeys.java`](../../test/testLayoutRenameKeys.java).
`testRenamedMemberIsStillRecognisedAsLinked` and `testDeletingARenamedMemberRemovesItFromTheConsist`
were written against the unfixed tree, confirmed failing there, and pass with the fix. The same file
also pins the delete-sweep behaviour, which had no coverage at all - the defect above was found while
writing that coverage, not by reading the fix again.

---

## A2. A Central Station sync re-addresses a locomotive with no guard and no repair

Found by the author while questioning how CS imports resolve a name that already exists at a different
address. `syncWithCS2` updates a locomotive whose address the Central Station reports differently:

```java
this.locDB.getByName(l.getName()).setAddress(l.getAddress(), l.getDecoderType());

this.locDB.delete(l.getName());
this.locDB.add(existingLoc, existingLoc.getName(), existingLoc.getUID());
```

`setAddress` assigns `this.address` and `this.type` in place, and both are `hashCode` inputs. This
re-keys the `locDB` and nothing else: it does not route through `changeLocAddress`, and called neither
repair. So a sync silently drifted the locomotive out of its consist, its station exclusions and
`locomotivesToRun` - the same damage as A1, arrived at from a different direction.

Worse than A1 in two respects. It is **automatic** - triggered from a dozen UI paths and on connect,
with no dialog - and it is **unguarded**: renames and manual address changes are both refused while
`isRunning()`, but `syncWithCS2` has no such check. A commented-out block at Layout-sync time says as
much: *"no longer needed now that we are allowing conditional routes during operation."* Because it can
run mid-operation, `activeLocomotives` could be stranded too, leaving `isRunning()` permanently true.

That last point partly reopens D6 trigger B in the independent review, which was withdrawn on the
grounds that renames are guarded. The withdrawal was correct *about renames*; the mechanism it
described was reachable through this unguarded path instead. The finding named the wrong trigger, and
the withdrawal stopped at disproving that trigger rather than asking whether the state had another
door.

**Fixed.** The branch now defers while `isAutonomyRunning()`, logging
`loc.addressUpdateDeferredWhileRunning` so the operator knows to sync again once trains have stopped -
the author's call, chosen over applying the change and repairing afterwards. When it does apply, it
performs the same repair `changeLocAddress` does: consist revalidation plus `rehashLocomotiveKeys`.

**Not tested.** Reaching this branch needs a Central Station to sync against - `syncWithCS2` builds a
`CS2File` from the network interface and parses remote config. The repair it calls is covered through
`changeLocAddress`; the guard is one predicate, verified by reading. The gap is recorded in the
`testLayoutRenameKeys` header rather than left silent.

---

## B1. A deleted locomotive stayed on every station's exclusion list

`Layout.locDeleted` cleared `locomotivesToRun`, `activeLocomotives` and `locomotiveMilestones`, but not
`Point.excludedLocs`. Nothing else cleared them either, so a deleted locomotive remained excluded for
the life of the graph, and `Point.toJSON` kept writing its name out as an exclusion for a locomotive
that no longer exists.

Mostly inert - a dead object matches no live locomotive - but it is silent data rot in a file the user
keeps, and it is worse in the case that led here: when a Central Station import resolves a name
collision, the locomotive being deleted is one the user did not choose to remove, and its exclusions
disappear from the graph on the next reload with no message.

Rated B rather than A because nothing is routed wrongly as a result: the stale entry can never match a
live locomotive, so no train goes anywhere it should not.

**Fixed.** `locDeleted` now clears the exclusion sets too, through `Point.removeExcludedLoc`. Both use
the rebuild helpers rather than a hash removal, for the reason in A1's second amendment - the deleted
locomotive is exactly the one whose hash may already have drifted.

**Tests.** `testDeletingALocomotiveClearsItsExclusion`, which asserts both that the set is empty and
that the name is gone from `toJSON` - the export leak being the part a user would actually see.

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
- **The three `rehash*` methods do not have the `removeIf` defect.** Checked after finding it in the
  removals: all three copy via a constructor, `clear()`, then re-add. `clear()` needs no lookup and
  re-insertion recomputes every hash, so no hash-based removal happens anywhere in them.
- **`executePath`'s own `activeLocomotives.remove` is deliberately left as a plain removal.** The key
  cannot have drifted there - every re-keying path is now refused or deferred while running - and a
  rebuild would be actively wrong: it empties the map briefly, and both `Layout.isRunning()` and
  `AutoLocomotiveStatus` read that map without taking the lock. They would momentarily see no active
  locomotives, and `isRunning()` reading false at the wrong instant is D5's failure mode. Documented at
  the call site so the inconsistency reads as a decision.
