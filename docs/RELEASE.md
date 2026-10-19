# Releasing TrainControl

Development happens on `dev`.  `master` is the release: `dev`'s tree less the development records (everything under
`docs/` except `docs/tools`, the three test classes that check those records, and the note beside the guide's
pictures).  `docs/tools/release-tree.sh` computes that tree, so a release never has a merge to resolve.

## Before

1. The validator rounds have converged (up to three), each fix round checked first by one reader per fix, and Adam has
   ruled on what is left open.
2. On `dev`: `IS_PRE_RELEASE = false` in `MarklinControlStation`, and the changelog heading dated, as
   `* v3.0.1 [MM/DD/YYYY]`.  `core.testTheBuildSaysWhatItIs` and `regression.testTheKeyMapReachesTheWholeWindow`
   branch on the flag; run them.
3. Push `dev`.

## The release commit

4. `bash docs/tools/release-tree.sh dev <file holding the commit message>` writes it to the local branch `release`,
   with `master` and the `dev` commit as its parents.  `master` does not move.
5. `git diff --stat dev release` should show only the paths above and three lines of `build.xml`.

## The gate

6. The full battery, from a worktree detached at `release`:
   `TC_SCRATCH=<scratch folder> bash docs/tools/battery.sh > battery.log`.
   Outside the main checkout three classes are always red, because the live layout preference is kept per folder:
   `core.testMockCentralStation`, `core.testRouteInventory`, `regression.testConfirmedGoodState`.  Run those from the
   main checkout on `dev`, with the three record tests the release tree leaves out.
7. Anything changed after the battery: run the classes it touches and the bookkeeping classes, then rebuild the
   release commit (step 4).

## The jar

8. Build it in a throwaway worktree detached at `release`, never in the main checkout (`package-for-store` deletes
   `store\` first): `ant -Duser.properties.file=<NetBeans build.properties> clean package-for-store` with JDK
   1.8.0_361.  `javap -constants` on `MarklinControlStation`: `RAW_VERSION` is the new version, `IS_PRE_RELEASE` is
   false.
9. If Adam builds his own in NetBeans, compare the two entry by entry.  Expect every entry the same, except possibly
   `traincontrol_python_example.py`'s line endings (a fresh checkout converts them).

## Publish

10. On Adam's word: `git branch -f master release`, `git push origin master` (a fast forward), `git branch -D release`.
11. Adam tags `vX_Y_Z` on `master` and uploads the jar.

## After

12. On `dev`: the next version in `RAW_VERSION`, `IS_PRE_RELEASE = true`, and an undated `* vX.Y.Z` heading above the
    release just made.
