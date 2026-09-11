# The three-week review: 2026-08-21 to 2026-09-11, weighted to the older half

**Status:** open

**Prefix for citing these findings elsewhere:** `W21`

Checked free before writing: no `W21` appears anywhere under `docs/`, `src/`, `test/`, and
`grep -c W21 docs/manual-tests/findings.tsv` is `0`.

**Commit reviewed:** `6fe9ec38` — "The five the battery caught: a placeholder made an unreadable folder
look like a success, and a killed test JVM left the operator's layout preference pointing at a
fixture", 2026-09-11 01:17. The brief named `d168f769`; that commit exists, carries the same subject
and a timestamp nineteen seconds earlier, and is **not** an ancestor of `HEAD` — it was amended. The
tree reviewed is the working tree at `6fe9ec38` on `autonomy-diagram-r0`.

**Date:** 2026-09-11.

---

## Method, and how the effort was split

The window is `git log --since=2026-08-21` — 786 commits, 84 changed files under `src/`. Eight review
documents dated 2026-09-09 and 2026-09-10 already cover the last week heavily, and a second reviewer
was working the same last ten days in parallel while this pass ran (their scratch directory,
`test/scratchT10/`, and their test JVMs were both live; one run of mine queued behind theirs).

So the effort went roughly:

- **~70% on 2026-08-21 to 2026-09-03**, chosen mechanically rather than by feel: for every file
  touched in the window I took `git log -1 --format=%ci -- <file>`, and worked down the list of files
  whose LAST commit is inside the older half. Those are the ones nobody has reread since. That
  produced the Central Station and locomotive layers (`MarklinFeedback` 08-22, `NetworkProxy` 08-24,
  `CSDetect` 08-27, `LocomotiveSelectorItem`/`LocomotivePlaceholder` 09-01,
  `LocomotiveFunctionAssign` 08-29), the autonomy-on-the-diagram files that have not moved since
  (`TilePorts` 08-23, `DiagramMonitor` 08-26, `TileOverlay` 08-30, `StationIndex`), and the
  right-click menus (`RightClickSelectorMenu` 08-29, `LayoutRightclickAutonomyMenu` via
  `RightClickMenuListener` 08-30).
- **~20% on the seams named in the brief** that span the whole window and are not file-scoped:
  manual routes against autonomy (`MarklinRoute.heldReason`, `LayoutLabel.aboutToClearProtection`,
  `Layout.moveLocomotive`), the companion store's registry, home staging, and the timetable.
- **~10% on whole-tree scans** written for this pass: a self-comparing-assertion scan and a
  no-assertion-`@Test` scan over all of `test/`.

**Execution.** Five of the nine findings below rest on a measurement. I wrote one throwaway TestNG
class, `test/core/testW21Probe.java`, ran it twice through
`TC_SCRATCH=... bash docs/tools/one.sh core.testW21Probe`, and deleted it. Every number quoted below
with `W21-PROBE` in front of it is stdout from that runner. Each probe carries a control, or
preconditions that are asserted before the claim is made, so a green control proves the harness could
have seen the thing it says it did not see. The working tree was restored: `git diff --stat -- src
test docs build.xml` is empty and `git status --porcelain` shows only the three `cs2_sample_layout`
lines Adam already had, the other reviewer's `test/scratchT10/`, and this document.

I did not run `battery.sh`, did not build, did not kill anything, and wrote nothing under
`cs2_sample_layout/`.

---

## A — high

None. Nothing found in this pass writes the wrong thing to the railway or silently loses data at a
severity the last week's documents have not already claimed. The three `B` items are the closest, and
`W21-B3` is the one I would look at first if `A` were being reconsidered.

---

## B — medium

| id | title | disposition |
|---|---|---|
| W21-B1 | `MarklinFeedback.setState` tells nobody, so the route editor's capture cannot see a sensor set from the diagram or in simulation | open |
| W21-B2 | when the CAN socket closes, `sendMessage` brings transmission back and nothing ever brings reception back | open |
| W21-B3 | the diagram's "Place locomotive" writes AND SAVES a placement the railway has just refused | open |

### W21-B1 — `MarklinFeedback.setState` tells nobody, and its twin `parseMessage` does

`src/org/traincontrol/marklin/MarklinFeedback.java:120-137`, against
`src/org/traincontrol/marklin/MarklinFeedback.java:83-112`.

A feedback module has two ways to change state, and only one of them announces it:

- `parseMessage` — a sensor arriving over the wire — calls `this.network.feedbackChanged(getName(),
  state == 1)` at line 102, under the comment *"And anything watching for one, which today is the
  route editor's capture"*.
