# FR-109 experiment: grades

Read-only review of the four builds (git diff base..head in each worktree, plus the JUnit reports left in each
`w-exp-*-scratch/oneout/junitreports`). Nothing was run. Line numbers are in the head commit of each build.
Scores are 1 to 5, and 5 is best. For "Risk to merge", 5 means low risk.

## Scorecard

| Build | Correctness | Reuse vs copy | Tests | Fit | Risk to merge | Tokens | Tool calls | Wall time |
|---|---|---|---|---|---|---|---|---|
| pre-sonnet (b6f3dfcb..c5a0b615) | 4 | 3 | 4 | 3 | 2 | 299,723 | 98 | 6,586 s |
| pre-haiku (b6f3dfcb..cbb25668, unfinished) | 2 | 3 | 1 | 3 | 1 | ~360k (stopped) | ~150 | ~7,500 s (stopped) |
| post-sonnet (ab54c48b..b950dcb0) | 4 | 4 | 4 | 5 | 4 | 194,440 | 56 | 1,481 s |
| post-haiku (ab54c48b..2aaa5df7) | 3 | 3 | 3 | 3 | 3 | 265,130 | 72 | 1,348 s |

Production change size (`src/` only): pre-sonnet +430/-109, pre-haiku +359/-105, post-sonnet +228/-9,
post-haiku +314/-115. No build touched a `//GEN-BEGIN`..`//GEN-END` region or the variables block (I checked
every hunk against those ranges by script). All four edited the bodies of the GEN-FIRST/GEN-LAST handlers, which
the brief allows. Two of them (pre-haiku, post-haiku) moved the bodies out into new methods.

All four use the existing bundle keys (`ui.main.toolbar.addBlackPage`, `renameCurrentPage`, `deleteCurrentPage`,
and `modifyLayout` or `layout.ui.sidebarPages` for the menu title). None changed a bundle.

---

## post-sonnet: the pick

**Design.** The editor gets a "Manage Pages" menu bar (`LayoutEditor.java:4054`). `administerPage`
(`LayoutEditor.java:4092`) does the following:
- settles unsaved work;
- re-selects the editor's page in the main window (`:4107`);
- closes the window the way Cancel or Close does: `undoAutonomyEdits()` plus `layoutEditingCompleteFromTheEditor(run)`
  for the track editor (`:4121-4123`), and `autonomyEditorClosedFromTheEditor` for the setup editor (`:4116`);
- then calls the main window's real handlers through `addBlankPageForTheEditor` and the other two
  (`TrainControlUI.java:22063`ff).

A one-shot field, `pageActionSettled` (`TrainControlUI.java:22043`), is fired at every exit of the three handlers
and of `LayoutFileActions.duplicateOrRenameCurrentLayout` (`LayoutFileActions.java:423-531`). It reopens the
editor on whatever page the main window then shows, in the same mode (`TrainControlUI.java:22100-22104`).

**Strengths**
- Real reuse. The Layouts-menu handlers run unchanged in logic: the same name checks, the same confirmation text,
  the same run refusal and the same only-page refusal. No signature was changed, nothing's visibility was widened,
  and no rule was copied. The only new overload is `layoutEditingCompleteFromTheEditor(Runnable)`
  (`TrainControlUI.java:19994`).
- It acts on the edited page even when the main window shows another one, because of `selectLayoutPage(page)`
  before the action. The page list in the main window is never locked while an editor is open, so this matters.
  Both pre builds miss it.
- It mirrors the existing close paths exactly: `confirmExit` for the track editor and `jumpToSquare` for the
  setup editor. So unsaved work, the setup snapshot and the refresh counting behave as they already do.
- The setup-editor path is handled and tested. It falls back to the track editor when no setup can be edited
  (`:22104`).
- It follows the editor's house rule that nothing takes the keyboard: `setFocusable(false)` on the menu and on
  the items (`LayoutEditor.java:4060,4077`). Every other control in the class does the same.
- Smallest change, lean javadoc, no history, US English.

