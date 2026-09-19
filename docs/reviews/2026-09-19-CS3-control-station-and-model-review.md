# The control station and the model core, 2.7.4c to 3.0.0 rc14

**Status:** open 2026-09-19 - B1 fixed the same day (MT-464); B2 accepted as a documented limitation by Adam; C1, C2 and C3 fixed; C4 open

**Prefix:** CS3 (checked free: no `CS3-` id in `docs/manual-tests/findings.tsv` (the rendering of the finding catalogue in `triage.db`), no `CS3-[A-D]n` spelling in any declaration in `docs/reviews/`, and a grep of `src/` and `test/` finds the letters only inside message-bundle words such as `CS3-webapp`)

**Covers** `src/org/traincontrol/marklin/**` (`MarklinControlStation`, `MarklinLocomotive`, `MarklinAccessory`, `MarklinRoute`, `MarklinFeedback`, `MarklinSimpleComponent`, `file/CS2File`, `udp/CS2Message`, `udp/CSDetect`, `udp/NetworkProxy`), `src/org/traincontrol/base/**` (every file, `udp/CANMessage` included) and `src/org/traincontrol/util/**`, as they stand at `5b8dc021` (tag `v3_0_0_rc14`, 2026-09-17), read against the delta from `v2_7_4c` - 242 commits touching these paths, 31 files, about 10,000 lines added.  There is no `src/org/traincontrol/model_cs2/`; `src/org/traincontrol/model/` holds three interfaces and was read only where a signature mattered.  One of four reviewers Adam asked for on the 2.7.x-to-3.0.0 delta; the automation, autonomy-editor and GUI trees belong to the other three and were opened here only to trace a caller.  **Read-only**: no test was run, no build made, nothing under `src/`, `test/`, `cs2_sample_layout/` or the tracker touched.  Every file in scope was read in full, not sampled; the delta decided where to slow down.

Each finding below names the file and line, the caller that reaches it, what it does to the railway, and how to confirm it - the test that should be written and what it asserts red.  Where a finding predates 2.7.4c that is said, because a fault that shipped in 2.7.x is a different kind of news from one this delta introduced.  Two of the six are confirmed against Adam's own layout files rather than argued from the code.

---

## A - wrong behaviour on the layout, or data silently lost

None found.  Both B findings are about what the operator SEES and about a route stopping short; neither sends a wrong command to a decoder, and the data-loss shapes this delta was most concerned with - the locomotive database, the page files, the index - were each checked and came back clean (D1 to D6).

---

## B - incorrect results or crashes in specific configurations

| id | status | where |
|---|---|---|
| CS3-B1 | Fixed - MT-464 | `Route.locomotiveDeleted` (`base/Route.java:190-209`) removes from a command list `MarklinRoute.execRoute` is iterating on its own thread (`marklin/MarklinRoute.java:684`) |
| CS3-B2 | Accepted - documented, behaviour.md 8 | `MarklinControlStation.wireComponents` (`MarklinControlStation.java:655-737`) re-creates an accessory when a page's tile type differs from the database's; two of Adam's pages draw address 131 as different types, so the page wired first keeps a tile bound to an evicted object |

### CS3-B1 - deleting a locomotive while a route that names it is running mutates the list the route thread is walking

| | |
|---|---|
| **Disposition** | Fixed 2026-09-19 - MT-464 |
| **Introduced** | this delta - `5dc60db5` "Renaming or deleting a locomotive reaches everything that held it"; in 2.7.4c `deleteLoc` touched nothing but `locDB` and the id cache |

**The code.**  `MarklinControlStation.deleteLoc` (`:3250-3318`) now walks every route and calls `r.locomotiveDeleted(name)` (`:3305`).  `Route.locomotiveDeleted` (`base/Route.java:194-199`) removes, through `Iterator.remove()`, every command in `this.route` that names the locomotive.  `MarklinRoute.execRoute` runs on a thread of its own (`MarklinRoute.java:585`, "Must be a thread for the UI to update correctly") and iterates `for (RouteCommand rc : this.route)` (`:684`) - the same live list, not a copy - sleeping `SLEEP_INTERVAL + max(delay, 150)` ms between commands (`:1035-1045`), so a route of a dozen commands is in that loop for seconds.  `hasEmergencyStop()` (`:402`) and the mid-route `askable` test (`:793`) walk the same list from the same thread.  Nothing in `Route` or `MarklinRoute` synchronises the list, and nothing in the model or the UI refuses a delete while a route is executing: `TrainControlUI.deleteLoc` (gui, about `:20962`) asks `isAutonomyRunning()` and nothing else, and a route runs by hand or by s88 trigger with autonomy idle.

