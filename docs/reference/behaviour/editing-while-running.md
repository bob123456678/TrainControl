# Editing the setup while the railway runs

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 6a. Editing the setup while the railway is using it

**Escape lets go of what the editor is holding, and closes it when there is nothing left to let go
of** (FR-065). Adam: *"escape closes autonomy/track editor - same as closing via button, with warning
shown as needed."* With nothing armed, one press closes the window down its Cancel button's path, unsaved-work prompt
included. With something armed it drops that instead: an armed tool or a half-made two-click gesture in the autonomy
editor; the picked squares, the copied group, a tile or palette piece picked up, the picking mode and a box being drawn
(with the click its press would end in) in the track editor. A second press then closes it. Closing outright would take
a half-finished gesture through the prompt that asks whether to throw the edits away; pressing Escape twice costs
nothing.

It is handled in the editor frame's own key listener and nowhere else: every control in that window is unfocusable so
the frame keeps the keyboard, a `WHEN_IN_FOCUSED_WINDOW` binding never fires on a top-level frame, and a second handler
would disarm the tool first and leave the frame's branch closing the window: one press doing both.

**Nothing that changes the shape of the graph may run while trains are moving or a plan is being
made.** Renaming a point, deleting a point and deleting an edge all refuse while `isRunning()` or
`isStagingInProgress()`: a rename mutates a `Point` under live visited sets, and a delete removes the object those sets
and already-issued paths hold.

**Staging counts as running.** The Return Home planner walks these structures with nothing dispatched at all.

