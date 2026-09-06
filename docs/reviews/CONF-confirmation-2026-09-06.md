# Confirmation pass: did the fixes for SPEC, REG6 and ACC4 stick, and what did they break?

**Status:** open

**Prefix for citing these findings elsewhere:** `CONF` (confirmed unused - `grep -rn "CONF-" docs/` returned nothing before this file).

**Reviewed:** branch `autonomy-diagram-r0`, working tree at HEAD `af1aada8`, on 2026-09-06. The subject is the eight fix commits `7d4661d0`, `ca0265f4`, `ec4d5ec7`, `6c95ffde`, `5670d7a9`, `ef158b98`, `d9fb7f88`, `af1aada8`, read against the three reports they answer: `docs/reviews/SPEC-validation-2026-09-06.md`, `docs/reviews/REG6-regressions-2026-09-06.md`, `docs/reviews/ACC4-acceptance-2026-09-06.md`. Nothing else in the tree was re-reviewed.

**Method - and its limit.** Reading and tracing only. No build, no test run, no probe; that was the instruction. `cs2_sample_layout/` was read and never written. No file except this one was created or modified. Every finding says whether the claim follows from the source as written (**CONFIRMED**) or needs a run to settle (**UNMEASURED**).

Two of the three reports leaned on `testEverySquareOnThisLayoutBuildsToOneCopy` to bound reachability. **That test no longer exists** - `ef158b98` deleted it (see `CONF-B5`) - so the reachability caveats those reports carried can no longer be checked by running anything, and this pass could not restore them by reading.

---

## Verdict

**Seven of the nine fixes stick. Two of them introduced worse defects than the ones they closed, and both are in the same three lines.**

`5670d7a9` fixed `REG6-A1` by deleting the `at.isReversing()` clause from `asksAbout`, and separately fixed `SPEC-A2`/`REG6-B3` by narrowing the stop from `mayReverseAt(current)` to `current.isReversing()`. Each edit is right about the thing it was aimed at. Together they leave `Layout.shouldReverseAt` free to answer **"do not turn"** at a Point whose only outgoing edges leave by the side the train arrived from:

- **Nothing asks about a compulsory turn any more, and nothing performs one either.** In manual mode a `mustReverse` square is now excluded from `asksAbout` (correct) and then falls through `shouldReverseAt` to `reversals.shouldReverse(...)`, which for a manual policy answers the captured `turn` - `false` by default. Before this range `executePathInternal` turned unconditionally at `current.isReversing()`. **A compulsory turn on a hand-driven path is now silently skipped.** That is `REG6-A1`'s exact hazard - a train left running forward at a turning copy - promoted from "the operator has to press the default button" to "it happens whatever they press". `CONF-A1`.
- **The stop and the rule are no longer the same question, in the other direction.** The stop is `current.isReversing() || asksAbout(current)`; the rule's gate is `mayReverseAt(current) || asksAbout(current)`. On the plain copy of a split square where the door does not ask, the rule can still turn the train and the stop will not have happened - which is `DIR-A1` restored in a narrow window. The comment three lines above still says *"The same question the rule asks ... and nowhere else (DIR-A1)"*. `CONF-A2`.

`SPEC-A2`'s own suggested fix was one predicate used by both. That was not done, and this is the defect it predicted, at the call, twice - `extracted-rule-moves-the-bug-to-the-call`.

**The paste fix (`SPEC-A1`) is architecturally right and behaviourally inert on this railway.** `facingAfterAPaste` returns the landing copy's own side whenever the square has one copy, before it ever looks at the heading that was so carefully read before the move. On a layout where every named square builds to one copy - which is what the deleted test measured - that is byte-for-byte the behaviour `SPEC-A1` condemned. And the replacement test feeds the rule a heading drawn from `held.values()`, which is the same value the one-copy branch returns, so it cannot fail there either: `assert-the-variable-not-the-control`, in the commit that cites that note while fixing the previous instance of it. `CONF-A3`.

**Everything else confirmed fixed:** `ACC4-1` (`reverseFor`), `REG6-B4`, `REG6-B1`/`SPEC-B1`, `REG6-A2`, `REG6-B5` at the door it names, `SPEC-B2`, `SPEC-C3`/`ACC4-3`, `ACC4-4`, and the message bundles (eight files, key parity for the reworded keys, zero non-ASCII bytes).

**Not addressed, and confirmed still real:** `REG6-B2`, `REG6-B3`'s second-order speed note, `SPEC-B4`, `SPEC-B5`, `SPEC-C2`, `SPEC-C4`/`REG6-C2`, `SPEC-C5`, `REG6-C3`, `REG6-C4`, `REG6-C5`, `REG6-C6`. One placement door still records no facing at all and no note was left saying so (`CONF-B2`).

**Outside the review, and worth a look before anything else:** `git status` shows `cs2_sample_layout/config/autonomy/setup.json` and `configuration-Main.json` modified in the working tree - locomotive placements moved, facings dropped and added, `maxTrainLength` changed, and `atomicRoutes` flipped from `true` to `false`. See `CONF-D1`.

