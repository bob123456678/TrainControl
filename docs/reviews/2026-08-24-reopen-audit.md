# Reopen audit: the last two days' dispositions, tested as claims

**Status:** open

**Prefix:** `RA` — cite findings from here as `RA-C1`, `RA-D7`, and so on. Taken elsewhere and not
reused here: `DD`, `FR`, `FV`, `GC`, `IR`, `ISD`, `LT`, `AR`, `FBR`, `FSR`, `IAR`, `DR`, `SV`.

**What was reviewed, and when.** The tree at `5ad62001` ("The second validation: two of nine did not
hold, and the deadlock was real", HEAD of `autonomy-diagram-r0`), read on 2026-08-24. The brief was
Adam's sentence: *"see if there are any items that need to be reopened and fixed."* So this is not a
fresh hunt — it is an audit of dispositions already written. Every finding marked **fixed** in the
2026-08-22 → 2026-08-24 review chain (`FBR`, `FSR`, `IAR`, `DR`, `SV`, `FV`, plus the OB/FR receipts
in `issues.md` and the `fixed unvalidated` entries in `tests.md`) was treated as a claim to be tested
against the code, with the hardest look reserved for anything fixed more than once.

**Method.** Read-only on `src/`, `test/` and `docs/` — no source, test or ledger file was changed;
this document is the only file written into the repository. Things that were **run**, all from the
session scratchpad with mutants compiled outside the tree and shadowed on the classpath:

- `regression.testPageIdsAreDurable` at HEAD — 11 green — and against **two mutants** of
  `AutonomyCompanionStore` (SV-C1's own mutations, re-made independently): all four square-valued
  `HELD_FIELDS` shapes downgraded to `PLAIN` → **1 failure**, at the SV-C1 cross-page caption assert
  (`expected [null] but found [1:3,3]`); `captions` removed from the map outright → **1 failure**.
  Both of SV-C1's measured claims reproduce.
- `core.testHomeStaging` at HEAD — 60 green — and against a mutant of `HomeStaging` reintroducing
  OB-073 (the state-aware `canRest(loc, to, state)` at line 699 replaced with the stateless form) →
  **1 failure**, and it is the FR-001 assertion inside `applyPlan` (testHomeStaging.java:2370,
  "sends a locomotive into a station that is held back while HS D is occupied"), not the move-count
  check. FSR-C1's disposition and MT-165's "mutation-checked" claim reproduce exactly.
- `regression.testEditorSurfaceRules` at HEAD — 12 green, under the current CRLF working tree.
- `regression.testTimetableCapture` at HEAD — 3 green (OB-088).
- `py -3 docs/manual-tests/triage.py verify-ledger` — clean, exit 0.

**Working-tree note.** At the time of reading, `cs2_sample_layout/config/autonomy/*` and the two
ledger files were modified (test churn and triage comments), and five jars under `resources/` were
untracked. `src/` and `test/` matched HEAD exactly, so every code claim below is about `5ad62001`.

---

## What should be REOPENED

This list is the answer to Adam's question. One item.

| Item | Where its disposition says fixed | What is still wrong |
|---|---|---|
| **FSR-C7** | [2026-08-24-fable-second-round.md](2026-08-24-fable-second-round.md) Dispositions — "Fixed. Each of the two comments describes its own site" | The fix **swapped the two comments**. Each now describes the other site; both are false where they stand. Details in RA-C1. |

**Everything else checked came back genuinely fixed.** In particular, all six twice-fixed chains the
brief named — `FBR-B1`→`FBR-B2`, `FBR-C3`→`FBR-A1`, `FBR-C4`→`FBR-A2`/`B3`/`C7`, `DR-B7`→`SV-A1`,
`DR-B4`→`SV-B1`, `DR-A1`→`SV-C1` — hold at HEAD, each verified against the code and, where a
mutation claim was made, by re-running the mutation independently (see D). The reverts (`FBR-C4`'s
filters, `DR-B7`'s synchronization) left nothing behind: no orphaned helper, no dangling reference
to the deleted picker test, no test pinning the reverted behaviour. FSR-C7 is the third mistake in
its own chain — a comment about comments, wrong in the fix for a wrong comment — and it is C
severity because the code the comments sit on is correct and the class runs green.

Three further C findings below (RA-C2 to RA-C4) are residuals and record-keeping gaps found in
passing, not overstated dispositions: nothing in them says "fixed" anywhere, so nothing is reopened
by them. They are here so they are decisions rather than surprises.

---

## A — high. Wrong behaviour on the layout, or data silently lost.

None found. Every A-severity disposition from the last two days (FBR-A1, FBR-A2, IAR-A1, IAR-A2,
SV-A1, DR-A1, FV-A1, FV-A2, ISD-A1–A3) was verified against the code and holds; the receipts are in
D.

---

## B — medium. Incorrect results, or crashes in specific configurations.

None found.

---

## C — low. Cosmetic, dead code, or narrow edge cases.

| | Finding | Status |
|---|---|---|
| **RA-C1** | FSR-C7's fix swapped the two comments: each now describes the other site | Open — **reopen FSR-C7** |
| **RA-C2** | A carried-through blocked-points entry with no name renders as a blank check box, and the picker has no automated guard left | Open |
| **RA-C3** | SV-B1's residual: the delete path still destroys the page file and the setup record before the index throw can fire, for a genuinely locked index | Open |
| **RA-C4** | Adam's triage notes on MT-159, MT-160 and MT-163 have no receipt anywhere | Open |

### RA-C1 — the FSR-C7 comments are swapped, and the disposition says the opposite

**Where.** `test/regression/testEditorSurfaceRules.java` lines 158–163 (inside
`testTheFacingSubmenuIsBuiltOnce`) and lines 704–709 (inside
`testTheCaptionItemsNameTheStationTheyAreAbout`), written by `f1befe72` acting on FSR-C7.

**What is wrong.** FSR-C7 found that one comment ("two of the rules below bound their windows on a
newline followed by the closing brace") had been pasted at both `\r`-strip sites, describing only
one of them. The disposition reads: *"Fixed. Each of the two comments describes its own site; the
second says it is consistency rather than a fix."* The fix wrote the corrected sentences **at the
wrong sites**:

- The **first** site's comment now says "the rule below bounds its window on a newline followed by
  the closing brace ... was red on a fresh clone (FBR-C8)". Nothing in
  `testTheFacingSubmenuIsBuiltOnce` bounds any window — it counts occurrences of `menuFacingGroup`
  with an `indexOf` loop, and it was never the test that went red. The strip there is the
  prophylactic one.
- The **second** site's comment now says "Nothing here bounds a window on a brace, so this is
  consistency rather than a fix". Thirty lines below it, line 736 is
  `int ends = source.indexOf("\n    }\n", at);` — exactly the brace-bounded window, and exactly the
  line FBR-C8 was raised against. The strip there **is** the FBR-C8 fix; without it this test is
  red on a CRLF checkout. There is exactly one such window in the whole file, and it is at this
  site (verified by grep: one `"\n    }\n"`, at line 736).

Each sentence is true of the other site and false of its own. The behaviour is right at both sites
(`\r` stripped both places; 12/12 green under the current CRLF tree), so this is C — but a
disposition marked fixed that describes the opposite of the code is precisely what this audit was
asked to find, and it is the third mistake in this chain (FBR-C8's fix carried a pasted comment;
FSR-C7 caught the paste; the FSR-C7 fix inverted it). FSR's own closing paragraph — "a sentence
written from what the code looked like it did" — recurred inside the fix for it, again.

**How it was verified.** Read both tests end to end at HEAD; grepped the file for every
`"\n    }\n"` bound (one, line 736) and every `.replace("\r", "")` (two, lines 163 and 709);
confirmed against `git show f1befe72` that the swap is what that commit wrote, not later drift.

**What should be done.** Swap the two comments' content (the FBR-C8 history and window sentence to
the caption test; the consistency sentence to the facing test). Two-minute fix; the reopen matters
more than the wording.

### RA-C2 — the unnamed carried entry, and a picker with no guard

**Where.** `src/org/traincontrol/gui/AutonomyEditorPanel.java` `promptBlockingPoints` — the
carried-entries loop (`for (TileKey held : already) ... choices.add(held)`) against the check-box
construction `new JCheckBox(session.getStore().getPointName(tile), ...)`.

**What is wrong.** Two small residues of the FSR-C5 fix, which is otherwise the right shape and is
verified sound in RA-D4:

1. The loop exists precisely to offer "a square that has since lost its name" — and for such a
   square `getPointName` returns null, so the check box renders with **no text at all**: a ticked,
   blank box the user cannot identify and is being asked to keep or remove. `describeTile(...)` is
   the method built for exactly this fallback (the same file's caption rule asserts it is used for
   the same reason), and it is not used here.
2. Since `testTheBlockedPointsPickerOffersOnlySquaresThatResolve` was deleted with the FBR-B3/C7
   revert (correctly — it pinned the reverted filters, FBR-D20), **nothing automated pins any of
   the picker's rules**: not OB-083's self-caption exclusion, not FSR-C5's stored-entries-offered
   rule. Store-level `setBlockingPoints` behaviour is tested; the dialog's offer/carry logic is
   not. Recorded so the next regression there is not invisible; a source rule in
   `testEditorSurfaceRules` in the shape of the caption rule would cover both.

**How it was verified.** Read `promptBlockingPoints` end to end; confirmed `getPointName` is a
plain map lookup that returns null for an unnamed square; grepped `test/` for any reference to the
picker (four store-level classes reference `setBlockingPoints`/`getBlockingPoints`; none reads the
dialog or the panel source).

### RA-C3 — SV-B1's disposition does not name the residual it left

**Where.** `src/org/traincontrol/gui/TrainControlUI.java` 18918–18956 (the delete path), against
`LayoutDiagram.writeLayoutIndex`'s refusal at 1052–1061.

**What is wrong.** SV-B1's finding had three parts: the permanent refusal for a non-UTF-8 index
(fixed — the ISO-8859-1 fallback is present and total), the misleading "try again in a moment"
message (now true, because only genuinely unreadable files reach the throw), and **the delete path
throwing after the page file is gone and the setup has forgotten it**. The disposition says
"Fixed." without qualification. The third part is narrowed, not gone: for an index a sync client
genuinely holds locked at the moment of a page delete — the code's own comment calls that "an
ordinary Tuesday" here — `deleteLayoutFile()` and `deletePage()`+save still run before
`writeLayoutIndex` throws, so the index keeps naming a page whose file and settings are gone, and
`layoutEditingComplete()` never runs. Materially better than before SV-B1 (the failure now surfaces
in the `errorSavingLayoutWithMessage` dialog rather than silently, and a retry genuinely can fix
it; the phantom row is dropped by the next successful index write), and the residual state is a
harmless stale row rather than a loss — which is why this is C and not a reopen. But the residual
was part of the finding and is not part of the disposition, and the next reader of SV-B1 will
believe the delete path was made safe end to end.

**What should be done.** One sentence in the SV-B1 disposition (or a comment at 18918), or reorder
the delete path to write the index before deleting the file — the ordering comment at 18913 argues
file-first against a *failed delete*, which is a different failure from a failed index write and
does not preclude index-first.

### RA-C4 — three triage notes from Adam with no receipt

**Where.** `docs/manual-tests/tests.md` — MT-159, MT-160, MT-163, each carrying an
"**Adam, 2026-08-24 (triage).** Works, with notes." comment against build `b1e22b5b`.

**What is wrong.** The same triage pass produced OB-087, OB-088 and FR-019, and commit `67ce9f84`
("Adam's three tickets") picked all three up. The notes riding inside these three entries were not
picked up and have no ref anywhere:

- **MT-159**: the archive should carry the layout folder's own name (it currently zips the folder
  as literal `config` — verified still true at HEAD, `state.put("config", ...)` at
  TrainControlUI 15855); and the open question of what backup should do on a CS2-hosted layout
  (skip, or download from the CS2 — "the latter would be preferred if serviceable").
- **MT-160** and **MT-163**: the FR-017 window's font should match the application standard, with
  the could/never-choose headings bold.

The entries stay `fixed unvalidated` in the ledger, so they are not invisible — but the convention
everywhere else in this ledger is that an Adam note either moves the disposition or becomes a
filed item, and these did neither. Three actionable requests currently live only inside comments of
entries whose dispositions say nothing is pending beyond a hands-on run.

**What should be done.** File them (one FR for the archive naming + CS2 question, one OB or FR for
the window styling), or answer them in the entries' comments so the record shows they were seen.

---

## D — not defects. Every disposition checked and found to hold.

With this many claims audited, this section is the substance of the report: it is the list of the
last two days' work Adam can rely on. Order follows the brief — the twice-fixed chains first.

| | What was checked | Verdict |
|---|---|---|
| **RA-D1** | Chain FBR-B1 → FBR-B2: the staging impossibility scan | Holds |
| **RA-D2** | Chain FBR-C3 → FBR-A1 (+ FSR-C6): the start-up latch ordering and exits | Holds |
| **RA-D3** | Chain FBR-C4 → FBR-A2/B3/C7 → FSR-C5: the blocked-points picker | Holds |
| **RA-D4** | The FBR-C4 and DR-B7 reverts left nothing behind | Clean |
| **RA-D5** | Chain DR-B7 → SV-A1: getPoints/getEdges back to live views | Holds |
| **RA-D6** | Chain DR-B4 → SV-B1: the index refusal and the charset fallback | Holds (residual: RA-C3) |
| **RA-D7** | Chain DR-A1 → SV-C1: HELD_FIELDS and the strengthened test — **mutations re-run** | Holds, measured |
| **RA-D8** | IAR-A1 + SV-B2: the page-id floor, at the mechanism and at the seam | Holds |
| **RA-D9** | IAR-A2 + IAR-B1: the backup's two flags and the archive's contents | Holds |
| **RA-D10** | IAR-B2: getActiveAccs off the monitor | Holds |
| **RA-D11** | The inverted test `testAHomeHeldBackByAnOccupiedPointStillGetsAnExecutablePlan` — **mutation re-run** | Guards, for its stated reason |
| **RA-D12** | The strengthened test `testASaveWhileAPageIsAbsentLosesNothingOfIt` — **mutations re-run** | Guards, for its stated reason |
| **RA-D13** | The `testEditorSurfaceRules` source rules after FBR-C8/FSR | Can fail, in charter |
| **RA-D14** | DR-B3(1), DR-B9: the FR-001 reason in the window, and the two new log lines | Holds |
| **RA-D15** | FBR-C1, C2, C5, C6: the four small Fable-round fixes | All hold |
| **RA-D16** | FSR-C2, C3, C4: the corrected record claims | All accurate |
| **RA-D17** | OB-072, OB-088, OB-071 spot checks | Hold |
| **RA-D18** | The five open tickets: OB-084, OB-085, OB-086, FR-013, FR-018 | All still accurate |
| **RA-D19** | Ledger and document hygiene | Clean |

### RA-D1 — the scan, third state, verified

`HomeStaging.plan()` (lines 300–357): the impossibility scan now uses only the stateless
`canRest(l, home)` plus `isActive` and `connected` — no occupancy anywhere, so its "impossible by
construction" claim is true of every test it makes. `couldEverRest` and `heldByAnImmovable` are gone
without residue (no code references anywhere; the only mentions are the explanatory comment at the
scan, which correctly narrates both wrong attempts and cites FSR-C3's cycle-case correction, filed
as OB-085). The state-aware `canRest(loc, to, state)` survives exactly once, inside
`firstClearRoute` at line 699, asked of the evolving state. `testABlockerWithAHomeOfItsOwnIsNotAProofOfImpossibility`
carries the FBR-D19 precondition assert (`getBlockedBy().size() == 1`, line 1586) as its
disposition claims.

### RA-D2 — the latch, third state, verified

`MarklinControlStation` 3703–3813: `theUI.setViewListener(model, null)` (the latch is not passed);
`built.set(true)` is the statement after it; the lambda's `finally` `countDown` is the **only**
release (confirmed: `TrainControlUI.setViewListener`'s own release is null-guarded at 5408 and
receives null); `if (!built.get())` exits **1**; and the FSR-C6 sibling catch also exits **1** with
a comment naming why. The ordering hazard FBR-A1 measured cannot recur: the sole release now
happens after the write on every path.

### RA-D3 — the picker, third state, verified

`AutonomyEditorPanel.promptBlockingPoints` (2596–2738): `already` is read **first**; the only
filters on offerable squares are OB-083's originals (the station itself, and a caption square about
*this* station) — the FBR-C4 widenings are gone, with the three reasons (false premise, captions
are real track, data destruction) written into the comment; the self-caption filter carries the
FSR-C5 exception (`&& !already.contains(tile)`); a second loop puts every stored entry the offer
loop missed onto the list; the empty-`choices` early return can therefore no longer hide anything;
and `chosen` is built from the boxes alone — no invisible carry — so OK can neither delete what was
not shown (FBR-A2's fault) nor keep it unremovable (FSR-C5's fault). `setBlockingPoints` still
refuses a self-blocker on write, so a hand-edited self-entry offered by the carry loop cannot
survive an OK.

### RA-D4 — the reverts are complete

FBR-C4: no reference to `testTheBlockedPointsPickerOffersOnlySquaresThatResolve` anywhere in `src/`,
`test/` or the ledgers; no `getTiles()`-based filter residue in the picker; no orphaned message key.
DR-B7: no `synchronized` on `getPoints`/`getEdges`; no copy; no test pinning the synchronized
behaviour (DR-B7 was never given one). FBR-A2's carry loop is gone, replaced rather than stacked,
and the comment at the write site records the replacement.

### RA-D5 — the revert and its reasons

`Layout.getEdges`/`getPoints` (4987–5002) return the live `values()` views, and the javadoc above
them now carries all three reasons in order — the EDT freeze, **the AB-BA deadlock as the one that
matters** (updateVisiblePoints/repaintAutoLocList hold the UI monitor and would take Layout's;
configureAndLockPath holds Layout's and reaches the synchronized repaintSwitch; DiagramMonitorDriver
fires the first every tick during a run), and the quadratic `deleteEdge` sweep — plus the honest
statement that DR-B7's hazard is real, unaddressed, and folded into OB-086 with the
concurrent-maps shape named. That is exactly what SV-A1's disposition claims the note now says.

### RA-D6 — the fallback, verified

`readIndexLines` (927–939) tries strict UTF-8 and falls back to ISO-8859-1 on
`MalformedInputException` only — total, so a pre-2026-07-27 `FileWriter` index is read, not
refused. The DR-B4 refusal in `writeLayoutIndex` (1052–1061) stays, is now reachable only for a
file that cannot be *read*, and says so in a comment. `readLayoutIndexIds` records the failure
loudly rather than swallowing it. Residual: RA-C3.

### RA-D7 — HELD_FIELDS and its guard, measured

`HELD_FIELDS` (store 3308–3327) is one map of twelve fields to shapes; the hold
(`withoutAbsentPages`, 3354) and the merge (`sharedFields`, 1518) both iterate it, so DR-A1's
"half-updated lists" failure is structurally gone; the `pages`-record merge (1491–1494) still
precedes it, never over a live id. The SV-C1 test strengthening is in place — the cross-page
caption on a loaded page (`liveCaption`, line 432), and the two held-precondition asserts (472,
476). **Re-measured independently**: baseline 11/11 green; all four square shapes downgraded to
`PLAIN` → 1 failure at line 476 with the SV-C1 message; `captions` removed from the map outright →
1 failure. Both of the disposition's measured claims reproduce from scratch. One deliberate scope
note: `withoutAbsentPages` is still not in `testStoreCollectionsAreHandledEverywhere.SITES` — the
DR-A1 fix chose the map-plus-behaviour shape instead, which the mutations above show has teeth for
the twelve fields the fixture covers; a *thirteenth* collection left out of `HELD_FIELDS` entirely
is caught only when its author extends the durability fixture, which is the ratchet's ordinary
limitation and is recorded in the test's own comment.

### RA-D8 — the floor, at both ends

`writeLayoutIndex(path, list, renamed, floor)` starts `next` at `floor + 1`; the only three
production callers (TrainControlUI 18452, 18816, 18954) all pass `pageIdFloor()`; `pageIdFloor()`
reads **the field** (`this.autonomySession`), never the lazy builder, with the SV-B2 reasoning in
place and a `RuntimeException` guard so a failed floor cannot stop a save. `highestPageIdSeen()`
reads the two id-keyed maps (the right pair; a page named "2" cannot poison it). And the SV-B2
trade — no session, no floor — is narrower than it reads: `refreshAutonomyTabState()` calls
`getAutonomySession()` unconditionally during `setViewListener` (line 5376/2671), so on any local
layout the session field is warm before the first page gesture is possible.

### RA-D9 — the backup, verified

`saveState(boolean, boolean)`: the one-argument overload maps to `saveState(backup, !backup)`, so
all pre-existing callers keep their behaviour; the capture block is gated on `captureSession` with
the IAR-A2 story in the javadoc; the backup thread calls `saveState(false, false)` (15817) — live
files, no session commit — and `state` includes `AUTONOMY_FILE_NAME` (15846, IAR-B1) with the
skip-when-absent semantics stated. The menu item disables itself around the work and re-enables in
`finally`.

### RA-D10 — getActiveAccs

Unsynchronized (Layout 713), with the safety argument in the javadoc: `activeLocomotives` is a
ConcurrentHashMap whose value lists are only ever put and removed whole, and what is given up is
compound atomicity for a warning. Matches IAR-B2's disposition and SV's D5 caller sweep.

### RA-D11 — the inverted OB-073 test, measured

At HEAD: 60/60 green. Under the OB-073 mutation (stateless `canRest` at the `firstClearRoute`
gate): 1 failure, and it is `applyPlan`'s FR-001 assertion — "sends a locomotive into a station
that is held back while HS D is occupied ... which is OB-073, exactly" — at the `applyPlan` call
(line 2370), with the move-count bound now after the replay as FSR-C1's disposition says. So the
test fails for precisely the reason its comments claim, which is what two earlier versions of it
could not do. One pre-existing caveat, already on the books as DR-B2 (inside OB-086): `applyPlan`'s
FR-001 clause has no departing-train exemption, so a legal plan whose move *is* the watched
square's occupant arriving at the held-back station would fail the oracle wrongly. No current
fixture reaches it; it is the fail-loud direction; it stays DR-B2's.

### RA-D12 — the held-save test, measured

See RA-D7 for the runs. Beyond the mutations: the test asserts its own fixture took (the
"Ghost Platform" in-file precondition), asserts the entries are actually **held** (the SV-C1
addition), and its `countIn` walker was read — balanced, empty-collection-safe, and only ever asked
of object/array fields.

### RA-D13 — the source rules

12/12 green at HEAD under a CRLF tree — the class FBR-C8 found red is green in the tree the round
left, both `\r`-strips present. The caption rule can fail for every reason it claims (reasoned per
mutation: reintroducing `menuClearStationHere` → the assertFalse; naming moved out of
`addCaptionItems` → the body.contains; `describeTile` dropped → its assert; and the window bound
asserts `at > 0` and `ends > at` so it fails loudly rather than widening). The facing-submenu count
breaks on any second `menuFacingGroup`. Only the two comments are wrong (RA-C1).

### RA-D14 — the reason and the logs

`blockingOccupantOf` (Layout 3540) is one method with both callers: the enforcement at 1966 and the
explanation at 3610 — the FR-001 reason (`errorDestinationBlockedByPoint`) is reachable from the
FR-017 window whenever the rule is actually in force (`isAutoRunning()`, matching the fence at
1943). Both DR-B9 log lines are live (`warnExcludedLocomotiveNotInDatabase` 6305,
`warnRunLocomotiveNotInDatabase` 6954) and present in the bundles (spot-checked base and de).

### RA-D15 — the small Fable-round fixes

FBR-C1: the `asked != locomotive` guards and the reuse sentences are gone from
`AutoLocomotiveStatus`; the `noPathsNow` half — the part that was ever real — is kept, EDT-only.
FBR-C2: the seven `why*` keys are translated (spot-checked `whyStanding` in de: real German, `{0}`
and `{1}` in place). FBR-C5: `readShared`'s `@param wholeRoot` names the real parameter and draws
the real distinction against the local `root`. FBR-C6: `explainDestinationsGrouped` exists
(synchronized, Layout 3331) with exactly one caller (`whyNotReport`, AutoLocomotiveStatus 657).

### RA-D16 — the corrected record claims

FSR-C2's replacement sentence is accurate: `init()`'s no-argument overload passes `showUI = true`
(third parameter, verified against the signature at 3476), and `testAutonomyPathValidation` /
`testLayoutTiles` pass `true` outright. FSR-C3: the scan comment names the blockedBy-cycle
counterexample and cites OB-085. FSR-C4: MT-157's instructions are back verbatim, the entry is
superseded with MT-165 named, MT-165 carries the corrected instructions, and MT-164's supersession
is by the book — the ledger holds none of the three as outstanding beyond MT-165.

### RA-D17 — spot checks on the round's other fixes

OB-072: `abandoned` is set on both stop paths (Layout 4053, 4114) and `return !abandoned.get()` is
the single exit. OB-088: the capture flag is read off the old layout before `parseAuto` replaces it
and applied to the new one (MarklinControlStation 790/800); `testTimetableCapture` 3/3 green.
OB-071: all four page-key split sites use `lastIndexOf(':')` (store 3733, 3748, 3846, 4199).

### RA-D18 — the open tickets still describe the code

- **OB-084**: the committed `cs2_sample_layout` setup.json is still `"version": 1`, so the
  clean-checkout failure and the battery-order masking it describes are both still real.
- **OB-085**: accurate — the scan comment says exactly what the ticket quotes it as saying, and the
  cycle case is indeed not implemented.
- **OB-086**: every row re-checked against HEAD and still true: the audit has three exemptions and
  no FR-001 one (`auditAgainstRuntime`, 405–468); the runtime clause is fenced behind
  `isAutoRunning()` (1943) while the planner's is not; the `applyPlan` oracle is still the third,
  exemption-less form; `readLayoutIndexIds` and `CS2File` still parse the index separately with
  different corrupt-id behaviour, and `writeLayoutIndex` has no duplicate-id check;
  `pageIsHere`/`pageOf` are still prose-coupled twins; `facingChoices` still carries the third
  inline arrival-sides loop (AutonomySession 3021); the settings matrix still never calls
  `setPageIds`.
- **FR-013**: "not started" is accurate — the store's collections are all still `Map<String, …>`,
  and the `#dx,dy` suffix is still string surgery. The "Done" half (the id/name pun dissolved via
  holding) matches the code.
- **FR-018**: accurate **again**. Its "Nothing is lost and nothing is found" sentence was falsified
  by IAR-A1 (settings were handed to the next new page) and is true at HEAD because of the floor: a
  returning page still gets a fresh id, its settings still wait under the old one, and no later
  page can collect them. The entry does not mention IAR-A1 or the floor; one sentence pointing at
  them would spare the next reader re-deriving why the risk ranking still holds, but nothing in the
  entry is wrong.

### RA-D19 — ledger and documents

`verify-ledger`: clean, exit 0, no duplicate refs; the ledger's 38 outstanding rows match the
entries. The `fixed unvalidated` set in the ledger corresponds one-to-one with work verified above;
nothing sits in `fixed validated` on the strength of a disposition this audit found false.

---

## What was not covered

- Nothing was exercised through the user interface or on the railway. FBR-A1's fix in particular is
  verified by ordering argument and by the showUI-true test classes' existence, not by a hands-on
  start — MT-160 remains the hands-on check.
- `ui.testRenderingCost` was not run (OB-084, filed; fails from clean checkout by design of the
  fixture problem).
- The FV round's seven verifications (FV-D1) were re-read but their subjects (`38ccbfc8`'s doors)
  were not independently re-traced; FV's own pass was adversarial and nothing in the later rounds
  touched those doors.
