# Fifth validation, 2026-09-02: is anything in today's commits worse for the operator than what it replaced?

**Status:** open

**Prefix:** `V35`. Cite these findings as `V35-C1`, `V35-D4`, and so on.

**Version reviewed:** `3c014e77` (branch `autonomy-diagram-r0`), 2026-09-02. **Range validated:**
`1cfdf370..3c014e77` - the task named "ten commits" and listed the same set the log holds, which is
nine: `1cfdf370`, `87b6c10a`, `975f157d`, `8d1c17ca`, `cf048f9b`, `54a70c03`, `e6791631`, `2e83b737`,
`3c014e77`. The preceding commit `469f69d6` (MT entries and the sweep) is outside the listed set and
was not judged.

**Method:** reading only. No test, ant, javac, java or TestNG invocation was made - a battery was
running throughout. Every claim that rests on the operator's real data was measured against the frozen
copy at `test/operator_layout/config/`; `cs2_sample_layout/` was neither read nor written.

**The question this pass asked** is not "is it correct" - six reviewers and three validators had
already asked that. It is: does any of these changes, correct or not, leave the operator worse off
than the behaviour it replaced? A train that moved and now does not, a control that worked and now
refuses, a setting honoured and now dropped, a dialog or warning that fires on a routine act, work
that could be lost.

---

## Summary

| Finding | Severity | Disposition | One line |
|---|---|---|---|
| V35-A | - | none found | nothing in the range stops a train, loses data, or refuses a routine act |
| V35-B | - | none found | no incorrect results and no crash paths introduced by the range |
| V35-C1 | C | open | Clear All Home Locomotives greys on an excluded page though its own javadoc calls it setup-wide, and the greyed tooltip does not say why |
| V35-C2 | C | open | the new protecting-signal question is asked only in the power-on branch; "power on and proceed" reaches the signal unasked |
| V35-C3 | C | open | `2e83b737`'s message reads as though RG3-B1 was fixed; the Readme fix exists only uncommitted, and HEAD still ships the false changelog sentence |
| V35-D1..D12 | D | closed | the checks that came back clean, each with what was measured |

---

## A - things that would put the operator worse off on the layout

**None found.** What was hunted, specifically: a guard firing on a case the operator meets routinely
(D3, D4, D5), a refusal stricter than the railway needs (D2, D6), a plan or path that used to exist
and now does not (D6, D7), a setting silently dropped (D8, D9), and dialogs or warnings on routine
acts (D3, D10). Each came back clean with the measurement recorded in its D entry.

## B - incorrect results or crashes in specific configurations

**None found.** The one ordering change with crash potential - the page-switch teardown - now pins its
continuation with a `finally` at the statements themselves, a `finally` around the worker, and a
`catch` for a throw before the worker starts, once-guarded (D11).

---

## C - narrow, cosmetic, or at-risk

### C1 - Clear All Home Locomotives greys on an excluded page, though the action is setup-wide

**FIXED 2026-09-02.**  The finding was overtaken once and then true again one level up.  Both actions moved into a **Bulk Tools** submenu whose items grey on their own counts alone - so the `ignored` term the finding names is gone - but an excluded page answered every right-click with "nothing here is yours to set" and no menu at all, which took the setup-wide action away by a different route.  Such a square now gets a menu carrying Bulk Tools and nothing else: the sentence is still said, and the menu is not empty afterwards.

| | |
|---|---|
| **Disposition** | FIXED 2026-09-02 |

`AutonomyEditorPanel.java:6235`:

    clearHomes.setEnabled(homed > 0 && !ignored);

where `ignored` is "the page being viewed is excluded from autonomy" (`AutonomyEditorPanel.java:6105`).
But `tilesWithAHome()` counts homes on **every** page, and the button's own javadoc
(`AutonomyEditorPanel.java` at `clearAllHomes`, and `AutonomySession.tilesWithAHome`) says it is "an
action about the whole setup rather than about the tile under the pointer". So on an excluded page the
operator is refused an action whose subject is not that page. The guard inside `clearAllHomes()` asks
only `homed.isEmpty()` - the affordance is stricter than the guard by the `ignored` term, which is
exactly the divergence shape this round spent three findings removing (TS3-B6, V31-B1, DY3-C6).

Two mitigations keep this a C rather than a B: every sibling in that column (`nameAll`, `testButton`,
`whyButton`, `AutonomyEditorPanel.java:6112-6113, 6220`) greys the same way, so this may be a
deliberate "an excluded page's tool column is inert" rule - if so, it is written nowhere; and the
remedy is one page switch. The cosmetic half: when `homed > 0 && ignored`, the disabled button's
tooltip (`AutonomyEditorPanel.java:6237-6239`) shows the action's confirmation text, not the reason it
is disabled.