---

## Findings

| | Finding | Severity | Disposition | Confidence |
|---|---|---|---|---|
| CONF-A1 | NEW: a compulsory turn is no longer performed on a hand-driven path, and "keep direction" at a turning copy drives a train off its reserved route | A | Open | CONFIRMED in code; UNMEASURED on how often a manual route takes a turning copy |
| CONF-A2 | NEW: the stop and the rule ask different questions again, so a train can be turned at a plain copy without being stopped first | A | Open | CONFIRMED in code; UNMEASURED on reachability, and the test that bounded it was deleted |
| CONF-A3 | `SPEC-A1` is not fixed in effect: the one-copy branch returns before the heading is consulted, and the new test cannot fail there | A | Open | CONFIRMED |
| CONF-B1 | `facingOf` reads the SETUP, which is exactly the staleness `REG6-A2` was fixed for three commits later - the sweep did not reach it | B | Open | CONFIRMED |
| CONF-B2 | `AutonomyEditorPanel.placeLocomotive(TileKey)` still records no facing, leaves the previous occupant's, and got no note | B | Open | CONFIRMED |
| CONF-B3 | The edit-or-assign door records the OUTGOING occupant's heading for the INCOMING train | B | Open | CONFIRMED |
| CONF-B4 | `REG6-B3`'s second half is untouched: the exit `setSpeed(speed)` still discards the per-point multiplier | B | Open | CONFIRMED |
| CONF-B5 | `ef158b98` deleted `testEverySquareOnThisLayoutBuildsToOneCopy`, the control three reviewers used to bound reachability | B | Open | CONFIRMED |
| CONF-B6 | `SPEC-C5` was answered with a test that pins two rules the code does not make equal | B | Open | CONFIRMED in code; the test's own result is UNMEASURED |
| CONF-C1 | The deleted `isReversing()` clause survives as the `ReversalPolicy` interface default, where the next policy inherits it | C | Open | CONFIRMED |
| CONF-C2 | `facingAfterAPaste`'s `landedOn` parameter is never read, and its javadoc says it is | C | Open | CONFIRMED |
| CONF-C3 | `headingBeforeTheMove` is not cleared on the cut/clear branch | C | Open | CONFIRMED; harmless on a one-copy layout |
| CONF-C4 | `ManualReversalPrompt`'s threading and "train is stopped" javadocs are still the ones `ACC4-2` asked to be fixed with the message | C | Open | CONFIRMED |
| CONF-C5 | Confirmed still open, unchanged: `REG6-B2`, `SPEC-B4`, `SPEC-B5`, `SPEC-C2`, `SPEC-C4`, `REG6-C2`, `REG6-C3`, `REG6-C4`, `REG6-C5`, `REG6-C6` | C | Open | CONFIRMED |
| CONF-D1 | Confirmed fixed, and one thing outside the review | D | Open | see each |

---

## A - high

### CONF-A1 - a compulsory turn is no longer performed on a hand-driven path, and "keep direction" at a turning copy drives the train off its route

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED that `shouldReverseAt` can answer false at a Point with `isReversing() == true`; the operational consequence is reasoned from `AutonomyBuilder.Node.leavesBy`, as `REG6-A1` reasoned it, not measured |
| **Where** | `src/org/traincontrol/automation/Layout.java:5194`, `:5196`, `:5834`, `:6154`; `src/org/traincontrol/gui/ManualReversalPrompt.java:211`, `:220`, `:113` |
| **From** | `5670d7a9`, fixing `REG6-A1` |

`REG6-A1` is **CONFIRMED FIXED** as written: `forOperator().asksAbout` no longer consults `at.isReversing()` (`ManualReversalPrompt.java:193-220`), the may-reverse case is still asked about through `session.mayTurnTiles()` (`:220`), `session == null` answers false (`:211`), and `testACompulsoryTurnIsNotAQuestion` carries the control that a `canReverse` square IS still asked about (`test/core/testACompulsoryTurnIsNotAQuestion.java:172-192`) as well as a source check for the deleted clause. Nothing below disputes that edit.

**What the edit did not carry with it is the turn itself.** `shouldReverseAt`:

```java
if (reversals == null || reversals == ALWAYS_REVERSE) return current.isReversing();   // :5161
if (destination != null && destination.isTerminus()) return current.isReversing();     // :5181
if (!mayReverseAt(current) && !reversals.asksAbout(current)) return false;             // :5194
return reversals.shouldReverse(loc, current);                                          // :5196
```

Take a hand-driven send whose path passes an intermediate `mustReverse` square, to a destination that is not a terminus:

- `forJourney` skips it - `asking.asksAbout` excludes `mandatoryTurnTiles()` by design - so `first` is null and the method returns `KEEP_DIRECTION` (`ManualReversalPrompt.java:113`). Nothing is asked, which is correct.
- At runtime the stop fires, because `current.isReversing()` is true (`Layout.java:5834`).
- `shouldReverseAt` reaches `:5194`. `mayReverseAt(current)` is true (the copy itself reverses), so the guard does not return false; `:5196` asks `KEEP_DIRECTION.shouldReverse`, which is **false**.
- The train is not turned. `loc.setSpeed(speed)` at `:5851` drives it on.

