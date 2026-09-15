# The 2026-09-15 feedback fixes: round 3 validated, and the cycle closed

**Status:** closed 2026-09-15 - the third and last validation; its wording findings C1 and C3 fixed in `6cb25a2b` (claims in `71194951`) and C2 in `bd31e252`, no further validation run (Adam asked for up to three)

**Prefix:** MFX (checked free with MFR, MFV and MFW before the review: `SELECT DISTINCT ref FROM finding` in `docs/manual-tests/triage.db`, every declaration spelling in `docs/reviews/`, and a grep of `src/`, `test/` and `docs/`)

**Covers** round 3 of the MFR cycle: the claims and clock seam in `55b0d0b5` and `3f5410d8`, the fixes in `ae19fbf3`, and the status corrections in `fdb98884` (`2026-09-15-MFW-fix-validation-round-two.md`); and, as the last round, the whole cycle's net change `9e626e48..ae19fbf3` in `src`.  One validator (Opus), read-only.

**The validator's verdict: fit to close** - no A or B findings, and no C finding changes what a pending hands-on test checks.

---

## Round 3, item by item

| MFW | verdict | note |
|---|---|---|
| B1 | Confirmed fixed | `offersAHome` follows the menu step by step and the key acts on the leader the menu acts on; its refusal gave the wrong reason - MFX-C1 |
| C1 | Confirmed fixed | all five keyboard doors cancel before they refuse; the claim presses all five |
| C3 | Confirmed fixed | red on round 1's shared deadline for the reason it names ("after 15600 ms" is the shared deadline plus one read); not flaky, and the HashMap order that would break it fails its precondition |
| C2 | Fix incomplete | two status lines still named the wrong commits - MFX-C2 |
| MFR-C1, MFR-C3 | Held correctly | round 3 touched neither |

---

## A - wrong behaviour on the layout

None.

## B - incorrect results or refusals in specific configurations

None.

---

## C - documentation, tests, minor

| id | status | where |
|---|---|---|
| MFX-C1 | Fixed | `promptHomeFor` refused with Control+B's sentence about a maximum train length |
| MFX-C2 | Fixed | the MFR and MFW status lines named the wrong commits; MFR-C2 still said "not claimed by a test" |
| MFX-C3 | Fixed | the main window's menu named Control+N, its notes key |

### MFX-C1 - Control+H's refusal talked about a maximum train length

`promptHomeFor` said `autosetup.ui.infoNotAStationHere`, Control+B's: the right remedy, make it a station, under the wrong reason.  **Fixed:** its own sentence, `autosetup.ui.infoNotAStationForAHome`, in all eight bundles.  `regression.testControlNAsksTheMenusQuestion.testControlHSaysWhyItOffersNoHome`, red: *"Control+H refused a home on plain track with Control+B's reason, about a maximum train length: ... No train stops on this square, so it has no maximum train length."*

### MFX-C2 - the status lines

The MFR status credited C4 to `ddaa1b9b`, which touches no test (the claims were restored in `3369b650`); the MFW status and `4e6081e4`'s message credited C2 and C3 to `ae19fbf3`, which holds B1 and C1 only; and MFR-C2's body kept "not claimed by a test" after MFW-C3 claimed it.  **Fixed:** all three.  `4e6081e4`'s commit message stays as written - a commit message is not rewritten - and this entry is the correction.

### MFX-C3 - the main window's menu named the editor's key

`TrainControlUI` builds the same menu with `setMenuOnly(true)`, and on a text or empty square its caption items carried "(Control+N)" - which in the main window is the notes key.  **Fixed:** the key is named only where the panel is the editor's.  `testTheMainWindowsMenuDoesNotNameTheEditorsKey`, red: *"the main window's menu names Control+N, which is the notes key in that window: ... It is a live readout, not a name plate.  (Control+N)"*; the editor's menu is its control.  The Home, Rename and Length tooltips are not gated either; those keys do nothing else in the main window, and are left as they are.

---

## D - looked wrong and is not

| id | what |
|---|---|
| MFX-D1 | The Control+H claim cannot tell `leaderOf(tile)` from `tile`, and cannot need to: every reducer Point is its own leader |
| MFX-D2 | `HomeStaging.clock` is private, set only by its initializer on the railway, and confined to one planning call on one worker |
| MFX-D3 | The C3 claim's HashMap dependence is in the safe direction: the order that breaks it fails its precondition |
| MFX-D4 | `lastWhyTile` cannot re-ask a stale square: every way of disarming the Why tool clears it |
| MFX-D5 | The refusal quotes the route in only at a square autonomy may choose; a parking berth still quotes the room past the switch |

---

## The cycle

Four documents, one Fable review and three Opus validations of 2026-09-15's fixes for Adam's MT feedback (FR-086, FR-087, OB-225, OB-226, OB-227, FR-088, OB-228).  Across them: five B findings fixed (MFR-B1, B2, B3; MFV-B1; MFW-B1), and twenty C findings fixed or held with a reason (eight in MFR, six in MFV, three each in MFW and MFX); nothing at A.  The last validator read the whole net change in `src` once and found one thing no pass had looked at (MFX-C3).

## What the passes missed

- **The same status error twice.**  MFW-C2 was raised because round 2's statuses named the wrong commits, and round 3's statuses did it again - the round's hash was written into the status line before the round's parts were in separate commits.  Status lines now name each finding's commit, not the round's last one.
- **A borrowed sentence.**  MFW-B1's fix reused the nearest refusal string without reading it; the claim checked what the key refuses and not what it says.
