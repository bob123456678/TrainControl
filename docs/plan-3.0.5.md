# 3.0.5 plan

3.0.4 was released on 2026-10-26 (`master` `ea873fce`, from `dev` `00d32d3d`).  Adam, the same day: "Bump to 3.0.5.  Do
any battery fixes, JaCoCo, and the cleanup after, with C10 (still target 3.0.5) and the test runner deferred until
later on", then "actually, add C10 and a validator iteration on it to today's list".

## Today, in order

1. The gate battery's one finding: two claims in `testTheImportDoorReadsAnOldFile` read a file from `docs/`, which the
   release tree leaves out.  Fixed by shipping it with the test fixtures.
2. C10, the last of the four TrainControlUI extractions Adam picked for 3.0.4, in two steps: the autonomy menu and
   controls mounting (`mountAutonomyMenu`, `mountAutonomyControls`, `ensureDiagramStrip`, `liveSetupPanel`,
   `setAutonomyDependentTabs`), then the session's life (`getAutonomySession`, `resetAutonomySession`,
   `prepareAutonomyReload`, `unloadAutonomy`, `repairAutonomyLocomotive`, `refreshAutonomyFindings`,
   `refreshAutonomyPrompt`).  Same rules as C8, C6 and C13.
3. One Opus validator round on C10, then its fixes.  Done: step one `500ed626`, step two `59ddfba0` (TrainControlUI
   22,153 to 21,502 lines; `AutonomyLifecycle` 791); RSA82 found no A or B, C1 (a test race) and C2 (a comment) fixed,
   D1-D8 clean.
4. The JaCoCo rerun, outside the repository, so it measures the code after C10.  Done on `b91a2e76`: lines 86.8%
   (81.5% on 2026-10-19), branches 74.4% (70.2%); TrainControlUI 82.3% of its lines.  Report outside the repository.
5. Worktree cleanup.  Done: 130 clean scratch worktrees removed, their branches kept; five with uncommitted edits left.

## Later

Running the window tests inside Windows Sandbox (Adam: "We can do the sandbox idea for the next release").
