# Validation of the interface and control-station fixes of 2026-09-19

**Status:** open

**Prefix:** SVB. Checked free before use: no document in `docs/reviews/` declares it, and no `SVB-` id appears in `docs/manual-tests/tests.md`.

**Covers** the four commits on `autonomy-diagram-r0` that fixed and dispositioned the interface half of the 2026-09-19 review round, read at the tree as it stands after them:

- `6b4321a8` - UIX-B1 (the exit asks an open `RouteEditorFrame` through `maySettleBeforeExit` / `hasUnsavedWork`, and `TrainControlUI.routeEditorHasUnsavedWork`) and UIX-B2 (`LayoutLabel`: a flash ends an outstanding accessory highlight before capturing; an accessory change discards an outstanding flash; two predicates say which restore is armed).
- `dad874e6` - CS3-B1 (every route-thread walk of a route's command list takes a copy; `runningRouteDriving` on `ViewListener`; the delete and name/address doors refuse while a route that drives the locomotive runs).
- `58f94664` - the low findings: CS3-C1 (the route name trimmed in both `MarklinRoute` constructors), CS3-C2 (a feedback module is in the database before it parses its first message), CS3-C3 (the locomotive id cache is rebuilt by every add). The SET and RTX items in the same commit belong to the other half and were not read.
- `3a457b4c` - the `behaviour.md` paragraph recording the accepted one-address-two-tile-types limitation (CS3-B2).

This is a validation pass, not a fresh review: each fix was asked whether it does what its message and comments say on every path, whether its siblings got the same treatment, and whether it broke anything beside it. **Read-only**: nothing was run, built or edited; no test was executed; nothing under `src/`, `test/`, `cs2_sample_layout/` or the tracker was touched. The two layout files under `cs2_sample_layout/config/gleisbilder/` were grepped for one address to check a sentence in `behaviour.md`, and nothing else was read there.

Every finding names the file and line, the caller path that reaches it, and the test that would show it red.

---

## A - high

| id | status | where |
|---|---|---|
| - | - | none found |

Nothing reached A. The shapes looked for hardest: a fix that sends a wrong command (none of the four touches a command path), a fix that loses data (CS3-C1 changes only what a name is trimmed to; CS3-C2 reorders an add and a parse; neither writes a file), and a lock inversion introduced by CS3-C3 (D5 - there is none).

---

## B - medium

| id | status | where |
|---|---|---|
| SVB-B1 | open | `LayoutLabel.setImageOnEDT`, `LayoutLabel.java:995-1028`: `discardFlash()` sits inside the accessory-highlight branch, whose condition (`:1016`) excludes the two paths that also replace the picture under a flash |

### SVB-B1: UIX-B2 is fixed on the path the test exercises and not on the two others that replace a flashed tile's picture

**What the fix does.** `discardFlash()` (`LayoutLabel.java:1028`) runs inside the branch at `:1016`:

```java
if (!edit && (this.component.isSignal() || this.component.isSwitch()) && hadIcon
        && (System.currentTimeMillis() - lastClicked) > CLICK_TIMEOUT)
```

The picture itself is replaced earlier and unconditionally: `lastIcon = new ImageIcon(img); this.setIcon(lastIcon);` at `:995-999`. So a flash is given up only when the replacement ALSO qualifies for a yellow wash. Two replacements do not, and both leave `flashTimer` armed with `flashRestore` - the picture the tile had before the change - which `flashHighlight`'s timer puts back at `:1160-1166` up to five seconds later.

**Path one - the switch is thrown by clicking its own flashed tile.** The route editor's *Highlight on Diagram* lights the tiles of the route's switches (`RouteEditorFrame.java:2678` -> `TrainControlUI.highlightAddresses:8464-8492` -> `flashHighlight`). The operator clicks one of those tiles to test it, which is what the button is for. The mouse handler sets `lastClicked = System.currentTimeMillis()` (`LayoutLabel.java:537`) and then throws the accessory on the switching worker; the accessory's `setSwitched` (`MarklinAccessory.java:208-237`) calls `updateTiles`, this label's `updateImage(false)` sees a new image key (the key carries the state while not editing, `:901-903`), `setImage(true)` decodes on the tile loader and `setImageOnEDT` replaces the icon at `:999`. The branch at `:1016` is skipped because the click is within `CLICK_TIMEOUT` (2500 ms) - that exclusion exists so a click does not wash its own tile - and `discardFlash()` with it. The flash fires at five seconds and draws the switch in its pre-throw position. It stays that way until the accessory next changes: `updateImage(false)` returns early on an unchanged key, and the page cache re-attaches the same label.

**Path two - a sensor tile.** *Highlight on Diagram* also lights, in orange, every tile whose address a CONDITION names (`RouteEditorFrame.java:2663-2682`, `checked`), and conditions name s88 sensors. A train reaching one of those sensors inside the five seconds runs `MarklinFeedback.parseMessage` -> `updateTiles` -> `updateImage(false)` -> `setImage` -> the icon is replaced at `:999` with the occupied picture; the branch at `:1016` is never taken for a feedback tile (`isSignal() || isSwitch()`), so the flash is left armed and puts the CLEAR picture back over an occupied sensor. It stays clear until the sensor next changes.

**Why B.** It is UIX-B2's own severity: a tile on the operating diagram drawn in a state the railway is not in, silently, for as long as that device is quiet - reached through the most natural gesture the highlight invites (path one) and through ordinary running (path two). Narrow window, self-healing on the next change, no wrong command: the same arguments that made UIX-B2 a B and not a C or an A.

**MT-462 does not catch it.** Its step 2 throws the turnout *"from the keyboard or from the Central Station"*, which are exactly the sources that take the branch. Neither a click on the tile nor a sensor is in it.

**How to prove it.** In `core.testLayoutTiles`, beside `testTheFlashAndTheAccessoryHighlightDoNotUndoEachOther`:

- Path two is the easy one and needs no click: build a FEEDBACK label the way `drawnSignalLabel` builds a signal one (`componentType.FEEDBACK`, `edit == false`), wait for its icon, `flashHighlight()`, assert `isFlashOutstanding()` (precondition), then change the component's state and `updateImage(false)` so the key changes, wait for the `invokeLater` pass, and **assert `isFlashOutstanding()` is false** - red today, true. Then `Thread.sleep` past the hold and assert `getIcon() == lastIcon`, not the captured picture - also red.
- Path one: the same on a signal label with `lastClicked` made recent. It is private; the honest way is to give the test what the click gives - a package-private `clickedNow()` setter, or dispatching a `MouseEvent.MOUSE_CLICKED` through `getMouseListeners()` on a label whose model is in simulate mode - then `updateImage(false)` after a state change and the same two assertions.

**The fix.** Move `discardFlash()` to where the picture is replaced - immediately after `this.setIcon(lastIcon)` at `:999`, guarded by `hadIcon` - so that any new picture, from any source, takes an outstanding flash with it. The comment already says the rule (*"what is on the tile now is the truth"*); the code enforces it in one branch. In edit mode the editor's reveal flash (`LayoutEditor.java:2083-2089`) would then also be dropped by a redraw of that tile, which is the same rule and harmless there.

---

## C - low

| id | status | where |
|---|---|---|
| SVB-C1 | open | `LayoutLabel.java:1033-1050`: the accessory highlight's timer never clears `accessoryHighlight`, so `isAccessoryHighlightOutstanding()` is true for ever after the first highlight |
| SVB-C2 | open | `TrainControlUI.checkForRenameMenuItemActionPerformed`, `:26580-26700`: the fourth rename door has no `refuseWhileARouteDrivesIt` |
| SVB-C3 | open | `MarklinRoute.java:303-316`, `MarklinSimpleComponent.java:90`, `TrainControlUI.java:21956-21957`: "every walk takes a copy" is wider than the code, and the copy itself is not atomic |
| SVB-C4 | open | three insertions anchored at the declaration orphaned a neighbour's javadoc or put one where javadoc will not read it: `RouteEditorFrame.java:440-466`, `TrainControlUI.java:5940-5980`, `MarklinRoute.java:303-316` |
| SVB-C5 | open | `TrainControlUI.java:604-606`: the javadoc names a test that does not exist |
| SVB-C6 | open | `behaviour.md:1735-1743`: the CS3-B2 paragraph records the display half of the limitation and not the state half |

### SVB-C1: the accessory-highlight predicate is stale after its timer fires

`accessoryHighlight` is assigned at `LayoutLabel.java:1033` and cleared only by `endAccessoryHighlight()` (`:1216`). The timer's own action (`:1035-1049`) puts `lastIcon` back and leaves the field set. So after the first accessory highlight on a tile has expired naturally, `isAccessoryHighlightOutstanding()` (`:1198-1201`) answers true until the next flash, and its javadoc - *"true while the yellow wash of a state change will still be taken off by its own timer"* - is false for most of a session.

**What it costs in production.** Almost nothing: `endAccessoryHighlight()` on a later flash stops a timer that has already fired and sets `lastIcon`, which is what is showing anyway. It is the predicate that is wrong, and predicates are what the test reads.

**What it costs the test.** In `testTheFlashAndTheAccessoryHighlightDoNotUndoEachOther`, step TWO builds `second` through `warmedSignalLabel()`, which runs one highlight and waits it out - so `second.isAccessoryHighlightOutstanding()` is already true before `updateImage(true)` is called, and `awaitHighlight(second)` returns at once without waiting for the second highlight. The precondition *"the accessory highlight never went up"* cannot fail. The scenario is still exercised, by luck of ordering: `updateImage(true)` on a cached key posts straight to the EDT queue (`setImage`, `:895-931` - the worker is only taken on a cache miss), ahead of the later `invokeAndWait(flashHighlight)`. Step ONE is sound, because `flashHighlight` calls `endAccessoryHighlight()` first and clears the stale field before the change is made. (SOP: "assert the precondition that makes a test meaningful" - here it is asserted and always true.)

**How to prove it.** `warmedSignalLabel()`, then **assert `isAccessoryHighlightOutstanding()` is false** - red today. Fix: `accessoryHighlight = null;` inside the timer's action, before or after the restore.

### SVB-C2: the Central Station name-proposal door renames without the CS3-B1 refusal

`dad874e6` says *"Delete and the name/address door also refuse while a route that drives the locomotive is running"*. There are two rename doors. `changeLocAddress` (`TrainControlUI.java:20052`) got the refusal; `checkForRenameMenuItemActionPerformed` (`:26580-26700`, the door OB-074 calls *"the fourth door"*) applies a name the station proposes through `model.renameLoc(currentName, newName)` at `:26672` after only the autonomy check at `:26590`. Its `deleteLoc(newName)` at `:26660` goes through the guarded UI method, so a refusal there leaves `l2` non-null and the rename is skipped silently, with a dialog about the OTHER locomotive.

**Why C, not B.** CS3-B1's own text sized it: `locomotiveRenamed` writes a value into the `RouteCommand`s the running copy shares (`Route.java:152-158`), which is not structural. The route thread resolves the locomotive by that name on its next locomotive command; between `locDB.delete(name)` and `locDB.add(l, newName, ...)` (`MarklinControlStation.java:3406-3410`) neither name resolves, so at worst one speed or function command is logged as `route.warningLocomotiveNotExistCalledFrom` and skipped - microseconds wide. It is the sibling the commit message claims and did not sweep.

**How to prove it.** MT-464's step 3 through *Locomotives > Check for renames* while the route runs: expected a refusal naming the route; observed the rename dialog. In code, one line - `if (refuseWhileARouteDrivesIt(this, currentName)) continue;` beside the autonomy check inside the loop.

### SVB-C3: the comment claims every walk takes a copy; three do not, one of them off the event thread, and the copy is a snapshot only in the loose sense

The javadoc at `MarklinRoute.java:303-316` reads *"EVERY WALK OF THIS COMMAND LIST TAKES A COPY FIRST"*. The four route-thread walks do (`:418`, `:440`, `:700`, `:1141` - the last is an event-thread writer whose copy is harmless). `toJSON` (`:1283`), `toCSV` (`:1374`, `Route.java:305`), `otherRouteRenamed` (`Route.java:136`), `namesLocomotives` (`:227`) and `commandsDrive` (`:258`) walk the live list. All are event-thread callers today except one: **`MarklinSimpleComponent(MarklinRoute)` aliases the live list** (`MarklinSimpleComponent.java:90`, `this.route = r.getRoute()`) and `saveState` serialises it - and the backup menu calls `this.model.saveState(false)` from its own worker (`TrainControlUI.java:21956-21957`, whose comment says so: *"from this thread, which is not the event thread"*). A locomotive deleted, or a route-editor row removed (`removeItem`, `MarklinRoute.java:1163`), while that serialisation walks the list is the same shape CS3-B1 fixed on the route thread: `LinkedList.writeObject` writes `size` first and then the nodes it can reach, and a mismatch is a stream the next start cannot read.

And the copy itself: `new ArrayList<>(this.route)` is `toArray()` under the hood, which on a `LinkedList` walks `first..next` without a modification check - a removal landing inside it shortens the copy or hands back a `null` slot (which the `rc != null` at `:702` then skips). The window went from seconds to microseconds, which is the whole of the fix's value; it did not go to zero, because nothing locks the list on either side.

**Why C.** Both windows are microseconds and need a manual backup or a delete to land inside them; I could not construct a way to make either wide without a hook. Recorded because the comment says "every" and the SOP says a comment must be authoritative, and because the honest fix - `synchronized (this)` on the route around `locomotiveDeleted`'s loop and around each copy, the monitor `isExecuting` already uses - is smaller than the claim it would make true.

**How to prove it.** Not deterministically without a hook. The comment can be corrected by reading; the test for the backup path would need a latch inside `saveState`.

### SVB-C4: three javadocs orphaned by inserting at the declaration

The memory rule "insert above the javadoc" was broken three times in the same round, and each looks right in a diff:

- `RouteEditorFrame.java:440-466`: `closeIfThrowingNothingAway`'s javadoc (*"Closes, asking first if anything would be lost..."*) now sits directly above `hasUnsavedWork`'s own `/** */`, and `closeIfThrowingNothingAway` (`:490`) has none. Two consecutive doc comments; javadoc keeps the second and the first is a dangling paragraph about a different method.
- `TrainControlUI.java:5940-5980`: `refuseWhileAutonomyRunning`'s javadoc (*"Adam, MT-141: Never allow any modifications..."*, with `@param source` / `@return`) now precedes `refuseWhileARouteDrivesIt`'s, and `refuseWhileAutonomyRunning` (`:5979`) has none.
- `MarklinRoute.java:303-316`: the CS3-B1 paragraph is a `/** */` placed BETWEEN `@Override` and `public void execRoute(boolean auto)`. It compiles; javadoc attaches a doc comment only when it precedes the annotations, so this one is documentation nothing generates, and the method's real javadoc (`:299-302`, *"Wrapper for a standard execution call"*) is now separated from it by the annotation.

**How to prove it.** Read the three spots; or `javadoc` on the three classes and look for the missing summaries. Fix: move each new comment above its neighbour's javadoc, and the `MarklinRoute` one above `@Override`.

### SVB-C5: a javadoc names a test that is not there

`TrainControlUI.routeEditorHasUnsavedWork` (`:604-606`) says *"`regression.testTheExitAsksTheRouteEditor` uses it on both sides of a typed change"*. No such test exists (`grep -rn testTheExitAsksTheRouteEditor test/ src/ docs/` finds only this line). The test is `core.testLayoutTiles.testTheExitKnowsTheRouteEditorHasUnsavedWork` (`test/core/testLayoutTiles.java:363`), which does use it on both sides. A comment that sends the reader to a class that does not exist is the "outsourced" kind the SOP names.

### SVB-C6: the accepted-limitation paragraph records half of CS3-B2

`behaviour.md:1735-1743` says what the operator SEES - the page wired first keeps a tile bound to the evicted object. It does not say what the database BELIEVES, which CS3-B2 also recorded and which the code confirms: `wireComponents` re-creates the accessory through `newAccessory(...)` with `c.getPrimaryDriveState()` (`MarklinControlStation.java:675-684`), and the five-argument `newAccessory` (`:3097-3108`) carries over only the actuation count from the object it replaces - the state is seeded from the page file's `zustand`. Pages are wired in order (`syncLayouts:864-875`), so on every wire pass 131 is re-created twice and ends up believing what page 5's file says (`zustand=1` -> green / straight), whatever the railway has. That happens on every `refreshLayouts()`, which runs after every diagram save (`TrainControlUI.java:23174`, `:25896`), so the keyboard key and the page-5 tile show the file's state from each save until the next echo.

**Whether any door decides from that belief.** The paragraph says *"nothing is commanded wrongly - every door commands by address"*, which is true of the address. Two places read the remembered state: the tile click toggles from its own object's memory, so on a stale page-3 tile the first click after a divergence can be a no-op on the railway (the paragraph's "shows the position it had" covers it); and the protecting-signal skip in `Layout.refreshOneSignal` (`Layout.java:8712-8777`, the skip at `:8772`, `acc.isRed() == claimed`) reads the database object resolved by name at call time (`:8748` -> `getAccessoryByName:2287`). That skip needs its memo to agree as well, so a reset belief can suppress a command only if 131 is a protecting signal in the autonomy setup - it is not in `config/autonomy/` (grep for 131: none) - and only when a diagram save lands between a change that moved the signal and the next clearing. Recorded as "could", not "does".

