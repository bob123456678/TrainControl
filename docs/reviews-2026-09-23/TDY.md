# Today's work (2026-09-23) on autonomy-diagram-r0

**Status:** open

**Prefix:** `TDY`

**Reviewed:** branch `autonomy-diagram-r0` at `281c79de`, 2026-09-23.  Baseline: `68852cfc` (the last commit before today; the range is `68852cfc..281c79de`, 64 commits, 87 files).

**Method:** read every src hunk of the 23 commits that touch `src/` (`git show <hash> -- src`), then the enforcing method and its callers for each changed rule: `Layout.walkOneTail` / `whyABerthCannotHoldIt` / `measuredRouteIn`, `AutonomySession.walkBackFrom` / `stationsWithAHalfMeasuredApproach` / `routesBlockedByStandingTrains` / `walkFrom` / `foldRouteTileLengths` / `writeHome` / `captureFromLayout`, `HomeStaging.atHome` and its siblings, `AutonomyBuilder.homeCopy` / `facingOf(Node)` / copy emission, `TrainControlUI` paste and route doors, `MarklinControlStation` route lock, `TailCrossedPrompt`, `Edge` / `Point` JSON round trips, `isPathClear` for the OB-269 lock change.  Read the MUTATION lines of the new and changed tests against the code they name, the MT-480..486 entries and the behaviour.md sections they cite, and the two frozen railways' `setup.json` / `configuration-Main.json` (python, read-only) to see which squares a finding reaches.  Grepped `findings.tsv` for the two B subjects (OP2-D1 is cited in B2).  **Nothing was run**; every finding below that depends on behaviour carries a verification request.

## A - high

None found.

## B - medium

### TDY-B1 - Return Home counts the turning twin as home, and the turning twin faces the other way: at a may-reverse home a train can still come home turned round

| | |
|---|---|
| **Disposition** | Fixed - ac5fe2f2 (claim 8873b0a4, red first): a home held to a facing is home only on a copy with that facing (`Point.copyFacing`, `HomeStaging.atHome`); baseline re-blessed eab6e4ae |
| **Where** | `HomeStaging.java:2504-2515` (`atHome`); `AutonomyBuilder.java` `homeCopy` / `COPY_ARRIVAL` emission (~680-715, ~1136); `behaviour.md` section 6 (the OB-282 bullet) |

OB-282's rule is "Return Home brings a train back facing the way its home was set".  `atHome` implements it as "the same ARRIVAL side":

```java
return !home.isHomeFacingFixed() || java.util.Objects.equals(home.getCopyArrival(), where.getCopyArrival());
```

and the build writes `copyArrival` as the arrival side, shared by a plain copy and its turning twin (`AutonomyBuilder`: "A turning copy and its plain twin share it: one arrival").  But a turning copy does not face the way its plain twin faces.  `AutonomyBuilder.facingOf(Node)`:

```java
// Turned round: pointing back at the side it came in by, whatever the track does
if (node.reverse) return node.arrival;
```

while the plain copy faces `arrival.opposite()` (or the route's other side).  behaviour.md section 3 says "which copy a train is on IS its direction", and the paste doors rely on it (`TrainControlUI.copyFacing`: on BottomMainB "the only copy a train can stand on facing west is the turning copy").  So on a may-reverse square the arrival-W pair is {plain, faces E} and {turning, faces W}.  A home set facing E (held on the plain arrival-W copy by `homeCopy`'s first loop) is satisfied by a train standing on the arrival-W turning copy, which faces W - the train turned round, which is the defect OB-282 was filed for ("Return Home brought it back on the EASTBOUND one and called it home").

**Reachable on Adam's railway.** Both frozen railways (`test/layouts/live-snapshot`, `test/operator_layout`) have BottomMainB (`1 - Main:20,13`) as `canReverse: true`, no barred arrival, and `home: EN57-947`.  Its turning copies are destinations (`AutonomyBuilder`: `stops = point.isStation() && arrivalAllowed(node)`, true for both copies of an allowed arrival; the MT-394 comment in `copyFacing` measured one).  Two ways it shows:

1. **Already standing on the twin: counted home and left turned round.**  `atHome` is what `triage()` (`misplaced(start) == 0` -> `ALREADY_HOME`), the pre-scan (`HomeStaging.java:487`) and the greedy pass (`:810`) ask of the train's current copy.  A train that autonomy or a hand send turned at its may-reverse home stands on the twin - facing the other way - and Return Home reports it home and does nothing.  Deterministic.
2. **A* can end a move on the twin.**  The greedy pass routes to the exact home Point (`firstClearRoute(..., home)`), so the ordinary case lands on the right copy; but when the search is needed, `astar` iterates every Point in `stations` and its goal (`misplaced(next) == 0`) is met equally by a leg ending on the plain copy or its twin, whose leg ends with the train turned (`ALWAYS_REVERSE`).  Which it takes is a tie between two states of equal cost and score.

The converse also holds: with the home held on the plain arrival-E copy (facing W), a train already standing on the arrival-W turning copy - facing W, the homed facing - is NOT home, and is moved away and back.

**Why the test cannot see it.** `core.testATrainComesHomeFacingTheWayItWasHomed` uses BottomMainA, which is not a may-reverse square (its setup has no `canReverse`), so it has no turning twins; and its comparison helper `arrival()` folds `", reverse)"` onto the plain name - the test encodes the same "one arrival" equivalence, so it would pass on a run that ended on the twin.  The behaviour.md bullet states the twin rule ("counts a train home only on that copy or its turning twin - the same arrival") in the same paragraph as "brings the train back in it", which section 3 makes contradictory.  Adam decides which is meant; as written, doc and code agree with each other and disagree with the headline.

**Verification request (execution, deterministic):** on `test/operator_layout`, as in `testATrainComesHomeFacingTheWayItWasHomed.setUpClass` (build, parse, clear standing trains and homes, one reversible probe train of length 0).  Stand the train on `BottomMainB (eastbound, reverse)` (the arrival-W turning copy; confirm by `session.facingsFor(BottomMainB)` that it faces W), then `layout.setHomeLocomotive("BottomMainB (eastbound)", probe)` (confirm that copy faces E and `isHomeFacingFixed()`), then `layout.planReturnToHome().getOutcome()`.  **Proves:** `ALREADY_HOME` - a train facing W accepted as home for a home set facing E.  **Refutes:** a plan that moves it.  (Names: `AutonomyBuilder.heading` calls the arrival-W copies "(eastbound)" and "(eastbound, reverse)"; if they differ, list `layout.getPoints()` with `getCopyArrival()` and `session.facingsFor(tile)` and take the twin pair from there.)  The A* half needs an arrangement where the greedy pass cannot finish (another train standing on the home copy with its own home elsewhere); run it several times and record the final copy's facing - any run ending on the twin proves it.

**Suggested fix:** compare the facing, not the arrival: carry the copy's facing onto the running Point (the build knows `facingOf(node)`) and have `atHome` require the home copy's facing; or accept only the home copy itself.  Then change the test's `arrival()` helper to compare facings, and add a may-reverse fixture (BottomMainB).  behaviour.md section 6 needs the "or its turning twin" clause settled with Adam.

Note for MT-486: its step 1 asks Adam for "a platform that can be stopped at facing either way" and says his railway has few; BottomMainB is one, and it is the square where this bites.  A pass there is not evidence either way until the plan's final copy is checked (it is a tie).

### TDY-B2 - OB-278 says the half-measured notice now counts the berth's own square; it never looks at it, so the one half-measured state OB-278 creates is not warned about

| | |
|---|---|
| **Disposition** | Fixed - a67452cd: the half-measured notice walks the berth's own square |
| **Where** | `AutonomySession.java:8803-8826` (`stationsWithAHalfMeasuredApproach`); compare `Layout.java:9613-9653` (`whyABerthCannotHoldIt`) |

Commit `5cb7cf01` (OB-278): "The half-measured notice counts the berth's own square as the berth rule does."  The change was to delete one line from the loop:

```java
for (GraphReducer.TileStep step : arriving.getPath())
{
    // The berth's own square is rail the walk spends first (OB-278), so it counts as a measured
    // place here as it does in `Layout.whyABerthCannotHoldIt`.  ...
    if (getGraph() != null && takesNoLength(step.getTile())) continue;
    if (store.getTileLength(step.getTile()) > 0) anyMeasured = true;
    else if (!store.isTileLengthAnswered(step.getTile())) unmeasured++;
}
```