**The caller path.**  Right-click a locomotive, Delete (`LocomotiveMenuItems:110` -> `TrainControlUI.deleteLoc` -> `model.deleteLoc`), on the event thread, while any route naming that locomotive is part-way through its commands - the play button, the diagram tile, or an s88 trigger with nobody watching.  The confirmation dialog the UI shows first ("say how many in the popup") is what makes the window wider than a click: the operator reads it while the route runs.

**What goes wrong on the railway.**  The route's command list is either an `ArrayList` (built by the editor) or a `LinkedList` (`Route:35`, the CS2 importer, the database restore).  Both iterators are fail-fast: the route thread's next `next()` throws `ConcurrentModificationException`, which nothing in the thread body catches - the `try` at `:600` has only a `finally` - so the thread dies with a stack trace on stderr and **every command after the one being sent is not sent**.  With a `LinkedList` there is a second shape: `LinkedList.unlink` nulls the removed node's links, so an iterator standing on that node can return `null` (the `if (rc != null)` at `:686` then skips it) or end the walk early with no exception at all.  Either way a route that sets six turnouts and then a signal can set four and stop, silently as far as the log goes (`route.executed` is never written), leaving ironwork half-set for whatever train the route was preparing the road for.  The `finally` does run, so `isExecuting` is released and the route can be run again - the operator's only clue is that it did not finish.

**Why B and not A.**  It needs two things to coincide: a delete of a locomotive that a running route names, inside the seconds that route is running.  When it does coincide the ironwork is left half-set, which is the A shape, but it is a person's deliberate action landing in a narrow window rather than the railway doing it to itself.

**How to prove it.**  `core.testRoutes`-style, model built with `init(null, true, false, false, true)`:

1. `model.newMM2Locomotive("Deleted", 61)`.  Build eight `RouteCommand.RouteCommandAccessory(90 + i, MM2, true)` and, LAST, `RouteCommandLocomotiveSpeed("Deleted", 30)`; `model.newRoute("CME", commands, 0, CLEAR_THEN_OCCUPIED, false, null)`.
2. `model.setSentMessageObserver(m -> if (m.isAccessoryCommand()) sent.incrementAndGet())` - the observer is told whether or not the network is on.
3. `model.execRoute("CME")`; `Thread.sleep(300)` so the first command is out and the thread is in its 200 ms pause; `model.deleteLoc("Deleted")`.
4. Wait, bounded at 5 s, for `model.getRoute("CME").isExecuting()` to go false.  **Assert `sent.get() == 8`** - red today: fewer, with a `ConcurrentModificationException` (or a `NullPointerException` from a nulled node) printed by the route thread.  Assert the precondition too - that `commandsDrive("Deleted")` was true before the delete - or the test passes by exercising nothing (SOP, "assert the precondition that makes a test meaningful").

**The smaller fix.**  Iterate a snapshot in `execRoute` - `for (RouteCommand rc : new ArrayList<>(this.route))` - and in `hasEmergencyStop`, so a route runs the list it started with; `locomotiveDeleted` then edits the route the NEXT run sees, which is the right semantics anyway.  Making `Route.route` copy-on-write is the alternative and touches every writer.  Grep for the twins before closing: `locomotiveRenamed` (`Route:152`) and `otherRouteRenamed` (`:132`) write a value into a `RouteCommand`'s map from the same thread - not structural, and survivable - but they are the siblings a fix here should look at.

### CS3-B2 - one address drawn as a switch on one page and a signal on another leaves the first page's tile bound to an accessory object the database has evicted

| | |
|---|---|
| **Disposition** | Accepted 2026-09-19 - documented in behaviour.md 8 |
| **Introduced** | before 2.7.4c (the type-mismatch re-creation is at `v2_7_4c:MarklinControlStation.java:332`); the extraction into `wireComponents` in this delta carried it unchanged.  Reported because it is live on Adam's railway, not because it is new. |
| **behaviour.md** | disagrees with the code, and I believe the code is wrong - see below |

