# Regression sweep: the whole of 3.0.0 against 2.8.1, measured

**Status:** open

**Prefix for citing these findings elsewhere:** `RG4`

**Reviewed:** branch `autonomy-diagram-r0` on 2026-09-04, at `2cef4211` - three commits past
`v3_0_0_rc13` (`47d8d1bc`), because the tree moved twice while this pass ran (`83fb8456`,
`2cef4211`). Every HEAD citation below was read at `2cef4211` via `git show HEAD:` rather than from
the working tree, because Adam was operating the railway and editing `LayoutGrid.java`,
`LayoutEditor.java` and `AutonomyEditorPanel.java` throughout; the two working-tree reads this pass
did make (his live `configuration-Main.json` globals, for `RG4-C1`) are marked as such.
`cs2_sample_layout/` was read and never written.

**Baseline:** branch `master` at `5f0a75e3` (`RAW_VERSION = "2.8.1"`), reached with two-dot diffs
and `git show master:`, per `R28`'s reasoning. A mid-pass scare that `master` had grown new commits
resolved to shared history (`RG4-D2`); the baseline is exactly what `R28` and `RG3` reviewed.

**The question, and what is left of it.** Three passes have already asked what a 2.8.1 user loses -
[`RGN`](2026-08-31-regression-review.md) (vs 2.7.4c), [`R28`](2026-09-01-regression-vs-2.8.1-review.md)
and [`RG3`](2026-09-02-regression-vs-2.8.1-review.md) - and all three were forbidden to execute
anything. Each named the same two gaps: *measure the legacy edge data against what the diagram
derives* ("the single most valuable thing somebody with a machine could do" - `R28`, open question
2), and *autonomy runtime parity*. The parity harness has since run (2026-09-03), which changes what
a fourth pass should be: not a fourth key-sweep - `RG3-D2` already ran the sweep at both revisions
and subtracted - but the measurements the readers could not make, plus a re-audit of what the three
passes concluded, done against the bodies rather than the tables (two of their claims turned out to
be wrong - `RG4-D1`).

**Method.** (1) The derived setup the parity harness emitted from Adam's own configuration
(`../traincontrol-parity/v3_0_0/autonomy.json`, built 2026-09-03 from his live railway) was compared
accessory-by-accessory and state-by-state against his 2.8.1 file - the comparison `R28-B1` and
`ACC-B1` both said they could not make. (2) The parity TSVs were re-analysed in the direction
`compare.py` never asks: journeys 2.8.1 *forbade* running together that 3.0.0 now allows. (3) The
downgrade matrix - every artefact 3.0.0 writes that 2.8.1 also reads - was walked to completion.
(4) Every open or recently-closed finding of the three predecessor passes was re-derived at HEAD
from the code, not from its disposition. (5) The 30 commits touching shared (pre-3.0.0) code since
`RG3`'s baseline `cf048f9b` were read for 2.8.1-visible semantic changes; the same-day validation
rounds (`VD9`-`VD11`, `OPV`, `OV2`, `ACC`, `FR3`, `AC2`) cover their correctness and are cited, not
re-derived. Freshness of the Sep-3 derivation artefacts was verified: the only `AutonomyBuilder`
edits since the parity jar was built are comment-only (`0a9f9ffc`, `0eda3843`, both checked
line-by-line), and `GraphReducer.java` is untouched since.

