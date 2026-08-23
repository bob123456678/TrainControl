# Uninformed review - 2026-08-23

**Prefix for citing this document: `UR`.**

**Scope as given:** `automationui/` (the diagram-to-graph model), `automation/` (the running railway),
and the diagram/autonomy half of the GUI - `AutonomyEditorPanel`, `LayoutGrid`, `LayoutLabel`,
`LayoutEditor`, `AutonomyViewerPanel`, and the autonomy-related parts of `TrainControlUI`.

**Deliberately uninformed.** I was told nothing about what has changed recently or what anyone
suspects, and I did not read the other reviews in this folder beyond one, skimmed for house style. So
some of this may already be recorded elsewhere under another prefix. Where a finding duplicates one of
those, the duplication is accidental and the second opinion is probably still worth something.

**Read-only.** Nothing was compiled and nothing was run. No `javac`, no TestNG, no application.

**Method, honestly.** I read `TilePorts`, `TileGraph`, `GraphReducer`, `AutonomyBuilder`,
`DiagramMonitor`, `TileOverlay`, `StationIndex`, `DiagramMonitorDriver` and parts of `AutonomyChecks`,
`AutonomySession`, `AutonomyCompanionStore`, `LayoutEditor` and `TrainControlUI` myself. Three
delegated readers covered `AutonomyCompanionStore` + `AutonomySession` in full, `Layout` + `Point` +
`Edge` in full, and the four GUI panels in full. **Every finding below was re-opened and re-verified by
me against the source before it was written down**, including the sibling code each rests on; the line
numbers and quotes are ones I looked at. Two of the readers' findings did not survive that check and
are recorded in section E as not-findings. `TileAnnotation`'s painting, `HomeStaging`, the CS2/CS3
parsers and the whole `test/` tree were not read.

**Confidence** is mine. CONFIRMED means I traced it end to end in the source. PLAUSIBLE means the
mechanism is certain but I could not prove the trigger from reading alone. Where the two differ - a
certain mechanism with an unproven trigger - the entry says so in as many words.

**The impression I formed.** This is careful code, and the comments are unusually honest: several of
them name the defect they were written to fix, and two of them name a defect that is *still there*
(`UR-5`, `UR-18`). The weakest area is not the graph derivation, which is the part that has clearly had
the most attention and where I found almost nothing - it is the **protecting-signal feature**, which is
the one part of this subsystem that commands real hardware for a safety reason. Four separate holes
(`UR-2`, `UR-4`, `UR-6`, and `UR-13`) all end the same way: a signal showing green over a platform that
has a train on it. Each is individually narrow; together they mean the feature cannot be relied on. The
second weakest is what happens to the setup when things are *removed* - a locomotive deleted, a tile
deleted, a page renamed outside TrainControl - which is consistently less well covered than what
happens when things are added or moved.

---

## Ranked

| # | Finding | Sev | Conf. |
|---|---|---|---|
| UR-1 | Escape on the "this accessory is on an active route" warning throws the turnout anyway | A | CONFIRMED |
| UR-2 | A hand-driven route never turns its destination platform's protecting signal red | A | CONFIRMED |
| UR-3 | `locDeleted` leaves the deleted locomotive standing on its Point, forever | A | CONFIRMED |
| UR-4 | The signal-aspect memo assumes it is the only thing that commands these signals; edge commands are not | A | CONFIRMED (mechanism), PLAUSIBLE (trigger) |
| UR-5 | The editor's page snapshot shares its lists with the live store, so undo cannot bring a signal pairing back - the class says so itself | B | CONFIRMED |
| UR-6 | A barred arrival copy carries no protecting signal, two lines under a comment saying "on every copy" | B | CONFIRMED |
| UR-7 | `excludedPages` is the one shared collection stored by page NAME, against the class's own rule | B | CONFIRMED (mechanism), PLAUSIBLE (trigger) |
| UR-8 | An empty page makes `LayoutGrid` throw a `NegativeArraySizeException` and poisons `LIVE` for the session | B | CONFIRMED (arithmetic), PLAUSIBLE (trigger) |
| UR-9 | Renaming or deleting a locomotive does not repair the autonomy configurations; `renameLoc`'s comment enumerates two places and there are three | B | CONFIRMED |
| UR-10 | `importBundle` structurally guarantees `pageIdConflicts` is empty, so a renumbered bundle lands on the wrong pages | B | CONFIRMED (mechanism), PLAUSIBLE (trigger) |
| UR-11 | A throw inside `configureAndLockPath`'s lock loop leaks every lock and leaves the locomotive on several Points | C | PLAUSIBLE |
| UR-12 | `reconcile` never prunes `stations` for a square that was never named | C | CONFIRMED |
| UR-13 | `reconcile` never checks `stationSignals` *values*, so a new accessory inherits a dead pairing | C | CONFIRMED |
| UR-14 | `load()` empties the store before a type-strict read, under a comment promising it does not | C | CONFIRMED (mechanism), unproven trigger |
| UR-15 | A caption label is never unregistered when its caption is removed, retaining the whole previous grid | C | CONFIRMED |
| UR-16 | A split copy is NAMED by the side it arrived at while its facing is computed from the route; on a curve they disagree | C | CONFIRMED |
| UR-17 | `hasItemsBesidesTitle` is dead code and the empty submenu it was written for is still shown on links | D | CONFIRMED |
| UR-18 | `GraphReducer.locationsOf` documents a portal rule the method does not implement | D | CONFIRMED |
| UR-19 | `AutonomyViewerPanel.populating` guards a listener that no longer exists | D | CONFIRMED |

