**Status:** open

**Prefix:** BPV

# Validation of the 2.8.2 backports (master 5f0a75e3..d736c9f9)

Reviewed 2026-09-25. Master tip d736c9f9 in the worktree `scratchpad/tc-master`, base 5f0a75e3 (2.8.1 line). 3.0 sources read from the main checkout on `autonomy-diagram-r0`.

## Method

Read-only, by reading alone - nothing was compiled, no test and no JVM was run, and no git state was changed in either checkout. `cs2_sample_layout/` was not opened in either checkout. For each of the twelve commits I read the master diff (`git show <commit>`) and the 3.0 source commit it names (`git -C <main> show <hash>`, whole commit, not only the ported hunk), compared them hunk by hunk, then read the fixed method as it now stands on master (`git show master:<file>` with line ranges) together with every caller found by `git grep` on master, and the neighbouring code each fix's reasoning depends on (for example that configureAndLockPath really holds the Layout monitor, that isAutoRunning is `running`, that the pending-sensor pair is the only wait/notify on the Layout monitor). For each claim test I read the test body and its fixture and traced by hand whether it goes red on the pre-fix master code for the defect's own reason, whether its fixture could supply the answer, and what files, preferences and global flags it touches. Where 3.0 HEAD matters (does 3.0 still write the page names last; did 3.0 later add an audit exemption; is the member-rename sweep the same) I read HEAD. The bundles were checked byte-wise (`tr -d '\000-\177' | wc -c` on every master and base bundle) and key-wise. The rebase was checked with `git diff -w` and plain `git diff` between the old tip 56c1204d (still in the worktree reflog) and master, and pairwise for all twelve rewritten commits against their originals, including their messages. Line numbers below are master's (`git show master:...`) unless marked 3.0. Every finding that needs execution to confirm says so and carries a verification request; reading finds about half of what running finds, so those requests are the next step, not an afterthought.

Counts: A 1, B 0, C 13, D 16. The one A is pre-existing on both branches and is not introduced by these commits; nothing I found makes the railway less safe than 2.8.1.

## Findings

### BPV-A1 - Renaming a member of a multi-unit that stands on a station still takes the multi-unit off it (pre-existing, both branches)

| | |
|---|---|
| **Disposition** | Fixed on both branches - an edit sweeps only for a train that stands, so a member's rename leaves its head on its station.  Master: claim f0b5dbe1 (red first), fix a373a18a, the placing and loading halves pinned in 78205ebf and 58923311.  3.0: claim 69f1cefe, fix 6033fb45, loader pin 612d9600.  Its gap - a member re-addressed onto a standing train - is RLA-B1. |

**What is wrong.** Fix 2 (a4763d04) makes the sweep skip the locomotive it was asked about, which fixes renaming a train that is standing on a station. But both rename doors call `sanitizeMultiUnits(l)` with the RENAMED locomotive, and when that locomotive is a member of a multi-unit whose head stands on a station, the sweep's first test asks the head `isSimultaneousMultiUnitCompatible(member)`. That answers false for any linked member (`if (this.isLinkedTo(l)) return false`), and for a Central Station multi-unit the address loop answers false the same way. So the head's station is cleared. A rename cannot create a multi-unit conflict - nothing about placement or membership changed - so this eviction is as spurious as the one the port fixed.

**Evidence.** Rename doors: TrainControlUI.java:10336 then 10351, and 14582 then 14593 (`l` is the renamed locomotive, 14568). Sweep: Layout.java:3611-3641, skip at 3627, first test at 3631. Compatibility: MarklinLocomotive.java:1013 (linked member) and 1030-1035 (CS multi-unit members by address). 3.0 has the identical sweep (3.0 HEAD Layout.java:9227-9267), and 66c96736 covered only the self case, so this is not a porting omission - it is a sibling of the same door that neither branch fixed.

**Consequence on the railway.** The same as the defect fix 2 was written for, which 3.0 filed critical (MT-149): the multi-unit disappears from its platform, the panel shows "?????", renaming back restores nothing, and at the next autonomy start that platform reads as free while the train stands on it. The v2.8.2 changelog line 368 tells users the rename fault is fixed, which invites exactly this rename.

**Mitigation.** Needs a multi-unit whose head is placed on the graph and a rename of one of its members. isPathClear still refuses a path into a platform whose sensor reads occupied (Layout.java:1415-1420), so a train standing ON the sensor is protected; a train that stopped clear of a pulse-type sensor is not.

**Verification (needs execution).** In testMultiUnitMembership: head "MU head J" (MM2, 78) linked to member "MU member J1" (MM2, 79) with the file's `link` helper; `new Layout(model)` with stations "MU station C"/"MU station D" and an edge; place the head on C; `renameLoc("MU member J1", "MU member J1 renamed")`; then `sanitizeMultiUnits(getLocByName("MU member J1 renamed"))`, as the window does. Proves it: `getLocomotiveLocation(head)` is null. Refutes it: the head is still on C. Clean up with `deleteAll`.

