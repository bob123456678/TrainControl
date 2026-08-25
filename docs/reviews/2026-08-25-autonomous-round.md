# The autonomous round, 25 August 2026

**Status:** open

**Prefix for citing this document's findings: `AU`.**

Adam stepped away with a sixteen-step work order: finish the open bug and feature items, then run
three review passes over the work and iterate on what they found. This is the record of the review
half - what each pass covered, what it found, and what it missed.

**Read the findings as calibration, not as a bug list.** Every one is fixed, and the ledger entries and
`MT-###` tests carry the operational half. What is worth keeping here is *which kinds of mistake got
through*, because three of the four serious ones were made by the same author, in the same session, as
the fix for a finding of exactly that shape.

---

## Method, and why it was set up this way

Three passes over the last two days of commits, each given one axis and told to hunt **regressions and
omissions** rather than style:

| Pass | Scope | Model |
|---|---|---|
| Store | `AutonomyCompanionStore`, `TileGraph`, `LayoutPageEdit`, `LayoutDiagram`, `CS2File` and their tests | Opus |
| Window | everything under `src/org/traincontrol/gui/` and its tests | Opus |
| Core | `automation/`, `AutonomySession`, `AutonomyBuilder`, `AutonomyChecks`, `GraphReducer`, `DiagramMonitor` | Opus |

Then a validation pass over the fixes, and two independent passes: one over three days on a different
model, and one over the whole application from first principles rather than from the diff.

The whole-application pass was told not to start from the commits or the ledger. It began by reading
what twelve prior passes had *declared they had not covered* and went there - the consist lock
ordering, the eleven files an earlier review listed as "not read at all", the protocol layer nobody had
ever run, and the seam where route execution crosses a running autonomy session. That last one is where
the round's most serious finding was, and no diff-shaped review could have found it: both sides of it
are old, correct, and unchanged.

The three-day pass earned its place by NOT re-walking what the first three had covered. It went to the
seams instead - the day nobody had reviewed, the protecting-signal mechanism, the fixes from the first
round that no reviewer had seen, and two whole-tree sweeps for defect shapes rather than for files.
Both of its findings are in seams: a rule applied at two of three doors, and two halves of one editor
reaching disk at different moments.

Each was told to prove findings rather than report suspicions, to check whether an existing test
already caught the thing, and to say what it had **checked and cleared**. That last instruction earned
its place: the cleared lists are most of the value of these documents six weeks later, because they say
where not to look again.

---

## A - wrong behaviour on the layout, or data silently lost

### AU-A3 - the route guard discarded the emergency stop it shared a route with

**Found by:** the second validation pass, which measured `getPowerState()` rather than reasoning about
it. **Fixed:** `6b6e6bd4`.

AU-A2's refusal returned before the command loop, so every command in the route went - not only the
accessory ones. A route that cuts the power **and** sets a trap point, which is the shape a safety
route on an s88 trigger naturally has, was refused entirely because of the turnout. The stop did not
run, through the door that fires by itself, with nobody present.

"Refused whole" is a good argument about accessories - setting three switches of five leaves the layout
in a state nobody chose - and it is not an argument for suppressing a stop, which is safe to obey
whatever else is true. The accessories go as a group now; everything else runs.

**This is the round's clearest lesson about itself.** AU-A2 was the most serious defect found, its fix
was written carefully, its test was mutation-proven, and the fix introduced a worse defect than the one
it removed. The only reason it did not ship that way is that somebody was asked to attack it
specifically.

### AU-A2 - an executed route threw switches on track autonomy had locked

**Found by:** the independent whole-application pass, which proved it by running it.
**Fixed:** `7e2c6f81`.

Route execution and autonomy path locking each worked exactly as designed, and neither consulted the
other. `configureAndLockPath` reserves every accessory on a path, commands it and validates it. A
route then set the same accessory back, with no refusal and nothing said. The train is routed off the
path that was protecting it.

