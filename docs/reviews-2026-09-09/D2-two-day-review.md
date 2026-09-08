# Two-day review: the OB-180..MT-247 run

**Prefix for citing these findings elsewhere:** `D2`

Reviewed 2026-09-08, by reading only — no tests were run, no files changed.

## Scope and method

`git log --since="2 days ago"` covers ~120 commits, 2026-09-06 16:42 through 2026-09-08 12:54. The
09-08 run (OB-180..MT-247) was read as diffs against the current source; the 09-06/09-07 evening
commits were read as commit messages and consulted in the source where a 09-08 change built on them.

Read in full, diff plus surrounding current code: MT-247 (`MarklinRoute.execRoute`,
`respondToConflict`), MT-262 and its follow-up (`Layout.isPathClear`, `whyTooLongForTheBerth`,
`measuredRoomAtTheBerth`, `whyNoRouteFitsTo`, both manual-send doors, `HomeStaging` both length
sites), both MT-309 commits (`AutonomySession.routesCoveredByStandingTrains`, `walkBackFrom`,
`pathBetween`, `LayoutLabel.paintCoveredMark`/`coveredRoads`, `TrainControlUI.refreshCoveredTrack`
and the map-diff repaint), MT-334, MT-337 both halves (`putTheTrainsBack` and the caption
suppression), the reversal commits (tests only, plus the reverted narrowing), OB-187, OB-189,
MT-313, and `Layout.edgesCoveredByStandingTrains` as the ground both MT-262 and MT-309 stand on.
New tests were read as claims: `testAnEditedPlacementSurvivesTheRebuild`,
`testAConflictSkipsOnlyTheSwitchUnderTheTrain`, `testAManualSendIsRefusedABerthTooShort`,
`testTheWashIsNoLongerThanTheTrain` assertion structure. `docs/reference/behaviour.md` was read
against every behavioural change. All eight message bundles were checked for the two key renames and
for ASCII-only content (clean; no stale `errorTrainTooLongToReverse` or `SKIP_ACCESSORIES`
references anywhere).

Verified clean and worth saying so: MT-247's per-command grain matches the ruling at all three
doors, and the mid-route human dialog still asks at most once; MT-262 updated behaviour.md §5a and
put both halves of the length rule behind one predicate that all doors and `isPathClear` share;
the planner mirrors the runtime's shared-metal covered-track check (`HomeStaging:1278-1297` against
`Layout:2440-2494`); the tile-level covered data added for MT-309 is consumed by the painter only —
every routing guard still reads `edgesCoveredByStandingTrains`; the wash test's containment
assertion carries a non-vacuity guard.

## A - high

### A1 - putTheTrainsBack now lets a stale setup placement overwrite the running railway

`src/org/traincontrol/gui/TrainControlUI.java:5852` (`putTheTrainsBack`), reached from
`rebuildRunningLayoutFromSetup` at `TrainControlUI.java:6048`; introduced by commit `1728986e`
(MT-337).

The MT-337 fix changed the rule from "put every recorded train back" to "leave any train the
rebuild has already placed, and put back only those the setup has no opinion about". The commit's
justification: *"where the rebuild has an answer for a train, that answer is the newer of the two."*

That is true only where the setup was captured after the last train movement, and one production
path breaks the precondition. The setup's `loc` entries are written only by
`captureRunningLayout()` (editor open, editor close, exit, configuration load — its own javadoc at
`TrainControlUI.java:2956`: *"Stopping autonomy does not capture; nothing else does either"*). But
`rebuildRunningLayoutFromSetup` is also reachable with **no editor session at all**: the track
diagram viewer's right-click autonomy menu is an `AutonomyEditorPanel` (`autonomyTileMenus`,
`TrainControlUI.java:4446`, wired to the main window at `:4476`), and every gesture on it —
setting a home, a priority, a caption, an exclusion — runs `setupChanged()` →
`rebuildRunningLayoutSoon()` → `parentWindow().rebuildRunningLayoutFromSetup(true)`
(`AutonomyEditorPanel.java:7206`). On that path nothing captured first.

Concrete failure:

1. Setup last captured with locomotive T at station A (any editor close, exit, or load).
2. An autonomy or Return Home run moves T from A to B, and ends. The move lives only in the
   running layout (`currentLoc`); the setup still says A.
3. The operator right-clicks any square on the track diagram viewer and changes any autonomy
   setting — a home on some unrelated square is enough.
