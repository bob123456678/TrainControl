# RSA72 - Validation of TrainControl 3.0.2, round 2, over RSA71's fixes and a re-read of everything since 3.0.1 (`b2037955..a74e279a` on `dev`): the turn a route held is followed when the route ends, on its own path; it met a run started before the route ended (C1), and a Yes to stop autonomy's trains loaded under a route still running (C2); the changelog missed two lines (C3) and two methods shared a name for two moments (C4) - CONVERGED, every C fixed

**Status:** closed

**Prefix:** RSA72

**Reviewed:** branch `dev` at `a74e279a` (*testAClickOnATrainIsAClickOnItsStation sets Show Trains on and puts it back*): RSA71's fixes (`6919ec4c`, `b8608078`, `a6f895fa`, `82897755`, `09bfe71a`), OB-355's tooltip wrap (`3ee312fe`) and OB-356's revert (`0bf2ae89`), then the 3.0.2 autonomy changes again for races (`a5e787aa..a74e279a`).  Read-only, in a worktree detached at the commit.  2026-10-18.

## Method

**Read whole:** the brief, RSA71's report; `TrainControlUI.followDirectionChanges`, `followTheTurn`, `routeFinished`, `reconcileFacingWhenIdle`, `autonomyLocomotiveRenamed`, `autonomyLocomotiveDeleted`, `refusedWhileSomethingElseRuns`, `prepareAutonomyReload`, `unloadAutonomy`; `MarklinRoute.execRoute`'s end; every user door to a configuration load, unload and export, and the quiet reloads.

**Probe:** `diag.testV302r2Turns` (the live-snapshot sandbox with echoed packets, as `regression.testAThrottleReversalIsFollowedFromItsEcho`; a hand send made by setting `handSendsUnderWay`): a reversal during a route alone (control), then a hand send started before the route ends, the route ending during it (P1) or outliving it (P2).  No Central Station was reached; Adam's own files and his running TrainControl were not touched.

**Counts:** 0 A, 0 B, 4 C, D below.

**This round has CONVERGED: no A and no B.**  Every C is fixed (`4d4bfc5c..e3bc351b`).

---

### RSA72-C1 - A turn held for a route met a run started before the route ended: dropped for good when the route ended during the run, or applied after the run when the route outlived it

| | |
|---|---|
| **Disposition** | Fixed - `cc5fe6cb`, `e3bc351b`, on Adam's choice (2026-10-18: *"prevent starting autonomy etc if a route is still running"*): the one send gate, `TrainControlUI.whyNoTrainMayBeSent`, asked by Start, Execute Timetable, Return Home's Run and a hand send, refuses while any route executes (`aRouteIsExecuting`, taken out of `somethingIsRunning`; `autolayout.ui.errorWaitForARouteToFinish`), so a held turn never meets a run that began under it.  A route an s88 sensor starts during a run holds only the reversals made after the run.  The changelog says runs wait for a route.  Claim `regression.testNoSetupEditWhileARouteRuns.testNoRunStartsWhileARouteRuns`, red first. |
| **Grade** | C - a reversal, then a start, both within one route's run time. |
| **Names** | RSA71-C8, Adam on C8: *"it should reverse, but be careful to avoid duplication in code mechanisms on how this is done"* |
| **Where** | `TrainControlUI.followTheTurn` (`:14956-14968`), `routeFinished(Route)` (`:15001-15010`). |
| **Needs execution** | done - probe: *"P1 all idle: held=[] facing=E decoderForward=true"* (the turn lost); *"P2 route ended: facing=W"* (applied after the run). |

---

### RSA72-C2 - Load and Stop Using Autonomy, answered Yes to the offer to stop autonomy's trains, went ahead under a route still running

| | |
|---|---|
| **Disposition** | Fixed - `9c1487ff`: after the Yes and before the stop, `prepareAutonomyReload` refuses while a route executes.  Claim in `regression.testNoSetupEditWhileARouteRuns`, red first. |
| **Grade** | C - an s88 route fired by an autonomy train, or a route started while the question is up. |
| **Names** | RSA71-C2 |
| **Where** | `TrainControlUI.prepareAutonomyReload` (`:5084-5110`), from `AutonomyViewerPanel.load` and `unloadAutonomy`. |
| **Needs execution** | read; the claim. |

---

### RSA72-C3 - The 3.0.2 changelog had no line for the turn a route's end follows, and did not name Stop Using Autonomy among the doors refused during a route

| | |
|---|---|
| **Disposition** | Fixed - `96d7be1c`. |
| **Grade** | C - wording. |
| **Where** | `Readme.md`, v3.0.2. |
| **Needs execution** | read. |

---

### RSA72-C4 - Two methods named routeFinished meant two moments: the play button's, milliseconds after the press, and the route's real end

| | |
|---|---|
| **Disposition** | Fixed - `4d4bfc5c`: the play button's is `releaseTheRoutePlayButton`, its javadoc pointing to `View.routeFinished`. |
| **Grade** | C - a trap for a later caller. |
| **Where** | `TrainControlUI.routeFinished(String)` (`:33669`), `routeFinished(Route)` (`:15001`). |
| **Needs execution** | read. |

---

### RSA72-D1 - RSA71-C8's fix on its own path: event order, parity, a second route, autonomy running, rename and delete, no double follow, no second mechanism

| | |
|---|---|
| **Disposition** | Checked - clean (but C1). |
| **Grade** | D |
| **Where** | `stopExecuting` before `routeFinished` posts; every follow a `flipFacing` toggle; a second route puts the name back; autonomy's run never posts; the idle reconcile only levels `lastSeenDirection`. |
| **Needs execution** | done - probe control: *"CONTROL after route end: facing=W forward=false"*. |

---

### RSA72-D2 - RSA71-C2 and C3's doors, OB-355's wrap, OB-356's revert, the docs and a re-read of 3.0.2's autonomy changes

| | |
|---|---|
| **Disposition** | Checked - clean (but C2, C3). |
| **Grade** | D |
| **Where** | Every user door to load, unload and export guarded, the quiet reloads not newly refused; both tooltip setters wrap; nothing of OB-356's note left; `PAUSED_AWAY` only with nothing else misplaced. |
| **Needs execution** | read. |