**Bugs and weak spots**
- **Medium.** Unsaved work is settled before any refusal or question. If the user picks Discard and then cancels
  the name prompt, or the name is refused, or a run refuses the action, the edits are already gone.
  (`administerPage` calls `settleUnsavedWork()` at `:4096`, before the handlers ask anything.)
- **Medium-low.** If a run holds the setup (a route started by an s88 trigger while the editor is open), the editor
  is closed, the handler refuses, and then `reopenEditorAfterAPageAction` calls `openLayoutEditor`, which refuses
  again ("cannot edit while running"). The user gets two dialogs and no editor. Nothing is lost. This path is not
  tested.
- **Low (UX).** The window closes and reopens on every action, cancel and refusal included. Scroll position, zoom
  and undo history do not survive the reopen.
- **Low (fragility).** The `pageActionSettled` field depends on every exit of three handlers and of
  `LayoutFileActions` remembering to call it: 13 call sites. An unexpected throw, for example `getSelectedItem()`
  returning null in a handler, would leave it set. The next main-window page action would then reopen an editor
  nobody asked for.
- **Low.** The reopen runs synchronously inside `layoutRefreshComplete`'s `after`, before the main window's tab is
  restored. The existing `leaveFor` posts its `arriveAt` instead. This is harmless today.

**Tests** (`test/ui/testTheLayoutEditorManagesPages.java`, 6 claims, all through the editor's own menu items with
real dialogs answered by `support.Answering`)
- Each claim checks what the user sees: the editor's title shows the page, and exactly one editor is open. It also
  checks what is saved: the page file, the index, `setup.json` page names, and a whole-folder SHA fingerprint for
  No and for a refused rename.
- Mutations, one per claim:
  - Add: write a copy rather than a blank page. Caught, because the page's squares must be empty (`:128`).
  - Rename: reopen the old page. Caught (`:166`).
  - Delete: use the editor's own confirmation. Caught, because the exact Layouts sentence is asserted (`:199`).
  - Setup-editor rename: always reopen the track editor. Caught (`:251`).
  - Delete answered No: do not reopen. Caught (`:278`).
  - Taken name: skip the check. Caught (`:310`).
  - **Not caught:** dropping `selectLayoutPage(page)`, because `editPage` already selects the page in the main
    window first (`:333`). No test covers the unsaved-work question or the run refusal.
- Red was shown for 5 of the 6 claims. The setup-editor claim was added after the red run and never shown failing.
- Fixture: fresh `LayoutSandbox` and `TestStation` per method. It opens the editor through
  `ui.openLayoutEditor(page, autonomy, null)` rather than the Edit button, so no preference is written. The class is
  in `build.xml`. There is a single class-level "MUTATIONS" paragraph rather than one `MUTATION:` note per claim, as
  CLAUDE.md asks.
- Classes run green: 18 report files, including `testTheRefusalsAreAskedAtTheDoors`, `testCommentsStayLean` and
  `testEveryTestIsInTheBattery`. It did not run `ui.testTheEditorNamesItsShortcuts`. Its focus handling probably
  keeps that class green, but nobody has shown it.

**To ship:** see "What to fix" below. This is about a day of work, not a rewrite.

---

## pre-sonnet: best interaction design, wrong base

**Design.** The editor presses the main window's own handlers with itself as the event source
(`TrainControlUI.java:28967` `editorBehind`, and `addBlankPageFromTheEditor` and the other two). The handlers:
- hang their dialogs over the editor (`promptUserForLayout(from == null ? this : from, ...)` at `:28947`, and
  `over` in every message);
- skip the editor-open refusal only for the editor that asked (`:29416`, `:29575`);
- ask every question and refusal first, and only then have the editor step aside
  (`LayoutEditor.stepOutFor` at `:7020`). Stepping aside settles unsaved work, tears down and re-reads the pages,
  but keeps the window;
- end with `arriveOn` (`:7065`), which re-points the window. A `finally` puts the editor back on its page whenever
  the action comes to nothing (`TrainControlUI.java:29458`, `:29641`).

