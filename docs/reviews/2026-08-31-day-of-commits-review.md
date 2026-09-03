# The last day of commits, read as a hostile reader

**Status:** open

**Prefix for citing these findings elsewhere:** `DAY`

**Reviewed:** every commit from `308b1396` (2026-08-30 00:14) to `e4c94ac9` (2026-08-31 02:33) - 42
commits, +3,003/-760 lines under `src/`, plus all eight message bundles. Read at `e4c94ac9`, tagged
`v3_0_0_rc4`, on 2026-08-31. Nothing was compiled and nothing was run: this pass is reading and
grepping only, and every finding below says what would confirm or refute it.

**What this pass was looking for.** Not whether the day's commits are reasonable - they are, and their
messages argue for themselves at length. What it looked for is what they BROKE or LEFT HALF DONE, and
in particular the pattern this project records more than any other: **a rule applied at one call site
and not at its twins.** Six of the nine findings are that shape, and the two most serious are both
last night's own fixes - MT-149 and MT-165 - carried to some of their call sites and not to all. Two
findings (B3, C5) are older asymmetries that the day's changes made load-bearing; they are marked as
out of range where they appear.

**The one to read first is A1.** MT-165 taught `HomeStaging.atHome` that the copies of a split square
are one piece of track. It did not teach `Layout.claimHome`'s injectivity guard the same thing, and
that guard is the only thing standing between the operator and a home configuration that cannot be
satisfied. On Adam's own railway - ten station squares with a block, including the main-line platforms
- an ordinary sequence of gestures now produces `IMPOSSIBLE` naming two locomotives, on track that is
fine.

---

## A - high

### A1 - MT-165 broke claimHome's injectivity, so two trains can claim one platform and Return Home refuses everything

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading; the reachability trace is step-by-step below and needs no runtime state |
| **Where** | `src/org/traincontrol/automation/Layout.java:1080`, against `src/org/traincontrol/automation/HomeStaging.java:1589` |

`claimHome`'s own javadoc states the invariant the whole feature rests on:

> First claim wins, and a station can be claimed only once, so the map stays injective - two
> locomotives can never want the same station, which would make returning home unsatisfiable by
> construction.

The guard that enforces it is one line, `Layout.java:1080`:

```java
// Station already spoken for: this locomotive is a free agent
if (this.homeStations.containsValue(p)) return;
```

`containsValue` compares **Points**. Until last night that was the same question as "is this square
spoken for", because the line above it - `if (p.getBlock() != null) return;` - meant a square emitted
as several Points could never hold a home at all. `66c96736` removed that line. The injectivity guard
was not widened with it, and a square is now several Points.

`HomeStaging.atHome` (1589) was added in the same commit and does ask the right question:

```java
return home.getBlock() != null && home.getBlock().equals(where.getBlock());
```

So the two halves of the same fix disagree: `atHome` says the copies of a square are one place, and
`claimHome` says they are several.

**The trace, entirely from ordinary gestures.** Take `BottomMainA`, one of the ten split station
squares the commit message names, emitted as copies `X1` and `X2` sharing a block and an s88, both
active stations.

1. Loco `A` is standing on `X1` when the graph loads. `rebuildHomeStations` calls
   `claimHome(A, X1)`; homes = `{A -> X1}`.