### C2 - the protecting-signal question is asked only in the power-on branch

**FIXED 2026-09-02.**  It was the `else` of the power-off dialog and is now its own `if`, so the question is asked whether or not the power was on.  Two dialogs in a row in that case, which is the honest cost: the second only appears when autonomy is running and the accessory actually conflicts.  The finding's own reachability argument is why this was worth doing rather than writing down - power off with trains standing is exactly the state an emergency stop leaves, and "turn power on and proceed" is the gesture that follows it.

| | |
|---|---|
| **Disposition** | FIXED 2026-09-02 |

`LayoutLabel.java:343-382`: when track power is off, a click on an accessory takes the power-dialog
branch (`if (!tcUI.getModel().getPowerState())`), and the protecting-signal question added by SVN-B16
lives in the `else if` below it (`LayoutLabel.java:384-431`). So after an emergency stop during an
autonomy run - power off, trains standing, autonomy still loaded - clicking a protecting signal green
and choosing "Turn power on and proceed" turns protection off at an occupied platform with no mention
of the platform.

This is the briefing's "applied at every site" question, not a worsening: the identical gap has
existed for the active-route half (`activeAccs`) since that dialog was written, and the power dialog
does interpose a confirmation of its own. The route door (`MarklinRoute.heldReason`) is not gated on
power and still refuses. Worth a term in the power-off branch, or a written decision that power-off
clicks are the operator's own risk.

### C3 - the RG3-B1 changelog fix is claimed by a commit message and exists only uncommitted

| | |
|---|---|
| **Disposition** | FIXED 2026-09-02 (`76d7bb70`) |

**The line is committed.**  Adam asked for it directly - "commit the Readme fix" - and `76d7bb70` carries `Readme.md` along with his own changelog editing.  The finding was right that a commit message had claimed work that was only in the working tree, and right that one checkout would have lost it.

`2e83b737`'s message: "CD3-B3 and RG3-B1: two user-facing documents describing software that does not
exist - AutomationAPI said there is no single action that clears every home assignment, hours after
one shipped; the changelog offered users an older route editor that is deleted." The commit fixes
`AutomationAPI.md` (CD3-B3 - verified: `Layout.clearHomeLocomotives`, `hasHomeLocomotives`,
`setHomeLocomotive` all exist at `Layout.java:1190/1277/1291`, and the documented button shipped in
`1cfdf370`). It does not touch `Readme.md`. At HEAD, `Readme.md:387` still says:

    The older text editor is still there, and routes that came from the Central Station open
    read-only in both.

The RG3 review's own disposition table honestly says B1 is open. The fix - deleting the sentence -
exists in the working tree, uncommitted, alongside two further changelog edits and the three untracked
round-1 validation reports. Two risks, both cheap to close: a v3.0.0 built from any commit at or
before HEAD ships a changelog sentence that sends users hunting for a deleted editor (RG3-B1's
scenario), and the uncommitted edit is one `git checkout --` from gone. Commit it, and let the commit
that does claim it.

(Observed in passing, not judged: `git status` also shows `cs2_sample_layout/config/*` modified -
consistent with the battery run in progress against the live layout. Per the briefing those files were
not read.)

---

## D - what was checked and found sound

This is the substance of the pass. Each entry says what was measured, so the fix can be trusted for
the reason given rather than on faith.

### D1 - the funnel guard on executeRoute refuses nothing the operator ever had

