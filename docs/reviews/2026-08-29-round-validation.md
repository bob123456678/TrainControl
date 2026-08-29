# Independent validation of the uncommitted round

**Status:** open

**Prefix for citing this document: `VAL`.** Nothing in `docs/reviews/` cites a `VAL-` identifier
today, so the letter is free.

**Version reviewed:** the WORKING TREE, uncommitted, on top of `eac0e392`, branch
`autonomy-diagram-r0`. **Reviewed:** 2026-08-29.

**The tree moved during this review, and one finding depends on that.**
`src/org/traincontrol/gui/LoadingSpinner.java` was rewritten at 01:05:48 while this pass was running -
after I had read the version the brief describes and after the compile that my test runs used. What I
report on that file is the 01:05 version, which is a *different and better* fix from the one I was
asked to check. `git diff HEAD | md5sum` of the state reviewed: `a6ac6978f44c1c9aa2753d583abbe005`.
Newest changed files at the time of writing: `LoadingSpinner.java` 08-29 01:05,
`cs2_sample_layout/config/autonomy/configuration-Main.json` 08-29 01:03,
`test/ui/testTheWaitMarkIsAnHourglass.java` 08-29 00:54. Everything else predates 08-28 23:41.

**No source or test file was changed as part of this review.** Nothing was written to
`cs2_sample_layout/`; the `one.sh` live-layout fingerprint guard passed on every run.

---

## Method, and what it could and could not settle

Reading, plus execution wherever a claim could be executed. Five things were settled by running code
rather than by argument, and three of them came out against the claim:

- **Test classes run**, one JVM each, via `one.sh`, against this working tree:
  `regression.testJavadocsAreAttached` (1/0/0), `regression.testSwitchingToACentralStationLayout`
  (9/0/0), `core.testAutonomyDiagramSession` (96/0/0), `core.testTrainTailClearsEdges` (5/0/0),
  `ui.testTheWaitMarkIsAnHourglass` (15/0/0). Format is `run/failures/skips`; **skips were checked and
  are zero everywhere**, because a class that skips reads as green.
- **A read-only probe of `AutonomyCompanionStore`** compiled against the same build directory,
  reproducing `testAnUnreadableImportChangesNothing` outside the repo in three variants. This settled
  VAL-B2 and VAL-D8.
- **Four read-only probes of `LoadingSpinner`**, rendering frames to `BufferedImage` and measuring
  sand mass, band positions and pixel differences, plus a reflective read of its constants and a
  direct reflective call to `drawHourglass`. This settled VAL-B4 and VAL-D4.
- **A script over all eight message bundles**, checking eleven properties rather than the seven asked
  for.
- **A script over all fifteen `.form`-backed classes**, diffing each `GEN-BEGIN:variables` block
  against the names its `.form` knows.

**What I could NOT verify.** No part of this was exercised through a real window: `guardLayoutMenu`,
the three right-click menus, `refreshActivateRoutesControls`, the `gracefulStop` wait and the
`MarklinControlStation.init` splash paths are all read-only findings, and VAL-B1 in particular deserves
a hands-on check before it is believed. I did not run the battery. I did not judge the *meaning* of any
translation, only its mechanics. And I could not determine who wrote to
`cs2_sample_layout/config/autonomy/configuration-Main.json` at 01:03 (VAL-C7).

---

## A - high

| | Finding | Disposition |
|---|---------|-------------|
| **A1** | The escape clause that survived WK-B1 is still a guess about this train, and it decides an unlock | open |

### VAL-A1. `tailHasProvablyPassed`'s first clause is the same defect WK-B1 removed, one edge earlier

WK-B1's central claim is written into three comments, a javadoc and a test name:

> Neither may rest on a guess about where the tail is (WK-B1).
> The one escape that is NOT a guess about this train is the first clause below. Where nothing on a
> path has a length, distance can never accumulate...

The first sentence is what the round set out to make true. The second is what the code actually does,
and the two are not the same statement. `Layout.java:3433`:

```java
if (travelledOnThisPath <= 0) return true;
```

`travelledOnThisPath` is not a property of the path. It is a running accumulator, declared at
`Layout.java:4722` as `int travelledOnThisPath = 0;` and incremented at `Layout.java:4834` with
`justTravelled` - the length of the edge the head has *already left*. So the clause reads "nothing
measured has been traversed YET", not "nothing on this path has a length anywhere". Its own comment at
4721 states the wrong one outright: *"Zero means the path carries no lengths at all."*