2. Autonomy runs. `A` departs and finishes somewhere else. `X1` and `X2` are now empty.
3. Autonomy is stopped. The operator hand-places loco `B` on that same platform. `moveLocomotive`
   names one Point, and the diagram's right-click menu deliberately enumerates the copies so the
   operator picks one (`TrainControlUI.java:5571`: *"the menu enumerates copies precisely to pick
   one"*). If it names `X2`, `clearBlockExcept(X2)` finds the square empty and `claimHome(B, X2)` is
   reached with `homeStations.containsKey(B)` false and `homeStations.containsValue(X2)` false - `X1`
   is the stored value, not `X2`. Homes are now `{A -> X1, B -> X2}`: **two locomotives homed on one
   platform.** Landing on the same copy `A` used is the only case the existing guard catches, and
   which copy a placement lands on is decided by facing, not by the operator's intent.
4. Return Home. `HomeStaging` skips `B` (it is standing on its own home) and does not skip `A`. The
   pairwise goal scan then asks `sharesSection(X1, X2)` - both active, same s88, different Points -
   which is **true**, the both-already-parked exemption does not apply because `A` is away, and both
   are added to `unreachable`.
5. The operator gets `autolayout.ui.errorCannotReachHome`: *"These locomotives cannot reach their home
   station at all: A, B. Check the track between them and where they started."* The whole staging run
   is refused, and every other train that only needed driving to the next platform stays where it is.

**The message sends them to look at track that is fine, and the state really is unsatisfiable** -
these are not the same defect. Even with the `sharesSection` scan removed, `misplaced()` can never
reach zero: `A` counts as home only when it stands somewhere in that block, and `B` is standing there.
So this is not merely a false proof; the model now lets a person build a configuration `claimHome` was
written to make impossible.

**It does not survive a reload, and that is the one piece of luck here.** Positional homes are derived
fresh from the placements at every load, and the ASSIGNED route to the same state is closed:
`parseAuto` at `Layout.java:7035` drops a `home` naming a split square and logs
`autolayout.errorHomeSquareIsSeveralPoints`. So the damage is confined to a session, and reloading the
configuration clears it. That is why this is A1 and not something worse.

**The precondition that made `sharesSection` safe was destroyed by the same commit, and its javadoc
still asserts it.** `HomeStaging.java:1541-1546`, written on 2026-08-30, argues at length that this
scan is safe on Adam's railway and ends: *"a home on a square that is several Points is refused by
`whyNotAHome` anyway. So the case is real and general, and his layout is not an instance of it."*
`whyNotAHome` still refuses an ASSIGNED home there. `claimHome` no longer refuses a POSITIONAL one,
and the `homes` map `HomeStaging` reads is `layout.getHomeStations()`, which holds both kinds. His
layout is an instance of it now.

**Severity.** Graded A rather than B because the visible result is the feature MT-165 was filed to fix
going dark again, on the same ten squares, after gestures nobody would think twice about - and because
what is created is an invalid model state rather than only a wrong message. Nothing moves wrongly and
nothing is lost from disk, which is the argument for B; I have put it at A and left the argument here.

**How to confirm or refute.** In `test/core/testHomeStaging.java`, build a layout with one square
emitted as two Points sharing `setBlock("b")` and `setS88("4")`, both destinations and active, plus a
third plain station `Y`:

```
layout.moveLocomotive("A", "X1", false);       // homes A -> X1
layout.moveLocomotive("A", "Y", false);        // A leaves; homeStations keeps A -> X1
layout.moveLocomotive("B", "X2", false);       // the second claim on the same square
```

Then print `layout.getHomeStations()` - two entries whose values return the same `getBlock()` is the
defect - and `HomeStaging.snapshot(layout).plan()`, which should come back `IMPOSSIBLE` naming both.
The control that keeps the test honest is the same sequence with `X` a plain single-Point square,
which must give one home and a satisfiable plan.

**The fix is the same shape as the rest of MT-165**: ask the block, not the Point. Something along the
lines of "already claimed if any existing home shares this square's block", with `getBlock() == null`
falling back to the current `containsValue`. Whatever form it takes, the `sharesSection` javadoc at
`HomeStaging.java:1541` needs its safety argument rewritten, because it is now false as written.

---

## B - medium

### B1 - MT-149's timetable repaint sits behind two early returns the rename doors compensate for, and it was not given to them

**FIXED 2026-09-03** (re-found as `SVN-B12`, and re-derived by pass 4 of the release review).

Both rename doors call `repaintTimetable()` now.  The shape is worth recording: `MT-149` was that a
rename did not reach the timetable on screen, the fix went to the GUARD inside `repaintTimetable` - it
was keyed on a hash a rename cannot move - and neither door was made to CALL it.  So the guard was
right, and nothing asked it.

Pinned by the rule that already watched the same two blocks for the station labels
(`testLocomotiveIdentityPropagates`), extended to the timetable and mutation-confirmed by taking the
call out of the second door.

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |
| **Where** | `src/org/traincontrol/gui/TrainControlUI.java:3908`, against `:3838`, `:3860`, `:16528` and `:22212` |

`3f507b4d` put `repaintTimetable()` at `TrainControlUI.java:3908`, inside the `invokeLater` at the end
of `repairAutonomyLocomotive`. That block is reachable only past two early returns in the same method:

- `:3838` - `if (session == null) { ... repairLocomotiveOnDisk(...); return; }`
- `:3860` - `if (!session.exists()) return;`

Both are about the **companion setup file**, not about whether there is a timetable. A timetable lives
on `Layout` and can exist with no `setup.json` at all - a legacy `autonomy.json` load, or a timetable
captured during a run in a session where the diagram setup has never been saved.

The giveaway is that the two rename doors already know this. `:16528-16529` and `:22212-22213` each
call `updateVisiblePoints()` and `repaintAutoLocList(false)` **themselves**, under
`if (this.model.hasAutoLayout())` - precisely because OB-081 taught them that the repair method's
redraws do not reach every case. Neither of them calls `repaintTimetable()`. The comment at `:16524`
even states the rule the new call breaks: *"Every other door that changes which locomotive stands
where already does this."*

So on a layout with autonomy loaded and no diagram-autonomy setup, renaming a locomotive still leaves
the timetable on screen naming a locomotive that no longer exists - which is Adam's original MT-149
report, on the branch the fix does not cover. The DATA is right; only the drawing is stale, exactly as
`3f507b4d` describes.

This is not reachable on Adam's own machine as it stands - `cs2_sample_layout/config/autonomy/setup.json`
exists - which is why it survived his re-run and why it is B rather than A.

**How to confirm.** `test/ui/testARenameReachesTheTimetableOnScreen.java` already exercises the fixed
path. Add the same assertion with the companion session absent: build the UI with a layout that has a
timetable but no `setup.json` on disk, rename the locomotive through the same door the test uses, and
assert the table's row text. Or, without a test: put a breakpoint on `:3908` and rename a locomotive on
a folder with no `config/autonomy/` directory - it is not hit.

**The fix is where the other two redraws are**: `repaintTimetable()` beside `updateVisiblePoints()` in
both rename doors (and in `deleteLoc` - see C2), rather than inside the setup-file repair.

### B2 - `atHome` was taught to five comparisons and not to the two launch-pad ones

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading that the comparisons were not converted; reachability needs execution |
| **Where** | `src/org/traincontrol/automation/HomeStaging.java:199` and `:839` |

`66c96736` converted five `home.equals(where)` comparisons to `atHome(...)`: lines 393, 477, 545, 739
and 1627. Two comparisons of the same shape were left as `equals`:

**`:199`**, in the launch-pad pruning at construction:

```java
boolean standingOnIt = entry.getKey().equals(occupancy.get(entry.getValue()));
...
if (!assigned && !standingOnIt && launchPads.contains(entry.getValue().getName())) it.remove();
```

`occupancy.get(home)` asks the exact copy. A locomotive whose positional home is copy 1 of a split
square, standing on copy 2 of the same square, reads as "not standing on it" - and if copy 1 has no
incoming edges, the home is dropped and the train becomes a free agent. `atHome` says that train IS
home; this line says it has left. The failure mode is precisely MT-165's own symptom - a train with no
home and `NO_HOMES` - restored in a narrower case.

**`:839`**, in the A\* expansion:

```java
if (this.launchPads.contains(at.getName()) && (ownHome == null || ownHome.equals(at)))
```

The comment above it explains why a locomotive standing on a launch pad must be pinned there: *"a pad
has no incoming edges, so the move can never be planner-undone: the hand-staging the pad represents
would be destroyed permanently, silently."* A train standing on a different copy of its own home
square fails `ownHome.equals(at)`, so it is not pinned, and the expansion is free to relocate it off a
square nothing can re-enter.

Both are the same argument the rest of the fix makes, at two sites the sweep missed. Neither is
reachable without a launch-pad copy of a split square, which is why this is B and not A - Adam's graph
has nineteen launch pads, and whether any of them is a copy of a multi-Point square is exactly what I
could not check without running.

**How to confirm.** Print, over the derived graph:
`for (Point p : layout.getPoints()) if (p.getBlock() != null && layout.getIncomingEdges(p).isEmpty()) System.out.println(p.getName() + " " + p.getBlock());`
Any output at all makes both sites reachable. If it is empty, both are traps for the next graph rather
than live defects, and should be fixed as such.

### B3 - the diagram editor offers a home the loader silently drops, and the ruling behind that rests on a measurement the same day contradicts

| | |
|---|---|
| **Disposition** | open. The refusal itself is Adam's 2026-08-25 ruling; what is raised is the guard/affordance mismatch and the measurement the ruling was decided against |
| **Confidence** | the mismatch and the contradiction are both confirmed by reading; which count describes HEAD's graph needs execution |
| **Where** | `src/org/traincontrol/gui/AutonomyEditorPanel.java:3367` and `:3402`, `src/org/traincontrol/automationui/AutonomySession.java:3521`, against `src/org/traincontrol/automation/Layout.java:7035` |

**The button offers what the loader refuses.** The split-square home rule is enforced at three places:
`Layout.setHomeLocomotive` throws (`:1192`), `whyNotAHome` returns the refusal key
(`HomeStaging.java:1236`), and `parseAuto` logs and drops it (`Layout.java:7035`). The diagram editor's
home menu reaches none of the first two: `AutonomyEditorPanel:3367` calls `AutonomySession.setHome`
(`:3521`), which is `setPointProperty(tile, "home", locomotive)` and asks nothing. The one check the
panel makes, `mayRestHere` (`:3402`), deliberately filters `whyNotAHome`'s answer down to the rest half:

```java
return !"autolayout.errorHomeCannotRestHere".equals(HomeStaging.whyNotAHome(loc, point));
```

- discarding the split-square refusal by design (LD-9), on the reasoning that "the model throws for
it". The model door it means is `setHomeLocomotive`, which this path never calls.

So on any of the ten platforms named below, the operator sets a home from the menu, the editor accepts
it, `AutonomyBuilder.homeCopy` (`:590`) picks a copy for it with thirty lines of care, `:923` emits it
- and `parseAuto` throws it away on the next load with a log line. The home is set in the setup, shown
as set in the editor, and never reaches the railway. This is the pattern the house rule names outright:
**the button that offers an action must ask the guard's own predicate.** It asks a strict subset of it,
and the operator finds out from a log line, at a moment unconnected to the click.

It also means `homeCopy` is doing careful work whose result is always discarded, which is worth knowing
before somebody reasons from it.

**And the measurement the ruling rests on is contradicted by the same day's other measurement.**
`whyNotAHome`:

> Measured before it was written: on Adam's own layout this refuses ONE square of fifty-seven, and he
> has no homes assigned at all, so nothing existing is invalidated.

`claimHome`, written last night, about the same railway:

> TEN of his thirty-six station squares carry a block - BottomMainA, BottomMainB, BottomMainC,
> BottomInner, TopMainR1, TopMainR2 and Tunnel among them, which are the main-line platforms trains
> actually stand on.

One of fifty-seven and ten of thirty-six cannot both describe the graph at `e4c94ac9`. The
fifty-seven sentence dates from `5a9d57a6` (2026-08-25), before two "New graph state" commits, so the
likely reading is that it is stale - I could not derive the graph without running, so I claim only
that the surviving justification for the rule is the smaller of two numbers that disagree tenfold.
"Nothing existing is invalidated" reads very differently at ten main-line platforms than at one square.

Both halves of the ruling's remaining argument have also been answered since it was made: *"is the
train home?"* has one answer now, because `atHome` gives it one, and *"there is no way to know which
copy they meant"* is what `homeCopy` decides. Whether to keep the refusal is Adam's. What should not
stand either way is a rule the one door that produces the state never asks.

**How to confirm.** For the mismatch: in the diagram editor, right-click a station square that is drawn
as more than one graph Point (any of the ten named above), set a home locomotive on it - the menu
accepts it - then reload the configuration and look for
`autolayout.errorHomeSquareIsSeveralPoints` in the log and for the home having vanished. For the counts: print
`layout.getPoints().stream().filter(p -> p.getBlock() != null).map(Point::getBlock).distinct().count()`
and the same restricted to `p.isDestination()`, over the derived graph for the real layout. Two
numbers, one statement, and both comments can be made to agree.

**Origin note.** The editor door and `mayRestHere`'s filter both predate this range (`76f4013d`
2026-08-23 and `b75876fa` 2026-08-25). They are raised here because MT-165 changed the answer to the
question the ruling settles, on ten of Adam's squares rather than one.