`AutonomyBuilder.Node.leavesBy` is `if (reverse) return arrival == exitSide;` - a turning copy's outgoing edges are exactly the ones leaving by the side the train came in at. So the reserved next edge is behind the train, the sensors the run waits on never fire, and the locomotive continues forward past the point. That is the consequence `REG6-A1` described; the fix removed the *question* and left the *outcome*.

The same holds at the arrival (`:6154`): a manual send whose destination is a reversing point is no longer turned on arrival, where before this range it always was.

And it holds for a **may**-reverse square too, whenever the operator answers "keep direction": the path is chosen before the question is asked (both doors compute `path` and then call `forJourney`), so `bfs` may already have routed through the turning copy. "Keep direction" is then not a thing the graph can express - the copy that carries straight on is a different Point, and the train is standing on the wrong one. That half is inherent to the feature rather than new, but `5670d7a9` made the compulsory half unconditional.

**What this used to be.** At `1ffe6c50` the block read `if (current.isReversing() && isCurrentLayout())` and turned with no policy consulted at all. Every `isReversing()` copy turned. The regression is against that.

**The shape of a fix.** Either the rule refuses to answer at a copy that only leaves the way it came - `if (current.isReversing()) return true;` above `:5194`, which restores the pre-range invariant and leaves the may-reverse choice to the *plain* copy where it belongs - or the choice is moved ahead of path selection so that "keep direction" picks a path through the plain copy. The first is one line and is what the javadoc at `:5115-5119` already claims ("A point trains do not reverse at: no ... Anywhere else the caller's policy decides"); it does not currently say what happens at a point trains *must* reverse at, because the code no longer has an answer for it.

**Reachability.** `Layout.java:3583-3585` records that `getPossiblePaths` - the manual right-click list - *"by design ... includes reversing stations"*, and `isPathClear` has no rule against a reversing intermediate. MT-245 exists precisely because a manual path turns at a reversing point on the way. So the path shape is one this railway routinely produces; what is unmeasured is how often a manual route takes a turning copy as an intermediate to a **non**-terminus destination, which is the case `:5181` does not already rescue.

---

### CONF-A2 - the stop and the rule ask different questions again, and the comment still says they agree

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED in code; UNMEASURED on reachability, and `CONF-B5` removed the test that bounded it |
| **Where** | `src/org/traincontrol/automation/Layout.java:5834`-`:5836` against `:5194`; the comment at `:5818`-`:5819` |
| **From** | `5670d7a9`, fixing `SPEC-A2` / `REG6-B3` |

The first half of `SPEC-A2` / `REG6-B3` is **CONFIRMED FIXED**. `:5834` now reads `current.isReversing()` where it read `mayReverseAt(current)`. Autonomy and Return Home pass `ALWAYS_REVERSE`, the second disjunct is switched off for them by construction, so the condition reduces to `current.isReversing()` - exactly what it was before `8fca0215`. **Autonomy and Return Home no longer brake and re-accelerate at plain copies of split may-reverse squares.**

What the edit created is the mirror image. Write the two predicates side by side:

| | stops? (`:5834`) | may turn? (`:5194`) |
|---|---|---|
| turning copy | `isReversing()` - yes | `mayReverseAt` - yes |
| plain copy of a split square, door asks | `asksAbout` - yes | `asksAbout` - yes |
| **plain copy of a split square, door does not ask** | **no** | **`mayReverseAt` - yes** |

In the third row `shouldReverseAt` runs on to `:5196` and asks the policy, which for a `forJourney` policy with `turn == true` answers **yes** - and the train has not been stopped, because the stop's condition was false. `loc.switchDirection()` is then issued to a moving locomotive. That is `DIR-A1`, whose measured symptom was *"speedAtTheMomentTheOperatorIsAsked=30, and still 30 five seconds later"*, restored in one row of a three-row table.

The row needs `asksAbout(current)` to be false at a copy `mayReverseAt` says yes about: `getStationIndex().squareOf(at.getName())` returning null, or a square whose `canReverse` mark has been removed since the layout was last built. Narrow, and it needs a split square, which is the same caveat `SPEC-A2` carried - except that the test which measured that caveat has since been deleted.

The comment above the condition was not updated with it and now asserts the opposite of what the code does, for the second time in this file in two days:

> *"The same question the rule asks, so the train is stopped exactly where somebody may be asked something - and nowhere else (DIR-A1)."* (`:5818-5819`)

It is not the same question. It is *narrower* than the rule for every non-`ALWAYS_REVERSE` caller. `SPEC-A2` proposed extracting one predicate used by both - `stopsToDecideAt`, which is `shouldReverseAt` with its last line removed - and that is still the fix: two spellings of one question is what produced this finding, `REG6-B3` and `SPEC-A2`, all three at this line.

---