**Why C.** It is a document finding: the limitation Adam accepted is narrower on the page than on the railway, and the next person who sees the keyboard disagree with a turnout after saving a page will not find the sentence that explains it. Two sentences fix it. The rest of the paragraph checks out (D10).

---

## D - not defects, and checks that came back clean

| id | status | where |
|---|---|---|
| SVB-D1 | not a finding | UIX-B1's question order: layout editor, route editor, trains |
| SVB-D2 | clean | UIX-B1 siblings: every other window is modal or holds nothing; one route editor at a time; `isDisplayable` is the right test |
| SVB-D3 | clean | CS3-C2: every `newFeedback` caller, the constructor with `null`, and the waiters |
| SVB-D4 | clean | CS3-C1: every reader of a route's name, the route-in-route command, and nothing keyed by name |
| SVB-D5 | clean | CS3-C3: lock order, the callers' locks, and the cost |
| SVB-D6 | clean | CS3-B1, model half: the four copies, the readers left live, nested routes, no lock held by the walk |
| SVB-D7 | clean | CS3-B1, door half: both doors and every path into them, the message in eight bundles, check-then-act |
| SVB-D8 | clean | UIX-B2: `endAccessoryHighlight` while the picture is loading; what a discarded flash loses |
| SVB-D9 | clean | other timers and restores over a `LayoutLabel` |
| SVB-D10 | clean | the rest of the CS3-B2 paragraph, against the code and the files |
| SVB-D11 | clean | the four tests: red before green, preconditions, timing |

