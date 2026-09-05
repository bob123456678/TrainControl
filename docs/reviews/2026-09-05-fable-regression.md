# Regression pass five: the ground the fourth pass named and could not reach

**Status:** open

**Prefix for citing these findings elsewhere:** `RG5` (confirmed unused - the prefix census over
`docs/reviews/*.md` has no `RG5-` citation).

**Reviewed:** branch `autonomy-diagram-r0` at `357cdc40`, on 2026-09-05 (UTC). The question is
`RG4`'s, asked over what `RG4` explicitly did not reach: the commits since its review point
`2cef4211` (five, ending in `357cdc40`, which no pass has reviewed), the per-journey command-parity
measurement its D section names as "a stronger property nobody has measured", the `CS2File` half of
`80249ebd` that `VLD` names as not reached, and `compare.py` re-run against the *real* parity data
(`VLD` verified the new direction on synthetic TSVs only). `cs2_sample_layout/` was read and never
written; the working-tree modifications to its two config files predate this pass and are Adam's,
as `VLD`'s housekeeping note also records.

**Baseline:** `master` at `5f0a75e3` (`RAW_VERSION = "2.8.1"`), re-verified this pass - unchanged
since `R28`.

**Method.** Execution first, per the standing observation that reading has repeatedly been wrong on
this codebase. (1) Seven test classes were run through `one.sh` in two batches - **197 tests, 0
failures, 0 skips** - covering every test class `357cdc40` touched plus the autonomy runtime and the
two rendering classes `VLD` left as its only word on `80249ebd`'s unreached halves. (2) A temporary
probe class was compiled against `build/classes` and run: it executed the shipped
`whatALegacyImportLeaves` over **Adam's own 2.8.1 file** (`cs2_sample_layout/config/autonomy_legacy/
autonomy.json`, read only; the session itself opened on a fresh temp directory, the same fixture
shape the shipped tests use) and executed `legacySignalAddress` against the protocol-suffixed names,
which doubles as a freshness proof for the 18:40 `build/classes` (see `RG5-B1`). (3) A python probe
joined the parity harness's PATH rows to both engines' per-edge commands and compared per-journey
command sequences and final states - the measurement `RG4` could not make. (4) `compare.py` was read
in full and re-run against `../traincontrol-parity/out/v2_8_1.tsv` / `v3_0_0.tsv`. (5) Every source
diff in `80249ebd..357cdc40` not already dispositioned by `VLD` was read.

**Test runs (all Failures: 0, Skips: 0).** `core.testAutonomyDiagramSession` 114 by TestNG's own
count (this run's JUnit XML records 115 testcases, 0 failures - the off-by-one is TestNG's summary
versus the XML writer, present in `VLD`'s 114 too, and both agree on zero failures and zero skips),
`core.testParseCS2Layout` 20,
`ui.testTheRebuildIsOnePass` 4, `regression.testEveryTestIsInTheBattery` 4, `core.testAutoLayout`
26, `ui.testDiagramLooksRight` 20, `ui.testTheDiagramPrintsItsCoordinates` 9. The
`%TEMP%/traincontrol-battery.lock` guard was free throughout; `one.sh`'s live-layout fingerprint was
silent on both runs.

---

## Verdict

**Nothing here should hold the release.** The three measurements the predecessor passes could not
make all came back in 3.0.0's favour: per-journey command **order** is provably immaterial at both
revisions, so the set-level comparison `RG4-D4` already captured everything order could have hidden
(`RG5-D1`); the import report, executed over Adam's real file through the shipped code, names every
hand-driven signal including the five `RG4-B1` was filed about (`RG5-D2`); and the repaired parity
harness, re-run on the real data, reproduces the shipped report exactly and prints precisely the
three Adam-ruled freed pairs in its new direction, no others (`RG5-D3`). The unreviewed fix commit
`357cdc40` held up under both reading and execution (`RG5-D4`).

**What is wrong is small but should not be lost.** Adam's own uncommitted triage of MT-273 says the
viewer is fixed and **"The editor and autonomy still has the flicker"** - and the note's
auto-generated footer attributes his test to a September-2 commit, when the `build/classes` he ran
demonstrably carry `357cdc40`'s fixes (proven by executing those classes: the DCC signal parse that
only exists since 18:24 is present in the 18:40 build). Read at face value the note says he tested
pre-fix code, and the still-open symptom would be dismissed exactly the way `VLD-C6` warned
(`RG5-B1`). Below that: one authored aspect of Adam's file that the pairing successor does not carry
and no prior pass filed, because its two signals counted as "paired" at the accessory grain
(`RG5-C1`), and one guard that was widened to count occurrences the same day a second occurrence was
added under a different spelling (`RG5-C2`).

| | |
|---|---|
| **A** | none |
| **B1** | open - the editor/autonomy flicker survives the round that closed the flicker findings, per Adam's own post-fix triage; and the triage note's machine-written footer misattributes the build, so the only record of the symptom reads as evidence against itself |
| **C1** | open - 2.8.1 set Signals 63 and 64 red when a train left TopMainR0 across the junction; the pairing successor reproduces their occupancy reds but has no author for the junction-crossing red, and no prior pass filed it because both signals counted as "restored" |
| **C2** | open - `testTheRebuildIsOnePass` now asserts LayoutGrid empties the panel in exactly one place, in the same commit that added a second emptying spelled `owner.removeAll()` |
| **D1-D7** | the three measurements made and clean, the `357cdc40` review, and the re-verified baseline claims |

---

## B - medium

### RG5-B1 - The flicker Adam reported survives in two of three surfaces, and the only record of that misattributes its own evidence

| | |
|---|---|
| **Severity** | B - the round's stated purpose was Adam's flicker report; his own post-fix triage says two surfaces still show it; and the one place that records this is uncommitted and carries a false provenance line that invites closing it as tested-too-early |
| **Disposition** | fixed - and the finding was right about the provenance line.  The cause was the 120ms grace timer, which `OB-109` left armed when it stopped the immediate hide; a same-size rebuild is now left alone entirely.  Reproduced by probe and pinned by `testARebuiltDiagramIsNotTakenAwayAMomentLater`.  MT-273 carries the account and asks Adam to re-test the two editors. |
| **Confidence** | **Executed:** the freshness half. `AutonomySession.legacySignalAddress("Signal 116 DCC")` returns `116` and `"Signal -5"` returns `null` when run against `build/classes` - behaviour that exists only since `357cdc40` (18:24) - so the classes compiled 04 Sep 18:40, the ones the triage footer names, carry the flicker fixes, and Adam's observation was made against fixed code. **Read:** the triage text itself (`docs/manual-tests/tests.md:15260-15264`, uncommitted working tree) and the candidate mechanisms in `LayoutGrid`. **Not reached:** the flicker itself - no window was opened, and I could not name the mechanism (the two obvious ones are excluded, see below) |

`docs/manual-tests/tests.md:15260-15264`, uncommitted, appended to MT-273:

    **Adam, 2026-09-04 (triage).** Works.

    Track diagram viewer works.  The editor and autonomy still has the flicker.

    *Run against commit 409d4ce8, build\classes, compiled 04 Sep 18:40 - ...*

Two problems, one of them the reason this is a B rather than a note.

**The footer is wrong about which code was tested.** `409d4ce8` is 2026-09-02 - three days and the
whole rendering round before the fixes. Taken at face value, the natural disposition of "still has
the flicker" is *he tested a stale build; the fixes came later* - and MT-273 quietly closes. But the
same footer says `build\classes, compiled 04 Sep 18:40`, and executing those classes shows they
contain `357cdc40`'s work (the digit-scan signal parse, 18:24 the same day). So the observation
stands **against the fixed code**: `VLD-B3`'s and `VLD-B4`'s fixes cured the viewer and, per the
person the round was for, did not cure the editor or the autonomy surface. Whatever writes that
footer resolved the wrong commit, and every one of the 358 footers in the file is now suspect in the
same direction.

**Nothing but this uncommitted note records the open symptom.** The editor and the autonomy surface
are one rendering path - `AutonomyEditorPanel` is "mounted into a container the editor already has"
and draws on the editor's own grid - so this is one defect, not two. The two mechanisms a reader
would suspect first are both excluded by reading: `LayoutEditor.drawGrid` passes a real
`TrainControlUI`, so `showWhenTilesAreReady`'s `ui == null` bail-out is not it; and the placement
rebuild is cache-hit-settled (`OB-109`'s guard), so the hold-back is not it. What actually blinks in
that window I could not determine from source, and per this project's record, would not trust myself
if I had.

**What would close it.** Reproduce on the screen (MT-273 step 1 is the recipe), file the mechanism
as its own finding, and fix the footer generator's commit resolution - or at minimum correct this
one footer by hand so the note stops testifying against itself.

---

## C - low

### RG5-C1 - The junction-crossing reds of Signals 63 and 64 have no author at 3.0.0, and no prior pass filed them because the accessory-grain count said both signals came back

| | |
|---|---|
| **Severity** | C - same family as `RG4-B1` but weaker on every axis: both signals keep their occupancy reds via Adam's own pairings, and the uncovered case is display at an *empty* platform during a departure from TopMainR0 |
| **Disposition** | closed - the finding's mechanism was wrong and its conclusion was right.  Adam, 2026-09-05: **"TopMainR0 -> TopMainPost does not cross signals 63 and 64.  The route set them red to guarantee that the locked stations (TopMainR1 and TopMainR2) cannot accidentally have a train cross them.  In this case, we would want to set a guard for topmainR0 of each of those signals, too."**  So it is not a junction rule: both signals want TopMainR0 as a second owner, on top of R1 and R2.  That is configuration, not code - the pairing UI's only guard is "already on this station's list", and the runtime already holds a signal red while ANY platform it protects is claimed.  Untested until now, and it is the half his repair depends on, so `testOneSignalProtectingTwoPlatforms` pins it. |
| **Confidence** | **Measured** from Adam's 2.8.1 file (`../traincontrol-parity/v2_8_1/autonomy.json`, identical provenance to the file `RG4` measured): the edge `TopMainR0 -> TopMainPost` carries `Signal 63 red` **and** `Signal 64 red`, and the derived setup's only 63/64 commands are four greens on the R1/R2 platform edges. **Executed:** the per-journey probe (`RG5-D1`) confirms no shared parity journey carries the R0 edge - TopMainR0 is a station, not reversing (measured from the file), but no parity train starts there, so the harness structurally cannot see this row. **Read, not verified:** that the pairing runtime holds a signal red only while its platform is claimed - taken from `RG4-B1`'s tracing of `Layout.java:7560-7578` |

At 2.8.1, twelve signals were driven red by hand. `RG4-B1` filed the five with *no* red author at
3.0.0; Signals 63 and 64 were not among them because Adam paired both (`TopMainR1 -> Signal 63`,
`TopMainR2 -> Signal 64` in the derived setup), and the pairing reproduces their main red - held
while the platform is claimed. But his file authors a *second* red for each:

    TopMainR0 -> TopMainPost    Signal 63 red,  Signal 64 red

- when a train departs R0 across the TopMainPost junction, both neighbouring platform signals go
red. A pairing cannot reproduce that: it reds a signal for its own platform's occupancy, not for a
move through the junction in front of it. So after this upgrade, a train leaving TopMainR0 crosses
the junction with 63 and 64 showing whatever they showed before.

**Why only C.** Walked through the cases: if R1 (or R2) is *occupied*, its signal is already red -
the pairing holds it - so the case 2.8.1's belt-and-braces red protected is covered by the
successor. The only divergent case is an **empty** platform showing green while the junction is
crossed, which restrains no train because there is no train. The locks govern the actual movement at
both revisions. This is aspect fidelity at its thinnest - but it is operator-authored data the
migration drops without naming it (the import's signal list names the *signals*, and 63/64
legitimately appear there; what it cannot say is that one of their authored aspects has no
successor), and `RG4-B2`'s corridor precedent says these residue rows are worth one sentence from
the operator each. If Adam says the R0 red was occupancy shorthand, this closes as covered.

### RG5-C2 - The one-emptying assertion was added in the same commit as a second emptying it cannot see

| | |
|---|---|
| **Severity** | C - the code is correct today (the second emptying is guarded by `!swapped` and unreachable in a healthy build), and the test still kills the mutation it names; what is wrong is that its new assertion states a property of the file that stopped being true in the very commit that added it |
| **Disposition** | fixed - the scan now counts `owner.removeAll()` as well as `parent.removeAll()`, and pins one of each.  The guard listed one spelling of the panel and there were two. |
| **Confidence** | **Read**, with the two lines quoted; the test itself was **executed** (green, 4/0/0) which is the point - it is green while its own failure message's claim ("empties the diagram panel in 1 places") is false of the class |
| |

`VLD-C1`'s fix widened `testTheRebuildIsOnePass` to count occurrences
(`test/ui/testTheRebuildIsOnePass.java:196-199`):

    assertEquals(emptiedCount, 1,
        "LayoutGrid empties the diagram panel in " + emptiedCount + " places. ...

but it counts the literal string `parent.removeAll();` - and the same commit's `VLD-C2` fix added a
second place LayoutGrid empties the diagram panel, spelled `owner.removeAll();`
(`LayoutGrid.java:757`), where `owner` is assigned from `parent` (`:892`). The count reads 1 because
the identifier differs, not because the statement is unique. This is the exact "guard knows only
what it lists" shape `VLD-C1` itself was filed about, reproduced one abstraction step up on the same
day: the guard was taught to count, and the census it counts over was out of date before the commit
landed.

No behavioural consequence today: the `owner.removeAll()` branch runs only when a build threw before
its swap (`!swapped`), which is deliberate and correct at reading. But a future reader moving that
branch, or renaming `parent`, gets no help from a test whose message asserts a census it does not
take. The cheap repair is to count `.removeAll();` against the diagram panel however named, or to
assert the `!swapped` guard's presence alongside - either makes the test's sentence true again.

---

## D - not defects: the measurements, the fix-commit review, and the re-verified claims

### RG5-D1 - Per-journey command parity, measured: order is immaterial, final states agree, and the residue is exactly what was already filed

The measurement `RG4` names as unmade ("whether every journey issues its commands in an equivalent
order/grouping to 2.8.1"). Method: the parity harness's PATH rows (both engines executed 2026-09-03
against copies of Adam's layout) give each journey's edge sequence; the two `autonomy.json` files
give per-edge commands; concatenating along the edges, in order, is the order the executor issues
them. Probe output, in full:

- **Order cannot matter at 2.8.1:** across every 2.8.1 journey variant, no accessory is ever
  commanded to two different states within one journey - so there is no journey whose outcome
  depends on command order, and the set-level comparison `RG4-D4` already captured everything.
  The same holds for every 3.0.0 journey.
- **17 journeys exist at both revisions; zero final-state conflicts** - no accessory is driven to a
  different final state by 3.0.0 on any shared journey.
- **Per-journey absences** (2.8.1 commands it on the journey, 3.0.0 never does): Signals 61 and 62
  (3 journeys each - `RG4-B1`'s unpaired residue, closed as Adam's work in progress), Switches 50,
  52, 58 (the `RG4-B2` corridor, Adam-ruled), and Signals 63/64 on six journeys - all six being the
  arrival reds `TopMainR1Pre -> TopMainR1` / `TopMainR2Pre -> TopMainR2`, which are precisely what
  Adam's pairings reproduce at runtime. The one 63/64 aspect that is *not* runtime-covered is the
  R0 junction red, filed as `RG5-C1`.

This closes the fourth pass's open measurement in 3.0.0's favour, with the residue mapping
one-to-one onto findings already ruled on.

### RG5-D2 - The import report, executed over Adam's own file, does what RG4-B1's closure says

`whatALegacyImportLeaves` was run through the shipped code (`build/classes`, freshness proven in
`RG5-B1`) against `cs2_sample_layout/config/autonomy_legacy/autonomy.json`, read only. Six lines
come back; the counts match `RG4`'s aggregates (69 command-carrying edges, 30 lengths, 50 locking
edges, 36 timetable entries), and the signal line reads:

    15 signals were switched by hand in your old setup (numbers 37, 38, 39, 40, 61, 62, 63, 64,
    81, 86, 87, 94, 107, 108, 116).  Signals are now driven only where you pair one with a
    station... any you do not pair will simply stay green.

All five of `RG4-B1`'s work-order signals (37, 39, 61, 62, 116) are named; the list deliberately
errs toward naming the already-paired ones too, as the code half's comment says it must. The
`RG4-B1` closure ("the import now names them") is therefore true **by execution on his data**, not
just on the fixture. One footnote for whoever reads the list against `RG4-B1`: it names 15, not 12,
because it collects every hand-commanded signal including green-only authors (81 among them); that
is the safe direction and matches the stated design.

### RG5-D3 - compare.py, read in full and re-run on the real data: it measures what it claims

Read line-by-line before trusting, per the brief. It measures: destination survival by base name
with facings unioned (the honest normalisation, and its docstring records the two bugs that taught
it); route survival as waypoint sequences reduced to shared vocabulary; concurrency from lock-set
disjointness in both directions since `RG4-C2`'s fix. Nothing it claims is unmeasured; the one
judgement call - the freed-pairs direction not gating the exit code - is now stated in the output
with its reason (`VLD-C4`'s fix, verified present in the run below).

Re-run against `../traincontrol-parity/out/v2_8_1.tsv` / `v3_0_0.tsv` (`VLD` had run it on
synthetic TSVs only): the output reproduces the shipped 2026-09-03 report - every destination
survives, the four reduced `* -> BottomSecondary` / `BottomInner -> Tunnel` rows (Adam's red-signal
ruling, `RG4-D8`), no lost concurrency - and the new direction prints **exactly the three freed
pairs Adam ruled valid** (`RG4-B2` closed), no fourth. Exit code 1, from the four Adam-ruled route
rows, as documented. Also checked while there: `conditions-v2_8_1.tsv` and `conditions-v3_0_0.tsv`
are byte-identical modulo the revision label - route-condition parity holds exactly.

### RG5-D4 - `357cdc40`, the unreviewed fix commit, holds up

All twelve `VLD` fixes were read against their findings and none contradicts its claim; the four
test classes it touched, plus three neighbours, run **197/0/0** at HEAD (this pass's runs, listed in
the header - the first executed TestNG runs any regression pass has managed, `RG4` having been
locked out throughout). Specifics worth recording: the `legacySignalAddress` digit-scan handles the
protocol suffixes and rejects signed input (executed, `RG5-B1`'s freshness probe); the
case-insensitive `modelled` match and the capital-`Version` fixture landed together with the
missing exactly-once assertion; `filterLocList` and `LayoutPopupUI` carry the one-word and
one-deletion repairs `VLD` prescribed; `deleteRoute` gained `duplicateRoute`'s second refresh
behind the same `!wasLocal` guard; and the `singlePass` javadoc now states the serialisation
rationale that is true of the source. The two pieces of genuinely new machinery - the
spinner-ownership fields and the `!swapped` discard branch in `LayoutGrid` - are correct at
reading, with `RG5-C2` filed on the guard that cannot see the second of them.

### RG5-D5 - The `CS2File` half of `80249ebd` is the AC2-C2 sweep, strictly more tolerant than 2.8.1

`VLD` named it not-reached. It wraps `parseMags`'s per-record accessory construction in the same
skip-the-record-not-the-import guard its three sibling parsers already had, with the blast radius
in the comment (one malformed accessory record used to take the whole sync down as "not
connected"). Against the 2.8.1 baseline this is monotone: every file 2.8.1 parsed still parses, and
files 2.8.1 died on now degrade to a logged skip. The new bundle key `acc.invalidCs2Accessory` is
present in all eight message bundles (measured). The `AxisRuler`/`LayoutGrid` half is covered by
`ui.testTheDiagramPrintsItsCoordinates` 9/0/0 and `ui.testDiagramLooksRight` 20/0/0, executed this
pass - the same evidence `VLD` had, now actually run.

### RG5-D6 - Baseline claims re-verified

`git rev-parse master` is still `5f0a75e3`. `TimetablePath.java` still diffs empty against master
(`RG4-D14` holds). The autosave-forced-on block is still at HEAD
(`TrainControlUI.java:930-936`) - `RGN-C1` remains open where it is filed, and remains the one path
by which 3.0.0 rewrites `autonomy.json` for a user who had autosave off.

### RG5-D7 - What this pass did not reach

- **No window was opened.** `RG5-B1`'s flicker is Adam's observation, not mine; its mechanism is
  unidentified, and the two candidate mechanisms I excluded were excluded by reading.
- **The import write-path.** `RG5-D2` executes the *report*; the import itself (store writes,
  gap-filling, `rebuild()`) was last verified by the `ACC`/`AC2` rounds and was not re-executed
  here.
- **The downgrade matrix** (`RG4-D3`) was not re-walked; nothing in `2cef4211..357cdc40` touches a
  serialised format (checked by reading the five commits' file lists - the only serialisation-
  adjacent file is `LayoutDiagram`'s index writer, covered by `core.testParseCS2Layout` 20/0/0).
- **`AC2`'s ground** (CAN path, `MarklinLocomotive`, `CS2Message`) - deliberately not re-walked,
  same as `RG4`.
- **The footer generator** behind the 358 `*Run against commit ...*` lines in
  `docs/manual-tests/tests.md` is not in the repository (nothing under `docs/tools/` writes it), so
  `RG5-B1`'s misattribution could be diagnosed but not fixed from here.

### Housekeeping

Temporary probes were written to this session's scratchpad only (never the repository), run, and
deleted: `RG5Probe.java`/`.class` (the import-report probe), `rg5_cmd_parity.py` (the per-journey
measurement), and a re-run copy of the parity report. Nothing was left in the repository by this pass; no source
file, test, or existing document was edited. The working-tree modifications to
`cs2_sample_layout/config/autonomy/*` and `docs/manual-tests/tests.md` predate this pass and were
left untouched. **Not this pass's:** an untracked `test/ui/probeGrace.java` appeared in the working
tree while this pass ran - it was not present at the start and was not written by me; a concurrent
session's probe, named here so it is not attributed to this one.