**Three doors reached it, and the automatic one is why this is the most serious finding of the
round.** An s88 trigger route left over from manual operation fires when an *autonomy* train crosses
the trigger sensor - sensors are shared and reused on this railway - so no person is involved at any
point. The routes tab is manual and silent. And the diagram's route tile *looked* guarded: that guard
asks `activeAccs.contains(c.getAccessory())`, and a route component's accessory is null, so the one
door that appeared to check was checking nothing.

Refused rather than confirmed, and refused whole: `MarklinRoute` is the model half and has no business
showing a dialog, and a route half executed leaves the layout in a state nobody chose. Only the
accessories actually on a locked path, so a route that turns on the lights or cuts the power runs
during autonomy exactly as before.

**The test for it passed with the guard deleted, at first.** The started callback fires once per leg,
and the second call overwrote the first's verdict - by which time the accessory had already been
thrown, so "unchanged since I last looked" was true. Found only by running the mutation the javadoc
claimed. It accumulates now.

#### Both of the sentences in bold above were wrong, and Adam corrected each of them

This is the most useful thing in the document, so it is left in place rather than tidied: the finding
was right, the fix was reasonable on its own terms, and both of the design decisions stated confidently
above turned out to be over-reach that only somebody who runs the railway could see.

**"Refused whole" was wrong twice over.** First in the small: refusing whole discarded the emergency
stop, which is the one command in a route you least want silently dropped. That was caught by
validation the same night and narrowed to the accessory commands only.

Then in the large, by Adam: *"conflicting routes should still be executable in case of a transient
accessory failure. Add a confirmation dialog to the UI similar to how individual clicks currently work
when an accessory has an active route."*

The case is not obvious until it is said out loud. **A turnout that did not take its command is exactly
when somebody needs to set it, and exactly when it will be on a locked path** - because the path is
what commanded it. A guard with no way past it takes the recovery away at the moment it is wanted. The
two doors with a person at them now ask, using the dialog an accessory click has always used; the s88
trigger door still refuses, because nobody is there to ask. Fixed in `5a9d57a6`.

**"Only the accessories actually on a locked path" was too strict**, and Adam predicted the shape of it
before seeing the code: *"be careful with auto disallowed routes to avoid regression. once a train
passes, signals on the route, but behind the train, should still be allowed to be changed by auto
routes."*

The guard asked whether the edge's LOCK was still held - and with `atomicRoutes` on, which is what his
configuration uses, the lock is held for the whole path until the run ends, **by design**. So every
accessory on the path was refused for the whole run, including the ones the train cleared in the first
thirty seconds. His railway has 39 s88-triggered routes.

Locking and clearance are different questions. The lock asks *may another train be routed here*, and
atomic means no for the whole run. The guard needs to ask *is there a train on top of this* - and the
railway already computed that: it is what decides when an edge may be released when atomic routes are
off. It simply was not computed when nothing was going to be released. Now it is, in both modes, by the
same code, with the unlocking as one of two consumers rather than the owner. Fixed in `48f48bae`.

**What this says about the round.** Two of the round's design decisions were made by reasoning from the
code, and both were defensible from the code. Neither survived contact with somebody who knows what the
railway is for. The first, "the model does not show dialogs", is a rule that is true in general and
whose exception - a person recovering from a hardware fault - is invisible from inside `MarklinRoute`.
The second is worse, because the information was there: `atomicRoutes` is read three hundred lines from
the guard, and asking what it did to the guard's premise was one grep. What made it easy to skip was
that the guard *looked* like it followed the train. It read `isLockHeld`, which does follow the train -
in the other mode.

### AU-A1 - the OB-085 impossibility proof was built out of a rule the railway does not have

**Found by:** the core pass. **Fixed:** `4419d1cf`, narrowed further in `6523a90b`.

`HomeStaging` proves that two homes each held back by a square the other's occupant must end on is
impossible from the graph alone. It asked "would a train standing there close this station" through
`sameTrackAs`, which unions block copies **and** the points sharing a feedback address.