---

## A - wrong behaviour on the layout

### UR-1 - Escape on the active-route warning throws the turnout anyway

**Where:** `src/org/traincontrol/gui/LayoutLabel.java:351-371`, and the same shape at `:315-336`.

```java
int choice = JOptionPane.showOptionDialog(
    tcUI, I18n.t("layout.ui.confirmAccessoryActiveRoute"), ...,
    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
    null, options, options[0]);

switch (choice)
{
    case 0: // ok
        break;
    case 1: // cancel
        return;
    default:
        break;
}
```

**What is wrong.** `showOptionDialog` returns `JOptionPane.CLOSED_OPTION`, which is `-1`, when the
dialog is dismissed with Escape or with the title-bar close box. `-1` is neither `0` nor `1`, so it
falls through `default: break;` and execution carries on to `lastClicked = ...;` and
`submitSwitching(... component.execSwitching())`.

**The consequence.** The operator right-clicks a turnout while autonomy is running, is told the
accessory is on an active route, presses Escape to back out - and the turnout is thrown, under a moving
train. This is the one dialog in this subsystem whose Cancel is a safety interlock, and it is the one
that treats dismissal as consent. `:315-336` is the same defect on the power-off confirmation, milder:
the switch is sent with track power off and without the power-on the user asked for.

Every other confirmation in these files is written the safe way - `if (answer != 0) return;` at
`AutonomyEditorPanel.java:1719`, `:5132`, `AutonomyViewerPanel.java:1268`, `:1020`. These two are the
outliers.

**What I would do.** `default: return;` at both sites. It is a two-character fix and there is no case
in which falling through is the wanted behaviour.

---

### UR-2 - A hand-driven route never protects the platform it arrives at

**Where:** `src/org/traincontrol/automation/Layout.java:4588-4606` (the guard),
`:1261-1265` (`isRunning`), `:3982-4004` (the ordering), `:2853` (`locomotiveThreads`),
`src/org/traincontrol/gui/AutoLocomotiveStatus.java:696-698` and
`src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java:203-213` (the call sites).

```java
// Layout.java:4600-4602
// isRunning rather than isAutoRunning: a train under a driving thread is being run whether or
// not the whole layout is, and its arrival still has to protect its platform.
if (!this.isRunning()) return;
```

```java
// Layout.java:1261-1265
public boolean isRunning()
{
    return this.running || !this.getActiveLocomotives().isEmpty()
        || this.locomotiveThreads.get() > 0;
}
```

**What is wrong.** The comment states the rule correctly and `isRunning()` cannot answer it at the
moment the signals would be commanded. All three of its terms are false during a hand-driven route:

- `running` is false - both semi-autonomous call sites are gated on `!isAutoRunning()`;
- `locomotiveThreads` is incremented only inside `runLocomotive` (`:2853`), and both call sites use a
  bare `new Thread(...)` rather than going through it, so the counter stays at zero;
- `activeLocomotives` is still empty, because `executePathInternal` registers the locomotive at
  `:4004`, **after** `configureAndLockPath` has returned at `:3982`.

So every `e.getEnd().reserve(loc)` inside the lock loop (`:2286`) calls
`Point.reserve` → `refreshProtectingSignal` (`Point.java:464-471`) and returns at the guard. The
platform is reserved with no signal command issued at all.

Afterwards, in atomic mode - the default - the destination's occupancy never changes again:
`unlockPath` clears the start at `i == 0` and the intermediate ends at `i < path.size() - 1`
(`:2555-2568`), deliberately leaving the locomotive on the last edge's end. Nothing else calls
`refreshProtectingSignal` for that Point, and `refreshOneSignal` is only reached through it.

This is not a race. It is deterministic for a hand-driven route run while nothing else is active.

**The consequence.** The operator pairs a signal to a station on the diagram and then drives a route to
that station from the right-click menu or the autonomy locomotive tab. The signal stays green
throughout the move and after the train has stopped on the platform - which is the exact situation the
feature exists to prevent. It corrects itself only when full autonomy is next started, at
`runLocomotives` → `refreshAllProtectingSignals` (`:1295`). The behaviour is also inconsistent in a way
that will make it hard to report: if some other train happens to be in `activeLocomotives` at that
instant, `isRunning()` is true and the identical gesture *does* protect the platform.

**What I would do.** Do not decide "is a train being run" from state that is written after the
decision. Either increment `locomotiveThreads` (or an equivalent) around `executePath` on the
semi-autonomous path, or register the locomotive in `activeLocomotives` before locking rather than
after. The second is a bigger change; the first is local and matches what the comment already claims is
true.

---

### UR-3 - A deleted locomotive is never taken off the Point it stands on

**Where:** `src/org/traincontrol/automation/Layout.java:718-744`, with
`src/org/traincontrol/gui/TrainControlUI.java:14524-14529` (the only caller).

```java
synchronized public void locDeleted(Locomotive l)
{
    if (l == null) return;

    this.locomotivesToRun.remove(l);
    this.activeLocomotives.remove(l);
    this.locomotiveMilestones.remove(l);

    for (Point p : this.getPoints())
    {
        p.removeExcludedLoc(l);

        if (l.getName().equals(p.getHomeLoc())) p.setHomeLoc(null);
    }

    this.homeStations.remove(l);
}
```