### BPV-C1 - The keepalive claim does not pin the outage clock that keeps the latency cutoff working

| | |
|---|---|
| **Disposition** | Fixed - the keepalive claim pins the outage clock across a resend (master 39466571). |

**What is wrong.** testControlStationFaults.testTheKeepaliveResumesAfterAnUnansweredPing (test lines 70-101) proves that a second ping goes out. It does not prove that `getTimeSinceLastPing` measures from the FIRST unanswered ping: its first reading is taken before any retry (both clocks agree then) and its last after the answer (both are zero). A mutation that makes getTimeSinceLastPing read `pingStart` again (MarklinControlStation.java:2320-2331) passes it.

**Evidence.** Test lines 76-84 and 98. The outage clock is MarklinControlStation.java:182, set at 2301-2304, cleared at 2146. 33403b49's own commit message names this second clock as the part that keeps the warning honest.

**Consequence on the railway.** None as the code stands - the port has it right. Under that mutation, every 5 s tick would resend and reset the reading to about 5000 ms, so the `> PING_INTERVAL` test at TrainControlUI.java:2029 would rarely pass during a real outage and the latency power cut (checkAutoLayoutLatency, TrainControlUI.java:2189-2213) would go quiet exactly when it is needed. The test would stay green.

**Mitigation.** The code is correct today; this is a guard that is missing.

**Verification (needs execution).** Mutate getTimeSinceLastPing to read pingStart and run the class: if it stays green, the gap is real. A claim that closes it: nothing answering, `sendPing(true)`, sleep 2100 ms, `sendPing(false)` (a retry goes out), sleep 2100 ms, assert `getTimeSinceLastPing() >= 4000`. Red against the mutation, green now.

### BPV-C2 - Two load-bearing lines of the Return Home fix are not exercised by its tests

| | |
|---|---|
| **Disposition** | Fixed - each of Return Home's two lines has a claim (master 5055a36b). |

**What is wrong.** (a) testHomeStaging.testALocomotiveOnANonStationIsNotPlannedHome (test line 1911) reaches IMPOSSIBLE through the pre-scan (HomeStaging.java:324) before the search ever calls firstClearRoute, so deleting the second check (HomeStaging.java:676) leaves it green. That second check is what stops the planner using a non-homed train standing on a non-station as a blocker to shuffle; without it such a leg is planned, refused at run time and the run abandoned - the SG-A2 symptom. (b) The stamp in the no-speed skip (Layout.java:2962) matters only when another leg follows the skipped one. The commit's own red for testStagingSkipsALegWithNoSpeed ("the run reported itself abandoned", with the train that had a speed already home) means the moving train's leg ran FIRST and the no-speed leg was LAST - and nothing waits on the last leg's stamp. On that plan order, deleting the stamp also stays green. In 3.0, fd31d2b2 records that its first SG-A5 fix without the stamp hung the dispatch loop; that is the regression this test would not see on master.

**Evidence.** testStagingSkipsALegWithNoSpeed.java:71 (fixture), 136 (the assertion that went red); Layout.java:2907-2911 (the next entry waits while the previous executionTime is 0).

**Consequence on the railway.** None today. A regression of (a) abandons Return Home runs; a regression of (b) hangs a Return Home run with Start greyed until restart.

**Mitigation.** Both lines are present and correct on master.

**Verification (needs execution).** Mutate each line away and run its test; log the plan's move order for (b). If (b) stays green, add a case whose no-speed leg is planned first (for example put the no-speed train one station from its home and the other train two), so the second leg can only start if the first was stamped.

### BPV-C3 - The double-press test stops before the "started" path, so the half that keeps Start greyed during a run is untested

| | |
|---|---|
| **Disposition** | Fixed - a press that starts the run is asserted to leave Start greyed (master 03b1576c). |

**What is wrong.** testMainWindowFaults.testADoublePressOnStartStartsOnce ends every worker at `getRouteList` (test line 83), so `started.set(true)` (TrainControlUI.java:13569) never runs and the `if (!started.get())` guard on the re-enable (13615) is never tested in its true branch. A mutation that re-enables the button unconditionally in the finally passes both assertions.

**Evidence.** Test lines 67-159; handler TrainControlUI.java:13497-13621.

**Consequence on the railway.** None today. Under that mutation the button comes back a moment after a successful press, before runLocomotives' own thread has set `running`, so a second press can pass `isAutonomyBusy` - the double start the fix exists to prevent.

**Mitigation.** The code is correct today.

**Verification (needs execution).** Mutate the guard and run the test: green proves the gap. Closing it needs a worker that reaches the start branch - a stand-in model whose `getAutoLayout` returns a real, valid Layout with one locomotive to run and simulation on - then assert the button is still disabled after the worker has finished.

### BPV-C4 - The Start press now greys the button for as long as the worker lives, and the dialog the worker can block on is still raised off the event thread

