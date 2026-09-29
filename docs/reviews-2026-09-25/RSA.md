# RSA - Release validation, autonomy and race conditions, everything since v2.7.4: a train length cleared during a non-atomic run hands back the track under the train, and a retired railway's thread stops a train the next railway is driving (TrainControl 3.0.0)

**Status:** open

**Prefix:** RSA

**Reviewed:** branch `autonomy-diagram-r0` at `375b542a`, read and run from `git archive 375b542a` unpacked into `validate30/rsa/a375/` (reading, and the baseline class) and a copy of it, `validate30/rsa/p1/`, with one scratch probe class added (`test/regression/rsaProbes.java`, not in the battery); `cs2_sample_layout` excluded and an empty folder of that name made for the runner; `LocDB.data` and `UIState.data` beside each root are copies from TC30.  TC30's working tree and the main checkout's were never read; git was used only as `git -C tc-30 log / show / grep / archive`.  Scope `c469b81c..375b542a` (tag `v2_7_4f`, 2026-07-25, to round 15's records): 2,029 commits, 134 files under `src/`.  2026-09-29.

## Method

**Read whole or nearly so (the ground covered in depth):** the brief; `docs/reviews/README.md`; `validate30/RLV13.md` as the model and the parts of RLV6 and RLV7 that describe the reload during a run; behaviour.md sections 1, 1a, 3 (to *Direction commands*), 5c (to the grey), 5d, 6, 6a, 7a and 8.  **The running railway, `automation/Layout.java`:** its fields and constructor; `runLocomotives`, `runLocomotive`, `stopLocomotives`, `pickPath`; `isPathClear` whole (the occupancy, lock-edge, same-rail, covered-track, length, own-tail and held-back arms); `configureAndLockPath`, `validatePathActuation`, `handleMisconfiguredPath`, `unlockPath`; `executePath` and `executePathInternal` whole (the fences, the speed writes, the waits, the early release, the arrival, the turn and the re-stand); `executeTimetable(Internal)`, `waitForS88Reached`, `updatePendingS88`; `turnsOnArrival`, `shouldReverseAt`, `standOnTheCopyItDidNotTurnOn`; `getLastPointsReached`, `keptApart`, `movedOn`, `lastKnownPoint`; the head of `walkStandingTrains`; `moveLocomotive`, `clearLocomotiveExcept`, `clearBlockExcept`, `locomotiveInBlock`; `refreshProtectingSignal`, `refreshOneSignal`, `throwEntryGuard`, `protectsAnOccupiedSquare`; `retireEveryLayout`, `makeCurrent`, `isCurrentLayout`, `fromJSON`'s first lines; `planReturnToHome`, `loadReturnToHomeTimetable`, `setTimetable`; `tailHasProvablyPassed`, `pathIsUnmeasured`, `unmeasuredTrackThatCouldBeReleased`, `trainsWithNoLength`, `setAtomicRoutes`; `getActiveAccs`.  `Point`'s occupancy (`setLocomotive`, `reserve`, `assign`, `getBlockLocomotive`, `isSamePlaceAs`) and `Edge`'s (`isOccupied`, `isLockHeld`, `isRunOver`, `setOccupied`, `setUnoccupied`, `release`, `isMeasured`).  `Locomotive`'s waits and its speed and power bookkeeping; `Feedback._setState`.  **The network and the routes:** `MarklinControlStation.receiveMessage` whole (the duplicate window, the three executors), `clearAutoLayout`, `parseAuto`, `getAutoLayout(IfLoaded)`, `syncWithCS2`, `isAutonomyRunning`, `stop`/`go`/`stopAllLocs`, `setSentMessageObserver`; `MarklinAccessory.setSwitched` and its echo; `MarklinRoute.heldReason` and `execRoute`'s command loop.  **The doors and their threads (`TrainControlUI`):** `whyNoTrainMayBeSent`, `refusedToSendATrain`, `anEditorIsOpenOrOnItsWay`, `refusedForAnEditorOnItsWay`, `sendATrainByHand`, `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack`, `openLayoutEditor` (round 15's `editorOnItsWay` in every branch), `startAutonomyActionPerformed`, `executeTimetableActionPerformed`, `requestReturnToHome`, `gracefulStopActionPerformed`, `requestStartAutonomy`, `isAutonomyBusy`, `prepareAutonomyReload`, `unloadAutonomy`, `rebuildRunningLayoutFromSetup`, `whereTheTrainsAre`, `putTheTrainsBack`, `carryTheTrainsAcross`, `keepWhereTheTrainsStand`, `captureRunningLayout`, `saveState`'s capture, `WindowClosed`, `followDirectionChanges`, `followTheTurn`, `reconcileFacingWhenIdle`, `writeTheTurns`, `updateVisiblePoints`, `updateStationLabels`, `refreshCoveredTrack`, `repaintTimetable`, the three `repaintAutoLocList`s, `promptTrainLength`, `applyTrainLength`, `checkAutoLayoutLatency`, the Enter key's all-stop; `AutonomyViewerPanel.load` and `loadPrepared`; `LayoutRightclickAutonomyMenu.showFor`, `gatherPathOptions`, `destinationItem`, `removeLocomotiveHere`; `AutoLocomotiveStatus`'s double-click; `LocomotiveMenuItems.trainLength` and both menus that add it; `GraphLocAssign.commitChanges`; `AutonomySession.moveOntoFacingCopy` and `faceTheWayItCameIn`; `AutonomyRefreshCallback` and `DiagramMonitor.attach`; the head of `HomeStaging.snapshot`.