**The data.**  `cs2_sample_layout/config/gleisbilder/3 - Top Parking.cs2:13-17` has `.typ=rechtsweiche .artikel=262` at `0x204`; `5 - Test.cs2:368-372` has `.typ=signal_sh01 .artikel=262 .zustand=1` at `0x11007`.  Same raw address 262, halved by `CS2File.parseLayout` to logical **131**, both MM2 (no `.prot`, and the folder has no `magnetartikel.cs2`).  `test/layouts/live-snapshot` - the snapshot of the real railway - carries the identical pair.  So this is not a constructed case: Adam's layout draws turnout 131 on the parking page and the same address as a signal on the test page, which behaviour.md section 8 says is legitimate: *"a signal and a switch at one address are one accessory, and `accessoryType` only decides which icon is drawn."*

**The code.**  `syncLayouts` (`:864-875`) wires the pages in index order - `1 - Main`, `2 - Bottom`, `3 - Top Parking`, `4 - Combined`, `5 - Test`.  For each switch or signal tile `wireComponents` (`:655-664`) re-creates the accessory when `accDB.getById(target).isSignal() != c.isSignal()`, through `newAccessory(...)` (`:675`, `:684`), which builds a fresh `MarklinAccessory` and `accDB.add`s it - `RemoteDeviceCollection.add` (`:60-79`) removes the old name for that id and replaces the object.  The tile is then bound to whatever the database holds at that moment (`c.setAccessory(this.accDB.getById(targetAddress))`, `:737`).  So on every sync and every `refreshLayouts()` (which runs after each diagram save):

1. Page 3's turnout tile: the database holds `Signal 131` (from the last save, or from page 5 a moment ago) -> re-created as `Switch 131`, state seeded from page 3's `zustand`; the tile is bound to this object; the log says "Adding accessory Switch 131".
2. Page 5's signal tile: the database holds `Switch 131` -> re-created as `Signal 131`, state seeded from page 5's `zustand=1` (green); page 5's tile is bound to it; the log says "Adding accessory Signal 131".
3. The database now holds only the Signal.  **Page 3's tile still holds the Switch object, which is in no database and which no echo will ever reach**: `receiveMessage`'s accessory branch (`:2603-2628`) resolves `accDB.getById(id)` - the Signal - and calls ITS `parseMessage`, whose `updateTiles` (`MarklinAccessory:132-145`) repaints only the labels registered on it.  `LayoutGrid:1852-1855` (gui) registers each label on `c.getAccessory()` - the evicted object for page 3.

**What goes wrong on the railway.**  Turnout 131 is thrown from the keyboard, from page 5, from a route, from autonomy, or at the Central Station itself: the database object follows the echo; **page 3's tile goes on showing the position it had when the page was last wired**, until somebody clicks that tile (whose optimistic `setSwitched` updates its own stale object, and whose command's echo then updates the database one, so the two agree again by accident).  On the parking page, which is where trains are backed into berths, the operator is shown a turnout position that is not the turnout's.  Two side effects: every refresh **resets the database object's remembered state to the page file's static `zustand`** (`c.getPrimaryDriveState()`, `:675`/`:684`) - so after any diagram save the keyboard and page 5 show 131 as the file has it, not as the railway has it, until the next echo; and the log carries two "Adding accessory" lines for 131 on every refresh.  No wrong command is sent: every door commands by UID, and the UID is the same object-independent number.

**Where code and document disagree.**  behaviour.md section 8: *"code that looks one up must not key on the type."*  `wireComponents` keys the decision to REPLACE the database object on the type.  The type is display-only, and the display does not even use it - `LayoutDiagramComponent.getTypeName()` draws the icon from the component's own type, not from `MarklinAccessory.getType()`; the accessory's type reaches the operator only as the word in its name ("Switch 131" / "Signal 131") and in the log ("turn"/"straight" against "red"/"green").  So the re-creation buys nothing the diagram needs and costs a stable object.  I believe the code is wrong and the document right.  **What a fix has to decide**, and it is Adam's call: with one object per address, which word does 131 get in the route editor and the log?  Whichever page wires first, unless the name is chosen some other way - and `getAccessoryByName` (`:2287-2310`) already resolves either spelling to the one object, so nothing that looks one up by name would break.

