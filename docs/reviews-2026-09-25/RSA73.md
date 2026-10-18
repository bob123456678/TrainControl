# RSA73 - Validation of TrainControl 3.0.2, round 3, over RSA72's fixes and a last sweep since 3.0.1 (`a74e279a..e3bc351b` on `dev`): no run starts while a route runs at the press, but a route begun between the press and the dispatch still met the run (C1); the refusal after a Yes named the wrong thing (C2); the guide did not say runs wait for a route (C3) - CONVERGED, every C fixed

**Status:** closed

**Prefix:** RSA73

**Reviewed:** branch `dev` at `e3bc351b` (*RSA72-C1: the 3.0.2 changelog says runs wait for a route*): RSA72's fixes (`4d4bfc5c..e3bc351b`), every door that starts a run and every run that dispatches from inside another, then `a5e787aa..e3bc351b -- src Readme.md Automation.md` once more.  Read-only, in a worktree detached at the commit; no probe (a full battery held the test lock).  2026-10-18.

## Method

**Read whole:** `TrainControlUI.whyNoTrainMayBeSent`, `refusedToSendATrain`, `refusedForAnEditorOnItsWay`, `sendATrainByHand`, `requestReturnToHome`, Start's and Execute Timetable's handlers, `prepareAutonomyReload`, `followTheTurn`, `routeFinished`, `executeRoute`; `MarklinRoute`'s s88 trigger; the run's own dispatch, the timetable's next leg and Return Home's staged run.

**Counts:** 0 A, 0 B, 3 C, D below.

**This round has CONVERGED: no A and no B.**  Every C is fixed (`e06443b3`, `7ca0eb29`, `2ce0052e`).

---

### RSA73-C1 - The route was asked only at the press: a route begun while Return Home planned, or while the conditional-routes or reversal question was up, still met the run, and a turn held in that gap was lost

| | |
|---|---|
| **Disposition** | Fixed - `7ca0eb29`: the last-moment question every run door asks before its dispatch, `refusedForAnEditorOnItsWay`, also refuses while a route executes (`autolayout.ui.errorWaitForARouteToFinish`), and the hand send asks it after its reversal question, before anything is counted.  Each caller gives its controls back.  Claim `regression.testNoSetupEditWhileARouteRuns.testARouteBegunAfterThePressStopsTheRun`, red first. |
| **Grade** | C - a route started in the seconds between a press and its dispatch, and a reversal in the same window. |
| **Names** | RSA72-C1, Adam: *"prevent starting autonomy etc if a route is still running"* |
| **Where** | `TrainControlUI.refusedForAnEditorOnItsWay` (`:7504`), its callers (Start's worker, Execute Timetable, Return Home after planning); `sendATrainByHand` (`:7574-7584`). |
| **Needs execution** | read; the claim. |

---

### RSA73-C2 - The refusal after a Yes to stop autonomy's trains used Place All's sentence, naming autonomy (being stopped) and asking the user to stop a route

| | |
|---|---|
| **Disposition** | Fixed - `2ce0052e`: it says "A route is running.  Wait for it to finish, then try again." |
| **Grade** | C - wording. |
| **Names** | RSA72-C2 |
| **Where** | `TrainControlUI.prepareAutonomyReload` (`:5105`). |
| **Needs execution** | read; the claim. |

---

### RSA73-C3 - Automation.md did not say that runs wait for a running route

| | |
|---|---|
| **Disposition** | Fixed - `e06443b3`: a paragraph in "Nothing is sent at all". |
| **Grade** | C - documentation. |
| **Where** | `Automation.md` (`:636-646`). |
| **Needs execution** | read. |

---

### RSA73-D1 - No refusal leaves a control greyed; runs already under way are not stopped by a route; the parity case; RSA72-C2's re-check before anything is stopped; the rename complete; the key in 8 bundles

| | |
|---|---|
| **Disposition** | Checked - clean (but C1, C2). |
| **Grade** | D |
| **Where** | Autonomy's own dispatch, a timetable's next leg and Return Home's staged run never ask the gate; reversals during a run are ignored and never held; no `routeFinished(String)` left. |
| **Needs execution** | read. |