But `ReducedEdge.getPath()` is "The tiles between the two Points, endpoints excluded" (`GraphReducer.java:228-236`; `placesAlong` adds the end square separately, and `routesBlockedByStandingTrains` relies on `places.size() == path.size() + 1`).  The berth - the edge's END - was never in the loop, so the deleted `if (step.getTile().equals(square)) continue;` was dead and removing it changed nothing.  The notice still ignores the berth.

What changed underneath it is the rule it warns about.  Since OB-278 `whyABerthCannotHoldIt` counts the berth in `anyMeasured` (the loop runs to `spans.size()`, not `size() - 1`), so a berth measured on an otherwise unmeasured approach is now JUDGED: a train longer than the berth claims every unmeasured square behind for nothing and is refused against any road sharing any of them - the "half measured is the trap" the notice exists for (Adam, 2026-09-19: *"We want clear warnings to the user"*).  Before OB-278 that state returned null (nothing measured but the berth = not judged), so the notice's blind spot did not matter; now it is the state `Automation.md` produces first (the berth rule's own comment: it "tells him to measure the berth square first").  In it the editor lists nothing, while the berth refuses every train longer than its own square.  The refusal still carries RTX-C2's "N squares have no length" note, which is why this is B and not A.

**The finding store has the earlier half of this as a non-defect:** `OP2-D1` (closed, 2026-09-19): "The half-measured detector does NOT fire ... on a station whose only measured square is its own".  That was right while the rule left the berth out of `anyMeasured`; OB-278 removed that premise and the detector did not move with it.

A smaller disagreement in the other direction: an unmeasured berth on a measured approach is counted unmeasured by the rule (`unmeasured++` on the last place) and not by the notice, which reports one square fewer.