The sensor half is the planner being conservative on purpose, and `plannedOccupancy`'s own javadoc
prices it honestly four lines away: *"It fails SAFE - a refused plan, never a wrong movement - but it
is the 'planner is the stricter half' shape, whose symptom is NO_PLAN_FOUND."*

**A refused plan and a proof are not the same claim.** `IMPOSSIBLE` names locomotives and asserts that
no arrangement exists; `NO_PLAN_FOUND` says only that the search ran out of room. A heuristic that
fails safe in the second role fails *unsafe* in the first, and the direction of the error flips with
nothing in the code changing.

The reviewer built the counterexample: two ordinary platforms, one one-way restriction, and an approach
guard sharing a feedback address with the other platform - which `AutonomyBuilder` says outright is
normal. The railway stages it in two moves. Return Home reported IMPOSSIBLE and named both locomotives.

**Both of the tests written for that scan were blind to it.** They build their restrictions from direct
`Point` references, so neither reached `sameTrackAs` at all - including the control written
specifically to stop the scan over-claiming. It could not see the way the fault actually happened.

Fixed by giving the proof a block-only widening. The counterexample is a permanent fixture and putting
the wide relation back fails it.

**This is the third thing put into that scan that was wrong.** All three looked obviously right. The
ticket said so before any of them were written, and it was right.

### AU-A2 - `deletePage` never dropped the setup's record of the page, and a fix hours old made it visible

**Found by:** the store pass. **Fixed:** `4419d1cf`.

`deletePage` cleared the twelve collections, the excluded-page set and the configurations, and left
`pageNamesWhenWritten` - which is written back out as the file's `"pages"` map and therefore survives
every reload.

`pagesNotLoaded` walks that record, so a page deleted **on purpose** was named for ever as one that had
merely failed to load. `pagesSafeToJudge` was therefore false for the rest of the layout's life, and a
session that cannot judge never reconciles anything again. FR-018 could not clear it either: it offers
the "it was deleted" answer for pages the *index* still holds, and a deleted page is not in the index.
There was no way out of the state.

`forgetHeldPages`, written the same day, ends with exactly the missing line. Three hundred lines apart.

Two things make this worth reading twice. First, DR-B10 - landed three hours earlier - added a warning
dialog on every editor save when the setup cannot be judged, so a latent bug became a dialog naming the
page the operator had deliberately got rid of. **A correct fix made an existing defect user-visible,
which is what fixes are supposed to do, and it still reads as a regression.** Second, the first attempt
at the fix was ineffective: `sharedFields` rebuilds `"pages"` from the live index, which still held the
page. The test caught that.

---

## B - wrong results in a reachable configuration

### AU-B1 - Escape meant "yes" at six confirmations

**Found by:** the window pass, with a working probe. **Fixed:** `4419d1cf`.

All six use `showOptionDialog` with a custom options array, so the return is an index - 0 for Yes, 1
for No, **-1 when dismissed**. Every site tested `== JOptionPane.NO_OPTION`, which is 1, so Escape and
the close box fell through into the destructive branch.

What they do: wipe the whole captured timetable with no undo; discard unsaved autonomy JSON; delete the
timetable entry under the cursor; reset the timetable to unvisited; exit the application with trains at
speed; execute a timetable past a conditional-route warning.

**The same fix was made in `LayoutLabel` days earlier.** The review that raised it there checked
two neighbouring files, concluded *"every other confirmation in these files is written the safe way"*,
and never looked in `TrainControlUI` - where six of them were.

### AU-B2 - Start Autonomy was offered over a setup that would refuse it, under a comment saying otherwise

**Found by:** the window pass. **Fixed:** `4419d1cf`.

`canStartAutonomy()` was the Start button's enabled state and nothing else, and no writer of that flag
consults the checks - the button is deliberately left enabled and explains at press time. The
right-click menu item asked it, greyed on it, and offered the item live over an errored setup; pressing
it produced a refusal dialog. Two controls on one window, feet apart, disagreeing about one action.

