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

**Claude, 2026-09-14 - what Cancel does has changed since (WK7-C2, WKV-B1).**  OB-223 made Cancel restore
the setup as the editor opened it, placements included, and the rebuild when the editor closes puts the
cleared locomotives back - so the warning now says Cancel puts them back and Save keeps the change.  On the
track diagram's own right-click menu, which has no Cancel and saves as it goes, it says the clear is saved
at once.  `regression.testTheBulkClearWarnsThatCancelWillNotUndoIt` is now
`regression.testTheBulkClearSaysWhatCancelDoes`, and `regression.testCancelUndoesAutonomyEdits` runs the undo.
MT-415, which Adam passed on the old wording, expects the opposite sentence.

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

### FR-085 - 2026-09-14 - Ask for the farthest sensor a placed train's tail crossed, and keep the road it names

**Kind:** feature request  
**Raised from:** WK7-B1  
**Filed:** 2026-09-14  

Adam, 2026-09-14, on WK7-B1: *"For the tails, if a train is long, why not ask the user to specify the last sensor it crossed from a list of possible sensors?  Then state will always be fully consistent."*  And: *"Go - build it autonomously, battery when done.  the prompt should ask the user to pick from a list and select the farthest sensor the tail of the train recently crossed.  Also, ideally in the autonomy editor, it should allow the user to click to select as well."*

Two halves.  **Kept:** a standing train's arrival road is saved with the point, reloaded, carried across every rebuild and captured into the setup, so a driven train's tail is the same after a restart or any setup gesture (WK7-B1, `63ac68f4`).  **Asked:** a hand-placed train long enough for its tail to have crossed sensors on two roads back from a junction is asked for the farthest sensor its tail crossed - at the paste, the right-click Place and the locomotive dialog, in the right-click menu beside Train arrived from, and by clicking in the autonomy editor.  behaviour.md 5c.

### FR-086 - 2026-09-15 - Control+N for Show a Station Name Here in the autonomy editor

**Kind:** feature request  
**Raised from:** MT-397  
**Filed:** 2026-09-15  

Adam, on MT-397 (2026-09-15): *"Works, but let's add a hotkey for 'show station name here' too"*.  Asked which key, he chose **Control+N** (free in the editor; N for name).  Named in the item's tooltip like the other editor shortcuts (`ui.testTheEditorNamesItsShortcuts`).

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

### OB-228 - 2026-09-15 - Return Home plans a move through the tail of a train it has just moved

**Kind:** bug  
**Raised from:** MT-335  
**Filed:** 2026-09-15  

Adam, on MT-335 (2026-09-15): *"The 335 park works, but I get: Could not run EN57-203 from BottomInner (northbound) to TopMainR0Park - the path stayed blocked. ... Shouldn't it get sent to rampdown and then parked?"*  His log: the plan moved 75 407 DB Tunnel -> BottomMainA first, then routed EN57-203 BottomInner -> Tunnel -> BottomMainAPre -> RampDown -> TopMainPost -> TopMainR0Park, and the runtime refused it: *"75 407 DB is standing across BottomMainAPre -> RampDown - the train is longer than the track in front of it"*.  The planner models the tails of trains that have not moved yet (MT-335's stated limit); a train it has moved now has a known road - the route the plan gave it - so its tail can be modelled too.  The same log's agreement check also reported the planner allowing 75 407 DB -> RampDown and -> BottomSecondary where the layout would refuse them.

### OB-229 - 2026-09-15 - Return Home's agreement check: the planner allows 75 407 DB to RampDown and BottomSecondary, the layout does not

**Kind:** bug  
**Raised from:** MT-335  
**Filed:** 2026-09-15  

From Adam's MT-335 run of 2026-09-15 (log `traincontrol-20260915-035118.log`, 03:54:12 and again at 03:55:14, with 75 407 DB standing at Tunnel before the plan): *"planner allows 75 407 DB -> BottomSecondary, but the layout would refuse it"* and *"planner allows 75 407 DB -> RampDown, but the layout would refuse it"*.  `HomeStaging.auditAgainstRuntime` compares `firstClearRoute` with `Layout.getPossiblePaths` for the railway as it stands, so this is a rule the planner applies differently from the runtime - the OB-073 shape: a plan the runtime refuses on the move.  Neither move was in the plan that ran, and OB-228 was the refusal Adam saw, so this is recorded rather than guessed at: both are squares autonomy will never choose, and the berth rules are the first place to compare.  **Narrowed by review MFR-C8 (2026-09-15):** the audit counted three disagreements and printed two, because `logStagingAudit` prints `placeNameOf`, which names a square and not its copy - so the third is almost certainly RampDown's other copy (northbound, and northbound reverse).  The rule to compare is one that reads the copy - a turn at the destination, a route ending on a reverse copy - more than the berth rules.  FR-087 does not bear on it (neither square is one autonomy may choose), and OB-228's moved tails are empty when the audit runs.

**Investigated 2026-09-15, and fixed.**  Reproduced on a copy of Adam's layout with 75 407 DB stood back at Tunnel: three
disagreements - BottomSecondary, RampDown (southbound), and RampDown (southbound, reverse), the one the log did not print.
**The planner was right.**  Its route to all three - Tunnel, BottomMainA or B, BottomMainPost, RampUp, TopMainR1,
TopMainPost, down RampDown - passes `isPathClear`.  **The railway's route search did not find it.**  `Layout.bfs` marks a
square visited the first time it is taken off the queue, and the shortest way to TopMainPost (eastbound) ran through
TopMainR0Park, a terminus `isPathClear` refuses in the middle of a route - so that square was spent on a route that could
never be used and the loop through it was never tried; its one offering for RampDown was refused, and it stopped.  The
right-click menu (`getPossiblePaths`), autonomy (`pickPath`) and Why Not Moving? (`firstClearOrWhyNot`,
`whyNoRouteFitsTo`) all use that search.  Asked how to fix it, Adam chose **search past termini**: the search does not
extend a route through a terminus that is not its end.  For the four trains where they stood that is these three destinations - but **across every station pair** a later probe (every route each search can yield, put to `isPathClear` with a train at the start, disagreements retried thirty times) found **97 pairs opening on his layout and 153 on the frozen snapshot, and none lost**; the v2.8.1 railway drives the same 102 pairs before and after.  Every one is the same loop - up the ramp, across the top, down RampDown - and they end at seven destinations; the report Adam was sent lists them.  The first figure given him, +3, counted only the trains' current squares.  **Adam, 2026-09-15:** *"These paths all make sense to me, except for the circular ones like RampDown (northbound, reverse) -> RampDown (southbound, reverse).  These are the same point so we should never do a round trip just to change direction."*  Three of the 97 were those; the right-click menu and autonomy never offered them (a copy of the square a train stands on reads as occupied by it), and Return Home's planner now refuses them too - `core.testARouteIsFoundPastATerminus.testNoRoundTripBackToTheTrainsOwnSquare`, red first.  So 94 real pairs.  Two pinned counts that measure the search rather than the drivable railway moved with it: `test/autonomy_formats/v2_8_1-station-paths.txt` (1381 to 1095 pairs, none ever drivable) and the room-rule census's routable pairs (1848 to 1354).  `core.testARouteIsFoundPastATerminus`, red first (`4b519e71`); fixed
in `badb0a2c`; MT-441.

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

### OB-231 - 2026-09-16 - Mass Assign Lengths and Name Everything leave a trail of yellow flashes when skipped quickly

**Kind:** bug  
**Raised from:** MT-454  
**Filed:** 2026-09-16  

Adam, on MT-454, 2026-09-16: *"When iterating quickly on the assign lengths popup (like by clicking skip), it does not clear the prior highlights."*

**Why.**  Every step of the walk calls `LayoutEditor.reveal`, which gives the square the diagram's yellow flash for 2.25 seconds by swapping its icon and starting a timer.  Nothing ended that flash when the walk moved on, so pressing Skip faster than 2.25 seconds left a trail of yellow squares, each looking like the one being asked about.  Name Everything reveals through the same door and had the same trail.  The orange outline of the stretch was replaced correctly at each step; the yellow flash was not.

**Fixed.**  `LayoutLabel.endFlash` ends a flash at once, and `reveal` ends the flash on the square it revealed last before flashing the next.  `regression.testAWalkMovesTheFlashOn` opens the real editor on the live snapshot, reveals two squares in a row and requires the first to be back to its own picture at once - red first with the reported symptom, the first square still wearing the highlight.  The route editor's flash, which lights several squares on purpose, is untouched.

### FR-090 - 2026-09-16 - The walk's prompt stays where it was left until a new round is started

**Kind:** feature request  
**Raised from:** MT-454  
**Filed:** 2026-09-16  

Adam, on MT-454, 2026-09-16: *"when the popup closes and reopens, make sure it remembers its location unless I reopen a new mass assignment round from the right click menu.  right now, every skip press re centers it, which covers some of the track diagram"*

**Why it re-centred.**  Each prompt of the walk is a new dialog, and `JOptionPane.createDialog` centres every one on its owner - so a prompt dragged off the track diagram came back over it at the next Skip.

**Built.**  Each prompt of a walk opens where the last one was left; starting a round from the right-click menu opens its first prompt afresh.  Name Everything's walk had the same habit and shares it.  `core.testMassAssignLengths.testTheWalkPromptStaysWhereItWasLeftUntilANewRound` drives the real walk on the event thread: it moves the first prompt, presses Skip, and requires the next prompt where the first was left, then presses Cancel, starts a new round and requires its first prompt NOT to open there.  Red first - the second prompt opened centred, at 733,489 - and a mutation dropping the new-round reset fails its second half.

### OB-232 - 2026-09-16 - The walk prompt's number field did not have the keyboard focus

**Kind:** bug  
**Raised from:** MT-454  
**Filed:** 2026-09-16  

Adam, on MT-454, 2026-09-16: *"make sure the entry field is focused, and when I hit enter after typing the number, it submits and goes to the next one (it may already do the latter)"*

**Why.**  The prompt was a `JOptionPane` with OK as its initial value, so when the dialog gained the keyboard focus it gave it to the OK button.  The field did ask for the focus, but when it was added to the prompt - before the dialog was on screen - so that request lost.  What was typed went nowhere; Enter on the empty field did submit, as he suspected, and an empty answer counts as Skip.

**Fixed.**  Both walk prompts - Mass Assign Lengths and Name Everything - now have no initial value, so no button takes the focus and the field, the first thing in the prompt that can, has it.  Enter in the field answers OK through the field's own listener, as it already did.  A window-focus listener was tried first and is not in the fix: it lost the same race, and once the initial value was gone a mutation removing it changed nothing, so it would have been code nothing proves.

`core.testMassAssignLengths.testTheNumberFieldHasFocusAndEnterSubmits` drives the real walk: it waits for the prompt to take the keyboard focus, requires it to be in the number field, then types 3 and presses Enter on the field and requires the next prompt and the first piece measured.  Red first: the prompt had the focus and the OK button held it.  A desktop that never gives the test window focus would make it skip, not pass; this one gives it.

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

### OB-240 - 2026-09-21 - deleting a joining word silently turns an OR into an AND

**Kind:** bug  
**Raised from:** your note on MT-469, 2026-09-21  
**Filed:** 2026-09-21  

**Your note on MT-469, 2026-09-21:** *"in the layout view, we can review [remove] operators (like
and) without deleting the conditions they are linked to.  This permanently leaves an orphan entry.  Any
linked entries should also be deleted."*