---

## C - low

### C1 - OB-159 made `liftAboveLabels` unnecessary, and it was left in doing only its harm

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading that the lift no longer serves its purpose; the residual harm is stated by the code's own comment |
| **Where** | `src/org/traincontrol/gui/LayoutLabel.java:1018` and `:1081`, against `src/org/traincontrol/gui/LayoutGrid.java:675` |

`liftAboveLabels` exists for one reason, given in its javadoc: *"Adam, looking at the locomotive icon:
'make sure it renders on top of the S88's.'"* It answers that by pushing the tile to component index 0
while a train is moving on it, then handing the front back to the `StationCaption`s.

OB-159 (`6afe6390`) replaced that mechanism entirely. `newDiagramContainer` paints its children and
then asks every `LayoutLabel` for `paintTrainOverCaptions`, so **the locomotive now lands over every
sibling regardless of z-order**. The lift buys the train icon nothing.

What it still does is what OB-117 was filed about. A tile is opaque; lifted to index 0 it paints out
whatever overlaps it, and `keepCaptionsInFront` rescues only `StationCaption`s. The address labels are
plain `JLabel`s - `LayoutGrid.java:1345` builds them with `new JLabel()`, against `new StationCaption()`
at `:907` - so a sensor's number that overlaps a square is blanked for as long as a train is moving on
it. `LayoutLabel.java:1174` says exactly this: *"Address labels get no such rescue."*

