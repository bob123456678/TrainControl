# Validating the eight-day review's fixes

**Prefix for citing these findings elsewhere:** `E8V`

**Status:** closed

**Worked 2026-09-10, and two of the eight went differently from the way they were put.**

`E8V-B3` offered two answers - drop the claim, or write the test it promises. The test was written: a
four-point ring with two stations on one sensor, a train on the sibling, and the destination held back
by the empty watched square. It did not isolate the rule. Both routes to that destination run through
either the watched square, whose sensor `canEnter` sees as held, or the square holding the train - so
with the sensor half of `sameTrackAs` neutered the class still came back 93 tests, 0 failures. A test
that is red for a reason other than the one it names is what this finding was about, so it was deleted
and the first answer taken: `canRest` now says the divergence is declared, deliberate and **pinned by
nothing**, and records the mutation that shows it. Isolating it needs a fixture where the watched
square is on no route to the destination, which is a bigger railway than that file builds by hand.

`E8V-C1` said the invariant was settled rather than outstanding, and that the assertion `E8-B4` added
covers what it protected. That is right, and it was reached twice: an attempt to give the invariant a
home through `theRunInto` failed on the fixture - the run into BottomMainPost answers zero squares - so
the check is asked of the room walk instead, which is the thing that decides, and `theRunInto` is
deleted as the finding asked.

Reviewed 2026-09-10, on branch `autonomy-diagram-r0` at `4dc1b9f6` ("The eight-day review, worked").
Scope: the seventeen A/B/C findings of `docs/reviews-2026-09-10/E8-eight-day-review.md` that commit
claims to have fixed, the eight D entries it claims were never defects, the measurements in its commit
message and closing note, and whatever else in the same eight-day window turned up while checking them.

Confirmed absent from `docs/reviews/`, `docs/reviews-2026-09-09/`, `docs/reviews-2026-09-10/`,
`src/`, `test/` and `docs/manual-tests/findings.tsv` before it was chosen: `E8V` collides with
nothing. Prefixes already live are `D2`, `D3`, `E8`, `IND9X`, `REV9`, `W7`, `W7B`.

**Read the second paragraph of Method before the findings.** The working tree began changing at 11:47,
while this was being written, and the uncommitted changes now in it cite six of the findings below. Everything measured here is against `4dc1b9f6` as committed and none of those changes
is validated by this document — but one of them cost the battery run a clean result, which is E8V-D4.

---

## Verdict on the seventeen fixed findings

**14 HELD, 3 PARTIAL, 0 NOT FIXED.**

| id | what it asked for | verdict |
|---|---|---|
| E8-A1 | `behaviour.md` §6 must stop saying Return Home ignores the occupancy restrictions | **HELD** |
| E8-A2 | §3 must stop filing OB-195 as open, and §7's magenta sentence must lose the compulsory turn | **HELD** |
| E8-B1 | `stationsAutonomyWillNotChoose` must ask `isActive`, and its three readers must come right | **PARTIAL** — all three asks were carried out and the set is pinned by a test that goes red without it. Two of the three readers now give the right verdict with the wrong reason: they tell the operator a square he has taken out of service is one he may drive to by hand (E8V-B1). Three comments still state the old one-clause rule (E8V-B2) |
| E8-B2 | the cap bullet must stop contrasting itself with `isFullAutonomyRunning` | **HELD** |
| E8-B3 | §5c must describe a fade rather than a wash | **HELD** |
| E8-B4 | the two-segment test must ask a question only the room rule can answer | **HELD** |
| E8-B5 | two `HomeStaging` comments must stop reasoning from a rule that came back | **HELD** |
| E8-C1 | `LayoutLabel.BLOCKED_WASH` must go | **HELD** |
| E8-C2 | "the three gestures" must become every setup edit | **HELD** |
| E8-C3 | `WireRecorder` must say that the wire drops a repeated line | **HELD** |
| E8-C4 | six names into `REACHES_THE_MONITOR`, five doors written up | **HELD** |
| E8-C5 | two UI test names must stop calling the fade a wash | **HELD** |
| E8-C6 | the "whole square" floor must be a fraction of the tile | **HELD** |
| E8-C7 | one case that pins the CONTENT of `offersALength` off the fixture | **HELD** |
| E8-C8 | one sentence in `canRest` saying which way the audit's guarantee runs | **PARTIAL** — the sentence is there and true, and it ends by naming a test that does not exercise the divergence at all (E8V-B3) |
| E8-C9 | two unreachable private helpers, one holding a fixture invariant | **PARTIAL** — both deleted, and deleting them left a third helper unreachable: the same defect, one call deeper (E8V-C1) |
| E8-C10 | Control+E must appear in `behaviour.md` | **HELD** |

