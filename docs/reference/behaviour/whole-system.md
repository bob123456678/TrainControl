# Things true of the whole system

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 8. Things that are true of the whole system

**Pages are ordered by name, not by the number the Central Station orders them with.** Adam:
*"the `.id` field is what the Central Station uses to order the pages. We still order by
name, which is the simpler behavior."* `getLayoutList` sorts, and that sort is what the window and
every menu show.

An id is still an identity - the autonomy setup is keyed by it, which is why `writeLayoutIndex` keeps
each page's id rather than renumbering by position - but it is not a position. The same goes for the
`page=N` line at the top of a page file: it is preserved exactly as the station wrote it (X8-A1), and
if a reissued id later disagrees with it, neither number decides where the page appears.

**A page that states no `.id` has id 0.** Adam: *"no .id means id 0 implicitly."* That is
the ordinary CS2 convention - a key the station omits carries the zero value - and it is what the files
say: of the index files here, four have a first page with no `.id`, none states `.id=0`, and in each of
them the stated ids run consecutively from 1 or 2. On the genuine station export the pages are named `0 stationer` .. `7 autonom
annotated` against stated ids 1..7, so the absent one is 0 and the ids line up with the names.

Reading it as the page's POSITION instead would give two pages one id, which would key both of them
into one space in the autonomy setup (S14-A2).

**A PAGE LINK IS STORED AS A POSITION, and it is the only thing that is.** Adam: *"right
now, let's make artikel be the sorted index of the page - which i believe is already how it worked."*
So an arrow tile holds the destination's place in the name-sorted list, not its id.

Three things follow, and they are the behaviour rather than the implementation:

- **Every operation that changes the set of page names re-aims the arrows.** Adding, renaming,
  duplicating, deleting and combining all move the alphabet, and an arrow follows the page it pointed
  at. Otherwise it would silently come to mean another page and the new number would be written to the file -
  on the five pages of the sample layout, adding one page repoints four of seven arrows
  (N8-A1). **Combine is covered too**, though not obviously: its copy is written before the re-aim, but
  the page is then refilled square by square from the corrected sources and saved again, so what ends up
  on disk is right (T10-C2).
- **An arrow whose page is deleted points at nothing.** Adam: *"set the ID to -1. This shouldn't throw
  any errors, and simply resolve to nothing when clicked. Then, the user can set it to the right page
  on their next edit."* Clicking it does nothing and its tooltip says so.
- **An arrow may not point at the page it is drawn on.** Adam: *"make sure links can only go to other
  pages, not themselves."* The page being edited is not offered in the list.