**What is wrong.** Every holder is swept except `Point.currentLoc` - the one that says where the train
is standing. Nothing else clears it: `TrainControlUI.deleteLoc` calls only `locDeleted`,
`repaintAutoLocListFull` and `refreshUI`, and the only writer of `currentLoc` is `Point.setLocomotive`,
which nothing on this path calls. This is the same omission the method's own comments describe having
already made twice, for exclusions and then for home assignments - one site fixed, the sibling missed.

**Two consequences, both user-visible.**

The platform stays permanently occupied by a locomotive that no longer exists.
`Point.getBlockLocomotive()`, `Edge.isOccupied`, `pickPath`'s `end.getBlockLocomotive() == null` filter
and the "why not" panel all still see it, so autonomy will never route a train to that station again,
and the explanation names a locomotive the user deleted. And, by `UR-2`'s route, its protecting signal
will read the platform as claimed forever.

Worse, it survives to disk. `Point.toJSON` (`Point.java:822`) writes `"loc": {"name": ...}`;
`TrainControlUI.saveState` and `resetAutonomySession` capture `getAutoLayout().toJSON()` into the
diagram configuration, and `AutonomySession.captureFromLayout` deliberately preserves the placement
name. On the next load, `Layout.fromJSON` reaches
`layout.invalidate(I18n.f("autolayout.errorLocomotiveNotInDatabase", ...))` at `:6090`, so `isValid()`
is false and `executePathInternal` refuses **every** path for **every** locomotive. The whole
configuration is dead until somebody hand-edits the JSON, and the error names a locomotive with nothing
connecting it to the deletion that caused it.

The delete itself is guarded against happening mid-run, so this is not a race - it is a cleanup
omission that bites on the next run and again on the next load.

**What I would do.** Add the sweep - `if (l.equals(p.getCurrentLocomotive())) p.setLocomotive(null);` -
to the loop that is already walking every Point. `clearLocomotiveExcept` is the existing shape for
this.

---

### UR-4 - The signal memo is a record of intent, and it is not the only thing commanding these signals

**Where:** `src/org/traincontrol/automation/Layout.java:4655-4680`, with
`src/org/traincontrol/automationui/TilePorts.java:262` and
`src/org/traincontrol/automationui/GraphReducer.java:918-950`.

```java
Boolean showing = this.signalAspects.get(accessory);

if (showing != null && showing == claimed) return;

this.signalAspects.put(accessory, claimed);

Accessory acc = this.control.getAccessoryByName(accessory);

if (acc == null) return;

acc.setState(claimed ? Accessory.accessorySetting.RED : Accessory.accessorySetting.GREEN);
```

**What is wrong.** `signalAspects` is a memo of what *this method* last commanded, and the comment above
it explains at length why it was moved off `Point` and onto the accessory - "one signal, one aspect".
But this method is not the only commander of that accessory. `TilePorts` gives `SIGNAL` a single state
carrying `{PRIMARY: GREEN}`:

```java
// TilePorts.java:262
signalled(componentType.SIGNAL, accessorySetting.GREEN, route(Side.E, Side.W));
```

so `GraphReducer.collectCommands` puts `signalName -> GREEN` into the commands of **every reduced edge
whose path crosses that signal tile**, and `configureEdge` sends it through `Accessory.setState` - the
same door. A protecting signal is, by definition, a signal drawn on the approach to the platform it
protects, so it is on an edge.

Once an edge command has driven the signal green, `signalAspects` still says `true` (red). The next
call to `refreshOneSignal` for that accessory takes the `showing == claimed` early return and sends
nothing. The signal stays green with a train on the platform - which is word for word the failure the
comment at `:4661-4666` says was fixed, reintroduced by a different route.

Two secondary problems in the same six lines, both failing in the same direction: the memo is written
**before** the command, so if `acc` is null or `acc.setState` throws (caught and logged at `:4682`) the
memo records an aspect that was never sent and no later refresh will retry; and the scan-compare-put-
command sequence is not atomic, while `executePathInternal:4134-4139` reaches
`Point.setLocomotive` holding `activeLocomotives` rather than the Layout monitor.

**Confidence.** The mechanism is CONFIRMED - I traced the `SIGNAL` port entry through `collectCommands`
into the emitted edge JSON. The trigger is PLAUSIBLE: it needs a path to be granted over the signal
tile while the protected platform is occupied, which requires either an approach guard sensor between
the signal and the platform (the codebase says elsewhere that a station and its approach guards
legitimately share a sensor), or a pairing the user made to a signal that is not strictly on that
platform's own approach - which the pairing UI allows, and which `refreshOneSignal`'s own comment
already contemplates for the two-stations-one-signal case.

**What I would do.** Stop trusting the memo as the record of the hardware. Compare against the
accessory's own state instead, which is the thing the rest of this file already does through
`Accessory.isConfirmedAt`; and move the memo write to after a successful command. As a cheaper
mitigation, call `refreshOneSignal` for any protecting signal named in a path's config commands, right
after `configureAndLockPath` finishes issuing them.

---

## B - the setup, and the diagram

### UR-5 - The editor's page snapshot shares its lists with the live store

**Where:** `src/org/traincontrol/automationui/AutonomyCompanionStore.java:1579` (capture),
`:1694-1704` (`onPage`), `:1833-1841` (the in-place mutation), `:1630` (restore), with
`src/org/traincontrol/gui/LayoutEditor.java:3795-3796` and `:3826-3852`.

The class states the defect itself, at `:1005-1006`:

> Deep copies on the way out, so that the snapshot cannot be changed underneath its holder by the
> editing that follows. **snapshotPage does not do this and should.**