**How to prove it.**  A test in the shape of `core.testParseCS2Layout`, against `test/layouts/live-snapshot` (never `cs2_sample_layout`):

1. `model = init(null, true, false, false, true)`; `CS2File parser = new CS2File("", model)`; `parser.setLayoutDataLoc("file:///" + fixture + "/")`; `pages = parser.parseLayout(new ArrayList<>())`; wire them in order with `model.wireComponents(page, null)` - the public door the 2026-09-08 note says tests must use.
2. `top = page("3 - Top Parking").getComponent(4, 2)`; `test = page("5 - Test").getComponent(7, 16)` (`0x11007` -> x 7, y 16).  Assert the precondition: both are at logical address 131, one `isSwitch()`, one `isSignal()`.
3. **Assert `top.getAccessory() == model.getAccessoryByAddressIfPresent(131, MM2)`** - red: page 3 holds an object the database no longer has.  Then `model.getAccessoryByAddressIfPresent(131, MM2).setSwitched(true)` and **assert `top.getAccessory().isSwitched()`** - red: the tile's object did not move.  Run the wiring twice and assert the number of `MarklinAccessory` objects created for 131 - red today at two per pass.

**Twins.**  The same test at `:660-663` runs the three-way branch (`targetAddress + 1`) through the identical re-creation.

---

## C - low: narrow cases, traps for the next caller, dead paths

| id | status | where |
|---|---|---|
| CS3-C1 | Fixed - trimmed at the constructor | `MarklinRoute` constructors keep a Central Station route's name untrimmed while `newRoute(MarklinRoute)` keys it trimmed - such a route can neither be deleted nor refreshed |
| CS3-C2 | Fixed - added before it parses | `newFeedback` notifies waiters before the module is in the database; reachable only for a sensor the JSON loader created and `syncLayouts` then pruned |
| CS3-C3 | Fixed - every add rebuilds | `syncWithCS2` rebuilds the locomotive id cache only on its success path, and the five-argument `newLocomotive` it uses does not rebuild it |
| CS3-C4 | Open | `getAutoLayout()` creates a `Layout` on a miss and is reached from a route thread through `hasAutoLayout()` with no lock between the two |

### CS3-C1 - a Central Station route whose name has leading or trailing whitespace is indexed under the trimmed name and deleted by the raw one

| | |
|---|---|
| **Disposition** | Fixed 2026-09-19 |

`CS2File.parseRoutes` (`:882`) and `parseRoutesCS3` (`:1297`) build `new MarklinRoute(control, m.get("name"), id)` with the name exactly as the file has it; `Route`'s constructor stores it (`Route:34`).  `newRoute(MarklinRoute r)` (`:2043-2045`) checks and adds under `r.getName().trim()`.  Everything that later deletes goes through `deleteRoute(String)` (`:3471-3494`), which finds the route by whatever name it is handed and then deletes by `r.getName()` (`:3492`) - the raw one - so `RemoteDeviceCollection.delete` (`:166-179`) finds no such name and returns false, after the route has already been `disable()`d and stripped from the autonomy selection (`:3478-3490`).  The doors: the right-click Delete (`getRouteList()` at `:3608` hands the UI `getById(i).getName()`, raw); `changeRouteId` (`:3528`, then `add` at `:3531` puts the route into the database under BOTH ids); and the sync's own refresh, `:1478`, which means a station route whose commands changed can never be re-read.  The user-input `newRoute(String, ...)` (`:2211`) and the database-restore `newRoute(String, int, ...)` (`:2176`) both trim before the constructor, so only the two file parsers are the odd doors.

**How to prove it.**  `MarklinRoute r = new MarklinRoute(model, " Padded ", 7); assertTrue(model.newRoute(r)); model.deleteRoute(" Padded "); assertNull(model.getRoute(7))` - red; and again with `deleteRoute("Padded")` - also red, since the lookup succeeds and the delete-by-raw-name fails.  A CS2 file fixture with `.name=Padded ` (trailing space) through `parseRoutes` shows the import door.