**Measured, because the consequence is worse than an orphan.**  The condition outline holds the joining
words as lines of their own, and `removeAt` takes out only the line you picked: taking out a CONDITION
also removes the word beside it (deliberately - "1 and 2" without 2 is "1"), but taking out a WORD
leaves the two conditions with nothing between them.  `tidy()` sweeps a word with nothing on one side
and two words in a row, and a RUN WITH NO WORD AT ALL is legal to it.  What that outline then means,
run through `ConditionOutline.toExpression`:

| the outline | what the route fires on |
|---|---|
| `1`, `and`, `2` | 1 AND 2 |
| `1`, `or`, `2` | 1 OR 2 |
| `1`, `2` - the word deleted | **1 AND 2** |
| `1`, `or`, `2`, `3` | 1 OR 2 OR 3 - the wordless third is folded into the run |

And `ConditionOutline.whatIsWrong` returns EMPTY for both wordless cases, so no row goes red and Save
writes it.

**So deleting an OR silently turns it into an AND.**  That is a change to when the route fires, made by
a delete that says nothing about firing - the same shape as FR3-B1, whose comment in `tidy()` describes
the level-0 word defaulting to AND after an unrelated deletion.

**FIXED 2026-09-21, AS YOU WROTE IT.**  Asked whether this was done, you said the deletion of the
and/or followed by orphaning is what you wanted put right, and your note on MT-469 already said how:
*"Any linked entries should also be deleted."*

**Built in the form that mirrors the rule already there, which is the smallest version of (a).**
Deleting a CONDITION has always taken the word beside it - "1 and 2" without the 2 is "1" - so deleting
the WORD now takes the term it joins on, and "1 and 2" without the `and` is "1" as well.  One term, not
both sides: a keystroke removes exactly as much as the mirror-image keystroke already removed, so no
confirmation dialog was added.  Say if you would rather it asked.

**The term, which is not always one line.**  `ConditionOutline.write` puts a group's rows one level in,
so the term after a word at depth *d* is either a single line at *d* - the `C` of `(A or B) and C` - or
the run of deeper lines that follows - the `(B and C)` of `A or (B and C)`.  Taking only a group's first
line would leave the rest of it behind with its own leading word, which `tidy()` then sweeps, producing
the same wordless run by another road.  Both shapes are asserted.

`ui.testRouteEditorValidation.testDeletingAJoiningWordTakesTheTermItJoinsWithIt`, seen red first: the
pair case left two lines behind and read back as `And(x,x)`.

**One thing deliberately NOT added, so you can ask for it.**  `whatIsWrong` still returns empty for a
wordless run.  Nothing can now produce one: this door was the only way in, and an outline that arrives
from a file is built by `ConditionOutline.of` from a parsed expression, which always carries its
operators.  A flag there would be a check for a state nothing reaches - cheap to add through the same
seam the tests use if you want belt and braces.

MT-469 itself passed and is validated.

### OB-241 - 2026-09-21 - the tail walk uses the recorded road for the first hop only

**Kind:** bug  
**Raised from:** measured while answering MT-438, 2026-09-21  
**Filed:** 2026-09-21  

**Found by measurement while answering MT-438, 2026-09-21.**

`Layout.walkOneTail` uses the recorded road (`arrivedAlong`) to choose the way back at the FIRST hop
only - its own comment says so: *"Only the first hop can be chosen this way; past that the train is
somewhere it never stopped, and the deterministic rule below takes over."*  Past that it uses the fork
rule: one way back means the tail certainly lies there, several means stop.

But the road is the whole journey, not one leg.  Measured on your railway, with 75 407 DB standing at
Tunnel and the twelve-edge road your configuration records:

| the railway claims | your road says |
|---|---|
| `TunnelPre (northbound) -> Tunnel (southbound)` | the same |
| `BottomSecondary -> TunnelPre (northbound)` | the same |
| `RampDown (northbound, reverse) -> BottomSecondary` | **`RampDown (southbound) -> BottomSecondary`** |