`Edge.length` defaults to `0` (`Edge.java:30`) and `getLength()` returns it unchanged, so an unmeasured
edge contributes nothing. On a path whose LEADING edges are unmeasured and whose later edges are not -
edges `[0, 100, 100]`, train 250 - the loop reaches `i = 1` with `travelledOnThisPath == 0`, the clause
fires, and edge 0 is added to `clearedEdges` and, with `atomicRoutes` off, unlocked. The train is
standing on it. That is Adam's own worked example from the WK-B1 write-up with the zero moved from the
end of the array to the front, and the code comment two files over says his railway has the
combination: *"sixty edges at zero, thirty measured, trains of two, three and four."*

The rule was verified by execution, not by argument: `core.testTrainTailClearsEdges`
(`testAnUnmeasuredPathDoesNotHoldForEver`) asserts `tailHasProvablyPassed(0, 0, 400) == true` and
passes. The derivation of `travelledOnThisPath == 0` at `i = 1` is from reading three lines of
straight-line code; I did not execute the loop, because doing so needs a railway.

The same test file makes the misreading explicit. `testUnmeasuredTrackIsNotProof` asserts

```java
assertFalse(Layout.tailHasProvablyPassed(200, 0, 150),
    "the edge the head has only just left is handed back immediately to a 150 train");
```

while its sibling asserts the identical situation with the accumulator at zero is *true*. Nothing
distinguishes the two cases on the railway: whether the accumulator is 200 or 0 when the head leaves an
edge depends only on whether somebody has measured the track behind it.

**Severity.** A, by the rubric - a route may throw a turnout on track a train is standing on. But it is
NOT a regression from this round: the clause is unchanged from `eac0e392` and the round made clearing
strictly stricter everywhere else. What this round got wrong is the claim that the class of defect is
closed, and it wrote that claim into the code in five places, which is exactly the kind of comment the
next reader will trust instead of re-deriving.

**Suggested repair, smallest first.** Compute the property the javadoc actually describes, once, before
the loop: `boolean pathHasNoLengths` over `path`, and pass that instead of the accumulator. It is a
one-line change at the call site, it makes the javadoc true, and it does not alter behaviour on a
railway where nothing is measured - which is the case the clause exists for.

---

## B - medium

| | Finding | Disposition |
|---|---------|-------------|
| **B1** | UXR-A1 greys `openCS3AppMenuItem` for the rest of the session; `repaintPathLabel` is not its writer | open |
| **B2** | `testAnUnreadableImportChangesNothing` still asserts nothing, and the round's fixture change made it less able to | open |
| **B3** | Two contradictory comment blocks at that same site, one of them describing an assertion that is not there | open |
| **B4** | Both new hourglass tests are dead guards: their stated mutation is what the shipped code does | open |
| **B5** | UXR-B5 fixed three `YES_NO_OPTS[0]` sites and left six destructive siblings | open |

### VAL-B1. Only two of the three Central Station items have an owner that hands them back

The brief asks whether closing the editor restores the three items `guardLayoutMenu` now refuses to
re-enable, and warns that if nothing calls `repaintPathLabel` on that path they stay greyed forever.
For two of the three the premise holds. For the third it does not.

`repaintPathLabel` (`TrainControlUI.java:23867`) writes exactly two of them:

```java
this.switchCSLayoutMenuItem.setEnabled(false);
this.downloadCSLayoutMenuItem.setEnabled(connected && isLayoutLoaded());
...
this.switchCSLayoutMenuItem.setEnabled(connected);
this.downloadCSLayoutMenuItem.setEnabled(false);
```

**`openCS3AppMenuItem` appears nowhere in it.** Its only other write in the file is
`TrainControlUI.java:6406`:

```java
if (!this.model.isCS3())
{
    this.openCS3AppMenuItem.setEnabled(false);
}
```

which runs once at start-up and only ever *disables*. Grep confirms there is no
`openCS3AppMenuItem.setEnabled(true)` anywhere in `src/`.

So on a CS3 controller: open the layout editor, open the Layouts menu while it is open (which is the
one thing the guard exists for), and `if (busy) child.setEnabled(false);` greys "Open CS3 Web App". The
editor closes, `layoutEditingComplete` -> `layoutRefreshComplete` -> `repaintLayout` ->
`repaintPathLabel` restores Switch and Download, and nothing restores this one. It is gone until the
application is restarted.

