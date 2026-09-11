# A pass over the application, in the parts the other four did not open

**Status:** open

**Prefix for citing these findings elsewhere:** S14

**What was reviewed.** Branch `autonomy-diagram-r0`, the working tree as it stood from 17:40 to 18:30
local on 2026-09-10. That started as `d86914a9` plus the uncommitted round of fixes; Adam committed
that round as `a281e3a2` at 18:07 while this pass was running, so the code reviewed is now HEAD and
nothing in it changed under me. See `S14-D7` for the one thing in the tree that did.

**Scope.** Every file under `src/` touched since 2026-08-27 (70 files), plus the working-tree changes.
**Tests, test helpers, fixtures and the harness under `docs/tools/` were out of scope and are not
reviewed here** - other passes own those. Test classes were run, and their sources read, only as
evidence about the application. The one documentation file in scope is
[`docs/reference/behaviour.md`](../reference/behaviour.md), because where it and the code disagree one
of them is a defect.

**Method.** Four reviews of this period already exist in this folder - `E8`, `E8V`, `X8`, `X8V`, 63
findings between them. Their finding titles were read first so this pass could go elsewhere. They
concentrated on the page file format (`CS2File`, `LayoutDiagram`), the layout editor's clipboard and
grid gestures, route conditions and locking, multi-unit compatibility, and the autonomy companion
store. This pass went to locomotive speed and function handling, the Central Station protocol and its
feedback, route import and the s88 trigger, the timetable, the JSON round trip, and the page-id reader
that everything else is keyed by. `grep` for the siblings of everything examined, on this project's own
recurring-defect pattern; the data checked against the real files where a claim depended on them.

Three reconnaissance passes were delegated for breadth - locomotive handling, the protocol and
accessories, the timetable and JSON. Every finding below was then re-verified first-hand against the
source; several leads were dropped at that stage and two became `D` items.

**What was executed.** `core.testRouteCommandParity`, `core.testParseCS2Layout`,
`core.testMultiUnitMembership`, `regression.testPageIdsAreDurable`,
`regression.testSwitchingToACentralStationLayout`, `regression.testTheGoldenLayoutHoldsTogether`, plus
one mutation of `LayoutDiagram.readLayoutIndexIds`, applied and reverted. A battery held the runner's
lock until 18:07, so the reading happened first and the runs are narrower than they would otherwise
have been. Data measurements were made with a Python re-implementation of the reader under test over
the real files, which is said explicitly wherever it is the evidence.

**Where the evidence is weaker, it says so.** Two findings (`S14-A1`, `S14-B4`) are established by
reading the call graph, not by producing the failure. Both say which interleaving or input would be
needed.

---

## A — high

| id | what | disposition |
|---|---|---|
| S14-A1 | `importRoutes` starts every imported route's s88 monitor during the parse, so an import double-watches every sensor and a parse that fails part way leaves live monitors no list can show and nothing can disable | open |
| S14-A2 | the duplicate-page-id guard went to `readLayoutIndexPageExtras` and not to `readLayoutIndexIds`, which is the reader the autonomy setup is keyed by - so two pages sharing an id share one setup | open |

### S14-A1 — `importRoutes` starts the new routes' sensor monitors before it deletes the old ones, and a parse that throws part way leaves them running

`MarklinControlStation.importRoutes` (`src/org/traincontrol/marklin/MarklinControlStation.java:3781`)
and `parseRoutesFromJson` (`:3769`); `MarklinRoute.fromJSON` (`src/org/traincontrol/marklin/MarklinRoute.java:1310`)
and the complete constructor (`:115-139`).

`MarklinRoute`'s complete constructor ends with:

```java
        // Starts the execution of the automated route
        this.executeAutoRoute();
```

`executeAutoRoute` (`:157-240`) starts a thread that parks on the route's s88 sensor and fires the
route whenever it triggers. So **constructing** a `MarklinRoute` from JSON arms it. `importRoutes` then
does its three steps in this order:

```java
        List<MarklinRoute> routes = this.parseRoutesFromJson(json);   // :3783  - every route now armed

        this.logf("route.deletingExisting");
        for (MarklinRoute r : this.routeDB.getItems())
        {
            this.deleteRoute(r.getName());                            // :3788  - old monitors retired
        }

        for (MarklinRoute route : routes)                             // :3792  - and now registered
```

Two distinct consequences.

**On every import, the sensors are watched twice.** From the moment the parse finishes until each old
route is deleted, the old route and its replacement are both parked on the same s88. A sensor firing in
that window fires both. Where the import changed the route - which is the reason to import one - the
two fire different sets of accessory commands at the same turnouts, spaced only by
`DEFAULT_SLEEP_MS`.

**If the parse throws part way, the routes already built keep running for the rest of the session.**
`parseRoutesFromJson` builds the list in a loop (`:3767-3771`) and `MarklinRoute.fromJSON` throws out
of it on a malformed entry. The routes constructed before that point are armed, are in no database,
appear in no route list, and have nothing that can reach `disable()` - `deleteRoute` walks
`routeDB.getItems()`, which never held them. They watch their sensors and throw switches until the
application is closed, and the operator has no way even to see that they exist.

**How I know.** Read, not executed - I did not construct a malformed route file and watch a switch
move. The chain is four hops and each was opened: `importRoutes:3783` → `parseRoutesFromJson:3769` →
`MarklinRoute.fromJSON:1310` (`return new MarklinRoute(network, name, id, routeCommands, s88,
triggerType, enabled, conditionExpression);`) → the complete constructor's last statement
`this.executeAutoRoute()` at `:139` → `this.monitorThread.start()` at `:235`. The guard at `:167-170`
prevents a *second* monitor on one route object and says nothing about two objects for one route.

`X8-D4` examined this same method and cleared it: *"`MarklinControlStation.importRoutes` calls
`parseRoutesFromJson` first and only then deletes ... The order is the thing that makes it safe and the
method's own comment says so."* That is right about the route **database** - no routes are lost to a
bad file. The comment it cites (`:3750-3752`) reasons entirely about the database too. Neither
considered that construction has a side effect on the railway. This is the layer below the one that
was verified.

**What I would change.** Do not arm a route from its constructor. Either move `executeAutoRoute()` out
of the constructor and let `newRoute` call it once the route is in the database, or give
`parseRoutesFromJson` a constructor path that builds the route disarmed. If neither is acceptable,
`parseRoutesFromJson` must at minimum disarm everything it built when it throws, in a `catch` - but
that leaves the double-watch window on a successful import, which is the half that happens every time.
State the rule where `executeAutoRoute` is called, because this is the second caller that did not
expect it.