`TrainControlUI.java:16130-16135` refuses a route whose name is in `routesExecuting`, logged
(`route.ui.infoAlreadyRunning`, messages.properties:1175). Measured: the flag cannot wedge -
`routeFinished` is called in the `finally` of `runAndTimeTheRoute` (`TrainControlUI.java:16224`), the
clear is the later of route-end and a 600 ms floor (`TrainControlUI.java:24582-24601, 24610`), and
`resetRouteSpinners` (`:24651`) wipes the set wholesale on sync, import and route edits. And nothing
was lost at the two newly-guarded doors: before this commit a second press there reached
`Route.setExecuting`, a synchronized re-entrancy guard that returned immediately and silently
(V31-B2's correction, now written at `:16109-16128`). The operator traded a silent nothing for a
logged nothing.

### D2 - the widened start door refuses only setups that could never run

The guard (`TrainControlUI.java:5183`) asks `hasErrors()` = `hasBlockingProblems() || errorCount() > 0`
(`AutonomySession.java:3583-3586`). The delta over the old `errorCount() == 0` gate is exactly "the
graph cannot be built", which no press of Start could ever have turned into a running setup. The LOAD
door deliberately stays narrower, with the reason at `AutonomySession.java:3572-3575` (loading an
errored setup is how its errors get fixed) - so no way past was removed. The affordances all read the
guard's own question after the sweep: `canStartAutonomy` (`TrainControlUI.java:20173-20176`), the
strip's `fixing` decision (`AutonomyOverlayToggle.java:354`), the right-click menu via
`canStartAutonomy` (`LayoutRightclickAutonomyMenu.java:194`). No decision site still asks the count;
the two remaining `autonomyErrorCount()` reads are for message text and band colour only.

### D3 - the protecting-signal confirmation cannot fire on a routine act

Three gates bound it, verified in the code: it exists only while autonomy is running
(`LayoutLabel.java:384` - manual-only operation never sees it); only in the green direction
(`aboutToClearProtection`, `LayoutLabel.java:1386-1401`: `isStraight()` returns false before the rule
is asked, so setting a signal RED - the protective act - is exempt, the WK3-B1 fix); and it is a
confirmation with Cancel-by-default-on-close semantics (`LayoutLabel.java:454-460`), not a refusal.
The one act that triggers it - turning a platform's protecting signal green by hand while a train
stands there, during a run - is the dangerous act the rule exists for, and the hand door ASKS where
the route door refuses outright, so the operator kept an escape hatch (deliberate double-heading is
one OK away). The route door's refusal itself predates this range; `87b6c10a` moved its computation to
`Layout.protectsAnOccupiedSquare` (`Layout.java:6157`) without changing which commands are refused
(`!rc.getSetting()` = green only, `MarklinRoute.java:471`).

### D4 - out of service now means what the cross always claimed, and on the operator's live data the change is inert

Measured against `test/operator_layout/config/autonomy/configuration-Main.json`: exactly **one** point
carries `active: false` - `2 - Bottom:8,7`, which `setup.json` lists in `stations` and names
`ParkingTrack12`. It is a **station**, and the builder never dropped `active` for stations - so
`D24-B5`'s fix (`AutonomyBuilder.java:936-953`) changes nothing in the graph his live configuration
emits. The badge fix (SVN-B6, `AutonomySession.java` `worthABadge`) likewise: a station was already
worth a badge. Both fixes matter only for plain squares crossed out in future - where the cross
previously drew in the editor and did nothing at all, the worst of the three possible behaviours.

### D5 - the legacy-import consequence is a restoration of the operator's own settings

`AutonomySession.java:510-527`'s claim was re-measured against the frozen legacy file: 24 points with
`active: false`, exactly 6 of them non-stations (`LowerDown`, `LowerDownPre`, `LowerParkingInner`,
`LowerParkingReverse`, `TunnelLongParkReverse`, `TunnelParkReverse`) - the javadoc's numbers are
exact. All six sit inside the same deliberately-mothballed group as the 18 inactive stations (the
Lower level and the parking yard), so blocking passage through them on import honours a setting the
operator recorded, and matches what the pre-diagram code did with the same file: the auto-run
inactive rule (`Layout.java:2224`) predates 2024, and the manual-intermediate rule
(`Layout.java:2278-2287`) was the 2026-08-02 fix `4e5fde8b`, in every rc. Only an rc-era user who
crossed a plain square, watched the cross be ignored, and preferred the ignoring would notice - and
what they lose is a bug.

### D6 - the reversal-room rule in the planner removes broken plans, not working ones

One helper, both sides: `Layout.measuredRoomToReverseInto` (`Layout.java:6201`), asked by
`isPathClear` (`:2405`) and by the planner (`HomeStaging.java:1041`). Verified equivalent: the
runtime call site's outer gates (train length recorded, path non-empty, ending terminus-or-reversing)
are re-checked inside the helper, so the runtime's refusals are unchanged; the planner refuses exactly
the plans whose first move the runtime would refuse - the plans that died on the railway yesterday.
Unmeasured is null, and null is accepted on both sides ("unmeasured is unknown, not zero"), so a
layout without lengths is untouched. The counting's two known unsoundnesses are shared by both sides
(no planner/runtime disagreement) and Adam has ruled on them ("OK", FX2-3, recorded at
`Layout.java:2389-2390`). The `e6791631` reorder (`HomeStaging.java:1020-1046`) only ADDS plans: room
is asked before the arrival is recorded in `seen`, so the longer approach the refusal's `continue`
depends on is no longer pruned as dominated. More trains get home, not fewer.

### D7 - two homes on one square: fires never on his data, and the state it repairs was worse

The new loader rule (`Layout.java:1155-1170`) drops the later of two home assignments that resolve to
one physical square, warned by name (`autolayout.warnHomeSquareAssignedTwice`, first-writer-wins,
deterministic in file order, same shape as the sibling `warnHomeLocomotiveAssignedTwice` beside it).
Measured: neither frozen config carries a single `home` key, so on the operator's data this fires
never. The state it repairs - Return Home answering IMPOSSIBLE naming both locomotives for the rest of
the session - is strictly worse than one warned removal with a named remedy.

### D8 - the function slots now honour OK/Cancel like everything else in their dialog

`RightClickFunctionMenu.java:287-305`: the two slots still write live (the label beside each tick has
to follow it), and every non-OK outcome - Cancel, Escape, the close box - restores the values captured
before the dialog opened (`:214-215`). The capture is taken outside the autonomy block, so with no
autonomy loaded the restore is a no-op rather than a special case. OK applies exactly what it always
applied. Nothing an operator ticks and confirms is dropped; nothing an operator abandons is kept.

### D9 - Clear All Home Locomotives cannot lose work

`AutonomyEditorPanel.java` `clearAllHomes()`: confirmation defaults to **No**
(`TrainControlUI.YES_NO_OPTS[1]`), the empty case is a hint line rather than a dialog, the clears go
through `session.setHome(tile, null)` - the same door as the per-square menu, so the one-home sweep
stays honest - and nothing touches disk before Save, so even a confirmed mistake is undone by Cancel.
The 2.8.1 confirmation wording was reused from the bundles where it had survived unreferenced.

### D10 - no new warning fires on load or on a routine act

Swept the range's new messages: `warnHomeSquareAssignedTwice` fires once per corrupt file (D7);
`infoAlreadyRunning` is a log line, not a dialog (D1); `confirmAccessoryProtecting` is bounded (D3);
`errorCannotBuildDetailOne` replaces a wrong count-of-zero message on a press that was already
refused; `errorInactiveIntermediatePoint` fires only when a path is asked through a square the
operator crossed out (D4/D5). All five keys are present in all eight bundles, and the added bundle
lines contain zero raw non-ASCII bytes - every accent is a `\uXXXX` escape (checked with a byte-level
grep over the round's resource diffs).

### D11 - the page-switch teardown ordering is guaranteed at all three links

`layoutEditingCompleteThen` (`TrainControlUI.java` at the method): the continuation is passed INTO the
chain and runs after the refresh - the fix's whole point, so Edit Layout can no longer re-enable with
the editor still open. Verified the guarantee survives every failure path: a `finally` in
`layoutRefreshComplete` covers the dozen statements, a `finally` in the worker posts the EDT half even
when `refreshLayouts` throws, and a `catch` in `layoutEditingCompleteThen` covers a throw before the
worker exists; an `AtomicBoolean` makes the two paths run it once. The javadoc that argued for the
old (covering-nothing) `finally` was corrected in `2e83b737` (CD3-B1), so the next reader will not
"simplify" it back.

### D12 - the remaining items, briefly

**`protectsAnOccupiedSquare` unsynchronised** (`e6791631`): it reads the documented-unsynchronised
points view and per-point lists only; the monitor it gave up is the one `configureAndLockPath` holds
across per-command sleeps, so keeping it would have re-frozen the window Stop lives in - the exact
regression `getActiveAccs`'s javadoc records being removed once already. Worst case now is an answer
about the railway a moment ago, on a confirmation dialog. **`one.sh`/`battery.sh`** (`3c014e77`,
`e6791631`): strictly more reporting - the two-line TestNG summary is now read whole, config
failures, zero-run and skipping classes are called out, the exit code goes non-zero, and the reaper is
resolved from the script's own directory with a loud warning when missing; nothing an operator of
these tools could miss that he saw before. **The disposition commits** (`cf048f9b`, `54a70c03`):
spot-checked SVN-B2 (closed by the ruling that names it, with the unsoundness still recorded at the
guard) and TCX-B2 (reopened because FX2-3 never put that question) - both corrections run toward
honesty, and the RG3/CD3 tables left open what is genuinely open (see C3). **The newest test fixes**
(`3c014e77`): the longer-approach test now carries the control that kills the
room-rule-deleted mutation (`testHomeStaging.java:3435-3447`), and the surface rule asserts the
helper's body still asks the shared rule (`testEditorSurfaceRules.java:647-659`) - both read as tests
that fail for their stated reasons.

---

## Verdict

Nothing in `1cfdf370..3c014e77` makes the operator's railway worse than what it replaced. The three C
findings are a greyed button on an excluded page, a pre-existing gap the new guard inherited, and
bookkeeping at the commit boundary. Every guard added or moved this round either refuses something
that was never obtainable (a second run that the model already swallowed, a start that could not
build), asks instead of refusing at the one door an operator might legitimately need (the protecting
signal by hand), or honours a setting of the operator's own that the rc had been silently ignoring
(the crossed square). The dangerous direction - a rule copied into the planner, a reorder in a search,
an unsynchronised read - was in each case measured against the frozen operator data and found to
widen what works rather than narrow it.
