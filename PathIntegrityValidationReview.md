# Path Integrity Validation — Review Findings

Comprehensive pass over all non-UI code touched while building the path integrity validation guard
(autonomy trains no longer depart until the Central Station confirms their path's accessories actually
switched). Excludes `TrainControlUI.java` per request.

Files covered: `Layout.java` (guard/validation/counter), `Accessory.java`, `MarklinAccessory.java`,
`MarklinControlStation.java` (`showAutonomyAlert`), `ViewListener.java`, the three message bundles,
`test/testAutonomyPathValidation.java`, `test/testAutonomySimulationSanity.java`,
`test/autonomy_sanity.json`.

No code has been changed as part of this pass — everything below is for discussion before we decide what
to act on.

---

## A. Confirmed bugs — should fix

### A1. German/Danish console log still says "after retry" — retry was removed

- **Where:** `src/org/traincontrol/resources/messages_de.properties:166`, `messages_da.properties:166`
  (key `autolayout.errorPathMisconfigured` — the always-logged console message, not the popup).
- **Context:** When the explicit single-retry was removed from `configureAndLockPath`, the English text
  was updated to drop "after retry" (`messages.properties:165`, *"...could not be confirmed: {1}. Locomotive
  stopped and path released."*). The German and Danish versions of this same key were never touched and
  still read *"...nach Wiederholung nicht bestätigt werden..."* / *"...kunne ikke bekræftes efter
  gentagelse..."* — both mean "could not be confirmed **after retry**."
- **Issue:** The console log now claims a retry happened when it didn't (validation fails after a single
  attempt). Misleading to a German/Danish-reading operator diagnosing a fault.
- **Suggested fix:** Reword both to match English's "could not be confirmed" (drop the retry reference),
  mirroring how `errorPathMisconfiguredDialog` was already updated in all three languages.

### A2. Stale comments describing `PATH_VALIDATION_MS` as a flat, non-scaling timeout

- **Where:**
  - `Layout.java:1375` — validatePathActuation javadoc: *"Waits (up to PATH_VALIDATION_MS) for every
    accessory..."*
  - `Layout.java:1332-1334` — configureAndLockPath comment: *"...other locomotives' path checks are not
    blocked for up to PATH_VALIDATION_MS."*
- **Context:** The actual wait deadline (`Layout.java:1411`) is now
  `PATH_VALIDATION_MS + PATH_VALIDATION_MS * accessories.size()` — i.e.
  `PATH_VALIDATION_MS * (accessories.size() + 1)` — after the dynamic per-path-size scaling was added
  (real-world tuning). Both comments still describe a flat `PATH_VALIDATION_MS` bound.
- **Issue:** Purely a documentation/maintainability gap — a future reader sizing `PATH_VALIDATION_MS` or
  reasoning about worst-case blocking time will underestimate it, possibly significantly for paths with
  several accessories.
- **Suggested fix:** Update both comments (and the field comment at `Layout.java:47-50`, which currently
  says "Max time to wait **per accessory**" — close to correct in spirit, but doesn't mention the flat
  `+1` baseline term) to state the actual formula.

---

## B. Design considerations worth discussing (not bugs)

### B1. No backoff/circuit breaker for a persistently failing path