The eight D entries were re-checked where reading or execution could reach them, and none turned out
to be a defect after all. `E8-D8` is re-stated in two parts with what was measured this time (E8V-D2,
E8V-D3); `E8-D2`'s argument was re-traced through `Layout.getPossiblePaths:4871`, which does call
`isPathClear` on every candidate; and `E8-D5` was checked in the code rather than by inference —
`LayoutLabel.paintComponent:1360-1371` sets its composite on a `g.create()` and disposes it in a
`finally`, so the fade cannot reach the border, the children, or the next component whether or not the
label is opaque. `E8-D4`'s census pin is covered by the battery run below.

---

## Method

**What was executed.** Eleven test classes were run against the unmodified working tree with
`docs/tools/one.sh`, in two JVM batches, and all came back green at `4dc1b9f6` (`Failures: 0`,
`Skips: 0`): `core.testAShutStationIsOneAutonomyWillNotChoose` (3 tests),
`core.testTheAutoTierScopeMatchesTheRuntime` (2), `core.testTheLengthGuardsOnTheRealLayout` (8),
`core.testACompulsoryTurnIsChosenLikeAnyOtherStation` (4),
`core.testManualOnlyPathsAreADifferentColour` (3),
`regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor` (5),
`regression.testControlEAsksTheMenusQuestion` (4),
`regression.testWhatReachesTheTracksAtEachKindOfArrival` (6),
`regression.testEveryTestIsInTheBattery` (5), `ui.testBlockedTrackIsGreyWhileAutonomyRuns` (6) and
`ui.testTheGreyAppearsAtIdleToo` (7).

Two more — `core.testHomeStaging` (92) and `core.testATrainMustFitEverySquareOnItsRoute` (6) — were run
only UNDER a mutation, and are said so below rather than counted as clean measurements here. The
battery covers both, and was run once at the end; its result is under E8V-D4.

**Five mutations were performed on the shared tree and restored, one at a time.** After each, the
affected classes were run and the file was put back from a copy taken immediately before the edit;
`git status --porcelain` was checked after every restore and `src/` and `test/` were clean each time.
The only files that ever show modified are the three under `cs2_sample_layout/` that Adam's running
TrainControl rewrites, and nothing here read or wrote that folder.

| # | mutation | what it proved |
|---|---|---|
| 1 | `AutonomySession.java:3618`, `(shut \|\| !isAutoDestination(tile))` → `(false \|\| …)` | `core.testAShutStationIsOneAutonomyWillNotChoose` goes 1 of 3 red at `testAShutStationIsReported`; `core.testTheAutoTierScopeMatchesTheRuntime` stays green (E8V-D1) |
| 2 | `Layout.java:7584`, the reviewer's own `if (room == null \|\| true) continue;` | `core.testTheLengthGuardsOnTheRealLayout` goes **5 of 8** red where the E8 pass measured four, and the fifth is `testTwoOneUnitSegmentsRefuseTheFourUnitTrain` failing at its new `assertNotNull(why, …)`. Control: `core.testATrainMustFitEverySquareOnItsRoute` 3 of 6 red |
| 3 | `HomeStaging.java:1641`, `if (track.getS88() != null)` → `if (false && …)` — the sensor half of `sameTrackAs`, which is the declared divergence from the runtime | `core.testHomeStaging` stays **92 / 0 / 0** (E8V-B3) |
| 4 | `AutonomyEditorPanel.java:4902`, the text-label clause out of `offersALength` | `regression.testControlEAsksTheMenusQuestion` goes 3 of 4 red, the new `testTheKeyRefusesTheTwoSquaresTheMenuOffersNoLengthFor` among them — and so does the whole-page comparison E8-C7 called self-comparing, because `buildTileMenu` keeps its own early return. So that finding's premise holds only for a change that moves BOTH sides, which is worth knowing when reading it |
| 5 | the six names of `REACHES_THE_MONITOR` back to `{ "triageReturnToHome" }` | `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor` goes 1 of 5 red at `testEveryAllowanceIsStillADoor`, naming the four new keys |

**One temporary probe class was written, run and deleted.** `test/core/E8VProbe.java` — printing, no
assertions — opened `test/layouts/single-switch`, marked `MainPlatform` as an auto destination, shut it
the way the right-click menu does, and asked the runtime what it would then offer. Deleted after the
run rather than left, because a class in `test/` with no `build.xml` entry reddens
`regression.testEveryTestIsInTheBattery`. Its output is quoted under E8V-B1. **No file under `src/` or
`test/` was changed by this review**, and this document is the only thing it adds;
`git status --porcelain` outside `cs2_sample_layout/` was empty at 11:36, after the last mutation was
restored and the probe deleted.

**THE TREE MOVED WHILE THIS WAS BEING WRITTEN, and it has to be said because it cost a measurement.**
Beginning at 11:47 - during the battery run below, and still arriving when this was written - another
hand edited `AutonomySession`,
`AutonomyEditorPanel`, `AutonomyChecks`, `GraphReducer`, `testHomeStaging`,
`testTheLengthGuardsOnTheRealLayout`, all eight message bundles, `behaviour.md`, `findings.tsv` and the
seven documents of `docs/reviews-2026-09-09/`. Those changes are uncommitted, they cite `E8V-B1`,
`E8V-B2`, `E8V-B3`, `E8V-C1`, `E8V-C2` and `E8V-C4`, and they appear to be the fixes for them - a
`manualOnlyStations()` that subtracts `shutTiles()`, a new `autosetup.ui.testSquareIsOutOfService`
string in every bundle, and status lines on the seven documents.