**SVB-D1.** `WindowClosed` (`TrainControlUI.java:19167-19266`) asks the layout editor, then the route editor, then - if autonomy runs - about trains. A No from the route editor therefore lands after the layout editor's Save or Discard has been acted on. That is the state WKV-B2 already reasoned about for the trains dialog's refusal one step later (*"if the exit is then refused the setup and the railway agree, as after a Cancel"*, `LayoutEditor.java:5994-6015`), and `completeExitDiscard` is still deferred to the exit's end (`:6036-6047`). The route editor's question is asked in the same position as the trains question and inherits the same argument. Not a finding; noted so nobody reopens it.

**SVB-D2.** Non-modal top-level windows in `gui/`: `LayoutEditor` (asked since OB-070), `RouteEditorFrame` (asked now), `LayoutPopupUI` (a diagram popup, nothing unsaved). `BusyDialog:53`, `LocIconCropDialog:138`, the autonomy editor's dialog at `AutonomyEditorPanel:6048` and the one at `TrainControlUI:27012` are `APPLICATION_MODAL`, so the exit cannot be reached while one is up. Both route-editor doors (`:21207-21230`, `:23024-23045`) refocus an existing visible editor rather than opening a second, so one field covers every editor. Nothing hides the frame without disposing it (no `setVisible(false)` in `RouteEditorFrame`), so `isDisplayable()` and "open" agree. `hasUnsavedWork` is `!locked && signature differs` - a Central Station route is never asked, which is right: nothing in it can be typed.

