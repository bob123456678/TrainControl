# Issues

Bugs and feature requests, in one inbox. Adam writes here - by hand, or through
[triage.py](triage.py)'s **New issue** button. Claude reads here, turns each item into a finding in
`docs/reviews/` (for a bug, under that round's prefix) or works it directly (for a feature request),
opens an `MT-###` entry in [tests.md](tests.md) to cover a bug fix (a feature request only if the work
turns out to need one), and clears the item out of the Inbox.

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

### OB-155 - 2026-08-30 - synchronizing with cs2 after deleting routes

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-30 12:38  
**Build:** commit c386be96, build\classes, compiled 30 Aug 12:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

the route page should not have to sync with the cs2 after edits/deletions for routes >= ID 1000

### OB-156 - 2026-08-30 - missing autonomy routing logic

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-30 13:16  
**Build:** commit c386be96, build\classes, compiled 30 Aug 12:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

there should also be a "by station priority" option that simply uses the station priority and randomly choose from the highest available.

### OB-157 - 2026-08-30 - selection drag repaints every tile on the diagram

**Kind:** bug  
**Raised from:** MT-228  
**Filed:** 2026-08-30  
**Build:** commit 72234e18 plus the release-candidate round

Adam, testing MT-228: "I'd prefer less flickering when making the selection."

**Cause found, fix not attempted.** Dragging a selection box calls `refreshSelectionBorders` on every
mouse-motion event, and that begins with `clearBordersFromChildren`, which calls `setBorder` on EVERY
tile label in the grid before re-applying the outline to the picked ones. On a diagram of a few hundred
squares that is a few hundred repaints per mouse move, which is what the flickering is.

**Why it was not fixed in the same round.** The fix is to remember which border each tile currently
carries and touch only the difference - but four separate places set a tile's border: the hover reset
(`LayoutEditor:3921`), the highlight (`:3947`), the clear (`:3976`) and the drag grip (`:2508`). All
four have to maintain that record or a tile is left carrying a stale outline, which is a worse failure
than the flicker. It is also the same resting-border logic RC-C10 was raised about, where the previous
mistake was a comment describing a guard that had been removed.

Worth doing, worth doing carefully, and not worth doing at the end of a long round.

### OB-158 - 2026-08-30 - ... on traversing trains in station labels

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-30 14:07  
**Build:** commit c386be96, build\classes, compiled 30 Aug 14:05 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

... on traversing trains in station labels make the ... not be bold, and change it to one arrow that correctly shows the direction of travel.

### OB-159 - 2026-08-30 - locomotive icon over stations while running

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-30 14:59  
**Build:** commit c386be96, build\classes, compiled 30 Aug 14:57 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

the locomotive icon sometimes appears BELOW stations while running, as rendered in the track diagram viewer under autonomy.

### OB-160 - 2026-08-30 - route buttons that conduct track they were not drawn to conduct

**Kind:** bug
**Raised from:** asked for directly - Adam, after OB-158
**Filed:** 2026-08-30

Adam: "make it be an error if two route tiles are next to each other (only if they are connected to
something else on the graph).  let me know if any other placement of surrounding tiles against a route
should emit an error.  I am inclined to treat it as a static crossing under the hood."

A route button carries no track of its own - what it conducts is decided by what is beside it.  Two
errors: a run of buttons that reaches real track at BOTH ends, which conducts a route across diagram
with no rails on it; and track running into a button from three sides, where the through-pair wins and
the third arm is dropped in silence.  Four sides is a fixed crossing and is left alone, which is his
"static crossing under the hood".

### OB-161 - 2026-08-30 - a phantom row stays highlighted below the diagram

**Kind:** bug
**Raised from:** MT-228
**Filed:** 2026-08-30

Adam: "the flicker is gone, but when dragging the selected tiles to the bottom of the diagram, a
phantom row gets permanently highlighted in blue.  the rest seems to work OK."

The grid is built one row taller and one column wider than the diagram, and that extra row and column
are blank labels holding the GridBagLayout together (OB-055).  Every outline goes on through one
method, which was happy to put one on them - and the routine that takes outlines off deliberately
leaves them alone, being the grid's own furniture rather than squares.  So a group dragged onto the
last row painted its landing outline onto the padding underneath, and nothing ever removed it.

Reachable in red as well, by releasing a selection box on that row.  Refused now at the one door, asked
of what the label IS, which is how the clearing side already asks it.

### OB-162 - 2026-08-30 - the timetable shows the form designer's headings when it is empty

**Kind:** bug
**Raised from:** asked for directly - Adam, 2026-08-30
**Filed:** 2026-08-30

Adam: "also, the timetable entry has default table heading when blank.  make sure these are always
set."

The form designer starts every table off with four columns called "Title 1" through "Title 4" and four
blank rows.  The real headings were installed only when the timetable was redrawn, and that cannot
happen before there is an autonomy configuration to redraw it from - so on a fresh installation the
Timetable tab showed the placeholder for exactly as long as it took to set autonomy up.

They are set when the window is built now.  Separately, because redrawing the timetable begins by
asking the running graph for a snapshot and at that point there is no graph to ask.

### OB-163 - 2026-08-30 - the routing rules explain themselves to nobody

**Kind:** bug
**Raised from:** found while writing MT-240 for OB-156
**Filed:** 2026-08-30

Every routing rule has a written explanation - `autolayout.ui.tooltip.pathPreferenceFEWEST_STATIONS`
and its eight siblings, translated into all eight languages.  Nothing reads them.  The dropdown is
built from the NAMES only, and its tooltip is the general one about what the control does; the
per-rule text has no caller anywhere in `src/`.

That is seventy-two written and translated sentences the operator cannot see, on the one control where
they are choosing between ten similarly-worded options.  It also hid the fact that "Completely at
Random", added yesterday for OB-156, is the only rule with no explanation written at all - a gap that
would have been obvious the moment the text was on screen.

The tooltip for "At Random, Respecting Priority" is stale as well: it says "whatever free route is
found first", which was true when the rule was called "At Random" and is only half the story now that
its name promises priority.

### OB-164 - 2026-08-31 - the diagram right-click menu offers no destinations in non-atomic mode

**Kind:** bug
**Raised from:** MT-087
**Filed:** 2026-08-31
**Build:** commit c386be96, build\classes, compiled 31 Aug 00:07 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

Adam, closing MT-087: "Works, with notes.  Works from the autonomy commands panel, but right-clicking
on the track diagram does not show available options in non-atomic mode."

**Not the same question on the two surfaces.**  `AutoLocomotiveStatus.findPaths` gates on
`layout.isAutoRunning()`, which is about the whole layout; `LayoutRightclickAutonomyMenu` gates on
`getActiveLocomotives().containsKey(locomotive)`, which is about that one train.  Both then call
`getPossiblePaths(loc, true)` and the same distinct-destinations filter.