So the day left a mechanism whose benefit was superseded and whose cost was not, plus a container
reorder and a full-parent repaint on every train start and stop.

**How to confirm.** Comment out the body of `liftAboveLabels` and run `ui.testDiagramLooksRight` -
`testTheTrainIconDoesNotPaintOutACaption` (`:1024`) and the OB-159 container test (`:2077`, which
builds a `LayoutGrid.newDiagramContainer()` out of plain components) are the two that would notice. If
both still pass, the lift is dead weight. Visually: put a train on a square whose s88 address label
overlaps it, start autonomy, and see whether the number disappears.

Note that DOC-B11 in `2026-08-28-documentation-review.md` raised the javadoc's contradiction on this
method. This is a different claim - not that the comment is wrong, but that OB-159 has since removed
the method's reason to exist.

### C2 - `deleteLoc` did not get the redraws both rename doors have

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |
| **Where** | `src/org/traincontrol/gui/TrainControlUI.java:17338-17345`, against `:16504`/`:16528` and `:22199`/`:22212` |

`Layout.locDeleted` clears `p.setLocomotive(null)` and `p.setHomeLoc(null)` on every point, and
`MarklinControlStation.deleteLoc` now also strips the deleted locomotive out of every route
(`:2986-2996`, added in this range). The UI door does:

```java
this.model.deleteLoc(value);
clearCopyTarget();  repaintLoc();  repaintMappings();  selector.refreshLocSelectorList();
if (this.model.hasAutoLayout()) { locDeleted(l); repaintAutoLocListFull(); refreshUI(); }
```

Both rename doors additionally call `this.refreshRouteList()` and `this.updateVisiblePoints()`. The
second is the substantive one: OB-081 established that station captions on the track diagram are
written only by `updateVisiblePoints`, and that `refreshUI()` redraws the autonomy overlay and not the
captions. So after deleting a locomotive that was standing at a station, the caption goes on naming it.
`refreshRouteList` may well show nothing different - the route table renders `Route` objects and I did
not establish that it reads commands - so treat that half as precautionary symmetry rather than a
proven defect.

**How to confirm.** Place a locomotive on a station, delete it from the database, and look at the
station caption without touching anything else. Or assert on `updateVisiblePoints` being called, in the
style of `testARenameReachesTheTimetableOnScreen`.

### C3 - RC-A11's graceful stop fires from the two manual dispatch doors, where autonomy may never have been running

| | |
|---|---|
| **Disposition** | open |
| **Confidence** | confirmed by reading |
| **Where** | `src/org/traincontrol/automation/Layout.java:4828`, reached from `src/org/traincontrol/gui/AutoLocomotiveStatus.java:1027` and `src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:313` |

`d210fdf0` added `stopLocomotives()` and `control.logf("autolayout.errorRunStoppedByFailure", ...)` to
the `RuntimeException` catch in `executePath`. The comment reasons entirely about a RUN - *"every other
train still running"*, *"the run is stopped, not abandoned"*. `executePath` has two other callers, both
manual: the commands panel and the diagram's right-click menu, and both are usable with autonomy
stopped.

Two consequences, the second small and the first worth a decision:

- With autonomy running, a `RuntimeException` in a hand-dispatched path now stops the whole run. That
  is arguably right - the reasoning about abandoned locked track applies identically - but it is a
  behaviour change the commit describes only in terms of the run's own locomotives, and it means one
  right-click can end a session.
- With autonomy NOT running, the user is told *"Autonomy has stopped itself so the railway can be
  parked and started again"* about something that was never started. `stopLocomotives()` is a no-op
  there, so only the message is wrong - but it is wrong in a way that will send somebody to press Start.

**How to confirm.** Read the two call sites; no execution needed for the reachability. For the message,
dispatch a path by hand with autonomy stopped and force a throw (an accessory removed between
`isPathClear` and `configureEdge` is the natural one).

**The smaller fix** is to gate both the stop and the message on `this.running` at the moment of the
catch, so a manual dispatch failure says what actually happened.

### C4 - "setUnoccupied on an edge that is already clear does nothing" stopped being true when occupancy started counting

| | |
|---|---|
| **Disposition** | open - the comment, not the code |
| **Confidence** | confirmed by reading; not reachable today |
| **Where** | `src/org/traincontrol/automation/Layout.java:2754-2761`, against `src/org/traincontrol/automation/Edge.java:466-475` |