**Fix.**  Trim in the two `MarklinRoute` constructors (`super(name.trim())`), which puts the rule at the one place every door meets - the same argument `RouteCommandLocomotiveSpeed` makes for the speed clamp.

### CS3-C2 - a sensor created from its own first event notifies the waiter before it is in the database

| | |
|---|---|
| **Disposition** | Fixed 2026-09-19 |

`newFeedback` (`:2344-2352`) constructs the `MarklinFeedback` - whose constructor parses the message (`MarklinFeedback:38-41` -> `parseMessage` -> `Feedback._setState` -> `Locomotive.monitor.notifyAll()`, `Feedback:70-82`) - and only THEN `feedbackDB.add`s it.  A waiter in `Locomotive.waitForOccupiedFeedback` (`:789`) or `waitForClearFeedback` (`:889`) wakes on that notify, asks `isFeedbackSet(name)` -> `feedbackDB.hasName` -> false, and goes back to sleep.  The add happens after `updateTiles`, `feedbackChanged` and a `logf` - milliseconds, so the waiter almost always loses.  Nothing notifies again until the next `_setState` of ANY sensor.  The dispatch path polls every 5 s until its advisory has been given (`:807-814`), so a train is at worst 5 s late being seen; a route monitor thread ("Dummy Loc", untimed wait) stays parked until the next event anywhere.

**Reachability, honestly.**  `Layout.createPoint` refuses a sensor not in the database (`automation/Layout.java:1972`), and the JSON loader creates a missing one with a warning (`:10496-10502`).  So the only way a waiter names a sensor the database lacks is that path - a sensor in the setup that no page draws - followed by any `refreshLayouts()`, whose prune (`syncLayouts:887-902`) deletes every module no LOADED page mentions.  After that prune the point's `getFeedbackState` reads false for the rest of the session (the other half, in automation's court), and the first real event re-creates the module through this ordering.  Adam's setup is built from his pages, so on his railway this needs a hand-edited configuration.

**How to prove it.**  Model in simulate mode, no feedback "777": start a thread that calls `model.newMM2Locomotive("W", 3).waitForOccupiedFeedback("777", 0)`; deliver one frame through `model.receiveMessage(new CS2Message(CS2Message.CMD_ACC_SENSOR, CS2Message.CS2_PROTOCOL_V2, true, new byte[]{0, 0, 0x03, 0x09, 0, 1, 0, 0}))` (short UID 777, new state 1, length 8 - what the FV3 gate at `:2296` requires).  **Assert the thread ends within 2 s** - red; then deliver a frame for sensor 778 and assert it ends - green, which names the mechanism.  Fix: `add` first, then parse - or construct with `null` and call `parseMessage` after the add, which is what the restore path already does (`:467-472`).

### CS3-C3 - the locomotive id cache is rebuilt only when the whole sync succeeds

| | |
|---|---|
| **Disposition** | Fixed 2026-09-19 |

`syncWithCS2` adds station locomotives through the five-argument `newLocomotive` (`:1534` -> `:3031-3039`), which - unlike the three-argument overload at `:3012-3022` - does not call `rebuildLocIdCache()`; the sync relies on the one call at `:1634`, which is after the `catch` at `:1626` returns -1.  So if anything in the locomotive loop throws after a locomotive has been added or re-addressed (`:1563-1568`), the cache stays stale, `receiveMessage`'s locomotive branch (`:2314-2316`) resolves the new UID to nothing, and every speed, direction and function echo for that locomotive is logged as "unknown locomotive" until the next add, rename, delete or successful sync.  I found no statement in that loop that a real file makes throw - `parseLocomotives` already catches per record - which is why this is a C.  Fix: rebuild in the five-argument overload as the three-argument one does, or move `:1634` into a `finally`.  Check by reading: the two overloads differ by that one line.

### CS3-C4 - `getAutoLayout()` creates a layout on a miss, and a route thread reaches it through an unlocked two-step

| | |
|---|---|
| **Disposition** | Open - trap; predates 2.7.4c (`v2_7_4c:MarklinRoute.java:299-306`) |

