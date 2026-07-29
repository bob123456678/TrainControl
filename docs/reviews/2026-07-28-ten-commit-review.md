# Ten-commit review - 2026-07-28

**Prefix for citing this document: `TCR`.**

**Version reviewed:** commit `171c735`, branch `master`, working tree clean. **Scope:** the last ten
commits, `bbaca6f..171c735` - the seven "Fix test bugs" commits (the `IR` fix rounds) and the three
"Home loc display" commits. **Reviewed:** 2026-07-28. **No code was changed as part of this review,
and no tests were run** - the author builds and tests in NetBeans. Claims were verified by reading
the enforcing method; one library claim (TCR-C3) was verified against the JDK 8 `src.zip` installed
on this machine rather than from memory.

**Independence:** the code diffs were read and the findings below reached **before** any document in
this folder was opened; the comparison section reconciles them afterwards. The commissioning question
was explicitly "what new errors did the changes themselves introduce", and the comparison section
answers it directly.

Coverage note: the existing record ([IR](2026-07-28-independent-full-review.md)) covers the seven
"Fix test bugs" commits round by round. The last five commits of the window - `d7826d8` (title-case),
`46bdb66` (separator), and the three "Home loc display" commits `413db89`/`aaa30eb`/`171c735` - had
**no prior review coverage**; this pass is their first read.

Findings use the A/B/C/D convention in [README.md](README.md).

---