The previous code had a different bug in the same line - `child.setEnabled(!busy)` re-lit the CS3 item
on a CS2 controller, undoing line 6406 - so this is not a case of the old code being right. Both
writers are wrong; the item has no owner. The smallest repair is to give it one: decide it in
`repaintPathLabel` from `model.isCS3()` alongside the other two, and keep the skip in `guardLayoutMenu`
as written.

Two smaller consequences of the same shape, recorded here rather than as separate findings:
`LayoutEditor.confirmExitWithoutAsking` (`LayoutEditor.java:5119`) calls `setEditLayoutEnabled(true)`
and `dispose()` with no `repaintLayout`, so Switch and Download stay greyed after a failed page switch
too - see VAL-C5.

### VAL-B2. The store test proves nothing, and the fixture change made it prove less

This is the withdrawn assertion the brief asks about. The withdrawal's **conclusion** is right; its
**reason** is wrong twice over; and the fixture "fix" applied in the same edit is inert. All three were
settled by running the code, not by reading it.

I compiled a read-only probe against the same build and ran three variants:

```
CASE A (the fixture as it now stands - keys taken from the export)
  exported pointNames keys = [1:4,7]
  filled=0  threw=none   configurations after = [Mine, Theirs]

CASE B (the ORIGINAL fixture - the literal key "1:4,7")
  filled=0  threw=none   configurations after = [Mine, Theirs]

CASE C (a stored-form key the local store does NOT already hold)
  threw=org.json.JSONException: JSONObject["1:9,9"] is not a string (class java.lang.Integer : 12345)
  configurations after = [Mine]        <- "Theirs" correctly rolled back
  pointName(4,7) = Bottom Main         <- shared half correctly restored
```

Three things follow.

**The re-keying is a no-op.** The key the store exports for that square *is* `"1:4,7"` - the exact
string the comment calls "a key that does not parse". CASE A and CASE B are the same test. The new
`assertFalse(names.keySet().isEmpty())` precondition passes trivially and guards nothing that matters.

**Both stated reasons are wrong.** The first comment block says the old key "does not parse - the
import therefore succeeded quietly". The second says re-keying "does not help either, because the
export translates keys through page ids and importing under another configuration does not translate
them back". Neither is the mechanism. The mechanism is the merge rule in `importBundle`
(`AutonomyCompanionStore.java`, ~1806):

```java
// Kept, not replaced.  See the note above: this is a merge, not an adoption.
if (mine.has(inner)) continue;
```

Exporting from a store and importing back into the same store means every incoming key is already
present locally, so `filled` stays 0, so the `if (filled > 0)` block never runs, so `clearShared()` and
`readShared()` are never called at all. The strict accessor is not "skipped before it is reached" - it
is never reached because the whole read is never attempted. Page ids are shared store state set by
`setPageIds`, not per-configuration, so the configuration name has nothing to do with it.

**A refusing fixture is one line away.** CASE C adds a second `setPointName` for a square, exports,
removes that name locally so the merge has a gap to fill, and corrupts the exported value. It throws,
`importBundle` rolls back, and *both* halves of the rollback are observable - the shared half and the
configuration. The withdrawal's closing sentence ("Anyone re-adding it needs a bundle that is genuinely
refused first - which is the half of this test that has never run") is true and reads as though it were
hard. It is four lines.

As it stands, `testAnUnreadableImportChangesNothing` is a test whose name promises a rollback check and
whose body checks two things its own comment admits "hold whether the import threw or quietly
succeeded". It reads as protection for a real hazard - the shared half being wiped on the way to
reporting a failure - and is not.

### VAL-B3. Two agents' comments contradict each other in one block

At the same site, the surviving comment reads, in order:

> ...This is the one that actually needs the throw: MUTATION this catches - change readShared to use
> opt* accessors instead of the type-strict ones... without this assertion the whole method would still
> pass.
> NOT ASSERTED, and the reason is worth more than the assertion would have been (TST-B17).

The first paragraph documents a mutation caught by an assertion that the next line says is not there.
Above it, the fixture comment claims the re-keying fixed the problem the block below says it did not
fix. This is the seam the brief predicted: one agent repaired the fixture and wrote up the repair,
another withdrew the assertion and wrote up the withdrawal, and both paragraphs shipped. A reader
arriving cold cannot tell which is current. Whichever way VAL-B2 is resolved, one of these two blocks
has to go.