**None of that is reviewed here, and no finding below is dispositioned on it.** Everything above the
line was measured against `4dc1b9f6` as committed, with a compile taken at 11:38 - before those edits
existed - so the executed results stand. What does not stand is the part of the battery that reads the
tree rather than the bytecode: see E8V-D4.

**What was only read.** The whole of `4dc1b9f6`'s diff; `AutonomySession.stationsAutonomyWillNotChoose`
and its four callers; `AutonomyBuilder`'s extras loop, for whether `active` reaches every copy of a
split square; `Layout.isSendableDestination`, `isOfferableToOperator`, `isPathClear` and
`whyTooLongForThisRoute`; `AutonomyChecks.checkReversingGoesSomewhere`; `GraphReducer.findPath`'s closed-set
semantics; `HomeStaging.canRest`, `plannedOccupancy` and `sameTrackAs`; the five dispatch call
sites named in E8-C4, each read in full for the thread it runs on; `CS2Message.toString`, for E8-C3's
claim; the eight message bundles' entry for the Auto-tier note; and `behaviour.md` §1, §3, §5c, §6, §7
and the new keyboard-doors section against the code each describes.

---

## A — high

Nothing. No fix in `4dc1b9f6` was found to have changed what the railway does, and the one behavioural
change it makes — the extra clause at `AutonomySession.java:3618` — is correct as a statement of
`Layout.isSendableDestination`. What it is not is correctly *read*, which is B below.

---

## B — medium

| id | what | disposition |
|---|---|---|
| E8V-B1 | the Auto-tier note and the magenta leg now say "manual-only" about a square nothing may reach at all | fixed |
| E8V-B2 | three comments still say the set asks "the one clause a SQUARE can answer" — the sentence that made E8-B1 invisible, left in three more places | fixed |
| E8V-B3 | `canRest`'s new javadoc names a test that does not exercise the divergence; measured, nothing does | fixed |

### E8V-B1 — a station switched out of service is now drawn and described as one you may drive to by hand, and you may not

`AutonomySession.java:3618` is right: a shut station belongs in `stationsAutonomyWillNotChoose`,
because `Layout.isSendableDestination` (`Layout.java:7906-7910`) requires `isActive()`. Two other
places in the tree had already spelled both clauses and were the editor's own evidence for it — the
running diagram's badge (`AutonomySession.java:6638`) and the setup panel's green banner
(`AutonomyEditorPanel.java:7950-7952`, `isAutoDestination && !shut`, which is what makes the banner's
"autonomy will send a train to N of M" agree with this set for the first time). What has changed is
what that set *means to its readers*, and neither reader was changed with it.

**The Auto-tier note.** `AutonomyEditorPanel.java:7037-7043` appends
`autosetup.ui.testNotAnAutoDestination` for any square in the set. In eight bundles that message is
(`src/org/traincontrol/resources/messages.properties:1958`):

> The track is passable, but autonomy will never choose {0} - it is a station autonomy is told to leave
> alone. **Switch Path Type to Manual to check it as a hand-driven run.**

Every clause of that is wrong for a shut square. It is not "a station autonomy is told to leave alone" —
it is a square the operator has taken out of service. The track is not passable to it: `isPathClear`
refuses a closed final point **at every tier** since Adam's ruling of 2026-09-06 —
`Layout.java:2390-2405`, *"In manual mode, inactive endpoints and intermediates should be refused as
well. Just not inactive start points. Inactive really means nothing can pass."* And the remedy it
offers cannot work: `Layout.isOfferableToOperator` (`:4406-4411`) is `if (end == null ||
!end.isActive()) return false;`, so the right-click menu that would make the hand-driven run
(`LayoutRightclickAutonomyMenu.java:255`) never offers the square at all. The operator is sent to a
door that is shut, in the panel they opened to find out why a train is not moving — which is the same
sentence E8-B1 was raised on.

**The magenta leg.** `AutonomyEditorPanel.java:7102-7103` colours a whole leg from the same set, and
the colour was asked for in these words (`behaviour.md:1040`): *"just use a different color going to
manual-only points."* A shut square is not a manual-only point. `behaviour.md:1037` now records the
conflation rather than the rule — "a parking berth, **or a station switched out of service**" — four
lines above the quotation that says what the colour is for.

**How I know.** Executed. `test/core/E8VProbe.java` (written, run, deleted — see Method) shut
`MainPlatform` on `single-switch` with `setPointProperty(berth, "active", Boolean.FALSE)`, the way
`AutonomyEditorPanel.java:3454` does, and asked:

