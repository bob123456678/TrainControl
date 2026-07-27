# TrainControl - Code Review, July 2026

Full read-through of `src/` (automation, base, marklin, marklin/file, marklin/udp, util) plus targeted
review of the GUI layer, looking for logic errors and latent bugs. Reviewed against 2.7.5; the fixes
landed in 2.8.0.

**How to read this.** It is a working log, kept in the order things were found, so later sections
correct earlier ones - several findings were withdrawn once the code disproved them, and others had
their severity revised. **The status table at the head of each section is authoritative.** The prose
under an individual finding records what was believed when it was written and may have been superseded
by a note further down.

Every A and B item is resolved. Three findings were withdrawn as mistaken. Nothing is outstanding.

Each item lists the file/line, what is wrong, and the observable consequence.

---

## A. High — wrong behaviour on the layout, or data silently lost

> **Status - all eight resolved.**
>
> | # | Finding | Outcome |
> |---|---|---|
> | A1 | CS3 route mixing locomotive commands with a delayed accessory was dropped entirely | **Fixed** |
> | A2 | Edge config commands could not resolve a signal name at a switch's address | **Fixed.** Severity lowered to Medium - a signal and a switch at one address are the same decoder |
> | A3 | A missing accessory did not stop the train departing | **Fixed** |
> | A4 | `autoloc` conditions threw NPE and killed the route's monitor thread | **Fixed** |
> | A5 | CS2 condition parser stored a condition once per trailing token | **Fixed, but downgraded.** `parseFile` flattens each group through a `HashMap` that deterministically yields `hi` before `kont`, so no real file ever produced the unsatisfiable pair. Changelog entry withdrawn |
> | A6 | Re-enabling an s88 route left two monitor threads running | **Fixed** |
> | A7 | Edges failing to unlock in non-atomic mode never released their lock edges | **Fixed.** The original analysis was wrong about what leaked, and the obvious fix would have been unsafe - see the finding |
> | A8 | The CAN listener died permanently on the first network hiccup | **Fixed** |


### A1. A CS3 route that mixes locomotive commands with a delayed accessory is dropped entirely

> **FIXED.** `MarklinRoute.setDelay` now skips commands that carry no address, via a new
> `RouteCommand.hasAddress()`. Coverage moved to `testRoutes`; changelog entry under v2.8.0.

`marklin/MarklinRoute.java:501-513`, `marklin/file/CS2File.java:1199`, `base/RouteCommand.java:315-318`

`MarklinRoute.setDelay(Integer key, ...)` walks every command in the route and calls
`rc.getAddress()`:

```java
for (RouteCommand rc : this.route)
{
    if (rc.getAddress() == key)   // getAddress() = Integer.parseInt(commandConfig.get(KEY_ADDRESS))
```

`TYPE_LOCOMOTIVE` and `TYPE_LOCOMOTIVE_DIRECTION` commands have no `ADDRESS` key, so
`Integer.parseInt(null)` throws `NumberFormatException`. (Verified:
`testA1_cs3RouteWithLocoCommandThenDelayedAccessoryIsDropped`, with two controls isolating the cause
to command ordering plus the presence of a delay.)

The shipped fixtures do not cover this: `test/CS3_automatics.json` contains 601 items, all of type
`mag`, and **not one** carries a `sekunde` key — so `setDelay` is never reached in the existing CS3
tests. The CS2 path is safe because `parseRoutes` only ever appends accessory commands.

In `parseRoutesCS3` the items are processed in JSON order, and a `mag` item calls
`r.setDelay(address, …)` whenever the item has a `sekunde` key. If a `speed` or `dir` item was
added to the route first, the exception propagates to the per-route
`catch (NumberFormatException | JSONException)` at `CS2File.java:1249` and **the whole route is
skipped** with a generic "invalid CS3 route" message. The user sees a route silently missing after
import with no indication of the real cause.

`setDelay` should skip commands that have no address (or `getAddress()` should be null-safe).

### A2. Edge config commands cannot resolve a signal name at a switch's address
`automation/Edge.java:122-143`, `marklin/MarklinControlStation.java:2454-2473`

> **FIXED**, alongside A3, since A3 makes this user-visible. `getAccessoryByName` now retries under
> the other accessory type when the name misses — the address and protocol suffix still have to match
> exactly — and `validateConfigCommand` no longer rejects a signal at a switch's address nor invents
> an accessory while checking. Coverage in `testAccessory`.
>
> Note the *structural* fix would be for edges to store address + protocol instead of a name. That
> changes the autonomy file format, and names are what the operator reads and edits in that file, so
> resolving names robustly was chosen instead.
>
> **Validation result: DOWNGRADED to Medium.** My original framing ("a signal can never be created")
> overstated this. A signal and a switch are the *same device* on the wire — the distinction is
> purely how TrainControl displays and interprets it — and creating accessories from edge config
> commands is a fallback path, not the primary workflow (which is importing from the CS2/CS3 layout).
> So "no signal object gets created" is close to a non-event on its own. What survives is the
> knock-on into A3, described at the end of this item.


```java
if (null == control.getAccessoryByName(accessory))
{
    ...
    Accessory existing = control.getAccessoryByAddress(address, protocol);

    if (existing != null && existing.getType() != type)
    {
        throw new Exception(I18n.f("acc.commandConflictSameAddressMustRename", ...));
    }

    if (type == SIGNAL) control.newSignal(...);
```

`getAccessoryByAddress()` is not a pure getter — when the address is unknown it **creates and
registers a new `Switch`** and returns it (`MarklinControlStation.java:2471`). So `existing` is
never null, and for `type == SIGNAL` the check `existing.getType() != type` always fires. The
result:

* The signal is never created.
* A phantom `Switch N` is left in `accDB` and gets persisted into `LocDB.data`.
* The user gets a misleading "conflict, please rename" error for an address that was free.

Knock-on effect in `Layout.fromJSON` (`automation/Layout.java:3728-3738`): the exception is
swallowed, the edge command is stored anyway, and later `configureEdge` looks the accessory up by
name, finds nothing, and invalidates the entire autonomy layout at run time.

**What actually matters here — the coupling to A3.** Since a signal and a switch are the same device,
the type check at `Edge.java:134` is enforcing a distinction that has no physical meaning, and it does
so against an accessory that `getAccessoryByAddress` may have invented one line earlier. The harm is
not the missing signal object, it is that in `Layout.fromJSON` the exception is swallowed and the edge
keeps a config command whose **name** will never resolve — which is precisely the precondition for A3:
the train departs over an accessory that is never commanded.

An autonomy file referencing `Signal 5` behaves completely differently from one referencing
`Switch 5`, for the same physical decoder: the switch is auto-created and works, the signal silently
leaves a dead command on the edge.

Underneath both is a name/UID identity problem. `accDB` keys accessories by UID (address + protocol),
so `Switch 5` and `Signal 5` collide on one entry, while `names` can hold both strings pointing at it.
`getAccessoryByName("Signal 5")` therefore returns whatever object occupies that UID — possibly an
object named `Switch 5` — or null if that name was never registered. Edge config commands resolve by
name; the database is keyed by UID. Resolving edge commands by address + protocol (as
`Route.evaluate` already does) would remove the whole class of problem.

On an installation affected by B6 the failure is also indistinguishable from a real conflict, since a
switch genuinely does occupy every address by then.

**Resolution — no user-visible regression from A3.** With A2 and A3 in the same release, both branches
resolve and no path is refused for a name that could have worked:

| address 3 holds | `getAccessoryByName("Signal 3")` | outcome |
|---|---|---|
| `Switch 3` | prefix swap hits it | commanded directly; nothing created |
| nothing | swap also misses | `validateConfigCommand` reaches `newSignal` and creates `Signal 3` |

Note that `newSignal` was previously **unreachable** for a free address: `getAccessoryByAddress`
invented a `Switch` and the type check then rejected the signal against it, so the creation call below
never ran. Removing that check and that lookup is what makes the second row work. The only residue is
cosmetic — an edge whose command says `Signal 3` while the database holds `Switch 3` resolves and
operates correctly, but the UI displays the accessory under its registered name.

### A3. A missing accessory does not stop the train departing
`automation/Layout.java:1069-1091` and `1336-1379`

> **FIXED.** `configureEdge` now returns a status and, during the `isPathClear` preview, marks the
> configuration invalid so the path is never offered; `configureAndLockPath` releases its locks and
> returns false if the real configure pass fails. Coverage in `testAutonomyPathValidation`.
>
> A2 shipped in the same release, which is what makes this safe — see the resolution note under A2.
> On its own this fix would have refused paths whose accessory names could not resolve, including the
> `Signal 3/4/5` in the shipped `sample_autonomy.json`.


`configureEdge(e, null)` returns early when `control.getAccessoryByName(name)` is null. It calls
`this.invalidate()` — but the caller `configureAndLockPath` keeps looping over the remaining edges,
returns `true`, and `executePath` starts the locomotive. The `isValid()` guard at the top of
`executePath` (line 2252) already ran before this point, so it does not help.

Path Integrity Validation does not catch this either: `validatePathActuation` only collects
accessories that resolve (`acc != null`, line 1402), so a missing one is simply not checked.

Net effect: the train departs over a switch that was never commanded, and the only symptom is a
log line.

### A4. `autoloc` route conditions throw NPE and kill the route's monitor thread
`base/Route.java:223-251`

> **FIXED**, in two layers. `Route.evaluate` now null-checks the locomotive's location, so an unplaced
> locomotive simply fails the condition. And `executeAutoRoute` wraps its condition evaluation and
> route execution in a try/catch, so **no** future failure of any kind can cost a route its monitor
> thread. Coverage in `testRoutes` (four tests, including the end-to-end monitor survival case).


```java
Integer.toString(rc.getAddress()).equals(
    control.getAutoLayout().getLocomotiveLocation(control.getLocByName(rc.getName())).getS88()
)
```

`Layout.getLocomotiveLocation()` returns `null` when the locomotive is not currently placed on any
point in the graph (`automation/Layout.java:1795-1806`). The `||` short-circuits, so this only runs
when the first (milestone) check fails — i.e. exactly the common case.

The NPE propagates out of the `while (this.enabled)` loop in
`MarklinRoute.executeAutoRoute()` (`marklin/MarklinRoute.java:133-166`), which is a bare `Thread`
with no handler. **The route stops monitoring its S88 permanently**, with no message to the user;
it looks enabled in the UI but will never fire again. Only a restart or an edit/re-enable brings it
back.

### A5. CS2 condition parser adds a condition once per trailing token — *currently masked, see below*
`marklin/file/CS2File.java:719-764`

> **FIXED.** The condition is now stored once, after every token in the group has been read, so the
> result no longer depends on token order at all — which also removes the `LinkedHashMap` trap
> described below. Existing `testA5_*` assertions inverted to match.
>
> **Validation result: DOWNGRADED.** The structural defect was real and reproducible, but it did **not**
> misfire on real CS2 files, because of the accident described under "why this is currently masked".


The "add final condition to route" block is inside the `for (String info : infos)` loop, not after
it:

```java
for (String info : infos)
{
    if (info.contains("="))
    {
        ...
        if ("kont".equals(kv[0])) { ...; conditionS88 = ...; }
        if ("hi".equals(kv[0]))   { s88Status = ...; }
    }

    // "Add final condition to route"  <-- still inside the loop
    if (conditionS88 != 0)
    {
        r.addConditionS88(conditionS88, s88Status != 0);
    }
}
```

