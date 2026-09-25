# Issues

Bugs and feature requests, in one inbox. Adam writes here - by hand, or through
[triage.py](triage.py)'s **New issue** button. Claude reads here, turns each item into a finding under
the round's prefix (for a bug) or works it directly (for a feature request), opens an `MT-###` entry in
[tests.md](tests.md) to cover a bug fix (a feature request only if the work turns out to need one), and
clears the item out of the Inbox. Findings live in the `finding` table of `triage.db`; the review
document that first wrote one is deleted with its round - see
[../reviews/README.md](../reviews/README.md).

This replaces the separate `bug-reports.md` and `feature-requests.md` files from 2026-08-22 - the two
inboxes worked identically and existed only because bugs and features felt like different things when
this was hand-maintained. Now that [triage.py](triage.py) parses the file, one Kind field does the same
job as two files, and a bug filed while looking for a feature (or the reverse) has one place to land
regardless.

**How to use it, by hand.** Put anything under **Inbox** below - a sentence, a paragraph, a sketch.
There is no format to follow. Say whether it is a bug or a feature request if you can; if you can't
tell yet, say so and Claude will sort it out.

**How to use it, through triage.py.** The **New issue** button opens a small form: pick bug or feature
request, a one-line summary, and detail. It writes a structured entry here - `triage.py issues` can
then list every open one without anyone re-reading the file by hand.

**A bug is `OB-###`; a feature request is `FR-###`.** Separate counters - a glance at the ref says
which lifecycle an item is on. `OB` kept counting from before the split existed; nothing already
filed was renumbered.

**What happens next:** at the start of the next round, Claude picks up everything in the Inbox and
gives it a receipt row here. A **bug** also gets an `MT-###` tag in `tests.md`, disposition **needs
test** - a fix needs a repeatable hands-on check that the regression stays fixed, so that tag is
handed out immediately, not earned. A **feature request** is tracked directly instead, by default:
its receipt row gets a **State** in three of the four words `tests.md`'s disposition uses (not
**superseded**, which has no meaning for a request nobody has coded yet), plus two of its own,
**pending** for picked up and not built yet, and **declined** for something cancelled - see below -
set by Claude and only by Claude.  Once an `MT-###` tests it, the row names that tag in **Became**
instead, so its state follows the test. It
only gets promoted to an `MT-###` tag if the eventual work turns out to need a genuine hands-on
test the way a bug fix does.

**Filing is not asking for it to be worked.** Filing puts it on the list; asking gets it built. That
split is deliberate - it lets you write something down the moment you think of it without deciding
then and there whether it is worth doing now. The exception is your own judgement: say a bug is urgent
in its own text and it is treated that way.

**Cancelling something already filed works the same way filing does: you request it, Claude acts on
it.** The **Request cancel…** button in triage.py's Feature requests/Bugs tabs - on a pending item or
an already-picked-up one - opens a small prompt for an optional reason and files a new structured
item naming what it is cancelling. Nothing changes immediately: at the start of the next round,
Claude reads that request, sets the target's State to **declined** (or, for a bug already promoted,
records the decision in its `MT-###` entry instead, since a promoted bug's own Comments are where its
outcome lives), and closes both items out. Only Claude sets State, the same rule as everywhere else -
this is how you ask, not how you decide.

**Before adding to the Inbox below - by hand, through the app, or as an automated round reading this
file to decide what to do - check whether it is already there.** `py -3 docs\manual-tests\triage.py
issues` lists every pending item in one command. OB-001 and OB-002 in the receipt table below are
what skipping this looks like: the same observation, filed twice, two minutes apart, because nothing
checked whether the first filing had already happened before the second one ran. A round that reads
this tracker to decide what needs doing has to read the Inbox and the ledger BEFORE writing to either,
not just before reporting back - "I looked, so I know what to build" is not the same claim as "I
looked, so I know this is not already here."

---

## Inbox

### FR-048 - 2026-08-31 - easier locomotive editing

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-31 01:42  
**Build:** commit 302d7a11, build\classes, compiled 31 Aug 01:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

make a locomotive editable by right clicking its name when it's active.  split that from opening the locomotive database.  make a single locomotive parameters window rather than the many separate options right now.  for 3.1.0 release

### FR-049 - 2026-08-31 - locomotive import and export

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-31 01:42  
**Build:** commit 302d7a11, build\classes, compiled 31 Aug 01:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

locomotives can be exported to a csv, but not imported.  add an option to import, i.e. if another TC instance added runtie.  3.1.0 feature

### FR-050 - 2026-08-31 - modernize keyboard tab

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-31 01:44  
**Build:** commit 302d7a11, build\classes, compiled 31 Aug 01:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

more buttons per page.  tabs.  ability to re-fire without cycling.  feature for 3.1.0

### FR-051 - 2026-08-31 - improved log

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-31 01:48  
**Build:** commit 302d7a11, build\classes, compiled 31 Aug 01:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

log timestamps, search, export features.  for 3.1.0

### FR-052 - 2026-08-31 - autonomy editor bulk mark stations

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-31 02:16  
**Build:** commit 302d7a11, build\classes, compiled 31 Aug 01:35 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

in the autonomy editor, add a button to bulk mark stations.  click button then select all stations, then apply an action that is propagated to all of them.  feature for 3.1.0.

### FR-053 - 2026-08-31 - calculate signals to set to red

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-31 23:16  
**Build:** commit 302d7a11, build\classes, compiled 31 Aug 23:15 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

instead of specifying signals that guard a station, calculate them based on what gets locked and what is ahead of the active path.  feature for 3.1.0.

### FR-055 - 2026-09-02 - search function for points in autonomy editor

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-02 08:12  
**Build:** commit 409d4ce8, build\classes, compiled 02 Sep 08:07 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

search a point name, the right page is opened and the tile highlighted.

when other page points are clicked from warnings, also highlight it after the editor window switch

### FR-059 - 2026-09-04 - add the paused autonomy locomotive indicator to right click menu on track diagram

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-04 03:08  
**Build:** commit 409d4ce8, build\classes, compiled 04 Sep 02:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