```
E8V willNotChoose contains the shut berth = true
E8V runtime isActive                      = false
E8V runtime isSendableDestination         = false
E8V isOfferableToOperator(null loc)       = false
E8V isParking (the manual-only switch)    = false
```

The last two lines are the finding: the square is in the set that drives the note and the colour, it is
**not** a manual-only berth, and the hand-driven door refuses it. Read, for the third surface: the
route is still drawn, because `GraphReducer.findPath` treats a closed square as a valid destination and
says so (`GraphReducer.java:504-505`) — so the panel reports the leg reachable both ways and then
explains it with the wrong sentence.

**What I would change.** Ask the two readers `session.shutTiles()` first and give each its own
sentence — "this square is out of service; nothing may be sent here, by autonomy or by hand" — before
falling through to the manual-only one. The distinction already exists one method away:
`AutonomySession.java:6620-6643` builds the running diagram's badge with `shut` as its own flag
(`:6643`) *alongside* `shut || !isAutoDestination(tile)` (`:6638`), precisely so the drawing can say
which of the two it is. The alternative Adam may prefer is to leave `stationsAutonomyWillNotChoose` as the runtime's rule
and give the two readers the shut term themselves, which is the shape E8-B1's own "What I would
change" offered as its second option.

### E8V-B2 — the sentence that made E8-B1 invisible is still standing in three more places

E8-B1's own diagnosis was that `stationsAutonomyWillNotChoose`'s javadoc said **"Nothing else decides
it"**, and that the sentence is what hid the missing clause. The javadoc was corrected. Three comments
that say the same thing about the same set were not:

- `AutonomyEditorPanel.java:7025-7031`, above the Auto-tier note: "`stationsAutonomyWillNotChoose` is
  the runtime's rule asked of the diagram, **in the one clause a SQUARE can answer** -
  `isAutoDestination` … (The runtime's other clause, `!isReversing()`, is about a copy rather than a
  square and cannot be asked here.)" There are two clauses now, and `isActive` is the other one.
- `AutonomyEditorPanel.java:7093-7101`, above the leg colour: "**it asks the one clause a SQUARE can
  answer**: `isAutoDestination`, the switch the menu calls Can Be Chosen in Full Autonomy."
- `AutonomyChecks.java:464-465`, above the reversing check: "the runtime does not:
  `Layout.isSendableDestination` requires `!isReversing() && isAutoDestination()` before a square is a
  candidate at all." It requires `isActive()` too, and the parameter that carries the set into this
  method is still called `notAutoDestinations` (`:453`, `:487`).

**How I know.** Read, after `grep -rn "stationsAutonomyWillNotChoose" src test docs` returned exactly
four call sites and this is what stands over three of them. `behaviour.md` §3 (`:189-196`) and §7
(`:1037`) were both brought up to date by the same commit, so the document says two clauses and the
code beside the readers says one.

**What I would change.** Rewrite all three to state the two clauses, and rename the `AutonomyChecks`
parameter to something that survives the set widening again. This is B rather than C on the E8
document's own grading rule — a reader who trusts `AutonomyEditorPanel.java:7026` deletes the `shut`
term at `AutonomySession.java:3618` as an editor invention, and that is E8-B1 restored. It is also the
repository's most repeated mistake in its own words: *"When you fix a call site, grep for its twins
before closing the finding."*

### E8V-B3 — the new sentence in `canRest` ends by naming a test that does not test it

E8-C8 asked for one sentence saying which way `auditAgainstRuntime`'s guarantee runs. The sentence is
there and it is true (`HomeStaging.java:1545-1552`). Its last clause is not:

> That divergence is declared where it lives, fails safe - a refused plan, never a wrong movement - and
> **is pinned in both directions by `testHomeStaging.testTwoActivePointsSharingASensorAreNeverBothOccupied`**.

That test (`test/core/testHomeStaging.java:3340`) asserts `plan.getOutcome() == IMPOSSIBLE` for two
homes on one detection section, and its own comment says why: *"conflicting homes are proved, not
searched for"* — it is answered by the pairwise goal scan, before any arrival is planned, so it never
reaches `canRest` or `plannedOccupancy`. One test cannot pin a divergence "in both directions" in any
case; E8-C8 named two, and the second — `testASharedSensorDoesNotMakeAnOrdinaryLayoutImpossible` — is
about a scan that was removed on 2026-09-09 and asserts only that no verdict of IMPOSSIBLE is built
out of the wider relation.

**How I know.** Mutation, run. `HomeStaging.java:1641` — `if (track.getS88() != null)`, the sensor half
of `sameTrackAs` and the whole of the declared divergence — was replaced by `if (false && …)`.
`core.testHomeStaging` came back **`Total tests run: 92, Failures: 0, Skips: 0`**. Nothing in that
class holds the divergence in either direction. The line was restored and the tree verified clean.