- **Where:** `Layout.java:1729-1762` (`runLocomotive`'s per-locomotive loop), interacting with
  `Layout.java:1330-1372` (`configureAndLockPath`).
- **Context:** Since the explicit retry and the power-cut were both removed, a validation failure now
  just stops *that* locomotive and releases its locks (`Layout.java:1468-1531`,
  `handleMisconfiguredPath`) — autonomy is free to immediately re-pick and re-attempt the same path next
  loop iteration (`loc.delay(this.getMinDelay() * 1000)` at `Layout.java:1749`, which is `0` in fast
  configs like `autonomy_sanity.json`).
- **Issue:** A single persistently broken/stuck accessory will make *one* locomotive spin indefinitely —
  failing, releasing, immediately re-picking the same path, failing again — throttled only by
  `CONFIGURE_SLEEP` and the (now larger, per B above) validation timeout itself. This produces continuous
  console log spam and a UI popup every `PATH_VALIDATION_ALERT_THRESHOLD`-th failure, forever, until a
  human intervenes.
- **Worth deciding:** Is this the intended tradeoff (favor fast self-healing over alarm fatigue), or
  should a locomotive that fails validation N times in a row on the *same* path get a cooldown /
  auto-pause instead of retrying at full speed?

### B2. Confirmation can be "stale-true" for an accessory never freshly commanded to change

- **Where:** `Layout.java:1383-1439` (`validatePathActuation`/`allConfirmed`), relying on
  `Accessory.isConfirmedAt` (`Accessory.java:187-190`), which compares against `stateAtLastActuation`
  (only advanced by `MarklinAccessory.parseMessage`, `MarklinAccessory.java:167-180`).
- **Context:** `configureEdge` (`Layout.java:1063-1131`) unconditionally re-sends every accessory command
  on every path attempt, but `parseMessage` only advances `stateAtLastActuation` (and only notifies
  waiters) when the echoed state actually *differs* from the previous one (`MarklinAccessory.java:168`,
  `if (this.switched != stateAtLastActuation)`). If an accessory is already sitting at the desired
  position (from some earlier, unrelated actuation), it is treated as confirmed immediately — no fresh
  round-trip confirmation happens for it at all.
- **Issue:** This is an inherent limitation of the CS-echo confirmation model (there's no "poll current
  physical state" primitive to fall back on), not something the guard code got wrong. But it does mean a
  switch that is physically stuck/jammed at a position that happens to match the desired state will pass
  validation with no real verification, and a hand-moved switch that was last echoed correctly will also
  read as "confirmed" until it's actually re-commanded. Worth having explicitly on record as a known gap
  in what this guard can and can't catch.

### B3. Duplicate accessory entries when a path repeats an accessory across edges

- **Where:** `Layout.java:1385-1401` (the accessories/desired list built in `validatePathActuation`) and
  `Layout.java:1470-1485` (`handleMisconfiguredPath`'s misconfigured-list build), both of which iterate
  `e.getConfigCommands()` per edge without de-duplicating by accessory identity across edges.
- **Context:** If two different edges in the same path both command the same accessory to the *same*
  state (plausible with shared track segments), that accessory is added twice. Conflicting commands for
  the same accessory across edges are already rejected earlier by `isPathClear`'s `EdgeConfigurationState`
  check (`Layout.java:1019-1035`), confirmed — so this can't cause an unresolvable/always-failing path.
- **Issue (minor):** A duplicate entry (a) inflates `accessories.size()`, which — combined with A2's
  scaling formula — makes the validation timeout more generous than necessary, and (b) would make the
  same accessory name appear twice in the operator-facing popup/log if that accessory fails. Cosmetic +
  minor inefficiency, not a correctness problem.

---

## C. Test-specific findings

### C1. Station-change counter overwrites `parseAuto`'s default locomotive callback

- **Where:** `test/testAutonomySimulationSanity.java:122-127`:
  ```java
  model.getLocByName(name).setCallback(Layout.CB_ROUTE_END,
      (l) -> stationChanges.get(l.getName()).incrementAndGet());
  ```
- **Context:** `parseAuto` already calls `layout.applyDefaultLocCallbacks(l)` for every placed locomotive
  during `@BeforeClass` (`Layout.java:3614`), which sets `CB_ROUTE_END` to a callback that conditionally
  turns off functions on arrival (`Layout.java:2966-2979`, gated on `turnOffFunctionsOnArrival`, which is
  `true` in `autonomy_sanity.json`) and applies a small pacing delay. `Locomotive.setCallback`
  (`Locomotive.java:1065-1068`) is a single-slot `Map.put` — it doesn't chain, it replaces. So the test's
  counting callback silently discards the default one for the whole run.
- **Issue:** Doesn't break this test's own assertions, but (a) means the test isn't faithfully exercising
  the same callback path a real run of this autonomy file would use, and (b) would silently mask a future
  regression in `turnOffFunctionsOnArrival` handling, since nothing here would notice it stopped firing.
- **Suggested fix:** Use the layout-level multi-slot callback instead, which doesn't touch the
  per-locomotive slot: `Layout.setCallback(String, TriFunction<List<Edge>, Locomotive, Boolean, Void>)`
  (`Layout.java:2744`), fired for every locomotive at both path-start (`true`) and path-end (`false`) —
  e.g. `layout.setCallback("stationChangeCounter", (path, loc, started) -> { if (!started)
  stationChanges.get(loc.getName()).incrementAndGet(); return null; });`. Counts the identical events
  without clobbering anything.

### C2. Wind-down window isn't guaranteed sufficient before teardown deletes locomotives

- **Where:** `test/testAutonomySimulationSanity.java:151-160` (5-second wind-down loop) and
  `:92-107` (`@AfterClass`, unconditional `model.deleteLoc(name)`).
- **Context:** `stopLocomotives()` only sets `running = false`, which `runLocomotive`'s loop checks at the
  *top* of each iteration (`Layout.java:1740`) — a locomotive mid-path (e.g. waiting on an intermediate
  S88 sensor, possibly several stops in a multi-edge path, each up to `maxDelay` seconds) keeps running
  until it naturally finishes. The wind-down loop caps its wait at 5 seconds regardless.
  `MarklinControlStation.deleteLoc` (`MarklinControlStation.java:2180-2187`) has no guard against deleting
  a locomotive that's still referenced in `Layout.activeLocomotives` / `locomotiveMilestones` /
  `locomotivePendingS88`.
- **Issue:** Low probability given the frozen file's fast delays (`minDelay:0`, `maxDelay:1`), but not
  structurally guaranteed, especially with 3 locomotives potentially mid-path simultaneously. If the
  window times out, teardown deletes a locomotive that a still-running thread may still touch, risking a
  stale-reference issue (e.g. a null lookup) surfacing in whatever runs next in the same JVM session.
- **Suggested fix:** Either extend/verify the wind-down loop actually reached empty before proceeding
  (assert or log if it timed out), or don't delete locomotives that are still in `getActiveLocomotives()`.

### C3. `showUI = true` leaves a live popup and non-daemon AWT thread after the suite finishes

- **Where:** `test/testAutonomyPathValidation.java:42` (`init(null, true, true, false, true)`) and
  `:267-310` (`testUiAlertSuppressedUntilThreshold`, which triggers the real popup and only
  `Thread.sleep(5000)` before the test method returns).
- **Context:** `showAutonomyAlert` (`TrainControlUI`, not reviewed in depth per scope, but the call site
  is `MarklinControlStation.java:2541-2548`) shows a real, non-modal-to-the-caller `JOptionPane` via
  `invokeLater`. Nothing in the test or `@AfterClass` closes that dialog or disposes the `TrainControlUI`
  frame.
- **Issue:** AWT's event dispatch thread is non-daemon, so if this test class runs standalone or as part
  of a larger "run all tests" session, the JVM will not exit on its own once the suite finishes — someone
  has to manually close the popup and/or the main window first. This is presumably intentional (the whole
  point of C3 was for the operator to actually see the popup), but worth explicitly confirming: is it
  acceptable when this class runs alongside other test classes that need to report results afterward?

---

## D. Verified sound — no action needed (recorded for completeness)

These were things that looked like they might be issues on first read but checked out on inspection:

- **D1.** The `Accessory.actuationConfirmedMonitor` wait/notify pattern
  (`Layout.java:1405-1436`, `MarklinAccessory.java:176-179`) is textbook-correct: the check happens under
  the same lock as `wait()`, `parseMessage` can only notify while holding that same lock, so there is no
  lost-wakeup window and no deadlock (the waiter never holds an accessory's own monitor).
- **D2.** The `InterruptedException` short-circuit in `validatePathActuation`
  (`Layout.java:1428-1434`, returns `true` and re-sets the interrupt flag) correctly avoids flagging a
  misconfiguration when autonomy is being stopped mid-wait, in both the (now-removed) retry path and the
  single-attempt path.
- **D3.** `handleMisconfiguredPath`'s manual lock-release (`Layout.java:1492-1503`) correctly generalizes
  to multi-edge paths: it clears the locomotive from every edge's *end* point (matching exactly what the
  locking loop in `configureAndLockPath` set, `Layout.java:1344-1350`) and never touches the path's start
  point, so the locomotive correctly ends up back at its original position, not stranded mid-path.
- **D4.** `pathValidationFailureCount` (`Layout.java:68`) cannot leak between Layout instances — there is
  only one constructor (`Layout.java:155`), no copy constructor, so a freshly created Layout (e.g. after
  reloading a different autonomy config) always starts at 0.
- **D5.** `Edge.isOccupied(Locomotive)` cannot NPE after our release code sets a point's locomotive to
  `null`: `Point.isOccupied()` (`Point.java:304-307`) is literally `currentLoc != null`, so the `&&`
  short-circuits before the `.equals(loc)` call ever runs on a null locomotive.
- **D6.** Cross-edge conflicting accessory commands (same accessory, opposite desired states, on
  different edges of one path) are already rejected before `configureAndLockPath` ever locks or validates
  anything, via `isPathClear`'s `EdgeConfigurationState` check (`Layout.java:1019-1035`). So
  `validatePathActuation` never has to reconcile a genuinely-unsatisfiable path.