- `setState` — every other way — calls `_setState(val)` and `updateTiles()` and nothing else. Inside
  an `isDebug()` block it still carries this line, at 133:

  ```java
  // If we want to capture route commands in the future, we could call a method in the model here
  ```

  That is no longer true in either half. The method exists (`ViewListener.feedbackChanged` /
  `MarklinControlStation.feedbackChanged:2745`), and the sibling twenty lines above calls it.

`setState` is not a simulation-only path. Its callers are:

- `src/org/traincontrol/base/LayoutDiagramComponent.java:172,176` — **clicking an s88 tile on the
  track diagram**, which toggles the sensor;
- `src/org/traincontrol/marklin/MarklinControlStation.java:2363` (`setFeedbackState`), which is what
  `Layout.simAnnounce`/`simClearBehind` drive in simulation;
- `src/org/traincontrol/marklin/MarklinControlStation.java:463`, restoring state at start-up.

`TrainControlUI.feedbackChanged:4417` is the only consumer, and what it does is append an `s88`
condition row while the route editor is capturing into conditions. So: with the route editor
capturing, a sensor **clicked on the diagram** or **fired by simulation** does nothing, and the
symptom is the one `View.feedbackChanged`'s own javadoc names — the capture simply does not record
it. Over the wire it works. That is precisely the shape the brief calls this codebase's most repeated
mistake.

`setState` is also the only one of the two that is not `synchronized` (`synchronized public final void
parseMessage`).

**How I know.** Executed. A throwaway class installed a recording `View` on the model built by
`MarklinControlStation.init(null, true, false, false, true)` (reflection on the `view` field), then
created a module and set its state:

```
--- core.testW21Probe
W21-PROBE accepted=true name=60123 state=true calls=[]
FAIL testSetStateNotifies
  setState did not tell the model, so the route editor's capture cannot see a sensor set by a
  diagram click or by simulation: [] expected [1] but found [0]
PASS testTheRecorderWorks
```

`testTheRecorderWorks` is the control and it passed: calling `model.feedbackChanged("control", true)`
directly put exactly one entry in the recorder, so the recorder was installed and the plumbing works.
The preconditions of the subject are asserted and passed too — `accepted=true` (the module really is
in the database, which memory says is the way this test hangs if you get it wrong) and `state=true`
(the state really did change). Only the notification is missing.

**What I would change.** Call `this.network.feedbackChanged(this.getName(), val)` from `setState`,
unconditionally — not inside the `isDebug()` block, which is where the stale comment sits — and
delete that comment, replacing it with the rule: *both doors announce, because the capture cannot tell
a clicked sensor from a wired one and should not.* Then check `synchronized` on `setState` against
`parseMessage`, which has it.

### W21-B2 — the CAN listener never comes back, and `sendMessage`'s reopen restores only transmission

