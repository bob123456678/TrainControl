# Second validation pass: the two week-wide reviews, acted on

**Status:** open

**Prefix:** `SV`. Cites `IAR-` ([2026-08-24-independent-application-review.md](2026-08-24-independent-application-review.md)) and `DR-`
([2026-08-24-duplication-robustness.md](2026-08-24-duplication-robustness.md)).

**Commit validated:** `1babf184` - "The two week-wide reviews: four A findings, three of them mine".

**Date:** 2026-08-24. **Method:** read the whole diff against `1babf184^`; enumerated callers of every changed
signature across `src/` and `test/`; ran `regression.testPageIdsAreDurable` clean and against two source
mutants compiled outside the repository; ran a standalone probe against the committed `LayoutDiagram` to
settle DR-B4. No source or test file in the repository was changed by this pass.

**Working-tree note.** `src/org/traincontrol/automation/Layout.java` is modified in the working tree at the
time of writing: DR-B7 has already been reverted there, uncommitted, with a javadoc explaining why. This
document validates the commit as committed. The revert is corroborating evidence for SV-A1, not a
substitute for it, and SV-A1 records one hazard the revert note does not name.

---

## Verdicts

| Fix | Verdict | |
|---|---|---|
| **IAR-A1** floor on fresh page ids | **holds with a caveat** | The floor mechanism is correct and the maps read are the right ones; the call site `pageIdFloor()` reaches for the lazy session builder that its own sibling forty lines away documents as forbidden (SV-B2). |
| **IAR-A2** `saveState(boolean, boolean)` | **holds** | The one-argument overload is behaviour-identical for all six callers; `captureSession=false` is right for the archive and nothing later in the method depends on the capture. |
| **IAR-B1** `autonomy.json` in the archive | **holds** | Resolved identically at both ends (`new File(AUTONOMY_FILE_NAME)`, CWD-relative, `prefix` empty on the live write), and `Util.zipInto` skips an absent source silently as claimed. |
| **IAR-B2** `getActiveAccs` unsynchronized | **holds** | Every write to `activeLocomotives` is a whole put or remove; no path list is edited in place after being published; no caller held the monitor across the call. |
| **DR-A1** `HELD_FIELDS` | **holds with a caveat** | The map covers exactly the twelve fields the three old lists covered and every shape is correct. The guard is weaker than the disposition and the test's own javadoc claim: the shape half is not exercised at all (SV-C1), measured. |
| **DR-B3** `blockingOccupantOf` | **holds** | The extraction is behaviour-identical, the `continue` is correct, and calling it from `firstClearOrWhyNot` changes wording only, never a decision. |
| **DR-B4** refusing an unreadable index | **does not hold** | On a `gleisbild.cs2` that is not valid UTF-8 - which every TrainControl before 2026-07-27 produced for a non-ASCII page name - the refusal is permanent, not transient, and it removes the only path that repaired the file. Its own message says "Try again in a moment" (SV-B1). |
| **DR-B7** `getPoints`/`getEdges` under the monitor | **does not hold** | Puts the Layout monitor behind the TrainControlUI monitor, against a dispatch that takes them the other way round, on a path that runs on every diagram-monitor tick during a run. That is a deadlock, not a freeze (SV-A1). |
| **DR-B9** two logged drops | **holds** | Both keys present exactly once in all eight bundles, ASCII-only, one `{0}` each, both sites correctly placed. |

Five hold, two hold with a caveat, two do not.

---

## SV-A1. DR-B7 makes the two monitors lockable in both orders, and the EDT takes them

| | |
|---|---|
| **Severity** | A |
| **Status** | Open against the commit. Already reverted in the working tree, uncommitted. |

`Layout.getEdges` (4964) and `Layout.getPoints` (4976) became `synchronized`. The revert note now standing in
the working tree gives two reasons - an EDT freeze, and a quadratic sweep inside `deleteEdge`. Both are
right. Neither is the worst of it.

**The lock order the commit creates.** `TrainControlUI.updateVisiblePoints()` (19787) is
`synchronized public` - it holds the *TrainControlUI* monitor - and its body is
`for (Point p : this.model.getAutoLayout().getPoints())`. With DR-B7 applied, that is:

> **TrainControlUI monitor held -> Layout monitor wanted.**

`TrainControlUI.repaintAutoLocList(boolean)` (19901) does the same at 19908 (`getPoints().isEmpty()`).

**The lock order that already exists.** `configureAndLockPath` opens `synchronized (this)` on the Layout and
calls `configureEdge` inside it. `configureEdge` calls `acc.setState(state)` ->
`Accessory.turn()`/`straight()` -> `MarklinAccessory.setSwitched` (`MarklinAccessory.java:208`), whose body
is `this.network.getGUI().repaintSwitch(...)` - and `TrainControlUI.repaintSwitch` is `synchronized public`
(6121). The `invokeLater` inside it does not help: the monitor is on the method, so it is taken on the
calling thread before the lambda is ever queued. That is:

> **Layout monitor held -> TrainControlUI monitor wanted**, on the locomotive thread, once per accessory,
> with `Thread.sleep(CONFIGURE_SLEEP)` between commands inside the same monitor.

**Both halves run during an ordinary autonomy run.** `DiagramMonitorDriver` publishes on a timer and ends
its EDT block with `if (ui != null) ui.updateVisiblePoints();` (`DiagramMonitorDriver.java:278`) - every
tick, whenever a train moves. So the EDT enters `updateVisiblePoints`, takes the UI monitor, and blocks on
the Layout monitor; the dispatching locomotive holds the Layout monitor and blocks on the UI monitor at its
next turnout. Neither ever returns. The application hangs with no timeout and no recovery - worse than the
frozen Stop button IAR-B2 removed three thousand lines earlier in the same file, in the same commit.

Even where the interleaving misses, the plain freeze is certain: the EDT waits out the whole configuration
phase inside a `synchronized` UI method, so nothing else that needs the UI monitor - `repaintLoc`,
`repaintMappings`, `repaintLayout` - runs either.

**On the other two reasons.** The `deleteEdge` cost is real (`Layout.java:2280` walks `getEdges()` while
removing one, so a copy per call makes a linear sweep quadratic). And a third: `HomeStaging.snapshot` walks
`getPoints()` twice and `getIncomingEdges` per point on the Return-Home planner thread, so DR-B7 would have
put a background planner on the same monitor a dispatch holds.

**On the hazard DR-B7 named.** It is real and it is still there: `HomeStaging.snapshot`
(`HomeStaging.java:117,148`) iterates the plain `LinkedHashMap` view off the EDT while `createPoint` /
`deletePoint` / `renamePoint` write it from the EDT, and neither `planReturnToHome` nor `triageReturnToHome`
is synchronized. The revert leaves that unaddressed, correctly filed rather than fixed in a hurry. Nothing
in `src/` or `test/` mutates either returned collection, so `Collections.unmodifiableCollection` would not
have broken a caller - that half of the concern is clean.

---

## SV-B1. DR-B4's refusal is permanent where the index is not valid UTF-8, and its message says otherwise

| | |
|---|---|
| **Severity** | B |
| **Status** | Open. |

`readLayoutIndexIds` reads with `Files.readAllLines(path, UTF_8)`. Java 8's decoder reports rather than
replaces, so a file that is not valid UTF-8 throws `MalformedInputException` - which is an `IOException`,
and therefore lands in the branch DR-B4 added. `existing` is then empty and `getUnreadableIndex()` is
non-null, so `writeLayoutIndex` throws.

Measured, against the committed `LayoutDiagram`, on a `config/gleisbild.cs2` holding one cp1252 `0xFC`:

```
index readable? ids = {}
unreadable flag = java.nio.charset.MalformedInputException: Input length = 1
attempt 1: REFUSED -> ...Try again in a moment: Input length = 1
attempt 2: REFUSED -> ...Try again in a moment: Input length = 1
attempt 3: REFUSED -> ...Try again in a moment: Input length = 1
```

