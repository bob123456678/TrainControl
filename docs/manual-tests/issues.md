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

### FR-099 - 2026-09-24 - prettify usage graph

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-24 10:46  
**Build:** commit 2baafd8a, in English - build\classes, compiled 24 Sep 10:20 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

usage graph uses basic/ugly graphics.  Keep current functionality, but make it look nicer.

### FR-109 - 2026-10-04 - for 3.1.0: rename, delete and add pages from the layout editor

**Kind:** feature request  
**Raised from:** Adam, 2026-10-04 - a request for 3.1.0  
**Filed:** 2026-10-04  

Adam, 2026-10-04: *"Fr for 3.1.0- add basic layout administration, like renaming and deleting and adding pages, to the layout editor."*

**For 3.1.0**, not 3.0.  Pages are managed today from the main window's Layouts menu; this brings renaming, deleting and adding pages into the layout editor itself.  Related: BCR-A2 (a page name differing from another's only in case overwrites that page's file) - the same name checks will apply to these doors.

### FR-111 - 2026-10-04 - for 3.1.0: manage a point from one popup, not right-click menus

**Kind:** feature request  
**Raised from:** Adam, 2026-10-04  
**Filed:** 2026-10-04  

Adam, 2026-10-04: *"File an FR for 3.1.0- we need point management to be via a single popup, not right click menus with dozens of options that we have to open and close multiple times.  And we never implemented the thing that keeps those menus open"*.

**For v3.1.0**, on Adam's word.  Setting a point up in the autonomy editor - station or not, its name, the ways in, which way trains face, turning, length, home, signals, exclusions - is spread over right-click menus and their submenus, and each choice closes the menu, so one point takes many opens.  One popup per point, holding all of it.

**Claude, 2026-10-04.**  The earlier request to keep those menus open after a choice has no entry in issues.md or tests.md that I could find, so it is folded in here and tracked from now on; a single popup would make it moot.

### FR-113 - 2026-10-07 - for 3.1.0: a notice area above the editor's diagram whose height does not move the diagram

**Kind:** feature request  
**Raised from:** Adam, 2026-10-07  
**Filed:** 2026-10-07  

Adam, 2026-10-07: *"File a 3.1.0 FR: the notice area above the editor diagram should be redesigned so that it height change doesn't affect UX"*.

**For v3.1.0**, on Adam's word.  The area of notices above the diagram in the autonomy editor - the setup's findings and the messages a click leaves - grows and shrinks with what it holds, and every change of its height moves the whole diagram below it: the square under the pointer is no longer the one it was over, mid-gesture.  Redesign it so that a change in what it says never moves the diagram.

### OB-324 - 2026-10-07 - for 3.1.0: messages from a pop-up diagram window or another window open over the main window instead

**Kind:** bug  
**Raised from:** the popups sweep, 2026-10-07, on Adam's note on MT-703  
**Filed:** 2026-10-07  