### CONF-A3 - the paste fix returns before it reads the heading it went to such trouble to capture

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED on the code path; the claim that it is inert on Adam's railway rests on the one-copy measurement whose test `ef158b98` deleted |
| **Where** | `src/org/traincontrol/automationui/AutonomySession.java:4808`-`:4821`; `src/org/traincontrol/gui/TrainControlUI.java:5988`, `:6070`; `test/core/testAPastedTrainKeepsItsDirection.java:135`-`:148` |
| **From** | `ef158b98`, fixing `SPEC-A1` |

The mechanics `SPEC-A1` asked for are all present and correct:

- `AutonomySession.facingOf(String)` exists (`:4832`), `facingAfterAPaste(held, keep, landedOn)` exists (`:4804`), and `TrainControlUI:5988-5989` reads the heading **before** `moveLocomotive` at `:5991`. **The ordering is CONFIRMED correct on the paste path**, and `testTheDoorReadsTheHeadingBeforeItMovesTheTrain` pins it by source position with a stated mutation.
- The unholdable-heading case clears rather than invents (`:4821`), and `testAnUnholdableHeadingIsClearedNotInvented` exercises it on a hand-built two-copy map - the one genuinely discriminating assertion in the file.

**The rule then short-circuits above all of that:**

```java
if (held == null || held.isEmpty()) return null;                          // :4808
if (held.size() == 1) return held.values().iterator().next();             // :4812
if (keep != null && held.containsValue(keep)) return keep;                // :4815
return null;                                                             // :4821
```

`:4812` returns **the landing square's own copy side, ignoring `keep` entirely**. On a square that builds to one copy that is the same value the fourth attempt wrote - `session.facingsFor(tile).get(point.getName())` - because with one copy `point.getName()` is that copy. **Where every named square builds to one copy, `ef158b98` changes nothing at this door**, and Adam's *"pasting the train from bottommainpost to bottommainb, its direction in the dropdown was wrong"* is unchanged.

The commit argues for it - *"one copy means one answer ... it is the only heading a train can have on that square"* - and at a terminus that is exactly right and is how the "terminuses must reverse on paste" ruling is met. At a **through** station it is a different claim, and it is the one `SPEC-A1` disputed: a through station's built copy count is a property of which arrivals the reducer could model, not of which ways a train can physically stand. `:4812` is the arbitrary-copy behaviour with a better argument in front of it.

**And the new test cannot fail on the case it is named for.** `testTheHeadingSurvivesWhereTheLandingCanHoldIt` (`:135-142`) does:

```java
for (Side heading : held.values())
    assertEquals(AutonomySession.facingAfterAPaste(held, heading, station.getName()), heading, ...);
```

On a one-copy square `held.values()` has one element, and `:4812` returns that same element. The assertion compares the fixture value with itself. The floor at `:147` (`checked.size() >= 2`) counts stations, not meaningful cases, so it is satisfied by two vacuous checks - which is the failure mode `Property tests need a floor on how much they exercised` is about and which the commit message correctly diagnoses in the *previous* version of this same file. `assert-the-variable-not-the-control`, twice in one test file, in the commit that cites it.

**Two things are needed.** Adam's ruling on whether a one-copy through station may hold a heading the build did not model - which is the spec question `:4812` decides silently - and, whatever he says, a test whose input heading does not come out of `held`.

---

## B - medium

### CONF-B1 - `facingOf` reads the setup, which is the staleness `REG6-A2` was fixed for three commits later

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/automationui/AutonomySession.java:4832`-`:4842` against `:1516`-`:1537` |
| **From** | `ef158b98` added it; `af1aada8` fixed the identical defect in `flipFacing` and did not sweep here |

`REG6-A2` is **CONFIRMED FIXED** in `flipFacing`: `af1aada8` asks the RUNNING layout first (`:1518-1530`), then walks the setup in full (`:1532-1537`). `DIR-C3` is intact - the `continue`s at `:1553` and `:1566` still carry on past an undecidable square - and the candidate list cannot loop twice over one square, because both loops guard with `!candidates.contains(...)` (`:1528`, `:1536`).

`facingOf`, added three commits earlier and untouched by that sweep, does what `flipFacing` was just stopped from doing:

```java
for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())   // :4836
    if (locomotive.equals(placed.getValue())) return getFacing(placed.getKey());
```

`placedLocomotives()` is the setup, and `TrainControlUI:4736` states as established fact that between the end of a run and the next `captureFromLayout` the setup names the square a train **departed from**. So a train that has just completed a journey and is then pasted somewhere has its heading read off the platform it left, not the one it is standing on - which is `REG6-A2`'s own sentence with `flipFacing` swapped for `facingOf`.

`flipFacing` is four hundred lines above it in the same class and shows the pattern to copy. Both paste doors (`TrainControlUI:5988`, `AutonomyEditorPanel:3930`) feed this value straight into `facingAfterAPaste` as `keep`.

Latent while `CONF-A3` holds - `keep` is not read on a one-copy square - which is the only reason this is B.

### CONF-B2 - the third placement door still records no facing, keeps the previous occupant's, and got no note

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/gui/AutonomyEditorPanel.java:4048`-`:4099`, the write at `:4096`; `src/org/traincontrol/automationui/AutonomySession.java:4559`-`:4570` |
| **From** | `REG6-B5`, half-swept by `af1aada8` |

