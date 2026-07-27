# Independent code review - v2_7_2 to HEAD

**Version reviewed:** commit `9c9899b` ("BFS optimization test", 2026-07-26), branch `master`,
described as `v2_7_4c-38-g9c9899b`. 72 commits since tag `v2_7_2`, ~10,000 changed lines in `src/`.
**Reviewed:** 2026-07-26. **No code was changed as part of this review.**

**Scope and goal:** an independent full pass over the `v2_7_2..HEAD` diff, looking primarily for
regressions introduced since v2_7_2 (including by the fixes that followed the July 2026 review),
plus preexisting bugs. Conducted *before* reading `2026-07-code-review.md`, to keep it independent;
the comparison against that review is a separate section appended after the findings were frozen.

---

## Status

| # | Finding | Severity | Type | Status |
|---|---------|----------|------|--------|
| N1 | `receiveMessage` accessory branch: TOCTOU between `hasId(id)` and three `getById(id)` calls | Low | Preexisting gap, same class as the fixed locomotive branch | **Fixed 2026-07-26** - reachability mechanism corrected, see note |
| N2 | `validatePathActuation` treats *any* interrupt as "validated" | Very low | New code; interrupt branch verified unreachable today | Closed as trap-for-future - see disposition note |
| N3 | `moveLocomotive` NPEs on an unknown `targetPoint` in the locomotive!=null branch | Low | Preexisting | **Fixed 2026-07-26** |
| N4 | `getImageCache()` lazy init is unsynchronized | None | **Premise incorrect** - the getter is `static synchronized`, so the race cannot occur | **Changed anyway 2026-07-26**, for lock contention rather than correctness |
| B1 | `PATH_INTEGRITY_VALIDATION` defaults ON: autonomy without CS echoes stalls every path | Behavior change (deliberate) | Author confirmed offline autonomy requires debug + simulate, and simulate skips validation | **Closed - not an issue** |
| B2 | Autonomy-JSON routes omitting `triggerType` now fire on the opposite sensor edge | Behavior change (deliberate fix) | Affects hand-written JSON only | **Closed 2026-07-26** - the requested release note already existed (`Readme.md`, added in `1d859ee`, before this report was written); the reviewer had not checked the changelog |
| B3 | Sticky `actuationConfirmed`: a repeat command to the last-confirmed state passes validation instantly | Design property, not a defect | Informational | Recorded |
| B4 | `unlockPath` lock-edge release is unsafe for hand-edited configs where two edges share a lock edge without traversing it | Design limitation, documented in code | Informational | Recorded |

No high-severity regressions were found. Every load-bearing claim below was verified against the
method that actually enforces it, per `docs/reviews/README.md`; the "verified equivalences" section
records the checks that came back clean, so the next reviewer does not redo them - and can see
which assumptions this review rests on.

Findings D1-D4, M1-M4 and T1-T4 from the follow-up deep dives of the original (pre-diff) code have
their own authoritative status table in the "Deep dives" section at the end of this document.

---

## Findings

### N1 - `receiveMessage` accessory branch: check-then-act on `accDB` (Low)

[MarklinControlStation.java:1674](src/org/traincontrol/marklin/MarklinControlStation.java:1674)

The locomotive branch of `receiveMessage` was fixed this cycle to null-check `locDB.getById(l)`
because a delete between the cache read and the lookup NPE'd inside the executor, silently dropping
the message. The accessory branch two blocks down still has the identical shape: `hasId(id)`
followed by **three** separate `getById(id)` calls (one for `parseMessage`, two inside the
`repaintSwitch` argument list). If the accessory is deleted or replaced between them - e.g. an
accessory re-created as the other type, which now goes through `RemoteDeviceCollection.add`'s
eviction - the NPE is swallowed by the executor's `Future` exactly as the locomotive one was.

*Reachability:* requires an accessory deletion concurrent with a CS echo for that same accessory.

**Correction (2026-07-26).** The mechanism named above - "an accessory re-created as the other type,
which now goes through `RemoteDeviceCollection.add`'s eviction" - does not hold. On a switch/signal type
flip the UID is unchanged and only the name differs, so `existingId.equals(id)` is true, `db.remove` is
never reached, and the `removeIf` added for B18 evicts from `names`, not from `db`. `db.put` then
replaces in place, so `getById(id)` cannot return null through that path.

The finding is nonetheless reachable, by a route not cited: `accDB.delete(...)` at
`MarklinControlStation.java:228` drops accessories whose address is invalid during state restore, and
`RemoteDeviceCollection.delete` does `db.remove(id)`. The CAN listener is already running by then, so an
echo for that accessory during restore hits the window.

**Fixed.** Resolved once into a local with a null check, mirroring the locomotive branch - which also
removes two redundant map lookups. The argument for fixing was not the (narrow) reachability but that
the twin of an already-fixed defect had been left standing thirty lines away in the same method.

### N2 - `validatePathActuation` returns `true` on interrupt (Low)

[Layout.java:1517](src/org/traincontrol/automation/Layout.java:1517) (the `InterruptedException`
handler inside the wait loop)

The handler's comment says "autonomy is being stopped - abort validation without flagging a
misconfiguration," and for that scenario returning `true` is reasonable. But the method returns
"validated" for *any* interrupt of the calling locomotive thread, whatever its origin. The
consequence of a wrong `true` here is `configureAndLockPath` returning success and `executePath`
running the locomotive on a path whose accessories were never confirmed - the exact failure the
feature exists to stop.

*Reachability:* today locomotive threads are only interrupted on autonomy shutdown, so the current
behaviour is acceptable in practice; this is a trap for the next person who adds an interrupt.
Returning `false` without counting it as a failure (no `handleMisconfiguredPath`) would be the
conservative shape if this is ever revisited.

### N3 - `moveLocomotive` NPE on unknown target point (Low, preexisting)

[Layout.java:2747](src/org/traincontrol/automation/Layout.java:2747)

In the `locomotive != null` branch, `this.getPoint(targetPoint).isDestination()` dereferences
without a null check; the `locomotive == null` branch below checks `getPoint(targetPoint) != null`.
An unknown point name in the first branch throws instead of logging.

*Reachability:* all nine callers are UI flows passing names of existing graph nodes, so this is
unreachable in normal use - a guard-for-the-next-caller fix, not a changelog entry.

**Fixed (2026-07-26).** The point is resolved once into a local and null-checked, reporting
`autolayout.errorPointDoesNotExist` rather than throwing. The persuasive argument was the internal
asymmetry - the two branches of one method disagreeing about whether the same call can return null -
rather than the risk.

### N4 - `getImageCache()` lazy init race (Very low, preexisting)

[TrainControlUI.java:1332](src/org/traincontrol/gui/TrainControlUI.java:1332)

`imageCache` is now a `ConcurrentHashMap` (good - the old plain `HashMap` under 6+ threads was the
real bug), but the lazy `if (imageCache == null) imageCache = new ConcurrentHashMap<>()` is itself
unsynchronized static state. Two threads racing the first call can each create a map, and early
puts into the loser are discarded.

**Correction (2026-07-26). The race described here cannot happen.** The getter is declared

```java
synchronized public static Map<String,Image> getImageCache()
```

so it holds the `TrainControlUI.class` monitor for the whole check-then-act, and every read of the
field goes through that one method. There is no path from which a second map, or a partially published
one, is observable. The finding was written from the field declaration and the `if (imageCache == null)`
without the method modifier - the same mistake P4 made in the companion review, and worth noting as a
recurring one: an unreachable-race claim needs every guard on the path read, not just the obvious one.

**Changed anyway, on different grounds.** The field is now `final` and initialised eagerly, and the
getter is a plain return. Not a correctness fix - the justification is that the old getter took a
class-level lock on every call, and `LayoutLabel` calls it once per tile per repaint from both the EDT
and the tile-refresh threads. Verified safe to make `final`: the field had exactly one assignment, is
never cleared or reassigned, and is reachable only through the getter.

---

## Deliberate behavior changes worth surfacing to users

### B1 - Path integrity validation defaults ON

New subsystem: `configureAndLockPath` now waits (via `Accessory.actuationConfirmedMonitor`) for the
CS to echo every accessory command on a path before releasing the locomotive, stops the locomotive
and frees its locks on persistent mismatch. Default enabled
(`Layout.PATH_INTEGRITY_VALIDATION = true`, persisted UI preference `ENHANCED_PATH_VALIDATION`).

The mechanism itself checks out: flag set before `notifyAll`, waiter re-checks under the monitor
(no lost wake-up), the wait deliberately does not hold the Layout lock, `handleMisconfiguredPath`
releases exactly the locks taken (including lock edges, via `setUnoccupied`), and the partial-lock
release on a mid-path configure failure correctly uses `path.subList(0, edgesLocked)`.