### VAL-B4. The two new hourglass tests no longer test what they say, and their mutations are live

The brief asks whether the inversion (`turns % 2 == 0 ? drained : 1.0 - drained`) is right at both
seams. It is not in the tree any more: `LoadingSpinner.java` was rewritten at 01:05 to keep the drain
always upright and rotate only during the turn. **That newer fix is correct** - see VAL-D4, where I
verify it by rendering. But the tests written for the inversion were last touched at 00:54 and were not
updated with it, and they are now dead:

- `testItRunsDownwardsAfterTheFlipToo` shoots frames 62 and 111. With the current code those frames
  render **pixel for pixel identical** to frames 0 and 49 - measured, `diff = 0` over 240x240 - which
  are the frames `testItRunsDownwards` already shoots. It is an exact duplicate of its sibling.
- Its stated mutation is *"dropping the inversion - passing `drained` for both cycles - fails this."*
  The shipped code passes `drained` for both cycles. The test passes. The mutation is live in the tree
  and the guard does not see it.
- `testTheTurnIsSeamless` carries the same claim - *"MUTATION: dropping the inversion fails both
  seams"* - and is equally false. Both seams are exact (`diff = 0`) with no inversion anywhere.

Neither is wrong about the *code*; both are wrong about what they are protecting, which is worse,
because "MUTATION: X fails this" is the sentence that stops the next reviewer re-deriving it. What the
current code needs guarding is different and narrower: that the drain phase is never drawn rotated. A
test that renders a mid-drain frame in the second cycle and asserts it is upright - equivalently, that
frame `n` and frame `n + CYCLE` are identical for `n` inside the drain - would catch the anchor
inversion that Adam actually reported.

The class javadoc and the `frame` field comment are stale in the same direction - see VAL-C3.

### VAL-B5. Three `YES_NO_OPTS[0]` sites were fixed; six destructive siblings were not

The fix direction is right: `YES_NO_OPTS = { I18n.t("ui.yes"), I18n.t("ui.no") }`
(`TrainControlUI.java:568`), so index 0 is Yes and index 1 is No. Verified.

The three sites changed are the three whose comment said `// default selection = "No"` while passing
index 0 - that is, the three where the intent was written down and contradicted. Twenty `[0]` sites
remain. Six of them are confirmations of destructive actions with Yes pre-selected:

| Line (`TrainControlUI.java`) | Prompt key |
|---|---|
| 15292 | `route.ui.confirmDeleteRoute` |
| 15641 | `page.ui.confirmClearKeyMappings` |
| 16634 | `ui.confirmDeleteFromDatabase` |
| 17359 | `timetable.ui.confirmRemoveAllEntries` |
| 19003 | `autolayout.ui.confirmReloadJsonResetsUnsavedChanges` |
| 20769 | `layout.ui.dialogConfirmDeletion` |

Unlike the three that were fixed, none of these has a comment stating an intent, so I am flagging the
list rather than calling each one a defect: whether "Delete Route" should pre-select No is Adam's
call, and it is the same call for all six. But the sweep was not done, and this is the pattern the
review README names as the July cycle's most repeated mistake.

---

## C - low

| | Finding | Disposition |
|---|---------|-------------|
| **C1** | `waiting[2]` is now written and never read | open |
| **C2** | `Layout.java:4721` states the false premise VAL-A1 rests on | open |
| **C3** | `LoadingSpinner`'s two-cycle frame counter and its javadoc are vestigial | open |
| **C4** | `guardLayoutMenu`'s loop still re-enables `initializeLocalLayoutMenuItem` - the third instance of the pattern the round fixed twice | open |
| **C5** | `confirmExitWithoutAsking` closes an editor with no `repaintLayout`, leaving the CS items greyed | open |
| **C6** | `BulkEnableOrDisable` duplicates `editRoute` rather than calling `enableOrDisableRoute` | open |
| **C7** | The real railway's `configuration-Main.json` is in the uncommitted set with a settings change | open |
| **C8** | The counting ratchets absorb a regression that coincides with a repair | open |
| **C9** | `gracefulStop`'s unbounded wait can leave both buttons disabled | open |