add the paused autonomy locomotive indicator (and the ability to toggle whether it's paused) to the right-click menu on the track diagram. maintain parity with the autonomous locomotive commands panel. likely for 3.1.0

### FR-061 - 2026-09-07 - Text Labels as a dropdown.

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-07 19:19  
**Build:** commit 409d4ce8, build\classes, compiled 07 Sep 19:13 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

OB-174 is currently only partially fixed.  fully address it by adding a "Text Labels" label and dropdown right above Track Directions, with the following options: Station Names, Parked Locomotives, Home Locomotives, and None.  Station Names should be default, with the setting remembered between open.

### OB-181 - 2026-09-07 - copy and paste direction still inconsistent

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-07 23:47  
**Build:** commit 409d4ce8, build\classes, compiled 07 Sep 23:42 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when copy and pasting, the right click menu direction still doesn't match the shown direction.  moving a train in the track diagram editor from tunnelleftpark (EN 57-203) to bottommaina showed its direction as eastbound in its station label, but westbound in the right click menu.  also, its "arrived from" is not set, even though it is forced.

### OB-182 - 2026-09-08 - arrival direction prompt error

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-08 01:00  
**Build:** commit 409d4ce8, build\classes, compiled 08 Sep 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

placing a train on bottommainc asks about arrival from the west or the north, whereas it should be east or west.

### OB-184 - 2026-09-08 - home planner bug

**Kind:** bug  
**Raised from:** MT-300 (Return Home reaches a split platform from either direction)  
**Filed:** 2026-09-08 01:04  
**Build:** commit 409d4ce8, build\classes, compiled 08 Sep 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

The home planner does not consider blocks due to the length of a train, i.e. a train that blocks edges behind it.

### FR-062 - 2026-09-08 - download the CS3 data files too when the user confirms a Central Station download

**Kind:** feature request  
**Raised from:** the triage API  
**Filed:** 2026-09-08  

Raised by Adam in MT-170 on 2026-08-24, in the note attached to a Works verdict, and never filed on its own: *"if the user confirms the CS download, we should also download CS3 data files if using a CS3."* MT-170 tested backing up a layout that lives on the Central Station and passed; this is the follow-up it raised, filed so MT-170 can be closed without losing it.

### FR-063 - 2026-09-08 - local locomotive icons

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-08 09:40  
**Build:** commit 22f3d302, build\classes, compiled 08 Sep 08:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

right clicking a local locomotive icon should provide a clear option to clear it and change it, rather than opening an editor without context.  create a dropdown for this in 3.1.0 when the icon is clicked.

### FR-064 - 2026-09-08 - routes when power is off

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-08 10:19  
**Build:** commit 22f3d302, build\classes, compiled 08 Sep 08:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when the power is off, replace the route play icon with a wrench icon to edit it.  restore play icon and run behavior on click when the power comes on.

### OB-192 - 2026-09-08 - critical: UI freeze in autonomy

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-08 22:05  
**Build:** commit 0016fc18

starting autonomous operation from the current track state, via the netbeans compiled jar, makes the UI unresponsive.  Trains still run, but nothing is repainted, and controls are stuck.

### FR-070 - 2026-09-11 - make it easier to enable/disable routes

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-11 03:31  
**Build:** commit ac960047, build\classes, compiled 11 Sep 03:29 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

instead of having to risk-click to enable/disable auto execution, show a checkbox next to each entry in the route tiles.  checking it has the same effect as activating auto execution.  grey out checkboxes for routes with no s88, and disable everything while the power is on.  feature for 3.1.0

### FR-071 - 2026-09-11 - right clicking the edit button

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-11 03:44  
**Build:** commit ac960047, build\classes, compiled 11 Sep 03:29 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

right clicking the edit button on the track diagram viewer pulls up the same "manage pages" right click menu to manage pages (add/delete/rename), and is blocked at the same times as the other one.

### OB-200 - 2026-09-12 - hotkeys don't work in autonomy editor

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-12 00:29  
**Build:** commit ac960047, build\classes, compiled 11 Sep 22:41 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when items in the list of issues are selected, hotkeys on the track diagram stop working, and there is no way to regain focus.  just send the commands through with the list of issues panel selected.

### OB-201 - 2026-09-12 - incomplete error message in autonomy

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-12 00:35  
**Build:** commit ac960047, build\classes, compiled 11 Sep 22:41 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

This error message should say either close arrivals from <direction>, open the way ahead, or let trains change direction.  "A train reaching s88 1026 from at least one direction could not go on: the only track ahead of it is the track it just came along, and a train cannot turn round here.  Either set "trains may change direction here", or open the way ahead."

### FR-072 - 2026-09-12 - easier autonomy triage

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-12 00:35  
**Build:** commit ac960047, build\classes, compiled 11 Sep 22:41 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

clicking an issue in the autonomy view should allow the user to triage the issue- open the relevant dialog or give the user options to choose from for each type of issue. 3.1.0 feature

### FR-073 - 2026-09-12 - filter autonomy issue list

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-12 00:40  
**Build:** commit ac960047, build\classes, compiled 11 Sep 22:41 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

add a text box so users can type a station or train name (string match against the list) to filter it.  careful about refreshing it when issues are added.

### FR-076 - 2026-09-13 - easy tracking of station labels

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-13 22:32  
**Build:** commit ac960047, build\classes, compiled 13 Sep 22:07 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

autonomy station labels should be deduped per page, not globally- that way, other pages' stations can be tracked from a main page if desired.

### FR-083 - 2026-09-14 - the arrow and badge of a train on its way should already face the way it will arrive

**Kind:** feature request  
**Raised from:** MT-412  
**Filed:** 2026-09-14  

Adam, on MT-412 (2026-09-14): *"Works.  But we should make a FR to track fixing this behavior so that the pre-arrival arrow just matches."*

The behaviour is OB-218's, accepted on 2026-09-14 on the condition that the arrow is right once the train arrives, which it is: while a hand-driven train is on its way to a square it may turn at, the route is reserved on the copy the path ENDS on - routinely the turning copy - so the badge and arrow show the turned facing until arrival, when the train is re-stood on the copy that matches the answer.  The request is for them to match before arrival too: reserve the copy the train will actually end on, decided from the answer at dispatch - a change to the locking path, which is why OB-218 was accepted rather than changed.

### OB-224 - 2026-09-14 - a train was refused for standing across the points at a square it only passes

**Kind:** bug  
**Raised from:** MT-333  
**Filed:** 2026-09-14  

Adam, on MT-333 (2026-09-14): *"Regression, but not directly related to this test.  75 407 DB (len 2) can no longer go from tunnel to bottommaina because of the 1+1 length split around the switch between buttommainapre and bottommaina.  this SHOULD be allowed per the standing rule that this switch blocking should only affect berthes.  Figure out when introduced the regression, fix it, and then re-file the protrosion test itself."*

**What refused it.**  Not BottomMainA - its approach measures 1 + 1, and the relaxation of 2026-09-12 admits a two-unit train.  It was refused one square earlier: *"75 407 DB (length 2) does not fit at BottomMainAPre on the way to BottomMainA, where the track measures 1"* - the run from Tunnel to BottomMainAPre crosses a switch with only 7,9 measured on it.  The same refusal closed every lower-level destination from Tunnel.

**When.**  The rule arrived in `5948a88a` (2026-09-10), built on the ruling of 2026-09-09 that every square on a route is judged - a square the train only passes wherever a switch lies behind it.  The relaxation of 2026-09-12 was built in `825e7d91` (2026-09-13) at the destination only.  It became visible on this railway when 13,12, 7,9 and 10,10 were measured, after the last committed copy of the layout (8 Sep, which has 19,12, 14,13 and 22,7).  Not a code change on 13-14 Sep: the refusal could be reproduced from `5948a88a` on with these measurements; old builds cannot load today's setup, so it was established from the history rather than by running them.

### FR-084 - 2026-09-14 - switching Path Type redraws the tested route

**Kind:** feature request  
**Raised from:** MT-399  
**Filed:** 2026-09-14  

Adam, on MT-399 (2026-09-14): *"Works, but when changing the auto and manual radio buttons, make it update the shown route to the new selection without having to repeat the button press sequence."*

### FR-085 - 2026-09-14 - Ask for the farthest sensor a placed train's tail crossed, and keep the road it names

**Kind:** feature request  
**Raised from:** WK7-B1  
**Filed:** 2026-09-14  

Adam, 2026-09-14, on WK7-B1: *"For the tails, if a train is long, why not ask the user to specify the last sensor it crossed from a list of possible sensors?  Then state will always be fully consistent."*  And: *"Go - build it autonomously, battery when done.  the prompt should ask the user to pick from a list and select the farthest sensor the tail of the train recently crossed.  Also, ideally in the autonomy editor, it should allow the user to click to select as well."*

Two halves.  **Kept:** a standing train's arrival road is saved with the point, reloaded, carried across every rebuild and captured into the setup, so a driven train's tail is the same after a restart or any setup gesture (WK7-B1, `63ac68f4`).  **Asked:** a hand-placed train long enough for its tail to have crossed sensors on two roads back from a junction is asked for the farthest sensor its tail crossed - at the paste, the right-click Place and the locomotive dialog, in the right-click menu beside Train arrived from, and by clicking in the autonomy editor.  behaviour.md 5c.

### FR-087 - 2026-09-15 - A station autonomy may choose accepts a train its own route in holds, not only its approach

**Kind:** feature request  
**Raised from:** MT-431  
**Filed:** 2026-09-15  

Adam, on MT-431 (2026-09-15): *"But didn't we say that BottomMainA should be allowed at length 3, since it is not a parking spot, the station allows the length, and the length would be tracked?"*  Asked how far the allowance should reach, he chose **bounded by the route**: at a station autonomy may choose, accept the train if the measured track of the route it drives in on holds it back from the destination - its tail lies on that route and blocks it - while unmeasured track or a gap still refuses.  Today the bound is the approach's own length (behaviour.md 5a, the 2026-09-12 relaxation), so 75 407 DB at length 3 is refused BottomMainA's two-unit approach.

### OB-225 - 2026-09-15 - Switching Path Type does not redraw the tested route in the autonomy editor

**Kind:** bug  
**Raised from:** MT-434  
**Filed:** 2026-09-15  

Adam, on MT-434 (2026-09-15): *"Does not work.  Changes don't happen when switching, and in manual mode, I still get reasons like 'tunnellongpark will never be chosen in autonomy'."*  The note is written only when Auto is selected, so on Manual it means the test was never run again.  `core.testManualOnlyPathsAreADifferentColour.testSwitchingPathTypeRedrawsTheTestedRoute` passed without reaching the real editor: it drives `applyTest` on a panel with no page, and its route assertion compares against an empty substring.

### OB-226 - 2026-09-15 - The tail question leaves out a sensor exactly the train's length back

**Kind:** bug  
**Raised from:** MT-435  
**Filed:** 2026-09-15  

Adam, on MT-435 (2026-09-15): *"When 75 407 DB is set to length 3, only BottomMainAPre is offered (2 away from the station), but Tunnel should also be offered since it is 3 away.  The check works as intended at length 4."*  `TailCrossedPrompt` counts a sensor as crossed only when some of the train is left beyond it.

### OB-227 - 2026-09-15 - The tail question offers roads a train could not have driven in on

**Kind:** bug  
**Raised from:** MT-435  
**Filed:** 2026-09-15  

Adam, on MT-435 (2026-09-15): *"If 'Ramp down' is selected, rather than the path that takes the train there through bottomsecondary, sensor 1030, tunnel, bottommaina, the blocked orange path crosses switch 99 and switch 100 instead of going to bottomsecondary, which would require the train to reverse in.  that isn't a realistic path."*  `TailCrossedPrompt` follows track back in either direction; the road a tail lies on is the one the train drove in on, forwards.

### FR-088 - 2026-09-15 - The tail question starts on the sensor nearest the back

**Kind:** feature request  
**Raised from:** MT-435  
**Filed:** 2026-09-15  

Adam, on MT-435 (2026-09-15): *"The closest sensor to the back should be the default selection in the length window, so the user can just click OK if appropriate."*  Asked what to do where the tail could be on more than one road, he chose: start on the recorded road if there is one; otherwise on the sensor nearest the tail when exactly one qualifies; otherwise nothing chosen.

### OB-230 - 2026-09-15 - Return Home's A* runs out of time on real arrangements: the heuristic gives no credit for getting closer

**Kind:** bug  
**Raised from:** review finding AMH-C1  
**Filed:** 2026-09-15  

**Raised from review finding AMH-C1** (`grep "^AMH-C1" docs/manual-tests/findings.tsv`), and **deferred at Adam's word, 2026-09-15:** *"I want to do the heuristic, but I think this needs to be deferred until I deliver you the fully measured layout.  So, let's mark this as an open OB for now and continue."*

**What happens.**  Return Home answers `NO_PLAN_FOUND` on arrangements a person can solve by eye.  The round 3 battery left one: five trains, every one of them with a route home, three of those routes merely not clear yet because another train is standing on the destination - an ordinary rearrangement, and the order that solves it is visible by inspection (loc 3 home first, its route being clear; then loc 0 off loc 2's home; then loc 2; then loc 1).  The blocked list came back empty, which is honest: nothing on the railway is barred, the search simply did not find the order in the time it had.

**Measured, on Adam's instruction** (*"the whole point of the A* is to figure out how to rearrange other trains to park things when they belong, so we also need to differentiate issues with the track diagram from issues with the algorithm.  i recommend relaxing restrictions (track/station lengths) in the layout used for the A* tests."*).  The same arrangement, one relaxation at a time:

| variant | outcome | time |
|---|---|---|
| trains three units long | `NO_PLAN_FOUND` | 15.1 s - the whole budget and its retry |
| train lengths zeroed | **READY, 8 moves** | 6.6 s |
| and station maximums cleared | READY | 6.6 s - **none were set** |
| and FR-001 restrictions cleared | READY | 6.6 s - **none existed** |
| and every train reversible | `NO_PLAN_FOUND` | 15.0 s |

Then ten presses of the relaxed arrangement, to see whether the planner is intermittent where nothing but the order is in question: **READY on 10 of 10, 6.2 to 7.0 seconds, the identical eight-move plan every time.**

**What that settles.**  Length is the only restriction in play on his railway - no station maximums are set and no FR-001 restrictions exist - and zeroing the train lengths is what turns this arrangement from unplannable into an eight-move plan.  Every failure is deadline-bound: none exhausted `SEARCH_LIMIT`'s 50 000 configurations, so time is the binding constraint and not the state space.  And more freedom can lose a plan - making every train reversible widened the branching factor and the answer went back to `NO_PLAN_FOUND` inside the same deadline.  Non-monotonic behaviour of that kind is a budget symptom.

**The agreed fix: the heuristic.**  `HomeStaging`'s estimate is `misplaced` - the count of trains not at home.  It cannot tell a move that carries a train most of the way home from one that shuffles it sideways, so A* spends its budget breadth-first over orders that are all equally promising by its own measure.  A heuristic that gives credit for getting closer - route length remaining, or moves-to-home over the diagram - is what turns the constrained problem from a deadline into a search.  **Deferred until Adam delivers the fully measured layout**, because the constrained problem is the one worth tuning against and its lengths are what make it hard: tuning against relaxed lengths would fit the instrument rather than the railway.

**Not a regression, and the pinned class is not evidence for this.**  `core.testTrainsComeHomeFromAPinnedArrangement` freezes that scatter with lengths relaxed per Adam's ruling (*"make a pinned version and keep the current version, aiming for both to be true.  normal autonomy runs should always have a solution."*), and it is reliable 10 of 10 - so it is a regression instrument for the determinism fix, not budget evidence.  The live `core.testTrainsComeHomeToTheirPlatforms` leaves lengths unset and reaches the deadline on harder scatters; that is this issue.  Neither AMV-B1 nor AMW-B3 is implicated: asked again with every train reversible the answer is still `NO_PLAN_FOUND`.

The probes are not committed; the figures above are their output.

### FR-089 - 2026-09-16 - Mass Assign Lengths, and a display that highlights the track still needing a length

**Kind:** feature request  
**Raised from:** Adam, 2026-09-16  
**Filed:** 2026-09-16  

Adam, 2026-09-16, asking what it would take: *"How big of a lift would it be to add a "measurement view" which shows just the logical segments that require a measurement?  Basically, I am looking for a way to make it easy for me to ensure every relevant segment gets a measurement, similar to "name all"."*  Then, having been shown the trade-off between asking per square and per stretch: *"per stretch, every relevant square a rule reads. build it.  let's have: the "mass assign lengths" feature in the right click menu that cycles through each relevant square.  and a display option to statically highlight all relevant unmeasured squares."*

**Which squares.**  Every square a length rule reads: on every approach to a station, parking berth or turn-round square, the track from that square back to the nearest switch - and where a leg crosses no switch, on through the sensor behind it onto the leg before, because the room rule does (AMR-B3).  The switch tile is not room and is not asked for.

**Per stretch.**  A stretch is one leg's share of that - between the square it arrives at and the switch, or between two sensors.  One whole length is typed and shared evenly over the squares that have none; squares already measured keep theirs; any remainder goes to the squares farthest from where the train rests, the refusing direction for the tail and berth rules.

**On his railway today** (a sandbox copy, 2026-09-16): 46 stretches and 181 squares - 33 stretches and 131 squares on 1 - Main, 13 and 50 on 2 - Bottom, none on the three pages left out of autonomy, and no stretch crossing a page.

**Revised the same day after review MAL** (`grep "^MAL-" docs/manual-tests/findings.tsv`).  The squares above are only the room rule's: the FR-087 allowance and the tail and berth walks read the switch and the track before it, and on this railway their reach is every leg.  Asked, Adam ruled *"Every leg, cut at switches"* and *"One length for all switches"*: every leg is cut into pieces between sensors and switches, switches are asked for together with one turnout length, and a piece needs a length only while its whole total is 0.  On a copy of the railway after the fix: 96 pieces (91 still with no length) and 54 switches.

### FR-090 - 2026-09-16 - The walk's prompt stays where it was left until a new round is started

**Kind:** feature request  
**Raised from:** MT-454  
**Filed:** 2026-09-16  

Adam, on MT-454, 2026-09-16: *"when the popup closes and reopens, make sure it remembers its location unless I reopen a new mass assignment round from the right click menu.  right now, every skip press re centers it, which covers some of the track diagram"*

**Why it re-centred.**  Each prompt of the walk is a new dialog, and `JOptionPane.createDialog` centres every one on its owner - so a prompt dragged off the track diagram came back over it at the next Skip.

**Built.**  Each prompt of a walk opens where the last one was left; starting a round from the right-click menu opens its first prompt afresh.  Name Everything's walk had the same habit and shares it.  `core.testMassAssignLengths.testTheWalkPromptStaysWhereItWasLeftUntilANewRound` drives the real walk on the event thread: it moves the first prompt, presses Skip, and requires the next prompt where the first was left, then presses Cancel, starts a new round and requires its first prompt NOT to open there.  Red first - the second prompt opened centred, at 733,489 - and a mutation dropping the new-round reset fails its second half.

### FR-091 - 2026-09-17 - Mass Assign Max Train Lengths walks the stations with no maximum

**Kind:** feature request  
**Raised from:** Adam, 2026-09-17  
**Filed:** 2026-09-17  

Adam, 2026-09-17: *"add a similar feature to walk stations that don't have a min length set up, so I can enter it"*, corrected at once: *"max length"*.

**Built.**  **Mass Assign Max Train Lengths...** in Bulk Tools, beside Mass Assign Lengths.  It goes through the stations on the page that will still take a train of any length - no maximum, or a maximum of 0 - row by row, outlining and scrolling to each, and asks for its maximum through the same prompt as Mass Assign Lengths: the number box has the focus, Enter submits, each prompt opens where the last was left, OK / Skip / Cancel, Escape stops.  0 is refused with a sentence saying it means any length.  A station that already has a maximum keeps it.  The number lands where the right-click menu's Maximum Train Length and Control+B put it.  Greyed, with a tooltip saying so, when every station on the page has one.

**The same stations the `NO_MAX_TRAIN_LENGTH` notice lists**, now through one predicate, `hasNoMaximumTrainLength`, so the walk and the notice cannot come to disagree about which they are - but not behind the notice's gate: the notice stays quiet on a railway that models no lengths, and somebody who opens this walk has just said they are modelling them.

`core.testMassAssignLengths`: `testTheMaximumWalkAsksAboutStationsWithNone`, `testAMaximumOfZeroIsRefusedAndOneAlreadySetIsKept`, and `testTheMaximumWalkWritesWhatIsTypedAndMovesOn`, which drives the real walk - types 7, presses Enter, requires the next prompt, presses Skip, and requires the first station to hold 7 and the second nothing.  Five mutations each caught: 0 accepted, a set maximum overwritten, the notice's gate added to the walk, a non-station accepted, and the walk not writing.

### FR-092 - 2026-09-17 - Clear All Max Train Lengths, grouped with the other clears in Bulk Tools

**Kind:** feature request  
**Raised from:** Adam, 2026-09-17  
**Filed:** 2026-09-17  

Adam, 2026-09-17: *"Add a right click menu open to clear all max station train lengths (grouped with the other clear options)"*

**Built.**  **Clear All Max Train Lengths (n)** in Bulk Tools, directly after Clear All Track Lengths.  It takes the maximum train length off every station on every page, after a confirmation that says how many and that each will then take a train of any length.  Built like the clears beside it: the tooltip is the confirmation's own sentence, the item is greyed with a reason when there is nothing to clear, and the walk keeps its own emptiness guard.  Only maxima above 0 are counted, because the setup writes an explicit 0 on every destination and 0 is what the clear leaves behind.  Through one session door, `clearEveryMaxTrainLength`, which re-derives the station index once rather than per station.

`core.testMassAssignLengths.testClearAllMaxTrainLengthsClearsEveryPage` and `testTheClearMaxTrainLengthsItemCountsAndGreys`, on a two-page railway built in memory.  Five mutations each caught: clearing one page only, counting a 0, not clearing, never greying, and the item moved away from the other clears.

### OB-234 - 2026-09-21 - clicking the findings list kills the autonomy editor keyboard shortcuts for the session

**Kind:** bug  
**Raised from:** review finding IND9X-B4, 2026-09-09  
**Filed:** 2026-09-21  

The findings list in the autonomy editor (`AutonomyEditorPanel`, the `JList` behind
`findingsModel`) is focusable, and every other control in that column is not: `button()` calls
`setFocusable(false)` on each one, deliberately, so that the FRAME keeps the keyboard. Clicking a
finding therefore moves focus into the list, and the frame's `KeyListener` - which is where the
editor's shortcuts live - stops seeing keys until something hands focus back.

**The fix is one line**, `setFocusable(false)` on the list, which is what its siblings do. What it
needs beside it is a claim that can fail: the shortcuts are a `KeyListener` on the frame, so a test can
give the list focus, dispatch a key, and assert the frame saw it.

**Verified still true on 2026-09-21**, when the review that found it was deleted.

### OB-235 - 2026-09-21 - the One-way tool is not greyed on an excluded page, unlike its two siblings

**Kind:** bug  
**Raised from:** review finding IND9X-C6, 2026-09-09  
**Filed:** 2026-09-21  

`AutonomyEditorPanel.refresh()` greys `testButton` and `whyButton` on an excluded page, under a
comment saying nothing in that column can do anything on a page autonomy takes no notice of.
`oneWayButton` is not in that pair, and its `tileClicked` branches run before the `isIgnored(tile)`
check.

**What you see.** On an excluded page the two-click one-way gesture completes, the direction dialog
appears, and the refusal reads "no path between the squares" - which is the wrong explanation for a
refusal whose real cause is that the page is excluded. This is the affordance-and-guard shape of
OB-057 and OB-090: the control that offers an action has to ask the same question the action does.

**The fix** is `oneWayButton.setEnabled(!ignored)` beside its two siblings.

**Verified still true on 2026-09-21**, when the review that found it was deleted.

### OB-236 - 2026-09-21 - the cut half of cut-and-paste builds the autonomy session it deliberately does not tell

**Kind:** bug  
**Raised from:** review finding FV3-C6, 2026-09-10  
**Filed:** 2026-09-21  

`LayoutEditor.deleteSelection(boolean tellAutonomy)` reads
`parent.getAutonomySession()` before it tests `tellAutonomy`. The cut half of cut-and-paste passes
`false` on purpose - the paste carries the setup - so it now reaches the lazy builder that
`delete(label, false)` never did. That getter parses every page, runs the caption migration (which
writes page files) and can put a dialog on screen; three comments in this codebase, `pageIdFloor`'s
among them, warn against reaching it from a gesture that has nothing to do with autonomy.

In practice the session is normally already cached while the editor is open, which is why this is low
rather than medium. **Moving the read inside the `if` is the whole fix.**

**Verified still true on 2026-09-21**, when the review that found it was deleted.

### OB-237 - 2026-09-21 - refreshReturnHomeButton materialises the autonomy Layout from a repaint

**Kind:** bug  
**Raised from:** review finding D3-C3, 2026-09-09  
**Filed:** 2026-09-21  

`TrainControlUI.refreshReturnHomeButton()` asks `this.model.getAutoLayout()`, and
`getAutoLayout()` creates a layout when it has none (that is CS3-C4's subject, now synchronized but
still creating). So a repaint of the Return Home button can bring a Layout into being, which its own
sibling paths take care not to do - they ask `hasAutoLayout()` first.

**The fix** is the same shape: ask `hasAutoLayout()` and treat absence as "no button", which is what
the method does with a null layout anyway.

**Verified still true on 2026-09-21**, when the review that found it was deleted.

### FR-093 - 2026-09-21 - manual-only destinations shown by the transparent treatment, as you ruled

**Kind:** feature request  
**Raised from:** your ruling of 2026-09-09, carried from the deleted for-adam note  
**Filed:** 2026-09-21  

**Your ruling of 2026-09-09, which has never been built.** It was recorded in
`docs/for-adam-2026-09-09.md`, which was deleted on 2026-09-21 with the rest of the stale review prose,
so it is here instead - an unbuilt ruling in a document nobody opens is an unbuilt ruling nobody
builds.

Your words: *"Already answered this: keep the orange to represent the train, and make the tiles
transparent just like when we block edges."*

So there is no new arrow overlay of any kind. Orange keeps one meaning on the running diagram - where
a train is - and a **manual-only destination** is shown by the same treatment blocked track already
gets: the tile goes transparent.

**What is there today.** Nothing draws it. The grey wash is computed for track a train covers, and
neither `LayoutGrid` nor `LayoutLabel` asks anything about the parking marking (*Can Be Chosen in Full
Autonomy*) when it paints. The marking is visible in the editor and on the notice surfaces, and not on
the running diagram.

**What it needs.** The same path the blocked-edge treatment uses, asked of
`AutonomySession`'s parking marking rather than of the block list, on the running diagram only. Worth
settling at the same time: whether it applies while autonomy is stopped, where "manual-only" describes
every square.

### OB-239 - 2026-09-21 - the covered-track paint re-derives the tail's road instead of following it

**Kind:** bug  
**Raised from:** reading around MT-438, 2026-09-21  
**Filed:** 2026-09-21  

**Found by reading while trying to reproduce MT-438; NOT shown to have caused it.**

`AutonomySession.routesCoveredByStandingTrains` takes the edges the railway says a standing train
covers, throws away the edges and keeps only their endpoint SQUARES, and then `walkBackFrom` steps from
square to square by asking `pathBetween(at, candidate)` for the first reduced edge that joins the pair
in either direction.

Two consequences, both on the misleading side:

- **A step nothing covers.** The walk takes the first covered endpoint it can find a path to. Where a
  train's covered chain is A to B to C, and the reduction also holds a direct A to C road, the walk can
  step A to C and paint that road - track no covered edge names, and track the railway would let
  another train onto.
- **The wrong road of two.** `pathBetween` returns the FIRST matching reduced edge. Where two sensors
  are joined by more than one road, which one gets painted is the order of `reducer.getEdges()`.

**The railway's own walk does not have this problem**: `Layout.walkOneTail` follows `arrivedFrom` and
the recorded road for the first hop and then refuses to guess at a fork. The paint re-derives what the
walk already knew.

**The fix is to stop re-deriving it.** Every covered `Edge` carries its own places -
`Edge.getPlaceIds()`, written by `GraphReducer.placesAlong` - so the painted squares can come from the
covered edges themselves, in order, and then the two answers cannot differ by construction.

**What it needs to ship**: a fixture with two roads between one pair of sensors, which no scenario in
`test/layouts` has today - a claim that goes red on the road it paints rather than on how many squares.
**Swept 2026-09-21 and it does not fire on your railway.**  127 placements - every point as the
standing square, every incoming edge as the recorded road, forty-unit tails, every tile measured at one
unit - and the paint never once left the edges the railway itself refuses.  So this is a hazard in the
code rather than something you are seeing: `pathBetween` would have to find a reduced edge joining two
covered endpoints that no covered edge joins, and your geometry does not offer one.  It stays open
because the next page you draw might.

### OB-245 - 2026-09-21 - a cancelled customization copy can leave the custom flag set

**Kind:** bug
**Raised from:** VD12-C3, a validation of 2026-09-20 and 2026-09-21
**Filed:** 2026-09-21

`isCustomFunctions()` is DERIVED - any custom icon makes it true whatever the stored flag says - so
`applyCustomizations` deliberately writes the flag back only when the answer would otherwise be wrong.
Take a locomotive whose stored flag is false and which has one custom icon: the capture records
`custom == true` (the derived answer), Copy Customizations sets the flag true, and Cancel then sees
`isCustomFunctions()` already true and writes nothing.  Delete that icon later and the locomotive reads
as customized for ever.

**What that costs:** `syncWithCS2` never adopts the Central Station's function types for a locomotive
whose flag is set (`loc.functionTypesMismatchIgnoredUI`), so it silently stops tracking the station.
`MarklinSimpleComponent` stores the derived answer and the loader writes it back as the raw field,
which is the second half of the same confusion.

**Not changed**, because the fix wants a test with an icon set first, and because the right answer may
be to stop storing a derived value at all rather than to patch the Cancel.

### OB-247 - 2026-09-21 - thirteen finding ids in the records lead nowhere, and nothing will notice

**Kind:** bug
**Raised from:** VD12-R10, a validation of 2026-09-20 and 2026-09-21
**Filed:** 2026-09-21

`AR-17` to `AR-23` are cited as the **From:** of seven MT entries and `LR-1` to `LR-6` in `issues.md`;
the catalogue holds `AR-1..AR-16` and no `LR-` row at all.  `testEveryCitationResolves` walks `src/`
and `test/` only, so the guard that exists to keep every citation resolvable cannot see the records -
which are the documents a reader actually follows.

The rule the review deletion was made under is *"no reference will ever be stale or lost"*, and it is
already false inside `tests.md` and `issues.md`.

**Two ways.** (a) Widen the guard to `docs/` and put them on the dead-citation ratchet, which is
currently EXACT at 45 and asserts EQUALITY - so it lands red until the new number is MEASURED rather
than guessed.  It is not 58: `AR-19` is already on the roll, and a docs-wide scan with the guard's own
pattern finds more than these thirteen - `REV-B2`, `FCR-B1` and `WP-C19f` among them (VD13-R7). (b) Resolve them: find
what `AR-17..23` and `LR-1..6` were (both rounds are in git history) and either add the rows or correct
the citations.

(b) is the honest one and it is an afternoon's reading; (a) takes ten minutes and records the loss.
Your call which is worth it.

### OB-249 - 2026-09-21 - the catalogue reads one column two ways

**Kind:** bug
**Raised from:** VD12-R16, a validation of 2026-09-20 and 2026-09-21
**Filed:** 2026-09-21

`catalog-findings.py` has two paths into a finding's description.  `fromTheTable` excludes `where` and
`note` from the disposition vocabulary (`DISPOSES[:-2]`); the table-only path includes them.  So in a
document whose findings have sections, a `| id | where |` cell is evidence; in one whose findings are
only table rows, the same cell becomes the DISPOSITION - and `described == disposed` then blanks the
description, which is exactly the fault that lost 200 D findings their text and that the column-aware
parser was written to fix.

**It is not firing today**: all 21 table-only rows have dispositions that really do read as verdicts.
It fires on the next round whose review uses a two-column `| id | where |` or `| id | note |` table,
and it fires at the moment the documents are deleted - which is the only moment it matters.

Worth knowing beside it: `--add` calls `prune_findings`, which deletes the deliberately-set `status`
and `status_note` of any ref the re-scanned document no longer makes.  Re-running `--add` over an
edited folder therefore discards answers `behaviour.md` advertises as set deliberately, with nothing
said.

### OB-250 - 2026-09-21 - an s88 route condition can read a train at a sensor it has not reached

**Kind:** bug
**Raised from:** VD13-C2, a validation of the 2026-09-21 fixes
**Filed:** 2026-09-21

`Layout.whereTheTrainIs` was written on 2026-09-21 because four readers wanted "where is the train" and
three of them had worked the answer out separately: the last MILESTONE, falling back to whichever Point
holds the locomotive.  The fourth was the diagram's tail overlay, and it is fixed.

**There is a fifth, and it decides when a route FIRES.**  The s88 arm of the static `Route.evaluate`
(`Route.java:364-372`) asks the
layout's `getLatestMilestoneS88` for the sensor a train has most recently reached - that method has no fallback of its own, and returns null - and then falls back itself to
`getLocomotiveLocation`'s s88 - and that fallback is the arbitrary answer: while a path is locked the
locomotive is the occupant of every Point on it, so an s88 condition can be told the train is at a
sensor it has not reached yet, or has long left.

**What it costs.** A route with an autoloc condition fires, or fails to fire, on a place the train is
not.  This is the shape of MT-438 moved from the picture to the railway's behaviour, which is why it is
filed rather than changed in passing: it alters when ironwork moves, and it wants a test with a real
locked path before and after.

**Also not on the shared rule**, and harmless: `AutonomyEditorPanel`'s standing-train tooltip asks the
raw question.  A tooltip about a standing train is right either way - a train that is not running has
one reservation - so it is left alone and noted here so the next reader does not have to work it out.


### OB-252 - 2026-09-22 - the general sweep's open findings, so they are named somewhere live

**Kind:** bug
**Raised from:** the general sweep of 2026-09-22 (GS)
**Filed:** 2026-09-22

Six Opus reviewers read one area each - the run loop, the editor and its store, routes against
autonomy, persistence and import, threading, and the 51 findings that were already open.  Six
findings were fixed in the commit that filed this, two are deferred until the manual
tests are done, and the Instant Stop question became OB-251, which Adam has since declined.
  The 33 that remain are listed here rather than as an entry each, because
`behaviour.md` says open work belongs in a live document and OB-248 is the entry about findings
that are in none - adding 33 orphans to it would be a poor way to answer it.

The documents are not in the repository, by your ruling.  The store has each finding's evidence:
`SELECT ref, title, status FROM finding WHERE ref LIKE 'GS%-%'`.

- **`GS-B3`** *(deferred)* the tail bookkeeping is one edge behind its own definition, and on a short path it never releases anything
- **`GS-C1`** the departure speed write is not fenced, and a comment above it says it is
- **`GSB-C1`** route.ui.frameNameNotUsable is a second copy of VD12-R7's wrong rule, and VD12-R7 does not name it
- **`GSB-C2`** RouteEditorFrame imports ConditionRows and does not use it
- **`GSB-C3`** nothing verifies the status_note claims that an OB exists - second instance
- **`GSE-C1`** the `setupChanged` guard reads the enclosing 820-line method, so `buildTileMenu`'s inline writers exempt each other
- **`GSE-C2`** an externally renamed page is reported as one that failed to load, and nothing reconciles for the rest of the session
- **`GSE-C3`** the "why is it not moving" worker iterates the live store collections off the event thread
- **`GSE-C4`** demoting a station destroys five settings with no warning, and `viewer-editability.md` says the gesture changes two things
- **`GSE-C5`** the menu door replaces a caption the drag door refuses to replace
- **`GSP-B2`** A setup that will not read is reported as a layout folder that cannot be written
- **`GSP-C1`** A locomotive database that reads as something other than a List loads as "loaded", and the exit save writes emptiness over it
- **`GSP-C2`** The page shifts move the tiles and leave the unmodelled elements behind
- **`GSP-C3`** `fileNameTaken` names two doors and there are three
- **`GSP-C4`** `readShared` has two callers and one rollback, and the pre-edit note is the caller without it
- **`GSR-B2`** the fourth writer past `runningRouteDriving`
- **`GSR-B3`** the running flag belongs to an object the edit throws away
- **`GSR-B4`** a deleted route's name is still a live target
- **`GSR-B5`** a dropdown that cannot show the value in the cell rewrites it
- **`GSR-B6`** the guard is asked before the dialog, and the thing it guards against starts on its own
- **`GSR-C1`** the one target the editor does not range-check
- **`GSR-C2`** reservation read as occupancy, then described as standing
- **`GSR-C3`** the ROUTE dropdown offers the route being edited
- **`GSR-C4`** a re-trigger while the route runs is dropped in silence
- **`GST-C7`** quitting while autonomy is running skips the exit capture of the session's placements in silence, and `autosetup.log.placementsNotSaved` already exists to say so
- **`GST-B1`** *(deferred)* nothing in `Layout` asks whether the track is live before it commands a speed - the halt half of this is declined by Adam's ruling in OB-251, the POWER half is a different control and stands
- **`GST-B2`** `GraphLocAssign.commitChanges` discards `moveLocomotive`'s answer and writes five locomotive fields past its guard
- **`GST-C1`** The volatile sweep did not reach `Layout.isValid`, `Locomotive.speed`/`direction`/`trainLength`/`reversible`, or `Feedback.set`
- **`GST-C2`** `forwardLoc`/`backwardLoc` touch Swing off the EDT and re-read `activeLoc` inside the worker
- **`GST-C3`** `Layout.getTimetable()` is the live list, beside a synchronized snapshot door
- **`GST-C4`** `repaintTimetable`'s comment justifies itself with a mechanism `executePath` closed
- **`GST-C5`** `createPoint`/`createEdge` are the unsynchronized, unguarded siblings of the delete doors
- **`GST-C6`** `getInvalidReason()` has no callers, and its javadoc describes a surface nothing implements


**And the round that validated all of this left nine of its own** (VD17, 2026-09-22 - three Opus
reviewers over the four commits above).  Twenty-four of its thirty-three findings were fixed in the
commit that files this; these are what is left, and none of them needs the railway:

- **`VD17-C4`** the premise the new warning disavows still stands, as the javadoc of the guard it drives
- **`VD17-C6`** the import refusal says "a configuration called X already exists" in the one case it can reach, where it does not
- **`VD17-C9`** two stale hard-coded finding counts in `triagedb.py`, disagreeing with each other
- **`VD17-R10`** `tests.md` MT-471 step 5: right answer, wrong rule
- **`VD17-R11`** `src/.../TrainControlUI.java:6111` and `:6130`: the gate method still says it has two doors
- **`VD17-R12`** Adam's note is corrected inside the quotation marks in two javadocs
- **`VD17-T6`** (medium) - the records class promises coverage it does not have, and the README's one unchecked piece of arithmetic sits in the paragraph it does chec
- **`VD17-T8`** (low) - `testStartAsksBeforeItDispatchesAnything` is strictly subsumed and carries a refuted rationale
- **`VD17-T9`** (low) - the hand-door triples pin the gate *between the modal prompt and the dispatch*, not "before the dispatch"

**And the round that validated THOSE fixes left six** (VD18, 2026-09-22 - two Opus reviewers over
the last three hours of commits).  Sixteen of its twenty-two were fixed in the commit that files
this, and one of those was a commit of mine reverted whole: it had turned a road the operator had
shut into an open one.  What is left:

- **`VD18-B3`** Mass Assign Lengths still cuts pieces at `isSwitch()`, so a piece straddles a permanent turnout while the room walk stops at one
- **`VD18-C2`** the arm checkboxes show both arms open where the menu offers one way
- **`VD18-C4`** `Edge.crossesASwitch` and five statements of the rule still say "switch" where a permanent turnout now bounds too
- **`VD18-R3`** a bullet's line citations were wrong in the commit that wrote them
- **`VD18-T5`** the menu will not offer `BOTH` on a permanent turnout while the arm mask will store it
- **`VD18-T9`** (low) - a failure message whose remedy the assertion will not accept
**The three I would take first**, and none of them needs the railway: `GSR-B2` (a locomotive's
address is rewritten by the Central Station sync past the guard that refuses it while a route is
driving that locomotive), `GSR-B3` (`editRoute` replaces the route object, so `isExecuting` -
and the guard that reads it - forgets a route that is still running), and `GSP-B2` (every reason
a setup will not load is reported as "it needs a local layout folder", which `isUsable()` has
already ruled out two lines earlier).

### OB-253 - 2026-09-22 - a route import that parses can still destroy routes

**Kind:** bug  
**Raised from:** review finding FP-B3, 2026-07-27, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

`importRoutes` deletes every existing route and then adds the parsed ones.  Parsing first is what
makes an UNREADABLE file safe, and a test pins that.  It does not make a READABLE file safe: `newRoute`
refuses a route whose id or name is already taken, logs `route.alreadyImportedSkipping` and returns
false, and `importRoutes` logs `route.notAdded` and carries on.

So a file holding two routes that share an id or a name imports as: **everything the operator had is
deleted, and one of the two is dropped**, with nothing but a log line to say so.

**Checked against today's code, 2026-09-22.**  `MarklinControlStation.importRoutes` still deletes in a
loop over `routeDB.getItems()` before it adds, and both refusal paths in `newRoute` still return false
after a `logf`.

**What would settle it:** either refuse the whole file when it holds a duplicate - the parse already
walks it, so this costs one pass - or keep the existing routes until every add has succeeded.  The
first is the one that matches the parse-first rule already in place.

### OB-254 - 2026-09-22 - auto-save on exit is forced on and its checkbox hidden, so autonomy.json is rewritten for somebody who turned it off

**Kind:** bug  
**Raised from:** review finding RGN-C1, 2026-08-31, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

`TrainControlUI` sets `autosave.setSelected(true)` and `autosave.setVisible(false)` on start-up, and
the comment says the stored preference is *"deliberately ignored rather than read"* on the reasoning that
turning it off only ever meant losing work.

**That reasoning covers what the flag SAVES; it does not cover what it PREVENTS.**  On the legacy path
the same flag gates rewriting `autonomy.json` from the in-memory graph, so somebody who had turned
auto-save off - and whose file is therefore something they are keeping - has it rewritten anyway.

**Checked against today's code, 2026-09-22.**  Both lines are still there, and the checkbox is still
built and then hidden.

**Your call rather than a defect:** either the preference is read again, or the two things the one flag
does are separated so the graph rewrite has a gate of its own.

### OB-255 - 2026-09-22 - a UIState.data written by 3.0.0 with a non-default page count loses its page names under 2.7.4c

**Kind:** bug  
**Raised from:** review finding RGN-C4, 2026-08-31, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

2.7.4c restores the trailing page-names map only when `saveStates.size() > NUM_LOC_MAPPINGS`, and
that constant is fixed at 10.  This version's page count is a preference between 2 and 50 and the file
still carries exactly one trailing entry, so a user who deletes a page writes nine pages plus names -
ten entries - and 2.7.4c evaluates `10 > 10` as false: no page names, no active page, no active button,
and the empty map serialised back over them on exit.

**Downgrade only, and clean in both directions at the default ten pages** - which is the only count a
2.7.4c installation can have.  This version reading a 2.7.4c file is fine: `!saveStates.isEmpty()` is
what it asks, and that replaced the count comparison precisely because it was fragile.

**Not fixable here**, which is why it is a question.  The failing comparison is inside 2.7.4c.  The
choices are to write a file shape the old version reads, or to say in the release notes that a downgrade
loses page names.  The second is a sentence; the first is a format decision.

### OB-256 - 2026-09-22 - the caption migration is a fourth door past one station, one caption

**Kind:** bug  
**Raised from:** review finding IPR-C1, 2026-08-31, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

`AutonomySession.setCaption` carries the rule and its own comment names the three doors it was
written for: place a station automatically, choose the square in the autonomy editor, drag the square in
the track diagram editor.  Only the first knew to remove the old caption, so the rule was moved into one
place.

**There is a fourth.**  `migrateStationLabels` - the one-time bring-across of captions that used to live
in the layout file - writes `store.setCaption(where, station)` directly, past the rule.

**Checked against today's code, 2026-09-22.**  The migration still runs from `AutonomySession`'s
constructor path and still writes to the store directly.

**What that costs:** a station captioned in the old file and again in the setup comes through captioned
twice, with nothing saying which is current - the state the rule exists to prevent, arriving by the one
door that does not ask it.

### OB-257 - 2026-09-22 - the locomotive icon crop dialog: a saved view restores to a different rectangle, and the clamp's guarantee is in the wrong units

**Kind:** bug  
**Raised from:** review findings IPR-C2 and IPR-C4, 2026-08-31, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

Two findings about the same dialog, filed together because they are one afternoon's work.

**A saved crop view restores to a different rectangle when the dialog has been resized (IPR-C2).**  The
five numbers saved are `centerX, centerY, zoomFraction, frameAspect, frameSize`.  The centre is
panel-independent; the crop SIZE is not - `sourceRect().width` is `cropWindow().width / getScale()`, and
both `largestWindow` and `fitScale` take their own `min` over the panel.  So the same saved view
reopened in a differently-sized dialog crops a different part of the photograph.

**The clamp's overlap guarantee is in panel pixels (IPR-C4).**  `clampCenter` keeps
`min(24, min(w,h)/3) / scale` source pixels of the photograph under the frame; the numerator is in PANEL
pixels, so the guarantee falls below one source pixel once the scale passes 24 - any source under about
682 x 442 at full zoom.  **This half is a pointer**: the reviewer verified the units and did NOT
re-derive the rounding-to-zero its scenario depends on, and said so.  It needs a source smaller than the
icon and a drag to the exact bound, so it is narrow either way.

**Checked against today's code, 2026-09-22.**  `copyViewInto` still saves those five numbers and none of
them is the panel size; `clampCenter` still divides a panel-pixel number by the scale.

### OB-258 - 2026-09-22 - the copy mark is filled white whatever ink it is given, so it disappears on a selected row

**Kind:** bug  
**Raised from:** review finding IPR-C3, 2026-08-31, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

`RowIcons.copy(int, Color)` draws its outline in the ink it is handed and then fills the inner
rectangle with `Color.WHITE` unconditionally.  On a selected row the mark is drawn in the selection's
own ink - which the arrows and the trash already do, deliberately, so they stay visible against the
look-and-feel's selection blue - and the white fill then covers most of it.

**Checked against today's code, 2026-09-22.**  `RowIcons.java` still fills with `Color.WHITE` two lines
after setting the given colour.

**It is one line:** fill with the row's background rather than with white, or leave the inner rectangle
unfilled.  Cosmetic, and it is the mark on the row somebody has just clicked - the one they are most
likely to be reaching for.

### OB-259 - 2026-09-22 - liftAboveLabels was made unnecessary by OB-159 and left in doing only its harm

**Kind:** bug  
**Raised from:** review finding DAY-C1, 2026-08-31, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

`liftAboveLabels` exists for one reason, given in its own javadoc - your *"make sure it renders on
top of the S88's"* - and answers it by pushing a tile to component index 0 while a train is moving on it,
then handing the front back to the station captions.

OB-159 replaced that mechanism: `newDiagramContainer` paints its children and then asks every
`LayoutLabel` for `paintTrainOverCaptions`, so the locomotive lands over every sibling whatever the
z-order is.  The lift buys the train icon nothing.

**What it still costs** is stated by the code's own comment: address labels are plain JLabels, so
`keepCaptionsInFront` does not rescue them, and a lifted tile paints over their text for as long as a
train sits on the square.

**Checked against today's code, 2026-09-22.**  `LayoutLabel` still calls `liftAboveLabels` and still
defines it, and the comment describing the residual harm is still there.

### OB-260 - 2026-09-22 - deleting a timetable row removes against a stale index, behind a modal dialog

**Kind:** bug  
**Raised from:** review finding D3-C2, 2026-09-09, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

The handler checks `isAutonomyBusy()`, holds a modal confirmation open for as long as the operator
leaves it, and then calls `getTimetable().remove(index)` - a structural mutation of a plain `LinkedList`
with no lock, while `addTimetableEntry` (which IS synchronized) appends from locomotive threads whenever
capture is on.

`repaintTimetable`'s own comment already names this window as real - *"three of those callers hold a
modal dialog open between the check and the snapshot, so the window is as wide as the operator leaves
it"* - and that fix moved the SNAPSHOT off the event thread and left the REMOVE unguarded.

**Scenario:** the operator right-clicks a row to delete it; while the confirmation sits open a route
trigger dispatches a locomotive with capture on and entries append; Yes then removes against a stale
index, and in the worst interleaving corrupts the list a running timetable is reading.

**Checked against today's code, 2026-09-22.**  The remove is still inside the dialog's branch and still
unsynchronized.

### OB-261 - 2026-09-22 - a third accessory-reading door still creates the accessory on a miss

**Kind:** bug  
**Raised from:** review finding IND9X-C3, 2026-09-09, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

The rule is *"reading one must still not create it"*, written after a paint loop put 2048 phantom
switches into the live database.  Two of the three lookers were converted; `Locomotive.waitForAccessoryState`
- a public API door used by automation scripts - still polls `getAccessoryState` in its wait loop, and
that registers on a miss.

One phantom row is harmless by your own counter doctrine.  **The finding is the rule enforced at two
doors of three**, which is this codebase's commonest defect shape.

**Scenario:** a user script waits on a mistyped address; a phantom accessory appears, and the wait never
returns with nothing logged.

**Checked against today's code, 2026-09-22.**  `waitForAccessoryState` still calls `getAccessoryState`
inside its `while`.

### OB-262 - 2026-09-22 - two dialogs open unowned, and can fall behind the main window

**Kind:** bug  
**Raised from:** review findings IND9X-C4 and IND9X-C5, 2026-09-09, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

Two findings with one cause, filed together.

**The save-reconciliation warning (IND9X-C4).**  `AutonomyViewerPanel.save()` parents `AutonomyReport.show`
on `this` - a panel that is built but never shown.  The panel's own field comment says every dialog in
the file must parent on the main window for exactly this reason, and every other dialog in it does.  It
is reached from page exclusion, import, initialize, duplicate, rename and delete, and it is the one
notice that says a page's settings are at risk.

**The right-click menu's refusals (IND9X-C5).**  Menu actions fire after the popup is torn down, so
`this` has no window ancestor and the dialogs centre on the screen, unowned.  The same dialogs in
`AutoLocomotiveStatus` parent correctly.

**Scenario:** send a train from the diagram with track power off; "power on to start" appears mid-monitor,
is covered by a stray click, and the refusal reads as the command having silently failed.

**Checked against today's code, 2026-09-22.**  `AutonomyViewerPanel` still passes `this`, and
`LayoutRightclickAutonomyMenu` still has seven `showMessageDialog(this, ...)` calls.

### OB-263 - 2026-09-22 - a station caption's rotation depends on what was standing on the sensor when the layout was saved

**Kind:** bug  
**Raised from:** review finding IND9X-C8, 2026-09-09, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

`LayoutGrid.runsNorthSouth` asks `TilePorts.ports(type, orientation, STATE)`, and a FEEDBACK tile's
state is the `zustand` the Central Station saved - 1 if the s88 happened to be occupied at export.  A
feedback tile has one state, so `ports` returns EMPTY for state 1 rather than throwing: a vertical sensor
square then reads as not-north-south and its caption is drawn unrotated, as though the track ran
east-west.  Persistently, per file, and differently for two identical vertical stations.

The `catch` fallback's comment blames *"a tile type the port table does not describe"*; the real failure
returns empty and never reaches the catch.

`AutonomySession.labelSides` documents this exact trap and asks `graph.getRoutes(tile)` instead -
`runsNorthSouth` is the surviving sibling of that fix.

**No fixture can see it**, which is why nothing caught it: every hand-built fixture is orientation 0,
horizontal, state 0.

**Checked against today's code, 2026-09-22.**  `runsNorthSouth` still passes `c.getState()`, and
`TilePorts.ports` still returns `Collections.emptyList()` when the state is past the end of the table.

### OB-264 - 2026-09-22 - three coverage gaps against rules behaviour.md states

**Kind:** bug  
**Raised from:** review finding IND9X-C7, 2026-09-09, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

Three absences, each confirmed by search rather than by reading a test.

- **The `isRunning()` half of the section 6a guard has no test.**  All three refusals - rename point,
  delete point, delete edge - are exercised against `isStagingInProgress` and none against a live run,
  which is the other half of the OR the guard is written as.  Re-checked 2026-09-22: the claim in
  `testHomeStaging` still asserts `!isRunning() && !isStagingInProgress()` as a precondition and never
  sets a run going.
- **Nothing asserts the covered mark survives an accessory highlight flash.**  behaviour.md section 5c
  states the rule - both marks are applied on every paint so a highlight cannot take either away - and
  the test that covered it went with the wash it was written against.
- **One section 4 limit is code-only.**  A recorded `arrivedFrom` naming a side no track leaves by - a
  stale value after an edit - stops the tail walk entirely, blocking nothing even where the geometry is
  unambiguous.  The code says so at the break; no document does.  Re-checked 2026-09-22: the comment is
  still there and behaviour.md still does not carry the limit.

### OB-265 - 2026-09-22 - the rest of the dispatch-choice layer is documented only in user prose

**Kind:** bug  
**Raised from:** review finding IND9X-C2, 2026-09-09, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

**Half of this is now done, and the half that is done was the load-bearing one.**  The finding was
that the concurrency cap, station priority, the longest-idle preference, the minimum and maximum delay
and atomic routes all decide which train goes where and when, and that behaviour.md - whose head claims
*"the features that decide where trains may go"* - said nothing about any of them.  It named one
consequence that was stated nowhere: the cap is fenced on `isAutoRunning()`, and Return Home runs under
that flag, so the cap binds Return Home moves.

Since then behaviour.md section 1 has gained the cap, the 0-means-no-cap rule, the hand-dispatch
exemption, and exactly that Return Home consequence with the note that the fence is wider than "full
autonomy"; section 5d has gained atomic routes.

**What is left** is station priority, the longest-idle preference and the minimum and maximum delay -
still described only in `Automation.md`, in user prose that is not written to be read against the code.
They decide which train is chosen and where it is sent, which is what section 1 is about.

### OB-266 - 2026-09-22 - the invalidation message shown is the last one, not the first

**Kind:** bug  
**Raised from:** review finding FP-C6, 2026-07-27, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

`Layout.invalidate(String)` overwrites `invalidReason` and `lastError` on every call, and the point
loop keeps parsing after invalidating rather than returning.  So a configuration with three mistakes
calls it three times and the operator is shown the THIRD - usually the one furthest from the cause.

**A related confusion has since been fixed and this one has not.**  `invalidReason` was split out of
`lastError` because `lastError` is written by every path that fails for any reason, so the invalidation
message was being replaced by whatever path failed most recently.  That fixed which FIELD is read; it
did not change that the field holds the last of several invalidations rather than the first.

**Checked against today's code, 2026-09-22.**  `invalidate` still assigns both fields unconditionally.

**What would settle it:** keep the first message rather than the last, or collect them and show all of
them - the loop already visits every point, so nothing has to be read twice.

### OB-267 - 2026-09-22 - two test-harness leaks: a tab left selected on a failure, and a restore that does not restore one thing

**Kind:** bug  
**Raised from:** review findings VD12-T9 and VD12-T10, 2026-09-21, re-checked 2026-09-22  
**Filed:** 2026-09-22  

Two small findings about test hygiene, filed together because they are the same afternoon.

**A tab left selected on a failure (VD12-T9).**  `findSomewhereToType` changes the selected tab and the
restore is the last statement rather than a `finally`, so a failure leaves a different tab showing for
whatever runs next in the same JVM - where the same keystroke means something else.

**A restore that does not restore one thing (VD12-T10).**  The `finally` re-parses the setup's JSON and
`parseAuto` deliberately carries the visit history forward, so the arrival the test noted survives into
the restored layout.  Nothing reads it today; the class's own comment claims the setup is put back, and
for this one piece of state it is not.

Both are cross-class leaks of the kind that make a suite's result depend on its order, which is what
`DEBUG_SIMULATE_PACKETS` did in the same round.

### OB-268 - 2026-09-22 - the turning-copy invariant is measured with two different rulers

**Kind:** bug  
**Raised from:** review finding REV9-C2, 2026-09-09, recovered from git and re-checked 2026-09-22  
**Filed:** 2026-09-22  

Section 3 states, as the reason a compulsory turn is not a question, that *"a turning copy's only
outgoing edges leave by the side the train arrived from"*.  The builder guarantees it in the metal
vocabulary.

The only test that examines reversing copies on the wired railway,
`testTheTurnRuleDoesNotChangeTheRealRailway.testAReversingCopyCanBeLeftByAnotherSide`, computes the
in-side from the STORED entry side and the out-side from `entrySideOf(leaving, copy)` - which, for an
edge whose START is the copy, always falls through to the geometric `sideTowards` fallback.  So its
counter-examples measure the curve between the build's side and the neighbour's compass position, not a
turning copy with a genuine onward edge.

The test is right to block the geometric narrowing, which is what it was written for.  **What is wrong
is what it reads as**: its name and its assertion read as a refutation of the section 3 sentence, and its
closing message invites a future author to re-measure with the same mixed ruler.  Meanwhile the metal
invariant is pinned only on a small fixture.

**Checked against today's code, 2026-09-22.**  The test still takes both sides from `entrySideOf` and
its message still says *"re-measure before assuming either way"*.

### OB-283 - 2026-09-23 - Keep the current direction can end a journey net-reversed where it passed a compulsory-turn square

**Kind:** bug  
**Raised from:** OB-190  
**Filed:** 2026-09-23  

Adam's call rather than a defect, carried until now only on OB-190's Inbox body, which the certified-entries clear-out of 2026-09-23 removed (DCN-C14). A journey that passes a compulsory-turn square is turned there, invisibly to the operator, so at a may-reverse destination the answer "Yes - keep the current direction" can leave the train net-reversed: measured 2026-09-08, keep ends backward and reverse ends forward. Section 3 of behaviour.md promises the opposite. The question: should keep and reverse be measured against the way the train set off, or against the way it arrives?

### OB-284 - 2026-09-23 - A paste or Place where the train's heading is one only a barred copy holds turns it round

**Kind:** bug  
**Raised from:** OB-270  
**Filed:** 2026-09-23  

Adam's call.  At a square where trains may not arrive from one side - BottomMainA, arrivals from the east barred - a train can face the barred way: reversed there on the throttle, or turned by the Facing menu, it now stands on the copy facing that way (the copy is the direction; 8370abb1).  But a paste, the editor's Place and the Place Locomotive dialog choose only among facings a train may arrive in (OB-270: 'we shouldn't allow an impossible facing to be saved'), so the same train cut and pasted back onto the same square is turned round - against OB-270's other half, 'no train should inadvertently change direction when pasted'.  The question: should a placement keep a heading only a barred copy holds (the train then stands where autonomy will not start it, and says why), or keep choosing a way trains may arrive in?  (AUT2-A1's second half, AUT3-B2, DCN3-B1.)

### OB-285 - 2026-09-24 - A train turned where it stands is cleared out over its own tail without looking for another train's tail

**Kind:** bug  
**Raised from:** AUT2-C2  
**Filed:** 2026-09-24  

Found while fixing AUT2-C2, and filed rather than changed, because it makes the running railway refuse more.  `Layout.isPathClear` asks three questions of each piece of track a route runs over - whose tail was traced along it, whose tail lies on track sharing its metal, and whose tail lies on the squares it runs over - and stops at the first answer.  Where that first answer is the moving train itself, the piece is passed and the other two are never asked.  That happens to a train standing on the copy that faces the way it will leave, with its tail on that same side: it came in from that side and was then turned where it stands - reversed on the throttle, turned with the Facing menu, or turned at a square trains may turn at.  Its way out runs over its own tail, and another train's tail on that stretch is not seen.  **Measured on the frozen railway, 2026-09-24** (a probe, not kept): 10 such cases on 4 squares, every one cleared.  The clearest: a train comes into Tunnel from the south, is reversed on the throttle, and is sent back south towards BottomMainAPre; a train in TunnelLeftPark, TunnelCenterPark or TunnelRightPark whose tail sticks out across the points at `1 - Main:7,8` to `7,11` is on that way out, and the moving train is cleared into it.  The standing train needs 3 units to reach those points, against those parks' allowance of 2, so it gets there only by hand, with a wrong or missing length, or by stopping short.  The same shape at TopR1ParkShort and TopR1ParkLong (for a train leaving `1 - Main 1,3` south; 5 and 4 units against allowances of 4 and 3) and ButtomLongestPark (10 against 9).  The planner now asks every train on a piece of track (AUT2-C2) and mirrors this exit on purpose, so that it never refuses a move the railway would make.  The question: should the railway refuse that move too?  If yes, `isPathClear` and the planner change together, with a claim at Tunnel.

### OB-286 - 2026-09-24 - Highlight on Diagram can wash one three-way in both colours

**Kind:** bug  
**Raised from:** GUI2-C4  
**Filed:** 2026-09-24  

Cosmetic, rare, and deferred rather than worked: claiming it needs a three-way on a test diagram and the route editor open over it, which no fixture has yet.  A route that commands one of a three-way's two decoders and has a condition on the other lights the one tile in both colours - commanded and checked - and counts it twice, because the rule that a tile both commanded and checked is drawn as commanded is applied per address, and since GUI-C5 a three-way answers to two.  The fix is to decide it per tile in `TrainControlUI.lightWhere` (commanded first), or to fold a three-way's two addresses together before the rule in `RouteEditorFrame`.

### FR-098 - 2026-09-24 - Grey out Apply in Customize Function Icons when there is nothing to apply

**Kind:** feature request  
**Raised from:** MT-466  
**Filed:** 2026-09-24  

Adam, on MT-466, 2026-09-22: *"there is no cancel button if you go to manage locomotive -> customize function icons, only apply- and closing without clicking on apply still persists the functions here.  That's OK, but just make sure apply is greyed out if there is nothing to apply."*  MT-466's note of the same day said this was filed; it was not, and the 2026-09-24 pass over the waiting tests found it missing.  It needs the dialog to know whether anything has changed since it opened, a flag it does not keep - and greying Apply while there IS something to apply would lose work, so it is to be done with a test for both directions.

### OB-287 - 2026-09-24 - MT-464's automated test was asked for and only half built: the refusal to delete or rename a locomotive a running route drives

**Kind:** bug  
**Raised from:** MT-464  
**Filed:** 2026-09-24  

Adam, on MT-464, 2026-09-22: *"make an automated test for this"*.  `core.testAdvancedRoutes` holds the half about the route finishing its commands (CS3-B1).  The refusal itself - `TrainControlUI.refuseWhileARouteDrivesIt`, at both the delete and the rename door, naming the route - has no test.  Found by the 2026-09-24 pass over the waiting tests; MT-531 is the part of MT-464 still for his hands.

### OB-288 - 2026-09-24 - erronous autonomy editor warning

**Kind:** bug  
**Raised from:** MT-542 (Test a Path keeps its route when Path Type changes, and changes only the note)  
**Filed:** 2026-09-24 07:28  
**Build:** commit 107f54ba, build\classes, compiled 24 Sep 06:22 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

<station> can refuse trains that would otherwise fit shows up on all berths, even though we have measured the s88 tile to match the berth max train size. likely an artifact from before we included the station length in the measurement.

### OB-289 - 2026-09-24 - spacing below "visible elements" in autonomy editor

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-24 07:41  
**Build:** commit 107f54ba, build\classes, compiled 24 Sep 06:22 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

there is slightly too much spacing/padding below "visible elements" in autonomy editor.  make it be consistent with other labels

### OB-290 - 2026-09-24 - orange line for 75 407 DB

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-24 07:59  
**Build:** commit 107f54ba, build\classes, compiled 24 Sep 06:22 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

in the CURRENT setup, 75 407 DB gets no orange line at bottommaina.  it did earlier

### OB-291 - 2026-09-24 - max train length forgotten if station demoted

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-24 10:24  
**Build:** commit 2baafd8a, in English - build\classes, compiled 24 Sep 10:20 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

make max train length be remembered if a station is changed to a non-station, and then restored if it is changed back to a station.  don't modify the behavior of this attribute: it is still to be ignored for non-stations.

### FR-099 - 2026-09-24 - prettify usage graph

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-24 10:46  
**Build:** commit 2baafd8a, in English - build\classes, compiled 24 Sep 10:20 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

usage graph uses basic/ugly graphics.  Keep current functionality, but make it look nicer.

### OB-292 - 2026-09-24 - a new straight that would join two tracks is not turned to join them

**Kind:** bug  
**Raised from:** Adam, in conversation - not from a particular test  
**Filed:** 2026-09-24 11:16  
**Build:** commit c3efc506

Adam, 2026-09-24: *"when new straight tracks are placed in the autonomy editor and they would connect two other tracks, they are automatically oriented to connect rather than not."*  Today a straight is placed at its default orientation, so dropping one into a gap between two pieces of track can leave it lying across the line, joining neither, until it is turned by hand.

### FR-100 - 2026-09-24 - where is the tail visual selection

**Kind:** feature request  
**Raised from:** MT-557 (A tail question with one answer is not asked)  
**Filed:** 2026-09-24 11:48  
**Build:** commit fa71135f, in English - build\classes, compiled 24 Sep 11:44 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when asking about the tail, instead of showing the list of points, highlight possible squares on the diagram and ask the user to click one.  only show the list if there are options on another page.

### FR-101 - 2026-09-24 - warning for "nothing can pass" stations

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-24 11:53  
**Build:** commit fa71135f, in English - build\classes, compiled 24 Sep 11:44 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

these show "no train can reach <point> from any other station....check the direction".  Update the error message to say that is marked for nothing to be able to pass, user to validate if intentional.

### OB-293 - 2026-09-24 - missing tooltips

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-24 13:09  
**Build:** commit d252fc3c, in English - build\classes, compiled 24 Sep 13:02 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

add brief tooltips on what entry guards and exit guards are to their items in the autonomy right click menu

### FR-102 - 2026-09-24 - why not moving convenience

**Kind:** feature request  
**Raised from:** MT-516 (Why Not Moving? on Manual says a station that excludes the train will not take it)  
**Filed:** 2026-09-24 13:46  
**Build:** commit d252fc3c, in English - build\classes, compiled 24 Sep 13:02 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when the button is pressed an nothing is drawn yet, highlight stations w/ trains on the editor diagram so it's clear what the user can click on (consider greying out other things)

### OB-294 - 2026-09-24 - routing edge case - critical

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-24 13:58  
**Build:** commit d252fc3c, in English - build\classes, compiled 24 Sep 13:02 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

EN57-203 from bottomsecondary to lowerfront may run over its own tail. make sure the model factors in whether the train will  clear the area before it crosses over.  right now, If I set the train length to 20, is still allowed to go, even though it would likely hit its own tail.

separately, increase choosable train lengths up to 40 in the dropdown.

### OB-295 - 2026-09-24 - the 'unavailable while occupied' notice describes a standing train on a square trains only pass

**Kind:** bug  
**Raised from:** follow-up from the validation round, TDA-C9  
**Filed:** 2026-09-24  
**Build:** commit b62f1976 and later - found by reading, not on the railway

`63c4fdc0` keeps a restriction on a square made pass-through, and lists every restriction as *"{0} is unavailable while {1} is occupied."*  On a square that is not a station only the half the build writes into lock edges is live: a ROUTE running into the watched square holds it back, and a train STANDING there holds nothing back, because the standing half is asked only of a path's destination, which a pass-through square never is.  So the sentence describes the half that does not apply.  A notice, and the wording is yours; a pass-through variant might read *"... cannot be passed while a train is being sent to {1}"*.

### OB-296 - 2026-09-24 - the right-click Place item stands a train on a random copy of the square

**Kind:** bug  
**Raised from:** follow-up from the validation round, TDU-B4 - older than the range (`bd27c357`, 2026-08-18)  
**Filed:** 2026-09-24  
**Build:** commit b62f1976 and later - found by reading, not on the railway

Three placement doors keep the train's heading and stand it on the copy that faces that way (OB-270, GUI-B1, OB-284).  The fourth - the diagram's right-click **Place {0}** for the active locomotive - still takes one of the square's usable copies at random (`LayoutRightclickAutonomyMenu.placeSomewhereLegal`), so the same train placed the same way faces either way.  behaviour.md already calls random placement superseded by your ruling of 2026-09-12, *"as long as the direction isnt flipped"*.  The question for you: should this door follow the paste's rule, or keep choosing for the operator?

### OB-297 - 2026-09-24 - the own-tail refusal's count of stretches with no length misses one measured only at its switch

**Kind:** bug  
**Raised from:** follow-up from the validation round, TDA2-C1  
**Filed:** 2026-09-24  
**Build:** found by reading, not on the railway

The own-tail refusal's note says how many stretches of the way round have no length, so that measuring them is a way past.  It counts sensor-to-sensor legs, which is coarser than the pieces Mass Assign Lengths asks for - a leg cut at its switches - so a leg whose only length is its switch counts as measured, and between Mass Assign sittings (switches first, pieces later) the note can say fewer stretches than there are, or nothing.  The rule itself is right: unmeasured track refuses more, not less.  Counting pieces needs the build to mark which of an edge's places are switches, which the configuration does not carry today.  Left for later; the rule's javadoc and behaviour.md 5c say so.

### OB-298 - 2026-09-24 - a configuration build and a rebuild on two threads at once raise ConcurrentModificationException

**Kind:** bug  
**Raised from:** a test run in the OB-294 session  
**Filed:** 2026-09-24  
**Build:** found in a test, not on the railway

Seen once, in a test that edited the setup off the event thread while a rebuild ran: `AutonomyBuilder.splitSides` iterated `reducer.getEdges()` while the other rebuild cleared and refilled that list in place (`GraphReducer` keeps one `ArrayList` of edges).  The test was put right - it now edits on the event thread, as the application does - and every door read so far builds and edits there too.  Left for later because the question is whether any door builds off the event thread: the start-up resume and `rebuildRunningLayoutFromSetup` are the ones to check.  If one does, a build could throw mid-way or read half a rebuild.

## What has been picked up

Newest first. This is a receipt for something promoted into `tests.md` - **Became** names its
`MT-###` tag, and its state lives there from then on. Something tracked directly instead - most
feature requests, going forward - has no `MT-###` tag to point at; **State** is its disposition,
in three of the four words `tests.md` uses (`needs test` / `fixed unvalidated` / `fixed
validated` - not `superseded`, which has no meaning for something nobody has coded yet), plus
**pending** for picked up and not built yet and **declined** for something cancelled, set by Claude
and only by Claude, the same rule as everywhere else it appears. Exactly one of
State or Became is filled in for any row - a feature request either gets its own tag, or it does
not, never both.

**How the triage app shows these (Adam, 2026-09-13).** A row that Became a test has no State of its own, so the app shows the state of the tests it became - worst open state wins, so one test still waiting on a run keeps the request open however many of its siblings are validated, and a superseded test is set aside. Became is read in either spelling, a markdown link or a bare code span, and every tag in it counts: the code-span spelling used to be missed, which is why most feature requests showed "-". **Declined rows are hidden from every view** - they stay here, which is the record - and so is the undocumented word "cancelled", which reached the file twice and is now written as declined.

| Filed | Ref | Kind | What | State | Became |
|---|---|---|---|---|---|
| 2026-09-24 | OB-286 | bug | Highlight on Diagram lights a three-way once, as commanded, where a route commands one of its decoders and checks the other. | - | `MT-578` |
| 2026-09-24 | FR-098 | feature request | Customize Function Icons greys Apply while the function on show is as the locomotive holds it. | - | `MT-577` |
| 2026-09-24 | OB-287 | bug | The refusal to delete or rename a locomotive a running route drives has its test, at both doors and the name proposal: `regression.testARouteDrivenLocomotiveIsNotEdited`. | fixed unvalidated | - |
| 2026-09-24 | OB-294 | bug | A train is not sent round a loop into its own tail: the head may come back to a place only once the tail has left it - every tier, both hand doors, and Return Home.  Train lengths go to 40 in both lists. | - | `MT-571`, `MT-572` |
| 2026-09-24 | FR-102 | feature request | While Why not Moving? waits for its click, the squares with trains on them are outlined.  Greying everything else - the "consider" - is not done. | - | `MT-570` |
| 2026-09-24 | OB-293 | bug | The exit- and entry-guard items say what each guard does. | - | `MT-569` |
| 2026-09-21 | OB-235 | bug | One-Way Run is greyed, and disarmed, on a page left out, as Test Route and Why not Moving? are (MT-528's note). | - | `MT-568` |
| 2026-09-24 | FR-100 | feature request | The tail question is asked on the diagram: its sensors lit, a click on one answers it; the list where a sensor is on another page. | - | `MT-565` |
| 2026-09-24 | FR-099 | feature request | prettify usage graph - **for v3.1.0**, on Adam's word of 2026-09-24 (*"FR-099 for later (mark it as for v3.1.0)"*).  Picked up and not built. | pending | - |
| 2026-09-24 | FR-101 | feature request | A station set to No - Nothing Can Pass is said to be, once, in place of the two reachability sentences. | - | `MT-562` |
| 2026-09-24 | OB-292 | bug | A straight put down between two pieces of track is turned to join them. | - | `MT-563` |
| 2026-09-24 | OB-291 | bug | A station made pass-through keeps its maximum train length and has it back when made a station again; while it is not one, the maximum is ignored - not counted by Clear All, and starting no notices. | - | `MT-558` |
| 2026-09-24 | OB-290 | bug | Found: the orange was drawn only from covered edges, and a tail that stops where the rails part behind its platform - any train with no road, which a restart or an edit leaves every train - covers none.  Such a train is now drawn from the places it claims. | - | `MT-549` |
| 2026-09-24 | OB-289 | bug | The gap between Text Labels and Grid stayed when the autonomy editor hid Text Labels; it goes with the box now. | - | `MT-553` |
| 2026-09-24 | OB-288 | bug | A berth whose measured track before any unmeasured square holds its longest train is no longer warned about - no train it takes reaches the hole.  On Adam's files, 18 berths down to 3. | - | `MT-552` |
| 2026-09-22 | OB-254 | bug | Adam, 2026-09-24: *"We should only write to the new save format in the layout folder, IMO.  And I can delete the checkbox from the UI entirely when we are ready."*  Then, the same day: *"Remove it, require a local copy for autonomy."*  Adam deleted the Load Autonomy Configuration tab, and the hidden auto-save checkbox with it, in the GUI builder.  Nothing reads or writes autonomy.json any more, on any layout; a layout with no local copy has no autonomy until it is downloaded, and an old autonomy.json is still imported from the Autonomy menu, which now ends with Documentation.  `regression.testALocalLayoutNeverWritesAutonomyJson`, `regression.testTheOldAutonomyTabIsGone`, `regression.testTheAutonomyMenuLinksItsDocumentation`; claims 3cc49293 and 6cc5447f, removal a4674c9b.  Then: *"Yes, open the autonomy menu with everything but those 2 greyed"* - on a Central Station layout the Autonomy menu opens onto the download and Documentation (claims a187cce2, fix cf7b43dc). | - | `MT-545`, `MT-546`, `MT-547`, `MT-548` |
| 2026-09-21 | OB-247 | bug | Adam, 2026-09-24: *"look it up, though likely these are defunct review findings."*  They were not findings at all: AR-17 to AR-23 were ids given to items of his second hands-on round when the manual-test file was made (27261d16), and LR-1 to LR-6 the six of a 2026-08-26 review reported without a document.  Each now has a catalogue row naming the entry that is its only record, so every citation resolves; AR-19 left the dead-citation roll (44). | fixed unvalidated | - |
| 2026-09-21 | FR-093 | feature request | Adam, 2026-09-24: *"I thought we got this through the 'more destinations' option? Then we are OK."*  Checked: the right-click menu puts every destination autonomy would never choose in its own More Destinations submenu (FR-058).  Answered, no change. | declined | - |
| 2026-09-24 | OB-285 | bug | Adam, 2026-09-24: *"Add the refusal"*.  A train's own tail no longer ends the check for other trains' tails on its way out, on the railway (`Layout.isPathClear`) or in Return Home's planner: the train turned at Tunnel is refused its way south while a train in TunnelRightPark lies across the points.  `core.testATurnedTrainIsNotSentIntoAnotherTail`. | - | `MT-495` |
| 2026-09-15 | OB-230 | bug | Adam, 2026-09-24: *"now that I have a measured layout, we should do OB-230"*.  Return Home's estimate counts the moves still needed - each train not home, and each train standing on another's home - and a weighted search takes over when the shortest plan cannot be found in time.  Six trains on his frozen railway now come home in ten moves; four still get the shortest plan.  `core.testReturnHomeFindsAPlanOnAFullRailway`. | - | `MT-492` |
| 2026-09-23 | OB-284 | bug | Adam, 2026-09-24: *"this should check for barred departure directions, not arrival ones.  for barred arrival directions, keep the direction.  for barred departure directions, turn it to a way trains may arrive in"*.  A paste, the editor's Place and the Place Locomotive dialog keep a heading the train can leave by (`AutonomySession.departableFacingsFor`), and turn only one it cannot. | - | `MT-498` |
| 2026-09-23 | OB-283 | bug | Adam, 2026-09-24: *"if it's a compulsory turn, turn it in the forced direction."*  Answered, no change: the code already turned trains at a compulsory turn on the way, and keep and reverse are measured on arrival.  `behaviour.md` section 3 said the opposite and is corrected. | declined | - |
| 2026-09-23 | OB-282 | bug | Found by the refreeze: the pinned Return Home arrangement brought a train homed westbound on BottomMainA back eastbound, because the planner counted any copy of the home square as home (MT-165).  Adam: *"yes, it should accomplish the facing"*.  A home now keeps the facing it was set with - asked for a train standing elsewhere - and Return Home brings the train back on that copy or its turning twin. | - | `MT-486` |
| 2026-09-23 | OB-281 | bug | Five route tiles held a length of 1 from before OB-273, which nothing reads, so each piece measured a unit less.  Adam: *"Fold them, they were likely auto set during the mass assignment run."*  Opening a setup moves a route tile's length onto the track beside it. | - | `MT-485` |
| 2026-09-23 | FR-097 | feature request | Adam, in conversation, asked whether Segment Length's 0 should go on clearing a length: *"no, add a clear button"*; and *"stop listing answered zeros as missing."*  Segment Length's 0 records an answered 0, a Clear button removes the length, and the half-measured, reversal and berth refusal notices leave answered squares out. | - | `MT-484` |
| 2026-09-22 | OB-270 | bug | Adam: *"Trains should not inadvertently change direction when pasted, so a loc going west from bottomsecondary should always face east when pasted on bottommaina."*  A cut train kept the heading it was cut with, because the walk had nowhere to start; it now walks from the square it was cut from, and stands on the copy facing the heading recorded, chosen over copies a train may stand on. | - | `MT-483` |
| 2026-09-23 | OB-280 | bug | Adam, in conversation: *"grey what routing actually refuses.  if the train doesn't protrude past the switch, there should be nothing else to gray."*  The grey was the whole of every covered edge (OB-208); it is the squares the railway claims under standing trains, which is what `isPathClear` refuses a path over. | - | `MT-482` |
| 2026-09-23 | OB-279 | bug | Found by the refreeze: the orange line and the grey skipped every route tile, because a route tile's roads are named by the sides they join and the diagram looked them up in the port map by index.  `TileGraph.transparentRouteOf` decodes them beside the encoder, and `LayoutLabel.routesOf` asks it. | - | `MT-481` |
| 2026-09-23 | OB-278 | bug | Adam, in conversation: *"75 407 DB can't go to TunnelLongPark from BottomMainPost unless it is length 1, whereas up to length 3 should be allowed (both measured and max on the station)"*, and *"the 2 length tile with the s88 consumes 2 units of the train"* - everywhere.  His ruling of 2026-09-13 (*"The station size is an allowance, not a length"*) had been read as the length measured on the station's square; the tail walk, the berth rule and the orange line now spend that square first. | - | `MT-480` |
| 2026-09-23 | FR-096 | feature request | Adam: *"a signal that turns red after arrival at the final designation.  Same UI to set it as the current linked signal exit guard, and multiple selections are possible."*  Built as a second list per station beside the protecting signals, set up through the same dialog, thrown red where a journey's arrival is recorded; nothing turns it green (*"The next route sets it green"*). | - | `MT-479` |
| 2026-09-23 | OB-277 | bug | Adam: *"when we draw orange lines, they don't overlap with sensors"* - on every sensor.  The walk behind the line left sensor squares out on purpose; it now draws the standing square and each sensor the body reaches, along the road facing the track.  The mechanism first filed (a live tile state) was wrong and is corrected on the entry. | - | `MT-475` |
| 2026-09-23 | OB-276 | bug | Adam: *"the tail question lists rampdown twice in the list."*  Two copies of RampDown leaving by one rail were counted as two roads by name; roads are now compared by the places the rail runs over.  21 pairs on his railway, none now. | - | `MT-477` |
| 2026-09-23 | OB-274 | bug | Adam: *"allow a length of 0 as a length that is set deliberately ... same meaning to the model"*.  The walk accepts 0 and stops offering the piece; every length rule reads it as unmeasured; it survives a save and a load. | - | `MT-476` |
| 2026-09-23 | OB-273 | bug | Adam: *"a route tile should not need or accept a length.  it just implicitly connects things as if it were a crossing."*  Route tiles are in no piece, not asked for on their own, and never reported unmeasured.  Five route tiles on his layout still hold an earlier unit each - his call. | - | `MT-476` |
| 2026-09-23 | OB-272 | bug | Adam: *"make text labels be a dedicated setting ... make control+L cycle the options"* - *"the dropdown's 4, plus ... the labels only"*.  Labels Only appended; captions follow the dropdown and the writing the text switch; Control+L steps the five in the autonomy editor. | - | `MT-478` |
| 2026-09-22 | FR-094 | feature request | Adam, on MT-470: *"sounds like we need a bulk tool for locomotive lengths."*  Asked for again on 2026-09-23 in place of FR-095's menu: *"add a bulk tool to the autonomy editor to set missing train lengths, similar to how the station lengths are set."*  Bulk Tools > Mass Assign Train Lengths walks the trains the Atomic Routes refusal names, 1 to 20, through `applyTrainLength`. | - | `MT-474` |
| 2026-09-23 | FR-095 | feature request | Adam: *"when right clicking the train name in the autonomous locomotive commands page, show a right click menu with the edit locomotive option"*.  Withdrawn the same day in favour of `FR-094`: *"Rather than adding complexity through new menus, add a bulk tool to the autonomy editor to set missing train lengths, similar to how the station lengths are set."* | declined | - |
| 2026-09-23 | OB-275 | bug | Adam: *"I thought we were consolidating lengths on tiles with arrows?"*  No such ruling was on record; asked whether to consolidate a piece's length on one square or keep the even share, he chose the even share: *"let's stick to a then, since that is more visually pleasing."*  Answered, no change - `behaviour.md` 5a already carried the rule. | declined | - |
| 2026-09-22 | OB-271 | bug | Adam, from MT-446: *"make the test, capture, etc. buttons in the route editor non focusable."*  `setFocusable(false)` on Test, Highlight, the capture box and its target; Save left focusable, since Enter on it is how a route is saved. | fixed unvalidated | - |
| 2026-09-21 | OB-243 | bug | Adam, MT-438: *"coloring switch 99 and 100 is still an error regardless"* and *"it's almost as if you are shifting the location of the train"*.  A locked path reserves every Point on it, and the tail walk ran from each - one of four same-side roads picked by list order.  Fixed on his ruling, *"the tail is certain at departure and shouldn't change"*: one tail per train, a running one anchored at its head.  `core.testARunningTrainHasOneTail`. | - | `MT-438` |
| 2026-09-21 | OB-242 | bug | Adam, MT-438: *"it departed, so the tail would be gone as of the time of this screenshot."*  Not a stale mark: it was recomputed, correctly, from a reservation - the same cause as `OB-243` and fixed with it. | - | `MT-438` |
| 2026-09-21 | OB-241 | bug | Filed as "the tail walk uses the recorded road for the first hop only", and withdrawn the same day: the premise was wrong - `walkOneTail` has followed the road past a junction since MT-335, pinned by `core.testATailFollowsTheRouteItCameIn`, and a test written first passed before any change. | declined | - |
| 2026-09-21 | OB-240 | bug | Adam, MT-469: *"we can remove operators (like and) without deleting the conditions they are linked to ... Any linked entries should also be deleted."*  Worse than an orphan: a deleted OR silently read as AND.  Deleting a joining word now takes the term it joins, the mirror of deleting a condition. | - | `MT-471` |
| 2026-09-22 | OB-269 | bug | *"when running auto layout, more edges (tracks) may get locked than necessary ... significant bug."*  A lock reached TWO hops, because the write and the read each reach one: `setOccupied` marks every edge in each of a path's edges' `lockEdges`, and `isPathClear` read the flag off every edge in each CANDIDATE's `lockEdges` - so a candidate was refused when a THIRD edge shared rail with it and, separately, with the run, while the candidate and the run shared none.  `Edge.runOver` now counts only rail a path runs over and `isRunOver` asks it; `occupancy` and `isLockHeld` are untouched, so a held throat's accessories stay protected.  Two more rules were needed and each was found by a test rather than by reading: the same rail the OTHER WAY is refused on its own account, found by PLACE because the two directions live on different copies; and a train is never refused rail its OWN reservation holds, which is `behaviour.md` 5c and is what five trains with no way home turned out to be.  Held by `core.testALockReachesTheRailBeingRunOver` - four mechanism claims plus Adam's own list of eleven allowed concurrent journeys, which was red for six of them first and is what caught both extra rules.  Every mutation measured. | fixed unvalidated | - |
| 2026-09-21 | OB-248 | bug | Twenty-nine findings were open and named in no document a reader opens - only in this entry's own paragraph, which is the failure it was about.  Adam, 2026-09-22: *"no idea what 'the twenty-nine' are"*, and then *"re-file the others if they are found and still relevant"*.  All twenty-nine were found: the deleted reviews are recoverable from `77649f88^` and `4020a899^`, and each finding was read out of its own document and then checked against the code as it stands, by reading - nothing was run.  **Six were already closed** by the 2026-09-22 audit (four fixed with their commits, two not defects).  **Three are closed now**: `MON-C12` is the decomposition question `open-questions.md` already defers past 3.0.0, and `VD15-R8` and `VD15-R11` were lost with the message that reported them and name only their own absence.  **Twenty are still true and are now OB-253 to OB-268**, each with what was checked and when - `FP-B3` is the B, a route import that deletes every route and then drops one of two sharing an id.  No open finding is now absent from a live document, which is the claim `testTheRecordsCountTheStore` makes. | fixed unvalidated | - |
| 2026-09-21 | OB-246 | bug | The target column offered every locomotive in the database, including one whose name holds a COMMA - which `RouteCommand.isNameUsable` refuses, because a command line is comma-separated - so the operator picked it and learnt at Save that the only way out was to rename the locomotive.  Adam, 2026-09-22: *"yes"*.  Marked rather than hidden, because hiding it would refuse a legal selection with nothing shown: the row is drawn in the refusal ink with the gate's own reason on its tooltip, as it is typed.  A row nobody has filled in yet is not marked - a mark that is always there is a mark nobody reads.  Held by `testARowSaveWouldRefuseIsMarkedAsItIsTyped`, whose two mutations were each measured. | fixed unvalidated | - |
| 2026-09-21 | OB-238 | bug | The collision block that stops two trains standing on one square was keyed by the TILE, so a train on one arc of a double curve made the other arc read occupied and `isPathClear` refused track that is physically free.  Adam, 2026-09-22: *"it is two pieces of metal.  imagine two parallel tracks simple appearing on one tile for visual convenience.  two distinct, not connected paths."*  `AutonomyBuilder.blockFor` keys DOUBLE_CURVE, FEEDBACK_DOUBLE_CURVE and OVERPASS per ROAD - the grain the reduction has used since AUR-B1 - and every other split square still groups by the tile.  Held by `testEachArcOfADoubleCurveIsItsOwnPieceOfMetal`, seen failing first.  No hands-on test: it is what the build emits, and the claim reads it back out of the emitted configuration. | fixed unvalidated | - |
| 2026-09-21 | OB-244 | bug | A station's allowance - *"the station size is an allowance, not a length"* - was being spent at a running train's last MILESTONE, so a train whose milestone is a long block claimed a square behind it as well and `isPathClear` refused track no train is on.  Adam, 2026-09-22, asked where the maximum train length should be checked: *"it's the arrival station only"* - answer (a).  `walkStandingTrains` now passes `atRest`, false for exactly the trains part-way along a run, and both halves of the rule honour it; a train standing at the end of a road keeps the allowance unchanged.  Held by `testAMidRunMilestoneIsNotAnAllowance`, whose two mutations were each measured.  No hands-on test: it is a calculation. | fixed unvalidated | - |
| 2026-09-21 | OB-233 | bug | The berth-room walk counted straight across a permanently-set turnout, so a berth beyond one was measured from wherever the run began and a train too long for it was admitted, coming to rest fouling the merge.  Adam, 2026-09-22, having ruled the other way on 2026-09-15: *"(c) - treat them the same as regular switches for the purposes of the check"*.  Both walks ask `GraphReducer.boundsTheRoom` now; `isSwitch()` is untouched, and a crossing still does not stop the walk.  Held by `testTheRoomWalkStopsAtASwitchAndAPermanentTurnoutButNotACrossing`, seen failing first.  No hands-on test: it is a calculation, and the claim's mutation was measured. | fixed unvalidated | - |
| 2026-09-22 | OB-251 | question | Two reviewers found that Instant Stop halts the trains and leaves autonomy running - a train between paths sets off again, and a train mid-path leaves its thread waiting on a sensor it will not reach.  Adam, 2026-09-22: *"Instant stop is unrelated to autonomy"*, and *"So it's OK to keep autonomy running"*.  So the button is a Central Station halt and nothing else, and the exit capture's `!isRunning()` is right rather than a consequence - placements taken mid-run would record trains half-way along a path. | declined | - |
| 2026-09-17 | FR-092 | feature request | Adam: *"Add a right click menu open to clear all max station train lengths (grouped with the other clear options)"*.  Clear All Max Train Lengths, after Clear All Track Lengths in Bulk Tools; every page, after a confirmation. | - | `MT-457` |
| 2026-09-17 | FR-091 | feature request | Adam: *"add a similar feature to walk stations that don't have a min length set up"* (*"max length"*).  Mass Assign Max Train Lengths in Bulk Tools walks the stations with no maximum, through the Mass Assign Lengths prompt. | - | `MT-456` |
| 2026-09-16 | OB-232 | bug | Adam, on MT-454: *"make sure the entry field is focused"*.  OK was the prompt's initial value and took the focus; both walk prompts now have none, so the field has it, and Enter submits as before. | - | `MT-454` |
| 2026-09-16 | FR-090 | feature request | Adam, on MT-454: *"make sure it remembers its location unless I reopen a new mass assignment round from the right click menu"*.  Each prompt opens where the last was left; a new round opens afresh; Name Everything too. | - | `MT-454` |
| 2026-09-16 | OB-231 | bug | Adam, on MT-454: *"When iterating quickly on the assign lengths popup (like by clicking skip), it does not clear the prior highlights."*  Each reveal's yellow flash held 2.25 s and nothing ended it when the walk moved on; `reveal` now ends the last one first. | - | `MT-454` |
| 2026-09-16 | FR-089 | feature request | Adam: *"per stretch, every relevant square a rule reads. build it."*  Mass Assign Lengths in Bulk Tools walks the stretches still needing a length, one whole length each; the Unmeasured Track display highlights the same squares. | - | `MT-454`, `MT-455` |
| 2026-09-15 | OB-229 | bug | Return Home's agreement check blamed the planner for routes the railway's search never found: `Layout.bfs` spent squares on routes through a terminus.  Searched past termini, at Adam's choice. | - | `MT-441` |
| 2026-09-15 | OB-228 | bug | Adam, on MT-335: *"Could not run EN57-203 from BottomInner (northbound) to TopMainR0Park - the path stayed blocked."*  Return Home routed a train over the tail of one it had just parked; the planner now models the tails of trains it moves. | - | `MT-440` |
| 2026-09-15 | FR-088 | feature request | Adam, on MT-435: *"The closest sensor to the back should be the default selection in the length window"*.  Starts on the recorded road, else on the one sensor nearest the back, else nothing. | - | `MT-438` |
| 2026-09-15 | OB-227 | bug | Adam, on MT-435: the tail question offered roads a train could only have reversed along - *"that isn't a realistic path."* | - | `MT-438` |
| 2026-09-15 | OB-226 | bug | Adam, on MT-435: at length 3 Tunnel, three units back, was not offered. | - | `MT-438` |
| 2026-09-15 | OB-225 | bug | Adam, on MT-434: *"in manual mode, I still get reasons like 'tunnellongpark will never be chosen in autonomy'"* - Why Not Moving? ignored Path Type. | - | `MT-439` |
| 2026-09-15 | FR-087 | feature request | Adam, on MT-431: *"didn't we say that BottomMainA should be allowed at length 3"*.  At a station autonomy may choose, the measured route in bounds the train, not the approach alone. | - | `MT-437` |
| 2026-09-15 | FR-086 | feature request | Adam, on MT-397: *"let's add a hotkey for 'show station name here' too"* - Control+N. | - | `MT-436` |
| 2026-09-14 | OB-224 | bug | Adam, on MT-333: *"75 407 DB (len 2) can no longer go from tunnel to bottommaina ... this SHOULD be allowed per the standing rule that this switch blocking should only affect berthes."*  Refused at BottomMainAPre, a square it only passes, by the pass-through room check that arrived in `5948a88a` (2026-09-10) on the ruling of 2026-09-09; the relaxation of 2026-09-12 had been built at the destination only.  Only where a train comes to rest is judged now - the destination and a square it turns at - in the runtime and the Return Home planner.  The protrusion test is re-filed.  `regression.testAPassingTrainMayStandAcrossThePoints` (seen red first), `core.testATrainIsJudgedOnlyWhereItStops`, censuses re-pinned, behaviour.md 5a; `e677cbab`. | - | `MT-431, MT-432` |
| 2026-09-14 | FR-084 | feature request | Adam, on MT-399: *"when changing the auto and manual radio buttons, make it update the shown route to the new selection without having to repeat the button press sequence."*  The autonomy editor remembers the last two squares tested and runs the same test again, through the same door, when Path Type changes - only while Test a Path is still armed, and forgotten wherever the drawn route is put away.  `core.testManualOnlyPathsAreADifferentColour.testSwitchingPathTypeRedrawsTheTestedRoute`, seen red first; `b1cb1fe9`. | - | `MT-434` |
| 2026-09-14 | OB-223 | bug | Adam, on MT-406: *"a one-way run (or any other edits to arrows) persist after I press cancel.  They are not undone by cancelling."*  Every setup gesture in the autonomy editor rebuilds the running layout, and that rebuild loads the configuration, which saves it - clearing the session's unsaved flag, so Cancel never asked and a discard would have re-read a file that already held the edit.  Closing now compares the setup with the snapshot the window took when it opened, and discarding restores that snapshot and writes it; placements come back with it - a train a bulk clear lifted is put back, one that was moved stays where it was moved (WK7-C2) - and Discard when closing the application now does the same (WKV-B2, `testDiscardOnTheWayOutPutsBackTheLocomotivesABulkClearTook`).  `regression.testCancelUndoesAutonomyEdits` - the arrow back in the setup and the file, the question asked, and Save keeping it - seen red first; `244b07c2`. | - | `MT-430` |
| 2026-09-14 | FR-080 | feature request | why not moving clarity - Adam: *"show all stations first, then show berths (non-autonomy stations), both in alphabetical order.  If a point is on another page, show it."*  The autonomy editor's answer asks `explainDestinationsGrouped` - the locomotive panel's own call, one lock - and lists the refused stations under that window's two headings, each alphabetical, filing a station as choosable when any copy of it is; the "can go to" names are alphabetical too, and a station on a page other than the train's carries its page, in eight languages.  `regression.testTheDiagramRefreshDoesNotWaitOnTheRailway.testTheWhyAnswerListsStationsThenBerthsWithTheirPages`, seen red first; `89d3156d`. **Claude, 2026-09-14.** Adam's note on MT-429 - capital Page, and by page then name with the current page first - built; MT-433 checks it; `a48cb5e6`. | - | `MT-429, MT-433` |
| 2026-09-14 | FR-083 | feature request | Adam, on MT-412: *"we should make a FR to track fixing this behavior so that the pre-arrival arrow just matches."*  OB-218's accepted behaviour - the route reserved on the copy the path ends on, so the arrow and badge show the turned facing until arrival - asked to match before arrival too: reserve the copy the train will end on, from the answer at dispatch.  **Claude, 2026-09-14.** Picked up and not built. | pending | - |
| 2026-09-14 | OB-222 | bug | `core.testTrainsComeHomeToTheirPlatforms` fails intermittently: after the random run two trains are boxed in on TopMainR1Inter and TopMainR2Inter and the planner answers NO_PLAN_FOUND after its whole budget.  Adam: *"I think the test will always fail until the railway has realistic track lengths set ... Shall we leave this open pending updates to the live layout that you can then freeze?"*  **Claude, 2026-09-14.** Pending Adam's lengths on the live layout; then `test/operator_layout` is refrozen and the class rerun.  The frozen copy sets a length on 6 tiles only, and every maxTrainLength is 0.  **Claude, 2026-09-23.** `test/operator_layout` refrozen from his measured layout (`e36df979`); the class passed on 3 runs of 3.  The search's budget question is OB-230, still open: on this railway three-unit trains are refused TopMainR1 by length, so the pinned arrangement answers IMPOSSIBLE rather than running out of time, and that is not the case the heuristic is for. | fixed validated | - |
| 2026-09-14 | OB-221 | bug | Adam: *"I set address to 40, which makes it a Signal.  Then, if I click on Kind -> Signal and exit out, it snaps back to Switch 1."*  The condition table reset a line to its kind's starting address on every commit of the Kind cell, and a drop-down commits when it closes whether or not anything was chosen - so any line went back to address 1, and a signal line then read as the switch at 1.  Choosing the kind a line already is now changes nothing; moving between Switch and Signal, which are stored as one accessory command, keeps the address and says the setting in the other words (turn is red, straight is green).  `ui.testRouteEditorValidation.testChoosingTheKindALineAlreadyIsKeepsIt` and `testSwitchingBetweenSwitchAndSignalKeepsTheAddress`, seen red first; `e4651cec`. | - | `MT-427` |
| 2026-09-14 | FR-082 | feature request | Adam: *"For the red operators, can we add a warning triangle icon with a tooltip explaining what's wrong next to it?"*  `ConditionOutline.whatIsWrong` gives each flagged line its reason - a word that differs from its level's word, or one indented past a condition beside it, which are put right differently - and the editor paints a triangle after the red word with that reason as its tooltip, in eight languages.  The renderer is one label reused down the column, so the triangle is cleared on every other line.  `core.testConditionOutline.testEachFlagSaysWhy`, `ui.testRouteEditorValidation.testARedWordCarriesAWarningThatSaysWhy` - seen red first and mutation-checked both ways; `e4651cec`. | - | `MT-428` |
| 2026-09-14 | OB-220 | bug | Adam: *"the first or is read as an and here. also, the first condition for some reason cannot be indented for proper grouping."*  The OR sat deeper than the conditions either side of it, where a word joins nothing, so the reader dropped it and the level's AND joined all three - with nothing in red.  `ConditionOutline.problems` now flags a word deeper than a condition beside it, which draws it red and makes Save refuse; the indent itself is still allowed, because building a group one line at a time passes through that state, and refusing it broke that gesture.  The first line may go one level in, so a condition can start with a group.  `core.testConditionOutline.testAWordDeeperThanAConditionBesideItIsFlagged` (with a control for a word joining two groups), `ui.testRouteEditorValidation.testAConditionThatStartsWithAGroupCanBeBuilt` and `testAWordIndentedAloneIsFlagged`, each seen red first; `a5a51ac3`. | - | `MT-425` |
| 2026-09-14 | FR-081 | feature request | Adam: *"In the 'reads as', can we also bold the operators, make on/straight/green green and off/turn/red red?"*  Reads as is markup now: the joining words bold, and the setting word coloured by the WORD - on, straight and green one colour, off, turn and red the other - because the same true or false is "on" for a sensor and "turn" for a switch.  Everything taken from the route is escaped on the way in.  `ui.testRouteEditorValidation.testReadsAsBoldsTheWordsAndColoursTheSettings`, seen red first; `a5a51ac3`. | - | `MT-426` |
| 2026-09-14 | OB-218 | bug | From MT-368 point 2: the in-progress badge shows the turned facing until the train arrives. **Closed as accepted, 2026-09-14.** Adam: *"if the arrow gets updated upon arrival, then I am OK with it. validate that it does, then close the OB."* Validated: after arrival the train is stood on the copy that faces the way it really points - kept direction, `core.testTheArrivalHonoursTheAnswer` (`testDecliningTheTurnDoesNotLeaveItOnTheTurningCopy`, `testItIsStoodOnTheCopyItsOwnApproachReaches`); turned, `regression.testTheTurnAtTheDestinationReachesTheDiagram`, which reads the facing the diagram draws from the setup and the running layout - both run green on 2026-09-14, and Adam saw the same on MT-368 point 3: *"Once the train arrives, the displayed direction is correct."* The route is still reserved on the copy the path ends on while it runs; that is the part accepted. | declined | - |
| 2026-09-13 | OB-217 | bug | Adam: *"move 'one way run' and 'name everything' into the bulk tools menu, available only in the autonomy editor itself (not track diagram).  update 'path type' to use the blue label style, and move it below 'why not moving' as it belongs.  Also, add a 'page settings' label above 'exclude page'"*.  Both items are on Bulk Tools where the panel has a page - the autonomy editor - so the track diagram's menus, which are the same panel with no page, do not carry them; the two buttons still exist for the refresh and the disarm path and are simply no longer mounted.  Path Type is a blue group heading below Why Not Moving?, and a Page Settings heading, in eight languages, sits above Exclude Page.  `ui.testBulkToolsHoldsTheWholeLayoutTools` asks the real menu and the real column, mutation-checked | - | `MT-400` |
| 2026-09-13 | OB-216 | bug | Adam: *"the orange lines over the switch at 12,13 are misaligned.  A small offset as on the other tiles is OK."*  Each further segment on a square was pushed aside by a ninth of the tile times its position in the square's list, and the route out from BottomMainB and the route back cross that switch four times between them - so the fourth, the straight rail again, went two and a half ninths off it.  The nudge is per RAIL now: a second pass along the same rail moves half a ninth, which is exactly the offset every ordinary square already had, and a different rail is not moved.  `core.testATestedPathStaysOnItsRail` paints the square and measures - 20px off the rail against 6px on an ordinary square before the change | - | `MT-399` |
| 2026-09-13 | OB-215 | bug | Adam: *"for 'which way does it face', it should be 'to the north (up)'"*.  The facing question borrowed the arrival-side labels, which answer the opposite question about the opposite end of the train.  It has four labels of its own - To the North (up) and the rest - in eight languages.  `core.testAPasteDoesNotTurnTheTrainRound.testTheFacingButtonsNameAHeadingNotAnArrival`, mutation-checked | - | `MT-398` |
| 2026-09-13 | FR-078 | feature request | Adam: *"if a return home plan fails, state the reason why the layout doesn't allow a locomotive to go to its home in the log (length, blocked, etc.)"*.  A failed plan keeps one sentence per locomotive and the log gets each one: the square it stands on out of service or not a station; its home not a station, out of service, excluding it, or too short - with both lengths; no route it may take; two homes on one detection section; or, where the search only ran out of room and proves nothing, the home another train is standing on.  `core.testReturnHomeSaysWhy` reads both the plan and the log, mutation-checked | - | `MT-402` |
| 2026-09-13 | FR-077 | feature request | Adam: *"there needs to be a spinner on the return home button while it is calculating"*.  A turning mark on the button from the press until the plan has an answer, cleared as well if the planning throws.  A turning arc rather than the hourglass, which is the mark for a modal wait.  `ui.testReturnHomeShowsItIsWorking` holds the railway the planner must read, so the plan is certainly still being worked out while the button is looked at; mutation-checked | - | `MT-401` |
| 2026-09-13 | OB-214 | bug | Adam: *"Set Segment Length needs a tooltip that says 'Control+E'.  change for other missing tooltip hints."*  Read off the editor's key handler against every tooltip, four were unnamed: Control+E on Segment Length, Control+S on Rename, Control+H on the Home item, and Control+G on the Track Lengths toggle.  Control+L, D, K and B already were.  `ui.testTheEditorNamesItsShortcuts` asks the real right-click menu, mutation-checked | - | `MT-397` |
| 2026-09-13 | FR-075 | feature request | Adam: *"to bulk tools in the autonomy editor, add an option to mass mark current train locations as their homes."*  **Home All Trains Where They Stand** sits beside the three clears and is built like them: one bulk door on the session so the station index is re-derived once rather than once per train, the count in the label, and a tooltip that is the same sentence the confirmation shows.  It is the only one of the four that ADDS, so the confirmation names what it will overwrite rather than what it will lose - a train homed here loses its home elsewhere and a square homed to another train is reassigned, both of which follow from `AutonomySession.setHome`, where the one-locomotive-one-station rule lives and which the bulk door calls rather than writing the property itself. | - | `MT-378` |
| 2026-09-13 | OB-213 | bug | Adam: *"For the buttons on multi-unit Mm2 locomotives paired with mfx/dcc ones, don't print 'F<x>' text labels on the function buttons - keep the label blank as is the default."*  A tweak to MT-359's fix: the buttons past an MM2 head's own range are offered because the CONSIST can drive them and carry no icon because the icon and function-type tables are sized to the head's decoder, and the first cut filled that gap with the function's number.  It says what every other iconless function button says, which is the point - a number on some buttons and not others reads as a difference in kind where there is none. | - | `MT-392` |
| 2026-09-13 | FR-076 | feature request | easy tracking of station labels **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-12 | OB-219 | bug | **Formerly listed as MT-368**, the test it was raised on, which is not a bug id (renumbered 2026-09-14). Adam: *"When 2-8-4 is pasted, it should always face east.  Does it?"*  It did not - measured on the frozen snapshot, **east 21 of 40 and west the other 19** from an unchanged setup in one process.  `facingByPath` answered with the first copy of the target its walk touched, and `Layout.getNeighbors` shuffles by design (*"Randomize order to allow for variation in paths"*), so on a square trains may turn at - where the plain copy and the turning copy sit the same distance away facing opposite ways - the shuffle chose the direction.  The walk now runs to the end and lets the distances decide, and prefers the copy a train could be standing on over the one it would have had to turn round on; a compulsory turn has no plain copy, so it still reverses.  The no-path arm kept a `new Random()` as well, and now keeps the train's own heading where the landing can hold it - Adam's Option 1 - and is otherwise deterministic.  `MT-377` **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-377` |
| 2026-09-12 | FR-074 | feature request | Two small things in the **Unavailable While Occupied** dialog. Sorted alphabetically on the text each row actually shows - `describeTile`, not the point name, because an entry that has lost its name renders as its sensor or coordinates and sorting by something invisible puts it where the reader cannot predict; the old order was `getNamedTiles()` with stored-but-unlisted entries appended, so ticked ones collected at the bottom. And a square autonomy will never choose is drawn in a lighter grey with a tooltip saying why, asked of `stationsAutonomyWillNotChoose` - the runtime's own rule, the same one the captions and the no-available-paths window use, so this does not become a third answer. A foreground colour rather than a disabled box, deliberately: these are perfectly valid to block with, which is what FR-001 exists for, so the mark says what kind of square it is and not that it cannot be picked. `MT-376` **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-376` |
| 2026-09-12 | OB-212 | bug | Two washes, each right on its own, landing on one square. `LayoutLabel` draws the tile art at `BLOCKED_ALPHA` where the railway refuses the square, and `TileAnnotation` lays a white wash at `DIM` under its arrows so thin arrows lift off busy tile art - neither knew about the other, so a square that is both blocked and annotated was faded twice. The annotation's is the one that gives way: on a tile already at 40% its purpose has been served, while dropping the other would lose what the fade is SAYING. `ui.testTheWashDoesNotStack` paints onto a known ground and reads the corners back, because every model-level question answers the same with the stacking present and absent; both mutations caught | - | `MT-375` |
| 2026-09-12 | OB-209 | bug | **Promoted to an MT, not fixed** - Adam: *"there are MT's that you filed as OB's - I can't act on those in the system."* An intermittent failure of `core.testTimetableCaptureThroughARealRun` under load: red in a battery and three times standalone right after it, green three times once the machine settled, identical source. Not OB-207 - forcing `tailLiesOn` true leaves it red. What blocks a diagnosis is that `whyNothingMoved` substitutes `blockedWhileRunning` for the real reason whenever autonomy is running, so the failure cannot say which rule refused | - | `MT-374` |
| 2026-09-12 | OB-208 | bug | **Promoted to an MT, not fixed** - it is a decision of Adam's and the issue list is not somewhere he can rule. Narrowing the wash to the train's extent, which he approved, made the grey mark the same squares as the orange line, so the "blocked but no train on it" category is empty and `ui.testBlockedTrackIsGreyWhileAutonomyRuns` asserts three kinds of square where two exist. Two ways to go, both written up on the MT: leave the grey as it is and rewrite the test around two categories, or put it back to what routing refuses. Not picked, because it sits between his ruling about the drawing and his ruling about the guard **Claude, 2026-09-14.** It asked for Adam's decision, and he ran its test and said **Works** (MT-373, 2026-09-12) before the entry was superseded.  **Reversed 2026-09-23 on his ruling** - asked again, *the whole stretch*: *"orange shows where the train is, gray shows what's blocked."*  The grey is every covered edge again, per road. | - | `MT-475` |
| 2026-09-12 | OB-211 | bug | The "..." under the destination list compared against a count taken BEFORE the menu dropped the squares it never shows, so it fired whenever anything at all had been filtered - and OB-205 added a whole new class of those, every ordinary terminus whenever the train cannot reverse, which on a non-reversible locomotive is nearly every right-click. The rule has a name now and means one thing: more ordinary destinations than fitted. `possible` is deleted rather than corrected, so no wrong number is left to pass. The first fix kept a SECOND site for a menu with nothing at all to offer, on my reasoning rather than his instruction, and Adam reported the symptom again - *"I still see ... for 74 407 DB at Tunnel, even though it has no valid paths"* - so that site is gone too and the item is decided in exactly one place. `regression.testTheDestinationDoorsAgree` pins the arithmetic, asserts the pre-filter count no longer exists, and counts the sites: what was wrong was never the comparison but which number reached it, and then which places bypassed it | - | `MT-372` |
| 2026-09-12 | OB-210 | bug | A rule at one door of two, and the second door was mine. OB-205 refused a non-reversible train an ordinary terminus in `isOfferableToOperator`, and only the track diagram's menu asked it; the Locomotive commands tab listed the terminus with a DASH, which is right for a parking berth and wrong here - a dash is a label, not a refusal, and the row still executed on a double click. `AutoLocomotiveStatus.whatTheOperatorMayChoose` now filters through the same rule, in the funnel both call sites already shared, and asks the RULE rather than carrying a fourth copy of it - the monitor guard carries the thread reasoning as its allowance. The dash keeps its terminus limb and it is not dead: a terminus that is also a berth stays offered and stays dashed. `regression.testTheDestinationDoorsAgree` is a census over both doors, seen red on the Locomotive tab | - | `MT-372` |
| 2026-09-12 | OB-207 | bug | A one-unit train parked at TunnelLongPark made BottomMainA unreachable from anywhere. Coverage was recorded per EDGE and `isPathClear` refuses any edge sharing metal with a covered one, so a train lying in the FIRST tile of a twelve-tile run fouled the far end of it. Diagnosed wrong twice before it was measured - first *"blocking a whole edge is right"*, then *"the first hop's covering is redundant because its endpoint is occupied"*, and the shared-metal sweep refutes both by refusing edges whose endpoints are free. Fixed along Adam's own line - *"there has to be a computed pre-reduction point where the switch is"* - by carrying the reduction's PLACES to the runtime rather than inventing nodes for them: `locationsOf` already gives every step a place id and `deriveLocks` builds the whole shared-metal relation by intersecting those sets, so only the result was reaching `Layout`. `GraphReducer.placesAlong` writes them, `AutonomyBuilder` and `Edge.toJSON` emit them, `parseAuto` reads them, one tail walk answers in both edges and places, and the sweep asks `tailLiesOn`. The direct case stays whole-edge - a path uses all of its own edges - and an edge with no places keeps the old answer. Length 2 is still refused, by the ROOM rule and not by the standing train, which is a separate question for Adam. `core.testAShortTrainDoesNotBlockTheWholeRun`, seen red first, asserts the covering is PARTIAL and that every edge involved carries places so neither claim can be met by the fallback **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-371` |
| 2026-09-12 | OB-206 | bug | The Return Home refusal listed the trains that could not get home and left the operator to work out where each was going. It names the station too now, by its BASE name - a message about "BottomMainB (westbound, reverse)" would be worse than none - falling back to the locomotive alone if the home cannot be looked up, because a sentence that drops a train to avoid an awkward name hides the thing it is reporting. `MT-370` **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-370` |
| 2026-09-12 | OB-205 | bug | Three claims, two causes. **(1)** `isOfferableToOperator` asked only whether the square was active and the locomotive not excluded, so a train that cannot reverse was offered an ordinary terminus by hand; the terminus rule was moved out of `isPathClear` on 2026-09-01 so that *"the operator asking for that BERTH by hand is no longer refused"*, and the rule that was written did not carry the word berth. It does now: a square autonomy never chooses is still offered, an ordinary terminus is not. **(2) and (3) are one cause** - `AutonomyBuilder` emits the turning copy of a MAY-turn square with `terminus: true`, so `isTerminus()` cannot tell it from a compulsory terminus, and both doors that decide whether to ask read exactly that flag. `ManualReversalPrompt.forJourney` returned KEEP_DIRECTION without asking and `Layout.shouldReverseAt` returned `current.isReversing()`, which is true there - so the operator was not asked AND the train was turned against the only answer the prompt could have given. Both now ask the DOOR, which is the lesson `shouldReverseAt` already wrote down about `current` and never applied to `destination`. `core.testNonReversibleTrains` gains two claims, each planted. `MT-367`, `MT-368` **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-367, MT-368` |
| 2026-09-12 | OB-204 | bug | *"About half the time"* was the diagnosis. `AutonomyBuilder.splitSides` collects the entry side of every reduced edge arriving at a square and takes no notice of the barred list, so BottomMainA - which bars its arrival from the east - looked two-sided to every placement door. That defeats `ArrivalSidePrompt.suggestedFor`'s first and best rule, *"one way in that is not the way it is pointing"*, and drops it through to the compass assumption, which reads the train's FACING - and the facing alternates, because the square it was moved from turns every train round. `unbarredArrivalSides` is what the doors ask now. The test is about STABILITY rather than about west: the same square and two facings must give one answer, with the raw geometry as a control that still gives two, so a rule that had simply stopped working could not pass it. `MT-369` **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-369` |
| 2026-09-12 | OB-203 | bug | The arrived-from row is the only one in that dialog not laid out by the form, and it showed: it used the MENU's caption - "Train arrived from", which reads as the start of a sentence its submenu finishes - in a column of Title Case nouns, and it put the combo in `BorderLayout.CENTER`, so the combo stretched the full width while every field above it sat at its natural size against the right edge. The label has its own key now, `trainArrived`, in eight languages (the drift argument in the old comment is answered rather than ignored: they are two wordings because a menu item and a form label are two things). The combo moved to `EAST`, which is the shape the form's own rows use, and the row is inset to the form's left and right margins - **read off a laid-out `arrivalFuncLabel` rather than written down**, because the container gap is the look and feel's to choose and this window has two of those depending on how it was built **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-414 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-414` |
| 2026-09-12 | OB-202 | bug | The real Autonomy menu is built from the configurations on disk, so it could not exist until the connect was over and was APPENDED to the bar from `setViewListener` - the heading grew in front of the operator part-way through start-up. `autonomyTopMenu`, which Adam added to the form immediately after Layouts, holds that slot from the first frame now: dressed at construction with the real menu's own heading from the bundle (the form hard-codes "Autonomy", which would have been English in all eight languages), greyed, with the reason on its tooltip - and `mountAutonomyMenu` REPLACES it rather than adding beside it, so the bar's shape never changes. The three-part condition behind the greying moved onto the window as `autonomyMenuIsUsable` / `whyAutonomyIsUnavailable`, because the placeholder and the menu both ask it now and two copies would be two answers waiting to disagree. `regression.testTheAutonomyMenuHoldsItsSlot` asserts the heading is there before any mount, that the count and the index do not move across the swap, that exactly one autonomy heading exists afterwards, and that whatever holds the slot agrees with the window - **written but not yet run**, because it builds the real window through `LayoutSandbox` and Adam's own TrainControl was open **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-409 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-409` |
| 2026-09-12 | FR-072 | feature request | easier autonomy triage **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-12 | FR-073 | feature request | filter autonomy issue list **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-12 | OB-200 | bug | hotkeys don't work in autonomy editor **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-12 | OB-201 | bug | incomplete error message in autonomy **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-11 | FR-070 | feature request | make it easier to enable/disable routes **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-11 | FR-071 | feature request | right clicking the edit button **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-10 | FR-069 | feature request | Adam: *"to autonomy bulk tools menu, add 'clear all track lengths' - this should clear the segment lengths across all pages, after the user confirms in a popup."* **Clear All Track Lengths (N)** sits beside the two clears already there and is built like them: one bulk door on the session so the reducer is re-derived once rather than once per measured square, the count in the label, and a tooltip that is the same sentence the confirmation shows - from one builder, which is the arrangement OB-194 put there so the two cannot drift. The dialog says how many squares it is about to empty and that typing them again is the only way back, because there is no undo and no file to restore from until Save. Greyed when nothing is measured, and the guard asks again when it is pressed - the affordance and the guard being one question is `guard-and-affordance-same-question`. `regression.testClearAllTrackLengths` measures squares on more than one page and asserts none survives, which is the half a per-page clear would pass | - | `MT-350` |
| 2026-09-10 | OB-198 | bug | Fixed in one place rather than three, which is what makes it stay fixed. `LayoutEditor.autonomyHover` was set on hover and never cleared, and Control+H, Control+S and Control+E each read it and built a `TileKey` out of whatever coordinates it gave - so after a page step all three named a square on the page before, `getCoordinates` answered -1,-1, and the key acted on `(page, -1, -1)`. There is one question now, `hoveredSquare()`, which answers null when the remembered label is not on the grid it is asked about and **forgets it while it is there**, so nothing can read it twice; `leaveFor` clears it on the way out of a page as well. `regression.testTheHoveredSquareIsForgotten` hovers a real label off the rendered editor, then one that is not in the grid, and asserts the second names nothing - two of its three claims go red with the check removed **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-351` |
| 2026-09-10 | OB-199 | bug | Two things, and the diagnostic half was fixed first: `whyNothingMoved` was asked AFTER `stopLocomotives()` and reports exactly the flag that call clears, so it was a constant on the failing path. The flake itself: both tests in the class call `loadedConfiguration()`, which parses a NEW `Layout` while the one before it is still driving trains - `stopLocomotives` stops them *gracefully*, at their next station, so it returns long before the railway is quiet, and the locomotives belong to the MODEL and are shared. So the second test can start against a fleet the first is still driving, `getActiveLocomotives()` on the new layout stays empty, and the wait runs to its ceiling. `loadedConfiguration` now waits for the railway in force to go quiet, and says so loudly if it does not inside a minute. **This is a mechanism, not a proof** - it matches the shape (four failures in a battery, none standalone) and the change is right on its own terms whether or not it is the cause, and the diagnostic will now say if it was not **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-09-10 | OB-196 | bug | **Not a defect.** Adam: *"A/B/C are distinct pieces of track. We put the lengths of 1 in there for testing. Actual tracks are much longer... these are not realistic lengths, and I need to change them on the diagram."* So the eight squares that offer a train of two units or more nowhere at all are an artefact of three test measurements, not of the rule - and the answer is a tape measure on the diagram, which is his to do. What came out of it: `test/layouts/live-snapshot` now carries **no lengths at all**, on his instruction *"remove all currently set segment lengths and set custom lengths where it makes sense for your tests"*, and the two censuses set the three tight tiles themselves and say in the file that it is a deliberate stress configuration rather than a measurement of his railway. Every figure they report was really about those three tiles | declined | - |
| 2026-09-10 | OB-197 | bug | Adam: *"it should stop doing that. that was not intended."* `TrainControlUI`'s key chain ended `else if (this.buttonMapping.containsKey(keyCode))` with no `!controlPressed`, and `buttonMapping` holds all 26 letters - so Control plus every letter no named shortcut caught selected a locomotive button exactly as the bare letter does. That is also why the shortcut guard's printout was wrong about which keys were free, and FR-066's key was picked off that list. One clause, plus the guard that keeps it: `regression.testNoTwoShortcutsShareAKey.testTheFallThroughStillFiltersControl` fails if the filter comes off, and everything that class prints about free keys rests on it **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-344` |
| 2026-09-09 | FR-066 | feature request | Adam picked the key himself once both handlers had been read - **"let's do E"** - Control+D, which the ticket proposed, being taken twice over by the editor's address toggle and the main window's locomotive adder. **Control+E** opens the length dialog on the square under the pointer in the autonomy editor. **The first cut had two defects and a review found both**, each the shape `promptLengthFor`'s own javadoc said it avoided: it asked `isIgnored`, which is one of THREE questions `buildTileMenu` asks before it reaches Set Length, so it opened the dialog on a text label whose menu offers no length at all; and it wrote to the hovered square where the menu writes to the run's LEADER, so the two doors put the same number in two places and a run measured through both counted twice. There is one door now - `buildTileMenu` adds its item inside `if (offersALength(tile))` and binds it to `applyLength(squareTheLengthWouldGoOn(tile))`, which is what the key calls - and `regression.testControlEAsksTheMenusQuestion` compares the two over every square of a page rather than over two named ones. `regression.testNoTwoShortcutsShareAKey` reads both key handlers on every run, fails on a key bound twice in one window, and prints what is free in both - which became true only with `OB-197` | - | `MT-341, MT-342, MT-343` |
| 2026-09-09 | OB-195 | bug | Adam: *"narrow the notice to match the runtime - autonomy should only allow a turn at a point if the 'allow in autonomy' option is checked, otherwise the train may only pass through in its current direction."* `AutonomySession.stationsAutonomyWillNotChoose` carried a second clause, `isMustTurnAround`, and a comment claiming it was the runtime's rule; it is not, because a compulsory turn that stops is built as a TERMINUS and `Layout.isSendableDestination` admits one. The clause is gone and nothing replaces it: **Can Be Chosen in Full Autonomy** is the one switch that decides, and turning round says what happens when a train arrives rather than who may send one. `core.testACompulsoryTurnIsChosenLikeAnyOtherStation` asserts both halves on `single-switch`, where the two markings can disagree - on Adam's own railway every compulsory turn is also marked manual-only, which is why this survived. behaviour.md section 3 carries the ruling **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-348` |
| 2026-09-09 | OB-193 | bug | TopMainR2 is `1 - Main:6,4`, and the snapshot captions it twice - on `6,4` and on `6,5` - which is the state `migrateStationLabels` can produce without anybody asking, because it writes captions through the raw store door and that door does not sweep the old one away. Already fixed on 2026-09-08 by **05c7f48e** (MT-337): `TrainControlUI.autonomyCaptionAt` makes a SELF-caption give way when another square is already naming that station, so the offset label - the one somebody placed - is the one that survives. Verified rather than believed: `regression.testTheHomeLabelIsDrawnOnce` now names that square and asserts one label on it, and goes red with the fix removed | fixed validated | - |
| 2026-09-09 | OB-194 | bug | Adam: *“Warning if using the bulk tool”* - so the warning rather than a real undo. The confirmation was a fixed sentence about home assignments; it now names every locomotive it is about to lift, says how many squares it will empty, and says that Cancel will not put them back. It cannot, since OB-183: placements are read from the railway rather than from the setup, so closing the editor restores the file and not the trains. One builder feeds both the dialog and the menu item's tooltip, so the warning cannot arrive only after the click. `regression.testTheBulkClearWarnsThatCancelWillNotUndoIt` asks the editor for the string and checks all three, in whichever of the eight languages the run is in **Claude, 2026-09-14 (WK7-C2, WKV-B1).** Since OB-223 Cancel does put them back, and the warning says so; on the track diagram's own menu, which has no Cancel, it says the clear is saved at once. The test is now `regression.testTheBulkClearSaysWhatCancelDoes`. **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-415 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-415` |
| 2026-09-09 | FR-068 | feature request | Adam: *“it should be representable already in the UI, right?”* - and it is, so nothing in the editor changed. A line may be indented one level past the line above it, so `3 or (4 and (1 or 2))` is built by typing seven lines flat and indenting twice; the outline holds it and the editor saves it. The one shape that cannot be TYPED is the outline `ConditionOutline.of` writes when the group is the AND's left child, which steps from depth 0 straight to depth 2 - and a route carrying that opens and reads correctly, which is what MT-320 settled. `ui.testRouteEditorValidation.testANestedGroupThatIsNotTheFirstTermCanBeBuilt` is the gesture, and asserts the one-level rule that decides which order is typeable **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-416 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-416` |
| 2026-09-09 | FR-067 | feature request | A tooltip in the autonomy editor's right-click menu saying Control+S renames **Claude, 2026-09-14.** Built with OB-214, which named Control+S on Rename; MT-397 checks it.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-397` |
| 2026-09-08 | OB-180 | bug | the covered-track shading was computed correctly and never redrawn: a tile decides its wash when it is DRAWN, and tiles are only drawn when their own accessory, feedback or route changes, so a train moving left the old squares grey. `refreshCoveredTrack` now repaints the symmetric difference. Two tests, at the icon and at the pixel, both seen failing without the fix | - | MT-329 |
| 2026-09-08 | OB-181 | bug | the station label read which COPY of a split square a train stands on - which IS its direction - and the facing menu read the value stored in the setup, which is written only when somebody places a train by hand. A deliberate split, never swept to the second surface. Both read `AutonomySession.facingOnTheRailway` now, with the stored value as the fallback | - | `MT-330, MT-331, MT-338` |
| 2026-09-08 | OB-182 | bug | two vocabularies for one question: the geometry names where the neighbouring POINT lies, the build names the side the metal enters by, and they differ on every curve. Correcting the doors alone had been tried and reverted the same day because it stopped the tail blocking working at all. The builder now writes `entrySide` into the configuration and `Layout.entrySideOf` is the one definition every reader calls | - | MT-332, MT-333 |
| 2026-09-08 | OB-183 | bug | a rebuild regenerates every placement from the setup, which is stale about any train moved by hand since the editor opened - so changing a home put trains back where the file said. Adam ruled the railway wins: placements are carried across the rebuild, and closing the editor now writes the setup file, which was the third of his three save moments **Claude, 2026-09-14.** Both its tests were superseded - MT-337 by `regression.testAnEditedPlacementSurvivesTheRebuild` after Adam's last "Does not work" - so nothing on the Tests tab can close it.  Fixed, with an automated test and no hand check since; left **fixed unvalidated** for Adam to close or re-test. **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-09-08 | OB-184 | bug | the staging planner's model of occupancy was 'a locomotive at rest occupies its own station's sensor', which is half of it - a train longer than its berth covers track no sensor reports. It now consults the covered track the same way `isPathClear` does, symmetric lock partners included, for trains that have not yet moved in the plan | - | MT-335 |
| 2026-09-08 | OB-185 | bug | a one-way restriction changes no tile art - what moves is the arrow drawn over it - and the only redraw door rebuilt every label on the page. There is a light door beside the heavy one now, and the running layout is still rebuilt because that half was never the flicker **Claude, 2026-09-14.** Its only test, MT-334, was superseded by `regression.testTheDiagramIsNotRebuiltForAnArrow` - after Adam had already run it on the fix and said **Works** (tests.md, MT-334).  Closed on that verdict. | fixed validated | - |
| 2026-09-08 | OB-186 | bug | the menu bar moved into the window title and the whole interface shrank, after the look and feel was installed earlier so that tests would stop running against a program drawn in Metal. FlatLaf decides its scale AND its window decorations once at install and caches both, so one line's position changed every font and margin. Both are declared constants now, and the install touches AWT first so the scale cannot depend on call order | - | `MT-339, MT-340` |
| 2026-09-08 | FR-063 | feature request | local locomotive icons **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-08 | FR-064 | feature request | routes when power is off **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-08 | OB-192 | bug | critical: UI freeze in autonomy **Claude, 2026-09-14.** Fixed in `e4f8f577` and `49a3aee4`; the freeze re-test was sent to MT-335.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-335` |
| 2026-09-08 | FR-062 | feature request | download the CS3 data files too when the user confirms a Central Station download **Claude, 2026-09-14.** Fixed in code with an automated test and never checked by hand; MT-405 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-405` |
| 2026-09-08 | FR-065 | feature request | escape closes autonomy/track editor **Claude, 2026-09-14.** Fixed in code with an automated test and never checked by hand; MT-407 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-407` |
| 2026-09-08 | OB-187 | bug | the menu options ungrey at different times when connecting finishes **Claude, 2026-09-14.** Fixed in code with an automated test and never checked by hand; MT-408 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-408` |
| 2026-09-08 | OB-188 | bug | use current vs use active buttons **Claude, 2026-09-14.** Fixed in code with an automated test and never checked by hand; MT-410 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-410` |
| 2026-09-08 | OB-189 | bug | trains don't reverse. **Claude, 2026-09-14.** Fixed in code with an automated test and never checked by hand; MT-411 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-411` |
| 2026-09-08 | OB-190 | bug | OB-189 follow-up: confirm the diagram shows the new facing after a hand-driven reversal **Claude, 2026-09-14.** Fixed in code with an automated test and never checked by hand; MT-412 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-412` |
| 2026-09-08 | OB-191 | bug | why not moving is blank **Claude, 2026-09-14.** Fixed in code with an automated test and never checked by hand; MT-413 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-413` |
| 2026-09-07 | OB-179 | bug | the column and row numbers stopped being a setting of their own: they are part of the grid, on with it and off without, and the "Show Coordinates" item is off both editors' right-click menus. Control+K now turns the grid, which is the only switch either of them has **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-276, MT-323` |
| 2026-09-07 | FR-061 | feature request | what a caption says is one **Text Labels** dropdown above Track Directions - Station Names (default, remembered), Parked Locomotives, Home Locomotives, None - replacing a master switch and two boxes that were not independent of it | - | `MT-274, MT-282, MT-283` |
| 2026-09-06 | OB-178 | bug | a route with auto-fire unchecked and a blank s88 refused to save, demanding an integer; blank now reads as 0 **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-290` |
| 2026-09-05 | OB-177 | bug | the "<locomotive> is facing" menu showed nothing ticked when the recorded facing was one the square cannot hold; it now lists that facing too, so the menu says what the train is down as **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-289` |
| 2026-09-05 | OB-176 | bug | the track-length box is selected when its dialog opens **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-288` |
| 2026-09-04 | OB-175 | bug | the incoming arrow on a curved sensor tile overlapped the track; it sits in the lower-right corner now **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-287, MT-278, MT-279, MT-280` |
| 2026-09-04 | OB-174 | bug | ticking "show parked trains" left the captions hidden if Text Labels was off. **Superseded by FR-061**, which replaced the switch and its two boxes with one dropdown whose four options exclude each other - there is no longer a state that needs correcting | fixed validated | - |
| 2026-09-04 | FR-060 | feature request | back a train into a parking track, with a new parking designation to say which stations those are **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"FR-060 was already tested afaik"* - MT-249, *A parking berth a train cannot get out of*, passed with his Works on 2026-09-02. | fixed validated | - |
| 2026-09-04 | OB-173 | bug | the editor opened behind other windows on the first open; it now raises itself as the second open always did **Claude, 2026-09-14.** MT-268 was split; the editor coming to the front on the first open is MT-286. | - | `MT-286` |
| 2026-09-04 | OB-172 | bug | one missing square could delete an axis number; the ruler now looks in three rows, and the numbers follow the grid setting **Claude, 2026-09-14.** MT-268 was split into MT-322 and MT-323, one outcome each. | - | `MT-322, MT-323` |
| 2026-09-04 | FR-059 | feature request | add the paused autonomy locomotive indicator to right click menu on track diagram **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-03 | FR-058 | feature request | the diagram's right-click menu lists only what autonomy would choose; everything else valid is under **More Destinations**, uncapped | - | [MT-266](tests.md#mt-266) |
| 2026-09-03 | OB-171 | bug | the reversal-length notice fired on every square of the run in; one notice per square trains turn at now, saying how many squares still need a length **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-262` |
| 2026-09-02 | FR-057 | feature request | the diagram prints its column and row numbers, on by default. **Its toggle is gone as of OB-179** - the numbers follow the grid now, so they have no menu item and no preference of their own, and Control+K turns the grid instead **Claude, 2026-09-14.** Closed on Adam's word: *"FR-057 is in a weird state but I believe it all good to be closed."* The weird state was its Became - MT-274 superseded, MT-277 and MT-284 validated, MT-291 still waiting - so the row showed MT-291's "needs test". MT-291 (the Grid tooltip in a language you read) stays open in the Tests tab on its own. | fixed validated | - |
| 2026-09-02 | OB-170 | bug | the window still came up without the keyboard, and the request was never made again | - | [MT-259](tests.md#mt-259) |
| 2026-09-02 | FR-056 | feature request | right-clicking a tunnel flashes the square it is joined to, when that square is on this page | - | `MT-391` |
| 2026-09-02 | FR-079 | feature request | **Formerly listed as R28-C1**, the review finding it came from (R28 round, C1), which is not a feature request id - Adam, 2026-09-14: *"I see a featre request R28-C1, which is not a valid feature request ID."* The finding keeps its id in the review catalogue and in the code that cites it. "Clear All Home Locomotives" restored to the autonomy editor, with a confirmation | - | [MT-254](tests.md#mt-254) |
| 2026-09-02 | OB-167 | bug | station no + must reverse + disabled gets the same icon as a terminus **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-417 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-417` |
| 2026-09-02 | OB-169 | bug | clicking a palette tile then an occupied square no longer placed it | - | [MT-252](tests.md#mt-252) |
| 2026-09-02 | OB-168 | bug | the window came up without the keyboard, so the locomotive letters did nothing **Claude, 2026-09-14.** Its test MT-251 was superseded by MT-259, which is where it is tracked now. | - | `MT-259` |
| 2026-09-02 | FR-055 | feature request | search function for points in autonomy editor **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-01 | FR-054 | feature request | a drawn placeholder locomotive for one with no picture | - | `MT-390` |
| 2026-08-31 | OB-166 | bug | a hand dispatch swept every protecting signal, so an empty platform was commanded green **Claude, 2026-09-14.** MT-246 was split into MT-303 and MT-304, one outcome each. | - | `MT-303, MT-304` |
| 2026-08-31 | OB-165 | bug | Return Home stayed dark after a train left its claimed home | - | [MT-165](tests.md#mt-165) |
| 2026-08-31 | OB-164 | bug | the diagram right-click menu offers no destinations in non-atomic mode - closed as a known limitation | declined | - |
| 2026-08-31 | FR-048 | feature request | easier locomotive editing **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-08-31 | FR-049 | feature request | locomotive import and export **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-08-31 | FR-050 | feature request | modernize keyboard tab **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-08-31 | FR-051 | feature request | improved log **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-08-31 | FR-052 | feature request | autonomy editor bulk mark stations **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-08-31 | FR-053 | feature request | calculate signals to set to red **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-08-30 | OB-159 | bug | locomotive icon over stations while running | - | [MT-242](tests.md#mt-242) |
| 2026-08-30 | OB-163 | bug | the routing rules explain themselves to nobody | - | [MT-241](tests.md#mt-241) |
| 2026-08-30 | OB-158 | bug | ... on traversing trains in station labels | - | [MT-234](tests.md#mt-234) |
| 2026-08-30 | OB-156 | bug | a routing rule that picks at random from the highest priority | - | [MT-240](tests.md#mt-240) |
| 2026-08-30 | OB-155 | bug | synchronizing with cs2 after deleting routes | - | [MT-239](tests.md#mt-239) |
| 2026-08-30 | OB-161 | bug | a phantom row stays highlighted below the diagram | - | [MT-236](tests.md#mt-236) |
| 2026-08-30 | OB-162 | bug | the timetable shows placeholder headings when it is empty | - | [MT-237](tests.md#mt-237) |
| 2026-08-30 | OB-160 | bug | route buttons conducting track they were not drawn to conduct | - | [MT-235](tests.md#mt-235) |
| 2026-08-30 | OB-157 | bug | selection drag repaints every tile on the diagram | - | [MT-228](tests.md#mt-228) |
| 2026-08-29 | OB-154 | bug | checkNoMaxTrainLength does not specify the station name | - | [MT-224](tests.md#mt-224) |
| 2026-08-29 | OB-153 | bug | checkNoTrainLength names the station, not the train at it | - | [MT-224](tests.md#mt-224) |
| 2026-08-29 | OB-152 | bug | translate checkNoTrainLength per manual change - cancelled, the change was reverted | declined | - |
| 2026-08-29 | OB-151 | bug | font size and alignment of autonomy notices above track diagram viewer | - | [MT-220](tests.md#mt-220) |
| 2026-08-29 | OB-150 | bug | no error on duplicate s88 | - | [MT-223](tests.md#mt-223) |
| 2026-08-29 | OB-149 | bug | padding above "start autonomous operation" in track diagram | - | [MT-220](tests.md#mt-220) |
| 2026-08-29 | FR-047 | feature request | autonomy train length easier configuration | - | [MT-222](tests.md#mt-222) |
| 2026-08-29 | FR-046 | feature request | warning if train length is not set | - | [MT-221](tests.md#mt-221) |
| 2026-08-29 | FR-045 | feature request | easier autonomy function management | - | [MT-222](tests.md#mt-222) |
| 2026-08-29 | OB-148 | bug | flickering of top bar in layout viewer | - | [MT-219](tests.md#mt-219) |
| 2026-08-29 | OB-147 | bug | play button in routes | - | [MT-217](tests.md#mt-217) |
| 2026-08-29 | FR-044 | feature request | find route feature | - | [MT-218](tests.md#mt-218) |
| 2026-08-29 | OB-132 | bug | testTimetableOnDerivedGraph now skips, so it covers nothing | fixed validated | - |
| 2026-08-29 | OB-130 | bug | Ruling needed: may importing a legacy graph shut pages you already chose to keep | fixed validated | - |
| 2026-08-29 | OB-146 | bug | play icons in routes can be on top of text | - | [MT-217](tests.md#mt-217) |
| 2026-08-29 | FR-043 | feature request | route view user experience | - | [MT-217](tests.md#mt-217) |
| 2026-08-29 | OB-143 | bug | graceful stop from track diagram | - | [MT-216](tests.md#mt-216) |
| 2026-08-29 | OB-142 | bug | route command editor capitalization | - | [MT-215](tests.md#mt-215) |
| 2026-08-29 | OB-141 | bug | route commands contain s88 sensor | - | [MT-214](tests.md#mt-214) |
| 2026-08-29 | OB-140 | bug | syncing cs db from button shows no hourglass | - | [MT-213](tests.md#mt-213) |
| 2026-08-29 | OB-145 | bug | loc facing direction choice | - | [MT-212](tests.md#mt-212) |
| 2026-08-29 | OB-144 | bug | Critical: trains teleport | - | [MT-211](tests.md#mt-211) |
| 2026-08-29 | OB-139 | bug | move pointer in autonomy editor. | - | [MT-210](tests.md#mt-210) |
| 2026-08-29 | OB-138 | bug | doube clicking station label in track viewer | - | [MT-209](tests.md#mt-209) |
| 2026-08-29 | OB-137 | bug | route table freeze on import | - | [MT-208](tests.md#mt-208) |
| 2026-08-29 | OB-136 | bug | simulate: true has gone from the live autonomy configuration | - | [MT-207](tests.md#mt-207) |
| 2026-08-29 | OB-134 | bug | Six destructive confirmations still pre-select Yes **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-418 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-418` |
| 2026-08-29 | OB-133 | bug | The unreadable-import test has never exercised the rollback it is named for | fixed validated | - |
| 2026-08-29 | OB-135 | bug | The wait mark still does not animate on the track diagram | - | [MT-206](tests.md#mt-206) |
| 2026-08-29 | OB-131 | bug | Start Autonomy never comes back if a train never berths | - | [MT-205](tests.md#mt-205) |
| 2026-08-29 | FR-042 | feature request | Spot-check the newly translated autonomy strings | - | [MT-204](tests.md#mt-204) |
| 2026-08-28 | FR-041 | feature request | A splash while the station is reached, before there is a window to put one in | - | `MT-389` |
| 2026-08-28 | OB-129 | bug | The wait mark counted timer ticks on an event thread that coalesces them, and was capped at 400 in a top-aligned parent **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-206` |
| 2026-08-28 | FR-040 | feature request | The Layout menu names its data source instead of offering to tell you | - | `MT-388` |
| 2026-08-28 | OB-128 | bug | A greyed tab is not a closed door: nine methods opened it by index, and the panel kept the railway that had been unloaded | fixed validated | - |
| 2026-08-28 | OB-127 | bug | An empty layout path is the working directory, and its index named five pages that were never deleted | fixed validated | - |
| 2026-08-28 | OB-126 | bug | The grey Edit Layout button named one of its three reasons whatever was true **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-419 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-419` |
| 2026-08-28 | OB-125 | bug | The crop editor reopens where the crop was taken, not on the default view **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-203` |
| 2026-08-28 | FR-038 | feature request | Mis-filed: the crop editor quirk is a bug, re-filed as OB-125 | declined | - |
| 2026-08-28 | FR-039 | feature request | The request to cancel FR-038, which is done - nothing of its own to work | declined | - |
| 2026-08-27 | FR-036 | feature request | Plus and minus walk through the pages, through the switch that already existed | - | `MT-386` |
| 2026-08-27 | FR-037 | feature request | Travel restrictions can be drawn on the ordinary track diagram, on by default | - | `MT-387` |
| 2026-08-27 | OB-122 | bug | Not a defect: the warning was right, and the track diagram was the thing at fault | fixed validated | - |
| 2026-08-27 | OB-123 | bug | A reversing point is judged by where a train could go, not by what arrives **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-08-27 | OB-124 | bug | Four windows had no application icon, and the rule was written seven times **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-420 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-420` |
| 2026-08-27 | FR-033 | feature request | Fifty locomotive mapping pages, refused in the method and greyed in the menu | - | `MT-383` |
| 2026-08-27 | OB-121 | bug | The + row in the conditions list was handed the previous cell’s grey by a recycled renderer **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-421 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-421` |
| 2026-08-27 | FR-034 | feature request | The label chooser opens on the nearest station, and the last-clicked one is spent after one use | - | `MT-384` |
| 2026-08-27 | FR-035 | feature request | Station labels can be dragged in the autonomy editor, by the label or by its square | - | `MT-385` |
| 2026-08-27 | OB-120 | bug | Test a path drew routes into stations that refuse arrivals from that side **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-422 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-422` |
| 2026-08-27 | OB-119 | bug | Escape did not put the autonomy editor's tools down **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-406 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-406` |
| 2026-08-27 | FR-032 | feature request | Crop or pan an icon again without reselecting the source | - | [MT-203](tests.md#mt-203) |
| 2026-08-27 | OB-118 | bug | An empty station's caption sat at the left of its square rather than over the track **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-423 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-423` |
| 2026-08-27 | OB-117 | bug | The running-train tile painted out the station caption underneath it **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-242` |
| 2026-08-27 | OB-116 | bug | The left and right facing arrows were half the height of the up and down ones **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-424 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-424` |
| 2026-08-26 | LR-6 | bug | A comment describing behaviour that changed under it, and the dead half of the condition beside it | fixed validated | - |
| 2026-08-26 | LR-5 | bug | The first-dispatch signal sweep asked the counter again instead of remembering it had been first **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-08-26 | LR-4 | bug | Two trains on one platform still drew brackets inside the pill | - | [MT-197](tests.md#mt-197) |
| 2026-08-26 | LR-3 | bug | A placeholder station and a named one came out the same colour on the pill, under a comment saying they did not | - | [MT-197](tests.md#mt-197) |
| 2026-08-26 | LR-2 | bug | The route conflict question named the wrong reason, and the refusal was logged before it was one | - | [MT-202](tests.md#mt-202) |
| 2026-08-26 | LR-1 | bug | Closing TrainControl with the track editor open and answering Discard half-discarded the edit | - | [MT-201](tests.md#mt-201) |
| 2026-08-26 | OB-114 | bug | The capture test waits for the railway to move rather than for a fixed number of seconds **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-08-26 | OB-110 | bug | Already fixed when it was filed - the build predates it; re-test under MT-179 | - | [MT-179](tests.md#mt-179) |
| 2026-08-26 | OB-053 | bug | Not a defect as filed: one label per cell, built once. The REBUILDING underneath it is real and is a separate, smaller question | fixed validated | - |
| 2026-08-26 | FR-031 | feature request | Station labels can be light grey, and it is remembered | - | [MT-200](tests.md#mt-200) |
| 2026-08-26 | FR-030 | feature request | No captions in the track editor; station names in the autonomy one, with a switch | - | [MT-199](tests.md#mt-199) |
| 2026-08-26 | FR-029 | feature request | Seven flat sidebar icons in the theme blue | - | [MT-198](tests.md#mt-198) |
| 2026-08-26 | OB-115 | bug | Captions stopped voting on the row baseline, and stopped dragging their neighbours with them | - | [MT-197](tests.md#mt-197) |
| 2026-08-26 | FR-028 | feature request | Station captions are blue ovals with arrow icons, placed against the track | - | [MT-197](tests.md#mt-197) |
| 2026-08-26 | FR-027 | feature request | A locomotive on the square a train is running on, from a file you can replace | - | [MT-196](tests.md#mt-196) |
| 2026-08-26 | FR-025 | feature request | What holds a station back can be picked by clicking it | - | [MT-195](tests.md#mt-195) |
| 2026-08-26 | OB-111 | bug | The class that was rewriting your railway on every battery, found and given a sandbox **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-08-26 | OB-109 | bug | The diagram is not taken off the screen to be rebuilt | - | [MT-194](tests.md#mt-194) |
| 2026-08-26 | OB-113 | bug | A reversing point that reaches no station is reported - your own case was a missing one, and nothing said so | - | [MT-193](tests.md#mt-193) |
| 2026-08-26 | FR-026 | feature request | The full editor is one item away on the diagram’s own menu | - | [MT-192](tests.md#mt-192) |
| 2026-08-26 | OB-112 | bug | The diagram’s autonomy menu says which square it is about | - | [MT-192](tests.md#mt-192) |
| 2026-08-25 | FB-A1..C2 | bug | Independent pass over the last day (`grep "^FB-" findings.tsv`) - two more A, one of them a defect in an LD fix **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-08-25 | LD-A1..C5 | bug | The last day, reviewed (`grep "^LD-" findings.tsv`) - six A and seven B, seven of them from that same day; C6-C9 left open **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-184, MT-189, MT-191` |
| 2026-08-25 | OB-108 | bug | A layout edit that never finished is put back to how it was | - | [MT-191](tests.md#mt-191) |
| 2026-08-25 | OB-025 | bug | The store keeps a registry of what it keeps **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-08-25 | OB-107 | bug | The signal window opened over the diagram it describes | - | [MT-182](tests.md#mt-182) |
| 2026-08-25 | OB-085 | bug | Two homes holding each other back are now proved impossible | - | [MT-187](tests.md#mt-187) |
| 2026-08-25 | OB-086 | bug | The duplication review's remainder - six places one rule was written twice | - | [MT-187](tests.md#mt-187) |
| 2026-08-25 | OB-089 | bug | The test suite audit’s remainder - seven guards that asserted less than they read **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-08-25 | OB-084 | bug | testRenderingCost was a coin toss **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-08-25 | FR-024 | feature request | The wait mark is a grey hourglass | - | [MT-183](tests.md#mt-183) |
| 2026-08-25 | FR-023 | feature request | Show Inactive Labels | - | [MT-181](tests.md#mt-181) |
| 2026-08-25 | FR-022 | feature request | Crop and pan a local locomotive icon | - | [MT-184](tests.md#mt-184) |
| 2026-08-25 | FR-018 | feature request | A page whose file returns keeps its id, and a deleted one is pruned | - | [MT-185](tests.md#mt-185) |
| 2026-08-25 | FR-013 | feature request | The store holds objects, not strings | - | [MT-186](tests.md#mt-186) |
| 2026-08-25 | OB-105 | bug | No application icon on the IP prompt | - | [MT-180](tests.md#mt-180) |
| 2026-08-24 | OB-103 | bug | A layout that would not read showed no notice | - | [MT-180](tests.md#mt-180) |
| 2026-08-24 | OB-102 | bug | Timetable stations carried their direction suffix | - | [MT-180](tests.md#mt-180) |
| 2026-08-24 | OB-099 | bug | CS3 locomotive database was not downloaded **Claude, 2026-09-14.** MT-170 was superseded by MT-405, which asks for the CS3's own data files in the download. | - | `MT-405` |
| 2026-08-25 | OB-106 | bug | Legacy import made no configuration to load into | - | [MT-178](tests.md#mt-178) |
| 2026-08-24 | OB-104 | bug | Autonomy could start over a Central Station layout | - | [MT-179](tests.md#mt-179) |
| 2026-08-24 | OB-101 | bug | Capture toggle live while trains returned home | - | [MT-179](tests.md#mt-179) |
| 2026-08-24 | OB-100 | bug | Download CS layout offered with no station connected | - | [MT-179](tests.md#mt-179) |
| 2026-08-24 | OB-098 | bug | Switch to CS layout offered with no station connected | - | [MT-179](tests.md#mt-179) |
| 2026-08-24 | OB-097 | bug | A finished route still read as active on the locomotive panel | - | [MT-175](tests.md#mt-175) |
| 2026-08-24 | OB-096 | bug | The no-available-paths window: white text area, standard font size | - | [MT-177](tests.md#mt-177) |
| 2026-08-24 | OB-095 | bug | Show autonomy controls checkbox visible with nothing loaded | - | [MT-177](tests.md#mt-177) |
| 2026-08-24 | OB-094 | bug | Switch to Central Station Layout stayed selectable on a station layout | - | [MT-177](tests.md#mt-177) |
| 2026-08-24 | OB-093 | bug | Autonomy checkbox visible beside a greyed tab; the notice now offers a download | - | [MT-177](tests.md#mt-177) |
| 2026-08-24 | OB-092 | bug | Renaming a page to "5" excluded the page whose id is 5 and emptied it | - | [MT-161](tests.md#mt-161) |
| 2026-08-24 | OB-090 | bug | Autonomy error count, and Fix it offered instead of Start | - | [MT-173](tests.md#mt-173) |
| 2026-08-23 | FR-001 | feature request | Station unavailable while another point is in use, as lock edges | - | `MT-146, MT-345, MT-346` |
| 2026-08-22 | FR-006 | feature request | Editor grid is a toggle, and hovering no longer resizes a tile | - | `MT-379` |
| 2026-08-22 | FR-007 | feature request | Autonomy can be set up by importing, from the menu with nothing set up | - | `MT-380` |
| 2026-08-23 | FR-010 | feature request | Home locomotive picker filters, and offers the one being driven | - | `MT-381` |
| 2026-08-23 | FR-011 | feature request | Add to autonomy uses the same filtering picker | - | `MT-382` |
| 2026-08-23 | FR-012 | feature request | A dozen editor cycles must retain nothing | fixed validated | - |
| 2026-08-22 | FR-008 | feature request | Route editor: Highlight on Diagram dropped, Test renamed Test Condition | fixed validated | - |
| 2026-08-23 | OB-054 | bug | Page link menu: a repeated heading and an empty section | - | [MT-143](tests.md#mt-143) |
| 2026-08-23 | OB-055 | bug | The grid was drawn on the editor's own spacer row and column | - | [MT-143](tests.md#mt-143) |
| 2026-08-23 | OB-056 | bug | The grid toggle did nothing in the autonomy editor | - | [MT-143](tests.md#mt-143) |
| 2026-08-23 | OB-057 | bug | Autonomy could be started with errors outstanding, or an editor open | - | [MT-143](tests.md#mt-143) |
| 2026-08-24 | OB-059 | bug | Deleting a page told the autonomy setup nothing at all | - | [MT-142](tests.md#mt-142) |
| 2026-08-24 | OB-060 | bug | Page ids were list positions, so any rename or delete renumbered the others | - | [MT-142](tests.md#mt-142) |
| 2026-08-24 | OB-061 | bug | A source guard promised more coverage than it checked | - | [MT-142](tests.md#mt-142) |
| 2026-08-23 | OB-058 | bug | The Edit button brings an already-open editor forward | - | [MT-144](tests.md#mt-144) |
| 2026-08-24 | OB-063 | bug | The info mark had no glyph, so the font drew a box | - | [MT-144](tests.md#mt-144) |
| 2026-08-24 | OB-062 | bug | A locomotive rename did not reach a setup nothing had open | - | [MT-145](tests.md#mt-145) |
| 2026-08-24 | OB-064 | bug | Renaming or deleting a page invented an autonomy setup | - | [MT-142](tests.md#mt-142) |
| 2026-08-24 | OB-065 | bug | Page delete, rename, combine and the database sync ran during autonomy | - | [MT-141](tests.md#mt-141) |
| 2026-08-24 | OB-066 | bug | deletePage left cross-page pointers to the deleted page | - | [MT-142](tests.md#mt-142) |
| 2026-08-24 | OB-068 | bug | A page that fails to load had its whole setup pruned | - | [MT-148](tests.md#mt-148) |
| 2026-08-24 | OB-069 | bug | The timetable was an unrepaired holder of locomotive names **Claude, 2026-09-14.** MT-149 was superseded after its second-round fix (the timetable follows a locomotive rename, proven by a probe), and nothing replaced it - left for Adam to close or re-test. **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-08-24 | OB-070 | bug | Closing the app never asked the editor about unsaved work | - | [MT-155](tests.md#mt-155) |
| 2026-08-24 | OB-071 | bug | A page name containing a colon lost its setup to another page | - | [MT-150](tests.md#mt-150) |
| 2026-08-24 | OB-072 | bug | A failed timetable leg reported the run as completed | - | [MT-156](tests.md#mt-156) |
| 2026-08-24 | OB-073 | bug | The return-home planner could not see the FR-001 restriction **Claude, 2026-09-14.** Its test MT-157 was superseded by MT-165, which is where it is tracked now. | - | `MT-165` |
| 2026-08-24 | OB-074 | bug | A Central Station rename bypassed the unusable-name guard | - | [MT-153](tests.md#mt-153) |
| 2026-08-24 | OB-075 | bug | Legacy import wrote homes without the one-home sweep | - | [MT-151](tests.md#mt-151) |
| 2026-08-24 | OB-076 | bug | The editor's Cancel reverted edits made from the main window | - | [MT-154](tests.md#mt-154) |
| 2026-08-24 | OB-077 | bug | Start-up could hang for ever if the window failed to build | - | [MT-160](tests.md#mt-160) |
| 2026-08-24 | OB-078 | bug | A modal refusal dialog was raised from worker threads | - | [MT-160](tests.md#mt-160) |
| 2026-08-24 | OB-079 | bug | The event thread could block on the Layout monitor | - | [MT-160](tests.md#mt-160) |
| 2026-08-24 | OB-080 | bug | Comments that contradicted the code, and the two defects behind them | - | [MT-152](tests.md#mt-152) |
| 2026-08-24 | OB-081 | bug | A locomotive rename did not reach the diagram labels | - | [MT-153](tests.md#mt-153) |
| 2026-08-24 | OB-082 | bug | The autonomy editor title used a dash rather than a colon | - | [MT-158](tests.md#mt-158) |
| 2026-08-24 | OB-083 | bug | Cosmetics of the unavailable-while-occupied window | - | [MT-158](tests.md#mt-158) |
| 2026-08-24 | OB-067 | bug | A page named after another page's id collected its settings | - | [MT-161](tests.md#mt-161) |
| 2026-08-24 | FR-015 | feature request | Backup writes one archive holding all the state | - | [MT-159](tests.md#mt-159) |
| 2026-08-24 | FR-014 | feature request | The caption menu items name the station | - | [MT-162](tests.md#mt-162) |
| 2026-08-24 | FR-017 | feature request | The no-available-paths reasons, as a window | - | [MT-163](tests.md#mt-163) |
| 2026-08-24 | FR-019 | feature request | The backup dialog offers to show the file | - | [MT-166](tests.md#mt-166) |
| 2026-08-24 | FR-020 | feature request | Backing up a layout that lives on the Central Station **Claude, 2026-09-14.** MT-170 was superseded by MT-405; the backup's remaining half is the CS3 data files it asks for. | - | `MT-405` |
| 2026-08-24 | FR-021 | feature request | The route file is downloaded, so it reaches the backup | - | [MT-172](tests.md#mt-172) |
| 2026-08-24 | OB-091 | bug | The autonomy editor reserves the same room for the grid | - | [MT-172](tests.md#mt-172) |
| 2026-08-24 | OB-087 | bug | A deadlock reported on an old build; a real one found and reverted | - | [MT-167](tests.md#mt-167) |
| 2026-08-24 | OB-088 | bug | Capture stopped whenever the setup was rebuilt | - | [MT-168](tests.md#mt-168) |
| 2026-08-23 | OB-045 | bug | Autonomy Setup greyed while trains run | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-046 | bug | Go to the other end asks save/discard/cancel | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-047 | bug | Neither editor opens while trains run | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-048 | bug | Segment lengths capped at three digits | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-049 | bug | Renaming a page keeps its autonomy setup | - | [MT-135](tests.md#mt-135) |
| 2026-08-23 | OB-050 | bug | Start Autonomy greyed when it cannot start | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-051 | bug | Import and export moved where they can be found | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-052 | bug | The tidy-up report says what it is | - | [MT-137](tests.md#mt-137) |
| 2026-08-23 | OB-042 | bug | Station labels on curves | - | [MT-132](tests.md#mt-132) |
| 2026-08-23 | OB-044 | bug | Station labels on bumpers | - | [MT-132](tests.md#mt-132) |
| 2026-08-23 | OB-043 | bug | Segment length entry | - | [MT-133](tests.md#mt-133) |
| 2026-08-23 | OB-041 | bug | Switching a paired link off switches its partner off | - | [MT-131](tests.md#mt-131) |
| 2026-08-23 | OB-023 | bug | The right-click menu and grid teardown, unified | - | [MT-128](tests.md#mt-128) |
| 2026-08-23 | OB-024 | bug | Port map and side-lookup cleanups | - | [MT-129](tests.md#mt-129) |
| 2026-08-23 | OB-039 | bug | Changing a locomotive's orientation updates its label | - | [MT-125](tests.md#mt-125) |
| 2026-08-23 | OB-040 | bug | Picking a guarding signal de-clutters the diagram | - | [MT-126](tests.md#mt-126) |
| 2026-08-23 | OB-028 | bug | The autonomy editor draws the railway, not a grid over it | - | [MT-127](tests.md#mt-127) |
| 2026-08-22 | OB-026 | bug | The trace stub at the end of a run cuts across a curved tile | - | [MT-119](tests.md#mt-119) |
| 2026-08-22 | OB-038 | bug | Export/import restoring a placement - already covered by a test | - | [MT-118](tests.md#mt-118) |
| 2026-08-22 | OB-037 | bug | The train star was drawn too small for its own outline | - | [MT-124](tests.md#mt-124) |
| 2026-08-22 | OB-036 | bug | Findings read "(Page 2)" rather than "On 2 -" | - | [MT-123](tests.md#mt-123) |
| 2026-08-22 | OB-035 | bug | Placing from the viewer did not update the caption | - | [MT-122](tests.md#mt-122) |
| 2026-08-22 | OB-031 | bug | Pairing a link now switches both ends on | - | [MT-121](tests.md#mt-121) |
| 2026-08-22 | OB-030 | bug | The Autonomy menu's tooltips wrap | - | [MT-120](tests.md#mt-120) |
| 2026-08-22 | OB-034 | bug | Renaming a station blanked its label until renamed back | - | [MT-116](tests.md#mt-116) |
| 2026-08-22 | OB-033 | bug | The Layouts menu declines while an editor is open, and both lead back | - | [MT-115](tests.md#mt-115) |
| 2026-08-22 | OB-029 | bug | Findings shown for a configuration nobody had loaded | - | [MT-114](tests.md#mt-114) |
| 2026-08-22 | OB-032 | bug | An empty "Trains May Depart" heading is hidden | - | [MT-113](tests.md#mt-113) |
| 2026-08-22 | OB-027 | bug | Three tool labels renamed | - | [MT-113](tests.md#mt-113) |
| 2026-08-22 | OB-022 | bug | DD-A6: three safety rules in code nothing called | - | [MT-112](tests.md#mt-112) |
| 2026-08-22 | OB-021 | bug | Layouts menu: Edit Layout Page under Manage Pages, and a doubled divider | - | [MT-111](tests.md#mt-111) |
| 2026-08-22 | OB-020 | bug | The autonomy tools column is narrower, and three labels changed | - | [MT-110](tests.md#mt-110) |
| 2026-08-22 | OB-019 | bug | Track lengths: a hotkey, the focus theft, and the font size | - | [MT-109](tests.md#mt-109) |
| 2026-08-22 | OB-018 | bug | Route editor: Save to the bottom right corner, Cancel beside it | - | [MT-108](tests.md#mt-108) |
| 2026-08-22 | OB-017 | bug | The track palette was empty after autonomy mode, under the wrong heading | - | [MT-107](tests.md#mt-107) |
| 2026-08-22 | OB-016 | bug | The track diagram viewer was drawn in edit mode while the editor was open | - | [MT-106](tests.md#mt-106) |
| 2026-08-22 | OB-015 | bug | The mode buttons are text-sized, not bold | - | [MT-105](tests.md#mt-105) |
| 2026-08-22 | OB-014 | bug | The page list is text-sized | - | [MT-105](tests.md#mt-105) |
| 2026-08-22 | OB-013 | bug | The tile menu reordered: five moves, and Length becomes Segment Length inside Advanced Parameters | - | [MT-104](tests.md#mt-104) |
| 2026-08-22 | OB-012 | bug | Starting autonomy from the track diagram menu jumped to the autonomy tab | - | [MT-103](tests.md#mt-103) |
| 2026-08-22 | OB-011 | bug | "Route Choice" reads "Choose Routing Logic..." | - | [MT-102](tests.md#mt-102) |
| 2026-08-22 | OB-010 | bug | "Show track lengths" reads "Track Lengths" | - | [MT-102](tests.md#mt-102) |
| 2026-08-22 | OB-009 | bug | Placing a locomotive did not update the labels; Move retired in favour of the edit dialog | - | [MT-101](tests.md#mt-101) |
| 2026-08-22 | OB-008 | bug | A direction edit with the arrows hidden happened invisibly | - | [MT-100](tests.md#mt-100) |
| 2026-08-22 | FR-005 | feature request | A white * on the station icon where a train is set up to be standing | - | [MT-099](tests.md#mt-099) |
| 2026-08-22 | FR-004 | feature request | Move "make a one way run from here" off the right-click menu and onto a button that asks for both points and a direction | - | [MT-098](tests.md#mt-098) |
| 2026-08-22 | OB-005 | bug | Switching between the autonomy view and the track diagram editor flashes - the window closes and reopens | - | [MT-095](tests.md#mt-095) |
| 2026-08-22 | FR-003 | feature request | Editor sidebar: buttons become a clickable list, and the layout/autonomy pair becomes a radio switch | - | [MT-097](tests.md#mt-097) |
| 2026-08-22 | OB-003 | bug | Editor window size varies by page and is often too small - default to the diagram's own size, capped at the screen | - | [MT-096](tests.md#mt-096) |
| 2026-08-22 | FR-002 | feature request | Appearance of stations and incoming arrows - circles, squares and diamonds are not semantic, and the arrows are messy.  **Not done** (Adam, 2026-09-13): `MT-094`, its only test, was superseded, so nothing current implements it and it is tracked here again rather than through that test **Claude, 2026-09-14.** Not built. Adam: *"FR-002 should show as pending"* - State set to **pending**, the word added that day for a request picked up and not yet built; "needs test" said there was something to test. | pending | - |
| 2026-08-22 | FR-009 | feature request | Highlight on Diagram button in the route editor, and rename Test to Test Condition | - | [MT-064](tests.md#mt-064) |

**OB-008 to OB-012 are fixed, 2026-08-22.** Two of them share `MT-102`, because they are the same
test: read two labels and check they say the right thing. Splitting that into two entries would mean
two trips to the same screen.

**One part of `OB-009` is answered rather than fixed, and it is called out in `MT-101`.** "Adding a
locomotive to the graph doesn't correctly place it at the station where it belongs" - I could not find
a placement going to the wrong square, and the likeliest explanation is the missing refresh that was
the third part of the same report: a placement that does not appear looks exactly like a placement that
went somewhere else. `MT-101` asks Adam to re-check that specific half now the labels update, and says
what to tell me if it still happens.

**All five are fixed, 2026-08-22.** The three feature requests earned an `MT-###` after all, for the
same reason the two bugs did: each changed something only a person at the railway can confirm.

`FR-005` was the interesting one - what it asked for was already built and had been for a while. The
star is drawn by `paintTrainMark`; `paint()` just never got that far, because `isBlank()` did not count
a train as content and a square with only a train on it was therefore "nothing to draw". It appeared on
stations, which carry a badge and so were never blank, and was missing on exactly the squares the
request was about.

**`OB-003` and `OB-005` are fixed, 2026-08-22, on Adam's "fix all the bugs".** Both earned an
`MT-###` after all, which is the rule working rather than an exception to it: each one changed
behaviour that only a person at the railway can confirm, and `OB-005` in particular introduced three
new ways to lose work that no automated test can see the whole of. The three feature requests filed
alongside them - `FR-003`, `FR-004`, `FR-005` - are untouched and still have no tag.

**On the five filed 2026-08-22, and the Kind field.** All five arrived as `bug`; two of them are, and
three are feature requests. `FR-003`, `FR-004` and `FR-005` do not describe anything behaving wrongly -
they ask for a control to be built differently, moved, or added. `OB-003` and `OB-005` are behaviour:
a window that comes up the wrong size, and a switch that visibly closes and reopens.

Recorded by substance rather than by the dropdown, because the two routes differ - a bug earns an
`MT-###` regression check once it is fixed, a feature request does not by default. Nothing is lost
either way: the Kind as filed is above, and if I have called one wrong, say so and it moves.

**None of the five has an `MT-###`, deliberately.** That is the `MT-094` lesson applied - a tag is
earned when the work turns out to need a repeatable hands-on check, not handed out at pick-up. `OB-003`
and `OB-005` are the two most likely to earn one when they are built.

**`FR-005` may already be half-built.** `AutonomyEditorPanel` already calls `annotation.withTrain()`
for any square the setup puts a locomotive on, so the editor is already drawing *a* mark there. Worth
looking at what it currently draws before adding a second one - the request may be to change that mark
to a white `*` rather than to add one. Flagged rather than assumed.

**`OB-005` is the cost of a decision, not an accident.** The F2 sidebar was specified as "switching
tabs or mode is the same as the old exit and reopen: prompt for save/discard, then regenerate", and
the flash is that regeneration being visible. Removing it means keeping the window and swapping its
contents, which is a different design from the one that was asked for - worth saying before it is
built, not after.

**`FR-002` is retired-and-relit, not new.** It was promoted to `MT-094` on 2026-08-22, which turned
out to be the wrong call - a feature request that had not even been designed yet, sitting in the
Tests ledger looking like a hands-on regression test. `MT-094` stays in `tests.md`, superseded
rather than deleted (its tag is already cited by a commit), and this row is the live one now:
tracked here directly, with its own State, never promoted again unless the eventual work turns out
to need a genuine repeatable hands-on test the way a bug fix does.

*OB-001 and OB-002 are the same request submitted twice, two minutes apart, against commits 3a2106ab
and cd27e285. Recorded as one entry - a duplicate is a duplicate, and two ledger rows for one decision
is exactly the noise the ledger exists to avoid. If the second was meant to say something the first did
not, put the difference back in the Inbox and it gets its own entry.*

*`MT-064` is `FR-009` now - it was filed before any numbering existed at all, directly as a sentence
in what was then `feature-requests.md`, and its ref sat as `-` until 2026-08-22, when it got the
same real number everything else here has.*

*`OB-001`/`OB-002`, `OB-004`, `OB-006` and `OB-007` are now `FR-002`, `FR-003`, `FR-004` and
`FR-005` - renamed 2026-08-22 once bugs and feature requests got separate counters, since all four
are feature requests that predate the split. The table above and the `MT-###` entries they link to
use the new refs; older prose in this file and in commit messages still names them by the OB number
they were filed under, and this mapping is how to trace one to the other.*

## Where the older backlog is

The deleted `2026-08-18-manual-test-plan.md` had a "Feature backlog (Adam, 18 August)" section -
things written down so they would not be lost, none of them scheduled. It has not been picked up into
this mechanism, deliberately: filing something here is a decision, and those were explicitly not
decisions. Anything from it you want on the ledger, paste into the Inbox above and it will be.