## Status

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| TCR-C1 | The busy guard protecting the four EDT entrances into `repaintTimetable` cannot see a manually launched path's configuration window, so the EDT can block on the Layout monitor for the length of a path configuration - the premise the fifth `IR` round used to leave those callers alone does not hold, and the method's "never on the EDT" comment is wrong as stated | C | **Open** |
| TCR-C2 | The Polish translation of the new home-locomotive tooltip misspells "ciągłą" as "ciąłą" - a missing `g` the escape validator cannot see | C | **Fixed 2026-07-28** (working tree) - one escape restored, file re-verified ASCII-pure |
| TCR-C3 | Both arrow-button handlers in `GraphLocExclude` end in `setSelectedIndices(null)`, which throws NPE on every invocation (verified in the installed JDK's source) - pre-existing and invisible because the work completes first and the EDT swallows the throw, but the new double-click path now routes through it | C | **Open** (pre-existing; newly load-bearing) |
| TCR-C4 | The Danish tooltip for the same key reads "Markerér" - neither the indicative ("Markerer") nor the imperative ("Markér"). Raised as assumption 2; the author confirmed the imperative was intended, which makes it a defect | C | **Fixed 2026-07-28** (working tree) - now "Markér", matching the bundle's imperative style ("Simulér", "Skjul") |
| TCR-D1 | Clean checks: the A* fix, the retirement fence end to end, the lock-order re-audit, the home-display styling against the stylesheet, the bundles, the tests, and three suspicions that died correctly | - | Recorded |
| TCR-D2 | Answers round: the author's three answers recorded, the menu gating verified as directed - and the verification overturned this review's own assumption 3 in the code's favour | - | Recorded |

No A or B findings. The ten commits do what their record says they do: every `IR` fix validated here
independently, and the Home loc display feature is clean apart from two translation typos (both
fixed in the answers round) and the latent NPE its dialog change newly exercises.

---

## C. Low

### TCR-C1. The guarded EDT snapshot callers are not actually uncontended

[TrainControlUI.java:15406](../../src/org/traincontrol/gui/TrainControlUI.java) (`repaintTimetable` -
its entry comment: the snapshot is taken "on the calling thread, and never on the EDT"), against its
four guarded callers, all of which run it **on the EDT**: `clearTimetable`
([TrainControlUI.java:11661](../../src/org/traincontrol/gui/TrainControlUI.java)),
`deleteTimetableEntry` (line 14598), `updateTimetableDelay` (line 14632) and `restartTimetable`
(line 14684). The record's position
([IR fifth round](2026-07-28-independent-full-review.md)): a marshalled caller behind the busy guard
"only ever runs when the monitor is uncontended", so it was deliberately left alone.

That premise has a hole, read in the enforcing methods rather than the guard:

1. `executePathInternal` runs `configureAndLockPath` **before** the locomotive is added to
   `activeLocomotives` ([Layout.java:2939](../../src/org/traincontrol/automation/Layout.java) then
   line 2956). Configuration holds the Layout monitor across its per-command sleeps
   ([Layout.java:1688](../../src/org/traincontrol/automation/Layout.java)) - half a second to two
   seconds on an ordinary path.
2. For a **manually launched** path (the double-click in `AutoLocomotiveStatus` - no autonomy, no
   timetable), `running` is never set, so for the whole of that configuration window
   `isRunning()` ([Layout.java:821](../../src/org/traincontrol/automation/Layout.java):
   `running || !activeLocomotives.isEmpty()`) is **false**, and with it `isAutonomyBusy()`.
3. So the guard passes, and the caller's `getTimetableSnapshot()` - `synchronized` - blocks the EDT
   on the monitor the configuration holds. Three of the four callers additionally hold a **modal
   dialog** open between the guard check and the snapshot, so the operator can widen the race window
   arbitrarily: check passes while idle, the confirmation sits open, a double-click launches a path,
   Yes is clicked mid-configuration.

Consequence: the whole UI freezes for up to one path configuration (~0.5-2 s), interaction-triggered
and self-resolving - the same calibration that made IR-C9(b) a C rather than a B, and the reason this
is C too. But it is a **new** exposure: before the IR-B2 hoist these four surfaces never touched the
Layout monitor at all, and the manual-launch window predates everything but was harmless to them.

Two accurate sentences would also need fixing with the code: the entry comment's "never on the EDT",
and the fifth round's "only ever runs when the monitor is uncontended" - both are contradicted by
the trace above.

**Fix shape:** the smallest honest one is inside `repaintTimetable` itself: when called on the EDT,
take the snapshot on a short-lived worker and let the existing `invokeLater` consume it (the method
already does its own marshalling, so callers need not change). Alternatively, close the guard's blind
spot instead - but registering the locomotive in `activeLocomotives` before `configureAndLockPath`
changes semantics several fences depend on, which is the larger and riskier change (`README`: prefer
the smaller fix when the larger one changes behaviour).

### TCR-C2. The Polish tooltip misspells "ciągłą"

[messages_pl.properties](../../src/org/traincontrol/resources/messages_pl.properties), key
`ui.main.tooltip.showHomeLocomotives`, introduced in `aaa30eb`. The value (stored as `\uXXXX`
escapes, per the bundle discipline) decodes to "... ciąłą linią, gdy tam stoi ...". The word
intended is "ciągłą" - solid, as in a solid line; "ciąłą" is a form of "ciąć", to cut. One missing
`g`, three code points in: the escape for it is absent between the ones for "ą" and "ł".

Worth noting why no check caught it: `validate_all.py` verifies escapes, ASCII purity and placeholder
parity - all of which this value satisfies. The July cycle's previous Polish misspelling (`WR-C7`)
was caught by a human read too. Spelling inside valid escapes is exactly the blind spot.

### TCR-C4. The Danish tooltip conjugates a verb that does not exist

Same key, [messages_da.properties](../../src/org/traincontrol/resources/messages_da.properties):
the value began "Markerér stationer..." - the indicative would be "Markerer", the imperative
"Markér"; "Markerér" is neither. Raised in this document's first round as assumption 2 (a
native-speaker question rather than a finding); the author's answer - "imperative is what it
should be" - resolved it into a defect. Now "Markér", which also matches the bundle's established
imperative style ("Simulér", "Aktivér", "Skjul").

### TCR-C3. Both list-move handlers end in a guaranteed NPE, and the new double-click inherits it

[GraphLocExclude.java:274](../../src/org/traincontrol/gui/GraphLocExclude.java) and line 288:
`allowedLocList.setSelectedIndices(null)` / `excludedLocList.setSelectedIndices(null)` as the last
statement of `excludeLocActionPerformed` / `includeLocActionPerformed`.

