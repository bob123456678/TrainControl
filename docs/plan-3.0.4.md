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
- The duplicated-code ratchet test (Java port of `dupscan.py`'s tokenizer), and the approved pre-2.7.0 merges.
- behaviour.md split by area, with a short index; citations updated in the same commit.
- Builder tooling: `newtest.py`, `redgreen.sh`, `env.sh`, a watchdog for a dialog nobody answers, an affected-classes
  helper; the CLAUDE.md lessons from the FR-109 experiment.
- RSA76-C6, C10 and D7: claims driven by the user's gesture, an independent oracle for the reach claim.
- The JaCoCo rerun, outside the repository.

## 5. Release

Validator rounds to convergence, a full battery, then `docs/RELEASE.md`.

Not in 3.0.4: the rest of the 3.1.0 list.
