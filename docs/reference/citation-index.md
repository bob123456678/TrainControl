# Citation index

Every review-finding id cited from `src/` and `test/`, with the document it came from and that
document's own words for what the finding was. Built so that `docs/reviews/` can be retired into
git history without the citations in the code becoming dead ends.

**Measured 2026-09-07.** 587 distinct ids, 1564 citations. 28 ids (58 citations) resolve to nothing anywhere
in `docs/`; a further 16 ids (36 citations) are mentioned in passing but have no entry of their own.

How much of this the code actually DEPENDS on - which comments stop making sense without the
documents - is measured separately in [citation-load.md](citation-load.md).

Regenerate with `scan_citations.py` then `make_index.py`, both in
`C:/Users/adamo/AppData/Local/Temp/claude/C--Users-adamo-Downloads-ClaudeProjects/c6c874b0-959b-45ae-a00d-c70a9696e777/scratchpad/`. The first walks `src/` and `test/` for
`[A-Z][A-Z0-9]{1,6}-[A-Z][0-9]{1,2}`, resolves each id against every `.md` and `.txt` under
`docs/`, and writes `citations.json`; the second renders this table.

## How an id is resolved

Two numbering conventions are in use and both are handled. Older documents write the finding id
out in full - `| DR-B10 | ... |`. 60 documents declare a prefix once at the top
(`**Prefix:** `LE``) and then number their findings short - `### A1 - ...`. 271 of the 559 resolved
ids are findable only through that second convention, which is why a naive full-string search
across `docs/` reports roughly three times as many missing ids as there really are. No prefix is
declared by two documents, so a short-form match is unambiguous.

The quoted column is the document's line, copied, truncated to fit. Where the id resolves only to
a passing mention in somebody else's document, the row says `(mentioned only)` and quotes that
mention - those ids have no finding entry to read. Where nothing was found at all the row says
`- not found -`.

## The ids that cannot be looked up

These are the rows that matter, because they are already dead ends today - deleting the documents
takes nothing further away from them. They cluster by prefix rather than scattering: a second
round was run under `RC`, whose document defines A1-A5 and B1-B5 and stops, and `LE2`, `V33` and
`D24` have no declaring document in `docs/` at all.

**Resolves to nothing (28 ids, 58 citations):**

| id | cites | | id | cites | | id | cites |
|---|---|---|---|---|---|---|---|
| `RC-B11` | 8 | | `LE2-B8` | 6 | | `RC-B10` | 6 |
| `RC-A8` | 5 | | `LE2-C16` | 3 | | `RA-A1` | 3 |
| `RC-A12` | 2 | | `V33-C5` | 2 | | `VAL9-B3` | 2 |
| `VAL9-B4` | 2 | | `VAL9-B5` | 2 | | `D24-C3` | 1 |
| `D24-C4` | 1 | | `D24-C6` | 1 | | `F0-F19` | 1 |
| `IND-M1` | 1 | | `LE2-A8` | 1 | | `LE2-B20` | 1 |
| `LE2-B21` | 1 | | `LE2-B9` | 1 | | `LE2-C12` | 1 |
| `LE2-C13` | 1 | | `LE2-C14` | 1 | | `RC-B6` | 1 |
| `RC-B7` | 1 | | `RC-B9` | 1 | | `V33-C12` | 1 |
| `V33-C6` | 1 | |  |  | |  |  |

`F0-F19` is not a finding id at all - it is a Swing tab title in `TrainControlUI`, and the
pattern cannot tell a function-key range from a citation. `F20-F31` is the same thing twice more,
in the mention-only list below.

**Mentioned in passing, no entry of its own (16 ids, 36 citations):**

| id | cites | where it is mentioned |
|---|---|---|
| `RC-A6` | 10 | reviews/2026-09-02-week-of-commits-review.md:532 |
| `RC-B8` | 5 | reviews/2026-09-01-test-suite-review.md:884 |
| `LE2-B7` | 4 | reviews/2026-08-30-release-candidate-review.md:51 |
| `D24-C9` | 2 | reviews/2026-09-02-comments-and-docs-review.md:516 |
| `F20-F31` | 2 | reviews/2026-08-17-unread-half-review.md:132 |
| `RC-A10` | 2 | reviews/2026-09-03-release-review.md:1586 |
| `RC-A11` | 2 | reviews/2026-08-31-day-of-commits-review.md:374 |
| `AD-B7` | 1 | reviews/2026-08-17-disposition-audit.md:210 |
| `AD-B8` | 1 | reviews/2026-08-17-disposition-audit.md:41 |
| `D24-B2` | 1 | reviews/2026-09-01-fanout-index.md:226 |
| `INT-A2` | 1 | reviews/2026-07-cycle-summary.md:142 |
| `SA-C1` | 1 | reviews/2026-08-18-station-arrivals-review.md:550 |
| `V33-C10` | 1 | reviews/2026-09-02-second-validation.md:374 |
| `V33-C11` | 1 | reviews/2026-09-02-fourth-validation.md:435 |
| `V33-C7` | 1 | reviews/2026-09-02-test-suite-review.md:392 |
| `V33-C9` | 1 | reviews/2026-09-02-third-validation.md:364 |

## The table