**SVB-D3.** All four callers now hand the constructor `null` (`MarklinControlStation.java:467`, `:748`, `:2347`, `Layout.java:10508`), so `MarklinFeedback(network, id, null)` (`:31-41`) is the only shape built and it parses nothing. `newFeedback` adds, then parses (`:2350-2354`). `parseMessage` (`:79-107`) requires `id == this.UID` - satisfied, the module was created from that id - and `_setState` (`Feedback.java:70-82`) notifies under `Locomotive.monitor` AFTER `feedbackDB.add`, so a waiter in `waitForOccupiedFeedback` / `waitForClearFeedback` (`Locomotive.java:789`, `:889`) that wakes finds `isFeedbackSet(name)` true and reads the state. The order is the one the restore path has always used (`:467-472`). `updateTiles` on a fresh module has no tiles either way; `feedbackChanged` and the log line run once either way. Nothing else creates a module from a message.

**SVB-D4.** Every door that reads a route's name reads the trimmed one: `getRouteList` (`:3641-3653`, `getName()`), `deleteRoute` (`:3512-3535`, finds by the name handed in and deletes by `r.getName()` - the two now agree), `changeRouteId` (`:3560`), `getRouteId` (`:3543`), `newRoute(MarklinRoute)` (`:2129`, trims again - harmless), and the sync's duplicate-name check (`:1459-1464`), which now finds a padded station route it used to miss - the intended change. The route-in-route command (`MarklinRoute.java:976`, `getRoute(rc.getName())`) resolves by the command's raw name; a padded name there failed before the fix too, because the key was already trimmed, so nothing regressed. Both file parsers and `fromJSON` (`:1339`) go through the constructors. Nothing keys a `Map` or `Set` by route name (CS3-D8 stands). `Route(null)` is unchanged and `newRoute(MarklinRoute)` still refuses it.