`configureAndLockPath` counts an edge as taken before taking it, and justifies it:

> Counting first can only ever release an edge that was never taken, and setUnoccupied on an edge that
> is already clear does nothing.

`Edge.setUnoccupied` calls `release()`, which floors at zero for this edge - and then cascades a
release to **every lock edge**, unconditionally. Those may be held by another locomotive. So releasing
an edge that was never taken is not free: it silently takes a claim off the throat that edge would have
locked, which is the exact unbalancing the RC-A9 counter was introduced to prevent.

This is not reachable today: the only way `edgesLocked` outruns the claims is `setOccupied` itself
throwing, and it is a `synchronized` increment and a loop. `Point.reserve` and `configureEdge`, which
can fail, both run after the increment and are correctly covered. So the code is right and the
reasoning under it is not - and this file's own history is of a reader extending a comment's argument
into a place it does not hold.

**How to confirm.** Read `Edge.setUnoccupied` at `:466`. No execution needed.

### C5 - `nameEverything` does not rebuild the running layout; `promptName` does, and says why

| | |
|---|---|
| **Disposition** | open. **Out of this pass's range** - the asymmetry dates from `37009269` (2026-08-22), not from the day reviewed. Recorded because I could find no review or MT covering it |
| **Confidence** | the asymmetry is confirmed by reading; the blank-caption symptom needs execution |
| **Where** | `src/org/traincontrol/gui/AutonomyEditorPanel.java:6386`, against `:3949` |

`promptName` ends with `parentWindow().rebuildRunningLayoutFromSetup()` under a fifteen-line comment
(OB-034) explaining that without it the setup's station index maps the square to the new name while the
running layout still holds the old one, so *"the caption finds nothing and draws nothing"*.

`nameEverything` calls the identical `session.setPointName(tile, name.trim())` and the identical
`placeLabelFor` guard - and its own comment at `:6377` says it is written the same way deliberately,
*"because two rename paths that ask different questions is how one of them ends up wrong"* - then ends
at `:6386` with `selection.clear(); refresh();` and no rebuild. `rebuildRunningLayoutFromSetup` has
exactly two callers in `src/`, both in `promptName`'s neighbourhood.

`nameEverything` visits only unnamed squares, so the stale name is the reducer's generated coordinate
name rather than a user's - the mismatch is the same shape either way.

**How to confirm.** With a running layout loaded and the autonomy editor open, use "name everything" on
a page with unnamed stations and look at the station captions in the VIEWER window (the editor hides
them in autonomy mode). If they are blank until the editor is closed, this is OB-034 through the other
door.

---

## D - not defects

These are the checks that came back clean. Several of them were opened as suspected A-grade
regressions, which is why they are written out rather than merely listed.

### D1 - the eight-language rename swept all eight, completely

Checked mechanically at `e4c94ac9`:

- All eight bundles hold exactly **1,864** keys, and the key SETS are byte-identical - `diff` of the
  sorted key lists is empty for `de`, `fr`, `es`, `it`, `nl`, `da` and `pl` against `messages.properties`.
- Zero non-ASCII bytes in any of the eight.
- `{0}`/`{1}`/... placeholder sets match `messages.properties` for every key in every language - no
  arity drift.
- Of the twenty keys the day removed (`autolayout.ui.tooltip.reopenGraph`, the graph-window family,
  `autosetup.ui.warnDoubleCurveSensor`, `ui.main.tooltip.hideReversingStations` and the rest),
  **none** is still referenced from any `.java` or `.form` file.
- The dynamic key families are complete. `autolayout.ui.pathPreference<NAME>` and
  `autolayout.ui.tooltip.pathPreference<NAME>` exist in all eight bundles for all ten `PathPreference`
  constants, including the two added yesterday (`RANDOM_ANY_STATION`, `BALANCED_PRIORITY`), and
  `ROUTING_ORDER` at `TrainControlUI.java:8276` covers all ten with no duplicates - so
  `I18n.t` cannot be handed a key that is not there from that path.
- Every string literal passed to `I18n.t(...)` in `src/` resolves, except the five prefixes that are
  concatenated with an enum name (`autolayout.ui.pathPreference`, `...tooltip.pathPreference`,
  `autosetup.ui.facing`, `autosetup.ui.side`, `route.kind.`) and the javadoc example
  `error.invalidLogin`.

Given the brief flagged this rename as the likeliest place for a missing key, that is worth stating
plainly: there isn't one.

### D2 - no hand-written code has drifted into a GUI Builder block