Verified in the installed JDK's own source (`jdk1.8.0_361/src.zip`, `javax/swing/JList.java`) rather
than from memory: `setSelectedIndices` calls `clearSelection()` and then iterates the array
(`for (int i : indices)`), so a null argument clears the selection **and then throws
NullPointerException**. Every press of either arrow button has always done this; it goes unnoticed
because the move, the sorts and the clear all complete first, and the EDT's default handler prints
the trace to a console nobody sees in a double-clicked jar.

Pre-existing (the handlers are generated-code-era), so not a new error - but `171c735`'s double-click
feature deliberately routes through these handlers "so the move, the re-sort and the selection reset
stay in one place", which makes the defect newly load-bearing: every double-click now also ends in a
swallowed throw, and anything later added after those calls in `moveOnDoubleClick` would silently
never run. The fix is one word at each site: `clearSelection()` - which is also the only thing the
null call ever achieved.

---

## TCR-D1. Clean checks and suspicions that died correctly

- **The A* fix (`IR-C2`) is sound, independently re-derived.** The `Scored` entries are immutable;
  `Integer.compare` removes the subtraction-overflow shape; the stale-entry skip is correct because a
  state's score can only ever *decrease* (relaxation fires only on a cheaper cost, `h` is fixed per
  state), so `polled.score != score.get(key)` exactly identifies superseded entries; and the
  closed-set check preceding it covers equal-score duplicates. The strict `assertEquals(..., 3)` in
  the swap test is safe, not flaky: `misplaced` is admissible **and consistent** (each move relocates
  one locomotive and costs 1, so `h` changes by at most 1 per move), and A* with a consistent
  heuristic is optimal - a three-move swap plan is the unique minimal size.
- **The retirement fence, traced end to end including the case the tests do not cover.** Dispatch
  loop fence, entry fence, and completion wait all read `isCurrentLayout()`; the suspicion that a
  **non-sequential** timetable run's per-entry retry loop (`while (running && !executePath(...))`)
  could spin forever on a retired Layout died on reading the reload handler
  ([TrainControlUI.java:12814](../../src/org/traincontrol/gui/TrainControlUI.java)): a confirmed
  reload calls `stopLocomotives()` on the old Layout *before* the swap, so `running` is false on the
  retired instance and every retry loop exits. The only paths that retire a Layout without that call
  go through the same handler.
- **Lock-order re-audit for the new EDT entrances.** TCR-C1's stall is a stall, not a deadlock: no
  `synchronized` Layout method takes the `activeLocomotives` monitor (checked method by method), so
  `activeLocomotives -> Layout` remains the only order on that pair, and the EDT blocking on the
  Layout monitor always has a holder that progresses. Consistent with the sixth `IR` round's audit.
- **The home-display styling, verified against the stylesheet.** The fallback
  `stroke-mode: plain; stroke-color: #EEE; stroke-width: 1px` is byte-for-byte the `node` default in
  [graph.css](../../src/org/traincontrol/gui/resources/graph.css), so the comment's "stroke is
  uniform #EEE everywhere today" is true, and emitting the default unconditionally is what erases a
  stale teal ring when the pref is toggled off or an assignment is cleared. Hidden nodes are hidden
  via `ui.hide`, not the `invis` class, so no ring can float on a hidden station. The read is
  Point-only on the EDT, as the comment claims - the IR-B2 shape deliberately avoided.
- **The menu work.** The `showHomes` toggle is idiom-identical to `showLengths` beside it
  (ItemListener, negate-and-put, `updateVisiblePoints`); the home-assignment callback's change from
  `updatePoint(p, ...)` to `updateVisiblePoints()` is correct, not cosmetic - assigning a locomotive
  releases it from wherever it was assigned before, so a *second* station's outline changes too. The
  double-click-to-move guards (`locationToIndex` plus cell-bounds check) correctly reject clicks in
  the empty space below the list.
