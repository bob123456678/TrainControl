# 3.0.4 plan

Adam, 2026-10-25: "Put FR-124 into 3.0.4.  Go with the package split proposal and the proposal for the pre-2.7 code
first for my approval, and then the rest of the plan sounds good."  Then: "Sorry, I also meant to put FR-145 in.  Then proceed with the plan."

The details behind most items are in `docs/refactor-3.0.3.md` section 8 ("Next steps, after 3.0.3"); this file sets
the order.  Dev became 3.0.4 at `992ea86b`, after 3.0.3 shipped as master `1aa89b05`.

## Adam's decisions, 2026-10-25

Taken on the approval page (claude.ai/artifact/26JF5A9WtBf6QDRCedLCVL, 58 items; the proposals' data is in the
session's `plan304/` scratch folder).  Then: "Decisions are in.  Autonomously, follow the plan and then run up to 3
iteration rounds on a validator.  Make sure the plan includes trimming comments in the test files.  You should also
trim comments elsewhere in the UI that we haven't trimmed yet."

**Package split** (counts on `4552832e`):
- `gui.widgets`: About, AxisRuler, BusyDialog, HandScrollListener, LoadingSpinner, PositionAwareJFrame,
  QuickFileChooser, RowIcons, StartupSplash, AddressLabel, StationCaption, and UpdateFlow and BackupAction (Adam:
  "Move into widgets."; 19 members made public, `TrainControlUI#DATA_FILE_NAME` the one from before 2.7.0).
- `gui.locomotive`: the 17 locomotive dialogs, menus and icons (`TrainControlUI#fileOf` made public).
- `gui.autonomy`: the 10 prompts, reports and diagram monitor, and RightClickTimetableMenu (Adam: "RightClickTimetableMenu
  should go to autonomy.  Leave the other two, since it's just two classes.").
- `gui.layouteditor`: LayoutEditor and its five helpers.
- Stay in gui: RouteEditorFrame, RightClickRouteMenu, TrainControlUI, and AutonomyEditorPanel (its preferences line is
  left alone).
- Later the same day, asked whether the Editor* classes and the small helpers move too, Adam chose to pin the
  preferences node and move them: "I want the clean way in 3.0.4".  AutonomyEditorPanel keeps its preferences under
  the gui package's node, then moves with EditorMassAssign, EditorPrompts, EditorSignals, EditorTileMenus and EditorWhy
  into `gui.autonomyeditor`.  CaptionVisibility, CoalescedAsk, CoveredTrackView and SettledQuestion go with them where
  the count of members made public stays small.  This runs after the UI comment pass is merged.
  Done: the six moved, with 25 members made public and none from before 2.7.0.  The four helpers stay in gui: each
  is mostly TrainControlUI's (CaptionVisibility would have widened 17 members, one from before 2.7.0, and
  CoveredTrackView 26), and CoalescedAsk and SettledQuestion are shared with classes that stay.
- The move also anchors `AutoLocomotiveStatus`'s `resources/info.gif` on TrainControlUI, makes the four checks that
  list the gui folder recurse, and updates the path-keyed baselines.

**Pre-2.7.0 duplicated code** (cluster ranks on `4552832e`): yes to p1 (the icon cache, "contingent on this not
reducing runtime efficiency"), p3 (the "0x" address parse), p4 (the daily statistics walk), p6 (the popup listeners),
p7 (the CS2 JSON readers), p8 (the focus-timer text field).  No to p2 ("revisit later, seems like a minimal gain"), p5
and p9 to p16.

**Other items:** OB-374, FR-145 and FR-109 as proposed.  FR-124 (Adam: "The restorestate is fine, but we also need to
figure out how to restore layouts.  And we probably need to take a shadow backup so the user can manually revert."):
the restore brings back the layout folder too, and takes a full backup of the current data first, kept where the user
can find it.

**Messages:** the target shape and caps, Details areas for long lists and widths for wide HTML dialogs, log-only text
out of scope with Help and About exempt, `I18n.countForm` for counts before plural nouns, the
`core.testMessagesStayShort` ratchet, the 11 keys shown by pre-2.7.0 code included, and all 22 samples as written.

**Comments:** the test comment pass (section 4) and, in `src/`, every UI file the 3.0.3 passes did not trim (those
were Layout, AutonomySession, AutonomyEditorPanel, LayoutEditor and TrainControlUI).

## 1. For Adam's approval, before other branches open

- **The gui package split proposal.**  A read-only script proposes groups for `org.traincontrol.gui` and counts, per
  group, the package-private members that would have to become public, which of them date from before 2.7.0, and the
  `.form` files and test strings that move with them.  TrainControlUI and AutonomyEditorPanel stay in `gui` (Adam's
  preference nodes are keyed by their package).  Nothing moves until Adam picks the groups.
- **The pre-2.7.0 proposal.**  The duplicated code still standing in code from before 2.7.0 (rerun `dupscan.py`; the
  six same-rule groups were merged in 3.0.3), each with what repeats, where, the change proposed and the claim that
  covers it, plus the other 3.0.4 items below that would change pre-2.7.0 code.  Each goes only on Adam's word.
- Adam's nine open FR-136 regression tests (MT-806 to 808, 811 to 813, 816 to 818), early, so a regression is fixed in
  3.0.4.

## 2. The approved package moves

By script, at the start, before other branches are open: `git mv`, package and import lines, `.form` files, the
`Class.forName` strings in tests and paths in docs, proved by `javac` and by `javap` differing only in package names.

## 3. User-facing

- **OB-374:** Layout > Screenshot offers the Medium size and drops the pixel counts.
- **FR-124:** Restore from Backup in the File menu (its entry lists what to settle when it is built).
- **FR-145:** the last five layouts in the Layouts menu, for easy switching.
- **FR-109:** rename, delete and add pages from the layout editor, from `exp-post-sonnet` with the grader's fixes and
  the window kept open.
- **Shorter messages and tooltips:** measure, then Adam approves the target shape and about 20 samples, then all eight
  languages, then a ratchet.
- **RSA76-C5:** Test a Path reads reach as the setup check does.

## 4. Code health

- The test comment pass (`lean.py`, the 23 longest test files first), then `testCommentsStayLean` counts `test/` too.
- Adam, later the same day: "Check for any other classes you've made that haven't yet had a comment pass, add the
  pass to the plan, and run it."  A survey of `dev` at `5f179da5` found 445 of the 569 Java files were written with
  Claude, and 359 of those had no comment trim in their history.  The pass takes 17 `src` files (JarUpdater,
  CommandRow, StandingTrains, SelfUpdate, ConditionOutline, TrainFit, LayoutExplanations, ThreeWaySwitch,
  TileSelection, AutonomySettingsTab, RouteCapture, BuiltReach, RestoreAction, SessionRuns, CoalescedAsk, DetailsArea,
  PopupListener) and the 278 test files with 30 or more comment lines, in eight Sonnet sets.  The 35 files that open
  branches touch go in a ninth set once those branches are merged; the 29 tests with fewer than 30 comment lines are
  left as they are.  Then the ratchet pins are lowered once, before `testCommentsStayLean` counts `test/`.
  Done the same day: 321 files trimmed (16 src, 305 tests) on dev at `d5c19e79` and `47eb0cb9`, each proved
  comment-only by `comment-strip-compare.py` (bytecode for src, tokens for tests), with the history pin lowered from 120
  to 106 and the long-block pin from 26 to 25.  A few files already under about 15% comments were left as they are.
- The duplicated-code ratchet test (Java port of `dupscan.py`'s tokenizer), and the approved pre-2.7.0 merges.
- behaviour.md split by area, with a short index; citations updated in the same commit.  Done (`606d2c2a`..
  `6d1a8116`): 14 area files in `docs/reference/behaviour/`, `behaviour.md` kept at its path as the index because the
  tracker and old reviews cite it; `docs/tools/behaviour-split-proof.py` shows all 139 tracker ids and 262 of Adam's
  quotes kept.  `testCommentsStayLean` counts `test/` too, under pins of its own (`eb7e4f4a`, then 53 and 3).
- Builder tooling: `newtest.py`, `redgreen.sh`, `env.sh`, a watchdog for a dialog nobody answers, an affected-classes
  helper; the CLAUDE.md lessons from the FR-109 experiment.
- RSA76-C6, C10 and D7: claims driven by the user's gesture, an independent oracle for the reach claim.  Done
  (`9799c527`..`68ceff2a`, `9864e1dc`), except Add Blank Page and Rename during a run: they ask the name before the
  refusal, in code from before 2.7.0, so their claim still calls the handler.
- The JaCoCo rerun, outside the repository.
- Core-package comment pass (Adam: "Do the sonnet comment trim workers for 3.0.4"): the 27 files in `automation`,
  `base`, `marklin`, `model`, `util` and `examples` no trim had reached, plus `HomeStaging` and `LayoutDiagram`; only
  lines written since 2.7.0 by Claude, the scripting API's public javadoc kept as user documentation.  Four Sonnet sets
  in `w-core1`..`4` from `f35e9d89`.
- TrainControlUI split, four more extractions by delegation as in 3.0.3 (sizing on `4d36b0c6`; Adam picked C6, C8,
  C10 and C13 of the 18 candidates).  One class per commit, a battery per phase, in this order:
  - C8 `LayoutMenus`: the layout, diagram and recent-layout menus and the editor's page buttons (779 lines out, 2 from
    before 2.7.0).  Its one widened member from before 2.7.0, `getLocalLayoutPath`, waits for Adam's word; the move
    goes without it if a delegator serves.
  - C6 `DiagramLocGestures`: placing and turning a train on the diagram (471 lines out, none from before 2.7.0).  The
    monitor guard key, `testNoTwoShortcutsShareAKey`'s handler list and `testEditorSurfaceRules`' setFacing writers
    follow it; the delegator keeps the name 13 tests reflect.
  - C13 `EditorWindows`: opening and closing the layout and autonomy editors (492 lines out, 11 from before 2.7.0).
  - C10 `AutonomyLifecycle`, in two steps: the menu and controls mounting, then the session's life (1,476 lines out,
    23 from before 2.7.0, 59 test files call it through delegators).
  - Rules: bodies from before 2.7.0 move with only their receivers requalified; every `synchronized` method keeps the
    window's lock; the preferences node stays TrainControlUI's; no test hooks.  Then one validator round on the split.

## 5. Release

Validator rounds to convergence, a full battery, then `docs/RELEASE.md`.

Not in 3.0.4: the rest of the 3.1.0 list.
