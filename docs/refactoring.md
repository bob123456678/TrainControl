# Refactoring: the standing rules

What lasts from the 3.0.3 plan (FR-136, approved by Adam on 2026-10-18: *"I approve the plan and the change to the
comment rule"*).  The full plan, its measurements and phases are in git: `git show c7166fe9:docs/refactor-3.0.3.md`.

## Rulings

- **Keep every public method on the API classes, used or not** (Layout, Edge, Locomotive, MarklinControlStation; the
  list under Dead code): *"these seem useful to an API user, and we should add examples later"*.
- **Keep anything Adam wrote, and ask before removing it.**  Code first written before 2.7.0 (tag `v2_7_0`, or before
  2026-05-01) may move with only its receivers requalified, but is not changed, removed or widened without his word.
- **Keep `Conversion.convertSecondsToDate` and `ImageUtil.generateImageWithRect`.**
- **Comments:** CLAUDE.md "Comments stay lean" is the rule (it amends the 2026-09-07 "self-contained" ruling; see
  `docs/reviews/README.md`).

## Extracting from a large class

**The method.**  Delegate into a package-private collaborator in the same package: a cluster of methods moves to
`final class X { private final TrainControlUI ui; ... }`, built once by a field of the owner.  Fields stay on the owner
unless no test reflects them.  Each moved method leaves a delegator with its name and visibility.  No superclass chain:
it breaks `getDeclaredField` and hides where state lives.  Subclass only where several copies already share a shape.
Done this way: LocIconActions, TimetableTab, AutonomySettingsTab, LayoutMenus, DiagramLocGestures, EditorWindows,
AutonomyLifecycle.

**What breaks if you do not look.**
- **Source-scanning tests.**  Many tests read source as text (`SourceOf`, `indexOf("private void x(")` up to
  `"\n    }"`), so they depend on the file, visibility, signature text and indentation.  Negative assertions
  (`assertFalse(source.contains(...))`) go silently vacuous when code moves to another file.  A delegator and its body
  must not both match one needle.
- **Reflection by name.**  `getDeclared*` does not search superclasses, and some tests catch `NoSuch*Exception`, so a
  moved member can pass silently there.
- **Guards keyed by file:** the monitor guard's `ALLOWED` (`File.java#member`) and `SURFACES`,
  `testJavadocsAreAttached`' `ORPHANS_BY_FILE`, `testNoTwoShortcutsShareAKey`' `HANDLERS`, `testEditorSurfaceRules`'
  writer lists, `testNoSelfRecursiveWrappers`' `COLLABORATORS`.  Move the entry with the code; never loosen it.
- **.form classes.**  `initComponents`, the variables block and the guarded handler signatures stay in
  TrainControlUI and LayoutEditor.  Handler bodies may shrink to one-line delegations.  Removing a generated
  component needs the GUI Builder (Adam), not a hand edit.
- **Locks.**  A `synchronized` method keeps the window's lock: leave the synchronized delegator, or take
  `synchronized (ui)`; never lock the new class.

**Before and after each move:** `docs/tools/reflection-audit.py` lists, for each member, every test string literal equal
to its name, every reflective call and name-taking helper, every quoted signature fragment in a test that reads `src/`,
and every `catch (NoSuch*Exception)` near it.  Any name whose declaring file changed has its test edited in the same
commit.

## Dead code

- A private member referenced only by its own declaration is a candidate; a non-private one referenced nowhere in `src/`
  is a candidate unless it is on an API class or an interface implementation without `@Override` (listener false
  positives are common: check by hand).
- A candidate whose name appears as a test string literal is kept until that test is read.
- Removing code that asks for a message key can leave the key dead and turn `testEveryMessageKeyIsAskedFor` red: check
  each key has another user.
- **Kept by ruling** (unused, but useful to an API user): Layout.hasOnlyReversingIncoming, hasOnlyInactiveIncoming,
  hasOnlyInactiveNeighbors, hasOnlyReversingNeighbors, getPointById, getEdgeById, getPathLength, waitForS88Reached,
  getPointsInActivePath; Edge.clearConfigCommand, clearLockEdges; Locomotive.goForward, goBackward, getNumPaths,
  getTypes; MarklinControlStation.getLastLatency, getMarklinLocomotives.  The `examples/` classes are API samples
  (`core.testTheApiExamplesRun`).

## Proving behavior is unchanged

1. **Comment-only commits:** `docs/tools/comment-strip-compare.py` compares the comment-stripped token streams and the
   `javap -c -p` output without line tables.  Identical bytecode means no behavior change by construction.  Run the
   bookkeeping classes per commit (javadoc, citations, records count, message keys).
2. **Moves:**
   - `docs/tools/javap-members.py`: a member diff of each touched class shows only the planned moves; the public diff
     of Layout, MarklinControlStation, Locomotive, Point, Edge, model/View and every public gui class is empty.
   - `git diff -- '*.properties'` is empty.
   - The reflection audit above, before and after.
   - The touched tests (`docs/tools/affected.py <base>`), then a battery at the end of the phase.
3. **Bookkeeping in the same commit:** the file-keyed guards above, the window census
   (`testEveryWindowWearsTheIcon`, `testEveryWindowWearsTheApplicationsLook`) if a helper makes a window, build.xml and
   battery membership and "Used by" lists for test splits, `DEAD_CITATIONS` when trims drop dead ids, and
   `catalog-findings.py` so `cited_by` follows moved code.
4. **Mutation spot checks:** apply two or three existing `MUTATION:` notes at the code's new place and see them go red
   (methods, not classes).
5. **Review:** one Opus validator round (`docs/reviews/FANOUT.md`) over the phase's diff.
6. **Manual tests** for each feature whose code moved, one MT per feature.