The comment beside it - written by me, in this session - said *"the Start button has always known; it
just was not asked."* It had not.

**This is the guard-versus-affordance shape again** - the one this repository has paid for repeatedly
in the last week - and the first instance where the false claim was in a comment justifying the fix for
an earlier instance of the same shape.

### AU-B3 - the compound key's write path was guarded by nothing

**Found by:** the store pass, by mutation. **Fixed:** `4419d1cf`.

FR-013 stage two gave `tileDirections` a typed compound key. Making `DirectionKey.withSquare` throw the
route away - which would collapse every direction on a switch onto one route, last write wins, on any
page rename or tile move - **passed 209 tests across seven classes.**

Every fixture in the repository used `RouteId(0, 0)`, a pair that reads the same either way round. The
identical discovery had already been made for the *read* path while the conversion was being written,
and recorded in its commit message; it was not carried to the write path.

The settings matrix now uses an asymmetric route and the mutation fails three tests.

### AU-B4 - `forgetHeldPages` reintroduced the id-as-name pun it exists to prevent

**Found by:** the store pass. **Fixed:** `4419d1cf`.

It matched held keys against a set holding both page names and the ids those names were written under.
A page may legally be called `"5"` - Adam's ruling - so declaring the page *named* `"5"` gone deleted
the held entries of the page whose *id* is 5.

The names half could never match anything legitimate: `pageIsHere` answers true for any bare
unrecognised name, so a name-keyed entry is never held. It could only produce that false positive.

### AU-B5 - the staging audit was structurally blind to the thing it exists for

**Found by:** the core pass. **Fixed:** `4419d1cf`.

`auditAgainstRuntime` exists to catch the planner and the runtime disagreeing. Its FR-001 exemption
asked `plannedOccupancy(this.start)` - which is exactly what `canRest` asks, on exactly those
arguments. The exemption and the audited expression cancelled, so a planner mis-copy of FR-001 could
never produce a divergence in either direction.

The comment claimed the narrowing bought visibility of precisely that. It asks the runtime's question
now.

### AU-B6 - a parked train was reported as holding an impossible facing

**Found by:** the core pass, with a probe naming 16 squares on the fixture. **Fixed:** `4419d1cf`.

`facingChoices` offers where a train could be sent *onward*, so it never offers an arrival side. A
train that turned round is pointing back at the side it came in by - so on a berth with one turning
copy, the only facing the square can hold was flagged as impossible.

The carve-out was written into the new agreement test when the arrival-sides consolidation was checked,
and not into the production check three hundred lines away. It appears on the railway the first time
autonomy parks a train in a berth.

### AU-B7 - a hand dispatch never swept the protecting signals

**Found by:** the three-day pass, with a probe that failed at HEAD. **Fixed:** `8117b2a7`.

While nothing is running the protection refresh is deliberately silent - trains are placed and taken
off by hand then, and driving real signals from a setup gesture is what that silence exists to
prevent. The consequence is that a train placed at a protected platform while idle produces **no
occupancy change**, so nothing will ever command that platform's signal on its own.

`runLocomotives` and `executeTimetableInternal` both sweep every protecting signal the moment they set
`running`, and both say why. `executePath` - the diagram's right-click dispatch - became a full run in
the MT-139 work, counting its thread and engaging every guard, and did not inherit the sweep.

So: place a train at a protected platform, then hand-dispatch a *different* train, and that platform
shows GREEN for the whole dispatch with a train standing in it. Start autonomy instead and it goes
red.

Adam's rule is quoted inside `executePath` itself: *"The same thing should happen in manual operation
vs auto - the same switches and signals set, and guards applied."* Two of the three doors did it.

**The shape:** the sweep went in on 22 August, the hand dispatch became a run on the 23rd, and nobody
joined them. Neither change was wrong; the pair was.

