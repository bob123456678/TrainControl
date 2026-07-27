# Independent regression review - v2.8.0 pre-release

**Version reviewed:** commit `5e80c41` ("Fix minor bugs", 2026-07-27), branch `master`, described as
`v2_7_4c-58-g5e80c41`, `RAW_VERSION` 2.8.0. **Reviewed:** 2026-07-27.
**No code was changed as part of this review, and no tests were run or added** - the author builds and
tests in NetBeans, so every claim rests on reading the enforcing method and tracing its callers, per
[README.md](README.md). Bundle claims were verified with a scripted audit of the `.properties` files
(key parity, duplicates, ASCII purity, and cross-referencing every key cited from `src/`), which reads
files only and builds nothing.

**Prefix for citing this document: `RR`.**

**Scope:** regressions from v2_7_2, with the focus on the v2_7_4..HEAD range (72 commits, ~2,700
inserted source lines excluding the five new translation bundles). The full diff of both ranges was
read hunk by hunk; every suspicious hunk was then verified against the current source, not the patch
text. The v2_7_2..v2_7_4 range (19 commits) was re-scanned separately - see D1.

**Independence caveat, for calibration:** unlike [the independent review](2026-07-26-independent-review.md),
this pass was conducted *after* reading all five July 2026 documents, so it is not blind. That cuts both
ways: no time was spent rediscovering closed findings, but a defect that every prior document also
missed is more likely to survive this pass too. The comparison section is integrated rather than
appended after a freeze.

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| RR-C1 | A Central Station sync that applies an address change rebuilds every consist through the unsynchronised `setLinkedLocomotives`, while `setSpeed` may be iterating the same map under the locomotive's lock | C | **Fixed 2026-07-27** - the rebuild is now staged and swapped under the locomotive's monitor |
| RR-C2 | `refreshLayouts` is serialised against itself, but `syncWithCS2` reaches the same clear-and-repopulate without the lock | C | **Fixed 2026-07-27** - the lock moved inside the shared method, so every entrance inherits it |
| RR-C3 | The Danish and German path-validation dialogs tell the user to disable an option under a name that does not match the menu item | C | **Fixed 2026-07-27** |
| RR-C4 | The backup-complete dialog names the `tc_backup` folder even when the backup fell back to the working directory | C | **Fixed 2026-07-27** |
| RR-C5 | The S88 monitor's catch block reports any execution failure as "condition not satisfied" | C | **Fixed 2026-07-27** |
| RR-D1 | Checks that came back clean (see section) | - | Recorded |

**No A findings. No B findings.** No regression from v2_7_2 with wrong behaviour on the layout or data
loss was found, in either range. All five C items were introduced by this cycle's own changes; none is
reachable without a specific configuration, and none loses data. Per the README's changelog rule, none
of them warrants a changelog entry.

**All five were fixed on 2026-07-27**, after this report was written; the suite passes. The two
substantive ones - `RR-C1` and `RR-C2` - were defects in fixes made the previous day, and `RR-C2` is the
fifth instance of one recurring mistake, catalogued in
[the cycle summary](2026-07-cycle-summary.md).

`RR-C1`'s fix took the route this report recommends while respecting the `INT-D1` warning it cites:
validation stays outside the lock, and only the swap is inside. That needed a `canBeLinkedTo` overload
taking the member set to test against, because the address-conflict rule has to see the members staged
so far rather than the stale live map - getting that wrong would have silently changed which members a
consist accepts.

---

## C. Low

### RR-C1. A sync-applied address change rebuilds every consist without the fan-out lock

[MarklinControlStation.java:986](../../src/org/traincontrol/marklin/MarklinControlStation.java) (the
repair loop the `INT-A2` fix added to `syncWithCS2`),
[MarklinLocomotive.java:1063](../../src/org/traincontrol/marklin/MarklinLocomotive.java)
(`setLinkedLocomotives`: `linkedLocomotives.clear()` then re-`put`, not synchronized),
[MarklinLocomotive.java:706](../../src/org/traincontrol/marklin/MarklinLocomotive.java) and `:760`
(`setSpeed` / `setDirection`, both `synchronized`, both iterating that same map).

When `syncWithCS2` applies an address change reported by the Central Station, the `INT-A2` fix runs
the same consist revalidation `changeLocAddress` performs: for **every** locomotive with linked
locomotives, `preSetLinkedLocomotives(...)` then `setLinkedLocomotives()`. That rebuild clears and
repopulates `linkedLocomotives` - a plain `LinkedHashMap` - **without holding the locomotive's
monitor**, while `setSpeed` and `setDirection` iterate it under that monitor. A consist being driven
*manually* during such a sync can therefore hit a `ConcurrentModificationException` part-way through a
fan-out - some members commanded, others not, the physical hazard class the `IND-M4` follow-up fixed -
or, without the exception, a fan-out landing in the clear-to-put window that commands the head alone.
The `isAutonomyRunning()` deferral added by the same fix guards autonomy, not manual driving.