**VAL-C1.** `waitingToClear` entries are `{ index, behind, edgesSince }`. With `tailMayStillBeOn` gone,
`edgesSince` has no reader: `waiting[2]` appears exactly twice in the file, as the declaration
`new int[] { i - 1, 0, 0 }` and as `waiting[2]++`. The comment above it still explains why two numbers
are needed - *"Distance AND how many edges ago, because the two answer different halves of the
question"* - and only one half survives. Drop the third slot with its comment, or the next reader will
reconstruct the removed rule from it.

**VAL-C2.** `// How far this run has gone in total. Zero means the path carries no lengths at all` is
false, and it is the sentence that makes VAL-A1 invisible. Fix it with VAL-A1 or before it.

**VAL-C3.** With the drain always upright, the drawing has period `CYCLE_FRAMES`, not `CYCLE_FRAMES *
2` - measured: every frame `n` renders identically to `n + 62`. `frameAt` and `advanceOneFrame` still
wrap at `CYCLE_FRAMES * 2`, and the `frame` field's javadoc still says *"Two rather than one because the
drawing alternates between upright and inverted, and one cycle of frames leaves it standing on its
head."* The drawing no longer alternates. The class javadoc's "the animation loops by rotating rather
than by resetting" is now only true of the twelve turn frames.

**VAL-C4.** `guardLayoutMenu`'s loop sets `child.setEnabled(!busy)` on every unexcluded child, and
`initializeLocalLayoutMenuItem` is one - so opening the Layouts menu re-enables the item that
`initializeLocalLayoutMenuItemActionPerformed` (`TrainControlUI.java:17852`) deliberately disabled after
a local layout was initialised. This is the same "two writers of one property, disagreeing" that UXR-A1
fixed for the Central Station items and UXR-B2 for Manage Pages, in the same loop, left in place. UXR-B1
widened its reach: moving the menu listener above the `session == null` return means the loop now runs
for Central Station sessions too, where it never used to.

**VAL-C5.** See VAL-B1. Error path only.

**VAL-C6.** The three predicates asked about in the brief do agree - see VAL-D2 - but
`BulkEnableOrDisable` reaches `this.model.editRoute(...)` directly instead of going through
`enableOrDisableRoute`, so the rule exists in two places that happen to match today. The one asymmetry
is benign: Bulk *Enable* passes an s88-less route that is already enabled, and setting `enabled = true`
on an already-enabled route changes nothing.

**VAL-C7.** `cs2_sample_layout/config/autonomy/configuration-Main.json` is modified in the working tree,
last written 08-29 01:03. The change is not only placements: `"simulate": true` has been **removed**
from the settings block, alongside four locomotives moving between points. Per `one.sh`'s own rule,
placement churn is what a running railway looks like and a settings change is not. I could not
determine what wrote it - the battery guard would have caught a test, so the application is the likely
author - but it is a change to Adam's live configuration sitting in a commit of source changes, and it
should be separated from the round rather than swept in with it.

**VAL-C8.** All three ratchets pin with `assertEquals`, which is the right shape - an increase and a
decrease both fail, so an improvement cannot be silently lost. What none of them can see is a repair
and a new violation in the same round: 56 stays 56. For `MODELS_WITHOUT_A_SANDBOX` in particular, the
offending file names are already computed in the loop, so pinning the sorted set instead of the count
costs one line and closes the hole.

**VAL-C9.** `gracefulStopActionPerformed` now waits for `isRunning()` to clear before re-enabling
Start, having already disabled Graceful Stop synchronously. If a train never reaches its next station,
both buttons stay dead for the rest of the session. That is consistent with
`LayoutRightclickAutonomyMenu`, which asks the same question, and starting autonomy with a stuck active
locomotive would be wrong - so this is recorded as a consequence to be aware of rather than a defect to
fix. The `InterruptedException` branch `return`s without enabling Start, which is the same state by a
rarer door.

---

## D - not defects, and checks that came back clean

**VAL-D1. WK-B1's mechanism is real, and the single-gate structure holds.** The chain the brief asks
about is exactly as claimed, and I read every link rather than the summary of it:
`clearedEdges` (`Layout.java:424`) is consulted in `getActiveAccs` (`Layout.java:735-782`) as
`if (cleared != null && cleared.contains(e)) continue;`, `getActiveAccs` is read by
`MarklinRoute.heldReason` (`MarklinRoute.java:399`) as the `locked` collection it refuses against, and
`heldReason` is called per command from `MarklinRoute.java:367` and `:650`. The `isLockHeld` test sits
*before* the `cleared` test, so with `atomicRoutes` on - where the lock is held for the whole run - the
`cleared` set is indeed the only thing that can drop an edge's protection. `tailMayStillBeOn` is gone
from `src/` entirely; the only surviving mention is the assertion in `testTrainTailClearsEdges` that
forbids its return. Exactly one call site now decides both, and `testTheClearAndTheUnlockAskTheSameQuestion`
counts the calls rather than positioning them, which is the right shape - a second write to
`clearedEdges` in front of the gate fails it. Nothing else in `src/` calls a looser rule. The
qualification is VAL-A1: the one rule still contains one guess.