**Read lightly:** `HomeStaging` beyond its snapshot (the plan is re-checked leg by leg by `isPathClear` under the monitor at run time, so I read where the plan meets the runtime rather than the search); `TailCrossedPrompt` (round 15's `isStagingInProgress` lines only - fourteen rounds went over it); `AutonomyCompanionStore` (its write primitives, not its logic); `open-questions.md` (its outline).  **Not reached:** the graph's construction - `AutonomyBuilder`, `GraphReducer`, `TileGraph`, `TilePorts`, `AutonomyChecks`, `TileAnnotation`, `StationIndex` - and so whether the lock edges and places the runtime trusts are derived right; the length rules' bodies (`whyTooLongForThisRoute`, `whyABerthCannotHoldIt`, `whyItWouldMeetItsOwnTail`, `walkOneTail`); `AutonomyEditorPanel`, `LayoutEditor` beyond its opening; import, export, revert and the configurations' files; the timetable UI, the route editor, `CS2File`, the multi-unit sweep; the bundles and the docs beyond the sections named.  Every finding was searched for in a copy of `triage.db` (all 4,851 rows, titles and dispositions) before it was written.

**Runs.**  Every JVM through the archive's own `docs/tools/one.sh`, `TC_SCRATCH` a Windows-form folder per job under `rsa/scratch`, started by a queue (`rsa/runjob.sh`) only once no `java.exe`, `javaw.exe` or `javac.exe` had been seen and no battery lock existed for two checks 20 s apart, deciding any retry from that attempt's own log (none was needed).  The coordinator's battery held the machine until about 04:06; everything below ran 04:06-04:11, one job at a time.

- **Baseline:** `core.testATrainIsDispatchedOnce` on the archive's code, 16 run, 0 failures (it holds the dispatch-once claims and the tail answer during Return Home's planning).
- **Probes** (`regression.rsaProbes`, simulated network, hand-built railways, feedback set by the probe; each writes what it saw to `rsa/out/`): `rsaProbeARetiredRailwaysThreadStopsTheTrainTheNewRailwayRuns` (RSA-B1), `rsaProbeTheReloadsStopIsOverriddenByTheDrivingThreads` (RSA-C1), `rsaProbeATrainLengthClearedMidRunHandsBackTheTrackUnderIt` (RSA-A1, with its control), `rsaProbeTheReStandLeavesTheSquareEmptyForAMoment` (RSA-C3).  The first two runs of RSA-A1's probe were my fixture's fault - the dispatch was refused, once because a 4-unit train did not fit 3 units of track, once because a train I had stood at the head of the crossing road lay across the shared metal - and their results are kept (`P3-first-attempt-...`, `P3-second-attempt-...`) and not counted.

**Nothing written** outside `validate30/rsa/` and this file.  The runner takes the shared lock under `%TEMP%` and a preference node of its own, and removes both; two nodes under `HKCU\Software\JavaSoft\Prefs\traincontrol-test-runs` exist (`battery-680`, `one-620`) - the same two RLV13 found; not mine, not touched.  No git state changed; no commit.  The real `LocDB.data` and `UIState.data` - the main checkout's and `C:/Users/adamo`'s - were copied and hashed before the first JVM and compared byte for byte after the last: unchanged (`3f54de3a...`, `3604bf5f...`, `6ef56939...`, `9e32894e...`).  Nothing of mine is running: every queue, monitor and JVM of mine has ended, and no `java.exe`, `javaw.exe` or `javac.exe` is left.

**Counts:** 1 A, 1 B, 7 C, 9 D.