From the popups sweep of 2026-10-07, on Adam's note on MT-703 (*"Do a sweep for popups not tied to the right parent"*).  Its eight cases of a message with no visible window behind it were fixed in round 85 (`f61a3811`, MT-705).  This is the other group, left for **v3.1.0**: about 35 questions and messages that open over the main window although the operator was working in another one - a track diagram page opened in its own window (Small or Large on the Layout tab), the locomotive database, the route editor, the track editor.  They are seen today because the main window is usually in front; with it minimised they are hidden, and they appear away from where the operator clicked.  Among them: clicking a switch, signal or route tile in a page's own window (power off, a route conflict); its right-click autonomy menu and the questions it leads to (which side a train arrived from, a train's tail, the setup report); a hand send's refusals from that window; keyboard shortcuts pressed there; Multi-Unit from the locomotive database; Save of a new route; Open in Route Editor from the track editor.

The page windows now follow the main window's always-on-top (round 85), which is what makes owning their own messages safe.  The sweep's table, with each place in the code, is section B of the round's notes.

### OB-330 - 2026-10-10 - for 3.1.0: save the timetable and the autonomy settings on an exit with trains running

**Kind:** bug  
**Raised from:** RSA49-C1  
**Filed:** 2026-10-10  

Closing TrainControl while a train runs - after the question *"Trains are still running ... Close anyway?"* - saves nothing of the configuration, as 2.8.x did: the timetable recorded since the last save and any change to the autonomy settings are lost with where the trains are (RSA49-C1).  Adam, 2026-10-10, chose to keep that and say so (*"b, easier to manage edge cases.  document as a future item to revisit"*): round 122 (`d6ce5a16`) names the settings in the question, beside the timetable.

**To revisit:** fold what the railway owns and nothing of where its trains stand - `AutonomySession.captureTheRailwaysSettings`, the timetable and the settings - and save without reconciling, which is what the exit already does while a declined setup edit waits (`TrainControlUI`'s exit, `setupEditDeclinedDuringRun`).  It changes no train.  The question then names only where the trains are.

### OB-331 - 2026-10-10 - for 3.1.0: the August design review's code-structure notes (DD-A1, DD-B9, DD-C1, DD-C6, DD-C7, DD-C8)

**Kind:** bug  
**Raised from:** DD (2026-08-22 duplication and design review)  
**Filed:** 2026-10-10  

None of these changes what TrainControl does; they are why changes keep being risky.  Adam, 2026-10-10: *"Agree- defer the DD's for 3.1.0"*.  The cheap and safe ones first.

- **DD-C6** - the autonomy store's collections declared in six places: move the declarations together.
- **DD-C8** - the keyboard shortcuts as one long if/else chain in `TrainControlUI`: a table of shortcuts, so the set can be listed in one place.
- **DD-C1** - `TrainControlUI` (about 28,000 lines): the seams that come out cleanly - image loading and caching, then the locomotive-to-button mapping.
- **DD-C7** - about 330 message dialogs naming their parent window five ways: a `Dialogs` helper, adopted as code is touched.
- **DD-A1** - the store's collections each handled by hand in about fourteen code paths: one registry the paths walk.
- **DD-B9** - the routing rule (which way a train may go on from a square) written out three times; tried on 2026-09-07 and backed out - reachability is per copy, not per square - with the shape of a working fix in `open-questions.md`.

Closed with it: DD-B6 (a shared test harness) declined, as `open-questions.md` already recommended; DD-C4 (the dead code) done in round 122 (`d6ce5a16`).

### FR-123 - 2026-10-11 - for 3.1.0: a fullscreen mode, and ways to resize the main window

**Kind:** feature request  
**Raised from:** Adam, 2026-10-11  
**Filed:** 2026-10-11  

Adam, 2026-10-11: *"FR for 3.1.0: add a fullscreen mode and options to resize the main window."*

**For v3.1.0**, on Adam's word.  The main window is a fixed size today (`TrainControlUI` calls `setResizable(false)`), so the track diagram, the keyboard pages and the Auto tab never grow with the screen.  Add a fullscreen mode, and options to resize the main window.

To settle when it is built: how fullscreen is entered and left (a menu item, a key such as F11, Escape - which already powers off); whether resizing is free dragging, a few preset sizes, or both; what grows with the window (the track diagram's area, the keyboard, the lists) and what stays fixed; that the size and mode are remembered, per screen; and that the pop-up windows, dialogs and always-on-top keep working over a fullscreen window.

### FR-124 - 2026-10-11 - for 3.1.0: Restore from Backup in the File menu

**Kind:** feature request  
**Raised from:** Adam, 2026-10-11  
**Filed:** 2026-10-11  

Adam, 2026-10-11: *"FR for 3.1.0: a restore from backup option in the file menu"*

**For v3.1.0**, on Adam's word.  File > Backup TrainControl Data writes one zip into the `tc_backup` folder - the locomotive database (`LocDB.data`), the window state and keyboard pages (`UIState.data`), the locomotive icons, the routes, and for a layout stored on this computer its track diagram folder with the autonomy setups - but nothing reads one back: the Readme's restore is by hand (close TrainControl, unzip beside the JAR, start it again).  Add a Restore from Backup item to the File menu that does it.

To settle when it is built: choosing the archive (opening on `tc_backup`); showing what it holds and what it will replace before anything is written; backing up the current data first, so a restore can be undone; where the layout folder goes (back where it was, or a folder the user picks) and pointing TrainControl at it; refusing while autonomy or a timetable is running; restarting, or reloading everything, afterwards; an archive from an older or newer version; and the CS3's own locomotive, route and accessory files, which a backup carries but TrainControl does not read.

### FR-125 - 2026-10-11 - for 3.1.0: one-click updates - download, swap the jar, restart

**Kind:** feature request  
**Raised from:** Adam, 2026-10-11  
**Filed:** 2026-10-11  

Adam, 2026-10-11: *"FR for 3.1.0: one-click updates.  Downloads the new jar, makes a script to swap it with the current one in the current folder (if it exists), and runs that script then exits so it can be done, then restarts."*

**For v3.1.0**, on Adam's word.  Today Check for Updates (Preferences > Startup) finds a newer release, and the main window's Download Update saves the new jar and ends with *"Replace your existing .jar with the new file"* (`ui.infoDownloadedUpdateReplaceExistingJar`): the swap and the restart are the user's.  One click should do all of it: download the jar, write a small script that waits for TrainControl to close, replaces the jar it is running from in the current folder with the new one (where that jar exists), and starts TrainControl again; run the script, and exit.

To settle when it is built: a .bat on Windows and a shell script on macOS and Linux; how the script knows TrainControl has really exited (its process, or the jar no longer locked) before it swaps; keeping the old jar (renamed, say) until the new one has started, so a bad download can be undone; checking the download is whole (size, or a checksum from the release) before anything is replaced; running from somewhere other than a jar (an IDE, build\classes), where there is nothing to swap and it should say so; a jar the user cannot write over; starting again with the same Java and arguments (IP address, debug, simulate, a forced language); refusing while autonomy, a timetable or a route is running, and saving everything first; and the LocDB.data and UIState.data beside the jar, which must be left alone.

### OB-337 - 2026-10-11 - for 3.0.1: the autonomy guide does not say how to start TrainControl for simulation

**Kind:** bug  
**Raised from:** Adam, 2026-10-11  
**Filed:** 2026-10-11  

Adam, 2026-10-11: *"For 3.0.1, file an OB - the autonomy readme should tell the user how to start the app in debug mode so they can do simulations.  That is critical to testing autonomy.  Right now, there is just a quick mention of the simulate setting."*

**For v3.0.1**, on Adam's word.  Automation.md's only word on it is one line under Preferences > Autonomy: *"(`Simulate`, on the Settings tab, is only for testing without a Central Station.)"*  The box is refused unless TrainControl was started in debug mode and is not connected to a Central Station (`Layout.setSimulate`), and the refusal (`autolayout.errorSimulationModeDebugOnly`) is the only place a user is told that debug mode means *"passing a second argument via the command line"*.  The Readme's Download and Run section has the command, `java -jar TrainControl.jar 0 debug simulate` (`TrainControl.main` counts the arguments: a second one turns on debug mode and a third simulation, whatever they say), but Automation.md does not point to it, and a user who double-clicks the jar passes none.

What the guide should say, near where it first asks the user to run a setup: why to simulate (try a setup and watch the trains route with no Central Station and nothing moving); how to start TrainControl that way (the command, and a `.bat` or `.sh` file beside the jar for those who double-click); then tick `Simulate` on the Settings tab and start autonomy; and what simulation does - after a wait between the configured minimum and maximum delays each sensor on a train's route is reported occupied and then cleared behind it, the trains themselves do not run, and Path Integrity Validation is skipped - and that it cannot be changed while trains are running.  The Readme's Download and Run section can link to it.

### OB-338 - 2026-10-12 - No warning and no way to retry when the Central Station answers but the first database sync fails

**Kind:** bug  
**Raised from:** Adam, 2026-10-12  
**Filed:** 2026-10-12  

Adam, 2026-10-12: *"File OB: there is no warning popup in the UI, and no way to reattempt a connection, if the cs3 ip is reachable but the initial DB sync fails.  if this happens, show a popup with the error and offer a resync.  keep re-prompting as needed until a connection is established or the user exits."*

Today, at start-up `MarklinControlStation` runs `syncWithCS2()`; when it fails (it returns -1 on any exception while reading the Central Station's database) the only sign is the log line `log.csNotConnected` (*"Central Station network connection not established."*) and TrainControl carries on with communication off.  That start-up block is the only place communication is turned on (`this.on = true`), so a later sync from the menu cannot bring the connection up: the user has to notice the log line and restart.

Wanted: when the address answers but the first sync fails, a popup with the error and a Resync choice; on a failed resync, ask again, until the sync succeeds (communication on, as after a good start) or the user exits.

**For v3.0.1**, on Adam's word (2026-10-12: *"Yes, 3.0.1."*).

### OB-339 - 2026-10-12 - for 3.0.1: a simulation start forgets the saved Central Station address

**Kind:** bug  
**Raised from:** Adam, 2026-10-12  
**Filed:** 2026-10-12  

Found by the per-fix check of OB-337: at the end of `MarklinControlStation.init`, *"Connection failed - ask for IP on next run"* removes the saved address (`IP_PREF`) whenever communication did not come on - and a start in simulation never turns it on, so after every simulation session the next normal start asks for the Central Station's address again.  Adam, 2026-10-12, asked whether to keep it: *"Sure, avoid clearing the address from simulation sessions"*.

**For v3.0.1**, on Adam's word.  A simulation start keeps the saved address; a real start that does not connect, and Exit in OB-338's question, still forget it, as before.

### OB-340 - 2026-10-12 - for 3.0.1: departing a terminus, autonomy leaves its exit guard red, so a train the guard stops stays there

**Kind:** bug  
**Raised from:** Adam, 2026-10-12  
**Filed:** 2026-10-12  

Adam, 2026-10-12: *"when departing a terminus, autonomy should make its exit guard green.  otherwise, trains stopped by that guard would remain stuck.  it can flip back to red after the run ends (if wiring already exists for this), otherwise keep it at green."*

**For v3.0.1**, on Adam's word.  behaviour.md 7c: an exit guard is RED while its platform is claimed - a train standing there, or a locked path that starts or ends there - and a route over the guard sets it GREEN as it is claimed edge by edge, which is how a train leaving a through station by its exit signal gets a green.  At a terminus the exit guard is often on the station's own track, where no departing route commands it, so the departing train's own claim holds it red; where the signal cuts power to a stop section, the train cannot leave.

Wanted: when autonomy sends a train out of a terminus, the terminus's exit guard is turned green for it.  After the run it may go back to red where something already does that; otherwise it stays green.

### OB-341 - 2026-10-12 - for 3.0.1: two captions for one station (St01) in a setup, and the setup check does not say so

**Kind:** bug  
**Raised from:** Adam, 2026-10-12  
**Filed:** 2026-10-12  

Adam, 2026-10-12: *"in C:\Users\adamo\OneDrive\Documents\NetBeansProjects\TrainControl\random_test_layout\Oles kreds - TC3 sandbox, there are two labels for the same station (St01).  This is likely an import artifact- determine if so.  And add code to make this be an error in the autonomy validator (i.e. if someone edits the file by hand externally)."*

**For v3.0.1**, on Adam's word.  In that layout's `config/autonomy/setup.json`, `captions` maps two caption squares, `0:6,16` and `0:8,16`, to St01's square `0:9,16` - where every door of the editor keeps one station to one caption (OB-256).  Wanted: find whether an import (or a migration) produced it, and the setup check reports a station with more than one caption as an error, naming the station and the squares.

### FR-126 - 2026-10-12 - for 3.0.1: bulk tools to activate and to deactivate every train; the locomotive commands tab sorted active first when opened

**Kind:** feature request  
**Raised from:** Adam, 2026-10-12  
**Filed:** 2026-10-12  

Adam, 2026-10-12: *"Add an FR to add a bulk tool to mass activate and mass deactivate all trains.  Upon opening the locomotive autonomy commands tab, re-sort them to show active first, then inactive, then ones on inactive stations (as currently), sorted alphabetically within those groups.  Don't trigger the re-sort if you just click the pause button on the locomotive autonomy commands tab."*

**For v3.0.1**, on Adam's word.  Active and inactive are FR-117's: a paused train is left where it stands, its icon grey, set from the Auto tab's pause button or the `Is Active` tick, and kept with the configuration.  Two bulk tools set every train active, or every train inactive, at once.  The Auto tab's locomotive commands list is re-sorted each time the tab is opened - active trains, then inactive ones, then those on inactive stations, each group alphabetical - and not when a pause button on it is clicked, so a card does not move from under the pointer.

### FR-127 - 2026-10-12 - for 3.0.1: the active autonomy configuration's name in the autonomy and layout editors and the layout pop-ups

**Kind:** feature request  
**Raised from:** Adam, 2026-10-12  
**Filed:** 2026-10-12  

Adam, 2026-10-12: *"FR and build with sonnet: the current autonomy configuration name (not just the page name) in the autonomy/layout editor and layout popups. (if active)"*

**For v3.0.1**, on Adam's word.  Where those windows name the page they show, they also name the autonomy configuration that is active, when one is; with none active they read as today.

### OB-342 - 2026-10-12 - for 3.0.1: the autonomy editor's smallest height is a few pixels short

**Kind:** bug  
**Raised from:** Adam, 2026-10-12  
**Filed:** 2026-10-12  

Adam, 2026-10-12: *"It looks like the min height of the autonomy editor window needs to be a few more pixels taller"*

**For v3.0.1**, on Adam's word.  The editor's floor is set in `LayoutEditor`: `sizeForDiagram` for a page with no remembered size (630 plus `EXTRA_MINIMUM_HEIGHT`, 50, plus more at the larger sizes) and 650 plus 50 for a remembered one.  Wanted: at its smallest, the autonomy editor shows all of its controls.

### OB-343 - 2026-10-12 - for 3.0.1: a train paused from its Auto tab card goes again after the Is Active tick is used on another train

**Kind:** bug  
**Raised from:** FR-126's build, 2026-10-12  
**Filed:** 2026-10-12  

Found by FR-126's build, 2026-10-12.  The Auto tab card's pause button changes the train alone (its `autonomyPaused` flag); the setup's list of paused trains learns of it later.  The `Is Active` tick, on any train, goes through the door that writes the train and the list together and tells the window the railway's pauses and the setup's agree (`notePausesAsBuilt`); the rebuild it asks for then takes the setup's list as the word on every train.  So: pause train A with its card's button, tick `Is Active` off on train B, and at the rebuild A is set going again, though nobody asked.  FR-126's bulk tools avoid it by writing every standing train through the door.

**For v3.0.1** (Adam, 2026-10-12: *"Everything is the next release unless I specify one."*).  Wanted: a train paused from its card stays paused whatever is done to another train.

### OB-344 - 2026-10-12 - for 3.0.1: an old Point:name label on a Central Station layout is drawn without its Point: prefix

**Kind:** bug  
**Raised from:** Adam, 2026-10-12  
**Filed:** 2026-10-12  

Adam, 2026-10-12, told that on a layout served by the Central Station the old autonomy's `Point:name` labels are drawn with the prefix stripped: *"stop stripping that prefix."*

**For v3.0.1**, on Adam's word.  `LayoutGrid` (the branch for a label starting with `LAYOUT_STATION_PREFIX`, added in `0f743bff`) draws `Point:Bahnhof` as `Bahnhof`, greyed in the autonomy editor.  Such a label is never turned into a caption there (no local folder, so no setup).  Wanted: it is drawn as the text it is, like any other label.

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
| 2026-10-12 | OB-340 | bug | A train leaving any station turns that station's exit guard signals green: the first edge of its path sets them with its own accessories and holds them like them, and protection does not turn red a signal an active path holds green, so a guard shared with an occupied platform is green for the train leaving until its tail is past the first edge (`dc5ed1e7`, `5e331d9e`). | - | MT-733 |
| 2026-10-12 | OB-339 | bug | A simulation start keeps the saved Central Station address; a real start that does not connect, and Exit in OB-338's question, still forget it (`aa8acc53`, `MarklinControlStation.forgetsTheAddress`). | - | MT-732 |
| 2026-10-12 | OB-338 | bug | When the Central Station answers but the first sync fails, a window gives the reason and what to check, with Resync and Exit, asking again until it connects or the user exits; never in a simulation, without an address or without a window (`4fef0969`, `aa8acc53`). | - | MT-732 |
| 2026-10-11 | OB-337 | bug | Automation.md's `Trying it out without a Central Station`: the command window, the command, `Simulate`, what a simulation tests and what it does not, and going back to normal; linked from Getting started, the Settings tables and the Readme (`e7b40f51`, `546d50bf`). | - | MT-732 |
| 2026-10-08 | FR-114 | feature request | Import locomotive data from a CSV file, as Export to CSV writes it.  Closed as a duplicate of FR-049 on Adam's word (2026-10-12: *"close FR-114 as a duplicate."*); FR-049, for v3.1.0, carries it. | declined | - |
| 2026-10-11 | OB-336 | bug | The turn-round warning on a home only at a parking berth (not an autonomy destination) no route can back a train into - one turning it at a reversing point on the way (`AutonomySession.berthsATrainCanBackInto`, off the build); no longer at an active terminus such as BottomMainC (round 141, `5526ba19`): `core.testAutonomyDiagramSession.testAHomeIsWarnedAboutTurningOnlyAtABerthNoTrainCanBackInto`, red before (*"a home on a terminus autonomy may choose is warned"*). | - | MT-730 |
| 2026-10-11 | FR-122 | feature request | Built for 3.0.0 on Adam's word of 2026-10-11 ("Let's implement the 46px diagram.  I added mediumButton to the UI.  Finish internationalizing it, add the option and phrasing to SizeList, and advise when ready.  Start with the cheap 60px scaling."), where it was filed for 3.1.0: a Medium size, 46 pixels a square, in the main window's size list between Small and Large and as a pop-up button beside theirs (his, the three labelled S, M and L - initials in the other languages too), in the eight languages; drawn from the Large art scaled down smoothly (`LayoutDiagramComponent.artSize`), each size's pictures cached apart; the editor's window sized in proportion (round 135, `af2bf807`): `ui.testDiagramLooksRight` - `testTheMediumSizeIsBetweenSmallAndLarge`, `testAMediumSquareIsTheLargeArtScaledDown`, `testTheMediumButtonOpensAMediumPopUp`.  Its own 46 pixel icon set, drawn rather than scaled, is still to come. | - | MT-726 |
| 2026-10-11 | OB-335 | bug | Not reproduced - three routes clicked on a copy of Adam's layout at Large lit every switch they changed.  Covered by OB-333's fix, the likely cause (round 134, `d28e2de1`). | fixed validated | MT-725 |
| 2026-10-11 | OB-334 | bug | The same defect as OB-333, filed twice from MT-715.  The highlight is drawn again when a square changes twice inside it: the first highlight ended before the second's yellow goes on, where ending it afterwards put the plain picture back over the yellow (round 134, `d28e2de1`): `core.testLayoutTiles.testASecondHighlightIsDrawnYellow`, red before.  Reproduced on a copy of Adam's layout at the Large size: every switch and signal autonomy changed had a highlight armed and none showed yellow; with the fix they do. | fixed validated | MT-725 |
| 2026-10-11 | OB-333 | bug | The highlight is drawn again when a square changes twice inside it: the first highlight ended before the second's yellow goes on, where ending it afterwards put the plain picture back over the yellow (round 134, `d28e2de1`): `core.testLayoutTiles.testASecondHighlightIsDrawnYellow`, red before.  Reproduced on a copy of Adam's layout at the Large size: every switch and signal autonomy changed had a highlight armed and none showed yellow; with the fix they do. | fixed validated | MT-725 |
| 2026-10-11 | FR-121 | feature request | A point's locomotive list has two modes in the autonomy editor's `Excluded Locomotives` window: exclude the chosen locomotives, the default, or allow only them - every other one excluded, a locomotive added to the database later included; the menu says which (*Allowed Locomotives Only*).  The list is kept as chosen and read in its mode wherever an exclusion is asked (`Point.excludes`), so switching back keeps the choice; saved as `excludedLocsMode: whitelist` beside it (round 133, `f68aabbc`): `core.testLocomotiveExclusions` - `testAnAllowOnlyStationTakesOnlyTheLocomotivesItNames`, `testAnAllowOnlyListKeepsOutALocomotiveAddedLater`, `testAnAllowOnlyPointLetsOnlyItsLocomotivesPass`, `testAnAllowOnlyListSurvivesASave`; `core.testAutonomyDiagramSession.testAnAllowOnlyModeGoesIntoTheRailwayAndComesBack`; `regression.testHomeAssignmentRules.testAnAllowOnlyListThatLeavesOutTheHomeIsReported`. | - | MT-724 |
| 2026-10-11 | OB-332 | bug | A route button on a running line is track in the autonomy editor: not greyed, its arrows clickable, a click stepping its direction as on plain track - the button alone, as it is not part of a run; a button beside the line, with no track through it, is still greyed (round 132, `28e4e138`): `core.testAutonomyDiagramSession.testARouteButtonOnALineTakesADirectionClick`, red before (*"a route button on the line is greyed in the editor"*). | - | MT-723 |
| 2026-10-10 | FR-120 | feature request | A sensor with no name is asked for one as it becomes a station, from the editor's and the track diagram's station menu: Cancel or a blank name leaves it a sensor; a name another square has, in any case, is refused and asked for again with what was typed; a named square is not asked (round 126, `8389b266`): `core.testAutonomyDiagramSession.testAnUnnamedSensorIsNamedAsItBecomesAStation`. | - | MT-719 |
| 2026-10-10 | FR-119 | feature request | Show Trains in the autonomy editor, under Unmeasured Track: unticked, the editor draws no trains - icons or tails; the track diagram's are not its; remembered (round 126, `8389b266`): `ui.testDiagramLooksRight.testTheEditorsShowTrainsSwitchHidesItsTrains`, `core.testAutonomyDiagramSession.testTheEditorHasAShowTrainsSwitch`. | - | MT-718 |
| 2026-09-25 | OB-305 | bug | Every placement door saves what it wrote before it asks the tail question, unless an editor is open or on its way, so a crash while the question waits no longer loses the train's place (round 115, `3ce4be42`): `regression.testTheTailIsPickedOnTheDiagram.testEveryPlacementDoorSavesBeforeItAsks`, `testThePlacementIsOnDiskWhileTheQuestionWaits`.  **Claude, 2026-10-10:** closed on its claims, verified on my end, on Adam's *"fix all the outstanding bugs in this round"* (2026-10-09). | fixed validated | - |
| 2026-09-25 | OB-304 | bug | An old-file import names every skip - a home given twice, a placement or a home on a square already taken - and keeps a recorded facing only where trains may arrive, counted with the guesses (round 115, `3ce4be42`): `core.testAutonomyDiagramSession.testAnImportSaysWhatItLeftOnATakenSquare`, `testTheImportMessageNamesEverySkip`, `testAnImportKeepsARecordedFacingOnlyWhereTrainsMayArrive`.  **Claude, 2026-10-10:** closed on its claims, verified on my end, on Adam's *"fix all the outstanding bugs in this round"* (2026-10-09). | fixed validated | - |
| 2026-09-25 | OB-303 | bug | The Central Station sync remembers a multi-unit sweep a run held back and does it at the next sync with autonomy stopped, and a sweep that takes a train off redraws the diagram (round 115, `3ce4be42`): `core.testMockCentralStation.testASyncThatAddsAStandingMemberTakesItOffAndRedraws`, `testAMemberAddedDuringARunIsTakenOffByTheNextSync` (run in this checkout; the live layout preference is per folder).  **Claude, 2026-10-10:** closed on its claims, verified on my end, on Adam's *"fix all the outstanding bugs in this round"* (2026-10-09). | fixed validated | - |
| 2026-09-25 | OB-302 | bug | A failed old-file import keeps out the pages it said it left out: the exclusion now comes before the rollback snapshot (round 114, `f60a0e60`): `regression.testNoSetupEditDuringARun.testAPageTheImportLeftOutStaysOutWhenTheFileFails`.  **Claude, 2026-10-10:** closed on its claims, verified on my end, on Adam's *"fix all the outstanding bugs in this round"* (2026-10-09). | fixed validated | - |
| 2026-09-25 | OB-300 | bug | The own-tail rule judges a way round answered 0 throughout as measured track of no length, as every other length rule reads an answered 0 (round 116, `20c25047`): `core.testATrainDoesNotRunIntoItsOwnTail.testALoopAnsweredZeroThroughoutIsJudged`.  **Claude, 2026-10-10:** closed on its claim, on Adam's *"OB-300: own-tail on 0-length loops"* (2026-10-10, into 3.0.0).  The refusal for a 0-unit way round reads "0 units or shorter" - true, if odd. | fixed validated | - |
| 2026-09-22 | OB-268 | bug | The reversing-copy claim says it measures by the compass, and why (round 113, `dde09fb5`): `regression.testTheTurnRuleDoesNotChangeTheRealRailway.testAReversingCopyCanBeLeftByAnotherSide`.  **Claude, 2026-10-10:** closed on its claims, verified on my end, on Adam's *"fix all the outstanding bugs in this round"* (2026-10-09). | fixed validated | - |
| 2026-09-22 | OB-267 | bug | Both harness leaks closed (round 113, `dde09fb5`): the tab is put back in a `finally` (`regression.testTheKeyMapReachesTheWholeWindow`), and the visit history is restored exactly (`core.testAutoLayout`).  **Claude, 2026-10-10:** closed on its claims, verified on my end, on Adam's *"fix all the outstanding bugs in this round"* (2026-10-09). | fixed validated | - |
| 2026-09-22 | OB-264 | bug | All three gaps answered: the running half of section 6a's guard claimed (round 113, `dde09fb5`): `core.testHomeStaging.testTheGraphCannotBeEditedWhileARunIsGoing`; the marks surviving a highlight are `regression.testEditorSurfaceRules.testTheCoveredWashIsDrawnWhereItShouldBe`'s, restated for MT-309; and behaviour.md section 4 states the stale arrival side's limit.  **Claude, 2026-10-10:** closed on its claims, verified on my end, on Adam's *"fix all the outstanding bugs in this round"* (2026-10-09). | fixed validated | - |
| 2026-09-22 | OB-256 | bug | The caption migration captions only a station with none, as the import does - one station, one caption (round 114, `f60a0e60`): `core.testAutonomyDiagramSession.testALegacyLabelDoesNotCaptionAStationTwice`.  **Claude, 2026-10-10:** closed on its claims, verified on my end, on Adam's *"fix all the outstanding bugs in this round"* (2026-10-09). | fixed validated | - |
| 2026-09-22 | OB-252 | bug | The general sweep's findings and VD17's and VD18's leftovers, fixed in rounds 113 to 117 (`dde09fb5`, `8afd7d9d`, `f60a0e60`, `2b18974e`, `3ce4be42`, `20c25047`, `06b8b163`), each closed in the store with its claim: GSE-C4 and GSR-B4 on Adam's rulings of 2026-10-10, GS-B3 and GST-B1 on his *"into 3.0.0"*; GS-C1 and GST-C7 were answered already (round 16; the close-while-running ruling of 2026-09-29), GST-C6 and VD17-T9 needed nothing more, VD18-T5 went with VD18-C2.  The tangible ones became tests. |  | `MT-713`, `MT-714`, `MT-715` |
| 2026-09-21 | OB-249 | bug | The catalogue reads one disposition vocabulary on both paths, and a row of a two-column id-and-where table keeps its description unless the cell reads as a verdict (round 117, `06b8b163`) - checked over every review folder in history: no row changes but 23 descriptions in two deleted 2026-09-01 documents, none in disposition.  **Claude, 2026-10-10:** closed on that check, on Adam's *"fix all the outstanding bugs in this round"* (2026-10-09).  The `--add` prune beside it is unchanged: an unchanged folder keeps every status. | fixed validated | - |
| 2026-09-21 | OB-239 | bug | A train's orange follows the road it covers - each covered edge and its own places - where it re-derived a road from the squares at its ends (round 115, `3ce4be42`): `regression.testTheOrangeIsDrawnWhereTheTailStopsAtTheSwitch.testTheOrangeFollowsTheRoadTheTrainCovers`.  **Claude, 2026-10-10:** closed on its claims, verified on my end, on Adam's *"fix all the outstanding bugs in this round"* (2026-10-09). | fixed validated | - |
| 2026-10-09 | FR-118 | feature request | The station icons Adam chose: a rounded block for a station, the hexagon where trains may turn, two terminus shapes back to back where they must, the flat-ended terminus wherever a station's track runs out (whatever its turning setting), parking grey in its own shape, out of service the orange X over a grey block - each no taller than the sensor's contact, which it covers; captions stood on end back to full size (round 110, `67cf6045`): `core.testAutonomyDiagramMonitor`, `core.testAutonomyDiagramSession.testAStationWhoseTrackRunsOutIsATerminus`, `ui.testDiagramLooksRight.testAPillOnEndIsFullSize`. | - | MT-710 |
| 2026-10-09 | OB-329 | bug | The halo round a train's icon reaches at most a fifteenth of the icon: three pixels at the large size where it was four, two at the small as before (round 108, `5aa69c2f`): `core.testAutonomyDiagramMonitor.testTheHaloIsNarrowerAtTheLargeSize`. | - | MT-709 |
| 2026-10-09 | OB-328 | bug | A window remembered maximised - a page's own window, the editor - is shown at its remembered size and then maximised and brought forward, where it was maximised before it was shown and came up behind (round 108, `5aa69c2f`): `ui.testAMaximisedWindowComesForward`. | - | MT-709 |
| 2026-10-09 | FR-117 | feature request | A paused train stays paused: the configuration keeps which trains are paused, set from the Auto tab's pause button or the new **Is Active** tick on the track diagram's and the editor's right-click menus (at rest, where a train stands); autonomy and Return Home leave a paused train where it stands; its icon is grey; the log names the paused trains as a configuration loads (round 107, `ed0d310d`): `core.testAPausedTrainStaysPaused`, `ui.testTheActiveTickPausesATrain`. | - | MT-708 |
| 2026-10-09 | FR-115 | feature request | Place All at Their Homes, in Bulk Tools (the diagram's right-click menu under Autonomy Setup, and the editor's): every train with a home put on it facing the way it was homed, every other train taken off, nothing sent to the railway (round 106, `635b6ec8`): `core.testPlaceAllAtTheirHomes`. | - | MT-706 |
| 2026-10-09 | OB-327 | bug | A caption stood on end is narrower and centred between its own station's badge and the next one's: text 15% smaller on end, the pill as thick as the letters need, on the line between the squares (round 104, `a54ea8e4`): `ui.testDiagramLooksRight.testAPillOnEndClearsTheStationsBesideIt`, red before (*“starts at 21, on or against its own station’s badge, which reaches 22”*). | - | MT-707 |
| 2026-10-09 | OB-326 | bug | A side trains may not arrive by is one grey chevron at both sizes, its arms scaled with the square, where it was tan with strokes that came apart at 60 pixels (round 103, `c9b6c475`): `ui.testDiagramLooksRight.testABarredArrivalIsTheSameGreyChevronAtBothSizes`. | - | MT-707 |
| 2026-10-09 | FR-116 | feature request | An address is red letters on a white halo with nothing behind them - `AddressLabel`, drawn as letter shapes with a round-joined white stroke round them, in place of the translucent box (round 102, `6061f789`): `ui.testDiagramLooksRight.testAnAddressHasAHaloAndNoBox`, red before on the box (*“paints its corner (0,0) - a box behind the text”*).  Drawn over the locomotive standing on its square too, on his *“make sure they are rendered on top of the autonomy locomotive icons”* (round 105, `64dbe905`): `ui.testDiagramLooksRight.testAnAddressIsDrawnOverTheTrain`. | - | MT-707 |
| 2026-10-08 | OB-325 | bug | Return Home names a train that cannot leave where it stands, with the reason, instead of searching the whole fleet and answering that no plan was found (round 93, `66cffd63`): `core.testHomeStaging.testATrainThatCannotLeaveWhereItStandsIsNamed`, the guard `core.testHomeStaging.testATrainHeldInOnlyByAnotherIsPlannedNotNamed`, and on Adam's railway `core.testAMayTurnStationIsNotATerminus.testReturnHomeNamesATrainThatCannotLeaveTheTurningCopy`.  The setup check the entry also suggested was not built: since round 92 autonomy no longer turns a train there, and on his railway it would warn at BottomMainB.  **Claude, 2026-10-08:** closed on its claims, on Adam's *"Fix OB-325 and run a validator"*. | fixed validated | - |
| 2026-09-21 | OB-250 | bug | A route's "train X at sensor T" asks where the train is now - the last sensor it reached, or where it stands - and not which Point holds it, which for a running train could be one it had not reached (round 89, `129bebda`): `core.testRoutes.testARunningTrainIsAtTheSensorItLastReached`.  **Claude, 2026-10-08:** closed on its claim, on Adam's *"250- the first.  Fix b5 and 253"* (*"X is at T now"*). | fixed validated | - |
| 2026-09-22 | OB-253 | bug | A routes file naming one route twice, by id or by name, is refused whole before anything is deleted, and says why (round 89, `129bebda`): `core.testAnImportSaysItsRoutesAreOff.testAFileThatNamesARouteTwiceIsRefusedWhole`.  **Claude, 2026-10-08:** closed on its claim, on Adam's *"250- the first.  Fix b5 and 253"*. | fixed validated | - |
| 2026-09-22 | OB-261 | bug | **Claude, 2026-10-08:** closed on Adam's *"Close 261 and fix 266 to report all reason"*.  `Locomotive.waitForAccessoryState` is called by nothing in TrainControl - only from a program written against its Java API - and the accessory row it adds on a miss is harmless (an accessory is a counter). | declined | - |
| 2026-09-22 | OB-266 | bug | A layout refused more than once keeps every reason, first first, where it kept only the latest - the reason repeated in the log when a train is sent on the refused railway (`d0581d24`): `core.testInvalidInput.testEveryReasonASetupIsRefusedForIsKept`.  **Claude, 2026-10-08:** closed on its claim, on Adam's *"Close 261 and fix 266 to report all reason"*. | fixed validated | - |
| 2026-09-26 | OB-306 | bug | **Claude, 2026-10-07:** closed on Adam's agreement (*"Agree on OB-306"*).  The loss it describes needs a train standing on a station with no name of its own, and such a station is an ERROR the setup refuses to run with (`AutonomyChecks.checkNames`), so no train is ever there.  Its second part - two stations of one name on different pages trading their " (2)" when the page order changes - is not ruled out by that, but it needs the same one-event race, a page rename while it waits, and two stations sharing a name; none of the 33 on his railway do. | declined | - |
| 2026-09-22 | OB-258 | bug | The route editor's copy mark is drawn in outlines in any ink, as the marks beside it are (round 81, `8efa1078`): `regression.testEditorSurfaceRules.testTheCopyMarkIsOutlinesInAnyInk`.  Milder than filed: on a selected row it did not disappear, it showed as a solid white sheet among outlines.  **Claude, 2026-10-07:** closed on its claim, on Adam's *"Make a sweep on the 18 to see if any are likely for a user to see, and fix if cheap"*. | fixed validated | - |
| 2026-09-25 | OB-301 | bug | Change Name or Address asks the name before it changes anything, so a refused name leaves the address as it was, and no two standing trains are left on one decoder address (round 80, `7b6c026c`): `regression.testARouteDrivenLocomotiveIsNotEdited.testARefusedNameChangesNothing`.  **Claude, 2026-10-06:** closed on its claims, on Adam's *"fix the mentioned OB's that are valid"*. | fixed validated | - |
| 2026-09-22 | OB-263 | bug | A station's caption follows its rails whatever its sensor was reporting when the layout was saved (round 80, `e3ae5d1b`): `ui.testDiagramLooksRight.testASensorSavedOccupiedKnowsWhichWayItsRailsRun`.  **Claude, 2026-10-06:** closed on its claims, on Adam's *"Fix OB-263"*. | fixed validated | - |
| 2026-09-22 | OB-260 | bug | Deleting a timetable row removes the entry the operator was shown, under the railway's lock and off the event thread, or nothing (round 80, `e3ae5d1b`): `core.testLayoutTimetable.testADeletedRowIsTheOneShownOrNone`.  **Claude, 2026-10-06:** closed on its claims, on Adam's *"Fix OB-260"*. | fixed validated | - |
| 2026-09-22 | OB-259 | bug | A running train's square is no longer lifted in front of its labels, so the s88 address stays visible under a running train; the train is drawn above everything by the diagram's panel (OB-159) (round 80, `7b6c026c`). | - | `MT-701` |
| 2026-09-22 | OB-257 | bug | A remembered crop view crops the same part of the photograph in a dialog of another shape: the crop's width in the photograph is saved with it (round 80, `e3ae5d1b`): `ui.testLocIconCrop.testARememberedViewCropsTheSameRectangleInAnotherShape`.  The second half, the clamp's units, is not a defect: its margin is 24 screen pixels on purpose, so there is always something on screen to drag back.  **Claude, 2026-10-06:** closed on its claims, on Adam's *"Fix OB-257"*. | fixed validated | - |
| 2026-09-21 | OB-245 | bug | A locomotive's customized flag is kept as itself through a cancelled Copy Customizations and through the save (round 80, `e3ae5d1b`): `core.testACopiedCustomizationBringsTheIcons` - `testACancelledCopyLeavesTheFlagAsItWas`, `testTheSaveKeepsTheFlagNotTheIconsAnswer`.  **Claude, 2026-10-06:** closed on its claims, on Adam's *"Fix OB-245"*. | fixed validated | - |
| 2026-09-21 | OB-236 | bug | A cut asks for no autonomy session (round 80, `7b6c026c`): `regression.testEditorSurfaceRules.testACutAsksForNoSetupItWillNotTell`.  It never showed: opening the editor builds the session.  **Claude, 2026-10-06:** closed on its claims, on Adam's *"fix the mentioned OB's that are valid"*. | fixed validated | - |
| 2026-09-22 | OB-265 | bug | The dispatch choices are written in behaviour.md section 1a (`ab5dc191`).  **Claude, 2026-10-06:** closed - found fixed when the open bugs were checked before gold. | fixed validated | - |
| 2026-09-22 | OB-262 | bug | The setup's tidy report and the right-click menu's refusals belong to the main window (`693dd0a1`).  **Claude, 2026-10-06:** closed - found fixed when the open bugs were checked before gold. | fixed validated | - |
| 2026-09-22 | OB-255 | bug | **Claude, 2026-10-06:** closed on Adam's decision - going back to 2.7.4c after this version loses page names, and the Readme already says to keep a copy of the settings before going back to an older version. | declined | - |
| 2026-09-21 | OB-237 | bug | Repainting the Return Home button no longer creates an autonomy layout (`75827053`, RLV9-C2).  **Claude, 2026-10-06:** closed - found fixed when the open bugs were checked before gold. | fixed validated | - |
| 2026-09-21 | OB-234 | bug | The findings list no longer takes the editor's keyboard - fixed as OB-200 (`c1ff1d9e`).  **Claude, 2026-10-06:** closed - found fixed when the open bugs were checked before gold. | - | `MT-678` |
| 2026-09-14 | FR-085 | feature request | Ask for the farthest sensor a placed train's tail crossed, and keep the road it names: built in two phases - the road kept (WK7-B1, `63ac68f4`) and the question asked, from a list, in the menu or by clicking (`53a82f7c`). **Claude, 2026-10-04.**  It never had a receipt, so it sat in the Inbox as if never picked up.  Closed on Adam's word (*"Close the two"*): its tests ran MT-435, MT-438, then MT-515, which passed. | fixed validated | - |
| 2026-10-05 | FR-112 | feature request | A square a click turns shows a hand and the line *Click to change allowed directions*; about thirty tooltips on the autonomy right-click menus and the main window's menus shortened in every language, and a long one wrapped. | - | `MT-697` |
| 2026-10-05 | OB-323 | bug | Every file chooser opens at once: the Look In list is the shell's answer, asked once ahead of time rather than each time a chooser is made and at every change of folder. | - | `MT-690`, `MT-691`, `MT-692`, `MT-696` |
| 2026-10-05 | OB-322 | bug | A two-square crossing draws no arrows where its squares meet, on the track diagram and in the autonomy editor; a permanent Y on its own keeps its toe's arrow. | - | `MT-688`, `MT-689` |
| 2026-10-04 | FR-110 | feature request | Display Allowed Directions draws the green arrows on the track diagram too, with no wash. | - | `MT-675`, `MT-681`, `MT-683`, `MT-686`, `MT-687` |
| 2026-10-04 | OB-321 | bug | The track diagram draws a one-way run's arrow once, at its first square; a square that differs keeps its own. | - | `MT-674` |
| 2026-10-04 | OB-320 | bug | Two permanent Ys toe to toe are one crossing: corner to corner both ways, one direction per track, the click steps through both ways, the four one-way combinations and closed.  Two permanent lefts or rights toe to toe, legs facing, too. | - | `MT-676`, `MT-677`, `MT-682` |
| 2026-10-04 | FR-108 | feature request | Bulk Tools > Allow Every Path (n), with the clears' warning; a turnout with no address stays as its blades run it. | - | `MT-673` |
| 2026-10-04 | OB-319 | bug | Delete asks which configuration, and is offered while none is loaded. | - | `MT-672` |
| 2026-10-04 | OB-318 | bug | Swing's popup titles and buttons and the file chooser's words are put from TrainControl's messages. | - | `MT-671` |
| 2026-10-03 | OB-317 | bug | A train standing on the path it holds - at its start while its route is set - is drawn as its locomotive where it stands, not the dot. | - | `MT-663` |
| 2026-10-03 | FR-107 | feature request | Control+M for Select by Dragging a Box in the track diagram editor, named on its item. | - | `MT-661` |
| 2026-10-03 | OB-316 | bug | A train passing a station leaves its exit guard green; signal 108 was TopMainR2Inter's, passed on the way to TopMainR2. | - | `MT-660` |
| 2026-10-03 | OB-315 | bug | A train passing a station leaves its exit guard green - only where it starts or stops counts; the entry guard was already thrown only on arrival. | - | `MT-660` |
| 2026-10-03 | OB-314 | bug | On its way, a train's label faces the way it will stand, not the way of the turning copy its path ends on. | - | `MT-659` |
| 2026-10-03 | OB-313 | bug | The strip's buttons are as tall as the page's own, with their padding, at every display scaling. | - | `MT-658` |
| 2026-10-03 | OB-312 | bug | Parked Locs labels only stations a locomotive is parked at; the Visible Elements checkboxes evenly spaced. | - | `MT-657` |
| 2026-10-03 | OB-311 | bug | Show Files after a backup opens the backup folder itself. | - | `MT-656` |
| 2026-10-02 | OB-310 | bug | Changing what trains may do at a station leaves its label where it is, on end beside north-south track. | - | `MT-651` |
| 2026-10-02 | OB-309 | bug | A turning mark on Start Autonomous Operation whenever anything runs. | - | `MT-650` |
| 2026-10-02 | FR-106 | feature request | A running train's tail shows in orange over its route; the route ahead in the stations' blue, driven track in dark grey, which goes where non-atomic routes give the track back.  The colours are kept in one place, `DiagramColours`. | - | `MT-643`, `MT-644` |
| 2026-09-30 | FR-105 | feature request | A page left out of autonomy greys Auto and Manual with Test a Path and Why not Moving?. | - | `MT-634` |
| 2026-09-29 | OB-308 | bug | Layouts > Open Layout... opens beside your last layout folder with it selected, also after switching to the Central Station's layout. | - | `MT-589` |
| 2026-09-29 | OB-307 | bug | A train's orange line is drawn, and a square's autonomy menu opens, only with autonomy loaded; the diagram's own menu still opens. | - | `MT-590` |
| 2026-09-29 | FR-104 | feature request | A wrench in the picture's upper left chooses the icon; the plain pointer over the picture; a Central Station picture can be cropped; the placeholder a tenth lighter. | - | `MT-591` |
| 2026-09-26 | FR-103 | feature request | A station autonomy will not choose - parking, or switched off - is marked a medium dark grey, rgb(128,130,134), just darker than the grey station labels, instead of orange; a train's line keeps the orange. | - | `MT-587` |
| 2026-09-25 | OB-299 | bug | Why not Moving? says when the copy a train stands on reaches no station autonomy may choose - to turn it round where another copy of the square reaches one, and otherwise to drive it off by hand or let autonomy choose a station it can reach; eight languages: `core.testWhyStuck.testACopyThatReachesNoStationSaysSo`.  Nothing to run by hand: the frozen copy of your railway has no such copy (its two stranded copies are on sides trains may not arrive by, which already say why).  Mark it Works to close it. **Claude, 2026-10-04:** closed on its claims, green in the full battery of 0efa7777 (321 of 321) - your rule of 2026-09-30 that what you do not see is verified by claims and closed, and your note today that you take these as validated. | fixed validated | - |
| 2026-09-24 | OB-298 | bug | A rebuild builds its lists apart and hands them over whole, so a reader walking them finishes on the railway it began on: `core.testAutonomyDiagramReducer.testARebuildDoesNotChangeWhatAReaderIsWalking`; the setup hands a rebuilt railway out only once it is whole (ADA-C2).  Nothing to run by hand: mark it Works to close it. **Claude, 2026-10-04:** closed on its claims, green in the full battery of 0efa7777 (321 of 321) - your rule of 2026-09-30 that what you do not see is verified by claims and closed, and your note today that you take these as validated. | fixed validated | - |
| 2026-09-24 | OB-297 | bug | The own-tail refusal counts what Mass Assign Lengths would ask a length for on the way round - a piece, a page's switches, a page's crossings - and says so in its name: `core.testTheOwnTailArithmetic.testTheNoteCountsWhatMassAssignAsksFor`, `core.testMassAssignLengths.testTheSwitchesOfAPageAreOneThingToMeasure`. | - | `MT-571` |
| 2026-09-24 | OB-296 | bug | The right-click Place keeps the train's heading, on your answer. | - | `MT-581` |
| 2026-09-24 | OB-295 | bug | "Unavailable while occupied" shuts a square to routes through it while a train stands on the square it watches, as well as to arrivals - at runtime, in Why not Moving? and in Return Home's planner: `regression.testStationBlockedByAnotherPoint`, `core.testHomeStaging`. | - | `MT-584` |
| 2026-09-24 | OB-286 | bug | Highlight on Diagram lights a three-way once, as commanded, where a route commands one of its decoders and checks the other. | - | `MT-578` |
| 2026-09-24 | FR-098 | feature request | Customize Function Icons greys Apply while the function on show is as the locomotive holds it. | - | `MT-577` |
| 2026-09-24 | OB-287 | bug | The refusal to delete or rename a locomotive a running route drives has its test, at both doors and the name proposal: `regression.testARouteDrivenLocomotiveIsNotEdited`. **Claude, 2026-10-04:** closed on its claims, green in the full battery of 0efa7777 (321 of 321) - your rule of 2026-09-30 that what you do not see is verified by claims and closed, and your note today that you take these as validated. | fixed validated | - |
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
| 2026-09-21 | OB-247 | bug | Adam, 2026-09-24: *"look it up, though likely these are defunct review findings."*  They were not findings at all: AR-17 to AR-23 were ids given to items of his second hands-on round when the manual-test file was made (27261d16), and LR-1 to LR-6 the six of a 2026-08-26 review reported without a document.  Each now has a catalogue row naming the entry that is its only record, so every citation resolves; AR-19 left the dead-citation roll (44). **Claude, 2026-10-04:** closed on its claims, green in the full battery of 0efa7777 (321 of 321) - your rule of 2026-09-30 that what you do not see is verified by claims and closed, and your note today that you take these as validated. | fixed validated | - |
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
| 2026-09-22 | OB-271 | bug | Adam, from MT-446: *"make the test, capture, etc. buttons in the route editor non focusable."*  `setFocusable(false)` on Test, Highlight, the capture box and its target; Save left focusable, since Enter on it is how a route is saved. **Claude, 2026-10-04:** closed on its claims, green in the full battery of 0efa7777 (321 of 321) - your rule of 2026-09-30 that what you do not see is verified by claims and closed, and your note today that you take these as validated. | fixed validated | - |
| 2026-09-21 | OB-243 | bug | Adam, MT-438: *"coloring switch 99 and 100 is still an error regardless"* and *"it's almost as if you are shifting the location of the train"*.  A locked path reserves every Point on it, and the tail walk ran from each - one of four same-side roads picked by list order.  Fixed on his ruling, *"the tail is certain at departure and shouldn't change"*: one tail per train, a running one anchored at its head.  `core.testARunningTrainHasOneTail`. | - | `MT-438` |
| 2026-09-21 | OB-242 | bug | Adam, MT-438: *"it departed, so the tail would be gone as of the time of this screenshot."*  Not a stale mark: it was recomputed, correctly, from a reservation - the same cause as `OB-243` and fixed with it. | - | `MT-438` |
| 2026-09-21 | OB-241 | bug | Filed as "the tail walk uses the recorded road for the first hop only", and withdrawn the same day: the premise was wrong - `walkOneTail` has followed the road past a junction since MT-335, pinned by `core.testATailFollowsTheRouteItCameIn`, and a test written first passed before any change. | declined | - |
| 2026-09-21 | OB-240 | bug | Adam, MT-469: *"we can remove operators (like and) without deleting the conditions they are linked to ... Any linked entries should also be deleted."*  Worse than an orphan: a deleted OR silently read as AND.  Deleting a joining word now takes the term it joins, the mirror of deleting a condition. | - | `MT-471` |
| 2026-09-22 | OB-269 | bug | *"when running auto layout, more edges (tracks) may get locked than necessary ... significant bug."*  A lock reached TWO hops, because the write and the read each reach one: `setOccupied` marks every edge in each of a path's edges' `lockEdges`, and `isPathClear` read the flag off every edge in each CANDIDATE's `lockEdges` - so a candidate was refused when a THIRD edge shared rail with it and, separately, with the run, while the candidate and the run shared none.  `Edge.runOver` now counts only rail a path runs over and `isRunOver` asks it; `occupancy` and `isLockHeld` are untouched, so a held throat's accessories stay protected.  Two more rules were needed and each was found by a test rather than by reading: the same rail the OTHER WAY is refused on its own account, found by PLACE because the two directions live on different copies; and a train is never refused rail its OWN reservation holds, which is `behaviour.md` 5c and is what five trains with no way home turned out to be.  Held by `core.testALockReachesTheRailBeingRunOver` - four mechanism claims plus Adam's own list of eleven allowed concurrent journeys, which was red for six of them first and is what caught both extra rules.  Every mutation measured. **Claude, 2026-10-04:** closed on its claims, green in the full battery of 0efa7777 (321 of 321) - your rule of 2026-09-30 that what you do not see is verified by claims and closed, and your note today that you take these as validated. | fixed validated | - |
| 2026-09-21 | OB-248 | bug | Twenty-nine findings were open and named in no document a reader opens - only in this entry's own paragraph, which is the failure it was about.  Adam, 2026-09-22: *"no idea what 'the twenty-nine' are"*, and then *"re-file the others if they are found and still relevant"*.  All twenty-nine were found: the deleted reviews are recoverable from `77649f88^` and `4020a899^`, and each finding was read out of its own document and then checked against the code as it stands, by reading - nothing was run.  **Six were already closed** by the 2026-09-22 audit (four fixed with their commits, two not defects).  **Three are closed now**: `MON-C12` is the decomposition question `open-questions.md` already defers past 3.0.0, and `VD15-R8` and `VD15-R11` were lost with the message that reported them and name only their own absence.  **Twenty are still true and are now OB-253 to OB-268**, each with what was checked and when - `FP-B3` is the B, a route import that deletes every route and then drops one of two sharing an id.  No open finding is now absent from a live document, which is the claim `testTheRecordsCountTheStore` makes. **Claude, 2026-10-04:** closed on its claims, green in the full battery of 0efa7777 (321 of 321) - your rule of 2026-09-30 that what you do not see is verified by claims and closed, and your note today that you take these as validated. | fixed validated | - |
| 2026-09-21 | OB-246 | bug | The target column offered every locomotive in the database, including one whose name holds a COMMA - which `RouteCommand.isNameUsable` refuses, because a command line is comma-separated - so the operator picked it and learnt at Save that the only way out was to rename the locomotive.  Adam, 2026-09-22: *"yes"*.  Marked rather than hidden, because hiding it would refuse a legal selection with nothing shown: the row is drawn in the refusal ink with the gate's own reason on its tooltip, as it is typed.  A row nobody has filled in yet is not marked - a mark that is always there is a mark nobody reads.  Held by `testARowSaveWouldRefuseIsMarkedAsItIsTyped`, whose two mutations were each measured. **Claude, 2026-10-04:** closed on its claims, green in the full battery of 0efa7777 (321 of 321) - your rule of 2026-09-30 that what you do not see is verified by claims and closed, and your note today that you take these as validated. | fixed validated | - |
| 2026-09-21 | OB-238 | bug | The collision block that stops two trains standing on one square was keyed by the TILE, so a train on one arc of a double curve made the other arc read occupied and `isPathClear` refused track that is physically free.  Adam, 2026-09-22: *"it is two pieces of metal.  imagine two parallel tracks simple appearing on one tile for visual convenience.  two distinct, not connected paths."*  `AutonomyBuilder.blockFor` keys DOUBLE_CURVE, FEEDBACK_DOUBLE_CURVE and OVERPASS per ROAD - the grain the reduction has used since AUR-B1 - and every other split square still groups by the tile.  Held by `testEachArcOfADoubleCurveIsItsOwnPieceOfMetal`, seen failing first.  No hands-on test: it is what the build emits, and the claim reads it back out of the emitted configuration. **Claude, 2026-10-04:** closed on its claims, green in the full battery of 0efa7777 (321 of 321) - your rule of 2026-09-30 that what you do not see is verified by claims and closed, and your note today that you take these as validated. | fixed validated | - |
| 2026-09-21 | OB-244 | bug | A station's allowance - *"the station size is an allowance, not a length"* - was being spent at a running train's last MILESTONE, so a train whose milestone is a long block claimed a square behind it as well and `isPathClear` refused track no train is on.  Adam, 2026-09-22, asked where the maximum train length should be checked: *"it's the arrival station only"* - answer (a).  `walkStandingTrains` now passes `atRest`, false for exactly the trains part-way along a run, and both halves of the rule honour it; a train standing at the end of a road keeps the allowance unchanged.  Held by `testAMidRunMilestoneIsNotAnAllowance`, whose two mutations were each measured.  No hands-on test: it is a calculation. **Claude, 2026-10-04:** closed on its claims, green in the full battery of 0efa7777 (321 of 321) - your rule of 2026-09-30 that what you do not see is verified by claims and closed, and your note today that you take these as validated. | fixed validated | - |
| 2026-09-21 | OB-233 | bug | The berth-room walk counted straight across a permanently-set turnout, so a berth beyond one was measured from wherever the run began and a train too long for it was admitted, coming to rest fouling the merge.  Adam, 2026-09-22, having ruled the other way on 2026-09-15: *"(c) - treat them the same as regular switches for the purposes of the check"*.  Both walks ask `GraphReducer.boundsTheRoom` now; `isSwitch()` is untouched, and a crossing still does not stop the walk.  Held by `testTheRoomWalkStopsAtASwitchAndAPermanentTurnoutButNotACrossing`, seen failing first.  No hands-on test: it is a calculation, and the claim's mutation was measured. **Claude, 2026-10-04:** closed on its claims, green in the full battery of 0efa7777 (321 of 321) - your rule of 2026-09-30 that what you do not see is verified by claims and closed, and your note today that you take these as validated. | fixed validated | - |
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
| 2026-09-14 | FR-083 | feature request | Adam, on MT-412: *"we should make a FR to track fixing this behavior so that the pre-arrival arrow just matches."*  OB-218's accepted behaviour - the route reserved on the copy the path ends on, so the arrow and badge show the turned facing until arrival - asked to match before arrival too: reserve the copy the train will end on, from the answer at dispatch.  **Claude, 2026-09-14.** Picked up and not built. **Claude, 2026-10-04.**  Closed on Adam's word (*"Close the two"*): done by OB-314 - the copy a train will stand on is asked ahead of its arrival (`Layout.copyItWillStandOn`, `e6916672`), and the label and arrow read it (`AutonomySession.facingOnTheRailway`); MT-659 passed on 2026-10-03. | fixed validated | - |
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
| 2026-09-13 | FR-076 | feature request | easy tracking of station labels - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
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
| 2026-09-12 | FR-072 | feature request | easier autonomy triage - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-12 | FR-073 | feature request | filter autonomy issue list - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-12 | OB-200 | bug | hotkeys don't work in autonomy editor **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. **Claude, 2026-10-04.**  Built in this round, on Adam's word: *"fix OB-200 and OB-201 in this round too"*. | - | `MT-678` |
| 2026-09-12 | OB-201 | bug | incomplete error message in autonomy **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. **Claude, 2026-10-04.**  Built in this round, on Adam's word: *"fix OB-200 and OB-201 in this round too"*. | - | `MT-679` |
| 2026-09-11 | FR-070 | feature request | make it easier to enable/disable routes - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-11 | FR-071 | feature request | right clicking the edit button - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
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
| 2026-09-08 | FR-063 | feature request | local locomotive icons - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-08 | FR-064 | feature request | routes when power is off - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
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
| 2026-09-04 | FR-059 | feature request | add the paused autonomy locomotive indicator to right click menu on track diagram - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-03 | FR-058 | feature request | the diagram's right-click menu lists only what autonomy would choose; everything else valid is under **More Destinations**, uncapped | - | [MT-266](tests.md#mt-266) |
| 2026-09-03 | OB-171 | bug | the reversal-length notice fired on every square of the run in; one notice per square trains turn at now, saying how many squares still need a length **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-262` |
| 2026-09-02 | FR-057 | feature request | the diagram prints its column and row numbers, on by default. **Its toggle is gone as of OB-179** - the numbers follow the grid now, so they have no menu item and no preference of their own, and Control+K turns the grid instead **Claude, 2026-09-14.** Closed on Adam's word: *"FR-057 is in a weird state but I believe it all good to be closed."* The weird state was its Became - MT-274 superseded, MT-277 and MT-284 validated, MT-291 still waiting - so the row showed MT-291's "needs test". MT-291 (the Grid tooltip in a language you read) stays open in the Tests tab on its own. | fixed validated | - |
| 2026-09-02 | OB-170 | bug | the window still came up without the keyboard, and the request was never made again | - | [MT-259](tests.md#mt-259) |
| 2026-09-02 | FR-056 | feature request | right-clicking a tunnel flashes the square it is joined to, when that square is on this page | - | `MT-391` |
| 2026-09-02 | FR-079 | feature request | **Formerly listed as R28-C1**, the review finding it came from (R28 round, C1), which is not a feature request id - Adam, 2026-09-14: *"I see a featre request R28-C1, which is not a valid feature request ID."* The finding keeps its id in the review catalogue and in the code that cites it. "Clear All Home Locomotives" restored to the autonomy editor, with a confirmation | - | [MT-254](tests.md#mt-254) |
| 2026-09-02 | OB-167 | bug | station no + must reverse + disabled gets the same icon as a terminus **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-417 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-417` |
| 2026-09-02 | OB-169 | bug | clicking a palette tile then an occupied square no longer placed it | - | [MT-252](tests.md#mt-252) |
| 2026-09-02 | OB-168 | bug | the window came up without the keyboard, so the locomotive letters did nothing **Claude, 2026-09-14.** Its test MT-251 was superseded by MT-259, which is where it is tracked now. | - | `MT-259` |
| 2026-09-02 | FR-055 | feature request | search function for points in autonomy editor - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-09-01 | FR-054 | feature request | a drawn placeholder locomotive for one with no picture | - | `MT-390` |
| 2026-08-31 | OB-166 | bug | a hand dispatch swept every protecting signal, so an empty platform was commanded green **Claude, 2026-09-14.** MT-246 was split into MT-303 and MT-304, one outcome each. | - | `MT-303, MT-304` |
| 2026-08-31 | OB-165 | bug | Return Home stayed dark after a train left its claimed home | - | [MT-165](tests.md#mt-165) |
| 2026-08-31 | OB-164 | bug | the diagram right-click menu offers no destinations in non-atomic mode - closed as a known limitation | declined | - |
| 2026-08-31 | FR-048 | feature request | easier locomotive editing - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-08-31 | FR-049 | feature request | locomotive import and export - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found.  FR-114 (2026-10-08) was closed as its duplicate: the import reads a file in the format Locomotives > Export to CSV writes - Name, ButtonMappings, DecoderType, Address, PreferredSpeed, TotalRuntime, Start Year, End Year, RailwayName, Notes. | pending | - |
| 2026-08-31 | FR-050 | feature request | modernize keyboard tab - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-08-31 | FR-051 | feature request | improved log - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-08-31 | FR-052 | feature request | autonomy editor bulk mark stations - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
| 2026-08-31 | FR-053 | feature request | calculate signals to set to red - **for v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*). **Claude, 2026-09-14.** Picked up 2026-09-14 and not built - no code change found. | pending | - |
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
| 2026-08-22 | FR-002 | feature request | Appearance of stations and incoming arrows - circles, squares and diamonds are not semantic, and the arrows are messy.  **Not done** (Adam, 2026-09-13): `MT-094`, its only test, was superseded, so nothing current implements it and it is tracked here again rather than through that test **Claude, 2026-09-14.** Not built. Adam: *"FR-002 should show as pending"* - State set to **pending**, the word added that day for a request picked up and not yet built; "needs test" said there was something to test.  **For v3.1.0**, on Adam's word of 2026-10-10 (*"Yes, mark them for 3.1.0"*) - to be read first against FR-118's station icons and the arrows' later rounds (OB-326, MT-710's arrows over the icons), which may have answered it. | pending | - |
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