**What is wrong.** `onPage` copies the *map* and shares the *value objects*:

```java
if (isOnPage(entry.getKey(), page)) out.put(entry.getKey(), entry.getValue());   // same List
```

`stationSignals` is the one collection whose values are mutable `List`s, and `forgetSquares` is the one
place that mutates them **in place**:

```java
Map.Entry<String, List<String>> pair = pairs.next();

pair.getValue().removeAll(squares);

if (pair.getValue().isEmpty()) pairs.remove();
```

Every other move helper in the file - `moveListValues` at `:1927`, `rekeyListValues` at `:2823` -
builds a new list and calls `entry.setValue(...)`, so they are safe. This one is not.

**The consequence.** `LayoutEditor.snapshotLayout` pushes `captionSnapshot()` before every edit. Drag a
tile onto the square that holds a station's protecting signal: `moveTiles` → `forgetSquares(landing,
byKey)` strips that signal square from the station's list - the same list object the snapshot is
holding. Press Ctrl+Z. The track comes back; the pairing does not. `getProtectingSignals` returns
empty, the platform is silently unprotected on real hardware, and the only trace is a NOTICE on the
findings list saying the station has no signal. If the list emptied entirely, the restore puts the key
back pointing at an empty list, which `readStringListMap` then drops on the next load.

`snapshotPage` also shares the configuration point `JSONObject`s at `:1600`. Nothing mutates a point
object in place during a diagram edit today, so that half is currently benign - but it is the same
hazard and it will not stay benign by itself.

**What I would do.** Deep-copy in `snapshotPage`, exactly as `snapshotSetup`'s javadoc already says it
should. `new ArrayList<>(entry.getValue())` for the list map and a fresh `JSONObject` for the point
objects.

---

### UR-6 - A barred arrival copy carries no protecting signal

**Where:** `src/org/traincontrol/automationui/AutonomyBuilder.java:798-807`, with `:358-365`
(`arrivalAllowed`) and `:779`.

```java
// The signals thrown to red while this platform is claimed.  On every copy, because
// the copies are one platform.
...
List<String> protecting = protectingSignals.get(point.getTile());

