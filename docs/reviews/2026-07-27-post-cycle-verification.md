# Post-cycle verification review - 2026-07-27

**Prefix for citing this document: `PV`.**

**Version reviewed:** commit `bad66a7` ("Fix minor bugs", 2026-07-27), branch `master`, described as
`v2_7_4c-63-gbad66a7`, the v2.8.0 working tree with every July 2026 cycle fix applied.
**Reviewed:** 2026-07-27. **No code was changed as part of this review, and no tests were run or
added** - the author builds and tests in NetBeans. Every claim below was made by reading the enforcing
method or by a scripted count over the source, per [README.md](README.md).

**Scope.** The cycle closed with `RR` reviewed at `5e80c41` - but the fixes for `RR-C1`..`C5` and for
`FP-B1`/`B2`/`C1`..`C5` landed *after* that, in the four source-bearing commits `c24a82a`, `5af916a`,
`1a407b3` and `c8af58e`. Those commits had never been read by anyone who did not write them, and the
cycle's own data says fixes-to-fixes are where its defects concentrated (five instances of the
one-of-several-entrances mistake, two produced while fixing earlier instances). So this pass:

1. verified each of those twelve fixes against its writeup, hunk by hunk;
2. traced the identity `equals`/`hashCode` change across the serialization boundary and through every
   value-equality call site, the one angle no prior document had asked about;
3. re-verified a sample of `RR-D1`'s clean-check claims in the enforcing methods;
4. checked the past week's range for regressions beyond what `RR` and `IND` had covered.

**Method result, for the calibration record.** All four findings this pass produced came from reading
the enforcing method, grepping for twin call sites, or counting - none from inference. `PV-C5` was
added afterwards, by the verification of this document, which also corrected `PV-C4`'s severity.

`PV-C1` was found *inside a method `RR-D1` had already verified* for a different property: the
off-EDT decode claim was true, and the defect sat one line below it. Consistent with the cycle's
own conclusion that verifying the property you came for is not the same as reading the method.

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| PV-C4 | Duplicate addresses on the *Central Station* side produce two rename proposals for one local locomotive; the second deletes an unrelated locomotive and renames nothing | **B** | **Fixed 2026-07-27**. Raised from C on verification |
| PV-C1 | The `FP-C3` unwrap missed its one multiline site, so "all 100 sites" is false by one - and it is the per-repaint hot path | C | **Fixed 2026-07-27** |
| PV-C2 | `switchCSLayout` clears the layout database on the EDT without `layoutRefreshLock` - a third entrance to the rebuild the `RR-C2` fix says it covered | C | **Fixed 2026-07-27** |
| PV-C3 | The sync deferral's comment still gives the pre-identity-hash rationale; the cleanup that fixed its two siblings missed it | C | **Fixed 2026-07-27** |
| PV-C5 | The javadoc on `equals`/`hashCode` - the cycle's most-cited comment - is structurally mangled, having lost its `*` prefix on 22 lines | C | **Fixed 2026-07-27** |
| PV-D1 | Fix verification and clean checks | - | Recorded |

**One B finding, and it was filed as a C.** `PV-C4` was written up as benign; tracing the delete branch
to its end showed the benign conclusion was wrong and the outcome is data loss. It keeps its `C4`
identifier - identifiers are fixed when assigned - so this is the second place in the cycle where the
letter no longer matches the severity, after `FP-C4`.

All findings are in code this cycle touched, and all are now fixed.

---

## Findings

### PV-C1. The one wrapped Thread the unwrap missed - because its call spans two lines

[LayoutLabel.java:337](../../src/org/traincontrol/gui/LayoutLabel.java) (`setImageOnEDT`):

```java
javax.swing.SwingUtilities.invokeLater(
new Thread(() ->
```