4. `rebuildRunningLayoutFromSetup`: `whereTheTrainsAre()` correctly records T at B; the rebuild
   places T at A (the stale setup entry); the new `putTheTrainsBack` sees the rebuild "has an
   opinion" and **leaves T at A**, discarding B. The panel's save then writes A to disk.

T is physically at B and modeled at A. Occupancy is derived from placements only —
`Point.isOccupied` is `currentLoc != null` and `isPathClear` never consults the s88 (the DW-A1
comment at `TrainControlUI.java:2969` records exactly this consequence) — so the next dispatch can
route a second train into a block that is physically occupied, and can dispatch T from a square it
is not standing on. This is OB-183/OB-144 re-introduced through the viewer door, and it reproduces
Adam's own OB-183 report ("a locomotive teleporting when a home was changed") on the surface he
uses most. Before MT-337 this path was correct: the old code moved T back to B.

Three aggravations:

- The block comment above the call site (`TrainControlUI.java:6031-6047`) still states Adam's
  OB-183 ruling as the code's behaviour — *"where a train IS is a fact, and where the file thinks
  it is is a record... So the placements are carried across the rebuild"* — which the code beneath
  no longer does unconditionally. The document and the program now differ, and per behaviour.md's
  own opening sentence, one of them is a bug.
- `test/regression/testAnEditedPlacementSurvivesTheRebuild.java`'s "OB-183 control"
  (`testATrainTheRebuildDroppedIsStillPutBack`, line 155) models the setup having **no entry** for
  the moved train. The real OB-183 case leaves a **stale entry** — the rebuild places the train at
  its old square, not nowhere — and that case, now the losing one, is exactly the case the suite
  does not have.
- MT-337's own commit message names the shape: "a rule lifted to a new site without the
  precondition that made it true where it came from, again" — and the fix does it a second time,
  in the same method, in the other direction.

The editor-close path itself looks sound: `openLayoutEditor` captures on the way in, and the
placement doors that could move a train are shut while the editor is open (`isLayoutEditorOpen`
guard in `LayoutRightclickAutonomyMenu:142`, `refuseWhileEditorOpen()` on the run doors). It is
only the capture-free viewer path that hands the rebuild a stale opinion.

## B - medium

### B1 - behaviour.md §5c still describes the wash, and its "drawn == refused" claim is now false

`docs/reference/behaviour.md:365-378`, against commits `6bb0a0fc` and `e2d6e4de` (MT-309), neither
of which touched behaviour.md.

Three statements in §5c now describe a mechanism that no longer exists or a rule that is no longer
true:

- *"Covered squares are **greyed on the diagram** until the train moves"* — the grey wash was
  removed entirely; the mark is an orange line (`LayoutLabel.TRAIN_MARK`), drawn along one road of
  the square.
- *"**The wash survives a highlight.**"* — there is no wash; the line is painted in
  `paintComponent` under the highlight, so the paragraph's whole mechanism (icon swap on flash
  end) is gone. The property it protected does still hold, by construction, but the document
  describes the removed implementation as the rule.
- *"**What is drawn is what is refused.**"* — this is now false in substance, not wording. What is
  drawn is where the train IS: the walk stops when the train's length is spent
  (`AutonomySession.walkBackFrom`), a deliberate strict subset of what the railway refuses (whole
  covered edges, both directions, plus every edge sharing metal via the lock-edge relation,
  `Layout.java:2440-2494`). Concretely, on Adam's own MT-309 example: a length-1 train on a
  17-square run — the railway refuses routing over the entire edge and everything sharing metal
  with it, and the diagram marks one square. An operator asking "why is BottomMainC refused?" by
  looking at the diagram now sees no mark anywhere near the blocked track — the exact
  debugging-by-eye that §5c's greying was added for on 2026-09-06.

The change itself is Adam's ruling (quoted in the commits) and the routing guards are untouched;
this finding is that the single written statement of what the railway does now asserts the old
meaning, and the next reader who reconciles code against document will find them disagreeing on a
safety-adjacent indicator. §5c needs the indicator's new meaning ("where the train is, as long as
the train — a subset of what is refused; nothing shows the full refused set") and the known limit
stated as a limit.

## C - low

### C1 - the MT-247 route-conflict rule is documented nowhere but the commit