- **Bundles.** Both new keys (`route.ui.errorEditRouteFailed`, the `showHomeLocomotives` pair) are
  present in all eight bundles with valid escapes; `{0}` survives in every locale of the error key.
  `d7826d8`'s title-casing touched English only, which matches the other locales' own sentence-case
  conventions - not a parity break. The literal `’` characters in the new Java comments are safe:
  `source.encoding=UTF-8` in `nbproject/project.properties` (same resolution as IR-D2).
- **The exit-autosave staged-timetable window, re-traced and concurred.** IR-D2 recorded (not filed)
  that an exit landing between `setTimetable(staged)` and `running = true` would autosave the staging
  plan as the operator's timetable. Re-traced at HEAD: the window is the return path from
  `loadReturnToHomeTimetable` plus one uncontended snapshot - milliseconds, no natural trigger.
  Concur with recorded-not-filed. (The exit surfaces at
  [TrainControlUI.java:1132](../../src/org/traincontrol/gui/TrainControlUI.java) and line 9784 are
  also the last two `isRunning()` *guards* outside `isAutonomyBusy` itself; if they are ever swept
  onto the shared predicate, this window closes as a side effect.)
- **The tests, read for shape.** The `testRoutes` id-collision fix is real and the accumulating
  `currentIds.add` closes it; `unusedRoute`'s rejection loops terminate against the ~1000-id space;
  the TestNG `timeOut` on the retired-timetable test is the assertion, as its comment says; the
  snapshot-vs-live test pins exactly the two-accessor contract. One nit, recorded only: the
  `testEditRouteSucceedsWhenNothingIsInTheWay` cleanup deletes both the renamed and the original
  name, one of which never exists - harmless if `deleteRoute` tolerates a miss, which it does.

---

## Comparison against the existing review record

Done after the pass, per the independence requirement.

**The seven "Fix test bugs" commits are thoroughly covered** by
[IR](2026-07-28-independent-full-review.md) rounds one through six, and this pass independently
confirms the record's headline conclusions: the IR-B1 fence is complete (including the
non-sequential retry case the record did not explicitly trace - see D1), the IR-C6/C8 two-accessor
and Layout-flag shapes are correct, the IR-B2 hoist removed the unprompted freeze, and no deadlock
exists in the resulting lock graph.

**Five commits had no prior coverage** (`d7826d8`, `46bdb66`, `413db89`, `aaa30eb`, `171c735`); this
document is their first review. They are clean apart from TCR-C2 and TCR-C3's new exercise.

**New errors made as a result of the changes** - the commissioned question:

1. **TCR-C1 descends from the IR-B2/IR-C9 fix chain.** The hoist created the rule "snapshot on the
   calling thread"; the fifth round's fix desynchronised the method and classified every caller,
   but the classification's safety argument for the guarded EDT callers - "behind the busy guard,
   so the monitor is uncontended" - assumed the guard sees everything that holds the monitor. It
   does not see a manually launched path's configuration, because `executePathInternal` registers
   the locomotive only *after* configuring. This is the cycle's "check whether the surrounding
   machinery already compensates" rule failing in the opposite direction: the compensation was
   credited without tracing its blind spot. The freeze it permits is rarer and shorter than the one
   IR-B2 removed - the fix chain is still a large net win - but the record's "only ever runs when
   the monitor is uncontended" and the code comment's "never on the EDT" are both false as stated,
   and should not be left to calibrate the next reader.
2. **TCR-C2 was introduced with the Home loc display feature** (`aaa30eb`) - a one-letter
   misspelling in a Polish value that every automated bundle check rightly passes.
3. **TCR-C3 is *not* a new error** - the NPE predates the window - but `171c735` made it
   load-bearing for a new feature, which is how a latent trap graduates. Recorded here so the
   graduation is visible.

Nothing else in the ten commits was found to have introduced an error the record does not already
carry. The IR-D2 observations (Graceful Stop re-disable race, exit-autosave window) were re-traced
and remain correctly recorded-not-filed.

---

## Assumptions that needed the author's confirmation

Answered by the author on 2026-07-28; recorded here with the answers rather than rewritten, per the
one-status rule. The prose below each is the question as originally asked.