**Such a file is not hypothetical.** `writeLayoutIndex` used `FileWriter` - platform default, Cp1252 on this
machine - until `dabb6e53` (2026-07-27). Any local layout with a non-ASCII page name last written by a build
older than that is undecodable today. This file's own comment says the mangling happened
(`LayoutDiagram.java:844`, from the commit that fixed it).

**What the fix costs.** Before it, the one operation that hit this rewrote the index 1..n *in UTF-8*, so the
file was repaired and readable for ever after, and the autonomy setup recovered through
`pageNamesWhenWritten`. The renumber was ugly and self-healing. After it, all three page operations - add /
combine (`TrainControlUI.java:18415`), rename / duplicate (18779) and delete (18917) - throw, so there is no
gesture left in the application that rewrites the file, and no way out from the UI. "It can be saved again
in a moment" is exactly what is not true here, and it is what the operator is told.

**The delete path is left half done.** At 18917 the throw happens *after*
`this.model.getLayout(going).deleteLayoutFile()` and after the store has forgotten the page and saved. So
the page's file is gone, its settings are gone, the index still names it, and `layoutEditingComplete()` on
the next line never runs. The result is a phantom entry that survives a restart as a page whose file is
absent. The prior behaviour wrote a consistent index.

**Smaller notes on the same fix.** The guard is `existing.isEmpty() && getUnreadableIndex() != null`, so a
`gleisbild.cs2` that is readable but names no pages still renumbers - the same hole, reached by a truncated
or zero-length file rather than a locked one. And in the cited scenario - "a sync client holding it open" -
`Util.writeAtomically` ends in `Files.move(..., REPLACE_EXISTING)`, which fails on Windows against a file
another process has open, so the old code already threw there. The case the fix actually changes is the one
where the read fails and the write would have succeeded, which is the decode failure above and an
unhydratable placeholder.

**Suggested shape.** Refuse only where the index is present *and* previously named pages - or repair by
falling back to a lenient decode (`ISO-8859-1`) before concluding the file is unreadable, since the write
that follows is UTF-8 and would fix it. Either keeps DR-B4's guarantee without removing the repair.

---

## SV-B2. `pageIdFloor()` builds an autonomy session, which the same method forbids forty lines earlier

| | |
|---|---|
| **Severity** | B |
| **Status** | Open. IAR-A1's mechanism is unaffected; this is the call site. |

`TrainControlUI.pageIdFloor()` (1461) is `session == null ? 0 : session.getStore().highestPageIdSeen()`,
where `session = getAutonomySession()` - the *lazy builder*, not the field.

`getAutonomySession()` (1955) on a cold cache: constructs a session for any local layout
(`AutonomyCompanionStore.isUsable()` is only "the folder exists"), parses **every page** via
`this.model.getLayout(name)`, calls `session.open(pages)` which runs `store.load()`, `rebuild()`,
`migrateStationLabels()` - which **rewrites gleisbild files on disk** - and `forgetCaptionsOfNonStations()`,
may queue a modal dialog, and caches the result.

`duplicateOrRenameCurrentLayout` refuses to do this deliberately, at 18737, in a comment written for this
exact reason:

> "getAutonomySession() builds one on demand: it opens every page, runs the caption migration - which
> rewrites gleisbild files and can raise a dialog ... So renaming a page on a layout where autonomy had
> never been touched invented a setup out of nothing and attributed it to a gesture with nothing to do with
> autonomy."

It uses `this.autonomySession` for that reason - and then calls `pageIdFloor()` at 18779, forty lines later,
which uses the getter. The same method now does both. This is the pattern the README's "when you fix a call
site, grep for its twins" rule exists for, arriving in the opposite direction: the twin was already right
and the new code did not read it.

**Where it bites.** All three sites run on the EDT inside a page operation. On a layout where nothing has
warmed the cache, the gesture now parses every page and rewrites page files before writing the index. In the
**delete** path the ordering makes it worse: `pageIdFloor()` is evaluated as an argument at 18917, so on the
`session == null` branch it builds a session over `this.model.getLayoutList()` - which still contains the
page whose file was deleted five lines earlier, because `layoutEditingComplete()` has not run - and caches
that session as `this.autonomySession`.