### S14-A2 — two pages holding one id share one autonomy setup, because the guard was added to the other reader

`LayoutDiagram.readLayoutIndexIds` (`src/org/traincontrol/base/LayoutDiagram.java:935-1014`) against
`readLayoutIndexPageExtras` (`:1039-1113`) and `attribute` (`:1136-1151`).

`X8V-B1` found that two pages can hold one id and fixed `readLayoutIndexPageExtras`: it now tracks
every id a page has claimed and withdraws one that two pages claim, so neither inherits the other's
scroll offsets. `readLayoutIndexIds`, twenty-five lines above, got no such guard - and its own comment,
added in the same round, says what it is:

> *"This one was the odd one out, and it is the one whose answer everything else is keyed BY"*
> (`:971-972`)

It is. `AutonomySession` (`src/org/traincontrol/automationui/AutonomySession.java:123-130`) builds a
name→id map from the parsed pages and hands it to
`AutonomyCompanionStore.setPageIds` (`src/org/traincontrol/automationui/AutonomyCompanionStore.java:467-481`):

```java
            pageNameToId.put(entry.getKey(), entry.getValue());
            pageIdToName.put(entry.getValue(), entry.getKey());
```

With two names mapping to one id, `pageNameToId` sends both pages' keys to the same id and
`pageIdToName` keeps one name. Everything the setup holds is then written through
`toStored` (`:4962-4984`), which is that map:

```java
        String id = pageNameToId.get(key.substring(0, colon));

        return id == null ? key : id + key.substring(colon);
```

So both pages' stations, names, lengths, facings, captions, arrival restrictions and placements are
written into one key space, `"<id>:x,y"`. Within a collection, two pages with a square at the same
coordinates overwrite each other. Across the whole file, `fromStored`/`pageOf` (`:4986-5057`) resolves
that id to a single page name on the way back, so the other page's entire setup comes back attached to
track it has nothing to do with - and the next reconcile drops whatever does not fit, which is the
`MT-135` loss the page-id mechanism exists to prevent.

Nothing reports it. `pageIdConflicts` (`:4058-4080`) fires only when an id's recorded name has
*changed*; for a straight duplicate it has not. `pagesNotLoaded` (`:545`) walks
`pageNamesWhenWritten`, which on the first save is written from `pageIdToName` (`:1798-1801`) and
therefore already has the missing page absent rather than wrong.

**How I know.** Read and measured, and partly executed.