**Every caller of `session.placeLocomotive(` in `src/`, which is the command `REG6-B5` asked for:**

| call site | records a facing? |
|---|---|
| `TrainControlUI.java:6035` (diagram drag/paste) | yes - `:6070`, `af1aada8`'s sibling, fixed by `7d4661d0`/`ef158b98` |
| `AutonomyEditorPanel.java:3935` (edit-or-assign) | yes - `:3947`, **CONFIRMED FIXED** by `af1aada8`; the heading is read at `:3928-3930`, **before** `edit.commitChanges()` at `:3932`. See `CONF-B3` for which heading it reads |
| `LayoutRightclickAutonomyMenu.java:975` (place at a chosen copy) | yes - `:980`, and it moves the running layout too |
| `AutonomyEditorPanel.java:4096` (the picker, `placeLocomotive(TileKey)`) | **no** |
| `AutonomyEditorPanel.java:1133` and `LayoutRightclickAutonomyMenu.java:638` | `(tile, null)` - removals, and `placeLocomotive` clears `FACING` on that branch (`:4567`). Correct |

So the answer to "are there any other placement doors still not recording a facing" is **yes, exactly one**: `AutonomyEditorPanel.placeLocomotive(TileKey)`.

`REG6-B5` excused this door - *"No Point in hand, so the facing genuinely is not known here"* - and asked for a facing prompt or a documented note. **Neither was added, and the excuse has since expired**: `facingOf` looks a heading up by NAME, and this method has the name in hand at `:4086` before the write at `:4096` takes the train off wherever it was. The three-line fix from `TrainControlUI:6070` transplants directly.

It is also worse than "records nothing". `placeLocomotive(tile, name)` writes only the `loc` property (`:4590`); the `FACING` property already on that square is left standing. Placing a train through the picker onto a square another train was recently on gives the new train **the old train's recorded heading** - which is the hazard the null branch's own comment names four lines up: *"Left behind, the next locomotive placed here inherits the last one's direction without being asked."*

`testEditorSurfaceRules` cannot catch this. It counts `setFacing(` writers, and a door that writes no facing at all is invisible to a rule that counts facing writes - `guard-knows-only-what-it-lists`, and the guard was raised to two while the door it should have found stayed at zero.

### CONF-B3 - the edit-or-assign door reads the outgoing occupant's heading and records it for the incoming train

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/gui/AutonomyEditorPanel.java:3928`-`:3950` |
| **From** | `af1aada8` |

The ordering is right and the value is not:

```java
Side heading = point.getCurrentLocomotive() == null ? null
    : session.facingOf(point.getCurrentLocomotive().getName());   // :3928-3930, BEFORE the commit

edit.commitChanges();                                             // :3932

session.placeLocomotive(target, point.getCurrentLocomotive()...); // :3935, the NEW occupant
session.setFacing(target, facingAfterAPaste(..., heading, ...));  // :3947, the OLD occupant's heading
```

`GraphLocAssign` is *"edit **or assign** locomotive"*, and `menuLabelFor` reads *"Place Locomotive At..."* on an empty square. Two cases the read gets wrong:

- **Empty square, a train assigned.** `heading` is null, so on a multi-copy square `facingAfterAPaste` returns null and **clears** the facing - defensible as "the menu will ask", but it is not the incoming train's known heading, which `session.facingOf(theChosenName)` would give.
- **Occupied square, a different train assigned.** `heading` is the heading of the train being displaced, and it is written as the heading of the train arriving. On a multi-copy square that is a wrong direction recorded as though somebody chose it - which is the whole class `SPEC-A1` is about.

The heading that should be read is the one belonging to whichever locomotive the dialog will commit, which is knowable from `edit` before `commitChanges()`. Latent on a one-copy layout for the same reason as `CONF-A3`.

### CONF-B4 - `REG6-B3`'s second half was not addressed: the exit still discards the per-point speed multiplier

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/automation/Layout.java:5851` against `:5753`-`:5763` |
| **From** | pre-existing; unchanged by `5670d7a9` |

Reported here because it was asked for explicitly and because the answer is "still wrong, and slightly less reachable than it was".

`:5753` computes `calculatedSpeed = ceil(speed * current.getSpeedMultiplier())` and applies it for the approach to `current`. `:5851`, on the way out of the reversal block, restores **`speed`** and then blocks on `waitForSpeedAtOrAbove(speed)` - so a square given a 0.3 multiplier to slow a train down is left at full line speed the instant the block ends, and the loop's next iteration re-derives a multiplier for the *next* point rather than for this one.

`git show 1ffe6c50` confirms `setSpeed(speed)` predates this range and applied only inside `if (current.isReversing())`. `5670d7a9` restored that condition as the first disjunct, so the blast radius is back to roughly where it started - plus manual may-turn squares on a journey answered "reverse". The correct restore is `calculatedSpeed`, which is in scope five lines up but inside its own `if (isCurrentLayout())` block; hoisting it is the whole change.