In practice the cache is often warm (the diagram strip and a dozen other paths call the getter), so this
will frequently be a no-op lookup. It is not one on a first page gesture after start, and it is not one on a
layout with no autonomy at all.

**The floor itself is right.** `highestPageIdSeen()` reads `pageNamesWhenWritten.keySet()` and
`pageIdToName.keySet()` - both keyed by **id**, which is the correct pair; using `pageNameToId` would have
been the bug a page legally named `2` produces. Non-numeric ids are skipped rather than guessed. `next =
floor + 1` is still pushed above every live id by the loop below it, so a floor can never collide with an id
in use. A floor of zero - no session, no local path, no setup - is exactly the previous behaviour, and where
there is no setup there are no held settings to inherit, so the protection is not needed. Retired ids are
now never reclaimed and page ids climb monotonically; nothing in the tree bounds or reuses them, and
`gleisbild.cs2` writes `.id=` as a plain integer, so this appears to cost nothing.

**Untested at the seam.** `testARetiredIdIsNotHandedOutTwoWritesLater` passes the floor to `writeLayoutIndex`
directly. It proves the floor is honoured and that `highestPageIdSeen()` returns 2; it does not exercise
`pageIdFloor()`, which is where the defect above lives. That is the shape recorded as "an extracted rule
moves the bug to the call".

---

## SV-C1. DR-A1's guard does not exercise the half its own javadoc says only behaviour can catch

| | |
|---|---|
| **Severity** | C |
| **Status** | Open. The fix holds; the guard is narrower than recorded. |

`HELD_FIELDS` is correct: twelve entries, exactly the twelve names the old merge array carried and exactly
the classification the four old arrays gave, checked field by field against the store's collections and
against `KNOWN_SHARED` (which additionally holds `version`, `activeConfiguration` and `pages`, none of which
is per-square and none of which belongs in the hold). Each `Held` shape is right.

The test is the part that overstates. Its javadoc says:

> "the map also encodes each field's SHAPE - whether its values name squares - and a field held with the
> wrong shape cannot be caught by any amount of name-matching. Only behaviour catches it."

Measured. Two mutants, each compiled outside the repository and put ahead of the build on the classpath:

| Mutation | `regression.testPageIdsAreDurable` |
|---|---|
| `blockedPoints` held but not merged (the finding's own mutation) | **1 failure of 10** - caught, as claimed |
| `stationSignals`, `blockedPoints`, `portals`, `captions` all downgraded to `Held.PLAIN` - the entire shape half of the map destroyed | **0 failures of 10** - not caught |

The cause is the fixture. `testASaveWhileAPageIsAbsentLosesNothingOfIt` puts every square on the absent page
`Ghost`: `setCaption(ghostCaption, ghost)`, `pairPortals(ghost, ghostFar)`,
`setProtectingSignals(ghost, [ghostSignal])`. Every entry therefore has a **key** on the absent page, so the
key-only `PLAIN` check holds all of them and the square-aware shapes are never needed. The entry the shapes
exist for - a key on a **loaded** page whose value names an **absent** one, which is what a caption pointing
across pages or a station protected by a signal on another page looks like - is not built anywhere in the
test.

A second gap in the same test, and the one the README names directly ("assert the precondition that makes a
test meaningful"): nothing asserts that `Ghost` is actually held. If the fixture ever drifted so that the
page stayed resolvable, every entry would round-trip by the ordinary path and all six assertions would still
pass. `AutonomyCompanionStore` has a public predicate for exactly this question, declared immediately below
`highestPageIdSeen()`, and it is not used.

`countIn` itself works: `'"' + field + '"'` concatenates as a String, the brace/bracket walk is balanced,
and the empty-collection case returns 0 rather than 1. It is only ever asked about fields whose value is an
object or an array, which is the assumption it rests on.

**What would close this.** One cross-page entry in the fixture - a caption on `Alpha` naming a square on
`Ghost`, and a station on `Alpha` protected by a signal on `Ghost` - plus a precondition assert that the
page is held. The second mutant above then has to fail.

---

## Deadlock and lock-ordering sweep

Asked of the whole commit, independently of the findings above.

**One new hazard, and it is the worst thing in the commit.** DR-B7. Written up as SV-A1: `getPoints` and
`getEdges` take the Layout monitor from two `synchronized` TrainControlUI methods, while
`configureAndLockPath` takes the two in the opposite order through `acc.setState` -> `setSwitched` ->
`repaintSwitch`. Both orders are exercised by an ordinary autonomy run. Deadlock, unrecoverable. Already
reverted in the working tree.

**Everything else in the commit is clean on this axis:**

- **IAR-B2** *removes* a monitor acquisition and adds none, so it cannot create an ordering. Verified that
  the branch reaching it takes no other Layout lock: `hasAutoLayout`, `isAutonomyRunning`, `isRunning`,
  `isStagingInProgress` and `isAutoRunning` are all plain field reads. The freeze it names is therefore
  really gone from that path. `control.getAccessoryByName` was never protected by the Layout monitor in the
  first place, so nothing is lost by calling it outside.
- **`blockingOccupantOf`** is private and reached only from methods that already hold the monitor
  (`isPathClear` under `configureAndLockPath`'s `synchronized (this)`, and `firstClearOrWhyNot` from the
  `synchronized` `explainDestinations` family). Re-entrant, no new acquisition, no new ordering.
- **`pageIdFloor()`** takes no lock. It does perform file I/O and page parsing on the EDT and can queue a
  dialog - a freeze risk, not a deadlock - recorded as SV-B2.
- **`saveState(boolean, boolean)`** changes no locking. Note that its `getPoints().isEmpty()` at 1663 is one
  of the sites that would have been affected by DR-B7, on the EDT, in `WindowClosed`.
- **`unreadableIndex`** is a `volatile` static with no lock and no wait.
- **The DR-B9 log calls** go through `control.logf`, which neither of the two enclosing paths holds a Layout
  lock across differently from before.

**Verdict: not clean.** One deadlock, from DR-B7, in the same commit that removed a freeze of the same
shape for the same reason.

---

## Where the Dispositions overstate

**DR, on DR-B4:**

> "The page the caller wanted is not saved; that is the lesser harm, and it can be saved again in a moment."

Not for a `gleisbild.cs2` that is not valid UTF-8. It can never be saved again from the application, because
every gesture that would rewrite the file now refuses, and the refusal message repeats the sentence above to
the operator. Measured over three consecutive attempts (SV-B1).

**DR, on DR-B7:**

> "Fixed by sweeping the sibling, which is what the finding is about: `getPoints` and `getEdges` copy under
> the monitor exactly as `getHomeStations` does, citing its javadoc's reasoning."

`getHomeStations` is `synchronized public` already, so copying under the monitor added nothing to its lock
behaviour. `getPoints` and `getEdges` were not, and are called from two `synchronized` TrainControlUI
methods, so the same edit changed the *lock order* rather than only the copy. The reasoning was carried
across without the precondition that made it safe (SV-A1).

**DR, on DR-A1:**

> "mutation-checked against the exact mutation this finding used: held-but-not-merged now fails it, naming
> the collection."

True, and reproduced. But the test's own javadoc goes further - "a field held with the wrong shape cannot be
caught by any amount of name-matching. Only behaviour catches it" - and this behaviour does not: four shapes
can be destroyed at once with the suite still green (SV-C1). The disposition's next sentence records one
known non-catch (a field removed entirely); this is a second, and a larger one.

**IAR, on IAR-A1**, and the javadoc on `pageIdFloor`:

> "Zero when there is no session or no setup, which is exactly the behaviour this had before."

The *value* is. The act of asking is not: `getAutonomySession()` builds a session where none existed, opens
every page and runs a migration that writes to disk (SV-B2). "There is no setup" is not a state this method
can observe without first creating the machinery that would have made one.

**The commit message**, on coverage:

> "Battery: 100 classes green, the one failure being OB-084 as filed."

Consistent with what I saw - `testPageIdsAreDurable` is 10/10 green as committed. It is worth saying plainly
that a green battery is not evidence about DR-B7: the deadlock needs an autonomy run with the diagram
monitor driving, which no test in the suite stages.

---

## D. Attacks that came back clean

**D1. IAR-A2 preserves every caller.** Six call sites, all enumerated. `saveState(boolean)` is not an
interface method - `ViewListener.saveState(boolean)` is implemented only by `MarklinControlStation`, and
`View` does not declare it at all - so the overload cannot break a contract, and there is no second
implementer anywhere in `src/` or `test/`. `saveState(backup, !backup)` reproduces the old
`if (!backup && ...)` gate exactly. Arity disambiguates; no call site is ambiguous.

**D2. Nothing later in `saveState` depends on the capture.** After the `captureSession` block the method
writes the legacy `autonomy.json` (gated on `activeDiagramConfiguration == null`, which is the opposite
condition) and calls `saveLayoutTitles()`. Neither reads anything the capture writes.

**D3. The archive holding a stale active configuration is the right trade.** With `captureSession=false`,
`config/autonomy/setup.json` in the zip is the last *saved* state rather than the live session. That is what
"take a copy" should mean, and the alternative was the defect: committing an open editor's unsaved edits
from a non-event thread, past a `Cancel` that could not undo them. The FR-015 comment was edited to say so.
Unsaved work not being in a backup is ordinary; a backup that commits unsaved work is not.

**D4. IAR-B1 resolves the same file at both ends.** The live write uses
`prefix + TrainControlUI.AUTONOMY_FILE_NAME` with `prefix` empty on a non-backup save; the archive uses
`new File(TrainControlUI.AUTONOMY_FILE_NAME)`. Both are CWD-relative in one process, so they are the same
file, and `saveState(false, false)` runs first, so the copy is fresh. `Util.zipInto` skips a source that
does not exist (`Util.java:178`) without reporting a failure, so the "skipped silently" claim is accurate,
and there is no zip-entry collision with either `DATA_FILE_NAME`.

**D5. IAR-B2's safety argument holds under a full search.** `activeLocomotives` is written in exactly four
places - `locDeleted` (742), `executePath` (4293, 4458) and 4768 - and every one is a whole `put` or
`remove`. No `path.add/remove/set/clear/sort` exists anywhere in `executePath`; `unlockPath` builds a
separate `output` list and does not touch the argument. `getActiveLocomotives()` returns the live map to
twelve call sites and none mutates a value list. `Edge.configCommands` has no writer outside `Edge`. The
caller in `LayoutLabel` uses the result for one `contains` and needed no atomicity across the call.

**D6. DR-B3 is a faithful extraction.** The original loop `continue`d past an exempt or empty square and
`return false`d on the first real one; `blockingOccupantOf` returns that same first square and the caller
returns false on non-null. `getBlockLocomotive()` is preserved, so the "whole block, not the named copy"
property is intact. The added null guards on `destination` and `watched` are unreachable and harmless. In
`firstClearOrWhyNot` the `continue` is exactly equivalent to falling through - nothing follows the `why`
assignment before the loop's end - and only `why` is affected, never the loop's decision. Where FR-001 fires
it is also the reason `isPathClear` itself would have given, so the two agree.

**D7. DR-B9's bundles are correct.** `autolayout.warnExcludedLocomotiveNotInDatabase` and
`autolayout.warnRunLocomotiveNotInDatabase` each appear exactly once in all eight bundles
(base, da, de, es, fr, it, nl, pl), all ASCII with `\uXXXX` escapes, each with one `{0}`. Both call sites are
placed correctly: the exclusion branch is inside the existing `try`, and the run-list branch is the `else` of
`if (loc != null)` where `loc = control.getLocByName(s)`.

**D8. The `unreadableIndex` static cannot give a wrong answer today - but it is a trap.** Withdrawn as a
finding. `readLayoutIndexIds` clears it on entry and the only production caller is `writeLayoutIndex`, which
reads it on the next statement, so it behaves as a second return value. The race needs two concurrent
`writeLayoutIndex` calls and all three call sites are on the EDT. Two things left standing: the second
public caller that ever appears will read a flag another thread's read has cleared or set, and
`getUnreadableIndex()` is called twice in the throw expression (1014 and 1017), so a concurrent clear
between them is an NPE. Returning the failure with the map would remove both.

**D9. `Collections.unmodifiableCollection` would not have broken a caller.** Withdrawn. Every one of the
roughly forty `getPoints()` / `getEdges()` call sites across `src/` and `test/` iterates, sizes, or copies
into a new list; the only `Collections.sort` is in `toJSON`, on the copies. `deleteEdge` removes from
`this.edges` directly rather than through the view, and does so before the walk. So the DR-B7 revert is
warranted by the lock order and the copy cost, not by any caller needing the collection live or mutable.

**D10. A third silent drop of DR-B9's shape is not swept.** `Layout.java:6493` -
`if (control.getLocByName(loc) != null)` in `fromJSON`'s point loop - drops a locomotive *placement*
without a word. Left as an observation rather than raised: DR-B9 was scoped to restrictions the operator
believes are in force, and a placement that vanishes is visible on the diagram, which is the property that
made the other two worth a message. Worth a line in OB-086 if that list is revisited.

**D11. IAR-A1's floor cannot be wrong in a harmful direction.** Checked the cases the brief asks about. No
session, no local path, no `setup.json`, or a blanked `pages` record all give zero or the live maximum,
which is the previous behaviour - and in the no-setup case there are no held settings for a new page to
inherit, so the protection is not needed. `sharedFields` writes `pages` as the union of live ids and
remembered absent ones, so the record survives every save during an absence; a non-numeric id is skipped;
neither map can hold a null key. A floor that is too high only wastes ids.

---

## Dispositions

**Claude, 2026-08-24.** All four acted on. Two were live regressions of mine, and one of those was
worse than the reviewer who first raised it knew.

| | What was done |
|---|---|
| **SV-A1** | Already reverted before this pass reported, but for the wrong two reasons - the revert note gave the EDT freeze and the quadratic sweep and missed the deadlock, which is the one that matters. `updateVisiblePoints` and `repaintAutoLocList` are synchronized on the UI and would have taken the Layout monitor while holding it; `configureAndLockPath` holds the Layout monitor and reaches `repaintSwitch`, which is synchronized on the UI; `DiagramMonitorDriver` fires the first on the event thread every monitor tick during a run. AB-BA, on an ordinary run. The note now says so. |
| **SV-B1** | Fixed. `readIndexLines` falls back to ISO-8859-1, which is total, when strict UTF-8 refuses - so an index written by any build before 2026-07-27, when this file was written with a `FileWriter`, is read rather than refused. The DR-B4 throw stays, and is now reachable only for a file that genuinely cannot be read. Test seen red with the fallback removed, on the assertion about losing the ids. |
| **SV-B2** | Fixed. `pageIdFloor()` reads the `autonomySession` FIELD instead of calling the lazy builder, which parses every page, runs a disk-writing migration and can raise a dialog - on the event thread, mid-write, forty lines below a comment in the same file forbidding exactly that. No session means no floor, which is what this did before the floor existed. |
| **SV-C1** | Fixed, and it needed the fixture rather than the assertions. A caption on a LOADED page now points at a station on the absent one, so the value-side check decides an entry; and the test asserts the entries are actually HELD rather than only that the file still has them. Re-measured: the shape downgrade fails it, and so does removing a field outright - which the previous javadoc said it could not catch. |

### The javadoc that was stale within hours

The DR-A1 test carried a paragraph explaining, at length and correctly, why removing a field from
`HELD_FIELDS` could not fail it. Strengthening the fixture made that false, and the explanation would
have sat there being wrong. It now lists three mutations and what each did, because a claim about what
a test catches is exactly the kind that has to be measured rather than derived - and this session has
now produced two of them.

### On SV-A1 arriving from two directions

Adam asked, independently and before this pass reported, whether any new deadlock had been introduced -
prompted by a hang on a build 28 commits old that could not have contained one. It had, by me, that
morning. The report he filed was a false positive and the question it prompted was not.