**What I would change.** Either drop the claim to what is true — the divergence is declared and
deliberately unpinned, and here is the mutation that shows it — or write the test the sentence
promises: a fixture where a station and its approach guard share a feedback address, the guard holding
a train, and the assertion that the planner refuses an arrival `Point.heldBackBy` allows. The second
is better, because the sentence's real subject is a rule nothing would notice being deleted. This is B
for the same reason E8-B5 was: a reader who takes it at face value believes the divergence is
protected, deletes it, and sees the suite green.

---

## C — low

| id | what | disposition |
|---|---|---|
| E8V-C1 | E8-C9's fix left `theRunInto` unreachable — the same defect one call deeper | fixed |
| E8V-C2 | `GraphReducer.java:508` states an `isAutoRunning` fence that was removed on 2026-09-06 | fixed |
| E8V-C3 | `findings.tsv` records all seventeen E8 findings as `open`, in the commit that fixed them | fixed |
| E8V-C4 | the seven review documents of 2026-09-09 carry no status line, so the README's default makes them all open | fixed |
| E8V-C5 | the battery fingerprints the live railway around a run and does not fingerprint `src/` or `test/`, which forty-four of its classes read | fixed |

### E8V-C1 — deleting the two dead helpers left a third one dead

E8-C9 found `roomOf` and `berthsOfferedTo` unreachable in
`test/core/testTheLengthGuardsOnTheRealLayout.java` (`:940` and `:1279` as they stood at `8ae3a304`).
Both were deleted, and `roomOf` was the only caller of `theRunInto` — which is now itself a private
method nothing calls, at `test/core/testTheLengthGuardsOnTheRealLayout.java:942`. The finding's own
subject, a helper declared and never reached, was reproduced by the fix for it.

The invariant half of E8-C9 is settled rather than outstanding, and differently from how the finding
put it. `assertTrue(runIn.size() >= 2, …)` was about `roomOf` spreading two measurements across the run
into TunnelLongPark; with `roomOf` gone nothing does that, so the invariant has no subject. And the
assertion E8-B4 added in the same commit covers what it was really protecting — `assertNotNull(why, …)`
goes red if the two numbers land on track the room rule never counts, which is exactly the staleness
the file records at `:755-758`, where two named tiles turned out to be on a run the guard never
consulted.

**How I know.** `grep -n "theRunInto\|roomOf\|berthsOfferedTo" test/core/testTheLengthGuardsOnTheRealLayout.java`
returns one line, the declaration at `:942`; the method is private, so there is no caller outside the
file either. The class is green (8 tests, 0 failures, 0 skips) with it there, and nothing in the
battery reads it.

**What I would change.** Delete `theRunInto`. It is three lines of a review pass rather than a defect,
and it is here because it is the one thing in this commit that recreates the finding it closes — worth
one line in the same sweep that removes it: *when you delete a dead method, grep for what only it
called.*

### E8V-C2 — the comment E8-B1 leaned on describes a fence removed four days earlier

`GraphReducer.java:508-509`, in `findPath`'s javadoc:

> `isPathClear` refuses a closed FINAL point too, **but only `if (this.isAutoRunning())`** - and while
> autonomy runs it additionally refuses any edge with a closed endpoint. So for the checks, which ask
> what autonomy can do, treating a closed square as a valid destination overstates what is reachable.

The fence went on 2026-09-06. `Layout.java:2390-2405` says so at length, in Adam's words, and the code
under it is an unconditional `if (!path.get(path.size() - 1).getEnd().isActive())`. So the paragraph's
conclusion — that the overstatement is only about the autonomy tier and is therefore "in the safe
direction" — rests on a distinction the railway no longer draws: a route the tool draws to a closed
square is refused at every door, not just autonomy's. This is the paragraph E8-B1 quoted as its
defence analysis and did not check.

**How I know.** Read, against `Layout.java:2390-2405` and the `isPathClear` body; and
`grep -n "isAutoRunning" src/org/traincontrol/automationui/GraphReducer.java` returns only this
comment.

**What I would change.** Say that the refusal is now unfenced, and that the overstatement therefore
affects the manual tier too — which is half of E8V-B1's evidence and belongs where the walk is.

### E8V-C3 — the catalogue says every E8 finding is open, and the same commit closed them

`4dc1b9f6` added twenty-five rows to `docs/manual-tests/findings.tsv`. The seventeen A/B/C rows all
read `E8-xx  E8-eight-day-review.md  -  open`, with the document-status column empty, while the
document the same commit wrote says `**Status:** closed` and every one of those findings `fixed`.

**How I know.** `git show 4dc1b9f6 -- docs/manual-tests/findings.tsv`, against the status tables in
`docs/reviews-2026-09-10/E8-eight-day-review.md:83-84, 149-153, 339-348`.