The `algorithmType` / `jLabel2` addition and the `reopenGraphButton` removal both went through the
`.form`, and the generated blocks match it exactly. `TrainControlUI.form` declares 429 named
components; `GEN-BEGIN:variables` declares 429 fields; `GEN-BEGIN:initComponents` assigns 429
identifiers; the three sets are identical, in both directions. Every `handler=` in the form has a
`GEN-FIRST:event_` method and vice versa - `reopenGraphButtonActionPerformed` was removed from all
three places together. `LayoutEditor.java`/`.form` likewise, 13/13/13; none of its 676 changed lines
falls inside either GEN region.

The two hand-written pieces are correctly placed outside: `findRouteMenuItem`
(`TrainControlUI.java:24534`, 458 lines above `GEN-BEGIN:variables`) and the `algorithmType`
listener wiring in `mountRoutingLogic` (`:8203`, ~1,200 lines before `GEN-BEGIN:initComponents`). The
only non-generated-looking constructs inside `initComponents` are two lines of the `autoRouteList` cell
renderer, which the `.form` itself stores as a `Connection code=` block and re-emits verbatim.

### D3 - OB-164's double-release guard does not strand locks in atomic mode

This was opened as a suspected A. `executePath` at `Layout.java:5258` adds an edge to `clearedEdges`
**before** the `if (this.atomicRoutes) { pending.remove(); continue; }` branch, so in atomic mode edges
enter that set without ever being released. If `unlockPath`'s new `alreadyGivenUp` test ran in atomic
mode, every edge a train's tail had passed would stay locked for the rest of the session.

It does not. `unlockPath` (`:3096-3200`) branches on `atomicRoutes` first, and the atomic branch calls
`e.setUnoccupied()` unconditionally; the `alreadyGivenUp` and `givenUp` tests are both inside the
non-atomic `else`. In non-atomic mode every edge added to `clearedEdges` is released two lines later by
`path.get(waiting[0]).setUnoccupied()`, so the set and the releases correspond exactly. Withdrawn.

The one residual note, not a finding: a path containing the same `Edge` twice would be released once
and skipped twice, because `clearedEdges` is a `Set`. I could not construct such a path - the search
marks visited - so I am recording it rather than raising it.

### D4 - OB-159's three-layer painting reaches every diagram

`TileOverlay.paint` no longer draws the train at all, so any `LayoutLabel` outside a
`newDiagramContainer` would show no locomotive. There is no such label: `LayoutGrid.java:775` is the
only assignment to `container`, and it is `newDiagramContainer()` unconditionally - the `popup` branch
beside it is commented out. `LayoutGrid` is the only creator of grid `LayoutLabel`s (`:838`, `:873`),
and the one other `new LayoutLabel` in `src/` (`LayoutEditor.java:798`) builds palette tiles, which
carry no autonomy overlay. Captions and address labels are added to the same container
(`:1317`, `:1418`), so `paintChildren`'s loop sees them and correctly skips them.

### D5 - the `takingPath` Set-to-Map conversion is complete

All thirteen use sites converted: one `put` (`:2749`), eight `remove`, one `containsKey` (`:4954`), one
`keySet()` in `trainsUnderway` (`:2083`), and the new `entrySet()` read in `getActiveAccs` (`:897`). No
site still treats it as a `Set`. The union in `getActiveAccs` is also correctly ordered against the
lock loop: `setOccupied` runs before `configureEdge`, so an edge whose accessories have been thrown
always reads `isLockHeld` true by the time the guard asks.

### D6 - MT-149's self-eviction skip is exact

`if (l.equals(p.getCurrentLocomotive())) continue;` relies on `MarklinLocomotive.equals` being
`this == other` (`:1015`), which it is - deliberately, and documented. So the skip exempts only the
object asked about, and two DIFFERENT locomotives sharing an address are still swept, which is the
behaviour that makes the sweep worth having. `isSimultaneousMultiUnitCompatible` has exactly one caller
in `src/` - this sweep - so there is no twin to fix.

### D7 - the `tellAutonomy` overloads reach the right callers

`deleteSelection()` / `delete(label)` (both `true`) are called only from the keyboard Delete
(`LayoutEditor.java:6606`, `:6610`) and the right-click menu (`LayoutEditorRightclickMenu.java:140`,
`:385`) - all plain deletes. `false` is passed by `cutSelection` (`:3152`) and by the four bulk row and
column movers (`:2012`, `:2038`, `:2086`, `:2110`). Every caller matches its javadoc.

### D8 - OB-157's border cache has no unrecorded writer

