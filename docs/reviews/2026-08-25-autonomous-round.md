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
| AU-C4 | open - recorded, unreachable from anything this application writes |
| AU-C5 | open - pre-existing, needs a page name containing `#` |
| AU-C6 | open - unreachable today; the contradictory comments are the live half |
| AU-D1 to AU-D7 | not defects |