**What was executed.** The comparisons in (1) and (2) run over artefacts produced by *executing both
engines* against copies of Adam's layout (the parity harness's own TSVs and JSON emissions); the
analysis scripts are in this session's scratchpad and their full output is quoted in the findings.
`one.sh` was attempted four times across the pass for a direct test-class run at HEAD and refused
each time by its own guard - another test JVM was running throughout this pass (Adam's, or the concurrent `AC2`
round's) - and per the guard's instruction it was waited on, not killed. So no TestNG class was run
by this pass; the most recent runs on record are `OV2`'s 211/0/0 at `233ee0e9` and `AC2`'s single
class at `83fb8456`.

---

## Verdict

**Nothing here should hold the release, and the news is better than the open findings imply - with
two named questions that are Adam's to answer before, or shortly after, tagging.** The measurements
narrow the two big open regressions sharply: of the 65 accessories his 2.8.1 edge commands drive,
**64 come back** from the diagram derivation - `R28-B1`'s "fifteen lost signals" is actually one
signal (61) plus four whose *red* aspect lost its author (`RG4-B1`, five pairings to restore by
hand, using a gesture that already exists). The hand-written lock worry `ACC-B1` deliberately
counted rather than compared is now compared: of 78 journey-pairs 2.8.1 forbade running together, 75
are still forbidden and **three are now allowed** - all involving `BottomInner -> Tunnel` against
`* -> BottomSecondary` - and whether those three are physically safe is a question only Adam can
answer (`RG4-B2`). Everything else came back clean or fixed: the sekunde delay change the brief
carried as "known" turns out to have shipped *in* 2.8.1, so this upgrade does not change it at all
(`RG4-D1` - two prior findings corrected); the downgrade matrix has exactly the two documented
hazards and no third; and every capability removal the predecessors filed is either restored,
successor-covered, or Adam-ruled (`RG4-D3`-`D16`).

| | |
|---|---|
| **A** | none - stated as a claim: the three candidates raised during this pass each resolved to a B, a D, or an already-fixed finding, and the section says which |
| **B1** | open, needs Adam - five of the twelve signals 2.8.1 drove red have no red author at 3.0.0; the pairing gesture can restore all five, but nothing names them |
| **B2** | open, needs Adam - three journey-pairs his hand-written locks forbade can now run concurrently; the derivation believes the paths are disjoint |
| **C1** | question for Adam - his live pacing globals differ from his 2.8.1 file (delays 3-13s -> 1-2s, inactive-rotation off); tuning or residue, and a re-import cannot repair them |
| **C2** | open - the parity harness checks concurrency in one direction only, and the unchecked direction is the one the lock-import warning warns about |
| **C3** | open - `AutonomySession.java:875` still says the route-activation drop is "NOT REPORTED YET"; it is reported, and the dialog code even quotes this comment as history |
| **D1-D16** | corrections to prior findings, the downgrade matrix, and the successor census |

---

## A - high

**None.** Three candidates were raised and resolved:

- **"A route edit silently discards the route-activation selection"** - found independently by the
  concurrent `AC2` pass as `AC2-A1` and fixed at `2cef4211`, the commit this pass reviewed at. Not
  re-filed; the fix commit is inside this pass's window and its mechanism was read.
- **"Five signals now show green behind occupied platforms"** - the physical consequence is real but
  the restoring gesture exists and takes minutes once the signals are named; filed as `RG4-B1`, the
  same severity logic that made `ACC-B1` a B.
- **"Two trains can now be dispatched where 2.8.1 forbade it"** - the derivation's disjointness
  claim could not be shown false (and derives from shared tiles, which prior rounds validated), so
  the finding is the *question*, not a demonstrated collision. `RG4-B2`.

---

## B - medium

### B1 - five of the twelve signals 2.8.1 drove red have no red author at 3.0.0

| | |
|---|---|
| **Disposition** | code half fixed; data half still needs Adam - five pairings, or a ruling that these signals are decorative |
| **Confidence** | the accessory/state comparison is measured, not read, over the harness-emitted derivation of his real configuration (2026-09-03) - script output quoted below. NOT verified: whether Adam deliberately left these five unpaired; whether any of the five signals switches a stop-section rather than a lamp; and the derivation artefact is one day older than rc13 (the derivation code is unchanged since - `AutonomyBuilder`/`GraphReducer` checked commit-by-commit) |

At 2.8.1, twelve signals were driven to **red** by hand-authored edge commands - arrivals set the
platform's protecting signal against following trains. At 3.0.0 that job belongs to the
`protectingSignal` pairing: the builder emits it per point (`AutonomyBuilder.java:894-898`), the
runtime holds the signal red while the platform is claimed (`Layout.java:7560-7578` reads it,
`Layout.java:6231` and `:6369` drive and guard it), and Adam's own configuration pairs **seven** of
the twelve (38, 40, 63, 64, 86, 87, 94 - plus 107 and 108, pairings the legacy file never had).

Measured over his legacy file against the derived setup, per signal that loses a state:

    Signal 37   legacy [green, red]   derived [green]   paired: NO
    Signal 39   legacy [green, red]   derived [green]   paired: NO
    Signal 61   legacy [green, red]   derived []        paired: NO   <- vanishes entirely
    Signal 62   legacy [green, red]   derived [green]   paired: NO
    Signal 116  legacy [green, red]   derived [green]   paired: NO

and where each red was authored, from the file:

    Signal 37   red on  LowerDownPre -> LowerDown
    Signal 39   red on  RampDown -> BottomInner
    Signal 61   red on  BottomSecondaryPre -> BottomSecondary
    Signal 62   red on  BottomSecondaryPre -> BottomSecondary,  LowerUp -> BottomInnerOtherside,
                        TunnelReverse -> BottomInnerOtherside
    Signal 116  red on  Tunnel -> BottomMainBCPre,  BottomMainCTerm -> TunnelReversePre

So on the physical railway, after every 3.0.0 arrival at LowerDown, BottomInner, BottomSecondary or
BottomInnerOtherside, the signal a 2.8.1 arrival would have set red stays green - it is still set
*green* whenever a route passes it, and nothing ever sets it back. The model does not obey signals
(locks carry the safety), so no autonomous train is misrouted by this; what is wrong is the aspect
shown on the layout - and, if any of these five switches a braking/stop section rather than a lamp,
the electrical state of that section under a manually-driven train.

**The restoring gesture exists and reaches all five.** Pair Signal is on every station's menu and
takes a signal *by address*, with no adjacency requirement (`AutonomyEditorPanel.java:4452`
`askAboutProtectingSignals`, `:4680` `pairSignalsByAddress` - read from `git show HEAD:`, since the
working-tree file is being edited). So this is not `R28-B1`'s "cannot be authored anywhere" for
these five - it is that **nothing names them**: the import dialog's left-behind report counts "69
connections with commands" in aggregate (`AutonomySession.java:946` `whatALegacyImportLeaves`), and
a user who paired seven signals from memory has no way to see which five are still missing.

**Why B.** Same class and same grading as `ACC-B1`: operator-authored safety-adjacent data with no
automatic successor, disclosed only in aggregate. It does not climb to A because the trains
themselves are governed by locks, and it does not fall to C because the layout visibly lies about
occupancy at four stations until five specific pairings are made by hand.

**What would close it.** Either Adam pairs the five (this list is the work order), or rules that
these signals are decorative. A cheap code half, if wanted: have `whatALegacyImportLeaves` name the
accessories whose *states* the derivation never issues - the comparison is ~20 lines against the
builder's own emission and errs in the safe direction, unlike the edge-by-edge comparison the
current count rightly avoids.

### B2 - three journey-pairs 2.8.1 forbade running together are now allowed to run concurrently

| | |
|---|---|
| **Disposition** | closed - not a regression.  Adam, 2026-09-04: **"These are all valid."**  All three pairs may run together; the 2.8.1 locks were more conservative than the track requires. |
| **Confidence** | measured from the parity harness's own lock sets (both engines executed 2026-09-03, `../traincontrol-parity/out/*.tsv`), using `compare.py`'s own readers and normalisation so the vocabulary matches the shipped report. NOT verified: the physical layout - the whole finding is that only Adam can |

`compare.py` asks whether any pair 2.8.1 allowed has *stopped* being concurrent
(`docs/tools/parity/compare.py:339`, `judgeable - new_pairs`) and reports none. It never asks the
reverse - which is the direction `ACC-B1`'s import warning is about: *"the consequence is two trains
permitted to move at once where the file forbade it."* Asked here, over the same TSVs:

    route pairs judgeable (exist at both revisions, different trains): 105
    forbidden at 2.8.1:                                                 78
    still forbidden at 3.0.0:                                           75
    forbidden at 2.8.1, ALLOWED at 3.0.0:                                3

      PARITY-901: BottomMainA -> BottomSecondary  ||  PARITY-904: BottomInner -> Tunnel
      PARITY-902: BottomMainB -> BottomSecondary  ||  PARITY-904: BottomInner -> Tunnel
      PARITY-903: BottomMainC -> BottomSecondary  ||  PARITY-904: BottomInner -> Tunnel

What forbade them at 2.8.1 is his hand-written lock data: each `* -> BottomSecondary` route locked
`BottomInner -> BottomCrossover` and `BottomCrossover -> TunnelPre` - the corridor
`BottomInner -> Tunnel` runs through - via `lockedges`. At 3.0.0 both journeys exist but read
differently off the diagram: `BottomInner -> Tunnel` is the two-edge
`BottomInner (northbound) -> 1 - Main 12,7 -> Tunnel (southbound)`, and `* -> BottomSecondary`
arrives via `TopMainR1/R2 -> TopMainPost -> RampDown (southbound) -> BottomSecondary` (which is also
what satisfies the red-signal ruling recorded for these routes). The derived lock sets are disjoint,
so both trains may be dispatched at once.

Two readings, and the data cannot pick between them:

- **The 2.8.1 locks were conservative** - the routes never shared iron, and the hand lock was belt
  and braces (or written for an older variant of the route). Then 3.0.0 is simply less wasteful, and
  this is the concurrency *gain* the parity report celebrates.
- **The lock encoded something the tiles cannot see** - the crossover's clearance, an electrical
  section, the tunnel's single bore. Then two trains can now be moving toward the same physical
  space, and no square-sharing check will ever refuse it.

`GraphReducer.deriveLocks` locks edges that share a tile, which prior rounds validated; if the two
3.0.0 paths genuinely draw through disjoint squares, the derivation is internally correct either
way. The 2.8.1 file's 116 lock references across 50 edges are disclosed at import in aggregate
(`AutonomySession.java:990`, `:999`); these three pairs are the first *named* consequence.

**The corridor is also where every other residual difference concentrates.** The state-level
command comparison (D4) found exactly four switch-states his file commands that the derived setup
never issues - `Switch 50 straight` and `52 turn` on `BottomInner -> BottomCrossover`, `Switch 58
turn` and `59 straight` on `LowerUp -> BottomInnerOtherside` - the two legacy edges of this same
corridor, whose 3.0.0 journeys run through different squares. And three of `RG4-B1`'s five
unauthored reds (39, 61, 62) guard its ends. One physical answer from Adam about this corner of the
railway settles all three residue groups at once.

**Why B.** The worst case is two trains converging on real track, which would be an A - but it is
not demonstrated, and the equally likely case is a deliberate improvement. This is exactly the
question `ACC-B1` said should go to the operator "counted rather than compared"; it is now compared,
and the answer is three rows, not 116.

**What would close it.** One sentence from Adam per reading. If the lock was real, the successor is
the `menuBlockedByPoints` authoring gesture (lock edges survive the build - `R28-B1` established
that); if conservative, `RG4-C2` still stands so the *harness* keeps watching this direction on
future re-runs.

---

## C - low

### C1 - the live railway's pacing differs from the 2.8.1 file, and a re-import cannot repair it

| | |
|---|---|
| **Disposition** | question for Adam - data, not code; nothing to fix in the tree |
| **Confidence** | the values are read from his live `configuration-Main.json` working tree (globals block) and his frozen fixture copy, both on 2026-09-04; the 2.8.1 values from `autonomy_legacy/autonomy.json`. NOT verified: whether these are his own tuning - he was actively testing (simulate=true in the same block) while this pass ran |

His 2.8.1 file says trains pause 3-13 seconds at stations, rotate a locomotive idle for more than
120 seconds, and run atomic routes. His live 3.0.0 globals
(`cs2_sample_layout/config/autonomy/configuration-Main.json`, read only) say:

| key | 2.8.1 file | live 3.0.0 |
|---|---|---|
| `minDelay` / `maxDelay` | 3 / 13 | 1 / 2 |
| `maxLocInactiveSeconds` | 120 | 0 (off) |
| `atomicRoutes` | true | false |

`atomicRoutes` and the delays look like his current non-atomic testing (the MT-266 family), and
`simulate: true` in the same block says the railway is mid-experiment. But if any of these is
instead the residue of an import made before `RGN-A1` was fixed, it will never self-heal: the fixed
import gap-fills (`AutonomySession.java` - `if (settings.has(key)) continue;`), so keys that already
hold a wrong value are exactly the ones a re-import will not touch. `RGN-A1` measured this exact
file losing `minDelay 3 -> 1`, `maxDelay 13 -> 5`, `maxLocInactiveSeconds 120 -> 0` through the
pre-fix import, and two of those three match what stands today.

Listed with the two `setup.json` residuals already waiting on him
(`2026-09-03-questions-for-adam.md` item 3); this is the third of that kind. If the pacing is
deliberate, say so and this closes as tuning.

### C2 - the parity harness's concurrency check is one-directional

| | |
|---|---|
| **Disposition** | fixed |
| **Confidence** | the gap is read from `compare.py:331-349` and demonstrated by `RG4-B2`'s three rows, which the shipped report does not contain |

Section 3 of the parity report computes `judgeable - new_pairs` - "over-eager locking", routes 3.0.0
wrongly believes collide - and prints a headline when it is empty. The reverse set (pairs the 2.8.1
lock data forbade that 3.0.0 permits) is never computed, and it is the direction with the safety
consequence: an over-eager lock wastes throughput, a dropped one converges trains. The import-time
warning about hand-written locks (`autosetup.ui.leftEdgeLocks`) exists precisely because of that
asymmetry, and the harness that could check it structurally doesn't.

`RG4-B2`'s numbers came from `compare.py`'s own functions (`locks_of`, `concurrent_pairs`,
`by_station`) driven in the other direction - the addition is genuinely small, and the next parity
re-run (which `ACC-C2`'s disposition already anticipates) would then carry both headlines.

### C3 - the comment above the route-activation skip still claims the reporting gap it once named

| | |
|---|---|
| **Disposition** | fixed |
| **Confidence** | both sites read at HEAD (`git show HEAD:`); the reporting path traced from the dialog to the bundle key in all eight languages |

`AutonomySession.java:875-878`:

    // NOT REPORTED YET, and that is a gap worth naming rather than papering over: the
    // import dialog counts what it matched and what it skipped, and says nothing about
    // these.  A user who really did have route activations keeps them in autonomy.json,
    // which is untouched and readable, but nothing tells them to look.

Every sentence of that is now false: `whatALegacyImportLeaves` raises
`autosetup.ui.leftRouteActivations` (`AutonomySession.java:1039-1042`), the import path calls it and
logs the list with the dialog pointing at it (`AutonomyViewerPanel.java:1186-1197`), and the caller
even quotes this comment *as history* ("the code's own comment called that 'a gap worth naming'").
This is the `RG3-D7` failure shape - a comment that was true when written, made false by the fix for
the very finding it describes (`ACC-B2` / MT-257 item 3) - and it sits in the first place the next
reader of the skip will look. Left as written it invites rebuilding reporting that exists.

---

## D - not defects: corrections, the downgrade matrix, and the successor census

### D1 - the `sekunde` delay change is NOT a 3.0.0 regression: it shipped in 2.8.x, and two prior findings say otherwise

The brief for this pass, `RGN-C2`'s 2026-09-04 re-check, and `R28-D10`'s row all carry the same
sentence: *"2.3 was 2000 ms [at 2.8.1] and is now 2300."* Checked against the actual baseline:

    git show master:src/org/traincontrol/marklin/file/CS2File.java:795-828   (2.8.1)
        // Scale first, truncate second.  The other way round threw away
        // the fraction of every pause the operator tuned ...
        delay = Float.valueOf(Float.parseFloat(kv[1].trim()) * 1000).intValue();

**2.8.1 already scales first** - comment and code are character-identical to HEAD's
(`CS2File.java:819-828`), and the JSON route paths (`master:1165-1168` vs HEAD `:1189-1192`) agree.
The change was made in `062f7efa` ("Fixes", 2026-07-29), which is *shared* history - in `master`, in
HEAD, and in every `v3_0_0_rc*` tag - so it shipped with v2.8.0 (8/2/2026). Only `v2_7_4c` has the
old truncate-first line (`v2_7_4c:CS2File.java:737`, `delay = Float.valueOf(kv[1]).intValue() *
1000;`).

So: a 2.8.1 user's route delays are **bit-identical across this upgrade**, `RGN-C2` was already
shipped behaviour when it was filed and its open question to Adam has nothing left to decide for
3.0.0 (at most a v2.8.0 changelog line, which is his call under the non-technical-changelog rule),
and `R28-D10`'s C2 row ("master still truncates then scales") is factually wrong - the one row of
that table this pass found unverified. The mid-pass scare that `master` had sprouted new commits
("3 way delay and sequence" atop `CS2File.java`'s log) resolved the same way: those are July commits
below the merge base, visible because nothing on the 2.8.1 release line ever touched the file again.

### D2 - the baseline is stable and every 2.8.1-only fix is still at HEAD

`git rev-parse master` is `5f0a75e3`, unchanged since `R28`. `R28-D1`'s forward-port table was
spot-checked rather than re-derived (four rows re-read at HEAD: the callback guards, `isNameUsable`
- now narrowed, see D5 - the duplicate-route disable, the id-cache rebuilds); nothing has been
un-ported by the September commits.

### D3 - the downgrade matrix, walked to completion: two documented hazards, no third

Every artefact 3.0.0 writes that 2.8.1 also reads:

| artefact | verdict |
|---|---|
| Java preferences | same node, no key renamed, no format changed; the five 2.8.1 keys nothing reads any more are deletions, not rewrites (`RG3-D5`; the three dead constants were removed by the `ACC` round). New keys are invisible to 2.8.1. |
| `UIState.data` | **hazard 1, documented**: >10 pages - 2.8.1 restores pages 1-10 (`master:TrainControlUI.java:1700`, `j < NUM_LOC_MAPPINGS`) and its exit save writes ten back, destroying 11-50. `Readme.md:381` says keep a copy first, in bold. The <10-pages variant (page *names*, active page and button lost, because `saveStates.size() > 10` fails - `master:1722`) is not separately named, but the same bold sentence covers it: it says *keep a copy of your settings first*, unconditionally. Not re-filed. |
| `LocDB.data` | `MarklinSimpleComponent.java` and `NodeExpression.java` have **empty diffs** against master, re-checked at `2cef4211`; `RouteCommand`'s diff is the javadoc and one relaxed predicate (D5), no serialised field. Round-trips both ways. |
| `.cs2` page files | strictly-less-lossy holds (`RGN-D5`); the caption migration is hazard 2 - one-way, `.bak` kept, changelog line and log lines present (`RGN-B1` closed on exactly that); the page-id trap under a 2.7.4c rewrite is MT-135, old news. |
| `routes.json` / route export | untouched (`R28-D12`), and `AC2-C1` now covers the round-trip fidelity boundary from the other side. |
| `autonomy.json` | read from and written to the working directory by **both** revisions (`master:TrainControlUI.java:1783` / HEAD `:7002`, same constant); the import never deletes or moves it. His `config/autonomy_legacy/` archive is his own filing, not the application's - no code at HEAD writes that path. A downgrading user's legacy file is where 2.8.1 expects it. |
| `config/autonomy/setup.json` + configurations | new files; 2.8.1 has no reader and no writer, so a downgrade ignores them and an un-downgrade finds them intact. |

The autosave-forced-on point (`RGN-C1`) is confirmed still true at HEAD
(`TrainControlUI.java:935-936`) and still filed there - it is the one path by which 3.0.0 rewrites
`autonomy.json` for somebody who had turned that off, and it remains open with `RGN`.

### D4 - the derived-command measurement clears most of `R28-B1`

The headline of that finding was fifteen hand-authored signals lost to the derivation. Measured
(method-note artefacts, his real configuration): of **65** accessories named in his 2.8.1 edge
commands, **64** appear in the derived setup's commands - every switch, and fourteen of the fifteen
signals, most via his own pairings plus the geometry. At the stricter per-state grain the residue
is: one accessory gone (Signal 61), four red aspects unauthored (the rest of `RG4-B1`), and four
switch-states never issued - and those four all sit on the two corridor edges `RG4-B2` is about,
where they are quoted. That supersedes the "fifteen signals" arithmetic. The finding's other half (no UI authors an arbitrary per-edge accessory command) is
unchanged and stays with `R28`; the disclosed drops (edge commands, lengths, locks, timetable, route
activations - now all counted at import, `AutonomySession.java:999-1042`) are Adam-ruled via MT-257.

### D5 - bracketed locomotive names work again: `RGN-C3` is fixed at the root

`RouteCommand.isNameUsable` now refuses only the comma (`RouteCommand.java:617-621`), on Adam's
2026-09-04 ruling quoted in its javadoc ("bracketed loc names should just be allowed"), with the
reason the bracket rule died: conditions no longer round-trip through the text form. A 2.8.1 route
naming `SBB 460 (2)` saves again. The unswept doors (`AddLocomotive`, the sync) are named in the
same javadoc with the argument for leaving them open; nothing to add.

### D6 - `R28-A1`'s second half is really built

The delete confirmation counts and names the cost: `ui.confirmDeleteFromDatabaseWithRoutes` in all
eight bundles, chosen at `TrainControlUI.java:18125` when the driven-routes count is non-zero.
Adam's "put it in the log, popup on count" is satisfied on both halves. Closed as its disposition
says.

### D7 - `RG3-C1`'s Bulk Tools exist and are wired

`AutonomyEditorPanel` builds the submenu (`bulkTools()`, `:1864`) and mounts it on both the ordinary
and the ignored-square menus (`:955`, `:1625`) - read from `git show HEAD:`, not the moving working
tree. Both bulk actions are present with confirmations, as ruled.

### D8 - the parity report's four "missing" routes are Adam's own ruling

`* -> BottomSecondary` reduced/gone rows trace to the red-signal-after-the-end rule he stated
(recorded in `2026-08-30-staging-planner-round.md` and re-confirmed in `ACC`); the journeys survive
by the route through TopMainR1/R2, which is visible in the PATH rows quoted under `RG4-B2`. No
destination is lost (parity section 1). The runtime-parity gap all three predecessor passes named is
therefore closed to the extent enumeration can close it; only the timed run remains impossible in
simulate mode, as the report itself says.

### D9 - the graph window's monitoring role has a full successor, popouts included

The live-state half of `GraphViewer` (train positions lighting up as they move, watchable on a
second monitor) is carried by the overlay publish/registry: `DiagramTileRegistry` keeps a label-set
per square explicitly because "the main window and a popup can show the same page... every copy of a
tile shows it" (`DiagramTileRegistry.java:14-16`), late-drawn tiles catch up from `lastPublished`
(`:90`), and `LayoutPopupUI` builds its grid through the same registration (`LayoutPopupUI.java:57`).
The second-monitor workflow survives via View-page-in-new-window.

### D10 - the deleted-file census is fully accounted for

`git diff master..HEAD --diff-filter=D -- src/` is eleven files. `GraphViewer` + two right-click
menus + `GraphEdgeEdit` + `GraphLocExclude`: every advanced parameter re-homed (`RG3-D4`), keyboard
routes ruled (`R28-C5`), edge editing subsumed by derivation with the losses measured here and in
`R28-B1`. `RouteEditor.form/.java`: one editor remains, the changelog's false "older editor is still
there" sentence was `RG3-B1`, fixed and committed. `TrackDiagramEditor.zip`: withdrawal is complete
and correctly placed at HEAD - `removeLegacyEditorItem` is called once from the window's own set-up
(the javadoc records `RG3-C3`'s wrong placement and its fix), the handler is dead code kept
deliberately with its reasons written down, and the in-app editor is the successor on every
platform.

### D11 - the bundle-key delta at this baseline contains nothing new

Re-run at `2cef4211`: 18 keys present at 2.8.1 are gone from the bundle (the graph-window shell,
matching `R28-D4` minus `confirmClearLocomotives`/`menuClearLocomotives`, which D7's restoration
resurrected into live keys). The "live at 2.8.1, dead at HEAD" set is `RG3-D2`'s 191 minus the
restorations since; spot-checks on the `layout.ui` and `ui.main` members found no unfiled member.
The one previously-open orphan of the 2.8.1 line, `route.ui.errorUnusableLocName` (`R28-C4`), is out
of all eight bundles.

### D12 - form components and menu items

The only component in `master:TrainControlUI.form` absent from HEAD's is `reopenGraphButton`. The
locomotive right-click menus are re-homed key-for-key in `LocomotiveMenuItems` (`RG3-D3`), and the
keyboard shortcut set in `TrainControlUI` is unchanged against master.

### D13 - `MarklinRoute` against 2.8.1: the conflict concept, and nothing else

The full diff resolves to: the rename repair moved to the base class (already `R28-D7`); the
conflict machinery (`RGN-B2`, behaviour Adam-ruled, all three descriptions aligned since
`8a8ce798`); and one genuinely new refusal - a manual route command that would flip **green** a
signal currently protecting an *occupied* platform is skipped while autonomy runs
(`heldReason`, `route.refusedSignalProtectingOccupiedPlatform`, with `execRouteOverridingConflicts`
as the operator's way past and `hasEmergencyStop` exempting stop-carrying routes entirely). That
guard can only fire while a 3.0.0 autonomy run is active - a state 2.8.1 route execution never
coexisted with - so it is not a 2.8.1 capability lost; and it has a way past, per the standing rule
on guards.

### D14 - `TimetablePath` is byte-identical to 2.8.1

`git diff master..HEAD -- src/org/traincontrol/automation/TimetablePath.java` is empty. The
timetable's formats round-trip (`R28-D5`), its tab is reachable again (`RGN-A2` fixed, with the
test), and the executor was read by `FR3`'s delegated pass and `AC2`. The 2.8.1 timetable *content*
not surviving the import is a disclosed, Adam-ruled drop (MT-257) and stays where it is filed.

### D15 - the MFX import correction is a shipped, changelogged fix, not a silent meaning change

`CS2File.java:1786` now subtracts `MFX_BASE` when an address-less MFX record's UID was used as its
address - the same correction the DCC branch always had. A 2.8.1 user's affected locomotive gets a
*different* address on import because the old one pointed at a decoder that cannot exist;
`Readme.md:437` says so under 3.0.0. Read and cleared.

### D16 - the 30 shared-code commits since `cf048f9b` add no 2.8.1-visible regression beyond what is filed

Each commit's touched-file list was read and its 2.8.1-relevant members traced: `504ad2ab` *relaxes*
two 3.0.0-only refusals back toward 2.8.1 behaviour (D5 and `IPR-B3`); `489439fa` removes a
3.0.0-only staging rule (`mustBackIn` never existed at 2.8.1); `7c562279`/`2cef4211` fix 3.0.0-only
defects; the remainder are comment, message and test work validated by the seven same-day rounds.
The one candidate that survived scrutiny long enough to check was the `MarklinRoute` refusal family,
which is D13.

---

## Open questions - the two things only Adam can answer, restated as actions

1. **`RG4-B1`:** pair (or dismiss) five signals: 37 -> LowerDown, 39 -> BottomInner, 61 and 62 ->
   BottomSecondary / BottomInnerOtherside as appropriate, 116 -> the Tunnel/BottomMainBCPre
   corridor. Pair Signal by address reaches all of them.
2. **`RG4-B2`:** may a train run `BottomInner -> Tunnel` while another runs to `BottomSecondary`?
   If no: author the lock with Blocked By Points. If yes: nothing to do, and `RG4-C2`'s harness fix
   keeps the question answered automatically on future re-runs.

## What this pass did not look at

- **The three files Adam is editing** (`LayoutGrid`, `LayoutEditor`, `AutonomyEditorPanel`) beyond
  `git show HEAD:` reads; nothing here cites their working-tree state.
- **No TestNG class was executed** - the harness guard was up for the entire pass (another JVM
  running); the measurements here execute the engines through the parity artefacts instead.
- **Per-journey command parity.** `RG4-B1` compares accessory/state *sets*; whether every journey
  issues its commands in an equivalent order/grouping to 2.8.1 is a stronger property nobody has
  measured. The harness records PATH rows, so it is measurable if ever wanted.
- **`AC2`'s ground** (the low-citation files: `MarklinLocomotive`, `CS2Message`,
  `RemoteDeviceCollection`, the CAN path) - reviewed there the same day, deliberately not re-walked.