**What I would change.** Regenerate the catalogue from `triage.db` with the dispositions the document
carries — the file's own header says how. This is C and not B because the staleness is systemic rather
than new: `CD3-*` rows have said `Closed / open` since 2026-09-02, and `D3` already reported the same
thing about the `D2`, `W7` and `IND9X` rows. It is worth one entry here because the README's "One
status, one location" rule is the reason the catalogue exists, and a second location that always
disagrees is the failure that rule names.

### E8V-C4 — seven review documents in the window have no status line

`docs/reviews-2026-09-09/` holds `AUT-`, `D2-`, `D3-`, `IND-`, `REV-`, `W7-` and `W7B-`, and none of
them has a `**Status:**` line in any form. `docs/reviews/README.md` is explicit that **a document with
no status line is open**, so all seven are open by that default, and nothing distinguishes the ones
that are from the ones that are not. `E8-eight-day-review.md` is the only document in either dated
folder that carries the line.

**How I know.** `grep -in "status" docs/reviews-2026-09-09/*.md` returns only prose matches — window
titles, `git status`, `AutoLocomotiveStatus`.

**What I would change.** Add the line to each, with the value its findings actually justify. Nothing
about this is urgent; it is here because it is the one rule in that README with a stated default, and
seven documents from the same week are relying on it.

### E8V-C5 — the runner guards the railway's folder and not the source it is measuring

`docs/tools/battery.sh` fingerprints `cs2_sample_layout/` before and after a whole run, and says so at
length: *"that folder is Adam's real railway and is not recoverable"*, and a class that wrote there
did it silently until the guard existed. Nothing does the same for `src/` and `test/`, and
**forty-four classes in the battery read those trees from disk at run time** rather than through the
bytecode the run compiled — the citation roll, the battery-membership census, the event-thread door
census, the editor surface rules, the shortcut census, the javadoc checks and the rest.

So a battery is a measurement of one commit only while nobody edits the tree, and when somebody does,
what comes out is a failure in a source-scanning class with a message about its own subject. That is
what happened here at 11:47 (see Method), and working out that the failure was not about `4dc1b9f6`
took reading the diff of a tree that had moved rather than reading the runner's output.

**How I know.** `grep -rl 'new File("src\|new File("test\|Paths.get("src\|Paths.get("test\|new File("docs\|File("build.xml"' test/ --include=*.java`
returns 44 files; and `regression.testEveryCitationResolves` failed in this run for exactly that
reason, quoting five ids that did not exist in the tree when the run started.

**What I would change.** Hash `src/` and `test/` at the start and the end of `battery.sh` the way
`cs2_sample_layout/` is hashed, and print one line when they differ: *"the tree changed during this
run - N classes read it directly, so these results are not about one commit."* It is the same guard
already written twice in that file, pointed at the other directory.

---

## D — not defects

| id | what | disposition |
|---|---|---|
| E8V-D1 | the guard's corrected paraphrase is inert on its own fixture, and the deterministic class is why that is acceptable | clean |
| E8V-D2 | the five allowlisted dispatch doors are each genuinely off the event thread | clean |
| E8V-D3 | the ten members left out of `REACHES_THE_MONITOR` have no user-interface caller | clean |
| E8V-D4 | the commit message's measurements | clean |
| E8V-D5 | every new or rewritten assertion in the commit, and what turns each one red | clean |

### E8V-D1 — the corrected guard cannot catch this divergence on the layout it reads, and that is why the new class exists

`core.testTheAutoTierScopeMatchesTheRuntime:131` gained `point.isActive() &&`. Measured: with the
editor's `shut` term mutated out (Method, mutation 1) that class stays **2 / 0 / 0** green, and only
`core.testAShutStationIsOneAutonomyWillNotChoose` goes red. The reason is in the fixture:
`test/layouts/live-snapshot/config/autonomy/configuration-Main.json` has exactly one square with
`"active": false`, `2 - Bottom:8,7`, and it carries `"autoDestination": false` as well — so the clause
that was already there catches it and the new one can never be the deciding term.

That is the identical shape as OB-195's blind spot, which the E8 document quotes: *"on Adam's own
railway every compulsory turn is also marked manual-only, which is why this went unnoticed."* It is
recorded as clean rather than as a finding because the commit made exactly the right call about it —
"whether the operator's own railway happens to have a shut station is not something a guard should
depend on" — and the deterministic class on `single-switch` is what carries the claim. Worth knowing if
somebody ever proposes deleting that class on the grounds that the guard covers it: it does not.

### E8V-D2 — every one of the five doors E8-C4 wrote up is on a worker

Each was read in full rather than taken from the finding:

- `AutoLocomotiveStatus.java:1085` in `locAvailPathsMouseClicked` — the whole handler is inside
  `SwingUtilities.invokeLater`, the path and the reversal answer are captured on the event thread, and
  `executePath` is inside `new Thread(() -> …)`.
- `LayoutRightclickAutonomyMenu.java:1209` in `destinationItem` — `new Thread(() -> …)` around the
  `executePath`; the prompt is answered before it starts.