### AU-B8 - the editor writes the setup per gesture and the diagram only on Save

**Found by:** the three-day pass, from the code rather than from a run. **Filed as OB-108, not fixed.**

`LayoutEditor.rememberAutonomy` saves the setup after every drag, move and bulk edit - by design,
since the session is rebuilt from disk after each one. The diagram is edited in memory only, and
reaches disk at editor Save, because discard works by re-reading the page files.

An abnormal exit while an editor is open therefore leaves a setup keyed to the MOVED squares and page
files with the track where it was. On restart every page loads, so `pagesSafeToJudge` is true and the
OB-068 hold does not apply - and the first reconciling save prunes the moved stations as settings for
track that does not exist.

**Filed rather than fixed** because every remedy trades something real away - cancellability, a new
file format, or a heuristic whose false-positive rate is unmeasured - and that is Adam's call rather
than one to make at the end of a long round. OB-108 sets out the three options and what each costs.

### AU-B9 - the route guard could not see a protecting signal

**Found by:** the second validation pass, proved with a probe. **Fixed:** `6b6e6bd4`.

`getActiveAccs()` walks the config commands of active edges. A protecting signal is not one - it is
driven separately, by occupancy. So a route could turn a platform's signal green with a train standing
at it, and nothing re-asserts it until the next occupancy change: a green aspect inviting a
hand-driven train into an occupied platform, for as long as the train stays there.

AU-A2 one step over, and invisible until AU-A2's guard existed to be tested.

### AU-B10 - the cycle scan read a list it was extending

**Found by:** the second validation pass. **Fixed:** `6b6e6bd4`.

The skip added in `6523a90b` asks whether a locomotive was already proved stuck for its own reasons.
Read against the live `unreachable` list, an entry that the same loop had just added answered that too -
so one proved cycle silently suppressed another. Demonstrated with three locomotives in two genuine
mutual cycles: the second pair went unnamed, the operator repairs what was named, re-runs, and it is
still impossible.

Which is the same harm the skip was added to remove, pointing the other way. It reads a snapshot taken
before the loop now.

---

## C - worth doing, no urgency

### AU-C1 - the autonomy menu's download offer did not ask whether a station was there

Fixed by extracting `isCentralStationConnected()`, which the Layouts menu already computed inline after
two attempts. One predicate now, two callers.

### AU-C2 - `settleAbsentPages` marshalled one of its two dialogs

The absent-page question was carefully put on the event thread under a comment explaining why it had
to be; the unreadable-index message twelve lines above was not, and one caller genuinely runs on a
worker.

### AU-C3 - the crop chooser could never find the current icon

`new File(l.getLocalImageURL())` on a `file:` URI produces a path that never exists, so the chooser
never opened where the current icon lives - a silent no-op since the day it was written. FR-022's own
new code got this right in two places and wrong in this third one, one method away.

### AU-C4 - `readDirectionMap` drops a key it cannot parse, where the string map round-tripped it

Recorded rather than fixed: nothing this application has ever written is affected, since the suffix has
been appended since the collection was created. It reaches only a hand-edited file. A genuine
behaviour change at the read boundary, and worth knowing before somebody edits a setup by hand.

### AU-C5 - `deletePage`'s configuration loop strips a `#` that `isOnPage` already handles

Pre-existing, and it breaks a page named `Yard #2`: that page's placements, homes, termini and lengths
survive its deletion. `rekeyOne` has the identical pre-strip. Not fixed this round.

### AU-C6 - the block index landed at one of the planner's three "one piece of track" sites

`sameTrackAs` has it; `canEnter` and `sharesSection` do not, and `canEnter`'s comment still asserts the
opposite of the new one. Unreachable today, because `GraphReducer` only makes feedback tiles into
Points, so every builder-emitted Point carries a sensor. Two comments in one file now contradict each
other about the same fact.

### AU-C7 - a third copy of "would Start be refused"