- **An unpaired link is not marked on the diagram** (Adam: *"Marking links is not needed since we get error
  messages"*): the setup check's errors say where it is.

**A page whose file will not read keeps its place in the list.** It comes back blank with a text tile
at 1,1 saying it could not be loaded - the reason the page cannot simply be
dropped is the rule above it: the list is what every arrow indexes into, so a missing page would silently
re-aim every arrow after it, with nobody having edited anything (NSV-B3). An unhydrated cloud file is
enough to cause that. Such a page is never saved, because writing a blank page over the file somebody
is trying to recover is worse than not showing it.

**And its autonomy settings are kept, always.** Adam, asked whether the old "these pages
are absent - keep their settings, or were they deleted?" question should be pointed at stand-in pages
instead: *drop it.* Nothing is pruned automatically. A page whose file will not read today is a file
that is missing right now, which is not the same as a page that is gone, and the answer that cannot
lose anything is to keep. The question could not fire anyway once every page named in the index comes
back as something (T10-C3): there are no absences left for it to be about.
`core.testASetupIsBuiltSavedAndReopenedWhole.testAPageThatWouldNotReadIsNotJudged` is what holds this - a stand-in
must not let a save judge, and therefore prune, the settings of a page nobody can see.

The message the operator gets says the page is shown blank, that the page says so itself, and that it will not be
written over until the file reads again (T10-C4).

- **A square is several Points.** Anything reasoning about "the station" must say which copy it
  means, or it is asking a question the graph does not answer.
- **`canReverse` is not in the running layout.** Only the setup knows it. **It is a property of the
  TILE**, not of the train: *may a train turn round on this square.* `AutonomyBuilder` expresses it by
  splitting the square into a plain copy and a turning one and never emits the flag itself, so nothing
  in the automation layer can ask the question - which is why the manual doors read it from the setup.
  The train’s own ability to run in either direction is `Locomotive.isReversible`, a different fact
  asked in different places (§3).

- **A route tile carries no track of its own; it conducts what is beside it, like a fixed crossing**
  (OB-160). Adam: *"I am inclined to treat it as a static crossing under the hood"*, and
  *"it just implicitly connects things as if it were a crossing."* Track facing it from two
  sides is joined through it - a straight or a corner; from all four sides it is a crossing, two roads
  that do not meet. **Two situations are errors on the setup's findings list**, because the tile would
  otherwise conduct something nobody drew: track facing it from THREE sides, where one arm would be
  dropped in silence and there is no honest guess, and two route tiles side by side whose run reaches real
  track, which would splice two lines together across diagram with no rails on it. Route tiles with no
  track beside them are a control panel and conduct nothing. **And it takes no length** - 5b.
- **Facing is encoded as one-way edges.** There is no direction field on a train's route; the sparse
  and doubled edges *are* the direction.
- **A sensor stays occupied while a train is standing on it.**  Adam: *"the sensor
  will remain on while a train is standing there."*  The detection latches; it does not pulse as the train
  arrives and then clear.  Two things in the model follow from it and are therefore right to bite: `isPathClear`
  refuses an edge whose end reports a set sensor, and the staging planner refuses a point whose sensor sibling
  holds a train - and because one s88 address is shared by more than one Point here (a sensor is not a place
  key), both refuse the SIBLING square of a standing train as well.  That is the refusing direction and it is
  wanted.  **The simulation is the odd one out**: `Layout.simAnnounce` and `simClearBehind` pulse the feedback a
  simulated run reports, so the railway reads as clearing under a standing train there (AMR-D1, RTX-C4).

- **Signals and switches are the same device to the protocol, and different things on the railway.**
  They are commanded identically and share one address space - a signal and a switch at one address
  are one accessory, and `accessoryType` only decides which icon is drawn. Adam: *"they play very
  different roles in what they control; they are just commanded via the same protocol."* So code that
  looks one up must not key on the type, and a person reading the diagram must not be told the two are
  interchangeable. Both halves are true and neither implies the other.

  **Known limitation, accepted (CS3-B2).** One address drawn as a switch on one page and as
  a signal on another is held as one accessory in the database, and the pages are wired in order, so
  the page wired FIRST keeps a tile bound to the object the later page replaced: it stops following the
  railway and shows the position it had when its page was last wired. On Adam's layout that is address
  131, a turnout on 3 - Top Parking and a signal on 5 - Test. Nothing is commanded wrongly - every door
  commands by address - but each wiring pass also re-creates the accessory twice and seeds its remembered state
  from what the page file says, so after any diagram save the keyboard shows 131 as the file has it rather than
  as the railway does, until the next echo.  Adam's ruling is to leave it: *"Let's leave 5 as is and document
  the limitation."* Changing a tile's type ON one page is a different thing and does update the database,
  because that is the same re-creation seen from the other side; red is the turnout's turn and the
  signal's red, green is the opposite.

- **Occupancy and reservation are different facts.** A route holds track it intends to use; a
  standing train covers track it is lying on. Neither is the other.
- **An accessory record is a counter, not a claim.** Adam: *"there’s no reason to delete
  an accessory, since we just track their actuations; them being in the database doesn’t otherwise
  harm anything."* A row does not assert that anything is wired to that address. **Reading one must
  still not create it**: `getAccessoryState` registers a switch on a miss, which is right on a path
  about to command the thing and wrong on one that is only looking, so route conditions and the
  keyboard paint use `getAccessoryStateIfPresent`. Creation belongs to the path that commands.
- **A speed the railway reports is rounded, not truncated.** What the model believes about a train is
  what the length and blocking rules run on, so a fine step set from another controller must not be
  quietly lost on the way in.

**A configuration that names something the database no longer has loses that one thing, not the
layout.** Adam, on the Load JSON door refusing a whole configuration over one placed
train it could not find: *"drop the train and keep the rest."* A sold or renamed locomotive is the
file outliving the fleet, and taking the railway out of service over it is a much worse answer than
forgetting the one entry. So each of these is dropped and named in the log, and everything else in
the file loads as written - the next save writes it back without the dangling name:

- a placed train (AMR-C3);
- a home assignment;
- an exclusion from a station;
- a station restriction naming a square (`blockedBy`);
- a locomotive in the run list;
- **a lock edge naming track the file does not contain - and this one loudly.** Adam:
  *"drop the lock edge with a loud log line too."* A lock says two roads are one piece of metal, so
  the drop has a dangerous reading: if the name is misspelt for track that IS there, two trains may
  now be let onto it. The log line names the track that owns the lock and the name that matched
  nothing, says what that could mean, and says to check the file. The locks beside it in the same
  list are still applied.

The legacy importer behaves the same way and names what it left out, so the two doors agree about the
same file. A MALFORMED entry is still refused - a lock entry the loader cannot read (`errorLockEdgeGeneric`), or a
placement with no locomotive name at all (`errorLocomotiveConfigMissingName`) - because that is a broken
file rather than one that has outlived its fleet or its track.

**A Point's name is carried wherever a configuration stores it** (RSA3-B1, RSA4-A1, RSA4-B1, RSA4-C4).  A timetable, a
train's road and where the trains stand all name Points, and a Point is named after its square - the square's name, and
a heading where the square is more than one copy.  So a station renamed, or a square given more copies or fewer, carries
each to the copy it named:

- every configuration's timetable and roads - the one in use by its build, every other across a rename alone, since its
  own build may differ - and, across the rebuild the setup's doors make, each train the last run left somewhere, its
  road and a turn it is owed, to the same copy under the square's new name or to the copy facing the same way.
- **By the square, not the name** (RSA5-A1, RSA5-C2): a station renamed to the name another has hands that name to the
  renamed square, so each running Point says which square it is a copy of, and a stored name the build now gives another
  square is traced from the square it named.  Where no copy of the square faces a train's way there is none to put it
  on - and the direction that would do that is refused while it stands there (6a).
- **The fold reads the running layout the same way** (RSA6-A2): each running Point is written into the square it says
  it is a copy of, with its copy's facing, so a page ticked out after a run keeps where the run left the trains on it.
- A page renamed, or a square with no name of its own moved on its page, carries the names made from it - *page x,y* -
  into every configuration's timetable and roads (RSA5-B2); and no fold follows a move until the setup is opened again,
  since the railway running still names the squares as they were (RSA6-B2) - opening the editor folded already.  Nor
  after a square deleted, cut or cleared (RSA7-A1): the editor's Cancel puts back what it removed, and the fold after it
  would judge those squares against the page the editor had emptied.
- A move, or a page renamed, moves the squares the railway's Points say they are copies of with the setup's, and a
  Cancel or an undo puts them back (RSA7-B3, RSA8-B3), so a carry while a declined edit waits reads each train's square
  as it now is.  What the carry of names remembers of each build moves with a move too, and a Cancel or an undo puts it
  back with every stored leg - each by where it is, a road by its square (RSA9-C1) - so a timetable through stations
  moved - saved, cancelled or undone - stays on those stations (RSA8-B2).  What the carry remembers is taken as the
  editor opens, so a Cancel after two stations' names were swapped puts every leg of the setup back on its station too
  (RSA9-B2).
- Cancel also keeps what the railway changed alone while the editor was open - an Auto tab setting, a timetable entry, a
  card's pause - whatever was rebuilt in between, with the legs of a kept timetable carried to the names of the edit's
  last build (RSA69-C1, OB-343).  Known exceptions, for 3.1.0: a chain of renames that hands one station's name to
  another can point such a leg, or the railway's leg at the fold itself, at the wrong station (OB-345, RSA70-C3); and an
  editor gesture can overtake a card's pause no fold has noted yet (OB-346, RSA70-C1).
- A station name two pages share is settled over every page, those out of autonomy too, so the plain name does not move
  to the other page while one is out (RSA6-C1).

**A page ticked out of autonomy keeps its timetable entries** (Adam: *"Just keep the entries, and if a
path is run that contains a point on a disabled page, reject it with an error as we do with other autonomy paths.  Nice
and simple"*; RSA4-B2 to RSA8-B6).  The railway built without the page keeps each entry it cannot build as it was
written: listed in the timetable like any other and written back by every fold, so none is lost, whichever door ticks
the page, across a restart, or while a page's file has not loaded.  **When a run reaches such an entry it stops there**
- trains already under way finish their journeys - **and the window says which entry and why** (Adam:
*"stop the timetable run upon encountering an invalid path, and let the user know"*; RSA9-B1): passed over, a train's
later entries would start where it was not.  The dialog is shown whatever stop was pressed in an earlier run (RSA10-B1);
a Start Timetable refused while a Graceful Stop is still being carried out leaves that stop pending (RSA11-C1).
Start Timetable asks where each train stands of its first entry before the one the run will stop at, and nothing
past it (RSA10-B2).  Ticked back in, the page's entries run again.  **Clear clears every entry** (RSA5-C1).  Only
a locomotive the database no longer has drops an entry at the load.
`core.testLayoutTimetable`, `regression.testAnEditedPlacementSurvivesTheRebuild`,
`regression.testNoSetupEditDuringARun`.

**A simulation that is not simulating asks before any train is sent** (Adam: *"adding a warning popup when
you start autonomy when the app is in simulate, and simulate isn't checked"*): every door that sends trains - Start,
Start Timetable, Return Home, a hand send - asks where TrainControl runs in a debug simulation and the autonomy setting
Simulate is not ticked (RSA10-C1).  The send gate asks it once its own refusals - an open editor, a broken setup, the
power off - have passed, and before each door's own (RSA11-C2): a press the door then refuses for its own reason, an
empty timetable or a railway still busy, is asked first.  And a debug simulation starts with Echo Sent
Commands on (Adam: *"auto-enable echo in simulate too"*), saying so where it had been turned off.
`regression.testNoSetupEditDuringARun`.

**While autonomy runs the Autonomy menu greys what changes the setup** - the configurations, Manage Configurations, the
editor and the pages - each saying why; Autonomy Settings and Documentation stay live (MT-613
and MT-626; Adam: *"why not grey out the whole menu while running?"*), and the debug export is greyed with the rest - only
greyed, as it writes nothing of the setup (MT-633, RSA23-C2).  Each door that changes the setup still refuses on its own, and the Auto tab's settings refuse
a change while autonomy is busy - Return Home's planning too, before any train moves (RSA12-C1).
`regression.testNoSetupEditDuringARun.testTheAutonomyMenuIsGreyedWhileAutonomyRuns`.

**While autonomy runs the Layouts menu grays what changes the diagram or the folder**, saying why; opening the folder,
the pop-outs, the picture and the CS3 web app stay live (Adam: *"grey the relevant options, but we need to keep open
CS3 web app available"*).  `regression.testNoSetupEditDuringARun.testTheLayoutMenuIsGreyedWhileAutonomyRuns`.

**Closing TrainControl while it drives trains, in any tier, warns and changes no train** (RSA-C7; Adam: *"For
simplicity, don't change train state here."*).  Keeping TrainControl open is the default, the close presses no Graceful
Stop, and the warning says what closing leaves: the trains keep moving with nothing to stop them, and where they are is
not saved.  `regression.testNoSetupEditDuringARun.testClosingWhileTrainsRunWarnsAndLeavesTheTrainsAlone`.

**While autonomy is busy the Auto tab greys what waits for the trains** - its settings, Execute Timetable and the
capture toggle, with Activate Routes and its list (UXR-B3) - each tooltip saying *"Please wait for all active
locomotives to stop."*, whoever greyed it (RSA27-C3), and gives them back with their own tooltips once the trains have
stopped (Adam: *"Implement the two grey usability fixes"*).  Busy is a run, a timetable run, Return Home, a
hand dispatch from its click (RSA27-C2) or a Graceful Stop's coast-down, and Graceful Stop offered - Start's run counts
before the railway does; a Start that starts nothing - every train to run skipped - gives Start back at once and takes
Graceful Stop away (RSA27-C1), and says why.  A click on a greyed setting says nothing and leaves it as the railway has it.  `regression.testNoSetupEditDuringARun.testTheAutoTabIsGreyedWhileAutonomyIsBusy`,
`testARunGreysTheAutoTabAndItsStopGivesItBack`.

**Layouts > Create New Layout waits for autonomy to stop**, as every Layout menu door that changes the diagram does
(MT-141, RSA13-B2).  `regression.testNoSetupEditDuringARun.testCreateNewLayoutWaitsForAutonomyToStop`.

**Which pages autonomy uses is chosen at Autonomy > Pages with Autonomy Enabled**, refused while autonomy runs (MT-141)
and rebuilding the railway after each tick.  The Autonomy panel has no Pages dialog of its own: the panel is
built and not shown (RLD3-C4, RSA8-C1).  `regression.testNoSetupEditDuringARun`.

**The Yes to "reloading will stop the trains" stops every train where it is** (RSA-C1).  Graceful Stop lets each journey
finish; the Yes also counts a stop, which every journey carries from where it was chosen and asks before and after each
speed it writes - so a journey chosen before it sends nothing, a train still claiming its route is given it back unrun,
and one reaching its next sensor is not given its speed back (RSA2-C1, RSA2-C2).  A timetable - Return Home's plan among
them - is not started after it: the Yes and a timetable starting are held apart, so one sees the other (RSA4-C2).  A
timetable's entries and its own loop count as running until they leave, and any stop wakes their pauses, so the railway
reads busy until the old timetable has gone and Start cannot come back into a run it would then join (RSA3-C5, RSA4-C1).
The Yes waits for no lock a journey holds: it is answered on the window's own thread (RSA3-C4).
`core.testATrainIsDispatchedOnce`.

**Nothing is sent while the track power is off** (GST-B1).  Autonomy, Execute
Timetable and Return Home - whose plan runs as a timetable - wait before they choose a journey, start a timetable's entry
or try a refused one again while the power reads off, cut from the window, by the Central Station or by a route, and go
on when it reads on: the run is not stopped, and the log says once that it is waiting.  A journey still setting its route when the
power goes off - or goes off and comes back while it does - is given back unrun: its train is left where it stood and given
no speed, the log says so, and the run waits for the power (RSA61-C1, RSA62-C2;
`testAJourneyIsNotSetGoingIfThePowerWentOffWhileItsRouteWasSet`, `testAJourneyIsNotSetGoingIfThePowerWentOffAndOnWhileItsRouteWasSet`).
An attempt the power cut is not counted against a timetable's entry as a refusal, so it does not end Return Home
(RSA62-C3, `testPowerCutsDoNotEndAReturnHomeLeg`).  A journey that has set off is not touched.  The power reads on until the Central Station says otherwise - at start-up, and always in a simulation without
Echo Sent Commands - so a state nobody has reported never holds a run.  Instant Stop is a halt, not a power cut, and
autonomy runs on through it (OB-251).  A hand send is not waited for: the send gate refuses it while the power is off, as
it refuses every door's press.  `core.testATrainIsDispatchedOnce.testAutonomySendsNothingWhileThePowerIsOff`,
`testATimetableStartsNothingWhileThePowerIsOff`.

**An old autonomy.json goes into the configuration named at the Import prompt** (Adam, choosing
between honouring the name typed and not asking for one: *"(a)"*), created where there is none of that name.  It is
chosen only while the import writes into it: the configuration running stays the one in use, and is the one the next
start resumes, whether or not the reload after the import goes ahead - the imported one is loaded only where nothing
was running.  The import's message names the configuration its trains went into and, where another is in use, where
to choose it.  It leaves Startup > Load Autonomy as the operator set it (TDD-C11): that setting resumes the active
configuration at start and is not the import's to untick.  `regression.testALegacyImportLeavesLoadAutonomyAlone`.

**Once a configuration exists, New Configuration is the one door to another** (Adam: *"One door: do it"*): a copy of the
one chosen, which asks whether where the trains stand and the timetable come too.  Add a Configuration is offered only
for the very first.  `regression.testNoSetupEditDuringARun.testManageOffersOneDoorToANewConfiguration`.

- **Into the configuration in use** (RLA2-B1, RLD3-C1, RLU4-B1): a bundle under that name is
  refused, and so is a configuration of the older, bare shape - the setup folder's own file, or an export from before
  bundles - with where to choose it once it is imported under another: each replaces a configuration, and the reload
  after it captures the running railway straight back over its settings and timetable.  An old file under that name
  fills gaps as MT-298 has it, and places none of its trains, which its question says (RLU4-C3): what the running layout
  knows goes into the configuration first, the reload after the import does not capture again, and where a train stands
  on the railway running is the railway's to say (OB-183) - the message names the trains it did not place, and how to put
  one down.
- **While autonomy runs every import is refused**, as Delete is, before it asks for a file (RLU4-C1, RSA4-C5): into the
  configuration in use the reload's capture would take back what the import had just brought, and into another the fold
  after a declined reload would read the running railway through names the import had changed.  So is every export,
  before it asks where (MT-601; Adam: *"Export should also only be allowed once we are stopped"*), and New
  Configuration, Rename and Add Configuration, before they ask a name (RSA10-A1).
- **At rest**, New Configuration and Export fold the running railway into the configuration first, so the copy and the
  file have the trains where they are and the settings and timetable as they now run, and the configuration running
  stays the one the next start resumes (RSA10-A1, RSA11-B1, as RLD-C3 for an import).
- **No import folds the running layout into the setup while a setup edit a run declined waits for its rebuild** -
  neither the capture first nor the reload (RLD4-C3, WKW-B2); every load then carries each train across from the running
  layout and lowers the flag once a layout carries the edit, and choosing another configuration first carries the one
  running, which is then folded as any configuration left is (RLA5-B1, RLV6-B1).
- **A load confirmed while a train is under way** - which holds every point of its path - folds the running layout with
  that train written on one point, the last station on its path whose sensor it has tripped, or where it set off; so the
  Auto tab's settings and a timetable captured this session are kept (RLV7-B1, RLV7-C1, RLV8-B1).  The timetable written
  is the configuration's own, not a plan Return Home has borrowed it for (RLV9-B1); the destination turns the railway
  has made and not yet written down are kept across the load (RLV9-C1); and a train under way whose last station another
  has since stopped on is written on the first station ahead on its path that it still holds and no other train under
  way is kept at - or one another is kept at that can itself be moved on, along the chain - and no two trains are kept on
  one square, its copies counting as one (RLV9-C4, RLV10-C2, RLV12-C1, RLV12-C2).
- **Unload** folds the running layout into the configuration first, as a reload does, so a load after it has the trains
  where the run left them (RLV9-A1); while a setup edit waits for a load it writes what the railway owns - where the
  trains stand, and the settings and timetable the railway changed - and nothing of the setup's (RLV10-B1, RSA21-C2).
  Unload, and choosing another configuration, write the turns the railway owes into the configuration they leave
  (RLV10-C1).  Unload, a deleted setup and a switch of railway forget the railway loaded, so a load after them carries
  nothing across (RLV7-C2) - a switch only to another source, not to the one in use chosen again (RLV8-C5), the folder
  the Central Station's layout was downloaded into counting as the one in use (RLV9-C8) - and Unload retires the railway
  it drops, so a train its thread was driving is not driven on (RLV8-A1).  Another configuration is not chosen while the
  one running cannot be used with the edit that waits: the choice is refused, and says so, and the one running stays
  loaded - with its errors, where it builds with them, since a setup with errors loads so that it can be fixed (RLV7-C4).
- **No setup edit is made while trains run** (Adam): the diagram's setup menu and its Edit Locomotive are offered only
  at rest and refuse, and say so, when clicked after a run has begun; no train is sent - from the Auto tab either -
  while the editor is open, nor while the diagram reloads after a page door - Duplicate, Add Blank Page, Rename, Delete,
  Combine Linked Pages, the editor's Save and Cancel, counted before the editor closes (RSA24-C1) - until the reset at
  its end has run (RSA23-B1), nor after the autonomy editor's Save, Cancel or a jump to another page, notice or square
  until the hand-over behind it has run (RSA25-C1).  A discard in the autonomy editor - Cancel's "exit without saving",
  Discard at a page switch or a jump, or on the way out - rebuilds the railway from the restored setup before the window
  goes, so a door that folds the railway into the configuration meanwhile writes the setup as it was (WKV-B2, RSA26-C1).
  A tail question answered once trains run, and a direction change followed once they run, write nothing - so during a
  run an edit waits only where the run began in the moment between an edit and its rebuild (RLV11-B1, RLV11-B2, RLV12-B1,
  RLV12-C3, RLV12-C4); at rest one also waits where an edit leaves the setup unable to build - a link unpaired - and the
  last railway that built stays loaded meanwhile (Adam; 6a).

Into a configuration that exists, an old file fills what that configuration does not already say (MT-298), and the
door asks exactly that - *"Add to it what the file has and {0} does not?"* - where a bundle replaces the
configuration and asks to replace it.  The question also says that a setting at its default, a home taken off or an
emptied exclusion list counts as not set and takes the file's value: a default is stored as nothing (RLA3-B1).  It says
too that once the configuration has been loaded each of its stations keeps its own maximum train
length, no limit included (RLV12-C6).  A train the configuration already has standing somewhere is not placed again,
and is named in the message; a train that already has a home keeps it, and is named too (RLA2-C3) - one train in two
places refuses the whole configuration (RLA-B2).  A train the file places faces the way the file ran it, over
the facing the square's last occupant left there (RLA2-B3); where the file cannot say, the last occupant's stays.  A value
the configuration holds is kept - including a station maximum of 0, which a capture writes for every station; whether a
setting at its default should count as set was put to Adam, who kept the gap-fill (RLA3-B1).  A file the import cannot read to its end leaves the setup as it was: nothing made, chosen or
saved (RLA-C3).  `core.testASecondImportFillsGapsAndDoesNotOverwrite.testAnImportGoesIntoTheConfigurationNamed`, and
`regression.testTheImportDoorReadsAnOldFile`.
