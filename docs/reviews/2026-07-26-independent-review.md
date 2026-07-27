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
| N4 | `getImageCache()` lazy init is unsynchronized | Very low | Preexisting pattern | Open |
| B1 | `PATH_INTEGRITY_VALIDATION` defaults ON: autonomy without CS echoes stalls every path | Behavior change (deliberate) | Author confirmed offline autonomy requires debug + simulate, and simulate skips validation | **Closed - not an issue** |
| B2 | Autonomy-JSON routes omitting `triggerType` now fire on the opposite sensor edge | Behavior change (deliberate fix) | Affects hand-written JSON only | Open - release-note worthy |
| B3 | Sticky `actuationConfirmed`: a repeat command to the last-confirmed state passes validation instantly | Design property, not a defect | Informational | Recorded |
| B4 | `unlockPath` lock-edge release is unsafe for hand-edited configs where two edges share a lock edge without traversing it | Design limitation, documented in code | Informational | Recorded |

No high-severity regressions were found. Every load-bearing claim below was verified against the
method that actually enforces it, per `docs/reviews/README.md`; the "verified equivalences" section
records the checks that came back clean, so the next reviewer does not redo them - and can see
which assumptions this review rests on.

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
puts into the loser are discarded. Consequence is a few redundant image decodes once per process
lifetime, nothing more. Eager initialization at the field would erase the question.

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
the fixes landed in 2.8.0," but a later section is titled "Final pre-release pass (v2.7.6)."
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