**VAL-D2. UXR-B6's three predicates agree.** Checked by enumeration over the four states:

| `hasS88()` | `isEnabled()` | Right-click offers | `enableOrDisableRoute` accepts | Agree |
|---|---|---|---|---|
| true | true | Disable | yes (`!enable`) | yes |
| true | false | Enable | yes (`hasS88()`) | yes |
| false | true | Disable | yes (`!enable`) | yes |
| false | false | nothing | no (would error) | yes |

`RightClickRouteMenu`'s `route.hasS88() \|\| route.isEnabled()` is `BulkEnableOrDisable`'s own filter at
`TrainControlUI.java:16979`, and it is `enableOrDisableRoute`'s `!enable \|\| r.hasS88()` seen from the
menu's side. The affordance and the guard ask one question, which is OB-057/OB-090's rule. See VAL-C6
for the duplication that makes them agree by copy rather than by construction.

**VAL-D3. UXR-B2 has one effective writer and the freshness is real.** `modifyLocalLayoutMenu.setEnabled`
appears exactly once in `src/`, at `TrainControlUI.java:4021` inside `applyLayoutEditingAvailability`.
`guardLayoutMenu` calls that method at `:2792`, before the `if (!busy)` block, so the menu-open refresh
Adam asked for is preserved and asks the owner's own predicate (`noEditorOpen && layoutCanBeEdited()`)
rather than a second one. One nuance worth writing down: the loop above it *does* transiently write
`modifyLocalLayoutMenu.setEnabled(!busy)` as an unexcluded child, and is then overwritten by the call
four lines later. The final value is correct because of the ordering, not because there is literally one
writer - so anything inserted between the loop and line 2792 will reintroduce the disagreement.

**VAL-D4. The current LoadingSpinner is correct, verified by rendering.** Not the fix the brief
describes - see VAL-B4 - but the 01:05 rewrite, which drops the inversion, holds `halfTurns` at 0 for
the whole drain, and rotates only during the twelve turn frames. Measured over 240x240 renders:

- Frame 55 (mid-turn) has ink bounding box `w=136 h=73` against frame 0's `w=73 h=136`: the glass is
  genuinely horizontal, so the turn animates.
- Seam one, frames 61 -> 62: **0 pixels differ**. Seam two, frames 123 -> 0: **0 pixels differ**.
- Every frame of the second cycle is pixel-identical to the corresponding frame of the first, so the
  sand runs downward in both and the loop is exactly periodic.
- The drain never leaves its anchors: sand mass above/below the waist at frame 25 is `[1660, 1143]`,
  and identically `[1660, 1143]` at frame 87.

For the record, the fix the brief describes - inverting `drained` on odd half-turns - would *not* have
been right, and a direct reflective call to `drawHourglass` shows why: `drawHourglass(0.5)` upright has
sand mass `[1660, 1143]` and the same call rotated has `[1149, 1625]`. The two are not each other, so
mid-drain the rotated cycle would have shown the upper sand hanging from the top plate with a gap above
the waist and the lower pile floating clear of the bottom plate. The gross direction would have been
fixed and the fill geometry left mirrored. The 01:05 version avoids the whole question by never rotating
during a drain.

**VAL-D5. No hand-written field survives in any generated block.** All fifteen `.form`-backed classes in
`src/org/traincontrol/gui/` were checked by extracting each `// Variables declaration - do not
modify//GEN-BEGIN:variables` block and testing every declared name against the `name="..."` attributes of
its `.form`. **Zero fields in any class are unknown to their form**, including `TrainControlUI`'s 428.
`cropOverlay` is correctly outside the block, at `TrainControlUI.java:21688`, with the reason recorded
beside it, and `grep -c cropOverlay TrainControlUI.form` returns 0. No `GEN-` marker line is added or
removed anywhere in the diff.