`FP-C3` records the unwrap of `submit(new Thread(...))` and `invokeLater(new Thread(...))` as
"**Fixed - all 100 sites**", verified by `new Thread(` occurrences falling from 169 to 69, with 69
"the independently pre-counted number of genuine thread creations, the ones that are actually
started". This site is the discrepancy in that arithmetic: the mechanical matcher fired only where
`submit(`/`invokeLater(`/`execute(` *immediately* preceded `new Thread(`, and here a line break
separates them - so the site was neither transformed nor counted as a candidate, and its `new Thread(`
was silently absorbed into the "genuine" 69 despite never being started. Reading the method settles
it: the object is handed to `invokeLater` and the method ends without a `.start()` (the only start
nearby is the Swing `Timer`'s, at line 402). The `FP` validation note even records the near-miss shape
("two used `()->` without a space, which an initial grep missed") - the checker was hardened against
lambda spacing, not against newline-split calls.

The behaviour is correct, exactly as `FP-C3` explains: the EDT calls `run()` on the unstarted Thread.
What remains is the hazard `FP-C3` was actually fixing - code that reads as though a thread is spawned
when it is not - plus the (weak) allocation argument, and this is the one site where that argument has
any weight at all: `setImageOnEDT` runs once per tile image refresh, the hottest of the 100 paths.

*Found by reading `setImageOnEDT` while re-verifying `RR-D1`'s off-EDT decode claim - which is true.
One-line fix; the multiline grep `(submit|invokeLater|execute)\(\s*\n\s*new Thread\(` confirms it is
the only one.*

### PV-C2. A third entrance to the layout rebuild, unguarded and on the EDT

[TrainControlUI.java:11775](../../src/org/traincontrol/gui/TrainControlUI.java)
(`switchCSLayoutMenuItemActionPerformed`: `prefs.put(LAYOUT_OVERRIDE_PATH_PREF, ""); this.model.clearLayouts(); this.model.syncWithCS2();`),
[MarklinControlStation.java:318](../../src/org/traincontrol/marklin/MarklinControlStation.java)
(the lock), [MarklinControlStation.java:1077](../../src/org/traincontrol/marklin/MarklinControlStation.java)
(`clearLayouts`, which had no lock of its own and now takes one).

The `RR-C2` fix moved `layoutRefreshLock` inside `syncLayoutsFromConfiguredSource` with the comment
"so every entrance inherits it". Both entrances *to that method* do inherit it. But "Switch to CS
Layout" reaches the same emptied-database state directly: it calls `clearLayouts()` itself, on the
EDT, with no lock, before calling `syncWithCS2()` (whose own clear-and-repopulate then locks
correctly). `clearLayouts` iterates and deletes from `layoutDB` - plain `HashMap`s.

Reachability was traced, not assumed: the menu item is enabled exactly when `isLocalLayout()`
([TrainControlUI.java:15885](../../src/org/traincontrol/gui/TrainControlUI.java)) - the same
override mode in which a background diagram-save refresh runs `clearLayouts()` + `syncLayouts()` under
the lock. So the user who saves a diagram edit and then clicks "Switch to CS Layout" while the refresh
is still parsing can have the EDT deleting from `layoutDB` while a background thread repopulates it:
concurrent structural modification on a plain `HashMap`, or - the benign outcome - the EDT's
`syncWithCS2` finding the database already repopulated from local files and skipping the CS load, so
the UI keeps showing the local layout the user just asked to leave.

This call predates the cycle, but the hazard does not: until `FCR-B3` moved refreshes off the EDT,
this handler and every refresh shared the event thread and were serialised for free. It is the
README's "moving work off a single thread removes an exclusion nobody wrote down", surfacing at an
entrance `RR-C2` did not enumerate - and it makes this the sixth instance of the cycle's
one-of-several-entrances pattern. Rated C like `RR-C2`: it needs override mode plus a user action
landing inside a refresh window, though it is B-shaped by the letter of the definition. Fix shape:
have `clearLayouts` take `layoutRefreshLock` internally (it is the model's own method, and the lock is
reentrant, so the locked callers are unaffected).

*Also noted, not a finding:* the same handler runs the full `syncWithCS2()` on the EDT, freezing the
UI for the duration - the known deferred roadmap item (the `syncWithCS2` EDT split), not a regression.

### PV-C3. The deferral comment the cleanup missed

[MarklinControlStation.java:989](../../src/org/traincontrol/marklin/MarklinControlStation.java)
(the `isAutonomyRunning()` deferral in `syncWithCS2`'s address-change branch):

> setAddress mutates the address and decoder type in place, and both are hashCode inputs, so the
> locomotive drifts out of every collection keyed on the object: its consist stops recognising it, its
> station exclusions stop applying, and an entry stranded in activeLocomotives would leave isRunning()
> permanently true.

Every claim in that sentence was true when it was written (`44fd035`) and none is true at HEAD:
`MarklinLocomotive` now hashes by identity
([MarklinLocomotive.java:947](../../src/org/traincontrol/marklin/MarklinLocomotive.java)), so nothing
drifts anywhere. Commit `9c5727e` ("Cleanup") deleted the dead `rehashLocomotiveKeys` sweeps and
rewrote the equivalent comments at `renameLoc` and `changeLocAddress` to say so - and left this third
copy standing. The comment-that-describes-the-old-world is the README's "leave the reasoning where the
next person will trip over it" failing in the other direction: the next person will trip over
reasoning that is no longer true, and may re-add repair machinery to satisfy it.

The *deferral itself* should stay - a rename and a manual address change are refused while running,
and a mid-run address change would redirect commands to a different decoder - so this is a
comment-only fix: state those reasons instead of the hash-drift ones.

*Found by reading the method during the `INT-A2` repair-loop verification, then confirmed stale with
`git log -S`: the cleanup commit edited the two sibling comments in the same file and not this one.*

### PV-C4. `FP-B1`'s mirror image: duplicate addresses on the Central Station side - severity B

[MarklinControlStation.java:779](../../src/org/traincontrol/marklin/MarklinControlStation.java)
(`getLocomotivesToRenameFromImport`),
[TrainControlUI.java:13949](../../src/org/traincontrol/gui/TrainControlUI.java) (the consumer loop),
[MarklinControlStation.java:2455](../../src/org/traincontrol/marklin/MarklinControlStation.java)
(`renameLoc`'s guard).

The `FP-B1` fix refuses to propose anything when *the local database* holds several locomotives at one
address. The Central Station can hold duplicates at one address too - the same convenience-duplication
the author's comment describes locally - and the parsed list then contains two locomotives with equal
`getIntUID()`. Each one looks up the same single local match, so the candidate list can be
`[{X -> A}, {X -> B}]`: two proposals with one source. The consumer precomputes the list and acts on
it in order, so after the user accepts `X -> A`, the second dialog asks to rename `X` - a locomotive
that no longer exists - to `B`.

What actually happens then was traced to the end, and it is benign: if the user confirms,
`getLocByName(B)` is null (see the reachability note below), `renameLoc("X", "B")` fails its
`l != null` guard and returns false, the UI ignores the return value, and
`sanitizeMultiUnits(null)` is a guarded no-op
([Layout.java:2824](../../src/org/traincontrol/automation/Layout.java)). No exception, no data
change - just a dialog naming a phantom locomotive and a confirmation that silently does nothing,
which is the same "the dialog names something the user has never heard of" complaint `FP-B1` was
partly about.

**The claim that the destructive variant is blocked was wrong - this is the correction.** The original
writeup allowed that `deleteLoc(B)` fires when a local locomotive named `B` sits at a *different*
address, but called that "the pre-existing, intended rename semantics". It is not. Traced to the end:

1. The dialog says "rename X to B" and the user confirms. `l` was read as `getLocByName("X")` at the
   top of the iteration - **null**, X having already become A - and is never re-read.
2. `l2 = getLocByName("B")` finds the unrelated local `B`, so `deleteLoc("B")` runs, prompts, and on
   confirmation **destroys it**.
3. `l2` is now null, so `renameLoc("X", "B")` runs and hits its `l != null` guard: **it does nothing**.

Intended rename semantics delete the old holder of the name *and* leave a locomotive holding it. Here
the user confirms two dialogs that both describe a rename, and ends with `B` deleted, nothing renamed,
and its function mappings, notes and images gone with it.

**Severity B, on the same rationale as `FP-B1`**: real destruction, unusual precondition, and a user
interface that does not say what happened. The reachability narrowing still holds - `syncWithCS2`
auto-adds CS locomotives the local database lacks, so after any sync the local side holds the
duplicates itself and the local refusal engages - which is why it is B and not A.

**Fixed** by grouping the parsed side by `getIntUID()` exactly as `FP-B1` groups the local side, and
declining any address ambiguous on *either* side, logging
`loc.renameAmbiguousCentralStationDuplicate`. The check sits after the local-match lookup, so a Central
Station duplicate with no local counterpart stays silent - there is nothing to propose for it either
way.

**Tests.** `testImportRename.testDuplicateCentralStationAddressProducesNoRenameProposal`. No new fixture
was needed: `CS3_loks.json` already holds two locomotives at each of MM2 1, 3 and 60 - the test uses 60
("ALCO UP" and "V 60 706").

The test has to *build* the one-local-two-remote shape rather than assume it, and the first version did
not - it asserted the precondition and failed on the author's database. The reason is the same
narrowing that keeps this finding at B: `syncWithCS2` auto-adds Central Station locomotives the local
side lacks, so on any database that has synced against this fixture both duplicates are local too and
the local-side refusal fires first. The test now deletes every other locomotive on that decoder before
installing its own, in memory only - `saveState` lives in `TrainControlUI`, which the tests never
construct. Its precondition counts by UID rather than address, since `getDuplicateLocAddresses` keys on
`getAddress` and cannot tell an MM2 60 from an MFX 60. A `renameTargetsFor` helper was added beside
`renameTargetFor`: the defect is precisely that one source yielded more than one proposal, which a
first-match lookup hides.

*Found by asking the `FP-B1` fix the question it asked the original code - "what are the keys, on both
sides?" - and then reading the consumer loop and `renameLoc`'s guard rather than predicting them. The
severity was corrected only when a second reader traced the delete branch to its end instead of
accepting the "intended semantics" characterisation - the tracing this document's own method section
recommends, applied to this document.*

---

### PV-C5. The mangled javadoc on the cycle's most-cited comment

The block explaining identity `equals`/`hashCode` on `MarklinLocomotive` had lost the leading `*` on 22
consecutive lines, and its summary sentence read `Identity, deliberately.Do NOT reimplement...` with no
space after the period. It compiles - everything between `/**` and `*/` is a comment either way - so
nothing flagged it, including the structural validator, which counts braces and quotes rather than
comment shape.

`Edge.validateConfigCommand` carries the same `valid.Creates` signature, which points at a NetBeans
javadoc reformat rather than a hand edit, and means other blocks in the tree may be affected.

**Fixed**: prefixes restored, summary sentence split, block rewrapped, no wording changed.

Worth recording because this document verified that fix's *behaviour* in detail and never looked at the
comment carrying its reasoning - the same shape as `PV-C1`, found while verifying a different property
of the same method.

---

## PV-D1. Fix verification and clean checks

Each of the twelve post-`RR` fixes was verified in the current source against its writeup - the
enforcing method was read in every case, and for bundle-backed messages the eight `.properties` files
were checked for the key with matching placeholder counts:

- **`RR-C1`** - `setLinkedLocomotives` stages into a local map and swaps under `synchronized (this)`;
  the `-1` early-return path also clears under the monitor. The `canBeLinkedTo` overload was read
  whole: the address-conflict loop is its **only** read of this locomotive's own member map (the other
  checks read the *other* locomotive), so passing the staged set reproduces the old in-place
  semantics exactly, with no stale read left behind.
- **`RR-C2`** - the lock now lives in `syncLayoutsFromConfiguredSource`; both `refreshLayouts` and
  `syncWithCS2` route through it. `PV-C2` is a third entrance that does not, not a defect in what was
  moved.
- **`RR-C3`** - the Danish and German dialogs now name "Validering af stiintegritet" /
  "Pfadintegritätsprüfung", byte-identical to `tabAutonomyEnhancedValidation` in the same bundles.
- **`RR-C4`** - the dialog derives its folder from `Util.getBackupPath("x")`, which is deterministic
  with the writers' own calls in both the success and fallback branches; only a
  folder-obstruction-vanishing-mid-dialog race could still mislabel, which is negligible. The
  three-writers residual `RR-C4` noted is unchanged and still recorded there.
- **`RR-C5`** - `route.s88MonitorFailed` exists in all eight bundles and the catch now uses it.
- **`FP-B1`** - the index-and-refuse shape is implemented as written; `renameAmbiguousDuplicateAddress`
  is in all eight bundles with both placeholders used; `testDuplicateAddressProducesNoRenameProposal`
  exists and `testImportRename` has the `@BeforeMethod` cleanup its writeup promised. The refusal is
  also what blocks `PV-C4`'s destructive variant. The changelog entry is present and phrased for a
  non-technical reader.
- **`FP-B2`** - both formerly-empty catches (`pickPath` and `debugPath`) log
  `errorPathSelectionFailed` plus the exception; the key is in all eight bundles; control flow is
  unchanged (still returns null into the retry).
- **`FP-C1`** - `parseFile` is a try-with-resources wrapper over `parseFileContents`, which is private
  with the wrapper as sole caller; the trailing `in.close()` is gone.
- **`FP-C4`** - the inner throw is converted by the edge loop's outer catch at
  [Layout.java:4046](../../src/org/traincontrol/automation/Layout.java) into the
  `errorInvalidEdgeWithMessage` invalidation, exactly as described; the edge is not created and the
  layout loads invalid.
- **`FP-C5`** - all four catches log through the model.
- **`FP-C3`** - zero single-line wrapped sites remain anywhere in `src/`; the one multiline site is
  `PV-C1`.
- **New tests** - `testAdvancedRoutes` and `testInvalidInput` are registered in `build.xml`.

And the checks this pass added that no prior document had made:

- **The identity `equals`/`hashCode` change is serialization-safe.** No locomotive *object* crosses a
  stream boundary: `UIState.data` stores button mappings as locomotive **names**
  ([TrainControlUI.java:1070](../../src/org/traincontrol/gui/TrainControlUI.java)), and `LocDB.data`
  stores `MarklinSimpleComponent` DTOs whose consist fields are `Map<String, Double>` of **names**
  ([MarklinSimpleComponent.java:43](../../src/org/traincontrol/marklin/MarklinSimpleComponent.java)),
  rebuilt into canonical instances on load. So identity equality can never be asked to match two
  deserialized copies of one locomotive.
- **No value-equality call site depends on the old `equals`.** Every `.equals(`/`.contains(`/`.remove(`
  involving locomotives compares instances obtained from the model or graph (canonical, one per
  locomotive) or operates on name strings - including `fromJSON`'s duplicate-locomotive check, which
  `testInvalidInput` pins.
- **`executePath`'s wrapper still honours all three `IND-D5` constraints** (catch not `finally`,
  rethrow preserved, no unlock) - re-read at HEAD, since the surrounding file changed again today.
- **The CS2/CS3 per-record catches still cannot store a partial record** - `out.add(...)` is the last
  statement of the guarded body in all four parsers (`parseRoutes`, `parseRoutesCS3`,
  `parseLocomotives`, `parseLocomotivesCS3`).
- **The version-fence mechanism reads as documented** (constructor bumps, instance compares).
- **The changelog rule holds**: both entries added today describe defects a user could actually hit,
  in non-technical wording.

---

## Standing items, unchanged by this review

- `FP-B3` and `FP-C6` remain open by the author's decision; nothing here changes their analysis.
- The `LocDB.data` round-trip matrix against real 2.6.x/2.7.x files (`IND` area 3) has still not been
  run - noted again because this pass verified the identity-hash change by reading the serialization
  path, which is not the same as loading an old file.
- `FCR-B1`'s byte-level charset regression test still does not exist.
- The editor flows remain covered by manual testing only (`FCR-B3`).
- The send-only `NetworkProxy` residual remains a design item.