- `TrainControlUI.java:21850` in `executeTimetableActionPerformed` — inside `new Thread`, with the
  button state marshalled back through `invokeLater`.
- `TrainControlUI.java:23283` in `requestReturnToHome` — inside the `new Thread` this method starts,
  which is what its pre-existing allowance already said.
- `TrainControlUI.java:23992` in `startAutonomyActionPerformed` — `new Thread(() -> runLocomotives())`.

So the four new allowances and the one existing one are accurate; nothing was written down as checked
that was not. The additions are also load-bearing rather than decorative: Method mutation 5 shows
`testEveryAllowanceIsStillADoor` goes red the moment the six names leave `REACHES_THE_MONITOR`.

### E8V-D3 — nothing in `gui/` or `automationui/` calls the reachers that were left out

E8-C4 named sixteen members the guard could not see — twelve that reach the monitor through a call
and four that take it with a `synchronized (this)` block — and asked for six of them. The other ten —
`executePathInternal`, `executeTimetableInternal`, `runLocomotive`, `isPathClear`, `pickPath`,
`firstClearOrWhyNot`, `debugPath`, `hasAutonomousDestination`, `checkForSlowerLoc` and
`handleMisconfiguredPath` — have no call site under either surface package, so leaving them unlisted
costs nothing today. It is worth saying that this was checked rather than assumed: the guard is an
inventory, and an inventory that quietly omits a door reads exactly like one that has none.

**How I know.** `grep -rn "\.\s*<name>\s*("` for each of the ten over
`src/org/traincontrol/gui` and `src/org/traincontrol/automationui`: no hits.

### E8V-D4 — the measurements in the commit message and the closing note

- *"five of that class's eight tests go red where four did"* — **true**, reproduced. Under the
  reviewer's own mutation of `Layout.java:7584`, `core.testTheLengthGuardsOnTheRealLayout` returns
  `Total tests run: 8, Failures: 5`, and the fifth is `testTwoOneUnitSegmentsRefuseTheFourUnitTrain`
  failing at `assertNotNull(why, …)` — the new assertion, for the right reason, and not at the floor
  or at the pre-existing refusal. Control unchanged: `core.testATrainMustFitEverySquareOnItsRoute` 3
  of 6 red.
- *"205 classes green, 0 failures, 0 that tested nothing"* — **not reproduced, and neither failure
  contradicts it.** `docs/tools/battery.sh` was run once, compiling `4dc1b9f6` at 11:38, and came back
  **206 classes, 1700 tests, 2 failures, 0 skips, 0 that tested nothing** (`core.testAutoDetect`
  skipped by the runner, as always - it needs a Central Station). 206 rather than 205 is this commit's
  own new class. The two failures:

  - `regression.testEveryCitationResolves.testEveryCitationLeadsSomewhere` — *"there are now 50
    citations in the code that are findings in no document, up from 45"*, and the five new ones are
    `E8V-B1`, `E8V-B2`, `E8V-B3`, `E8V-C1` and `E8V-C2`. **This one is mine, at one remove**: the class
    reads `src/` and `test/` from disk at run time rather than from the bytecode, it ran after 11:47,
    and by then the working-tree edits described in Method had put those ids into six files while the
    catalogue had no rows for them. Nothing about `4dc1b9f6` is implicated, and the failure will clear
    itself when whoever wrote those citations regenerates `findings.tsv` and lowers `DEAD_CITATIONS`.
    It is worth recording as a property of the harness: forty-four classes in this battery
    read files out of `src/`, `test/`, `docs/` or `build.xml` at run time, so **a battery is a
    measurement of a commit only while nobody is typing.** The runner already fingerprints
    `cs2_sample_layout/` around a whole run for the same reason; `src/` and `test/` have no such
    guard, and a two-line hash of the tree taken at the start and the end would have said this in the
    output instead of leaving it to be worked out from a failure message.
  - `core.testTimetableCaptureThroughARealRun.testARealRunCapturesNothingWithCaptureOff` — *"no
    locomotive moved in 480 seconds, so nothing was declined and nothing is proved."* This is the
    test's own precondition, and its own javadoc names this exact failure and what causes it: *"On a
    loaded machine - a full battery, the application open, several JVMs at once - autonomy does not
    always dispatch anything in that window."* All three were true here: the battery, Adam's running
    TrainControl (it was rewriting `cs2_sample_layout/` throughout), and a second session compiling
    the tree at 11:47. It was NOT re-run in isolation, because by the time it was read the tree was no
    longer the commit under review and a re-run would have measured somebody else's edits. So: not
    reproduced, cause consistent with load, and **open as a question rather than closed as a flake** -
    if it recurs on a quiet machine it is a real one, and the ceiling that was raised for it is
    `STARTUP_CEILING_MS`.
- *"All five were correct, on workers"* — **true**, each read individually (E8V-D2).
- *"the badge code a few thousand lines below spells this exact test"* — **true**:
  `AutonomySession.java:6638` is `shut || !isAutoDestination(tile)`, the same expression the fix put at
  `:3618`. Worth noting that the badge keeps `shut` as a *second, separate* flag (`:6643`), which is
  the distinction E8V-B1 asks the other two readers for.