Not a regression of this range. Real, unfixed, and now the only part of `REG6-B3` still standing.

### CONF-B5 - the control that bounded three reviewers' reachability caveats was deleted

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `test/core/testAPastedTrainKeepsItsDirection.java` - the method is gone; it was at `:184` in `7d4661d0` |
| **From** | `ef158b98` |

`git log -S` gives two commits: `7d4661d0` added `testEverySquareOnThisLayoutBuildsToOneCopy`, `ef158b98` removed it. `grep -rn "BuildsToOneCopy" test/` now returns nothing.

That test is cited as the reachability bound for `SPEC-A2`, `SPEC-B5` and `REG6-B3`, and `ACC4`'s watch item quotes it as the reason the paste fix's multi-copy control is untestable. `SPEC`'s closing section names re-running it as the single measurement that decides two severities. It was the only thing in the tree measuring the claim, and the claim is load-bearing for `CONF-A2`, `CONF-A3`, `CONF-B1` and `CONF-B3` as well.

Removing it as part of rewriting the file it lived in is understandable; nothing replaced it. Restoring it - anywhere - costs one method and re-arms four findings' severity assessments.

### CONF-B6 - `SPEC-C5` was answered with a test that pins two rules the code does not make equal

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED on the code; the test's actual result is UNMEASURED (no tests were run) |
| **Where** | `src/org/traincontrol/automationui/AutonomySession.java:3350`-`:3362`; `test/core/testTheAutoTierScopeMatchesTheRuntime.java:102` |
| **From** | `6c95ffde` |

`SPEC-C5` was that the Auto tier note decides "autonomy will never choose this" from a **second, partial** copy of `barredFromAutonomy`'s list. `6c95ffde` added a test and changed no code.

`stationsAutonomyWillNotChoose()` is still `store.isStation(tile) && !isAutoDestination(tile)` (`:3358`). Its javadoc now quotes the runtime's rule in full - *"`Layout:3576` - `!end.isReversing() && end.isAutoDestination()`"* - which the body does not implement: the `isReversing()` half is absent. So the tier note is still silent about a route ending at a reversing point, which is the case `SPEC-C5` said was the most relevant thing the Auto tier could say.

The new test asserts the two agree:

```java
boolean railwayRefuses = point.isReversing() || !point.isAutoDestination();   // :102
boolean diagramRefuses = excludedByTheDiagram.contains(square);
```

Those two expressions are not the same function. They can only agree on a layout where no `isDestination()` Point is both reversing and an auto destination. So either the test is red today - in which case `6c95ffde` did not land - or it is green because the sample layout happens not to contain the case, in which case it is a guard that will fire the day Adam marks one, and `SPEC-C5` remains open in the code exactly as filed. **This is the one finding in this report whose answer needs a test run rather than a read.**

`testTheSetActuallyDividesTheStations` (`:128-151`) is a good non-degeneracy floor and does its job; the objection is to the rule being pinned rather than fixed.

---

## C - low

### CONF-C1 - the clause `REG6-A1` deleted is still the interface default, where the next policy inherits it

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/automation/Layout.java:5106`-`:5109` |

```java
default boolean asksAbout(Point at)
{
    return at != null && at.isReversing();
}
```

That is character for character the expression `5670d7a9` removed from `forOperator` as dangerous, left as what every `ReversalPolicy` gets for free. It is unreachable today - `ALWAYS_REVERSE` is the only policy that does not override it, and `:5835` excludes `ALWAYS_REVERSE` from the disjunct that would call it - so this is a trap for the next caller rather than a fault, which is what `README.md` says such a thing is worth fixing as. The javadoc above it still recommends the default as *"the old behaviour"*.

`SPEC-C3`'s reasoning for deleting `reversalPolicy()` applies word for word: the next person building a policy will find this and inherit the defect at the new door only.

### CONF-C2 - `facingAfterAPaste`'s third parameter is never read

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/automationui/AutonomySession.java:4801`, `:4804`-`:4822` |

`landedOn` appears in the signature and in the javadoc - *"the copy the running layout chose, **used only when nothing better is known**"* - and nowhere in the body. When nothing better is known (`:4821`) the method returns null rather than `landedOn`, which is the correct behaviour and the opposite of what the parameter is documented to do. Both call sites compute and pass it (`TrainControlUI:6071`, `AutonomyEditorPanel:3949`).

`extraction-params-must-not-disagree`: an argument that cannot affect the result, documented as deciding one, is read by the next author as a fallback that exists.

### CONF-C3 - `headingBeforeTheMove` is not cleared on the cut/clear branch

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED that the field survives; the exploit needs a `moveLocomotive` that does not clear |
| **Where** | `src/org/traincontrol/gui/TrainControlUI.java:5995`-`:6001`, `:6022`, `:6063` |

Asked for explicitly, so: the field is written on the paste branch only (`:5988`). The `else` branch - cut and plain clear - calls `moveLocomotive(null, point.getName(), true)` and then falls into the same `rememberPlacement(point, aimed)` at `:6008` with a value left over from the last paste.