**A locomotive edit takes a train off the graph only through a train that stands and drives the edited one.**
Renaming, re-addressing, re-linking or deleting a locomotive is refused while autonomy runs (its coast-down and Return
Home's planning included), so the edit doors act on a stopped railway. Their sweep for multi-unit conflicts asks of the
edited train where it stands, and of every standing multi-unit that drives it (a consist linked here or one the Central
Station holds) what putting that multi-unit down again would ask. A train that neither stands nor is driven by one that
stands clashes with nothing. The Central Station sync asks the same of every locomotive it re-addresses or gives other
members, when autonomy is not running; members that change during a run are swept by the next load or Place (OB-303).
Renaming a member of a multi-unit whose head stood on a station must not take the head off it (a head is never
compatible with its own member), or the platform reads as empty with the train on it. Putting a train down still sweeps
first, before it is placed.

**The graying on the menu is not this guard.** Items are grayed when the popup *opens* and the action fires when it is
*clicked*; autonomy started from another window in between leaves a live item over a running railway. The refusal has to be
in the method, and the menu asks the same question so the two agree.

> *"Where a train IS is a fact, and where the file thinks it is is a record."* - Adam (OB-183)

**Every setup edit regenerates the running layout from the setup, and where the two disagree about a
train the railway wins.** A run moves trains, and nothing writes where they ended up back to the setup, so the setup is
stale about exactly those trains. **That applies to the recorded facing as well as to the placement**, which is why
nothing that runs when a run ends may work out an answer by *changing* what the setup records (§3). A recorded facing is
never cleared, so an empty square still carries the last occupant's: "there is a value here" is no evidence it is this
train's. Regenerating from the setup would put each train back where it set off, and the next dispatch could be routed
into a block that is physically occupied.

**The one exception is a train whose placement the edit was about**, and the doors that make such an edit say which
train it is. Placing a locomotive from the autonomy editor, taking one off a square and clearing every placement write
the new answer into the setup only, so for those trains the rebuild's placement stands. Every other door that moves a
train (the track diagram's own place, facing and assign items) writes the running layout too. Neither half is inferred
from a rebuild happening; the surface that reaches this most is the track diagram viewer's right-click autonomy menu,
which rebuilds after every gesture and never captures where the trains are.

**A direction is never refused for a standing train: what it does to the train is a finding** (Adam:
*"Generally I prefer warnings or errors over outright refusals, since it makes editing easier for the user.  Make
refusals that make sense be errors, and smaller issues be warnings."*). The copy a train stands on is its direction, so
a one-way run set through a station against the way a train there faces leaves the square no copy facing it. The change
is made, and the train is written into the setup where it stands, facing the way it faces (a train the last run left
there included, so the fold that follows keeps it). The build stands such a train on no Point, never on a copy facing
another way: capturing that copy's facing would turn the train in the setup and clear the error.

**A rebuild the setup has fallen behind never loses a train.** After a run the setup still names each moved train's old
square, and the diagram's own setup menu rebuilds without folding, so where the railway had a train is put back by
`putTheTrainsBack`:

- It stands the train on its own copy even where that copy is no station now (a station marked one trains only pass
  through, with the train on it): autonomy will not start it from there and says why.
- Where no copy of its square faces its way any more (its turning taken away), the train is taken off wherever the
  stale setup put it and recorded where it stands, so the error stops every send until it is put right.
- **An error is asked of the railway's train first**: a setup record whose train the railway has on another square is
  one the last run outran and raises nothing, nor does one on the same square, the railway's facing being the one
  asked.
- **Only over a railway the load replaced**: a load declined for a problem that stops the build (a link unpaired)
  leaves the railway and its trains as they were, and the setup is then marked newer than the railway, so no fold of the
  railway built before the edit (opening or closing the editor, Export, New Configuration, the exit) writes its homes,
  lengths and priorities back over the edit until a load carries it.
- **Those folds write what the railway owns**, at every door that folds (opening or closing the editor, Export, New
  Configuration, the exit, Unload, and the reset behind Open Layout, Combine Linked Pages, a page renamed or a switch of
  railway): where each train stands, and the settings and timetable the Auto tab and a run changed since the railway was
  built. A switch of railway writes them into the railway's own file, which it leaves, and carries nothing onto the
  next. After a page renamed or squares moved, the reset's fold leaves the timetable alone, as it leaves the placements:
  the railway still has the old Point names, and the rename's own fold has taken the run's timetable already. A page
  deleted is not folded back: the delete marks the session's pages stale, as a rename does.
- **A rebuild from the setup keeps the railway's settings and timetable**: the ones the railway changed since it was
  built are folded first, compared with the railway as built, so a setting a door edited in the setup is not written
  over; a setting the railway turned off (Simulate, the sequential flag, written only while on) is turned off in the
  setup, and atomic routes the load turned on itself for want of a length are not written as the operator's (MT-470). A
  train placed in the setup meanwhile goes where the railway has it at that load (OB-183).
- **A train recorded where it stands keeps the side it came in by and its road**, so the track put back stands it with
  its tail.
- **Readers of who stands where ask the railway first**: Home All Trains Where They Stand, from the track diagram's menu
  after a run, homes each train where the railway has it, and the no-length notice names the train that stands on its
  square.

`regression.testAnEditedPlacementSurvivesTheRebuild`:
`testATrainOnAStationMadePassThroughIsPutBackOnIt`, `testATrainWhoseTurnIsTakenAwayIsRecordedWhereItStands`,
`testARecordTheRunOutranRaisesNoError`, `testATrainBesideALinkSwitchedOffIsRecordedWhereItStands`.

The setup check then reports an **error**: the train faces a way its station no longer has, and nothing is sent until it
is turned from its right-click menu, moved, or the track is put back. A link switched off beside a train is the same.
**A train facing track that now runs only towards it**, judged at the train's own square (Adam: *"Only near
the train"*), is a **warning**: it can be started, and goes nowhere that way. **A station at the end of a line that
trains may not change direction at is an error** (Adam): a train sent there could never leave; marking it Trains Must
Change Direction Here clears it. A click on the track or a switch steps to the next direction.
`core.testTheEditorWarnsOfARailwayThatCannotWork.testADirectionUnderAPlacedTrainIsSetAndReportedAsAnError`,
`core.testTheEditorWarnsOfARailwayThatCannotWork.testATrainFacingTrackRunningTowardsItIsAWarning`,
`core.testTheEditorWarnsOfARailwayThatCannotWork.testAStationAtTheEndOfALineIsAnError`,
`regression.testAnEditedPlacementSurvivesTheRebuild.testATrainTheRunLeftIsRecordedWhereItStandsWhenItsWayGoes`.

**The Facing menu is about the train the RAILWAY has on the square.** After a run the setup can still name a square's
pre-run occupant while another train stands there. The menu names the train that is there, turns that one onto the copy
facing the chosen way, and writes the setup's facing only where the setup's train is the one standing there (or nothing
is). `regression.testTheFacingMenuIsAboutTheTrainThere`.