The third hop took the other COPY of the rail.  Here that costs nothing - two copies of one rail are one
piece of metal, and `getLockEdges` treats them alike - but it shows the shape: at a square where the
road knows which way the train came, the walk asks the graph instead.  Where the two roads back are
DIFFERENT metal (RampDown has BottomMainAPre on one side and TopMainPost on the other), the fork rule
stops rather than guessing, so nothing is claimed - but the road would have said which one, and the
tail would have been claimed correctly and further back.

**WITHDRAWN THE SAME DAY - THE PREMISE IS WRONG.**  `walkOneTail` already follows the road past a
junction: MT-335, Adam's ruling of 2026-09-13, added exactly that - *"PAST A JUNCTION, THE ROAD IT CAME
IN ON"* - and it is pinned by `core.testATailFollowsTheRouteItCameIn`, with the fork rule kept for a
train that has no road.  The comment I read (*"Only the first hop can be chosen this way"*) is about the
first-hop MECHANISM, not about the road being unused later.

Found by writing the test first: three claims on a junction fixture all passed before any change, which
is a green test disproving the hypothesis rather than a red one confirming it.  The duplicate test was
deleted rather than committed - `testATailFollowsTheRouteItCameIn` already makes all three claims.

What remains true is the observation, and it costs nothing: at RampDown the walk claimed the
`northbound, reverse` copy where the road says `southbound`.  Two copies of one rail are one piece of
metal and `getLockEdges` treats them alike, so the claim is the same track either way.

**What it needs to ship**: a claim that goes red on the road CHOSEN rather than on how much is claimed -
a fixture where the two ways back are different metal and the road names one of them. `single-switch`
has the shape of a fork but not the recorded road; the tail suite's `aPlatformBehindAJunction` has the
road but is built in code rather than on tiles.

Low, because what it changes today is how FAR a tail is claimed rather than whether the claim is right,
and it errs towards claiming less.

### OB-242 - 2026-09-21 - a covered-track mark outlives the train that justified it

**Kind:** bug  
**Raised from:** MT-438, measured 2026-09-21  
**Filed:** 2026-09-21  

**Adam, MT-438, 2026-09-21:** *"75 407 DB's tail was there earlier, but it never crossed the
switches.  it departed, so the tail would be gone as of the time of this screenshot."*

So the orange mark outlived the train that justified it. The claim was true when it was made.

**What the screenshot says, read off it as data.** On its true 30-pixel grid the TRAIN_MARK line is six
tiles: `10,12 11,12 12,12 13,12 14,12 14,11` - the first places of the edge `BottomMainAPre ->
RampDown`, stopping at switch 99. Everything else orange in that image is a badge (uniform 134-pixel
blobs; parking points autonomy leaves alone).

**The state that produces exactly those six**: searched, not assumed - of 1,590 placements on
`live-snapshot` (every point, every arrival side, lengths 1 to 6), exactly ONE matches: a three-unit
train at `BottomMainAPre (eastbound)` recorded as arrived from the east, with 13,12 and the two
switches measured at one unit each so three units land at 14,11.

**What is already ruled out, by reading:**

- the refresh IS asked as a train moves - `Layout` fires its callbacks at each milestone,
  `DiagramMonitor.markDirty` sets the flag, the driver's tick calls `updateVisiblePoints` ->
  `refreshCoveredTrack`;
- the coalescing hole that would drop the last ask of a run is closed, in `workOutCoveredTrack`'s
  `finally`, with a comment describing that exact case;
- `repaintTheWashWhereItChanged` repaints the symmetric difference, so a set that became empty should
  have repainted all six squares.

**Two candidates, and one gesture tells them apart.** With the stale orange on screen, switch pages and
back (or resize):

- **it goes** - the covered SET was already correct and the fault is the repaint reaching those labels.
  The suspect is `DiagramTileRegistry` handing back labels a grid rebuild has replaced, which is the
  OB-109 / MT-273 area: a rebuild that does not hide itself also does not re-register.
- **it stays** - the model still claims the tail, so a Point still lists the locomotive as its
  occupant. The suspect is `Point.assign`, the RESERVATION door: it puts a locomotive on a point
  without sweeping it off anywhere else - *"reserving sets the locomotive exactly as arriving does"* -
  and `walkStandingTrains` asks only `getCurrentLocomotive()`, so it cannot tell a reservation from a
  standing train.

**ANSWERED 2026-09-21, and it was neither: the mark was not stale.** It was being RECOMPUTED, correctly,
from a reservation. A locked path reserves every point on it and `Point.reserve` does not sweep, so the
locomotive was the occupant of several Points at once and the tail walk ran from each - which is why
refreshing changed nothing: every refresh produced the same phantom. Fixed with OB-243: one tail per
train, anchored at the head of its run. Nothing here needs a repaint change.

### OB-243 - 2026-09-21 - the tail walk picks one of several same-side roads by list order

**Kind:** bug  
**Raised from:** MT-438, measured 2026-09-21  
**Filed:** 2026-09-21  

**Adam, MT-438, 2026-09-21:** *"coloring switch 99 and 100 is still an error regardless"* and *"why
is the tail from bottommainapre to bottommaina not orange.  it's almost as if you are shifting the
location of the train"*.

Both sentences have one cause, and it is measured.

**Four roads leave `BottomMainAPre (eastbound)` on side E, and they agree for five tiles:**

| road | first places |
|---|---|
| -> BottomCrossover (northbound) | 10,12 11,12 12,12 13,12 14,12 **14,11** 15,11 |
| -> BottomMainA (eastbound) | 10,12 11,12 12,12 13,12 14,12 **15,12** 16,12 |
| -> RampDown (northbound) | 10,12 11,12 12,12 13,12 14,12 **14,11** 15,11 |
| -> RampDown (northbound, reverse) | same |

Every edge that ARRIVES at that point comes in on side W (from Tunnel and the three Tunnel parks). So
with `arrivedFrom = E` no arriving edge matches, and `walkOneTail` falls through to *"The other copy is
still taken when there is no arriving one"* - which here is not a copy of anything, but one of four
different roads. It takes the FIRST in `getNeighborsAndIncoming` order: `-> BottomCrossover`.

Measured, three units, no road recorded: claimed `BottomMainAPre -> BottomCrossover`, painted
`10,12 11,12 12,12 13,12 14,12 14,11` - which is Adam's screenshot exactly.

**So both of his observations are the same fault.** The switches are coloured because the road picked
turns up at them; the road to BottomMainA is NOT coloured past the divergence because that is not the
road picked; and the picture describes a train that came from BottomCrossover rather than from
BottomMainA, which is what *"shifting the location of the train"* means.

**This supersedes what I wrote earlier.** I called that claim true-when-made. It was not: it was the
wrong road from the start, and the staleness (OB-242) is a second, separate fault on top of it.

**Two remedies, and the choice is Adam's because it is his over-claim/under-claim trade.**

- **(a) Claim what they agree on.** All four roads share the first five tiles, so the tail is CERTAIN
  there and unknowable beyond; claim the common prefix and stop at the divergence. This gives exactly
  the picture he expects - orange along row 12, nothing at 14,11 - and protects the track that really
  is covered. It needs a cap on how far a claim reaches into an edge, which the `Edge -> Locomotive`
  map has no room for today; `isPathClear` already narrows by places, so the reader side exists.
- **(b) Claim nothing when the side is ambiguous.** One line, no model change: where more than one
  distinct neighbour matches the side and the recorded road does not resolve it, stop - the same
  reasoning the fork rule already uses one hop later. Never paints a wrong road; gives up the five
  tiles that really are covered, which is the direction that lets another train onto occupied track.