**VAL-D6. The seven bundles are clean, on eleven checks.** All eight files hold 1857 keys; key sets are
identical to `messages.properties` with zero missing and zero extra in every language; zero duplicate
keys anywhere; **zero non-ASCII bytes** in any file; zero malformed `\uXXXX` escapes; `{n}` placeholder
multisets match the base per key in all seven languages, zero mismatches; zero unescaped straight
apostrophes; zero stray `{` or `}` outside a placeholder; and no key that uses `''` is read through
`I18n.t` rather than `I18n.f` (which would render the doubled apostrophe literally). 348 `autosetup.*`
keys are present in every file - the brief says 309 translated; the German diff shows 306 changed lines,
so a few dozen were already there or already correct. Spot-check on translation reality rather than
mechanics: only 2-6 `autosetup.*` values per language are still byte-identical to the English, and
accented languages carry the expected escape density (pl 272, es 246, fr 242, da 220, de 189, it 172;
nl 35, which is what Dutch looks like). Two empty values exist in `it` and `pl`
(`stats.ui.valuePluralSuffix`) and are **pre-existing** - they are not in this round's diff.

**VAL-D7. All three ratchet pins match reality, and the tests really ran.** Confirmed by execution
rather than by trusting the battery: `testJavadocsAreAttached` 1 test, 0 failures, **0 skips**;
`testSwitchingToACentralStationLayout` 9/0/**0**; `testAutonomyDiagramSession` 96/0/**0**. Each pin is an
`assertEquals`, not a `<=`, so passing means the numbers are exactly 96 orphaned javadocs, 56 models
without a sandbox, and 2 `excludeRepeatedSensorPages` call sites. The two that also carry an
`assertTrue(<=)` use it only for a better message. See VAL-C8 for the one thing this shape cannot see.

**VAL-D8. `importBundle`'s rollback works.** Withdrawing the assertion left an impression that the
rollback is unproven. It is not - it is untested by *that* fixture. My CASE C probe drove a genuine
`JSONException` out of `readShared` and observed both halves restored: `getPointName` still returned
`"Bottom Main"` and `getConfigurationNames()` went back to `[Mine]` with `Theirs` removed. So the
withdrawal is right that the test does not exercise it, and wrong to imply the behaviour is in doubt.

**VAL-D9. Not defects, checked and cleared.** `YES_NO_OPTS` index direction is right (VAL-B5 concerns
only the unswept siblings). `MarklinControlStation.init`'s WK-B2 catch leaves `proxy` and `model`
definitely assigned because the `catch` rethrows, and the `finally` around `latch.await()` is harmless on
the ordinary path because `closeIfShown` is idempotent - though it catches `Exception`, so an `Error`
still escapes past the splash. `LayoutRightclickAutonomyMenu`'s switch from `isAutoRunning()` to
`ui.isAutonomyBusy()` is the right predicate: `isAutonomyBusy` delegates to `Layout.isRunning()`, which
is `running || !activeLocomotives.isEmpty() || locomotiveThreads > 0` and therefore stays true through
the coast-down, whereas `isAutoRunning()` returns the bare `running` flag that `stopLocomotives()` clears
immediately. The two names read alike and delegate differently, which is the case the review README says
to open rather than assume - I opened both. `refreshActivateRoutesControls` is called from
`repaintAutoLocListLite`, which runs on every arrival and departure, so the greying can un-grey itself
without a click. `mountLayoutHeadings` is idempotent (`localHeading != null` early return) and locates
its insertion points by lookup, so hoisting it above the `session == null` return is safe.

---

## Calibration note

Of the eight claims in the brief, five verified as stated (3, 4, 6, 7, 8), one verified with a material
qualification (1 - the mechanism is right, the "no guess remains" claim is not), one was half right
(2 - correct for two items of three), and one had been superseded in the tree before I could check it
(5). The withdrawn assertion was withdrawn for the wrong reason and the fixture edit that accompanied it
changed nothing.

Where the round was weakest is where the brief predicted: the seams. VAL-B3 is two agents writing
opposite conclusions into one comment block. VAL-B4 is a source file and its test file falling out of
step by eleven minutes. VAL-B1 and VAL-C4 are both the same loop, where three items were given an owner
and a fourth was not. VAL-B5 is a fix applied to the three sites that named their intent and not to the
six that did not.

Nine of my own findings came from executing something. Every one of the three that overturn a written
claim - VAL-B2, VAL-B4, VAL-D4 - had already been read past by me on a first pass and looked fine.