Neither `getPossiblePaths` nor `isPathClear` branches on `atomicRoutes` anywhere, so the difference has
to be in state a non-atomic run leaves behind rather than in the question.  Three readings did not
survive: a stale `activeLocomotives` entry (the removal at completion is unconditional), a point
cleared behind the train (the last edge's endpoints are deliberately left alone), and the menu picking
the wrong copy of a split square (`getAutonomyPointForTile` prefers the occupied copy).

**Closed as a known limitation, on Adam's ruling, 2026-08-31:** "The user can rely on full autonomy or
the panels to send trains more clearly."

Both of those surfaces work in non-atomic mode - the commands panel offers the destinations and full
autonomy chooses them - so what is lost is one of three ways to reach the same thing, on the surface
where it is least clear anyway.  Weighed against changing what the diagram menu asks, which is a gate
on a per-locomotive question that is right for the case it was written for, that is not a trade worth
making without a reproduction, and the reproduction is the expensive part.

Recorded rather than dropped: the next person to read `LayoutRightclickAutonomyMenu` and notice the two
surfaces disagree should find this rather than re-open it.

### OB-165 - 2026-08-31 - Return Home stays dark after a train is driven off its claimed home

**Kind:** bug
**Raised from:** MT-165
**Filed:** 2026-08-31
**Build:** commit 302d7a11, build\classes, compiled 31 Aug 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

Adam, closing MT-165: "This part works, but what doesn't work is the snapshotting - if I
semi-autonomously move a train away from the station where I opened traincontrol, the return home
button doesn't light up."

**Upstream of what MT-165 tests.**  That entry is about the planner staging a blocker out of the way,
and he says it works.  This is about the home CLAIM: `Layout.claimHome` gives a hand-placed locomotive
a positional home where it is put, so opening TrainControl with a train at a station should make that
station its home - and driving it away should light the button, because it then has somewhere to go
and is not there.

It stays dark, so `triageReturnToHome()` is still answering ALREADY_HOME or NO_HOMES after the move.
Two shapes fit and they need telling apart: the claim never happened, or the claim FOLLOWED the train.
The second would be the more interesting - a positional home that moves with the locomotive is a home
that can never be left.

Nothing is changed until that is reproduced.

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

### OB-166 - 2026-08-31 - signal changes unnecessarily

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-31 18:36  
**Build:** commit 302d7a11, build\classes, compiled 31 Aug 18:33 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when sending a train from bottominnerotherside to bottominner, signal 64 goes from red to green unnecessarily.

### FR-053 - 2026-08-31 - calculate signals to set to red

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-31 23:16  
**Build:** commit 302d7a11, build\classes, compiled 31 Aug 23:15 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

instead of specifying signals that guard a station, calculate them based on what gets locked and what is ahead of the active path.  feature for 3.1.0.

### OB-167 - 2026-08-31 - station no + must reverse + disabled gets same icon as terminus

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-31 23:20  
**Build:** commit 302d7a11, build\classes, compiled 31 Aug 23:15 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

station no + must reverse + disabled gets same large square icon as inactive terminus.  if nothing can pass, the icon should be a small x.

### FR-054 - 2026-08-31 - placeholder locomotive icon

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-08-31 23:25  
**Build:** commit 302d7a11, build\classes, compiled 31 Aug 23:15 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

for locomotives without an icon, add a placeholder rather than nothing.  the placeholder should be light gray and a simple electric locomotive consisting of: a main rectangle, trapezoid that 1/3 it's height, 2 small closed pantographs, and 4 1/5 height circular wheels in sets of two on each side, spaced evenly apart.

### OB-168 - 2026-09-02 - window not focused when UI starts up

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-02 01:17  
**Build:** commit 409d4ce8

ensure the UI is focused once the window is rendered so that keystrokes are registered on the main traincontrol window (locomotive letters, etc.).  this feels like a regression.

### OB-169 - 2026-09-02 - layout editor regression

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-02 01:19  
**Build:** commit 409d4ce8

clicking a tile in "new components" followed by a square on the diagram no longer places that tile.  it should place it and stay in place mode until escape is pressed or another action taken.

### FR-055 - 2026-09-02 - search function for points in autonomy editor

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-02 08:12  
**Build:** commit 409d4ce8, build\classes, compiled 02 Sep 08:07 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

search a point name, the right page is opened and the tile highlighted.

when other page points are clicked from warnings, also highlight it after the editor window switch

### OB-171 - 2026-09-03 - excessive warnings

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-03 02:21  
**Build:** commit 409d4ce8, build\classes, compiled 03 Sep 01:53 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

warnings like    Trains turn round at 19,14 and its track has no length recorded, so nothing can tell whether a long train would stand across the switch behind it.  Set the track length. fire on many tiles along a line.  Dedupe them, one per segment between a switch and a station.

### FR-058 - 2026-09-03 - autonomy path options in the rick click track diagram menu

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-03 19:53  
**Build:** commit 409d4ce8, build\classes, compiled 03 Sep 19:47 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

in the right click menu on the track diagrams, show only active stations that can be chosen in full autonomy.  add a menu called More Destinations and in there, list the points that cannot be chosen in full autonomy but are still valid.  the current setup lists both in one flat list, which truncates active stations, which I don't like

### OB-172 - 2026-09-04 - axis labels when switching between track diagram editor and autonomy editor

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-04 03:02  
**Build:** commit 409d4ce8, build\classes, compiled 04 Sep 02:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

some axis labels vanish when switching between track diagram editor and autonomy editor - 1 and 3 in my case.  they reappear if the grid setting is cycled.  fix this, and then tie the appearance of the numbers to the enablement of the grid, so we only see the axis labels if the grid is also on.

### FR-060 - 2026-09-04 - back a train into a parking track, and a parking designation to say where

**Kind:** feature request  
**Raised from:** noticed while ruling on MT-250  
**Filed:** 2026-09-04

Adam: *"if a backwards route is possible to a parking track, we can allow that and just reverse the
train.  we would want a new 'parking' designation on stations."*

**What this is really about.** Today a berth a train has to back into is expressed as a TERMINUS, and
a terminus means one thing to the graph - a square you may arrive at and not drive through. Whether a
train may back into one has been argued three times this fortnight from that single flag: `280ff08b`
took the rule out of `isPathClear`, `mustBackIn` kept it in staging, and on 2026-09-04 that came out
too. Each time the question was really *"is this a place where reversing is normal?"* and the only
thing available to answer it was *"is this a dead end?"*.

A parking designation separates them. A parking track is somewhere a train is meant to sit, where
backing in is ordinary rather than exceptional; a terminus is a fact about the track. With both, the
planner can say "a backwards route to a parking track is fine" without that also licensing every dead
end on the railway.

**What it would need, as far as I can see it now.**

- The designation itself, alongside `terminus` and `reversing` - and the three have to be made to say
  something different from each other, or this is a fourth spelling of the same idea. `setAutoDestination`
  already clears two older spellings for exactly that reason.
- Pathfinding that will build a backwards approach to such a square, and a plan that reverses the
  train on arrival rather than refusing.
- Somewhere on the diagram to set it, and something drawn so it can be told from a terminus.
- **And a decision about the twenty squares that already carry `autoDestination: false`** on his own
  railway, most of which are parking in everything but name. Whether they become parking tracks
  automatically or one at a time is the part that decides how much work this is for him.

**Worth saying before it is built:** this is the "third kind of station" his own 2026-09-01 ruling said
we would need if non-reversible trains could not back into termini - *"Otherwise we'd need a third kind
of station."* He chose not to need one then. This is that idea arriving on its own terms, wanted for
what it enables rather than to avoid something.

**Claude, 2026-09-13: left out of the FR migration, and this is why.**

Adam: *"migrate FR tests into MT's as I have no other way of triaging them."*  Every feature request
that has been BUILT now has an MT.  This one has none because nothing has been built - the section
above is a design proposal, and it ends in a list of what it would need.

The part that decides how big it is, and the part only he can answer:

> **The twenty squares on his railway already carrying `autoDestination: false` are parking in
> everything but name.  Do they become parking tracks automatically when the designation arrives, or
> one at a time by hand?**

Automatically is one migration, and the risk that a square meant as manual-only quietly gains a new
behaviour.  By hand is twenty right-clicks and nothing surprising.

**Adam, 2026-09-13 - and the answer is neither of those.**

> *"For now, we consider anything with autodestination=false and only one way in/out as a parking
> square.  We can revisit dedicated marking if this doesn't work out with clean logic, or if it gets
> too confusing to the user to manage."*

So there is no fourth flag, nothing to migrate and nothing to mark: a parking berth is derived from two
facts the setup already holds.  Neither is enough alone - `autoDestination` off is also true of a
platform he dispatches by hand, and one way in and out is also true of a headshunt - and together they
are what the designation was wanted for.

Measured on his railway the day of the ruling: of the 20 stations carrying `autoDestination: false`,
**12** are berths by this test.  The other eight - BottomMainPost, RampDown, TunnelLongPark,
ParkingTrack11, TunnelRightPark, LowerParkingOuter, TunnelCenterPark, TunnelLeftPark - have track
running through or past them and stay what they are: squares he keeps for himself.

**What it does NOT change**, deliberately, and recorded so a later reader does not "tidy" it:

- **Not the berth rule's gate.**  *"Parking berths cant block any other edges"* (2026-09-12) applies
  wherever autonomy will not choose the square, which is wider than this on purpose - the square he
  measured that ruling against is TunnelLongPark, which has three ways in.  Narrowing the guard to the
  twelve would drop the case it was written for.
- **Not `isParking`/`isAutoDestination` in the setup.**  Those are the switch he sets, and the derived
  answer reads them rather than replacing them.

`Layout.isParkingSquare` is the one definition; `core.testWhatCountsAsAParkingSquare` pins both halves
and the fact that the berth rule is not gated on it; `docs/reference/behaviour.md` section 4 says it in
prose.  The rest of FR-060 - a backwards approach the planner will build, and something drawn on the
diagram - is not built, and the backing-in half of it has been moot since 2026-09-04, when the rule
that refused such an arrival came out on his ruling that *"Return home is manual"*.

### OB-173 - 2026-09-04 - autonomy editor does not appear on top of the main window

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-04 03:04  
**Build:** commit 409d4ce8, build\classes, compiled 04 Sep 02:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when clicking edit, the autonomy editor appears below other windows, so I might not even notice it's there.  it should appear above the main TC window.  this works correctly when the track diagram editor is what opens when click on the edit button, but not when the autonomy editor is what opens.  check how it works for the track diagram editor and ensure consistency.  we managed this carefully in 2.7.x

### FR-059 - 2026-09-04 - add the paused autonomy locomotive indicator to right click menu on track diagram

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-04 03:08  
**Build:** commit 409d4ce8, build\classes, compiled 04 Sep 02:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

add the paused autonomy locomotive indicator (and the ability to toggle whether it's paused) to the right-click menu on the track diagram. maintain parity with the autonomous locomotive commands panel. likely for 3.1.0

### OB-174 - 2026-09-04 - checkbox in autonomy editor

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-04 19:27  
**Build:** commit 409d4ce8, build\classes, compiled 04 Sep 19:13 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

If checked, "Show parked trains" should auto check "text labels" if "text labels" is unchecked.

### OB-175 - 2026-09-04 - curved sensor tiles with incoming arrows

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-04 20:12  
**Build:** commit 409d4ce8, build\classes, compiled 04 Sep 19:13 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

curved sensor tiles going from n to e have an incoming from e arrow that overlaps with the track.  move the arrow to the lower-right corner of the tile, instead of the upper-right.

### OB-176 - 2026-09-05 - popup textbox not selected

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-05 23:42  
**Build:** commit 409d4ce8, build\classes, compiled 05 Sep 23:25 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when setting track length in the autonomy editor, the text box should be selected when the popup opens

### OB-177 - 2026-09-05 - locomotive is facing error

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-05 23:44  
**Build:** commit 409d4ce8, build\classes, compiled 05 Sep 23:25 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

the "<locomotive> is facing" menu doesn't always correctly reflect the facing of the train there

### OB-178 - 2026-09-06 - can't re-save manual route

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-06 00:10  
**Build:** commit 409d4ce8, build\classes, compiled 05 Sep 23:25 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

if a route's auto-fire checkbox is unchecked, and the s88 field is blank, the save will still fail asking the user to input an integer.  just treat this as 0

### FR-061 - 2026-09-07 - Text Labels as a dropdown.

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-07 19:19  
**Build:** commit 409d4ce8, build\classes, compiled 07 Sep 19:13 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

OB-174 is currently only partially fixed.  fully address it by adding a "Text Labels" label and dropdown right above Track Directions, with the following options: Station Names, Parked Locomotives, Home Locomotives, and None.  Station Names should be default, with the setting remembered between open.

### OB-179 - 2026-09-07 - show coordinates clutter

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-07 20:33  
**Build:** commit 409d4ce8, build\classes, compiled 07 Sep 20:29 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

hide the "show coordinates" right click menu option in the autonomy and track editors, make "coordinates on" be default, as this is tied to "show grid" option and will be toggled on and off together with the grid.

### OB-180 - 2026-09-07 - shaded length block tiles not reset on train move

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-07 23:45  
**Build:** commit 409d4ce8, build\classes, compiled 07 Sep 23:42 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when a train is manually moved to a new station in the track digram viewer using control+X and V, its former shaded icons are not reset.  example: en 57-203 from bottommaina back to tunnelleftpark

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

### OB-183 - 2026-09-08 - changing home inconsistency

**Kind:** bug  
**Raised from:** MT-300 (Return Home reaches a split platform from either direction)  
**Filed:** 2026-09-08 01:04  
**Build:** commit 409d4ce8, build\classes, compiled 08 Sep 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

Changing a home can teleport a current locomotive's location on the digram/graph.

### OB-184 - 2026-09-08 - home planner bug

**Kind:** bug  
**Raised from:** MT-300 (Return Home reaches a split platform from either direction)  
**Filed:** 2026-09-08 01:04  
**Build:** commit 409d4ce8, build\classes, compiled 08 Sep 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

The home planner does not consider blocks due to the length of a train, i.e. a train that blocks edges behind it.

### OB-185 - 2026-09-08 - every click is a flicker

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-08 01:05  
**Build:** commit 409d4ce8, build\classes, compiled 08 Sep 00:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

changing any pathing arrow in the track autonomy editor makes the whole screen flicker.  warning updates can be deferred a few seconds later if needed.

### FR-062 - 2026-09-08 - download the CS3 data files too when the user confirms a Central Station download

**Kind:** feature request  
**Raised from:** the triage API  
**Filed:** 2026-09-08  

Raised by Adam in MT-170 on 2026-08-24, in the note attached to a Works verdict, and never filed on its own: *"if the user confirms the CS download, we should also download CS3 data files if using a CS3."* MT-170 tested backing up a layout that lives on the Central Station and passed; this is the follow-up it raised, filed so MT-170 can be closed without losing it.

### OB-187 - 2026-09-08 - the menu options ungrey at different times when connecting finishes

**Kind:** bug  
**Raised from:** the triage API  
**Filed:** 2026-09-08  

Raised by Adam in MT-264 on 2026-09-06, in the note attached to a Works verdict, and never filed on its own: *"Looks good, but when the loading finishes, the menu options ungrey at different times."* MT-264 tested the window while it is connecting and passed; this is the follow-up it raised, filed so MT-264 can be closed without losing it.

### OB-188 - 2026-09-08 - use current vs use active buttons

**Kind:** bug  
**Raised from:** MT-334 (Changing a pathing arrow no longer flickers the diagram)  
**Filed:** 2026-09-08 09:17  
**Build:** commit 22f3d302, build\classes, compiled 08 Sep 08:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

add some spacing between the two buttons, as they currently touch (in the set home locomotive popup)

### OB-189 - 2026-09-08 - trains don't reverse.

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-08 09:26  
**Build:** commit 22f3d302, build\classes, compiled 08 Sep 08:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

I send EN57-203 from BottomMainA to BottomMainPost.  I say to No to keep current direction, but it does not reverse on arrival.

### FR-063 - 2026-09-08 - local locomotive icons

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-08 09:40  
**Build:** commit 22f3d302, build\classes, compiled 08 Sep 08:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

right clicking a local locomotive icon should provide a clear option to clear it and change it, rather than opening an editor without context.  create a dropdown for this in 3.1.0 when the icon is clicked.

### OB-190 - 2026-09-08 - OB-189 follow-up: confirm the diagram shows the new facing after a hand-driven reversal

**Kind:** bug  
**Raised from:** the triage API  
**Filed:** 2026-09-08  

The command half of OB-189 was measured firing on every answered journey - a probe in Layout.executePathInternal logged the arrival turn each time, and answering yes versus no produces different final directions. What was missing is the GRAPH half of Adam's instruction of 2026-09-08: **"if yes, emit reversal command and update on the graph."**

A destination reversal is recorded by toggling the locomotive into Layout.reversedOnArrival, and the window writes it to the setup in reconcileFacingWhenIdle - reachable only from a diagram refresh. Autonomy refreshes constantly so its turns land within a tick. Nothing refreshed when a HAND-DRIVEN journey ended, so the facing sat pending and the diagram went on showing the train pointing the way it set off, while the railway had already obeyed the command.

Both manual doors now refresh when the journey returns. This needs confirming on the real railway: send a train to a may-reverse destination, answer No, and check that the arrow on the diagram turns as well as the locomotive.

ALSO STILL OPEN, and Adam's call rather than a defect: a journey that passes a reversing square gets a compulsory turn there, which is invisible to the operator and flips the train once before the answered turn at the destination flips it again. Measured 2026-09-08: keep-direction ends backward, reverse ends forward. So the answer is honoured but reads as doing nothing when the net is nil. testAReversalCommandIsEmitted guards that the two answers differ.

### FR-064 - 2026-09-08 - routes when power is off

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-08 10:19  
**Build:** commit 22f3d302, build\classes, compiled 08 Sep 08:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when the power is off, replace the route play icon with a wrench icon to edit it.  restore play icon and run behavior on click when the power comes on.

### FR-065 - 2026-09-08 - escape closes autonomy/track editor

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-08 10:22  
**Build:** commit 22f3d302, build\classes, compiled 08 Sep 08:56 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

escape closes autonomy/track editor - same as closing via button, with warning shown as needed

### OB-191 - 2026-09-08 - why not moving is blank

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-08 18:22  
**Build:** commit 22f3d302, build\classes, compiled 08 Sep 18:14 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when i click on DRG 06 001, "why not moving" in the autonomy editor correctly paints the paths, but it does not show the list of reasons in the top banner- the banner expands, but I see no text.

### OB-192 - 2026-09-08 - critical: UI freeze in autonomy

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-08 22:05  
**Build:** commit 0016fc18

starting autonomous operation from the current track state, via the netbeans compiled jar, makes the UI unresponsive.  Trains still run, but nothing is repainted, and controls are stuck.

### OB-193 - 2026-09-09 - TopMainR2 shows two labels

**Kind:** bug  
**Raised from:** MT-293  
**Filed:** 2026-09-09  

Adam, on MT-293 (2026-09-08), in the same breath as passing it:

> *"This works. At the time of testing, TopMainR2 still shows two labels, but this is still pending
> being worked."*

MT-293 is **Control+L moves the caption dropdown with it** (from RGD-C3) and passed on both of its
runs. This is a separate observation made during them, and it appears nowhere in issues.md.

**CHECK IT AGAINST THE LABEL WORK OF 2026-09-08 BEFORE FIXING IT.** He said himself it was "pending
being worked", and the caption and station-label handling moved several times that day
(`updateStationLabels`, the `StationCaption` pill work, `getLayoutStations`). It may already be gone.
If it is, close this with the commit that did it rather than leaving it open; if it is not, the
question is why one square is drawn two captions - a square that splits into several Points is the
obvious candidate, since each copy can carry a label.

### FR-067 - 2026-09-09 - A tooltip in the autonomy editor's right-click menu saying Control+S renames

**Kind:** feature request  
**Raised from:** MT-293  
**Filed:** 2026-09-09  

Adam, on MT-293 (2026-09-08), in the same breath as passing it:

> *"Works, but we need a tooltip in the right-click menu in the autonomy editor showing that
> control+S is rename."*

MT-293 is **Control+L moves the caption dropdown with it** (from RGD-C3), and passed. This is a
separate, small, exact request made during the run, and it appears nowhere in issues.md.

The item is **Rename...** on the tile menu (`AutonomyEditorPanel.buildTileMenu`, which the track
diagram viewer also borrows through `TrainControlUI.buildAutonomyTileMenu`). A shortcut that exists
and is not written next to the thing it does is a shortcut nobody finds - the same argument the
accelerator hints elsewhere in the menus are there for. Worth checking at the same time whether the
other diagram chords (Control+L, Control+K, Control+G, Control+X/V/Delete) have anywhere at all that
names them.

### OB-194 - 2026-09-09 - Clearing every locomotive in the autonomy editor cannot be undone by Cancel

**Kind:** bug  
**Raised from:** MT-311  
**Filed:** 2026-09-09  

Adam, on MT-311 (2026-09-08), in the same breath as passing it:

> *"It works, but bug: clearning locomotives in the autonomy editor cannot be undone by a cancel.
> Make this clear in the popup."*

MT-311 is **Bulk Tools clears placements and keeps the homes** (from MT-257 item 1), and the counts
and the greying it asks about are right.

**IT CONTRADICTS THAT ENTRY'S OWN EXPECTATION**, which reads *"neither writes to disk - Cancel puts
everything back"*. What changed in between is OB-183: Adam, 2026-09-08, *"Where a train IS is a fact,
and where the file thinks it is is a record."*  Placements are now carried ACROSS a rebuild
(`TrainControlUI.putTheTrainsBack`) rather than regenerated from the setup, which is very likely why
Cancel no longer puts the cleared locomotives back - Cancel restores the SETUP, and the running
railway is what the placements now come from.

**Whether the answer is the warning he asked for or a real undo is a decision, not a defect report.**
The warning is the smaller of the two and is what he asked for: `AutonomyEditorPanel.clearAllPlacements`
already confirms first, and the confirmation can say that Cancel will not bring them back. A real undo
means the bulk clear capturing what it removed and the editor's Cancel replaying it, which is a
different and larger thing.

### FR-068 - 2026-09-09 - Whether a route condition with a bracket that is not at the start can be represented at all

**Kind:** feature request  
**Raised from:** MT-320  
**Filed:** 2026-09-09  

Adam, on MT-320 (2026-09-08), in the same breath as passing it:

> *"i have no such route - these can no longer be opened, anyway. make sure such examples can be
> properly represented."*

MT-320 is **A route condition with a bracket that is not at the start** (from IPR-B2), and it closed
on its own terms: it says that if he has no such route it is done, and he has none.

**THE SENTENCE AFTER IT IS A DIFFERENT QUESTION FROM THE ONE THAT CLOSED.** MT-320 asked whether the
editor MISREADS a condition like `3 or ((1 or 2) and 4)` - it used to turn the AND into an OR, flag
nothing red, evaluate the wrong expression in Test, and write it back on save. This asks whether the
editor can EXPRESS that shape at all: the condition rows are a flat list with one grouping level, and
a nested group that is not the first term has no representation in them.

Two honest answers, and the choice is Adam's:

1. the row editor gains real nesting, which is the larger piece of work;
2. it keeps the shape it has and REFUSES such a condition explicitly rather than silently flattening
   it - a message saying the expression cannot be shown, with the text preserved untouched.

The second is what the current behaviour is one step away from, and it is the one that cannot lose
somebody's route.

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

### OB-207 - 2026-09-12 - en57-203 blocks too much

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-12 02:10  
**Build:** commit ac960047, build\classes, compiled 12 Sep 01:57 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

when at tunnellongpark, en57-203 blocks most of the track leading up to bottommaina, even though it is of length 1, and the track next to it is of length 2.  too much blocked for such a short train.

**Claude, 2026-09-12 - diagnosed, not fixed, and the reason is worth reading before anybody patches it.**

`Layout.edgesCoveredByStandingTrains` walks back from the square a train stands on and blocks whole EDGES
until it has covered the train's length:

    covered.put(segment, loc);
    remaining -= segment.getLength();

**Blocking a whole edge is right, and that is the part not to change.** An edge is a run between two
Points with nowhere to stop in between, so any route using it passes end to end - a train sent over that
edge meets the standing train however far into it the tail actually reaches. What looks like "most of the
track leading up to BottomMainA" is ONE logical edge that happens to be physically long.

**The only real over-blocking is a train that never reaches the edge at all**, which is exactly this
case: EN57-203 is one unit long and is standing on its own square, so nothing behind it is fouled. The
walk starts `remaining = trainLength` and blocks the first edge before crediting the square the train is
standing on, so a one-unit train always fouls one whole edge.

**Why crediting the square is not a one-line fix.** Nothing at runtime knows how long a square is: a
`Point` carries `maxTrainLength` and no length of its own, and lengths live on edges - where
`GraphReducer` already folds the far tile in (`sumLength(path) + lengthOf(tile)`). So the fix is

  1. emit a per-point length from `AutonomyBuilder`, read it in `parseAuto`, hold it on `Point`; and
  2. credit it once, carefully: the first edge's length ALREADY includes that tile, so subtracting the
     full edge length after crediting the square double-counts it.

**And on this railway it would change nothing yet.** `setup.json` carries three measured tiles -
`5:19,12`, `5:13,12` and `5:10,10` - and TunnelLongPark is not one of them. With the square unmeasured
the walk cannot know the train fits on it, and "unmeasured is unknown, not zero" says block. So measuring
the berth squares is what turns this off today, and the change above is what makes the measurement pay.

Left open deliberately: this is the guard that stops one train being routed into another, and the change
is a model change plus an arithmetic trap. It wants its own pass rather than the end of a long one.

**Claude, 2026-09-12, re-measured against Adam's screenshot - and the paragraph above is WRONG where it
says blocking a whole edge is right.**

He said it plainly: *"we have a train of length 1 standing on a track of length 2, so it does not protrude
past the switches."*  Measured on his own layout, the edge the walk blocks is:

    TunnelLongPark -> BottomMainA   length 4, 12 tiles, THREE switches
    10,10{2} 10,11[SW] 11,11 11,12[SW] 12,12 13,12{1} 14,12[SW] 15,12 16,12 17,12 18,12 19,12{1}

So a one-unit train lying inside tile 10,10 - which is measured at 2, and is the only tile it reaches -
marks all twelve, across three switches, to the far end at BottomMainA. That is the screenshot.

**Why the earlier reasoning was wrong.** It said a route using the edge passes end to end through the
occupied part, so blocking it all costs nothing. That is true of the ROUTING, and it is not the whole
story: this edge shares its first seven tiles with `-> RampDown` and `-> BottomCrossover`, which diverge at
the switches inside it. The picture greys all twelve for every one of them.

**And the first edge's covering is redundant for routing anyway.** The walk's first hop always has the
train's own square as one endpoint, and `isPathClear` already refuses a path through an occupied point.
So what the wide mark protects, occupancy protects already; what it costs is the picture.

**There are already TWO marks and only one of them is coarse.**
`AutonomySession.routesCoveredByStandingTrains` is per square AND per ROAD - it was built for MT-309, when
Adam said *"graying makes it look confusing on double curve tiles"* - and it walks back only as far as the
train reaches. The greying beside it, added 2026-09-09, is per EDGE and per whole tile. **The double-curve
confusion he reports today is the second mark, not the first**, and the machinery to draw it selectively
already exists in the first.

So the choice, and it is his:

  1. **Draw the wide mark per road**, as the narrow one already is. No effect on routing, and it answers
     *"selectively gray the right half of it"* without a warning on every double curve.
  2. **Stop the tail at the first switch**, which is his own vocabulary from the room rule - *"the train
     comes to rest clear of the points"*. This narrows the GUARD, not just the picture, and a one-unit
     train would then mark tile 10,10 and nothing else.

(1) is safe and cheap. (2) is the one that makes the screenshot right, and it is a change to the guard
that stops one train being routed into another.

**Claude, 2026-09-12, third measurement - (1) is done and it is NOT enough, and the reason is a mechanism
neither of the notes above accounts for.**

Adam: *"75 407 DB cannot go from Tunnel to BottomMainA even though it should be able to"*, and then, at
lengths of 1 and 2, *"this part still needs to be fixed."*  Asked of his own layout:

    length 1: clear=false  why=null
              lastError = Disallowed because EN57-203 is standing across
                          BottomMainAPre (eastbound) -> BottomMainA (eastbound)
    length 2: clear=false  why=too long ... which measures 1
    length 3: clear=false  why=too long ... which measures 1

Two different refusals, and the interesting one is the first.

**The path does not use the covered edge at all.**  EN57-203 covers `BottomMainA (westbound) ->
TunnelLongPark`; the path is `Tunnel (southbound) -> BottomMainAPre (eastbound) -> BottomMainA
(eastbound)`.  What refuses it is `isPathClear`'s SHARED-METAL sweep: for each edge of the path it asks
whether any edge sharing tiles with it is covered, through `getLockEdges()`.  Those two edges run over the
same rails - 15,12 to 19,12 - so the covered one fouls the other.

**And that is right in principle and wrong here**, because the covered edge is covered WHOLE while
EN57-203 lies inside 10,10 alone.  A one-unit train therefore makes BottomMainA unreachable from
anywhere.

**It also corrects the note above.**  "The first hop's edge covering is redundant for routing, because it
always has the train's own occupied square as an endpoint" is false: through shared metal it refuses edges
whose endpoints are both free.  Narrowing the picture could never have fixed this, and did not.

**What the real fix needs, and why it was not done tonight.**  Per-TILE coverage: the guard must ask
whether another edge uses a tile the train is actually lying on. The runtime cannot ask that - `Edge`
holds start, end, length, roomAtTheEnd and lockEdges, and **no tiles** - so it needs the builder to emit
each edge's tiles AND their lengths, `parseAuto` to read them, and `Edge` to hold them. Only then can
`edgesCoveredByStandingTrains` answer in tiles and the shared-metal sweep intersect against them.

Measuring more track does NOT work around it: the first edge is covered whole whatever the lengths say.

Left for a fresh pass, deliberately. This is the guard that stops one train being routed into another, the
change is a model change, and the last two answers written on this issue were both wrong in a way that
only measuring caught.

**Claude, 2026-09-12 - FIXED, along the line Adam proposed.**

*"Wouldn't the best solution be to create / leverage additional edges, as there has to be a computed
pre-reduction point where the switch is?"*  There is, and the reduction has always had it: `locationsOf`
gives every step of an edge a place id, and `deriveLocks` builds the whole shared-metal relation by
intersecting those sets. Only the RESULT reached the runtime - "edge A cannot run with edge B" - so
`Layout` could answer at whole-edge grain and no finer.

So the places themselves now travel, rather than new nodes being invented for them: splitting the graph at
switches would rename every edge that crosses one (an edge's identity is the pair of Point names) and put
a Point with no feedback in the middle of the clearance check, when a Point in this model is a sensor.

  - `GraphReducer.placesAlong` - each edge's places in order, each with what it measures. Far endpoint
    included, near one not, so they sum to the edge's own length by construction.
  - `AutonomyBuilder` writes them, `Edge.toJSON` re-writes them (S14-B3), `Layout.fromJSON` reads them.
  - `Layout.walkStandingTrains` - the one tail walk, now answering in both edges and PLACES: it spends the
    train's length across the places from the end it entered by, claiming each before spending it.
  - `isPathClear`'s shared-metal sweep asks `tailLiesOn`: is the tail on metal THIS edge runs over? The
    DIRECT case stays whole-edge - a path uses all of its own edges, so a tail anywhere on one is in the
    way; only a SHARED edge can be touched at one end and no further.

An edge with no places keeps the whole-edge answer, so a hand-written configuration behaves as it did.

Measured on Adam's layout, same probe as the third measurement above:

    length 1: clear=TRUE
    length 2: clear=false  why=too long ... which measures 1
    length 3: clear=false  why=too long ... which measures 1

Test: `core.testAShortTrainDoesNotBlockTheWholeRun`, seen red first with exactly this refusal. It states
the geometry it needs (the tile behind the park measured at 2), asserts the covering is real and PARTIAL -
the train lies on some but not all of the covered run's places - and carries a control that a fifty-unit
train there still blocks. Both claims also assert every edge involved carries places, so neither can be
answered by the whole-edge fallback. Mutating `tailLiesOn` to a constant fails one claim each way.

**What remained after this, and it is now settled.** Length 2 was still refused, by the ROOM rule
rather than by EN57-203: `BottomMainAPre (eastbound) -> BottomMainA (eastbound)` measures 2 and its room
after the last switch is 1, because the switch sits inside that stretch. Adam read it as *"there are 2
units of room between BottomMainAPre and BottomMainA (2>=2)"*.

**Claude, 2026-09-12 - his reading is now the rule, and it is his ruling that made it one.**

Measured for him first, because the example he reached for did not say what he expected. With the figures
he gave - the run is really 6, split 3 either side of the switch at 14,12 - a six-unit train at
BottomMainA lies on the points whatever way the units are distributed, and closes both roads to the lower
level: 33 ordered pairs of stations stop being reachable. His own stated criterion, *"it doesn't interfere
with any other path to a primary station"*, refuses it. And the protruding train at TunnelLongPark he
wanted told apart from it does the identical thing one junction along.

So there is no property of the track that separates them, and he ruled on the difference himself: *"make
a rule that parking berths cant block any other edges, but not make that check for active stations."*
What decides is how long the train stays. `behaviour.md` section 5a carries the rule and what it spends;
`core.testABerthAndAPlatformJudgeAnOverhangDifferently` is the test, with all four mutations run.

On his layout as it stands today, `Tunnel -> BottomMainA` is now clear at lengths 1 AND 2 - the approach
measures 2, so his 2>=2 is exactly what admits it - and still refused at 3.

### OB-208 - 2026-09-12 - the grey mark and what routing refuses no longer agree

**Kind:** bug  
**Raised from:** the triage API  
**Filed:** 2026-09-12  

`ui.testBlockedTrackIsGreyWhileAutonomyRuns` is red, and it is red because its subject stopped existing when the wash was narrowed on your instruction - so this is a question rather than a fix.

**What the test asserts.** Three kinds of square, each drawn differently: orange (a train is lying there), grey and no orange (track a train's presence has made unusable), neither (free). It derives the blocked extent from `Layout.edgesCoveredByStandingTrains` - whole edges - and then picks a square in that set that the orange line does NOT cover. On the current build there is no such square, so nothing is drawn fainter and both claims fail at alpha 255.

**Why.** `AutonomySession.tilesBlockedByStandingTrains` delegates to `tilesCoveredByStandingTrains`, which is the per-square walk that pays each square its own length - the narrowing behind *"the visuals look perfect now"*. The grey and the orange therefore now mark the SAME squares, and the middle category is empty.

**What routing still refuses, after OB-207.** A path is refused if it uses a covered edge at all (whole-edge, deliberately left so - a path uses all of its own edges), or if it shares metal with a place the tail actually lies on. The second half now agrees with the picture exactly, which is new. The first does not: an edge is refused over its whole length while only the train's part of it is drawn.

**That may be fine.** A path through a covered edge runs over the square the train is standing on, and that square IS drawn - in orange. So the picture arguably says enough, and the grey is now redundant rather than wrong.

**Your call, and the test follows it either way:**

  1. **The grey stays as it is** - the same squares as the orange, saying "unusable" where the line says "train". Then this test's middle category is gone for good and the class is rewritten around two kinds of square instead of three.
  2. **The grey goes back to what routing refuses** - whole covered edges, plus the edges sharing the tail's places. Then the picture is stricter than the train's extent again, which is what MT-309 moved away from, and the double-curve work stays as it is.

I have not picked one. The last two answers written on this question were both wrong, and this one sits between a ruling of yours about the drawing and a ruling of yours about the guard.

### OB-209 - 2026-09-12 - timetable capture test times out under load

**Kind:** bug  
**Raised from:** the triage API  
**Filed:** 2026-09-12  

`core.testTimetableCaptureThroughARealRun.testARealRunCapturesNothingWithCaptureOff` fails intermittently under load, and it is a timeout rather than a refusal.

Measured on 2026-09-12: red in the full battery, then red three times standalone immediately after it while the machine was still busy, then green three times in a row once it had settled - identical source each time. It appeared once before, in the battery of 2026-09-11 09:35, with the two batteries after it green.

The message is *"no locomotive moved in 480 seconds"*, with the one train reporting all three destinations as `autolayout.why.blockedWhileRunning`. That sentence is a SUBSTITUTION, not a diagnosis: `whyNothingMoved` replaces the real reason with it whenever autonomy is running, so what the failure prints cannot say which rule refused. Adding a probe that prints `Layout.getLastError()` instead made the test pass, which is the usual sign of a timing window rather than a rule.

**Not OB-207.** Disabling the new place narrowing (`tailLiesOn` forced true, which restores the pre-2026-09-12 whole-edge sweep) leaves it red, so the change of that evening is not the cause.

Left as a report rather than a fix: it needs the real refusal reason to be visible on the failing path before anything can be said about which rule is slow, and that is the same gap OB-199 describes about `whyNothingMoved` being asked after the flag it reports has been cleared.

### OB-210 - 2026-09-12 - a non-reversible train is offered a terminus from the locomotive tab

**Kind:** bug  
**Raised from:** the triage API  
**Filed:** 2026-09-12  

Adam, 2026-09-12: *"In the current setup, why can non-reversible EN57-947 be manually sent from BottomSecondary to BottomMainC (terminus, train is non reversible, not a berth)?"*  And, on the diagnosis: *"there is no debate that bottommainc is a terminus."*

**A rule enforced at one door of two, and the second door is mine.**  OB-205 put the rule in `Layout.isOfferableToOperator` - a train that cannot reverse is not offered an ordinary terminus, while a parking berth stays offered - and only `LayoutRightclickAutonomyMenu` asked it.  The Locomotive commands tab builds its list from `getPossiblePaths` and marks what autonomy will not choose with a DASH, which is right for a berth and wrong here: a dash is a label, not a refusal, and the row still executes on a double click.

`AutoLocomotiveStatus.whatTheOperatorMayChoose` now filters that list through the same rule, inside the funnel both of its call sites already share.  **The rule itself, not a copy.**  `notChosenByAutonomy` beside it is a deliberate lock-free copy because it runs on the event thread three times per repaint; this runs where the path search runs, which is off the event thread for every caller with a worker and on it only for the fallback that was already taking the monitor for a whole-graph search on the same line.  `regression.testNothingOnTheEventThreadTakesTheRailwaysMonitor` carries that reasoning as its allowance.

**The dash keeps its terminus limb** and it is not dead: a terminus that is ALSO a parking berth is still offered, still dashed.  That is the line Adam drew in OB-205 and the filter respects it.

`regression.testTheDestinationDoorsAgree.testEveryDoorThatOffersADestinationAsksTheRule` is a source census over both doors, the shape `core.testNonReversibleTrains.testEveryManualDoorHandsOverAPrompt` already uses for this question.  Seen red on the Locomotive tab before the fix; taking the call back out fails it again.

### OB-211 - 2026-09-12 - the ... under the destination list shows when nothing was truncated

**Kind:** bug  
**Raised from:** the triage API  
**Filed:** 2026-09-12  

Adam, 2026-09-12: *"why is the ... below the list of options visible?  shouldn't this be only shown if there are many more regular destinations than can be displayed?"*

**Yes - and OB-205 is what made it fire constantly.**  The ellipsis compared against `PathOptions.possible`, a count of everything the search returned taken BEFORE the menu dropped the squares it never shows.  So it fired whenever anything at all had been filtered: a switched-off square, one excluding this train, and - after OB-205 - every ordinary terminus whenever the train cannot reverse.  On a non-reversible locomotive that is nearly every right-click, which is the complaint FR-058 was filed about arriving from a new direction.

The rule has a name now, `theListWasCutShort`, and it means one thing: **more ordinary destinations than fitted**.  A square this menu deliberately hides is not a destination missing from the list; it is one somebody went out of their way to switch off, and the autonomy tab lists it - which is Adam's own ruling of 2026-09-01 about switched-off squares.

**`possible` is deleted rather than corrected**, so there is no wrong number left to hand the rule.

**Corrected the same evening, and the correction is the interesting part.**  The first fix kept a SECOND site for the case where the train has nowhere at all to go, on the reasoning that it needs a way to the tab that explains why.  Adam: *"I still see ... for 74 407 DB at Tunnel, even though it has no valid paths"*.  That was my judgement rather than his instruction - his was *"only shown if there are many more regular destinations than can be displayed"* - and it left the reported symptom in place for the train with the least to show.

So the item is now decided in exactly ONE place, and nothing is lost that is not said better elsewhere: the "No available paths" tooltip names every station and why each was refused, and the setup editor's *Why not Moving?* answers the same question on the diagram.  A bare "..." said neither.

`regression.testTheDestinationDoorsAgree.testTheEllipsisMeansTheListWasCutShort` carries three claims, and the last two exist because the first cannot catch this on its own.  The arithmetic pins what the rule computes.  The second asserts that no pre-filter count exists on `PathOptions` - what was wrong was never the comparison but WHICH NUMBER was passed to it.  The third counts the places the menu draws the item and requires exactly one - a second site sits outside the rule, which is how the rule could be right and the symptom stay.

### OB-212 - 2026-09-12 - multiple opacity changes

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-12 10:29  
**Build:** commit ac960047, build\classes, compiled 12 Sep 10:25 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

minor: a parked (blocking in orange) train will be further shaded if an active autonomy route near it also locks that edge.  keep one level of opacity, dont stack.

### FR-074 - 2026-09-12 - usability of unavailable while occupied station list in autonomy editor.

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-12 18:38  
**Build:** commit ac960047, build\classes, compiled 12 Sep 18:31 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

sort unavailable while occupied list alphabetically.  lightly grey out stations that can't be chosen in full autonomy.

### FR-075 - 2026-09-13 - bulk tools

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-13 08:48  
**Build:** commit ac960047, build\classes, compiled 13 Sep 08:34 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

to bulk tools in the autonomy editor, add an option to mass mark current train locations as their homes

### OB-213 - 2026-09-13 - multi-unit function cascading UI

**Kind:** bug  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-13 09:06  
**Build:** commit ac960047, build\classes, compiled 13 Sep 08:34 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

For the buttons on multi-unit Mm2 locomotives paired with mfx/dcc ones, don't print "F<x>" text labels on the function buttons- keep the label blank as is the default.

### FR-076 - 2026-09-13 - easy tracking of station labels

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-13 22:32  
**Build:** commit ac960047, build\classes, compiled 13 Sep 22:07 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

autonomy station labels should be deduped per page, not globally- that way, other pages' stations can be tracked from a main page if desired.

### OB-218 - 2026-09-14 - the in-progress badge shows the turned facing until the train arrives

**Kind:** bug  
**Raised from:** MT-368  
**Filed:** 2026-09-14  

From MT-368, point 2 of Adam's run of 2026-09-13: *"If keep direction is selected, the 'in progress' badge on bottommainb shows the wrong direction until after the train arrives."*  Diagnosed on 2026-09-13 and not yet repaired: `configureAndLockPath` reserves every point on the route, including the destination, and the destination it reserves is the copy the path ENDS on - routinely the turning copy of a may-turn square (measured: the path offered to BottomMainC ends on its eastbound reverse copy five runs out of five).  The caption reads that copy's facing, which is the turned one; on arrival the train is re-stood on the plain copy and the badge becomes right.  The repair is to reserve the copy the train will actually end on, decided from the answer at dispatch - a change to the locking path, so it is filed rather than slipped in.

### FR-080 - 2026-09-14 - why not moving clarity

**Kind:** feature request  
**Raised from:** noticed while testing - not from a particular test  
**Filed:** 2026-09-14 08:01  
**Build:** commit f3b3b55e, build\classes, compiled 14 Sep 07:59 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

in the "why not moving" view in the autonomy editor, show all stations first, then show berths (non-autonomy stations), both in alphabetical order.  If a point is on another page, show it.

### OB-220 - 2026-09-14 - a route condition's OR was read as AND, and the first line could not be indented

**Kind:** bug  
**Raised from:** Adam, in conversation (screenshot of the route editor)  
**Filed:** 2026-09-14  

Adam, 2026-09-14, with a screenshot of the route editor: *"the first or is read as an and here. also, the first condition for some reason cannot be indented for proper grouping."*

**The OR.**  It sat one level deeper than the conditions either side of it.  A joining word joins the things at its own level, so at that depth it joined nothing, and the reading dropped it - the level's remaining AND then joined all three conditions.  No two words disagreed side by side, so nothing was drawn in red, and the route would have fired on a different condition from the one on screen.

**The first line.**  A line may be at most one level deeper than the line above it, and the first line has none, so it was refused outright - a condition could not start with a group such as `(2 or 3) and (1 or 4)`.

### FR-081 - 2026-09-14 - Reads as: bold joining words, coloured settings

**Kind:** feature request  
**Raised from:** Adam, in conversation  
**Filed:** 2026-09-14  

Adam, 2026-09-14: *"In the 'reads as', can we also bold the operators, make on/straight/green green and off/turn/red red?"*

### OB-221 - 2026-09-14 - choosing a condition line's Kind again reset it to address 1

**Kind:** bug  
**Raised from:** Adam, in conversation  
**Filed:** 2026-09-14  

Adam, 2026-09-14: *"I start with Switch 1.  Then I set address to 40, which makes it a Signal.  Then, if I click on Kind -> Signal and exit out, it snaps back to Switch 1."*

A drop-down commits whatever it holds when it closes, chosen or not, and the condition table reset a line to its kind's starting address on EVERY commit of the Kind cell - so opening the box on any line and leaving it put the line back to address 1, a sensor as much as a signal.  At a signal's address it then showed as a Switch, because address 1 on his railway is a switch.

### FR-082 - 2026-09-14 - a warning triangle with the reason beside a red word in a route's conditions

**Kind:** feature request  
**Raised from:** Adam, in conversation  
**Filed:** 2026-09-14  

Adam, 2026-09-14: *"For the red operators, can we add a warning triangle icon with a tooltip explaining what's wrong next to it?"*

### OB-222 - 2026-09-14 - the five-train Return Home test fails on the frozen layout's missing track lengths

**Kind:** bug  
**Raised from:** the battery of 2026-09-14  
**Filed:** 2026-09-14  

Found by the battery of 2026-09-14 (commit `e4651cec`): `core.testTrainsComeHomeToTheirPlatforms` red once in the battery and once of two runs on its own, green in the seven batteries before it.

**What fails.**  After the twenty-second autonomy run, two trains were left on TopMainR1Inter and TopMainR2Inter (southbound), boxed in by the trains already home on TopMainR1 and TopMainR2: a route home exists on the graph, but no path is clear and there is no square to stage through.  The planner spent its whole 15-second budget and answered NO_PLAN_FOUND, with nobody blocked.

**Why it is left open.**  Adam: *"I think the test will always fail until the railway has realistic track lengths set.  The random lengths of 1 to force edge cases will block things that shouldn't really be blocked."*  Measured on the frozen copy: `test/operator_layout` sets a length on only 6 tiles (2 to 4), every `maxTrainLength` is 0, and the test's locomotives have no train length - so a tail is measured against track that is mostly unmeasured.  Since the MT-335 fix of the same day a tail follows the route it came in on past a fork rather than stopping there, which makes that matter more.

**What closes it.**  Adam sets realistic lengths on the live layout; `test/operator_layout` is refrozen from it (copied over, and the commit says so); the class is run several times.  If it still fails with real lengths, the question becomes whether the planner should move a train that is already home out of the way.

### OB-223 - 2026-09-14 - Cancel in the autonomy editor did not undo the setup edits made in it

**Kind:** bug  
**Raised from:** MT-406  
**Filed:** 2026-09-14  

Adam, on MT-406 (2026-09-14): *"Escape works, but it seems a one-way run (or any other edits to arrows) persist after I press cancel.  They are not undone by cancelling."*

Not arrows only - every setup edit made in the autonomy editor.  Each gesture rebuilds the running layout so the railway follows the edit at once, and that rebuild loads the configuration, and loading SAVES it (`AutonomyViewerPanel.load`, "remembered for next start").  Saving clears the session's unsaved flag - so when Cancel asked whether there was unsaved work the answer was always no: nothing was asked, nothing was discarded, and the edit was already on disk.  The snapshot `LayoutEditor` takes when it opens, which the track editor's Cancel restores, was never consulted in autonomy mode.

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
| 2026-09-14 | OB-224 | bug | Adam, on MT-333: *"75 407 DB (len 2) can no longer go from tunnel to bottommaina ... this SHOULD be allowed per the standing rule that this switch blocking should only affect berthes."*  Refused at BottomMainAPre, a square it only passes, by the pass-through room check that arrived in `5948a88a` (2026-09-10) on the ruling of 2026-09-09; the relaxation of 2026-09-12 had been built at the destination only.  Only where a train comes to rest is judged now - the destination and a square it turns at - in the runtime and the Return Home planner.  The protrusion test is re-filed.  `regression.testAPassingTrainMayStandAcrossThePoints` (seen red first), `core.testATrainIsJudgedOnlyWhereItStops`, censuses re-pinned, behaviour.md 5a; `e677cbab`. | - | `MT-431, MT-432` |
| 2026-09-14 | FR-084 | feature request | Adam, on MT-399: *"when changing the auto and manual radio buttons, make it update the shown route to the new selection without having to repeat the button press sequence."*  The autonomy editor remembers the last two squares tested and runs the same test again, through the same door, when Path Type changes - only while Test a Path is still armed, and forgotten wherever the drawn route is put away.  `core.testManualOnlyPathsAreADifferentColour.testSwitchingPathTypeRedrawsTheTestedRoute`, seen red first; `b1cb1fe9`. | - | `MT-434` |
| 2026-09-14 | OB-223 | bug | Adam, on MT-406: *"a one-way run (or any other edits to arrows) persist after I press cancel.  They are not undone by cancelling."*  Every setup gesture in the autonomy editor rebuilds the running layout, and that rebuild loads the configuration, which saves it - clearing the session's unsaved flag, so Cancel never asked and a discard would have re-read a file that already held the edit.  Closing now compares the setup with the snapshot the window took when it opened, and discarding restores that snapshot and writes it; placements still follow the railway (OB-183, OB-194).  `regression.testCancelUndoesAutonomyEdits` - the arrow back in the setup and the file, the question asked, and Save keeping it - seen red first; `244b07c2`. | - | `MT-430` |
| 2026-09-14 | FR-080 | feature request | why not moving clarity - Adam: *"show all stations first, then show berths (non-autonomy stations), both in alphabetical order.  If a point is on another page, show it."*  The autonomy editor's answer asks `explainDestinationsGrouped` - the locomotive panel's own call, one lock - and lists the refused stations under that window's two headings, each alphabetical, filing a station as choosable when any copy of it is; the "can go to" names are alphabetical too, and a station on a page other than the train's carries its page, in eight languages.  `regression.testTheDiagramRefreshDoesNotWaitOnTheRailway.testTheWhyAnswerListsStationsThenBerthsWithTheirPages`, seen red first; `89d3156d`. **Claude, 2026-09-14.** Adam's note on MT-429 - capital Page, and by page then name with the current page first - built; MT-433 checks it; `a48cb5e6`. | - | `MT-429, MT-433` |
| 2026-09-14 | FR-083 | feature request | Adam, on MT-412: *"we should make a FR to track fixing this behavior so that the pre-arrival arrow just matches."*  OB-218's accepted behaviour - the route reserved on the copy the path ends on, so the arrow and badge show the turned facing until arrival - asked to match before arrival too: reserve the copy the train will end on, from the answer at dispatch.  **Claude, 2026-09-14.** Picked up and not built. | pending | - |
| 2026-09-14 | OB-222 | bug | `core.testTrainsComeHomeToTheirPlatforms` fails intermittently: after the random run two trains are boxed in on TopMainR1Inter and TopMainR2Inter and the planner answers NO_PLAN_FOUND after its whole budget.  Adam: *"I think the test will always fail until the railway has realistic track lengths set ... Shall we leave this open pending updates to the live layout that you can then freeze?"*  **Claude, 2026-09-14.** Pending Adam's lengths on the live layout; then `test/operator_layout` is refrozen and the class rerun.  The frozen copy sets a length on 6 tiles only, and every maxTrainLength is 0. | pending | - |
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
| 2026-09-12 | OB-208 | bug | **Promoted to an MT, not fixed** - it is a decision of Adam's and the issue list is not somewhere he can rule. Narrowing the wash to the train's extent, which he approved, made the grey mark the same squares as the orange line, so the "blocked but no train on it" category is empty and `ui.testBlockedTrackIsGreyWhileAutonomyRuns` asserts three kinds of square where two exist. Two ways to go, both written up on the MT: leave the grey as it is and rewrite the test around two categories, or put it back to what routing refuses. Not picked, because it sits between his ruling about the drawing and his ruling about the guard **Claude, 2026-09-14.** It asked for Adam's decision, and he ran its test and said **Works** (MT-373, 2026-09-12) before the entry was superseded. | fixed validated | - |
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
| 2026-09-09 | OB-194 | bug | Adam: *“Warning if using the bulk tool”* - so the warning rather than a real undo. The confirmation was a fixed sentence about home assignments; it now names every locomotive it is about to lift, says how many squares it will empty, and says that Cancel will not put them back. It cannot, since OB-183: placements are read from the railway rather than from the setup, so closing the editor restores the file and not the trains. One builder feeds both the dialog and the menu item's tooltip, so the warning cannot arrive only after the click. `regression.testTheBulkClearWarnsThatCancelWillNotUndoIt` asks the editor for the string and checks all three, in whichever of the eight languages the run is in **Claude, 2026-09-14.** Fixed in code with an automated test, and never checked by hand - MT-415 asks for that check.  Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-415` |
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
| 2026-08-25 | FB-A1..C2 | bug | [Independent pass over the last day](../reviews/2026-08-25-independent-fable.md) - two more A, one of them a defect in an LD fix **Claude, 2026-09-14.** Closed on Adam's word, 2026-09-14: *"close the 11 internal ones"* - an internal or test-only fix with automated coverage and nothing to check by hand. | fixed validated | - |
| 2026-08-25 | LD-A1..C5 | bug | [The last day, reviewed](../reviews/2026-08-25-last-day.md) - six A and seven B, seven of them from that same day; C6-C9 left open **Claude, 2026-09-14.** Tracked through its tests from now on, so this row's state follows theirs. | - | `MT-184, MT-189, MT-191` |
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

`docs/reviews/2026-08-18-manual-test-plan.md` has a "Feature backlog (Adam, 18 August)" section -
things written down so they would not be lost, none of them scheduled. It has not been picked up into
this mechanism, deliberately: filing something here is a decision, and those were explicitly not
decisions. Anything from it you want on the ledger, paste into the Inbox above and it will be.