1. **TCR-C1's severity rests on operator behaviour** - is editing the timetable while a manually
   launched path is configuring a thing that happens on the real layout?
   *Answer: no - and the timetable UI "says (or should say) please wait for all locomotives to
   stop".* Severity stays C, and the answer sets the fix's direction: the intended behaviour for
   that window is the refusal message, not a stall - so closing the guard's blind spot is not just
   a freeze fix but what the surfaces were always meant to do there. Still open.
2. **The Danish tooltip's "Markerér"** - native-speaker question.
   *Answer: imperative intended.* Resolved into TCR-C4, fixed.
3. **The Show Home Locomotives toggle is reachable during a live run** - assumed intended.
   *Answer: "the whole menu grays out, so should be OK as is, but verify this." Verified, and the
   author is right and this review's assumption was wrong* - see TCR-D2.

---

## TCR-D2. Answers round: the menu gating, verified as directed

The assumption-3 verification, read in the enforcing methods rather than assumed from either side's
memory:

- `GraphViewer`'s right-click handler selects the menu's `running` flag with
  `parent.isAutonomyBusy()` ([GraphViewer.java:397](../../src/org/traincontrol/gui/GraphViewer.java)) -
  the consolidated predicate, so the staging **planning window** is covered as well as a live run.
- With `running` true, `GraphRightClickGeneralMenu` builds only the Graceful Stop item and
  explicitly disables the Display Options submenu
  ([GraphRightClickGeneralMenu.java:287](../../src/org/traincontrol/gui/GraphRightClickGeneralMenu.java):
  `submenu.setEnabled(false)`) - the `showHomes` toggle, like `showLengths` beside it, is
  unreachable while anything is busy.
- The point right-click menu (the other path to home-display state) is likewise only constructed
  inside the `!isAutonomyBusy()` branch (line 371).

So this review's first-round assumption - "the toggle is reachable during a live run" - was wrong,
in the code's favour: the display options are correctly gated. Recorded per the withdrawn-suspicion
discipline, since a reader calibrating this document should know the reviewer mis-assumed the menu's
busy behaviour until told to verify it.

The two bundle fixes in this round (TCR-C2, TCR-C4) were applied with literal-string replacement
(no regex, no escape interpretation), and both files re-verified: zero bytes above 127, zero
malformed `\u` escapes. Each typo occurred exactly once; nothing else in either file changed.

## Addressed 2026-07-28

Both open findings fixed; `TCR-C2` and `TCR-C4` were already in the working tree.

- **`TCR-C3`** - `clearSelection()` at both sites.  The claim was checked rather than taken on trust:
  `setSelectedIndices` clears and then iterates the array, so a null argument does the intended work
  and then throws, every time either arrow was pressed.  Pre-existing, but the double-click added in
  `171c735` routes through those handlers deliberately, so it inherited a swallowed throw - and
  anything added after those calls would silently never have run.

- **`TCR-C1`** - the premise really is broken, and the trace holds: `configureAndLockPath` runs before
  the locomotive is put into `activeLocomotives`, and a hand-launched path never sets `running`, so
  `isRunning()` - and `isAutonomyBusy()` with it - answers false for the whole configuration window.
  Three of the four callers hold a modal dialog open across the gap, so the operator sets the width.

  Fixed with the smaller of the two shapes offered: `repaintTimetable` bounces to a short-lived
  thread when it finds itself on the EDT, and the callers are untouched.  The alternative - registering
  the locomotive before configuration - would move a fence several other things depend on.

  The entry comment's "never on the EDT" is now a statement of the requirement rather than a claim
  about the callers, and the paragraph that was wrong about the guard has been replaced with why it is
  wrong.

**A false positive in my own escape check, found by this work.**  The check added last round flagged
`AutoLocomotiveStatus`'s two `\u23F8` escapes - a pause glyph in a string literal, which is the
ordinary way to write a character that cannot be typed portably.  Leaked escapes land in *comments*;
deliberate ones live in literals.  The check now walks the file as a small state machine and looks
only at comment text, verified on a probe carrying all three cases: a leak in a `//` comment and a
malformed one in javadoc are both flagged, and the `\u23F8` literal is spared.