**FIXED 2026-09-21, and neither (a) nor (b): Adam ruled the ambiguity away.** *"There is no ambiguity -
the tail is certain at departure and shouldn't change.  You also know which way the train went
(bottommainapre to bottommaina onwards) when it started running.  Just unlock the rest of the diagram
once the tail by length is far enough away from bottommainapre."*

So the question was never which of four roads to guess: a running train's road is known, because the run
is holding it. `walkStandingTrains` now walks ONE tail per locomotive, and for a running one it anchors
at the head - its last reported milestone - and follows the part of its path it has already driven,
spending the train's length back along that road and stopping. Which is his sentence, and the same
arithmetic `tailHasProvablyPassed` already uses to hand an edge back as the head pulls away.

**Why there were several tails at all.** A locked path RESERVES every point on it (`Point.reserve`,
which deliberately does not sweep, because that reservation is what holds a junction behind the train
against a second train reaching it another way). So during a run the locomotive is the occupant of
several Points at once, and the walk ran from every one of them - a tail at the destination it had not
reached, another at a square it left ten minutes ago - each choosing its road from whatever arrival side
that Point happened to carry. That is where the four-road guess got in.

Held by `core.testARunningTrainHasOneTail`: a real run through a junction, sensors thrown by hand,
asked while the path is locked. Three claims - the road it drove IS claimed, the other road into the
junction is NOT, the leg two back is NOT (a one-unit train cannot reach it, so a claim there can only
come from a second anchor), and the leg AHEAD is not claimed as a tail either, which was Adam's *"as if
you are shifting the location of the train"*. The mutation - walk every holder again, drop the head
anchor - reddens two of them.

Run green afterwards: the tail suite (`testATailFollowsTheRouteItCameIn`, `testATailRouteIsKept`,
`testATrainCoversTheTrackBehindIt`, `testTheTailCrossedQuestion`, `testACoveredSwitchClosesTheOtherRoad`,
`testAShortTrainDoesNotBlockTheWholeRun`), the room rules (`testABerthAndAPlatformJudgeAnOverhang
Differently`, `testAManualSendIsRefusedABerthTooShort`, `testAStationsSizeIsAnAllowance`,
`testHomeStaging`, `testReturnHomeKeepsClearOfTheTailsItLeaves`), the diagram marks
(`testBlockedTrackIsGreyWhileAutonomyRuns`, `testTheGreyAppearsAtIdleToo`, `testTheShadingIsRedrawnWhen
ATrainMoves`, `testTheTrainIsShownAsALine`, `testTheWashIsNoLongerThanTheTrain`,
`testTheGreyDoesNotRebuildTheDiagram`, `testTheShadingFollowsTheTrain`) and `testAutoLayout`.