The diagram strip decided from its own cached error count, while `canStartAutonomy` and
`refuseAutonomyStartWhileBroken` both ask the session live. Any path that changed the findings without
firing the strip's listener would have left it offering Start where the guard refuses - the OB-057
shape at the surface that was fixed for it. Fixed in `8117b2a7`.

### AU-C8 - a stated mutation that did not fail its own test

`testADeletedPageIsNotReportedAsMerelyMissing` named `pageNamesWhenWritten.values().remove(page)` as
the line it guards. Removing that line leaves all nineteen tests green - the comment beside it in the
source concedes as much, because `sharedFields` rebuilds the file's `"pages"` map from the live index.
It is the index removal that does the work.

That is the defect class this repository keeps finding, inside the fix for an instance of it. Corrected
in `2ac2ee5e`, and the corrected mutation was run.

### AU-C9 - a behaviour change with no guard

Deleting the turn-around facing carve-out left every class green: nothing anywhere referenced
`FACING_IMPOSSIBLE`. It has a test now, and it had to go in the class that parses the real fixture -
the session's own tests use a synthetic three-square page with no berth on it, so the case cannot be
built there at all.

**Still unguarded, and recorded rather than hidden:** the staging audit's exemption changed from the
planner's occupancy to the railway's, and no test distinguishes the two. Reverting it fails nothing. It
is correct in direction and it can be undone silently.

### AU-C10 - three more of the twin shape

Swept for by pattern rather than by file, after AU-B1 showed the sweep was worth doing:

- `LocomotiveFunctionAssign` still did `new File(url)` on a value produced by `.toUri().toString()`
  twenty lines below, so its chooser never opened where the current icon is - the identical mistake
  fixed one class over.
- The backup offer still asked bare `getNetworkCommState`, which reports whether the last SYNC
  succeeded; a sync reads a local layout folder perfectly happily, so a simulated session calls itself
  connected. Adam found that once already, for a different offer.
- `canStartAutonomy`'s own javadoc claimed "requestStartAutonomy asks it", which it does not - the same
  false sentence removed from a comment forty lines away in the previous commit.

### AU-C12 - the Keyboard tab neither warns nor refuses

`TrainControlUI.UpdateSwitchState` calls `setAccessoryState` during a run with no autonomy check at
all. The diagram tile warns and lets you say OK; routes now refuse outright; the keyboard is silent.
Three surfaces, three different answers to one question. **Open** - it wants Adam's view on which of
the three answers is the right one, since making them agree means changing two of them.

### AU-C13 - `getAutonomySession()` is a lazy builder now reachable from a Swing listener

It parses every layout page, runs the caption migration - which writes to disk - and can put a dialog
on screen. `TrainControlUI` says so in a comment: *"The FIELD, never getAutonomySession()."* The strip
now reaches it from a `PropertyChangeListener` on the Start button's enabled state, and caches only on
success, so a layout whose session will not open re-attempts the whole build per property change.
**Open** - the correctness of asking the guard's own number is right and should not be undone; what it
wants is a cached accessor with invalidation.

### AU-C14 - my signal test made a sibling flaky

It took the feedback addresses `testAThrowWhileLockingReleasesTheTrack` uses, and a simulated dispatch
clears its feedback from a detached thread - so on a loaded machine the clear had not landed when the
sibling ran. Two failures in eighteen runs. Fixed in `6b6e6bd4`; six consecutive runs green.

### AU-C11 - `sharesSection` is AU-A1's shape, twelve lines above it, and is NOT changed

**Found by:** the validation pass, proved on Adam's own graph. **Deliberately open.**

The first of the two pairwise IMPOSSIBLE proofs asks whether two homes report the same feedback
address, which is the planner's conservative notion of one piece of track and not the railway's.
BottomMainC and BottomMainCTerm share feedback 4 with no block between them; homing two locomotives
there answers IMPOSSIBLE naming both.