**SVB-D5.** `rebuildLocIdCache` (`:2416-2436`) is `synchronized` on the station and takes `locDB`'s monitor inside it (`getItems`, `RemoteDeviceCollection.java:149`). Every other path that holds both takes them in the same order - `changeLocAddress` (`synchronized`, `:3158`), `deleteLoc` (`:3317`), `renameLoc` (`:3412`) - and `RemoteDeviceCollection` calls nothing out, `getUID`/`getIntUID` are unsynchronised (`MarklinLocomotive.java:406-415`) and `exec` is unsynchronised (`:2723`), so there is no `locDB -> station` path to invert against. The sync thread holds nothing at `:1534`. Cost: one rebuild per NEW locomotive (existing ones skip the add), each a walk of the database - for a first sync of a few hundred, tens of thousands of trivial iterations; the restore path (`newLocomotive(MarklinSimpleComponent)`, `:3061`) does not rebuild and relies on the lazy build at `:2573`/`:2752`, so start-up is untouched. The rebuild at `:1634` is now redundant and harmless.

**SVB-D6.** The route thread's walks: the command loop (`:700`), `hasEmergencyStop` (`:418`, asked mid-route at `:793` and at the human doors), `accessoryHeldByAutonomy` (`:440`) - all copied. `setDelay` (`:1141`) is an event-thread writer that changes values, not structure; its copy costs nothing. `commandsDrive`, `namesLocomotives`, `otherRouteRenamed`, `equals` and the editor's readers are event-thread only (the off-thread exception is SVB-C3). The walk holds no monitor - `setExecuting`/`stopExecuting`/`isExecuting` are the only `synchronized` members and they return at once - so `runningRouteDriving` asking `isExecuting()` from the event thread cannot wait on it. A nested route (`:976-1000`) runs on its own thread and sets its own `isExecuting`, so the door sees it once it has started; a delete landing before that edits the list the inner route will begin with, which is the "next run" semantics the fix chose.