**What holds it either way**: a fixture where two roads leave one square on the same side and diverge
after a known number of tiles. `single-switch` has the shape (Approach's two arms), so no new scenario
is needed.

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

### OB-270 - 2026-09-22 - loc facing

**Kind:** bug  
**Raised from:** MT-460 (Segment Length shows and writes what the whole run measures)  
**Filed:** 2026-09-22 12:27  
**Build:** commit bb183cad, build\classes, compiled 22 Sep 12:01 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

I am able to paste 75 407 DB on BttomMainA facing west.


**Checked against your railway, 2026-09-22.** BottomMainA is emitted as two copies and the paste menu
offers the facing of each: `{BottomMainA (westbound) = W, BottomMainA (eastbound) = E}`. So West is
not being invented - there is a westbound copy with track leaving it, to TunnelLongPark and to
BottomMainAPre (westbound).

**But only the eastbound copy is a station.** `BottomMainA (eastbound)` answers true to
`isDestination`; the westbound one answers false. So a train pasted facing west is standing on the
copy of the square that no route can END at.

**Which makes this one of two different defects, and only you can say which.**

(a) **The menu is wrong.** The westbound copy exists as track but is not somewhere a train is meant to
be put, so the paste should not offer that facing - the control offering what the guard will not
honour, which is the OB-057 / OB-090 shape this codebase has paid for repeatedly.

(b) **The geometry is wrong.** If no train can physically stand at BottomMainA facing west, the
westbound copy should not be there at all, and what needs fixing is the diagram or the reduction that
built it.

Say which and it is a small change either way. Guessing is not safe here: (a) takes a placement away
from you that may be legitimate, and (b) removes a road.


**Adam's ruling, 2026-09-22:** *"we had a test for this before.  Trains should not inadvertently
change direction when pasted, so a loc going west from bottomsecondary should always face east when
pasted on bottommaina."*

**So it is reading (a): the walk is right, and something else let West in.**  Measured on the
snapshot, twenty times over because this used to be a coin toss: `facingByPath` answers **E, twenty
times out of twenty**, for a train standing at BottomSecondary being put on BottomMainA.  That is the
answer he wants, and `core.testAPasteDoesNotTurnTheTrainRound` is the test he remembers - it pins
exactly this walk, on this railway, against the shuffle that used to make it a coin toss.

**What is still open is which door gave him West.**  The paste takes the walk's answer; the facing
submenu and the *"... Is Facing"* menu offer both copies, because `facingsFor` reports both and
BottomMainA genuinely has a westbound copy with track leaving it - to TunnelLongPark and to
BottomMainAPre (westbound).  Only the eastbound copy is a destination.

**Next step:** find the door he used and make it take the walk's answer, rather than offering a
heading that turns the train round without his having asked for it.  His rule settles what the answer
must be; what is left is which control is not asking for it.


**Second attempt, 2026-09-22, and nothing shipped - the claim would not fail.**

The candidate was a real seam and it is worth recording even though it is not his defect.  MT-394
fixed the CHOSEN heading reaching the setup but not the railway - the train stayed on whichever copy
`getAutonomyPointForTile` had picked, and the railway records direction by which copy a train stands
on.  That fix is written `if (facingChosenAtTheLanding != null)`, so it covers the square that ASKS
and leaves the ordinary square - which asks nothing, because it is not may-reverse - taking an
arbitrary copy while the setup records the walk's answer.  BottomMainA is such a square.

So the copy choice was made to follow the walk, and a claim was written for it: stand the train at
BottomSecondary, paste onto BottomMainA, assert it lands on the eastbound copy.

**It passed with the fix and passed without it.**  On this snapshot the arbitrary copy of BottomMainA
IS the eastbound one, so the paste already lands facing east and the claim cannot fail.  A test that
cannot fail is no evidence, and a fix shipped behind one is a fix nobody has checked - so both were
reverted.

**What that tells us about his report.**  The paste door is not where West came from, at least not on
this geometry: the walk answers E, the setup records E, and the copy taken happens to be E as well.
The doors still to look at are the ones that set a facing on a train ALREADY placed -
`buildAutonomyFacingMenu` and the *"... Is Facing"* menu, which offer both copies because
`facingsFor` reports both - and the placement menu, which picks a copy by name.

**What would settle it in one line from him:** which gesture he used.  Ctrl+V over the square, a drag,
the right-click placement menu, or the facing menu afterwards.  Each is a different door and only one
of them needs changing.

**Left as it is meanwhile**, because the iteration-order copy choice is a real fragility - it is the
`SPEC-A1` shape, an arbitrary copy standing in for an answer - but changing it without a claim that
can fail would be putting an unchecked change into the door that decides which way trains face.


**The door is confirmed, 2026-09-22.**  Adam: *"Control V over the square in the viewer."*  The popup
diagram forwards its keys to the main window - `LayoutPopupUI.formKeyPressed` calls
`childWindowKeyEvent`, which calls `LocControlPanelKeyPressed` - so Control+V in the viewer is the
same `locomotiveGestureOnDiagram` door, keyed off the hovered tile.  There is no second paste
implementation to look for.

**And the copy a paste takes disagrees with the walk on TEN of the seventeen ordinary two-copy squares
of his railway**, measured on an empty snapshot: `BottomMainAPre`, `BottomMainBCPre`, `TopMainR1Inter`,
`LowerFrontPre`, `TunnelPre`, `BottomCrossover`, `LowerDownPre`, `TopMainR2Inter`, `TopMainR1`,
`TopMainR2`.  `StationIndex.speakerAt` prefers a copy trains may STOP at - a sound rule for the
question it was written for, and one that says nothing about which way a train points.

**BottomMainA is NOT one of the ten**, which is why his own square would not reproduce: only its
eastbound copy is a destination, so `speakerAt` lands on it and the paste agrees with the walk there.

**What the third attempt found, and it is sharper than where this started.**  Making the copy follow
the walk does not work on most of those ten, because `copyFacing` accepts only a copy that
`isDestination` - `moveLocomotive` refuses any other - and on `TopMainR1` the southbound copy the walk
names is not one.  So the walked heading cannot be PLACED there at all.

**But it is still RECORDED.**  `facingAfterAPaste` keeps the walk's heading whenever
`held.containsValue(keep)`, and `held` is `facingsFor` - every copy of the square, including copies a
train may not stand on.  So the two sides of one paste ask different questions:

- the RECORD asks `facingsFor`: does any copy face this way?
- the PLACEMENT asks `isDestination`: may a train be put on that copy?

Where those disagree the setup records a heading the train is not standing on - and a diagram drawn
from the recorded facing then shows it pointing a way the railway does not have it.  That is a
one-line agreement rather than a redesign: `facingAfterAPaste` should be given the headings a train
may be PLACED on, not every heading the square builds.

**Three attempts, each refuted by its own evidence, and nothing shipped.**  The first was a claim that
could not fail (BottomMainA, where the two already agree).  The second and third each assumed the
walked heading was placeable.  Recorded rather than fixed because the next step changes what a paste
RECORDS, and the fourth thing to change about this is worth getting right - `facingAfterAPaste`'s own
javadoc says three attempts preceded it.

**What would confirm it in one gesture:** paste onto `TopMainR1` or `TopMainR2` in the viewer and say
whether the arrow disagrees with the way the train actually drives out.


**REPRODUCED, 2026-09-22, on his railway as it stands - and the cause is settled.**

His sequence: *"for BottomMainA if I face 75 407 DB east with tail to the west, and paste to
BottomSecondary and then back to BottomMainA, it now faces west.  When pasted on topMainR1 or R2, it
correctly faces north."*  Measured by pointing a sandbox at `cs2_sample_layout` - which COPIES, so his
folder was only read, and it was verified byte-identical before and after:

- leg 1, out to BottomSecondary: the walk says W, the setup records W, and BottomSecondary has one copy
  facing W.  All correct.
- leg 2, back to BottomMainA: the walk says **E**, `facingAfterAPaste` records **E**, and
  `StationIndex.speakerAt` lands the train on **`BottomMainA (westbound)`**.

**Why the snapshot said otherwise for three attempts.**  `speakerAt` prefers a copy trains may STOP at.
On `live-snapshot` only the eastbound copy of BottomMainA is a destination, so it lands there and the
paste agrees with the walk by accident.  On his railway TODAY **both** copies are destinations, so the
preference no longer discriminates and it takes the first - the westbound one.  The fixture had lost the
shape being tested and reported clean about it.

**And it explains his other two symptoms in one go.**  The arrival side and the tail are worked out for
the copy the train LANDED on, so landing westbound is why there was no prompt about the tail and why the
tail was not from the west.  One cause, three symptoms.

**TopMainR1 and R2 are right for the same reason turned round:** one placeable copy each, so there is
nothing for `speakerAt` to get wrong.

**The fix is one line** - the copy choice takes the chosen heading where there is one and the WALKED
heading otherwise, instead of only the chosen one.  `copyFacing` already refuses a copy a train may not
be placed on, so it is safe where the walked heading is not placeable.

**What is blocking it is a fixture, not the fix.**  A claim needs a railway with two PLACEABLE copies
facing different ways; `live-snapshot` has none, and his current geometry has two (`BottomMainA` and
`LowerParkingOuter`).  A frozen copy of it is committed as `test/layouts/two-placeable-copies` - but a
window stood up against it HANGS before any claim runs, after the version check, which is the shape of
a modal dialog on the event thread.  The same class against `live-snapshot` runs and fails its own
precondition correctly (*"has 1 placeable copies"*), so the class is sound and the fixture is not yet
usable.

**Next step, and it is bounded:** find what that fixture prompts about - or build a small hand-made
layout with two placeable copies, the way `test/layouts/single-switch` was made for its own rule.  Then
the fix and its claim go in together.

**Nothing shipped.**  Four attempts: one claim that could not fail, two that assumed the walked heading
was placeable, and this one blocked on the fixture.  The diagnosis is now certain; the fix is not going
in behind a claim that cannot run.

### OB-271 - 2026-09-22 - focusability in the route editor

**Kind:** bug  
**Raised from:** MT-446 (After an edit declined at the start of a run, where the trains are is saved again)  
**Filed:** 2026-09-22 12:36  
**Build:** commit bb183cad, build\classes, compiled 22 Sep 12:01 - java: C:\Program Files\Java\jdk1.8.0_361\bin\java.exe

make the test, capture, etc. buttons in the route editor non focusable.

### FR-094 - 2026-09-22 - a bulk tool for locomotive train lengths, beside the one for track

**Kind:** feature request  
**Raised from:** MT-470 (Atomic Routes cannot be switched off while autonomy could release track)  
**Filed:** 2026-09-22  

Adam, 2026-09-22, on MT-470: *"sounds like we need a bulk tool for locomotive lengths."*

**Why it came up there.** Atomic Routes cannot be switched off while any locomotive autonomy runs has
no train length - that is the second half of the gate (VD14-B1), because a train with no length is
treated as clear of the track the moment its head passes. The notice names the locomotives, and then
there is nowhere to go: Bulk Tools walks the TRACK (Mass Assign Lengths, FR-089) and the STATIONS
(Mass Assign Max Train Lengths, FR-091), and a locomotive's own length is set one at a time in the
locomotive dialog.

**What it would be.** The same walk the other two use - the locomotives autonomy would run that have
no length, one prompt each, in a stable order, with the prompt opening where the last one was left.
Skipping one leaves it unset rather than setting it to zero, because zero is the value that makes the
gate fire.

**Where it belongs:** Bulk Tools, under the two that are there.  With this built, the atomic-routes
notice has somewhere to send you, which is what its shortened wording now promises.

**ASKED FOR AGAIN, 2026-09-23, AND IT REPLACES FR-095.**  Adam: *"Rather than adding complexity through
new menus, add a bulk tool to the autonomy editor to set missing train lengths, similar to how the
station lengths are set."*  So the way to a locomotive's length from the autonomy side is this walk, not a
right-click menu on the commands page, and FR-095 is withdrawn in its favour.

**What "similar to how the station lengths are set" fixes, taken from FR-091 rather than re-invented:**

- **Bulk Tools, as its own item**, beside Mass Assign Lengths and Mass Assign Max Train Lengths.  Greyed
  when there is nothing to ask about, with a tooltip saying how many there are when there is - the
  station walk's own affordance.
- **One prompt per locomotive**, the shared walk prompt: the number field has the keyboard focus, Enter
  submits, Skip leaves the locomotive as it was, Cancel or Escape stops, and the prompt opens where the
  last one was left.
- **0 is refused**, as the station walk refuses it: 0 is what a train with no length already holds, and
  it is the value that makes the atomic-routes gate fire.  A skip leaves it unset, not 0.

**Which locomotives: the ones the gate names, asked through the same method.**  The atomic-routes notice
lists `Layout.trainsWithNoLength()` - every locomotive on the run list with no length, alphabetically -
so the walk asks exactly that list and in that order.  One method for both is what stops the notice
naming a train the walk never offers, which is the guard-and-affordance rule (OB-057 / OB-090).

**Two things that differ from the station walk, because a train is not a square:**

- **Where it is written.**  A train length belongs to the locomotive, in the locomotive database, not to
  the autonomy setup - so the editor's Cancel, which restores the setup as it opened (OB-223), does not
  take it back.  The walk says so before it starts, the way the bulk clear says what Cancel does
  (OB-194), rather than leaving it to be discovered.
- **What it points at.**  The station walk flashes the square it is asking about.  A train standing on
  the diagram can be shown the same way - its square flashed while it is asked about - and one that is
  not placed has nowhere to show, so the prompt names it and nothing flashes.

**Not built.**  Filing is not asking for it to be worked; this is ready to build when he says so.

### OB-272 - 2026-09-23 - text labels follow the autonomy editor's caption setting instead of having one of their own

**Kind:** bug  
**Raised from:** Adam, 2026-09-23  
**Filed:** 2026-09-23  

Adam, 2026-09-23: *"when the text labels setting is anything but None in the autonomy editor, text
labels in the layout are also shown.  make text labels be a dedicated setting, and hide the text labels
unless it is selected.  Also, make control+L cycle the options."*

**It is wired that way on purpose today, and this reverses that decision.**  `AutonomyEditorPanel`'s
caption dropdown has four options - Stations, Parked Locs, Homes, None - and `applyCaptionMode` ends:

    if (mode == CAPTIONS_NONE) turnTextLabelsOff();
    else turnTextLabelsOn();

So anything but None turns the track diagram's TEXT tiles on.  That is FR-061's doing, on the reading
that *"None IS that switch turned off"*, and the Text Labels checkbox is then HIDDEN while a setup is
open (`LayoutEditor:1614`, `showTextCheckbox.setVisible(session == null)`) because the dropdown is
supposed to be the one control.  Worth naming because this entry undoes it - his call, and his to
reverse, but a future reader should not have to rediscover which decision was changed.

**What is being asked for, in three parts.**

1. **Text labels get a setting of their own**, not a consequence of which captions are shown.  The
   checkbox exists and is merely hidden in autonomy mode, so the smallest version of this is to stop
   hiding it and stop `applyCaptionMode` touching it.
2. **They are off unless that setting says otherwise** - so choosing Stations, Parked Locs or Homes
   shows those captions and nothing else.
3. **Control+L cycles the options.**  Today it is a flip: `LayoutEditor:7190` calls `toggleText()`, and
   `AutonomyEditorPanel.textLabelsChanged` exists only to keep the dropdown in step when it does.

**One thing to settle before building it (ONE LINE FROM HIM).**  *"cycle the options"* - which options?

- the CAPTION dropdown's four (Stations, Parked Locs, Homes, None), which is what "the options" most
  naturally refers to in the sentence before it; or
- the text-labels setting's own, which after part 1 is on and off - and cycling two states is what
  Control+L already does.

The first reading gives the key a new job and leaves text labels to the mouse; the second keeps its job
and makes the rename cosmetic.  Guessing costs a shortcut he has to unlearn either way.

**What falls out once it is decoupled**, and it is worth doing in the same pass rather than leaving
machinery that no longer has a reason: `lastNamedCaptionMode` and `textLabelsChanged` exist only
because the two settings were one.  `RGD-C3` is the defect that pairing produced - Control+L with
Parked Locs selected made every caption vanish under a control still saying Parked - and it goes away
with the coupling rather than needing its own fix.

**Minor**, by his own word, and it is display only: nothing here reaches the railway.

### OB-273 - 2026-09-23 - Mass Assign Lengths puts a share on a tile that cannot show it, so a stretch reads shorter than it measures

**Kind:** bug  
**Raised from:** Adam, 2026-09-23  
**Filed:** 2026-09-23  

Adam, 2026-09-23: *"in mass assign lengths, if part of the stretch is a route, the length isn't shown on
it.  for example 3 regular tiles, 1 route, length 4 = 3 tiles have length 1.  i'd prefer one to have
length 2, and two others length 1, or just 1 of length 4."*

**What the code does.**  `AutonomySession.assignStretchLength` shares the answer evenly over EVERY tile
in the stretch, with the remainder going one unit at a time to the front of the order - stations and
turn-arounds first, which is MAL-B2's rule:

    int each = wholeLength / order.size();
    int over  = wholeLength % order.size();

and `AutonomyCompanionStore.setTileLength` has **no type guard at all** - it writes the length onto
whatever tile it is handed, a route tile included.  So his four-tile stretch really is given 1, 1, 1, 1
and the fourth unit lands on the route tile, where nothing draws it.

**So the share is not lost - it is INVISIBLE, and that is the worse of the two.**  `measuredIn` sums
`getTileLength` over the stretch's tiles, so the stretch reads as measured 4 while the diagram shows 3.
A railway he cannot verify by looking at it is the state every one of these length tools exists to get
him out of.

**THE THING TO ESTABLISH BEFORE BUILDING IT**, because it decides whether this is cosmetic or an
under-measurement: do the room and tail walks COUNT a route tile's length?

- If they do, the measurement is correct and only the display is wrong - but the display is what he
  works from, so it still has to change.
- If they skip route tiles, a unit is genuinely lost and the stretch under-measures, which is the
  refusing direction and therefore safe, but wrong.

Either way his preference settles the design, and it is the right one: **do not put a share on a tile
that cannot show it.**  Excluding such tiles from `order` shrinks the divisor and the arithmetic still
sums to the answer he gave - his 2, 1, 1.  Putting the whole length on one tile is the other option he
offered and is simpler, but it loses the "each square carries its share" property the rest of section
5a's arithmetic reads.

**What is not yet known** is which types cannot hold or show one.  A route tile (`fahrstrasse`) is the
case he hit; the same question applies to text, labels and anything else drawn on the grid that is not
track.  That list belongs in ONE place and asked once, the way `GraphReducer.boundsTheRoom` was made
one question for OB-233 - two lists that must agree is how that defect happened.

**Minor**, by his own word, and it never admits a train: an under-measured stretch refuses more.

Related: MT-454 and MT-459 are the hands-on tests for this walk, and both are in his retest queue.


**HIS RULING, 2026-09-23, and it settles the open question above:** *"a route tile should not need or
accept a length.  it just implicitly connects things as if it were a crossing."*

So the answer is not to share the length differently - it is that a route tile is **not a square a
length rule reads at all**.  It neither takes a share nor is asked for one, and it connects what is
either side of it the way a crossing does.

**That settles three things at once.**

- This entry: exclude route tiles from the stretch's squares, and the divisor shrinks so his 2, 1, 1
  falls out on its own.  No tile is given a share it cannot show, because no such tile is in the list.
- `OB-274`'s adjacent case is smaller than it looked: a piece that is genuinely zero because the only
  thing between two sensors is a route tile stops needing a deliberate 0 at all.  The rest of OB-274
  stands - two sensors truly adjacent still need one.
- The `checkHalfMeasuredApproach` warnings on `TopR1ParkLong` and `TopR1ParkShort`: the unmeasured
  squares the check counts on those approaches ARE route tiles - `5:3,5` for the first, `5:3,7` and
  `5:4,7` for the second - so both warnings go once route tiles are out of the walk, with nothing
  measured and nothing changed about the berths.

**And a correction he made to my reading of his diagram**, recorded because it is about his railway
rather than about the code: *"there is no path from 2,5 to 4,5 through 3,5, so TopR1ParkShort should
still allow trains of length 3 because 2,5's length is 3."*  I had inferred the approach ran east-west
through the route tile from the tile map; it does not.  The berths are measured - `5:2,5` is 3 and
`5:2,7` is 4 - and a berth must admit a train of its own measured length.

**Which makes the list of types one question asked once**, as this entry already argued: route tiles
are the first entry on it, and text and labels are the obvious next ones to settle.

**AND THE SHARE ITSELF IS CONFIRMED EVEN, 2026-09-23** (`OB-275`, answered the same day): *"let's stick
to a then."*  So this entry's fix is the divisor and nothing else - route tiles leave the list of
squares a stretch is shared over, and the share stays even.

Which lands exactly on the answer he asked for.  His four-tile example is three plain squares and one
route tile with 4 typed: the route tile is no longer in the list, 4 over 3 squares is 2, 1, 1 by the
existing even-share rule with the remainder going first to a square a train stands on - and *"one to
have length 2, and two others length 1"* is what he said he preferred.  No new sharing code; one
exclusion.

### OB-274 - 2026-09-23 - Mass Assign Lengths cannot accept a deliberate length of 0, so a genuinely zero piece reads as skipped

**Kind:** bug  
**Raised from:** Adam, 2026-09-23  
**Filed:** 2026-09-23  

Adam, 2026-09-23: *"in the mass assign lengths, we need to allow a length of 0 as a length that is set
deliberately, i.e. for adjacent tracks.  same meaning to the model, but this will allow everything to
get assigned without what appears to be a skip."*

**Today 0 and "not set" are the same value, and that is why it looks like a skip.**
`AutonomyCompanionStore.setTileLength` reads:

    if (length <= 0) tileLengths.remove(tile);

so writing 0 ERASES the entry, `getTileLength` answers 0 for an absent one, and every "still needs a
length" query asks `<= 0` - seven of them.  `assignStretchLength` refuses `wholeLength < 1` outright and
the prompt says so: *"0 is the same as no length at all.  Enter at least 1."*  So a piece whose real
length IS zero - two sensors with no track between them, his adjacent tracks - cannot be answered.  It
stays in the unmeasured list for ever, and the walk keeps offering it.

**What he is asking for is a THIRD state, and the model already half has one.**  `behaviour.md` 5b's
rule is *"Unmeasured is unknown, not zero"* - which is exactly right and is the reason 0 cannot simply
be treated as measured today.  A deliberate 0 is neither: it is **known to be zero**.  So:

- absent  -> unknown, ask for it;
- 0       -> known, and contributes nothing;
- above 0 -> known, and contributes that.

**And it makes the railway MORE measured, not less.**  A piece that is genuinely 0 currently reads as
unknown, so the room rule, the tail walk and the atomic-routes gate all treat it as indeterminate and
refuse things they need not.  Answering it truthfully lets them proceed - which is the opposite of the
refusing direction, so the change wants care rather than being waved through.

**What it touches.**  The store's write (stop erasing on 0), the JSON round trip (a 0 must survive a
save and a load, or the answer is lost on restart), the seven `<= 0` readers - each has to be read for
whether it means "unknown" or "contributes nothing", and they are not all the same - and the prompt's
refusal, which becomes a legal answer.  `behaviour.md` 5a's sentence *"the least a piece can be given is
1"* is his own earlier ruling and is what this reverses; it should say so rather than be quietly
rewritten.

