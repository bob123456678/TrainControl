# The 2026-09-15 feedback fixes: round 1 validated

**Status:** open 2026-09-15 - round 2 fixed B1, C1, C2, C3, C4, C5 and C6 in `ab217322`

**Prefix:** MFV (checked free with MFR, MFW and MFX before the review: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, every declaration spelling in `docs/reviews/`, and a grep of `src/`, `test/` and `docs/`)

**Covers** round 1 of `2026-09-15-MFR-feedback-fixes-review.md`: the claims in `3369b650` and the fixes in `ddaa1b9b`.  One validator (Opus), read-only; every fix below was run.

---

## Round 1, item by item

| MFR | verdict | note |
|---|---|---|
| B1 | Confirmed fixed | the Manual answer agrees with `getPossiblePaths` station by station; the autonomy answer is unchanged |
| B2 | Confirmed fixed | the key, `routesOf` and `rebuild` agree; growth of the state space is MFV-B1's concern |
| B3 | Confirmed fixed | `offersAStationName` matches the menu line by line; the claim was thin (MFV-C3) |
| C1 | Held correctly | a pre-selection the operator confirms |
| C2 | Fix incomplete | the shared deadline starves the retry - MFV-B1 |
| C3 | Held correctly | over-refusal only, unchanged by B2 |
| C4 | Confirmed fixed | one restored message is stale under FR-088 - MFV-C2 |
| C5 | Not a defect | the count is a set of names, never truncated; Manual cannot fall below Auto |
| C6 | Confirmed fixed | `measuredRouteIn` is never below the room past the switch, so the refusal quotes the route in |
| C7 | Fix incomplete | two test comments - MFV-C1 |

---

## A - wrong behaviour on the layout

None.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| MFV-B1 | Fixed | `HomeStaging.search` - the retry from the start could have no time left |

### MFV-B1 - the Return Home retry could get no time

| | |
|---|---|
| **Disposition** | Fixed |

MFR-C2 put every A* of one search on one deadline, set before the greedy pass.  The retry from the start exists for the arrangement that exhausts A* from the greedy one (OB-228), and on a real layout that exhaustion is the deadline, not `SEARCH_LIMIT` - so the retry began with no time and answered null at once, turning a plan the round before found into NO_PLAN_FOUND.  Touches MT-440.

**Fixed, round 2.**  The budget is shared out: A* from the greedy arrangement may use half, A* from the start has the rest, and when the greedy pass moved nothing the one search has all of it.

**Not claimed by a test.**  The failure is a budget running out, which only a search too big to exhaust inside it shows - fifteen seconds of a test run per assertion, on a layout the fixtures do not have.  Recorded here as untested so the next validation can weigh that.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| MFV-C1 | Fixed | two test comments still bounded a station by its whole approach |
| MFV-C2 | Fixed | a restored message stated the pre-FR-088 rule |
| MFV-C3 | Fixed | `testControlNAsksTheMenusQuestion` checked two squares |
| MFV-C4 | Fixed | Control+S and Control+E left a gesture armed; the menus and Control+N put it down |
| MFV-C5 | Fixed | behaviour.md section 7 called a switched-off station reachable by hand |
| MFV-C6 | Fixed | the MFR document said "measures 2" where the red said 1 |

### MFV-C1 - stale "whole approach" in two tests

`testAPassingTrainMayStandAcrossThePoints`'s class javadoc and `testABerthAndAPlatformJudgeAnOverhangDifferently.testAPlatformStillRefusesATrainLongerThanItsApproach`.  **Fixed, round 2:** they say the measured route in; the second's fixture is one leg, so there it is the approach alone.

### MFV-C2 - a restored message under FR-088

`testTheRecordedRoadIsTheOneOfferedFirst` passes only because its fixture has two sensors nearest the back, and its message said a train with no road never has a choice made for it.  **Fixed, round 2:** the message says FR-088's rule.

### MFV-C3 - the Control+N claim checked two squares

Reordering `isIgnored` above the text branch would have refused the empty square MT-436 uses and left the claim green.  **Fixed, round 2:** the claim also asks a label and an empty square of both the menu and the key.  Green before and after the round's fixes, as a widened claim should be.

### MFV-C4 - two keys left a gesture armed

**Confirmed by running.**  `regression.testControlNAsksTheMenusQuestion.testEveryEditorKeyPutsDownAGestureAsTheMenuDoes` arms Test a Path and presses each key on a square where none opens a dialog.  Red: *"Control+S left Test a Path armed, where the right-click menu puts it down before offering anything"*.  The red stopped at the first key, so Control+E was never seen failing on its own - it has the same one-line fix.  The claim as first written armed the tool with a click, and on the ignored page the panel's refresh disables Test a Path after that click, so after the fix it failed its own precondition before Control+E; it now arms the tool through the panel's field, the state a click leaves.  **Fixed, round 2:** `promptNameFor` and `promptLengthFor` call `cancelPendingGesture` first, as `showStationNameFor` does.

### MFV-C5 - a switched-off station by hand

`isPathClear` refuses an inactive destination in every tier, so on Manual it is listed under *cannot be sent*, not as reachable.  **Fixed:** behaviour.md section 7 says so, in agreement with its own first paragraph.

### MFV-C6 - the MFR document's C6 number

**Fixed:** "measures 1", as the red and the commit say.

---

## D - looked wrong and is not

| id | what |
|---|---|
| MFV-D1 | Control+N cancelling a tail pick or signal pick leaves nothing waiting - both are armed from a menu item and completed by a later click |
| MFV-D2 | `offersAStationName` answers true on a text square with `menuOnly` set - `buildTextMenu` asks no `menuOnly` either, and the key returns early on it |

---

## What the passes missed

- **MFR-C2 was a fix that made a fix worse.**  Sharing the deadline answered the finding's letter (twice the budget) and broke the retry the same commit series had added for OB-228.  No test could see it: every fixture exhausts its search in milliseconds.
- **MFR-B3's claim tested the case it was written for and not the neighbours.**  The empty square, the one a station name most often goes on, was never asked.