By AU-A1's own thesis that is a false proof. It has not been changed, and the reasoning is written
where the method is: `canEnter` enforces the sensor rule deliberately and structurally, so the claim is
true of every arrangement this planner can reach - and removing it does not make a plan appear, it
turns an instant answer naming both locomotives into a search that burns its whole budget. Adam's
layout has eleven shared sensors.

It is the same decision as the sensor-versus-block divergence in `plannedOccupancy`, and the two should
move together or not at all. MT-187 asks it.

---

## D - not defects

### AU-D1 - the FR-013 stage two conversion itself

Swept for the hazard the ticket named - a wrongly-typed key reaching an `Object`-taking method - with
the static type of every argument established. **No live instance.** All ten deleted string-keyed
duplicates had every call site rewritten, and the two behaviours that existed only for the string form
are gone rather than half-gone.

### AU-D2 - the FR-001 consolidation

The runtime path is byte-for-byte the rule it replaced; the planner's copy is the old one plus a block
term that can only make it stricter; the test oracle moved from a weaker hand-written rule to the live
one. A strict improvement, traced call site by call site.

### AU-D3 - the three arrival-sides consolidations

Ordering preserved, null-reducer safety preserved, and the one behavioural difference proven
unreachable by a sweep. Behaviour preservation was shown by A/B - all 70 findings and all 56 squares'
facings byte-identical either way - rather than by "the tests still pass".

### AU-D4 - the crop delete gate, attacked

Refused a plain sibling outside the folder, a `..` walk out of it, an `http:` URL, a `file://` URL with
an authority, and a file one level deeper. Not defeasible on location. The remaining hole is ownership,
recorded as a window-pass C finding.

### AU-D8 - the old code, attacked and found sound

The whole-application pass verified rather than read: all 101 speeds and both directions round-tripped
through the protocol layer, 64 function frames, foreign-UID rejection. Consist lock ordering is
strictly two-tier with no member-to-head path anywhere, so no deadlock cycle is constructible. Every
store the operator accumulates - locomotive database, UI state, layout pages, the companion store, icon
crops, Central Station downloads - goes through the atomic write; the raw writes left are user-chosen
exports. The eleven files an earlier review had never read hold nothing new.

Its verdict is worth quoting because it is calibration: *"nearly every hazard I went hunting for was
already found by a prior pass, fixed, and annotated in place with the reasoning."*

### AU-D6 - the Escape-as-yes sweep, whole tree

All eleven files containing a confirmation dialog, about sixty-five sites: every destructive branch
requires an explicit yes, and `confirmOnEventThread` defaults to no. The six in `TrainControlUI` were
the last of them.

### AU-D7 - the identity-hash serialisation sweep

`excludedLocs` is the only collection whose iteration order depends on identity hashes and which
reaches a file. Points, edges and timetable paths hash by name; store and JSON keys are strings. The
fix in `d03da1ba` is complete rather than the first of several.

### AU-D5 - "Show Inactive Labels", swept for a fourth decider

Three deciders, all asking the shared rule, and `updateStationLabels` writes text and colour but never
visibility. No fourth. Clean.

---

## What these passes missed, and what caught it instead

**The harness.** None of the three looked at `tools/`, and two defects there had each been hiding a
real problem for two days:

- `battery.sh` decided "green" by grepping `Failures: 0`, so a class whose `@BeforeClass` threw -
  13 tests, **0 passed, 13 skipped** - was counted among the green in three separate runs and three
  commit messages.
- That class had been skipping since the test fixture was separated from the live layout, on a
  hardcoded coordinate. Nothing said a word.

Both were found by pulling on a loose thread rather than by review, which is an argument for the loose
threads getting the same attention as the diffs.

**The live layout.** The suite was rewriting Adam's own `cs2_sample_layout` on every battery run. The
cause was mundane - a set of locomotives iterated in identity-hash order, so an unchanged save produced
a different file - but the *exposure* is not fixed: two test classes start the real window, which loads
whatever the saved UI state names. `tools/battery.sh` and `regression.testTheGoldenLayoutHoldsTogether`
now watch for it at two scopes.