Once `kont=` has been seen, **every subsequent token re-adds the condition**. With `kont=5,hi=0` the
first add uses the default `s88Status = 1` and the second uses `0`, yielding
`S88 5 == true AND S88 5 == false` — a condition that can never be satisfied, so the route silently
never fires. (Verified: `testA5_conditionIsAddedOncePerTrailingToken`.)

The block belongs after the `for (String info : infos)` loop closes (line 764).

**Why this is currently masked.** In a real `fahrstrassen.cs2` the tokens are written `kont=` first,
then `hi=`:

```
 .S88Flag
 ..kont=3
 ..hi=0
```

but `parseFile` accumulates each `..key=value` group into a plain `HashMap` (line 409) before
flattening it with `toString()`. For the key pair `{"kont","hi"}` that map iterates **"hi" first**
(bucket 1 vs bucket 8 at the default capacity), so by the time `kont=` is read there is no trailing
token left and the parse comes out correct. `testA5_realCs2FileCurrentlyParsesConditionsCorrectly`
pins this against `test/fahrstrassen.cs2`.

That makes this a latent trap: switching that `HashMap` to a `LinkedHashMap` — the obvious fix for the
unrelated `.S88Flag` key-association problem below — restores file order (`kont`, then `hi`) and turns
**every `hi=0` condition in every imported CS2 route into an unsatisfiable one**. Fix the loop scope
before touching the map.

**Adjacent, found while validating:** the `.S88Flag` line never matches the key regex
`^ \.[a-z]+$` (line 472) because of its capital letters and digits, so `lastKey` is never set to
`S88Flag`. The condition groups are appended to whatever lowercase key came last — which happens to be
`item` only because the preceding route block ended with `.item`. A route whose `.S88Flag` is not
preceded by an `.item` would have its conditions attached to the wrong key and silently dropped.

Also dead: the "already saw a previous condition" branch (lines 744-748) can never execute, because
`conditionS88` is reset per piece and a `Map` cannot hold two `kont` keys in one group.

### A6. Re-enabling an S88 route can leave two monitor threads running
`marklin/MarklinRoute.java:118-173`, `marklin/MarklinControlStation.java:549-556`

> **FIXED.** `executeAutoRoute` now tracks its monitor thread and refuses to start a second one while
> the first is alive. Interrupting the parked thread instead was not viable - the feedback wait loops
> re-set the interrupt flag and would spin. Coverage in `testRoutes`.

`disable()` only sets a flag. The monitor thread is blocked in
`loc.waitForClearThenOccupied(...)` and does not exit until the sensor next fires. If the route is
re-enabled before then — e.g. `applyAutonomyRouteActivations` disables it during one autonomy load
and re-enables it on the next (`r.enable(); r.executeAutoRoute();`) — a second thread starts while
the first is still alive and `enabled` is true again.

Consequence: the route **fires twice for every trigger**, and each disable/enable cycle in that
window adds another thread. `executeAutoRoute()` should refuse to start if a monitor is already
live.

### A7. Edges that fail to unlock in non-atomic mode are never released
`automation/Layout.java:1578-1638` and `2571-2573`

`unlockPath` returns the list of edges it deliberately skipped ("skipping unlock due to non-atomic
paths"). The only caller discards the return value:

```java
this.unlockPath(path, loc);
```

> **FIXED** — but see the correction below: what actually leaks is the skipped edge's **lock edges**,
> not the edge itself. `unlockPath` now releases them. Coverage in
> `testAutonomyPathValidation.testUnlockPathReleasesLockEdgesOfASkippedEdge`.

**Correction to my original analysis.** I claimed the skipped edge stays `occupied` forever. It does
not, and the test I first wrote to "confirm" that set up a state which cannot occur in production.

Tracing `executePath`'s early unlock properly: it releases edge `index` and clears edge `index`'s
*start* point in the same step. Edge `index`'s start is edge `index-1`'s end, and indices are added to
`toUnlock` in order — so a point can only become free *after* the edge ending at it was already
released. Therefore any edge whose end point another locomotive has claimed was necessarily
early-unlocked first, and its own flag is already false. The skip is harmless for the edge, and the
returned list is genuinely inert.

What the skip did lose is the **lock edges**. The early unlock uses `setLockedEdgeUnoccupied()`, which
clears only the edge's own flag and deliberately leaves lock edges held until the path completes (a
crossing may still be in use). `unlockPath`'s `setUnoccupied()` is what finally releases them — so
skipping it left the crossing marked occupied for the rest of the session, blocking every path through
it. That is the real leak, and it is now released explicitly in the skip branch.

The obvious fix — always calling `setUnoccupied()` — would have been **unsafe**: in non-atomic mode
another locomotive can legitimately have re-locked that edge after the early unlock, and `occupied` is
a single boolean with no owner, so releasing it would clear that locomotive's lock. The original guard
was right; only its treatment of lock edges was wrong.

**Preconditions:** `atomicRoutes = false` (not the default), lock edges configured, a path long enough
for an intermediate point to be released mid-run, and another locomotive claiming that point.

### A8. The CAN listener dies permanently on the first network hiccup
`marklin/udp/NetworkProxy.java:136-187`

> **FIXED.** The catch now sits inside the receive loop: a recoverable error is logged, backed off
> briefly and retried, while a closed socket still ends the thread. `sendMessage`'s reopen check moved
> ahead of the send so it is actually reachable, and `socket` is now `volatile` so the reader sees a
> replacement. Coverage in `testNetworkProxy` (transient survives / close terminates /
> send reopens).