- *"It does not weaken the claims that matter"* (`test/support/WireRecorder.java`'s new paragraph) —
  **true as far as it goes, and it is one-directional.** The five absolute assertions in
  `regression.testWhatReachesTheTracksAtEachKindOfArrival` are three `== 0` (`:177`, `:244`, `:269`)
  and two `== 1` (`:202`, `:220`), all in the arrival window. The new text answers the
  two-read-as-one direction and not the one-read-as-none direction: a command emitted at the arrival
  is dropped if the immediately preceding logged line is identical, and that would turn a real command
  into a passing `== 0`. Checked, and it cannot happen here — `CB_PRE_ARRIVAL` fires *after* the
  pre-arrival speed reduction, which puts a different message on the wire, so `lastMessage` is never a
  direction command at that moment. Worth one clause in the same paragraph, because it is a property
  of where the mark sits rather than of the dedupe.
- *"Every finding fixed"* — three of the seventeen are short of what the finding asked; see the verdict
  table.

### E8V-D5 — what would turn each new assertion red

The brief's question, answered one test at a time. Four were measured by mutation (Method); the rest
are stated with the change that would redden them.

| test | turns red when |
|---|---|
| `testAShutStationIsOneAutonomyWillNotChoose.testAShutStationIsReported` | the `isActive` term leaves `AutonomySession.java:3618` — **measured**, mutation 1 |
| `…testAStationInServiceIsNotReported` | the set widens to every station. A control, and the right one: every other claim in the class is that the square IS in the set |
| `…testTheRuntimeStopsChoosingAShutStation` | `isActive()` leaves `Layout.isSendableDestination`, or `setPointProperty(…, "active", FALSE)` stops reaching the built Point. It asserts `!station.isActive()` before the claim that rests on it, so a fixture that failed to shut anything says so rather than passing |
| `testTheLengthGuardsOnTheRealLayout.testTwoOneUnitSegmentsRefuseTheFourUnitTrain` | the track-room rule stops binding — **measured**, mutation 2, failing at `assertNotNull(why, …)`. Its floor (`offeredDestinations(train).isEmpty()`) turns it red on a railway that offers this train nothing, which is the failure mode E8-B4 named |
| `testControlEAsksTheMenusQuestion.testTheKeyRefusesTheTwoSquaresTheMenuOffersNoLengthFor` | `offersALength` stops refusing a text label or an excluded page — **measured**, mutation 4. It opens with `assertTrue(panel.offersALength(track))`, so a predicate that answers false for everything cannot satisfy it |
| `testNothingOnTheEventThreadTakesTheRailwaysMonitor` (six new names) | the names leave `REACHES_THE_MONITOR` — **measured**, mutation 5, red at `testEveryAllowanceIsStillADoor`. Note which test caught it: the freshness guard, not the door census, because the four new allowances make the census silent about them by design |
| `testTheAutoTierScopeMatchesTheRuntime` (the new `isActive` clause) | nothing, on the fixture it reads — see E8V-D1. It is right to have and it is not what holds the rule |
| `ui.test*.testThe*MarkIsTheWholeSquareAndNotAStrokeAcrossIt` | the mark becomes a stroke rather than a fade, as before; the strengthened floor (`drawn > TILE * TILE / 10`, TILE = 30, so 90 of 900 pixels) additionally reddens if the fixture square goes near-blank, which `drawn > 0` did not |

Two of the eight are controls rather than claims, and both are the kind this suite's history says are
needed: the in-service case, because a set containing every station would satisfy every other assertion
in its class, and the `offersALength(track)` line, because a predicate that refuses everything would
satisfy both refusals under it.

---

## What I did not cover

- The eight D entries were re-checked only where execution or a short read could reach them. E8-D4's
  three-JVM census claim, E8-D5's stale-pixel argument and E8-D7's scratch leftovers were taken as
  written; D7's evidence no longer exists, because this pass wrote over the same scratch directory.
- The eight message bundles were read only for the one key E8V-B1 rests on. If that note is given a
  shut-specific variant, seven translations go with it.
- `docs/manual-tests/` — the twenty-five rows the commit added were checked for disposition and not
  for content, and the triage database itself was not opened.
- The E8 document's own scope — the 320 commits of the eight-day window — was not re-reviewed. This
  pass is about its seventeen fixes and what they touch.
- **The uncommitted working-tree changes of 11:47-11:49 were not reviewed at all.** They were read only
  far enough to say what they are, in Method, and to explain the citation failure in E8V-D4. Six
  findings below have a fix sitting in the tree that nothing here has run, and the two that would
  measure them - a test that a shut station is absent from whatever set drives the manual-only wording,
  and one that `plannedOccupancy`'s sensor term goes red when removed - are the ones to ask for before
  those are called fixed.