**SVB-D7.** `refuseWhileARouteDrivesIt` guards `deleteLoc(String, MouseEvent)` (`:20939`) and `changeLocAddress(Locomotive, MouseEvent)` (`:20052`); every path into a delete or a re-address goes through those two - the right-click items (`LocomotiveMenuItems.java:81`, `:110`), the keyboard shortcuts (`:18794`, `:18799`) and the one-argument overloads. `runningRouteDriving` (`:3257-3269`) walks names, resolves by name, and asks `isExecuting() && commandsDrive(name)`. It is check-then-act - a route can start during the confirmation dialog that follows - and the model copy is what makes that harmless, which the fix's comment says (*"the second half rather than the only one"*). `loc.ui.errorLocomotiveDrivenByARunningRoute` is in all eight bundles, ASCII with `\uXXXX` escapes, two placeholders each.

**SVB-D8.** `lastIcon` is assigned only once the picture is made (`:995`), so `endAccessoryHighlight` (`:1208-1217`) called while a decode is in flight puts back the previous picture and the decode's own `setIcon` then lands on top; when nothing has ever loaded it is null-guarded. A discarded flash loses the remaining seconds of one tile's flash and the tile shows the same yellow for 2.25 s anyway, so the operator sees no gap; an orange (condition) wash becomes yellow for that tile, which says something truer.