This is the one place a cycle fix *widened* a recorded exposure rather than narrowing it. `INT-D1`
records the decision to leave `setLinkedLocomotives` unsynchronised, but that decision was scoped to
the multi-unit **dialog** - a deliberate user action on a consist they are editing. The pre-existing
twin, `changeLocAddress`'s revalidation loop
([MarklinControlStation.java:2289](../../src/org/traincontrol/marklin/MarklinControlStation.java)), is
likewise behind a deliberate user action. The sync path is automatic, fires from a dozen flows, and
did not touch `linkedLocomotives` at all before this cycle - `INT-A2`'s own writeup says so.

*Reachability, honestly:* it needs a consist being actively driven outside autonomy, a sync running at
the same moment, and the Central Station reporting a changed address for a same-named, same-decoder
locomotive - a rare event. Rated C for that reason, though it is B-shaped by the letter of the
definition ("crashes in specific configurations"). Fix shape, if taken: route the rebuild's mutation
through the locomotive's monitor the way `unlinkLocomotive` already is
([MarklinLocomotive.java:1187](../../src/org/traincontrol/marklin/MarklinLocomotive.java)) - noting
`INT-D1`'s warning that naively synchronising `setLinkedLocomotives` holds a locomotive lock across
`canBeLinkedTo`, which logs through the UI, so it needs its own analysis.

The `setDirection(getDirection())` at the end of the rebuild puts a real direction command on the
track per consist per applied change - pre-existing, identical to `changeLocAddress`, and deliberate
per `INT-A1`'s rebuild-vs-rehash discussion. Not part of this finding.

### RR-C2. The layout-refresh lock covers half the entrances

[MarklinControlStation.java:371](../../src/org/traincontrol/marklin/MarklinControlStation.java)
(`refreshLayouts`, `synchronized (this.layoutRefreshLock)`),
[MarklinControlStation.java:854](../../src/org/traincontrol/marklin/MarklinControlStation.java)
(`syncWithCS2` calling `syncLayoutsFromConfiguredSource()` with no lock).

The `FCR-B3` follow-up serialised layout refreshes on a dedicated lock, because two diagram edits saved
in quick succession could interleave two clear-and-repopulate cycles. But `syncWithCS2` reaches the
same `syncLayoutsFromConfiguredSource()` - the same `clearLayouts()` + `syncLayouts()` - without taking
`layoutRefreshLock`. A diagram-edit refresh on one background thread can therefore still interleave
with a full sync on another (the sync menu item, route bulk enable/disable, connect - about fourteen
call sites, most already threaded). The comment on the lock says refreshes "could interleave and leave
pages missing until the next one"; that remains true for this pairing, and `layoutDB` is backed by
plain `HashMap`s, so concurrent structural modification is undefined behaviour on top of the visible
symptom.

*Reachability:* needs a local layout override configured (`clearLayouts()` only runs on that branch;
without an override the sync path only refills an empty database), plus a sync and a diagram save
overlapping. Two concurrent `syncWithCS2` calls could always interleave here - that part is
pre-existing - but the fix's stated guarantee is broader than what the lock enforces, and diagram
saves now run on background threads *routinely* rather than blocking the EDT, so the window is visited
far more often than before. Same family as `IND` E1: a fix applied to one of two identical entrances.
Fix shape: take `layoutRefreshLock` inside `syncLayoutsFromConfiguredSource` itself, so every entrance
inherits it.

### RR-C3. Danish and German dialogs name an option the menu does not have

`messages_da.properties`, `messages_de.properties` - `autolayout.errorPathMisconfiguredDialog` vs
`autolayout.ui.tabAutonomyEnhancedValidation`.