| id | cites | files | document | the document's own words |
|---|---|---|---|---|
| `RC-A1` | 19 | 3 | reviews/2026-08-30-release-candidate-review.md:42 | ### A1 - a cut whose paste was not the very next gesture lost the setup |
| `DR-B10` | 18 | 11 | reviews/2026-08-24-duplication-robustness.md:147 | \| DR-B10 \| The absent-page rule is enforced four different ways and reported to the user at none of its six doors \| Open \| |
| `LE-A1` | 15 | 2 | reviews/2026-08-30-layout-editor-review.md:29 | ### A1 - a group cut and paste left the setup on the squares it emptied |
| `TSX-B8` | 15 | 10 | reviews/2026-09-03-test-suite-audit.md:56 | \| B8 \| Medium \| Two fixture factories open the sandbox and then do the work that can throw; every one of their eight callers has the `finally`,... |
| `VD11-A1` | 13 | 2 | reviews/2026-09-03-validation-round-three.md:42 | \| **A1** \| `VD10-B2`'s widened rule is still a list of setters, and at least **eight more doors on the same menu** write the setup and never tell... |
| `WK-B1` | 13 | 3 | reviews/2026-08-28-week-review.md:36 | - **WK-B1**: the tail-clearing "guess" that `ff6368bb` deliberately kept for *clearing* weakens the |
| `DR-B3` | 12 | 4 | reviews/2026-08-24-duplication-robustness.md:140 | \| DR-B3 \| The "sendable destination" conjunction exists three times, and the why-window can never name the FR-001 reason \| Open \| |
| `VD10-B2` | 12 | 2 | reviews/2026-09-03-validation-round-two.md:45 | \| **B2** \| Five more doors on the same shared tile menu write the setup and never tell the running layout; the new surface rule can see none of t... |
| `DR-B4` | 11 | 4 | reviews/2026-08-24-duplication-robustness.md:141 | \| DR-B4 \| Two parsers of `gleisbild.cs2`; a swallowed index-read failure renumbers every page; two pages on one id is silent everywhere \| Open \| |
| `DW-A1` | 11 | 3 | reviews/2026-08-24-day-review.md:39 | \| DW-A1 \| A \| AutonomySession.captureFromLayout / LayoutPageEdit \| The rename-path capture refusal discards everything the running layout learn... |
| `LE-C1` | 11 | 3 | reviews/2026-08-30-layout-editor-review.md:300 | ### C1 - shift up and shift left were offered where they refuse in silence |
| `VB-B1` | 11 | 4 | reviews/2026-08-29-c-round-validation.md:87 | \| VB-B1 \| `exportJSONActionPerformed` is not the only Swing dialog left off the event thread - three more in the same file, one of them live and... |
| `DD-A1` | 10 | 5 | reviews/2026-08-22-duplication-and-design.md:79 | \| DD-A1 \| `AutonomyCompanionStore`: eleven collections, fourteen per-collection sites, and the four commits it took to finish adding the eleventh... |
| `LE-B1` | 10 | 4 | reviews/2026-08-30-layout-editor-review.md:189 | ### B1 - shrinking the page stranded station captions outside it |
| `RC-A6` | 10 | 3 | reviews/2026-09-02-week-of-commits-review.md:532 | (mentioned only) **Why I was wrong.** The claim was true of `10694670` and false of the tree. `72234e18` - "RC-A6..B10: |
| `REG6-A1` | 10 | 5 | reviews/REG6-regressions-2026-09-06.md:56 | ### REG6-A1 - a manual send is offered "keep direction" at squares the path turns the train at, and the default drives it off the path |
| `AU-A2` | 9 | 5 | reviews/2026-08-25-autonomous-round.md:73 | ### AU-A2 - an executed route threw switches on track autonomy had locked |
| `DIR-A1` | 9 | 3 | reviews/2026-09-06-direction-and-consistency.md:88 | \| A1 \| A \| The reversal question is put to the operator while the train is still running at line speed, and it keeps running for as long as the... |
| `DR-B6` | 9 | 5 | reviews/2026-08-24-duplication-robustness.md:143 | \| DR-B6 \| The arrival-sides walk and the facing rule each gained a new copy this week (delta on DD-A7/DD-B9) \| Open \| |
| `IND9-A1` | 9 | 3 | reviews/IND9-independent-2026-09-07.md:26 | ### IND9-A1 - a stale `arrivedFrom` outlives its train, and the assign door never writes one |
| `LE-A6` | 9 | 3 | reviews/2026-08-30-layout-editor-review.md:100 | ### A6 - the cut flag went stale through doors A3 did not sweep |
| `RC-A3` | 9 | 3 | reviews/2026-08-30-release-candidate-review.md:91 | ### A3 - an unreadable layout page deleted the sensors that only lived on it |
| `RC-B5` | 9 | 3 | reviews/2026-08-30-release-candidate-review.md:223 | ### B5 - Start could leave the layout "running" with nothing running |
| `TA-B5` | 9 | 1 | reviews/2026-08-24-test-suite-audit.md:67 | \| TA-B5 \| `testMockCentralStation`'s sync-safety block: a timeout assertion that cannot fail, a cumulative counter as proof of a fetch, a vacuous... |
| `DD-A6` | 8 | 4 | reviews/2026-08-22-duplication-and-design.md:84 | \| DD-A6 \| `HomeLocomotiveMenu` lost four of its five callers; two safety warnings are now unreachable and their tests still pass \| Open \| |
| `DD-A7` | 8 | 3 | reviews/2026-08-22-duplication-and-design.md:85 | \| DD-A7 \| The checker re-implements rules the builder enforces; it disagreed with the railway once, and two of the copies have drifted again sinc... |
| `DOC-C24` | 8 | 4 | reviews/2026-08-28-documentation-review.md:825 | \| **DOC-C24** \| Eight smaller comment drifts, listed together \| Open \| |
| `DR-B2` | 8 | 4 | reviews/2026-08-24-duplication-robustness.md:139 | \| DR-B2 \| FR-001 is written three inequivalent ways: runtime, planner, test oracle \| Open \| |
| `FSR-C5` | 8 | 2 | reviews/2026-08-24-fable-second-round.md:71 | \| **FSR-C5** \| A carried-through blockedPoints entry is invisible in the picker and can never be removed through it \| **fixed** \| |
| `LE-A5` | 8 | 3 | reviews/2026-08-30-layout-editor-review.md:78 | ### A5 - a non-rectangular cut moved the setup off squares that were never cut |
| `RC-A2` | 8 | 3 | reviews/2026-08-30-release-candidate-review.md:70 | ### A2 - the routing migration deleted its own source, by the branch LE-B5 did not fix |
| `RC-A9` | 8 | 3 | reviews/2026-08-31-test-suite-review.md:235 | RC-A9 already wrote down and `testAPathDoesNotReleaseAnEdgeItHasAlreadyReleased:707-714` spells out - |
| `RC-B11` | 8 | 2 | - not found - | - not found - |
| `RC-B2` | 8 | 2 | reviews/2026-08-30-release-candidate-review.md:166 | ### B2 - `BALANCED_PRIORITY` inverted below zero |
| `TST-B20` | 8 | 7 | reviews/2026-08-28-test-suite-review.md:360 | \| B20 \| Process-global and fixture state left mutated for later classes \| open \| |
| `AC2-C1` | 7 | 2 | reviews/2026-09-04-independent-acceptance.md:54 | \| C1 \| C \| The index writer and the page parser silently delete what a genuine Central Station export carries that TrainControl does not model:... |
| `DIR-C3` | 7 | 2 | reviews/2026-09-06-direction-and-consistency.md:97 | \| C3 \| C \| `flipFacing` returns at the FIRST placement it finds, so a locomotive recorded on two squares gets no follow at all - not even on the... |
| `IAR-A1` | 7 | 5 | reviews/2026-08-24-independent-application-review.md:44 | `IAR-A1`. Its output is quoted verbatim below. |
| `IND9-B5` | 7 | 4 | reviews/IND9-independent-2026-09-07.md:204 | ### IND9-B5 - the dismissed-paste guard reads three different nulls as one |
| `LE-A7` | 7 | 2 | reviews/2026-08-30-layout-editor-review.md:121 | ### A7 - the whole of A1, A4 and A5 was dead code, and nothing could see it |
| `LE-B5` | 7 | 3 | reviews/2026-08-30-layout-editor-review.md:257 | ### B5 - the routing choice could be applied and never stored, and the migration deleted its own source |
| `LE-C2` | 7 | 2 | reviews/2026-08-30-layout-editor-review.md:315 | ### C2 - clearing a page told the setup nothing |
| `RC-A4` | 7 | 2 | reviews/2026-08-30-release-candidate-review.md:110 | ### A4 - a folder where every page fails no longer fell back to the Central Station |
| `REG8-A1` | 7 | 2 | reviews/REG8-regressions-2026-09-07.md:29 | \| REG8-A1 \| Every build or reload silently erases `arrivedFrom` from every occupied square: `parseAuto` applies it and then `setLocomotive` - car... |
| `REG8-B2` | 7 | 4 | reviews/REG8-regressions-2026-09-07.md:31 | \| REG8-B2 \| The diagram's Edit/Assign-locomotive door records the placement in the running layout only - no `session.placeLocomotive`, no `setFac... |
| `SPEC-A1` | 7 | 3 | reviews/SPEC-validation-2026-09-06.md:57 | \| SPEC-A1 \| A pasted train's heading is never read; the paste records the destination copy's facing \| A \| Fixed \| High on the mechanism, high... |
| `SPEC-B4` | 7 | 4 | reviews/SPEC-validation-2026-09-06.md:62 | \| SPEC-B4 \| The diagram's "is facing" radio writes the setup only - `DIR-B3`'s sibling \| B \| Fixed \| High \| |
| `SV-B1` | 7 | 3 | reviews/2026-08-24-second-validation.md:94 | ## SV-B1. DR-B4's refusal is permanent where the index is not valid UTF-8, and its message says otherwise |
| `SV-B2` | 7 | 2 | reviews/2026-08-24-second-validation.md:148 | ## SV-B2. `pageIdFloor()` builds an autonomy session, which the same method forbids forty lines earlier |
| `WK3-C3` | 7 | 2 | reviews/2026-09-02-week-of-commits-review.md:76 | \| `WK3-C3` \| C \| `AutonomySession.tilesWithAHome()` is a third copy of the same "which squares carry a home" walk \| `AutonomySession.java:3364`... |
| `AC2-A1` | 6 | 2 | reviews/2026-09-04-independent-acceptance.md:53 | \| A1 \| A \| Editing, renaming, or enable/disabling a route drops it from the autonomy "Activate routes" selection; the exit capture persists the... |
| `ACC-C6` | 6 | 3 | reviews/2026-09-04-release-acceptance-review.md:63 | \| C6 \| C \| The undo door discards `saveQuietly()`'s failure result that its sibling door logs \| `LayoutEditor.java:428` vs `:596` \| |
| `CONF-A1` | 6 | 2 | reviews/CONF-confirmation-2026-09-06.md:40 | \| CONF-A1 \| NEW: a compulsory turn is no longer performed on a hand-driven path, and "keep direction" at a turning copy drives a train off its re... |
| `DIR-B3` | 6 | 3 | reviews/2026-09-06-direction-and-consistency.md:93 | \| B3 \| B \| `flipFacing` writes the setup's facing and nothing moves the locomotive between the running Layout's split copies, so the diagram arr... |
| `DY3-C5` | 6 | 3 | reviews/2026-09-02-last-day-review.md:449 | ### C5 |
| `IPR-B2` | 6 | 2 | reviews/2026-08-31-independent-review.md:296 | ### B2 - a condition written with a bracket in a non-leading position is re-read with the wrong operator |
| `LE-B2` | 6 | 3 | reviews/2026-08-30-layout-editor-review.md:11 | B2, C1 and C2. The second, one agent over those fixes and the preceding two days of commits, produced |
| `LE2-B8` | 6 | 2 | - not found - | - not found - |
| `RC-B1` | 6 | 3 | reviews/2026-08-30-release-candidate-review.md:141 | ### B1 - undo after shrinking a page put a station's name back outside it |
| `RC-B10` | 6 | 2 | - not found - | - not found - |
| `REL-A2` | 6 | 3 | reviews/2026-09-03-release-review.md:53 | \| A2 \| A \| The diagram flow silently wipes every placed locomotive's train length, reversibility and function slots on each configuration load:... |
| `RG4-B1` | 6 | 2 | reviews/2026-09-04-regression-sweep-3.0.0.md:75 | \| **B1** \| open, needs Adam - five of the twelve signals 2.8.1 drove red have no red author at 3.0.0; the pairing gesture can restore all five, b... |
| `TA-A1` | 6 | 1 | reviews/2026-08-24-test-suite-audit.md:62 | \| TA-A1 \| The SV-B1 encoding guard never asserts the accented page's id, so a decode-drift regression renumbers every page and reattaches every s... |
| `V31-C2` | 6 | 3 | reviews/2026-09-02-first-validation.md:43 | \| `V31-C2` \| The accessory keyboard is a third hand-switching door, and asks neither half of the question `SVN-B16` moved onto `Layout` "so both... |
| `VAL-B5` | 6 | 1 | reviews/2026-08-29-round-validation.md:274 | ### VAL-B5. Three `YES_NO_OPTS[0]` sites were fixed; six destructive siblings were not |
| `AC3-B1` | 5 | 4 | reviews/2026-09-05-fable-acceptance.md:58 | \| B1 \| B \| A corrupt `LocDB.data` leaks its file handle inside `restoreState`; the keep-aside protection then cannot replace the file, the sessi... |
| `ACC-A1` | 5 | 2 | reviews/2026-09-04-release-acceptance-review.md:54 | \| A1 \| A \| A lock-phase failure is recovered twice: the ruled unconditional unlock in `executePath`'s handler re-releases a path `configureAndLo... |
| `AU-B7` | 5 | 2 | reviews/2026-08-25-autonomous-round.md:284 | ### AU-B7 - a hand dispatch never swept the protecting signals |
| `DIR-A3` | 5 | 4 | reviews/2026-09-06-direction-and-consistency.md:90 | \| A3 \| A \| The emergency-stop carve-out was removed from the midway question and left at the pre-route one, so a manual Cancel discards a power... |
| `DR-B1` | 5 | 3 | reviews/2026-08-24-duplication-robustness.md:138 | \| DR-B1 \| The staging audit lacks an FR-001 exemption and falsely accuses the planner on every FR-001 layout \| Open \| |
| `FBR-A2` | 5 | 2 | reviews/2026-08-24-fable-round-review.md:28 | **FBR-A2**, in the validation pass at the foot of this document. |
| `FBR-C8` | 5 | 2 | reviews/2026-08-24-fable-round-review.md:642 | ### FBR-C8 - `regression.testEditorSurfaceRules` is red at HEAD, on line endings |
| `FR3-B1` | 5 | 2 | reviews/2026-09-04-final-release-review.md:58 | \| B1 \| B \| Deleting an unrelated condition flattens a leading bracketed group, silently turning its `or` into `and`; nothing flags it and Save w... |
| `IPR-B1` | 5 | 2 | reviews/2026-08-31-independent-review.md:243 | ### B1 - "Highlight on Diagram" throws on any route containing a locomotive command |
| `IPR-B4` | 5 | 2 | reviews/2026-08-31-independent-review.md:500 | ### B4 - pressing OK in the crop dialog at full zoom-out allocates an image proportional to the SQUARE of the source |
| `R28-C3` | 5 | 3 | reviews/2026-09-01-regression-vs-2.8.1-review.md:46 | \| **C3** \| fixed - `SHOW_HOME_LOCOMOTIVES` and `HIDE_REVERSING_EDGES_PREF` survive as dead constants; the home assignment is no longer drawn \| |
| `RC-A8` | 5 | 2 | - not found - | - not found - |
| `RC-B8` | 5 | 2 | reviews/2026-09-01-test-suite-review.md:884 | (mentioned only) `(9+1)*1000/18 = 555` and `(0+1)*1000/2 = 500`. `Layout.ratioOf` uses `1000000L` (`Layout.java:335`) since RC-B8. The |
| `SVN-A3` | 5 | 2 | reviews/2026-09-01-week-of-commits-review.md:64 | \| **SVN-A3** \| `layoutEditingCompleteThen` runs its continuation before the refresh it is named after \| open \| |
| `SVN-B11` | 5 | 2 | reviews/2026-09-01-week-of-commits-review.md:377 | \| **SVN-B11** \| A cut consumed by a paste that carried nothing, then a paste back that forgets the setup \| **fixed** \| |
| `SVN-B16` | 5 | 4 | reviews/2026-09-01-week-of-commits-review.md:382 | \| **SVN-B16** \| The plain-accessory tile guard never got the protecting-signal half its route twin has \| **fixed** 2026-09-02 (`87b6c10a`) \| |
| `V31-C3` | 5 | 3 | reviews/2026-09-02-first-validation.md:44 | \| `V31-C3` \| `AutonomyChecks` reachability is blind to `active`, so after `D24-B5` a plain square switched out of service can cut the railway wit... |
| `V36-C4` | 5 | 3 | reviews/2026-09-02-sixth-validation.md:45 | \| C4 \| The new comment claims `canReachAnyDestination` "is what the editor's new copy check reports on". It is a second, independently written pr... |
| `VAL-A1` | 5 | 2 | reviews/2026-08-29-round-validation.md:61 | ### VAL-A1. `tailHasProvablyPassed`'s first clause is the same defect WK-B1 removed, one edge earlier |
| `VAL8-A1` | 5 | 3 | reviews/VAL8-validation-2026-09-07.md:20 | \| VAL8-A1 \| A \| Tail blocking covers one directional edge per hop, and prefers the direction only the standing train itself can use; routes over... |
| `VB-B2` | 5 | 3 | reviews/2026-08-29-c-round-validation.md:88 | \| VB-B2 \| VAL-B5's sweep stopped at `TrainControlUI`; six destructive confirmations still pre-select the destructive answer \| open \| |
| `VD10-A1` | 5 | 2 | reviews/2026-09-03-validation-round-two.md:42 | \| **A1** \| `RC` #3 releases the path *after* `clearedEdges` has been dropped, so with `atomicRoutes` off every edge the tail already gave up is r... |
| `WK3-B1` | 5 | 4 | reviews/2026-09-02-fanout-index.md:85 | \| `WK3-B1` / `DY3-B1` / `D3F-C4` \| three \| the diagram tile asked the protecting-signal rule without the aspect, so setting a signal RED - the p... |
| `AU-C12` | 4 | 2 | reviews/2026-08-25-autonomous-round.md:428 | ### AU-C12 - the Keyboard tab neither warns nor refuses |
| `D24-B1` | 4 | 2 | reviews/2026-09-01-fanout-index.md:97 | \| `D24-B1` \| `HomeStaging.connected` proved journeys impossible using a stricter rule than the executor enforces \| `9f1b80c8` \| |
| `D24-B5` | 4 | 4 | reviews/2026-09-02-comments-and-docs-review.md:480 | `D24-B5`, `D24-C8`, with no `C7` among them. `D24-C7` is a forward reference to a finding that was |
| `D3F-C6` | 4 | 1 | reviews/2026-09-02-three-days-review.md:310 | ### D3F-C6 - `check()` now builds the configuration three times per call, on call paths already documented as expensive |
| `DAY-A1` | 4 | 2 | reviews/2026-08-31-day-of-commits-review.md:31 | ### A1 - MT-165 broke claimHome's injectivity, so two trains can claim one platform and Return Home refuses everything |
| `DD-C10` | 4 | 3 | reviews/2026-08-22-duplication-and-design.md:971 | \| DD-C10 \| The port table exists three times, one of them a Python script the javadoc asks you to hand-edit in step \| Open \| |
| `DIR-A2` | 4 | 2 | reviews/2026-09-06-direction-and-consistency.md:89 | \| A2 \| A \| A hand-driven send whose DESTINATION is a may-reverse point turns the train round without asking - the exact case Adam described. The... |
| `DIR-B1` | 4 | 3 | reviews/2026-09-06-direction-and-consistency.md:91 | \| B1 \| B \| `findPath` never got the `closed` set `reachableTiles` gained, so the editor's path test routes through a closed square while the fin... |
| `DR-A1` | 4 | 2 | reviews/2026-08-24-duplication-robustness.md:69 | \| DR-A1 \| The held-entries mechanism keeps three hand lists of field names outside every guard; a dropped name silently deletes a page's settings... |
| `DR-B7` | 4 | 3 | reviews/2026-08-24-duplication-robustness.md:144 | \| DR-B7 \| `getPoints()`/`getEdges()` are live views read off-thread; the `getHomeStations` fix was not swept to its siblings \| Open \| |
| `DR-B8` | 4 | 1 | reviews/2026-08-24-duplication-robustness.md:145 | \| DR-B8 \| The settings matrix runs with no page index, so the id-translation layer is untested by it \| Open \| |
| `DY3-C7` | 4 | 3 | reviews/2026-09-02-last-day-review.md:541 | ### C7 |
| `FBR-B1` | 4 | 2 | reviews/2026-08-24-fable-round-review.md:36 | \| **FBR-B1** \| The staging impossibility scan turns a movable blocker into a proof of impossibility \| **fixed** \| |
| `FBR-B2` | 4 | 2 | reviews/2026-08-24-fable-round-review.md:514 | ### FBR-B2 - `couldEverRest` still turns a movable blocker into a proof of impossibility |
| `IND9-B4` | 4 | 2 | reviews/IND9-independent-2026-09-07.md:176 | ### IND9-B4 - a manual turn at a may-reverse destination is executed on the track and never recorded on the graph |
| `IND9-C5` | 4 | 1 | reviews/IND9-independent-2026-09-07.md:334 | ### IND9-C5 - two of the new source-shape assertions prove less than their failure messages claim |
| `LE-A3` | 4 | 3 | reviews/2026-08-30-layout-editor-review.md:107 | A3 identified the invariant correctly - `clipboardWasCut` asserts "the origin squares are empty now" - |
| `LE-A4` | 4 | 2 | reviews/2026-08-30-layout-editor-review.md:12 | A4, A5, A6, B3, B4 and C3-C7 - **three of them holes in the first round's own fixes**, which is the |
| `LE2-B7` | 4 | 2 | reviews/2026-08-30-release-candidate-review.md:51 | (mentioned only) other half of the gesture (LE2-B7). |
| `OV2-C3` | 4 | 2 | reviews/2026-09-04-second-validation.md:55 | \| C3 \| C \| ACC-C6's fix was not swept to its third sibling: the page-exclusion write still discards `saveQuietly()`'s answer \| `TrainControlUI.... |
| `R28-C5` | 4 | 2 | reviews/2026-09-01-regression-vs-2.8.1-review.md:48 | \| **C5** \| one restored, one declined - the graph window's keyboard routes to s88 address and home locomotive have mouse-only successors \| |
| `RA-C2` | 4 | 2 | reviews/2026-08-24-reopen-audit.md:82 | \| **RA-C2** \| A carried-through blocked-points entry with no name renders as a blank check box, and the picker has no automated guard left \| Ope... |
| `REG6-B3` | 4 | 2 | reviews/REG6-regressions-2026-09-06.md:234 | ### REG6-B3 - autonomy and Return Home now stop dead at every plain copy of a split may-reverse square |
| `REG6-B4` | 4 | 3 | reviews/REG6-regressions-2026-09-06.md:284 | ### REG6-B4 - the run still stops at every may-turn square after the operator has already said "keep direction" |
| `REL-C4` | 4 | 3 | reviews/2026-09-03-release-review.md:33 | \| C4 \| C \| `clearAllPlacements` pays 2N full station-index re-derives on the EDT - the DY3-C5 cost, un-swept in the twin bulk action added the s... |
| `RGN-A2` | 4 | 2 | reviews/2026-08-31-regression-review.md:40 | \| **A2** \| fixed - the Auto tab is disabled for every user whose autonomy comes from `autonomy.json`, so nothing on it can be reached after upgra... |
| `RGN-B1` | 4 | 3 | reviews/2026-08-31-regression-review.md:41 | \| **B1** \| fixed - `Point:` station captions are stripped out of the user's own `.cs2` page files, one way, with no changelog line \| |
| `RGN-C3` | 4 | 2 | reviews/2026-08-31-regression-review.md:26 | `RGN-C3` is one specific, reachable instance of that last worry and is written up because the RC note |
| `SV-C1` | 4 | 1 | reviews/2026-08-24-second-validation.md:204 | ## SV-C1. DR-A1's guard does not exercise the half its own javadoc says only behaviour can catch |
| `SVN-B17` | 4 | 2 | reviews/2026-09-01-week-of-commits-review.md:383 | \| **SVN-B17** \| AU-C12 is still open: the Keyboard tab throws accessories with no guard at all \| **fixed** 2026-09-02, with `V31-C2` and `AU-C12... |
| `SVN-B8` | 4 | 2 | reviews/2026-09-01-week-of-commits-review.md:374 | \| **SVN-B8** \| Undo cannot re-open a portal whose two halves are on different pages \| **fixed** \| |
| `TA-B4` | 4 | 1 | reviews/2026-08-24-test-suite-audit.md:66 | \| TA-B4 \| `testHomeAssignmentRules`' wiring check reads tokens, not call sites; deleting the only caller of the safety rule leaves it green - DD-... |
| `TCX-A2` | 4 | 3 | reviews/2026-09-01-test-suite-review.md:44 | \| TCX-A2 \| `HomeStaging` re-implements `isPathClear`'s rules and did not get the new length rule \| open \| |
| `TCX-A3` | 4 | 3 | reviews/2026-09-01-fanout-index.md:98 | \| `TCX-A3` \| `testTrainTailClearsEdges` asserted a string that this release made non-unique, so the assertion could no longer fail \| `9f1b80c8` \| |
| `TCX-B12` | 4 | 1 | reviews/2026-09-01-test-suite-review.md:63 | \| TCX-B12 \| Three of `testTheGoldenLayoutHoldsTogether`'s four tests have no floor on cases exercised \| open \| |
| `TS3-B6` | 4 | 2 | reviews/2026-09-02-fanout-index.md:86 | \| `TS3-B6` / `WK3-C1` / `D3F-C3` \| three \| the start guard was widened and the affordances were not \| |
| `TST-B18` | 4 | 2 | reviews/2026-08-28-test-suite-review.md:358 | \| B18 \| Three sample-layout tests assert an empty list with nothing proving the list can fill \| open \| |
| `TST-B7` | 4 | 4 | reviews/2026-08-28-test-suite-review.md:347 | \| B7 \| The exit-discard ordering assertion passes if the settle call is deleted \| open \| |
| `UXR-B2` | 4 | 3 | reviews/2026-08-28-ux-consistency-review.md:148 | \| B2 \| Manage Pages has two writers with different predicates; the menu and the button beside it disagree \| open \| |
| `UXR-C4` | 4 | 1 | reviews/2026-08-28-ux-consistency-review.md:485 | \| C4 \| Return Home is greyed at five sites without the tooltip `disableReturnHome` exists to keep true \| open \| |
| `VAL-C8` | 4 | 2 | reviews/2026-08-29-round-validation.md:354 | **VAL-C8.** All three ratchets pin with `assertEquals`, which is the right shape - an increase and a |
| `VAL9-A1` | 4 | 2 | reviews/VAL9-validation-2026-09-07.md:34 | ### VAL9-A1 - the destination-turn record fires for autonomy too, and a Set loses the parity |
| `VD9-C18` | 4 | 4 | reviews/2026-09-03-validation-of-the-day.md:73 | \| **C18** \| `RGN-B2` says "all three now say the same thing"; three code sites still say the s88 door stops \| fixed \| |
| `VD9-C2` | 4 | 2 | reviews/2026-09-03-validation-of-the-day.md:57 | \| **C2** \| `IPR-B2`'s central sentence is refuted by the counterexample printed two lines below it, in four places \| fixed \| |
| `VLD-B2` | 4 | 2 | reviews/2026-09-05-single-pass-validation.md:80 | \| B2 \| B \| The index preservation carries the capitalised `Version` block through as unmodelled, so a genuine CS2 export gains a *second* versio... |
| `VLD-C2` | 4 | 1 | reviews/2026-09-05-single-pass-validation.md:85 | \| C2 \| C \| Two consequences of holding the panel: an exception mid-build now leaves a discarded, stale diagram mounted rather than an empty pane... |
| `ACC-B1` | 3 | 2 | reviews/2026-09-04-release-acceptance-review.md:55 | \| B1 \| B \| Legacy import silently drops hand-written `lockedges` - the one dropped field missing from `whatALegacyImportLeaves`'s report; 116 re... |
| `ACC-B3` | 3 | 1 | reviews/2026-09-04-release-acceptance-review.md:57 | \| B3 \| B \| A setup edit declined during a run can be silently reverted by the exit capture, and the decline message promises "it will be picked... |
| `ACC-C1` | 3 | 3 | reviews/2026-09-04-release-acceptance-review.md:58 | \| C1 \| C \| Three comments still describe the removed `mustBackIn` rule as if it stood \| `Layout.java:7879`, `AutonomyBuilder.java:581`, `Autono... |
| `ACC-C4` | 3 | 2 | reviews/2026-09-04-release-acceptance-review.md:61 | \| C4 \| C \| A legacy placement on a split square with no recorded facing gets one at random - undeclared, unreported, two imports of one file can... |
| `CONF-A2` | 3 | 2 | reviews/CONF-confirmation-2026-09-06.md:41 | \| CONF-A2 \| NEW: the stop and the rule ask different questions again, so a train can be turned at a plain copy without being stopped first \| A \... |
| `CR-C3` | 3 | 2 | reviews/2026-08-24-conversion-review.md:56 | \| CR-C3 \| open \| |
| `DAY-B1` | 3 | 2 | reviews/2026-08-31-day-of-commits-review.md:138 | ### B1 - MT-149's timetable repaint sits behind two early returns the rename doors compensate for, and it was not given to them |
| `DAY-C2` | 3 | 2 | reviews/2026-08-31-day-of-commits-review.md:344 | ### C2 - `deleteLoc` did not get the redraws both rename doors have |
| `DD-A2` | 3 | 1 | reviews/2026-08-22-duplication-and-design.md:11 | \| **DD-A2, DD-A3, DD-A4** \| Closed in `ae94421a`. These were rank 1 and 2 on the list below. \| |
| `DD-B3` | 3 | 2 | reviews/2026-08-22-duplication-and-design.md:591 | \| DD-B3 \| Four grid-construction sites; `discard()` reaches three of them \| Open \| |
| `DD-D9` | 3 | 2 | reviews/2026-08-22-duplication-and-design.md:1245 | \| DD-D9 \| `reconcile` and `applyTo` must stay hand-written even if DD-A1 lands \| Recorded \| |
| `FSR-C1` | 3 | 1 | reviews/2026-08-24-fable-second-round.md:67 | \| **FSR-C1** \| The inverted test guards through its move-count bound, not the replay its comments credit \| **fixed** \| |
| `FSR-C7` | 3 | 1 | reviews/2026-08-24-fable-second-round.md:73 | \| **FSR-C7** \| The carriage-return comment describes rules its site does not have \| **fixed** \| |
| `GC-A1` | 3 | 2 | reviews/2026-08-22-general-code-review.md:15 | **GC-A1 is half closed, and the half that is closed is not the half the finding proposed.** The |
| `GC-A2` | 3 | 2 | reviews/2026-08-22-general-code-review.md:72 | \| GC-A2 \| Leaving autonomy mode never undoes the findings-panel swap. The list stays across the bottom of the track editor, and coming back mount... |
| `IAR-B2` | 3 | 1 | reviews/2026-08-24-independent-application-review.md:233 | \| **IAR-B2** \| A fourth event-thread caller of a synchronized `Layout` method, and the only one gated on autonomy running \| **Open** \| |
| `IPR-A1` | 3 | 2 | reviews/2026-08-31-independent-review.md:61 | ### A1 - a legacy import writes each station's LENGTH LIMIT into the track's LENGTH, and leaves the limit unset |
| `IPR-A2` | 3 | 2 | reviews/2026-08-31-independent-review.md:175 | ### A2 - a save that prunes tile properties reports nothing at all, and the display is the only thing missing |
| `IPR-B3` | 3 | 2 | reviews/2026-08-31-independent-review.md:463 | ### B3 - two bracketed groups at the same indent are flagged red and the save is refused, for an outline the reader parses correctly |
| `LE2-C16` | 3 | 1 | - not found - | - not found - |
| `R28-C1` | 3 | 3 | reviews/2026-09-01-regression-vs-2.8.1-review.md:44 | \| **C1** \| fixed - "Clear All Home Locomotives" is gone, and its two API methods are dead \| |
| `RA-A1` | 3 | 2 | - not found - | - not found - |
| `REG6-B1` | 3 | 2 | reviews/REG6-regressions-2026-09-06.md:166 | ### REG6-B1 - the prompt's answer is discarded whenever the journey ends at a terminus |
| `REG7-A1` | 3 | 2 | reviews/REG7-regressions-2026-09-07.md:24 | REG7-A1 and REG7-B1 sit *beside* two of those limits and are distinct from them; each entry says how. |
| `REG7-A2` | 3 | 2 | reviews/REG7-regressions-2026-09-07.md:47 | \| REG7-A2 \| `followDirectionChanges` cannot tell the run's own reversal from an operator's: after any journey that turned the train, the first po... |
| `REG8-C4` | 3 | 1 | reviews/REG8-regressions-2026-09-07.md:35 | \| REG8-C4 \| `testADirectionChangeIsNotSwallowed`'s stated rationale is `ca0265f4`'s withdrawn "deferred until safe" ruling; it enforces `putIfAbs... |
| `REL-C2` | 3 | 2 | reviews/2026-09-03-release-review.md:31 | \| C2 \| C \| `stationsWithoutMaxLength` gated on `measuresAnyTrack()`, a borrowed precondition: `maxTrainLength` is compared against the locomotiv... |
| `RGN-B2` | 3 | 2 | reviews/2026-08-31-regression-review.md:42 | \| **B2** \| closed - an s88-fired route with a conflict does not "stop": it drops its accessories and runs everything else \| |
| `SG-A3` | 3 | 3 | reviews/2026-08-30-staging-planner-round.md:105 | ### A3 - a locomotive held on two points made the goal unreachable, and counting it properly is the wrong fix |
| `SG-A5` | 3 | 3 | reviews/2026-08-30-staging-planner-round.md:166 | ### A5 - one locomotive with no speed ended the whole staging run |
| `SPEC-B1` | 3 | 2 | reviews/SPEC-validation-2026-09-06.md:59 | \| SPEC-B1 \| A journey to a terminus asks the reversal question and then discards the answer \| B \| Fixed \| High \| |
| `SV2-A1` | 3 | 2 | reviews/2026-09-01-second-validation.md:47 | \| **SV2-A1** \| `firstClearRoute`'s new terminus seed makes the back-in rule vacuous for any train standing in a berth, so Return Home will drive... |
| `SVN-B7` | 3 | 2 | reviews/2026-09-01-week-of-commits-review.md:373 | \| **SVN-B7** \| The "route already running" guard is on one door of three \| **fixed** 2026-09-02 (`87b6c10a`), reasoning corrected by `V31-B2` \| |
| `SVN-B9` | 3 | 2 | reviews/2026-09-01-week-of-commits-review.md:375 | \| **SVN-B9** \| Nothing repairs the on-disk pre-edit note when a locomotive is renamed \| **fixed** \| |
| `SVN-C13` | 3 | 2 | reviews/2026-09-01-week-of-commits-review.md:1013 | \| **SVN-C13** \| The page-rename duplicate check sees two sentinels stored in `pageNames` \| open \| |
| `SVN-C3` | 3 | 2 | reviews/2026-09-01-week-of-commits-review.md:1003 | \| **SVN-C3** \| The max-train-length check has no "does this layout measure anything" gate, though its twin does \| open \| |
| `TA-B8` | 3 | 2 | reviews/2026-08-24-test-suite-audit.md:70 | \| TA-B8 \| `testFacingFollowsTheTrack`'s oracle is built from the same `getRoutes` map as the subject, so the MT-125 rule is satisfied by construc... |
| `TA-B9` | 3 | 1 | reviews/2026-08-24-test-suite-audit.md:71 | \| TA-B9 \| No test ever asserts `DiagramMonitor` *publishes*; both monitor tests hand it a null layout so `compute()` - the milestone/run/lock-was... |
| `TCS-A2` | 3 | 1 | reviews/2026-08-31-fanout-index.md:46 | \| `TCS-A2` \| test suite \| The only OB-159 test passed with OB-159 put back: it sampled a pixel the caption never paints. \| |
| `TCX-B4` | 3 | 2 | reviews/2026-09-01-test-suite-review.md:55 | \| TCX-B4 \| `configureEdge`'s release-before-throw ordering - a mechanical constraint of the real turnout - is untested \| open \| |
| `TS3-B7` | 3 | 1 | reviews/2026-09-02-test-suite-review.md:50 | \| TS3-B7 \| The staging planner's "a longer approach is more room" rule has no test, and a `continue` turned into a refusal would pass every test... |
| `TST-B10` | 3 | 1 | reviews/2026-08-28-test-suite-review.md:350 | \| B10 \| `testBusyDialogInteraction` never asserts a dialog is shown or disposed \| open \| |
| `TST-B15` | 3 | 1 | reviews/2026-08-28-test-suite-review.md:355 | \| B15 \| `testRunningAgainOverASettledSetupChangesNothing` states an invariant already violated \| open \| |
| `TST-C10` | 3 | 2 | reviews/2026-08-28-test-suite-review.md:798 | TST-C10. |
| `TST-C8` | 3 | 2 | reviews/2026-08-28-test-suite-review.md:813 | \| C8 \| Loop-only assertions with no floor, each currently covered by a sibling \| open \| |
| `TST-C9` | 3 | 2 | reviews/2026-08-28-test-suite-review.md:814 | \| C9 \| Self-referential oracles: expected value computed by the method under test \| open \| |
| `TSX-B3` | 3 | 3 | reviews/2026-09-03-test-suite-audit.md:30 | \| B3 \| Medium \| Three tests write the operator's own preferences back without the "was it ever stored" guard the fourth was given, and one can l... |
| `UXR-A1` | 3 | 2 | reviews/2026-08-28-ux-consistency-review.md:63 | \| A1 \| The Layout menu re-enables every item when it opens, undoing "not without a Central Station" \| open \| |
| `UXR-B5` | 3 | 1 | reviews/2026-08-28-ux-consistency-review.md:151 | \| B5 \| Three destructive confirmations pre-select Yes while their own comment says No \| open \| |
| `UXR-C12` | 3 | 1 | reviews/2026-08-28-ux-consistency-review.md:493 | \| C12 \| "Increase Size" has no predicate while its stated mirror "Decrease Size" does - and the greyed one says no reason \| open \| |
| `UXR-C20` | 3 | 1 | reviews/2026-08-28-ux-consistency-review.md:501 | \| C20 \| Three jumps to the Auto tab lack the `isEnabledAt` guard `showLayoutTab` was given \| open \| |
| `UXR-C21` | 3 | 3 | reviews/2026-08-28-ux-consistency-review.md:502 | \| C21 \| Assorted dead code: `showTab(Icon)`, `LocFilterBoxKeyTyped`'s Escape branch, `RightClickFunctionMenu`'s mouse handlers \| open \| |
| `V31-B1` | 3 | 2 | reviews/2026-09-02-first-validation.md:40 | \| `V31-B1` \| The diagram strip decides Start-vs-Fix on `errorCount()`, which the guard stopped asking - and the rule written to catch exactly tha... |
| `V34-B1` | 3 | 3 | reviews/2026-09-02-fourth-validation.md:56 | \| **V34-B1** \| The strip's error count is read and thrown away, and two places now assert it must stay \| open \| |
| `V34-C4` | 3 | 1 | reviews/2026-09-02-fourth-validation.md:133 | \| **V34-C4** \| The 600 ms correction was not swept to its twins in the same file \| open \| |
| `VAL-B4` | 3 | 1 | reviews/2026-08-29-round-validation.md:248 | ### VAL-B4. The two new hourglass tests no longer test what they say, and their mutations are live |
| `VAL8-A2` | 3 | 2 | reviews/VAL8-validation-2026-09-07.md:21 | \| VAL8-A2 \| A \| `arrivedFrom` never crosses between setup and running layout: the builder does not emit it, capture does not carry it, and the p... |
| `VD10-B6` | 3 | 1 | reviews/2026-09-03-validation-round-two.md:49 | \| **B6** \| `VD9-C9`'s in-place write can leave a truncated or zero-byte note, silently, where the atomic move it replaced could never destroy one... |
| `VD10-C2` | 3 | 2 | reviews/2026-09-03-validation-round-two.md:52 | \| **C2** \| One click on the station radio now runs `setupChanged()` **three** times: three full configuration loads on the event thread, where th... |
| `VD9-C16` | 3 | 2 | reviews/2026-09-03-validation-of-the-day.md:71 | \| **C16** \| `REL-C15` orphaned `captionsOnPage`'s javadoc and left three documents asserting it has no caller \| fixed \| |
| `VD9-C5` | 3 | 1 | reviews/2026-09-03-validation-of-the-day.md:60 | \| **C5** \| `IPR-B4`: two of the "three assertions" carrying the fix cannot fail \| fixed \| |
| `VD9-C9` | 3 | 2 | reviews/2026-09-03-validation-of-the-day.md:64 | \| **C9** \| `SVN-B9` repairs the note through the one write primitive this class documents as failing under the lock it is written for, silently \... |
| `VLD-C5` | 3 | 2 | reviews/2026-09-05-single-pass-validation.md:88 | \| C5 \| C \| `legacySignalAddress` accepts `Signal -5`, `Signal 0` and `Signal +116`; and the shipped test checks the count with `line.contains("2... |
| `WK-B2` | 3 | 1 | reviews/2026-08-28-week-review.md:39 | - **WK-B2**: the new start-up splash (`FR-041`) is shown before the two constructors most likely to |
| `WK3-B2` | 3 | 2 | reviews/2026-09-02-fanout-index.md:84 | \| `WK3-B2` / `D3F-B1` / `RT3-B1` \| three, independently, and `TS3-B7` filed the missing test \| the planner's new room check ran after the arriva... |
| `AC2-C3` | 2 | 2 | reviews/2026-09-04-independent-acceptance.md:56 | \| C3 \| C \| `exportLocsToCSV` dereferences the view without a null check, so the programmatic API (headless init) NPEs on it \| `MarklinControlSt... |
| `AC3-C1` | 2 | 2 | reviews/2026-09-05-fable-acceptance.md:59 | \| C1 \| C \| `writeAtomically` deletes its staging file when the WRITE fails but leaves it behind when the MOVE fails - the comment's "would other... |
| `ACC-B2` | 2 | 2 | reviews/2026-09-04-release-acceptance-review.md:56 | \| B2 \| B \| The VD11-A2 stop-narrowing missed a caller its own census never counted: the reload after the track-diagram editor closes gets no eme... |
| `ACC-C10` | 2 | 1 | reviews/2026-09-04-release-acceptance-review.md:67 | \| C10 \| C \| The "..." escape to the autonomy tab is created inside the loop over the base list, so it never renders when that list is empty - ex... |
| `ACC-C7` | 2 | 2 | reviews/2026-09-04-release-acceptance-review.md:64 | \| C7 \| C \| A modern points-ARRAY export re-imported is classified `LEGACY_GRAPH` and silently loses new-model fields; no genuine 2.8.1 file is a... |
| `AU-A1` | 2 | 1 | reviews/2026-08-25-autonomous-round.md:145 | ### AU-A1 - the OB-085 impossibility proof was built out of a rule the railway does not have |
| `CD3-C4` | 2 | 1 | reviews/2026-09-02-comments-and-docs-review.md:89 | \| CD3-C4 \| C \| open \| |
| `CONF-B2` | 2 | 2 | reviews/CONF-confirmation-2026-09-06.md:44 | \| CONF-B2 \| `AutonomyEditorPanel.placeLocomotive(TileKey)` still records no facing, leaves the previous occupant's, and got no note \| B \| Fixed... |
| `CONF-B6` | 2 | 2 | reviews/CONF-confirmation-2026-09-06.md:48 | \| CONF-B6 \| `SPEC-C5` was answered with a test that pins two rules the code does not make equal \| B \| Fixed \| CONFIRMED in code; the test's ow... |
| `CONF2-B1` | 2 | 2 | reviews/CONF2-confirmation-2026-09-06.md:41 | \| **CONF2-B1** \| The reversal policy's contract is inverted, and three places now say the opposite \| Open \| |
| `CONF2-B2` | 2 | 1 | reviews/CONF2-confirmation-2026-09-06.md:42 | \| **CONF2-B2** \| `62f845a5` shipped untested, and the clause it removed has no pin \| Open \| |
| `CP-C1` | 2 | 1 | reviews/2026-08-01-two-commit-review.md:16 | \| CP-C1 \| The epoch guard is per-`Layout`, so a detached clear that outlives its `Layout` bypasses the guard on the successor's run - the same we... |
| `D24-C7` | 2 | 2 | reviews/2026-09-02-comments-and-docs-review.md:476 | `D24-C7` exactly once in the whole file, at its own line 175, as a parenthetical inside a different |
| `D24-C9` | 2 | 2 | reviews/2026-09-02-comments-and-docs-review.md:516 | (mentioned only) The `SVN-B6`/`D24-C9` comment at `AutonomySession.java:4696-4701` (leading into `boolean worthABadge = |
| `D3F-B1` | 2 | 2 | reviews/2026-09-02-three-days-review.md:57 | ### D3F-B1 - the room check's "keep looking" is defeated by the search's own `seen` bookkeeping |
| `DAY-C4` | 2 | 2 | reviews/2026-08-31-day-of-commits-review.md:405 | ### C4 - "setUnoccupied on an edge that is already clear does nothing" stopped being true when occupancy started counting |
| `DIR-C5` | 2 | 1 | reviews/2026-09-06-direction-and-consistency.md:99 | \| C5 \| C \| `lastSeenDirection` is keyed by name and is never repaired on rename nor evicted on delete, so the first direction change after a ren... |
| `DIR-C6` | 2 | 1 | reviews/2026-09-06-direction-and-consistency.md:100 | \| C6 \| C \| `placedLocomotives()` is a fifth walk of the same map WK3-C3 consolidated four of - and it already carries the qualification that hel... |
| `DIR-C7` | 2 | 1 | reviews/2026-09-06-direction-and-consistency.md:101 | \| C7 \| C \| The name-check census is per FILE, so `TrainControlUI`'s second door cannot fail it; and the CS2/CS3 sync creates locomotives from Ce... |
| `DR-B9` | 2 | 1 | reviews/2026-08-24-duplication-robustness.md:146 | \| DR-B9 \| `parseAuto` resolves names at four doors; two log the drop and two are silent, and all four persist it \| Open \| |
| `DW-C2` | 2 | 2 | reviews/2026-08-24-day-review.md:41 | \| DW-C2 \| C \| AutonomyMenu / downloadCSLayoutMenuItemActionPerformed \| The download offer's guard is narrower than the handler's, so one reacha... |
| `F20-F31` | 2 | 1 | reviews/2026-08-17-unread-half-review.md:132 | (mentioned only) **C5.** Cosmetics: a hardcoded English "Pending Start +...s"; a literal `F20-F31` tab title; the |
| `FBR-B3` | 2 | 2 | reviews/2026-08-24-fable-round-review.md:579 | ### FBR-B3 - the caption filter refuses squares that are real track |
| `FBR-C1` | 2 | 1 | reviews/2026-08-24-fable-round-review.md:82 | \| **FBR-C1** \| The stale-answer guards in FR-017 and OB-079 cannot fire, and their comments say why they should \| **fixed** \| |
| `FBR-C3` | 2 | 1 | reviews/2026-08-24-fable-round-review.md:84 | \| **FBR-C3** \| OB-077's comment promises a recovery the caller does not perform \| **fixed** \| |
| `FBR-C6` | 2 | 2 | reviews/2026-08-24-fable-round-review.md:87 | \| **FBR-C6** \| The FR-017 window's reasons and grouping are read under two separate acquisitions of the Layout monitor \| **fixed** \| |
| `FR3-C3` | 2 | 1 | reviews/2026-09-04-final-release-review.md:61 | \| C3 \| C \| The new `saveQuietly` source rule passes on any `!` in the statement and scans only four named files, so a discarding call with a nea... |
| `FSR-C3` | 2 | 1 | reviews/2026-08-24-fable-second-round.md:69 | \| **FSR-C3** \| "No state-independent statement about an FR-001 blocker" is overstated - a blockedBy cycle between homes is one \| **fixed** \| |
| `FV2-B2` | 2 | 1 | reviews/2026-09-01-fix-validation.md:121 | \| **FV2-B2** \| `D24-B1` is fixed for `isReversing` and left for `isTerminus`, which is the half that is reachable on the diagram-derived graph \|... |
| `IAR-A2` | 2 | 1 | reviews/2026-08-24-independent-application-review.md:57 | \| **IAR-A2** \| "Backup Data" now commits the session - writing the open editor's unsaved edits to disk, from a worker thread \| **Open** \| |
| `IND-M4` | 2 | 2 | reviews/2026-07-27-home-staging-review.md:551 | `IND-M4`/`INT-B1` defect class, pre-empted). All four are pinned by tests. |
| `IND9-C2` | 2 | 2 | reviews/IND9-independent-2026-09-07.md:282 | ### IND9-C2 - parseReleaseVersion takes the first number, which is not always the version |
| `LD-C6` | 2 | 2 | reviews/2026-08-25-last-day.md:346 | \| **C6** \| The station right-click menu walks the whole graph four times on the EDT \| open \| |
| `LE-B6` | 2 | 1 | reviews/2026-08-30-layout-editor-review.md:279 | ### B6 - subtracting the origins wholesale preserved setup on squares that were built over |
| `LE-C9` | 2 | 1 | reviews/2026-08-30-layout-editor-review.md:406 | ### C9 - the menu assertions could not tell which item got which predicate |
| `OPV-C4` | 2 | 1 | reviews/2026-09-04-opus-validation.md:46 | \| C4 \| C \| ACC-C10's ellipsis is added before the More Destinations heading, so the empty-list case gets two separators and a "..." that heads n... |
| `OPV-C5` | 2 | 1 | reviews/2026-09-04-opus-validation.md:47 | \| C5 \| C \| ACC-B3's exit message names a cause that may not be the reason, and the flag is set by a rebuild request that carries no edit \| `Tra... |
| `RA-C3` | 2 | 1 | reviews/2026-08-24-reopen-audit.md:83 | \| **RA-C3** \| SV-B1's residual: the delete path still destroys the page file and the setup record before the index throw can fire, for a genuinel... |
| `RC-A10` | 2 | 1 | reviews/2026-09-03-release-review.md:1586 | (mentioned only) \| `RC` carried #3 \| A-ish \| a mid-run failure strands a train on locked track **and** drops that track's route protection in the same statement:... |
| `RC-A11` | 2 | 2 | reviews/2026-08-31-day-of-commits-review.md:374 | (mentioned only) ### C3 - RC-A11's graceful stop fires from the two manual dispatch doors, where autonomy may never have been running |
| `RC-A12` | 2 | 1 | - not found - | - not found - |
| `RC-B3` | 2 | 2 | reviews/2026-08-30-release-candidate-review.md:193 | ### B3 - Add Route stopped proposing a name |
| `RC-C11` | 2 | 1 | reviews/2026-08-30-release-candidate-review.md:330 | ### C11 - "not reachable from the menu today", passed to `setEnabled` twice |
| `REL-A1` | 2 | 2 | reviews/2026-09-03-release-review.md:28 | \| A1 \| A \| Diagram tile silently refuses protecting-signal clicks whenever autonomy is loaded but NOT running - no dialog can ever appear in tha... |
| `REL-B1` | 2 | 2 | reviews/2026-09-03-release-review.md:29 | \| B1 \| B \| The excluded-page filter on the SIGNAL side of `signalsThatAreGone` silences a true warning: a signal on an excluded page really is d... |
| `REL-C13` | 2 | 1 | reviews/2026-09-03-release-review.md:59 | \| C13 \| C \| `CommandRow.canBeACondition`'s javadoc says `AUTO_LOCOMOTIVE` is not offered and "the editor cannot yet build the row its documentat... |
| `REL-C15` | 2 | 2 | reviews/2026-09-03-release-review.md:61 | \| C15 \| C \| Eighteen methods in the autonomy, diagram and route UI have no caller anywhere in `src/` or `test/` - and the scan that found them s... |
| `REL-C16` | 2 | 2 | reviews/2026-09-03-release-review.md:62 | \| C16 \| C \| DAY-C4's false claim has been copied into `Edge.release`'s own javadoc - which is the authority the `Layout` comment cites - so the... |
| `REL-C6` | 2 | 1 | reviews/2026-09-03-release-review.md:35 | \| C6 \| C \| `isPathClear`'s reversal-room comment stack is stale twice: "every segment ... added together" and "the counting is unchanged" both s... |
| `RG3-C3` | 2 | 1 | reviews/2026-09-02-regression-vs-2.8.1-review.md:57 | \| **C3** \| open - "Open Legacy Track Diagram Editor" is withdrawn, but the item is taken off the menu on only one of the two branches, and its ha... |
| `RG5-C1` | 2 | 1 | reviews/2026-09-05-fable-regression.md:71 | \| **C1** \| open - 2.8.1 set Signals 63 and 64 red when a train left TopMainR0 across the junction; the pairing successor reproduces their occupan... |
| `RG5-C2` | 2 | 1 | reviews/2026-09-05-fable-regression.md:72 | \| **C2** \| open - `testTheRebuildIsOnePass` now asserts LayoutGrid empties the panel in exactly one place, in the same commit that added a second... |
| `RGN-A1` | 2 | 2 | reviews/2026-08-31-regression-review.md:39 | \| **A1** \| fixed - legacy `autonomy.json` import silently drops every run-wide setting, the whole timetable, and all 90 authored edge lengths \| |
| `RT3-B1` | 2 | 2 | reviews/2026-09-02-autonomy-routing-review.md:36 | \| RT3-B1 \| B \| The reversal-room `continue` in `firstClearRoute` (975f157d) is defeated by the search's own dominance pruning: the "longer appro... |
| `SF-B1` | 2 | 1 | reviews/2026-08-01-reversing-destinations-review.md:54 | `SF-B1` race is fixed: a traversal of a shared-sensor parking point is uneventful. Stated here |
| `SG-A2` | 2 | 2 | reviews/2026-08-30-staging-planner-round.md:77 | ### A2 - a plan from a non-station origin was planned, refused, retried, and abandoned |
| `SG-A4` | 2 | 2 | reviews/2026-08-30-staging-planner-round.md:143 | ### A4 - the planner exempted the mover from its own detection section; the runtime exempts nobody |
| `SPEC-A2` | 2 | 2 | reviews/SPEC-validation-2026-09-06.md:58 | \| SPEC-A2 \| Autonomy and Return Home now stop at every plain copy of a may-reverse square \| A \| Fixed \| High on the logic, **unmeasured** on r... |
| `SPEC-B5` | 2 | 2 | reviews/SPEC-validation-2026-09-06.md:22 | `SPEC-B5`) turn on whether a square on Adam's railway ever builds to more than one Point, which |
| `SVN-A4` | 2 | 2 | reviews/2026-09-01-week-of-commits-review.md:65 | \| **SVN-A4** \| A route refused at a human door discards its emergency stop, which is the defect its own comment claims to have removed \| open \| |
| `SVN-B1` | 2 | 2 | reviews/2026-09-01-week-of-commits-review.md:367 | \| **SVN-B1** \| The reversal-length notice and the guard it serves ask different questions \| **closed** - the under-report was Adam's ruling 2; t... |
| `SVN-B10` | 2 | 2 | reviews/2026-09-01-week-of-commits-review.md:376 | \| **SVN-B10** \| The load door asks a narrower question than the start door \| **fixed** 2026-09-02 (`87b6c10a`) \| |
| `SVN-B12` | 2 | 2 | reviews/2026-09-01-week-of-commits-review.md:378 | \| **SVN-B12** \| The MT-149 timetable repaint sits behind two early returns \| **fixed** as `DAY-B1` (`97137c4b`) \| |
| `SVN-B13` | 2 | 2 | reviews/2026-09-01-week-of-commits-review.md:379 | \| **SVN-B13** \| `rebuildHomeStations` dedups by locomotive, not by square \| **fixed** 2026-09-02 (`8d1c17ca`) \| |
| `SVN-B15` | 2 | 2 | reviews/2026-09-01-week-of-commits-review.md:381 | \| **SVN-B15** \| The mid-route conflict question parks the rest of the route, stop included, behind a modal \| half overtaken, half **fixed** 2026... |
| `SVN-B6` | 2 | 2 | reviews/2026-09-01-week-of-commits-review.md:372 | \| **SVN-B6** \| The running diagram draws no cross on a shut *plain* point \| **fixed** 2026-09-02 (`1cfdf370`) \| |
| `SVN-C4` | 2 | 2 | reviews/2026-09-01-week-of-commits-review.md:1004 | \| **SVN-C4** \| `AutoLocomotiveStatus`'s "autonomy will not choose this" dash was not swept for the terminus move \| open \| |
| `SVN-C6` | 2 | 2 | reviews/2026-09-01-week-of-commits-review.md:1006 | \| **SVN-C6** \| The excluded-page filter was added to one check and not its twin \| open \| |
| `TA-B10` | 2 | 1 | reviews/2026-08-24-test-suite-audit.md:72 | \| TA-B10 \| `testRouteEditorLocked` cannot see table cell editors, so the `isCellEditable` locked gate - the guard for "the plus, the trash, the a... |
| `TA-B6` | 2 | 1 | reviews/2026-08-24-test-suite-audit.md:68 | \| TA-B6 \| The CS3 `isNotFoundError` JSON branch - the one a real pre-2.6.0 CS3 exercises - is never executed by any test; `CS3TestServer` encodes... |
| `TA-B7` | 2 | 1 | reviews/2026-08-24-test-suite-audit.md:69 | \| TA-B7 \| The outgoing UDP wire format has no oracle anywhere; the short-datagram guard is untested; the reopen test never asserts reception resu... |
| `TCX-B11` | 2 | 1 | reviews/2026-09-01-test-suite-review.md:62 | \| TCX-B11 \| `testTheGoldenLayoutHoldsTogether` reads Adam's real railway in place rather than through `LayoutSandbox.open(File)` \| open \| |
| `TCX-B2` | 2 | 2 | reviews/2026-09-01-test-suite-review.md:53 | \| TCX-B2 \| The editor's length notice asks for a different measurement from the one the guard needs \| **DEFERRED - needs Adam** \| |
| `TCX-B3` | 2 | 1 | reviews/2026-09-01-test-suite-review.md:54 | \| TCX-B3 \| The reversal-room guard's reach on the real railway is never measured, and on his data it is nearly zero \| open \| |
| `TCX-B6` | 2 | 1 | reviews/2026-09-01-test-suite-review.md:57 | \| TCX-B6 \| `badgeAt` confounds `parking` and `shut`, so two badge tests pass under the wrong rule \| open \| |
| `TCX-B7` | 2 | 1 | reviews/2026-09-01-fanout-index.md:135 | \| `TCX-B7` \| `testAutonomySimulationSanity` does assert `getPathValidationFailureCount() == 0` on a `simulate: true` fixture where the guard cann... |
| `TCX-B9` | 2 | 1 | reviews/2026-09-01-test-suite-review.md:60 | \| TCX-B9 \| `testCopiesOfOneSquareAreTheSamePlaceAndNeighboursAreNot` asserts a floor of one where it needs two \| open \| |
| `TS3-B2` | 2 | 1 | reviews/2026-09-02-fanout-index.md:87 | \| `TS3-B2` \| one \| a test left the shared locomotive mutated for its 84 siblings \| |
| `TST-A4` | 2 | 1 | reviews/2026-08-28-test-suite-review.md:50 | \| A4 \| The two-minute soak asserts a counter that simulate mode pins at zero \| open \| |
| `TST-B11` | 2 | 1 | reviews/2026-08-28-test-suite-review.md:351 | \| B11 \| `testTimetableOnDerivedGraph` derives from the machine's preference, and its config name is dead \| open \| |
| `TST-B12` | 2 | 1 | reviews/2026-08-28-test-suite-review.md:352 | \| B12 \| `testReturnHomeOnRealLayout` has an unseeded `Random` and no floor on meaningful rounds \| open \| |
| `TST-B2` | 2 | 1 | reviews/2026-08-28-test-suite-review.md:342 | \| B2 \| `testNothingWroteToTheGoldenLayout` compares before its own siblings run \| open \| |
| `TST-B5` | 2 | 1 | reviews/2026-08-28-test-suite-review.md:345 | \| B5 \| `testRouteInventory` contributes eight green results and almost no assertions \| open \| |
| `TST-B8` | 2 | 1 | reviews/2026-08-28-test-suite-review.md:348 | \| B8 \| `testThePickedUpLabelIsAlwaysPutDown` matches the wrong branch and is now vacuous \| open \| |
| `TST-B9` | 2 | 1 | reviews/2026-08-28-test-suite-review.md:349 | \| B9 \| `testTheActivePageDrawsTheSamePictureAsChoosingIt` renders the same call twice \| open \| |
| `TST-C15` | 2 | 1 | reviews/2026-08-28-test-suite-review.md:820 | \| C15 \| Two `testLocomotiveIdentityPropagates` sweeps with narrow patterns and no floor \| open \| |
| `TST-C7` | 2 | 2 | reviews/2026-08-28-test-suite-review.md:812 | \| C7 \| Ordering scans whose left term is not proved present \| open \| |
| `TSX-C11` | 2 | 1 | reviews/2026-09-03-test-suite-audit.md:44 | \| C11 \| Low \| `testTimetableOnDerivedGraph` counts every restorable locomotive where its message says "every one this test placed", and `TST-B11... |
| `TSX-C17` | 2 | 1 | reviews/2026-09-03-test-suite-audit.md:60 | \| C17 \| Low \| `CS3TestServer` hardcodes port 8080 with no override, `getPort()` reads the constant back, and neither of the two classes that sta... |
| `TSX-C18` | 2 | 2 | reviews/2026-09-03-test-suite-audit.md:61 | \| C18 \| Low \| Today's blanket `@AfterClass(alwaysRun = true)` reached ten teardowns that dereference a static the `@BeforeClass` may never have... |
| `TSX-C2` | 2 | 1 | reviews/2026-09-03-test-suite-audit.md:35 | \| C2 \| Low \| The same class's railway fingerprint is sampled at the instant the sandbox closes, and is skipped entirely when a measurement throw... |
| `TSX-C5` | 2 | 1 | reviews/2026-09-03-test-suite-audit.md:38 | \| C5 \| Low \| `testSidebarIcons` regexes `TrainControlUI.java` without stripping comments, and a commented-out `setIconAt` already stands in that... |
| `TSX-C8` | 2 | 1 | reviews/2026-09-03-test-suite-audit.md:41 | \| C8 \| Low \| `testRoutePicking`'s `COVERED_HERE` is still an unchecked claim; delete a mirror test and the coverage index goes on reporting the... |
| `UC-B1` | 2 | 1 | reviews/2026-08-01-untouched-code-review.md:78 | \| UC-B1 \| Stale arrival/departure function survives a decoder change and crashes the graph's locomotive-assignment dialog; the dialog that could... |
| `UC-C11` | 2 | 1 | reviews/2026-08-01-untouched-code-review.md:129 | \| UC-C11 \| `Point` traps: runtime-inert `assert` guards, a nullable `Integer` that NPEs later, `toJSON` throws on a non-numeric s88 \| Fixed - th... |
| `UC-C12` | 2 | 2 | reviews/2026-08-01-untouched-code-review.md:130 | \| UC-C12 \| Clearing a local locomotive icon also wipes the Central Station image URL; compensated online, not offline \| Fixed - `setLocalImageUR... |
| `UC-C6` | 2 | 1 | reviews/2026-08-01-untouched-code-review.md:124 | \| UC-C6 \| `MarklinControlStation.execRoute` NPEs on an unknown route name \| Fixed - unknown names log and return. Pinned by `testExecutingAnUnkn... |
| `UXR-B3` | 2 | 1 | reviews/2026-08-28-ux-consistency-review.md:149 | \| B3 \| "Activate Routes" and the autonomy route list are live during a run and answer with a dialog \| open \| |
| `UXR-B6` | 2 | 2 | reviews/2026-08-28-ux-consistency-review.md:152 | \| B6 \| "Enable Automatic Execution" is offered on every route and always fails on one with no s88 \| open \| |
| `UXR-B7` | 2 | 2 | reviews/2026-08-28-ux-consistency-review.md:153 | \| B7 \| `isAutoRunning` vs `isRunning`: Start is offered and Stop vanishes while trains are still moving \| open \| |
| `UXR-C11` | 2 | 2 | reviews/2026-08-28-ux-consistency-review.md:492 | \| C11 \| The pop-out and the picture export sit under the "Local Layout" heading although they work on CS layouts \| open \| |
| `V31-C1` | 2 | 2 | reviews/2026-09-02-first-validation.md:42 | \| `V31-C1` \| Two of the three "why can't I start" messages still split on `errorCount()`, so both can say "wait for trains" in the case `2e83b737... |
| `V31-C4` | 2 | 1 | reviews/2026-09-02-first-validation.md:45 | \| `V31-C4` \| `rebuildHomeStations`' new square rule is keyed on the BLOCK; the harm it cites (`sharesSection` answering IMPOSSIBLE) is keyed on t... |
| `V32-B1` | 2 | 2 | reviews/2026-09-02-second-validation.md:59 | \| **V32-B1** \| The affordance sweep stopped one site short: `AutonomyOverlayToggle` still decides Fix-setup-versus-Start from `autonomyErrorCount... |
| `V32-C1` | 2 | 2 | reviews/2026-09-02-second-validation.md:213 | \| **V32-C1** \| The right-click Start item's tooltip says "wait for trains" in exactly the case the widened guard added \| open \| |
| `V32-C2` | 2 | 1 | reviews/2026-09-02-second-validation.md:214 | \| **V32-C2** \| The affordance rule names two questions rather than reading one, does not pin `autonomyHasErrors()`'s body, and its javadoc still... |
| `V33-C5` | 2 | 1 | - not found - | - not found - |
| `V33-C8` | 2 | 1 | reviews/2026-09-02-seventh-validation.md:184 | `V33-C8` objected to and what both earlier attempts got wrong. |
| `V35-C1` | 2 | 1 | reviews/2026-09-02-fifth-validation.md:31 | \| V35-C1 \| C \| open \| Clear All Home Locomotives greys on an excluded page though its own javadoc calls it setup-wide, and the greyed tooltip d... |
| `V36-C3` | 2 | 1 | reviews/2026-09-02-sixth-validation.md:44 | \| C3 \| `V34-C6` is untouched, and is now the *only* rule holding the strip: one whole-file `contains` over raw source, beside a sibling in the sa... |
| `V37-B2` | 2 | 1 | reviews/2026-09-02-seventh-validation.md:50 | \| B2 \| The precondition was deleted on the strength of a control in another test. This test has no accepted case, so `V36-B2`'s mutation still pa... |
| `VAL-B1` | 2 | 2 | reviews/2026-08-29-round-validation.md:129 | ### VAL-B1. Only two of the three Central Station items have an owner that hands them back |
| `VAL-C3` | 2 | 1 | reviews/2026-08-29-round-validation.md:323 | **VAL-C3.** With the drain always upright, the drawing has period `CYCLE_FRAMES`, not `CYCLE_FRAMES * |
| `VAL-C6` | 2 | 1 | reviews/2026-08-29-round-validation.md:340 | **VAL-C6.** The three predicates asked about in the brief do agree - see VAL-D2 - but |
| `VAL9-B3` | 2 | 2 | - not found - | - not found - |
| `VAL9-B4` | 2 | 1 | - not found - | - not found - |
| `VAL9-B5` | 2 | 1 | - not found - | - not found - |
| `VD10-B1` | 2 | 2 | reviews/2026-09-03-validation-round-two.md:44 | \| **B1** \| The release happens before the only statement that stops the failed locomotive, and it waits on a monitor this file documents as held... |
| `VD10-C14` | 2 | 1 | reviews/2026-09-03-validation-round-two.md:64 | \| **C14** \| `promptNumber` and `promptPercent` announce a setup change after a parse that failed and wrote nothing \| open \| |
| `VD11-C8` | 2 | 2 | reviews/2026-09-03-validation-round-three.md:54 | \| **C8** \| What `VD10-C2`'s coalescing costs, which is small but is not nothing: `onChanged`'s two cache refreshes now run against the pre-rebuil... |
| `VD9-B5` | 2 | 1 | reviews/2026-09-03-validation-of-the-day.md:52 | \| **B5** \| `SVN-B8`'s test passes against the very fix its disposition says it rules out \| fixed \| |
| `VD9-C20` | 2 | 1 | reviews/2026-09-03-validation-of-the-day.md:75 | \| **C20** \| `RGN-B1`'s caption count is incremented before the page write, and the page list is keyed by page name rather than filename \| fixed \| |
| `VD9-C4` | 2 | 1 | reviews/2026-09-03-validation-of-the-day.md:59 | \| **C4** \| `IPR-B4`: `contentOf`'s javadoc now says the opposite of what `contentOf` does \| fixed \| |
| `VD9-C6` | 2 | 1 | reviews/2026-09-03-validation-of-the-day.md:61 | \| **C6** \| `SVN-B11`: the second assertion of the second test cannot fail either way \| fixed \| |
| `VLD-B1` | 2 | 2 | reviews/2026-09-05-single-pass-validation.md:79 | \| B1 \| B \| `legacySignalAddress` matches only the MM2 spelling, so every DCC/MFX-protocol signal - a name this program generates itself, `Signal... |
| `VLD-C1` | 2 | 1 | reviews/2026-09-05-single-pass-validation.md:84 | \| C1 \| C \| The source-shape test passes with the defect fully present: it keeps the *last* `parent.removeAll();` in the file, so re-adding one a... |
| `AC2-C2` | 1 | 1 | reviews/2026-09-04-independent-acceptance.md:55 | \| C2 \| C \| One malformed record in the station's `magnetartikel.cs2` aborts the entire sync - `parseMags` never received the per-record guard al... |
| `AC2-C4` | 1 | 1 | reviews/2026-09-04-independent-acceptance.md:57 | \| C4 \| C \| `Util.getLatestReleaseInfo` leaks its reader and HTTP connection when the response cannot be read \| `Util.java:655-664` \| |
| `AC3-C2` | 1 | 1 | reviews/2026-09-05-fable-acceptance.md:60 | \| C2 \| C \| `importRoutes` throws an undeclared `JSONException` straight through the programmatic API on malformed input; the one UI caller is fu... |
| `ACC-C5` | 1 | 1 | reviews/2026-09-04-release-acceptance-review.md:62 | \| C5 \| C \| `importLegacy`'s javadoc promises "Nothing is written to disk - the caller saves, so a bad match can still be cancelled"; its only ca... |
| `ACC-C9` | 1 | 1 | reviews/2026-09-04-release-acceptance-review.md:66 | \| C9 \| C \| Three graph-display preference constants are dead, their stored values orphaned, and the R28-C3 comment's "no surface shows homes" cl... |
| `AD-B7` | 1 | 1 | reviews/2026-08-17-disposition-audit.md:210 | (mentioned only) **C6.** The page-less `autonomyStationAt(int,int)` overload is dead and would reintroduce `AD-B7` if |
| `AD-B8` | 1 | 1 | reviews/2026-08-17-disposition-audit.md:41 | (mentioned only) \| Incomplete - unfixed twin \| 5 (`AD-A2`, `AD-A3`, `AD-A6`, `AD-B8`, `AD-C15`) \| |
| `AU-C1` | 1 | 1 | reviews/2026-08-25-autonomous-round.md:352 | ### AU-C1 - the autonomy menu's download offer did not ask whether a station was there |
| `CD3-B1` | 1 | 1 | reviews/2026-09-02-comments-and-docs-review.md:83 | \| CD3-B1 \| B \| open \| |
| `CD3-B2` | 1 | 1 | reviews/2026-09-02-comments-and-docs-review.md:84 | \| CD3-B2 \| B \| open \| |
| `CD3-C1` | 1 | 1 | reviews/2026-09-02-comments-and-docs-review.md:86 | \| CD3-C1 \| C \| open \| |
| `CMT-C1` | 1 | 1 | reviews/2026-09-01-comments-and-docs-review.md:62 | \| CMT-C1 \| C \| open \| |
| `CMT-C2` | 1 | 1 | reviews/2026-09-01-comments-and-docs-review.md:63 | \| CMT-C2 \| C \| open \| |
| `CONF-B1` | 1 | 1 | reviews/CONF-confirmation-2026-09-06.md:43 | \| CONF-B1 \| `facingOf` reads the SETUP, which is exactly the staleness `REG6-A2` was fixed for three commits later - the sweep did not reach it \... |
| `CONF-B5` | 1 | 1 | reviews/CONF-confirmation-2026-09-06.md:47 | \| CONF-B5 \| `ef158b98` deleted `testEverySquareOnThisLayoutBuildsToOneCopy`, the control three reviewers used to bound reachability \| B \| Fixed... |
| `CONF-C1` | 1 | 1 | reviews/CONF-confirmation-2026-09-06.md:49 | \| CONF-C1 \| The deleted `isReversing()` clause survives as the `ReversalPolicy` interface default, where the next policy inherits it \| C \| Fixe... |
| `CONF-C2` | 1 | 1 | reviews/CONF-confirmation-2026-09-06.md:50 | \| CONF-C2 \| `facingAfterAPaste`'s `landedOn` parameter is never read, and its javadoc says it is \| C \| Fixed \| CONFIRMED \| |
| `CONF-C3` | 1 | 1 | reviews/CONF-confirmation-2026-09-06.md:51 | \| CONF-C3 \| `headingBeforeTheMove` is not cleared on the cut/clear branch \| C \| Fixed \| CONFIRMED; harmless on a one-copy layout \| |
| `CR-C2` | 1 | 1 | reviews/2026-08-24-conversion-review.md:55 | \| CR-C2 \| open \| |
| `D24-B2` | 1 | 1 | reviews/2026-09-01-fanout-index.md:226 | (mentioned only) ### FX2-3. The reversal-room rule is unsound in two ways (`D24-B2`, `SVN-B2`, `RTG-B2`, `TCX-A1`) |
| `D24-C3` | 1 | 1 | - not found - | - not found - |
| `D24-C4` | 1 | 1 | - not found - | - not found - |
| `D24-C6` | 1 | 1 | - not found - | - not found - |
| `D3F-C1` | 1 | 1 | reviews/2026-09-02-three-days-review.md:152 | ### D3F-C1 - `checkBadCopies` documents the ERROR its own body removed |
| `D3F-C2` | 1 | 1 | reviews/2026-09-02-three-days-review.md:181 | ### D3F-C2 - "It is inert on his railway today" survived being proven false, through a commit that edited the lines beneath it |
| `D3F-C3` | 1 | 1 | reviews/2026-09-02-three-days-review.md:206 | ### D3F-C3 - the start gate widened to `hasErrors()`; every offer-side reader still asks `errorCount()` |
| `D3F-C4` | 1 | 1 | reviews/2026-09-02-three-days-review.md:251 | ### D3F-C4 - the tile door warns about a protecting signal in both directions; the route door refuses only green |
| `DAY-C1` | 1 | 1 | reviews/2026-08-31-day-of-commits-review.md:309 | ### C1 - OB-159 made `liftAboveLabels` unnecessary, and it was left in doing only its harm |
| `DAY-C3` | 1 | 1 | reviews/2026-08-31-day-of-commits-review.md:374 | ### C3 - RC-A11's graceful stop fires from the two manual dispatch doors, where autonomy may never have been running |
| `DD-B5` | 1 | 1 | reviews/2026-08-22-duplication-and-design.md:593 | \| DD-B5 \| The right-click entry point is written four times; the guard is on three \| Open \| |
| `DD-C9` | 1 | 1 | reviews/2026-08-22-duplication-and-design.md:970 | \| DD-C9 \| `TileGraph` has `sideTowards` and `sideToward` - two methods, one letter apart, answering one question two ways \| Open \| |
| `DIR-B4` | 1 | 1 | reviews/2026-09-06-direction-and-consistency.md:94 | \| B4 \| B \| `followDirectionChanges` runs a station-index derive, the whole findings recompute and a grid rebuild on the CS locomotive-message th... |
| `DIR-C10` | 1 | 1 | reviews/2026-09-06-direction-and-consistency.md:104 | \| C10 \| C \| `reachableTiles`'s new closed semantics are the MANUAL tier's, and both callers ask the autonomy question, where `isPathClear` refus... |
| `DIR-C2` | 1 | 1 | reviews/2026-09-06-direction-and-consistency.md:96 | \| C2 \| C \| The pre-route refusal logs nothing while the midway cancel logs two lines - the same operator gesture, two doors, one record \| `Trai... |
| `DIR-C4` | 1 | 1 | reviews/2026-09-06-direction-and-consistency.md:98 | \| C4 \| C \| The one state `flipFacing` declines is the one OB-177 shipped the same day to make visible: a recorded facing the square cannot hold... |
| `DIR-C9` | 1 | 1 | reviews/2026-09-06-direction-and-consistency.md:103 | \| C9 \| C \| The timetable and Return Home get `ALWAYS_REVERSE` by falling through the 4-argument overload, and nothing says that was decided; `Ho... |
| `DOC-A3` | 1 | 1 | reviews/2026-08-28-documentation-review.md:70 | \| **DOC-A3** \| `LayoutEditor.java:3707-3728` - the only warning against growing the diagram at the top is orphaned, and `growEdges` is undocument... |
| `DOC-B10` | 1 | 1 | reviews/2026-08-28-documentation-review.md:297 | \| **DOC-B10** \| `TrainControlUI.java:21607-21609` - "the mark was moved to the MIDDLE"; it was moved back the next day \| Open \| |
| `DOC-B12` | 1 | 1 | reviews/2026-08-28-documentation-review.md:299 | \| **DOC-B12** \| `TrainControlUI.java:2732-2741` - `mountEditPageMenu`'s javadoc now heads `guardLayoutMenu` \| Open \| |
| `DOC-B16` | 1 | 1 | reviews/2026-08-28-documentation-review.md:303 | \| **DOC-B16** \| `LayoutGrid.java:523-530` - `owner`'s javadoc endorses the rule found to be a bug, on a field nothing reads \| Open \| |
| `DOC-B2` | 1 | 1 | reviews/2026-08-28-documentation-review.md:289 | \| **DOC-B2** \| `LayoutEditor.java:3699-3705` - says four methods were removed; they are 46 lines below and on the menu \| Open \| |
| `DOC-C17` | 1 | 1 | reviews/2026-08-28-documentation-review.md:818 | \| **DOC-C17** \| `AutonomyChecks.java:452-455` - `checkReversingGoesSomewhere` gained a parameter the `@param` list omits \| Open \| |
| `DOC-C2` | 1 | 1 | reviews/2026-08-28-documentation-review.md:803 | \| **DOC-C2** \| `LocIconCropDialog.java:63-69` - "Eight is generous on purpose" over `MAX_ZOOM = 32.0` \| Open \| |
| `DOC-C4` | 1 | 1 | reviews/2026-08-28-documentation-review.md:805 | \| **DOC-C4** \| `TrainControlUI.java:1866-1873` - `settleAbsentPages`'s `@return` lost two of null's three meanings \| Open \| |
| `DOC-C5` | 1 | 1 | reviews/2026-08-28-documentation-review.md:806 | \| **DOC-C5** \| `LayoutGrid.java:85-94` - "the right two booleans" for a three-boolean method, and no `@param` for the third \| Open \| |
| `DOC-C6` | 1 | 1 | reviews/2026-08-28-documentation-review.md:807 | \| **DOC-C6** \| `LayoutEditor.java:105` - the summary line names the wrong colour, and the rest of its own javadoc refutes it \| Open \| |
| `DR-B5` | 1 | 1 | reviews/2026-08-24-duplication-robustness.md:142 | \| DR-B5 \| `pageIsHere` restates `pageOf` and must "agree with it exactly" by prose alone \| Open \| |
| `DW-C1` | 1 | 1 | reviews/2026-08-24-day-review.md:40 | \| DW-C1 \| C \| TrainControlUI delete-page path \| Page DELETE has the same stale-naming capture the rename just fixed: forgotten settings are wri... |
| `DW-C4` | 1 | 1 | reviews/2026-08-24-day-review.md:43 | \| DW-C4 \| C \| testTheWindowAttachesItsRefreshCallback \| The `attachAutonomyRefresh` count reads raw source with comments in, in the same test t... |
| `DY3-A1` | 1 | 1 | reviews/2026-09-02-last-day-review.md:49 | ### A1 |
| `DY3-B1` | 1 | 1 | reviews/2026-09-02-last-day-review.md:148 | ### B1 |
| `DY3-C2` | 1 | 1 | reviews/2026-09-02-last-day-review.md:278 | ### C2 |
| `DY3-C6` | 1 | 1 | reviews/2026-09-02-last-day-review.md:502 | ### C6 |
| `F0-F19` | 1 | 1 | - not found - | - not found - |
| `FBR-A1` | 1 | 1 | reviews/2026-08-24-fable-round-review.md:407 | ### FBR-A1 - the `built` flag is read before it is written, and exits a working application |
| `FBR-C4` | 1 | 1 | reviews/2026-08-24-fable-round-review.md:85 | \| **FBR-C4** \| OB-083 closed one instance of a silently-dropped blocker; the general case remains \| **fixed** \| |
| `FBR-C5` | 1 | 1 | reviews/2026-08-24-fable-round-review.md:86 | \| **FBR-C5** \| readShared's javadoc names a parameter that was renamed \| **fixed** \| |
| `FBR-C7` | 1 | 1 | reviews/2026-08-24-fable-round-review.md:616 | ### FBR-C7 - the silent drop FBR-C4 was written about cannot happen |
| `FBR-D19` | 1 | 1 | reviews/2026-08-24-fable-round-review.md:681 | \| **FBR-D19** \| The staging test can fail, and the one precondition it does not assert \| |
| `FBR-D20` | 1 | 1 | reviews/2026-08-24-fable-round-review.md:682 | \| **FBR-D20** \| The picker test can fail, and what it cannot tell you \| |
| `FCR-B3` | 1 | 1 | reviews/2026-07-27-fresh-perspective-review.md:106 | `FCR-B3` made that refresh routine. |
| `FR3-C1` | 1 | 1 | reviews/2026-09-04-final-release-review.md:59 | \| C1 \| C \| The release blocker's re-aimed test asserts only that the stack contains `configureEdge` - true in the preview too, so it cannot dete... |
| `FR3-C4` | 1 | 1 | reviews/2026-09-04-final-release-review.md:62 | \| C4 \| C \| The gutter test's javadoc describes the `paintChildren` mechanism that `15a2878d` reverted, not the `paint()` one it pins \| `testThe... |
| `FR3-C5` | 1 | 1 | reviews/2026-09-04-final-release-review.md:63 | \| C5 \| C \| OV2-C1's lift is real, but with both destination lists empty - the case ACC-C10 is about - the "..." renders bare, no separator, no l... |
| `FSR-C6` | 1 | 1 | reviews/2026-08-24-fable-second-round.md:72 | \| **FSR-C6** \| `System.exit(0)` two lines below the comment that calls it a habit \| **fixed** \| |
| `FV2-B1` | 1 | 1 | reviews/2026-09-01-fix-validation.md:120 | \| **FV2-B1** \| "The guard is inert on his railway" is the reason `FX2-3` was deferred, and it is not established - the two squares that carry bot... |
| `FV2-C1` | 1 | 1 | reviews/2026-09-01-fix-validation.md:280 | \| **FV2-C1** \| The cross's colour clause cannot fire in production; both `Badge` sites make `shut` imply `parking` \| open \| |
| `FV2-C2` | 1 | 1 | reviews/2026-09-01-fix-validation.md:281 | \| **FV2-C2** \| The index says the six measured tiles are "all on the Test page". They are all on `1 - Main` \| open \| |
| `FV2-C6` | 1 | 1 | reviews/2026-09-01-fix-validation.md:285 | \| **FV2-C6** \| `HomeStaging.connected(Point, Point)` has had no caller since 2026-08-31 \| open \| |
| `GC-B2` | 1 | 1 | reviews/2026-08-22-general-code-review.md:8 | **GC-B2 and GC-B3 were both right and both mine, and they are the two worth reading.** Ctrl+G could |
| `IAR-B1` | 1 | 1 | reviews/2026-08-24-independent-application-review.md:232 | \| **IAR-B1** \| `autonomy.json` is no longer in the backup, and used to be \| **Open** \| |
| `IND-M1` | 1 | 1 | - not found - | - not found - |
| `IND9-C4` | 1 | 1 | reviews/IND9-independent-2026-09-07.md:318 | ### IND9-C4 - the drag tests overwrite the runner's clipboard while explaining why they must not |
| `INT-A1` | 1 | 1 | reviews/2026-07-cycle-summary.md:172 | `INT-A1` was found while writing test coverage, not while reviewing. `FCR-B3` was found by the author |
| `INT-A2` | 1 | 1 | reviews/2026-07-cycle-summary.md:142 | (mentioned only) \| 5 \| `INT-D1` decided `setLinkedLocomotives` could stay unsynchronised \| That decision was scoped to the multi-unit dialog; `INT-A2` then calle... |
| `IPR-C5` | 1 | 1 | reviews/2026-08-31-independent-review.md:751 | ### C5 - `getScale`'s "safe against recursion" comment names a reason that is no longer true |
| `LD-C8` | 1 | 1 | reviews/2026-08-25-last-day.md:348 | \| **C8** \| `captionIsActive` may hide every station name in a narrow window \| open \| |
| `LE-C7` | 1 | 1 | reviews/2026-08-30-layout-editor-review.md:382 | ### C7 - `TileSelection.bounds()`'s javadoc was attached to the wrong method |
| `LE-C8` | 1 | 1 | reviews/2026-08-30-layout-editor-review.md:393 | ### C8 - the LE-C3 fix repeated LE-C3, one line below itself |
| `LE2-A8` | 1 | 1 | - not found - | - not found - |
| `LE2-B20` | 1 | 1 | - not found - | - not found - |
| `LE2-B21` | 1 | 1 | - not found - | - not found - |
| `LE2-B9` | 1 | 1 | - not found - | - not found - |
| `LE2-C12` | 1 | 1 | - not found - | - not found - |
| `LE2-C13` | 1 | 1 | - not found - | - not found - |
| `LE2-C14` | 1 | 1 | - not found - | - not found - |
| `OPV-B1` | 1 | 1 | reviews/2026-09-04-opus-validation.md:42 | \| B1 \| B \| Eleven findings closed, one test written: deleting ACC-A1's gate, or ACC-B3's whole flag, leaves 310 tests green - and the round's on... |
| `OPV-C1` | 1 | 1 | reviews/2026-09-04-opus-validation.md:43 | \| C1 \| C \| ACC-C7's modern-file detector asks a point for `blocks`; the key `Point.toJSON` writes is `block`, so the strongest of its three mark... |
| `OPV-C3` | 1 | 1 | reviews/2026-09-04-opus-validation.md:45 | \| C3 \| C \| ACC-C4's fix left the comment three lines above it still saying the facing is "chosen at random", and duplicated a paragraph verbatim... |
| `OV2-B1` | 1 | 1 | reviews/2026-09-04-second-validation.md:19 | OV2-B1, and nothing else in this document would have found it: the test passes, its mutation genuinely |
| `OV2-C1` | 1 | 1 | reviews/2026-09-04-second-validation.md:53 | \| C1 \| C \| OPV-C4 moved the ellipsis inside the More Destinations gate, so when both lists are empty - the case ACC-C10 is about - there is now... |
| `OV2-C2` | 1 | 1 | reviews/2026-09-04-second-validation.md:54 | \| C2 \| C \| `sayIfDeclined` now answers two different questions; the door it excludes DOES carry an edit, and only an unwritten unreachability ma... |
| `OV2-C5` | 1 | 1 | reviews/2026-09-04-second-validation.md:57 | \| C5 \| C \| The exit gate's four conditions are now written out twice and must agree forever, and the copy omits the capture's fifth \| `TrainCon... |
| `RC-A5` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:125 | ### A5 - the warning that explains both was debug-only |
| `RC-B4` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:211 | ### B4 - the route editor is the one child window that does not stay on top |
| `RC-B6` | 1 | 1 | - not found - | - not found - |
| `RC-B7` | 1 | 1 | - not found - | - not found - |
| `RC-B9` | 1 | 1 | - not found - | - not found - |
| `RC-C1` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:250 | ### C1 - "no second dialog results" was made false by OB-140 |
| `RC-C10` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:322 | ### C10 - a guard OB-091 removed, described as still there |
| `RC-C12` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:337 | ### C12 - "resolved ONCE", asked twice more three lines later |
| `RC-C2` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:260 | ### C2 - the overlay strip called itself the scroll pane's column header |
| `RC-C3` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:266 | ### C3 - `fromJSON` said blockedBy names are never resolved, and they are |
| `RC-C4` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:277 | ### C4 - `AutonomySession.save()` promised pruning that DR-B10 made conditional |
| `RC-C5` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:285 | ### C5 - `sideTowardNeighbour` claimed it "asks the graph" |
| `RC-C6` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:293 | ### C6 - the fourth copy of a zoom claim DOC-C24 fixed three times |
| `RC-C7` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:300 | ### C7 - the menu separator described an order Adam changed |
| `RC-C8` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:307 | ### C8 - `homeLoc` is a reference, and its javadoc said "by name" |
| `RC-C9` | 1 | 1 | reviews/2026-08-30-release-candidate-review.md:315 | ### C9 - two comments in one file disagreed about whether an item is disabled |
| `REG6-A2` | 1 | 1 | reviews/REG6-regressions-2026-09-06.md:43 | **`REG6-A2` is the same shape one layer up.** `ca0265f4` made a reversal made during a run get |
| `REG6-B5` | 1 | 1 | reviews/REG6-regressions-2026-09-06.md:318 | ### REG6-B5 - `7d4661d0`'s facing write went into one of the three placement doors |
| `REG7-B1` | 1 | 1 | reviews/REG7-regressions-2026-09-07.md:48 | \| REG7-B1 \| `arrivedFrom` has two stores and no reconciliation: the paste prompt's answer never reaches the running layout until a rebuild; a reb... |
| `REG7-B3` | 1 | 1 | reviews/REG7-regressions-2026-09-07.md:50 | \| REG7-B3 \| The tail walk covers one directional `Edge` per hop while `checkPath` does an exact-edge lookup - the twin edge of a doubled rail, an... |
| `REG8-C3` | 1 | 1 | reviews/REG8-regressions-2026-09-07.md:34 | \| REG8-C3 \| The call-site comment above the room check in `isPathClear` still states the withdrawn all-or-nothing rule ("a path carrying any unme... |
| `REG8-C5` | 1 | 1 | reviews/REG8-regressions-2026-09-07.md:36 | \| REG8-C5 \| Three orphan bundle keys were carefully reworded this week (`layout.ui.tooltipGrowDiagram`, `tooltipShrinkDiagram`, `layout.ui.errorN... |
| `REL-B3` | 1 | 1 | reviews/2026-09-03-release-review.md:56 | \| B3 \| B \| `AUTO_LOAD_AUTONOMY` defaulted from **false** to **true**, so a 2.8.1 user who never ticked the box now loads a configuration on ever... |
| `REL-C1` | 1 | 1 | reviews/2026-09-03-release-review.md:30 | \| C1 \| C \| `connectingFailed()` / `connectingFinished()` are called off the EDT on all three failure paths out of `init` - the marshalling the o... |
| `REL-C5` | 1 | 1 | reviews/2026-09-03-release-review.md:34 | \| C5 \| C \| `tilesWithALocomotive` javadoc claims "the configuration's own order" - the same false `JSONObject`-order claim CD3-C4 corrected on i... |
| `REL-C7` | 1 | 1 | reviews/2026-09-03-release-review.md:36 | \| C7 \| C \| `clearAllHomes` javadoc says the clearing goes "through `session.setHome(tile, null)`"; the body calls `clearEveryHome()` \| `Autonom... |
| `RG4-C3` | 1 | 1 | reviews/2026-09-04-regression-sweep-3.0.0.md:79 | \| **C3** \| open - `AutonomySession.java:875` still says the route-activation drop is "NOT REPORTED YET"; it is reported, and the dialog code even... |
| `RR-C1` | 1 | 1 | reviews/2026-07-27-post-cycle-verification.md:544 | - **`RR-C1`** - `setLinkedLocomotives` stages into a local map and swaps under `synchronized (this)`; |
| `RS-C1` | 1 | 1 | reviews/2026-08-02-pre-release-sweep.md:23 | \| RS-C1 \| `CS2File.parseFileContents` splits array-syntax values on `=` without a limit - the twin of a fix applied to the adjacent non-array bra... |
| `RS-C2` | 1 | 1 | reviews/2026-08-02-pre-release-sweep.md:24 | \| RS-C2 \| `downloadCS2Layout` builds local filenames from page names taken verbatim out of the fetched index, so a name carrying a path separator... |
| `RTG-B2` | 1 | 1 | reviews/2026-09-01-autonomy-routing-review.md:191 | ### RTG-B2 - The backing-over-the-switch guard sums the whole path, but a train that reverses mid-path can only stand on the post-reversal suffix |
| `RV-C2` | 1 | 1 | reviews/2026-08-01-reversing-destinations-review.md:20 | \| RV-C2 \| Filed while validating `b56b407`: the through-berth filter has one call site, so `hasAutonomousDestination` - aligned with `pickPath` o... |
| `SA-C1` | 1 | 1 | reviews/2026-08-18-station-arrivals-review.md:550 | (mentioned only) \| DX2 \| rebuild() reopened the SA-C1 derivation race across the whole rebuild body \| fixed, `ec7973c` \| |
| `SG-A1` | 1 | 1 | reviews/2026-08-30-staging-planner-round.md:43 | ### A1 - two homes on one sensor were proved impossible even with both trains already on them |
| `SPEC-B3` | 1 | 1 | reviews/SPEC-validation-2026-09-06.md:61 | \| SPEC-B3 \| Return Home refuses an inactive START; manual allows it \| B \| Wont-fix \| High \| |
| `SPEC-C4` | 1 | 1 | reviews/SPEC-validation-2026-09-06.md:67 | \| SPEC-C4 \| One door asks the reversal question before the power check, the other after \| C \| Fixed \| High \| |
| `SVN-B14` | 1 | 1 | reviews/2026-09-01-week-of-commits-review.md:380 | \| **SVN-B14** \| The autonomy function slots are written before OK is pressed \| **fixed** 2026-09-02 (`1cfdf370`) \| |
| `SVN-B2` | 1 | 1 | reviews/2026-09-01-week-of-commits-review.md:368 | \| **SVN-B2** \| A partially measured edge is treated as measured, and under-counts the room \| **closed** by `FX2-3` \| |
| `SVN-C1` | 1 | 1 | reviews/2026-09-01-week-of-commits-review.md:1001 | \| **SVN-C1** \| `firstOnTheRailway` is a dead parameter, plumbed three levels \| open \| |
| `SVN-C14` | 1 | 1 | reviews/2026-09-01-week-of-commits-review.md:1014 | \| **SVN-C14** \| Smaller items \| open \| |
| `SVN-C16` | 1 | 1 | reviews/2026-09-01-week-of-commits-review.md:1016 | \| **SVN-C16** \| One `OVERRIDE` disables the guard for accessories the operator was never shown \| open \| |
| `SVN-C17` | 1 | 1 | reviews/2026-09-01-week-of-commits-review.md:1017 | \| **SVN-C17** \| `atomicRoutes` can in principle be flipped between the early release and `unlockPath` \| open \| |
| `SVN-C2` | 1 | 1 | reviews/2026-09-01-week-of-commits-review.md:1002 | \| **SVN-C2** \| `refreshAllProtectingSignals` has no production caller left \| open \| |
| `SVN-C7` | 1 | 1 | reviews/2026-09-01-week-of-commits-review.md:1007 | \| **SVN-C7** \| Nine dead `AutonomyChecks.run` overloads, each defaulting an argument that switches a check off \| open \| |
| `SVN-C8` | 1 | 1 | reviews/2026-09-01-week-of-commits-review.md:1008 | \| **SVN-C8** \| `execCopy` writes the whole setup to disk on every palette click \| open \| |
| `TA-B1` | 1 | 1 | reviews/2026-08-24-test-suite-audit.md:63 | \| TA-B1 \| `testEveryTestIsInTheBattery` is satisfied by a `<test-one-class>` line inside an XML comment **[receipt]** \| B \| Open \| |
| `TA-B3` | 1 | 1 | reviews/2026-08-24-test-suite-audit.md:65 | \| TA-B3 \| `testLoadData`'s seven old-build fixtures are asserted only for non-emptiness; a restore that keeps one component of each file passes a... |
| `TA-C1` | 1 | 1 | reviews/2026-08-24-test-suite-audit.md:73 | \| TA-C1 \| `testABlockerWithAHomeOfItsOwn...` asserts `!= IMPOSSIBLE`, which cannot tell a refusal from a success; a planner with its search remov... |
| `TCX-B10` | 1 | 1 | reviews/2026-09-01-test-suite-review.md:61 | \| TCX-B10 \| `testRoutes` has three unseeded `Random`s, and its property test's only assertion carries no message \| open \| |
| `TCX-B13` | 1 | 1 | reviews/2026-09-01-test-suite-review.md:64 | \| TCX-B13 \| `testFullAutonomyDoesNotDriveThroughAReversingPoint` has no control \| open \| |
| `TCX-B5` | 1 | 1 | reviews/2026-09-01-test-suite-review.md:56 | \| TCX-B5 \| `testAnUnmarkedLayoutIsUntouched` compares a builder against an identically-configured builder \| open \| |
| `TCX-B8` | 1 | 1 | reviews/2026-09-01-test-suite-review.md:59 | \| TCX-B8 \| `testNothingIsLoadedWhenAlreadyHome` survives the mutation it was written for \| open \| |
| `TS-C3` | 1 | 1 | reviews/2026-08-19-test-suite-review.md:29 | \| TS-C3 \| Six of `testRouteInventory`'s eight tests silently `return` (green) when their input bundle is absent, so a pass can mean "did not run"... |
| `TS3-B1` | 1 | 1 | reviews/2026-09-02-second-validation.md:38 | \| `TS3-B1` - the control asks the emitted copies \| `e6791631` \| **Yes**, and it uses the same helper the assertion below it uses (`V32-D4`) \| N... |
| `TS3-B3` | 1 | 1 | reviews/2026-09-02-second-validation.md:39 | \| `TS3-B3` - the surface rule stops grepping a method name \| `e6791631` \| Partly. It pins more strings and dropped the one that tied the tile to... |
| `TS3-C1` | 1 | 1 | reviews/2026-09-02-test-suite-review.md:56 | \| TS3-C1 \| `testAShutPlainSquareReachesTheRunningGraph` has no floor on `copies`, though its own comment says the number is the point - the floor... |
| `TS3-C3` | 1 | 1 | reviews/2026-09-02-test-suite-review.md:58 | \| TS3-C3 \| `testATrainTooLongForTheBerthIsNotBackedOverTheSwitch`'s second stated mutation cannot be told apart by its own fixture, and now descr... |
| `TST-A1` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:47 | \| A1 \| `testEveryKeyParseAutoReadsIsAlsoWritten` omits five keys `parseAuto` reads \| open \| |
| `TST-A2` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:48 | \| A2 \| `testEverySensorTheLegacyConfigUsesIsDerived` compares a set with itself \| open \| |
| `TST-A3` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:31 | TST-A3 - was written against a test method that has since been replaced, and is restated against its |
| `TST-B14` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:354 | \| B14 \| `testAnAddressWithOneLocomotiveIsNotReportedFree`'s closing assertion is a tautology \| open \| |
| `TST-B19` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:359 | \| B19 \| `testStationLabelPrefill` tests `nearestOf` hard and `nearestStation` not at all \| open \| |
| `TST-B21` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:361 | \| B21 \| `testErrorsStopTheSetupRunning` asserts a method with no production callers \| open \| |
| `TST-B22` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:362 | \| B22 \| The address-0 rule is pinned; the dialog that re-implements it is untested \| open \| |
| `TST-B23` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:363 | \| B23 \| `testTheWindowAttachesItsRefreshCallback` strips `//` only, and says so \| open \| |
| `TST-B3` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:343 | \| B3 \| Nothing enforces the `LayoutSandbox` rule; three classes reach the live layout without it \| open \| |
| `TST-B6` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:346 | \| B6 \| `testNoSelfRecursiveWrappers` only detects the `this.`-qualified spelling \| open \| |
| `TST-C12` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:817 | \| C12 \| `testTheDerivedGraphCanBeExportedForInspection` has no assertions and writes into the repo root \| open \| |
| `TST-C13` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:818 | \| C13 \| Fixture constants asserted as results \| open \| |
| `TST-C14` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:819 | \| C14 \| Fixed ports and machine-dependent skips \| open \| |
| `TST-C16` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:821 | \| C16 \| `testTheEditorsArrivalsAreNotCoupledToTheViewersSetting` checks for a method NAME \| open \| |
| `TST-C17` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:822 | \| C17 \| `testFacingFollowsTheTrack` opens a live session on the tracked fixture \| open \| |
| `TST-C2` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:807 | \| C2 \| `testEveryTestIsInTheBattery` cannot see a test method, only a test class \| open \| |
| `TST-C4` | 1 | 1 | reviews/2026-08-28-test-suite-review.md:809 | \| C4 \| `testEveryRuleIsCoveredSomewhere` is a coverage index, not coverage \| open \| |
| `TSX-A1` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:27 | \| A1 \| High \| `roomAtTheEnd` - the measurement the whole reverse-over-switch guard runs on - is written by two writers, read by one reader, and... |
| `TSX-B1` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:28 | \| B1 \| Medium \| Eight GUI classes open a `LayoutSandbox` at a point a throw can skip the close, leaving the operator's machine-global layout pre... |
| `TSX-B2` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:29 | \| B2 \| Medium \| `testARenameReachesTheTimetableOnScreen` survives the mutation its own javadoc names - it drives `timetableSignature`, and nothi... |
| `TSX-B4` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:31 | \| B4 \| Medium \| `AutonomyMenu.refreshEnabled()` - what greys the autonomy menu and what its two tooltips say - has no test anywhere \| Pass 1 \| |
| `TSX-B5` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:32 | \| B5 \| Medium \| `unmeasuredAfterTheLastSwitch`'s stop at the last switch is unreachable by the one fixture that drives it, so deleting the line... |
| `TSX-B6` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:33 | \| B6 \| Medium \| `testNothingAsksForAKeyThatIsNotThere` scans `src/` with no floor, alone among the three scans in its own file, and passes havin... |
| `TSX-C1` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:34 | \| C1 \| Low \| `testEveryLanguageFits`'s "the locale reached the text" control passes when both screenshots are missing \| Pass 1 \| |
| `TSX-C10` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:43 | \| C10 \| Low \| `testStationPriorityDistribution` floors on one sample in four hundred where its own class javadoc promises a tenth, and its list... |
| `TSX-C12` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:45 | \| C12 \| Low \| `testEveryKindOfferedAsACommandIsOneExecutionActsOn` scans from `execRoute` to end of file while its comment says "the dispatch on... |
| `TSX-C13` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:46 | \| C13 \| Low \| Two more "preconditions" that read back what the two lines above them set, in the file split out of the one that states the rule a... |
| `TSX-C19` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:62 | \| C19 \| Low \| `testEveryTestIsInTheBattery` counts ANY annotation as an annotation, including `@Override`, while its own javadoc says what must... |
| `TSX-C3` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:36 | \| C3 \| Low \| `testEditorSwitchClearsPageState.methodSource` anchors `arriveAt` on a call site, not on the declaration, and is rescued only by th... |
| `TSX-C4` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:37 | \| C4 \| Low \| `testTheActivePageDrawsTheSamePictureAsChoosingIt` still renders one page against itself; the real check is the assertion three lin... |
| `TSX-C6` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:39 | \| C6 \| Low \| `testDiagramLooksRight` builds a `TrainControlUI` in `@BeforeClass` and never disposes it, alone among its siblings \| Pass 1 \| |
| `TSX-C7` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:40 | \| C7 \| Low \| `testANumberTooWideForItsSquareIsLeftOut` asserts an empty list with no floor on the reader that produces it \| Pass 1 \| |
| `TSX-C9` | 1 | 1 | reviews/2026-09-03-test-suite-audit.md:42 | \| C9 \| Low \| `testTrainTailClearsEdges` pins an LF inside a `src/` file that `.gitattributes` does not protect, on a repository with `core.autoc... |
| `TV2-C5` | 1 | 1 | reviews/2026-09-01-third-validation.md:308 | \| **TV2-C5** \| Neither assertion in the new test pins the outcome it names; five of the seven outcomes satisfy both \| open \| |
| `TV2-C6` | 1 | 1 | reviews/2026-09-01-third-validation.md:309 | \| **TV2-C6** \| `connected` will not travel through a terminus, but the search can stop at one and go on, so the impossibility proof is tighter th... |
| `UC-C1` | 1 | 1 | reviews/2026-08-01-untouched-code-review.md:49 | UC-C1 in `compareVersions` rather than `parseReleaseVersion`, so the tolerance is not tied to one |
| `UC-C17` | 1 | 1 | reviews/2026-08-01-untouched-code-review.md:455 | \| UC-C17 \| The `UC-C12` fix missed its twin: the sync guard at MarklinControlStation.java:1167 still skips adopting the CS image while a local ov... |
| `UC-C18` | 1 | 1 | reviews/2026-08-01-untouched-code-review.md:456 | \| UC-C18 \| The `UC-C5` fix guards with `isAutoRunning()`, the weakest of the three busy predicates - the staging planning window and the graceful... |
| `UC-C2` | 1 | 1 | reviews/2026-08-01-untouched-code-review.md:120 | \| UC-C2 \| `Feedback N, 1` (space before the state) silently parses as state 0 \| Fixed - the token is trimmed. Pinned by `testFeedbackStateTokenI... |
| `UC-C20` | 1 | 1 | reviews/2026-08-01-untouched-code-review.md:458 | \| UC-C20 \| The `UC-C4` fix normalizes the JSON door only; conditions restored from the locomotive database bypass it \| Fixed one level deeper th... |
| `UC-C21` | 1 | 1 | reviews/2026-08-01-untouched-code-review.md:650 | \| UC-C21 \| The changelog promises launch-pad locomotives "simply stay where they are", but they join the free-agent class, which the A* expansion... |
| `UC-C3` | 1 | 1 | reviews/2026-08-01-untouched-code-review.md:121 | \| UC-C3 \| `locdir` treats every direction string other than `forward` as backward, silently \| Fixed - only forward/backward parse, case-insensit... |
| `UC-C4` | 1 | 1 | reviews/2026-08-01-untouched-code-review.md:122 | \| UC-C4 \| Condition AND/OR precedence is nonstandard and undocumented; ungrouped JSON-origin trees change meaning on an edit round-trip \| Fixed... |
| `UC-C5` | 1 | 1 | reviews/2026-08-01-untouched-code-review.md:123 | \| UC-C5 \| `Layout.renamePoint` enforces neither precondition its sole caller does (unique name, autonomy idle) \| Fixed - `renamePoint` refuses a... |
| `UXR-B1` | 1 | 1 | reviews/2026-08-28-ux-consistency-review.md:147 | \| B1 \| On a Central Station layout the Layout menu guard is never registered at all \| open \| |
| `UXR-B12` | 1 | 1 | reviews/2026-08-28-ux-consistency-review.md:158 | \| B12 \| Every item on the timetable right-click menu is live during a run and all four refuse \| open \| |
| `UXR-B4` | 1 | 1 | reviews/2026-08-28-ux-consistency-review.md:150 | \| B4 \| The function-icon dialog focuses its field after the dialog has closed \| open \| |
| `UXR-C1` | 1 | 1 | reviews/2026-08-28-ux-consistency-review.md:482 | \| C1 \| One sentence serves as a dialog body and three different menu items, and on the Layout menu it names the wrong subject \| open \| |
| `UXR-C13` | 1 | 1 | reviews/2026-08-28-ux-consistency-review.md:494 | \| C13 \| The Autonomy menu's Edit submenu does not ask the guard's fourth refusal, though its sibling does \| open \| |
| `UXR-C14` | 1 | 1 | reviews/2026-08-28-ux-consistency-review.md:495 | \| C14 \| "Deselect All" and "Clear Selection" are the same action under two labels in one popup \| open \| |
| `UXR-C17` | 1 | 1 | reviews/2026-08-28-ux-consistency-review.md:498 | \| C17 \| `AutonomyMenu.refreshEnabled` asks `getLayoutList().isEmpty()` live \| open \| |
| `UXR-C18` | 1 | 1 | reviews/2026-08-28-ux-consistency-review.md:499 | \| C18 \| Latent double-add in the layout editor's right-click menu \| open \| |
| `UXR-C19` | 1 | 1 | reviews/2026-08-28-ux-consistency-review.md:500 | \| C19 \| `HomeLocomotiveMenu` is two-thirds dead and its javadoc claims three callers \| open \| |
| `UXR-C3` | 1 | 1 | reviews/2026-08-28-ux-consistency-review.md:484 | \| C3 \| The data-source item: stale tooltip, dead string, and a 17th site asking a centralised question \| open \| |
| `UXR-C5` | 1 | 1 | reviews/2026-08-28-ux-consistency-review.md:486 | \| C5 \| Three near-identical "wait for the locomotives" sentences \| open \| |
| `V31-B2` | 1 | 1 | reviews/2026-09-02-first-validation.md:41 | \| `V31-B2` \| `SVN-B7`'s disposition is not honest: the concurrent double-run it names cannot happen, and the guard added is a 600 ms debounce rat... |
| `V32-B2` | 1 | 1 | reviews/2026-09-02-second-validation.md:60 | \| **V32-B2** \| `testSwitchingAnAccessoryByHandAsksAboutProtectingSignals` can no longer see the tile's guard gutted, and its `MUTATION` line name... |
| `V32-C3` | 1 | 1 | reviews/2026-09-02-second-validation.md:215 | \| **V32-C3** \| The aspect comment justifies itself with `doSwitch()`, which is not what `execSwitching` runs for the second limb it was written f... |
| `V32-C4` | 1 | 1 | reviews/2026-09-02-second-validation.md:216 | \| **V32-C4** \| The new staging test's comment says the defect is shuffle-dependent. It is not - the pre-fix code fails deterministically \| open \| |
| `V32-C5` | 1 | 1 | reviews/2026-09-02-second-validation.md:217 | \| **V32-C5** \| The aspect is read on the event thread and re-read by `doSwitch` on the switching worker, so a protection command landing between... |
| `V33-C10` | 1 | 1 | reviews/2026-09-02-second-validation.md:374 | (mentioned only) and says why the repetition is kept anyway. Cited there as `V33-C10`. |
| `V33-C11` | 1 | 1 | reviews/2026-09-02-fourth-validation.md:435 | (mentioned only) // THE EDITOR TOO, not only the window that owns it (V33-C11). |
| `V33-C12` | 1 | 1 | - not found - | - not found - |
| `V33-C6` | 1 | 1 | - not found - | - not found - |
| `V33-C7` | 1 | 1 | reviews/2026-09-02-test-suite-review.md:392 | (mentioned only) **Disposition: fixed, confirmed 2026-09-05** - fixed under a different tag - `testEditorSurfaceRules.java:605` now guards the negative with `assert... |
| `V33-C9` | 1 | 1 | reviews/2026-09-02-third-validation.md:364 | (mentioned only) no longer be satisfied by the room rule's absence. It is cited there as `V33-C9`. |
| `V34-C5` | 1 | 1 | reviews/2026-09-02-fourth-validation.md:134 | \| **V34-C5** \| The three-way comment omits the drives commanded to red \| open \| |
| `V34-C6` | 1 | 1 | reviews/2026-09-02-fourth-validation.md:135 | \| **V34-C6** \| The two new affordance assertions are whole-file greps \| open \| |
| `V34-C7` | 1 | 1 | reviews/2026-09-02-fourth-validation.md:136 | \| **V34-C7** \| The palette test's new dispose runs before the preference restore \| open \| |
| `V35-C2` | 1 | 1 | reviews/2026-09-02-fifth-validation.md:32 | \| V35-C2 \| C \| open \| the new protecting-signal question is asked only in the power-on branch; "power on and proceed" reaches the signal unaske... |
| `V36-A1` | 1 | 1 | reviews/2026-09-02-sixth-validation.md:39 | \| A1 \| `canReachAnyDestination` is autonomy's dispatch question, not Return Home's. The `trapped` screen has false positives in both flag directi... |
| `V36-B2` | 1 | 1 | reviews/2026-09-02-sixth-validation.md:41 | \| B2 \| `testHomeStaging`'s new precondition is stated backwards and cannot fail; the `mustBackIn` confound is now guarded only by `setReversible(... |
| `V36-C5` | 1 | 1 | reviews/2026-09-02-sixth-validation.md:46 | \| C5 \| `LD-C6`'s cost at this door is reduced from three walks of `check()` to two, not closed; the comment reads as though it were \| C \| Open \| |
| `V37-C1` | 1 | 1 | reviews/2026-09-02-seventh-validation.md:51 | \| C1 \| `V36-C`'s recorded reason is false: `autonomyHasErrors()` never appears in a comment in that file, and `V36-C3` said so \| C \| Open \| |
| `V37-C2` | 1 | 1 | reviews/2026-09-02-seventh-validation.md:52 | \| C2 \| The twin site five lines below is still a whole-file `contains`, in the same test method, under the comment arguing that shape is inadequa... |
| `VAL-C1` | 1 | 1 | reviews/2026-08-29-round-validation.md:313 | **VAL-C1.** `waitingToClear` entries are `{ index, behind, edgesSince }`. With `tailMayStillBeOn` gone, |
| `VAL-C4` | 1 | 1 | reviews/2026-08-29-round-validation.md:330 | **VAL-C4.** `guardLayoutMenu`'s loop sets `child.setEnabled(!busy)` on every unexcluded child, and |
| `VAL-C5` | 1 | 1 | reviews/2026-08-29-round-validation.md:338 | **VAL-C5.** See VAL-B1. Error path only. |
| `VAL-D4` | 1 | 1 | reviews/2026-08-29-round-validation.md:409 | **VAL-D4. The current LoadingSpinner is correct, verified by rendering.** Not the fix the brief |
| `VAL8-B1` | 1 | 1 | reviews/VAL8-validation-2026-09-07.md:22 | \| VAL8-B1 \| B \| The "Train arrived from" menu cannot appear in the autonomy editor - `setRunningLayoutSource` is only ever called from the track... |
| `VAL8-B4` | 1 | 1 | reviews/VAL8-validation-2026-09-07.md:25 | \| VAL8-B4 \| B \| Neither `moveLocomotive` nor the hand-placement door clears or sets the live Point's `arrivedFrom`, so a newly placed train inhe... |
| `VAL9-B1` | 1 | 1 | reviews/VAL9-validation-2026-09-07.md:99 | ### VAL9-B1 - the whole window half of IND9-B4 is unpinned; three mutations survive |
| `VD10-A2` | 1 | 1 | reviews/2026-09-03-validation-round-two.md:43 | \| **A2** \| `MT-246`'s persistence half rests on a premise the wiring refutes: `onDiagramChanged` is `repaintLayout()`, not `session.save()`. The... |
| `VD10-C15` | 1 | 1 | reviews/2026-09-03-validation-round-two.md:65 | \| **C15** \| `Layout.java:5512` says `atomicRoutes` on is "what Adam runs". His active configuration says otherwise, and `A1` is the cost \| open \| |
| `VD10-C5` | 1 | 1 | reviews/2026-09-03-validation-round-two.md:55 | \| **C5** \| `VD9-C13`'s replacement invents a period in which "four" was right. When `Kind` had seven members the predicate admitted two, so five... |
| `VD10-C6` | 1 | 1 | reviews/2026-09-03-validation-round-two.md:56 | \| **C6** \| `VD9-C20`'s per-page count now understates the clause it is grammatically bound to, and the filename half was reworded rather than fix... |
| `VD10-C7` | 1 | 1 | reviews/2026-09-03-validation-round-two.md:57 | \| **C7** \| `VD9-C9` cites `forgetBeforeEdit`'s licence for torn writes at a site where that method's own javadoc says it does not apply \| open \| |
| `VD10-C8` | 1 | 1 | reviews/2026-09-03-validation-round-two.md:58 | \| **C8** \| `VD9-C15` renamed the test and left the sentence it was about standing three lines under the new disclaimer \| open \| |
| `VD11-A2` | 1 | 1 | reviews/2026-09-03-validation-round-three.md:43 | \| **A2** \| Every door that ends in `setupChanged()` performs an **unconditional emergency stop of every locomotive** and a tab jump, because the... |
| `VD11-B1` | 1 | 1 | reviews/2026-09-03-validation-round-three.md:44 | \| **B1** \| `FR-058`'s ellipsis now fires when nothing is hidden: `possible` counts the *More Destinations* entries as left out. On Adam's own con... |
| `VD11-B2` | 1 | 1 | reviews/2026-09-03-validation-round-three.md:45 | \| **B2** \| `VD10-B6`'s "a failure is recorded" records into a field nothing reads and nothing clears. `didTheNoteRepairFail()` has no callers, an... |
| `VD11-C1` | 1 | 1 | reviews/2026-09-03-validation-round-three.md:47 | \| **C1** \| `Layout.java:5535-5541` - `VD10-C15`'s replacement was spliced into the middle of the sentence it was correcting, at the wrong indent,... |
| `VD11-C2` | 1 | 1 | reviews/2026-09-03-validation-round-three.md:48 | \| **C2** \| `FR-058`'s comment says *More Destinations* holds a terminus a non-reversible train could not leave. It does not - `isChoosableByAuton... |
| `VD11-C3` | 1 | 1 | reviews/2026-09-03-validation-round-three.md:49 | \| **C3** \| When every valid destination is non-choosable, *More Destinations* is added with no separator and no locomotive-name header: both are... |
| `VD11-C6` | 1 | 1 | reviews/2026-09-03-validation-round-three.md:52 | \| **C6** \| The new `FR-058` test's last assertion cannot fail: it asserts the setter it called two lines above \| fixed \| |
| `VD11-C9` | 1 | 1 | reviews/2026-09-03-validation-round-three.md:55 | \| **C9** \| *More Destinations* is uncapped by argument rather than by measurement; ~25 items are reachable on the operator's layout \| fixed \| |
| `VD9-B3` | 1 | 1 | reviews/2026-09-03-validation-of-the-day.md:50 | \| **B3** \| `SVN-B8`'s "correct rather than lucky" is neither, and the half of the finding that actually holds is the unfixed one \| fixed \| |
| `VD9-B6` | 1 | 1 | reviews/2026-09-03-validation-of-the-day.md:53 | \| **B6** \| `SVN-B1` replaced one wrong sentence with two: the guard *is* blind on an unmeasured reversal square, and an unmeasured stretch *earli... |
| `VD9-B7` | 1 | 1 | reviews/2026-09-03-validation-of-the-day.md:54 | \| **B7** \| `SVN-B1`'s refuted sentence still stands, more strongly, at the sibling that actually builds the notice \| fixed \| |
| `VD9-B8` | 1 | 1 | reviews/2026-09-03-validation-of-the-day.md:55 | \| **B8** \| `RGN-B1` promises a `.bak` for every migrated page, in the Readme and in the log; `saveChanges` writes one only if there is not one al... |
| `VD9-C12` | 1 | 1 | reviews/2026-09-03-validation-of-the-day.md:67 | \| **C12** \| Stale line citations written into permanent source and docs, in four places \| fixed \| |
| `VD9-C13` | 1 | 1 | reviews/2026-09-03-validation-of-the-day.md:68 | \| **C13** \| `REL-C13`'s replacement javadoc says four kinds are absent; nine are \| fixed \| |
| `VD9-C14` | 1 | 1 | reviews/2026-09-03-validation-of-the-day.md:69 | \| **C14** \| `TSX-C17` is dispositioned fixed with its third clause unfixed \| fixed \| |
| `VD9-C15` | 1 | 1 | reviews/2026-09-03-validation-of-the-day.md:70 | \| **C15** \| `testEverySandboxIsClosedOnEveryPath` enforces "inside a `try`", which is not what its name or its message promises \| fixed \| |
| `VD9-C19` | 1 | 1 | reviews/2026-09-03-validation-of-the-day.md:74 | \| **C19** \| `RGN-B1`'s test asserts a second half that cannot fail, and the disposition counts it as coverage \| fixed \| |
| `VLD-B3` | 1 | 1 | reviews/2026-09-05-single-pass-validation.md:81 | \| B3 \| B \| `refreshLocSelectorList` was converted but `filterLocList`, which it calls, kept its own `invokeLater` - so the locomotive selector s... |
| `VLD-B4` | 1 | 1 | reviews/2026-09-05-single-pass-validation.md:82 | \| B4 \| B \| `LayoutPopupUI` empties the diagram panel itself, one line before building the grid, so the popup diagram window keeps the blank-pane... |
| `VLD-C3` | 1 | 1 | reviews/2026-09-05-single-pass-validation.md:86 | \| C3 \| C \| `deleteRoute` is `duplicateRoute`'s sibling and never got its "and again after the sync" second refresh; the conversion removes the l... |
| `VLD-C7` | 1 | 1 | reviews/2026-09-05-single-pass-validation.md:90 | \| C7 \| C \| Two small corrections: a comment says "the four names this file writes for itself" over a three-element set, and the new signal scan... |
| `WK-C3` | 1 | 1 | reviews/2026-08-28-week-review.md:150 | \| **C3** \| The page-sized spinner may centre its hourglass outside the visible viewport \| open, needs hands-on verification \| |
| `WK3-A1` | 1 | 1 | reviews/2026-09-02-fanout-index.md:82 | \| `WK3-A1` / `DY3-A1` \| two, independently \| `protectsAnOccupiedSquare` was `synchronized` and called from the event thread, four lines below th... |
| `WK3-C1` | 1 | 1 | reviews/2026-09-02-week-of-commits-review.md:74 | \| `WK3-C1` \| C \| `SVN-B10` widened the Start guard to `hasErrors()` and left the affordance on `errorCount()`, and left a javadoc saying they re... |