Commit `c22c9d90` changed a behavioural rule of the railway: an s88-fired route used to drop its
entire accessory group when any one accessory was held, and now skips only the held command while
everything else in the route runs (`MarklinRoute.java:596-700`). Adam's 2026-09-06 ruling — what
"conflicting" means, Cancel/OK semantics at the human doors, notification-only at the s88 door —
exists in the commit message and in comments, but `docs/reference/behaviour.md` has no section on
route conflict handling at all (its only accessory content is §8's signals-are-switches and
counter notes). This rule has now been written twice in opposite directions inside three days
("refused WHOLE" → per-command), which is behaviour.md's own stated criterion for a rule that
must be recorded with its reason, and the document's scope ("what happens when they get there")
covers it. A §-level entry for routes under conflict is missing.

### C2 - MT-262 widened both known-unsound halves of the room sum without saying so where they are documented

`Layout.measuredRoomAtTheBerth` (Layout.java:7436), commit `716cf3f5`. Removing the
terminus-or-reversing fence is Adam's ruling and behaviour.md §5a records it. But §5b's "two the
room sum is known to get wrong" (behaviour.md:379-397) was written when the sum ran only at
termini and reversing berths, and neither bullet was revisited:

1. The partial-measurement under-count (one measured tile makes a "measured" segment) can now
   refuse **autonomy** journeys at every through platform. On a layout with scattered lengths,
   destinations silently drop out of autonomy's reachable set with the refusal visible only in the
   log — journeys that were legal two days ago. The rule's blast radius grew from "a handful of
   reversal squares" (the FV2-B1 measurement quoted in the old call-site comment) to every
   destination in every tier.
2. The wrong-segments hole ("a 10 + 1 + 2 path admits an eight-unit train into three units of
   room" where the train backs in part way along) now applies at every berth reached by a
   mid-path reversal, not only at termini. §5b still calls this "the one of the pair worth ruling
   on first"; the ruling is still pending while the hole just got wider.

Both paragraphs should say the fence is gone and what that multiplies, so the pending ruling is
priced correctly.

## D - minor

### D1 - walkBackFrom computes each hop's path twice

`AutonomySession.walkBackFrom` calls `pathBetween(at, candidate)` to test reachability inside the
candidate loop and then calls `pathBetween(at, next)` again for the same pair
(`AutonomySession.java:5085-5095`), and `pathBetween` is a linear scan of every reduced edge. Runs
per train per covered-track refresh, and the refresh runs whenever anything about a train changes.
Harmless at this layout's size; keep the first result.

### D2 - a covered square's line is drawn along the road of a stale switch state

`LayoutLabel.coveredRoads` (LayoutLabel.java:1514) resolves the recorded `RouteId` through
`TilePorts.ports(type, orientation, road.getState())` using the switch state captured when the
covered set was computed. Throwing the switch under a covered square (possible with the override,
or by hand at the CS) redraws the tile with the line along the road the train was recorded on,
until the next train movement recomputes the set. Self-correcting and visual only.

### D3 - the MT-337 caption suppression consults captions on every page

`TrainControlUI.autonomyCaptionAt` suppresses a station's self-caption when
`session.captionsFor(caption)` names any other square, and `AutonomyCompanionStore.captionsFor`
(AutonomyCompanionStore.java:5272) scans the whole store — captions on other pages included. A
caption on another page naming this station would blank the station's own page. No current door
appears to create a cross-page caption, so this is a latent interaction, recorded here so the next
door that does is not surprised.

## What this pass could not check

- **Anything requiring execution.** Every "seen red first" and "clean: ..." claim in the commit
  messages was taken on trust; the tests were audited for assertion shape only. In this codebase's
  own recorded experience (review-by-running), reading passes miss most execution-dependent
  defects.
- **Rendering.** The orange line's legibility, the flicker fixes (OB-187, MT-334), the FlatLaf
  title-bar work, and the set-home dialog spacing are visual claims a reading cannot confirm.
- **The triage store.** `docs/manual-tests/triage.db` is binary; the catalogue commits
  (`af642884`..`22f3d302`) were reviewed as messages only. Whether the 2,269 findings and their
  statuses survived the docs/reviews deletion faithfully is unverifiable by reading the diffs.
- **The real railway.** `cs2_sample_layout/` was not opened; MT-262's and MT-309's on-layout
  measurements are the commits' word.
- **A1's frequency in practice.** The mechanism is fully traced in source, but how often Adam's
  workflow interleaves "run ends" with "viewer-menu gesture" before any capturing event decides
  how often it fires; a hands-on reproduction (run → move → right-click set-home → check the
  train's modeled square) would settle it in two minutes.
