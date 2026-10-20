# TrainControl: rules for anyone building here

Java 8 Swing.  The owner is Adam.  These rules apply to every builder, human or agent; a brief may add to them, never
take them away.  This file stays on the development branch (`docs/tools/release-tree.sh` leaves it off master).

## Comments stay lean (Adam approved the rule on 2026-10-18; `docs/refactor-3.0.3.md` section 4)

Agents pay to read every comment line, and most of this code's cost was comment history.  So:

- A comment states the **current** rule and why the code meets it.  It makes sense on its own, ideally in 8 lines or
  fewer.  Write none where the code already says it.
- **No history.**  What the code used to do, which review round found it, who said what on which date, how a bug was
  found: that lives in the tracker row the id resolves to, and in git.  Write the commit message, not a comment.
- **One id per rule** (OB-123, FR-130, MT-456, RSA52-B1).  Never invent one; never delete the last mention of one.
- **Quote Adam only where his words are the rule**, verbatim, without the date.
- A block over 15 lines is a javadoc contract or it is too long.  Javadoc sits directly on its declaration.
- Do not copy the density of the comments around you: older code is still being trimmed.
- In tests, keep what the test asserts and why it matters, the `MUTATION:` note (the production change that turns it
  red), and why a non-obvious wait or fixture was chosen.  Never edit assertion messages to shorten them.
- `regression.testCommentsStayLean` counts history in comments and long blocks; its numbers may only go down.

Good:

    // A train standing across the switch blocks both roads behind it (OB-271).

Not:

    // OB-271 (2026-09-14): Adam reported that a train standing across the switch was sent the other way.  Round 112
    // found the guard only checked one road; RSA61-B2 widened it.  Before that the code used to ...

## Working here

- Java files are CRLF; keep them CRLF.  Message bundles (`src/org/traincontrol/resources/messages*.properties`, eight
  languages) are ASCII only: every other character as `\uXXXX`.
- New UI is hand-written Swing.  Never edit the NetBeans `//GEN-BEGIN`..`//GEN-END` regions or the variables block.
- Never touch `LocDB.data`, `UIState.data`, Adam's preferences, or anything under `cs2_sample_layout/` (his live
  railway).  Never connect to a Central Station.
- Stage files by name (never `git add -A`); never `git stash` (the stash is shared by every worktree).  Work on `dev`
  or a branch of it; moving or pushing `master` is a release and needs Adam.
- Tests: red before green.  Run classes with `TC_SCRATCH=<scratch dir> bash docs/tools/one.sh pkg.ClassA pkg.ClassB`
  (`label:pkg.Class.m1,pkg.Class.m2` for single methods).  "nothing was run" means the machine is busy: retry later.
  A whole run is `TC_LANES=auto TC_SCRATCH=<dir> bash docs/tools/battery.sh`.
- A new test class goes into `build.xml`; read `test/README.md` for the census and "Used by" bookkeeping.
- The tracker (MT-###, OB-###, FR-###, findings) is `docs/manual-tests/triage.db`, read and written only through
  `py -3 docs/manual-tests/triagedb.py`.  Manual-test entries are append-only.
- Docs for users (Readme.md, Automation.md) use no em dashes and no spaced hyphens as dashes.