`applyBorder`'s premise is that every path that sets a tile's border either goes through it or calls
`forgetBorderState`. Sweeping every `setBorder(` in `LayoutEditor.java`: the two tile paths that set
directly - the drag grip (`:2515`) and the caption drop mark being put back (`:3931`) - both call
`forgetBorderState` immediately after. The others are the palette tile at creation (`:813`, which has
no record to stale), two drag ghosts, and three pieces of window chrome. The cache key also carries
everything `restingBorder` reads: `palette`, `isAutonomyMode()` and `showGrid()`.

### D9 - the `AutonomyChecks` signature change reached both overloads and its one caller

`List<String>` to `Map<TileKey, String>` at `:362`, the convenience overload's
`Collections.<TileKey, String>emptyMap()` at `:341`, `checkRepeatedSensorPages` at `:779`, and the
single production caller `AutonomySession.java:3049`. No other caller exists.

### D10 - the routing-dropdown re-entrancy guard is correctly scoped

`restoringRoutingLogic` is read at the top of the listener, outside the `invokeLater`, which is the only
place it can be read - `setSelectedIndex` delivers the event synchronously and the flag is held only for
that call. `mountRoutingLogic` populates the combo BEFORE attaching the listener, so the
`removeAllItems`/`addItem` sequence cannot fire it, and `mountRoutingLogic` has one caller (the
constructor). All four writers of the selection reach `refreshRoutingLogicTooltip`.

---

## What this pass did not look at, and what it could not settle

**It did not run anything.** Not one line of this was executed, and the constraint was the point: five
reviewers sharing one Preferences store and one build directory is how the real railway was damaged on
2026-08-30. Everything above therefore carries a "how to confirm" that somebody running serially can
act on, and three findings (B2's reachability, B3's count, C5's symptom) are stated as unsettled
because the thing that would settle them is a print statement I was not allowed to run.

**It did not read the tests.** 13,404 lines were added in this range and roughly three quarters of them
are test code. I read the source diff and the surrounding methods; I did not audit whether the new
tests assert what their names claim, whether their preconditions can fail, or whether any of them is
vacuous. `2026-08-28-test-suite-review.md` is the pass for that and it is a different pass. In
particular I did not check the new `testALocomotiveDoesNotEvictItself` or the rewritten LD-8 test in
`testHomeStaging`, which are the tests for the two fixes A1 and B2 are about - if either is weaker than
it reads, that is a hole this pass leaves open.

**It did not verify anything against the derived graph.** `cs2_sample_layout/` holds the diagram and
the per-tile setup, not the graph `AutonomyBuilder` derives from it, and deriving that graph means
running code. So every claim about Adam's own railway - A1's "ten split station squares", B2's
launch-pad question, B3's contradiction - is reasoning from the code's own comments about that graph
rather than from the graph. Two of those comments contradict each other, which is B3.

**It did not exercise any UI.** C1, C2 and C5 are all claims about what gets repainted and when, derived
from reading which method writes which pixels. Every one of them could be wrong in the way UI findings
are usually wrong: something else repaints for an unrelated reason and the stale state is never seen.

**Areas of the day's diff that got a single reading and no adversarial pass:** the `LayoutEditor`
cut/paste origin tracking (LE-A1/A4/A5/A7 and RC-A1/A6 - six findings layered on one another over the
day, in a method whose correctness depends on the exact order of four reads and one write, and I traced
that order once); `TileGraph.checkTransparentTiles` and the OB-160 route-button rules; the
`RouteEditorFrame` and `MarklinControlStation` route-id work (OB-155); and `CS2File`'s
`pagesThatCouldNotBeRead` counter, where I checked the two consumers the commit names and did not go
looking for a third that also assumes a complete read.

**Two corrections I made to myself, recorded because they are the calibration data.** I twice wrote a
finding down and then had to take it apart:

- I claimed A1 had a variant that survived a reload, through an assigned home on a split square. It
  does not: `parseAuto` at `Layout.java:7035` drops exactly that, which I found only by grepping the
  message key rather than the method name. The finding was overstated by a whole severity band for
  about ten minutes.
- I nearly filed `AutonomyBuilder.homeCopy` as dead code with a story attached, on the strength of
  `whyNotAHome` refusing what it exists to arrange. Opening the door the operator actually uses showed
  the refusal is the part that never fires from the menu, which is B3.

Both are the same lesson the README states - *read the method that enforces the rule, not the one that
looks like it should* - and in both cases the enforcing method was in a file I had no reason to expect
it in. A reader who takes A1 or B3 further should assume there is a fourth door I did not open.