**Verification request (execution, fast, no railway):** in `testMassAssignLengths`, `openBerthBehindASwitch(key(5, 1))`, then `session.setTileLength(key(5, 1), 2)` and nothing else.  Build (`session.buildConfiguration()` -> `parseAuto`) and ask `Layout.whyABerthCannotHoldIt(Arrays.asList(<edge into the berth over the switch>), <train of length 3>)`.  **Proves:** the rule returns a refusal while `session.stationsWithAHalfMeasuredApproach()` is empty.  **Refutes:** the map contains `key(5, 1)`.  (If the fixture's switch is shared by no other road, the rule may return null for want of a road to foul; then assert only that the notice is empty for a berth-only measurement, which is the half of the claim that rests on reading `getPath`.)

**Suggested fix:** after the step loop, visit `arriving.getEnd()` too (measured -> `anyMeasured`; unmeasured and unanswered -> `unmeasured++`), and correct the commit's claim in the comment.

## C - low

### TDY-C1 - The IMPOSSIBLE pre-check still asks about any copy of the home square, while the goal now needs the home's arrival

| | |
|---|---|
| **Disposition** | Fixed - ce8983ce (claim 273727eb, red first): the pre-check asks `homeCopiesOf` |
| **Where** | `HomeStaging.java:551-560` (`canGetHome`, `whyNotHome`), `HomeStaging.java:2094-2151` |

OB-282 narrowed the goal (`atHome`) for a home with a fixed facing, but its siblings `canGetHome` (and `whyNotHome`, `canRestOnSquare`) still answer "can the train reach and rest on ANY copy of the home square".  Where only the other arrival's copies are reachable or long enough - a directional approach, a train that cannot reverse, a length rule on one copy - the pre-check passes, the search then spends its whole budget and answers `NO_PLAN_FOUND` ("maybe"), where the pre-check exists to answer `IMPOSSIBLE` naming the locomotive with a reason (FR-078).  Nothing is driven wrongly; the diagnosis and the latency are.  Reading only.  **Verification request:** on a hand-built fixture where a square has two arrivals and the train can reach only one of them (one-way approach), home the train on the other arrival's copy with `setHomeLocomotive` and plan: `NO_PLAN_FOUND` proves, `IMPOSSIBLE` refutes.  **Suggested fix:** when `home.isHomeFacingFixed()`, restrict the copies these three walk to those with the home's arrival (or facing, after B1).

### TDY-C2 - A home's facing is read from the setup only, the reading CONF-B1 fixed in `facingOf` for exactly this staleness

| | |
|---|---|
| **Disposition** | Fixed - 919e0dc8 (claim cdedb3fc, red first), and its third case in 1facc0c2 (TDY2-C4) |
| **Where** | `AutonomySession.java` `writeHome` / `homeFacingOf` (~7290-7320); `AutonomyEditorPanel.java:5101` (`picked.equals(locomotiveAt(tile))`) |

OB-282's rule is "Direction it is facing when home is set".  `homeFacingOf(tile, locomotive)` answers it from the setup alone: the square's `loc` and its `FACING` property.  `AutonomySession.facingOf(String, Layout)` was given a running-layout-first reading (CONF-B1) because "the setup names the square a train SET OFF FROM until `captureFromLayout` writes the arrival back", and its javadoc calls shipping the same defect in a sibling "`fix-one-site-sweep-the-siblings` almost verbatim".  The home door is that sibling: after a run that turned the train at this square (a may-reverse square, or a terminus), and before a capture, the setup's `FACING` is the pre-run heading, and the home is saved facing the way the train no longer faces.  Two smaller edges of the same reading: where the setup has the train here but no `FACING` recorded, the home silently becomes the square (no question is asked, because `locomotiveAt(tile)` - also the setup - says the train is here); and where the train has since driven here but the setup does not know yet, the operator is asked a question the railway could answer.  Whether the editor door is reachable before a capture depends on which doors capture first (the editor's open does; I did not establish whether the running diagram's square menu does), so this rests on reading.  **Verification request:** on a built session with a train placed at a may-reverse station facing E in the setup, move that train on the running layout onto the square's W-facing copy without capturing, then `session.setHome(tile, train)` and read `HOME_FACING`: `E` proves, `W` refutes.  **Suggested fix:** take the facing from `facingOf(locomotive, running)` when a running layout is at hand, as the paste doors do.

### TDY-C3 - The editor's tail pick still says "click the farthest sensor the tail has crossed" while it now outlines sensors the tail has not reached

| | |
|---|---|
| **Disposition** | Fixed - 59fdb67d: the editor's tail pick uses MT-477's heading and wording, eight bundles |
| **Where** | `AutonomyEditorPanel.java:3923-3959` (`armTailPick`), `messages*.properties` `autosetup.ui.promptClickTailCrossed`, `headingTailCrossed`, `hintTailCrossed` |

MT-477 added `Choice`s with `isReached() == false` - "towards X (not reached)" - and reworded the dialog (`autolayout.ui.askTailCrossed`: "Where it has not reached one, pick the way it lies").  `armTailPick` outlines `choice.getFarthest()` for every choice, so an unreached sensor is outlined too, and clicking it records the road towards it - which is the intended meaning - but the prompt beside it still reads "Click the farthest sensor the tail of the train at {0} has crossed - one of the outlined squares", and the right-click menu heading is still "Farthest sensor the tail crossed".  The operator is told to click a sensor the tail crossed and shown one it did not.  Wording only; the recorded road is the one the dialog would record.  Reading only; **verification** is looking at the editor at BottomSecondary with a three-unit train (MT-477's own case).  **Suggested fix:** the same clause the dialog gained, in the click prompt, the heading and its hint, eight bundles.

### TDY-C4 - OB-270's claim pins the old code's variable and leaves the new one's unasserted

| | |
|---|---|
| **Disposition** | Fixed - 59fdb67d: the cut claim cuts from the westbound copy where there is one |
| **Where** | `test/ui/testACutTrainArrivesTheWayItWouldDrive.java:157-205` |

The fix walks from `cutFrom` - the Point the train stood on when cut - and the cut heading is used only when that walk finds no route.  The test forces `cutFacing = W` by reflection ("GOING WEST, as he described it ... Set here rather than read") but chooses the Point it cuts from as the LAST destination copy of BottomSecondary in `facingsFor` order, and never asserts which way that copy faces.  So "a train cut going west lands facing east" is the story in the message; what is tested is "a train cut from whichever copy came last lands facing east".  If that copy faces E, the claim still goes red under the mutation it names (the paste falls back to the forced W), so it is not vacuous, but it does not show what its message says.  Reading only.  **Verification request:** add `assertEquals(session.facingsFor(secondary).get(fromName), Side.W, ...)` after choosing `from` (or choose the copy by that facing): passing confirms the premise; failing means the claim tests a different journey from the one Adam described.

## D - not defects

### TDY-D1 - OB-278's three walks agree

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`walkOneTail` spends the arriving copy's last place (the standing square) first on the first hop, the hop budget is `chargedHere` so the two budgets cannot drift, `spendableAllowance` / `atRest` are gone with no caller left (`git grep` finds no `allowance` arithmetic in src); `whyABerthCannotHoldIt` now counts the berth in `anyMeasured` and in the walk; `walkBackFrom` spends `store.getTileLength(at)` once, after the first `next` is found, which the covered first edge guarantees whenever the Layout walk claimed anything.  `TailCrossedPrompt` already spent whole edges including the standing square.  `measuredRouteIn` / the room rule always counted it.  The MUTATION lines of `testTheSquareWithTheSensorConsumesItsLength`, `testTheBerthTakesWhatItMeasures` and `testTheOrangeStopsWhereTheTrainDoes` would each turn red as stated (reasoned from the 2 + 1 fixture).  The one miss is the editor notice (B2).

### TDY-D2 - OB-279's decoder is the encoder's inverse

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`Side` has four values, so `transparentRouteId`'s change from `* 4` to `* Side.values().length` writes the same ids; `transparentRouteOf` rejects state != 0 and index < 100, which no port-map index reaches.  The only other index-into-port-map reader (`TileGraph.java:1466-1473`) already refuses index >= 100.

### TDY-D3 - OB-280: every path routing refuses touches a grey square

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`isPathClear` refuses by covered-edge identity as well as by claimed places, and the grey shows only places; but a covered edge's end place (the square the walk turned at, or the standing square) is always claimed, so any path over a covered edge, either direction, starts or ends on a grey square.  `locationsOf` returns exactly one id per step, so the `places.size() != path.size() + 1` skip in `routesBlockedByStandingTrains` never fires on a built reduction.  A whole-square place yields an empty road set, which `theRoadToFade` reads as "fade the whole tile", as the javadoc says.

### TDY-D4 - The new fields survive their round trips

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`Edge.answeredPlaces`: written by `Edge.toJSON` only where true, read in `Layout.fromJSON` inside the all-or-nothing `whole` branch, never copied anywhere else (no Edge copy constructor; `setPlaces`' only src caller is that same `fromJSON` branch, which sets the answers beside it).  `Point.homeFacingFixed` / `copyArrival`: written by `Point.toJSON`, read in `parseAuto` onto the same Point (`homeAt` is `layout.getPoint(point.getString("name"))`), cleared with the home in `setHomeLoc(null)`; Point has no copy constructor.  Capture carries `HOME_FACING` only from a copy the running layout holds fixed and drops it with the home.  The setup's `HOME_FACING` is excluded from the copy passthrough in the build (`AutonomyBuilder.java:1075`).

### TDY-D5 - MT-467's lock is a sound stand-in for "the station carried it"

| | |
|---|---|
| **Disposition** | Closed - checked clean |

Only the sync import sets it (`MarklinControlStation.java:1554`, after clearing every lock at `:1499`); `editRoute` carries it across its delete-and-re-add (X8-B5, `:2065-2085`); `MarklinRoute` is `Serializable` with `locked` not transient, so it survives a restart.  Delete and Change Route ID are offered only on unlocked routes (`RightClickRouteMenu.java:139`), so at those doors the new test reduces to the id range for the new id, which is the stated intent.

### TDY-D6 - OB-269 (`12d74ad5`) narrows only the proxy case

| | |
|---|---|
| **Disposition** | Closed - checked clean |

A candidate edge that shares rail with a running edge is still refused both ways: its own `occupancy` (raised through the running edge's `lockEdges`) by `isOccupied`, and the running edge in its `lockEdges` by `isRunOver`.  The exact reverse rail, which `deriveLocks` never pairs, is refused by place; the own-train exemption applies to that loop only, matching what `isLockHeld` did before.  Reading only - the commit's own mutation record covers execution.

### TDY-D7 - OB-281's fold is idempotent and in memory

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`open()` folds after `rebuild()`, rebuilds again if anything moved, then sets `dirty = false`; a reopen before a save folds the same units again from disk, a reopen after one finds nothing.  MT-485 says "in memory until the setup is saved", which is what the code does.  behaviour.md 5a says no length rule reads a route tile's length; `GraphReducer.lengthOf` / `sumLength` in fact read any stored length, route tiles included - but after the fold none holds one, and no UI door can give one (every door asks `isIgnored`, which is true of transparent tiles), so nothing behaves differently.

### TDY-D8 - OB-270's clipboard state has one life

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`cutFrom` is set with `cutLocomotive` on a cut and cleared with it on a successful paste and when the locomotive is deleted or renamed (`TrainControlUI.java:7298-7302`, `:21422-21430`); a dismissed question keeps both, so the next paste walks again.  It is a name, so a rebuild between cut and paste finds the new Point or falls back to the cut heading.  The paste's may-reverse question still offers `facingsFor` (every copy) while the record and copy choice now use `placeableFacings`; on both frozen railways every may-reverse square keeps both headings on destination copies even with one arrival barred (plain copy one way, turning copy the other), so the wider offer cannot pick an unplaceable heading there.

### TDY-D9 - testTheWashIsNoLongerThanTheTrain.testATrainOfLengthOneCoversOneSquare's new control asks the model (ui.isTrackCovere

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`testTheWashIsNoLongerThanTheTrain.testATrainOfLengthOneCoversOneSquare`'s new control asks the model (`ui.isTrackCovered`) where the old one painted the label, so its message ("would pass equally well with the wash switched off") overclaims - but `testTheSquareTheTrainStandsOnIsOrange` in the same class paints that square, so nothing is left unchecked.

### TDY-D10 - Bundles

| | |
|---|---|
| **Disposition** | Closed - checked clean |

All eight `messages*.properties` are pure ASCII, and each gained exactly the same 27 keys today.  No stale "Signal Protecting This Station" / "Entry Guard Signals..." label is left in src, bundles or docs outside history paragraphs.

### TDY-D11 - MT-479

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`show.run()` runs after `headingWidth[0]` is set, so the first paint is wrapped; `wrappedAt` escapes the three HTML characters; the exit guard's short sentence falls back to the buttons' width, as the javadoc says.

### TDY-D12 - MT-477's new arithmetic agrees with the walk after OB-278

| | |
|---|---|
| **Disposition** | Closed - checked clean |

`reachOf` spends a rail from its end place, which at the first junction is the standing square and further back is the junction square the previous hop did not charge (an edge's length is its path plus its END), so nothing is charged twice.  An unreached choice records `[hop, ...]`, which the tail walk follows at the first hop (`roadBackAtTheFirstHop`) and at forks (`cameFromAlong`) and then stops for want of length.

### TDY-D13 - FR-096's entry guard

| | |
|---|---|
| **Disposition** | Closed - checked clean |

is thrown only where `executePath` records the arrival, after `setArrivedAlong`, i.e. on a journey's last Point; a passed square is never `arrived`.  It commands through `Accessory.setState` like the exit guard; the exit guard's aspect memo re-reads `acc.isRed()` before trusting itself, so a shared accessory cannot leave the memo stale.


## What this pass did not cover

- **Nothing was executed.** B1's first probe and B2's probe are deterministic and cheap; every other request above is secondary.
- **The refreeze and the test-only commits** (`a9590790`, `e77c96e9`, `0a5dab2a`, `87a8caa1`, `cda5f42a`, `3d034f1d`, `63736546`, `41bd1831`, `91f27dfb`, `b16eead2`, `281c79de`): read only where a changed claim touched a fixed rule (`c755d965` in D9 and D1).  I did not audit whether each expected value moved to the measured railway was re-derived or copied from the code's output.
- **Commits outside the task's list that are still today's**: FR-094 (Mass Assign Train Lengths), OB-272 (caption option, Control+L), OB-273, OB-274, OB-276, OB-277/OB-208 were read for their interaction with the listed work only; OB-272's caption/preference code was not reviewed.
- **The MT-477 walk's edge cases**: tails through portals, balloons and a junction whose rails share no square before the standing square (`lastSharedPlace` returns the standing square there).  Reasoned only for the ordinary fork.
- **OB-282 on a compulsory-turn home** (every copy a turning copy, facing = arrival): `homeCopy`'s second loop can put the home there, and `atHome`'s arrival test then agrees with the facing; not traced further.
- **`issues.md`** (1,752 lines removed today, the Inbox tidy "on Adam's word") and `open-questions.md` were not audited against the store.