**SVB-D9.** The only icon-restoring timers over a `LayoutLabel` are the two in the class. `LayoutGrid`'s `failsafe` and `grace` (`:699-701`, `:2001-2007`) reveal the grid; `LayoutEditor`'s reveal flash (`:2083-2089`) is on editor labels, which never take the accessory branch; the autonomy overlay and the covered marks are painted in `paintCoveredMark`, not swapped into the icon, so neither restore can lose them.

**SVB-D10.** Checked against the code and the files: address 131 is `artikel=262` as `rechtsweiche` on `3 - Top Parking.cs2:14-17` and as `signal_sh01` with `zustand=1` on `5 - Test.cs2:369-372` (read only); pages are wired in index order (`syncLayouts:864-875`); a tile is bound at wire time (`wireComponents:737`) and `RemoteDeviceCollection.add` (`:60-79`) replaces the object under that id; changing a tile's type on one page saves, then `refreshLayouts()` (`TrainControlUI.java:23174`) re-wires and re-creates - the same re-creation, as the paragraph says; `isRed() == isTurned() == switched` and `isGreen() == isStraight()` (`Accessory.java:253-283`), so "red is the turnout's turn" is right; autonomy resolves every accessory by name at call time (`Layout.java:998`, `:2856`, `:3629`, `:3740`, `:8748` via `getAccessoryByName`, which accepts either spelling) and so is never bound to an evicted object. What is missing is in SVB-C6.

**SVB-D11.** The tests. `testARouteNameWithSpacesAroundItIsUsable` is red before the fix on its first assert (`getName()` was untrimmed) and its cleanup uses the name it asserts. `testARouteFinishesWhenItsLocomotiveIsDeletedMidRun` asserts the precondition that the delete lands mid-route (`0 < sent < 8` after 400 ms against `SLEEP_INTERVAL + 150` per command) and asks the door's question before the delete; timing-dependent on a loaded machine, as its author says. `testTheExitKnowsTheRouteEditorHasUnsavedWork` asserts its precondition, reads Swing text fields from the test thread through `routeEditorHasUnsavedWork` (a test-only habit, not a production path), and restores the field in `finally`. `testTheFlashAndTheAccessoryHighlightDoNotUndoEachOther` reads the armed restore rather than the icon, which is the right thing to pin; its weak precondition is SVB-C1. `6b4321a8` also carries `testACrossingOnlyOneRoadUsesStaysOrdinaryTrack` in `testMassAssignLengths`, which belongs to the SET half and is unrelated to either UIX finding - noted, not a defect.

---

## What this pass did not cover

- **Nothing was run.** Every red-today claim above is from reading; SVB-B1's two paths are traced through the code, not observed on a display.
- The SET and RTX items in `58f94664` (`Layout.java`, `AutonomySession.java`, `AutonomyEditorPanel.java`) - the other half of the round.
- The `messages*.properties` translations for meaning; only presence, placeholder count and ASCII were checked.
- `RouteEditorFrame.stateSignature` for completeness - whether the six inputs it covers are all the inputs the window has. UIX-B1 rests on it and it predates this round.
- `LayoutLabel`'s mouse handler beyond `lastClicked` and the switching worker; the dialogs it can raise before a click reaches the accessory were not re-read.
- Whether the CS3-B1 copy is the right long-term shape against a lock; SVB-C3 sizes the residue and stops there.