`MarklinRoute.execRoute`'s "All Lights On (Autonomy Locomotives Only)" branch (`:883-894`) asks `hasAutoLayout()` and then `getAutoLayout()` on the route thread; `autoLayout` (`:311`) is not volatile and `getAutoLayout()` (`:980-988`) builds `new Layout(this)` when it is null.  `clearAutoLayout()` (`:1001-1010`) on the event thread between the two calls makes the route thread instantiate an empty `Layout` - after which `hasAutoLayout()` is true for a configuration the operator just cleared, and the static layout version has ticked (the comment at `:3481-3483` records the same trap being live once, in `deleteRoute`).  The window is microseconds and needs a lights-on route firing during a clear; a deterministic test cannot force it without a hook.  The twin at `heldReason` (`:456`) is safe by accident - `isAutonomyRunning()` re-asks `hasAutoLayout()` and short-circuits.  Fix: a `Layout got = this.autoLayout; if (got != null) ...` idiom at the two route-thread readers, or make the field volatile and stop creating on read.

---

## D - not defects: looked wrong and is not, and checks that came back clean

| id | status | where |
|---|---|---|
| CS3-D1 | Clean | `Util.writeAtomically` / `saveState` / `restoreState` - the database-loss shapes |
| CS3-D2 | Clean | `LayoutDiagram.writeLayoutIndex` and the page-id rule |
| CS3-D3 | Clean | `CS2File.parseFileContents` and `parseLayout` - what a user-supplied file can do |
| CS3-D4 | Clean | `CS2Message` parsing, `NetworkProxy` reader and reopen |
| CS3-D5 | Clean | `RemoteDeviceCollection`, `linkedLocomotives`, the speed and power monitors |
| CS3-D6 | Clean | behaviour.md sections 7a, 7b and 8 against the code |
| CS3-D7 | Not a defect | `MarklinRoute.executeAutoRoute` re-enable reuses the parked monitor thread |
| CS3-D8 | Not a defect | `MarklinRoute.hashCode` includes the mutable `enabled` |
| CS3-D9 | Not a defect | `LayoutDiagramComponent.getAddress()` on a link tile is the halved artikel |
| CS3-D10 | Not a defect | `MarklinFeedback.parseMessage` calls into the view while holding the module's lock |
| CS3-D11 | Not a defect | `init()` forgets the IP preference in simulate mode |

**CS3-D1.**  `saveState` (`:1703-1792`): staged through `Util.writeAtomically` (`Util:519-569`), which deletes the staging file on a failed write AND on a failed move (AC3-C1); the unreadable-database copy-aside (`:1737-1762`) runs once and only on the non-backup save; `restoreState` (`:1874-1944`) opens the `FileInputStream` as its own resource so a corrupt header cannot leak it (AC3-B1).  `LayoutDiagram.saveChanges` (`:731-824`) refuses a placeholder, keeps a one-time `.bak`, writes atomically, and handles the case-only rename through a temporary name.  `MarklinSimpleComponent` reads `linkedLocomotives` from the volatile unmodifiable map, so a save cannot see a half-built consist (S14-B4 holds).

**CS3-D2.**  `writeLayoutIndex` (`:1691-1883`) reads ids from the file, refuses to write when the file exists and could not be read (DR-B4), reissues a duplicate to the later page, always writes `.id`, keeps `keepAbsent` pages and every unmodelled block and per-page extra, and writes atomically.  `pageIdOrPosition` (`:1166-1178`) is the one rule both readers use; `CS2File.parseLayoutIndex` (`:2210-2246`) calls it.  `repointLinksForNewOrder` sorts both ends and takes the old order from the pages in hand (FV3-A2, T10-C1).  `readIndexLines` falls back to ISO-8859-1 so an old index reads rather than being refused (SV-B1).

**CS3-D3.**  `parseFileContents` (`CS2File:449-663`): both array and key splits are limit-2 (a name with `=` survives), the array join is by hand (a name with `, ` survives), block names match case-insensitively and keep their spelling for the writer, the array-header arm shares its character class with the key arm (X8-B3), left-margin keys are kept (X8-A1).  `parseLayout` (`:2462-2808`) catches `Exception | Error` per page, counts what would not read, puts a marked placeholder in the page's slot, and carries the page id by position so a bad page does not renumber its neighbours; `syncLayouts` throws before `clearLayouts()` when nothing read but the index named pages (RC-A4, T10-C6) and skips the prune when any page failed (RC-A3).  `parseMags`, `parseRoutes`, `parseLocomotives`, `parseLocomotivesCS3` and `parseRoutesCS3` all skip the offending record rather than the import.  `sanitizeFilename` is applied on both the read and the write of a local page.  `copyAtomically` (`:2144-2170`) stages every download.  `parseRoutesFromJson` (`:3855-3924`) disarms each route the statement after its constructor and wraps the JSON failure in a declared type.