| | |
|---|---|
| **Disposition** | Fixed - Start's conditional-routes question is asked on the event thread.  Claim 2f372814 (red first), fix 6fbab968 (master). |

**What is wrong.** Fix 8 greys Start at the press (TrainControlUI.java:13504) and gives it back only in the worker's finally (13610-13619). The conditional-routes confirmation (13534-13543) is still a modal JOptionPane built and shown from that worker thread. 739c933c moved it to the event thread in the same change (confirmOnEventThread, "the kind of violation that mispaints on a good day and deadlocks on a bad one"); the port took the press guard and its finally only, as its commit says.

**Evidence.** 3.0 739c933c, TrainControlUI: the new confirmOnEventThread method and the hunk in startAutonomyActionPerformed that calls it in place of the worker-thread JOptionPane; master handler above. requestStartAutonomy (13137-13149) refuses whenever the button is grey.

**Consequence on the railway.** None directly. If that off-thread dialog ever hangs, Start stays grey for the rest of the session and both right-click Start items say "wait for trains"; before the port the button stayed live. Recoverable by restart.

**Mitigation.** The dialog is shown at most once per session (conditionalRouteWarningShown) and off-thread modal dialogs usually work.

**Verification.** Timing-dependent and not practical to force; decide by reading. The low-risk course is to take confirmOnEventThread (3.0 739c933c) with the guard.

### BPV-C5 - The sensor-monitor test does not guard the wait/notify pairing it moved

| | |
|---|---|
| **Disposition** | Fixed - a wait for a sensor is asserted to end when the sensor is reached (master cd537ec8). |

**What is wrong.** testAutoLayoutRace.testARunningTrainIsNotBlockedByOneBeingDispatched (test lines 170-219) times `updatePendingS88` under a held Layout monitor. Nothing in it waits in `waitForS88Reached`. A half-revert that leaves the waiter on the Layout monitor (Layout.java:3088-3108) while the notifier uses pendingS88Monitor (3116-3131) passes the test.

**Evidence.** As above; the only waiter is Route.evaluate (Route.java:243) on a route's monitor thread (MarklinRoute.java:188-235).

**Consequence on the railway.** None today. Under that half-revert a route whose condition is "locomotive X has reached sensor N" parks its monitor thread for ever and stops firing, silently.

**Mitigation.** Both halves are ported together and correctly.

**Verification (needs execution).** Set a pending sensor for "Race loc A", start a thread in `waitForS88Reached(loc, "Race sensor")`, clear it with `updatePendingS88(loc, null)`, and assert the thread returns within one second. Red against the half-revert.

### BPV-C6 - The three file-based claims skip wherever a real LocDB.data or UIState.data exists, which is every folder Adam runs from

| | |
|---|---|
| **Disposition** | Not a defect - the claims refuse to run where a real file is present, which is right; the master harness runs them in a folder of its own, where they ran green, and 3.0's ports run them on one.sh's own copy of the data (66319d3c). |

**What is wrong.** testControlStationFaults.testAnUnreadableDatabaseIsKeptBeforeItIsSavedOver (test line 169), testMainWindowFaults.testAnUnreadableUiStateIsKeptBeforeItIsSavedOver (180) and testPageNamesSurviveAFileWithFewerPages (266) throw SkipException when the working directory holds the real file. That refusal is right - they must not touch live data - but it means they only ever run in a clean folder such as the scratch worktree. In the NetBeans project folder, where the porting agent says they skip, the battery reports them as skipped and the class reads as passing.

**Evidence.** The three `if (live.exists())` guards; the working-directory file names are MarklinControlStation.DATA_FILE_NAME and "UIState.data".

**Consequence on the railway.** None. The regression guard for fixes 10 and 12 exists only where the battery is run from a clean folder.

**Mitigation.** The skip messages say why.

**Verification (needs execution).** Run the 2.8 battery from the folder Adam normally uses and count the skips (expected 3). If that is the only place it runs, the claims give no protection there; the options are to run the 2.8 battery from a clean checkout, or to give the two restore/save pairs a path parameter a test can point at a temporary folder.

### BPV-C7 - The unreadable-file flag is cleared before the copy is known to exist, and the operator is told only during the exit save