**Strengths**
- The best user experience of the four. There is no flash. Refusals and the Delete question come before the
  unsaved-work question and before any teardown. Every dialog hangs off the window the user is looking at. A refused
  or canceled action leaves the editor exactly as it was.
- Every exit path tells the editor where to arrive, through the `finally` blocks.
- The broadest tests. It also hit a real regression (the menu took the keyboard), found it, and fixed it
  (`LayoutEditor.java:6976`).

**Bugs and weak spots**
- **Medium.** It acts on the main window's selected page, not on the editor's page. Rename reads
  `this.LayoutList.getSelectedItem()` (`TrainControlUI.java:28947,29478`). Delete confirms and deletes
  `LayoutList.getSelectedItem()` (`:29602,29650`). Nothing re-selects the editor's page first, and the main
  window's page list stays live while an editor is open. If the user browses another page in the main window, Rename
  or Delete in the editor targets that other page. The dialog does name it, which limits the damage.
- **Medium (merge).** It is built on 3.0.2. In 3.0.3, `duplicateOrRenameCurrentLayout` moved to
  `LayoutFileActions` and the run gates moved to `RunGates`, so the TrainControlUI half does not apply and must be
  rewritten. It also adds 169 comment lines (most of them the moved delete block, kept verbose, with review ids
  `DW-C1` and `RSA22-C2`), 17 `FR-109` tags and an Adam quote in javadoc. All of that would trip
  `testCommentsStayLean` on the 3.0.3 tree.
- **Low.** The setup-editor path (`stepOutFor`'s autonomy branch, which rebuilds the session under a window that
  stays open) is untested.
- **Low.** After the focus fix it did not rerun the other classes.
- It is the most invasive of the four: the delete handler's tail moved into `deleteTheConfirmedPage`, a `from`
  parameter runs through `duplicateOrRenameCurrentLayout`, and the editor-open guard was made conditional.

**Tests** (`test/regression/testTheEditorManagesPages.java`, 6 claims: add, rename, delete, the menu's own
refusals (taken name, case-only name, declined delete), only-page refusal, and all three items refused while a route
runs)
- Red was shown for 6 of 6. It checks the editor's title, the main window's page, the exact dialog text, the index
  and the page files. It does not check `setup.json`.