`src/org/traincontrol/marklin/udp/NetworkProxy.java:112-115` (`stopListening`), `:172-180`
(`sendMessage`'s reopen), `:138-144` (the only place `ReadMessages` is ever started).

`NetworkProxy` was hardened so that one transient `receive()` error could not leave TrainControl
*"able to transmit but deaf - no feedback, no accessory echoes, no power state changes, and path
integrity validation failing every path"* — the class javadoc and `testNetworkProxy`'s own header both
say so. Half of that is still true.

The reader stops for one reason, the socket being closed (`while (!socket.isClosed())`, line 239, and
the `break` at 265). `sendMessage` then reopens the socket — `this.socket = openReceiveSocket()` at
177, correctly ahead of the `send()`. Nothing re-creates `ReadMessages`: it is constructed once, in
`setModel`. So the end state after a close plus a send is an OPEN socket with nothing listening on it,
which is the deaf-but-transmitting state the class exists to prevent, made permanent.

Two smaller things in the same place, which are why it reads as covered:

- `private ReadMessages reader;` at line 103 carries the comment *"The listener thread, kept so that
  stopListening can end it"*. `stopListening` does not mention it, and the field is read nowhere in
  the file. (Filed separately as `W21-C6`.)
- `testNetworkProxy.testSendReopensAClosedSocket` (priority 3) asserts the socket reopened and the
  send succeeded — and never re-checks `countReaderThreads()`, which its own priority-2 test has just
  proved is `0`. The helper is right there in the class.

`stopListening`'s only production caller is `MarklinControlStation.shutdown()`, which is terminal, so
reaching this in the application needs the socket to be closed by something else — the reader's own
terminal exit after a fault. I am not claiming a routine occurrence; I am claiming that the recovery
path the class advertises recovers one direction of two, and that nothing measures the other.

**How I know.** Executed, in the same throwaway class, against the live proxy taken out of the model
by reflection:

```
W21-PROBE readers before close = 1
W21-PROBE readers after close = 0
W21-PROBE sent=true socketClosed=false readers after reopen = 0
FAIL testTheReaderComesBackAfterAReopen
  transmission came back and reception did not: the socket is open again and nothing is listening
  on it expected [1] but found [0]
```

All three preconditions are asserted and passed: one listener before the close, zero after it (so the
close really was terminal and the test is not measuring a race it lost), `sent=true` and
`socketClosed=false` after the send (so the reopen really happened).

**What I would change.** Give `NetworkProxy` one method that owns "the socket is live and something is
listening on it", and have `sendMessage`'s reopen call it — restarting `ReadMessages` when the field
holds a dead thread, using the `reader` field that already exists for it. Then add the missing
assertion to `testNetworkProxy.testSendReopensAClosedSocket`; it is one line and the helper is in the
file. Separately, `stopListening` reads and closes `socket` with no lock while `sendMessage` is
`synchronized` and is the writer, so a send racing a stop can re-bind 15730 after the close check —
which is the exact thing `stopListening`'s javadoc says it exists to prevent.

### W21-B3 — the diagram's "Place locomotive" writes and SAVES a placement the railway refused

`src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:1082-1117` (`placeFacing`), reached from
`:1061-1074` (`placeSomewhereLegal`) and `:997-1050` (`placeableCopies`).

```java
private void placeFacing(String locName, String pointName, Side facing)
{
    if (locName == null) return;

    ui.getModel().getAutoLayout().moveLocomotive(locName, pointName, false);   // <- result discarded

    if (session != null)
    {
        session.placeLocomotive(station, locName);
    }

    if (facing != null && session != null)
    {
        session.setFacing(station, facing);
        ...
        AutonomyReport.show(ui, session.save());                               // <- and written to disk
    }
```

`Layout.moveLocomotive` (`src/org/traincontrol/automation/Layout.java:7017`) returns `false` and logs
rather than throwing, in four cases: `isRunning()`, an unknown locomotive, an unknown point, and
`!target.isDestination()`. `placeFacing` discards the answer and goes on to write the placement and
the facing into the setup and save it. So for any of those four refusals the file records a train on a
square the railway has just declined to put it on, and the running layout and the setup now disagree
in the direction that survives a restart: the next `parseAuto` builds the graph from the setup and
emits the train there.

Two ways in that I can name:

- **`placeableCopies()` deliberately returns non-destination copies.** Its own comment says so: *"Nothing
  open is not the same as nowhere to go… refusing to place one there would be a different message."*
  When no copy is a reachable destination it falls back to `shut` — the copies for which
  `copy.isDestination()` is explicitly false — and `placeSomewhereLegal` then picks one at random and
  hands it to `moveLocomotive`, which refuses exactly that. The menu item's own enablement asks
  `current.isDestination()` about the copy the user clicked, not about the copy the placement will
  use.
- **Autonomy started from another window between the popup opening and the item being clicked.** This
  is not hypothetical: `behaviour.md` §6a states it as a rule — *"Menu items are greyed when the popup
  opens and the action fires when it is clicked; starting autonomy from another window in between
  leaves a live item over a running railway. The refusal has to be in the method."* Here the refusal
  **is** in the method, `moveLocomotive` makes it correctly, and the caller writes the setup anyway.

**How I know.** Read, not executed — this one needs a window and a right-click, so it belongs in
`docs/manual-tests/tests.md` rather than in a unit test. What I verified by reading is every link in
the chain: `moveLocomotive`'s four `return result` arms at `Layout.java:7022-7086`; that `placeFacing`
has no `if` around its return; that `placeableCopies` can return a non-destination
(`LayoutRightclickAutonomyMenu.java:1040-1050`); and that `session.save()` is on the path
(`:1111`).

**What I would change.** `if (!ui.getModel().getAutoLayout().moveLocomotive(locName, pointName,
false)) return;` — the log line `moveLocomotive` already writes is the user-visible half. `AutonomySession
.moveOntoFacingCopy`'s javadoc is worth reading beside this: it says in as many words why
`moveLocomotive` is the wrong primitive for a square's copies (*"requires the target to be a
destination - which a split copy need not be"*), and this door is the one that still uses it.
`removeLocomotiveHere` (`:1263-1285`) has the same discarded-result shape; its failure direction is
the safer one, and I could not settle whether its missing `session.save()` is a second defect or is
covered by a rebuild — see `W21-D6`.

---

## C — low

| id | title | disposition |
|---|---|---|
| W21-C1 | `LocomotiveSelectorItem` renders `(char) -1` in a dialog; its twin one class over guards against exactly that | open |
| W21-C2 | the gateway ping is the one ping in `CSDetect` that is not retried, and it gates the whole scan | open |
| W21-C3 | `baseNameOf` never got `describe`'s arrival-suffix fallback, so the timetable can still print "(northbound)" | open |
| W21-C4 | `DiagramMonitor.invalidate()` writes `published` outside the monitor `refresh()` is synchronized for, and the caller's comment claims the opposite | open |
| W21-C5 | `paintRun` draws a chevron for an IDLE segment whose line it has just skipped; `isBlank()` was taught about that pair and this was not | open |
| W21-C6 | `NetworkProxy.reader`: "kept so that stopListening can end it" — `stopListening` never touches it and nothing reads it | open |
| W21-C7 | `testTheWindowAttachesItsRefreshCallback`'s first assertion still reads the raw source, which is the hole its own javadoc names | open |
| W21-C8 | localising the trigger-type combo moves a Momentary selection to Toggle; only a deferred repaint puts it back | open |
| W21-C9 | `GraphLocAssign` declares the missing arrival side a non-issue; `behaviour.md` §4 lists it as an open defect | open |

### W21-C1 — `(char) -1` in a message dialog, guarded in the twin and not here

`src/org/traincontrol/gui/LocomotiveSelectorItem.java:210-219`, against
`src/org/traincontrol/gui/RightClickSelectorMenu.java:26-30`.

`TrainControlUI.getKeyForCurrentButton()` (`TrainControlUI.java:1910-1920`) ends `return -1;` when no
button is selected. `LocomotiveSelectorItem` formats it straight into the "Click to Assign" dialog:

```java
I18n.f("loc.ui.messageClickToAssign",
    String.valueOf((char) this.selector.getUI().getKeyForCurrentButton().intValue()))
```

`loc.ui.messageClickToAssign` is *"Check "Click to Assign" to map this locomotive to the active button
({0})."*, so the user is told to map to button `￿`. The sibling got the guard and says why:

```java
// No current button means no key to render - (char) -1 painted as U+FFFF garbage.
// Without a target the item is meaningless, so it is omitted rather than disabled.
if (currentButtonKey != null && currentButtonKey != -1)
```

**How I know.** Read. `getKeyForCurrentButton`'s `return -1` is at `TrainControlUI.java:1920`; the
unguarded use is `LocomotiveSelectorItem.java:216`; the guarded one is `RightClickSelectorMenu.java:30`.

**What I would change.** Ask the same question the sibling asks, and give the dialog the other half of
the sentence — with no active button there is nothing to map to, so say that instead of naming a key.
While there: the sibling's `currentButtonKey != null` half can never be false, because
`getKeyForCurrentButton` returns `-1` and never `null`. A guard whose first clause is dead is what
made the second site look already covered.

### W21-C2 — the gateway ping is the one ping with no retry, and it gates the entire scan

`src/org/traincontrol/marklin/udp/CSDetect.java:149-151`.

```java
// Check for default gateway by sending a ping
if (isReachable(inetAddress.getHostAddress().substring(0, ...lastIndexOf('.')) + ".1"))
```

Every other ping in the class goes through `isReachable(host, PING_RETRY)` (`:79`, `PING_RETRY = 2`),
and the whole of the MT-060 javadoc at `:40-52` is about what one dropped reply costs: *"The ping was
retried and the web request was not, so a single timeout threw away a station that had just answered.
Three attempts turn a one-in-ten failure into a one-in-a-thousand one."* The gateway ping is the one
that did not get it, and it is upstream of everything: one lost reply makes `getLocalSubnet()` return
empty, `hasLocalSubnets()` false, and `MarklinControlStation.java:4101` tells the operator auto-detect
is not possible at all — a worse answer than the one MT-060 was raised about, from the same cause.

**How I know.** Read. The two overloads are at `CSDetect.java:167` (no retry) and `:180` (retry loop);
the gateway call at `:150` uses the first.

**What I would change.** `isReachable(gateway, PING_RETRY)`. It costs at most one extra ICMP round trip
per interface, once, before a scan that sends 254.

### W21-C3 — `baseNameOf` is `describe`'s twin and never got its fallback

`src/org/traincontrol/automationui/StationIndex.java:236-241`, against `:180-189` and `:206-230`.

`describe(Point)` answers from `baseByPoint` and, when the index has no entry, falls back to
`withoutArrivalSuffix(point.getName())` — written for the degraded case its own javadoc names, *"a
setup reloaded, a layout edited underneath a running graph"*, with the reason spelled out:
*""BottomMainA (northbound)" is not a place on anybody's railway."*

`baseNameOf(String)` answers from the same map and returns the raw internal name when it misses:

```java
String base = pointName == null ? null : baseByPoint.get(pointName);

return base == null ? pointName : base;
```

The timetable goes through the second one. `TrainControlUI.stationLabel:27378-27388` calls
`session.baseNameOf(point.getName())`, under a javadoc that is entirely about OB-102 — *"timetable
stations show (northbound) and (southbound) etc."* — and about why the suffix is noise there. So in
exactly the case `withoutArrivalSuffix` exists for, the timetable still prints the compass bearing.

**How I know.** Read. `test/core/testWhyStuck.java:477-497` covers `describe`; nothing covers
`baseNameOf`.

**What I would change.** Have `baseNameOf` end in `withoutArrivalSuffix(pointName)` rather than
`pointName`, and make `describe` call it, so there is one answer instead of two.

### W21-C4 — `invalidate()` writes `published` with no lock, and the caller's comment says that is what makes it safe

`src/org/traincontrol/automationui/DiagramMonitor.java:118-121`, against `:186-193`, and
`src/org/traincontrol/gui/DiagramMonitorDriver.java:296-307`.

`refresh()` is `synchronized`, and its javadoc says why: *"the compare-against-published is a check
then a set, and this is called from both the driver's timer thread and the event thread."*
`invalidate()` performs the set half of that same field with no lock at all:

```java
public void invalidate()
{
    published = Collections.emptyMap();
}
```

The threads really are different. `DiagramMonitorDriver` schedules `refreshIfDirty()` on a
`java.util.Timer` named `DiagramMonitor` (`:159-161`, called at `:239`), and `clear()` calls
`invalidate()` from inside `SwingUtilities.invokeLater` (`:291-307`). A tick already inside
`refresh()`, between `compute()` and `published = overlays`, will overwrite the invalidate — after
which the identical picture is suppressed as unchanged and the wiped tiles stay blank until something
moves, which is the outcome `invalidate()` exists to prevent.

The caller's comment asserts the opposite, and that is the part I am most sure about:

> doing it here rather than on the caller's thread is what stops a tick already in flight from writing
> its picture into that memory afterwards and going quiet.

Running on the event thread excludes nothing from the timer thread.

**How I know.** Read. `published` is `volatile` (`:71`), so visibility is fine and only the
check-then-set is not. I did not reproduce the race — the window is one `compute()` per `clear()` —
so this is "can happen", not "does happen", and it is filed at C for that reason. The false comment is
not conditional.

**What I would change.** `public synchronized void invalidate()`, and rewrite the driver's comment to
say what the `invokeLater` is actually for (ordering against `registry.clearOverlays()`, which is a
Swing call) rather than claiming an exclusion it does not provide.

### W21-C5 — an IDLE segment gets a chevron and no line

`src/org/traincontrol/automationui/TileOverlay.java:693-758`.

The line pass skips a segment whose colour is null:

```java
Color colour = colourOf(segment.getState());

if (colour == null) continue;          // colourOf returns null for IDLE (:760-769)
```

The chevron pass twenty lines below skips only `LOCKED` and a null `to`:

```java
if (segment.getState() == State.LOCKED || segment.getTo() == null) continue;
```

so an IDLE segment draws a black arrowhead floating over track with no line under it. `isBlank()`
(`:300-307`) was taught about precisely this pair and says so in its own comment — *"an overlay
carrying a line with no state reported itself blank and painted nothing while still forcing the
repaint. Nothing emits that pair today - `lay()` always sets a state - and the first thing that wants
a neutral line would have found it silently invisible."* The fix went into `isBlank()` and not into
`paintRun`.

**How I know.** Read, and I checked reachability rather than assuming it: the only `State.IDLE`
overlay constructed anywhere is `DiagramMonitor.java:411`, which passes `null` segments. So **nothing
reaches this today** — it is a trap for the next author, of the same class the SOP calls *"worth
fixing as traps for the next caller, not worth a changelog entry."* `testAutonomyDiagramMonitor.java:254-259`
asserts `assertFalse(quiet.isBlank(), ...)` and never renders, so it would not see it either.

**What I would change.** Add `|| colourOf(segment.getState()) == null` to the chevron loop's skip, so
the two passes agree about which segments exist.

### W21-C6 — `NetworkProxy.reader`: a comment describing a mechanism that is not there

`src/org/traincontrol/marklin/udp/NetworkProxy.java:102-115`.

```java
// The listener thread, kept so that stopListening can end it
private ReadMessages reader;
```

`stopListening` closes the socket and never mentions `reader`. The field is assigned once, at `:138`,
and read nowhere in the file. A reader who trusts the comment will believe the proxy can stop and
restart its listener, which is what `W21-B2` is about.

**How I know.** Read; `grep -n "reader" src/org/traincontrol/marklin/udp/NetworkProxy.java` shows the
declaration and the single assignment and nothing else.

**What I would change.** Fix it as part of `W21-B2` by making the field load-bearing, which is what
its comment already claims. Deleting the field instead would be the smaller change and the wrong one.

### W21-C7 — the first assertion of the callback guard still reads the raw source

`test/regression/testTheWindowAttachesItsRefreshCallback.java:87`.

```java
assertTrue(source.contains("AutonomyRefreshCallback.attach"), ...)
```

`source` is the raw file. Twenty lines down, the sibling assertion in the same method was hardened for
exactly this and explains itself at length:

> The other assertion in this test was hardened against exactly this — it searched for a method name
> that also appeared in the comment explaining why the call was there, so deleting the call left it
> green — and this one was left counting raw text.

That paragraph is describing the count, which now runs `withoutComments(source).split(...)`. The
`contains` above it was not brought along, and it is guarding the more important half: whether
`TrainControlUI` attaches the callback at all, without which *"the timetable and the locomotive
status panel redraw ONLY when the layout announces a path start or end."*

It passes today by luck, and I checked the luck: `grep -c AutonomyRefreshCallback
src/org/traincontrol/gui/TrainControlUI.java` is `1`, the call itself at `:3837`. The first comment
anybody writes mentioning it reopens the hole — which is word for word what this file's own javadoc
warns about, applied to one of its two assertions.

**How I know.** Read, plus the grep above.

**What I would change.** `withoutComments(source).contains("AutonomyRefreshCallback.attach")`. The
helper is in the same class. The `source.indexOf("private void repairAutonomyLocomotive")` on the next
line has the same shape and the same one-word fix.

### W21-C8 — localising the trigger-type combo moves a Momentary selection to Toggle

`src/org/traincontrol/gui/LocomotiveFunctionAssign.java:208-216`, against `:111-117` and `:511-530`.

The constructor selects the current function's trigger type first — `fNoItemStateChanged(null)` at
`:111` and `fNo.setSelectedIndex(functionIndex)` at `:114`, both of which run `updateFNumber`, which
ends in `functionTriggerType.setSelectedIndex(1)` for a momentary function — and then mutates the
combo's model underneath the selection:

```java
defaultModel.removeElementAt(0);
defaultModel.insertElementAt(I18n.t("function.ui.toggle"), 0);
defaultModel.removeElementAt(1);
defaultModel.insertElementAt(I18n.t("function.ui.momentary"), 1);
```

`DefaultComboBoxModel.removeElementAt` moves the selection when the element being removed *is* the
selected one, and the third statement removes exactly that for a momentary function. `applyButton`
then reads `functionTriggerType.getSelectedIndex()` (`:428-440`) and writes
`Locomotive.FUNCTION_TOGGLE`.

**How I know.** Executed, on the model alone, walking all four starting selections:

```
W21-PROBE combo start=0 (Toggle) -> index 0 (Umschalten)
W21-PROBE combo start=1 (Momentary) -> index 0 (Umschalten)
FAIL testComboLocalisationKeepsTheSelection
  the localisation moved the selection from 1 expected [1] but found [0]
```

Toggle survives by luck — it is moved to Momentary and back — and Momentary does not. (The probe
stopped at `start=1`; by the same walk, a timed type is never the removed element and is unaffected.)

**This is filed at C because I could not show it reaching the user, and the reason matters.** The
`SwingUtilities.invokeLater` block at `:180-206` ends with a second `fNoItemStateChanged(null)`, and
that runnable is queued during the constructor, so it runs after the mutation and re-derives the
trigger type from the locomotive — repairing it before the panel is interactive. So what is here is a
corrupted state with an accidental repair standing over it: move that deferred call, delete it, or let
anything throw out of the loop above it (`this.loc.getNumFnIcons()` in the loop condition is outside
the per-icon `try`), and every momentary function quietly becomes a toggle on Apply.

**What I would change.** Localise the model before the selection is made — the four lines belong above
`fNoItemStateChanged(null)` at `:111`, not at the end of the constructor. Or build the combo's model
localised in the first place, which is what every other combo in this file does.

### W21-C9 — `GraphLocAssign` calls the missing arrival side correct by design; `behaviour.md` calls it an open defect

`src/org/traincontrol/gui/GraphLocAssign.java:142-144`, against `docs/reference/behaviour.md:473-479`.

The source says:

> The arrival side is not written and does not need to be: `placeLocomotive` clears it when the
> occupant changes, and neither of these doors asks. Nothing recorded is the honest answer for a train
> nobody watched arrive.

`behaviour.md` §4, listing the cases where `arrivedFrom` is not known, says:

> and one that is **not** narrow and is a defect rather than a design: the **right-click "Place
> locomotive"** item and the **graph window's assign** both place a train without working the side out
> at all (`REV9-B2`, open). Only the diagram drag/paste door implements the rules above. A train put
> down by either of the other two lands with no tail recorded, so nothing behind it is blocked, while
> the identical placement by drag asks the question and blocks it.

These are the same two doors and they cannot both be right. Per the SOP this is Adam's to settle, but
the current state is the worse of the two: a reader who opens `GraphLocAssign` — which is the one file
that exists *because* the rule had to live in one place for both doors — is told there is nothing to
do here.

**How I know.** Read. The code matches the source comment: `commitAndRecord` (`:150-195`) writes
`placeLocomotive`, `setFacing` and `save()`, and never `setArrivedFrom`.
`LayoutRightclickAutonomyMenu.placeFacing` is the same. The door that does implement the rule is
`TrainControlUI.rememberPlacement:7380-7452`, which writes both the setup (`session.setArrivedFrom`)
and the live `Point`, and says why.

**What I would change.** Nothing in the code until Adam rules. In the meantime the comment at `:142`
should not assert a decision that `behaviour.md` records as an open defect — it should name `REV9-B2`
and say the question is open, which is the one case where citing a finding id is the whole point.

---

## D — not defects, clean checks, and what I could not settle

### W21-D1 — no assertion in `test/` compares an expression with itself

`E8-C7` found one (*"the Control+E test compares one expression with itself"*), so I swept for the
class rather than the instance. A script parsed every `assertEquals` / `assertSame` / `assertNotEquals`
/ `assertNotSame` in `test/`, split its arguments with a proper brace/string/char-aware scanner, and
compared the first two with whitespace removed.

**How I know.** Executed:

```
$ python selfassert.py test
0 hits
```

Clean. The script is in my scratch directory, not in the tree.

### W21-D2 — no `@Test` method that asserts nothing

The second flavour of "a guard that cannot fail". A script took every `@Test` annotation in `test/`,
brace-matched the method body that follows it, and reported the ones containing no `assert*(`,
`fail(`, `expectThrows` or `Assert.`. Eighteen hits; I opened all of them.

Every one is either a parser artefact (the `@Test` string inside a comment or a generated-source
literal in `testRouteInventory`, `testRoutePicking`, `testEveryTestIsInTheBattery`,
`testSwitchingToACentralStationLayout`) or a two-line delegate to a helper that does the asserting
(`testACurvedPlatformRecordsASideTheBuildUses` → `answersWithABuildSide` / `blocksTheRailBehind`;
`testAMovedTileCarriesItsSetup` → `roundTrip`; `testStationLabelsFollowMoves` → `check`;
`testTimetableOnDerivedGraph` → `runCaptureAndReplay`; `testDiagramLooksRight`,
`testTheDiagramRefreshDoesNotWaitOnTheRailway`). **How I know.** Executed the scan, read all eighteen.

### W21-D3 — `gridSideTowards` and `neighbour` agree, and the class knows they are two copies

`TileGraph.gridSideTowards:171-188` and `TileGraph.neighbour:1649-1659` are independent
implementations of the same grid arithmetic, and `gridSideTowards`' javadoc says it exists so there
would not be two: *"two copies of this would be two chances to disagree about which way is north."*
There are two. I checked all four sides in both and they agree (N = y−1, S = y+1, E = x+1, W = x−1),
and `sideTowardNeighbour:1482-1493` — the third asker — already documents the duplication and why the
answers cannot diverge. Not a defect; recorded because the javadoc reads as a claim that no second
copy exists.

### W21-D4 — `forgetSquares` cannot handle a `#`-suffixed configuration key and does not need to

`AutonomyCompanionStore.deletePage:2556-2568` strips a `#` suffix before deciding whether a
configuration's point key is on the page being deleted; `forgetSquares:3251-3260` removes
`points.remove(key.toString())` and would miss a suffixed key entirely. That is the exact shape
`DirectionMapKept`'s javadoc describes as having been silently dead for a long time, so I checked the
data rather than the code.

**How I know.** Executed, over every configuration in the tree:

```
test\operator_layout\...\configuration-Main.json      71 ['3 - Top Parking:18,15', '1 - Main:0,11', ...]
test\test_layout\...\configuration-Main.json          56 ['1 - Main:0,11', '2 - Bottom:16,5', ...]
test\test_layout_snapshot\...\configuration-Main.json 71 [...]
```

No key in any of them carries a `#`. The suffix belongs to `tileDirections` in the shared file, not to
a configuration's `points`. `deletePage`'s handling is defensive; `forgetSquares`' omission costs
nothing.

### W21-D5 — the three doors that ask "would this clear protection" all ask the same question

The brief's "two systems commanding one railway" seam. `MarklinRoute.heldReason:434-513`,
`LayoutLabel.aboutToClearProtection:1710-1743` and the switch keyboard all gate on
`hasAutoLayout() && isAutonomyRunning()` and all delegate the rule itself to
`Layout.protectsAnOccupiedSquare` / `Layout.clearsProtection`. `LayoutLabel` carries the note (REL-A1)
recording the round where it asked only half. No divergence left. **How I know.** Read all three.

### W21-D6 — not settled: whether `removeLocomotiveHere` losing its `session.save()` is a second defect

`LayoutRightclickAutonomyMenu.removeLocomotiveHere:1263-1285` changes the running layout and the
in-memory setup and never calls `session.save()`, while `placeFacing` on the same menu does
(`:1111`), and `GraphLocAssign:194` says in its own comment that *"Every other door that writes the
setup saves it: the paste door, the facing menu, placeFacing beside this one on the same menu."*

I could not establish whether something downstream saves it. `TrainControlUI:4525-4540` shows the
tile autonomy menu (an `AutonomyEditorPanel`) saving from its refresh callback, and `behaviour.md` §6a
says the diagram's right-click autonomy menu rebuilds after every gesture — but I did not trace
whether `removeLocomotiveHere`'s two repaints reach that callback. If they do not, a removal made from
the track diagram is lost at the next launch. Worth ten minutes from somebody who knows that wiring;
I am not filing it as a finding on a guess.

### W21-D7 — the orphaned javadoc in `GraphReducer` is inside an existing ratchet, not a new finding

`GraphReducer.java:680-687` holds `onwardSides`' javadoc immediately above `reachableTiles`' own, so
`onwardSides` (at `:839`) is undocumented. This is counted: `testJavadocsAreAttached` pins
`GraphReducer.java (2)` in `ORPHANS_BY_FILE` and caps the total at 92, deliberately as a ratchet
rather than a clean sheet. Not refiled. (Its `KEPT`-adjacent prose in
`testStoreCollectionsAreHandledEverywhere` says "eleven collections" over a list of twelve; likewise
not worth a finding.)

### W21-D8 — what I did not cover

- **`LayoutPageEdit` and the page-link re-aiming.** A read pass raised two candidates here — that the
  renamed or duplicated page file is written at `:158` before `writeIndexAndKeepLinksAimed` re-aims at
  `:286`, on a premise (`LayoutDiagram.java:568-574`) that is false once the write has already
  happened; and that `page.clear()` at `:155` empties the live page object so its own link tiles can
  no longer be re-aimed. That file was last touched 2026-09-10 and sits squarely inside the second
  reviewer's ten-day window and beside `N8-A1` / `FV3-A2` / `NSV-B3`, so I did not spend the pass
  re-deriving it. If it is not in their document, it is worth an hour.
- **`AutonomySession` (7,242 lines) and `AutonomyCompanionStore` (5,790) end to end.** I read the
  registry, the placement/facing/arrival writers, `renamePage`, `deletePage` and `forgetSquares`, and
  nothing else.
- **`AutonomyBuilder`, `AutonomyChecks`, `TileAnnotation`, `TilePorts`, `HomeStaging`'s search.** I read
  `HomeStaging.plan`, the `canRest` family and `plannedOccupancy`, and found nothing the 09-10
  documents have not already written up (`E8-B5`, `E8-C8`, `E8V-B3`).
- **Anything needing the real railway or a display.** Three of the nine findings above (`W21-B3`,
  `W21-C1`, `W21-C8`) would be pinned by a hands-on test rather than a unit test; none of them is in
  `docs/manual-tests/tests.md` today.
- **Live threading.** `W21-C4` is a race I reasoned about and did not reproduce.