**CS3-D4.**  `CS2Message(byte[])` (`:102-137`) copies the buffer, masks every field, clamps the length to the payload; `getSubCommand` guards on `length`, not `data.length`, so a short system frame is not a stop.  `equals(CS2Message)` compares the header bytes as well as the fields.  `NetworkProxy.ReadMessages.run` (`:268-353`) parses only a full-length datagram, treats every `IOException` on an open socket as recoverable with a 50 ms backoff, and exits only when the socket is closed; `sendMessage` (`:207-248`) reopens a closed socket AND restarts the listener (`listen()`, `:160-186`), which declines while the model is still null (TWV-C3).  Lock order is locomotive -> proxy, and nothing takes them the other way round.  `receiveMessage` (`:2460-2699`) resolves each device once into a local and creates a feedback module only from a frame the parser would read (S14-C7, FV3).

**CS3-D5.**  `RemoteDeviceCollection` (`:60-179`): every method synchronised, `add` keeps the two maps one-to-one, and it calls nothing out, so it cannot be part of a cycle.  `MarklinLocomotive.linkedLocomotives` (`:79`) is volatile, published unmodifiable, assigned under the monitor in both writers (`applyLinkedLocomotives:1357-1360`, `unlinkLocomotive:1572-1589`), and `deleteLoc` unlinks through the method rather than the map.  `notifyOfPowerStateChange` and `_setSpeed` both hold `speedMonitor` (`Locomotive:390-498`), in the order `setSpeed` already takes.  `powerState`, `on`, `locIdCache`, `pingStart`, `pingOutstandingSince` and `lastLatency` are volatile with the reason written beside each; `waitForPowerState` is bounded (`:941-967`).  `setF` sends to every member and clamps the range to the consist's widest decoder (MT-359), and `functionsOff` clears the same range it can set.  `Accessory.isConfirmedAt` requires an echo to have been seen.  `MarklinAccessory.parseMessage` confirms and wakes on every echo, changed or not.

**CS3-D6.**  Section 7a: `conflictingAccessoryAndReason` answers null for a route carrying a stop (`:388`), `askable` is false for one (`:793`), and the s88 door skips only the held accessory (`respondToConflict`, `:562-567`; `continue` at `:841`) - verified against the code, not the comments.  Section 7b: an imported route is disarmed (`:3918`); speed is clamped where the command is built (`RouteCommand:170-171`), with -1 kept for instant stop; `pause = max(delay, 150)` (`:1035`) and `THREEWAY_ROUTE_DELAY_MS` = 300 sits above the floor; the CS3 speed `wert / 1000 * 100` passes through the same clamp.  Section 8: `getLayoutList` sorts by name; absent `.id` is 0; a placeholder page is never saved; `Route.evaluate` uses `getAccessoryStateIfPresent` (`:336`); an echoed speed is rounded (`MarklinLocomotive:612`); `getAccessoryByName` resolves either type word (`:2287-2310`).  The one disagreement found is B2.

**CS3-D7.**  `executeAutoRoute` (`:157-254`) returns false when a monitor thread is alive, so `disable()` then `enable(); executeAutoRoute()` starts no second thread.  That looked like a lost monitor; it is not: the parked thread's loop tests `this.enabled` at the top of each pass and after each wait (`:182`, `:194`), so once the sensor next fires it finds the route enabled again and simply continues as its monitor.  What it costs is one full clear-then-occupied cycle before the check is made, which is the same cycle a fresh thread would have waited out.

**CS3-D8.**  `MarklinRoute.hashCode` (`:1424-1433`) hashes `enabled`, which `enable()`/`disable()` change - the drift shape that cost the locomotive class six defects.  Grepped `src/` and `test/`: no `Set` or `Map` keys on a route; both databases key on id and name.  A trap, not a defect; noted so nobody adds one.

