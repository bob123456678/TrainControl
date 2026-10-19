# RSA75 - Validation of TrainControl 3.0.2, round 5, over everything since RSA74 (`2ce0052e..91370232`): OB-357 to OB-361, FR-137, FR-139, MT-760's and MT-767's notes, OB-359's removals and the new sample; OB-361's run correction, OB-357's redraw and FR-137's notices hold; track laid on an empty square beside a one-way run was saved one open late (C1), a route started again as it ended kept a still mark (C2), a heading was copied and markup stripped (C3), the sample ships with Simulate on (C4), and the changelog and the guide lagged (C5, C6) - CONVERGED

**Status:** closed

**Prefix:** RSA75

**Reviewed:** branch `dev` with the clipboard change, at `91370232` (dev `be2361dc` plus MT-767's note), and `080d43d4` (Adam's updated sample) by `git show`: `git log 2ce0052e..91370232`, every production hunk, against Adam's rulings in the brief.  Read-only; no probe (a full battery held the test lock).  2026-10-18.

## Method

**Read whole:** `TileGraph` run-finding, `AutonomyCompanionStore.normalizeRuns` beside `normalizeToArms`, `AutonomySession.rebuild`, `open`, `moveTiles`, `forgetTiles`, `followsItsRun`, `runsAs`, `guardNotices`, `tilesWithATrappedArrival`, `sensorAddressesSharedByPlaces`, `moveOntoFacingCopy`; `RouteRunningMarks`, `MarklinRoute.execRoute`, `TrainControlUI.routeStarted`/`routeFinished`/`executingRouteNames`/`routeRunningRefusal`; `LayoutEditor.orientationThatJoins`/`joinScore`/`execCopy`; `AutonomyEditorPanel.copyNoticeToClipboard` and both notice lists; the Readme, Automation.md and the two samples.

**Counts:** 0 A, 0 B, 6 C, D below.

**This round has CONVERGED: no A and no B.**  C1 to C3, C5 and C6 fixed; C4 left on Adam's word.

---

### RSA75-C1 - Track laid on an empty square beside a one-way run was given the run's direction in memory and saved only at the next open, which logged it as a correction

| | |
|---|---|
| **Disposition** | Fixed - `5011152d`: `AutonomySession.moveTiles` answers true when the rebuild brought a run or a switch into line, so every door that saves on it (the drop, paste, fill, delete) writes the direction with the edit; no correction line.  Claim `core.testAutonomyDiagramSession.testTrackLaidBesideAOneWayRunIsSavedWithTheEdit`, red first. |
| **Grade** | C - nothing a train does changes; a message about track just laid. |
| **Names** | OB-361 (`25fcf33f`) |
| **Where** | `AutonomySession.java:4367`, `:452-460`, `:784-812`; `LayoutEditor.java:2715-2722`; `TrainControlUI.java:3151`. |
| **Needs execution** | done - the claim. |

---

### RSA75-C2 - A route started again between its last run's end and the end notice kept a mark that did not turn

| | |
|---|---|
| **Disposition** | Fixed - `7f9b4a21`: `RouteRunningMarks.finished` only asks the running routes again (`check`), which takes out each route that is no longer executing. |
| **Grade** | C - a race of milliseconds; display only. |
| **Names** | FR-137 |
| **Where** | `RouteRunningMarks.java:82-86`; `MarklinRoute.java:1228-1234`. |
| **Needs execution** | read. |

---

### RSA75-C3 - A click on a heading of the notice lists copied the heading over the clipboard, and a notice's text lost anything in angle brackets

| | |
|---|---|
| **Disposition** | Fixed - `693ecbf0`: only a row with a severity (a notice) is copied, as the list shows it, nothing stripped.  Claim in `core.testAutonomyDiagramSession.testClickingANoticeCopiesItsTextToTheClipboard`. |
| **Grade** | C |
| **Names** | MT-767 |
| **Where** | `AutonomyEditorPanel.java:1269`, `:11625-11631`; `AutonomyViewerPanel.java:502`. |
| **Needs execution** | done - the claim. |

---

### RSA75-C4 - The sample configuration ships with Simulate ticked, so a normal start logs that simulation needs debug mode for a brand-new layout

| | |
|---|---|
| **Disposition** | Ruled - kept (Adam, 2026-10-18: *"no, keep simulate in the sample"*). |
| **Grade** | C - a log line. |
| **Where** | `sample_layout.zip`, `config/autonomy/configuration-Sample Autonomy.json`. |
| **Needs execution** | read. |

---

### RSA75-C5 - The Readme's 3.0.2 changelog named none of this round's user-visible changes

| | |
|---|---|
| **Disposition** | Fixed - `9d7bcabf`. |
| **Grade** | C - wording. |
| **Where** | `Readme.md`, v3.0.2. |
| **Needs execution** | read. |

---

### RSA75-C6 - Automation.md quoted the old wait-for-a-route message, which now names the route

| | |
|---|---|
| **Disposition** | Fixed - `9d7bcabf`. |
| **Grade** | C - wording. |
| **Where** | `Automation.md:644`. |
| **Needs execution** | read. |

---

### RSA75-D1 - OB-361's correction, OB-357's redraw, FR-137's notices, the refusal naming, OB-358, OB-360, FR-139, OB-359 and the sample

| | |
|---|---|
| **Disposition** | Checked - clean (but C1 to C4). |
| **Grade** | D |
| **Where** | Runs are bounded by feedback squares and only feedback squares become Points, so the correction changes nothing a train does; it cannot fight OB-351, a click, the One-Way tool, undo, Cancel or an import, and is saved and logged once; a click leaves every other run alone; `refreshUI` takes no monitor and cannot loop; a run always announces its end; the names are escaped; OB-360 turns only palette placements; FR-139 is quiet where OB-150 speaks; nothing refers to OB-359's removals; the updated sample's addresses are distinct and it opens clean. |
| **Needs execution** | read. |