**The track a train is given is kept from every other train by one monitor and balanced counts** (RSA-D1, D2): the check and the claim are one monitor, every release gives back exactly what was taken, the one release outside that monitor only lowers counts, and no lock order closes a cycle.  **The two defects are both about a train being moved or stopped by something that decided before the railway changed.**  A train's length is written onto the live locomotive during a run, and a non-atomic run reads it at every sensor: set to 0 - the menu's "not set", offered while trains run - the track the train is lying across is handed back under it, and another train's road across the same switch reads clear (RSA-A1, measured).  The gate that exists for exactly this state is asked only where a run starts.  And the reload's and Unload's Yes stop the trains between sensors, which leaves each train's path thread on the retired railway waiting on a sensor its train will not reach; when anything next occupies that sensor, the thread commands its train to a stand - by then, perhaps, a train the next railway is driving (RSA-B1, measured).  **New since v2.7.4:** A1, B1, C1, C2, C3, C5 (the field's readers), C6.  **Older:** C4, C7.

---

### RSA-A1 - A running train's length can be cleared from the locomotive's own menu during a run, and a non-atomic railway then hands back the track the train is lying across at every sensor it passes: the atomic-routes gate is asked only where a run starts

| | |
|---|---|
| **Disposition** | Fixed - the train-length door refuses while trains run, at the click and again at OK (refuseWhileAutonomyRunning, which the other locomotive edits ask; Adam, MT-141: "Never allow any modifications to a running layout.  This includes locomotive database"), and a journey reads its train's length once, at dispatch, so no length written while it runs can shorten what it holds.  claims 02bbd438 (red first), fix e17e7062; mutations MA1 red, MA2 red, MA3 red.  GraphLocAssign.commitChanges (GST-B2) is reached only through the Edit Locomotive dialog's two doors, both refused while trains run (the diagram's at the click and at OK, RLV12-C3; the autonomy editor's cannot be open during a run), and no journey reads the length after dispatch in any case; GST-B2 stays open for its own reason. |
| **Grade** | A.  Two trains on one track.  Adam's railway runs `"atomicRoutes": false` (behaviour.md 5d), so an edge behind a moving train is given back - `setUnoccupied`, its lock edges with it, its start square emptied - as soon as `tailHasProvablyPassed(false, behind, trainLength)` holds, and with the length at 0 that is `behind >= 0`: true the moment the head has finished the edge, with the whole train still on it.  behaviour.md 5d names this state and rules *"the railway is not allowed to run non-atomic while either state holds"*, asked *"at every door that could start a train or load a setting"*.  But inside a run nothing passes a door: autonomy's own threads choose and dispatch every later journey (`runLocomotive` -> `pickPath` -> `executePath`), a timetable's and Return Home's entry threads likewise, and the gate is not asked between them.  And the length can be changed during the run: **Manage Locomotive > Autonomy train length** on a locomotive button's right-click menu and on the locomotive database's is offered whenever autonomy is loaded, and `promptTrainLength` -> `applyTrainLength` writes `setTrainLength(units)` straight onto the live locomotive, 0 ("not set", the first entry of the list) included, with no running check and no gate.  From that train's next sensor every edge it finishes is handed back under it; its own tail walk skips it too (a length of 0 is not walked), so nothing claims the track; and another train's road over the switch or crossing it is standing across reads clear - in autonomy, the next journey chosen.  Every later journey it is given in the run does the same.  A length lowered but not cleared shortens what the run holds from that sensor on in the same way.  Behaviour.md and the code disagree: the rule says the railway may not run non-atomic while a train has no length; the code lets a run continue non-atomic after one is cleared.  **New since v2.7.4** (the early release, FR-047's menu item and the gate are all 3.0 work).  VD16-B2 found the train half of the gate had a door that did not ask - *"a train length can be set to 0"* - and put the question at Start; GS-B1 added the other run doors; neither reached a length changed after the run began.  The same write reaches a train through `GraphLocAssign.commitChanges` past `moveLocomotive`'s refusal (GST-B2, open). |
| **Names** | VD16-B2, GS-B1, VD14-B1, VD13-B1, GUI-A1, SVN-C17, GST-B2, FR-047, FR-046, MT-141 |
| **Where** | `LocomotiveMenuItems.java:128-145` (`trainLength`: offered while `isAutonomyLoaded()`); `RightClickMenuListener.java:231`, `RightClickSelectorMenu.java:67`; `TrainControlUI.java:28359-28389` (`promptTrainLength`: 0 to 40, no refusal), `:28417-28432` (`applyTrainLength`: `setTrainLength`, then two refreshes, nothing else); `Layout.java:9105-9237` (the early release: `tailHasProvablyPassed` at `:9163`, `setUnoccupied` and `getStart().setLocomotive(null)` at `:9215-9216`), `:5232-5253` (`tailHasProvablyPassed`: `behind >= length`, null read as 0), `:7635` (the walk passes over a train of length 0), `:4825-4907` (autonomy's thread dispatches its next journey through no door); `TrainControlUI.java:6358-6397` (the gate, asked by the dispatch doors and the load only); behaviour.md 1655-1666 |
| **Needs execution** | done - probe `rsaProbeATrainLengthClearedMidRunHandsBackTheTrackUnderIt`: a non-atomic railway of three measured edges (1, 1 and 4 units), a 4-unit train sent along it by `executePath`, and a second road whose rail is a lock partner of the first edge - the other leg of its switch.  The control leaves the length at 4; the case sets it to 0 after the first sensor, as `applyTrainLength` does. |

| | first edge held (`isLockHeld`) | its start square holds | the crossing road clear for another train (`isPathClear`) |
|---|---|---|---|
| control, after sensor 1 | true | the train | false |
| control, after sensor 2 (head one unit past the first edge, a 4-unit train over it) | **true** | the train | **false** |
| length set to 0, after sensor 1 | true | the train | false |
| length set to 0, after sensor 2 | **false** | **nobody** | **true** |

**Would a test have caught it?**  No.  `ui.testNonAtomicRoutesNeedTheirLengths.testEveryDispatchDoorAsksTheGate` is a list of the doors that start a run, and `testALoadedSetupCannotRunNonAtomicWithATrainThatHasNoLength` is the load door; no claim changes a length after a run has begun.  The claim that would: with a non-atomic railway running and a train under way over measured edges, set its length to 0 through `applyTrainLength`, fire its next sensor, and assert the edge it has just finished is still held (or that the railway is now atomic, or the write refused).  The probe is that claim with its control.

**Direction:** refuse the length while autonomy is busy, as the other locomotive edits are refused (MT-141: *"Never allow any modifications to a running layout.  This includes locomotive database"*), or have `applyTrainLength` ask the gate, which GUI-A1 made safe to switch mid-run; and, either way, let a run read its train's length once, at dispatch, so no edit made while it runs can shorten what it holds.  The same for `GraphLocAssign.commitChanges` (GST-B2).

---

### RSA-B1 - A retired railway's path thread stops its train whenever the sensor it was waiting for is next occupied: after the reload's or Unload's Yes, a train the next railway is driving can be stopped dead mid-route, its route held and the run unable to finish

| | |
|---|---|
| **Disposition** | Fixed - a retirement (a new railway, a load made current, Unload's retirement) wakes every sensor wait (Locomotive.wakeEveryWait), and a journey's waits give up once their railway is retired, so its thread reaches the fence and ends then - not when the sensor is next occupied, perhaps under a train the next railway drives.  claims 02bbd438 (red first), fix e17e7062; mutations MB1 red, MB2 red, MB3 red.  The claim allows two seconds, under the wait's five-second poll, so it is the wake that it proves. |
| **Grade** | B.  The reload's and Unload's Yes (`prepareAutonomyReload`) clear `running` and command every train under way to 0 - between two sensors - and then the load or Unload retires the railway (RLV8-A1).  Each of those trains' path threads is inside `Locomotive.waitForOccupiedFeedback`, untimed and level-triggered, for a sensor its stopped train will not reach, and nothing wakes it or ends it.  It stays there until something - any train - occupies that sensor.  Then it runs the rest of its loop step: every arm is fenced by `isCurrentLayout()` except the last, which is `loc.setSpeed(0)`.  That is right when it is the same train coasting on to the sensor a moment after the Yes, which is what RLV8-A1 was written for; it is wrong minutes later, when the operator has loaded the configuration, pressed Start, and the next railway is driving that locomotive somewhere else.  Another train crossing the old sensor then stops it wherever it is: the new railway's thread waits on a sensor the standing train never reaches, the route stays locked, Graceful Stop never finishes, setup edits stay refused, and nothing is logged outside debug (`debugLocomotivePathExecutionHaltedFromPriorLayoutVersion`).  Where the train itself reaches the old sensor on its new route, both threads wake on the one event and the order decides - a stutter, or the same stop.  Not A: a train stopping is the safe direction, and its route stays reserved.  The way out is to drive it by hand onto its next sensor, or Unload again.  **New since v2.7.4**: 2.7.4's reload stopped nothing, so every thread ran its train on to its next sensor and ended there; the stop at the Yes came with `5fd1a54f` (2026-07-26), the retirement at Unload with RLV8-A1. |
| **Names** | RLV8-A1, RLV7-B1, RLV6-B1, GST-B1, TR-D9, GS-C1 |
| **Where** | `TrainControlUI.java:4373-4407` (`prepareAutonomyReload`: `stopLocomotives()` and `setSpeed(0)` for each train under way), `:9212-9244` (`unloadAutonomy` asks it, then `clearAutoLayout`); `AutonomyViewerPanel.java:769-777`, `:956` (the load asks it, then `parseAuto`); `MarklinControlStation.java:1055-1078`, `:1112-1130` (retirement); `Locomotive.java:777-857` (the wait: untimed, ended only by the sensor); `Layout.java:8980-9011` (the intermediate wait), `:9249-9295` (the destination wait, then `setSpeed(0)`), `:9298-9318` (the fence's `loc.setSpeed(0)`) |
| **Needs execution** | done - probe `rsaProbeARetiredRailwaysThreadStopsTheTrainTheNewRailwayRuns` |

| step | train's speed | new railway has it under way | old thread | new thread |
|---|---|---|---|---|
| sent GA -> GB -> GC on the old railway; its thread waits on GB's sensor | 30 | - | waiting | - |
| the Yes (`stopLocomotives`, `setSpeed(0)`), a new railway made current, the train on GA as the fold leaves one that tripped nothing | 0 | - | waiting | - |
| the new railway sends it GA -> GD | 15 (pre-arrival) | yes | waiting | waiting on GD's sensor |
| **another train occupies GB's sensor** | **0** | **yes** | ended (`true`) | **still waiting on GD's sensor** |

**Would a test have caught it?**  No.  `regression.testTheImportDoorReadsAnOldFile.testAnUnloadRetiresTheRailwayItUnloads` asserts the retirement, and RLV8's and RLV9's probes asserted that the awaited sensor, fired after Unload, leaves the train stopped - the behaviour that is right at once and wrong later.  No claim re-dispatches the train on a new railway before the old sensor fires.  The claim that would: dispatch, the Yes, a new railway, dispatch the same train on it, occupy the old sensor, and assert the train keeps the speed the new railway gave it (the probe, with its final row as the failure).

**Direction:** end a retired railway's path threads at the retirement rather than at their next sensor - the Yes has already stopped their trains - for instance a wait that also returns when its railway is retired (a notify on `Locomotive.monitor` at retirement, and the dispatch loop's waits asking `isCurrentLayout()`), after which the thread returns without commanding anything.

---

### RSA-C1 - The reload's Yes ("stops running locomotives") is overridden by the threads it stopped and the one it missed: a train still claiming its route sets off after it, a train whose sensor comes before the new railway is built is given its speed back, and a load then refused leaves both kinds as they are

| | |
|---|---|
| **Disposition** | Fixed - the Yes is the railway's own stop (Layout.stopEveryTrainWhereItIs): it counts a stop first, then stops autonomy choosing and every train under way.  A journey reads the count when it is dispatched - before its claim - and every speed it writes asks it again, before and after (drive): a claim completed after the Yes is given back unrun and the train stands where it was; the departure (GS-C1's unfenced write), the next leg, the pre-arrival speed and a turn's resume are not written; and autonomy's thread sends nothing it was choosing when the Yes came (it asks whether the railway still runs after the choice).  A load refused after the Yes leaves every train stopped - RLV6's state - and none given its speed back.  claims 02bbd438 (red first), fix e17e7062; mutations MC1a red, MC1d red, MC1b red, MC1p red, MC1r red, MC1c red, MC1u red.  drive's second look after its write - a Yes landing between the check and the write - is by reading: no fixture lands a stop in that instant. |
| **Grade** | C.  `prepareAutonomyReload` stops the trains in `activeLocomotives`.  A train still inside `configureAndLockPath` - seconds, on a path with accessories - is not in that map: when the lock returns, `executePathInternal` registers it and issues its departure speed, which no fence covers (GS-C1), and it runs to its first sensor.  A train that was stopped but reaches its awaited sensor before the retirement - braking onto it, or because the load is slow (the fold's `toJSON` waits for any `configureAndLockPath` holding the monitor) - has its thread run the next iteration on a railway that is still current, and that iteration writes the leg's speed back (`setSpeed(calculatedSpeed)` or the pre-arrival speed).  Where the load then succeeds, each stops at its next sensor on the fence, one section beyond where the fold wrote it - RLV7-C1's accepted gap, widened by a section.  Where the load is refused (`loadPrepared`: the configuration chosen has blocking problems, `revert`) nothing is retired: the trains that were given their speed back finish their journeys, as a graceful stop would, and the ones that stayed stopped stand between sensors with their paths held and the railway reading busy - RLV6's "stuck" state, reached through a load the operator was told had stopped everything.  **New since v2.7.4** (the stop at the Yes, `5fd1a54f`). |
| **Names** | GS-C1, GST-B1, RLV6-B1, RLV7-B1, RLV7-C1 |
| **Where** | `TrainControlUI.java:4396-4401`; `Layout.java:8869-8928` (the lock, then `loc.setSpeed(speed)` with no fence), `:8963-8977` (the speed written back at the next point), `:9249-9258` (the pre-arrival speed); `AutonomyViewerPanel.java:899-914` (the fold, `toJSON` on the monitor), `:923-946` (a refused load, `revert`) |
| **Needs execution** | done - probe `rsaProbeTheReloadsStopIsOverriddenByTheDrivingThreads`: (a) Yes answered while the train was claiming a four-edge route: speed 0, then 30 once the lock returned, registered, and stopped at its first sensor only after the retirement; (b) Yes answered with a train under way (speed 0), then its awaited sensor set before any retirement: speed 15, the next leg's pre-arrival speed.  The refused-load half by reading. |

**Direction:** make the Yes the railway's own: have `executePathInternal` ask, before each speed write, whether the run it belongs to has been told to stop now (a flag the Yes sets on the railway, distinct from the graceful `running`), and give a claim that completes after it back unrun - the same repair RSA-B1 needs, one step earlier.

---

### RSA-C2 - The Auto tab's double-click asks only whether autonomy's flag is up: a train is sent by hand while Return Home is planning, or while another hand send runs, where the diagram's destinations offer nothing then

| | |
|---|---|
| **Disposition** | Fixed - the Auto tab's double-click asks whether a staging flow owns the railway as well as whether autonomy runs (Layout.isStagingInProgress), so no train is sent by hand while Return Home plans.  A second hand send while another runs stays allowed from the Auto tab, where the diagram offers none: Adam's ruling on OB-164 accepted that difference ("The user can rely on full autonomy or the panels to send trains more clearly").  claims 02bbd438 (red first), fix e17e7062; mutation MC2 red. |
| **Grade** | C.  The two hand doors send through one gate (`sendATrainByHand`), and the gate asks the editor, the setup and the power - not whether autonomy is busy, which each door asks for itself.  The diagram's door gathers no paths while `isAutonomyBusy()` - a staging flow planning, or any train under way, a hand send included.  The Auto tab's double-click asks `layout.isAutoRunning()`, the flag alone, which is false while Return Home plans (up to fifteen seconds on a full railway, with nothing yet running), during another hand send, and during a graceful stop's coast-down; its list stays on screen through all three.  So Return Home's planner snapshots the railway while a train it plans for is being dispatched: where that journey is still running when the plan is made, `loadReturnToHomeTimetable` refuses (*"locomotives running"*) and the press does nothing; where it has ended, the plan is for positions the railway no longer has, and its legs are refused at run time until the run gives up (*"Return Home stopped"*).  No two trains meet - every leg is checked under the monitor - but a press of Return Home is spent, and the two hand doors answer "may I send this train now" differently: the ruling that asked for one gate (Adam, 2026-09-29) is kept for the three questions in it and not for this one.  **New since v2.7.4** (Return Home, the diagram door and the gate are 3.0). |
| **Names** | RLV13-C3, RLV12-B1, OB-192, UXR-B7 |
| **Where** | `AutoLocomotiveStatus.java:1075` (`!layout.isAutoRunning()`); `LayoutRightclickAutonomyMenu.java:246` (`ui.isAutonomyBusy()`); `TrainControlUI.java:6504-6516` (the gate: no busy question), `:24665-24669` (`stagingFlowActive`, `setStagingInProgress`), `:24868-24878` (`isAutonomyBusy`); `Layout.java:11982-11993` (`loadReturnToHomeTimetable` refuses a railway running after the plan); `HomeStaging.java:219-248` (the snapshot reads the live points) |
| **Needs execution** | no - by reading |

**Direction:** ask `isAutonomyBusy()` at the Auto tab's click, as the diagram's gather does - or, if a hand send while another runs is wanted, put that answer in the gate so both doors give it.

---

### RSA-C3 - The re-stand after a declined turn moves the train off the turning copy and onto the plain one outside the railway's monitor: between the two the square is empty and the train is nowhere, and a platform's exit-guard signal is commanded green and red again over the standing train

| | |
|---|---|
| **Disposition** | Fixed - the re-stand reserves the plain copy, carries the side and the road across, and only then clears the turning copy, so the square always holds the train and its tail and the exit guard is never commanded GREEN over it (no command at all: the aspect never changes).  Not under the railway's monitor: with no empty moment there is nothing for a claim on another thread to see.  claims 02bbd438 (red first), fix e17e7062; mutation MC3 red. |
| **Grade** | C.  A hand send answered "keep the direction" that ends on a may-turn square's turning copy is re-stood on the plain copy after its path is unlocked (`standOnTheCopyItDidNotTurnOn`, PRW-A1, placed there by PRV-B2), inside `synchronized (this.activeLocomotives)` but not the railway's monitor, by `Point.setLocomotive`: which first takes the train off every other copy, clearing the side and road it came in by, then places it, and only then are the side and road written back.  In between, a lock taken on another thread - `configureAndLockPath` holds the railway's monitor, which this does not take - sees the square with no train on it and no tail behind it, the path having just been released.  The square's own sensor still refuses a route that ends on or passes through it (`isPathClear` asks every edge end's sensor), so what the window exposes is the tail: track behind the square it shares metal with.  The window is short, except that each change of occupant refreshes the platform's exit-guard signal while a train runs - so the first half commands the signal GREEN (nobody claims the platform), the second RED, and the first command goes out through `MarklinAccessory.setSwitched`, which calls the window's synchronized `repaintSwitch` and can wait on the event thread.  `AutonomySession.moveOntoFacingCopy` - the idle drain, the direction follow and the Facing menu - clears and places the same way, at rest.  Needs two hand sends converging, or a hand send and a run, within that window: a C.  **New since v2.7.4.** |
| **Names** | PRW-A1, PRV-B2, TDR-B1, REV9-B1, OB-166 |
| **Where** | `Layout.java:9527-9541` (the re-stand inside `activeLocomotives` only), `:3779-3849`; `Point.java:541-592` (`setLocomotive`: sweep, assign, refresh, clear the side and road); `Layout.java:10323-10439` (`refreshProtectingSignal`, `refreshOneSignal`); `AutonomySession.java:2137-2150` |
| **Needs execution** | done - probe `rsaProbeTheReStandLeavesTheSquareEmptyForAMoment`: a train sent to the turning copy MT of a two-copy square carrying one exit-guard signal, answered keep; the signal command sent during the re-stand held by the model's sent-message observer.  While held: MT holds nobody, MP holds nobody, the train is not under way and `getLocomotiveLocation` answers null.  Signal commands in order: RED (at the dispatch), **GREEN, RED** (at the arrival, with the train standing on the platform).  Afterwards MP holds the train. |

**Direction:** make the re-stand one step under the railway's monitor (a package-level `moveOntoCopy` that assigns the new copy before clearing the old, and carries the side and road across in the same step), and refresh the signal once, after the move.

---

### RSA-C4 - A route's emergency stop is skipped when the model believes the power is already off, and that belief is only the last echo heard

| | |
|---|---|
| **Disposition** | Fixed - a route's emergency stop is sent whatever the power flag says; the flag chooses only the log line and whether the notice is shown.  claims 02bbd438 (red first), fix e17e7062; mutation MC4 red. |
| **Grade** | C.  `execRoute` sends the stop only `if (this.network.getPowerState())`, and otherwise logs *"power already off"*.  `getPowerState()` is the last GO or STOP echo the Central Station sent - a UDP broadcast - so where one GO was lost (or the power was switched on by something whose echo has not been processed) the model reads off while the track is live, and a stop route fired by its sensor, with nobody present, does not cut the power.  behaviour.md 7a: *"An emergency stop is obeyed whatever else is true ... never has its stop skipped (SVN-A4)."*  The check exists only to send the stop, and its notice, once; a STOP sent to a station already stopped does nothing.  **Older**: the same branch is in 2.7.4 (`MarklinRoute.java:264-282` at `c469b81c`). |
| **Names** | SVN-A4, RLA5-A1, MT-507, MT-508 |
| **Where** | `MarklinRoute.java:990-1012`; `MarklinControlStation.java:4076-4079` (`getPowerState`), `:2811-2844` (set from the echo) |
| **Needs execution** | no - by reading |

**Direction:** send the stop whatever the flag says, and let the flag decide only whether the notice is shown.

---

### RSA-C5 - `Layout.atomicRoutes` is not volatile: the gate's write on the event thread may not reach a driving thread already running

| | |
|---|---|
| **Disposition** | Fixed - Layout.atomicRoutes is volatile.  GST-C1's list (isValid, speed, direction, trainLength, reversible, Feedback.set) stays open for its own reasons; a journey no longer reads trainLength after dispatch (RSA-A1).  claims 02bbd438 (red first), fix e17e7062; mutation MC5 red. |
| **Grade** | C.  The early release reads `this.atomicRoutes` on the driving thread at every point (`Layout.java:9168`, `:9188`, `:9344`, `:9551`), and the atomic-routes gate writes it from the event thread through `setAtomicRoutes` - while trains run, when a hand send's gate finds a train with no length (GUI-A1's case).  A thread started after the write sees it (`Thread.start` orders them); one already driving has no guarantee, and would go on handing edges back under a train the gate has just made unsafe.  On HotSpot today the field is re-read across the calls in that loop, which is why it has not been seen.  The same shape as GST-C1's list (`isValid`, `Locomotive.speed`, `trainLength`), which did not name this field; `trainLength`, which RSA-A1 turns on, is on it.  **New since v2.7.4** (the field's readers are the 3.0 early release). |
| **Names** | GST-C1, GUI-A1, TR-A19 |
| **Where** | `Layout.java:685`, `:11840-11843` |
| **Needs execution** | no - by reading |

**Direction:** `volatile`, as the other flags a driving thread reads are.

---

### RSA-C6 - The diagram's destination item dispatches on the railway current at the click with a path found on the railway current at the gather: were the railway replaced between the two, the new railway would lock the old railway's track and drive the train over rails it does not hold

| | |
|---|---|
| **Disposition** | Fixed - the gathered paths carry the railway they were found on (PathOptions.railway), and the destination sends on it, as the Auto tab sends on the railway its list was found on.  claims 02bbd438 (red first), fix e17e7062; mutation MC6 red. |
| **Grade** | C.  `gatherPathOptions` asks `getAutoLayoutIfLoaded()` once and searches that railway; `destinationItem` hands `sendATrainByHand` `ui.getModel().getAutoLayout()`, read again when the item is clicked.  `executePath` on a railway checks `isCurrentLayout()` of itself, and `path.get(0).getStart()` is the old railway's Point, which still holds the train - so with two railways the dispatch would be admitted, `configureAndLockPath` would lock and configure the old railway's edges under the new one's monitor, the train would be registered as under way on the new one, and the new one's edges would stay free for every other train.  Nothing replaces the railway while this popup is open today: every rebuild is a gesture, or is posted within the event after one, and a popup closes when anything else is clicked.  So it is a trap for the next door that rebuilds asynchronously, not a defect a user can meet.  The Auto tab's door hands over the railway its list was found on (`this.layout`), and a retired one is refused - the two doors differ again.  **New since v2.7.4** (D3-A1 moved the gather to a worker). |
| **Names** | D3-A1, OB-192, RLV12-C5 |
| **Where** | `LayoutRightclickAutonomyMenu.java:241-258`, `:1417-1430`; `AutoLocomotiveStatus.java:1089`; `Layout.java:8773-8776`, `:8818-8824` |
| **Needs execution** | no - by reading |

**Direction:** carry the gathered railway in `PathOptions` and dispatch on it, as the Auto tab does; a railway replaced since is then refused by its own fence.

---

### RSA-C7 - Closing TrainControl while trains run asks only about saving, and Yes exits with the trains still under way and nothing left to stop them

| | |
|---|---|
| **Disposition** | Open - Adam's decision - closing TrainControl while trains run asks only about saving, and Yes exits with the trains under way.  Two answers: (a) say so in the question - the trains keep moving and nothing stops them; (b) on Yes, stop every train where it is, as the reload's Yes does (RSA-C1), and then exit.  Recommendation: (b). |
| **Grade** | C.  `WindowClosed` with autonomy running presses Graceful Stop - which clears `running` and lets every train under way finish its journey - and then asks *"Autonomy logic is still running.  State will not be auto-saved unless all trains are gracefully stopped.  Are you sure you want to quit?"*.  Yes goes straight on to `saveState` and `System.exit(0)`: every path thread dies with its train at speed between sensors, and nothing sends the stop at its destination, so each runs on until the operator cuts the power.  The code knows it - *"so that Escape and the close box do not shut the application down with trains at speed"* - and the question does not say it: it is about the save.  Adam's ruling of 28 September (*"closing TC should keep no edit, correct"*) is about the setup, and the code keeps it.  Graded C because the operator has confirmed a quit and the trains are under their own locked routes; the one thing missing is the sentence, or the stop.  **Older**: the same handler and message are in 2.7.4. |
| **Names** | UR-1, GST-C7, OB-070 |
| **Where** | `TrainControlUI.java:20141-20163`, `:20165-20200`; `messages.properties:423` (`autolayout.ui.confirmExitAutonomyRunning`) |
| **Needs execution** | no - by reading |

**Direction:** say that the trains are still moving and will not be stopped, or, on Yes, stop them - `stopAllLocs()`, or wait for the graceful stop to finish - before exiting; Adam to choose.

---

### RSA-D1 - Claiming and releasing track: the check and the claim in one monitor, every release matched to a claim, and every failure path giving back exactly what it took

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA-A1 (what the release is told about the train's length) and RSA-C3. |
| **Grade** | D |
| **Names** | RC-A9, RC-A10, AUT-C1, AUT2-C1, ACC-A1, VD10-A1, GUI-A1, OB-164, OB-269, TDD-A1 |
| **Where** | `Layout.java:2674-3274` (`isPathClear`), `:3990-4154` (`configureAndLockPath`), `:4266-4355` (`handleMisconfiguredPath`), `:4392-4535` (`unlockPath`), `:8536-8742` (`executePath` and its failure handler), `:9105-9237` (the early release), `:9527-9560` (the arrival's unlock); `Edge.java:597-779`; `Point.java:541-641` |
| **Needs execution** | done - `core.testATrainIsDispatchedOnce` green on the archive (16); the rest by reading |

`isAlreadyUnderway`, `isPathClear` and the `takingPath` claim are all inside `configureAndLockPath`'s monitor, so two dispatches cannot both pass; every other lock of track is in that monitor too.  Occupancy is a count floored at zero, and each `setOccupied` is matched by exactly one `setUnoccupied` - the early release records what it gave back in `releasedEarly`, and `unlockPath` chooses its road by that record rather than by the setting at the end, so neither a flip of Atomic Routes mid-run nor a second release takes away another train's claim.  The one release outside the railway's monitor, the early one, only lowers counts: a concurrent check can see less occupied than a moment later, never more.  The three failure paths each give back what was taken and only that: a refused lock took nothing; a configuration or actuation failure releases the prefix it took and re-reserves the start; a throw mid-run releases only where `activeLocomotives` says the path was got.  A path the early release could leave held with its end taken by another train needs a release out of path order, which the monotone distance rule cannot produce.  The covered-track check walks every other train once, a running one from its last milestone back along the road it drove, so a train mid-path claims its body behind the head and its locked path ahead.

---

### RSA-D2 - Lock order: no cycle among the railway's monitor, `activeLocomotives`, the edges, the points, the window and the accessories

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | OB-192, DR-B7, D3-B2, IAR-B2, WK3-A1 |
| **Where** | `Layout.java:8589-8717`, `:9527-9560` (`activeLocomotives` then the railway), `:3990-4099` (the railway, then `Edge` and `Point`, then `MarklinAccessory.setSwitched` -> `TrainControlUI.repaintSwitch`); `AutonomyRefreshCallback.java:67`, `DiagramMonitor.java:147` (the only callbacks: they post or set a flag); `TrainControlUI.java:29222-29251`, `:5949-6030` (`updateVisiblePoints` and `updateStationLabels` take no railway monitor); `Edge.java:749-779` |
| **Needs execution** | no - by reading |

The orders taken are `activeLocomotives` -> railway (both ends of `executePath`), railway -> edge -> lock edge, railway -> point, and railway -> accessory -> window (a signal or switch thrown while a path is locked).  A cycle would need someone holding the window and then taking the railway's monitor: the census guards the direct calls, and the window's fifteen synchronized methods reach none indirectly (each either posts its work or asks only lock-free readers) - the covered-track pass, the Auto tab's search and the timetable snapshot all run on workers, and the drain in `reconcileFacingWhenIdle` moves trains through `Point.setLocomotive`, which takes no railway monitor.  Symmetric lock edges cannot deadlock their two releases: while one edge is occupied its partner's count is above zero, so no second train holds the partner.  The callbacks fired inside `activeLocomotives` only post to the event thread or set a flag.

---

### RSA-D3 - Retirement: every load, Unload and deleted setup retires the railway it replaces, nothing else builds a railway during a run, and the fences stop a retired railway's thread from driving

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA-B1 (what the fence does minutes later) and RSA-C1 (the departure write, GS-C1). |
| **Grade** | D |
| **Names** | RLV8-A1, RLV8-C3, RLV12-C5, RLV11-C5, TR-A19, CS3-C4 |
| **Where** | `Layout.java:783-800`, `:1868-1907`, `:8773-8776`, `:8963-9105` (each speed write and wait fenced), `:9298-9318`, `:9448-9470` (the arrival's turn), `:6574`, `:6798` (the timetable's loop and completion wait), `:2053-2075` (`simClearBehind`), `:12345-12347`; `MarklinControlStation.java:1022-1139` |
| **Needs execution** | done - probe `rsaProbeARetiredRailwaysThreadStopsTheTrainTheNewRailwayRuns`, steps 1-2; the rest by reading |

`new Layout` is called in `src` only by `fromJSON` (a load) and by `getAutoLayout` when nothing is loaded, both under `autoLayoutLock`, and `parseAuto` makes the parsed railway current under it (RLV8-C3); Unload retires explicitly.  The counter is volatile and only ticked under `VERSIONS`.  A retired railway's thread commands nothing on its path but the final stop: every speed write in the loop but the departure is behind `isCurrentLayout()`, the arrival's turn asks again after its pause, the timetable neither dispatches nor waits on a retired railway, and a simulated sensor is never cleared by one.  Everything that asks "is there a railway" from a worker asks `getAutoLayoutIfLoaded` once.

---

### RSA-D4 - The network's receive path and the feedback a train waits on: ordered, level-triggered, deduplicated only within a window, and no two sensor squares on the autonomy's pages share an address

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | TR-D9, S14-C7, FV3, DIR-B4, RLV12-C4, MON-C15 |
| **Where** | `MarklinControlStation.java:2642-2881`; `Feedback.java:69-82`; `Locomotive.java:777-857`; `Layout.java:6848-6900`; `TrainControlUI.java:13148-13263`; `test/layouts/live-snapshot/config/gleisbilder/*.cs2`, `.../autonomy/setup.json` (`excludedPages`) |
| **Needs execution** | done - the live snapshot's pages parsed by a script: 116 sensor tiles, 58 addresses; every repeated address repeats across pages, and none between the two pages the setup includes (`1 - Main`, `2 - Bottom`) |

Feedback frames go through one single-thread executor, so a sensor's changes arrive in order; the state is set and every waiter woken under `Locomotive.monitor`, and the wait tests the level, so a notify before the wait is not lost.  The duplicate filter drops an identical frame only within `DUPLICATE_WINDOW_NS`.  A wait's debounce restarts on a flicker (older behaviour).  The pending-sensor record has its own monitor.  The direction echo decides on the message thread and posts `followTheTurn`, which asks `isAutonomyBusy()` again on the event thread (RLV12-C4).  A train's wait can be ended by another train only where two squares share a sensor; on the diagram-built configuration that is only the copies of one square, which the block treats as one place.  Sensors shared by different places - which a hand-written configuration can carry - do not occur on the live snapshot's active pages; where a diagram has them, a train waiting on one can be woken by a train on the other (older behaviour, the reason the s88 is not used as a place key).

---

### RSA-D5 - The single gate, and round 15's editor-on-its-way flag: set before the posted build, cleared on every way out of it, and asked again by the three run doors at their last event-thread moment

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA-C2 (the busy question is not in the gate). |
| **Grade** | D |
| **Names** | RLV13-C2, RLV12-B1, OB-047, MT-135, MT-263 |
| **Where** | `TrainControlUI.java:5305-5460` (`openLayoutEditor`: set at `:5391`, cleared at `:5403`, `:5426`, `:5456`), `:6504-6570`, `:6610-6639`, `:24616`, `:24724-24743`, `:27387`, `:27500`, `:27592`, `:27692-27710` |
| **Needs execution** | no - by reading (round 15's claims are RLV13's disposition) |

The flag is set on the event thread after the busy question and before the posted build; the build clears it when it refuses over a run begun since, in its catch, and otherwise in a task posted after `render()`'s own posted show, so it is down only once the window exists - and `isLayoutEditorOpen` answers from then.  Every path out clears it; a throw that is not an `Exception` would leave it up, which nothing in the build throws.  Start and Return Home ask `refusedForAnEditorOnItsWay` inside the `invokeAndWait` that also runs the atomic-routes gate, and Execute Timetable in its posted body, so an editor asked for after the click is seen before anything is dispatched.

---

### RSA-D6 - The route guard: asked per accessory command, of a railway busy in any tier, over the union of paths running and paths still being claimed, minus what the tail has cleared

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA-C4. |
| **Grade** | D |
| **Names** | AU-A2, RC-A10, MT-247, SVN-B16, WK3-B1, DIR-A3 |
| **Where** | `MarklinRoute.java:585-651`, `:824-989`; `MarklinControlStation.java:3382-3393`; `Layout.java:1006-1096`, `:10477-10498` |
| **Needs execution** | no - by reading |

`isAutonomyRunning` is `isRunning() || isStagingInProgress()`, so a hand send's path and Return Home's planning are guarded as a run's are; `getActiveAccs` reads `takingPath` as well as `activeLocomotives`, so a path still throwing its accessories is protected; an edge leaves the set only when released or when `tailHasProvablyPassed` - the release's own rule - says the tail is past it.  The question is asked immediately before each command goes out.

---

### RSA-D7 - Return Home: planned once, run as a timetable whose every leg the runtime checks again, refused where a run began during the planning, and the timetable handed back on every way out

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA-C2. |
| **Grade** | D |
| **Names** | OB-073, OB-192, RLV9-B1, RLV13-C3, FR-077 |
| **Where** | `TrainControlUI.java:24611-24843`; `Layout.java:6514-6841`, `:11850-12036`, `:12287` |
| **Needs execution** | no - by reading |

The flow raises `stagingFlowActive` and the railway's own `stagingInProgress` before planning on its worker, so the setup doors, the route guard and the tail answer see it; the plan is loaded only where nothing began running meanwhile; its legs run one at a time and each passes `isPathClear` under the monitor, so a stale plan can fail but cannot put two trains together; the borrowed timetable is what a fold writes (`timetableOnLoan`), and the `finally` hands it back and lowers both flags on every path, a throw included.  A load confirmed during planning retires the railway the worker holds, and `executeTimetable` on it dispatches nothing.

---

### RSA-D8 - The setup during a run: the rebuild declines, the folds read one point per train, and closing TrainControl keeps the last state saved at rest

| | |
|---|---|
| **Disposition** | Checked - clean, apart from RSA-C1. |
| **Grade** | D |
| **Names** | OB-183, ACC-B3, RLV7-B1, RLV7-C1, RLV9-A1, RLV10-B1, GST-C7, UR-1 |
| **Where** | `TrainControlUI.java:6682-6767`, `:6877-6990`, `:7018-7192`, `:3100-3143`, `:3187-3233`, `:2407-2535`, `:20110-20201`; `AutonomyViewerPanel.java:769-980`; `Layout.java:1607-1831` |
| **Needs execution** | no - by reading |

`rebuildRunningLayoutFromSetup` builds only while `isAutonomyBusy()` is false and records a declined edit otherwise; every fold refuses a running railway except the three that follow a Yes (a load, Unload, the carry), and those write each train under way once, at the point `getLastPointsReached` keeps it - so no configuration is left with a train in two places.  All of it runs on the event thread, so a fold and a rebuild cannot interleave.  A hand send that slips between a rebuild's busy question and the build finds its railway retired at its first fence and stops (a lurch, not a run).  The exit asks the operator when trains run, stops autonomy gracefully and saves no positions while anything moves - the last state saved at rest is what the next start loads, as Adam agreed on 28 September (what its Yes does to the trains is RSA-C7).

---

### RSA-D9 - The probes' own controls, and the baseline

| | |
|---|---|
| **Disposition** | Checked - clean. |
| **Grade** | D |
| **Names** | - |
| **Where** | `validate30/rsa/p1/test/regression/rsaProbes.java`; `validate30/rsa/out/`; `validate30/rsa/logs/` |
| **Needs execution** | done |

Each probe states its precondition in its output before its result: the train under way on the railway it was sent on, the old railway retired and the new one current (RSA-B1), the claim still taken when the Yes is answered (RSA-C1), the signal's command actually held (RSA-C3), and RSA-A1's control - the same run with the length left at 4 - holding the edge and refusing the crossing road at the same sensor.  RSA-A1's first two runs refused the dispatch for reasons of my fixture and are kept, marked, and not counted.  `core.testATrainIsDispatchedOnce` on the archive: 16 run, 0 failures.  Every job's runner reported `Failures: 0, Skips: 0` and exited 0.