The path-validation failure dialog ends by telling the user where to turn the feature off. In Danish
it names "Stærk stivalidering"; the menu item is labelled "Validering af stiintegritet". In German it
names "Starke Pfadvalidierung"; the menu item is "Pfadintegritätsprüfung". A user following the dialog
will not find an option by that name. The menu *path* ("Præferencer > Autonomi" / "Einstellungen >
Autonomie") is correct in both, and the other six languages - including all five bundles new in this
cycle - use the menu item's own wording. Two-line bundle fix.

### RR-C4. The backup dialog reports the folder even when the folder was not used

[TrainControlUI.java:11723](../../src/org/traincontrol/gui/TrainControlUI.java),
[Util.java:73](../../src/org/traincontrol/util/Util.java).

`Util.getBackupPath` falls back to the working directory when `tc_backup` cannot be created, but
`backupDataMenuItemActionPerformed` builds its "Saved to:" message from `Util.BACKUP_FOLDER`
unconditionally. In exactly the failure mode the fallback exists for, the dialog points at a folder
that does not contain the backups (and may not exist). The three backup writers also each compute
their own path, so in principle a `mkdirs` race could split one backup across two directories -
noted for completeness; the realistic case is simply a wrong dialog. Cosmetic, failure-mode only.

### RR-C5. Any monitor-thread failure is logged as "condition not satisfied"

[MarklinRoute.java:193](../../src/org/traincontrol/marklin/MarklinRoute.java) (the `catch (Exception e)`
in `executeAutoRoute`'s monitor loop).

The hardening this cycle added around the S88 monitor body - so no exception can silently end the
monitor, the `CR-A4` failure shape - reuses `route.s88ConditionFailed` ("Condition not satisfied for
route {0}") as its message for *any* exception, including a failure inside `execRoute` that has nothing
to do with conditions. The exception itself is logged on the next line, so no information is lost; the
headline is just wrong about the cause. A dedicated key would make the log truthful. Cosmetic.

---

## RR-D1. Checks that came back clean

Recorded so the next reviewer does not redo them - each was verified in the enforcing method, not
assumed, and several are exactly the places a regression from this cycle's own fixes would live:

- **The v2_7_2..v2_7_4 range holds nothing new** (19 commits: concurrency hardening, drag-and-drop,
  download progress). The `Layout` concurrent-collection conversion's null-hostility was re-checked at
  every writer: `setCallback` is never passed null (one caller, a real lambda), and `updatePendingS88`
  removes rather than putting null. The drag-and-drop cut/restore protocol (`createTransferable` cuts,
  `exportDone` restores on anything but a completed MOVE) covers cancel, failed-import and
  drop-outside-app. This range was also inside `IND`'s v2_7_2..HEAD scope, so it has now had two passes.
- **Bundle audit is clean.** All eight bundles: 1,191 keys each, exact parity with English, zero
  duplicate keys, zero non-ASCII bytes - including the five bundles (es/fr/it/nl/pl) new in this cycle.
  Every key referenced from `src/` (1,182 via `I18n.t/f`, `logf`, `getString`, and `.form` resources)
  exists; the only two misses are javadoc examples in `I18n.java`, a known non-issue. The keys removed
  this cycle (`acc.commandConflictSameAddressMustRename` and the `C20` duplicate definitions) have no
  live references - the dedup left one live definition of each shadowed key.
- **The `executePath` wrapper honours all three constraints `IND-D5` recorded as regression risks:**
  it catches rather than using `finally` (the fenced abort still leaves its entry), it rethrows (and
  `executeTimetable`'s catch at Layout.java:2285 still halts the run rather than retrying), and it does
  not unlock the path. The `activeLocomotives.remove` is a no-op when the exception predates the `put`.
- **The `isCurrentLayout` fence cannot be tripped by a stray `Layout`.** All eight `FCR-C1` sites use
  `hasAutoLayout()`; the only remaining constructions are `parseAuto` (guarded by the D2 warn-and-stop)
  and the lazy initialiser, which cannot fire while a layout exists - so nothing bumps `layoutVersion`
  while a path is running except a real reload.
- **`LayoutLabel`'s new off-EDT decode is coherent.** Both the background path and the EDT fallback key
  the cache with the same `getImageKey(size, edit)`; concurrent misses collapse via `putIfAbsent`; a
  failed background decode falls back to the EDT decode, whose `IOException` handler already existed.
  The commit-`5e80c41` repaint guard is safe: nothing is hidden before its early return
  (`InnerLayoutPanel.setVisible(false)` is commented out), and `layoutRefreshComplete` always repaints
  after the rebuild.
- **The plain hash removals the identity fix enabled are sound.** `Point.excludedLocs` is initialised
  in the constructor and `setExcludedLocs` rejects null, so `locDeleted`'s new sweep cannot NPE;
  `removeExcludedLoc` and the `executePath`-tail `remove()` are correct now that
  `MarklinLocomotive` hashes by identity.
- **The CS2 per-record catches cannot store a partial record.** In both `parseRoutes` and
  `parseLocomotives`, `out.add(...)` is the last statement of the guarded body, so a record that throws
  contributes nothing - the condition `FCR-C3` named as what made the wrap safe, re-verified.
- **`getAccessoryByAddressIfPresent` matches the creating lookup exactly** (same
  `UIDfromAddress(address - 1, type)` computation, minus `newSwitch`), and `NodeExpression`'s
  constructed name matches the `getNameWithProtocol` convention, with the Signal/Switch prefix fallback
  in `getAccessoryByName` covering the other type.
- **The autonomy right-click menu change is guarded downstream.** "Start Autonomy" now shows during a
  graceful stop (`!isAutoRunning()` alone), but it calls `requestStartAutonomy()`, which refuses via
  the button's enabled state, and `startAutonomyActionPerformed` re-checks `isRunning()`; the
  remove/edit items were simultaneously tightened with `!isRunning()`.
- **`NetworkProxy`'s socket-replacement protocol holds end to end**: the field is volatile, the reader
  re-reads it per pass and in its catch (so a replacement socket is picked up, and only a genuinely
  closed socket ends the thread), and `sendMessage`'s reopen now precedes the send. The known residual
  - a reader that has already exited is never restarted, leaving the proxy send-only - is unchanged
  from `CR`'s "Also noted, not fixed".
- **Every fix recorded across the five July documents is present at HEAD in its final, corrected
  form.** The whole v2_7_4..HEAD source diff was read; the fixes appear with their amendments applied
  (`SR1`'s `subList(0, edgesLocked)`, `SR2`'s close-nothing `finally`, `M4` routed through
  `unlinkLocomotive`, the `T1` capture-side convention, `D1/D2/D3/D5`, the identity `equals`/`hashCode`
  with all five repair methods deleted, `FCR`'s charset/round-trip/`editRoute`/per-record fixes).
  Nothing recorded as fixed is missing or reverted.

---

## Comparison against the July 2026 cycle

Written against [2026-07-cycle-summary.md](2026-07-cycle-summary.md) and the five documents it indexes.

### Did the cycle's changes introduce new errors?

**Two, both C-severity, both incompleteness rather than breakage - and both instances of the cycle's
own recorded patterns:**

1. **RR-C1** - the `INT-A2` fix added an automatic caller of the unsynchronised
   `setLinkedLocomotives`, widening an exposure `INT-D1` had recorded and deliberately scoped to the
   dialog path. This is the "a decision held for one path while a new path acquired the behaviour"
   shape the cycle summary names as its second recurring pattern - this time produced by a fix.
2. **RR-C2** - the `FCR-B3` follow-up's serialisation covers refresh-vs-refresh but not
   refresh-vs-sync, though both run the identical clear-and-repopulate. Same family as `IND` E1
   (the C19 fix applied to one of two identical sites).

The remaining three C items (RR-C3/C4/C5) are polish defects in code and strings that are new in this
cycle, not regressions of prior behaviour.

**No surviving regression was found in the cycle's high-risk fixes.** The areas re-derived here: path
integrity validation and `handleMisconfiguredPath`'s lock accounting (release is bounded to edges
actually taken; the shared-lock-edge limitation is the recorded `PC-P5`/`IND-B4` convention, not a new
hole), the `executePath` exception wrapper, the `isCurrentLayout` fence, identity hashing and the
deletion of the repair methods, the charset unification, `editRoute`'s check-before-delete, and the
per-record import catches. The errors the cycle made *and caught itself* (`CR-SR1`, `CR-SR2`, the `M4`
`changeLocAddress` regression, `INT-A1`'s `removeIf`, the first D2 refusal) are all present in their
corrected form; this pass found no additional error of that kind beyond the two above.

### Regressions from v2_7_2

None found at A or B severity. Every user-visible behaviour change traced in the two ranges is either
a documented fix, a documented feature (path validation, drag-and-drop, backups folder, new
translations, off-EDT tile decoding), or one of the C items above - all of which are new-code defects
rather than lost v2_7_2 behaviour.

### Standing items, unchanged by this review

- `CR-B6`'s acceptance (creating lookups on command paths) stands; this cycle removed the display-path
  triggers (`FCR-C2`), and no new creating lookup was added.
- The `LocDB.data` round-trip matrix against real 2.6.x/2.7.x files (`IND` area 3) has still not been
  run.
- `FCR-B1`'s charset fix is still verified by reading only - the byte-level regression test it calls
  for does not exist yet.
- The send-only `NetworkProxy` residual (reader exited, socket later reopened) remains a design item.

---

## Method note

Per the README: every "not synchronized", "without the lock", "no caller", "exists in all eight
bundles" and "last statement of the body" claim above was made by opening the named method or running
the described audit, not from the diff text or a method's name. Reachability claims were traced
caller-by-caller (`requestStartAutonomy` through its guard; `clearLayouts` to its override-only
branch; the sync repair loop to its `isAutonomyRunning` gate). The two findings that rest on
concurrency (RR-C1, RR-C2) name the exact lock each side holds, because that is where this codebase's
reviews have historically been wrong in both directions.