The comment says "Do not exit on error, simply close the socket connection", but the `try` wraps the
entire `while (true)` loop. Any `IOException` from `socket.receive()` exits the loop, closes the
socket, and the reader thread terminates. TrainControl keeps running and keeps *sending* commands,
but never receives another CAN message — no feedback, no accessory echoes, no power-state changes,
and (with 2.7.5's new feature) Path Integrity Validation fails every path. The only visible sign is
one "network.fatalError" log line.

The `catch` should be inside the loop.

Related, same file, `sendMessage` (lines 90-117): the socket-reopen check runs *after* a successful
`send` and is therefore unreachable — if the socket is closed, `send` throws and we return `false`
without reopening. `socket` is also non-`volatile` while `ReadMessages` reads it.

---

## B. Medium — incorrect results, crashes in specific configurations

> **Status - seventeen fixed, one withdrawn, one accepted as-is.**
>
> | # | Finding | Outcome |
> |---|---|---|
> | B1 | Adding columns built columns of the wrong height | **Fixed** |
> | B2 | Editing a tile's address stored double the logical address | **Fixed**, latent only - the editor's commit-time reparse discards the in-memory value |
> | B3 | Changing a tile's address did not rebind its accessory | **Withdrawn - not a bug.** `syncLayouts` rebinds during the commit-time reparse, and the proposed fix would have created a phantom accessory at every address typed |
> | B4 | `AND` / `OR` substring-replaced inside locomotive names | **Fixed** |
> | B5 | Condition rendering passed the accessory type as the protocol | **Fixed** |
> | B6 | Read-only lookups create accessories, filling the database with phantoms | **Accepted, no change.** Tolerable because the keyboard returns an existing signal rather than replacing it |
> | B7 | Validation could not detect a failure on a never-actuated accessory | **Fixed** |
> | B8 | Function array sized by entry count but indexed by function number | **Fixed**, defensive only - 243 locomotive blocks across the real fixtures have no gaps. Changelog entry withdrawn |
> | B9 | Feedback tile set was not thread-safe | **Fixed** |
> | B10 | Swing calls off the EDT in the tile-highlight path | **Fixed** |
> | B11 | CAN message parsing did not mask its bit fields | **Fixed**, confirmed against live captured packets |
> | B12 | Every received message aliased the shared receive buffer | **Fixed.** Also repaired a comparison in `equals` that had been silently vacuous |
> | B13 | Stream leaks on the HTTP paths | **Fixed** |
> | B14 | `Edge.isOccupied` could NPE | **Fixed** |
> | B15 | `RouteCommand.fromLine` threw unchecked exceptions instead of a readable error | **Fixed** |
> | B16 | `getIP()` mangled any address entered as a hostname | **Fixed**, confirmed from the operator's own logs |
> | B17 | `newAccessory` took the actuation count from the wrong address, and created one there | **Fixed.** Fired on every startup of the sample layout |
> | B18 | `RemoteDeviceCollection.add` stranded the previous name for an id | **Fixed** |
> | B19 | A route that threw mid-execution stayed disabled for the session | **Fixed** |


### B1. Adding columns to a track diagram builds columns of the wrong height

> **FIXED.** The new column is now sized by `sy`.
`base/LayoutDiagram.java:330-359`

```java
for (int x = 0; x < numColumns; x++)
{
    List<LayoutDiagramComponent> newColumn = new ArrayList<>();

    for (int i = 0; i < sx; i++)   // <-- should be sy
    {
        newColumn.add(null);
    }
    grid.add(newColumn);
    sx += 1;   // and sx grows inside the loop, compounding it
```

`grid` is a list of *columns* of length `sy`. For any layout where `sy > sx` (a tall, narrow
diagram) the new column is too short, and `getComponent(x, y)` — which bounds-checks `y` against
`grid.get(0).size()`, i.e. the *old* column length — throws `IndexOutOfBoundsException`. Reached via
`shiftRight()` and the editor's add-column action.

### B2. Editing a tile's address stores double the logical address
`base/LayoutDiagramComponent.java:856-892`

```java
this.rawAddress = address * 2;
this.address    = address * 2;   // <-- should be `address`
```

The parser establishes the invariant `address == rawAddress / 2` (see
`CS2File.java:1986-1998`): `address` is the logical, 1-based address, `rawAddress` is the CS2
`artikel` value. `getLogicalAddress()` derives correctly from `rawAddress`, so the *file export* is
fine, but `getAddress()` now returns 2× the truth.

Consequences: the tile tooltip (`toSimpleString()`, line 623) shows the wrong address, and
`MarklinControlStation.syncLayouts()` — which does `int newAddress = c.getAddress() - 1;` and binds
the accessory from it (line 326) — would bind the wrong accessory if it runs against an in-memory
edited component. Same off-by-2 for green uncouplers.

### B3. Changing a tile's address does not rebind its accessory
`gui/LayoutEditor.java:950-970`

`editAddress()` calls `lc.setLogicalAddress(...)`, `layout.addComponent(...)`, `lc.setProtocol(...)`
and `refreshGrid()` — but never updates `lc.setAccessory(...)`. The component keeps its old
`Accessory` reference, so after an address change the tile still switches the *old* address and
displays the *old* state, until the layout is saved and re-synced.

### B4. `AND` / `OR` are substring-replaced inside names when parsing route conditions

> **FIXED.** `AND` / `OR`, so they only split out when they stand alone.
>
> **Accepted limitation:** a locomotive whose name contains a standalone `OR`/`AND` word, or a
> parenthesis, still cannot be used in an `autoloc` condition — the name is genuinely ambiguous with the
> expression syntax, and solving it properly needs quoting in the format. Confirmed as acceptable: such
> names are not realistically usable here.
`base/NodeExpression.java:232-247`

```java
text = text.replaceAll("\\(", "\n(\n").replaceAll("\\)", "\n)\n")
           .replaceAll("AND", "\nAND\n").replaceAll("OR", "\nOR\n");
```

These are plain substring replacements with no word boundaries. An `autoloc,<locname>,<s88>`
condition referencing a locomotive whose name contains `OR` or `AND` — extremely common in this
domain: `NORD`, `MOTOR`, `ORIENT`, `GRAND`, `BR 01 NORDEXPRESS` — is split mid-name and the
expression fails to parse ("Ensure parentheses are matched…"). The user has no way to tell why.

Should use `\bAND\b` / `\bOR\b`, or better, only treat a line that is exactly `AND`/`OR` as an
operator (`fromTextRepresentation` already compares with `line.equals("AND")`).

### B5. Condition rendering looks accessories up with the accessory *type* as the protocol

> **FIXED.** Resolved via `getAccessoryByName` built from the command's own protocol, which also stops
> this display path inventing an accessory the way `getAccessoryByAddress` does.
`base/NodeExpression.java:113-120`

```java
if (command.isAccessory()) acc = network.getAccessoryByAddress(command.getAddress(),
    Accessory.determineAccessoryDecoderType(command.getAccessoryType())   // "Switch" / "Signal"
);
```

`getAccessoryType()` returns `KEY_ACCESSORY_TYPE` ("Switch"/"Signal"), not the protocol.
`determineAccessoryDecoderType("Switch")` fails to parse and silently falls back to MM2. So for a
DCC accessory condition the code looks up (and, per B6, *creates*) the MM2 accessory at the same
address and takes its switch/signal type from that. A DCC signal condition can therefore render as
"Switch …". `command.getProtocol()` is right there and is what should be passed.

### B6. Read-only lookups create accessories — the database fills with phantoms
`gui/TrainControlUI.java:2786-2792`, `marklin/MarklinControlStation.java:2424-2473`,
`marklin/MarklinRoute.java:733`, `base/NodeExpression.java:116`, `automation/Edge.java:132`

> **Promoted after validation — this is not theoretical, it has already happened.** See the field
> evidence below.

`MarklinControlStation.getAccessoryByAddress()` and `getAccessoryState()` both create-on-miss and
register the result in `accDB`. They are called from pure read paths.

The worst offender is the keyboard repaint:

```java
for (int i = 1; i <= TrainControlUI.KEYBOARD_KEYS /* 64 */; i++)
{
    JToggleButton key = this.switchMapping.get(i);
    if (key != null)
    {
        if (this.model.getAccessoryState(i + offset, getKeyboardProtocol()))   // creates on miss
```

So **merely viewing a keyboard page permanently registers all 64 of its addresses**, and `saveState`
then writes them to `LocDB.data`. Same pattern, less volume, in `MarklinRoute.toCSV()` and
`NodeExpression.toTextRepresentation()` — opening the route editor or exporting routes injects an
accessory per command.

**Field evidence.** The `LocDB.data` in this working copy contains:

| | count | addresses |
|---|---|---|
| MM2 switches | 320 | 1-320, complete, no gaps |
| DCC switches | 2048 | 1-2048, complete, no gaps |
| MM2 signals | 51 | 37-157, sparse — these look genuine |

320 = 5 MM2 keyboard pages × 64 keys. 2048 = 32 DCC pages × 64. Exhaustive coverage of the entire
addressable space with no gaps is not a layout anyone built; it is the repaint loop's footprint.
Roughly 2,300 of the ~2,400 accessories in that database are artifacts.

Consequences: a bloated save file serialized on every save and every backup, every accessory list and
dropdown in the UI padded with thousands of entries, and — see A2 — **no new signal can be created at
any address, because a phantom switch already occupies all of them**.

`getAccessoryState`/`getAccessoryByAddress` should not mutate; the create-on-miss behaviour belongs in
a separate, explicitly named method that only the code that genuinely needs it calls.

Note this also masks A2 on an affected installation: `validateConfigCommand`'s rejection then looks
like a legitimate address conflict, because by that point there really *is* a switch at the address.

### B7. Path validation cannot detect a failure on a never-actuated accessory
`base/Accessory.java:33-40, 188-191`, `marklin/MarklinAccessory.java:73`

`stateAtLastActuation` is seeded from the *assumed* state at construction
(`this.stateAtLastActuation = this.switched`), and only advances on a CS echo that *changes* the
state. `isConfirmedAt(desired)` therefore returns `true` for any accessory whose assumed startup
state already matches the commanded state — even though the CS has never confirmed anything.

So the very first time a path commands a switch to the position it is believed to already be in,
validation passes unconditionally. That is arguably correct (no command needed → nothing to
confirm), but it means a decoder that has silently drifted since the last session is never caught
on the first pass. Worth documenting in the feature's tooltip at minimum; a tri-state
(`UNKNOWN / CONFIRMED_STRAIGHT / CONFIRMED_TURNED`) would make the guarantee real.

### B8. `parseLocomotiveFunctions` indexes by function number into an array sized by entry count

> **FIXED.** Both methods now collect into a map first and size the array by the highest function
> number present, with a floor of the entry count so a contiguous list is unchanged.
`marklin/file/CS2File.java:826-867` (and the identical `parseFunctionTriggerTypes`, 869-915)

```java
int[] output = new int[data.length];   // number of entries in the file
...
output[fn] = type;                     // fn is the `nr=` value
```

Contiguous `nr=0..N` lists work. A sparse list (a CS2 file that omits undefined functions) gives
`ArrayIndexOutOfBoundsException`. That escapes `parseLocomotives()` uncaught, is caught by the
blanket handler in `syncWithCS2()` (line 943), and **aborts the entire CS sync** — one malformed
locomotive costs the user every route and locomotive update in that pass.

The same blanket catch means any single bad record in `lokomotive.cs2` has this effect; the CS3
paths already handle this per-record (`describeCS3Field`), the CS2 path does not.

### B9. `MarklinFeedback.updateTiles()` mutates a plain `HashSet` from a fresh thread every call

> **FIXED.** The tile set is now `ConcurrentHashMap.newKeySet()`, and `updateTiles` no longer spawns a
> thread — `updateImage` already marshals to the EDT. Applied to `MarklinAccessory` and `MarklinRoute`
> too, which carried the same latent race.
`marklin/MarklinFeedback.java:56-72`

```java
public void updateTiles()
{
    new Thread(() -> {
        Iterator<LayoutLabel> i = this.tiles.iterator();
        while (i.hasNext()) { ...; i.remove(); }
    }).start();
}
```

`tiles` is a non-synchronized `HashSet`; `addTile()` writes to it from the EDT, and two overlapping
`updateTiles()` threads iterate/remove concurrently. `ConcurrentModificationException` (which will
kill that thread and leave tiles unrefreshed) is a matter of timing. `MarklinAccessory.updateTiles()`
does the same iteration but is only ever called from `synchronized` methods, so it is safer by
accident. Both should use a concurrent set.

### B10. Swing calls off the EDT in the tile-highlight path

> **FIXED.** The overlay is applied inline (already on the EDT) and the restore is scheduled with a
> `javax.swing.Timer`, which also fires on the EDT.
`gui/LayoutLabel.java:385-405`

The rest of this class is careful to marshal to the EDT, but the highlight overlay is applied from a
raw thread:

```java
new Thread(() -> {
    this.setIcon(ImageUtil.addHighlightOverlay((ImageIcon) this.getIcon()));
    Thread.sleep(HIGHLIGHT_DURATION);
    ...
    this.setIcon(lastIcon);
}).start();
```

Both `setIcon` calls run off the EDT. Same class of issue in
`gui/TrainControlUI.java:12875` (`JOptionPane.showOptionDialog` called from a worker thread) and
`12915` (`startAutonomy.setEnabled(false)` off the EDT) — note the sibling dialogs in that same
method *are* wrapped in `invokeLater`.

### B11. CAN message parsing does not mask its bit fields

> **FIXED.** Priority, command, hash and length are all masked, length is clamped to the payload size,
> `getSubCommand`'s bound corrected to 5, and the outgoing constructor now stores the hash unsigned so
> parsed and constructed messages compare equal.
`marklin/udp/CS2Message.java:102-130, 420-432`

```java
this.command = (message[0] << 7) | (message[1] >> 1);  // no & 0x01 / & 0xFF
this.hash    = (message[2] << 8) |  message[3];        // no & 0xFF on byte 3
this.length  = (int) message[4];                       // no & 0x0F
```

* `message[0]` carries the 4 priority bits in the high nibble; only bit 0 belongs to the command.
  Any packet with a non-zero priority decodes to a garbage command and is dropped by every
  `isXxxCommand()` test. This is benign today only because the observed CS traffic uses priority 0.
* `message[1] >> 1` sign-extends, so any command ≥ 0x40 decodes negative. All commands currently
  used are ≤ 0x23.
* The hash is mis-parsed in two distinct ways, only one of which is harmless:
  * **Byte 2 high bit set** — `message[2] << 8` sign-extends, so hash `0xE31D` decodes as `-7395`
    instead of `58141`. Observed live during the test run (a real device on the LAN sent it). This
    one is benign: `(short)`-casting in the outgoing constructor (line 255) produces the same
    negative value, and `fromCS2Message` writes the same two bytes back, so it round-trips.
  * **Byte 3 high bit set** — genuinely corrupting. `(0xE3 << 8) | 0x9D` evaluates as
    `0xFFFFE300 | 0xFFFFFF9D` = `0xFFFFFF9D` = `-99`; the entire high byte is destroyed, and the
    correct value would be `-7011`. Any hash whose low byte is ≥ 0x80 decodes wrong, which breaks
    the hash comparison in `equals()` and so the duplicate-packet suppression in `receiveMessage`.
    Needs `& 0xFF` on `message[3]`.
* `length` is unmasked, so a corrupted 5th byte > 8 walks off the end of the 8-byte `data` array
  in the copy loop at line 126.

`getSubCommand()` guards `data.length < 4` but then reads `getData()[4]` — should be `< 5`.

### B12. Every received `CS2Message` aliases the same receive buffer

> **FIXED.** The parsing constructor copies the buffer.
`marklin/udp/NetworkProxy.java:142-160`, `marklin/udp/CS2Message.java:105`

`ReadMessages` allocates one `buffer` and passes it to `createMessage(buffer)` on every packet;
`CS2Message` stores the reference (`this.rawMessage = message`) without copying. All parsed fields
*are* copied out, so decoding is safe, but:

* `equals()`'s second loop compares `rawMessage` bytes — always identical, so that half of the
  duplicate check is a no-op.
* `toString()` (used by the debug packet log) prints whatever packet arrived most recently, not the
  one being logged, because logging happens on a different thread.

`CS2Message(byte[])` should defensively copy.

### B13. Stream leaks on the HTTP paths

> **FIXED.** `ping`, `isCS3`, `isNotFoundError`, `parseJSONArray` and `parseJSONObject` all close their
> reader now, the last two matching `parseFile`'s existing ownership convention.
`marklin/file/CS2File.java`

`fetchURL()` returns a `BufferedReader` that these callers never close:
`ping()` (2063-2075 — called in a retry loop at startup), `isCS3()` (349-371 — returns early on
match), `isNotFoundError()` (513-540), `parseJSONArray()` / `parseJSONObject()` (1713-1741).
`parseFile()` is the only one that closes. Each leaked reader holds an HTTP connection until GC.

### B14. `Edge.isOccupied` can NPE

> **FIXED.** `currentLoc` read once into a local; the `locomotiveMilestones` dereference in `Layout`
> is null-guarded.
`automation/Edge.java:325-333`

```java
if (this.end.isOccupied() && !this.end.getCurrentLocomotive().equals(loc))
```

`isOccupied()` is `synchronized` on the Point but `getCurrentLocomotive()` is not, and `currentLoc`
is `volatile`. Another thread clearing the point between the two calls (e.g. `unlockPath`,
`moveLocomotive`, `locDeleted`) produces a `NullPointerException` inside `isPathClear`, on a
locomotive thread. Read `currentLoc` once into a local.

Same shape at `automation/Layout.java:2528` — `this.locomotiveMilestones.get(loc).add(current)`
NPEs if `locDeleted(loc)` runs while that locomotive is mid-path.

### B15. `RouteCommand.fromLine` throws unchecked exceptions instead of the friendly error
`base/RouteCommand.java:630-826`

* Accessory branch: if the regex at line 762 does not match (e.g. `Switch abc,turn`),
  `accessoryAddress` stays `""` and `Integer.parseInt("")` throws `NumberFormatException`, bypassing
  the `error.invalidLine` message the method takes care to produce elsewhere.
* `locdir,` / `locspeed,` / `locfunc,` / `autoloc,` branches index `parts[1]`, `parts[2]`,
  `parts[3]` with no length check → `ArrayIndexOutOfBoundsException` on a truncated line.

Callers (route editor, `NodeExpression.parseLine`) expect a checked `Exception` with a readable
message.

### B16. `NetworkProxy.getIP()` mangles any address that has a hostname

> **FIXED.** Returns `getHostAddress()`. Note IPv6 literals would still need bracketing to be valid in
> a URL, which was equally true before.
`marklin/udp/NetworkProxy.java:47-50`

```java
public String getIP()
{
    return this.transmitIP.toString().replaceAll("/", "");
}
```

`InetAddress.toString()` returns `hostname + "/" + literal`, with the hostname part empty when no
name is known. Stripping the slash is correct for `getByName("192.168.1.5")`, which leaves the
hostname null and so yields `/192.168.1.5` → `192.168.1.5`. But when a hostname **is** present the two
halves are concatenated into nonsense.

Observed directly in every test run, where `getByName(null)` gives the loopback with hostname
`localhost`:

```
Initializing CAN listener on localhost127.0.0.1...
Station type detection error: java.net.UnknownHostException: localhost127.0.0.1
```

In production this triggers whenever the operator types a **hostname** instead of a dotted quad —
plausible, since a CS3 advertises an mDNS name. The failure is badly misleading:

1. `CS2File.ping(initIP)` and `CSDetect.isCentralStation(initIP)` both use the raw string the user
   typed, so the connection check passes and the address is written to preferences.
2. `MarklinControlStation` then builds `new CS2File(NetworkInterface.getIP(), this)` from the mangled
   string, so every HTTP fetch throws `UnknownHostException`.
3. `syncWithCS2()` returns -1, `on` stays false, and the user is told
   "Central Station network connection not established" — for a station that answered a moment ago.

Only the file/HTTP side is affected; the UDP socket uses the `InetAddress` object directly, so nothing
warns that the address string is corrupt. Fix: use `getHostAddress()` and drop the `replaceAll`.

### B19. A route that throws mid-execution is disabled forever
`marklin/MarklinRoute.java:229-445`

> **FIXED.** `try`/`finally` around the command loop, with `stopExecuting()` in the `finally`.

Found while fixing A4 — the same failure mode one level down.

```java
if (this.setExecuting())
{
    ...
    for (RouteCommand rc : this.route) { ... }

    this.stopExecuting();      // never reached if anything above throws
```

`setExecuting()` is the re-entrancy guard: it returns false while a route is already running. If any
command throws, `stopExecuting()` is skipped, `isExecuting` stays true, and **every** later attempt to
run that route returns false — silently, for the rest of the session. The same shape as A4, but for
execution rather than monitoring.

Most command types are type-guarded, so the reachable trigger is corrupt stored data — for instance a
`TYPE_LOCOMOTIVE_DIRECTION` whose `DIRECTION` value does not match the enum makes `getDirection()`
throw `IllegalArgumentException`. A hand-edited routes JSON would do it.

The loop is now wrapped in `try`/`finally` with `stopExecuting()` and `updateTiles()` in the `finally`,
and the "executed" log stays inside the `try` so a failed run is not reported as a success.

### B18. `RemoteDeviceCollection.add` stranded the previous name for an id
`base/RemoteDeviceCollection.java:37-50`

> **FIXED.**

`add` cleaned up the case where a *name* was re-pointed at a different id, but not the reverse: an
*id* re-registered under a different name left the old name in the `names` map permanently. It kept
appearing in `getItemNames()` and kept resolving through `getByName()` — to the new device.

Accessories are where this bites, because a switch and a signal at one address share a UID: after a
track diagram tile changed type and `syncLayouts` re-created the accessory, `Switch 5` lingered
alongside `Signal 5`. Locomotives are unaffected (their id embeds the name, so a rename yields a new
id) and layouts are unaffected (the id *is* the name).

`add` now removes any other name mapped to the incoming id, giving a strict one-to-one mapping.
Removing the stale name is only safe because of the A2 fix above — old references still resolve
through the accessory-type fallback.

### B17. `newAccessory` carried the actuation count over from the wrong address — and created one
`marklin/MarklinControlStation.java:2026-2032`

> **FIXED** alongside A2, because it sat directly in the path being repaired.

```java
private MarklinAccessory newAccessory(int logicalAddress, int address, ...)
{
    MarklinAccessory current = this.getAccessoryByAddress(address, decoderType);
```

The second parameter is the **raw** address (`logical - 1`), but `getAccessoryByAddress` takes a
**logical** one and subtracts 1 itself. So creating an accessory read the actuation count from the
accessory *one address below* — and, since that lookup creates on miss (B6), registered a spurious
accessory there as well. Every accessory creation quietly seeded another phantom one address down.

Now resolved by name via the address-aware `getAccessoryByName`, which neither shifts the address nor
creates anything.

---

## C. Low — cosmetic, dead code, or narrow edge cases

> **Status - fifteen fixed, two withdrawn, three closed by decision. Each row below carries its own
> outcome; the fuller reasoning for the later ones is under "Remaining C items: dispositions".**

| # | Location | Finding |
|---|---|---|
| C1 | `marklin/MarklinLocomotive.java:855-873` | **FIXED.** `addressFromUID` tested `UID > MFX_BASE` first, but `DCC_BASE (0xC000) > MFX_BASE (0x4000)`, so the DCC and MULTI_UNIT branches were unreachable and DCC addresses printed as `UID - 0x4000`. Now tested from the highest base down. It also names the decoder type and uses decimal, since the string's whole purpose is to identify an unrecognised locomotive and a bare address matched nothing the UI shows. Covered by `testLocomotive.testAddressFromUID`. |
| C2 | `base/Locomotive.java:1089, 1114, 1296, 1306` | **FIXED.** Off-by-one: `arrivalFunc <= numF`, `departureFunc <= numF`, `fNo <= this.numF`. Valid functions are `0 .. numF-1`. Setting `f == numF` is accepted and then silently ignored by `_setF`. |
| C3 | `base/Locomotive.java:920-928` | **FIXED.** `getF(int)` checks `fNumber < numF` but not `>= 0` → `ArrayIndexOutOfBoundsException` on a negative index (`validF` gets this right). |
| C4 | `marklin/MarklinControlStation.java:575-629` | **FIXED**, though narrower than described here - it needs the chart to be opened within an hour of midnight *and* the window to span a DST change. Daily stats step back by a fixed `86400000` ms and key a `TreeMap` by a locale-formatted date. On DST transition days two iterations can produce the same date string (one silently overwrites the other) or skip a day, so the runtime chart loses/duplicates a bar twice a year. Use `LocalDate.minusDays()`. |
| C5 | `marklin/MarklinControlStation.java:1196-1217` | **WITHDRAWN - the finding was wrong.** MFX addresses are not unique in practice: the same locomotive can be duplicated in the UI or left by a stale sync, and both entries drive one decoder, so they must be reported. Reverted to comment-only. `getDuplicateLocAddresses` groups purely by raw address, ignoring decoder type. An MM2 loc at 5, a DCC loc at 5 and an MFX loc at 5 are distinct devices but are reported to the user as duplicates. |
| C6 | `automation/Layout.java:1248-1259` | **SKIPPED by decision.** Dead code with no callers; left as-is. `getPossibleEdges` does `List<Point>.removeAll(List<Edge>)` — no compile error, no runtime error, just never removes anything. Dead code (no callers), but the bug will surface if it is ever wired up. |
| C7 | `automation/Point.java:342-360` | **FIXED**, latent only - every existing caller already guards with `coordinatesSet()`, so this was a trap for the next one rather than a live NPE. `getX()` / `getY()` return `int` from a nullable `Integer` field → NPE unless every caller checks `coordinatesSet()` first. |
| C8 | `automation/Point.java:38, 59`, `automation/Layout.java:581-610` | **ACCEPTED, no change.** Intended behaviour. `Point.uniqueId` comes from a non-thread-safe static counter and is never serialised in `toJSON()`. IDs are therefore re-assigned on every load, so `getPointById` / `getEdgeById` (used by the GraphStream viewer) are only valid within a single session. |
| C9 | `marklin/MarklinLocomotive.java:728, 770, 806` | **WITHDRAWN - the coupling is load-bearing.** Zeroing `lastStartTime` on power-off is what makes TrainControl re-assert direction after a power cycle; renaming it would have silently changed hardware command emission. `lastStartTime` is overloaded as both the runtime-statistics start timestamp and a "direction already sent" flag (`lastStartTime = -1`). Any future change to the stats logic will silently change the direction-resend behaviour, and vice versa. |
| C10 | `base/Locomotive.java:357-385` vs `393-430` | **FIXED.** `notifyOfPowerStateChange` synchronises on the `Locomotive`; `_setSpeed` synchronises on the static `speedMonitor`. Both mutate `lastStartTime` and `historicalOperatingTime`, so runtime accounting can be lost if a power-off races a speed change. |
| C11 | `marklin/MarklinControlStation.java:1773-1787` | **ACCEPTED, no change.** The suppression is deliberate - there is a `TODO` acknowledging it. `log()` suppresses *any* message identical to the immediately preceding one, globally and unsynchronised. Legitimate repeats (the same accessory failing twice, the same path rejected twice) vanish from the log. |
| C12 | `marklin/MarklinControlStation.java:2318-2338` | **FIXED.** `deleteRoute` calls `getAutoLayout()`, which *creates* a `Layout` when none exists (and bumps the static `layoutVersion`). `if (this.getAutoLayout() != null)` is always true. Prefer `hasAutoLayout()`. |
| C13 | `marklin/MarklinRoute.java:663-694` | **FIXED.** `fromJSON` leaves `triggerType` null when the key is absent; `executeAutoRoute` then falls through to `OCCUPIED_THEN_CLEAR` rather than the documented `CLEAR_THEN_OCCUPIED` default. |
| C14 | `base/LayoutDiagramComponent.java:335-357` | **FIXED.** `getImage` does `ImageIO.read(getResource(path))`; a missing icon makes `getResource` return null and `ImageIO.read` throw `IllegalArgumentException`, which `LayoutLabel` (catching only `IOException`) does not handle — on the EDT. |
| C15 | `base/LayoutDiagramComponent.java:691` | **FIXED**, latent only - unreachable today, since the field starts as `""` and both `setLabel` callers guard against null. The guard simply did not mean what its comment said. `if (this.label == null \|\| this.type == TEXT && this.label.isEmpty()) return "";` — a null label on a *non-text* component silently drops the component from the CS2 export. |
| C16 | `resources/messages.properties` (9 lines), `messages_da.properties` (3) | **FIXED.** Straight apostrophes in bundle values. `I18n.f()` uses `MessageFormat`, which eats them. Today only `layout.configFolderStructureHint` (line 814) goes through `logf`, so the impact is one message losing its quote marks — the FR/IT/NL bundles correctly use U+2019. **But this is a live trap**: the day someone adds a `{0}` placeholder to a message containing `'`, the placeholder stops being substituted. Worth normalising the English/Danish bundles now. |
| C17 | `automation/Layout.java:1648-1707` | **FIXED in part, deliberately.** Only the container changed. Marking `visited` on enqueue - the other half of this finding - would change behaviour, not just cost; see "Path finding: what testing C17 turned up". `bfs()` marks `visited` on dequeue rather than enqueue, and `visited` is a `LinkedList` (O(n) `contains`). Correct, but the queue can blow up on dense graphs. |
| C18 | `marklin/udp/CSDetect.java:178-211`, `checkWebServer` | **FIXED.** `HttpURLConnection` is never disconnected; ~254 connections per subnet scan. |
| C19 | `marklin/MarklinControlStation.java:1583-1613` | **FIXED**, and quieter than described - the exception was swallowed by the executor's `Future`, so it dropped one update in silence rather than being noisy. `receiveMessage` reads `locIdCache`, then `locDB.getById(l)` — if a locomotive is deleted between the two, `locList.get(...)` is null and `parseMessage` NPEs on the message-processor thread. Narrow, but the executor is single-threaded and a repeated failure would be noisy. |
| C20 | `resources/messages*.properties` | **FIXED.** Every bundle defined five keys twice (fifteen in Danish), and a `.properties` file silently keeps only the last. Three pairs disagreed; `autolayout.ui.errorAddEdge` had its `{0}` variant shadowed, so the reason an edge failed to be added was discarded. Deduplicated across all eight bundles, with the call site corrected to use `I18n.f`. |

---

## Notes on things that look wrong but are not

* `Layout` lock ordering: `activeLocomotives → this` (at `Layout.java:2571`) is the only nested
  acquisition; nothing takes them in the opposite order, so there is no deadlock.
* `Accessory.actuationConfirmedMonitor`: `validatePathActuation` re-checks `allConfirmed()` before
  each `wait()`, so there is no lost-wakeup window between sending the commands and starting to
  wait.
* `RemoteDeviceCollection.getItemIds()/getItemNames()/getItems()` all return fresh copies, so the
  delete-while-iterating in `syncLayouts()`'s feedback pruning is safe.
* `MarklinAccessory.parseMessage`'s `stateAtLastActuation = !stateAtLastActuation` is equivalent to
  assigning `this.switched` given the guard above it — correct, if indirect.

---

## Test coverage in the repository

`.gitignore` excluded all four `testReviewFindings*` classes, which was right for scaffolding — but
three had become the only home for the permanent regression coverage of a *fixed* defect, so on a fresh
clone that coverage did not exist. All three have been moved into tracked classes:

| Was in (ignored) | Now in (tracked) | Coverage |
|---|---|---|
| `testReviewFindingNetworkReader` | `testNetworkProxy` | A8, B16 |
| `testReviewFindingsUiThreading` | `testLayoutTiles` | B9, B10 |
| `testReviewFindingsRoutes` | `testParseCS2Routes` | A5, including the pin against the real `fahrstrassen.cs2` |
| `testReviewFindingsAutonomy` | *(unchanged)* | B6 only — documents an open defect, correctly excluded |

Also added: `testCS2Message`, 11 tests covering B11/B12. Four decode packets captured from live Central
Stations; the rest pin each masking defect separately. No model, socket or display, so it runs anywhere.

The A5 tests moved into `testParseCS2Routes` rather than a new class because that class already parses
`fahrstrassen.cs2`, so the real-fixture assertion reuses the already-parsed `routes_mags` instead of
re-reading the file.

Three now-stale `.gitignore` entries remain for the deleted scaffolding files, and `testLayoutTiles`
needs adding to the index.

## Pre-release self-review

A full re-read of every change, after the fixes were committed. Two defects were found **in the fixes
themselves** and corrected; both were in `A3`/`A8` and both were narrow but in safety-relevant paths.

### SR1. `configureAndLockPath` released edges it had never locked
`automation/Layout.java`

The A3 fix `break`s out of the configure loop on failure, so edges past that point were never
`setOccupied()`. It then passed the **whole** path to `handleMisconfiguredPath`, which calls
`setUnoccupied()` on every edge — and that also clears each edge's lock edges. For an edge we never
locked, its lock edges may legitimately belong to another locomotive by then, precisely because we
never held them. That is the same unsafe release A7 established must not happen.

Now tracks `edgesLocked` and passes `path.subList(0, edgesLocked)`. The failing edge is still included
(the counter is incremented before `configureEdge` is called), so the operator message is unchanged.

### SR2. The CAN reader's `finally` could close a healthy replacement socket
`marklin/udp/NetworkProxy.java`

The A8 reader re-reads the `socket` field each pass, so it can pick up a socket `sendMessage` reopened.
But the `finally` also re-read the field before closing — so if a reopen landed between the loop test
and the `finally`, the reader closed the *new* socket on its way out, breaking transmission until the
next send reopened again.

The `finally` no longer closes anything. The loop only exits once the socket is already closed, so
there was nothing for it to close in the first place.

### Also noted, not fixed

* `Edge.validateConfigCommand` — a command written as a bare address (`5,turn`) fails with "invalid
  accessory type", because `stringToAccessoryType("")` throws before reaching the friendlier error
  below it. Pre-existing.
* `NetworkProxy.sendMessage` reopening the socket now works (B16/A8), but if the reader thread has
  already exited there is nothing to restart it — so the proxy can end up send-only. Making the reopen
  reachable exposed this; a proper fix is a listener supervisor, which is a design change.

## Validation status

Findings A1-A8, B9 and B10 were re-derived against the source and pinned with tests.
**All 26 tests across the four classes have been executed and pass**, which — since every test asserts
the *current, buggy* behaviour — means every finding below is confirmed to exist in 2.7.5.

The correct behaviour is named in a comment directly above each assertion, so a test turns red the
moment its defect is fixed; at that point invert the assertion to lock the fix in.

| Finding | Verdict | Test |
|---|---|---|
| A1 CS3 mixed route dropped | **FIXED** in `MarklinRoute.setDelay` | `testRoutes.testSetDelayOnRouteContainingLocomotiveCommands`, `testRoutes.testCS3RouteWithLocomotiveCommandThenDelayedAccessory` |
| A2 signal name unresolvable | **FIXED** in `getAccessoryByName` / `validateConfigCommand` | `testAccessory.testSignalAndSwitchNamesResolveToTheSameDecoder` + 3 more |
| B17 `newAccessory` looked up the wrong address | **FIXED** with A2 | covered by `testAccessory.testSignalIsCreatedAtAnUnusedAddress` |
| B18 stale names in `RemoteDeviceCollection` | **FIXED** | `testAccessory.testChangingAccessoryTypeDoesNotStrandTheOldName` |
| A3 train departs over missing accessory | **FIXED** in `Layout.configureEdge` / `configureAndLockPath` | `testAutonomyPathValidation.testMissingAccessory*`, `testPathRunsOnceTheAccessoryExists` |
| A4 autoloc condition NPE kills monitor | **FIXED** in `Route.evaluate` + `executeAutoRoute` | `testRoutes.testAutoLocomotiveCondition*`, `testRoutes.testUnsatisfiedAutoLocConditionDoesNotKillTheRouteMonitor` |
| A5 CS2 condition duplication | **FIXED** in `CS2File.parseRoutes` | `testReviewFindingsRoutes.testA5_*` (assertions inverted; real-file pin retained) |
| A6 duplicate route monitor threads | **FIXED** in `MarklinRoute.executeAutoRoute` | `testRoutes.testDisableAndReEnableDoesNotStartASecondMonitor` |
| A7 lock edges leaked on a skipped unlock | **FIXED** in `Layout.unlockPath` (analysis corrected) | `testAutonomyPathValidation.testUnlockPathReleasesLockEdgesOfASkippedEdge` |
| A8 CAN listener dies permanently | **FIXED** in `NetworkProxy` | `testNetworkProxy.*` (run standalone) |
| B6 read-only lookups create accessories | **Confirmed from field data** | `testReviewFindingsAutonomy.testB6_*` |
| B9 feedback tile set not thread-safe | **FIXED** in all three device classes | `testReviewFindingsUiThreading.testB9_*` (assertions inverted) |
| B10 setIcon off the EDT | **FIXED** in `LayoutLabel` | `testReviewFindingsUiThreading.testB10_*` (assertion inverted) |
| B11 CAN message field masking | **FIXED** in `CS2Message` | `testCS2Message` (11 tests, incl. real captured packets) |
| B16 getIP() mangles hostnames | **FIXED** in `NetworkProxy.getIP` | `testNetworkProxy.testGetIpReturnsALiteralAddress` |

**Harness defect found on the first autonomy run and fixed:** the original `freeAddress()` helper
searched for an unused MM2 accessory address and there are none (see B6) — so all five A2/A3 tests
failed on their first statement without executing any product code. They now clear a specific address
via reflection instead, which reproduces the fresh-installation precondition.

Notes on running them:

* `testNetworkProxy` **must be run on its own** — it closes the model's UDP socket, which
  kills CAN reception for the whole JVM.
* `testReviewFindingsUiThreading` needs a display (`showUI = true`), like `testAutonomyPathValidation`.
* Each class calls `init(...)`, which binds UDP port 15730, so classes cannot share a JVM — the same
  constraint the existing test classes already have.
* `testA6_*` leaves parked monitor threads behind (that is the bug) and its second test disables any
  other s88 routes restored from `LocDB.data`, via `parseAuto`.
* **The tests are not network-isolated.** Even started with `simulate = true` and no connection, the
  proxy binds UDP 15730 on all interfaces, so live CAN traffic from any Central Station on the LAN is
  received and processed mid-run. Eight such packets (two ping responses from two real devices) were
  observed during the autonomy run. Transmission stays off, so nothing is sent, but incoming
  accessory or feedback echoes could in principle perturb a timing-sensitive test. Run the suite off
  the layout network if a result ever looks inexplicable.

Two corrections to the original report came out of this pass, both in the "confirmed real, but I had
the blast radius wrong" category — A5 (masked by HashMap iteration order; see that item) and A7
(needs three preconditions, not one).

## Suggested order of attack

1. **A3** — autonomy safety, and the only finding with a physical consequence: a train departs over an
   accessory that was never commanded. Demonstrated end to end.
2. **A4, A8** — both silently disable a whole subsystem (conditional routes / all CAN input) with a
   single log line and no recovery.
3. **B6** — already polluting real databases, and it is what makes A2 indistinguishable from a genuine
   conflict. Cheap to fix; needs a migration story for existing `LocDB.data` files.
4. **A1** — route import correctness; users lose routes without knowing.
5. **A6, A7** — cumulative failures that appear only after prolonged operation.
6. **A2** — worth fixing as part of A3, by resolving edge config commands via address + protocol
   rather than by name.
7. **B1, B2, B3** — layout editor correctness.
8. **A5** — fix the loop scope *before* anyone touches `parseFile`'s accumulator map.
9. **B9, B10, B11, B12, B16** — threading and protocol-layer hardening (cheap, low-risk fixes).

---

## Final pre-release pass (v2.7.6)

Adversarial re-read of the full production diff (`b47b6ed~1..HEAD`, 15 source files) looking specifically
for regressions introduced by the fixes themselves.

### Changed in this pass

| # | File | Change |
|---|---|---|
| FR1 | `MarklinRoute.java` | `enabled` made `volatile`. The monitor thread loops on it while `enable()`/`disable()` are called from the EDT, so it had no guarantee of observing a disable. `deleteRoute` depends on that observation to retire the monitor of a route being edited or deleted; an unretired monitor keeps firing the **old** command list on a route the UI can no longer reach. The new `isAlive()` re-entry guard also depends on it. |
| FR2 | `Layout.unlockPath` | **Comment stated a false invariant.** It claimed `isPathClear` "refuses any path whose lock edges are occupied". It does not — `isPathClear` checks each path edge, its opposite-direction twin, and point state, and never inspects `getLockEdges()`. Replaced with the actual reason the release is safe (symmetric crossing declarations put the crossing edge in the conflicting path itself), and stated the limit explicitly. Behaviour unchanged. |
| FR3 | `CS2File.parseJSONArray` | **Comment stated a false fact.** It claimed callers "pass `fetchURL(...)` inline"; `parseRoutesCS3` passes named readers (`routeBR`/`magBR`/`locBR`). Closing is still correct — it is a clean `if/else`, so no reader is consumed twice — but the stated reason was wrong. |

### Assumptions checked and confirmed

- `Edge.isOccupied` rewrite is **exactly** equivalent to the original minus the NPE: `Point.isOccupied()` is literally `return this.currentLoc != null`. No weakening of occupancy detection.
- B8's function array cannot overflow anything: `Locomotive.setFunctionTypes` does `Arrays.copyOf(functionTypes, this.numF)`, so an oversized sparse array is truncated to the decoder's function count and `assert functionTypes.length == getMaxNumF(type)` still holds.
- `ping()` is behaviourally identical: `fetchURL` always returns a new reader or throws, so `reachable != null` is always true where the old code returned `true`.
- `newAccessory`'s actuation-count lookup matches the DB key exactly — the 6-arg overload registers under the same `getNameWithProtocol(logicalAddress, type, decoderType)` expression.
- `addressFromUID` has exactly one caller, a log message, so hex → decimal breaks no parser.
- `RouteCommand.getProtocol()` can never return null (`determineAccessoryDecoderType` falls back to `DEFAULT_IMPLICIT_PROTOCOL`), so the new `NodeExpression` display path cannot NPE.
- `synchronized executeAutoRoute()` cannot deadlock: `setExecuting`/`stopExecuting` touch only `isExecuting` and call nothing, and the monitor is held only across `new Thread(...).start()`.
- `handleMisconfiguredPath` genuinely releases the locks (`setUnoccupied()` + clears end-point assignments + stops the loco), and SR1's `subList(0, edgesLocked)` bounds it to edges actually taken.
- `LayoutLabel`'s highlight block is inside the `invokeLater` at line 337, and `updateImage` marshals via `invokeLater` (not `invokeAndWait`) — so `MarklinFeedback.updateTiles()` running inline cannot block the CAN reader thread.
- All `logf`/`I18n.f` keys referenced from changed files exist in `messages.properties` (1186 keys checked).
- `import java.util.HashSet` in `MarklinRoute` is still required — raw-type use in `equals()` at line 832.

### Residual items, not changed

1. **Lock edges are a flag, not a reference count.** `setUnoccupied()` clears all lock edges unconditionally. For a crossing declared symmetrically this is safe. A hand-edited `autonomy.json` where two edges name a third as a lock edge *without traversing it* could have that crossing freed while another locomotive still relies on it. Pre-existing and independent of the A7 fix; the fix made the skip branch consistent with the normal branch.
2. **`executeAutoRoute` narrow drop window.** If it is called during the microseconds between a monitor thread deciding to exit and actually terminating, `isAlive()` is still true and the new monitor is skipped, leaving the route not watching until re-enabled. Requires the sensor to fire at exactly that instant. Not worth more machinery.
3. `parseRoutesCS3` leaks `routeBR` and `magBR` if `isCS3Version260OrAbove()` throws between opening them and the branch. Pre-existing.
4. Instance `MarklinAccessory.getNameWithProtocol()` builds from the **raw** address while the DB is keyed on the **logical** one, so it returns a name one lower than `getName()`. Zero callers today — a trap for a future one.
5. `acc.commandConflictSameAddressMustRename` is now an orphaned resource key in all bundles (its only use was removed for A2). Harmless; left in place rather than editing every translation.
6. **B6 remains open** (phantom accessories in existing `LocDB.data`; needs a migration).
7. The shipped `resources/*.zip` sample layout was never confirmed free of the address-131 switch/signal conflict fixed in `cs2_sample_layout`.

### B2 / B3 / C12 / C13 outcomes

The layout editor commits through a **full reparse**, which changes the severity of B2 and B3. Verified
chain: `saveButtonActionPerformed` → `LayoutDiagram.saveChanges` (writes `.artikel` from
`this.rawAddress`, which `setLogicalAddress` sets correctly) → `TrainControlUI.layoutEditingComplete`
→ `model.syncWithCS2()` → `syncLayouts()`, which re-derives `address` from `rawAddress` and re-runs
`c.setAccessory(...)`.

| # | Outcome |
|---|---|
| **B2** | **FIXED**, but downgraded to cosmetic/latent. `setLogicalAddress` set `address = address * 2` alongside `rawAddress`, violating the parser's `address == rawAddress / 2` invariant. Not observable in production: the export writes `rawAddress`, and in edit mode `LayoutLabel` gates both the tooltip (line 406) and actuation (line 121) behind `!edit`. Fixed anyway — one line, and it makes `address` mean what every consumer of `getAddress()` assumes. Also dropped the `address += 1` for green uncouplers, since the parser floors `2N+1` back to `N`. No changelog entry. |
| **B3** | **CLOSED — not a bug.** `syncLayouts` is the only place that binds a component, and the commit-time reparse runs it. The report's claim that the tile keeps the old accessory "until the layout is saved and re-synced" is true but vacuous: saving *is* the re-sync, and nothing can actuate the tile in between. **The fix originally proposed would have been actively harmful** — rebinding via `getAccessoryByAddress` creates on miss, so it would have spawned a phantom accessory at every address typed into the editor, working directly against the B6 tradeoff. |
| **C12** | **FIXED.** `deleteRoute` now tests `hasAutoLayout()` instead of `getAutoLayout() != null`, which was always true and instantiated a `Layout` (bumping the static `layoutVersion`) on setups with no autonomy. |
| **C13** | **FIXED** in two places: `fromJSON` now defaults `triggerType` to `CLEAR_THEN_OCCUPIED`, and the constructor coerces a null to the same value so no other caller can reintroduce it. Covered by `testRoutes.testRouteFromJSONDefaultsToClearThenOccupied`. Changelog entry added. |

### False-positive sweep (post-B3)

Re-examined every fix for the B3 failure mode — a defect that is structurally real but which some
downstream mechanism already neutralises, making the user-facing claim unsupportable.

**Two changelog entries were unsupported and have been removed.** Both code fixes are kept as
hardening; only the user-facing claims went.

| # | Why the claim did not hold |
|---|---|
| **A5** | My own validation had already concluded **DOWNGRADED**: `parseFile` flattens each `..key=value` group through a `HashMap`, and for the pair `{kont, hi}` that map deterministically iterates `hi` first (`String.hashCode` is specified, so this is stable across JVMs). No trailing token follows `kont`, so no real `fahrstrassen.cs2` ever produced the unsatisfiable pair. The changelog nevertheless claimed "a route imported from a Central Station 2 could never fire". Removed. The fix still matters as a latent trap: switching that map to a `LinkedHashMap` would restore file order and break every `hi=0` condition. |
| **B8** | Scanned both real fixtures: **243 locomotive blocks, zero gaps** — `..nr=` is always contiguous from 0, so `max(nr) == data.length - 1` and the old `new int[data.length]` could never be overrun. No observed Central Station file lists functions sparsely, so the claim "instead of cancelling the whole database sync" describes a failure no user hit. Removed. |

**Checked and confirmed genuine** (not B3-class):

- **B17** — reachable through exactly the address-131 re-create path: `syncLayouts` calls the 5-arg `newAccessory` when the stored type disagrees with the layout, and the old lookup passed the *raw* address to `getAccessoryByAddress`, which subtracts one again. So it read the accessory two below the logical address, copied its actuation count onto the new one, and created a phantom there. This fired on every startup of the sample layout.
- **B12** — `CS2Message.equals` genuinely compares `rawMessage` (its third loop), and messages are queued onto single-threaded executors that process them after the reader has overwritten the buffer. Aliasing made `lastPacket.rawMessage` and the incoming message the *same array*, so that comparison was silently vacuous. The fix restores it without changing behaviour, since bytes `0..5+length` are fully determined by the fields and data already compared.
- **B1** — `addRowsAndColumns` is reachable from the editor (`LayoutEditor:1693`), the right-click menu, and new-layout creation, so a layout grown taller than it is wide really did get a short column.
- **B14** — the NPE window is real: `Point.isOccupied()` is synchronised but `getCurrentLocomotive()` is not.

### Housekeeping done

Removed three stale `.gitignore` entries for scaffolding files that no longer exist. The fourth,
`testReviewFindingsAutonomy.java`, still exists but now only pins **B6 — which is accepted behaviour** —
so it documents an accepted trade-off rather than an open defect.

### C14 / C16 fixed

**C14 — `LayoutDiagramComponent.getImage`.** Two failure modes, both now reported as the `IOException`
the method already declares, so `LayoutLabel`'s existing handler catches them and the tile simply
renders without its icon:

1. A missing icon made `getResource` return null, and `ImageIO.read(null)` throws
   `IllegalArgumentException` — which `LayoutLabel` (catching only `IOException`, at lines 316 and 418)
   did not handle, so it escaped **on the EDT**.
2. `ImageIO.read` returns *null* rather than throwing when no installed reader can decode the file,
   which then dereferenced null on `img.getWidth(null)`.

Uses a new `error.missingLayoutIcon` key, a sibling of the existing `error.missingLocalFunctionIcon`.
Added to `messages.properties` only; other locales fall back through `ResourceBundle`'s parent chain.
Both handlers log `ex.getMessage()`, so the message names the icon.

Worth noting the second mode was already guarded everywhere else — `TrainControlUI.getLocImage` and
`getLocImageMaxHeight` both wrap their result in `if (img != null)`. `getImage` was the only site missing it.

**C16 — apostrophe normalisation.** 12 bundle values carried straight apostrophes: 9 in
`messages.properties`, 3 in `messages_da.properties`. All replaced with the `’` escape, matching
the convention the other bundles already follow — verified: fr/it/nl contain 346/224/10 occurrences of
`’` and **zero** non-ASCII bytes. All eight bundles remain pure ASCII, which Java 8 requires.

`’` rather than the MessageFormat `''` escape, because most of these keys are read through
`I18n.t`, which does no `MessageFormat` pass — a doubled apostrophe would render literally there.

The exception is `layout.configFolderStructureHint`, the one key that really does go through
`MessageFormat` (so its quote marks were being eaten today). Its folder names now use double quotes,
matching how the Italian and Dutch translations of the same string already render them.

Diff is exactly 12 insertions and 12 deletions with no line-count or line-ending change.

Neither fix gets a changelog entry: C14 guards a packaging error that should never occur in a shipped
build, and C16's only live symptom was one hint message losing its quote marks.

### C20 (new). Duplicate keys in the message bundles — one definition silently wins

Found while validating C16. Every bundle defines **5 keys twice**, and `messages_da` defines 15
(the extra 10 are `timetable.ui.*`). In a `.properties` file the **last** definition wins silently.

Three pairs disagree. One is a live defect:

- **`autolayout.ui.errorAddEdge`** — defined as `Error adding edge: {0}` (line 338) and then
  `Error adding edge.` (line 342). The second wins, so the `{0}` version is dead. It is called **both**
  ways: `I18n.f("autolayout.ui.errorAddEdge", e.getMessage())` at `GraphRightClickPointMenu:528` and
  `I18n.t(...)` at `:599`. The `f` call's argument is therefore **silently discarded** — when adding an
  edge fails, the operator is told "Error adding edge." with the reason stripped off. Someone evidently
  added the second definition to serve the no-arg call site without realising it shadows the first
  globally. Correct fix: two distinct keys, in all eight bundles.
- `layout.ui.errorEditingOnlySupportedForLocalFiles` — the surviving text drops the "download this
  layout" hint. Cosmetic.
- `route.ui.errorOnlyOneRouteEditorAllowed` — the surviving text drops "Close the editor window
  first." Cosmetic.

The remaining duplicates are byte-identical and are harmless clutter.

A duplicate-key assertion was deliberately **not** added to `testMessageBundles` yet, because it would
fail immediately on these pre-existing pairs. Fix the three disagreeing pairs first, then add it.

### C16 regression guard added

`test/testMessageBundles.java` — three tests, no model/socket/display needed. Validated by simulating
its logic against `git show HEAD:` versions of the bundles: it reports **exactly the 12 offenders** that
C16 fixed, so it is load-bearing rather than vacuous. Bundle discovery verified against
`build/classes/org/traincontrol/resources`, which holds all 8 bundles.

Also confirmed: `error.missingLayoutIcon` is the **only** key not present in all eight bundles
(`extra=0` for every locale), and it falls back to English through `ResourceBundle`'s parent chain by
design. No existing test asserts key parity, so nothing breaks.

### C20 fixed — bundle deduplication, key parity, and the shadowed placeholder

**`autolayout.ui.errorAddEdge` deduped to the `{0}` variant in all eight bundles** — which required a
code change, not just a bundle edit. The key was called both ways, and `I18n.t` does a bare
`getString()` with no `MessageFormat` pass, so keeping only the `{0}` form would have printed a literal
`{0}` at `GraphRightClickPointMenu:599`. Both call sites are `catch (Exception e)` blocks feeding a
`showMessageDialog`, and 599 was throwing the exception away, so it now uses
`I18n.f("autolayout.ui.errorAddEdge", e.getMessage())` too. The original defect is closed at both sites:
the reason an edge failed to be added is now shown either way.

Verified no other key has this shape — no key whose value contains a `{n}` is referenced through
`I18n.t` anywhere in `src/`. (`error.invalidLogin` looked like a missing key but is only a javadoc
example in `I18n.java:11`, not a live call.)

**`error.missingLayoutIcon` added to all seven translations**, composed from each bundle's own
established vocabulary rather than invented: layout renders as Layout / maqueta / r&eacute;seau /
plastico / baan / makieta, icon as ikon / Icon / icono / ic&ocirc;ne / icona / pictogram / ikona, and
the sentence shape mirrors the sibling `error.missingLocalFunctionIcon`. All ASCII-escaped.

**Duplicates removed:** 5 per bundle, 15 in Danish. All eight bundles now hold **1187 keys with exact
parity** (missing=0, extra=0 everywhere), no duplicates, no non-ASCII, no straight apostrophes.

For the two *other* pairs whose values disagreed, the **last** definition was kept — that is what users
see today, so deduping does not silently change any UI text as a side effect. Both discarded variants
were the more verbose ones and are recorded here in case they are preferred:

- `route.ui.errorOnlyOneRouteEditorAllowed` dropped "... Close the editor window first."
- `layout.ui.errorEditingOnlySupportedForLocalFiles` dropped "... download this layout or initialize a
  new one." in favour of "... initialize a local track diagram."

**`testMessageBundles` extended to five tests**, adding `testNoDuplicateKeys` and
`testTranslationsMatchEnglishKeySet` — the two that could not be added before the bundles were clean.

### B7 / B15 fixed

**B15 — `RouteCommand.fromLine` threw unchecked exceptions.** Every branch splits user-entered text on
commas and calls `Integer.parseInt` on the pieces, so `Switch abc,turn`, `Switch 5`, `locdir,MyLoc`,
`locspeed,MyLoc,abc`, `locfunc,MyLoc,3`, `autoloc,MyLoc` and `Switch 5,turn,abc` all escaped as
`NumberFormatException` or `ArrayIndexOutOfBoundsException`, bypassing the friendly `error.invalidLine`
message the parser produces in its other branches.

The body was renamed to a private `parseLine`, and `fromLine` is now a thin wrapper converting
**`RuntimeException` only** into the checked, readable error. Catching just the unchecked type means the
friendly messages `parseLine` raises itself pass through with their own wording intact, and every
branch is covered at once, including any added later. All three callers (`Edge`, `NodeExpression`,
`RouteEditor`) simply propagate `throws Exception`, so none depended on seeing the unchecked type.

**B7 — validation passed on accessories the CS had never acknowledged.** `stateAtLastActuation` is
seeded from the *assumed* startup state, so `isConfirmedAt(desired)` returned true for any accessory
whose assumption already matched the command. The first path to set a switch to the position it was
believed to be in therefore passed validation with nothing confirmed by anybody.

Fixed with an `actuationConfirmed` flag that starts false and is set by any CS echo. Two things made
this safe to require rather than merely cosmetic:

1. `MarklinAccessory.setSwitched` **always** transmits, even when the accessory is already in the
   requested position — so an echo is always expected and the stricter rule cannot hang.
2. `parseMessage` now sets the flag **and notifies** on every echo, not only on state-*changing* ones.
   Without that second half the stricter rule would have been actively harmful: an accessory commanded
   to the position it was already in produces a non-changing echo, so it would never have become
   confirmed, and path validation would have slept until its timeout and held the train.

`numActuations` still increments only on a real change. Note the limit of what any echo can prove: the
Märklin protocol echoes the commanded position, not a sensed one, so this confirms the Central Station
acknowledged the command — a decoder that has physically drifted cannot be detected through this
channel at all.

Covered by `testRoutes.testMalformedRouteLinesReportAReadableError` (8 malformed lines),
`testRoutes.testValidRouteLinesStillParse` (7 valid ones, so the conversion cannot mask a regression),
and `testAccessory.testAccessoryIsNotConfirmedUntilTheCentralStationEchoes` (which also pins the
non-changing-echo case). Both got a changelog entry.

## Remaining C items: dispositions

Per user decision: **C6 skipped**, **C8 intended**, **C11 intended** (the log dedup is deliberate; there
is a `TODO` acknowledging it).

### Fixed

| # | Change |
|---|---|
| **C3** | `Locomotive.getF` now delegates to `validF`, which checks both ends. It previously tested only `fNumber < numF`, so a negative index reached the array and threw. |
| **C4** | All three stats methods (`getDailyRuntimeStats`, `getDailyCountStats`, `getTotalLocStats`) now step with `LocalDate.minusDays(1)` instead of subtracting a fixed `86400000` ms. `DateTimeFormatter.ISO_LOCAL_DATE` produces the identical `yyyy-MM-dd` key that `Locomotive.getDate` writes. |
| **C5** | **Reverted — the reasoning was wrong, see "C5 withdrawn" below.** `getDuplicateLocAddresses` is unchanged apart from a comment. |
| **C15** | The null check moved inside the TEXT test, where it belongs as a guard for `isEmpty()`. |
| **C18** | `CSDetect.checkWebServer` disconnects in a `finally`. One connection per host, ~254 per subnet scan, all previously left to the finalizer. |
| **C19** | Null guard on `locDB.getById` in `receiveMessage`. |

Covered by `testLocomotive.testGetFRejectsOutOfRangeIndexes` and
`testLocomotive.testDuplicateAddressesIgnoreMFX` (which pins the MFX rule as a product decision).

### C4 — narrower than reported

The DST fault needs the *current local time of day* to fall within the hour that the transition adds or
removes, since only then does a 24-hour step land twice on one date or skip one. So: twice a year, and
only for someone opening the stats chart within about an hour of midnight. The fix is still right and
cheap, but it was not costing anyone a visible bar twice a year.

### C15 — not intentional

`git log -S` puts the guard's origin in `573b0f3 "Minor Bug fixes"`, added whole, comment and all —
there is no earlier form that was refactored into this shape. Three things say precedence slip rather
than design: the comment scopes the rule to *"Empty text labels"* only; a deliberate "drop any component
with a null label" rule would be a strange thing to write and stranger to leave uncommented; and it
landed in a bug-fix commit next to a `LayoutEditor` change, consistent with someone patching an NPE on
`.isEmpty()` by hoisting the null test one level too far. Unreachable in practice either way.

### C9 — withdrawn, the coupling is load-bearing

Previously assessed as "cheap; prevents a future silent coupling." Reading the actual code, that was
wrong and the item should **not** be fixed as described.

`lastStartTime == 0` is not merely overloaded, it is *doing work*: `notifyOfPowerStateChange` resets
`lastStartTime` to 0 on a power-off, and `MarklinLocomotive` reads exactly that to decide it must
re-assert the locomotive's direction on the next movement command. A normal stop leaves the field at a
non-zero timestamp, so direction is *not* re-sent then. Re-asserting direction after a power cycle is
almost certainly desirable behaviour, and it is emergent from this coupling rather than written down.

A correct fix therefore is not a rename: a new `directionSent` flag would have to be cleared in the same
branch of `notifyOfPowerStateChange` that zeroes `lastStartTime`, or TrainControl silently stops
re-asserting direction after every power cycle. That is a change to hardware command emission, in the
base class, for zero user-visible benefit. Deferred.

### C10 fixed

`notifyOfPowerStateChange` now takes `speedMonitor` - the same lock `_setSpeed` already uses - so the two
methods that mutate `speed`, `lastStartTime`, `powerState` and `historicalOperatingTime` finally exclude
each other. They previously held different locks (`this` versus the global `speedMonitor`) and so never
did, which is why a power-off arriving during a speed change could lose the running interval or count it
twice.

Chose to reuse `speedMonitor` over introducing a per-instance lock, having checked the alternative:
`Locomotive` is not `Serializable` (persistence goes through `MarklinSimpleComponent`), so an instance
monitor field would have been safe too - but reusing the existing lock adds no field and, more
importantly, introduces no new acquisition order.

Deadlock safety was verified rather than assumed, and the check that mattered was not the obvious one:

- The order `this -> speedMonitor` already exists, via `MarklinLocomotive.setSpeed` (synchronized) calling
  `_setSpeed`. So this change follows the established direction.
- The reverse order would create a cycle. Every call made from inside a `synchronized (speedMonitor)`
  block was enumerated: `Locomotive.getDate()` (static), `Thread.currentThread().interrupt()`, and
  `getSpeed()` in the two `waitForSpeed*` loops. **`getSpeed()` is not synchronized** (line 960) - had it
  been, `waitForSpeedAtOrAbove` would have supplied a `speedMonitor -> this` edge and this change would
  have deadlocked the emergency-stop path against any waiting locomotive.
- `waitForSpeedAtOrAbove` / `waitForSpeedBelow` are not synchronized methods, so a waiting thread holds no
  locomotive lock and `wait()` releases `speedMonitor`. The power-off handler cannot be stalled behind one.

Also confirmed the only writers of `historicalOperatingTime` are these two methods, so its
`getOrDefault`/`put` read-modify-write is now atomic as well - previously it was not, even though the map
is a `ConcurrentHashMap` (concurrency-safe per operation, not across a pair of them).

No test: a lost-update race cannot be pinned deterministically, and a timing-based attempt would be
flaky. The existing sequential coverage (`testLocomotive` lines 64 and 113, which exercise runtime
accumulation through `_setSpeed`) still applies - the accounting body is unchanged apart from
indentation, and `_setSpeed` was not touched at all.

### C5 withdrawn — MFX addresses are not unique in practice

The MFX exclusion has been reverted. `getDuplicateLocAddresses` now differs from its original only by a
comment warning against re-applying the filter.

The reasoning was wrong. It treated an MFX locomotive's mfxuid as making its *address* unique, so an MFX
entry sharing an address could not be a second decoder. In reality the operator can duplicate the same
MFX locomotive in the UI for convenience, or be left with a duplicate by a stale sync — and both entries
then drive the same physical decoder, which is exactly what the duplicate-address warning exists to
surface. Filtering MFX out suppressed the warning in a case where it was wanted.

`testLocomotive.testDuplicateAddressesIgnoreMFX` was inverted rather than deleted, and is now
`testDuplicateAddressesIncludeMFX`: it asserts all three decoder types on one address are reported, and
carries the rationale so the filter is not re-added.

### Assumptions rechecked against this

Duplicate locomotives on one address are a supported scenario, not an anomaly. Everything that could
have depended on the opposite was checked, and the codebase turns out to model it deliberately:

- **B18 is safe.** This was the one at real risk: `RemoteDeviceCollection.add` now evicts any name already
  mapped to the same id, so if two MFX locomotives shared a `locDB` id, adding the second would have
  silently removed the first from the name map — losing a locomotive the operator had deliberately
  duplicated. They do not collide: `MarklinLocomotive.getUID()` returns a **String**,
  `getName() + '_' + numericUID`, so the `locDB` key includes the name and stays distinct.
  (`accDB` is keyed by the plain numeric UID, but there the shared-id case is switch/signal, which really
  is one device — the A2/B17/B18 premise is unaffected.)
- **`locIdCache` is built for this.** It is a `HashMap<Integer, List<String>>` keyed by the *numeric* UID,
  and `rebuildLocIdCache` appends rather than overwrites — so a Central Station message for a shared
  address correctly updates every duplicate. This is deliberate design, and it corroborates the point.
- **C19's fix sits correctly on top of that.** Its loop walks exactly that list of composite ids, and the
  comment about "any other locomotives on this UID" is accurate — more so than when it was written.
- **C1 is unaffected.** `addressFromUID` names a decoder type and address for an unrecognised UID; that
  string is still correct when two locomotives share the address.

### C2 / C7 / C17 fixed, plus cleanup

| # | Change |
|---|---|
| **C2** | The four `<= numF` bounds are now `< numF`. Valid functions are `0 .. numF-1`. Two of the sites (`getLocalFunctionImageURL` / `setLocalFunctionImageURL`) are Map-backed so the old bound merely created a dead entry; the other two let an arrival or departure function be configured one past the last real one, which `_setF` then silently ignored - the function simply never fired. The setters have no else branch, so an out-of-range value is still ignored rather than throwing; only the boundary moved. |
| **C7** | `Point.getX()` / `getY()` return 0 instead of unboxing a null `Integer`. |
| **C17** | `bfs`'s `visited` is now a `HashSet` rather than a `LinkedList`. **The mark-on-dequeue behaviour was deliberately left alone** - see below. |
| — | `MarklinAccessory.getNameWithProtocol()` (the instance overload) now builds from the logical address, `this.address + 1`. It was using the raw address, so it returned a name one below `getName()` and below anything `getAccessoryByName` could resolve. Still has no callers; corrected so the first one does not inherit the off-by-one. |
| — | `acc.commandConflictSameAddressMustRename` removed from all eight bundles. Its only use went away with A2. All eight remain at 1186 keys with exact parity. |

**C17 — only half the textbook fix is safe here.** Marking `visited` on *enqueue* instead of on dequeue is
the usual BFS refinement and would stop a point being queued more than once. It would also change what
this method returns. All three callers pass `excludePaths` (`Layout:1921`, `1973`, `2040`), and the search
depends on reaching a point by several different routes so that it can discard the excluded ones and
return an allowed alternative. Marking on enqueue explores only the first route to each point, so a
path that exists could stop being found. Only the container was changed - `Point` overrides both `equals`
and `hashCode`, so membership is decided identically and only the lookup cost differs.

**C7 — 0 rather than an exception, and why.** Every current caller is already guarded: `Point.toJSON`
wraps its access in `coordinatesSet()`, `TrainControlUI:14674` and `:14695` short-circuit on it,
`:14697` sits inside that guard, `:14735` sits behind `setPoints` (only true when every point passed the
check), and `GraphRightClickGeneralMenu:62` calls `setX`/`setY` immediately before. So this is a latent
trap, not a live NPE, and the fix is about the *next* caller. 0 was chosen over throwing because
`TrainControlUI:14674` already treats `(0,0)` as equivalent to `!coordinatesSet()` when deciding whether
to auto-lay-out the graph - an unguarded caller therefore lands on the existing "not positioned" path
rather than crashing.

### Repository housekeeping

- `PathIntegrityValidationReview.md` untracked with `git rm --cached`. It was tracked despite being
  listed in `.gitignore:25`, which is why it kept appearing as modified. The file remains on disk.
- `/nbproject/configs/` added to `.gitignore`, covering the untracked `Debug.properties`. Note that
  `.gitignore` is itself untracked in this repository, so that entry is local only.

## Path finding: what testing C17 turned up

C17 was recorded above as a container swap. Building tests for it produced three findings that matter
more than the change did.

### bfs is nondeterministic by design

`Layout.getNeighbors` ends with `Collections.shuffle(neighbors)`, commented *"Randomize order to allow
for variation in paths"*. Two calls to `bfs` with identical arguments may therefore return different
routes of the same length. Consequences:

- **Any test asserting an exact route is only safe where the shortest route is unique.** Every such
  assertion in `testLayoutBfs` was checked against that; a note in its header says so.
- The first version of the differential suite compared exact routes and reported false divergences.
  It was rewritten to compare only what is deterministic: whether a route exists, and its length.

### The alternative-route search is order-dependent per call, but NOT in its outcome

An earlier version of this section claimed `pickPath` could spuriously report "no free paths" because of
the shuffle. **That was wrong, and the measurement behind it was of the wrong thing.**

What is true: a single `(start, end)` exclusion *sequence* varies between runs. Because `visited` is
marked on dequeue, which points get queued at each depth depends on the shuffled order, so the routes
enumerated - and their order - differ per call. Measured over 120 graphs, comparing one implementation's
sequence against **itself** produced 62 disagreements on whether a route was found at that step; against
the previous implementation, 57. Same rate either way, so this is the shuffle, not the C17 change.

What does not follow is that `pickPath` can miss an available path, and that is the claim that matters.
`pickPath` does not make one call. For **each** valid destination, in priority order, it runs

```java
do { path = this.bfs(start, end, seenPaths); ... } while (path != null);
```

which enumerates routes to that destination **until bfs is exhausted**, and only then moves to the next
destination. Exhaustion is what makes the ordering irrelevant: the shuffle changes the order routes come
back in, but the loop keeps going until there are none left, so a clear route cannot be skipped merely
for being found late.

Measured directly, modelling the whole of `pickPath` - every destination, full enumeration each, with a
third of edges blocked so `isPathClear` really has to reject: across 134 generated graphs where a clear
route genuinely existed, `pickPath` returned null in **0** of 20 runs each - 2,680 invocations - and the
outcome never varied between runs on the same graph.

So an intermittent "no free paths" is not something to expect from this. The caveats on that measurement
are that it models `isPathClear` only as "no blocked edge" (the real one additionally rejects for lock
edges, terminus rules, excluded intermediate points and max active trains - all further *rejections*,
which only cause more enumeration), and that it treats destinations as equally ranked (priority changes
which is tried first, and all are tried regardless).

### The C17 fix was deliberately only half of what the finding suggested

Marking `visited` on enqueue - the usual BFS refinement, and the other half of the original C17
write-up - would change behaviour, not just cost. All three `bfs` callers pass `excludePaths`, and
finding an allowed alternative depends on reaching a point by more than one route. Only the container
was changed. The reasoning is recorded at the call site.

Note the guard for this needed strengthening: measured over 500 runs, mark-on-dequeue finds the
alternative every time while mark-on-enqueue finds it 247 times, so a single-shot assertion would have
caught the regression only about half the time. The test now repeats twenty times.

### Coverage added

| Suite | Tests | Covers |
|---|---|---|
| `testLayoutBfs` | 12 | Shortest-path, exclusion, cycles, rejections, plus property tests over 150 seeded random graphs checked against an independently written shortest-path implementation |
| `testLayoutBfsEquivalence` | 4 | Differential against the pre-change implementation, transcribed verbatim from `071d424~1` |
| `testLayoutPickPath` | 10 | Station **priority** ordering, destination filtering (occupied / inactive / excluded), and the negative cases - this layer previously had no coverage at all |

`pickPath` is where station priority lives; `bfs` never reads it. Notable behaviour pinned there:
priority outranks distance, so a higher-ranked destination two edges away is chosen over a lower-ranked
neighbour.

All three suites use fixed seeds so a failure names the seed, and every generator-driven test asserts a
floor on how much it actually exercised - a property test over random data that quietly stops covering
anything is worse than none.