| | |
|---|---|
| **Disposition** | Fixed on both branches - the mark is cleared only once the copy exists, and until then the save leaves the file as it is.  Master: claim 103375b1 (red first), fix 86b8b73a.  3.0: claims 66319d3c, fix e7a2f1fa (RLD-B1).  The start-up wording (a first launch's words for an unreadable file) is left as it was. |

**What is wrong.** Both saves clear the flag first and then try the copy (MarklinControlStation.java:1338-1362; TrainControlUI.java:1121-1146). If `Files.copy` throws, the failure is logged and the save goes on to replace the unreadable file with no copy kept. Separately, a LocDB.data that exists and will not read still logs "No compatible data file found, DB initializing with default data" at start-up (MarklinControlStation.java:1496), the same words as a first launch; the only line that says the file was unreadable is written during the exit save, as the window closes. So the operator runs a whole session with an empty locomotive list and is not told why.

**Evidence.** As cited. Both behaviours are the same in 3.0 (df5d291b; 3.0 HEAD MarklinControlStation.java:1975-1988), so they are faithful ports.

**Consequence on the railway.** Data loss only in the narrow case where the copy fails and the replacement succeeds. The copy reads the old file and writes into tc_backup (or the working folder if that cannot be made, Util.java:74-85); the replacement writes a sibling and moves it (Util.java:123-145). Most causes (a lock on the old file, a full disk) fail both.

**Mitigation.** The failures are mostly correlated; the copy works in every ordinary case.

**Verification (needs execution).** Make the copy alone fail (a realistic trigger is not obvious - `getBackupPath` falls back to the working folder when tc_backup cannot be made - so this likely needs a test seam around the copy) and check whether LocDB.data is still replaced. Proves it: replaced, no copy anywhere. If no realistic trigger exists, clearing the flag only after a successful copy closes it by reading. The start-up wording needs no run: the log line is at MarklinControlStation.java:1496.

### BPV-C8 - Both restoreState methods still leak the file handle when the stream header is bad (pre-existing; noted by the porting agent)

| | |
|---|---|
| **Disposition** | Open - carried to the 2.8.2 release validation: a stream header that will not read leaves the file open on 2.8 until garbage collection, so the save that follows can fail; 3.0 has it fixed (AC3-B1). |

**What is wrong.** `try (ObjectInputStream obj_in = new CustomObjectInputStream(new FileInputStream(dataFile)))` (MarklinControlStation.java:1470) and the same shape in TrainControlUI.java:1393: when the ObjectInputStream constructor throws - a zero-byte file or plain garbage, the commonest corruption - the FileInputStream was never assigned to the resource, so it stays open until garbage collection finalises it. On Windows Java opens it without delete-sharing, so the exit save's `Files.move(..., REPLACE_EXISTING)` (Util.java:145) fails while it is open: "Could not save database" is logged, the session's database is not written, and the staging file is left beside it.

**Evidence.** The port's own test avoids this case on purpose (testControlStationFaults `truncatedObjectStream` javadoc: "on Windows the file handle opened for that constructor then stays open until garbage collection"). Pre-existing on 2.8.1; 3.0 has the same shape.

**Consequence on the railway.** None on the track. For the data: the promised copy is still kept (copying only reads), but until a session happens to garbage-collect the stream, each session starts empty again, fails to save, and adds another "unreadable" copy to tc_backup.

**Mitigation.** Java 8's FileInputStream finaliser closes it at GC, which a long session usually triggers.

**Verification (needs execution).** Clean folder, zero-byte LocDB.data, `init(...)`, then `saveState(false)` without `System.gc()`. Proves it: "Could not save database" in the log and LocDB.data still 0 bytes. Refutes it: the save succeeds. The fix is one line each: open the FileInputStream as its own resource in the same try.

### BPV-C9 - The gateway-ping retry (2b07376f) is left out, so auto-detect can still say "not possible" after one lost reply

| | |
|---|---|
| **Disposition** | Fixed - one lost reply to the gateway ping no longer makes auto-detect say it is impossible.  Claim eacc48d1 (red first), fix bbe308d8 (master). |

**What is wrong.** getLocalSubnet pings the gateway once, with a 200 ms timeout (CSDetect.java:146-147). One dropped reply empties the subnet list: `hasLocalSubnets()` is false and the IP dialog says auto-detect is not possible, or, if that first call passed, `detectCentralStation` scans no subnet and reports nothing found. It is the same fault family as the web check fix 11 repairs, and 3.0 fixed it in 2b07376f (W21-C2) by adding `, PING_RETRY` to that one call - no 3.0 dependency.

**Evidence.** CSDetect.java:146-147 versus the retried scan ping at 76 (`isReachable(host, PING_RETRY)`); 3.0 2b07376f CSDetect hunk.

**Consequence on the railway.** None. The user retries auto-detect or types the address. The changelog (line 381) claims only the slow-answer half, which is true, so this is an omission, not an overclaim.

**Mitigation.** A second press of Auto-Detect usually works.

**Verification.** Reading suffices. If taken, it is the one-argument change from 2b07376f.

### BPV-C10 - The WEB_RETRY comment says the scan does not get longer; live hosts that drop port 80 now cost three timeouts

| | |
|---|---|
| **Disposition** | Fixed - the comment says what the retry costs (master c630d22a). |

**What is wrong.** CSDetect.java:40-50 says "Only hosts that answered a ping ever get here, so this does not lengthen the scan". True for empty addresses; not for live ones. Every PC, phone or printer that answers ping but silently drops port 80 now costs up to three connect timeouts (3 x 500 ms) instead of one, in ten threads; the start-up check `isCentralStation(initIP)` (MarklinControlStation.java:3446) likewise triples when the saved address is not a Central Station web server. Refused connections and 404s are fast.

**Evidence.** As cited; `askWebServer` sets 500 ms connect and read timeouts.

**Consequence on the railway.** None; a few seconds on a busy network. The comment overstates, and so does the commit message.

**Mitigation.** Bounded at three attempts.

**Verification.** Reading suffices; reword the comment.

### BPV-C11 - Return Home with a train off-station now refuses before starting, but its message sends the operator to check the track, and the changelog line reads as if it now works

| | |
|---|---|
| **Disposition** | Fixed in part - the changelog now says what happens (master 5ecbf918); the message's remedy, which sends the operator to the track, is left: it names the train, and nothing moves. |

**What is wrong.** After fix 7 a homed train standing on a non-station makes the plan IMPOSSIBLE with that train named (HomeStaging.java:324, 351), which is shown as `autolayout.ui.errorCannotReachHome` - "These locomotives cannot reach their home station at all: {0}. Check the track between them and where they started." (messages.properties:313). The track is not the problem; the train has to be put on a station. The changelog line 371 ("Fixed: Return Home gave up and stopped every train when one train was standing somewhere that is not a station, or had no speed set") is accurate for the no-speed half - the other trains now go home - but for the non-station half Return Home still does not run: it refuses up front instead of failing half way.

**Evidence.** As cited; TrainControlUI describeStagingOutcome IMPOSSIBLE branch (13450-13458).

**Consequence on the railway.** None - refusing before anything moves is the safe outcome. The operator is sent looking for broken track, and a reader of the changelog expects the case to work.

**Mitigation.** The train is named.

**Verification.** Reading suffices. Plain-English wording for the changelog could be: "Return Home no longer starts and then stops every train when one train has no speed set (that train is now skipped), or when one train is standing somewhere that is not a station (Return Home now names that train so it can be moved first)."

### BPV-C12 - The timetable per-entry warning reuses the line that used to mean "the whole timetable was lost"

| | |
|---|---|
| **Disposition** | Fixed - a skipped timetable entry has its own log line.  Claim eeddbf43 (red first), fix 59ac83ff (master). |

**What is wrong.** Fix 3 logs a skipped entry with `autolayout.warnTimetable` (Layout.java:5135-5139) - the same key the outer catch still uses when the timetable as a whole cannot be read (5146-5150). The text, "Auto layout timetable warning: {0}" (messages.properties:272), does not say the entry was skipped, so a log reader cannot tell one lost entry from a lost timetable. 3.0 added `autolayout.warnTimetableEntry` ("... could not be loaded and was skipped: {0}") for this; the porting agent reused the old key to avoid eight bundle edits, as its commit says.

**Evidence.** As cited; 3.0 a2decb01 bundle hunks.

**Consequence on the railway.** None; log wording only.

**Mitigation.** The {0} detail names the missing locomotive or track.

**Verification.** Reading suffices.

### BPV-C13 - Faults left on 2.8 in the same source commits the port drew from (for Adam's decision)

| | |
|---|---|
| **Disposition** | Fixed in part, on Adam's ruling (2026-09-25: the graceful stop forced, and the invalidate lever to force a reload): a path that fails part way stops autonomy until the configuration is reloaded (SG-A3; claim 85ecf219 red first, fix bfc92395, master).  The short system frame, SG-A1 and SG-A4 stay on 2.8 as they were: none is new in 2.8.2. |

**What is wrong.** The port took selected hunks, as instructed. Three commits it drew from carry other fixes whose defects master still has, with no dependency on 3.0 code. None is a defect of these commits; they are listed so the selection is a decision rather than an accident.

- 33403b49, third fault: `CS2Message.getSubCommand` guards on `data.length` (CS2Message.java:432), which is 8 for every parsed frame (CS2Message.java:127-130), so a system frame declaring fewer than five bytes returns data[4] = 0 = STOP, and receiveMessage (MarklinControlStation.java:2104-2122) treats it as a stop from another controller: power off. Reachability depends on whether anything on the bus sends such frames; unverified.
- fd31d2b2, SG-A1: the pairwise shared-sensor scan (HomeStaging.java:335-348) refuses the whole run even when both trains of the pair are already parked at home; 3.0 notes this is "an ordinary evening" on Adam's railway (BottomMainC and BottomMainCTerm share feedback 4). Applies on 2.8 wherever two homes share a sensor.
- fd31d2b2, SG-A3: a locomotive held on more than one point (reservations left by a path that failed part way; master's executePath deliberately does not unlock on a RuntimeException, Layout.java:3142-3190) is located at whichever point comes first, so a plan can depart from a point the train is not on, and executePath's at-path-start check (3252-3257) passes on a reservation. 3.0 graded this at "the highest price this project has". Not verified on master; needs execution.
- fd31d2b2, SG-A4: the planner exempts the moving train from its own detection section and the runtime does not, so on latching occupancy detection legs are planned, refused and the run abandoned after three tries.

**Consequence on the railway.** As listed per item; SG-A3, if reachable on 2.8, would be the most serious.

**Mitigation.** Each needs its own trigger; none is new in 2.8.2.

**Verification (needs execution for items 1 and 3).** Item 1: feed a system frame with DLC 4 to receiveMessage on a simulated model and check the power state. Item 3: fail a path part way with a RuntimeException in simulation, then ask planReturnToHome and read which point the plan departs from.

### BPV-D1 - Fix 1 (keepalive): faithful, and the UI timer needs no change

| | |
|---|---|
| **Disposition** | Checked - clean. |

Compared 56c1e99a with 33403b49's MarklinControlStation hunks: the field, the retry constant, the reply-handler reset (2146), sendPing (2286-2313) and getTimeSinceLastPing (2320-2331) are identical. 33403b49 changed no keepalive code in TrainControlUI, and master's timer (TrainControlUI.java:2018-2060) already sends one forced ping and then `sendPing(false)` every PING_INTERVAL = 5000 (248); the only other caller is the one-off after the start-up sync (MarklinControlStation.java:292). Because the 5 s fixed-rate tick sits above the 2 s retry, a single lost reply is resent at the next tick, and the outage clock (started just after the previous tick) normally reads just under 5000 there, so one lost reply normally triggers neither the warning nor the cutoff (it can if that tick runs later against its schedule than the previous one did); two in a row do, as on 2.8.1, and recovery now clears both. Users who never lose a reply see no change. One benign race: a late answer to the previous ping landing between the outage-clock check and the `pingStart` write leaves the clock at 0 for one cycle, delaying a real outage warning by one tick; same in 3.0. The claim test is real: the old code sends nothing on `sendPing(false)` while a ping is outstanding, so the reading stays above zero (the reported "found [7120]"); it writes no files and restores both debug flags. See C1 for what it does not pin.

### BPV-D2 - Fix 2 (rename): faithful; every caller of the sweep checked

| | |
|---|---|
| **Disposition** | Checked - clean. |

Layout.java:3627 is 66c96736's MT-149 line, with the comment shortened; the MT-165 half of that commit (HomeStaging.atHome, claimHome on split squares) is 3.0-only. Callers: both rename doors (TrainControlUI.java:10351, 14593) - fixed; the multi-unit editor (10731) - editing a placed train's own consist no longer evicts it, an unlisted improvement; moveLocomotive (Layout.java:3714) removes the train from elsewhere first, so there is no self to skip; fromJSON (4844) - a hand-edited file placing one train twice used to end with it on the later point, now it is on both, but the duplicate check (4917-4923) invalidates that layout either way, so the outcome is unchanged. The claim test is real (red "expected [MU station A] but found [null]"), does what the window does in the window's order, and deletes its locomotives; the two feedback objects it creates live only in the test model. The member case the fix does not cover is A1.

### BPV-D3 - Fix 3 (timetable): faithful; what a partly loaded timetable does

| | |
|---|---|
| **Disposition** | Checked - clean. |

The per-entry try is a2decb01's; its other half (repairing a rename inside the stored configuration, AutonomyCompanionStore) is 3.0-only. On master a timetable with a dropped entry is safe to run: executePath refuses an entry whose locomotive is not standing at the path's start (Layout.java:3252-3257), so no train is dispatched from the wrong place - the run proceeds until the affected train's next entry and then waits there with "not yet executable" (2991) until a graceful stop. Before the fix nothing ran at all, and the next autosave wrote the empty timetable. The claim test is real (red "expected [1] but found [0]"), covers both failure kinds (a deleted locomotive and missing track), and deletes its locomotive. Log wording: C12.

### BPV-D4 - Fix 4 (priority): faithful and complete on master

| | |
|---|---|
| **Disposition** | Checked - clean. |

Point.java:197 is 33403b49's line. Only the priority dialog passes null (GraphRightClickPointMenu.java:251-264; fromJSON uses `getInt`, Layout.java:4742). Every unboxing reader now sees 0: the pickPath comparator (Layout.java:2488-2494), which runs inside each locomotive's thread with no try (2426-2447) - so the "autonomy stops sending trains" claim holds on master; Point.toJSON (528), which the exit autosave calls (so the exit save threw before); and the menu label (GraphRightClickPointMenu.java:234-245). The claim test is real (the NPE is caught and failed) and touches no files.

### BPV-D5 - Fix 5 (routes): faithful; both doors; the one behaviour change is the intended one

| | |
|---|---|
| **Disposition** | Checked - clean. |

Both doors of 2cef4211 are ported - editRoute (MarklinControlStation.java:1554, 1568) and the Central Station refresh (1080, 1090) - with the helpers private (2951-2976; no other caller needs them). deleteRoute still strips the id (2864-2885), so a genuine delete still leaves the selection, which is what the strip is for; changeRouteId is unchanged. Every edit path reaches editRoute: the route editor (RouteEditor.java:2007), Bulk Enable/Disable (TrainControlUI.java:11429) and the per-route toggle (11452). importRoutes (MarklinControlStation.java:3218) still deletes and re-adds without restoring; 3.0 is the same, and an import replaces the route set rather than re-reading one route. The one change a user will notice: with "activate specified routes" on, a route switched off by hand now stays in the selection, so the next graph load (applyAutonomyRouteActivations, 656-690) switches it back on and restarts its sensor monitor. That is exactly what the changelog line 376 calls the fix. The claim test is real (red "expected [true] but found [false]"), asserts its preconditions, restores the selection and deletes both route names.

### BPV-D6 - Fix 6 (pending-sensor monitor): no other waiter or notifier on the Layout monitor

| | |
|---|---|
| **Disposition** | Checked - clean. |

Layout.java:100-119, 3088-3131 are 0b5f5e73's Layout hunk; that commit's other changes (daemon threads, shutdown, LayoutLabel.forgetReplaced - the last later found to be a regression in 3.0) are rightly not taken. The comment's premise holds on master: the only wait/notify on a Layout monitor was this pair (the other hits are Accessory.actuationConfirmedMonitor at 1993 and MarklinControlStation's own monitor at 3100). The only outside waiter, Route.evaluate (Route.java:243), runs on a route's monitor thread (MarklinRoute.java:188-235) or in the route editor, neither holding the Layout monitor, so the change adds no lock-order hazard. configureAndLockPath does hold the Layout monitor across CONFIGURE_SLEEP per edge (Layout.java:1882-1906), so the defect is real on master. The claim test is real (900 ms held monitor blocks the old synchronized method). See C5.

### BPV-D7 - Fix 7 (Return Home): SG-A2 and SG-A5 faithful; the ordinary-timetable skip and the testLayoutTimetable speed change are sound

| | |
|---|---|
| **Disposition** | Checked - clean. |

The pre-scan (HomeStaging.java:324), firstClearRoute (676) and the dispatch-loop skip with its stamp (Layout.java:2938-2965) match fd31d2b2. The runtime rule being mirrored is on master (isPathClear, Layout.java:1406-1413; isAutoRunning is `running`, which executeTimetable sets). The skip applies to ordinary timetables, as in 3.0: the old loop waited for ever on an entry whose locomotive has no speed; now it logs "Failed to run locomotive" and moves on, and that train's later entries wait at "not at path start" - nothing is ever dispatched at speed 0. A Return Home run with a skipped train still ends with the "Return Home stopped" dialog because the train is not home (TrainControlUI.java:13262-13280), so the skip is not silent. The testLayoutTimetable change is justified: its retry characterisation test needs executePath to keep refusing for the invalid-layout reason, and executePath checks validity before speed, so speed 35 keeps it meaningful while speed 0 would now end it at once. Side effect, same in 3.0 HEAD: the debug-only planner audit (HomeStaging.java:375-437) will now report disagreements for a train standing on a non-station at rest, because the runtime check applies only while running. The claim tests are real (red as the commit reports); gaps in C2; message and changelog in C11; the unported SG items in C13.

### BPV-D8 - Fix 8 (Start): every door to the handler is covered by the greyed button

| | |
|---|---|
| **Disposition** | Checked - clean. |

The handler is reached by the button's form listener and by requestStartAutonomy (TrainControlUI.java:13137-13149), which both right-click menus use (GraphRightClickGeneralMenu.java:94, LayoutRightclickAutonomyMenu.java:33) and which refuses when the button is grey. So greying the button at the press (13504) closes all three doors; 3.0's diagram-strip mirror does not exist on 2.8. Every not-started path - no power, a refused dialog, no locomotives, busy, invalid - leaves through the finally and gets the button back (13610-13619); the started path leaves it grey, as the old code's worker did. The claim test is real for the double press (red "expected [1] but found [2]"). Not taken from 739c933c: the event-thread dialog (see C4) and the page-exclusion reload (3.0-only). Test limits: C3 and D12.

### BPV-D9 - Fix 10 (unreadable files): faithful; restore runs once at start-up, save once at exit

| | |
|---|---|
| **Disposition** | Checked - clean. |

The MarklinControlStation hunks are df5d291b's without the unused accessor; the TrainControlUI hunks are d6b9b00c's IP-A1. restoreState(String) is called only from the constructor (MarklinControlStation.java:249) and TrainControlUI.restoreState only from setViewListener (1750); the only non-backup saves are the exit pair (TrainControlUI.java:9904-9905); Backup Data (11898-11899) is excluded by the `!backup` guard. A first launch leaves the flag false (no file). The two new message keys are in all eight bundles (D13). The claim tests are real (red "expected [1] but found [0]" copies), refuse to run over a real file, delete the file they wrote, the copies they made and the backup folder if they created it, and write no preferences: the only preference write reachable from saveState is saveLayoutTitles, gated on the per-folder REMEMBER_WINDOW_LOCATION (11669), which the UI test checks before running. See C6, C7 and C8.

### BPV-D10 - Fix 11 (auto-detect): faithful; the private retry reaches both callers

| | |
|---|---|
| **Disposition** | Checked - clean. |

The CSDetect change is 42d66b31's, kept private instead of 3.0's public overload. Both callers go through it: isCentralStation (CSDetect.java:54) and the scan (79). The claim test is real - one attempt returns false on the first slow answer - and its second assertion (`asked > 1`) proves the retry was actually exercised; the negative test bounds the attempts. It binds 127.0.0.1 on an ephemeral port only and sends nothing to the railway. Its two executor pools are never shut down (idle non-daemon threads), harmless in the per-class forked JVM the battery uses. See C9 and C10.

### BPV-D11 - Fix 12 (page names): faithful; the pre-Dec-2023 file case is harmless; 3.0 still writes the names last

| | |
|---|---|
| **Disposition** | Checked - clean. |

TrainControlUI.java:1781 is d6b9b00c's gate adapted to 2.8's constant. The loop bound change (`size() - 1`) was not taken; harmless, because with a four-page 3.0 file the loop at 1753 reads the names entry as a fifth page whose keys (page numbers and -1/-2) match no button key code. The porting agent's doubt, a pre-Dec-2023 file with pages and no names: the last page's key-code map becomes pageNames; page-name lookups use keys 1-10 and -1/-2, none of which it contains, so tabs show their default names, and that page's mappings still load from the loop. The junk entries persist in later saves and can only make renameCurrentPage (1658) refuse a page name equal to a locomotive name from that page. 3.0 HEAD's saveState still writes pages then names, so the 3.0-to-2.8 path the fix is for holds. The claim test is real and carries its own control (a ten-page file must work, so the four-page failure cannot be a fixture error).

### BPV-D12 - testMainWindowFaults' constructor-less window: sound for what it asserts

| | |
|---|---|
| **Disposition** | Checked - clean. |

`Unsafe.allocateInstance` runs TrainControlUI's static initialiser - resource icons and string constants, no preference writes - and no instance initialisers; every field the three handlers reach is set by the test or not reached. Double press: `graphViewer` is null, so ensureGraphUIVisible is a no-op, and the worker stops at getRouteList; a real window only adds work before that point, which widens the old race rather than closing it, so the result carries over. UI state: saveState reads locMapping, pageNames, buttonMapping, autosave and autonomyJSON, all set; `popups` and `layoutSizes` sit behind the preference the test checks. Page names: the restore runs before any component is touched; switchLocMapping's NPE is swallowed by its own catch after pageNames is assigned; the NPE the test expects comes from `locCommandPanels`, and the test fails loudly if the call ever completes. Nothing later in the real window reassigns pageNames (only 449 and 1783 do). What the technique cannot show is anything after its stop point - C3.

### BPV-D13 - Message bundles clean

| | |
|---|---|
| **Disposition** | Checked - clean. |

All eight bundles have zero non-ASCII bytes on master (as on the base). The only bundle change in the range is the two new keys, `log.databaseUnreadableKept` and `ui.uiStateUnreadableKept`, present exactly once in each of the eight; each takes `{0}` and none contains a straight apostrophe (French, Italian and Dutch use the escaped right single quote, u2019). The reused keys `autolayout.warnTimetable`, `ui.errorBadUiDataFile` and `autolayout.errorFailedToRunLocomotive` already existed in all eight.

### BPV-D14 - Changelog v2.8.2: one plain line per port, nothing extra

| | |
|---|---|
| **Disposition** | Checked - clean. |

Readme.md:366-381: eleven lines for the eleven ported fixes, none for item 9, nothing listed that is not in the commits, grouped as the 2.8.1 entry is, in plain English. Each line is true of the code except the non-station half of line 371 (C11). Line 368 is true as far as it goes; A1 is the case it does not cover.

### BPV-D15 - The whitespace rebase changed no content

| | |
|---|---|
| **Disposition** | Checked - clean. |

The old tip 56c1204d is still reachable (reflog HEAD@{20}). `git diff -w 56c1204d master` is empty; the plain diff is 18 lines in five files, every one a trailing-whitespace difference on a blank line, a brace line, after a statement or after a javadoc tag - nothing inside a string literal or a bundle. All twelve rewritten commits are whitespace-identical to their originals and carry identical messages. The worktree is clean, and the touched files stay CRLF throughout. What was tested at 56c1204d is what master holds.

### BPV-D16 - Version bump consistent

| | |
|---|---|
| **Disposition** | Checked - clean. |

RAW_VERSION (MarklinControlStation.java:81) is the only place master records its version; the About box, the window title, the start-up line and the update check all read it. No other "2.8.1" remains in src, build files or nbproject, apart from a version-comparison test (testInvalidInput.java:571, unaffected) and the historical 2.8.1 test-plan document. The changelog date format matches the 2.8.1 entry.