**A fourth over-claim in the OB-085 scan,** found by rereading it after the review pass had signed off:
the cycle argument rests on both trains actually parking, so a train already proved unreachable for its
own reasons should not make the other one impossible too. Fixed in `6523a90b`.

---

## Dispositions

| Finding | Disposition |
|---|---|
| AU-A1 | fixed, `4419d1cf` and `6523a90b`; counterexample kept as a fixture |
| AU-A2 | fixed, `7e2c6f81`, and corrected in `6b6e6bd4` - see AU-A3 |
| AU-A3 | fixed, `6b6e6bd4` |
| AU-A2 | fixed, `4419d1cf` |
| AU-B1 | fixed, `4419d1cf` |
| AU-B2 | fixed, `4419d1cf` |
| AU-B3 | fixed, `4419d1cf` |
| AU-B4 | fixed, `4419d1cf` |
| AU-B5 | fixed, `4419d1cf` |
| AU-B6 | fixed, `4419d1cf` |
| AU-C1 | fixed, `4419d1cf` |
| AU-C2 | fixed, `4419d1cf` |
| AU-C3 | fixed, `4419d1cf` |
| AU-B7 | fixed, `8117b2a7` |
| AU-B8 | filed as OB-108 - three remedies, each trading something real away |
| AU-C7 | fixed, `8117b2a7` |
| AU-B9 | fixed, `6b6e6bd4` |
| AU-B10 | fixed, `6b6e6bd4` |
| AU-C12 | open - three surfaces disagree; making them agree changes two of them |
| AU-C13 | open - the fix is right, the cost wants a cached accessor |
| AU-C14 | fixed, `6b6e6bd4` |
| AU-C8 | fixed, `2ac2ee5e` |
| AU-C9 | fixed, `2ac2ee5e`; the audit exemption remains unguarded and says so |
| AU-C10 | fixed, `2ac2ee5e` |
| AU-C11 | **open, deliberately - Adam's decision, and the same one MT-187 asks** |
| AU-C4 | open - recorded, unreachable from anything this application writes |
| AU-C5 | open - pre-existing, needs a page name containing `#` |
| AU-C6 | open - unreachable today; the contradictory comments are the live half |
| AU-D1 to AU-D8 | not defects |

---

## What this round says about how it went

Worth writing down, because it is the only part of this document that will still be useful when every
finding above has been forgotten.

**Three of the four A findings were made in this session, by me, as or beside the fix for a finding of
the same shape.** An impossibility proof built out of a heuristic that fails safe only in the other
role. A collection missing one line that its twin, written the same day, ends with. A comment asserting
that a guard had always known something it had never asked. And then AU-A3, which is the sharpest of
them: the fix for the round's most serious defect introduced a worse one, was carefully written, was
mutation-proven, and would have shipped if nobody had been asked to attack it specifically.

**The reviews that found the most were the ones told to look differently, not to look again.** The
three axis passes over the diff found real defects in the code they were pointed at. The pass told to
start from what previous reviews said they had NOT covered found the worst defect in the round, in code
that had not been touched for weeks and where both sides of the seam were individually correct. The
pass told to attack the fixes found that one of them was worse than the bug.

**Four defects were caught by running a stated mutation rather than by writing one.** In each case the
javadoc claimed a mutation that did not actually fail the test: the hourglass sand count, the first
version of the FR-018 prune test, the delete-page record line, and the route guard's own first test. A
mutation claim is a testable statement, and it is worth testing.

**Two harness defects had each been hiding a real one for two days.** A battery that called a class
green when it had run nothing, and a test suite quietly rewriting the operator's own railway. Neither
was in any reviewer's scope, and both were found by pulling on a loose thread. That is an argument for
the loose threads getting the same attention as the diffs.