- Mutations: skipping the route-run refusal is caught (`:408-414`). Dropping the editor-as-parent change is not
  caught (no claim checks a dialog's owner). The wrong-page case is not caught (no claim covers it).
- Bookkeeping: the window census pin was bumped from 66 to 67 (`testSwitchingToACentralStationLayout.java:979`), the
  "Used by" lists in the `live-snapshot` and `one-page` READMEs were updated, and the class was added to `build.xml`.
  It wrote its own dialog-answering thread, because the 3.0.2 tree has no `support.Answering`.

**To ship:** port the design onto 3.0.3, trim the comments, add a re-select of the editor's page, and rerun. That
is a re-implementation. Its ideas are worth taking; its patch is not.

---

## post-haiku

**Design.** The editor asks the questions itself while open, then hands over:
- a run check through `RunGates` with the editor as parent (`LayoutEditor.java:4143`);
- `parent.promptUserForLayout` (`:4155,:4171`), which was widened from private to package-private
  (`TrainControlUI.java:21935`);
- `parent.refusePageName`, newly extracted from `LayoutFileActions` (`LayoutFileActions.java:499`);
- `parent.askToDeleteLayoutPage`, newly split out of the delete handler (`TrainControlUI.java:22083`).

`handOverPageAction` (`LayoutEditor.java:4210`) then settles unsaved work, tears down, disposes, and runs
`addBlankLayoutPage`, `renameCurrentLayoutPage` or `deleteLayoutPage` with a reopen callback. To pass that callback,
it changed the signature of `LayoutFileActions.duplicateOrRenameCurrentLayout` (`:417`).

**Strengths**
- Refusals and questions come before unsaved work is settled and before the window closes, so a refused or declined
  action leaves the editor untouched. The tests check this.
- `refusePageName` puts the name rule in one place, which is a fair extraction.
- It re-selects the editor's page before Rename and Delete (`LayoutEditor.java:4179,4199`). Delete reopens on the
  page that took the deleted page's place.

**Bugs and weak spots**
- **High (regression, almost certain).** The new `JMenu` and its items are left focusable
  (`LayoutEditor.java:4118-4140`). That is the exact change that turned `ui.testTheEditorNamesItsShortcuts.
  testNothingInTheAutonomyEditorTakesTheKeyboard` red in both pre builds: `[JMenu 'Manage Pages']` in pre-haiku's
  report, and the red pre-sonnet fixed. The test is unchanged in the 3.0.3 tree (`test/ui/
  testTheEditorNamesItsShortcuts.java:135`). post-haiku never ran that class, so its "14 classes green" does not
  cover it. In the app, one click on the menu takes the keyboard from the editor's shortcuts (OB-019, OB-200).
- **Medium.** The dialogs hang off the wrong window while the editor is open: the name prompt, both name refusals
  and the Delete question all use the main window as parent (`TrainControlUI.java:21963,22094`;
  `LayoutFileActions.java:499`ff).
- **Medium-low.** After the window is gone, the callback runs only on success. A cancel at the absent-pages question
  (`TrainControlUI.java:22132`, `LayoutFileActions.java:465`), a failed write, or a run that began between the check
  and the action, leaves the editor closed with no reopen.
- **Low.** `reopenOn` passes `autonomy` as-is (`LayoutEditor.java:4241`). If the setup can no longer be edited, the
  user gets an error and no editor; there is no fallback to the track editor.
- **Low.** The order of the editor's checks differs from the menu's: the run check comes before the prompt, and the
  local-layout check is dropped (harmless, since the editor only opens on local layouts). The name check runs twice:
  in the editor, and again inside `duplicateOrRenameCurrentLayout`.
- Invasiveness: a signature change, a visibility widening, a split delete handler (its long comment block moved,
  not trimmed), and an edit to an existing pin. `testTheRefusalsAreAskedAtTheDoors.java:97-98` now redirects the
  body check to `refusePageName`, anchored on a fragile source string. The editor puts the Layouts menu's sequence
  back together rather than calling its handlers, which is the kind of second copy of the flow that drifts.

**Tests** (`test/ui/testTheEditorManagesItsPages.java`, 5 claims, track editor only)
- Each claim goes through the real Edit button and the editor's Pages menu, with dialogs answered and a folder
  fingerprint for No and for the refused rename. It does not check `setup.json`, and it has no setup-editor claim.
- Mutations: an off-by-one in `reopenNearest` is not caught (the delete claim only asks for "another page", `:193`).
  Leaving the menu focusable is not caught here, though the shortcuts class would catch it. Skipping the editor-side
  name check is caught, because the editor then closes and does not reopen (`:264`).
- Red was shown for 5 of 5, but the failures were not read one by one ("should be the menu lookup").
- Hygiene: it writes `LAST_EDITOR_AUTONOMY_PREF` and never puts it back (`:69`). `testTheTrackEditorByHand` saves
  and restores it. Prefs are isolated per run, but the value leaks to later classes in the same run. It did update
  the `ui/` count in `test/README.md` and `build.xml`.

**To ship:**
- make the menu and items non-focusable and run the shortcuts class;
- hang the dialogs off the editor (a parent parameter for `promptUserForLayout` and `refusePageName`);
- reopen on every exit;
- restore the pref;
- reconsider the pin edit.

That is moderate work. post-sonnet is a better starting point.

---

## pre-haiku (unfinished)

**Design.** It is close to post-sonnet: close the editor, run the action, reopen with a
`Consumer<String> whenDone` threaded through the handlers. To do that it moved all three GEN-FIRST handler bodies
out into new methods (`TrainControlUI.java:28940,29530,29735`).

**Bugs and weak spots**
- **High.** Left red:
  - its own class failed 2 of 5 (rename timed out at 61 s waiting for Edit; add timed out at 120 s on "A page called
    1 - Main already exists");
  - `testTheRefusalsAreAskedAtTheDoors` failed 2 of 13 (the delete door moved, so the declared count is 15 against
    17 found);
  - `testTheEditorNamesItsShortcuts` failed 1 of 29 (`[JMenu 'Manage Pages']` takes the keyboard).
- **Medium.** Like pre-sonnet, it acts on the main window's selected page (`:28944,29349,29532`).
- **Low.** It has no `switching`/`changingPage` guard in `leaveForPageAction` (`LayoutEditor.java:6808`). The track
  path calls `layoutEditingCompleteThen` directly instead of the counted `layoutEditingCompleteFromTheEditor`
  (`:6848`).
- **Tests:** a shared window across the class (`@BeforeClass`), reflection on the private `editLayoutButton`, and a
  hand-written dialog answerer. Per results.md, the JVM once hung for 30 minutes on an unanswered dialog.

**To ship:** not a candidate.

---

## Ranking and the pick for 3.0.4

1. **post-sonnet.** Correct on the 3.0.3 tree, the least invasive, house style followed, and the setup-editor path
   tested. Take this one.
2. **pre-sonnet.** Its interaction design is better (no flash, dialogs over the editor, refusals before teardown),
   but it is on the wrong base, it acts on the wrong page if the main window's list moves, and its comments are
   heavy.
3. **post-haiku.** It works for the common track-editor paths, but it almost certainly breaks the keyboard rule,
   hangs its dialogs off the wrong window, and does not reopen after late failures.
4. **pre-haiku.** Unfinished and red.

### What to fix in post-sonnet before 3.0.4

1. **Ask before tearing down.** In `administerPage`, ask `RunGates.refuseWhileARunHoldsTheSetup(parent, this)`
   before `settleUnsavedWork()`. That removes the double dialog and the closed editor. For Delete, the only-page
   refusal and the Yes/No question could also come first. Taking pre-sonnet's approach is better still: keep the
   window, skip the editor-open refusal only for the asking editor, and hang the dialogs off the editor. This is a
   decision for Adam: is the close-and-reopen flash acceptable?
2. **Close the hole in the callback.** Wrap the handler call in `administerPage`'s `run` so that a
   `RuntimeException` still fires `pageActionSettled()`, or clear the field in a `finally` there when no
   asynchronous continuation is pending.
3. **Add claims:**
   - a route running while the editor is open refuses all three items, and the editor survives (copy pre-sonnet's
     `testTheEditorsItemsRefuseWhileARouteRuns`);
   - the main window showing another page, so Rename and Delete still target the edited page (this catches dropping
     `selectLayoutPage`);
   - an unsaved edit answered Save before Rename lands in the renamed page's file, not the old one;
   - show the setup-editor claim red.
4. **Bookkeeping:** run `ui.testTheEditorNamesItsShortcuts` and `ui.testTheTrackEditorByHand`, and put a
   `MUTATION:` note on each claim.

---

## Lessons

### What the compaction and CLAUDE.md seem to have changed (pre vs post, same model)

- **Sonnet:** about 35% fewer tokens (194k against 300k), about 43% fewer tool calls (56 against 98), about 4.4x
  less wall time (much of the gap is slot waits, see the confounds), and a production change about half the size
  (+228/-9 against +430/-109) with lean comments. Quality was about the same: post is better on reuse and fit, pre
  is better on interaction design and refusal coverage. The rule "Do what was asked, no widening" shows: post-sonnet
  widened nothing and changed no signature.
- **Haiku:** the clearest effect. Pre-haiku did not finish at about 360k tokens and about 150 calls, and left five
  red claims across three classes. Post-haiku finished in 265k tokens, 72 calls and 22 minutes with a mostly working
  feature. On the compacted tree, Haiku moves from "cannot do it" to "does it with defects a reviewer must catch".
- **The test helpers are probably the largest single effect,** and they are part of the treatment. The 3.0.3 tree
  ships `support.Answering`, `Components`, `TestStation` and `SourceOf`. The 3.0.2 tree has none of them. Both post
  test classes are nearly the same shape and are built on `Answering`. Both pre builders hand-wrote dialog-answering
  threads, and pre-haiku's hung the JVM.
- **What CLAUDE.md did not prevent:** post-haiku missed the editor's non-focusable convention and did not run the
  class that guards it. The "run only the classes your change can affect" rule is too abstract to reach
  `testTheEditorNamesItsShortcuts` from a change to `LayoutEditor.java`.

### Haiku against Sonnet

- In both trees Sonnet found the existing close-and-reopen idioms (`confirmExit`, `jumpToSquare`, `leaveFor`) and
  mirrored them. Haiku in the post tree invented a hybrid: it asks in the editor and then closes. That cost it a
  signature change, an extraction, a visibility widening and a pin edit, and it partly re-assembles the menu's flow.
- Sonnet read its red failures per test (post), or found a real regression from red (pre). Haiku did not inspect its
  reds.
- Haiku is not cheaper in tokens on this task: post-haiku used 265k against post-sonnet's 194k. It is cheaper only
  per token, and it needs a stronger review.

### Concrete changes for next time

**Briefs.** State acceptance criteria, not solution paths:
- the item acts on the page being edited even if the main window shows another;
- a refused or canceled action leaves the editor as it was;
- dialogs hang off the editor;
- unsaved work is asked about only once the action will go ahead.

Every build missed at least one of these.

**CLAUDE.md (Working here or Tests):**
- "A new control in `LayoutEditor` is `setFocusable(false)` like every other one; run
  `ui.testTheEditorNamesItsShortcuts`."
- "A change to `LayoutEditor` or the page handlers runs `ui.testTheTrackEditorByHand`,
  `ui.testThePageMenuEditsTheLayoutFolder` and `regression.testTheRefusalsAreAskedAtTheDoors`."
- "Never edit an existing pin to make it pass; report it." (post-haiku edited one.)
- "A test that sets a preference puts it back."

**Tooling:**
- *The unanswered-dialog hang:* give every UI test a watchdog. Either `Answering` fails the test and disposes any
  modal still open after N seconds, or `one.sh` sets a per-class timeout that dumps the open windows' titles and
  dialog texts, then kills the JVM, so a builder sees "stuck on dialog X" in minutes, not 30.
- *Which classes a change affects:* a helper, for example `docs/tools/affected.sh <file>`, that lists the test
  classes naming the changed file or its classes, so the run set is not left to judgement.
- *Keyboard guard:* widen the guard in `testTheEditorNamesItsShortcuts` from the autonomy editor to the track editor
  too, if that is cheap.

**Experiment design:**
- Run each cell at least twice before drawing model conclusions.
- Give both trees the same test helpers if the question is the code compaction alone.
- Give each worktree its own test slot, or run the cells one after another.

### Confounds

- One run per cell, so model and tree effects cannot be separated from run-to-run variance.
- The bases differ in more than compaction: 3.0.3 also moved code (`LayoutFileActions`, `RunGates`) and added the
  test helper library. A better result on post may come from the helpers, the smaller files, or CLAUDE.md, and this
  experiment cannot say which.
- The machine was loaded and test slots were contended. The pre trees ran with a back-ported runner: `docs/tools/
  one.sh`, `battery.sh` and `wait-for-slot.sh` are modified and uncommitted in both pre worktrees, with identical
  timestamps, so this was the setup, not the builders. That runner held every slot. Wall times for the pre builds are
  inflated (pre-sonnet reported about 30 minutes of slot waits), and token counts may include polling.
- Pre-haiku was stopped by decision, not by failure. Its score is a floor.
- The post builders ran fewer bookkeeping classes in some areas (shortcuts) and more in others (`testCommentsStayLean`,
  `testEveryTestIsInTheBattery`). Their green claims cover different sets.
