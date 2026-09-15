# The 2026-09-15 feedback fixes: round 2 validated

**Status:** open 2026-09-15 - round 3 fixed B1, C1, C2 and C3 in `ae19fbf3` (claims and the clock seam in `55b0d0b5` and `3f5410d8`)

**Prefix:** MFW (checked free with MFR, MFV and MFX before the review: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, every declaration spelling in `docs/reviews/`, and a grep of `src/`, `test/` and `docs/`)

**Covers** round 2 of the MFR cycle: the claims in `099f6327`, the fixes in `ab217322` and the documentation corrections in `2a4b2cc8` (`2026-09-15-MFV-fix-validation.md`).  One validator (Opus), read-only; every fix below was run.

---

## Round 2, item by item

| MFV | verdict | note |
|---|---|---|
| B1 | Confirmed fixed | the retry cannot be starved - its deadline is always the start plus the whole budget; claimed on a stepped clock in round 3 (MFW-C3) |
| C4 | Fix incomplete | Control+H and Control+B still left a gesture armed - MFW-C1 |
| C3 | Confirmed fixed | the reorder it names would now fail the claim |
| C1 | Confirmed fixed | |
| C2 | Confirmed fixed | |
| C5 | Confirmed fixed | |
| C6 | Confirmed fixed | |

**On MFV-B1's trade.**  A plan that A* from the greedy arrangement would have found between half the budget and all of it is lost unless the retry finds it in the other half.  Nothing in the repository measures how long that search takes on Adam's railway, so the loss cannot be ruled out and has no evidence for it either; against round 1, which gave the retry no time, the share-out is the better side of it.

---

## A - wrong behaviour on the layout

None.

---

## B - incorrect results or refusals in specific configurations

| id | status | where |
|---|---|---|
| MFW-B1 | Fixed | `AutonomyEditorPanel.promptHomeFor` - Control+H wrote a home on any square |

### MFW-B1 - Control+H opened the home chooser on any square

| | |
|---|---|
| **Disposition** | Fixed |

R28-C5 restored the key at Adam's word as `if (tile != null) promptHome(tile)`.  The menu offers the home item only on a station, past the ignored-square branch; nothing on the write path asks either, and `writeHome` takes the locomotive's home off the station it had.  So Control+H over plain track, a switch or a page left out of autonomy wrote a home onto a square that is not a station.  Found in the sweep of keyboard doors; it predates this cycle.

**Confirmed by running.**  `regression.testControlNAsksTheMenusQuestion.testControlHOffersAHomeOnlyWhereTheMenuDoes`: the menu and the key on a station, on plain track, and on the station with its page excluded.  Red: *"AutonomyEditorPanel has no offersAHome: the key asks its own question, not the menu's"* - red on the missing predicate, as MFR-B3's was, because the key opens a modal dialog and cannot be asked directly.

**Fixed, round 3.**  `offersAHome` is the menu's own question - not a text or blank square, not ignored, the run's leader a Point and a station - and `promptHomeFor` acts only where it says so, on the leader the menu acts on, saying why otherwise.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| MFW-C1 | Fixed | Control+H and Control+B left a gesture armed |
| MFW-C2 | Fixed | the MFR and MFV documents' statuses did not match the commits |
| MFW-C3 | Fixed | MFV-B1 was unclaimed |

### MFW-C1 - two more keys left a gesture armed

**Fixed, round 3:** `promptHomeFor` and `promptMaxTrainLengthFor` call `cancelPendingGesture` first; the claim asks all five keys that are doors onto the menu - S, E, N, B and H.  Red on the same missing predicate before Control+H could be pressed safely; Control+B's own red was not seen separately - it has the same one-line fix, and the claim now asks it.

### MFW-C2 - the statuses

The MFR header credited C2 to round 1 with no word that round 2 replaced it, and catalogued C5 as fixed where its body and MFV call it not a defect; the MFV status line credited C5 and C6 to the wrong commit.  **Fixed:** both documents.

### MFW-C3 - MFV-B1 claimed on a stepped clock

The validator's point: fifteen seconds was the cost of reading the wall clock, not of the rule.  **Fixed, round 3:** `HomeStaging` reads time through a `clock` field, the wall clock on the railway, and `core.testReturnHomeKeepsClearOfTheTailsItLeaves.testTheRetryFromTheStartHasTimeLeft` steps it 300 ms a read on the X, Y, Z railway with sixty sidings, so the first search cannot run out of arrangements before its share of time does.  A precondition requires more than half the budget read, so a greedy pass that brought everyone home cannot pass it vacuously.  Red against round 1's shared deadline, put back for the run and then restored from the commit: *"the first search spent the time and the retry from the start ... had none left: NO_PLAN_FOUND after 15600 ms on the search's clock"*.  The first version of the claim was vacuous - with sixty sidings Layout's point map has 128 buckets and the greedy pass met X first and brought everybody home - and its precondition said so (the clock read once).  The two start squares are named so the greedy pass meets Y first (Java's String hash, checked against the ten-point fixture that went red as Y-first); the precondition stays, for the day the map or the names change.

---

## D - looked wrong and is not

| id | what |
|---|---|
| MFW-D1 | A key drops what a right-click drops - the Why answer, tail and signal picks, a portal - through the same cancel |
| MFW-D2 | The gesture claim's excluded page resets nothing else; arming on an excluded page is reachable by ticking Exclude after arming |
| MFW-D3 | `tailKey`'s cost is one short tail walk per successor beside a route search per successor; the cache lives for one snapshot |
| MFW-D4 | The greedy pass ignores the deadline, but its time comes out of the first search's half |
| MFW-D5 | `measuredRouteIn`'s two callers read the same count at the destination |

---

## What the passes missed

- **"Every key" was three keys.**  The claim for MFV-C4 was written from the finding's list rather than from `LayoutEditor`'s key handler, and the handler has five doors onto the menu.  The sweep that found Control+B and Control+H also found Control+H asking nothing at all.
- **"Untested by design" was a choice of instrument.**  The budget rule was judged too slow to claim because the claim was imagined against the wall clock; a stepped clock makes it fifty reads.
