# Routes

Part of the behavior reference: [`../behaviour.md`](../behaviour.md) is the index.  Sections keep the numbers and headings they had in the single file, so a citation such as "behaviour.md §5c" still resolves.

## 7a. A route that meets a train

A route is a list of commands: accessories, functions, locomotive speeds, the power. Any of its switches may sit on track
a train is standing on or has reserved, and throwing one there moves metal under a train.

**What "under a train" covers**: the track a train's journey holds, claimed and not yet given back. A train standing at
the end of its journey has given its route back, so a switch under its tail is neither refused nor asked about. A route
reaches such a switch during a run only because somebody fired it by hand or armed it as a conditional route and
answered Yes to Start's warning that conditional routes are active; Adam ruled that the operator's to control.

**Only the accessory a train needs is refused - the switch under it, or a signal turned green over a platform it
stands at or is bound for. Everything else in the route runs.** Adam:
*"don't run the conflicting switch commands, but do run the power off and others."* Each accessory is asked about
immediately before it goes out, so one held turnout costs that turnout and nothing else (MT-247).

**The setting the train's path already holds is not a change** (Adam: *"make sure the code allows
switching to the correct (same) setting that autonomy is using"*). A switch a path holds, and a guard signal it holds
green (the exit guard of the platform it leaves, the home signal of the one it is bound for), may be set by a route to
that same setting, and is neither refused nor asked about. Where two paths underway would hold one accessory at
different settings, every command on it is refused.

**Who is asked depends on who started it.**

- **A person started it.** The dialog stands. *"Cancel should cancel everything. OK should fire
  everything."* OK fires the whole route, the switch under the train included, because the operator has looked at the
  railway and said so. Cancel abandons **the rest** of the route:
  - the screen asked **before** the route starts refuses it outright: `askAboutRouteConflict` returns `REFUSED` and
    neither caller calls `execRoute`, so not one command goes out;
  - the question raised by a conflict that **appears while the route runs** stops it where it stands:
    `MarklinRoute.execRoute` returns from inside its command loop, so earlier commands have gone out and stand, *"once a
    command is refused the ones already sent stand, because
    they went out before the conflict existed."*
- **A trigger started it** (an s88 or a condition, with nobody watching). No dialog: nobody would see it. A line is
  written to the log, and the route runs without the held switches.

**An emergency stop is obeyed whatever else is true.** A route carrying a stop is never refused at a human door and
never has its stop skipped: suppressing a stop is the one refusal that can make things worse.

**An emergency stop stands in a route of its own** (Adam: *"if a route has emergency stop, it cannot have
any other types of commands.  Reject it from being created or imported as such.  This will keep a clean separation."*;
MT-507, MT-508: *"routes with an emergency stop should still fire"*). The route editor will not save a stop among other
commands, and the model refuses to make one. A route that should set something and then cut the power fires a stop-only
route with a Route command; it counts as carrying the stop, so it is never asked about, and fired by its sensor it
passes that on, so the notice that a route cut the power is shown. A route saved or exported before the rule is split
when it is loaded or imported (Adam, asked about his own *"Auto Emergency Stop Bottom Secondary"*:
*"Split it automatically"*): it keeps its name, sensor, conditions and whether it is armed, and fires a new route named
"(its name) (Emergency Stop)" where its stop stood, with the stop's wait; the log names both, and so does an import's
message. The stop route is numbered from 1000, as a route made in the editor is, so a Central Station sync, which
replaces its own routes by number, cannot replace it. A route that is only a stop runs however deep in a chain of routes
it is fired (it fires nothing further), and a route that fires a split route still cuts the power, and counts as a
route that does.

`core.testAStopRouteStandsAlone`, `ui.testCommandTableMarks.testAnEmergencyStopStandsAlone`,
`ui.testARouteOverATrainAtItsDoors`.

**Saving a route leaves autonomy alone.** A route is no page of the diagram (its tiles follow it by number), so the
route editor's Save refreshes the routes and redraws the diagram and does not rebuild the autonomy setup: during a run
that rebuild would ask to abandon it, and either answer is wrong.
`regression.testTheImportDoorReadsAnOldFile.testSavingARouteWhileAutonomyRunsLeavesTheRunAlone`.