**CS3-D9.**  `parseLayout` halves every non-route `artikel` (`:2655-2665`), links included, so `getAddress()` on a link is half its page index.  Every reader of a link's target uses `getRawAddress()` - `repointPageLinks` (`:682`), `toSimpleString` (`:754`), `LayoutLabel:342` and `TrainControlUI:25889` (gui) - and `setLinkedPageIndex` writes both fields the same.  Consistent today; the halved `getAddress()` is a number nothing should read.

**CS3-D10.**  `MarklinFeedback.parseMessage` is `synchronized` and, holding the module's lock, calls `updateTiles` and `network.feedbackChanged` (`:99-102`).  Checked the other side: `LayoutLabel.updateImage` (gui `:1795-1808`) only posts with `invokeLater`, and `TrainControlUI.feedbackChanged` (gui `:4551-4560`) returns before touching Swing unless the route editor is capturing, and never waits on the event thread.  So a tile click on the event thread (`execSwitching` -> `feedback.setState`, also synchronised) cannot deadlock against a sensor frame.  The `logf` inside the lock is the cost the `waitedTooLongFor` comment already records.

**CS3-D11.**  `init()` removes `IP_PREF` whenever `getNetworkCommState()` is false (`:4495-4498`), which is every simulated run.  Old (`v2_7_4c:2934`), and simulate mode asks for no IP, so the operator sees it only as the IP prompt reappearing after a simulated session - a nuisance the comment beside it ("Connection failed - ask for IP on next run") does not mention, not a fault.

---

## What this pass did not cover

- **Nothing was run.**  Every claim above is from reading; the two B findings rest on real files (`cs2_sample_layout`, `test/layouts/live-snapshot`) read by hand and by a throwaway script that halved every `artikel` and grouped by type.  The tests named under each finding are the confirmation, and they have not been written.
- **`automation/**`, `automationui/**` and `gui/**`** - three other reviewers.  Opened only where a path crossed: `LayoutGrid`'s tile binding, `TrainControlUI.deleteLoc`, `feedbackChanged` and `repaintSwitch`, `LayoutLabel.updateImage`, and `Layout`'s two feedback doors.  Nothing in those files was reviewed for its own sake.
- **The CS3 JSON parsers against a live CS3.**  `parseLocomotivesCS3` and `parseRoutesCS3` were read against their own fixtures' shape; no `/app/api/*` output from a real station was compared.
- **`MarklinSimpleComponent` against databases written by 2.6 and earlier** - only the current shape and the two enum-move remaps in `CustomObjectInputStream` were read.
- **`CSDetect`** - read for correctness of the retry logic; the scan was not exercised on a network.
- **The message bundles** - only where a key named in the code needed to exist; not read for content, mojibake or placeholder counts.
- **`ImageUtil` and `I18n`** - read, nothing found, and neither was looked at hard.
- **Rare-path Swing/EDT interactions** beyond the two traced in D10 - `repaintLoc`, `updatePowerState`, `updateLatency`, `confirmRouteConflictMidway`, `emergencyStopTriggered` were not opened.

## Seen outside my scope

Handed to whoever holds those trees; not counted above.

- **gui/LayoutGrid.java:1852-1855** registers each diagram label on `c.getAccessory()` at grid-build time and never re-binds.  This is the half of CS3-B2 that makes an evicted object visible; a fix in `wireComponents` makes it moot, and nothing here is wrong on its own.
- **gui/TrainControlUI.deleteLoc (about :20962)** refuses a delete only while autonomy is running.  With CS3-B1 fixed in the model this door needs nothing; until then it is the one that lets a delete land inside a running route.
- **automation/Layout.java:10496-10502** creates a feedback module for a JSON point's sensor that no page draws, with a warning, and `MarklinControlStation.syncLayouts` (`:887-902`) prunes exactly that module on the next diagram refresh; from then on `Layout.getFeedbackState` for that point reads false.  The two halves each behave as documented and disagree with each other; CS3-C2 is the smaller consequence.  Whether a sensor autonomy relies on should be exempt from the prune is a question for the automation reviewer and for Adam.
- **gui/TrainControlUI.repaintSwitch (:10636-10665)** repaints the keyboard key by address, not the diagram tiles - correct, and the reason a diagram tile bound to a stale object (CS3-B2) gets no help from it.
