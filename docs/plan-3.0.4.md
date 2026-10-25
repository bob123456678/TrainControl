# 3.0.4 plan

Adam, 2026-10-25: "Put FR-124 into 3.0.4.  Go with the package split proposal and the proposal for the pre-2.7 code
first for my approval, and then the rest of the plan sounds good."  Then: "Sorry, I also meant to put FR-145 in.  Then proceed with the plan."

The details behind most items are in `docs/refactor-3.0.3.md` section 8 ("Next steps, after 3.0.3"); this file sets
the order.  Dev became 3.0.4 at `992ea86b`, after 3.0.3 shipped as master `1aa89b05`.

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