**Also affects the switch and crossing steps**, which refuse `length < 1` the same way - and two
switches back to back are the same case as his adjacent tracks.

Related: `OB-273`, which is the other half of how a piece's length is shared out.  MT-454 and MT-459 are
the hands-on tests for this walk.

### OB-275 - 2026-09-23 - were lengths meant to be consolidated onto tiles with arrows? the record says an even share

**Kind:** bug  
**Raised from:** Adam, 2026-09-23  
**Filed:** 2026-09-23  

Adam, 2026-09-23: *"I thought we were consolidating lengths on tiles with arrows?"*

**I can find no such ruling in the record, and the decision that IS recorded is the opposite.**  Asked
and answered rather than assumed:

- `FR-089`, his own instruction of 2026-09-16: *"per stretch, every relevant square a rule reads"*, and
  its entry says *"One whole length is typed and shared evenly over the squares that have none."*
- Revised the same day after review MAL to *"Every leg, cut at switches"* and *"One length for all
  switches"*, with `behaviour.md` 5a carrying the share rule: the share is even, and any unit left over
  goes first to a square a train stands on, *"whose length its own tail never spends, which is the
  refusing direction"* (MAL-B2).
- `assignStretchLength` implements exactly that - an even share with the remainder to stations and
  turn-round squares first.