The data. A Python re-implementation of `readLayoutIndexIds` as it stands (the block rule, the
case-insensitive `.id=`/`.name=` matching, and `pageIdOrPosition`'s "absent means the page's position")
run over every `gleisbild.cs2` in the tree:

```
cs2_sample_layout\config\gleisbild.cs2       pages=5  ids=5
Oles kreds\config\gleisbild.cs2              pages=8  ids=7  DUPLICATE {1: ['0 stationer', '1 gods']}
sample_layout\config\gleisbild.cs2           pages=3  ids=2  DUPLICATE {1: ['Page 1', 'Page 2']}
tc_backup\...\gleisbild.cs2                  pages=5  ids=5
test\baseline\layout\config\gleisbild.cs2    pages=5  ids=5
test\layout\config\gleisbild.cs2             pages=1  ids=1
test\layouts\curve-into-platform\...          pages=1  ids=1
test\layouts\live-snapshot\...                pages=5  ids=5
test\layouts\single-switch\...                pages=1  ids=1
test\layout_subpage\config\gleisbild.cs2     pages=2  ids=2
test\operator_layout\config\gleisbild.cs2    pages=5  ids=5
test\test_layout\config\gleisbild.cs2        pages=5  ids=5
test\test_layout_snapshot\...                 pages=5  ids=5
---
indexes with a duplicate resolved page id: 2
```

Two of thirteen, and one of them is `Oles kreds` - the genuine Central Station export this repository
ships, the file `readLayoutIndexPageExtras`'s own comment calls *"the genuine Central Station export
in this repository"*. Its shape is:

```
seite
 .name=0 stationer      <- no .id, so pageIdOrPosition gives it the POSITION, 1
 .xoffset=1
 .yoffset=3
seite
 .id=1                  <- and this page states 1
 .name=1 gods
```

The pages are named `0 stationer` … `7 autonom annotated` and the stated ids run 1…7, which is only
consistent if the station omits `.id` when the id is **0** - the ordinary CS2 convention that an absent
key is the zero value. `sample_layout/config/gleisbild.cs2`, an untracked runtime artifact on this
machine (`.gitignore:2`), has the same shape. If that reading is right then
`pageIdOrPosition`'s rule is wrong for every genuine multi-page export, and so is the comment above it
(`:909`, and `CS2File.java:2165`: *"The first page carries no id of its own and is page 1, which is
what the Central Station assumes"*). The finding does not depend on the interpretation - two pages
resolving to one id is a defect either way - but the fix does, so it is a question for Adam.

The window. All three production callers of `writeLayoutIndex`, whose `issued` gate does resolve a
duplicate, are page operations: `LayoutPageEdit:265` (add and rename), `TrainControlUI:24834`
(Combine Pages) and `:25263` (Delete Page). Nothing writes the index at start-up or on layout load. So
on a layout with this shape, autonomy can be set up and saved - repeatedly - with the duplicate
standing.

Executed. I mutated `readLayoutIndexIds` to add the same `claimed`-set withdrawal `attribute` has, and
ran four classes:

```
--- core.testParseCS2Layout                          Total tests run: 27, Failures: 0, Skips: 0
--- regression.testPageIdsAreDurable                 Total tests run: 19, Failures: 1, Skips: 0
--- regression.testSwitchingToACentralStationLayout  Total tests run: 12, Failures: 1, Skips: 0
--- regression.testTheGoldenLayoutHoldsTogether      Total tests run:  6, Failures: 0, Skips: 0
```

The `testPageIdsAreDurable` failure is a precondition, and it is the useful result: `the fixture is not
a duplicate, so this test proves nothing: {Bravo=4} expected [4] but found [null]`. An existing test
asserts deliberately that this reader returns the duplicate, because that is the starting state for
testing the writer's gate. So the mechanism is known - `DR-B4` chose to answer it in the writer - and
**what is new here is only that the writer never runs until the operator adds, renames, deletes or
combines a page, and the store is keyed from the reader in the meantime.** With the mutation reverted
the class is green again (`19, Failures: 0, Skips: 0`). The
`testSwitchingToACentralStationLayout` failure is not mine and not about `src/` - see `S14-D7`.

**What I would change.** Two decisions, and they are separable.

The narrow one: give `readLayoutIndexIds` the guard its sibling has. Withdrawing both claimants is the
fail-safe answer `attribute` already chose and its reasoning transfers - nobody inherits anybody's
settings, and a page with no id keeps its name as a key, which
`AutonomyCompanionStore.pageIsHere` (`:5068-5080`) already handles (*"a page added since the index was
read has no id yet - both were always meant to survive"*). That removes the misattachment and costs at
most the rename-survival of two pages until the next index write.

The wider one, for Adam: if an absent `.id` means 0 rather than the page's position, `pageIdOrPosition`
has been giving the first page of every genuine export the second page's number, and the two comments
asserting otherwise should go with the change.

Either way, say in `behaviour.md` what a duplicate id means, since §8 now states what an id *is*.

---

## B — medium

| id | what | disposition |
|---|---|---|
| S14-B1 | `setF` fans a function out to every multi-unit member before bounds-checking it, so a function the head does not have is switched on on the members and can never be switched off | open |
| S14-B2 | `RouteCommand.fromJSON` does not clamp a locomotive speed where its sibling does, and `setSpeed` clamps the members and not the head - so an imported route sends a consist's members to full speed | open |
| S14-B3 | `Edge.toJSON` does not write `entrySide`, which `Layout.fromJSON` reads and the field's own javadoc says travels in the configuration - so Export JSON then Load JSON loses every edge's arrival side | open |
| S14-B4 | the save path reads `linkedLocomotives` with no lock, while the rebuild that was made atomic for `setSpeed` and `setDirection` clears and refills it | open |

### S14-B1 — `setF` tells every multi-unit member about a function number the head does not have

`MarklinLocomotive.setF` (`src/org/traincontrol/marklin/MarklinLocomotive.java:879-913`):

```java
    synchronized public Locomotive setF(int fNumber, boolean state)
    {
        // Pass through commands
        for (Locomotive l : this.linkedLocomotives.keySet())
        {
            l.setF(fNumber, state);
        }

        if (this.validF(fNumber))
        {
```

The fan-out is unconditional; the bounds check is the head's own, and comes after. Every sibling asks
first - `setFunctionType` (`:151`), `isFunctionPulse` (`:340`), `isFunctionTimed` (`:356`), and
`_setF` itself (`src/org/traincontrol/base/Locomotive.java:505-511`).

A head can legally have fewer functions than a member. `getMaxNumF` (`:374-383`) gives MM2 five
functions, DCC twenty-nine and MFX thirty-two, and `canBeLinkedTo` (`:1286-1328`) refuses self, a
Central Station multi-unit, a member that is itself a head, and an address clash - and says nothing
about the decoder type. So an MM2 consist head with an MFX member is a supported configuration.

On that consist, `setF(6, true)`:

- the member's own `setF(6, true)` passes its `validF` and sends f6 to its decoder;
- the head's `validF(6)` is false, so nothing is sent for the head and nothing is recorded: its
  `functionState` is five long.

Three consequences follow from nothing being recorded. The function is invisible - no button exists for
it and `getF(6)` returns `false` (`Locomotive.java:1144-1153`). `functionsOff()`
(`Locomotive.java:249-258`) loops `i < this.getNumF()`, five, so it can never clear it - and that is
what autonomy's arrival handling calls. And `switchF` (`src/org/traincontrol/gui/TrainControlUI.java:9598-9603`)
passes `!this.activeLoc.getF(fn)`, which is always `true` out of range, so **every press sends ON and
no press ever sends OFF**. The only way back is to select the member locomotive directly and clear it
there.

**How I know.** Read. The reachable door is the keyboard: `TrainControlUI` binds `switchF(0)` through
`switchF(30)` at `:18266-18380`, on bare function keys (`keyCode == KeyEvent.VK_F6` at `:18290`, no
modifier), with no gate on the active locomotive's `numF` - `switchF` checks only `activeLoc != null`.
I did not drive a real consist; the program's own data is what establishes that the configuration is
legal, and `canBeLinkedTo`'s four refusals are all that stand between the user and it.

**What I would change.** Move the fan-out inside `validF`, or give it its own check. The head is the
thing being commanded and a function it does not have is not a command to pass on. Note that a member
with *fewer* functions than the head is already handled correctly, by the member's own `validF` - so
this is the one direction that leaks. Whichever way it is fixed, write the rule down beside the loop:
`setDirection` (`:838-877`) and `setSpeed` (`:784-836`) fan out unconditionally too, correctly, because
neither carries an index.

### S14-B2 — a route imported from JSON can ask for a speed above 100, and the clamp that exists protects the members and not the head

Three places, and the defect is the gap between them.

`RouteCommand.fromLine` (`src/org/traincontrol/base/RouteCommand.java:722`) clamps, at `:825-830`:

```java
            int speed = Integer.parseInt(line.split(",")[2].trim());

            // Validate speed, negative means instant stop
            if (speed < 0) speed = -1;
            if (speed > 100) speed = 100;
```

`RouteCommand.fromJSON` (`:492`) does not, at `:519-521`:

```java
                String locoName = jsonObject.getJSONObject("state").getString(KEY_NAME);
                int speed = Integer.parseInt(jsonObject.getJSONObject("state").getString(KEY_SPEED));
                routeCommand = RouteCommand.RouteCommandLocomotiveSpeed(locoName, speed);
```

`RouteCommandLocomotiveSpeed` (`:151-164`) stores the string as given. Execution
(`src/org/traincontrol/marklin/MarklinRoute.java:891-904`) passes it straight through:

```java
                                    if (rc.getSpeed() < 0) { loc.instantStop(); }
                                    else                   { loc.setSpeed(rc.getSpeed()); }
```

And `MarklinLocomotive.setSpeed` (`:784-836`) clamps each member and not itself:

```java
            // Clamped, because multipliers up to 2 are accepted and the scaled value can therefore
            // exceed the 0-100 range _setSpeed will store.  _setSpeed IGNORES an out-of-range value
            // rather than clamping it, and the member then transmits its previous speed - ...
            roundedSpeed = Math.min(roundedSpeed, 100);

            entry.getKey().setSpeed(roundedSpeed);
        }
        ...
        super._setSpeed(speed);

        int newSpeed = this.getSpeed() * 10;
```

`_setSpeed` (`src/org/traincontrol/base/Locomotive.java:451-497`) is wrapped in
`if (speed >= 0 && speed <= 100)` with no `else`, so 150 is discarded and `this.getSpeed()` is still
whatever it was. The head therefore re-transmits its **old** speed while every member is sent
`min(150 × |multiplier|, 100)` - which for the ordinary 1.0 multiplier is 100. That is the exact
failure the comment quoted above was written for, *"the two engines of one consist pulled against each
other"*, one level up from where it was fixed.

On a plain locomotive the same command is merely inert: nothing is stored and the current speed is
re-sent, with no log line to say the number was ignored.

**How I know.** Read, with the parsers compared side by side, and one measurement. `core.testRouteCommandParity`
is green (`Total tests run: 3, Failures: 0, Skips: 0`) and does not cover this: its corpus uses a
single in-range speed (`RouteCommand.RouteCommandLocomotiveSpeed("Test loc 1", 40)`, line 46) and it
exercises the **text** form only, never `fromJSON`. So the divergence is unpinned in both directions.
The reachable door is Routes → Import (`TrainControlUI.java:24388` → `MarklinControlStation.importRoutes`
→ `MarklinRoute.fromJSON:1306` → `RouteCommand.fromJSON`), which is how a hand-edited or shared route
file enters the database. I did not run a consist at 150.

**What I would change.** Clamp where the value is stored rather than in each parser - in
`RouteCommandLocomotiveSpeed`, which both parsers go through - and separately make
`MarklinLocomotive.setSpeed` clamp its own argument the way it already clamps its members'. The second
half is worth doing even if the first lands: `setSpeed` is public, the comment beside it already
explains that `_setSpeed` ignores rather than clamps, and "ignored and the previous speed re-sent" is
the worst of the three possible answers. Note while fixing it that `fromLine` maps any negative to
`-1` and `fromJSON` does not, and that `-1` is what `execRoute` reads as "instant stop".

### S14-B3 — `Edge.toJSON` does not write `entrySide`, and the field's javadoc says it does

`Edge.toJSON` (`src/org/traincontrol/automation/Edge.java:607-652`) writes exactly six keys:
`start`, `end`, `length`, `roomAtTheEnd` (conditionally), `commands` and `lockedges`. It does not write
`entrySide`.

`Layout.fromJSON` reads it (`src/org/traincontrol/automation/Layout.java:9519-9522`):

```java
                if (edge.has("entrySide") && edge.get("entrySide") instanceof String)
                {
                    e.setEntrySide(edge.getString("entrySide"));
                }
```

and the only writer of that key in `src/` is the diagram generator,
`AutonomyBuilder.java:1081`:

```java
            if (edge.getEntrySide() != null) json.put("entrySide", edge.getEntrySide().name());
```

So the builder mints it, the parser accepts it, and the Layout's own serializer drops it. The field's
javadoc (`Edge.java:323-326`) asserts the opposite:

> *"So the answer is not to pick a side but to give the model the one the builder already has. The
> builder knows this when it emits the edge; **it travels in the configuration**; and the arrival
> write, the tail walk and the operator doors all read it."*

The contrast one line away is what makes this look like an omission rather than a decision:
`roomAtTheEnd` (`:639`) was added in the same era, by the same generator (`AutonomyBuilder:1093`),
with the same "an absent key is meaningful" idiom - and it is written.

What is lost. `entrySide` is the build's answer to which side of a square an edge comes in by, which
`REV9-B3` and `OB-182` exist to supply because the compass opposite of the facing names no rail on a
curve. `Layout.entrySideOf` (`:5660-5665`) prefers it and falls back to geometry without it;
`:6860` is where an arrival writes `arrivedFrom` from it. With it gone, a curved platform records an
arrival side the build does not use - `behaviour.md` §4 describes that failure in detail, including
*"the tail walk matched nothing on its first hop, and nothing was blocked - a protection reporting
itself as present while doing nothing."*

**How I know.** Read, and measured against the real files. `Edge.toJSON`'s key list was read in full
(`:633-649`). The round trip available in the UI is Export JSON
(`TrainControlUI.java:24231` `this.getModel().getAutoLayout().toJSON()`, offered as a file through
`AutoJSONExport` at `:24257` and put on the clipboard at `:24262`) and Load JSON
(`:24203` sets the text area from the chosen file, then `:22934` `this.model.parseAuto(this.autonomyJSON.getText())`).
Neither is fenced on `activeDiagramConfiguration`, unlike the legacy `autonomy.json` autosave, which is
(`:2521`). The data:

```
autonomy.json                     : entrySide=0   roomAtTheEnd=0   edges=1030
test/baseline/configuration.json  : entrySide=101 roomAtTheEnd=77  edges=637
```

The builder-generated baseline carries 101 of them; a file that has been through `Layout.toJSON`
carries none. (`autonomy.json` is the legacy hand-authored graph, which never had them, so it is not
itself evidence of loss - it is the control.)

What limits this to B rather than A: in ordinary v3.0.0 use the running Layout is rebuilt from the
setup by `AutonomyBuilder` on every change, so the field is regenerated and the in-session behaviour is
correct. The loss is in the export, which is the documented way to back up, share or inspect a
configuration, and in anything loaded back from one.

**What I would change.** Write `entrySide` in `Edge.toJSON` alongside `roomAtTheEnd`, conditionally on
it being non-null, for the reason already stated in that method's comment about absent keys. If it is
deliberately not persisted, the javadoc's *"it travels in the configuration"* has to go, and the
sentence that replaces it should say what an exported configuration therefore cannot be used for.

### S14-B4 — the save path reads `linkedLocomotives` without the lock the rebuild was given

`MarklinLocomotive.setLinkedLocomotives` (`src/org/traincontrol/marklin/MarklinLocomotive.java:1180-1240`)
was made atomic on purpose, and says why:

> *"Staged in a local map and swapped in one step, rather than clearing the live map and refilling it in
> place. `setSpeed` and `setDirection` iterate `linkedLocomotives` under this locomotive's monitor;
> rebuilding it unsynchronised let a fan-out land mid-rebuild and either throw
> ConcurrentModificationException or command the head alone. That was tolerable while only the
> multi-unit dialog rebuilt consists ... but a Central Station sync now rebuilds them too,
> automatically, and a consist can be driven manually at the same time."* (`:1182-1188`)

The swap is correct (`:1227-1231`). The readers the comment names - `setSpeed`, `setDirection`, `setF`,
`stop` - are all `synchronized` and are safe. Three readers are not:

- `getLinkedLocomotiveNames` (`:1247-1259`) iterates `this.linkedLocomotives.entrySet()` with no lock.
  **This is the save path**: `MarklinSimpleComponent:161` `this.linkedLocomotives = l.getLinkedLocomotiveNames();`.
- `commandedLocomotives` (`:1079-1086`) returns the live `keySet()`, which
  `isSimultaneousMultiUnitCompatible` (`:1110`, not synchronized) iterates at `:1139` and `:1150`.
- `canBeLinkedTo` (`:1268-1270`) passes the live `keySet()` into the loop at `:1312`.

Because the swap is `clear()` then `putAll()` inside the monitor, a reader that does not take the
monitor can observe the map **empty** or half-filled. On the save path that means a consist persisted
with no members - and `restoreState` rebuilds consists from exactly that field, so the consist is gone
after the next start, silently. `getLinkedLocomotiveNames` is also what
`MarklinControlStation:1508` and `:3063` use to re-seed a rebuild, so a partial read there rebuilds the
consist from a partial snapshot.

**How I know.** Read, not executed - **I did not produce the interleaving.** What I established is
that the two threads exist and that the reader is unguarded: `saveState`
(`TrainControlUI.java:2303`, reached from the exit path at `:18486-18487` and from Backup Data at
`:21124-21125`) runs the collection, while `setLinkedLocomotives` is reached from the Central Station
sync. Whether those two overlap in practice depends on timing I have not measured. What makes it worth
filing rather than dropping is the shape: the fix enumerated the readers it was protecting against and
three were left out, one of them the one that writes to disk. That is this project's most repeated
defect class.

**What I would change.** Make `getLinkedLocomotiveNames`, `commandedLocomotives` and `canBeLinkedTo`
`synchronized`, or have `getLinkedLocomotives` (`:1335`) hand back an unmodifiable snapshot taken under
the monitor and route every reader through it - which would also make the remaining unguarded readers
visible to the next person, instead of leaving the rule as a convention documented at the one call site
that obeys it (`MarklinControlStation:3147`).

---

## C — low

| id | what | disposition |
|---|---|---|
| S14-C1 | the route editor validates a logical accessory address against the raw maximum, so the top address of each protocol is refused as "not an address" | open |
| S14-C2 | the s88 route monitor is not a daemon thread, where the three executors beside it were made daemons with a comment saying why | open |
| S14-C3 | `loadReturnToHomeTimetable`'s javadoc contradicts its own body in three places, and the comment that replaced one of them names the wrong flag | open |
| S14-C4 | `toggleF(int)`'s javadoc says one second; every locomotive that exists overrides it with 300ms | open |
| S14-C5 | `AutoJSONExport` is the one writer in the project that truncates its target instead of writing atomically | open |
| S14-C6 | `getPowerState()` reads, unsynchronised, a field written under the monitor - where four siblings in the same file were made volatile with reasons | open |
| S14-C7 | `isFeedbackCommand` claims three commands, one is parsed, and the unknown-id branch creates and persists a feedback module from the other two's bytes | open |
| S14-C8 | the route delay floor silently overrides what the operator typed and is not in `behaviour.md` | open |

### S14-C1 — a logical address checked against the raw maximum refuses the top address of each protocol

`RouteEditorFrame.addressProblem` (`src/org/traincontrol/gui/RouteEditorFrame.java:2432-2451`):

```java
        int address = numberOr(target, 0);

        if (address <= 0 || !Accessory.isValidAddress(address, speaks))
```

`Accessory`'s maxima are raw, and say so (`src/org/traincontrol/base/Accessory.java:49-51`):

```java
    // Maximum MM2 and DCC addresses.  These are the low level addresses, not the logical addresses of 320 and 2048
    public static final int MAX_MM2_ADDRESS = 319;
    public static final int MAX_DCC_ADDRESS = 2047;
```

The sibling converts first - `LayoutDiagramComponent.setLogicalAddress`
(`src/org/traincontrol/base/LayoutDiagramComponent.java:1100-1106`):

```java
            // 3-way switches have an address 1 above the base, so make the check more strict
            if (!Accessory.isValidAddress(this.isThreeWay() ? address : address - 1, protocol))
```

So the route editor's accepted range is one short at the top: logical MM2 320 and logical DCC 2048 are
reported as `route.ui.frameNotAnAddress` and the row will not validate, while the diagram editor takes
both. The three-way branch (`RouteEditorFrame:2374-2375`) checks `N` and `N+1` as logical, so a
three-way at logical MM2 319 is refused too, where the diagram accepts it.

**How I know.** Read - the predicate (`Accessory.java:497-512`, `addr >= 0 && addr <= MAX`), both call
sites and the constants' own comment. Arithmetic, not a measurement: passing the logical value shifts
the accepted band from `1..MAX+1` to `1..MAX`, so it is exactly one address per protocol. An over-strict
guard is worth more here than its size suggests - Adam's standing preference is that he would rather
have no check than one that refuses something legal.

**What I would change.** `isValidAddress(address - 1, speaks)` for ACCESSORY and SIGNAL, and for
THREE_WAY the same pair the diagram uses. Better, give `Accessory` a named predicate that takes the
logical address, so the conversion is stated once rather than at each call site - there are now two
call sites and they disagree.

### S14-C2 — the s88 route monitor keeps the JVM alive, which is the defect its neighbours were fixed for

`MarklinRoute.executeAutoRoute` (`src/org/traincontrol/marklin/MarklinRoute.java:172-235`) builds its
thread with `new Thread(() -> {...})` and calls `start()` at `:235`. No `setDaemon(true)`, no
`setName`. The thread parks in `Locomotive.waitForClearThenOccupied` on an untimed `wait`, and
`disable()` only clears a flag the thread will not look at until the sensor next fires, so it never
returns on its own.

`MarklinControlStation:353-366` made its three executors daemons and recorded exactly this:

> *"DAEMONS. These serve inbound CAN messages and have no meaning without the application they serve,
> but the default factory makes ordinary threads - so all three outlived every caller and kept the JVM
> up. The GUI hid it behind System.exit(0) and nothing else could."*

`NetworkProxy:138-142` did the same for the reader. The route monitor is the sibling that was not
swept, and it is created from `restoreState` as well (`MarklinControlStation:447`), so any saved route
with an s88 trigger and auto-execution on arms one at start-up. `shutdown()` (`:3601-3608`) stops the
executors and the socket and knows nothing about route monitors.

**How I know.** Read. For the GUI there is no visible consequence, because `System.exit(0)` still
covers it; the consequence is for anything that embeds or drives `MarklinControlStation` without
calling it, which is what the comment above means by *"nothing else could"*.

**What I would change.** `setDaemon(true)` and a name (`"TrainControl s88 route monitor: " + getName()`
would make a thread dump readable), and a line in `shutdown()` that disables every route's monitor.
The naming is not cosmetic here: these threads execute routes, and a stack trace that says `Thread-14`
gives nobody a starting point.

### S14-C3 — `loadReturnToHomeTimetable`'s javadoc argues against its own body

`Layout.loadReturnToHomeTimetable` (`src/org/traincontrol/automation/Layout.java:8270-8332`). The
javadoc says:

> *"Capture is forced off for the load: with it on, every move would be appended to the timetable a
> second time as though the operator had recorded it."* (`:8283-8284`)

Nineteen lines below, in the body:

```java
        // Capture is left exactly as the operator set it.  Turning it off around the load covered the
        // load only, and the moves are appended during the RUN - addTimetableEntry now excludes them
        // for as long as timetableSequential is set, which is the whole duration.
```

Nothing in the method touches `timetableCapture`. Two further problems in those four lines:

- the replacement comment names the wrong flag. `addTimetableEntry` (`:815`) tests
  `!this.timetableExecuting`, not `timetableSequential` - which is the whole reason
  `timetableExecuting` exists, per its own field comment at `:614-616`. The guard is *stronger* than
  the comment claims, so the behaviour is right and the explanation is about the flag it replaced.
- the javadoc's warning is about a loss its only caller prevents: *"This replaces the current
  timetable. Save it first if it matters"* (`:8273-8275`), where `TrainControlUI:23242-23248` takes a
  copy (*"Borrowed, not replaced"*) and restores it in a `finally` at `:23356`.

**How I know.** Read both, at `:8283` and `:8301-8303`, and `addTimetableEntry`'s guard at `:815`, and
the caller at `:23242` and `:23356`.

**What I would change.** Delete the two stale javadoc paragraphs and correct the flag name in the
comment that replaced one of them. This is the failure mode the SOP's comment rule is about: a reader
who trusts the javadoc concludes capture is off during a Return Home load, and would not think to look
nineteen lines down for the sentence that says it is not.

### S14-C4 — "one second" is never true

`Locomotive.toggleF(int)` (`src/org/traincontrol/base/Locomotive.java:1011-1019`):

```java
    /**
     * Turns a function on for one second, then off
     ...
    public Locomotive toggleF(int f)
    {
        return this.toggleF(f, 1000);
    }
```

`MarklinLocomotive` overrides it (`:929-933`) with `PULSE_FUNCTION_DURATION`, which is `300`
(`:35`). Every concrete locomotive in the application is a `MarklinLocomotive`, so the base body is
unreachable and the duration the javadoc states is never the one used.

**How I know.** Read both, plus the constant. The override has its own correct javadoc (*"Turns a
function on for the default pulse function duration"*), which is what makes the base one misleading
rather than merely old.

**What I would change.** Either state the real default in the base javadoc and say the subclass decides
it, or delete the base implementation and make the method abstract. A number in a javadoc that no
caller can obtain is worse than no number.

### S14-C5 — the export writes straight over its target

`AutoJSONExport.jsonSaveAsActionPerformed` (`src/org/traincontrol/gui/AutoJSONExport.java:125`):

```java
                Files.write(Paths.get(f.getPath()), json);
```

Default options are `CREATE + TRUNCATE_EXISTING + WRITE`: the target is emptied when the write opens
and is incomplete until it flushes. `Util.writeAtomically` exists for exactly this and says so
(`src/org/traincontrol/util/Util.java:501-507`): *"Opening a file for writing empties it immediately,
so from the first byte until the last is flushed the only copy of the data is incomplete."* Eight other
sites use it - `TrainControlUI:2409`, `:2573`, `:26747`, `:26827`; `MarklinControlStation:1702`;
`CS2File:2117`; `LayoutDiagram:543`, `:1629`; `AutonomyCompanionStore:4935`. This is the one writer
that does not.

`JFileChooser.showSaveDialog` does not prompt on overwrite and none is added here, and the extension
filter filters the view rather than the selection - so the operator can point it at an existing export,
or at `autonomy.json` itself.

**How I know.** Read, and the inventory of `writeAtomically` call sites measured with `grep`. What keeps
this at C: the default filename is timestamped to the second (`:108-110`), so hitting an existing file
takes a deliberate re-selection, and what is clobbered is a copy rather than live state.

**What I would change.** `Util.writeAtomically`, as the other eight do. One line, and it removes the
asymmetry a reader otherwise has to explain.

### S14-C6 — `powerState` is read without the monitor it is written under

`MarklinControlStation:277-278` declares it plain:

```java
    // Is the power turned on?
    private boolean powerState = true; // default to true unless power is turned off
```

`setPowerState` (`:3624-3631`) writes it inside `synchronized(this)` with a `notifyAll()`, and
`waitForPowerState` (`:873-892`) reads it under the same monitor. `getPowerState()` (`:3615-3618`) does
not - and that is the reader everyone uses: `MarklinRoute:840` on the route thread,
`LayoutLabel:362`/`:376` and `TrainControlUI:21962` on the EDT. There is no happens-before edge, so a
route thread can see the power as on after a STOP has been processed, and skip the
`route.powerTurnedOffCondition` branch. `on` (`:276`), written by `setNetworkCommState` (`:2647`) from
the menu and read by `exec` (`:2611`) from every command thread, has the same shape.

The same file went out of its way to get this right four times, each with the reason written out:
`locIdCache` (`:257-260`), `pingStart` (`:305`), `pingOutstandingSince` (`:317`) and
`TEST_CS2_ADDRESS` (`:209`). These two are what the sweep missed.

**How I know.** Read - the declarations, the writer's monitor, the getter, and the four volatile
siblings' comments. A stale read, not corruption, and I did not measure one.

**What I would change.** `volatile` on both, with the one-line reason the siblings carry. If the
`notifyAll()` means the monitor has to stay for `waitForPowerState`, `volatile` alongside it is still
correct and is what `locIdCache` does.

### S14-C7 — two of the three commands routed to feedback are never parsed, and an unknown id creates a module

`CS2Message.isFeedbackCommand` (`src/org/traincontrol/marklin/udp/CS2Message.java:378-383`) answers for
three commands:

```java
        return this.command == CAN_S88_REPORT       // 0x21
            || this.command == CAN_SENSOR_EVENT     // 0x23
            || this.command == CMD_ACC_SENSOR;      // 0x11
```

`MarklinFeedback.parseMessage` (`src/org/traincontrol/marklin/MarklinFeedback.java:85`) acts on one:
`if (m.getCommand() == CS2Message.CMD_ACC_SENSOR)`. And `MarklinControlStation.receiveMessage`
(`:2435-2444`) treats an id it does not recognise as a new device:

```java
                MarklinFeedback feedback = this.feedbackDB.getById(id);

                if (feedback != null) { feedback.parseMessage(message); }
                else                  { newFeedback(id, message); }
```

where `id` is `message.extractShortUID()` (`:2424`) - bytes 2 and 3 of the payload. For a 0x21 or 0x23
frame those bytes are not a sensor number, so the program creates a feedback module under whatever they
happen to hold, parses nothing into it, and `saveState` (`:1649-1652`) persists it.

**How I know.** Read, and one measurement that came back negative. I could not establish that such a
frame reaches the application - so this is a trap rather than a demonstrated defect, and it is filed
under the SOP's rule that an unreachable defect is worth fixing and not worth a changelog entry. The
measurement: Adam's saved `LocDB.data` (399,607 bytes, written 17:35 today) contains no
feedback-named string at all, so nothing suggests phantom modules are accumulating on his railway.
`MarklinFeedback`'s guard is also stricter than its accessory sibling's - `if (m.getLength() == 8)`
(`MarklinFeedback:87`) against `if (m.getLength() >= 6)` (`MarklinAccessory:158`) - with no `else` and
no log line, so a frame of any other length is dropped silently; I found no station that sends one.

**What I would change.** Make `isFeedbackCommand` answer for the command the feedback path actually
parses, and decide deliberately what the other two are - if 0x21 and 0x23 are not s88 events, they do
not belong in a predicate named for feedback. Separately, `newFeedback` should not be reached for a
message `parseMessage` would refuse: creating a device from a frame you have decided you cannot read is
the step that persists the mistake.

### S14-C8 — the railway overrides a typed delay and `behaviour.md` does not say so

`MarklinRoute.execRoute` (`src/org/traincontrol/marklin/MarklinRoute.java:1022`) waits
`Math.max(rc.getDelay(), DEFAULT_SLEEP_MS)` plus `SLEEP_INTERVAL` between commands, so a delay between
1 and 149 is replaced by 150. `X8-C2` and `X8V-C7` made that visible in the route editor, which now
shows the number the railway will use rather than the number stored
(`RouteEditorFrame.delayTheRailwayWillUse`, `:2755-2762`, and the renderer at `:2992`). The rule itself
is written out three times in the code and nowhere in `behaviour.md`:

```
$ grep -i "delay" docs/reference/behaviour.md
(no matches)
```

A delay is something the operator types and the railway then silently raises - which is a fact about
what the railway does, and `behaviour.md` is where those live. The same gap was filed for Control+E as
`X8-C10`.

**How I know.** Executed the grep above; read the floor at `MarklinRoute:1022`, the constant at `:46`,
and the editor's two uses of it. Note while documenting it that the actual gap is
`SLEEP_INTERVAL + max(delay, 150)`, so the editor's number is the floor on the *delay*, not on the
wait.

**What I would change.** A short paragraph in §8 of `behaviour.md`: the floor, that zero means "no
delay asked for" and is left alone, that `THREEWAY_ROUTE_DELAY_MS` is defined as sitting above it, and
that the editor shows the raised number rather than the stored one.

---

## D — not defects

| id | what |
|---|---|
| S14-D1 | withdrawn, opened as a suspected A: the conditional `touched()` does not leave the autonomy graph describing deleted track |
| S14-D2 | `readLayoutIndexIds` returning a duplicate is deliberate and pinned - which is what narrows `S14-A2` |
| S14-D3 | a route that names a signal registering a switch is not a defect |
| S14-D4 | Adam's own railway has no duplicate page id, so `S14-A2` does not affect it today |
| S14-D5 | the route editor's displayed delay floor does not write itself into a stored route |
| S14-D6 | `roomToGrow`'s per-dimension rewrite is asked by the menu and by every gesture that grows |
| S14-D7 | the working tree changed under this pass, and one test failure belongs to that rather than to `src/` |

### S14-D1 — withdrawn (opened as a suspected A): the conditional `touched()` does not leave autonomy describing track that has been deleted

The newest round made the graph rebuild conditional
(`src/org/traincontrol/automationui/AutonomySession.java:2112`):

```java
        if (changed || (moves != null && !moves.isEmpty())) touched();
```

with a comment arguing that for a built-over-only call *"there is nothing to rebuild FROM unless
something was stored"*. I thought that was false, and for a good reason: the `TileGraph` is built from
the diagram (`rebuild()`, `:345`, `graph = new TileGraph(pages, store.getExcludedPages())`), and
deleting or pasting a piece of plain track changes the diagram whether or not anything was stored about
that square. So after deleting unannotated track the graph still holds the deleted tile and the edges
traced through it.

It is true that the graph goes stale, and it does not matter, because **every door out of the editor
re-derives the session from scratch.** Closing the track editor reaches
`TrainControlUI.layoutEditingComplete` → `layoutRefreshCompleteInternal` (`:22388-22435`), which calls
`resetAutonomySession()` at `:22414` and reloads the configuration at `:22422`; a page or mode switch
without closing goes through `layoutEditingCompleteThen` (`LayoutEditor:6099`) to the same place, or
through `parent.autonomyEditorClosed()` (`:6076`) for the autonomy-mode branch. `rememberAutonomy`
(`LayoutEditor:601-609`) only writes the setup to disk and deliberately does not rebuild. So the
staleness cannot outlive the editor, and nothing reads the graph for a railway decision while the
editor holds it.

Recorded rather than deleted because the argument in the comment is the wrong one for the right
conclusion: it is not that there is nothing to rebuild from, it is that the rebuild is owed to somebody
else who always does it. If that reset is ever removed, this becomes a defect with nothing to say so.

### S14-D2 — `readLayoutIndexIds` returning a duplicate is deliberate, and that is what `S14-A2` is narrowed against

`regression.testPageIdsAreDurable.testTwoPagesCannotShareAnId` writes a hand-made index with two pages
at `.id=4` and asserts, as a precondition, that the reader gives both the same id. Its javadoc states
the whole misattachment mechanism - *"`setPageIds` inverts name-to-id into id-to-name, so the second
page silently wins the number and the first page's settings resolve to the SECOND page's track"* - and
`DR-B4` answered it in `writeLayoutIndex`'s `issued` gate. So the mechanism is known, the remedy was
chosen, and the reader's behaviour is pinned on purpose.

`S14-A2` is therefore not that mechanism. It is that the writer is the only thing that resolves it and
the writer runs only on a page add, rename, delete or combine - so between opening such a layout and
the next page operation the store is keyed from a reader that still produces the duplicate. That is why
the entry above spends its measurement on which real files have the shape and on when the writer runs,
rather than on the inversion.

### S14-D3 — a route that names a signal and registers a switch is not a defect

`MarklinRoute:832-835` calls `this.network.setAccessoryState(idd, rc.getProtocol(), state)` and drops
`rc.getAccessoryType()`, which `RouteCommand` does carry
(`KEY_ACCESSORY_TYPE`, set at `RouteCommand:963-966`); `MarklinControlStation:3296-3305` then creates a
`newSwitch` where `Edge.java:172-179` would have dispatched on the type. So a route line naming a
signal registers `Switch N` against a clean database, and `Route.java:323` still says
`// TODO rc should maintain the accessory type`.

Not a defect on this railway: a signal and a switch are the same device and `accessoryType` is a
display property, not a behavioural one. `getAccessoryByName` (`MarklinControlStation:2205-2229`) swaps
the prefix on a miss and `RemoteDeviceCollection.add` (`:75`) strips the stale name, so resolution
works either way. The only consequence is the wording of the tile and the log line.

### S14-D4 — Adam's own layout index has no duplicate

`cs2_sample_layout/config/gleisbild.cs2`, read and not written, states `.id` on all five pages: 5, 1,
2, 3, 4 against `1 - Main` … `5 - Test`. Five pages, five ids. So `S14-A2` is not a defect he has hit,
which is the distinction the changelog rule turns on - and it is also why the finding rests on
`Oles kreds` and `sample_layout` rather than on his railway.

### S14-D5 — the displayed delay floor does not write itself back

The renderer raises a sub-floor delay for display (`RouteEditorFrame:2992`) while
`stateSignature()` (`:459-501`) compares `row.getDelay()`, the stored value. So opening a route with a
delay of 100 shows 150, does not mark the window dirty, and does not prompt on close; editing any other
cell rebuilds the row from `at.getDelay()` (`:3083`) and keeps 100. The editor shows a number that is
not the stored number, which is the point of the fix, and nothing reads the displayed one as a value.
Checked because a renderer that changes a value is usually a round-trip defect; here it is not.

### S14-D6 — `roomToGrow` is asked by the menu and by every gesture that grows

`X8V-C2`'s rewrite (`LayoutEditor:4479-4496`) asks each dimension only where the gesture adds to it. Its
callers are consistent: `canShiftDown` asks `roomToGrow(1, 0)` (`:4532`) and `canShiftRight`
`roomToGrow(0, 1)` (`:4552`), `growEdges` and `addRowsAndColumns` ask `roomToGrow(1, 1)` (`:4896`,
`:5041`), and the right-click menu greys Increase Size on the same predicate
(`LayoutEditorRightclickMenu:445`). `canShiftUp` and `canShiftLeft` correctly do not ask it - neither
grows the page - and `shiftDown`/`shiftRight` both call their own predicate before snapshotting
(`:4644`, `:4746`). One predicate, asked in both places, as `LE-C1` intended. No finding.

### S14-D7 — the tree changed under this pass, and one failing class belongs to that

Two things happened in the working tree during this review, neither of them mine:

- Adam committed the round under review as `a281e3a2` at 18:07:41. The content did not change, so the
  review stands; the header above records it.
- `test/regression/n8Probe.java` appeared, untracked, at 18:08, and another session was mutation-testing
  `LayoutDiagram.attribute` at the same time (its edit was labelled `N8 MUTATION` and was reverted by
  that session while this one was running). My own mutation was removed surgically so as not to disturb
  theirs; `git diff -- src` is empty and `grep -rn "s14" src/` finds nothing.

`regression.testSwitchingToACentralStationLayout` fails (`12 tests, 1 failure`) on an assertion about
`n8Probe.java` - *"1 sandbox(es) are opened outside a try ... expected [[]] but found
[[n8Probe.java, in public void probePageLinks() throws Exception]]"*. It failed with my mutation applied
and it failed with my mutation removed, and it passed in the baseline run at 18:05 before that file
existed. It is about a test source, not about `src/`, and it is not a finding of this pass - recorded
so the next reader does not spend the time I did working out whose it was.

---

## What I did not cover

- **The battery was not run**, and the runner was locked for the first two thirds of the pass. Six
  classes were run, totalling 75 tests, all green except `S14-D7`'s. Everything below the level of the
  files named above is unmeasured here.
- **`S14-A1` and `S14-B4` were not executed.** Each says what would be needed.
- **The autonomy graph and its runtime** - `Layout.isPathClear` and the occupancy, length, coverage and
  reversal rules around it - were read against `behaviour.md` §1-§5 and no disagreement was found, but
  that is a reading of the two documents side by side rather than a measurement. `HomeStaging`,
  `GraphReducer`, `TileGraph`, `AutonomyChecks` and `AutonomyBuilder` were opened only where something
  else led into them.
- **The layout editor's rotate gesture**, which `X8` left unfiled for lack of evidence, was not
  re-examined.
- **`docs/manual-tests/`** was not opened except to confirm `S14` was free as a prefix. That is now four
  passes in a row that have declined it.
- **The uncommitted changes under `cs2_sample_layout/`** are Adam's running application's and were read
  only - its index, for `S14-D4`.
- **The message bundles** were not checked; `X8-D1` covers them for this window.