if (protecting != null && !protecting.isEmpty() && stops)
{
    json.put("protectingSignal", ...);
}
```

where `boolean stops = point.isStation() && arrivalAllowed(node);`

**What is wrong.** The comment says "on every copy". The code says "only on copies a train may stop
at". Those differ exactly when the user has barred an arrival side: the copy for the barred side is
emitted with `station:false` and, because of the `&& stops`, with no `protectingSignal` either.

`refreshOneSignal` decides whether a signal must be red by scanning every Point for one that names the
accessory in `getProtectingSignals()` (`Layout.java:4644-4653`). The barred copy names none, so a train
occupying it is invisible to the protection.

**The consequence.** A platform whose approach from one end has been barred - which is the natural way
to set up a terminal platform, and the `withBarredArrivals` javadoc says trains still run over it - has
a real occupancy state that its own signal cannot see. A train passing through on the barred approach,
or stopped there when a run is abandoned, leaves the signal green over an occupied platform.

Nothing warns. `AutonomyChecks` has `SIGNAL_GONE` and `NO_SIGNAL_PAIRED` but nothing about this
combination.

**What I would do.** Emit `protectingSignal` on every copy of the square, as the comment says, and gate
only `station`/`terminus` on `arrivalAllowed`. The protection is about the piece of rail, not about
whether a train is permitted to stop on it.

---

### UR-7 - `excludedPages` is stored by page name, alone among the shared collections

**Where:** `src/org/traincontrol/automationui/AutonomyCompanionStore.java:823` (write), `:2267`
(read), `:2283-2291` (the untranslate block), against the class's own rule at `:312-315` and `:332-334`.

```java
root.put("pointNames",      new JSONObject(translateKeys(pointNames, true)));
root.put("stations",        new JSONArray(translateSet(stations)));
...
root.put("excludedPages",   new JSONArray(excludedPages));          // raw names
root.put("disabledLinks",   new JSONArray(translateSet(disabledPortals)));
```

and on the way back in, `untranslate` is called for `pointNames`, `tileDirections`, `barredArrivals`,
`stationSignals`, `linkNames`, `portals`, `captions`, `stations` and `disabledPortals` - and not for
`excludedPages`.

The rule it breaks is stated at `:312-315`:

> Pages are keyed on disk by the id the Central Station gave them, not by their name. A name is what a
> user changes on a whim; ... so it survives the rename that would otherwise orphan every entry on that
> page at once.

**What is wrong / the consequence.** `renamePage` has no production caller - only two tests - and its
own comment says so ("Nothing renames a page today"). So every real page rename is an external one, in
the Central Station or by editing the gleisbild, which is precisely the case ids exist to survive.
After such a rename, everything tile-keyed comes back correctly translated onto the new name and the
page's *exclusion* does not, because the stored name matches no page any more. The page silently
rejoins autonomy, and the stale name sits in `excludedPages` forever because nothing prunes it.

`pageIdConflicts` cannot cover this: it only fires when the *old* name still exists somewhere
(`:2311`). On a layout where pages were excluded because they redraw another page's sensors - the case
`excludeRepeatedSensorPages` exists for - this reintroduces two Points for one sensor, which is exactly
the state that code says nothing downstream can resolve.

**Confidence.** Mechanism CONFIRMED by reading both sides. Trigger PLAUSIBLE: it needs an external page
rename, which I cannot exercise from a reading.

**What I would do.** Translate and untranslate `excludedPages` like the other nine.

---

### UR-8 - An empty page throws out of `LayoutGrid`'s constructor and poisons `LIVE`

**Where:** `src/org/traincontrol/gui/LayoutGrid.java:180-188`, `:201-206`, `:261`, `:147`;
`src/org/traincontrol/base/LayoutDiagram.java:189-238`.

```java
// LayoutDiagram.checkBounds
if (IGNORE_PADDING && !edit) { minx = sx; miny = sy; } else { minx = 0; miny = 0; }
maxx = 0;
maxy = 0;
// ... only lowered/raised for squares that hold a component
```

```java
// LayoutGrid
int width  = layout.getMaxx() - layout.getMinx() + 1;
...
width = width + 1;
...
grid = new LayoutLabel[width][height];
```

**What is wrong.** For a page with no components at all in non-edit mode the seeds survive, so on a
30-wide page `width = 0 - 30 + 1 + 1 = -28` and `:261` throws `NegativeArraySizeException`. A page with
even one component is fine, because the `x < minx` branch pulls `minx` down to it.

The knock-on is worse than the single failure. Lines `:180-188` register this grid in the static `LIVE`
map **before** `container` is assigned at `:216`:

```java
java.lang.ref.WeakReference<LayoutGrid> was = LIVE.put(parent, new java.lang.ref.WeakReference<>(this));
LayoutGrid outgoing = was == null ? null : was.get();
if (outgoing != null && outgoing != this) outgoing.discard();
```

So a constructor that throws at `:261` leaves a half-built grid registered with `container == null`,
and `discard()` at `:147` dereferences `container` unguarded. Whether the next grid built over that
panel NPEs depends on whether the weak reference has been collected - so the failure is intermittent
on top of being obscure. When it does happen, the track-diagram tab goes blank and stays blank, and
`updateVisiblePoints()` and `refreshAutonomyFindings()` never run again.

**Confidence.** The arithmetic is CONFIRMED. The trigger is PLAUSIBLE: `LayoutEditor.clear()` empties
the page and `saveChanges` has no emptiness guard, and closing the editor calls `setEdit(false)` →
`checkBounds()`, but I did not drive the UI to confirm a fully cleared page can be saved.

**What I would do.** Two independent fixes, and I would take both: normalise an empty page in
`checkBounds` (`minx = miny = maxx = maxy = 0`), and register into `LIVE` only after the constructor has
completed - or at minimum null-guard `container` in `discard()`.

---

### UR-9 - Renaming or deleting a locomotive does not repair the autonomy configurations

**Where:** `src/org/traincontrol/marklin/MarklinControlStation.java:2944-2946` (the comment), `:2858`
(`deleteLoc`), against `src/org/traincontrol/automationui/AutonomySession.java:1634-1636`
(`POINT_OPERATIONAL_KEYS`) and `Layout.java:6090`.

```java
// State held by NAME does still need repairing, and there are two such places - the routes
// below, and autonomy home assignments.
```

**What is wrong.** There are three. The autonomy *store* holds locomotive names too - `loc` on a
placement, `home`, and `excludedLocs` are all in `POINT_OPERATIONAL_KEYS` and all stored as text inside
the configuration JSON - and neither `renameLoc` nor `deleteLoc` touches it.

`captureFromLayout` launders the **active** configuration back from the running layout, so a rename is
repaired there *if* a capture happens. Inactive configurations are never touched at all, and a deleted
locomotive is not repaired even in the active one, because the running Layout still holds it by
reference (`UR-3`) and `toJSON` re-emits its name.

**The consequence.** Rename or delete a locomotive, then switch to a configuration that was not active
at the time. `parseAuto` invalidates the whole layout at `Layout.java:6090` and every path is refused
as "configuration is invalid", with an error naming a locomotive and nothing connecting it to the
rename. `AutonomySession.importLegacy` guards against exactly this for imports, and says so; the
ordinary edit path does not. `AutonomyChecks` has `checkDuplicateLocomotives` but no
"locomotive is not in the database" finding, so nothing catches it before it is fatal.

**What I would do.** Give the store a `locomotiveRenamed`/`locomotiveDeleted` that walks every
configuration, not only the active one, and call it from the two `MarklinControlStation` methods
alongside the route repair. Failing that, add the check to `AutonomyChecks` so it is visible before it
invalidates.

---

### UR-10 - `importBundle` can never detect a page renumber

**Where:** `src/org/traincontrol/automationui/AutonomyCompanionStore.java:902`, `:923-930`, `:2293-2315`.

```java
JSONObject merged = sharedFields();      // MY page ids and MY names
...
for (String inner : ((JSONObject) value).keySet())
{
    if (mine.has(inner)) continue;       // the exporter's name for an id I already have: dropped
    mine.put(inner, ((JSONObject) value).get(inner));
```

**What is wrong.** `exportBundle` writes the exporter's `pages` map (id to name) alongside keys built
from the exporter's page ids. The merge starts from *my* `sharedFields()`, so for any id present in
both files the exporter's name is discarded. `readShared` then reads `pages` into
`pageNamesWhenWritten` - which is now my own names - and the conflict loop compares it against
`pageIdToName`, which is also my own names. The two always match, so **`pageIdConflicts` is guaranteed
to be empty after any import**. The incoming keys are then untranslated through my `pageIdToName`, so
the exporter's page id 3 lands on my page id 3 whatever the two are called.

**The consequence.** Import a bundle from someone whose pages are numbered differently and their
station names, lengths, one-way directions, portal pairings and captions attach to the wrong pages of
your layout, silently - which the id/name machinery describes at `:337-340` as "worse than losing them,
because nothing looks wrong". The detection that would have caught it is disabled by construction, not
by an oversight in the check itself.

Secondary: if `readShared(merged)` throws and the rollback at `:987-991` runs, `importConfiguration`
has already installed the imported configuration and is not rolled back.

**Confidence.** Mechanism CONFIRMED. Trigger PLAUSIBLE - it needs the exporter and importer to disagree
about page ids, which I cannot demonstrate from a reading. The path is reachable from the UI
(`AutonomyViewerPanel.java:1048`).

**What I would do.** Merge the *incoming* `pages` map into a separate field and compare against it,
rather than merging it into `merged` where my own entries win.

---

## C - narrower, or with an unproven trigger

### UR-11 - A throw inside the lock loop leaks every lock and mislocates the train

**Where:** `src/org/traincontrol/automation/Layout.java:2262-2300`, `:3844-3902`.

The two *returned-false* failure modes of `configureAndLockPath` are handled correctly
(`handleMisconfiguredPath` plus `takingPath.remove`). A **thrown** exception is not. `configureEdge`
reaches `MarklinAccessory.setSwitched`, which synchronously calls `network.getGUI().repaintSwitch(...)`
- a Swing call on the driving thread - and then `network.exec(...)`. A `RuntimeException` from either
propagates out of the `synchronized (this)` block into `executePath`'s catch, whose comment says:

> It does not unlock the path. The locomotive may be physically standing on those edges, and releasing
> them would let another train be routed into occupied track.

That justification is right for a failure *mid-run* and false for a failure *during locking*:
`loc.setSpeed(speed)` is not issued until `:4036`, so the train has not moved. The rule was lifted from
the case whose precondition made it safe.

**Consequence.** Every edge locked so far and all their lock edges stay occupied for the rest of the
session. And `e.getEnd().reserve(loc)` has already put the locomotive on those Points; `reserve`
deliberately does not sweep, so it is now recorded at several at once. `pickPath` then picks the first
Point in iteration order where the locomotive matches - which may be a mid-path Point the train is not
standing on - and a full route is configured and real ironwork thrown for a movement from a station the
train is not at.

**Confidence: PLAUSIBLE.** The leak and the mislocation are certain given any throw inside the loop; I
have not proven that `repaintSwitch` or `network.exec` throws in practice.

**What I would do.** Wrap the lock loop so that a throw during locking releases what it has taken - the
precondition that makes that safe is "the train has not been given speed yet", which is checkable.

---

### UR-12 - An unnamed station survives its tile's deletion forever

**Where:** `src/org/traincontrol/automationui/AutonomyCompanionStore.java:2055-2103`, with `:569-573`.

`stations` is pruned only as a side effect of pruning `pointNames`:

```java
for (String key : pointNames.keySet()) if (!keys.contains(key)) goneTiles.add(key);

for (String key : goneTiles)
{
    ...
    if (referrers.isEmpty()) { pointNames.remove(key); stations.remove(key); ... }
```

A square with a `stations` entry and no `pointNames` entry is never visited. There is no
`dropMissingMembers(stations, keys)` to match the one written for `disabledPortals` twenty lines above.

Unnamed stations are an ordinary reachable state, not a corner case: `setStation` adds to the set with
no name required, and `AutonomySession.placeCaption` has a dedicated "not named yet" return for exactly
this. Note also that `LayoutEditor.delete` (`:2863-2909`) tells the setup only about *captions* - it
does not call `forgetTiles` - so a deleted tile relies entirely on this reconcile.

**Consequence.** Mark a sensor as a station, do not name it yet, later delete that tile. `setup.json`
keeps the square in `stations` indefinitely. Redraw a sensor at those coordinates - routine when a page
is re-laid-out - and it is silently a station again. It is at least noisy rather than invisible:
`checkNames` raises `UNNAMED_STATION` as an ERROR, so the user gets a blocking finding they did not
create and no explanation of where it came from.

---

### UR-13 - `reconcile` never checks `stationSignals` values

**Where:** `src/org/traincontrol/automationui/AutonomyCompanionStore.java:2058`, `:2859-2877`.

```java
report.droppedTileProperties.addAll(dropMissing(stationSignals, keys, false));
```

`dropMissing` tests the **key** - the station square. `stationSignals` is the only square-referencing
collection whose *value* reconcile never checks: `portals` values are checked at `:2108-2114` and
`captions` values inside `reconcileCaptions`.

`forgetSquares` handles the built-over case, so this bites on a plain **deletion** of the signal tile.
The pairing then survives every save. `signalsThatAreGone()` reports it, so it is not invisible - but
nothing drops it, and if any accessory-bearing tile is later drawn at those coordinates,
`protectingSignalNames()` resolves it and autonomy starts throwing an accessory nobody paired. That is
the same defect class the method fixed two paragraphs earlier for `linkNames` and `disabledPortals`
("INHERITED by the next link drawn on that square"), applied here to the one collection that commands
real hardware.

`docs/reviews/2026-08-18-station-signals-plan.md:38` states the intended rule: "dropped in `reconcile`
when **either** tile goes".

---

### UR-14 - `load()` empties the store before a type-strict read

**Where:** `src/org/traincontrol/automationui/AutonomyCompanionStore.java:482-484`, against `:411-417`.

> Read and parse BEFORE anything is thrown away. ... A load that fails now leaves the setup exactly as
> it was.

That is true of a *parse* failure - the file text and every configuration are read and parsed before
`clear()`. It is not true of a *type* failure. `readShared` runs after `clear()` and uses the strict
accessors throughout - `object.getString(key)` at `:2887`, `array.getString(i)` at `:2938`,
`lengths.getInt(key)` at `:2276` - each of which throws an unchecked `JSONException` part way through,
with the store already empty.

`importBundle` really is guarded: it snapshots `sharedFields()` and rolls back in
`catch (RuntimeException e)`. `load()` and `restoreSetup` have no such guard, and the callers catch
`IOException` only. `AutonomySession.discardEdits` then leaves `dirty` set with an empty store, and the
next `saveWithoutReconciling()` on the exit path writes that empty store over `setup.json`.

**Confidence: mechanism CONFIRMED, trigger unproven.** Every field this build writes round-trips, so I
could not construct a trigger from files TrainControl produces. The exposure is a hand-edited
`setup.json`, a third-party writer, or a future type change within version ≤ 2. What is unambiguous is
that the comment promises a guarantee the method does not provide - and `importBundle`'s comment cites
`load()` as the model it was following.

---

### UR-15 - Caption labels are never unregistered

**Where:** `src/org/traincontrol/gui/LayoutGrid.java:361`;
`src/org/traincontrol/gui/TrainControlUI.java:973-1023`.

`addLayoutStation` is the only thing that removes from `layoutStations`, and it prunes lazily: a stale
label is dropped only when a **successor label for the same `TileKey` with the same owner** is
registered. When a caption is *cleared* - `AutonomyEditorPanel.applyCaption(tile, null)` - the rebuilt
grid registers nothing for that square, so no successor ever arrives and the old `JLabel` stays in the
map forever. Being still a child of the retired container, it keeps the whole previous grid reachable:
every `LayoutLabel`, icon and listener for that page. The same applies to every key whose page has been
renamed or deleted, since `TileKey` carries the page name.

Secondary: `updateStationLabels` gates on `!getLayoutStations(square).isEmpty()`
(`TrainControlUI.java:3306`), so a square whose caption was removed still passes and does its full
per-Point work on every autonomy update, writing into a detached label.

The premise is documented at `TrainControlUI.java:2187-2189` - "these do NOT survive: a label is
registered as the grid is built" - so the asymmetry is known; what is missing is a removal path for
"this square no longer has a caption". I have not measured the retained size, and it grows only per
caption-removal or page-rename, so this is moderate rather than severe.

---

### UR-16 - A split copy's name and its facing are computed two different ways

**Where:** `src/org/traincontrol/automationui/AutonomyBuilder.java:707-716` (`heading`) against
`:626-648` (`facingOf`), used together at `:677-702`.

`facingOf` computes which way a train on a copy is pointing by following the route it arrived on, and
its javadoc explains why that is not the same as the opposite of the arrival side:

> A train entering an N-E curve by N leaves by E, and saying it faces S describes a train sitting
> across the rails. That error was invisible in the model ... and became visible the moment a facing
> was shown to a user.

`heading`, which produces the text in the name, is the version that error was fixed out of:

```java
switch (arrival)
{
    case W: return "eastbound";
    case E: return "westbound";
    case N: return "southbound";
    default: return "northbound";
}
```

So on a curve or a diverging leg, the copy is *called* "Main 4 (eastbound)" while `facingsAt` reports
its train faces south, and `nodeName`'s own javadoc claims the name is "for the direction of travel
rather than the side arrived by". Names only - nothing routes on this - but it is one rule in two
places with one copy fixed, and the running log is where a user goes to work out what a train is doing.

`StationIndex.withoutArrivalSuffix` strips exactly these four words, so changing `heading` to use
`facingOf`'s answer would need that list kept in step.

---

## D - documentation that has come loose from the code

### UR-17 - `hasItemsBesidesTitle` is dead, and its empty submenu is still shown

`src/org/traincontrol/gui/AutonomyEditorPanel.java:1451-1471` documents the helper as the fix for
OB-032. `grep -rn hasItemsBesidesTitle` over `src/` and `test/` returns exactly one hit: the
declaration. Meanwhile `menu.add(connections)` at `:1279` is unconditional, while everything that puts
items into `connections` sits inside `if (session.canCarryDirection(target))` - which returns false for
any portal. So right-clicking a link or a tunnel in autonomy mode opens a "Connections and Direction"
submenu containing a greyed heading and a separator, which is precisely the state the helper exists to
detect. Guard `:1279` with it.

### UR-18 - `locationsOf` documents a portal rule it does not implement

`src/org/traincontrol/automationui/GraphReducer.java:1059-1082`:

> Normally the tile itself. An overpass is identified per route ... **A paired portal counts as one
> location with its partner**, since a tunnel and its far end are one piece of track drawn twice.

The method has only the `OVERPASS` branch; there is no portal case at all. It happens to be harmless
today, and it is worth writing down *why*, because the reason is not obvious and the next person to
read that sentence will assume the code does what it says: a portal tile is never a feedback tile, so
it is never a Point, so any walk that crosses the jump records **both** tiles as steps and the two
edges share the first tile's location key anyway. The union would be redundant. The comment should say
that instead of describing machinery that is not there.

### UR-19 - `AutonomyViewerPanel.populating` guards a listener that no longer exists

`src/org/traincontrol/gui/AutonomyViewerPanel.java:117-118`: "Set while a configuration is being loaded
into the combo, so reacting to that does not load it straight back again". Written at `:647`, `:655`,
`:1374`, `:1391`; never read. The `configurations` combo carries only a `MouseListener` for the
right-click menu (`:322`) - there is no `ActionListener`, so there is nothing to suppress. Harmless,
but the comment asserts a guard the class does not have.

---

## E - checked, and not findings

Recorded so a later reader knows what was looked at and can skip it.

- **`TilePorts` in full.** The rotation arithmetic, `deriveToe`'s intersection (including the stub
  early-return and the `candidates.size() != 1` rule), `isRoutable`, and `normalizeOrientation` are all
  correct for every type in the table. I checked `deriveToe` by hand against `SWITCH_LEFT`,
  `SWITCH_CROSSING`, `CUSTOM_PERM_Y`, `DOUBLE_CURVE`, `CURVE` and `END`. Clean.
- **Rotating a tile does not corrupt its stored one-way settings.** I went looking for this and it is
  not there. Directions are stored as `TOWARD_A`/`TOWARD_B` against a route index, `TilePorts.ports`
  rotates routes while preserving list order, and A and B rotate with the tile - so a restriction
  rotates with the track it is about. `LayoutEditor.rotate` telling autonomy nothing is correct.
- **Replacing a tile in place.** `execCopy` and the paste path both call `forgetTiles` /
  `moveTiles` for the squares they build over, so a stored direction cannot be inherited by a different
  piece of track drawn on the same square.
- **`LayoutEditor`'s two undo stacks staying in step.** The caption stack is not cleared on a page
  switch while the component stack is (`:4370-4371`), which looks wrong - but `push` is `addFirst` and
  the trim is `removeLast`, so the first edit on the new page pushes its own snapshot at the head and
  trims the stale tail away; and both `undo()` and `redo()` are gated on the *component* stack being
  non-empty, which after a switch requires that snapshot to have happened. No misalignment is
  reachable. It is fragile, not broken.
- **`transparentRouteId`'s encoding.** `100 + a.ordinal() * 4 + b.ordinal()` produces distinct values
  for all six possible route-button routes, and `routeOf` correctly returns null for the ≥ 100 range so
  they default to bidirectional. No collision with a real `(state, index)`.
- **`TileGraph.findUndirectedPath` and `continuations`.** The `(tile, entry side)` walk state,
  the FIFO frontier, the stub handling and the portal branch are all right; the predecessor
  reconstruction terminates on the start key's null entry.
- **`GraphReducer.deriveLocks` and the parallel-route replacement.** `edges.remove(existing)` is
  identity-based and `ReducedEdge` does not override `equals`, which is what is wanted; the
  reverse-direction exemption matches what `Edge`/`isPathClear` do with opposite edges.
- **`AutonomyBuilder.uniqueNames` and `nodeName`.** I tried to construct a name collision between a
  hand-authored name and a generated split suffix and could not: `taken` is the base-name set, the
  base of the tile being named is removed from it, and `uniqueNames` guarantees the bases are distinct.
  The `seen.put(candidate, 1)` line closes the "X, X, X (2)" case its comment describes.
- **`DiagramMonitor` threading.** The publisher goes through `invokeLater`, so `refresh()` being
  `synchronized` cannot deadlock against the event thread; `edgesByName`/`pointTiles` are swapped
  wholesale rather than mutated; `activeLocomotives` and `locomotiveMilestones` are concurrent
  collections with COW values. The generation fence in `DiagramMonitorDriver` is sound in both
  orderings.
- **`AutonomySession.rebuild`'s deliberate non-nulling of `stationIndex`.** The reasoning holds. The
  only latent note is that `graph` and `reducer` are plain non-volatile fields reassigned in the same
  method; every current reader is on the EDT, so there is no bug today, but the safe-publication
  argument in that comment covers `stationIndex` alone and the first off-EDT caller of `getGraph()`
  reopens it.
- **`parseTileKey` and `isOnPage`.** The last-colon split is correct for a page named "Yard: Upper",
  and the `#`-suffix fallback is right. A page name containing `#` would defeat `rekeyOne`, but
  `renamePage` has no production caller (see `UR-7`), so there is nothing to report.
- **Lock ordering in `Layout`.** Every `synchronized (this.activeLocomotives)` block was enumerated
  against every `synchronized` on `this`. Only `activeLocomotives → this` occurs. No inversion, no
  deadlock. The `maxActiveTrains` cap and the `takingPath` claim are genuinely closed on every exit.
- **`isPathClear` with an empty path** throws `IndexOutOfBoundsException` at `Layout.java:1849`, but no
  live caller can reach it - `executePathInternal` checks `isEmpty()` first and `bfs` never returns an
  empty list. Latent, unreachable, not worth changing on its own.
- **Two claims from the delegated readers that did not survive verification.** One reported that
  `LayoutEditor`'s page switch loses caption undo data - it does not, see above. One reported
  `AutonomyEditorPanel.refresh()` dereferencing a null reducer; I could not construct a path that
  reaches it in that state either, and it is recorded here rather than as a finding.

---

## What I would fix first

`UR-1`, because it is two characters and it throws real ironwork under a moving train.

Then `UR-2` and `UR-3` together, because they are both single-line omissions with disproportionate
consequences and they interact: a ghost locomotive left by `UR-3` is exactly the kind of permanent
"claimed" state that `UR-4`'s memo will then latch onto.

`UR-4` and `UR-6` are the ones I would want somebody who knows the railway to argue with me about
before changing anything, because both touch what a signal is commanded to show and I have inferred
the intent from comments rather than from the layout.