Nothing in `issues.md`, `tests.md` or `behaviour.md` mentions consolidating a piece's length onto a tile
because it carries an arrow.  So this is either a decision taken in conversation and never written
down - which is worth capturing here either way - or a memory of the arrow work of 2026-09-22, where
directional arrows and lengths were discussed together on permanent turnouts (`behaviour.md` 5e) but
lengths were not part of what changed.

**IT MAY BE THE SAME REQUEST AS `OB-273` ARRIVING TWICE, and if so one answer settles both.**  There he
said of a four-tile stretch: *"i'd prefer one to have length 2, and two others length 1, **or just 1 of
length 4**."*  That second option IS consolidation - the whole of a piece's length on one square - and
the tile carrying the arrow is a defensible choice of which square, because the arrow marks the
direction the piece is read in.

**What would settle it in one line from him:** is the wanted behaviour (a) an even share, as built; (b)
the whole length on one square, chosen as the one with an arrow; or (c) the whole length on one square
chosen some other way?  Each is a small change to one method, and each reads differently to the room and
tail rules - a consolidated length sits on ONE square, so a train resting anywhere else on the piece
spends nothing there, which is the direction that admits rather than refuses.  That last point is why
this needs his word rather than a guess.


**HIS RULING, 2026-09-23 - the even share stays:** *"let's stick to a then, since that is more visually
pleasing."*

So the record was right and nothing changes in `assignStretchLength`.  The question is answered rather
than fixed, and the reason he gives is a reason this entry did not consider: a stretch whose squares
each read 1 LOOKS like a measured stretch, and one square reading 5 beside four reading nothing looks
like a stretch somebody gave up on.  The length display is read far more often than the length rules
are.

**And his reason happens to pick the option that was already the safe one**, which is worth recording
because it will not always work out that way:

- Every rule that ADDS a stretch up is indifferent - `roomAfterTheLastSwitch`, `sumLength`, the
  editor's own readouts.  Same total either way.
- The rule that reads squares ONE AT A TIME is not indifferent, and that is the tail.  The protrusion
  walk spends length square by square backwards from where the train stands and stops the moment it has
  spent the train's length (`AutonomySession:6121`).  Under consolidation the answer depends on where
  the train happened to stop: on the square holding the whole length it spends it all at once and the
  tail is drawn over ONE square - under-reporting, so track behind the train reads free - and on one of
  the zeros it spends nothing and the tail is drawn over the WHOLE stretch.  Even share draws the tail
  where the train is, wherever it stopped.
- Consolidation also could not be built before `OB-274`: four squares of five would store 0, 0 means
  unmeasured today, and they would come straight back onto the "still needs a length" list - which is
  the phantom skip he filed OB-274 about.

**Closed as answered, no code change, no MT.**  `behaviour.md` 5a already carries the rule and needed no
edit.  The one thing this entry leaves behind is the observation above about which readers are
indifferent and which are not, because the next request to move lengths around will need it.

### OB-276 - 2026-09-23 - the tail question lists one square twice when the road back to it is a single hop

**Kind:** bug  
**Raised from:** Adam, 2026-09-23  
**Filed:** 2026-09-23  

Adam, 2026-09-23: *"when pasting 75 407 DB on bottomsecondary, the tail question lists rampdown twice in
the list."*