**Conditions hold back the route's own firing, not a route somebody runs by hand.** A route's conditions are read in
two places only: the s88 monitor thread that fires the route on its trigger, and the editor's Test button, which reports
what that thread would decide. `execRoute` does not read them, so the play button, the right-click Execute item and a
route tile on the diagram run the route whatever its conditions say: the same rule as the tiered destination lists in
§1, and the same reason as letting a person answer OK above. The help text says which firing the conditions hold back.

## 7c. The signals a station commands: the exit guard and the entry guard

A station can be paired with signals of two kinds, each set up from the station's right-click menu in the autonomy
editor through the same dialog (the list, **Click It on the Diagram**, **Enter Its Address...** (several at once,
comma-separated), **Remove Selected**, **Done**), and each may hold several signals, because a platform reachable from
two ends needs one on each approach. Each item says what its guard does in a tooltip (OB-293; Adam: *"add brief tooltips on what
entry guards and exit guards are"*).

**The exit guard**, *Exit Guard Signal...* on the menu (Adam: *"rename it, but add Signal at the end
(Exit Guard Signal, Entry Guard Signal)"*). Its signals are RED while the platform is claimed (a train standing there,
or a locked path that starts or ends there) and GREEN when it is free, except that the first edge of the path a train
leaves by holds it GREEN for that train (OB-340, below). **A train only passing through leaves it alone** (OB-315,
OB-316; Adam: *"the exit guard signals are applied for more than just the arrival
station"*): stations a locked path only passes do not count (`Layout.onlyPassing`). An aspect DERIVED from the platform,
asked again on every change of occupancy (`Layout.refreshProtectingSignal`), and per SIGNAL: one paired to two platforms
stays red while either is claimed. Only while trains are being run, so arranging the railway by hand moves no hardware.

**A route over a guard sets it as it sets any signal on it**: GREEN, with the edge's other accessories, as the route is
claimed edge by edge. So a train leaving by its station's exit signal turns it green as it goes (OB-315), and it stays
green while that edge is held. A guard that routes to other stations cross is turned green the same way, over a train
standing at its station. A guard on a way INTO its station (a home signal) is turned green by the arriving route on its
last edge and stays green over the train once it stands there; one an edge further out is turned green by its edge and
stays green through the approach (OB-340). Adam's ruling is that a signal a path crosses and a signal protecting a
station are two different signals on a railway wired as he intends; the signal on the way in is the entry guard's. The
runtime does not choose between a route and protection; the editor's notices say which case a guard is in.

**A train leaving a station turns its exit guards green** (OB-340; Adam: *"when departing a terminus,
autonomy should make its exit guard green.  otherwise, trains stopped by that guard would remain stuck."*, then *"extend
the rule to MAY TURN stations too.  just make the exit guard green when something departs."*, and *"this is essentially
just adding the exit guard into the config commands of the path out of the terminus, so that it turns green on the way
out."*). The first edge of every path sets the exit guards of the station it leaves GREEN with its own accessories and
holds them as it holds those: any station, from whichever copy of the square the train stands on, and where the edge
commands one of them itself its own setting stands.

- **Protection leaves a signal an active path holds green**: it does not turn it red while that edge is held, though a
  platform it guards is claimed (the leaving train's own, or on a shared guard another train's; Adam ruled that a shared
  guard is green while a train leaves). A refresh on another train's thread reads the holds and commands the signal as
  one step, and a departure turns a signal green only once its hold can be read, so GREEN lands after any red decided
  before it.
- The hold lapses when the train's tail has passed the first edge. With Atomic Routes off the start is given back then
  and its guards are asked again at once; with them on, at the next change of occupancy at a platform they guard, or
  when the run's track is given back, so a shared guard can stay GREEN over a train at its other platform until then.
- The rule is general: a signal any active path holds GREEN is not turned red under it, so a home signal an edge further
  out stays GREEN through the approach, and over the train once it stands there until that station next changes.
- The guards are set GREEN before the train is given its speed, commanded, not confirmed: they are not among the
  accessories `Layout.validatePathActuation` waits for, so a departure that fails that check leaves them green while it
  waits and red once its route is given back.
- A signal that is one station's entry guard and another's exit guard is thrown RED by an arrival at the first
  (`Layout.throwEntryGuard`), whatever a path holds; the editor's notice for an exit guard that routes to other stations
  cross already points at such wiring.

**The entry guard**, *Entry Guard Signal...* (FR-096; Adam: *"a signal that turns red after arrival at the
final designation.  Same UI to set it as the current linked signal exit guard, and multiple selections are
possible"*). Its signals are thrown RED when a train ARRIVES at the station as the end of its journey, in every tier,
because every run records its arrival in one place (`Layout.executePath`). A train that only passes throws nothing.
**Nothing turns it green**: *"The next route sets it green, so that is out of scope."* So it is a command on an event,
not an aspect, and nothing is remembered or undone.

The two lists are separate and thrown at different moments, and **one station's entry guard is never its exit
guard** (Adam: *"make sure the entry guard can never be the same as the exit guard.  otherwise, it's
up to the user to set it up right."*): the editor refuses the pairing and says why, both setters refuse it, and a file
carrying it is warned about. Two stations may still share a signal. The editor's notices read the built railway (which
routes command each guard, walked from the station's copies to the first stop on each way, so a train turns back only
where it may; *"if the guard signal is not on a path leading to the chosen station, we can add notice"*):
- an entry guard no route into its station commands;
- an exit guard a route into its station commands (the entry guard is the signal on the way in);
- an exit guard routes to other stations command (they turn it green over a train standing there);
- an exit guard no route commands away from the station's own track (it only shows whether the station is occupied; on
  the station's own track, past a terminus's sensor, that is what such a signal is for).

An exit guard commanded only by routes out of its station is its exit signal, and gets none. Where the guards are is
otherwise the operator's. Both are dropped with the station when it stops being one, and a pairing whose signal tile has
gone is dropped when the setup is reconciled and reported by the editor's gone-signal notice.

## 7b. Routes that come from a file, and the pause between their commands

**A route read from a file arrives switched off.** Adam: *"they should not be armed.  The
user can choose to do this when they are ready."* Routes then Import reads each route's automatic flag and then
overrides it: the one thing about an import that is deliberately not a faithful restoration.

**Unless the operator asks for it** (Adam: *"save the state in the file on export, and ask the
user on import.  if they want them armed, arm them.  otherwise, don't."*). The export writes each route's `auto`, and
where the file holds any route saved armed, the import asks (before anything is replaced, No the default) whether to
turn automatic firing back on for those. A Yes arms exactly those, after every route is in, and only one with a sensor
to watch; a No, or a file with none saved armed, imports them all off. The question names the routes, and the message
after a Yes counts the routes the import armed: a route saved armed that has lost its sensor is not armed and not
counted, and where none was armed the message is the one a No gets (MT-496, MT-497).

It is also what makes the import safe. Building a route ARMS it (a route with a sensor and its flag set parks a thread on
that sensor as soon as it exists), so a file that failed half way through would leave routes on no list throwing
switches for the rest of the session, and on a successful import the route replaced and its replacement would both
watch the same sensor until the old one was deleted.

**A delay below 150 ms is the same as a delay of 150 ms.** The executor pauses for a fixed interval PLUS the larger of
the command's delay and 150, so the floor is on the delay, not the whole wait. The editor shows the floored number in
both directions: a delay from an earlier build, from a station or from a JSON file is shown raised too. Zero is left
alone: it means no pause was asked for, what an untouched command holds, and the executor waits the same 150; raising it
in the editor would put a pause on every command in every route. `THREEWAY_ROUTE_DELAY_MS` sits above the floor, which
makes the gap a three-way switch needs between its two commands real.

**A speed in a route is between 0 and 100, and a negative speed means an instant stop.** Any other number is clamped
where the command is BUILT, so a route from a JSON file or a Central Station import cannot hold one. The editor instead
range-checks a typed speed and refuses to save, which tells the operator rather than quietly changing what they wrote.
An unclamped 150 would be discarded by the head of a multi-unit while every member was sent 100.