**Assumption needing validation by the author:** in v2_7_2, could a user meaningfully run autonomy
with no Central Station connected (not in simulate mode, which requires the debug flag)? If yes,
that workflow now stalls: no echoes ever arrive, every path times out
(`PATH_VALIDATION_MS * (n+1)`), every locomotive is stopped at its start point, and after three
failures a one-time alert fires. The opt-out exists in the UI, but a user upgrading from 2.7.2
would not know to look for it. If offline autonomy was never functional anyway (commands go
nowhere), this is moot.

### B2 - `triggerType` default flip for hand-written autonomy JSON

[MarklinRoute.java:724](src/org/traincontrol/marklin/MarklinRoute.java:724)

In v2_7_2, a route imported from autonomy JSON with no `triggerType` key got `null`, and the
monitor's `== CLEAR_THEN_OCCUPIED` test made it behave as OCCUPIED_THEN_CLEAR. Now it defaults to
CLEAR_THEN_OCCUPIED (the constructor's documented default). The fix is right - null was never a
sanctioned state - but a hand-written JSON file that omitted the key and whose author tuned the
layout around the *old accidental* edge will see the trigger flip on upgrade. TrainControl's own
export always writes the key (`toJSON` line 684-687, and the field can no longer be null), so only
hand-written files are exposed. One release-note line would cover it.

### B3 - Sticky confirmation (informational)

`Accessory.actuationConfirmed` latches true on the first CS echo and never resets. Thereafter,
`isConfirmedAt(desired)` passes immediately whenever the *last confirmed* state equals the
commanded state - including when a fresh command's echo has not yet arrived (or never arrives).
This matches the documented contract ("the last CS-confirmed position matches desired") and is the
right trade-off for switches already lying in position; recorded here so nobody later "discovers"
that validation does not wait for the newest echo per command.

### B4 - `unlockPath` lock-edge release for shared lock edges (informational)

[Layout.java:1661](src/org/traincontrol/automation/Layout.java:1661)

Releasing a skipped edge's lock edges is correct for editor-written symmetric crossings, and fixes
a real bug (lock edges held forever after an end-point handoff). The in-code comment already
records the one unsafe shape - a hand-edited `autonomy.json` where two edges name a third as a lock
edge without either traversing it. Verified the reasoning: `isPathClear` rejects conflicting paths
on the crossing edge's own occupancy flag, which the current holder retains; it does not inspect
lock edges, so the protection genuinely comes from the traversal requirement.

---

## Verified equivalences (checks that came back clean)

These are the places where the diff *looked* like it might change behaviour and did not - each was
verified in the enforcing method, not assumed:

- **`Edge.isOccupied` rewrite** (single read of `getCurrentLocomotive()`): equivalent to the old
  `end.isOccupied() && ...` because `Point.isOccupied()` is exactly `currentLoc != null`
  ([Point.java:304](src/org/traincontrol/automation/Point.java:304)). The race it fixes (check
  passing, then dereference NPE) was real.
- **`RemoteDeviceCollection.add` one-to-one eviction**: cannot evict a duplicated locomotive,
  because `MarklinLocomotive.getUID()` embeds the name
  ([MarklinLocomotive.java:400](src/org/traincontrol/marklin/MarklinLocomotive.java:400)), so two
  locomotives sharing a decoder address have distinct ids. The accessory case ("Switch 5" lingering
  after becoming "Signal 5") is the intended target. Verified every `locDB.add` call site passes
  `getUID()` and that `renameLoc` renames *before* re-adding.
- **BFS visited-set change** (`LinkedList` -> `HashSet`, still marked on dequeue): `Point`
  overrides `equals`/`hashCode` consistently on name, `contains` decides membership identically;
  enqueue behaviour unchanged, so the alternative-route search semantics (`excludePaths`) are
  preserved.
- **Stats rewrite date keys**: `Locomotive.getDate` is `SimpleDateFormat("yyyy-MM-dd")`, identical
  to `DateTimeFormatter.ISO_LOCAL_DATE` output, so `getDailyRuntimeStats`/`getDailyCountStats`/
  `getTotalLocStats` keys still match the keys runtime tracking writes. The DST reasoning for
  stepping by `LocalDate` is sound.
- **`getAccessoryByName` type-prefix fallback**: verified `getAccessoryByAddress` really does
  create a switch on miss ([MarklinControlStation.java:2532](src/org/traincontrol/marklin/MarklinControlStation.java:2532)),
  so the moves away from it (Edge validation, `NodeExpression`, `newAccessory` actuation-count
  carry-over) close a genuine invent-on-lookup hole. The fallback swaps only the type prefix;
  address and protocol suffix must still match exactly.
- **`addressFromUID` reorder**: `MULTI_UNIT_BASE (0x2c00) < MFX_BASE (0x4000) < DCC_BASE (0xc000)`
  confirmed at [MarklinLocomotive.java:32](src/org/traincontrol/marklin/MarklinLocomotive.java:32),
  so highest-first testing is correct and the old code indeed could never reach its DCC/MU branches.
- **Un-threaded repaint calls** (`repaintLoc`, `repaintSwitch`, `updateLatency` now called directly
  from message-processor threads): all three marshal internally via `invokeLater` - verified in
  TrainControlUI, not taken from the comments.
- **`ConcurrentHashMap` null-hostility in Layout**: every accessor that can receive a null
  `Locomotive` now guards (`getDestination`, `getStart`, `getReachedMilestones`,
  `getLatestMilestoneS88`, `getPointsInActivePath`, `getPossiblePaths`, `waitForS88Reached`,
  `locDeleted`); the two unguarded-looking sites (`moveLocomotive` line 2741/2786,
  `updatePendingS88`) are protected by surrounding null checks. `fromJSON` filters unresolvable
  locomotive names before `setLocomotivesToRun`.
- **`notifyOfPowerStateChange` under `speedMonitor`**: lock ordering (locomotive -> speedMonitor)
  matches `MarklinLocomotive.setSpeed`; nothing takes the locks in the reverse order, so no new
  deadlock ordering is introduced.
- **Message bundles**: all 8 files ASCII-only (project requires `\uXXXX` escapes for Java 8),
  1186 keys each, zero key differences vs. English, and every new key referenced by changed code
  exists. The one removed key (`acc.commandConflictSameAddressMustRename`) matches removed code -
  no dangling references.
- **`CS2Message` masking fixes**: verified against the parse path - the sign-extension fix is what
  makes commands >= 0x40 parseable at all, and the outgoing constructor's `hash & 0xFFFF` now
  agrees with the parser. The length clamp (`& 0x0F`, capped at payload size) matches the CAN DLC
  range.
- **`RouteCommand.fromLine` unchecked-exception wrapping**: all callers declare `throws Exception`
  and none catches `NumberFormatException` specifically, so no caller silently changes behaviour.

## Assumptions - resolved with the author (2026-07-26)

1. **B1 - resolved, closed.** Author: offline autonomy has always required debug *and* simulate,
   both of which existed in 2.7.2 - there was never a working "autonomy with no CS, no simulate"
   workflow. Since `configureAndLockPath` skips validation entirely in simulate mode, the
   ON-default cannot strand anyone. No release note needed for the offline case.
2. **N2 - downgraded to a documented trap; interrupt branch verified dead.** Author was unsure
   ("probably not"), so it was verified against the source: every `.interrupt()` in `src/` is a
   `Thread.currentThread().interrupt()` re-assertion after a caught `InterruptedException`;
   nothing anywhere interrupts *another* thread, and no `Future.cancel(true)` exists (autonomy
   stop is the cooperative `stopLocomotives()`). The `InterruptedException` handler in
   `validatePathActuation` - and its "autonomy is being stopped" comment - therefore describe a
   situation that cannot currently occur. The return-`true` shape remains the wrong default if an
   interrupt source is ever added; that is now the whole finding.

   **Author's adjacent concern, recorded:** the real danger around stopping is a *forceful
   autonomy restart* - re-parsing the JSON creates a new `Layout` while locomotive threads from
   the old one may still be driving their current path to completion. That hazard does not pass
   through the interrupt machinery at all and is independent of N2; `testAutoLayoutRace` covers
   part of this ground, but a dedicated look at forceful-restart overlap would be its own review
   item.
3. **`LayoutDiagramComponent.setAddress`** logical/raw split (`address` no longer doubled): I
   verified the parser floors `rawAddress / 2` and that `syncLayouts` treats `getAddress()` as
   logical, but did not trace every downstream consumer of `getAddress()` on the uncoupler path.
   The new `testLayoutTiles` suite presumably covers this; confirming that was out of scope here.

---

## Comparison against the 2026-07 review

*Appended after the findings above were frozen. Everything up to this line was written before
`2026-07-code-review.md` was read.*

### Headline: did the July fixes introduce errors?

**No surviving functional regression from the fix commits was found.** The two defects the fixes
did introduce - SR1 (`handleMisconfiguredPath` releasing edges never locked) and SR2 (the CAN
reader's `finally` closing a healthy replacement socket) - were caught by that review's own
pre-release self-review and corrected before release; the current code carries both corrections
(`path.subList(0, edgesLocked)`; the `finally` that closes nothing). This independent pass
specifically re-derived the safety of the highest-risk fixes (A3/SR1 lock accounting, A7/FR2
lock-edge release, B18 eviction vs. duplicated locomotives, C10 lock ordering, B7 monitor
wake-up protocol) and none is wrong in HEAD.

### Errors and gaps attributable to the July changes

**E1 - C19 was fixed in one of two identical sites; the sibling was left broken (my N1).**
The C19 fix null-guards `locDB.getById` in `receiveMessage`'s locomotive branch. The accessory
branch fifteen lines below has the identical check-then-act shape - `accDB.hasId(id)` followed by
*three* separate `accDB.getById(id)` calls - and was untouched, even though the same commit region
edited that very block (removing the thread wrapper around `repaintSwitch`). The failure mode is
the one C19's own writeup describes: an NPE swallowed by the executor's `Future`, dropping the
echo in silence. Reachable when an accessory is deleted or re-registered (startup invalid-address
cleanup, or `RemoteDeviceCollection.add`'s eviction during a type change) concurrently with a CS
echo - narrow, but the fix pattern was already established and simply not applied to the sibling.
This is the one concrete *error of incompleteness* this comparison found.

> **E1/E2/E3 - all resolved 2026-07-26.** E1 is finding N1, fixed (see its entry). E2's stale
> residual and E3's version labels were corrected in `2026-07-code-review.md` in the same fix
> cycle (2.7.5/2.7.6 were staged then cancelled; everything shipped as 2.8.0) - both verified in
> the evaluation pass below.

**E2 - one residual item in the July document went stale after being fixed (documentation error).**
"Residual items, not changed" #3 states `parseRoutesCS3` "leaks `routeBR` and `magBR` if
`isCS3Version260OrAbove()` throws between opening them and the branch. Pre-existing." The current
code probes the version *before* opening any reader and opens all three in one
try-with-resources - the leak is fixed, and the in-code comment says the reordering was done
precisely so "a failed probe cannot strand a reader." The review document was never updated, so it
now understates the shipped code - exactly the "one status, one location" failure its own README
warns about. (Residuals #4 and #5 in the same list *were* superseded further down the document;
only #3 was left dangling.)

**E3 - minor version-label drift in the July document.** The header says "reviewed against 2.7.5;
the fixes landed in 2.8.0," but a later section is titled "Final pre-release pass (v2.8.0)."
`RAW_VERSION` in HEAD is 2.8.0 and no 2.7.6 tag exists (`v2_7_4c` is the last pre-review tag).
Cosmetic, but the README's "say what version was reviewed" rule exists for this.

### Where the two reviews agree (independently)

- **Sticky confirmation** - my B3 restates July's B7 resolution note (echo proves acknowledgement,
  not physical position) plus one nuance neither of us can fix at this layer: after any echo, a
  later command to the last-confirmed state passes validation without waiting for its own echo.
- **`unlockPath` shared-lock-edge limitation** - my B4 is July's FR2/residual #1, independently
  re-derived: the protection genuinely comes from the crossing edge being part of any conflicting
  path, and `isPathClear` really does not inspect lock edges.
- **`triggerType` default flip** - my B2 is July's C13. The fix is right; my addition is the
  release-note angle: a *hand-written* autonomy JSON omitting the key flips trigger edge on
  upgrade (TrainControl's own exports always carry the key, so only hand-written files are
  exposed).
- **`Edge.isOccupied` equivalence, B18-vs-duplicated-locomotives, `addressFromUID` base ordering,
  stats date-key identity, bundle hygiene** - both reviews verified these independently and agree.

### Found here, not in the July review

- **N2** - `validatePathActuation` returns "validated" on *any* interrupt, not only autonomy
  shutdown. A trap for the next interrupt source, in the safety feature the July cycle built.
- **N3** - `moveLocomotive` NPE on an unknown target point (preexisting, unreachable from current
  callers).
- **N4** - `getImageCache()` unsynchronized lazy init (preexisting pattern; consequence is a few
  redundant decodes).
- **B1's open question** - whether autonomy-without-CS was ever a functional 2.7.2 workflow, which
  decides whether path validation's ON-default can strand an upgrading user. July's A8 writeup
  shows awareness that a dead listener makes validation fail every path, but the
  no-connection-at-all case is not addressed anywhere.

---

## Recommended further review areas (2026-07-26)

After the diff review closed, seven areas of the *original* codebase were identified as carrying
the highest risk of latent defects - places where small changes have historically had major side
effects. Recorded here so the list survives this session; areas 1, 2 and 7 received deep dives
(next section) at the author's request.

| # | Area | Status |
|---|------|--------|
| 1 | `executePath` core sequencing + forceful-restart / stale-thread fencing | **Deep dive done** - findings D1-D4 |
| 2 | Multi-unit and linked-locomotive command fan-out in `MarklinLocomotive` | **Deep dive done** - findings M1-M4 |
| 3 | `LocDB.data` serialization/migration (`MarklinSimpleComponent`, `CustomObjectInputStream`); B6 phantom migration still open; recommend a round-trip matrix against real 2.6.x/2.7.x data files | Recommended, not yet reviewed |
| 4 | Concurrent reads of the Layout graph: `points`/`edges`/`adjacency` are plain `HashMap`s read by route-monitor threads (`Route.evaluate`), the GraphStream viewer and loco threads while occupancy mutates | Recommended, not yet reviewed |
| 5 | `syncWithCS2` merge semantics against a live database (renames + address changes + type changes composing with `RemoteDeviceCollection.add` eviction); review before the roadmap EDT split touches it | Recommended, not yet reviewed |
| 6 | Interrupt hygiene: the re-asserted-interrupt pattern turns every `while (...) { delay() }` loop into a hot spin the day an interrupt source appears; needs one deliberate policy decision | Recommended, not yet reviewed |
| 7 | Timetable subsystem (`TimetablePath`, capture/replay, `executeTimetable`) | **Deep dive done** - findings T1-T4 |

---

## Deep dives (2026-07-26): areas 1, 2 and 7

**Scope:** original-code correctness, not diff regressions. Version: same HEAD (`9c9899b`).
This table is the authoritative status for findings D*, M*, T*.

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| D1 | Fenced mid-path abort in `executePath` never stops the locomotive | High (given D2) | **Fixed 2026-07-26** |
| D2 | JSON reload (`validateButton`/`loadJSON`) has no `isRunning()` guard, unlike every sibling operation | Medium - the enabler for D1 | **Fixed 2026-07-26** - warns and stops rather than refusing, see note |
| D3 | Layout-version fence compares against the global counter, captured after `configureAndLockPath` | Low-Medium | **Deferred 2026-07-26** - the D2 fix stops locomotives before the swap, making the fence non-load-bearing, see note |
| D4 | `CB_*` callbacks on shared `Locomotive` objects are re-registered by the new layout mid-flight | Informational | Recorded |
| D5 | `executePath` can strand an `activeLocomotives` entry, making `isRunning()` permanently true | Low | **Open** - deferred to next cycle; no longer costs a restart via the reload path. Escape inventory corrected in the third evaluation pass: the delete route recorded in the note is unreachable |
| M1 | Linked-locomotive speed fan-out silently desyncs above the 100-speed threshold | Medium | **Fixed 2026-07-26** |
| M2 | One-sided link validation permits chains; saved chains restore order-dependently | None | **Withdrawn 2026-07-26 - the finding is wrong.** The membership check exists, in the UI layer; chains are unreachable |
| M3 | Direction re-assert after power cycle only covers locomotives that were moving at power-off | Low | **Closed - accepted as-is** (author, 2026-07-26) |
| M4 | Deleting a locomotive never unlinks it from consists that reference it | Medium | **Fixed 2026-07-26** - caused one regression, see note |
| T1 | Timetable capture stores each gap on the earlier entry; replay/UI read it as the entry's own pre-delay | Medium | **Fixed 2026-07-26** - migration risk was overstated, see note |
| T2 | `getUnfinishedTimetablePathIndex` overloads 0 as both "first entry" and "none unfinished" | Low | **Fixed 2026-07-26** - stated trigger corrected, see note |
| T3 | Timetable entry that can never execute retries forever with log spam | Low | **Closed - accepted as-is** (author, 2026-07-26); the invalidation trigger is effectively unreachable, see note |
| T4 | `executeTimetable` returns when the last entry is dispatched, not completed | Informational | Recorded |
| D6 | Rename re-keys a locomotive out of the Layout's hash-keyed collections (`excludedLocs`, `locomotivesToRun`) - sibling of integration-review A1, which repaired consists only | Medium | **Fixed 2026-07-26** (trigger A). Trigger B **withdrawn - wrong**; see note |

### Area 1 - `executePath` and forceful-restart fencing

The fencing mechanism exists and is more deliberate than expected: `executePath` captures
`Layout.layoutVersion` (a static counter each `Layout` constructor increments) after locking the
path, and re-checks it before every speed adjustment, s88 wait, reversing move, early unlock, and
at the end of every milestone iteration ([Layout.java:2585](src/org/traincontrol/automation/Layout.java:2585)),
returning early when a newer layout exists. `parseAuto` retires the old instance with
`invalidate()` (blocks new paths) + `stopLocomotives()` (ends the `runLocomotive` loops). Three
gaps remain:

**D1 - the fenced abort leaves the locomotive at speed.** The abort path at
[Layout.java:2585](src/org/traincontrol/automation/Layout.java:2585) just `return true` - no
`setSpeed(0)`. For an intermediate milestone, the last speed command before the fence was cruising
speed (set at 2414 or 2443). Concrete sequence: locomotive is between stations, thread parked in
`waitForOccupiedFeedback`; the user reloads the autonomy JSON; the version bumps; the sensor fires;
the thread falls through the (now-fenced-off) reversing and unlock blocks and returns - with the
physical locomotive still running and **nothing left that will ever stop it**. The new layout
knows nothing about it; new autonomy can then command conflicting switch positions in its path.
The one abort branch that *does* stop the locomotive is a version change during the final
destination wait (the `setSpeed(0)` at 2576 precedes the check). The conservative fix is
`loc.setSpeed(0)` in the fenced-abort branch - the locomotive is at a known milestone point at
that moment, so stopping there is exactly what the old graceful-stop semantics promised.

**D2 - the reload itself is unguarded.** Timetable capture, timetable execute, `moveLocomotive`
and at least eight other flows check `isRunning()` and refuse with "wait for active locomotives to
stop." `validateButtonActionPerformed` (TrainControlUI.java:12650) and
`loadJSONButtonActionPerformed` (13000) - the operations that actually re-create the layout - have
no such check; the only confirmation is about unsaved graph edits. Adding the same guard makes D1
unreachable through the UI and is the cheapest fix in this report.

**D3 - the fence measures the wrong thing, in a window the diff made wider.** The captured value
is the *global* counter read at [Layout.java:2407](src/org/traincontrol/automation/Layout.java:2407) -
*after* `configureAndLockPath`, which since path-integrity validation can block for seconds. A
reload landing in that window means the capture already equals the new version, the fence never
trips, and the thread drives the **entire** old path against the retired graph. A `Layout` also
has no instance version field, so the fence detects "some layout was created since my capture,"
not "my layout is stale." Storing `this.version = ++layoutVersion` at construction and comparing
`this.version == Layout.layoutVersion` closes both.

**D4 (informational)** - `CB_ROUTE_START`/`CB_PRE_ARRIVAL`/`CB_ROUTE_END` are stored on the shared
`Locomotive` objects, and the new layout's `fromJSON` re-registers them; an old path that passes
its final fence check can fire the *new* JSON's arrival lambdas. Harmless today (they toggle
functions), worth knowing.

*Coverage note:* `testAutoLayoutRace` covers only the `activeLocomotives` map race - none of the
above. A simulate-mode soak that reloads JSON mid-path and asserts every locomotive's speed is 0
within a bounded time would pin D1/D2/D3 directly.

**D1 and D2 fixed (2026-07-26); D3 deferred.** Both claims were verified against the code before
being acted on, and both hold.

D1's premise - that nothing else would stop the locomotive - is the load-bearing part, and it is
correct: `stopLocomotives()` is `this.running = false` and nothing more. It ends the dispatch loop
between paths and never commands a locomotive, so a train abandoned mid-path by the fence really did
have nothing left to stop it. The fenced branch now calls `loc.setSpeed(0)` before returning.

*Not addressed in that branch:* the early `return` also skips `unlockPath`, the `activeLocomotives`
and `locomotiveMilestones` removals, and `CB_ROUTE_END`. The lock and map state is moot - that Layout
is being discarded - but a function switched on by `CB_ROUTE_START` (lights, sound) stays on with no
route left to turn it off. Firing `CB_ROUTE_END` here was deliberately not added: per D4 the callback
registered on the shared `Locomotive` may by then belong to the *new* JSON, so invoking it would run a
lambda from a layout this path never ran on.

D2's stronger claim - that guarding the UI makes D1 unreachable - was checked at the call-graph level
rather than taken at face value, since that is exactly the step M2 skipped. It survives:
`parseAuto` has one production caller, `TrainControlUI.validateButtonActionPerformed`, and the only
other `new Layout(...)` outside `fromJSON` is the lazy initialiser in `getAutoLayout()`, which cannot
fire while a layout exists. Guarding that one method closes every path that retires a running layout.

The guard uses `isRunning()` (`running || !activeLocomotives.isEmpty()`) rather than `isAutoRunning()`,
so it also covers a graceful stop that is still in progress with locomotives finishing paths.

**Revised the same day, after D5.** The guard was first written as an outright refusal, and that was
wrong: `isRunning()` is also true in the stranded state D5 describes, so refusing would have removed the
only in-session way out of it. It now *warns* - a Yes/No dialog defaulting to No - and, on
confirmation, calls `stopLocomotives()` and then sets every active locomotive to speed 0 before
`parseAuto` runs.

Stopping the trains first is what makes the confirmation safe, and it is the more important half of the
fix. The danger in reloading was never the reload; it was that retiring a `Layout` commands nothing, so
trains kept moving while their graph was replaced. With them stopped up front, the outcome no longer
depends on whether any given path's fence gets a chance to fire.

The guard sits only in `validateButtonActionPerformed` - the one place that retires a layout.
`loadJSONButtonActionPerformed` delegates there, so a second prompt in it would only ask twice.

One detail sharper than the finding states: the existing confirmation dialog only appears when the
editor text differs from the live layout's `toJSON()`. Pressing Validate without having edited
anything recreated the layout with no prompt at all.

**D3 deferred, on the strength of D2 rather than on probability.** The window is real and this release
widened it - path-integrity validation waits on `Accessory.actuationConfirmedMonitor` for up to
`PATH_VALIDATION_MS * (1 + accessories.size())`, so roughly four seconds on a three-accessory path
where previously there was almost none. A reload landing inside `configureAndLockPath` still makes the
capture at Layout.java:2426 equal to the new version, so the fence never trips and the locomotive
drives the whole path against a retired graph.

**D5 (new, raised by the author on review of the D2 fix): the guard removes the only in-session
recovery from a stuck autonomy state.** Confirmed, and it is a real cost of the D2 fix rather than a
hypothetical one.

`isRunning()` is `running || !activeLocomotives.isEmpty()`. The `running` half always clears - graceful
stop sets it false. The `activeLocomotives` half is emptied in exactly two places: the tail of
`executePath` (Layout.java:2686) and `locDeleted` (Layout.java:322). There is **no** `try`/`catch`/
`finally` anywhere in `executePath` between the `put` at 2400 and that removal, the thread body in
`runLocomotive` does not catch either, and the codebase installs no `UncaughtExceptionHandler`. So an
unexpected exception part-way through a path kills the thread silently and strands the entry, and
`isRunning()` then reports true for the remainder of the session.

Before the D2 fix that state already blocked `startAutonomy` (which has always used the same
predicate) and `setSimulate`, but reloading the JSON still worked and produced a clean `Layout` -
which is precisely the escape hatch D2 closed.

**Corrected in the third evaluation pass.** This note originally listed two ways out: reloading the
graph, or deleting the stranded locomotive. The second does not work. `deleteLoc` is gated on
`isAutonomyRunning()` (TrainControlUI.java:10938), which is the strong predicate - the very thing the
stranded state holds permanently true - and `Layout.locDeleted` has no other caller. The delete is
refused with "cannot edit while running". **Reloading the graph is the only in-session recovery**,
which strengthens the warn-not-refuse decision in D2 retroactively: had that guard stayed an outright
refusal, the stuck state would have had no door at all short of restarting.

So the D2 guard is only as sound as `executePath`'s freedom from unhandled exceptions, and nothing
structurally guarantees that. Three ways out, in increasing order of correctness:

1. Make the refusal a confirmation instead - the run is abandoned rather than blocked. Cheapest, and
   restores the escape hatch, but it puts D3's trigger back: a confirmed reload can still land inside
   `configureAndLockPath`'s validation wait, where the fence never trips at all.
2. Keep the refusal and add an explicit recovery action that clears `activeLocomotives`.
3. Guarantee the map entry cannot leak, by cleaning it up when `executePath` unwinds abnormally.
   This removes the stuck state rather than working around it, and makes both the D2 guard and the
   existing `startAutonomy` guard sound. Note it must not disturb the fenced abort, which returns
   *deliberately* without unlocking or clearing - `testLayoutReloadFence` pins that distinction.

**Option 1 was taken** (see the D2 note), which is why D5 is Low rather than Medium: a stranded entry
is still a defect - it makes `isRunning()` permanently true, which continues to block `startAutonomy`
and `setSimulate` - but it no longer costs the user a restart, because reloading the graph is available
and rebuilds the `Layout` from scratch.

Option 3 remains the right fix and was not applied: it restructures the error handling of the most
safety-critical method in the application, which is not something to bundle into a release already
carrying this many changes. **Recorded as the open item for the next cycle**, with three constraints
established while evaluating it, each of which is a distinct regression if missed:

- It must `catch`, not `finally`. A `finally` also fires on the fenced abort's `return true`, which
  deliberately leaves the entry in place; `testLayoutReloadFence` pins that distinction.
- It must rethrow. `executeTimetable` already catches `Exception` around `executePath` and responds by
  calling `stopLocomotives()`. Swallowing the exception and returning false instead would leave the
  retry loop `while (running && !executePath(...))` spinning on a permanent fault - turning
  halt-on-error into retry-forever, which is T3's shape.
- It must not unlock the path. Unlocking on error marks edges free while a train may be physically
  standing on them, so another route could be sent into occupied track. Leaving them locked is degraded
  but fail-safe, and a reload resets the locks anyway.
It is deferred because the D2 fix makes the fence non-load-bearing on this path. The reload now stops
every active locomotive *before* creating the new `Layout`, so whether the fence trips - and D3 is
precisely the case where it never does - no longer determines whether a train is left running.

Note this rationale changed once already. The first version of the D2 fix refused the reload outright,
and D3 was deferred on the grounds that its trigger had been removed entirely; when the refusal became
a warning, that reasoning expired. The current reasoning does not depend on the trigger being
unreachable, only on the trains being stopped before the swap.

The correct fix remains an instance version field compared against the counter. It changes the meaning
of the fence at five sites and wants its own tests, which is why it is not bundled into this release.

### Area 2 - multi-unit / linked-locomotive fan-out

**M1 - speed fan-out silently desyncs the consist above a threshold.** Link multipliers up to
|2.0| are accepted ([MarklinLocomotive.java:1059](src/org/traincontrol/marklin/MarklinLocomotive.java:1059)).
The fan-out computes `ceil(speed * multiplier)` for multipliers > 1
([MarklinLocomotive.java:707-724](src/org/traincontrol/marklin/MarklinLocomotive.java:707)) with no
clamp, and `Locomotive._setSpeed` **silently ignores** values outside 0-100 rather than clamping
([Locomotive.java:413](src/org/traincontrol/base/Locomotive.java:413)). The linked locomotive's
`setSpeed` then transmits `this.getSpeed() * 10` - the *stale previous* speed. Net effect, verified
end-to-end: with a 1.5x member, main speeds up to 66 track correctly; from 67 the member freezes at
its last speed while the head accelerates, and the two engines of one physical consist fight each
other. It works in every casual test and breaks only at high speed. Fix: clamp the scaled speed to
100 in the fan-out (or make `_setSpeed` clamp instead of ignore).

**Fixed (2026-07-26).** Clamped in the fan-out rather than in `_setSpeed`, because making the latter clamp would change behaviour for every caller and some may rely on an out-of-range value being ignored. Arithmetic confirmed exactly as stated: at 1.5x, speed 66 yields 99 and 67 yields 101.

**M2 - membership is invisible to link validation.** `canBeLinkedTo`
([MarklinLocomotive.java:1107](src/org/traincontrol/marklin/MarklinLocomotive.java:1107)) rejects a
*head* being added as a member (`other.hasLinkedLocomotives()`), but nothing records or checks that
the *candidate head* is already someone's member - membership has no back-reference at all. So: A
links B (fine), then B links C - accepted by both the base validation and the linking dialog, which
filters with the same `canBeLinkedTo` (TrainControlUI.java:10323). Result is a chain A->B->C with
compounded multipliers and nested monitor acquisition. Direct cycles are blocked (a head can never
become a member), so no deadlock - but on reload, `setLinkedLocomotives` resolves links in `locDB`
iteration order (a `HashMap` - effectively arbitrary): if B's links resolve before A's, A->B is
rejected ("B has linked locomotives") and **silently dropped from a configuration that worked
before the restart**. Either forbid members becoming heads (needs a membership check spanning the
DB) or make restore order-insensitive.

**Withdrawn (2026-07-26) - the finding is wrong.** The membership check this asks for already exists. `TrainControlUI.changeLinkedLocomotives` refuses at its sixth line if `MarklinControlStation.isLocLinkedToOthers(l)` returns non-null - that method sweeps every locomotive asking `other.isLinkedTo(l)`, which is precisely the back-reference the finding says does not exist. It is computed rather than stored, which is presumably why looking in `MarklinLocomotive` did not find it.

So B cannot acquire C while A holds B: both entry points to the dialog (the right-click menu and Ctrl+L) go through that method. A->B->C is unreachable, the rule is enforced in both directions, and with no chains creatable there are no saved chains for the restore-order half to mishandle.

**M4 - deleting a locomotive does not unlink it.** `MarklinControlStation.deleteLoc`
([MarklinControlStation.java:2245](src/org/traincontrol/marklin/MarklinControlStation.java:2245))
removes the DB entry and rebuilds the id cache; the UI wrapper additionally clears button mappings.
Neither touches other locomotives' `linkedLocomotives`, which hold **object references**. A consist
head keeps fanning every speed/direction/function command to the deleted locomotive's decoder - a
locomotive the UI no longer shows anywhere - until restart, at which point the dangling name fails
to resolve in `setLinkedLocomotives` and the link vanishes with only a log line. Fix: on delete,
sweep `getLocomotives()` for consists referencing the victim and unlink (mirroring what
`renameLoc` already does for routes).

**Fixed (2026-07-26), and it caused a regression that `testLocomotive` caught.** `changeLocAddress` used `deleteLoc` as a *re-key* primitive - delete, change the address, re-add - so the new sweep unlinked the locomotive from its consist on every address change, and the revalidation loop at the end of that method could not restore it because the name map had already lost the entry. `changeLocAddress` now calls `locDB.delete` directly, which is exactly what `renameLoc` was already doing for this reason. Every other caller of `deleteLoc` is a genuine user-initiated delete.

Worth recording as a pattern: a method used both as a public operation and as an internal primitive silently acquires the operation's side effects. An existing test was the only thing between that and a shipped defect.

**Follow-up (2026-07-26), found by an integration pass.** The sweep was correct about the data and wrong about the concurrency: it mutated `linkedLocomotives` - a plain `LinkedHashMap` - directly, from the delete flow, while `setSpeed` and `setDirection` iterate that same map under the locomotive's own lock. Deleting a member while its consist was being driven could therefore have thrown `ConcurrentModificationException` part-way through a fan-out, leaving some members commanded and others not. The unlink now goes through `MarklinLocomotive.unlinkLocomotive`, which is `synchronized` on that same lock.

Pre-existing and deliberately left alone: `setLinkedLocomotives` and `preSetLinkedLocomotives` mutate the same map unsynchronised, so editing a consist's links in the dialog while it runs carries the same risk. That is a separate call path and was not widened by this fix.

**M3 (question for the author)** - the C9 coupling (`lastStartTime == 0` → re-assert direction)
only zeroes the field in `notifyOfPowerStateChange`'s `speed > 0` branch. A locomotive **parked**
during a power cycle keeps its stale nonzero timestamp and never gets the direction re-assert -
which is the majority case, since most locomotives are stationary when power cycles. Whether this
matters depends on whether the Central Station's own state refresh makes TrainControl's re-assert
redundant; the author knows the hardware behavior here.

*Verified clean in this area:* rename is persistence-safe (saved link names are regenerated from
live objects at save time); direct link cycles are impossible; CS-defined multi-units cannot be
linked or link; `changeLocAddress` refuses turning a linked locomotive into a multi-unit; `stop()`
sends the member a redundant but harmless second speed-0.

### Area 7 - timetable subsystem

**T1 - capture and replay disagree about which entry owns a gap.** Capture stores the interval
between entry k and entry k+1 on **entry k**
([Layout.java:221](src/org/traincontrol/automation/Layout.java:221):
`first.setSecondsToNext(second.executionTime - first.executionTime)`). Replay waits on **the
current entry's own** value ([Layout.java:2197](src/org/traincontrol/automation/Layout.java:2197)),
and both the edit dialog ("delay before route executes", TrainControlUI.java:14122) and the table
display ("Pending Start +Xs", 14916) use that same delay-before-me reading. A captured timetable
therefore replays with every gap shifted one entry earlier than it was recorded: the delay observed
before entry k+1 is the *captured* gap between k+1 and k+2, the first captured gap is never applied,
and the final entry always starts with zero delay. Invisible when gaps are even; wrong pacing when
they are not. The one-line fix is storing the gap on `second` at capture - but that flips the
meaning of every already-saved timetable, so it needs a decision about which semantic is intended
(the UI dialog's wording suggests delay-before-me is the intended one, making **capture** the
defective side).

**Fixed (2026-07-26) - and the migration risk was overstated.** The field had two writers that disagreed: capture wrote *delay-after-me*, while the edit dialog (`timetable.ui.enterDelaySecondsBeforeRouteExecutes`) and the replay loop both read *delay-before-me*. Capture was the outlier and now stores the gap on the later entry.

This does **not** flip the meaning of saved timetables: stored values are untouched, and any delay set by hand in the UI was already correct - which is why the feature looked right in use, and why only a purely auto-captured timetable replayed with shifted gaps. Entry 0 now correctly carries no preceding gap, and the final entry keeps its real one instead of always starting immediately.

**T2 - `getUnfinishedTimetablePathIndex` overloads 0.** It returns 0 both for "entry 0 is
unfinished" and "everything is finished"
([Layout.java:2113](src/org/traincontrol/automation/Layout.java:2113)), and
`timetableHasUnfinishedPaths()` is `!= 0`. Today this is safe purely because entries execute in
order, so entry 0 unfinished implies nothing later finished - but the UI allows deleting timetable
entries, and deleting the executed first entry from a partially-run timetable breaks the invariant:
the resume logic then sees "nothing unfinished," silently resets every timestamp, and replays from
the top. Return -1 for "none" (or use `noneMatch`) to make the sentinel honest.

**Fixed (2026-07-26) - the stated trigger does not work.** Deleting entries cannot break the invariant: finished entries form a prefix, deletion preserves that, so index 0 always legitimately means "no finished prefix" and restarting from the top is correct.

The reachable break is **parallel dispatch plus graceful stop**. Each entry runs on its own thread, so entry 1 can finish while entry 0 is still retrying; a stop then leaves `[unfinished, finished]`, and index 0 read as "nothing unfinished" wiped entry 1's completion. The sentinel now returns -1, with `Math.max(0, ...)` at the two sites that use it as a loop start. The fix stops the timestamp being destroyed; the resume loop still re-runs finished entries from `startIndex`, which is separate.

*Verification note (2026-07-26, evaluation pass).* A third caller not named above -
`restartTimetable`'s "already reset" guard (`getUnfinishedTimetablePathIndex() == 0 && ... &&
get(0).getExecutionTime() == 0`, TrainControlUI.java:14157) - was checked against the new sentinel
and remains correct: its third clause previously excluded the all-finished case that `== 0` used to
also mean, and under -1 that clause is merely redundant, not wrong. Also noted for the record: the
parallel-dispatch trigger described above has the same shape of problem as the withdrawn deletion
trigger - the dispatch gate at Layout.java:2204 blocks entry k+1 until entry k's `executionTime` is
set, and it is set at path *lock*, so `[unfinished, finished]` could not be observed through that
route either. The fix needs no trigger to justify it: the sentinel was dishonest, and
`restartTimetable` plus any future caller depend on it meaning one thing.

**T3 - a permanently unexecutable entry retries forever.** Each dispatched entry loops
`while (running && !executePath(...))` with a delay
([Layout.java:2222](src/org/traincontrol/automation/Layout.java:2222)). `executePath` returning
false for a *permanent* reason - most notably `!isValid()` after the layout is invalidated mid-run -
produces an infinite retry loop logging "configuration invalid, must reload" until the user notices
and presses graceful stop. Distinguishing permanent from transient refusals (or capping retries)
would let the timetable fail loudly instead.

*One detail to weigh when fixing: the retry delay is `loc.delay(getMinDelay(), getMaxDelay())`, and `Locomotive.delay(int,int)` multiplies by 1000 - those are seconds, and `setMinDelay` permits 0. A layout configured with zero delays turns this into a hot spin, and because the log suppresses a message identical to the previous one (C11, kept deliberately), it spins nearly silently.*

**Closed as accepted (2026-07-26).** The stated trigger is effectively unreachable. Of the ~45 `invalidate()` callers, almost all are in the JSON-loading region and run before anything moves; of the four that can fire during operation, three call `stopLocomotives()` immediately after, which ends the retry loop cleanly. The one that does not - `configureEdge`'s real pass, Layout.java:1115 - needs an accessory to vanish between `isPathClear`'s preview and the configure moments later inside the same synchronized block, and accessories are only deleted during startup restore.

A reachable trigger the finding did not mention: `executePath` also refuses when the locomotive is not at the path's start point, which never self-resolves without a human. Accepted regardless, since the loop honours a graceful stop - which `testLayoutTimetable` now pins.

**T4 (informational)** - `executeTimetable` returns once the *last* entry is dispatched, so the UI
re-enables "Start Autonomy"/"Execute Timetable" while the final path is still running. Every
downstream flow re-checks `isRunning()`, so this is a cosmetic button-state quirk, not a conflict.

*Verified clean in this area:* the whole execution runs on a real background thread (the
`invokeLater(new Thread(...))` wrapper only covers the pre-checks); `secondsToNext` is
milliseconds despite the name, but consistently ms at capture, edit (x1000), display (/1000) and
comparison - a naming trap, not a defect; parallel entry threads plus the same-locomotive retry
loop compose correctly; stop-on-exception in any entry thread ends the whole run.

---

## Evaluation pass (2026-07-26): verification of the fix batch

Independent re-verification of the fixes for N1, N3, N4, M1, M4, T1, T2 (commits
`43eb65f..7fc8fc2` plus the then-uncommitted M4 concurrency follow-up), conducted after the author
updated this document. Statuses live in the tables above; this section records what was checked.

### Fixes verified against the code

- **M1** - clamp confirmed in the fan-out at the exact arithmetic claimed (1.5x member: speed 66
  scales to 99, 67 to 101, previously ignored by `_setSpeed`). Clamping at the fan-out rather than
  in `_setSpeed` is right: `_setSpeed` has other callers whose ignore-out-of-range behaviour is
  load-bearing.
- **M4** - the unlink sweep is present, keyed off the object captured before the delete, and the
  new `loc.unlinkedDeletedLocomotive` key exists in all eight bundles. The `changeLocAddress`
  regression (delete-as-re-key acquiring the unlink side effect) is resolved by calling
  `locDB.delete` directly, matching `renameLoc`. The concurrency follow-up routes the removal
  through `MarklinLocomotive.unlinkLocomotive`, `synchronized` on the same monitor the
  `setSpeed`/`setDirection` fan-outs hold - verified against both the method and its caller.
- **T1** - capture now writes the gap onto the later entry; entry 0 carries none. Pinned by
  `testLayoutTimetable.testCaptureStoresEachGapOnTheLaterEntry` and
  `testFirstCapturedEntryHasNoGap`.
- **T2** - sentinel is -1; both loop-start callers clamp. The third caller
  (`restartTimetable`, TrainControlUI.java:14157) was not named in the fix note - verified
  separately, remains correct (see the verification note under T2).
- **N1** - resolve-once + null-check in place, mirroring the locomotive branch; pinned by
  `testAccessory.testEchoForADeletedAccessoryIsIgnored`.
- **N3** - point resolved once, null-checked, reports `autolayout.errorPointDoesNotExist` - a key
  verified to pre-exist in all eight bundles.
- **N4** - eager `final` field, `synchronized` dropped from the getter. Behaviour-preserving.
- **Documents** - E2's stale residual and E3's version labels corrected in
  `2026-07-code-review.md`; the July post-change review is now tracked; `Readme.md` gained
  changelog entries for M1, M4, T1, T2.

**State at evaluation time:** the M4 concurrency follow-up (`unlinkLocomotive` in
`MarklinLocomotive.java` / `MarklinControlStation.java`), this document's follow-up notes, and the
new README lesson were present in the working tree but **not yet committed** - HEAD (`7fc8fc2`)
still carried the unsynchronised direct-map unlink. Flagged to the author.

### Reviewer-error tally for this report

Consolidated here for calibration; each is corrected in place at its finding:

| Finding | What this report got wrong |
|---|---|
| N4 | Claimed an init race on a getter that was `static synchronized` - the race could not occur |
| M2 | Withdrawn entirely: verified the dialog's candidate filter but not its entry gate; `isLocLinkedToOthers` is the membership check the finding said did not exist |
| N1 | Right defect, wrong reachability mechanism (type-flip eviction cannot empty `db`; the real route is the startup invalid-address delete) |
| T1 | Fix was right; the claimed migration risk was overstated - stored values are never reinterpreted |
| T2 | Fix was right; the claimed deletion trigger cannot occur (finished entries form a prefix that deletion preserves). The author's replacement trigger appears equally unreachable (the dispatch gate at Layout.java:2204); the fix stands on sentinel honesty, not on either trigger |
| B2 | Asked for a release note that already existed in `Readme.md` (added in `1d859ee`, before this report was written) - the changelog was never checked |

The pattern across N4, M2 and B2 is the same one `docs/reviews/README.md` warns about: a claim was
made about a layer (the getter's declaration, the dialog's entry point, the changelog) that was
never actually read. The deep-dive *defects* all held up; the errors clustered in reachability
stories and surrounding-machinery claims.

### Remaining open items

*(Stale as of the second evaluation pass below - the deep-dive status table is authoritative. At
the time this was written: D1-D3 open. Since then D1 and D2 were fixed, D3 deferred with recorded
reasoning, and D5/D6 opened.)*

---

## Second evaluation pass (2026-07-26): the D-fix / integration-review batch

Re-verification of commits `e772486..5fd1a54` (D1/D2 fixes, integration-review finding A1, tests),
conducted after the author's own verification notes were added to this document.

### Verified against the code

- **D1** - `loc.setSpeed(0)` in the fenced-abort branch, before the return; pinned by
  `testLayoutReloadFence.testFencedAbortStopsTheLocomotive`, with
  `testPathRunsToCompletionWhenNoLayoutReplacesIt` guarding the non-reload path. The deliberate
  omission of `CB_ROUTE_END`/`unlockPath` in that branch is correctly reasoned (D4: the callback
  may belong to the new JSON by then).
- **D2** - warn-and-stop guard in `validateButtonActionPerformed`; stop ordering (dispatch flag
  first, then speed-0 sweep) is right, and the residual race - a locomotive thread already past
  its `while(running)` check dispatching one more path after the sweep - is closed by the D1 fix
  at that path's first milestone. The "one production caller of `parseAuto`" claim was
  re-verified independently: confirmed (TrainControlUI.java:12744 only, plus the lazy
  `getAutoLayout()` initialiser which cannot fire while a layout exists). `loadJSONButton`
  inherits the guard by delegation, as stated.
- **Integration A1 (rename vs. consists)** - `rehashLinkedLocomotives` is correct (copy
  re-buckets under current hashes), `synchronized` on the fan-out lock, swept across all
  locomotives in `renameLoc`; the rehash-not-rebuild rationale (a rebuild would put a direction
  command on the track per consist per rename) is verified against `setLinkedLocomotives`.
  Failing-test-first discipline confirmed by the test names in `testMultiUnitMembership`.
- **D5** - the strand analysis (no catch between `activeLocomotives.put` and its removal, no
  `UncaughtExceptionHandler`) matches the code; the three constraints recorded for the next-cycle
  option-3 fix are all genuine regression risks.
- **Bundles** - `confirmReloadJsonStopsRunningLocomotives` present in all eight; changelog gained
  three user-facing entries (rename/MU, reload warn-and-stop, mid-route stop on reload).

### D6 (new) - rename re-keys a locomotive out of the Layout's hash-keyed collections (Medium)

The integration review's A1 established the mechanism - `MarklinLocomotive.hashCode` is built from
mutable fields, so an in-place rename strands the object in any hash container holding it - and
repaired **consists**. The `Layout` holds the same object as a hash key in containers A1's sweep
does not touch, and the rename guard does not prevent the overlap:

- The rename/address dialog is gated on `isAutonomyRunning()`
  ([TrainControlUI.java:10093](src/org/traincontrol/gui/TrainControlUI.java:10093)) - the
  `running` flag alone - while the D2 reload guard deliberately uses the stronger `isRunning()`.
  Renames are therefore permitted with a graph loaded but idle, and during a graceful stop whose
  locomotives are still finishing paths.
- **Trigger A - idle rename loses exclusions.** `Point.excludedLocs` is a `HashSet<Locomotive>`
  populated with object references at JSON load ([Layout.java:3475](src/org/traincontrol/automation/Layout.java:3475));
  `isPathClear` (952) and `pickPath` (1931) probe it with `contains(loc)`. Rename an excluded
  locomotive while the graph is idle, start autonomy: the exclusion silently stops applying and
  the locomotive can be routed into stations it was excluded from. `locomotivesToRun`
  (`ConcurrentHashMap` key set, populated at load) has the same exposure: `contains` at
  moveLocomotive can duplicate-add, and `locDeleted`'s `remove` misses.
- ~~**Trigger B - rename during a graceful stop manufactures D5's stuck state.**~~ **Withdrawn
  2026-07-26 - the finding is wrong.** Its premise, that the rename guard is weaker than D2's,
  does not hold. `MarklinControlStation.isAutonomyRunning` is not the `running` flag:

  ```java
  public boolean isAutonomyRunning()
  {
      return this.hasAutoLayout() && this.getAutoLayout().isRunning();
  }
  ```

  It delegates to `Layout.isRunning()` - `running || !activeLocomotives.isEmpty()` - the *same*
  predicate D2 uses, not `isAutoRunning()`. Both rename entry points carry that guard
  (TrainControlUI.java:10093 and :13873), so a rename during a graceful stop with paths still
  finishing is refused: `activeLocomotives` is non-empty, so the guard fires. `activeLocomotives`
  and `locomotiveMilestones` cannot be stranded this way, and D5's escape hatch is not defeated.
- ~~Adjacent, pre-existing: after any rename the autonomy JSON text still carries the old name, so
  a subsequent reload silently drops the locomotive from the graph.~~ **Overstated - corrected
  2026-07-26.** `Point.toJSON` builds `excludedLocs` by *iterating* the set and reading
  `l.getName()` on each live object, and iteration is immune to hash drift, so regenerated and
  exported JSON always carry the current name. The stale name exists only in the editor pane if it
  has not been re-validated since the rename - visible, and recoverable.
  `testLayoutRenameKeys.testExportUsesTheNewNameAfterARename` pins this.

**Trigger A fixed (2026-07-26).** `Layout.rehashLocomotiveKeys` rebuilds `locomotivesToRun` and,
through `Point.rehashExcludedLocs`, every point's exclusion set. `renameLoc` calls it when the model
has an auto layout, alongside the consist sweep A1 added. Same shape as `rehashLinkedLocomotives`.

`activeLocomotives` and `locomotiveMilestones` are deliberately *not* re-keyed, and the withdrawal of
trigger B is what makes that safe: renaming is refused while `isRunning()` is true, so both are
necessarily empty whenever the re-key can run. Re-keying a live map would have been the racy choice,
not the thorough one.

Blocking renames outright was considered first, at the author's suggestion, and does not work: the
reachable trigger is the *idle* case - graph loaded, nothing moving - where renaming is permitted and
correct to permit. Blocking it would have meant refusing renames whenever a graph exists at all,
which is a real usability cost to avoid a bookkeeping bug with a 40-line fix.

Tests: [`test/testLayoutRenameKeys.java`](../../test/testLayoutRenameKeys.java).

### Reviewer-error tally, addendum

| Finding | What this report got wrong |
|---|---|
| Area 2 "verified clean" list | "Rename is persistence-safe" was true but gave rename a clean bill while its in-memory keying was broken - the integration review's A1, which this report's area-2 deep dive missed despite reading `hashCode` adjacent code. The mutable-key hazard was found by the author writing test coverage, not by either review's reading |
| D6 trigger B | Asserted the rename guard was "the `running` flag alone" and built a stuck-state scenario on it. `isAutonomyRunning` delegates to `Layout.isRunning()` - the same predicate D2 uses. Inferred from the method *name* rather than read: the same error as M2, in the same document |
| D6 JSON bullet | Claimed a rename leaves the old name in exported JSON. `Point.toJSON` iterates rather than looks up, so export was never affected |

### State after this pass

Open: **D5** (Low, deferred to next cycle with constraints recorded), **D3** (deferred,
non-load-bearing while the D2 stop-before-swap holds), and the standing root cause below.
Everything else across all documents is fixed, withdrawn, closed by decision, or informational.

### Standing item: MarklinLocomotive is a mutable hash key - **FIXED 2026-07-26**

Six findings now trace to one cause, spread across three reviews, and every one was fixed where it
surfaced rather than at the root:

| Finding | Trigger | Container | Repaired by |
|---|---|---|---|
| M4 follow-up | delete | `linkedLocomotives` | `unlinkLocomotive` |
| A1 (integration) | rename | `linkedLocomotives` | `rehashLinkedLocomotives` |
| A1, amended | **address change** | as above, via `changeLocAddress` | the same call, wired in late |
| D6 trigger A | rename | `excludedLocs`, `locomotivesToRun` | `rehashLocomotiveKeys` |
| A2 (integration) | **Central Station sync** | all of the above | deferral + the same repairs |
| B1 (integration) | delete | `excludedLocs` | `removeExcludedLoc` |

`hashCode` is built from the name, address and decoder type, and `rename` and `setAddress` assign all
three in place. Every hash container holding a locomotive is one identity change away from silently
losing it.

What the count shows is not that the repairs were wrong but that the approach does not converge. Each
one was correct and each one was found by someone tripping over a *different* symptom: a consist that
stopped recognising a member, an exclusion that stopped applying, a name still in an export. Twice the
new repair itself was incomplete - wired into `renameLoc` but not `changeLocAddress`, and written with
a `removeIf` that cannot remove a drifted key. Two of the six were found only because a test asserted
its own precondition.

The removals have since been consolidated behind `Locomotive.removeFrom` and `Locomotive.removeKey`,
which document the hazard in one place. That helps a future author who reaches for them. It does
nothing for one who adds a new `HashMap<Locomotive, ...>` and never learns any of this exists.

**Fixed.** `MarklinLocomotive.equals` is now `this == other` and `hashCode` is
`System.identityHashCode(this)`. The hash is fixed for the object's lifetime, so no mutation can move a
locomotive out of a bucket, and all eight object-keyed collections are correct by construction - one of
them, `Layout.locomotivePendingS88`, having never been repaired by anything and found only by
enumerating them for this change.

*Keying by name, listed above as an alternative, is not actually a root fix and was struck.* A stored
name is a snapshot: rename the locomotive and the stored key is stale, which is the identical bug in a
different type, still needing every repair call.

*The blast radius was measured before the change, not after.* Every `.equals()` and `.contains()` on
locomotives in `src/` compares two live objects from the same database. Names are unique and enforced,
so two distinct live locomotives could never be value-equal in the first place - identity and the old
equality already agreed everywhere either was used. `RemoteDeviceCollection` keys by UID and name, so
the database never depended on `hashCode` at all. The single test asserting locomotive equality
re-fetches the same object and passes either way.

*No new method was needed.* The author's framing - `equals` should compare instances, with a separate
method for logical equivalence - turned out to describe what already existed:
`hasEquivalentAddress` for address and protocol, `getName` for the name, `getIntUID` for both. The old
`equals` was a conjunction of name AND address AND type, matching neither dedupe rule and doing no job
anything asked for.

It also removes a latent deserialization hazard: `linkedLocomotives` is a non-transient
`Map<Locomotive, Double>` on a `Serializable` class, and `HashMap.readObject` hashes each key while
that key may still be only partly restored. An identity hash does not depend on field state.

**Confirmed by a test failing.** `testDeletingFindsALocomotiveWhoseHashAlreadyDrifted` manufactured a
drifted hash by calling `setAddress` directly; after the change it could no longer establish that
precondition, because the state it needed had become unconstructible. It was inverted into
`testHashNeverMovesWhenAnIdentityFieldChanges`, which now guards the fix - reimplementing `hashCode`
from the fields is the obvious thing for a future author to do, and would silently reopen six defects
at once.

**Follow-up, deliberately not done.** All six repairs - `rehashLinkedLocomotives`,
`rehashLocomotiveKeys`, `rehashExcludedLocs`, `removeFrom`, `removeKey` and their call sites - are now
dead code. They were left in for one cycle: they are what keeps the application correct if this change
has to be reverted, and removing them in the same commit would have doubled the blast radius. The
behavioural tests in `testLayoutRenameKeys` and `testMultiUnitMembership` are what will prove the
removal safe, because they assert that an exclusion still applies after a rename rather than asserting
which mechanism achieves it.

---

## Third evaluation pass (2026-07-26): the D6/A2/B1 batch

Re-verification of commits `1f96472..44fd035` plus the uncommitted `removeFrom`/`removeKey`
consolidation in the working tree at evaluation time.

### The trigger-B withdrawal is confirmed - by reading the method this time

`MarklinControlStation.isAutonomyRunning()` was read directly
([MarklinControlStation.java:2235](src/org/traincontrol/marklin/MarklinControlStation.java:2235)):
it is `hasAutoLayout() && getAutoLayout().isRunning()` - the strong predicate, exactly as the
withdrawal states. The error is mine and is correctly recorded in the tally: the predicate was
inferred from the method's *name* (conflated with `Layout.isAutoRunning()`) rather than read -
the same failure as M2, in the same document, by the same reviewer. With that settled, every
premise the new code states was re-verified and holds: the rename/address dialog, `deleteLoc`,
the import-rename flow and the CS-sync deferral all sit behind the strong predicate, so
`activeLocomotives` and `locomotiveMilestones` are necessarily empty whenever any re-keying or
rebuilding operation can run - which is what makes `locDeleted`'s non-atomic rebuild safe and the
`executePath`-tail plain `remove()` correct.

### Verified against the code

- **D6 trigger A fix** - `rehashLocomotiveKeys` (locomotivesToRun + every point's exclusions),
  wired into `renameLoc`, `changeLocAddress`, *and* the CS-sync address-update path; pinned by
  `testLayoutRenameKeys` (rename, address change, and export-name cases).
- **Integration A2** (CS sync re-keys without repair) - the deferral while `isAutonomyRunning()`
  plus the same repairs on apply; new bundle key present in all eight files. A trigger neither of
  my passes had named: sync is automatic and had no guard at all.
- **Integration B1** (deleted locomotive stays excluded forever, and kept exporting) - fixed via
  `removeExcludedLoc` in `locDeleted`; pinned by `testDeletingALocomotiveClearsItsExclusion`.
- **The `removeIf` correction** - verified: `Collection.removeIf` delegates to
  `Iterator.remove()`, which the hash-based collections implement by recomputing the key's hash,
  so it cannot remove a drifted key. The first fix's comment described the API's shape, not its
  implementation; caught by `testDeletingFindsALocomotiveWhoseHashAlreadyDrifted`. The
  consolidation into `Locomotive.removeFrom`/`removeKey` (uncommitted at evaluation time)
  carries the right caveats, including non-atomicity.
- **My D6 JSON bullet correction** - verified: `Point.toJSON` iterates and reads live names, so
  export was never affected; only the un-revalidated editor pane can hold a stale name.

### D5 amendment: the recorded delete escape is unreachable

The D5 note lists two in-session escapes from the stranded state: reloading the graph, or
"deleting the stranded locomotive from the database, which reaches `locDeleted` and clears the
entry." The second cannot happen: `deleteLoc` is gated on `isAutonomyRunning()`
([TrainControlUI.java:10938](src/org/traincontrol/gui/TrainControlUI.java:10938)), which - being
the strong predicate - is precisely what the stranded state holds permanently true. The guard
refuses with "cannot edit while running," so **the D2 reload path is the only in-session
recovery**. This strengthens the warn-not-refuse decision retroactively, and slightly sharpens
the case for D5's option 2 or 3 next cycle: with one escape gone, the stuck state has exactly one
door, and it discards the run.

### State after this pass

Open: **D5** (Low, deferred; escape inventory corrected above) and **D3** (deferred,
non-load-bearing while stop-before-swap holds). The **standing root-cause item is now fixed** - see
its section, updated - leaving one follow-up: deleting the six repairs it made redundant, next cycle.
Everything else across all four review documents is fixed, withdrawn, closed by decision, or
informational.