On the ordinary path this is harmless: the clear empties the square, `point.getCurrentLocomotive()` is null at `:6063`, and `setFacing` is not reached. It becomes reachable when the clear does **not** clear - `Layout.moveLocomotive` returns false without doing anything `if (this.isRunning())` (`:6261-6267`), and neither the cut branch nor `rememberPlacement` checks the return. In that case the square still holds a train and the stale heading of whatever was pasted last is fed to `facingAfterAPaste` as `keep`. Latent on a one-copy square, where `keep` is ignored.

`headingBeforeTheMove = null;` in the `else` branch closes it. Worth noting separately that the paste door ignores `moveLocomotive`'s return value entirely and writes the setup either way, which is a wider question than this finding.

### CONF-C4 - the javadoc `ACC4-2` asked to be fixed alongside the message was not

| | |
|---|---|
| **Disposition** | Open |
| **Confidence** | CONFIRMED |
| **Where** | `src/org/traincontrol/gui/ManualReversalPrompt.java:255`-`:265`, `:273`; `:24` |

`ACC4-2` / `SPEC-C1` is **CONFIRMED FIXED** for the message: `messages.properties:306` now reads *"{0} will pass {1} on its way, where trains may turn round."*, and all eight bundles carry the rewording with correct `\uXXXX` escapes and zero non-ASCII bytes (checked byte-wise across all eight).

`ACC4-2` named two javadoc sentences to go with it. Both are still there:

- `:255-257` - *"Both callers dispatch on a worker - `executePath` blocks until the train arrives, so it cannot run on the event thread"*. Since `df584d0b` both callers ask on the EDT before the worker starts; `:328`'s `isEventDispatchThread()` branch is the rule now, `invokeAndWait` the fallback.
- `:260-265` - *"**The train is stopped before this is called**"*, in bold, with the `DIR-A1` measurement behind it. False on the `forJourney` path, where the train has not been dispatched. The class javadoc's *"`Layout.executePathInternal` does not consult the policy at a terminus at all"* (`:24`) is also imprecise: the exemption is `shouldReverseAt:5181` and it is about the journey's DESTINATION, not the point being passed.
- `:273` - `@param where the point it has reached`.

A comment asserting the comfortable version of what the code does is how `DIR-A1` survived being written and reviewed - which this file's own `:262-264` says.

### CONF-C5 - confirmed still open, unchanged

Each re-read at HEAD and confirmed present exactly as filed. No new argument; recorded so the next reader does not have to check again.

- **`REG6-B2`** - `forJourney` still breaks at the FIRST asked-about square (`ManualReversalPrompt.java:103-107`) and applies the answer everywhere (`:120-123`). The dialog names `first`; a may-reverse DESTINATION inherits the answer without ever being named. Real, and the sharpest remaining gap against `DIR-A2`'s requirement - *"if the user decides to send a train to a 'may reverse' point, explicitly ask the user"*. The destination is `path.get(path.size()-1).getEnd()` and is in hand at `:85`; preferring it over the first match is a two-line change. **B in substance; listed here only because the letter it was filed under is not mine to change.**
- **`SPEC-B4`** - `AutonomyEditorPanel.buildFacingMenu:2922` is still `() -> session.setFacing(target, facing)` and nothing else; the other two `setFacing` writers move the running layout. `testEditorSurfaceRules:263+` still asks whether each writer REDRAWS, not whether it MOVES. Real. Its effect is bounded by the same one-copy question: where the square builds to one copy `moveOntoFacingCopy` would find nothing to do anyway.
- **`SPEC-B5`** - `TrainControlUI:5975-5983` still asks `getNeighbors(point)` of the single `speakerAt`-chosen copy, and the comment at `:5971-5974` still claims that is the same question the menu asks. `ef158b98` made this worse in a small way: the door now explicitly distrusts the landing copy for the facing while still trusting it for the way-out guard, twenty lines apart.
- **`SPEC-C2`** - `messages.properties:422` still reads *"Inactive points will not be traversed in autonomous operation."* under a radio labelled "No - Nothing Can Pass". Eight bundles.
- **`SPEC-C4` / `REG6-C2`** - `LayoutRightclickAutonomyMenu:1032` still calls `forJourney` before the power check at `:1038`; `AutoLocomotiveStatus:1010` still checks power at `:1010` before asking at `:1044`. Two doors, one gesture, opposite orders.
- **`REG6-C3`** - `Layout.java:2341` still logs `autolayout.errorInactiveStationInAutoRun` for hand-driven sends, and `messages.properties:173` still says *"cannot be chosen in autonomous operation"*. Eight bundles, and `autolayout.errorInactiveIntermediatePoint` is the correctly worded sibling to copy.
- **`REG6-C4`** - `TrainControlUI.java:17159` and `:17164`, two adjacent identical `if (answer == RouteConflict.REFUSED)` blocks.
- **`REG6-C5`** - `AutonomySession:1445-1448`, the redundant clear loop in `moveOntoFacingCopy` that drives the protecting signal green and back through `setLocomotive(null)`.
- **`REG6-C6`** - `AutonomySession:1574`, `flipFacing` still `return tile`s on the first decidable square. The `af1aada8` javadoc at `:1512-1515` argues for walking the setup "in full", which it does - it just stops acting after one.
- **`ACC4-5`** - `IS_PRE_RELEASE` was not in scope for these commits and was not checked; it is a release-checklist row, not a defect.