- `parseAuto`'s handling of a null watching name (FSR-D8's untraced tail) remains untraced here
  too, for FSR's reason: it predates every round in scope.
- The triage.py application itself was not re-reviewed.

---

## Dispositions

*(To be filled by whoever acts on this. RA-C1 is the only reopen; RA-C2–C4 are new small items;
D needs nothing.)*

---

## Dispositions

**Claude, 2026-08-24.** The one reopen and all three C findings acted on.

| | What was done |
|---|---|
| **FSR-C7, reopened** | Fixed properly. The two comments were swapped rather than corrected, so each was true of the other site - which is FSR-C7 again in mirror image, produced by the fix for FSR-C7. The first site counts occurrences and never bounds a window on a brace; the second does, at the `indexOf` a few lines below it. Both now describe themselves, and the second says what happened, because a reader who finds this in six months deserves to know the comment has been wrong twice. |
| **RA-C2** | Noted, not fixed. A carried blocked-points entry with no name renders a blank check box - real, and it needs a stored entry whose square has since lost its name, which is narrow. Folded into [OB-086](../manual-tests/issues.md) with the rest of the picker work rather than fixed alone, because the same paragraph is where the check-side warning belongs. |
| **RA-C3** | Fixed in the record. SV-B1's disposition said "Fixed" without naming what survives: for an index that is genuinely locked, the delete path still throws after the page file is gone and the setup has forgotten it, leaving an index entry for a page that no longer exists. That is now said in [OB-086](../manual-tests/issues.md). |
| **RA-C4** | Fixed, and it was the most valuable of the three. Adam ran MT-159, MT-160 and MT-163 and wrote "works, with notes" on each; the dispositions never moved, so three tests he had already passed sat in his queue, and three notes sat unreceipted. All three are **fixed validated** now, his notes are [MT-169](../manual-tests/tests.md#mt-169) - archive folder name, standard font, bold headings, all three done - and his fourth note, a question about backing up a Central Station layout, is [FR-020](../manual-tests/issues.md). |

### What the audit says about the last two days

Thirty-five claims checked, thirty-four sound. All six twice-fixed chains hold at HEAD, several
re-verified by running their mutations independently rather than trusting the dispositions that
recorded them. The five open tickets still describe the code accurately - including FR-018, whose key
sentence was falsified by IAR-A1 in the middle of the day and is true again because of the id floor.

The one that did not hold was a comment, in a fix for a comment. That is the right shape for the tail
end of a long day, and it is worth noting that it was found by asking specifically "what needs
reopening" rather than by another hunt for defects.