**Two entries, one name - and they are almost certainly two COPIES of RampDown.**  The walk keys its
first hops by `roadKeyOf(candidate.getStart())` and marks squares walked by PLACE, so it does not visit
one square twice; what it can do is reach two different copies of RampDown by two different roads back,
and both are shown by whatever `shown` maps their Point name to, which is the square's own name.  His
railway builds `RampDown (northbound)` and `RampDown (northbound, reverse)`, so there are two to reach.

**The disambiguation already exists, and a guard switches it off in exactly his case.**
`TailCrossedPrompt.label` counts the shown names and, where one occurs more than once, relabels it
*"<name> via <next sensor>"* (TLR-C3), then falls back to naming every sensor on the way where that is
still ambiguous (TLV-B3).  Both passes are guarded by:

    choice.getRoad().size() > 1

BottomSecondary is one hop from RampDown, so each road back is a SINGLE edge, the guard is false for
both choices, and neither gets a via - leaving two entries reading `RampDown`.

The guard is not careless: with a one-edge road there is no *"next sensor towards the train"* to name,
so `viaNames` would have nothing to say.  The machinery simply has no material for an adjacent square.

**So the fix needs a discriminator that works when the road is one hop**, and the honest options are:

- the SIDE the road comes in by, which `entrySideOf` already answers and which the arrival-side prompt
  already shows the operator elsewhere;
- the copy's own facing, which is what makes the two answers different in the first place;
- or the square itself as the via - *"RampDown via RampDown"* is useless, so this is the one to reject.

**IT MUST NOT BE DEDUPED.**  The two choices describe DIFFERENT roads - that is why there are two - so
dropping one silently discards a real answer about where the train's tail is, and the tail is what
blocks track behind it.  The exception is two choices whose roads are literally the same edges, which
would be a genuine duplicate and should be collapsed by ROAD rather than by name.  Which of those his
two are is the first thing to measure.

**Worth checking at the same time:** whether the same guard hides a duplicate anywhere else.  Two
squares adjacent to a junction is not a rare shape, and `label` is the only place that disambiguates.

Related: `FR-088` is the question itself, `MT-435` and `MT-438` are its hands-on tests, and section 5c
of `behaviour.md` carries the rule about which sensors are offered.

**His aside** - *"this is why my new, measured layout"* - reads as another argument for finalising and
blessing the layout, which he raised the same day.  Recorded here because it is the third defect this
week whose cause is one square being several Points.

### FR-095 - 2026-09-23 - a right-click menu on the train name in the autonomy commands page, with Edit Locomotive

**Kind:** feature request  
**Raised from:** Adam, 2026-09-23  
**Filed:** 2026-09-23  

Adam, 2026-09-23: *"when right clicking the train name in the autonomous locomotive commands page, show a
right click menu with the edit locomotive option, when allowed (maintain consistency with when autonomy
is running, as the others)."*

**Where.**  `AutoLocomotiveStatus` - one panel per locomotive on the autonomy Locomotive Commands tab.
The train's name is the `locName` label, and it already carries a `MouseAdapter`: `locNameMouseClicked`
makes that locomotive the active one.  So the plumbing is there and a popup trigger goes beside it
rather than replacing it - a left click must go on meaning what it means today.

**The gate already exists as ONE method and this must ask it rather than spell it again.**  Every other
edit door asks the same two:

- `TrainControlUI.refuseWhileAutonomyRunning(source)` - *"Cannot edit locomotives while autonomy is
  running."*
- `refuseWhileARouteDrivesIt(source, name)` (CS3-B1) - because a route running by hand rewrites what a
  locomotive edit is changing, with autonomy idle.

`changeLocAddress` and `deleteLoc` both ask both.  A third spelling of either is how they come apart.

**"When allowed" is the OB-057 / OB-090 rule, and it needs BOTH halves.**  His *"maintain consistency
with when autonomy is running, as the others"* is the rule this repository has paid for repeatedly: the
control that OFFERS an action asks the question the guard asks.  So the item is greyed when the guard
would refuse - not offered and then refused, which is the shape the others were fixed away from.

And the greying alone is not enough, for the reason `testHomeStaging`'s own comment gives about the
menu items it covers: **the item is greyed when the popup OPENS and the action fires when it is
CLICKED**, and autonomy can be started from another window in between.  So the guard is still asked on
the click.  Greyed at open, guarded on click; either alone is a hole.

**Parenting, because this panel is the one that gets it right.**  `IND9X-C4` and `IND9X-C5` are about
dialogs opening unowned - the right-click menu on the diagram parents on an already-dismissed popup,
while `AutoLocomotiveStatus` parents its dialogs on itself correctly.  The new menu keeps that: hang
the dialog off the panel or the label, not off a popup that is gone by the time the action runs.

**Scope, as he wrote it: the edit option, singular.**  `loc.ui.dialogEditLocomotiveInfo` is what it
opens.  Its natural siblings - change address, edit functions, delete - are behind the same two gates
and would be one line each afterwards, but he asked for one item and one item is what this is.  Worth
asking whether he wants the others while the menu is being built.

**Not a defect**: nothing is wrong today, there is simply no way to reach the edit from that page.
Minor, and display-side only.

**WITHDRAWN IN FAVOUR OF FR-094, 2026-09-23.**  Adam: *"Rather than adding complexity through new
menus, add a bulk tool to the autonomy editor to set missing train lengths, similar to how the station
lengths are set."*  The right-click menu described above is not to be built; what it was a way to
reach, a locomotive's train length, is reached through the Bulk Tools walk FR-094 describes.

### OB-277 - 2026-09-23 - the orange train line is not drawn on sensor squares

**Kind:** bug  
**Raised from:** Adam, 2026-09-23  
**Filed:** 2026-09-23  

Adam, 2026-09-23: *"when we draw orange lines, they don't overlap with sensors."*

**A likely cause, and it is the same root as `OB-263` filed this morning.**  `LayoutLabel.coveredRoads`
decides the line's shape by asking

    TilePorts.ports(component.getType(), component.getOrientation(), road.getState())

and `TilePorts.ports` answers `Collections.emptyList()` whenever the state is past the end of that
type's table - it does not throw.  A FEEDBACK tile has exactly ONE state, so any state of 1 returns
nothing, `roads.isEmpty()` is true, and the method returns before a line is drawn.

**Which would explain the symptom exactly, and in the worst place.**  A sensor with a train on it is
the one that reads occupied - so the square where the train actually is would be the square with no
line, and the orange would break at every sensor it covers.

**Why this is worth taking seriously rather than filing as cosmetic:** `IND9X-C8` / `OB-263` is the
same trap one layer away - `LayoutGrid.runsNorthSouth` asks the port table with a feedback tile's saved
`zustand` and gets an empty answer, which is why a station caption's rotation depends on whether the
s88 happened to be occupied when the layout was exported.  `AutonomySession.labelSides` documents the
trap and asks `graph.getRoutes(tile)` instead.  That makes this the THIRD site of one confusion, and
the fix is the one already written down: ask the graph what roads a tile has, not the port table with a
state it cannot interpret.

**ONE MEASUREMENT WOULD CONFIRM IT** and it needs a quiet tree: put a train on a sensor square, read
`coveredRoads` for that square, and see whether the list is empty.  If it is, the mechanism above is
it; if it is not, the line is being drawn and something later hides it, which is a different fault.
The mechanism is read from the code rather than observed, and that distinction is kept here because
this repository has been caught by a plausible-but-unmeasured cause twice this week.

**What it costs him:** the orange is how he reads where a train's body is.  Broken at every sensor, it
under-reports the very squares a tail walk is most often asked about - and `behaviour.md` 5c's claim
that the picture and the guard mark the same squares stops being true at exactly those squares.

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