---

## D - checked and clean, or outside the review

### CONF-D1

**Confirmed fixed, with the check that establishes it:**

- **`ACC4-1`** - `ManualReversalPrompt.reverseFor(int)` is `return chose == NO;` with `NO = 1` (`:231`, `:249`). Only index 1 reverses; `CLOSED_OPTION` (-1), index 0 (Yes) and any other value keep the direction. The call site passes the dialog's return **unchanged** - `answer[0] = reverseFor(chose);` at `:325`, with `chose` straight out of `showOptionDialog` at `:295` and nothing between them. `ask()`'s `catch (Exception cannotAsk) { return false; }` at `:331-336` still means "keep", and `answer[0]`'s initialiser is `false` (`:280`). `test/ui/testDismissingTheReversalPromptKeepsTheDirection.java` exercises 0, 1, `CLOSED_OPTION` and a sweep. All four inputs correct.
- **`REG6-B4`** - `ManualReversalPrompt:138` is `return turn && asking.asksAbout(at);`. A journey answered "keep direction" no longer stops at may-turn squares. A **turning copy still stops**, through the first disjunct of `Layout:5834` (`current.isReversing()`), which is the property the comment at `:136-137` claims and which holds.
- **`SPEC-B1` / `REG6-B1`** - the terminus short-circuit is **restored** at `Layout.java:5181` (`if (destination != null && destination.isTerminus()) return current.isReversing();`), so MT-245 / `testATrainThatCannotReverseMayBackIntoATerminus` is intact: a train backing into a terminus still turns at the reversing point before it, whatever any policy says. `forJourney` now returns `KEEP_DIRECTION` for such a journey before asking anything (`ManualReversalPrompt:83-91`), using the same expression the rule uses (`path.get(path.size()-1).getEnd()`), so **no prompt is shown whose answer would be discarded**. The two terminus tests now agree by construction. This is the cleanest of the nine fixes.
- **`REG6-A1`** - fixed as filed; see `CONF-A1` for what the fix did not carry with it.
- **`REG6-A2`** - fixed; see `CONF-B1` for the sibling it did not sweep.
- **`REG6-B5`** - fixed at the door it named; see `CONF-B2` for the door it excused and `CONF-B3` for which heading the fixed door reads.
- **`SPEC-B2`** - `messages.properties:1948` now reads *"Manual covers a train you send by hand, and Return Home, which picks where to go the same way"*, in all eight bundles. **Correct on the axis the radio implements**: the radio's only consequence is the tier note at `AutonomyEditorPanel:6050`, keyed on `stationsAutonomyWillNotChoose()` = `isStation && !isAutoDestination`, and `grep -n isAutoDestination src/org/traincontrol/automation/HomeStaging.java` returns nothing - Return Home never consults the flag. No place in code or docs was found still claiming the opposite. Two caveats stay in the tooltip's last sentence: *"Both are stopped by the same inactive squares"* is not true of a Return Home **start** (`SPEC-B3`, deliberately left to Adam and correctly so), and *"and turning points"* is the `SPEC-C5` claim, which `CONF-B6` says is still not what the code does.
- **`SPEC-C3` / `ACC4-3`** - `reversalPolicy()` is gone; `grep -rn "reversalPolicy" src/` returns nothing.
- **`ACC4-4`** - `grep -n splash Readme.md` returns nothing.
- **Message bundles** - `autolayout.ui.confirmManualReversal` and `autosetup.ui.tooltipPathType` present exactly once in each of the eight files; zero lines containing a byte outside printable ASCII plus tab, in any of the eight. `traincontrol-properties-ascii-only` satisfied.

**Outside the review, and the reason it is here rather than in a finding: `cs2_sample_layout/` has been modified in the working tree.**

`git status` shows `cs2_sample_layout/config/autonomy/setup.json` and `cs2_sample_layout/config/autonomy/configuration-Main.json` as modified, uncommitted. The diff moves locomotive placements between squares (`EN57-203` removed from `1 - Main:20,14`, `2-8-4 3505 SP` added at `20,13`), drops and adds `facing` values, changes `maxTrainLength` on two squares, and flips `atomicRoutes` from `true` to `false`. That last one is a global that changes how every path unlocks.

**Nothing in this pass wrote to that folder.** It was read and never written, and this report is the only file created or changed. The shape of the diff - placements shuffled, facings dropped, a global flipped - reads like a harness that opened the real setup rather than a sandbox, which is `traincontrol-harness-blast-radius` and `traincontrol-tests-open-the-real-layout` exactly. Worth establishing which run did it before the next one, and `git checkout -- cs2_sample_layout/` restores both files if the changes are not Adam's own.
